# Uber Kitchen Mirror Production webhook preparation

This runbook describes code and operational gates. It does not authorize any
Production release, restart, credential mutation, Store activation or physical
printing. No live signed Production POST or granted role/scope is proved by the
existence of this document. Current deployment facts belong in governance evidence.

## Endpoints and isolation

| Application | Webhook URL |
| --- | --- |
| TEST App | `https://staging-pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook` |
| PROD App | `https://pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook` |

Keep separate App credentials, signing keys, environments, Store bindings and
databases. Never point a PROD App at Staging or copy TEST compatibility flags to
Production. Credentials belong only in the backend. The Frontend/Android APK
continues using Restaurant_System and needs no Uber secret or token.

The exact webhook POST bypasses POS Bearer login but still requires raw-body
`X-Uber-Signature` authentication and the matching `X-Environment`. Existing
Nginx `/api/` forwarding must preserve method, body bytes and headers. A valid
TLS certificate, no redirect/login/SPA interception, and actual signed POST
results must be checked on the target deployment. Browser GET is only a method
check (405), never evidence of valid signature processing.

## Configuration contract

Use [the Production template](../../backend/.env.uber-eats-production.example).
Spring does not automatically load this file. An authorized operator must inject
the actual keys using the existing private environment/Compose procedure and
verify the resolved backend configuration without printing secrets. Changing a
file on disk alone does not update a running container. Keep the resolved secret
file outside Git/release sources, restricted to its operator (for example 0600
under a 0700 private directory). Do not put secret values in shell commands,
history, browser console, documentation, screenshots, logs or chat.

| Key | Default / requirement |
| --- | --- |
| `UBER_EATS_ENABLED` | false; disabled webhook POST returns 503 |
| `UBER_EATS_WORKER_ENABLED` | code default true for legacy compatibility; Production template explicitly false |
| `UBER_EATS_ENVIRONMENT` | code default sandbox; enabled Production runtime must explicitly use production |
| `APP_ENVIRONMENT` | Production runtime must identify itself as production |
| `UBER_EATS_PROCESSING_MODE` | default ORDER_MANAGER; Production Kitchen Mirror must explicitly select KITCHEN_MIRROR |
| `UBER_EATS_SCOPES` | actual key; Mirror target `eats.store eats.store.orders.read`, critical permission `eats.store.orders.read` |
| `UBER_EATS_CLIENT_ID`, `UBER_EATS_CLIENT_SECRET` | independent PROD App credentials; optional only while receive-only, required before OAuth/processing |
| `UBER_EATS_WEBHOOK_SIGNING_KEY` | explicit matching PROD Primary Signing Key required when enabled; Production cannot use legacy fallback |
| `UBER_EATS_PROCESSING_NOT_BEFORE` | explicit ISO-8601 UTC instant ending Z required before a Production worker can start |

Do not use the nonexistent `UBER_EATS_OAUTH_SCOPES` key. The configured scope
string is sent unchanged in Client Credentials token requests. If the response
contains `scope`, the critical mode permission is checked before caching. Tokens
without the critical permission are rejected; a missing response scope field
retains compatibility. Memory caching, expiry refresh, 401 invalidation and
redacted errors remain in use. No remote Accept/Deny/Cancel, menu or Store
mutation is part of Kitchen Mirror.

The process-wide mode is a permission ceiling, not a second Store identity.
New bindings inherit it; existing per-Store modes remain persisted and each
order freezes its mode. A Mirror process rejects an ORDER_MANAGER mode change
or decision before any token/API write. Do not change historical order modes.

## Receive-only behavior and ACK

`ENABLED=true`, `WORKER_ENABLED=false`, `ENVIRONMENT=production`, global Mirror
and an explicit Signing Key allow receipt without OAuth credentials or Store
Access. HMAC authenticates webhook delivery; OAuth authenticates outbound API
calls and is a different boundary.

For a valid matching-environment receipt, the database transaction must commit
before an empty HTTP 200. Supported events are retained as `HELD_RECEIVE_ONLY`;
unsupported event types are retained as `IGNORED`. No new integration/internal
orders, kitchen tasks, Print Jobs or PAD_DIRECT dispatch are created. Duplicate
event ID with identical body hash returns 200; different content returns 409.

| Condition | Expected POST response |
| --- | --- |
| Valid signature/environment and durable receipt | 200, empty body |
| Invalid signature | 401 |
| Malformed body or foreign environment | 400 |
| Integration disabled | 503 |
| Event ID/body hash conflict | 409 |
| Body exceeds 256 KiB | 413 |
| Database persistence failure | non-200; do not claim receipt |

Disabled integration is not a healthy webhook. Uber may retry non-200 delivery;
finite upstream retries do not replace local durable receipt. A Dashboard save
is not proof of a healthy receiving endpoint or granted role/Store access.

## Backlog inventory and cutover

