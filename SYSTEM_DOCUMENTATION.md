# SYSTEM DOCUMENTATION

Generated from the current codebase only. If a detail is not explicit in code, it is marked as `UNKNOWN`.

Additional maintainable architecture document:
- `doc/SystemDesign_Bilingual.md`
- `doc/PAD_APP_ARCHITECTURE.md`
- `doc/PAD_APP_PR_PROMPTS.md`

## Cloud Ready PR2: Flyway Migration Baseline

PR2 introduces Flyway as the versioned schema migration mechanism for pilot/cloud profiles while preserving local development convenience.

Current behavior after PR2:

- Local/default profile still uses JPA `ddl-auto=update` and has Flyway disabled, so existing local developer databases are not unexpectedly blocked by missing Flyway history.
- Pilot profile uses Flyway and `spring.jpa.hibernate.ddl-auto=validate`.
- Cloud profile is introduced through `backend/src/main/resources/application-cloud.yml` and uses Flyway plus `ddl-auto=validate`.
- `backend/src/main/resources/db/migration/V1__baseline_current_schema.sql` is the first schema baseline.
- The V1 baseline was generated from the current PostgreSQL schema using `pg_dump --schema-only --no-owner --no-privileges --no-comments --schema=public` and then cleaned for Flyway execution.
- V1 intentionally captures the existing schema as-is. It does not insert seed data, rename columns, drop tables, rewrite historical records, or add aggressive new production constraints.
- `spring.flyway.baseline-on-migrate=true` is enabled for pilot/cloud so an existing non-empty database can be marked as baseline version `1` without replaying V1 over existing tables.
- An empty pilot/cloud database can run V1 normally to create the baseline schema.

Operational notes:

- Before enabling the pilot/cloud profile on an existing database, create a PostgreSQL backup with `pg_dump -Fc`.
- Existing databases without `flyway_schema_history` should be baselined by the application/Flyway startup path before follow-up migrations are added.
- Follow-up migrations should add indexes, data checks, data repair, and stricter constraints in small reviewed PRs.
- The current PR does not change order submission, `completeOrder`, payment/refund behavior, print modes, Android Pad code, Store Access rules, or menu/order business flows.

## Cloud Ready PR3: Production Safety Guard

PR3 adds a backend startup safety guard for cloud, production, and pilot profiles.

The guard lives in `backend/src/main/java/com/restaurant/system/common/config/ProductionSafetyConfig.java` and runs before regular singleton beans are created. This is intentional: unsafe cloud/prod configuration should fail fast before Hibernate, Flyway, auth, printing, or business services start normal runtime work.

Strict production profiles:

- `cloud`
- `prod`
- `production`

Strict production profiles fail startup when any of the following are true:

- `app.auth.jwt-secret` is empty.
- `app.auth.jwt-secret` is shorter than 32 characters.
- `app.auth.jwt-secret` contains `dev-local`.
- `app.auth.jwt-secret` contains `replace-this`.
- `app.auth.x-user-id-fallback-enabled=true`.
- `app.dev-tools.role-switcher-enabled=true`.
- `app.seed.force-overwrite=true`.
- `spring.jpa.hibernate.ddl-auto` is `update`, `create`, or `create-drop`.
- `spring.flyway.enabled=false`.

Pilot profile is semi-strict. It fails startup when:

- `app.auth.jwt-secret` is empty.
- `app.auth.jwt-secret` is shorter than 32 characters.
- `app.auth.jwt-secret` contains `replace-this`.
- `app.auth.jwt-secret` contains `dev-local`.

Local/dev behavior remains unchanged. Local development may continue using the dev JWT secret, `X-User-Id` fallback, optional Dev Role Switcher, JPA `ddl-auto=update`, and Flyway disabled.

Deployment notes:

- Cloud/prod must provide a strong `JWT_SECRET` through environment variables or a secret manager.
- Cloud/prod must keep `app.auth.x-user-id-fallback-enabled=false`.
- Cloud/prod must keep `app.dev-tools.role-switcher-enabled=false`.
- Cloud/prod must keep `app.seed.force-overwrite=false`.
- Cloud/prod must use Flyway and `spring.jpa.hibernate.ddl-auto=validate` or `none`.
- If startup fails with `Production safety check failed`, fix the listed configuration item rather than bypassing the guard.

## Cloud Ready PR4: RuntimeDataSeeder Production Safety

PR4 makes startup seed behavior explicit so cloud, production, and pilot deployments do not silently create default accounts or demo restaurant data.

Runtime seeding is controlled by:

- `app.seed.runtime-enabled`
- `app.seed.force-overwrite`
- `app.seed.safe-metadata-enabled`
- `app.seed.default-users-enabled`
- `app.seed.demo-data-enabled`
- `app.seed.membership-supplement-enabled`
- `app.seed.production-bootstrap-enabled`

Local/default seed policy:

- `safe-metadata-enabled=true`
- `default-users-enabled=true`
- `demo-data-enabled=true`
- `membership-supplement-enabled=true`
- `production-bootstrap-enabled=false`

This keeps local development convenient. Local startup can still create the familiar demo store, roles, demo menu, dining tables, printer settings, memberships, and default recoverable login accounts.

Cloud and pilot seed policy:

- `safe-metadata-enabled=true`
- `default-users-enabled=false`
- `demo-data-enabled=false`
- `membership-supplement-enabled=false`
- `production-bootstrap-enabled=false`

Under cloud/pilot policy, `RuntimeDataSeeder` does not:

- create or reset `owner`, `manager`, `staff`, `frontdesk`, or `kitchen` default credentials
- reset any password to `741xu741`
- create demo menu categories, menu items, menu item options, or demo option reconciliation
- run fried-noodle option activation/deactivation reconciliation
- create demo dining tables
- create the default `192.168.2.200` printer or printer assignments
- create ramen/noodle demo templates or KDS display configs
- attach stores to the demo `RAMEN_NOODLE_RESTAURANT` organization
- supplement memberships from legacy `users.store_id`

Strict production profiles (`cloud`, `prod`, `production`) fail startup if any of these are enabled:

- `app.seed.default-users-enabled=true`
- `app.seed.demo-data-enabled=true`
- `app.seed.membership-supplement-enabled=true`
- `app.seed.production-bootstrap-enabled=true`

Pilot profile fails startup if either of these are enabled:

- `app.seed.default-users-enabled=true`
- `app.seed.demo-data-enabled=true`

Production bootstrap is intentionally not implemented in PR4. A future PR must provide an explicit one-time owner/admin initialization path. Until then, cloud/pilot deployments should initialize real owner credentials through a reviewed operational script or migration/runbook, not through the local/demo default `owner / 741xu741` flow.

## Cloud Ready PR5: Cloud Printing Guard

PR5 adds a cloud/prod printing safety boundary. Strict production profiles (`cloud`, `prod`, `production`) must not open backend TCP sockets to store-private ESC/POS printers.

Blocked backend printer endpoints include:

- `10.0.0.0/8`
- `172.16.0.0/12`
- `192.168.0.0/16`
- `127.0.0.0/8`
- `localhost`
- `0.0.0.0`
- IPv4 link-local `169.254.0.0/16`
- IPv6 loopback, any-local, link-local, and site-local literals

The first implementation intentionally checks literal IPv4, literal IPv6, `localhost`, and `0.0.0.0` without DNS resolution. Domain names are not resolved during the guard check to avoid introducing DNS blocking during print dispatch. A future enhancement may add bounded DNS resolved-private-IP detection.

Cloud/prod behavior:

- `REAL` mode with a blocked private/local printer endpoint fails before socket connect.
- The related print job is marked `FAILED` with `error_code = CLOUD_PRIVATE_PRINTER_BLOCKED`.
- The error message tells operators to use `PAD_DIRECT`, `MOCK`, `DISABLED`, or a local print bridge.
- Order submit still succeeds; print failure does not roll back the order.
- Print Center keeps printer configuration visible and editable, but shows a cloud/private-printer warning when applicable.

Local/dev/pilot behavior:

- Local development and Windows/Mac store pilot deployments may continue using `REAL` mode with LAN printer IPs.
- `MOCK` still renders receipts and marks jobs printed without opening sockets.
- `PAD_DIRECT` still renders jobs and leaves them pending for Android Pad local printing; the backend does not connect to the LAN printer.
- `DISABLED` still prevents automatic dispatch using the existing cancelled-job behavior.

## Cloud Ready PR6: Print Failure Visibility Hardening

PR6 keeps the existing rule that print failures do not roll back order submission, but makes failures easier for staff and owners to see and act on.

Backend behavior:

- `PrintJobResponse` now includes `operator_message`.
- `operator_message` is generated dynamically from existing fields such as `status`, `error_code`, `module_code`, `receipt_type`, and `printing_mode` behavior.
- No database schema or migration is added.
- Existing raw `error_code` and `error_message` remain available for debugging.
- Mapped operator-facing cases include `CLOUD_PRIVATE_PRINTER_BLOCKED`, `PRINTING_DISABLED`, `ASSIGNMENT_MISSING`, `ASSIGNMENT_DISABLED`, `PRINTER_MISSING`, `PRINTER_DISABLED`, render failures, dispatch/connection failures, Pad Direct failures, and fallback unknown failures.

Frontend behavior:

- Frontdesk ordering checks relevant print jobs shortly after submit/update for `GRAB`, `FRONTDESK_RECEIPT`, `GRAB_UPDATE`, and `FRONTDESK_RECEIPT_UPDATE`.
- If a relevant job is `FAILED` or `CANCELLED`, the order is still saved and the page shows a visible warning that tells staff which ticket needs attention and where to reprint.
- After returning to the table board, `DineInPage` performs a short one-time check for the just-submitted order at approximately 1s, 3s, 6s, and 10s so asynchronous printer failures such as socket timeouts are still visible to frontdesk staff.
- Order History loads print jobs only when an order detail is opened, avoiding list-level N+1 requests.
- Order History displays print job status, operator message, raw error detail when useful, and keeps full-order reprint buttons.
- Print Center marks `FAILED` and `CANCELLED` jobs as needing attention, shows `error_code`, `operator_message`, and raw error details, and keeps manual reprint behavior unchanged.

Explicitly unchanged:

- No automatic retry system is introduced.
- Print Center job reprint still reprints from that job snapshot.
- Order Center reprint still prints the full current order.
- Print failures still do not roll back orders.
- `MOCK`, `REAL`, `PAD_DIRECT`, and `DISABLED` print modes keep their existing dispatch semantics.

## PR6.5: Chinese-First UI Copy Cleanup

PR6.5 is a display-only cleanup pass. It makes the most visible staff/owner UI copy Chinese-first without introducing a full i18n system and without changing backend business behavior.

Frontend behavior:

- Print Center uses Chinese-first labels for printer lists, printer assignments, print jobs, reprint actions, previews, connection tests, and failure/attention states.
- Frontdesk Ordering uses Chinese-first loading, submit/update, cancel, totals, and print-failure warning copy.
- Order History uses Chinese-first order detail, print job, reprint, status, empty-state, and refresh copy.
- Login, store access, and feature-disabled screens use Chinese-first user-facing copy.
- A lightweight frontend display helper centralizes module labels, print status labels, order status labels, and print error messages.

Stable internal codes are intentionally not translated:

- Print modules remain `GRAB`, `FRONTDESK_RECEIPT`, `HOT_KITCHEN`, `GRAB_UPDATE`, `FRONTDESK_RECEIPT_UPDATE`, and `HOT_KITCHEN_UPDATE`.
- Print statuses remain `PENDING`, `PRINTING`, `PRINTED`, `FAILED`, and `CANCELLED`.
- Print error codes such as `CLOUD_PRIVATE_PRINTER_BLOCKED`, `DISPATCH_ERROR`, and `PRINTING_DISABLED` remain stable English machine codes.
- Backend `BusinessException` messages, `GlobalExceptionHandler`, `PrintJobResponse` fields, receipt renderers, database schema, and print routing logic are unchanged.

Future language work:

- Full `zh-CN` / `en-US` i18n dictionaries, owner/store language preference, and backend `message_key + params` localization are deferred to a later PR.

## Cloud Ready PR7-1: Frontend API Context Cleanup

PR7-1 removes remaining frontend identity/store context shortcuts before cloud deployment.

Frontend REST rules:

- Business REST calls must use the shared `apiRequest` / `apiClient` path.
- Frontend code must not add `X-User-Id`; `apiClient` strips the header as a defense-in-depth measure.
- Production frontend paths must not hardcode `store_id=1`, `storeId=1`, or `DEFAULT_STORE_ID`.
- Draft order creation no longer sends `created_by: 1`; the backend fills `created_by` from the authenticated JWT user after store/capability authorization.

Admin context cleanup:

- Platform Admin draft JSON now uses the active store workspace context instead of falling back to store `1`.
- Menu Management and Dining Tables editor normalization use the active store as fallback when a backend record omits `store_id`.
- Missing related ids such as organization, category, station, role, or menu item are left empty/zero-like for the operator to correct instead of silently targeting store `1`.

Explicitly unchanged:

- KDS/Pickup polling cleanup is deferred to PR7-2.
- Order lifecycle, printing dispatch semantics, payment/refund, `completeOrder`, Android App code, and database schema are unchanged.

## Cloud Ready PR7-2: Printer Restaurant Mode KDS/Pickup Route Guard

PR7-2 documents the current Printer Restaurant Mode frontend boundary without changing business code.

Current Printer Restaurant Mode:

- `PRINTING=true`
- `KDS=false`
- `HOT_KITCHEN` printing module enabled

KDS/Pickup display behavior:

- When `KDS=false`, `/pickup`, `/kds/*`, `/stores/{storeId}/pickup`, and `/stores/{storeId}/kds/*` render `FeatureDisabledPage`.
- Because those pages do not mount, KDS/Pickup hooks do not run.
- Frontdesk, Order Center, Owner/Admin, Print Center, Menu, Dining Tables, Staff, Audit, and Reports pages should not create `/api/v1/kds/*`, `/api/v1/kitchen-tasks/*`, or KDS WebSocket polling/subscriptions while KDS is disabled.

Important distinction:

- `HOT_KITCHEN` printing module and `HOT_KITCHEN` KDS display page are separate features.
- Disabling KDS/Pickup display pages does not disable `HOT_KITCHEN` print assignment, Test HOT_KITCHEN, automatic HOT_KITCHEN print job creation, Print Center visibility, or reprint behavior.

Future work:

- If `KDS=true` is re-enabled, do a dedicated KDS/Pickup polling cleanup PR before production use. Current KDS/Pickup display hooks still use short polling and should be converted to WebSocket-first, visibility-aware fallback polling before the feature is used operationally.

## Cloud Ready PR7A: HOT_KITCHEN Print Routing With Stable Semantics

PR7A enables the `HOT_KITCHEN` printing module for heat-line / fry / wok / fried-egg workflows while keeping `COLD_KITCHEN`, `BAR`, and `TAKEOUT_RECEIPT` reserved.

Routing rules:

- Fried items route to `HOT_KITCHEN` by stable station/category metadata:
  - primary: `kitchen_tasks.station_code = DEEPFRIED`
  - fallback: `order_items.category_code_snapshot in (FRIED, DEEPFRIED)`
- Chow mein routes to `HOT_KITCHEN` by stable station/category/SKU metadata:
  - primary: `kitchen_tasks.station_code = WOK`
  - fallback: `order_items.category_code_snapshot = FRIED_NOODLE`
  - fallback: known chow-mein `menu_items.sku`
- Noodle items route to `HOT_KITCHEN` only when they include fried egg semantics:
  - extra fried egg: `order_item_options.option_code_snapshot = fried_egg`
  - combo fried egg: `option_group_snapshot = COMBO_EGG` and `option_code_snapshot = combo_fried_egg`
  - current `menu_item_options.option_code` is used only as fallback for older snapshots.
  - Chinese/English display-name checks are legacy fallback only and are centralized in `OptionSemanticResolver`.

Dispatch behavior:

- Initial order submit still dispatches `GRAB` and `FRONTDESK_RECEIPT`.
- Submit additionally dispatches `HOT_KITCHEN` only when the order has hot-kitchen content.
- Update Order still dispatches `GRAB_UPDATE` and `FRONTDESK_RECEIPT_UPDATE`.
- Update Order additionally dispatches `HOT_KITCHEN_UPDATE` only when the current `order_update_batch_id` has hot-kitchen content.
- If an order or update batch has no hot-kitchen content, no `HOT_KITCHEN` print job is created. This avoids blank failed jobs.
- Manual order reprint supports `HOT_KITCHEN`, but rejects orders without hot-kitchen content before creating a print job.
- `HOT_KITCHEN` Test Module Print uses the real renderer path with synthetic wok and fried-egg examples.
- Combo side tasks such as combo edamame, shredded potato, or cucumber salad do not inherit the main combo item's fried-egg eligibility. Synthetic combo side kitchen tasks route to `HOT_KITCHEN` only if their own task station is hot, such as `DEEPFRIED` or `WOK`.

Renderer behavior:

- `HotKitchenReceiptRenderer` renders only eligible hot-kitchen tasks.
- For noodle items routed because of fried egg, the ticket prints the whole kitchen-facing item line, not just an isolated egg.
- The renderer reuses `kitchen_tasks.special_instructions_snapshot` so HOT_KITCHEN output stays aligned with existing GRAB/kitchen shorthand where possible.

Print Center behavior:

- `HOT_KITCHEN` assignment is active and editable.
- `Test HOT_KITCHEN` is available.
- `COLD_KITCHEN`, `BAR`, and `TAKEOUT_RECEIPT` remain Future / Reserved.

Explicitly unchanged:

- GRAB and FRONTDESK_RECEIPT routing/rendering semantics are unchanged.
- Print failures still do not roll back orders.
- No automatic retry system is introduced.
- No database migration is added.
- Payment/refund, completeOrder semantics, Android Pad, Pad Direct worker, and order lifecycle are unchanged.

## Pad App Architecture PR 1

`doc/PAD_APP_ARCHITECTURE.md` defines the proposed independent Android Pad shell architecture. This is documentation only and does not change runtime behavior.

Key decisions:

- `Restaurant_System` remains the stable Web + Backend source of truth.
- A separate `restaurant-pad-app` should host the Android WebView / Capacitor shell.
- The Pad App should reuse the current React frontend build instead of rewriting POS UI.
- Backend remains responsible for orders, menu, auth, store workspace, owner multi-store access, print jobs, and receipt rendering.
- Android native code should execute local LAN ESC/POS printing through a Pad-local printer bridge.
- `PAD_DIRECT` printing mode creates and renders print jobs on the backend while the Pad claims, prints, and reports completion/failure locally.
- The Android WebView shell serves the bundled frontend from `https://restaurant-pad.local`; backend REST APIs under `/api/**` must allow that origin through CORS. WebSocket origin configuration alone does not cover login or other REST calls.
- Cloud servers must not directly connect to store-private `192.168.x.x` printers.
- Current `MOCK`, `REAL`, and `DISABLED` printing modes must remain available for local testing, Windows pilot, and operations.

The document also records the claim/lease anti-duplicate-print mechanism, device registration APIs, `print_jobs` / `store_devices` schema additions, Android native printing POC plan, environment configuration, risks, and follow-up PR sequence.

`doc/PAD_APP_PR_PROMPTS.md` expands the follow-up work into PR2-PR8 execution prompts. It is a planning document only and does not implement Android, backend APIs, database migrations, frontend runtime changes, or printing behavior changes.

Pad App PR2 has added an independent `restaurant-pad-app/` skeleton with documentation and placeholder directories only. It does not add Android runtime files, install dependencies, load frontend assets, implement printing, or modify existing backend/frontend business behavior.

Pad App PR3 adds an independent Android WebView shell POC under `restaurant-pad-app/android`. It is isolated from the current Restaurant_System frontend/backend runtime. The shell serves bundled assets from Android assets, falls back to `index.html` for path-based routes, and stores a runtime API base URL in Android preferences. It does not implement printing, `PAD_DIRECT`, or print job integration.

Pad App PR4 adds a code-level native TCP printer test POC under `restaurant-pad-app/android`. It exposes `RestaurantPrinter.testConnection(...)` and `RestaurantPrinter.printRawTcp(...)` to a Pad-only test page. This POC sends raw bytes only, does not print restaurant orders, does not use `print_jobs`, and requires manual hardware QA with a real Android Pad and LAN ESC/POS printer.

Pad App PR5 adds backend/frontend recognition for `PAD_DIRECT` printing mode. In `PAD_DIRECT`, the backend creates `print_jobs`, resolves assignment/printer, renders `rendered_text_snapshot`, and leaves jobs `PENDING` for Android Pad local printing. The backend does not open TCP printer sockets in this mode. Existing `MOCK`, `REAL`, and `DISABLED` modes remain available.

Pad App PR6 adds backend device registration and Pad Direct print job APIs. Store devices are recorded in `store_devices`; the raw device token is returned only at registration and only a SHA-256 hash is stored. Pad clients authenticate with `X-Device-Id` and `X-Device-Token`, poll pending jobs for their store, claim a job with a lease, fetch the rendered/ESC-POS base64 payload, and report complete/fail/release. Claiming uses an atomic database update so only one Pad can own a `PENDING` job at a time; expired claims can be reclaimed.

Pad Direct JPA entities use camelCase Java properties mapped to snake_case database columns with `@Column(name = "...")`. Spring Data derived repository methods must reference Java property names such as `storeId`, `deviceTokenHash`, `claimedByDeviceId`, and `claimExpiresAt`, never database column names such as `store_id`.

Pad App PR7 surfaces the PR6 queue state in Print Center. `/admin/settings/printing` shows registered Pad Direct devices, last seen/app/platform status, job execution mode, claimed device id, claim lease expiration, printed device id, and whether a Pad Direct preview has an ESC/POS base64 payload.

Pad App PR11A adds a debug/local Web App URL mode to the Android shell. The shell can now load `http://{developer-lan-ip}:5173` directly for LAN production-preview testing, letting the frontend continue to use relative `/api` and `/ws` paths through the Vite preview proxy to backend `localhost:8080`. If no Web App URL is configured, the existing bundled assets mode at `https://restaurant-pad.local/index.html` remains available with the runtime API Base URL bridge. Debug builds allow local HTTP cleartext and WebView debugging; release/default assets mode still keeps cleartext disabled by default.

## Web POS Field Compatibility Fixes

- Submitted/preparing/ready order updates still require an `idempotency_key`, but the frontend now generates it through a compatibility helper instead of directly calling `crypto.randomUUID()`. The helper falls back to `crypto.getRandomValues()` and then a timestamp/random suffix for older iPad/Pad browsers on local HTTP pages.
- GRAB kitchen instructions keep `加上海青` as a full modifier name. Green onion/cilantro can still use the existing green-shortening rules, but bok choy must not be collapsed to `加青`.
- The shared frontend API client applies request timeouts and normalizes offline/network-timeout failures so long-running frontdesk/menu pages can recover after Android browser network suspension instead of leaving requests pending indefinitely.

## Phase 1 Store Access Backend Scope

Backend store scope is now based on explicit memberships instead of relying only on `users.store_id`.

- `organization_memberships` records which organizations a user belongs to and the user's organization-level role.
- `store_memberships` records which stores a user can access and the user's store-level role.
- `users.store_id` remains as a legacy/default store for compatibility and login token claims.
- `StoreAccessService` is the shared backend authority for store access checks.
- `ADMIN` remains the platform/legacy admin role and can access all stores.
- `OWNER` no longer bypasses store scope by role alone; an owner can access stores inside active `organization_memberships`.
- `MANAGER`, `FRONTDESK`, `HOT_KITCHEN`, `NOODLE_VIEW`, and `PASS` require active `store_memberships`.
- A legacy fallback allows `users.store_id` only when the user has no active organization or store memberships, so older local data can still boot before the seeder has filled memberships.
- Runtime seeding adds missing memberships for existing users and dev role switcher users, but it does not overwrite or delete existing membership records.
- Phase 1 QA tightened workspace listing to use the same fallback rule: `users.store_id` appears in workspaces only when the user has no active membership records.

New backend APIs:

- `GET /api/v1/me/workspaces`: returns accessible organizations, stores, and the user's default store.
- `GET /api/v1/stores/{storeId}/context`: returns store context only after `StoreAccessService` authorizes the current user for that store.

Phase 1 intentionally does not change frontend routes, does not add Store Switcher UI, and does not introduce `/stores/:storeId/...` workspace routes. Those are Phase 2 concerns.

## Phase 1 Add-Only Order Updates and Read-Only History

Submitted order editing now uses immutable old lines plus transactional update batches:

- `orders.current_revision` tracks the latest committed order revision.
- `order_update_batches` stores one revision per update and enforces unique `(order_id, revision)` and `(order_id, idempotency_key)` constraints.
- New update items store `order_items.added_revision` and `order_items.order_update_batch_id`.
- `POST /api/v1/orders/{orderId}/updates` performs batch creation, new item insertion, kitchen/frontdesk production task creation, inventory deduction, total recalculation, and revision update in one transaction.
- The order row is pessimistically locked while checking idempotency, so duplicate taps with the same key do not duplicate items or GRAB update jobs.
- Printing is registered after commit. A print failure cannot roll back the order update.
- `is_modified_after_submit` remains a UI/KDS indicator only; it is not used to select update ticket lines.
- GRAB update jobs use receipt type `GRAB_UPDATE`, retain `order_update_batch_id`, and render only items tagged with that exact batch.
- FRONTDESK update jobs use receipt type `FRONTDESK_RECEIPT_UPDATE`, retain `order_update_batch_id`, and render only items tagged with that exact batch.
- FRONTDESK update receipts are marked `UPDATED` / `Added items only`; their subtotal, tax, and total are calculated from the added batch lines rather than the full order.
- FRONTDESK receipts display combo side dishes under the combo main item, including combo side remove instructions such as `走花生`, so staff can pack takeout combo sides correctly.
- Manual reprint always renders the complete current order.

The occupied table card now exposes `Edit Order`, `Print`, and `Finish` in that order. Print choices come from `GET /api/v1/orders/{orderId}/print-options`; unavailable modules include a reason rather than being hardcoded in the client.

`/frontdesk/order` is now a read-only today-order history page. It has no checkout, split-bill, cash/payment, or completion controls. The first request loads up to 100 lightweight summaries from `/api/v1/frontdesk/orders/today`; details and print options are loaded only after selecting an order. The today endpoint treats an order as visible when any operational timestamp (`created_at`, `submitted_at`, `updated_at`, or `completed_at`) falls within the current local day, so a draft created earlier but submitted today still appears in frontdesk history. Summary construction batch-loads beverage rows for all listed order IDs, removing the prior per-order beverage query pattern.

Owner/admin pages use a single shared `OwnerAdminShell` from the application route layer. Individual admin content pages such as Menu Management, Dining Tables, Printing Settings, Staff Management, Audit Logs, Dashboard, and Reports must render only their page content and must not create a second Owner Console sidebar/header internally.
Audit Logs is available through `/admin/audit-logs`; `/admin/audit` is accepted as a compatibility alias and still renders inside the same single Owner Admin shell.

Database compatibility:

- The project currently uses JPA `ddl-auto=update`; the new table and nullable revision columns are added on application startup.
- Existing orders with null `current_revision` are interpreted as revision `1` and are assigned the next revision on their first update.
- Existing order items remain valid with null update batch fields and are treated as original/locked submitted items.

Takeout receipt copies remain assignment-driven through `printer_assignments.takeout_receipt_copies`. `FRONTDESK_RECEIPT` resolves one copy for dine-in regardless of configuration, and one or two copies for `pickup`/`takeout`; GRAB always resolves one copy.

## Order Tax Policy

- The active order tax rate is `14.975%`.
- Backend order totals use `TaxCalculator`:
  - `backend/src/main/java/com/restaurant/system/common/pricing/TaxCalculator.java`
- Frontend order summary displays use the matching shared utility:
  - `frontend/src/utils/tax.ts`
- Tax is calculated from the full order subtotal and rounded once to cents with half-up style rounding.
- Example:
  - subtotal `$1.99`
  - tax `$0.30`
  - total `$2.29`
- The current schema has no dedicated tax column, so persisted orders store:
  - `orders.subtotal_amount`
  - `orders.total_amount = subtotal + rounded tax`
- `FRONTDESK_RECEIPT` derives the tax amount as `orders.total_amount - orders.subtotal_amount` and labels it as `Tax (14.975%)`.

## 1. Project Overview

### 1.1 Tech Stack

#### Backend
- Java 17
- Spring Boot 3.3.3
- Spring Web
- Spring Validation
- Spring Data JPA
- Spring JDBC
- MyBatis-Plus 3.5.7
- Spring WebSocket + STOMP

#### Database
- PostgreSQL
- Runtime driver: `org.postgresql:postgresql`
- JPA `ddl-auto` setting: `update`

#### Frontend
- React 19.2.0
- TypeScript 5.9.x
- Vite 7.1.x
- Tailwind CSS 4.1.x

#### Realtime
- Spring WebSocket
- STOMP broker with `/topic`
- WebSocket endpoint: `/ws`
- Frontdesk pickup / handoff board uses WebSocket subscription to serving-shelf updates
- Current noodle KDS frontend screen does not use WebSocket yet.
- Current noodle KDS frontend screen uses polling every 4 seconds.

### 1.2 Project Structure

```text
Restaurant_System/
├── AGENTS.md
├── README.md
├── SYSTEM_DOCUMENTATION.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/restaurant/system/
│       │   │   ├── analytics/
│       │   │   ├── common/
│       │   │   ├── inventory/
│       │   │   ├── kitchen/
│       │   │   ├── menu/
│       │   │   ├── order/
│       │   │   ├── production/
│       │   │   ├── station/
│       │   │   └── user/
│       │   └── resources/
│       └── test/
├── doc/
└── frontend/
    ├── package.json
    └── src/
        ├── components/
        ├── data/
        ├── features/
        ├── pages/
        ├── services/
        └── types/
```

### 1.3 Backend Modules Present in Code

- `analytics`
- `common`
- `inventory`
- `kitchen`
- `menu`
- `order`
- `production`
- `station`
- `user`

### 1.4 Frontend Status

- The frontend currently implements:
  - table selection screen
  - ordering page
  - item customization modal
  - pickup / handoff board
