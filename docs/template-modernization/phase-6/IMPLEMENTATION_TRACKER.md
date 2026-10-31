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

| Batch / original Task IDs | Scope | Owner | Final status | Validation |
| --- | --- | --- | --- | --- |
| B0 / B01,G01,E01 | Worktree, source protection, baseline | Integration | COMPLETE | 147 protected files and 36 Research reports unchanged |
| B1 / C01–C05 | Library, item copies, leaf prices, validation | Integration | COMPLETE | Backend + real HTTP price/default/copy cases PASS |
| B2 / C06–C08,H02 | Menu/POS UI, Quick Add, Combo | Frontend | COMPLETE_WITH_LIMITATION | 401 Vitest; public Quick Add; Combo legacy COLD scope PARTIAL |
| B3 / D01,D03,D07 | Routing configuration and pure preview | Planner + Frontend | COMPLETE | Planner18; real 1/3/shared-device/category/item preview PASS |
| B4 / D04,D08,E04,H03 | Existing Outbox/Job seam, durable Warning | Integration | COMPLETE | Real MOCK jobs + pre-Job intents; public Order43 persistence |
| B5 / G02,G03,G05 | Scope, money, snapshots, idempotency, legacy proof | Integration/reviewer | COMPLETE | Final verify1310/1193 executed/117 skipped; local45/public45/SQL19 |
| B6 / Test | Isolated Compose, TLS, safe shared vhost | Deployment + Integration | COMPLETE | Guard62; HTTPS200; exact dbe207a; Flyway40; old8 unchanged |
| B7 / I02 | Public login/business/failure checks and Owner checklist | Integration | TECHNICAL_COMPLETE_OWNER_PENDING | Root public browser smoke PASS; Owner checklist OWNER_NOT_RUN |

## Ownership

Integration Owner exclusively owns database migrations, shared backend entities/APIs, price/order transaction/Outbox/Job seams and commits.
Frontend worker owns frontend only. Planner worker owns new pure planner + its tests only. Deployment worker owns new deployment/test only.
Shared core changes are serial. No agent may revert another worker or edit the original/research checkout.

## CR impact record

- CR-MENU-001: additive menu-group metadata/library, explicit item group mode, existing item option leaves as the only runtime price authority, minimum catalog/validation seams. Legacy absent marker is unchanged.
- CR-PRINT-001: new frozen generic event in existing Outbox + independent handler using public JobService in MOCK only; legacy Dispatcher internals/Renderer/dedupe/Pad remain unchanged.
- CR-DIAG-001: existing Outbox context/error projection includes pre-Job intent; new scoped read endpoints and UI, no second queue or executable state machine.
- CR-HISTORY-001 remains excluded. No new complex price-window or general rules engine.

## Historical initial Test environment preflight (superseded by final evidence)

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

### Integrated regression checkpoint — 2026-10-31 05:03 UTC

- Full JDK21 `mvn -q verify` initially ran 1303 tests with 20 fixture/context errors (0 assertion failures): isolated Order JPA slice lacked new optional-feature dependency mocks, Addon hand-built H2 schema lacked the V40 marker column, and existing MenuRevisionLock slice lacked its preexisting realtime publisher mock. Repaired only fixture bean/schema setup, preserving all business assertions. Second full verify exit0: 1303 total, 1186 executed successful, 117 conditional external-Postgres tests skipped, 0 failures/errors. Original failing log preserved.
- Frontend full suite: 71 files / 401 tests PASS, build PASS. Real local strict HTTP: 40/40 PASS, receipt http-local-review-complete.json; browser exact-Origin login and Spring Roll Quick Add without modal PASS. Final clean candidate adds a 41st null-linked Combo negative case; not yet called PASS.
- Deployment Agent6 ACCEPT (11 files, 42/42 safety tests), frozen tree digest e4d3bed68e1220f4e8d1d10cb9dd59a38fa2fe1d3653c1655fdcfe9dc876a8d6. Product rereview found one remaining null-linked legacy-side gap; fixed by requiring a real linked COLD side at the new adapter boundary, with a focused regression and a real HTTP negative case. Mature Task logic unchanged.
- Source freeze to build exact candidate follows this checkpoint. Remote deployment and domain acceptance remain NOT_RUN until actual execution.

