# Repository Authority and Workflow

This file defines authority routing and permanent governance policy. It does
not record an execution timeline. The single current state is
[`CURRENT_STATE.yml`](CURRENT_STATE.yml).

## 1. Authority precedence

When sources disagree, use this order:

1. verified evidence from the exact target environment;
2. fresh `origin/main` executable code, migrations, configuration and tests;
3. [`CURRENT_STATE.yml`](CURRENT_STATE.yml) for current governance state;
4. current technical contracts and environment runbooks;
5. [`ROADMAP.md`](ROADMAP.md) and [`BACKLOG.md`](BACKLOG.md);
6. historical evidence and archived plans;
7. chat history, old prompts and old handoffs.

Boundaries:

- Runtime evidence proves only the environment, SHA, artifacts and checks it
  actually verified.
- Repository code proves capability, not deployment.
- `CURRENT_STATE.yml` cannot override executable behavior or verified runtime
  evidence.
- A merged capability does not authorize deployment or data mutation.
- Historical evidence cannot authorize current or future mutation.
- Chat history is never repository authority.

If two higher-priority sources genuinely conflict, report the conflict and
fail closed at the narrower safety boundary.

## 2. Document ownership

| Document | Sole responsibility |
| --- | --- |
| [`AGENTS.md`](../../AGENTS.md) | Permanent Agent operating and safety rules. |
| `AUTHORITY.md` | Authority precedence, routing, lifecycle and Agile Loop policy. |
| [`CURRENT_STATE.yml`](CURRENT_STATE.yml) | Exactly one current Phase, Package, Gate, next action and authorization boundary. |
| [`ROADMAP.md`](ROADMAP.md) | Phase definitions, boundaries and high-level acceptance structure. |
| [`BACKLOG.md`](BACKLOG.md) | Active feature, bug, debt, governance and future work only. |
| [`SYSTEM_DOCUMENTATION.md`](../../SYSTEM_DOCUMENTATION.md) | Current technical system reference; never Phase authority. |
| [`doc/API.md`](../../doc/API.md) | Current API contract. |
| Runbooks | Environment-specific operational procedure; never mutation authority by themselves. |
| Contracts | Current task-specific technical invariants; never Phase or deployment authority. |
| Evidence | Proof of a past/current observation; never future authorization. |
| Archive | Historical context only. |

No Handoff, Planbook, backlog entry or evidence file may claim a second current
Phase, Package, Gate or Stop Marker.

## 3. Default startup and routing

Default required reading is exactly:

1. [`AGENTS.md`](../../AGENTS.md);
2. `AUTHORITY.md`;
3. [`CURRENT_STATE.yml`](CURRENT_STATE.yml).

Then route by task:

| Task | Additional authority |
| --- | --- |
| Phase/Package planning | [`ROADMAP.md`](ROADMAP.md), then relevant active entry in [`BACKLOG.md`](BACKLOG.md). |
| Product/runtime implementation | [`SYSTEM_DOCUMENTATION.md`](../../SYSTEM_DOCUMENTATION.md), [`doc/API.md`](../../doc/API.md), relevant code/tests and one task-specific contract. |
| Database/Flyway | Current migrations, mapped entities/tests, deployment preflight and relevant contract. |
| Staging operation | [`deployment/cloud/README_STAGING.md`](../../deployment/cloud/README_STAGING.md), exact action helper and current evidence routed from `CURRENT_STATE.yml`. |
| Production preparation | [`README_GIT_DEPLOY_WORKFLOW.md`](../../README_GIT_DEPLOY_WORKFLOW.md), [`deployment/cloud/README_ROLLBACK.md`](../../deployment/cloud/README_ROLLBACK.md), backup tooling and immutable release evidence. |
| Current pilot behavior | [`docs/CURRENT_PILOT_SCOPE.md`](../CURRENT_PILOT_SCOPE.md) plus executable code/tests. |
| Architecture-sensitive change | Relevant current code/contracts first; use the [Phase A Staging architecture snapshot](../archive/architecture/phase-a-staging-2026-08-14/README.md) only as historical comparison context. |
| Historical/forensic question | [`docs/archive/README.md`](../archive/README.md), then only the specific evidence needed. |

Do not read the entire archive to recover current state.

## 4. Ground Truth

Before substantial work:

```text
git fetch origin --prune
-> exact origin/main SHA
-> branch/worktree/dirty-state audit
-> CURRENT_STATE Phase/Package/Gate
-> task-specific contract/runbook
-> authorization check
```

The exact current repository SHA is always the result of
`git rev-parse origin/main` after a fresh fetch. The SHA stored in
`CURRENT_STATE.yml` is the last verified baseline used to write that state; a
governance commit cannot contain its own Git object ID. Any mismatch requires
checking whether state-bearing files changed, not blindly treating the baseline
as current HEAD.

