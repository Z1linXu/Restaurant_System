# Phase 3B Cloud Runtime Evidence

## Scope and provenance

- Environment label: restaurant-prod
- Verification timestamp: 2026-07-24, America/Toronto (EDT)
- Verification mode: owner-approved, read-only runtime evidence collection
- Local governance baseline commit: ed5f173273208429d301233da128a7626e69cd15
- Server checkout commit: 3d7ad88e1ec4c6d11a05aa2fbada7abaa889e611
- Server branch observed: main
- Server latest commit summary: Merge pull request #25 from Z1linXu/fix/pad-menu-cache-submission

The local governance baseline and the server checkout are different commits. The
governance baseline is not evidence of the application version currently deployed.
The server checkout commit is the only repository version used for the server-side
observations in this report.

This report records evidence already collected during Phase 3B. No further remote
commands were executed for this report.

## Classification vocabulary

- VERIFIED_MATCH: The observed runtime fact matches the stated repository or
  deployment expectation within the checked scope.
- VERIFIED_DIFFERENCE: The observed runtime fact differs from the checked
  repository or deployment expectation.
- RUNTIME_EVIDENCE_PENDING: The repository or a passive observation is
  insufficient to establish the final runtime fact.
- NOT_RUN_BY_POLICY: The check was intentionally not run because the approved
  read-only scope did not provide the required test object or permission.

## Server repository baseline

| Check | Evidence | Classification |
|---|---|---|
| Project directory | The expected project directory exists at the approved location under the remote user's home directory. | VERIFIED_MATCH |
| Branch | main | VERIFIED_MATCH |
| Commit | 3d7ad88e1ec4c6d11a05aa2fbada7abaa889e611 | VERIFIED_MATCH |
| Working tree | Git status reported untracked deployment runtime files. No contents of those files were read. | VERIFIED_DIFFERENCE |
| Intended approved release | No release tag or separate deployment approval was established by the read-only checks. | RUNTIME_EVIDENCE_PENDING |
| Expected deployment data | The relationship between the untracked runtime files and the intended deployment state was not established. | RUNTIME_EVIDENCE_PENDING |

No git pull, fetch, checkout, reset, clean, merge, or repair command was run on
the server. The untracked runtime files remain a runtime difference and were not
modified.

## Compose and container evidence

The static Compose service names were:

- db
- backend
- nginx

The observed project containers were:

| Service | Container | Container ID prefix | Created | Restart count | Status | Health | Image ID prefix | Mount destinations |
|---|---|---|---|---:|---|---|---|---|
| db | cloud-db-1 | c2ab37fec6ac | 2026-07-11T12:09:37Z | 0 | running | healthy | 57c72fd2a128 | /var/lib/postgresql/data |
| backend | cloud-backend-1 | c629ad857072 | 2026-07-19T20:49:01Z | 0 | running | NO_HEALTHCHECK | 1dead6ad13dd | none |
| nginx | cloud-nginx-1 | 12e8e87bba00 | 2026-07-19T20:49:12Z | 0 | running | NO_HEALTHCHECK | 8d13a3d42b1e | /etc/nginx/templates/default.conf.template; /var/www/certbot; /etc/letsencrypt |

Mount sources were intentionally redacted because they contain private server
paths. No container environment, command, host configuration, labels, or full
container JSON was read or recorded.

| Compose/runtime check | Classification |
|---|---|
| Expected db/backend/nginx service structure | VERIFIED_MATCH |
| All three expected containers present and running | VERIFIED_MATCH |
| Database container healthcheck is healthy | VERIFIED_MATCH |
| Restart count observed as zero | VERIFIED_MATCH |
| Backend healthcheck | VERIFIED_DIFFERENCE: no healthcheck was reported |
| Nginx healthcheck | VERIFIED_DIFFERENCE: no healthcheck was reported |
| Overall application health from container metadata alone | RUNTIME_EVIDENCE_PENDING |

The missing backend and nginx healthchecks are an observability difference. They
are not, by themselves, evidence that either service is unhealthy.

## Backend runtime evidence

The explicit runtime environment profile was:

- SPRING_PROFILES_ACTIVE: cloud

This is an explicit runtime environment override and is classified as
VERIFIED_MATCH for the observed container.

No explicit runtime environment override was found for the following keys. This
means only that no explicit environment override was found; it does not establish
the final effective value, that a feature is disabled, or that a configuration
default is absent:

- SPRING_FLYWAY_ENABLED
- SPRING_JPA_HIBERNATE_DDL_AUTO
- APP_AUTH_X_USER_ID_FALLBACK_ENABLED
- APP_DEV_TOOLS_ROLE_SWITCHER_ENABLED
- APP_SEED_RUNTIME_ENABLED
- APP_SEED_DEFAULT_USERS_ENABLED
- APP_SEED_DEMO_DATA_ENABLED
- APP_FEATURES_CORE_POS
- APP_FEATURES_PRINTING
- APP_FEATURES_KDS
- APP_FEATURES_ADMIN
- APP_FEATURES_ANALYTICS
- APP_FEATURES_PLATFORM
- APP_FEATURES_DEVELOPER_TOOLS

