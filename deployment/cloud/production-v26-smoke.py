#!/usr/bin/env python3
"""Sanitized V26 read/write smoke for an isolated clone or live Production.

Live checks accept only loopback HTTP. Rehearsal checks may use the exact
private IP of a run-owned frontend on an internal Docker network with no
published ports. That immutable container/network guard runs before any token,
database or API access. The helper discovers only numeric authorization
identities, mints a short-lived in-memory token and emits no credential or
business payload.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import ipaddress
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def fail(message: str) -> None:
    raise SystemExit(f"NO_GO|{message}")


def run_psql(container: str, sql: str) -> str:
    try:
        result = subprocess.run(
            [
                "docker",
                "--context",
                "default",
                "exec",
                container,
                "sh",
                "-eu",
                "-c",
                'psql -X -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" '
                '-d "$POSTGRES_DB" -c "$1"',
                "production-v26-smoke",
                sql,
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=20,
        )
    except subprocess.TimeoutExpired:
        fail("smoke database query exceeded 20 seconds")
    if result.returncode != 0:
        fail("smoke database identity query failed")
    return result.stdout.strip()


def docker_json(*arguments: str) -> object:
    try:
        result = subprocess.run(
            ["docker", "--context", "default", *arguments],
            check=False,
            capture_output=True,
            text=True,
            timeout=20,
        )
    except subprocess.TimeoutExpired:
        fail("write-target Docker identity check exceeded 20 seconds")
    if result.returncode != 0:
        fail("write-target Docker identity check failed")
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError:
        fail("write-target Docker identity is malformed")


def validate_rehearsal_target(
    base_url: str,
    container: str,
    expected_container_id: str | None,
    expected_backend_container_id: str | None,
    expected_api_container_id: str | None,
    run_id: str | None,
    network_name: str | None,
) -> str:
    """Reject live, published or ambiguous clone targets before any data access."""
    if container in {"cloud-db-1", "restaurant-pos-staging-db-1"}:
        fail("write smoke refuses live Production/Staging DB containers")
    if not expected_container_id or not expected_backend_container_id or not expected_api_container_id or not run_id or not network_name:
        fail("rehearsal smoke requires immutable ownership arguments")
    for expected_id in (expected_container_id, expected_backend_container_id, expected_api_container_id):
        if re.fullmatch(r"[0-9a-f]{64}", expected_id) is None:
            fail("expected rehearsal container ID is malformed")
    parsed_api = urllib.parse.urlparse(base_url)
    try:
        requested_address = ipaddress.ip_address(parsed_api.hostname or "")
    except ValueError:
        fail("rehearsal smoke requires a literal private container IP")
    if (
        parsed_api.scheme != "http"
        or parsed_api.username is not None
        or parsed_api.password is not None
        or parsed_api.path not in {"", "/"}
        or parsed_api.query
        or parsed_api.fragment
        or parsed_api.port not in {None, 80}
        or requested_address.version != 4
        or not requested_address.is_private
        or requested_address.is_loopback
    ):
        fail("rehearsal smoke requires the private internal frontend address")

    containers = docker_json("inspect", container)
    if not isinstance(containers, list) or len(containers) != 1 or not isinstance(containers[0], dict):
        fail("write-target container identity is ambiguous")
    details = containers[0]
    actual_id = str(details.get("Id") or "")
    labels = ((details.get("Config") or {}).get("Labels") or {})
    networks = ((details.get("NetworkSettings") or {}).get("Networks") or {})
    if actual_id != expected_container_id:
        fail("write-target container ID differs")
    if labels.get("restaurant.production-v26-rehearsal") != run_id:
        fail("write-target container is not owned by this rehearsal")
    if labels.get("com.docker.compose.project") == "cloud":
        fail("write smoke refuses a Production Compose container")
    if set(networks) != {network_name}:
        fail("write-target container is not isolated to its run-owned network")

    backend_rows = docker_json("inspect", expected_backend_container_id)
    api_rows = docker_json("inspect", expected_api_container_id)
    for label, rows, expected_id in (
        ("backend", backend_rows, expected_backend_container_id),
        ("API frontend", api_rows, expected_api_container_id),
    ):
        if not isinstance(rows, list) or len(rows) != 1 or not isinstance(rows[0], dict):
            fail(f"write-target {label} identity is ambiguous")
        row = rows[0]
        row_labels = ((row.get("Config") or {}).get("Labels") or {})
        row_networks = ((row.get("NetworkSettings") or {}).get("Networks") or {})
        if row.get("Id") != expected_id or row_labels.get("restaurant.production-v26-rehearsal") != run_id:
            fail(f"write-target {label} is not exact and run-owned")
        if row_labels.get("com.docker.compose.project") == "cloud" or set(row_networks) != {network_name}:
            fail(f"write-target {label} is not isolated from Production")

    for label, rows in (("database", containers), ("backend", backend_rows), ("API frontend", api_rows)):
        configured_bindings = ((rows[0].get("HostConfig") or {}).get("PortBindings") or {})
        ports = ((rows[0].get("NetworkSettings") or {}).get("Ports") or {})
        if configured_bindings or any(bindings not in (None, []) for bindings in ports.values()):
            fail(f"rehearsal {label} unexpectedly publishes a host port")

    api_network = (((api_rows[0].get("NetworkSettings") or {}).get("Networks") or {}).get(network_name) or {})
    api_address = str(api_network.get("IPAddress") or "")
    try:
        exact_api_address = ipaddress.ip_address(api_address)
    except ValueError:
        fail("rehearsal API frontend has no valid internal address")
    if exact_api_address != requested_address or exact_api_address.version != 4 or not exact_api_address.is_private or exact_api_address.is_loopback:
        fail("rehearsal API URL is not the exact internal frontend address")

    network_rows = docker_json("network", "inspect", network_name)
    if not isinstance(network_rows, list) or len(network_rows) != 1 or not isinstance(network_rows[0], dict):
        fail("write-target network identity is ambiguous")
    network = network_rows[0]
    network_labels = network.get("Labels") or {}
    if network.get("Internal") is not True or network_labels.get("restaurant.production-v26-rehearsal") != run_id:
        fail("write-target network is not internal and run-owned")
    attached_ids = set((network.get("Containers") or {}).keys())
    if attached_ids != {expected_container_id, expected_backend_container_id, expected_api_container_id}:
        fail("write-target network members differ from the exact rehearsal stack")
    return api_address


def b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def mint_token(secret: str, user_id: int, role_id: int, store_id: int, organization_id: int, role_code: str) -> str:
    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "user_id": user_id,
        "role_id": role_id,
        "store_id": store_id,
        "organization_id": organization_id,
        "role_code": role_code,
        "iat": now,
        "exp": now + 600,
    }
    encoded = ".".join(
        b64url(json.dumps(part, separators=(",", ":")).encode("utf-8"))
        for part in (header, payload)
    )
    signature = hmac.new(secret.encode("utf-8"), encoded.encode("ascii"), hashlib.sha256).digest()
    return f"{encoded}.{b64url(signature)}"


class Api:
    def __init__(self, base_url: str, token: str, validated_rehearsal_host: str | None = None):
        parsed = urllib.parse.urlparse(base_url)
        live_loopback = parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost"}
        rehearsal_internal = parsed.scheme == "http" and validated_rehearsal_host is not None and parsed.hostname == validated_rehearsal_host
        if not live_loopback and not rehearsal_internal:
            fail("smoke URL is neither loopback nor the validated rehearsal frontend")
        self.base_url = base_url.rstrip("/")
        self.token = token

    def request(
        self,
        path: str,
        method: str = "GET",
        body: object | None = None,
        expected: int = 200,
        envelope: bool = True,
    ) -> object:
        data = None if body is None else json.dumps(body, separators=(",", ":")).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            method=method,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                status = response.status
                raw = response.read()
        except urllib.error.HTTPError as exception:
            status = exception.code
            raw = exception.read()
        except Exception:
            fail(f"request failed without response: {method} {path}")
        if status != expected:
            fail(f"unexpected HTTP {status}: {method} {path}")
        if not raw:
            return None
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            fail(f"non-JSON response: {method} {path}")
        if envelope and expected < 400 and isinstance(parsed, dict) and parsed.get("success") is not True:
            fail(f"API failure envelope: {method} {path}")
        return parsed


def numeric_identity(container: str) -> tuple[int, int, str, int, int]:
    sql = """
