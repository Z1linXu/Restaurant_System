# STG-008 synthetic runtime progress evidence

> Classification: `MACHINE_VERIFIED_STAGING_EVIDENCE`
>
> Scope: sanitized Staging-only execution facts. These facts establish
> `STG-008=PASS` for the completed synthetic topology/source gates, but do not
> imply `STAGING_ACCEPTED`, and authorize neither
> Chinatown onboarding/clone nor any Production mutation.

## Verified runtime facts

- Exact deployed Staging SHA is `468b8705c8e360b9e34336c5560442179544069b`.
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
- `STG-009_PHASE_A_OWNER_LOGIN` passed on this exact release: login,
  authenticated principal/workspace/overview checks and logout all returned
  HTTP 200 for `STG005_OWNER_20260808_R01`; the exact synthetic source Store
  was the only Store exposed. See [sanitized Phase-A evidence](STG-009_PHASE_A_OWNER_LOGIN_EVIDENCE.md).

## Current boundary

`STG-008=PASS` covers the completed synthetic topology/source contract and the
separately bounded `STG-009_PHASE_A_OWNER_LOGIN=PASS` evidence:
fresh exact deployment/readiness, STG-005A and STG-005B plan/execute/replay,
expected counts/revision, no duplicate/crossover, disabled Printing, intact
isolation, and unchanged Production continuity. Phase B remains prohibited.
The unique stop state is
`STG-009_PHASE_A_OWNER_LOGIN_VERIFIED_WAITING_FOR_PHASE_B_CLONE_APPROVAL`.

No password, token, cookie, authorization header, or business data is retained
in this evidence.
