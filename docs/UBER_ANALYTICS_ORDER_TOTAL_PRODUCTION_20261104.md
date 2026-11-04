# Six-order v3 Production promotion: complete

PRIMARY_REPAIR_GOAL: deploy the exact Owner-approved74dfc1f artifacts, confirm only
the six authoritative merchant totals CAD315.41 and preserve item uncertainty,
original snapshots, business state and independent recovery.
EXPECTED_REPAIR_CLASS: MEDIUM. Execution2026-11-04 approximately04:53–05:21UTC.

Owner explicitly accepted the post-daily-close baseline107 orders/POS61/Uber46,
POS2707.04, and authorized continuous Production promotion through acceptance.
The existing automatic Finish records are retained. Current PhaseB package only;
no next Phase, performance, Allô, Inbox, archive or cleanup work is opened.
Fresh origin/main80e888fcdad74e98326fcbeaa5df14bc0d9baed6 is not a release input.
Dedicated worktree/branch retained; unrelated Owner work is untouched.

## Exact immutable application and operation scope

Production before: `27612b6f72dabc48e0167cc7d214a3ece565dd5e`.
Production after: `74dfc1fd235af31fc7345c43f770d9991960da70`.
Application JAR SHA256 `5a5efdc7e4fb1df91b1a7227e419ee4e5859c4446fc1a3f01a714947246eba34`.
Static archive SHA256 `aa598848c65cf27822f222612a230bf9291c9ffe8942139efc07b5c97eab012c`.
Migration resource SHA256 `07785bfa52ec8a8aa2abb28713a4d9bc7254792bc01721f1aa0506911cd06e4f`.
Only two COPY layers over the protected current runtime bases; no server-side
Maven/npm compile or package installation. No application rebuild or source
change after approval. The application and operator script have separate identity.

BLOCKING TOOLING: `promote-order-total.py` implements the already-reviewed release
plan by reusing the prior private Compose, inventory, resource, health, backup
and rollback primitives. The frozen fourteen-target/v2 helper cannot run this
new SHA/schema/six-target case unchanged. Operator commit
`9e948f380eecc50a04447670288665be98e53a11`, script SHA256
`9a13ff5f35d7518777d6ffaa7db582567a673e0325dfec48bdba033ab68f4bf5`. Agent6 ACCEPT, independent11/11 tool tests PASS.
One review P2 closed: canonical Compose and release markers restore before health
wait, so DB outage cannot skip their restoration. App/financial behavior remains
the already accepted backend1254/0fail/0error/117conditional skips, focused31,
frontend374/build/lint, Java17 and cross-JVM preview evidence. No new deployment
framework or additional credential/device lifecycle was introduced.

- backend: container `6a9587d1a6058dad77004c7b97e46fbe3cffdea203f64c8377fe5d0d3c53785a`, image `sha256:3170aae3f7783bf1fdcf7da239e9c881d48e2bb5cec2f3cf3893b1e89cd783a7`, started `2026-11-04T05:09:43.950789675Z`, restarts0.
- nginx: container `1c5dcf0f617c3d9d667fb884c990f782ccedfe2f6ceb05f3bfc00aa6d62df008`, image `sha256:e3607bba1a2bf3540f8bcaa2dbae757214cc75ec1c10c9e00298e10409b5de09`, started `2026-11-04T05:10:09.559727536Z`, restarts0.
- db: container `c2ab37fec6ac966e77a1d8ab7aa41d1da294b9ac57b1a21ce18904d04cfae91e`, image `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777`, started `2026-07-11T12:09:37.936437539Z`, restarts0.

DB container/image/start are unchanged. Only backend/nginx were replaced.
Flyway old ledger unchanged, exactly one append:
`39.2|V39_2__allow_uber_order_total_correction_v3.sql|684147509|t`.
Only journal version CHECK widens2->2/3; no business index, SQL tuning or parameter
change. Eight paused containers and all unrelated runtime fingerprints unchanged.
Deployment lock/quiet queues/resource gates passed each mutation boundary;
other active Codex work was local-only, with no observed concurrent deployment.

## Backup and verified recovery source

