# AL-001 Owner Store Onboarding / Chinatown Requirements and Technical Plan

> Loop ID: `AL-001`
>
> Type: `FEATURE_DISCOVERY_AND_PLAN`
>
> Status: `REQUIREMENTS_CONFIRMED`; `READY_FOR_TECHNICAL_PLAN`
>
> Last updated: 2026-07-27, America/Toronto
>
> Boundary: read-only repository discovery and planning only. This document does
> not authorize creating Chinatown, accounts, credentials, devices, printer
> configuration, migrations, or production data.

## 1. Decision summary

`FT-001` is feasible, but the current application **does not yet provide a
safe, reusable owner-scoped Store onboarding workflow**. Existing Platform
Admin creation and template APIs are useful reference points only; they are not
safe to use for Chinatown provisioning because they are globally authorized and
do not clone the current live menu or provision identity/membership safely.

The implementation must add a dedicated owner-onboarding capability with:

1. owner-to-organization authorization enforced on the target Organization;
2. durable idempotency and a single transactional provisioning workflow;
3. live source-Store menu cloning with new IDs and explicit exclusion rules;
4. explicit staff credential plus membership provisioning without source-control
   secrets;
5. an explicit per-Store print-module policy so Chinatown never creates a
   HOT_KITCHEN job merely because an assignment is absent;
6. on-site-only printer endpoints and explicit device pairing after Store setup.

## 2. Requirements and non-negotiable boundary

### 2.1 Confirmed Chinatown request

- Source Store: St-Denis, selected by an owner at provisioning time from the
  same Organization, not hard-coded by ID.
- Target Store: `Chinatown`, suggested code `CHINATOWN`, enabled/active only
  after the provisioned configuration passes the field readiness checklist.
- Owner view: individual St-Denis, individual Chinatown, and organization-wide
  `All Stores` aggregation.
- Employee accounts: `staffCT1` (`MANAGER`) and `staffCT2`, `staffCT3`,
  `staffCT4` (`FRONTDESK`), each scoped to Chinatown only.
- Passwords: an owner supplies each initial password once at runtime over the
  approved secured workflow. The service stores BCrypt hashes only. No raw
  password belongs in Git, migrations, seeders, documentation, logs, audit
  metadata, test fixtures, or API responses.
- Menu source: current production St-Denis rows at actual provisioning time.
  It cannot be reconstructed from `RuntimeDataSeeder`, `menuImportSeed`, a
  template JSON field, or this document.
- Exclusions: no WOK station/category/items/four Chow Mein SKU set/KDS or
  printer assignments. No historical orders, analytics summaries, print jobs,
  or stock balances are copied.
- Tables: begin blank; use the normal Chinatown-scoped table UI afterward.
- Printing: PAD_DIRECT, exactly GRAB and FRONTDESK_RECEIPT modules; two physical
  printers are configured on site. No endpoint, device token, or credential is
  part of this plan or repository.
- Pads: four independent device pairings bound to Chinatown only. Any paired
  Chinatown Pad can atomically claim Chinatown GRAB/receipt jobs.

### 2.2 Non-goals for this loop

- No creation of real production data or staff accounts.
- No automatic first-login device pairing; that is a later loop for `KI-007`.
- No implementation of payment, refund, completeOrder, KDS lifecycle, automatic
  reprint, background daemon, or inventory migration.
- No change to St-Denis data or behavior.

## 3. Read-only executable evidence

