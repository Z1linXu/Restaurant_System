# Uber TEST Store binding audit — 2026-10-01

> Earlier 16:xx UTC snapshot. Dashboard access and URL were later verified;
> the [subsequent pre-order check](UBER_PREORDER_CHECK_20261001.md) supersedes
> current readiness/menu counts. Historical failures below are preserved.
>
> Count correction: re-reading this audit's original menu JSON yields **41 root
> items / 258 root-modifier contexts / 0 unreferenced items**, not the 31 / 238 / 10
> reported below. The new and old identity sets match. This was an audit counting
> omission, not a later menu change. The zero-mapping conclusion is unchanged.

Scope: STAGING / TEST ONLY. Evidence is from the current running containers,
Staging database, browser UI, real Uber Testing OAuth/API, and isolated local
tests. No Uber order, Accept/Deny, local Uber order, printing, payment/refund,
Production operation, menu write, secret rotation or account/role change occurred.

The original workspace was dirty and was left untouched. Work is in the isolated
`codex/uber-test-store-binding-audit-20261001` worktree, based on freshly fetched
`origin/main` `43d86d11a0354868df209441cf8a4abc1a1bfa91`. This is not the deployed
application SHA. Prior implementation acceptance remains historical evidence.

## A. Current Staging

| Field | Current observation |
|---|---|
| STAGING_URL | `https://staging-pos.lanzhounoodlesmtl.com` |
| BACKEND_URL | `https://staging-pos.lanzhounoodlesmtl.com/api` |
| Health | `https://staging-pos.lanzhounoodlesmtl.com/api/v1/system/health` — UP |
| WebSocket | `wss://staging-pos.lanzhounoodlesmtl.com/ws` — real HTTP 101 upgrade |
| Deployment root | `/srv/restaurant-pos/staging` |
| Application release directory | `/srv/restaurant-pos/staging/releases/7b70a4fac4e350444bcd37687ca88a8636ff8f2a` — present |
| DEPLOYED_SHA | `7b70a4fac4e350444bcd37687ca88a8636ff8f2a` (immutable image release; no running Git branch) |
| Backend | `restaurant-pos-staging-backend-1`, image `restaurant-pos-backend:staging-7b70a4fac4e350444bcd37687ca88a8636ff8f2a` |
| Backend image ID | `sha256:23f332075f0142f2aeb941da62d58212cd595b1e393a69adb609b4869f18d870` |
| Backend Compose overlay | `/srv/restaurant-pos/staging/pad-direct-endpoints-20260930` |
| Frontend | `restaurant-pos-staging-nginx-1`, image `restaurant-pos-frontend:staging-7b70a4fac4e350444bcd37687ca88a8636ff8f2a` |
| Frontend image ID | `sha256:a146f2b927e29afd46d6a1355712052f58db3c98ab3346f9df72a59fab81d3cf` |
| Frontend assets | `index-CzXAVTN1.js`, `entry-Yk7lggtH.js` |
| Frontend Compose overlay | `/srv/restaurant-pos/staging/ingress-20260930` |
| Environment / Spring profile | `staging` / `cloud` |
| Database | `restaurant-pos-staging-db-1` / `restaurant_pos_staging`, PostgreSQL 16, healthy |
| UBER_INTEGRATION_DEPLOYED | YES; current endpoint, tables, connection API and worker observed |
| Worker | JVM PID 7 native thread `uber-eats-1`; dedicated scheduler running |
| V29 | PASS; success=true, checksum `-1088042546` |
| Current Flyway | V30; startup successfully validated 30 migrations, schema up to date |

All four `uber_eats_*` tables and `orders.external_source`,
`orders.external_order_id`, `orders.external_display_id` exist. Existing public
ingress verifier passed 42 checks, including TLS, assets, health, auth boundary,
Origin rejection, forwarded-header spoof rejection and WSS. The application has
a fixed Staging domain and does not require the temporary tunnel for that entry.