Sensitive configuration presence was recorded only in redacted form:

| Key | Observed |
|---|---|
| JWT_SECRET | PRESENT_REDACTED |
| DB_PASSWORD | PRESENT_REDACTED |
| CORS_ALLOWED_ORIGINS | UNSET |
| WEBSOCKET_ALLOWED_ORIGINS | UNSET |
| DOMAIN | UNSET |

No secret values, token values, password values, authorization headers, or full
connection strings were read or recorded. Presence does not establish secret
strength or configuration correctness.

The passive backend health endpoint returned HTTP 200:

- /api/v1/system/health: 200, VERIFIED_MATCH for endpoint reachability

The following remain RUNTIME_EVIDENCE_PENDING:

- final effective Flyway setting
- final effective JPA schema setting
- final effective auth fallback setting
- final effective seed settings
- final effective feature flags
- effective CORS and WebSocket origin boundaries
- full backend application health beyond the checked endpoint

## Flyway evidence

The server checkout contains these migration files:

- V1__baseline_current_schema.sql
- V2__add_versioned_menu_revision.sql
- V3__add_idempotent_order_submission_and_dispatch_outbox.sql
- V4__add_menu_item_sort_order.sql
- V5__set_cold_chicken_noodle_default_type.sql
- V6__add_order_item_routing_snapshots.sql

The read-only database history contained the following successful rows:

| Version | Description | Script | Database checksum | Success | Installed on (UTC) |
|---:|---|---|---:|---|---|
| 1 | baseline current schema | V1__baseline_current_schema.sql | 431188510 | true | 2026-07-11 08:12:00.441154 |
| 2 | add versioned menu revision | V2__add_versioned_menu_revision.sql | -1546045661 | true | 2026-07-13 15:50:30.378796 |
| 3 | add idempotent order submission and dispatch outbox | V3__add_idempotent_order_submission_and_dispatch_outbox.sql | -1713808660 | true | 2026-07-13 15:50:30.549387 |
| 4 | add menu item sort order | V4__add_menu_item_sort_order.sql | 1636049775 | true | 2026-07-15 12:12:27.124676 |
| 5 | set cold chicken noodle default type | V5__set_cold_chicken_noodle_default_type.sql | -1638580130 | true | 2026-07-15 13:09:37.55345 |
| 6 | add order item routing snapshots | V6__add_order_item_routing_snapshots.sql | -1681894826 | true | 2026-07-19 16:49:20.968577 |

V7 was absent from both the server checkout migration file set and the
database history observed in this phase.

The installed_on values above are recorded as UTC values from the approved
read-only query.

| Migration comparison | Classification |
|---|---|
| Database V1-V6 rows are successful | VERIFIED_MATCH |
| Database history matches the migration files in server commit 3d7ad88 | VERIFIED_MATCH within the checked-in migration set |
| Local governance baseline includes V7 while server checkout and database do not | VERIFIED_DIFFERENCE |
| Whether V7 is required for the intended release | RUNTIME_EVIDENCE_PENDING |

The database checksum values are Flyway's recorded integer checksums. They are
not directly comparable to local SHA-256 file hashes without an explicit
algorithm mapping; that comparison remains RUNTIME_EVIDENCE_PENDING.

No migrate, repair, clean, or other database write was executed.

## Store and printing configuration

The observed store configuration was:

| Store | Status | Printing enabled | Printing mode |
|---:|---|---|---|
| 1 | active | true | PAD_DIRECT |

Four enabled printer configurations were observed:

| Printer ID | Name | Type | Paper width | Port | Encoding | Printer font size | Endpoint |
|---:|---|---|---:|---:|---|---|---|
| 1 | Test | ESC_POS_TCP | 80mm | 9100 | GBK | MEDIUM | PRESENT_REDACTED |
| 2 | Main Printer | ESC_POS_TCP | 80mm | 9100 | GBK | MEDIUM | PRESENT_REDACTED |
| 3 | Grab Printer | ESC_POS_TCP | 80mm | 9100 | GBK | MEDIUM | PRESENT_REDACTED |
| 4 | Fired Printer | ESC_POS_TCP | 80mm | 9100 | GBK | MEDIUM | PRESENT_REDACTED |

Printer assignment rows observed:

| Module | Printer ID | Enabled | Module font size | Takeout copies |
|---|---:|---|---|---:|
| FRONTDESK_RECEIPT | 2 | true | SMALL | 1 |
| GRAB | 3 | true | MEDIUM | 1 |
| HOT_KITCHEN | 4 | true | LARGE | 1 |

