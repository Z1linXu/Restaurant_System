# Pad App PR2-PR8 Execution Prompt Plan

This document plans the follow-up PRs after `doc/PAD_APP_ARCHITECTURE.md`.

Scope of this document: planning only. It does not implement Android, Capacitor, backend APIs, database migrations, frontend runtime changes, or printing behavior changes.

## 1. Overall Execution Principles

- Each PR must be small and reviewable.
- Each PR must be independently testable and independently revertible.
- Do not combine Android, backend, frontend, database, and printing execution changes into one large PR.
- Existing local Web + Backend execution must remain usable after every PR.
- Existing `MOCK`, `REAL`, and `DISABLED` printing modes must not be broken.
- Printing failure must never roll back an order.
- `PAD_DIRECT` is an added print execution mode. It does not replace existing modes.
- `restaurant-pad-app` is an independent project and must not pollute the current `Restaurant_System` frontend/backend codebase.
- First verify Android Pad TCP printing before connecting full order printing.
- Multi-Pad duplicate printing must be prevented by claim / lease.
- Every store-scoped API must enforce backend store access. URL `storeId` is never trusted as permission.
- Never hardcode developer computer IP, printer IP, or API base URL in source code.
- Runtime config, pairing, store/device binding, and printer config must be explicit and inspectable.
- Owner/Admin operational visibility must remain available through Print Center.
- Manual reprint must remain separate from automatic update tickets.

## 2. PR2-PR8 Overview

| PR | Name | Goal | Code? | DB? | Backend? | Frontend? | Android? | Existing local run impact | Depends on | Acceptance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PR2 | `restaurant-pad-app` skeleton | Create independent Pad App skeleton only | Yes, in new project only | No | No | No current frontend changes | Yes, skeleton only | None | PR1 | Skeleton exists, docs clear, no business integration |
| PR3 | WebView loads frontend dist | Load current React production build in Pad shell | Yes, Pad project only | No | No | No current frontend source changes | Yes | None | PR2 | Login page and store routes load from bundled dist |
| PR4 | Native TCP printer plugin test print | Prove Android can connect and print to LAN ESC/POS | Yes, Pad project only | No | No | No current frontend source changes | Yes | None | PR3 | Test connection and test receipt print from Pad |
| PR5 | Backend `PAD_DIRECT` mode | Add server mode that creates rendered jobs without socket printing | Yes | No or nullable only if needed | Yes | Minimal Print Center label if needed | No | Should remain compatible | PR1, PR4 recommended | Existing modes unchanged; `PAD_DIRECT` jobs stay pending |
| PR6 | Device + claim APIs | Add device registration, heartbeat, claim, payload, complete, fail, release | Yes | Yes | Yes | No or minimal docs/client later | No | Should remain compatible | PR5 | Atomic claim prevents duplicate printing |
| PR7 | Print Center device/claim status | Show Pad devices, claimed jobs, stale jobs, force release | Yes | No or minor API additions | Yes | Yes | No | Print Center only | PR6 | Operators can see and manage Pad print status |
| PR8 | Cloud config + pairing flow | Production-ready API base, pairing code, device binding docs/UI | Yes | Maybe pairing fields/table | Yes | Yes in Pad/Admin | Yes | Should remain compatible | PR6, PR7 | Pad can bind to store without hardcoded URLs/IPs |

## 3. Dependency And Parallelization Rules

Recommended order:

1. PR2
2. PR3
3. PR4
4. PR5
5. PR6
6. PR7
7. PR8

Possible parallel work:

- PR2 and PR5 can be planned in parallel, but PR5 should not be merged before PR1 architecture is accepted.
- PR3 depends on PR2.
- PR4 depends on PR3 because the test UI/bridge should live inside the Pad shell.
- PR6 depends on PR5 because claim APIs need a `PAD_DIRECT` job source.
- PR7 depends on PR6 because Print Center needs device/claim data.
- PR8 depends on PR6 and PR7 because pairing/device management needs the core device model.

High-risk PRs:

- PR4: Android printer behavior is hardware/network dependent.
- PR5: printing mode branching touches order-triggered printing.
- PR6: claim/lease concurrency and device security are production-critical.
- PR8: pairing/security/config can accidentally expose device access if too loose.

## 4. PR2: `restaurant-pad-app` Skeleton

### PR Title

`PR2: Add independent restaurant-pad-app skeleton`

### Branch Name

`codex/pad-app-skeleton`

### Objective

Create a new independent `restaurant-pad-app` project skeleton that is structurally ready for an Android Pad shell, without loading the real frontend, without backend integration, and without printing code.

### Context

PR1 established that the Android Pad shell should remain independent from `Restaurant_System`. PR2 creates only the skeleton and documentation for that future app. It must not affect current Web + Backend runtime.

### Allowed Changes

