# Uber Kitchen Mirror acceptance — 2026-10-02

Scope: TEST/Sandbox only; local Store 1 / org 1 / STG005_SRC_20260809_R01,
Test UUID bd993244-5589-4b19-8f0d-dc2ba73d4273. No Production mutation,
real Sandbox order, Accept/Deny or physical print is authorized/executed.

## Product verification before Staging

- Full backend verify: 845 tests, 0 failures/errors, 7 existing conditional skips.
- After three review fixes: 35 PostgreSQL Uber + 3 HMAC + 4 OAuth + 20 OrderService
  tests PASS, 0 skips. PostgreSQL is an isolated local database, not Sandbox E2E.
- Frontend: 236 tests PASS; build and targeted eslint PASS.
- Fresh PostgreSQL Flyway V1–V31 and Hibernate schema validation PASS; migration
  validation and uniqueness checks remain in the integration suite.
- Agent 6 initial review: no P0/P1, three P2; consolidated fixes and re-review
  ACCEPT, no open P0/P1/P2. Fixes cover Toronto midnight/DST, ordinary POS history
  SQL pagination, and partial/absent financial amounts. Duplicate release cannot
  restart a LOCAL_REVIEW_REQUIRED order.
- Credential scan: tracked/untracked non-ignored repository files against known
  private-key/token patterns plus current Staging credential fingerprints, no
  credential values emitted; PASS. No secrets/tokens in build inputs or docs.

Validated behavior: notification save-only; release without notification; fresh
Store-bound GET; no remote decision; incomplete mapping blocks; explicit NO_OP
with root context; seven noodle choices; COMBO_ROOT with both eggs and all three
confirmed sides; kitchen parity with equivalent Pad orders; transactional
rollback/restart recovery; one local order/tasks/inventory deduction/outbox;
GRAB + routed HOT_KITCHEN only; no customer receipt; customer first name + initial
or display fallback; immutable kitchen reprints and duplicate intent protection;
cancel/edit preserve history; ordinary cashier/history isolation; in-store
analytics excludes platform money; raw and minor-unit financial snapshot retained.

## Real Uber condition (read-only)

Fresh Testing GET pos_data during runtime acceptance at 2026-10-02T17:43:51Z:

- integration_enabled=true
- order_manager_client_id=t86ofdunSsVjL-0AvK6MA6LCTyF2eYLf (TEST app)
- is_order_manager_pending=NOT_RETURNED
- order_release_enabled=false
- online_status=online

