# Repository Agent Constitution

## 1. Purpose

This repository is an active restaurant-system productization and pilot
repository. It is no longer governed as the original single-store MVP.

The operating priorities are:

1. runtime and data safety;
2. business-rule correctness;
3. Store and Organization isolation;
4. reliable ordering and printing;
5. controlled productization through Owner-authorized phases.

Keep solutions focused and maintainable for a solo-developer project. Do not
turn governance or implementation into a framework without demonstrated need.

## 2. Owner Communication

- Owner-facing communication defaults to Chinese.
- Questions, summaries, failure reports, acceptance results and Owner Gates
  must be written in Chinese unless the Owner requests another language.
- Preserve canonical technical identifiers, including filenames, APIs,
  class/function names, table/field names, Git SHAs, branches, environment
  variables, status/error codes, commands and log excerpts.
- Do not mechanically translate source-code, API or schema identifiers.

## 3. Default Required Reading

At the start of substantial work, read only:

1. `AGENTS.md`;
2. `docs/governance/AUTHORITY.md`;
3. `docs/governance/CURRENT_STATE.yml`.

Then use `AUTHORITY.md` to select only the task-specific authority required.
Historical evidence is never part of the default reading bundle.

## 4. Ground Truth

Before substantial work:

```bash
git fetch origin --prune
git status --short --branch
git worktree list
git rev-parse origin/main
```

Establish and report:

- exact fresh `origin/main` SHA;
- current branch/worktree and divergence;
- staged, unstaged and untracked state;
- current Phase, Package, Gate and Stop Marker;
- the task-specific contract or runbook;
- whether the requested mutation is authorized.

Fresh `origin/main` beats chat history, old prompts and stale local branches.
Git proves repository content; it does not prove what is deployed. Runtime
claims require evidence from the exact environment they describe.

If fresh authority materially differs from the request's assumptions, explain
the difference and adapt to the merged authority. Fail closed if that changes
the requested safety boundary.

## 5. Existing Work Protection

- Never reset, clean, unstage, overwrite, discard, commit or merge unknown
  user work.
- Never checkout over a dirty worktree.
- Treat staged, unstaged and untracked content as protected until provenance
  and ownership are established.
- Prefer a fresh worktree based exactly on `origin/main` for isolated work.
- Do not use a stale or dirty Owner workspace as current authority or a release
  input.
- Destructive Git operations require explicit Owner authorization and exact
  target verification.

## 6. Authority and Scope

- Follow `docs/governance/AUTHORITY.md` for precedence, routing and workflow.
- Follow `docs/governance/CURRENT_STATE.yml` for the single current Phase,
  Package, Gate, next action and authorization boundary.
- `CURRENT_STATE.yml` does not override executable code, migrations or verified
  target-environment evidence.
- Historical evidence proves a past event only. It cannot authorize current or
  future mutation.
- A plan, branch, draft PR or merged capability does not by itself authorize
  deployment or runtime mutation.
- Never expand a bounded package because adjacent work appears convenient.

## 7. Agile Loop

Inside an Owner-authorized Phase and Package, use this bounded loop:

```text
Ground Truth
-> bounded task
-> implementation
-> focused tests
-> required review
-> PR
-> merge when authorized
-> fresh fetch
-> authority refresh
-> authorized exact-SHA Staging
-> acceptance
-> concise evidence
-> governance sync
-> next bounded task inside the same authorized Package
```

Ordinary technical failures inside the approved scope enter a bounded repair
loop automatically:

```text
root cause -> minimal repair -> focused validation -> re-review when required
```

Stop when the repair would expand scope, weaken a safety boundary, require an
unapproved real-data action, or cross a TRUE OWNER GATE.

Technical acceptance may close the current technical gate. It never opens the
next Phase or expands the Package without Owner authorization.

## 8. TRUE OWNER GATES

Stop and obtain an explicit Owner decision before:

- opening the next Phase;
- expanding the current Package;
- making a material product/business-rule change outside current authority;
- weakening an authentication, authorization or environment boundary;
- mutating real credentials, Master data or operational data without existing
  explicit authority;
