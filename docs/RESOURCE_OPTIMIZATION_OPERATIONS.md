# Resource optimization: independent operation boundaries

PRIMARY_REPAIR_GOAL: reduce Uber Inbox query amplification and prepare safe,
reviewable reporting/data/disk lifecycle tooling without runtime mutation.
EXPECTED_REPAIR_CLASS: HIGH (three explicitly authorized workflows).

This document prepares separate future approvals. It authorizes no operation.
Production remains unchanged; Staging/Test must remain paused. No real order,
physical print or live Uber call belongs to local acceptance.

## Baseline and integration

Fresh origin/main at implementation start: `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`.
Verified Production application: `27612b6f72dabc48e0167cc7d214a3ece565dd5e`, V39.1.
The optimization branch starts at that exact deployed application. Pending v3
financial changes and their V39.2 are excluded. A dedicated immutable PR base at
27612b6 keeps this PR's diff free of the unmerged financial repair.

The application diff changes Inbox read projection/UI and adds a read-only
period report. Printing core, PAD/Android, Outbox execution, order execution,
Uber Worker and financial source/snapshot/correction files are unchanged.
There is no new automatic Flyway migration. Before any future rollout, fetch
and compare the then-current Production SHA/schema. If the other financial
candidate has deployed, integrate onto that approved baseline and repeat the
affected completeness/report/Inbox tests; never overwrite it with this branch.

## Application rollout approval

Prepare one immutable reviewed commit and backend/frontend artifact pair,
checksums, actual target identity, backup/restore proof, runtime settings,
expected interruption window, and exact rollback images using the existing
reviewed promotion/backup path. This work adds no second deployment helper.
Staging/Test were deliberately not deployed or started under this authorization;
local isolated evidence must not be relabeled as live Staging acceptance.

The new frontend requires `/inbox`. Upgrade the compatible Backend before the
frontend, or promote the same approved pair in that order. The old frontend
continues using the preserved `/orders` endpoint. A single Backend replacement
can interrupt HTTP and must not be described as zero downtime.

After authorized deployment, check health and exact artifact identity, then
observe normal business traffic without creating a real test order/print.
Compare Inbox requests/connections, fixed SQL budget, scan/buffer deltas,
DB/Java CPU, latency and errors under comparable load. Recheck cross-day and
attention visibility. Revert frontend before reverting Backend so a new UI is
not left calling a removed endpoint. Retain all financial data and journals.

## Optional index approval (separate)

The scripts are `deployment/cloud/manual/print-jobs-inbox-index.sql` and its
rollback counterpart. They are outside `backend/src/main/resources/db/migration`
and ordinary startup never executes them. Apply is not part of application
rollout by default. No Production index was built in this task.

Synthetic evidence strongly supports the bounded state projection's index
access path; complete feed P95 did not improve in the final sample. First
observe the batch/UI improvement, then decide whether the optional index is
worth its build/write/storage cost. Synthetic warm figures are not a server
capacity guarantee or API HTTP latency measurement.

If independently approved: verify exact DB, existing definition/validity,
long transactions, load, disk margin and lock budget; use the established
private connection with the reviewed script's explicit approval/database
parameters. Concurrent creation must run outside a transaction. On failure,
inspect for an invalid remnant; do not blindly retry or normalize another
index under the same name. The rollback script checks the exact definition
before separately approved concurrent removal. App rollback does not require
index removal. No VACUUM/ANALYZE/REINDEX is bundled into this operation.

## Data lifecycle approval (separate)

All table cleanup is blocked. This branch deliberately has no DELETE executor
or scheduled cleanup. Configurable ages select review horizons, not eligible
transaction deletion. Daily/monthly reports still require raw transactions.
The guarded audit-copy/local recovery proof is not an order-graph archive or
permission to delete audit evidence. Do not activate any automatic cleanup.

Before proposing real archive/deletion, specify exact records, statuses,
retention evidence, FK/application/dedup closure, original financial sources,
archive-backed reporting, expected WAL/locks/vacuum effects and restore plan.
Rehearse exact restoration without overwriting later activity. A full backup
restore over new restaurant orders is not an ordinary rollback.

## Host disk cleanup approval (separate)

Use a fresh read-only snapshot and offline plan from `scripts/disk-governance/`.
The committed snapshot is evidence of its timestamp, not future deletion
eligibility. Protect current and rollback images, every paused environment,
unknown Allô dependencies, volumes and data-bearing/opaque releases.

The current plan retains 141 images, 87 releases and 3 volumes. Eleven cache
records are review candidates only, logical upper bound770115120 bytes.
Shared Docker bytes and file allocations cannot be added as reclaimable disk.
Native pruning can retain candidates, so actual df reduction is UNKNOWN.
Cache has no byte-for-byte rollback; rebuildability/dependency proof is a
separate approval prerequisite. No broad system prune is proposed.

Re-use the already reviewed maintenance policy if a later exact bounded action
is approved; do not install another scheduler or broaden its deletion domain.
The existing server cache/journal/release cron domains were already enabled
before this task and were left unchanged. New tools have no execute/delete mode.

## Logging approval (separate)

Pending fragments under `scripts/disk-governance/pending/` have isolated syntax
and native synthetic logrotate proof. They were not installed. Maintenance file
logrotate does not rotate Docker-owned files; Docker's logging driver owns
container logs. Do not unlink or truncate active container logs manually.

Adopting Docker container log limits requires a later approved container
recreation and explicit old/new LogConfig comparison using the existing rollout
path. Changing daemon defaults or journald configuration needs its own approved
operation and impact/rollback check. Keep pre-change config backups. Neither
service restart nor log removal was performed here.

## Scope check

PRODUCT: Inbox batch read/projection, shared subscription and grouped visibility;
Store/day/month reporting using unchanged financial policy.
TOOLING: explicitly requested isolated benchmark, dependency/restore rehearsal,
read-only disk plan, manual index guard and pending logging proof.
GOVERNANCE: API/system references, exact evidence and this operation separation.
No deployment architecture, deletion framework, scheduler or business state
machine was added. Production, index, data deletion and disk actions remain
independent Owner gates.
