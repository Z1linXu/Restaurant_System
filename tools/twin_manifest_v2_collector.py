#!/usr/bin/env python3
"""Collect the bounded, secret-free St-Denis reconstruction manifest v2.

The collector never reads container environment values.  Its remote command
uses the database container's existing connection variables internally and
returns only the explicit safe configuration projection below.
"""
import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path

STORE_ID = 1
ORG_ID = 1
STORE_CODE = "4483_R_SAINT_DENIS"
STORE_NAME = "4483 R. Saint-Denis"
ORG_CODE = "LANZHOU_NOODLES"

SQL = r'''\pset pager off
BEGIN READ ONLY;
SET LOCAL statement_timeout = '1500ms';
SET LOCAL lock_timeout = '100ms';
SELECT jsonb_build_object(
  'flyway', (SELECT jsonb_agg(jsonb_build_object('installed_rank', installed_rank, 'version', version, 'description', description, 'success', success, 'checksum', checksum) ORDER BY installed_rank) FROM (SELECT installed_rank, version, description, success, checksum FROM flyway_schema_history ORDER BY installed_rank LIMIT 16) f),
  'store', (SELECT jsonb_build_object('organization_id', s.organization_id, 'code', s.code, 'name', s.name, 'status', s.status, 'enable_bar_kitchen_tasks', s.enable_bar_kitchen_tasks, 'printing_enabled', s.printing_enabled, 'printing_mode', s.printing_mode, 'menu_revision', s.menu_revision, 'menu_updated_at', s.menu_updated_at) FROM stores s WHERE s.id = 1 AND s.organization_id = 1 AND s.code = '4483_R_SAINT_DENIS' AND s.name = '4483 R. Saint-Denis' LIMIT 1),
  'organization', (SELECT jsonb_build_object('code', o.code, 'name', o.name, 'status', o.status) FROM organizations o WHERE o.id = 1 AND o.code = 'LANZHOU_NOODLES' LIMIT 1),
  'categories', (SELECT jsonb_agg(jsonb_build_object('id', c.id, 'code', c.code, 'name_zh', c.name_zh, 'name_en', c.name_en, 'sort_order', c.sort_order, 'is_active', c.is_active) ORDER BY c.sort_order, c.id) FROM (SELECT id, code, name_zh, name_en, sort_order, is_active FROM menu_categories WHERE store_id = 1 ORDER BY sort_order, id LIMIT 64) c),
  'stations', (SELECT jsonb_agg(jsonb_build_object('id', s.id, 'code', s.code, 'name', s.name, 'sort_order', s.sort_order, 'is_active', s.is_active) ORDER BY s.sort_order, s.id) FROM (SELECT id, code, name, sort_order, is_active FROM stations WHERE store_id = 1 ORDER BY sort_order, id LIMIT 64) s),
  'items', (SELECT jsonb_agg(jsonb_build_object('id', i.id, 'category_id', i.category_id, 'station_id', i.station_id, 'sku', i.sku, 'item_type', i.item_type, 'name_zh', i.name_zh, 'name_en', i.name_en, 'base_price', i.base_price, 'cost_per_item', i.cost_per_item, 'is_active', i.is_active, 'is_sold_out', i.is_sold_out, 'sort_order', i.sort_order) ORDER BY i.category_id, i.sort_order, i.id) FROM (SELECT id, category_id, station_id, sku, item_type, name_zh, name_en, base_price, cost_per_item, is_active, is_sold_out, sort_order FROM menu_items WHERE store_id = 1 ORDER BY category_id, sort_order, id LIMIT 128) i),
  'options', (SELECT jsonb_agg(jsonb_build_object('id', o.id, 'menu_item_id', o.menu_item_id, 'option_type', o.option_type, 'option_code', o.option_code, 'option_group', o.option_group, 'parent_option_id', o.parent_option_id, 'sort_order', o.sort_order, 'name_zh', o.name_zh, 'name_en', o.name_en, 'price_delta', o.price_delta, 'is_active', o.is_active) ORDER BY o.menu_item_id, o.sort_order NULLS LAST, o.id) FROM (SELECT o.id, o.menu_item_id, o.option_type, o.option_code, o.option_group, o.parent_option_id, o.sort_order, o.name_zh, o.name_en, o.price_delta, o.is_active FROM menu_item_options o JOIN menu_items i ON i.id = o.menu_item_id WHERE i.store_id = 1 ORDER BY o.menu_item_id, o.sort_order NULLS LAST, o.id LIMIT 512) o),
  'tables', (SELECT jsonb_agg(jsonb_build_object('table_code', d.table_code, 'table_name', d.table_name, 'area_name', d.area_name, 'table_config', d.table_config, 'capacity', d.capacity, 'supports_split', d.supports_split, 'sort_order', d.sort_order, 'is_active', d.is_active) ORDER BY d.sort_order, d.id) FROM (SELECT id, table_code, table_name, area_name, table_config, capacity, supports_split, sort_order, is_active FROM dining_tables WHERE store_id = 1 ORDER BY sort_order, id LIMIT 64) d),
  'staff', (SELECT jsonb_agg(jsonb_build_object('username', x.username, 'status', x.status, 'role_code', x.role_code, 'role_name', x.role_name) ORDER BY x.username) FROM (SELECT u.username, u.status, r.code AS role_code, r.name AS role_name FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.store_id = 1 ORDER BY u.username LIMIT 32) x),
  'organization_memberships', (SELECT jsonb_agg(jsonb_build_object('username', x.username, 'role_code', x.role_code, 'role_name', x.role_name, 'is_active', x.is_active) ORDER BY x.username) FROM (SELECT u.username, r.code AS role_code, r.name AS role_name, m.is_active FROM organization_memberships m JOIN users u ON u.id = m.user_id LEFT JOIN roles r ON r.id = m.role_id WHERE m.organization_id = 1 ORDER BY u.username LIMIT 32) x),
  'store_memberships', (SELECT jsonb_agg(jsonb_build_object('username', x.username, 'role_code', x.role_code, 'role_name', x.role_name, 'is_active', x.is_active) ORDER BY x.username) FROM (SELECT u.username, r.code AS role_code, r.name AS role_name, m.is_active FROM store_memberships m JOIN users u ON u.id = m.user_id LEFT JOIN roles r ON r.id = m.role_id WHERE m.store_id = 1 AND m.organization_id = 1 ORDER BY u.username LIMIT 32) x),
  'user_stations', (SELECT jsonb_agg(jsonb_build_object('username', x.username, 'station_code', x.station_code, 'is_primary', x.is_primary, 'is_active', x.is_active) ORDER BY x.username, x.station_code) FROM (SELECT u.username, s.code AS station_code, us.is_primary, us.is_active FROM user_stations us JOIN users u ON u.id = us.user_id JOIN stations s ON s.id = us.station_id WHERE u.store_id = 1 AND s.store_id = 1 ORDER BY u.username, s.code LIMIT 64) x),
  'role_permissions', (SELECT jsonb_agg(jsonb_build_object('role_code', x.role_code, 'role_name', x.role_name, 'feature_package', x.feature_package, 'permission', x.permission, 'capability_code', x.capability_code, 'is_allowed', x.is_allowed) ORDER BY x.role_code, x.feature_package, x.permission, x.capability_code) FROM (SELECT r.code AS role_code, r.name AS role_name, rp.feature_package, rp.permission, rp.capability_code, rp.is_allowed FROM role_permissions rp JOIN roles r ON r.id = rp.role_id WHERE r.code = 'OWNER' ORDER BY r.code, rp.feature_package, rp.permission, rp.capability_code LIMIT 128) x),
  'kds_display_configs', (SELECT jsonb_agg(jsonb_build_object('screen_code', k.screen_code, 'header_layout', k.header_layout, 'density_mode', k.density_mode, 'card_size_mode', k.card_size_mode, 'config_json', k.config_json) ORDER BY k.screen_code, k.id) FROM (SELECT id, screen_code, header_layout, density_mode, card_size_mode, config_json FROM store_kds_display_configs WHERE store_id = 1 ORDER BY screen_code, id LIMIT 32) k),
  'printers', (SELECT jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'printer_type', p.printer_type, 'text_encoding', p.text_encoding, 'escpos_code_page', p.escpos_code_page, 'font_size', p.font_size, 'font_size_mode', p.font_size_mode, 'enabled', p.enabled, 'paper_width_mm', p.paper_width_mm, 'timeout_ms', p.timeout_ms) ORDER BY p.name, p.id) FROM (SELECT id, name, printer_type, text_encoding, escpos_code_page, font_size, font_size_mode, enabled, paper_width_mm, timeout_ms FROM printer_configs WHERE store_id = 1 ORDER BY name, id LIMIT 16) p),
  'printer_assignments', (SELECT jsonb_agg(jsonb_build_object('printer_id', a.printer_id, 'module_code', a.module_code, 'enabled', a.enabled, 'font_size', a.font_size, 'takeout_receipt_copies', a.takeout_receipt_copies) ORDER BY a.module_code, a.id) FROM (SELECT id, printer_id, module_code, enabled, font_size, takeout_receipt_copies FROM printer_assignments WHERE store_id = 1 ORDER BY module_code, id LIMIT 32) a),
  'receipt_templates', (SELECT jsonb_agg(jsonb_build_object('template_code', r.template_code, 'template_name', r.template_name, 'is_default', r.is_default) ORDER BY r.template_code, r.id) FROM (SELECT id, template_code, template_name, is_default FROM receipt_templates WHERE store_id = 1 ORDER BY template_code, id LIMIT 32) r),
  'devices', (SELECT jsonb_agg(jsonb_build_object('device_name', d.device_name, 'device_type', d.device_type, 'platform', d.platform, 'app_version', d.app_version, 'status', d.status, 'is_active', d.is_active) ORDER BY d.device_name, d.device_type, d.platform, d.id) FROM (SELECT id, device_name, device_type, platform, app_version, status, is_active FROM store_devices WHERE store_id = 1 AND organization_id = 1 ORDER BY device_name, device_type, platform, id LIMIT 32) d)
);
COMMIT;
'''

