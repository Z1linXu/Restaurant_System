# STG-005 Synthetic Business Acceptance Plan

## Historical verified execution state (2026-08-10)

The Staging-only synthetic sequence remains complete without duplication on
exact Staging `1a3f2e...`: STG-005A
and STG-005B PLAN/EXECUTE/REPLAY are `VALIDATED/CREATED/REPLAYED`; menu counts
are `4/3/13/38` and replay revision is `2 -> 2`. Organization, Owner, source
Store and credential are ready; no one-shot or blocked marker remains and the
lock is empty. This is the `CURRENT_SYNTHETIC_BASELINE`, not a Production
parity Twin. The repaired runtime passed private credential rotation, API and
real-Chrome browser-equivalent checks without a 401/403. The former Phase-A
manual gate is preserved but deferred by the Owner Twin route; no Chinatown or
Production action is implied.

> Loop: `STG-005_SYNTHETIC_BUSINESS_ACCEPTANCE`
>
> Phase: `PLAN`
>
> Status: `STG-005_PLAN_COMPLETE_WAITING_FOR_OWNER_REVIEW`
>
> Planning date: 2026-07-30, America/Toronto
>
> Planning baseline: `origin/main`
> `2e6be1ac44f59cd6e005e68e61f8c567ea80022e`
>
> Runtime under future test:
> `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`
>
> This document does not authorize SSH, API or database writes, Docker
> operations, a Staging restart, account or Store creation, STG-006, AL-003,
> merge, or deployment.

## 1. Purpose and fixed boundaries

STG-005 will provide a repeatable synthetic business acceptance run against
the isolated server Staging environment established by STG-004. The run will
exercise owner-scoped onboarding, staff and Store isolation, bounded menu and
table setup, ordering, realtime refresh, disabled printing, and persistence.
It must not use production data, credentials, devices, printers, or customer
information.

The future execution must retain all STG-004 identities:

| Item | Required value |
|---|---|
| Runtime SHA | `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` |
| Compose project | `restaurant-pos-staging` |
| Access | SSH tunnel only |
| Printing | `DISABLED` |
| Production | Unchanged |

This planning pass used only local repository and documentation inspection.
No runtime command or mutation was performed.

The requested document names were reconciled to repository-tracked paths:
`docs/governance/runtime/KNOWN_ISSUES_BACKLOG.md` and
`docs/governance/runtime/FEATURE_BACKLOG.md` do not exist; the authorities are
`docs/governance/KNOWN_ISSUES_BACKLOG.md` and
`docs/governance/FEATURE_BACKLOG.md`. The repository API overview is
`doc/API.md`, not root-level `API.md`.

## 2. Executable-interface findings

The plan is based on the following repository authorities. An endpoint or UI
not listed here must not be invented during execution.

| Capability | Existing authority | Current usability |
|---|---|---|
| Login | `AuthController.login`, `POST /api/v1/auth/login` | Requires an existing active BCrypt credential. |
| Owner Store onboarding | `OwnerStoreOnboardingController.onboard`, `POST /api/v1/owner/organizations/{organizationId}/stores/onboard` | API only. Requires an authenticated `OWNER`, exact Organization membership, source Store, `Idempotency-Key`, and at least one staff account. |
| Organization creation | `PlatformAdminController.createOrganization`, `POST /api/v1/admin/platform/organizations` | Requires an authenticated Platform Admin capability and the PLATFORM feature. It is not a first-user bootstrap path. |
| Staff management | `StaffAdminController`, `/api/v1/admin/staff` | UI and API exist after an authorized Store-scoped account is available. |
| Stations/categories/items | `PlatformAdminController` station and menu endpoints | API is the confirmed setup route for stations/categories; menu-item UI may be used after dependencies exist. |
| Item options | `OwnerMenuOptionController`, `/api/v1/admin/menu/items/{itemId}/options` | UI or API after the item exists. |
| Dining tables | `PlatformAdminController` dining-table endpoints and `DiningTablesManagementPage` | UI or API after Store authorization exists. |
| Idempotent order submit | `IdempotentOrderSubmissionController.submit`, `POST /api/v1/stores/{storeId}/orders/idempotent-submit` | Used by the frontend offline/outbox flow. API `order_type` values are `dine_in` and `pickup`; the UI labels pickup as Takeout. |
| Order lifecycle | `OrderController`, `/api/v1/orders` | Synthetic orders only; no payment or refund acceptance is included. |
| Realtime | `WebSocketConfig`, `RealtimeTopics`, and frontend STOMP subscribers | SockJS/STOMP subscriptions can be observed without logging message payloads containing business detail. |
| Printing | Staging Compose plus `PrintDispatcherServiceImpl` | Staging printing feature and Store printing must remain disabled. No print API, printer, Pad, or job operation is allowed. |

