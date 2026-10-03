# Final channel closeout — evidence (2026-10-03)

Scope: Owner-authorized Staging-only final12 mapping contexts, Add-on canonical
reconciliation,23:30 Store-local Finish, and Dashboard/Reports channel sales.
PRIMARY_REPAIR_GOAL: complete this bounded product batch without Production mutation.
EXPECTED_REPAIR_CLASS: HIGH. Baseline main `c4b84883d44dc46436e3ae8f95fc95869ad77db6`;
observed Staging `ad3de48e973298604362cf21f119434d0b3f85f6`, Flyway V34.

## Product implementation and local validation

- 8 egg MAP and4 vegetable-noodle/Combo NO_OP contexts are Owner-confirmed;
  exact data batch in `deployment/cloud/final-channels-20261003/approved-modifiers.json`.
- 11 existing `tea_egg` options retain IDs,1.99 prices and active state; canonical
  加卤蛋 / Extra Tea Egg. Existing extra_meat canonical remains unchanged.
- Inactive unlinked options without active child/open-order references leave the
  active conflict catalog; historical rows and snapshots remain unchanged.
  Actual active ambiguous option370: item25/braised_beef_noodle, 加蛋 / Extra Egg,
  code/group null, price1.99. No unique egg identity was asserted.
- Auto Finish23:30 Store-local America/Toronto, including EDT/EST, durable ledger,
  restart catch-up and duplicate protection. No new physical-order acceptance.
- V35 adds a financial-only JSON column. Uber actual root totals include quantity
  and modifiers; financial calculations never consult local prices. Verified
  acceptance freezes the first snapshot, including orders held for mapping.
  Invalid/unsupported money remains unknown while valid quantities remain counted.
- Shared channel projection feeds Dashboard and Sales/Items/Store reports; legacy
  profit data stays in-store. New scope guards prevent cross-organization and
  restricted Store comparison leakage; global ADMIN semantics remain unchanged.

Backend:921 tests,0 failures,0 errors,7 existing optional skips. Includes actual
PostgreSQL Uber49 and daily-close8 tests. Frontend:47 files/247 tests PASS;
build and targeted ESLint PASS. Credential pattern scan1057 source/tool files,
no high-confidence secret hits; reviewed changed code reads private values only
from existing secure runtime environment. Governance validation/diffcheck PASS.
Agent6: ACCEPT; no remaining P0/P1/P2. Fixed findings covered by regression:
organization scope, release-only eligibility, held immutable financial history,
and exact-artifact financial backfill provenance.

## Financial source audit

