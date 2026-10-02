# Kitchen Mirror Staging update

PRIMARY_REPAIR_GOAL: deploy reviewed Kitchen Mirror code and V31 to the isolated
Staging TEST Store. EXPECTED_REPAIR_CLASS: HIGH (order/financial/printing boundary).

The Owner explicitly authorized implementation, tests, Agent 6 review and Staging
MOCK deployment; no real Sandbox order, remote Accept/Deny, physical print or
Production change. Mapping persistence is a separate pending Owner confirmation.

TOOLING_ASSESSMENT: BLOCKING bounded adaptation of the existing reviewed ingress
container-model helper. The generic wrapper would replace current forwarding/PAD
policy overlays. This helper changes only backend/frontend immutable images;
environment, ports, mounts, network IPs and resource limits remain unchanged.
No new deployment platform, credentials or infrastructure are introduced.

Prerequisites: full backend verify + live isolated PostgreSQL tests, frontend
tests/build, targeted lint, credential scan and Agent 6 no P0/P1. Commit reviewed
source; build immutable images labeled with full SHA. Backend replaces only
/app/app.jar on the exact current JRE image. Frontend replaces only built dist
on the exact existing nginx image. Keep build inputs free of secrets.

Run `python3 -B deployment/cloud/uber-kitchen-mirror/apply-once.py <full-sha>`
from `/srv/restaurant-pos/staging/releases/<full-sha>`. The helper locks the existing
operations locks, refuses unexpected source images/pending work/wrong MOCK Store,
backs up DB with readable custom archive, preserves old Compose privately, validates
resolved target, recreates only Staging backend/nginx, verifies additive V31 and
unchanged Production/DB-container fingerprints and Production Flyway.
Failure restores old application only if queue guards still hold; additive schema
is retained. No automatic destructive database restore. Backup/rollback files:
`/srv/restaurant-pos/staging/uber-kitchen-mirror-20261002` (private 0700/0600).

Then use existing authenticated Store API to set only Store 1 binding to
KITCHEN_MIRROR, verify readback/MOCK/empty order+mapping counts, public health,
invalid/valid IGNORED-event signatures and today's UI. No artificial kitchen order
is inserted into shared Staging. Local PostgreSQL fixtures prove kernel behavior;
real Uber Pad readiness remains NO until Uber enables release and the approved
first-test mapping is actually persisted.
