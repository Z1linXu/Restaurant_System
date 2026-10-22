# Two-store Kitchen Mirror activation

This is an operator runbook, not authority to activate a Store. The 2026-10-21
release permits code deployment with the Uber worker false and internal
preparation. Missing Uber approval, verified Store identity, actual secrets,
mapping or printer/device proof blocks activation. No Java/React change is
needed after those external facts and configuration are supplied.

## Verified local targets

| Restaurant | Owner-provided address | Production Store / org | Current printing |
| --- | --- | --- | --- |
| Saint-Denis | 4483 Rue Saint-Denis, Montréal | 1 / 1, code `4483_R_SAINT_DENIS` | PAD_DIRECT; same-Store GRAB 3 / HOT 4 |
| Sainte-Catherine | 1395 Rue Sainte-Catherine Ouest, Montréal | 3 / 1, code `ST_CATHERINE` | DISABLED; no printer assignment or Pad |

These local identities were read from Production, not inferred from the numeric
IDs. On 2026-10-22 the Owner explicitly confirmed Uber UUID
`5a7dca5c-b7ec-57c8-a684-87d23e66e8d9` belongs to Store1 at4483 Saint-Denis
and authorized its local production/KITCHEN_MIRROR binding with worker false.
This local identity confirmation does not establish Uber-granted Store Access;
recheck the remote Store name/address once the authorized read API is available.
The second real UUID is missing. Never use the Sandbox Store UUID as a substitute.
Chinatown is excluded; keep its Uber module disabled and create no Uber binding.

## Deployment and safe receipt

Use the reviewed `promote-uber-receive-only-20261022.py` with one full source SHA
and immutable image. It takes both existing operation locks, snapshots private
models, validates a new DB backup, changes only the backend image and allowed
Uber configuration, and preserves every unrelated container/device. Do not run
generic historical deploy templates over these private runtime models.

The target always sets `UBER_EATS_WORKER_ENABLED=false`, global
`KITCHEN_MIRROR`, `UBER_EATS_SCOPES=eats.store eats.store.orders.read` and an empty
`UBER_EATS_STORE_CUTOVERS={}`. Production uses environment `production`. If its
real Signing Key is absent, `UBER_EATS_ENABLED=false`; POST 503 is intentional
and is not a healthy registered receiver. Rollback uses the previous image with
Uber disabled and worker false; it never restores Staging's legacy active worker.
No schema migration beyond the deployed V37 is part of this release.

Run `verify-uber-receive-only-20261022.py staging IMAGE SHA`, then the promotion
helper's `--record-staging-acceptance` with the private `http-proof.json` path.
This retains one synthetic HELD receipt, checks HTTP statuses and zero business
side effects, and records a hash. Production promotion rechecks that evidence
and current Staging image/state before any restart. Production verification is
read-only POS smoke plus disabled POST; missing actual HMAC proof remains BLOCKED.

The verification tool reuses the existing short-lived in-memory POS identity
scheme for authenticated GET smoke. This is not a human password-login test.
It does not submit a live draft, place an Uber order or trigger physical printing.

For an already accepted current Production image, use the narrowly scoped
`configure-uber-production-receiver.py IMAGE FULL_SOURCE_SHA` instead of
rebuilding or replaying the original promotion. Supply a JSON object with only
`primary_signing_key` over protected stdin without echo/TTY, CLI arguments,
shell history or public output. The helper changes only the key/receiver,
preserves OAuth credentials and the image, validates backup and signed HTTP
receipt, and restores a disabled receiver/worker on failure. Private evidence
is stored below `/srv/restaurant-pos/production/uber-receiver-<UTC timestamp>`.
Do this before local binding preparation: the bounded bootstrap requires the
zero-binding baseline. Do not rerun it against a later bound/active Store state.

## Webhook and external grants

1. Obtain the Production scope decision, `order_webhook` role and confirmed
   merchant Store Access from Uber GTS for Client ID
   `6GaiQpZTcfcuTa150wr4RyztC33PV7LS`. GTS should preserve the Uber Tablet as
   Order Manager; do not write `/pos_data`, menu, Store status or order decisions.