NEW pre-release V39.1 custom backup17473776 bytes, SHA256:
`654fec1c1986a62c268e4305888aa47e65ebcc0963eb44359bb33aab637e1778`.
TOC includes orders/printing/financial journal. Complete isolated local restore
with --exit-on-error PASS; original business counts/max IDs, original financial
and protected-print fingerprints, complete V39.1 ledger and all eight v2 journal
row fingerprints match. Restored POS61/sales2707.04 independently checked.
Exact Java17 candidate against the restore produced the approved six-target
preview, identical to the live read-only preview before and after replacement:
`8c96d7b6a8fc3fa2bbaf1339c4ff3275224299a1fa9700f8c5ee0b08c6c41360`.
No app/Spring workers/printers or Uber client started against the restored data.

Private server backup/config/rollback/operator/evidence directory:
`/srv/restaurant-pos/production/uber-order-total-74dfc1fd235af31fc7345c43f770d9991960da70/`.
Private local backup/restore evidence retained outside Git. Task-owned local PG
is stopped cleanly at handoff. No database restore occurred on Production.

## Actual financial correction and API acceptance

Production journal read-back confirms exactly14 rows: old v2 eight unchanged,
new v3 APPLY six, v3 REVOKE0. Original v1 fields are byte-preserved. Actual values
below are read from journal snapshots, not just the proposed preview.

| Audit ID | v3 action | Actual CAD | Item amounts |
| --- | --- | ---: | --- |
| U-e081b098 | APPLY | 51.14 | Pending reconciliation |
| U-d40b43e1 | APPLY | 70.66 | Pending reconciliation |
| U-284238ab | APPLY | 73.54 | Pending reconciliation |
| U-31281cd2 | APPLY | 47.69 | Pending reconciliation |
| U-6f6dfe76 | APPLY | 32.74 | Pending reconciliation |
| U-ae3be476 | APPLY | 39.64 | Pending reconciliation |

Total31541 cents, with no cent added/subtracted and no guessed item net values.
Production APPLY ran once; replay/idempotency and REVOKE were proven in isolation.
No repeat Production write was used as an acceptance test. Existing known Uber
140306 +31541 =171847; known total411010 +31541 =442551, with no duplicate amount.

| Saint-Denis2026-11-03 | Actual API |
| --- | ---: |
| In-store sales | CAD2707.04 |
| Uber sales | CAD1718.47 |
| Total sales | CAD4425.51 |
| Total orders | 107 |
| In-store / Uber orders | 61 /46 |
| Total AOV | CAD41.36 |
| Unknown order totals | 0 |
| Item-reconciliation orders / root amount rows | 6 /18 |

Total/known revenue both4425.51, order-level completeness confirmed. Category/
item amount uncertainty and incomplete Revenue Mix percentages remain separate.
Quantities, date/store/channel filters and POS money retained. Source funding
breakdown and the accounting explanation of component rounding remain unknown;
order revenue is the accepted unchanged authority total, not settlement/eater pay.

Three-Store same-day regression: Store1 above; Store2 184/CAD8434.32 and Store3
103/CAD4289.32 unchanged from the accepted pre-release state. The prior-day
2026-11-02 Store2/3 projections also match in full (new completeness fields
excluded from legacy structural comparison). No all-history full scan claimed.
No new orders/items/PrintJobs/Outbox/kitchen tasks/Uber events; original finance,
old v2 rows, historical printing states and all other containers unchanged.
No order submission, physical print, Uber operation, replay or test business.

## Health and interruption evidence

Independent fresh audit at2026-11-04T05:12:40Z PASS; subsequent actual-six journal
and HTTP read-back PASS. HTTPS root200, Backend health200/UP; PostgreSQL healthy,
all Production restart counts0; bounded200-line Backend log0 ERROR. Public HTML
matches the exact approved local static archive. Backend/nginx have no Docker
healthcheck; health is the real HTTP endpoint, not an invented Docker status.
Single-instance replacement operation80.00s including checks/health/apply/accept.
Exact HTTP interruption seconds are UNKNOWN without a continuous trace; no
zero-downtime claim. No release rollback/retry or compensating write was needed.

## Executable recovery, only if required

