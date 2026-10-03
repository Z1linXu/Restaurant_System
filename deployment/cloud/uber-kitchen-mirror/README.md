# Kitchen Mirror Staging update

PRIMARY_REPAIR_GOAL: deploy reviewed Kitchen Mirror ACCEPTED observation and V32 to the isolated
Staging TEST Store. EXPECTED_REPAIR_CLASS: HIGH (order/financial/printing boundary).

The Owner explicitly authorized implementation, tests, Agent 6 review and Staging
MOCK deployment; TEST Sandbox orders and TEST-only Order Manager resignation are authorized in this
batch, after readiness. Restaurant_System remote Accept/Deny, physical printing and
Production mutation remain forbidden. The final-table mapping persistence is approved.

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
resolved target, recreates only Staging backend/nginx, verifies additive V32 and
unchanged Production/DB-container fingerprints and Production Flyway.
Failure restores old application only if queue guards still hold; additive schema
is retained. No automatic destructive database restore. Backup/rollback files:
`/srv/restaurant-pos/staging/uber-accepted-trigger-20261002` (private 0700/0600).

Then verify Store 1 KITCHEN_MIRROR/MOCK and the approved 226 mapping rules,
public health, invalid/valid IGNORED-event signatures and today's UI. Use the
read-only mapping preview with persisted rules for semantic resolution; this is
not a Sandbox order. The new bounded ACCEPTED-state observation can trigger the
same kitchen gate without order_release_enabled. Perform TEST-only Order Manager
resignation using the official optional field after code/runtime verification;
keep integration_enabled=true. A real TEST consumer order and Uber Tablet Accept
are required for E2E/Pilot PASS. Never fabricate real-order proof from fixtures.
