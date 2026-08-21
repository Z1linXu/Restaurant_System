# STG-004 Same-Host Server Staging Evidence

Status: `STG-004_SERVER_STAGING_RUNNING_WAITING_FOR_OWNER_VALIDATION`

Evidence classification: `MACHINE_VERIFIED`

Verified at: 2026-07-31, server UTC+08:00 / America/Toronto UTC-04:00

Environment label: `restaurant-prod`

## 1. Scope and safety boundary

This report records the first approved, isolated same-host Staging build,
startup, and restart-recovery verification. It used the exact Owner-approved
Git commit, a detached release, SHA-specific images, a dedicated Compose
project, an isolated PostgreSQL path, loopback-only HTTP, and disabled
printing.

The run did not:

- modify the Production checkout or read the Production `.env`;
- modify, stop, restart, or recreate project `cloud`;
- copy Production data, accounts, passwords, devices, printers, or customer
  records;
- run REAL, MOCK, or PAD_DIRECT printing;
- run restore, `Flyway clean`, `down -v`, image/system/builder prune, or a
  database write outside Flyway startup;
- modify the approved release checkout;
- start STG-005, STG-006, AL-003, or another backlog item.

## 2. Approved identity and evidence binding

| Item | Machine-verified value |
| --- | --- |
| Approved SHA | `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` |
| Invalid prior SHA | `35033645b5414f0804cc0aba92a8b8bb832bb074`; not used |
| Release realpath | `/srv/restaurant-pos/staging/releases/4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` |
| Release state | Detached exact SHA; clean tracked, staged, and untracked state |
| Compose project | `restaurant-pos-staging` |
| Staging state | `/srv/restaurant-pos/staging/state` |
| Private environment | `/srv/restaurant-pos/staging/config/.env.staging`, owner `ubuntu`, mode `0600` |
| Environment SHA-256 | `926a075e482215b1e8c0917a96db483f342dfed895adfe122f1c9cccb63fa94c` |
| Preflight evidence | `/srv/restaurant-pos/staging/evidence/stg-004-preflight-4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c.txt` |
| Evidence SHA-256 | `01fca943915a922a389c3d00d6e38bb5dcbcae3dc5bed5e1718daf1d875f1707` |
| Printing | `STAGING_PRINT_MODE=DISABLED`; printing feature `false`; endpoint absent or empty |

The formal preflight exited `0` with `SUMMARY|PASS`. Path, permissions,
release, port, disk, memory/CPU, Docker context, Compose service, input, and
container-metadata checks passed. The two exact-SHA images were
`PENDING_PREBUILD`, the expected state before the first build.

## 3. Build and isolated Docker CLI state

The approved wrapper revalidated the approved SHA, environment digest, evidence
digest, Staging root, project, and printing gates before execution.

Build order was machine observed:

1. `restaurant-pos-backend:staging-4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`
2. `restaurant-pos-frontend:staging-4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`
3. `docker compose up -d` for the isolated Staging project

The frontend build started only after the backend image completed. The
PR #37 wrapper created an owner-only `mktemp` root with writable mode-`0700`
`HOME` and `DOCKER_CONFIG`, verified Compose under Docker context `default`,
and did not read the user's default `~/.docker`. Matching temporary CLI-state
directory counts were zero before and after deploy, initial verification, and
restart verification.

The frontend `npm ci` reported six dependency audit findings: one low, four
high, and one critical. This is a follow-up dependency risk; the deployment
did not silently alter lock-file versions.

## 4. Images, containers, network, and ports

