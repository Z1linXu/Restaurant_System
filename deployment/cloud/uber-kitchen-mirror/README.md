# Kitchen Mirror Staging update

PRIMARY_REPAIR_GOAL: deploy the Owner-authorized TEST webhook environment
compatibility after the ACCEPTED observation/V32 release. EXPECTED_REPAIR_CLASS:
HIGH (signed event/environment boundary).

TOOLING_ASSESSMENT: BLOCKING, bounded reuse of the reviewed ingress container-model
helper. It preserves the existing forwarding/PAD overlays, private configuration,
ports, mounts, network IPs and resource limits. The only intended runtime changes
are immutable backend/frontend images and two non-secret environment values:
`UBER_EATS_TEST_WEBHOOK_CLIENT_ID=t86ofdunSsVjL-0AvK6MA6LCTyF2eYLf` and
`UBER_EATS_TEST_WEBHOOK_STORE_ID=bd993244-5589-4b19-8f0d-dc2ba73d4273`.
No new deployment platform, credential or infrastructure is introduced.

Prerequisites: relevant backend regression/live isolated PostgreSQL tests,
credential scan and Agent 6 ACCEPT. Reuse frontend test/build proof only when its
source is unchanged. Commit reviewed source; build immutable images labeled with
full SHA. Backend replaces only `/app/app.jar` on the current JRE image; frontend
uses the same verified dist when unchanged. Keep build inputs free of secrets.

Run `python3 -B deployment/cloud/uber-kitchen-mirror/apply-once.py <full-sha>`
from `/srv/restaurant-pos/staging/releases/<full-sha>` after verifying both images
exist. The helper locks the existing operations locks, refuses unexpected images
or pending work, validates Store 1 MOCK, backs up DB with a readable custom archive,
keeps old Compose privately and recreates only Staging backend/nginx. V32 must
remain unchanged. Production/DB-container fingerprints and Production Flyway
must match. Failure restores old application only if queue guards still hold;
there is no automatic destructive database restore. Private backup/rollback path:
`/srv/restaurant-pos/staging/uber-test-webhook-compat-20261002`.

Then verify Store 1 KITCHEN_MIRROR/MOCK, the approved 226 rules, public health,
signed webhook rejection/receipt and today's UI. Only the exact TEST app/store
pair in Staging sandbox can use a production-labelled order notification: raw-body
HMAC, enabled MIRROR binding, Sandbox Get Order identity and locked binding
revalidation must all pass before durable event/order/cancel writes. Default
strict isolation remains. Normal matching-environment receipt stays fast; this
TEST exception additionally waits for the existing bounded Sandbox API read.

Real TEST consumer orders and Uber Tablet acceptance are Owner-authorized.
Restaurant_System remote Accept/Deny, physical printing and Production mutation
remain forbidden. Mapping previews and internal signed IGNORED probes never
substitute for actual Sandbox webhook/order/GRAB evidence.