| Runtime setting | Observation |
|---|---|
| UBER_EATS_ENABLED | true |
| UBER_EATS_ENVIRONMENT | sandbox |
| UBER_EATS_CLIENT_ID | PRESENT; matches TEST APP |
| UBER_EATS_CLIENT_SECRET | PRESENT |
| UBER_EATS_WEBHOOK_SECRET | SAME_AS_CLIENT_SECRET by current implementation; no separate variable |
| UBER_EATS_SCOPES | `eats.order eats.store.orders.read` |
| UBER_EATS_WORKER_ENABLED | MISSING; default true, runtime worker confirmed |
| UBER_EATS_STORE_ID | MISSING; binding comes from database, not a global Store ID |
| APP_PRINTING_ALLOWED_MODES | `DISABLED,MOCK,PAD_DIRECT`; target Store remains MOCK |

No secret/token values were emitted or stored in this report or source files.

## B. Uber TEST APP and Test Store

Initially observed Dashboard: **Restaruant Pos**, **TEST APP**, Eats Marketplace,
Sandbox Access Granted. Client ID `t86ofdunSsVjL-0AvK6MA6LCTyF2eYLf`.
Client Secret authentication is selected; one masked secret row remains. No
redirect URI was configured. Production creation was disabled and verification
had not completed. No credential fields were edited.

Fresh tokens were obtained using current Staging backend credentials, held only
in memory. No browser token-generation control was used.

| Requested scopes | HTTP | Actual grant |
|---|---:|---|
| `eats.order eats.store.orders.read` | 200 | Exactly both requested scopes |
| `eats.store` (separate read-only discovery token) | 200 | `eats.store` |
| `eats.pos_provisioning` | 400 | `invalid_scope`; not granted through this client-credentials request |

Runtime default scopes were not expanded. Other scopes were not requested.
Dashboard labels are not treated as token grants.

- Real `GET https://test-api.uber.com/v1/eats/stores`: HTTP 200, **1 Store**, no next page key.
- TEST_STORE_API_VISIBLE = YES.
- Test Store UUID: `bd993244-5589-4b19-8f0d-dc2ba73d4273`.
- Name: **StDenis Test Store**; status **active**.
- Get Store Details: HTTP 200; `pos_data.integration_enabled=true`.
- `pos_data.order_manager_client_id` matches the TEST APP; ORDER_MANAGER = YES
  for observed configuration. Accept/Deny permission was not exercised.
- Address: **4483 Rue Saint-Denis, Montréal, QC H2J 1W2, Canada**. API and visible
  GTS correspondence match the supplied Test Store identity/address.
- User-provided Uber Org UUID `df2ade9f-c21f-4844-8ac3-2001f1d22fab` and tenancy
  `uber/testing/eats.StDenis` are recorded as provided. These fields were not
  returned by the Store API or independently visible in the opened GTS message;
  they are not claimed as independently API-verified.
- GTS case **60508735 / request #0AD0E7** includes a visible reply confirming
  the live menu was copied to the Test Store. No new support request was sent.

