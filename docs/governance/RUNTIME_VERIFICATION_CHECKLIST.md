# Runtime Verification Checklist

## Purpose

This is a future, read-only checklist for reconciling repository claims with a
specific deployed environment. It must be completed only with the system
owner's approval and against a named environment. It does not authorize a
deployment, migration, print, reprint, claim, device pairing, or data change.

Repository source and generated assets are not proof of production state. The
operator must record the target environment, timestamp, commit/image digest,
redactions, command output location, and the person who approved access.

## Evidence record

- [ ] Environment name and owner approval recorded.
- [ ] Verification timestamp and timezone recorded.
- [ ] Production hostname/IP is recorded outside this repository; do not add
      secrets or private network details to these reports.
- [ ] Read-only evidence directory is identified.
- [ ] JWTs, passwords, device tokens, database URLs, customer data, and printer
      credentials are redacted.

## A. Production commit and branch

- [ ] Read the deployed Git commit from the deployment system or image label.
- [ ] Record the deployed branch/tag and compare it with the approved release.
- [ ] Record the frontend asset build version and backend image digest.
- [ ] Compare the deployed commit with the intended repository commit.
- [ ] Do not infer deployment from the current checkout, local branch, or a
      successful build.

Suggested owner-approved read-only evidence:

- Git hosting release/commit metadata.
- Container labels and image digests.
- CI artifact manifest.
- Server-side deployment log, without printing environment secrets.

## B. Docker Compose services and images

- [ ] Confirm the active Compose project name.
- [ ] Confirm services are db, backend, and nginx for the cloud package.
- [ ] Record container IDs, image digests, creation times, restart counts,
      health status, and mounted data paths.
- [ ] Confirm backend and nginx image source commit labels if present.
- [ ] Confirm the PostgreSQL data mount/volume is the expected persistent path.
- [ ] Confirm no unexpected frontend orphan is serving the public port.
- [ ] Confirm no command would recreate or remove the database during evidence
      collection.

## C. Spring profile and effective configuration

- [ ] Record the active Spring profile from a safe runtime endpoint, startup
      metadata, or container configuration.
- [ ] Confirm cloud/pilot profile uses Flyway and ddl-auto=validate.
- [ ] Confirm production JWT secret policy is satisfied without exposing the
      secret.
- [ ] Confirm X-User-Id fallback is false in cloud/prod.
- [ ] Confirm role switcher and unsafe developer features are disabled.
- [ ] Confirm printing, KDS, admin, analytics, and platform feature values from
      effective configuration, not only repository defaults.
- [ ] Record whether CORS/WebSocket origins match the deployed frontend.

## D. Flyway schema history

- [ ] With owner-approved read-only DB access, query flyway_schema_history.
- [ ] Record installed versions, descriptions, success flags, checksums, and
      installed timestamps.
- [ ] Confirm V1 through the expected current migration are present and
      successful.
- [ ] Compare checksums with the approved migration files.
- [ ] Confirm no repair, migrate, clean, DDL, or data update is run during this
      checklist.
- [ ] Record the most recent successful backup before any future migration.

Suggested read-only SQL, to be reviewed by the owner before execution:

    SELECT installed_rank, version, description, script, checksum, success, installed_on
    FROM flyway_schema_history
    ORDER BY installed_rank;

## E. Authentication and authorization

- [ ] Verify a normal Bearer-authenticated /api/v1/auth/me request without
      exposing the token.
- [ ] Verify unauthenticated access receives the expected 401 behavior.
- [ ] Verify an X-User-Id-only request is rejected in cloud/prod, if the owner
      approves a controlled negative test.
- [ ] Verify a role can access its own store workspace.
- [ ] Verify the same role is denied access to another store.
- [ ] Verify FRONTDESK printing/menu capability only if this is in the
      approved pilot scope.
- [ ] Verify Staff, Audit, Platform Admin restrictions remain enforced.

## F. Feature flags and pilot scope

- [ ] Record effective CORE_POS, PRINTING, KDS, ADMIN, ANALYTICS, PLATFORM,
      and DEVELOPER_TOOLS values.
- [ ] Open frontdesk and owner/admin pages with a test account.
- [ ] Verify /pickup and /kds routes show the feature-disabled behavior when
      KDS is false.
- [ ] Verify HOT_KITCHEN printing configuration is still available when KDS
      display is disabled.