Applicable when this batch is still the active app pair and its saved runtime
configuration remains current, in an Owner-authorized maintenance/incident window.
The operator acquires the existing deployment lock and rejects another app image
or changed environment before manual rollback. It first attempts this batch's
six-record v3 REVOKE using the retained new image, then restores the exact saved
27612b6 app pair and both canonical deployment markers. Schema V39.2 remains.

```bash
ssh restaurant-prod 'python3 /srv/restaurant-pos/production/uber-order-total-74dfc1fd235af31fc7345c43f770d9991960da70/operator.py rollback'
```

Only if needed; NOT RUN after this successful release. It must not be used to
replace a later release. The retained rollback.private.json contains exact old
image IDs, environment/mount/network/limit parameters; rollback-release.private.json
contains its canonical marker. New images and all original/current data retained.

Data compensation is independently available through the exact new CLI mode:
`--mode revoke --case order-total-v3 --preview-sha 8c96d7b6a8fc3fa2bbaf1339c4ff3275224299a1fa9700f8c5ee0b08c6c41360`.
REVOKE appends audit events; no APPLY/v1/v2 is deleted. It works with later source
or business-state drift and is idempotent. Verify six REVOKE entries and unchanged
old eight v2 before claiming financial recovery; check compensation.json and
compensation-failure.json. DB/compensation failure does not skip app rollback,
but app recovery alone is NOT proof of successful financial compensation.
No old whole-DB backup may overwrite newer business data; no down-migration,
original-snapshot rewrite, order-state edit or print/outbox replay is part of recovery.

## Docker / Disk Hygiene

Actual df before64%/21GiB free, after65%/20GiB free. Build Cache8.452->8.562GB;
COPY-only new image/artifact layers explain growth within observe8–12GB band.
Cleanup performed:NO, explicitly reclaimed0. Current74df and prior27612 app pairs,
DB/volumes, all paused images/data, new verified backup and private models protected.
Staging/Test remain PAUSED. Production mutation only app pair + V39.2 + six
financial journal inserts. No cleanup, expansion, JVM/PG tuning or CPU fix.
Post-audit MemAvailable2007003136 bytes, load0.66/0.56/0.30; no long-term trend claim.

## Handoff and Stop

Owner acceptance: https://pos.lanzhounoodlesmtl.com/stores/1/admin/dashboard .
Select Saint-Denis and2026-11-03 explicitly (local time has crossed midnight).
Verify complete totals/AOV and separate6 orders/18 item rows under reconciliation.

PRODUCT REPAIR: exact pre-tested74df application/JAR/static artifacts unchanged.
TOOLING / GOVERNANCE REPAIR: bounded six-target operator/test adaptation, exact
backup/restore/preview/continuity proof, current-state and this evidence only.
Final evidence-only changes use the documentation risk-based exception, with no
application rebuild/redeploy. PR307 remains open/unmerged; runtime is the exact
approved74df artifact, not current main or the documentation HEAD.

Stop: UBER_ANALYTICS_ORDER_TOTAL_PRODUCTION_COMPLETE_6_APPLIED_ZERO_ORDER_PENDING.
Default Production mutation authorization is closed after this batch. No further
optimization, archiving, cleanup, deployment or new Phase follows automatically.

## Execution Time Breakdown

Estimated attribution from04:53 to05:21UTC, approximately28 minutes, counted once
across overlapping validation/build activity.

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 3m | Fresh source/runtime/lock/concurrency/new baseline/resource gates |
| Implementation | 6m | Operator adaptation only; application unchanged |
| Tests | 1m | 11 tool tests; approved app test/artifact proof reused |
| Agent 6 / PR / Merge | 3m | Operator ACCEPT/P2 marker-order fix; no merge |
| Staging Deploy | N/A | Remains paused |
| Staging Acceptance | N/A | Owner-approved local isolation proof reused |
| Production Backup / Build / Restore | 6m | Fresh dump, full local restore/fingerprints/Java17 preview, COPY images |
| Production Deploy / Acceptance | 4m | 80s replacement/apply plus independent read-only audit |
| Evidence / Governance | 5m | Actual-six read-back, rollback/handoff/state sync |
| Total | 28m Estimated | Stop after successful acceptance |
