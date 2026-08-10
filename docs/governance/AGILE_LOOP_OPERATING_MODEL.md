# Agile Loop Operating Model

## Current reconstruction NO-GO override (2026-08-10)

The Owner granted `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`. The runtime batch
did not begin because pre-write validation proved the retained manifest is not
a complete or V7-schema-consistent reconstruction input. The loop is now
`TWIN-001_RECONSTRUCTION_NO_GO_WAITING_FOR_MANIFEST_COMPLETION_READ_APPROVAL`.
Its next TRUE OWNER GATE is
`TWIN-001_RECONSTRUCTION_MANIFEST_COMPLETION_READ_APPROVAL`; no prior approval
authorizes that corrected Production read.

Local PostgreSQL 16.14 evidence proves `V7 -> V8 -> V9 -> V10` is the intended
append-only forward chain and preserves representative safe Store/menu/table/
access/feature/printing/device configuration. The raw version delta is
`CURRENT_PRODUCTION_VERSION_DIFFERENCE`, never a backward-parity target.
`SCHEMA = BLOCKING_BEHAVIOR_DIFFERENCE` remains at the aggregate Twin level
until the reconstructed St-Denis configuration operates and passes V10 parity
validation. See
[the immutable NO-GO evidence](runtime/TWIN-001_STAGING_RECONSTRUCTION_SCHEMA_NO_GO_EVIDENCE.md).

## Historical verified inventory checkpoint (2026-08-10)

The active loop remains `TWIN-001_ST_DENIS_STAGING_TWIN`, but its independent
Production read gate has now completed under the Owner's bounded
`PRODUCTION_ST_DENIS_CONFIGURATION_READ_APPROVAL`. Fresh `origin/main` is
`34ef8c577dd5e8464ef885bf235b0bece0018503`; Production is retained at
`4667f3c35f85c9f8538f82789d9df1531d4fbc9e`/V7 and isolated Staging at
`1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`/V10. The exact Production identity
is Store `1 / 4483_R_SAINT_DENIS / 4483 R. Saint-Denis` in Organization
`1 / LANZHOU_NOODLES / Lanzhou Noodles`.

