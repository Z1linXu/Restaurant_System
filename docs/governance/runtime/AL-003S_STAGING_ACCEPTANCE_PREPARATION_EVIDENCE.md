# AL-003S Staging Acceptance Preparation Evidence

> Evidence class: `LOCAL_REPOSITORY_VERIFICATION`
>
> Package state: `IN_MAIN_WAITING_FOR_RUNTIME_APPROVAL`
>
> Runtime execution: `NOT_RUN_BY_POLICY`

## Scope

This record covers only the guarded STG-005A/STG-005B one-shot launcher,
shell regression, command/evidence/rollback preparation, and governance sync.
It does not prove a Staging deployment, migration, bootstrap, Owner login,
source-menu write, target onboarding, clone validation/execution, restart, or
Production continuity.

## Git binding

| Field | Value |
|---|---|
| Historical parent STG-005B checkpoint | `0aba8377a3b7acec047c6ffd025f774d8a4d5e87` |
| Branch | `codex/al-003s-staging-acceptance-preparation` |
| Package implementation commit | `47902430313117ac51a1f054a267ec7371267218` |
| Final evidence/PR-binding commit | PR #63 merge `732d77c89ff067982702426ff918d5e097e1d0fb` |
| PR | PR #63 (`IN_MAIN`) |
| Current dependency state | PRs #61, #62, and #63 are `IN_MAIN`; runtime approval remains separate |

## Local verification

| Check | Result |
|---|---|
| All Staging shell files `bash -n` | `PASS` |
| `test_staging_deploy_cli_state.sh` | `PASS` |
| `test_staging_guard.sh` | `PASS` |
| `test_staging_server_control.sh` | `PASS` |
| `test_staging_server_preflight.sh` | `PASS` |
| `test_staging_synthetic_acceptance.sh` | `PASS` |
| `test_staging_synthetic_acceptance_runtime_guards.sh` | `PASS` |
| `test_staging_acceptance_readiness.sh` | `PASS` |
| Backend full suite | `PASS`: 358 tests, 0 failures, 0 errors, 3 skipped |
| Backend compile | `PASS` |
| Local Markdown links in changed documents | `PASS` |
| `git diff --check` | `PASS` before commit |
| Secret/scope scan | `PASS`; no secret value, migration, business Java, Compose topology, frontend, or Android change |
| `test_staging_local_rehearsal.sh` | `PASS` on clean committed Head `47902430313117ac51a1f054a267ec7371267218` |

The pre-commit invocation correctly refused a dirty worktree. The required
post-commit rerun then passed on the exact clean implementation commit above.

## Safety properties exercised

- validation is the default and creates no one-shot container;
- every one-shot action requires `--execute-runtime`;
- write behavior requires an explicit `*-execute` action;
- bootstrap password is accepted only through non-interactive stdin;
- duplicate arguments and unsafe/non-`STG005_` identities are rejected;
- source-menu actions require reviewed synthetic source Store ID `1`;
- exact release, env, formal preflight digest, project, printing, image, port,
  and health checks fail closed;
- readiness expires after 15 minutes and binds current resource thresholds plus
  exact Staging/Production container fingerprints;
- fresh resource checks cover memory, CPU count, free disk, and normalized
  load before and after the bounded action;
- action approval binds the exact action, SHA, synthetic identities, preflight
  digest, readiness digest, expiry, and sanitized Owner reference;
- the one-shot backend uses the already-running immutable image ID rather than
  a mutable tag lookup at container creation time;
- a fixed owner-only Staging action lock prevents concurrent launchers, each
  one-shot is bounded to 600 seconds, Docker metadata/cleanup is bounded to 20
  seconds, and scoped cleanup uses a deterministic action-fingerprint container
  name;
- blocked state is attempted in the held lock record and a companion marker;
  either record is independently authoritative, and both are rechecked after
  acquiring the lock to close the marker/flock race;
- post-action checks repeat Staging/Production fingerprints, image, health,
  release, evidence, and resources;
- Docker uses context `default` with isolated mode-`0700` HOME/DOCKER_CONFIG;
- private Docker CLI state is removed on success and failure;
- one-shot commands use `--rm --no-deps -T --pull never` and never build, pull,
  restart, or expose a service;
- no password, token, raw key, or raw payload is accepted as launcher input or
  written into the evidence template.

## Independent review

The initial read-only audit found that STG-005A/STG-005B had no independently
bound one-shot launcher and that the acceptance plan still referenced
row-by-row application APIs. A second independent review identified missing
action/identity approval binding, mutable-tag execution, stale readiness, and
insufficient failure-path tests. Final review then identified concurrency,
timeout/interrupt cleanup, procedural-approval wording, post-action continuity,
and disk/load gaps. This package addresses those bounded findings with
serialized/bounded execution, fresh pre/post project and resource checks,
immutable image pinning, scoped cleanup, explicit non-cryptographic approval
semantics, and focused negative tests.

Final Agent 6 review: `PASS_AFTER_BOUNDED_REPAIRS`; no remaining P0/P1/P2 code
finding. The final documentation-precision finding about dual blocked records
was corrected. Real Docker timeout, process-level lock contention, signal
interruption, and cleanup-failure behavior remain runtime rehearsal evidence,
not locally machine-verified outcomes.

## Remaining gates

- architecture PR #61, STG-005B PR #62, and guarded preparation PR #63 are
  `IN_MAIN`; runtime approval remains separate;
- a fresh merged-main candidate SHA and new Owner command-batch approvals do not
  exist;
- no secret-safe release/env rotation helper is published;
- no reviewed same-image restart/Flyway evidence collector is published;
- no secret-safe Owner/API acceptance client is published;
- PostgreSQL 16 source-menu concurrency and all runtime outcomes remain pending;
- retained Staging/Production SHA and Flyway levels were not freshly inspected.

These are hard acceptance gates. Passing this local package must not be
reported as `AL-003_STAGING_ACCEPTANCE_READY`.
