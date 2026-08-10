# STG-008 synthetic runtime progress evidence

> 2026-08-10 clarification: STG-008 topology/source remains PASS, but the
> separately claimed Phase-A Owner-login PASS is superseded for browser
> acceptance by `STG-009_PHASE_A_BROWSER_LOGIN_403_FORBIDDEN`. API-only 200 did
> not exercise browser Origin/proxy behavior; see
> [the browser-login repair evidence](STG-009_PHASE_A_BROWSER_LOGIN_403_REPAIR_EVIDENCE.md).
>
> Later exact runtime evidence deployed `1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`,
> privately rotated the credential and passed both API and real-Chrome
> browser-equivalent acceptance. STG-008 remains `PASS`; only fresh Owner
> post-repair manual UI confirmation remains for Phase A. See
> [the browser-equivalent evidence](STG-009_PHASE_A_BROWSER_EQUIVALENT_ACCEPTANCE_EVIDENCE.md).

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
- The API-only `STG-009_PHASE_A_OWNER_LOGIN` client passed on this exact
  release, but manual Chrome acceptance later failed at the initial CORS 403
  before authentication. See [the superseded API evidence](STG-009_PHASE_A_OWNER_LOGIN_EVIDENCE.md)
  and [the browser repair evidence](STG-009_PHASE_A_BROWSER_LOGIN_403_REPAIR_EVIDENCE.md).

## Current boundary

`STG-008=PASS` covers the completed synthetic topology/source contract. Exact
`1a3f2e...` retained that topology and passed fresh deployment/readiness,
private credential rotation, API and real-Chrome browser-equivalent Phase-A
acceptance. STG-005A/B plan/execute/replay, expected counts/revision, no
duplicate/crossover, disabled Printing, intact isolation and unchanged
Production continuity remain valid. Fresh Owner post-repair manual UI evidence
is pending and Phase B remains prohibited. The unique stop state is
`STG-009_PHASE_A_BROWSER_EQUIVALENT_PASS_WAITING_FOR_OWNER_MANUAL_UI_ACCEPTANCE`.

No password, token, cookie, authorization header, or business data is retained
in this evidence.
