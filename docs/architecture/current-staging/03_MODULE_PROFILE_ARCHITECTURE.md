# 03 Module/Profile Architecture

## Purpose

This diagram shows how the current Phase A module catalog, dependency graph,
Store module state, environment/hardware capabilities, and Store Profile
contract relate to each other.

## Current runtime/source SHA

- Repository source SHA: `5f4504d23135655f63d564301f8e98f3218347b2`
- Deployed Staging SHA: `3440fddad7571409c66189e44976658921e5de1f`
- Staging Flyway: `V15`

## Scope

A1 through A5 implemented architecture. A6/A7/A8 are shown only as future
work, not as current behavior.

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

    profileTables[("store_profiles<br/>store_profile_versions<br/>store_profile_artifacts")] --> profileService["StoreProfileCatalogService"]
    profileService --> ownerApi["Owner-only Profile read API"]
    profileValidator["StoreProfileContractValidator"] --> profileTables
    validator --> profileValidator
    profileSeed["A5 ST_DENIS_CANONICAL_PROFILE/v1<br/>12 deterministic artifacts"] --> profileTables
    dryRun["StoreProfileMaterializationDryRunValidator"] --> profileValidator

    legacy["Legacy runtime compatibility<br/>stores.printing_enabled / printing_mode<br/>frontend feature config"] -. "current compatibility" .-> context
    a6["A6 backend module enforcement<br/>future"] -. "not implemented" .-> service
    a7["A7 frontend module gating<br/>future"] -. "not implemented" .-> context
    phaseB["Phase B Store provisioning<br/>future"] -. "not implemented" .-> profileTables
```

## Key invariants

- The module catalog and dependency graph are machine-readable repository
  contracts.
- `store_modules` is Store-scoped canonical module state.
- Environment capability is not the same thing as Store module enablement.
- Hardware capability is derived from safe topology and readiness, not secrets.
- `ST_DENIS_CANONICAL_PROFILE/v1` is reviewed seed data and a template for
  later provisioning/materialization work.
- Current Staging runtime intentionally retains bounded legacy gates until
  A6/A7.

## What omitted

- A6 implementation mechanics
- A7 UI hiding/security enforcement implementation
- A8 physical hardware pairing or printer binding
- Phase B materialization commands

## Source files used

- `backend/src/main/resources/module/module-catalog.v1.json`
- `backend/src/main/resources/module/module-dependency-graph.v1.json`
- `backend/src/main/java/com/restaurant/system/modules/ModuleDependencyValidator.java`
- `backend/src/main/java/com/restaurant/system/modules/StoreModuleServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImpl.java`
- `backend/src/main/java/com/restaurant/system/modules/StoreModuleController.java`
- `backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java`
- `backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileMaterializationDryRunValidator.java`
- `frontend/src/App.tsx`
- `frontend/src/features/feature-flags/featureConfig.ts`

## Last verified date

2026-08-14.
