# Production V10 → V26 Exact-Artifact Release

This runbook defines the bounded Production upgrade path from the retained V10
runtime to the application artifact already accepted on isolated Staging at
V26. It does not authorize a release by itself; current Owner authorization and
`docs/governance/CURRENT_STATE.yml` remain required.

## Safety contract

- Promote the immutable backend/frontend image IDs currently running on
  accepted Staging; never rebuild or pull during Production promotion.
- Keep the Production Compose project `cloud`, PostgreSQL container and fixed
  state root unchanged.
- Create the fresh backup with `production-backup-rehearsal.sh` and validate its
  exact V10 ledger before any migration.
- Run `production-v10-v26-rehearsal.sh` against that backup. Its Docker network
  is internal, publishes no host ports, and its database volume and containers
  are run-owned and removed on exit. Host-side smoke may reach only the exact
  private frontend address after validating all three run-owned members and the
  internal network before any token, database or API access.
- The rehearsal starts the target V26 backend/frontend images, runs read/write
  smoke only on the clone, checks a canonical content fingerprint plus additive
  V11–V26 relationships, and exercises recovery by restoring the backup into a
  separately validated temporary database, proving a failed restore leaves the
  primary clone unchanged, then switching database names while the failed V26
  database remains quarantined until V10 application smoke passes. Write smoke rejects live Production and Staging DB
  identities before reading a credential, database row or API.
- The rehearsal also proves that the installed Production Pad identity remains
  `versionCode=2` / `0.2.0-offline-pr7`: the Android/WebView tree, worker DTOs,
  PAD_DIRECT service contract, API paths and required headers are unchanged;
  heartbeat persistence is additive and V26 introduces no minimum-version guard.
- `production-v26-exact-artifact-promote.sh` accepts only a digest-bound frozen
  RC with typed gate values (`PASS`, `VERIFIED`, and `ACCEPT`) and strict parsed
  evidence. It uses `--no-build --pull never`, starts and restarts the backend
  before reopening the frontend, verifies exact V26 Flyway history, preserves
  the DB container, and runs full read-only authenticated smoke through a
  run-owned loopback frontend. It permanently closes automatic database-restore
  authority before attempting the public edge; an edge-only failure therefore
  keeps V26 data and is retried with `--finalize-edge`, never by restoring V10.
  The authenticated read-only smoke is repeated through the public edge after
  that boundary; a failure cannot invoke database recovery.
- Production `.env` remains a Compose dotenv file and is never shell-sourced.
  The helpers require exactly one `DB_NAME`, `DB_USER`, and `JWT_SECRET`, extract
  their resolved values from the reviewed Compose model, and cross-check the DB
  identity against the exact running PostgreSQL container.
- Security parsers run with an empty environment and Python isolated mode; the
  tooling checkout must contain no tracked or untracked drift. Every required
  rehearsal marker carries the same random run ID, so evidence from different
  executions cannot be spliced into a PASS.
- The Production control checkout must have no tracked drift. Its untracked
  status is restricted to the exact fixed runtime paths already used for the
  ops lock, backups, bootstrap secret, database/Nginx state and retained legacy
  Store migration archives; any additional untracked path fails closed.
- Every Docker, Compose, restore and smoke subprocess is bounded. Temporary
  containers, volume and internal network are removed only after exact ID,
  label and mountpoint ownership checks; Docker query/timeout errors are
  failures, never proof of absence, and cleanup is part of the PASS contract.
- No real Store creation/activation, credential rotation, Printer binding, Pad
  binding, physical print, manual SQL, Flyway repair, volume prune or unrelated
  Production mutation belongs to this release.

## RC preparation

Keep prepared/frozen RC manifests and command evidence under the private
mode-0600 Production evidence directory. The manifest binds:

- accepted source SHA and current Staging backend/frontend image IDs;
- current Production and rollback image IDs;
- tooling SHA plus helper/override/checksum digests;
- the strict evidence parser and RC-bound automatic recovery helper;
- exact resolved Production Compose digest;
- fresh backup digest and V10 baseline;
- rehearsal evidence digest and all automated gate results;
- full V10 business-content and printing-topology fingerprints;
- Agent 6 result and final Production preflight result.

Do not place passwords, JWTs, device tokens, printer endpoints, raw DB content
or customer/order payloads in a manifest or evidence file.
Device last-seen/heartbeat timestamps are deliberately excluded from frozen
content identity; device ownership, status, token hash and printer topology
remain covered.

## Execution order

```text
prepared RC
-> fresh reviewed backup
-> existing isolated V10 restore check
-> full V10 -> V26 Production-shaped rehearsal
-> read/write clone smoke
-> old-app compatibility classification
-> failed-restore primary-preservation proof
-> validated temporary V10 DB switch + V10 artifact recovery proof
-> Agent 6 release review
-> reviewed `--snapshot` of Compose/business-content/printing fingerprints
-> prepared-RC Production validation
-> frozen RC
-> exact-image Production execute with pre-edge same-image restart/no-pending proof
-> private target-frontend authenticated smoke
-> close automatic database-restore authority
-> public-edge switch, repeated read-only smoke and observation
-> exact temporary-resource cleanup
```

The concrete helpers require absolute manifest/backup paths and explicit
SHA-256 values. Run `--help` for their argument shape.

Every remote invocation must have an outer lifecycle timeout sized for the
phase (for example, backup/rehearsal up to 30 minutes and promotion up to 10
minutes) plus bounded SSH keepalive/connect settings. Do not background these
helpers, pipe unbounded producers into them, or leave a tunnel/session owned by
the release run.

## Recovery

If the previous V10 application cannot run safely on V26 schema, classify it
as `OLD_PRODUCTION_APP_ON_V26_SCHEMA=NOT_SUPPORTED`. The rehearsed recovery is
then the fresh predeploy V10 backup plus retained V10 backend/frontend images.
After a Production mutation starts and while the public edge remains closed, a
helper failure automatically invokes the digest-bound reviewed recovery helper.
Recovery first proves that the database is an exact V10–V26 Flyway prefix and
that business/printing content still matches the frozen predeploy fingerprint.
It restores V10 into a new database, validates ledger and fingerprints, then
switches names without dropping the failed primary. The failed database is kept
until the V10 backend/frontend and legacy read smoke pass. A restore failure
removes only its exact temporary database, leaving the primary retryable.
Before restore and again immediately before the name switch, recovery enumerates
the complete `cloud` Compose project and rejects unknown services, duplicate
resources, non-canonical DB identity, or ambiguous/mismatched app containers.
It also requires Nginx to be absent or the same exact stopped V10 rollback
container; a running rollback frontend or any V26 target frontend proves a
public-edge boundary and makes database recovery fail closed.

Once private target smoke passes, database-restore authority is closed before
the public Nginx edge is started. Any later edge failure keeps V26 and requires
the bounded `--finalize-edge` path (or an Owner recovery decision); automatic V10
restore is forbidden because public writes may already exist. Unexplained
post-freeze writes always fail closed. Never improvise SQL or edit Flyway history.

After success, retain the backup and sanitized release evidence according to
policy. Remove only exact run-owned rehearsal containers, network, volume and
temporary files. Verify no run-owned SSH/background process remains.
