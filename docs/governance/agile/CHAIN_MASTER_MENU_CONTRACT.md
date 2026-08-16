# Chain Master Menu Contract

## Purpose

The Chain Master Menu defines the Organization standard menu graph from which
future Stores can be materialized. It is not a live Store and it is not a clone
of a Store database.

## Scope

```text
organization_id
master_menu_key = LANZHOU_CHAIN_MASTER_MENU
master_menu_version = v1
fingerprint_sha256 = deterministic canonical graph fingerprint
source = reviewed St-Denis canonical profile/menu artifact
```

The first version is derived from reviewed St-Denis configuration evidence.
Production St-Denis runtime data must not be queried or copied to create this
version.

## Required graph

- Categories with stable `master_category_key`, display names, sort order and
  active-by-default state.
- Products/items with stable `master_product_key`, category reference, SKU,
  display names, default sort order, default station intent and default/base
  reference price.
- Option groups and options with stable `master_option_key`, option code when
  available, semantic group, parent option relationship and default price delta.
- Default item eligibility metadata such as combo allowed, size options and
  modifier groups.
- References to profile-owned defaults for pricing policy, combo
  configuration, station template, table template, printing display rules,
  module defaults, roles and hardware requirements.

## Identity rule

Master identity must never be a Store-local database ID. Master identity is a
stable Organization-scoped key carried into Store materialization mappings.

Recommended keys:

| Entity | Key rule |
| --- | --- |
| Master menu | `(organization_id, master_menu_key)` |
| Master menu version | `(master_menu_id, version_key)` |
| Category | `master_category_key`, seeded from stable category codes where safe |
| Product | `master_product_key`, seeded from stable SKU where safe |
| Option | `master_option_key`; may include `product_key + option_group + option_code` when option code is present |
| Option parent | parent relationship by `master_option_key`, not local DB ID |

Current St-Denis profile rows include legacy options with null
`option_code`. Before implementation, those rows must receive explicit
`master_option_key` values in the canonical Master artifact.

## Fingerprint

The Master Menu version fingerprint is computed from the canonical Master graph
and metadata, not from live Store row IDs or Store catalog hashes.

Inputs include:

- master menu key/version;
- master category/product/option keys;
- names, sort order and default active state;
- option hierarchy and default deltas;
- reference/default pricing metadata;
- source profile identity and artifact fingerprint.

Inputs exclude:

- Store IDs, item IDs, category IDs, option IDs and station IDs;
- order, print, report and inventory runtime rows;
- credentials, secrets, printer endpoints and device tokens;
- Store-local overrides after materialization.

## Profile relationship

Future Profile versions reference a published Master Menu version by key,
version and fingerprint. Profiles own non-menu operating defaults such as
tables, station templates, module defaults, role defaults, logical printing
topology, hardware requirements and A11 printing display rule defaults.

Historical `ST_DENIS_CANONICAL_PROFILE/v1` remains immutable and valid. It is
not rewritten to become the Master Menu.
