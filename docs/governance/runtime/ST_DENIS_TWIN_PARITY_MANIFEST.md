# ST_DENIS_TWIN_PARITY_MANIFEST

> Manifest version: `1`
>
> Collection date: `2026-08-10` (America/Toronto)
>
> Collection mode: `PRODUCTION_ST_DENIS_CONFIGURATION_READ_APPROVAL`
>
> Scope: bounded, read-only Production St-Denis inventory and sanitized
> comparison with the retained isolated Staging synthetic baseline. This
> manifest authorizes no Staging reconstruction, Twin synchronization,
> deployment, migration, restart, or business-data write.

## 1. Runtime identity and provenance

| Field | Production | Staging | Provenance |
|---|---|---|---|
| environment | `restaurant-prod` | isolated Staging | `OBSERVED_FROM_PRODUCTION` / `OBSERVED_FROM_STAGING` |
| application commit observed | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` | `1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c` | runtime checkout observation |
| database schema | Flyway V7 | Flyway V10 | bounded Flyway history query |
| migration chain | V1--V7, all successful | V1--V10, all successful | `DERIVED_FROM_REPOSITORY` + runtime history |
| pending/failed migration | none observed in history | none observed in history | read-only Flyway query |
| authoritative health | `/api/v1/system/health` = HTTP 200, `UP` | `/api/v1/system/health` = HTTP 200, `UP` | runtime observation |
| WebSocket probe | `/ws/info` = HTTP 200 | `/ws/info` = HTTP 200 | runtime observation |
| legacy probe | `/api/health` = HTTP 500 with sanitized generic body | same | `EXPECTED_ENVIRONMENT_DIFFERENCE` = `MATCH` route behavior |
| container continuity | db/backend/nginx running; restart count 0 | db/backend/nginx running; restart count 0 | before/after read-only snapshots |

Production images are retained local tags (`restaurant-pos-backend:local`,
`restaurant-pos-frontend:local`, `postgres:16-alpine`). Staging images are
tagged with the exact deployed SHA
(`restaurant-pos-backend:staging-1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`,
`restaurant-pos-frontend:staging-1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`,
`postgres:16-alpine`). Container
IDs, host names, credentials, endpoint values, token hashes, and other secret
material are intentionally omitted; continuity fingerprints are retained in
the evidence report.

Production Nginx proxies API and WebSocket traffic to `backend:8080` and
preserves `$host`. Staging uses the same upstream and preserves `$http_host`
for the loopback/tunnel browser contract. This is an explicit
`EXPECTED_ENVIRONMENT_DIFFERENCE`, not a business-routing mismatch.

The successful Flyway checksum sequence is retained as a non-secret schema
fingerprint. Production V1--V7 checksums are
`431188510,-1546045661,-1713808660,1636049775,-1638580130,-1681894826,625683957`;
Staging has the same V1--V7 sequence followed by V8 `1654406856`, V9
`1828097009`, and V10 `482267873`. No pending or failed history row was
observed in either bounded result.

## 2. Exact Store and Organization identity

The bounded identity cross-check proved one active target, without relying on a
display-name search:

| Field | Observed value |
|---|---|
| Production Store id | `1` |
| Production Store code/name | `4483_R_SAINT_DENIS` / `4483 R. Saint-Denis` |
| Production Organization id/code/name | `1` / `LANZHOU_NOODLES` / `Lanzhou Noodles` |
| Production Store status | `active` |
| Staging synthetic Store id/code/name | `1` / `STG005_SRC_20260809_R01` / `STG005_SRC_20260809_R01` |
| Staging synthetic Organization id/code/name | `1` / `STG005_ORG_20260809_R01` / `STG005_ORG_20260809_R01` |

The Production identity is unique for this bounded read. No alias or inferred
name is used as a substitute for the exact code/name pair. Staging and
Production IDs are environment-local and are not interchangeable.

## 3. Safe configuration inventory

### 3.1 Production menu

Production has 6 active categories and 39 menu-item rows (36 active, 3
inactive/sold-out rows). Category order is:

`SOUP_NOODLE` (Soup Noodle), `FRIED_NOODLE` (Stir-Fried Noodles),
`DRY_NOODLE` (Mixed / Dry / Cold Noodles), `SIDE` (Side Dishes), `FRIED`
(Fried Items), `DRINK` (Drinks).

The complete safe SKU/price/status inventory observed was:

| SKU | price | state | SKU | price | state |
|---|---:|---|---|---:|---|
| traditional_beef_noodle | 16.99 | active | braised_beef_noodle | 14.80 | inactive/sold-out |
| braised_beef_tendon_noodle | 17.99 | active | pickled_vegetable_beef_noodle | 17.99 | active |
| vegetable_noodle | 16.99 | active | tea_egg | 1.99 | active |
| cucumber_salad | 4.99 | active | edamame | 4.99 | active |
| shredded_potato | 4.99 | active | braised_beef_shank_salad | 9.99 | active |
| fried_egg (id 36) | 1.99 | active | fried_egg (id 35) | 0.00 | inactive/sold-out |
| beef | 6.99 | active | tendon | 6.99 | active |
| beef_chow_mein | 18.99 | active | chicken_chow_mein | 18.99 | active |
| tomato_chow_mein | 18.99 | active | vegetable_chow_mein | 18.99 | active |
| cold_noodle_shredded_chicken | 16.99 | active | zha_jiang_noodle | 17.99 | active |
| dan_dan_noodle | 17.99 | active | fried_spring_rolls | 5.99 | active |
| tempura_shrimp | 8.99 | active | fried_steamed_buns | 5.99 | active |
| fried_wontons | 5.99 | active | seven_up | 3.00 | active |
| coke | 3.00 | active | diet_coke | 3.00 | active |
| chinese_herbal_tea | 3.00 | active | ice_tea | 3.00 | active |
| canada_dry | 3.00 | active | shochu_fruit | 24.00 | active |
| lg_sake | 18.00 | active | tsingtao_beer | 8.00 | active |
| sapporo | 9.50 | active | sm_sake | 15.00 | active |
| soju | 20.00 | active | shochu | 18.00 | inactive |
| sake | 18.00 | inactive | — | — | — |

The 380 safe option rows are represented by their concrete option-code
families and row-count fingerprint: `COMBO`, `COMBO_EGG`, `COMBO_SIDE`,
`COMBO_SIDE_REMOVE`, `SIZE`, `NOODLE_TYPE`, `SPICY_LEVEL`, `SOUP_BASE`,
`ADD_ON`, and `REMOVE`. Observed codes include `combo`, `combo_tea_egg`,
`combo_fried_egg`, `combo_edamame`, `combo_shredded_potato`,
`combo_cucumber_salad`, `size_regular`, `size_large`, `noodle_sanxi`,
`noodle_erxi`, `noodle_thin`, `noodle_capillary`, `noodle_leek_leaf`,
`noodle_wide`, `noodle_extra_wide`, `extra_noodle`, `tea_egg`, `extra_meat`,
`fried_egg`, `bok_choy`, `cilantro`, `green_onion`, `extra_radish`,
`remove_cilantro`, `remove_green_onion`, `remove_beef`, `remove_noodle`,
`less_noodle`, and the item-specific vegetable/broth removal codes. Prices,
active flags, ordering, and parent-option relationships are included in the
query fingerprint; no free-text customer/order data was selected.

### 3.2 Production tables, staff, access, flags

- Stations: 5 active rows — `NOODLE`, `WOK`, `COLD`, `DEEPFRIED`, `BAR`.
- Dining tables: 13 active rows. Names are `10`, `11`, `1里`, `7`, `8`, `9`,
  `1外`, `2里`, `2外`, `3`, `4`, `5`, `6`; areas are `Main Hall` except
  `5`/`6` in `Window`. Capacity is 4 where configured; split support is
  false only for `1里` and `2里`.

  Concrete safe table identity pairs are `T12->10`, `T12->11`, `T1->1里`,
  `T9->7`, `T10->8`, `T11->9`, `T2->1外`, `T3->2里`, `T4->2外`, `T5->3`,
  `T6->4`, `T7->5`, and `T8->6`. These IDs are Production-local and must not
  be copied into Staging.
- Staff: one active `owner` user, `OWNER` role, Store id 1.
- Access: one active Organization Owner membership and one active Store Owner
  membership. No role-permission rows were present for `OWNER` in the bounded
  mapping table; this remains `NOT_YET_VERIFIED` at capability level.
- Feature flags: `printing_enabled=true`, `printing_mode=PAD_DIRECT`,
  `enable_bar_kitchen_tasks=false`. Six safe KDS configurations were observed:
  `FRONTDESK_TABLE_BOARD`, `FRONTDESK_MENU`, `KDS_GRAB`,
  `KDS_HOT_KITCHEN`, `KDS_NOODLE_MONITOR`, `PICKUP_BOARD`.

### 3.3 Production printing and devices

Safe printing topology has 4 logical printer configurations and 3 assignments:
`GRAB`, `FRONTDESK_RECEIPT`, and `HOT_KITCHEN`. Only non-secret mode, width,
timeout, encoding, font-size, enabled state, assignment role, and copy count
were selected. Endpoint addresses, ports, error payloads, and credentials
were excluded. Receipt-template rows were empty.

Seven active `ANDROID_PAD` devices were observed with safe fields only:
display name `Restaurant Pad`, platform `ANDROID`, status `ACTIVE`, and
enabled `true`. Device token hashes and identifiers that could authenticate a
device were excluded.

### 3.4 Staging synthetic baseline

Staging has the retained synthetic source only: 4 categories, 13 active items,
38 active options, 3 stations, and 0 dining tables. Categories are
`SOUP_NOODLE`, `DRY_NOODLE`, `SOURCE_SIDE`, `DRINK`; stations are `NOODLE`,
`COLD`, `BAR_SOURCE`. The 13 SKUs are
`traditional_beef_noodle`, `braised_beef_tendon_noodle`, `vegetable_noodle`,
`dan_dan_noodle`, `zha_jiang_noodle`, `braised_beef_shank_salad`,
`cucumber_salad`, `edamame`, `shredded_potato`, `coke`, `diet_coke`,
`ice_tea`, and `chinese_herbal_tea`; all are active synthetic entries.
Option families are the seven noodle choices plus `tea_egg`, `extra_meat`,
and `remove_garlic` (38 rows total). Printing is disabled, there are no
printer assignments or devices, and the synthetic Owner/access topology is
one active `OWNER` membership.

## 4. Domain comparison and reconciliation classes

| Domain | Class | Observed difference | Future reconciliation (not executed) |
|---|---|---|---|
| application/runtime | `EXPECTED_ENVIRONMENT_DIFFERENCE` | Production retained `4667f3c...`; Staging exact `1a3f2e...` | Bind a reviewed exact release before any reconstruction. |
| schema | `BLOCKING_BEHAVIOR_DIFFERENCE` | Production V7 versus Staging V10; no compatibility proof | Owner-approved migration/compatibility decision; do not copy schema or run Flyway in this round. |
| Store/Organization | `SANITIZED_DATA_DIFFERENCE` | Exact code/name/settings differ | Create a Store Profile mapping; never copy Production IDs or credentials. |
| menu | `SANITIZED_DATA_DIFFERENCE` | 6/39/380 versus 4/13/38; option graph differs | Versioned, idempotent profile projection with validator and dry-run diff. |
| tables | `SANITIZED_DATA_DIFFERENCE` | 13 Production tables versus 0 Staging tables | Add synthetic Twin tables only after the reconstruction gate. |
| staff | `EXPECTED_ENVIRONMENT_DIFFERENCE` | `owner` versus `STG005_OWNER_20260808_R01` | Replace identity with a private synthetic credential; preserve role contract. |
| access | `EXPECTED_ENVIRONMENT_DIFFERENCE` | Environment-local IDs; one Owner membership shape in each | Reconcile membership graph by role and Store identity, never by copied IDs. |
| features | `EXPECTED_ENVIRONMENT_DIFFERENCE` | `PAD_DIRECT`/printing enabled versus `DISABLED` | Keep Staging disabled until hardware/secret gate is separately approved. |
| printing | `TEST_HARDWARE_DIFFERENCE` | 4 configs/3 assignments versus none | Use Owner-approved home test endpoints only; do not import Production endpoints. |
| devices | `TEST_HARDWARE_DIFFERENCE` | 7 Production pads versus none | Pair synthetic devices through a separate runtime gate; no token/hash transfer. |
| operational workflows | `NOT_YET_VERIFIED` | Inventory does not prove orders, payments, or customer behavior | Validate with synthetic fixtures only in a later acceptance loop. |
| routing/health | `MATCH` | Authoritative health and WebSocket routes return 200; legacy `/api/health` returns sanitized 500 in both | Keep the repository-authoritative endpoint; no health-contract change is proposed. |

No differences were repaired in this collection. The manifest is an inventory,
not a synchronization request.

## 5. Safety, query, and fingerprint contract

- Query [allowlist](TWIN-001_PRODUCTION_QUERY_ALLOWLIST_V1.md):
  `TWIN001_PRODUCTION_QUERY_ALLOWLIST_V1`, 19 bounded query
  identities reused across environments; 34 read-only SQL invocations were
  executed (18 Production, 16 Staging).
- Every invocation used `transaction_read_only=on`, `statement_timeout=1500ms`,
  `lock_timeout=100ms`, explicit columns, Store scoping, and bounded limits.
- No `SELECT *`; no business SQL for orders, customers, payments, credentials,
  token hashes, or secrets; no PII was retained.
- Before/after continuity snapshots matched for container identity, image,
  start time, restart count, database health, authoritative health, and
  WebSocket health. No runtime mutation occurred.
- Manifest fingerprint (SHA-256 over this document with this line replaced by
  `MANIFEST_FINGERPRINT=UNSET`):
  `MANIFEST_FINGERPRINT=286ccf3e80c7b9fcdf5c92de1429339cff8927ae6bc5788ae2ea2c716abf65ab`.

The companion evidence report records the exact query identities, permitted
columns, runtime continuity observations, sanitized response classification,
and the design-only `TWIN-001_STAGING_RECONSTRUCTION_PLAN`.
