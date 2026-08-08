# Current Project Handoff

> This Handoff is a navigation snapshot only.
> Git ground truth, `ALIVE_RUNTIME_PLANBOOK.md`, `FEATURE_BACKLOG.md`,
> `AGILE_LOOP_OPERATING_MODEL.md` and applicable Technical Plans remain
> authoritative. If this file conflicts with those sources, the authoritative
> sources win.
>
> Snapshot date: 2026-08-08, America/Toronto
>
> Runtime freshness: after PR #78 entered main, STG-007 restarted Batch A from
> exact candidate `35ccf5cb...`. Fresh retained Staging `4397f995...` / V8,
> disabled printing, isolation/resources/Production continuity, import and
> exact release creation passed. Approval was consumed; rotation stopped before
> recovery/env write at its remaining `0700` versus safe `0750` state-parent
> guard. Inert release/approval are preserved and env remains unchanged.

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
| `origin/main` before this STG-007 dependency repair | `35ccf5cb823bb22b449d8b82baa2f22db2e242df` | `IN_MAIN`; merge of releases-parent guard repair PR #78 |
| Owner workspace | `main@ba169ed8b689ddef8dffe94deee82fea191cdcfb`, dirty with Owner work | Local checkout is behind `origin/main`; it was not modified by this handoff |
| Current delivery branch | `codex/ops001-rotation-state-root-mode-repair` | complete rotation state-parent mode reconciliation only; runtime stopped before recovery/env mutation |
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
| `/private/tmp/restaurant-ops001-tooling` | `codex/ops-001-staging-secret-safe-tooling` | Current OPS-001 isolated repository worktree |
| `/private/tmp/restaurant-post-stack-audit` | `codex/post-stack-ground-truth-audit` | Retained historical PR #72 worktree |
| `/private/tmp/restaurant-current-handoff` | `codex/current-project-handoff` | Retained historical PR #71 worktree |
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

Main stack review order:

`#61 -> #62 -> #63 -> #64 -> #65 -> #67 -> #68 -> #69 -> #70`

PR #66 is independent and is now `IN_MAIN`. PR #67 is also `IN_MAIN` as the
Printing Provisioning planning foundation for #68; neither authorizes runtime
printing or device operations.

## 4. Runtime ground truth

STG-006 freshly observed only the bounded runtime identity below.

