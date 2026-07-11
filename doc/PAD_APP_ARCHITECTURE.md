# Pad App Architecture Docs

PR 1 scope: documentation only. This document does not implement Android, Capacitor, database migrations, backend APIs, frontend runtime changes, or printing logic changes.

## 1. Background And Goals

The current `Restaurant_System` should remain the stable Web + Backend product:

- React Web frontend for Frontdesk, Admin, Owner Home, KDS, Print Center, and order history.
- Spring Boot backend for orders, menu, auth, store workspace, owner multi-store access, print jobs, receipt renderers, permissions, and audit logs.
- PostgreSQL as the source of truth for orders, stores, users, menus, printer assignments, and print jobs.

The Android Pad direction should be a new independent project, tentatively named:

```text
restaurant-pad-app
```

The Pad App should reuse the current React frontend build instead of rewriting the POS UI. The Android native layer should provide local device capabilities that a browser cannot safely provide, especially direct TCP printing to LAN ESC/POS printers.

Responsibilities should be split as follows:

- Backend continues to own orders, menu catalog, auth, store access, owner multi-store workspaces, `print_jobs`, and receipt rendering.
- Android Pad shell runs the existing frontend UI in WebView / Capacitor.
- Android native layer prints locally to in-store LAN printers such as `192.168.1.200:9100`.
- Future cloud servers must not directly connect to `192.168.x.x` printers.
- Future cloud servers should only manage data, permissions, orders, print tasks, owner dashboard, and device coordination.

This keeps the current local/Windows pilot deployment working while opening a path to cloud SaaS where the in-store Pad performs the private-LAN printing.

## 2. Why Android / Capacitor Should Not Be Added Directly To The Current Project

The first Pad App should be independent from the current `Restaurant_System` repository/runtime because:

- It avoids polluting the stable Web project with Gradle, Android SDK, permissions, native plugin code, Capacitor config, and APK build artifacts.
- It keeps the existing local Web + Backend workflow runnable without Android tooling.
- It allows Android WebView and native printing to be debugged, versioned, and released independently.
- It keeps future APK releases separate from backend/frontend application releases.
- It allows later binding to a specific frontend build artifact or frontend build version.
- It avoids accidental changes to `frontend/package.json`, lockfiles, Vite config, routing, backend printing, or order flows while Android printing is still experimental.

The current `Restaurant_System` should remain the canonical Web + Backend codebase. The Pad App should consume a frontend build artifact and talk to the backend through documented APIs.

## 3. Recommended Architecture

```text
Android Pad App
  - WebView / Capacitor shell
  - bundled React frontend build
  - native PrinterPlugin
  - runtime API base config
  - device / store binding
  - local print execution

        ↓ HTTPS / HTTP debug

Backend
  - orders
  - menu
  - auth
  - store access
  - print_jobs
  - receipt renderer
  - owner dashboard
  - device management

        ↓ no direct LAN printer access in cloud

Printer
  - LAN ESC/POS printer
  - 192.168.x.x:9100
  - only reachable by Pad inside store LAN
```

Design principles:

- Pad App is the runtime container for the existing frontend UI. It should not rewrite POS screens.
- Backend is the source of truth for order state and receipt content.
- `PrinterPlugin` should only send bytes/text to the local printer over TCP. It should not decide business layout, prices, totals, taxes, combo rules, or kitchen instructions.
- Receipt renderer should stay backend-first so Android and backend do not drift into two different receipt formats.
- Android should receive backend-generated rendered text and, later, an exact ESC/POS payload.
- Current Web Print Center and Mock Preview should continue to use existing backend `rendered_text_snapshot`.

Long-term best target:

```text
Backend receipt renderer -> rendered text / markup -> ESC/POS encoder -> escpos_payload_base64
Android PrinterPlugin -> open socket -> send payload -> report result
```

The short-term POC can mirror the current backend ESC/POS transport behavior in Android, but business receipt rendering should not move into Android.

## 4. Printing Mode Design

Current modes:

- `MOCK`
- `REAL`
- `DISABLED`

Recommended new mode:

- `PAD_DIRECT`

### MOCK

- Creates print jobs.
- Renders mock text.
- Saves `rendered_text_snapshot`.
- Does not connect to a physical printer.
- Used for local development, no-printer testing, CI-style validation, and receipt preview.

### REAL

- Backend/server directly connects to ESC/POS printers over TCP.
- Suitable for local deployments where the backend machine is in the same LAN as printers, such as the Windows pilot setup.
- Not suitable for a future cloud server trying to reach store-private `192.168.x.x` printers.

### DISABLED

- Printing is disabled.
- Orders must still submit normally.
- No physical printing should be attempted.
- Useful for temporary store-level printing shutdowns.

### PAD_DIRECT

- Backend creates `print_jobs` and receipt payload.
- Backend does not open TCP socket connections to printers.
- Pad fetches pending jobs for its authorized store.
- Pad claims a job.
- Pad native `PrinterPlugin` connects to the LAN printer.
- Pad reports `complete` on success.
- Pad reports `fail` with error details on failure.
- Owner/Admin/Print Center can view status and reprint.

Important constraints:

- `PAD_DIRECT` must not replace `MOCK`, `REAL`, or `DISABLED`.
- Existing local testing and Windows pilot printing must continue to work.
- Printing failure must not roll back order submission.
- `PAD_DIRECT` changes where printing is executed, not the order submit flow.

## 5. Pad Direct Printing Flow

End-to-end flow:

1. Pad submits an order through the existing order API.
2. Backend saves the order transactionally.
3. Backend creates print jobs, for example:
   - `GRAB`
   - `FRONTDESK_RECEIPT`
4. Backend renders receipt content:
   - `rendered_text_snapshot`
   - future field: `escpos_payload_base64`
5. Pad fetches pending jobs for the current store.
6. Pad claims one job.
7. Backend marks the job claimed and records:
   - `claimed_by_device_id`
   - `claimed_at`
   - `claim_expires_at`
8. Pad native `PrinterPlugin` connects to printer IP/port, usually `192.168.x.x:9100`.
9. If printing succeeds, Pad calls `complete`.
10. If printing fails, Pad calls `fail` with `error_code` and `error_message`.
11. If Pad crashes, loses power, disconnects, or is killed, the claim lease expires and the job can be claimed again.
12. Reprint creates or reopens a print job so the Pad can claim and print it.

Recommended runtime sequence:

```text
Order submit success
  -> print_jobs PENDING
  -> Pad sees pending job
  -> Pad claim job atomically
  -> print job CLAIMED / PRINTING
  -> Pad prints locally
  -> Pad complete/fail
  -> print job PRINTED / FAILED
```

## 6. Claim / Lease Anti-Duplicate Printing Mechanism

### Why Claim Is Required

In a multi-Pad restaurant, querying pending jobs without claim can duplicate prints:

```text
Pad A queries pending jobs -> sees job 101
Pad B queries pending jobs -> also sees job 101
Pad A prints job 101
Pad B also prints job 101
Result: duplicate kitchen ticket
```

Claim is the temporary ownership lock that prevents this.

### `claimed_by_device_id`

`claimed_by_device_id` records which Pad is responsible for the job.

It is needed to:

- prevent another Pad from completing or failing the same job
- track which device printed a ticket
- help owner/admin debug "which device handled this job"
- enforce safe reprint and release behavior

Only the claiming device should be able to call `complete`, `fail`, or normal `release`.

### `claim_expires_at`

`claim_expires_at` prevents jobs from being stuck forever.

If a Pad claims a job and then crashes, loses power, loses Wi-Fi, or the app is killed, the job should not stay locked permanently. After the lease expires, backend can allow another Pad to claim the job.

This is a lease / temporary lock, not permanent ownership.

### Claim Must Be Atomic

Claim must not be implemented as:

```text
SELECT pending job
then UPDATE claimed
```

That pattern can race under concurrent Pads.

Claim should be a single atomic database update similar to:

```sql
UPDATE print_jobs
SET status = 'CLAIMED',
    claimed_by_device_id = :deviceId,
    claimed_at = now(),
    claim_expires_at = now() + interval '60 seconds'
WHERE id = :jobId
  AND store_id = :storeId
  AND status = 'PENDING';
```

Expired claims can be reclaimed with:

```sql
UPDATE print_jobs
SET status = 'CLAIMED',
    claimed_by_device_id = :deviceId,
    claimed_at = now(),
    claim_expires_at = now() + interval '60 seconds'
WHERE id = :jobId
  AND store_id = :storeId
  AND (
    status = 'PENDING'
    OR (status = 'CLAIMED' AND claim_expires_at < now())
  );
```

Updating one row means claim succeeded. Updating zero rows means another device already claimed it, the claim has not expired, or the job is no longer printable.

### Complete / Fail / Release Must Check Device

Rules:

- `complete` must verify `claimed_by_device_id == current_device_id`.
- `fail` must verify `claimed_by_device_id == current_device_id`.
- normal `release` must verify `claimed_by_device_id == current_device_id`.
- manager/admin may have a separate forced release path.
- Pad must not complete or fail another Pad's claimed job.

### Keep Claim Fields Even In A One-Pad First Version

Even if the first restaurant only has one Pad, the DB/API contract should still include:

- `claimed_by_device_id`
- `claimed_at`
- `claim_expires_at`

Without those fields, adding multi-Pad safety, device status, and print responsibility tracking later becomes much harder and riskier.

## 7. API Contract Draft

These APIs are proposed only. PR 1 does not implement them.

### `POST /api/v1/devices/register`

Purpose:

- Register a Pad for a store.
- Bind the Pad to `organization_id` and `store_id`.
- Return a device identity or device token.

Auth / store access:

- Initial version can require an authenticated owner/manager and a pairing code.
- Device must be store-scoped.
- Backend must verify the registering user can manage the target store.

Request shape:

```json
{
  "store_id": 1,
  "device_name": "Frontdesk Pad 1",
  "device_type": "PAD_PRINT_CLIENT",
  "pairing_code": "123456",
  "app_version": "0.1.0",
  "platform": "android"
}
```

Response shape:

```json
{
  "device_id": "pad_abc123",
  "store_id": 1,
  "organization_id": 1,
  "device_token": "plain-token-returned-once",
  "status": "ACTIVE"
}
```

Idempotency / concurrency notes:

- Device token should be returned only once.
- Re-registering the same physical device should either rotate token intentionally or return an existing device record without exposing old token.

Store isolation notes:

- Device must be bound to one store unless a future explicit multi-store device model is added.

Error cases:

- invalid pairing code
- user cannot manage store
- device disabled
- store inactive

Security notes:

- Store only `device_token_hash`, never the raw token.

### `POST /api/v1/devices/heartbeat`

Purpose:

- Pad reports that it is online.
- Backend updates `last_seen_at`, `app_version`, `platform`, and current store.

Auth / store access:

- Require valid device token or authenticated user session.
- Device must belong to the reported store.

Request shape:

```json
{
  "device_id": "pad_abc123",
  "store_id": 1,
  "app_version": "0.1.0",
  "platform": "android",
  "current_route": "/stores/1/frontdesk"
}
```

Response shape:

```json
{
  "device_id": "pad_abc123",
  "status": "ACTIVE",
  "server_time": "2026-06-30T12:00:00Z"
}
```

Error cases:

- invalid device token
- disabled device
- store mismatch

### `GET /api/v1/stores/{storeId}/printing/jobs/pending`

Purpose:

- Pad fetches pending or claim-expired print jobs for its store.

Auth / store access:

- Require device token or user session with store access.
- URL `storeId` is not trusted as authority.

Query example:

```text
?modules=GRAB,FRONTDESK_RECEIPT&limit=20
```

Response shape:

```json
{
  "store_id": 1,
  "jobs": [
    {
      "job_id": 101,
      "module_code": "GRAB",
      "receipt_type": "GRAB",
      "status": "PENDING",
      "printer_id": 4,
      "created_at": "2026-06-30T12:00:00Z"
    }
  ]
}
```

