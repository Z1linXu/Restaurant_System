# Phase B Part 1 Implementation Evidence

Prepared: 2026-08-16

## Status

```text
PHASE_B_OWNER_IMPLEMENTATION_APPROVAL = GRANTED_FOR_PART_1
PHASE_B_PART1_REPOSITORY_IMPLEMENTATION = MERGED_TO_MAIN_VIA_PR_161
PHASE_B_PART1_IMPLEMENTATION_MERGE_SHA = 4ace6988dd4793b3b7259bf7455289af24f13d4b
PHASE_B_PART1_FINAL_REVIEW_BY_AGENT_6 = ACCEPT
PHASE_B_PART1_STAGING_PREFLIGHT = PASS
PHASE_B_PART1_STAGING_DEPLOYMENT = PASS
PHASE_B_PART1_STAGING_AUTOMATED_ACCEPTANCE = PENDING
PHASE_B_PART1_RUNTIME_REPAIR = V19_SORT_ORDER_FALLBACK_DEPLOYED_HEALTH_PASS
PHASE_B_PART1_ACCEPTANCE_TOOLING_REPAIR = JQ_FALLBACK_DEPLOYED_VALIDATE_PASS
PHASE_B_PART1_ACCEPTANCE_RUNTIME_CREDENTIAL_GATE = SUPERSEDED_FOR_PRODUCT_AUTH_BY_OWNER_DECISION
PHASE_B_AUTHORIZATION_PREFIX_DRIFT = REPAIR_CANDIDATE_AGENT6_ACCEPT_PENDING_PR_STAGING_ACCEPTANCE
PHASE_B_AUTH_PREFIX_REPAIR_AGENT6 = PHASE_B_AUTH_PREFIX_REPAIR_ACCEPT
PHASE_B_PART1_OWNER_ACCEPTANCE = PENDING
PHASE_B_PART2 = NOT_STARTED
PRODUCTION = NO_MUTATION
```

Fresh source authority at implementation start:

```text
origin/main = 0de03c773ef04594e7d737c6bccdf6f607692eca
branch = codex/phase-b-part1-owner-store-provisioning
```

Merge authority:

```text
PR = #161
merged_at = 2026-08-16T02:50:24Z
merge_commit = 4ace6988dd4793b3b7259bf7455289af24f13d4b
```

Runtime repair evidence:

```text
docs_sync_merge = 908902b41d4d4b34b0ce663da4a7dd75800cdb36
release_env_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-release-env-908902b41d4d4b34b0ce663da4a7dd75800cdb36.txt
preflight_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-preflight-908902b41d4d4b34b0ce663da4a7dd75800cdb36.txt
preflight_sha256 = 665fa21c7fb18c0846f7f3f98f4b16259902e8d48ce7ac856d78c939de865605
deploy_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-deploy-908902b41d4d4b34b0ce663da4a7dd75800cdb36.txt
deploy_sha256 = b2d9401e8b5dae779cd2ecf7104ccaaaa4d86214c11439267c2f7258104bc2ae
health_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-health-r2-908902b41d4d4b34b0ce663da4a7dd75800cdb36.txt
health_sha256 = aef44a9a0b167dbf1b1c87f40454c022ce9ed68713d1fce8365eaf8b63075719
blocker = Flyway V19 null option sort_order from source Profile options without sort_order
flyway_runtime_state = V1-V18 successful, no V19 successful row
repair = coalesce source option sort_order to JSON ordinality before content seed and table insert
```

V19 repair deploy evidence:

```text
repair_merge_sha = 397bf09d01371961f6a438db67fd069afa7ed049
candidate_import_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-candidate-import-397bf09d01371961f6a438db67fd069afa7ed049.txt
candidate_import_sha256 = ed056da3556cc571d18886e85c75b4b45bb87dbe1b86a98a631d4a941bdc6d55
release_env_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-release-env-397bf09d01371961f6a438db67fd069afa7ed049.txt
release_env_sha256 = b69066aa9ab50debd4ed5057d2b64be4080d19ecb1175d9c798edad850c5c781
preflight_r2_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-preflight-r2-397bf09d01371961f6a438db67fd069afa7ed049.txt
preflight_r2_sha256 = a12231c644b0b1155a6af443c56ae88c0d6d510dc1e50a9424eed653e5f329ab
deploy_r2_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-deploy-r2-397bf09d01371961f6a438db67fd069afa7ed049.txt
deploy_r2_sha256 = 69200b5e1df781da20a29f1c5dce8b344edb471001c97dd0b20e01c3c423d4a7
health_r2_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-health-r2-397bf09d01371961f6a438db67fd069afa7ed049.txt
health_r2_sha256 = e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14
backend_log_flyway = V19 and V20 successfully applied; schema now v20
```