2. Keep all real bindings inactive/uncreated and the worker false during key
   bootstrap. In PROD App prepare Primary Webhook / Basic HMAC with
   `https://pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook`.
   Securely inject its actual Primary Signing Key into the existing private
   backend model as `UBER_EATS_WEBHOOK_SIGNING_KEY`; do not reuse either TEST
   OAuth secret or TEST Signing Key, including cross-key substitution.
   Set `UBER_EATS_ENABLED=true` while retaining `UBER_EATS_WORKER_ENABLED=false`
   for receive-only verification; injecting a key alone does not enable receipt.
   The observed Production Dashboard requires an operator-supplied Signing Key
   before Save. The account owner must generate/save it privately and enter it;
   never paste it into chat. The prepared URL alone is not registration.
3. If Dashboard Save generates the key, coordinate a no-real-Store-traffic
   window: save, retain/inject securely, update the receiving backend while
   keeping worker false, verify. If Uber requires a healthy endpoint before
   revealing a key, ask GTS for bootstrap; do not bypass signature validation.
4. Prove valid signed Production receipt commits then returns empty 200;
   invalid signature 401; wrong environment 400; duplicate one row; zero local
   orders/tasks/jobs. Do not mark Webhook READY without this live proof.
5. Inject independent Production OAuth credentials through private 0600 storage.
   Verify requested/actual scopes and authorized read-only Store/menu/order GETs;
   never display token/secret or store them in shell history. A Client ID alone
   is not a credential or evidence of approval.

## Binding and mapping using existing APIs

Keep the worker false throughout preparation. Admin APIs remain Store/org
authorized; no new onboarding system or integration-specific Add-on catalog is
introduced. Use a logged-in authorized operator or the existing controlled API
client, keeping bearer credentials out of command arguments and evidence.

1. Verify each Uber UUID's returned name/address against the table above.
   Read `deployment/cloud/uber-two-store-readiness-readonly.sql` using confirmed
   backend JVM timezone `America/Toronto`; it is `BEGIN READ ONLY` and reports
   menu/FK/printing/backlog facts without customer fields or credentials.
2. Enable only the intended local module with the existing module API:
   `PUT /api/v1/admin/stores/{id}/modules`, body
   `{"store_id":1,"modules":[{"module_key":"UBER_EATS","enabled":true}]}`
   (use 3 for Sainte-Catherine). Other modules must remain unchanged.
3. Platform ADMIN binds the verified UUID through
   `PUT /api/v1/stores/{id}/integrations/uber-eats/store`, body
   `{"uber_store_id":"<verified real UUID>"}`. Read it back: environment
   production, correct Store/org, `KITCHEN_MIRROR`. No placeholders or guessed
   identity may be saved. A module can be prepared without inventing a binding.
4. Read that Store's `mapping-catalog` and `mapping-options/{itemId}`. Resolve
   the previously Human Confirmed semantics to exact active local SKU/option
   identities in this Production Store. Staging numeric IDs are never portable.
   Both current local catalogs resolve the 41 approved roots and eight combo
   triggers; this proves the local half only, not live Uber IDs or full coverage.
5. Fetch each real Store's current Uber menu independently. Preserve external
   stable IDs and parent context. Compare against the actual selected menu;
   never generate mappings by fuzzy/display name or blindly copy TEST IDs.
6. Import each explicitly verified rule through the existing idempotent
   `PUT /api/v1/stores/{id}/integrations/uber-eats/mappings`. Its JSON uses the
   actual entity keys `kind`, `identifierType`, `uberIdentifier`, `uberItemId`,
   `localMenuItemId`, `localOptionCode`, `localOptionGroup`, `parentOptionCode`,
   `mappingAction`, `itemMappingMode`, `actionReason`. Root `uberItemId` is empty;
   modifiers require the real parent root ID. Read back every rule.
7. Use `COMBO_ROOT` for a combo root, unique local `COMBO/combo` injection and
   existing `COMBO_EGG` / `COMBO_SIDE` choices. Canonical Add-ons must resolve
   through the same-Store option assignment's `store_addon_id` FK; code/name
   similarity does not replace that relationship. NO_OP requires the approved
   `INGREDIENT_NOT_USED` reason. Retain unknowns visibly; do not drop modifiers.
