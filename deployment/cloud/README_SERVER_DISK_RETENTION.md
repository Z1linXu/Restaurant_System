# Shared-server disk retention

The Owner authorized this policy and its safe initial cleanup on 2026-10-31.
It covers the shared `restaurant-prod` host without deploying/restarting the
application or Docker. The actual builder is `default`, Docker driver, local
Unix socket. Production, Staging and template-test share that builder.

## Installed policy

Reviewed scripts are installed, root-owned, at
`/usr/local/lib/restaurant-disk-retention`. The installed file hashes and live
verification are recorded in `docs/governance/runtime/disk-cleanup-20261031/`.
The server control checkout and immutable application releases were not edited.

| Domain | Policy | Execution |
|---|---|---|
| Build cache | Private, unused, last used more than 720 hours ago; exclude internal/frontend records | Weekly `server-buildkit-retention.py --execute`; default invocation prints candidates/retained IDs only |
| Staging releases | Current runtime, legacy env target, latest five successful deployments, newest five checkouts, all rollback/mount/process/static/download references | Weekly `staging-retention-run.sh --execute`, using the existing digest-bound `git worktree remove` helper; uncertain/dirty/unsafe/data-bearing trees are retained |
| systemd journal | Only ARCHIVED files with both last-entry timestamp and mtime older than 30 days | Daily reviewed-plan execution by `run-journal-retention.sh`; active/open/held/unverified files remain |
| Maintenance-job logs | Daily rotation, 30 archives, `maxage 30`, compression | `/etc/logrotate.d/restaurant-disk-retention`; no daemon reload required |
| Docker/application stdout logs | `local`, 10m × 3 per service | Prepared only; existing containers are unchanged |

The cron file is `/etc/cron.d/restaurant-disk-retention`. Host time is UTC+08:00:
cache Sunday 13:15 (05:15 UTC); journal daily 13:45 (05:45 UTC); releases Sunday
14:15 (06:15 UTC). Jobs skip/fail closed on active deployment/builds or locks.
Outputs go to `/var/log/restaurant-disk-retention` (root journal job) and its
`user/` subdirectory (Ubuntu jobs). These directories have separate ownership
so the privileged journal job does not append into an Ubuntu-writable parent.

`/srv/restaurant-pos/staging/config/disk-retention-enabled` records the standing
release-policy opt-in. Removing it disables automatic release cleanup; it does
not affect application services. Disable a whole domain by commenting its
specific cron entry. No service restart is needed for cron changes.

Cache cleanup uses only:

```bash
docker --context default buildx prune --builder default \
  --filter until=720h --force --verbose
```

