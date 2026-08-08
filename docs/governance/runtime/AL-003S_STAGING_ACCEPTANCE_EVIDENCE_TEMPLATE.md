# AL-003S Staging Acceptance Evidence Template

> Template classification: `NOT_RUNTIME_EVIDENCE`
>
> Use only after exact-SHA and command-batch Owner approval.

## Authorization binding

| Field | Evidence |
|---|---|
| Owner approval reference | `EVIDENCE_PENDING` |
| Candidate full SHA | `EVIDENCE_PENDING` |
| Approved command batches | `EVIDENCE_PENDING` |
| Execution window | `EVIDENCE_PENDING` |
| Operator label | `EVIDENCE_PENDING` |
| Action request fingerprint | SHA-256 only; `EVIDENCE_PENDING` |
| Action approval artifact SHA-256 | `EVIDENCE_PENDING` |
| Action approval expiry | `EVIDENCE_PENDING` |

Do not record a password, token, raw idempotency key, Authorization header, or
secret-bearing command.

## Before-state

| Check | Expected | Result | Evidence class |
|---|---|---|---|
| Staging project | `restaurant-pos-staging` | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Staging SHA | reviewed prior SHA | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Staging Flyway | reviewed prior version | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Staging binding | `127.0.0.1:18080` only | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Printing | `DISABLED` | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Production project | unchanged | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Production containers | IDs/start/restart/health unchanged | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Resource thresholds | reviewed preflight PASS | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Fresh memory / CPU / disk / normalized load | reviewed thresholds PASS before and after action | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Fresh readiness age | at most 15 minutes | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Readiness evidence SHA-256 | Owner-reviewed file | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Staging container fingerprint | unchanged through action gate | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Production container fingerprint | unchanged through action gate | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |

## Release and migration

| Check | Expected | Result | Evidence class |
|---|---|---|---|
| Detached release HEAD | exact candidate SHA | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Environment SHA-256 | matches formal preflight | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Preflight evidence SHA-256 | Owner-reviewed file | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Backend/frontend image IDs | SHA-tagged exact candidate | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| One-shot backend image | immutable running backend `sha256:` ID | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| One-shot serialization | fixed Staging action lock acquired | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| One-shot timeout | at most 600 seconds | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Scoped container cleanup | deterministic AL-003S container absent after action | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| PostgreSQL | version 16 | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Flyway | V1-V10 success | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| JPA | schema validation PASS | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| Second startup | no new migration | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| `/` | HTTP 200 | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| `/api/v1/system/health` | HTTP 200 | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |
| `/ws/info` | HTTP 200 | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` |

## Synthetic topology

| Check | Sanitized result |
|---|---|
| Bootstrap plan | `EVIDENCE_PENDING` |
| Bootstrap create | status/result code and IDs only |
| Bootstrap exact replay | status/result code and same IDs only |
| Source Store ID | must be `1` |
| Owner login | HTTP/result only; no credential/token |
| Organization access | Organization/Store IDs only |
| Target onboarding | status/result code and IDs only |
| Target onboarding replay | same IDs, `replayed=true` |
| Target printing | `DISABLED` |

## Synthetic source menu

| Phase | Categories | Stations | Items | Options | Revision | Result code |
|---|---:|---:|---:|---:|---|---|
| Plan | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` | unchanged | `EVIDENCE_PENDING` |
| Create | 4 | 3 | 13 | 38 | exactly `+1` | `EVIDENCE_PENDING` |
| Replay | 4 | 3 | 13 | 38 | unchanged | `EVIDENCE_PENDING` |

Record the immutable manifest identity/fingerprint and bootstrap request ID,
but not row payloads.

## Chinatown clone

| Phase | Expected result | Actual |
|---|---|---|
| Validate | valid, empty diagnostics, 4/3/17/74, no write | `EVIDENCE_PENDING` |
| Execute | `COMPLETED`, 3/4/17/74 created, target revision `+1` | `EVIDENCE_PENDING` |
| Replay | same durable summary, `replayed=true`, no new rows/revision | `EVIDENCE_PENDING` |
| Source invariance | source revision and graph unchanged | `EVIDENCE_PENDING` |
| Excluded side effects | no printer/device/table/order/payment/KDS/inventory writes | `EVIDENCE_PENDING` |

The raw key, payload, internal ID maps, credentials, and tokens are forbidden
from this evidence.

## Restart persistence

| Check | Result |
|---|---|
| Same project/images restarted | `EVIDENCE_PENDING` |
| Flyway unchanged at V10 | `EVIDENCE_PENDING` |
| Health restored | `EVIDENCE_PENDING` |
| Owner login/access restored | `EVIDENCE_PENDING` |
| Bootstrap/source/onboarding/clone replay unchanged | `EVIDENCE_PENDING` |
| Production continuity | `EVIDENCE_PENDING` |
| Post-action Staging/Production fingerprints | unchanged | `EVIDENCE_PENDING` |

## NO-GO ledger

Record only sanitized codes and the phase. Do not paste stack traces that might
contain secrets or request bodies.

| Time (UTC) | Phase | Safe code | Action taken |
|---|---|---|---|
| `EVIDENCE_PENDING` | `EVIDENCE_PENDING` | `EVIDENCE_PENDING` | stopped without boundary expansion |

## Final classification

- `AL-003_STAGING_ACCEPTANCE_READY`: `EVIDENCE_PENDING`
- Remaining runtime gaps: `EVIDENCE_PENDING`
- Production implication: none; Staging acceptance never authorizes Production.
