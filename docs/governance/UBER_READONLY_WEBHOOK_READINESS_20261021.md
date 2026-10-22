# Uber Kitchen Mirror read-only scopes and webhook readiness — 2026-10-21

## Authority and delivery identity

Owner attachment: `21bb8e26-32b1-439d-98d1-7db01ef11503/已粘贴的文本.txt`.
This batch authorizes implementation, tests, required review, a reviewable PR and
safe Staging verification. It does not authorize Production deployment/restart,
secret changes, real Store binding, Uber writes or physical printing.

- Fresh implementation authority: `origin/main` at
  `06bac51ddc0a829a9aa1106f07ef886572efbb65`.
- Branch: `codex/uber-readonly-webhook-readiness`.
- Isolated managed worktree:
  `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System`.
- The original Owner worktree has 128 protected status entries and was not
  reset, cleaned, checked out, staged or committed by this batch.
- PR/commit identity is the commit containing this evidence; the PR URL is
  attached to the delivery. No claim of merge or deployment is made.

`PRIMARY_REPAIR_GOAL`: support Kitchen Mirror read-only OAuth and durable
receive-only Production webhook preparation without enabling real import.
`EXPECTED_REPAIR_CLASS`: MEDIUM. Product code, tests and operational instructions
are bounded to the existing Uber integration. No second order/printing pipeline,
schema migration, Frontend or Android change was introduced.

## A. Implementation summary

Startup validates an explicit process-wide mode before Store bindings exist.
`KITCHEN_MIRROR` accepts `eats.store eats.store.orders.read`; its critical order
permission is `eats.store.orders.read`. `ORDER_MANAGER` retains `eats.order`.
New bindings inherit the process mode. The Mirror process rejects Manager mode
changes and remote decisions. Existing per-Store mode checks remain enforced.

OAuth sends the configured scope string, checks a returned scope field before
caching, and preserves cache expiry, refresh/401 handling and redacted failures.
GET order remains `/v2/eats/order/{id}`. Only accepted, safely mapped eligible
orders enter the existing local transaction/outbox/printing flow.

Receive-only startup needs a matching explicit Production HMAC key but can omit
outbound OAuth credentials. Recognized events are durably retained as
`HELD_RECEIVE_ONLY`, then ACKed with an empty HTTP 200. While paused there are no
integration-order placeholders, local orders, kitchen tasks, Print Jobs or
remote decisions. Direct processing/manual mutation entry points also fail
closed. A DB persistence/commit failure cannot return a successful receipt.

Held notification/release receipts are never automatically promoted. Production
worker startup requires an explicit UTC `UBER_EATS_PROCESSING_NOT_BEFORE`.
Receipt time and fetched `placed_at` protect pending/delayed events; existing
unimported old rows become `CUTOVER_REVIEW_REQUIRED`. Cancel/edit safety signals
remain effective, including when held behind a worker page: the order lock
checks the durable event ledger before release. No history is deleted or reset.

## B. Files changed

Full paths refer to the isolated review worktree, not the protected Owner tree.

