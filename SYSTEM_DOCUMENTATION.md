# SYSTEM DOCUMENTATION

Generated from the current codebase only. If a detail is not explicit in code, it is marked as `UNKNOWN`.

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
│       │   │   ├── common/
│       │   │   ├── inventory/
│       │   │   ├── kitchen/
│       │   │   ├── menu/
│       │   │   ├── order/
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

- `common`
- `inventory`
- `kitchen`
- `menu`
- `order`
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
- `InventoryItem` -> `inventory_items`
- `InventoryTransaction` -> `inventory_transactions`
- `PrepRecipe` -> `prep_recipes`
- `PrepRecipeDetail` -> `prep_recipe_details`
- `Station` -> `stations`

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
- `UNKNOWN`

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
- The shared ordering workflow is mounted at:
  - `/frontdesk/menu?slot={slotLabel}&table={tableLabel}&type={dine_in|pickup}[&pickup={pickupLabel}]`
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
4. Create `order_items` rows from request items.
5. Create `order_item_options` rows from selected options.
6. Copy snapshot data from menu master data into bilingual snapshot fields.
7. Calculate subtotal and total amount.
8. Save the order.
9. Publish realtime event `order.created`.

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
6. Deduct inventory using `menu_item_bom` and `menu_item_option_bom`.
7. Insert `inventory_transactions`.
8. If kitchen tasks exist:
   - final order status becomes `preparing`
9. If kitchen tasks do not exist:
   - final order status becomes `ready`
   - `ready_at` is set
10. Save the order.
11. Publish realtime event `order.submitted`.
12. If status became `ready`, also publish `order.ready`.

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

- The table board is still driven by local mock slot occupancy state.
- Order completion / end-table flow is not wired into the visible ordering page yet.
- No payment UI exists.
- No routing beyond the current single-page setup is present.
- The frontend currently hardcodes:
  - `store_id = 1`
  - `X-User-Id = 1`
- Backend draft writes can have a short visibility delay on immediate follow-up `GET /api/v1/orders/{id}` reads; the frontend currently mitigates this with a small retry window for manual refresh reads.
- `GET /api/v1/orders/{id}` can still return stale order-level totals/flags after some submitted/preparing-order edits, even when mutation responses are correct.
- The frontend works around table re-entry by resolving the latest editable order id through the frontdesk order board before fetching full order detail.

### 9.2 Backend Gaps

- Inventory, menu, station, and user modules currently expose health endpoints only.
- Payment flow is not implemented.
- Refund flow is not implemented.
- Authentication is not fully implemented; current authorization depends on `X-User-Id` request header.
- Explicit database foreign key constraints are `UNKNOWN` from code.
- No Flyway or Liquibase migration files were found in the current codebase.

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
