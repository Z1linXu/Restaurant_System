# Staging endpoint policy acceptance, 2026-09-30 EDT

PRIMARY_REPAIR_GOAL: enable Staging endpoint writes while REAL stays forbidden.
EXPECTED_REPAIR_CLASS: SMALL. No application business-source change.

## Audit

Enforcement: `PrintingRuntimePolicyProperties`,
`app.printing.endpoint-configuration-enabled`; `PrinterConfigServiceImpl.savePrinter`
checks this before persistence. It is independent of `app.printing.allowed-modes`.
Staging used `APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED=false` from its
restricted deployment configuration. The new Staging overlay is
`deployment/cloud/staging-pad-direct/backend-environment.json`.

Mode ceiling remains `DISABLED,MOCK,PAD_DIRECT`; endpoint permission changes to
true. REAL is still denied. CloudPrintingGuard, authorization and attestation
remain unchanged. Saving an endpoint does not open a printer socket.

Production audit: both printing policy environment variables are unset; no
external mounts, Spring JSON/config environment overrides or printing/config
argument overrides were found. Exact deployed `11996ef` source has endpoint
permission true and four allowed modes by default, with strict cloud guard.
Those existing Production defaults, configuration and containers are unchanged.
This batch does not tighten or relax Production policy.

`PrintJobServiceImpl.markPadDirectQueued` stores the assigned printer ID,
execution mode PAD_DIRECT, PENDING status and ESC/POS payload. Payload retrieval
resolves the same-Store enabled printer and `PadPrintJobPayloadResponse.from`
returns name/host/port/endpoint/timeout/encoding/paper width. PAD_DIRECT dispatcher
returns before `EscPosTcpPrinterTransport`; Android native performs actual LAN
printing. No payload/schema/application-source change is needed.

## Identities and validation