Printer 1 was not assigned in the observed assignment rows.

| Printing check | Classification |
|---|---|
| Store 1 PAD_DIRECT mode | VERIFIED_MATCH |
| GRAB, FRONTDESK_RECEIPT, HOT_KITCHEN assignments | VERIFIED_MATCH |
| Assignment font-size values saved in runtime data | VERIFIED_MATCH |
| Assigned endpoint reachability | RUNTIME_EVIDENCE_PENDING |
| Physical print output applying the configured font sizes | RUNTIME_EVIDENCE_PENDING |

No printer connection test and no test print were performed in this phase.

## Registered Android Pad devices

Seven Store 1 Android device records were observed. Device names were not
included in this report. No device token, token hash, or credential value was
read.

| Device ID | Status | Active flag | Platform | App version | Last seen |
|---:|---|---|---|---|---|
| 1 | ACTIVE | true | ANDROID | unknown | 2026-07-11 20:38:23.884282 |
| 2 | ACTIVE | true | ANDROID | unknown | 2026-07-11 20:39:42.332384 |
| 3 | ACTIVE | true | ANDROID | unknown | 2026-07-11 20:41:27.739113 |
| 4 | ACTIVE | true | ANDROID | unknown | 2026-07-21 21:30:30.852208 |
| 5 | ACTIVE | true | ANDROID | unknown | 2026-07-21 21:51:20.764916 |
| 6 | ACTIVE | true | ANDROID | unknown | 2026-07-12 17:52:42.934661 |
| 7 | ACTIVE | true | ANDROID | unknown | 2026-07-22 13:23:50.891163 |

| Device check | Classification |
|---|---|
| Seven Store 1 Android device rows exist | VERIFIED_MATCH |
| All observed rows are ACTIVE and enabled | VERIFIED_MATCH |
| App versions are known and traceable | VERIFIED_DIFFERENCE: app_version was unknown |
| Devices are currently online | RUNTIME_EVIDENCE_PENDING |
| Android worker is enabled and polling | RUNTIME_EVIDENCE_PENDING |

The last_seen values are database observations at the recorded timestamps. They
are not proof of current connectivity or worker activity.

## Frontend, static metadata, and WebSocket entry points

Passive HTTP observations from the local Nginx endpoint:

| Path | HTTP status | Download size | Interpretation |
|---|---:|---:|---|
| / | 200 | 954 | VERIFIED_MATCH for homepage reachability |
| /build-info.json | 200 | 954 | The response was SPA homepage content, not confirmed metadata |
| /asset-manifest.json | 200 | 954 | The response was SPA homepage content, not confirmed metadata |
| /ws | 400 | 34 | RUNTIME_EVIDENCE_PENDING; ordinary HTTP GET is not a WebSocket handshake |
| /ws/info | 200 | 78 | VERIFIED_MATCH for SockJS info entry existence only |

The SHA-256 of the server response for both /build-info.json and
/asset-manifest.json was:

eee87274888edbc7cbf6d9eaf57f6745329ac6496cd861f7d5042093697f84f0

The two metadata paths had the same download size and the same SHA-256 hash.
Neither response contained the expected Build Info fields, and neither file was
present in the Nginx container. These observations are consistent with SPA
fallback behavior. This phase did not calculate the SHA-256 of the homepage
response itself, so it does not establish that either metadata-path hash is the
homepage hash. The difference is not classified as a frontend failure.

The Nginx container did not contain:

- /usr/share/nginx/html/build-info.json
- /usr/share/nginx/html/asset-manifest.json

Safe Build Info fields were not found in the server response. Server
Build Version, Generated At, and Offline Database Schema Version are therefore
RUNTIME_EVIDENCE_PENDING.

The Phase 3A local metadata hashes were:

| Local artifact | SHA-256 |
|---|---|
| build-info.json | 1d72da47fbe447e14d413a1e49b030aabd887d69f29f0c64e6ba77c0bc312964 |
| asset-manifest.json | e799f8f92dfea30ff651efb5e71be24f6820c888c7b0d9684d945de1b8dcd926 |

The server and local metadata observations differ, but local or bundled asset
hashes are not proof of what the server should contain. This difference is not
classified as a frontend failure.

## PAD_DIRECT print-job evidence

No existing test Print Job ID was provided and approved for this phase.
Accordingly, the following operations were not performed:

- no all-job query
- no print_job_attempts query
- no order submission
- no reprint creation
- no new Print Job creation
- no claim
- no start print
- no payload fetch
- no complete
- no fail
- no release

Classification: NOT_RUN_BY_POLICY.