| Full path | Change |
| --- | --- |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java` | Mode-aware scopes, worker gate, Production environment/key validation |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/client/UberEatsOAuthClient.java` | Critical granted scope validation before caching |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/client/UberEatsOrderClient.java` | Global Mirror/paused write denial; existing GET retained |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsWebhookController.java` | Narrow GET 405 with `Allow: POST` |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsEventRepository.java` | Held cancellation/edit selection without replaying held notifications |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsConfigurationService.java` | New binding inherits mode; Mirror cannot become Manager |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderImportService.java` | Paused processing/decision guards |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderTransactions.java` | Cutover guards and durable disruptive-event ledger before release |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsProcessingGate.java` | UTC startup and receipt/order watermark policy |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsWebhookService.java` | Durable receive-only events without inline order creation |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsOAuthClientTest.java` | Read-only GET/scopes/token/remote decision tests |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPropertiesTest.java` | Startup mode/scopes/environment/explicit key tests |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsProcessingGateTest.java` | UTC cutover and disabled optional integration tests |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java` | Real PostgreSQL receipt/rollback/paused/idempotency/cutover/ledger tests |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/.env.uber-eats.example` | Existing Sandbox compatibility and explicit mode/worker documentation |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/backend/.env.uber-eats-production.example` | Safe disabled/paused template with blank credentials/key |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/deployment/cloud/README_UBER_PRODUCTION_WEBHOOK.md` | Secret injection, ACK contract, cutover, Dashboard/activation sequence |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/deployment/cloud/uber-webhook-backlog-readonly.sql` | Read-only counts/ages with explicit backend JVM timezone |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/SYSTEM_DOCUMENTATION.md` | Current integration contract |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/doc/API.md` | Webhook/status/mode API contract |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/docs/governance/CURRENT_STATE.yml` | Current implementation/review and environment limits |
| `/Users/xuzilin/.codex/worktrees/uber-readonly-webhook/Restaurant_System/docs/governance/UBER_READONLY_WEBHOOK_READINESS_20261021.md` | This evidence |

Product repair is the ten existing/new product Java files above. Tooling /
governance changes are limited to requested templates, the read-only inventory,
runbook and required documentation/evidence; they do not introduce a deployer,
replay mechanism or new credential lifecycle. No runtime tooling was executed
to mutate Staging or Production.

## C. Read-only scope compatibility and protocol basis

Before: enabled integration always required `eats.order`, including Mirror.
After: explicit global Mirror requires `eats.store.orders.read`; Manager still
requires `eats.order`. These validations are not removed or globally bypassed.
The actual configuration key is `UBER_EATS_SCOPES`, not
`UBER_EATS_OAUTH_SCOPES`.

Official sources checked 2026-10-21:

- [Get Order Details](https://developer.uber.com/docs/eats/references/api/v2/get-eats-order-orderid)
  permits `eats.store.orders.read` for the v2 GET endpoint.
- [Authentication](https://developer.uber.com/docs/eats/guides/authentication)
  documents Client Credentials and scopes. `eats.store` itself can include
  store/menu writes: this implementation's Mirror write denial is an
  application boundary, not a claim that every requested scope is inherently
  read-only.
- [Webhooks](https://developer.uber.com/docs/eats/guides/webhooks) describes raw
  body SHA256 HMAC, lowercase hex `X-Uber-Signature`, empty 200 ACK and retries
  on 503. Its legacy text names Client Secret; the actual Dashboard Basic HMAC
  Signing Key must be used and verified for this specific PROD App. This batch
  does not claim an untested Production key is valid. Existing TEST explicit
  signing-key evidence remains historical, not a new Production pass.

## D. Webhook readiness

| Case | Code/test result |
| --- | --- |
| Valid signed matching-environment event | Durable commit then empty 200 |
| Invalid signature | 401 |
| Foreign/missing environment | 400; exact existing TEST exception remains Sandbox-only |
| Integration disabled | 503; not a healthy registered receiver |
| Persistence failure | Non-200; no false ACK |
| Identical duplicate | 200; one event, no duplicate processing |
| Same event ID / changed body | 409 |
| Worker disabled | Held durable event; zero import/printing/mutation |
| GET webhook | Code returns 405, `Allow: POST`; old live deployment unchanged |
| Historic backlog | Held receipts retained; UTC cutover guards pending/recovery/manual paths |

Production uses only `UBER_EATS_WEBHOOK_SIGNING_KEY`; it cannot fall back to the
OAuth Client Secret, including when startup validation is bypassed. Sandbox
fallback is retained for existing installations. Constant-time digest comparison
is unchanged. No unsigned acceptance, sensitive raw-body logging or secondary
key overlap was introduced.

## E. Automated verification and required review

All Uber API interactions in this batch's tests use mocks. PostgreSQL databases
are dedicated local synthetic databases; no remote business database is written.

| Check | Result / evidence |
| --- | --- |
| Complete backend `mvn -q -f backend/pom.xml verify` | PASS: 986 tests, 0 failures/errors, 8 conditional skips; `/tmp/rs-uber-ro-backend-verify.log` |
| Relevant conditional migration tests explicitly rerun | PASS: 3 tests, 0 skips; `UberStoreModuleMigrationTest`, `StoreAddonPostgresMigrationTest`; `/tmp/rs-uber-ro-migrations.log` |
| Final complete `UberEats*Test` after additional pagination test | PASS: 98 tests, 0 failures/errors/skips; 69 PostgreSQL, 11 OAuth, 10 properties, 5 cutover, 3 signature; `/tmp/rs-uber-ro-isolationfix.log` |
| Read-only backlog SQL on dedicated local PostgreSQL | PASS; `/tmp/rs-uber-ro-backlog-sql.log` |
| Governance validation / diff whitespace / changed-file credential pattern scan | PASS; 22 changed files scanned, no credential-pattern hits; blank Production template explicitly included |
| Agent 6 consolidated product/security/reliability review | ACCEPT; no remaining reproducible P0/P1/P2 |

The backend verify's remaining five skips are unrelated optional tests:
`PrintingReliabilityPostgresMigrationTest` (1),
`OwnerStoreMenuClonePostgresIntegrationTest` (3), and
`PrintingDisplayRuleLifecyclePostgresIntegrationTest` (1). The three directly
relevant migration cases required `127.0.0.1` in their conditional URL and were
explicitly rerun successfully. Counts across these runs overlap and must not be
added together as unique cases.

During implementation, test fixture column/timezone assertions were repaired.
The additional pagination test then exposed cross-method synthetic backlog
leakage in the full Uber run; its `finally` now retires only its unique synthetic
Store/UUID's pending fixtures after all assertions. Complete rerun passed with
no weakened assertions.
No failing test is waived. Agent 6 reviewed the product diff; it did not execute
remote acceptance or independently rerun Maven.

## F. Staging verification and deployment decision

Read-only observation: 2026-10-21 21:48:23–21:48:24 America/Toronto, plus the
21:39 queue/preflight inventory. Safe output:
`/tmp/rs-uber-readonly-webhook-preflight-20261021.json`, SHA256
`0d10a861b798331e95cbc5773cf54b5431375a4206e36fe8bb9fd7fdac82c782`.
Collection used filtered SSH inspect, public HTTPS GET and `BEGIN READ ONLY`
aggregate SQL. No unsigned or signed public POST was executed in this refresh.

| Fact | Observation |
| --- | --- |
| Staging and Production health | HTTP 200 / UP |
| Live webhook GET, both environments | HTTP 500, no redirect; the 405 repair is code-tested only |
| Both backend deployments | `ce35278b3569a4eacfa29efc01cadbf0c409bec6`; Flyway V37 |
| Staging Uber | Enabled Sandbox; OAuth/signing key PRESENT; worker flag unset => existing true default |
| Staging binding/mapping | One Kitchen Mirror binding; 299 mapping rules |
| Staging events | 13 COMPLETED, 8 IGNORED; no current due event |
| Staging integration rows | 13 total: 6 released, 5 warnings, 2 held |
| Staging existing local Uber orders / print jobs | 11 / 28; no new business side effects in this batch |
| Staging active queue snapshot | 0 PENDING/CLAIMED print jobs; 0 PENDING/PROCESSING outbox |
| Retained ambiguous Uber prints | GRAB #200/#205/#207 PRINTING; #209/#212 FAILED; no retry/reset performed |

**LIVE_STAGING_DEPLOY = BLOCKED.** The existing shared Staging still has active
Sandbox input and PAD_DIRECT devices. Its reviewed backend promotion helper
preserves `worker=true`; an empty queue snapshot does not prove it will remain
empty during deployment. Generic deployment tooling rejects the real endpoints
and is not an equivalent safe substitute. The retained PRINTING jobs are not
claimed to auto-reprint (the pending-job query excludes PRINTING); their
physical outcomes are simply not reset by this batch.

The authorized safe alternative was executed: isolated local Spring MockMvc /
mock Uber / real PostgreSQL tests. This is a PASS for isolated acceptance, not
a new public Staging deploy, real Sandbox OAuth test or physical-print pass.
No new Staging orders or physical jobs were created. A reviewed paused-worker
deployment procedure is a separate bounded follow-up, not a reason to improvise
a second deployment system in this batch.

## G. Production safety

Production remains on the existing backend; no deploy/restart/env/DB/Store/Pad
mutation was performed. Observed Uber disabled, credentials/signing key ABSENT,
active bindings 0, mapping rules 0, all three Store Uber modules disabled,
integration/local Uber orders 0 and Uber print jobs 0. Ordinary POS/PAD_DIRECT
operation remains independent and unchanged.

Public HTTPS and existing `/api/` route evidence do not prove successful
Production HMAC delivery. With disabled Uber, POST is expected to return 503
from the deployed code (not a new live POST measurement); a valid-key
Production receipt is **EXTERNALLY_BLOCKED_SIGNING_KEY_NOT_CONFIGURED**. No
live signed Production POST was fabricated, no Uber order was created, and no
Accept/Deny/Cancel/`pos_data`/menu/store write was invoked.

## H. External dependencies

1. Uber Production scope approval and actual granted permissions.
2. PROD App `order_webhook` role and the coordinated Store Access procedure.
3. Independently secured PROD OAuth credentials and matching Basic HMAC key.
4. Separately authorized exact-SHA receive-only Production release and live
   durable ACK acceptance; current task forbids that release.
5. Separate real Store/menu/pilot and physical-print authorization before import.

## I. Dashboard / secure configuration sequence

Full instructions: `deployment/cloud/README_UBER_PRODUCTION_WEBHOOK.md`.

Do not click Save now on the assumption that the current Production receiver is
healthy. Prepare the reviewed receive-only release and obtain its exact
Production authorization first. In the **PROD APP**, prepare **Webhooks → Add
Webhook → Primary Webhook**, delivery URL
`https://pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook`,
authentication **Basic HMAC**. Keep TEST pointing to Staging.

