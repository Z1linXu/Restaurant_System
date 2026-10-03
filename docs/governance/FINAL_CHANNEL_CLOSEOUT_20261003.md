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

Runtime write/deploy/acceptance evidence will be appended only after execution.
