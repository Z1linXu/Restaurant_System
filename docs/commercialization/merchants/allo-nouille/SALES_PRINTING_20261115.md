# Allô sales / printing delivery — 2026-11-15

PRIMARY_REPAIR_GOAL: Deliver the Owner's bounded Allô printing, financial adjustments,
sales overview, Takeout completion/repair and structured-spice removal request.
EXPECTED_REPAIR_CLASS: HIGH

Owner request: attachment 461d7ddd-09bc-48b2-a52d-7de6eb4bde38, explicitly authorizes
Allô-only implementation, isolated tests, backups, bounded data repair, deployment and APK.
No Lanzhou mutation or retired shared Test/Staging restart. Existing Allô runtime
is the baseline, not main. Production release manifest and checks remain required;
this request supplies deployment authority inside its stated compatibility boundary.

Ground truth: origin/main 80e888fcdad74e98326fcbeaa5df14bc0d9baed6;
clean Allô branch HEAD 7490694e80316156ffbdd5f62a2eb4e66c708bd5 (2 main-only /40 Allô-only).
Target verified read-only: Store1/org1 code ALLO_NOUILLES_TEST / Allô Nouilles,
project allo-nouilles, DB restaurant_allo_nouilles_test, backend4c75f960,
frontend b78ab21d, V43. 8 pickup/preparing observed. No device connected.

APK impact: bundled Allô1.0.3/code4, com.allonouilles.pos. New UI requires new assets
and compatible APK update; server-generated ESC/POS is sent as bytes by existing
Worker, with no module/type whitelist found. Complete responses already terminate
local drafts; verify regression before server release. No IndexedDB schema change
planned. New spice choices use menu revision; immutable old outbox hashes survive.

Money: gross effective stored lines, one tax-before discount, net=gross-discount,
tax=HALF_UP(net*current established14.975%,2), total=net+tax, collected=total only
for completed valid orders. Reports sum determined order tax, never daily tax.
Original line snapshots remain; partial voids use retained cumulative void quantity.
Allo-only services use the exact existing independent-runtime opt-in. No tenders.

Product simplicity: one order menu, explicit amount confirmation, no PIN/approval,
no financial offline queue, shared server presets. No new user lifecycle.

Status: IN_PROGRESS; no Production mutation yet.

Agent 6 consolidated review: PASS; initial five finding clusters (combo whole void,
double void projection, front modifier quantity, ordering confirmed amounts,
rule/printing-policy history) repaired and tested. Actual API concurrency200/409,
ledger failure rollback, original artifact hashes, preset isolation, original-day
limits, old net display/new update gate and legacy committed batch replay PASS.
Data repair replay zero writes and stale spice Outbox/new selection PASS. Existing
Pad report protocol including GBK payload/hash/XS1D2111/one copy/zero TCP PASS.
Physical paper NOT TESTED. No connected USB device at initial adb observation.

Release procedure reuses existing Compose/images. V44 first starts with compatible
old Web, then one bounded stop after migration backs up exact repair targets and
applies/replays the reviewed SQL. A try/finally always starts the same new backend;
health precedes Web publication. No DB container restart, queue reset or test data
injection into Production. No retired shared Test/Staging starts.

Recovery: pre-migration custom dump with87 tables has been socket-only restored,
row hashes and constraints verified. Preserve V44 and post-release orders. After
any financial adjustment, old backend rollback is unsafe because it ignores void
quantities/rules; forward repair is required. Frontend-only rollback keeps the new
backend. Before any backend recovery require no CLAIMED/PRINTING physical attempt.
The per-release pre-repair-targets.json retains exact old order/config fields for
revision-guarded compensation with an additional ledger record, never whole-DB
restore, old-ticket replay or deleting the original adjustment.

Android update follows the official same applicationId/signing certificate and
higher versionCode requirements: https://developer.android.com/build/configure-app-module
XS uses the existing supported integer character-size mode, not arbitrary scaling:
https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_exclamation.html