- [ ] Record whether the deployed frontend route metadata matches backend
      feature settings.

## G. Printing mode and assignment evidence

- [ ] Record each pilot store's effective printing mode: REAL, MOCK,
      PAD_DIRECT, or DISABLED.
- [ ] Record module-to-printer assignments for GRAB,
      FRONTDESK_RECEIPT, and HOT_KITCHEN.
- [ ] Record printer IDs, names, endpoint reachability policy, enabled status,
      paper/encoding/font settings, with private IPs redacted in shared reports.
- [ ] Confirm cloud REAL mode is not attempting private LAN socket connections.
- [ ] For PAD_DIRECT, use only an already-existing, owner-approved test Job
      (Already-existing, Owner-approved Test Job). Inspect its existing
      PENDING/CLAIMED/PRINTING/terminal evidence without creating a job for this
      checklist. Do not initiate backend TCP output.
- [ ] Confirm Print Center displays failed/stale attention data without
      automatically reprinting.

## H. PAD device registration and worker state

- [ ] Record registered device IDs, store IDs, active/status values, app
      versions, platforms, and last_seen_at, redacting tokens.
- [ ] Confirm device tokens are not present in logs or browser storage.
- [ ] Record which installed APK is paired with each device.
- [ ] Record Android worker state: user enabled, running/recovering/stopped,
      lastPollAt, last error, stop reason, and pending kick state.
- [ ] Confirm user Stop persists disabled and a transient network error does not
      clear the user preference.
- [ ] Confirm lifecycle recovery only through an approved foreground behavior;
      do not assume an Android background service exists.
- [ ] For multi-printer routing, compare payload printer endpoint fields with
      the assignment rows and the Pad's local network reachability.

## I. PAD_DIRECT job state evidence

- [ ] For a named job/order, record status, execution mode, module, printer ID,
      retry count, claimed device, client attempt token presence (not value),
      claim expiry, created/updated/printed/failed timestamps, and error code.
- [ ] Record matching print_job_attempts rows and transport type.
- [ ] Compare existing database/log evidence for the expected transitions
      PENDING -> CLAIMED -> PRINTING -> PRINTED or FAILED. Do not initiate a
      claim, start-print, payload fetch, complete, fail, or release operation.
- [ ] Identify stale CLAIMED/PRINTING rows for operator review.
- [ ] Confirm FAILED jobs are not silently requeued by the evidence process.

Suggested read-only SQL, with store/order IDs supplied by the owner:

    SELECT id, order_id, store_id, module_code, execution_mode, status,
           printer_id, retry_count, max_retry_count, claimed_by_device_id,
           (client_attempt_token IS NOT NULL) AS has_client_attempt_token,
           claim_expires_at, created_at, updated_at, printed_at, failed_at,
           error_code
    FROM print_jobs
    WHERE store_id = :store_id
    ORDER BY created_at DESC
    LIMIT 100;

    SELECT print_job_id, printer_id, device_id, transport_type,
           (client_attempt_token IS NOT NULL) AS has_client_attempt_token,
           attempt_number, status, started_at,
           finished_at, error_code
    FROM print_job_attempts
    WHERE print_job_id = :print_job_id
    ORDER BY attempt_number;

## J. REST endpoint and DTO smoke evidence

- [ ] Record deployed route and response shape for auth/me, menu revision,
      menu catalog, idempotent submit, printing jobs, device registration,
      and PAD_DIRECT pending from existing approved evidence or static route
      inspection.
- [ ] Use only safe GET/HEAD or owner-approved non-mutating test endpoints.
- [ ] Do not submit an order, create a reprint, claim a Job, start printing,
      fetch a live print Payload, complete a Job, fail a Job, release a Job, or
      register a device during this checklist.
- [ ] Compare deployed JSON field names with controller DTOs. Never record raw
      JWTs, refresh tokens, device tokens, token hashes, Authorization headers,
      client_attempt_token values, passwords, or raw print payloads. Record
      token presence only.
- [ ] Record unexpected 400/401/403/409/5xx responses for a separate approved
      incident investigation.

## K. Database backup and restore readiness

- [ ] Record the last successful pg_dump -Fc backup timestamp and location.
- [ ] Verify backup file size and checksum without opening or copying secrets
      into the repository.