| Service | Container | Image ID | Final state |
| --- | --- | --- | --- |
| db | `restaurant-pos-staging-db-1` | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` | Running, healthy |
| backend | `restaurant-pos-staging-backend-1` | `sha256:61bc50fc8a2c3d4bef6ccbb2d2c6450545417df0b876cf682c9efc2d3b92189a` | Running; health endpoint 200 |
| nginx | `restaurant-pos-staging-nginx-1` | `sha256:1382d1c4205c70fce0f83326d37de8f6a67b97ca39c4707e0e14f2383b6e1077` | Running; frontend 200 |

The bridge network is `restaurant-pos-staging_restaurant-pos`
(observed ID prefix `d1367daac4f2`). PostgreSQL mounts only
`/srv/restaurant-pos/staging/state/postgres` at
`/var/lib/postgresql/data`. Nginx mounts the approved release's reviewed HTTP
template; backend has no bind mount.

The only host port is `127.0.0.1:18080->80/tcp`. No
`0.0.0.0:18080`, `[::]:18080`, host 80, or host 443 Staging binding was
observed. PostgreSQL and backend remain internal to the Staging network.

## 5. PostgreSQL, Flyway, JPA, and data

PostgreSQL reported version 16.14. First startup applied these eight successful
migrations:

| Version | Script | Checksum |
| --- | --- | ---: |
| 1 | `V1__baseline_current_schema.sql` | 431188510 |
| 2 | `V2__add_versioned_menu_revision.sql` | -1546045661 |
| 3 | `V3__add_idempotent_order_submission_and_dispatch_outbox.sql` | -1713808660 |
| 4 | `V4__add_menu_item_sort_order.sql` | 1636049775 |
| 5 | `V5__set_cold_chicken_noodle_default_type.sql` | -1638580130 |
| 6 | `V6__add_order_item_routing_snapshots.sql` | -1681894826 |
| 7 | `V7__add_print_job_attention_acknowledgement.sql` | 625683957 |
| 8 | `V8__add_owner_store_onboarding_requests.sql` | 1654406856 |

The backend log reported eight migrations applied, schema version v8, JPA
EntityManagerFactory initialization, and successful application startup.

The approved restart test stopped only Staging nginx, backend, and db, then
started db, backend, and nginx in dependency order. Before and after restart:

- Flyway history contained exactly eight successful rows;
- the version/checksum sequence was identical;
- schema version remained 8;
- business counts remained Stores 0, Store Devices 0, Print Jobs 0.

On second startup the backend reported eight migrations validated, current
schema version 8, `Schema "public" is up to date. No migration necessary`,
JPA initialization, and successful application startup. No second-start
`Migrating schema` event was observed.

## 6. HTTP and proxy evidence

| Path | Result | Interpretation |
| --- | --- | --- |
| `/` | HTTP 200 | Nginx serves the exact-SHA frontend image. |
| `/api/v1/system/health` | HTTP 200 | Nginx `/api` proxy and backend health are available. |
| `/ws/info` | HTTP 200 | SockJS information endpoint is available through Nginx. |

No real STOMP subscription, order write, account creation, or print action was
performed.

## 7. Resource and log controls

Pre-build available memory was approximately 2.1 GiB. The first-start snapshot
showed approximately 1.8 GiB available memory and 42 GiB free disk. The final
post-restart snapshot showed approximately 1.8 GiB available memory.

| Service | Final sampled CPU | Final sampled memory | Memory limit |
| --- | ---: | ---: | ---: |
| db | 0.09% | 39.16 MiB | 512 MiB |
| backend | 0.43% | 302.2 MiB | 768 MiB |
| nginx | 0.00% | 3.422 MiB | 128 MiB |

All services use the Docker `local` log driver with `max-size=10m` and
`max-file=3`. This is an idle point-in-time observation, not load, pressure,
or long-duration capacity evidence.

## 8. Production continuity

Production project `cloud` remained running throughout the build, start, and
Staging restart:

| Service | Container ID prefix | Started at (UTC) | Restart count |
| --- | --- | --- | ---: |
| db | `c2ab37fec6ac` | `2026-07-11T12:09:37.936437539Z` | 0 |
| backend | `e5027dc08709` | `2026-07-24T19:44:29.149398323Z` | 0 |
| nginx | `a5c37d6f289c` | `2026-07-24T19:44:29.346691651Z` | 0 |

The IDs, start times, running states, and restart counts matched before the
Staging build, after first startup, before restart, and after final recovery.
This proves container continuity during this bounded run; it is not a
production business smoke test.

## 9. Non-mutating command issue

The first read-only Production baseline command used a Docker Go template that
assumed every container had `.State.Health`. It exited on the backend, which
has no Healthcheck. No state changed. The retry separated base state from the
optional Health field and completed successfully.

During restart recovery, the first frontend request briefly received a
connection reset while Nginx/backend were still becoming ready; the bounded
retry then returned 200, followed by 200 for backend health and `/ws/info`.

## 10. Access and remaining Owner validation

The server keeps Staging running. From a trusted local machine, the approved
loopback tunnel command is:

```bash
ssh -N -L 18080:127.0.0.1:18080 restaurant-prod
```

Then open `http://127.0.0.1:18080`. This does not expose Staging publicly.

Still pending Owner validation:

- browser login and synthetic workspace acceptance;
- synthetic AL-002 onboarding behavior and Store Code concurrency;
- real STOMP message exchange;
- Android access through an explicitly approved tunnel/network path;
- backup/restore rehearsal;
- pressure or soak testing;
- any production release decision.

Staging remains running at the approved SHA. The next state is
`STG-004_SERVER_STAGING_RUNNING_WAITING_FOR_OWNER_VALIDATION`; no merge,
Production deployment, or next Loop is authorized by this evidence.
