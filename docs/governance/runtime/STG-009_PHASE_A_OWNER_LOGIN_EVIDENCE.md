# STG-009 Phase-A Owner Login Evidence

## Scope

This is sanitized Staging-only evidence for `STG-009_PHASE_A_OWNER_LOGIN_ACCEPTANCE`.
It proves the bounded synthetic Owner login contract on one exact release; it
does not prove Chinatown onboarding, menu cloning, Production deployment, or
`STAGING_ACCEPTED`.

## Exact runtime identity

| Field | Value |
|---|---|
| Git `origin/main` / detached release / build source / deployed Staging | `468b8705c8e360b9e34336c5560442179544069b` |
| Flyway | V10; repository chain V1--V10; failed migrations 0; pending migration NONE |
| Formal preflight evidence | `stg009-468b8705c8e360b9e34336c5560442179544069b-preflight.evidence`; SHA-256 `eabfa5f8ec2bb8b136a098f0c669186bc4ab04132d745546f2d9849ba6582e07` |
| Fresh readiness evidence | `stg009-468b8705c8e360b9e34336c5560442179544069b-readiness-pass.evidence`; SHA-256 `c0a5e1cc592f7b30b99d97d000271539550944e84c55f2cc580087d81c71e036` |
| Runtime boundary | Staging only, loopback, isolated project/network/state/mounts, Printing `DISABLED` |

## Synthetic topology and source integrity

The pre-login read-only verification retained exactly one synthetic
Organization, Store, Owner/User, credential, organization membership, store
membership, and bootstrap request. The clone-request count remained zero.
The source menu remained `4 categories / 3 stations / 13 items / 38 options`;
replay retained revision `2 -> 2`. No duplicate graph or cross-Store mutation
was observed. There was no active one-shot, blocked marker, or lock record.

## Owner-login acceptance

The secret-safe client used synthetic Owner identifier
`STG005_OWNER_20260808_R01` and the existing private runtime-only credential
through an inherited secret-FD. The password, access/refresh tokens, cookies,
and Authorization values were not written to Git, stdout, logs, evidence, or
this document.

| Check | Result |
|---|---|
| Login | PASS, HTTP 200 |
| Authenticated `/auth/me` | PASS, HTTP 200; expected Organization Owner principal |
| `/me/workspaces` | PASS, HTTP 200; exactly the synthetic source Store in the synthetic Organization |
| `/owner/overview` | PASS, HTTP 200; exactly the synthetic source Store |
| Logout/session cleanup | PASS, HTTP 200 |
| Unexpected Store access | None observed; exact-one-Store predicate passed |

The first invocation was fail-closed before any HTTP request because the
private credential artifact was a password-only file rather than the client's
required JSON envelope. A private, mode-0600 temporary envelope was then
constructed from that file and supplied through the same secret-FD contract;
the successful retry above is the only login evidence. No runtime data was
created or changed by the failed invocation.

## Production boundary

Production continuity was limited to container IDs/image IDs/start times,
restart counts, running/health state, and a lightweight health observation.
Production database, Store 1, menu, order, customer, payment, and business data
were not read or mutated. Production remained unchanged.

## Final state

`STG-008 = PASS` and `STG-009_PHASE_A_OWNER_LOGIN = PASS` for the bounded
synthetic gates above. This evidence stops at
`STG-009_PHASE_A_OWNER_LOGIN_VERIFIED_WAITING_FOR_PHASE_B_CLONE_APPROVAL`.
Chinatown onboarding, AL-003 validate/execute/clone/replay, and all Production
operations require the next Owner Runtime Gate.
