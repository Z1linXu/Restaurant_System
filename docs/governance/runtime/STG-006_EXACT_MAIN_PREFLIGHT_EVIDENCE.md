# STG-006 Exact-Main Passive Preflight Evidence

> Evidence state: `MACHINE_VERIFIED_READ_ONLY`
>
> Candidate: `origin/main@33c6e3c52aa40793f6bb861101c16ccdd1b85b5b`
>
> Observed: 2026-08-08 18:15 UTC / 2026-08-08 14:15 America/Toronto
>
> Runtime mutation performed: `NO`

## 1. Authorization and method

The Owner authorized one fresh passive Staging preflight and the minimum
read-only Production-continuity observation. One Runtime Coordinator used a
single bounded BatchMode SSH timeline containing filtered Git, filesystem,
Docker metadata, resource, loopback HTTP, and read-only
`flyway_schema_history` queries. It did not create or change a release,
environment file, image, container, network, volume, database row, credential,
printer, Pad, or business record.

The reviewed `staging-server-preflight.sh` was inspected but not invoked. It is
a pre-start gate that requires an already-created candidate release/private
environment and deliberately rejects an occupied port; this passive loop had
neither authority to create that release nor authority to stop the healthy
retained runtime on port 18080. Treating its expected preconditions as met
would have bypassed the guard. STG-006 therefore records a separate filtered
current-runtime observation and does not claim a formal preflight evidence
digest. A fresh formal script PASS remains mandatory after approved OPS-001
release/env preparation and before any STG-007 start.

Candidate selection is repository identity only. It is not deployment,
migration, Staging acceptance, Production approval, or activation.

## 2. Candidate and repository boundary

| Check | Observation | Result |
|---|---|---|
| Exact candidate | `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | `PASS` |
| Git identity | PR #72 merge commit and latest `origin/main` | `PASS` |
| Main ancestry | PRs #61-#72, including independent #66, are ancestors | `PASS` |
| Migration chain | exactly V1 through V10; no V11 or later file | `PASS` |
| Candidate Staging release | absent, as expected before a separately approved release-preparation batch | `NOT_APPLICABLE_TO_PASSIVE_PREFLIGHT` |

The V9 and V10 SQL files remain append-only create-table/index migrations.
Static inspection found no new migration ordering or obvious compatibility
blocker. This is not runtime migration proof.

## 3. Fresh Staging observation

| Item | Fresh observation | Result |
|---|---|---|
| Checkout / runtime SHA | clean detached release `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` | `PASS` |
| Compose project | `restaurant-pos-staging` | `PASS` |
| Services | `db`, `backend`, `nginx` running; restart count `0`; db healthy | `PASS` |
| Images | backend/frontend tags and immutable image IDs remain bound to `4397f995...` | `PASS` |
| Flyway | successful SQL rows V1-V8; maximum version `8` | `PASS` for observed current boundary |
| Bind | only `127.0.0.1:18080` | `PASS` |
| HTTP | `/`, `/api/v1/system/health`, and `/ws/info` returned HTTP 200 | `PASS` |
| Printing | `STAGING_PRINT_MODE=DISABLED`; feature flag `false` | `PASS` |
| State | `/srv/restaurant-pos/staging/state/postgres` -> PostgreSQL data mount | `PASS` |
| Network | `restaurant-pos-staging_restaurant-pos` only | `PASS` |

The Staging root and Production checkout canonicalize to separate trees.
Staging root, config, environment file, state root, and PostgreSQL leaf are not
symlinks. Config is mode `0700`, the environment file is `0600`, and the
PostgreSQL private leaf is UID `70`, mode `0700`. Staging mounts use only the
Staging state/release trees; Production mounts use only the Production tree.
No volume, network, or configuration crossover was observed.

## 4. Fresh resource gate

| Resource | Observation | Gate |
|---|---:|---|
| Online CPUs | 2 | `PASS` |
| Available memory | 1,838,400 KiB at the bounded sample | `PASS`; above the 1 GiB stop threshold |
| Filesystem available | 43,511,008 KiB | `PASS` |
| Filesystem used | 27% | `PASS` |
| Load average | 0.60 / 0.32 / 0.17 | `PASS` point-in-time |

Observed Staging container memory was approximately 4.4 MiB nginx, 337.8 MiB
backend, and 41.5 MiB database. These point-in-time values support this passive
decision only; every future build/start action must recollect resources and
enforce the same 1 GiB stop gate with serial builds.

## 5. Production continuity

The before/after Production snapshots matched exactly:

- runtime Git SHA `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` on `main`;
- Compose project `cloud` with the same `db`, `backend`, and `nginx` container
  IDs, image IDs, creation/start times, and restart count `0`;
- database health remained healthy and `/api/v1/system/health` returned 200;
- Production network and bind mounts remained under `cloud_restaurant-pos` and
  `/home/ubuntu/Restaurant_System`.

This is a minimum continuity observation only. It did not query Production
Flyway, Store 1, customer/order/payment data, backups, environment values, or
business records, and it is not a Production gap audit or deployment approval.

## 6. Migration boundary

```text
Current Staging: V8
Repository candidate: V10
Pending if a later deployment is approved: V9, V10
```

No Flyway command ran. The retained V8 application images remain unproven
against a future V10 database and are not an approved post-migration rollback
target.

## 7. Decisions

`STG-006 = PASS`

The fresh passive evidence proves the current isolated runtime, resources,
migration gap, and Production continuity needed to finish STG-006. It does not
mean that the candidate exists on the server or may be deployed.

`OPS-001 = REQUIRED`

Repository audit confirms three unresolved acceptance prerequisites:

1. no reviewed secret-safe detached-release/private-environment rotation
   helper or fixed release-source contract;
2. no approval-bound same-image restart/start and Flyway evidence collector;
3. no secret-safe Owner login/onboarding/validate/execute client that keeps
   passwords, bearer/refresh tokens, staff initial passwords, and raw
   idempotency keys out of argv, stdout, shell history, and evidence.

The current launchers safely cover STG-005A/STG-005B and passive readiness, but
do not close these gaps. A repair cannot be safely improvised in this loop:
the release source/control-root contract, atomic private-environment rotation
and recovery semantics, restart approval artifact, and exact secret-input/API
session contract require one bounded OPS-001 technical design and Owner review.
No product API change is requested.

## 8. Blockers and stop

| Class | Blocker | Effect |
|---|---|---|
| `CODE_BLOCKER` | OPS-001 helpers above are absent | Blocks STG-007 and full acceptance; does not invalidate STG-006 evidence |
| `CONFIG_BLOCKER` | Candidate release, private env digest, candidate images, and action approvals do not exist | Must be created only after approved tooling and runtime batch |
| `EVIDENCE_BLOCKER` | V9/V10, second start, bootstrap, login, onboarding, clone, replay, and persistence are unexecuted | Staging acceptance remains pending |
| `OWNER_DECISION` | OPS-001 design/repair and every future mutation batch need separate approval | No automatic transition to STG-007 |

No `RESOURCE_BLOCKER` or `ISOLATION_BLOCKER` was observed.

Unique stop state:

`STG-006_PASS_OPS-001_REQUIRED_WAITING_FOR_OWNER_REVIEW`
