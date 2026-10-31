# Phase 6 M0 + M1 Implementation Tracker

Owner authorization: 2026-10-31, request SHA256 bbad4b500e02a61b5ac0115e0b795d544324d3c2bc4ea430796c5a228c8c4e11.
Fixed source base: 028d4971740889a0ac0d784a43497bc3e5b54b3d.
Branch: codex/template-m0-m1-test-20261031.
Research source: /Users/xuzilin/projects/Restaurant_System_Template_Research (read-only).
Target: https://test.lanzhounoodlesmtl.com (independent Test, not Staging).
PRIMARY_REPAIR_GOAL: Implement configurable menu groups/routing and real existing-order/Outbox/Job MOCK closure, then deploy Test.
EXPECTED_REPAIR_CLASS: HIGH (feature implementation, not an unrelated framework repair).

## Authority and boundaries

The 2026-10-31 Owner request supersedes Phase 2–5 NOT_AUTHORIZED planning statuses for M0/M1 and the minimum CR-MENU-001 / CR-PRINT-001 / CR-DIAG-001 seams. It does not approve PrintDispatcher internal refactoring, old Renderers, existing dedupe/state machine/PAD_DIRECT, Android, Production/Staging changes or real transports.
Current execution authority is docs/governance/CURRENT_STATE.yml in THIS implementation checkout. Historical runtime facts remain dated observations.

## Creation and source preservation

Managed worktree created at exact base, then immediately narrowed to the Research safe sparse set before source inspection/build: 1422 materialized, 370 excluded. No excluded dump/secret was read or used. Initial materialized fingerprint: 0e728e9dc2e150af149def0136d692a84ff80b5b28c3ed4193e85cc0429d41d5.
Original development workspace is dirty and protected; no reset/clean/stash/overwrite. Research remains unchanged.
Protected source bytes are recorded in PROTECTED_BASELINE.sha256; verify before each release batch.

## Task batches

| Batch / original Task IDs | Scope | Owner | Status | Validation |
| --- | --- | --- | --- | --- |
| B0 / B01,G01,E01 | Worktree, source protection, baseline, auth contract | Integration | COMPLETE | 147 protected hashes unchanged; golden/Outbox/Job baseline PASS |
| B1 / C01,C02,C03,C04,C05 | Group library, item copy, canonical leaf prices, server validation | Integration | IMPLEMENTED; integration ongoing | 20 menu/Bootstrap tests PASS; HTTP negative cases PASS |
| B2 / C06,C07,C08,H02 | Menu/POS controls, Quick Add, Combo reuse | Frontend | IMPLEMENTED; UI acceptance ongoing | 400 Vitest PASS; build PASS |
| B3 / D01,D03,D07 | Pure routing planner and management/preview | Planner + Frontend | IMPLEMENTED | 18 planner tests PASS; real preview/three group HTTP PASS |
| B4 / D04,D08,E04,H03 | Existing transactional Outbox/Job seam and durable warnings | Integration | IMPLEMENTED; final regression ongoing | 8 boundary tests PASS; initial 26 real HTTP cases PASS |
| B5 / G02,G03,G05 | Scope/money/snapshot/idempotency/legacy integration proof | Integration/reviewer | PENDING | NOT_RUN |
| B6 / Test | Isolated Compose, HTTPS vhost, Test fixture and deployment | Deployment + Integration | PREFLIGHT | DNS correct; HTTPS missing; no remote mutations |
| B7 / I02 | Domain login/business/recovery acceptance and Owner checklist | Integration | PENDING | NOT_RUN |

## Ownership

Integration Owner exclusively owns database migrations, shared backend entities/APIs, price/order transaction/Outbox/Job seams and commits.
Frontend worker owns frontend only. Planner worker owns new pure planner + its tests only. Deployment worker owns new deployment/test only.
Shared core changes are serial. No agent may revert another worker or edit the original/research checkout.

## CR impact record

- CR-MENU-001: additive menu-group metadata/library, explicit item group mode, existing item option leaves as the only runtime price authority, minimum catalog/validation seams. Legacy absent marker is unchanged.
- CR-PRINT-001: new frozen generic event in existing Outbox + independent handler using public JobService in MOCK only; legacy Dispatcher internals/Renderer/dedupe/Pad remain unchanged.
- CR-DIAG-001: existing Outbox context/error projection includes pre-Job intent; new scoped read endpoints and UI, no second queue or executable state machine.
- CR-HISTORY-001 remains excluded. No new complex price-window or general rules engine.

## Test environment preflight

DNS A 170.106.13.34 observed 2026-10-31; default HTTP response is not Test deployment proof. HTTPS SNI currently fails. Shared host has about 1.4Gi available RAM/12Gi disk; no server-side Maven/npm builds or existing cache/volume cleanup. Test resources must be bounded and admitted before startup.
No Production/Staging app/container/db/restart mutation authorized. Shared ingress changes limited to Test vhost backup/check/minimal reload under the explicit Owner exception.

