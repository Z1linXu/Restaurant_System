# Human-like browser acceptance — 2026-11-12

Status: **PARTIAL / RELEASE GATE BLOCKED**. This is actual browser evidence,
not a claim that API tests cover unexecuted browser actions.

## Environment and source

- App source: `c2425481ec25257660fa1a73d9ed0ec284eb5fe2`.
- Dedicated worktree: `codex/allo-production-improvements-20261112`.
- Browser: controlled Chromium in-app browser, existing browser ID 2;
  initial QA tab 3, recovery QA tab 4.
- Browser URL: `http://127.0.0.1:28182`; proxy backend `127.0.0.1:18512`.
- New synthetic PostgreSQL `127.0.0.1:26512`, Flyway V42;
  `allo-test,allo-qa` exact profiles, independent synthetic credentials.
- Store banner: **SYNTHETIC Allo QA**, Store/Org 1/1 inside this QA database.
  This is a separate database from Production Store/Org 1/1.
- QA namespace `allo-qa-20261112`; Production build separately retains
  `allo-nouilles`. No production recovery dump was used for QA.
- Store remains MOCK, configured printers have no endpoints. No native Android
  worker or physical printer was connected.

Actions below used supported `getByRole`/`getByText` locators, real clicks,
form inputs and fresh AX/DOM observations. No browser-evaluated hidden API,
authentication/storage modification or jsdom result is counted as browser proof.
Private chronological evidence is `qa-browser-trail.json` in the restricted
release evidence directory; committed images are unchanged browser JPEG bytes.

## Actually executed interactions

| Area | Actual operation and observed result | Status |
| --- | --- | --- |
| Owner authentication | Synthetic Owner password login; dashboard and merchant banner loaded; navigation to Menu Management | PASS |
| Item list/search/code | Search `C6`, edit Chinese name to `合成双拼 QA`, change 19.99 → 20.49, save; observed `C6 合成双拼 QA`, `AN_C6`, 20.49. Reopen retained original French. Restore original Chinese name and 19.99 through the same form | PASS |
| Categories and routing | Five ordered categories; existing one `BACK_KITCHEN` target. Drink kitchen checkbox disabled. Dessert checked → unchecked → save → reload remained unchecked → checked → save. No category creation/duplication | PASS |
| Batch increase | Check Category 2 and 3, enter CAD1.00, Preview. Backend returned 14 eligible B/C items, B1 16.99 → 17.99, C6 19.99 → 20.99; expand excluded list showed free noodle/rice refills. Confirm Save succeeded, refresh retained capability/UI | PASS |
| Frontdesk price refresh | Return to frontdesk, start TEST1 local dine-in draft, select Noodle category. Card showed B1 17.99 and other B prices +1; A prices remained original | PASS |
| Batch decrease | Return via user menu, select the same two categories, Decrease CAD1.00. Preview showed 14 reverse changes. Confirm Save restored approved base prices; secondary DB read confirmed both ledger rows and historical invariants | PASS |
| Add-on edit/current authority | Shared Add-ons listed 13 entries. Edit `n_extra_beef` Chinese/English to synthetic QA labels and price7.99 →8.49; save and refresh. B1 customization displayed the new names and +8.49 while other Add-ons stayed7.99 | PASS |
| Item customization | Open B1 modal; select thin noodles, add one canonical beef portion; increase/decrease item quantity2 →1; input synthetic preparation note; add to cart | PASS |
| Draft editing | Reopen cart item; change thin → thick noodles; update item. Cart retained price25.48 and changed noodle choice | PASS |
| Dine-in Submit | Submit TEST1 draft. Table returned occupied, with Edit/Print/Finish controls. Backend accepted synthetic order | PASS |
| Append Update | Edit submitted order; submitted B1 fields locked. Add D1 water and E1 ice cream, Update Order. Returned confirmed occupied table; original item remained frozen | PASS |
| Reprint guard | Open Print, choose FRONTDESK_RECEIPT. UI rejected whole-order reprint of an updated order and directed the user to original tickets in Print Center, preventing omitted additions | PASS |
| Finish | Click Finish. A synchronous ordinary confirmation blocked browser control; no completed status/table release was observed | NOT RUN TO COMPLETION |