8. POST representative actual-menu snapshots to `mapping-preview` (read-only
   semantics). Require errors=[] and warnings=[] and no raw fallback for the
   planned pilot menu, including size/add-on/remove/combo/notes quantities.
   Mirror `valid=true` alone is insufficient because raw fallback is allowed
   for future drift. Mapping writes must occur while worker=false: saving calls
   `remapStore`; it must not release held kitchen orders.

## Controlled first-store activation

Before enabling any import, record all of: actual scope/role/Store Access proof,
Production HMAC acceptance, verified local/external UUID, mapping read-back and
zero-warning previews, each Store's own printer routing, recent correct Pad
worker proof, and the approved first-order/time window. Sainte-Catherine's
missing printer/device configuration must be resolved through the existing
printing setup, not by borrowing Saint-Denis endpoints. Registration rows alone
are not proof of a live Pad or physical output.

Run the read-only backlog inventory first. Preserve all HELD/CUTOVER/PRINTING/
FAILED history; never blanket requeue, delete, reset or retry ambiguous prints.
Select a UTC cutover after the historical orders. The effective boundary is
`max(UBER_EATS_PROCESSING_NOT_BEFORE, store-specific cutover)` and checks both
receipt time and remote `placed_at`. Previously HELD notifications are never
automatically promoted, even after configuration changes.

During the separately approved pilot, set the global UTC watermark and a private
`UBER_EATS_STORE_CUTOVERS` JSON object containing only the first verified Uber
Store UUID mapped to its approved UTC ISO-8601 Z instant. Keep the second Store
module off/unlisted. Only then enable the global worker in the controlled
private model and deploy that configuration under the existing locks. Production
startup fails closed without both global and nonempty valid per-Store cutovers.
This activation is intentionally not a mode of the receive-only promotion helper.

Observe one naturally accepted/approved pilot order: no pre-ACCEPTED import,
exactly one local order, correct Store/tasks/outbox and unique GRAB/HOT jobs.
Uber Tablet continues Accept/Deny. Do not infer paper output from database status.
Enable the second Store only after its own independent readiness/window proof;
add its verified UUID/cutover, retaining the first Store's unchanged watermark.

## Pause and resume one Store

- Pause using its existing module API with `UBER_EATS=false`; leave the other
  Store and ordinary POS/printing modules unchanged. This stops new Uber
  processing; it does not retract an already-running submission transaction,
  committed orders or queued Print Jobs.
  Inspect existing jobs and handle an emergency separately. Never blindly reprint.
- For a resume, first leave that module off, inspect its retained backlog,
  advance only that Store's UTC cutover to the newly approved window in the
  private runtime configuration, and restart under deployment locks. Then
  re-enable that Store module. Do not move the other Store's boundary.
  This configuration restart affects the shared backend; independent Store
  cutovers do not imply a restart-free or zero-interruption configuration change.
- Global worker=false pauses new processing for all Stores while preserving
  receipt. It cannot undo already queued physical work. Disruptive events remain
  durably retained; reconciliation checks the cancellation/edit ledger before
  any later release. No task/job state is reset by pause/resume.

No scope grant alone enables printing. External confirmations plus data/config
and controlled acceptance are the remaining work; no additional core API or UI
development is required for these two Stores.

## 4483-only preparation after the Production scope grant

The 2026-10-22 Owner batch targets only Store1 at4483 Saint-Denis. Do not
activate, bind or migrate Store3 as part of that batch. GTS Case60916651 granted
`eats.store` and `eats.store.orders.read` to the Production Client ID and asked
for the merchant UUID for manual integration. That email proves the grant
decision; actual OAuth, Store Access and `order_webhook` remain separate checks.
The accepted application is4b3abd1/V38. Do not replay the historical V37
promotion/bootstrap or its old-image rollback over this runtime.

`configure-uber-4483-oauth-20261022.py` is the bounded same-image credential
operator. It uses existing locks, canonical private Compose, validated database
backup, HTTP acceptance and rollback helpers. On the verified server, run it
from a real SSH TTY and enter the existing PROD App Client Secret at its hidden
prompt. Never supply it as argv, shell text, a chat message or an exported file
in Git. It refuses echo fallback, a different existing Secret, image drift,
unexpected bindings, nonempty cutovers or pending business queues. Only
`UBER_EATS_CLIENT_SECRET` may change; Primary Signing Key, receiver=true,
worker=false, all other environment values and unrelated containers stay intact.
It does not create, rotate or revoke an Uber credential. OAuth POST is the only
POST to Uber; the Eats calls are only Stores GET and the exact4483 Menu GET.

