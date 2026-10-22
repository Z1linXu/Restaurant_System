# 4483 Saint-Denis Production launch preparation — 2026-10-22

## Scope and actual baseline

Owner attachment `b4e83c2f-7342-47a7-a5b9-95c92b04569d/已粘贴的文本.txt`
authorizes4483-only Production credential, verified mapping and launch
preparation. It conditionally permits a controlled first order only when actual
access, role, mappings, Pad/printer readiness and a field window are established.
It does not authorize guessing external IDs, unapproved physical output, Uber
menu/order decisions or `/pos_data`. Sainte-Catherine and Chinatown are excluded.

Fresh main baseline: `1093c0c2400f00d65b75b5c462f0ec2cfc25b6d9`.
Independent clean worktree/branch: `codex/uber-4483-final-mapping`.
The protected original Owner workspace was not changed. Both actual application
environments retain backend/frontend4b3abd1 and FlywayV38; backend image:
`sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5`.
No new application artifact or migration is required for this batch.

## GTS, OAuth and remote access

GTS Case**60916651**, request635DA2, email2026-10-22T04:41:45Z from
`eats-partner-tech-support@uber.com` confirms Production Client ID
`6GaiQpZTcfcuTa150wr4RyztC33PV7LS` has grants `eats.store` and
`eats.store.orders.read`. GTS asks for Store UUIDs for manual integration.
This is current grant evidence; the older Dashboard Requested screenshot is not
the latest grant decision. It does not prove actual OAuth or `order_webhook`.

At the11:54–11:56 Toronto preflight, the Production OAuth Secret was absent.
The independent Primary Signing Key was present and matched the established
receive-only configuration. No key was revealed, rotated, copied to TEST or
written into repository content. Secondary Key remains empty.

The bounded OAuth operator was tested and reviewed, then uploaded0600 to
`/tmp/rs-uber-4483-oauth-prepare-20261022.py` on the verified server. SHA256:
`c64d45184054680230b727d3b146578b2e652aabc26d187f803efd210413ac25`.
Only artifact upload has occurred; upload is not configuration acceptance.
The computer-control tool refuses Terminal access for safety reasons. The Owner
was given a real SSH TTY command to paste the existing PROD Secret into getpass,
with no value in chat/arguments/history/output. Live OAuth/Stores/Menu requests
remain **NOT_RUN** until that hidden input is completed. No token is retained.

The prepared GTS reply identifies4483 UUID
`5a7dca5c-b7ec-57c8-a684-87d23e66e8d9`, asks for manual read-only access and
`order_webhook`, retains Uber Tablet as Order Manager and excludes Store3.
No email was sent by this task. A later direct read of Gmail confirms that the
Owner independently sent the4483 UUID to the same GTS case at11:53:07 EDT on
October22. Do not ask the Owner to repeat that message; no subsequent Uber
reply was present at the current read. Remote manual integration is unconfirmed.

## Mapping reuse and exact unresolved gap

Fresh Staging read:11:56:44 Toronto. Production read:11:56:45 Toronto.

| Fact | Observed result |
| --- | --- |
| Approved semantic rules | 299 |
| ITEM MAP / MODIFIER MAP / NO_OP | 41 / 238 / 20 |
| COMBO_ROOT, included in ITEM count | 8 |
| Store1 uniquely resolved Production targets | 299/299 |
| Canonical Add-on FK references verified | 49 |
| Canonical Remove FK + eligibility references verified | 14 |
| Local missing/ambiguous identities | 0 |
| Verified Production external rules | 0 |
| Actual Production mapping rows | 0 |

Remove selections remain source-kind MODIFIER, mapped to local REMOVE; the
source contains no REMOVED_MODIFIER rules. No local database PK was copied from
Staging. Exact SKU/code/group, parent root, MAP/NO_OP and combo semantics were
reused. Owner does not need to reconfirm the299 unchanged business decisions.

