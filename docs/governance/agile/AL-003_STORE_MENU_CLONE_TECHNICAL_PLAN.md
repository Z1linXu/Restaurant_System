# AL-003 Store 1 -> Chinatown Live Menu Clone Technical Plan

> Status: `AL-003_PR_F0_READ_ONLY_PLANNING_WAITING_FOR_OWNER_REVIEW`
>
> Prepared: 2026-07-31, America/Toronto
>
> Phase: `IMPLEMENT / REVIEW`
>
> PR-C repository base: `ae019bf6460cbbbd69153a046d0fbda1fe707eb0`
>
> Migration baseline: PR #40 is merged and STG-005A owns
> `V9__add_staging_synthetic_bootstrap_requests.sql`; AL-003 plans V10 only
>
> Runtime access: not performed
>
> Real clone execution, merge, and deployment: not authorized

## 1. Plan discovery result

| Check | Result |
|---|---|
| `AL003_PLAN_FOUND` | `false` before this document was created |
| `PLAN_PATH` | `docs/governance/agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md` |
| `PLAN_STATUS` | `AL-003_PR_F0_READ_ONLY_PLANNING_WAITING_FOR_OWNER_REVIEW` |
| `PLAN_GAPS` | No prior standalone plan covered the current clone contract, target profile, idempotency, transaction, audit, rollback, tests, PR split, and multi-agent ownership together. |
| `PLAN_STALE_SECTIONS` | AL-001 and the Feature Backlog retained historical `Small 13.99`, older item ordering, broader WOK/FRIED/printing assumptions, and an earlier combined AL-003 scope. Those statements are superseded for menu cloning by the final AL-003A comparison and this plan. |
| `RECOMMENDED_ACTION` | Owner review the bounded PR-C, PR-D, and PR-E Drafts plus the PR-F0 read-only planning prerequisite. Keep PR-F blocked until PR-F0 merges. |

PR-A, PR-B, and prerequisite repairs PR-B2 through PR-B4 are now merged. PR-C
implements only the generic locked source snapshot and Category/Station/Item
base graph. Option copying, the concrete Chinatown profile overrides, and the
public Owner API remain isolated to PR-D, PR-E, and PR-F respectively.

This plan is the implementation contract for AL-003. The product mapping is
owned by
[AL-003A_FINAL_MENU_COMPARISON.md](AL-003A_FINAL_MENU_COMPARISON.md).
Repository seed data remains historical evidence only.

## 2. Fixed scope and authorities

### 2.1 Fixed business inputs

- The only source is the live menu of St-Denis, Store ID `1`, read from the
  database when the approved clone operation executes.
- `RuntimeDataSeeder`, `menuImportSeed.ts`, restaurant templates, catalog cache,
  and documentation examples must never supply clone rows or fill missing Live
  data.
- The target Store is supplied by ID at runtime. No Chinatown Store ID is
  hard-coded.
- Source and target must belong to the same Organization.
- Chinatown receives only `SOUP_NOODLE`, `DRY_NOODLE`, `SIDE_DISHES`, and
  `DRINK`, in that order.
- PDF prices and the final AL-003A mapping override earlier backlog prices.
- Combo 1-4 apply only to the four approved main items. Each includes one of
  the three approved sides and one tea egg. Combo 3 includes the tea egg.
- Tea egg is both a standalone target item and an add-on/Combo egg option.
- All five target noodle items receive all seven noodle types.
- Active Store 1 add/remove options for each reused item are preserved.
- There is no schedule or French-localization implementation.
- The operation does not clone printers, assignments, devices, staff, tables,
  orders, payments, credentials, inventory/BOM, analytics, receipt templates,
  KDS configuration, or historical snapshots.

### 2.2 Executable repository evidence

| Concern | Current executable evidence | Planning consequence |
|---|---|---|
| Store-scoped menu | `MenuCategory`, `Station`, `MenuItem`, and `MenuItemOption` contain Store/item ownership fields and generated IDs. | Clone creates fresh rows and may use transient source-to-target ID maps inside the transaction. Those maps are not part of the durable request or public replay contract. |
| Stable identifiers | `MenuItem.sku`, `MenuItemOption.option_code`, and `option_group` are persisted and catalogued. | Match by normalized stable code, never by Chinese name alone. |
| Parent options | `MenuItemOption.parent_option_id` is persisted. | Clone parents after all target option IDs exist. |
| Menu revision | `Store.menu_revision`, `Store.menu_updated_at`, and `MenuRevisionService.incrementRevision`. | Increment target once after all validation; never increment source. |
| Owner authorization | `OwnerOrganizationAuthorizationService` requires an active exact-Organization Owner membership and intentionally has no global Admin bypass. | First API is Owner-only and reuses this boundary. |
| Store access | `StoreAccessService` resolves Owner Organization memberships and Store scope. | Both source and target access are checked; URL IDs cannot override membership. |
| Durable idempotency precedent | V8, `OwnerStoreOnboardingRequestRepository.insertIfAbsent`, and `findForUpdate`. | Use a separate menu-clone request table with the same PostgreSQL conflict/row-lock pattern. |
| Audit | `AuditLogService` stores sanitized metadata but intentionally catches audit write failures. | The clone-request row is canonical durable evidence; `AuditLogService` is supplementary. |
| Existing template path | `PlatformAdminServiceImpl.createStoreFromTemplate` copies template JSON, not live items/options. | It is not reused as clone source or clone engine. |
| Size pricing | Catalog and ordering use item `base_price` plus selected option `price_delta`; size options are data-driven. | PDF S/M/L prices fit the model without order/payment schema changes. |
| Combo pricing | Frontend uses one COMBO upcharge plus selected size delta. | All four PDF combos fit a constant `+5.00` upcharge. |
| Receipt shorthand gap | Existing receipt/kitchen mappings recognize Regular/Large and may treat any non-large size as medium. | Small display compatibility requires explicit regression coverage and a bounded compatibility change if tests prove it is needed; cloning must not hide the issue. |

