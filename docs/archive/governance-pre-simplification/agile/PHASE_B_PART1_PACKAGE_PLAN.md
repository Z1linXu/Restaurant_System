# Phase B Part 1 Package Plan

Prepared: 2026-08-16

Status:

```text
PHASE_B_PART1_PACKAGE_PLAN = ACCEPTED_BY_AGENT_6
PHASE_B_OWNER_IMPLEMENTATION_APPROVAL = GRANTED_FOR_PART_1
PHASE_B_PART1_IMPLEMENTATION = COMPLETE_PENDING_PR_MERGE
```

Current note: this file records the package plan and target runtime exit
state. The current repository implementation and Agent 6 final review state
are recorded in
[PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md).

This package plan is derived from latest repository authority, the A11.5
contracts and the actual code/schema audit. It does not mechanically reuse old
chat package names or B1/B2/B3 numbering.

## Success Target

Part 1 must deliver a Staging-only Owner flow where an authorized Owner can
create a clearly synthetic/non-active Store from the UI, materialize the full
Chain Master Menu into independent Store-scoped rows, review/deactivate local
menu content, and stop for Owner manual retest.

Required stop:

```text
PHASE_B_PART1_CREATE_STORE_AND_MASTER_MENU_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

Part 1 must not activate a real Store, create Chinatown/Sainte-Catherine, bind
physical hardware, perform final staff credential delivery or touch Production.

## Package 0 - Governance Sync and Plan Review

Goal:

- Sync the latest Owner Part 1 authorization into living governance.
- Record fresh implementation audit and package plan.
- Obtain Agent 6 `PHASE_B_PART1_PLAN_REVIEW`.

Schema impact:

- None.

Backend impact:

- None.

Frontend impact:

- None.

Tests:

- Documentation consistency and `git diff --check` before PR.

Dependency:

- Fresh authority recovery and audit.

Staging requirement:

- No deploy or runtime mutation.

Rollback/risk:

- Documentation-only. Main risk is overstating implementation as complete.

Acceptance:

- Agent 6 returns `PHASE_B_PART1_PLAN_ACCEPT` or exact blockers.

## Package 1 - Phase B Schema, Identity and Version Contracts

Goal:

- Add additive Phase B persistence for Master Menu, immutable Master version,
  stable Master category/product/option identity, Store provenance/lifecycle,
  validation-fixture visibility and provisioning idempotency.
- Add the bounded A11 profile artifact whitelist repair for
  `PRINTING_DISPLAY_RULES`.

Schema impact:

- Next available Flyway migration, expected `V18` unless fresh main adds a
  newer migration first.
- Add `chain_master_menus`.
- Add `chain_master_menu_versions` with published/immutable content and
  deterministic `fingerprint_sha256`.
- Add normalized Master category/product/option identity tables.
- Add `store_menu_master_mappings` for local category/item/option rows,
  Master-derived rows and Store-only rows.
- Add `owner_store_provisioning_requests` for Organization-scoped
  idempotency/retry evidence.
- Add additive Store columns for lifecycle, visibility/provisioning kind and
  Profile/Master provenance while preserving existing `stores.status`
  compatibility.
- Extend `store_profile_artifacts.artifact_type` to allow
  `PRINTING_DISPLAY_RULES`.

Backend impact:

- Add entities/repositories for the new tables.
- Add migration contract tests for additive shape, check constraints,
  immutability and no prohibited data.

Frontend impact:

- None in this package.

Tests:

- Migration contract test.
- Master identity table constraint test.
- Profile artifact whitelist test.
- Published Master/Profile immutability test.

Dependency:

- Package 0 Agent 6 plan acceptance.

Staging requirement:

- No Staging deploy until schema package is merged with implementation packages
  and local/Flyway rehearsal passes.

Rollback/risk:

- Additive schema only. Risk is incomplete constraints or changing existing
  Store `status` semantics. Use additive lifecycle fields and avoid destructive
  rewrites.

Acceptance:

- Empty-database Flyway rehearsal reaches the new version.
- Existing V17 schema upgrades cleanly.
- Existing Store/Profile/Menu/Printing tests pass.

## Package 2 - Initial Master v1 and Phase B-ready Profile Integration

Goal:

- Create `LANZHOU_CHAIN_MASTER_MENU/v1` from reviewed
  `ST_DENIS_CANONICAL_PROFILE/v1` menu/profile artifacts, not live Production
  or Staging Store rows.
- Create or expose a Phase B-ready `ST_DENIS_CANONICAL_PROFILE` version that
  references the published Master Menu version and includes A11
  `PRINTING_DISPLAY_RULES`.

Schema impact:

- Seed or deterministic bootstrap data for Master v1 and, if required by the
  validator, `ST_DENIS_CANONICAL_PROFILE/v2`.
- Preserve immutable historical v1 rows and artifacts.

Backend impact:

- Add Master catalog/fingerprint service.
- Add Profile-to-Master reference validation.
- Reuse `StoreProfileCanonicalJson` for deterministic canonicalization.
- Ensure duplicate SKU cases are disambiguated by Master product key/item ref.

Frontend impact:

- None yet, except future create flow will consume the new read response.

Tests:

- Master v1 count/fingerprint test.
- Master product identity test for representative products including duplicate
  SKU cases.
- Profile v2 validation test with `PRINTING_DISPLAY_RULES`.
- No Production/live Store source reference scan.

Dependency:

- Package 1 schema.

Staging requirement:

- Included in first exact-SHA Staging deploy only after packages are merged and
  preflight confirms safe runtime state.

Rollback/risk:

- Main risk is treating reviewed artifact content as live clone data. Tests
  must prove source provenance is Profile artifact based and contains no
  source Store DB IDs.

Acceptance:

- Master v1 is published/immutable with deterministic fingerprint.
- Profile integration validates.
- Master/Profile source provenance points to reviewed artifacts.

## Package 3 - Idempotent Store Materialization and Validator

Goal:

- Implement the canonical one-time materialization transaction:
  Owner request -> non-active Store -> module rows -> station/category/item/
  option rows -> pricing policy -> combo config -> printing display rules ->
  Master/local mappings -> validation evidence.

Schema impact:

- Uses Package 1 tables.
- No further schema unless audit finds a true blocker.

Backend impact:

- Add `OwnerStoreProvisioningService` and DTOs.
- Add request fingerprinting and reservation/complete/fail semantics.
- Add materializer services for modules, stations, menu graph, pricing, combo
  and printing rules.
- Add `PhaseBPart1ProvisioningValidator` returning `PASS`, `WARNING` or
  `BLOCKING`.
- Store remains non-active/review lifecycle; no final activation.
- Extract or add a safe public method to materialize default A11 printing
  display rules for a Store.

Frontend impact:

- None in this package.

Tests:

- Service tests for Owner-only organization authorization.
- Wrong Organization rejection.
- Idempotency replay does not duplicate Store/menu/modules/rules.
- New local IDs differ from source/profile examples while mapping to Master
  identities.
- Parent option remap.
- Module materialization and Store Context validity.
- Pricing, combo and printing rule independence.
- Master/Profile immutability after local edits.

Dependency:

- Packages 1 and 2.

Staging requirement:

- Automated acceptance will use this same backend path.

Rollback/risk:

- Main risks are silent partial success and duplicate rows on retry. Use request
  ledger state and fail closed with a visible failed provisioning state.

Acceptance:

- Materialized validation Store receives full menu/config and validation
  returns `PASS` or documented non-blocking `WARNING`.

## Package 4 - Owner Provisioning API and Acceptance Harness

Goal:

- Expose a canonical Owner-only API used by both UI and automated acceptance.
- Add a controlled Staging acceptance harness that creates
  `PHASE_B_VALIDATION_STORE_<unique>` through the real provisioning backend.

Schema impact:

- Uses provisioning ledger.

Backend impact:

- Add endpoints under Owner Organization scope, for example:
  - list available provisioning profiles and referenced Master Menu version;
  - create/provision Store with `Idempotency-Key`;
  - fetch provisioning result/validation details.
- Do not expose DB IDs as Owner-facing primary choices.
- Do not restore `/api/v1/admin/platform/stores` direct active create.

Frontend impact:

- Service-layer client can be added for UI package.

Tests:

- MockMvc controller tests.
- Idempotency header required/validated.
- 403 for non-owner and cross-Organization request.
- API response includes lifecycle/provenance/validation/result counts.
- Acceptance harness dry-run/unit tests.

Dependency:

- Package 3.

Staging requirement:

- Harness runs only after exact-SHA deploy and fresh preflight.

Rollback/risk:

- Main risk is a test-only bypass. Harness must call the same service/API path
  as the Owner UI.

Acceptance:

- Automated API-level create/provision path works in local tests and can be
  used from Staging acceptance tooling.

## Package 5 - Owner Create Store UI and Store List Hygiene

Goal:

- Build the first real Owner-facing Create New Store experience.
- Show lifecycle/provenance clearly.
- Hide validation fixtures from normal Owner operational Store cards by
  default.
- Reuse Menu Management for review/deactivation and Store-only item proof.

Schema impact:

- None beyond Package 1.

Backend impact:

- Extend Owner Overview / Workspace / Store Context DTOs with lifecycle,
  provisioning visibility and Profile/Master provenance as needed.
- Filter validation fixtures by default while preserving evidence rows.

Frontend impact:

- Add Create New Store entry in Owner Home.
- Organization selector, Store name/code fields, Profile selector, Master
  Menu/version display and create/progress/result UX.
- Generate idempotency key client-side for each submitted create attempt while
  backend remains authoritative.
- Show non-active lifecycle state so the Store is not mistaken for live.
- Link created Store to Menu Management for local review/deactivation.

Tests:

- Frontend service tests.
- Owner Home component tests if current harness supports them.
- Store list fixture filtering unit coverage.
- Store Context DTO compatibility tests.

Dependency:

- Package 4 API.

Staging requirement:

- Owner manual retest uses this UI.

Rollback/risk:

- Main risk is UI implying activation. Text/status must make non-active state
  obvious without exposing internals.

Acceptance:

- Owner can create a synthetic Store from UI and open its menu review surface
  without SSH/SQL/Codex.

## Package 6 - Regression, Staging Deploy and Final Part 1 Acceptance

Goal:

- Prove Part 1 end-to-end on exact-SHA Staging and stop at Owner retest gate.

Schema impact:

- Flyway rehearsal and Staging migration verification only.

Backend impact:

- Add final evidence writer or scripts if needed.
- No Production code path or mutation.

Frontend impact:

- Production build and targeted UI verification.

Tests:

- Backend full tests.
- Frontend full tests.
- Frontend build.
- Applicable lint / changed-file lint.
- Flyway local rehearsal.
- `git diff --check`.
- Secret/prohibited-data scan.
- Architecture anti-drift scan.
- Legacy hardcode scan for `SOURCE_STORE_ID`, Store ID 1, St-Denis/Chinatown
  conditionals and live source clone dependency.
- Automated Staging acceptance:
  - create validation Store through canonical provisioning path;
  - retry same request and prove no duplicates;
  - prove menu counts, parent relationships and Master mappings;
  - prove item/category deactivation, Store-only item, pricing, combo and
    printing-rule independence;
  - prove Store Context/module gating and no validation fixture leakage.

Dependency:

- Packages 1 through 5 merged to main.

Staging requirement:

- Before first deploy, run fresh runtime preflight for actual app SHA, Flyway
  ledger, failed migrations, containers, health, WebSocket, printing mode and
  Store identity.
- Deploy exact SHA to Staging only.
- Verify Flyway, backend, frontend, health, WebSocket, printing mode and Store
  identity after deployment.

Rollback/risk:

- If Staging is DB-ahead/app-behind in a safe additive way, document and
  proceed through reviewed tooling. If unexpected drift exists, fail closed and
  repair before deploy.
- No Flyway history edits, resets, Production mutation or physical printer
  contact.

Expected acceptance state after PR/merge, fresh Staging preflight, exact-SHA
deploy and automated acceptance:

```text
EXPECTED_PHASE_B_PART1_IMPLEMENTATION = COMPLETE
EXPECTED_PHASE_B_PART1_STAGING_AUTOMATED_ACCEPTANCE = PASS
EXPECTED_PHASE_B_PART1_OWNER_ACCEPTANCE = PENDING
PHASE_B_PART1_CREATE_STORE_AND_MASTER_MENU_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

## Agent 6 Review Points

Agent 6 should review this plan for:

- no live St-Denis clone or Store 1 dependency;
- Chain Master Menu contract consumption;
- Profile/Master immutability and versioning;
- safe Store lifecycle and no accidental activation;
- true backend idempotency;
- Owner-only Organization authorization;
- A11 printing rule materialization;
- fixture hygiene;
- Store isolation and local override safety;
- no Part 2 scope creep;
- Production untouched.