Private local evidence directory:
`/Users/xuzilin/.codex/artifacts/uber-4483-20261022/`.
`production-store1-mapping-candidates.private.json` contains only299 Store1
rules, SHA256 `e331993daa5748ee70e5ad32b31b0180c5b4b32effee9e001aec0d065d7e6271`.
The original TEST menu bytes SHA256 are
`104cd731d21806244778d7cf2b9c442f34d0e116564fc96e934d11711fb3cddc`.
The current299-rule source is paired to41 roots/258 exact modifier contexts.
All artifacts are0600 and are not credentials or database import approvals.

The offline verifier produced `mapping-manifest.private.json` with299 API payload
candidates and `menu-difference-report.private.json`. Without actual Production
menu/read-proof, verified0, persisted0, ready_for_persistence=false; the only
current blocking differences are PRODUCTION_READ_PROOF_REQUIRED and
PRODUCTION_MENU_REQUIRED. This is expected exit2, not a passed external audit.
No fuzzy name expansion, database write, mapping remap or historical change.

Production mapping previews are **NOT_VERIFIED** because formal external IDs
and mappings have not yet been established. No RAW fallback is called complete.
When access is available the reviewed reader+verifier can check IDs/context,
quantity/availability and exact semantics without new core development.

## Binding, webhook provenance and backlog

Current binding id1 remains Store1/org1, production/KITCHEN_MIRROR, enabled,
Owner-confirmed exact4483 UUID. This is local configuration, not a remote Uber
grant. It was created/audited in the previous authorized batch; not recreated.
Store3 remains unbound. No platform ADMIN role was fabricated.

Production URL is
`https://pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook`.
Prior actual runtime-key HMAC acceptance on the current4b/V38 image:
valid200, invalid401, wrong environment400, duplicate200/one durable HELD row.
Evidence: [receiver acceptance](UBER_PRODUCTION_RECEIVER_20261022.md).

The current database contains exactly the two known operator-generated probes:
`3d817c22-89ce-451a-89f1-38f93fa07164` and
`1d477e52-d491-44ca-9c83-8e0433b71c5a`, both HELD_RECEIVE_ONLY.
Thus **official Uber delivery is NOT_VERIFIED**; a Received Event UI does not
prove it. Receiver=true, worker=false; no global or Store cutover. Integration
orders/local Uber orders/Uber Print Jobs remain0. No replay/retry/reset.

Public Production and Staging system health were both200/UP at16:09:00Z.
Production OAuth and role/Store grants remain external acceptance requirements;
no real order or printing activation was attempted.

## Printing and selected Pad

Store1 retains PAD_DIRECT, GRAB printer3 and HOT_KITCHEN printer4. Endpoints were
validated in memory as non-loopback private hosts with valid ports; no socket
or print request was sent. Ten active Android registrations exist. Devices13/14
show recent authenticated API activity at11:54:30/46 Toronto, but installed
version is unknown and heartbeat fields are null. ADB devices0; no install,
unpair or reset. Mere last_seen was not used as worker proof.

A subsequent16:10:10Z read-only log inspection found746 HTTP200 native automatic
pending polls over15:40:11–16:10:08Z; the4 manual polls use a distinct limit.
Backend device-attributed logs identify Device13/Store1 with6 nonempty pending
responses and GRAB/FRONTDESK claim → processing → complete twice each, latest
completion16:02:00Z. These are existing POS activity, not actions by this task.
Thus Device13's native worker is proven online. Device14 and current HOT physical
output remain UNKNOWN: device14 last_seen cannot establish its worker activity.
Android `MainActivity.java` automatic/manual pending paths use limit10/25;
`PadPrintingController` and `PadPrintJobServiceImpl` record the attributed claim
chain. Empty pending responses have no device-attributed INFO log. No raw log,
network address, header, token or customer field was output.

The unchanged4b/V38 contract retains shared GRAB, conditional nonempty HOT,
no Mirror FRONTDESK_RECEIPT and normal POS receipts. Prior focused60 backend
tests after V38 and16 unchanged Android policy/transport tests passed, linked in
the receiver report. This batch does not repeat a real order or physical print.
No APK update is justified by these tooling/configuration-only changes; actual
installed version remains unknown. The current Device13 pipeline demonstrates
backend compatibility without reinstalling; current HOT printer field readiness
and the controlled first-order window remain separate from worker-online proof.