- Add a new independent project directory or document an external repo structure.
- Add placeholder app files only if this PR is explicitly allowed to create the skeleton.
- Add `README_PAD_APP.md` or equivalent docs.
- Add local development instructions.
- Add `.gitignore` for the Pad project if the skeleton is in-repo.

### Forbidden Changes

- Do not modify current `frontend/src`.
- Do not modify current `backend/src`.
- Do not modify current package files or lockfiles unless this PR intentionally creates an independent Pad project package in its own directory.
- Do not add printing logic.
- Do not add backend APIs.
- Do not add `PAD_DIRECT`.
- Do not connect to `print_jobs`.
- Do not change current `Restaurant_System` startup behavior.

### Expected Files To Add/Modify

If kept in a separate repository:

- `restaurant-pad-app/README_PAD_APP.md`
- `restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md`
- `restaurant-pad-app/docs/ARCHITECTURE.md`

If temporarily kept under current repo:

- `restaurant-pad-app/README_PAD_APP.md`
- `restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md`
- `restaurant-pad-app/docs/ARCHITECTURE.md`
- `restaurant-pad-app/.gitignore`

### Backend Changes

Not in scope.

### Frontend Changes

Not in scope for current `Restaurant_System` frontend.

### Android / Pad App Changes

Skeleton only. Recommended structure:

```text
restaurant-pad-app/
  android/
  app/
  web/
  plugins/
  docs/
```

The first app screen may be a placeholder page that says the Pad shell is not yet connected.

### DB / Migration Changes

Not in scope.

### API Changes

Not in scope.

### Tests

- Verify current `Restaurant_System` still starts the same way.
- Verify no backend/frontend files changed.
- If a placeholder Android project exists, verify it can be opened in Android Studio or at least passes directory review.

### Manual QA

- Open the new README and confirm setup instructions are understandable.
- Confirm there is no real printer code.
- Confirm current Web app still runs as before.

### Acceptance Criteria

- Independent skeleton exists.
- Current `Restaurant_System` runtime is untouched.
- No real printing or backend integration exists.
- Docs explain why the project is independent.

### Rollback Plan

Delete the `restaurant-pad-app` skeleton directory or revert the PR. No data or runtime migration is involved.

### Risks

- Accidentally creating a monorepo dependency that affects current frontend builds.
- Accidentally committing generated Android/Gradle artifacts.
- Creating the impression that printing is already implemented.

### Final Codex Prompt

```text
Please implement PR2: independent restaurant-pad-app skeleton.

Strict scope:
- Create only an independent Pad App skeleton.
- Do not modify Restaurant_System backend runtime code.
- Do not modify Restaurant_System frontend runtime code.
- Do not install dependencies unless the skeleton setup explicitly requires it and I confirm.
- Do not implement printing.
- Do not implement backend APIs.
- Do not add PAD_DIRECT.

Before coding:
1. Run git status.
2. Confirm whether the working tree has existing changes.
3. Read doc/PAD_APP_ARCHITECTURE.md and doc/PAD_APP_PR_PROMPTS.md.

Create a skeleton structure:
restaurant-pad-app/
  android/
  app/
  web/
  plugins/
  docs/

Add docs:
- restaurant-pad-app/README_PAD_APP.md
- restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md
- restaurant-pad-app/docs/ARCHITECTURE.md

The README must explain:
- This is independent from Restaurant_System.
- No real printing exists yet.
- Current Restaurant_System local run is unchanged.
- Future PR3 will load frontend dist.
- Future PR4 will add native TCP test printing.

Forbidden:
- No backend/src changes.
- No frontend/src changes.
- No package.json or lockfile changes in Restaurant_System.
- No database migrations.
- No Android printing implementation.

Validation:
- git diff --name-only
- git status --short
- git diff --check
- Confirm only allowed skeleton/docs files changed.

Output:
- Files added/changed.
- Why this does not affect current local run.
- Next PR recommendation.
```

## 5. PR3: Android WebView Loads Current Frontend Dist

### PR Title

`PR3: Load Restaurant_System frontend build in Pad WebView`

### Branch Name

`codex/pad-webview-frontend-dist`

### Objective

Make `restaurant-pad-app` load the existing React frontend production build without copying frontend source code.

### Context

The current frontend uses relative `/api/v1` and path-based routes. A bundled WebView needs index fallback and runtime API base config because it cannot rely on Vite proxy.

### Allowed Changes

- Modify only `restaurant-pad-app`.
- Add a local `web/dist` placeholder or artifact copy instruction.
- Add WebView / Capacitor loading behavior.
- Add runtime API base config mechanism.
- Add debug-only HTTP cleartext handling where appropriate.

### Forbidden Changes

- Do not modify current `frontend/src` unless explicitly approved.
- Do not copy frontend source into Pad app.
- Do not implement native printing.
- Do not connect print jobs.
- Do not add backend `PAD_DIRECT`.

### Expected Files To Add/Modify

