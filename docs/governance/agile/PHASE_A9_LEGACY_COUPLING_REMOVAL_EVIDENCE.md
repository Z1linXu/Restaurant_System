# Phase A9 Legacy Coupling Removal Evidence

Status: `PHASE_A9_LEGACY_COUPLING_REMOVAL = REPOSITORY_IMPLEMENTED_PENDING_FINAL_VALIDATION`

Date: 2026-08-14

Fresh A9 base after A8 merge:

```text
origin/main@8796d03a2f01d3f222fa2e05fc9d2c6152f4809e
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging: no mutation during repository implementation; exact-SHA deployment
  is required only after the A9 PR enters `main`
- Schema/Flyway: no migration
- Phase B/C, Chinatown, Sainte-Catherine and Production deployment: not
  authorized

## Scope

A9 removes or bounds the remaining legacy couplings identified by the Final
Productization Planbook:

- direct Platform Admin active Store creation;
- blank/unknown print-mode fallback to `REAL`;
- ungated Owner Store provisioning/menu-clone HTTP facades;
- legacy feature/config paths that could be mistaken for canonical Store module
  truth;
- Store ID/name hardcode risk.

The detailed source-of-truth ledger and static scan classification are in:

```text
docs/governance/agile/PHASE_A9_LEGACY_COMPATIBILITY_LEDGER.md
```

## Repository changes

Printing runtime mode:

- Blank or unknown persisted `stores.printing_mode` resolves to safe
  `DISABLED`.
- Explicit print-mode mutations still validate and fail closed for unsupported
  or runtime-disallowed modes.
- `stores.printing_enabled` remains only a compatibility mirror.

Store creation/provisioning:

- `PlatformAdminServiceImpl#saveStore` rejects new Store creation.
- `PlatformAdminServiceImpl#createStoreFromTemplate` rejects legacy template
  creation.
- The Platform Admin frontend no longer exposes direct active Store creation.
- Existing Store updates and unrelated platform read/edit surfaces are retained.
- Owner onboarding/menu-clone HTTP facades are gated by the existing
  `PLATFORM` environment capability, preserving Phase B/C as separate gates.

Authorization:

- Membership + role/capability remains canonical.
- Existing `users.store_id` fallback is retained only when a user has no active
  Store or Organization membership; existing regression coverage proves it does
  not override memberships.

Hardware/module contracts:

- A6 backend module gating, A7 frontend module gating and A8 hardware
  capability readiness remain intact.
- No Store Profile content is rewritten.

## Local validation

Focused backend:

```text
mvn -q -Dtest='PrinterConfigServiceImplTest,PlatformAdminServiceImplMenuOrderingTest,OwnerStoreMenuCloneControllerTest,OwnerStoreOnboardingControllerTest,StoreAccessServiceTest,StoreModuleAccessEvaluatorTest,StoreModuleServiceImplTest,ModuleDependencyValidatorTest,HardwareCapabilityCatalogContractTest' test
PASS
```

Focused frontend:

```text
npm run test -- storeModuleAccess.test.ts --run
PASS
```

Full regression:

```text
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

Runtime/source Store hardcode scan:

```text
RUNTIME_BUSINESS_HARDCODE = 0
```

Remaining hits are bounded in
`PHASE_A9_LEGACY_COMPATIBILITY_LEDGER.md` as profile identity, staging-tool
guard or historical evidence.

Pending before PR:

```text
PR / auto-merge
fresh fetch
exact-SHA Staging deployment/regression
```

Agent 6 focused review:

```text
A9_AGENT6_ACCEPT
```

Agent 6 confirmed that runtime/source Store hardcode hits are bounded profile
identity or staging-tool references; printing mode now fails closed for
blank/unknown persisted values; explicit unsupported/disallowed mutations fail
closed; Platform Admin active Store creation paths are disabled while existing
Store updates remain; Owner onboarding/menu-clone HTTP facades are
`PLATFORM`-gated; no Flyway/schema diff is present; and A9 docs/evidence
record Production `NO MUTATION` and bounded legacy classifications.

## Stop target

After A9 PR merge and exact-SHA Staging validation, stop at:

```text
PHASE_A9_LEGACY_COUPLING_REMOVAL_COMPLETE_WAITING_FOR_PHASE_A10_OWNER_CONTINUATION
```
