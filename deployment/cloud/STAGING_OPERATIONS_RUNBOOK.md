# Staging Operations Runbook

Status: `STG-006_PREPARATION_ONLY_BLOCKED_ON_STG-003_STG-005`

This runbook documents a future owner-approved operating procedure for the
isolated Staging project only. It does not authorize production changes and it
does not make Staging runnable by itself.

## Immutable scope

- Project: `restaurant-pos-staging` only.
- Root: `/srv/restaurant-pos/staging` only.
- Network: loopback `127.0.0.1:18080` only.
- Data: synthetic or empty only.
- Printing: `DISABLED`; no printer endpoint is permitted.
- Release identity: a full approved Git SHA and SHA-specific backend and
  frontend image tags.

The scripts in this package are validation, inventory, disk metadata, and
static-image-comparison tools. They never start, stop, restart, build, pull,
remove, back up, restore, or query a database.

## Owner checkpoints

1. Approve the exact SHA, the same-host Staging root, and the maintenance
   window before any state-changing Staging operation.
2. Approve explicit disk thresholds. No default headroom values are assumed.
3. Review a successful `--validate` result, then separately approve any future
   deployment action supplied by STG-004.
4. Review loopback health, image identity, and Flyway evidence after a future
   deployment. Static image comparison alone cannot prove schema compatibility.
5. Approve every stop, restart, rollback, retention deletion, backup, and
   restore action independently. Approval to start is not approval to stop.

## Read-only command plans

All commands require exact identity arguments. Values below are placeholders
and must be replaced only in an owner-approved environment. Do not put secrets
in terminal history, shared evidence, or source control.

```bash
deployment/cloud/staging-operations.sh --validate \
  --env-file /srv/restaurant-pos/staging/config/.env.staging \
  --root /srv/restaurant-pos/staging \
  --commit <full-40-character-sha> \
  --compose-file /srv/restaurant-pos/staging/releases/<full-40-character-sha>/deployment/cloud/docker-compose.staging.yml \
  --backend-image restaurant-pos-backend:staging-<full-40-character-sha> \
  --frontend-image restaurant-pos-frontend:staging-<full-40-character-sha>
```

`--dry-run` is an alias for `--validate`. Every operation first runs the exact
release's existing `staging-deploy.sh --validate` guard, then uses only
read-only Compose/image metadata operations. This rechecks the Git release,
PostgreSQL path, loopback binding, database identity, cloud profile, printing
disablement, resource limits, log rotation, and private resolved Compose
configuration. The resolved Compose file is never printed.

```bash
deployment/cloud/staging-operations.sh --inventory <same identity options>
deployment/cloud/staging-operations.sh --disk-check \
  --min-free-bytes <owner-approved-bytes> \
  --max-used-percent <owner-approved-percent> \
  <same identity options>
deployment/cloud/staging-operations.sh --image-compatibility \
  --previous-sha <full-40-character-previous-sha> \
  <same identity options>
```

Inventory is project-scoped and uses only `docker compose ps -q` followed by a
formatted `docker inspect`. It does not read container environment variables,
labels, commands, mounts, or application data. Disk checking reads filesystem
metadata for the Staging root only. Backup metadata reports a SHA-256 of each
basename rather than the filename itself and never hashes backup contents.
Image compatibility verifies that every historical migration Git blob remains
unchanged and that SHA-specific images exist; it must report
`STATIC_CHECK_ONLY_RUNTIME_PENDING` until a separately approved runtime/schema
compatibility rehearsal exists.

## Evidence and retention

Capture script stdout into a private, owner-created evidence directory. The
scripts do not create evidence directories or write evidence files. A future
private convention is:

```text
/srv/restaurant-pos/staging/evidence/<UTC-timestamp>-<full-sha>/
```

Record the approved SHA, shortened image IDs, command result, disk thresholds,
and any owner approval reference. Do not record environment files, credentials,
tokens, resolved Compose output, customer data, or print payloads.

Retention is a manual checklist only. Before deleting any Staging evidence,
images, or backups, require a separate Owner decision and preserve the release
identity required for any pending investigation. This package provides no
automatic retention deletion.

## Future owner-only action templates

The following are documentation templates only. They are not invoked by the
scripts and require explicit owner approval after STG-004 preflight succeeds.

```bash
# OWNER_ACTION_REQUIRED: stop only the isolated Staging project.
docker --context default compose --project-name restaurant-pos-staging \
  --env-file /srv/restaurant-pos/staging/config/.env.staging \
  -f /srv/restaurant-pos/staging/releases/<full-sha>/deployment/cloud/docker-compose.staging.yml \
  stop nginx backend db
```

```bash
# OWNER_ACTION_REQUIRED: future restart sequence after a separate approved plan.
# Start db, verify its health, then start backend and nginx. Do not use a
# destructive Compose command and do not target the production project.
```

Never use `down`, volume deletion, `rm`, `prune`, `pull`, database cleanup, or
an automatic rollback as part of Staging operations.

## Synthetic rebuild boundary

Synthetic fixture rebuild is `BLOCKED_ON_STG-005_FIXTURE_CONTRACT`. A future
implementation must use only fixture IDs recorded in an approved manifest,
inside a transaction and in reviewed foreign-key order. It must not use broad
table deletion, truncation, schema reset, data-directory removal, or production
data. Recreated synthetic Stores must remain inactive with printing disabled.