- `restaurant-pad-app/app/...`
- `restaurant-pad-app/android/...`
- `restaurant-pad-app/web/README.md`
- `restaurant-pad-app/docs/API_BASE_CONFIG.md`
- `restaurant-pad-app/docs/WEBVIEW_ROUTING.md`

### Backend Changes

Not in scope.

### Frontend Changes

Not in scope for current frontend source. The Pad App consumes the built `dist` artifact.

### Android / Pad App Changes

- Load bundled frontend `dist`.
- Ensure unknown paths route to `index.html`.
- Persist runtime API base config.
- Inject API base into WebView, for example:
  - `window.__RESTAURANT_API_BASE_URL__`
  - Capacitor Preferences
  - WebView request rewriting

### DB / Migration Changes

Not in scope.

### API Changes

Not in scope.

### Tests

- Build current frontend with `npm run build`.
- Copy or reference `dist` as POC artifact.
- Open login page in Android shell.
- Navigate to `/stores/:storeId/frontdesk`.
- Verify deep route fallback.

### Manual QA

- Configure API base as `http://{developer-lan-ip}:8080`.
- Login through Android WebView.
- Refresh/deep-link to store route without white screen.
- Verify normal Web browser version still works.

### Acceptance Criteria

- Android shell loads login page.
- Store workspace route loads.
- API base is not hardcoded.
- Relative `/api` problem is addressed by runtime config or documented bridge.
- Current Web app is unaffected.

### Rollback Plan

Revert Pad app changes. No backend/frontend runtime change to roll back.

### Risks

- WebView path routing may white-screen on deep links.
- API base injection may not be picked up by existing frontend without a later small frontend adapter.
- LocalStorage/token behavior may differ from desktop browser.
- Debug HTTP may leak into production if not isolated.

### Final Codex Prompt

```text
Please implement PR3: Android WebView loads current Restaurant_System frontend dist.

Strict scope:
- Work only in restaurant-pad-app.
- Do not modify Restaurant_System backend.
- Do not modify Restaurant_System frontend source unless I explicitly confirm a tiny API-base adapter.
- Do not implement printing.
- Do not implement PAD_DIRECT.
- Do not connect print_jobs.

Before coding:
1. Run git status.
2. Read doc/PAD_APP_ARCHITECTURE.md and doc/PAD_APP_PR_PROMPTS.md.
3. Inspect current frontend build output assumptions and Vite dist behavior.

Implement:
- WebView or Capacitor shell loading bundled frontend dist.
- Documented manual copy path from Restaurant_System/frontend/dist to restaurant-pad-app/web/dist.
- Path-based routing fallback to index.html.
- Runtime API base config, not hardcoded.
- Local debug API example: http://{developer-lan-ip}:8080.
- Production API placeholder: https://api.example.com.
- Debug-only cleartext HTTP configuration if Android requires it.

Validation:
- Build Restaurant_System frontend.
- Load login page in Android shell.
- Navigate to /stores/1/frontdesk.
- Verify no white screen on deep route.
- Verify API base can be changed without rebuilding APK.
- Confirm current browser Web app still works.

Output:
- Files changed.
- Runtime API base design.
- WebView routing fallback design.
- Known limitations.
- Manual QA results.
```

## 6. PR4: Android Native TCP Printer Plugin Test Print

### PR Title

`PR4: Add Android native TCP printer plugin test print`

### Branch Name

`codex/pad-native-printer-test`

### Objective

Verify that Android Pad can connect directly to LAN ESC/POS printers and print test output before any order/print job integration.

### Context

The backend currently prints via Java TCP socket, but Android must be proven separately on real store Wi-Fi. This PR is hardware POC, not production print workflow.

### Allowed Changes

- Add native PrinterPlugin in `restaurant-pad-app`.
- Add Pad-only test UI for IP, port, timeout, test connection, and test receipt.
- Add docs for printer network prerequisites.

### Forbidden Changes

- Do not modify backend order or print dispatch.
- Do not connect to `print_jobs`.
- Do not add `PAD_DIRECT`.
- Do not implement receipt business logic in Android.
- Do not modify current Web Print Center.

### Expected Files To Add/Modify

- `restaurant-pad-app/plugins/printer/...`
- `restaurant-pad-app/app/.../PrinterTestPage`
- `restaurant-pad-app/docs/PRINTER_PLUGIN_POC.md`

### Backend Changes

Not in scope.

### Frontend Changes

Not in scope for current `Restaurant_System` frontend. Pad app may have a local test UI.

### Android / Pad App Changes

Implement plugin methods:

```text
testConnection(ip, port, timeoutMs)
printRawTcp(ip, port, payloadBase64, timeoutMs)
```

Native behavior:

- Use background thread / coroutine, not UI thread.
- Open TCP socket with timeout.
- Decode base64 payload.
- Write bytes.
- Flush and close socket.
- Return structured errors:
  - `TIMEOUT`
  - `CONNECTION_REFUSED`
  - `UNREACHABLE`
  - `WRITE_FAILED`
  - `UNKNOWN`

