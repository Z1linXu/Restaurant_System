# Phase A0 Dynamic Item Size Configuration Evidence

Status: `DEPLOYED_TO_STAGING_AUTOMATED_VALIDATION_PASS_WAITING_FOR_OWNER_SIZE_RETEST`

Base `origin/main`: `447d581b430aae7ec8c12f94dc8a95ffb714b9bc`
Merged `origin/main`: `c83933f16f4eb1c1be33bd13772ac489d79a7176`

Scope:

- Phase: `PHASE_A_MODULAR_PRODUCTIZATION`
- Loop: `PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION`
- Production: `NO MUTATION`
- Staging: repository candidate only until PR merge and separately executed
  exact-SHA Staging validation
- Flyway: no migration required

## A0.1 refinement note

Owner Staging UI review accepted A0's underlying
`OPTION + MODIFIER_GROUP + PRICE_DELTA` implementation direction, but rejected
free-form Size creation/editing as final product UX. The follow-up A0.1 product
contract requires only system-controlled Small/Regular/Large Size definitions
and Store-level Size/Combo pricing policy as canonical price source.

Fresh schema audit found no Store-level pricing policy/settings table. A0.1 is
therefore stopped before implementation at:

```text
PHASE_A0_1_PRICING_POLICY_SCHEMA_CHANGE_WAITING_FOR_OWNER_APPROVAL
```

Evidence/design:
[PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_SCHEMA_GATE](PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_SCHEMA_GATE.md).

## Current size model audit

`CURRENT_SIZE_MODEL` is:

```text
OPTION + MODIFIER_GROUP + PRICE_DELTA
```

The existing canonical Menu Engine already represents item sizes as
`menu_item_options` rows:

| Product contract | Current canonical field |
|---|---|
| `id` | `menu_item_options.id` |
| `item_id` | `menu_item_options.menu_item_id` |
| `code/key` | `menu_item_options.option_code` |
| `display_name` | `menu_item_options.name_en` |
| `display_name_zh` | `menu_item_options.name_zh` |
| `display_order` | `menu_item_options.sort_order` |
| `enabled` | `menu_item_options.is_active` |
| `default_selected` | first active `SIZE` option ordered by `sort_order ASC NULLS LAST, id ASC` |
| `pricing rule` | `menu_items.base_price + selected SIZE.price_delta + other selected option deltas` |

Canonical size rows use:

```text
option_group = SIZE
option_type = size
```

No second size engine, item-variant table, hardcoded UI size table, or absolute
variant price truth is introduced.

## Implementation summary

- Owner Menu Management now displays the `SIZE` group and supports add, edit
  bilingual label, price delta, enable/disable, up/down display order and
  `设为默认` through the existing reorder API.
- Backend owner option writes validate the size contract:
  - at least one active Size when any Size config exists;
  - unique Size code per item;
  - bilingual labels;
  - non-null price delta;
  - active display order present and unique;
  - Size cannot have a parent option, including legacy/type-only
    `option_type=size` rows;
  - group `SIZE` canonicalizes to type `size`.
- `/frontdesk/menu` frontend mapping ignores inactive options defensively,
  preserving the backend active-only catalog contract.
- Ordering auto-selects the first active Size. A single configured Size renders
  as read-only/auto-selected instead of a choice grid.
- Order line snapshots, frozen submit payloads, order history and printing
  continue to use `order_item_options` snapshots. Existing draft lines keep
  their selected option identity; no code path silently converts one selected
  Size into another.

## Required A0 cases covered by tests

| Case | Evidence |
|---|---|
| Small / Regular / Large | `useMenuCatalog.test.ts` dynamic size ordering and default tests |
| Regular / Large | existing catalog/default tests plus owner default reorder helper |
| Single Size | `useMenuCatalog.test.ts` single Size snapshot and `OrderingPage.test.ts` no extra required size choice |
| Small enabled + Regular enabled + Large disabled | `useMenuCatalog.test.ts` active-only dynamic size mapping |
| valid single default for multi-size items | backend reorder test and `menuOptionDefaults.test.ts` default Size reorder |
| Size parent forbidden | `OwnerMenuOptionServiceImplTest` rejects `option_type=size` with a parent even when `option_group` is blank |
| Menu Management edit/reorder -> revision increment | `OwnerMenuOptionServiceImplTest` verifies revision increment on create/reorder; existing owner option service increments on update/deactivate |
| draft line snapshot retention | `useDraftOrder.menuSnapshot.test.ts` and `useMenuCatalog.test.ts` frozen payload snapshot tests |

## Validation commands

```text
frontend: npm test -- src/features/owner-admin/menuOptionDefaults.test.ts src/hooks/useMenuCatalog.test.ts src/hooks/useDraftOrder.menuSnapshot.test.ts src/features/ordering/OrderingPage.test.ts
backend:  mvn test -Dtest=OwnerMenuOptionServiceImplTest
```

Current result:

```text
frontend focused tests: PASS (4 files, 16 tests)
backend focused tests:  PASS (OwnerMenuOptionServiceImplTest, 6 tests)
frontend build:        PASS
frontend full tests:   PASS (17 files, 85 tests)
backend full tests:    PASS (411 tests run, 0 failures, 3 skipped)
changed-file eslint:   PASS
git diff --check:      PASS
```

