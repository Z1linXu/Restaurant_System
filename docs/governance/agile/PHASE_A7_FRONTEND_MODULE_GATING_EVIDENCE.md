# Phase A7 Frontend Module Gating Evidence

Date: 2026-08-14

## Authority

- Owner approval:
  `PHASE_A7_FRONTEND_MODULE_GATING`
- Fresh `origin/main` before this package:
  `ae144e91a7900f0a541446e93c0f498f41f670c0`
- Prior A6 evidence:
  [PHASE_A6_BACKEND_MODULE_GATING_EVIDENCE](PHASE_A6_BACKEND_MODULE_GATING_EVIDENCE.md)
- Current Staging observed before A7 deployment:
  deployed SHA `923346f15757ca85fdafb509a803e87f04ae55bd`, Flyway `V16`,
  Store `1` / Organization `1`, menu revision `159`, Printing `MOCK/true`

This package is frontend/module-gating only. It adds no Flyway migration,
performs no Production mutation, and does not start A8 hardware capability
work, Phase B, Phase C, Chinatown or Sainte-Catherine.

## Canonical frontend gating contract

Frontend page access is evaluated from authenticated Store Context instead of
legacy frontend feature visibility:

```text
Authenticated Principal
→ Store Context Provider
→ Store module_configuration
→ route/page/nav required Store module
→ module enabled + configuration/environment status
→ page rendered or fail-closed module unavailable page
```

`featureConfig` remains only a bounded environment/platform compatibility
gate for non-Store runtime features such as `PLATFORM` and
`DEVELOPER_TOOLS`. It is no longer the Store-level source for Printing, KDS,
Reports or Store admin navigation.

## Implementation summary

- `StoreContext` now exposes `moduleConfiguration` from
  `/api/v1/stores/{storeId}/context`.
- `storeModuleAccess.ts` defines the frontend Store module key contract,
  route-to-module mapping and fail-closed evaluation aligned to A6 error
  semantics:
  - `MODULE_DISABLED`
  - `MODULE_CONFIGURATION_INVALID`
  - `MODULE_ENVIRONMENT_CAPABILITY_MISSING`
- `RequireStoreModule` gates Store pages after `RequireStoreAccess`.
- Store module unavailable UI distinguishes:
  - disabled Store module
  - missing/invalid Store module configuration
  - missing environment capability
  - Printing runtime mode / legacy Store printing flag where applicable
- Store hooks were split into `useStoreContext.ts` and `StoreContextCore.ts`
  so component files remain compatible with frontend lint/fast-refresh rules.

## Route/page coverage

| Frontend surface | Required Store module | Result |
| --- | --- | --- |
| `/stores/{storeId}/frontdesk` | `ORDERING_POS` | Store Context module gated |
| `/stores/{storeId}/frontdesk/menu` | `ORDERING_POS` | Store Context module gated |
| `/stores/{storeId}/frontdesk/order` | `ORDER_HISTORY` | Store Context module gated |
| `/stores/{storeId}/pickup` | `KDS` | Store disabled UX when `KDS=false` |
| `/stores/{storeId}/kds/*` | `KDS` | Store disabled UX when `KDS=false` |
| `/stores/{storeId}/admin/dashboard` | `STORE_ADMINISTRATION` | Store Context module gated |
| `/stores/{storeId}/admin/audit-logs` | `STORE_ADMINISTRATION` | Store Context module gated |
| `/stores/{storeId}/admin/staff` | `STAFF_ACCESS` | Store Context module gated |
| `/stores/{storeId}/admin/settings/tables` | `TABLE_MANAGEMENT` | Store Context module gated |
| `/stores/{storeId}/admin/menu/items` | `MENU_MANAGEMENT` | Store Context module gated |
| `/stores/{storeId}/admin/settings/printing` | `PRINTING` | Store Context module gated; Printing mode messaging retained |
| `/stores/{storeId}/admin/reports/*` | `REPORTING_CORE` | `ANALYTICS_ADVANCED=false` does not hide core reports |
| `/stores/{storeId}/admin/platform` | non-Store environment gate | Remains `PLATFORM` feature gated |

## Navigation coverage

- Owner admin navigation filters Store-scoped entries by current Store
  `module_configuration`.
- Frontdesk top navigation filters Orders, Menu, Pickup and Dashboard by Store
  modules.
- Frontdesk user menu hides Printing Settings and Menu Management when the
  corresponding Store module is unavailable.
- Dine-in sidebar filters Orders, Menu and Dashboard by Store modules.
- Store switching naturally reloads Store Context and reevaluates all module
  visibility for the selected Store.

## Special cases

- KDS is optional/default-off. A Store with `KDS=false` now reaches a clear
  Store module unavailable screen instead of being blocked by a global legacy
  frontend feature flag before Store Context loads.
- Reporting Core remains visible and routable when `REPORTING_CORE=true`,
  even if `ANALYTICS_ADVANCED=false`.
- Printing UI continues to show runtime print mode (`DISABLED`, `MOCK`,
  `PAD_DIRECT`, `REAL`) after the Store `PRINTING` module gate passes.
  If the module gate blocks, the unavailable page still shows safe runtime
  mode/legacy flag values supplied by Store Context.

## Validation

Focused frontend contract test:

```text
npm run test -- storeModuleAccess.test.ts --run
PASS — 1 file, 5 tests
```

Full frontend tests:

```text
npm run test
PASS — 21 files, 108 tests
```

Frontend build:

```text
npm run build
PASS
```

Changed-file eslint:

```text
{ git diff --name-only -- frontend/src; git ls-files --others --exclude-standard frontend/src; } \
  | sort -u | sed 's#^frontend/##' | (cd frontend && xargs npx eslint)
PASS — 0 errors, 5 pre-existing hook dependency warnings on changed files.
```

Backend regression:

```text
mvn -q test
PASS
```

Agent 6 focused review:

```text
NO_GO finding: OwnerAdminShell still used ADMIN featureConfig for Owner Home.
Repair: removed the remaining ADMIN feature gate from OwnerAdminShell and
removed Store-module feature labels from Home fallback links.
Fix verification: ACCEPT.
```

Full `npm run lint` remains blocked by pre-existing unrelated frontend lint
debt outside this A7 package, including `TakeoutEntryDialog`,
`PrintWorkerHealthBanner`, `OrderLineItemRow`, `OrderDetailPanel`,
`OwnerDashboardPage`, `reportUtils`, and existing hook dependency warnings.
Changed-file ESLint has no A7 errors; the remaining changed-file warnings are
existing hook dependency warnings on data-loading pages, not module-gating
logic errors.

## Production / runtime boundary

- Production: no mutation.
- Staging: no mutation during implementation; exact-SHA Staging deploy and
  validation are required after PR merge under the current Owner-approved A7
  authority.
- Flyway: no migration expected.
- Hardware: A8 remains pending.

## Stop state after merge, exact-SHA Staging deploy and validation

```text
PHASE_A7_FRONTEND_MODULE_GATING_COMPLETE_WAITING_FOR_PHASE_A8_OWNER_CONTINUATION
```
