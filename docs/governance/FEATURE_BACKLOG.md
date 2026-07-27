# Feature Backlog

> Status: `ACTIVE_GOVERNANCE_BACKLOG`
>
> Last updated: 2026-07-27, America/Toronto
>
> Features are not incidents. A feature may be requirements-confirmed without
> being authorized for implementation or production provisioning.

## FT-001 - Owner Store Onboarding - Chinatown

| Field | Value |
|---|---|
| feature_id | `FT-001` |
| title | Owner Store Onboarding - Chinatown |
| priority | `HIGH` |
| status | `REQUIREMENTS_CONFIRMED` |
| target_loop | `AL-001` |
| implementation status | Planning only; no Store, account, menu, device, migration, or production data has been created by this feature record. |
| authority | [AL-001 technical plan](agile/AL-001_OWNER_STORE_ONBOARDING_CHINATOWN_TECHNICAL_PLAN.md) |

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

At execution time, clone the **current production** St-Denis menu from the
production database within the approved onboarding transaction. Do not use a
Seeder, static documentation, `menuImportSeed`, or an old snapshot.

Copy active categories, items, options, SKU, option code/group, parent-option
relations, station relation, names, price, cost, ordering, combo metadata, and
stable kitchen metadata to new IDs. Do not copy temporary sold-out state;
copied active items are active and not sold out.

Chinatown excludes WOK entirely: no WOK station, `FRIED_NOODLE` category, WOK
items, four Chow Mein SKUs, WOK tasks, KDS routing, printer assignments, or
WOK-specific flow. St-Denis remains unchanged.

For `traditional_beef_noodle`, `dan_dan_noodle`, and `vegetable_noodle`, add a
Chinatown-only Small size at 13.99. The stable `option_code` convention must be
confirmed from the production source menu during implementation; Medium and
Large remain exactly as copied.

#### Tables

Do not clone tables. Chinatown begins with blank table setup. Its owner/manager
uses the existing table UI to create, edit, split, combine, and change status
within Chinatown only.

#### Printing and Pads

- Chinatown operates in `PAD_DIRECT` with two physical printers configured
  on-site through Print Center: GRAB and FRONTDESK_RECEIPT.
- Only `GRAB` and `FRONTDESK_RECEIPT` are enabled. No `HOT_KITCHEN`,
  `COLD_KITCHEN`, `BAR`, `TAKEOUT_RECEIPT`, or WOK printing is enabled.
- GRAB includes real Chinatown kitchen work from NOODLE, COLD, and DEEPFRIED.
  Fried items and combo eggs must not cause a HOT_KITCHEN failure, cancellation,
  or missing-assignment job.
- Printer IP/port values are on-site runtime configuration and must never enter
  Git.
- Four Pads use the same APK but independent device identities. Each is paired
  to Chinatown only; its pending/claim scope must not cross Store boundaries.
- Any Chinatown Pad may claim GRAB or receipt work. Atomic claim remains the
  duplicate-print protection; at least one Pad must have auto processing enabled.

#### Acceptance boundaries

The feature is accepted only after owner, staff isolation, menu clone, WOK
exclusion, Chinatown-only size price, tables, print routing, PAD device scope,
real-time Store visibility, and St-Denis non-regression checks pass according
to the AL-001 field checklist. It is not accepted by creating a seed/demo Store.

### Explicit non-goals

- No automatic failed-job reprint or background daemon.
- No credential, token, printer endpoint, or production data in source control.
- No automatic production deployment, initialization, restore, `docker compose
  down -v`, or data deletion.
- Future single-store first-login auto pairing is not in the first FT-001
  implementation batch.