### 2.1 Blocking bootstrap fact

The current empty Staging runtime contains no Organization, Store, or user.
Staging disables default users, demo data, membership supplementation,
production bootstrap, developer role switching, and the PLATFORM feature.
`RuntimeDataSeeder` also records that production bootstrap user creation is
not implemented.

Therefore there is no currently verified formal entry point that can create
the first synthetic Organization, source Store, and Owner credential from an
empty Staging installation:

- AL-002 cannot bootstrap itself because it requires an existing Owner,
  Organization membership, and source Store.
- Platform Admin organization creation requires an already authenticated
  Platform Admin and does not grant an Owner membership.
- An `ADMIN` is not an implicit bypass for the owner-only AL-002 endpoint.
- Direct SQL, migrations, seed hacks, developer switching, and production
  credentials are prohibited.

**Execution is `NO_GO` at checkpoint CP-0 until the Owner chooses and separately
authorizes one safe prerequisite:**

1. provide an already-existing, approved synthetic Owner/Organization/source
   Store in Staging; or
2. authorize a separate, minimal, Staging-only bootstrap implementation and
   review cycle.

The second option is not part of this STG-005 plan PR.

### 2.2 KDS and Assembling availability fact

The current cloud backend configuration and frontend feature configuration
disable KDS. Positive Kitchen and Assembling page acceptance therefore cannot
be claimed against the exact current build. STG-005 may verify the expected
feature-disabled boundary and may use read-only database evidence that
submitted orders produced kitchen tasks. Positive KDS workflow acceptance
requires a separate Owner decision and exact-SHA build/config authorization.

## 3. Synthetic naming and data rules

Every non-secret synthetic identifier or display name created by this run must
start with `STG005_`. A run identifier prevents collisions:

```text
STG005_<YYYYMMDD>_R<NN>
```

Example naming:

| Object | Pattern |
|---|---|
| Organization | `STG005_ORG_<RUN>` |
| Source Store | `STG005_SRC_<RUN>` |
| Target Store name/code | `STG005_STORE_<RUN>` |
| Owner login/display name | `STG005_OWNER_<RUN>` |
| Manager login/display name | `STG005_MANAGER_<RUN>` |
| Frontdesk login/display name | `STG005_FRONTDESK_<RUN>` |
| General STG-005 station/category/item/option fixture | `STG005_` display and technical identifiers |
| AL-003 synthetic source menu | `STG005_` display names; reviewed semantic category/station/SKU/option codes |
| Table label/code | `STG005_TABLE_<RUN>` |
| Idempotency key | `STG005_ONBOARD_<RUN>_<CASE>` |
| Local draft/client order ID | `STG005_ORDER_<RUN>_<CASE>` |

Passwords and JWTs are runtime secrets, not naming fields. They must be newly
generated, supplied through an approved secure channel, and never written to
Git, evidence, shell history, logs, screenshots, or API transcripts.

The run must not use real people, restaurants, phone numbers, email addresses,
customers, notes, printer endpoints, Pad registrations, or production data.
Synthetic free-text fields must be blank or use an `STG005_` marker.

The AL-003 synthetic source baseline is the explicit exception for technical
menu identifiers: its category/station codes, item SKUs, option groups, and
option codes must match the reviewed clone profile. Only topology/run identity
and human-readable synthetic labels use `STG005_`. This prevents a synthetic
SKU prefix from causing `SOURCE_SKU_MISSING` during formal clone validation.

