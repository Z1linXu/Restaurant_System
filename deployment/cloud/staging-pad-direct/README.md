# Bounded Staging PAD_DIRECT policy, 2026-09-30

PRIMARY_REPAIR_GOAL: permit PAD_DIRECT for the explicitly authorized Android
acceptance while keeping REAL forbidden. EXPECTED_REPAIR_CLASS: SMALL.

`PrintingRuntimePolicyProperties` binds `APP_PRINTING_ALLOWED_MODES`. The
existing Staging Compose default is `DISABLED,MOCK`, so both PAD_DIRECT and REAL
are rejected before Store persistence. This is an environment ceiling, not a
missing feature or hardcoded cloud prohibition. `CloudPrintingGuard` remains
unchanged. PAD_DIRECT queues payloads; only the Android Pad connects to LAN.

The reviewed `backend-environment.json` changes exactly one environment value
to `DISABLED,MOCK,PAD_DIRECT`. It does not enable endpoint configuration, change
feature flags, weaken device attestation, or change any Production policy.
The normal `staging-deploy.sh` / synthetic guards remain restricted to MOCK and
DISABLED. This is an explicitly authorized one-off exception, not a new default.
Future deployment must intentionally preserve or retire this reviewed overlay,
and retain the existing HTTPS overlay; do not use the generic deploy wrapper
unmodified for this hardware acceptance runtime.

## Execution

Use files from the merged full configuration SHA only. Record that SHA and
SHA-256 hashes of both this directory's artifacts and the reused
`staging-ingress/apply-once.py`. Application images stay immutable at application
SHA `7b70a4fac4e350444bcd37687ca88a8636ff8f2a`, Flyway V30. Configuration SHA and
application SHA are different identities; do not claim an application rebuild.

On the existing Staging host, as root, run `apply-once.py prepare`, then `apply`.
The helper reuses the reviewed ingress model, read-only fingerprint, health and
Compose primitives. It takes both operation locks, guards Staging/cloud/feature
and endpoint settings, privately snapshots the current backend, resolves a
backend-only Compose model, and recreates only that backend with the same image,
resources, network/IP and complete environment except the reviewed allowlist.
The preflight rejects any existing PAD_DIRECT Store, executable print job or
nonterminal dispatch outbox work across Staging before enabling the allowlist.
No build, pull, migration or business SQL is requested. Existing V30 means
startup Flyway has no pending migration; compare both ledgers afterward.
Private backups are under `/srv/restaurant-pos/staging/pad-direct-20260930`.
Do not publish their environment content. `verify` checks unchanged Production,
Staging Nginx/DB, Flyway and exact backend configuration. Failed apply triggers
backend-only rollback. `rollback` restores the captured backend environment;
it never touches the Production edge or restarts another service.

SCOPE_EXPANSION_CHECK: no product source repair is needed. The small batch
adapter is BLOCKING because the general deployment guard deliberately rejects
PAD_DIRECT and must retain its default boundary; it reuses existing reviewed
primitives instead of creating another deployment framework. General guard
redesign and endpoint-policy changes are out of scope.

## Non-paper acceptance boundary

Target only Store 18, code CHINATOWN, organization 1. Use existing Owner
`PUT /api/v1/admin/printing/status` with `printing_mode=PAD_DIRECT`; verify the
response and persisted mode. Do not mutate historical orders, Master data,
printer assignments, pairing, credentials or Production data. Authenticate with
the existing private Staging credential without logging secrets.

Before making eligible PENDING jobs, pause all eligible Pad workers and confirm
how the new jobs will remain non-executable until Owner deliberately begins
physical acceptance. Policy or Store rollback does **not** disarm queued jobs:
queue list/claim does not check the current mode ceiling. Device affinity expires
and is not a safety barrier. Do not create a real endpoint/device to work around
a missing prerequisite. No endpoint or enabled assignment means order dispatch
cannot satisfy PENDING acceptance; report that limitation instead of manufacturing
PASS. Unit queuing/no-transport evidence does not prove online job creation.