### Test deployment repair — 2026-10-31 05:09 UTC

- b64cc7ed product + HTTP runner independently ACCEPTED; final clean real-JAR HTTP 41/41 PASS, scoped read-only PG supplement 13/13 PASS. Jar SHA256 2e2716739259da8b04f8693dffe166adedad43128ad31c9ad12b4f3bdd4b6624. Local browser Owner login, no-group Quick Add and local-draft refresh persistence observed.
- Draft PR #304 created for review, not merged. Its base is current main80e888fc, while implementation remains pinned to Owner's028d497 source; no main merge or deployment used. GitHub reports conflicts against newer main; resolving them is outside this isolated Test release.
- First remote Test startup failed before healthy containers: three YAML inline tmpfs values split at the unquoted comma, yielding an invalid mode=1777 mount path. The failure receipt is retained. Shared ingress remained untouched; existing apps were not restarted. Repair only quotes the full three mount strings, validates parsed tmpfs contract and adds six regression cases. Guard tests now48/48 PASS; independent deployment rereview pending. No product Java/Frontend change.
- Reuse the verified exact JAR for the deployment-only repair (runtime sources/resources/pom byte-identical); bind the next candidate SHA in the frontend build and runtime environment. Existing failed Test release/images remain as evidence, no cleanup or volume deletion.

### Docker host-publish compatibility repair — 2026-10-31 05:18 UTC

- Second startup: all four new Test containers running, PostgreSQL healthy, Flyway40/guarded synthetic Stores1/2 initialized; backend65s startup. Actual host health failed because Docker did not publish the frontend port while it had only an internal bridge (NetworkSettings.Ports8080=null). No shared ingress changed. Preserve failed log and resources; stopped only the four verified new Test containers, without deleting volume/data, to release their allocated memory before retry.
- Minimum topology repair: Backend/DB remain on the project-owned internal bridge with no host ports. Frontend additionally joins a new project-owned Test-only ingress bridge and still publishes only127.0.0.1:28080; only that exact Test frontend may join it. Relay remains host-bound to the old edge gateway, allows only the observed old edge IP and forwards to Test loopback. No shared-network join. Frontend has no secret env and a fixed backend-only upstream. Backend/DB retain no external egress.
-59/59 safety tests and real Compose-config normalization PASS. Independent Agent6 deployment rereview ACCEPT tree f39ba7cfc5f492ce6f3e86a1f1d77c58c5113239ac447ef801163768baee6efd. Product Java/Frontend unchanged from accepted b64cc7ed; verified JAR reuse.
- Known advanced-API limitation found during bounded review: normal UI library/item copies never send source_option_id and are independent. Manually importing a legacy canonical Remove with raw source_option_id retains canonical refs; later StoreRemove master rename/reorder can propagate to it. This unsupported advanced import path is not advertised as independent; record P2 before any future import UI, rather than silently claiming all raw API imports are covered.

### Isolated Test live proof and ingress-only repair — 2026-10-31 05:30 UTC

- Frontend host publish actual200 and exact1a07e1f source marker verified. A reused/stopped Test DB container lost its db network alias; DB was healthy and TCP password contract matched, but backend DNS failed. Only the four owned Test containers were recreated with the same sealed images, configuration, credentials and persistent DB volume; no rebuild/volume deletion. Backend getent db then succeeded; protected eight old containers retained identity. Actual healthy runtime observed2026-10-31T05:26:21Z.
- Live remote proof: actual container JAR2e2716739259da8b04f8693dffe166adedad43128ad31c9ad12b4f3bdd4b6624, build1a07e1f, four Test containers running, all forty exact Flyway scripts success. Owner/Manager login PASS; anonymous401, cross-Org403; actual Test policy bean MOCK/DISABLED with endpointsfalse.
- First shared-edge precheck failed before any source/effective write: nginx accepted pipe syntax then reported no events section for /dev/stdin. Both actual hashes remained originalb9d6c2618d96b1c0710df4a6346c63ec06b23770f4dde0724adaa6272e27b2e0, no Test marker. Fix limited to validation using a0700 private tmpdir/0600 regular candidate inside existing edge, exact cleanup and preserved failure status.62 local guard tests PASS; independent ops-only rereview pending.
- Operational tool commit/hash is intentionally separate from sealed App1a07e1f. Upload reviewed helper into its own /srv/restaurant-pos/test/operations/<ToolSHA>/, verify bytes, keep sealed release unchanged, run it against validated App1a. No app rebuild/restart solely for this helper-only change; no protected printing core change.