Turning the worker on must not turn a receive-only backlog into a batch of
kitchen tickets. Held notification/release receipts never become pending merely
because a flag or watermark changes. Pre-watermark pending events become
`HELD_CUTOVER`; delayed remote orders with pre-watermark or unusable `placed_at`
remain held. Existing unimported old integration rows become
`CUTOVER_REVIEW_REQUIRED`. No local order/print history is rewritten.

Cancel/edit are safety signals, including when they were received while paused.
Once processing is enabled, held cancellation/edit events must be applied before
eligible imports; this does not promote the original held notification. A
cancelled/review-required order must not escape via duplicate or later notice.

Before proposing activation, collect a read-only inventory using
[uber-webhook-backlog-readonly.sql](uber-webhook-backlog-readonly.sql). It reports
counts/status/oldest receipt and order ages by Store/environment without request
bodies, customer fields or credentials. Confirm the exact backend JVM ZoneId
first: legacy receipt/retry `TIMESTAMP` columns use that local zone, whereas
`placed_at` is UTC. Database session timezone is not evidence of the JVM zone.
For an authorized connection with the JVM zone independently confirmed as UTC:

```bash
psql -X -v uber_event_timezone=UTC -f deployment/cloud/uber-webhook-backlog-readonly.sql
```

The command requires an already secure connection configuration (service file or
other approved credential injection); never append passwords. Run the inventory
on the intended database and preserve only the safe result. The script is
`BEGIN READ ONLY`; it does not reset events, retry jobs or perform disposition.

Record the approved UTC watermark, eligible Store and first allowed order window,
backlog counts/age, and decision on every held/ambiguous group. Do not delete,
overwrite, reset or blanket requeue events. A historical event may never be
silently discarded. Exceptional recovery requires a separate reviewed and
explicitly authorized disposition; this package provides no replay script/API.
Never auto-reprint a job already in PRINTING or with uncertain physical outcome.

## Preparation and activation sequence

1. Complete code review, required checks and isolated Staging verification at an
   exact reviewed SHA. Keep worker disabled when connecting any retained queue;
   first confirm existing devices cannot consume new jobs. A local mock pass is
   not live Sandbox/Production acceptance.
2. Confirm Uber's Production scope decision, `order_webhook` role and planned
   Store Access procedure. For this integration Uber may handle merchant linking;
   no self-service OAuth onboarding is introduced by this package.
3. Prepare an exact Production release/configuration proposal and obtain Owner
   authorization before deploy/restart/secret changes. Keep the worker false,
   real Store access ungranted, Store modules off and no real Store binding while
   establishing receipt. OAuth credentials may be prepared securely now or later.
4. In the **PROD APP**, choose **Webhooks → Add Webhook → Primary Webhook**, use
   the Production URL above and choose **Basic HMAC**. Configure its actual
   Primary Signing Key in `UBER_EATS_WEBHOOK_SIGNING_KEY`. Do not reuse the TEST
   key or assume it equals the OAuth Client Secret. No Secondary overlap is
   implemented, so any future rotation needs a separately coordinated procedure.
5. If Save generates the Signing Key, use a coordinated setup window with no
   real Store traffic: save, securely retain/inject the generated key through the
   authorized backend procedure, enable receipt with worker false, then verify.
   If the Dashboard requires a healthy endpoint before it will reveal a key,
   stop for the official Uber key/bootstrap procedure; do not bypass HMAC or
   temporarily accept unsigned events. Leave an unhealthy endpoint unclaimed.
6. Perform an authorized nonbusiness signature check: invalid key 401, valid
   event 200 only after persistence, foreign environment 400, duplicate receipt
   one row, and zero new orders/tasks/print jobs. Keep sensitive raw bodies/keys
   out of evidence. Without the actual PROD Signing Key this check is
   `EXTERNALLY_BLOCKED`, never PASS. Verify public HTTPS/proxy behavior separately.
7. Only under separate Store/pilot authority, provide Production OAuth
   credentials, validate actual granted scopes and read-only access, prepare the
   exact real Store binding in KITCHEN_MIRROR and explicitly verified menu IDs,
   then coordinate manual Store Access with Uber. Keep worker false and inspect
   all received/held events; do not replace the Uber Tablet Order Manager.
8. After explicit Owner authorization of real import and physical printing,
   approve the UTC watermark and enable worker for a controlled pilot. Existing
   PAD_DIRECT may print immediately. Confirm one eligible accepted Uber order,
   one local order, expected tasks/outbox and no duplicate automatic jobs. Keep
   ordinary POS lifecycle and the Pad claim/PRINTING/complete/fail state machine.

This batch does not authorize steps that mutate Production or activate Stores.
Production webhook registration is NO-GO until the actual receiving deployment,
matching key and durable ACK are verified; real-order import remains NO-GO until
the separate pilot gate is satisfied. Missing Uber approval, role, Store Access
or actual secrets are external dependencies, not reasons to use fake values.
