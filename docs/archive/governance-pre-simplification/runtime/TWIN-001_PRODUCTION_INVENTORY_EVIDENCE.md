# TWIN-001 Production St-Denis Inventory Evidence

> Evidence status: `INVENTORY_COMPLETE_WAITING_FOR_RECONSTRUCTION_APPROVAL`
>
> Collection mode: `PRODUCTION_ST_DENIS_CONFIGURATION_READ_APPROVAL`
>
> Date: `2026-08-10` (America/Toronto)

## Scope and authorization

This report records the bounded read-only Production configuration inventory
authorized for TWIN-001. It does not authorize Staging reconstruction, Twin
sync, Production deployment, restart, Flyway, or any business-data mutation.
The exact Store/Organization identity was proven before domain queries:
Production Store `1 / 4483_R_SAINT_DENIS / 4483 R. Saint-Denis` inside
Organization `1 / LANZHOU_NOODLES / Lanzhou Noodles`.

## Ground Truth

| Item | Observed value | Classification |
|---|---|---|
| repository `origin/main` | `34ef8c577dd5e8464ef885bf235b0bece0018503` | `IN_MAIN` |
| Production runtime checkout | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` | `DEPLOYED_TO_PRODUCTION` retained runtime |
| Staging runtime | `1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c` | `DEPLOYED_TO_STAGING` |
| Production Flyway | V7 | observed, historical retained runtime |
| Staging Flyway | V10, no pending/failed history rows | observed |
| Production/Staging printing | `PAD_DIRECT`/enabled versus `DISABLED` | expected environment boundary |
| next stop after this evidence | `TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL` | active governance state |

## Query allowlist and prohibited-data proof

The [allowlist](TWIN-001_PRODUCTION_QUERY_ALLOWLIST_V1.md) was established
before the first business SQL as `TWIN001_PRODUCTION_QUERY_ALLOWLIST_V1`. It
contains 19 bounded identities:
`Q-FLYWAY-PROD`, `Q-STORE-CANDIDATE`, `Q-STORE-ID-CANDIDATE`,
`Q-STAGING-STORE-CANDIDATE`, `Q-STORE-ORG`, `Q-MENU-CATEGORIES`,
`Q-MENU-ITEMS`, `Q-MENU-OPTIONS`, `Q-STATIONS`, `Q-TABLES`, `Q-STAFF`,
`Q-ORG-ACCESS`, `Q-STORE-ACCESS`, `Q-ROLE-PERMISSIONS`, `Q-FEATURES`,
`Q-PRINTER-CONFIG`, `Q-PRINTER-ASSIGNMENTS`, `Q-RECEIPT-TEMPLATES`, and
`Q-DEVICES`. The same identity is reused only when the environment is
explicitly selected and the Store predicate is preserved.

Executed statements were bounded to the following safe tables and columns:

| Domain | Tables/columns read |
|---|---|
| Store/org | `stores(id,organization_id,code,name,status,is_active,printing_enabled,printing_mode,enable_bar_kitchen_tasks,created_at)`, `organizations(id,code,name,status)` |
| Menu | `menu_categories(id,store_id,code,name_zh,name_en,sort_order,is_active)`, `menu_items(id,store_id,category_id,station_id,sku,item_type,name_zh,name_en,base_price,is_active,is_sold_out,sort_order)`, `menu_item_options(menu_item_id,option_type,option_group,option_code,parent_option_id,name_zh,name_en,price_delta,sort_order,is_active)` |
| Tables/staff/access | `stations(id,store_id,code,name,sort_order,is_active)`, `dining_tables(id,store_id,table_name,area,capacity,sort_order,is_active)`, `users(id,username,status,store_id)`, `roles(id,code,status)`, `organization_memberships(user_id,organization_id,role_id,status)`, `store_memberships(user_id,store_id,role_id,status)`, `role_permissions(role_id,permission_id)` |
| Flags/printing/devices | `store_kds_configs(store_id,screen_code,layout_mode,display_density,is_active)`, `printer_configs(id,store_id,name,printer_type,enabled,paper_width_mm,connection_timeout_ms,encoding,font_size_mode)`, `printer_assignments(id,store_id,printer_id,assignment_type,enabled,font_size_mode,copies)`, `receipt_templates(id,store_id,template_code,is_active)`, `store_devices(id,store_id,device_name,device_type,platform,status,is_active,last_seen_at)` |
| Schema | `flyway_schema_history(installed_rank,version,description,success,checksum)` |

No business orders, customers, payments, credential values, password fields,
device token hashes, endpoint secrets, or unrestricted rows were selected.
No `SELECT *` was used. No write statement, migration, restart, or deployment
occurred.

## Inventory and comparison

The complete sanitized inventory and concrete SKU/category/option families are
in [ST_DENIS_TWIN_PARITY_MANIFEST](ST_DENIS_TWIN_PARITY_MANIFEST.md). The
domain classes are: application `EXPECTED_ENVIRONMENT_DIFFERENCE`, schema
`BLOCKING_BEHAVIOR_DIFFERENCE`, Store/menu/tables `SANITIZED_DATA_DIFFERENCE`,
staff/access/features `EXPECTED_ENVIRONMENT_DIFFERENCE`, printing/devices
`TEST_HARDWARE_DIFFERENCE`, operational workflows `NOT_YET_VERIFIED`, and
authoritative routing `MATCH`. These are observations only; no reconciliation
was attempted.

## Continuity and stop state

Production `cloud-db-1`, `cloud-backend-1`, and `cloud-nginx-1` remained
running with unchanged start/restart observations; the database remained
healthy. Staging database/backend/nginx likewise remained running with
unchanged start/restart observations; authoritative health remained 200 and
WebSocket health remained 200. The legacy `/api/health` 500 body was retained
only as a sanitized shared-route observation.

The current stop is
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
The next Owner Gate is
`TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`. The design-only reconstruction
plan may be reviewed and refined; execution remains prohibited until that
gate.
