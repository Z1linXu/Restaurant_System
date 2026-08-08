# AL-003 Staging Release Attempt Evidence

> Status: `AL-003_STAGING_RELEASE_NO_GO_WAITING_FOR_OWNER_REPAIR_APPROVAL`
>
> Attempted: 2026-08-08, server UTC+08:00 / America/Toronto UTC-04:00
>
> Owner-approved release SHA: `8f909525781804f61d1da388882f530da358c3c4`
>
> Deployment result: `NO-GO`; no image build, application start, or migration

## Scope and authorization

The Owner approved an exact-SHA deployment of merged PR-F to the isolated
Staging project. The approved scope allowed an independent detached release,
private Staging identity update, fresh formal preflight, serial backend/nginx
build, Staging-only start, V9/V10 application startup, and post-start
verification. It did not authorize Production changes, synthetic bootstrap,
menu validate/execute, a real clone, or Production migration.

PR #57 was confirmed in `origin/main` at
`f73fce9aa1c9abff1796715f3258dc4f6bb22207`. The runtime release remained the
separately approved PR-F merge SHA
`8f909525781804f61d1da388882f530da358c3c4`.

## Pre-write gate

The fresh execution-time baseline passed before any Staging write:

| Gate | Observation | Result |
|---|---|---|
| Existing Staging SHA | `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` | `PASS` |
| Existing Staging schema | Flyway V1-V8 successful; V9/V10 absent | `PASS` |
| Staging printing | `DISABLED`; feature flag `false` | `PASS` |
| Staging bind | only `127.0.0.1:18080` | `PASS` |
| Staging health | `/`, backend health, and `/ws/info` returned 200 | `PASS` |
| Staging project | `restaurant-pos-staging`, three expected services | `PASS` |
| Production SHA | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` | `PASS` |
| Production project | `cloud`, three expected services, zero restarts | `PASS` |
| Available memory | 1,822,392 KiB | `PASS` against 1 GiB gate |
| Disk | 44,680,474,624 bytes free; 27% used | `PASS` |
| CPU | 2 online CPUs | `PASS` |

## Detached release

The independent Staging bare repository fetched `origin/main`; the Production
checkout was not used or changed. A clean detached release was created at:

```text
/srv/restaurant-pos/staging/releases/8f909525781804f61d1da388882f530da358c3c4
```

Its HEAD exactly matched the approved SHA, its directory owner was `ubuntu`,
its mode was `0700`, and its Git worktree was clean. No candidate image was
built.

## Formal preflight failure

Only the four non-secret Staging identity fields were atomically pointed to the
candidate: commit SHA, backend image tag, frontend image tag, and frontend build
version. The old Staging nginx, backend, and db were then stopped in that order
to free port 18080 for the formal preflight.

The approved release's formal preflight returned `NO-GO` before Docker build or
application startup:

```text
cd: /srv/restaurant-pos/staging/state/postgres: Permission denied
CHECK|PATHS|NO_GO|required Staging path is missing or traverses a symlink
```

The exact failed evidence is retained privately on the server at:

```text
/srv/restaurant-pos/staging/evidence/al-003-release-preflight-8f909525781804f61d1da388882f530da358c3c4-FAILED.txt
```

Evidence metadata:

- owner `ubuntu`, mode `0600`, size 273 bytes;
- SHA-256
  `c0c926e77bafeacb2ad972c2580417791814b323e4a3ab9fc05462c475f384b5`.

## Root cause

`staging-server-preflight.sh` canonicalizes every required directory by
executing `cd` as the `ubuntu` deployment user. After PostgreSQL first ran, its
container correctly owned the persistent data leaf as UID 70 with mode `0700`.
The deployment user can verify the leaf entry and its non-symlink parent but
cannot enter the PostgreSQL data directory. The path check therefore rejects a
healthy, already-initialized Staging database during an upgrade.

This is a deployment-preflight upgrade-path defect. It is not evidence of
database corruption, a bad migration, insufficient resources, a Production
problem, or a menu-clone failure.

The guard must not be bypassed by weakening PostgreSQL directory permissions,
running the whole preflight as root, editing the approved release, or forging
evidence. The smallest repair should validate the opaque PostgreSQL leaf by
checking its exact parent/name, existence, type, and non-symlink status without
requiring the deployment user to `cd` into it. The corresponding preflight test
must reproduce a non-traversable PostgreSQL leaf.

## Automatic pre-migration recovery

The failure occurred before build and before candidate application startup.
The recovery guard therefore:

1. restored the four Staging identity fields to `4397f995...`;
2. preserved `.env.staging` owner `ubuntu` and mode `0600`;
3. restarted only `restaurant-pos-staging` db, backend, and nginx;
4. retained the existing PostgreSQL state;
5. performed no database rollback or restore.

Post-recovery evidence:

| Check | Result |
|---|---|
| Staging images | old `4397f995...` backend/frontend images |
| Staging containers | same three container IDs, running; db healthy |
| Flyway | exactly V1-V8 successful; V9/V10 absent |
| Frontend | HTTP 200 |
| Backend health | HTTP 200 |
| SockJS info | HTTP 200 |
| Printing | `DISABLED` |
| Port | only `127.0.0.1:18080` |

## Production continuity

Production remained `main` at
`4667f3c35f85c9f8538f82789d9df1531d4fbc9e`. Containers `cloud-db-1`,
`cloud-backend-1`, and `cloud-nginx-1` retained their existing IDs, original
start times, running states, and zero restart counts. Production health returned
HTTP 200. No Production Git, Docker, environment, database, Flyway, or network
mutation was performed.

## Migration and rollback boundary

- V9 and V10 were not applied anywhere by this attempt.
- Production remains on its previously reported Flyway V7 state.
- No synthetic bootstrap, validate request, execute request, Store 1 read, or
  real clone occurred.
- The retained V8-era Staging images remain the only running Staging images.
- They are still not an approved rollback target after a future V10 migration;
  compatibility remains unverified.
- `Flyway clean`, repair, history edits, restore, `down -v`, permission
  weakening, and destructive schema rollback remain forbidden.

## Required next gate

This exact release cannot be deployed while its mandatory preflight cannot
produce `SUMMARY|PASS`. Apply the Dependency Repair Gate:

1. create the smallest preflight-only repair and regression test;
2. merge it through Owner review;
3. choose and approve the resulting new full main SHA;
4. create a new detached release;
5. regenerate the private environment digest and fresh preflight evidence;
6. rerun the deployment from the beginning.

Do not reuse the failed evidence, the previous environment digest, or the old
exact-SHA approval.

The stop state is
`AL-003_STAGING_RELEASE_NO_GO_WAITING_FOR_OWNER_REPAIR_APPROVAL`.
