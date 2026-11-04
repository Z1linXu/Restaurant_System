# Database reporting and lifecycle preparation

Owner scope: local isolated implementation/tests only. No Production data,
schema, retention setting or scheduler is changed. Staging/Test remain paused.
The application baseline is deployed `27612b6f72dabc48e0167cc7d214a3ece565dd5e`,
including V39.1. Pending v3 financial changes are not part of this work.

## Daily and monthly reporting

`GET /api/v1/admin/analytics/period-report?store_id=ID&period=day|month&date=YYYY-MM-DD`
requires the existing Store `ADMIN_STORE_CONFIG`, `REPORTING_CORE` and
`AnalyticsReadScope` authorization. Store ID is mandatory; no implicit global
financial scope is introduced. A month uses the anchor's calendar month.
The selected Store timezone defaults to America/Toronto as in the existing
Uber sales adapter; no new business-day cutoff is invented.

The response embeds the existing `ChannelSalesSummary`: channel order counts,
known revenue, nullable complete revenue, distinct pending orders, unknown
item lines, reasons, stable item keys, frozen names, quantity, item/category
money, daily totals and Store split. POS option charges are already included
in frozen `line_amount`; they are not read or added again. Existing channel
eligibility, amounts, allocation and financial correction rules are unchanged.

The projection is recomputed in one read-only REPEATABLE READ transaction.
It does not increment a stored total or cache financial evidence. Repeating
the same read does not double count; late completed submissions and valid
APPLY/REVOKE changes become visible on the next read through the existing
financial adapter. A pending *amount* stays null/UNKNOWN when its value is
unknown; a pending order count is not an amount.

`adjustments.in_store_discount` is only the sum of recorded eligible POS
order discounts, or null if any relevant value is absent. It is informational
and must not be subtracted again. Separate verified tax, Uber discount and
refund ledgers are not available through the deployed shared reporting
contract, so those values remain null with explicit UNKNOWN reasons. Tax is
not reverse-calculated from totals or item allocations. No new financial
interpretation is made.

No schema, Flyway migration, scheduler or existing report endpoint changes
are required. Original transactions/snapshots remain necessary. Legacy
daily/hourly/item summary tables are not an archival substitute. Long-term
materialization and archive-backed reporting require a separate validated
design before original records can be removed.

## Lifecycle tool

`scripts/data-lifecycle/lifecycle.py` has exactly four modes:

| Mode | Behavior |
| --- | --- |
| `plan` | Read-only metadata/dependency plan; bounded audit archive-copy identities/hashes; no payload bodies in plan |
| `archive-audit` | Recheck exact planned row hashes and create a private copy of audit evidence; no source mutation |
| `verify-archive` | Offline schema/scope/row-count/checksum validation |
| `restore-local` | Idempotent insert of missing identical-schema audit rows in a marked loopback synthetic sandbox only |

There is **no cleanup executor or DELETE path**, for any table. Every reviewed
table is explicitly blocked. Unknown future tables are protected. The example
policy has automatic cleanup disabled, which the tool refuses to enable.
Its retention ages are review horizons only, not a legal retention decision
or authority to delete. Store IDs must be explicit. Batches are 1–500 rows;
`has_more` and `next_after_id` support interruption/resume. Private outputs use
0600, exclusive creation, and never overwrite previous evidence.

The catalog plan includes current incoming/outgoing foreign keys plus explicit
application references. The review classes cover all audited Top20 tables,
plus `sales_daily_summary` and `uber_eats_financial_corrections`:

- A: menu/configuration/master mappings and financial correction journal;
  no time-based cleanup.
- B: completed order graph, kitchen/beverage/production and summary history;
  archive reads and report equivalence are unproven, so blocked.
- C: audit logs; per-action financial/security/evidence retention is unapproved,
  so only archive-copy review is supported.
- D: PrintJobs/Attempts/Outbox, submission/update idempotency and Uber
  orders/events; dedup, active work, reprint, source hashes and financial risks
  block cleanup.

Active orders/printing/Outbox, Uber pending money, dedup keys and historical
correction evidence cannot be selected for deletion: no such operation exists.
Archiving is a copy, not proof that deletion is safe or authorized.

