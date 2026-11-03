# Finish All Tables — Staging implementation and acceptance

PRIMARY_REPAIR_GOAL: Store-scoped confirmed bulk Finish with frozen target set,
accurate partial outcomes and offline protection. EXPECTED_REPAIR_CLASS: HIGH.
Start2026-11-03T16:13:52Z. Fresh main80e888fcdad74e98326fcbeaa5df14bc0d9baed6.
Owner authorizes implementation, tests, required Agent6 review, PR, exact-SHA
Staging deployment and synthetic API acceptance. Production mutation/restart,
real orders/printing and main auto-merge are not part of this delivery.
Original Owner workspace and uncommitted Uber investigation remain protected.
Managed worktree finish-all-tables-3276, branch codex/finish-all-tables-staging.

## Minimum scope and implementation

- New order/finish DTO/service and controller. GET preview is signed and read-only;
  POST confirms exactly that set. Reuse completeOrder, Store access, ORDER_COMPLETE,
  ORDERING_POS and existing order.completed WebSocket fanout. No new finish logic.
- V40 adds only a batch journal; plan+outcomes are durable. Each per-order
  REQUIRES_NEW transaction locks batch then order and refreshes OSIV state;
  Finish, mandatory audit and result commit atomically. Retry/concurrency never
  repeats a completed/skipped/failed entry. Process interruption resumes prefix.
- Fingerprint Store/type/table/revision/submission/amount; status is revalidated,
  allowing normal kitchen progress but excluding edits made after preview.
- Frontdesk top operation area and ordering workspace share the same minimal
  component for base/A/B routes. Accessible responsive portal, explicit Confirm,
  loading/double-click lock, accurate partial results, return/refresh table board.
- IndexedDB is inspected read-only before preview and confirmation. Pending outbox
  and dirty updates block, unsubmitted new drafts warn and remain. Current editor
  changes block the action. Only this Pad's unsynced work is observable.
- Necessary offline safety repair: remote terminal reconciliation retains pending
  payload/items as CONFLICT and does not bind old work to a newly created order
  on the same table. Repeated terminal observations do not re-publish conflicts.
- Use existing table display rules and conservative whole/split occupancy overlap;
  do not change dining_tables, split modes, order money, printing or PAD worker.

Product Simplicity: one button and one confirmation, no user-facing batch lifecycle
configuration. Existing draft/outbox warnings and conflict recovery are reused.
No new printing capability, authorization role or Store-wide setting.

## Runtime preflight

2026-11-03T16:14Z read-only: Stage backend/nginx028d497... existing bf09/af9a
image pair; independent PostgreSQL16, target project restaurant-pos-staging.
Stage Store18 CHINATOWN synthetic fixture has two split tables1里/1外 and
single2, no active dine-in orders; nine preparing pickup fixtures are protected.
Production cloud backend/nginx still028d497, container identities/starttimes
retained. No Production business API or data-write command is used.
Disk64% /21GiB free; Build Cache8.293GB/reclaimable1.576GB. No cleanup needed.

## Tooling boundary

BLOCKING deployment adaptation only: small Staging-only wrapper around the
reviewed dine-in-headers deploy lifecycle, pins current pair and permits exact
V39→V40 append. Retains original backup, bounded build, Compose/environment,
protected runtime fingerprints, app-pair rollback and history guards. No new
release lifecycle. It has no Production target argument and rejects other DBs.
Tests cover exact ledger append, baseline, SHA and Staging-only dispatch.

## Validation and acceptance

Pending final consolidated validation, Agent6 and exact-SHA Staging acceptance.
No Production deployment will follow this Package. Owner manual Staging
acceptance is the final gate; Android/iPad physical devices are not presumed.

Local final regression:171 backend cases,158 passed,13 existing conditional skips,
0failures/errors. Includes actual PostgreSQL16 new Finish16, controller3 and
prior soft-delete28. Frontend393/67files PASS; typecheck/build PASS. Changed
lint0errors, one pre-existing useDraftOrder catalogItems dependency warning.
Deployment tests4new+8existing PASS. Agent6 consolidated review ACCEPT, no blockingP0/P1/P2; independent frontend28/tool12 PASS.
Tooling metadata now reports actual final Flyway version instead of hardcoded39.
No unrelated baseline warning/config/cache cleanup enters this Package.

## Changed files

- `SYSTEM_DOCUMENTATION.md`
- `backend/src/main/java/com/restaurant/system/order/controller/FinishAllTablesController.java`
- `backend/src/main/java/com/restaurant/system/order/finish/FinishAllTablesResponse.java`
- `backend/src/main/java/com/restaurant/system/order/finish/FinishAllTablesService.java`
- `backend/src/main/java/com/restaurant/system/station/service/DiningTableDisplayService.java`
- `backend/src/main/resources/db/migration/V40__order_finish_batches.sql`
- `backend/src/test/java/com/restaurant/system/order/finish/FinishAllTablesControllerTest.java`
- `backend/src/test/java/com/restaurant/system/order/finish/FinishAllTablesPostgresTest.java`
- `deployment/cloud/dine-in-headers/deploy.py`
- `deployment/cloud/finish-all-tables/acceptance.py`
- `deployment/cloud/finish-all-tables/deploy-staging.py`
- `deployment/cloud/finish-all-tables/test_deploy.py`
- `doc/API.md`
- `docs/FINISH_ALL_TABLES_STAGING_20261103.md`
- `docs/governance/CURRENT_STATE.yml`
- `frontend/src/features/dinein/DineInPage.tsx`
- `frontend/src/features/dinein/components/DineInTopBar.tsx`
- `frontend/src/features/dinein/components/FinishAllTablesButton.test.tsx`
- `frontend/src/features/dinein/components/FinishAllTablesButton.tsx`
- `frontend/src/features/ordering/OrderingPage.tsx`
- `frontend/src/hooks/useDraftOrder.finishProtection.test.tsx`
- `frontend/src/hooks/useDraftOrder.ts`
- `frontend/src/offline/localDrafts.ts`
- `frontend/src/offline/orderLifecycle.test.ts`
- `frontend/src/offline/orderLifecycle.ts`
- `frontend/src/services/finishAllTablesService.ts`

Application/review source: `c1e4981a85831cadb9785d98a80375f47443e50f`.
PR #306 is open for Owner review; no main merge or Production promotion.
Staging build began16:32:37 UTC; fresh custom backup exists. No deployment
retry, runtime failure or rollback has occurred at this checkpoint.

## First Staging acceptance repair

First deploymentc1e4981 completed16:39:12Z (6m35), V39→V40, health and
protected runtime/config/history PASS. No lifecycle failure or rollback.
Actual browser auth/me permissions are enum names: ORDER_COMPLETE, not the
Capability.getCode value order:complete. The button was hidden; fixed frontend
and positive/negative contract fixtures. Actual Store18 menu types are drink/
menu_item/main, so the acceptance selector's SINGLE assumption returned no row.
It stopped before creating any order; lookup now preserves the actual menu type.
Frontend394PASS/buildPASS; bounded Agent6 re-review ACCEPT, independent12tool/
12component PASS. Backend/schema product trees unchanged.

One necessary application-pair rebuild/redeploy follows the standard reviewed
lifecycle rather than introducing a new frontend-only release framework. Repair
pins the actual c1e4981 pair and requires the complete V40 ledger unchanged.
Shared release metadata now derives both before/after versions, avoiding a false
V39_TO_V40 label on a V40→V40 application repair. Old evidence stays intact.