Unknown user work is protected. Use an isolated worktree from fresh
`origin/main` when the current workspace is dirty, stale or purpose-bound.

## 5. Bounded Agile Loop

Within an explicitly Owner-authorized Phase and Package:

```text
Ground Truth
-> choose one bounded backlog item
-> implement
-> focused and regression tests
-> Agent 6 when required
-> PR
-> merge when authorized
-> fresh fetch and changed-authority refresh
-> exact-SHA Staging when authorized
-> acceptance
-> concise evidence and CURRENT_STATE sync
-> continue only inside the same authorized Package
```

Ordinary implementation, test, CI, documentation-link and Staging technical
failures may be repaired autonomously when the repair remains inside the
authorized Package and safety boundary. Use:

```text
root cause -> minimal repair -> validation -> re-review if material
```

Stop instead of repairing when the required action expands scope, weakens a
safety boundary, changes a material product rule, needs unapproved real data or
credentials, or crosses a TRUE OWNER GATE.

## 6. TRUE OWNER GATES

Explicit Owner authorization is required for:

- opening the next Phase;
- expanding the Package;
- material product/business-rule decisions outside current authority;
- security-boundary weakening;
- unapproved real credential, Master-data or operational-data mutation;
- real Store activation;
- real Printer/Pad binding outside an already approved exact scope;
- destructive database operations;
- materially different safety alternatives;
- every Production release.

Technical PASS closes only the documented technical gate. It never opens the
next Phase.

## 7. Review, PR and merge

- Material work requires a dedicated branch/worktree and PR.
- Agent 6 is required for material code, Flyway/database, auth/security,
  deployment, printing, offline/idempotency and architecture-sensitive work.
- Pure documentation or trivial mechanical work may use a risk-based exception
  unless the Owner requires review.
- Tests/checks and required review must pass before merge.
- Auto-merge is allowed only when the Owner has authorized it for the exact
  Package and no TRUE OWNER GATE remains.
- After merge, fetch, verify main ancestry, then refresh the three default files
  and only the additional authority changed by the merge.

## 8. Staging policy

Staging standing authorization exists only inside an Owner-authorized Phase and
Package. When it exists, Codex may perform reviewed exact-SHA deployment,
required Staging restart/Flyway, automated acceptance and evidence collection
without another approval for every contained action.

Within that authorized Phase/Package, merged runtime-sensitive or
Owner-testable application, UI, backend, printing, API and runtime-contract
changes proceed by default through fresh exact-SHA Staging deployment and
applicable automated acceptance. Ordinary Staging technical failures use the
bounded repair loop without repeated Owner approval when the repair does not
expand scope or weaken a safety boundary.

Documentation-only, governance-only, archive/cleanup, comment/formatting and
other non-runtime mechanical changes do not trigger Staging deployment.

Required boundaries:

- isolated Staging configuration/data/credentials;
- exact SHA and reviewed artifacts;
- preflight and migration checks;
- no real printer/device endpoint unless explicitly authorized;
- evidence records environment, SHA, Flyway and acceptance result.

Standing authorization does not open another Package or Phase and does not
authorize real Store activation, new real credentials or Master-data mutation,
or real Printer/Pad binding. Staging authority never implies Production
authority. `CURRENT_STATE.yml` may explicitly pause otherwise standing
authority for a governance-only run.

## 9. Production policy

Production is always a TRUE OWNER GATE. Prepare one immutable release batch:

- exact SHA/artifacts;
- migration scope;
- backup plan/evidence;
- rollback plan;
- Staging acceptance evidence;
- deployment/restart actions;
- smoke and observation plan.

Owner approval applies only to that exact batch. A material change to SHA,
artifact, migration, environment assumption or procedure invalidates it.
Production defaults to `mutation_authorized: false`.

## 10. Document lifecycle

- Current facts live in the one owning file from section 2.
- Do not prepend status history to living authority.
- Keep active backlog concise; move completed narrative to evidence/archive.
- Preserve evidence with real decision, acceptance, deployment, migration,
  incident, security, architecture or forensic value.
- Drafts must be physically separated and marked `DRAFT / SUPPORTING / NOT
  AUTHORIZED`.
- Delete only verified duplicates, obsolete prompts or redundant wrappers with
  no independent value and no required link/build dependency.
- Historical files must remain outside default reading and cannot be cited as
  mutation authorization.

## 11. Governance maintenance

When current facts change:

1. update only the owning authority;
2. keep `CURRENT_STATE.yml` short and non-historical;
3. run `python3 scripts/validate-governance.py`;
4. check affected links and `git diff --check`;
5. record concise evidence when an actual environment or acceptance was
   verified;
6. never manufacture runtime certainty—use `UNKNOWN` when proof is absent.
