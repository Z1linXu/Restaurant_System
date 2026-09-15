# Menu Add-on Catalog and Combo Defaults

## Scope and ownership

Owner-approved bounded menu simplification: explicit item Combo egg defaults,
Store Add-on editing, immutable Organization semantic codes, item eligibility,
and existing code-based printing. Execution status belongs only to
[`CURRENT_STATE.yml`](../CURRENT_STATE.yml).

PRIMARY_REPAIR_GOAL: configure Combo egg exceptions and eliminate duplicated
editable Add-on names/prices without altering historical orders or printing
identity. EXPECTED_REPAIR_CLASS: HIGH (schema, money, isolation, provisioning).

## Combo default

Nullable `menu_items.default_combo_egg_component_code` is an explicit Store-local
exception. Null inherits the Store COMBO_EGG default; no normal-item backfill.
Selection priority is existing user/draft choice, valid item exception, Store
group default, existing safe fallback/required selection. The exception must
resolve to an enabled same-Store COMBO_EGG component on a Combo-allowed item.
Names never determine product categories or defaults. Configuration shows only
exceptions and allows adding, editing and removing them. Manual selection wins.

## Add-on sources of truth

| Field | Editable authority |
| --- | --- |
| Semantic code | Organization definition, immutable after creation |
| Name, price, active | Store Add-on catalog |
| Item eligibility | Existing item option linked to Store Add-on |
| Kitchen token | Store Printing Display Rule MODIFIER_ADD keyed by code |
| Submitted name, code and price | Frozen OrderItemOption snapshot |

Store A changes must not mutate Store B, Organization definitions, published
Master artifacts or Profile fingerprints. Master/Profile values are defaults
for independent Store materialization, not shared mutable operating prices.

Existing option IDs and runtime fields remain as compatibility materialization.
Catalog updates and all linked option name/price/effective-active updates occur
in one transaction together with the existing menu revision update. Item
eligibility persists independently when the whole catalog Add-on is inactive.
Ordinary generic option endpoints cannot bypass the catalog for ADD_ON writes.
Other option groups retain their current editing behavior.

## Reconciliation and history

Schema changes are additive. A bounded reviewed reconciliation path groups
current rows by Store and stable code. Only identical names/prices may be linked
automatically; active differences preserve item eligibility. Blank/invalid codes,
ambiguous names/prices and duplicate ambiguous relationships remain unresolved
with sanitized conflict evidence. No majority, min/max, newest, Master/source
or name-matching heuristics choose business values. Replay is idempotent;
failure cannot leave partial links/catalog writes.

Legacy unresolved rows remain readable/orderable under the established runtime
contract until explicit reconciliation; normal business UI cannot edit their
semantic fields independently. New Store materialization uses the same bounded
reconciliation boundary inside the existing transaction and preserves unresolved
template conflicts without silently choosing a value.

Historical order/print snapshots are never rewritten or backfilled. Current
Staging beef-tendon aliases may be reconciled only by explicit audited code
mapping to `extra_beef_tendon`; historical aliases remain printable. Production
value choices and reconciliation require the separate Owner Production gate.

## Printing and product simplicity

`fried_egg` and `combo_fried_egg` remain different identities, as do the tea egg
counterparts. Equal labels/tokens never justify deduplication. Printing resolver
and historical fallbacks stay unchanged unless tests demonstrate a regression.
Missing MODIFIER_ADD coverage produces a lightweight fallback warning; it does
not block menu editing or create an invented token.

Add-ons UI edits names, price and availability once per Store; item UI only
enables/disables an Add-on and displays read-only prices. Code is entered on
creation and read-only afterward. No destructive definition deletion, semantic
replacement UI, generic migration framework or duplicate Printing editor is
required. Normal menu re-fetch/revision invalidation exposes catalog edits.

## Acceptance boundary

Focused Combo and Add-on tests, transaction/concurrency/isolation/migration and
historical-printing regression, consolidated independent Agent 6, reviewed PR,
merged exact-SHA Staging deployment and integrated synthetic acceptance are
required. No Production mutation, real hardware binding or Phase C work.
