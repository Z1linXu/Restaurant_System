# Feature Backlog

> Status: `ACTIVE_GOVERNANCE_BACKLOG`
>
> Last updated: 2026-08-07, America/Toronto
>
> Features are not incidents. A feature may be requirements-confirmed without
> being authorized for implementation or production provisioning.

## FT-001 - Owner Store Onboarding - Chinatown

| Field | Value |
|---|---|
| feature_id | `FT-001` |
| title | Owner Store Onboarding - Chinatown |
| priority | `HIGH` |
| status | `AL-003_STAGING_PREFLIGHT_REPAIR_WAITING_FOR_OWNER_REVIEW` |
| target_loop | `AL-003` |
| implementation status | PR-A through PR-F and PR #58 evidence are in `main`. The bounded repair now validates the initialized PostgreSQL private leaf without entering or weakening it and includes focused regressions; the repair is review-only and has not changed Staging. |
| authority | [AL-003A final menu comparison](agile/AL-003A_FINAL_MENU_COMPARISON.md) and [AL-003 technical plan](agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md) |
| next action | Owner reviews the private-leaf repair. After merge, deployment requires a new exact SHA and fresh approval; Staging acceptance separately remains `AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`. |

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
| Private-leaf preflight repair | `IMPLEMENTED_IN_WORKTREE`; Draft PR is the next gate |

The Owner-login acceptance prerequisite is not satisfied by repository code or
deployment alone. STG-005A has not run on the evidenced Staging runtime and its
reviewed scope creates only the synthetic source topology. Before AL-003 can be
accepted, separate Owner-approved synthetic runtime evidence must prove target
Store access, safe Owner login, and authenticated validate/execute calls. No
Production credential, raw SQL membership insert, authorization bypass, or
real business data may supply that topology.

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

### Explicit non-goals

- No automatic failed-job reprint or background daemon.
- No credential, token, printer endpoint, or production data in source control.
- No automatic production deployment, initialization, restore, `docker compose
  down -v`, or data deletion.
- Future single-store first-login auto pairing is not in the first FT-001
  implementation batch.
