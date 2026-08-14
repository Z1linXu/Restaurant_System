# Phase A6 Backend Module Gating Evidence

Date: 2026-08-14

## Authority

- Owner approval:
  `PHASE_A6_BACKEND_MODULE_GATING`
- Fresh `origin/main` before this package:
  `923346f15757ca85fdafb509a803e87f04ae55bd`
- Formal current Staging anti-drift baseline:
  [Current Staging Architecture UML Baseline](../../architecture/current-staging/README.md)
- Current Staging observed read-only before A6:
  deployed SHA `923346f15757ca85fdafb509a803e87f04ae55bd`, Flyway `V16`,
  Store `1` / Organization `1`, menu revision `159`, Printing `MOCK/true`

This package is repository/backend enforcement only. It adds no Flyway
migration, performs no Staging deploy, and performs no Production mutation.

## Canonical backend gating contract

Backend business access is evaluated as layered capability, not as one
legacy feature flag:

```text
Authenticated Principal
→ Store / Organization access
→ role / permission capability
→ Store Module enabled
→ Environment Capability available
→ business action
```

Authentication, Store/Organization access and role permissions remain owned by
the existing `AuthorizationService` / Store access services. A6 adds the
Store-module/runtime capability layer through
`StoreModuleAccessEvaluator`.

The new evaluator exposes the required A6 application contract:

- `isModuleEnabled(storeId, moduleKey)` — Store module state only
- `requireModuleEnabled(storeId, moduleKey)` — fail-closed Store module check
- `evaluateCapability(storeId, moduleKey)` — Store module plus environment
  capability check with auditable issue details
- `requireCapability(storeId, moduleKey)` — fail-closed runtime capability
  guard for business actions

The evaluator uses:

- `store_modules` as the canonical Store-scoped module state source
- `module-catalog.v1.json` and `module-dependency-graph.v1.json` as
  repository authority for module relationships
- `StoreModuleCapabilityProvider` as the environment/runtime capability source
- no hardware-capability enforcement in A6; physical printer/device readiness
  remains the A8 hardware boundary

## Error contract

Store module and environment capability failures return HTTP 403 through
`ModuleAccessException` with one canonical module error family:

| Error code | Meaning |
| --- | --- |
| `MODULE_DISABLED` | The Store module exists but is disabled for this Store. |
| `MODULE_CONFIGURATION_INVALID` | Module key/state/dependency configuration is missing, unknown, duplicate or internally invalid. |
| `MODULE_ENVIRONMENT_CAPABILITY_MISSING` | Store module state is enabled, but the environment/runtime capability required by the dependency graph is unavailable. |

`FEATURE_DISABLED` remains only for legacy environment feature gates such as
developer tools and platform runtime enablement. It is not the canonical Store
module disabled response for A6 business endpoints.

## Domain coverage

| Domain / module | A6 backend coverage | Classification |
| --- | --- | --- |
| `ORDERING_POS` | `OrderController`, `IdempotentOrderSubmissionController`, active frontdesk order APIs, frontdesk beverage workflow. | `CANONICAL_STORE_MODULE` |
| `MENU` | Menu catalog and revision read APIs. | `CANONICAL_STORE_MODULE` |
| `MENU_MANAGEMENT` | Owner Menu Management, option management, pricing rules, combo configuration, categories, stations, and platform menu reads. | `CANONICAL_STORE_MODULE` |
| `TABLE_MANAGEMENT` | Frontdesk dining-table config reads and platform dining-table management. | `CANONICAL_STORE_MODULE` |
| `PRINTING` | Owner printing config/test/reprint APIs, Pad print-job APIs, Store device APIs, print options, reprint and dispatcher print-job creation. | `CANONICAL_STORE_MODULE` plus environment/runtime capability |
| `GRAB_PRINTING` | Covered under the `PRINTING` module and print assignment/module routing. | `MODULE_CAPABILITY` under `PRINTING` |
| `FRONTDESK_RECEIPT` | Covered under the `PRINTING` module and print assignment/module routing. | `MODULE_CAPABILITY` under `PRINTING` |
| `ORDER_HISTORY` | Frontdesk order history/today APIs. | `CANONICAL_STORE_MODULE` |
| `REPORTING_CORE` | Analytics/reporting summary and rebuild APIs. | `CANONICAL_STORE_MODULE` |
| `STAFF_ACCESS` | Staff admin service and platform user management reads/writes. | `CANONICAL_STORE_MODULE` |
| `STORE_ADMINISTRATION` | Owner dashboard, audit-log read path and platform store overview. | `CANONICAL_STORE_MODULE` |
| `KDS` | KDS screens and kitchen task actions require the Store `KDS` module. Kitchen health remains health-only. `KDS=false` remains valid and not activation-blocking. | `OPTIONAL_STORE_MODULE` |
| `ANALYTICS_ADVANCED` | Remains optional/default-off and valid. Current backend has no separate advanced-only API surface beyond core reports; A7 owns frontend advanced UI hiding. | `OPTIONAL_STORE_MODULE` |

