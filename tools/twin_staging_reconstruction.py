#!/usr/bin/env python3
"""Project the reviewed St-Denis manifest v2 onto isolated Staging.

The tool has three modes:

* ``plan`` reads only the bounded, explicit-column Staging configuration;
* ``apply`` accepts only the exact retained STG-005 baseline (or an already
  reconstructed graph), then performs one Store-scoped transaction;
* ``validate`` reads only the same safe configuration and proves manifest
  parity without inspecting credentials, tokens, orders, payments or PII.

Production is never contacted.  The manifest remains the only
Production-derived input.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from collections import Counter
from decimal import Decimal
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "docs/governance/runtime/ST_DENIS_TWIN_PARITY_MANIFEST_V2.json"
EXPECTED_MANIFEST_FINGERPRINT = "1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68"
STORE_ID = 1
ORGANIZATION_ID = 1
STAGING_STORE_CODE = "STG005_SRC_20260809_R01"
STAGING_ORGANIZATION_CODE = "STG005_ORG_20260809_R01"
STAGING_OWNER_USERNAME = "STG005_OWNER_20260808_R01"


SNAPSHOT_SQL = r"""
\pset pager off
BEGIN READ ONLY;
SET LOCAL statement_timeout = '3000ms';
SET LOCAL lock_timeout = '100ms';
SELECT jsonb_build_object(
  'flyway', (SELECT jsonb_agg(jsonb_build_object('version', version, 'success', success) ORDER BY installed_rank) FROM (SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank LIMIT 16) f),
  'organization', (SELECT jsonb_build_object('id', id, 'code', code, 'name', name, 'status', status) FROM organizations WHERE id = 1 AND code = 'STG005_ORG_20260809_R01'),
  'store', (SELECT jsonb_build_object('id', id, 'organization_id', organization_id, 'code', code, 'name', name, 'status', status, 'enable_bar_kitchen_tasks', enable_bar_kitchen_tasks, 'printing_enabled', printing_enabled, 'printing_mode', printing_mode, 'menu_revision', menu_revision, 'menu_updated_at', menu_updated_at) FROM stores WHERE id = 1 AND organization_id = 1 AND code = 'STG005_SRC_20260809_R01'),
  'categories', (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', id, 'code', code, 'name_zh', name_zh, 'name_en', name_en, 'sort_order', sort_order, 'is_active', is_active) ORDER BY id), '[]'::jsonb) FROM menu_categories WHERE store_id = 1),
  'stations', (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', id, 'code', code, 'name', name, 'sort_order', sort_order, 'is_active', is_active) ORDER BY id), '[]'::jsonb) FROM stations WHERE store_id = 1),
  'items', (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', i.id, 'category_code', c.code, 'station_code', s.code, 'sku', i.sku, 'item_type', i.item_type, 'name_zh', i.name_zh, 'name_en', i.name_en, 'base_price', i.base_price, 'cost_per_item', i.cost_per_item, 'is_active', i.is_active, 'is_sold_out', i.is_sold_out, 'sort_order', i.sort_order) ORDER BY i.id), '[]'::jsonb) FROM menu_items i JOIN menu_categories c ON c.id=i.category_id AND c.store_id=i.store_id JOIN stations s ON s.id=i.station_id AND s.store_id=i.store_id WHERE i.store_id = 1),
  'options', (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', o.id, 'item_id', i.id, 'option_type', o.option_type, 'option_code', o.option_code, 'option_group', o.option_group, 'parent_option_id', o.parent_option_id, 'sort_order', o.sort_order, 'name_zh', o.name_zh, 'name_en', o.name_en, 'price_delta', o.price_delta, 'is_active', o.is_active) ORDER BY o.id), '[]'::jsonb) FROM menu_item_options o JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id = 1),
  'tables', (SELECT COALESCE(jsonb_agg(jsonb_build_object('table_code', table_code, 'table_name', table_name, 'area_name', area_name, 'table_config', table_config, 'capacity', capacity, 'supports_split', supports_split, 'sort_order', sort_order, 'is_active', is_active) ORDER BY id), '[]'::jsonb) FROM dining_tables WHERE store_id = 1),
  'staff', (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', u.id, 'username', u.username, 'status', u.status, 'role_code', r.code, 'role_name', r.name) ORDER BY u.username), '[]'::jsonb) FROM users u JOIN roles r ON r.id=u.role_id WHERE u.store_id = 1),
  'organization_memberships', (SELECT COALESCE(jsonb_agg(jsonb_build_object('username', u.username, 'role_code', m.role_code, 'role_name', r.name, 'is_active', m.is_active) ORDER BY u.username), '[]'::jsonb) FROM organization_memberships m JOIN users u ON u.id=m.user_id JOIN roles r ON r.id=m.role_id WHERE m.organization_id = 1),
  'store_memberships', (SELECT COALESCE(jsonb_agg(jsonb_build_object('username', u.username, 'role_code', m.role_code, 'role_name', r.name, 'is_active', m.is_active) ORDER BY u.username), '[]'::jsonb) FROM store_memberships m JOIN users u ON u.id=m.user_id JOIN roles r ON r.id=m.role_id WHERE m.organization_id = 1 AND m.store_id = 1),
  'user_stations', (SELECT COALESCE(jsonb_agg(jsonb_build_object('username', u.username, 'station_code', s.code, 'is_primary', us.is_primary, 'is_active', us.is_active) ORDER BY u.username, s.code), '[]'::jsonb) FROM user_stations us JOIN users u ON u.id=us.user_id JOIN stations s ON s.id=us.station_id WHERE u.store_id=1 AND s.store_id=1),
  'role_permissions', (SELECT COALESCE(jsonb_agg(jsonb_build_object('role_code', r.code, 'role_name', r.name, 'feature_package', rp.feature_package, 'permission', rp.permission, 'capability_code', rp.capability_code, 'is_allowed', rp.is_allowed) ORDER BY r.code, rp.feature_package, rp.permission, rp.capability_code), '[]'::jsonb) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id WHERE r.code='OWNER'),
  'kds_display_configs', (SELECT COALESCE(jsonb_agg(jsonb_build_object('screen_code', screen_code, 'header_layout', header_layout, 'density_mode', density_mode, 'card_size_mode', card_size_mode, 'config_json', config_json) ORDER BY id), '[]'::jsonb) FROM store_kds_display_configs WHERE store_id=1),
  'printers', (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', id, 'name', name, 'printer_type', printer_type, 'text_encoding', text_encoding, 'escpos_code_page', escpos_code_page, 'font_size', font_size, 'font_size_mode', font_size_mode, 'enabled', enabled, 'paper_width_mm', paper_width_mm, 'timeout_ms', timeout_ms, 'endpoint_configured', (ip_address IS NOT NULL OR port IS NOT NULL)) ORDER BY id), '[]'::jsonb) FROM printer_configs WHERE store_id=1),
  'printer_assignments', (SELECT COALESCE(jsonb_agg(jsonb_build_object('printer_id', printer_id, 'module_code', module_code, 'enabled', enabled, 'font_size', font_size, 'takeout_receipt_copies', takeout_receipt_copies) ORDER BY id), '[]'::jsonb) FROM printer_assignments WHERE store_id=1),
  'receipt_templates', (SELECT COALESCE(jsonb_agg(jsonb_build_object('template_code', template_code, 'template_name', template_name, 'is_default', is_default) ORDER BY id), '[]'::jsonb) FROM receipt_templates WHERE store_id=1),
  'devices', (SELECT COALESCE(jsonb_agg(jsonb_build_object('device_name', device_name, 'device_type', device_type, 'platform', platform, 'app_version', app_version, 'status', status, 'is_active', is_active) ORDER BY id), '[]'::jsonb) FROM store_devices WHERE store_id=1 AND organization_id=1),
  'device_credentials_present', (SELECT count(*) FROM store_devices WHERE store_id=1 AND organization_id=1 AND device_token_hash IS NOT NULL)
);
COMMIT;
"""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def load_manifest(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"), parse_float=Decimal)
    reported = value["manifest"].pop("fingerprint_sha256")
    # Decimal JSON rendering differs from the collector; recompute from the raw
    # artifact for the authoritative fingerprint instead.
    raw = json.loads(path.read_text(encoding="utf-8"))
    raw_reported = raw["manifest"].pop("fingerprint_sha256")
    raw_body = json.dumps(raw, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    require(raw_reported == hashlib.sha256(raw_body.encode()).hexdigest(), "manifest fingerprint mismatch")
    require(reported == EXPECTED_MANIFEST_FINGERPRINT, "unexpected manifest identity")
    value["manifest"]["fingerprint_sha256"] = reported
    validate_manifest(value)
    return value


def validate_manifest(value: dict) -> None:
    expected_counts = {
        "categories": 6, "stations": 5, "items": 39, "options": 380,
        "tables": 13, "staff": 4, "kds_display_configs": 6,
        "printers": 4, "printer_assignments": 3, "devices": 7,
    }
    for key, count in expected_counts.items():
        require(len(value[key]) == count, f"manifest {key} count mismatch")
    refs = {row["source_ref"] for row in value["items"]}
    option_refs = {row["source_ref"] for row in value["options"]}
    require(len(refs) == 39 and len(option_refs) == 380, "manifest refs are not unique")
    require(all(row["item_ref"] in refs for row in value["options"]), "option item ref mismatch")
    require(all(row["parent_option_ref"] in option_refs for row in value["options"] if row["parent_option_ref"]), "option parent ref mismatch")
    non_null_option_keys = [(row["item_ref"], row["option_code"]) for row in value["options"] if row["option_code"] is not None]
    require(len(non_null_option_keys) == len(set(non_null_option_keys)), "option codes are ambiguous within an item")
    null_signatures = [option_signature(row) for row in value["options"] if row["option_code"] is None]
    require(len(null_signatures) == len(set(null_signatures)), "legacy null-code options are ambiguous")


def psql_command() -> list[str]:
    return [
        "ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=10", "restaurant-prod",
        "docker exec -i restaurant-pos-staging-db-1 sh -c 'exec psql -X -q -t -A -v ON_ERROR_STOP=1 -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\"'",
    ]


def run_sql(sql: str) -> str:
    result = subprocess.run(psql_command(), input=sql, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("Staging SQL failed: " + result.stderr.strip())
    return result.stdout.strip()


def snapshot() -> dict:
    raw = run_sql(SNAPSHOT_SQL)
    require(bool(raw), "Staging snapshot returned no result")
    return json.loads(raw)


def normalized_money(value):
    return None if value is None else f"{Decimal(str(value)):.2f}"


def without(row: dict, *keys: str) -> dict:
    return {key: value for key, value in row.items() if key not in keys}


def canonical_counter(rows: list[dict]) -> Counter:
    return Counter(json.dumps(row, ensure_ascii=False, sort_keys=True, separators=(",", ":")) for row in rows)


def option_signature(row: dict) -> tuple:
    return (
        row.get("item_ref"), row.get("option_type"), row.get("option_code"),
        row.get("option_group"), row.get("sort_order"), row.get("name_zh"),
        row.get("name_en"), normalized_money(row.get("price_delta")),
        bool(row.get("is_active")),
    )


def baseline_contract() -> dict:
    """Return the complete reviewed STG-005 synthetic baseline contract."""
    categories = [
        {"code": code, "name_zh": f"STG005_{code}", "name_en": f"STG005_{code}", "sort_order": order, "is_active": True}
        for code, order in (("SOUP_NOODLE", 1), ("DRY_NOODLE", 2), ("SOURCE_SIDE", 3), ("DRINK", 4))
    ]
    stations = [
        {"code": code, "name": f"STG005_{code}", "sort_order": order, "is_active": True}
        for code, order in (("NOODLE", 1), ("COLD", 2), ("BAR_SOURCE", 3))
    ]
    item_specs = (
        ("traditional_beef_noodle", "SOUP_NOODLE", "NOODLE", 1),
        ("braised_beef_tendon_noodle", "SOUP_NOODLE", "NOODLE", 2),
        ("vegetable_noodle", "SOUP_NOODLE", "NOODLE", 3),
        ("dan_dan_noodle", "DRY_NOODLE", "NOODLE", 1),
        ("zha_jiang_noodle", "DRY_NOODLE", "NOODLE", 2),
        ("braised_beef_shank_salad", "SOURCE_SIDE", "COLD", 1),
        ("cucumber_salad", "SOURCE_SIDE", "COLD", 2),
        ("edamame", "SOURCE_SIDE", "COLD", 3),
        ("shredded_potato", "SOURCE_SIDE", "COLD", 4),
        ("coke", "DRINK", "BAR_SOURCE", 1),
        ("diet_coke", "DRINK", "BAR_SOURCE", 2),
        ("ice_tea", "DRINK", "BAR_SOURCE", 3),
        ("chinese_herbal_tea", "DRINK", "BAR_SOURCE", 4),
    )
    items = [
        {
            "category_code": category, "station_code": station, "sku": sku,
            "item_type": "menu_item", "name_zh": f"STG005_{sku}",
            "name_en": f"STG005_{sku}", "base_price": "10.00",
            "cost_per_item": "1.00", "is_active": True,
            "is_sold_out": False, "sort_order": order,
        }
        for sku, category, station, order in item_specs
    ]
    noodle_codes = (
        "noodle_capillary", "noodle_thin", "noodle_sanxi", "noodle_erxi",
        "noodle_leek_leaf", "noodle_wide", "noodle_extra_wide",
    )
    options = []
    for sku in ("traditional_beef_noodle", "braised_beef_tendon_noodle", "vegetable_noodle", "dan_dan_noodle", "zha_jiang_noodle"):
        for order, code in enumerate(noodle_codes, 1):
            options.append({
                "item_sku": sku, "option_type": "noodle_type", "option_code": code,
                "option_group": "NOODLE_TYPE", "parent_option_code": None,
                "sort_order": order, "name_zh": f"STG005_{code}",
                "name_en": f"STG005_{code}", "price_delta": "0.00", "is_active": True,
            })
    for sku, code, option_type, group, order, price in (
        ("traditional_beef_noodle", "tea_egg", "addon", "ADD_ON", 100, "0.50"),
        ("traditional_beef_noodle", "extra_meat", "addon", "ADD_ON", 101, "4.25"),
        ("cucumber_salad", "remove_garlic", "remove", "REMOVE", 1, "0.00"),
    ):
        options.append({
            "item_sku": sku, "option_type": option_type, "option_code": code,
            "option_group": group, "parent_option_code": None, "sort_order": order,
            "name_zh": f"STG005_{code}", "name_en": f"STG005_{code}",
            "price_delta": price, "is_active": True,
        })
    return {"categories": categories, "stations": stations, "items": items, "options": options}


def classify_snapshot(current: dict, manifest: dict, *, include_staff: bool) -> str:
    require(current.get("organization") is not None, "Staging Organization identity missing")
    require(current.get("store") is not None, "Staging Store identity missing")
    require(current["organization"]["id"] == ORGANIZATION_ID and current["organization"]["code"] == STAGING_ORGANIZATION_CODE, "Staging Organization identity mismatch")
    require(current["store"]["id"] == STORE_ID and current["store"]["organization_id"] == ORGANIZATION_ID and current["store"]["code"] == STAGING_STORE_CODE, "Staging Store identity mismatch")
    require([(row["version"], row["success"]) for row in current["flyway"]] == [(str(i), True) for i in range(1, 11)], "Staging is not exact successful Flyway V10")
    require(current["device_credentials_present"] == 0, "Staging device credentials are outside reconstruction authority")

    counts = {key: len(current[key]) for key in ("categories", "stations", "items", "options", "tables", "kds_display_configs", "printers", "printer_assignments", "devices")}
    baseline_counts = {"categories": 4, "stations": 3, "items": 13, "options": 38, "tables": 0, "kds_display_configs": 0, "printers": 0, "printer_assignments": 0, "devices": 0}
    if counts == baseline_counts:
        validate_baseline(current)
        return "CURRENT_SYNTHETIC_BASELINE"

    expected_counts = {"categories": 6, "stations": 5, "items": 39, "options": 380, "tables": 13, "kds_display_configs": 6, "printers": 4, "printer_assignments": 3, "devices": 7}
    require(counts == expected_counts, f"Staging configuration count conflict: {counts}")
    validate_final(current, manifest, include_staff=include_staff)
    return "TWIN_PARITY"


def validate_baseline(current: dict) -> None:
    expected = baseline_contract()
    require(canonical_counter([without(row, "id") for row in current["categories"]]) == canonical_counter(expected["categories"]), "synthetic category baseline mismatch")
    require(canonical_counter([without(row, "id") for row in current["stations"]]) == canonical_counter(expected["stations"]), "synthetic station baseline mismatch")
    actual_items = []
    for row in current["items"]:
        projected = without(row, "id")
        projected["base_price"] = normalized_money(projected["base_price"])
        projected["cost_per_item"] = normalized_money(projected["cost_per_item"])
        actual_items.append(projected)
    require(canonical_counter(actual_items) == canonical_counter(expected["items"]), "synthetic item baseline mismatch")
    item_sku = {row["id"]: row["sku"] for row in current["items"]}
    actual_options = []
    for row in current["options"]:
        projected = without(row, "id", "item_id", "parent_option_id")
        projected["item_sku"] = item_sku[row["item_id"]]
        projected["parent_option_code"] = None
        projected["price_delta"] = normalized_money(projected["price_delta"])
        actual_options.append(projected)
    require(canonical_counter(actual_options) == canonical_counter(expected["options"]), "synthetic option baseline mismatch")
    require(len(current["staff"]) == 1 and current["staff"][0]["username"] == STAGING_OWNER_USERNAME and current["staff"][0]["role_code"] == "OWNER" and current["staff"][0]["status"].lower() == "active", "synthetic Owner baseline mismatch")
    require(len(current["organization_memberships"]) == 1 and len(current["store_memberships"]) == 1, "synthetic access baseline mismatch")
    require(not current["user_stations"] and not current["role_permissions"] and not current["receipt_templates"], "synthetic baseline contains unexpected access/template rows")


def validate_final(current: dict, manifest: dict, *, include_staff: bool) -> None:
    store = current["store"]
    require(store["status"].lower() == "active" and not store["enable_bar_kitchen_tasks"], "Store operational flags mismatch")
    require(not store["printing_enabled"] and store["printing_mode"] == "DISABLED", "Staging printing safety boundary mismatch")
    require(store["menu_revision"] == manifest["identity"]["store"]["menu_revision"], "menu revision mismatch")
    require(store["menu_updated_at"] == manifest["identity"]["store"]["menu_updated_at"], "menu timestamp mismatch")

    categories = [without(row, "id") for row in current["categories"]]
    expected_categories = [without(row, "source_ref") for row in manifest["categories"]]
    require(canonical_counter(categories) == canonical_counter(expected_categories), "category parity mismatch")
    stations = [without(row, "id") for row in current["stations"]]
    expected_stations = [without(row, "source_ref") for row in manifest["stations"]]
    require(canonical_counter(stations) == canonical_counter(expected_stations), "station parity mismatch")

    category_code = {row["source_ref"]: row["code"] for row in manifest["categories"]}
    station_code = {row["source_ref"]: row["code"] for row in manifest["stations"]}
    expected_items = []
    expected_item_ref_by_signature = {}
    for row in manifest["items"]:
        projected = without(row, "source_ref", "category_ref", "station_ref")
        projected["category_code"] = category_code[row["category_ref"]]
        projected["station_code"] = station_code[row["station_ref"]]
        projected["base_price"] = normalized_money(projected["base_price"])
        projected["cost_per_item"] = normalized_money(projected["cost_per_item"])
        signature = json.dumps(projected, ensure_ascii=False, sort_keys=True)
        require(signature not in expected_item_ref_by_signature, "manifest item signature is ambiguous")
        expected_item_ref_by_signature[signature] = row["source_ref"]
        expected_items.append(projected)
    actual_items = []
    actual_item_ref_by_id = {}
    for row in current["items"]:
        projected = without(row, "id")
        projected["base_price"] = normalized_money(projected["base_price"])
        projected["cost_per_item"] = normalized_money(projected["cost_per_item"])
        signature = json.dumps(projected, ensure_ascii=False, sort_keys=True)
        require(signature in expected_item_ref_by_signature, "unexpected Staging item")
        actual_item_ref_by_id[row["id"]] = expected_item_ref_by_signature[signature]
        actual_items.append(projected)
    require(canonical_counter(actual_items) == canonical_counter(expected_items), "item parity mismatch")

    expected_option_ref_by_signature = {}
    for row in manifest["options"]:
        signature = option_signature(row)
        require(signature not in expected_option_ref_by_signature, "manifest option signature is ambiguous")
        expected_option_ref_by_signature[signature] = row["source_ref"]
    actual_option_ref_by_id = {}
    for row in current["options"]:
        require(row["item_id"] in actual_item_ref_by_id, "option crosses Store/item boundary")
        projected = without(row, "id", "item_id", "parent_option_id")
        projected["item_ref"] = actual_item_ref_by_id[row["item_id"]]
        signature = option_signature(projected)
        require(signature in expected_option_ref_by_signature, "unexpected Staging option")
        actual_option_ref_by_id[row["id"]] = expected_option_ref_by_signature[signature]
    actual_edges = Counter()
    for row in current["options"]:
        parent_ref = None if row["parent_option_id"] is None else actual_option_ref_by_id.get(row["parent_option_id"])
        require(row["parent_option_id"] is None or parent_ref is not None, "option parent crosses graph boundary")
        actual_edges[(actual_option_ref_by_id[row["id"]], parent_ref)] += 1
    expected_edges = Counter((row["source_ref"], row["parent_option_ref"]) for row in manifest["options"])
    require(actual_edges == expected_edges, "option parent graph mismatch")

    require(canonical_counter(current["tables"]) == canonical_counter(manifest["tables"]), "table parity mismatch")
    require(canonical_counter(current["kds_display_configs"]) == canonical_counter(manifest["kds_display_configs"]), "KDS configuration parity mismatch")
    printer_ref_by_id = {}
    expected_printer_by_name = {row["name"]: row for row in manifest["printers"]}
    actual_printers = []
    for row in current["printers"]:
        require(not row.pop("endpoint_configured"), "printer endpoint unexpectedly configured")
        printer_id = row.pop("id")
        expected = expected_printer_by_name.get(row["name"])
        require(expected is not None, "unexpected logical printer")
        printer_ref_by_id[printer_id] = expected["source_ref"]
        actual_printers.append(row)
    require(canonical_counter(actual_printers) == canonical_counter([without(row, "source_ref") for row in manifest["printers"]]), "logical printer parity mismatch")
    actual_assignments = []
    for row in current["printer_assignments"]:
        projected = without(row, "printer_id")
        projected["printer_ref"] = printer_ref_by_id.get(row["printer_id"])
        actual_assignments.append(projected)
    require(canonical_counter(actual_assignments) == canonical_counter(manifest["printer_assignments"]), "printer assignment parity mismatch")
    require(canonical_counter(current["receipt_templates"]) == canonical_counter(manifest["receipt_templates"]), "receipt template parity mismatch")
    require(canonical_counter(current["devices"]) == canonical_counter([without(row, "source_ref") for row in manifest["devices"]]), "device topology parity mismatch")

    if include_staff:
        require(canonical_counter([without(row, "id") for row in current["staff"]]) == canonical_counter(manifest["staff"]), "staff username/role parity mismatch")
        require(canonical_counter(current["organization_memberships"]) == canonical_counter(manifest["organization_memberships"]), "Organization access parity mismatch")
        require(canonical_counter(current["store_memberships"]) == canonical_counter(manifest["store_memberships"]), "Store access parity mismatch")
        require(canonical_counter(current["user_stations"]) == canonical_counter(manifest["user_stations"]), "user-station parity mismatch")
        require(canonical_counter(current["role_permissions"]) == canonical_counter(manifest["role_permissions"]), "role permission parity mismatch")


def json_recordset(rows: list[dict], columns: str) -> str:
    payload = json.dumps(rows, ensure_ascii=False, separators=(",", ":"), default=str)
    require("$twinjson$" not in payload, "manifest contains reserved SQL delimiter")
    return f"jsonb_to_recordset($twinjson${payload}$twinjson$::jsonb) AS x({columns})"


def apply_sql(manifest: dict) -> str:
    baseline = baseline_contract()
    baseline_categories = json_recordset(baseline["categories"], "code text, name_zh text, name_en text, sort_order integer, is_active boolean")
    baseline_stations = json_recordset(baseline["stations"], "code text, name text, sort_order integer, is_active boolean")
    baseline_items = json_recordset(baseline["items"], "category_code text, station_code text, sku text, item_type text, name_zh text, name_en text, base_price numeric, cost_per_item numeric, is_active boolean, is_sold_out boolean, sort_order integer")
    baseline_options = json_recordset(baseline["options"], "item_sku text, option_type text, option_code text, option_group text, parent_option_code text, sort_order integer, name_zh text, name_en text, price_delta numeric, is_active boolean")
    categories = json_recordset(manifest["categories"], "source_ref text, code text, name_zh text, name_en text, sort_order integer, is_active boolean")
    stations = json_recordset(manifest["stations"], "source_ref text, code text, name text, sort_order integer, is_active boolean")
    items = json_recordset(manifest["items"], "source_ref text, category_ref text, station_ref text, sku text, item_type text, name_zh text, name_en text, base_price numeric, cost_per_item numeric, is_active boolean, is_sold_out boolean, sort_order integer")
    options = json_recordset(manifest["options"], "source_ref text, item_ref text, parent_option_ref text, option_type text, option_code text, option_group text, sort_order integer, name_zh text, name_en text, price_delta numeric, is_active boolean")
    tables = json_recordset(manifest["tables"], "table_code text, table_name text, area_name text, table_config text, capacity integer, supports_split boolean, sort_order integer, is_active boolean")
    kds = json_recordset(manifest["kds_display_configs"], "screen_code text, header_layout text, density_mode text, card_size_mode text, config_json text")
    printers = json_recordset(manifest["printers"], "source_ref text, name text, printer_type text, text_encoding text, escpos_code_page integer, font_size text, font_size_mode text, enabled boolean, paper_width_mm integer, timeout_ms integer")
    assignments = json_recordset(manifest["printer_assignments"], "printer_ref text, module_code text, enabled boolean, font_size text, takeout_receipt_copies integer")
    devices = json_recordset(manifest["devices"], "source_ref text, device_name text, device_type text, platform text, app_version text, status text, is_active boolean")
    menu_updated_at = str(manifest["identity"]["store"]["menu_updated_at"]).replace("'", "''")
    menu_revision = int(manifest["identity"]["store"]["menu_revision"])
    fingerprint = manifest["manifest"]["fingerprint_sha256"]
    return f"""
\pset pager off
BEGIN;
SET LOCAL statement_timeout = '15000ms';
SET LOCAL lock_timeout = '500ms';
LOCK TABLE flyway_schema_history, organizations, stores, menu_categories, stations, menu_items, menu_item_options, dining_tables, store_kds_display_configs, printer_configs, printer_assignments, receipt_templates, store_devices IN SHARE ROW EXCLUSIVE MODE;
DO $$ BEGIN
  IF NOT pg_try_advisory_xact_lock(hashtext('TWIN-001-STAGING-RECONSTRUCTION')) THEN RAISE EXCEPTION 'TWIN001_LOCK_BUSY'; END IF;
  IF current_database() <> 'restaurant_pos_staging' OR current_user <> 'restaurant_pos_staging' THEN RAISE EXCEPTION 'TWIN001_DATABASE_IDENTITY_REJECTED'; END IF;
  IF (SELECT count(*) FROM flyway_schema_history) <> 10 OR EXISTS (SELECT 1 FROM flyway_schema_history WHERE NOT success) OR (SELECT array_agg(version::text ORDER BY installed_rank) FROM flyway_schema_history) <> ARRAY['1','2','3','4','5','6','7','8','9','10']::text[] THEN RAISE EXCEPTION 'TWIN001_FLYWAY_V10_REQUIRED'; END IF;
  IF NOT EXISTS (SELECT 1 FROM organizations WHERE id=1 AND code='{STAGING_ORGANIZATION_CODE}') OR NOT EXISTS (SELECT 1 FROM stores WHERE id=1 AND organization_id=1 AND code='{STAGING_STORE_CODE}' AND status='active' AND printing_enabled=false AND printing_mode='DISABLED') THEN RAISE EXCEPTION 'TWIN001_IDENTITY_REJECTED'; END IF;
END $$;

CREATE TEMP TABLE twin_baseline_category ON COMMIT DROP AS SELECT x.* FROM {baseline_categories};
CREATE TEMP TABLE twin_baseline_station ON COMMIT DROP AS SELECT x.* FROM {baseline_stations};
CREATE TEMP TABLE twin_baseline_item ON COMMIT DROP AS SELECT x.* FROM {baseline_items};
CREATE TEMP TABLE twin_baseline_option ON COMMIT DROP AS SELECT x.* FROM {baseline_options};
CREATE TEMP TABLE twin_category ON COMMIT DROP AS SELECT x.*, NULL::bigint AS target_id FROM {categories};
CREATE TEMP TABLE twin_station ON COMMIT DROP AS SELECT x.*, NULL::bigint AS target_id FROM {stations};
CREATE TEMP TABLE twin_item ON COMMIT DROP AS SELECT x.*, NULL::bigint AS target_id FROM {items};
CREATE TEMP TABLE twin_option ON COMMIT DROP AS SELECT x.*, NULL::bigint AS target_id FROM {options};
CREATE TEMP TABLE twin_table ON COMMIT DROP AS SELECT x.* FROM {tables};
CREATE TEMP TABLE twin_kds ON COMMIT DROP AS SELECT x.* FROM {kds};
CREATE TEMP TABLE twin_printer ON COMMIT DROP AS SELECT x.*, NULL::bigint AS target_id FROM {printers};
CREATE TEMP TABLE twin_assignment ON COMMIT DROP AS SELECT x.* FROM {assignments};
CREATE TEMP TABLE twin_device ON COMMIT DROP AS SELECT x.* FROM {devices};

DO $$ BEGIN
  IF (SELECT count(*) FROM menu_categories WHERE store_id=1) <> 4 OR (SELECT count(*) FROM stations WHERE store_id=1) <> 3 OR (SELECT count(*) FROM menu_items WHERE store_id=1) <> 13 OR (SELECT count(*) FROM menu_item_options o JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id=1) <> 38 THEN RAISE EXCEPTION 'TWIN001_BASELINE_COUNT_CHANGED'; END IF;
  IF EXISTS ((SELECT code,name_zh,name_en,sort_order,is_active FROM menu_categories WHERE store_id=1) EXCEPT (SELECT * FROM twin_baseline_category)) OR EXISTS ((SELECT * FROM twin_baseline_category) EXCEPT (SELECT code,name_zh,name_en,sort_order,is_active FROM menu_categories WHERE store_id=1)) THEN RAISE EXCEPTION 'TWIN001_BASELINE_CATEGORY_CHANGED'; END IF;
  IF EXISTS ((SELECT code,name,sort_order,is_active FROM stations WHERE store_id=1) EXCEPT (SELECT * FROM twin_baseline_station)) OR EXISTS ((SELECT * FROM twin_baseline_station) EXCEPT (SELECT code,name,sort_order,is_active FROM stations WHERE store_id=1)) THEN RAISE EXCEPTION 'TWIN001_BASELINE_STATION_CHANGED'; END IF;
  IF EXISTS ((SELECT c.code,s.code,i.sku,i.item_type,i.name_zh,i.name_en,i.base_price,i.cost_per_item,i.is_active,i.is_sold_out,i.sort_order FROM menu_items i JOIN menu_categories c ON c.id=i.category_id AND c.store_id=i.store_id JOIN stations s ON s.id=i.station_id AND s.store_id=i.store_id WHERE i.store_id=1) EXCEPT (SELECT * FROM twin_baseline_item)) OR EXISTS ((SELECT * FROM twin_baseline_item) EXCEPT (SELECT c.code,s.code,i.sku,i.item_type,i.name_zh,i.name_en,i.base_price,i.cost_per_item,i.is_active,i.is_sold_out,i.sort_order FROM menu_items i JOIN menu_categories c ON c.id=i.category_id AND c.store_id=i.store_id JOIN stations s ON s.id=i.station_id AND s.store_id=i.store_id WHERE i.store_id=1)) THEN RAISE EXCEPTION 'TWIN001_BASELINE_ITEM_CHANGED'; END IF;
  IF EXISTS ((SELECT i.sku,o.option_type,o.option_code,o.option_group,NULL::text,o.sort_order,o.name_zh,o.name_en,o.price_delta,o.is_active FROM menu_item_options o JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id=1 AND o.parent_option_id IS NULL) EXCEPT (SELECT * FROM twin_baseline_option)) OR EXISTS ((SELECT * FROM twin_baseline_option) EXCEPT (SELECT i.sku,o.option_type,o.option_code,o.option_group,NULL::text,o.sort_order,o.name_zh,o.name_en,o.price_delta,o.is_active FROM menu_item_options o JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id=1 AND o.parent_option_id IS NULL)) THEN RAISE EXCEPTION 'TWIN001_BASELINE_OPTION_CHANGED'; END IF;
  IF (SELECT count(*) FROM dining_tables WHERE store_id=1) <> 0 OR (SELECT count(*) FROM store_kds_display_configs WHERE store_id=1) <> 0 OR (SELECT count(*) FROM printer_configs WHERE store_id=1) <> 0 OR (SELECT count(*) FROM printer_assignments WHERE store_id=1) <> 0 OR (SELECT count(*) FROM receipt_templates WHERE store_id=1) <> 0 OR (SELECT count(*) FROM store_devices WHERE store_id=1) <> 0 THEN RAISE EXCEPTION 'TWIN001_BASELINE_TOPOLOGY_CHANGED'; END IF;
END $$;

UPDATE twin_category t SET target_id=c.id FROM menu_categories c JOIN (VALUES ('SOUP_NOODLE','CAT-001'),('DRY_NOODLE','CAT-003'),('SOURCE_SIDE','CAT-004'),('DRINK','CAT-006')) m(legacy_code,source_ref) ON c.code=m.legacy_code WHERE c.store_id=1 AND t.source_ref=m.source_ref;
DO $$ BEGIN IF (SELECT count(*) FROM twin_category WHERE target_id IS NOT NULL) <> 4 THEN RAISE EXCEPTION 'TWIN001_CATEGORY_MAPPING_REJECTED'; END IF; END $$;
UPDATE menu_categories c SET code=t.code,name_zh=t.name_zh,name_en=t.name_en,sort_order=t.sort_order,is_active=t.is_active,updated_at=clock_timestamp() FROM twin_category t WHERE c.id=t.target_id;
INSERT INTO menu_categories(store_id,code,name_zh,name_en,sort_order,is_active,created_at,updated_at) SELECT 1,code,name_zh,name_en,sort_order,is_active,clock_timestamp(),clock_timestamp() FROM twin_category WHERE target_id IS NULL;
UPDATE twin_category t SET target_id=c.id FROM menu_categories c WHERE c.store_id=1 AND c.code=t.code;

UPDATE twin_station t SET target_id=s.id FROM stations s JOIN (VALUES ('NOODLE','STA-001'),('COLD','STA-003'),('BAR_SOURCE','STA-005')) m(legacy_code,source_ref) ON s.code=m.legacy_code WHERE s.store_id=1 AND t.source_ref=m.source_ref;
DO $$ BEGIN IF (SELECT count(*) FROM twin_station WHERE target_id IS NOT NULL) <> 3 THEN RAISE EXCEPTION 'TWIN001_STATION_MAPPING_REJECTED'; END IF; END $$;
UPDATE stations s SET code=t.code,name=t.name,sort_order=t.sort_order,is_active=t.is_active,updated_at=clock_timestamp() FROM twin_station t WHERE s.id=t.target_id;
INSERT INTO stations(store_id,code,name,sort_order,is_active,created_at,updated_at) SELECT 1,code,name,sort_order,is_active,clock_timestamp(),clock_timestamp() FROM twin_station WHERE target_id IS NULL;
UPDATE twin_station t SET target_id=s.id FROM stations s WHERE s.store_id=1 AND s.code=t.code;

UPDATE twin_item t SET target_id=i.id FROM menu_items i WHERE i.store_id=1 AND i.sku=t.sku AND (SELECT count(*) FROM twin_item x WHERE x.sku=t.sku)=1;
DO $$ BEGIN IF (SELECT count(*) FROM twin_item WHERE target_id IS NOT NULL) <> 13 OR (SELECT count(DISTINCT target_id) FROM twin_item WHERE target_id IS NOT NULL) <> 13 THEN RAISE EXCEPTION 'TWIN001_ITEM_MAPPING_REJECTED'; END IF; END $$;
UPDATE menu_items i SET category_id=c.target_id,station_id=s.target_id,sku=t.sku,item_type=t.item_type,name_zh=t.name_zh,name_en=t.name_en,base_price=t.base_price,cost_per_item=t.cost_per_item,is_active=t.is_active,is_sold_out=t.is_sold_out,sort_order=t.sort_order,updated_at=clock_timestamp() FROM twin_item t JOIN twin_category c ON c.source_ref=t.category_ref JOIN twin_station s ON s.source_ref=t.station_ref WHERE i.id=t.target_id;
INSERT INTO menu_items(store_id,category_id,station_id,sku,item_type,name_zh,name_en,base_price,cost_per_item,is_active,is_sold_out,sort_order,created_at,updated_at) SELECT 1,c.target_id,s.target_id,t.sku,t.item_type,t.name_zh,t.name_en,t.base_price,t.cost_per_item,t.is_active,t.is_sold_out,t.sort_order,clock_timestamp(),clock_timestamp() FROM twin_item t JOIN twin_category c ON c.source_ref=t.category_ref JOIN twin_station s ON s.source_ref=t.station_ref WHERE t.target_id IS NULL;
UPDATE twin_item t SET target_id=i.id FROM menu_items i JOIN twin_category c ON c.target_id=i.category_id JOIN twin_station s ON s.target_id=i.station_id WHERE i.store_id=1 AND c.source_ref=t.category_ref AND s.source_ref=t.station_ref AND i.sku IS NOT DISTINCT FROM t.sku AND i.sort_order=t.sort_order AND i.name_zh=t.name_zh AND i.name_en IS NOT DISTINCT FROM t.name_en;
DO $$ BEGIN IF (SELECT count(*) FROM twin_item WHERE target_id IS NULL) <> 0 OR (SELECT count(DISTINCT target_id) FROM twin_item) <> 39 THEN RAISE EXCEPTION 'TWIN001_ITEM_GRAPH_REJECTED'; END IF; END $$;

UPDATE twin_option t SET target_id=o.id FROM menu_item_options o JOIN twin_item i ON i.target_id=o.menu_item_id WHERE i.source_ref=t.item_ref AND t.option_code IS NOT NULL AND o.option_code=t.option_code;
UPDATE twin_option t SET target_id=o.id FROM menu_item_options o JOIN twin_item i ON i.target_id=o.menu_item_id WHERE t.source_ref='OPT-334' AND i.source_ref='ITEM-007' AND o.option_code='remove_garlic' AND o.name_zh='STG005_remove_garlic';
DO $$ BEGIN IF (SELECT count(*) FROM twin_option WHERE target_id IS NOT NULL) <> 38 OR (SELECT count(DISTINCT target_id) FROM twin_option WHERE target_id IS NOT NULL) <> 38 THEN RAISE EXCEPTION 'TWIN001_OPTION_MAPPING_REJECTED'; END IF; END $$;
UPDATE menu_item_options o SET option_type=t.option_type,option_code=t.option_code,option_group=t.option_group,parent_option_id=NULL,sort_order=t.sort_order,name_zh=t.name_zh,name_en=t.name_en,price_delta=t.price_delta,is_active=t.is_active,updated_at=clock_timestamp() FROM twin_option t WHERE o.id=t.target_id;
INSERT INTO menu_item_options(menu_item_id,option_type,option_code,option_group,parent_option_id,sort_order,name_zh,name_en,price_delta,is_active,created_at,updated_at) SELECT i.target_id,t.option_type,t.option_code,t.option_group,NULL,t.sort_order,t.name_zh,t.name_en,t.price_delta,t.is_active,clock_timestamp(),clock_timestamp() FROM twin_option t JOIN twin_item i ON i.source_ref=t.item_ref WHERE t.target_id IS NULL;
UPDATE twin_option t SET target_id=o.id FROM menu_item_options o JOIN twin_item i ON i.target_id=o.menu_item_id WHERE i.source_ref=t.item_ref AND t.option_code IS NOT NULL AND o.option_code=t.option_code;
UPDATE twin_option t SET target_id=o.id FROM menu_item_options o JOIN twin_item i ON i.target_id=o.menu_item_id WHERE i.source_ref=t.item_ref AND t.option_code IS NULL AND o.option_code IS NULL AND o.option_type=t.option_type AND o.option_group IS NOT DISTINCT FROM t.option_group AND o.sort_order IS NOT DISTINCT FROM t.sort_order AND o.name_zh=t.name_zh AND o.name_en IS NOT DISTINCT FROM t.name_en AND o.price_delta IS NOT DISTINCT FROM t.price_delta AND o.is_active=t.is_active;
DO $$ BEGIN IF (SELECT count(*) FROM twin_option WHERE target_id IS NULL) <> 0 OR (SELECT count(DISTINCT target_id) FROM twin_option) <> 380 THEN RAISE EXCEPTION 'TWIN001_OPTION_GRAPH_REJECTED'; END IF; END $$;
UPDATE menu_item_options o SET parent_option_id=p.target_id FROM twin_option t LEFT JOIN twin_option p ON p.source_ref=t.parent_option_ref WHERE o.id=t.target_id;

INSERT INTO dining_tables(store_id,table_code,table_name,area_name,table_config,capacity,supports_split,sort_order,is_active,created_at,updated_at) SELECT 1,table_code,table_name,area_name,table_config,capacity,supports_split,sort_order,is_active,clock_timestamp(),clock_timestamp() FROM twin_table;
INSERT INTO store_kds_display_configs(store_id,screen_code,header_layout,density_mode,card_size_mode,config_json,created_at,updated_at) SELECT 1,screen_code,header_layout,density_mode,card_size_mode,config_json,clock_timestamp(),clock_timestamp() FROM twin_kds;
INSERT INTO printer_configs(store_id,name,printer_type,text_encoding,escpos_code_page,font_size,font_size_mode,enabled,paper_width_mm,timeout_ms,ip_address,port,created_at,updated_at) SELECT 1,name,printer_type,text_encoding,escpos_code_page,font_size,font_size_mode,enabled,paper_width_mm,timeout_ms,NULL,NULL,clock_timestamp(),clock_timestamp() FROM twin_printer;
UPDATE twin_printer t SET target_id=p.id FROM printer_configs p WHERE p.store_id=1 AND p.name=t.name;
INSERT INTO printer_assignments(store_id,printer_id,module_code,enabled,font_size,takeout_receipt_copies,created_at,updated_at) SELECT 1,p.target_id,x.module_code,x.enabled,x.font_size,x.takeout_receipt_copies,clock_timestamp(),clock_timestamp() FROM twin_assignment x JOIN twin_printer p ON p.source_ref=x.printer_ref;
INSERT INTO store_devices(organization_id,store_id,device_name,device_type,platform,app_version,status,is_active,device_token_hash,last_seen_at,created_at,updated_at) SELECT 1,1,device_name,device_type,platform,app_version,status,is_active,NULL,NULL,clock_timestamp(),clock_timestamp() FROM twin_device;
UPDATE stores SET enable_bar_kitchen_tasks=false,printing_enabled=false,printing_mode='DISABLED',menu_revision={menu_revision},menu_updated_at='{menu_updated_at}'::timestamp,updated_at=clock_timestamp() WHERE id=1 AND organization_id=1;

DO $$ BEGIN
  IF (SELECT count(*) FROM menu_categories WHERE store_id=1) <> 6 OR (SELECT count(*) FROM stations WHERE store_id=1) <> 5 OR (SELECT count(*) FROM menu_items WHERE store_id=1) <> 39 OR (SELECT count(*) FROM menu_item_options o JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id=1) <> 380 OR (SELECT count(*) FROM dining_tables WHERE store_id=1) <> 13 OR (SELECT count(*) FROM store_kds_display_configs WHERE store_id=1) <> 6 OR (SELECT count(*) FROM printer_configs WHERE store_id=1) <> 4 OR (SELECT count(*) FROM printer_assignments WHERE store_id=1) <> 3 OR (SELECT count(*) FROM receipt_templates WHERE store_id=1) <> 0 OR (SELECT count(*) FROM store_devices WHERE store_id=1 AND organization_id=1) <> 7 THEN RAISE EXCEPTION 'TWIN001_POST_WRITE_COUNT_REJECTED'; END IF;
  IF EXISTS ((SELECT code,name_zh,name_en,sort_order,is_active FROM menu_categories WHERE store_id=1) EXCEPT (SELECT code,name_zh,name_en,sort_order,is_active FROM twin_category)) OR EXISTS ((SELECT code,name_zh,name_en,sort_order,is_active FROM twin_category) EXCEPT (SELECT code,name_zh,name_en,sort_order,is_active FROM menu_categories WHERE store_id=1)) THEN RAISE EXCEPTION 'TWIN001_POST_CATEGORY_PARITY_REJECTED'; END IF;
  IF EXISTS ((SELECT code,name,sort_order,is_active FROM stations WHERE store_id=1) EXCEPT (SELECT code,name,sort_order,is_active FROM twin_station)) OR EXISTS ((SELECT code,name,sort_order,is_active FROM twin_station) EXCEPT (SELECT code,name,sort_order,is_active FROM stations WHERE store_id=1)) THEN RAISE EXCEPTION 'TWIN001_POST_STATION_PARITY_REJECTED'; END IF;
  IF EXISTS ((SELECT c.source_ref,s.source_ref,i.sku,i.item_type,i.name_zh,i.name_en,i.base_price,i.cost_per_item,i.is_active,i.is_sold_out,i.sort_order FROM menu_items i JOIN twin_category c ON c.target_id=i.category_id JOIN twin_station s ON s.target_id=i.station_id WHERE i.store_id=1) EXCEPT (SELECT category_ref,station_ref,sku,item_type,name_zh,name_en,base_price,cost_per_item,is_active,is_sold_out,sort_order FROM twin_item)) OR EXISTS ((SELECT category_ref,station_ref,sku,item_type,name_zh,name_en,base_price,cost_per_item,is_active,is_sold_out,sort_order FROM twin_item) EXCEPT (SELECT c.source_ref,s.source_ref,i.sku,i.item_type,i.name_zh,i.name_en,i.base_price,i.cost_per_item,i.is_active,i.is_sold_out,i.sort_order FROM menu_items i JOIN twin_category c ON c.target_id=i.category_id JOIN twin_station s ON s.target_id=i.station_id WHERE i.store_id=1)) THEN RAISE EXCEPTION 'TWIN001_POST_ITEM_PARITY_REJECTED'; END IF;
  IF EXISTS ((SELECT i.source_ref,p.source_ref,o.option_type,o.option_code,o.option_group,o.sort_order,o.name_zh,o.name_en,o.price_delta,o.is_active FROM menu_item_options o JOIN twin_item i ON i.target_id=o.menu_item_id LEFT JOIN twin_option p ON p.target_id=o.parent_option_id WHERE o.id IN (SELECT target_id FROM twin_option)) EXCEPT (SELECT item_ref,parent_option_ref,option_type,option_code,option_group,sort_order,name_zh,name_en,price_delta,is_active FROM twin_option)) OR EXISTS ((SELECT item_ref,parent_option_ref,option_type,option_code,option_group,sort_order,name_zh,name_en,price_delta,is_active FROM twin_option) EXCEPT (SELECT i.source_ref,p.source_ref,o.option_type,o.option_code,o.option_group,o.sort_order,o.name_zh,o.name_en,o.price_delta,o.is_active FROM menu_item_options o JOIN twin_item i ON i.target_id=o.menu_item_id LEFT JOIN twin_option p ON p.target_id=o.parent_option_id WHERE o.id IN (SELECT target_id FROM twin_option))) THEN RAISE EXCEPTION 'TWIN001_POST_OPTION_PARITY_REJECTED'; END IF;
  IF EXISTS ((SELECT table_code,table_name,area_name,table_config,capacity,supports_split,sort_order,is_active FROM dining_tables WHERE store_id=1) EXCEPT (SELECT * FROM twin_table)) OR EXISTS ((SELECT * FROM twin_table) EXCEPT (SELECT table_code,table_name,area_name,table_config,capacity,supports_split,sort_order,is_active FROM dining_tables WHERE store_id=1)) THEN RAISE EXCEPTION 'TWIN001_POST_TABLE_PARITY_REJECTED'; END IF;
  IF EXISTS ((SELECT screen_code,header_layout,density_mode,card_size_mode,config_json FROM store_kds_display_configs WHERE store_id=1) EXCEPT (SELECT * FROM twin_kds)) OR EXISTS ((SELECT * FROM twin_kds) EXCEPT (SELECT screen_code,header_layout,density_mode,card_size_mode,config_json FROM store_kds_display_configs WHERE store_id=1)) THEN RAISE EXCEPTION 'TWIN001_POST_KDS_PARITY_REJECTED'; END IF;
  IF EXISTS ((SELECT name,printer_type,text_encoding,escpos_code_page,font_size,font_size_mode,enabled,paper_width_mm,timeout_ms FROM printer_configs WHERE store_id=1) EXCEPT (SELECT name,printer_type,text_encoding,escpos_code_page,font_size,font_size_mode,enabled,paper_width_mm,timeout_ms FROM twin_printer)) OR EXISTS ((SELECT name,printer_type,text_encoding,escpos_code_page,font_size,font_size_mode,enabled,paper_width_mm,timeout_ms FROM twin_printer) EXCEPT (SELECT name,printer_type,text_encoding,escpos_code_page,font_size,font_size_mode,enabled,paper_width_mm,timeout_ms FROM printer_configs WHERE store_id=1)) OR EXISTS (SELECT 1 FROM printer_configs WHERE store_id=1 AND (ip_address IS NOT NULL OR port IS NOT NULL)) THEN RAISE EXCEPTION 'TWIN001_POST_PRINTER_PARITY_REJECTED'; END IF;
  IF EXISTS ((SELECT p.source_ref,a.module_code,a.enabled,a.font_size,a.takeout_receipt_copies FROM printer_assignments a JOIN twin_printer p ON p.target_id=a.printer_id WHERE a.store_id=1) EXCEPT (SELECT * FROM twin_assignment)) OR EXISTS ((SELECT * FROM twin_assignment) EXCEPT (SELECT p.source_ref,a.module_code,a.enabled,a.font_size,a.takeout_receipt_copies FROM printer_assignments a JOIN twin_printer p ON p.target_id=a.printer_id WHERE a.store_id=1)) THEN RAISE EXCEPTION 'TWIN001_POST_ASSIGNMENT_PARITY_REJECTED'; END IF;
  IF EXISTS ((SELECT device_name,device_type,platform,app_version,status,is_active FROM store_devices WHERE store_id=1 AND organization_id=1) EXCEPT (SELECT device_name,device_type,platform,app_version,status,is_active FROM twin_device)) OR EXISTS ((SELECT device_name,device_type,platform,app_version,status,is_active FROM twin_device) EXCEPT (SELECT device_name,device_type,platform,app_version,status,is_active FROM store_devices WHERE store_id=1 AND organization_id=1)) OR EXISTS (SELECT 1 FROM store_devices WHERE store_id=1 AND organization_id=1 AND device_token_hash IS NOT NULL) THEN RAISE EXCEPTION 'TWIN001_POST_DEVICE_PARITY_REJECTED'; END IF;
  IF EXISTS (SELECT 1 FROM receipt_templates WHERE store_id=1) THEN RAISE EXCEPTION 'TWIN001_POST_TEMPLATE_PARITY_REJECTED'; END IF;
  IF NOT EXISTS (SELECT 1 FROM stores WHERE id=1 AND organization_id=1 AND code='{STAGING_STORE_CODE}' AND status='active' AND enable_bar_kitchen_tasks=false AND printing_enabled=false AND printing_mode='DISABLED' AND menu_revision={menu_revision} AND menu_updated_at='{menu_updated_at}'::timestamp) THEN RAISE EXCEPTION 'TWIN001_POST_STORE_PARITY_REJECTED'; END IF;
  IF EXISTS (SELECT 1 FROM menu_items i JOIN menu_categories c ON c.id=i.category_id WHERE i.store_id=1 AND c.store_id<>1) OR EXISTS (SELECT 1 FROM menu_items i JOIN stations s ON s.id=i.station_id WHERE i.store_id=1 AND s.store_id<>1) OR EXISTS (SELECT 1 FROM printer_assignments a JOIN printer_configs p ON p.id=a.printer_id WHERE a.store_id=1 AND p.store_id<>1) THEN RAISE EXCEPTION 'TWIN001_CROSS_STORE_REJECTED'; END IF;
END $$;
SELECT 'TWIN001_RECONSTRUCTION|status=APPLIED|manifest={fingerprint}|categories=6|stations=5|items=39|options=380|tables=13|kds=6|printers=4|assignments=3|devices=7';
COMMIT;
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("plan", "apply", "validate"))
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--execute", action="store_true", help="required for apply")
    parser.add_argument("--config-only", action="store_true", help="validate before STAFF/API reconciliation")
    args = parser.parse_args()
    manifest = load_manifest(args.manifest)
    before = snapshot()
    state = classify_snapshot(before, manifest, include_staff=not args.config_only)
    if args.mode == "plan":
        require(state == "CURRENT_SYNTHETIC_BASELINE" or state == "TWIN_PARITY", "unsupported Staging state")
        if state == "TWIN_PARITY":
            print(f"TWIN001_RECONSTRUCTION|status=PLAN_REPLAY|state={state}|manifest={EXPECTED_MANIFEST_FINGERPRINT}")
        else:
            print(f"TWIN001_RECONSTRUCTION|status=PLAN_READY|state={state}|manifest={EXPECTED_MANIFEST_FINGERPRINT}|add=categories:2,stations:2,items:26,options:342,tables:13,kds:6,printers:4,assignments:3,devices:7|delete=0")
        return
    if args.mode == "validate":
        require(state == "TWIN_PARITY", "Staging is not reconstructed Twin parity")
        print(f"TWIN001_PARITY|status=PASS|manifest={EXPECTED_MANIFEST_FINGERPRINT}|flyway=V10|blocking_behavior_difference=0")
        return
    require(args.execute, "apply requires --execute")
    if state == "TWIN_PARITY":
        print(f"TWIN001_RECONSTRUCTION|status=REPLAYED|manifest={EXPECTED_MANIFEST_FINGERPRINT}")
        return
    require(state == "CURRENT_SYNTHETIC_BASELINE", "apply requires the exact synthetic baseline")
    output = run_sql(apply_sql(manifest))
    require("status=APPLIED" in output, "apply did not emit the expected result")
    after = snapshot()
    require(classify_snapshot(after, manifest, include_staff=False) == "TWIN_PARITY", "post-write configuration validation failed")
    print(output)
    print(f"TWIN001_RECONSTRUCTION|status=POST_VALIDATE_PASS|manifest={EXPECTED_MANIFEST_FINGERPRINT}")


if __name__ == "__main__":
    main()
