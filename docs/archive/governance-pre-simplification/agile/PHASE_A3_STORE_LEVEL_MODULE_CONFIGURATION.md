# Phase A3 Store-level Module Configuration

Status: `PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION_PASS`

Date: 2026-08-13

Fresh repository authority at A3 start:

```text
origin/main@1780c8934a502709844713d91c493b076e714983
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging: exact-SHA deploy required after PR merge because A3 adds Flyway V13
  and backend read/config contracts
- Schema: additive only
- Runtime effect: Store module persistence/read/config foundation; existing
  backend/frontend capability gating remains legacy-compatible until A6/A7

## A3 persistence model

A3 adds canonical Store-scoped module persistence:

```text
store_modules
```

Key fields:

```text
store_id
module_key
enabled
source
configuration_status
profile_code
profile_version
metadata_json
created_at
updated_at
```

Schema guarantees:

- `UNIQUE (store_id, module_key)`
- foreign key to `stores(id)`
- allowed module keys match the A1 catalog
- source/status vocabularies are constrained
- no legacy Store fields are dropped or rewritten
- no orders, order items, print jobs, receipts, reports, credentials, devices
  or printer endpoints are rewritten

Migration:

```text
backend/src/main/resources/db/migration/V13__add_store_modules.sql
```

Existing Stores are deterministically materialized without Store ID/name
hardcode:

- core modules: enabled
- `KDS`: disabled/default-off
- `ANALYTICS_ADVANCED`: disabled/default-off

## Canonical read/config contract

Canonical Store module state is exposed through:

```text
GET /api/v1/stores/{storeId}/context
GET /api/v1/stores/{storeId}/modules
PUT /api/v1/admin/stores/{storeId}/modules
```

`/stores/{storeId}/context` is the canonical Store context payload for future
A6/A7/A4 consumers. `/me/workspaces` remains a lightweight Store/Organization
list and intentionally does not duplicate module state.

The response separates:

```text
environment_capabilities
hardware_capabilities
Store module state
legacy runtime mode
user authorization
validation issues
```

## Authorization model

- Store module reads require authenticated Store access through
  `StoreAccessService`.
- Store module mutation requires `ADMIN_STORE_CONFIG` and manager/owner/admin
  authority through the existing `AuthorizationService`.
- Ordinary staff cannot mutate Store module state.
- Store module state does not bypass role/capability authorization.

## Validator integration

`StoreModuleServiceImpl` loads the A1 catalog and A2 dependency graph, then
validates persisted Store module state with `ModuleDependencyValidator`.

Fail-closed cases include:

- unknown module update
- duplicate update
- missing persisted module row
- core module disabled for an active Store
- missing environment capability
- missing hardware capability
- invalid dependency graph

`KDS = DISABLED` remains valid. If KDS is enabled, the runtime must expose both
`KDS_FEATURE_FLAG` and `KDS_DISPLAY_CLIENT`.

## Environment / Store module / runtime boundaries

A3 explicitly keeps these separate:

| Concern | A3 source |
|---|---|
| Environment capability | runtime feature flags and shared infrastructure |
| Store module state | `store_modules.enabled` |
| Store runtime mode | legacy Store runtime fields such as `stores.printing_mode` |
| User authorization | existing role/capability + Store access services |

Printing compatibility:

- `store_modules.PRINTING.enabled` is the canonical Store module state for A3.
- Existing runtime behavior still uses current `stores.printing_enabled` and
  `stores.printing_mode` until A6/A7 gating migration.
- `PAD_DEVICE_FOR_PAD_DIRECT` is evaluated only when current Store runtime mode
  is `PAD_DIRECT`; MOCK/REAL/DISABLED modes do not require physical Pad Direct
  readiness in A3.

Legacy compatibility status:

```text
A3_FOUNDATION_ONLY_LEGACY_RUNTIME_GATING_RETAINED_UNTIL_A6_A7
```

## Store isolation

Focused tests prove updating Store A's optional module state does not mutate
Store B's module state and does not touch Store B menu/pricing/combo tables.
A3 does not increment menu revision and does not alter menu, pricing, combo,
permission, printer, device or order data.

## Focused validation

Focused backend tests:

```text
mvn -q -Dtest='ModuleCatalogContractTest,ModuleDependencyValidatorTest,StoreModuleMigrationTest,StoreModuleServiceImplTest,StoreModuleControllerTest,WorkspaceControllerTest' test
```

Test coverage:

- V13 migration is additive and deterministic
- normal migrated Store module configuration is valid
- KDS disabled/default-off is valid
- unknown module update fails closed
- active Store core module disable fails closed
- optional KDS enable requires environment + hardware capabilities
- cross-Store isolation
- Store Context includes canonical module configuration
- Store module read/mutation authorization

Local validation before Agent 6:

```text
focused backend tests = PASS
backend full regression (mvn -q test) = PASS
frontend build (npm run build) = PASS
frontend tests (npm test) = PASS
isolated Postgres migration rehearsal V1->V13 = PASS
existing Store backfill rehearsal = PASS
unknown module DB check constraint = PASS
Agent 6 = A3_ACCEPT
```

## Runtime DI repair

The first exact-SHA Staging deployment of
`1643ca071199c49b5d4404feac6ba367a3143a81` applied Flyway V13 successfully,
but backend startup failed before readiness because Spring could not instantiate
`StoreModuleServiceImpl`. Root cause: the class has both the production
constructor and a package-private test constructor, but the production
constructor was not explicitly annotated for Spring injection.

Minimal repair:

```text
StoreModuleServiceImpl production constructor = @Autowired
constructor-injection regression test = added
schema/migration changes = none
Store module semantics = unchanged
Production mutation = none
```

Repair validation:

```text
focused backend tests = PASS
backend full regression (mvn -q test) = PASS
frontend build (npm run build) = PASS
frontend tests (npm test) = PASS
```

Repair merge:

```text
PR = #140
MERGE_SHA = c1b5e7681f24a11fbf99293567b3da08076fa3b6
```

## Exact-SHA Staging deployment and acceptance

The final A3 Staging runtime is exact:

```text
deployed Staging SHA = c1b5e7681f24a11fbf99293567b3da08076fa3b6
Staging Flyway = V13
latest migration = V13__add_store_modules.sql
failed migrations = 0
frontend health = 200
system health = 200
SockJS health = 200
Printing = MOCK / true
logical printers = 4
printer assignments = 3
```

Runtime evidence on the canonical Staging host
`restaurant-prod:/srv/restaurant-pos/staging`:

```text
preflight evidence =
/srv/restaurant-pos/staging/evidence/phase-a3-runtime-di-repair-preflight-c1b5e7681f24a11fbf99293567b3da08076fa3b6-1786662504.txt
preflight sha256 =
92b56be77113f2fd8a198eb00b8e03b7b43164726192b19cea89837cf260dba3