select u.id || '|' || u.role_id || '|' || upper(r.code) || '|' || s.id || '|' || s.organization_id
from users u
join roles r on r.id = u.role_id
join organization_memberships om on om.user_id = u.id and om.is_active = true
join stores s on s.organization_id = om.organization_id and lower(s.status) = 'active'
where lower(u.status) = 'active' and upper(r.code) in ('OWNER', 'ADMIN')
order by case when upper(r.code) = 'OWNER' then 0 else 1 end, u.id, s.id
limit 1
""".strip()
    row = run_psql(container, sql).split("|")
    if len(row) != 5 or row[2] not in {"OWNER", "ADMIN"}:
        fail("active Owner/Admin smoke identity is unavailable")
    try:
        return int(row[0]), int(row[1]), row[2], int(row[3]), int(row[4])
    except ValueError:
        fail("smoke identity is malformed")


def assert_data(envelope: object, label: str) -> object:
    if not isinstance(envelope, dict) or "data" not in envelope:
        fail(f"missing API data: {label}")
    return envelope["data"]


def require_non_empty(value: object, label: str) -> None:
    if isinstance(value, (list, dict, str)) and len(value) > 0:
        return
    fail(f"required Production-clone data is empty: {label}")


def menu_items(menu: object) -> list[dict[str, object]]:
    if not isinstance(menu, dict):
        fail("menu catalog is invalid")
    items: list[dict[str, object]] = []
    for category in menu.get("categories") or []:
        if not isinstance(category, dict) or category.get("is_active") is False:
            continue
        items.extend(item for item in category.get("items") or [] if isinstance(item, dict))
    return items


def read_smoke(
    api: Api,
    wrong_organization_api: Api,
    store_id: int,
    organization_id: int,
    evidence_run_id: str | None = None,
) -> dict[str, object]:
    checks = {
        "auth": "/api/v1/auth/me",
        "workspace": "/api/v1/me/workspaces",
        "owner": "/api/v1/owner/overview",
        "store_context": f"/api/v1/stores/{store_id}/context",
        "admin": f"/api/v1/admin/dashboard?range=today&compare=true&organization_id={organization_id}&store_id={store_id}",
        "tables": f"/api/v1/frontdesk/dining-tables?store_id={store_id}",
        "stations": f"/api/v1/admin/stations?store_id={store_id}",
        "menu": f"/api/v1/menu/catalog?store_id={store_id}",
        "orders": f"/api/v1/frontdesk/orders/history?store_id={store_id}&limit=5",
        "staff": f"/api/v1/admin/staff?store_id={store_id}",
        "printing": f"/api/v1/admin/printing?store_id={store_id}",
        "printers": f"/api/v1/admin/printing/printers?store_id={store_id}",
        "assignments": f"/api/v1/admin/printing/assignments?store_id={store_id}",
        "display_rules": f"/api/v1/admin/printing/display-rules?store_id={store_id}",
        "devices": f"/api/v1/admin/printing/devices?store_id={store_id}",
        "reports": f"/api/v1/admin/analytics/summaries?range=today&organization_id={organization_id}&store_id={store_id}",
    }
    results: dict[str, object] = {}
    for name, path in checks.items():
        results[name] = assert_data(api.request(path), name)

    for name in ("auth", "workspace", "owner", "tables", "stations", "menu", "orders", "staff", "printing", "printers", "assignments", "devices", "reports"):
        require_non_empty(results[name], name)
    if not menu_items(results["menu"]):
        fail("menu catalog has no active item")

    context = results["store_context"]
    if not isinstance(context, dict) or context.get("is_live") is not True or context.get("operational_state") != "LIVE":
        fail("existing Production Store is not canonical LIVE after migration")

    history = results["orders"]
    if not isinstance(history, list) or not isinstance(history[0], dict) or not isinstance(history[0].get("order_id"), int):
        fail("historical order list has no usable real order")
    detail = assert_data(api.request(f"/api/v1/orders/{history[0]['order_id']}"), "historical_order_detail")
    if not isinstance(detail, dict) or detail.get("store_id") != store_id or not isinstance(detail.get("items"), list):
        fail("historical order detail is invalid")

    # This uses the real Store ID with a deliberately wrong Organization claim;
    # it is stronger than probing a made-up maximum Store ID.
    wrong_organization_api.request(f"/api/v1/stores/{store_id}/context", expected=403)
    api.request(f"/api/v1/stores/{store_id + 1000000}/context", expected=403)
    ws = api.request("/ws/info", envelope=False)
    if not isinstance(ws, dict):
        fail("WebSocket bootstrap response is invalid")
    print(
        f"READ_SMOKE|{f'run_id={evidence_run_id}|' if evidence_run_id else ''}"
        f"store_id={store_id}|checks={len(checks) + 4}|nonempty=PASS|"
        "historical_detail=PASS|wrong_organization_real_store=PASS|wrong_store=PASS|"
        "websocket=PASS|result=PASS"
    )
    return results


def legacy_read_smoke(api: Api, store_id: int, organization_id: int, evidence_run_id: str | None = None) -> None:
    checks = {
        "auth": "/api/v1/auth/me",
        "workspace": "/api/v1/me/workspaces",
        "owner": "/api/v1/owner/overview",
        "admin": f"/api/v1/admin/dashboard?range=today&compare=true&organization_id={organization_id}&store_id={store_id}",
        "tables": f"/api/v1/frontdesk/dining-tables?store_id={store_id}",
        "menu": f"/api/v1/menu/catalog?store_id={store_id}",
        "orders": f"/api/v1/frontdesk/orders/history?store_id={store_id}&limit=5",
        "printing": f"/api/v1/admin/printing?store_id={store_id}",
        "devices": f"/api/v1/admin/printing/devices?store_id={store_id}",
    }
    for name, path in checks.items():
        assert_data(api.request(path), name)
    api.request("/api/v1/stores/9223372036854775807/context", expected=403)
    ws = api.request("/ws/info", envelope=False)
    if not isinstance(ws, dict):
        fail("legacy WebSocket bootstrap response is invalid")
    print(
        f"LEGACY_READ_SMOKE|{f'run_id={evidence_run_id}|' if evidence_run_id else ''}"
        f"store_id={store_id}|checks={len(checks) + 2}|isolation=PASS|result=PASS"
    )


def option_payload(option: dict[str, object], option_id: int | None = None) -> dict[str, object]:
    return {
        "option_id": int(option_id if option_id is not None else option["id"]),
        "quantity": 1,
        "option_type_snapshot": option.get("option_type"),
        "option_code_snapshot": option.get("option_code"),
        "option_group_snapshot": option.get("option_group"),
        "parent_option_id_snapshot": option.get("parent_option_id"),
        "option_name_snapshot_zh": str(option.get("name_zh") or option.get("name_en") or "RC option"),
        "option_name_snapshot_en": str(option.get("name_en") or option.get("name_zh") or "RC option"),
        "option_price_snapshot": option.get("price_delta") or 0,
    }


def fnv1a32(value: str) -> int:
    result = 0x811C9DC5
    for character in value:
        result ^= ord(character)
        result = (result * 0x01000193) & 0xFFFFFFFF
    return result


def component_option_id(group: str, code: str) -> int:
    legacy = {
        "COMBO_EGG:combo_tea_egg": -20101,
        "COMBO_EGG:combo_fried_egg": -20102,
        "COMBO_SIDE:combo_edamame": -20201,
        "COMBO_SIDE:combo_shredded_potato": -20202,
        "COMBO_SIDE:combo_cucumber_salad": -20203,
    }
    key = f"{group.strip().upper()}:{code.strip().lower()}"
    return legacy.get(key, -300000 - (fnv1a32(key) % 600000))


def orderable_item(menu: object, required_id: int) -> dict[str, object]:
    for item in menu_items(menu):
        if (
            item.get("id") == required_id
            and item.get("is_active") is not False
            and item.get("is_sold_out") is not True
            and item.get("station_id") is not None
        ):
            return item
    fail("inventory-backed orderable menu item is unavailable from API")


def normal_option(item: dict[str, object]) -> dict[str, object]:
    for option in item.get("options") or []:
        if not isinstance(option, dict) or option.get("is_active") is False or option.get("parent_option_id") is not None:
            continue
        group = str(option.get("option_group") or "").upper()
        if group not in {"COMBO", "COMBO_EGG", "COMBO_SIDE", "COMBO_SIDE_REMOVE", "REMOVE"}:
            return option_payload(option)
    fail("inventory-backed item has no active non-combo option")


def combo_item_and_options(menu: object) -> tuple[dict[str, object], list[dict[str, object]]]:
    if not isinstance(menu, dict):
        fail("menu catalog is invalid")
    combo_groups = [
        group for group in ((menu.get("combo_configuration") or {}).get("groups") or [])
        if isinstance(group, dict) and group.get("enabled") is not False
    ]
    if not combo_groups:
        fail("Store combo configuration is empty")
    for item in menu_items(menu):
        if item.get("is_active") is False or item.get("is_sold_out") is True or item.get("station_id") is None:
            continue
        combo = next((
            option for option in item.get("options") or []
            if isinstance(option, dict)
            and option.get("is_active") is not False
            and str(option.get("option_group") or "").upper() == "COMBO"
        ), None)
        if combo is None:
            continue
        selected = [option_payload(combo)]
        for group in combo_groups:
            components = [component for component in group.get("components") or [] if isinstance(component, dict) and component.get("enabled") is not False]
            if group.get("required") is not False and not components:
                fail("required combo group has no enabled component")
            if not components:
                continue
            default_code = str(group.get("default_component_code") or "").lower()
            component = next((row for row in components if str(row.get("component_code") or "").lower() == default_code), components[0])
            group_code = str(group.get("group_code") or group.get("component_group") or "").upper()
            code = str(component.get("component_code") or "")
            selected.append(option_payload({
                "option_type": "addon",
                "option_code": code,
                "option_group": group_code,
                "parent_option_id": None,
                "name_zh": component.get("name_zh"),
                "name_en": component.get("name_en"),
                "price_delta": 0,
            }, component_option_id(group_code, code)))
        return item, selected
    fail("no active combo-capable menu item exists")


def inventory_backed_item_id(container: str, store_id: int) -> int:
    row = run_psql(container, f"""