## 4. Synthetic topology

The minimum topology, after CP-0 is satisfied, is:

```text
STG005_ORG_<RUN>
  |
  +-- STG005_SRC_<RUN>
  |     +-- STG005_OWNER_<RUN> (active OWNER Organization membership)
  |
  +-- STG005_STORE_<RUN>
        +-- STG005_MANAGER_<RUN> (target Store only)
        +-- STG005_FRONTDESK_<RUN> (target Store only)
        +-- one station
        +-- one category
        +-- two menu items plus one option
        +-- one dining table
```

AL-002 creates the target Store as `inactive`, with
`printing_enabled=false`, `printing_mode=DISABLED`, and no cloned menu,
printer assignment, or Pad. Activation or other configuration must use only
an already-authorized formal endpoint. If no current Store-scoped endpoint can
perform a required transition, that step remains `EVIDENCE_PENDING`; direct
database updates are not a substitute.

## 5. Owner login prerequisite

Before any business write is approved, CP-0 evidence must identify:

- synthetic Owner user ID and redacted login label;
- Organization ID and source Store ID;
- active Owner Organization membership;
- the formal mechanism that created those prerequisites;
- confirmation that no production credential or data was reused;
- confirmation that the credential is active and login succeeds;
- confirmation that the Owner cannot access another synthetic Organization.

Only the login UI or `POST /api/v1/auth/login` may be used. Tokens are kept in
the approved client session and are never copied into evidence. There is no
approved bootstrap-admin shortcut in the current exact runtime.

## 6. AL-002 onboarding acceptance matrix

Each case requires a fresh, named `STG005_` key unless the case explicitly
tests replay. Password fields must be redacted from all retained evidence.

| ID | Case | Expected result | Required evidence |
|---|---|---|---|
| ONB-01 | Valid Owner, own Organization/source Store, new key and one or more supported staff entries | One inactive target Store; staff credentials/memberships created; printing disabled; HTTP success; `replayed=false` | Sanitized response fields, Store/staff IDs, read-only counts and status |
| ONB-02 | Replay exact ONB-01 key and exact request, including the same supplied password values | Same Store and staff IDs; no duplicate rows; `replayed=true` | First/replay IDs and before/after counts |
| ONB-03 | Same key with changed Store name/code or staff structure | HTTP 409 `IDEMPOTENCY_CONFLICT`; no new Store/staff | Safe error code and unchanged counts |
| ONB-04 | Same key and structure but changed staff password | HTTP 409 `IDEMPOTENCY_CONFLICT`; no credential change | Safe error code; no password/hash value |
| ONB-05 | Missing `Idempotency-Key` | HTTP 400; no data created | Status and unchanged counts |
| ONB-06 | Unsupported staff role | HTTP 400; no data created | Safe error code and unchanged counts |
| ONB-07 | Duplicate normalized Store code in the same Organization | HTTP 409 `STORE_CODE_CONFLICT`; no duplicate Store | Normalized code and unchanged counts |
| ONB-08 | Owner requests another Organization | HTTP 403; no onboarding record or Store | Status, actor ID, target Organization ID |
| ONB-09 | Source Store belongs to another Organization | HTTP 403; no onboarding record or Store | Status and unchanged counts |
| ONB-10 | Concurrent exact requests with one key | Exactly one target Store. A contender may replay after completion or receive the defined in-progress conflict; it must not create a duplicate | Request timing, statuses, one Store ID, row counts |
| ONB-11 | Injected transactional failure in a separately approved test harness | No partial Store, membership, user, or credential | Automated test evidence only unless Owner approves a safe Staging fault path |

ONB-11 is not authorization to damage the running Staging database. Existing
backend transaction tests are the default evidence unless a safe fault
injection mechanism is separately approved.

## 7. Authorization and Store-scope matrix

