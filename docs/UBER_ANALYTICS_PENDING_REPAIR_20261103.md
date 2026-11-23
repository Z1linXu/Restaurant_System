# Uber Analytics Pending repair

PRIMARY_REPAIR_GOAL: Recognize reliable post-promotion merchant merchandise amounts, preserve unknown amounts and original snapshots, and correct only the 14 approved Saint-Denis orders dated 2026-11-03 through an idempotent, compensatable financial journal.

EXPECTED_REPAIR_CLASS: MEDIUM.

Baseline: verified Production `028d4971740889a0ac0d784a43497bc3e5b54b3d`, Flyway V39. Fresh origin/main is `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`; it is not the release input. Dedicated branch: `codex/uber-analytics-pending-production`. Unrelated Owner work is untouched.

Owner authorizes local isolated tests, independent review, backup/restore verification and this bounded Production release without Staging/Test deployment. Staging/Test must remain stopped. Owner explicitly exempts only the four previously identified expired historical PAD_DIRECT PRINTING jobs from this release's active-work gate; their states must remain unchanged. New active work, financial validation, resource, backup and rollback gates remain mandatory.

Product changes: financial parser, financial correction journal/read integration, Analytics completeness and counts. No printing execution, polling, webhook/import/dispatch, Inbox/performance, Android, inventory or archiving change. Internal correction tooling has no Owner-facing workflow.

The six unexplained one-cent discrepancies remain blocked. An original snapshot is never overwritten. APPLY/REVOKE journal events provide data compensation independently of application rollback.

## Focused scope and validation

PRODUCT REPAIR: financial capture/policy, versioned correction/read path, shared Analytics completeness fields and UI, focused financial/PG/UI tests. Eight strict candidates total29993 cents; six ±1 cent mismatches remain Pending. Official source: https://developer.uber.com/docs/eats/references/api/v2/get-eats-order-orderid (charges, Promotions, PromoFundingSplit). No generic cent tolerance or payout inference.

BLOCKING TOOLING: the reviewed dine-in-headers promotion path assumes active Staging and no Flyway change. Owner explicitly prohibits restoring Staging. This case helper reuses its private Compose modeling, exact config comparison, health, inventory, backup and rollback primitives, replacing only case-specific gates with local restore proof, V39.1 additive scope and the fourteen-target compensation. No second release architecture or credential lifecycle. Scope expansion check: roughly260 lines of bounded orchestration are required to carry the authorized direct Production release safely; reusable/new generalized tooling is deferred. Production backup is custom pg_dump, checksum/TOC plus full isolated restore validation before replacement. Single Backend/nginx replacement can interrupt HTTP and must not be called zero downtime.

A pre-existing MenuRevisionLockIntegrationTest JPA fixture lacked the existing RealtimeEventPublisher constructor mock. Only the test fixture is repaired so full regression can run; table/product behavior is unchanged.

Application rollback uses the saved private Compose/images with unchanged runtime settings; additive journal schema is retained. Data compensation is independent: the same exact new image CLI `--mode revoke --preview-sha <batch>` appends REVOKE events, without deleting APPLY or changing original snapshots. Restore of full Production backup is an emergency separately controlled action, not automatic rollback.

## Reviewed immutable batch and backup proof

Agent 6 ACCEPT, no remaining P0/P1/P2. Two review findings closed before release:
fixed charge-field serialization order with three independent JVM previews;
compensation failure cannot skip application rollback, fault-injection PASS.
Backend full1249 tests:1132 executed PASS,117 conditional skips,0 failures/errors;
focused post-review26 PASS including actual local PostgreSQL. Frontend373 PASS,
production build and changed-file lint PASS. Bounded helper4 PASS. Production
baseline tests had a missing existing RealtimeEventPublisher mock; test-only
fixture repair closed it (one regression retry). Staging/Test NOT RUN by Owner.

