# Current Restaurant Pilot Scope

This document records the operational boundary after
`PR-PILOT-RELIABILITY-1`. It describes the current restaurant pilot, not the
complete source tree or future product roadmap.

## Enabled / Pilot Scope

| Area | Current pilot behavior |
| --- | --- |
| Authentication | JWT login and refresh with store-scoped authorization. |
| Ordering | Frontdesk table/takeout ordering with server validation. |
| Weak-network ordering | Versioned IndexedDB menu cache, persistent local drafts, idempotent server submit, and foreground outbox replay. Only server-confirmed orders are shown as received by the kitchen. |
| Printing | Printer Restaurant Mode with GRAB, FRONTDESK_RECEIPT, and HOT_KITCHEN jobs. Printing failure does not roll back an order. |
| PAD_DIRECT | Paired Android Pads claim assigned jobs and print to the job's assigned LAN printer under the existing duplicate-print safety boundary. |
| Menu management | Store-scoped item, option, price, availability, and sold-out management. |
| Menu item ordering | Category-scoped up/down ordering persisted in `menu_items.sort_order`; catalog revision and IndexedDB refresh carry the new order to ordering clients. |
| Cloud deployment | The `deployment/cloud` package remains the production deployment template; deployment requires operator review, backup, and smoke tests. |

## Out of Scope / Disabled

| Area | Boundary |
| --- | --- |
| KDS | Disabled for Printer Restaurant Mode and unchanged by this batch. |
| Pickup display | Disabled with KDS and unchanged by this batch. |
| Inventory | Not part of this pilot reliability batch. |
| Platform Admin | Existing code remains present, but no Platform Admin feature work is included. |
| Redis / multi-backend scaling | Not introduced. |
| Payment and refund | No payment or refund behavior changed. Offline payment is not supported. |
| `completeOrder` | No semantic or lifecycle changes. |

## Reliability Invariants

- A local draft or queued outbox record is not a server order.
- Automatic and manual retry reuse one stable idempotency key and one in-flight
  request.
- Server replay returns the original order; payload conflict stops automatic
  retry and requires review.
- Print failures remain visible and do not roll back submitted orders.
- Menu caches are isolated by account, organization, store, and revision.
- Menu item ordering is isolated by store and category.
