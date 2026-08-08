# AL-003 Staging Release Read-only Preflight Evidence

> Status: `AL-003_STAGING_RELEASE_PLAN_WAITING_FOR_OWNER_APPROVAL`
>
> Observed: 2026-08-08 10:27 +08:00 / 2026-08-07 America/Toronto
>
> Exact release candidate: `8f909525781804f61d1da388882f530da358c3c4`
>
> Runtime mutation performed: `NO`

## Decision

| Gate | Result | Meaning |
|---|---|---|
| Read-only technical preflight | `GO` | Current resources, loopback bind, Compose separation, health, and Production continuity support requesting an exact-SHA deployment approval. |
| Immediate deployment | `NO-GO` | The Owner has not approved execution for this SHA. Its detached release, private environment binding, fresh formal preflight evidence, and evidence digest do not yet exist. |
| Application rollback after V10 | `NO-GO` | The retained `4397f995...` images have no reviewed runtime evidence proving startup against a V10 database. They must not be presented as an executable rollback target after migration. |
| Database restore | `NO-GO` | No Staging backup/restore procedure or restore rehearsal was executed or evidenced. Flyway remains forward-only. |

The aggregate result is **GO to the Owner exact-SHA approval checkpoint, but
NO-GO for deployment until that approval and the fresh execution-time gates
are satisfied**.

## Git and release identity

| Item | Observed value | Classification |
|---|---|---|
| Latest merged `origin/main` | `8f909525781804f61d1da388882f530da358c3c4` | `MACHINE_VERIFIED` locally |
| Merge identity | PR #56 merge commit; Owner menu-clone PR-F is in `main` | `MACHINE_VERIFIED` from Git history |
| Current server Staging SHA | `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` | `MACHINE_VERIFIED` |
| Current server Production SHA | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` on `main` | `MACHINE_VERIFIED` |
| Candidate release directory | Absent | `MACHINE_VERIFIED`; expected before authorization |
| Production worktree | Five porcelain entries | `MACHINE_VERIFIED`; names were not read or recorded, and the checkout must not be used for Staging |

The release must be a clean detached checkout at
`/srv/restaurant-pos/staging/releases/8f909525781804f61d1da388882f530da358c3c4`.
It may be created only in a later Owner-approved execution batch.

## Current Staging runtime

| Item | Observed value | Result |
|---|---|---|
| Compose project | `restaurant-pos-staging` | `GO` |
| Services | `db`, `backend`, `nginx`, all running; db healthy; restart count 0 | `GO` |
| HTTP bind | only `127.0.0.1:18080` | `GO` |
| Printing | `STAGING_PRINT_MODE=DISABLED`; feature flag `false` | `GO` |
| Database mount | `/srv/restaurant-pos/staging/state/postgres` -> `/var/lib/postgresql/data` | `GO` |
| Network | `restaurant-pos-staging_restaurant-pos` | `GO` |
| Frontend `/` | HTTP 200 | `GO` |
| Backend health through Nginx | HTTP 200 | `GO` |
| SockJS `/ws/info` | HTTP 200 | `GO`; not a STOMP exchange |

The current image tags remain the exact old Staging SHA:

- `restaurant-pos-backend:staging-4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`
- `restaurant-pos-frontend:staging-4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`

## Flyway gap

| Environment | Current schema evidence | Gap to candidate | Result |
|---|---|---|---|
| Staging | V8 is successful; no V9 or V10 row | V9 and V10 pending | `GO` only for an approved migration-bearing Staging release |
| Production | no V8, V9, or V10 row; historical evidence reports V7 | V8 through V10 pending | `NOT_IN_SCOPE`; no Production action authorized |

The repository candidate contains the append-only migrations:

- `V8__add_owner_store_onboarding_requests.sql`
- `V9__add_staging_synthetic_bootstrap_requests.sql`
- `V10__add_owner_store_menu_clone_requests.sql`

No Flyway command or application startup against the candidate occurred in
this preflight. After Owner approval, first startup must apply only the pending
V9 and V10 migrations to Staging, and the second startup must report schema 10
with no migration necessary.

## Resources and Production continuity

| Evidence | Observation | Result |
|---|---|---|
| Host CPU | 2 CPUs | `GO` |
| Available memory | about 1.7 GiB, no Swap | `GO` against the 1 GiB stop threshold; build must remain serial |
| Disk | about 42 GiB free, 27% used | `GO` |
| Load average | 0.55 / 0.29 / 0.16 | `GO` point-in-time only |
| Staging sampled memory | nginx 3.3 MiB, backend 321.1 MiB, db 38.4 MiB | `GO` point-in-time only |
| Production sampled memory | nginx 8.3 MiB, backend 525.7 MiB, db 80.7 MiB | `GO` point-in-time only |
| Production services | `cloud` db/backend/nginx running, zero restarts | `GO` |
| Production continuity | IDs and start times match the retained STG-004 evidence | `GO` for this bounded observation |
| Production health | `/api/v1/system/health` HTTP 200 | `GO` |

The Staging build must remain backend then nginx, never parallel. Any available
memory below 1 GiB, Production state/restart change, or resource anomaly at the
fresh preflight is an immediate `NO-GO`.

## Backup, rollback, and evidence conditions

- The known Production backup directory contains one file. Its filename was
  hashed, not disclosed; metadata reports 830843 bytes and modification time
  2026-07-25 03:36:58 +08:00. This proves only file presence and is not a
  Staging backup, integrity check, or restore rehearsal.
- STG-002 intentionally supplies no Staging backup/restore helper. A future
  backup or restore action requires a separate project-guarded Owner-approved
  procedure. No backup content was read in this preflight.
- Existing formal preflight evidence belongs only to historical SHAs
  `35033645...` and `4397f995...`. It must not be reused for the candidate.
- A fresh formal preflight must bind the candidate release, private environment
  SHA-256, evidence SHA-256, Compose project, root, port, resources, and
  printing-disabled state.
- The retained old images are inventory evidence only. Runtime compatibility
  with Flyway V10 is unproven, so application rollback to them is `NO-GO` after
  V10 until separately verified. Schema deletion, Flyway clean/repair/history
  edits, restore, and `down -v` remain forbidden.

## Owner approval package

The next Owner decision may authorize only this exact candidate and must state:

1. exact SHA `8f909525781804f61d1da388882f530da358c3c4`;
2. a non-peak serial build window and the 1 GiB available-memory stop gate;
3. creation of an independent detached release and update of only the private
   Staging environment identity/image tags;
4. fresh formal preflight capture and digest review before any start;
5. application of V9/V10 only through candidate backend startup;
6. post-start Flyway/JPA/health/port/resource/Production-continuity checks;
7. no synthetic bootstrap, validate, execute, or real clone in the release
   deployment batch;
8. no application rollback to `4397f995...` unless a separate compatibility
   gate is approved and passes.

## Commands and safety boundary

The preflight used local Git reads, one BatchMode SSH session containing
filtered read-only Git/Docker/filesystem queries, two read-only
`flyway_schema_history` SELECTs, metadata-only backup inspection, and loopback
health GETs. It did not deploy, build, start, stop, restart, migrate, bootstrap,
validate, clone, fetch a print payload, modify a file, or write a database.

The next state is
`AL-003_STAGING_RELEASE_PLAN_WAITING_FOR_OWNER_APPROVAL`.
