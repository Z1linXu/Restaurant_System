# Production Uber receiver and confirmed Store preparation — 2026-10-22

## Authority and bounded implementation

Owner attachment `10296bcd-b4f5-43ae-954a-fe5af5424691/已粘贴的文本.txt`
and the explicit Primary Key supplement authorize receive-only Production
configuration, the exact local Store1/org1/4483 UUID binding, verified mapping
writes and two-store printing preparation. They prohibit activating the worker,
remote Uber writes, order import, physical printing or guessed external IDs.
The supplied Key is not reproduced here. No key rotation or Secondary Key setup.

`PRIMARY_REPAIR_GOAL`: install the confirmed Production Primary key into the
existing private model and prove receive-only durable HMAC receipt, then complete
all safe local Store/mapping/printing preparation. `EXPECTED_REPAIR_CLASS=SMALL`
configuration work with explicitly requested Production operations.

The accepted application image is reused. No Java/React/Android/migration changes
or image build were required. PR284 merged as
`5d2c5635faf232e5f986299e7d73167d4a7c4877`; its bounded operator is
`deployment/cloud/configure-uber-production-receiver.py`. It reuses existing
locks/private Compose/backup/HTTP helpers. This small wrapper is BLOCKING because
the earlier promotion requires a fresh image and matching current Staging,
whereas this change must preserve the accepted Production image while another
authorized POS task independently updates shared Staging. It creates no new
secret store, app lifecycle, onboarding or generalized deployment system.

Agent6 ACCEPT after fixing an early SystemExit path that could skip rollback.
The added full configuration mock proves post-recreate smoke failure restores
receiver/worker=false and the canonical private model on the same image.
Python:31/31 (25 existing +6 new). Existing focused backend:60/60, no skips/errors;
Android policy/transport/proof pure JVM:16/16. Backend test log:
`/tmp/rs-uber-printing-audit-20261022.log`. These tests target the unchanged Uber/
printing artifact, not the independent POS release. Governance/whitespace checks
pass. No configured GitHub CI checks; local PASS is not called CI PASS.

## Actual Production configuration and public acceptance

Private evidence root:
`/srv/restaurant-pos/production/uber-receiver-20261022T044959Z`.
At this configuration operation the application was
`7d573be03d66a9505e0c68f92fa17829247f56e3`, image
`sha256:6512955fe8b066855fa1e4b3675549cf4b10c975340b4f6d1db000aaff653f8b`.
The backend restarted at 04:50:13 UTC (00:50:13 Toronto). No frontend, Nginx,
database or other container was recreated by this operation. Flyway V37 unchanged.
Database backup was validated with pg_restore --list; SHA256:
`7505b7c2a26c01fd93f349d0159b5251475405dd610feab80fd3f151ff1cf97a`.
The root contains the private original/target/rollback Compose models and dump.
Rollback retains this same image with receiver and worker false; no DB restore
is needed for this no-migration configuration change.

Only `UBER_EATS_WEBHOOK_SIGNING_KEY` and `UBER_EATS_ENABLED` changed. Runtime
read-back proves the supplied key exactly matches the running container and
private canonical model. Canonical file permissions0600, release directory0700.
Worker=false, environment=production, global mode=KITCHEN_MIRROR, scopes exactly
`eats.store eats.store.orders.read`, Store cutovers empty. OAuth Client ID/Secret
were unchanged; Production OAuth Secret remains absent. Key transport used SSH
stdin with terminal echo disabled, no secret in arguments/history/Git. Recent
backend logs were checked in memory: the key was absent; log bodies were not
printed. Secondary Key was not configured.

| Public HTTPS check | Actual result |
| --- | --- |
| Valid HMAC over raw body with actual runtime Primary Key | 200, empty body |
| Invalid signature | 401 |
| Sandbox header against Production receiver | 400 |
| Exact duplicate event | 200, empty body |
| Committed database receipt | exactly1 HELD_RECEIVE_ONLY |
| New integration/local Uber orders | 0 |
| New Uber kitchen/production tasks | 0 |
| New Uber Print Jobs | 0 |
| Authenticated POS GETs | 7 PASS |
| Unauthorized Store read | 403 PASS |

