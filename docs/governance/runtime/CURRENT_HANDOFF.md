# Current Project Handoff

> This Handoff is a navigation snapshot only.
> Git ground truth, `ALIVE_RUNTIME_PLANBOOK.md`, `FEATURE_BACKLOG.md`,
> `AGILE_LOOP_OPERATING_MODEL.md` and applicable Technical Plans remain
> authoritative. If this file conflicts with those sources, the authoritative
> sources win.
>
> Snapshot date: 2026-08-08, America/Toronto
>
> Runtime freshness: no SSH, database, Docker runtime, Android, or Production
> access was performed for this handoff.

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
| `origin/main` | `dc682203b2b24bbdb453a5520b297b9051139f13` | `IN_MAIN`; merge of PR #69 |
| Owner workspace | `main@ba169ed8b689ddef8dffe94deee82fea191cdcfb`, dirty with Owner work | Local checkout is behind `origin/main`; it was not modified by this handoff |
| Handoff branch | `codex/current-project-handoff` | Documentation-only snapshot; PR #71 remains `IN_MAIN` and is an ancestor of the current `origin/main` above |
| Handoff PR | [PR #71](https://github.com/Z1linXu/Restaurant_System/pull/71) | `IN_MAIN`; its GitHub merge commit is `5baada03935e004d80af1e7a36fb7db39bd6abbb` |

`IN_MAIN`, `DRAFT_PR`, `STACKED_ONLY`, `PREPARATION_ONLY`,
`DEPLOYED_TO_STAGING`, and `DEPLOYED_TO_PRODUCTION` are distinct states. A
GitHub Merged badge into a non-main base is not evidence that work entered
`main`.

### Relevant worktrees at snapshot time

| Worktree | Branch / purpose | State |
|---|---|---|
| `/Users/xuzilin/projects/Restaurant_System` | Owner `main` workspace | Dirty with Owner work; behind `origin/main` and untouched |
| `/private/tmp/restaurant-al006-activation-plan` | PR #69 | Temporary rebuild worktree; removed after push; branch retained for Owner review |
| `/private/tmp/restaurant-rel001-rc-plan` | PR #70 | Temporary rebuild worktree; removed after push; branch retained for Owner review |
| `/private/tmp/restaurant-current-handoff` | this handoff | Documentation-only worktree |

Other `git worktree list` entries were historical prunable registrations. They
are not active delivery inputs and were not cleaned by this task.

## 3. Draft PR dependency map

GitHub state was read on 2026-08-08. Every PR below was open and Draft.

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
| #70 | REL-001 Production RC plan | `main` | `4e69a04195c8b58b37d778ef8d83c5eb7715a501` | `DRAFT_PR` | #69/main | No | Owner review; no ACT-001 or runtime action |

Main stack review order:

`#61 -> #62 -> #63 -> #64 -> #65 -> #67 -> #68 -> #69 -> #70`

PR #66 is independent and is now `IN_MAIN`. PR #67 is also `IN_MAIN` as the
Printing Provisioning planning foundation for #68; neither authorizes runtime
printing or device operations.

## 4. Runtime ground truth

These are retained historical evidence, not fresh observations made for this
handoff.

| Environment | Retained evidence | Classification and boundary |
|---|---|---|
| Production | reported commit `4667f3c`; Flyway V7; Compose `db/backend/nginx`; PAD_DIRECT field printing | `NOT FRESHLY VERIFIED`; full 40-character runtime SHA was not retained in this snapshot |
| Staging | `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`; Flyway V8 | `NOT FRESHLY VERIFIED`; historical STG-004 evidence only |
| Staging isolation | Compose project `restaurant-pos-staging`; `127.0.0.1:18080`; independent state | `NOT FRESHLY VERIFIED` |
| Staging printing | `DISABLED` | `NOT FRESHLY VERIFIED` |

Repository migrations V1-V10 exist in current main. That does not prove V9 or
V10 ran on Staging or Production.

## 5. Current feature and loop

| Field | Current value |
|---|---|
| Current Feature | `FT-001 Owner Store Onboarding - Chinatown` |
| Current Agile Loop | `REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE` |
| Current package | Rebuilt Draft PR #70 REL-001 Chinatown Production Release Candidate plan |
| Feature stop state | `REL-001_RC_PLAN_PREPARED_WAITING_FOR_STAGING_ACCEPTANCE_AND_OWNER_APPROVAL` |
| Handoff stop state | `PROJECT_HANDOFF_IN_MAIN` |
| Current Owner gate | Review rebuilt #70; separately approve any runtime or Production action |

### Permitted work

- Fetch and verify Git/GitHub ground truth.
- Review Draft PRs and rebuild the next stacked layer from latest main after its
  dependency enters main.
- Run local tests, independent review, and mandatory governance sync.
- Create bounded repairs when the Dependency Repair Auto-Loop applies.

### Prohibited work without new approval

- SSH, Staging or Production deployment, runtime Docker lifecycle operations.
- Runtime Flyway, bootstrap, credential creation, login, validate/execute, or
  real menu clone.
- Production Store 1 read or mutation.
- Printer configuration, test print, Pad pairing, or device/Worker mutation.
- PR merge, auto-merge, Production activation, restore, destructive database or
  Git commands.

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
  PR #70 is the rebuilt main-based Draft; no later package is authorized.

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
| REL-001 / #70 | Formal Chinatown Production RC plan | `DRAFT_PR_WAITING_FOR_OWNER_REVIEW` | latest `main` with #69 IN_MAIN; no candidate, deployment, or activation |
| ACT-001 | Production provisioning and field acceptance | `NOT_STARTED_OWNER_GATED` | Accepted RC and explicit Production activation approval |

## 11. Stack rebuild rule

After each dependency enters main:

`fetch latest main -> verify dependency IN_MAIN -> rebuild/rebase/retarget next package -> review diff -> rerun checks -> governance sync -> Owner review`

Do not merge into an intermediate feature branch and report it as main. If a PR
was merged only to a non-main base, keep it `STACKED_ONLY` and promote it again
from current main.

## 12. Known blockers and risks

- Exact-SHA Staging runtime approval and acceptance are pending.
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
4. Verify GitHub PR #61 through #70 and independent PR #66.
5. Distinguish main, stacked Draft, Staging, and Production state.
6. Report the current queue and review order to the Owner.
7. Do not recreate or redesign packages #61-#70.
8. Do not create duplicate implementation while the Draft queue is unreconciled.
9. After an Owner merge, rebuild only the next dependency layer from latest
   main and rerun its checks/review/governance sync.
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
- [System Documentation](../../../SYSTEM_DOCUMENTATION.md)
- [API contract](../../../doc/API.md)
