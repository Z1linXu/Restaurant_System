# Feature Backlog

> Status: `ACTIVE_GOVERNANCE_BACKLOG`
>
> Last updated: 2026-08-08, America/Toronto
>
> Features are not incidents. A feature may be requirements-confirmed without
> being authorized for implementation or production provisioning.

## FT-001 - Owner Store Onboarding - Chinatown

| Field | Value |
|---|---|
| feature_id | `FT-001` |
| title | Owner Store Onboarding - Chinatown |
| priority | `HIGH` |
| status | `STG-008_DEPENDENCY_REPAIR_IN_MAIN_WAITING_FOR_EXACT_SHA_STAGING_REBIND_AND_BLOCKED_STATE_RECOVERY_OWNER_RUNTIME_APPROVAL` |
| target_loop | `STG-008_SYNTHETIC_TOPOLOGY_AND_SOURCE`; PR #85 one-shot/Flyway safety repair is `IN_MAIN`, runtime rebind/recovery remains Owner-gated |
| implementation status | PR-A through PR-F, PRs #58-#85, and independent #66 are `IN_MAIN`. Exact `2837ae88...` remains deployed to isolated Staging at Flyway V10 and `STG-007=PASS`; PRs #83/#84 were documentation only, while PR #85 is the bounded backend safety repair and is not deployed. The Owner approved `STG005_OWNER_20260808_R01` without lowering the password guard. Fresh readiness passed, but the first password-free `bootstrap-plan` one-shot stopped before the command/data path because the older cloud safety guard rejected its required Flyway-disabled profile. Cleanup succeeded, topology stayed empty, Production continuity stayed unchanged, and fail-closed state was retained. |
| authority | [AL-003A final menu comparison](agile/AL-003A_FINAL_MENU_COMPARISON.md), [AL-003 technical plan](agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md), [STG-008 entry evidence](runtime/STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md), and [STG-008 Flyway guard repair evidence](runtime/STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md) |
| next action | Request a new Owner-approved latest-exact-main Staging release/preflight/deploy containing PR #85 plus separately approved blocked-state recovery; then restart STG-008 with fresh readiness/approvals. Do not request the runtime-only password before that gate, patch the old image, or begin source-menu/login/onboarding/clone/Production work. |

### Current AL-003 delivery state

