# Uber Kitchen Mirror acceptance — 2026-10-02

Scope: TEST/Sandbox only; local Store 1 / org 1 / STG005_SRC_20260809_R01,
Test UUID bd993244-5589-4b19-8f0d-dc2ba73d4273. No Production mutation,
real Sandbox order, Accept/Deny or physical print is authorized/executed.

## Product verification before Staging

- Full backend verify: 842 tests, 0 failures/errors, 7 existing conditional skips.
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

Fresh Testing GET pos_data at 2026-10-02T16:41:08Z:

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

## Runtime acceptance

Staging deployment/verification pending at this implementation evidence commit.
Use the reviewed bounded helper in deployment/cloud/uber-kitchen-mirror; preserve
HTTPS/PAD overlays, take private database backup, allow only additive V31 and
verify unchanged Production fingerprints/ledger. Shared Staging gets no fixture
kitchen orders; local PostgreSQL proof must not be labeled physical/Sandbox PASS.
