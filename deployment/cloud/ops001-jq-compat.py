#!/usr/bin/env python3
"""Fail-closed jq subset for OPS-001 Staging helpers.

This helper is selected only when the reviewed Staging host has no jq binary.
It implements the fixed filters used by the secret-safe Staging tools and
rejects every unknown expression.
"""
import json
import re
import sys

args = sys.argv[1:]
raw = False
compact = False
null_input = False
exit_status = False
arg = {}
slurpfile = {}


def fail():
    raise SystemExit(1)


def pop_value(flag):
    if len(args) < 2:
        fail()
    key = args.pop(0)
    value = args.pop(0)
    if not key:
        fail()
    return key, value


while args and args[0].startswith("-"):
    flag = args.pop(0)
    if flag == "--arg":
        key, value = pop_value(flag)
        arg[key] = value
        continue
    if flag == "--argjson":
        key, value = pop_value(flag)
        try:
            arg[key] = json.loads(value)
        except json.JSONDecodeError:
            try:
                arg[key] = int(value)
            except ValueError:
                fail()
        continue
    if flag == "--slurpfile":
        key, path = pop_value(flag)
        try:
            with open(path, encoding="utf-8") as handle:
                slurpfile[key] = [json.load(handle)]
        except (OSError, json.JSONDecodeError):
            fail()
        continue
    if not flag.startswith("-"):
        fail()
    raw = raw or "r" in flag
    compact = compact or "c" in flag
    null_input = null_input or "n" in flag
    exit_status = exit_status or "e" in flag

filter_text = args.pop(0) if args else ""
source = args.pop(0) if args else None

if null_input:
    value = None
else:
    try:
        if source:
            with open(source, encoding="utf-8") as handle:
                value = json.load(handle)
        else:
            value = json.load(sys.stdin)
    except (OSError, json.JSONDecodeError):
        fail()


def require(condition):
    if not condition:
        fail()


def as_int(name):
    candidate = arg.get(name)
    require(isinstance(candidate, int))
    return candidate


def walk_reject_secret_keys(node, pattern):
    if isinstance(node, dict):
        for key, child in node.items():
            lowered = str(key).lower()
            if re.search(pattern, lowered, re.IGNORECASE):
                fail()
            walk_reject_secret_keys(child, pattern)
    elif isinstance(node, list):
        for child in node:
            walk_reject_secret_keys(child, pattern)


def secret_key_pattern(expression):
    match = re.search(r'test\("([^"]+)"', expression)
    return match.group(1) if match else r"password|token|cookie|authorization|secret"


def active_first_id(collection):
    require(isinstance(collection, list))
    for row in collection:
        if isinstance(row, dict) and row.get("is_active") is True and isinstance(row.get("id"), int):
            return row["id"]
    fail()


def first_active_item_with_sku(rows):
    require(isinstance(rows, list))
    for row in rows:
        if isinstance(row, dict) and row.get("is_active") is True and row.get("sku"):
            return row
    fail()


def visible_catalog_items(document):
    categories = document.get("data", {}).get("categories", [])
    require(isinstance(categories, list))
    for category in categories:
        for item in category.get("items", []) or []:
            yield item


def output(result):
    if isinstance(result, bool):
        print("true" if result else "false")
    elif raw and isinstance(result, (str, int, float)):
        print(result)
    else:
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":") if compact else None))
    if exit_status and (result is False or result is None):
        raise SystemExit(1)


result = None

if null_input and "refresh_token" in filter_text:
    result = {"refresh_token": arg.get("refresh", "")}
elif null_input and "profile_code: $catalog[0].data.profile_code" in filter_text:
    catalog = slurpfile.get("catalog", [{}])[0].get("data", {})
    result = {
        "store_name": arg.get("store_name"),
        "store_code": arg.get("store_code"),
        "profile_code": catalog.get("profile_code"),
        "profile_version": catalog.get("profile_version"),
        "master_menu_key": catalog.get("master_menu_key"),
        "master_menu_version": catalog.get("master_menu_version"),
        "master_menu_fingerprint_sha256": catalog.get("master_menu_fingerprint_sha256"),
    }
elif null_input and filter_text.strip() == "{}":
    result = {}
elif null_input and "expected_readiness_fingerprint:$fingerprint" in filter_text:
    result = {"expected_readiness_fingerprint": arg.get("fingerprint")}
