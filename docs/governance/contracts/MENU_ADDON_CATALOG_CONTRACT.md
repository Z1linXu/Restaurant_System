# Menu Add-on Catalog and Combo Defaults

## Scope and ownership

Owner-approved bounded menu simplification: explicit item Combo egg defaults,
Store Add-on editing, immutable Organization semantic codes, item eligibility,
typed free Store Removes, and code-based printing. Execution status belongs only to
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
A current-read resolver joins the formal Store Add-on FK; these copies cannot
choose a second price/name/code for POS, Admin options, Uber mapping or new orders.
New selections and direct draft submission reject stale canonical prices without
rewriting snapshots. Kitchen Mirror retains non-financial zero amounts.
Disabled or foreign assignments fail closed. Explicit Combo/Remove semantics
remain separate from ordinary Add-ons.
Catalog updates and all linked option name/price/effective-active updates occur
in one transaction together with the existing menu revision update. Item
eligibility persists independently when the whole catalog Add-on is inactive.
Ordinary generic option endpoints cannot bypass the catalog for ADD_ON writes.
Linked canonical Removes use their separate free catalog/applicability boundary.
Unresolved legacy Removes and remaining option groups retain their established
compatibility behavior.

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
template conflicts without silently choosing a value. Menu cloning now fails closed on unresolved target Add-on identities within the original transaction; target references never copy source Store IDs.

Historical order/print snapshots are never rewritten or backfilled. Owner's
2026-10-06 Production decision explicitly confirms `addon_beef_tendons` at 6.99
and the exact existing `extra_beef_tendon` row identity; no fuzzy or automatic
remap of other codes is permitted. Unmapped legacy codes remain
unresolved; historical aliases remain printable. Production
value choices and reconciliation require the separate Owner Production gate.

## Printing and product simplicity

`fried_egg` and `combo_fried_egg` remain different identities, as do the tea egg
counterparts. Equal labels/tokens never justify deduplication. Known Add-on
tokens and frozen historical jobs keep their identity and fallback contract. Spicy display is rule-first through the shared
`KitchenSpicinessDisplayResolver`; it does not introduce or change Menu identity.
Missing MODIFIER_ADD coverage produces a lightweight fallback warning; it does
not block menu editing or create an invented token.

Menu Management alone owns normal Add-on creation and immutable code. Printing
Display Rules, now a Menu Management tab, derives a read-only identity
projection and edits only nullable aliases. Printing Settings retains the
same-Store link plus physical assignment/mode/device/job management.
The Add-on alias section cannot create, delete or rename Menu identities.
A new Menu entry appears automatically.
The versioned CANONICAL_SNAPSHOT fallback policy preserves old published revisions
and explicit aliases while new jobs use frozen Menu names without an alias.
Combo egg codes remain available to Combo/printing but absent from normal Add-ons.

Explicit confirmed prices may be reconciled through the bounded Staging request
in one transaction. This does not select unresolved Chinese/English names, remap
identity, alter historical snapshots, or authorize Production. The current price
schedule is a dated Owner decision, not a permanent future Add-on default.

Add-ons UI edits names, price and availability once per Store; item UI only
enables/disables an Add-on and displays read-only prices. Code is entered on
creation and read-only afterward. No destructive definition deletion, semantic
replacement UI, generic migration framework or duplicate Printing editor is
required. Normal menu re-fetch/revision invalidation exposes catalog edits.

## Free Removes and price snapshots

V38 adds a separate Store-owned `store_removes` identity and category/item
applicability, not an Add-on mode or an Organization-wide price definition.
Codes are immutable and unique per Store. There is no price field: linked
Remove options are `REMOVE` / `remove`, price zero and never simultaneously
linked to an Add-on. Item ALLOW/DENY overrides category inheritance; INHERIT
means no item override. Orderability also requires catalog active. New category
members inherit eligibility without editing every item.

Normal catalog edits/assignment refresh and menu revision updates are atomic.
Read resolution uses formal same-Store FKs; generic item-option mutation cannot
change a linked/catalog-owned Remove. Migration does not reconcile real data.
Explicit dry-run/apply reconciliation preserves unambiguous old option IDs and
item eligibility, reports conflicts, and never chooses differing business names.
Provisioning/clone creates target-owned identities rather than shared mutable
references. See [Remove Catalog](../../operations/REMOVE_CATALOG.md).

For local orders, `unit_price` is captured base price. Option quantity is per
item, so `line_amount = (unit_price + sum(price_delta * option.quantity)) *
item.quantity`. Initial submission and Update serialize the same frozen quote.
The backend validates current scope/availability/canonical prices before
persisting money; an old submitted line is never silently repriced. Frontdesk
per-bowl amounts allocate the stored line's cents exactly rather than displaying
the base price alone. Add-on/Remove ingredient conflicts use stable codes and
remain scoped to their main dish or selected Combo side; stale/crafted
conflicting submissions fail rather than selecting a preference server-side.

Menu Management has seven tabs: Menu Items, Add-ons, Categories, Stations,
Combo Configuration, Pricing Rules and Printing Display Rules. The Add-ons tab
contains Add-ons/Removes subnavigation. There is no duplicate priced Remove
catalog or duplicate display-rule editor in Printing Settings.

## Acceptance boundary

Focused Combo and Add-on tests, transaction/concurrency/isolation/migration and
historical-printing regression, consolidated independent Agent 6, reviewed PR,
merged exact-SHA Staging deployment and integrated synthetic acceptance are
required. No Production mutation, real hardware binding or Phase C work.
