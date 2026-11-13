# Allô Print Center test repair — 2026-11-13

PRIMARY_REPAIR_GOAL: Restore Android PAD_DIRECT connection diagnostics and Allô test/receipt printing; Owner confirms all current orders/queues are disposable test data. Keep pairing, printer configuration, lease/claim and duplicate-print safeguards.
EXPECTED_REPAIR_CLASS: MEDIUM

## Ground truth

- Started 2026-11-13T18:26:41Z. Fresh origin/main `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`.
- Clean Allô checkout at `d54fd08264c30d69443ca942a5157e05f9c531b1`; 2 main-only / 36 Allô-only commits. Protected other worktrees unchanged.
- Repair branch `codex/allo-printer-test-20261113`, Phase 6 / ALLO_ANDROID_APK_20261113.
- Contracts: `REQUIRED_CHANGE_REQUEST.md`, `SYSTEM_DOCUMENTATION.md` Android bridge and Print Center sections, `doc/API.md` Allô PAD intent/artifact policy.
- Owner authorized this bounded bug repair. A new immutable Production backend batch still requires approval per AGENTS.md §§8,15; historical b7893860 CORS approval is not a standing release authority.

## Root causes / minimum repair

Production logs at 13:21:41 and 13:22:24 Toronto show printer #3 test and FRONTDESK_RECEIPT assigned-module sample rejected at `markPadDirectQueued` with `RECEIPT_PAD_INTENT_REQUIRED`. Both jobs have no real order and never captured explicit PAD intent.

The connection button independently called the cloud endpoint, whose PAD_DIRECT branch intentionally refuses cloud-to-LAN probing. Android already exposes a socket-only native probe.

- Android UI reads the Store-authorized printer configuration and calls the existing native connection bridge in PAD_DIRECT; other modes preserve backend diagnostics. Cards show the local probe result scoped to Store and endpoint.
- New Allô diagnostic samples freeze a distinct server-owned `ALLO_PRINT_TEST_V1` intent and reuse existing artifact encoding/hash/current-mode checks. Diagnostic markers are rejected on real orders, mismatched Store/module or unknown sample sources. Historical missing/MOCK intent remains nonphysical.
- Actual printers #3/#4 use GBK, no code page, MEDIUM, 80mm. GBK cannot encode the Store display name's `ô`. New Allô PAD static diagnostics use stable Store code/Printer ID when those display names are not encodable; real receipt encoding/text remains unchanged. GBK tests use real GRAB and FRONTDESK renderers.
- No migration, new API, worker reset, pairing change, data clear, blind retry or physical test print.
- Product Simplicity: existing buttons; zero extra steps, states or repeated configuration.
- Small extension to existing artifact boundary is BLOCKING: real receipt context requires order/item snapshots and cannot safely represent an order-free sample. Deployment architecture/tooling improvements are NON_BLOCKING and deferred.

## Validation and release