Reason: A broad job query would exceed the approved read-only scope, and no
specific existing owner-approved test job was supplied. PAD_DIRECT runtime
processing, attempt records, and worker behavior remain unverified.

## Backup metadata evidence

The repository backup script documents the default path as:

- deployment/cloud/backups, relative to the deployment/cloud directory

The default remote directory was not present during the approved metadata check.
The backup directory override from .env was not inspected.

| Backup check | Result | Classification |
|---|---|---|
| Default deployment/cloud/backups directory | Not found | VERIFIED_DIFFERENCE |
| Backup file count | Not run because the default directory was absent | RUNTIME_EVIDENCE_PENDING |
| Backup file sizes and modification times | Not available | RUNTIME_EVIDENCE_PENDING |
| Backup directory override | Not inspected | RUNTIME_EVIDENCE_PENDING |
| Backup contents | Not read by policy | RUNTIME_EVIDENCE_PENDING |
| Restore success | Not tested | RUNTIME_EVIDENCE_PENDING |

The absence of the default directory is not evidence that no backups exist
elsewhere, and it is not evidence that a restore would fail. No server-wide
search was performed. No backup content was read. No checksum, pg_dump,
pg_restore, backup script, or restore script was run.

## Overall findings

| Domain | Result |
|---|---|
| Server project location and Git checkout | VERIFIED_MATCH for the observed project and commit |
| Compose service structure and running containers | VERIFIED_MATCH |
| Database container health | VERIFIED_MATCH |
| Backend explicit cloud profile | VERIFIED_MATCH |
| Backend effective configuration | RUNTIME_EVIDENCE_PENDING |
| Flyway V1-V6 against the current server checkout | VERIFIED_MATCH |
| Local governance baseline versus server/database migration set | VERIFIED_DIFFERENCE |
| Store 1 PAD_DIRECT mode and module assignments | VERIFIED_MATCH |
| Printer network reachability and physical output | RUNTIME_EVIDENCE_PENDING |
| Registered Android Pad rows | VERIFIED_MATCH |
| Installed APK versions and live worker state | RUNTIME_EVIDENCE_PENDING |
| Homepage and SockJS info entry | VERIFIED_MATCH |
| WebSocket/STOMP end-to-end operation | RUNTIME_EVIDENCE_PENDING |
| Server build metadata | RUNTIME_EVIDENCE_PENDING |
| PAD_DIRECT print-job processing | NOT_RUN_BY_POLICY |
| Default backup directory metadata | VERIFIED_DIFFERENCE |
| Backup usability and restore rehearsal | RUNTIME_EVIDENCE_PENDING |

## Safety and non-actions

This Phase 3B report was created from already collected, owner-approved,
read-only evidence. No additional remote command was run while creating it.

The phase did not:

- deploy or update the server
- run git pull, fetch, checkout, reset, clean, merge, or repair remotely
- start, stop, restart, recreate, build, pull, or remove containers
- run deployment scripts
- run Flyway migrate, repair, or clean
- modify the database or server files
- submit orders or create reprints or Print Jobs
- claim, start, fetch, complete, fail, or release PAD_DIRECT jobs
- modify Android devices or APKs
- read or record JWTs, refresh tokens, device tokens, token hashes,
  authorization headers, database passwords, raw print payloads, customer
  information, or customer notes

Phase 1 and Phase 2 governance reports, application code, runtime configuration,
Flyway migrations, Android source, generated assets, and deployment files were
not modified for this report. No Phase 3C work was started.

## Evidence still requiring an approved runtime procedure

The following require a separately approved, narrowly scoped verification:

- confirm the intended production release commit and deployment provenance
- confirm final effective Spring and application configuration without exposing secrets
- validate HTTPS and real WebSocket/STOMP behavior
- verify printer reachability and physical print output
- verify installed APK build versions and Android worker state
- inspect a specifically identified, existing, owner-approved PAD_DIRECT test job
- verify backup location metadata if an approved directory is provided
- perform a separately approved backup integrity or restore rehearsal

Any future PAD_DIRECT validation must use an already-existing, owner-approved
test Job ID. It must not create or mutate jobs unless a later scope explicitly
approves those operations.

## Local completion checks

The final local checks for this report are:

- git diff --check
- git diff --stat
- git status --short

The expected final local change is one new report:

docs/governance/runtime/CLOUD_RUNTIME_EVIDENCE.md

The checks performed after this report was updated were:

- git diff --check: passed with no output.
- git diff --stat: no output because this report is an untracked file and the
  default git diff does not include untracked files.
- git status --short: exactly
  ?? docs/governance/runtime/CLOUD_RUNTIME_EVIDENCE.md

No other local changes were present. No new remote verification was executed.
Phase 3B is formally complete. Phase 3C has not started.

No commit, push, merge, deployment, documentation unification, or follow-up
Phase 3C activity is part of this checkpoint.
