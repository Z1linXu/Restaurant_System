# Restaurant System API (MVP)

> Phase A0.1 implementation (2026-08-13): Owner review rejected free-form Size
> editing and selected system-controlled Small/Regular/Large plus Store-level
> Size/Combo pricing policy. `store_pricing_policies` is the new application
> canonical read/write source for Size/Combo deltas. `menu_item_options`
> remains the per-item Size enablement/identity and ordinary option source.
> Size/Combo `menu_item_options.price_delta` is a rollback compatibility mirror
> only.

> Phase A0.2 implementation (2026-08-13): Store-level Combo contents are
> configured through `store_combo_components` and the dedicated Store Combo
> Configuration API. Combo price still comes from
> `store_pricing_policies.combo_delta`; item Combo allowed still comes from the
> existing item-scoped `COMBO` option row. Exact-SHA Staging deployment
> `90ac0cb0496161b12c47cff00573b56b4abc961c` applied Flyway V12 and passed
> automated A0.2 validation; Production remained no-mutation.

> Phase A1 Module Catalog (2026-08-13): the canonical product module catalog is
> `backend/src/main/resources/module/module-catalog.v1.json`, with technical
> evidence in
> [PHASE_A1_MODULE_CATALOG](../docs/governance/agile/PHASE_A1_MODULE_CATALOG.md).
> A1 is a contract/static-validation package only; no public API shape changes.
> Current `/api/v1/me/workspaces` and `/api/v1/stores/{storeId}/context`
> continue to expose workspace context but not final Store module state. A3 owns
> the Store-level module read contract.

> Phase A2 Module Dependency Graph (2026-08-13): the application-readable
> dependency graph lives at
> `backend/src/main/resources/module/module-dependency-graph.v1.json`.
> `ModuleDependencyValidator` validates module choices against required modules,
> conflicts, environment capabilities and hardware capabilities. A2 is an
> internal contract/validator package only; it adds no endpoint, DTO, header,
> Store module persistence or runtime gating behavior. A3 owns the Store-level
> module read contract.

> Phase A3 Store-level Module Configuration (2026-08-13): additive Flyway V13
> introduces Store-scoped `store_modules` as the canonical Store module state
> source. `/api/v1/stores/{storeId}/context` now includes
> `module_configuration`; `/api/v1/stores/{storeId}/modules` exposes the same
> canonical read contract; `/api/v1/admin/stores/{storeId}/modules` is a
> bounded admin configuration contract. A3 separates environment capability,
> Store module state, runtime mode and user authorization, while retaining
> legacy runtime gating until A6/A7.

> Final productization Phase A0 boundary (2026-08-13): Size configuration uses
> the existing menu option/modifier APIs rather than a second size engine.
> `MenuItem -> SizeVariant[1..N]` is represented by `menu_item_options` rows
> with `option_group=SIZE`, `option_type=size`, stable `option_code`, bilingual
> names, `sort_order`, `is_active`, and `price_delta`. Default Size is derived
> as the first active Size by catalog order. No public DTO shape or Flyway
> migration is introduced by A0.

> Production three-reliability promotion boundary (2026-08-12): exact
> application SHA `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9` is deployed to
> Production. No public endpoint or DTO changed in this promotion. PAD_DIRECT,
> menu catalog, offline snapshot, PrintJob/outbox and Print Center API
> contracts remain as documented below. Production retained its own
> `PAD_DIRECT` configuration; Staging `MOCK` configuration was not copied.

> Owner field-test three-reliability repair boundary (2026-08-11): no public
> endpoint or DTO changed. PAD_DIRECT still uses the existing
> pending/claim/start-print/payload/complete/fail/release surface; the Android
> worker now requests a shorter pre-output `CLAIMED` lease while preserving the
> longer `PRINTING` lease for ambiguous physical output. Automatic printing
> still enters the durable outbox and returns independently of order submission;
> repository dispatch is now Store+printer keyed, same-key FIFO and bounded by
> the configured print executor. Menu catalog API shape is unchanged; Pad/Web
> clients use the existing revisioned catalog plus local IndexedDB snapshots for
> revision-aware, offline-first refresh.

> Owner field-test printing repair boundary (2026-08-11): no endpoint or DTO
> changed. PAD_DIRECT keeps the existing pending/claim/start-print/payload/
> complete/fail/release API surface. The Android worker lifecycle repair keeps
> an in-flight job generation alive across app pause/stop so the existing API
> can receive complete/fail/recovery calls; it does not create blind
> `PRINTING` reclaim or any new physical-printer authority.

> TWIN-001 approved reconstruction tooling adds no endpoint or DTO. The
> Staging-only staff step reuses `/auth/login`, Store-scoped Staff Admin
> list/update/create, and `/auth/logout`; all passwords enter through an
> inherited mode-0600 descriptor and remain independent from Production.
> Menu/table/KDS/logical printing/device topology projection is a guarded
> operational tool, not a public API.

