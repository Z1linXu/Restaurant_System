# Post-Stack Ground Truth Audit

> Audit date: 2026-08-08, America/Toronto
>
> Current documentation repository base before STG-008 repair publication:
> `origin/main@828af4e84581dcb051248beee694c307a65210c5`
>
> Exact deployed Staging runtime:
> `2837ae88e55142c99c6975f8b6575febffc913a1`
>
> Follow-up runtime access: STG-006 passive/read-only observation and the
> bounded STG-007 V10-aware continuation completed; see
> `STG-006_EXACT_MAIN_PREFLIGHT_EVIDENCE.md` and
> `STG-007_EXACT_SHA_CONTINUATION_EVIDENCE.md`
>
> Current decision:
> `STG-008_DEPENDENCY_REPAIR_IN_MAIN_WAITING_FOR_EXACT_SHA_STAGING_REBIND_AND_BLOCKED_STATE_RECOVERY_OWNER_RUNTIME_APPROVAL`

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

STG-006 bound `33c6e3c...` and completed its authorized passive preflight.
OPS-001 and its fail-closed dependency repairs later entered main. Under a new,
bounded V10-aware authorization, STG-007 deployed exact
`2837ae88e55142c99c6975f8b6575febffc913a1` to isolated Staging, retained
Flyway V10 with no pending migration, passed repaired readiness and runtime
collection, and passed one same-image restart. Printing remained disabled and
Production continuity was unchanged. `STG-007 = PASS`; this is infrastructure
acceptance only, not synthetic topology/source creation or AL-003 acceptance.
PR #83 then merged only that evidence/governance into `main@2ed56b06...`.
The Owner-authorized STG-008 read-only entry retained exact runtime
`2837ae88...`, found zero synthetic topology/credential rows, and proved the
next Store ID is `1`; it stopped before plan/write at the credential contract.
PR #84 merged that sanitized evidence into `main@828af4e8...`. The Owner then
aligned the contract. Fresh password-free plan readiness passed, but the
one-shot stopped before its STG-005A command at the older cloud/Flyway safety
conflict. Cleanup and zero-write continuity passed; blocked state was retained
and a bounded repository repair is under review.

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
| #73 | STG-006 evidence/governance | `85d97b7327b2e15aa561ed28a5788b92cedf6f5b` | `IN_MAIN`; passive preflight evidence only |
| #74 | OPS-001 secret-safe tooling repair | `362c954a8753204476ddf1415ea86050589760dd` | `IN_MAIN`; repository tooling only |
| #81 | Flyway success-token repair | `63600b13b10a5549d9095a03c94e69a9f880af9f` | `IN_MAIN`; fail-closed collector repair |
| #82 | Restart/readiness fail-closed repair | `2837ae88e55142c99c6975f8b6575febffc913a1` | `IN_MAIN`; bounded readiness/restart repair |
| #83 | STG-007 final evidence/governance | `2ed56b06f37c9257a655ec334f81e31ca4a518a6` | `IN_MAIN`; documentation/evidence only |
| #84 | STG-008 entry evidence/governance | `828af4e84581dcb051248beee694c307a65210c5` | `IN_MAIN`; sanitized credential-entry evidence only |

