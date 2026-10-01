# TEST webhook signing-key backend update

PRIMARY_REPAIR_GOAL: use the TEST Dashboard Primary Signing Key through an
independent Staging environment property. EXPECTED_REPAIR_CLASS: SMALL.

The Owner authorized this exact Staging-only correction on 2026-10-01. No
Production mutation, order/decision, menu write or printing is authorized.
`apply-once.py` is a bounded invocation of the existing reviewed ingress
container-model/Compose/health helpers. It preserves current forwarding, ports,
network IP, resources, entrypoint, existing environment and printing policy.
Only backend image/revision label and UBER_EATS_WEBHOOK_SIGNING_KEY change.

Required before apply: Agent 6 review, focused/full verification, exact committed
source, immutable image labeled with that full SHA, and key already present in
the private Staging env. Build the jar from the reviewed source, record its hash,
and make the runtime image by replacing /app/app.jar in the exact existing JRE
image; no other package or runtime change is needed. No secret enters the build.

Run from `/srv/restaurant-pos/staging/releases/<sha>` with that SHA argument.
Invoke with `python3 -B`; the helper also disables bytecode before importing the
existing model helper so its own cache cannot dirty the source checkout.
The helper refuses unexpected old image, pending Uber/print/outbox work, wrong
Store/org/MOCK identity or an existing evidence directory. It backs up Staging
DB and exact old backend model privately, validates resolved Compose, recreates
only backend with --no-deps/--no-build/--pull never, checks health/model/Flyway
and unchanged other container fingerprints, and restores old backend on failure.
No DB restore/migration change or other container restart is performed.
Queue checks accept both historical COMPLETED and current DISPATCHED terminal
outbox records, plus MOCK_RENDERED/SKIPPED; pending and unknown states block.

Follow apply with public invalid/valid internal-signature probes, IGNORED-event
receipt/no-order proof, public ingress verification and a masked Dashboard read.
The legacy client-secret fallback remains code-compatible, but this particular
Staging acceptance requires the explicit property to be PRESENT and used.

Private evidence/rollback files: `/srv/restaurant-pos/staging/uber-signing-key-20261001`.
Do not print resolved Compose or private env files. Do not use the generic
Staging wrapper to replace the current ingress/PAD policy overlays.

The first backend update restored the previous healthy image after a management-label comparison failure. The bounded repair filters Docker Compose-owned image labels, consistently with runtime normalization; application revision remains verified. Attempt 2 preserves the original backup and uses a separate evidence directory. Agent 6 accepted this repair without P0/P1/P2 findings.
