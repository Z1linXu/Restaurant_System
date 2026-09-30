# Staging HTTPS Phase 1 — 2026-09-30

Result: **PASS**.
Owner authorized only Staging ingress/TLS/forwarding compatibility, with the
existing Production application and database preserved. No hardware acceptance,
APK installation, business mutation, physical printing or Production cutover ran.

## Exact batch and review

- Base main: `e2a2694e212fc0541d11e380b8922b5ac0236af2`.
- Implementation PR [242](https://github.com/Z1linXu/Restaurant_System/pull/242),
  merge `9b55b8366f51d0cab7d7ed21f3c57c326cca0c5a`.
- Preflight repairs: PR [243](https://github.com/Z1linXu/Restaurant_System/pull/243),
  merge `af42e959ba0f7191b58e2a4ae0031b74b3deb169`; PR
  [244](https://github.com/Z1linXu/Restaurant_System/pull/244), final deployed
  configuration merge `f9734e379e957e81f49706c79a5c38730cad82e0`.
- Agent 6 independently reviewed trust/CORS, spoofing, proxy isolation, TLS,
  WebSocket, driver preservation/rollback and renewal units. Initial literal-dollar
  preservation finding was fixed before deployment. All material changes received
  ACCEPT; final independent runtime/public audit found no P0/P1.
- Seven real embedded Tomcat / Spring Boot 3.3.3 forwarding/CORS tests PASS;
  Compose literal unit test PASS; origin contract and governance checks PASS.
  Same-image Nginx syntax checks PASS. No application build was needed.
- Preflight retries occurred before application changes: Compose v5 serializes
  dollar escaping and memory byte values differently; long map keys required
  `map_hash_bucket_size 128`. Fail-closed checks caught each issue. A disposable
  network-disabled Engine-create probe verified literal entrypoint/env/label
  preservation. Original failed-preflight backups were retained.

## Actual topology and changes

`Internet :443 -> cloud-nginx-1 named Staging TLS vhost -> 172.18.0.1:18480`
`-> dedicated host-network relay -> 127.0.0.1:18080 -> Staging Nginx -> backend`.

The original Production HTTP server block is an unchanged byte prefix. Only
additive Staging ingress vhosts were applied using `nginx -t` and graceful reload.
The existing edge container was never restarted/recreated. Production backend,
frontend assets/images and DB were unchanged. No Docker networks were joined.

The new relay is `restaurant-pos-staging-ingress`, immutable existing Nginx image,
UID101, read-only root, cap-drop ALL, no-new-privileges, 64MiB/0.25CPU, bounded
local logs and a `/tmp` tmpfs. It binds only `172.18.0.1:18480` and permits only
edge source `172.18.0.4`. Staging remains loopback-published at `127.0.0.1:18080`.

Only Staging backend/nginx were recreated using the same exact application
images. The backend gained six standard Tomcat NATIVE remote-IP variables with
exact trusted proxy `172.19.0.4`; Nginx gained the reviewed mounted template.
Actual Env/Entrypoint/Cmd were checked against the baseline plus that overlay.
Origin is preserved; the REST CORS whitelist is unchanged. The edge replaces
client forwarding metadata; Staging accepts HTTPS metadata only from the audited
host path. WebSocket ingress restricts origins explicitly.

Tencent Lighthouse Virginia instance `lhins-isekf2gg`, `170.106.13.34`: Owner
confirmed adding only TCP443/IPv4 source `0.0.0.0/0`. Console result showed
22/80/ICMP retained and443 added. Before opening, external443 SYN did not reach
host eth0 while80 did; after opening it reached the host, and TLS became available
after the listener configuration. Host firewall and Docker publication were not
reworked. DNS was unchanged.

## Runtime identity / continuity

[Structured before/after evidence](staging-https-20260930/runtime-identity.json)
contains full container IDs, image IDs/tags, start times, restart counts, ports,
networks and exact read-only Flyway ledgers. Before:20:12:59 UTC. After:20:35 UTC.

| Runtime | Before and after application SHA | Flyway | Result |
|---|---|---|---|
| Production | `11996ef919d9b28ee5366c7e40a58a78c074b675` | V28 | All3 container IDs/start times/restarts/images unchanged |
| Staging | `7b70a4fac4e350444bcd37687ca88a8636ff8f2a` | V30 | Backend/nginx recreated; same images; DB container unchanged |

All newly recreated Staging containers have RestartCount0, but a Staging
application restart/recreation **did occur**. No Production application restart
occurred. Flyway history was byte-identical in both environments (SELECT with
read-only transactions and5s statement timeout). No migrations were added/run.

Production frontend200 and health200/UP passed after the Staging change, HTTP
routing change, certificate operation, TLS activation and renewal-hook reload.
The immutable Production fingerprints passed every driver continuity gate.
Agent6 independently rechecked public Production frontend/health200.

Public index SHA256 `35f40bb5bec31c5e175426574923ac08515f892b7c7be821cfc759c2cc36fbc0`
equals the running Staging index, while Production index hash is
`b195fa9e26b838a476c81f41d27194ab1a4e115d82db173f65e29657c6fc18d0`.
The spoofed-header public probe produced this Staging upstream log:

```text
uri=/api/v1/system/health status=200 upstream=172.19.0.3:8080 client=38.253.17.131 proto=https host=staging-pos.lanzhounoodlesmtl.com probe=phase1-spoof
```

The supplied fake client IP `198.51.100.42`, fake host/proto/port and Forwarded
header did not survive the ingress. Combined with actual backend image/IP and
its DB/Flyway evidence, this proves API routing to Staging, beyond DNS alone.

## External acceptance

[42 public checks](staging-https-20260930/public-acceptance.json) PASS using
normal public DNS and verified TLS; no `--resolve`, hosts override or insecure
TLS. [Additional checks](staging-https-20260930/additional-public-checks.json)
cover CORS preflight, other-domain rejection and public DNS.

- Frontend200 and all15 entry HTML static assets200; browser rendered `/login`
  with account/password fields. No credential login was attempted.
- Health200/UP, valid HTTPS/Android/localdev Origin200, invalid/Production/wrong
  port Origin403. No wildcard ACAO. Android authorization/content-type OPTIONS
  returned200 with exact Android ACAO; invalid Origin OPTIONS403.
- `/api/v1/auth/me` with Staging Origin returned401, not CORS403/502/404.
- Actual TLS/RFC6455 `/ws` upgrade returned101 and valid Sec-WebSocket-Accept.
  This was more than `/ws/info`; no authenticated subscription/business message
  was tested. Invalid WebSocket Origin returned403.
- HTTP Staging hostname returned308 to its canonical HTTPS hostname; no longer
  serves Production. `pos` and `www` TLS SNI were rejected; not enabled.

## Certificate and renewal

Public [chain and hostname validation](staging-https-20260930/tls-chain.txt):
TLS1.3, verify code0. CN/SAN=`staging-pos.lanzhounoodlesmtl.com`, issuer Let's
Encrypt YR2, chain through RootYR to ISRG RootX1. Valid from2026-09-30 19:35:02UTC
to2026-12-29 19:35:01UTC. Standard public trust is verified; a physical Android
Pad's trust store/device behavior is a Phase2 hardware check.

Certificate webroot manager is distro Certbot1.21.0, installed without Nginx
plugin, package upgrades or automatic service/container restarts. Only the
Staging name was issued. Initial ACME404 came from pre-existing empty webroot
mode0700; its archived mode/content were backed up and only the public webroot
was set0755. Public challenge probe200 preceded successful retry. Certificate
private key remains root-owned0600; no key was copied into repository evidence.

Certificate directory:
`/home/ubuntu/Restaurant_System/deployment/cloud/data/letsencrypt`.
Root-owned script `/usr/local/sbin/restaurant-staging-cert-renew` is byte-identical
to reviewed `renew-staging.sh`. Custom `restaurant-staging-cert-renew.timer`
runs at00:00/12:00 server time plus up to1h random delay, Persistent=true.
Default newly installed `certbot.timer` is disabled. Unit validation passed;
service no-op renewal check exited0; timer next scheduled run was2026-10-01
12:29:20CST (04:29:20UTC). Timer unit contents are recorded in
[renewal evidence](staging-https-20260930/renewal.txt).

Dry-run result: PASS (all simulated renewals succeeded; process exit0). Certbot1.21 does not support `--run-deploy-hooks`, so
renewal dry-run and the exact `nginx -t && nginx -s reload` hook are checked
separately. Hook PASS with Production continuity unchanged.

## Backup and rollback

Private server root: `/srv/restaurant-pos/staging/ingress-20260930/backup`.
Contains actual original edge template/effective configuration, original Staging
template, Docker inspect snapshot, baseline, SHA256SUMS, certificate-directory
archive, and private original Compose model. Each edge mutation also has a
`before-edge-http` / `before-edge-tls` checkpoint with hashes. Files with secret
configuration remain server-only0600. Failed-preflight directories are retained.

Rollback artifact: `backup/rollback.private.json`, original7b70a4f images.
Run `sudo python3 /srv/restaurant-pos/staging/ingress-20260930/apply-once.py rollback`
for reviewed ingress/Staging restoration, then disable only the new renewal timer
and stop/remove only the new relay when fully withdrawing this ingress. No
Production application or DB restart/restore is part of rollback. Rollback models
were resolved/verified; a disruptive rollback rehearsal was not performed.

[Deployed checksums](staging-https-20260930/final-config-checksums.txt) match the
merged package. This runtime overlay survives ordinary container restarts.
A future standard Staging application deployment must explicitly preserve the
reviewed forwarding variables, mounted ingress template and audited trusted IPs;
do not replace these with the pre-ingress defaults without a compatibility check.

## Docker / Disk Hygiene

Disk60% before/after,24GB free after. Build cache14.17GB /251records before and
after; cache total exceeds the12GB soft target and was reviewed read-only.
Shared3.447GB/private10.72GB; no new builds/cache growth in this task. Cleanup
NONE; reclaimed0. Agent6 accepted preserving active/rollback images, database
volumes, releases and backups in this healthy-disk ingress operation. Both
runtimes healthy. [Structured review](staging-https-20260930/disk-review.json).

## Android / limits

Current printing-reliability APK supports runtime SharedPreferences. Long-press
the app page to open `Restaurant Pad Local Control Panel`; set
`Bundled Assets API Base URL` to `https://staging-pos.lanzhounoodlesmtl.com`, keep
`Local Preview Web App URL` blank and press bottom `Save` to reload bundled assets.
No URL-only APK rebuild is required. This batch did not install or operate an APK,
pair hardware, modify printer/Store/PAD_DIRECT configuration, place orders or
print. Phase1 completion does not authorize Phase2 hardware actions or Phase3
Production HTTPS. Owner must separately start that next scope.
