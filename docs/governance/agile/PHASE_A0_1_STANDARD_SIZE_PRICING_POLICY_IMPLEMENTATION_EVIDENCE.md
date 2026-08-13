# Phase A0.1 Standard Size Pricing Policy Implementation Evidence

Status: `REPOSITORY_VALIDATED_WAITING_FOR_PR_MERGE_AND_STAGING_DEPLOY`

Date: 2026-08-13

Owner approval:

```text
PHASE_A0_1_PRICING_POLICY_SCHEMA_CHANGE_APPROVAL
```

Scope:

- Phase: `PHASE_A_MODULAR_PRODUCTIZATION`
- Loop: `PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY`
- Production: `NO MUTATION`
- Staging: no runtime mutation in this repository evidence checkpoint
- Schema: additive Flyway `V11__add_store_pricing_policies.sql`

## Implemented contract

`store_pricing_policies` is now the new-application canonical read/write source
for Store-level Size and Combo deltas:

| Policy field | Semantic |
|---|---|
| `size_small_delta` | `SMALL` / `小碗` / `Small` / `size_small` |
| `size_regular_delta` | `REGULAR` / `中碗` / `Regular` / `size_regular` |
| `size_large_delta` | `LARGE` / `大碗` / `Large` / `size_large` |
| `combo_delta` | Store-level Combo upcharge |

`menu_item_options` remains canonical for:

- per-item Size enablement and ordering/default identity;
- per-item `COMBO_ALLOWED`;
- ordinary add-on/remove/noodle/soup/spicy options.

For rollback compatibility only, Size/Combo `menu_item_options.price_delta` is
mirrored from `store_pricing_policies` during policy and canonical Size writes.
The mirror is not the new application's source of truth. Phase A retirement
strategy: after old-app rollback compatibility is no longer required by the
runtime planbook, stop writing the mirror for Size/Combo rows and retain the
column only for ordinary options and historical submitted-order snapshots.

## Migration/backfill behavior

`V11__add_store_pricing_policies.sql`:

1. checks active Store-scoped Size/Combo semantic deltas before creating the new
   table;
2. fails closed if the same Store has conflicting active deltas for the same
   Size/Combo semantic:

```text
PHASE_A0_1_PRICING_POLICY_BACKFILL_CONFLICT_WAITING_FOR_OWNER_DECISION
```

3. creates one Store-isolated policy row per Store;
4. derives existing consistent values where possible;
5. uses product defaults only when a Store lacks rows for that semantic.

Local PostgreSQL 18 rehearsal applied V1-V10, inserted non-conflicting
Store-scoped fixtures, applied V11 and produced:

```text
1|-1.50|0.00|3.25|6.00|1
2|-2.00|0.00|2.00|5.00|1
```

The separate conflict rehearsal produced:

```text
CONFLICT_REHEARSAL_FAIL_CLOSED_PASS
```

## API/UI implementation

New Owner/Admin API surface:

```text
GET  /api/v1/admin/menu/pricing-policy?store_id={storeId}
POST /api/v1/admin/menu/pricing-policy/preview
PUT  /api/v1/admin/menu/pricing-policy
PUT  /api/v1/admin/menu/items/{itemId}/size-configuration
PUT  /api/v1/admin/menu/items/{itemId}/combo-policy
```

Menu Management now has:

- `Pricing Rules / 价格规则`, with Store-level Small/Regular/Large/Combo deltas
  and impact preview;
- `Size Configuration / 规格`, with only system-controlled Small/Regular/Large
  enablement and default selection;
- per-item Combo allowed/disabled toggle.

The old generic Owner option writer rejects Size create/update/deactivate/reorder
attempts. Non-Size ordinary options continue to use the existing option writer.

## Revision/cache/snapshot boundaries

- Pricing policy mutations, compatibility mirror writes and
  `stores.menu_revision` / `stores.menu_updated_at` updates occur in the same
  database transaction.
- Catalog responses include `pricing_policy`.
- Catalog hash includes pricing policy identity and deltas.
- Frontend IndexedDB validation uses the same hash contract, preserving
  complete-snapshot/atomic refresh.
- Submitted/completed orders, receipts, print snapshots and reports keep using
  order/order-item/order-option snapshots; no policy change reprices history.

## Validation

Repository validation:

```text
backend full tests:      PASS (415 tests, 3 skipped)
frontend full tests:     PASS (17 files, 85 tests)
frontend build:          PASS
changed-file eslint:     PASS
V11 PostgreSQL rehearsal: PASS
```

Repository-wide frontend lint still reports pre-existing unrelated lint errors
outside the A0.1 changed-file set. The A0.1 touched files pass targeted eslint.

## Boundaries retained

- No Production write, deploy, restart, Flyway, configuration, credential,
  printer, Pad or business-data action occurred.
- No Staging deploy occurred in this repository validation checkpoint.
- No schema downgrade, Flyway history edit, destructive migration, raw DB clone,
  Chinatown, Sainte-Catherine, Phase B, Phase C or Production promotion is
  introduced by this package.

Expected stop after PR merge, exact-SHA Staging V10->V11 deployment and
automated validation:

```text
PHASE_A0_1_STANDARD_SIZE_PRICING_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```
