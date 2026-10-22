# Two-store Uber local preparation — read-only Production evidence

Observed 2026-10-21 22:31–22:36 America/Toronto (2026-10-22 UTC).
This is local-configuration evidence only; no external Uber menu access, role,
scope grant or Store authorization is inferred. No code release, Production
restart, database write, mapping persistence, business order or printing was
performed by this audit.

The read-only SSH helper used PostgreSQL `default_transaction_read_only=on`,
`BEGIN READ ONLY`, a 10-second statement timeout and a 1-second lock timeout.
The reusable [inventory SQL](../../deployment/cloud/uber-two-store-readiness-readonly.sql)
selects only configuration identities and aggregate counts. It does not select
printer endpoints, device token hashes, customer data, credentials or order bodies.

The entire inventory, including age calculations, ran successfully after
independent runtime confirmation: both backend containers have
`TZ=America/Toronto`, no `-Duser.timezone` override, and the application does not
replace the JVM default. Container `date` reports EDT -0400. This was not inferred
from the database session timezone. Safe output is
`/tmp/rs-uber-two-store-readiness-20261022.log`. At that observation Saint-Denis
had zero device rows seen within five minutes (newest about 22 minutes old),
and no explicit heartbeat; registration alone is not current worker proof.

## Exact local identity and root resolution

| Proof | St-Denis | St-Catherine |
| --- | --- | --- |
| Production Store ID / code / organization | 1 / `4483_R_SAINT_DENIS` / 1 | 3 / `ST_CATHERINE` / 1 |
| Approved root semantic rows | 41 | 41 |
| Distinct local SKUs among those roots | 30 | 30 |
| Exact unique local item matches | 41/41 | 41/41 |
| Active and not sold out | 41/41 | 41/41 |
| Active same-Store station and category | 41/41 | 41/41 |
| COMBO_ROOT with unique active `COMBO/combo` trigger | 8/8 | 8/8 |
| Current Production Uber mapping rules | 0 | 0 |

The 41-row input is the previously Owner-approved local semantic table in
[UBER_ROOT_MAPPING_STATUS_20261002](UBER_ROOT_MAPPING_STATUS_20261002.md).
Only exact local SKU and mapping-mode values were reused in the SELECT; Test
Store external stable IDs and Staging numeric item/option IDs were not copied.
This proves those local identities can be resolved; it does not prove the live
merchant menu uses the same Uber IDs. Live root/modifier IDs and parent contexts
must be read and compared after Uber grants the read-only access.

Both stores contain 39 menu item rows, 35 active. Each has one historical inactive,
sold-out `fried_egg` row alongside one active `fried_egg` row; this SKU is not among
the 41 approved roots. No historical row was changed or removed. Any future
mapping involving that SKU must explicitly resolve the effective active catalog,
not choose the first row from an unrestricted database lookup.

## Canonical Add-on and combo integrity

| Proof | St-Denis | St-Catherine |
| --- | --- | --- |
| Canonical Add-ons / active | 15 / 15 | 15 / 15 |
| Active free `green_onion` | 1 | 1 |
| Existing option assignments with formal StoreAddon FK | 66 | 66 |
| Broken FK scope/code/group/type identity | 0 | 0 |
| Active unlinked ordinary ADD_ON options | 0 | 0 |
| Enabled COMBO_EGG components / enabled default matches | 2 / 1 | 2 / 1 |
| Enabled COMBO_SIDE components / enabled default matches | 3 / 1 | 3 / 1 |
| Invalid combo component scope/linked-item associations | 0 | 0 |

Both combo groups are enabled, required and EXACTLY_ONE. Defaults are
`combo_tea_egg` and `combo_edamame`. COMBO_EGG `NO_KITCHEN_TASK` components may
intentionally have no linked menu item; enabled COMBO_SIDE linked items are
active and belong to the same Store.

Formal chain remains `menu_item_options.store_addon_id` → `store_addons.id` →
`organization_addon_definitions.code`. The shared effective menu resolver feeds
both Pad ordering and Uber mapping. Production mapping preparation should resolve
item SKU and exact option code/group/parent context through that catalog, then
verify formal Add-on eligibility. No fuzzy names, new catalog or parallel Add-on
identity is required.

## Printing, devices and isolation

| Proof | St-Denis | St-Catherine |
| --- | --- | --- |
| Store printing | enabled / PAD_DIRECT | disabled / DISABLED |
| Enabled same-Store GRAB assignment | printer 3 | none |
| Enabled same-Store HOT_KITCHEN assignment | printer 4 | none |
| Other enabled assignment | FRONTDESK_RECEIPT printer 2 | none |
| Invalid/foreign enabled printer assignments | 0 | 0 |
| Registered active device rows | 10 | 0 |
| Last observed device seen timestamp (backend-local) | `2026-10-21T22:13:48.930741` | none |
| Explicit device heartbeat timestamps | none | none |
| Uber module | disabled | disabled |
| Production Uber binding | none | none |

Device row presence and historical last-seen do not establish current worker
health or physical printing. Device freshness requires the independently verified
backend JVM time zone; database session time zone was America/Toronto and alone
is insufficient evidence. No device heartbeat or physical print was triggered.
St-Catherine has a concrete local printing blocker: no active printing mode,
printer assignments or paired device. This audit does not create/bind any hardware.

All three Production Stores, including Chinatown, retain `UBER_EATS=false`.
Production totals read back as: bindings 0, mapping rules 0, events 0,
integration orders 0, local `external_source=UBER_EATS` orders 0. No existing
Production Uber backlog was found. Retained future receive-only events still need
explicit cutover review; a current zero count is not future authorization.

## Remaining gates

1. Actual granted scopes, `order_webhook` role, Production Signing Key and Store
   Access remain external evidence requirements. Do not infer them from local data.
2. Read each real Store's live Uber stable item/modifier identifiers and parent
   contexts, match them to the verified local identities, and persist/read back
   only reviewed explicit rules. No Production rules were written by this audit.
3. St-Catherine needs separately authorized, verified printing/device preparation.
   St-Denis still needs current device health and controlled pilot evidence.
4. Keep the global worker false until the exact receive-only/cutover/Store
   authorization and real-printing gates are met. Per-Store activation uses the
   reviewed Store cutover ceiling plus formal UBER_EATS module; no implicit replay
   of historical held receipts is authorized.

`LOCAL_ROOT_IDENTITY_PREFLIGHT = PASS_BOTH_STORES`

`PRODUCTION_MENU_MAPPING_PERSISTED = NO`

`ST_CATHERINE_PRINTING_READY = NO`

`PRODUCTION_CHANGED_BY_THIS_AUDIT = NO`
