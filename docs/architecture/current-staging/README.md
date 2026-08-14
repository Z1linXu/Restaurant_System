# Current Staging Architecture UML Baseline

## Purpose

This directory is the documentation-only architecture baseline for the current
Phase A Staging runtime after A5. It records what is implemented and verified
today, before A6 backend module enforcement, A7 frontend module gating, A8
hardware capability management, Phase B Store provisioning, or Phase C
multi-Store creation.

## Current runtime/source SHA

- Repository source authority used for this documentation pass:
  `5f4504d23135655f63d564301f8e98f3218347b2`
- Current deployed Staging application SHA observed read-only:
  `3440fddad7571409c66189e44976658921e5de1f`
- Current Staging Flyway: `V15`
- Current Staging deployment root: `/srv/restaurant-pos/staging`
- Current Staging compose project: `restaurant-pos-staging`
- Current Staging Store identity:
  `organization_id=1`, `store_id=1`, `store_code=STG005_SRC_20260809_R01`
- Current Staging Printing: Store feature enabled, Store mode `MOCK`,
  runtime allowlist `DISABLED,MOCK`, endpoint configuration disabled
- Current profile identity:
  `ST_DENIS_CANONICAL_PROFILE/v1`, status `READY`,
  fingerprint
  `af1a8f34cd156c1987b74ec1a9a22ddfd004859c617937b7d53f05e16e762602`

## Scope

The diagrams cover the implemented current Staging architecture:

- system context and runtime boundaries
- Store-scoped domain model
- module catalog/dependency/profile architecture
- ordering sequence
- printing sequence
- menu revision and IndexedDB cache sequence
- Staging deployment topology
- authorization flow

They intentionally do not describe future A6-A10, Phase B, Chinatown, or
Sainte-Catherine behavior as current.

## Mermaid diagram

```mermaid
flowchart TD
    baseline["Current Staging Architecture UML Baseline"] --> context["01 System Context"]
    baseline --> domain["02 Domain Model"]
    baseline --> modules["03 Module/Profile Architecture"]
    baseline --> ordering["04 Ordering Sequence"]
    baseline --> printing["05 Printing Sequence"]
    baseline --> cache["06 Menu Revision Cache Sequence"]
    baseline --> deployment["07 Deployment Architecture"]
    baseline --> auth["08 Authorization Flow"]

    runtime["Observed Staging runtime<br/>3440fdd... / Flyway V15"] --> baseline
    source["Fresh source main<br/>5f4504d..."] --> baseline
    future["A6-A10 / Phase B / Phase C<br/>not implemented here"] -. "future only" .-> baseline
```

## Diagram index

- [01 System Context](01_SYSTEM_CONTEXT.md)
- [02 Domain Model](02_DOMAIN_MODEL.md)
- [03 Module/Profile Architecture](03_MODULE_PROFILE_ARCHITECTURE.md)
- [04 Ordering Sequence](04_ORDERING_SEQUENCE.md)
- [05 Printing Sequence](05_PRINTING_SEQUENCE.md)
- [06 Menu Revision Cache Sequence](06_MENU_REVISION_CACHE_SEQUENCE.md)
- [07 Deployment Architecture](07_DEPLOYMENT_ARCHITECTURE.md)
- [08 Authorization Flow](08_AUTHORIZATION_FLOW.md)

## Architecture drift findings

| Finding | Classification | Current evidence | Disposition |
| --- | --- | --- | --- |
| A3 added `store_modules` as canonical Store module state, but current runtime still keeps some behavior gated by legacy environment flags and Store runtime fields, especially printing. | `A6_EXPECTED_WORK` | `StoreModuleServiceImpl`, `StoreModuleCapabilityProviderImpl`, `App.tsx`, current Store row `printing_enabled=true`, `printing_mode=MOCK`. | Document as current compatibility boundary; do not present A6/A7 enforcement as implemented. |
| Live Staging Store menu option rows include legacy/null option-group compatibility rows while the A5 profile template has deterministic profile-local menu graph counts. | `BOUNDED_LEGACY_COMPATIBILITY` | Live Store observed `items=39`, `options=382`, active options `329`; A5 profile evidence records `options=380`, active profile semantics `330`. | Document that A5 profile is a reusable template, not live Store materialization. A6+ may reconcile materialization/runtime parity; this is not an A5.5 blocker. |
| `ST_DENIS_CANONICAL_PROFILE/v1` is stored and validated, but no current code path creates a live Store from it. | `A6_EXPECTED_WORK` | `StoreProfileMaterializationDryRunValidator` validates graph shape only. | Keep profile as template architecture; do not draw Phase B Store creation as current. |
| Frontend package visibility still reads frontend feature configuration in places even though Store modules are available through Store Context. | `A7_EXPECTED_WORK` | `frontend/src/App.tsx`, `frontend/src/features/feature-flags/featureConfig.ts`, Store Context `module_configuration`. | Document as current packaging/navigation compatibility, not a security boundary. |

## Key invariants

- Store state is Store-scoped and Organization-scoped where applicable.
- Store Profiles are versioned templates, not live Store database clones.
- Staging is not downgraded from Flyway V15 and no Flyway history is edited.
- Printing in current Staging is `MOCK`; physical printer binding and Pad
  pairing remain separate runtime gates.
- Production is not read or mutated by this documentation baseline.
- The source repository SHA and deployed Staging SHA are intentionally distinct.

## What omitted

- Phase B Store provisioning workflow
- real Chinatown and Sainte-Catherine Store creation
- A6 backend module enforcement implementation
- A7 frontend module gating implementation
- physical printer endpoints, printer credentials, device tokens, and raw env
- customer PII, order history, payment data, and Production runtime details

## Source files used

- `docs/governance/runtime/ALIVE_RUNTIME_PLANBOOK.md`
- `docs/governance/runtime/CURRENT_HANDOFF.md`
- `docs/governance/agile/FINAL_PRODUCTIZATION_PLANBOOK.md`
- `docs/governance/agile/PHASE_A1_MODULE_CATALOG.md`
- `docs/governance/agile/PHASE_A2_MODULE_DEPENDENCY_GRAPH.md`
- `docs/governance/agile/PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION.md`
- `docs/governance/agile/PHASE_A4_STORE_PROFILE_CONTRACT.md`
- `docs/governance/agile/PHASE_A5_ST_DENIS_CANONICAL_PROFILE.md`
- `docs/governance/runtime/PHASE_A5_ST_DENIS_CANONICAL_PROFILE_STAGING_EVIDENCE.md`
- `backend/src/main/resources/module/module-catalog.v1.json`
- `backend/src/main/resources/module/module-dependency-graph.v1.json`
- `backend/src/main/resources/db/migration/V11__add_store_pricing_policies.sql`
- `backend/src/main/resources/db/migration/V12__add_store_combo_components.sql`
- `backend/src/main/resources/db/migration/V13__add_store_modules.sql`
- `backend/src/main/resources/db/migration/V14__add_store_profiles.sql`
- `backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql`
- `deployment/cloud/docker-compose.staging.yml`
- `deployment/cloud/README_STAGING.md`
- `deployment/cloud/staging-deploy.sh`

## Last verified date

2026-08-14.
