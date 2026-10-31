# Server system-disk audit — 2026-10-31

## Authorized cleanup and installed retention — 2026-10-31

The Owner subsequently authorized safe cleanup and retention implementation.
This section supersedes the earlier inspection-only result; the original
05:11–05:14 UTC audit is retained below as historical evidence. Fresh cleanup
baseline is **05:56:25 UTC**, final installed-policy/runtime snapshot is
**06:12:03 UTC** (01:56–02:12 America/Toronto). GB here remains decimal.

### Actual result

| Metric | Before | After |
|---|---:|---:|
| Root used | 49,327,218,688 B / **49.33 GB** | 37,996,285,952 B / **38.00 GB** |
| Root `df` use | **82%** | **63%** |
| Available | 11,261,980,672 B / 11.26 GB | 22,592,913,408 B / 22.59 GB |
| Build cache | 329 records / 17.22 GB | 209 records / 8.29 GB |
| Images | 134 | 134; complete ID/tag/layer inventory unchanged |
| Containers | 12 | 12; IDs/images/start times/restarts/mounts/status/log config unchanged |
| Production / Staging health | HTTP 200 / 200 | HTTP 200 / 200 |

**Net root-space reduction: 11,330,932,736 bytes / 11.33 GB.**
This accounts for the new independent Windows distribution copy (~104 MB),
maintenance files and ordinary live writes. Logical cache bytes and filesystem
space are different measurements; do not add parent/child/Docker totals.

No application or Docker restart, rebuild, deployment, direct SQL or business-data write,
volume/image/container/network deletion, migration or printing occurred.
The existing server control checkout and application release files were not
modified. Only separate maintenance files/jobs and protected distribution
copies were installed on the host.

### 1. Build cache: executed

The actual builder was confirmed as local `default`, Docker driver, BuildKit
0.31.1/Engine29.6.1. Latest template-test builds had completed; no active build
history or deploy/build clients were found. Existing Staging hygiene and action
locks plus the shared maintenance lock were held. The candidate list contained
131 records / 9,699,338,412 bytes with native `InUse=false`, `Shared=false`, exact
last-use older than30 days, excluding internal/frontend types. All other cache
records and every image/container were listed as protected.

Executed once, without wider storage/free-space flags or `--all`:

```bash
docker --context default buildx prune --builder default \
  --filter until=720h --force --verbose
```

Actual deletion: **120 records / 8,929,223,292 logical bytes (8.93 GB)**.
Before/after ID comparison proves every removed record belonged to the reviewed
age/private/unused candidate set. The same command retained 11 candidate records
/770,115,120 bytes; no broader deletion or forced retry was attempted. All134
images, including rollback images, and all12 containers were preserved.

Candidate/retained IDs are in `before.json`, the execution command/record log is
`cache-execute.txt`, and exact deletion assertions are in `cache-result.json`.

### 2. Staging releases: zero safe deletion candidates

All **72 releases / about9.07 GB were retained**. The original root retains
about14.90 GB of release/tooling data; Production-tools and unrelated roots
were outside the approved Staging-directory deletion domain.

- `current`, `previous`, `rollback` symlinks do not exist; no new pointer was
  invented. The legacy env still identifies `7b70a4f...` and previous verified
  `d2ac3e6...`; actual runtime is `028d497...`. Both legacy and runtime identities
  were protected rather than rewriting the env to fit the cleanup.
- Container working/config directories and mounts, ancestor mounts, active
  process cwd/fds, operational scripts/configuration, Nginx static/download
  paths and explicit rollback models were checked. Static web files are served
  from images; no active Windows-package download route was found.
- 29 directories fail existing strict legacy metadata/worktree checks and were
  kept without chmod, force removal or normalization. Historical opaque Python/
  Java acceptance artifacts leave references unresolved; the existing fail-closed
  rule retains all other worktrees. Those artifacts themselves remain untouched.