- Focused backend: 126 tests PASS, zero failures/errors/skips (artifact/job/dispatcher/Pad/policy).
- Frontend: 87 files / 522 tests PASS; TypeScript PASS; diff check PASS.
- Agent 6: initial P2 GBK sample-name blocker closed; consolidated re-review PASS, no P0/P1/P2.
- Application exact SHA: `774034ef1f17428789c6f9ffad74648efa77d4a4`; PR [#314](https://github.com/Z1linXu/Restaurant_System/pull/314), based on the dedicated Allô actions branch, no main merge.
- JAR SHA256 `794ac20d07a0f5375db4d2e989616191b0eb2d969bafc59efb362c97816954f8`. Exact artifact isolated HTTP acceptance PASS: 4 order-free GBK diagnostic samples, claim/start/payload SHA/fail reports, sentinel 0 connections, orders unchanged, endpoint-free MOCK restored; one purpose-built protocol fixture revoked after use. No physical print.
- Android native unit 18 PASS; actual WebView→native bridge→IPv4 loopback probe with EOF/zero-byte assertion and independent sandbox checks 2 PASS. Initial test fixture bound IPv6 but probed IPv4; one-line androidTest-only correction and rerun passed without rebuilding application.
- Unchanged fresh-empty-login test failed on this reused QA AVD (expected 2 blank login inputs, actual 1). Fresh-fixture precondition was not met; failure retained, not counted as PASS. Earlier fresh-install authentication evidence remains applicable because auth runtime code is unchanged. Do not clear offline drafts merely to satisfy a fresh-fixture check.
- APK 1.0.3/code4, Debug signed with unchanged certificate; 53 bundled asset hashes verified. SHA256 `d081b52d2ed88328227072d335aebf183dfb21ed1401436fb273f935eea8796a`, 4,348,863 bytes; `dist/android/allo-nouilles-pos-v1.0.3-debug.apk`.
- Physical Pad `R5GL83EDH0M` upgraded in place. Five native prefs/IndexedDB payload hashes identical before first launch; pairing, drafts and outbox retained, no uninstall/clear/reset. Main Printer `192.168.0.184:9100` and Grab `192.168.0.121:9100` both showed native connection success (13:47 / 13:48 Toronto). Only connection buttons clicked, no print/worker recovery buttons. Existing last-print error is historical and remains preserved.
- Legacy Lanzhou identity/signing/defaults unchanged; all 51 original bundled assets hash-identical. No Lanzhou runtime access or mutation.
- Fresh Production backup: 87 tables restored/hash-matched, constraints/indexes valid, socket-only recovery cluster removed. Initial dump SHA256 `9adbbcefbe5a6b8cbe5bc25492464361ac9be9f539ada04c0433cdf60fbc403f`; not imported into QA.
- Production unchanged. Existing samples 44/45/46 remain PENDING with null execution mode/no artifact; no replay or mutation authorized.
- Governance full scan: FAIL on existing untracked dependency README link (`frontend/node_modules/jiti/README.md`); tracked archive scan: FAIL on existing unavailable ignored `.template-test-private/LOCAL_BROWSER_MOCK.png`. Both failures retained as NON_BLOCKING_EXISTING_DEBT; changed authority/files and diff checks validated separately. Do not repair unrelated tooling or manufacture evidence assets.

## Owner steering / same-root extension at 13:52 Toronto

Owner confirms all current orders and print queues are test data and need not be preserved. Bounded terminal cleanup of current Store1 test queues is authorized; destructive full-database reset, credential/pairing or printer configuration changes are unnecessary.

While verifying the physical connection, independent store activity created test orders 21/22 and a new sample 142. Frontdesk outbox retries hit `RECEIPT_ENCODING_UNSUPPORTED` on existing b789 runtime; the new static-sample fallback does not address real receipt branding. This is BLOCKING for the original usable-printing outcome: new Allô PAD receipt intents now freeze `Allo Nouilles` (ASCII o) as the physical brand; UI/MOCK names and historical contexts/artifacts remain unchanged. Old pending test intents will be cancelled/skipped, not rewritten or replayed.

- Consolidated Agent 6 review of new PAD branding + exact test-data cleanup SQL/helper: PASS, no P0/P1/P2. Outbox lock/claim transaction boundary verified; no new scheduler or stop framework.
- Extended backend acceptance: 198 tests PASS, zero failure/error/skip, including actual Frontdesk renderer text strictly GBK-encodable with the new frozen physical brand and original historical context replay unchanged.
- Fresh pre-cleanup backup SHA256 `50627c0f9e7540b770dc1dbf9d5ab435680dad37d38d2951c6505c3d998e2de0`, 87 tables restored/hash-matched in socket-only recovery; original dump retained. No test data imported into QA.

- Authorized cleanup executed transactionally: outbox 6 SKIPPED, pending jobs 5 CANCELLED, active test orders 4 cancelled (related kitchen tasks 7, beverage tasks 2). Exact SQL SHA256 `2749440ecddb5c357345135b2937cc76847cd25470a53c584f327465fdd08e83`; SYSTEM audit records the Owner instruction. No pairing/printer/master configuration mutation.
- Final backend source `4c75f960d1ad00a799c1d5054f22286149429447`, JAR SHA256 `c48bfc753aaa82672c603aa5ee030047cf218719fa88fe6dd3e6978c06bb6a66`. Exact artifact isolated HTTP PASS: 4 diagnostics + GRAB/FRONTDESK from one signed synthetic order, all 6 claim/start/payload-hash/fail reports; actual GBK brand, sentinel 0 connections. New QA order cancelled; original Store brand/config and endpoint-free MOCK restored; old orders unchanged. No real printer bytes sent by this acceptance.

### SCOPE_EXPANSION_CHECK

Continuous onsite testing created more orders after the first cleanup, so requiring an empty shop before merely staging an image would indefinitely block the repair. The release reuses the existing backend replacement stop boundary, runs the same reviewed test-data cleanup while submissions are stopped, then starts the exact accepted artifact. Roughly 35 lines of bounded private deployment adaptation are BLOCKING for this release; no new scheduler, credential lifecycle, deployment platform, cache cleanup or general acceptance framework is added. App-code changes remain bounded to the requested connection/intent and GBK repairs.

## Final production result

Owner explicitly approved the exact 4c75 batch once. At 2026-11-13T19:10:42.833342Z (14:10 Toronto), Backend `4c75f960d1ad00a799c1d5054f22286149429447` became healthy with image `sha256:7141585f9b545a69afe865a38a7577fca166bda5ab3cf7a9f3a7a9925c884804`. Backend container `20580de9e8f6e9546caa3cf0e7b16a8f7c53a4dae86e50f3fb9edf742305b866`. Frontend and DB container IDs/start times remained unchanged; V43 retained.

The approved replacement stop window cleared another 2 test outbox events, 1 pending test print job, 2 active test orders, 9 kitchen tasks and 5 beverage tasks. Together with the earlier cleanup: 8 outbox events, 6 pending jobs, 6 active orders, 16 kitchen tasks, 7 beverage tasks terminally cleared/cancelled. Completed history was not needed for the repair and remains in place; no database reset was performed. Static Store/printer assignments/configuration and device bindings matched before/after release. No new binding, credential reset, worker recovery or blind reprint.

Post-deploy read-only acceptance PASS at 19:11:28Z: 9 API checks, 5 exact-origin CORS checks, SockJS, 5 WSS denials and authenticated own-Store subscribe; cross-Store403 and anonymous401 preserved. Three public frontend artifact hashes unchanged. Current Store1 PAD_DIRECT, 2 devices, 0 active orders and 0 pending/claimed/printing/outbox work at the observation. Business-row differences reflect the explicitly authorized cleanup and onsite tests, not an assertion that all rows stayed unchanged. Expected negative WSS checks produce denial ERROR logs; no new intent/encoding errors were observed in the short post-deploy window.

Disk before preparation: 16% / 48GB free, Build Cache1.445GB. After: 17% / 47GB free, Cache1.78GB. No cleanup/prune; current/previous backend, frontend, DB and backups retained. All three runtime containers healthy.

Physical paper output on the new release was not proactively triggered. Actual LAN connectivity succeeded from the physical Pad; exact rendered bytes and protocol behavior were verified in isolated QA. Other Pads can use the delivered v1.0.3 APK for the corrected Android connection-test UI.

## Product repair / tooling and governance repair

| Area | Files | Why required / blocker |
| --- | --- | --- |
| PRODUCT REPAIR | frontend `PrintingSettingsPage.tsx`, `printerConnectionService.ts`, `androidPadBridge.ts` and focused tests | Authoritative Store printer config → native socket probe, scoped result display; original connection entry was wrong. BLOCKING. |
| PRODUCT REPAIR | backend `ReceiptArtifact.java`, `PrintJobServiceImpl.java`, `PrintDispatcherServiceImpl.java` and focused tests | Capture valid order-free diagnostic intent, retain frozen artifact/current-mode/encoding protection. BLOCKING. |
| PRODUCT REPAIR | `ReceiptContextService.java` and test | New physical brand encodable on actual GBK printers, no historical rewrite. BLOCKING. |
| PRODUCT DELIVERY | Android `app/build.gradle`, `AlloBundledLaunchTest.java`; ignored `dist/android` artifacts | Version1.0.3, emulator WebView probe and in-place physical upgrade. BLOCKING for delivered Android repair. |
| TOOLING / GOVERNANCE | Private reused Allô stage/activation/backup/recovery/QA helpers, exact cleanup SQL; `CURRENT_STATE.yml`, this evidence, release batch, `SYSTEM_DOCUMENTATION.md`, `doc/API.md` | Existing release stop boundary handles disposable concurrent tests; immutable artifact, backup, audit and behavior/evidence updates. BLOCKING safety/delivery proof. No new platform or general tooling. |

## Execution Time Breakdown

Stages are estimated allocations; total is wall clock, from 13:26:41 Toronto through final evidence sync. Reviews/builds overlap; retries are included.

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | Estimated12m | Log/request/native route investigation; current GBK and test-data steering. |
| Implementation | Estimated9m | Intent/native connection repair plus same-root physical-brand fix. |
| Tests | Estimated5m | Backend198, frontend522, Android18+2; GBK follow-up and IPv4 fixture rerun included. Fresh-empty-login fixture failure retained separately. |
| Agent 6 / PR / Merge | Estimated7m | Consolidated source/GBK and bounded release/cleanup reviews; PR314, no main merge. |
| Staging Deploy | Estimated1m | Isolated local QA exact-JAR launches only; shared Staging untouched. |
| Staging Acceptance | Estimated2m | Four-sample then six-ticket HTTP proof; zero sentinel connections, fixture restore. |
| Production Deploy / Smoke | Estimated2m | One backend replacement after approval and read-only checks. |
| Evidence / Governance | Estimated12m | Fresh87-table recovery proof, audit cleanup, exact release/artifact/evidence sync. |
| Total | Approximately50m wall clock | 2026-11-13 13:26:41 to approximately14:17 Toronto. |
