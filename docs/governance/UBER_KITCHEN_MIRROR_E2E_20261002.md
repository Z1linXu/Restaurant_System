# Uber Kitchen Mirror — real TEST / Staging acceptance

Observed 2026-10-03 00:36–01:16 UTC (2026-10-02 Toronto).
Scope: TEST App `t86ofdunSsVjL-0AvK6MA6LCTyF2eYLf`, StDenis Test Store
`bd993244-5589-4b19-8f0d-dc2ba73d4273`, Staging Store 1 / org 1 /
`STG005_SRC_20260809_R01`, printing MOCK.

**READY_FOR_UBER_KITCHEN_MIRROR_PILOT = YES**, for the validated mapped
combination and kitchen-mirror behavior. This is technical readiness, not
Production approval, complete menu coverage or physical-printer acceptance.
Production remains unchanged and activation is not authorized.

## Exact implementation and deployment

- PR #252 merged as `703488638ec2bf2002cee113b037ef547aaa61fa`: durable bounded
  ACCEPTED polling, V32, shared exactly-once release gate and mapping preview.
- PR #253 merged as `f7f0bb344bfa8a157cb15ccbefb9e588aebd3da5`: Owner-authorized
  TEST webhook environment compatibility, HMAC then Sandbox GET identity proof,
  locked binding recheck before receipt/mutation. Default remains strict.
- Backend/frontend runtime: `f7f0bb344bfa8a157cb15ccbefb9e588aebd3da5`; Flyway
  `32:true`; health/login/workspace/frontdesk/printing PASS.
- Backend image `sha256:1324bd845bac5c702f1641c859a394c54ae3c7f3e07c192f2eae01e4cb370531`.
- Frontend image `sha256:1f27422fe342d2a2e8d05fb5703006ddddf43ba2cb434037f01a816b4ab0440b`.
- Public endpoint: `https://staging-pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook`.

## Mapping and TEST Order Manager

The approved [final table](UBER_KITCHEN_MAPPING_FINAL_20261002.md) was persisted
through scoped APIs after a readable private Staging DB backup. Final readback at
01:15:34 UTC matched the complete approved set, including parent contexts: 5
ITEM MAP, 205 MODIFIER MAP, 16 MODIFIER NO_OP. No foreign Store/org mappings or
Human Confirmed=NO rules were written. Remaining: 36 items / 37 modifier contexts.

Actual deployed resolver: first-stage item and all five options PASS;
COMBO_ROOT main + implicit combo + tea egg + cucumber/edamame/shredded-potato
variants PASS; unknown modifier blocked. Sixteen NO_OP contexts emit no local
options with reason INGREDIENT_NOT_USED. Their eight chow-mein roots remain
unmapped: isolated exact-SKU modifier checks do not authorize full-order release.

Official TEST `PATCH /v1/eats/stores/{testStoreId}/pos_data` changed only
`is_order_manager=false`. Before: current TEST App was order manager, integration
enabled, release disabled, online. Follow-up GET 200 at 00:53:45 UTC and deployment
acceptance confirmed manager ID absent, integration still enabled, release still
disabled, online. The PATCH status was not retained after a diagnostic teardown
failure; the mutation was reconciled by GET and was not repeated. Human Uber Pad
Accept below proves the intended workflow. Restaurant_System sent no Accept/Deny.

## Genuine Uber TEST order and timing

Order `05c16dcb-8ef8-4b75-9548-45581ef2a3a2` / display `2A3A2` was placed through
the actual TEST consumer checkout. Owner confirmed “已接单 2A3A2” in Uber Orders.
Local order: **77**. Event: `8898e89a-8919-5a30-a3fb-c52d6c03ab5e`,
`orders.notification`, COMPLETED. No synthetic POST or fixture supplied this E2E.

All times below are UTC. Legacy event/print DB timestamps were converted from
Toronto UTC−04; integration observed/placed timestamps are UTC already.

| Point | Real evidence time | Meaning |
| --- | --- | --- |
| T0 | 01:11:33.000000 | Uber placed_at |
| T1 | 01:11:34.810573 | Durable notification receipt, after authenticated TEST GET |
| T2 | 01:11:37.923937 | Captured CREATED / WAITING_FOR_ACCEPTANCE observation; no local order/jobs |
| T3 | Not instrumented | Human accepted in Uber Pad; confirmed by Owner, exact click time unavailable |
| T4 | 01:11:54.366739 | First backend ACCEPTED observation, poll count 3 |
| T5 | 01:11:56.385919 | Kitchen dispatch marker; one local order |
| T6 | 01:11:57.278678 | Initial GRAB job 195 |
| T7 | 01:11:57.309153 | Initial HOT_KITCHEN job 196 |

Notification-to-durable-receipt latency: 1.811 s. ACCEPTED observation-to-kitchen
dispatch: 2.019 s; to initial GRAB: 2.912 s. Exact human-click detection latency
cannot be calculated; last captured CREATED to first ACCEPTED spans 16.443 s.
No official Uber accept timestamp is fabricated. No orders.release was required.