- **All72 Windows pilot packages include a PostgreSQL dump**. The Windows README
  also describes package backups. These are protected data-bearing distribution
  packages; no JAR/ZIP extension sweep or recursive release deletion was used.

The latest five healthy Staging deployments were explicitly protected (healthy
deployment evidence is not a claim of manual business acceptance):

| SHA | Deployment evidence |
|---|---|
| `028d4971740889a0ac0d784a43497bc3e5b54b3d` | `dining-table-soft-delete-.../result.json`, health PASS; current runtime |
| `f838e48de1f20689253716bd1910b952fcbc00d3` | `split-label-mode-.../result.json`, health PASS |
| `e44d7a5fe534a50c675574e589751cce41615cc5` | `dine-in-top-feed-.../result.json`, health PASS |
| `0d2e244c3d1565a06bda8fa269e784af58e875e2` | `dine-in-headers-.../result.json`, health PASS |
| `56185edcdd9f4496cc6073e478546a5a39ba77d9` | `uber-4483-backend-.../result.json`, health PASS; record mtime used where deployed_at absent |

The complete retained list, modes and dump-presence flags are in
`release-inventory.json`; explicit runtime/rollback protections are in
`runtime-protected.txt`; the final installed policy's candidate/protected list
is `installed-release-policy.plan` (zero ELIGIBLE, PASS). No attempt was made to
remove legacy binary protections merely to reach an expected reclaim number.

### 3. Historical logs: executed only for verified journal archives

Candidate list: **50 archived journal files /2,508,345,344 allocated bytes**.
Each had State ARCHIVED, final-entry timestamp and mtime older than30 days,
root-owned regular single-link identity, no open process descriptor and no
explicit retention hold. Twenty active/recent/unverified files were retained.
Execution rechecked the exact plan digest, fresh eligibility, inode/size/mtime,
last-entry timestamp, open-file status and hold digest immediately before unlink.
All50 reviewed files were removed; actual allocated bytes total **2.51 GB**.
No active journal was rotated, truncated or cleared; journald was not restarted.

A broad `journalctl --vacuum-time=30d` was not used: the installed systemd vacuum
implementation uses head/filename age and can include a file spanning the30-day
boundary. The strict policy uses the final record instead.

Production's three containers currently use unbounded `json-file`; Staging
already uses bounded local logging, and template-test uses bounded json-file.
Managed Docker log files were not manually removed. No separate application
file-log sink is configured; application stdout remains Docker-managed.
System security/package/installation archives keep their existing policies.
Deployment/acceptance/evidence logs were retained. Sanitized checks are in
`log-inventory.json`; journal candidates/retentions and deletion results are in
`journals.plan.json` and `journals-result.json`.

### 4. Installed prevention and prepared changes

Active now, without application/daemon restart:

- Root-owned reviewed maintenance bundle at
  `/usr/local/lib/restaurant-disk-retention/` and standing release opt-in at
  `/srv/restaurant-pos/staging/config/disk-retention-enabled`.
- `/etc/cron.d/restaurant-disk-retention`: weekly30-day cache cleanup, daily
  last-entry-aware journal retention, weekly guarded release retention. Host
  UTC+08 schedules correspond to 05:15/05:45/06:15 UTC. The current release job
  safely retains all72 releases while unknown references/backup payloads remain.
- Maintenance-job log rotation under
  `/etc/logrotate.d/restaurant-disk-retention`: daily,30 archives, maxage30,
  compression; root and Ubuntu log ownership separated. Debug validation PASS.
- Separate Windows JAR/ZIP copy at
  `/srv/restaurant-pos/artifacts/windows-pilot/preserved-20261031/`, private
  permissions and matching SHA-256 manifest. Original packages/backups and
  download paths were not changed.

Prepared only, in the code branch and installed `pending/` directory:

- New web worktrees use sparse checkout to exclude Windows package and ZIP;
  existing checkout identities remain intact. Ad-hoc archive-based deploy paths
  need equivalent exclusion when next revised; they were not rewritten now.
