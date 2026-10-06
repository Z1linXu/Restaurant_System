# Production Add-on identity reconciliation — 2026-10-06

PRIMARY_REPAIR_GOAL: one canonical Store Add-on entity for current Admin/POS/Uber
selections, formally referenced by existing item options, with historical snapshots
and item eligibility preserved. EXPECTED_REPAIR_CLASS: HIGH (current prices,
Production data, order creation, cross-Store identity).

## Authority and actual baseline

Owner explicitly authorized Production reconciliation and necessary implementation.
Fresh source baseline `60418f8e8489013366579850f2838573bcc59cd8`; isolated branch
`codex/production-addon-identity-20261006`. Original dirty worktree untouched.
Actual Production and Staging backend before: `758dc5b2111db1b298b6104fac81c07f8aee0e12`,
image `sha256:8399cd393c532b4fd66fa7a50cfa5f30106b156eed0a3fb0fd840ec11db9133b`.
Frontend remains `8c3a9e356f59cd025beff25f7fb1d5a20594e028`; Flyway V37.

Production Store IDs are verified, not inferred from names:

| Store | ID | Catalog before | Existing active ordinary options to link |
| --- | ---: | ---: | ---: |
| St-Denis | 1 | 0 | 66 |
| St-Catherine | 3 | 0 | 66 |
| Chinatown | 2 | 0 | 63 |

ONE_SOURCE_OF_TRUTH_BEFORE = NO in Production (all current ordinary options
unlinked); schema already supported the formal FK. No duplicate assignment per
item/code. No Store Add-on creation/update/reconciliation audit or catalog price
override existed. Owner's current default schedule is applied to this manifest.
`green_onion` already exists on eligible items; no new eligibility is invented.

Canonical entity: `store_addons`, code from `organization_addon_definitions`.
Assignment and retained legacy option entity: `menu_item_options`, with formal
`store_addon_id` + `store_addon_store_id` composite FK and `addon_eligible`.
The option ID is an assignment/history/BOM reference, not a second Add-on identity.

## Exact data decisions

15 Owner-confirmed codes per Store, including green_onion/加葱/Extra Green Onion/$0.
The SQL contains the exact 195 original option rows as compare-before guards.
Existing active options on inactive parent items are linked too, retaining parent
inactivity and existing eligibility. Inactive historical options remain untouched.

- Blank-code Extra Meat -> extra_meat (explicit Owner alias).
- 16/458/838 on braised_beef_noodle, Extra Egg $1.99 -> tea_egg, using Owner's
  previously explicit parent-specific identity confirmation (Staging option370),
  not a fuzzy name guess. IDs and $1.99 retained.
- 1148 extra_beef_tendon -> addon_beef_tendons (Owner-confirmed 牛筋 identity).
- Formal canonical tendon English was verified read-only as addon_beef_tendons.

## Implementation and validation

Current reads resolve the FK into canonical values without dirtying managed JPA
entities. Generic writes cannot edit linked rows or disguise canonical identities.
New selections and direct draft submission reject stale canonical prices or
unavailable assignments without silently repricing. Kitchen Mirror remains
non-financial; its local option amounts remain zero. Submitted history and
versioned printing snapshots are unchanged. Explicit printing aliases remain
code-based; canonical fallback is enabled through the existing service.

Backend verify: 953 tests, 0 failures/errors, 5 optional skips with Add-on and
Uber PostgreSQL suites enabled. Additional final forged-group guard tests pass.
Local SQL rehearsal: actual PostgreSQL schema, exact approved menu configuration
(no customer data), 45 catalog rows/195 references, full protected-row hashes and
eligibility unchanged. Replay rejects CATALOG_ALREADY_CHANGED before writes.
Independent Agent 6: code and SQL ACCEPT; backend-only Stage model assertion fixed
as requested (actual private Stage services=backend, Production=backend+nginx).

## Release/recovery scope

Necessary tooling extension: one bounded backend-only promotion helper, reusing
actual private runtime Compose models, operation locks, fresh custom-format DB
backup plus pg_restore listing, image-label SHA proof and health checks. Only
backend image changes. No schema, frontend, proxy, env, printer or device mutation.
Production must reuse the accepted Staging image. Runtime artifacts and backups
are protected; no Docker cache cleanup is required at 69% disk/18GB free.

SQL uses one repeatable-read transaction, exact Store/code/row guards, current
option-only mutation, audited Owner decision, menu revision increment, and full
in-transaction protected-data/eligibility checks. Failure rolls back. Application
rollback restores previous V37-compatible backend; materialized option values
remain compatible. Never restore the full database over subsequent orders.

Deployment and final runtime read-back are pending at this evidence write.
