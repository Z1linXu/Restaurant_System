# Current Restaurant Pilot Scope

This document records the pilot product boundary and current implementation
contracts. It does not certify a deployment: environment SHA/schema/acceptance
belong to fresh runtime evidence and `governance/CURRENT_STATE.yml`. The five
issue repair is bounded to implementation and authorized Staging validation;
Production mutation remains separately gated.

## Enabled / Pilot Scope

| Area | Current pilot behavior |
| --- | --- |
| Authentication | JWT login and refresh with store-scoped authorization. |
| Ordering | Frontdesk table/takeout ordering with server validation. Base plus per-item option deltas is multiplied by line quantity; initial submit and add-only Update serialize the same frozen quote. Tax remains 14.975% with existing rounding. |
| Weak-network ordering | Versioned IndexedDB menu cache, persistent local drafts, idempotent server submit, foreground outbox replay, and an actionable frontdesk queue. Only server-confirmed orders are shown as received by the kitchen. |
| Printing | GRAB, FRONTDESK_RECEIPT and HOT_KITCHEN retain separate wording. Frontdesk explicitly shows frozen soup selection and allocates stored line cents per bowl; configured NO_SPICY kitchen text wins before legacy suppression. Printing failure does not roll back an order. |
| PAD_DIRECT | Paired Android Pads claim assigned jobs and print to the job's assigned LAN printer under the existing duplicate-print safety boundary. |
| Menu management | Seven tabs: Menu Items, Add-ons, Categories, Stations, Combo Configuration, Pricing Rules, Printing Display Rules. Add-ons contains Add-ons/Removes subnavigation. Free Removes have Store-owned names and category inheritance with item overrides; the old Printing Settings page links to the same-Store wording tab. |
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
- Print Center operators can acknowledge the current attention snapshot without
  changing a print job's status; a later retry, error, or status change becomes
  visible again.
- Menu caches are isolated by account, organization, store, and revision.
- Menu item ordering and modifier applicability are isolated by Store/category;
  item overrides take precedence. A private Store rule/catalog edit never
  changes another Store or published Master/Profile.
- Menu refresh/recovery preserves captured draft prices/options; new submission
  still validates current eligibility and canonical-price rules.
- Old order amounts, kitchen instructions and rendered jobs are not rewritten.
  Source-job reprint copies frozen text; full order reprint uses persisted order
  snapshots. Missing old soup selection is unrecorded, never an assumed broth.
- V38 adds schema only; legacy Remove reconciliation is explicit and leaves
  ambiguous names unresolved. No automatic operational-data cleanup is implied.
- The frontdesk can continue a local draft, retry a queued order, check a
  submitting result, or cancel only a locally safe record. It does not turn a
  `SUBMITTING` or uncertain retryable result into a new editable order.
