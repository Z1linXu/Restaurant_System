# Phase A9 Legacy Compatibility Ledger

Status: `PHASE_A9_LEGACY_COUPLING_REMOVAL = REPOSITORY_IMPLEMENTED_PENDING_FINAL_VALIDATION`

Fresh A9 base:

```text
origin/main@8796d03a2f01d3f222fa2e05fc9d2c6152f4809e
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging: exact-SHA deployment/regression only after A9 PR merge
- Schema/Flyway: no migration
- Phase B/C, Chinatown, Sainte-Catherine: not authorized

## Canonical source of truth after A9

| Domain | Canonical source | Legacy path classification |
|---|---|---|
| Store modules | `store_modules` | legacy feature flags are environment/platform capability inputs only |
| Module definitions | `module-catalog.v1.json` | old page-level feature names are not module definitions |
| Dependencies | `module-dependency-graph.v1.json` | invalid/unknown dependency inputs fail closed |
| Profiles | versioned Store Profile tables/artifacts | historical Chinatown profile code is deferred profile identity, not active Store creation |
| Pricing | `store_pricing_policies` | Size/Combo option `price_delta` mirror remains rollback compatibility only |
| Combo contents | Store Combo Configuration (`store_combo_groups`, `store_combo_components`) | old item option rows are rollback/snapshot compatibility only |
| Menu | Store-scoped menu entities/catalog hash | no Store ID/name runtime menu branch |
| Authorization | active Store/Organization memberships + role capability | `users.store_id` is a login/default-store compatibility fallback only when no active memberships exist |
| Hardware | A8 hardware capability/readiness contract | physical endpoints/device credentials remain separate runtime gates |
| Print runtime mode | `stores.printing_mode` resolved through `PrintingRuntimePolicyProperties` | `stores.printing_enabled` is a compatibility mirror, not canonical mode |

## Static hard-code scan

Command class:

```text
rg -n "storeId\\s*==\\s*1|storeId\\s*=\\s*1|store_id\\s*=\\s*1|SOURCE_STORE_ID|ST_DENIS|ST-DENIS|SAINT_DENIS|CHINATOWN|SAINTE_CATHERINE|Sainte-Catherine|4483_R_SAINT_DENIS|4483 R\\. Saint-Denis|DEFAULT_STORE_ID" backend/src/main/java frontend/src -S
```

Repository runtime/source findings:

| Finding | Classification | A9 disposition |
|---|---|---|
| `ChinatownMenuCloneProfile.PROFILE_CODE`, `CONTRACT_VERSION`, `FINGERPRINT_VERSION` | `PROFILE_IDENTITY` | Retained as historical/deferred profile identity; not a shared Store ID/name runtime branch. |
| `ChinatownMenuCloneProfile.SOURCE_STORE_ID = 1L` | `BOUNDED_LEGACY_PROFILE_SOURCE_REFERENCE` | Retained only inside the historical Chinatown profile contract and source-menu staging tools. HTTP clone/onboarding facades are now gated by `FeaturePackage.PLATFORM`; Phase C remains unauthorized. |
| `ChinatownMenuProfileOverridesComposer` source-store equality check | `PROFILE_IDENTITY_GUARD` | Retained to prevent using the Chinatown overrides with an unreviewed source/target context. |
| `StagingSyntheticSourceMenuGuard` source-store check | `STAGING_TOOL_GUARD` | Retained for historical STG-005 source-menu tooling only; not active Production or Store provisioning behavior. |
| `StagingSyntheticSourceMenuManifestFactory.MANIFEST_CODE` | `STAGING_TOOL_IDENTITY` | Retained as staging synthetic evidence identity. |

Result:

```text
RUNTIME_BUSINESS_HARDCODE = 0
BOUNDED_LEGACY_PROFILE_SOURCE_REFERENCE = 1
STAGING_TOOL_GUARD = 2
PROFILE_IDENTITY = retained/deferred
```

## Legacy feature/config inventory

| Path | Classification | A9 result |
|---|---|---|
| Backend `FeatureFlagService.CORE_POS/ADMIN/PRINTING/KDS/ANALYTICS` | `KEEP_ENVIRONMENT_CAPABILITY` | Kept as environment/runtime availability. Store module state remains canonical for business access. |
| Backend `FeatureFlagService.PLATFORM` | `KEEP_ENVIRONMENT_CAPABILITY` | Used to gate legacy/future platform provisioning surfaces; not a Store module source. |
| Backend `FeatureFlagService.DEVELOPER_TOOLS` | `KEEP_ENVIRONMENT_CAPABILITY` | Kept for endpoint test/diagnostic actions only. |
| Frontend `featureConfig.PLATFORM/DEVELOPER_TOOLS` | `KEEP_ENVIRONMENT_CAPABILITY` | Retained to hide platform/dev surfaces in current runtime. |
| Frontend route/page module gating | `REPLACED_BY_STORE_MODULE` | A7 Store Context `module_configuration` remains canonical for Store-scoped business pages. |
| `stores.printing_mode` | `KEEP_RUNTIME_MODE` | Canonical print execution mode. Blank/unknown persisted values now resolve to safe `DISABLED`, never legacy `REAL`. Explicit mutations still validate/fail closed. |
| `stores.printing_enabled` | `BOUNDED_COMPATIBILITY_MIRROR` | Updated with mode changes and displayed as legacy state only; it no longer decides blank print mode as `REAL`. |
| `users.store_id` | `BOUNDED_COMPATIBILITY_DEFAULT` | Retained for login token/default-store compatibility only. It can grant store access only when the user has no active Store or Organization membership. |
| Platform Admin direct Store create | `DISABLED_LEGACY_WRITER` | Backend create paths fail closed with `LEGACY_PLATFORM_STORE_CREATION_DISABLED_USE_PHASE_B_PROVISIONING`; frontend no longer shows direct active Store creation affordances. |
| Owner onboarding/menu clone HTTP facades | `PLATFORM_ENVIRONMENT_GATED` | Existing repository services remain deferred, but HTTP runtime is blocked unless the Platform environment capability is explicitly enabled. |

## A9 code changes

- `PrintingMode` now has explicit supported-mode parsing. Blank/unknown values no longer normalize to `REAL`.
- `PrintingRuntimePolicyProperties` requires `DISABLED` in allowed modes and exposes a safe persisted-mode resolver.
- `PrinterConfigServiceImpl#getStorePrintingMode` resolves blank/unknown/disallowed persisted mode to `DISABLED`.
- `PlatformAdminServiceImpl` fails closed for direct new Store save and create-from-template.
- `PlatformAdminPage` removes the legacy direct create form and marks Store creation as Phase B only.
- Owner onboarding/menu-clone HTTP controllers now require `FeaturePackage.PLATFORM` before Owner authorization/service execution.

## Validation snapshot

Focused and full local validation:

```text
mvn -q -Dtest='PrinterConfigServiceImplTest,PlatformAdminServiceImplMenuOrderingTest,OwnerStoreMenuCloneControllerTest,OwnerStoreOnboardingControllerTest,StoreAccessServiceTest,StoreModuleAccessEvaluatorTest,StoreModuleServiceImplTest,ModuleDependencyValidatorTest,HardwareCapabilityCatalogContractTest' test
PASS

npm run test -- storeModuleAccess.test.ts --run
PASS

mvn -q test
PASS

npm run test
PASS

npm run build
PASS

changed-file eslint --max-warnings=0
PASS

git diff --check
PASS

diff-only prohibited-data scan
PASS
```

Full regression, Agent 6, PR/merge and exact-SHA Staging validation are tracked
in the A9 implementation evidence.

Agent 6:

```text
A9_AGENT6_ACCEPT
```