| Package | State |
|---|---|
| PR-A | `IN_MAIN` |
| PR-B | `IN_MAIN` |
| PR-B2 | `IN_MAIN` |
| PR-B3 | `IN_MAIN` |
| PR-B4 | `IN_MAIN` |
| PR-C | `IN_MAIN` |
| PR-D | `IN_MAIN` via PR #52 |
| PR-E | `IN_MAIN` via PR #54 |
| PR-F0 | `IN_MAIN` via PR #55 |
| PR-F | `IN_MAIN` via PR #56 |
| PR #58 attempt evidence | `IN_MAIN` |
| Private-leaf preflight repair / PR #59 | `IN_MAIN` via merge `c3956592da8a33092ab745c7cc6aac05e9babfa7` |
| Owner decisions governance sync / PR #60 | `IN_MAIN` at `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`; documentation only, no runtime action |
| Current project handoff / PR #71 | `IN_MAIN` at `5baada03935e004d80af1e7a36fb7db39bd6abbb`; navigation only, no runtime action |
| Modular architecture / PR #61 | `IN_MAIN` at `bbb1af9520c188b6ef6362e783284ba4001a7e63`; Generic Store Provisioning Engine + Versioned Store Profiles + Reusable Provisioning Modules, architecture only |
| STG-005B Synthetic St-Denis baseline / PR #62 | `IN_MAIN` at `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b`; guarded, versioned, transactional source baseline; no runtime execution implied |
| AL-003S Staging acceptance preparation / PR #63 | `IN_MAIN` at `732d77c89ff067982702426ff918d5e097e1d0fb`; guarded launcher, passive evidence, approval/identity binding, immutable image pin, command plan, acceptance template, and rollback boundary only; no runtime action |
| AL-004 Generic Store Profile contract / PR #64 | `IN_MAIN` at `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6`; declarative version/profile identity, module-reference, canonical fingerprint, and safe-summary contract only; no concrete profile, API, migration, UI, or provisioning execution |
| AL-005A Staff/Table plan / PR #65 | `IN_MAIN` at `8f58bcbfca253c1598b967f4d17c04c0be1cce5b`; planning only; no writer, endpoint, migration, credential, table, or runtime execution |
| Printer Store-isolation repair / PR #66 | `IN_MAIN` at `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3`; rejects cross-Store printer config updates, cross-Store automatic dispatch, and PAD_DIRECT printer-health updates; no migration, endpoint shape, transport, Android, or runtime action |
| AL-005 Printing plan / PR #67 | `IN_MAIN` at `65e3d3ced2b5b05eb36d56ce67e475768ad19dff` | Reusable Store-scoped Printing Provisioning planning only; no writer, endpoint, migration, printer, assignment, mode change, test print, or runtime mutation |
| AL-005B Device/Pad plan / PR #68 | `IN_MAIN` at `9e93573be97cfd01a9ad3efe64d55827854c497a` | Single-layer reusable Device/Pad Provisioning plan; no pairing, token, Worker, endpoint, migration, or runtime mutation |
| AL-006 Activation plan / PR #69 | `IN_MAIN` at `dc682203b2b24bbdb453a5520b297b9051139f13` | Fail-closed workflow plan only; lifecycle and validator are conceptual; no status transition or activation writer |
| REL-001 Production RC plan / PR #70 | `IN_MAIN` at `645d4909625f70fc241d5468382d66a30a030fb1` | Exact-SHA release gates only; no selected candidate, Staging pass, Production deploy, or activation action |
| Post-stack Ground Truth audit / PR #72 | `IN_MAIN` at `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | Capability/runtime gap audit only; no deployment or acceptance |
| STG-006 evidence/governance / PR #73 | `IN_MAIN` at `85d97b7327b2e15aa561ed28a5788b92cedf6f5b` | Passive evidence only; STG-006 PASS, no deployment or mutation |
| OPS-001 secret-safe tooling | `REPOSITORY_COMPLETE` through PR #74 plus control-path repairs #75-#78 | Detached release/env rotation, runtime/Flyway restart evidence, and secret-FD Owner/API helpers; runtime actions remain separately gated |
| Readiness health fingerprint repair / PR #80 | `IN_MAIN` at `39fa284b7bccd64d650c396f2c7532b0a0858b4b` | Missing optional health is classified as `NO_HEALTHCHECK`; present-invalid and unhealthy remain fail-closed |
| Flyway success-token repair / PR #81 | `IN_MAIN` at `63600b13b10a5549d9095a03c94e69a9f880af9f` | PostgreSQL `success::text=true` is accepted exactly; false/abbreviated/invalid history remains fail-closed |
| Restart readiness/fail-closed repair / PR #82 | `IN_MAIN` at `2837ae88e55142c99c6975f8b6575febffc913a1` | Bounded three-endpoint readiness and nonzero-exit blocked-state persistence; exact merged SHA later passed STG-007 |
| STG-007 final evidence/governance / PR #83 | `IN_MAIN` at `2ed56b06f37c9257a655ec334f81e31ca4a518a6` | Documentation/evidence only; no runtime-capability or runtime-state change |
| STG-008 entry evidence/governance / PR #84 | `IN_MAIN` at `828af4e84581dcb051248beee694c307a65210c5` | Sanitized credential-gate entry evidence only; no application, migration, runtime configuration, credential, or data mutation |
| STG-008 guarded one-shot Flyway safety repair / PR #85 | `IN_MAIN` at `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6` | Exact no-migration synthetic one-shot startup reconciliation plus tests/governance; not deployed, no migration, credential, or data mutation |

