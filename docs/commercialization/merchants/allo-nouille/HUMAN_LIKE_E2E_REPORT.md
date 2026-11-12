# Human-like browser acceptance — 2026-11-12

Status: **CHANGED-SCOPE ACCEPTANCE PASS WITH EXPLICIT TOOL COVERAGE LIMITS**. This is actual browser evidence,
not a claim that API tests cover unexecuted browser actions.

## Environment and source

- Initial app source: `c2425481ec25257660fa1a73d9ed0ec284eb5fe2`.
- Resumed backend: `23f3e804713ad633f1d95f10d1b0cfebfcf31edf`; frontend executable source unchanged fromc242.
  Read-transaction repair and actual concurrency evidence are in [this report](MENU_OPTION_READ_CONCURRENCY.md).
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
| Finish | Initial synchronous confirmation blocked browser control; later Owner accepted it and18:25follow-up observed completed/history36.17 and4free/0occupied | PASS_OWNER_ASSISTED; see follow-up below |

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

## First-session browser blocker (later risk classification supersedes release blocking)

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

## Resumed real browser work, 14:55–15:46 UTC

Input recovered after the previous temporary tab was cleaned up. The same
selected browser2 and QA tab4 performed these actual actions. All writes below
remain synthetic; no Production order, price, print or device action occurred.

| Area | Actual operation/result | Status |
| --- | --- | --- |
| Add-on availability | Disable canonical beef; reopen existingOrder8 and newB1 customization: current beef absent, locked historical choice retained. Restore enabled through UI. [Evidence](evidence/qa-resume-addon-disabled.jpg) | PASS |
| Shared Removes | Catalog has0 records. Open Add Remove form, fill a synthetic draft, Cancel. No record/selection invented | PASS_FORM_ONLY; configured selection NOT_CONFIGURED |
| Station | Edit唯一 BACK_KITCHEN Chinese name to合成后厨QA, Save, reload confirms, restore后厨 through Save. [Evidence](evidence/qa-resume-station-saved.jpg) | PASS |
| Combo Configuration | New group form accepts translations/rule draft, discarded by reload. Optional egg panel initially exposed pool starvation; after23f3 repair entire panel loads without Request timed out. [Evidence](evidence/qa-resume-combo-loaded-fixed.jpg) | PASS_LOADED; no fixed offers configured |
| Printing Display Rules | Save DRAFTv3 alias合成打印名QA, reload retains it, validationPASS, actual generic preview renders alias; activev2 unchanged. [Evidence](evidence/qa-resume-display-rules-preview.jpg) | PASS_DRAFT; generic legacy preview does not certify frozen Allô renderer |
| Option Group Library | Select existing加汤1.99, verify name/price actual DOM readonly, edit group translation draft; reload discards. [Evidence](evidence/qa-resume-option-library.jpg) | PASS_DRAFT_ONLY; full Save confirmation NOT_RUN |
| PAD policy | Select/save PAD_DIRECT, visible current mode succeeds; REAL rejected and mode unchanged. RestoreMOCK.0pending/0PRINTING,3revoked protocol devices. Ordinary browser pairing disabled | PASS_BACKEND_POLICY; REAL rejection message is generic500/P3 |
| Printer Assignment | Takeout copies2→Save, wait success, reload confirms selected2; restore1 and await success. No direct-test print | PASS |
| Expected failed Job | Open failedJob2 stored receipt; HTTP_NO_PRINT_ATTEMPT marker retained. No retry/ack or ambiguous reprint | PASS |
| Saved update receipt | OpenJob22 at768×1024: E1, subtotal3.99, order-rounded tax0.59,total4.58. [Evidence](evidence/qa-resume-receipt-preview-768.jpg). OldJob18 at1024 retains historical6.88 | PASS |
| Original MOCK reprint | One click on sourceJob22 createsJob29; actual preview preserves originalE1/0.59/4.58. [Evidence](evidence/qa-resume-mock-reprint.jpg) | PASS; no physical transport |
| C6 two-protein selection | One protein blocks Add; two enable Add and disable third; quantity2→1. [Evidence](evidence/qa-resume-c6-two-proteins.jpg) | PASS |
| Takeout Submit | TO-7197ZN actualOrder11: C6+A2(two-roll fullportion)+D1+E1; subtotal30.96/tax4.64/total35.60. Server-confirmed UI returned to board | PASS |
| Order History | Actual11orders; newTakeout shows two proteins/fouritems/35.60. Order8 retains previous synthetic beef snapshot/36.17; whole-order reprint rejected with original-ticket direction | PASS |
| Long saved preview | Job30 actual fouritem receipt; at1024×768 pre height538/scrollHeight680. KeyboardEnd movedscrollTop142; totals35.60 and fixed Close reachable. [Top](evidence/qa-resume-long-receipt-top-1024.jpg), [bottom](evidence/qa-resume-long-receipt-bottom-1024.jpg) | PASS |
| Actual GRAB routing | Order11Job31 containsC6(two proteins),A2,E1; excludesD1. Job30frontdesk containsall4. [Evidence](evidence/qa-resume-grab-routing.jpg) | PASS |
| Finish retry | Only occupiedTEST1, Order8. Click with concurrent supported getJsDialog; no actionable dialog returned, input timeout; later screenshot/close also timed out | TOOL_BLOCKED; completion/table release NOT_OBSERVED |