elif null_input and "ROLLBACK_STATION" in filter_text:
    result = {
        "stations": [{"code": "ROLLBACK_STATION", "name": "Rollback Station", "station_type": "KITCHEN", "sort_order": 900, "is_active": True}],
        "tables": [{"table_code": "ROLLBACK_TABLE", "table_name": "Rollback Table", "capacity": 2, "supports_split": True, "sort_order": 900, "is_active": True}],
        "staff": [{"role_code": "MANAGER", "full_name": "Rollback Manager"}],
        "printer_roles": [
            {"role_code": "ROLLBACK_GRAB_A", "module_code": "GRAB", "display_name": "Rollback A", "mode": "MOCK", "enabled": False, "required": False},
            {"role_code": "ROLLBACK_GRAB_B", "module_code": "GRAB", "display_name": "Rollback B", "mode": "MOCK", "enabled": False, "required": False},
        ],
        "devices": [],
    }
elif null_input and "CHANGED_REQUEST_TABLE" in filter_text:
    result = {"tables": [{"table_code": "CHANGED_REQUEST_TABLE", "table_name": "Changed", "capacity": 2, "supports_split": True, "sort_order": 901, "is_active": True}]}
elif null_input and "app_version:\"synthetic-build\"" in filter_text:
    result = {"app_version": "synthetic-build", "platform": "STAGING"}
elif null_input and "trusted_build:false" in filter_text:
    result = {"trusted_build": False, "worker_status": "ERROR"}
elif null_input and "trusted_build:true" in filter_text:
    result = {"trusted_build": True, "worker_status": "HEALTHY"}
elif null_input and "name_zh: \"Phase B Store-only Item\"" in filter_text:
    result = {
        "store_id": as_int("store"),
        "category_id": as_int("category"),
        "station_id": as_int("station"),
        "sku": arg.get("sku"),
        "name_zh": "Phase B Store-only Item",
        "name_en": "Phase B Store-only Item",
        "item_type": "OTHER",
        "base_price": 1.23,
        "cost_per_item": 0,
        "is_active": True,
        "is_sold_out": False,
        "sort_order": 9990,
    }
elif null_input and "revision_id: $revision" in filter_text:
    result = {"store_id": as_int("store"), "revision_id": as_int("revision")}
elif filter_text.startswith(".login_identifier"):
    result = value.get("login_identifier") if isinstance(value, dict) else None
    require(isinstance(result, str) and len(result) > 0)
    if 'startswith("STG005_")' in filter_text:
        require(result.startswith("STG005_"))
elif ".new_login_password" in filter_text and "length == 20" in filter_text:
    new_password = value.get("new_login_password") if isinstance(value, dict) else None
    require(isinstance(new_password, str) and len(new_password) == 20 and new_password != value.get("login_password"))
    result = True
elif "type == \"object\"" in filter_text and ".login_identifier" in filter_text and ".login_password" in filter_text:
    require(isinstance(value, dict))
    require(isinstance(value.get("login_identifier"), str) and len(value["login_identifier"]) > 0)
    require(isinstance(value.get("login_password"), str) and len(value["login_password"]) >= 12)
    if ".phase_b_idempotency_key" in filter_text:
        key = value.get("phase_b_idempotency_key")
        require(isinstance(key, str) and re.match(r"^[A-Za-z0-9._:-]{16,255}$", key))
    result = True
elif "{new_password:" in filter_text and ".new_login_password" in filter_text:
    result = {"new_password": value["new_login_password"]}
elif ".login_identifier" in filter_text and ".new_login_password" in filter_text:
    result = {"login_identifier": value["login_identifier"], "password": value["new_login_password"]}
elif ".login_identifier" in filter_text and ".login_password" in filter_text:
    result = {"login_identifier": value["login_identifier"], "password": value["login_password"]}
