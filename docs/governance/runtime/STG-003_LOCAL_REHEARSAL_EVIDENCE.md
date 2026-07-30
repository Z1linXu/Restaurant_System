# STG-003 Local Isolated Rehearsal Evidence

Status: `STG-003_LOCAL_REHEARSAL_COMPLETE_WAITING_FOR_OWNER_REVIEW`

Evidence classification: `MACHINE_VERIFIED`

Verified at: 2026-07-30, America/Toronto

## 1. Scope and boundary

This report records a real local Docker Compose rehearsal of the STG-002
standalone package. It used an exact clean Git SHA, local Docker Desktop, an
isolated temporary PostgreSQL state directory, synthetic credentials, and
application-level `DISABLED` printing.

The rehearsal did not:

- connect to a server, production database, domain, or Android device;
- read or mount production data or a production `.env`;
- create a Store, user, printer, Pad pairing, order, or customer record;
- run REAL, MOCK, or PAD_DIRECT printing;
- run restore, `Flyway clean`, edit `flyway_schema_history`, or use
  `docker compose down -v`;
- start STG-005, AL-003, merge a PR, or deploy any environment.

## 2. Git and local runtime baseline

| Item | Verified value |
| --- | --- |
| Branch | `codex/stg-003-docker-rehearsal` |
| Base branch | `origin/main` |
| Base SHA | `4ac1d10cde169bf7ebc807aac3624b0323e3c440` |
| Rehearsed SHA | `b17ffa9a397bef62d474a58b649f1e55467a974f` |
| Docker Client | 29.6.2, darwin/arm64 |
| Docker Server | 29.6.2, linux/arm64 |
| Docker Compose | v5.3.1 |
| Docker context endpoint | Local Unix socket through Docker Desktop |
| Compose project | `restaurant-pos-staging` |
| Staging root | `/private/tmp/restaurant-pos-stg003-docker-b17ffa9/restaurant-pos/staging` |
| Printing | `STAGING_PRINT_MODE=DISABLED`; feature flag `false` |
| Data policy | Empty synthetic database; all checked business counts were zero |

The branch was safely rebased onto the latest `origin/main` after PR #31
merged. The previously merged PR #32 remains historical; this continuation
branch carries the real Docker evidence and compatibility fixes.

## 3. Compose isolation

`docker compose config` and the runner's private JSON/YAML checks passed before
each build/start.

| Check | Result | Evidence |
| --- | --- | --- |
| Services | `MACHINE_VERIFIED` | Exactly `db`, `backend`, `nginx`. |
| Project | `MACHINE_VERIFIED` | Explicit `restaurant-pos-staging`. |
| Published ports | `MACHINE_VERIFIED` | Only Nginx `127.0.0.1:18080->80/tcp`; PostgreSQL and backend have no host binding. |
| Production ports | `MACHINE_VERIFIED` | No `0.0.0.0`, host 80, or host 443 binding. |
| PostgreSQL state | `MACHINE_VERIFIED` | Bind mount source is the isolated temporary Staging root; destination is `/var/lib/postgresql/data`. |
| Production data | `MACHINE_VERIFIED` | No production checkout or production PostgreSQL path is mounted. |
| Network | `MACHINE_VERIFIED` | `restaurant-pos-staging_restaurant-pos`, local bridge network. |
| Resource limits | `MACHINE_VERIFIED` | DB 0.75 CPU/512MiB, backend 1 CPU/768MiB, Nginx 0.25 CPU/128MiB. |
| Log rotation | `MACHINE_VERIFIED` | Docker `local` driver, `max-size=10m`, `max-file=3` for every service. |
| Seed policy | `MACHINE_VERIFIED` | Database counts: Stores 0, users 0, printers 0, Pad devices 0, Print Jobs 0. |

Compose v5 normalizes `1.00` to `1` and memory suffixes to byte values.
The guards now validate those normalized values per service rather than
searching for raw dotenv strings.

## 4. Images and containers

The backend and frontend images used the required full-SHA tag.

| Service | Container | Image | Image ID (first 12) | Runtime state observed |
| --- | --- | --- | --- | --- |
| db | `restaurant-pos-staging-db-1` | `postgres:16-alpine` | `57c72fd2a128` | Running, healthy |
| backend | `restaurant-pos-staging-backend-1` | `restaurant-pos-backend:staging-b17ffa9a397bef62d474a58b649f1e55467a974f` | `1a83ee83aa28` | Running; HTTP health 200 |
| nginx | `restaurant-pos-staging-nginx-1` | `restaurant-pos-frontend:staging-b17ffa9a397bef62d474a58b649f1e55467a974f` | `1dfad34a6d3e` | Running; root HTTP 200 |

Observed container IDs (first 12) were `43854cc4ab2e` (db),
`1cd312b9b64e` (backend), and `6eaa82837b8b` (nginx). All restart counts were
zero during evidence collection.

## 5. PostgreSQL and Flyway

PostgreSQL reported version 16.14.

### First startup

The backend started with the cloud profile against an empty database. Flyway
validated and applied eight migrations:

| Version | Script | Checksum | Success |
| --- | --- | ---: | --- |
| 1 | `V1__baseline_current_schema.sql` | 431188510 | true |
| 2 | `V2__add_versioned_menu_revision.sql` | -1546045661 | true |
| 3 | `V3__add_idempotent_order_submission_and_dispatch_outbox.sql` | -1713808660 | true |
| 4 | `V4__add_menu_item_sort_order.sql` | 1636049775 | true |
| 5 | `V5__set_cold_chicken_noodle_default_type.sql` | -1638580130 | true |
| 6 | `V6__add_order_item_routing_snapshots.sql` | -1681894826 | true |
| 7 | `V7__add_print_job_attention_acknowledgement.sql` | 625683957 | true |
| 8 | `V8__add_owner_store_onboarding_requests.sql` | 1654406856 | true |