- Fresh main: `2a37e4bccc3fa1f137791a5fd82461a139acb7c6`.
- Implementation: `2e03b3366312b07e86df8b10051debcb79bfb3d1`.
- [PR 248](https://github.com/Z1linXu/Restaurant_System/pull/248), merged configuration SHA
  `d2316234b2936fd89a926aa24a7b6343008834e6`.
- Application before/after: `7b70a4fac4e350444bcd37687ca88a8636ff8f2a`, V30.
- Backend image reused: `sha256:23f332075f0142f2aeb941da62d58212cd595b1e393a69adb609b4869f18d870`.
- Production: `11996ef919d9b28ee5366c7e40a58a78c074b675`, V28.

62 focused tests passed: actual JSON binding and unchanged shared defaults,
endpoint create/update host/port/timeout/encoding/paper width, REAL rejection,
PAD_DIRECT PENDING and printer ID, returned endpoint fields, zero transport
interaction, MOCK regression and queue/payload isolation. The four unsafe
preflight cases fail closed. Governance and diff checks passed. Agent 6 ACCEPT,
no P0/P1 findings; approved temporary unassigned `.invalid` API verification.

Exact-SHA archive hashes verified on server:

| Artifact | SHA-256 |
| --- | --- |
| policy JSON | `fbe8e8b88982388da64fdcd0eaa3dc8644881619898bc9a000bad712cd1ea88b` |
| bounded adapter | `e3e43402d0f0fa05089b23d535ae1b6bc2e11fe2d0baa4e95ac843f99839ea7e` |
| unchanged ingress primitives | `b7c785a5eadb09c2b39002bceddd925f65fb6be4a8b373e0256fee9ebc830af1` |

Private current-runtime backup:
`/srv/restaurant-pos/staging/pad-direct-endpoints-20260930`.
Immutable execution source:
`/srv/restaurant-pos/staging/endpoint-config-d2316234b2936fd89a926aa24a7b6343008834e6`.
Earlier mode-only backup is untouched. Only Staging backend is in the Compose
apply/rollback model; no build/pull/edge action or direct business SQL.

## Runtime acceptance

ENDPOINT_CONFIG_ALLOWED_BEFORE = NO
ENDPOINT_CONFIG_ALLOWED_AFTER = YES
STAGING_REAL_ALLOWED = NO
STAGING_PAD_DIRECT_ALLOWED = YES
STAGING_ENDPOINT_CONFIG_ALLOWED = YES
STAGING_HEALTH = UP / V30
PRODUCTION_POLICY_CHANGED = NO
PRODUCTION_RESTART = NO
PRODUCTION_DB_MUTATION = NONE

Backend after: `5f312d3880c442d2f3c2c2d5c830fa3429646cad846f8f97cf9ce370bf9e547a`,
started `2026-10-01T03:04:01.436492234Z`. Exact environment/model comparison
passed: endpoint permission is the sole runtime difference. Production's three
containers plus Staging Nginx/DB fingerprints remain unchanged (ID/start/restarts,
image/network/ports/environment hash); both Flyway ledgers are identical. No
Production restart, edge reload, config mutation or database write was requested.

Public HTTPS/CORS/assets/WSS: 42 checks passed.

Authenticated Staging API verification created one uniquely named, unassigned
synthetic printer (ID 11) with a reserved `.invalid` hostname and port 9100,
timeout 3100, GBK, 80mm. Update to a second reserved `.invalid` hostname, port
9101, timeout 4500, UTF-8, 58mm returned the exact new values. No assignment,
print, connection test or job API was invoked. REAL mode update returned HTTP
400 with the expected runtime-policy denial. Store 18 remained PAD_DIRECT.
The exact temporary row was deleted through the same-Store API; final Store 18
printer count returned to zero. All assignment fields remained equal before
and after, and Store 18 print-job count was unchanged. Test session logout 200.
Temporary create/update/delete and login/logout are deliberate Staging API
side effects; this does not claim zero Staging database writes.

Passive `tcpdump` covered backend source IP 172.19.0.3 to TCP destination ports
9100 or 9101 through the safe API acceptance and cleanup: zero packets, zero
kernel drops, capture terminated normally. No backend TCP printer operation was
observed. This window does not prove a real-printer job execution: no confirmed
real endpoint or live PENDING job was available, and no such claim is made.

STORE_18_PRINTING_MODE = PAD_DIRECT
STORE_18_PRINTERS = 0 (temporary API-test row removed)
GRAB_ASSIGNMENT = UNBOUND / unchanged
FRONTDESK_RECEIPT_ASSIGNMENT = UNBOUND / unchanged
HOT_KITCHEN_ASSIGNMENT = UNBOUND / unchanged
PAD_DIRECT_PENDING_JOB_CREATION = UNIT_PASS / ONLINE_NOT_RUN
PAD_DIRECT_PAYLOAD_PRINTER_ENDPOINT = UNIT_PASS / REAL_ENDPOINT_UNCONFIRMED
BACKEND_TCP_9100_CONNECTION = NONE_OBSERVED_DURING_API_ACCEPTANCE

PRODUCT REPAIR: only the Staging endpoint environment permission changed;
application sources/images remain identical. TOOLING / GOVERNANCE: adapted the
existing reviewed helper to the new baseline/backup directory, updated focused
tests, runbook and current state. This was required for safe current-runtime
backup/rollback; no new deployment framework or generic guard relaxation.
Disk before/after 60%, 24 GB free; Build Cache unchanged 14.17 GB with 9.973 GB
reclaimable. Reviewed the >12 GB cache condition: no image build or cache growth,
healthy disk and protected rollback artifacts; no cleanup performed.

## Binding boundary

Initial Store 18: CHINATOWN, organization 1, PAD_DIRECT; no printers and three
unbound module assignments. Other Staging printer rows have blank endpoints.
Existing Production printer addresses belong to Saint-Denis and do not establish
which devices Chinatown testing should use. Owner was asked for printer names,
IPs, ports, GRAB/FRONTDESK_RECEIPT/HOT_KITCHEN mapping, and worker pause status.
No real endpoint is guessed and no Production configuration is copied implicitly.

Without confirmation, stop before real binding and executable PENDING jobs.
Unit payload generation is permitted by the Owner when workers cannot be
confirmed paused; it is not live PENDING/hardware acceptance. Mode/policy rollback
does not disarm already-queued jobs. No physical printing.

OWNER_ACTION_REQUIRED = Confirm Store 18 printer names, IPs, ports, module mapping;
confirm all eligible Pad workers paused before any executable test job.
STOP before unconfirmed real binding, physical printing or Production.