elif ".onboarding_request" in filter_text and "type == \"object\"" in filter_text:
    request = value.get("onboarding_request") if isinstance(value, dict) else None
    require(isinstance(request, dict))
    require(isinstance(value.get("onboarding_idempotency_key"), str) and len(value["onboarding_idempotency_key"]) >= 16)
    require(request.get("source_store_id") == 1)
    require(isinstance(request.get("store_code"), str) and request["store_code"].startswith("STG005_"))
    staff = request.get("staff")
    require(isinstance(staff, list) and len(staff) > 0)
    for row in staff:
        require(isinstance(row.get("login_identifier"), str) and row["login_identifier"].startswith("STG005_"))
        require(row.get("role_code") in ("MANAGER", "FRONTDESK"))
        require(isinstance(row.get("initial_password"), str) and len(row["initial_password"]) >= 12)
    result = True
elif ".onboarding_request" in filter_text:
    result = value["onboarding_request"]
elif ".clone_idempotency_key" in filter_text and "length >= 16" in filter_text:
    require(isinstance(value.get("clone_idempotency_key"), str) and len(value["clone_idempotency_key"]) >= 16)
    result = True
elif ".success == true" in filter_text:
    require(isinstance(value, dict) and value.get("success") is True)
    result = True
elif ".data.proof_status == \"NOT_READY\"" in filter_text or ".data.proof_status == \"PASS\"" in filter_text:
    expected = "PASS" if ".data.proof_status == \"PASS\"" in filter_text else "NOT_READY"
    result = value.get("data", {}).get("proof_status")
    require(result == expected)
elif ".data.readiness.readiness_fingerprint | strings" in filter_text:
    result = value.get("data", {}).get("readiness", {}).get("readiness_fingerprint")
    require(isinstance(result, str) and len(result) == 64)
elif ".data.readiness_fingerprint | strings" in filter_text:
    result = value.get("data", {}).get("readiness_fingerprint")
    require(isinstance(result, str) and len(result) == 64)
elif ".data.synthetic_device_credentials[0].device_id" in filter_text:
    result = value.get("data", {}).get("synthetic_device_credentials", [{}])[0].get("device_id")
    require(isinstance(result, int))
elif ".data.synthetic_device_credentials[0].device_token" in filter_text:
    result = value.get("data", {}).get("synthetic_device_credentials", [{}])[0].get("device_token")
    require(isinstance(result, str) and len(result) > 20)
elif ".data.ready == false" in filter_text or ".data.ready == true" in filter_text:
    data = value.get("data", {})
    expected_ready = ".data.ready == true" in filter_text
    require(data.get("ready") is expected_ready)
    if 'readiness_status == "NOT_READY"' in filter_text:
        require(data.get("readiness_status") == "NOT_READY")
    if 'readiness_status == "READY"' in filter_text:
        require(data.get("readiness_status") == "READY")
    if "DEVICE_READINESS" in filter_text:
        require(any(
            isinstance(check, dict)
            and check.get("code") == "DEVICE_READINESS"
            and check.get("status") == "FAIL"
            for check in data.get("checks", [])
        ))
    result = True
elif ".data.status == \"COMPLETED\"" in filter_text and "synthetic_staff_credentials" in filter_text:
    data = value.get("data", {})
    counts = data.get("counts", {})
    readiness = data.get("readiness", {})
    require(data.get("status") == "COMPLETED")
    require(data.get("replayed") is False)
    require(readiness.get("ready") is True)
    require(counts.get("table_count", 0) >= 2)
    require(counts.get("staff_count", 0) >= 2)
    require(counts.get("printer_role_count", 0) >= 2)
    require(counts.get("device_count", 0) >= 1)
    require(len(data.get("synthetic_staff_credentials", [])) >= 2)
    require(len(data.get("synthetic_device_credentials", [])) == 1)
    result = True
elif ".data.replayed == true" in filter_text and "synthetic_staff_credentials" in filter_text:
    data = value.get("data", {})
    require(data.get("replayed") is True)
    require(len(data.get("synthetic_staff_credentials", [])) == 0)
    require(len(data.get("synthetic_device_credentials", [])) == 0)
    result = True
elif ".data.replayed == true" in filter_text and ".data.target_state == \"LIVE\"" in filter_text:
    data = value.get("data", {})
    require(data.get("replayed") is True and data.get("target_state") == "LIVE")
    result = True
elif "paths(scalars)" in filter_text:
    walk_reject_secret_keys(value, secret_key_pattern(filter_text))
    result = True
elif ".data.access_token" in filter_text:
    result = value["data"]["access_token"]
    minimum = 24 if "length >= 24" in filter_text else 21
    require(isinstance(result, str) and len(result) >= minimum)