- Canonical deployment shares maintenance locking and records a successful
  deployment only after exact backend/frontend image identity plus HTTP health,
  while still holding the deployment lock. Latest-five success protection also
  retains all if success history is incomplete.
- Production Compose logging defaults to local10m×3 on next authorized container
  recreation. A Docker defaults fragment and one-day journald rotation fragment
  are prepared, not applied to active daemon/container configuration. Existing
  explicit LogConfig in legacy promotion models also needs updating during that
  later rollout.

Policy files/hashes and installer results: `installed-policy-manifest.json`,
`policy-install.json`. Installed read-only cache/release plans passed. The old
seven-day cache helper is not used by this policy. Operational details and safe
activation are in [the retention runbook](../../../deployment/cloud/README_SERVER_DISK_RETENTION.md).

### Validation, review and remaining work

- 15 meaningful Python tests PASS, including last-entry/open-file/hold/identity
 deletion protection, exact cache age and sparse-checkout identity.
- Existing infrastructure-hygiene, Staging guard, release-rotation and private
 Docker-CLI-state shell suites PASS; shell/Python syntax and diff checks PASS.
- Independent Agent6 review: ACCEPT, no remaining blocking findings. Review
 required by the applicable current repository/server AGENTS.md.
- `after.json` and `result.json` prove unchanged container/image inventories and
 both health endpoints HTTP200. This is operational continuity, not order/print
 business acceptance. No application/Staging release acceptance was triggered.

Remaining work is bounded: resolve historical release-reference/data-package
retention before deleting old releases; activate prepared Docker/container and
journald configuration only in a later authorized maintenance/release; apply
Windows exclusions to noncanonical archive-based deployments when next revised.
The retained770 MB cache candidates were not forced out. No goal to reduce the
system disk to10 GB was used to expand the approved deletion scope.

Implementation branch: `codex/server-disk-retention`, based on fresh
`origin/main=80e888fcdad74e98326fcbeaa5df14bc0d9baed6`. The Owner's original dirty
workspace was preserved; this audit and evidence were synchronized back there.
No application merge or rollout is implied by this branch.

Execution Time Breakdown (estimated, stages overlap; first fresh host check
05:54 UTC, installed-policy snapshot06:12 UTC): Investigation/Planning ~3min;
Implementation/cleanup ~9min; Tests ~2min; Agent6 review ~3min; Evidence/Governance
~3min. Staging Deploy and application Staging Acceptance: Not required/not run.
Total host execution/evidence interval about18min; PR/document finalization is
additional. The current product Phase/Package remains unchanged.

Evidence directory: [disk-cleanup-20261031](disk-cleanup-20261031/).

---

## Original read-only inspection (retained)

Read-only investigation requested by the owner. Observations were collected
through the existing `restaurant-prod` SSH alias from 05:11–05:14 UTC
(01:11–01:14 America/Toronto). No cleanup, deployment, configuration change,
container lifecycle action, or database query was performed by this task.
Existing local work was preserved. Only this report and
`SYSTEM_DOCUMENTATION.md` were edited locally.

All GB below are decimal (10^9 bytes). `du` measurements are allocated bytes;
Docker cache/image measurements are logical accounting and overlap with the
filesystem measurements. Parent and child directory sizes must not be added.
The server remained active during collection; these are sequential observations,
not an atomic snapshot. A separate template-test deployment was already starting
during the initial observation, and release-directory totals changed during
collection. It was not started or changed by this audit.

## Disk usage and largest directories

Initial `df -B1 -T /`:

```text
/dev/vda2 ext4
total      63,290,032,128 bytes
used       49,001,807,872 bytes
available  11,587,391,488 bytes
use        81%
```

Final `df -B1 /` at 05:14:24 UTC reported used 49,007,939,584 bytes and
available 11,581,259,776 bytes, still 81%. No space was reclaimed by this task.
The initial root `du -x -B1 --max-depth=1 /` total was 48,988,413,952 bytes,
close to `df` used. The visible directory contents account for the usage;
there is no large unexplained `df`/`du` gap in these observations.