### Public acceptance and new security repair — 2026-10-31 05:39 UTC

- Reviewed ops helperf6fb166 real nginx check/HTTP staging entry/certificate/Test HTTPS PASS. Old vhost prefix preserved, old8 container identity and old entrance200 retained; newTest /ws403 and /actuator/env404. Valid TLS chain/hostname, certificate expiry2027-01-29. App1a public41/41 HTTP PASS; remote scoped read-only SQL19/19 PASS with devices/endpoints/Uber0 and exactly2 synthetic users. All receipts retained.
- Actual IAB Owner login found preexisting P1 P6-SEC-OVERVIEW-001: own-store overview included foreign Org/Store arrays. Direct HTTPS proof confirmed2/2 arrays for Org1-only Owner; no foreign template rows existed in Test. Stop claiming tenant isolation complete: repair the response boundary using existing accessible Store/Org scopes, preserve ADMIN and same-Org authorized multi-store semantics. This is a security defect, not a customer-config feature. No Production patch.
- Minimal Controller filter plus6 semantic regression cases and4 direct HTTP cases (next suite45). First focused new serialization fixture2 errors fixed by registering existing Java time modules; original failure log preserved, assertions unchanged. Full verify/independent rereview/new exactTest candidate in progress. Protected printing/Android bytes unchanged; no schema or old data migration.

## Final delivery — 2026-10-31

Actual Test app SHA dbe207a1cc62256c7173108ccc375499f1e16caf, Backend JAR77b2204e05af8a828810fed3f42590f432ddeb47a7e70e1239945cb7b41d1362. Final documentation commit is separate and is never substituted for this deployed SHA. Full evidence, feature states, environment/ingress safety and remaining UNKNOWN: [RELEASE_EVIDENCE.md](RELEASE_EVIDENCE.md).

- Product Agent6 ACCEPT final dbe207a; deployment independent Agent6 ACCEPT62 guards. Source frozen after these reviews.
- Final local and public HTTP45/45 each, remote Test SELECT-only proof19/19. Final public TLS/health/API/source marker checked06:07:31 UTC; wrong Host/SNI421, /ws403.
- Public browser created no-group item14 and submitted real Order43; A/B MOCK PRINTED, C UNRESOLVED/pre-Job intent67/no Job. Reload plus signout/relogin retained the same error. C binding restored; old intent intentionally unchanged. Frontdesk includes all3 items. Root smoke is not Owner acceptance.
- Two product limits remain PARTIAL: supported legacy COLD Combo scope; zero-price arbitrary CUSTOM detail omitted by protected legacy frontdesk renderer. Future CR-RECEIPT-CUSTOM-001 is proposed, unauthorized and unimplemented. No notes workaround.
- Existing8 app/container identities, images, StartedAt and restart counts remain equal. Shared edge only appended Test vhost under explicit authorization with backup/check/two graceful reloads. Old certificates/status unchanged. No Production/Staging app deployment, DB change, device or Android mutation.
- Research36 reports unchanged; original dirty workspace untouched. Local temporary PG/Java/preview stopped; public Test remains running. No cache prune or release/volume cleanup performed; observed cache decrease has UNKNOWN cause.

STOP: await Owner review. No merge, M2, next phase, Production/Staging deployment or protected-core change authorized.