elif ".data.refresh_token" in filter_text:
    result = value["data"]["refresh_token"]
    require(isinstance(result, str) and len(result) > 20)
elif ".data.user.id" in filter_text:
    result = value["data"]["user"]["id"]
    require(isinstance(result, int) and result > 0)
elif ".data.user.role_code" in filter_text and ".data.user.username" in filter_text:
    user = value["data"]["user"]
    require(user.get("role_code") == "OWNER")
    require(user.get("organization_id") == as_int("organization"))
    require(user.get("username") == arg.get("login"))
    result = True
elif ".data.user.username" in filter_text:
    require(value["data"]["user"].get("username") == arg.get("login"))
    result = True
elif ".data.user.role_code" in filter_text:
    user = value["data"]["user"]
    require(user.get("role_code") == "OWNER" and user.get("organization_id") == as_int("organization"))
    result = True
elif ".data.stores" in filter_text and "VALIDATION_FIXTURE" in filter_text:
    stores = value.get("data", {}).get("stores", [])
    require(isinstance(stores, list))
    for store in stores:
        if store.get("store_kind", "") == "VALIDATION_FIXTURE" and store.get("provisioning_source", "") != "PHASE_B_OWNER_PROVISIONING":
            fail()
    result = True
elif ".data.stores" in filter_text and "organization_id" in filter_text:
    org = as_int("organization")
    source_id = int(arg.get("source", arg.get("target", 0)))
    stores = value["data"]["stores"]
    require(isinstance(stores, list) and len(stores) == 1)
    require(stores[0].get("id") == source_id and stores[0].get("organization_id") == org)
    result = True
elif ".data.organizations" in filter_text:
    org = as_int("organization")
    organizations = value["data"]["organizations"]
    require(any(row.get("id") == org and row.get("role_code") == "OWNER" for row in organizations))
    result = True
elif ".data.enabled == true" in filter_text and ".data.profile_code" in filter_text:
    data = value.get("data", {})
    require(data.get("enabled") is True)
    require(data.get("profile_code") == "ST_DENIS_CANONICAL_PROFILE")
    require(data.get("profile_version") == "v2")
    require(data.get("master_menu_key") == "LANZHOU_CHAIN_MASTER_MENU")
    require(data.get("master_menu_version") == "v1")
    result = True
elif filter_text.startswith(".phase_b_idempotency_key"):
    result = value.get("phase_b_idempotency_key")
    require(isinstance(result, str) and len(result) >= 16)
elif ".data.store_id" in filter_text:
    result = value["data"]["store_id"]
    require(isinstance(result, int) and result > 0)
elif ".data.validation_status" in filter_text:
    result = value["data"]["validation_status"]
    require(result in ("PASS", "WARNING"))
elif ".data.status == \"COMPLETED\"" in filter_text:
    data = value.get("data", {})
    counts = data.get("counts", {})
    require(data.get("status") == "COMPLETED")
    require(data.get("result_code") == "PHASE_B_STORE_PROVISIONED")
    require(counts.get("category_count", 0) > 0)
    require(counts.get("item_count", 0) > 0)
    require(counts.get("option_count", 0) > 0)
    require(counts.get("printing_rule_count") == 1)
    result = True
elif ".data.replayed" in filter_text:
    result = value["data"]["replayed"]
    require(result is True)
elif ".data.status != \"active\"" in filter_text and ".data.module_configuration.modules" in filter_text:
    data = value.get("data", {})
    require(data.get("status") != "active")
    require(data.get("store_kind") == "VALIDATION_FIXTURE")
    require(data.get("lifecycle_status") == "READY_FOR_REVIEW")
    require(data.get("provisioning_source") == "PHASE_B_OWNER_PROVISIONING")
    modules = data.get("module_configuration", {}).get("modules", [])
    require(isinstance(modules, list) and len(modules) > 0)
    result = True
elif ".data.categories | length > 0" in filter_text:
    categories = value.get("data", {}).get("categories", [])
    require(isinstance(categories, list) and len(categories) > 0)
    require(sum(len(category.get("items", []) or []) for category in categories) > 0)
    result = True
elif "select(.is_active == true and (.sku // \"\") != \"\")" in filter_text:
    result = first_active_item_with_sku(value.get("data", []))