[Official pos_data GET](https://developer.uber.com/docs/eats/references/api/v1/get-eats-stores-storeid-posdata),
[release webhook](https://developer.uber.com/docs/eats/references/api/webhooks/orders-release),
[documented pos_data PATCH](https://developer.uber.com/docs/eats/references/api/v1/patch-eats-stores-storeid-posdata).
The documented PATCH does not expose an order_release_enabled field. Uber must
confirm/enable the test release flow and the intended Uber Tablet order manager;
Pad acceptance must not be assumed to guarantee immediate courier-release events.
No Uber configuration was changed in this batch.

ORDER_RELEASE_TEST_BLOCKED = YES
READY_FOR_STAGE_UBER_PAD_TEST = NO

## Mapping gate

[Final executable table](UBER_KITCHEN_MAPPING_FINAL_20261002.md): 5 MAP items,
205 MAP modifier contexts, 16 explicit NO_OP contexts planned; 36 items and 37
modifier contexts remain UNMAPPED. Stable modifier IDs are consolidated with root
context retained. No fuzzy expansion. Formal persisted rules currently 0.
Final table approval remains required before persistence; approval has not arrived.
FIRST_STAGE_TEST_COMBINATION_FULLY_MAPPED = NO

## Runtime acceptance — PASS_WITH_EXTERNAL_GATES

- Product PR: [#250](https://github.com/Z1linXu/Restaurant_System/pull/250).
- Merged and deployed source: `aa0ee0918f4ab4ae1c8686eccd1b4b7d2a6a5627`.
- Environment: https://staging-pos.lanzhounoodlesmtl.com; target Store 1/org 1,
  sandbox Test Store only. Processing mode API readback `KITCHEN_MIRROR`; MOCK.
- Private custom-format DB backup verified readable before deployment; private
  backup/rollback manifests under `/srv/restaurant-pos/staging/uber-kitchen-mirror-20261002`.
  Those files contain private runtime configuration and are deliberately not copied.
- Exact-SHA backend image `sha256:9fa3fde316e14e675a9567a44ffdac0710544a08b67cb6a9c862f4f51af5b61b`.
- Exact-SHA frontend image `sha256:91974a411434e19f4187084c50805825442df3ad80e6a9a5f592f86e1a7e61d1`.
- Flyway V31 PASS; earlier Staging ledger checksums unchanged, DB container unchanged.
- HTTPS health UP, login, Store context, Frontdesk/Today/Printing routes PASS.
  Logged-in browser verified Today empty state with no Accept/Deny, normal frontdesk
  tables and existing Printing Settings MOCK. No create-order or print action used.
- Public webhook POST: wrong signature 401, valid internally signed ignored event
  200, approximately 0.058s for the POST/GET check. No redirect or login interception;
  exact-body HMAC and signature header survive the HTTPS proxies.
- GET webhook returns sanitized generic 500, with no credentials or order data.
  This is existing unsupported-method error handling, recorded as non-blocking P3;
  it is not reported as HTTP 405/PASS. Initial acceptance assertion expected 405,
  so two retries were needed to isolate and classify it; product code was unchanged.
- Zero Uber integration orders and zero Uber local orders after checks; zero formal
  mappings. No real Sandbox order, remote Accept/Deny, physical print or Uber
  configuration change. Production container fingerprints and Flyway ledger unchanged.

### Readiness matrix

PASS below means automated implementation verification unless explicitly described
as a Staging runtime readback. It never means real Uber order/physical printing PASS.

| Requested field | Result / evidence boundary |
| --- | --- |
| PROCESSING_MODE | KITCHEN_MIRROR — Staging readback |
| ORDER_RELEASE_ENABLED | false — real TEST pos_data |
| ORDERS_NOTIFICATION / SAVE_ONLY | PASS — automated tests |
| ORDERS_RELEASE / KITCHEN_TRIGGER_READY | PASS implementation; external TEST release disabled |
| REMOTE_ACCEPT_ENABLED | NO in target Mirror mode |
| REMOTE_DENY_ENABLED | NO in target Mirror mode |
| UBER_FINANCIAL_SNAPSHOT | PASS — raw charges and minor-unit persistence tests |
| IN_STORE_ANALYTICS_PROTECTED | PASS — query/data-boundary tests |
| COMBO_ROOT_MAPPING | PASS implementation; formal mapping persistence pending |
| FINE_MAPPING | PASS confirmed identity/test: noodle_capillary; not persisted |
| ONE_FINE_MAPPING | PASS confirmed identity/test: noodle_thin; not persisted |
| SWEET_SOUR_MINI_FRIES | shredded_potato / combo_shredded_potato = PASS identity/tests; not persisted |
| CHOW_MEIN_GREEN_MODIFIERS | NO_OP = PASS implementation/confirmed identity; not persisted |
| UBER_HEADER | PASS — first name + last initial, display fallback |
| GRAB | READY implementation; real event and physical print NOT TESTED |
| HOT_KITCHEN | READY routed implementation; real event and physical print NOT TESTED |
| FRONTDESK_RECEIPT | DISABLED_FOR_UBER_KITCHEN_MIRROR |
| TODAY_UBER_ORDERS | PASS — API and logged-in browser |
| GRAB_REPRINT | PASS automated snapshot/audit/idempotency tests; real order NOT TESTED |
| HOT_KITCHEN_REPRINT | PASS automated snapshot/audit/idempotency tests; real order NOT TESTED |
| PRODUCTION_CHANGED | NO — fingerprints and Flyway unchanged |
| FIRST_STAGE_TEST_COMBINATION_FULLY_MAPPED | NO — explicit table approval/persistence pending |
| READY_FOR_STAGE_UBER_PAD_TEST | NO — release disabled plus no formal mappings |

### Mapping database readback

At 2026-10-02T17:43:51Z the target connection API and read-only database query both
show zero mapping rules. No mapping writes were attempted.

| Field | Persisted/resolved now | Planned after final table approval |
| --- | ---: | ---: |
| MAPPED_ITEMS | 0 | 5 |
| MAPPED_MODIFIERS | 0 | 205 parent contexts |
| NO_OP_MODIFIERS | 0 | 16 parent contexts |
| UNMAPPED_ITEMS | 41 | 36 |
| UNMAPPED_MODIFIERS | 258 parent contexts / 33 unique IDs | 37 parent contexts |

The first-stage combination's six stable identities are confirmed in the table.
Approval and verified persistence are still required to mark it fully mapped.
Unconfirmed root items continue to block even if their modifiers are resolved.

### Docker / Disk Hygiene

- Before: 22.23 GiB available from preflight; exact percent/cache total not retained.
- After: disk 62% used, approximately 22 GiB available; Build Cache 14.42 GB total,
  10 GB reclaimable. Read-only review of all 243 cache records completed.
- Cache exceeds the 12 GB review threshold. Disk remains below the 70% healthy
  target, so bounded repair does not require cleanup; existing cache debt deferred.
- Cleanup performed: NO; reclaimed 0 GB. Current and rollback images, DB volumes,
  releases/private evidence retained. Staging healthy; Production mutation NO.

### Product / tooling and governance scope

Product: 37-file PR including code/tests/docs adds Mirror mode within existing
order/printing services; no second pipeline or Uber-specific kitchen renderer.
Tooling: bounded Staging application helper was required to preserve the existing
HTTPS/PAD overlays; it reuses reviewed ingress helpers. No general deployment
framework changes. Runtime evidence is documentation-only and requires no rebuild.
Agent 6 ACCEPT covers product/schema/helper; evidence-only follow-up uses the
repository's risk-based documentation review exception, with diff/link/governance
validation. Production and manual Sandbox/physical acceptance remain outside scope.
