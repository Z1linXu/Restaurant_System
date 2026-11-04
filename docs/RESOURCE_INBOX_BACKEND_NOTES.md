# Inbox read-path optimization

PRIMARY_REPAIR_GOAL: eliminate per-order printing-state reads, keep valid Uber work visible, and prove the optional index with isolated synthetic PostgreSQL.
EXPECTED_REPAIR_CLASS: MEDIUM. This package changes read projection only. No printing core, Android, claim/lease/retry, Outbox processing, Uber Worker, financial parser/source/correction or operational write path is changed.

## API contract

Existing `GET /api/v1/stores/{storeId}/integrations/uber-eats/orders` retains its array envelope and existing selection semantics, now with batched print state.

New `GET .../inbox` uses the same authorization/capability boundary. Query:

- `limit`: default100, range1–200, per bucket.
- `today_before_id`, `attention_before_id`: independent descending-ID cursors.
- `history_date`: optional `YYYY-MM-DD`; historical data is not requested without it.
- `history_before_id`: only valid with `history_date`.

`data` contains `business_date`, `time_zone`, `today`, `attention`, and `history` (null unless requested). Every page contains `orders`, `has_more`, `next_before_id`. Cursors represent returned rows, not filtered-away candidates, so unresolved work beyond200 remains reachable. Live insertions appear on refresh of the first page.

The Store timezone is used, falling back to existing `UBER_EATS_BUSINESS_TIME_ZONE`. Midnight business-day boundaries preserve the existing UTC event / JVM-local legacy creation contract; no new service-day cutoff is invented. Tests cover23/25-hour DST days.

Today includes all processing modes inside the business day. Attention independently includes cancel/edit/review/mapping warnings, latest print failures/uncertain states, and cross-day unfinished local/integration work. Successfully printed and completed mirror history is omitted from attention, but remains available by date. A current-day exception may occur in both buckets; the frontend may avoid rendering duplicate cards while keeping the attention warning visible. An old completed order with an unresolved print job remains visible. Pending integration work lacking every timestamp also remains in attention.

## Query shape and invariants

`UberEatsInboxReadRepository` selects only order/module/status for printing state, applies `created_at DESC,id DESC` per module and order, then fetches missing module source keys in one Store-scoped Outbox query. Neither query selects ticket text, ticket snapshot or ESC/POS bytes. A list with200 local orders takes one print-state and at most one fallback query per Store, not200 plus up to400.

The grouped endpoint does two bucket queries, one timezone lookup, and at most two print-state reads across the combined returned rows (a requested history page adds one bucket query). Authorization has its existing fixed reads. The attention selector materializes latest narrow job states once for the Store; it does not execute a separate correlated print-table scan per order. Its cost still grows with retained print history, and should be observed separately from the bounded display projection.

`UberEatsKitchenView.state(row)` remains available to existing callers. Status rules, latest reprint precedence, NOT_REQUIRED fallback, cancel/edit warning priority and the original UNCERTAIN projection semantics are preserved. Controller content reads decode each returned localRequest once rather than separately for mapped/raw/note fields.

## Tests and benchmark

Focused Java tests: `UberEatsInboxBatchTest` and `UberEatsInboxReadPostgresTest`.
The PostgreSQL test accepts only a loopback `RESOURCE_TEST_POSTGRES_URL`, creates a uniquely named synthetic database, and drops that scratch database after testing. It does not start Spring workers or contact printers/Uber.

```
RESOURCE_TEST_POSTGRES_URL=jdbc:postgresql://127.0.0.1:55486/postgres \
  mvn -f backend/pom.xml -Dtest=UberEatsInboxBatchTest,UberEatsInboxReadPostgresTest test
python3 scripts/performance/benchmark_uber_inbox.py \
  --output docs/evidence/resource-optimization/inbox-synthetic-benchmark.json
```

The benchmark always starts a new temporary loopback-only PostgreSQL cluster. It accepts no existing DB URL. It creates40,000 synthetic jobs with large external ticket fields, old-style active partial index,200 displayed orders and missing-module fallbacks. It compares original per-order reads, batch without the candidate, and batch with `(store_id,order_id,created_at DESC,id DESC)`. Thirty measured repetitions after three warmups provide query counts, warm P50/P95, backend process CPU, client CPU, buffer totals, representative JSON plans and exact result hashes. This is not a Production capacity prediction. Python psutil is optional; when absent backend CPU is explicitly UNKNOWN. The cluster stops and is removed in `finally`.

## Optional index and deployment compatibility

No Flyway migration was added. The reviewed scripts live under `deployment/cloud/manual/`, outside application startup scanning. Both require explicit approval and an exact matching database name; apply refuses a pre-existing name rather than masking an invalid index.

Apply and rollback are independent future Production actions. Concurrent construction must run outside a transaction and can consume I/O or leave an invalid index on failure. Before approval, inspect current indexes, long transactions, active workload, disk/memory margin and the exact immutable release. After an interruption inspect `pg_index.indisvalid/indisready` before retrying. Rollback is an independently approved `DROP INDEX CONCURRENTLY` of this new index only. Reverting the application does not require undoing the optional index.

No Production index creation, ANALYZE, VACUUM, deployment or business mutation was executed.

## Recorded synthetic results

Final artifact: `docs/evidence/resource-optimization/inbox-synthetic-benchmark.json`.
40,000 jobs,10,000 synthetic Uber/local orders; job physical storage705,568,768 bytes
includes large external synthetic ticket fields. Warm buffers,3 warmups and30
measured repetitions per variant, persistent loopback connection. CPU is the
actual PostgreSQL backend process user+system delta over30 repetitions, not CPU%.

| Read variant | SQL | P50 ms | P95 ms | Backend CPU seconds /30 | Print shared-hit blocks |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original per-order state reads | 268 | 765.841 | 799.347 | 21.6017 | 1,000,000 |
| Batched narrow state, no new index | 2 | 7.120 | 7.527 | 0.1953 | 5,000 |
| Batched narrow state + candidate index | 2 | 1.316 | 1.397 | 0.0245 | 702 |

All three status-map hashes match on every repetition. Buffers in this table
cover print queries; SQL counts include missing-module fallback queries. This
is the replacement of the original association reads, not total HTTP latency.

The complete grouped SQL path additionally measures both bucket queries,
attention latest-job/fallback set processing, and batch state/fallback reads:

| Complete grouped SQL path | SQL | P50 ms | P95 ms | Backend CPU seconds /30 | All shared-hit blocks |
| --- | ---: | ---: | ---: | ---: | ---: |
| Without candidate index | 4 | 48.574 | 49.332 | 1.4093 | 10,854 |
| With candidate index | 4 | 45.470 | 127.778 | 1.3620 | 6,582 |

The Store timezone lookup adds one fixed query; existing authorization queries,
Spring/HTTP serialization and frontend rendering are not timed. The indexed
complete-path P95 has local tail variation and is worse in this sample; do not
claim a full-endpoint tail improvement from this run. The candidate clearly
benefits bounded state projection, but it remains an optional independently
approved change. Batch/ frontend deduplication do not depend on applying it.
Read SQL fragment hash is recorded with the plans. The final fallback set
produces the same result hash as the prior scalar-fallback safety version.

`inbox-index-script-tests.json` records six actual synthetic PostgreSQL16 cases:
default refusal, wrong database refusal, approved concurrent create, existing
name refusal, approved exact rollback and refusal of a reused/different index.
No Production action occurred. No manual index file is a Flyway location.
