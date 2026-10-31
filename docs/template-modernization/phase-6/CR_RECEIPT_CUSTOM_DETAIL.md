# CR-RECEIPT-CUSTOM-001 — Zero-price custom detail on frontdesk receipt

Status: PROPOSED / NOT_AUTHORIZED / NOT_IMPLEMENTED. This does not expand Phase6 or authorize a protected-core edit.

## Concrete limitation

At the fixed baseline and candidate1a07e1f, protected `backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java:378` (`resolveChargeableOptionLines`, filter at380) includes ordinary modifier lines only when price_delta>0; existing dedicated Size/Spicy/Soup/Combo branches cover their own semantics. A new zero-price CUSTOM group does not fit those branches and its individual label is omitted. Full items, totals/tax and existing Update delta semantics remain unchanged. Real HTTP acceptance explicitly records this limitation; the new kitchen tickets retain the zero-price preparation instruction.

## Approval requested for a later bounded slice

If Owner requires that label on the frontdesk ticket, separately approve a narrow template-aware display extension in the protected frontdesk Renderer (or a demonstrably equivalent approved view seam). It should read the existing immutable option snapshots, add each selected generic instruction once under its item, and preserve legacy orders/layout/totals and Update batch selection. No PrintDispatcher, PAD_DIRECT, Job dedupe/state or history migration change is proposed.

Retagging CUSTOM as a different business option cannot overcome the existing positive-price filter and misstates its identity; fabricating price or stuffing notes is not acceptable. Rewriting rendered text around the protected path would also create unaudited formatting behavior. This report therefore leaves the detail partial instead of bypassing the protected boundary.

## Required acceptance before any future implementation

- All existing three-receipt text/bytes goldens remain exact for legacy/non-template inputs.
- Synthetic zero-price CUSTOM selections appear once under the correct item, including Update only for the added batch.
- No item/option amount, tax, totals, parent identity or historical snapshot changes.
- Duplicate dispatch, uncertain result protection and PAD payload parity retain existing checks.

No estimate, PR implementation or deployment of this slice is approved by this document. Current Test remains MOCK-only; physical paper/Pad output is NOT_TESTED.
