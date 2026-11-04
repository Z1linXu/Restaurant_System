# Uber authoritative order totals: six-order follow-up

PRIMARY_REPAIR_GOAL: recognize trusted merchant post-promotion order totals independently of unresolved item allocation/component rounding, preserve all historical snapshots/journal events and prepare a six-target compensatable correction preview without Production mutation.
EXPECTED_REPAIR_CLASS: MEDIUM.

Start2026-11-04T04:06Z. Fresh origin/main80e888fcdad74e98326fcbeaa5df14bc0d9baed6;
clean reused branch codex/uber-analytics-pending-production at PR307 head
e5b13776fafca68bc2e0c2699d032045c31eda1c (main divergence2/2). Runtime reverified
27612b6f72dabc48e0167cc7d214a3ece565dd5e, backend1e06e79b..., nginx18dd0fa...,
DB unchanged/healthy, Flyway39.1, eight APPLY records, no REVOKE. Current PhaseB
bounded Owner-authorized follow-up; implementation/tests/review/preview only.
No deploy/merge/Production schema or data change, no Staging/Test activation.
Prior completed release evidence is immutable; this document covers the follow-up.

Contract: existing SYSTEM_DOCUMENTATION channel-sales and doc/API contract,
with the Owner's new order-total-vs-item-completeness rule. Preserve no-promo,
v1/v2, Store/environment/date eligibility, printing/webhook/Outbox/task behavior.
No performance, Inbox, Allô, archiving or general deployment tooling change.

Official source: https://developer.uber.com/docs/eats/references/api/v2/get-eats-order-orderid
Charges.total_promo_applied is the order cost after merchant-funded promotions,
including applicable taxes/fees, distinct from eater cash due and final platform
settlement. Non-merchandise/unrecognized fees or material financial conflicts
must still block. Exact saved amount is never adjusted to reconcile components.

Fresh saved six totals are5114,7066,7354,4769,3274,3964 CAD cents =31541 cents.
Owner forecast26741 differs4800 cents; no evidence currently supports changing
an authoritative field by4800. If all pass, known/current Uber140306 ->171847,
Total404231 ->435772, POS263925 unchanged, counts106/60/46 unchanged.
This is a read-only preview expectation, not a deployed result.

## Validation and bounded scope

CONFIRMED: fresh Production source/original MD5 matches the retained financial-only
sources for all six; none has a correction row yet. Production baseline at04:22Z:
106 orders (60 POS/46 Uber), POS263925, known Uber140306, known total404231 cents,
6 unknown order amounts/18 unknown root-item amount rows. All eight Staging/Test
containers remain stopped. This round performed read-only metadata, bounded
financial queries and authenticated aggregate GETs only.

Backend full1254:1137 executed PASS,117 existing conditional skips,0failure/error.
Focused31 PASS with actual isolated PostgreSQL41 migrations, two independent v3
preview JVMs, legacy v2 eight + new v3 six apply/replay/revoke, Store/environment
isolation, changed-evidence failure and independent compensation; entire original
business rows and old v2 journal byte fingerprints unchanged. Old V39 and current
V39.1 Flyway validation against additive V39.2 PASS. One test-fixture retry fixed
incorrectly filtering the old V39 migration from the rollback proof; no applied
migration was edited. Frontend374 PASS/build PASS/changed-file lint PASS.
Agent6 independent review ACCEPT, no P0/P1/P2; independent focused31 PASS/no skips.

Order totals remain confirmed when item evidence is absent; OTHER does not get
invented order-level revenue. Complete order totals/AOV coexist with null item
amounts/percentages. Non-promo compatibility, merchant/platform/multiple promos,
zero, unsupported fees/refunds, mixed currency, invalid/extreme money, +/-1 cent
and real >1 cent conflicts are covered. No print execution, webhook handling,
order state, polling, Outbox, Kitchen Tasks, Inbox or performance code changes.

## Six-target final candidate preview (not applied)

Source: saved merchant `payment.charges.total_promo_applied.amount`, CAD cents.
Delta = authority total minus saved post-promotion subtotal and tax. PASS means
order-level amount is valid; every item net amount remains PENDING_RECONCILIATION.
All six original v1 UNSUPPORTED_PAYMENT_DETAIL snapshots remain immutable.

| Audit ID | Saved subtotal | Saved tax | Authority total CAD | Delta cents | Order validation | Item amounts |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| U-e081b098 | 44.48 | 6.67 | 51.14 | -1 | PASS | Pending review |
| U-d40b43e1 | 61.46 | 9.21 | 70.66 | -1 | PASS | Pending review |
| U-284238ab | 63.96 | 9.57 | 73.54 | +1 | PASS | Pending review |
| U-31281cd2 | 41.48 | 6.22 | 47.69 | -1 | PASS | Pending review |
| U-6f6dfe76 | 28.48 | 4.27 | 32.74 | -1 | PASS | Pending review |
| U-ae3be476 | 34.48 | 5.17 | 39.64 | -1 | PASS | Pending review |