> Historical TWIN-001 manifest v2 boundary (2026-08-10): no API was added or changed.
> The completed read-only manifest loop creates no reconstruction endpoint or
> write authorization at that checkpoint. Historical stop:
> `TWIN-001_MANIFEST_V2_RECONSTRUCTION_READY_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.

> TWIN-001 reconstruction boundary (2026-08-10): reconstruction approval did
> not create a new API. Pre-write validation stopped before runtime entry
> because the retained manifest is not a complete, V7-schema-consistent writer
> input. Local verification passed the intended `V7 -> V10` forward path, but
> no reconstructed Twin or API smoke ran. The historical NO-GO stop was
> `TWIN-001_RECONSTRUCTION_NO_GO_WAITING_FOR_MANIFEST_COMPLETION_READ_APPROVAL`.
> See the
> [immutable NO-GO evidence](../docs/governance/runtime/TWIN-001_STAGING_RECONSTRUCTION_SCHEMA_NO_GO_EVIDENCE.md).

> TWIN-001 inventory boundary (2026-08-10): the Owner-approved Production
> St-Denis configuration read is recorded in the [sanitized parity manifest](../docs/governance/runtime/ST_DENIS_TWIN_PARITY_MANIFEST.md)
> and [inventory evidence](../docs/governance/runtime/TWIN-001_PRODUCTION_INVENTORY_EVIDENCE.md).
> This is repository/evidence documentation only: no API contract changed and
> no endpoint performs Staging reconstruction, Twin Sync, Production read,
> deployment, migration, restart, or business-data mutation.

> Browser transport note (2026-08-10): loopback SSH-tunnel deployments must
> preserve the browser-visible Host including an explicit port when proxying
> `/api/` and `/ws`. Otherwise Spring can classify a same-origin login as CORS
> and reject it before the authentication controller. This transport repair
> does not change any authentication, role, Organization, Store, or endpoint
> contract; API-only acceptance does not establish browser UI acceptance.

> Staging route update (2026-08-10): the existing synthetic St-Denis topology
> and browser-equivalent evidence are Twin foundation evidence only. TWIN-001
> parity planning is current; no Production configuration read or runtime
> mutation is authorized by this documentation change.

## Runtime acceptance boundary

The synthetic Staging topology/source evidence is not a public API acceptance
claim. STG-005A/STG-005B are guarded operational actions; PR #95/#97's
`owner-login-acceptance` reuses existing auth/workspace/overview/logout
contracts only. Exact Staging `1a3f2e...` deployed the proxy repair and passed
both the secret-safe API path and real-Chrome browser-equivalent acceptance.
The former Owner manual Phase-A gate is preserved but deferred by the TWIN-001
priority. Chinatown onboarding and menu clone remain blocked until the future
Twin, field-test and explicit resume gates are complete.

## Release and promotion boundary

Release identity and promotion are governance contracts, not API endpoints.
The canonical [Agile Loop release/promotion policy](../docs/governance/AGILE_LOOP_OPERATING_MODEL.md#83-canonical-release-promotion-drift-and-recovery-policy)
requires an immutable RC after Twin/automated/Owner acceptance, promotion of
the same artifact digests accepted in Staging, read-only drift detection with
explicit sync approval, `APPLICATION_ROLLBACK_COMPATIBILITY_GATE`, and backup
integrity plus restore-rehearsal readiness. No API call silently synchronizes
Staging, deploys Production, restores a database, or activates Chinatown.

This document defines the core API endpoints for the restaurant management system MVP.

## Base URL
/api/v1

## MVP Auth Context

For MVP, backend authorization uses request header:
- `X-User-Id`

Behavior:
- backend loads the current user from `users`
- backend resolves role from `roles`
- backend enforces role capability checks server-side
- this header-based context is temporary and should be replaceable by real login/auth later

## Store Access Scope

Backend store access is enforced by `StoreAccessService`.

- `organization_memberships` grants organization-level access, primarily for owners.
- `store_memberships` grants store-level access for managers, frontdesk, kitchen, noodle, and pass/runner users.
- `users.store_id` remains a legacy/default store, but new authorization checks should not depend on it alone.
- `ADMIN` is treated as platform/legacy admin and can access all stores.
- `OWNER` can access stores inside active organization memberships.
- Store-scoped APIs must return `403` when the authenticated user cannot access the requested store.

### Current User Workspaces

GET `/me/workspaces`

Returns the organizations and stores available to the current authenticated user.

### Store Context

GET `/stores/{storeId}/context`

Returns store context only if the current user is authorized for that store. URL `storeId` is never considered proof of access.

The response includes canonical Store module configuration:

- `module_configuration.store_id`
- `catalog_version`
- `dependency_graph_version`
- `valid`
- `validation_status`
- `environment_capabilities`
- `hardware_capabilities`
- `modules[]`
- `validation_issues[]`

`/me/workspaces` intentionally remains a lightweight Store/Organization list
and does not duplicate the module configuration payload.

### Store Module Configuration

GET `/stores/{storeId}/modules`

Returns canonical Store module state for authorized users with Store access.
The payload separates:

- environment capability;
- hardware capability;
- Store module state;
- legacy Store runtime mode/flag where applicable;
- validation issues.

PUT `/admin/stores/{storeId}/modules`

Request:

```json
{
  "store_id": 1,
  "modules": [
    {
      "module_key": "KDS",
      "enabled": false
    }
  ]
}
```

Rules:

- requires authenticated Store access plus `ADMIN_STORE_CONFIG` manager/owner
  authority;
- rejects unknown modules;
- rejects duplicate module updates;
- rejects disabling core modules for an active Store;
- validates the resulting Store module graph with the A2 validator;
- does not change menu revision, pricing, combo configuration, printing
  runtime mode, devices, printer endpoints, orders, receipts or reports.

## Modules
- Orders
- Kitchen
- Inventory
- Prep
- Menu
- Users & Stations

---

## Orders

### Order Status Definition
`draft`, `submitted`, `preparing`, `ready`, `picked_up`, `completed`

### Create Order
POST /orders

Request assumption for MVP:
- Combo 订单必须直接提交为真实 `order_items`
- 不允许把 Combo 作为单独 `menu_item`
- 套餐主品、配菜、鸡蛋分别作为独立 order line
- 同一套餐组共享 `combo_group_no`
- `combo_role` 支持：`main`, `combo_side`, `combo_egg`, `standalone`
- 订单项与选项响应应返回双语快照字段
- 历史订单展示必须使用快照，不依赖当前菜单主数据
- 订单项应返回 `category_code_snapshot`
- 订单选项应返回 `option_type_snapshot`

### Submit Order
POST /orders/{id}/submit

### Get Order
GET /orders/{id}

Order detail should include:
- snapshot-based item names/options
- kitchen progression when applicable
- beverage progression when applicable
- item notes and instructions
- modified-after-submit flags when applicable
- order header and timestamps

### Complete Order
POST /orders/{id}/complete

### Post-Submit Add-Only Update Rule
For orders in `submitted`, `preparing`, or `ready` status, existing items are immutable after they have been submitted.

- Existing item quantity, options, notes, and deletion are locked.
- Legacy `POST /orders/{id}/items`, item `PUT`, quantity `PUT`, and item `DELETE` endpoints accept draft orders only.
- New items are added atomically through `POST /orders/{id}/updates` with an `idempotency_key`.
- One update request creates one `order_update_batches` revision and tags each new item with `order_update_batch_id`.
- The automatic GRAB update ticket contains only items from that exact update batch.
- FRONTDESK_RECEIPT is not automatically reprinted for an update.
- Manual order reprint always renders the complete current order.

### Create Submitted Order Update
POST `/orders/{id}/updates`

Request:
- `idempotency_key` (required)
- `items` (required, new items only)

The same `order_id + idempotency_key` returns the previously created batch and does not duplicate items, tasks, inventory deductions, or print jobs.

### Order Print Options
GET `/orders/{id}/print-options`

Returns renderer-backed module options with availability and an unavailable reason based on feature, store mode, assignment, and printer configuration.

### Store Printing Modes

Print Center stores the active mode in `stores.printing_mode`.

The shared service may additionally receive an environment-specific
`app.printing.allowed-modes` ceiling and
`app.printing.endpoint-configuration-enabled` policy. Defaults preserve the
existing four-mode contract. A restricted environment rejects a Store mode or
printer endpoint write outside its configured policy before persistence or
dispatch; this is a deployment safety boundary, not a Store-specific business
branch.

- `REAL`: backend renders and sends ESC/POS TCP to the configured printer.
- `MOCK`: backend renders, stores preview text, and marks jobs printed without socket access.
- `PAD_DIRECT`: backend renders and stores `PENDING` print jobs for Android Pad local printing. Backend must not open TCP printer sockets in this mode.
- `DISABLED`: backend cancels automatic print jobs without physical printing.

`PAD_DIRECT` only changes where printing is executed. It does not change order submission, order update, manual reprint, or receipt rendering semantics.

PAD_DIRECT complete/fail keeps its existing job-state contract. Printer health
timestamps are updated only through a printer lookup scoped to the durable
job's Store; a missing or out-of-scope printer does not redirect the health
write to another Store.

Printer configuration writes are Store-scoped. `PUT
/api/v1/admin/printing/printers/{id}` requires access to the request Store and
the existing printer row must already belong to that same Store. A mismatched
printer ID is rejected; the endpoint cannot transfer a printer config between
Stores by changing `store_id`.

### Pad Direct Device APIs

Admin/device registration:

- `POST /api/v1/devices/register`
  - Auth: normal Bearer token with store admin configuration access.
  - Request: `store_id`, `device_name`, `device_type`, `app_version`, `platform`.
  - Response includes `device_id` and one-time `device_token`.
  - Backend stores only a SHA-256 hash of the token.
- `POST /api/v1/devices/heartbeat`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Updates `last_seen_at` only when stale by at least 30 seconds, and updates
    `app_version` / `platform` when changed.
- `GET /api/v1/admin/printing/devices?store_id={storeId}`
  - Auth: normal Bearer token with store admin configuration access.
  - Returns registered store devices without token secrets, including
    `id`, `device_name`, `store_id`, `organization_id`, `platform`,
    `app_version`, `status`, `is_active`, `last_seen_at`, `created_at`, and
    `updated_at`.
- `PATCH /api/v1/admin/printing/devices/{deviceId}/rename?store_id={storeId}`
  - Auth: normal Bearer token with store printing management/config access.
  - Request: `device_name`.
  - Renames the device without rotating credentials.
- `POST /api/v1/admin/printing/devices/{deviceId}/disable?store_id={storeId}`
  - Auth: normal Bearer token with store printing management/config access.
  - Soft-disables the device with `status = DISABLED`, `is_active = false`.
- `POST /api/v1/admin/printing/devices/{deviceId}/revoke?store_id={storeId}`
  - Auth: normal Bearer token with store printing management/config access.
  - Soft-revokes the device with `status = REVOKED`, `is_active = false`.
  - Disabled/revoked devices fail device-authenticated runtime calls with `403`.

AL-005B is an `IN_MAIN` provisioning plan, not an additional API. PR #68 adds
no endpoint, DTO, migration, device write, token operation,
pairing, or Worker behavior. Versioned Store Profiles must not carry device IDs,
tokens, pairing state, `last_seen_at`, auto-print preferences, Worker state, or
printer endpoints. Existing code has no per-device module assignment: every
active paired Pad can consume eligible PAD_DIRECT jobs for its Store. A future
read-only planner may expose only sanitized readiness counts/diagnostics after a
separately reviewed contract.

Pad print queue:

- `GET /api/v1/stores/{storeId}/printing/jobs/pending?limit=25`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Returns `PAD_DIRECT` jobs with status `PENDING` or expired `CLAIMED` lease.
- `POST /api/v1/printing/jobs/{jobId}/claim`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Request: `client_attempt_token`, optional `lease_seconds`.
  - Atomically changes the job to `CLAIMED`; concurrent devices receive `409`.
- `POST /api/v1/printing/jobs/{jobId}/start-print`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Request: `client_attempt_token`, optional `lease_seconds`.
  - Changes the claimed job to `PRINTING` for the same device/attempt and
    extends the lease before native TCP output starts.
- `GET /api/v1/printing/jobs/{jobId}/payload`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Only the claiming device can read payload.
  - Returns `rendered_text_snapshot` and `escpos_payload_base64`.
- `POST /api/v1/printing/jobs/{jobId}/complete`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Request: `client_attempt_token`, optional `raw_result`.
  - Marks the job `PRINTED`.
- `POST /api/v1/printing/jobs/{jobId}/fail`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Request: `client_attempt_token`, `error_code`, `error_message`, optional `raw_result`.
  - Marks the job `FAILED` and increments `retry_count`.
- `POST /api/v1/printing/jobs/{jobId}/release`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Request: `client_attempt_token`, optional `reason`.
  - Returns the job to `PENDING`.

### Today Order History
GET `/frontdesk/orders/today?store_id=1&limit=100`

Returns lightweight summaries for today's orders. Order detail is loaded separately through `GET /orders/{id}`.

---

## Kitchen

### Get Tasks
GET /kitchen-tasks

Task response should use snapshot fields:
- `item_name_snapshot_zh`
- `item_name_snapshot_en`
- `special_instructions_snapshot`

### Kitchen Task Status Definition
`pending`, `in_progress`, `ready_for_pickup`, `served`, `cancelled`

### Complete Task
POST /kitchen-tasks/{id}/complete

Compatibility alias:
- for MVP this means mark item `ready_for_pickup`

### Start Task
POST /kitchen-tasks/{id}/start

### Mark Ready For Pickup
POST /kitchen-tasks/{id}/ready-for-pickup

### Mark Served
POST /kitchen-tasks/{id}/served

### Get Ready Orders
GET /orders/ready

### Mark Picked Up
POST /orders/{id}/pickup

### KDS APIs
- GET `/kds/noodle-display`
- GET `/kds/hot-kitchen`
- GET `/kds/pass`
- GET `/kds/frontdesk-beverages`
- GET `/kds/serving-shelf`
- GET `/kds/history`

---

## Frontdesk Beverage

### Beverage Item Status Definition
`pending`, `preparing`, `ready`, `served`, `cancelled`

### Beverage Board
GET `/frontdesk/beverages`

Default behavior:
- show frontdesk-managed beverage items for one store
- include `DRINK`, `ALCOHOL`, and taskless `MILK_TEA`
- use order/item snapshot fields only

### Start Beverage Preparation
POST `/frontdesk/beverages/{orderItemId}/start`

### Mark Beverage Ready
POST `/frontdesk/beverages/{orderItemId}/ready`

### Mark Beverage Served
POST `/frontdesk/beverages/{orderItemId}/served`

### Cancel Beverage Item
POST `/frontdesk/beverages/{orderItemId}/cancel`

---

## Frontdesk Order Board

### Frontdesk Active Board
GET `/frontdesk/orders`

Recommended filters:
- `store_id`
- `status` (`submitted`, `preparing`, `ready`, `completed`, `cancelled`, or `all`)
- `order_type`
- `table_no`
- `pickup_no`
- `keyword`

Default behavior:
- active board defaults to `submitted`, `preparing`, `ready`
- completed/cancelled only appear when explicitly filtered

Summary response should include:
- `order_id`
- `order_no`
- `order_type`
- `table_no`
- `pickup_no`
- `order_status`
- `is_modified_after_submit`
- `modified_after_submit_at`
- `submitted_at`
- `updated_at`
- `total_item_count`
- `ready_item_count`
- `beverage_pending_count`
- `kitchen_pending_count`

### Frontdesk Order History
GET `/frontdesk/orders/history`

Recommended filters:
- `store_id`
- `status` (`completed`, `cancelled`, or `all`)
- `order_type`
- `table_no`
- `pickup_no`
- `keyword`
- `limit`

Default behavior:
- recent history defaults to 20 orders
- default statuses are `completed` and `cancelled`

---

## Realtime / WebSocket

### WebSocket Endpoint
`/ws`

Recommended transport for MVP:
- Spring WebSocket + STOMP
- SockJS fallback is enabled

### Topic Design
Topics are store-scoped and screen-scoped:
- `/topic/stores/{storeId}/frontdesk/orders`
- `/topic/stores/{storeId}/frontdesk/beverages`
- `/topic/stores/{storeId}/kds/noodle-display`
- `/topic/stores/{storeId}/kds/hot-kitchen`
- `/topic/stores/{storeId}/kds/pass`
- `/topic/stores/{storeId}/kds/serving-shelf`
- `/topic/stores/{storeId}/history`

### Event Payload
For MVP the backend publishes lightweight refresh events. Frontend may re-fetch the relevant REST view after receiving an event.

Recommended payload shape:
- `event_type`
- `store_id`
- `order_id`
- `order_item_id`
- `order_status`
- `task_status`
- `beverage_status`
- `is_modified_after_submit`
- `happened_at`
- `suggested_topics`

### Realtime Publish Triggers
Order events:
- order created
- order submitted
- order modified after submit
- order cancelled
- order marked ready
- order marked completed

Kitchen task events:
- kitchen task started
- kitchen task marked `ready_for_pickup`
- kitchen task marked `served`
- kitchen task cancelled

Beverage item events:
- beverage item started
- beverage item marked `ready`
- beverage item marked `served`
- beverage item cancelled

### Frontend Refresh Expectation
- submitted orders should appear automatically on frontdesk and KDS screens
- modified orders/items should refresh and expose modified flags
- serving shelf should refresh when pass marks an item `ready_for_pickup`
- serving shelf should refresh again when runner marks an item `served`
- beverage board and order detail should refresh when beverage status changes

---

## Inventory

### Get Items
GET /inventory/items

### Restock
POST /inventory/restock

### Transactions
GET /inventory/transactions

---

## Prep

### Execute Prep
POST /prep-recipes/{id}/execute

---

## Menu

### Health
GET /menu/health

### Get Catalog
GET /menu/catalog?store_id=...

Response behavior:
- requires `X-User-Id`
- current backend enforces `order:create` capability for store-scoped catalog access
- returns active categories with nested active items and nested active options
- returns bilingual fields directly (`name_zh`, `name_en`)
- item payload includes:
  - `id`
  - `category_id`
  - `station_id`
  - `sku`
  - `item_type`
  - `base_price`
  - `is_sold_out`
- catalog payload includes `pricing_policy`:
  - `store_id`
  - `policy_revision`
  - `size_small_delta`
  - `size_regular_delta`
  - `size_large_delta`
  - `combo_delta`
- catalog payload includes `combo_configuration`:
  - `store_id`
  - `menu_revision`
  - `groups[]`
    - `component_group`
    - `name_zh`
    - `name_en`
    - `default_component_code`
    - `components[]`
      - `component_group`
      - `component_code`
      - `name_zh`
      - `name_en`
      - `enabled`
      - `display_order`
      - `is_default`
- option payload includes:
  - `id`
  - `option_type`
  - `option_code`
  - `option_group`
  - `parent_option_id`
  - `sort_order`
  - `name_zh`
  - `name_en`
  - `price_delta`
  - `is_active`

### Menu Modeling Notes
- `menu_items.station_id` 是菜品默认工位
- `menu_item_options` 为菜品级独立选项，不是全局选项
- `option_type` remains for compatibility: `noodle_type`, `size`, `addon`, `remove`, `soup_base`, `spicy_level`
- `option_group` is the preferred semantic grouping for new code: `SIZE`, `SOUP_BASE`, `NOODLE_TYPE`, `SPICY_LEVEL`, `ADD_ON`, `REMOVE`, `COMBO`, `COMBO_EGG`, `COMBO_SIDE`, `COMBO_SIDE_REMOVE`
- `option_code` is the preferred stable machine identifier. Legacy Chinese-name matching is fallback only.
- `parent_option_id` supports child option modeling, for example `COMBO_SIDE_REMOVE` under a specific `COMBO_SIDE`
- Catalog option ordering is `sort_order ASC NULLS LAST, id ASC`
- Inactive options are hidden from new ordering, but historical orders use `order_item_options` snapshots
- `SIZE` options are the per-item Size enablement/identity model, but Owner
  generic option create/update/deactivate/reorder routes reject Size writes.
  Use the Size Configuration endpoint below. Supported Size semantics are only
  `size_small`, `size_regular`, and `size_large`.
- Combo upcharge rows are also system-controlled. Generic option
  create/update/deactivate/reorder routes reject Combo upcharge writes; use
  Item Combo Policy for allow/disable and Pricing Rules for Store-level delta.
- Store-level Combo contents are system-controlled in `store_combo_components`.
  Use Store Combo Configuration for egg/side availability. Supported first
  catalog codes are `combo_tea_egg`, `combo_fried_egg`, `combo_edamame`,
  `combo_shredded_potato`, and `combo_cucumber_salad`.
- Frontdesk ordering reads Store-level egg/side choices from
  `combo_configuration`, not from item-scoped `menu_item_options`
  `COMBO_EGG`/`COMBO_SIDE` rows. Frozen order snapshots use stable negative
  transport IDs for those Store-level choices; the IDs are not database row
  identities.
- Size and Combo deltas in new catalog responses are effective Store policy
  values from `store_pricing_policies`; Size/Combo `menu_item_options.price_delta`
  is maintained only as a rollback compatibility mirror.
- 菜单主数据使用双语字段：`name_zh`, `name_en`
- MVP API 默认返回双语字段，由前端决定中文优先与英文回退逻辑
- `DRINK` 与 `ALCOHOL` 为 direct-serve，不进厨房
- `MILK_TEA` 是否进入 BAR 任务流由门店配置决定

### Owner Pricing Rules

All endpoints require `X-User-Id` with `admin:menu_manage` for the target Store.

#### Get Store Pricing Policy

`GET /api/v1/admin/menu/pricing-policy?store_id={storeId}`

Response data:

```json
{
  "store_id": 1,
  "policy_revision": 1,
  "size_small_delta": -2.00,
  "size_regular_delta": 0.00,
  "size_large_delta": 2.00,
  "combo_delta": 5.00
}
```

#### Preview Store Pricing Policy

`POST /api/v1/admin/menu/pricing-policy/preview`

Request:

```json
{
  "store_id": 1,
  "size_small_delta": "-2.00",
  "size_regular_delta": "0.00",
  "size_large_delta": "3.00",
  "combo_delta": "6.00"
}
```

Response includes current/proposed policy and impact groups with affected item
counts and sample old/new effective prices for future orders. Historical orders
are not repriced.

#### Update Store Pricing Policy

`PUT /api/v1/admin/menu/pricing-policy`

Request shape matches preview. The backend stores decimal money as BigDecimal,
writes the Size/Combo compatibility mirror, increments `stores.menu_revision`
and updates `stores.menu_updated_at` in the same transaction.

#### Update Item Size Configuration

`PUT /api/v1/admin/menu/items/{itemId}/size-configuration`

Request:

```json
{
  "enabled_size_codes": ["size_regular", "size_large"],
  "default_size_code": "size_regular"
}
```

Only `size_small`, `size_regular`, and `size_large` are accepted. If Regular is
enabled it is the deterministic default. If only one Size is enabled it is
auto-selected. If multiple Sizes are enabled and Regular is disabled,
`default_size_code` must name one enabled Size.

#### Update Item Combo Policy

`PUT /api/v1/admin/menu/items/{itemId}/combo-policy`

Request:

```json
{
  "combo_allowed": true
}
```

The item controls only whether Combo is allowed. The Combo delta remains
Store-level.

#### Get Store Combo Configuration

`GET /api/v1/admin/menu/combo-configuration?store_id={storeId}`

Response data:

```json
{
  "store_id": 1,
  "menu_revision": 12,
  "groups": [
    {
      "component_group": "COMBO_EGG",
      "name_zh": "蛋类",
      "name_en": "Egg",
      "default_component_code": "combo_tea_egg",
      "components": [
        {
          "component_group": "COMBO_EGG",
          "component_code": "combo_tea_egg",
          "name_zh": "卤蛋",
          "name_en": "Tea Egg",
          "enabled": true,
          "display_order": 10,
          "is_default": true
        }
      ]
    }
  ]
}
```

#### Update Store Combo Configuration

`PUT /api/v1/admin/menu/combo-configuration`

Request:

```json
{
  "store_id": 1,
  "components": [
    {
      "component_group": "COMBO_EGG",
      "component_code": "combo_tea_egg",
      "enabled": true
    },
    {
      "component_group": "COMBO_EGG",
      "component_code": "combo_fried_egg",
      "enabled": false
    }
  ]
}
```

Only reviewed `COMBO_EGG` and `COMBO_SIDE` component codes are accepted. The
write is Store-scoped, emits `COMBO_CONFIGURATION_UPDATED`, increments
`stores.menu_revision`, and updates `stores.menu_updated_at` in the same
transaction. If an item allows Combo but no enabled required egg/side component
remains available for that Store, the backend rejects the update with
`COMBO_EGG_CONFIGURATION_MISSING` or `COMBO_SIDE_CONFIGURATION_MISSING`.

New order submission rejects disabled or unsupported Store-configured
`COMBO_EGG` / `COMBO_SIDE` selections. Historical order item snapshots are not
rewritten.

Frontdesk catalog/ordering clients must use the `combo_configuration` response
for Store-level egg/side choices. Item-scoped `menu_item_options`
`COMBO_EGG`/`COMBO_SIDE` rows are rollback-compatible legacy data and are hidden
from new ordering.

---

## Users & Stations

## Owner Workspace

### Authentication Login Contract

`POST /api/v1/auth/login`

Canonical request:

```json
{
  "login_identifier": "<approved-login-identifier>",
  "password": "<runtime-only-secret>"
}
```

- `login_identifier` is the canonical JSON field. The compatibility aliases
  `loginId`, `loginIdentifier`, and `username` are accepted, but operational
  tooling should use the canonical field.
- The lookup trims the identifier and matches
  `user_credentials.login_identifier` case-insensitively. It does not require
  an email-shaped value.
- A successful credential must be active and use the `BCRYPT` algorithm; login
  returns access/refresh tokens, user context, feature flags, and permissions.
  Those tokens and the submitted password must never enter repository or
  runtime evidence.
- The guarded STG-005A creation path is deliberately narrower than the general
  login lookup: the bootstrap Owner login/display identity must begin with
  `STG005_`, is written consistently to `users.username` and
  `user_credentials.login_identifier`, and accepts only a runtime password of
  12 through 256 characters through non-interactive standard input.
- The STG-008 read-only entry found no existing synthetic Owner and stopped
  before plan/write because the requested credential convention did not meet
  those retained bootstrap guards. The Owner later approved
  `STG005_OWNER_20260808_R01` without changing them. Fresh password-free plan
  readiness then exposed a pre-command cloud/Flyway safety-rule conflict; no
  credential, bootstrap transaction, or login was attempted. The API contract
  is unchanged, and runtime remains gated on repaired exact-SHA deployment and
  blocked-state recovery.

### Owner Store Onboarding API (AL-002 in main)

`POST /api/v1/owner/organizations/{organizationId}/stores/onboard`

Headers:

- authenticated Owner context;
- required `Idempotency-Key` (maximum 255 trimmed characters).

Request:

```json
{
  "source_store_id": 1,
  "store_name": "Synthetic Target",
  "store_code": "STG005_TARGET",
  "staff": [
    {
      "login_identifier": "STG005_MANAGER_EXAMPLE",
      "full_name": "STG005 Manager",
      "role_code": "MANAGER",
      "initial_password": "runtime-only secret"
    }
  ]
}
```

Current contract:

- requires the caller to have role `OWNER` plus an active `OWNER`
  `organization_membership` in the exact Organization;
- requires the source Store to belong to that Organization;
- creates one inactive, printing-disabled target Store;
- requires at least one staff entry and currently accepts only `MANAGER` and
  `FRONTDESK` staff roles;
- creates BCrypt-backed credentials and explicit target Store memberships for
  those staff accounts;
- does not create an Owner target Store membership. Under the current
  `StoreAccessService` contract, the Organization Owner automatically accesses
  every Store in that Organization and receives the Organization Owner role in
  workspace/store context;
- returns `onboarding_request_id`, Organization/source/target Store fields,
  onboarding/result status, `replayed`, and redacted staff identifiers/roles;
  it never returns the supplied passwords or password hashes;
- same Organization/key/fingerprint replays the existing completed result;
  changed structural content or a different replay password conflicts; an
  in-progress request does not create another Store;
- creates no menu, table, printer, assignment, Pad/device, order, payment, or
  activation record.

This existing API is the target-Store creation component of the future Owner
provisioning workflow. No Owner UI or menu-template selection contract is
currently exposed by this endpoint.

### Store Activation API (not implemented)

There is currently no Owner or Platform API that implements the reviewed
fail-closed Store activation workflow. `Store.status` is a free-text field, not
the conceptual AL-006 lifecycle, and legacy Platform Admin Store endpoints can
write status directly. Do not infer activation readiness from onboarding,
menu-clone completion, a device heartbeat, or an existing `active` value.

The future contract is planned in the `IN_MAIN` PR #69 document
[AL-006 Store Activation Workflow Plan](../docs/governance/agile/AL-006_STORE_ACTIVATION_WORKFLOW_PLAN.md).
It requires Profile-bound evidence from access/staff, menu, tables, printing,
devices, login, and operational smoke checks before an approved workflow can
perform a final status transition. This planning package adds no route, DTO,
Migration, authorization capability, or runtime action.

### Owner Store Menu Clone API (AL-003 PR-F in main)

Current `main` contains internal persistence/idempotency DTOs, the generic
Category/Station/Item transaction, generic source-option cloning, and the
versioned Chinatown Profile, plus PR-F0's shared read-only option planner and
structured diagnostics. PR #56 added the following Owner-only routes. They are
in the repository contract but are not thereby deployed or runtime-validated.

The current internal request DTO shape is:

```json
{
  "source_store_id": 1,
  "profile_code": "CHINATOWN_MENU_2026_02_02"
}
```

The internal reservation contract binds a bounded idempotency key to a SHA-256
request fingerprint and safe execution evidence under this composite
uniqueness scope:

```text
(organization_id, source_store_id, target_store_id, idempotency_key)
```

Current internal request states are `PROCESSING`, `COMPLETED`, and `FAILED`.
The internal foundation behavior is:

- same scope/key/fingerprint after completion returns the stored result as a
  replay containing only request/scope IDs, revisions, status, created counts,
  safe `result_code`, and deterministic safe warning codes;
- replay does not return category, station, item, or option ID maps;
- same scope/key with a different fingerprint returns
  `IDEMPOTENCY_CONFLICT`;
- an existing processing request returns `MENU_CLONE_IN_PROGRESS`;
- `FAILED` is terminal for that idempotency key; after revalidation, a retry
  must use a new key and must not transition the failed request back to
  `PROCESSING`;
- failure evidence stores only a normalized error code and bounded revision
  context, never a menu payload, credential, token, printer endpoint, or raw
  exception message.

V10 has no warning payload column. Response DTO `warnings` are therefore
bounded stable codes derived from durable result evidence (or an empty list),
not replayed execution detail. The response DTO includes `result_code`
and intentionally excludes internal source-to-target ID maps.

PR #52 placed the internal, generic `SOURCE_OPTIONS` graph composer in `main`;
PR #54 added the concrete Chinatown Profile and target override composer without
changing the request/response DTO or registering a Controller. PR #55 adds an
internal `validate` contract only: it composes a virtual
target option plan, invokes the same complete validator used by execute, and
returns bounded `missingCodes`, `duplicateCodes`, and safe `warnings`. It is not
an HTTP contract and performs no menu, revision, request, or audit write.
PR #56's PR-F facade reuses those exact internal paths rather than implementing
a second clone engine:

- `POST /api/v1/owner/organizations/{organizationId}/stores/{targetStoreId}/menu-clone/validate`
  is read-only, needs no idempotency key, and returns `valid`, revisions,
  expected counts, and bounded `missing_codes`, `duplicate_codes`, and
  `warnings`.
- `POST /api/v1/owner/organizations/{organizationId}/stores/{targetStoreId}/menu-clone`
  requires `Idempotency-Key` and returns the existing sanitized durable clone
  response. Completed same-key replays return `replayed=true`; `FAILED` is
  terminal and returns `MENU_CLONE_RETRY_REQUIRES_VALIDATION` for that key.
- Both routes require an active `OWNER` membership in the exact Organization.
  Platform `ADMIN` has no implicit bypass. Source or cross-Organization target
  failures return `MENU_CLONE_FORBIDDEN`; an authorized missing target returns
  `TARGET_STORE_NOT_FOUND`.
- Fixed request-field errors return `MENU_CLONE_REQUEST_INVALID`. No response
  exposes source-to-target ID maps, source menu payloads, credentials, tokens,
  endpoints, or raw exceptions.

STG-005B does not add another menu endpoint. Its Synthetic St-Denis manifest
planner/applier is a disabled-by-default, non-web Staging command used only to
prepare AL-003 acceptance data after separate runtime approval. Public clients
must continue to use the existing Owner `/menu-clone/validate` and
`/menu-clone` contracts; the synthetic manifest, fingerprint payload, and
internal database IDs are not API responses.

The dependency-bound AL-003S guarded one-shot launcher is operational Staging
tooling, not a public HTTP endpoint. It does not alter authentication, DTO,
idempotency, replay, terminal-`FAILED`, authorization, or error contracts. Its
future use still requires an exact-SHA Owner runtime approval, and it never
handles bearer tokens or menu-clone idempotency keys. Before any one-shot it
requires fresh resource and Staging/Production container fingerprints, an
action/identity-bound approval digest, and an immutable backend image ID; these
operational bindings do not alter any HTTP API contract. The one-shot is
serialized, time-bounded, and post-checked; the approval artifact is procedural
evidence binding rather than a cryptographic API authorization credential.

STG-006 added no endpoint, DTO, header, or authentication behavior. Its passive
runtime evidence confirms only the current Staging boundary. OPS-001 adds a
repository operational client that reuses the existing `/auth/login`,
`/auth/me`, workspace, Owner overview/onboarding, `/menu-clone/validate`,
`/menu-clone`, and `/auth/logout` contracts; its bounded
`owner-login-acceptance` action uses only `/auth/login`, `/auth/me`,
`/me/workspaces`, `/owner/overview`, and `/auth/logout` to prove the approved
synthetic Owner's exact Organization and source-Store access before logout. It
adds no endpoint, DTO, header, authorization bypass, onboarding, clone, or
application behavior. Passwords, access/refresh
tokens, staff passwords, and raw onboarding/clone idempotency keys enter only
through an inherited descriptor and private temporary files, never argv,
stdout, shell history or evidence. The client verifies Organization Owner
identity/access, target onboarding/replay, inherited target access, reviewed
profile/counts, execute revision and replay. Its repository tests are not login
or Staging evidence; every real API batch remains separately Owner-approved.

The `IN_MAIN` AL-004 Store Profile contract does not add an HTTP endpoint. Its
registry and safe summary are internal declarative contracts only. Owner
template discovery/selection, Store provisioning execution, and concrete
Chinatown/St-Denis Store Profiles remain unimplemented and must not be inferred
from these internal types.

The AL-005A Staff/Table preparation adds no HTTP endpoint or DTO. The existing
Owner onboarding transaction and the guarded STG-005A synthetic-bootstrap
transaction are the two controlled parent paths that currently invoke
`OnboardingStaffProvisioningService`; the internal staff service is not an
independently idempotent public API. It currently creates a Store membership for
every supplied role, so a future Store-scoped staff adapter must not use it to
create Organization Owners. No Owner table-provisioning endpoint, table
template API, replay contract, or activation API exists. Existing Platform
Admin dining-table endpoints must not be treated as a generic Store
provisioning writer until Store ownership and table-code idempotency constraints
are separately reviewed.

The AL-005 Printing provisioning preparation adds no HTTP endpoint or DTO.
Existing Print Center APIs remain the only operational configuration surface;
they are not a generic Store provisioning API. No profile endpoint accepts or
returns printer endpoints, database printer IDs, device identities, secrets,
raw print payloads, or physical-test evidence. A future read-only planner and
writer require separate reviewed API contracts after Store-isolation,
strict-mode, assignment-integrity, idempotency, and device-readiness gates.

PR #70's `IN_MAIN` REL-001 package is a Production Release Candidate plan only.
It adds no release, deployment, Store 1 read, provisioning, or activation API.
There is no generic Store Provisioning Engine endpoint in the current contract.

### Owner Multi-Store Overview
GET `/api/v1/owner/overview`

Purpose:
- Returns the current user's accessible organizations/stores and lightweight per-store operating summary for Owner Home.
- Used by `/owner/dashboard`.
- Does not replace store-scoped operational APIs.

Access:
- Allowed: `OWNER`, `ADMIN`, `MANAGER`
- Denied: `FRONTDESK`, `HOT_KITCHEN`, `NOODLE_VIEW`, `PASS`
- Backend must scope stores through `StoreAccessService`; frontend filtering is not a security boundary.

Response data:
- `organizations[]`
  - `id`
  - `name`
  - `code`
  - `status`
  - `role_code`
  - `stores[]`
- `stores[]`
  - `id`
  - `name`
  - `code`
  - `status`
  - `role_code`
  - `features.core_pos`
  - `features.printing`
  - `features.kds`
  - `features.admin`
  - `features.analytics`
  - `summary.today_orders`
  - `summary.today_sales`
  - `summary.active_orders`
  - `summary.occupied_tables`
  - `summary.open_tables`
  - `summary.failed_print_jobs`
  - `summary.printing_mode`
  - `summary.last_failed_print_at`
  - `summary.kds_active_count`
  - `summary.last_updated_at`

Notes:
- KDS active count is returned only when KDS feature is enabled; Owner Home must not call KDS live endpoints when KDS is disabled.
- Printing summary is read-only and must not dispatch jobs.

### Get Stations
GET /stations

### Assign Stations
POST /users/{id}/stations

### MVP Role Model

Role codes:
- `FRONTDESK`
- `HOT_KITCHEN`
- `NOODLE_VIEW`
- `PASS`
- `ADMIN`

Capability summary:
- `FRONTDESK`: order create/edit/submit/modify/complete/cancel, active/history/detail reads, beverage board/actions, serving shelf view, mark shelf item served
- `HOT_KITCHEN`: hot kitchen view, start task, mark item ready_for_pickup
- `NOODLE_VIEW`: read-only noodle display
- `PASS`: pass screen view, full-order monitoring, mark item ready_for_pickup, serving shelf view
- `ADMIN`: full access for MVP

---

## Notes
- All responses use JSON
- Use ISO datetime format
- MVP focuses on core flow only
- Order status flow is strictly: `draft` -> `submitted` -> `preparing` -> `ready` -> `picked_up` -> `completed`
- Combo is pricing/sales logic only, not a standalone kitchen item
- Combo egg/side/side-remove selection should use `option_group` and `option_code`; display-name matching is legacy fallback only
- Kitchen tasks are assigned using `menu_items.station_id`
- Kitchen tasks are generated on `POST /orders/{id}/submit`
- `station_code` is copied from the resolved enabled station record
- If the configured station is not enabled for the store, submission must fail clearly
- Kitchen task handoff is item-level: `ready_for_pickup` means the item is prepared and placed on the serving shelf
- Runner/server marks individual shelf items as `served`
- Order becomes `ready` automatically when all required kitchen tasks are `ready_for_pickup` or `served`
- Frontdesk beverage view uses order snapshots for `DRINK`, `ALCOHOL`, and store-configured taskless `MILK_TEA`
- Frontdesk beverage workflow is item-level and stored separately from `kitchen_tasks`
- Beverage items do not block kitchen READY automation for this store
- Order detail should show both kitchen progression and beverage progression when applicable
- Frontdesk board and history must use snapshot-backed order/item/task/beverage data, not live menu names
- Chinese is the default display language; English is optional via UI language switch
- If English text is empty, frontend should fall back to Chinese