| Actor | Own target Store | Source Store | Another Organization/Store | Expected |
|---|---|---|---|---|
| Owner | Owner/admin surfaces allowed by current capabilities | Allowed through Organization membership | Denied | 403 on cross-Organization access |
| Manager | Target Store management allowed by current capabilities | No inherited access | Denied | Workspace and API remain target-Store scoped |
| Frontdesk | Target Store frontdesk/ordering only | No inherited access | Denied | Admin, Staff, and cross-Store requests denied |
| Unauthenticated | None | None | None | 401 |

Evidence must include positive and negative requests for the same endpoint
family. A hidden UI route alone is not authorization evidence; the API must
also deny out-of-scope requests.

## 8. Synthetic staff acceptance

AL-002 supplies at least one target-only `MANAGER` or `FRONTDESK`. Additional
bounded checks may use `StaffAdminController` and the Staff Management UI:

1. Owner creates or confirms `STG005_MANAGER_<RUN>` and
   `STG005_FRONTDESK_<RUN>`.
2. Stored credential existence is checked only as
   `password_hash IS NOT NULL`; no hash or raw password is selected.
3. Each staff member logs in through the formal login flow.
4. Workspace lists only `STG005_STORE_<RUN>`.
5. A request to the source Store and a different Store returns 403.
6. Frontdesk cannot manage staff.
7. Manager/Owner capability behavior matches the current controller rules.
8. Cleanup uses the formal deactivate endpoint; it never deletes users or
   credentials directly.

## 9. Synthetic menu, category, item, and option acceptance

After Store authorization is established:

1. Create one `STG005_STATION_<RUN>` and one
   `STG005_CATEGORY_<RUN>` through confirmed admin APIs.
2. Create two active `STG005_ITEM_<RUN>` items with synthetic SKUs and harmless
   prices; create one `STG005_OPTION_<RUN>` option on one item.
3. Verify the Menu Management UI and menu API show only the target Store data.
4. Verify another Store cannot read or mutate these records.
5. Add an item to a local draft, update the server menu item, and confirm the
   existing draft snapshot is retained while newly added content uses the
   refreshed menu.
6. Confirm no production or source-Store menu content was copied. Real-time
   menu clone is AL-003 and must not be introduced here.
7. Cleanup uses supported inactive/update behavior. No direct delete, truncate,
   or database reset is allowed.

Station and category creation are planned through API because a current,
enabled, Store-scoped creation UI was not confirmed. Menu item and option
checks may use the existing UI after their dependencies exist.

## 10. Synthetic dining-table acceptance

Create one `STG005_TABLE_<RUN>` through the formal dining-table API or
Dining Tables Management UI. Verify:

- it is visible only in the target Store;
- its code/label remains stable after refresh;
- another Store request is denied;
- it can host a synthetic dine-in draft/order;
- cleanup uses the supported inactive/update behavior.

No production table code or physical restaurant label may be reused.

## 11. Dine-in and takeout ordering acceptance

Use the actual frontend so IndexedDB draft/outbox and the idempotent submit API
are exercised. The UI term Takeout maps to API `order_type=pickup`; the plan
must not invent a `takeout` enum value.

### Dine-in

1. Start an order on `STG005_TABLE_<RUN>`.
2. Add the synthetic item and option, with no personal data.
3. Reload once to verify draft restoration.
4. Submit once through the UI.
5. Verify one server order, one idempotency result, expected items/snapshots,
   and kitchen tasks using read-only API/DB evidence.
6. Repeat the client submission with the same stable key only in the dedicated
   replay case; verify no duplicate order/tasks.

### Takeout

1. Select Takeout in the UI.
2. Add a synthetic item without customer identity or contact data.
3. Submit through the same outbox/idempotent path.
4. Verify one server order with `order_type=pickup`.
5. Verify Store isolation and no duplicate kitchen or print effects.

Payment, refund, production customer data, real phone/email values, and
unrelated `completeOrder` changes are outside this loop.

## 12. Frontdesk, Kitchen, and Assembling acceptance

