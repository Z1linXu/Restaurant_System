# Store Menu Local Override Contract

## Purpose

Store-local overrides record the difference between the chain standard and a
Store's operating reality after materialization.

## Override classes

```text
IN_SYNC
LOCALLY_DEACTIVATED
LOCAL_NAME_OVERRIDE
LOCAL_SORT_OVERRIDE
LOCAL_PRICE_OVERRIDE
LOCAL_OPTION_OVERRIDE
LOCAL_COMBO_OVERRIDE
LOCAL_PRINT_RULE_OVERRIDE
STORE_ONLY
```

The exact schema can be columns or an override/mapping table, but the runtime
meaning must remain Store-scoped.

## Store-only additions

Store-only categories, items and options are allowed after materialization.
They have no master identity unless a future Owner-approved process promotes
them into a later Master Menu version.

Store-only additions must:

- stay scoped to one Store;
- carry Store-local SKU/code rules;
- appear in Store catalog and ordering only for that Store;
- not alter the published Master Menu.

## Pricing boundary

Master Menu standard/base price is a reference/default for materialization.
Store actual pricing remains:

```text
menu_items.base_price
store_pricing_policies
store_addons.price (ADD_ON catalog; option rows are materialization)
```

Store-local pricing changes do not rewrite Master Menu prices. Remove choices
use the separate Store-owned free Remove catalog and always contribute zero;
they are never a second priced Add-on type. Option quantity is per item/bowl:
`line_amount = (captured_base_price + sum(option_delta * option_quantity)) *
item_quantity`. Initial submit and Update serialize the same captured quote;
the backend persists the resulting amount and retains existing canonical-price
and eligibility checks. Receipt blocks allocate stored line cents without
consulting current menu prices.

## Combo boundary

Profile/Master defaults can seed Store combo groups/components. After
materialization, `store_combo_groups` and `store_combo_components` are the Store
runtime source of truth.

## Remove applicability boundary

`store_removes`, `store_remove_categories` and `store_remove_item_rules` are
Store-local. Item ALLOW/DENY overrides category inheritance; absent item rows
mean INHERIT. Names, active state and sort order are shared only within that
Store; no change flows to another Store or published Master/Profile. New
Store/clone materialization creates independent target catalog references.
Legacy reconciliation is explicit, preserves unambiguous IDs/eligibility and
leaves conflicting names unresolved. Details: [Remove Catalog](../../operations/REMOVE_CATALOG.md).

## Printing boundary

Profile/Master defaults can seed the initial Store A11 printing display rule
revision. After materialization, Store-owned
`printing_display_rule_revisions` are the runtime source. Store print aliases
do not rewrite the Master product name or Master SKU. Each output module reads
its own column. Explicit NO_SPICY kitchen wording overrides legacy suppression;
empty means hide and missing/null keeps the legacy fallback. Other Stores keep
their own revisions/defaults. No Store-private edit is promoted to a shared
Profile/default as a side effect.

## Historical behavior

Order, receipt, KDS and print snapshots retain what was submitted/rendered at
the time. Local overrides affect future catalog/rendering behavior only. Source
job reprint copies frozen text; full order reprint reads persisted item/option
snapshots and captured module rules where available. Frozen GRAB task text is
not rebuilt merely because a new rule was published. Frontdesk can expose a
previously stored SOUP_BASE selection; missing old vegetable-noodle selection
is reported as unrecorded, never inferred from today's menu. No historical
amount, task or rendered payload is rewritten.
