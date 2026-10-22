# 4483 controlled Kitchen Mirror launch — 2026-10-22

## Scope and repair

PRIMARY_REPAIR_GOAL: exact4483 Uber vegetable Combo defaults to the existing
`soup_vegan` once, then complete controlled single-Store launch acceptance.
EXPECTED_REPAIR_CLASS: SMALL business repair, BLOCKING current-runtime tooling.
The Owner explicitly authorizes review/merge, necessary isolated Staging and
Production deployment, fresh4483-only cutovers, worker activation and one new
real accepted order's kitchen output after technical gates. No Uber remote
menu/price/Accept/Deny/Cancel write; no Store2/3 mutation, historical replay,
print-queue reset, ambiguous reprint or historical snapshot rewrite.

Implementation baseline: `a939a85f1a22568da2941efe3dd9d9130cb05602`; protected
original Owner workspace untouched. Before this batch, both backend images were
`sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5`
(source4b3abd1), frontend4b3abd1, FlywayV38. There are no intervening application
changes between deployed4b and fresh main; this batch must not include unrelated
POS fixes. No migration, frontend build or APK installation is required.

The strict optional `UBER_EATS_MIRROR_ITEM_DEFAULTS` array scopes by environment,
local Store/Organization, Uber Store UUID and root stable ID, and validates the
local SKU and exact ID/COMBO_ROOT rule. Current eligible SOUP_BASE/soup_vegan
supplies the existing option ID and normal price/name/quantity snapshot. Empty
configuration is the default. No Store name or Production ID is hardcoded in
Java. Explicit matching soup is preserved; conflicting broth/quantity, unknown
modifier, unavailable choice or mapping drift blocks local release through
existing RELEASED_MAPPING_REQUIRED, without a second order/printing pipeline.

SCOPE_EXPANSION_CHECK: legacy receive-only promotion assumesV37/binding0 and
cannot safely deploy overV38, Store1 binding and299 mappings. Its independent
`--current-4483` mode is BLOCKING for this repair: reuse dual locks/private
Compose/backup/health/CAS and preserve all OAuth/key/environment values, changing
only Backend image and the exact scoped default. Production requires the same
Staging artifact plus300 clean previews. Old modes remain unchanged. A generic
release/activation framework, new credential flow and unrelated cleanup are
NON_BLOCKING and deferred. Activation remains a separate reviewed same-image
three-environment-value operation after all deployment/readiness proof.

## Fresh read-only preflight

2026-10-22T19:39:45Z: OAuth200 with exactly `eats.store eats.store.orders.read`;
Store GET200, Menu GET200. Full current menu matches the earlier verified menu
when entity arrays are ordered by stable ID; raw bytes hash
`5db2ab10e52e5ca381e1629575ac37075564123a4887d7704f142c08218c461d`.
No credential or full customer/order response is retained in this report.

Three previously HELD event references resolve through Production GET Order200
to the same4483 Store and actual ACCEPTED order IDs. Their corresponding public
HTTPS access records return200/empty response. They are retained pre-cutover
observations, not a substitute for the newly controlled first-order proof:

| Event | Order | Receipt local America/Toronto |
| --- | --- | --- |
| f19a96a2-cf92-5313-901c-bee3358e70f2 | 49d220a8-62ca-4849-9507-ca09c01a0188 | 15:29:40 |
| 9ee60049-4b11-535f-a1b5-87d31918447c | a05790ed-6208-429d-a6e7-645b3549b777 | 15:00:16 |
| 52be4ba9-1d35-586c-b735-5634913dc307 | a4f200e8-538f-43ae-8363-48e692a8cd07 | 14:52:33 |

Preflight has8 HELD receipts, integration/local Uber orders0, mappings299.
Keep all old notifications unchanged; no resetting or requeueing. Current
receiver=true/worker=false, no cutovers.

Device13 is ACTIVE Store1/org1 ANDROID_PAD with last_seen8 seconds before the
15:37 local observation. At15:19 it claimed/completed ordinary POS GRAB26396
and HOT26397; at15:24 GRAB26400/FD26401. These are current worker/transport
observations, not new Uber physical-output acceptance. Store1 PAD_DIRECT,
GRAB printer3/HOT printer4 assignments and enabled endpoints are present.
Four legacy PRINTING jobs1176/3169/9226/20391 belong to old Device7 and are kept
untouched. They must not block by a total-count assumption or be silently retried.

Disk before build73% (42G used,16G free); shared Build Cache16.82GB, reclaimable
10.45GB. Cache age/eligibility was reviewed earlier; no unrestricted cleanup.
Protect both runtimes, images, database volumes, private models, backups and
rollback artifacts; remove only this batch's disposable builder after its build.

## Execution proof

Automated tests277/277 PASS, failures/errors/skips0:139 Uber (PostgreSQL81,
new defaults unit12, migration1 included) and138 affected POS/printing. One
existing migration URL condition initially skipped underlocalhost; the same
existing test was rerun with127.0.0.1 and passed. Deployment/operator Python suite92/92 PASS after the final Stage historical-model
preservation clarification. Governance/diff checks PASS. Agent6 final ACCEPT,
no remaining P0/P1/P2 across product/default configuration, bounded deployment
mode, acceptance runner and documentation. Preview evidence reports actual
before/after business totals and explicit zero deltas, preserving Stage history.

Pending exact-SHA build/deployment,300-case previews and the
controlled fresh first order. No pending observation is PASS. Actual runtime,
cutover, linked order/job IDs, physical confirmation and rollback will be added
only after the corresponding action completes.
