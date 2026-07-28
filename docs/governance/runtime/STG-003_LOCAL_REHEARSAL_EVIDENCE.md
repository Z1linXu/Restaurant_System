# STG-003 Local Isolated Rehearsal Evidence

Status: `BLOCKED_LOCAL_DOCKER_RUNTIME_UNAVAILABLE`

## Scope

STG-003 provides a local-only, reproducible rehearsal harness for the STG-002
standalone Compose package. The harness requires an explicit local-container
confirmation, a clean exact Git SHA, a non-production temporary root, isolated
data, SHA-tagged images, `127.0.0.1:18080`, and `DISABLED` printing.

It does not deploy to a server, use production data, use real credentials,
connect to real printers, start PAD_DIRECT, submit orders, restore data, or run
`Flyway clean`/`docker compose down -v`.

## Implemented Static Evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Exact clean SHA guard | `MACHINE_VERIFIED` | Runner requires full SHA equal to clean local `HEAD`. |
| Local root and evidence isolation guard | `MACHINE_VERIFIED` | Requires canonical `TMPDIR` to be an approved local temporary base, canonicalizes the nearest existing ancestor, rejects symlink traversal and forbidden targets, revalidates after creation/before Compose, and writes evidence only to the fixed `<root>/evidence/stg-003-local-rehearsal.md` path. |
| Local Docker guard | `MACHINE_VERIFIED` | `--run` and `--cleanup` require local Docker context `default` with a Unix/npipe endpoint. |
| Docker unavailable fail-closed | `MACHINE_VERIFIED` | Test verifies no local state is created before `BLOCKED_LOCAL_DOCKER_RUNTIME_UNAVAILABLE`. |
| Synthetic-only configuration | `MACHINE_VERIFIED` | Runner generates mode-0600 local DB/JWT values and rejects real printing configuration. |
| Print safety | `MACHINE_VERIFIED` | `STAGING_PRINT_MODE=DISABLED` and `STAGING_PRINTING_FEATURE_ENABLED=false` are asserted. |
| Fake-Docker lifecycle command-plan coverage | `MACHINE_VERIFIED` | Tests use an isolated PATH and fake Docker for `run`/`cleanup`, proving the default context/project and `build`, `up`, `stop`, and non-volume `down` command plan without resolving host Docker. |

## Supplemental Local PostgreSQL Evidence

The following evidence was collected by the coordinator using an isolated local
PostgreSQL 16.14 instance. It is not Docker/Compose evidence and must not be
used to claim that the STG-002 container package has completed a local
rehearsal.

| Check | Result | Evidence |
| --- | --- | --- |
| Candidate backend base | `MACHINE_VERIFIED` | Backend built from the current STG-003 base candidate (`e7015fe`). |
| Isolated PostgreSQL startup | `MACHINE_VERIFIED` | PostgreSQL 16.14 used a temporary local database named `restaurant_pos_staging` with synthetic credentials. |
| First cloud-profile startup | `MACHINE_VERIFIED` | Cloud profile started with runtime seed disabled and `APP_FEATURES_PRINTING=false`; Flyway applied V1-V8 and backend health returned HTTP 200. |
| Second cloud-profile startup | `MACHINE_VERIFIED` | Flyway validated eight migrations at current schema version 8 and reported no migration necessary. |
| Empty synthetic data policy | `MACHINE_VERIFIED` | Observed counts were `stores=0`, `users=0`, and `print_jobs=0`. |
| Local cleanup behavior | `MACHINE_VERIFIED` | Backend and temporary PostgreSQL were gracefully stopped; the temporary database directory was retained rather than deleted. |

No synthetic secret values, local network address, production data, real
accounts, printer endpoints, or PAD_DIRECT activity were used or recorded.

## Runtime Evidence Pending

| Runtime check | Status | Reason |
| --- | --- | --- |
| PostgreSQL 16 container first startup | `EVIDENCE_PENDING` | Non-container local PostgreSQL evidence exists, but Docker CLI/runtime is not available. |
| Container Flyway V1-V8 first migration | `EVIDENCE_PENDING` | Non-container local Flyway evidence exists; owner-approved local Docker execution is still required. |
| Container Flyway second-start validation | `EVIDENCE_PENDING` | Non-container local Flyway evidence exists; owner-approved local Docker execution is still required. |
| Container JPA/backend startup | `EVIDENCE_PENDING` | Non-container local backend health evidence exists; owner-approved local Docker execution is still required. |
| Nginx/API/WebSocket HTTP entry | `EVIDENCE_PENDING` | Requires owner-approved local Docker execution. |
| PostgreSQL persistence across restart | `EVIDENCE_PENDING` | Requires owner-approved local Docker execution. |
| Resource/runtime observations | `EVIDENCE_PENDING` | Requires owner-approved local Docker execution. |

## Blocker

The machine currently has no Docker CLI/runtime. The harness must not install
Docker or substitute a remote daemon. A local execution may proceed only after
the owner provides/approves a local Docker runtime. Until then, STG-003 is not
complete and STG-005 acceptance cannot start.