The resulting [sanitized parity manifest](runtime/ST_DENIS_TWIN_PARITY_MANIFEST.md)
and [inventory evidence](runtime/TWIN-001_PRODUCTION_INVENTORY_EVIDENCE.md)
contain only explicit, bounded, read-only configuration observations. No
business/history/secret data was selected and no runtime mutation occurred.
The checkpoint stop was
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`;
its next Owner Gate was `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`, which was
later granted before the current pre-write NO-GO.

## Verified continuation state (2026-08-10)

Owner has reprioritized the active route to `TWIN-001_ST_DENIS_STAGING_TWIN`.
The former `STG-009 Phase A -> Chinatown Phase B -> REL-001` route remains
historical and deferred, not cancelled. Staging's designated long-term role is
a Production-like St-Denis Operational Twin and mandatory pre-Production
validation environment. The Twin is not yet established.
The former route is explicitly
`DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY`; its plans and
evidence remain preserved.
STG-005A and STG-005B PLAN/EXECUTE/REPLAY remain
`VALIDATED/CREATED/REPLAYED`; the source menu is `4/3/13/38` and replay is
`2 -> 2`. `IN_MAIN`, `DEPLOYED_TO_STAGING`, and `STAGING_ACCEPTED` remain
distinct. Exact Staging `1a3f2e...` passed formal preflight, V10-to-V10 deploy,
readiness, private credential rotation, API acceptance and real-Chrome
browser-equivalent acceptance without a 401/403. The pre-repair manual failure
remains historical; the former manual-acceptance stop is deferred by the Owner.
The former read-approval stop and inventory checkpoint are historical. The
checkpoint stop was
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
The next independent Owner gate at that checkpoint was
`TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`.

> Status: `ACTIVE_GOVERNANCE_PROCESS`
>
> Last updated: 2026-08-10, America/Toronto

## 1. Required lifecycle

Every operational bug or feature slice moves through these states in order,
with an explicit recorded transition:

`OBSERVE -> TRIAGE -> SELECT -> DISCOVER -> PLAN -> IMPLEMENT -> VERIFY -> OWNER_APPROVAL -> MERGE -> DEPLOY -> POST_DEPLOY_OBSERVE -> CLOSE_OR_REOPEN`

| State | Required outcome |
|---|---|
| `OBSERVE` | Retain bounded field, user, test, or log evidence without guessing. |
| `TRIAGE` | Classify operational impact, priority, scope, and safety boundaries. |
| `SELECT` | Choose one bounded issue loop or feature slice. |
| `DISCOVER` | Identify executable evidence, current constraints, and unknowns. |
| `PLAN` | Publish acceptance criteria, tests, rollback, and approval boundaries. |
| `IMPLEMENT` | Change only the approved scope on an independent branch. |
| `VERIFY` | Run relevant automated checks and documented manual checks. |
| `OWNER_APPROVAL` | Owner approves the exact reviewed change and any production action. |
| `MERGE` | Merge only after approval. CI success is not a deployment. |
| `DEPLOY` | Owner-approved deployment using the approved operational process. |
| `POST_DEPLOY_OBSERVE` | Capture bounded field/runtime evidence and update the Alive Planbook. |
| `CLOSE_OR_REOPEN` | Close only against acceptance evidence; otherwise reopen with retained history. |

## 2. Loop sizing

- A bug loop contains one P1 issue, or at most two closely related P2 issues.
- A feature loop contains one independently acceptable feature slice.
- A larger feature must be split before `IMPLEMENT`; no endless Codex loop or
  unbounded “finish everything” batch is allowed.
- New scope discovered during work is added to the relevant backlog and selected
  in a later loop, not silently appended to the active loop.

## 3. Branch, PR, and deployment controls

- Work uses an independent branch and reviewable PR. Do not modify `main`
  directly.
- Codex may create a branch, edit approved code/documents, run local tests,
  commit, push, and prepare a PR when explicitly authorized.
- Repository PR merge follows the permanent auto-merge policy in section 16.
  Runtime execution remains separately approval-gated and is never implied by
  repository auto-merge.
- CI success means code verification only. It is not owner approval, merge, or
  deployment approval.
- A migration, data repair, backup/restore action, printer action, or Android
  device action is independently approval-gated even if the PR itself is approved.

## 4. Evidence and safety rules

- Preserve historical evidence. Do not rewrite a prior report to make it match
  newer field results.
- Keep `OPERATOR_CONFIRMED`, `LOG_OBSERVED`, `MACHINE_VERIFIED`, and
  `EVIDENCE_PENDING` distinct.
- Never place credentials, raw tokens, printer endpoints, customer data, raw
  print payloads, or one-time passwords in code, Git, migrations, documentation,
  test fixtures, PR text, or logs.
- No unauthorized destructive action: no `git reset --hard`, `git clean`,
  production `docker compose down -v`, database wipe, restore, `Flyway clean`,
  or unapproved migration/repair.
- No automatic reprint, print claim, or print state change is introduced merely
  to verify a loop.

## 5. Closure and reopening

- `POST_DEPLOY_OBSERVE` must update
  [ALIVE_RUNTIME_PLANBOOK.md](runtime/ALIVE_RUNTIME_PLANBOOK.md) and the
  relevant backlog item.
- A loop closes only when all stated acceptance criteria have evidence.
- Missing acceptance evidence means `REOPENED` or `EVIDENCE_PENDING`, not
  “probably complete.”
- Closed historical issues remain discoverable with their original evidence and
  may be reopened if a new incident is observed.

## 6. Current application

`AL-001` is `PLAN_COMPLETE` for `FT-001`. AL-002's backend foundation was
merged into `main` by PR #27 but is not thereby deployed or production-ready.
AL-003 PR-A through PR-F are in `main`; that is repository capability only and
is not Staging or Production acceptance. Current dependency-bound preparation
is superseded by TWIN-001 planning; REL-001 remains preserved deferred planning.

PR #59's bounded PostgreSQL private-leaf repair is now `IN_MAIN`, and PR #60's
2026-08-08 Owner decisions are `IN_MAIN` at
`2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`; neither proves a new Staging
deployment. PR #71's handoff navigation, PRs #61-#70, and PR #72's post-stack
audit are all `IN_MAIN`; PR #73 then entered
`origin/main@85d97b7327b2e15aa561ed28a5788b92cedf6f5b`. PR #66 is the
independent Printer Store-isolation code repair. The remaining overnight layers
are architecture, contracts, plans, or guarded preparation except for the
implemented STG-005B baseline and AL-003S tooling; none changes runtime state.
STG-006 freshly verified the retained isolated Staging runtime and minimum
Production continuity without mutation. PR #73 placed that evidence/governance
package in `main` at `85d97b7327b2e15aa561ed28a5788b92cedf6f5b`.
OPS-001 now supplies the bounded secret-safe repository tooling. PR #81 is
`IN_MAIN` at `63600b13b10a5549d9095a03c94e69a9f880af9f`; its historical
V10-aware runtime use ended at restart `NO_GO`. PR #82 then entered `main` at
`2837ae88e55142c99c6975f8b6575febffc913a1` with bounded three-endpoint
restart readiness and nonzero-exit blocked-state persistence. A fully fresh,
separately authorized V10-to-V10 continuation deployed that exact SHA to
isolated Staging and passed formal preflight, repaired readiness, sanitized
Flyway/runtime collection, one same-image restart and post-restart
verification. Flyway remained V10/no-pending; printing/isolation and Production
continuity were unchanged. `STG-007=PASS`. Exact `2a6c30a...` later validated
PR #89 in Staging before exposing the separate non-web one-shot lifecycle
defect. PR #91 is `IN_MAIN` at `9a776d3...`; later exact `1a3f2e...` completed
the authorized synthetic prerequisites, deployed the browser repair and passed
API plus real-Chrome browser-equivalent acceptance. That Phase-A stop is now
historical and deferred by the Owner Twin route.

Fresh read-only state inspection found a bounded recovery-tooling defect: the
retained current lock has the reviewed ordered cleanup-plus-action-failure
records, while the recovery prerequisite accepted only a one-line legacy
shape. This enters the Dependency Repair Auto-Loop. Its exact pair-only
compatibility repair must pass the permanent repository gates, merge, and be
rebound under the already authorized same-scope Staging sequence; it does not
authorize a manual clear or a broader blocked-state bypass.
PR #83 merged only the final STG-007 evidence/governance into
`main@2ed56b06f37c9257a655ec334f81e31ca4a518a6`; exact Staging correctly
remained `2837ae88...`. The Owner then authorized STG-008. Its read-only entry
reconfirmed V10/readiness/printing/isolation and Production continuity, found
zero synthetic topology/credential rows, and safely proved the next Store ID
is `1`. It stopped before `bootstrap-plan` because the requested account
convention does not satisfy the reviewed `STG005_` identity and
12-through-256 password contract. No one-shot or data write occurred.
PR #84 merged that sanitized evidence/governance into
`main@828af4e84581dcb051248beee694c307a65210c5`. The Owner then approved
`STG005_OWNER_20260808_R01` without weakening the password guard. Fresh exact
readiness passed, but the password-free bootstrap plan one-shot stopped during
shared startup safety validation: its reviewed profile must keep Flyway
disabled, while the older cloud rule required Flyway enabled. The command,
credential reader, and transaction did not run; cleanup succeeded, topology
remained empty, V10 and both environments remained healthy/unchanged, and the
launcher retained fail-closed state. The bounded repository repair models the
exact no-migration one-shot shape while retaining all ordinary cloud checks.
PR #61 is the architecture/governance foundation: it defines the Generic
Store Provisioning Engine, Versioned Store Profiles, and Reusable Provisioning
Modules without adding runtime behavior. PR #62 provides the guarded Synthetic
St-Denis baseline, PR #63 provides guarded acceptance preparation, and PR #64
supplies the generic version/profile identity, module-reference, and
canonical-fingerprint contract as repository capability, not runtime evidence.
PR #65 supplies reusable Staff/Access and Table planning boundaries as
repository capability only.
The completed STG-007 authority does not authorize another deploy/restart,
Flyway execution, synthetic bootstrap, credential creation, source-menu write,
login, target onboarding, validate, execute, replay, clone, or Production
action. Every consumed STG-007 approval remains non-replayable. The credential
decision is resolved, but the failed plan readiness/approval cannot be reused.
PR #85 entered main with the bounded startup-safety repair and PR #86 closed
its documentation Ground Truth. The Owner then authorized a continuation
conditionally bound to exact `4759a23b...`. Fresh Git/runtime observation
reconfirmed deployed `2837ae88...`, V10, zero synthetic state, retained exact
blocked records, Printing disabled, isolation and Production continuity. Before
Batch A mutation, control-flow review found that ordinary release rotation
must pass the block that the approved sequence may clear only after the new
runtime is deployed. The Agent therefore entered Dependency Repair rather than
bypass or prematurely clear state. PR #87 entered main at `4b954e09...` with
a recovery-only, digest-bound release/env preparation path that preserves both
blocked records and leaves the ordinary action gate unchanged. A later
Owner-authorized continuation used it for exact `6753855497...`: release/env
binding, formal preflight, V10-to-V10 Staging deploy, readiness and recovery of
only the reviewed old blocked pair all passed. The subsequent password-free
STG-005A plan failed before its command or data path because non-web startup
required a servlet-bound request context. The resulting fail-closed records
are historical; later exact continuation cleared the reviewed state, and
current topology/runtime evidence is complete.

The bounded non-web request-context repair was runtime-validated by exact
`2a6c30a...`; PR #91 then placed the separate lifecycle repair in main at
`9a776d3...`. Later continuations superseded this historical checkpoint and
deployed exact `1a3f2e...`. See [its evidence](runtime/STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md).
The restarted batch was completed by retained exact runtime evidence;
STG-005A/B must not be repeated. The former
`AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING` is historical Phase-A
context, not the current Agile Loop.

The authoritative post-stack matrix remains historical capability evidence.
The next loop is TWIN-001; see [TWIN-001 St-Denis Twin Plan](agile/TWIN-001_ST_DENIS_STAGING_TWIN_PLAN.md)
and [POST_STACK_GROUND_TRUTH_AUDIT.md](runtime/POST_STACK_GROUND_TRUTH_AUDIT.md).
STG-006/7/8 and Phase-A evidence remain scoped historical runtime records.
The synthetic baseline is not yet a parity Twin, and no prior PASS proves
Production approval.

The architecture authority for future provisioning packages is
[STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md).
It classifies each Store-related change as shared capability, Store Profile,
reusable module, runtime-only configuration, or operational evidence before
implementation.

## 7. Dependency Repair Gate and Auto-Loop

When `PLAN`, `PREFLIGHT`, `IMPLEMENT`, `VERIFY`, or Staging acceptance discovers
a blocker or `NO_GO`, preserve the immutable evidence and classify the root
cause before choosing the next state:

`DETECT BLOCKER -> CLASSIFY ROOT CAUSE -> CHECK AUTHORIZED BOUNDARY -> BOUNDED DEPENDENCY REPAIR -> MINIMAL FIX -> FOCUSED TESTS -> REGRESSION -> INDEPENDENT REVIEW -> GOVERNANCE SYNC -> DRAFT PR -> OWNER REVIEW`

The Agent must automatically enter that bounded repair sub-loop when the root
cause and minimal correction are clear, the correction remains inside the
authorized package, and it does not change an approved product contract,
expand business scope, reduce a safety boundary, require Production mutation,
write real business data, perform an irreversible action, or require a new
Owner decision. A reproducible `NO_GO`, test failure, compilation failure, or
infrastructure guard defect is not by itself a reason to abandon the loop.

The following sequence is prohibited for an in-boundary repairable blocker:
`record evidence -> update Planbook -> stop without repair`. The required
sequence is `preserve evidence -> repair -> test -> review -> governance sync ->
Draft PR`. Evidence retention and continued bounded repair are complementary.

Stop at `WAITING_FOR_OWNER_DECISION` only when one of these gates applies:

- **Contract Gate:** product behavior, API contract, Store Profile rules,
  pricing, ordering, authorization, payment/refund, or printing semantics must
  change.
- **Safety Gate:** the proposed path weakens permissions, bypasses a guard,
  disables validation, or changes Production firewall/environment/security.
- **Runtime Mutation Gate:** any Staging or Production deployment, migration,
  bootstrap, clone, account/data write, container lifecycle change, or other
  runtime mutation requires explicit Owner authorization; Production writes,
  destructive or irreversible database work, restore, and real business-data
  mutation remain independently gated.
- **Scope Gate:** the repair is materially outside the selected package.
- **Ambiguity Gate:** materially different safe options require an Owner
  product or operations decision.

Downstream packages remain paused until the Owner merges the repair. They must
then be rebased or rebuilt from the new `main`; old exact-SHA approvals and
runtime evidence cannot authorize a changed commit.

## 8. Store Profile Principle

Shared implementation must remain generic. Store-specific behavior must never
be implemented inside shared services by branching on Store IDs or Store names.
Every Store-specific behavior must be represented by a reviewed, versioned
Store Profile.

Store onboarding and provisioning work must treat each restaurant configuration
as a versioned Store Profile consumed by shared services, repositories,
transactions, and idempotency controls. A profile may define Store-specific
catalog, pricing, ordering, and provisioning inputs, but shared implementation
must not branch on a restaurant name or a hard-coded target Store ID. Store 1 is
the reviewed source input for the current AL-003 profile, not a permanent engine
restriction, and `ChinatownMenuCloneProfile` is the first profile rather than
the only supported profile or a special-case implementation.

The long-term architecture target is a Generic Store Provisioning Engine plus
Versioned Store Profiles plus Reusable Provisioning Modules, capable of
provisioning an arbitrary number of Stores without modifying shared business
logic.

Future design direction includes a Generic Store Provisioning Engine, Store
Profile Framework, Printing Provisioning Module, Staff/Table Provisioning
Module, Device/Pad Provisioning Module, and Store Activation Workflow. Candidate
loops are `AL-004_GENERIC_STORE_PROFILE_FRAMEWORK`,
`AL-005A_STAFF_TABLE_PROVISIONING_MODULES`,
`AL-005_PRINTING_PROVISIONING_TEMPLATE`,
`AL-005B_DEVICE_PAD_PROVISIONING_MODULE`, and
`AL-006_STORE_ACTIVATION_WORKFLOW`. Production promotion/activation candidates
are `REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE` and
`ACT-001_CHINATOWN_PRODUCTION_ACTIVATION`. Naming them here does not authorize
or start their implementation.

### 8.1 Owner/Admin Store provisioning principle

A Store selected for onboarding is a real operational target unless its plan
explicitly labels it synthetic. Chinatown is the second planned Production
Store, not a demo or one-off clone exercise. Completion therefore means a
Production-ready Store, not merely a created Store row or a successful menu
clone transaction.

The normal long-term workflow is Owner/Admin initiated and must reuse shared
provisioning modules:

`Create Store -> choose versioned Store Profile -> provision access/staff -> provision menu -> provision tables -> provision printing -> provision devices -> activation validation -> activate Store`

Programmer-run SQL, copied database rows, per-Store ID/name branches, and a new
clone engine per restaurant are forbidden as routine provisioning mechanisms.
An Organization Owner automatically receives access to Stores in that
Organization when the current authorization contract grants Organization-level
Owner access; provisioning must not manufacture redundant Store memberships.
Store-scoped staff continue to require explicit Store memberships.

The reviewed Chinatown profile and a future versioned St-Denis profile are
separate inputs to the same generic engine. A profile identifies reviewed
Store-specific data; it does not authorize runtime execution or activation.

### 8.2 Production-like Staging and release-candidate principle

Staging is a long-lived Production-like acceptance environment with
synthetic-only data. It may mirror Production configuration shape, menu shape,
roles, permissions, feature flags, ordering behavior, and printing semantics,
but it must not reuse Production credentials, password hashes, tokens,
customers, orders, payment data, printer endpoints, device secrets, or a copy
of the Production database. Printing remains `DISABLED` or an explicitly
approved non-real mode; real printers and Pads require their own Owner gate.

Production promotion follows:

`Staging accepted exact SHA -> Production gap audit -> Production Release Candidate -> migration/backup/rollback review -> Owner approval -> exact-SHA Production deployment -> post-deploy verification`

Deploying the latest branch tip without this chain is prohibited. Repository
capability, Staging deployment, Staging acceptance, release-candidate status,
and Production deployment remain separate governance states.

### 8.3 Canonical release, promotion, drift, and recovery policy

This section is the canonical repository policy for release promotion. TWIN-001
and REL-001 provide route-specific evidence and gates; neither may create a
second or weaker release authority.

#### Release Candidate Freeze

After the St-Denis Twin passes automated regression, classified parity checks,
and required Owner manual acceptance, freeze an immutable Release Candidate
record before any Production promotion. The record must bind a unique `RC_ID`,
the exact 40-character source SHA and verified `main` ancestry, backend and
frontend image digests, relevant Android artifact identity, migration/Flyway
target and checksums, parity-manifest identity, automated acceptance result,
Owner acceptance result, and build timestamp/manifest metadata. Freeze also
binds the reviewed release scope and approval identity. Later `main` movement
does not move the frozen RC; Production may promote only that RC. Any source,
artifact, migration, parity, evidence, environment or scope change requires a
new RC and a fresh Staging acceptance chain.

#### Build once, promote the same artifact

The required flow is:

`build once -> deploy exact artifacts to Staging -> validate -> promote the same artifact digests to Production`.

Rebuilding the same source SHA separately for Production is not equivalent to
promoting the accepted artifact. Before Production promotion, the backend,
frontend, and relevant Android artifact identities/digests must match the
Staging-accepted release manifest. If current tooling cannot carry the exact
artifact digests through promotion, record a tooling implementation gap and
stop the promotion; do not alter deployment runtime in a planning loop.

#### Continuous drift detection and explicit Twin sync

Production-to-Twin drift detection is recurring and read-only. It may emit
sanitized fingerprints/manifests for `MENU_DRIFT`, `TABLE_DRIFT`,
`STAFF_DRIFT`, `ACCESS_DRIFT`, `FEATURE_DRIFT`, `PRINTING_DRIFT`,
`DEVICE_DRIFT`, and other approved parity domains. Every result is classified
as `MATCH`, `EXPECTED_ENVIRONMENT_DIFFERENCE`,
`SANITIZED_DATA_DIFFERENCE`, `TEST_HARDWARE_DIFFERENCE`,
`BLOCKING_BEHAVIOR_DIFFERENCE`, or `NOT_YET_VERIFIED`.

Detection never mutates Staging and never silently overwrites Owner test
state. A drift finding follows:

`report -> classify -> explicit Owner-approved, Owner-triggered Twin sync request -> bounded Twin sync -> parity validation`.

Any `BLOCKING_BEHAVIOR_DIFFERENCE` makes Production promotion `NO_GO`. This
policy grants no Production-read or Staging-mutation authority; every sync or
runtime execution remains separately bound to an explicit Owner gate.

#### Rollback-first compatibility gate, otherwise roll-forward

Production incident handling is `rollback-first WHEN compatibility is proven;
otherwise roll-forward`. The mandatory
`APPLICATION_ROLLBACK_COMPATIBILITY_GATE` binds the current schema and
migration state, previous application artifact identity, compatibility
evidence, rollback target identity, and post-rollback health verification. An
old application must never be assumed compatible with a newer Flyway schema.
If compatibility is unknown or false, automatic rollback is forbidden and a
bounded emergency roll-forward repair is required. Destructive database
rollback, restore, Flyway history edits, and volume deletion require a
separate Owner gate and are never implicit in application rollback.

#### Backup and restore readiness

`backup existence != proven recoverability`. Release maturity requires current
backup integrity evidence, a reviewed recovery boundary, and an appropriately
periodic isolated restore rehearsal with recorded recovery point/time
expectations. Ordinary releases need not restore Production, but a release may
not claim recovery readiness when integrity, rehearsal status, or boundaries
are unknown. Backup creation, restore rehearsal, and restore execution remain
separately approved operations.

#### Source direction and responsibilities

Application-code source is the latest approved shared candidate/RC. Production
St-Denis is the operational configuration parity source. Copying a currently
deployed old Production application binary or code backward into Staging is
forbidden; the Twin validates the next shared candidate, not an old binary.

Owner owns product requirements/bug reports, Staging manual acceptance,
strategic phase transitions, and final Production approval. Codex owns bounded
reproduction, planning within authority, implementation, tests, Agent 6 review,
PR/auto-merge, exact-SHA Staging deployment when separately authorized,
automated regression, and evidence/governance sync. An Owner-reported ordinary
bug is one repair package -> tests -> review -> auto-merge -> separately
authorized Staging deploy -> Owner retest; Codex is not authorized to invent an
unbounded “no bugs remain” loop.

## 9. Git ground truth and stacked PR rules

Every status decision must distinguish these states explicitly:

- `MERGED_ON_GITHUB`: GitHub reports a PR merged into its configured base.
- `IN_MAIN`: the relevant head or reviewed equivalent is an ancestor of the
  current `origin/main`.
- `DEPLOYED_TO_STAGING`: retained runtime evidence identifies the exact Staging
  commit and environment.
- `DEPLOYED_TO_PRODUCTION`: retained production evidence identifies the exact
  production commit and environment.

A GitHub `Merged` badge does not imply `IN_MAIN`. For a stacked PR whose base
is not `main`, merge means only that its changes entered that base branch.
Promotion must proceed one dependency layer at a time from the then-latest
`main`; each layer receives fresh diff review, tests, Owner review, and its own
merge decision. Stacked merge commits must not be treated as main merge SHAs or
used to collapse multiple layers into one unreviewable promotion.

Every loop ends with exactly one stop state in the Alive Runtime Planbook.
Production runtime, current `main`, stacked-only development, and unimplemented
work must remain separate in every governance record.

Status reporting must keep these layers distinct:

`IMPLEMENTED_IN_WORKTREE`, `DRAFT_PR`, `MERGED_TO_STACKED_BASE`, `IN_MAIN`,
`STAGING_RELEASE_CANDIDATE`, `DEPLOYED_TO_STAGING`, `STAGING_ACCEPTED`,
`PRODUCTION_RELEASE_CANDIDATE`, `DEPLOYED_TO_PRODUCTION`,
`OPERATOR_CONFIRMED`, and `MACHINE_VERIFIED`.

In particular, a GitHub merge is not automatically `IN_MAIN`; `IN_MAIN` is not
deployment; and Staging evidence is not Production evidence.

## 10. Mandatory governance sync

Every completed feature, bug fix, refactor, promotion, dependency repair,
migration, test package, Staging gate, deployment, acceptance, or rollback must
synchronize governance before it ends. Governance updates may not be deferred
and accumulated across later iterations.

Before commit and again before the review gate, inspect and update as needed:

- `docs/governance/runtime/ALIVE_RUNTIME_PLANBOOK.md`;
- `docs/governance/FEATURE_BACKLOG.md`;
- this operating model;
- `SYSTEM_DOCUMENTATION.md`;
- `doc/API.md`;
- `docs/governance/KNOWN_ISSUES_BACKLOG.md` when the change affects an issue;
- for AL-003 work,
  `docs/governance/agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md`.

Every listed authority must be checked, but a file should receive a diff only
when its facts or navigation need to change. Do not manufacture documentation
churn merely to prove that it was inspected.

The synchronization must verify that the current loop, stop state, permitted
and prohibited work, main capability, API surface, and deployment state match
Git ground truth. It must correct within the same iteration any completed work
still marked waiting, main change missing from the Planbook, stacked-only work
described as main, unimplemented API described as callable, or code/document
contract mismatch. Every code commit therefore carries the governance changes
needed to describe that exact reviewed scope.

## 11. Planbook ground-truth rule

Every iteration starts by reading the Alive Runtime Planbook, verifying Git
ground truth, and, only when separately authorized, verifying runtime ground
truth. The Agent must identify the current loop, unique stop state, allowed
actions, prohibited actions, unresolved risks, and exact evidence authority.
Conversation history is not a substitute for this check.

The concise [Current Project Handoff](runtime/CURRENT_HANDOFF.md) may accelerate
conversation transfer, but it is navigation only and never overrides this
model, the Planbook, Feature Backlog, technical plans, Git, or verified runtime
evidence.

When Git or authorized runtime evidence conflicts with the Planbook, ground
truth wins and governance drift is repaired in the same iteration. Work must
not continue on a Planbook already known to be stale.

## 12. Continuous Agile Loop rule

After a package is complete, read the Planbook again and identify the next
allowed action. Continue automatically when that action is inside the current
authorization and needs no new Owner decision. Preparation for a dependent
package may continue without pretending that an unmerged dependency is
satisfied. Stop when an Owner merge, runtime authorization, product decision,
or another gate in section 7 is genuinely required.

Agents may create isolated worktrees and branches, edit the authorized scope,
test, commit, push, open Draft PRs, perform independent review, synchronize
governance, and apply section 16's repository auto-merge policy. They may not
force-push a reviewed branch over others, deploy or mutate any runtime,
perform a real clone, or bypass a runtime gate.

## 13. Ephemeral Agent / Worker Lifecycle

Every multi-agent, sub-agent, and worker is a temporary execution resource.
After a bounded task completes, the Agent must:

`finish assigned task -> return result/evidence to Coordinator -> confirm the
result is persisted where required -> terminate its active session/process ->
release temporary resources -> clean obsolete task-owned scratch/build/worktree
resources when safe`

Completed Agents must not remain running or attached indefinitely. The
Coordinator owns lifecycle accounting and must end a round with zero active
Agents unless a clearly named task is still executing.

## 14. Worktree and disk cleanup safety

Cleanup may remove only known task-owned temporary scratch directories, test
output, obsolete build caches, unused detached rehearsal worktrees, and
terminated worker/session resources after commits, pushed branches, PRs, and
required evidence are safely persisted. Never automatically delete an
unmerged branch, reviewed commit, Draft PR branch, active worktree, uncommitted
evidence, runtime/database/backup data, or a shared dependency cache without a
specific safety proof.

Do not use `git clean -fdx`, `git reset --hard`, broad `rm -rf`,
`docker system prune -a`, or `docker volume prune` for disk pressure. Before
cleanup, record the resource owner, purpose, and safe-to-delete decision;
report retained unknown or historical artifacts instead of guessing.

Each round reports Agents spawned/completed/active, temporary worktrees
created/retained/removed, and known leftover large artifacts.

## 15. Smart Multi-Agent execution policy

The Coordinator must build a task-dependency graph before assigning work. A
candidate task may run in another Agent only when it can start immediately and
finish independently without another Agent's unfinished output. Discovery,
planning, implementation, verification, and governance reconciliation stay
with the same Coordinator when they form one serial chain; they must not be
split merely to increase Agent count.

The default is one Coordinator. Use the minimum Agents required for maximum
safe parallelism, normally zero to three independent workers plus an optional
independent reviewer. Agent count is not a success metric.

Runtime interaction follows a stricter rule:

`ONE RUNTIME ENVIRONMENT = ONE ACTIVE EXECUTOR`

One Runtime Coordinator owns the complete Staging and Production observation
timeline. Other Agents may inspect repository state or analyze already
sanitized evidence, but must not concurrently SSH, inspect overlapping runtime
state, run runtime scripts, or collect competing evidence. This applies even
to read-only work.

An independent reviewer may start after implementation or evidence collection
is complete. The reviewer does not edit or silently repair the work; findings
return to the Coordinator for disposition. This sequential launch is allowed
because it preserves review independence rather than pretending to be parallel
delivery.

At normal completion, every bounded worker has returned its result and ended,
and the round reports `Agents active = 0`. The lifecycle and worktree cleanup
rules in sections 13 and 14 remain mandatory.

### Historical STG-008 one-shot lifecycle dependency repair

The exact `2a6c30a...` Staging continuation demonstrated that PR #89's
request-context repair works in the guarded non-web runtime: the password-free
STG-005A plan reached `VALIDATED` before credential or data access. It also
identified a distinct lifecycle defect: the one-shot profile instantiated the
long-lived WebSocket broker, so the process reached its 600-second bound and
the fail-closed scoped-cleanup path retained the blocked pair. This is a
bounded repository Dependency Repair, not authority to replay the plan.

The repair may exclude WebSocket infrastructure only from the dedicated
non-web `staging-synthetic-bootstrap` profile. It must preserve every normal
web-runtime WebSocket contract and every one-shot safety guard. After merge,
the repair creates a new runtime-sensitive exact SHA: a fresh Owner Runtime
Gate must bind/deploy it and recover only the matching blocked pair before a
new digest-bound PLAN. Existing approval, evidence, credential, and timeout
artifacts are non-replayable. See
[STG-008 one-shot lifecycle repair evidence](runtime/STG-008_ONE_SHOT_LIFECYCLE_REPAIR_EVIDENCE.md).

PR #91 merged that repair at `9a776d3aaa2c357e1edeac46e54168bda1f5431f`.
Its exact Staging rebind, preflight, V10-to-V10 deployment, readiness and
matching blocked-pair recovery are historical retained evidence; the former
continuous authorization is consumed. No current rebind, recovery, migration,
Production action, destructive operation or product/security/identity change
is authorized. The current route is TWIN-001 planning and its independent
Production read gate.

## 16. Repository auto-merge policy

Repository PRs default to automatic merge after, and only after, the complete
review gate below passes. This standing repository policy is Owner authority to
mark a qualifying Draft ready and merge that exact reviewed head; it is not
authority to weaken branch protection, bypass GitHub checks, or merge a
different scope.

All of these conditions are mandatory:

1. The PR targets current `main`; its base contains the latest `origin/main`,
   its reviewed head is frozen, GitHub reports no conflict, and any base drift
   was handled by Stack Rebuild plus semantic reconciliation followed by fresh
   verification.
2. The diff contains exactly one selected Agile Loop package. No unexpected
   implementation, migration, generated artifact, secret, runtime
   configuration mutation, or unrelated commit is present.
3. Focused and required regressions pass. Compilation/Maven, frontend, Android,
   migration or deployment checks are mandatory whenever that surface changed.
4. `git diff --check`, Markdown-link validation, secret scan and governance
   drift scan pass against the final head.
5. An independent reviewer returns `ACCEPT` with no unresolved blocking
   finding, review thread, requested change, or security/safety ambiguity.
6. GitHub reports `mergeable=true`, clean merge state, no failed or pending
   required check, unchanged base/head/scope, and no unexpected new commit.

Any conflict, base drift, failed check, unresolved finding, product/architecture
ambiguity, safety-boundary change, migration surprise, secret finding, runtime
behavior surprise, or scope change cancels automatic merge and stops at the
Owner Gate. Repairing it requires a new final-head review; a previous ACCEPT or
approval cannot be reused.

Repository auto-merge never authorizes SSH, deployment, Flyway, bootstrap,
credential creation, login, API mutation, container lifecycle, Store/Printer/
Pad mutation, Production read, or any Staging/Production action. Runtime
auto-execution is a distinct policy and remains prohibited unless the Owner
explicitly approves the exact environment, SHA, action batch and rollback/
evidence boundary for that occurrence. `IN_MAIN`, `DEPLOYED_TO_STAGING`,
`STAGING_ACCEPTED`, and `DEPLOYED_TO_PRODUCTION` remain separate states.
