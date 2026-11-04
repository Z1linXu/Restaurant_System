# Shared-host disk review and pending logging

PRIMARY_REPAIR_GOAL: Produce an exact, conservative disk-retention review and
locally verified logging candidates without changing the shared server.
EXPECTED_REPAIR_CLASS: MEDIUM. This is a read-only companion to the existing
retention policy, not a replacement deployment or cleanup system.

## Scope and defaults

`scripts/disk-governance/collect_metadata.py` has only read operations. It uses
the existing local Docker Unix socket and GET metadata APIs, filters container
inspect output, stats direct Docker log files and fixed release/package paths,
and reads journal disk usage. It does not output environment values, arbitrary
labels, build descriptions, log contents, database dumps, or business files.
It never recursively scans the filesystem, changes permissions, creates locks,
invokes a database, or installs anything. Fixed operational policy/code files
are bounded to 1 MiB and hashed to verify which policy is actually installed.

`plan.py` consumes an already captured snapshot offline. It has no execute
option, cleanup subprocess, file removal, installation, timer, or server
connection. Unknown or active deployment/maintenance observations retain every
cache. Container inventory inconsistency fails the review. Missing/unsafe
individual metadata produces retention reasons, not optimistic eligibility.

No repository Compose file, daemon default, live container, server cron job or
installed retention script is changed by this package. Owner authorization for
local development does not activate any pending setting.

## Retention decisions

| Domain | Rule in this companion | Recovery boundary |
| --- | --- | --- |
| Running images | Retain exact image IDs referenced by every running container | Preserve the current immutable application and database images |
| Paused Staging/Test | Retain images of **all stopped containers**, their container IDs, mount sources and volumes | Never rebuild or remove their restoration resources |
| Production rollback | Retain exact IDs explicitly supplied from the freshly verified private recovery model | `policy.example.json` records the known pre-27612b6 pair as an example, not runtime authority |
| Unreferenced images / Allô Nouilles | Retain; external development and tooling references are not proven complete | A Docker reclaimable label cannot establish dispensability |
| Build Cache | Review candidates only when native `InUse=false`, `Shared=false`, last use **strictly older than 720h**, valid metadata, non-internal/frontend type, and operation gate clear | Logical bytes are an upper bound; reconstructibility remains UNKNOWN until source, lockfiles and required artifacts are checked. No byte-for-byte undo exists |
| Releases | Retain all in this companion; show container config/mount/working-directory references, package/dump paths, canonical identity and incomplete references | Existing digest-bound worktree retention remains the eventual execution mechanism. Opaque/unknown references and data-bearing Windows packages stay protected |
| Volumes | Retain all, including zero-refcount entries | Persistent data has no cleanup path here |
| Docker/Nginx logs | Measure separately; prepare bounded Docker logging only | Never manually rotate, rename, truncate or unlink Docker-managed files |
| Journal | Report metadata; preserve the installed strict archive policy | Existing policy verifies ARCHIVED state, final-entry age, file identity, open files and holds. No broad vacuum is introduced |

The 30-day cache rule follows the reviewed shared-host policy installed on
2026-10-31. The older Staging seven-day helper is not the authority for this
review. Existing installed policy hashes and cron domain markers are included
in each new snapshot; existence alone does not prove a maintenance run succeeded.

## Read-only use

The main Agent first verifies the actual Production SHA, schema, health and
absence of concurrent deployment. Capture once on the server using the script
on standard input; no script needs to be uploaded or installed:

```bash
umask 077
ssh -o BatchMode=yes restaurant-prod 'sudo -n python3 -' \
  < scripts/disk-governance/collect_metadata.py > /private/local/path/disk-snapshot.json
python3 scripts/disk-governance/plan.py \
  --snapshot /private/local/path/disk-snapshot.json \
  --policy scripts/disk-governance/policy.example.json --dry-run \
  > /private/local/path/disk-plan.json
```

The local output directory must already exist. Before use, replace the example
policy with exact rollback IDs verified from retained recovery models. The
planner rejects a rollback ID absent from the complete image inventory.
Snapshot SHA-256 binds the review to its input. It is evidence, **not deletion
authorization**. Fresh state and independent Owner approval are required for any
later action.

The plan includes every exact retained/candidate ID or path and its reasons.
`cache_candidate_logical_bytes_upper_bound` is not a promised reduction in
`df`. There is deliberately no combined reclaim total: image/cache layers can
overlap, and allocated release/log bytes use another measurement basis. A
snapshot is sequential, not atomic, and becomes stale when deployments, builds
or dependencies change.

## Pending logging candidates

All candidates are under `scripts/disk-governance/pending/` and are inactive:

- `compose-logging.json`: logging-only overlay for db/backend/nginx, Docker
  `local`, `max-size=10m`, `max-file=3`. Apply only during a separately approved
  exact-artifact container recreation. Existing explicit logging in private
  promotion models also requires review; daemon defaults do not replace it.
- `docker-logging.json`: the same optional daemon default. Merge only the
  approved keys into existing daemon configuration in a future approved window;
  never overwrite unrelated keys. No Docker daemon restart is performed here.
- `journald.conf`: one-day file rotation only, preserving the existing strict
  archive-expiry job. No size cap or head-timestamp expiry widens deletion.
- `maintenance.logrotate`: the existing maintenance-job log policy, daily,
  30 archives, maxage 30, delayed compression, separate root/Ubuntu ownership.
  It does not target container logs or application business files.

Nginx uses Docker stdout/stderr logging; a second host Nginx file-rotation path
is not invented. Rollback of a future logging change restores the previous
reviewed configuration and, if required, recreates only the approved services.
Expired/deleted log contents cannot be recovered by configuration rollback.