- activating a real Store;
- binding a real Printer or Pad unless the exact action is already authorized;
- destructive or irreversible database work;
- choosing between materially different safety/product alternatives;
- any Production release, deployment, restart, Flyway or data mutation.

When authority is ambiguous, use the safer interpretation and fail closed.

## 9. Business and System Invariants

- Backend Store/Organization authorization is authoritative. Frontend URL or
  Store context is never authorization.
- Retry must preserve one stable idempotency identity.
- Offline drafts and outbox state must not be casually cleared or duplicated.
- A local queued record is not a server-confirmed order.
- Submitted-order updates follow the current add-only/current API contract.
- Print failure must not roll back a committed order.
- `PAD_DIRECT` claim/lease and duplicate-print protections must remain intact.
- Ambiguous physical-print failure must not trigger blind automatic reprint.
- Historical orders and receipts must remain snapshot-safe.
- Environment credentials, configuration, data and device endpoints must stay
  isolated.
- Store Profile or Master Menu materialization must produce independent
  Store-owned state; no shared mutable menu graph is allowed.

Read `SYSTEM_DOCUMENTATION.md`, `doc/API.md` and the routed technical contract
before changing one of these boundaries.

## 10. Database and Migrations

- Staging and Production schema evolution uses reviewed Flyway migrations.
- Never edit an applied migration.
- Use additive forward repair unless an explicitly approved recovery plan says
  otherwise.
- Do not run ad-hoc SQL against a shared environment unless explicitly
  authorized for the exact statement and target.
- Destructive database actions are TRUE OWNER GATES.
- Migration, backup and rollback evidence are environment-specific; do not
  generalize a Staging result to Production.

## 11. API and Implementation

- Never assume a missing API.
- Inspect the current controller, service, DTO, tests and `doc/API.md` before
  changing a contract.
- Preserve existing naming and compatibility unless authority explicitly
  permits a change.
- Keep module boundaries clear and changes minimal.
- Use transaction management for multi-write business flows, including order
  submission, kitchen completion, prep execution, inventory restock and Store
  provisioning/materialization.
- Do not implement Product, Phase or runtime work that is not authorized by
  `CURRENT_STATE.yml` and the Owner request.

## 12. Testing and Review

Use focused tests first, then the repository checks proportionate to risk.
Document what ran and distinguish PASS, FAIL, BLOCKED and NOT RUN.

Agent 6 review is mandatory for material changes involving:

- application code;
- database schema or Flyway;
- authentication/security;
- deployment or runtime tooling;
- printing;
- offline/idempotency behavior;
- architecture-sensitive boundaries.

Pure documentation and trivial mechanical changes may use a documented
risk-based exception unless the Owner explicitly requires Agent 6.

## 13. PR and Merge

- Material work uses a dedicated branch/worktree and a PR.
- Inspect staged and unstaged diffs; stage only confirmed paths.
- Required tests, checks and review must pass before merge.
- Auto-merge is allowed only when current Owner/governance authority explicitly
  allows it and no TRUE OWNER GATE is open.
- After merge, fetch fresh `origin/main`, verify ancestry and reread the three
  default authority files plus authority files actually changed by the merge.
- A GitHub Merged badge into a non-main base is not proof of entry into `main`.

## 14. Staging

Standing Staging authorization exists only inside the current explicitly
Owner-authorized Phase/Package and reviewed safety boundary.

For runtime-sensitive changes inside that authorized Phase/Package, the
default delivery loop continues after merge through fresh exact-SHA Staging
deployment and applicable automated acceptance without a second per-deploy
Owner approval. This includes user-testable application, UI, backend,
printing, API and runtime-contract changes. Ordinary Staging technical failures
remain inside the bounded repair loop and may be fixed, reviewed, merged,
redeployed and re-tested without repeated Owner approval while scope and safety
boundaries remain unchanged.

Documentation-only, governance-only, archive/cleanup, comment/formatting and
other non-runtime mechanical changes do not trigger a Staging deployment.

When authorized, Staging work must use:

- an exact full SHA;
- the Staging runbook and isolated environment configuration;
- preflight and migration safety gates;
- focused automated acceptance;
- concise evidence tied to the exact SHA and environment.

