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

Consolidated validation, Agent6 and exact-SHA Staging acceptance PASS.
No Production deployment will follow this Package. Owner manual Staging
acceptance is the final gate; Android/iPad physical devices are not presumed.

Local final regression:171 backend cases,158 passed,13 existing conditional skips,
0failures/errors. Includes actual PostgreSQL16 new Finish16, controller3 and
prior soft-delete28. Frontend394/67files PASS; typecheck/build PASS. Changed
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

## Final application deployment and real acceptance

Application/backend/frontend SHA `a14cc4ce87ecaea17b31d86aa55aae76f1a79294`.
Exact reviewed application pair deployed2026-11-03T16:49:10Z. Both public health
checks PASS. Second deployment6m35; first6m35; no runtime failure/rollback.
V40 ledger unchanged on repair. Backup SHA256
`19b5dfa96312753c8de3431b810f8cd9d69edfcda1b63b86fe02066b0f6575ea`;
initial V39 backup retained, hash in initial-staging-deploy.json.
Rollback remains reviewed previous exact c1 app pair plus retained database;
no destructive V40 downgrade. Production promotion is expressly prohibited.

Evidence directory `docs/evidence/finish-all-tables-20261103/` contains sanitized
local-validation.json, initial-staging-deploy.json, integration-repair.json,
staging-deploy.json, acceptance-api.json, browser-cancel.json and
final-verification.json. Private credentials, environment, backups and session
files remain server0600; no secrets copied into repository.
`staging-deploy.json.accepted=false` is the immutable pre-acceptance deployment
checkpoint; subsequent acceptance-api/final-verification prove final PASS.

Acceptance script SHA `41e99907a85dc326427d9faffb61ff32b87b67f6` reuses exactly
identical backend/frontend trees to deployed a14. Drink submissions legitimately
become ready; corrected the helper's preparing-only assertion. That assertion
stopped before any Finish POST. Exact five interrupted fixtures were verified
(Store/type/table/state/notes), resumed once, and normal authenticated APIs used.
Agent6 acceptance-only re-review ACCEPT;4tools/syntax PASS. No application rebuild.

| Requirement | Proof / result |
| --- | --- |
| Single/multiple, submitted/preparing/ready | Actual PostgreSQL16 tests; real Stage three-order batch PASS |
| Split A/B | Stage98/99 and104/105 completed; parent1里 AVAILABLE, configuration unchanged |
| Cancel | Real browser Cancel; DB98/99/100 stillready,101draft,102ready; journal0 |
| Confirm | UI104/105/106: completed3/skipped0/failed0/released3; DB independently verified |
| Frozen set | Stage103 created after first preview stayedready until separately previewed |
| Retry/concurrency | Real same-token replay identical; PostgreSQL same/distinct Pad batch and timeout tests PASS |
| Partial failure | PostgreSQL injected per-order failure: prior successes commit, precise failed result, retry stable |
| Draft/outbox/editor | Frontend queued/submitting/conflict/retryable and unsaved tests PASS; Stage101 draft excluded, later explicitly cancelled via normal API |
| Empty board | Stage preview0; actual UI Confirm disabled, no active tables message |
| Store/type isolation | All existing Stage history hashes unchanged; Store18 pickup102 remainsready; crossStore/Uber/delivery tests PASS |
| History/money | Original rows/items/prints hashes unchanged; completed test order/item money identity unchanged |
| Printing | Finish job delta0; UI before/after250; no physical transport or PAD worker action |
| WebSocket | Second independent open page changed IN USE3→0/AVAILABLE1→3 without refresh after UI Confirm |
| Routes/layout | Base1440x900, /a1024x768, /b1280x800 show48px action; modal fits; screenshots retained |
| Audit | 3 committed batches,7 TABLE_FINISH_RESULT +3 TABLES_FINISHED, actor1/Store18/timestamps/order IDs |
| Runtime safety | Production3 and StageDB fingerprint equality; V40 unchanged; Stage health PASS |

Final states98/99/100/103/104/105/106 completed;101cancelled;102pickupready.
All test records remain for audit. Other pre-existing Stage records untouched.
No forced Completed, row deletion, outbox clearing or printing retry was used.

Known limits: physical Android Pad WebView/iPad devices NOT RUN; browser viewport
checks are not physical-device certification. Other Pads' local IndexedDB cannot
be inspected remotely; warning is explicit and their data is preserved when they
observe terminal state. 1024px page has a2px document edge overflow; action/dialog
remain entirely visible. No unrelated shell-layout repair was added.
Normal Submit for eight Coke fixtures created16 jobs before Finish:8MOCK receipt
PRINTED and8GRAB FAILED/RENDERED_CONTENT_BLANK. This is isolated fixture/printing
behavior, not an extra Finish dispatch; no printing code/config change or reprint.
Operational retained_printing aggregate consequently changed; configuration hashes
exclude that runtime aggregate, while all pre-existing print rows remain identical.
This is NON_BLOCKING_EXISTING_DEBT, not evidence of successful physical printing.

## Docker / Disk Hygiene

Before64% /21GiB free; after66% /20GiB free. Build Cache8.293GB before/after,
reclaimable1.576GB. No cache/image/volume/release cleanup performed; disk remains
below70%. Only own disposable bounded builders were removed. Existing current and
rollback images, database volumes, backups and private environment files retained.
Stage healthy; Production lifecycle/data mutation NONE. See final-verification.json.

## Product repair versus tooling / governance

PRODUCT REPAIR: backend preview/execute/V40 batch journal, shared frontend control,
offline terminal-conflict protection and relevant tests. These directly implement
confirmed Store-scoped Finish and prevent pending local work loss.
TOOLING / GOVERNANCE REPAIR: small Stage-only deploy wrapper around reviewed
lifecycle, exact ledger handling, bounded acceptance helper, current contracts and
sanitized evidence. Required to prove isolated migration/deployment and real API
behavior; no new deployment architecture or credential/config lifecycle.

PR306 remains open; branch codex/finish-all-tables-staging. No main merge and no
Production promotion. Final Gate OWNER; Stop FINISH_ALL_TABLES_STAGING_OWNER_ACCEPTANCE_PENDING.