| Surface | Current STG-005 action | Verdict rule |
|---|---|---|
| Frontdesk | Verify login, Store workspace, table/takeout context, submitted order visibility, reload recovery, and cross-Store denial. | `PASS` when UI and API agree for target Store. |
| Kitchen/KDS | Verify current feature-disabled boundary. Use read-only evidence to confirm whether kitchen tasks were generated. Do not enable KDS in this loop without a separate Owner checkpoint. | Positive workflow is `EVIDENCE_PENDING`; unexpected access is `NO_GO`. |
| Assembling/GRAB | Verify current feature-disabled boundary only. Do not perform task transitions through a hidden or disabled route. | Positive workflow is `EVIDENCE_PENDING`; unexpected authorization bypass is `NO_GO`. |

The current frontend and cloud backend both disable KDS. A positive Kitchen or
Assembling workflow requires a separately reviewed exact-SHA change or runtime
configuration decision and cannot be silently folded into execution.

## 13. WebSocket and refresh recovery

Use two approved browser sessions against the SSH-tunneled Staging URL:

1. Session A performs one already-planned synthetic order action.
2. Session B subscribes through the application's normal STOMP/SockJS client.
3. Record connection state and sanitized event metadata only: topic, event
   type, Store ID, order ID, and status. Do not retain headers, tokens, notes,
   item details, or full frames.
4. Confirm Session B updates without a manual full-page refresh.
5. Disconnect/reconnect Session B, reload, and confirm HTTP state recovery.
6. Confirm no duplicate requests or writes were introduced solely to test
   realtime delivery.

SockJS `/ws/info` HTTP 200 alone is not a STOMP acceptance result.

## 14. Printing-disabled acceptance

Before and after each ordering case, verify:

- runtime printing feature is disabled;
- target Store has `printing_enabled=false` and
  `printing_mode=DISABLED`;
- no printer config, assignment, registered Pad, or endpoint exists for the
  synthetic Store;
- no `print_jobs` row is created for synthetic orders while the printing
  feature is disabled;
- no print, reprint, claim, payload, complete, fail, release, TCP, or Pad action
  occurs.

An unexpected print job, device call, endpoint, or physical print is an
immediate `NO_GO`.

## 15. Staging restart persistence

This step requires a separate Owner checkpoint after the business cases pass.
Only project `restaurant-pos-staging` may be stopped and started through the
approved control script. Before and after evidence must show:

- exact runtime SHA and image identity unchanged;
- Organization, Store, staff, menu, table, orders, idempotency records, and
  Flyway V1-V8 remain present;
- replay after restart returns the same onboarding/order result;
- health, frontend root, and SockJS entry recover;
- printing remains disabled;
- production `cloud` container IDs/start times/restart counts remain unchanged.

`down -v`, restore, Flyway clean/repair, Production restart, and state removal
are forbidden.

## 16. Production zero-impact evidence

At future CP-4 and CP-5, owner-approved read-only evidence must compare:

- production project container IDs, states, start times, and restart counts;
- production HTTP health;
- port bindings, confirming Staging remains loopback-only on `18080`;
- resource headroom;
- absence of Production checkout/config/database changes.

No Production `.env`, data, tokens, or customer records may be read. Any
Production restart, health regression, resource stop threshold, or identity
change is `NO_GO`.

## 17. Evidence format

Each retained evidence item uses this schema:

```text
evidence_id: STG005-E###
timestamp_utc: <ISO-8601>
runtime_sha: <full SHA>
compose_project: restaurant-pos-staging
actor_role: <OWNER|MANAGER|FRONTDESK|SYSTEM_READ_ONLY>
channel: <UI|HTTP_API|STOMP|DB_READ_ONLY|CONTAINER_READ_ONLY>
case_id: <ONB-01, ORDER-DINEIN-01, ...>
synthetic_identifiers: <STG005_ identifiers only>
expected: <sanitized expectation>
actual: <sanitized result>
classification: <PASS|NO_GO|EVIDENCE_PENDING>
artifact_sha256: <when an artifact is retained>
owner_checkpoint: <approval reference>
```

HTTP evidence retains method, path template, status, safe error/result code,
and synthetic IDs. It must redact passwords, tokens, cookies, Authorization
headers, full request/response payloads, credential hashes, notes, and personal
data. Database evidence uses bounded SELECTs and boolean existence checks; it
does not select password/token/hash contents.

## 18. Verdict definitions

