# Bounded Staging PAD_DIRECT endpoint policy, 2026-09-30

PRIMARY_REPAIR_GOAL: allow Staging printer endpoint configuration while keeping
REAL forbidden. EXPECTED_REPAIR_CLASS: SMALL. This supersedes the earlier
mode-only batch; its historical artifacts remain at configuration SHA 7ab9366
and in the separate private `pad-direct-20260930` backup directory.

`PrintingRuntimePolicyProperties` binds two independent properties:
`APP_PRINTING_ALLOWED_MODES` and `APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED`.
`PrinterConfigServiceImpl.savePrinter` guards endpoint writes with the second;
Store mode changes use the first. No business-code change is necessary.
The reviewed JSON retains `DISABLED,MOCK,PAD_DIRECT` and changes endpoint
configuration from false to true. REAL remains rejected. `CloudPrintingGuard`
and device attestation stay intact; endpoint permission does not invoke TCP.

`PrintJobServiceImpl.markPadDirectQueued` stores assigned printer ID and ESC/POS
bytes in a PENDING PAD_DIRECT job. `PadPrintJobServiceImpl` resolves that
same-Store enabled printer, and `PadPrintJobPayloadResponse.from` returns its
name, host, port (default 9100), endpoint, timeout, encoding and paper width.
Dispatcher PAD_DIRECT returns before backend transport. Native Android performs
LAN printing after claim/payload retrieval.

The normal `staging-deploy.sh` / synthetic guards remain restricted to MOCK and
DISABLED with endpoint writes disabled. This explicitly authorized exception is
not a generic default. Future deployments must intentionally preserve/retire
this overlay and retain the HTTPS overlay. No Production configuration changes.

## Execution

Export artifacts from the merged full configuration SHA; verify SHA-256 on the
host, including reused `staging-ingress/apply-once.py`. Application images stay
immutable at `7b70a4fac4e350444bcd37687ca88a8636ff8f2a`, Flyway V30. Record the
configuration SHA separately; there is no application build.

On the existing host as root, run `apply-once.py prepare`, then `apply`. The
existing bounded helper takes both operation locks, privately snapshots current
backend/configuration and unchanged-container fingerprints, and resolves a
backend-only Compose model. Preflight requires the exact three-mode allowlist,
endpoint false, cloud/Staging, printing feature true, Chinatown Store 18 already
PAD_DIRECT, no other PAD store, and no executable queue or nonterminal outbox.
It recreates only Staging backend, preserving image/resources/IP/HTTPS settings
and all environment values except endpoint false -> true. No build/pull, direct
business SQL, new migration, Nginx action, or Production mutation is requested.

Private current-runtime backup and rollback model:
`/srv/restaurant-pos/staging/pad-direct-endpoints-20260930`.
`verify` compares the exact expected backend model, both Flyway ledgers, and all
Production plus Staging Nginx/DB fingerprints. Failed apply restores the captured
backend configuration. `rollback` never changes another service. Old mode-only
backup is not overwritten or reused. Generic deployment tooling is unchanged.

## Acceptance and device boundary

Focused tests cover actual JSON binding, REAL denial, endpoint create/update,
PAD_DIRECT PENDING/printer ID/ESC-POS payload, returned endpoint fields, zero
backend transport calls, MOCK regression and unchanged shared defaults.

Online API create/update acceptance may use one uniquely named, unassigned
synthetic printer on reserved `.invalid` hostnames, changing host/port/timeout,
then deleting that same exact row. Never assign it, invoke print/test-connection,
or create a job. Verify assignment contents unchanged. Use Owner authentication
from the existing private credential and log out without printing secrets.
Recheck REAL rejection and Store 18 PAD_DIRECT; run public HTTPS/WSS regression.

For actual Store 18 bindings, use only Owner-confirmed test printer name, host,
port and module mapping. Other-Store/Production addresses are not implicit
Chinatown authorization. Missing identity means stop and ask; do not guess LAN
addresses or create duplicate printer rows for modules sharing one device.
Preserve assignment font size, copies and enabled when binding GRAB,
FRONTDESK_RECEIPT and HOT_KITCHEN.

Before creating any executable PENDING job, confirm all eligible Android workers
are paused and establish that test jobs remain non-executable until deliberate
Owner physical acceptance. Queue claim does not recheck mode/policy; rollback
or expiring device affinity is not a disarm mechanism. If worker pause cannot be
confirmed, exercise only safe configuration and unit payload generation paths.
Do not fabricate online PENDING or hardware PASS. No physical printing.
