# Agile Loop Operating Model

> Status: `ACTIVE_GOVERNANCE_PROCESS`
>
> Last updated: 2026-08-08, America/Toronto

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
- Codex must not merge a PR, deploy, SSH into production, initialize production
  data, or access/record production secrets without explicit owner approval.
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
The current feature loop is AL-003. PR-A through PR-F are in `main`; that is
repository capability only and is not Staging or Production acceptance.

PR #59's bounded PostgreSQL private-leaf repair and PR #60's Owner-decision
governance sync are `IN_MAIN` at
`2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`; this does not prove a new Staging
deployment. The current package records the modular Store provisioning
architecture before STG-005B implementation. Its review state is
`STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN_WAITING_FOR_OWNER_REVIEW`.
No current statement authorizes server access, Flyway execution, synthetic
bootstrap, credential creation, login, source-menu writes, validate, execute,
a runtime clone, merge, or deployment. Staging acceptance retains the distinct
prerequisite
`AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING` until synthetic-only runtime
evidence proves the complete Owner login topology.

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
test, commit, push, open Draft PRs, perform independent review, and synchronize
governance. They may not merge Owner-gated PRs, enable auto-merge, force-push a
reviewed branch over others, deploy or mutate Production, perform a real clone,
or bypass a runtime gate.
