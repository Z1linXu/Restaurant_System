# Category base-price adjustment — implementation notes

Supporting implementation contract, 2026-11-12. This document does not authorize
deployment, a Store capability change, or any Production price adjustment.

## Scope and Store capabilities

`V42__add_category_bulk_pricing.sql` adds
`store_pricing_policies.category_bulk_pricing_enabled boolean NOT NULL DEFAULT false`.
It enables no Store and changes no menu price. Only an explicitly reviewed,
Store-scoped reconciliation may enable the capability. Store names, presentation,
theme, Organization defaults, or the presence of template groups never enable it.
Existing Size/Combo policy updates preserve this flag.

`GET /api/v1/admin/menu/pricing-capabilities?store_id=18` returns:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "store_id": 18,
    "category_bulk_enabled": true,
    "standard_size_enabled": false
  }
}
```

`standard_size_enabled` describes actual active, non-template canonical
`size_small` / `size_regular` / `size_large` leaves on active Store items, with
a Store pricing policy. A template `SIZE` group such as Allô noodle shapes does
not qualify. This capability response does not remove the legacy Size/Combo APIs
or alter Lanzhou or neutral Store configuration.

All three endpoints require existing `ADMIN_MENU_MANAGE` Store authorization and
the `MENU_MANAGEMENT` management module gate. Bulk preview/apply additionally
require the explicit bulk capability. Missing capability fails closed with 403.

## Preview

`POST /api/v1/admin/menu/category-pricing/preview`

```json
{
  "store_id": 18,
  "category_ids": [11, 12],
  "direction": "INCREASE",
  "amount": "1.25",
  "currency": "CAD",
  "include_inactive": false
}
```

The request must explicitly choose whether inactive items are included. Selected
categories must be distinct, active, and owned by the requested Store; a missing,
inactive, or foreign category rejects the entire request. Item relationships
that violate Store scope also reject the entire request. `direction` accepts only
`INCREASE` / `DECREASE`; `amount` is a positive `BigDecimal` with at most two
decimal places. There is no percentage or order surcharge mode.

The response includes `preview_id`, normalized request fields, `menu_revision`,
`fingerprint`, `expires_at`, `affected_item_count`, `excluded_item_count`, and:

- `items`: every affected item with `item_id`, `category_id`, `sku`, three name
  fields, `old_base_price`, and `new_base_price`;
- `excluded_items`: item identity/name fields and `REFILL_ITEM`, `FIXED_OFFER`,
  or `INACTIVE_ITEM` reason.

Any non-null server-owned `refill_category_code` always excludes the item.
Explicit item types `COMBO`, `OFFER`, `FIXED_OFFER`, and `FIXED_COMBO` are always
excluded, case-insensitively. Neither item names nor SKU prefixes infer fixed
offers. Including inactive items does not override those exclusions. A decrease
that would make any eligible base price negative rejects the complete preview;
zero resulting prices are permitted. Null/invalid eligible base prices fail closed.

Preview briefly takes the existing Store menu lock for a consistent snapshot.
It writes only an immutable preview identity/hash/expiry row in
`store_category_price_adjustment_previews`, valid for ten minutes. It does not
alter menu items, options, pricing policy, audit, or menu revision.

## Apply and replay

`POST /api/v1/admin/menu/category-pricing/apply` repeats the exact adjustment
fields and adds:

```json
{
  "preview_id": "8af6e9c3-e59a-4ddc-97f2-1047a15cc09f",
  "expected_revision": 94,
  "fingerprint": "<64 lowercase hexadecimal characters from preview>",
  "idempotency_key": "<one stable identity for this save and every retry>"
}
```

Inside one transaction the service acquires the same Store row lock used by
single-item edits, checks the Store capability, and looks up the independent
`store_category_price_adjustments` ledger. The database enforces
`UNIQUE(store_id,idempotency_key)`.

- A previously committed key with the same normalized complete request hash
  returns the original persisted result before checking expiry or revision.
  It writes no price, revision, ledger, or second audit.
- Reusing a key with a different declared request body returns 409
  `CATEGORY_PRICING_IDEMPOTENCY_CONFLICT`.
- An expired preview returns 409 `CATEGORY_PRICING_PREVIEW_EXPIRED`.
- Changed request, missing preview, revision mismatch, or recalculated snapshot
  fingerprint mismatch returns 409 `CATEGORY_PRICING_PREVIEW_STALE`.
- Empty eligible selections cannot be applied. Validation/scope/negative-price
  errors reject the entire batch.

A new successful apply changes only affected `menu_items.base_price` and
`updated_at`, advances the existing menu revision once, inserts a strict
`MENU_CATEGORY_BASE_PRICES_ADJUSTED` audit, and persists the original result in
the ledger. Audit or ledger failure rolls back every price and revision write.
It deliberately bypasses the failure-suppressing `AuditLogService`.

The result contains the normalized adjustment, `preview_id`, `idempotency_key`,
`before_revision`, new `menu_revision`, `before_fingerprint`, all actual price
changes, exclusions, and counts. No Add-on/Remove/Size/Combo delta, historical
order snapshot, receipt, or PrintJob is rewritten.

## Frontend integration

`CategoryBulkPriceAdjustmentPanel.tsx` exports:

```ts
interface CategoryBulkPriceAdjustmentPanelProps {
  storeId: number
  categories: Array<{ id: number; label: string }>
  onSaved?: (message: string) => void
}
```

The parent passes active Store categories. The panel loads its own pricing
capability, renders loading/error/retry states, and returns `null` when the
capability is disabled. It supports category multiselect, increase/decrease,
fixed CAD amount, explicit inactive inclusion, actual server price preview,
and a single confirm/save action. Controls have a minimum 48px touch height.
English, Chinese, and French display changes preserve the draft/preview.

`ownerMenuPricingService.ts` exports `fetchStorePricingCapabilities`,
`previewCategoryBasePrices`, and `applyCategoryBasePrices`, plus request/result
types. The parent can read `standard_size_enabled` to choose its existing pricing
surface. The shared `MenuManagementPage` integration is owned by the main agent.

Same-tick duplicate save clicks are guarded synchronously. A network-unknown
save, including an HTTP 5xx/gateway timeout after a possible commit, freezes
draft editing and retains the exact request/key for `Retry save`. Only explicit
400/401/403/404/409/422 rejections discard that request; a confirmed 409 asks for
a fresh preview. Late responses
from a previously selected Store are ignored. `onSaved` fires only after a
confirmed successful apply/replay.

## Verification

- Frontend focused tests: PASS, 18 tests after gateway-loss/replay regression.
- Backend tests added; Maven execution is coordinated by the main agent:
  `CategoryBulkPriceServiceTest`, `CategoryBulkPriceIntegrationTest`,
  `OwnerMenuCategoryPricingControllerTest`, and the existing
  `StorePricingPolicyServiceImplTest` capability-preservation assertion.
- H2 integration exercises exact V42 SQL, real JPA menu locking and single-item
  edit contention, concurrent replay, scope/negative-price rejection, permanent
  refill/fixed-offer exclusions, strict audit and ledger rollback, and unchanged
  option/order/receipt/PrintJob data.
- PostgreSQL deployment/Flyway, real Allô opt-in, browser acceptance, and actual
  restaurant prices have not been mutated by this implementation subtask.

The main agent must incorporate the final API/schema summary into the shared
`SYSTEM_DOCUMENTATION.md` and API reference and record consolidated validation.
Preview rows and ledgers are retained; no cleanup job or retention policy is
introduced in this bounded change.


### Final role boundary

The three new bulk-price/capability endpoints use the existing Manager/Owner/Admin
role gate in addition to ADMIN_MENU_MANAGE and MENU_MANAGEMENT. This matches the
existing pricing-policy management boundary. The shared FRONTDESK capability
registry, existing single-item operations and Add-on permissions are unchanged.
Synthetic QA verifies FRONTDESK cannot invoke these new batch operations.

## Coordinator acceptance and production boundary

App e7dde0a6e27adf3be00baa9972fee06cdcc5482c:combined backend1519total/1402pass/
117skip/0failures, frontend481/84pass and synthetic PostgreSQL V42. Human browser
selected Noodle+Rice, previewed14 items +CAD1, expanded the two excluded free
refills, confirmed save/reloaded and observed B1=17.99 in Frontdesk. The same
UI decreased1 and restored all approved base prices. Tablet1024/768 controls
were operated. Two actual strict-audit/idempotency ledger operations were
verified; previous order/print snapshot hashes stayed unchanged. Existing
network-unknown retry tests verify one commit with the retained operation key.

Production has not run V42 or enabled the new Store capability; current34 base
prices remain original. Candidate deployment and a bounded Store1 capability
enable are pending the remaining browser gate. No production adjustment was
applied. Hosted CI is not configured/run; these are local/isolated results.