The Owner-login acceptance prerequisite is not satisfied by repository code or
deployment alone. Read-only code audit confirms that an active Organization
`OWNER` membership already grants access to every Store in that Organization;
the onboarding flow therefore does not need to create a redundant target-Store
membership for the Owner. STG-005A and onboarding can establish the required
identity/access topology, but they have not run on the evidenced Staging
runtime. STG-008 entry evidence now proves the account/topology rows are
absent and Store ID `1` remains safely allocatable; it also records the
historical credential-contract `NO_GO`. The Owner has now aligned that contract,
but the fresh password-free plan exposed the bounded cloud/Flyway safety-rule
conflict before the command path. Separate Owner-approved exact-SHA recovery
and runtime evidence must still prove the synthetic credential, login,
workspace access, target onboarding, and
authenticated validate/execute calls. No Production credential, raw SQL,
authorization bypass, or real business data may supply that evidence.

The AL-003S preparation closes the reviewed non-web launcher gap for
STG-005A/STG-005B. STG-006 freshly collected passive resource, isolation, and
Staging/Production-continuity evidence. Secret-safe release/env rotation,
sanitized Flyway collection and valid same-image restart now have runtime
evidence. Synthetic topology/source and secret-safe Owner/API calls remain
explicit, separately approved prerequisites rather than inferred capability.

`MERGED_ON_GITHUB` is not sufficient evidence for `IN_MAIN` when a PR's base
is another feature branch. Each stacked layer requires a latest-`main`
promotion, fresh verification, and Owner review in dependency order.

### Goal

Provide a reusable, owner-scoped Store onboarding capability. Chinatown is the
first approved onboarding request, not a hard-coded special case. The existing
Owner must be able to view St-Denis, Chinatown, and organization-wide `All
Stores` data while never creating a Store in an organization they do not own.

### Confirmed business definition

#### Organization and Store

- Chinatown and St-Denis belong to the same Organization.
- Create Store `Chinatown`, suggested code `CHINATOWN`, status `ACTIVE` after
  provisioning acceptance.
- Timezone, tax, language, and base receipt defaults inherit from the selected
  source Store. The present Store model does not yet prove a single per-store
  representation for all four values, so the implementation must identify and
  persist the authoritative existing configuration before activation.
- Start with zero sales. Do not copy orders, sales, analytics summaries, print
  jobs, or inventory balances.

#### Accounts and membership isolation

Create these exact runtime login identifiers only after owner approval:

| Login identifier | Role | Store scope | Initial destination |
|---|---|---|---|
| `staffCT1` | `MANAGER` | Chinatown only | Chinatown administration/dashboard |
| `staffCT2` | `FRONTDESK` | Chinatown only | Chinatown frontdesk |
| `staffCT3` | `FRONTDESK` | Chinatown only | Chinatown frontdesk |
| `staffCT4` | `FRONTDESK` | Chinatown only | Chinatown frontdesk |

- No email is required.
- The initial password is an owner-approved, one-time runtime input. It must be
  BCrypt-hashed and must never be written to Git, migrations, seeders,
  documentation, logs, API responses, or audit metadata.
- Each account receives only an active Chinatown `store_membership`; no
  St-Denis membership may be created. The legacy `users.store_id` must be
  Chinatown or null and cannot grant another Store through fallback behavior.
- Direct St-Denis URLs and APIs must return 403 for these users.

#### Live menu clone

At an independently approved execution phase, clone from the current live menu
of St-Denis, Store ID `1`. `RuntimeDataSeeder`, `menuImportSeed.ts`, and other
repository seed data are historical reference only and must never supply clone
rows or fill missing live data.

The target profile is `CHINATOWN_MENU_2026_02_02`. It creates only
`SOUP_NOODLE`, `DRY_NOODLE`, `SIDE_DISHES`, and `DRINK`, in that order, and
uses the Chinatown PDF prices instead of the superseded Small-13.99 backlog
rule. Dry noodles are ordered Dan Dan then Zha Jiang. Side dishes are ordered
Braised Beef Shank, Spicy Cucumber, Edamame, Seaweed Potato, Sichuan Pepper
Chicken, then Tea Egg.