STAGING_SQL = r'''\pset pager off
BEGIN READ ONLY;
SET LOCAL statement_timeout = '1500ms';
SET LOCAL lock_timeout = '100ms';
SELECT jsonb_build_object(
  'flyway', (SELECT jsonb_agg(jsonb_build_object('version', version, 'success', success) ORDER BY installed_rank) FROM (SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank LIMIT 16) f),
  'store', (SELECT jsonb_build_object('code', code, 'status', status, 'printing_enabled', printing_enabled, 'printing_mode', printing_mode) FROM stores WHERE id = 1 LIMIT 1),
  'counts', jsonb_build_object('categories',(SELECT count(*) FROM menu_categories WHERE store_id = 1),'stations',(SELECT count(*) FROM stations WHERE store_id = 1),'items',(SELECT count(*) FROM menu_items WHERE store_id = 1),'options',(SELECT count(*) FROM menu_item_options o JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id = 1),'tables',(SELECT count(*) FROM dining_tables WHERE store_id = 1),'printers',(SELECT count(*) FROM printer_configs WHERE store_id = 1),'assignments',(SELECT count(*) FROM printer_assignments WHERE store_id = 1),'devices',(SELECT count(*) FROM store_devices WHERE store_id = 1))
);
COMMIT;
'''