PR #307: https://github.com/Z1linXu/Restaurant_System/pull/307 . Application SHA:
`27612b6f72dabc48e0167cc7d214a3ece565dd5e`. Built local Java17 JAR SHA256:
`a2ae74909f8396137729933a951631b35ffb426e60aa1a829144a2d3317e57f5`.
New backend image `sha256:1e06e79b8655fdc56bd647390d12099f4df304da6314d7ed617a436031f999f5`;
nginx image `sha256:18dd0fa21a80f77a357f2a2d32069094182cdb26c8f7cd135f4b04fd76d598e7`.
Only verified original runtime bases + tested JAR/static files; no server code
compile, dependency update or Staging startup. One build precheck rejected an
incomplete artifact transfer, before any build/deploy; completed copy checksum
passed on retry. No invalid artifact was used.

Production backup17463990 bytes, SHA256
`9d2f4ad377d7484ed521c78d9df112b330a2785e1384fe6ebb2ebf6dec79bd30`.
Custom archive TOC PASS; full isolated local restore with --exit-on-error PASS;
original business table counts/max IDs, original financial snapshot fingerprints,
protected print fingerprints and complete V39 ledger match. No Spring application,
Uber worker or printer started against the restored copy. Backup/actual Production
readonly previews match exactly:
`8df47b912fe488545adfbf56504e7e9090c90fd9db90f6c2a27bc735e0e99b15`.
Only eight strict candidates can add29993 cents; original v1 records remain.

Pre-release gate rejected validation.json mode0644, with both old application
containers unchanged. Corrected private artifact mode0600 and rechecked; no
safety gate was weakened. Historical static POS preparing records are retained;
Owner confirms operations stopped and explicitly exempts historical tasks. All
new work/printing/outbox/webhook gates remain zero; avoid23:20-23:40 automatic
end-of-day window.

### Production-sourced read-only correction preview

Original state for all14: Pending / UNSUPPORTED_PAYMENT_DETAIL. Candidate values
below are source proposals, not confirmed revenue for failed rows. Allowed scope
is INSERT into the financial journal only; no original order/snapshot/task write.

| Audit ID | Candidate CAD | Validation | Allowed |
| --- | ---: | --- | --- |
| U-6632c973 | 28.16 | PASS | YES |
| U-7009e85a | 25.86 | PASS | YES |
| U-ec16337a | 34.47 | PASS | YES |
| U-e081b098 | 51.14 | PROMO_TOTAL_RECONCILIATION_REQUIRED | NO |
| U-ca3f5b0c | 28.16 | PASS | YES |
| U-d40b43e1 | 70.66 | PROMO_TOTAL_RECONCILIATION_REQUIRED | NO |
| U-85566866 | 29.87 | PASS | YES |
| U-284238ab | 73.54 | PROMO_TOTAL_RECONCILIATION_REQUIRED | NO |
| U-5b5e15b8 | 33.32 | PASS | YES |
| U-31281cd2 | 47.69 | PROMO_TOTAL_RECONCILIATION_REQUIRED | NO |
| U-5a10537c | 56.89 | PASS | YES |
| U-a9c8dfef | 63.20 | PASS | YES |
| U-6f6dfe76 | 32.74 | PROMO_TOTAL_RECONCILIATION_REQUIRED | NO |
| U-ae3be476 | 39.64 | PROMO_TOTAL_RECONCILIATION_REQUIRED | NO |

## Production acceptance

2026-11-04T03:54Z release PASS; independent fresh audit03:55:31Z PASS.
Backend container `e546d107ee2b3a7dc0d9628dc6fa52cc10ef550226c88fe2b804a0b4f1c92f89`
started03:53:55Z; nginx `9a993f8a0da774bf2bf8de9c658188a3d320c333153b7499c1edc148e7413a9c`
started03:54:21Z. Both restart count0. DB container/image/start unchanged, healthy.
HTTPS/Backend200/UP; bounded200-line startup log check0 ERROR. Public index bytes
match the exact tested static build. All eight paused Staging/Test containers
remain stopped; all other containers/fingerprints unchanged.

Release operation window59.00s, including single-instance replacements, health
waits and financial acceptance. No continuous availability trace was taken, so
exact HTTP interruption duration is UNKNOWN; this is not zero-downtime proof.
No application/data rollback was needed. Only pre-mutation artifact transfer and
private evidence permission checks retried; neither changed runtime.

