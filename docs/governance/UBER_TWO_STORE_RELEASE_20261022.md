# Uber two-store receive-only release — 2026-10-21 Toronto / 2026-10-22 UTC

## Authorization and scope

Owner attachment `be948e49-fb25-4b07-81bf-18f87e0f00f2/已粘贴的文本.txt`
authorizes PR #280 merge, reviewed follow-up fixes, safe Staging acceptance,
backup and Production code deployment with Uber worker disabled, and two-store
local preparation. It does not allow unverified external UUID binding, assumed
grants, TEST secret reuse, live order mutations or printing before readiness.
The generic Production default remains deny; this is a bounded release authority,
not standing permission for future Production releases.

PR #280 was merged as `fb76c16ac6255e921bf3185bc60cf62ac85fce67` at
2026-10-22 02:18:36 UTC. Its reviewed HEAD and test results were unchanged.
The follow-up branch starts exactly there, in its own managed worktree;
the dirty Owner tree and the independent POS task's branch are untouched.

`PRIMARY_REPAIR_GOAL`: deploy the approved read-only Mirror code safely and
prepare independent Store activation without consuming historical orders.
`EXPECTED_REPAIR_CLASS`: MEDIUM product extension with explicitly requested
release operations. No Frontend/Android/schema/payment/PAD_DIRECT state-machine
changes are included. Existing frontend image is preserved.

## Minimum repair and scope expansion check

The existing global cutover was insufficient for independent Store pause/resume:
enabling a paused module could consume its post-global-cutoff backlog. The small
product repair adds `UBER_EATS_STORE_CUTOVERS` and passes each event/order UUID
into the existing gate. Production requires global plus per-Store UTC cutovers;
unlisted Stores cannot import. Existing orders' cancellation/edit safety remains.

`SCOPE_EXPANSION_CHECK`: operational tooling is larger than the product gate.
It is BLOCKING for this explicitly requested release: the reviewed old helper
preserves an active Staging worker and restores it on rollback. Running it
unchanged is unsafe. The new bounded helper reuses its private models, two
existing locks, immutable backend image, backups and backend-only Compose action,
adding only explicit Uber env deltas, safe rollback and live acceptance proof.
The second tool performs the requested nonbusiness receipt/POS checks. No new
deployer framework, credential store, replay API, onboarding or UI was created.
The read-only SQL/runbook are requested readiness evidence. General deployment
redesign, generic retention and non-Uber POS changes are deferred.

## Local validation and Agent 6

- Final full backend verify: 998 tests, 993 passed, 5 unrelated optional skips,
  0 failures/errors. `/tmp/rs-uber-launch-backend-verify.log`.
- Complete Uber suite: 109/109, including 73 real PostgreSQL integration cases;
  same-organization Store mappings/Add-on FKs/notes/printers/devices and module
  isolation, plus accepted gate and duplicate dispatch.
- Promotion helper: 16 mocked Python tests. Verification helper: 9 mocked Python
  tests. Combined 25/25; no remote actions inside these tests.
- Relevant Flyway migration tests ran in full verify; no new migration.
- Governance, whitespace and credential checks are recorded before commit.
- Agent 6 ACCEPT after closing the one P2 (cross-key TEST secret reuse).
  Delta review includes verification tool/runbook/SQL and confirms no remaining
  reproducible P0/P1/P2. It is not a live deployment or grant acceptance.

The five optional skips are `PrintingReliabilityPostgresMigrationTest` (1),
`OwnerStoreMenuClonePostgresIntegrationTest` (3), and
`PrintingDisplayRuleLifecyclePostgresIntegrationTest` (1).

## Preflight facts

Both backends before release are `ce35278b3569a4eacfa29efc01cadbf0c409bec6`,
Flyway V37. Their public health is 200/UP. Production Uber is disabled, no
credentials/key, binding/mapping/event/order/job counts zero. Shared Staging
contains its approved Sandbox binding, 299 rules and retained historical work.
No pending/claimed print or dispatch work was observed; PRINTING/FAILED history
is preserved and never reset. Existing operation locks were not held at initial
inspection; promotion must acquire both again and recheck the live baseline.

Disk preflight: 70% used, 18 GB free; Build Cache 16.25 GB, reclaimable 10.37 GB.
Reviewed without cleanup: preserve current/rollback images, releases and DB
volumes; sufficient headroom exists for one cached backend build. Capture the
post-build/deploy figures rather than claim cleanup or reclaimed space.

Local Store/menu/printer facts are in
[the exact read-only preparation](UBER_TWO_STORE_LOCAL_PREPARATION_20261022.md).
Both targets resolve 41/41 root semantics, eight combo triggers and 66 canonical
Add-on references. Production external mappings remain zero: neither live menu
IDs nor the two Uber UUID identities have been verified. Sainte-Catherine lacks
printer assignments/devices; Saint-Denis has configured routing but no recent
heartbeat proof. These facts block real activation, not safe code deployment.

## Dashboard observation

The existing logged-in Chrome session was inspected in a separate Uber window.
PROD App Client ID is `6GaiQpZTcfcuTa150wr4RyztC33PV7LS`.
Setup shows Production Access Requested, Production Access Granted still an
hourglass. Access Token shows “Scopes Requested / Your scope request is being
verified”. Webhooks shows “No Webhooks / Currently no webhooks are configured”.
The existing OAuth Client Secret is masked and was not revealed/copied/rotated.
No role or Store Access approval is inferred. No Dashboard setting was saved.

## Immutable release and runtime acceptance

Exact source SHA/image, backup references, Stage acceptance, Production smoke,
module preparation and post-deploy disk figures will be added only after actual
execution. Until then this section is pending, not PASS. The reviewed operator
sequence is [the activation runbook](../../deployment/cloud/README_UBER_TWO_STORE_ACTIVATION.md).
Real order import is prohibited while external dependencies are unresolved.