| Domain | Existing capability | Evidence | AL-001 conclusion |
|---|---|---|---|
| Store access | Active `store_membership` is checked before the legacy fallback. Owners can access Stores in active `organization_membership`; legacy `users.store_id` is used only when no active membership exists. | `StoreAccessService.java:40-106`; `StoreAccessServiceTest.java:45-108` | Reuse this model. Create Chinatown memberships only and ensure legacy store values cannot expose St-Denis. |
| Login/workspace routing | Login creates its initial user response from legacy `users.store_id`; `/api/v1/me/workspaces` exposes accessible Stores. Frontend sends a multi-Store owner to `/owner/dashboard` and a single Store role to its workspace. | `AuthServiceImpl.java:198-244`; `WorkspaceController.java`; `frontend/src/features/store/storeRoutes.ts:34-65` | Staff can have direct single-Store entry, but onboarding must set a safe legacy default plus explicit membership. |
| Owner overview | Owner overview already builds summaries across `accessibleStores`. The current dashboard Store selector is store-specific; an explicit tested `All Stores` choice still needs UI/API verification. | `OwnerOverviewServiceImpl.java:52-154`; `OwnerAdminDashboardPage.tsx:193-264` | Reuse organization-aware summaries; add an explicit owner-only All Stores selection if current analytics UI cannot represent it safely. |
| Platform Store creation | `POST /api/v1/admin/platform/stores` and `/stores/from-template` require global `ADMIN_STORE_CONFIG`, not an owner-organizational check. | `PlatformAdminController.java:106-133` | Do not expose this path as owner onboarding. |
| Template cloning | `createStoreFromTemplate` copies only stations, dining tables, menu categories, and KDS config JSON. | `PlatformAdminServiceImpl.java:173-196`, `434-520`; `RestaurantTemplate.java:34-50` | Insufficient for live menu clone and violates blank-table Chinatown requirement. |
| Menu data | Items are Store/category/station scoped; options include code/group/parent/sort/price/active data. Menu revisions exist per Store. | `MenuItem.java`; `MenuItemOption.java`; `V2__add_versioned_menu_revision.sql`; `PlatformAdminServiceImpl.java:294-369` | A new clone service can preserve stable fields while creating a new ID map and fresh target revision. |
| Staff creation | Platform `saveUser` saves a legacy User only. It neither hashes a credential nor creates Store/Organization membership. | `PlatformAdminServiceImpl.java:403-415`; `UserCredential.java`; `PasswordServiceImpl.java` | Add a dedicated provisioning path; never use Seeder/default account behavior. |
| Device pairing | Registration uses an authenticated Store-scoped API; a device persists `organizationId` and `storeId`. Runtime PAD calls authenticate the device token before listing/claiming jobs. | `StoreDeviceController.java:41-112`; `StoreDeviceServiceImpl.java:42-86`; `PadPrintingController.java:42-121`; `PadPrintJobServiceImpl.java:61-114` | Pairing is correctly separate from staff login. Reuse store binding; do not pre-generate token records. |
| PAD duplicate protection | Pending jobs are listed for the authenticated device Store and claim uses an atomic repository update. | `PadPrintJobServiceImplTest.java:52-94`; `PrintJobRepository.java` | Multiple Chinatown Pads may process the same module safely, subject to existing atomic claim rules. |
| Printer modules | Existing PAD payload tests cover GRAB, FRONTDESK_RECEIPT, and HOT_KITCHEN. Current assignment lookup can produce an assignment-missing problem if a dispatched module has no policy/assignment. | `PadPrintJobServiceImplTest.java:178-206`; `PrintDispatcherServiceImpl.java`; `PrintModuleCode.java` | A target Store needs an explicit enabled-module policy before dispatcher job creation, not just absent HOT_KITCHEN assignment. |

## 4. Why the existing template cannot meet the live-clone requirement

The template request only has `organization_id`, `name`, `code`, `status`,
`enable_bar_kitchen_tasks`, and `template_id`
(`CreateStoreFromTemplateRequest.java:3-10`). Its service method creates the
Store and then only deserializes template JSON for stations, dining tables,
categories, and KDS configuration. It does not read a source Store's current
`menu_items` or `menu_item_options`, build old-to-new IDs, copy option parents,
or retain current cost/price/ordering/availability metadata.

It also cannot meet Chinatown requirements because it copies tables, does not
provision UserCredential or memberships, does not create a Store-scoped printing
policy, and has no idempotency record. A static template or Seeder would be
stale at the exact moment production administrators alter the St-Denis menu.

## 5. Proposed owner-onboarding architecture

### 5.1 Owner-scoped API and UI

Add a dedicated owner-facing endpoint and workflow, conceptually:

`POST /api/v1/owner/organizations/{organizationId}/stores/onboard`

The exact path/DTO names remain an implementation decision, but the service
must:

1. require an authenticated owner capability;
2. verify active membership in `organizationId` through `StoreAccessService`,
   not merely `ADMIN_STORE_CONFIG`;