The new target SKUs are `sichuan_pepper_chicken`, `tea_egg`, `seven_up`, and
`ginger_ale`. Combo 1-4 apply only to their mapped main dishes; Combo 3 includes
a side and tea egg. All five target noodles receive all seven noodle types, and
all active Store 1 add/remove options for reused items are preserved. Tea egg
exists as both a standalone target item and an add-on option. No automatic
schedule or French localization is added.

St-Denis remains unchanged. AL-003 does not clone printers, printer
assignments, devices, staff, tables, orders, payments, credentials, inventory,
analytics, KDS configuration, or production data.

#### Tables

Do not clone tables. Chinatown begins with blank table setup. Its owner/manager
uses the existing table UI to create, edit, split, combine, and change status
within Chinatown only.

#### Printing and Pads

- AL-003 neither clones nor configures printing or Pads. The target remains
  printing-disabled until a separate Owner-approved provisioning loop.
- Any future Chinatown printer endpoint remains on-site runtime configuration
  and must never enter Git or the clone request/evidence record.
- Future Pad pairing, printer module-to-printer assignments, and physical print
  acceptance are separate from the menu-clone transaction and cannot be
  inferred from PR-A. There is no per-device module assignment.

#### Acceptance boundaries

AL-003 is accepted only after the exact Store 1 live-source validation, target
mapping, PDF price/size/Combo rules, transaction rollback, idempotency,
Organization isolation, source invariance, and explicit side-effect exclusions
pass. The broader FT-001 feature still requires separately approved table,
printing, Pad, UI, and field acceptance; it is never accepted by creating a
seed/demo Store.

### Owner decisions effective 2026-08-08

- Chinatown is the second planned real Production Store. FT-001 closes only at
  `Production-ready Chinatown Store`, not at Store creation, API completion, or
  a Staging demonstration.
- The reviewed `CHINATOWN_MENU_2026_02_02` Categories, Stations, 17 items,
  bilingual names, prices, sizes, noodle types, Combo rules, tea egg, extra
  meat, and ordering are frozen as the initial Production target contract.
  Normal post-activation changes use Menu Management and do not expand AL-003.
- Production Store 1 / St-Denis live menu is the only Production clone source.
  Repository seeds and synthetic Staging data are not Production evidence.
- Chinatown's first menu initialization must use the reviewed clone engine:
  create inactive Store, validate, review, execute, verify, then activate.
  Manual Menu Management is not the initial Production provisioning path.
- Organization Owners inherit access to all Stores in their active
  Organization membership. Manager/frontdesk and other Store-scoped staff keep
  explicit target-Store memberships.
- Future Owner UI must offer reviewed versioned menu templates including
  `CHINATOWN_MENU` and a future `ST_DENIS_MENU` profile, both backed by the same
  generic clone/provisioning engine.
- Staging is a persistent Production-like, synthetic-only environment. A
  Synthetic St-Denis baseline must be reproducible without Production
  credentials, database copies, customers, orders, payments, real printers, or
  device secrets.
- Production release strategy is a formal exact-SHA Release Candidate after
  Staging acceptance, Production gap audit, migration review, and
  backup/rollback review. `git pull latest` is not a release process.

### FT-001 completion gap matrix

