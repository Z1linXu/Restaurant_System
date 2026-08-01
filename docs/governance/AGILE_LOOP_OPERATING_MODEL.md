# Agile Loop Operating Model

> Status: `ACTIVE_GOVERNANCE_PROCESS`
>
> Last updated: 2026-07-28, America/Toronto

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

`AL-001` is `PLAN_COMPLETE` for `FT-001`. `AL-002` completed local
implementation and verification and is now `AL-002_WAITING_FOR_OWNER_APPROVAL`.
Its scope was limited to owner-scoped onboarding authorization, durable
idempotency, secure staff credential/membership provisioning, and safe
Store-default handling on an independent review branch. `AL-003` (menu
clone/print policy), `AL-004` (Owner UI), and `AL-005` (production
provisioning) are not authorized to enter implementation.

`STG-001` is the current delivery-governance loop in `DISCOVER -> PLAN`. Its
isolated Staging plan is `PLAN_COMPLETE_WAITING_FOR_OWNER_APPROVAL`. It does
not change the AL-002 approval state and does not authorize STG-002
implementation, server access, Docker/Flyway execution, merge, deployment, or
AL-003 implementation.

## 7. Reusable Store-profile architecture

Store onboarding and provisioning work must treat each restaurant configuration
as a versioned Store Profile consumed by shared services, repositories,
transactions, and idempotency controls. A profile may define Store-specific
catalog, pricing, ordering, and provisioning inputs, but shared implementation
must not branch on a restaurant name or a hard-coded target Store ID. Store 1 is
the reviewed source input for the current AL-003 profile, not a permanent engine
restriction, and `ChinatownMenuCloneProfile` is the first profile rather than
the only supported profile.

Future design direction includes a Generic Store Provisioning Engine, Store
Profile Framework, Printing Provisioning Module, Staff/Table Provisioning
Module, Device/Pad Provisioning Module, and Store Activation Workflow. Candidate
loops are `AL-004_GENERIC_STORE_PROFILE_FRAMEWORK`,
`AL-005_PRINTING_PROVISIONING_TEMPLATE`, and
`AL-006_STORE_ACTIVATION_WORKFLOW`; naming them here does not authorize or start
their implementation.
