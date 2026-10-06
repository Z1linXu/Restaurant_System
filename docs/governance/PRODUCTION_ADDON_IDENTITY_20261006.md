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

PR #272 merged as `ce35278b3569a4eacfa29efc01cadbf0c409bec6`. Application image
`sha256:5b9583ee43ad2ceeb9b47a08a47397cb8802a19f1d89366a0b0b04889ebbaaad`.
First Stage promotion reached healthy startup, then the helper compared Docker
Env lists positionally and automatically rolled back. Environment key/value
comparison is corrected to dictionary equality; no config relaxation or
application rebuild. Production remained untouched during this attempt.
PR #273 merged as `420dbb1268540c3ad5a2d0befa1d9fd96c684bd0` (helper-only fix).
Staging promotion succeeded 2026-10-06 16:35:07 UTC; Production promotion succeeded
16:36:03 UTC. Both run the exact application image/SHA above. SQL committed, then
three-Store read-back passed. Frontend image remains
`sha256:4bf301d4d547cc017be91c1e6d7c0d859cca345d4aed4db1696e32000e7048fc`.

## Final Production read-back

| Store | Catalog | Linked existing options | References on active parent items | Conflicts |
| --- | ---: | ---: | ---: | ---: |
| St-Denis | 15/15 | 66 | 64 | 0 |
| St-Catherine | 15/15 | 66 | 64 | 0 |
| Chinatown | 15/15 | 63 | 39 | 0 |

45 Store-owned catalog entities and 195 existing assignments. Different counts
for active parents reflect existing inactive items, not removed eligibility.
All three catalogs contain the same confirmed active dictionary:

| Code | Chinese | English | Price CAD |
| --- | --- | --- | ---: |
| addon_beef_tendons | 加牛筋 | addon_beef_tendons | 6.99 |
| bok_choy | 加上海青 | Extra Bok Choy | 3.00 |
| broccoli | 加西兰花 | Extra Broccoli | 3.00 |
| carrot_slice | 加胡萝卜片 | Extra Carrot Slice | 3.00 |
| cilantro | 加香菜 | Extra Cilantro | 0.00 |
| corn | 加玉米 | Extra Corn | 3.00 |
| extra_meat | 加肉 | Extra Meat | 6.99 |
| extra_noodle | 加面 | Extra Noodle | 3.99 |
| extra_radish | 加萝卜 | Extra Radish | 3.00 |
| extra_sauce | 加酱 | Extra Sauce | 3.00 |
| fried_egg | 加煎蛋 | Extra Fried Egg | 1.99 |
| green_onion | 加葱 | Extra Green Onion | 0.00 |
| mushroom | 加蘑菇 | Extra Mushroom | 3.00 |
| seaweed | 加海菜 | Extra Seaweed | 3.00 |
| tea_egg | 加卤蛋 | Extra Tea Egg | 1.99 |

Each cell below is `catalog ID ← assignment option ID / menu item ID`.
DB FK, catalog API, item Add-ons API, item Options API and POS menu agree:

| Store | extra_meat | fried_egg | tea_egg | green_onion |
| --- | --- | --- | --- | --- |
| St-Denis | 19 ← 76 / 1 | 31 ← 77 / 1 | 43 ← 75 / 1 | 34 ← 80 / 1 |
| St-Catherine | 21 ← 1030 / 83 | 33 ← 914 / 82 | 45 ← 924 / 82 | 36 ← 1066 / 83 |
| Chinatown | 20 ← 651 / 50 | 32 ← 664 / 56 | 44 ← 640 / 50 | 35 ← 694 / 50 |

ONE_SOURCE_OF_TRUTH_AFTER = YES for current/future configuration. All four
identity checks PASS. Uber canonical resolution PASS by actual PostgreSQL
integration test, including deliberately corrupted old copied option fields;
no real Production Uber traffic was used. Printing uses the same stable canonical
code and frozen canonical order snapshot. Existing deliberate MODIFIER_ADD aliases
remain presentation rules (for example tea_egg `+蛋`, green_onion `+葱`), not
independent names on menu items. Existing versioned reconciliation API enabled
only `formatting.addon_fallback=CANONICAL_SNAPSHOT`; parsed printing-document
comparison proves other rules unchanged. No new order or physical print test.

Transaction-protected before/after hashes PASS for historical orders, options,
print jobs/snapshots, kitchen/production tasks, inventory transactions, printer
configuration/assignments, devices, Store modules and Uber mappings. Eligibility
signature unchanged; inactive historical option rows unchanged. Post-write
reconcile reports linked_options=0 and conflicts=0 for each Store. Production
Uber remains disabled and scoped routes return 403 in all three Stores.

Owner auth/me, workspace, Store context, menu and printing API checks PASS.
Production and Staging health PASS. Post-release browser visual acceptance was
not completed: native Chrome automation encountered clipboard timeout / session
changes; no UI PASS is claimed. Read-only API/DB identity proof avoids changing a
Production name merely to test propagation. No login secret or token is included.

## Recovery and artifact retention

Verified custom-format DB backups, private rollback/target models and safe
result/read-back evidence are retained under each environment's private directory:
`/srv/restaurant-pos/{production,staging}/addon-identity-ce35278b3569a4eacfa29efc01cadbf0c409bec6`.
Private models must never be pasted into logs/repository (they contain env values).
The failed initial Staging attempt is retained separately. Production current
Compose/release metadata points at the new backend; DB, nginx and frontend
containers were not recreated. Original dirty Owner workspace remains untouched.

Docker / Disk Hygiene: disk 69% / 18 GB free before and after; build cache
16.07 → 16.25 GB, reviewed without cleanup. Reclaimed 0 GB. Active images,
rollback images, releases and database volumes retained; both environments and
PostgreSQL healthy. Production mutation was limited to the authorized backend,
current Add-on catalog/references and canonical printing fallback.

## Scope and completion

PRODUCT REPAIR: resolver plus MenuServiceImpl, OwnerMenuOptionServiceImpl,
OrderServiceImpl and StoreMenuCloneTransactionServiceImpl, related regression
suites and current technical contracts. No new frontend/Android artifact.
TOOLING / GOVERNANCE REPAIR: bounded SQL compare-before reconciliation and
backend-only promotion helper were blocking necessities for this explicitly
approved Production data/release operation; env-order comparison repaired after
safe automatic rollback. No new generalized release platform. Final evidence and
CURRENT_STATE sync are docs-only; risk-based review exception, no rebuild.

Approximate execution allocation (Estimated, work overlaps): investigation 6 min,
implementation 9 min, tests 5 min, Agent 6/PR/merge 4 min, Staging build/deploy 4 min
(includes one safe rollback/retry), Staging acceptance 1 min, Production deploy /
reconcile / acceptance 2 min, evidence/browser/governance 5 min. Total approximately
36 min, starting 16:07 UTC. Final Stop follows evidence merge; no further release,
real-order test or Production Uber activation is authorized by this evidence.
