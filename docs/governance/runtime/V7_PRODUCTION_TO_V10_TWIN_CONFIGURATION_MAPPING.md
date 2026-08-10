# V7 Production to V10 Twin Configuration Mapping

> Status: `MAPPING_VALIDATED_READ_ONLY`
>
> Source: manifest v2 `1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68`

Production V7 and Staging V10 share the canonical configuration tables below.
V8--V10 add only request/audit tables; they do not rename or alter a source
configuration field. Production IDs are converted to manifest-local references
and are never target IDs.

| V7 source | Sanitized semantic value | V10 canonical target | Result |
|---|---|---|---|
| `stores.organization_id,code,name,status,enable_bar_kitchen_tasks,printing_enabled,printing_mode,menu_revision,menu_updated_at` | Store identity and operational flags | same `stores` fields | `MAPPED` |
| `menu_categories(id,code,name_zh,name_en,sort_order,is_active)` | `CAT-*` plus concrete fields | same fields; `store_id` is new Twin Store | `MAPPED` |
| `stations(id,code,name,sort_order,is_active)` | `STA-*` plus concrete fields | same fields; `store_id` is new Twin Store | `MAPPED` |
| `menu_items(category_id,station_id,sku,item_type,name_zh,name_en,base_price,cost_per_item,is_active,is_sold_out,sort_order)` | `ITEM-*`, `category_ref`, `station_ref` and concrete values | same fields with mapped target category/station IDs | `MAPPED` |
| `menu_item_options(menu_item_id,option_type,option_code,option_group,parent_option_id,sort_order,name_zh,name_en,price_delta,is_active)` | `OPT-*`, `item_ref`, optional `parent_option_ref` and concrete values | same fields with mapped target item/parent IDs | `MAPPED` |
| `dining_tables(table_code,table_name,area_name,table_config,capacity,supports_split,sort_order,is_active)` | concrete safe table configuration | same fields; new Twin Store ID | `MAPPED` |
| `users(username,status,role_id)` + `roles(code,name)` | username/role parity only | same safe fields; independently created credential | `MAPPED`, credential excluded |
| membership and `user_stations` relations | username/role/station-code topology | same relations with target IDs | `MAPPED` |
| `store_kds_display_configs(screen_code,header_layout,density_mode,card_size_mode,config_json)` | concrete display configuration | same fields; new Twin Store ID | `MAPPED` |
| `printer_configs(name,printer_type,text_encoding,escpos_code_page,font_size,font_size_mode,enabled,paper_width_mm,timeout_ms)` | logical printer topology | same fields; no endpoint or port | `MAPPED`, hardware gate remains |
| `printer_assignments(printer_id,module_code,enabled,font_size,takeout_receipt_copies)` | logical routing via `PRINTER-*` | same fields with target printer ID | `MAPPED` |
| `receipt_templates(template_code,template_name,is_default)` | safe template identity | same fields | `MAPPED` |
| `store_devices(device_name,device_type,platform,app_version,status,is_active)` | `DEVICE-*` safe topology | same fields; no token/hash | `MAPPED`, device gate remains |

Invalid v1 labels are intentionally not mapped: `stores.is_active`,
`roles.status`, `dining_tables.area`, `store_kds_configs`,
`connection_timeout_ms`, `encoding`, `assignment_type`, and `copies` are not
V7/V10 canonical reconstruction fields. Their v2 replacements are the fields
in this table.

`CURRENT_PRODUCTION_VERSION_DIFFERENCE` is limited to V7 versus V10 version
position. It is not a schema mapping failure. Aggregate `SCHEMA` remains
`BLOCKING_BEHAVIOR_DIFFERENCE` until the future reconstructed Twin operates on
V10 and passes parity validation.