CONFIRMED: candidate31541 cents. Current known Uber140306 -> candidate complete
Uber171847; current known total404231 -> candidate complete total435772; POS263925
and orders106/60/46 unchanged. Candidate overall AOV41.11, Uber AOV37.36. Unknown
order totals6 ->0, item-pending orders6 and actual root amount rows18 remain.
Category known amounts stay partial and percentage pie remains incomplete.
UNKNOWN: evidence behind Owner's26741 forecast / the4800-cent discrepancy. No
source supports subtracting4800 from these six merchant totals. No customer-paid
or platform-payout fallback is used; platform funding is not subtracted twice.
Historical source funding splits are not retained, so detailed funding breakdown
remains UNKNOWN rather than being invented.

Preview was generated from financial-only evidence in a local isolated PG; it
never ran against Production. Original/source hashes were freshly matched against
Production using read-only PK lookups. Candidate batch SHA256:
`8c96d7b6a8fc3fa2bbaf1339c4ff3275224299a1fa9700f8c5ee0b08c6c41360`.
Expected mutation is six new v3 APPLY rows only, after future Owner release approval;
no original snapshot/order/item/print/outbox/kitchen update and no v2 row rewrite.
The original target preview is private; this table contains no external order ID,
customer information, payment credentials or Uber credentials.

## Production release proposal: NOT AUTHORIZED / NOT EXECUTED

1. Owner accepts the actual six-order preview and a non-business maintenance
   window. Freshly verify Production still matches27612b6/image1e06e79... and
   nginx18dd0fa..., DB16.14/V39.1, eight old v2 APPLY and zero corrections on these
   six. If runtime/source/business state differs, stop and regenerate evidence.
2. Recheck no active POS submissions, print/dispatch queues, processing Uber
   events or kitchen work; preserve historical task states and all paused IDs.
   Do not infer a future quiet window from the earlier Owner closure. Avoid the
   existing23:20–23:40 daily-close window. Keep existing resource/disk thresholds.
3. Create a NEW private custom Production DB backup at V39.1 including the eight
   v2 events; checksum/TOC plus actual isolated restore, ledger/row/financial/v2
   fingerprints must pass. The earlier pre-V39.1 backup is not this batch's backup.
   Preserve exact current images, parameters, private Compose and release marker.
4. Use only the reviewed candidate SHA and matching local JAR/static checksums.
   Build immutable image layers from the current protected runtime bases. Compare
   full resolved Compose/environment/mount/network/resource settings: only app
   image/OCI identities may differ. No Staging/Test or DB container replacement.
   The previous fourteen-target deploy.py is a frozen prior release helper and
   must NOT be invoked unchanged for this new six-target batch.
5. Controlled backend/nginx replacement only; single-instance replacement can
   interrupt HTTP and must be recorded. Flyway adds only V39.2 CHECK widening;
   lock timeout1s / statement timeout3s, transactionally fail closed. No other
   table/index/parameter change, no repair/reindex/vacuum or worker replay.
6. After HTTPS200/UP, DB healthy/restarts0, V39.2 exact ledger and paused/runtime
   continuity PASS, run the exact new JAR CLI preview with `--case order-total-v3`.
   Require the six PK identities/date/store/CAD/original+source hashes and preview
   batch SHA above to match. Any mismatch stops correction. Only then Owner-
   approved `--mode apply --case order-total-v3 --preview-sha <above>` may append
   the six validated events. Retrying identical input inserts0; revoked batches
   cannot silently reactivate; competing corrections fail closed.
7. Read-only acceptance:106/60/46, POS2639.25, Uber1718.47, Total4357.72, AOV41.11,
   unknown order totals0 / item-pending6 / actual root amount rows18, incomplete
   category percentages, original eight v2 events and all v1 bytes unchanged.
   Regress other two Stores and historical day, verify no new orders/items/print/
   outbox/kitchen records, no real order/print/Uber call, health and bounded logs.
   Natural concurrent business changes require rebaselining, not data adjustment.

## Independent recovery

Before financial APPLY, app rollback uses the saved27612b6 backend/nginx image
pair and exact saved private Compose; retain additive V39.2. The old V39.1 app's
Flyway compatibility is locally validated. Do not restart/restore the DB.
After APPLY, data recovery first uses the exact new candidate CLI:
`--mode revoke --case order-total-v3 --preview-sha 8c96d7b6a8fc3fa2bbaf1339c4ff3275224299a1fa9700f8c5ee0b08c6c41360`.
It appends six REVOKE events, preserves APPLY/v1/v2, restores the prior effective
six Pending and leaves the old eight v2 active. It works even after source or
business-state changes. Repeated revoke inserts0. Then roll app images back,
restore the saved canonical Compose/release marker, and repeat read-only checks.
DB outage or compensation failure must not prevent attempted application rollback;
record unresolved financial compensation explicitly and retry only after recovery.
Never delete journal rows, overwrite frozen snapshots, down-migrate the CHECK,
or use whole-DB restore as ordinary financial rollback. Backup restore is an
emergency independently approved recovery, not automatic deployment rollback.

PRODUCT REPAIR: existing financial policy/capture/read path, existing case-scoped
correction CLI, one version CHECK, Analytics completeness fields/UI and tests.
TOOLING / GOVERNANCE: no deployment framework/change; optional private financial
fixture/preview output in the existing isolated PG test, this plan and technical/
current-state sync only. No build or upload on Production occurred this round.
