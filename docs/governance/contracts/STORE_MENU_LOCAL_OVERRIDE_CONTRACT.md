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
```

Store-local pricing changes do not rewrite Master Menu prices.

## Combo boundary

Profile/Master defaults can seed Store combo groups/components. After
materialization, `store_combo_groups` and `store_combo_components` are the Store
runtime source of truth.

## Printing boundary

Profile/Master defaults can seed the initial Store A11 printing display rule
revision. After materialization, Store-owned
`printing_display_rule_revisions` are the runtime source. Store print aliases
do not rewrite the Master product name or Master SKU.

## Historical behavior

Order, receipt, KDS and print snapshots retain what was submitted/rendered at
the time. Local overrides affect future catalog/rendering behavior only.
