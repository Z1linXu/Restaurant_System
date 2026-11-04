# Six-order Production release: preflight stopped

Owner conditionally approved exact candidate74dfc1fd235af31fc7345c43f770d9991960da70
and six authoritative CAD315.41 corrections. Attempt started2026-11-04T04:47Z.
Owner explicitly requires stopping if runtime/data/safety conditions differ.

Result: STOPPED BEFORE MUTATION. No Production upload/build, backup, container
replacement, migration, financial correction, rollback or cleanup was executed.
Only bounded read-only metadata/aggregate/financial fingerprint checks ran.
Repository executable trees and approved local artifacts/checksums match the
approved manifest. Fresh origin/main80e888fcdad74e98326fcbeaa5df14bc0d9baed6;
worktree was clean at83977da6f507f282580ec0449d728cbd8f412b95. Documentation-only
sync uses the risk-based review exception; no new code, tests or release artifact.

## Confirmed blocking discrepancy

| Saint-Denis2026-11-03 | Approved baseline | Live preflight |
| --- | ---: | ---: |
| Total orders | 106 | 107 |
| In-store orders | 60 | 61 |
| Uber orders | 46 | 46 |
| In-store sales CAD | 2639.25 | 2707.04 |
| Known Uber sales CAD | 1403.06 | 1403.06 |
| Known total sales CAD | 4042.31 | 4110.10 |
| Unknown order amounts | 6 | 6 |

CONFIRMED cause: existing StoreDailyCloseScheduler's persisted close ledger for
2026-11-03, America/Toronto, started/completed23:30:19.768628-05:00 records Store1
finished_order_count1, Store2 count0, Store3 count3. Bounded order aggregates for
23:29–23:32 confirm Store1 completed one POS order CAD67.79 at23:30:20.166683;
Store3 completed three totalingCAD119.50 at23:30:19.806525–23:30:20.039680.
These are pre-existing scheduler changes, before this release attempt; no order
or completion action was triggered by this task. They must not be reversed or
excluded to force the old financial acceptance totals.

Current Store2 same-day184 orders/CAD8434.32; Store3 now103/CAD4289.32.
If a later Owner decision accepts the live baseline and all gates pass, the
unchanged six-order candidate would naturally yield Store1 Uber1718.47,
POS2707.04, Total4425.51, Total AOV41.36, orders107/61/46, zero unknown order
totals and six orders/eighteen root-item amount rows pending item reconciliation.
These are CONDITIONAL projections, not applied or published results.

## Checks that did not block

- Runtime still27612b6f72dabc48e0167cc7d214a3ece565dd5e: backend image
  sha256:1e06e79b8655fdc56bd647390d12099f4df304da6314d7ed617a436031f999f5,
  nginx sha256:18dd0fa21a80f77a357f2a2d32069094182cdb26c8f7cd135f4b04fd76d598e7.
- DB remains healthy/V39.1, original V39.1 checksum1612790231; eight existing
  v2 journal records, zero v3 records. Six original/source financial fingerprints
  match retained evidence; six saved authority totals still sum31541 cents and
  all six have zero correction rows. Original approved preview remains intact.
- Production deployment lock available at inspection, no concurrent Docker/
  deployment process observed. Another active Codex is doing local performance
  development under explicit no-Production-deploy/mutation authority; no such
  code or artifact was included. No message or command was sent to that task.
- Read-only queue guard passed: pending print/outbox/Uber events/due Uber work0;
  no fresh printing/new POS submissions. Historical tasks retained unchanged.
- Load0.002/0.049/0.051, MemAvailable1955643392 bytes; df64% used/21GiB available.
  Docker Build Cache8.452GB, no cleanup. Production API aggregate GETs200; eight
  Staging/Test containers remain stopped. No new backup was started after the
  failed data gate; no backup/restore success is claimed for this attempt.

## Next gate and recovery

Owner must review/accept the changed live baseline before continuing this exact
candidate release. Fresh runtime/source/quiet/resource/lock checks and a NEW
Production backup + isolated restore verification remain required. Do not write
fixed totals, modify POS history, undo daily close, or substitute the previous
pre-V39.1 backup. No application/data rollback is needed because nothing was
changed by this release attempt. Existing current images, configuration, data,
local candidate artifacts and v3 compensation plan are retained.

Stop: UBER_ANALYTICS_ORDER_TOTAL_RELEASE_STOPPED_BASELINE_CHANGED_PROD_UNTOUCHED.

## Execution Time Breakdown

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 4m Estimated | Artifact/runtime/lock/concurrency/resource/live baseline; bounded close-ledger proof |
| Implementation | N/A | Approved executable/artifacts unchanged |
| Tests | N/A | Existing test/review evidence reused; artifact hashes verified |
| Agent6 / PR / Merge | N/A | Documentation-only exception; no merge |
| Staging Deploy | N/A | Remains paused |
| Staging Acceptance | N/A | Remains paused |
| Production Backup / Deploy / Correction | NOT RUN | Failed data gate before mutation |
| Evidence / Governance | 2m Estimated | Stop evidence and current-state sync |
| Total | 6m Estimated | Release attempt to explicit Stop |