## Printing special handling

A6 keeps Store printing module state, environment print capability, runtime
print mode and hardware binding separate:

- Store `PRINTING=false` or missing environment capability prevents
  order-driven `PrintJob` creation in `PrintDispatcherServiceImpl`.
- Order submission still commits independently because printing dispatch is
  asynchronous after the order transaction.
- `stores.printing_enabled` and `stores.printing_mode` remain runtime/rollback
  compatibility fields, not the canonical Store module source.
- A8 remains responsible for real printer endpoint binding, Pad pairing and
  physical hardware capability enforcement.

## Legacy gate audit

| Legacy gate/source | A6 classification | Disposition |
| --- | --- | --- |
| `store_modules.module_key/enabled` | `CANONICAL_STORE_MODULE` | Source of truth for Store module state. |
| `FeatureFlagService.PRINTING` | `ENVIRONMENT_CAPABILITY` / `LEGACY_FEATURE_FLAG` | Consumed by `StoreModuleCapabilityProvider`; not a Store module source. |
| `FeatureFlagService.KDS` | `ENVIRONMENT_CAPABILITY` / `LEGACY_FEATURE_FLAG` | Consumed by `StoreModuleCapabilityProvider`; Store `KDS=false` still returns `MODULE_DISABLED`. |
| `FeatureFlagService.ANALYTICS` | `ENVIRONMENT_CAPABILITY` / `LEGACY_FEATURE_FLAG` | Required for reporting runtime availability; Advanced Analytics remains a separate optional Store module. |
| `FeatureFlagService.PLATFORM` | `ENVIRONMENT_CAPABILITY` | Platform admin runtime guard remains outside Store module state. |
| `FeatureFlagService.DEVELOPER_TOOLS` | `ENVIRONMENT_CAPABILITY` | Developer/diagnostic endpoints remain environment gated. |
| `stores.printing_enabled` | `RUNTIME_MODE` / rollback compatibility | Still exposed by printing config; not the A6 module source. |
| `stores.printing_mode` | `RUNTIME_MODE` | Controls `DISABLED` / `MOCK` / `PAD_DIRECT` / `REAL` runtime behavior after Store module access passes. |
| Printer configs/assignments/device topology | `A8_HARDWARE_BOUNDARY` | Safe topology is visible, but hardware readiness enforcement is deferred to A8. |
| Frontend feature config | `LEGACY_FEATURE_FLAG` / `A7_IMPLEMENTED` | A7 consumes authenticated Store Context module state for Store route/navigation/page gating; feature config remains environment/platform compatibility only. |

## Remaining intentional legacy surface

The raw Platform Admin menu option create/update endpoints remain legacy
admin compatibility because their route shape is not directly Store-scoped.
Owner-facing Menu Management option APIs and all Store-scoped menu management
paths are A6-gated by `MENU_MANAGEMENT`. A future cleanup package should
either retire or Store-scope the raw legacy Platform Admin option mutation
surface.

## Validation

Focused backend tests:

```text
mvn -q -Dtest='AnalyticsAdminControllerTest,PadPrintingControllerTest,OwnerPrintingControllerDisabledStateTest,StoreModuleAccessEvaluatorTest,StoreModuleServiceImplTest,PrintDispatcherServiceImplTest,StoreDeviceControllerTest' test
PASS
```

Full backend regression:

```text
mvn -q test
PASS
```

No Flyway migration was added, so no migration rehearsal is required for A6.

## Drift result

The current Staging architecture package was promoted to the formal
`CURRENT_STAGING_ARCHITECTURE_BASELINE` and synchronized to deployed Staging
`923346f15757ca85fdafb509a803e87f04ae55bd` / Flyway `V16`. A6 introduces an
expected backend architecture change through `StoreModuleAccessEvaluator`; A7
must update the frontend/module-gating architecture once the frontend package
is implemented.

## Stop state if this package is the active loop

```text
PHASE_A6_BACKEND_MODULE_GATING_COMPLETE_WAITING_FOR_PHASE_A7_CONTINUATION
```