Concurrency notes:

- This endpoint only lists candidates. It does not reserve them.
- The Pad must call `claim` before printing.

Error cases:

- no store access
- device not assigned to store
- printing mode is not `PAD_DIRECT`

### `POST /api/v1/printing/jobs/{jobId}/claim`

Purpose:

- Pad atomically claims a print job before printing.

Auth / store access:

- Require device token or user session with store access.
- Device must belong to the same store as the job.

Request shape:

```json
{
  "device_id": "pad_abc123",
  "client_attempt_token": "uuid",
  "lease_seconds": 60
}
```

Response shape:

```json
{
  "job_id": 101,
  "status": "CLAIMED",
  "claimed_by_device_id": "pad_abc123",
  "claim_expires_at": "2026-06-30T12:01:00Z"
}
```

Idempotency / concurrency notes:

- Claim must be a single atomic database update.
- If the same `client_attempt_token` retries after network failure, backend may return the same claim if it is still owned by that device.
- If another device already owns an unexpired claim, return `409`.

Store isolation notes:

- Claim must verify job store matches the device/user store scope.

Error cases:

- `409` already claimed
- `409` already printed/cancelled
- `403` device has no store access
- `404` job not found within accessible scope

### `GET /api/v1/printing/jobs/{jobId}/payload`

Purpose:

- Pad fetches printable payload after claim.

Auth / store access:

- Prefer requiring the current device to be the claimant.
- Manager/admin may be allowed for preview/debug.

Response shape:

```json
{
  "job_id": 101,
  "module_code": "GRAB",
  "receipt_type": "GRAB",
  "rendered_text_snapshot": "桌号：1里\n...",
  "escpos_payload_base64": null,
  "printer": {
    "id": 4,
    "name": "Kitchen Printer",
    "ip_address": "192.168.1.200",
    "port": 9100,
    "printer_type": "ESC_POS_TCP",
    "text_encoding": "GBK",
    "escpos_code_page": 0,
    "font_size": "LARGE",
    "timeout_ms": 3000
  }
}
```

Idempotency / concurrency notes:

- Fetching payload must not change print state.

Error cases:

- job not claimed
- claimed by another device
- missing rendered content
- assigned printer missing or disabled

### `POST /api/v1/printing/jobs/{jobId}/complete`

Purpose:

- Pad reports successful physical printing.

Auth / store access:

- Current device must match `claimed_by_device_id`.

Request shape:

```json
{
  "device_id": "pad_abc123",
  "client_attempt_token": "uuid",
  "printed_at": "2026-06-30T12:00:00Z",
  "raw_result": "socket write ok"
}
```

Response shape:

```json
{
  "job_id": 101,
  "status": "PRINTED",
  "printed_by_device_id": "pad_abc123",
  "printed_at": "2026-06-30T12:00:00Z"
}
```

Idempotency / concurrency notes:

- Repeating the same `client_attempt_token` should not duplicate attempts or side effects.
- Repeating complete for an already printed job by the same attempt can return current state.

Error cases:

- claimed by another device
- claim expired and reclaimed
- job already cancelled

### `POST /api/v1/printing/jobs/{jobId}/fail`

Purpose:

- Pad reports failed physical printing.

Auth / store access:

- Current device must match `claimed_by_device_id`.

Request shape:

```json
{
  "device_id": "pad_abc123",
  "client_attempt_token": "uuid",
  "error_code": "TIMEOUT",
  "error_message": "connect timed out",
  "raw_result": "socket timeout after 3000ms"
}
```

Response shape:

```json
{
  "job_id": 101,
  "status": "FAILED",
  "error_code": "TIMEOUT",
  "error_message": "connect timed out"
}
```

Idempotency / concurrency notes:

- Same `client_attempt_token` retry should not create duplicate attempts.
- Future retry policy may move the job back to `PENDING`; first implementation can mark `FAILED` and rely on manual reprint.

Error cases:

- claimed by another device
- job already printed
- invalid error code

### `POST /api/v1/printing/jobs/{jobId}/release`

Purpose:

- Pad gives up a claim, or admin force-releases a stuck job.

Auth / store access:

- Normal Pad release requires matching `claimed_by_device_id`.
- Manager/admin may have a separate force release permission.

Request shape:

```json
{
  "device_id": "pad_abc123",
  "reason": "app shutting down"
}
```

Response shape:

```json
{
  "job_id": 101,
  "status": "PENDING"
}
```

Error cases:

- claimed by another device
- already printed
- no permission for forced release

## 8. Database / Entity Change Suggestions

No migration is implemented in PR 1. This section is the recommended future schema contract.

### `print_jobs` Suggested Fields

`execution_mode`

- Records `SERVER_DIRECT`, `MOCK`, or `PAD_DIRECT`.
- Helps distinguish how a job was intended to print.

`claimed_by_device_id`

- References the Pad/device currently responsible for printing.
- Prevents duplicate printing and supports operator debugging.

`claimed_at`

- Timestamp when the claim was acquired.

`claim_expires_at`

- Lease expiration timestamp.
- Allows reclaim after Pad crash or network loss.

`printed_by_device_id`

- Records the device that successfully printed.

`client_attempt_token`

- Client-generated UUID used for idempotency of claim/complete/fail.

`escpos_payload_base64`

- Future exact ESC/POS payload.
- Allows Android to send bytes without duplicating encoder logic.

### New Table: `store_devices`

Suggested fields:

```text
id
organization_id
store_id
device_name
device_type
device_token_hash
status
last_seen_at
app_version
platform
is_active
created_at
updated_at
```

Purpose:

- Bind Pad devices to a store.
- Track device online/offline state.
- Enforce store-scoped device permissions.
- Identify which Pad printed or failed a ticket.
- Allow future Print Center device status and owner troubleshooting.

Security:

- `device_token_hash` must store only a hash.
- Raw device token should be returned once and never displayed again.

### `print_job_attempts` Suggested Fields

`device_id`

- Which device attempted the print.

`transport_type`

- Example values: `SERVER_DIRECT`, `PAD_DIRECT`, `MOCK`.

`error_code`

- Structured error such as `TIMEOUT`, `CONNECTION_REFUSED`, `WRITE_FAILED`.

`raw_result`

- Bounded debug message from native plugin or server transport.

Purpose:

- Preserve each physical/logical attempt.
- Debug printer connectivity, Pad failures, socket timeouts, and reporting failures.

## 9. Independent `restaurant-pad-app` Project Plan

Recommended structure:

```text
restaurant-pad-app/
  android/
  app/
  web/
  plugins/
  docs/
```

Suggested ownership:

- `web/`: bundled frontend build artifact.
- `plugins/`: native printer bridge code.
- `android/`: Gradle/Android app shell.
- `docs/`: Pad-specific setup, pairing, debugging, release notes.

Frontend reuse plan:

- Phase 1 POC may manually copy `Restaurant_System/frontend/dist` into the Pad app.
- Manual copy is acceptable only for POC.
- Long-term maintenance should use a frontend build artifact or a pinned frontend build version.
- Do not maintain two copies of frontend source code.
- Do not start by converting the current repository to a monorepo; that is too invasive for the current stable product.

Recommended future artifact flow:

```text
Restaurant_System frontend build
  -> versioned dist artifact
  -> restaurant-pad-app consumes artifact
  -> APK release references frontend build version
```

## 10. Environment Configuration

### Local Development

Example:

```text
API base: http://192.168.1.50:8080
Printer: 192.168.x.x:9100
```

Requirements:

- Android Pad, development computer, and printers must be on the same LAN.
- Android debug builds may allow cleartext HTTP for local testing.
- Development computer IP must not be hardcoded into app source.
- Printer IP must not be hardcoded into app source.
- Printer config should come from backend `printer_configs` or a store-level device setting.

### Production Cloud

Example:

```text
API base: https://api.example.com
Admin / Owner Web: https://pos.example.com
Printer: still local LAN, e.g. 192.168.x.x:9100
```

Rules:

- Cloud server must not connect directly to private printer IPs.
- Pad App prints locally inside the restaurant LAN.
- API base should be runtime configurable through first-launch setup, QR config, pairing code, or backend device config.
- APK should not contain hardcoded customer IPs.
- Production should use HTTPS.

### Current Frontend `/api` Concern

The current frontend uses relative `/api/v1/...` paths. That works in Vite dev/preview through proxy, but the Pad App cannot rely on Vite proxy.

The Pad App needs a runtime API base strategy, such as:

- injecting `window.__RESTAURANT_API_BASE_URL__`
- WebView request rewriting
- Capacitor Preferences + API client config
- loading frontend from a backend-hosted origin

This should be solved before the Pad App is expected to run against arbitrary backend hosts.

## 11. Android Native Printing POC Plan

The current Java backend can already print through TCP socket, which strongly suggests the printers support LAN ESC/POS TCP printing. That does not automatically prove Android will work in every store network.

Android still needs a focused POC to verify:

- Pad and printer are on the same LAN.
- Router does not enable AP isolation / client isolation.
- Printer IP is fixed.
- Printer port is actually `9100`.
- Chinese encoding prints correctly.
- Font size, bold/emphasis, cut command, and line feeds work correctly.
- Timeout, unreachable host, and connection refused errors are shown clearly.

First native plugin POC should expose only:

```text
testConnection(ip, port, timeoutMs)
printRawTcp(ip, port, payloadBase64, timeoutMs)
```

Do not connect it to full order printing first. Prove Android can print before wiring print jobs.

Suggested POC acceptance:

1. Pad and printer are in the same Wi-Fi / LAN.
2. App can input printer IP and port.
3. `Test Connection` succeeds.
4. `Print Test Receipt` prints physically.
5. Chinese, font size, emphasis, line feeds, and cut work.
6. Failure cases show human-readable errors.

## 12. Risk List And Mitigations

### WebView Routing Fallback

Risk:

- Existing frontend uses path-based routing. Reloading `/stores/1/frontdesk` inside WebView may fail if `index.html` fallback is not configured.

Mitigation:

- Android shell must route unknown paths to bundled `index.html`.
- Test deep links and refresh-like reloads.

### API Relative Path Problem

Risk:

- Relative `/api/v1` works with Vite proxy but not automatically in bundled WebView.

Mitigation:

- Add runtime API base injection in a later PR.

### Android Cleartext HTTP

Risk:

- Local debug uses `http://192.168.x.x:8080`, which Android may block.

Mitigation:

- Allow cleartext only for debug/local config.
- Production must use HTTPS.

### WebView LocalStorage / Token Persistence

Risk:

- Tokens may be cleared by WebView data reset or app reinstall.

Mitigation:

- Use normal auth refresh, clear session handling, and later consider secure native token storage.

### Chinese ESC/POS Encoding

Risk:

- Android byte generation may differ from backend `GBK` / code page behavior.

Mitigation:

- Keep backend receipt rendering canonical.
- Extract or mirror ESC/POS encoder carefully.
- Validate with real printer test receipts.

### Printer DHCP IP Change

Risk:

- Printer IP changes after router reboot.

Mitigation:

- Require static DHCP reservation or fixed printer IP.
- Print Center should show printer endpoint and connection status.

### Router AP Isolation / Client Isolation

Risk:

- Pad cannot reach printer even on same Wi-Fi.

Mitigation:

- Document router setting.
- Native test connection must surface clear errors.

### Multi Pad Duplicate Printing

Risk:

- Multiple Pads print the same pending job.

Mitigation:

- Atomic claim + lease + device verification.

### Pad Crash During Claim

Risk:

- Job remains locked.

Mitigation:

- `claim_expires_at` lease expiration and reclaim policy.

### Print Success But Complete API Failed

Risk:

- Physical ticket printed, but backend still thinks job failed/pending.

Mitigation:

- Pad should persist a local completion outbox and retry `complete`.
- Print Center should show ambiguous/stale claimed jobs.

### Backend Created Order But Pad Did Not Print

Risk:

- Kitchen misses order if no Pad is online.