| Verdict | Meaning |
|---|---|
| `PASS` | The exact case met its expected behavior with retained, bounded evidence. |
| `NO_GO` | A security, isolation, printing, data-integrity, production-impact, or prerequisite gate failed. Execution stops. |
| `EVIDENCE_PENDING` | The case was not safely executable or did not retain sufficient evidence. It is not success or failure. |

The overall loop may pass only when every mandatory case is `PASS` and every
optional/blocked case is explicitly accepted by the Owner as
`EVIDENCE_PENDING`. Cross-Organization access, duplicate Store/order creation,
credential leakage, physical printing, or Production impact can never be
accepted as pending.

## 19. Interruption and cleanup strategy

Execution stops immediately on:

- missing or unapproved bootstrap prerequisites;
- authorization leakage or unexpected Store/Organization access;
- duplicate Store, membership, order, task, or idempotency result;
- password/token/hash leakage;
- printing not disabled or any printer/Pad interaction;
- exact-SHA/project/port drift;
- Production container, health, or resource regression;
- a required action lacking a confirmed API/UI;
- any need for direct database mutation.

Synthetic records are not destroyed by SQL. Cleanup is an auditable lifecycle:

1. cancel or complete only synthetic orders using existing business APIs when
   the acceptance case explicitly authorizes it;
2. deactivate synthetic staff using the staff API;
3. deactivate synthetic menu/table records using supported update APIs;
4. leave immutable onboarding/idempotency/audit records intact;
5. mark Store/Organization cleanup `EVIDENCE_PENDING` when no authorized
   endpoint exists, rather than using SQL;
6. retain a manifest of remaining `STG005_` IDs for later approved cleanup.

Forbidden cleanup includes truncate, drop, restore, Flyway clean, `down -v`,
direct DELETE/UPDATE, database reset, or deletion of non-synthetic data.

## 20. Owner checkpoints and phased execution

| Checkpoint | Owner decision | Gate |
|---|---|---|
| CP-0 Bootstrap | Choose an existing approved synthetic prerequisite or authorize a separate Staging-only bootstrap implementation. | Current state: `NO_GO` until decided. |
| CP-1 Credentials and write scope | Approve secure synthetic credentials, exact run ID, API/UI cases, and evidence redaction. | Required before any login or write. |
| CP-2 Onboarding and configuration | Approve ONB matrix plus staff/menu/table writes. | Stop after configuration for review. |
| CP-3 Ordering and realtime | Approve dine-in/pickup submissions and two-session STOMP observation. | No payment, print, or production writes. |
| CP-4 KDS decision | Accept feature-disabled boundary or authorize a separate exact-SHA KDS enablement cycle. | Positive KDS/Assembling is otherwise pending. |
| CP-5 Restart | Approve Staging-only stop/start persistence check. | Production continuity required. |
| CP-6 Cleanup and closure | Approve supported logical deactivation and retained-record manifest. | No direct database cleanup. |

## 21. Acceptance criteria

STG-005 execution is complete only when:

1. bootstrap provenance is approved and contains no production identity/data;
2. ONB-01 through ONB-10 meet their mandatory expected outcomes;
3. one Store only is created for concurrent/replayed onboarding;
4. credential redaction and target-Store-only memberships pass;
5. menu, option, and table data remain target-Store scoped;
6. dine-in and pickup submissions create exactly one order each;
7. draft/reload and idempotent replay recovery pass;
8. Frontdesk UI and API state agree;
9. STOMP delivery and HTTP refresh recovery pass, or are explicitly retained
   as `EVIDENCE_PENDING` with Owner acceptance;
10. current KDS/Assembling feature-disabled behavior is accurately recorded;
11. printing remains disabled and creates no print jobs or physical output;
12. Staging-only restart retains data and Flyway V1-V8;
13. production remains unchanged;
14. all retained evidence is sanitized and tied to the exact runtime SHA;
15. cleanup uses only supported APIs and leaves a synthetic-ID manifest.

## 22. Current risks and Owner decisions required

