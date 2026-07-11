# Restaurant System API (MVP)

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

- `REAL`: backend renders and sends ESC/POS TCP to the configured printer.
- `MOCK`: backend renders, stores preview text, and marks jobs printed without socket access.
- `PAD_DIRECT`: backend renders and stores `PENDING` print jobs for Android Pad local printing. Backend must not open TCP printer sockets in this mode.
- `DISABLED`: backend cancels automatic print jobs without physical printing.

`PAD_DIRECT` only changes where printing is executed. It does not change order submission, order update, manual reprint, or receipt rendering semantics.

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

Pad print queue:

- `GET /api/v1/stores/{storeId}/printing/jobs/pending?limit=25`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Returns `PAD_DIRECT` jobs with status `PENDING` or expired `CLAIMED` lease.
- `POST /api/v1/printing/jobs/{jobId}/claim`
  - Auth: `X-Device-Id`, `X-Device-Token`.
  - Request: `client_attempt_token`, optional `lease_seconds`.
  - Atomically changes the job to `CLAIMED`; concurrent devices receive `409`.
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
- 菜单主数据使用双语字段：`name_zh`, `name_en`
- MVP API 默认返回双语字段，由前端决定中文优先与英文回退逻辑
- `DRINK` 与 `ALCOHOL` 为 direct-serve，不进厨房
- `MILK_TEA` 是否进入 BAR 任务流由门店配置决定

---

## Users & Stations

## Owner Workspace

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