### DB / Migration Changes

Not in scope.

### API Changes

Not in scope.

### Tests

- Unit-level plugin error mapping where possible.
- Manual hardware test with real printer.

### Manual QA

- Pad and printer on same LAN.
- Printer fixed IP.
- Router AP isolation disabled.
- Test connection succeeds.
- Print test receipt succeeds.
- Chinese, font size, emphasis, and cut command are checked.
- Failure cases show clear errors.

### Acceptance Criteria

- Android Pad can print a test receipt physically.
- UI remains responsive during socket operations.
- Errors are human-readable.
- Current Web/backend runtime is unaffected.

### Rollback Plan

Revert Pad app plugin and test page. No backend or database rollback.

### Risks

- Printer firmware may differ from backend assumptions.
- Chinese encoding may fail.
- Android network security may block local sockets.
- Router client isolation may block printer access.

### Final Codex Prompt

```text
Please implement PR4: Android native TCP PrinterPlugin test print POC.

Strict scope:
- Work only in restaurant-pad-app.
- Do not modify Restaurant_System backend.
- Do not modify Restaurant_System frontend.
- Do not connect order printing or print_jobs.
- Do not implement PAD_DIRECT.

Before coding:
1. Run git status.
2. Read doc/PAD_APP_ARCHITECTURE.md and doc/PAD_APP_PR_PROMPTS.md.
3. Inspect existing backend EscPosTcpPrinterTransport only as behavioral reference.

Implement native plugin:
- testConnection(ip, port, timeoutMs)
- printRawTcp(ip, port, payloadBase64, timeoutMs)

Implement a Pad-only test screen:
- printer IP
- port
- timeout
- Test Connection button
- Print Test Receipt button
- clear success/error messages

Requirements:
- Socket work must not block UI.
- Decode base64 payload and write raw bytes.
- Error codes: TIMEOUT, CONNECTION_REFUSED, UNREACHABLE, WRITE_FAILED, UNKNOWN.
- Test payload should include English, Chinese, font-size/emphasis commands if feasible, line feeds, and cut.

Manual QA:
- Pad and printer on same LAN.
- Input 192.168.x.x:9100.
- Test Connection succeeds.
- Print Test Receipt prints physically.
- Chinese/emphasis/cut verified.
- Failure messages verified by wrong IP/port.

Output:
- Files changed.
- Plugin interface.
- Test payload details.
- Manual printer test result.
- Known printer/encoding limitations.
```

## 7. PR5: Backend `PAD_DIRECT` Printing Mode

### PR Title

`PR5: Add backend PAD_DIRECT printing mode`

### Branch Name

`codex/pad-direct-printing-mode`

### Objective

Add `PAD_DIRECT` as a store printing mode where backend creates and renders print jobs but does not connect to printers.

### Context

Current modes are `MOCK`, `REAL`, and `DISABLED`. `REAL` opens TCP sockets from the backend. `PAD_DIRECT` prepares jobs for client-side Pad execution.

### Allowed Changes

- Backend printing mode enum/config.
- Print dispatcher branch for `PAD_DIRECT`.
- Tests for all printing modes.
- Print Center label/status support if needed.
- Documentation updates.

### Forbidden Changes

- Do not add device claim APIs in PR5.
- Do not add Android code.
- Do not change order transaction behavior.
- Do not break `REAL`, `MOCK`, or `DISABLED`.
- Do not remove server-side printing.

### Expected Files To Add/Modify

- `backend/src/main/java/com/restaurant/system/printing/PrintingMode.java`
- `backend/src/main/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java`
- Print-related DTO/tests if present
- `frontend/src/services/printingAdminService.ts` only if UI type needs `PAD_DIRECT`
- Print Center page only if mode display/update enum is hardcoded
- `SYSTEM_DOCUMENTATION.md`
- `doc/API.md`

### Backend Changes

- Normalize `PAD_DIRECT`.
- Allow store mode update to `PAD_DIRECT`.
- On order submit/update/reprint in `PAD_DIRECT`:
  - create print job
  - resolve assignment/printer
  - render receipt
  - attach `rendered_text_snapshot`
  - leave job `PENDING`
  - do not call `sendToPrinter`
- Keep failed job behavior for missing assignment/printer/renderer.

### Frontend Changes

Only if existing mode selector/types are hardcoded to `REAL | MOCK | DISABLED`. Add `PAD_DIRECT` display as "Pad Direct / Android Pad Prints Locally".

### Android / Pad App Changes

Not in scope.

### DB / Migration Changes

Not expected unless mode constraints exist. Current `stores.printing_mode` is string-based.

### API Changes

- Store printing mode accepts `PAD_DIRECT`.
- Existing print jobs API returns `PENDING` rendered jobs.

### Tests

- `MOCK` still marks printed with mock message.
- `REAL` still calls transport.
- `DISABLED` cancels or skips as currently designed.
- `PAD_DIRECT` creates pending rendered job and does not call transport.
- Order submit still succeeds.