Official read-only API references: [Get Store](https://developer.uber.com/docs/eats/references/api/v1/get-eats-stores-storeid),
[Get Menu](https://developer.uber.com/docs/eats/references/api/v2/get-eats-stores-storeid-menu),
[Sandbox](https://developer.uber.com/docs/eats/guides/sandbox).

## C. Webhook

| Field | Observation |
|---|---|
| OLD_WEBHOOK_URL | `https://optional-nice-tubes-perry.trycloudflare.com/api/v1/integrations/uber-eats/webhook` |
| CURRENT_REQUIRED_WEBHOOK_URL | `https://staging-pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook` |
| DASHBOARD_WEBHOOK_URL | Last successfully read value was the old URL above |
| Authentication | PRIMARY / BASIC_HMAC; signing fields left unchanged |
| WEBHOOK_URL_MATCH | NO at last verified read; final saved value cannot currently be verified |
| WEBHOOK_UPDATED | NOT CONFIRMED — URL was filled and Save clicked, but no successful persistence proof |
| PUBLIC_REACHABLE | PASS for fixed-domain POST; valid HTTPS, no redirect/login interception |
| INTERNAL_SIGNATURE_TEST | PASS on current Staging |
| REAL_UBER_WEBHOOK | NOT TESTED |
| OLD_WEBHOOK_URL_STALE | NO as a reachability claim: old GET still returns 405; superseded for desired configuration |

After Save, the visible table still showed the old URL. A full reload then
displayed **“You do not have access to this organization”**. The logged-in
Developer Dashboard home had no accessible applications. Save success must not
be inferred. Exact external blocker: **UBER_DASHBOARD_ORGANIZATION_ACCESS_BLOCKED**.
This browser access failure does not invalidate the successful backend Testing
OAuth/API evidence. Restore the owning Developer account/session and re-read
the TEST APP before retrying a URL-only save.

Fixed-domain probes, with no redirects:

| Probe | HTTP | Elapsed |
|---|---:|---:|
| Invalid `X-Uber-Signature` | 401 | 11 ms |
| Correct HMAC over original request bytes, `X-Environment: sandbox` | 200 | 23 ms |
| GET on POST-only endpoint | 500, generic 63-byte response | 14 ms |
| GET on old tunnel | 405 | 356 ms |

Internal event `internal-binding-audit-20261001-565d7aa0-c752-4c3f-84af-bdcd372ef377`,
type `codex.internal.signature_probe`, is persisted in the current Staging DB as
**IGNORED**, attempt_count=0. This demonstrates raw-body/header forwarding to the
current backend without manufacturing an Uber notification. Uber inbox rows and
local `external_source=UBER_EATS` orders both remain **0**. The internal probe
contains no Test Store/order identity and does not make its connection page say
a real Store webhook was received.

The old tunnel/sidecar remains running, untouched. It is historical infrastructure,
not the selected fixed-domain design. No cleanup was performed that could erase
evidence. GET 500 is an existing method/error classification issue, not a signature
bypass; it is recorded as P2 rather than relabeled 405 PASS.

## D. Target Restaurant_System Store

| Field | Current Staging DB result |
|---|---|
| TARGET_STORE_ID | 1 |
| TARGET_STORE_CODE | STG005_SRC_20260809_R01 |
| TARGET_STORE_NAME | STG005_SRC_20260809_R01 |
| TARGET_ORGANIZATION_ID | 1 |
| TARGET_ORGANIZATION_NAME | STG005_ORG_20260809_R01 |
| TARGET_STORE_STATUS | active; lifecycle ACTIVE; store_kind BUSINESS |
| Printing | enabled=true, mode=MOCK |
| STAGING_CONFIRMED | YES; unique code result from isolated Staging database |

Enabled/configured modules: ORDERING_POS, MENU, MENU_MANAGEMENT, TABLE_MANAGEMENT,
PRINTING, ORDER_HISTORY, REPORTING_CORE, STAFF_ACCESS, STORE_ADMINISTRATION.
KDS and ANALYTICS_ADVANCED are disabled. These settings were not changed.

There is no formal `UBER_EATS` FeaturePackage/module key. Current control is the
global integration flag plus binding.enabled; `connection.enabled` alone only
reports the global flag. No feature-package redesign was performed.

## E. Uber Store mapping — PASS

MAPPING_BEFORE: **MISSING**; the actual mapping table contained zero rows, and
there were no Uber orders or actionable/recovery events.

MAPPING_AFTER, committed **2026-10-01 16:42:16 UTC**:

| Field | Value |
|---|---|
| id | 1 |
| environment | sandbox |
| uber_store_id | bd993244-5589-4b19-8f0d-dc2ba73d4273 |
| store_id / organization_id | 1 / 1 |
| enabled | true |
| created_at (server local) | 2026-10-01T12:42:16.692013 |
| Audit record | 768, UBER_STORE_MAPPED |

**FINAL_MAPPING_STATUS = PASS**:
`bd993244-5589-4b19-8f0d-dc2ba73d4273` → `STG005_SRC_20260809_R01`.

The normal binding service requires platform ADMIN, and this Staging DB has
zero ADMIN users. Rather than change accounts/roles, the explicitly authorized
maintenance used one serializable SQL transaction. Guards verified backend
staging/sandbox/TEST Client ID, exact database, unique target code, Store/org,
active/MOCK mode, empty mapping table and absence of actionable Uber work.
The target row was locked; mapping table serialization and existing unique/FK
constraints remained enforced. One binding and one truthful SYSTEM maintenance
audit row were inserted. No old rows were deleted or reassigned.

Before/after transaction evidence is retained privately on the Staging host at
`/srv/restaurant-pos/staging/evidence/uber-test-binding-20261001.json`.

## F. Organization/Store scope and frontend

- Binding.organization_id equals target Store.organization_id. Import/recovery
  checks binding environment, enabled status and current local organization.
- Authenticated OWNER target context, connection and inbox calls: **HTTP 200**;
  connection has exactly the expected binding, inbox is empty.
- Same OWNER Store 18 and Store 24 context/connection/inbox: **HTTP 200**, each
  connection has no binding and inbox is empty; target binding is not leaked.
- Existing FRONTDESK user 3 has workspaces `[1]`; target three endpoints return
  **200**, the same endpoints for existing Stores 18 and 24 return **403**.
- Actual Staging has 9 Stores, all Organization 1. A foreign-organization runtime
  403 test was therefore unavailable. Organization guard is code-reviewed; no
  foreign organization/credential or test order was created to fabricate proof.
- WRONG_STORE_VISIBLE = NO in the executed probes. CROSS_TENANT_RISK = no
  detected misbinding; this is not a claim of exhaustive cross-tenant testing.
- STORE_SCOPE = PASS for the executed checks and reviewed guards.
- Frontdesk target: `https://staging-pos.lanzhounoodlesmtl.com/stores/1/frontdesk`.
- Inbox target: `https://staging-pos.lanzhounoodlesmtl.com/stores/1/frontdesk/uber-eats`.
- Browser confirms selected Store code, bound UUID on management page, and
  the empty target Inbox. The Inbox toolbar has the active-Store Uber button.
- Route/query/badge code uses current Store context, with no hardcoded Store 1.

**FRONTDESK_ENTRY_BADGE = PARTIAL**: the desktop DineInSidebar has no Uber entry.
The iPad landscape TopNav (1024–1400 wide) and Inbox/Orders TopNav do include
the badge. This matches current runtime observation; direct Inbox navigation
works. No badge PASS is claimed for the desktop landing page.

## G. Menu state — read only

GET_MENU = PASS (HTTP 200 using actual `eats.store` grant). GTS correspondence
confirms menu-copy completion; this is not assumed to be an empty/default menu.

| Metric | Count |
|---|---:|
| Menus | 1 |
| UBER_MENU_ITEMS (all API item objects) | 74 |
| Category-referenced root item IDs | 31 |
| UBER_MODIFIER_GROUPS | 11 |
| Unique modifier option item IDs | 33 |
| Root item + modifier ID contexts | 238 |
| Target stored mapping rules | 0 |
| MAPPED_ITEMS / UNMAPPED_ITEMS | 0 / 31 |
| MAPPED_MODIFIERS / UNMAPPED_MODIFIERS (unique IDs) | 0 / 33 |
| Mapped / unmapped modifier contexts used by current rules | 0 / 238 |
| Unreferenced API item objects | 10 |
| Local effective catalog categories / active items / option rows | 6 / 35 / 270 |

All 74 Uber item objects have empty external_data; no explicit mappings exist.
Consequently there are no exact external_data=SKU/option_code fallback matches.
Coverage is based on stable identifiers and the existing context-sensitive
mapping algorithm, not display-name guesses. There are no nested modifier groups
in this menu. Root/category and modifier objects are counted separately; 74 is
not the number of purchasable main dishes. Static mapping state is
**MAPPING_REQUIRED**. No actual order has been imported to calculate per-order
mapping errors. MENU_CHANGED = NO.

The menu-only snapshot is private on the Staging host at
`/srv/restaurant-pos/staging/evidence/uber-test-menu-20261001.private.json`.

## H. Changes and validation

- Dashboard TEST: attempted primary webhook URL correction only; save outcome
  unconfirmed after organization access failure. No signing key or scope changed.
- Staging config: none. Runtime scopes, printing policy and secrets unchanged.
- Database: one correct binding and maintenance audit row, plus one IGNORED
  internal signature probe. Existing events/orders/menu/printing data retained.
- Restart/redeploy: none; existing release was already sufficient.
- Code: only the Uber migration test's obsolete total-count=29 assertion was
  replaced by Flyway validate, no pending migrations, and exactly one successful
  V29 record. Five unique-constraint assertions retained. No application logic,
  migration, frontend code, payment or PAD_DIRECT semantics changed.
- Documentation: this report/current-state pointers and the latest St-Denis-only
  Production candidate plan; historical observations retained and labeled.
- **PRODUCTION_CHANGED = NO**. Production was not inspected/mutated this round;
  prior Production release metadata is retained as dated historical evidence.

| Verification | Result and boundary |
|---|---|
| Backend `mvn -Dtest='UberEats*Test' verify` | PASS; 24/24, 0 skips; isolated local PostgreSQL |
| Fresh empty DB migrations | PASS V1–V30, Flyway validate/no pending and Hibernate schema validate |
| Frontend UberInboxPage/useUberInbox tests | PASS 7/7 |
| Frontend production build | PASS |
| Current Staging ingress checks | PASS 42 checks |
| Current DB V29/V30 and backend startup validation | PASS |
| Real Testing OAuth and read-only Store/menu | PASS; scope limits above |
| Internal webhook signature/current DB receipt | PASS; no real Uber notification |
| Runtime target context / cross-Store access | PASS within section F limits |
| Independent review of migration test change | No P0/P1; no tests rerun by reviewer |
| Independent report/diff review | PASS; no secret values or inflated E2E/Production claims found |
| Governance validation | PASS in an export of tracked and task-new files, excluding ignored build dependencies |
| `git diff --check` | PASS |

The first relevant backend test run failed solely on hardcoded migration total
29 after V30 was added; the bounded test repair and rerun passed. This round did
not run every backend/frontend test or assert real Sandbox E2E success.

## I. Risks / blockers

- **P0:** none found within audited scope.
- **P1 readiness blocker:** `UBER_DASHBOARD_ORGANIZATION_ACCESS_BLOCKED`; cannot
  confirm/save the required fixed-domain primary webhook in the current session.
- **P1 before Accept:** no item/modifier mappings. Notification/inbox observation
  may be tested after webhook is confirmed, but Accept must stay blocked until
  the actual order is fully mapped. No silent dropping is allowed.
- **P2:** desktop DineInSidebar lacks a direct Uber entry/badge; use the verified
  direct Inbox route. GET on the current POST-only webhook returns generic 500
  rather than 405. Neither observation is reported as a successful UI/GET check.
- `eats.pos_provisioning` is not granted by the tested flow; no current provisioning
  call is needed because the official Test Store already names this TEST APP as
  manager. Org UUID/tenancy independent verification is limited as documented.

## J. Next step

**READY_FOR_REAL_SANDBOX_ORDER_TEST = NO** until the fixed-domain Dashboard
webhook value is successfully re-read. Human action: restore the Uber Developer
session that owns this TEST APP and confirm the Setup page is accessible. Do not
send credentials in chat. The binding and API validations do not need to restart.

After the URL-only correction is saved and verified, the next authorized manual
test can open the [official Test Store](https://www.ubereats.com/ca-zh/store/stdenis-test-store/vZkyRFWJSxmPDdwrpz1Ccw?diningMode=DELIVERY),
create one Sandbox order, observe real orders.notification, check this exact
Staging Store's Inbox, and only then decide about mapping and Accept under the
next task's authorization. None of those order/decision steps was executed here.
