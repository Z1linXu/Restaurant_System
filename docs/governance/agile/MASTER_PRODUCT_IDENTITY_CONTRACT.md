# Master Product Identity Contract

## Purpose

Master product identity lets the system say that the same standard product
exists across Stores even though each Store has different local database IDs,
prices, active state and optional local overrides.

## Product key

The canonical product key is:

```text
organization_id + master_menu_key + master_product_key
```

For `LANZHOU_CHAIN_MASTER_MENU/v1`, seed `master_product_key` from the existing
reviewed SKU when it is stable. Example:

```text
traditional_beef_noodle
beef_chow_mein
dan_dan_noodle
```

The key is immutable within a published Master Menu version. Display names can
change in a new version; identity must not.

## Category key

The canonical category key is:

```text
organization_id + master_menu_key + master_category_key
```

Seed from reviewed category code when stable. Store-local category IDs are not
identity.

## Option key

The canonical option key is:

```text
organization_id + master_menu_key + master_product_key + master_option_key
```

When current data has a reliable `option_code`, the implementation may derive:

```text
master_option_key = option_group + ":" + option_code
```

When current data lacks `option_code`, the Master artifact must assign an
explicit stable key. Do not use live `menu_item_options.id` or profile-local
`OPT-*` as the final cross-Store identity unless it is consciously promoted and
frozen in the Master artifact.

## Store mapping

Store materialization must retain a mapping from each Store-local row to its
master identity:

- Store category to master category key;
- Store item to master product key;
- Store option to master option key;
- mapping source Master Menu version and fingerprint;
- materialization request identity.

This can be stored as future columns or a separate mapping table; the schema
option is defined in
[MASTER_MENU_SCHEMA_OPTIONS](MASTER_MENU_SCHEMA_OPTIONS.md).

## Reporting

Future reporting should aggregate by master product key when chain-level
reporting is requested, and by Store-local item ID when Store operational
detail is requested.

Historical order snapshots remain authoritative for what the customer ordered.
Master identity is an aggregation/provenance key, not a replacement for order
snapshots.

## A11 printing integration

A11 display rules already use `item_sku` for item aliases. The Phase B design
aligns master product keys with stable SKUs where safe so printing aliases can
be seeded without Store-local item IDs. Store-owned A11 revisions remain the
runtime source after materialization.