Acceptance gate drift found after health PASS:

```text
staging_host_jq = absent
tooling_repair_branch = codex/phase-b-part1-acceptance-jq-fallback
tooling_repair_scope = Phase B acceptance uses ops001-jq-compat.py fallback when jq is absent
runtime_stg005_credential_count = 0
runtime_legacy_credentials = owner, manager, staffA, staffB, a10_staff_*
bootstrap_request = STG005_20260809_R01 completed at old runtime 712531b941db92f4325a86126883706314f4cba5c with owner_user_id=1
conflict = historical governance says STG005 Owner credential ready, latest runtime evidence does not
required_next_gate = reviewed Staging STG005 Owner credential reconciliation before automated acceptance
```

Jq fallback repair deploy evidence:

```text
jq_fallback_merge_sha = 83741ea88e07bf6735462fb5f3816650b6db59b4
candidate_import_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-candidate-import-83741ea88e07bf6735462fb5f3816650b6db59b4.txt
candidate_import_sha256 = d37f5031f3ef0ed2821152ecf1de082aa5446a08abb4ecf1e5f389246ec58cbe
release_env_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-release-env-83741ea88e07bf6735462fb5f3816650b6db59b4.txt
release_env_sha256 = e19eb3306d5513be45fbb53a09d388be79f95bd1b97c0b2fb9b51a11184c2b9c
preflight_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-preflight-83741ea88e07bf6735462fb5f3816650b6db59b4.txt
preflight_sha256 = 3564abfd7c3e969b56b9a1d506a525d63d74248a5ff605c346bc304e3ea1b777
deploy_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-deploy-83741ea88e07bf6735462fb5f3816650b6db59b4.txt
deploy_sha256 = a1a2802c460186baadc20274d2c14436c73b0031548708b175f5bfc8a41f2a8e
health_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-health-83741ea88e07bf6735462fb5f3816650b6db59b4.txt
health_sha256 = e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14
acceptance_validate_evidence = /srv/restaurant-pos/staging/evidence/phase-b-part1-acceptance-validate-83741ea88e07bf6735462fb5f3816650b6db59b4.txt
acceptance_validate_sha256 = daa170b306ebf3b7b35abb96dfab0187cf256bdcd044cbc9b1ba921197999ec3
runtime_env_sha = 83741ea88e07bf6735462fb5f3816650b6db59b4
flyway = V20 with all rows success and V19 checksum 182579909
```

## Phase B STG005 Authorization Drift Analysis

Owner product decision:

```text
PHASE_B_PROVISIONING_AUTHORIZATION =
  authenticated principal
  + OWNER authority
  + active Organization Owner membership
  + correct Organization scope
PHASE_B_PROVISIONING_USERNAME_PREFIX_REQUIRED = NO
```

Fresh finding:

1. `STG005_` prefix requirements are defined in synthetic bootstrap/source
   fixture docs and tools, credential rotation/reconciliation tooling, and the
   historical Phase B Part 1 acceptance helper.
2. The prefix belongs to Staging synthetic bootstrap/fixture identity,
   acceptance-tooling convention and historical compatibility. It is not the
   Phase B product authorization contract.
3. The backend Phase B provisioning controller and service use feature flag,
   non-Production runtime gate, Owner authority and active Organization Owner
   membership. They do not check username prefix.
4. The blocking Phase B prefix check existed in
   `deployment/cloud/staging-phase-b-part1-acceptance.sh`.
5. Frontend `Access denied` comes from role/route gating or backend 403; no
   frontend username-prefix gate was found.
6. Fresh runtime read-only evidence shows the current `owner` credential is
   active, has role `OWNER`, active Organization Owner membership and the
   expected Organization scope for Organization `1`.