CONFIRMED: eight APPLY v2 journal records, only Production/store1/approved batch,
29993 cents; zero REVOKE. All original snapshots retained and unchanged.
The eight YES preview rows above are APPLIED; the six NO rows remain PENDING,
without journal writes. Existing known Uber110313 + recovered29993 =140306 cents;
POS263925 unchanged; known total404231. Counts106/60/46 unchanged. Complete Uber/
Total revenue, AOV and incomplete category percentages stay null with6 Pending.
Unknown order count is distinct from unknown line count. No new orders,
OrderItems, PrintJobs, Outbox, kitchen tasks or Uber events. Protected historical
printing states are unchanged. No real test order, print, replay or Uber call.

Store2 same-day184 orders/$8434.32 and Store3 same-day100/$4169.82 legacy
financial projections unchanged in full (new completeness fields excluded from
old/new structural comparison). Additional2026-11-02 historical read-only reports
match the independently restored pre-release backup: Store2 198/$8308.75,
Store3 103/$3910.19; both0 Uber. This is bounded regression evidence, not a claim
that every historical date was exhaustively queried.

UNKNOWN: the business/accounting explanation of the remaining six ±1 cent
subtotal/tax/total differences. They were not silently rounded or forced through.
No CPU/query/index/Inbox/archiving optimization was implemented.

## Docker / Disk Hygiene

Actual df before66%,20GiB available; after64%,21GiB available. Build Cache8.293GB
->8.452GB, within observe/explain8–12GB band; growth is two artifact COPY builds.
Cleanup performed:NO; reclaimed by explicit cleanup:0. Normal application
container replacement is not a cache cleanup. Existing Production baseline and
new image pairs, stopped environment images/volumes, database, private config,
rollback models and verified backup remain protected. Staging/Test are PAUSED,
not reported UP. Production mutation: authorized app pair + additiveV39.1 + eight
financial journal inserts only. Post-release snapshot MemAvailable1878MiB,
load1.02/0.54/0.30; no long-term CPU or memory trend claim.

## Recovery and handoff

Private server evidence/backup/model location:
`/srv/restaurant-pos/production/uber-analytics-pending-27612b6f72dabc48e0167cc7d214a3ece565dd5e/`.
Application recovery and independent financial compensation commands are in the
bounded helper README. Restore source checksum and preview batch are recorded
above. Old application image pair and compatible additive schema are retained.
A complete DB restore is not normal rollback and was not performed on Production.
PR307 remains open; runtime is the explicit tested branch SHA, not main's HEAD.
No automatic merge, second deployment or next Package follows this completion.

PRODUCT REPAIR: financial policy/capture, journal/read/CLI, Analytics metrics/UI
and tests. TOOLING / GOVERNANCE REPAIR: bounded reuse of reviewed promotion path,
backup restore/target preview proof, rollback fault test, one existing test mock,
SYSTEM/API/current-state/evidence sync. Necessary direct-Production safety only;
no general deployment architecture, unrelated product or performance work.

Stop: UBER_ANALYTICS_PENDING_PRODUCTION_COMPLETE_8_CORRECTED_6_PENDING.
Owner acceptance: /stores/1/admin/dashboard, Saint-Denis,2026-11-03. Further cent
reconciliation or historical financial correction requires a new bounded decision.

Evidence-only final sync uses the documentation risk-based review exception;
application/deployment trees remain exactly27612b6. No rebuild/redeploy is
triggered by this documentation commit. Local task-owned PostgreSQL55483 stopped
cleanly after verification; its verified backup/private evidence is retained.

## Execution Time Breakdown

Estimated allocation of the actual03:17–04:00UTC interval (2026-11-04), about43m;
parallel implementation/test work is attributed once, not double-counted.

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 7m | Real deployed baseline, fourteen financial sources, boundaries |
| Implementation | 12m | Policy, append-only journal, completeness UI |
| Tests | 8m | Backend/PG/frontend, fixture repair and review regression retries |
| Agent 6 / PR / Merge | 5m | Two P1 clusters closed; PR307; merge NOT RUN |
| Staging Deploy | N/A | Owner requires paused |
| Staging Acceptance | N/A | Replaced by isolated local proof per Owner |
| Production Backup / Build / Deploy / Acceptance | 7m | Full backup restore, identical preview, exact artifacts, gate permission retry; replacement operation59s |
| Evidence / Governance | 4m | Fresh audit, historical regression, current-state sync |
| Total | 43m Estimated | Bounded authorized task to final Stop |