### 2.3 Scope exclusions

AL-003 does not activate the target Store, enable printing, configure printers,
pair Pads, create tables or accounts, clone inventory, alter ordering/payment
semantics, or deploy data. Store activation and runtime provisioning remain
separate Owner-approved loops.

## 3. End-to-end pipeline

```text
Owner-authenticated request
  -> validate request and fixed profile
  -> authorize exact Organization
  -> reserve/lock idempotency request
  -> lock source and target Store rows in stable ID order
  -> validate Store 1 live source and target preconditions
  -> read a live source snapshot
  -> create required target stations
  -> create four target categories
  -> create selected target items
  -> create normalized target options and parent mappings
  -> apply Chinatown names, prices, sizes, combos, ordering, and exclusions
  -> validate the complete target graph
  -> increment target menu revision exactly once
  -> complete durable clone request and sanitized audit evidence
  -> commit
```

Any failure before commit rolls back all target menu rows and the target menu
revision. Source rows and source revision are read-only throughout.

## 4. Application and API contract

### 4.1 Recommended first entry point

Use a protected Owner Admin API backed by an application service. Do not expose
an unauthenticated endpoint and do not put clone logic in a controller.

Proposed endpoints:

```http
POST /api/v1/owner/organizations/{organizationId}/stores/{targetStoreId}/menu-clone/validate
POST /api/v1/owner/organizations/{organizationId}/stores/{targetStoreId}/menu-clone
```

The validate endpoint is read-only. The execute endpoint requires the
`Idempotency-Key` header. A future controlled command may invoke the same
service, but is not part of the first implementation package.

Platform Admin does not receive an implicit bypass through this Owner endpoint.
If a future internal command is needed, it requires a separately reviewed
authorization and runtime gate.

### 4.2 Request DTO

Proposed `OwnerStoreMenuCloneRequest`:

```json
{
  "source_store_id": 1,
  "profile_code": "CHINATOWN_MENU_2026_02_02"
}
```

Rules:

- `source_store_id` must equal `1` for this profile.
- `targetStoreId` comes only from the path and must not equal the source.
- `profile_code` must exactly match the reviewed profile version.
- The request contains no menu payload, price overrides, passwords, printer
  endpoints, tokens, or arbitrary source mappings.
- Execute requires a nonblank bounded-length `Idempotency-Key`; validate does
  not reserve a key or write evidence.

### 4.3 Response DTO

Proposed `OwnerStoreMenuCloneResponse`:

```json
{
  "clone_request_id": 123,
  "organization_id": 10,
  "source_store_id": 1,
  "target_store_id": 20,
  "profile_code": "CHINATOWN_MENU_2026_02_02",
  "source_menu_revision": 42,
  "target_revision_before": 1,
  "target_revision_after": 2,
  "status": "COMPLETED",
  "replayed": false,
  "created": {
    "stations": 3,
    "categories": 4,
    "items": 17,
    "options": 0
  },
  "result_code": "MENU_CLONE_COMPLETED",
  "warnings": []
}
```

`options` is computed from the live snapshot and normalized profile, so the
plan does not invent a count. Replay returns the original durable request ID,
scope, revisions, status, created counts, safe result code, and deterministic
safe warnings; it does not return category, station, item, or option ID maps.
Internal transaction code may maintain temporary source-to-target ID maps while
building the graph, but those maps are neither persisted nor exposed.

`warnings` contains bounded stable warning codes only. Because V10 deliberately
stores no warning payload, replay warnings must be derivable solely from the
durable result summary and result code; execution-only detail is not replayed,
and an empty list is valid. Responses never include source option payloads,
credentials, endpoints, raw exceptions, or secrets.

### 4.4 Service contracts

Proposed package ownership:

```text
owner/dto/OwnerStoreMenuCloneRequest
owner/dto/OwnerStoreMenuCloneValidationResponse
owner/dto/OwnerStoreMenuCloneResponse
owner/controller/OwnerStoreMenuCloneController
owner/service/OwnerStoreMenuCloneService
owner/service/StoreMenuCloneValidationService
owner/service/StoreMenuCloneTransactionService
owner/service/impl/OwnerStoreMenuCloneServiceImpl
owner/service/impl/StoreMenuCloneValidationServiceImpl
owner/service/impl/StoreMenuCloneTransactionServiceImpl
owner/menu/ChinatownMenuCloneProfile
owner/menu/StoreMenuCloneSnapshot
owner/exception/OwnerStoreMenuCloneException
```

The coordinator owns authorization, idempotency reservation/replay, and error
mapping. The transaction service owns one atomic target-menu write. The profile
contains reviewed target rules, not source menu data.

## 5. Authorization and Store preconditions

### 5.1 Authorization

1. Resolve the authenticated user through the existing request context.
2. Require role `OWNER`.
3. Require an active Owner membership in `{organizationId}`.
4. Load source Store `1` and target Store by ID.
5. Require both stores to have the same requested `organization_id`.
6. Require Store access to target through the same Organization membership.
7. Return 403 for another Organization without revealing whether its Store
   exists.

### 5.2 Target preconditions

Execute is allowed only when all are true:

- target exists and differs from source;
- target belongs to the authorized Organization;
- target status is `inactive` or the exact onboarding-safe equivalent approved
  before implementation;
- `printing_enabled=false` and `printing_mode=DISABLED`;
- target has no categories, stations, items, or options, except a completed
  replay represented by the clone request;
- target has no conflicting profile completion from a different key;
- target has no printer, device, table, staff, order, or other data mutation
  performed by this service.

An empty target is an intentional fail-closed requirement. AL-003 does not
merge into or overwrite an independently edited target menu.

### 5.3 Source preconditions

- Source ID is exactly `1` and source Store is in the same Organization.
- Source Store has a non-null menu revision.
- Required source SKUs are active and unique.
- Required source stations/categories/options resolve uniquely by stable code.
- Source options required to construct seven noodle types are present and
  semantically consistent.
- Unknown, duplicate, or ambiguous stable codes fail validation. No name-based
  guessing or seed fallback is allowed.

## 6. Consistent live source snapshot

The clone transaction locks source and target Store rows in ascending Store ID
order to avoid deadlocks. The shared menu-revision lock contract uses
pessimistic Store-row locks for both source and target so formal menu mutations
serialize with the snapshot transaction. Menu mutation services update the Store
revision in the same transaction while honoring that lock. PR-B2 establishes
this prerequisite before graph-clone implementation proceeds.

Within the clone transaction:

1. Record source `menu_revision` and target revision before.
2. Read all source categories, stations, items, and options needed for the
   selected graph using Store-scoped repository queries.
3. Build an immutable `StoreMenuCloneSnapshot`.
4. Recheck source revision before write completion.
5. Abort with `SOURCE_MENU_CHANGED` if consistency cannot be proven.

No source entity is passed to `save`, mutated in place, or reused as a target
entity. Every target object is a new instance with a null ID.

### 6.1 Reusable profile boundary

The clone transaction, repository queries, revision locking, idempotency, and
evidence handling are shared infrastructure. Chinatown-specific names, prices,
ordering, Combo definitions, and option rules belong only to the versioned
`ChinatownMenuCloneProfile`. Shared code must not contain `if store ==
Chinatown`, `if store_id == 2`, St-Denis-only catalog branches, or
Chinatown-only printing branches. Store 1 is this profile's current reviewed
source input, not a permanent restriction of the future provisioning engine.

Shared request coordination resolves a reviewed profile through a generic
`StoreMenuCloneProfileRegistry`. The profile descriptor owns its code, reviewed
source Store policy, and profile fingerprint. Shared coordinator and fingerprint
services must not import or branch on `ChinatownMenuCloneProfile`; the Chinatown
descriptor is only the first registry entry.

This first profile is intended to feed a later Generic Store Provisioning
Engine and Store Profile Framework. Printing, staff/table, device/Pad, and Store
activation provisioning remain separate future modules and are not implemented
by AL-003 PR-B2.

## 7. Category clone contract

Create exactly these target categories:

| Sort | Code | Chinese | English | Active |
|---:|---|---|---|---|
| 1 | `SOUP_NOODLE` | 汤面 | Soup Noodles | true |
| 2 | `DRY_NOODLE` | 干拌面 | Dry Noodles | true |
| 3 | `SIDE_DISHES` | 小菜 | Side Dishes | true |
| 4 | `DRINK` | 饮料 | Drinks | true |

The target gets new IDs. Source IDs are never retained. WOK, FRIED_NOODLE,
FRIED, and all other source categories are excluded. Duplicate target category
codes, pre-existing category rows, or duplicate relevant source codes fail
closed. Replay reads the completed result and creates no row.

## 8. Station clone contract

Target stations are limited to those referenced by target items:

- `NOODLE` for all five noodles;
- `COLD` for six side dishes;
- Store 1's one active drink station, expected to be `BAR` or its proven live
  equivalent, for six drinks.

