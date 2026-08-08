# Post-Stack Ground Truth Audit

> Audit date: 2026-08-08, America/Toronto
>
> Repository base: `origin/main@33c6e3c52aa40793f6bb861101c16ccdd1b85b5b`
>
> Follow-up runtime access: STG-006 passive/read-only observation completed;
> see `STG-006_EXACT_MAIN_PREFLIGHT_EVIDENCE.md`
>
> Current decision after reviewed OPS-001 repository merge:
> `OPS-001_REPOSITORY_COMPLETE_STG-007_WAITING_FOR_OWNER_RUNTIME_APPROVAL`

## 1. Executive summary

PRs #61 through #70 and the independent handoff PR #71 are all ancestors of
the current `origin/main`; PR #72's audit is also `IN_MAIN`. PR #66 remains the independent Printer
Store-isolation repair even though it entered main between #65 and #67.

The overnight stack completed architecture, contracts, plans, the guarded
Synthetic St-Denis baseline, Staging acceptance tooling, and one concrete
Printer isolation repair. It did **not** implement a complete Generic Store
Provisioning Engine, concrete complete Store Profiles, reusable Staff/Table,
Printing, or Device provisioning writers, the Activation workflow, a
Production Release Candidate, or ACT-001.

STG-006 bound `33c6e3c...` and completed the authorized fresh passive preflight.
The retained Staging runtime remains isolated and healthy at `4397f995...` /
V8, and Production continuity was unchanged. Deployment and acceptance remain
`NO_GO` for immediate runtime execution. OPS-001 now closes the repository
release/env, restart/Flyway, and Owner/API tooling gaps after reviewed merge;
STG-007 still needs a new exact-main SHA and separate Owner runtime approvals.

## 2. Git and PR ground truth

| PR | Package | Merge commit | Current classification |
|---|---|---|---|
| #71 | Current Handoff navigation | `5baada03935e004d80af1e7a36fb7db39bd6abbb` | `IN_MAIN`; navigation only |
| #61 | Modular Architecture Foundation | `bbb1af9520c188b6ef6362e783284ba4001a7e63` | `IN_MAIN`; architecture only |
| #62 | STG-005B Synthetic St-Denis baseline | `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b` | `IN_MAIN`; repository capability |
| #63 | AL-003S Staging acceptance preparation | `732d77c89ff067982702426ff918d5e097e1d0fb` | `IN_MAIN`; operational tooling only |
| #64 | Generic Store Profile contract | `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6` | `IN_MAIN`; contract slice only |
| #65 | Staff/Table provisioning preparation | `8f58bcbfca253c1598b967f4d17c04c0be1cce5b` | `IN_MAIN`; plan only |
| #66 | Printer Store-isolation repair | `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3` | `IN_MAIN`; independent code repair |
| #67 | Printing provisioning preparation | `65e3d3ced2b5b05eb36d56ce67e475768ad19dff` | `IN_MAIN`; plan only |
| #68 | Device/Pad provisioning preparation | `9e93573be97cfd01a9ad3efe64d55827854c497a` | `IN_MAIN`; plan only |
| #69 | Store Activation workflow preparation | `dc682203b2b24bbdb453a5520b297b9051139f13` | `IN_MAIN`; plan only |
| #70 | Chinatown Production RC preparation | `645d4909625f70fc241d5468382d66a30a030fb1` | `IN_MAIN`; plan only |
| #72 | Post-stack Ground Truth audit | `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | `IN_MAIN`; governance audit only |

All listed merge commits are verified ancestors of current `origin/main`.
There is no `DRAFT_PR` or `STACKED_ONLY` package remaining in #61-#72.
`IN_MAIN` does not imply `DEPLOYED_TO_STAGING` or
`DEPLOYED_TO_PRODUCTION`.

## 3. Capability matrix