| Capability | State | Evidence / next boundary |
|---|---|---|
| Generic Owner Organization authorization | `DONE_IN_MAIN` | Active Organization `OWNER` membership grants same-Organization Store access; cross-Organization access remains forbidden. |
| Idempotent inactive Store onboarding | `DONE_IN_MAIN` | AL-002 creates an inactive target and requested Manager/Frontdesk accounts with BCrypt credentials and Store memberships. |
| Generic menu clone transaction, options, replay, locks, API | `DONE_IN_MAIN` | PR-A through PR-F and V10 are repository capability only. |
| Frozen Chinatown Store Profile | `DONE_IN_MAIN` | `CHINATOWN_MENU_2026_02_02` is the approved initial Production target contract. |
| PostgreSQL private-leaf Staging guard | `DONE_IN_MAIN` | PR #59 merged at `c3956592da8a33092ab745c7cc6aac05e9babfa7`; no redeploy is implied. |
| Exact-SHA Staging deployment and Flyway V9/V10 | `STG-007_PASS` / `DEPLOYED_TO_STAGING` | Exact `2837ae88...` passed fresh V10 continuation entry, formal preflight, V10-to-V10 deploy, repaired readiness, runtime collection, same-image restart and post-restart verification. Flyway remains exact V10/no-pending; this is not AL-003 Staging acceptance. |
| Synthetic Organization/source/Owner bootstrap | `STAGING_DEPENDENCY_REPAIR_RUNTIME_GATE` | Owner identity/password contract is aligned. Fresh plan reached one bounded one-shot but failed before the STG-005A command because the old cloud safety guard rejected required Flyway-disabled mode. Cleanup and zero-write evidence passed; blocked state is retained. PR #85 is `IN_MAIN`; before retry, approve a freshly fetched latest exact main containing #85 for Staging release/preflight/deploy and separately approve blocked-state recovery. |
| Synthetic target onboarding and Owner target access | `STAGING_PENDING` | Existing onboarding plus Organization Owner access is sufficient; runtime evidence is missing. |
| Synthetic Owner login/workspace/Owner API authorization | `STAGING_PENDING` | Account is `NOT_CREATED`; a compatible credential must first be Owner-approved and supplied at runtime, never retained in Git/evidence. Login itself remains outside STG-008. |
| Reproducible Synthetic St-Denis source-menu baseline | `IN_MAIN` via PR #62 | Guarded, versioned, transactional empty-or-exact implementation is repository capability only and has not run on Staging. |
| AL-003 validate/execute/replay/restart acceptance | `STAGING_PENDING` | Requires the full synthetic topology and source-menu contract first. |
| STG-006 exact-main passive preflight | `PASS` | At STG-006 capture, candidate was `33c6e3c...` and retained Staging was `4397f995...` / V8; this historical PASS does not override the later STG-007 deployment. |
| OPS-001 secret-safe tooling | `REPOSITORY_COMPLETE` through PR #82; STG-007 runtime evidence `PASS` | Release/env rotation, exact deploy, repaired readiness, sanitized runtime collection and same-image restart passed at `2837ae88...`. No credentials or API action occurred. |
| Production Store 1 read-only source capture/drift review | `PRODUCTION_PENDING` | Separate Owner Runtime Gate; only menu-related evidence may be read. |
| Production Chinatown Store/staff/menu provisioning | `PRODUCTION_PENDING` | Exact-SHA Release Candidate and production approval required. |
| Generic Store Profile identity/composition contract | `IN_MAIN` via PR #64 | Exact versioned identity, module references with reviewed expected fingerprints, activation requirements, canonical fingerprint, and safe summaries are repository capability only; no concrete Store Profile or callable workflow is implied. |
| Owner Create Store / Choose Menu Template UI | `NOT_IMPLEMENTED` | Existing Platform Admin template UI is not the approved Owner workflow. |
| Generic Store Profile contract | `IN_MAIN` via PR #64 | Declarative identity/composition contract only; no Owner UI or provisioning execution. |
| Versioned `ST_DENIS_MENU` profile | `NEEDS_NEW_LOOP` | The strict identity for a complete Store Profile is not finalized; it must reuse the generic profile registry/clone engine with no Store ID 3 branch. |
| Staff/Table provisioning module | `IN_MAIN` via PR #65 | Reusable Staff/Access and Table module planning only; no writer. Chinatown remains blank-table/manual setup; future predefined-table writing requires schema, normalization, ownership, and replay decisions. |
| Printing provisioning module | `IN_MAIN` via PR #67 | Single-layer reusable Store-scoped Printing Provisioning plan; no executable writer or runtime mutation. |
| Device/Pad provisioning module | `IN_MAIN` via PR #68 at `9e93573be97cfd01a9ad3efe64d55827854c497a` | Reusable Store-scoped pairing, binding, readiness, and health planning only; no pairing, token, Worker, endpoint, or runtime mutation. |
| Store activation validation/workflow | `IN_MAIN` via PR #69 | Fail-closed plan only; lifecycle and validator are conceptual; no status transition or activation writer. |
| Chinatown Production Release Candidate | `PLAN_ONLY` via PR #70 `IN_MAIN` | Exact-SHA/migration/backup/rollback/resource/deployment gates only; no selected candidate, Staging pass, Production deploy, or Chinatown activation. |
| Chinatown end-to-end field acceptance | `PRODUCTION_PENDING` | Owner/staff login, dine-in order, update, expected tickets, and operational completion remain required. |