3. verify that the selected source Store belongs to the same Organization;
4. validate a unique target Store code and all required scope selections;
5. accept an idempotency key owned by the target Organization;
6. return a redacted onboarding result that contains no password, token,
   printer endpoint, or raw device data.

The owner UI belongs under Owner Home/Store management, not Platform Admin. It
must make source Store, WOK exclusion, tables blank, staff role/scope, printing
setup pending, and idempotency state visible before submission. The existing
Platform Admin `Create Store From Template` form is not reusable as the final
owner UI because it exposes arbitrary organizations/templates and does not
represent the required data contract.

### 5.2 Transaction and idempotency

Provision in one service-owned transaction, using a client-generated request
key and a durable server record. Same Organization + same idempotency key must:

- return the completed/known onboarding result when the normalized request is
  the same;
- return a conflict when the request differs;
- never create a second Store, duplicate accounts, or duplicate menu clone.

Validation happens before writes. After validation, clone rows inside one
transaction using a repeatable source snapshot. On a clone failure, roll back
the incomplete work. If an operational process intentionally retains a failed
request or a committed but unusable Store, mark it `INACTIVE` with a safe error
code rather than automatically deleting data. No rollback path deletes
production business data.

### 5.3 Live menu clone algorithm

The clone service reads source data from the database at execution time:

1. Read current source stations and categories in their stored order.
2. Exclude WOK station, `FRIED_NOODLE`, and the four specifically named Chow
   Mein SKU rows before generating target maps.
3. Create target station and category records, retaining stable code/name/sort
   values and mapping source IDs to target IDs.
4. Read active source menu items and skip any excluded category/station/SKU.
   Create target items with new IDs, copied SKU/name/type/base price/cost/sort
   and stable kitchen fields; force `is_active=true`, `is_sold_out=false`.
5. Read options only for cloned source items. Create target options with new IDs
   while retaining option type, code, group, names, price delta, sort, active
   state, and parent relations. Resolve `parent_option_id` in a second pass from
   the source-to-target option map.
6. Add the Chinatown-only Small size to the three approved SKUs only. Current
   code uses size-related option types and stable option codes such as
   `size_regular`/`size_large`; AL-003 must determine the live source convention
   before choosing the new code. Its price is 13.99 only for Chinatown.
7. Set/increment the target Store menu revision once the clone commits. Do not
   mutate the source Store menu revision or values.

The service copies no order, order item, kitchen task, sales summary, inventory
balance, print job, temporary sold-out state, table, or device data.

### 5.4 Staff identity and Store membership

Create User, UserCredential, and StoreMembership as one onboarding unit:

- Resolve the existing `MANAGER`/`FRONTDESK` role IDs and role codes.
- Create a User whose legacy `store_id` is Chinatown (or null only after login
  routing is explicitly proven), then create a BCrypt `UserCredential` with
  the exact required case-sensitive login identifier.
- Create one active Chinatown StoreMembership per staff account with matching
  organization and role data. Do not create a St-Denis StoreMembership or
  OrganizationMembership for these staff accounts.
- Do not log, audit, serialize, or persist the supplied raw password beyond the
  request lifetime. `PasswordServiceImpl` already uses `BCryptPasswordEncoder`.
- Test direct St-Denis API and URL attempts for 403, and test that workspace
  enumeration contains only Chinatown.

### 5.5 Printing policy and PAD isolation

Current StoreDevice pairing is **device identity**, not staff membership:

- A staff login authenticates a human through `UserCredential` and receives
  workspace access according to membership.
- A paired Pad uses `StoreDevice` identity plus a one-time device token and is
  authenticated by `X-Device-Id`/`X-Device-Token` at runtime.
- The device record stores a Store ID, and PAD pending/claim operations compare
  the device Store against the requested/job Store. This is the required
  execution boundary; a URL Store argument cannot override it.

For Chinatown, create an explicit Store-scoped policy that dispatches only
`GRAB` and `FRONTDESK_RECEIPT` before printer assignment lookup. A missing
HOT_KITCHEN assignment is not a policy: it risks a failed/attention job after a
module is already selected. The design must prevent HOT_KITCHEN job creation
for Chinatown, including fried and combo-egg paths, while retaining existing
St-Denis behavior.