elif filter_text.strip() == ".sku":
    result = value.get("sku")
    require(isinstance(result, str) and len(result) > 0)
elif filter_text.strip() == ".is_active = false":
    require(isinstance(value, dict))
    result = dict(value)
    result["is_active"] = False
elif filter_text.strip() == ".id":
    result = value.get("id")
    require(isinstance(result, int))
elif ".data.categories[].items[]?" in filter_text and "length == 0" in filter_text:
    sku = arg.get("sku")
    require(isinstance(sku, str))
    require(sum(1 for item in visible_catalog_items(value) if item.get("sku") == sku) == 0)
    result = True
elif ".data.categories[].items[]?" in filter_text and "length == 1" in filter_text:
    sku = arg.get("sku")
    require(isinstance(sku, str))
    require(sum(1 for item in visible_catalog_items(value) if item.get("sku") == sku) == 1)
    result = True
elif "select(.id == $category)" in filter_text:
    category_id = as_int("category")
    for row in value.get("data", []):
        if row.get("id") == category_id:
            result = row
            break
    require(result is not None)
elif filter_text.strip() == ".code":
    result = value.get("code")
    require(isinstance(result, str) and len(result) > 0)
elif "enabled: false" in filter_text and "name_zh" in filter_text:
    result = {
        "store_id": as_int("store"),
        "name_zh": value.get("name_zh"),
        "name_en": value.get("name_en"),
        "sort_order": value.get("sort_order"),
        "enabled": False,
    }
elif "enabled: true" in filter_text and "name_zh" in filter_text:
    result = {
        "store_id": as_int("store"),
        "name_zh": value.get("name_zh"),
        "name_en": value.get("name_en"),
        "sort_order": value.get("sort_order"),
        "enabled": True,
    }
elif ".data.categories[] | select(.code == $code)" in filter_text:
    code = arg.get("code")
    categories = value.get("data", {}).get("categories", [])
    require(sum(1 for row in categories if row.get("code") == code) == 0)
    result = True
elif "select(.is_active == true)" in filter_text and "first.id" in filter_text:
    result = active_first_id(value.get("data", []))
elif ".size_small_delta" in filter_text and "combo_delta" in filter_text:
    data = value.get("data", {})
    result = {
        "store_id": data.get("store_id"),
        "size_small_delta": float(data.get("size_small_delta", 0)) + 0.11,
        "size_regular_delta": data.get("size_regular_delta"),
        "size_large_delta": data.get("size_large_delta"),
        "combo_delta": data.get("combo_delta"),
    }
elif ".data.groups[].components[]" in filter_text and "first.id" in filter_text:
    for group in value.get("data", {}).get("groups", []):
        for component in group.get("components", []) or []:
            if component.get("enabled") is True and isinstance(component.get("id"), int):
                result = component["id"]
                break
        if result is not None:
            break
    require(result is not None)
elif ".data as $data" in filter_text and "components:" in filter_text:
    component_id = as_int("component")
    data = value.get("data", {})
    result = {
        "store_id": as_int("store"),
        "groups": [
            {
                **{key: group.get(key) for key in (
                    "group_id", "group_code", "name_zh", "name_en", "selection_rule",
                    "required", "enabled", "display_order", "default_component_code"
                )},
                "components": [
                    {
                        **{key: component.get(key) for key in (
                            "id", "group_id", "component_group", "component_code", "name_zh",
                            "name_en", "display_order", "is_default", "linked_menu_item_id",
                            "business_behavior"
                        )},
                        "enabled": False if component.get("id") == component_id else component.get("enabled"),
                    }
                    for component in (group.get("components", []) or [])
                ],
            }
            for group in data.get("groups", [])
        ],
    }
elif ".data.active_revision.content" in filter_text and "item_aliases" in filter_text:
    content = dict(value.get("data", {}).get("active_revision", {}).get("content", {}))
    aliases = list(content.get("item_aliases") or [])
    aliases.append({"item_sku": "phase_b_acceptance_probe", "outputs": {"GRAB": "PB"}})
    content["item_aliases"] = aliases
    result = {"store_id": as_int("store"), "content": content, "summary": "Phase B acceptance local rule"}
elif ".data.id" in filter_text:
    result = value["data"]["id"]
    require(isinstance(result, int) and result > 0)
else:
    fail()

output(result)
