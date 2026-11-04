# Production resource optimization — local delivery

Owner scope: three local implementation/test workflows. No merge, Production
rollout, index creation, real data deletion/archive, host cleanup or Staging/Test
restoration is authorized or performed. Work began2026-11-03 America/Toronto and
continued past midnight2026-11-04.

Fresh main:80e888fcdad74e98326fcbeaa5df14bc0d9baed6. Verified runtime and branch
base:27612b6f72dabc48e0167cc7d214a3ece565dd5e / V39.1. Pending v3 financial repair
is excluded. Branch:codex/production-resource-optimization. PR delivery uses an
immutable27612b6 baseline branch to exclude the unmerged financial implementation
from this optimization diff. No automatic merge/deploy follows this report.

PR: https://github.com/Z1linXu/Restaurant_System/pull/309 (draft, not merged).
Implementation commit: `0346c8bae09ecfd37e52cf8499a1ca0c3be3cc73`.
Subsequent publication sync changes documentation/evidence only.

## A–C. Inbox, sharing and synthetic performance

Implemented batch narrow latest PrintJob states and one batch missing-module
Outbox fallback, preserving latest created_at/id ordering, warnings, NOT_REQUIRED
and existing UNCERTAIN display semantics. Local request JSON decodes once per
rendered order. The existing/orders API remains compatible. New/inbox provides
Store/day, independent attention and opt-in history pages with explicit cursors.
Cross-day unfinished work, cancelled dispatch, print fallback problems, unknown
states and broken references remain visible. Invalid query parameters return400
before data reads; authorization401/403 boundaries remain in place.

Page+Badge share one identity/permissions/Store stream, realtime client and
visible-page5s timer. Last consumer aborts and tears down; Store/user/permission
changes isolate old results. Reconnect/online/visibility refreshes are retained.
Additional explicitly loaded pages remain live; history is manual only.

PG16.14 synthetic benchmark:40,000 jobs,10,000 Uber records, large external
printing payloads,3 warmups and30 measured repetitions. Values below are SQL
round-trip groups on the local test machine, not HTTP or Production latency.

| State projection | SQL/group | P50 ms | P95 ms | Buffers | Backend CPU,30 groups |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original per-order | 268 | 765.84 | 799.35 | 1000000 | 21.6017s |
| Batch, existing indexes | 2 | 7.12 | 7.53 | 5000 | 0.1953s |
| Batch + candidate index | 2 | 1.316 | 1.397 | 702 | 0.02454s |

| Full new grouped SQL | SQL/group | P50 ms | P95 ms | Buffers | Backend CPU,30 groups |
| --- | ---: | ---: | ---: | ---: | ---: |
| Existing indexes | 4 | 48.57 | 49.33 | 10854 | 1.4093s |
| Candidate index | 4 | 45.47 | 127.78 | 6582 | 1.3620s |

A timezone query and fixed authorization reads are additional. Complete grouped
P95 did not improve in this sample. State result hashes match exactly; complete
feed fallback sets also match. Representative plans and all metrics are in
[benchmark JSON](evidence/resource-optimization/inbox-synthetic-benchmark.json).
The latest-state attention selector still scales with retained Store printing
history; there is no claim of a constant-cost full feed for unlimited years.

The optional(store_id,order_id,created_at DESC,id DESC) index benefits the bounded
projection, but is a separate measured/approved follow-up after batch/UI
observation. Explicit apply/rollback scripts are outside Flyway; six real-PG
approval/database/name/definition/rollback guard tests passed. No production DDL.

## D–E. Reporting and database lifecycle

New authorized Store/day/month report reuses the deployed channel model without
changing any financial parser, snapshot or correction. Counts, known money,
Pending orders/lines, stable item identity/name/quantity, item/category sales and
daily series are retained. Option money is not added again. Recorded POS
discount is informational; independent unverified tax/Uber discount/refund
amounts are null/UNKNOWN. A repeatable-read live recomputation reflects late
transactions/corrections idempotently; it does not replace original evidence.

The lifecycle tool classifies22 tables with real catalog FK and explicit
application dependencies, configurable review ages and bounded resumable pages.
All deletion is blocked. There is no DELETE executor or scheduled cleanup.
Audit-copy archives have private exclusive files, scope/schema/checksum proof,
and strictly guarded synthetic local recovery with no overwrite. Twelve tests
include simulated fixture removal/exact recovery, repeat recovery, identity
conflict rollback, sequence safety and unchanged protected business fingerprints.
This proves audit-copy recovery only, not completed-order graph archival or
financial/security retention eligibility.

Daily short batches would be preferred after a safe deletion class is proven;
weekly bursts are larger, monthly single-transaction deletion is unsuitable.
Normal deletion primarily creates internally reusable space, not guaranteed OS
file shrinkage. No Trigger or database maintenance was introduced.

## F–G. Disk and logging