There is no `--all`, image/container/network/volume prune, disk-pressure override,
or filesystem removal of Docker storage. Age-based candidates are upper bounds;
Engine references can retain some records. Recent caches are not sacrificed to
hit a space target. See [Docker's buildx prune reference](https://docs.docker.com/reference/cli/docker/buildx/prune/).
The older `staging-buildkit-cache-hygiene.sh` is a legacy guarded helper with a
known inaccurate preview/overstrict eligibility check; it is **not** used by the
new shared-host policy. Its old documentation is not permission to use a
seven-day cleanup in this thirty-day policy.

## Release safeguards and current skip

The legacy Staging env file still points to `7b70a4f...`; the actual Staging
backend/frontend run `028d497...`. Runtime discovery protects both. The
`current`, `previous` and `rollback` symlinks are absent, so none is invented.
Container working directories/config paths, bind mounts (including ancestor
mounts), symlinks, process cwd/open descriptors, operational scripts/configs,
static serving/download paths and explicit rollback models supplement the
existing state/evidence protection. Failed/incomplete results are not counted
as successful deploys; fewer than five known successes retains all releases.

Unknown binary references retain all releases. Known PostgreSQL custom-format
backups outside releases are retained as data, without parsing them as scripts.
A release containing backups, uploads, PostgreSQL paths, dumps, keys, the
Windows pilot package directory or its known ZIP is never a deletion candidate.
`find` failures, metadata drift and unverified worktrees fail closed. No `--force`
worktree removal or recursive release deletion is used.

At initial execution there were **zero safe release-deletion candidates**:
29/72 directories had unsafe legacy metadata and opaque historical acceptance
helpers left references unresolved; all 72 releases also carried Windows
PostgreSQL dumps. These were retained, not normalized or deleted. Resolving
those historical references/backup purposes is a follow-up before additional
release reclamation.

## Separate Windows distribution and future deployment

The Windows pilot README describes a Windows/LAN distribution that includes a
DB dump. The web containers serve static files from their images, not these
release packages; no active Windows download route was found. JAR and ZIP were
preserved with SHA-256 verification under
`/srv/restaurant-pos/artifacts/windows-pilot/preserved-20261031/`, mode 0700,
with a manifest. Original copies and all embedded backups remain untouched.
No public download path was changed.

Prepared `staging-release-rotation.sh` uses sparse checkout to omit the extracted
Windows package and its ZIP from **new** web worktrees. Git HEAD and clean
status remain intact. It keeps Windows documentation/scripts. No current
checkout was changed to sparse mode. Other ad-hoc archive-based release paths
must also omit Windows distribution artifacts when next revised; they were not
rewritten by this maintenance task.

Prepared `staging-deploy.sh` shares the maintenance lock across build/up. Before
releasing it, the new success hook verifies exact backend/frontend tag-to-image
identity and HTTP health, then writes a private success receipt under
`state/release-success/`. It does not label validation-only or failed builds
successful. The existing pre-build retention plan remains read-only; scheduled
cleanup performs the separately locked/digest-checked execution.

## Pending logging/deployment changes

`/usr/local/lib/restaurant-disk-retention/pending/` contains the modified deploy,
rotation and success-hook sources, production Compose logging configuration,
Docker logging JSON fragment and journald rotation fragment. They are prepared,
**not activated**. The repository PR carries the same future-deploy changes.

- Apply Compose `local` 10m × 3 logging at the next authorized container
  recreation. Explicit logging in legacy promotion models must be updated too;
  daemon defaults do not replace a container's existing explicit LogConfig.
- Merge the Docker JSON fragment into existing daemon configuration only during
  a later approved Docker maintenance window. Do not overwrite unrelated keys.
- The journald fragment rotates files at one day; activation is deferred until
  later journald maintenance. Strict expiry stays in the last-entry-aware job.
- Existing managed Docker log files, including the approximately 461 MB largest
  active container log directory, were not truncated or manually removed.
  The application has no separately configured file-log sink; its stdout remains
  under the container logging driver. Deployment/acceptance evidence logs and
  package/security/system rotated logs retain their existing policies.

Optional exact-path journal holds can be installed as a root-owned,
non-group/other-writable `/etc/restaurant-system/log-retention-holds.json`:

```json
{"paths": ["/var/log/journal/<machine-id>/<archived-file>.journal"]}
```

A new/changed hold invalidates a reviewed deletion plan. A broad
`journalctl --vacuum-time=30d` is deliberately not used: the installed systemd
vacuum implementation dates archives from their head/filename, whereas this
policy checks the final record and protects a file spanning the cutoff.
See [systemd v249 vacuum implementation](https://github.com/systemd/systemd/blob/v249/src/libsystemd/sd-journal/journal-vacuum.c).

## Validation

```bash
python3 deployment/cloud/tests/test_server_disk_retention.py
bash deployment/cloud/tests/test_staging_infrastructure_hygiene.sh
bash deployment/cloud/tests/test_staging_guard.sh
bash deployment/cloud/tests/test_staging_release_rotation.sh
bash deployment/cloud/tests/test_staging_deploy_cli_state.sh
git diff --check
```

Tests use temporary files/fake CLIs; the 15 Python tests cover exact age,
shared/in-use protection, invalid metadata, success ordering, sparse checkout,
and verified journal deletion/open-file/hold/identity/recent-tail guards.
The installed bundle was checked with read-only plans, hashes, logrotate debug
validation and final runtime continuity/HTTP health snapshots. No build,
application deployment, direct SQL, printing or service restart is part of
this task.