Repository-wide frontend lint still reports pre-existing unrelated lint errors
outside the A0 changed-file set; the A0 touched files pass targeted eslint.

## Boundaries retained

- Production was not written, deployed, restarted, migrated or configured; only
  bounded read-only continuity health checks were observed.
- No Flyway migration or schema operation was added.
- Staging was deployed only through the exact-SHA same-host Staging release,
  preflight and deploy gates after PR merge.
- Phase B, Phase C, Chinatown, Sainte-Catherine, Store provisioning and
  Production promotion remain unauthorized.

## Exact-SHA Staging deployment and automated validation

Final Productization Planbook entered `main` through PR #127 at merge
`447d581b430aae7ec8c12f94dc8a95ffb714b9bc`. Phase A0 entered `main` through
PR #128 at merge `c83933f16f4eb1c1be33bd13772ac489d79a7176`.

Staging was then rebound and deployed to exact
`c83933f16f4eb1c1be33bd13772ac489d79a7176` only. Production remained
unchanged.

Runtime evidence:

| Evidence | Result |
|---|---|
| release/env rotation | `OPS001_RELEASE_ENV PASS`; approval SHA-256 `fc839ce7f4bc42c58688e3dce32c658d8fe36bf6498829f068d4fd6c2b1392be` |
| preflight | `/srv/restaurant-pos/staging/evidence/phase-a0-preflight-c83933f16f4eb1c1be33bd13772ac489d79a7176.txt`; SHA-256 `f140468ca5f62f4310af3e1a86c6756ed94fbd62c422fc456db9ef73d5356d20`; `SUMMARY|PASS` |
| deploy | `staging-deploy.sh --execute-start`; exact SHA `c83933f16f4eb1c1be33bd13772ac489d79a7176` |
| health | Staging frontend/backend/WebSocket `200/200/200`; Production system/menu `200/200` after validation |
| Flyway | last row `installed_rank=10`, `version=10`, `success=true`; rows `10`, failed `0` |
| printing | Staging Store 1 retained `printing_enabled=true`, `printing_mode=MOCK`, enabled logical printers `4`, enabled assignments `3` |

Automated Staging validation:

- The deployed A0 contract initially exposed inactive legacy Size rows on
  `traditional_beef_noodle` with blank `option_code` and blank
  `option_group`. Those rows were Staging-only legacy configuration and blocked
  the new V10 Size editor before persistence. They were normalized through the
  existing Staging API only:
  `/srv/restaurant-pos/staging/evidence/phase-a0-size-legacy-normalization-c83933f16f4eb1c1be33bd13772ac489d79a7176.txt`,
  SHA-256 `f9ded6735819c8c87bbb9a5b753166ce2bb4542aa3dc5f33976fcf8c3773c6c5`.
  The rows remained inactive; active Production-like menu visibility and
  prices were not changed.
- Dynamic Size smoke PASS evidence:
  `/srv/restaurant-pos/staging/evidence/phase-a0-dynamic-size-smoke-c83933f16f4eb1c1be33bd13772ac489d79a7176-A0R372d333.txt`,
  SHA-256 `fcb6144ed0608e87d1d48c705b628ce0da83197163adca097e26d703af946fc2`.
- Conventional Size print smoke PASS evidence:
  `/srv/restaurant-pos/staging/evidence/phase-a0-conventional-size-print-smoke-c83933f16f4eb1c1be33bd13772ac489d79a7176-A0Pab75f9.txt`,
  SHA-256 `de98ecf708d90beb419283712471d9d749c8914d3601e37fe7bd8fee91ced8eb`.

Validated Staging cases:

| Case | Runtime result |
|---|---|
| Small / Regular / Large with default Regular | PASS; catalog active order `a0_regular/a0_small/a0_large` after reorder |
| Regular / Large | PASS; catalog active order `a0_regular/a0_large` |
| Single Size | PASS; catalog active order `a0_single` |
| Small + Regular active, Large disabled | PASS; catalog active order `a0_small/a0_regular`; disabled Large hidden from catalog |
| Menu Management API routes | PASS for `/admin/menu/management-context` and `/admin/platform/menu/items` |
| revision/cache contract | PASS; every scenario observed matching revision endpoint and catalog revision/content hash |
| order snapshot | PASS; submitted Staging order retained selected Size snapshot |
| printing | PASS; order submit produced `GRAB` and `FRONTDESK_RECEIPT` `PRINTED`; HOT_KITCHEN module test `PRINTED`; conventional Size labels rendered on GRAB and Frontdesk |

The smoke created Staging-only audit/menu-revision/order/print rows and restored
the target item's active Size configuration to its pre-smoke normalized state.
No credential value, token, raw environment value, printer endpoint, device
credential, customer/PII, payment or Production business data was output.

Expected post-validation stop after PR merge, exact-SHA Staging deploy and
automated validation:

```text
FINAL_PRODUCTIZATION_PLANBOOK_MERGED_PHASE_A0_DEPLOYED_WAITING_FOR_OWNER_SIZE_RETEST
```
