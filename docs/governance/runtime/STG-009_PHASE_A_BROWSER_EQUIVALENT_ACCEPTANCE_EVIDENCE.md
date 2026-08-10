# STG-009 Phase-A browser-equivalent acceptance evidence

> Classification: `MACHINE_VERIFIED_STAGING_RUNTIME_EVIDENCE`
>
> Result: `BROWSER_EQUIVALENT_PASS_OWNER_MANUAL_UI_PENDING`
>
> Observation date: 2026-08-10, America/Toronto

## Exact runtime and safety boundary

- Git Ground Truth before the runtime action was exact
  `1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`, containing PR #99's reviewed
  browser-origin and credential-rotation repair plus PR #100's governance
  closure.
- Detached release, private environment binding, build source and deployed
  Staging identity all matched that exact SHA.
- Formal preflight, resource/isolation gates, V10-to-V10 Staging-only deploy,
  three-endpoint readiness and the formal OPS runtime collector passed.
- Flyway remained V10 with repository migrations V1--V10, 10 successful
  versioned rows, no failed row and no pending migration. No historical
  migration was replayed.
- Printing remained disabled. Loopback exposure, project/network/mount/state
  isolation and the clean release identity remained intact.
- Production received no deploy, restart, Flyway, database or business-data
  action. Its bounded container/image/start/restart/authoritative-health
  fingerprint remained unchanged.

## Credential rotation and API acceptance

The synthetic Owner credential was rotated through the reviewed OPS-001
private secret-FD action. The permanent runtime-only artifact remains outside
Git and exportable evidence, is password-only, has mode `0600`, and satisfies
the reviewed 20-character contract. No password, token, cookie or
Authorization value was retained in command arguments, logs, evidence or
governance.

The rotation action proved old-login/context, credential replacement,
logout, new-login/context and final logout. A fresh, separately approval-bound
`owner-login-acceptance` then returned HTTP 200 for login, authenticated
principal, workspaces, Owner overview and logout.

## Real-browser equivalent acceptance

A real Chrome session used the loopback tunnel and the rotated private
credential without exposing the value. The observed sequence passed:

1. `POST /api/v1/auth/login` succeeded and navigated to the source Store Admin
   Dashboard.
2. The authenticated principal was `STG005_OWNER_20260808_R01` with the
   highest Organization Owner authority.
3. Owner Home showed the correct synthetic Organization and exactly one
   accessible Store, `STG005_SRC_20260809_R01`; no unexpected Store appeared.
4. The Owner workspace and Store Admin Dashboard authenticated GET flows
   loaded successfully.
5. Refresh preserved the authenticated Owner session and the same
   Organization/Store scope.
6. Logout returned to `/login`; direct navigation to the Owner route after
   logout redirected to `/login`.

Bounded nginx evidence for this browser window recorded HTTP 200 for login,
principal, workspaces, Owner overview, Store context, Admin overview/dashboard
and logout. It recorded no 401 or 403. The bounded backend error window
contained no matching error. Temporary local secret material was overwritten
and removed, and the browser clipboard was cleared.

## Synthetic and runtime continuity

- STG-005A remains `VALIDATED/CREATED/REPLAYED`: one synthetic Organization,
  Owner, source Store, credential, required memberships and completed request.
- STG-005B remains `VALIDATED/CREATED/REPLAYED`: categories/stations/items/
  options are `4/3/13/38`, revision remains `2`, duplicate counts are zero and
  no Store crossover exists.
- No one-shot or blocked marker is active and the reviewed lock is empty.
- Staging frontend, authoritative backend health and WebSocket info are HTTP
  200 after the browser flow. Printing remains disabled.

## Closure boundary

This evidence closes the automated API and browser-equivalent requirements;
it does not fabricate Owner manual UI evidence. The pre-repair Owner manual
attempt remains a historical failure. A fresh Owner post-repair manual login,
Organization/Store visibility and dashboard confirmation is the only remaining
Phase-A acceptance item.

Therefore:

- `STG-008 = PASS` remains valid;
- `STG-009_PHASE_A_OWNER_LOGIN = PENDING_OWNER_MANUAL_UI_ACCEPTANCE`;
- the unique stop state is
  `STG-009_PHASE_A_BROWSER_EQUIVALENT_PASS_WAITING_FOR_OWNER_MANUAL_UI_ACCEPTANCE`;
- Chinatown onboarding, validate, execute, clone, replay and every Production
  mutation remain prohibited.