## Local verification

```bash
python3 scripts/disk-governance/test_disk_governance.py
python3 scripts/disk-governance/validate_logging.py
python3 scripts/disk-governance/test_logrotate_native.py
```

The policy suite covers current/paused/rollback/unverified image protection,
volume retention, cache age boundaries/reference protection, unknown metadata,
active/unknown operations, release data/opaque references, forbidden execute
mode, and secret/content exclusion. Semantic logging validation rejects
unrelated runtime changes or more aggressive journal expiry.

The native logrotate test is separate and skips clearly if no binary exists.
Set `LOGROTATE_BIN` to an existing local binary; optionally use
`LOGROTATE_LIBRARY_DIR` for an extracted macOS bottle dependency. It substitutes
only private temporary paths and local account names, runs native debug first
(verifying no state or log change), then rotates two synthetic logs twice and
verifies `.1`, `.2.gz` contents and mode `0640`. It never targets `/var/log`.

This task's native rehearsal passed with logrotate **3.22.0** and popt **1.19**,
official Homebrew bottles extracted into a task-owned temporary directory.
No package was installed and no service started. A bottle-cache version refresh
and macOS dynamic-library environment issue were corrected locally; neither
affected repository product code or the server. This proves grammar and local
rotation behavior, not Linux account existence or Production activation.

## Separate future approval packets

No actual cleanup is authorized by this document. Prepare separate bounded
packets for cache, images, releases and logs; do not combine them with an
application deployment:

1. Fresh operation gate and exact container/image/recovery inventory.
2. Exact object IDs/paths, age, native reference state and independent holds.
3. For images/releases, verified external/tooling references, protected paused
   environments and independently recoverable data/artifacts; otherwise retain.
4. Expected **non-additive** logical/allocated benefit and rebuild/recovery cost.
5. Explicit irreversible-loss boundary, approved command/object selection,
   post-action continuity checks and measured actual `df` change.

The existing execution helpers must recheck their locks, exact approved plan,
references, identity and freshness immediately before any future mutation.
This companion supplies no broad prune, release removal or log-deletion path.

## Captured acceptance, 2026-11-03

The main Agent captured metadata at **2026-11-04 04:51:54 UTC / 2026-11-03
23:51:54 America/Toronto**. This worker made no server connection. The frozen
collector SHA-256 is
`3a7fa63b2d1ea9707bca48a0b6ee88b2a7a101c651b099ddc8bf0952d2167d17`.

- [Read-only metadata](evidence/production-resource-optimization-20261103/disk-metadata.json):
  12 containers, 141 images, 207 cache records, 87 releases, 3 volumes;
  operation gate complete/clear, collection errors empty. Snapshot SHA-256
  `3b8bd85a5da59363c7bafb7c60dc65ac10541c993601e7497dd29eb16f9cc21e`.
- [Exact retained/candidate plan](evidence/production-resource-optimization-20261103/disk-plan.json):
  every image/release/volume retained. All 87 releases contain Windows
  distribution/data paths and have incomplete recovery-reference proof.
  `Config.WorkingDir` is an internal container path and is deliberately not
  treated as a host release reference.
- [Verification evidence](evidence/production-resource-optimization-20261103/disk-verification.json):
  **14 safety tests PASS**, logging semantics PASS, native logrotate synthetic
  rehearsal PASS. Five installed cron/logrotate/retention script hashes match
  the reviewed 2026-10-31 source exactly.

The installed cron currently has cache, journal and release domains enabled.
Those **pre-existing policies were not disabled, reinstalled or changed**.
This package adds no automated cleanup. A matching cron/script hash proves
configuration identity, not that every scheduled run succeeded.

All current Production and stopped-container image IDs are listed in the plan.
The explicitly verified prior Production rollback pair is `bf09f83635d5…` and
`af9a528ae56c…`; the full IDs are bound by `policy_sha256` in the plan. Allô
Nouilles and other unproven image/tooling dependencies remain protected.

Eleven native private/unused cache records satisfy the existing age rule:

| Cache ID | Logical bytes |
| --- | ---: |
| 10t4rd1qm9i520kqlrup4kjfk | 14,751 |
| 37jb3ed57319w1tp76512pl6u | 20,480,394 |
| 4dwrshlvokews7e13gvmftlku | 24,931 |
| 51u9ids2ndrnfqeuia4xa962m | 12,446 |
| b38sbh9tj31hulvnwt2ibkxa0 | 21,329 |
| j36e7rfx495gcn6t1qn1rdz8u | 113,127,900 |
| k1tb0f1c3sa8z3bt5ougczivl | 117,398,036 |
| lsuyigptdvqk1q1h41vu4gj4g | 427,409,003 |
| r2kp5lhrtyxx4y76xhhu87not | 91,601,600 |
| sqwa1u9cfu118husoresmrkh8 | 12,442 |
| v52m2id5pyacmj18lapsum7hq | 12,288 |
| **Upper bound, not promised disk release** | **770,115,120** |

These are review candidates only. The same approximate 770 MB was retained by
the prior reviewed prune; engine references may retain it again. No wider or
forced cleanup is justified. Other cache retention reasons include shared
references, in-use state, recent use and unknown last-use timestamps.

Container log files total **11,690,937 logical bytes / 11,743,232 allocated
bytes**, separately measured from Docker image/cache accounting. No logs were
rotated or removed on the server. Cleanup performed: **NO**; bytes reclaimed by
this task: **0**. Production mutation: **NO**. Staging/Test remained paused.
