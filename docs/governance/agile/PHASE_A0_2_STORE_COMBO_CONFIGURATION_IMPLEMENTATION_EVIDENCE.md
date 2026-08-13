# Phase A0.2 Store Combo Configuration Implementation Evidence

## Authority

- Date: 2026-08-13
- Fresh repository authority before implementation: `origin/main@a1f19bcae9e34b95f7ec1c856342816bcf25b7e4`
- Owner accepted A0.1 manual Staging retest:
  `OWNER_A0_1_PRICING_UX_RETEST = PASS`
- Owner approved bounded implementation:
  `PHASE_A0_2_STORE_COMBO_CONFIGURATION`
- Production boundary: `NO MUTATION`
- Staging boundary: deployment allowed only after tests, Agent 6, governance sync,
  PR merge, fresh fetch and exact-SHA deploy.

## Product decision captured

A0.2 implements Store-level Combo Contents configuration. It is intentionally
separate from A0.1 Store-level Combo Pricing:

- Combo price remains canonical in `store_pricing_policies.combo_delta`.
- Item-level Combo availability remains canonical in the existing per-item
  `menu_item_options` `COMBO` row.
- Store-level Combo content availability is canonical in the new
  `store_combo_components` table.
- `menu_item_options` continues to model Size enablement/identity, item
  `COMBO_ALLOWED`, ordinary options, and rollback-compatible legacy rows. It is
  not the new application canonical source for `COMBO_EGG` / `COMBO_SIDE`
  contents.
- New ordering represents Store-level Combo egg/side selections as frozen
  `order_item_options` snapshots with stable negative transport IDs. Those IDs
  are not database row identities and do not create a second source of truth.
- There is no Store-name or Store-ID conditional in shared application code.

## Schema contract

Additive Flyway migration:

```text
V12__add_store_combo_components.sql
```

New table:

```text
store_combo_components(
  id,
  store_id,
  component_group,
  component_code,
  name_zh,
  name_en,
  enabled,
  display_order,
  created_at,
  updated_at
)
```

Constraints:

- unique `(store_id, component_group, component_code)`
- Store foreign key to `stores(id)`
- component groups restricted to `COMBO_EGG` and `COMBO_SIDE`
- component codes restricted to the reviewed system-controlled component catalog

Canonical first component catalog:

| Group | Code | zh | en |
|---|---|---|---|
| `COMBO_EGG` | `combo_tea_egg` | `卤蛋` | `Tea Egg` |
| `COMBO_EGG` | `combo_fried_egg` | `煎蛋` | `Fried Egg` |
| `COMBO_SIDE` | `combo_edamame` | `毛豆` | `Edamame` |
| `COMBO_SIDE` | `combo_shredded_potato` | `土豆丝` | `Shredded Potato` |
| `COMBO_SIDE` | `combo_cucumber_salad` | `拌黄瓜` | `Cucumber Salad` |

Backfill is Store-scoped. It inserts every canonical component for every Store
and enables a component only when active existing item-option semantics show the
Store already exposes that component. It does not delete, truncate, rename,
rewrite order snapshots, edit Flyway history, or use Production data.

## Backend implementation

- `StoreComboComponentDefinition` defines the system-controlled component
  catalog.
- `StoreComboConfigurationService` is the only application read/write service
  for Store-level Combo contents.
- Admin APIs use existing `admin:menu_manage` Store authority:
  - `GET /api/v1/admin/menu/combo-configuration?store_id={storeId}`
  - `PUT /api/v1/admin/menu/combo-configuration`
- Mutations lock the Store, persist component state, increment
  `stores.menu_revision`, and update `stores.menu_updated_at` in the same
  database transaction through `MenuRevisionService`.
- `COMBO_CONFIGURATION_UPDATED` audit logs are emitted without component
  payload details.
- `/frontdesk/menu` includes `combo_configuration`, hides legacy
  `menu_item_options` `COMBO_EGG` and `COMBO_SIDE` rows from new ordering, and
  includes combo configuration in the catalog content hash.
- The frontend builds egg/side pickers from `combo_configuration` and uses
  stable negative option IDs only as transport/snapshot anchors:
  `-20101` Tea Egg, `-20102` Fried Egg, `-20201` Edamame,
  `-20202` Shredded Potato, `-20203` Cucumber Salad.
- New order submission rejects disabled or unsupported Store-configured
  `COMBO_EGG` / `COMBO_SIDE` selections. Historical submitted order snapshots
  remain frozen and are not repriced or reselected.

Fail-closed errors:

```text
COMBO_EGG_CONFIGURATION_MISSING
COMBO_SIDE_CONFIGURATION_MISSING
COMBO_COMPONENT_DISABLED
COMBO_COMPONENT_UNSUPPORTED
```

## Frontend implementation