Use the PROD webhook's actual Primary Signing Key in backend private
`UBER_EATS_WEBHOOK_SIGNING_KEY`; never paste it into chat/Git or use the TEST
key. If Save generates the key, coordinate a no-real-Store-traffic setup window:
save, securely inject the key using the authorized runtime procedure, enable
receipt with worker false, then prove valid durable 200 / invalid 401 / duplicate
one-row / zero business side effects. If the Dashboard requires a healthy
endpoint before revealing the key, obtain Uber's bootstrap procedure rather than
bypassing HMAC. No claim of Production readiness until that proof exists.

Configure independent OAuth credentials before outbound GET verification. Keep
worker false through Store Access, binding and mapping preparation. Only a
separate authorized pilot may approve the UTC cutover and enable accepted-order
import and existing PAD_DIRECT printing.

## J. Delivery decision

Code and isolated tests are distinct from live environment readiness.

```text
READ_ONLY_SCOPES_SUPPORTED = YES
KITCHEN_MIRROR_GET_ORDER = PASS (MOCK)
KITCHEN_MIRROR_REMOTE_MUTATIONS_BLOCKED = YES
ORDER_MANAGER_COMPATIBILITY = PASS
SANDBOX_REGRESSION = PASS (AUTOMATED; NO NEW LIVE SANDBOX ORDER)
PRODUCTION_WEBHOOK_HMAC_CODE = READY
PRODUCTION_WORKER_DISABLED_SAFETY = PASS (ISOLATED POSTGRES)
WEBHOOK_DURABLE_ACK = PASS (ISOLATED POSTGRES)
DUPLICATE_WEBHOOK_GUARD = PASS
BACKLOG_SAFE_CUTOVER = PASS (ISOLATED TESTS)
PRODUCTION_BUSINESS_ORDERS_IMPORTED = 0
PRODUCTION_UBER_PRINT_JOBS_CREATED = 0
PRODUCTION_STORE_BINDING_CHANGED = NO
PRODUCTION_DEPLOYED = NO
LIVE_PRODUCTION_SIGNED_POST = EXTERNALLY_BLOCKED
LIVE_STAGING_DEPLOY = BLOCKED
PHASE_1_RESULT = PASS
PHASE_2_CODE_READINESS = PASS
PRODUCTION_WEBHOOK_REGISTRATION_GO = NO
PRODUCTION_UBER_ORDER_IMPORT_GO = NO
```

Code review does not grant Uber verification/Production approval. No merge or
Production continuation is inferred from this delivery.