Create no fake device token during onboarding. Each physical Pad is explicitly
paired after the Store exists and Print Center is configured. The future
single-Store first-login auto-registration idea requires a separate reviewed
flow with explicit consent and remains outside the first implementation batch.

### 5.6 Store defaults, tables, and analytics

The current `Store` entity contains organization, code, status, printing, and
menu revision fields, but not a documented per-Store timezone/tax/language/base
receipt-default bundle. AL-002 must locate any current authoritative setting or
add the smallest explicit Store configuration model needed to copy those source
defaults. It must not invent values from frontend/static data.

Tables are deliberately not cloned. Existing Store-scoped table APIs/UI should
be reused after Chinatown creation. Owner analytics already aggregates
accessible Stores by Organization; AL-004 must expose and test an explicit
`All Stores` owner view and preserve Store-specific reports.

## 6. Data and migration assessment

No migration is created in AL-001. A migration is **anticipated** for a safe
implementation because the current schema has no durable owner-onboarding
idempotency/request record and no explicit per-Store print-module enable policy.
It may also need a minimal Store-default configuration if no existing
authoritative configuration is discovered.

The exact migration names, columns, and foreign-key/index design are an
AL-002 decision. Required design properties are:

- unique organization-scoped idempotency key;
- redacted request fingerprint/state/error metadata only, never raw passwords
  or device tokens;
- Store-scoped module policy that prevents disabled-module job creation;
- forward-compatible upgrade and rollback behavior; no destructive data change.

Existing tables that should be reused where possible include `stores`,
`stations`, `menu_categories`, `menu_items`, `menu_item_options`, `users`,
`user_credentials`, `store_memberships`, `printer_configs`,
`printer_assignments`, `store_devices`, and dining-table entities.

## 7. Test matrix

### Backend

1. Owner with active membership in the source Organization can onboard one
   Store; owner of another Organization receives 403.
2. Global admin behavior remains explicitly tested and does not become an
   accidental owner bypass.
3. Same idempotency key + same normalized request returns one Store/result;
   same key + changed request returns conflict; concurrent requests create one
   Store only.
4. Live clone maps categories, stations, items, options, parent options,
   ordering, prices, costs, combo metadata, and new IDs correctly.
5. Source changes after a static template was made are reflected by the live
   source clone fixture, proving the service does not use seed/template data.
6. WOK category/station/four Chow Mein SKUs/options/tasks/assignments are absent
   in Chinatown and unchanged in St-Denis.
7. The three Small size options have 13.99 in Chinatown only; copied Medium and
   Large values match source; St-Denis values remain unchanged.
8. Staff have exactly one active Chinatown membership; direct St-Denis API
   checks return 403; login/workspace default routes are correct.
9. No plaintext password appears in response, exception, audit, persisted
   onboarding record, or test log; credential hashes use BCrypt.
10. Chinatown generates GRAB/FRONTDESK_RECEIPT only. HOT_KITCHEN is not created
    for fried items or combo eggs; St-Denis HOT_KITCHEN behavior is unchanged.
11. Device from Chinatown cannot list, claim, fetch, complete, fail, or release
    a St-Denis job. Multiple Chinatown devices race safely on one job.

### Frontend and Android

1. Owner sees St-Denis, Chinatown, and an explicit All Stores choice only when
   authorized; staff see their single Chinatown workspace.
2. Owner onboarding form never retains or displays entered passwords after
   submission/error and never shows device tokens or printer endpoints.
3. Chinatown table management is empty initially and remains Store-scoped.
4. Print Center permits on-site assignment of exactly GRAB and receipt printers;
   disabled modules are not offered for the Chinatown policy.
5. Four independently paired Pads show Chinatown scope; each can run the same
   APK and only Chinatown queue jobs are visible.

### Field acceptance

Owner-approved production validation must prove, without recording secrets:

- the existing owner sees both individual stores and All Stores;
- `staffCT1` is manager-only for Chinatown and `staffCT2`-`staffCT4` are
  frontdesk-only for Chinatown;