The live drink station is resolved from the reused Store 1 drink SKUs and must
be unique. If source drinks resolve to multiple station codes or none, validation
returns `SOURCE_DRINK_STATION_AMBIGUOUS`.

Each target station has a new ID and copied operational name/code semantics,
with deterministic target ordering. `WOK` and `DEEPFRIED` are not created.
Printer assignments, printer endpoints, devices, KDS config, and user station
assignments are not copied.

## 9. Item clone and override contract

### 9.1 Target item table

| Category/order | Target SKU | Source rule | Target Chinese / English | Target price model |
|---|---|---|---|---|
| SOUP 1 | `traditional_beef_noodle` | required unique active source SKU | 兰州牛肉面 / Traditional LanZhou Hand-pull Beef Noodle | base S 14.99; M +2.00; L +4.00 |
| SOUP 2 | `braised_beef_tendon_noodle` | required unique active source SKU | 红烧牛筋面 / Braised Beef Tendon Noodle | fixed 17.99 |
| SOUP 3 | `vegetable_noodle` | required unique active source SKU | 蔬菜面 / Vegetable Noodle | base S 14.99; M +2.00; L +4.00 |
| DRY 1 | `dan_dan_noodle` | required unique active source SKU | 担担面 / Dan Dan Noodle | base S 15.99; M +2.00 |
| DRY 2 | `zha_jiang_noodle` | required unique active source SKU | 老兰州炸酱面 / Zha Jiang Noodle | fixed 17.99 |
| SIDE 1 | `braised_beef_shank_salad` | required unique active source SKU | 兰州辣拌牛展 / Beef Shank Mix With Home Made Spicy Sauce | 9.99 |
| SIDE 2 | `cucumber_salad` | required unique active source SKU | 香辣黄瓜 / Cucumber Mix With Home Made Spicy Sauce | 4.99 |
| SIDE 3 | `edamame` | required unique active source SKU | 雪菜毛豆 / Edamame With Preserved Vegetable | 4.99 |
| SIDE 4 | `shredded_potato` | required unique active source SKU | 海菜土豆丝 / Seaweed Potato Salad | 4.99 |
| SIDE 5 | `sichuan_pepper_chicken` | clone exact live SKU if present; otherwise profile creates this stable SKU | 椒麻鸡 / Sichuan Pepper Chicken | 9.99 |
| SIDE 6 | `tea_egg` | clone exact live SKU if present; otherwise profile creates this stable SKU | 茶叶卤蛋 / Tea Egg | 1.99 |
| DRINK 1 | `coke` | required unique active source SKU | 可乐 / Coke | 3.00 |
| DRINK 2 | `diet_coke` | required unique active source SKU | 健怡可乐 / Diet Coke | 3.00 |
| DRINK 3 | `seven_up` | profile-created item | 七喜 / 7 Up | 3.00 |
| DRINK 4 | `ginger_ale` | profile-created item | 姜汁汽水 / Ginger Ale | 3.00 |
| DRINK 5 | `ice_tea` | required unique active source SKU | 冰茶 / Ice Tea | 3.00 |
| DRINK 6 | `chinese_herbal_tea` | required exact Live product evidence | 中式凉茶 / Chinese Herb Tea | 3.00 |

The two proposed new drink SKUs and optional new side SKUs are technical stable
identifiers. PR-A tests and Owner review freeze them before data implementation.
No Chinese-name match silently substitutes another source product.

### 9.2 Copied and overridden fields

For reused items, copy `item_type` and `cost_per_item` only after source
validation; apply target category, station, bilingual display name, target
price, and sort order from the profile. Every target item is `is_active=true`
and `is_sold_out=false`. Temporary source sold-out state is not copied.

Profile-created side items use the current stable `menu_item` item type and
profile-created drinks use `drink`, matching the executable direct-serve
category behavior. They use the target category/station and no source ID.
`cost_per_item` remains null for a profile-created item until an authorized
manager configures a real cost; the clone must not guess zero or copy an
unrelated source cost.

Items absent from the final AL-003A target table are not copied.

## 10. Option clone and normalization contract

### 10.1 Group policy

| Group | Policy |
|---|---|
| `ADD_ON` / legacy `addon` | Copy every active item-scoped source option for each reused target item. Ensure tea egg add-on exists on the five target noodles at 1.99. |
| `REMOVE` / legacy `remove` | Copy every active item-scoped source option for each reused target item. |
| `NOODLE_TYPE` / `noodle_type` | Resolve the seven live stable semantics from Store 1 and create all seven on every target noodle. |
| `SIZE` / `size` | Do not blindly copy. Replace with the exact target size sets and price deltas. |
| `COMBO`, `COMBO_EGG`, `COMBO_SIDE`, `COMBO_SIDE_REMOVE` | Do not blindly copy. Rebuild only the four approved target combos. |
| `SPICY_LEVEL`, `SOUP_BASE` | Copy active source options for the corresponding reused noodle when present and unambiguous. |
| Unknown group/type | Fail validation; do not silently copy or discard. |
| Inactive option | Do not copy. |

### 10.2 Seven noodle types