| Directory | Allocated bytes | GB | Contents / explanation |
|---|---:|---:|---|
| `/var/lib/containerd` | 22,495,141,888 | 22.50 | Docker image/content storage and build/runtime snapshots |
| `/srv/restaurant-pos` | 14,896,697,344 | 14.90 | Release checkouts, tools, Git mirrors, artifacts, state and evidence |
| `/usr` | 4,652,134,400 | 4.65 | Installed operating-system programs and libraries |
| `/var/log` | 3,531,681,792 | 3.53 | Primarily systemd journals |
| `/var/lib/docker` | 676,663,296 | 0.68 | Container logs, BuildKit metadata and volumes; additional to containerd |
| `/home` | 529,195,008 | 0.53 | Production control checkout, database and backups |
| `/tmp` | 315,772,928 | 0.32 | Temporary files; not a primary consumer |

These selected rows are not an exhaustive partition of the root filesystem.
The system disk stores much more than the deployed application binary.

## 1. Build and image history retained in Docker

The actual Engine is 29.6.1, API 1.55, overlayfs/containerd storage, with one
default Docker builder running BuildKit 0.31.1. `docker system df` and read-only
`GET /version`, `GET /v1.55/system/df` reported:

| Object | Count | Docker size | Additional observation |
|---|---:|---:|---|
| Build cache | 316 | 17,106,837,225 bytes | 150 private unused records total 10,502,868,134 bytes |
| Images | 130 | 9,286,765,987 bytes | 8 images referenced by containers; CLI reports 3.556 GB reclaimable |
| Containers | 12 | About 4.596 MB writable layers | All running at initial inspection |
| Local volumes | 3 | About 47.74 MB | Production/Staging databases use bind mounts and are counted separately |

**Do not add cache size, image size and `/var/lib/containerd` size.** Shared
layers and Docker logical accounting overlap. Image reclaimability alone also
does not establish that an image is unnecessary for rollback or other tooling.

Build-cache categories, inferred from the recorded build-step descriptions:

| Step | Records | Logical bytes |
|---|---:|---:|
| Base image layers | 93 | 6,349,399,573 |
| `npm ci` dependency installation | 17 | 4,384,366,592 |
| COPY layers | 127 | 3,963,067,564 |
| Maven dependency installation | 6 | 1,474,375,680 |
| Maven package | 9 | 680,660,992 |
| Frontend build | 17 | 71,000,064 |
| Source context | 7 | 60,104,704 |
| Other | 40 | 123,862,056 |

Private unused cache by exact last-use timestamp at 05:13:59 UTC:

| Last use | Records | Bytes |
|---|---:|---:|
| Under 7 days | 9 | 60,163,066 |
| 7–14 days | 3 | 329,781,248 |
| 14–30 days | 7 | 413,585,408 |
| Over 30 days | 131 | 9,699,338,412 |

No records in this private-unused set had an unknown last-use timestamp.
These are accounting candidates, not an executed deletion plan or a guarantee
of the number of filesystem bytes a future prune would free.

The actual Staging release frontend Dockerfile still sets
`VITE_APP_BUILD_VERSION` through ARG/ENV **before** `RUN npm ci`. A changed
release version changes that RUN environment even when package-lock is unchanged,
which prevents reuse of the dependency-installation layer for that input.
No Dockerfile was changed or image built to test an optimization in this audit.

`docker buildx inspect default` confirms GC is configured: a general 1440-hour
(60-day) keep duration, 5.588 GiB reserved, 44.7 GiB maximum used space, and
11.18 GiB minimum free space, plus other rules. This is not a small fixed cache
budget and does not ensure that this server stays near 10 GB total disk usage.