- In iPad landscape, frontdesk pages use a shared top-navbar workstation shell.
- The frontdesk entry page no longer uses a table search box in the main top bar.
- Takeout entry is started from a dedicated `Takeout / 外带` button on the main frontdesk page.
- Clicking that button now opens the same shared ordering page used by dine-in immediately in `pickup` mode.
- The frontend generates a temporary takeout pickup label automatically for that first entry.
- A customer name or phone number can be added later from inside the shared ordering page and is optional for walk-in takeout flow.
- The ordering page menu read layer is connected to the backend menu catalog API.
- Menu categories, menu items, and customization options are fetched from:
  - `GET /api/v1/menu/catalog?store_id=1`
  - request header `X-User-Id: 1`
- The ordering page order-edit layer is partially connected to backend order APIs.
- Current frontend order-edit behavior uses backend APIs for:
  - find/reuse editable order by table slot
  - create draft order
  - add draft item
  - update draft item
  - update draft item quantity
  - remove draft item
  - cancel draft order
- submit draft order
- Submit-order flow is now connected from the frontend ordering page.
- Owner Admin `Sales Report` now uses a split presentation rule:
  - the trend chart always keeps the full selected date range visually, including zero-sales dates
  - the `Daily Summary Table` hides rows where both sales and orders are `0`
  - if no non-zero days exist in the selected range, the table shows `No sales data for this period.`
- The `Sales Trend` chart now shows compact value labels only for non-zero bars:
  - examples: `$727`, `$2.0k`, `$65`
  - zero-value bars do not render `$0.00`
  - exact value and order count remain available through hover tooltip text
- The analytics layer now includes profit-oriented fields:
  - `menu_items.cost_per_item`
  - `sales_daily_summary.total_cost`
  - `sales_daily_summary.total_profit`
  - `sales_daily_summary.profit_margin`
  - `menu_item_sales_summary.total_cost`
  - `menu_item_sales_summary.total_profit`
- Profit values are derived only from analytics summary tables in reports:
  - item total cost = `quantity_sold * cost_per_item`
  - item total profit = `sales_amount - total_cost`
  - daily total profit = `net_sales - total_cost`
  - daily profit margin = `total_profit / net_sales`
- Owner Admin Reports now include `/admin/reports/profit`:
  - KPI cards for total sales, total cost, total profit, and profit margin
  - top profitable items
  - worst margin items
  - daily profit trend
  - profit by day table
- Profit analytics do not change POS, KDS, Frontdesk, or Platform Admin behavior.
- If `menu_items.cost_per_item` is missing or `0`, profit analytics will treat that item cost as `0` until restaurant cost data is maintained.
- Owner Admin now includes a menu maintenance module at `/admin/menu/items`.
- The owner menu management page uses existing database-driven configuration:
  - `menu_items`
  - `menu_categories`
  - `stations`
- The page displays and edits:
  - item name zh/en
  - category
  - station
  - `base_price`
  - `cost_per_item`
  - `is_active`
  - `is_sold_out`
- Menu item updates are saved through the existing admin platform API:
  - `GET /api/v1/admin/platform/menu/items?store_id=...`
  - `POST /api/v1/admin/platform/menu/items`
  - `PUT /api/v1/admin/platform/menu/items/{id}`
- `PlatformAdminServiceImpl.saveMenuItem(...)` now persists `cost_per_item`.
- The owner menu page now verifies save results by:
  - saving through the existing backend API
  - reloading menu items from `GET /api/v1/admin/platform/menu/items?store_id=...`
  - showing success only after persisted values match the saved form values
  - keeping the edit form open and showing an error if backend verification fails
- The owner menu page supports search and filters:
  - search by Chinese name
  - search by English name
  - search by SKU
  - filter by category
  - filter by station
  - filter by status: `All`, `Active`, `Inactive`, `Sold Out`, `Available`
- The owner menu page supports quick row-level toggles:
  - `Active / Inactive`
  - `Sold Out / Available`
  - each toggle saves to backend and refreshes the row from backend data
- The owner menu page shows an explicit rebuild warning when price or cost changes are saved:
  - `Price or cost changes affect future orders immediately. Historical profit reports require analytics rebuild.`
- The owner menu page includes a `Rebuild Analytics` action:
  - uses the existing backend API `POST /api/v1/admin/analytics/rebuild?date=YYYY-MM-DD&store_id=...`
  - currently rebuilds one selected summary date at a time

### 1.5 Table System

Current frontend table behavior in code:

- Table rendering is configuration-driven per table.
- Each table has a configuration field:
  - `split_supported`
  - `single_only`
- Each table also has a separate runtime occupancy field:
  - `empty`
  - `full`
  - `split`
- Each rendered card represents one order slot.
- The UI is flat. There is no parent-child nesting between `T1` and `T1-A` / `T1-B`.
- Valid rendered slot labels are:
  - `T1` for full-table mode
  - `T1-A`
  - `T1-B`
- Rendering rules in current code:
  - `single_only` + `empty` -> render only `T1` with `Start order`
  - `single_only` + `full` -> render only `T1` with `Edit order`
  - `split_supported` + `empty` -> render only `T1` with `Left`, `Right`, and `Full` entry buttons
  - split table -> render `T1-A` and `T1-B`
  - full-table order -> render only `T1`
- `T1` is never rendered together with `T1-A` or `T1-B`.
- Empty split slots render `Start order`.
- Occupied slots render `Edit order`.
- Empty table entry actions behave like this:
  - on `split_supported` tables:
    - `Left` -> local mock state becomes split mode with `T1-A` occupied
    - `Right` -> local mock state becomes split mode with `T1-B` occupied
    - `Full` -> local mock state becomes full-table mode with `T1` occupied
  - on `single_only` tables:
    - `Start order` -> local mock state becomes full-table mode with `T1` occupied
- The local table-state hook also contains `endOrder(tableLabel, target)` so slot release logic exists in code for:
  - ending `T1-A`
  - ending `T1-B`
  - ending full-table `T1`
- Start/edit actions open the ordering page.
- The table board derives occupancy from backend active orders only, using table config only as the base shape.
- The board does not preserve local occupied/split state after finish or cancel.
- Active occupancy is read through:
  - `GET /api/v1/frontdesk/orders?store_id=1&status=draft&status=submitted&status=preparing&status=ready`
- Outside iPad landscape mode, the frontdesk table board sidebar is collapsible:
  - expanded: icon + label navigation
  - collapsed: icon-only rail
- The frontend now has a dedicated `iPad landscape workstation` layout mode across frontdesk pages:
  - activated by client viewport detection for landscape widths roughly in the iPad range
  - uses a shared top-navbar shell instead of the left sidebar shell
  - table board uses a denser 4-column layout on smaller iPads
  - table board expands to 5 columns on larger iPad landscape widths when space allows
  - compacts the top bar, legend row, and table cards to maximize one-screen table visibility
  - ordering page keeps the existing 3-column workflow under the same shared top navbar
- Operational pages outside frontdesk also use the same compact iPad-landscape density rule:
  - reduced header and card padding
  - tighter grid gaps
  - more content visible per screen while keeping touch-safe controls
- In this compact mode, split-table entry actions use a denser layout:
  - `Left` and `Right` on the first row
  - `Full table` on the second row
- If the backend reports an active order on a slot, the slot is rendered as occupied/editable even if the local mock seed was empty.
- Empty draft orders with zero active items are excluded from the frontdesk active board and do not keep a table or split seat occupied.
- Occupied/editable slots with a real backend `order_id` also render a `Finish` button below `Edit order`.
- `Finish` is the normal frontdesk completion flow, not cancellation.
- `Finish` is shown only for active editable orders in status:
  - `submitted`
  - `preparing`
  - `ready`
- Draft-only placeholder orders do not show `Finish`.
- Clicking `Finish` shows a confirmation dialog, then calls:
  - `POST /api/v1/orders/{id}/complete`
- After a successful complete response, the table board refreshes backend occupancy and the slot returns to available when no other active order remains on that slot.
- For split tables, finishing `T1-A` only frees `T1-A`; `T1-B` remains occupied if it still has its own active order. When both sides are completed, the table is fully available again.
- The frontend performs a short follow-up refresh sequence after `Finish` so split tables collapse back automatically once the backend active-order board reflects the completion.
- On split-supported tables, each `Finish` also performs an explicit same-table check:
  - if the other seat still has an active order, keep split view
  - if neither seat has an active order, collapse back to the main available table immediately
- Frontend draft initialization also de-duplicates concurrent `ensureEditableOrder(slotLabel)` calls for the same slot so React development double-mount behavior does not create duplicate empty draft orders.

## 2. Backend Architecture (Spring Boot)

### 2.1 Application Entry and Shared Infrastructure

#### Main Application
- `backend/src/main/java/com/restaurant/system/RestaurantSystemApplication.java`

#### Shared Backend Components
- `common/config/MybatisPlusConfig.java`
- `common/config/RuntimeDataSeeder.java`
  - controlled by:
    - `app.seed.runtime-enabled`
    - `app.seed.force-overwrite`
  - default mode is missing-data supplement only
- `common/config/WebSocketConfig.java`
- `common/response/ApiResponse.java`
- `common/exception/BusinessException.java`
- `common/exception/GlobalExceptionHandler.java`
- `common/auth/*`
- `common/realtime/*`

### 2.2 Controller List

#### Order Module
- `OrderController`
- `FrontdeskOrderController`
- `FrontdeskBeverageController`

#### Kitchen Module
- `KitchenController`
- `KdsController`

#### Analytics Module
- `AnalyticsAdminController`

#### Placeholder / Health Controllers
- `InventoryController`
- `StationController`
- `UserController`

#### Menu Controller
- `MenuController`

### 2.3 API Endpoints Overview

All normal API responses use this wrapper:

```json
{
  "success": true,
  "message": "string or null",
  "data": {}
}
```

Validation and business errors use the same wrapper with `success = false`.

### 2.4 Detailed Controllers and Endpoints

#### OrderController

Base path: `/api/v1/orders`

##### POST /api/v1/orders
- Method: `POST`
- Auth capability: `order:create`
- Request body DTO: `CreateOrderRequest`
- Response type: `ApiResponse<OrderResponse>`
- Current backend behavior: if the same table or pickup slot already has an editable order in status `draft`, `submitted`, or `preparing`, this endpoint returns that existing order instead of creating a duplicate draft.

Request DTO structure:

```json
{
  "store_id": 0,
  "created_by": 0,
  "order_type": "string",
  "table_no": "string or null",
  "pickup_no": "string or null",
  "items": [
    {
      "menu_item_id": 0,
      "quantity": 1,
      "combo_group_no": 0,
      "combo_role": "string",
      "notes": "string or null",
      "options": [
        {
          "option_id": 0,
          "quantity": 1
        }
      ]
    }
  ]
}
```

##### GET /api/v1/orders/{id}
- Method: `GET`
- Auth capability: `order:view_detail`
- Request body: `NONE`
- Response type: `ApiResponse<OrderResponse>`

##### GET /api/v1/orders/draft-open
- Method: `GET`
- Auth capability: `order:create`
- Query params:
  - `store_id` required
  - `table_no` optional
  - `pickup_no` optional
- Request body: `NONE`
- Response type: `ApiResponse<OrderResponse | null>`

##### GET /api/v1/orders/open-editable
- Method: `GET`
- Auth capability: `order:view_detail`
- Query params:
  - `store_id` required
  - `table_no` optional
  - `pickup_no` optional
- Request body: `NONE`
- Response type: `ApiResponse<OrderResponse | null>`

##### PUT /api/v1/orders/{id}/draft-header
- Method: `PUT`
- Auth capability: `order:edit_draft` or `order:modify_submitted`
- Request body DTO: `UpdateDraftOrderHeaderRequest`
- Response type: `ApiResponse<OrderResponse>`

Request DTO structure:

```json
{
  "order_type": "string or null",
  "table_no": "string or null",
  "pickup_no": "string or null"
}
```

##### POST /api/v1/orders/{id}/items
- Method: `POST`
- Auth capability: `order:edit_draft` or `order:modify_submitted`
- Request body DTO: `CreateOrderItemRequest`
- Response type: `ApiResponse<OrderResponse>`

##### PUT /api/v1/orders/{id}/items/{itemId}/quantity
- Method: `PUT`
- Auth capability: `order:edit_draft` or `order:modify_submitted`
- Request body DTO: `UpdateDraftOrderItemQuantityRequest`
- Response type: `ApiResponse<OrderResponse>`

Request DTO structure:

```json
{
  "quantity": 1
}
```

##### PUT /api/v1/orders/{id}/items/{itemId}
- Method: `PUT`
- Auth capability: `order:edit_draft` or `order:modify_submitted`
- Request body DTO: `UpdateDraftOrderItemRequest`
- Response type: `ApiResponse<OrderResponse>`

Request DTO structure:

```json
{
  "quantity": 1,
  "combo_group_no": 0,
  "combo_role": "string",
  "notes": "string or null",
  "options": [
    {
      "option_id": 0,
      "quantity": 1
    }
  ]
}
```

##### DELETE /api/v1/orders/{id}/items/{itemId}
- Method: `DELETE`
- Auth capability: `order:edit_draft` or `order:modify_submitted`
- Request body: `NONE`
- Response type: `ApiResponse<OrderResponse>`

##### POST /api/v1/orders/{id}/submit
- Method: `POST`
- Auth capability: `order:submit`
- Request body: `NONE`
- Response type: `ApiResponse<OrderResponse>`

##### GET /api/v1/orders/active
- Method: `GET`
- Auth capability: `order:view_active`
- Query params:
  - `store_id` required
  - `status` optional, repeated list
  - `order_type` optional
  - `sort_by` optional
- Response type: `ApiResponse<List<OrderResponse>>`

##### POST /api/v1/orders/{id}/complete
- Method: `POST`
- Auth capability: `order:complete`
- Request body: `NONE`
- Response type: `ApiResponse<OrderResponse>`

##### POST /api/v1/orders/{id}/cancel
- Method: `POST`
- Auth capability: `order:cancel`
- Request body: `NONE`
- Response type: `ApiResponse<OrderResponse>`

#### FrontdeskOrderController

Base path: `/api/v1/frontdesk/orders`

##### GET /api/v1/frontdesk/orders
- Method: `GET`
- Auth capability: `order:view_active`
- Query params:
  - `store_id` required
  - `status` optional list
  - `order_type` optional
  - `table_no` optional
  - `pickup_no` optional
  - `keyword` optional
- Response type: `ApiResponse<List<FrontdeskOrderBoardResponse>>`

##### GET /api/v1/frontdesk/orders/history
- Method: `GET`
- Auth capability: `order:view_history`
- Query params:
  - `store_id` required
  - `status` optional list
  - `order_type` optional
  - `table_no` optional
  - `pickup_no` optional
  - `keyword` optional
  - `limit` optional
- Response type: `ApiResponse<List<FrontdeskOrderBoardResponse>>`
- Sorting behavior:
  - completed orders sort by `completed_at` descending
  - cancelled orders sort by `updated_at` descending
  - ties break by `order_id` descending

#### FrontdeskBeverageController

Base path: `/api/v1/frontdesk/beverages`

##### GET /api/v1/frontdesk/beverages
- Method: `GET`
- Auth capability: `beverage:view_board`
- Query params:
  - `store_id` required
  - `status` optional list
- Response type: `ApiResponse<List<FrontdeskBeverageItemResponse>>`

##### POST /api/v1/frontdesk/beverages/{orderItemId}/start
- Method: `POST`
- Auth capability: `beverage:start`
- Request body: `NONE`
- Response type: `ApiResponse<FrontdeskBeverageItemResponse>`

##### POST /api/v1/frontdesk/beverages/{orderItemId}/ready
- Method: `POST`
- Auth capability: `beverage:ready`
- Request body: `NONE`
- Response type: `ApiResponse<FrontdeskBeverageItemResponse>`

##### POST /api/v1/frontdesk/beverages/{orderItemId}/served
- Method: `POST`
- Auth capability: `beverage:served`
- Request body: `NONE`
- Response type: `ApiResponse<FrontdeskBeverageItemResponse>`

##### POST /api/v1/frontdesk/beverages/{orderItemId}/cancel
- Method: `POST`
- Auth capability: `beverage:cancel`
- Request body: `NONE`
- Response type: `ApiResponse<FrontdeskBeverageItemResponse>`

#### KitchenController

Base path: `/api/v1/kitchen-tasks`

##### GET /api/v1/kitchen-tasks/health
- Method: `GET`
- Auth: `NONE`
- Request body: `NONE`
- Response type: `ApiResponse<String>`

##### GET /api/v1/kitchen-tasks
- Method: `GET`
- Auth capability: `kds:hot:view`
- Query params:
  - `store_id` required
  - `station_code` optional
- Response type: `ApiResponse<List<KitchenTaskResponse>>`

##### POST /api/v1/kitchen-tasks/{id}/start
- Method: `POST`
- Auth capability: `kds:hot:start`
- Request body: `NONE`
- Response type: `ApiResponse<KitchenTaskResponse>`

##### POST /api/v1/kitchen-tasks/{id}/ready-for-pickup
- Method: `POST`
- Auth capability: `kds:hot:ready_for_pickup` or `kds:pass:ready_for_pickup`
- Request body: `NONE`
- Response type: `ApiResponse<KitchenTaskResponse>`

##### POST /api/v1/kitchen-tasks/{id}/served
- Method: `POST`
- Auth capability: `shelf:served`
- Request body: `NONE`
- Response type: `ApiResponse<KitchenTaskResponse>`

##### POST /api/v1/kitchen-tasks/{id}/complete
- Method: `POST`
- Auth capability: `kds:hot:ready_for_pickup` or `kds:pass:ready_for_pickup`
- Request body: `NONE`
- Response type: `ApiResponse<KitchenTaskResponse>`
- Note: in code this is an alias to `ready-for-pickup`

#### KdsController

Base path: `/api/v1/kds`

##### GET /api/v1/kds/noodle-display
- Method: `GET`
- Auth capability: `kds:noodle:view`
- Query params:
  - `store_id` required
  - `limit` optional
- Response type: `ApiResponse<List<KdsTaskDisplayResponse>>`

##### GET /api/v1/kds/hot-kitchen
- Method: `GET`
- Auth capability: `kds:hot:view`
- Query params:
  - `store_id` required
- Response type: `ApiResponse<List<KdsTaskDisplayResponse>>`

##### GET /api/v1/kds/pass
- Method: `GET`
- Auth capability: `kds:pass:view`
- Query params:
  - `store_id` required
- Response type: `ApiResponse<List<KdsOrderGroupResponse>>`

##### GET /api/v1/kds/frontdesk-beverages
- Method: `GET`
- Auth capability: `beverage:view_board`
- Query params:
  - `store_id` required
- Response type: `ApiResponse<List<FrontdeskBeverageOrderResponse>>`

##### GET /api/v1/kds/serving-shelf
- Method: `GET`
- Auth capability: `shelf:view`
- Query params:
  - `store_id` required
- Response type: `ApiResponse<List<ServingShelfItemResponse>>`
- Current backend behavior:
  - returns serving-shelf rows for kitchen tasks already marked `ready_for_pickup`
  - excludes orders whose `order_type = delivery`
  - enriches each row with:
    - `order_item_id`
    - `order_type`
    - `category_code_snapshot`

##### GET /api/v1/kds/history
- Method: `GET`
- Auth capability: `kds:hot:view` or `kds:pass:view`
- Query params:
  - `store_id` required
  - `limit` optional
  - `station_code` optional
- Response type: `ApiResponse<List<KdsOrderGroupResponse>>`

#### Health Controllers

##### GET /api/v1/inventory/health
##### GET /api/v1/menu/health
##### GET /api/v1/stations/health
##### GET /api/v1/users/health

All four:
- Method: `GET`
- Request body: `NONE`
- Response type: `ApiResponse<String>`

#### Menu Controller

##### GET /api/v1/menu/catalog
- Method: `GET`
- Auth capability: `order:create`
- Query params:
  - `store_id` required
- Response type: `ApiResponse<MenuCatalogResponse>`

### 2.5 Service Layer Overview

#### OrderService / OrderServiceImpl

Implemented responsibilities in current code:
- Create draft order
- Read order detail
- Edit draft order header and items
- Modify submitted/preparing/ready orders with restrictions
- Submit order
- Generate kitchen tasks on submit
- Generate frontdesk beverage operational records on submit
- Deduct inventory on submit
- Create inventory transaction records
- Complete order
- Cancel order
- Query active orders
- Query frontdesk order board and history
- Publish realtime events

#### FrontdeskBeverageService / FrontdeskBeverageServiceImpl

Implemented responsibilities:
- Beverage board query
- Start beverage preparation
- Mark beverage ready
- Mark beverage served
- Cancel beverage item
- Publish realtime events

#### KitchenService / KitchenServiceImpl

Implemented responsibilities:
- Query kitchen tasks
- Start task
- Mark task ready for pickup
- Mark task served
- Alias complete -> ready for pickup
- Auto-update order to `ready` when all kitchen-required tasks are at least `ready_for_pickup` or `served`
- Publish realtime events

#### KdsService / KdsServiceImpl

Implemented responsibilities:
- Noodle display query
- Hot kitchen screen query
- Pass screen query
- Frontdesk beverage view query
- Serving shelf query
- KDS history query
- Serving shelf query now enriches rows with order item id, order type, and category snapshot for frontdesk pickup display
- Serving shelf query excludes `delivery` orders from pickup/handoff reads

#### MenuService / MenuServiceImpl

Implemented responsibilities:
- Read store-scoped active menu catalog
- Return categories with nested active items
- Return nested active item options

#### InventoryService / StationService / UserService

Current code status:
- Interfaces exist
- Business implementations are `UNKNOWN`
- Corresponding controllers currently expose health endpoints only

### 2.6 Entity Models

The following entity classes exist in code and map to database tables:

- `Store` -> `stores`
- `Role` -> `roles`
- `User` -> `users`
- `UserStation` -> `user_stations`
- `MenuCategory` -> `menu_categories`
- `MenuItem` -> `menu_items`
- `MenuItemOption` -> `menu_item_options`
- `MenuItemBom` -> `menu_item_bom`
- `MenuItemOptionBom` -> `menu_item_option_bom`
- `Order` -> `orders`
- `OrderItem` -> `order_items`
- `OrderItemOption` -> `order_item_options`
- `FrontdeskBeverageItem` -> `frontdesk_beverage_items`
- `KitchenTask` -> `kitchen_tasks`
- `ProductionTask` -> `production_tasks`
- `InventoryItem` -> `inventory_items`
- `InventoryTransaction` -> `inventory_transactions`
- `PrepRecipe` -> `prep_recipes`
- `PrepRecipeDetail` -> `prep_recipe_details`
- `Station` -> `stations`
- `DiningTable` -> `dining_tables`

## 3. API Specification

This section describes the current API surface based on controller code and DTOs only.

### 3.1 Order APIs

#### POST /api/v1/orders

Request:

```json
{
  "store_id": 1,
  "created_by": 1,
  "order_type": "dine_in",
  "table_no": "T1-L",
  "pickup_no": null,
  "items": [
    {
      "menu_item_id": 100,
      "quantity": 2,
      "combo_group_no": null,
      "combo_role": "standalone",
      "notes": "less spicy",
      "options": [
        {
          "option_id": 1000,
          "quantity": 1
        }
      ]
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "message": "Order created in draft status",
  "data": {
    "id": 1,
    "order_no": "string",
    "status": "draft",
    "store_id": 1,
    "created_by": 1,
    "order_type": "dine_in",
    "table_no": "T1-L",
    "pickup_no": null,
    "subtotal_amount": 0,
    "discount_amount": 0,
    "total_amount": 0,
    "submitted_at": null,
    "ready_at": null,
    "completed_at": null,
    "created_at": "timestamp",
    "updated_at": "timestamp",
    "is_modified_after_submit": false,
    "modified_after_submit_at": null,
    "modified_after_submit_by": null,
    "items": [],
    "beverage_items": [],
    "kitchen_items": []
  }
}
```

### 3.6 Menu APIs

#### GET /api/v1/menu/health

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": "OK",
  "data": "menu module ready"
}
```

#### GET /api/v1/menu/catalog?store_id=1

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "store_id": 1,
    "categories": [
      {
        "id": 1,
        "code": "SOUP_NOODLE",
        "name_zh": "汤面",
        "name_en": "Soup Noodle",
        "sort_order": 1,
        "items": [
          {
            "id": 1,
            "category_id": 1,
            "station_id": 1,
            "name_zh": "经典牛肉面",
            "name_en": "Traditional Beef Noodle",
            "sku": "traditional_beef_noodle",
            "item_type": "menu_item",
            "base_price": 12.50,
            "is_sold_out": false,
            "options": [
              {
                "id": 1,
                "option_type": "size",
                "name_zh": "标准份",
                "name_en": "Regular",
                "price_delta": 0.00
              }
            ]
          }
        ]
      }
    ]
  }
}
```

Current runtime menu seed verified against the live backend on 2026-04-03:
- `SOUP_NOODLE / 汤面`
  - `traditional_beef_noodle / 传统牛肉面`
  - `braised_beef_tendon_noodle / 红烧牛筋面`
  - `pickled_vegetable_beef_noodle / 酸菜牛肉面`
  - `vegetable_noodle / 蔬菜面`
- `FRIED_NOODLE / 炒面`
  - `beef_chow_mein / 牛肉炒面`
  - `chicken_chow_mein / 鸡肉炒面`
  - `tomato_chow_mein / 番茄炒面`
  - `vegetable_chow_mein / 素菜炒面`
- `DRY_NOODLE / 拌面`
  - `cold_noodle_shredded_chicken / 鸡丝凉面`
  - `zha_jiang_noodle / 炸酱面`
  - `dan_dan_noodle / 担担面`
- `SIDE / 小菜`
  - `cucumber_salad / 拌黄瓜`
  - `edamame / 毛豆`
  - `shredded_potato / 土豆丝`
  - `braised_beef_shank_salad / 拌牛展`
- `FRIED / 炸物`
  - `fried_spring_rolls / 炸春卷`
  - `tempura_shrimp / 炸虾`
  - `fried_steamed_buns / 炸馒头`
  - `fried_wontons / 炸馄饨`
- `DRINK / 饮品`
  - `coke / 可乐`
  - `diet_coke / 健怡可乐`
  - `chinese_herbal_tea / 王老吉`
  - `ice_tea / 冰红茶`
  - `shochu / 烧酒`
  - `sake / 清酒`
  - `tsingtao_beer / 青岛啤酒`

Current option behavior in runtime seed:
- all noodle items expose an optional combo path using add-on options:
  - `套餐`
  - combo egg: `套餐卤蛋` or `套餐煎蛋`
  - combo side: `套餐毛豆`, `套餐土豆丝`, `套餐拌黄瓜`
- when the frontend user enables combo but does not manually change the combo egg or combo side dropdowns, the frontend now still submits the default combo egg and default combo side option IDs with the same order item
- `加香菜` and `加葱` are currently seeded as zero-price add-ons and are intended to behave as one-tap garnish toggles rather than quantity-priced extras
- soup noodles and `担担面` default to noodle type `三细`
- `炸酱面` and `鸡丝凉面` default to noodle type `韭叶`
- spicy level order is:
  - `不辣`
  - `少辣`
  - `正常辣`
  - `加辣`
- `vegetable_noodle` exposes required `soup_base` choices:
  - `素汤`
  - `肉汤`