### Manual QA

- Set mode to `PAD_DIRECT`.
- Submit order.
- Verify GRAB and FRONTDESK jobs exist.
- Verify `rendered_text_snapshot` exists.
- Verify no socket connection attempted.
- Switch back to `MOCK` and verify mock preview still works.

### Acceptance Criteria

- `PAD_DIRECT` does not physically print from backend.
- Existing modes are unchanged.
- Print Center can show `PAD_DIRECT` jobs.
- Order submit never rolls back due to Pad printing not yet connected.

### Rollback Plan

Revert PR5. Stores using `PAD_DIRECT` should be manually switched back to `MOCK`, `REAL`, or `DISABLED` before rollback if needed.

### Risks

- Accidentally marking jobs printed instead of pending.
- Accidentally skipping rendering, leaving Pad without payload.
- Mode selector mismatch between frontend and backend.
- Reprint behavior may need explicit `PAD_DIRECT` semantics.

### Final Codex Prompt

```text
Please implement PR5: backend PAD_DIRECT printing mode.

Strict scope:
- Add PAD_DIRECT mode only.
- Do not implement device register/claim APIs.
- Do not add Android code.
- Do not change order transaction behavior.
- Do not break MOCK, REAL, or DISABLED.

Before coding:
1. Run git status.
2. Read doc/PAD_APP_ARCHITECTURE.md and doc/PAD_APP_PR_PROMPTS.md.
3. Inspect PrintingMode, PrinterConfigServiceImpl, PrintDispatcherServiceImpl, PrintJobServiceImpl, OwnerPrintingController, Print Center frontend mode selector.

Implement:
- Add PAD_DIRECT to printing mode normalization.
- Allow store printing mode to be updated to PAD_DIRECT.
- In PAD_DIRECT, order-triggered printing creates jobs and renders rendered_text_snapshot but does not call TCP transport.
- Keep jobs PENDING for future Pad claim.
- Missing assignment/printer/renderer still creates visible failed job as appropriate.
- Existing MOCK/REAL/DISABLED behavior unchanged.

Tests:
- Backend tests for MOCK, REAL, DISABLED, PAD_DIRECT mode branching.
- Verify PAD_DIRECT does not call PrinterTransport.
- Verify rendered_text_snapshot is attached.

Manual QA:
- Switch store to PAD_DIRECT.
- Submit order.
- Verify GRAB/FRONTDESK jobs PENDING with preview content.
- Verify no socket timeout/log from TCP transport.
- Switch to MOCK and verify mock print still works.

Run:
- cd backend && mvn -q test
- cd backend && mvn -q -DskipTests compile
- cd frontend && npm run build if frontend mode selector changed
- git diff --check

Output:
- Files changed.
- PAD_DIRECT behavior.
- Existing mode regression results.
- Any limitations for PR6.
```

## 8. PR6: Device Register / Heartbeat / Claim / Complete / Fail API

### PR Title

`PR6: Add Pad device registration and print job claim APIs`

### Branch Name

`codex/pad-device-claim-api`

### Objective

Implement the backend foundation for Pad Direct Printing: device registration, heartbeat, pending jobs, atomic claim, payload, complete, fail, and release.

### Context

PR5 creates `PAD_DIRECT` jobs but does not let a Pad safely claim and complete them. PR6 adds the production-critical anti-duplicate printing mechanism.

### Allowed Changes

- Backend DB/entity/repository/service/controller/DTO.
- Atomic claim query.
- API docs.
- Tests.

### Forbidden Changes

- Do not implement Android printing UI.
- Do not change order submit semantics.
- Do not break existing `MOCK`, `REAL`, `DISABLED`.
- Do not bypass StoreAccessService.
- Do not store raw device token.

### Expected Files To Add/Modify

- New `store_devices` entity/repository/service/controller/DTO.
- `PrintJob` entity fields.
- `PrintJobAttempt` fields.
- `PrintJobRepository` atomic claim method.
- `PrintJobServiceImpl` claim/complete/fail/release logic.
- Device token hash utility/service.
- API docs and system docs.
- Backend tests.

### Backend Changes

- Add `store_devices`.
- Add device registration with token hashing.
- Add heartbeat.
- Add pending jobs endpoint.
- Add claim endpoint.
- Add payload endpoint.
- Add complete endpoint.
- Add fail endpoint.
- Add release endpoint.
- Add claim expiration.
- Add attempt recording with device metadata.

### Frontend Changes

Not in scope, except docs or generated API type notes if needed.

### Android / Pad App Changes

Not in scope. API can be tested with HTTP clients first.

### DB / Migration Changes

Suggested:

- Create `store_devices`.
- Add to `print_jobs`:
  - `execution_mode`
  - `claimed_by_device_id`
  - `claimed_at`
  - `claim_expires_at`
  - `printed_by_device_id`
  - `client_attempt_token`
  - `escpos_payload_base64`
