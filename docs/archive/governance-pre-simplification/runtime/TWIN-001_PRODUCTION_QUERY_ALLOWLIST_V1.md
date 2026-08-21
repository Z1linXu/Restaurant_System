# TWIN001_PRODUCTION_QUERY_ALLOWLIST_V1

> Created before the first business SQL: 2026-08-10 America/Toronto
>
> Execution contract: read-only transaction, `statement_timeout=1500ms`,
> `lock_timeout=100ms`, explicit columns, bounded result, exact Store
> predicate where applicable. No `SELECT *`, write, migration, or broad dump.

This allowlist is the query identity referenced by the
[inventory evidence](TWIN-001_PRODUCTION_INVENTORY_EVIDENCE.md). The same
identity may be reused for Staging only when the environment and Store scope
are explicit. The allowlist excludes customers, orders, payments, credentials,
passwords, token hashes, endpoint secrets, and unrelated Organizations/Stores.

| ID | Domain | Tables and explicit columns | Predicate/bound | Sensitivity |
|---|---|---|---|---|
| `Q-FLYWAY-PROD` | schema | `flyway_schema_history(installed_rank,version,description,success,checksum)` | ordered history; bounded current rows | schema metadata |
| `Q-STORE-CANDIDATE` | identity | `stores(id,code,name,status)` | bounded name candidate, `LIMIT 2`; not accepted as identity | Store identity |
| `Q-STORE-ID-CANDIDATE` | identity | `stores(id,code,name,status,organization_id)` | explicit `id=1`, `LIMIT 1`, cross-check only | Store identity |
| `Q-STAGING-STORE-CANDIDATE` | identity | `stores(id,code,name,status,organization_id)` | synthetic-code prefix, ordered, `LIMIT 20` | synthetic identity |
| `Q-STORE-ORG` | Store/org | `stores(id,organization_id,code,name,status,is_active,printing_enabled,printing_mode,enable_bar_kitchen_tasks,created_at)`; `organizations(id,code,name,status)` | exact verified Store id, `LIMIT 1` | Store configuration |
| `Q-MENU-CATEGORIES` | menu | `menu_categories(id,store_id,code,name_zh,name_en,sort_order,is_active)` | exact Store id, ordered, bounded | menu configuration |
| `Q-MENU-ITEMS` | menu | `menu_items(id,store_id,category_id,station_id,sku,item_type,name_zh,name_en,base_price,is_active,is_sold_out,sort_order)` | exact Store id, ordered, bounded | menu configuration |
| `Q-MENU-OPTIONS` | menu | `menu_item_options(menu_item_id,option_type,option_group,option_code,parent_option_id,name_zh,name_en,price_delta,sort_order,is_active)` | join to exact Store menu items, ordered, bounded | menu configuration |
| `Q-STATIONS` | tables | `stations(id,store_id,code,name,sort_order,is_active)` | exact Store id, ordered, bounded | station configuration |
| `Q-TABLES` | tables | `dining_tables(id,store_id,table_name,area,capacity,sort_order,is_active)` | exact Store id, ordered, bounded | table configuration |
| `Q-STAFF` | staff | `users(id,username,status,store_id)`; `roles(id,code,status)` | exact Store id and role join, bounded | username/role only |
| `Q-ORG-ACCESS` | access | `organization_memberships(user_id,organization_id,role_id,status)`; role identity | exact target Organization, bounded | membership topology |
| `Q-STORE-ACCESS` | access | `store_memberships(user_id,store_id,role_id,status)`; role identity | exact target Store, bounded | membership topology |
| `Q-ROLE-PERMISSIONS` | access | `role_permissions(role_id,permission_id)` | exact `OWNER` role, bounded | permission mapping |
| `Q-FEATURES` | features | `store_kds_configs(store_id,screen_code,layout_mode,display_density,is_active)` plus safe Store flags | exact Store id, bounded | feature/config flags |
| `Q-PRINTER-CONFIG` | printing | `printer_configs(id,store_id,name,printer_type,enabled,paper_width_mm,connection_timeout_ms,encoding,font_size_mode)` | exact Store id; no endpoint/credential fields | logical hardware topology |
| `Q-PRINTER-ASSIGNMENTS` | printing | `printer_assignments(id,store_id,printer_id,assignment_type,enabled,font_size_mode,copies)` | exact Store id, bounded | logical assignments |
| `Q-RECEIPT-TEMPLATES` | printing | `receipt_templates(id,store_id,template_code,is_active)` | exact Store id, bounded | template identity |
| `Q-DEVICES` | devices | `store_devices(id,store_id,device_name,device_type,platform,status,is_active,last_seen_at)` | exact Store id; token hash excluded | safe device topology |

Every query result was sanitized before evidence retention. Query identity,
environment, purpose, bound, and sensitivity were retained; raw SQL output is
not retained when it could expose operational identifiers or secrets.
