# Staging PAD_DIRECT policy execution, 2026-09-30 EDT

PRIMARY_REPAIR_GOAL: enable PAD_DIRECT for the Owner's Staging Android acceptance
while forbidding cloud-backend REAL printing. EXPECTED_REPAIR_CLASS: SMALL.

## Audit and exact identities

- Fresh main baseline: `031c15b613ae80de43bf276260abda5349b39e66`.
- Implementation branch: `codex/staging-pad-direct-policy`; PR [246](https://github.com/Z1linXu/Restaurant_System/pull/246).
- Reviewed implementation: `b0f091eb2e82a4fc3c3192d78f70e3c67ce0bcfd`.
- Merged and deployed **configuration** SHA: `7ab93661f97885bed20e9a9791b79e731da515d3`.
- Staging **application** before/after: `7b70a4fac4e350444bcd37687ca88a8636ff8f2a`, V30.
- Same backend image: `sha256:23f332075f0142f2aeb941da62d58212cd595b1e393a69adb609b4869f18d870`.
- Production remains `11996ef919d9b28ee5366c7e40a58a78c074b675`, V28.

ROOT_CAUSE: `PrintingRuntimePolicyProperties` binds the environment ceiling
`APP_PRINTING_ALLOWED_MODES`; the Staging Compose/runtime value was
`DISABLED,MOCK`, so both PAD_DIRECT and REAL were rejected before persistence.
It is an environment-level restriction. The four modes already exist in the
application; this is neither missing PAD_DIRECT code nor a cloud-profile hardcode.
Cloud profile guard and printing feature enablement remain unchanged.

POLICY_FILE: `deployment/cloud/staging-pad-direct/backend-environment.json`.
Enforcement: `backend/src/main/java/com/restaurant/system/printing/PrintingRuntimePolicyProperties.java`.
Generic default: `deployment/cloud/docker-compose.staging.yml`.

STAGING_ALLOWED_MODES_BEFORE = DISABLED,MOCK
STAGING_ALLOWED_MODES_AFTER = DISABLED,MOCK,PAD_DIRECT
REAL_ALLOWED = NO
PAD_DIRECT_ALLOWED = YES

## Validation and execution

35 focused tests passed: actual overlay binding, Store PAD_DIRECT persistence,
REAL rejection without another save, dispatcher with zero printer transport
interactions, and CloudPrintingGuard regression. Governance/diff checks passed.
Queue preflight mock checks rejected each unsafe count independently. Agent 6
review and delta execution review: ACCEPT, no P0/P1 findings. Nullable Compose
command comparison was corrected before merge.

All-Staging preflight found no persisted PAD_DIRECT store, no executable print
job, and only terminal outbox rows (COMPLETED 135, MOCK_RENDERED 36, SKIPPED 3).
The same read-only checks ran during preparation and immediately before apply.
No unsafe old jobs could become newly eligible through this allowlist change.

Deployment files were exported from the merged SHA and SHA-256 verified on host:

| Artifact | SHA-256 |
| --- | --- |
| policy JSON | `b236fbf5edeec0948a4127beb94bcb2d92ada60666d70e39f7aea3751292bf42` |
| bounded adapter | `a4ef7f7292611cf59285186a21f8e88924c296341b32bef768c832a6255c95ed` |
| reused ingress primitive | `b7c785a5eadb09c2b39002bceddd925f65fb6be4a8b373e0256fee9ebc830af1` |

Execution source: `/srv/restaurant-pos/staging/pad-direct-config-7ab93661f97885bed20e9a9791b79e731da515d3`.
Private current-runtime backup and rollback model:
`/srv/restaurant-pos/staging/pad-direct-20260930` (never publish env contents).
Only Staging backend was recreated, with no build or pull. Before/after model
comparison confirmed the exact allowlist-only environment delta, identical
image, command, resources, static IP/network and HTTPS forwarding configuration.
Backend ID after: `530d20ffd9aa07eb920ac96ca77e94f319bbd0c0014d34dd7a18685a267f8c4c`;
started `2026-10-01T01:41:36.442849906Z`.

Production and Staging Nginx/DB fingerprints (ID, start time, restart count,
image, networking, ports, environment hash) remained identical. Both Flyway
ledgers remained identical. No pending migration or DDL executed. Configuration deployment performed no
business-data mutation; the subsequent authorized mode API writes are listed below.
No Production edge reload/restart/configuration action was invoked.

STAGING_HEALTH = UP
PUBLIC_HTTPS_CORS_ASSETS_WSS = PASS (42 checks)
PRODUCTION_POLICY_CHANGED = NO
PRODUCTION_RESTART = NO
PRODUCTION_DB_MUTATION = NONE

## Chinatown mode and remaining acceptance gap

Exact target: Store 18, CHINATOWN, organization 1. Read-only preflight and Owner
API overview confirmed DISABLED, zero printers, three enabled assignments with
null printer IDs, and zero eligible jobs. Because no endpoint/eligible job
existed, changing the mode did not activate physical printing even though the
Owner had not yet confirmed worker pause.

Existing authenticated public Owner API:
- PUT `/api/v1/admin/printing/status`, Store 18, PAD_DIRECT: HTTP 200.
- PUT same endpoint with REAL: HTTP 400 and expected runtime-policy rejection.
- GET overview and read-only DB: `18|PAD_DIRECT|true` persisted; cloud guard active.
- Test authentication session logged out successfully.

PAD_DIRECT_MODE_SWITCH = PASS
PAD_DIRECT_PENDING_JOB_CREATION = FAIL (BLOCKED / NOT EXECUTED)

No new order, printer, assignment, Pad pairing or print job was created. No
historical business record was modified. The only requested Store change was
printing mode/enabled; its normal audit record and temporary login/logout
session records are expected API side effects. This is not a claim of zero
Staging DB writes.

Online PENDING verification cannot pass without an enabled printer binding.
Current dispatch would fail with an absent/unbound assignment before queuing.
Endpoint configuration remains false. Owner was asked whether to extend the
scope and provide LAN endpoint/module mapping; no endpoint permission or
binding was inferred from the allowlist-only instruction. All eligible workers
must also be paused before creating jobs, with a defined non-executable job
end-state until Owner starts physical acceptance. Queue claims do not recheck
Store/runtime mode; rollback alone does not disarm existing jobs.

Backend no-TCP proof: unchanged PAD_DIRECT code returns after queueing; focused
dispatch test confirms zero transport interactions. Post-switch backend network
namespace snapshot observed zero remote TCP 9100 sockets. This snapshot is not
a packet capture or proof of an online order-to-PENDING execution; that part
remains explicitly blocked. No physical printing was requested or performed.

## Scope and resource review

PRODUCT REPAIR: one Staging allowlist value; no application-source changes.
TOOLING / GOVERNANCE: bounded adapter reuses reviewed ingress primitives because
the generic deployment wrapper must continue rejecting PAD_DIRECT by default;
focused tests, runbook, system documentation and current state record the exact
exception. No generic deploy redesign, APK build, endpoint policy change,
cleanup, Production work or unrelated repair.

Disk before/after: 60% used, 24 GB free. Build cache unchanged at 14.17 GB,
9.973 GB reclaimable, 251 records. Cache-hygiene review: no build/cache growth,
healthy disk, protected active/rollback artifacts; no cleanup needed in this
bounded config batch. No images/volumes/cache were deleted.

Stop at policy/mode acceptance; online PENDING acceptance remains an Owner
prerequisite gate. Do not interpret this evidence as completed hardware Phase 2.