#### GET /api/v1/orders/{id}

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": null,
  "data": {
    "id": 1,
    "order_no": "string",
    "status": "string",
    "store_id": 1,
    "created_by": 1,
    "order_type": "string",
    "table_no": "string or null",
    "pickup_no": "string or null",
    "subtotal_amount": 0,
    "discount_amount": 0,
    "total_amount": 0,
    "submitted_at": "timestamp or null",
    "ready_at": "timestamp or null",
    "completed_at": "timestamp or null",
    "created_at": "timestamp",
    "updated_at": "timestamp",
    "is_modified_after_submit": false,
    "modified_after_submit_at": "timestamp or null",
    "modified_after_submit_by": 0,
    "items": [
      {
        "id": 1,
        "menu_item_id": 100,
        "category_code_snapshot": "string or null",
        "item_name_snapshot_zh": "string",
        "item_name_snapshot_en": "string or null",
        "quantity": 1,
        "unit_price": 0,
        "line_amount": 0,
        "combo_group_no": 1,
        "combo_role": "standalone",
        "notes": "string or null",
        "is_modified_after_submit": false,
        "modified_after_submit_at": "timestamp or null",
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "NOODLE",
        "task_status": "pending",
        "started_at": "timestamp or null",
        "ready_for_pickup_at": "timestamp or null",
        "served_at": "timestamp or null",
        "beverage_status": "string or null",
        "beverage_special_instructions_snapshot": "string or null",
        "beverage_started_at": "timestamp or null",
        "beverage_ready_at": "timestamp or null",
        "beverage_served_at": "timestamp or null",
        "beverage_cancelled_at": "timestamp or null",
        "options": [
          {
            "id": 1,
            "option_id": 1000,
            "option_type_snapshot": "size",
            "option_name_snapshot_zh": "大份",
            "option_name_snapshot_en": "Large",
            "price_delta": 0,
            "quantity": 1
          }
        ]
      }
    ],
    "beverage_items": [],
    "kitchen_items": []
  }
}
```

#### PUT /api/v1/orders/{id}/draft-header

Request:

```json
{
  "order_type": "pickup",
  "table_no": null,
  "pickup_no": "P-001"
}
```

Response:

```json
{
  "success": true,
  "message": "Draft order header updated",
  "data": {}
}
```

#### POST /api/v1/orders/{id}/items

Request:

```json
{
  "menu_item_id": 100,
  "quantity": 1,
  "combo_group_no": null,
  "combo_role": "standalone",
  "notes": "no cilantro",
  "options": [
    {
      "option_id": 1000,
      "quantity": 1
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "message": "Draft order item added",
  "data": {}
}
```

#### PUT /api/v1/orders/{id}/items/{itemId}/quantity

Request:

```json
{
  "quantity": 3
}
```

Response:

```json
{
  "success": true,
  "message": "Draft order item quantity updated",
  "data": {}
}
```

#### PUT /api/v1/orders/{id}/items/{itemId}

Request:

```json
{
  "quantity": 2,
  "combo_group_no": null,
  "combo_role": "standalone",
  "notes": "extra spicy",
  "options": [
    {
      "option_id": 1000,
      "quantity": 1
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "message": "Draft order item updated",
  "data": {}
}
```

#### DELETE /api/v1/orders/{id}/items/{itemId}

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": "Draft order item removed",
  "data": {}
}
```

#### POST /api/v1/orders/{id}/submit

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": "Order submitted and moved to preparing after kitchen task and inventory processing",
  "data": {}
}
```

#### GET /api/v1/orders/active

Request:

```json
{
  "store_id": 1,
  "status": ["submitted", "preparing", "ready"],
  "order_type": "dine_in",
  "sort_by": "updated_at"
}
```

Response:

```json
{
  "success": true,
  "message": null,
  "data": []
}
```

#### POST /api/v1/orders/{id}/complete

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": "Order completed",
  "data": {}
}
```

#### POST /api/v1/orders/{id}/cancel

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": "Order cancelled",
  "data": {}
}
```

### 3.2 Frontdesk Order Board APIs

#### GET /api/v1/frontdesk/orders

Request:

```json
{
  "store_id": 1,
  "status": ["submitted", "preparing", "ready"],
  "order_type": "dine_in",
  "table_no": "T1",
  "pickup_no": null,
  "keyword": "ORD"
}
```

Response item shape:

```json
{
  "order_id": 1,
  "order_no": "string",
  "order_type": "string",
  "table_no": "string or null",
  "pickup_no": "string or null",
  "order_status": "string",
  "is_modified_after_submit": false,
  "modified_after_submit_at": "timestamp or null",
  "submitted_at": "timestamp or null",
  "updated_at": "timestamp",
  "total_item_count": 0,
  "ready_item_count": 0,
  "beverage_pending_count": 0,
  "kitchen_pending_count": 0
}
```

#### GET /api/v1/frontdesk/orders/history

Request:

```json
{
  "store_id": 1,
  "status": ["completed", "cancelled"],
  "order_type": "dine_in",
  "table_no": null,
  "pickup_no": null,
  "keyword": "ORD",
  "limit": 20
}
```

Response:

```json
{
  "success": true,
  "message": null,
  "data": []
}
```

### 3.3 Frontdesk Beverage APIs

#### GET /api/v1/frontdesk/beverages

Request:

```json
{
  "store_id": 1,
  "status": ["pending", "preparing", "ready"]
}
```

Response item shape:

```json
{
  "beverage_item_id": 1,
  "order_id": 1,
  "order_no": "string",
  "table_no": "string or null",
  "pickup_no": "string or null",
  "order_type": "string",
  "order_item_id": 1,
  "item_name_snapshot_zh": "string",
  "item_name_snapshot_en": "string or null",
  "quantity": 1,
  "special_instructions_snapshot": "string or null",
  "beverage_status": "pending",
  "created_at": "timestamp",
  "submitted_at": "timestamp or null",
  "updated_at": "timestamp",
  "started_at": "timestamp or null",
  "ready_at": "timestamp or null",
  "served_at": "timestamp or null",
  "cancelled_at": "timestamp or null"
}
```

#### POST /api/v1/frontdesk/beverages/{orderItemId}/start
#### POST /api/v1/frontdesk/beverages/{orderItemId}/ready
#### POST /api/v1/frontdesk/beverages/{orderItemId}/served
#### POST /api/v1/frontdesk/beverages/{orderItemId}/cancel

All four:

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": "string",
  "data": {}
}
```

### 3.4 Kitchen APIs

#### GET /api/v1/kitchen-tasks

Request:

```json
{
  "store_id": 1,
  "station_code": "WOK"
}
```

Response item shape:

```json
{
  "id": 1,
  "order_id": 1,
  "order_item_id": 1,
  "store_id": 1,
  "station_code": "WOK",
  "item_name_snapshot_zh": "string",
  "item_name_snapshot_en": "string or null",
  "quantity": 1,
  "special_instructions_snapshot": "string or null",
  "status": "pending",
  "priority": 0,
  "created_at": "timestamp",
  "started_at": "timestamp or null",
  "completed_at": "timestamp or null",
  "served_at": "timestamp or null",
  "cancelled_at": "timestamp or null"
}
```

#### POST /api/v1/kitchen-tasks/{id}/start
#### POST /api/v1/kitchen-tasks/{id}/ready-for-pickup
#### POST /api/v1/kitchen-tasks/{id}/served
#### POST /api/v1/kitchen-tasks/{id}/complete

All four:

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "message": "string",
  "data": {}
}
```

### 3.5 KDS Read APIs

#### GET /api/v1/kds/noodle-display

Current backend behavior:
- Despite the endpoint name, this endpoint currently powers the assembling/prep KDS screen.
- It returns visible task rows from the combined assembling station group:
  - `NOODLE`
  - `WOK`
  - `COLD`
  - `DEEPFRIED`
- In the frontend these are rendered as logical sections:
  - `NOODLE`
  - `WOK`
  - `SIDE`
  - `FRIED`
- The backend now writes concise Chinese kitchen shorthand into the task snapshot fields used by this endpoint:
  - `item_name_snapshot_zh`
  - `special_instructions_snapshot`
- Runtime-verified examples:
  - soup noodle: `中（s） | 走香 +蛋 +土豆`
  - wok noodle: `（s） | +煎`
  - braised beef tendon noodle: `中红`
  - pickled vegetable beef noodle: `中酸`
  - large vegetable noodle default broth shorthand: `大素`
  - large vegetable noodle with meat broth: `大素（肉汤）`
  - side dish with remove-only modifiers: `牛展 | 走花生`
  - tea egg add-on / combo egg now renders as `+蛋` rather than `+卤`
- Internal normalization for remove/add-on semantics is handled by backend mapping helpers rather than a dedicated database code column. Examples of normalized semantics in code include:
  - `cilantro`
  - `green_onion`
  - `peanut`
  - `bok_choy`
  - `extra_noodle`
  - `tea_egg`

Response item shape:

```json
{
  "task_id": 1,
  "order_id": 1,
  "order_no": "string",
  "table_no": "string or null",
  "pickup_no": "string or null",
  "station_code": "NOODLE | WOK | COLD | DEEPFRIED",
  "item_name_snapshot_zh": "string",
  "item_name_snapshot_en": "string or null",
  "quantity": 1,
  "is_order_modified_after_submit": false,
  "is_item_modified_after_submit": false,
  "status": "pending",
  "special_instructions_snapshot": "string or null",
  "size_label": "string or null",
  "noodle_type_label": "string or null",
  "extra_flags": ["string"],
  "created_at": "timestamp",
  "started_at": "timestamp or null",
  "completed_at": "timestamp or null",
  "served_at": "timestamp or null"
}
```

#### GET /api/v1/kds/hot-kitchen

Response:

```json
{
  "success": true,
  "message": null,
  "data": []
}
```

#### GET /api/v1/kds/pass
#### GET /api/v1/kds/history

Current backend behavior:
- `station_code` is optional.
- When `station_code=ASSEMBLING`, history is built from the combined assembling station group:
  - `NOODLE`
  - `WOK`
  - `COLD`
  - `DEEPFRIED`
- Orders appear there only after they have at least one completed assembling task and no active assembling tasks remain.
- Results are sorted by the latest relevant assembling completion timestamp descending.

Response item shape:

```json
{
  "order_id": 1,
  "order_no": "string",
  "table_no": "string or null",
  "pickup_no": "string or null",
  "order_status": "string",
  "is_modified_after_submit": false,
  "modified_after_submit_at": "timestamp or null",
  "created_at": "timestamp",
  "ready_at": "timestamp or null",
  "completed_at": "timestamp or null",
  "items": [
    {
      "order_item_id": 1,
      "category_code_snapshot": "string or null",
      "item_name_snapshot_zh": "string",
      "item_name_snapshot_en": "string or null",
      "quantity": 1,
      "is_modified_after_submit": false,
      "modified_after_submit_at": "timestamp or null",
      "station_code": "WOK",
      "requires_kitchen_task": true,
      "task_status": "pending",
      "special_instructions_snapshot": "string or null",
      "started_at": "timestamp or null",
      "completed_at": "timestamp or null",
      "served_at": "timestamp or null"
    }
  ]
}
```

#### GET /api/v1/kds/frontdesk-beverages

Response item shape:

```json
{
  "order_id": 1,
  "order_no": "string",
  "table_no": "string or null",
  "pickup_no": "string or null",
  "order_status": "string",
  "created_at": "timestamp",
  "items": [
    {
      "order_item_id": 1,
      "item_name_snapshot_zh": "string",
      "item_name_snapshot_en": "string or null",
      "quantity": 1,
      "special_instructions_snapshot": "string or null",
      "beverage_status": "pending"
    }
  ]
}
```

#### GET /api/v1/kds/serving-shelf

Response item shape:

```json
{
  "task_id": 1,
  "order_id": 1,
  "order_no": "string",
  "table_no": "string or null",
  "pickup_no": "string or null",
  "item_name_snapshot_zh": "string",
  "item_name_snapshot_en": "string or null",
  "quantity": 1,
  "created_at": "timestamp",
  "ready_for_pickup_at": "timestamp or null"
}
```

## 4. Database Schema

The schema below is based on entity classes. Exact SQL column types other than explicit `BIGSERIAL` are `UNKNOWN` unless directly inferable from Java field types.

### 4.1 Tables and Fields

#### stores
- `id` BIGSERIAL
- `name` String
- `code` String
- `status` String
- `enable_bar_kitchen_tasks` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### roles
- `id` BIGSERIAL
- `name` String
- `code` String
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### users
- `id` BIGSERIAL
- `store_id` Long
- `role_id` Long
- `username` String
- `full_name` String
- `phone` String
- `status` String
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### user_stations
- `id` BIGSERIAL
- `user_id` Long
- `station_id` Long
- `is_primary` Boolean
- `is_active` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### menu_categories
- `id` BIGSERIAL
- `store_id` Long
- `code` String
- `name_zh` String
- `name_en` String
- `sort_order` Integer
- `is_active` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### menu_items
- `id` BIGSERIAL
- `store_id` Long
- `category_id` Long
- `station_id` Long
- `name_zh` String
- `name_en` String
- `sku` String
- `item_type` String
- `base_price` BigDecimal
- `is_active` Boolean
- `is_sold_out` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### menu_item_options
- `id` BIGSERIAL
- `menu_item_id` Long
- `option_type` String
- `name_zh` String
- `name_en` String
- `price_delta` BigDecimal
- `is_active` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### menu_item_bom
- `id` BIGSERIAL
- `menu_item_id` Long
- `inventory_item_id` Long
- `qty_per_unit` BigDecimal
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### menu_item_option_bom
- `id` BIGSERIAL
- `menu_item_option_id` Long
- `inventory_item_id` Long
- `qty_per_unit` BigDecimal
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### orders
- `id` BIGSERIAL
- `store_id` Long
- `created_by` Long
- `order_no` String
- `order_type` String
- `status` String
- `table_no` String
- `pickup_no` String
- `subtotal_amount` BigDecimal
- `discount_amount` BigDecimal
- `total_amount` BigDecimal
- `submitted_at` LocalDateTime
- `ready_at` LocalDateTime
- `completed_at` LocalDateTime
- `is_modified_after_submit` Boolean
- `modified_after_submit_at` LocalDateTime
- `modified_after_submit_by` Long
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### order_items
- `id` BIGSERIAL
- `order_id` Long
- `menu_item_id` Long
- `category_code_snapshot` String
- `item_name_snapshot_zh` String
- `item_name_snapshot_en` String
- `quantity` Integer
- `unit_price` BigDecimal
- `line_amount` BigDecimal
- `combo_group_no` Integer
- `combo_role` String
- `status` String
- `notes` String
- `is_modified_after_submit` Boolean
- `modified_after_submit_at` LocalDateTime
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### order_item_options
- `id` BIGSERIAL
- `order_item_id` Long
- `option_id` Long
- `option_type_snapshot` String
- `option_name_snapshot_zh` String
- `option_name_snapshot_en` String
- `price_delta` BigDecimal
- `quantity` Integer
- `created_at` LocalDateTime

#### frontdesk_beverage_items
- `id` BIGSERIAL
- `order_id` Long
- `order_item_id` Long
- `store_id` Long
- `item_name_snapshot_zh` String
- `item_name_snapshot_en` String
- `special_instructions_snapshot` String
- `status` String
- `quantity` Integer
- `created_at` LocalDateTime
- `started_at` LocalDateTime
- `ready_at` LocalDateTime
- `served_at` LocalDateTime
- `cancelled_at` LocalDateTime

#### production_tasks
- `id` BIGSERIAL
- `order_id` Long
- `order_item_id` Long
- `store_id` Long
- `source_type` String
- `source_id` Long
- `station_code` String
- `item_name_snapshot_zh` String
- `item_name_snapshot_en` String
- `special_instructions_snapshot` String
- `status` String
- `quantity` Integer
- `priority` Integer
- `started_at` LocalDateTime
- `completed_at` LocalDateTime
- `ready_at` LocalDateTime
- `served_at` LocalDateTime
- `cancelled_at` LocalDateTime
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### dining_tables
- `id` BIGSERIAL
- `store_id` Long
- `table_code` String
- `table_name` String
- `area_name` String
- `capacity` Integer
- `supports_split` Boolean
- `is_active` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### kitchen_tasks
- `id` BIGSERIAL
- `order_id` Long
- `order_item_id` Long
- `store_id` Long
- `station_code` String
- `item_name_snapshot_zh` String
- `item_name_snapshot_en` String
- `special_instructions_snapshot` String
- `status` String
- `quantity` Integer
- `priority` Integer
- `started_at` LocalDateTime
- `completed_at` LocalDateTime
- `served_at` LocalDateTime
- `cancelled_at` LocalDateTime
- `created_at` LocalDateTime

#### inventory_items
- `id` BIGSERIAL
- `store_id` Long
- `name` String
- `code` String
- `item_level` String
- `item_type` String
- `unit` String
- `current_stock` BigDecimal
- `safety_stock` BigDecimal
- `default_prep_batch` BigDecimal
- `is_key_item` Boolean
- `is_active` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### inventory_transactions
- `id` BIGSERIAL
- `inventory_item_id` Long
- `operated_by` Long
- `txn_type` String
- `source_type` String
- `source_id` Long
- `qty_change` BigDecimal
- `stock_before` BigDecimal
- `stock_after` BigDecimal
- `remarks` String
- `created_at` LocalDateTime

#### prep_recipes
- `id` BIGSERIAL
- `output_inventory_item_id` Long
- `output_qty` BigDecimal
- `output_unit` String
- `is_active` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### prep_recipe_details
- `id` BIGSERIAL
- `prep_recipe_id` Long
- `input_inventory_item_id` Long
- `input_qty` BigDecimal
- `input_unit` String
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

#### stations
- `id` BIGSERIAL
- `store_id` Long
- `name` String
- `code` String
- `sort_order` Integer
- `is_active` Boolean
- `created_at` LocalDateTime
- `updated_at` LocalDateTime

### 4.2 Relationships

#### Relationships inferable from field names and repository usage

- `users.store_id -> stores.id`
- `users.role_id -> roles.id`
- `user_stations.user_id -> users.id`
- `user_stations.station_id -> stations.id`
- `menu_categories.store_id -> stores.id`
- `menu_items.store_id -> stores.id`
- `menu_items.category_id -> menu_categories.id`
- `menu_items.station_id -> stations.id`
- `menu_item_options.menu_item_id -> menu_items.id`
- `menu_item_bom.menu_item_id -> menu_items.id`
- `menu_item_bom.inventory_item_id -> inventory_items.id`
- `menu_item_option_bom.menu_item_option_id -> menu_item_options.id`
- `menu_item_option_bom.inventory_item_id -> inventory_items.id`
- `orders.store_id -> stores.id`
- `orders.created_by -> users.id`
- `order_items.order_id -> orders.id`
- `order_items.menu_item_id -> menu_items.id`
- `order_item_options.order_item_id -> order_items.id`
- `order_item_options.option_id -> menu_item_options.id`
- `frontdesk_beverage_items.order_id -> orders.id`
- `frontdesk_beverage_items.order_item_id -> order_items.id`
- `production_tasks.order_id -> orders.id` inferred by field naming only
- `production_tasks.order_item_id -> order_items.id` inferred by field naming only
- `dining_tables.store_id -> stores.id` inferred by field naming only
- `kitchen_tasks.order_id -> orders.id`
- `kitchen_tasks.order_item_id -> order_items.id`
- `kitchen_tasks.store_id -> stores.id`
- `inventory_items.store_id -> stores.id`
- `inventory_transactions.inventory_item_id -> inventory_items.id`
- `inventory_transactions.operated_by -> users.id`
- `prep_recipes.output_inventory_item_id -> inventory_items.id`
- `prep_recipe_details.prep_recipe_id -> prep_recipes.id`
- `prep_recipe_details.input_inventory_item_id -> inventory_items.id`

#### Explicit database foreign key constraints
- `order_items.order_id -> orders.id`
- `order_item_options.order_item_id -> order_items.id`

## 5. Frontend Structure (React + TypeScript)

### 5.1 Frontend Project Structure

```text
frontend/src/
├── App.tsx
├── components/ui/
│   ├── Badge.tsx
│   ├── Button.tsx
│   ├── Card.tsx
│   └── Input.tsx
├── data/
│   └── mockDineIn.ts
│   └── mockOrdering.ts
│   └── menuImportSeed.ts
├── hooks/
│   └── useTableBoard.ts
│   └── useOrderSessions.ts
├── features/dinein/
│   ├── DineInPage.tsx
│   └── components/
│       ├── DineInSidebar.tsx
│       ├── DineInTopBar.tsx
│       ├── TableCard.tsx
│       ├── TableGrid.tsx
│       ├── TableStatusBadge.tsx
│       └── TableStatusLegend.tsx
├── features/ordering/
│   ├── OrderingPage.tsx
│   └── components/
│       ├── CategoryNav.tsx
│       ├── ItemCustomizationModal.tsx
│       ├── MenuItemCard.tsx
│       ├── OrderingTopBar.tsx
│       ├── OrderLineItemRow.tsx
│       └── OrderSummaryPanel.tsx
├── features/orders/
│   ├── OrdersPage.tsx
│   └── components/
│       ├── OrderDetailPanel.tsx
│       └── OrderMiniCard.tsx
├── features/pickup/
│   ├── PickupBoardPage.tsx
│   └── components/
│       └── PickupOrderCard.tsx
├── features/kds/noodle/
│   ├── NoodleStationPage.tsx
│   └── components/
│       ├── KdsOrderCard.tsx
│       ├── KdsSidebar.tsx
│       ├── KdsTopBar.tsx
│       └── KitchenItemRow.tsx
├── features/kds/history/
│   └── KdsHistoryPage.tsx
├── pages/
│   ├── DineIn.tsx
│   ├── KdsHistory.tsx
│   ├── KdsNoodle.tsx
│   └── PickupBoard.tsx
├── services/
│   ├── dineInService.ts
│   ├── kdsService.ts
│   ├── menuService.ts
│   ├── orderService.ts
│   └── pickupService.ts
├── types/
│   ├── dinein.ts
│   ├── kds.ts
│   └── ordering.ts
├── index.css
└── main.tsx
```

### 5.2 Pages / Screens

Current pages/screens present in code:
- `pages/DineIn.tsx`
- `pages/Orders.tsx`
- `pages/KdsHistory.tsx`
- `pages/KdsHotKitchen.tsx`
- `pages/KdsNoodle.tsx`
- `pages/PickupBoard.tsx`
- `features/dinein/DineInPage.tsx`
- `features/ordering/OrderingPage.tsx`
- `features/orders/OrdersPage.tsx`
- `features/pickup/PickupBoardPage.tsx`
- `features/kds/history/KdsHistoryPage.tsx`
- `features/kds/noodle/NoodleStationPage.tsx`

Current actual behavior:
- The root homepage is mounted at:
  - `/`
- The frontdesk table board is mounted at:
  - `/frontdesk`
- Additional dedicated frontdesk workstation routes are also mounted at:
  - `/frontdesk/menu/a`
  - `/frontdesk/menu/b`
- The shared ordering workflow is mounted at:
  - `/frontdesk/menu?slot={slotLabel}&table={tableLabel}&type={dine_in|pickup}[&pickup={pickupLabel}]`
  - `/frontdesk/menu/{workstation}?slot={slotLabel}&table={tableLabel}&type={dine_in|pickup}[&pickup={pickupLabel}]`
- The frontdesk order lookup / checkout page is mounted at:
  - `/frontdesk/order`
- The frontdesk pickup / handoff board is mounted at:
  - `/pickup`
- The frontend opens the ordering page after selecting or editing a table slot.
- The ordering page menu read layer uses the backend menu catalog API.
- The ordering page draft layer uses backend draft-order APIs.
- The orders page uses frontdesk order board + history reads for compact order cards, and order detail reads for itemized pricing and checkout.
- The frontend also exposes a separate assembling KDS screen at:
  - `/kds/grab`
- The frontend also exposes a separate hot-kitchen KDS screen at:
  - `/kds/hot-kitchen`
- The frontend also exposes a separate read-only ramen-station screen at:
  - `/kds/noodle`
- The frontend also exposes a separate KDS history screen at:
  - `/kds/history`
- The pickup board reads ready shelf items through:
  - `GET /api/v1/kds/serving-shelf?store_id=1`
- The pickup board subscribes to the realtime topic:
  - `/topic/stores/{store_id}/kds/serving-shelf`
- The pickup board preloads current ready shelf rows on first mount, then continues syncing through serving-shelf realtime events.
- The pickup board also polls the serving shelf every 4 seconds as a runtime fallback, so ready items still appear even if the WebSocket event is delayed or unavailable.
- The pickup board excludes delivery orders and only shows dine-in or pickup/takeout ready items.
- The assembling KDS screen reads active task rows through:
  - `GET /api/v1/kds/noodle-display?store_id=1`
- The assembling KDS screen also reads full order detail through:
  - `GET /api/v1/orders/{id}`
- The assembling KDS screen renders active combined assembling tasks from `kds/noodle-display`, and uses order detail for order-level context.
- The KDS history screen reads:
  - `GET /api/v1/kds/history?store_id=1&station_code=ASSEMBLING`
- Draft order data is no longer local-state only.
- Table occupancy on the board is derived from backend active-order reads plus table configuration, not from standalone local occupied-state mock data.
- `frontend/src/data/menuImportSeed.ts` contains a normalized backend-compatible menu import seed with `categories`, `items`, and `options`, but it is not wired into the UI or backend import process yet.
- In iPad landscape mode:
  - frontdesk pages use a shared compact top-navbar shell
  - ordering workflow keeps its 3-column structure but uses tighter panel widths and spacing
  - item customization modal uses a compact max-height/max-width layout around the 900 to 1000px range, with denser option sections and a tighter bottom action area
  - KDS active and history pages use tighter headers, denser order grids, and more compact item/action rows

### 5.3 Components

#### Base UI Components
- `Button`
- `Card`
- `Badge`
- `Input`

#### Dine-in Feature Components
- `DineInSidebar`
- `DineInTopBar`
- `TableGrid`
- `TableCard`
- `TableStatusBadge`
- `TableStatusLegend`

#### Ordering Feature Components
- `OrderingPage`
- `OrderingTopBar`
- `CategoryNav`
- `MenuItemCard`
- `OrderSummaryPanel`
- `OrderLineItemRow`
  - includes per-item `备注 / Special note` input in the cart
  - notes are bound to the concrete `order_items` row, not the whole order
- `ItemCustomizationModal`

#### Orders Feature Components
- `OrdersPage`
- `OrderMiniCard`
- `OrderDetailPanel`

#### Pickup Feature Components
- `PickupBoardPage`
- `PickupOrderCard`

#### KDS Feature Components
- `NoodleStationPage`
- `KdsHistoryPage`
- `KdsSidebar`
- `KdsTopBar`
- `KdsOrderCard`
- `KitchenItemRow`

### 5.4 State Management

Current state management in code:
- React local state with `useState`
- One custom local-state hook: `useTableBoard`
- One custom draft-order integration hook: `useDraftOrder`
- One KDS read hook: `useNoodleStationOrders`
- One KDS history hook: `useKdsHistory`
- One pickup board realtime hook: `usePickupBoard`
- No Redux, Zustand, React Query, or Context-based global state found
- The `/orders` page keeps selected-order state and a frontend-only split-bill assignment map in local component state.

State used in `DineInPage.tsx`:
- `serviceMode`
- `tableQuery`

State used in `useTableBoard.ts`:
- `tables`
- `nextOrderSequence`

State used in `useDraftOrder.ts`:
- `order`
- `loading`
- `saving`
- `error`

State used in `usePickupBoard.ts`:
- `ordersMap`
- `busyTaskIds`
- `error`

### 5.5 API Calls Mapping

Current frontend API integration:
- `GET /api/v1/menu/catalog?store_id=1`
- `GET /api/v1/orders/open-editable?store_id=1&table_no={slotLabel}`
- `GET /api/v1/orders/open-editable?store_id=1&pickup_no={pickupLabel}`
- `GET /api/v1/frontdesk/orders?store_id=1&status=submitted&status=preparing&status=ready`
- `GET /api/v1/frontdesk/orders/history?store_id=1&status=completed&status=cancelled&limit=20`
- `GET /api/v1/orders/{id}`
- `GET /api/v1/frontdesk/orders?store_id=1&status=submitted&status=preparing`
- `GET /api/v1/kds/noodle-display?store_id=1`
- `GET /api/v1/kds/history?store_id=1&station_code=ASSEMBLING`
- `GET /api/v1/kds/serving-shelf?store_id=1`
- `POST /api/v1/orders`
- `POST /api/v1/kitchen-tasks/{id}/ready-for-pickup`
- `POST /api/v1/kitchen-tasks/{id}/served`
- `POST /api/v1/orders/{id}/items`
- `PUT /api/v1/orders/{id}/items/{itemId}`
- `PUT /api/v1/orders/{id}/items/{itemId}/quantity`
- `DELETE /api/v1/orders/{id}/items/{itemId}`
- `POST /api/v1/orders/{id}/submit`
- `POST /api/v1/orders/{id}/complete`
- `POST /api/v1/orders/{id}/cancel`

Actual frontend data source:
- Table board: local mock data in `data/mockDineIn.ts`
- Ordering menu: backend menu catalog API through `services/menuService.ts`
- Ordering draft state: backend draft-order APIs through `services/orderService.ts`
- Orders page card list: backend frontdesk order board + history APIs through `services/orderService.ts`
- Orders page detail / checkout state: backend order detail + complete APIs through `services/orderService.ts`
- Pickup board: backend serving-shelf API + serving-shelf realtime subscription through `services/pickupService.ts`
- Noodle KDS screen: backend noodle KDS + order detail APIs through `services/kdsService.ts`
- KDS history screen: backend KDS history API through `services/kdsService.ts`

### 5.6 Frontend Domain Types

From `types/dinein.ts`:

- `ServiceMode = "dine_in" | "takeout"`
- `TableStatus = "available" | "occupied" | "alert"`
- `TableSeatCode = "A" | "B"`
- `TableConfigMode = "split_supported" | "single_only"`
- `TableOccupancyMode = "empty" | "full" | "split"`
- `SlotOrder`
- `DiningTable`
- `TableSlot`
- `DineInMockData`
- `LocalizedText`
- `ChoiceOption`
- `MenuCategory`
- `MenuItemCustomizationConfig`
- `MenuItem`
- `ItemSelectionState`
- `OrderLineItem`
- `OrderSession`
- `ItemCustomizationDraft`

## 5.7 Frontend Behavior

Current frontend behavior in code:

1. The main frontdesk entry page top bar no longer uses a dine-in/takeout tab switcher.
2. The main frontdesk entry page no longer renders a search field.
3. The page renders flat order-slot cards rather than nested table cards.
4. Table behavior depends on per-table configuration.
5. `split_supported` empty tables render a single `T1` card with three entry buttons: `Left`, `Right`, and `Full`.
6. `single_only` empty tables render a single `T1` card with one `Start order` button.
7. Choosing `Left` on a `split_supported` table creates local split mode and renders `T1-A` as occupied plus `T1-B` as available.
8. Choosing `Right` on a `split_supported` table creates local split mode and renders `T1-B` as occupied plus `T1-A` as available.
9. Choosing `Full` on a `split_supported` table creates local full-table mode and renders only `T1` as occupied.
10. Choosing `Start order` on a `single_only` table creates local full-table mode and renders only `T1` as occupied.
11. Empty split slots render `Start order`.
12. Occupied slots render `Edit order`.
13. Selecting or editing a slot opens the ordering page instead of staying on the table board.
13a. The ordering workflow route is `/frontdesk/menu`, not `/`, and route state is stored in URL query parameters for slot, table, order type, and optional pickup label.
14. The ordering page layout is:
    - left: category navigation only
    - center: menu item cards
    - right: current order summary
    - on iPad landscape, the page keeps the same 3-column workflow but switches to a compact workstation shell with a shared top navbar and tighter panel widths
15. Clicking `Takeout / 外带` on the main frontdesk page immediately opens the shared ordering page in `pickup` mode.
16. On takeout entry, the frontend generates a temporary pickup label in the format `TO-xxxxXX` and uses that as the initial editable pickup slot.
17. The ordering page top area shows:
    - back button
    - table / slot label
    - order type context (`Dine-in / 堂食` or `Takeout / 外带`)
    - takeout pickup label when the order type is `pickup`
    - menu search field
    - in iPad landscape, this top area is visually compressed to reduce wasted vertical space
18. In `pickup` mode, the ordering header also exposes an `Edit info` action that opens a lightweight dialog for an optional customer name or phone number.
19. Saving that dialog updates the same backend order through:
    - `PUT /api/v1/orders/{id}/draft-header`
    - and writes the value into the existing `pickup_no` field
20. The ordering page does not show history, logout, or quick checkout.
21. Clicking a menu item opens a separate customization modal.
22. The ordering page reads categories, items, and option groups from the backend menu catalog API and maps them into the frontend UI shape.
23. The live menu catalog currently supports these option groups on the ordering page when present on an item:
    - combo toggle
    - combo egg selection
    - combo side selection
    - size
    - soup base
    - noodle type
    - spicy level
    - add-ons with quantity controls
    - remove options
24. Fried items currently bypass the customization modal when they do not expose options and can be added directly from the item card plus button.
25. When the ordering page opens, the frontend reuses or creates an editable order through:
    - `GET /api/v1/orders/open-editable?store_id=1&table_no={slotLabel}`
    - or `GET /api/v1/orders/open-editable?store_id=1&pickup_no={pickupLabel}`
26. Only if no editable order exists does the frontend create a new draft with:
    - `POST /api/v1/orders`
27. The backend `POST /api/v1/orders` endpoint also has duplicate protection and will return the current editable order for the same table slot or pickup slot if one already exists.
28. The returned backend `order.id` is stored in local component state and is used for subsequent order item mutations.
29. Adding an item in the customization modal calls the backend order item API and sends:
    - `menu_item_id`
    - selected backend `option_id` values
    - selected add-on quantities
30. Editing an item calls the backend order item update API for the same order, including submitted/preparing orders.
31. Increment/decrement quantity calls the backend order item quantity API for the same order.
32. Removing an item calls the backend order item delete API for the same order.
33. Cancelling the order from the ordering page calls the backend cancel API for the current order.
34. The right-side order summary is derived from the real backend `OrderResponse`, not from mock menu order state.
35. The `Save Draft` button performs a backend detail refresh through `GET /api/v1/orders/{id}`.
36. `GET /api/v1/orders/{id}` reads in the frontend use a small retry window because the backend may have a short visibility delay immediately after a successful write response.
37. The primary bottom action button depends on order status:
    - `draft` -> active `Submit Order / 提交订单`
    - `submitted` or `preparing` with `is_modified_after_submit = false` -> disabled `Order In Progress / 订单进行中`
    - `submitted` or `preparing` with `is_modified_after_submit = true` -> active `Update Order / 更新订单`
38. Clicking `Submit Order` calls:
    - `POST /api/v1/orders/{id}/submit`
39. Clicking `Update Order` does not create a new order. For submitted/preparing orders, the frontend stages item changes locally and only writes them back to the same order when the user explicitly clicks `Update Order / 更新订单`.
40. After a successful submit response, the frontend leaves the ordering page and returns to the table board.
41. The table board shows a lightweight submitted message for the slot that was just submitted or updated.
42. On occupied slots backed by a real backend order id, the table board also shows a `Finish` button under `Edit order`.
43. Clicking `Finish` opens a confirmation dialog and, if confirmed, calls:
    - `POST /api/v1/orders/{id}/complete`
44. After a successful finish response, the frontend refreshes the backend-driven table occupancy state so the completed order no longer appears occupied on the table board.
45. The table board no longer shows backend order codes inside table cards; occupied cards only show table or slot label, zone, and available actions.
46. The frontdesk also has a dedicated `/frontdesk/order` page for operational order lookup and checkout.
47. The `/frontdesk/order` page renders compact order cards that show only the table number or takeout identifier plus a small status/type marker.
48. Clicking an order card loads the real backend order detail through:
    - `GET /api/v1/orders/{id}`
49. The `/frontdesk/order` page shows itemized line pricing, subtotal, tax, and total from the backend order response.
50. The `/frontdesk/order` page includes a frontend-only split-bill calculator.
51. Split billing exists only inside the checkout flow on `/frontdesk/order`; the ordering/menu workflow does not contain any bill assignment logic.
52. In `1 Bill` mode, the order behaves like normal checkout and no item-level split controls are shown.
53. In `2 Bills`, `3 Bills`, or `4 Bills` mode, every order item starts as `Unassigned`.
54. In split mode, each order item can be in one of three frontend-only states:
    - `UNASSIGNED`
    - `SINGLE`
    - `SHARED`
55. `SINGLE` assigns the full line amount to one bill bucket (`A`, `B`, `C`, or `D`).
56. `SHARED` opens a lightweight inline editor that supports:
    - participant selection (`A`/`B`/`C`/`D`)
    - `Equal Split`
    - `Manual Amount`
57. `Equal Split` uses deterministic 2-decimal rounding; any rounding remainder is assigned to the earliest selected bill so the participant totals always match the original line amount exactly.
58. `Manual Amount` keeps the apply action disabled until the entered amounts match the original line amount exactly.
59. In split mode, checkout is disabled while any item remains `Unassigned`.
60. Bill summary cards on `/frontdesk/order` currently show split subtotal, split tax, and split total using a frontend checkout tax rate of `14.975%`.
61. The overall order summary block on `/frontdesk/order` also uses the same frontend checkout tax rate of `14.975%` for display.
62. The `/frontdesk/order` page also supports a frontend-only `Cash` payment label toggle for checkout marking.
63. The `Cash` toggle does not persist to the backend; it is a frontend checkout label only.
64. Split billing does not create separate backend orders or payments; it is a checkout planning tool only.
65. The `/orders` page checkout action calls:
    - `POST /api/v1/orders/{id}/complete`
66. After a successful checkout response, the orders page refreshes active orders, recent history, and the selected order detail.
67. The frontend has a separate pickup / handoff board mounted at `/pickup`.
68. The pickup board is a frontdesk-serving screen for ready-item handoff and not a kitchen-production page.
69. The pickup board preloads already-ready shelf items on initial mount from:
    - `GET /api/v1/kds/serving-shelf?store_id=1`
70. The pickup board listens to `/topic/stores/{store_id}/kds/serving-shelf` and reacts only to:
    - `kitchen_task.ready_for_pickup`
    - `kitchen_task.served`
71. The pickup board refreshes ready shelf rows from:
    - `GET /api/v1/kds/serving-shelf?store_id=1`
72. Only dine-in and pickup/takeout ready items are shown there; delivery orders are excluded.
73. The pickup board renders one card per `order_id`, with each ready item row having its own `COMPLETE` action.
74. Ready pickup rows now also render noodle bowl size information when available, using backend shelf fields such as `size_label` and `special_instructions_snapshot` to distinguish `中` vs `大`.
75. For soup noodles and dry/mixed noodles on the pickup board, the row also renders the backend shorthand line from `special_instructions_snapshot`, so frontdesk can see noodle-type and modifier context such as `中韭`, `大毛`, `+蛋`, `走葱`.
76. Item completion from the pickup board calls:
    - `POST /api/v1/kitchen-tasks/{taskId}/served`
77. The pickup board removes the completed item row immediately and removes the whole card when no ready rows remain.
78. Cards with multiple ready rows also expose `ALL COMPLETE`, which completes each visible row sequentially.
79. Pickup cards are sorted by `ready_for_pickup_at` ascending, so the oldest ready card stays first.
78. The frontend has a separate KDS assembling screen mounted at `/kds/noodle`.
79. The assembling KDS screen includes a display-size control in the top-right corner (`Aa` popover) with 4 presets:
    - `Compact`
    - `Standard`
    - `Large`
    - `Extra Large`
    Default is `Standard`.
80. The selected assembling-screen display size is persisted in browser localStorage and restored automatically when the page is reopened.
81. Display-size changes affect only presentation density:
    - card width / height
    - item font sizes
    - quantity sizes
    - spacing / padding
    They do not affect task ordering, realtime updates, completion state, or ready-state logic.
82. The frontend has a separate Hot Kitchen KDS screen mounted at `/kds/hot-kitchen`.
83. The hot-kitchen KDS screen reads the same live task feed from:
    - `GET /api/v1/kds/noodle-display?store_id=1`
    but filters it down to hot-kitchen workload only:
    - `station_code = WOK`
    - `station_code = DEEPFRIED`
    and keeps only hot-kitchen tasks that are still active for this station (it does not keep `ready_for_pickup` rows on the hot-kitchen screen after the chef finishes them).
84. Each hot-kitchen card represents one order and renders only that order’s hot-kitchen items.
84a. The hot-kitchen screen uses a dense multi-column production-board layout with fixed minimum card width per display mode rather than a full-width one-card-per-row list.
85. Each hot-kitchen item row has its own `Complete` action, which calls:
    - `POST /api/v1/kitchen-tasks/{taskId}/ready-for-pickup`
    and sends that single item to the Ready Board immediately.
86. Hot-kitchen cards also expose `Complete All`, which applies only to the remaining hot-kitchen items inside that one order card.
87. The hot-kitchen screen includes its own `Aa` display-size popover with:
    - `Compact`
    - `Standard`
    - `Large`
    - `Extra Large`
    persisted in browser localStorage.
87a. In hot-kitchen `Large` and `Extra Large` modes, item names, quantities, modifier text, buttons, row spacing, and card spacing are intentionally much larger to support poor-eyesight kitchen staff.
87b. Hot-kitchen modifiers are rendered as compact inline Chinese text instead of separate chip labels, so rows scan faster and use less space.
88. The frontend has a separate KDS history screen mounted at `/kds/history`.
89. The frontend has a separate passive ramen-station monitor mounted at `/kds/ramen`.
90. The ramen-station monitor is display-only:
    - no complete button
    - no ready button
    - no pickup interaction
91. The ramen-station monitor reads the same live assembling task feed from:
    - `GET /api/v1/kds/noodle-display?store_id=1`
    and filters it down to noodle-chef workload:
    - `station_code = NOODLE`
    - `station_code = WOK` for stir-fried noodles that still require noodle-pull workflow.
92. Because it derives from the active assembling feed, it keeps showing noodle rows while the order still belongs to the active downstream workflow and removes the whole card automatically once assembling `Complete All` clears the order from active combined KDS reads.
84. The ramen-station monitor also subscribes to `/topic/stores/{store_id}/kds/noodle-display` and uses 4-second polling as a fallback, so cards add, update, and disappear without manual refresh.
85. The ramen-station monitor excludes:
    - side-only orders
    - fried-only orders
    - drink-only orders
    - delivery-only workflow rows when they do not have noodle-station items
86. Each ramen-station card represents one order and emphasizes:
    - table number or pickup code
    - shorthand noodle code in large Chinese text
    - quantity
    - smaller modifier text underneath when present
87. The ramen-station monitor uses a slim horizontal top bar only (station name, active count, current time) and does not reserve any side control panel.
88. The ramen-station card wall uses content-driven packed layout behavior instead of equal-height rows:
    - short orders render as short cards
    - longer orders render as taller cards
    - vertical space under short cards is reused by later cards to maximize visible orders.
89. In the ramen-station card rows, noodle lines with `item_modified_after_submit = true` or `order_modified_after_submit = true` display an `UPDATED` label to the right of the shorthand.
90. Because the ramen screen uses packed masonry layout, each card also shows a stable queue index and created-time hint so chefs can still identify which active order came earlier.
69. The assembling KDS screen polls automatically every 4 seconds.
70. The assembling KDS screen reads active combined assembling tasks from:
    - `GET /api/v1/kds/noodle-display?store_id=1`
71. For each active order returned by noodle-display, the frontend also reads full order detail from:
    - `GET /api/v1/orders/{id}`
72. The assembling KDS screen renders one card per `order_id` and does not split an order into separate update cards.
73. In compact iPad landscape mode, the assembling KDS board uses a horizontal production-board layout:
    - compact sidebar rail remains on the left
    - order cards are arranged left-to-right
    - the board scrolls horizontally for additional orders
    - the main browsing direction is horizontal rather than vertically stacked
    - card width is intentionally larger than before so compact iPad boards tend toward about five visible cards instead of six very narrow cards
52. The active assembling KDS screen only shows orders that still have at least one active assembling task in `kds/noodle-display`.
53. The current assembling task group is:
    - `NOODLE`
    - `WOK`
    - `COLD` rendered as `SIDE`
    - `DEEPFRIED` rendered as `FRIED`
54. KDS item rows are now Chinese-first and kitchen-oriented:
    - the row title uses `item_name_snapshot_zh`
    - wok and some dry noodle items use concise short names such as `牛炒`, `鸡炒`, `番炒`, `素炒`, `炸`, `担`, `鸡凉`
    - line 1 reads the compact primary shorthand from `special_instructions_snapshot`
    - examples verified from the running backend:
      - `中（s）`
      - `中（s） | 走香 +卤 +土豆`
      - `（s） | +煎`
      - `中红`
      - `中酸`
      - `中 | +蛋`
55. If one visible assembling item is completed but the same order still has another active assembling item, the completed item remains visible on the active card and is rendered in a greyed completed style.
56. An order leaves the active assembling KDS page only after no active assembling tasks remain for that order.
57. The KDS history page reads station-group history for the assembling screen from:
    - `GET /api/v1/kds/history?store_id=1&station_code=ASSEMBLING`
58. The active assembling KDS screen groups visible tasks into section blocks and only renders the sections that exist on that order:
    - `SIDE`
    - `NOODLE`
    - `WOK`
    - `FRIED`
    - `SIDE` is intentionally sorted first on the card so cold/small dish prep is visible before noodle and wok work
59. Each KDS item row renders:
    - a compact primary shorthand line with quantity appended using the multiplication sign, for example `大细（s） ×1`
    - prep detail line 2 only when extra remove/add-on shorthand exists
    - the previous separate large item-name title is intentionally minimized/omitted in the compact KDS row layout
    - if a side dish task only has remove modifiers, the frontend falls back to the dish short name as the primary line instead of showing a modifier alone
60. Combo side dishes are rendered through real backend kitchen tasks instead of frontend-only derived rows.
    - Selecting `套餐毛豆`, `套餐土豆丝`, or `套餐拌黄瓜` creates a separate `KitchenTask` on station `COLD`.
    - Those combo-side tasks are marked with `priority = 100` and are treated as `SIDE` rows by assembling KDS and serving-shelf reads.
    - Because they are real tasks, they can be individually selected on the assembling KDS card and completed through `Complete Selected`.
    - Combo side selections are not repeated inside the noodle shorthand line.
    - Combo egg selections are still kept inside the noodle shorthand line, for example `中酸 | +蛋 +香`.
    - Zero-price garnish add-ons such as `加香菜` remain attached to the noodle line and do not leak into the combo side row.
61. Repeated `SIDE` or `FRIED` tasks with the same dish on the same order are merged in the assembling UI into one row with summed quantity when they share the same completion state, for example `黄瓜 ×2` or `炸春卷 ×3`.
62. On the frontdesk ordering page, pressing the quantity minus button on a draft item removes the item when the quantity would drop below 1 instead of getting stuck at 1.
63. Fried-item cards on the ordering page use quick-add instead of opening the customization modal.
64. The fried-item quick-add button now shows immediate tap feedback in the UI:
    - `...` while the add request is in flight
    - `Added` briefly after success
65. On the assembling KDS screen, `Complete Selected` and `Complete All` now also apply an immediate optimistic completed state to the affected rows before the next background refresh arrives, so the selected items grey out without waiting for a manual refresh.
63. The assembling KDS screen does not render all prep details as small chips. Important prep details are rendered as larger wrapped text lines for readability.
64. If `order.is_modified_after_submit = true`, the KDS order card uses a light red background and shows an `UPDATED` badge.
65. If `item.is_modified_after_submit = true`, the KDS item row shows an `UPDATED` label.
66. Current backend task/detail data does not provide a reliable explicit "newly added after submit" item flag, so `NEW` item labeling is `UNKNOWN` in current frontend code.
64. The compact assembling KDS screen header is intentionally simplified:
    - station title
    - current time
    - current date
    - the previous active/urgent/avg metric blocks are no longer rendered in the current frontend layout
65. The assembling KDS `Complete Selected` and `Complete All` buttons call:
    - `POST /api/v1/kitchen-tasks/{id}/ready-for-pickup`
66. The current frontend KDS implementation uses `X-User-Id: 2` for KDS API calls because the seeded `X-User-Id: 1` frontdesk user does not have `kds:noodle:view`.
67. Re-entering the same slot after submit reuses the same submitted/preparing order instead of creating a new draft.
68. The customization modal supports, when configured on the item:
    - bilingual item name
    - base price
    - size selection
    - soup base selection
    - noodle type selection
    - spicy level selection
    - combo toggle
    - combo egg selection
    - combo side selection
    - add-ons with quantity controls
69. In iPad landscape, the customization modal uses a compact workstation layout:
    - reduced outer padding
    - denser section spacing
    - tighter choice controls sized roughly to 44 to 48px tall
    - narrower modal width around the 900px to 1000px range instead of a presentation-width dialog
    - quantity / subtotal / primary action remain visible in a compact bottom action area
70. In iPad landscape, the KDS active and history screens use compact workstation density:
    - tighter sidebar width
    - single compact header row
    - denser order cards with reduced padding and reduced dashboard chrome
    - smaller but still touch-friendly complete buttons
    - prep detail text remains readable while using less vertical space
    - active KDS card browsing is horizontal in compact mode, with additional cards available by horizontal swipe
    - order cards omit redundant order-id-heavy metadata in the main visual hierarchy and prioritize table number, timer, and item rows
    - remove options
    - quantity
    - live subtotal
71. In the current frontend configuration, `Extra Noodle / 加面` is modeled as an add-on, not a remove option.
72. When the customization modal is open, background page scrolling is locked and the modal content scrolls internally.
73. The order summary supports:
    - increment quantity
    - decrement quantity
    - edit item
    - remove item
    - subtotal
    - tax
    - total
    - save draft
    - cancel order
    - submit order
74. The table screen does not show kitchen execution states such as `preparing`, `ready_for_pickup`, or `served`.

## 6. Order Flow

### 6.1 Create Order

Implemented in `OrderServiceImpl#createOrder`.

Step-by-step:
1. Validate referenced store and creator user.
2. Create `orders` row with status `draft`.
3. Generate `order_no`.
4. Create `order_items` rows from request items, including per-item `notes` when provided.
5. Create `order_item_options` rows from selected options.
6. Copy snapshot data from menu master data into bilingual snapshot fields.
7. Calculate subtotal and total amount.
8. Save the order.
9. Publish realtime event `order.created`.

Current item-note behavior:
- frontend cart item rows allow staff to enter `备注 / Special note`
- notes are saved through draft item create/update APIs into `order_items.notes`
- notes survive refresh because they are returned in `OrderItemResponse.notes`
- GRAB tickets print notes under the corresponding item as `备注：...`
- `FRONTDESK_RECEIPT` intentionally does not print item notes

### 6.2 Submit Order

Implemented in `OrderServiceImpl#submitOrder`.

Step-by-step:
1. Load the order and require current status `draft`.
2. Load order items and validate that the order is not empty.
3. Update order status to `submitted`.
4. Set `submitted_at`.
5. For each order item:
   - Determine whether it is a kitchen item or frontdesk beverage/direct-serve item.
   - If kitchen item:
     - validate `menu_items.station_id`
     - resolve station
     - validate station is enabled for store
     - create `kitchen_tasks`
   - If beverage/direct-serve item:
     - create `frontdesk_beverage_items`
6. Phase 1 schema-refactor dual-write is active:
   - after old operational rows are created, the backend also inserts additive `production_tasks` rows
   - `source_type = kitchen_task` rows mirror newly created `kitchen_tasks`
   - `source_type = frontdesk_beverage_item` rows mirror newly created `frontdesk_beverage_items`
   - existing APIs and old tables remain the source of current runtime behavior
7. Deduct inventory using `menu_item_bom` and `menu_item_option_bom`.
8. Insert `inventory_transactions`.
9. If kitchen tasks exist:
   - final order status becomes `preparing`
10. If kitchen tasks do not exist:
   - final order status becomes `ready`
   - `ready_at` is set
11. Save the order.
12. Publish realtime event `order.submitted`.
13. If status became `ready`, also publish `order.ready`.

### 6.3 Kitchen Processing

Implemented in `KitchenServiceImpl`.

Step-by-step:
1. Kitchen task starts at `pending`.
2. Hot kitchen can mark task `in_progress`.
3. Hot kitchen or pass can mark task `ready_for_pickup`.
4. Runner/frontdesk can mark task `served`.
5. When all active kitchen-required tasks for an order are `ready_for_pickup` or `served`, the order becomes `ready`.

### 6.4 Serve Order

Current code has item-level serving behavior:

#### Kitchen-required items
1. Item reaches `ready_for_pickup`.
2. It appears on serving shelf.
3. Runner/frontdesk marks it `served`.

#### Beverage items
1. Beverage item starts at `pending`.
2. Frontdesk marks it `preparing`.
3. Frontdesk marks it `ready`.
4. Frontdesk marks it `served`.

### 6.5 Complete Order

Implemented in `OrderServiceImpl#completeOrder`.

Step-by-step:
1. Load order.
2. Require current order status to be one of:
   - `submitted`
   - `preparing`
   - `ready`
3. For kitchen tasks on that order:
   - `ready_for_pickup` tasks are advanced to `served`
   - `pending` and `in_progress` tasks are cancelled
4. For frontdesk beverage items on that order:
   - `ready` items are advanced to `served`
   - `pending` and `preparing` items are cancelled
5. Change status to `completed`.
6. Set `completed_at`.
7. Save order.
8. Publish realtime event `order.completed`.

Current frontend implication:
- The ordering page calls the backend menu catalog API for menu reads only.
- The ordering page now uses real backend draft-order APIs for:
  - create/reuse draft
  - reopen submitted/preparing order for the same table slot
  - add draft item
  - update draft item
  - update draft quantity
  - remove draft item
  - cancel draft order
  - submit draft order
- For submitted/preparing orders, item-level edits are already written to the same order immediately; the `Update Order` button is a frontend confirmation action rather than a second write API call.
- After a successful submit response, the frontend returns to the table board instead of remaining in editable draft mode.
- Re-entering the same slot after submit is intended to reopen the same submitted/preparing order rather than create a new draft.
- Cancel order releases the local table slot after the backend draft order is cancelled.
- Order completion / end-table behavior from the ordering page is still `TODO`.

### 6.6 Cancel Order

Implemented in `OrderServiceImpl#cancelOrder`.

Current behavior:
1. Completed and already-cancelled orders cannot be cancelled again.
2. Draft orders can be cancelled.
3. Submitted or preparing orders can be cancelled only if their kitchen tasks have not started.
4. Related pending kitchen tasks and beverage items are cancelled where applicable.
5. Order status becomes `cancelled`.
6. Publish realtime event `order.cancelled`.

### 6.7 Payment

- Payment flow in current code: `NOT IMPLEMENTED`

### 6.8 Pickup / Handoff Board

1. Kitchen task reaches `ready_for_pickup`.
2. Backend publishes realtime event `kitchen_task.ready_for_pickup`.
3. Frontdesk pickup board receives the event on `/topic/stores/{store_id}/kds/serving-shelf`.
4. Frontend refreshes only the affected order from `GET /api/v1/kds/serving-shelf?store_id=1`.
5. If the order is dine-in or pickup and still has ready shelf items, a pickup card appears or updates.
6. Frontdesk taps `COMPLETE` on one item row.
7. Frontend calls `POST /api/v1/kitchen-tasks/{taskId}/served`.
8. The served item is removed from the pickup board immediately.
9. If no ready shelf items remain for that order, the whole card disappears.

## 7. Realtime (WebSocket)

### 7.1 Infrastructure

Configured in `WebSocketConfig.java`:
- STOMP endpoint: `/ws`
- SockJS endpoint: `/ws`
- Application destination prefix: `/app`
- Broker prefix: `/topic`

### 7.2 Topic Pattern

Actual topic pattern in code:

```text
/topic/stores/{store_id}/{topic_suffix}
```

### 7.3 Topic Suffixes

Defined in `RealtimeTopics.java`:
- `frontdesk/orders`
- `frontdesk/beverages`
- `kds/noodle-display`
- `kds/hot-kitchen`
- `kds/pass`
- `kds/serving-shelf`
- `history`

### 7.4 Event Payload

`RealtimeUpdateMessage` fields:

```json
{
  "event_type": "string",
  "store_id": 1,
  "order_id": 1,
  "order_item_id": 1,
  "order_status": "string or null",
  "task_status": "string or null",
  "beverage_status": "string or null",
  "is_modified_after_submit": false,
  "happened_at": "timestamp",
  "suggested_topics": ["string"]
}
```

### 7.5 Events Published

#### Order events
- `order.created`
- `order.modified_after_submit`
- `order.submitted`
- `order.ready`
- `order.completed`
- `order.cancelled`

#### Kitchen task events
- `kitchen_task.started`
- `kitchen_task.ready_for_pickup`
- `kitchen_task.served`

#### Beverage events
- `beverage_item.started`
- `beverage_item.ready`
- `beverage_item.served`
- `beverage_item.cancelled`

### 7.6 Trigger Conditions

- Order created -> `order.created`
- Order submitted -> `order.submitted`
- Order becomes ready -> `order.ready`
- Order modified after submit -> `order.modified_after_submit`
- Order completed -> `order.completed`
- Order cancelled -> `order.cancelled`
- Kitchen task started -> `kitchen_task.started`
- Kitchen task ready for pickup -> `kitchen_task.ready_for_pickup`
- Kitchen task served -> `kitchen_task.served`
- Frontdesk pickup board listens specifically to serving-shelf topic messages and refreshes only the affected ready order when `kitchen_task.ready_for_pickup` or `kitchen_task.served` arrives
- Beverage started -> `beverage_item.started`
- Beverage ready -> `beverage_item.ready`
- Beverage served -> `beverage_item.served`
- Beverage cancelled -> `beverage_item.cancelled`

## 8. Important Enums / Constants

### 8.1 Order Status Values Found in Code

Found in `OrderServiceImpl`:
- `draft`
- `submitted`
- `preparing`
- `ready`
- `completed`
- `cancelled`
- `picked_up`

Note:
- `picked_up` exists in code constants/repository filtering.
- No dedicated controller endpoint for a `picked_up` transition exists in current code.

### 8.2 Kitchen Task Status

From `KitchenTaskStatus.java`:
- `pending`
- `in_progress`
- `ready_for_pickup`
- `served`
- `cancelled`

### 8.3 Beverage Status

Used in `FrontdeskBeverageServiceImpl`:
- `pending`
- `preparing`
- `ready`
- `served`
- `cancelled`

### 8.4 Combo Role

Used in `OrderServiceImpl`:
- `main`
- `combo_side`
- `combo_egg`
- `standalone`

### 8.5 Snapshot Category Codes Explicitly Referenced in Logic

Used in `OrderServiceImpl` and `KdsServiceImpl`:
- `DRINK`
- `ALCOHOL`
- `MILK_TEA`

### 8.6 Option Types Explicitly Referenced in Logic

Used in order submission and snapshot/instruction builders:
- `noodle_type`
- `size`
- `addon`
- `remove`
- `soup_base`

### 8.7 Frontend Table Status

From `types/dinein.ts`:
- `available`
- `occupied`
- `alert`

### 8.8 Frontend Seat Status

No standalone frontend seat status enum exists.

Current seat occupancy is represented by whether `seatOrders.A` or `seatOrders.B` exists on a table.

### 8.9 Frontend Service Mode

From `types/dinein.ts`:
- `dine_in`
- `takeout`

## 9. Known Issues / TODO / Missing Parts

This section is based on current code only.

### 9.1 Frontend Gaps

- Order completion / end-table flow is not wired into the visible ordering page yet.
- No payment UI exists.
- No routing beyond the current single-page setup is present.
- The frontend currently hardcodes:
  - `store_id = 1`
  - `X-User-Id = 1`
- The new platform admin UI is intentionally minimal:
  - one `/admin/platform` page
  - JSON editor workflow
  - no advanced form validation, pagination, or bulk operations yet
- Backend draft writes can have a short visibility delay on immediate follow-up `GET /api/v1/orders/{id}` reads; the frontend currently mitigates this with a small retry window for manual refresh reads.
- `GET /api/v1/orders/{id}` can still return stale order-level totals/flags after some submitted/preparing-order edits, even when mutation responses are correct.
- The frontend works around table re-entry by resolving the latest editable order id through the frontdesk order board before fetching full order detail.

### 9.2 Backend Gaps

- Payment flow is not implemented.
- Refund flow is not implemented.
- Authentication is not fully implemented; current authorization depends on `X-User-Id` request header.
- Explicit database foreign key constraints are `UNKNOWN` from code.
- No Flyway or Liquibase migration files were found in the current codebase.
- `production_tasks` has been introduced in additive migration mode, but the system still dual-writes to:
  - `kitchen_tasks`
  - `frontdesk_beverage_items`
  - `production_tasks`
- Platform onboarding is currently template-driven for:
  - stations
  - dining tables
  - menu category structure
  - KDS display configs
  - role setup
- Template-based store creation does not yet clone live menu items/options or historical transactional data.

### 9.3 Order Lifecycle Gaps

- `picked_up` exists in code constants, but no dedicated controller/API transition for `picked_up` was found.

### 9.4 TODO / FIXME Comments

Search result in current codebase:
- No `TODO`, `FIXME`, or `XXX` comments were found in the inspected source files.

### 9.5 Tests Present

Backend tests found:
- `backend/src/test/java/com/restaurant/system/common/auth/AuthorizationServiceTest.java`
- `backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java`

Frontend tests found:
- `NONE`

## 10. Platformization Phase 1

### 10.1 Goal

The current ramen / noodle restaurant has been preserved as the first onboarded tenant while introducing a platform-ready configuration layer.

This phase is intentionally additive and non-breaking:
- existing `store_id = 1` remains valid
- existing menu, station, user, role, dining, and order data stay attached to `store_id = 1`
- existing APIs for POS, KDS, pickup, and checkout remain unchanged

### 10.2 New Platform Tables

Additive tables now present in the running system:
- `organizations`
- `restaurant_templates`
- `store_kds_display_configs`

These are in addition to previously added:
- `dining_tables`
- `production_tasks`

### 10.3 Current Tenant Seed

Runtime seed now creates and maintains:
- `organization_id = 1`
- organization code: `RAMEN_NOODLE_RESTAURANT`
- organization name: `Ramen / Noodle Restaurant`

The existing store is attached without changing its id:
- `stores.id = 1`
- `stores.organization_id = 1`

### 10.4 Database-Driven Table Board

Frontdesk table layout is no longer frontend-mock-first.

Current runtime behavior:
- frontend requests `GET /api/v1/frontdesk/dining-tables?store_id=1`
- backend returns the configured `dining_tables` rows
- frontend derives slot display from:
  - `dining_tables`
  - active orders

Safe fallback:
- if the dining-table config endpoint fails, the frontend still falls back to local mock table definitions to avoid breaking service during development

### 10.5 Current Template

A default template is seeded from the current restaurant:
- template code: `RAMEN_NOODLE_SHOP_TEMPLATE`
- template name: `Ramen / Noodle Shop Template`

Template payload currently captures:
- default station setup
- default KDS display rules
- default menu category structure
- default dining table layout rules
- default role setup

### 10.6 New Admin APIs

Platform admin APIs are now available under:
- `GET /api/v1/admin/platform/overview?store_id=1`
- `GET/POST/PUT /api/v1/admin/platform/organizations`
- `GET/POST/PUT /api/v1/admin/platform/templates`
- `GET/POST/PUT /api/v1/admin/platform/stores`
- `POST /api/v1/admin/platform/stores/from-template`
- `GET/POST/PUT /api/v1/admin/platform/stations`
- `GET/POST/PUT /api/v1/admin/platform/dining-tables`
- `GET/POST/PUT /api/v1/admin/platform/menu/categories`
- `GET/POST/PUT /api/v1/admin/platform/menu/items`
- `GET/POST/PUT /api/v1/admin/platform/menu/item-options`
- `GET/POST/PUT /api/v1/admin/platform/kds-configs`
- `GET/POST/PUT /api/v1/admin/platform/users`
- `GET/POST/PUT /api/v1/admin/platform/roles`

### 10.7 New Admin UI

A minimal admin management page is available at:
- `/admin/platform`

Current UI characteristics:
- overview-based loading from the live backend
- JSON editor driven create/update workflow
- template-based store creation form
- intentionally minimal operational UI, suitable for solo-developer MVP maintenance

### 10.8 Current KDS / Screen Config Seed

Per-store KDS/screen config is now seeded in `store_kds_display_configs` for:
- `FRONTDESK_TABLE_BOARD`
- `FRONTDESK_MENU`
- `KDS_GRAB`
- `KDS_HOT_KITCHEN`
- `KDS_NOODLE_MONITOR`
- `PICKUP_BOARD`

This is the first step in moving restaurant-specific screen behavior out of hardcoded frontend defaults and into database-driven configuration.

### 10.9 Owner Admin Console

A separate owner/manager-facing admin shell is now available at:
- `/admin/dashboard`

This is intentionally distinct from:
- `/admin/platform`

Current role split:
- `Platform Admin`
  - software-provider / developer operations
  - tenant, template, and low-level configuration management
- `Owner Admin Console`
  - restaurant owner / manager experience
  - operational business dashboard
  - no developer-oriented configuration workflow

Current Owner Admin Console layout:
- left sidebar
  - `Home`
  - `Stores`
  - `Menu Management`
  - `Reports`
  - `Integrations`
  - `Settings`
- top header with:
  - current organization context
  - store selector
  - date range selector:
    - `Today`
    - `Week`
    - `Month`
  - compare toggle:
    - previous period on/off

Current owner routes also include:
- `/admin/menu/items`
  - menu maintenance
- `/admin/settings/printing`
  - Print Center
- `/admin/settings/tables`
  - dining table add/edit workflow for owners/managers
  - live clock

Current store selector behavior:
- `All Stores`
- each individual store under the current organization

Current data source behavior:
- organization/store seed data is loaded from the live backend via:
  - `GET /api/v1/admin/platform/overview?store_id=1`
- owner analytics are loaded from the live backend via:
  - `GET /api/v1/admin/dashboard`
- owner dashboard query params:
  - `organization_id`
  - `store_id`
  - `range=today|week|month`
  - `compare=true|false`
- the owner dashboard is no longer mock-only

Current dashboard widgets:
- KPI cards:
  - `Today Sales`
  - `Today Orders`
  - `Average Order Value`
  - `Active Orders`
  - each KPI includes percentage change vs the previous period
- `Alerts & Insights`
- `Sales Trend`
- `Top Selling Items`
- `Worst Items`
- `Order Status`
- `Sales by Hour`
- `Sales by Store`
- `Recent Orders`

Current owner analytics behavior:
- `Sales Trend`
  - uses real completed-order data
  - `Today` renders hourly points
  - `Week` renders daily points
  - `Month` renders weekly points
- `Top Selling Items`
  - shows quantity and revenue
- `Worst Items`
  - ranks lowest-performing items by revenue
- `Order Status`
  - shows `Pending / Preparing / Ready` counts from active orders
- `Alerts & Insights`
  - detects sales drops greater than 15%
  - detects trending items based on quantity change vs the previous period
  - detects low inventory when `current_stock <= safety_stock`
- `Sales by Store`
  - shows per-store sales, previous sales, change percentage, and active order count
- `Recent Orders`
  - is clickable
  - opens `/frontdesk/order?orderId={id}`
  - the checkout / order lookup page now respects this `orderId` query param as the initial selected order

Current non-home sections:
- present as layout-ready placeholders
- no deep CRUD/reporting logic yet

### 10.10 Analytics Data Layer

The backend now includes a dedicated `analytics` module for pre-aggregated owner-reporting data.

Current analytics package:
- `backend/src/main/java/com/restaurant/system/analytics/entity`
- `backend/src/main/java/com/restaurant/system/analytics/repository`
- `backend/src/main/java/com/restaurant/system/analytics/service`
- `backend/src/main/java/com/restaurant/system/analytics/controller`

Current analytics summary tables:
- `sales_daily_summary`
  - daily sales/order rollup per store and organization
  - fields include:
    - `summary_date`
    - `gross_sales`
    - `net_sales`
    - `order_count`
    - `completed_order_count`
    - `cancelled_order_count`
    - `average_order_value`
- `sales_hourly_summary`
  - hourly sales rollup for a single operating date
  - fields include:
    - `summary_date`
    - `hour_of_day`
    - `sales_amount`
    - `order_count`
- `menu_item_sales_summary`
  - per-item sales rollup by date
  - fields include:
    - `menu_item_id`
    - `item_name_snapshot_zh`
    - `item_name_snapshot_en`
    - `quantity_sold`
    - `sales_amount`
    - `order_count`
- `store_performance_summary`
  - daily store comparison snapshot
  - fields include:
    - `sales_amount`
    - `order_count`
    - `average_order_value`
    - `active_order_count`
- `analytics_alerts`
  - persisted analytics insight records
  - fields include:
    - `alert_type`
    - `severity`
    - `title`
    - `message`
    - `metric_value`
    - `comparison_value`
    - `is_resolved`

Current aggregation service:
- `AnalyticsAggregationService`
- implementation:
  - `AnalyticsAggregationServiceImpl`

Current rebuild behavior:
- `POST /api/v1/admin/analytics/rebuild?date=YYYY-MM-DD`
- optional:
  - `store_id`
- if `store_id` is omitted, the service rebuilds all stores
- rebuild currently:
  - loads raw `orders`
  - loads raw `order_items`
  - writes daily summary rows
  - writes hourly summary rows
  - writes item sales summary rows
  - writes store performance summary rows
  - writes analytics alert rows

Current source-of-truth rules for aggregation:
- completed sales metrics use `orders.status = completed`
- completed sales date uses `orders.completed_at`
- cancelled-order count uses `orders.status = cancelled`
- cancelled date uses `orders.updated_at`
- item sales are aggregated from `order_items` that belong to completed orders
- inventory alerts read live `inventory_items.current_stock` and `inventory_items.safety_stock`

Current rebuild query rule:
- `rebuild(date, store_id)` now queries completed sales rows with:
  - `store_id = :storeId`
  - `status = 'completed'`
  - `DATE(completed_at) = :date`
- summary rows for the same `summary_date + store_id` are deleted before reinsertion
- `sales_daily_summary.order_count` now counts completed orders only
- debug logs now print:
  - target date
  - store id
  - completed orders found
  - total gross sales
  - total net sales
  - summary rows inserted

Current analytics summary read API:
- `GET /api/v1/admin/analytics/summaries?store_id=1&range=today|week|month`
- optional:
  - `organization_id`
- current response includes:
  - `sales_daily_summaries`
  - `sales_hourly_summaries`
  - `menu_item_sales_summaries`
  - `store_performance_summaries`
  - `analytics_alerts`

Current scheduling behavior:
- Spring scheduling is enabled at application level with `@EnableScheduling`
- a daily analytics rebuild job now runs automatically for yesterday:
  - cron: `0 20 0 * * *`
  - timezone: `America/Toronto`

Current owner dashboard read strategy:
- owner dashboard still uses the existing API:
  - `GET /api/v1/admin/dashboard`
- dashboard now reads from analytics summary tables where possible
- current priority:
  - if current-day summary coverage exists for the selected scope, use summary tables for:
    - KPI sales/order/AOV
    - sales trend
    - top items
    - worst items
    - store comparison
    - persisted analytics alerts
  - always use live raw data for:
    - active orders KPI
    - order status panel
    - recent orders
    - low-inventory inspection fallback
- if current-day summary data does not exist yet, dashboard falls back to raw `orders` / `order_items`

Current scope protection:
- this analytics upgrade does not change:
  - POS ordering behavior
  - KDS behavior
  - Pickup / handoff behavior
  - Platform Admin behavior
- it is a backend data-layer upgrade only

### 10.11 Reports Module Design

Current owner reports routes:
- `/admin/reports/sales`
- `/admin/reports/items`
- `/admin/reports/stores`

Current reports module location:
- `frontend/src/features/reports`

Current report pages:
- `SalesReportPage`
- `ItemSalesReportPage`
- `StoreComparisonReportPage`

Current shared report components:
- `OwnerAdminReportsShell`
- `ReportsTopBar`
- `ReportMetricCard`
- `ReportPanel`
- `ReportTrendChart`
- `ReportEmptyState`

Current report data source rule:
- reports consume analytics summary APIs only
- reports do not query raw `orders`
- reports do not query raw `order_items`
- reports do not use the owner dashboard raw fallback path

Current analytics report API usage:
- `GET /api/v1/admin/analytics/summaries`

Current supported query parameters for reports:
- `organization_id`
- `store_id`
- `range=today|week|month|custom`
- `anchor_date=YYYY-MM-DD`
- `start_date=YYYY-MM-DD`
- `end_date=YYYY-MM-DD`

Current behavior of report date windows:
- `today`
  - current period uses today
  - compare mode uses previous day through `anchor_date`
- `week`
  - current period uses the current Monday-Sunday window
  - compare mode uses the previous Monday-Sunday window through `anchor_date`
- `month`
  - current period uses the current calendar month
  - compare mode uses the previous calendar month through `anchor_date`
- `custom`
  - current period uses `start_date` + `end_date`
  - compare mode uses the immediately preceding window with the same duration

Current reports data consumption:
- Sales Report
  - `sales_daily_summary`
  - `sales_hourly_summary`
- Item Sales Report
  - `menu_item_sales_summary`
- Store Comparison Report
  - `store_performance_summary`

Current empty-data rule:
- if analytics summary data does not exist for the selected scope or period:
  - report pages show an empty state
  - report pages do not fall back to raw order queries

Current report date-axis behavior:
- reports use `start_date` + `end_date` returned by analytics summaries as the display window
- `Sales Report` fills missing dates in the selected window with zero-value rows on the frontend
- this applies to:
  - `week`
  - `month`
  - `custom`
- result:
  - the daily summary table always shows every day in the selected period
  - the sales trend chart always shows a continuous day sequence for the selected period
- `Sales Report` KPI totals are also computed from this visible filled date sequence:
  - `Total Sales` = sum of visible daily sales rows
  - `Order Count` = sum of visible daily completed-order counts
  - `Average Order Value` = `Total Sales / Order Count`
- this zero-fill behavior is presentation-only
- reports still read only analytics summary tables and do not query raw order data

Current hourly chart readability rule:
- `today` uses the full `00:00-23:00` hourly series from `sales_hourly_summary`
- the sales trend chart suppresses repeated currency labels under every bar
- hourly x-axis shows sampled hour ticks to avoid overlap on laptop-width screens
- exact hourly value remains available through chart hover/title text and the hourly breakdown table

Current loading/caching rule:
- analytics summary responses are cached on the frontend per:
  - organization
  - store
  - range
  - anchor/custom date window
- cache is read-only and only affects report rendering performance
- cache does not change analytics values or business logic

Current navigation integration:
- owner dashboard `Reports` sidebar entry now routes to:
  - `/admin/reports/sales`
- home launcher page also links to:
  - sales report
  - item sales report
  - store comparison report

Current scope protection:
- this reports module upgrade does not modify:
  - POS ordering
  - KDS screens
  - Frontdesk table flow
  - Platform Admin
  - analytics aggregation logic
- this step adds a reporting layer on top of existing analytics summary tables only

### 10.12 Print Center Phase 1

Current Print Center route:
- `/admin/settings/printing`

Current Print Center purpose:
- provide a store-level multi-printer foundation
- keep printer IP/port out of order and KDS controllers
- support future printer routing by business module
- support future receipt template editing without redesigning the print data model

Current Phase 1 database additions:
- `printer_configs`
  - store-level printer registry
  - fields:
    - `id`
    - `store_id`
    - `name`
    - `ip_address`
    - `port`
    - `printer_type`
    - `enabled`
    - `font_size`
    - `font_size_mode`
      - legacy compatibility field from the temporary diagnostic implementation
      - production printing now reads `font_size`
    - `paper_width_mm`
    - `timeout_ms`
    - `created_at`
    - `updated_at`
- `printer_assignments`
  - module-to-printer routing
  - assignment-level print preferences override physical-printer defaults for that module
  - fields:
    - `id`
    - `store_id`
    - `printer_id`
    - `module_code`
    - `enabled`
    - `font_size`
    - `created_at`
    - `updated_at`
- `receipt_templates`
  - future-ready template storage placeholder
  - fields:
    - `id`
    - `store_id`
    - `template_code`
    - `template_name`
    - `template_json`
    - `is_default`
    - `created_at`
    - `updated_at`

Current store-level global print flag:
- `stores.printing_enabled`
- this is the store-wide master gate for Print Center
- module assignment still applies underneath the global switch

Current supported module codes:
- `GRAB`
- `FRONTDESK_RECEIPT`
- `HOT_KITCHEN`
- `COLD_KITCHEN`
- `BAR`
- `TAKEOUT_RECEIPT`

Current Phase 1 active modules:
- `GRAB`
- `FRONTDESK_RECEIPT`

Current default local printer seed for store `1`:
- printer name:
  - `Main Print Center Printer`
- printer IP:
  - `192.168.2.200`
- printer port:
  - `9100`
- printer type:
  - `ESC_POS_TCP`

Current backend Print Center module location:
- `backend/src/main/java/com/restaurant/system/printing`

Current backend Print Center structure:
- `controller`
  - `OwnerPrintingController`
- `service`
  - `PrinterConfigService`
  - `PrinterAssignmentService`
  - `PrintDispatcherService`
- `service/impl`
  - `PrinterConfigServiceImpl`
  - `PrinterAssignmentServiceImpl`
  - `PrintDispatcherServiceImpl`
- `repository`
  - `PrinterConfigRepository`
  - `PrinterAssignmentRepository`
  - `ReceiptTemplateRepository`
- `entity`
  - `PrinterConfig`
  - `PrinterAssignment`
  - `ReceiptTemplate`
- `dto`
  - `PrintCenterOverviewResponse`
  - `PrinterAssignmentUpdateRequest`
  - `PrinterTestRequest`
  - `PrinterTestResponse`
  - `StorePrintingStatusRequest`
  - `PrintRenderRequest`
- `transport`
  - `PrinterTransport`
  - `EscPosTcpPrinterTransport`
- `renderer`
  - `ReceiptRenderer`
  - `GrabReceiptRenderer`
  - `FrontdeskReceiptRenderer`

Current transport architecture:
- Print Center currently supports:
  - `ESC_POS_TCP`
- `EscPosTcpPrinterTransport` opens a TCP socket to the configured IP/port
- ESC/POS init + cut commands are handled inside the transport layer
- emphasized receipt / ticket lines respect assignment-level `font_size` first, then per-printer `font_size`
- production renderers do not write ESC/POS font bytes directly
- `EscPosTcpPrinterTransport` converts `PrintMarkup.doubleHeight(...)` content into the configured ESC/POS font command for the module assignment or assigned printer
- current supported `font_size` values:
  - `XS`
    - `GS ! 0x00`
  - `SMALL`
    - `GS ! 0x11`
  - `MEDIUM`
    - `GS ! 0x22`
  - `LARGE`
    - `GS ! 0x33`
  - `XL`
    - `GS ! 0x44`
- default `font_size`:
  - `MEDIUM`
- current font-size resolution priority:
  1. `printer_assignments.font_size`
  2. `printer_configs.font_size`
  3. `MEDIUM`
- after each enlarged line, transport resets font mode with:
  - `GS ! 0x00`

Current dispatch architecture:
- business modules do not look up printer IPs directly
- `PrintDispatcherService` receives:
  - `module_code`
  - `store_id`
  - `order_id`
- it resolves:
  - store global print enable state
  - module assignment
  - assigned printer
  - correct receipt renderer
  - correct transport

Current async/safety rule:
- printing is dispatched after transaction commit
- printing runs on a dedicated background executor:
  - `printTaskExecutor`
- printing errors are logged
- printing errors do not block:
  - order submission
  - KDS update
  - frontdesk checkout completion

Current Phase 1 trigger points:
- `GRAB`
  - triggered after `OrderServiceImpl.submitOrder(...)`
- `FRONTDESK_RECEIPT`
  - triggered after `OrderServiceImpl.submitOrder(...)`
  - no longer triggered after `OrderServiceImpl.completeOrder(...)`

Current duplicate-print guard:
- `submitOrder(...)` only allows printing from `draft -> submitted`
- a second submit attempt fails because only draft orders can be submitted
- `GRAB` and `FRONTDESK_RECEIPT` are dispatched as two separate after-commit tasks during submit
- if one module print fails, the other module dispatch still continues independently
- `completeOrder(...)` still keeps business completion rules, but it no longer triggers `FRONTDESK_RECEIPT`
- Phase 1 therefore has basic duplicate-print protection at the business action level

Current Print Center priority order:
1. `stores.printing_enabled`
   - global store-wide master gate
2. `printer_assignments.enabled`
   - per-module enable gate
3. `printer_assignments.printer_id`
   - module must point to a real printer
4. `printer_configs.enabled`
   - assigned printer itself must be enabled
5. `printer_assignments.font_size`
   - optional module-specific font size
   - if blank, `printer_configs.font_size` is used
- if any layer above blocks printing, dispatch is skipped without breaking business flow

Current skip behavior:
- if global printing is disabled:
  - all module printing is skipped for that store
- if a module assignment is disabled:
  - that module is skipped even if global printing is enabled
- if no printer is assigned:
  - that module is skipped
- if the assigned printer is disabled:
  - that module is skipped

Current GRAB ticket content:
- table number or pickup number
- order type
- item name
- quantity
- special instructions/customizations
- submitted or created time if available
- no order number
- current GRAB renderer prints kitchen-task content only:
  - item name from `kitchen_tasks.item_name_snapshot_zh/en`
  - customization text from `kitchen_tasks.special_instructions_snapshot`
- item notes are printed as a separate line under the corresponding item:
  - `备注：{order_items.notes}`
- `OrderServiceImpl.buildKitchenSecondaryParts(...)` intentionally excludes item notes from the kitchen-task special-instruction snapshot so notes do not duplicate in GRAB output
- `FRONTDESK_RECEIPT` does not read or print `order_items.notes`
- current text layout:
  - centered `GRAB TICKET`
  - divider line
  - `Table/Pickup: ...`
  - `Order Type: ...`
  - `Submitted: ...` or `Created: ...`
  - divider line
  - GRAB item rows now use transport-level ESC/POS double-height mode for kitchen readability
  - GRAB item sorting is now kitchen-facing and module-specific:
    - `COLD` / side items first
    - `DEEPFRIED` items second
    - `NOODLE` and `WOK` items after that
    - any remaining fallback task types last
  - GRAB item rendering now prefers the kitchen shorthand when it already represents the real production instruction
    - example:
      - old:
        - `传统牛肉面 x1`
        - `大二（s） | +蛋 +葱 走香 走牛`
      - new:
        - `大二（s） | +蛋 +葱 走香 走牛 x1`
    - side-item shorthand also suppresses duplicated parent names when the shorthand is the real kitchen label
      - old:
        - `拌黄瓜 x1`
        - `黄瓜`
      - new:
        - `黄瓜 x1`
  - one GRAB item block now prints as:
    - primary kitchen-facing line in double-height mode:
      - `{kitchen_shorthand_or_item_name} x{quantity}`
    - optional secondary line in double-height mode only if the special instruction is modifier-only and should remain separate
      - `  {special_instructions_snapshot}`
  - divider line
  - `No order number printed.`

Current GRAB ticket target example:

```text
GRAB TICKET
Table/Pickup: T2
Order Type: Dine-in
Submitted: 2026-05-28 23:18
---------------------------
黄瓜 x1
炸春卷 x1
炸馒头 x1

大二（s） | +蛋 +葱 走香 走牛 x1
中素 x1
中酸大宽 x1
--------------------------------
No order number printed.
```

Current GRAB layout behavior notes:
- Chinese output is expected to use the printer-configured transport encoding
- the current local RP820 recommendation remains:
  - `text_encoding = GBK`
- GRAB ticket layout changes do not modify:
  - printer assignment routing
  - module dispatch architecture
  - frontdesk receipt rendering

Current frontdesk receipt content:
- enlarged table number or pickup number header
- `Order Type: Takeout` only for takeout / pickup orders
- submitted time
- customer-facing item name
- optional `Combo` marker
- Chinese size prefix:
  - `中碗`
  - `大碗`
- English size label:
  - `Regular`
  - `Large`
- noodle type line when present:
  - `二细`
  - `三细`
  - `毛细`
  - `大宽`
- quantity
- line price from persisted order data
- subtotal
- tax
- total
- no order number
- current text layout:
  - centered `FRONTDESK RECEIPT`
  - enlarged `桌号: ...` or `取餐号: ...`
  - for takeout / pickup only:
    - `Order Type: Takeout`
  - divider line
  - one order item block per line:
    - `{中碗|大碗}{item_name} [Combo] [Regular|Large] x{quantity}`
    - optional noodle-type line:
      - `{noodle_type}`
    - optional charged add-on lines only:
      - `加面 x1`
      - `加蛋 x1`
    - money line:
      - `{line_total}`
      - or `{unit_price} x{quantity} = {line_total}` when quantity > 1
    - blank line between item blocks
  - divider line
  - `Subtotal: ...`
  - `Tax (14.975%): ...`
  - `Total: ...`
  - divider line
  - `Submitted: ...`
- dine-in orders intentionally do not print an order-type line
- no order-number line or explanatory debug text is printed
- frontdesk receipt intentionally does not print kitchen-production details such as:
  - spicy level
  - soup base
  - remove/addon instructions
  - grab shorthand
- combo display rule:
  - a main item is treated as combo when:
    - `order_items.combo_role = main`
    - or an addon option label contains `套餐` / `combo`
  - zero-priced combo included parts are not printed as separate priced rows
- size display rule:
  - both Chinese and English size markers can be shown:
    - `中碗 Regular`
    - `大碗 Large`
  - size is read from `order_item_options.option_type_snapshot = size`
  - if no size option exists, no size label is printed
- option visibility rule:
  - show noodle type
  - show charged options only when `price_delta > 0`
  - do not show zero-priced remove instructions
  - do not show zero-priced spicy or soup-base production-only details

Current frontdesk receipt totals source:
- `subtotal`
  - from `orders.subtotal_amount`
- `tax`
  - derived from persisted order totals as:
    - `orders.total_amount - orders.subtotal_amount`
  - because the current schema has no dedicated tax column
  - displayed as `Tax (14.975%)`
- `total`
  - from `orders.total_amount`
- receipt money lines still use:
  - `order_items.unit_price`
  - `order_items.line_amount`

Current owner admin APIs:
- `GET /api/v1/admin/printing?store_id=1`
  - print center overview
- `GET /api/v1/admin/printing/printers?store_id=1`
  - list printers
- `POST /api/v1/admin/printing/printers`
  - create printer
- `PUT /api/v1/admin/printing/printers/{id}`
  - update printer
- `DELETE /api/v1/admin/printing/printers/{id}?store_id=1`
  - soft disable printer
- `PUT /api/v1/admin/printing/status`
  - update global store print enabled flag
- `GET /api/v1/admin/printing/assignments?store_id=1`
  - list module assignments
- `PUT /api/v1/admin/printing/assignments/{moduleCode}`
  - update one module assignment
- `POST /api/v1/admin/printing/printers/test`
  - send a simple test ticket
- `POST /api/v1/admin/printing/printers/font-size-test`
  - send one current-font-size test ticket using that printer's saved `font_size`
- `POST /api/v1/admin/printing/modules/test`
  - send a module-routed test ticket using the currently assigned printer
  - currently used for `FRONTDESK_RECEIPT`
- `POST /api/v1/admin/printing/printers/encoding-test`
  - send temporary Chinese encoding comparison tickets for:
    - `UTF-8`
    - `GBK`
    - `GB2312`
- `POST /api/v1/admin/printing/grab-font-test`
  - send temporary GRAB font size comparison tickets for:
    - `FONT TEST A`
    - `FONT TEST B`
    - `FONT TEST C`
    - `FONT TEST D (3X)`
    - `FONT TEST E (4X)`

Current frontend Owner Admin page:
- `frontend/src/features/owner-admin/PrintingSettingsPage.tsx`

Current frontend page sections:
- `Print Center Status`
  - global enable/disable for the store
- `Printer List`
  - add printer
  - edit printer
  - disable printer
  - test print
  - encoding test
  - current font size test
  - GRAB font size test
  - per-printer `font_size` selector with 5 modes:
    - `XS`
    - `SMALL`
    - `MEDIUM`
    - `LARGE`
    - `XL`
  - show IP/port
  - show text encoding
  - show selected font size
  - optional ESC/POS code page
- `Printer Assignment`
  - editable in Phase 1:
    - `GRAB`
    - `FRONTDESK_RECEIPT`
  - each assignment row can configure:
    - assigned printer
    - enabled/disabled
    - assignment-level `font_size`
  - active assignment rows also include module test buttons:
    - `Test GRAB`
    - `Test FRONTDESK_RECEIPT`
    - this tests the currently assigned printer rather than a manually selected printer row
  - reserved for future:
    - `HOT_KITCHEN`
    - `COLD_KITCHEN`
    - `BAR`
    - `TAKEOUT_RECEIPT`

Current menu option price seed values:
- source of truth for runtime seeding:
  - `backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java`
- front-end import seed kept in sync:
  - `frontend/src/data/menuImportSeed.ts`
- current target option prices:
  - `套餐` / `Combo` / `Make it Combo`
    - `+5.00`
  - `加面`
    - `+3.99`
  - `加蛋`
    - `+1.99`
  - `加肉`
    - `+6.99`
  - `加上海青`
    - `+3.00`
- default startup behavior does not update existing `menu_item_options.price_delta`
- existing Admin-managed option prices are preserved across restart
- missing options are inserted on startup when `app.seed.runtime-enabled=true`
- `syncTargetOptionPrices()` only runs when `app.seed.force-overwrite=true`
- new order snapshots use `menu_item_options.price_delta` when creating `order_item_options.price_delta`
- order totals continue to use persisted order item and option amounts from the order flow

Current runtime seed controls:
- `app.seed.runtime-enabled=true`
  - run startup seed
- `app.seed.runtime-enabled=false`
  - skip `RuntimeDataSeeder` entirely
- `app.seed.force-overwrite=false`
  - default
  - insert missing seed records only
  - skip existing menu categories, menu items, menu item options, dining tables, KDS configs, restaurant templates, and printer assignments
  - do not deactivate records that are not present in seed data
- `app.seed.force-overwrite=true`
  - force demo/test reset behavior
  - overwrite configured seed records
  - allow target option price sync
  - allow seed-driven dining-table deactivation

Current phase boundary:
- this Print Center step does not modify:
  - POS ordering UI behavior
  - KDS screen interaction logic
  - Frontdesk operational flow
  - analytics aggregation
  - receipt template editor UI
- this step adds a future-ready print routing foundation with Phase 1 live printing on:
  - `GRAB`
  - `FRONTDESK_RECEIPT`

Current local testing setup:
- default seeded local test printer for store `1`:
  - `192.168.2.200:9100`
- if the database is fresh, startup seed inserts:
  - the default printer row
  - module assignments for all supported module codes
  - active default assignments for:
    - `GRAB`
    - `FRONTDESK_RECEIPT`
  - existing assignments are now preserved across restart; startup seed no longer overwrites a manually selected printer assignment
- local print-center setup steps:
  1. open `/admin/settings/printing`
  2. confirm store printing is enabled
  3. confirm an enabled printer exists with:
     - IP `192.168.2.200`
     - port `9100`
     - type `ESC_POS_TCP`
     - text encoding `GBK`
     - preferred `font_size` for that physical printer
  4. confirm `GRAB` is assigned to its intended printer and enabled
  5. confirm `FRONTDESK_RECEIPT` is assigned to its intended printer and enabled
  6. run row-level `Test Print` for a raw printer connectivity check
  7. run `Test FRONTDESK_RECEIPT` inside the `FRONTDESK_RECEIPT` assignment row to verify module routing and receipt formatting together
  8. run `Encoding Test` to compare:
     - `UTF-8`
     - `GBK`
     - `GB2312`
  9. run `GRAB Font Size Test` to compare:
     - `ESC ! 0x10`
     - `ESC ! 0x30`
     - `GS ! 0x11`
     - `GS ! 0x22`
     - `GS ! 0x33`
  10. submit a draft order to test:
     - `GRAB`
     - `FRONTDESK_RECEIPT`
  11. complete an order only to verify business completion flow, not receipt auto-print

Current dining-table ordering rule:
- all frontdesk dining-table APIs now apply natural table-code sorting centrally in the backend
- sort behavior:
  - letter prefix first
  - numeric portion ascending
  - suffix next
  - raw fallback string last
- example:
  - `T1`
  - `T2`
  - `T3`
  - `...`
  - `T10`
  - `T11`
  - `T12`
- implementation lives in:
  - `PlatformAdminServiceImpl.getDiningTables(...)`
- this keeps both owner-admin table management and frontdesk table-board screens aligned on the same natural ordering

Current active dining-table seed for store `1`:

| Code | Name | Area | Config | Capacity | Status |
| ---- | ---- | ---- | ------ | -------: | ------ |
| T12 | 10 | Main Hall | Split | 4 | Active |
| T12 | 11 | Main Hall | Split | 4 | Active |
| T1 | 1里 | Main Hall | Single | 4 | Active |
| T9 | 7 | Main Hall | Single | 4 | Active |
| T10 | 8 | Main Hall | Single | 4 | Active |
| T11 | 9 | Main Hall | Single | 4 | Active |
| T2 | 1外 | Main Hall | Split | 4 | Active |
| T3 | 2里 | Main Hall | Single | 4 | Active |
| T4 | 2外 | Main Hall | Split | 4 | Active |
| T5 | 3 | Patio | Split | 4 | Active |
| T6 | 4 | Patio | Split | 4 | Active |
| T7 | 5 | Window | Split | 4 | Active |
| T8 | 6 | Window | Single | 4 | Active |

Seed behavior:
- `RuntimeDataSeeder.seedDiningTables()` inserts missing defaults only when `app.seed.force-overwrite=false`.
- Existing dining-table rows are not renamed, moved, reconfigured, resized, activated, or deactivated by default.
- Rows outside this configured set are preserved by default.
- Full seed synchronization and deactivation only happens when `app.seed.force-overwrite=true`.
- Duplicate table codes are currently allowed because the live restaurant uses two `T12` entries with different display names.

### 10.12.1 Frontdesk Ordering Concurrency Notes

Current `/frontdesk/menu` isolation model:
- no `localStorage`
- no `sessionStorage`
- draft isolation is backend-driven by:
  - `store_id`
  - `table_no`
  - `pickup_no`
- frontend hook:
  - `useDraftOrder`
- backend lookup:
  - `GET /api/v1/orders/open-editable`
- dedicated workstation routes are available:
  - `/frontdesk/menu/a`
  - `/frontdesk/menu/b`
- workstation routes share the same backend order model but keep each iPad on a stable frontdesk URL

Current safe behavior:
- two iPads can open different tables at the same time
- different tables do not share draft state
- different browser tabs/windows do not share local cart state through browser storage because browser storage is not used for draft persistence
- the table board now subscribes to realtime `frontdesk/orders` updates and also polls every 4 seconds as a fallback, so table occupancy changes appear quickly across workstation pages

Current same-table behavior:
- two iPads opening the same table attach to the same backend editable order
- this is intentional current behavior, not isolated forked carts
- once one iPad submits the draft:
  - backend prevents a second successful `submitOrder(...)`
  - duplicate GRAB / FRONTDESK_RECEIPT auto-print is blocked by order status because only `draft` orders can be submitted
- current frontend now also has a local in-flight submit guard inside `useDraftOrder`

Current limitation:
- same-table concurrent editing is still last-write-wins at the backend editable-order level
- there is no optimistic locking or editor-presence indicator yet
- if two iPads edit the same table simultaneously after one side submits, they are still collaborating on the same backend order rather than on isolated copies

Temporary GRAB font size diagnostic:
- Owner Admin button:
  - `GRAB Font Size Test`
- current purpose:
  - compare kitchen ticket readability on RP820 before changing production GRAB font mode
- all 3 tickets print the same sample content:

```text
GRAB TICKET

Table/Pickup: T2
Order Type: Dine-in

黄瓜 x1
炸春卷 x1
炸馒头 x1

大二(S) | 走香 走牛 +蛋 +葱
中素
中酸大宽
----
```

- current command set:
  - `FONT TEST A`
    - activate: `1B 21 10`
    - reset: `1B 21 00`
    - meaning:
      - `ESC ! 0x10`
      - double-height only
  - `FONT TEST B`
    - activate: `1B 21 30`
    - reset: `1B 21 00`
    - meaning:
      - `ESC ! 0x30`
      - double-width + double-height
  - `FONT TEST C`
    - activate: `1D 21 11`
    - reset: `1D 21 00`
    - meaning:
      - `GS ! 0x11`
      - double-width + double-height using GS command
  - `FONT TEST D (3X)`
    - activate: `1D 21 22`
    - reset: `1D 21 00`
    - meaning:
      - `GS ! 0x22`
      - triple-width + triple-height
  - `FONT TEST E (4X)`
    - activate: `1D 21 33`
    - reset: `1D 21 00`
    - meaning:
      - `GS ! 0x33`
      - quadruple-width + quadruple-height
- the diagnostic resets the font mode back to normal after the enlarged sample section
- this path is temporary and does not modify production GRAB ticket rendering

Current printer text encoding support:
- `printer_configs.text_encoding`
  - dedicated text-to-bytes charset setting
  - current supported practical values:
    - `GBK`
    - `GB2312`
    - `UTF-8`
- `printer_configs.escpos_code_page`
  - optional numeric ESC/POS code page value
  - when present, Print Center sends:
    - `ESC t n`
  - this remains optional because Chinese output on ESC/POS printers is usually driven first by the selected multibyte charset and firmware support

Current transport encoding behavior:
- `EscPosTcpPrinterTransport` is the only place that converts rendered text into bytes
- previous Phase 1 behavior:
  - fixed `GBK`
  - no ESC/POS code page command
- current behavior:
  - resolve bytes using `printer_configs.text_encoding`
  - default fallback remains:
    - `GBK`
  - optionally send `ESC t n` before text bytes when `escpos_code_page` is configured

Current Chinese rendering findings:
- `GrabReceiptRenderer` and `FrontdeskReceiptRenderer` only generate Java `String` content
- they do not choose or enforce charset
- Chinese garbling risk is therefore in the transport layer, not in the renderer layer
- current RP820-class recommendation:
  - use `GBK` as the default encoding
  - treat `GB2312` as a secondary compatibility test option
  - do not use `UTF-8` as the default for ESC/POS Chinese tickets
- current ESC/POS code-page guidance:
  - single-byte code page selection alone usually does not solve Chinese output
  - for Chinese-capable firmware, correct multibyte charset selection is the first priority
  - keep code page support optional for vendor-specific testing, not as the default fix

Current print logging behavior:
- successful dispatch:
  - `Printed module {moduleCode} for store {storeId} order {orderId} using printer {printerId}`
- skipped dispatch:
  - info-level log with the skip reason
- failed dispatch:
  - `Print dispatch failed for module ...`
- test-print failure:
  - `Test print failed for printer ...`
- Phase 1 uses application log output from Spring Boot / SLF4J

## Feature Package Architecture

The system now has a lightweight Feature Package layer. This is not a SaaS billing system and it does not change existing business flows. The purpose is to define stable package boundaries so routes, APIs, and future permissions can be mapped consistently.

### Feature Packages

| Feature Package | Purpose | Current Page / API Scope |
| --- | --- | --- |
| `CORE_POS` | Core frontdesk table board, ordering, checkout, and order lookup | `/`, `/frontdesk`, `/frontdesk/menu`, `/frontdesk/menu/a`, `/frontdesk/menu/b`, `/frontdesk/order`, order/menu/frontdesk APIs |
| `PRINTING` | Print Center configuration and business receipt/ticket printing | `/admin/settings/printing`, `/api/v1/admin/printing/**`, automatic GRAB and FRONTDESK receipt dispatch |
| `KDS` | Kitchen display, pickup board, serving shelf, and kitchen task actions | `/pickup`, `/kds/grab`, `/kds/hot-kitchen`, `/kds/noodle`, `/kds/ramen`, `/kds/history`, `/api/v1/kds/**`, `/api/v1/kitchen-tasks/**` |
| `ADMIN` | Restaurant owner/admin operational configuration | `/admin/dashboard`, `/admin/menu/items`, `/admin/settings/tables`, admin menu/table APIs |
| `ANALYTICS` | Analytics summaries and reports | `/admin/reports/sales`, `/admin/reports/items`, `/admin/reports/stores`, `/admin/reports/profit`, `/api/v1/admin/analytics/**` |
| `PLATFORM` | Platform/developer tenant, template, store, and KDS display configuration tools | `/admin/platform`, platform-only organization/template/store/KDS-config APIs |
| `DEVELOPER_TOOLS` | Temporary diagnostics and JSON/debug editors | Print encoding/font diagnostics, Platform Admin JSON editor/debug tools |

### Backend Feature Configuration

Backend static configuration lives in:

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/restaurant/system/common/feature/FeaturePackage.java`
- `backend/src/main/java/com/restaurant/system/common/feature/FeatureFlagService.java`

Default current customer configuration:

```yaml
app:
  features:
    core-pos: true
    printing: true
    kds: false
    admin: true
    analytics: true
    platform: false
    developer-tools: false
```

Backend guard behavior:

- `PRINTING` guard protects `/api/v1/admin/printing/**`
- `KDS` guard protects `/api/v1/kds/**` and `/api/v1/kitchen-tasks/**`
- `ANALYTICS` guard protects `/api/v1/admin/analytics/**`
- `PLATFORM` guard protects platform-only organization/template/store/KDS-config APIs
- disabled features return `403` with message `Feature Disabled: {FEATURE_PACKAGE}`
- automatic print dispatch skips safely when `PRINTING=false`, so core order submission remains available

Note: `PlatformAdminController` also hosts some current owner-admin APIs used by menu/table/printing pages. Those owner-admin endpoints remain available under `ADMIN`; only platform-only endpoints are gated by `PLATFORM`.

### Frontend Feature Configuration

Frontend static configuration lives in:

- `frontend/src/features/feature-flags/featureConfig.ts`
- `frontend/src/features/feature-flags/useFeatureFlags.ts`
- `frontend/src/features/feature-flags/FeatureDisabledPage.tsx`

Frontend behavior:

- routes declare a `requiredFeature`
- disabled routes render a `Feature Disabled / Not Available` page instead of crashing
- home page and navigation entries hide links for disabled features
- developer diagnostic buttons are hidden when `DEVELOPER_TOOLS=false`

### Current Customer Feature Config

```yaml
features:
  core_pos: true
  printing: true
  kds: false
  admin: true
  analytics: true
  platform: false
  developer_tools: false
```

Expected behavior:

- Core POS pages remain available
- Print Center remains available
- Owner Admin and Analytics reports remain available
- KDS routes and pickup board are hidden/disabled
- Platform Admin route is hidden/disabled
- developer-only print diagnostics and JSON editor are hidden/disabled

### Future KDS Tablet Configuration Example

```yaml
features:
  core_pos: true
  printing: false
  kds: true
  admin: true
  analytics: true
  platform: false
  developer_tools: false
```

Expected behavior:

- Core POS remains available
- KDS and pickup screens are available
- Print Center is hidden/disabled
- Admin and analytics remain available
- Platform and developer diagnostics remain disabled

### Future Authentication / Authorization Integration

This feature layer is intentionally separate from login and permissions. Future auth should combine:

- `Role`
- `FeaturePackage`
- `Permission`

Reserved code locations:

- `FeaturePackage`:
  - `backend/src/main/java/com/restaurant/system/common/feature/FeaturePackage.java`
- `Permission`:
  - `backend/src/main/java/com/restaurant/system/common/auth/Permission.java`
- route metadata:
  - `frontend/src/features/feature-flags/featureConfig.ts`
- backend feature checks:
  - controller/service-level calls to `FeatureFlagService.requireEnabled(...)`

Recommended future rule shape:

```text
CanAccess = feature_enabled && role_has_permission_for_feature
```

This allows the current custom restaurant setup to remain simple while giving future restaurants a clear path to package-specific access control.

## Authentication Phase 1 + Phase 2

### Design Goal

The authentication module adds modern login/session foundations inside the current Restaurant POS system without replacing the existing business user model.

Kept business model:

- `organizations`
- `stores`
- `users`
- `roles`

Added authentication model:

- `user_credentials`
- `refresh_tokens`
- `role_permissions`

The current `X-User-Id` development mode remains available as a fallback for local/dev flows while new clients can use `Authorization: Bearer <access_token>`.

### Backend Files

| File | Purpose |
| --- | --- |
| `backend/src/main/java/com/restaurant/system/auth/controller/AuthController.java` | Exposes `/api/v1/auth/login`, `/refresh`, `/logout`, `/me` |
| `backend/src/main/java/com/restaurant/system/auth/service/AuthService.java` | Auth use-case interface |
| `backend/src/main/java/com/restaurant/system/auth/service/impl/AuthServiceImpl.java` | Login, refresh rotation, logout revoke, current-user response |
| `backend/src/main/java/com/restaurant/system/auth/service/PasswordService.java` | Password hashing contract |
| `backend/src/main/java/com/restaurant/system/auth/service/impl/PasswordServiceImpl.java` | BCrypt password hashing and verification |
| `backend/src/main/java/com/restaurant/system/auth/service/TokenService.java` | Access/refresh token contract |
| `backend/src/main/java/com/restaurant/system/auth/service/impl/TokenServiceImpl.java` | HMAC-SHA256 JWT access token and SHA-256 refresh token hash support |
| `backend/src/main/java/com/restaurant/system/auth/filter/AuthTokenFilter.java` | Reads Bearer token and sets request authenticated user |
| `backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java` | Reads Bearer-derived context first, then optional `X-User-Id` fallback |
| `backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java` | Still provides current role capability summary |
| `backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java` | Ensures local/dev default login users are active and have recoverable BCrypt credentials |

### Database Tables

| Table | Purpose | Key Fields |
| --- | --- | --- |
| `user_credentials` | Login credentials linked to existing `users` | `user_id`, `login_identifier`, `password_hash`, `password_algorithm`, `password_updated_at`, `is_active` |
| `refresh_tokens` | Server-side refresh token lifecycle | `user_id`, `store_id`, `token_hash`, `expires_at`, `revoked_at`, `created_by_ip`, `user_agent` |
| `role_permissions` | Future-ready database permissions | `role_id`, `feature_package`, `permission`, `capability_code`, `is_allowed` |

`refresh_tokens.token_hash` stores only the hash of the refresh token. Raw refresh tokens are only returned to the client once.

### Auth APIs

`POST /api/v1/auth/login`

```json
{
  "login_identifier": "owner",
  "password": "741xu741"
}
```

Returns:

```json
{
  "access_token": "jwt...",
  "refresh_token": "random...",
  "expires_in": 900,
  "user": {
    "id": 1,
    "username": "owner",
    "full_name": "Owner User",
    "role_code": "ADMIN",
    "store_id": 1,
    "organization_id": 1
  },
  "features": {
    "core_pos": true,
    "printing": true,
    "kds": false,
    "admin": true,
    "analytics": true,
    "platform": false,
    "developer_tools": false
  },
  "permissions": []
}
```

Other endpoints:

- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

### Token Policy

Access token:

- JWT
- HMAC-SHA256
- default expiry: 15 minutes
- contains `user_id`, `role_id`, `store_id`, `organization_id`, `role_code`, `iat`, `exp`

Refresh token:

- secure random string
- default expiry: 30 days
- database stores only SHA-256 hash
- refresh rotates the refresh token and revokes the old token
- logout marks the token `revoked_at`

Config lives in `backend/src/main/resources/application.yml`:

```yaml
app:
  auth:
    jwt-secret: dev-local-restaurant-pos-change-this-secret-please-2026
    access-token-expiration-seconds: 900
    refresh-token-expiration-days: 30
    x-user-id-fallback-enabled: true
```

Production must replace `jwt-secret` and default dev passwords.

### Dev / Local Default Users

For local development, `RuntimeDataSeeder` ensures the default login users exist, are active, and have BCrypt credentials. These credentials are for local/dev recovery only and must be changed before production use.

| Login ID | Password | Role | Store |
| --- | --- | --- | --- |
| `owner` | `741xu741` | `OWNER` | `1` |
| `manager` | `741xu741` | `MANAGER` | `1` |
| `staff` | `741xu741` | `FRONTDESK` | `1` |
| `frontdesk` | `741xu741` | `FRONTDESK` | `1` |
| `kitchen` | `741xu741` | `HOT_KITCHEN` | `1` |

Passwords are stored as BCrypt hashes, never plaintext.

`POST /api/v1/auth/login` accepts the canonical `login_identifier` field and also tolerates legacy cached-client aliases `loginId`, `loginIdentifier`, and `username`.

Login failure diagnostics log only non-secret facts: whether a credential was found, whether it is active, which password algorithm is configured, and whether password verification failed. The raw password is never logged or returned.

### Request User Resolution

Current order:

1. `AuthTokenFilter` validates `Authorization: Bearer <token>` and attaches `AuthenticatedUser` to the request.
2. `RequestUserContextService` uses that authenticated user if present.
3. If no Bearer token exists and `app.auth.x-user-id-fallback-enabled=true`, existing `X-User-Id` mode still works.

This keeps existing POS, KDS, Print Center, and Admin APIs compatible during the transition.

### Frontend Phase 1 Connection

Added minimal frontend files:

- `frontend/src/pages/Login.tsx`
- `frontend/src/services/authService.ts`
- `frontend/src/services/apiClient.ts`

Route:

- `/login`

The login page saves `access_token` and `refresh_token` in `localStorage`. The frontend API client automatically refreshes an expired access token with `POST /api/v1/auth/refresh`, saves the rotated tokens, and retries the original request once. Concurrent 401 responses share one refresh request to avoid rotating the same refresh token multiple times.

Long-running pages such as `/stores/{storeId}/frontdesk` must route API calls through `frontend/src/services/apiClient.ts`. Frontdesk table polling, dining table loading, order APIs, menu catalog, Print Center, admin dashboard, reports, KDS, pickup, platform admin, and owner menu option services use the shared `apiRequest(...)` path so background 401 responses can refresh tokens instead of causing request storms. If one request refreshes tokens before another old request receives its 401, the later request detects the changed access token and retries once without rotating the refresh token again. AuthProvider listens for `restaurant-auth-updated` and `restaurant-auth-expired` events so background refreshes keep React auth state in sync, while failed refreshes clear tokens and allow route guards to return to `/login`.

### Next Authorization Step

Recommended next rule:

```text
CanAccess = feature_enabled && role_has_permission_for_feature
```

Suggested next backend pieces:

- `CurrentUserContext`
- permission-aware route/service guard
- `role_permissions` seed and admin maintenance screen
- gradual replacement of hardcoded `X-User-Id` frontend service headers

## Authentication Phase 3: Owner / Manager / Frontdesk Access

Phase 3 keeps the existing BCrypt + JWT + refresh-token authentication module and adds the first operational authorization layer for real restaurant usage.

### Business Roles

| Role | Scope | Main Access |
|---|---|---|
| `OWNER` | All stores | Frontdesk, Owner Console, Menu Management, Printing Settings, Staff Management, Audit Logs, store switching |
| `MANAGER` | Own store only | Frontdesk, Order History, Menu Management, Printing Settings, Dining Tables, own-store Frontdesk staff management, own-store Audit Logs |
| `FRONTDESK` | Own store only | `/frontdesk`, `/frontdesk/menu`, `/frontdesk/order`, submit/update orders, print/reprint, finish table |
| `ADMIN` | Legacy owner-equivalent | Kept for compatibility with existing local data while new data should use `OWNER` |

Legacy roles such as `HOT_KITCHEN`, `NOODLE_VIEW`, and `PASS` remain in the database for compatibility but are not exposed in the Owner Staff UI.

### Store Scope Rules

- `OWNER` and legacy `ADMIN` can access any store.
- `MANAGER` and `FRONTDESK` must match `users.store_id`.
- Backend store checks are enforced through `AuthorizationService`; frontend route hiding is convenience only.
- Production deployments should set `app.auth.x-user-id-fallback-enabled=false`.
- Local/dev can keep `app.auth.x-user-id-fallback-enabled=true` during migration from older service calls.
- Bearer token identity is always preferred over `X-User-Id`.

### Staff Management

Route: `/admin/staff`

APIs:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/admin/staff/stores` | Return stores visible to the current actor |
| `GET` | `/api/v1/admin/staff?store_id=` | List staff in a store |
| `POST` | `/api/v1/admin/staff` | Create staff with BCrypt password credential |
| `PUT` | `/api/v1/admin/staff/{userId}` | Edit staff profile / role |
| `POST` | `/api/v1/admin/staff/{userId}/deactivate` | Soft deactivate user and credential |
| `POST` | `/api/v1/admin/staff/{userId}/reactivate` | Reactivate user and credential |
| `POST` | `/api/v1/admin/staff/{userId}/reset-password` | Replace password hash |

Rules:

- OWNER can create and manage `MANAGER` and `FRONTDESK`.
- MANAGER can only create/edit/deactivate/reactivate/reset password for own-store `FRONTDESK`.
- FRONTDESK receives `403` for Staff APIs.
- Staff API responses never include `password_hash`, refresh tokens, or credential secrets.

### Audit Logs

Route: `/admin/audit-logs`

Table: `audit_logs`

| Field | Purpose |
|---|---|
| `store_id` | Store scope for filtering and authorization |
| `actor_user_id` | User who performed the action |
| `actor_name_snapshot` | Human-readable actor snapshot |
| `actor_role_snapshot` | Actor role at event time |
| `action` | Stable action code such as `LOGIN_SUCCESS`, `ORDER_REPRINTED` |
| `entity_type` / `entity_id` | Target resource reference |
| `summary` | Short human-readable description |
| `metadata_json` | Redacted structured metadata; never store passwords, PINs, or tokens |
| `request_ip` / `user_agent` | Operational trace metadata |

API:

`GET /api/v1/admin/audit-logs?store_id=&date=&actor=&action=&page=&size=`

Audit visibility:

- OWNER can view all stores or filter by store.
- MANAGER can view own-store audit logs.
- FRONTDESK cannot view audit logs.

Current audit write points include login success/failure/logout, staff create/update/role change/status/password reset, menu item save/update/deactivate, menu option create/update/deactivate/reorder, printing status/mode/assignment changes, print job/order reprint, Update Order, and Finish table.

Audit writes are best-effort. If audit persistence fails, the main order/printing/admin operation should continue and the backend logs a warning.

### Frontend Auth Guard

The frontend now has:

- `AuthProvider`
- `useAuth`
- `RequireAuth`
- `AccessDeniedPage`

On app start, the frontend calls `/api/v1/auth/me` when either an access token or refresh token exists. If the access token is expired but the refresh token is still valid, the shared API client refreshes the session and restores the user without showing a login error. Protected routes redirect unauthenticated users to `/login`. Role failures render Access Denied instead of a blank page.

Role redirects after login:

- OWNER / ADMIN -> `/admin/dashboard`
- MANAGER -> `/admin/dashboard`
- FRONTDESK -> `/frontdesk`

High-priority frontend services now route requests through the shared API header builder so Bearer tokens are attached consistently. The builder strips legacy `X-User-Id` headers before network dispatch.

### Dev Role Switcher

The Dev Role Switcher is a local-only testing tool for switching between real seeded users without bypassing authentication or authorization.

Security model:

- The switcher does not use `X-User-Id`.
- The switcher does not directly spoof frontend user state.
- The backend dev API signs a normal JWT access token and creates a normal hashed refresh token row.
- All subsequent API calls still use `Authorization: Bearer <token>`.
- `AuthorizationService` and `RoleCapabilityRegistry` remain the source of truth for permissions.
- The backend requires both:
  - active Spring profile `local` or `dev`
  - `app.dev-tools.role-switcher-enabled=true`
- Pilot/production config must keep `app.dev-tools.role-switcher-enabled=false`.

Backend APIs:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/dev/test-users` | List predefined dev users only |
| `POST` | `/api/v1/dev/switch-user` | Switch to one predefined dev user and return normal auth tokens |

The API only accepts predefined usernames:

| Login ID | Display Name | Role |
|---|---|---|
| `dev_owner` | Dev Owner | `OWNER` |
| `dev_frontdesk` | Dev Frontdesk | `FRONTDESK` |
| `dev_kitchen` | Dev Kitchen | `HOT_KITCHEN` |
| `dev_runner` | Dev Runner | `PASS` |
| `dev_platform_admin` | Dev Platform Admin | `ADMIN` |

Local backend enablement:

```yaml
app:
  dev-tools:
    role-switcher-enabled: true
```

Frontend enablement:

```bash
VITE_ENABLE_DEV_ROLE_SWITCHER=true npm run dev
```

When the frontend env flag is missing or not `true`, no switcher UI is rendered. When the backend flag/profile check fails, dev API calls return `403` and the switcher shows a backend-disabled error.

KDS route guards include the real KDS roles for local testing:

- `/kds/hot-kitchen`: `HOT_KITCHEN`, plus owner/admin/manager
- `/kds/noodle` and `/kds/ramen`: `NOODLE_VIEW`, plus owner/admin/manager
- `/pickup` and `/kds/grab`: `PASS`, plus owner/admin/manager

Feature flags still apply first. If `KDS=false`, these pages show Feature Disabled even when the switched user has the KDS role.

## Printing Reliability Module

Printing Mode has been upgraded from fire-and-forget dispatch to durable print job tracking for controlled restaurant pilot use.

## iPad Local Production Testing

Use a production preview build for iPad field testing. Do not use `npm run dev` from an iPad during restaurant trials because the Vite development server enables HMR, React development behavior, and extra websocket traffic that can make iPad Safari feel slow or unresponsive.

Recommended local setup:

1. Keep the backend running on port `8080`.
2. Build the frontend from `frontend/`:
   ```bash
   npm run build:prod-preview
   ```
3. Serve the production build on the LAN:
   ```bash
   npm run preview:lan
   ```
4. Find the Mac LAN IP, for example `192.168.2.33`.
5. Open the iPad browser at:
   ```text
   http://192.168.2.33:5173
   ```

Operational notes:

- The iPad and Mac must be on the same WiFi network.
- The Mac must not sleep during service.
- `preview:lan` serves the compiled `dist` bundle and does not enable Vite HMR.
- The preview server proxies `/api` and `/ws` to the backend at `http://localhost:8080`, so iPad pages can continue using relative API paths.
- Use `npm run dev -- --host 0.0.0.0` only for development debugging from a laptop, not for iPad field testing.
- If iPad production preview still feels slow, the next optimization targets are 4-second polling on frontdesk/KDS/pickup pages, WebSocket refresh debouncing, and KDS order-detail N+1 requests.

### Purpose

The target operating mode is Printer Restaurant Mode:

1. Frontdesk submits an order.
2. GRAB and FRONTDESK_RECEIPT print jobs are created.
3. Each receipt is dispatched through Print Center module assignment.
4. Waiter collects payment offline by terminal or cash.
5. Waiter clicks Finish in Order Center.
6. Order moves to completed history.

No online payment provider integration is included in this phase.

### New Tables

| Table | Purpose |
| --- | --- |
| `print_jobs` | Durable record for each required receipt print. Tracks module, order, printer, rendered snapshot, status, retry count, and failure reason. |
| `print_job_attempts` | Attempt history for each print job. Tracks attempt number, printer, status, start/end time, and error. |

### Print Job Statuses

| Status | Meaning |
| --- | --- |
| `PENDING` | Job was created but has not started transport dispatch. |
| `PRINTING` | Transport dispatch is in progress. |
| `PRINTED` | Socket print dispatch completed successfully. |
| `FAILED` | Dispatch could not complete or configuration was missing/disabled. |
| `CANCELLED` | Reserved for future operator cancellation. |

### Important Reliability Behavior

- Order submission still does not fail if printing fails.
- GRAB and FRONTDESK_RECEIPT each create their own `print_jobs` row.
- Missing assignment, disabled assignment, disabled printer, renderer failure, and socket failure are all recorded as `FAILED`.
- If store-level Print Center status is disabled, order-triggered GRAB and FRONTDESK_RECEIPT jobs are created as `CANCELLED` with `error_code = PRINTING_DISABLED`.
- Store-level Print Center status only controls automatic order-triggered printing.
- Disabling Print Center does not hide or disable Print Center configuration APIs.
- Printer list, printer assignment, print job history, printer save, assignment save, and connection test remain available while Print Center is disabled.
- Successful dispatch updates `printed_at` and printer `last_successful_print_at`.
- Failed dispatch updates `failed_at`, `error_message`, and printer `last_failed_print_at`.
- Manual reprint is supported from Print Center and Order Center.
- Basic connection testing opens a TCP socket to the configured printer host/port with timeout and stores the last connection result on `printer_configs`.

### New Backend APIs

| API | Purpose |
| --- | --- |
| `GET /api/v1/admin/printing/jobs` | List print jobs with filters for status, order, module, printer, and date range. Defaults to today. |
| `POST /api/v1/admin/printing/jobs/{jobId}/reprint` | Reprint an existing job using its rendered snapshot when available. |
| `POST /api/v1/orders/{orderId}/reprint` | Recreate and print GRAB or FRONTDESK_RECEIPT from the order snapshot. |
| `POST /api/v1/admin/printing/printers/connection-test` | Test printer TCP connectivity and persist the last connection result. |

### Frontend Surfaces

Print Center `/admin/settings/printing` now shows:

- printer IP/port/status
- assigned modules per printer
- last successful print time
- last failed print time
- last printer error
- last connection test result with explicit `Connected`, `Failed`, or `Not tested` status
- direct test print for a physical printer
- module test print for assignment-backed production paths such as `Test GRAB` and `Test FRONTDESK_RECEIPT`
- test connection
- test all printer connections from the Printer List header
- failed print jobs counter with a high-visibility warning state
- failed print jobs
- recent print jobs
- Pad Direct registered devices with last seen, app version, platform, and active status
- Pad Direct claim state on recent/failed jobs, including claimed device, lease expiration, printed device, and payload availability
- manual reprint action
- delete printer action, blocked when the printer is still assigned to any module
- assignment-level enable/disable controls; physical printer configs are not disabled from normal operations

Print Center UX hardening rules:

- Printer configs are treated as physical devices and default to enabled.
- Normal operations should enable or disable printing at the module assignment level.
- Deleting a printer is allowed only after all module assignments are removed.
- `Test GRAB` and `Test FRONTDESK_RECEIPT` follow the production routing path: module code → assignment → assigned printer enabled check → renderer → transport.
- Frontdesk ordering checks GRAB print job status shortly after submit. If the kitchen ticket fails or is cancelled, the ordering page shows: `Kitchen ticket failed to print. Please reprint immediately.`

GRAB Ticket v2.0 layout rules:

- GRAB tickets no longer print `GRAB TICKET`, `Order Type`, `Table/Pickup`, order number, or explanatory debug text.
- Dine-in tickets print the table line at the top as large text, for example `桌号：1里`.
- Takeout tickets print the pickup identifier directly when available; they do not print `Order Type`.
- Item content is printed in the middle using the assignment font size.
- Takeout tickets print a large `外卖` above the final time.
- The final submitted/created time is printed at the bottom in small text using `HH:mm`.
- Green garnish shorthand is kitchen-optimized only when both matching options are selected: `加香菜` + `加葱` become `加青`, and `走香菜` + `走葱` become `走青`.
- Single green garnish instructions stay explicit: only `加葱` prints `加葱`, only `加香菜` prints `加香菜`, only `走葱` prints `走葱`, and only `走香菜` prints `走香菜`.
- If an item contains mixed add/remove green instructions such as both `加葱` and `走葱`, the original instructions are preserved.
- Side dishes are grouped only by the same dish name and the same sorted requirement set.
- Examples: two plain `黄瓜` rows print as `黄瓜 x2`; two `黄瓜 | 走花生` rows print as `黄瓜 x2` plus `走花生`.
- Different side-dish requirements remain separate, for example `黄瓜 | 走花生` and `黄瓜 | 加辣` print as separate `黄瓜 x1` blocks.
- GRAB never uses a generic `小菜 xN` total because kitchen staff still need the exact side-dish name.

Frontdesk receipt font behavior:

- `FRONTDESK_RECEIPT` uses assignment-level font size for the full receipt body.
- The selected assignment size applies to item names, combo/size labels, option lines, prices, subtotal, tax, total, and submitted/created time.
- Renderer output stays text/markup-only; ESC/POS font commands are still owned by the transport layer.

Order Center `/orders` and `/frontdesk/order` now supports:

- Reprint GRAB
- Reprint Receipt

## Field Test Feedback Fixes - 2026-06-17

This pass keeps existing POS/KDS/Print Center architecture intact and applies focused restaurant-floor fixes.

### Split Table Display

- Backend slot/table values still use `A` and `B`.
- Frontend display maps split slot suffixes only for UI readability:
  - `-A` -> `-左`
  - `-B` -> `-右`
- The display mapping is used on the table board, occupied table cards, ordering header, order summary, finish/submit messages, and edit-order entry display.
- No backend routing, table lookup, order table number, or slot persistence logic was changed.

### GRAB Ticket Spacing

- GRAB tickets add extra blank feed lines at the top before any header content.
- This gives the kitchen roughly 1cm more paper above the ticket for clipping/inserting.
- The change is renderer-only and does not affect `FRONTDESK_RECEIPT`, font-size settings, order numbers, assignment routing, print jobs, or retry/reprint behavior.

### Fried Noodle Option Rules

- All fried-noodle SKUs now share a dedicated fried-noodle option set:
  - `beef_chow_mein`
  - `chicken_chow_mein`
  - `tomato_chow_mein`
  - `vegetable_chow_mein`
- Fried-noodle add-ons are limited to:
  - `加煎蛋`
  - `加卤蛋`
- Fried-noodle remove options are ordered as:
  - `走豆芽`
  - `走洋葱`
  - `走青椒`
  - `走西兰花`
  - `走大头菜`
  - `走西葫芦`
  - `走所有菜`
  - `走番茄`
- `RuntimeDataSeeder` now reconciles only fried-noodle options by activating required fried-noodle options and deactivating legacy fried-noodle-only extras such as soup-noodle add-ons.
- Soup noodles, dry noodles, side dishes, and fried items keep their existing option behavior.
- `frontend/src/data/menuImportSeed.ts` was synchronized with the same fried-noodle option set for future import/dev tooling consistency.

### Edit Order Update Printing

- Submitted/preparing/ready orders can still be edited through the existing staged update flow.
- After `Update Order`, the backend automatically creates GRAB and FRONTDESK_RECEIPT update print jobs after the update transaction commits.
- Update print jobs use the normal assignment and printer routing path.
- GRAB update tickets print a large `UPDATED` marker and filter render data to items/tasks whose `order_update_batch_id` matches the committed batch.
- FRONTDESK update receipts print `UPDATED` / `Added items only`, filter render data to the same `order_update_batch_id`, and show subtotal/tax/total for the added batch only.
- FRONTDESK update receipts include combo side dish lines and combo side remove instructions under the combo main item.
- This does not re-submit the order and does not recreate kitchen tasks.
- Manual Print / Reprint remains a complete current-order print and does not use the update batch filter.

### Takeout Receipt Copies

- `printer_assignments` now includes `takeout_receipt_copies`.
- The value belongs to assignment configuration because one physical printer can serve multiple modules with different operational needs.
- The Printing Settings assignment row exposes the setting only for `FRONTDESK_RECEIPT`.
- Supported values:
  - `1 copy`
  - `2 copies`
- Default value is `1`.
- Dine-in `FRONTDESK_RECEIPT` always prints one copy.
- Takeout/pickup `FRONTDESK_RECEIPT` prints two copies only when the `FRONTDESK_RECEIPT` assignment is configured with `takeout_receipt_copies = 2`.
- GRAB tickets are not affected.

### Frontdesk Receipt Combo Ordering

- `FRONTDESK_RECEIPT` now prints `Combo` before the item name.
- Example: `1 x Combo 大碗传统牛肉面 Large`.
- Combo side dishes are printed beneath the combo main line for packing visibility, for example `小菜: 拌黄瓜` followed by side-specific requests such as `走花生`.
- GRAB kitchen tickets are unchanged and continue to focus on production instructions.

### Frontend Cache Versioning

- The frontend build defines a build version at compile time.
- On first load after a build-version change, the browser clears known UI/menu cache keys and refreshes once.
- Auth tokens are intentionally preserved:
  - `restaurant_pos_access_token`
  - `restaurant_pos_refresh_token`
- Cleared cache keys currently include:
  - `restaurant_pos_menu_catalog`
  - `restaurant_pos_feature_config`
  - `restaurant_pos_ui_cache`
  - `restaurant_pos_owner_dashboard_cache`
- A sessionStorage guard prevents infinite refresh loops.
- If iPad Safari still displays stale assets, manually clear website data for the Mac LAN host or close/reopen the tab after restarting production preview.

### Mock Printing Mode

Print Center supports four store-level runtime modes through `stores.printing_mode`:

- `REAL`: production/default mode. Renderers generate receipt text and the ESC/POS TCP transport connects to the configured printer IP/port.
- `MOCK`: no-printer local testing mode. Renderers still run and print jobs are created, but the dispatcher never opens a socket connection. Jobs are marked `PRINTED` with attempt message `Mock print succeeded - no physical printer used`.
- `PAD_DIRECT`: Android Pad local-printing mode. Renderers still run and print jobs are created with `rendered_text_snapshot` and `escpos_payload_base64`, but the backend leaves jobs `PENDING` for Pad clients to claim, print locally, and report complete/fail/release. The backend does not open a socket connection to the physical printer in this mode.
- `DISABLED`: automatic printing is off. Order submission still succeeds and order-triggered print jobs are cancelled with `PRINTING_DISABLED`.

The legacy `stores.printing_enabled` field is retained for compatibility:

- `REAL`, `MOCK`, and `PAD_DIRECT` set `printing_enabled = true`.
- `DISABLED` sets `printing_enabled = false`.
- If `printing_mode` is missing, the backend falls back to `printing_enabled=false -> DISABLED`, otherwise `REAL`.

Mock mode behavior:

- Submit Order still creates separate GRAB and FRONTDESK_RECEIPT print jobs.
- GRAB and FRONTDESK_RECEIPT renderers still execute, so formatting, Chinese text, quantities, prices, tax, totals, table/pickup labels, and notes can be verified.
- `rendered_text_snapshot` is saved on the print job and returned to Print Center.
- `/admin/settings/printing` shows the current mode and displays a blue warning banner in Mock mode: `当前为无打印机测试模式，系统不会连接任何实体打印机。`
- Recent/Failed Print Jobs include `Preview Receipt`, which opens the saved rendered receipt text.
- Test Print, Test GRAB, Test FRONTDESK_RECEIPT, Order Center reprint, and Print Center reprint all honor Mock mode.
- Connection test in Mock mode returns mock success and does not attempt TCP connection.

Pad Direct behavior:

- Store devices are registered through `POST /api/v1/devices/register` by an authorized store admin/manager using normal Bearer authentication.
- Registration returns a raw device token once; the backend stores only `device_token_hash`.
- Runtime Pad worker requests authenticate with `X-Device-Id` and `X-Device-Token`.
- `GET /api/v1/stores/{storeId}/printing/jobs/pending` returns `PAD_DIRECT` jobs that are `PENDING` or have expired `CLAIMED` leases.
- `POST /api/v1/printing/jobs/{jobId}/claim` atomically changes the job to `CLAIMED` and records `claimed_by_device_id`, `claimed_at`, `claim_expires_at`, and `client_attempt_token`.
- `GET /api/v1/printing/jobs/{jobId}/payload` returns the rendered receipt text plus `escpos_payload_base64` only to the device that owns the claim.
- `POST /api/v1/printing/jobs/{jobId}/complete` marks the job `PRINTED`.
- `POST /api/v1/printing/jobs/{jobId}/fail` marks the job `FAILED`, records the error, and increments `retry_count`.
- `POST /api/v1/printing/jobs/{jobId}/release` returns the job to `PENDING` without counting it as a print failure.
- `print_job_attempts` records Pad Direct attempts with `device_id`, `transport_type = PAD_DIRECT`, `client_attempt_token`, status, error, and raw result metadata.
- Backend logs include:
  ```text
  ===== MOCK PRINT START =====
  Module: GRAB
  Printer: Mock Printer
  Print Job ID: ...
  Order ID: ...

  ...rendered receipt text...

  ===== MOCK PRINT END =====
  ```

Operational safety:

- Production/default mode remains `REAL`.
- Mock mode must be explicitly selected from Print Center or set in the database for local testing.
- Mock mode is designed for local order-flow, receipt-format, reprint, and performance testing without printer timeouts or socket failure noise.

### Known Limitations

- There is still no automatic retry scheduler.
- There is still no ESC/POS paper-out detection.
- `PRINTED` means socket write completed; it does not guarantee the printer physically produced paper.
- Reprint is manual for pilot operations.
- Existing developer-only encoding/font diagnostics remain behind `DEVELOPER_TOOLS`.

## Frontdesk Performance P0 Optimization

The current pilot target is Printer Restaurant Mode, so the first production-preview performance pass focuses on the Frontdesk table board instead of KDS aggregation.

### Frontdesk Table Board Sync

- Frontdesk table board realtime updates are WebSocket-led through `/topic/stores/{storeId}/frontdesk/orders`.
- Polling remains as a safety fallback, but the interval is now 30 seconds instead of 4 seconds.
- Polling is paused while the browser tab is hidden (`document.visibilityState !== 'visible'`).
- When the page becomes visible again, the table board performs one immediate sync.
- WebSocket-triggered board refreshes are debounced by 500ms so a burst of order events only causes one full board refresh.
- In-flight sync protection prevents concurrent full board reloads.
- If a new sync request arrives while another sync is running, the hook marks one pending sync and performs at most one follow-up sync after the current request completes.
- `useTableBoard` accepts an `enabled` option.
- `DineInPage` disables `useTableBoard` while an ordering context is active, including `/frontdesk/menu`, `/frontdesk/menu/a`, and `/frontdesk/menu/b`.
- When disabled, the table board hook does not start polling, does not subscribe to the frontdesk WebSocket topic, does not listen for visibility resume sync, and ignores pending full-sync requests.
- Direct entry to a menu URL initializes the ordering context from the route before the table-board hook is enabled, so menu pages do not perform an unnecessary initial table-board sync.
- Returning from ordering mode to the table board re-enables the hook and triggers an immediate table-board sync.

### Finish Flow Refresh

- The Finish action still refreshes the affected table immediately after order completion.
- The previous repeated read-model fallback refresh loop was reduced to one delayed fallback refresh.
- WebSocket updates and 30-second fallback polling now cover any remaining eventual consistency cases without continuously hammering the backend.

### Expected Operational Impact

- Multiple iPads left open on `/frontdesk` no longer send table-board API requests every 4 seconds.
- Background tabs do not continue polling.
- Submit Order and Finish remain responsive because WebSocket events still drive quick board updates.
- This optimization does not change order submission, printing, checkout, KDS, analytics, authentication, or business rules.

### Temporary Frontdesk CPU Diagnostics

Temporary browser-console diagnostics were added to investigate high idle CPU in the Frontdesk renderer.

- `DineInPage` now counts renders with `console.count("DineInPage render")`.
- `DineInPage` logs every `activeOrderingContext` update through `console.count("setActiveOrderingContext called")` with a reason.
- `useTableBoard` counts hook renders, effect evaluations, and backend sync requests.
- `useTableBoard` logs enabled-state changes, visibility changes, in-flight sync behavior, polling interval creation/cleanup, and WebSocket subscription creation/cleanup.
- Expected healthy idle behavior:
  - `DineInPage render` should not keep increasing when no user action occurs.
  - `syncFromBackend called` should not fire continuously.
  - There should be one active table-board polling interval only while the table board is enabled.
  - There should be one active frontdesk WebSocket subscription only while the table board is enabled.
  - When ordering mode is active (`/frontdesk/menu`, `/frontdesk/menu/a`, `/frontdesk/menu/b`), table-board polling and WebSocket sync should remain disabled.
- These diagnostics are intentionally temporary and should be removed after the CPU loop root cause is confirmed.

## Menu Option Management Phase 2

Phase 2 upgrades menu item options from display-only seed data into owner-manageable menu master data while preserving historical order snapshots.

### `menu_item_options` Metadata

The `menu_item_options` table now supports nullable metadata fields:

- `sort_order`: controls `/frontdesk/menu` display order. `NULL` falls back to `id` order.
- `option_code`: stable machine code used by ordering, combo, and kitchen logic. If empty, code uses legacy Chinese-name fallback.
- `option_group`: semantic group such as `SIZE`, `ADD_ON`, `REMOVE`, `COMBO`, `COMBO_EGG`, `COMBO_SIDE`, or `COMBO_SIDE_REMOVE`. If empty, frontend and backend fall back to legacy `option_type`.
- `parent_option_id`: parent-child relation for child options, for example `COMBO_SIDE_REMOVE` (`走花生`) under `COMBO_SIDE` (`套餐拌黄瓜`).

Historical orders remain safe because `order_item_options` stores snapshots. Changing, disabling, or reordering current menu options does not rewrite old order detail or old print previews.

### Owner Admin Option APIs

Owner menu option management uses store-scoped Admin APIs instead of the broad Platform Admin option endpoint:

- `GET /api/v1/admin/menu/items/{itemId}/options`
- `POST /api/v1/admin/menu/items/{itemId}/options`
- `PUT /api/v1/admin/menu/items/{itemId}/options/{optionId}`
- `DELETE /api/v1/admin/menu/items/{itemId}/options/{optionId}`
- `PUT /api/v1/admin/menu/items/{itemId}/options/reorder`

Delete is a soft delete: it sets `is_active=false` and does not physically remove the row. The backend validates that the current user can administer the item's store, options belong to the same menu item, parent options belong to the same menu item, parent options do not point to themselves or form cycles, and `COMBO_SIDE_REMOVE` parents are `COMBO_SIDE`.

The Owner Menu Management UI currently exposes a simplified operations panel for day-to-day maintenance. The panel intentionally shows only `Add-on` and `Remove` groups, with create, edit, deactivate/reactivate, and Up/Down sorting controls. Advanced groups such as `SIZE`, `SOUP_BASE`, `NOODLE_TYPE`, `SPICY_LEVEL`, `COMBO`, `COMBO_EGG`, `COMBO_SIDE`, and `COMBO_SIDE_REMOVE` remain supported by the API/catalog data model for ordering and combo behavior, but they are not shown in this simplified owner panel yet. Inactive options remain visible in Admin for recovery, while `/frontdesk/menu` only receives active options.

Owner menu option APIs use the `admin:menu_manage` capability instead of the broader `admin:store_config` capability. All calls remain store-scoped and still verify that the menu item and option belong to the current store. Menu item create/update endpoints accept `admin:menu_manage` as well as the older `admin:store_config` capability for backward compatibility.

### Combo UI and Child Remove Options

The ordering modal uses touch-friendly buttons for combo egg and combo side selection. Combo child remove options are shown only after their parent side is selected. Switching combo side clears previously selected child remove options to prevent stale requests.

Example:

- Select `套餐拌黄瓜`
- Show child option `走花生`
- Switching to `套餐毛豆` clears `走花生`

Combo side remove options can be resolved from two sources:

- the selected side dish's own active `REMOVE` options, resolved from stable combo side option codes such as `combo_shredded_potato` -> side item SKU `shredded_potato`
- legacy explicit child options where `parent_option_id` points to a `COMBO_SIDE` option

Menu Management owns the displayed side requests. The catalog includes side item remove options on combo side options so `/frontdesk/menu` can show existing side-dish requests such as `走洋葱`, `走花生`, and `走香菜` for `套餐土豆丝`. If the real side item has active `REMOVE` options, the frontend uses those options and ignores legacy child options to avoid duplicates. Legacy child options are used only when the real side item has no active remove data. When the selected side remove belongs to the real side item rather than the main dish, order save accepts it only if the matching combo side is selected, then stores the snapshot as `COMBO_SIDE_REMOVE` with the selected combo side option as its parent. This keeps GRAB/kitchen side-task instructions correct without exposing arbitrary cross-item options.

The order payload includes selected combo, egg, side, and side child remove option IDs. New order option snapshots include `option_code_snapshot`, `option_group_snapshot`, and `parent_option_id_snapshot` so kitchen logic can use stable metadata for new orders. Legacy Chinese-name fallback remains only for older data.

### Frontdesk Ordering UX and Receipt Readability

- The item customization modal uses a large round close button with a touch-sized hit area. Tapping the modal backdrop closes the modal, while taps inside the modal content do not.
- Closing the customization modal unmounts the draft editor. Reopening an item rebuilds the draft from the selected menu item or the selected order line, so stale size/add-on/notes/quantity state is not reused.
- The `/frontdesk/menu` current-order panel uses a fixed-height flex layout in the ordering workspace: header, independently scrollable order lines, and a footer that remains visible with item count, subtotal, tax, total, and the submit/update action.
- `FRONTDESK_RECEIPT` no longer prints the `FRONTDESK RECEIPT` title. Its first receipt line is the table/pickup label rendered through `PrintMarkup.large(...)`, for example `[[LARGE]]桌号: 1里[[/LARGE]]`.
- Kitchen instruction generation and GRAB rendering aggregate duplicate add-on tokens instead of dropping or duplicating them. For example, `+蛋 +蛋x2` prints as `+蛋x3`; `+蛋 +煎x2` remains `+蛋 +煎x2`; item quantity such as trailing `x2` is not multiplied into modifier quantity.

### Menu Item Deactivation

Menu Management uses soft deactivation for menu item deletion. `Delete / Deactivate Item` sets `menu_items.is_active=false`; it does not hard delete the row. Active catalog queries hide inactive items from `/frontdesk/menu`, while Admin `All Status` can still show them and historical orders/print previews remain snapshot-backed. `Sold Out` remains a separate temporary availability state and should not be used as permanent deletion.

### Runtime Seeder Rule

`RuntimeDataSeeder` may supplement missing option metadata (`option_group`, `option_code`, `sort_order`, `parent_option_id`) but, when `app.seed.force-overwrite=false`, it must not overwrite owner-managed `name_zh`, `name_en`, `price_delta`, or `is_active`. Parent relationships are resolved through stable `option_code`, not display text.

## Authentication Phase 3 Stability Updates

Phase 3 adds simplified staff access management and audit log visibility for the Owner Admin Console.

### Staff Management Reliability

- `/api/v1/admin/staff/stores` returns the stores visible to the current owner-equivalent user (`OWNER` or legacy `ADMIN`) or the manager's own store.
- `/api/v1/admin/staff?store_id={id}` returns store-scoped staff records only. Responses do not expose password hashes or credential secrets.
- Legacy `ADMIN` is treated as owner-equivalent for staff visibility and store scope.
- Staff repository queries use explicit JPQL for snake_case entity fields such as `store_id` to avoid runtime derived-query parsing failures.
- Empty stores or empty staff lists are handled as empty states in the UI instead of backend 500 errors.

### Audit Log Reliability

- `/api/v1/admin/audit-logs` supports owner-equivalent all-store queries when `store_id` is omitted.
- Managers can query only their own store by passing a store id; frontdesk users are denied by the admin route guard and backend capability checks.
- Audit repository queries use explicit JPQL for `store_id` and `created_at`.
- Empty audit-log result sets return an empty page response and the UI shows `No audit logs`.
- Audit writes remain best-effort; audit persistence failures are logged and do not block the business operation.

### Owner Admin Shell

- Owner/admin pages use a shared Owner Admin Shell for consistent left navigation and page framing.
- The shared navigation includes:
  - Home / Dashboard
  - Menu Management
  - Dining Tables
  - Printing Settings
  - Staff Management
  - Audit Logs
  - Reports
- Frontdesk users cannot access owner admin routes.
- Managers see the owner admin shell for their permitted admin areas and remain store-scoped by backend authorization.

### Dev Login Seed Accounts

For local development, `RuntimeDataSeeder` ensures these login accounts exist and are active:

- `owner` / `741xu741` with role `OWNER`
- `manager` / `741xu741` with role `MANAGER`
- `staff` / `741xu741` with role `FRONTDESK`
- `frontdesk` / `741xu741` with role `FRONTDESK`
- `kitchen` / `741xu741` with role `HOT_KITCHEN`

Passwords are stored as BCrypt hashes in `user_credentials.password_hash`; plaintext passwords are never returned by auth or staff APIs. The local seeder resets these default credentials so a developer can always recover access after database changes.

`OwnerPrintingController` has both production and legacy test constructors. The production constructor is marked with Spring `@Autowired` so backend startup can select the full dependency-injected constructor while unit tests can keep using the shorter compatibility constructor.

## Windows Pilot Deployment Package

The Windows pilot package is built from `deployment/windows-pilot/build-windows-pilot.sh`.

Package output:

- `deployment/windows-pilot/package/`
- `deployment/windows-pilot/restaurant-pos-windows-pilot.zip`

The package contains:

- `backend/restaurant-system-backend.jar`
- `frontend/dist/`
- `config/application-pilot.yml`
- `database/restaurant_pos.dump`
- Windows scripts:
  - `start-pos-windows.bat`
  - `stop-pos-windows.bat`
  - `restore-db-windows.bat`
  - `backup-db-windows.bat`
  - `check-pos-windows.bat`
- `README_WINDOWS_PILOT.md`

Pilot backend config uses the `pilot` Spring profile and environment variables for database settings:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`

The Windows pilot frontend is served from static `dist` files by a lightweight Node server included in the package. It keeps frontend API calls relative (`/api`) and proxies `/api` and `/ws` to the local backend on port `8080`.

Pads should access the Windows fixed LAN IP:

```text
http://WINDOWS_FIXED_IP:5173/
```

Reports are still under validation and should not be used for accounting during pilot operations.

## Store Workspace Routing Phase 2

Phase 2 adds frontend store workspace context on top of the Phase 1 backend store-access guard.

Implemented routes now include store-scoped paths such as:

- `/stores/{storeId}/frontdesk`
- `/stores/{storeId}/frontdesk/menu`
- `/stores/{storeId}/frontdesk/order`
- `/stores/{storeId}/admin/dashboard`
- `/stores/{storeId}/admin/settings/printing`
- `/stores/{storeId}/admin/settings/tables`
- `/stores/{storeId}/admin/menu/items`
- `/stores/{storeId}/admin/reports/sales`
- `/stores/{storeId}/admin/staff`
- `/stores/{storeId}/admin/audit`
- `/stores/{storeId}/kds/grab`
- `/stores/{storeId}/kds/hot-kitchen`
- `/stores/{storeId}/kds/noodle`
- `/stores/{storeId}/pickup`

Frontend components use `StoreContextProvider`, `RequireStoreAccess`, and `useCurrentStore()` to load `/api/v1/stores/{storeId}/context` and pass the active store id into menu, order, frontdesk board, KDS, pickup, printing, reports, tables, staff, and audit APIs.

Legacy routes such as `/frontdesk`, `/frontdesk/menu`, `/frontdesk/order`, `/admin/settings/printing`, and `/kds/grab` redirect to the authenticated user's default accessible store using `/api/v1/me/workspaces`.

Login redirects are role-aware:

- `OWNER`, `ADMIN`, `MANAGER` -> `/stores/{storeId}/admin/dashboard`
- `FRONTDESK` -> `/stores/{storeId}/frontdesk`
- `HOT_KITCHEN` -> `/stores/{storeId}/kds/hot-kitchen`
- `NOODLE_VIEW` -> `/stores/{storeId}/kds/noodle`
- `PASS` -> `/stores/{storeId}/pickup`

The URL store id is not trusted as authorization. Backend APIs must continue validating access through the Phase 1 store membership and `StoreAccessService` rules. The frontend store context is a workspace selector and API parameter source, not a security boundary.

### Login Workspace Error Handling

The Phase 2 login flow is:

1. `POST /api/v1/auth/login`
2. Save the returned access/refresh tokens
3. `GET /api/v1/me/workspaces`
4. Redirect to the default accessible store workspace

Frontend API errors are normalized in `frontend/src/services/apiClient.ts`. Login displays separate user-facing messages for:

- bad credentials
- successful login with workspace loading failure
- no store access assigned
- forbidden store access
- expired session
- backend 500/system errors

If `/api/v1/me/workspaces` returns 500 locally after a Phase 1/Phase 2 schema change, restart the backend so Hibernate/JPA and the runtime seeder can create/supplement `organization_memberships` and `store_memberships`.

Phase 2 intentionally did not implement Platform tenant/store management, `/stores/:storeId` SaaS onboarding pages, Display Rules / Print Rules editor, or Phase 4 route cleanup.

## Owner Multi-Store Dashboard Phase 3

Phase 3 adds a lightweight Owner Home for owners, admins, and multi-store managers.

Routes:

- `/owner`
- `/owner/dashboard`
- `/owner/stores`

`/owner/dashboard` is the primary page. It is a multi-store overview and launcher, not a replacement for the single-store workspace. Store-specific work still happens under `/stores/{storeId}/...`.

Login redirect rules:

- `OWNER`, `ADMIN`, `MANAGER` with one accessible store -> `/stores/{storeId}/admin/dashboard`
- `OWNER`, `ADMIN`, `MANAGER` with multiple accessible stores -> `/owner/dashboard`
- `FRONTDESK` -> `/stores/{storeId}/frontdesk`
- `HOT_KITCHEN` -> `/stores/{storeId}/kds/hot-kitchen`
- `NOODLE_VIEW` -> `/stores/{storeId}/kds/noodle`
- `PASS` -> `/stores/{storeId}/pickup`

Owner Home shows each accessible store with:

- store name/code/status and current user's store role
- feature availability for Core POS, Printing, KDS, Admin, and Analytics
- today order count
- today completed sales
- active order count
- occupied/open table count
- failed print job count
- printing mode
- last print failure time
- KDS active count only when KDS is enabled

Backend API:

- `GET /api/v1/owner/overview`

Security rules:

- The backend uses `StoreAccessService.accessibleStores(...)` to choose stores before calculating summary data.
- `FRONTDESK`, `HOT_KITCHEN`, `NOODLE_VIEW`, and `PASS` cannot access the owner overview API or `/owner/dashboard`.
- URL store ids are still not trusted. Store-scoped business APIs must continue checking backend store access.
- Dev Role Switcher follows the same redirect rules and does not bypass store access.

StoreSwitcher vs Owner Home:

- `StoreSwitcher` lives inside a single-store workspace and switches the active store while keeping the same module path where possible.
- `Owner Home` is the multi-store landing page and launcher before entering a store workspace.

Printer Restaurant Mode behavior:

- Owner Home reads print summary only. It does not dispatch print jobs.
- If KDS is disabled, Owner Home does not call KDS live endpoints, mount KDS hooks, or create KDS polling/WebSocket subscriptions.

Phase 3 still does not implement Platform Admin, SaaS billing/subscription, Display Rules / Print Rules, or organization-level settings UI.

## Cloud Ready PR8: Cloud Deployment Architecture Package

PR8 adds a template-only cloud deployment package under `deployment/cloud/`. It does not connect to any server, deploy any image, change runtime business behavior, or introduce production secrets.

Package contents:

- `README_CLOUD_DEPLOYMENT.md`: cloud architecture, environment setup, safety guards, printing boundary, and smoke test checklist.
- `README_ROLLBACK.md`: application rollback, database restore, Flyway rollback cautions, and printing stabilization notes.
- `.env.example`: blank deployment placeholders only; the filled `.env` must stay outside git.
- `docker-compose.yml`: backend, frontend/Nginx, and optional local PostgreSQL profile for rehearsal.
- `nginx.conf.example`: static frontend serving plus `/api` and `/ws` reverse proxy examples.
- `application-cloud.yml.example`: documentation-only environment mapping for the cloud profile.
- `deploy.sh`, `backup-db.sh`, `restore-db.sh`, `health-check.sh`: local templates for compose validation/start, database backup/restore, and reachability checks.

Important boundaries:

- Windows pilot deployment remains separate under `deployment/windows-pilot/`.
- Cloud deployment should use `spring.profiles.active=cloud`, Flyway, production safety guards, and RuntimeDataSeeder cloud-safe settings.
- Cloud deployment must not rely on development default accounts; the production owner/bootstrap runbook remains future work.
- Cloud servers must not directly connect to private LAN printers. Use `MOCK`, `DISABLED`, `PAD_DIRECT`, or a local print bridge for real store printing.
- `HOT_KITCHEN` remains a printing module, but physical printer transport follows the same cloud printing boundary.

PR8 intentionally does not modify backend business code, frontend app code, database migrations, Android code, payment/refund behavior, `completeOrder`, or printing route semantics.

## Cloud Ready PR9-10: Production Bootstrap Runbook And Final Smoke Checklist

PR9-10 adds documentation-only cloud launch preparation under `deployment/cloud/`.

New documents:

- `README_PRODUCTION_BOOTSTRAP.md`: safe manual runbook for first cloud organization, store, owner, memberships, menu/table/printer setup, and rollback notes.
- `FINAL_SMOKE_TEST_CHECKLIST.md`: copy-paste checklist covering pre-deploy, cloud environment, backend startup, frontend, bootstrap, POS, printing, KDS-disabled mode, admin pages, backup/restore, long-running pilot, no-go conditions, accepted pilot limitations, and monitoring.
- `bootstrap-template.sql.example`: placeholder-only SQL skeleton for reviewed bootstrap rehearsal. It is not a production-ready script and must not contain plaintext passwords or real hashes.

Important boundaries:

- PR9-10 does not implement a production bootstrap CLI/API.
- PR9-10 does not connect to a server, deploy anything, or write secrets.
- Cloud bootstrap must not depend on local/demo default users or RuntimeDataSeeder demo data.
- Owner credentials must be generated securely, stored as BCrypt hashes, and handed to the owner through an out-of-band secure process.
- Menu, table, and printer setup should use reviewed import data or Owner Admin, not cloud demo seed.
- Cloud print mode should start with `MOCK`, `DISABLED`, `PAD_DIRECT`, or a local bridge; cloud backend direct LAN printer transport remains blocked.

PR9-10 intentionally does not modify backend runtime code, frontend runtime code, database migrations, Android code, payment/refund behavior, `completeOrder`, order lifecycle, printing routing, or Pad Direct worker behavior.

## PR11A-Hotfix: Android Local Ordering UX Fixes

PR11A-Hotfix is a focused Android local preview ordering/display cleanup. It does not change order lifecycle, pricing, payment/refund behavior, database schema, Pad Direct, Android native printing, or print dispatch semantics.

Frontend ordering behavior:

- Menu item cards show a touch-friendly stepper (`- 2 +`) when the current order already contains that `menu_item_id`; cards with zero quantity keep the standalone circular `+` add button.
- The stepper quantity totals all current active order rows with the same menu item id, including rows with different options, notes, or combo selections.
- Menu card `+` preserves existing behavior: quick-add items add directly, while items requiring customization open the customization modal.
- Menu card `-` only targets mutable rows. In edit-order mode, previously submitted/locked rows are not modified; if multiple mutable rows share the same menu item id, the latest row is decremented first.
- Drink-style items with stable metadata such as `categoryCode=DRINK`, `categoryCode=ALCOHOL`, `categoryCode=MILK_TEA`, `itemType=drink`, or `itemType=beverage` quick-add directly when they do not have required customization.
- Fried items keep the existing no-customization quick-add behavior.
- Noodles, combo-capable items, and any item with required customization still open the customization modal.
- Ordering layout avoids a full-page hard fixed shell. The category list, menu list, and order panel use reasonable max heights so menu items and order rows scroll independently without compressing the order panel to a single row.
- Ordering layout reserves bottom safe-area padding so Android WebView navigation controls do not cover the order summary footer or submit/update buttons.

Printing display behavior:

- Split table storage remains unchanged (`T1-A`, `T1-B`, etc.).
- Receipt/ticket display maps split suffixes for human-readable output:
  - `-A` -> `-左`
  - `-B` -> `-右`
- `GRAB`, `FRONTDESK_RECEIPT`, and `HOT_KITCHEN` renderers share the same backend display formatter for table labels.
- Pickup/takeout labels are not transformed by the split-table formatter.

## PR11B: Android App Web Shortcut Control Panel

PR11B adds a lightweight Android shell Local Control Panel. It is a Web shortcut
and local debugging panel only. It does not implement Pad Direct, native
ESC/POS printing, native Print Center, native Menu Management, payment/refund
behavior, `completeOrder`, backend APIs, frontend POS runtime logic, database
migrations, or cloud deployment changes.

Control Panel behavior:

- Long press inside the Android Pad App opens the Local Control Panel.
- The panel preserves Local Preview Web App URL and Bundled Assets API Base URL
  configuration.
- The panel displays current WebView URL, configured Web App URL, and current
  mode (`Local Preview Mode` or `Bundled Assets Mode`).
- The panel provides shortcuts for Frontdesk, Order Center, Print Center, Menu
  Management, Dining Tables, page refresh, and Web App URL reachability testing.
- `Save Settings / 保存配置` and the dialog `Save` action both persist Local
  Preview Web App URL, Bundled Assets API Base URL, and local printer-test
  IP/port/timeout to Android `SharedPreferences`.
- `Test Printer Connection` and `Test Print` persist only the local printer-test
  IP/port/timeout; they do not save the Web App URL.
- If the current URL contains `/stores/{storeId}/`, shortcuts use
  store-scoped routes. If no store id is available, shortcuts use legacy routes
  such as `/frontdesk` or `/admin/settings/printing`; the Web frontend handles
  redirecting those routes through normal auth/store access checks.
- Android native code does not read WebView localStorage, reuse bearer tokens,
  or call backend printing/menu/order APIs directly.
- `Test Web App URL` only checks the configured Web App URL reachability and
  does not test backend business APIs.

## PR11D-1: Android Native TCP Printer Connection Test POC

PR11D-1 makes the existing Android native TCP printer bridge usable from the
Local Control Panel for real hardware testing. It does not implement
`PAD_DIRECT` worker behavior, device registration, pending job polling, claim,
payload fetch, complete/fail/release, backend business API calls, Android
background printing, payment/refund behavior, `completeOrder` changes, database
migrations, or frontend POS runtime changes.

Local Control Panel printer test behavior:

- The panel stores local test-only settings in Android `SharedPreferences`:
  `printer_test_ip`, `printer_test_port`, and `printer_test_timeout_ms`.
- `Test Printer Connection` opens a TCP socket from the Android Pad to the
  configured printer endpoint and reports a clear success/failure message.
- `Test Print` builds a fixed local ESC/POS test ticket in Android and sends it
  through the native raw TCP bridge.
- The fixed test ticket includes English text, Chinese text, printer endpoint,
  current timestamp, line feeds, and a cut command.
- The test payload is built locally and encoded primarily with GBK for Chinese
  printer compatibility testing.
- The feature does not store device tokens, backend secrets, or WebView bearer
  tokens and does not upload printer IP to the backend.

Operational notes:

- Android Pad and printer must be on the same LAN.
- Default ESC/POS raw TCP port is `9100`.
- Timeout defaults to `3000ms` and is configurable in the panel.
- Common failures include timeout, connection refused, unreachable host,
  write failure, Chinese encoding mismatch, and unsupported cut command.
- This POC is the recommended hardware step before connecting Android to
  backend `PAD_DIRECT` device registration and print job claim APIs.

## PR11D-2: Pad Device Pairing Via Web Print Center And Android Bridge

PR11D-2 adds Pad Direct device pairing only. It does not implement pending jobs,
claim, payload fetch, native order printing, complete/fail/release, lease
renewal, retry, or an Android background worker.

Pairing flow:

- The operator opens Web Print Center inside the Android Pad App.
- The Pad Direct devices section shows `配对本机 Pad` only when the Android
  `window.RestaurantPadDevice` bridge is available.
- A normal browser cannot save Pad credentials and shows a disabled pairing
  prompt instead.
- The Web page calls the existing backend device registration API with the
  logged-in Web bearer token and current store id.
- The backend returns the raw `device_token` only in the registration response.
- The Web page immediately calls
  `window.RestaurantPadDevice.saveDeviceCredentials(...)` and does not persist
  the raw token in browser storage.
- Android saves `device_id`, `device_token`, `store_id`, device name,
  registration time, app version, and platform in local native preferences.
- Android Local Control Panel shows paired/unpaired status, device id, store id,
  device name, registration time, and token last four characters only.
- `Clear Pairing / 清除配对` requires confirmation and removes the local device
  credentials.

Security and scope:

- Android native code does not read WebView `localStorage`, does not reuse Web
  bearer tokens, and does not directly call bearer-token backend business APIs.
- `SharedPreferences` storage is acceptable for this local pilot pairing step.
  Before production Pad Direct worker rollout, migrate `device_token` storage to
  `EncryptedSharedPreferences` or Android Keystore-backed storage.
- Store device registration/listing accepts store-scoped
  `admin:printing_manage` or the legacy broader `admin:store_config`, so current
  `FRONTDESK` staff can pair this store's Pad without receiving
  `admin:store_config`, Staff Management, Audit Logs, or Platform Admin access.
- Backend `StoreAccessService` still enforces store scope. A URL store id or
  Web UI visibility is not treated as authorization.
- The next recommended Pad Direct step is a pending jobs viewer, not an
  automatic print worker.

## PR11D-3: Android PAD_DIRECT Pending Jobs Viewer

PR11D-3 adds a read-only Pending Print Jobs area to the Android Local Control
Panel. It does not implement claim, payload fetch, native order printing,
complete/fail/release, lease renewal, retry, automatic polling, WebSocket
subscriptions, or a background worker.

Viewer behavior:

- If the Android Pad is not paired, the panel shows `请先配对本机 Pad` and the
  pending jobs refresh button is disabled.
- If paired, the panel shows saved device/store status and allows manual
  `Refresh Pending Print Jobs / 刷新待打印任务`.
- The Android native request uses device authentication headers:
  `X-Device-Id` and `X-Device-Token`.
- The Android shell does not use the Web bearer token and does not read WebView
  `localStorage`.
- Local Preview mode calls the configured Web App origin, for example
  `http://{developer-lan-ip}:5173/api/v1/stores/{storeId}/printing/jobs/pending?limit=25`,
  relying on Vite preview to proxy `/api` to backend `localhost:8080`.
- Bundled Assets mode falls back to the configured API Base URL origin.
- The list displays job id, order id, module code, status, created time, printer
  endpoint, claimed device/lease when present, and operator/error message when
  present.

Operational notes:

- Store printing mode must be `PAD_DIRECT` for submitted orders to become
  pending Pad jobs.
- No paper should print from this viewer.
- `401/403` is shown as device authentication failure and should be fixed by
  re-pairing the Pad.
- Network failures are shown as Web App URL / Wi-Fi / `preview:lan` / backend
  troubleshooting prompts.
- PR11D-4 builds on this viewer with a manual claim + print happy path while
  still avoiding a long-running automatic worker.

## PR11D-4: Android PAD_DIRECT Manual Claim And Native Print Happy Path

PR11D-4 added the first manual one-job `领取并打印` action to the Android Local
Control Panel pending jobs list. The original PR11D-4 scope did not include an
automatic worker, automatic polling, batch claim, lease renewal, release,
retry/backoff, explicit `PRINTING` state changes, database migrations, order
lifecycle changes, payment/refund changes, or `completeOrder` changes. PR11D-5
/ PR11D-6 / PR11D-7 later upgraded the runtime flow with `start-print`,
`PRINTING`, and foreground semi-auto mode.

Manual flow:

- The operator manually refreshes pending jobs.
- The operator taps one job's `领取并打印` button.
- Android generates a `client_attempt_token`.
- Android claims the job with `X-Device-Id` and `X-Device-Token`.
- Android marks the job `PRINTING` through `start-print`.
- Android fetches the ESC/POS payload for the claimed/printing job.
- Android decodes `escpos_payload_base64` and sends it to the configured local
  LAN printer with the native TCP bridge.
- On native print success, Android calls the backend complete API.
- If payload fetch or native TCP print fails after claim, Android calls the
  backend fail API so Print Center shows the job as `FAILED`.

Operational boundaries:

- The backend still does not open a printer socket for `PAD_DIRECT`.
- The Android shell does not use the Web bearer token and does not read WebView
  `localStorage`.
- The manual button processes only one job at a time and disables itself while
  work is in progress.
- A `409` claim response is shown as `任务已被其他 Pad 领取`.
- `401/403` is shown as device authentication failure and should be fixed by
  re-pairing the Pad.
- Printer IP/port/timeout come from the Android Local Control Panel's local
  printer settings.

Original PR11D-4 pilot limitations:

- If a claim lease expires while a device is still printing, another Pad could
  reclaim the job. PR11D-5/6/7 now reduces this risk with `PRINTING` and
  foreground semi-auto mode, but encrypted storage, force release, and
  long-running worker hardening are still future work.
- Device token storage still uses Android `SharedPreferences` for local pilot
  testing and must move to encrypted storage before production worker rollout.

## PR11D-5/6/7: PAD_DIRECT Safe Semi-Auto Printing

PR11D-5/6/7 hardens the Android Pad Direct pilot from manual printing toward a
safe foreground semi-auto workflow. It does not add an Android background
service, boot receiver, WebSocket worker, database migration, order lifecycle
change, payment/refund behavior, `completeOrder` change, menu/order business
logic change, cloud deployment change, or REAL-mode server-side printing change.

Backend duplicate-print hardening:

- PAD Direct now supports `POST /api/v1/printing/jobs/{jobId}/start-print`.
- `start-print` is device-authenticated with `X-Device-Id` and
  `X-Device-Token`.
- Only the device that owns the claim and matching `client_attempt_token` can
  start printing.
- `start-print` moves the job from `CLAIMED` to `PRINTING`, extends
  `claim_expires_at`, and records the active attempt.
- Payload fetch, complete, and fail accept the claimed device from either
  `CLAIMED` or `PRINTING`, preserving compatibility with the earlier manual
  client.
- Ordinary claim can reclaim `PENDING` or expired `CLAIMED` jobs only; active
  or expired `PRINTING` jobs are not automatically stolen by another Pad.

Android pilot behavior:

- Manual `领取并打印` now runs:
  `claim -> start-print -> payload -> assigned printer TCP print -> complete/fail`.
- Local Control Panel includes explicit `Start Auto Print / 开启自动处理打印任务`
  and `Stop Auto Print / 停止自动处理` controls.
- A paired Pad auto-starts one foreground/headless semi-auto worker when the App
  opens or returns to the foreground. Opening the Control Panel attaches visible
  controls to the same worker instead of creating a second polling loop.
- When the Control Panel opens on a paired Pad, it also starts the same worker if
  it is not already running. The start path is guarded by the existing
  single-worker flag, so reopening the panel or refreshing the WebView does not
  create duplicate polling loops.
- If auto-start cannot run, Android logs the error and shows a top-panel warning.
- Semi-auto mode is foreground-only and operator controlled through the panel.
- It polls pending jobs at a small interval only while enabled, processes one
  job at a time, and uses the same safe flow as manual printing.
- It stops on device auth, backend, payload, or printer failures instead of
  retrying forever.
- It stops when the Android app leaves the foreground and resumes the same
  worker when the app returns, as long as it was running before the lifecycle
  stop. Manual Stop still disables auto processing until started again.
- `PENDING / Waiting Pad / Attempt 0` means no Pad has successfully claimed the
  job. In that state the backend is not stuck in processing; the Android worker
  has not consumed the queue yet.
- Android logcat emits `Worker Started`, `Worker Poll Queue`, `Job Picked`,
  `Job Processing`, `Job Finished`, `Job Failed`, `Worker Stopped`, and
  `Worker Exception` markers for PAD_DIRECT troubleshooting.
- Backend logs emit PAD_DIRECT pending/claim/start/payload/complete/fail events
  when the Android worker reaches the server APIs.

Print Center visibility:

- `PRINTING` status is visible in recent/attention print jobs.
- Claim owner device and claim expiry are shown for Pad Direct jobs.
- Expired `CLAIMED` jobs warn that another Pad may reclaim them.
- Expired `PRINTING` jobs warn operators to confirm whether paper already
  printed before reprinting, because blind reprint may duplicate tickets.

Pilot limitations still remaining:

- Device credentials are still stored in Android `SharedPreferences`; production
  should migrate to `EncryptedSharedPreferences` or Android Keystore-backed
  storage.
- No Android long-running foreground service or background daemon.
- No lease renewal loop during very long physical prints.
- No force-release / mark-failed operator tooling.
- No device-to-printer or device-to-module affinity.
- If physical printing succeeds but complete fails, staff must reconcile in
  Print Center before reprinting.

## PR11D-8: PAD_DIRECT Multi-Printer Routing

PR11D-8 changes PAD_DIRECT execution from a single Android-local default printer
to backend-assigned printer routing. It does not add database migrations,
device-printer affinity, Android background services, force release tooling,
encrypted credential storage, order lifecycle changes, payment/refund changes,
`completeOrder` changes, menu/order business logic changes, PrintDispatcher
routing changes, or REAL-mode server-side printing changes.

Routing model:

- Print Center remains the source of truth for module-to-printer assignment.
- `print_jobs.printer_id` is reused as the assigned printer reference.
- No new table or column is added.
- PAD_DIRECT payload now returns assigned printer fields:
  `printer_id`, `printer_name`, `printer_host`, `printer_port`,
  `printer_endpoint`, `paper_width_mm`, `text_encoding`, `escpos_code_page`,
  and `timeout_ms`.
- Android manual and foreground semi-auto flows print to
  `printer_host:printer_port` from the payload.
- The Local Control Panel printer IP/port fields are only for local printer
  connection tests and fixed test tickets, not real PAD_DIRECT order routing.
- PAD_DIRECT ESC/POS payload generation uses the effective module font size:
  `printer_assignments.font_size` first, then `printer_configs.font_size`, then
  `MEDIUM`. This keeps Web Print Center assignment changes and Android/iPad
  local printing behavior consistent.

Operational behavior:

- Any paired Pad for the store may claim any module job: `GRAB`,
  `FRONTDESK_RECEIPT`, or `HOT_KITCHEN`.
- Backend atomic claim and `PRINTING` state still prevent duplicate claim/print.
- If payload is missing assigned printer details, Android fails the job with
  `ANDROID_ASSIGNED_PRINTER_MISSING`.
- If the Pad cannot reach the assigned printer, Android fails the job with
  `ANDROID_ASSIGNED_PRINTER_UNREACHABLE` and stops the worker.
- Print Center job tables show printer id, name, and endpoint for verification.
- PR11D-12 adds structured native printer diagnostics for assigned printer
  failures. Android records `native_error_code`, `phase`, `bytes_written`,
  exception class/message, endpoint, job/module, printer id/name, and device id
  without logging ESC/POS payloads or device tokens.
- Android now maps assigned-printer failures to more specific error codes:
  `ANDROID_PRINTER_CONNECT_TIMEOUT`,
  `ANDROID_PRINTER_CONNECTION_REFUSED`,
  `ANDROID_PRINTER_NETWORK_UNREACHABLE`,
  `ANDROID_PRINTER_WRITE_FAILED`,
  `ANDROID_PRINTER_FLUSH_FAILED`, or fallback
  `ANDROID_NATIVE_PRINT_FAILED`.
- Safe short retry is limited to connect-phase failures where
  `phase=CONNECT`, `bytes_written=0`, and the native code is `TIMEOUT`,
  `CONNECTION_REFUSED`, or `UNREACHABLE`. Android retries the same job/device/
  endpoint after 500ms and 1500ms, then reports `FAILED` and stops the worker.
- `WRITE` and `FLUSH` failures are never retried automatically because the
  printer may have already received part or all of the ticket. Staff must check
  physical paper output before reprinting.
- `retry_count` remains a failure counter/display value only. It does not
  requeue failed jobs and the worker does not consume `FAILED` jobs.

Pilot limitations:

- Printer endpoints are resolved from current `printer_configs` through
  `print_jobs.printer_id`; there is no printer endpoint snapshot column.
- If a printer IP is changed after a job is already pending, payload uses the
  latest config for that printer id.
- Each Pad must be on a LAN/VLAN that can reach all printers it may claim.
- Device-printer/module affinity is intentionally not implemented in this PR.

## PR11D-9: PAD_DIRECT Immediate Print Trigger

PR11D-9 adds a frontend-to-Android quick trigger for current-Pad order
submissions. It does not add WebSocket printing, Android background services,
backend state-machine changes, order lifecycle changes, payment/refund changes,
or `completeOrder` changes.

Behavior:

- When an order submit or edit-order update succeeds inside the Android Pad
  WebView, the web frontend calls `window.RestaurantPadDevice.kickPrintWorker`.
- If the Pad Direct semi-auto worker is not enabled, the kick is ignored and no
  automatic printing starts.
- If the worker is busy, Android records a pending kick and starts a quick check
  as soon as the current job finishes.
- If the worker is idle, Android cancels the normal idle tick, polls after
  roughly 300 ms, retries once about 700 ms later if no job is visible, then
  returns to normal adaptive polling.
- Normal polling remains required for jobs submitted by other Pads, browser
  clients, reprint actions, and recovery after missed triggers.

This quick window exists because order submission may commit before the
after-commit asynchronous print dispatch has created PAD_DIRECT `print_jobs`.

## PR11D-13: PAD_DIRECT Android Worker Lost Tick Hardening

PR11D-13 hardens the Android foreground/headless PAD_DIRECT worker against the
long-run case where Print Center shows `PENDING / Waiting Pad / Attempt 0`
because no Pad is consuming the queue. It does not change PAD_DIRECT
claim/start-print/payload/complete/fail semantics, pending query rules,
PrintDispatcher routing, order lifecycle, payment/refund behavior,
`completeOrder`, database schema, automatic reprint, or failed-job requeue.

Android worker reliability changes:

- The worker now tracks explicit state: stopped, starting, waiting, polling,
  processing job, stopping, and error-stopped.
- The Local Control Panel shows whether auto processing is enabled, whether the
  worker is actually running, the current device/store, last poll time, last poll
  result count, whether the next poll is scheduled, watchdog status, current
  job/module/printer, last start reason, last stop reason, and last error.
- Manual Stop persists `pad_direct_auto_enabled=false`; after that, app start or
  foreground resume will not secretly restart automatic printing.
- Pairing or manual Start enables auto processing and starts the single worker.
- The worker records `pollScheduled`, `lastPollAt`, and `lastPollFinishedAt` so
  operators can distinguish “no jobs” from “worker stopped polling”.
- A lightweight watchdog checks foreground workers. If auto processing is
  enabled, the app is foreground, no job is in progress, and the worker has not
  polled for more than roughly 10 seconds, Android logs
  `Worker Watchdog Rescheduled` and schedules a fresh poll.
- App pause/stop/destroy cancels future polling. If auto processing was enabled,
  the app foregrounds again, and the previous stop was lifecycle-related, the
  worker safely resumes. Native printing that is already in progress is not
  force-interrupted, but the worker will not pull new jobs while backgrounded.
- Error stops remain visible and require a manual restart from the Control
  Panel, avoiding hidden restart loops after auth/backend/printer failures.

Logging changes:

- Android PAD_DIRECT worker logs use the `RestaurantPadWorker` tag.
- Logs include `Worker Started`, `Worker Stopped`, `Worker Poll Scheduled`,
  `Worker Poll Started`, `Worker Poll Result`, `Worker Picked`,
  `Worker Job Processing`, `Worker Job Finished`, `Worker Job Failed`,
  `Worker Exception`, `Worker Watchdog Rescheduled`, and lifecycle actions.
- Android logs do not print device tokens or ESC/POS payloads.
- Backend pending polling logs now keep returned job counts greater than zero at
  INFO while returned zero jobs is DEBUG, reducing idle log noise while keeping
  actionable queue-consumption evidence.

## PR11D-14: PAD_DIRECT Restaurant Pilot Preventive Hardening

PR11D-14 is a preventive hardening pass for restaurant PAD_DIRECT pilot testing.
It does not change order lifecycle, payment/refund behavior, `completeOrder`,
menu/order business rules, PrintDispatcher routing, PAD_DIRECT claim/start/
payload/complete/fail semantics, failed-job requeue behavior, automatic reprint,
Android background daemon behavior, or device-printer affinity.

Android Local Control Panel visibility:

- The worker status panel shows auto processing enabled/disabled, worker state,
  app foreground, device/store id, last poll time, last poll result count, last
  poll duration, oldest pending job age, last queue delay, last job processing
  duration, consecutive errors, scheduled poll/watchdog state, current job/
  module/printer endpoint, last start reason, last stop reason, and last error.
- If auto processing is disabled, the panel explicitly says no `PENDING` jobs
  will be consumed.
- If the worker is `ERROR_STOPPED`, the panel tells the operator to check the
  displayed reason and restart manually.
- If the worker appears stale for more than roughly 10 seconds while idle, the
  watchdog reschedules a poll and the panel warns the operator.
- While auto processing is enabled, the app is foregrounded, and the worker is
  running, Android keeps the screen awake. The flag is cleared when auto
  processing stops or the app backgrounds. PR11D-14 hotfix ensures this window
  flag update is always marshalled to the Android main thread so a background
  print worker cannot fail a job with `CalledFromWrongThreadException`.

Metrics and logs:

- Android `RestaurantPadWorker` logs include both the existing human-readable
  markers and stable markers such as `worker_started`, `worker_stopped`,
  `poll_start`, `poll_end`, `job_picked`, `claim_duration_ms`,
  `start_print_duration_ms`, `payload_duration_ms`, `tcp_print_duration_ms`,
  `complete_duration_ms`, `fail_duration_ms`, and `job_finished`.
- Android logs do not print device tokens or ESC/POS payloads.
- Backend PAD_DIRECT pending polling logs returned-zero jobs at DEBUG and
  returned-positive jobs at INFO with `oldestJobAgeMs`.
- Backend claim success logs `queueDelayMs`.

Print Center visibility:

- Print job rows show job age, queue delay, total time, claim time, print/fail
  time, claimed device, printed device, printer endpoint, and retry count.
- PAD_DIRECT `PENDING` jobs older than 30 seconds show a warning that they are
  waiting for Pad processing.
- PAD_DIRECT `PENDING` jobs older than 2 minutes show danger messaging that the
  Pad may not be running or auto processing may have stopped.
- `PRINTING` stale and `FAILED` jobs remain visible and require manual operator
  review before reprint.

Database/index note:

- PR11D-14 does not add a schema migration. PR11D-15 should evaluate a low-risk
  index for the PAD_DIRECT pending query, such as
  `(store_id, execution_mode, status, created_at, id)` plus a claim-expiry helper
  if real pilot `EXPLAIN ANALYZE` data shows the pending poll becomes slow.

Restaurant pilot checklist:

- See `restaurant-pad-app/docs/PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md` for
  before-arrival checks, 20-order field test steps, failure-state interpretation,
  and PR11D-15 index follow-up.

## PR11E-A: PAD_DIRECT Worker Recovery And Frontdesk Print Health

PR11E-A hardens the Android PAD_DIRECT foreground worker against temporary
network/API interruptions and exposes worker health directly in frontdesk
ordering pages. It does not add Android background daemon behavior, automatic
FAILED-job requeue, automatic reprint, database migrations, order lifecycle
changes, payment/refund behavior, `completeOrder` changes, or any bypass of the
`PRINTING` duplicate-print guard.

Android worker recovery policy:

- `pad_direct_auto_enabled` is now treated as a user preference only. It is set
  to true by pairing/manual Start and set to false only by manual Stop or clear
  pairing.
- Temporary pending-poll/backend/API failures enter `RECOVERING` instead of
  permanently stopping the worker. Auto processing remains enabled.
- Recovery backoff is 2s, 5s, 10s, then 30s. When the backend/network recovers,
  the worker resumes normal polling without requiring the operator to reopen
  the Control Panel.
- `RECOVERING` is visible in Android Control Panel and in the frontdesk print
  health banner. The watchdog does not mistake intentional recovery backoff for
  a lost poll tick.
- High-risk states still stop the worker for operator review: device auth
  401/403, TCP write/flush failures, successful local TCP print followed by
  complete API failure, failed failure-reporting, manual Stop, and other states
  where automatic continuation could duplicate or hide a print.
- Printer connect failures still use the safe short connect retry behavior from
  PR11D-12. If still unreachable, the current job is failed and the worker stops
  so the operator can inspect the assigned printer.

Android bridge additions:

- `RestaurantPadDevice.getPrintWorkerStatus()` returns auto-enabled, worker
  state, recovering/error-stopped flags, last poll time, last error, stop reason,
  device/store id, current job/module/printer endpoint, and recovery backoff
  metadata.
- `RestaurantPadDevice.kickPrintWorker(...)` can now wake an idle worker,
  queue a pending kick while busy, immediately retry a `RECOVERING` worker, or
  restart an `ERROR_STOPPED` worker only when the caller explicitly passes
  operator-confirmed recovery metadata.
- If auto processing is disabled by the user, kick requests are ignored and do
  not secretly restart automatic printing.

Frontdesk visibility:

- `/frontdesk` table board and Android landscape ordering pages show a print
  health banner.
- In Android Pad App, the banner shows whether automatic printing is running,
  recovering, stopped, or disabled, plus the latest worker/device/store status.
- In a normal desktop/iPad browser without the Android bridge, the banner says
  that print executor status is only visible inside Android Pad App.
- The `检查打印 / 唤醒打印` button calls the Android bridge. It wakes idle
  workers, triggers a recovery poll, queues a pending kick if a job is already
  running, and asks for confirmation before recovering an error-stopped worker.

## PR11D-14G: Ordering Combo Option Ordering

PR11D-14G is a frontend-only ordering UI polish for Android Pad WebView and
desktop browser ordering. It does not change backend menu models, option ids,
option codes, option groups, parent option relationships, price calculation,
order submit payloads, kitchen task generation, HOT_KITCHEN routing, printing
routing, payment/refund behavior, or `completeOrder`.

Behavior:

- For noodle menu items, the customization modal renders the combo / 套餐
  section at the top of the option area before size, soup base, noodle type,
  and spicy level.
- Noodle detection uses stable category codes such as `SOUP_NOODLE`,
  `DRY_NOODLE`, `FRIED_NOODLE`, `NOODLE`, and `NOODLES`, with a structural
  legacy fallback to existing noodle customization groups when old local data
  lacks category codes.
- Combo detection uses the existing normalized `customization.combo` data that
  is built from stable combo option semantics. It does not rely on scattered
  Chinese or English display-name checks in the modal.
- Non-noodle items keep their previous option display order, and drink/fried
  quick-add behavior is unchanged.

## PR11C: Frontdesk User Menu And Staff Store Tools Access

PR11C adds a Web frontdesk user menu for Android Pad and browser workflows. It
does not add Android native pages, Pad Direct, native ESC/POS printing,
payment/refund behavior, `completeOrder` changes, order lifecycle changes,
database migrations, or print routing changes.

Frontdesk user menu behavior:

- The round chef icon in the frontdesk top-left area is a touch-friendly user
  menu button.
- The menu shows the current user, current role, Printing, Menu Management, and
  Logout.
- Printing opens the current store workspace route:
  `/stores/{storeId}/admin/settings/printing` when a store context exists, or
  legacy `/admin/settings/printing` otherwise.
- Menu Management opens `/stores/{storeId}/admin/menu/items` when a store
  context exists, or legacy `/admin/menu/items` otherwise.
- Logout uses the normal Web auth logout flow, clears tokens/auth state, and
  returns to `/login`.

Authorization policy:

- This restaurant configuration intentionally allows `FRONTDESK` staff to
  access store-scoped Print Center and Menu Management.
- `FRONTDESK` receives `admin:menu_manage` and `admin:printing_manage`.
- `FRONTDESK` does not receive `admin:store_config`,
  `admin:user_role_manage`, or `admin:history_limit`.
- Staff Management, Audit Logs, Platform Admin, Owner Home, and other owner
  admin routes remain blocked for `FRONTDESK`.
- Store scope remains enforced by backend `StoreAccessService`; URL store ids
  are not trusted as authorization.

Owner Admin shell behavior:

- When `FRONTDESK` enters allowed admin tools, the shell only exposes Menu
  Management, Printing Settings, and Sign out.
- Owner, Admin, and Manager navigation remains unchanged.

PR11C hotfix notes:

- The frontdesk user menu and logout confirmation render in a fixed portal layer
  so table cards and table board stacking contexts cannot cover them.
- Logout requires explicit confirmation with touch-friendly Cancel and Logout
  actions.
- `FRONTDESK` Store Tools uses a lightweight top toolbar instead of the full
  owner sidebar to keep Printing Settings and Menu Management usable on Android
  Pad landscape screens.
- Store Tools exposes a `返回前台` shortcut back to
  `/stores/{storeId}/frontdesk` when a store context exists, or `/frontdesk`
  otherwise.
- Printing Settings falls back to current store context when `FRONTDESK` cannot
  load platform overview data; this does not grant Platform Admin, Staff, or
  Audit access.