Resolve exactly one consistent live definition for each semantic code:

`noodle_capillary`, `noodle_thin`, `noodle_sanxi`, `noodle_erxi`,
`noodle_leek_leaf`, `noodle_wide`, `noodle_extra_wide`.

The exact live codes are confirmed at validation time. If Store 1 uses a
different proven stable convention, PR-D must encode an explicit reviewed alias
map; it must not infer by display name. New target options retain proven names
and price deltas, receive new IDs, and use fixed sort order 1-7.

### 10.3 Size sets

- Traditional Beef and Vegetable: `size_small` delta 0, `size_medium` delta
  2.00, `size_large` delta 4.00.
- Dan Dan: `size_small` delta 0, `size_medium` delta 2.00.
- Zha Jiang and Beef Tendon: no SIZE option.

The first option is the default under current frontend behavior. Tests must
prove the cart total, submitted snapshots, and receipts distinguish Small from
Medium. If the existing display code cannot represent Small correctly, PR-E may
make only the minimum label compatibility change; any need to alter price,
payment, order lifecycle, print routing, or KDS semantics triggers the Owner
stop condition.

### 10.4 Combo sets

Each approved main item receives:

- one `COMBO` option with `price_delta=5.00`;
- one `COMBO_EGG` option for tea egg only;
- three `COMBO_SIDE` options mapped by stable codes to `cucumber_salad`,
  `edamame`, and `shredded_potato`;
- any side-removal behavior derived from the active REMOVE options of the
  standalone target side item, plus explicit parent options only where the
  current model requires them.

No combo option is created for Beef Tendon. No fried egg is implicitly added.
Combo 3 receives the same tea egg requirement as Combos 1, 2, and 4.

### 10.5 Parent mapping

Clone or create all parent options first. Build
`sourceOptionId -> targetOptionId`. Create child rows in a second pass. Every
non-null target `parent_option_id` must point to an option owned by the same
target menu item, unless the existing reviewed Combo-side contract explicitly
allows the current special representation. A source parent outside the selected
graph, missing parent, cycle, or cross-Store reference is a hard failure.

The final validation queries every target option parent and proves target Store
ownership transitively through its menu item.

## 11. Chinatown override profile

`ChinatownMenuCloneProfile` is a versioned code-level business policy, not seed
data and not a database migration. It contains only:

- target category/station selection;
- target item SKU mapping and fixed display order;
- bilingual target names;
- PDF base prices and size deltas;
- exclusion list;
- four combo definitions;
- tea egg dual identity;
- expected option semantics.

It contains no source IDs, target Store ID, runtime prices, credentials,
printer data, or copied menu payload. Changing the profile requires a reviewed
code change and a new profile code/fingerprint version.

## 12. Idempotency and concurrency

### 12.1 New durable model

Do not reuse `owner_store_onboarding_requests`: Store creation and menu cloning
have different lifecycles and replay results.

Proposed append-only migration `V10__add_owner_store_menu_clone_requests.sql`
creates `owner_store_menu_clone_requests` with:

- `id`
- `organization_id`
- `source_store_id`
- `target_store_id`
- `idempotency_key`
- `request_fingerprint`
- `profile_code`
- `status`
- `source_menu_revision`
- `target_revision_before`
- `target_revision_after`
- category/station/item/option result counts
- sanitized `result_code` and `error_code`
- actor user ID
- created/updated/completed timestamps

Required unique constraint:

```text
(organization_id, source_store_id, target_store_id, idempotency_key)
```

Required target lookup index:

```text
(target_store_id)
```

Do not add global historical Store+SKU/category/option uniqueness constraints in
V10. Existing data has not been proven clean, and such constraints could make an
append-only deployment destructive. Request uniqueness plus a pessimistic
target Store lock and empty-target validation protect this operation.

### 12.2 Fingerprint and replay

The SHA-256 fingerprint covers normalized Organization/source/target IDs,
profile code, and the profile-supplied fingerprint. The supplied fingerprint
must change whenever the reviewed profile content changes. It excludes actor
display name and runtime source revision. It stores no full request payload.
Profile codes are exact, case-sensitive version identifiers: surrounding
whitespace or case variants are invalid rather than aliases. This keeps registry
resolution, persisted `profile_code`, and request fingerprint identity aligned.

- Same key + same fingerprint + `COMPLETED`: return the original durable
  request/scope/revision/count/result summary with `replayed=true`; do not
  read/clone again or increment revision, and do not reconstruct or expose ID
  maps.
- Same key + different fingerprint: HTTP 409 `IDEMPOTENCY_CONFLICT`.
- Same key while `PROCESSING`: HTTP 409 `MENU_CLONE_IN_PROGRESS`.
- Concurrent same requests: PostgreSQL unique insert chooses one owner; the
  other locks/replays or reports in progress.
- Different keys against the same target: target Store lock serializes them;
  the later request fails `TARGET_MENU_NOT_EMPTY` after the first commits.

### 12.3 Failure state

