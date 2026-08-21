# Phase B Part 1 Implementation Audit

Prepared: 2026-08-16

Status:

```text
PHASE_B_PART1_IMPLEMENTATION_AUDIT = COMPLETE_FOR_PLAN_REVIEW
PHASE_B_OWNER_IMPLEMENTATION_APPROVAL = GRANTED_FOR_PART_1
PHASE_B_PART1_IMPLEMENTATION = NOT_STARTED
PRODUCTION = NO_MUTATION
```

Current note: this file is the pre-implementation audit authority. The current
repository implementation state is recorded in
[PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md)
and supersedes the `NOT_STARTED` implementation status above for current
handoff purposes.

## Fresh Authority

Fresh recovery command:

```text
git fetch origin --prune
```

Repository authority:

```text
origin/main = 0de03c773ef04594e7d737c6bccdf6f607692eca
HEAD = 0de03c773ef04594e7d737c6bccdf6f607692eca
branch = codex/phase-b-part1-owner-store-provisioning
latest main subject = Fix A11 print job fingerprint schema mapping (#160)
```

Checked-in runtime authority still records stable Staging as
`ad4572759e01b5546ec59af24aa36b09e5c2dd00` / Flyway `V16`. A11 evidence
records that V17 was applied during the A11 deployment attempt before the final
startup mapping repair. Therefore the first Phase B Staging deployment must
perform a fresh runtime preflight and must not assume either V16 or V17 from
stale docs.

Production authority remains documentation-only and unchanged:

```text
PRODUCTION_RC = RC-THREE-RELIABILITY-20260812-3EC4D88
PRODUCTION_APP_SHA = 3ec4d88a47f68e05b92d9246bfd63af2d1f297f9
PRODUCTION_FLYWAY = V10
PRODUCTION_MUTATION = FORBIDDEN
```

Owner authority supersedes older Phase B waiting text for Part 1 only:

```text
PHASE_A10_AUTOMATED_ACCEPTANCE = PASS
PHASE_A11_OWNER_ACCEPTANCE = PASS
PHASE_A11_5_CHAIN_MASTER_MENU_DESIGN = PASS
PHASE_B_OWNER_IMPLEMENTATION_APPROVAL = GRANTED_FOR_PART_1
```

Part 1 stop target:

```text
PHASE_B_PART1_CREATE_STORE_AND_MASTER_MENU_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

## Scope Readiness Summary

Phase A provides reusable Store-scoped building blocks: Store/Organization
membership, Store modules, Store Profile contracts, St-Denis canonical profile
artifacts, menu management, pricing, combo configuration, hardware readiness,
module gating and A11 printing display rules.

Phase B Part 1 is not implemented in current code. There is no Chain Master
Menu persistence, no published Master Menu version, no Profile to Master
reference, no Store materialization writer, no canonical Owner Create New Store
UI, no provisioning idempotency ledger, and no validation-fixture distinction
in the normal Owner Store list.

## Audit Matrix

| Area | Current state | Classification | Part 1 disposition |
| --- | --- | --- | --- |
| Organization selection | Organizations and memberships exist. Owners can access Stores through active Organization membership. | `REUSABLE` | Reuse organization membership and require exact Owner membership for provisioning. |
| Owner-only authorization | `OwnerOrganizationAuthorizationService` enforces active `OWNER` membership and deliberately excludes ADMIN bypass. | `REUSABLE` | Use this boundary for Phase B create/provision APIs. |
| Store entity | `stores` has identity, organization, status, printing runtime fields and menu revision. | `PARTIAL` | Add lifecycle/provisioning/provenance/fixture-safe fields without changing existing `active` semantics. |
| Store lifecycle | Existing `status` is mostly `active`/`inactive`; no provisioning state. | `MISSING` | Add safe Part 1 lifecycle state so generated Stores are non-active but reviewable. |
| Store visibility / fixture hygiene | Owner Home and workspace list expose accessible stores without validation-fixture distinction. | `BLOCKING` | Add canonical real/validation visibility and filter normal Owner UX by default. |
| Store Profile contract | A4 tables, validators, immutable versions and read APIs exist. | `REUSABLE` | Preserve historical v1; create a Phase B-ready profile version if needed. |
| St-Denis canonical profile | V15 seeds `ST_DENIS_CANONICAL_PROFILE/v1` with 39 items, 380 options, 11 parent option relationships, pricing, combo, stations and logical topology. | `REUSABLE_SOURCE` | Use reviewed artifacts as the initial Master v1 source; do not query Production or live Store rows. |
| Profile artifact whitelist | V14 allows original artifact types only; A11 says post-A11 versions require `PRINTING_DISPLAY_RULES`. | `BLOCKING` | Additive migration must extend the check constraint before Profile v2 persists A11 printing rules. |
| Chain Master Menu | No schema, repositories, service or API. | `MISSING` | Implement Organization-scoped Master Menu and immutable published version. |
| Master product identity | V15 profile has `item_ref`, `category_ref`, option refs; duplicate SKU examples exist. No master identity table. | `MISSING` | Use stable Master category/product/option keys, not SKU alone. |
| Master version fingerprint | Profile fingerprint exists; no Master fingerprint exists. | `MISSING` | Deterministic Master version fingerprint required. |
| Profile to Master reference | Current profile references menu template artifact, not Master Menu version. | `MISSING` | Create Phase B-ready profile reference to `LANZHOU_CHAIN_MASTER_MENU/v1`. |
| Store menu materializer | Only dry-run validator and legacy source Store clone exist. | `MISSING` | Implement one-time Profile -> Master -> Store materialization writer. |
| Legacy onboarding | `/owner/.../stores/onboard` requires source store and staff/password; creates inactive Store only. | `LEGACY` | Do not use as canonical Part 1 path; preserve/gate existing behavior. |
| Legacy menu clone | `/menu-clone` clones from source store with source_store_id and profile code. | `LEGACY` | Do not use as canonical Part 1 path; selected remap mechanics may inspire implementation only. |
| Idempotency | Existing order/onboarding/menu-clone ledgers prove patterns, but no Phase B provisioning ledger exists. | `PARTIAL` | Add provisioning request ledger keyed by Organization and Idempotency-Key. |
| Store modules | `store_modules` schema/service/gating exists and missing modules fail closed. | `REUSABLE` | Materialize all 11 module rows from Profile defaults with `PROFILE_DEFAULT` source. |
| Menu categories/items/options | Store-scoped rows and management APIs exist. | `REUSABLE` | Materialize new surrogate IDs and write Master mapping rows. |
| Parent options | Existing option model supports `parent_option_id`; profile has local parent refs. | `REUSABLE` | Remap parent refs within new Store during materialization. |
| Stations | Store-scoped rows, station types and management APIs exist. | `REUSABLE` | Materialize station refs required by menu and printing topology. |
| Pricing policy | `store_pricing_policies` and service exist. | `REUSABLE` | Materialize independent Store policy from profile/defaults. |
| Combo configuration | V16 dynamic combo groups/components exist. | `REUSABLE` | Materialize independent Store combo groups/components from profile/defaults. |
| Printing Display Rules | V17 tables/service/UI exist; default creation is private to A11 service path. | `PARTIAL` | Materialize Store-owned published rule set/revision during provisioning. |
| Menu revision/cache | Store-scoped menu revision and catalog hash exist. | `REUSABLE` | Materialization and local edits must use existing revision/cache contracts. |
| Owner Home UI | Store cards/actions exist; no Create New Store flow or lifecycle display. | `PARTIAL` | Add human-readable create flow, lifecycle/provenance display and fixture filtering. |
| Menu review/deactivation UI | Existing Menu Management supports item create/edit/enable/disable, category/station controls, pricing and combo. | `REUSABLE` | Reuse for review/deactivation; add provenance/origin display only if needed for Part 1 clarity. |
| Store-only item | Existing Menu Management can create a new item in a Store. | `REUSABLE_WITH_TRACE_GAP` | Add local origin mapping so Store-only rows are distinguishable from Master-derived rows. |
| Validation | Store Profile dry-run and module validation exist; no Part 1 provisioning validator. | `MISSING` | Add machine-readable PASS/WARNING/BLOCKING validation. |
| Staging runtime preflight | Existing staging scripts and evidence patterns exist. | `REUSABLE` | Must fresh inspect app SHA, Flyway ledger, failures, health, WebSocket, printing mode and Store identity before deploy. |
| Production | Production RC and V10 identity are documented. | `OUT_OF_SCOPE` | No Production read/mutation/deploy/restart/Flyway. |
| Staff credentials | Legacy onboarding can provision staff but Part 1 does not require final staff credential delivery. | `DEFERRED_PART2` | Owner access uses Organization membership; final staff provisioning deferred unless needed for Part 1. |
| Physical hardware | A8 logical/hardware readiness contract exists; physical binding is separate. | `DEFERRED_PART2` | No printer IP, Pad pairing or physical test in Part 1. |
| Activation | Active Store workflow not implemented. | `DEFERRED_PART2` | New Store remains non-active / review lifecycle at Part 1 stop. |

## Blocking Gaps Before Coding

The following gaps must be included in the first implementation packages:

1. Additive schema for Chain Master Menu, Master version identity, Store
   provenance/lifecycle, provisioning idempotency, Master-to-local mappings and
   `PRINTING_DISPLAY_RULES` profile artifact support.
2. Published `LANZHOU_CHAIN_MASTER_MENU/v1` sourced from reviewed V15 profile
   artifacts, not from Production or live Staging Store rows.
3. Phase B-ready Profile reference to the published Master version while
   preserving immutable historical `ST_DENIS_CANONICAL_PROFILE/v1`.
4. Idempotent materialization transaction that creates one non-active Store and
   all required module/menu/pricing/combo/printing rows with new local IDs.
5. Owner Create New Store API and UI that hide internal identifiers and expose
   human-readable Profile/Master choices.
6. Validation fixture hygiene so historical validation Stores do not appear as
   normal Owner restaurants.

## Drift / Inconsistency Found

- Current checked-in governance before this Part 1 sync still says Phase B is
  waiting for explicit Owner approval. The latest Owner authorization grants
  implementation approval for Part 1 only. This is not a technical conflict;
  it is a governance sync update.
- Checked-in Staging authority records stable V16, while A11 evidence records
  a V17 apply and startup repair trail. This must be resolved by fresh Staging
  runtime preflight before the first Phase B deploy.
- A11 requires post-A11 Profile versions to include `PRINTING_DISPLAY_RULES`,
  but the V14 artifact type check constraint still excludes that type.
- A11.5 planned Chain Master architecture is not implemented in code/schema.

## Authority Files Used

- `docs/governance/runtime/ALIVE_RUNTIME_PLANBOOK.md`
- `docs/governance/runtime/CURRENT_HANDOFF.md`
- `docs/governance/AGILE_LOOP_OPERATING_MODEL.md`
- `docs/governance/agile/FINAL_PRODUCTIZATION_PLANBOOK.md`
- `docs/governance/FEATURE_BACKLOG.md`
- `docs/governance/KNOWN_ISSUES_BACKLOG.md`
- `SYSTEM_DOCUMENTATION.md`
- `doc/API.md`
- `docs/governance/agile/PHASE_A1_MODULE_CATALOG.md`
- `docs/governance/agile/PHASE_A2_MODULE_DEPENDENCY_GRAPH.md`
- `docs/governance/agile/PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION.md`
- `docs/governance/agile/PHASE_A4_STORE_PROFILE_CONTRACT.md`
- `docs/governance/agile/PHASE_A5_ST_DENIS_CANONICAL_PROFILE.md`
- `docs/governance/agile/PHASE_A5_5_MENU_MANAGEMENT_CONFIGURABILITY_EVIDENCE.md`
- `docs/governance/agile/PHASE_A8_HARDWARE_CAPABILITY_CONTRACT_EVIDENCE.md`
- `docs/governance/agile/PHASE_A9_LEGACY_COMPATIBILITY_LEDGER.md`
- `docs/governance/agile/PHASE_A10_FINAL_MODULAR_PRODUCTIZATION_ACCEPTANCE_EVIDENCE.md`
- `docs/governance/agile/PHASE_A11_PRINTING_RULE_CONFIGURATION_IMPLEMENTATION_EVIDENCE.md`
- `docs/governance/agile/PHASE_A11_5_CHAIN_MASTER_MENU_DESIGN.md`
- `docs/governance/../../governance/contracts/CHAIN_MASTER_MENU_CONTRACT.md`
- `docs/governance/../../governance/contracts/CHAIN_MASTER_MENU_VERSIONING_CONTRACT.md`
- `docs/governance/../../governance/contracts/MASTER_PRODUCT_IDENTITY_CONTRACT.md`
- `docs/governance/../../governance/contracts/STORE_MENU_MATERIALIZATION_CONTRACT.md`
- `docs/governance/../../governance/contracts/STORE_MENU_LOCAL_OVERRIDE_CONTRACT.md`
- `docs/governance/../../governance/contracts/PHASE_B_MENU_PROVISIONING_CONTRACT.md`
- `docs/governance/agile/MASTER_MENU_SCHEMA_OPTIONS.md`
- `docs/governance/agile/PHASE_B_CHAIN_MENU_ENTRY_READINESS.md`
- `docs/archive/architecture/phase-a-staging-2026-08-14/README.md`
- `docs/archive/architecture/phase-a-staging-2026-08-14/03_MODULE_PROFILE_ARCHITECTURE.md`
- `backend/src/main/resources/db/migration/V13__add_store_modules.sql`
- `backend/src/main/resources/db/migration/V14__add_store_profiles.sql`
- `backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql`
- `backend/src/main/resources/db/migration/V16__add_dynamic_menu_management_configuration.sql`
- `backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql`
- `backend/src/main/java/com/restaurant/system/user/entity/Store.java`
- `backend/src/main/java/com/restaurant/system/common/auth/OwnerOrganizationAuthorizationService.java`
- `backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java`
- `backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java`
- `backend/src/main/java/com/restaurant/system/owner/service/impl/OwnerStoreOnboardingServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/owner/service/impl/StoreMenuCloneTransactionServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceImpl.java`
- `frontend/src/features/owner-home/OwnerDashboardPage.tsx`
- `frontend/src/features/owner-admin/MenuManagementPage.tsx`
- `frontend/src/features/store/StoreSwitcher.tsx`