health evidence =
/srv/restaurant-pos/staging/evidence/phase-a3-runtime-di-repair-health-c1b5e7681f24a11fbf99293567b3da08076fa3b6-1786662778.txt
health sha256 =
e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14

A3 module acceptance evidence =
/srv/restaurant-pos/staging/evidence/phase-a3-store-module-acceptance-r02-c1b5e7681f24a11fbf99293567b3da08076fa3b6-1786662887.txt
A3 module acceptance sha256 =
21a39676740b41cae00bab823803d63257c12ca1791f865cd6c5628f94cf932c

core regression smoke evidence =
/srv/restaurant-pos/staging/evidence/phase-a3-core-regression-smoke-r02-c1b5e7681f24a11fbf99293567b3da08076fa3b6-1786663014.txt
core regression smoke sha256 =
af47a60cd47997e2d83b1a37347db0c098cd9638d800255cc5c7adb069f880a0
```

Automated A3 acceptance:

```text
Store Context module_configuration = PASS
GET /api/v1/stores/{storeId}/modules = PASS
module count = 11
core required modules = 9
enabled modules = 9
disabled modules = 2
KDS = disabled/default-off
PRINTING module = enabled
legacy compatibility status =
A3_FOUNDATION_ONLY_LEGACY_RUNTIME_GATING_RETAINED_UNTIL_A6_A7
authorized no-op manager/owner mutation = PASS
unauthorized mutation rejected = PASS
unknown module rejected = PASS
duplicate update rejected = PASS
core module disable rejected = PASS
KDS dependency validator rejected missing capability enablement = PASS
duplicate Store/module rows absent = PASS
unique Store/module constraint present = PASS
fail-closed cases caused no semantic module drift = PASS
```

The current Staging runtime contains one Store, so cross-Store isolation remains
proved by focused repository regression tests rather than a live two-Store
mutation. No second Store was created for this A3 acceptance pass.

Core regression smoke:

```text
login = PASS
menu = PASS
Menu Management pricing policy read = PASS
Store Combo Configuration read = PASS
tables = PASS
ordering = PASS
frontdesk active/history reads = PASS
reports = PASS
printing MOCK = PASS
GRAB = PASS
FRONTDESK_RECEIPT = PASS
kitchen routing = NOT_PRESENT_FOR_SELECTED_ITEM
order submit independence = PASS
```

Production boundary:

```text
Production mutation = NONE
Production deploy/restart/config/Flyway = NONE
Production health = 200 / 200
Production Flyway = V10
Production failed migrations = 0
```

## Boundaries retained

A3 does not implement:

- A4 Store Profile Contract
- A5 St-Denis Canonical Profile
- A6 backend module gating rewrite
- A7 frontend route gating rewrite
- A8 hardware capability management
- A9 legacy cleanup
- Phase B Owner Store Provisioning
- Phase C Chinatown/Sainte-Catherine
- Production deploy, migration, restart or configuration change

Final A3 completion state after tests, Agent 6, PRs, merges, exact-SHA Staging
deploy and automated Staging validation:

```text
PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION = PASS
PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION_COMPLETE_WAITING_FOR_PHASE_A4_OWNER_CONTINUATION
```