Reserve the idempotency request in a small coordinator transaction. Perform all
menu writes, target revision increment, success result, and success evidence in
one clone transaction. On rollback, a separate bounded transaction marks the
request `FAILED` with a sanitized error code only. `FAILED` is terminal in the
first contract: the same idempotency key must never transition back to
`PROCESSING`. After an operator revalidates the target and source, a retry must
use a new `Idempotency-Key` and create a new request.

This design preserves durable failure evidence without retaining partial menu
data.

## 13. Transaction, revision, and source invariants

### 13.1 Atomic write boundary

The transaction includes target stations, categories, items, options, parent
links, final validation, target revision increment, clone-request completion,
and canonical result counts and safe result code. It excludes validation-only
requests. Generated ID maps are transient transaction state only.

Repository `saveAndFlush` checkpoints may expose generated IDs inside the same
transaction but do not commit partial work.

### 13.2 Revision rules

- Record target revision before any writes.
- Increment target revision exactly once after the complete target graph passes
  validation.
- Save the resulting revision in the clone request in the same transaction.
- Replay and failed attempts never increment target revision.
- Source revision is never incremented.

### 13.3 Source invariance evidence

Tests capture and compare before/after:

- source category count and field snapshots;
- source station count and field snapshots;
- source item count, names, prices, active/sold-out state, and ordering;
- source option count, code/group/parent/price/active/order;
- source `menu_revision` and `menu_updated_at`.

No source repository save method is permitted in the transaction service.

### 13.4 No side effects

Tests assert no new rows in printer/device/table/user/membership/credential,
order/order-item/kitchen-task/print-job, inventory/BOM, or analytics tables.
No realtime, KDS, order, or print dispatcher is called.

## 14. Validation and error model

Use `OwnerStoreMenuCloneException` with a safe code, HTTP status, and operator
message. Initial codes:

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `MENU_CLONE_REQUEST_INVALID` | Missing/invalid fixed request fields. |
| 403 | `MENU_CLONE_FORBIDDEN` | Owner/Organization/Store authorization failed. |
| 404 | `TARGET_STORE_NOT_FOUND` | Authorized target does not exist. |
| 409 | `IDEMPOTENCY_CONFLICT` | Key reused with different normalized request. |
| 409 | `MENU_CLONE_IN_PROGRESS` | Same request is currently executing. |
| 409 | `MENU_CLONE_RETRY_REQUIRES_VALIDATION` | A prior attempt with this key is terminal `FAILED`; revalidate and retry with a new key. |
| 409 | `SOURCE_TARGET_SAME_STORE` | Source and target IDs match. |
| 409 | `TARGET_STORE_NOT_READY` | Status or printing guard failed. |
| 409 | `TARGET_MENU_NOT_EMPTY` | Target already has menu/station rows. |
| 409 | `TARGET_CODE_CONFLICT` | Duplicate target category/station/SKU/option code. |
| 409 | `SOURCE_SKU_MISSING` | Required live SKU is absent. |
| 409 | `SOURCE_SKU_DUPLICATE` | Required live SKU is ambiguous. |
| 409 | `SOURCE_OPTION_AMBIGUOUS` | Required option semantics cannot be proven. |
| 409 | `SOURCE_MENU_CHANGED` | Source revision/snapshot changed during execution. |
| 409 | `TARGET_MENU_CHANGED` | Target revision changed during read-only validation. |
| 422 | `TARGET_MENU_VALIDATION_FAILED` | Built graph does not match the reviewed profile. |
| 500 | `MENU_CLONE_FAILED` | Sanitized unexpected failure; no internal payload exposed. |

Validation responses list stable missing/duplicate codes and counts only. They
do not return full source menu data.

## 15. Audit and evidence model

The request table is canonical durable execution evidence. On success, also
call `AuditLogService.record` with action `OWNER_STORE_MENU_CLONED`, target Store
scope, request ID, and sanitized metadata:

- Organization/source/target IDs;
- SHA-256 of the idempotency key, not the raw key in audit metadata;
- profile code;
- source revision and target revision before/after;
- created category/station/item/option counts;
- created/reused/overridden summary by stable code count;
- actor user ID and happened-at timestamp;
- result code.

Do not include names from arbitrary source rows, credentials, tokens, printer
endpoints, secrets, raw request payload, or full menu snapshots. Because the
current audit service is best-effort, audit-log failure does not make a
successful clone appear failed; the completed clone-request row remains the
authority.

## 16. Validation-only design

`/validate` executes authorization, Store guards, source snapshot resolution,
target-emptiness checks, mapping checks, size/combo expressibility checks, and
expected result counts. It returns:

- `valid`;
- source and target revisions;
- expected counts;
- missing/duplicate stable codes;
- safe warnings;
- profile code.

It writes no idempotency row, menu row, audit row, or revision and obtains no
write lock. Validation builds the exact logical graph in memory with
transaction-local virtual target IDs and the same ordered composers used by
execution. It rechecks target emptiness and queries both current menu revisions
after composition, rejecting source or target drift. Execution repeats all
validation and composition after
acquiring the source and target Store locks, persists the plan, and verifies
the stored option fields and parent links against it. A successful validate
response is not an execution authorization and does not guarantee the source
remains unchanged.