## Batch evidence

Commands, real outcomes, commit/artifact SHA and Test environment identity are appended here per completed batch. NOT_RUN is never PASS.

### Implementation checkpoint — 2026-10-31

- Local independent PostgreSQL18 at 127.0.0.1:25432/restaurant_template_test; no preexisting DB used. Empty database Flyway V1–V40 and Hibernate validate PASS.
- Real template-test application launched at 127.0.0.1:28081 with new private credentials; RuntimeDataSeeder explicitly disabled. Owner/Manager and distinct-Organization negative fixture initialized. Credentials never emitted.
- Initial bootstrap failed because a legacy COMBO leaf was placed in new metadata. Corrected to call the existing Combo policy writer with metadata-only opt-in, and initialize existing Combo components first; no schema or expectation weakening.
- Focused backend command: JDK21 mvn -q -Dtest=KitchenRoutingPlannerTest,TemplateMenuServiceTest,OrderDispatchOutboxProcessorTest,PrintJobServiceImplTest,GrabReceiptRendererTest,HotKitchenReceiptRendererTest,NoSpicyPrintingRuleTest,DineInTicketHeaderTest test. 205 tests, 0 failures/errors/skips. Subsequent boundary/menu/order/pricing/Combo selected suite PASS; XML totals recorded in private evidence, not inferred from error-log text.
- Frontend: npm ci --ignore-scripts; npm test (400 PASS); npm run build PASS. Existing dependency audit findings retained, no dependency/lock upgrades.
- backend/test_template_platform_http.py initial real local HTTP run: 26 PASS / 0 FAIL (protected receipt http-local-first.json). Actual order/Outbox/Job records, replay, Update, pre-Job missing-printer and asynchronous MOCK FAILED/UNCERTAIN were queried after reload. More Combo/concurrency/browser cases pending.
- Independent deployment pre-review REJECT: certbot inherited hooks, Test resource ownership reentry, ingress concurrent-write/rollback CAS, relay readiness/port ownership. Author is repairing these; no remote deployment has occurred.
- Independent product pre-review found missing PRINTING effect gate and a customer-group/system-diagnostic identity collision. Fixed in the independent handler/new identity namespace and added exact negative regression tests; existing core algorithms unchanged.

Local private runtime and receipts are under .template-test-private/ (ignored, 0700; credentials0600). This is not a final deployment receipt or phase acceptance. Production/Staging apps, DBs, devices and APK have not been modified. Test remote creation/HTTPS remains pending passing review/build/test gates.

### Review repair checkpoint — 2026-10-31 05:01 UTC

- Product review of f3097b6: REJECT retained, then repaired in the new template boundary: unknown item IDs and category/SKU forgery fail closed; canonical leaf snapshots preserve validated Combo-side Remove parents; stable virtual Combo IDs require exact code/group. New path explicitly supports existing legacy COLD side task behavior and modifier-only eggs, rejecting unsupported NO_KITCHEN_TASK sides/general LINKED_MENU_ITEM or HOT-linked sides instead of producing inconsistent Tasks. Old Combo task writer is unchanged.
- Preview now passes the full existing option snapshot and validates the same managed menu selections as real submission. Child Remove is scoped to its selected linked side and must remain active/free.
- Test-specific CORS permits only the exact local and Test HTTPS origins; legacy CORS is excluded only under template-test. Live login with exact local Origin PASS. Pre-Flyway guard rejects any alternate Flyway datasource and enforces the isolated database/user, MOCK/DISABLED ceiling, endpoint prohibition and external-worker prohibition. It is a guard for the explicitly enabled Flyway Test profile, not a claim of arbitrary deployment sandboxing.
- GET /api/v1/stores/{store}/template/runtime-safety exposes only actual Test policy and nonsecret build identity to authorized Owner/Manager; the live bean proof is used by strict HTTP acceptance. New Compose binds APP_BUILD_SHA to image SHA.
- Four focused review test classes PASS before alternate-Flyway test addition; complete backend verify now running with only a minimal environment, all optional external Postgres test overrides unset. No skipped integration test will be called PASS.
- All 147 protected files remain byte-identical to fixed source baseline. Deployment-only review repairs and 42 pure safety tests are under independent rereview; no remote mutation yet.

Known scope limits: general Combo LINKED_MENU_ITEM behavior is unsupported by the new adapter while the mature task writer remains fixed COLD; custom zero-price groups appear in the new kitchen tickets but the protected legacy FRONTDESK renderer omits their individual instruction labels. Existing complete item/amount/tax/incremental receipt behavior is retained; this detail is reported PARTIAL, not silently rewritten or hidden in notes.
