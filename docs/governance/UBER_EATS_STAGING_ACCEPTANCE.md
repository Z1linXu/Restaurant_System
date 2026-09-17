# Uber Eats Staging acceptance — 2026-09-17

## Result and scope

PR [#234](https://github.com/Z1linXu/Restaurant_System/pull/234) merged through the normal merge-commit policy at 2026-09-17 16:26:01 UTC, SHA `101dbdc75c25904c340251134ee1a0a03f5ce4b7`. Reviewed head `05273d61477d0f58702179381941b4437caac9b8` is an ancestor of fresh origin/main. GitHub reported CLEAN/MERGEABLE, no configured check runs; no admin bypass or force push. Original dirty/merging Owner workspace was not modified.

Local implementation, deployed Staging transport and real Sandbox OAuth passed. **REAL UBER SANDBOX E2E is BLOCKED**: app-authorized Sandbox store listing is empty; no real Uber test order, acceptance, local linked order or resulting printing exists. Synthetic transport probes and prior fixtures do not count as Uber E2E.

Current authorization covers this Test App, isolated Staging and Sandbox. No Production App/store/order-manager/menu/webhook/customer-order mutation was performed. St-Denis and St-Catherine remain future Pilot targets, third Store disabled by plan; Production UUIDs and separate Pilot approval remain pending.

## Security and review

- Test App client ID: `t86ofdunSsVjL-0AvK6MA6LCTyF2eYLf`; UI application name `Restaruant Pos`, TEST APP, Sandbox Access Granted.
- Owner explicitly authorized tool handling of the Test Secret. New Secret generated; Dashboard confirmed old Secret revoked and old row removed. New value saved outside Git with mode0600, then transferred through SSH stdin into Staging private environment. Clipboard cleared. No secret/token values included here.
- Actual Staging config: `/srv/restaurant-pos/staging/config/.env.staging`; `UBER_EATS_CLIENT_SECRET=PRESENT`, enabled=true, environment=sandbox. No Production env file changed.
- Agent6 found and closed one P2: v2 order-manager ID may be redacted. Local Store/menu guards remain; remote decision API permission and HTTP204 remain authoritative. Redacted-ID concurrent accept/deny and remote403/no-local-effects tests pass. P0=0, P1=0, P2=0 at merge.
- Full backend verify: 800 tests, 794 pass, 6 pre-existing dedicated-environment skips, zero failures/errors. Uber tests: 24 executed, including PostgreSQL/Flyway29 and unique constraints. Frontend225 pass, build and targeted eslint pass.
- Fresh merge-SHA worktree backend OAuth smoke/package and fresh npm ci/build pass. Governance validation passed on a clean Git archive with this change overlaid; diff-check passed. Running the validator directly in the dependency-installed worktree instead hits the unrelated `frontend/node_modules/jiti/README.md` → `./src/babel.ts` broken link. No validator or dependency files were changed to hide that limitation. New Secret exact scan across tracked files and PR commits: zero matches; deploy/backend logs also zero exact matches. Prior 56-file heuristic credential scan found no non-fixture credential. This is not a complete repository-history forensic audit.

## Deployed environment

- Existing verified SSH target alias `restaurant-prod` hosts **separate** `restaurant-pos-staging` Compose project. Its Staging DB/paths/config are separate from Production. Only Staging was deployed.
- Backend/frontend images: `restaurant-pos-{backend,frontend}:staging-101dbdc75c25904c340251134ee1a0a03f5ce4b7`; server release under `/srv/restaurant-pos/staging/releases/<same SHA>`.
- Pre-deploy PostgreSQL custom-format backup: `/srv/restaurant-pos/staging/evidence/uber-pre-v29-20260917.dump`, mode0600, 1,042,754 bytes; pg_restore --list successfully read523 lines without restoring data. No restore or destructive DB action.
- Reviewed release rotation and same-host preflight PASS; thresholds: free disk10GiB, used<80%, available memory1GiB, CPU2. Preflight evidence `uber-101dbdc-preflight.txt`; deployment evidence `uber-101dbdc-deploy-r2.log`.
- One preparation retry: the rotation helper required creating the exact worktree itself, so only this run's clean new worktree was removed and recreated through that helper. First deploy guard stopped before build due to an existing mode0775 Staging Python-cache directory; after verifying owner/type, removed group/other write permissions, then reran successfully. No guard was bypassed.
- Flyway latest version29/success=true. Staging loopback health helper PASS. Real synthetic login, auth/me, workspace, frontdesk orders/tables, Uber inbox/connection and printing overview returned HTTP200/success=true. Frontdesk/printing HTML resources and `/ws/info` HTTP200. Browser login form renders; authenticated visual interaction was not completed because browser virtual clipboard cannot receive the private OS clipboard. API/asset smoke is not a full visual acceptance claim.
- Runtime printing policy MOCK, allowed modes DISABLED/MOCK, endpoint configuration disabled. Existing synthetic Store27 remained DISABLED and was not rebound or activated. No real printer or PAD_DIRECT hardware used.

## Public HTTPS and TEST APP configuration

Temporary Sandbox callback:

`https://optional-nice-tubes-perry.trycloudflare.com/api/v1/integrations/uber-eats/webhook`

- TEST APP Primary Webhook saved, authentication `BASIC_HMAC`, signing key set privately to the backend Test Secret. Dashboard table confirmed saved URL/auth. No Production App opened or modified.
- Public HTTPS is a **temporary Cloudflare Quick Tunnel**, not a Production design or durable hostname guarantee. It remains usable only while this process/URL exists. Before resuming, verify health and update TEST APP if the URL changed. [Official temporary-tunnel limitations](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/).
- Reviewed standalone nginx config is versioned at `deployment/cloud/nginx.uber-sandbox-webhook.conf`; runtime copy `/srv/restaurant-pos/staging/state/uber-sandbox-webhook/nginx.conf`, SHA256 `b2c9c98c424a717ac760e7d5a8964c8c6c1c0ce4a265b7bb575d521047ea496c`.
- Sidecar `restaurant-pos-uber-sandbox-webhook`: UID101:101, read-only filesystem, no capabilities, no-new-privileges, tmpfs-only writable temp paths,64MiB/0.10CPU, host loopback127.0.0.1:18081 only. Fixed upstream is Staging127.0.0.1:18080; strips Authorization/Cookie, preserves X-Uber-Signature/X-Environment and body bytes. No access log. nginx -t PASS after moving standard temp directories to tmpfs.
- `restaurant-uber-sandbox-tunnel.service`: separate transient systemd service as ubuntu, NoNewPrivileges,192MiB/10%CPU. Cloudflared2026.9.1 official binary verified SHA256 `03f1f25d1cc93b9ad6c60569d44060bc4f17ed97075760ed8cfca4b12dcd68cc`. No Production nginx reload or port80/443 change.
- Public probes with normal TLS validation: root404; webhookGET405; invalid-signaturePOST401 (~239ms); correctly signed internally generated unknown-eventPOST200/empty (~241ms). Database recorded that synthetic probe as IGNORED. These prove forwarding/raw-body HMAC and no login interception, **not an Uber-generated notification**.
- Official [Webhook guide](https://developer.uber.com/docs/eats/guides/webhooks) routes app events through the Primary URL. No event checkbox subscription was exposed in this Dashboard flow. The implementation supports orders.notification, orders.cancel, scheduled notification and customer_order_edit; real deliveries remain untested pending provisioning.

## Real OAuth and Test Store blocker

- POST `https://sandbox-login.uber.com/oauth/v2/token`: HTTP200 before and after old-secret revocation. Requested and granted `eats.order eats.store.orders.read`; token retained only in private storage, no output.
- Separate Sandbox discovery request granted `eats.store` only for read-only store discovery. This does not expand backend runtime scopes or request Production scope approval.
- GET `https://test-api.uber.com/v1/eats/stores`: HTTP200, `stores=[]`, no next_key. This proves no stores authorized to this Test App, not the absence of every possible Uber test store.
- Official [Sandbox docs](https://developer.uber.com/docs/eats/guides/sandbox) route Test Store requests to [Integration Support](https://t.uber.com/integration-support). Opened the official form, selected Eats → Test Stores & Production Validation → Set up test stores. Prepared one restaurant, Delivery By Uber, this Client ID, successful OAuth/empty-store evidence, Sandbox-only/order-manager/menu-modifier requirements, and explicit no-Production actions.
- Submission is **NOT SENT**: form requires Street Address, City, State/Province, Zip/Postal Code and Country. No address invented or borrowed from Production. Await Owner-supplied test address, then submit the already prepared form. Case ID: none. This is a Test Store request, not an Integration Verification submission.
- Menu mapped/unmapped counts unknown; test order ID and local order ID absent. Real Sandbox accept, semantic parity, GRAB and reliability/restart tests blocked. PAD_DIRECT software evidence remains prior local-only; no physical PASS.
- Integration Verification not started (Sandbox gate not met). Production App/scopes not started; current turn authorizes Test App only. Production Ready=NO.

## Docker / disk hygiene

Disk before70%/18GiB free; after71%/17GiB free. Build Cache before20.66GB (17.06GB reclaimable), after21.13GB (17.41GB reclaimable). Required reviewed cache-hygiene dry-run failed closed because cache records were not clearly eligible; no prune, image deletion, release deletion or volume cleanup was performed. Cache reclaimed0. Existing rollback/recovery artifacts retained; enough disk remained for bounded deploy.

Production backend/frontend/db image IDs and StartedAt were unchanged before/after: backend `41d8e2a2743b…` at2026-08-24T17:44:29Z; frontend `7392658e65d9…` at2026-08-24T17:45:04Z; db `57c72fd2a128…` at2026-07-11T12:09:37Z. Staging health passed; Production mutation=NO.

## Execution time (estimated active stage totals)

| Stage | Time | Notes |
|---|---:|---|
| Investigation / Planning | 4min | UI, official APIs, isolation/host inspection |
| Implementation | 5min | Test Secret rotation/private config, P2 repair, temporary ingress |
| Tests | 3min | Full local regression and fresh-main smoke; runs overlapped |
| Agent6 / PR / Merge | 2min | Two code reviews plus ingress boundary review; overlaps tests |
| Staging Deploy | 6min | Retention guard, safe retry, image builds and V29 |
| Staging Acceptance | 5min | HTTPS/HMAC, APIs, OAuth, Dashboard and provisioning form |
| Evidence / Governance | 3min | Sanitized evidence, credential checks and documentation |
| Total | Approximately28min | Some stages overlap; browser native-permission attempt added about2min wait. |