select item.id
from menu_items item
join menu_item_bom bom on bom.menu_item_id = item.id
join inventory_items inventory on inventory.id = bom.inventory_item_id
where item.store_id = {store_id}
  and item.is_active is true
  and item.is_sold_out is false
  and item.station_id is not null
group by item.id
having bool_and(coalesce(inventory.current_stock, 0) >= bom.qty_per_unit)
order by item.id
limit 1
""".strip())
    try:
        return int(row)
    except ValueError:
        fail("no inventory-backed item with sufficient clone stock exists")


def write_smoke(
    api: Api,
    container: str,
    store_id: int,
    menu: object,
    evidence_run_id: str | None = None,
) -> None:
    api.request(
        "/api/v1/admin/printing/status",
        method="PUT",
        body={"store_id": store_id, "printing_mode": "MOCK"},
    )
    for module_code, enabled in (("GRAB", True), ("FRONTDESK_RECEIPT", True), ("HOT_KITCHEN", False)):
        api.request(
            f"/api/v1/admin/printing/assignments/{module_code}",
            method="PUT",
            body={
                "store_id": store_id,
                "printer_id": None,
                "enabled": enabled,
                "font_size": "NORMAL",
                "takeout_receipt_copies": 1,
            },
        )
    enabled_role = assert_data(
        api.request(
            "/api/v1/admin/printing/modules/test",
            method="POST",
            body={"store_id": store_id, "module_code": "GRAB"},
        ),
        "enabled_printing_role",
    )
    disabled_role = assert_data(
        api.request(
            "/api/v1/admin/printing/modules/test",
            method="POST",
            body={"store_id": store_id, "module_code": "HOT_KITCHEN"},
        ),
        "disabled_printing_role",
    )
    if not isinstance(enabled_role, dict) or enabled_role.get("success") is not True:
        fail("enabled MOCK printing role did not render endpoint-free")
    if not isinstance(disabled_role, dict) or disabled_role.get("success") is not False or "disabled" not in str(disabled_role.get("message") or "").lower():
        fail("disabled printing role did not fail closed")

    inventory_item = orderable_item(menu, inventory_backed_item_id(container, store_id))
    combo_item, combo_options = combo_item_and_options(menu)
    marker = f"RCV26-{uuid.uuid4().hex[:12]}"
    inventory_order_item = {
        "menu_item_id": int(inventory_item["id"]),
        "quantity": 1,
        "combo_role": "standalone",
        "options": [normal_option(inventory_item)],
    }
    combo_order_item = {
        "menu_item_id": int(combo_item["id"]),
        "quantity": 1,
        "combo_role": "standalone",
        "options": combo_options,
    }
    created = assert_data(
        api.request(
            "/api/v1/orders",
            method="POST",
            body={
                "store_id": store_id,
                "order_type": "pickup",
                "pickup_no": marker,
                "items": [inventory_order_item, combo_order_item],
            },
        ),
        "create_order",
    )
    if not isinstance(created, dict) or not isinstance(created.get("id"), int):
        fail("synthetic order creation returned no ID")
    order_id = int(created["id"])
    submitted = assert_data(api.request(f"/api/v1/orders/{order_id}/submit", method="POST"), "submit_order")
    if not isinstance(submitted, dict) or submitted.get("status") not in {"submitted", "preparing", "ready"}:
        fail("synthetic order did not enter submitted workflow")

    update_key = f"{marker}-UPDATE"
    first_update = assert_data(
        api.request(
            f"/api/v1/orders/{order_id}/updates",
            method="POST",
            body={"idempotency_key": update_key, "items": [inventory_order_item]},
        ),
        "order_update",
    )
    replay = assert_data(
        api.request(
            f"/api/v1/orders/{order_id}/updates",
            method="POST",
            body={"idempotency_key": update_key, "items": [inventory_order_item]},
        ),
        "order_update_replay",
    )
    if (
        not isinstance(first_update, dict)
        or not isinstance(replay, dict)
        or first_update.get("update_batch_id") != replay.get("update_batch_id")
        or first_update.get("revision") != replay.get("revision")
        or replay.get("already_processed") is not True
    ):
        fail("order update idempotency replay differs")

    reprint = assert_data(
        api.request(
            f"/api/v1/orders/{order_id}/reprint",
            method="POST",
            body={"receipt_type": "FRONTDESK_RECEIPT"},
        ),
        "mock_order_reprint",
    )
    if not isinstance(reprint, dict) or reprint.get("status") != "PRINTED":
        fail("synchronous MOCK order reprint did not render")

    deadline = time.time() + 20
    jobs: object = []
    while time.time() < deadline:
        jobs = assert_data(api.request(f"/api/v1/orders/{order_id}/print-jobs"), "print_jobs")
        if isinstance(jobs, list) and any(
            isinstance(job, dict)
            and job.get("status") == "PRINTED"
            and isinstance(job.get("rendered_text_snapshot"), str)
            and bool(job.get("rendered_text_snapshot"))
            for job in jobs
        ):
            break
        time.sleep(1)
    else:
        fail("MOCK PrintJob did not render")

    options_response = assert_data(api.request(f"/api/v1/orders/{order_id}/print-options"), "print_options")
    if not isinstance(options_response, list) or not options_response:
        fail("order print options are unavailable")
    availability = {
        str(option.get("module_code")): option.get("available")
        for option in options_response
        if isinstance(option, dict)
    }
    if availability.get("GRAB") is not True or availability.get("FRONTDESK_RECEIPT") is not True or availability.get("HOT_KITCHEN") is not False:
        fail("enabled/disabled printing-role availability matrix differs")
    detail = assert_data(api.request(f"/api/v1/orders/{order_id}"), "synthetic_order_detail")
    if not isinstance(detail, dict) or len(detail.get("items") or []) < 2:
        fail("synthetic order detail lost item semantics")
    api.request(f"/api/v1/orders/{order_id}/cancel", method="POST")

    facts = run_psql(
        container,
        "select count(*) || '|' || "
        "(select count(*) from order_update_batches where order_id = %d) || '|' || "
        "(select count(*) from print_jobs where order_id = %d and status = 'PRINTED') || '|' || "
        "(select count(*) from order_item_options oio join order_items oi on oi.id=oio.order_item_id where oi.order_id=%d) || '|' || "
        "(select count(*) from order_item_options oio join order_items oi on oi.id=oio.order_item_id where oi.order_id=%d and upper(coalesce(oio.option_group_snapshot,'')) in ('COMBO','COMBO_EGG','COMBO_SIDE')) || '|' || "
        "(select count(*) from inventory_transactions where source_type='order' and source_id=%d and qty_change < 0 and stock_after = stock_before + qty_change) || '|' || "
        "(select count(*) from print_jobs where order_id=%d and module_code='HOT_KITCHEN') || '|' || "
        "(select count(*) from order_dispatch_outbox where order_id=%d) "
        "from kitchen_tasks where order_id = %d" % (order_id, order_id, order_id, order_id, order_id, order_id, order_id, order_id),
    ).split("|")
    if (
        len(facts) != 8
        or int(facts[0]) < 2
        or int(facts[1]) != 1
        or int(facts[2]) < 1
        or int(facts[3]) < 3
        or int(facts[4]) < 3
        or int(facts[5]) < 2
        or int(facts[6]) != 0
        or int(facts[7]) < 4
    ):
        fail("synthetic order/options/combo/inventory/printing transaction facts differ")
    print(
        f"WRITE_SMOKE|{f'run_id={evidence_run_id}|' if evidence_run_id else ''}"
        f"order_id={order_id}|kitchen_tasks={facts[0]}|update_batches={facts[1]}|"
        f"printed_jobs={facts[2]}|options={facts[3]}|combo_options={facts[4]}|inventory_txns={facts[5]}|outbox_events={facts[7]}|"
        "disabled_hot_jobs=0|options=PASS|combo=PASS|inventory=PASS|printing_roles=PASS|"
        "mock_endpoint_free=PASS|result=PASS"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--db-container", required=True)
    parser.add_argument("--mode", choices=("read", "write", "legacy-read"), required=True)
    parser.add_argument("--expected-db-container-id")
    parser.add_argument("--expected-backend-container-id")
    parser.add_argument("--expected-api-container-id")
    parser.add_argument("--expected-run-id")
    parser.add_argument("--expected-network")
    parser.add_argument("--evidence-run-id")
    args = parser.parse_args()

    ownership_arguments = (
        args.expected_db_container_id,
        args.expected_backend_container_id,
        args.expected_api_container_id,
        args.expected_run_id,
        args.expected_network,
    )
    validated_rehearsal_host = None
    if any(ownership_arguments) or args.mode == "write":
        validated_rehearsal_host = validate_rehearsal_target(
            args.base_url,
            args.db_container,
            *ownership_arguments,
        )
    secret = os.environ.get("JWT_SECRET", "")
    if len(secret.encode("utf-8")) < 32:
        fail("JWT secret is unavailable or too short")
    user_id, role_id, role_code, store_id, organization_id = numeric_identity(args.db_container)
    token = mint_token(secret, user_id, role_id, store_id, organization_id, role_code)
    api = Api(args.base_url, token, validated_rehearsal_host)
    if args.mode == "legacy-read":
        legacy_read_smoke(api, store_id, organization_id, args.evidence_run_id)
        return
    wrong_organization_token = mint_token(
        secret,
        user_id,
        role_id,
        store_id,
        organization_id + 1000000,
        role_code,
    )
    results = read_smoke(
        api,
        Api(args.base_url, wrong_organization_token, validated_rehearsal_host),
        store_id,
        organization_id,
        args.evidence_run_id,
    )
    if args.mode == "write":
        write_smoke(api, args.db_container, store_id, results["menu"], args.evidence_run_id)


if __name__ == "__main__":
    main()
