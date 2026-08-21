# 04 Ordering Sequence

## Purpose

This sequence records the current order submission flow from the touch-friendly
frontend through backend transaction, durable snapshots, realtime events, and
printing dispatch handoff.

## Current runtime/source SHA

- Repository source SHA: `923346f15757ca85fdafb509a803e87f04ae55bd`
- Deployed Staging SHA: `923346f15757ca85fdafb509a803e87f04ae55bd`
- Staging Flyway: `V16`

## Scope

Current Staging safe ordering path, including IndexedDB menu/draft support and
server-side submitted order snapshots. It does not describe payment,
Production promotion, or Phase B Store creation.

## Mermaid diagram

```mermaid
sequenceDiagram
    autonumber
    participant Pad as Pad or browser UI
    participant Cache as IndexedDB menu and draft cache
    participant API as Order API
    participant OrderSvc as IdempotentOrderSubmissionService and OrderService
    participant DB as PostgreSQL transaction
    participant WS as Realtime publisher
    participant Outbox as Order dispatch outbox
    participant Printing as Printing pipeline

    Pad->>Cache: load active menu snapshot and local draft
    Pad->>API: submit order with Store scope, idempotency key, frozen snapshots
    API->>OrderSvc: validate Store access, menu snapshot, table/context
    OrderSvc->>DB: begin transaction
    DB-->>OrderSvc: current Store/menu/pricing/combo records
    OrderSvc->>DB: persist order, order_items, option snapshots
    OrderSvc->>DB: create kitchen/frontdesk/production tasks when applicable
    OrderSvc->>DB: persist inventory and order-side effects
    OrderSvc->>DB: enqueue durable print dispatch intents
    OrderSvc->>DB: commit
    OrderSvc->>WS: publish order and kitchen updates after commit
    OrderSvc-->>API: submitted order response with snapshot totals
    API-->>Pad: success; local draft/outbox can settle
    Outbox->>Printing: process committed dispatch intent asynchronously
```

## Key invariants

- Order submission is transactional for order rows, submitted snapshots,
  kitchen/frontdesk/production tasks, inventory side effects, and print outbox
  enqueueing.
- Snapshot prices, names, categories, station route, option selections, and
  combo component selections remain attached to the submitted order.
- Historical orders and receipts are not repriced by later pricing policy,
  combo, or menu changes.
- Printing dispatch is asynchronous after commit; order submission must not be
  rolled back by print renderer or dispatch failure.
- Store and Organization scope are enforced before business action.

## What omitted

- payment/refund behavior
- Production runtime differences
- physical printer transport details
- full inventory/BOM schema

## Source files used

- `backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/service/impl/OrderDispatchOutboxServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/service/impl/OrderDispatchOutboxProcessor.java`
- `backend/src/main/java/com/restaurant/system/menu/service/impl/MenuServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/menu/service/impl/MenuCatalogHashService.java`
- `frontend/src/offline/offlineDatabase.ts`
- `frontend/src/offline/menuCache.ts`
- `frontend/src/hooks/useMenuCatalog.ts`

## Last verified date

2026-08-14.
