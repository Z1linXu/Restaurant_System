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

## Actual deployment and preview acceptance

PR291 merged at19:46:41Z. Actual backend source:
`56185edcdd9f4496cc6073e478546a5a39ba77d9`; immutable image:
`sha256:264babe4f4bed353b310f5989a8b93623dff69ebeee9cd2e0dda81b829238d97`.
One Backend build ran19:48:32–19:51:47Z, capped1CPU/1GiB. The owned disposable
builder was removed. No frontend/APK/schema build or change; frontend remains
4b3abd1, FlywayV38. One pre-build clean-tree guard retry removed only the
bytecode file generated by importing this batch's reviewed build helper; no
application deployment occurred during that failed preflight. Further imports
used Python-B to preserve the exact clean release.

| Actual check | Result |
| --- | --- |
| Staging same-image deployment | PASS; receiver=true/worker=false |
| Staging300 previews,19:54:58Z | **300/300; errors0/warnings0/RAW0** |
| Production same-image deployment | PASS; backup validated, unrelated containers unchanged |
| Production300 previews,19:56:57Z | **300/300; errors0/warnings0/RAW0** |
| Production mapping read-back | **299/299**,41 ITEM/238 MODIFIER/20 NO_OP/8 COMBO_ROOT |
| Existing ID/Chinese/default quantity snapshot | Exact current eligible choice, soup_vegan once |
| HMAC on new artifact | valid200, invalid401, foreign environment400, duplicate200/one HELD |
| POS authenticated GET smoke / Store isolation | 7 checks PASS / foreign Store403 |

Each preview proof binds actual image/SHA and current mapping/catalog/options/
combo and other protected configuration fingerprints. Before/after fingerprints
match. Activation compares both environments' proofs to their then-current
configuration; post-proof drift fails closed. Stage historical totals13 Uber
integration orders/11 local orders/28 jobs remain unchanged, delta0. Production
business totals remain0, delta0. Internal HMAC probe
`9a128183-afec-4de2-9827-ded639fe3bf9` is explicitly synthetic and cannot prove
Uber delivery. No Source/PII/credential response was placed in Git.

## Controlled activation and field-window pause

The one-time activation call was separately reviewed by Agent6, final ACCEPT,
no P0/P1/P2. Script SHA256:
`5c59c32f03816d75fc3605d28ab59368ea5a1aa40df54794c71e77be044d0e9b`;
7 local mock tests PASS. It reuses the existing dual locks/private model,
validated backup, exact image/config CAS and POS GET helpers. It is an actual
bounded operation, not a new credential or activation framework.

Only three environment values changed: worker=true, global UTC boundary and the
single4483 UUID boundary, both **2026-10-22T19:59:30Z** (15:59:30 Montréal).
Device13 was fresh, PAD_DIRECT/GRAB3/HOT4 unchanged,10 old HELD notifications
preserved and4 legacy PRINTING rows untouched. Other Stores/containers unchanged.
This technical activation did not claim first-order or physical-print PASS.

The Owner then replied **currently unable to arrange the first field order**.
Before pausing, at20:02:52Z, there were no post-cutover events, integration orders,
local Uber orders or Uber jobs. The reviewed same-image worker=false rollback
was applied under both locks and canonical/runtime CAS. At**20:03:57Z**:
receiver=true, worker=false, global cutover empty, per-Store cutovers `{}`;
vegan default and299 mappings retained, other configuration unchanged.
No job/event reset, history replay, reprint or database restore occurred.

Final20:04:28Z read: mappings299, Uber integration/local orders0, Uber print jobs0,
Uber FRONTDESK_RECEIPT0, all10 receipts still HELD_RECEIVE_ONLY; Device13 last_seen
8.6 seconds old. Both environments healthy. Current runtime uses the accepted
56185ed backend; no credential values matched backend logs. Real GRAB/HOT paper
and the first controlled order→local-order link remain **NOT TESTED**, not FAIL
or PASS. No first-order ID is fabricated. `4483_LIVE=NO` while paused.

## Evidence and rollback

Private server directory0700/files0600; do not publish Compose/backups/tokens:

| Proof | Private path or SHA256 |
| --- | --- |
| Build | `/srv/restaurant-pos/staging/uber-4483-build-56185edcdd9f4496cc6073e478546a5a39ba77d9/result.json` |
| Stage preview | `/srv/restaurant-pos/production/uber-4483-final-20261022/staging-vegan-preview-20261022T195403Z/result.json` |
| Prod preview | `/srv/restaurant-pos/production/uber-4483-final-20261022/production-vegan-preview-20261022T195603Z/result.json` |
| Prod deployment backup | `40fd20108db1a928221a093ca52dbbec0a3a9b99abce3d22e0a8fd8b4e47a270` |
| Activation/field pause | `/srv/restaurant-pos/production/uber-4483-activation-20261022T195736Z/result.json` and `field-window-pause-result.json` |
| Activation backup | `4863eb52161346617874e656db9ad3df23fd54f2e9f6af8a8b599f2b2dc259af` |

The activation rollback model in that directory has already safely restored
same-image receive-only operation while preserving the deployed soup fix.
For an application regression, the saved model below
`/srv/restaurant-pos/production/uber-4483-backend-56185edcdd9f4496cc6073e478546a5a39ba77d9/rollback.private.json`
retains the prior4b image/environment. Any future use requires current config
ownership/CAS, dual locks and health checks. Do not restore a whole database,
clear queues or retry ambiguous physical jobs as an application rollback.

Resume needs an available field order/paper observer, fresh unchanged-configuration
proof and a new4483-only UTC boundary. No OAuth reinstall,299-row remapping,
frontend build or APK replacement is needed.

Docker after build/deploy: disk74% versus73% before; shared Build Cache16.82GB
unchanged, no pruning/reclaimed space0. Both environment runtimes, DB volumes,
old image and rollback/backup evidence protected. The only removed cache state
was this batch's owned disposable builder, not shared production build cache.