7. Removing prefix as a Phase B authorization condition affects acceptance
   tooling, jq fallback compatibility, tests and governance. It does not
   require backend or frontend product-code changes.
8. Explicit `STG005_` checks remain required for synthetic bootstrap and
   fixture tooling where the namespace itself is the safety contract.

Implemented local repair:

- Phase B Part 1 acceptance secret input now accepts any non-empty reviewed
  Owner login identifier, then verifies the login response is `OWNER`, belongs
  to the expected Organization and matches the authenticated username.
- `ops001-jq-compat.py` preserves explicit `startswith("STG005_")` checks for
  synthetic filters while supporting the Phase B non-empty login filter.
- Backend authorization regression tests prove authority/membership, not
  naming convention, determine provisioning access.

Local validation:

```text
bash deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh = PASS
mvn -q -f backend/pom.xml -Dtest=OwnerOrganizationAuthorizationServiceTest,OwnerStoreProvisioningControllerTest test = PASS
mvn -q -f backend/pom.xml test = PASS
npm test = PASS
npm run build = PASS
npm run lint = FAIL_EXISTING_FRONTEND_LINT_DEBT_UNRELATED_TO_AUTH_PREFIX_REPAIR
for test_script in deployment/cloud/tests/*.sh; do bash "$test_script" || exit $?; done = PASS
Agent 6 = PHASE_B_AUTH_PREFIX_REPAIR_ACCEPT
```

Agent 6 auth-prefix repair review:

```text
PHASE_B_AUTH_PREFIX_REPAIR_ACCEPT
```

Agent 6 found no blocking issues and confirmed product authorization remains
principal/role/membership/scope based, Phase B acceptance no longer requires
`STG005_`, explicit synthetic guards remain, the requested test matrix is
covered, and no Production mutation/deploy/restart/Flyway action was
introduced.

## Implemented Scope

Phase B Part 1 repository implementation adds the Owner UI-driven path to
create a synthetic, non-active Store from the approved Chain Master Menu and
Store Profile contracts. The implementation is Staging-only until exact-SHA
deployment and automated acceptance pass.

Implemented boundaries:

- canonical `LANZHOU_CHAIN_MASTER_MENU/v1` persistence and immutable published
  version identity;
- `ST_DENIS_CANONICAL_PROFILE/v2` reference to the published Master version and
  A11 `PRINTING_DISPLAY_RULES`;
- Owner-only Organization-scoped provisioning API;
- exact Organization Owner membership enforcement on both catalog and create;
- backend idempotency ledger and replay behavior;
- one-time materialization of Store, modules, stations, categories, items,
  options, parent option relationships, pricing policy, combo configuration
  and Store-owned A11 printing display rules;
- Store-local Master mapping rows for Master-derived and Store-only menu rows;
- validation fixture hygiene in Owner workspace/overview/store-switcher
  surfaces;
- Owner Dashboard Create New Store panel;
- Store-local item/category deactivation semantics, including category
  effective availability without destructive child item rewrites;
- Phase B Part 1 Staging acceptance harness that uses the same canonical
  provisioning API as the Owner UI.

Explicitly not implemented:

- final Store activation;
- final staff credential delivery;
- physical printer endpoint binding;
- Pad pairing;
- Master v2 sync/diff;
- Chinatown or Sainte-Catherine creation;
- Production deployment, restart, Flyway, Store creation or data mutation.

## Schema / Flyway

New additive migrations:

- `V18__add_phase_b_master_menu_provisioning.sql`
- `V19__seed_phase_b_master_menu_profile_v2.sql`
- `V20__align_phase_b_provisioning_validation_status.sql`

V18 adds:

- `stores.store_kind`, `lifecycle_status`, `provisioning_source` and
  Profile/Master provenance columns;
- `chain_master_menus`;
- `chain_master_menu_versions`;
- `chain_master_menu_categories`;
- `chain_master_menu_products`;
- `chain_master_menu_options`;
- `store_menu_master_mappings`;
- `owner_store_provisioning_requests`;
- additive `PRINTING_DISPLAY_RULES` support in
  `store_profile_artifacts.artifact_type`;
- published Master version immutability trigger.

V19 publishes:

```text
master_menu_key = LANZHOU_CHAIN_MASTER_MENU
master_menu_version = v1
fingerprint_sha256 = e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7
source_reference = ST_DENIS_CANONICAL_PROFILE/v1:MENU_TEMPLATE/v1
```

V19 also creates `ST_DENIS_CANONICAL_PROFILE/v2` as `READY`, preserving v1 and
adding a Master reference plus `PRINTING_DISPLAY_RULES/v1`.

V20 aligns provisioning validation status to:

```text
PENDING, PASS, WARNING, BLOCKING, FAILED
```

The Flyway manifest is updated through V20 in
`deployment/cloud/ops001-flyway-checksums.txt`.

## API

New Owner-only endpoint root:

```text
/api/v1/owner/organizations/{organizationId}/phase-b/store-provisioning
```

Contracts:

- `GET /catalog` returns the enabled Profile/Master selection:
  `ST_DENIS_CANONICAL_PROFILE/v2` and `LANZHOU_CHAIN_MASTER_MENU/v1`.
- `POST /` requires `Idempotency-Key`, Owner authorization, Organization
  ownership, non-Production runtime gate and Platform capability.
- The create response returns provisioning request ID, Store ID, status,
  replay flag, validation status, result/error code and materialization counts.

The API defaults missing Profile/Master fields to the approved Part 1 values
and rejects fingerprint mismatches.

## Materialization

The materializer creates one non-active Store:

```text
stores.status = inactive
store_kind = VALIDATION_FIXTURE
lifecycle_status = READY_FOR_REVIEW
provisioning_source = PHASE_B_OWNER_PROVISIONING
printing_enabled = true
printing_mode = MOCK
```

The Store receives new local surrogate IDs. Store-local rows retain Master
identity through `store_menu_master_mappings`; Store-only rows are recorded
with `origin = STORE_ONLY` and no Master key.

The initial Part 1 runtime print mode is `MOCK` so Staging remains safe while
logical A11 printing display rules are still materialized and independently
editable.

## Owner UI

Owner Dashboard now exposes a Create New Store panel for Organization Owners.
The UI uses human-readable Store name/code entry and the approved
Profile/Master catalog response. It does not ask the Owner for internal DB IDs
or manual idempotency keys.

The UI only offers the create panel for Organizations where the current user
has `OWNER` role. Backend enforcement remains authoritative and rejects
cross-Organization or non-Owner access for both catalog and create.

Owner workspace, overview, Store Context and Store Switcher expose lifecycle
and provenance fields. Non-Phase-B validation fixtures are hidden from normal
Owner Store lists by default; Phase B validation Stores remain explicitly
identified as validation fixtures and non-active.

## Validation Harness

New Staging helper:

```text
deployment/cloud/staging-phase-b-part1-acceptance.sh
```

The helper:

- validates exact release SHA and Staging env binding;
- requires prior read-only Staging preflight evidence;
- consumes scoped OPS approval;
- accepts login/idempotency only through inherited private FD;
- authenticates the approved Organization Owner;
- calls the canonical Phase B Owner provisioning API;
- replays the same idempotency key and proves no duplicate Store/request;
- verifies Store lifecycle/provenance and `MOCK` runtime mode;
- verifies menu/materialization counts, parent option remap and local ID
  difference;
- proves Store-local item/category deactivation, Store-only item, pricing,
  combo and A11 printing-rule independence;
- verifies Store Context/module state and validation fixture hygiene;
- proves source Store, Master Menu and Store Profile signatures are unchanged.

The helper does not call legacy onboarding, legacy menu-clone, Chinatown or
Production operations.

## Local Verification

Latest repository-side verification after the Agent 6 first implementation
review repairs and before Agent 6 final re-review:

```text
full_repository_verification_utc = 2026-08-16T02:41:58Z
mvn -q test = PASS
npm test = PASS (23 files, 114 tests)
npm run build = PASS
npx eslint <Phase B touched frontend files> = PASS
deployment/cloud/tests/test_staging_guard.sh = PASS
deployment/cloud/tests/test_staging_server_preflight.sh = PASS
deployment/cloud/tests/test_staging_deploy_cli_state.sh = PASS
deployment/cloud/tests/test_staging_runtime_evidence.sh = PASS
deployment/cloud/tests/test_staging_acceptance_readiness.sh = PASS
deployment/cloud/tests/test_staging_synthetic_acceptance.sh = PASS
deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh = PASS
git diff --check = PASS
Flyway manifest CRC check = PASS (count=20, max_version=20)
```