FORBIDDEN = ("select *", "customers", "orders", "payments", "user_credentials", "password_hash", "device_token_hash", "ip_address", " port", "refresh_tokens")
REQUIRED_COLUMNS = {
    "stores": {"organization_id", "code", "name", "status", "enable_bar_kitchen_tasks", "printing_enabled", "printing_mode", "menu_revision", "menu_updated_at"},
    "menu_categories": {"store_id", "code", "name_zh", "name_en", "sort_order", "is_active"},
    "stations": {"store_id", "code", "name", "sort_order", "is_active"},
    "menu_items": {"store_id", "category_id", "station_id", "sku", "item_type", "name_zh", "name_en", "base_price", "cost_per_item", "is_active", "is_sold_out", "sort_order"},
    "menu_item_options": {"menu_item_id", "option_type", "option_code", "option_group", "parent_option_id", "sort_order", "name_zh", "name_en", "price_delta", "is_active"},
    "dining_tables": {"store_id", "table_code", "table_name", "area_name", "table_config", "capacity", "supports_split", "sort_order", "is_active"},
    "store_kds_display_configs": {"store_id", "screen_code", "header_layout", "density_mode", "card_size_mode", "config_json"},
    "printer_configs": {"store_id", "name", "printer_type", "text_encoding", "escpos_code_page", "font_size", "font_size_mode", "enabled", "paper_width_mm", "timeout_ms"},
    "printer_assignments": {"store_id", "printer_id", "module_code", "enabled", "font_size", "takeout_receipt_copies"},
    "store_devices": {"organization_id", "store_id", "device_name", "device_type", "platform", "app_version", "status", "is_active"},
    "organizations": {"code", "name", "status"},
    "users": {"store_id", "role_id", "username", "status"},
    "roles": {"code", "name"},
    "organization_memberships": {"organization_id", "user_id", "role_id", "role_code", "is_active"},
    "store_memberships": {"organization_id", "store_id", "user_id", "role_id", "role_code", "is_active"},
    "user_stations": {"user_id", "station_id", "is_primary", "is_active"},
    "role_permissions": {"role_id", "feature_package", "permission", "capability_code", "is_allowed"},
    "receipt_templates": {"store_id", "template_code", "template_name", "is_default"},
}