## Delivery and stop condition

Product/core changes: none. Tooling required by this exact batch: same-image
hidden-input OAuth wrapper plus offline stable-ID/menu verifier and focused
mock/offline tests. Existing deployment/backup/rollback/lock mechanisms are
reused. No generalized deployment, identity or mapping framework was added.

SCOPE_EXPANSION_CHECK: product repair size0; tooling consists of two bounded
operators and focused tests. Both are BLOCKING for the explicitly requested
preparation: the old key bootstrap requires zero bindings/V37 and cannot safely
install OAuth while preserving the current bound4b/V38 receive-only runtime;
the Owner specifically requires an automatic external-ID verifier when Store
Access is unavailable. The new OAuth wrapper imports existing reviewed lock,
Compose, backup and HTTP helpers. Core refactoring, a generalized importer,
additional Store provisioning and app deployment are deferred/not required.

The remaining path is hidden credential input, actual GET evidence, GTS manual
Store/role confirmation, verified Store1 mapping import/read-back and previews,
then selected Pad/printer/window proof. Only then select a fresh UTC cutover
for this UUID and enable its controlled first order. An empty watermark is safer
than a stale prefilled cutover while external work is pending. Existing HELD and
ambiguous physical-print history must stay untouched.

Current readiness is **not ready for activation**. Missing evidence is not PASS.

Validation:67/67 Python tests pass (21 offline menu,15 OAuth mock,31 existing
receiver/promotion/verification). Agent6 ACCEPT, all three identified P2s fixed;
no remaining P0/P1/P2. The fixes preserve per-root menu availability attribution,
block same-ID title/external_data drift, and CAS-check before rollback recreate.
Application tests/build/deployment were not repeated because app/device/schema
contracts are unchanged. The actual candidate CLI result remains299/0/0, with
the two missing external proof inputs only. No configured CI checks are assumed.

## OAuth failure follow-up, 17:19–17:26 UTC

The Owner reports running the hidden command and receiving
`UBER_4483_OAUTH_PREPARATION_FAILED:PRODUCTION_OAUTH_NOT_200`. The original
reader received a non200 response but discarded both its HTTP status and error
body. Its failure was before Stores/Menu GET, configuration and restart. An
invalid client, denied scope, rate limit or another response cannot be selected
as the historical cause without evidence. Missing current Secret is the
consequence of not persisting a failed input, not proof of that original cause.

Current read-only runtime/canonical inspection finds OAuth Secret ABSENT and
Signing Key PRESENT, worker=false, same4b/V38 image. Bounded investigation of
the actual historical Production private models/.env and known local .env
paths finds no paired Production OAuth Secret either. No values were output.
The official Production endpoint remains `https://auth.uber.com/oauth/v2/token`;
client_credentials and the requested two scopes are correct. Do not switch
endpoints or use a TEST secret or HMAC key to guess around missing credentials.

Only the existing operator and its tests were repaired. It now reuses a valid
runtime/canonical credential when present; missing credentials require the
existing hidden TTY input once. Sanitized diagnostic proof distinguishes HTTP
status, OAuth error/description, requested scopes and actual allowlisted grants.
SigningKey confusion is rejected before network; error body is bounded in
memory and never logged. Failed attempts do not configure/restart the backend.
Focused operator26/26, entire related Python suite78/78 PASS; Agent6 ACCEPT,
one diagnostic P2 closed, no remaining P0/P1/P2. No duplicate script or app build.

Fresh database read: Staging299 =41 ITEM MAP +238 MODIFIER MAP +20 NO_OP,
including8 COMBO_ROOT; Production Store1 binding correct, formal mappings0,
other-Store mappings0, two HELD events, integration orders0. Actual Production
menu, Stable ID/context differences and migration previews remain unverified
until a real OAuth credential can authenticate. No rule was guessed or written.