## 17. Automated test strategy

### 17.1 Authorization and preconditions

1. Active Owner of the exact Organization can validate/execute.
2. Owner of another Organization receives 403 without Store disclosure.
3. Platform Admin does not implicitly bypass the Owner endpoint.
4. Missing source/target, same Store, target wrong Organization, active target,
   printing not DISABLED, and nonempty target fail with exact safe codes.

### 17.2 Live source validation

5. Required active source SKU missing fails.
6. Duplicate source SKU fails.
7. Duplicate/ambiguous category, station, option code, or drink station fails.
8. A fixture change after historical seed creation is used by the clone,
   proving no seed/template dependency.
9. Unknown option groups fail closed.

### 17.3 Graph creation

10. Exactly four categories with new IDs and fixed order are created.
11. Only NOODLE, COLD, and the proven drink station are created with new IDs.
12. Exactly 17 target items are created in fixed category order.
13. All target IDs differ from source IDs.
14. Names/prices/sizes match the final mapping.
15. Every target item is active and not sold out.
16. WOK, DEEPFRIED, excluded items, and non-PDF drinks are absent.

### 17.4 Options and combos

17. All seven noodle types exist exactly once on each of five target noodles.
18. All active source add/remove options for reused items are retained.
19. Inactive source options are absent.
20. SPICY_LEVEL/SOUP_BASE preservation follows the explicit group policy.
21. Parent options map to new same-target IDs; missing/cross-Store parents roll
    back.
22. Traditional and Vegetable have S/M/L; Dan Dan has S/M; Zha Jiang and Tendon
    have no SIZE options.
23. Size totals equal every PDF price and Small is the default.
24. Only Traditional, Zha Jiang, Vegetable, and Dan Dan have Combo +5.00.
25. Every Combo has tea egg and exactly three approved side choices.
26. Combo 3 includes tea egg; Tendon has no Combo.
27. Tea egg exists as standalone item and add-on option.
28. Sichuan Pepper Chicken and Tea Egg clone-or-create behavior is deterministic.
29. 7 Up and Ginger Ale use unique stable target SKUs.

### 17.5 Idempotency, concurrency, and rollback

30. Same key/same request returns the same durable summary and no duplicate
    rows/revision; no public ID map is returned.
31. Same key/different payload returns 409.
32. Concurrent same-key requests create one graph.
33. Concurrent different-key requests against one empty target create one graph
    and one `TARGET_MENU_NOT_EMPTY` result.
34. Failure at category, station, item, option-parent, final-validation, and
    revision checkpoints leaves no partial target rows.
35. Failed request evidence is sanitized, `FAILED` remains terminal for that
    key, and a revalidated retry succeeds only under a new key.

### 17.6 Invariance and side effects

36. Every source field/count/revision invariant remains unchanged.
37. Target revision increments exactly once; failure/replay do not increment.
38. No printer/device/staff/table/order/payment/KDS/print/inventory/analytics
    record is created or modified.
39. No password, token, credential, endpoint, full source payload, or raw
    idempotency key appears in response, exception, logs, audit metadata, or
    persisted result summary.

### 17.7 Compatibility and database verification

40. Frontend catalog renders all sizes and defaults Small without price drift.
41. Cart and submitted order snapshots preserve the selected size/Combo prices.
42. Receipt/display compatibility distinguishes Small, Medium, and Large; any
    required fix remains label-only and does not alter routing/lifecycle.
43. PostgreSQL 16 applies V1-V10 on first startup and validates without
    reapplying V10 on second startup.
44. Full backend tests and compile pass; frontend tests/build run only if the
    compatibility package changes frontend behavior.
45. `git diff --check` and secret scans pass for every package.

## 18. Migration plan

PR-B is expected to add only
`V10__add_owner_store_menu_clone_requests.sql`. It is append-only and creates the
request/evidence table, one composite unique constraint, and target lookup
index. It does not modify V1-V9, menu rows, Store 1, target menu data, or
historical identifiers.

Migration validation must use isolated PostgreSQL 16:

1. empty database first startup through V10;
2. `flyway_schema_history` V1-V10 success;
3. exact table/constraint/index inspection;
4. second startup with no reapply/checksum/schema error;
5. backend JPA validation and startup;
6. no `Flyway clean`.

Rollback means deploying a compatible prior application only after confirming
it ignores the additive table. The table is not automatically dropped and no
data-destructive down migration is proposed.

## 19. Planned file ownership

Expected implementation files, subject to PR review:

- owner controller, DTO, exception, service, and profile classes described in
  Section 4;
- a new platform entity/repository for menu clone requests;
- Store/category/station/item/option repository queries needed for Store-scoped
  reads and locks;
- V10 migration;
- focused owner/menu tests and PostgreSQL verification evidence;
- `doc/API.md`, `SYSTEM_DOCUMENTATION.md`, Feature Backlog, and Alive Planbook;
- narrowly scoped frontend/renderer tests and label compatibility only if Small
  acceptance proves necessary.

