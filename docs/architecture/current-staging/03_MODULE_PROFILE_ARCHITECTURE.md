# 03 Module/Profile Architecture

> Phase B Part 1 repository implementation note:
> `LANZHOU_CHAIN_MASTER_MENU/v1`, Master product identity, Store
> materialization and Store-local override mappings are implemented in the
> current Part 1 candidate through additive V18-V20 schema and Owner-only
> provisioning services. They are pending PR/merge and exact-SHA Staging deploy
> before becoming the deployed Staging runtime baseline.

> A10 update: automated Phase A acceptance passed on fresh main/deployed
> Staging SHA `ad4572759e01b5546ec59af24aa36b09e5c2dd00`. Module catalog,
> dependency graph, Store modules, profile template readiness, pricing, combo,
> hardware readiness and Store/staff/auth isolation are accepted for Phase A.
> Phase B materialization remains future work.

> A9 update: Store module state, profile templates, pricing policies, combo
> configuration, menu data, membership/role authorization and hardware
> readiness remain separate canonical sources. Legacy runtime compatibility is
> bounded: `stores.printing_mode` is the canonical print mode,
> `stores.printing_enabled` is a mirror, Owner provisioning/menu-clone facades
> are `PLATFORM` gated, and direct active Store creation is disabled until
> Phase B.

> A8 update: Hardware capability is now a first-class contract, not a blended
> `printing_enabled`/`printing_mode` concept. Module dependency validation uses
> the A8 catalog, Store Context exposes `hardware_readiness`, and legacy profile
> hardware keys resolve through catalog aliases without changing published
> `ST_DENIS_CANONICAL_PROFILE/v1` content.

## Purpose

This diagram shows how the current Phase A module catalog, dependency graph,
Store module state, environment/hardware capabilities, and Store Profile
contract relate to each other.

## Current runtime/source SHA

- Original A5.6 baseline source/deploy SHA:
  `923346f15757ca85fdafb509a803e87f04ae55bd`
- A6 merged source authority before A7:
  `ae144e91a7900f0a541446e93c0f498f41f670c0`
- A10 source/deployed Staging authority:
  `ad4572759e01b5546ec59af24aa36b09e5c2dd00`
- Staging Flyway: `V16`

## Scope

A1 through A10 implemented Phase A architecture. Phase B Part 1 Store
provisioning is repository-implemented in the current candidate and pending
runtime deployment; Phase B Part 2 activation/staff/physical hardware remains
future work.

## Mermaid diagram

```mermaid
flowchart TD
    catalog["A1 module catalog<br/>module-catalog.v1.json"] --> validator["ModuleDependencyValidator"]
    graph["A2 dependency graph<br/>module-dependency-graph.v1.json"] --> validator
    env["Environment capabilities<br/>feature/runtime config"] --> capability["StoreModuleCapabilityProvider"]
    hardware["Hardware capabilities<br/>logical printers, assignments, device readiness"] --> capability
    modules[("store_modules<br/>A3 canonical Store module state")] --> service["StoreModuleService"]
    validator --> service
    capability --> service
    service --> context["Store Context response<br/>module_configuration"]
    service --> adminApi["Admin Store module API"]
    service --> backendGate["A6 StoreModuleAccessEvaluator<br/>backend module/capability gate"]
    context --> frontendGate["A7 frontend module gate<br/>routes, pages, navigation"]

    profileTables[("store_profiles<br/>store_profile_versions<br/>store_profile_artifacts")] --> profileService["StoreProfileCatalogService"]
    profileService --> ownerApi["Owner-only Profile read API"]
    profileValidator["StoreProfileContractValidator"] --> profileTables
    validator --> profileValidator
    profileSeed["A5 ST_DENIS_CANONICAL_PROFILE/v1<br/>12 deterministic artifacts"] --> profileTables
    dryRun["StoreProfileMaterializationDryRunValidator<br/>current dry-run only"] --> profileValidator
    masterMenu["PHASE B PART 1 CANDIDATE<br/>LANZHOU_CHAIN_MASTER_MENU/v1<br/>V18-V19"] --> profileTables
    materializer["PHASE B PART 1 CANDIDATE<br/>Owner provisioning materializer<br/>synthetic READY_FOR_REVIEW Store"] --> phaseB

    legacy["Bounded legacy compatibility<br/>stores.printing_enabled mirror<br/>frontend platform/dev env config"] -. "environment inputs" .-> capability
    printMode["Canonical runtime print mode<br/>stores.printing_mode"] --> capability
    phaseB["Phase B Part 1 Store provisioning<br/>repository implemented, runtime pending"] --> profileTables
```

## Key invariants

- The module catalog and dependency graph are machine-readable repository
  contracts.
- `store_modules` is Store-scoped canonical module state.
- Environment capability is not the same thing as Store module enablement.
- Hardware capability is derived from safe topology and readiness, not secrets.
- `ST_DENIS_CANONICAL_PROFILE/v1` is reviewed seed data and a template for
  later provisioning/materialization work.
- Current Staging runtime at this baseline implements A6 backend gating and A7
  frontend gating; bounded legacy flags remain only environment/platform or
  runtime-mode inputs.
- A9 disables legacy direct active Store writers; Phase B must provide the next
  Store provisioning writer.
- Owner onboarding and menu-clone HTTP facades remain unavailable unless the
  `PLATFORM` environment capability is enabled.
- A10 accepts Phase A automated validation; Owner final Staging acceptance is
  still pending.
- Phase B Part 1 candidate adds Master Menu/materialization tables, APIs and
  writers; deployed Staging closure still requires exact-SHA runtime evidence.
- Part 1 provisioning creates synthetic/non-active validation Stores at
  `READY_FOR_REVIEW` with `MOCK` runtime printing mode.

## What omitted

- physical hardware pairing or printer binding
- Phase B Part 2 activation, staff credential delivery and physical hardware
  provisioning commands

## Source files used

- `backend/src/main/resources/module/module-catalog.v1.json`
- `backend/src/main/resources/module/module-dependency-graph.v1.json`
- `backend/src/main/java/com/restaurant/system/modules/ModuleDependencyValidator.java`
- `backend/src/main/java/com/restaurant/system/modules/StoreModuleServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImpl.java`
- `backend/src/main/java/com/restaurant/system/modules/StoreModuleAccessEvaluator.java`
- `backend/src/main/java/com/restaurant/system/modules/StoreModuleController.java`
- `backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java`
- `backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileMaterializationDryRunValidator.java`
- `frontend/src/App.tsx`
- `frontend/src/features/store/storeModuleAccess.ts`
- `frontend/src/features/store/StoreContext.tsx`
- `frontend/src/features/feature-flags/featureConfig.ts`

## Last verified date

2026-08-14, A10 automated acceptance.