Connection settings use libpq environment variables; passwords are never
printed or passed as command-line values. Plan/archive sessions force
`default_transaction_read_only=on`, 5-second statement and 1-second lock
timeouts. Example for an **already approved isolated target**, not Production:

```sh
export PGHOST=127.0.0.1 PGPORT=55487 PGDATABASE=resource_lifecycle_test
export PSQL=/opt/homebrew/opt/postgresql@16/bin/psql
python3 scripts/data-lifecycle/lifecycle.py plan \
  --as-of 2026-11-03 --store-id 1 --batch-size 100 --after-id 0 \
  --output /private/approved-plan.json
python3 scripts/data-lifecycle/lifecycle.py archive-audit \
  --plan /private/approved-plan.json --output /private/approved-audit-copy.json
python3 scripts/data-lifecycle/lifecycle.py verify-archive \
  --archive /private/approved-audit-copy.json
```

Restore additionally requires an explicitly named `resource_lifecycle_*`
database, PGHOST exactly 127.0.0.1, matching server port, a private sandbox root
containing the cluster data directory and a `.resource-lifecycle-synthetic`
marker, and the matching `resource_lifecycle_sandbox_guard` table marker.
Schema drift, tampering or existing differing PK identity fails closed.
All row conflicts are checked before inserts; one transaction performs the
bounded restore, and the audit sequence is advanced without decreasing it.
Existing rows are never overwritten; retries with identical rows are no-ops.
FK enforcement remains enabled. This is a local recovery rehearsal, **not a
Production restore procedure** or an order-graph archive implementation.

## Validation

```sh
bash scripts/data-lifecycle/run-isolated-tests.sh
```

This creates a new private local PostgreSQL16 cluster on 127.0.0.1:55487,
using synthetic records only, and stops that exact cluster on exit. It never
starts a Spring application, Worker or Uber connection. Fixture/logs are left
private for inspection. An occupied port fails rather than reusing a server.

Actual result: **12 Python tests PASS** (5 unit protection checks, 7 real-PG
checks). The first run exposed a test-only `nextval` call attempted through
the forced read-only helper; the fixture check now explicitly uses its local
write helper, and the rerun passed.

Proof includes bounded/resumable plans, no payload leakage in the plan,
unknown incoming FK protection, changed-evidence rejection, private CLI
outputs, archive checksum/schema checks, synthetic removal then exact restore,
retry idempotence, sequence safety, all-or-nothing PK conflict rejection,
and rejection of unmarked/nonlocal restore targets. Daily/item/hourly
summaries, orders, print state, attempts, dedup keys, active Outbox and Uber
evidence retain identical fixture fingerprints during copy/restore.

Java focused tests to include in the root's unified Maven run:
`SalesPeriodReportServiceTest,SalesPeriodReportControllerTest`, plus existing
`ChannelSalesServiceTest`, financial-source and analytics-scope regressions.
The new tests exercise day/month boundaries and leap year, frozen identity,
quantities, pending semantics, option non-duplication, recomputation after
late data/correction/revoke, unknown adjustments and foreign-Store denial.

## Future cadence and approval

Daily small batches are preferred *after* an actual deletion class has proven
safe: lower per-run lock/WAL/I/O peaks and smoother Autovacuum work. Weekly
operation has fewer invocations but larger bursts; it should still use short
batches. Monthly is suitable for archive/seal/restore review, not one large
deletion transaction. Ordinary row deletion usually creates reusable database
space, not immediate OS file shrinkage. No Trigger is proposed.

No Production archive or cleanup command is authorized or supplied here.
Before any future deletion: approve exact identities/state predicates,
retention and evidence requirements; prove FK/application/dedup closure;
validate archive-backed daily/monthly reporting; rehearse restoration; measure
WAL/locks/vacuum impact; then approve a separate bounded operation. A full DB
restore over new business activity is not an ordinary rollback.


Agent 6 found a valid audit payload containing `$restore$` could collide with
an SQL block delimiter. Recovery now chooses a delimiter absent from the whole
body and fixes standard string escaping; real-PG regression preserves delimiter
tags, overlapping tags, quotes, multiline/backslash and SQL-looking text exactly.
The complete12-test suite was independently rerun and accepted.