def command():
    return ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=10", "restaurant-prod", "docker exec -i cloud-db-1 sh -c 'exec psql -X -q -t -A -v ON_ERROR_STOP=1 -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\"'"]

def staging_command():
    return ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=10", "restaurant-prod", "docker exec -i restaurant-pos-staging-db-1 sh -c 'exec psql -X -q -t -A -v ON_ERROR_STOP=1 -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\"'"]

def require(condition, message):
    if not condition:
        raise ValueError(message)

def refs(rows, prefix):
    return {row["id"]: f"{prefix}-{index:03d}" for index, row in enumerate(rows or [], 1)}

def normalize(raw):
    require(raw["store"] and raw["organization"], "exact Store/Organization identity missing")
    require(raw["store"]["organization_id"] == ORG_ID and raw["store"]["code"] == STORE_CODE and raw["store"]["name"] == STORE_NAME, "Store identity mismatch")
    require(raw["organization"]["code"] == ORG_CODE, "Organization identity mismatch")
    categories, stations, items, options, printers = (raw[key] or [] for key in ("categories", "stations", "items", "options", "printers"))
    cat_refs, station_refs, item_refs, option_refs, printer_refs = refs(categories, "CAT"), refs(stations, "STA"), refs(items, "ITEM"), refs(options, "OPT"), refs(printers, "PRINTER")
    for item in items:
        require(item["category_id"] in cat_refs and item["station_id"] in station_refs, "item references unknown category/station")
    for option in options:
        require(option["menu_item_id"] in item_refs and (option["parent_option_id"] is None or option["parent_option_id"] in option_refs), "option references unknown item/parent")
    for assignment in raw["printer_assignments"] or []:
        require(assignment["printer_id"] in printer_refs, "printer assignment references unknown printer")
    def project(rows, mapping, drop=("id",)):
        return [dict({"source_ref": mapping[row["id"]]}, **{k:v for k,v in row.items() if k not in drop}) for row in rows]
    manifest = {
        "manifest": {"name": "ST_DENIS_TWIN_PARITY_MANIFEST", "version": 2, "collection_mode": "TWIN-001_RECONSTRUCTION_MANIFEST_COMPLETION_READ_APPROVAL", "provenance": "OBSERVED_FROM_PRODUCTION", "source_schema": "Flyway V7", "target_schema": "Flyway V10"},
        "identity": {"production_store_ref": "PROD_STORE", "organization_ref": "PROD_ORGANIZATION", "store": {k:v for k,v in raw["store"].items() if k != "organization_id"}, "organization": raw["organization"]},
        "field_provenance": {
            "identity.store,identity.organization,flyway_history,categories,stations,items,options,tables,staff,organization_memberships,store_memberships,user_stations,role_permissions,kds_display_configs,printers,printer_assignments,receipt_templates,devices": "OBSERVED_FROM_PRODUCTION",
            "source_schema_contract.target_contract,source_schema_contract.required_columns": "DERIVED_FROM_REPOSITORY",
            "staging_comparison": "OBSERVED_FROM_STAGING",
            "synthetic_credentials,test_printer_endpoints,test_device_tokens": "EXPECTED_ENVIRONMENT_DIFFERENCE"
        },
        "source_schema_contract": {"validated_by": "successful explicit-column V7 query execution", "target_contract": "repository V10 entities/migrations", "required_columns": {table: sorted(columns) for table, columns in REQUIRED_COLUMNS.items()}}, "flyway_history": raw["flyway"],
        "categories": project(categories, cat_refs), "stations": project(stations, station_refs),
        "items": [dict({"source_ref": item_refs[row["id"]], "category_ref": cat_refs[row["category_id"]], "station_ref": station_refs[row["station_id"]]}, **{k:v for k,v in row.items() if k not in ("id", "category_id", "station_id")}) for row in items],
        "options": [dict({"source_ref": option_refs[row["id"]], "item_ref": item_refs[row["menu_item_id"]], "parent_option_ref": option_refs.get(row["parent_option_id"])}, **{k:v for k,v in row.items() if k not in ("id", "menu_item_id", "parent_option_id")}) for row in options],
        "tables": raw["tables"] or [], "staff": raw["staff"] or [], "organization_memberships": raw["organization_memberships"] or [], "store_memberships": raw["store_memberships"] or [], "user_stations": raw["user_stations"] or [], "role_permissions": raw["role_permissions"] or [], "kds_display_configs": raw["kds_display_configs"] or [],
        "printers": project(printers, printer_refs),
        "printer_assignments": [dict({"printer_ref": printer_refs[row["printer_id"]]}, **{k:v for k,v in row.items() if k != "printer_id"}) for row in raw["printer_assignments"] or []],
        "receipt_templates": raw["receipt_templates"] or [], "devices": [dict({"source_ref": f"DEVICE-{i:03d}"}, **row) for i,row in enumerate(raw["devices"] or [], 1)],
        "staging_comparison": raw["staging_comparison"],
        "collection_safety": {"transaction_read_only": True, "statement_timeout_ms": 1500, "lock_timeout_ms": 100, "explicit_columns_only": True, "staging_access": "READ_ONLY_COMPARISON_PERFORMED", "production_write": "NOT_PERFORMED"}
    }
    body = json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    manifest["manifest"]["fingerprint_sha256"] = hashlib.sha256(body.encode()).hexdigest()
    return manifest

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--output", type=Path, default=Path("docs/governance/runtime/ST_DENIS_TWIN_PARITY_MANIFEST_V2.json"))
    args = parser.parse_args()
    require(not any(term in SQL.lower() for term in FORBIDDEN), "collector contains prohibited query surface")
    if not args.execute:
        print("PLAN_OK|two read-only transactions|explicit columns|no runtime access in plan mode")
        return
    result = subprocess.run(command(), input=SQL, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("Production collector SQL failed: " + result.stderr.strip())
    raw = json.loads(result.stdout.strip())
    staging = subprocess.run(staging_command(), input=STAGING_SQL, text=True, capture_output=True)
    if staging.returncode:
        raise RuntimeError("Staging comparison SQL failed: " + staging.stderr.strip())
    raw["staging_comparison"] = json.loads(staging.stdout.strip())
    manifest = normalize(raw)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"COLLECTED|categories={len(manifest['categories'])}|stations={len(manifest['stations'])}|items={len(manifest['items'])}|options={len(manifest['options'])}|fingerprint={manifest['manifest']['fingerprint_sha256']}")

if __name__ == "__main__":
    main()