Mitigation:

- Print Center failed/pending counter.
- Pad heartbeat/online status.
- Owner/admin alert for stale pending jobs.

### Store Isolation

Risk:

- Device or user pulls another store's jobs.

Mitigation:

- StoreAccessService and device store binding must be enforced server-side.

### Device Token Leakage

Risk:

- Stolen token could claim/print jobs.

Mitigation:

- Hash tokens in DB.
- Allow device revoke.
- Bind token to store and device status.

### MOCK / REAL / PAD_DIRECT Mixed Use

Risk:

- Operator thinks one mode is active while another executes.

Mitigation:

- Print Center must clearly display current mode.
- `PAD_DIRECT` must be explicit and not silently replace existing modes.

### Owner Multi-Store Print Visibility

Risk:

- Owner sees or acts on wrong store's print jobs.

Mitigation:

- Store-scoped APIs and Owner Home summary must use membership-backed access only.

### Cloud Latency

Risk:

- Delays in fetching jobs.

Mitigation:

- Use WebSocket or short polling with backoff.
- Keep order submission independent from print execution.

### Store Internet Outage

Risk:

- Cloud cannot deliver jobs to Pad.

Mitigation:

- Offline mode is a future non-goal for PR 1.
- Pilot should document internet dependency.

### Reprint vs Automatic Print

Risk:

- Manual reprint could be confused with order update ticket.

Mitigation:

- Keep automatic update tickets and manual full reprints separate.
- Preserve current print job metadata such as `order_update_batch_id`.

### Manual Reprint Should Not Modify Orders

Risk:

- Reprint accidentally triggers business state changes.

Mitigation:

- Reprint APIs only create/print jobs and never mutate order items/status.

## 13. PR Plan

### PR 1: `pad-app-architecture-docs`

- Add this architecture document.
- No runtime code changes.
- No Android project.
- No dependencies.
- No database migrations.

### PR 2: `restaurant-pad-app` Skeleton

- Create independent Pad App project.
- No real printing yet.
- No backend business logic changes.

### PR 3: WebView Loads Frontend Build

- Pad App loads frontend `dist`.
- Runtime API base config.
- Login, workspace, frontdesk, and menu can open.

### PR 4: Native TCP Printer Plugin Test Connection

- Implement Android `PrinterPlugin`.
- Only `testConnection` and test print.
- No order printing integration.

### PR 5: `PAD_DIRECT` Printing Mode

- Backend adds `PAD_DIRECT`.
- Order submit creates print jobs and rendered payload.
- Backend does not socket print in this mode.
- Existing `MOCK`, `REAL`, and `DISABLED` remain unchanged.

### PR 6: Device Register / Heartbeat / Claim / Complete / Fail

- Add device table/API.
- Add atomic claim.
- Add anti-duplicate printing.
- Add attempt tracking.

### PR 7: Reprint + Print Center Device Status

- Print Center shows device status, claimed jobs, failed reasons, and last seen.
- Reprint works through Pad Direct flow.

### PR 8: Cloud-Ready Config

- Runtime API base setup.
- Pairing code flow.
- Production HTTPS config.
- Remove or constrain debug cleartext.

## 14. Non-Goals For PR 1

This PR explicitly does not:

- implement Android App
- create Android / Gradle / Capacitor project files
- install Capacitor or any dependency
- modify order submission
- modify existing printing logic
- delete or replace `REAL`, `MOCK`, or `DISABLED`
- purchase or configure cloud servers
- implement offline mode
- add owner dashboard features
- add database migrations
- implement PrinterPlugin
- implement backend APIs
- change frontend runtime behavior

## 15. Final Recommendation

Proceed with an independent `restaurant-pad-app` and treat `Restaurant_System` as the stable backend/web source of truth.

The safest next implementation PR is not full order printing. It should be:

1. independent Pad App skeleton
2. WebView loading frontend build
3. runtime API base config
4. native TCP test print POC

Only after Android can reliably connect to and print from a real LAN ESC/POS printer should `PAD_DIRECT` print job claim/complete/fail be implemented.