The synthetic event ID is `3d817c22-89ce-451a-89f1-38f93fa07164`; random order
UUID and known Store UUID only, no customer or payment fields. These are operator
generated signed probes over the public endpoint, not official Uber delivery.
Real Uber delivery remains NOT_VERIFIED. Password login, business draft submit
and physical paper output were not repeated. No remote Uber API was called.

The logged-in Production Dashboard Webhooks page was read: PRIMARY,
BASIC_HMAC and exact Production URL were present. No Dashboard setting or secret
was edited/revealed. The current scope page did not provide accessible approval
content during this check; prior pending status is not promoted to approval.

## Mapping reuse, local identity and external gap

Read-only source/target observations: 00:42 Toronto. Staging still has299 rules:
41 ITEM MAP,238 MODIFIER MAP,20 NO_OP;8 COMBO_ROOT. All use identifier_type=ID.
Exact parent Uber root context is retained. Both Production Stores independently
resolve299/299 rules to their own effective local SKU/option/group choices;
zero local ambiguity. Each has49 ordinary ADD_ON rule references checked through
the existing StoreAddon FK, and8 unique combo triggers. No Staging local PKs were
copied and no new Add-on identity was created.

The original V37 598-row candidate artifact is explicitly candidateOnly,
notAProductionMappingManifest and persistenceAllowed=false. SHA256:
`194982bfae02057d218abd4fb185e30bd619a0f3803ce98d56dc495a327b6445`.
Local artifact:
`/Users/xuzilin/.codex/artifacts/uber-production-20261022/rs-uber-production-mapping-candidates-before-v38.json`.
Candidate and PK-free semantic source are retained0600 under the private evidence
root. This preserves all confirmed business decisions; the Owner need not
reconfirm299 rules. Local resolution alone is not Production external ID proof.

Verified Production external rules:0/store. Persisted Production rules:0/store.
Unverified external rules:299/store. No live Production menu/read credentials or
official export establishes which TEST identifiers survive into either merchant
menu. Do not silently persist them as official mappings. Later authorized live
menu/export comparison may reuse the approved semantic table without new core
development. No Production mapping-preview PASS is claimed for absent rules.

## Printing and hardware

Shared OrderService/outbox/dispatch are unchanged. Focused tests prove GRAB,
nonempty conditional HOT_KITCHEN, no FRONTDESK_RECEIPT or UPDATE for Mirror,
snapshot-safe reprints, one order/task/job pipeline and no blind ambiguous retry.
Normal in-store receipts remain supported. No Uber-specific renderer/socket.

Store1: printing enabled/PAD_DIRECT; GRAB printer3, HOT printer4, ordinary
FRONTDESK printer2 remain configured. Ten active Android registrations all report
app_version=unknown, heartbeat=NULL; latest known last_seen remains Oct21
22:13:48 backend-local. Registration is not a current worker or APK proof.
Source APK is0.4.0-kitchen-field-fixes/API26+, but installed version is UNKNOWN.
No upgrade is required by this unchanged backend contract; actual installed
compatibility still needs field observation. No APK installed/data cleared.

Store3: printing disabled/DISABLED; no physical assignments or registered Pad.
Existing GRAB/FRONTDESK logical roles are UNBOUND, no HOT logical role. No hardware
IP/endpoint was supplied; no fake binding or Store1 printer reuse was made.
Chinatown and all Store printing/module configurations are preserved.

## Final local binding and concurrent release observation

Completed at05:01 UTC under both deployment locks. The existing binding API
requires platform ADMIN and Production has no active ADMIN; no role was changed
or forged. Agent6 reviewed the exact Owner-authorized INSERT-only maintenance
transaction. Six real local PostgreSQL synthetic cases passed: repeated exact
binding/one audit, wrong Store, foreign binding, disabled module, inactive Owner,
and audit failure (binding rolls back). No real data was copied into the local
rehearsal. The synthetic database was removed afterward.

The actual transaction created binding id1 for Store1/org1, UUID
`5a7dca5c-b7ec-57c8-a684-87d23e66e8d9`, production, enabled=true,
KITCHEN_MIRROR. Its second execution returned the same id; exactly one accurate
Owner-maintenance audit was read back. It did not pretend that an ADMIN API user
performed the operation. Store3 remains unbound, Chinatown unchanged. No modules,
printer/device configuration, menu mapping, business order or Print Job changed.

