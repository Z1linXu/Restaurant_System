# Agile Loop Operating Model

> Status: `ACTIVE_GOVERNANCE_PROCESS`
>
> Last updated: 2026-08-04, America/Toronto

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
The current feature loop is AL-003. PR-A through PR-C are in `main`; PR-D,
PR-E, and PR-F0 are stacked-only, and PR-F is not implemented. Its unique stop
state is `AL-003_STACK_PROMOTION_PLAN_WAITING_FOR_OWNER_REVIEW`.

The next possible implementation action remains Owner-gated: promote PR-D,
PR-E, and PR-F0 one layer at a time from the then-latest `main`, with fresh
verification and review at every layer. No current statement authorizes PR-F,
server access, Flyway execution, a runtime clone, merge, or deployment.

## 7. Dependency Repair Gate

When a prerequisite or contract inconsistency is discovered:

1. Stop all downstream implementation packages.
2. Create the smallest prerequisite repair package.
3. Do not continue dependent PRs against an unresolved contract.
4. Resume only after the Owner merges the prerequisite repair.
5. Rebase or rebuild downstream stacked PRs from the new `main`.

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
`AL-005_PRINTING_PROVISIONING_TEMPLATE`, and
`AL-006_STORE_ACTIVATION_WORKFLOW`; naming them here does not authorize or start
their implementation.

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

## 10. Mandatory governance sync

Every code iteration must synchronize governance before it ends. This applies
to features, bug fixes, refactors, promotions, migrations, test-only changes,
and every other non-documentation code change. Governance updates may not be
deferred and accumulated across later iterations.

Before commit and again before the review gate, inspect and update as needed:

- `docs/governance/runtime/ALIVE_RUNTIME_PLANBOOK.md`;
- `docs/governance/FEATURE_BACKLOG.md`;
- this operating model;
- `SYSTEM_DOCUMENTATION.md`;
- `doc/API.md`;
- for AL-003 work,
  `docs/governance/agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md`.

The synchronization must verify that the current loop, stop state, permitted
and prohibited work, main capability, API surface, and deployment state match
Git ground truth. It must correct within the same iteration any completed work
still marked waiting, main change missing from the Planbook, stacked-only work
described as main, unimplemented API described as callable, or code/document
contract mismatch. Every code commit therefore carries the governance changes
needed to describe that exact reviewed scope.