### Proposed bounded loop order

The #61-#70 preparation packages below are complete in main. Their historical
dependency order is retained for traceability; it is not the current execution
queue. The current capability matrix and next executable/implementation loop
order are in [Post-Stack Ground Truth Audit](runtime/POST_STACK_GROUND_TRUTH_AUDIT.md).

1. `STG-005B_SYNTHETIC_ST_DENIS_BASELINE`: define a reviewed, idempotent,
   synthetic-only St-Denis menu/configuration baseline using existing generic
   modules and supported APIs.
2. `AL-003S_STAGING_CLONE_ACCEPTANCE`: exact-SHA deployment, V9/V10,
   STG-005A bootstrap, target onboarding, Owner login/access, source baseline,
   validate, execute, replay, restart, and Production-continuity evidence.
3. `AL-004_GENERIC_STORE_PROFILE_FRAMEWORK`: Owner Create Store UI, versioned
   template selection, and the future `ST_DENIS_MENU` profile without a second
   clone engine.
4. `AL-005A_STAFF_TABLE_PROVISIONING_MODULES`: reusable staff/access and table
   provisioning inputs around the existing onboarding authority and a new
   Store-safe table planner/provisioner boundary. The preparation contract is
   [AL-005A Staff and Table Provisioning Module Plan](agile/AL-005A_STAFF_TABLE_PROVISIONING_MODULE_PLAN.md).
5. `AL-005_PRINTING_PROVISIONING_TEMPLATE`: Store-scoped printer/module
   assignment inputs and safe physical-print acceptance gates. The bounded
   preparation is [AL-005 Printing Provisioning Module Plan](agile/AL-005_PRINTING_PROVISIONING_MODULE_PLAN.md).
6. `AL-005B_DEVICE_PAD_PROVISIONING_MODULE`: reusable Store device binding and
   Pad pairing/worker readiness gates without embedding device secrets. The
   bounded preparation is [AL-005B Device and Pad Provisioning Module Plan](agile/AL-005B_DEVICE_PAD_PROVISIONING_MODULE_PLAN.md).
7. `AL-006_STORE_ACTIVATION_WORKFLOW`: aggregate validation and explicit Store
   activation after all provisioning modules pass. The bounded preparation is
   [AL-006 Store Activation Workflow Plan](agile/AL-006_STORE_ACTIVATION_WORKFLOW_PLAN.md).
8. `REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE`: Store 1 read-only source
   capture, Production gap/migration/backup/rollback review, and exact-SHA
   approval. The bounded preparation is
   [REL-001 Chinatown Production Release Candidate Plan](agile/REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE_PLAN.md).
9. `ACT-001_CHINATOWN_PRODUCTION_ACTIVATION`: execute approved provisioning and
   complete Owner/staff/order/printing/Pad field acceptance.

These names record dependency order only. They do not authorize implementation,
runtime mutation, Production access, or deployment.

Historical short labels are preserved rather than silently reused: the AL-001
plan's `AL-004` UI/configuration scope is split across the current AL-004,
AL-005A, and AL-005 packages, while its historical `AL-005` Production scope
maps to REL-001 and ACT-001. The canonical mapping is maintained in
[STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md).

The shared architecture and anti-hardcode boundary for these loops is
[STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md).

### Explicit non-goals

- No automatic failed-job reprint or background daemon.
- No credential, token, printer endpoint, or production data in source control.
- No automatic production deployment, initialization, restore, `docker compose
  down -v`, or data deletion.
- Future single-store first-login auto pairing is not in the first FT-001
  implementation batch.
