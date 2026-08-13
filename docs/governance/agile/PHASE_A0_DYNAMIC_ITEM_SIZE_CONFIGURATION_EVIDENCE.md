# Phase A0 Dynamic Item Size Configuration Evidence

Status: `IMPLEMENTATION_CANDIDATE`

Base `origin/main`: `447d581b430aae7ec8c12f94dc8a95ffb714b9bc`

Scope:

- Phase: `PHASE_A_MODULAR_PRODUCTIZATION`
- Loop: `PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION`
- Production: `NO MUTATION`
- Staging: repository candidate only until PR merge and separately executed
  exact-SHA Staging validation
- Flyway: no migration required

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

- Production was not read, written, deployed, restarted, migrated or configured.
- No Flyway migration or schema operation was added.
- No Production or Staging runtime configuration was changed by the repository
  candidate.
- Phase B, Phase C, Chinatown, Sainte-Catherine, Store provisioning and
  Production promotion remain unauthorized.

Expected post-validation stop after PR merge, exact-SHA Staging deploy and
automated validation:

```text
FINAL_PRODUCTIZATION_PLANBOOK_MERGED_PHASE_A0_DEPLOYED_WAITING_FOR_OWNER_SIZE_RETEST
```