- St-Denis remains operational and rejects Chinatown staff access;
- current source menu clone, WOK exclusions, and three Small prices are correct;
- Chinatown table setup begins empty and can be managed by authorized users;
- GRAB and receipt print from any paired Chinatown Pad; no HOT_KITCHEN job is
  created for Chinatown fried/combo-egg scenarios;
- two Pads racing the same job produce one claim/print lifecycle;
- source Store menu/order/printing data is unchanged.

## 8. Recommended implementation PR split

| Proposed loop/PR | Scope | Migration expectation | Exit condition |
|---|---|---|---|
| `AL-002` | Owner-scoped backend onboarding authorization, durable request idempotency, membership/credential provisioning, safe Store defaults. | Likely yes. | Security, idempotency, credential-redaction, and Store-isolation tests pass. |
| `AL-003` | Live menu clone service, WOK exclusion, Chinatown Small-size override, Store print-module policy. | Likely yes for module policy and possibly Store defaults. | Clone mapping and no-HOT_KITCHEN tests pass; source Store remains unchanged. |
| `AL-004` | Owner onboarding UI, owner All Stores UX, staff/table setup UX, Print Center setup constraints. | Prefer no new migration beyond approved prior work. | Owner/staff UI and Store-scoped behavior tests pass. |
| `AL-005` | Owner-approved production provisioning and field verification runbook. | No unreviewed schema change. | Owner creates Chinatown at runtime, configures printers and pairs Pads, then records acceptance evidence. |
| Future pairing loop | Explicit-consent, single-Store first-login auto-pairing for `KI-007`. | To be decided separately. | Threat model and cross-store tests pass. |

No PR in this table is automatically authorized by the presence of this plan.

## 9. Production preparation, rollback, and approvals

### Before an owner-approved production run

1. Confirm runtime branch/commit, Flyway state, recent backup metadata, and
   approved target PRs through the current runtime planbook.
2. Take an owner-approved backup using the normal operations process; do not
   write backup contents into Git.
3. Verify no source/target Store ID, account password, device token, or printer
   endpoint is committed or logged.
4. Review the source St-Denis menu live at execution time, then execute exactly
   one owner-approved idempotency request.
5. Configure physical printer endpoints on site in Print Center, explicitly
   pair each Pad, and confirm its Store binding before any real order.

### Safe rollback principles

- Code rollback requires owner approval and migration compatibility review.
- Do not use automatic deletion of a failed Store. If a committed provisioning
  result is not fit for service, mark it `INACTIVE`, retain safe audit state,
  and investigate.
- Do not restore production, run `docker compose down -v`, remove volumes, or
  run a destructive Flyway action as onboarding rollback.
- Do not alter St-Denis as part of Chinatown rollback.

### Owner approval is required for

- entering `IMPLEMENT`, PR merge, deployment, migration execution, or
  production onboarding;
- all passwords, printer endpoints, physical device pairings, and account
  creation in production;
- database backup/recovery or any corrective data operation;
- print tests or any print-job state transition.

## 10. AL-001 entry and exit conditions

### Entry conditions met

- `FT-001` requirements are recorded as confirmed.
- Current production baseline is available in the Alive Runtime Planbook.
- Repository discovery identified current authorization, menu, pairing, and
  printing constraints without production mutation.

### Exit conditions for AL-001

- This technical plan is reviewed by the owner.
- The owner explicitly selects the first implementation slice (`AL-002` or a
  smaller safe split) and approves its scope.
- No production Store/account/device/menu/printer data has been created by
  AL-001.
- New implementation work starts only on an independent branch and follows
  [AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md).

## 11. Open design decisions requiring owner approval

1. The authoritative source of per-Store timezone, tax, language, and base
   receipt defaults if no reusable current Store configuration exists.
2. Whether production onboarding uses one owner form with a secure one-time
   credential step or a separate credentials action after Store creation.
3. The exact All Stores owner-dashboard interaction and its analytics API shape.
4. The final persistent representation of disabled print modules and its
   migration/rollback plan.
5. The production sequencing: create Store, configure printers, pair Pads, and
   activate operational use only after the field checklist passes.

## 12. Non-modification statement

This plan was produced from local repository and previously recorded governance
evidence. It did not connect to production, create a Store, user, credential,
printer, device, menu clone, order, print job, migration, or deployment.