## Kitchen, Today, reprint and finance

- Persisted SKU `traditional_beef_noodle`; options exactly `size_large`,
  `noodle_sanxi`, `spicy_none`, `fried_egg`, `remove_cilantro`; Chinese snapshots
  are 大碗 / 三细 / 不辣 / 加煎蛋 / 走香菜. Mapping MAPPED, RELEASED_TO_KITCHEN.
- One kitchen task 142, one production task 143, one order item 107. This Staging
  item/options have zero BOM rows, hence zero inventory transactions; actual
  stock deduction is not claimed for this order. Automated BOM/idempotency
  regression supplies the separate inventory coverage.
- Outbox 191/192 both MOCK_RENDERED; initial print jobs 195/196 PRINTED,
  printer_id absent, Store mode MOCK. GRAB and HOT body `大×1 | +煎 走香` uses the
  existing formatter: default 三细 and 不辣 are intentionally omitted by the
  active display rules. Shared UBER customer display header present (PII omitted
  from evidence), no prices/tax/Uber receipt descriptions. Fried egg routes HOT.
- Today UI verified actual order/display ID/placed time/accepted-observed time,
  Chinese summary, MAPPED and both PRINTED states. No Accept/Deny/Checkout/Payment.
- An existing manual GRAB job 197 was observed before controlled checks. Today
  UI GRAB and HOT reprint created only 198→195 and 199→196 respectively, both
  PRINTED. Frozen rendered snapshots exactly equal originals. Before/after
  items/options/tasks/inventory/outbox unchanged; two initial automatic jobs
  only; FRONTDESK_RECEIPT count zero. Reprint PASS.
- Real Uber snapshot CAD subtotal 24.49, tax 3.67, total 28.16; fees not returned.
  Local order/item/option monetary columns zero, financial_mode EXTERNAL_PLATFORM,
  external_source UBER_EATS. Actual aggregation inclusion predicate is false.
  Full completed-order analytics exclusion is additionally verified by
  `mirrorFinancialSnapshotIsExcludedFromEveryInStoreAnalyticsSummary`.
- Separate chow-mein real order NOT RUN: R20 root remains UNMAPPED.
  `HOT_KITCHEN_REAL_E2E_BLOCKED_BY_MAPPING` applies to that requested second
  scenario; the first real order's fried-egg HOT routing and reprint both PASS.
- PAD_DIRECT physical printing NOT TESTED; only MOCK was authorized here.

## Reliability, review and safety

Agent 6 ACCEPT for both material changes, no outstanding P0/P1/P2 findings.
Fresh isolated PostgreSQL full backend verify: 853 tests, zero failures/errors,
seven existing conditional skips. Focused real PostgreSQL Uber integration 43
tests plus three signature tests PASS with zero skips. Frontend 237 tests,
build, targeted ESLint, Flyway and credential scan PASS. Frontend source/artifact
was unchanged between the two repairs and reused. Governance validation PASS.

`acceptedPollingSurvivesRestartAndLateReleaseSharesExactlyOneKitchenGate` and
concurrent/release-first/recovery tests prove durable restart and shared gate in
automated PostgreSQL tests. These are distinct from the real Sandbox evidence;
no live pending-order backend restart or injected duplicate webhook is claimed.
Polling is bounded by 40 attempts / 30 minutes, with backoff and terminal stop.

The first full verify against a reused local fixture DB hit an unrelated PAD
recovery batch-size collision; a fresh isolated DB passed the complete suite.
Earlier TEST orders `e3badfcf-4b93-4525-9eea-22822bd2e9cb` and
`4ab95dd8-3a9d-49c8-9b13-9795adadf5c6` were rejected before the header repair and
did not create local orders. They are not counted as successful E2E. Genuine
signed TEST traffic used X-Environment=production; Owner explicitly approved
only the exact TEST pair exception, validated against Sandbox GET before use.

Final Production containers/config/images/Flyway and Staging DB container
fingerprints match pre-deploy baselines. Production SHA remains
`11996ef919d9b28ee5366c7e40a58a78c074b675`, V28. Production deployment/DB/Uber
mutation: NO. Secrets/tokens/PII absent from committed evidence.

Backups/rollback protected: private mapping DB backup and both Staging release
backup directories under `/srv/restaurant-pos/staging`; no restore/delete used.
Disk 62% / 22G free before, 63% / 22G free after. Build cache 14.42→14.64GB,
reclaimable 10.01GB after. Cache hygiene reviewed: recent layers and healthy
disk; no pruning, no space reclaimed. Active Production/Staging images, rollback
artifacts, DB volumes, private configuration and evidence remain protected.

Product repair covers the accepted-state worker, mapping preview, Today timestamp
and exact TEST transport compatibility. Tooling/governance changes only reused
the reviewed deployment path for its exact TEST pair configuration and recorded
runtime/mapping evidence; no general tooling refactor or docs-only rebuild.
Current execution gate belongs solely in [CURRENT_STATE.yml](CURRENT_STATE.yml).