- Add to `print_job_attempts`:
  - `device_id`
  - `transport_type`
  - `error_code`
  - `raw_result`

### API Changes

Add:

- `POST /api/v1/devices/register`
- `POST /api/v1/devices/heartbeat`
- `GET /api/v1/stores/{storeId}/printing/jobs/pending`
- `POST /api/v1/printing/jobs/{jobId}/claim`
- `GET /api/v1/printing/jobs/{jobId}/payload`
- `POST /api/v1/printing/jobs/{jobId}/complete`
- `POST /api/v1/printing/jobs/{jobId}/fail`
- `POST /api/v1/printing/jobs/{jobId}/release`

### Tests

- Device token is hashed, not stored raw.
- Pending endpoint returns only accessible store jobs.
- Two devices claim same job: one succeeds, one gets `409`.
- Claim expired job can be reclaimed.
- Non-claimant cannot complete/fail/release.
- Complete changes job to `PRINTED`.
- Fail changes job to `FAILED` or configured retry state.
- Idempotent same `client_attempt_token` does not duplicate side effects.
- Existing mode tests still pass.

### Manual QA

- Create PAD_DIRECT job.
- Register device.
- Heartbeat device.
- Fetch pending jobs.
- Claim job.
- Fetch payload.
- Complete job.
- Verify Print Center job status.
- Try second device claim and verify rejection.

### Acceptance Criteria

- Atomic claim prevents duplicate printing.
- Store isolation is enforced.
- Device token is secure.
- Attempts are recorded.
- Existing order submit and print modes still work.

### Rollback Plan

Revert PR6 code and migrations if no production data exists. If production data exists, leave nullable fields/tables in place and disable API routes/config until next release.

### Risks

- Race condition in claim.
- Incorrect device auth model.
- Token leakage.
- Stuck jobs if release/expiration logic is wrong.
- Overly broad admin/device access.

### Final Codex Prompt

```text
Please implement PR6: Pad device registration and print job claim APIs.

Strict scope:
- Backend API + DB support only.
- Do not implement Android UI.
- Do not change order submit semantics.
- Do not break MOCK, REAL, DISABLED, or PAD_DIRECT job creation.
- Do not bypass StoreAccessService.
- Do not store raw device token.

Before coding:
1. Run git status.
2. Read doc/PAD_APP_ARCHITECTURE.md and doc/PAD_APP_PR_PROMPTS.md.
3. Inspect current PrintJob, PrintJobAttempt, PrintJobRepository, PrintJobServiceImpl, OwnerPrintingController, StoreAccessService, AuthorizationService.

Implement:
- store_devices entity/table/repository/service.
- Device register with token hashing.
- Device heartbeat.
- print_jobs claim fields.
- print_job_attempts device/transport/error fields.
- pending jobs endpoint.
- atomic claim endpoint using database update semantics.
- payload endpoint.
- complete endpoint.
- fail endpoint.
- release endpoint.
- device/store authorization.
- client_attempt_token idempotency.
- claim_expires_at reclaim.

Security:
- URL storeId is not proof of access.
- Device must belong to job store.
- complete/fail/release require matching claimed_by_device_id.
- Manager/admin forced release should be separate and permission checked.

Tests:
- Two devices claiming same job: one success, one 409.
- Expired claim can be reclaimed.
- Non-claimant cannot complete/fail.
- Device token hash only.
- Pending jobs never leak another store.
- Existing printing modes still pass.

Run:
- cd backend && mvn -q test
- cd backend && mvn -q -DskipTests compile
- git diff --check

Output:
- Files changed.
- DB/entity/API changes.
- Claim SQL/atomicity explanation.
- Security/store isolation explanation.
- Test results.
- Remaining limitations for Android integration.
```

## 9. PR7: Print Center Pad Device And Claim Status

### PR Title

`PR7: Show Pad device and claim status in Print Center`

### Branch Name

`codex/print-center-pad-device-status`

### Objective

Make owner/manager operations visible: device status, claimed jobs, stale pending jobs, stale claimed jobs, failed reasons, and force release.

### Context

PR6 adds backend device and claim data. Operators need Print Center visibility to trust Pad Direct Printing during pilot.

### Allowed Changes

- Backend summary/list APIs for devices and claim status.
- Frontend Print Center UI.
- Force release action for authorized users.
- Owner overview summary if lightweight.

### Forbidden Changes

- Do not change order submit.
- Do not change Android printing plugin.
- Do not alter claim semantics except through explicit force release endpoint.
- Do not create aggressive polling.

### Expected Files To Add/Modify

- Printing controller/service DTOs for device status.
- Print Center page/service/types.
- Owner overview summary only if needed.
- Docs.

### Backend Changes

- Add store-scoped device list endpoint.
- Add print status summary endpoint if useful.
- Add force release endpoint with manager/admin permission.
- Ensure all data is store-scoped.