Private binding root:
`/srv/restaurant-pos/production/uber-receiver-20261022T044959Z/store1-binding-20261022T050147Z`.
Its validated pre-write dump SHA256 is
`ab45c311654ebffe2a19264b3a8fd902c87a95e3abd87d710c9bb31afbb8ee02`.
The operator and six-case rehearsal result are retained in the parent root;
operator SHA256 `49511f1a58f505c2333561ed201057b2d1ecc0e50fae227fcd92f102d8b3e93d`.
No destructive rollback/delete of the committed binding is authorized by this
evidence. In an emergency disable receipt/worker through the reviewed config
path; retain binding/history while investigating.

Owner GET connection subsequently returned200 for both Stores: Store1 has the
exact binding and0 mappings; Store3 has no binding/0 mappings. A no-PII TEST-ID
only mapping-preview returned200/errors0/warnings1 (RAW fallback, not ready).
An explicitly synthetic local external_data SKU/option-code preview returned200/
errors0/warnings0; this verifies that representative local semantic path only,
not any Production external ID. Orders/tasks/jobs remained unchanged.

The independent POS release subsequently promoted application4b3abd1/V38 to
Production. This task did not deploy those app changes. Actual latest backend
and frontend source in both environments is
`4b3abd1349e66a146c36ac310b6a8b48bb2ad572`; backend image
`sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5`.
No Uber configuration/binding was lost. At05:05:08 UTC the new Production image
passed another locked public HMAC200/401/400/duplicate200 proof, persisted
exactly one additional HELD event, seven POS GETs and Store isolation. Key
equality to the installed private configuration was confirmed without output.
Proof is `post-pos-release-http-proof.json` in the receiver root; second event ID
`1d477e52-d491-44ca-9c83-8e0433b71c5a`. Total synthetic receipts are now2,
each unique/idempotent, with no Uber business orders/tasks/jobs. Both public
system health endpoints are200/UP; Production login/frontdesk/Print Center HTML
routes are200. Password login and physical output remain NOT_TESTED.

Docker/disk before73%/16GB free, after74%/16GB free. Build Cache remains16.82GB
(10.45GB reclaimable). Cache above12GB was reviewed, no cleanup/reclaimed space;
the independent release was actively building/deploying, and this task built
no image. All8 containers Up, both databases healthy. Current/rollback images,
private models, dumps, evidence and database volumes were protected. Production
mutation by this task was only the receiver/key configuration+backend restart,
two nonbusiness receipts, exact binding and its Owner audit.

At01:05 Toronto both catalogs were re-read after the independent V38 release.
Staging still has299 rules; both Production Stores still resolve299/299 locally,
with no new ambiguity,49 canonical Add-on FK rules,14 canonical Remove FK/
applicability rules and8 COMBO_ROOT triggers each. The V38 review includes
same-Store Remove identity, active state and category/item override eligibility.
Current candidate SHA256:
`8fa25721ea03b148cfe2ab6f20b2b32cdc457fa6de04a61ffa32fb6cd5714ba0`.
Current local artifact:
`/Users/xuzilin/.codex/artifacts/uber-production-20261022/rs-uber-production-mapping-candidates-readonly.json`.
The new candidate replaces only the current artifact; the pre-V38 artifact is
retained separately. Both remain non-executable candidates, external verified0,
unverified299/store, formal persisted0/store. No further human semantic mapping
confirmation is required; authorized Production external IDs are the gap.

After the independent app release, the same focused60 backend tests were rerun
on fresh `d32fa709f2e895ebc4ebf729e8b546d6fd043df8` (4b3abd1 application plus
operator acceptance repair). All60 passed,0 failures/errors/skips; a fresh
local PostgreSQL applied all38 Flyway migrations successfully. This specifically
rechecks the changed OrderService/HOT/Remove dependency graph. Log:
`/tmp/rs-uber-printing-v38-audit-20261022.log`. Android was not rerun because
its source was unchanged. No new build or runtime mutation followed this check.

Receiver readiness is proven for operator-signed receipt only. Real Uber scopes,
order_webhook role, Store Access, Production OAuth, live mappings and field
printing proof remain separate activation requirements. Worker stays false.
