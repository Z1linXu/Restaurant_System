# STG-008 synthetic runtime progress evidence

> Classification: `MACHINE_VERIFIED_STAGING_EVIDENCE`
>
> Scope: sanitized Staging-only execution facts. These facts establish
> `STG-008=PASS` for the completed synthetic topology/source gates, but do not
> imply `STAGING_ACCEPTED`, and authorize neither
> Chinatown onboarding/clone nor any Production mutation.

## Verified runtime facts

- Exact deployed Staging SHA is `712531b941db92f4325a86126883706314f4cba5`.
- Flyway history is V1--V10, with all ten versioned migrations successful and
  no failed or pending migration.
- `STG-005A`: `PLAN=VALIDATED`, `EXECUTE=CREATED`, `REPLAY=REPLAYED`.
  Exactly one synthetic Organization, Owner, Synthetic St-Denis source Store,
  credential, required memberships, and bootstrap request exist.
- `STG-005B`: `PLAN=VALIDATED`, `EXECUTE=CREATED`, `REPLAY=REPLAYED`.
  The source graph is exactly four categories, three stations, thirteen items,
  and thirty-eight options; replay retained revision `2 -> 2` with no duplicate
  graph or Store crossover.
- No synthetic one-shot is active, the blocked marker is absent, and the fixed
  acceptance lock is owner-only and empty.
- Printing remains disabled; the Staging HTTP root, backend health, and
  `/ws/info` checks are healthy. Production continuity observation remains
  limited to container/image/start/restart/health identity and unchanged.

## Current boundary

`STG-008=PASS` is limited to the completed synthetic topology/source contract:
fresh exact deployment/readiness, STG-005A and STG-005B plan/execute/replay,
expected counts/revision, no duplicate/crossover, disabled Printing, intact
isolation, and unchanged Production continuity. `STG-009_PHASE_A_OWNER_LOGIN`
remains the next separately evidenced Phase-A acceptance; Phase B remains
prohibited.

The next action is a fresh exact-SHA rebind of current `origin/main`, formal
preflight, Staging-only V10-to-V10 redeploy and readiness. Only then may the
bounded Phase A `owner-login-acceptance` action use the private credential FD.
No password, token, cookie, authorization header, or business data is retained
in this evidence.
