# Phase A11.5 Chain Master Menu Design

## Status

```text
PHASE_A11_5_CHAIN_MASTER_MENU_AND_STORE_MATERIALIZATION_DESIGN = PASS
PHASE_B_MENU_PROVISIONING_MODEL = DEFINED
PHASE_B_IMPLEMENTATION = WAITING_FOR_EXPLICIT_OWNER_APPROVAL
RUNTIME_ACTION = NOT_PERFORMED
PRODUCTION = NO_MUTATION
```

This design closes the architecture gap between the accepted Phase A profile
foundation and future Phase B Store provisioning. It is documentation and
contract work only.

Current note: the Owner has since granted Phase B Part 1 implementation
authority. The repository implementation of this design is recorded in
[PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md).
The status block above remains historical A11.5 design evidence.

## Owner product rule

```text
MASTER DEFINES THE STANDARD.
STORE DEFINES LOCAL REALITY.
```

The initial Master Menu source is the reviewed and canonicalized St-Denis
configuration artifact, not the live Production St-Denis database and not a
live-linked St-Denis Store. The first published Master Menu is:

```text
LANZHOU_CHAIN_MASTER_MENU / v1
```

## Target flow

```text
Reviewed St-Denis profile/menu artifact
-> LANZHOU_CHAIN_MASTER_MENU/v1
-> future Store Profile version references Master Menu version and fingerprint
-> Phase B Store materialization transaction
-> Store-owned menu/pricing/combo/printing rows
-> Store local operation and overrides
```

After materialization, the Store is independent. It may deactivate categories
or items, adjust Store pricing, maintain Store-owned combo contents, publish
Store-owned A11 printing display rule revisions and add Store-only items.

## Current implemented vs planned

| Topic | Current implemented | Planned Phase B |
| --- | --- | --- |
| Profile storage | DB-backed profile versions and artifacts. | Profile version references a published Master Menu version/fingerprint. |
| Master Menu | Not implemented. | Organization-scoped immutable published versions. |
| Store creation | Legacy active creation disabled; dry-run validator only. | One bounded provisioning/materialization transaction. |
| Menu identity | Store-local DB IDs plus conventional `code`, `sku`, `option_code`. | Stable master category/product/option keys, with Store mapping. |
| Pricing | Store-local `menu_items.base_price` and `store_pricing_policies`. | Master standard price is a default/reference; Store actual price remains Store source of truth. |
| Combo | Store-owned combo groups/components. | Profile/Master may seed defaults; Store owns post-materialization state. |
| Printing | Store-owned A11 display rule revisions. | Profile/Master seeds defaults; Store owns post-materialization revisions. |
| Reporting | Store-local item IDs. | Future reporting can aggregate through master product identity. |

## Recommended architecture

Use the hybrid schema option from
[MASTER_MENU_SCHEMA_OPTIONS](MASTER_MENU_SCHEMA_OPTIONS.md):

```text
Option C = normalized Master identity/version rows + canonical JSON artifact
```

The normalized side gives Organization scope, immutable version state, master
category/product/option identity and future diff/reporting joins. The artifact
side preserves the full menu graph, option hierarchy, defaults and profile
provenance without over-normalizing every future menu nuance too early.

## Master Menu boundaries

Master Menu owns:

- Organization-scoped standard category/product/option identity.
- Master version lifecycle and fingerprint.
- Standard display names, default sort order, default station intent and default
  reference/base price values.
- Option group semantics and parent/child option relationships.
- Default item eligibility metadata used by Store materialization.

Master Menu does not own:

- live Store database IDs;
- current Store active/sold-out state;
- Store actual pricing policy after materialization;
- Store-only additions;
- tables, staff credentials or auth material;
- physical printer endpoints, Pad credentials or device pairing;
- PrintJob history, routing leases or runtime queue state;
- automatic update/sync into existing Stores.

## Store materialization principle

Phase B v1 materializes once. It does not continuously synchronize.

```text
Published Master Menu version + Profile version
-> new target Store rows with new Store-local IDs
-> master identity mappings/provenance retained
-> Store menu revision incremented
-> Store owns future changes
```

Existing Stores do not auto-update when `LANZHOU_CHAIN_MASTER_MENU/v2` is later
published. A future diff/apply tool may be designed separately and must remain
Owner-explicit.

## Phase B v1 minimum

- Create a new Store from a reviewed Profile plus published Master Menu version.
- Materialize categories, items, options, pricing defaults, combo defaults,
  station references and A11 printing display rule defaults into target
  Store-owned rows.
- Preserve master identity mapping for future reporting/diff.
- Support Store-local category/item deactivation.
- Support Store-only additions with no master reference.
- Keep existing Store pricing policy, combo and A11 rule ownership boundaries.

## Deferred

- automatic Master v1 to v2 sync;
- full Master Menu management UI;
- promoting Store-local additions back into Master;
- real Chinatown/Sainte-Catherine creation;
- Production deployment or Production data reads;
- cross-Store reporting implementation.

## Stop state

```text
PHASE_A11_5_CHAIN_MASTER_MENU_DESIGN_COMPLETE_WAITING_FOR_PHASE_B_OWNER_APPROVAL
```
