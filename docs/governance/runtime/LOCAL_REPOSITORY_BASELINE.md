# Local Repository Baseline

## Scope and safety boundary

This report records a local, passive repository baseline only. It does not
prove deployment, database migration, container state, printer state, or APK
installation.

**This check did not connect to any runtime environment.**

No server, database, Android device, domain, external API, deployment script,
container build, container start/stop/restart, image pull, or container
recreation was performed.

## Verification metadata

- Verification timestamp: 2026-07-22T16:25:56Z
- Branch: codex/pilot-site-reliability-batch
- Current commit: ed5f173273208429d301233da128a7626e69cd15
- Latest commit: ed5f173273208429d301233da128a7626e69cd15
- Latest commit subject: docs: add documentation governance audit baseline
- Latest commit time: 2026-07-22T12:23:46-04:00
- Governance audit commit: ed5f173273208429d301233da128a7626e69cd15
- Working tree before this report: clean
- Working tree after this report: one new untracked report, listed below

The governance audit commit contains the six checked-in Phase 1/Phase 2
reports. This Phase 3A report is intentionally not treated as evidence of a
deployed runtime.

## Commands executed

Only local passive commands were used:

- sed to read the three Phase 2 governance reports before checking.
- date, git branch --show-current, git rev-parse HEAD, git log, and
  git status --short --untracked-files=all.
- git ls-files for governance, deployment, migration, and generated metadata
  inputs.
- git show --stat for the current governance audit commit.
- Docker Compose availability/config check only. The Docker command was
  unavailable, so no Compose process ran.
- rg, sed, and test -f for static Compose/profile/build declarations.
- bash -n deployment/cloud/*.sh for shell parsing only.
- find, shasum -a 256, and rg for migration and metadata inspection.

No .env file, secret value, token, password, database URL, printer
credential, or external endpoint response was read or displayed.

## Local repository checks

| Check | Result | Sanitized evidence |
|---|---|---|
| Current branch and commit | PASS | Branch and HEAD are recorded above |
| Governance audit commit | PASS | HEAD contains the six checked-in governance reports |
| Working tree before this report | PASS | No pre-existing status entries were present |
| Cloud Compose service declarations | PASS | Static file declares db, backend, and nginx |
| Compose parser validation | UNKNOWN | Docker CLI was unavailable; docker compose config was not executed successfully |
| Expected service names from parser | UNKNOWN | Docker CLI was unavailable; static declaration says db, backend, nginx |
| Backend profile declaration | PASS | Static default is SPRING_PROFILES_ACTIVE=cloud |
| Backend build model | PASS | Context ../../backend, Dockerfile Dockerfile, local image default |
| Frontend/nginx build model | PASS | Context ../../frontend, Dockerfile Dockerfile, local image default |
| Backend Dockerfile exists | PASS | backend/Dockerfile exists locally |
| Frontend Dockerfile exists | PASS | frontend/Dockerfile exists locally |
| Deployment script parsing | PASS | backup-db.sh, deploy.sh, health-check.sh, and restore-db.sh passed bash -n |
| Deployment execution | UNKNOWN | Deployment scripts were intentionally not run |

The static Compose checks do not prove that a server uses this Compose file,
that images exist, or that services are running.

## Flyway migration chain

These are the migration files present in the local repository. The SHA-256
values identify local files only and do not prove database application.

| Version | File | Local SHA-256 |
|---|---|---|
| V1 | V1__baseline_current_schema.sql | 9de0b6a41cd3367cb35159bb8581439c0a8186ea66fa5dbd7e72193d8cb25900 |
| V2 | V2__add_versioned_menu_revision.sql | b10dffd0e9538a1f4754075e8a8eaa02e5c7a89716b3be724004cf495f083a44 |
| V3 | V3__add_idempotent_order_submission_and_dispatch_outbox.sql | 5f60fe2e4ec743b9df563810b0c212169142ab6a1a3975c62a897701f87d7290 |
| V4 | V4__add_menu_item_sort_order.sql | 495f35b3739c57b8eb646227653515776a579aab2c20d4c82514c859b70841eb |
| V5 | V5__set_cold_chicken_noodle_default_type.sql | 7b621c846941063405a97275b79c259c784f7dc3cfc3be681b965d19ea1bf9ac |
| V6 | V6__add_order_item_routing_snapshots.sql | d9cbd94a6276836635f2a451f3a846d9a2c1ea11020ac52be03951f4f869a2d3 |
| V7 | V7__add_print_job_attention_acknowledgement.sql | 95621913314b7afb44f0d56b42048a10ac9927b5890b4fd6d683d84f3ba303d0 |

| Migration check | Result | Evidence boundary |
|---|---|---|
| V1-V7 files exist locally | PASS | find and git ls-files |
| Local file checksums recorded | PASS | shasum -a 256 |
| V1-V7 applied to a database | UNKNOWN | No database was connected or queried |
| Flyway history matches these files | UNKNOWN | Requires owner-approved target database read-only query |

## Frontend and Android build metadata

Static metadata files exist in the checked-in Android asset directory:

| File | Exists | Local SHA-256 |
|---|---|---|
| restaurant-pad-app/android/app/src/main/assets/web/build-info.json | PASS | 1d72da47fbe447e14d413a1e49b030aabd887d69f29f0c64e6ba77c0bc312964 |
| restaurant-pad-app/android/app/src/main/assets/web/asset-manifest.json | PASS | e799f8f92dfea30ff651efb5e71be24f6820c888c7b0d9684d945de1b8dcd926 |

Sanitized static metadata:

- buildVersion: pilot-reliability-2-20260715
- generatedAt: 2026-07-15T16:56:05.207Z
- manifestVersion: 1
- offlineDatabaseSchemaVersion: 4
- build-info assetManifestSha256 matches the local asset-manifest SHA-256
- source frontend/src/offline/offlineDatabase.ts declares
  OFFLINE_DATABASE_VERSION = 4

| Metadata check | Result | Evidence boundary |
|---|---|---|
| build-info.json exists and is readable | PASS | Local checked-in file |
| asset-manifest.json exists and is readable | PASS | Local checked-in file |
| IndexedDB schema version recorded | PASS | Static metadata reports version 4 |
| Static metadata matches local source declaration | PASS | Source and metadata both report version 4 |
| Metadata is installed on an Android device | UNKNOWN | No Android device was connected |
| Metadata is served by the production frontend | UNKNOWN | No domain or server was contacted |
| Source commit for the generated assets | UNKNOWN | Metadata contains no source commit field |

## Required server or Android verification

The following remain open and require a separately approved runtime session:

- Production branch, commit, image digest, and Compose project.
- Actual db, backend, and nginx containers, health state, mounts, and restart
  history.
- Effective Spring profile, environment variables, feature flags, CORS, and
  WebSocket configuration.
- flyway_schema_history, migration success, checksums, and backup status.
- Store printing mode, module-to-printer assignments, and cloud printer guard
  behavior.
- Registered Pad devices, device activity, installed APK version, pairing
  status, and worker state.
- Served frontend build metadata and WebView mode.
- Health checks, login, workspace loading, and approved smoke-test evidence.

## Final local status for this Phase 3A step

The only file created by this step is:

docs/governance/runtime/LOCAL_REPOSITORY_BASELINE.md

The report is untracked until intentionally added to Git. No application code,
configuration, migration, Android source, generated asset, or existing
governance report was modified.