Saved preview displays the stored PrintMarkup text in a monospace scroll area;
this is actual UI snapshot proof, not paper typography/physical output proof.
No code or browser storage/auth injection was used to bypass the confirmation.
The computer-use safety layer also refused native Codex window access, so no
unsupported OS automation was attempted. An asynchronous user question requests
only dismissal/acceptance of the ordinary QA dialog to recover browser control.
It is not another release approval request.

Remaining browser-dependent coverage gaps are Frontdesk-role normal browser login/scope checks and full library Save. Finish/table-release and completed history were subsequently verified after Owner confirmation, as recorded below.
There are no configured fixed offers/Remove selections/free Add-on records to
exercise; this is reported as NOT_CONFIGURED, not a fabricated PASS. Actual CLI
role/device/isolation tests remain separate evidence. Independent risk review classified these remaining cases as non-blocking
coverage limits under Owner request§8.6/§11.1 and AGENTS risk-proportionate proof.
The baseline2b44→23f3 Finish client/controller/service/locking/board refresh paths
are unchanged; the synchronous confirmation happens before API invocation and
has no dependency on the new menu read, renderer or printing policy paths.
Actual Orders2–7 and9/10 already returned complete200/completed in the same
isolated runtime lineage. Frontdesk normal HTTP login/Bulk403 passed; auth/login
source is unchanged. Library Save changed, so it relies additionally on existing
JPA11canonical/read/write/stale/link/rollback and scoped frontend payload tests;
its end-to-end UI Save remains NOT_RUN. No known save failure is waived.
See [independent classification](INDEPENDENT_REVIEW.md). These limits permit
release of the verified changes without claiming all browser cases passed.

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
All original Orders1–8 and Jobs1–18 remained byte/row-hash identical; At this earlier point Order8
remained active; the18:25Owner-assisted confirmation follow-up below closes that gap. See
[actual update amount proof](UPDATE_RECEIPT_AMOUNT_REGRESSION.md).
Print Center was completed during the resume above; Finish subsequently completed with Owner-assisted native confirmation.

## Owner-assisted Finish follow-up — 18:25 UTC

Owner reported clicking the pending native confirm. The same browser2/tab4 was
readable again. Actual frontdesk AX/DOM showed `TEST 1订单已完成。`, all4dine-in
Tables idle and0occupied. Opening Order History and selecting `TEST 1 completed
堂食` showed已完成, the same B1/D1/E1 snapshots and totalCAD36.17. No new Finish,
order submit/update/reprint or database write was issued by the Agent in this
follow-up. The existing takeoutOrder11 remains preparing and is independent of
the dine-in table count; no claim of all QAorders completed is made.

Result: **PASS_OWNER_ASSISTED_NATIVE_CONFIRM_AND_BROWSER_READBACK**. This was
not fully automated confirmation. Full AX/DOM evidence is retained privately;
the native screenshot surface captured only the sidebar, so no full visual
screenshot certification is claimed for this follow-up. The previous viewport
override was successfully reset. Concise identifiers/proof digest are in
[the follow-up evidence](evidence/qa-finish-owner-assisted.json).

Only Frontdesk-roleUIlogin and LibraryfullSave remain NOT_RUN_TOOL_LIMIT.
Production was already deployed; this follow-up did not query, restart,
redeploy or mutate Production. No code or test implementation changed.
