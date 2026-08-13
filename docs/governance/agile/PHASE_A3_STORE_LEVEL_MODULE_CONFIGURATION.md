# Phase A3 Store-level Module Configuration

Status: `PHASE_A3_IMPLEMENTATION_IN_PROGRESS`

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

Expected final A3 completion state after tests, Agent 6, PR, merge,
exact-SHA Staging deploy and automated Staging validation:

```text
PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION = PASS
```
