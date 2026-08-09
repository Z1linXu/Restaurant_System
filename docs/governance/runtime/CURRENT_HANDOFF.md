# Current Project Handoff

> This Handoff is a navigation snapshot only.
> Git ground truth, `ALIVE_RUNTIME_PLANBOOK.md`, `FEATURE_BACKLOG.md`,
> `AGILE_LOOP_OPERATING_MODEL.md` and applicable Technical Plans remain
> authoritative. If this file conflicts with those sources, the authoritative
> sources win.
>
> Snapshot date: 2026-08-09, America/Toronto
>
> Runtime freshness: PR #82 entered `main` at `2837ae88...` with the bounded
> restart-readiness/fail-closed repair. A fully fresh Owner-authorized
> V10-to-V10 continuation then bound, preflighted, built and deployed exact
> `2837ae88...` to isolated Staging. Repaired readiness, sanitized runtime
> collection, one same-image restart, and post-restart verification all passed.
> Flyway remained exact V10, health returned `200/200/200`, printing and
> isolation remained unchanged, and Production continuity remained unchanged.
> `STG-007 = PASS`. PR #83 then merged only that evidence/governance into
> `main@2ed56b06...`; runtime remained exact `2837ae88...`. The first STG-008
> entry stopped read-only at the credential decision, and PR #84 placed that
> sanitized evidence in `main@828af4e8...`. The Owner then approved
> `STG005_OWNER_20260808_R01` while retaining the password guard. Fresh exact
> readiness passed, but the password-free STG-005A plan one-shot stopped before
> its command/data path because the older cloud safety rule rejected required
> Flyway-disabled mode. Cleanup and zero-write continuity passed, and the
> launcher retained blocked state. PR #85 merged the bounded startup-safety
> repair and PR #86 closed its Ground Truth. The next authorized continuation
> freshly reconfirmed exact Staging `2837ae88...`, V10, zero synthetic state,
> the reviewed blocked pair, Printing/isolation and Production continuity. It
> stopped before Batch A mutation because the ordinary release path could not
> legally cross the retained block. PR #87 merged the dedicated recovery
> release-rebind repair at `4b954e09...`; its initial publication state is
> historical. That runtime-sensitive merge superseded the authorization bound
> to `4759a23b...`. A later Owner-authorized continuation bound and deployed
> exact `6753855497b8c47be99a8d88ae9d9961653addb0` to Staging at Flyway V10,
> passed formal preflight/readiness and recovered only the reviewed old blocked
> records. The next password-free plan failed before its command/data path
> because non-web startup required a servlet request context; cleanup and
> zero-write continuity passed, and a new blocked pair was retained. The
> bounded repository repair is pending publication; Production remains
> unchanged.

## 1. Project mission

Restaurant System is moving from one operational Store to reusable multi-Store
provisioning without destabilizing current restaurant operations.

- St-Denis is the current Production Store.
- Chinatown is the planned second real Production Store and the highest-priority
  delivery target.
- A future third Store matching St-Denis should reuse a reviewed St-Denis
  profile, not copied code or data scripts.
- The direction is multi-Store SaaS-style provisioning through shared modules
  and Profiles, while the immediate goal remains a safely released Chinatown.

## 2. Current Git ground truth