Read-only metadata collected once with an initial collector, then refreshed
with its frozen final policy/volume fields; no script was installed on the host.
All141 images,87 data-bearing/opaque releases and3 volumes are protected.
Eleven native unused/private/aged cache records are review candidates, logical
upper bound770115120B. Rebuildability and actual df reduction remain UNKNOWN;
there is no delete/execute mode. Current/rollback/paused/unknown dependencies
remain protected. Exact IDs and reasons:
[disk plan](evidence/production-resource-optimization-20261103/disk-plan.json).

Existing server cache/journal/release cron domains were already enabled; five
installed policy/cron/logrotate hashes match their reviewed source. They were
not modified. New logging fragments remain pending. Fourteen safety tests,
logging semantics and one native synthetic logrotate rehearsal passed, including
retained archive content/permissions; no service or server configuration changed.
Docker logical/shared values and allocated file bytes must not be summed.

## H. Memory and current runtime

At2026-11-04 00:04 Toronto, runtime remained27612b6/V39.1; app IDs/images/start
and restart0 unchanged; DB healthy, existing health200/UP, no operation lock.
MemAvailable1924028KiB=1878.93MiB; MemFree137.62MiB; Cached1865.89MiB. Java RSS
732140KiB=714.98MiB, Xms256m/Xmx1024m unchanged; Backend cgroup716.43MiB. DB
cgroup268.98MiB, CLI-equivalent206.27MiB after inactive_file adjustment. These
measurements overlap and must not be added. PostgreSQL child RSS was not summed.

Current memory PSI avg10=0.00; recent some avg60/300=0.06/0.07 and full=0.04/0.05
were small but nonzero. OOM kills0, no swap. Earlier23:42 Available was1872.14MiB.
Available remains stable across the two observations; no sustained severe
pressure or leak is established. Heap Used/Committed/Non-Heap and peak trend are
UNKNOWN. Linux cache and JVM residency explain substantial used memory; the
cloud's exact metric formula is UNKNOWN. No present evidence requires8GB, and
this is not a high-traffic capacity guarantee. No JVM tuning/GC/profiling ran.

## I. Tests, review and delivery

Final Java17 full suite:1276 total,1158 executed PASS,118 conditional skips,
zero failures/errors. New27 tests all ran, including6 real-PG read tests and8
real-handler MVC validation tests. Frontend67 files/388 tests/build/changed-file
lint PASS. Lifecycle12 real/local tests, disk14 safety+native1, manual-index6
PASS. No application was started against a Production backup or live Uber.

Agent6 ACCEPT, no open P0/P1/P2. Two P2 clusters were closed before acceptance:
valid archive payload collided with an SQL delimiter; new Inbox invalid query
parameters were mapped to500. Independent reruns verified special-text recovery,
400 probes,37 frontend tests,12 lifecycle tests, and disk/native proofs.
[Unified verification](evidence/production-resource-optimization-20261103/verification-summary.json).

PRODUCT changes: Inbox controller/read repository/view, frontend subscription/
groups/services, period report controller/service/DTO and related tests.
TOOLING changes: explicit benchmark, guarded manual index scripts, lifecycle
Dry-run/audit-copy/local recovery, read-only disk plan and pending log fragments.
GOVERNANCE changes: SYSTEM/API/current state and concise evidence. Tooling is an
explicit Owner workflow, not a newly introduced deployment/deletion framework.

## J. Independent future operations and stop

[Operation instructions](RESOURCE_OPTIMIZATION_OPERATIONS.md) separate application,
optional index, data lifecycle, disk and logging approvals. New frontend needs
new Backend first; rollback frontend before Backend. A later financial rollout
requires fresh baseline integration/test before this candidate can be deployed.
No claim of live Staging acceptance or zero-downtime rollout is made.

Production mutation:NONE. Staging/Test lifecycle action:NONE. Explicit cleanup:
NONE, reclaimed by this task:0. No real orders, prints or Uber calls.
Stop:RESOURCE_OPTIMIZATION_LOCAL_COMPLETE_NO_PRODUCTION_MUTATION.


## Execution Time Breakdown

Estimated allocation for the approximately04:40–05:12UTC interval
(2026-11-03 23:40 through2026-11-04 00:12 Toronto). Parallel work is counted
once; the final PR/evidence completion may shift the final minute slightly.

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 4m | Fresh runtime/main, isolated deployed baseline, ownership |
| Implementation | 11m | Three parallel workflows and shared frontend |
| Tests | 4m | Java17, frontend, synthetic PG/performance/logging; focused then full |
| Agent6 / PR / Merge | 7m | Two P2 repair clusters and independent rechecks; merge NOT RUN |
| Staging Deploy | N/A | Owner requires paused |
| Staging Acceptance | N/A | Local evidence is not relabeled as Staging |
| Evidence / Governance | 6m | Read-only metadata, retention lists, contracts and delivery |
| Total | about32m Estimated | No Production or cleanup action |

Retries included: one root npm invocation used the repository rather than
frontend directory; one root lint invocation had the same cwd issue; initial
governance scan reached installed third-party README links, then passed with
the task-owned dependency directory temporarily outside the scan. No product
or validator scope was broadened for these local tooling issues. The lifecycle
fixture read-only nextval check was corrected before its successful run.
Two independent-review P2 clusters were repaired together and rechecked.