All price/configuration writes above were synthetic QA writes. No Production
sale, price change, test order or reprint was made. The later narrowly scoped
API cleanup of the temporary Add-on is recorded separately in the routing
report; it is not represented as a successful UI restoration.

## Actual layouts inspected

| Viewport | Actual evidence | Observation and limit |
| --- | --- | --- |
| Desktop1280×720 | [Pricing preview](evidence/qa-pricing-desktop.jpg), [item save](evidence/qa-single-item-save.jpg), [categories](evidence/qa-dessert-off.jpg) | Navigation and controls were reachable. Item table has horizontal scrolling; the save screenshot is scrolled to its action/price columns, so item-code assertions also rely on the actual DOM observation. Category screenshot is above the kitchen-routing controls; the persisted Dessert state was verified through AX/DOM after reload |
| Tablet1024×768 | [Pricing](evidence/qa-pricing-1024.jpg), [Frontdesk refreshed price](evidence/qa-frontdesk-price-sync-1024.jpg) | Category selection/preview and POS category navigation operated at this explicit viewport |
| Tablet768×1024 | [Price preview](evidence/qa-pricing-preview-768.jpg), [saved Add-on](evidence/qa-addon-saved-768.jpg) | Tabs wrapped; category selection, amount, preview and confirm/save were reachable. Actual B1 modal was visually inspected, including 2-column modifier counters, scrollable content and fixed Add button; no screenshot file was saved for that modal |

The screenshots cover the listed screens, not every module at every viewport.
No claim is made that unvisited Print Center previews are visually certified.

## Concrete browser blocker

At approximately08:18 UTC, clicking `完成订单` reached the existing
`window.confirm` in `DineInPage.handleFinish`. The controlled browser's input
command then timed out. Supported `getJsDialog()` did not provide an actionable
dialog and the affected tab's focus, close and normal navigation operations
timed out. A fresh tab in the **same selected browser** could render/read pages,
but clicks left their state unchanged. Rebinding after the tool reset did not
restore input. No undocumented CDP or OS mechanism was used to bypass this.

An asynchronous request asked the user to dismiss/accept this ordinary synthetic
QA confirmation manually. This is a tooling handoff to recover input, not a
new deployment-authorization request. Until input is restored, remaining
interaction cases below stay NOT RUN and the release gate stays open.

## Remaining browser cases

- Option Group Library and representative item eligibility/availability edits.
- Shared Removes; actual source has no configured Remove products. A missing
  configuration must not be counted as a successful selection.
- Station editing, Combo Configuration interaction and Printing Display Rules.
  Actual approved Allô configuration contains no fixed Combo offer; the five
  renderer tests use explicitly documented approved-item equivalents.
- Takeout ordering, completed Finish/table release, Order History interaction.
- Print Center saved-snapshot scrolling, original MOCK reprint, expected-failed
  Job visibility and Update ticket UI inspection.
- Frontdesk-role browser login and scope negative pages; real HTTP role/device/
  Store negative tests passed independently and do not replace these UI cases.

No product defect is inferred solely from the browser-tool dialog failure, and
no unexecuted action is marked PASS. Resume by restoring browser input, recheck
the active synthetic order/current data before any click, finish the above
cases, then refresh Production backup/queue/runtime checks before release.

## Amount discrepancy found and repaired after browser execution

The browser-created Order8 exposed a real receipt reconciliation issue in the
existing batch-rounding contract: initial receipt29.30 +update6.88 =36.18,
versus authoritative order36.17. This was not waived as an old behavior.
Commit `e7dde0a6e27adf3be00baa9972fee06cdcc5482c` freezes the rounded order delta
for new Allô update receipts; the corresponding update is6.87/tax0.89.
Orders/old Jobs are not rewritten.

Backend follow-up:1519 tests total,1402 passed/117 skipped/0failures; actual
new-JAR HTTP/renderer acceptance and independent review PASS. The new test
orders were created by API and are explicitly **not** additional browser cases.
All original Orders1–8 and Jobs1–18 remained byte/row-hash identical; Order8
remains active until the browser handoff is resolved. See
[actual update amount proof](UPDATE_RECEIPT_AMOUNT_REGRESSION.md).
The missing browser Finish/Print Center interactions remain NOT RUN.