| Item | Verified value | Classification |
|---|---|---|
| runtime-sensitive deployed-main floor | `6753855497b8c47be99a8d88ae9d9961653addb0` | `DEPLOYED_TO_STAGING`; includes the PR #87 recovery path. Any future runtime-sensitive repair must be freshly fetched as a new exact candidate before runtime approval. |
| exact deployed Staging runtime | `6753855497b8c47be99a8d88ae9d9961653addb0` | `DEPLOYED_TO_STAGING`; authorized V10-to-V10 continuation, distinct from the pending request-context repair |
| Owner workspace | `main@ba169ed8b689ddef8dffe94deee82fea191cdcfb`, dirty with Owner work | Local checkout is behind `origin/main`; it was not modified by this handoff |
| Runtime-sensitive delivery package | PR #87 / head `84457266d1f752cb755b1933b01d849ae62407ed` | `IN_MAIN` through merge `4b954e09...`; no unmerged runtime-sensitive package remains |
| Handoff PR | [PR #71](https://github.com/Z1linXu/Restaurant_System/pull/71) | `IN_MAIN`; its GitHub merge commit is `5baada03935e004d80af1e7a36fb7db39bd6abbb` |

`IN_MAIN`, `DRAFT_PR`, `STACKED_ONLY`, `PREPARATION_ONLY`,
`DEPLOYED_TO_STAGING`, and `DEPLOYED_TO_PRODUCTION` are distinct states. A
GitHub Merged badge into a non-main base is not evidence that work entered
`main`.

### Relevant registered worktrees at this audit

| Worktree | Branch / purpose | State |
|---|---|---|
| `/Users/xuzilin/projects/Restaurant_System` | Owner `main` workspace | Dirty with Owner work; behind `origin/main` and untouched |
| `/private/tmp/restaurant-stg006-preflight` | `codex/stg-006-exact-main-preflight` | Retained historical PR #73 worktree |
| `/private/tmp/restaurant-ops001-tooling` | `codex/ops-001-staging-secret-safe-tooling` | Retained historical OPS-001 repository worktree |
| `/private/tmp/restaurant-post-stack-audit` | `codex/post-stack-ground-truth-audit` | Retained historical PR #72 worktree |
| `/private/tmp/restaurant-current-handoff` | `codex/current-project-handoff` | Retained historical PR #71 worktree |
| `/private/tmp/restaurant-stg007-execution` | `codex/stg007-v10-continuation-final-evidence` | Retained historical STG-007 evidence/governance worktree; Owner workspace untouched |
| `/private/tmp/restaurant-stg008-execution` | `codex/stg008-synthetic-topology-source` | Current isolated STG-008 `NO_GO` evidence/governance worktree; Owner workspace untouched |
| `/private/tmp/restaurant-stg008-resume` | retained PR #85/#86 historical repair and closure worktree | Historical isolated repository worktree; Owner workspace untouched |
| `/private/tmp/restaurant-stg008-recovery` | PR #87 repair plus governance-closure worktree | Current isolated repository worktree; Owner workspace untouched and no runtime mutation performed from it |
| `/private/tmp/restaurant-pr61-rebuild` through `/private/tmp/restaurant-pr65-rebuild` | merged #61-#65 branch worktrees | Retained historical worktrees; not current delivery inputs |

No registered #69/#70 rebuild worktree remains. Historical worktrees were not
removed by this audit because their branches/evidence are retained and cleanup
was not required for the governance correction.

## 3. Completed PR dependency map

GitHub and main ancestry were verified on 2026-08-08. Every PR below is
closed, merged, and `IN_MAIN`.

| PR | Package | Base | Head | State | Depends on | In main? | Owner action |
|---|---|---|---|---|---|---|---|
| #61 | Modular architecture foundation | `main` | merge `bbb1af9520c188b6ef6362e783284ba4001a7e63` | `IN_MAIN` | PR #71/main | Yes | Main capability; no runtime behavior |
| #62 | STG-005B Synthetic St-Denis baseline | `main` | merge `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b` | `IN_MAIN` | #61/main | Yes | Main capability; no runtime execution |
| #63 | AL-003S Staging acceptance preparation | `main` | merge `732d77c89ff067982702426ff918d5e097e1d0fb` | `IN_MAIN` | #62/main | Yes | Repository-only acceptance preparation; runtime use remains separately gated |
| #64 | AL-004 Generic Store Profile contract | `main` | merge `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6` | `IN_MAIN` | #63/main | Yes | Declarative repository capability only |
| #65 | AL-005A Staff/Table plan | `main` | merge `8f58bcbfca253c1598b967f4d17c04c0be1cce5b` | `IN_MAIN` | #64/main | Yes | Repository planning only |
| #66 | Printer Store-isolation repair | `main` | merge `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3` | `IN_MAIN` | #65/main | Yes | Security foundation; no runtime behavior |
| #67 | AL-005 Printing provisioning plan | `main` | merge `65e3d3ced2b5b05eb36d56ce67e475768ad19dff` | `IN_MAIN` | #65/main; #66 IN_MAIN | Yes | Repository planning only |
| #68 | AL-005B Device/Pad plan | `main` | `80839d454e8f88391b16e8ba502d3e4bcccd4fb6` | `IN_MAIN` | #67/main | Yes | Main capability; no runtime behavior |
| #69 | AL-006 Activation workflow plan | `main` | `b38d3188edc0555bea7e54dafc4868a7c4726005` | `IN_MAIN` | #68/main | Yes | Main capability; no runtime behavior |
| #70 | REL-001 Production RC plan | `main` | merge `645d4909625f70fc241d5468382d66a30a030fb1` | `IN_MAIN` | #69/main | Yes | Planning authority only; no RC, ACT-001, or runtime action |
| #72 | Post-stack Ground Truth audit | `main` | merge `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | `IN_MAIN` | completed #61-#71 main stack | Yes | Governance/capability audit only; no runtime action |
| #73 | STG-006 evidence/governance | `main` | merge `85d97b7327b2e15aa561ed28a5788b92cedf6f5b` | `IN_MAIN` | #72/main | Yes | Passive evidence only; no runtime mutation |
| #74 | OPS-001 secret-safe tooling | `main` | merge `362c954a8753204476ddf1415ea86050589760dd` | `IN_MAIN` | #73/main | Yes | Repository tooling only; runtime actions remain separately gated |
| #75 | STG-007 retained-listener preflight repair | `main` | merge `b93d8efdbd699333d73d9ffcc29e8f8443e51764` | `IN_MAIN` | #74/main | Yes | Guard repair only; no runtime mutation |
| #76 | STG-007 exact-Git release bootstrap repair | `main` | merge `e6fac236c7620cd2f579d2a180367f4f753a6d42` | `IN_MAIN` | #75/main | Yes | Control-path repair only; no release, deploy or Flyway action |
| #77 | STG-007 state-parent mode guard repair | `main` | merge `5c6d8bb70d74756cc7fe3f76b2d43cb07c6e6f33` | `IN_MAIN` | #76/main | Yes | Trust-root guard repair only; no release/env, deploy or Flyway action |
| #78 | STG-007 releases-parent mode guard repair | `main` | merge `35ccf5cb823bb22b449d8b82baa2f22db2e242df` | `IN_MAIN` | #77/main | Yes | Trust-root guard repair only; no release/env, deploy or Flyway action |
| #79 | STG-007 rotation state-root mode guard repair | `main` | merge `868e229f1b5afd28163e5031ad8fabffaad651f6` | `IN_MAIN` | #78/main | Yes | Guard/test/governance repair; later runtime use was separately authorized |
| #80 | STG-007 readiness health fingerprint repair | `main` | merge `39fa284b7bccd64d650c396f2c7532b0a0858b4b` | `IN_MAIN` | #79/main | Yes | Runtime fingerprint repair; later V10 continuation and readiness were separately authorized |
| #81 | STG-007 Flyway success-token repair | `main` | merge `63600b13b10a5549d9095a03c94e69a9f880af9f` | `IN_MAIN` | #80/main | Yes | Exact PostgreSQL boolean-token validation repair; later runtime use was separately authorized |
| #82 | STG-007 restart readiness/fail-closed repair | `main` | merge `2837ae88e55142c99c6975f8b6575febffc913a1` | `IN_MAIN` | #81/main | Yes | Bounded three-endpoint readiness and nonzero-exit blocked-state persistence; exact merged SHA later passed the authorized V10 continuation |
| #83 | STG-007 final evidence/governance | `main` | merge `2ed56b06f37c9257a655ec334f81e31ca4a518a6` | `IN_MAIN` | #82/main | Yes | Documentation/evidence only; no runtime-capability or runtime-state change |
| #84 | STG-008 entry evidence/governance | `main` | merge `828af4e84581dcb051248beee694c307a65210c5` | `IN_MAIN` | #83/main | Yes | Sanitized credential-gate evidence only; no application, migration, runtime configuration, credential, or business-data mutation |
| #85 | STG-008 guarded one-shot Flyway safety repair | `main` | merge `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6` | `IN_MAIN` | #84/main | Yes | Exact-profile no-migration startup-safety reconciliation plus tests/governance; its original publication did not deploy or mutate credentials/data |
| #86 | STG-008 dependency-repair Ground Truth closure | `main` | merge `4759a23b1a00d3254936e6c8eeb0ec33012b5145` | `IN_MAIN` | #85/main | Yes | Documentation-only closure; no runtime action |
| #87 | STG-008 release-rebind serialization repair | `main` | merge `4b954e09a365fec909ed6da3ddf8fa9f13639cdc` | `IN_MAIN` | #86/main | Yes | Recovery-only release/env preparation preserves both reviewed blocked records and every ordinary action block; it later supported exact `6753855497...` Staging rebind/deploy/recovery |

Main stack review order:

`#61 -> #62 -> #63 -> #64 -> #65 -> #67 -> #68 -> #69 -> #70`

PR #66 is independent and is now `IN_MAIN`. PR #67 is also `IN_MAIN` as the
Printing Provisioning planning foundation for #68; neither authorizes runtime
printing or device operations.

## 4. Runtime ground truth

STG-006 established the historical passive baseline and STG-007 established
the historical infrastructure identity. The later STG-008 continuation updated
the current bounded Staging runtime identity below.

| Environment | Retained evidence | Classification and boundary |
|---|---|---|
| Production | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`; Compose `cloud` `db/backend/nginx`; unchanged IDs/start times/restart `0`; health 200 | `MACHINE_VERIFIED_READ_ONLY` continuity only; Flyway/business state not queried |
| Staging | `6753855497b8c47be99a8d88ae9d9961653addb0`; Flyway V10 with no pending migration; exact `db/backend/nginx` identities running; health 200/200/200 | `DEPLOYED_TO_STAGING`; STG-007 infrastructure PASS is retained, then rebind/preflight/readiness and old-pair recovery passed before a password-free plan failed before command/data access |
| Staging isolation | project `restaurant-pos-staging`; only `127.0.0.1:18080`; separate state/network/mounts; private leaf UID 70/mode 0700 | `MACHINE_VERIFIED_READ_ONLY` |
| Staging printing | `STAGING_PRINT_MODE=DISABLED`; feature flag `false` | `MACHINE_VERIFIED_READ_ONLY` |

Repository migrations are exactly V1-V10. Machine evidence proves Staging is
V10; it does not prove V8-V10 ran on Production.

The historical credential-entry decision is
[STG-008 Synthetic Topology and Source Entry Evidence](STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md).
The resumed plan failure and bounded repair are recorded in
[STG-008 Flyway Guard Repair Evidence](STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md).
This is a failed password-free plan one-shot before the STG-005A command, not a
failed transaction, deployment, migration, or credential operation. The
launcher retains the new fail-closed pair pending a repaired exact candidate
and separately approved recovery.

The later fresh baseline, release-rebind sequencing deadlock and bounded PR
#87 correction are recorded in
[STG-008 Release-Rebind Serialization Repair Evidence](STG-008_RELEASE_REBIND_SERIALIZATION_REPAIR_EVIDENCE.md).
That historical continuation stopped before candidate import or Batch A
mutation. Its PR #87 repair was later used by exact `6753855497...` for
Staging rebind/preflight/deploy/readiness and old-pair recovery. The following
non-web plan failure supersedes that historical gate; its repair requires a
new exact candidate and authorization cannot be reused.

## 5. Current feature and loop

| Field | Current value |
|---|---|
| Current Feature | `FT-001 Owner Store Onboarding - Chinatown` |
| Current Agile Loop | `STG-008_SYNTHETIC_TOPOLOGY_AND_SOURCE` |
| Current package | Exact `6753855...` passed rebind/preflight/deploy/readiness and cleared only the reviewed old block. Its fresh password-free `bootstrap-plan` then failed before command/data access because request context requires servlet injection in non-web mode. Synthetic Owner remains `NOT_CREATED`, topology is empty, and a newly retained blocked pair requires a repair-dependent fresh exact continuation. |
| Feature stop state | `STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_REQUIRES_NEW_EXACT_SHA_STAGING_REBIND_AND_BLOCKED_STATE_RECOVERY_OWNER_RUNTIME_APPROVAL` |
| Handoff navigation status | `PROJECT_HANDOFF_IN_MAIN` |
| Current Owner gate | After the repository repair enters `main`, approve a freshly fetched exact candidate for release/private-env binding, formal preflight, Staging-only V10-to-V10 deploy, fresh readiness and bounded recovery of the newly retained records. Then restart with a new password-free PLAN and new action approval. The password may be requested only when fresh STG-005A EXECUTE actually needs it, through the reviewed private stdin/FD channel. |

### Permitted work

- Fetch and verify Git/GitHub ground truth.
- Review STG-007 PASS, all three STG-008 evidence records, and merged PRs
  #85-#87.
- Complete only this documentation-only Ground Truth closure under the
  permanent auto-merge gate.
- Verify Git/GitHub Ground Truth and report the exact next runtime approval
  boundary.

### Prohibited work without new approval

- Reuse of consumed/failed STG-007 or STG-008 approval/readiness evidence,
  another deploy/restart, or continuation on the old image.
- Runtime Flyway, bootstrap, synthetic credential creation, source creation,
  login, target onboarding, validate/execute/replay, or clone.
- Production Store 1 read or mutation.
- Printer configuration, test print, Pad pairing, or device/Worker mutation.
- Candidate import, approval consumption, release/env preparation, or another
  Batch A action under the authorization bound to `4759a23b...`.
- Clearing the blocked marker/lock record, requesting the password, or any
  further STG-008 helper/one-shot before a newly Owner-approved, freshly
  fetched exact main containing PR #87 is deployed and the bounded post-Batch-A
  recovery conditions pass;
  lowering the password/prefix guard or modifying the Owner's secret.
- Repository merge that fails Operating Model section 16's permanent
  auto-merge gate, Production activation, restore, or destructive database/Git
  commands.

## 6. What is already done

- AL-001 planning is complete.
- AL-002 Owner onboarding foundation and V8 are in main.
- STG-001 through STG-004 established the isolated Staging plan, deployment
  package, local Docker rehearsal, and historical server Staging evidence.
- STG-005 plan and STG-005A guarded synthetic bootstrap are in main; bootstrap
  has not been evidenced on the retained Staging runtime.
- AL-003 PR-A through PR-F are in main.
- PR #58 retained the failed exact-SHA Staging attempt evidence.
- PR #59 fixed the bounded PostgreSQL UID-70/mode-0700 preflight defect in main.
- PR #60 merged Owner product decisions and governance alignment into main at
  `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`.
- PR #71 merged this navigation handoff into main at
  `5baada03935e004d80af1e7a36fb7db39bd6abbb`.
- PR #61 merged the modular architecture foundation into main at
  `bbb1af9520c188b6ef6362e783284ba4001a7e63`.
- PR #62 merged the guarded Synthetic St-Denis baseline into main at
  `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b`.
- PR #63 entered `main` at `732d77c89ff067982702426ff918d5e097e1d0fb`.
- PR #64 entered `main` at `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6`.
- PR #65 entered `main` at `8f58bcbfca253c1598b967f4d17c04c0be1cce5b`.
- PR #66 entered `main` at `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3`.
- PR #67 entered `main` at `65e3d3ced2b5b05eb36d56ce67e475768ad19dff`.
- PR #68 entered `main` at `9e93573be97cfd01a9ad3efe64d55827854c497a`.
- PR #69 entered `main` at `dc682203b2b24bbdb453a5520b297b9051139f13`.
- PR #70 entered `main` at `645d4909625f70fc241d5468382d66a30a030fb1`.
  This completes the #61-#70 preparation stack; no runtime state is implied.
- PR #72 entered `main` at `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b`.
  STG-006 then freshly verified the retained isolated Staging runtime and
  minimum Production continuity without mutation.
- PR #73 entered `main` at `85d97b7327b2e15aa561ed28a5788b92cedf6f5b`.
  It records `STG-006=PASS` and `OPS-001=REQUIRED`; it performed no runtime
  mutation.
- PR #74 entered `main` at `362c954a8753204476ddf1415ea86050589760dd`
  with OPS-001 repository tooling; runtime use remained separately gated.
- PR #75 entered `main` at `b93d8efdbd699333d73d9ffcc29e8f8443e51764`
  with the retained-listener preflight repair; no runtime mutation occurred.
- PR #76 entered `main` at `e6fac236c7620cd2f579d2a180367f4f753a6d42`
  with the exact-Git release bootstrap. The next Batch A attempt imported that
  candidate, then stopped before release/env mutation at the state-parent mode
  guard now under bounded repair.
- PR #77 entered `main` at `5c6d8bb70d74756cc7fe3f76b2d43cb07c6e6f33`
  with the state-parent correction. The next Batch A attempt imported that
  candidate and delegated bootstrap, then stopped before approval consumption
  or mutation at the releases-parent mode guard now under bounded repair.
- PR #78 entered `main` at `35ccf5cb823bb22b449d8b82baa2f22db2e242df`
  with the releases-parent correction. The next Batch A attempt created its
  exact release and consumed approval, then stopped before env mutation at the
  remaining rotation state-parent mode guard now under bounded repair.
- PR #79 entered `main` at `868e229f1b5afd28163e5031ad8fabffaad651f6`
  with the rotation state-parent correction. The next authorized run passed
  Batch A, deployed that exact SHA and applied V9/V10, then stopped before
  readiness PASS or restart at the optional-health fingerprint bug now under
  bounded repair.
- PR #80 entered `main` at `39fa284b7bccd64d650c396f2c7532b0a0858b4b`.
  The new V10-aware continuation deployed that exact SHA, retained V10 and
  passed repaired readiness. Runtime collection then exposed the canonical
  `true` versus mock-`t` validator mismatch before PASS evidence; no restart
  followed.
- PR #81 entered `main` at `63600b13b10a5549d9095a03c94e69a9f880af9f`.
  A fresh V10-aware continuation deployed that exact SHA, passed formal
  preflight, readiness and sanitized runtime/Flyway collection. Its same-image
  restart retained the exact containers/images but the single immediate health
  probe raced application startup and returned 502. Runtime recovered to
  200/200/200 at the same identities; the action remains `NO_GO` because no
  PASS evidence or blocked marker was produced.
- PR #82 entered `main` at `2837ae88e55142c99c6975f8b6575febffc913a1`
  with bounded three-endpoint restart readiness and nonzero-exit fail-closed
  persistence. A new exact-main continuation then deployed that SHA
  V10-to-V10 and passed formal preflight, readiness, runtime collection,
  same-image restart, and post-restart verification. `STG-007 = PASS`.
- PR #83 entered `main` at `2ed56b06f37c9257a655ec334f81e31ca4a518a6`
  with only the final STG-007 evidence/governance. STG-008 later performed a
  read-only entry check: zero synthetic topology/credential rows, safe next
  Store ID `1`, unchanged Staging/Production continuity, and a credential-
  contract `NO_GO` before any plan or write.
- PR #84 entered `main` at `828af4e84581dcb051248beee694c307a65210c5`
  with only that sanitized STG-008 entry evidence/governance. The Owner then
  aligned the credential contract. Fresh readiness passed, but the first
  password-free plan one-shot stopped before its command because the older
  cloud safety guard rejected Flyway-disabled mode. Cleanup and zero-write
  continuity passed; fail-closed state remains until separately approved
  recovery.

## 7. AL-003 repository capability

Current main includes:

- generic Category/Station/Item clone transaction;
- source option cloning with parent mapping;
- versioned Chinatown Profile and target overrides;
- shared read-only option-plan validation and structured diagnostics;
- V10 idempotency reservation, replay, terminal `FAILED`, and sanitized result;
- Organization Owner authorization;
- public Owner `validate` and `execute` API facade reusing the same planner and
  lock-owning transaction.

Repository capability is not Staging acceptance and is not Production
deployment. No real Chinatown clone has been evidenced.

Still missing are Synthetic St-Denis and Owner topology, login/target
onboarding, validate/execute/replay evidence, and the separate Production
source, RC, deployment, provisioning and field-acceptance gates.

## 8. Owner product decisions that are settled

1. Chinatown is the second real Production Store.
2. Production Store 1 / St-Denis is the Chinatown menu source of truth.
3. Chinatown's initial menu must be created through the reviewed Clone Engine.
4. After clone/activation, Menu Management may modify the target normally.
5. An Organization Owner naturally manages same-Organization Stores under the
   current authorization contract; do not manufacture redundant target Owner
   memberships.
6. Staging uses synthetic-only credentials and data.
7. Staging remains a Production-like synthetic St-Denis test environment.
8. Production uses a formal exact-SHA Release Candidate process.
9. Chinatown DoD is Store + Owner/Staff + Menu + Tables + Printing + Pads +
   Login + actual order test.
10. A third Store matching St-Denis must reuse a St-Denis Profile and shared
    modules, never copy/paste Store-specific code.

Do not reopen these decisions unless the Owner changes them or executable code
proves the underlying contract has changed.

## 9. Modular architecture direction

Owner-approved direction, currently planned/prepared rather than implemented as
one complete engine:

`Generic Store Provisioning Engine + Versioned Store Profiles + Reusable Provisioning Modules`

`Profile defines WHAT. Module defines HOW.`

Planned reusable modules are Store Core, Access/Staff, Menu, Tables, Printing,
Device/Pad, and Activation. Shared code must not branch on a Store ID/name,
duplicate the Menu Clone Engine, or create copy/paste Store engines. Chinatown
is the first Store Profile sample, not a shared-service special case.

## 10. Current roadmap

| ID | Purpose | Current state | Dependency / Owner gate |
|---|---|---|---|
| STG-005B / #62 | Reproducible synthetic St-Denis menu baseline | `IN_MAIN` | Repository capability only; no runtime execution |
| AL-003S / #63 | Exact-SHA Staging acceptance preparation | `IN_MAIN` | Repository capability only; explicit runtime approval remains separate |
| AL-004 / #64 | Generic Store Profile contract | `IN_MAIN` | Repository capability only; no provisioning/runtime execution |
| AL-005A / #65 | Staff/Table module plan | `IN_MAIN` | Repository planning only; no writer or runtime execution |
| AL-005 / #67 | Printing provisioning plan | `IN_MAIN` | repository planning only; no writer/runtime action |
| AL-005B / #68 | Device/Pad provisioning plan | `IN_MAIN` at `9e93573be97cfd01a9ad3efe64d55827854c497a` | no pairing/credential/Worker/runtime action |
| AL-006 / #69 | Fail-closed activation workflow plan | `IN_MAIN` at `dc682203b2b24bbdb453a5520b297b9051139f13` | no status transition or activation writer |
| REL-001 / #70 | Formal Chinatown Production RC plan | `IN_MAIN` at `645d4909625f70fc241d5468382d66a30a030fb1` | planning only; no candidate, deployment, or activation |
| ACT-001 | Production provisioning and field acceptance | `NOT_STARTED_OWNER_GATED` | Accepted RC and explicit Production activation approval |
| STG-006 | Exact-main passive preflight | `PASS` for candidate `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | Evidence only; no release/deploy/migration approval |
| OPS-001 | Secret-safe Staging tooling | `REPOSITORY_COMPLETE`; bounded repairs #75-#82 and #87 are `IN_MAIN` | PR #87's recovery-only release/env path supported the later exact `6753855497...` rebind/deploy/recovery; runtime actions remain independently exact-SHA/action/Owner-gated |
| STG-007 | Exact-SHA V10-aware continuation | `PASS` at deployed Staging SHA `2837ae88...` / Flyway V10 | Runtime batch complete; no approval or evidence is reusable |
| STG-008 | Synthetic topology and source | `DEPENDENCY_REPAIR_RUNTIME_GATE` | Identity/password contract is aligned. The exact `6753855497...` continuation completed rebind/deploy/readiness and old-pair recovery. Its password-free plan then failed before command/data access at the non-web request-context dependency; no data write occurred. A new exact-SHA Staging deploy and post-Batch-A recovery of the newly retained pair precede any retry. |

The authoritative post-stack capability matrix, Staging decision, and next
bounded loops are in [Post-Stack Ground Truth Audit](POST_STACK_GROUND_TRUTH_AUDIT.md).

## 11. Stack rebuild rule

After each dependency enters main:

`fetch latest main -> verify dependency IN_MAIN -> rebuild/rebase/retarget next package -> review diff -> rerun checks -> governance sync -> Owner review`

Do not merge into an intermediate feature branch and report it as main. If a PR
was merged only to a non-main base, keep it `STACKED_ONLY` and promote it again
from current main.

## 12. Known blockers and risks

- STG-007 passed at exact deployed `2837ae88...` / Flyway V10, but it does not
  authorize or prove synthetic bootstrap, Owner login, or clone acceptance.
- STG-008's bounded backend repair entered main through PR #85 and the
  blocked-state-safe release-rebind correction entered through PR #87. A
  freshly fetched exact main containing #87 requires a new Staging
  release/deploy approval because the authorization bound to `4759a23b...`
  cannot cross the runtime-sensitive merge. The old deployed image cannot be
  patched or treated as repaired.
- The failed plan retained both blocked records. A separate recovery approval
  must confirm one-shot absence, zero transaction state, V10, health and
  continuity before clearing them.
- Owner login, target onboarding, validate/execute/replay evidence is pending.
- Production and repository main have an unreviewed runtime gap.
- Production needs a fixed state/control-root strategy before detached-release
  deployment can be safe.
- Production deploy tooling needs guarded serial builds and the 1 GiB memory
  gates described by PR #70.
- Fresh Production preflight and separate Store 1 read approval are pending.
- Backup integrity and isolated restore rehearsal are pending.
- Old-application-on-new-schema rollback compatibility is pending.
- Printing and Device/Pad field provisioning remain unimplemented/runtime-gated.
- Exact RC, Production deployment, and separate ACT-001 approval are pending.

### Recently resolved

- AL-003 generic clone/profile/planner/API packages entered main.
- PostgreSQL private-leaf mode-0700 preflight handling entered main via PR #59.
- Owner decisions and access semantics entered main via PR #60.
- Draft architecture and downstream preparation packages are already written;
  do not regenerate them.
- OPS-001 restart readiness/fail-closed repair entered main through PR #82 and
  the fresh exact-SHA continuation produced valid same-image restart evidence.
- The STG-008 guarded one-shot Flyway safety repair entered main through PR
  #85 at `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6`; its publication itself did
  not perform a runtime action.
- PR #87 merged the blocked-state-safe release-rebind serialization repair at
  `4b954e09a365fec909ed6da3ddf8fa9f13639cdc` after full shell regressions and
  Agent 6 `ACCEPT`; it later supported the exact `6753855497...` Staging
  rebind/deploy/readiness and old-pair recovery continuation.

## 13. START HERE for the next Agent

1. Run `git fetch origin --prune` without altering unrelated local work.
2. Read this file, then read `ALIVE_RUNTIME_PLANBOOK.md`,
   `AGILE_LOOP_OPERATING_MODEL.md`, `FEATURE_BACKLOG.md`, and the applicable
   technical plan.
3. Verify current `origin/main`; do not trust the Owner workspace branch tip.
4. Verify GitHub PR #61 through #87 and independent PR #66 semantics.
5. Distinguish main, stacked Draft, Staging, and Production state.
6. Report the completed main stack and the next Staging Owner Gate.
7. Do not recreate or redesign packages #61-#70.
8. Do not infer implementation from the planning packages.
9. Read STG-006, OPS-001, the STG-007 repair/final evidence, and all three
   STG-008 evidence records. Treat `STG-007=PASS`, the credential decision as
   resolved, and STG-008 as blocked before data write. Do not reuse the
   `4759a23b...` authorization, run a synthetic helper, or clear blocked state
   without a newly approved exact-main-containing-#87 deploy Gate and the
   bounded post-Batch-A recovery conditions.
10. Stop at runtime/product/operations Owner Gates; otherwise continue the
    bounded Agile Loop and Dependency Repair Auto-Loop.

## 14. Auto-loop behavior

Continue to follow Dependency Repair Auto-Loop, Continuous Agile Loop,
Mandatory Governance Sync, and Planbook Ground Truth Rule. A clear bounded
defect with no product/security/runtime change should be repaired, tested,
reviewed, documented, and submitted as a Draft PR. Do not mechanically stop at
a repairable `NO_GO`.

Stop only for a real product/architecture ambiguity, security boundary,
runtime mutation, Production action, irreversible operation, or dependency
requiring Owner merge.

## 15. Do not repeat

- Do not redesign the AL-003 clone engine or create a second one.
- Do not re-decide whether Chinatown is a real Store.
- Do not re-decide the formal Production RC strategy.
- Do not add a target Owner membership unless the authorization contract changes.
- Do not re-fix the PostgreSQL mode-0700 preflight bug.
- Do not revive historical stacked promotion branches.
- Do not regenerate #61-#70 or implement duplicate equivalents.
- Do not infer that a Draft, merge badge, repository migration, or local test is
  deployed runtime evidence.

## 16. Safety and evidence references

Never place passwords, tokens, cookies, printer endpoints, customer data, raw
menu payloads, raw idempotency keys, database credentials, SSH keys, or secrets
in this handoff or future governance records.

Primary authorities:

- [Alive Runtime Planbook](ALIVE_RUNTIME_PLANBOOK.md)
- [Feature Backlog](../FEATURE_BACKLOG.md)
- [Agile Loop Operating Model](../AGILE_LOOP_OPERATING_MODEL.md)
- [Known Issues Backlog](../KNOWN_ISSUES_BACKLOG.md)
- [AL-003 technical plan](../agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md)
- [AL-003 Staging acceptance plan](../agile/AL-003_STAGING_RELEASE_ACCEPTANCE_PLAN.md)
- [STG-006 exact-main preflight evidence](STG-006_EXACT_MAIN_PREFLIGHT_EVIDENCE.md)
- [OPS-001 local tooling evidence](OPS-001_STAGING_SECRET_SAFE_TOOLING_EVIDENCE.md)
- [STG-007 Flyway success-token repair evidence](STG-007_FLYWAY_SUCCESS_TOKEN_REPAIR_EVIDENCE.md)
- [STG-007 restart readiness/fail-closed repair evidence](STG-007_RESTART_READINESS_FAIL_CLOSED_REPAIR_EVIDENCE.md)
- [STG-007 exact-SHA V10 continuation evidence](STG-007_EXACT_SHA_CONTINUATION_EVIDENCE.md)
- [STG-008 synthetic topology/source entry evidence](STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md)
- [STG-008 guarded one-shot Flyway repair evidence](STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md)
- [STG-008 non-web request-context repair evidence](STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md)
- [OPS-001 secret-safe tooling runbook](../../../deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md)
- [System Documentation](../../../SYSTEM_DOCUMENTATION.md)
- [API contract](../../../doc/API.md)