| Risk/decision | Current state |
|---|---|
| No safe first Owner/Organization/source Store bootstrap in empty Staging | `NO_GO`; Owner must choose CP-0 approach. |
| Positive Kitchen/Assembling flow disabled by current backend/frontend flags | `EVIDENCE_PENDING`; Owner must decide whether the disabled-boundary check is sufficient. |
| No confirmed Owner onboarding frontend UI | API-only AL-002 acceptance; no UI is to be invented. |
| Organization/Store final cleanup endpoint availability | `EVIDENCE_PENDING`; retain synthetic manifest if no formal endpoint exists. |
| Concurrent request orchestration against shared-host Staging | Must be bounded and Owner-approved; no load/stress test. |
| Staging restart on the shared production host | Separate CP-5 approval and resource/production preflight required. |

These two decisions are required before any STG-005 execution:

1. approve the CP-0 synthetic bootstrap source;
2. accept KDS/Assembling as feature-boundary evidence or authorize a separate
   enablement cycle.

## 23. Planned implementation footprint

This PLAN PR changes documentation only:

- `docs/governance/runtime/STG-005_SYNTHETIC_ACCEPTANCE_PLAN.md`
- `docs/governance/runtime/ALIVE_RUNTIME_PLANBOOK.md`
- `SYSTEM_DOCUMENTATION.md`

No application, migration, configuration, deployment, Android, generated
asset, or runtime file is modified.

## 24. Current STG-008 checkpoint (2026-08-08)

The historical CP-0 implementation gap was later closed in repository main by
the guarded STG-005A bootstrap and STG-005B source-menu packages. Exact
`2837ae88e55142c99c6975f8b6575febffc913a1` is now deployed to isolated
Staging at Flyway V10 and `STG-007=PASS`.

The Owner-authorized STG-008 entry nevertheless stopped before the first
one-shot. Read-only evidence proved all Organization/Store/user/credential/
membership/bootstrap-request counts are zero and the next generated Store ID
is safely `1`; it also proved Staging readiness/printing/isolation and
Production continuity remain intact. The requested account convention does
not satisfy the reviewed `STG005_` identity and 12-through-256 password guard.
No bootstrap plan, create, replay, source-menu action, login, or data mutation
occurred.

That entry decision was later resolved when the Owner approved
`STG005_OWNER_20260808_R01` without changing the password guard. Fresh exact
readiness passed, but the password-free STG-005A plan one-shot stopped before
its command/data path because the older cloud startup safety rule rejected the
profile's required Flyway-disabled mode. Cleanup and zero-write continuity
passed, and the launcher retained blocked state.

Current decision: the startup-safety dependency repair entered main through PR
#85 and the blocked-state-safe release-rebind repair entered main through PR
#87 at `4b954e09...`. A later Owner-authorized continuation deployed exact
`6753855497...` to Staging at V10, then exact `2a6c30a...` proved the
request-context repair by reaching password-free `VALIDATED`. The non-web
WebSocket broker nevertheless held the one-shot alive to its timeout; zero
synthetic rows changed and the new fail-closed pair remains. PR #91 then put
the bounded lifecycle repair in PR #91; PR #92's governance-only merge makes
`main@468b8705...` is deployed to Staging with the synthetic A/B sequence
complete. `STG-008=PASS`; the prior Phase-A PASS claim is API-only and
superseded by manual browser 403 evidence. Chinatown Phase B is
blocked until Phase A is reverified and then reaches its Owner Runtime Gate.
The immutable entry result remains
[STG-008 Synthetic Topology and Source Entry Evidence](STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md),
and the resumed failure/repair is
[STG-008 Flyway Guard Repair Evidence](STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md).
The subsequent fresh baseline/deadlock and repository correction are in
[STG-008 Release-Rebind Serialization Repair Evidence](STG-008_RELEASE_REBIND_SERIALIZATION_REPAIR_EVIDENCE.md).
The historical request-context repair is in
[STG-008 Non-Web Request-Context Repair Evidence](STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md);
the current lifecycle repair is in
[STG-008 One-Shot Lifecycle Repair Evidence](STG-008_ONE_SHOT_LIFECYCLE_REPAIR_EVIDENCE.md).