| Environment | Retained evidence | Classification and boundary |
|---|---|---|
| Production | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`; Compose `cloud` `db/backend/nginx`; unchanged IDs/start times/restart `0`; health 200 | `MACHINE_VERIFIED_READ_ONLY` continuity only; Flyway/business state not queried |
| Staging | `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`; Flyway V8; `db/backend/nginx` running | `MACHINE_VERIFIED_READ_ONLY`; candidate is not deployed |
| Staging isolation | project `restaurant-pos-staging`; only `127.0.0.1:18080`; separate state/network/mounts; private leaf UID 70/mode 0700 | `MACHINE_VERIFIED_READ_ONLY` |
| Staging printing | `STAGING_PRINT_MODE=DISABLED`; feature flag `false` | `MACHINE_VERIFIED_READ_ONLY` |

Repository migrations V1-V10 exist in current main. That does not prove V9 or
V10 ran on Staging or Production.

## 5. Current feature and loop

| Field | Current value |
|---|---|
| Current Feature | `FT-001 Owner Store Onboarding - Chinatown` |
| Current Agile Loop | `STG-007_ROTATION_STATE_ROOT_MODE_GUARD_REPAIR` |
| Current package | Complete environment rotation's state-parent reconciliation with exact `0700`/established `0750`; tests and governance only |
| Feature stop state | `STG-007_BATCH_A_BLOCKED_BY_ROTATION_STATE_ROOT_MODE_REPAIR` until the repair is verified and merged |
| Handoff navigation status | `PROJECT_HANDOFF_IN_MAIN` |
| Current Owner gate | Repair publication uses the Owner's Dependency Repair Auto-Loop policy. After merge, the old exact-SHA approval expires and Batch A must restart from the new merged main. |

### Permitted work

- Fetch and verify Git/GitHub ground truth.
- Review the post-stack capability audit and bounded next-loop ordering.
- Implement and verify only the complete rotation state-root mode repair
  without weakening repository, release, env, recovery or cleanup security.
- Run focused/mock regressions, Agent 6 review, publication gates, and mandatory
  governance sync; auto-merge only if every permanent repository gate passes.

### Prohibited work without new approval

- Further SSH or any Staging/Production mutation while the dependency repair is
  under review; Staging/Production deployment and runtime Docker lifecycle operations.
- Runtime Flyway, bootstrap, credential creation, login, validate/execute, or
  real menu clone.
- Production Store 1 read or mutation.
- Printer configuration, test print, Pad pairing, or device/Worker mutation.
- Any OPS-001 helper against a real runtime, or resumption of STG-007 Batch A/B
  before the repair enters main and Batch A restarts from the new exact SHA.
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

Still missing are the exact-SHA V9/V10 Staging deployment, Synthetic St-Denis
and Owner topology, login/target onboarding, validate/execute/replay/restart
evidence, and the separate Production source, RC, deployment, provisioning and
field-acceptance gates.

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
| OPS-001 | Secret-safe Staging tooling repair | `REPOSITORY_COMPLETE` through PR #74 plus control-path repairs #75-#78 | Every helper remains exact-action Owner-gated for runtime use |
| STG-007 | Exact-SHA deploy and V9/V10 migration | `WAITING_FOR_OWNER_RUNTIME_APPROVAL` after OPS-001 main verification | Bind the new merged-main SHA and approve release/env plus deploy/Flyway as distinct batches |

The authoritative post-stack capability matrix, Staging decision, and next
bounded loops are in [Post-Stack Ground Truth Audit](POST_STACK_GROUND_TRUTH_AUDIT.md).

## 11. Stack rebuild rule

After each dependency enters main:

`fetch latest main -> verify dependency IN_MAIN -> rebuild/rebase/retarget next package -> review diff -> rerun checks -> governance sync -> Owner review`

Do not merge into an intermediate feature branch and report it as main. If a PR
was merged only to a non-main base, keep it `STACKED_ONLY` and promote it again
from current main.

## 12. Known blockers and risks

- STG-006 passive evidence passed; exact-SHA release/deployment approval and
  full Staging acceptance remain pending.
- OPS-001 repository tooling must be verified `IN_MAIN`; its existence does not
  create the runtime release/env, action approvals or STG-007 evidence.
- Synthetic bootstrap, Owner login, validate/execute/replay/restart evidence is
  pending.
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

## 13. START HERE for the next Agent

1. Run `git fetch origin --prune` without altering unrelated local work.
2. Read this file, then read `ALIVE_RUNTIME_PLANBOOK.md`,
   `AGILE_LOOP_OPERATING_MODEL.md`, `FEATURE_BACKLOG.md`, and the applicable
   technical plan.
3. Verify current `origin/main`; do not trust the Owner workspace branch tip.
4. Verify GitHub PR #61 through #72 and independent PR #66 semantics.
5. Distinguish main, stacked Draft, Staging, and Production state.
6. Report the completed main stack and the next Staging Owner Gate.
7. Do not recreate or redesign packages #61-#70.
8. Do not infer implementation from the planning packages.
9. Read STG-006 and OPS-001 evidence; do not repeat runtime observation or run
   STG-007. After OPS-001 is `IN_MAIN`, stop at the exact-SHA STG-007 Owner
   Runtime Gate.
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
- [OPS-001 secret-safe tooling runbook](../../../deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md)
- [System Documentation](../../../SYSTEM_DOCUMENTATION.md)
- [API contract](../../../doc/API.md)