### Frontend Changes

Print Center should display:

- device name
- device status
- last_seen_at
- app_version
- platform
- current printing mode
- claimed_by_device
- claim_expires_at
- printed_by_device
- failed reason
- stale pending jobs
- stale claimed jobs
- force release action

### Android / Pad App Changes

Not in scope.

### DB / Migration Changes

Not expected if PR6 fields are sufficient.

### API Changes

Potential additions:

- `GET /api/v1/admin/printing/devices?store_id=...`
- `GET /api/v1/admin/printing/pad-status?store_id=...`
- `POST /api/v1/admin/printing/jobs/{jobId}/force-release`

### Tests

- Device list respects store access.
- Frontdesk role cannot manage devices.
- Force release requires permission.
- Stale claim summary is correct.
- Existing print jobs list still works.

### Manual QA

- Register/seed a device.
- Claim a job.
- View claimed device in Print Center.
- Simulate stale claim.
- Force release as owner/manager.
- Confirm frontdesk cannot force release.

### Acceptance Criteria

- Operators can see whether Pad devices are online.
- Operators can see which Pad claimed a job.
- Operators can see failed/stale jobs.
- Authorized user can force release stuck claim.
- No unrelated business flow is changed.

### Rollback Plan

Revert UI/API additions. Existing PR6 data can remain unused.

### Risks

- Too much polling in Print Center.
- Accidentally exposing another store's devices.
- Force release could cause duplicate printing if used carelessly.
- Confusing `reprint` vs `release`.

### Final Codex Prompt

```text
Please implement PR7: Print Center Pad device and claim status.

Strict scope:
- Add operational visibility for PR6 device/claim data.
- Do not change order submit.
- Do not change Android printing.
- Do not change claim core logic except authorized force release.
- Do not add aggressive polling.

Before coding:
1. Run git status.
2. Read doc/PAD_APP_ARCHITECTURE.md and doc/PAD_APP_PR_PROMPTS.md.
3. Inspect current PrintingSettingsPage, printingAdminService, OwnerPrintingController, PrintJobService, device APIs from PR6.

Backend:
- Add store-scoped device list/status API.
- Add stale pending/claimed job summary if useful.
- Add manager/admin force release API.
- Enforce store access and capability checks.

Frontend:
- Update Print Center to show Pad Devices section.
- Show device name/status/last_seen/app_version/platform.
- Show claimed_by_device, claim_expires_at, printed_by_device, failed reason in print jobs.
- Add warning for stale pending/claimed jobs.
- Add force release button for authorized users.
- Keep section-level errors; do not break printer list if device status fails.

Tests:
- Device list store isolation.
- Force release permission.
- Existing Print Center loads.
- Frontdesk cannot access management action.

Run:
- cd backend && mvn -q test
- cd backend && mvn -q -DskipTests compile
- cd frontend && npm run build
- git diff --check

Output:
- Files changed.
- New UI sections.
- New APIs.
- Store/permission validation.
- Test results.
- Remaining operator risks.
```

## 10. PR8: Cloud Deployment Config And Pairing Flow

### PR Title

`PR8: Add cloud-ready runtime config and Pad pairing flow`

### Branch Name

`codex/pad-cloud-config-pairing`

### Objective

Prepare Pad Direct Printing for cloud deployment by removing hardcoded environment assumptions and adding a secure store-device pairing flow.

### Context

PR6/PR7 provide device and claim mechanics. PR8 makes the setup safe and usable for cloud + restaurant LAN deployment.

### Allowed Changes

- Pairing code backend APIs.
- Owner/manager UI to generate pairing code.
- Pad setup screen to enter/scan pairing code.
- Runtime API base config.
- Docs for local/test/production.
- Android network security tightening.

### Forbidden Changes

- Do not purchase/configure cloud server.
- Do not hardcode API base or printer IP.
- Do not weaken store access.
- Do not allow production cleartext by default.
- Do not change order/receipt business logic.

### Expected Files To Add/Modify

- Backend pairing DTO/entity/service/controller if needed.
- Store device service from PR6.
- Print Center or Store Settings UI.
- Pad setup screen.
- Pad config storage.
- Android network security config.
- Deployment docs.

### Backend Changes

- Generate pairing code for authorized owner/manager.
- Store pairing code hash and expiration.
- Validate pairing code during device register.
- Revoke/disable device.
- Prevent expired/reused pairing codes.

### Frontend Changes

- Admin UI to generate pairing code and view expiration.
- Device revoke/disable action if not already present.
- Clear instructions for owner/manager.

### Android / Pad App Changes

- First-run setup screen.
- Runtime API base entry.
- Pairing code entry or QR scan placeholder.
- Persist API base and device identity securely.
- Use production HTTPS by default.
- Limit cleartext to debug/local builds.

### DB / Migration Changes

Possible options:

- Add `device_pairing_codes` table.
- Or add pairing fields to `store_devices` if simpler.