The Flyway log reported: eight migrations successfully applied and schema
version v8.

### V8 schema checks

| Check | Result |
| --- | --- |
| Table | `public.owner_store_onboarding_requests` exists |
| Primary key | `owner_store_onboarding_requests_pkey` on `id` |
| Idempotency uniqueness | `uq_owner_store_onboarding_organization_key` on `(organization_id, idempotency_key)` |
| Store lookup index | `idx_owner_store_onboarding_request_store` on `store_id` |

### Second startup and persistence

The runner stopped all three services without deleting state, then started the
same project again. The second backend log reported:

- eight migrations successfully validated;
- current schema version 8;
- schema up to date, no migration necessary;
- JPA EntityManagerFactory initialized;
- application started successfully.

`application-cloud.yml` declares `spring.jpa.hibernate.ddl-auto=validate`.
Successful cloud-profile initialization therefore provides machine evidence
that JPA schema validation passed.

Flyway history still contained exactly eight successful rows and exactly one V8
row. The PostgreSQL state directory remained approximately 48MiB.

## 6. HTTP and proxy checks

| Path | HTTP result | Interpretation |
| --- | --- | --- |
| `/` | 200 | Nginx served the built frontend. |
| `/api/v1/system/health` | 200 | Nginx `/api` proxy and backend health path worked. |
| `/ws/info` | 200 | SockJS information endpoint was reachable through Nginx. |
| `/ws` with ordinary HTTP GET | 400 | Request reached the backend, which logged a missing Upgrade header. This is a basic proxy-entry check, not a failed or successful STOMP/WebSocket session. |

No real WebSocket subscription, order action, print action, or business write
was performed.

## 7. Resource observation

One `docker stats --no-stream` sample after the second startup showed:

| Service | CPU | Memory | Limit | PIDs |
| --- | ---: | ---: | ---: | ---: |
| db | 0.16% | 38.86MiB | 512MiB | 16 |
| backend | 1.62% | 283.8MiB | 768MiB | 34 |
| nginx | 0.00% | 7.406MiB | 128MiB | 9 |

This is a single idle local sample, not load, soak, production-capacity, or
same-server headroom evidence.

## 8. Cleanup and retained state

The approved cleanup path executed plain Compose `down` for only
`restaurant-pos-staging`.

- the three local Staging containers were removed;
- `restaurant-pos-staging_restaurant-pos` was removed;
- port 18080 was released;
- no `-v` or `--volumes` option was used;
- the PostgreSQL state directory remained mode 0700 and approximately 48MiB;
- the exact detached release worktree and runner evidence were retained.

## 9. Failed attempts retained as evidence

| Attempt | Result | Root cause and containment |
| --- | --- | --- |
| Pre-fix Compose v5 run | Failed before build | Guard expected raw `1.00`/`512m` text, while Compose v5 emitted normalized values. No container or network was created. |
| Two `e20ec24` build attempts | Failed before startup | Docker Hub metadata requests timed out. A bounded diagnostic showed the personal Docker `credsStore=desktop` helper blocked anonymous image resolution. No container or network was created. |
| `0005db1` run | Failed before build | `DOCKER_CONFIG=/nonexistent` avoided the credential helper but also hid the user-installed Compose plugin. No container or network was created. |
| Final `b17ffa9` run | Passed | A generated credential-free Docker CLI config retained only the verified Compose plugin directory. Images built and the full lifecycle passed. |
| First local frontend test invocation | Failed before tests | `vitest: command not found` because host `node_modules` was absent. `npm ci` installed lock-file dependencies; the required test/build then passed. |

No failed attempt changed application code, production state, or database
schema. Failed local roots were preserved rather than silently overwritten.

## 10. Automated verification

| Command | Result |
| --- | --- |
| `bash -n` for all Staging shell scripts | PASS |
| `deployment/cloud/tests/test_staging_guard.sh` | PASS |
| `deployment/cloud/tests/test_staging_local_rehearsal.sh` | PASS |
| Real `docker compose config` through the runner | PASS |
| `backend: mvn -q test` | PASS |
| `backend: mvn -q -DskipTests compile` | PASS |
| `frontend: npm test -- --run` | PASS: 16 files, 76 tests |
| `frontend: npm run build` | PASS |
| `git diff --check` | PASS at the verification checkpoint |

The frontend install reported six dependency audit findings (one low, four
high, one critical). They are a follow-up dependency risk; STG-003 did not
silently change package versions.

## 11. Acceptance result and remaining boundaries

All STG-003 local rehearsal acceptance criteria are satisfied:

- exact SHA, project, image, port, path, printing, resource, and log guards;
- real db/backend/nginx build and startup;
- Flyway V1-V8 first startup;
- one V8 row and verified V8 table/constraint/index;
- second startup with no repeated migration or checksum/schema error;
- cloud-profile backend and JPA validation;
- Nginx frontend, `/api`, and bounded `/ws` entry checks;
- PostgreSQL persistence across stop/start;
- synthetic-only empty business data;
- non-volume cleanup with retained database state.

Still not verified or authorized:

- server or same-production-host Staging deployment;
- TLS, domain, firewall, SSH tunnel, or VPN access;
- synthetic login/order/onboarding acceptance data;
- real WebSocket/STOMP messaging;
- Android Staging access;
- backup/restore rehearsal;
- production capacity or pressure testing;
- REAL/MOCK/PAD_DIRECT print execution.

STG-005 and AL-003 have not started. The next action is Owner review of this
branch/PR, not merge or deployment.