All listed merge commits are verified ancestors of current
`origin/main@828af4e8...`. PR #82 and all earlier runtime-sensitive packages
are ancestors of deployed `2837ae88...`; PRs #83/#84 are intentionally later
and documentation-only. There is no `DRAFT_PR` or `STACKED_ONLY` package
remaining in #61-#84 before the current bounded repair publication.
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
| Synthetic St-Denis baseline | `DONE_IN_CODE`; `STG-008_DEPENDENCY_REPAIR_GATE` | Guarded empty-or-exact STG-005B planner/writer exists and is tested. Parent STG-005A credential alignment is resolved, but its password-free plan stopped before the command at the shared Flyway safety conflict. STG-005B has not run. |
| Staging acceptance launcher | `DONE_IN_CODE`; `INFRASTRUCTURE_ACCEPTED`; `AL003_PENDING` | Exact-main V10-to-V10 deploy, readiness, runtime collection and same-image restart passed. Bootstrap, source creation, login, onboarding, clone and replay remain unexecuted. |
| Tables | `PARTIAL_IMPLEMENTATION` | Existing admin CRUD/template copy exists. No Store Profile contract/planner/idempotent writer; uniqueness, ownership, normalization, and reconcile rules are unresolved. |
| Printer Store isolation | `DONE_IN_CODE` | PR #66 scopes config update, dispatch, and PAD printer-health lookup to the durable Store. This is not Printing provisioning. |
| Printing | `PARTIAL_IMPLEMENTATION` | Print Engine, Print Center, PAD_DIRECT and assignments exist. Reusable logical-role/module policy, strict planner, inactive idempotent writer, and physical acceptance are not implemented. |
| Device/Pad | `PARTIAL_IMPLEMENTATION` | Registration, hashed token, heartbeat, Store-bound queue and Android Worker exist. Reusable planner/writer, trusted build provenance, durable Worker-health evidence, and idempotent pairing are not implemented. |
| Activation | `NOT_IMPLEMENTED` | No validator, workflow, evidence repository, activation API, or exclusive inactive-to-active transition exists. Legacy direct `active` writes remain a prerequisite repair. |
| REL-001 Production RC | `PLAN_ONLY`; `STAGING_PENDING`; `PRODUCTION_PENDING` | The plan is in main, but no candidate SHA has passed Staging and no Production gap/backup/restore/compatibility/deploy evidence exists. |
| ACT-001 Chinatown activation | `NOT_IMPLEMENTED`; `PRODUCTION_PENDING` | No technical package or executable activation exists. |

## 4. Runtime ground truth

The original PR #72 audit did not inspect runtime. STG-006 and STG-007 now
record fresh, bounded evidence:

| Environment | Retained evidence | Current classification |
|---|---|---|
| Staging | exact release `2837ae88e55142c99c6975f8b6575febffc913a1`, Flyway V10/no pending migration, health 200/200/200, isolated project/network/state and loopback bind, printing disabled | `STG-007_PASS`; exact V10-to-V10 deploy and same-image restart verified |
| Production | retained release `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`, project `cloud`, identical before/after container IDs, image IDs, starts and restart counts, health 200 | `MACHINE_VERIFIED_READ_ONLY` continuity only; Flyway/business state not queried |
| Repository | migrations V1-V10; documentation main `828af4e8...`; deployed source `2837ae88...` | PRs #83/#84 are governance-only and do not change runtime identity; current repair changes backend startup safety only after merge/deploy |

There is retained evidence that Staging is at V10, has no pending migration,
and recovered from a same-image restart. No V8-to-V9-to-V10 migration was
rerun or inferred during the V10 continuation. STG-005A, STG-005B, Owner
login, target onboarding, menu validate/execute and replay remain unexecuted.
There is no evidence that Production contains any #61-#70 capability.

STG-008 additionally proved through aggregate/synthetic-scoped read-only
queries that Organization, Store, user, credential, membership, and bootstrap-
request counts are all zero. Synthetic Owner is `NOT_CREATED`, and
`stores_id_seq last_value=1, is_called=false` establishes safe first Store ID
allocation without a write probe.

After credential alignment, one fresh `bootstrap-plan` one-shot failed before
the STG-005A command, credential reader, or transaction. Post-failure
read-only evidence retained all zero counts, exact V10 and healthy unchanged
Staging/Production continuity. The one-shot was removed and both fail-closed
records remain present.

## 5. Current blockers

| Class | Blocker | Effect |
|---|---|---|
| Code/procedure | OPS-001 plus PRs #81/#82 publish fail-closed release/env, Flyway/readiness/restart and secret-FD Owner/API helpers | Repository blocker closed; the authorized STG-007 path passed |
| Configuration | Approved identity is `STG005_OWNER_20260808_R01`; the runtime-only password remains ungenerated/unread and guarded at 12-through-256 characters | Do not request it before the repaired exact image is deployed and blocked-state recovery passes |
| Evidence | Exact Staging V10 deploy/readiness/restart evidence is complete; bootstrap/source/login/clone evidence remains absent | STG-007 passes, while AL-003 acceptance remains pending |
| Owner/runtime gate | The password-free plan one-shot retained blocked state after a pre-command dependency defect. The repair changes backend SHA. | After repair merge, approve fresh exact-SHA Staging release/deploy and separate blocked-state recovery; then restart every action with fresh evidence |
| Production safety | Release-relative state path, combined Production build, missing phase resource gates, restore rehearsal, backup integrity, and old-app compatibility remain unresolved | Production deployment and ACT-001 are `NO_GO` |