- [ ] Record whether restore has been rehearsed in an isolated staging database.
- [ ] Confirm production restore commands require an explicit backup file and
      confirmation.
- [ ] Do not execute restore, rollback, Flyway repair, or destructive SQL.

## L. Frontend and Android generated artifacts

- [ ] Record served frontend build-info, asset manifest hash, generated time,
      and offline database schema version if exposed.
- [ ] Compare served build metadata with the approved source commit/build log.
- [ ] For a bundled APK, record package name, versionName/versionCode, signing
      identity, embedded build-info and asset manifest hashes.
- [ ] Record WebView mode: local preview, bundled assets, or another approved
      origin.
- [ ] Confirm the installed APK is the one used for worker and pairing
      evidence; the repository asset folder alone is insufficient.

## M. Health and smoke evidence

- [ ] Record GET /api/v1/system/health response and timestamp.
- [ ] Record frontend HTTP status and TLS certificate/domain evidence.
- [ ] Record WebSocket endpoint reachability without starting a business
      subscription that mutates state.
- [ ] Verify login and workspace loading with a test account.
- [ ] Verify menu cache source/last updated indicator.
- [ ] Verify a previously approved non-production smoke order only in a
      designated test environment, not in production.
- [ ] Verify Print Center read-only display for current jobs and devices.
- [ ] Record all failures and environment limitations; do not mark them as
      repository defects without code evidence.

## Command classes

### 1. Passive Read-only Inspection

These inspect the checkout or local build metadata and do not contact
production:

    git status --short
    git branch --show-current
    git rev-parse HEAD
    git log --oneline -20
    git ls-files
    find docs deployment backend/src/main/resources/db/migration -type f -print
    rg -n "pattern" docs backend frontend restaurant-pad-app
    sed -n "start,endp" path
    git show --stat COMMIT
    docker compose -f deployment/cloud/docker-compose.yml config
    docker compose -f deployment/cloud/docker-compose.yml config --services
    bash -n deployment/cloud/*.sh
    sha256sum restaurant-pad-app/android/app/src/main/assets/web/build-info.json
    git diff --check

Compose config and shell syntax are local validation only. They do not prove
that the server uses the same files.

### 2. Owner-approved Active Verification

Do not run these during this repository audit. Run only in a separately
approved evidence session, with read-only credentials and redaction:

    ssh <approved-host> "docker compose ps"
    ssh <approved-host> "docker compose images"
    ssh <approved-host> "docker inspect <container>"
    ssh <approved-host> "docker logs --since ... <container>"
    curl -I https://<approved-domain>/
    curl -sS https://<approved-domain>/api/v1/system/health
    curl -sS https://<approved-domain>/api/v1/auth/me
    psql "<read-only-connection>" -c "SELECT ..."
    adb shell dumpsys package <approved-package>
    adb logcat -d -s RestaurantPadWorker:V chromium:W
    apkanalyzer manifest print <approved-apk>

Even GET requests can expose customer, auth, printer, or infrastructure data.
Negative auth tests and endpoint probes require owner approval as well.

### 3. Forbidden Mutating / Destructive Actions

Do not run any of the following as part of this checklist:

- git reset --hard, git clean, git checkout --, git restore, force push, or
  branch deletion.
- docker compose down -v, docker volume rm, docker system prune, container
  removal, image removal, or any command that recreates production services.
- docker compose up/build/pull or deployment scripts against production.
- Flyway migrate, repair, clean, schema changes, or application startup against
  production for verification.
- PostgreSQL DROP, TRUNCATE, DELETE, UPDATE, INSERT, ALTER, GRANT, or restore.
- Production POST/PUT/PATCH/DELETE requests, order submit, reprint, claim,
  start-print, live payload fetch, complete, fail, release, device register,
  or force release.
- adb install, uninstall, shell pm clear, data wipe, WebView/IndexedDB clear,
  preference deletion, or APK replacement on a pilot device.
- Clearing browser storage, local drafts, Outbox records, pairing credentials,
  or printer configuration to make a test pass.
- Running backup/restore scripts with a production target or unreviewed
  credentials.

## Evidence conclusion

The checklist is complete only when every unchecked item is labeled either
"not applicable", "not run by policy", or "runtime evidence pending". A local
build, repository test, Compose config, or generated asset may support a
repository classification, but cannot close a deployed-runtime checkbox.
