# Staging HTTPS ingress (fixed topology, no application rebuild)

PRIMARY_REPAIR_GOAL: expose the existing 7b70a4f / V30 Staging at its HTTPS
hostname without interrupting Production. EXPECTED_REPAIR_CLASS: MEDIUM.
This package is infrastructure configuration only, not a new deployment platform.

The host's 80/443 already belong to cloud-nginx-1. Append edge-http.conf and
edge-https.conf after the unchanged existing default HTTP server. TLS terminates
there using its existing read-only certificate mount. A separate, unprivileged
host-network Nginx relay binds only172.18.0.1:18480 and allows only172.18.0.4.
It connects to127.0.0.1:18080. Docker networks are never joined. Host Nginx cannot
bind the occupied public443 without disrupting Docker ownership, so this avoids
an iptables interception scheme or Production container recreation.

## Trust and identity

First ingress replaces forwarded metadata and preserves Origin. The relay
accepts only the audited edge source. Staging Nginx preserves HTTPS metadata
only from172.19.0.1 with the canonical hostname and HTTPS marker; all other
requests use their actual HTTP metadata. Tomcat NATIVE trusts only172.19.0.4.
HTTP port-bearing tunnel origins remain intact. These exact addresses must be
checked against docker inspect before apply and after Staging recreation. A
future address change requires a reviewed configuration update; do not broaden
trust to all private CIDRs.

A valid HTTPS Staging Origin becomes same-origin after standard Tomcat header
processing. The existing REST CORS whitelist is unchanged: Android bundled and
local development origins remain allowed, unknown origins stay403. No '*' CORS
entry is added. The ingress additionally restricts WebSocket origins because
the existing application endpoint uses a broader WebSocket origin pattern.

## Bounded apply sequence (Owner authorization and Agent6 ACCEPT required)

1. Capture all seven current container IDs/start times/restarts/images,
   ProductionV28/StagingV30 read-only migration ledgers, frontend hashes,
   effective/template Nginx configs, mounts, networks, listeners and health.
2. Create a private timestamped backup directory on the server. Copy effective
   and template proxy configs, hash all copies, and save current resolved
   Staging backend/nginx Compose models (secrets remain server-only0600).
   Preserve exact images. Prepare restore commands before mutation.
3. Reuse immutable7b70a4f images. Generate a narrowly scoped Compose model from
   actual running backend/nginx settings, preserving environment, mounts,
   command, logging, limits, restart policy and exact network/IP bindings.
   Overlay only backend-environment.json and an external mount of
   staging.conf.template. Reject unexpected services, image differences,
   published backend ports, database changes or network changes. Validate the
   resolved config privately. Recreate only the two Staging app services using
   --no-build --pull never --no-deps; never include the DB service.
4. Validate Nginx candidate configs with the same existing image before reload.
   Create the independent host relay using that immutable Nginx image,
   host networking, UID101, cap-drop ALL, no-new-privileges, read-only root,
   /tmp tmpfs, max64MiB/0.25CPU, local bounded logs and unless-stopped policy.
5. Append edge-http.conf to BOTH the existing mounted source template and
   effective config. Write the single-file bind source IN PLACE (do not rename
   over its inode). nginx -t must pass before nginx -s reload. Restore both
   files on failure. Confirm old IP frontend/health and Production fingerprint.
6. Install Certbot only if absent, without Nginx plugin or automatic restarts.
   Issue certonly --webroot using the existing host data/certbot-www directory,
   --config-dir existing data/letsencrypt, domain staging-pos only. No standalone
   listener, self-signed certificate, Production certificate or DNS workaround.
7. Append edge-https.conf; validate and gracefully reload only. Check old IP
   and immutable Production fingerprints again. Reject unknown TLS SNI/Host.
8. Configure one Certbot renewal timer around renew-staging.sh. Disable the
   newly installed default timer when using this custom config-dir. Test
   renew --dry-run (no production certificate replacement) and reload hook.
9. Open only TCP443 on the existing Tencent instance firewall, retaining22/80.
   Verify publicly without --resolve or insecure TLS. Run the Origin matrix,
   auth401, all frontend static assets and a real WSS upgrade/STOMP exchange.
   Correlate public request nonce in Staging upstream logs and compare frontend
   content with the exact running Staging container. Read Flyway again.

Production continuity gates run after each key step. Any abnormality restores
only ingress/Staging configuration and stops; Production app/DB are never
restarted. DB checks here are read-only; a same-image Staging backend startup
performs normal Flyway validation, with no new migration expected.

## Rollback

Use the private original Staging Compose model to recreate only backend/nginx
with their original exact images/config. Restore edge effective and mounted
source-template bytes, nginx -t, then graceful reload. Stop/remove only the new
relay and disable only its new renewal timer if rolling back the full ingress.
Certificates may remain safely stored. Do not use docker compose down, restore
DBs, change Production images, or restart Production to repair ingress.

## Android

Printing Reliability0.3.0 supports runtime API configuration. On an isolated
test Pad, keep Web App URL blank for bundled assets and set API Base URL to
https://staging-pos.lanzhounoodlesmtl.com. No URL-only APK rebuild is needed.
This infrastructure operation does not change device pairing, install an APK,
enable PAD_DIRECT, create orders or print. Phase2 requires its own hardware scope.

## One-time batch driver

`apply-once.py` is deliberately fixed to this audited host, exact image IDs,
addresses and dated ingress root; it is not a generic replacement for the
normal Staging deployment lifecycle. It provides `prepare`, `stage`, `http`,
`tls`, `check` and `rollback`. Prepare writes a0600 private rollback model and
baseline, preserves literal dollar signs through Compose escaping, and compares
resolved settings with the live snapshot without printing credentials. It
refuses nondefault omitted Docker settings rather than silently discarding them.
Copy the reviewed batch to `/srv/restaurant-pos/staging/ingress-20260930` and
the driver takes the existing Production operations and Staging hygiene locks. After preparation,
review the sanitized resolved comparison before `stage`. Its rollback action
restores edge and Staging app configuration; remove only the newly created
relay/renewal timer separately when fully withdrawing ingress.

The native forwarding regression runs a real embedded Tomcat10.1 / Spring Boot3.3.3 stack
with the actual REST CORS configuration. Seven cases include trusted HTTPS,
unknown Origin, Android, development origins, wrong port, absent forwarding
and a socket source excluded from the trusted-proxy regex. No DB is started.