Recommended table:

```text
device_pairing_codes
  id
  organization_id
  store_id
  code_hash
  expires_at
  used_at
  created_by_user_id
  created_at
```

### API Changes

Potential additions:

- `POST /api/v1/admin/devices/pairing-codes`
- `GET /api/v1/admin/devices`
- `POST /api/v1/admin/devices/{deviceId}/disable`
- Existing `POST /api/v1/devices/register` consumes pairing code.

### Tests

- Pairing code expires.
- Pairing code cannot be reused after use.
- Device token stored hash only.
- Disabled device cannot heartbeat/claim.
- User cannot generate pairing code for unauthorized store.
- Production config rejects unexpected cleartext behavior where applicable.

### Manual QA

- Owner generates pairing code.
- Pad enters API base and pairing code.
- Device registers to correct store.
- Device appears in Print Center.
- Disable device and verify it cannot heartbeat/claim.
- Verify APK has no hardcoded customer IP.

### Acceptance Criteria

- Pad can be paired without hardcoded API/printer IP.
- Pairing is store-scoped and expires.
- Lost/disabled device can be blocked.
- Production docs clearly state cloud backend never connects to LAN printer.

### Rollback Plan

Disable pairing UI/API and fall back to manual dev registration only. Existing devices can remain disabled until next release.

### Risks

- Pairing code leakage.
- API base misconfiguration.
- Debug cleartext accidentally enabled in production.
- Lost device still authorized.
- Owner multi-store pairing to wrong store.

### Final Codex Prompt

```text
Please implement PR8: cloud-ready runtime config and Pad pairing flow.

Strict scope:
- Add pairing/config flow only.
- Do not purchase or configure real cloud infrastructure.
- Do not hardcode API base or printer IP.
- Do not change order submit or receipt business rules.
- Do not weaken store access.

Before coding:
1. Run git status.
2. Read doc/PAD_APP_ARCHITECTURE.md and doc/PAD_APP_PR_PROMPTS.md.
3. Inspect PR6 device model and PR7 Print Center device UI.

Backend:
- Add pairing code generation for authorized owner/manager.
- Store pairing code hash with expiration.
- Device register consumes valid pairing code.
- Pairing code cannot be reused.
- Disabled/revoked device cannot heartbeat or claim.
- Store access enforced server-side.

Frontend/Admin:
- Add UI to generate pairing code for current store.
- Show expiration and instructions.
- Add device disable/revoke if not already present.

Pad App:
- Add first-run setup screen.
- Enter runtime API base.
- Enter pairing code or placeholder for QR.
- Persist API base and device identity.
- Debug cleartext only in debug/local configuration.
- Production assumes HTTPS.

Docs:
- Local setup.
- Pilot setup.
- Production cloud setup.
- Printer IP must come from backend/store config, not APK.

Tests:
- Pairing code expiration.
- Pairing code one-time use.
- Unauthorized store pairing denied.
- Disabled device cannot heartbeat/claim.
- No hardcoded API/printer IP.

Run:
- backend tests/compile if backend changed.
- frontend build if admin UI changed.
- Android build if Pad app changed.
- git diff --check.

Output:
- Files changed.
- Pairing flow.
- Runtime config behavior.
- Security protections.
- Manual QA result.
- Remaining production deployment notes.
```

## 11. Final Pre-Launch Checklist

Before using Pad Direct Printing in a real restaurant pilot:

- `MOCK`, `REAL`, `DISABLED`, and `PAD_DIRECT` mode behavior is documented and tested.
- Store printing mode is clearly visible in Print Center.
- Android Pad can physically print a test receipt to every printer.
- Printer IPs are fixed by DHCP reservation or printer static IP.
- Router AP/client isolation is disabled for Pad-to-printer communication.
- Pad device registration is store-scoped.
- Device token is stored hashed server-side.
- `print_jobs` claim is atomic.
- `claim_expires_at` reclaim path is tested.
- Multiple Pads cannot print the same job.
- Pad crash during claim is tested.
- Print success but complete API failure is handled or documented.
- Print Center shows stale pending/claimed jobs.
- Manual reprint prints current full order and does not mutate order.
- Automatic update tickets remain batch-scoped.
- StoreAccessService protects all store-scoped APIs.
- Owner multi-store view never leaks other organization stores.
- Production APK does not hardcode API base or printer IP.
- Production does not allow unintended cleartext HTTP.
- Local debug cleartext is documented and isolated.
- Cloud backend never attempts to connect to `192.168.x.x` printers.

## 12. Recommended Next PR

The first implementation PR should be PR2: `restaurant-pad-app` skeleton.

Reason:

- It is low risk.
- It does not touch backend/frontend runtime.
- It creates a safe place to experiment with Android without destabilizing the current restaurant pilot system.

PR4 is the first hardware-risk PR and should not start until PR2/PR3 give the Pad app a stable shell.
