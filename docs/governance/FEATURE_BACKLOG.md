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
| status | `AL-004_STACKED_PROFILE_CONTRACT_WAITING_FOR_OWNER_REVIEW` |
| target_loop | `AL-004_GENERIC_STORE_PROFILE_FRAMEWORK` |
| implementation status | PR-A through PR-F, PR #58 evidence, PR #59's repair, and PR #60's Owner decisions are `IN_MAIN` at `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`. Draft PRs #61-#63 remain dependency-bound and are not in `main`. The stacked AL-004 package adds only a declarative Store-profile contract. Staging remains on the separately evidenced older runtime; no V9/V10/bootstrap/clone/provisioning execution is implied. |
| authority | [AL-003A final menu comparison](agile/AL-003A_FINAL_MENU_COMPARISON.md) and [AL-003 technical plan](agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md) |
| next action | Review Draft PR #61, then dependency-bound Draft PRs #62 and #63, then the stacked AL-004 contract package. Runtime acceptance still needs a fresh exact-SHA request and separate Owner approval after its dependencies enter `main`. |

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
| Owner decisions governance sync / PR #60 | `IN_MAIN` via merge `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`; documentation only, no runtime action |
| Modular Store provisioning architecture / PR #61 | `DRAFT_PR_WAITING_FOR_OWNER_REVIEW`; documentation only, no runtime action |
| STG-005B Synthetic St-Denis baseline / PR #62 | `STACKED_DRAFT_WAITING_FOR_OWNER_REVIEW`; no migration, public API, SSH, runtime write, or clone execution |
| AL-003S Staging acceptance preparation / PR #63 | `STACKED_DRAFT_WAITING_FOR_OWNER_REVIEW_AND_RUNTIME_APPROVAL`; guarded launcher/tests and command/evidence/rollback documents only; no runtime action |
| AL-004 generic Store Profile contract | `STACKED_ONLY_WAITING_FOR_OWNER_REVIEW`; exact identity/version/composition/fingerprint contract only; no concrete profile, API, migration, UI, or provisioning execution |

The Owner-login acceptance prerequisite is not satisfied by repository code or
deployment alone. Read-only code audit confirms that an active Organization
`OWNER` membership already grants access to every Store in that Organization;
the onboarding flow therefore does not need to create a redundant target-Store
membership for the Owner. STG-005A and onboarding can establish the required
identity/access topology, but they have not run on the evidenced Staging
runtime. Separate Owner-approved runtime evidence must still prove the
synthetic credential, login, workspace access, target onboarding, and
authenticated validate/execute calls. No Production credential, raw SQL,
authorization bypass, or real business data may supply that evidence.

The AL-003S preparation closes the reviewed non-web launcher gap for
STG-005A/STG-005B and adds a bounded passive resource plus
Staging/Production-continuity fingerprint collector. Runtime collection remains
unexecuted. Secret-safe release/env rotation, same-image restart/Flyway
evidence, and secret-safe Owner/API calls remain explicit runtime prerequisites
rather than inferred capabilities.

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
- Future Pad pairing, module assignments, and physical print acceptance are
  separate from the menu-clone transaction and cannot be inferred from PR-A.

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
| Exact-SHA Staging deployment and Flyway V9/V10 | `STAGING_PENDING` | Requires fresh merged SHA, preflight evidence, and explicit Owner runtime approval. |
| Synthetic Organization/source/Owner bootstrap | `STAGING_PENDING` | STG-005A is in main but has not executed on evidenced Staging. |
| Synthetic target onboarding and Owner target access | `STAGING_PENDING` | Existing onboarding plus Organization Owner access is sufficient; runtime evidence is missing. |
| Synthetic Owner login/workspace/Owner API authorization | `STAGING_PENDING` | Credential must be supplied at runtime and never retained in Git/evidence. |
| Reproducible Synthetic St-Denis source-menu baseline | `STACKED_DRAFT_PR_62` | A guarded, versioned, transactional empty-or-exact implementation is prepared above Draft PR #61. It is not in `main` and has not run on Staging. |
| AL-003 validate/execute/replay/restart acceptance | `STAGING_PENDING` | Requires the full synthetic topology and source-menu contract first. |
| Production Store 1 read-only source capture/drift review | `PRODUCTION_PENDING` | Separate Owner Runtime Gate; only menu-related evidence may be read. |
| Production Chinatown Store/staff/menu provisioning | `PRODUCTION_PENDING` | Exact-SHA Release Candidate and production approval required. |
| Generic Store Profile identity/composition contract | `STACKED_ONLY` | Exact versioned identity, module references with reviewed expected fingerprints, activation requirements, canonical fingerprint, and safe summaries are under review; no concrete Store Profile or callable workflow is implied. |
| Owner Create Store / Choose Menu Template UI | `NOT_IMPLEMENTED` | Existing Platform Admin template UI is not the approved Owner workflow. |
| Versioned St-Denis profile | `NEEDS_NEW_LOOP` | Historical planning calls the menu-only candidate `ST_DENIS_MENU`; the strict identity for a complete Store Profile is not finalized. It must reuse the generic profile registry/clone engine with no Store ID 3 branch. |
| Table provisioning module | `NEEDS_NEW_LOOP` | Existing Store-scoped table APIs can be reused; activation orchestration is absent. |
| Printing provisioning module | `NEEDS_NEW_LOOP` | No printer/assignment clone; runtime endpoints and physical tests remain separately gated. |
| Device/Pad provisioning module | `NEEDS_NEW_LOOP` | Pairing exists, but Store activation orchestration and Chinatown field evidence do not. |
| Store activation validation/workflow | `NEEDS_NEW_LOOP` | Must gate activation on access, menu, tables, printing, devices, login, and order acceptance. |
| Chinatown end-to-end field acceptance | `PRODUCTION_PENDING` | Owner/staff login, dine-in order, update, expected tickets, and operational completion remain required. |

### Proposed bounded loop order

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
   provisioning inputs around the existing onboarding and table APIs.
5. `AL-005_PRINTING_PROVISIONING_TEMPLATE`: Store-scoped printer/module
   assignment inputs and safe physical-print acceptance gates.
6. `AL-005B_DEVICE_PAD_PROVISIONING_MODULE`: reusable Store device binding and
   Pad pairing/worker readiness gates without embedding device secrets.
7. `AL-006_STORE_ACTIVATION_WORKFLOW`: aggregate validation and explicit Store
   activation after all provisioning modules pass.
8. `REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE`: Store 1 read-only source
   capture, Production gap/migration/backup/rollback review, and exact-SHA
   approval.
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
