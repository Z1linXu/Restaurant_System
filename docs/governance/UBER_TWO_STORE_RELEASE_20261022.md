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

PR #281 merged at 2026-10-22 02:45:58 UTC as
`7d573be03d66a9505e0c68f92fa17829247f56e3`. There are no configured GitHub
CI checks; the local tests and Agent 6 evidence above are not called CI PASS.
The backend was built once and reused for both environments:

- Image: `sha256:6512955fe8b066855fa1e4b3675549cf4b10c975340b4f6d1db000aaff653f8b`.
- OCI source revision: exact `7d573be03d66a9505e0c68f92fa17829247f56e3`.
- Build completed 02:49:17 UTC; build-log SHA256
  `2b5ba1fc151b765d5001172e5f87d87a8073c77a63024d3d7e8c64e461b7ca59`.
- Server archive: `/srv/restaurant-pos/artifacts/uber-release-7d573be03d66a9505e0c68f92fa17829247f56e3`.
- Both existing operation locks were held for each mutation; the other POS
  task was never interrupted. After it acquired the locks, our first Production
  verification exited on lock contention and succeeded after release.

### Staging release and exact-artifact acceptance

Deployed 02:51:33 UTC. Backend/HTTPS health and seven authenticated POS GETs
passed; Store isolation passed. Environment sandbox, receiver true, worker false,
global KITCHEN_MIRROR, requested scopes `eats.store eats.store.orders.read`.
Actual Testing OAuth returned 200 and both requested scopes; actual Testing
GET Order returned 200. Actual order mapping preview had zero errors/warnings.
These read-only calls used a retained historical TEST order, not a new order.

Public HTTPS: GET405, invalid HMAC401, foreign environment400, valid HMAC200,
duplicate200. One synthetic supported notification was retained as exactly one
`HELD_RECEIVE_ONLY` event, no integration order. The event ID is
`ae049cb4-e375-4d84-be83-05865ef3ad1e`. Bindings, integration/local orders and
Uber Print Jobs were unchanged. No business order or physical print was created.
Acceptance was recorded at 02:54:09 UTC with evidence SHA256
`af7080d2b5521f7c948ac0030ba809cb92285dbbb36853b1ca682be2139a8bd9`.

Private Stage evidence root:
`/srv/restaurant-pos/staging/uber-receive-only-7d573be03d66a9505e0c68f92fa17829247f56e3`.
The verified pre-deploy `database-before.dump` SHA256 is
`c141c19796c837b6602e31c9d63c447e02c30eebf49e02d6afb41f876a035f73`.
The dump was validated by `pg_restore --list`.

At 03:05 UTC the independent POS task had subsequently deployed shared Staging
backend `ece77240a7a77896632c10798fafa29120c6ab47`, image
`sha256:573dc7d9979455d52c46de650e8ec8a4a7d0b4feb4c21823e7acf55a918625b2`,
Flyway V38; its frontend also uses ece7724. Git ancestry includes our `7d573be` release. Current public system
health is UP, sandbox/receiver true/worker false/read-only scopes preserved.
Our exact-artifact acceptance proves the earlier 7d573be deployment; it is not
a full acceptance of the independent POS changes or its V38 migration. Its
worktree, deployment and evidence remain owned by that task.

### Production release and local preparation

Deployed 02:54:49 UTC, same verified image/source as Stage. Backend/system health
and frontend HTTP passed; V37 unchanged. Seven authenticated GET checks cover
auth/me, workspaces, menu, frontdesk tables, existing orders, Print Center and
devices; unauthorized Store403 passed. Password login is NOT TESTED; no live
draft was submitted and no physical print was triggered. Pad registration is
not current worker-health proof. POST with disabled receiver returns503, GET405.
Real Production HMAC remains `EXTERNALLY_BLOCKED_SIGNING_KEY_NOT_CONFIGURED`.
Both `UBER_EATS_ENABLED=false` and `UBER_EATS_WORKER_ENABLED=false` were read
back. Production environment and global KITCHEN_MIRROR/read-only scopes are set;
per-Store cutovers remain empty. OAuth secret and Signing Key are absent.

Private Production root:
`/srv/restaurant-pos/production/uber-receive-only-7d573be03d66a9505e0c68f92fa17829247f56e3`.
The validated pre-deploy `database-before.dump` SHA256 is
`90c91656325849b144688d707aaf7f8d646251fd740340b3272e61810ff8b890`.
Rollback snapshot `rollback.private.json` selects old image
`sha256:5b9583ee43ad2ceeb9b47a08a47397cb8802a19f1d89366a0b0b04889ebbaaad`
from backend source `ce35278b3569a4eacfa29efc01cadbf0c409bec6`, with receiver
and worker false. No database restore is required for this no-migration release.
Existing frontend `8c3a9e356f59cd025beff25f7fb1d5a20594e028`, Nginx, DB and
other containers were preserved. Private models/evidence are0600, roots0700.

After Production smoke, the Agent 6-reviewed one-time module preparation used
the existing Store-module API under both locks. Read-back confirms Store1=true,
Store3=true, Chinatown Store2=false. All other modules and Store printing mode/
enabled fields were unchanged. Production bindings0, mapping rules0, Uber orders0;
no new Uber local orders/jobs. Snapshots `modules-before.private.json` and
`modules-after.private.json` are retained in the Production evidence root.
The earlier read-only local-preparation report intentionally describes the
pre-write observation; this section records the later authorized write.

### Dashboard and external stop

The Production Primary/Basic HMAC form was prepared with the correct URL, but
not saved: the current Dashboard requires an operator-supplied Signing Key.
The account owner was asked to generate/store it privately, enter it and Save;
no key was requested in chat. No credential was revealed/rotated or copied from
TEST. Until safe backend injection, receiver enablement and actual signed200
proof, Production Webhook is BLOCKED, not READY. Scopes remain pending; role
and Store Access are unverified. The provided UUID's ownership remains
unverified and the second UUID is missing. Neither was bound. Both live Uber
menus/stable identifiers remain unavailable, so zero Production mappings is
intentional rather than a claim that internal SKU resolution completes mapping.

Saint-Denis local configuration is prepared but needs current Pad proof;
Sainte-Catherine also lacks printing assignments/paired hardware. Neither Store
is ready for real import. The reviewed operator sequence is
[the activation runbook](../../deployment/cloud/README_UBER_TWO_STORE_ACTIVATION.md).
Data/configuration, external authorization and controlled acceptance remain;
no new Java/React development is required for independent two-Store activation.
Existing in-flight transactions/jobs are not retracted by a module pause;
cutover configuration changes require a locked shared backend restart.

### Docker / Disk Hygiene

Disk before70%/18GB free; after73%/16GB free. Build Cache before16.25GB
(10.37GB reclaimable), after16.82GB (10.45GB reclaimable). Cache above12GB was
reviewed; no cleanup/reclaimed space. Simultaneous independent POS builds also
contributed, so growth is not attributed solely to this release. Disk is below
the75% cleanup threshold with sufficient headroom; defer unrelated retention
work. Current/rollback images, releases, evidence and database volumes were
protected. All eight containers are Up, both PostgreSQL containers healthy;
Production and current Staging public system health are UP. Production mutation
was limited to the authorized backend release and local Store1/3 module writes.

`PRODUCTION_WEBHOOK_READY = NO`

`PRODUCTION_UBER_WORKER_ENABLED = false`

`ACTIVATION_REQUIRES_NEW_CODE = NO`

`READY_FOR_CONTROLLED_PILOT = NO`