Additional acceptance-helper SQL signature repair:

```text
repair_utc = 2026-08-16T02:45:19Z
acceptance helper Store combo signature now derives default status from
store_combo_groups.default_component_code instead of the API-only is_default
field.
deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh = PASS
deployment/cloud/tests/test_staging_guard.sh through
deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh = PASS
git diff --check = PASS
```

Focused verification immediately after the final safety repairs:

```text
mvn -q -Dtest=OwnerStoreProvisioningControllerTest,OwnerStoreProvisioningServiceImplTest,OwnerStoreProvisioningMaterializerContractTest test = PASS
npm test = PASS (23 files, 114 tests)
npx eslint OwnerDashboardPage.tsx and Phase B touched frontend services/routes = PASS
```

Known residual verification note:

```text
frontend npm run lint = FAILS on pre-existing unrelated lint debt
```

Observed unrelated lint debt remains in legacy files such as
`RequireAuth.tsx`, `TakeoutEntryDialog.tsx`,
`PrintWorkerHealthBanner.tsx`, `OrderLineItemRow.tsx`,
`OrderDetailPanel.tsx` and `reportUtils.ts`.

## Staging / Runtime State

Staging deploy has not yet been executed for this repository implementation.
Before the first Phase B deploy, the authorized sequence still requires fresh
runtime preflight to resolve the documented A11 caveat:

```text
checked-in stable Staging authority = ad4572759e01b5546ec59af24aa36b09e5c2dd00 / V16
A11 evidence records V17 apply + startup repair history
fresh runtime preflight must confirm actual app SHA, Flyway ledger, failed
migration count, health, WebSocket, printing mode and Store identity
```

## Agent 6

Agent 6 plan review has been satisfied for the package plan during this Part 1
loop.

Agent 6 first implementation final review returned `PHASE_B_PART1_BLOCK` with
three bounded findings:

- the acceptance helper referenced `parent_option_key` instead of the actual
  `parent_master_option_key` schema column;
- the helper resolved `chain_master_menu_versions` by version/status only,
  without filtering by Organization, Master key and fingerprint;
- the service-level create path enforced Owner membership but needed its own
  Platform capability and non-Production runtime gate in addition to controller
  checks.

All three findings are repaired. The acceptance helper now joins
`chain_master_menus`, filters by Organization, Master key and fingerprint, and
uses `parent_master_option_key`; the helper unit test asserts those guards. The
service create path now fail-closes on Platform capability and Phase B runtime
gate before materialization, with focused service coverage.

Main-agent follow-up also repaired an acceptance-helper runtime SQL mismatch:
`is_default` is a combo API response field, not a `store_combo_components`
schema column. Store combo source/target signatures now derive default state
from `store_combo_groups.default_component_code`, and the helper test prevents
the DB-invalid expression from returning.

Final Agent 6 Part 1 re-review:

```text
PHASE_B_PART1_ACCEPT
```

Agent 6 accepted the package after confirming canonical Owner provisioning,
no legacy clone/onboard acceptance path, Organization-scoped Master lookup,
Profile/Master immutability, exact Owner membership enforcement, service-level
Platform/runtime gates, Store-local materialization and overrides, `MOCK`
printing, and no Part 2/activation/hardware/staff credential/Production scope
creep.

Agent 6 non-blocking residual risks:

- Staging runtime work still requires PR/merge, fresh preflight, exact-SHA
  deploy and automated acceptance.
- V18/V20 are non-data-destructive but replace constraints, so “additive” has
  that constraint-replacement nuance.
- UI browser retry after ambiguous failure may surface a duplicate-code UX
  conflict even though backend idempotency and acceptance replay are correct.
- Full frontend lint retains unrelated pre-existing lint debt.

## Unique Stop Target

After PR/merge, fresh Staging preflight, exact-SHA Staging deploy and
automated acceptance pass, stop at:

```text
PHASE_B_PART1_CREATE_STORE_AND_MASTER_MENU_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```