## 6. Staging decision

`STG-006 = PASS` for its historical passive scope and `STG-007 = PASS` for the
bounded V10-aware infrastructure continuation. The latter proves exact-main
deployment, V10/no-pending, repaired readiness, runtime evidence and same-image
restart. It does not prove or authorize synthetic Store topology/source data,
runtime credential creation, Owner login, target onboarding, clone execution,
full AL-003 acceptance or any Production mutation.

`STG-008 = NO_GO` after a password-free plan one-shot failed before the
STG-005A command at a bounded startup-safety defect. This is not deployment,
migration, bootstrap-transaction, credential, or source-menu failure; no data
write began, cleanup succeeded, runtime remained healthy/unchanged, and
blocked state was intentionally retained.

Production remains `NO_GO`: fixed external state-root protection, serial
build/resource gates, current Production evidence, Store 1 read approval,
V7-to-V10 compatibility, backup integrity, isolated restore rehearsal, and an
exact accepted RC are all pending.

## 7. Next bounded Agile Loops

| Order | Loop ID | Goal | Dependency | Acceptance evidence | Owner gate | Runtime gate | Rollback boundary |
|---:|---|---|---|---|---|---|---|
| 1 | `STG-006_EXACT_MAIN_PREFLIGHT` | Bind post-audit main SHA and collect fresh passive isolation/resource/continuity evidence | governance audit merged | `PASS` evidence at candidate `33c6e3c...` | completed read-only authorization | no further runtime action | no container/database change |
| 2 | `OPS-001_STAGING_SECRET_SAFE_TOOLING_REPAIR` | Close release/env rotation, same-image restart/Flyway collection, and Owner/API secret-handling gaps | STG-006 PASS | `PASS`; reviewed package and repairs #81/#82 are in main | completed repository merge gates | no runtime mutation in implementation | Git revert only |
| 3 | `STG-007_EXACT_SHA_CONTINUATION` | Deploy exact approved Staging SHA from V10 and verify no-pending, health, readiness, evidence, same-image restart and continuity | STG-006 PASS + OPS-001 accepted | `PASS` at exact `2837ae88...`; Flyway V10; health 200/200/200 | completed bounded V10-aware authorization | completed Staging-only V10-to-V10 continuation | no destructive rollback used |
| 4 | `STG-008_SYNTHETIC_TOPOLOGY_AND_SOURCE` | Execute/replay STG-005A and STG-005B with printing disabled | STG-007 PASS; bounded repair merged/deployed; blocked state recovered | entry and plan `NO_GO`; future sanitized IDs/counts/revisions/replay | new exact-SHA deploy plus separate recovery approval, then distinct plan/create/replay approvals | no data write yet; future synthetic Staging writes only | transaction rollback; retain successful evidence |
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

The credential decision is resolved. The current Owner gate is STG-008 exact
runtime rebinding and blocked-state recovery:

1. approve the new exact merged repair SHA for a fresh Staging release,
   preflight and Staging-only deploy;
2. separately approve recovery after read-only confirmation of one-shot
   absence, zero transaction state, V10, health and continuity, then remove
   both retained blocked records;
3. restart STG-008 with fresh readiness and a distinct digest-bound approval
   for every STG-005A/STG-005B plan, create and replay invocation;
4. request the 12-through-256 runtime-only password only at STG-005A execute
   through the reviewed private stdin/FD channel, never Git/argv/log/evidence;
5. review sanitized Store IDs, counts, revisions and replay evidence. Expected
   source data remains 4 categories, 3 stations, 13 items and 38 options.

This gate does not authorize Owner login, Chinatown target onboarding,
validate/execute/clone, printer configuration, Pad pairing or Production work.

Unique stop state:

`STG-008_DEPENDENCY_REPAIR_IN_MAIN_WAITING_FOR_EXACT_SHA_STAGING_REBIND_AND_BLOCKED_STATE_RECOVERY_OWNER_RUNTIME_APPROVAL`