If the current runtime and canonical private model already contain the same
OAuth Secret, the same operator reuses it without prompting or requiring TTY.
Conflicting models fail closed. Only an absent OAuth Secret requires hidden
input; the Webhook Signing Key is never its substitute. A matching SigningKey
input is rejected before any OAuth request. The original generic
PRODUCTION_OAUTH_NOT_200 result cannot recover a discarded historical response.
Current failures report bounded/sanitized HTTP status, allowlisted OAuth error,
redacted error description and requested scopes; scope-denied200 also records
allowlisted actual grants. Failure writes only0600 safe evidence to the existing
root's sibling `uber-4483-final-20261022-oauth-failure.json`, without credential
installation/restart or raw response/header/token output. Reuse this operator;
do not create a second credential-input script or blindly change endpoints.

The private output root is
`/srv/restaurant-pos/production/uber-4483-final-20261022`. Token stays in memory;
successful menu bytes and their authenticated read-proof are retained0600.
Store count, target-listed flag and Menu HTTP status are separate evidence.
A Menu GET200 proves readable menu provenance, not `order_webhook`, remote
Order Manager ownership or authorization to activate printing. A0-store list
with a readable menu is a discrepancy to resolve with GTS before activation.
Store403/0 stores does not prevent safely configuring credentials after a
successful read-scope OAuth grant. Failed acceptance restores the original
same-image receive-only environment; canonical-file drift stops before a
rollback recreate. Never apply the old7d receiver rollback to4b/V38.

`uber-4483-menu-verify.py` is offline and never writes a database or calls an
API. Supply the approved raw TEST menu, its299 approved rule contexts, freshly
resolved Production Store1 candidates, the actual Production menu bytes and
the reader's original proof. It requires the exact Client ID/Store UUID/GET
URL/HTTP200, actual read scope, raw-byte hash and a recent UTC observation.
The proof is operator-recorded provenance, not an Uber-signed attestation; keep
the original response/proof pair private and do not manufacture one from TEST.
Stable root/group/modifier IDs and parent context must agree, including group
limits, quantity and availability. New/missing IDs or unsupported nesting block
persistence and appear in a precise difference report. Names are not matched.

Example with private input/output paths, no credential arguments:

```bash
python3 deployment/cloud/uber-4483-menu-verify.py \
  --baseline "$PRIVATE_INPUT/approved-test-menu.private.json" \
  --approved-contexts "$PRIVATE_INPUT/approved-contexts.private.json" \
  --candidates "$PRIVATE_INPUT/production-store1-mapping-candidates.private.json" \
  --production-menu "$PRIVATE_INPUT/production-menu.json" \
  --read-proof "$PRIVATE_INPUT/production-menu-read-proof.json" \
  --output "$PRIVATE_OUTPUT/mapping-manifest.private.json" \
  --diff-output "$PRIVATE_OUTPUT/menu-difference-report.private.json"
```

Exit2 is an incomplete/unverified candidate report, never an import approval.
Without menu/read-proof the same command, omitting those two inputs, still
exports the299 Store1 API payload candidates with verified0 and persistence
disabled. Store3 candidates are excluded. The tool writes no mapping itself.
Immediately before any later authorized import, re-read the local Store1
catalog/FKs/eligibility, confirm worker=false, back up, then use the existing
idempotent mapping API or an explicitly reviewed bounded maintenance transaction.
Do not fabricate a platform ADMIN to bypass the binding API. Read back every
rule and run actual-menu previews with no errors/warnings/RAW fallback.

For activation, choose a fresh UTC cutover at the controlled field window;
do not prefill a timestamp while access or Pad readiness is pending. Configure
global and per-Store watermarks for only the4483 UUID. Preserve all old HELD
receipts and ambiguous print history. Existing API activity/registration alone
does not establish a healthy native print worker or installed APK version.
Keep worker=false until access/role/mappings, selected Pad worker, printers and
first-order window are proven. No first-order or paper PASS may be inferred
from the offline verifier or synthetic HMAC receipt.