Standing authorization never opens a new Package or Phase and never authorizes
real Store activation, new real credentials or Master-data mutation, or real
Printer/Pad binding. Do not copy Production credentials/data/endpoints into
Staging. Staging authorization never implies Production authorization.

## 15. Production

Production is always a TRUE OWNER GATE.

A Production request must prepare one immutable release batch containing:

- exact SHA and artifacts;
- migration scope;
- backup plan/evidence;
- rollback plan;
- Staging acceptance evidence;
- deployment/restart actions;
- post-deploy smoke and observation plan.

The Owner may approve that exact batch once. Any material SHA, artifact,
migration, environment-assumption or procedure change invalidates approval.

## 16. Documentation and Evidence

- Update only authority whose facts changed.
- Update `SYSTEM_DOCUMENTATION.md` when current technical behavior or its
  governance routing changes; do not append status history mechanically.
- Current execution state belongs only in `CURRENT_STATE.yml`.
- Phase definitions belong only in `ROADMAP.md`.
- Active work belongs only in `BACKLOG.md`.
- Evidence records proof, not future authorization.
- Archive historical context outside default authority paths.
- Prefer concise structured evidence with exact identifiers over raw logs.
- Preserve full logs only for meaningful acceptance, incident, security,
  migration or forensic value.

## 17. Token and Tool Efficiency

- Do not reread unchanged large authority without a concrete reason.
- Prefer repository search, headings, targeted sections, Git history and blob
  comparison.
- After merge, refresh changed authority plus the three default files only.
- Do not repeatedly summarize unchanged Ground Truth.
- Do not emit progress-only narration without a new finding, decision, failure,
  blocker or completed milestone.
- Historical evidence is not default reading.
- Do not restate whole Planbooks in prompts or reports.

For long-running asynchronous work:

- Empty `write_stdin` polls MUST use `yield_time_ms >= 180000`; prefer `300000`
  when intermediate output is unnecessary.
- `functions.wait` MUST use `yield_time_ms >= 180000`.
- `functions.exec` MUST set its outer `@exec yield_time_ms` at least 30000 ms
  longer than the longest nested tool wait.
- Do not apply the long wait to non-empty `write_stdin` calls that send input.
- These tools return early when the process or cell completes.
- Do not wake the model merely to report that work is still running.

## 18. Completion Report

Report in Chinese:

- what changed;
- how it was validated;
- exact branch/PR/merge/runtime SHAs where applicable;
- assumptions and unresolved risks;
- whether Staging or Production was mutated;
- the resulting Gate and Stop Marker.

Never continue into a new Phase or Production merely because the current task
completed successfully.

## 19. Execution Time Breakdown

After every substantive engineering task, the final Owner-facing report must
include an `Execution Time Breakdown` with these stages:

- Investigation / Planning
- Implementation
- Tests
- Agent 6 / PR / Merge
- Staging Deploy
- Staging Acceptance
- Evidence / Governance
- Total

Prefer actual wall-clock time. If a stage was not executed, report `N/A` or
`Not required`; if time can only be estimated, mark it `Estimated`. `Total`
must cover the actual interval from the start of the task through the final
Stop. Add a brief reason for materially long stages, such as full regression,
Maven/npm build, Agent 6 re-review, Staging image build, Flyway,
readiness wait, acceptance replay, repair or redeploy. Include retries in the
stage total and note their count and reason.

Do not add frequent polling or progress wake-ups solely for timing, and do not
weaken the existing long-running asynchronous-work or token-efficiency rules.
Documentation-only or trivial mechanical tasks may use a simplified report,
but must still include Implementation, Validation and Total. This reporting
rule changes only the final report; it does not change testing, Agent 6, PR,
Staging or Owner Gate requirements.

Recommended format:

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | ... | ... |
| Implementation | ... | ... |
| Tests | ... | ... |
| Agent 6 / PR / Merge | ... | ... |
| Staging Deploy | ... | ... |
| Staging Acceptance | ... | ... |
| Evidence / Governance | ... | ... |
| Total | ... | ... |