Live Testing GET for B8E83 returned21 roots summing22339 CAD minor units;
charges subtotal22339 + tax3345 = total25684. Item price and modifier price exist.
The old normalized snapshot had discarded item prices; the original charges are
retained. Basis: MERCHANDISE_INCLUDING_TAX_EXCLUDING_PLATFORM_FEES_TIPS.
Actual order tax is allocated by actual Uber root amounts, with exact minor-unit
reconciliation; this is not Uber payout/profit or a claimed supplied item tax.
Unsupported fees/promotions/accounting or mismatches remain unknown.
Official semantics: [Get Order v2](https://developer.uber.com/docs/eats/references/api/v2/get-eats-order-orderid).

## Operational boundary

Reviewed menu batch writes only exact approved rules/catalog fields after a full
Staging backup. No remap/replay is called. Financial backfill uses actual Sandbox
GET responses matched against frozen IDs, recursive modifiers, placement and
charges; only the new empty V35 column can change. Original fields, orders,
inventory and print history are hash-checked within the transaction.

The Java bridge reuses the exact application's capture algorithm. Runtime JAR,
DTO classes/Jackson dependencies and bridge source/class manifest are verified.
Its temporary container has no network, read-only filesystem,128 MB/0.5 CPU limit,
and no credentials. This bounded tooling is required to preserve historical raw
snapshots without adding a maintenance endpoint or duplicating financial logic.

Deployment reuses the reviewed Staging helper with current exact old images and
only additive V35 expected. Backup/rollback preserve current private environment,
TEST binding, existing Pad/printer configuration, DB container and Production.
Pre-build disk65% used/21GB free; cache14.92GB (9.953GB reclaimable). Cache review:
no disk pressure, small immutable-artifact layer addition; no cleanup authorized
or executed, rollback images and DB volumes retained.

## Menu transaction and review follow-up

PR262 merged2026-10-03T20:43:00Z as `9c832cae9704033aea6c504e174c41f5129f8bee`.
The repository has no required status checks/workflow; local tests and Agent6
passed, and merge used the exact reviewed head with normal merge policy.
Application source at merged SHA is identical to the tested branch artifact.

At2026-10-03T20:44:57Z independent readback confirmed299 mapping rows,
11 canonical tea_egg links, and exactly one batch audit row. Original B8E83
snapshot/note/print evidence is unchanged. The transaction's historical table
hash and old-field assertions passed before COMMIT. The script then failed to
parse PostgreSQL's multiline JSON acknowledgement; it did not rerun the write.
The output parser was repaired to decode one complete JSON object and independently
reviewed ACCEPT (2 valid and7 rejection samples). This tooling-only repair does
not require an application rebuild.

SCOPE_EXPANSION_CHECK: this is a HIGH product batch across mappings, catalog,
scheduler, financial ingestion, analytics and UI. Operational code mostly reuses
reviewed one-shot deployment/data guards. The two bounded data actions require
exact transaction checks because the ordinary mapping PUT replays old orders,
and old normalized snapshots lack item prices. The small Java bridge reuses the
application algorithm rather than adding a maintenance API or a second financial
calculator. Generic migration/operations framework work remains deferred.

## Deployed Staging acceptance

Backend `9c832cae9704033aea6c504e174c41f5129f8bee` and additive V35 passed.
Backend image `sha256:22a92693efd3c5b2ec4244883acf515a40a48cc258331088752bdee7165c8053`;
JAR SHA256 `7df5a4f0c536bab5e6b7aee2ce753b49f30cfe2d48f2ded71f22ec5ad8613291`.
Full pre-deploy backup/rollback remain under the private Staging
`final-channels-20261003` directory. Data backups are
`state/final-channels-menu-20261003.dump` and
`state/final-channels-financial-20261003.dump`; archive readability passed.

At20:47:13Z financial apply completed:13 empty rows,13 real Testing responses,
13 identity-matched complete financial snapshots,13 written,0 blocked. Original
fields/order/print history hashes unchanged; no remap/replay or print was invoked.
Existing289 POS summary pairs did not require rebuilding: channel sales are a
live projection of existing POS truth plus the additive immutable Uber snapshot.

At20:49:02Z automated exact-runtime acceptance PASS:
- Health UP; Owner login/Store workspace PASS; Staging Sandbox TEST pair preserved.
-299 rules =41 root MAP +238 modifier MAP +20 NO_OP;12 current-context read-only
  previews PASS, including no REMOVE/raw warning from vegetable-noodle NO_OP.
- Add-on conflicts2→1; canonical extra_meat/tea_egg PASS; only option370 remains.
- Today Uber:5 eligible orders,45 root quantity,CAD646.92,0 unknown financials;
  direct database evidence equals Dashboard and Reports.
- Week/Month:POS1 order/1 quantity/CAD19.53 + Uber13 orders/54 quantity/CAD885.92
  =14 orders/55 quantity/CAD905.45. Category quantities and cent amounts reconcile.
- Today13 hourly buckets10–22; Week7/Month31 daily buckets; Report totals match.
- Frontend routes and printing page PASS, Store remains PAD_DIRECT. Historical
  PRINTING200/205/207 and failed jobs remain unchanged; no physical-print claim.
- Production containers/DB fingerprint, environment digest, start times and
  V28 ledger unchanged. Staging DB container unchanged.

Browser acceptance: Dashboard Today/Week/Month, Total/In-store/Uber Donut changes,
Reports Sales and Item Sales channel tables, canonical Add-ons and one genuine
remaining yellow row, Frontdesk, Uber Today, and printing settings loaded.
Actual CSS viewport768 and1024 both have equal page scrollWidth; no horizontal
page overflow. Temporary viewport overrides reset. Historical Uber #2818E/#B8E83
retain their frozen partial-mapping warnings and old egg labels, even though new
mapping is resolved; no historical order or rendered ticket was rewritten.

Browser review found the legacy POS-only alerts lacked a channel label. PR263
adds only In-store panel/subtitle wording; focused Dashboard2 tests, ESLint,
frontend build and Agent6 ACCEPT. Backend calculations are unchanged. Frontend
application SHA is `22a71b050371a319a912a025d5252604346539ea`; frontend-only deploy
uses reviewed existing Compose modeling, exact image/archive/source guards,
nginx-only rollback, and protects backend/DB/ingress/Production fingerprints.
Frontend-only deployment PASS; image
`sha256:d4b25560887102f1ee2ffa436c8587eea8e3635679a47e496d38c07a34b0686e`,
USTAR SHA256 `84fa3fd55c89f1740db76702a62dbdb23735ba1f6f074d01a28901a831ebb7c7`.
Backend/database/ingress/Production were unchanged and health remained UP.
Browser reload confirmed the final In-store alert title/subtitle and unchanged
Today Total/Uber646.92; the delivered tab is Dashboard Today/Total.
The first attempt rejected macOS AppleDouble archive metadata before building
or changing runtime; repackaging as USTAR retained all archive guards.

## Time and resource accounting

Work began20:25:44Z. Investigation, independent implementation, regression and
review ran in parallel; exact final elapsed time is in the Owner completion
report. Main deploy had one pre-build artifact hash guard stop while upload was
in progress; upload completion followed by retry passed. Menu transaction was
not repeated after its acknowledgement parser failure. Frontend label acceptance
required one additional frontend-only build, with one archive-preflight retry.

Initial disk65%/21GB free and cache14.92GB; after the main deploy66%/20GB free and
cache15.03GB (final9.955GB reclaimable). Cache review found no disk pressure; no cleanup
or prune ran, reclaimed0GB. Current Staging/Production images, previous verified
rollback images, database volumes and private backups remain protected.
The isolated local PostgreSQL test process on58563 was stopped after validation.