No Android, deployment, printing state-machine, order lifecycle, payment,
inventory, or production configuration file is planned.

## 20. Reviewable PR sequence

| Package | Scope | Dependency | Exit gate |
|---|---|---|---|
| PR-A | This technical plan, frozen DTO/API/error/profile contracts, stale-governance correction. | AL-003A final input | Owner approves plan and stable codes. |
| PR-B | Request entity/repository, V10, idempotency coordinator, sanitized evidence, transaction skeleton. | PR-A | PostgreSQL V1-V10, replay/conflict/concurrency foundation tests pass. |
| PR-C | Store locks, source snapshot validation, category/station/item creation and source invariants. | PR-B | Selected graph/new-ID/order/exclusion/rollback tests pass. |
| PR-D | Active option copy, seven noodle types, parent mapping, conflict validation. | PR-C | Option and cross-Store parent tests pass. |
| PR-E | Chinatown names/prices/sizes/new items/Combo 1-4/order and bounded Small display compatibility. | PR-D | Exact AL-003A target and pricing tests pass. |
| PR-F0 | Shared read-only logical planning boundary and execution-only persistence. | PR-E | Exact validation plan performs no writes or Store/revision locks; execution and rollback tests remain green. |
| PR-F | Protected Owner API, validate-only endpoint, integration/concurrency/full suites, API/system docs. | PR-F0 | Full local verification and secret/diff review pass; Draft PR chain ready. |

Each package is independently reviewable and must not pull later package scope
forward. No package may merge or deploy automatically.

## 21. Multi-agent implementation split

Agents are not started by this planning turn. After Owner approval:

| Agent | Ownership | Prohibited overlap |
|---|---|---|
| Agent 1 - architecture/data | DTO contracts, request entity/repository, V10, idempotency, audit/evidence, transaction interfaces. | No item/option business mapping. |
| Agent 2 - category/station/item | Source snapshot validation, Store locks, transient transaction ID maps, category/station/item clone, source invariants. | No V10 or option implementation; no ID map persistence or public response fields. |
| Agent 3 - options | Active add/remove, noodle types, parent maps, option conflicts. | No target price/name/Combo policy. |
| Agent 4 - Chinatown override | Names, PDF prices, sizes, new items, exclusions, ordering, Combo 1-4, tea egg dual identity. | No authorization/idempotency infrastructure. |
| Agent 5 - API/integration | Owner endpoint, Organization/target scope, validation endpoint, API docs, integration tests. | No repository schema ownership. |
| Agent 6 - independent review | Security, migration, source mutation, transaction rollback, concurrency, and accidental side-effect review. | Read-only unless coordinator assigns a bounded test patch. |

Every modifying agent uses an isolated worktree, reports base/head SHA, changed
files, tests, blockers, and dependencies, and never SSHs or runs a real clone.
The coordinator resolves contracts and conflicts semantically, runs full tests,
and creates Draft PRs only.

## 22. Owner review checkpoints

Owner approval is required before:

1. PR-A is treated as the implementation contract.
2. V10 and the new request lifecycle are implemented.
3. The proposed new SKUs and profile code are frozen.
4. Any Small receipt/display compatibility change is accepted.
5. Any local implementation is merged.
6. Any Staging validation writes synthetic menu data.
7. Any Store 1 runtime read, real target clone, migration, deployment, Store
   activation, print configuration, or production action occurs.

No remaining product question is raised for prices, categories, item ordering,
Combo scope, Combo 3 egg, tea egg identity, noodle types, hidden add/remove,
Tendon scheduling, or language. Live Store 1 fields remain execution evidence,
not Owner product decisions.

## 23. Stop conditions

Stop and return to Owner if:

- the target Store cannot be identified safely;
- source and target are not in one Organization;
- stable source SKUs/options are missing, duplicated, or ambiguous;
- Store locking cannot protect source snapshot/target emptiness;
- database-level idempotency cannot be guaranteed;
- the current model cannot express Combo 3 or exact size prices;
- a destructive migration or source mutation would be required;
- the target graph cannot be committed atomically;
- payment, order lifecycle, print routing/state, or KDS core semantics must
  change;
- runtime access is required before the separately approved execution phase;
- agent contracts conflict in a way the plan cannot resolve safely.

## 24. Acceptance criteria for PLAN

- The source, target profile, exclusions, and current-code constraints are
  explicit.
- DTO, API, service, idempotency, transaction, revision, audit, error, and
  rollback contracts are defined.
- Category, station, item, option, size, Combo, ordering, and new-item rules
  match AL-003A.
- Source invariance and prohibited side effects are testable.
- V10 is additive and isolated from menu data.
- PR and agent ownership are bounded.
- No implementation, runtime read, clone, merge, or deployment occurred.

## Final state

`AL-003_PR_A_WAITING_FOR_OWNER_REVIEW`

The next allowed action is Owner review of PR-A scope. Multi-agent
implementation, migration creation, Store 1 access, and any real clone remain
unauthorized.