| Capability | Status | Ground truth and remaining boundary |
|---|---|---|
| Generic Provisioning Engine | `NOT_IMPLEMENTED` | No engine, module SPI/registry, parent request coordinator, or provisioning API exists. |
| Versioned Store Profile | `PARTIAL_IMPLEMENTATION` | Exact identity/composition/fingerprint/registry contract exists, but no complete Chinatown or St-Denis Store Profile is registered and no module registry proves referenced configuration. |
| Store Core | `PARTIAL_IMPLEMENTATION` | AL-002 creates an idempotent inactive, printing-disabled Store, but it is not Profile-driven and legacy Platform Admin/Seeder paths can still create `active` Stores. |
| Access/Staff | `PARTIAL_IMPLEMENTATION` | Onboarding transaction creates BCrypt credentials and Store memberships. There is no reusable Profile planner/reconcile contract or standalone idempotent module. Runtime passwords remain outside Git. |
| Menu | `DONE_IN_CODE`; `RUNTIME_EVIDENCE_PENDING` | Owner validate/execute API, V10 idempotency, generic clone transaction, Chinatown menu Profile, source invariance, replay, and tests exist. No Staging or Production clone evidence exists. |
| Synthetic St-Denis baseline | `DONE_IN_CODE`; `RUNTIME_EVIDENCE_PENDING` | Guarded empty-or-exact STG-005B planner/writer exists and is tested. It has not run on evidenced Staging. |
| Staging acceptance launcher | `DONE_IN_CODE`; `STAGING_PENDING` | AL-003S launcher/readiness guards exist. Exact-main deploy, V9/V10, bootstrap, login, onboarding, clone, replay, and restart evidence are pending. |
| Tables | `PARTIAL_IMPLEMENTATION` | Existing admin CRUD/template copy exists. No Store Profile contract/planner/idempotent writer; uniqueness, ownership, normalization, and reconcile rules are unresolved. |
| Printer Store isolation | `DONE_IN_CODE` | PR #66 scopes config update, dispatch, and PAD printer-health lookup to the durable Store. This is not Printing provisioning. |
| Printing | `PARTIAL_IMPLEMENTATION` | Print Engine, Print Center, PAD_DIRECT and assignments exist. Reusable logical-role/module policy, strict planner, inactive idempotent writer, and physical acceptance are not implemented. |
| Device/Pad | `PARTIAL_IMPLEMENTATION` | Registration, hashed token, heartbeat, Store-bound queue and Android Worker exist. Reusable planner/writer, trusted build provenance, durable Worker-health evidence, and idempotent pairing are not implemented. |
| Activation | `NOT_IMPLEMENTED` | No validator, workflow, evidence repository, activation API, or exclusive inactive-to-active transition exists. Legacy direct `active` writes remain a prerequisite repair. |
| REL-001 Production RC | `PLAN_ONLY`; `STAGING_PENDING`; `PRODUCTION_PENDING` | The plan is in main, but no candidate SHA has passed Staging and no Production gap/backup/restore/compatibility/deploy evidence exists. |
| ACT-001 Chinatown activation | `NOT_IMPLEMENTED`; `PRODUCTION_PENDING` | No technical package or executable activation exists. |

## 4. Runtime ground truth

The original PR #72 audit did not inspect runtime. Its STG-006 follow-up now
records fresh, bounded evidence:

| Environment | Retained evidence | Current classification |
|---|---|---|
| Staging | `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`, Flyway V8, isolated project/network/state, loopback bind, printing disabled, health 200 | `MACHINE_VERIFIED_READ_ONLY` by STG-006; candidate not deployed |
| Production | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`, project `cloud`, identical before/after container identity/start/restart, health 200 | `MACHINE_VERIFIED_READ_ONLY` continuity only; Flyway/business state not queried |
| Repository | V1-V10 and `origin/main@33c6e3c...` | Repository capability only |

There is no retained evidence that Staging applied V9/V10 or executed
STG-005A, STG-005B, Owner login, target onboarding, menu validate/execute,
replay, or same-image restart. There is no evidence that Production contains
any #61-#70 capability.

## 5. Current blockers

| Class | Blocker | Effect |
|---|---|---|
| Code/procedure | OPS-001 publishes fail-closed release/env, same-container restart/Flyway, and secret-FD Owner/API helpers after reviewed merge | Repository blocker closed; runtime artifacts and evidence remain absent |
| Configuration | No detached release, private environment digest, image identity, synthetic run identity, credential, or action approval exists for the next exact SHA | Must be created only inside separately approved runtime batches |
| Evidence | Current Staging SHA/Flyway freshness is now verified; V9/V10/bootstrap/login/clone/restart results remain pending | Repository capability cannot be promoted to Staging acceptance |
| Owner/runtime gate | STG-007 release/env, deployment, migration, credential/bootstrap, API execute, restart, Production read/deploy, and activation each require separate approval | Work stops before every unapproved runtime mutation |
| Production safety | Release-relative state path, combined Production build, missing phase resource gates, restore rehearsal, backup integrity, and old-app compatibility remain unresolved | Production deployment and ACT-001 are `NO_GO` |

## 6. Staging decision

`STG-006 = PASS` for the passive evidence scope only. It does not authorize
detached-release creation, deployment, Docker lifecycle, Flyway, credentials,
bootstrap, login, API calls, restart, or database writes. Immediate
deployment/full acceptance is still `NO_GO` until:

- the reviewed OPS-001 repository package is verified `IN_MAIN`;
- fresh release/environment/preflight digests pass;
- the exact merged-main SHA and separate STG-007 action batches are approved;
- each runtime mutation batch receives an action-specific Owner approval.

Production remains `NO_GO`: fixed external state-root protection, serial
build/resource gates, current Production evidence, Store 1 read approval,
V7-to-V10 compatibility, backup integrity, isolated restore rehearsal, and an
exact accepted RC are all pending.

## 7. Next bounded Agile Loops

| Order | Loop ID | Goal | Dependency | Acceptance evidence | Owner gate | Runtime gate | Rollback boundary |
|---:|---|---|---|---|---|---|---|
| 1 | `STG-006_EXACT_MAIN_PREFLIGHT` | Bind post-audit main SHA and collect fresh passive isolation/resource/continuity evidence | governance audit merged | `PASS` evidence at candidate `33c6e3c...` | completed read-only authorization | no further runtime action | no container/database change |
| 2 | `OPS-001_STAGING_SECRET_SAFE_TOOLING_REPAIR` | Close release/env rotation, same-image restart/Flyway collection, and Owner/API secret-handling gaps | STG-006 PASS | shell/focused tests, redaction and independent review | repository auto-merge gate | no runtime mutation in implementation | Git revert only |
| 3 | `STG-007_EXACT_SHA_DEPLOY_AND_MIGRATE` | Deploy only approved Staging SHA and verify V9/V10, health, second start, continuity | STG-006 PASS + OPS-001 accepted | exact images, Flyway V1-V10, health and continuity | deployment/migration approval | Staging deploy + Flyway | prior verified compatible image or stop/roll-forward; never clean/restore |
| 4 | `STG-008_SYNTHETIC_TOPOLOGY_AND_SOURCE` | Execute/replay STG-005A and STG-005B with printing disabled | STG-007 PASS | sanitized IDs/counts/revisions/replay | separate credential/bootstrap/source-write approvals | synthetic Staging writes | transaction rollback; retain successful evidence |
| 5 | `STG-009_AL003_OWNER_ACCEPTANCE` | Owner login, target onboarding, validate, execute, replay, restart/persistence | STG-008 PASS | sanitized auth/status/count/source-invariance/restart evidence | separate execute checkpoint | synthetic Staging writes and restart | transaction rollback; no destructive cleanup |
| 6 | `AL-004A_CONCRETE_STORE_PROFILE_AND_ENGINE_PLANNER` | Register complete non-secret Store Profile(s), module config registry, and read-only engine plan | STG-009 findings reviewed | deterministic fingerprints, planner and anti-hardcode tests | Profile identity/config review | none | Git revert |
| 7 | `AL-005A1_ACCESS_TABLE_CONTRACT_PLANNER` | Add reusable Staff/Access and Table contracts/read-only planner; resolve Store-isolation/normalization gates | AL-004A | focused authorization/fingerprint/planner tests | login convention/table policy decisions as needed | none | Git revert |
| 8 | `AL-005P1_PRINTING_CONTRACT_PLANNER` | Add fail-closed logical-role/module policy and read-only planner | AL-004A + #66 | printing/security regressions; no job/endpoint writes | strict mode/reconcile policy decisions | none | Git revert |
| 9 | `AL-005B1_DEVICE_CONTRACT_PLANNER` | Add safe Device/Pad readiness contract and read-only planner | AL-005P1 contract | Store isolation, redaction, build/health semantics tests | trusted build/freshness policy | none | Git revert |
| 10 | `AL-006A_ACTIVATION_READ_ONLY_VALIDATOR` | Implement fail-closed aggregate validator before any status writer | module contracts and evidence model stable | missing/stale/wrong-Store evidence tests; zero writes | lifecycle/persistence and legacy-writer decisions | none | Git revert |

Executable module writers, REL-001 Production RC selection, and ACT-001 remain
later loops after these contracts and Staging evidence. Planning documents in
main do not authorize skipping directly to their writers.

## 8. Current Owner gates and unique stop state

Current Owner actions after OPS-001 enters main, in order:

1. review one exact merged-main STG-007 release/env batch;
2. separately approve deploy/Flyway after formal preflight PASS;
3. approve each bootstrap/source, login/onboarding, execute/replay and restart
   batch separately.

Unique stop state:

`OPS-001_REPOSITORY_COMPLETE_STG-007_WAITING_FOR_OWNER_RUNTIME_APPROVAL`