One historical backend image,
`restaurant-pos-backend:staging-8eea8d47c9fe7906563d051196c703da7c14e198`,
has Docker logical size 1,570,257,456 bytes. Its actual image history contains
**11 successive application-JAR COPY layers**, each about 57 MB, targeting
`/app/app.jar`. Replacing a file in a later image layer leaves the earlier image
layers stored. This is direct evidence of accumulated application layers in
that historical image, not a claim that every current image has this issue.
Current Production/Staging backend image `bf09f83635d5` has one application-JAR
COPY layer, about 57.7 MB.

## 2. Release checkouts repeatedly include Windows pilot packages

| Path / item | Observed size / count |
|---|---:|
| `/srv/restaurant-pos/staging` | 10,687,225,856 bytes |
| Staging `releases` | 9,065,836,544 bytes; 72 release directories |
| Windows pilot directories inside those 72 releases | **7,588,233,216 allocated bytes** |
| Packaged backend JARs in those releases | 72 files; 3,951,644,672 allocated bytes |
| `/srv/restaurant-pos/production-tools` | 3,289,915,392 bytes at the earlier observation |
| Production-tools `releases` | 1,474,998,272 bytes earlier; 1,589,661,696 bytes in the later scan; 11 directories later |
| Production-tools `repository.git` | 961,642,496 bytes |
| Production-tools `evidence` | 853,270,528 bytes |
| `/srv/restaurant-pos/production` | 672,862,208 bytes |

The 72 Windows pilot directories alone account for about **84% of Staging's
release-directory size**. They include both the extracted package and its ZIP.
For example, release `028d4971740889a0ac0d784a43497bc3e5b54b3d` has a
105,390,080-byte `deployment/windows-pilot` directory, including a
49,454,860-byte `restaurant-pos-windows-pilot.zip` and a packaged backend JAR
of 54,879,638 bytes. Retaining many full releases repeatedly retains these
packaging outputs even though the web runtime uses Docker images.
This audit did not checksum all copies or claim they are all byte-identical.

No release was classified as safe to delete. Active mounts, Compose working
directories, rollback dependencies, artifacts and evidence must be checked
before a separate cleanup. Production and Staging database containers still
reference older checkout paths in their Compose metadata.

## 3. Logs and actual database size

Systemd journal allocation is 3,355,668,480 bytes (the CLI displays 3.1G in
binary units). `/var/log` totals about 3.53 GB. Docker's container directory
totals 487,866,368 bytes, already included in `/var/lib/docker`; the largest
single container directory is about 461 MB. Log contents were not read.

The production PostgreSQL bind-mount directory is only **222,949,376 bytes**.
Production backup directory size is **48,889,856 bytes**. Staging's entire
state directory is about 130 MB. The template-test volume is about 54 MB on
disk. These observations do not support business-data growth as the reason
for roughly 49 GB of system-disk usage. No database contents were queried.

## Result and possible follow-up

The main causes are retained Docker build/image history, many release copies
carrying Windows packaging output, and accumulated logs. They account for the
owner's observation without requiring an unusually large live application or
database.

A separate cleanup can first evaluate the approximately 10.50 GB of private
unused build cache, then release retention with current/rollback/mounted paths
protected, and a bounded journal/container-log retention policy. Future release
packaging can exclude unrelated Windows bundles, and future build changes can
move version-specific inputs after dependency installation and avoid layering
new JARs repeatedly onto previous application images. No such changes were
made or authorized by this inspection request; reclaim estimates are not
guaranteed filesystem savings.

At the final check, both Production and private Staging
`/api/v1/system/health` returned HTTP 200. No order workflow, printing or
business acceptance was exercised. All audit SSH commands completed.

To reproduce the high-level read-only checks:

```bash
ssh restaurant-prod 'df -B1 -T /; docker system df; sudo -n journalctl --disk-usage'
```

Directory evidence was collected with `du -x -B1` at the paths above; image
layer evidence used `docker history --no-trunc` and cache metadata came from
read-only Engine API calls. Historical September 30 reports remain unchanged.
