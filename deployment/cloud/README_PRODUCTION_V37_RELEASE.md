# Production V37 release — 2026-10-05

Owner explicitly authorized this application release, all nine V29–V37 migrations,
Production HTTPS and a safe MOCK order. Uber Production remains NOT ACTIVATED.
Canonical URL: https://pos.lanzhounoodlesmtl.com.

## Actual shared-host deployment

The public edge also serves the independently isolated Staging hostname. Do not
run the legacy `deploy.sh --https` on this host: replacing its whole template
would remove Staging ingress. The reviewed equivalent promotes immutable app
images using the actual runtime model and preserves fixed backend/nginx addresses,
all database/device state and the existing Staging server blocks.

The authoritative private resolved model is
`/srv/restaurant-pos/production/current.compose.json` (0600), with release metadata
beside it. After explicit authority for a future restart, its exact command is:

```bash
docker compose -p cloud -f /srv/restaurant-pos/production/current.compose.json up -d --no-build --pull never --no-deps backend nginx
```

This model contains **only backend/nginx**, not DB. It persists exact images,
`FLYWAY_TARGET=latest`, `UBER_EATS_ENABLED=false`, canonical hostname and narrowly
trusted proxy headers. The existing private `.env` also records DOMAIN,
NGINX_SERVER_NAME, FLYWAY_TARGET and Uber disabled. The repository generic Compose
now honors `${FLYWAY_TARGET:-latest}` instead of pinning V10; that template is not
the shared-host runtime model. Do not reset the historical control checkout or
rebuild from its old HEAD.

Dedicated Production HTTP redirects 308 to HTTPS. The Production certificate is
separate from Staging. Both HTTPS blocks and the legacy emergency IP/HTTP block
retain their separate purpose. Production NATIVE forwarding trusts only nginx
172.18.0.4; every Production proxy entry replaces forwarded host/protocol/port.
Production WSS allows the canonical browser origin and Android bundled origin.
`restaurant-production-cert-renew.timer` renews only the Production certificate;
the existing Staging timer is preserved.

## Recovery

Private release directory:
`/home/ubuntu/Restaurant_System/deployment/cloud/backups/v37-owner-release-20261005T051347Z`.
It retains the checked V28 dump, original env/proxy/container models, exact old
images, deployment driver, actual target and rollback models. Never print private
models or environment values. Never run Flyway repair/clean or delete V37 rows.

For a post-release application regression, a V37-compatible fallback model
`rollback-v37-compatible.private.json` is prepared and Compose-validated but **not
applied**. It uses the preceding accepted Staging backend `8eea8d4` and current
frontend with current HTTPS/Uber-off configuration. It temporarily lacks the new
prior-day-open-table Finish correction. Apply only under incident authority,
health-check Production and Staging, then synchronize the canonical model and
release metadata with the actual fallback. No DB restoration is involved.

The retained original V28 app models are a last-resort application rollback.
The reviewed driver automatically restores them on a failed deployment mutation,
including proxy/env and canonical metadata. Against retained V37 they have a
known module-administration limitation: old code recognizes UBER_EATS as unknown.
Do not claim that old-image/V37 combination is fully compatible. Database restore
is a separate coordinated recovery decision, never an automatic destructive SQL
rollback or overwrite of post-release orders.

## Android and acceptance limits

No native Production endpoint is hardcoded. Existing settings are native private
preferences, independent of web localStorage. Update both Web App URL and API
Base URL to the canonical HTTPS hostname using the native settings screen; keep
app data, device id/token, Store pairing and LAN printer endpoints. A new-domain
web login is expected. Web redirects cannot migrate the native preferences.
The server release does not prove a physical Pad was reconfigured.

Detailed verified checks and remaining manual observations are in
[release evidence](../../docs/governance/PRODUCTION_V37_RELEASE_20261005.md).