- Menu Management adds `Combo Configuration / 套餐配置` near Pricing Rules.
- Owners use touch-friendly checkboxes for Store-level egg/side availability.
- Component labels/codes are system-controlled; there is no free-form component
  creation.
- Save writes all enabled flags for the selected Store.
- The menu catalog mapper and IndexedDB content hash include
  `combo_configuration`, so Pad cache revision/content validation observes
  Combo content changes.
- POS combo egg/side choices are derived from `combo_configuration`, not from
  item-scoped `menu_item_options` component rows.

## Compatibility and non-regression

- Pricing Rules remain the only Store-level Combo price source.
- `menu_item_options.price_delta` remains only the A0.1 compatibility bridge for
  old-app rollback, not a permanent dual source of truth.
- Existing drafts/submitted/completed orders, receipts, printing snapshots and
  reports are not repriced or reselected when Combo configuration changes.
- Printing and KDS continue to use stable order option snapshots/codes.
- Store isolation is enforced by Store-scoped rows and Store-scoped admin
  authorization.
- Production is not read from or mutated by this repository package.

## Local verification

Focused backend tests:

```text
mvn -q -Dtest=StoreComboConfigurationServiceImplTest,OrderServiceImplTest,IdempotentOrderSubmissionServiceImplTest test
mvn test -Dtest=StoreComboConfigurationServiceImplTest,OrderServiceImplTest,MenuCatalogHashServiceTest,StoreComboComponentMigrationTest
```

Result:

```text
Focused Store Combo/content snapshot validation tests PASS.
BUILD SUCCESS
```

Frontend focused tests:

```text
npm test -- --run src/hooks/useMenuCatalog.test.ts src/offline/menuCache.test.ts
```

Result:

```text
2 files PASS
14 tests PASS
```

Full backend regression:

```text
mvn test
```

Result after final documentation sync:

```text
Tests run: 432, Failures: 0, Errors: 0, Skipped: 3
BUILD SUCCESS
```

Frontend full tests:

```text
npm test
```

Result:

```text
17 files PASS
87 tests PASS
```

Frontend production build:

```text
npm run build
```

Result:

```text
tsc -b PASS
vite build PASS
```

Changed-file frontend lint:

```text
npx eslint src/features/owner-admin/ComboConfigurationPanel.tsx \
  src/features/owner-admin/MenuManagementPage.tsx \
  src/hooks/useMenuCatalog.ts \
  src/offline/menuCache.ts \
  src/services/ownerMenuOptionService.ts \
  src/types/ordering.ts
```

Result:

```text
PASS
```

Repository-wide `npm run lint` remains blocked by pre-existing unrelated React
lint debt in legacy frontend files; no lint finding points at the A0.2 changed
frontend files.

Agent 6 independent review after exact BLOCK repair:

```text
ACCEPT
```

Accepted evidence:

- V12 additive and Production untouched.
- `store_combo_components` drives Store combo contents.
- `store_pricing_policies.combo_delta` remains Combo price.
- Item `menu_item_options` `COMBO` remains `COMBO_ALLOWED`.
- New POS egg/side choices are derived from `combo_configuration`; stable
  negative IDs are transport/snapshot anchors only.
- New order and idempotent submit paths validate option-row and synthetic
  snapshot Store Combo selections against Store-scoped configuration.

PostgreSQL migration rehearsal:

```text
AL003_POSTGRES_URL=jdbc:postgresql://localhost:5432/<ephemeral-db> \
AL003_POSTGRES_USER=xuzilin \
AL003_POSTGRES_PASSWORD=<local-only placeholder> \
mvn test -Dtest=OwnerStoreMenuClonePostgresIntegrationTest
```

Result:

```text
Flyway validated 12 migrations.
Flyway applied V1 -> V12 to an empty local ephemeral PostgreSQL database.
JPA validate passed.
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The first rehearsal attempt exposed a stale optional-test import for
`StoreMenuCloneProfileRegistry`; the test harness was repaired by importing the
existing registry and Chinatown profile beans. No runtime behavior changed.

## Runtime status

At implementation-evidence creation time:

```text
AGENT_6_REVIEW = PENDING_UNAVAILABLE_IN_CURRENT_CODEX_SESSION
PR = PENDING
STAGING_DEPLOY = PENDING
STAGING_FLYWAY = PENDING_V12
PRODUCTION = NO_MUTATION
```

Agent 6 review was requested multiple times in this Codex session, including
full-context, focused and static-only read-only review requests. No Agent 6
verdict returned within the bounded wait windows, so the package did not
proceed to PR, auto-merge or Staging deployment in this session.

Expected successful stop after PR merge and exact-SHA Staging validation:

```text
PHASE_A0_2_STORE_COMBO_CONFIGURATION_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```
