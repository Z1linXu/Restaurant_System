#!/usr/bin/env python3
"""Real HTTP acceptance for the explicitly isolated Template Test environment.

Uses only Python's standard library. Credentials and access tokens stay in memory;
the receipt contains synthetic artifact identities and assertions, never login bodies.
Existing orders/jobs are retained. Only this run's temporary routing changes are
restored through the same authorized configuration API at the end.
"""
import argparse
import copy
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from decimal import Decimal, ROUND_HALF_UP


ALLOWED_BASES = {"http://127.0.0.1:28081", "https://test.lanzhounoodlesmtl.com"}
REPO = Path(__file__).resolve().parent.parent


class ApiFault(Exception):
    def __init__(self, status, path, message="Request failed"):
        self.status, self.path, self.message = status, path, message
        super().__init__(f"HTTP {status} {path}: {message}")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise urllib.error.HTTPError(req.full_url, code, "Redirect blocked", headers, fp)


class Client:
    def __init__(self, base, redactions):
        self.base, self.token, self.redactions = base, None, redactions
        self.opener = urllib.request.build_opener(NoRedirect())
        self.requests = 0

    def safe(self, message):
        value = str(message)
        for secret in self.redactions:
            if secret:
                value = value.replace(secret, "[REDACTED]")
        value = re.sub(r"[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}", "[REDACTED_TOKEN]", value)
        return value[:400]

    def call(self, method, path, body=None, extra_headers=None, envelope=True):
        if not path.startswith("/api/v1/") or path.startswith("//"):
            raise ValueError("Only explicit application API paths are permitted")
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        headers.update(extra_headers or {})
        request = urllib.request.Request(self.base + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
        self.requests += 1
        try:
            with self.opener.open(request, timeout=25) as response:
                self.last_response_headers = {key.lower(): value for key, value in response.headers.items()}
                if not envelope:
                    response.read()
                    return {"http_status": response.status, "allow_origin": response.headers.get("Access-Control-Allow-Origin")}
                payload = json.loads(response.read())
                if not isinstance(payload, dict) or payload.get("success") is not True:
                    raise ApiFault(response.status, path, self.safe(payload.get("message", "Invalid API envelope")))
                return payload.get("data")
        except urllib.error.HTTPError as error:
            message = "Request rejected"
            if not path.endswith("/auth/login"):
                try:
                    payload = json.loads(error.read())
                    message = self.safe(payload.get("message", message))
                except (ValueError, TypeError):
                    pass
            raise ApiFault(error.code, path, message) from None
        except urllib.error.URLError:
            raise ApiFault(0, path, "Target connection unavailable") from None

    def login(self, username, password, expected_role, origin=None):
        result = self.call("POST", "/api/v1/auth/login", {"login_identifier": username, "password": password}, extra_headers={"Origin": origin} if origin else None)
        token = result.get("access_token")
        if not token or result.get("user", {}).get("role_code") != expected_role:
            raise AssertionError("Login did not return the expected synthetic role")
        self.token = token
        self.redactions.append(token)
        return result["user"]


def equal(actual, expected, label):
    if actual != expected:
        raise AssertionError(f"{label}: expected {expected!r}, received {actual!r}")


def money(value):
    return Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


class Acceptance:
    def __init__(self, base, credentials, foreign_store, wait_seconds, runtime_evidence):
        redactions = [value for key, value in credentials.items() if "password" in key and isinstance(value, str)]
        self.owner, self.manager = Client(base, redactions), Client(base, redactions)
        self.anonymous = Client(base, redactions)
        self.credentials, self.foreign, self.wait_seconds = credentials, foreign_store, wait_seconds
        self.runtime_evidence = runtime_evidence
        self.legacy_mock_jobs = set()
        self.task_followup = []
        self.run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S") + "_" + uuid.uuid4().hex[:6]
        self.prefix = "http_" + self.run_id
        self.store, self.org, self.original_route = None, None, None
        self.cases, self.artifacts = [], {"menu_item_ids": [], "order_ids": [], "run_id": self.run_id}

    def case(self, name, action):
        started = time.monotonic()
        try:
            detail = action()
            result = {"case": name, "status": "PASS", "seconds": round(time.monotonic() - started, 3)}
            if detail is not None:
                result["evidence"] = detail
        except Exception as error:
            result = {"case": name, "status": "FAIL", "seconds": round(time.monotonic() - started, 3), "error": self.owner.safe(error)}
        self.cases.append(result)
        print(f"{result['status']} {name}" + (f" — {result['error']}" if result["status"] == "FAIL" else ""), flush=True)
        return result["status"] == "PASS"

    def path(self, suffix):
        return f"/api/v1/stores/{self.store}/template/{suffix}"

    def get(self, suffix, client=None):
        return (client or self.owner).call("GET", self.path(suffix))

    def put(self, suffix, body, client=None):
        return (client or self.owner).call("PUT", self.path(suffix), body)

    def rejected(self, action, statuses=(400, 409, 422)):
        try:
            action()
        except ApiFault as error:
            if error.status not in statuses:
                raise AssertionError(f"Wrong rejection: {error}") from None
            return {"http_status": error.status, "path": error.path, "message": error.message}
        raise AssertionError("Request unexpectedly succeeded")

    def catalog(self):
        return self.owner.call("GET", f"/api/v1/menu/catalog?store_id={self.store}")

    def catalog_item(self, item_id):
        data = self.catalog()
        for category in data["categories"]:
            for item in category["items"]:
                if item["id"] == item_id:
                    return {**item, "category_code": category["code"]}
        raise AssertionError("Created synthetic item was absent from the real catalog")

    def setup(self):
        if set(self.runtime_evidence.get("allowed_printing_modes", [])) != {"MOCK", "DISABLED"} or self.runtime_evidence.get("endpoint_configuration_enabled") is not False:
            raise AssertionError("Integration-owner runtime evidence must prove MOCK/DISABLED ceiling and disabled endpoint configuration")
        if self.runtime_evidence.get("environment") != "test" or self.runtime_evidence.get("profile") != "template-test" or not self.runtime_evidence.get("evidence_ref"):
            raise AssertionError("Runtime evidence must identify the Test profile/environment and its verified evidence reference")
        owner = self.owner.login(self.credentials["owner_username"], self.credentials["owner_password"], "OWNER")
        manager = self.manager.login(self.credentials["manager_username"], self.credentials["manager_password"], "MANAGER")
        self.store, self.org = owner["store_id"], owner["organization_id"]
        equal(manager["store_id"], self.store, "Manager's synthetic Store")
        equal(manager["organization_id"], self.org, "Manager's synthetic Organization")
        workspace = self.owner.call("GET", "/api/v1/me/workspaces")
        current = next((store for store in workspace["stores"] if store["id"] == self.store), None)
        if not current or current.get("code") != "SYNTHETIC_TEMPLATE_TEST_MAIN":
            raise AssertionError("Safety guard: authenticated Store is not the dedicated SYNTHETIC Template Test fixture")
        self.inert_printers()
        if not self.foreign or self.foreign == self.store:
            raise AssertionError("An explicit distinct scope-negative synthetic Store id is required")
        context = self.owner.call("GET", f"/api/v1/admin/menu/management-context?store_id={self.store}")
        self.categories = {category["code"]: category["id"] for category in context["menu_categories"]}
        self.stations = {station["code"]: station["id"] for station in context["stations"]}
        for key in ("SYNTHETIC_HOT", "SYNTHETIC_FRIED", "SYNTHETIC_COLD"):
            if key not in self.categories:
                raise AssertionError("Required synthetic category is absent")
        self.original_route = self.get("kitchen-routing")
        self.printers = self.get("printers")
        if not self.printers or not all(printer["name"].startswith("SYNTHETIC MOCK") for printer in self.printers):
            raise AssertionError("Safety guard: expected endpoint-free synthetic MOCK printer choices")
        return {"store_id": self.store, "organization_id": self.org, "foreign_store_id": self.foreign, "roles": ["OWNER", "MANAGER"]}

    def create_item(self, label, category, station, price, client=None):
        body = {"store_id": self.store, "category_id": self.categories[category], "station_id": self.stations[station],
                "name_zh": f"SYNTHETIC_HTTP_{label}_{self.run_id}", "name_en": f"SYNTHETIC HTTP {label} {self.run_id}",
                "sku": f"synthetic_http_{label.lower()}_{self.run_id}", "item_type": "MAIN", "base_price": price,
                "cost_per_item": 0, "is_active": True, "is_sold_out": False, "sort_order": 1000}
        created = (client or self.owner).call("POST", "/api/v1/admin/platform/menu/items", body)
        item_id = created.get("id")
        if not isinstance(item_id, int):
            raise AssertionError("Create menu item did not return a canonical id")
        self.artifacts["menu_item_ids"].append(item_id)
        equal(self.catalog_item(item_id).get("option_groups"), [], "Atomic new item no-group catalog marker")
        return item_id

    def create_items(self):
        self.item_a = self.create_item("A", "SYNTHETIC_HOT", "WOK", 12)
        self.item_b = self.create_item("B", "SYNTHETIC_FRIED", "DEEPFRIED", 10, self.manager)
        self.item_c = self.create_item("C", "SYNTHETIC_COLD", "COLD", 3)
        return {"ids": [self.item_a, self.item_b, self.item_c], "catalog_option_groups": "explicit_empty", "manager_created_item": self.item_b}

    def configure_groups(self):
        library = self.get("option-library")
        spicy = next((group for group in library["groups"] if group["kind"] == "SPICY_LEVEL" and group["options"]), None)
        if not spicy:
            raise AssertionError("Synthetic preset spicy group was absent")
        custom = {"group_instance_code": self.prefix + "_sauce", "name": "SYNTHETIC HTTP Sauce " + self.run_id,
                  "kind": "CUSTOM", "selection_mode": "SINGLE", "required": True, "sort_order": 10, "is_active": True,
                  "default_option_ids": [], "options": [
                      {"option_code": self.prefix + "_garlic", "name_zh": "SYNTHETIC 蒜蓉", "name_en": "SYNTHETIC Garlic", "price_delta": 0.35, "is_active": True, "is_default": True, "sort_order": 20},
                      {"option_code": self.prefix + "_pepper", "name_zh": "SYNTHETIC 黑椒", "name_en": "SYNTHETIC Pepper", "price_delta": 1.20, "is_active": True, "is_default": False, "sort_order": 10}]}
        self.put("option-library", {"groups": library["groups"] + [custom]}, self.manager)
        canonical_library = self.get("option-library")
        custom = next(group for group in canonical_library["groups"] if group["group_instance_code"] == custom["group_instance_code"])
        for item_id in (self.item_a, self.item_b):
            copies = []
            for index, source in enumerate((custom, spicy)):
                group = copy.deepcopy(source)
                group["source_library_code"] = source["group_instance_code"]
                group["group_instance_code"] = self.prefix + f"_{item_id}_{index}"
                group["sort_order"] = index * 10
                group["default_option_ids"] = []
                for option in group["options"]:
                    option["is_default"] = bool(option.get("is_default") or (option.get("id") in source.get("default_option_ids", [])))
                    option.pop("id", None)
                copies.append(group)
            self.put(f"items/{item_id}/option-groups", {"groups": copies}, self.manager if item_id == self.item_b else self.owner)
        self.groups_a = self.get(f"items/{self.item_a}/option-groups")
        self.groups_b = self.get(f"items/{self.item_b}/option-groups")
        ids_a = {option["id"] for group in self.groups_a["groups"] for option in group["options"]}
        ids_b = {option["id"] for group in self.groups_b["groups"] for option in group["options"]}
        equal(ids_a & ids_b, set(), "Per-item copies have distinct leaf identities")
        sauce = next(group for group in self.groups_a["groups"] if group["kind"] == "CUSTOM")
        equal([option["name_en"] for option in sauce["options"]], ["SYNTHETIC Pepper", "SYNTHETIC Garlic"], "Option display order")
        equal([group["kind"] for group in self.groups_a["groups"]], ["CUSTOM", "SPICY_LEVEL"], "Group display order")
        baseline_b = copy.deepcopy(self.groups_b)
        changed_a = copy.deepcopy(self.groups_a)
        changed_group = next(group for group in changed_a["groups"] if group["kind"] == "CUSTOM")
        chosen = changed_group["options"][0]
        chosen["price_delta"] = 0.75
        changed_group["default_option_ids"] = [chosen["id"]]
        for option in changed_group["options"]:
            option["is_default"] = option["id"] == chosen["id"]
        changed_a["groups"].reverse()
        for index, group in enumerate(changed_a["groups"]):
            group["sort_order"] = index * 10
        self.put(f"items/{self.item_a}/option-groups", changed_a)
        after_b = self.get(f"items/{self.item_b}/option-groups")
        equal(after_b["groups"], baseline_b["groups"], "A price/default/group-order edits must not change B")
        self.groups_a = self.get(f"items/{self.item_a}/option-groups")
        return {"item_a": self.item_a, "item_b": self.item_b, "distinct_option_ids": True, "independent_price_default_order": True}

    def line(self, item_id, quantity=1, selected=None):
        item = self.catalog_item(item_id)
        leaf_by_id = {option["id"]: option for option in item["options"]}
        if selected is None:
            selected = {}
            for group in item.get("option_groups") or []:
                if not group["is_active"] or group["kind"] == "COMBO":
                    continue
                active = [option for option in group["options"] if option["is_active"]]
                defaults = [option for option in active if option.get("is_default") or option["id"] in group["default_option_ids"]]
                for option in (defaults or active[:1] if group["required"] else defaults):
                    selected[option["id"]] = 1
        snapshots = []
        for option_id, count in selected.items():
            option = leaf_by_id[option_id]
            snapshots.append({"option_id": option_id, "quantity": count, "option_type_snapshot": option["option_type"],
                              "option_code_snapshot": option["option_code"], "option_group_snapshot": option["option_group"],
                              "parent_option_id_snapshot": option.get("parent_option_id"), "option_name_snapshot_zh": option["name_zh"],
                              "option_name_snapshot_en": option["name_en"], "option_price_snapshot": option["price_delta"]})
        return {"menu_item_id": item_id, "quantity": quantity, "item_name_snapshot_zh": item["name_zh"], "item_name_snapshot_en": item["name_en"],
                "unit_price_snapshot": item["base_price"], "category_code_snapshot": item["category_code"], "station_id_snapshot": item["station_id"],
                "item_sku_snapshot": item["sku"], "item_type_snapshot": item["item_type"], "combo_group_no": None, "combo_role": "standalone", "notes": None, "options": snapshots}

    def payload(self, lines, label):
        key = "synthetic-http-" + uuid.uuid4().hex
        subtotal = sum((money(line["unit_price_snapshot"]) + sum(money(option["option_price_snapshot"]) * option["quantity"] for option in line["options"])) * line["quantity"] for line in lines)
        return {"client_order_id": key, "idempotency_key": key, "local_draft_id": key, "organization_id": self.org, "store_id": self.store,
                "server_order_id": None, "order_type": "pickup", "table_no": None, "pickup_no": "HTTP-" + label + "-" + uuid.uuid4().hex[:8],
                "menu_revision": self.catalog()["menu_revision"], "expected_subtotal_amount": float(money(subtotal)), "items": lines}

    def submit(self, payload):
        result = self.owner.call("POST", f"/api/v1/stores/{self.store}/orders/idempotent-submit", payload)
        order_id = result["order_id"]
        if order_id not in self.artifacts["order_ids"]:
            self.artifacts["order_ids"].append(order_id)
        equal(result["order"]["id"], order_id, "Server-confirmed saved order")
        return result

    def validation(self, variation):
        line = self.line(self.item_a)
        sauce = next(group for group in self.groups_a["groups"] if group["kind"] == "CUSTOM")
        sauce_ids = {option["id"] for option in sauce["options"]}
        if variation == "base_price":
            line["unit_price_snapshot"] += 1
        elif variation == "option_price":
            line["options"][0]["option_price_snapshot"] += 1
        elif variation == "required":
            line["options"] = [option for option in line["options"] if option["option_id"] not in sauce_ids]
        elif variation == "single":
            selected = {option["option_id"]: option["quantity"] for option in line["options"]}
            selected.update({option_id: 1 for option_id in sauce_ids})
            line = self.line(self.item_a, selected=selected)
        elif variation == "single_quantity":
            next(option for option in line["options"] if option["option_id"] in sauce_ids)["quantity"] = 2
        elif variation == "item_scope":
            foreign_leaf = next(group for group in self.groups_b["groups"] if group["kind"] == "CUSTOM")["options"][0]
            line["options"][0]["option_id"] = foreign_leaf["id"]
        elif variation == "unknown_item":
            line["menu_item_id"] = 900000000000000000
        elif variation == "negative_item":
            line["menu_item_id"] = -900000000000000000
        elif variation == "category_forgery":
            line["category_code_snapshot"] = "DRINK"
        elif variation == "sku_forgery":
            line["item_sku_snapshot"] = "synthetic_http_forged_sku_" + self.run_id
        before = self.observation()
        result = self.rejected(lambda: self.submit(self.payload([line], variation)), (400, 404, 409, 422))
        equal(self.observation(), before, "Rejected input must not create Order/Job/Outbox intent")
        return {**result, "no_order_job_intent_created": True}

    def routing(self, mode="three", missing=False, failure=None, override=False):
        printer_id = self.printers[0]["id"]
        if mode == "one":
            groups = [{"group_code": self.prefix + "_ONE", "name": "SYNTHETIC HTTP One Kitchen", "enabled": True, "sort_order": 0,
                       "category_ids": [self.categories[key] for key in ("SYNTHETIC_HOT", "SYNTHETIC_FRIED", "SYNTHETIC_COLD")],
                       "item_ids": [], "combo_component_codes": [], "logical_target": "http-one", "printer_id": printer_id, "mock_failure": None}]
        else:
            groups = [{"group_code": self.prefix + "_" + name, "name": "SYNTHETIC HTTP " + name, "enabled": True, "sort_order": index * 10,
                       "category_ids": [self.categories[category]], "item_ids": [], "combo_component_codes": [], "logical_target": "http-" + name,
                       "printer_id": None if name == "C" and missing else printer_id, "mock_failure": failure if name == "C" else None}
                      for index, (name, category) in enumerate(zip("ABC", ("SYNTHETIC_HOT", "SYNTHETIC_FRIED", "SYNTHETIC_COLD")))]
        config = {"enabled": True, "groups": groups, "item_overrides": [{"menu_item_id": self.item_c, "group_codes": [groups[0]["group_code"]]}] if override else []}
        return self.put("kitchen-routing", config, self.manager)

    def diagnostics(self, order_id=None, batch_id=None):
        params = {"order_id": order_id} if order_id is not None else {}
        if batch_id is not None:
            params["batch_id"] = batch_id
        result = self.get("printing-diagnostics" + ("?" + urllib.parse.urlencode(params) if params else ""))
        return [ticket for ticket in result["tickets"] if order_id is None or (ticket["order_id"] == order_id and (ticket.get("order_update_batch_id") == batch_id))]

    def jobs(self, order_id):
        jobs = self.owner.call("GET", f"/api/v1/orders/{order_id}/print-jobs")
        printers = self.inert_printers()
        for job in jobs:
            mode, module = job.get("execution_mode"), job.get("module_code")
            if module == "TEMPLATE_KITCHEN":
                # Existing toResponse concatenates a null IP into the display-only
                # string "null:9100". The authoritative config must still be inert.
                if mode != "MOCK" or job.get("printer_id") not in printers or job.get("printer_endpoint") not in (None, "", "null:9100"):
                    raise AssertionError("Safety guard: template Job is not explicit MOCK with an endpoint-free synthetic printer")
            elif module == "FRONTDESK_RECEIPT":
                # The unchanged legacy MOCK branch does not set executionMode.
                # Null is permitted only with the independently attested ceiling,
                # current Store MOCK mode and no attached printer/endpoint.
                if mode not in (None, "MOCK") or job.get("printer_id") is not None or job.get("printer_endpoint"):
                    raise AssertionError("Safety guard: legacy frontdesk Job is not the original endpoint-free MOCK path")
                self.legacy_mock_jobs.add(job["id"])
            else:
                raise AssertionError("Safety guard: unrecognized legacy module cannot bypass MOCK verification")
        return jobs

    def inert_printers(self):
        printing = self.owner.call("GET", f"/api/v1/admin/printing?store_id={self.store}")
        if printing.get("printing_mode") != "MOCK" or any(
            printer.get("printer_type") != "MOCK" or (printer.get("ip_address") or "").strip() or printer.get("port") is not None
            for printer in printing.get("printers", [])
        ):
            raise AssertionError("Safety guard: Test must use MOCK mode and printers with no endpoint or port")
        return {printer["id"]: printer for printer in printing["printers"]}

    def wait(self, order_id, count, batch_id=None, states=None):
        deadline = time.monotonic() + self.wait_seconds
        last = []
        while time.monotonic() < deadline:
            last = self.diagnostics(order_id, batch_id)
            if len(last) == count and all(ticket["state"] in (states or {"PRINTED", "FAILED", "CANCELLED", "UNRESOLVED", "UNCERTAIN"}) for ticket in last):
                return last
            time.sleep(0.25)
        summary = [{"group": ticket["group_name"], "state": ticket["state"], "job_id": ticket["job_id"], "error_code": ticket.get("error_code")} for ticket in last]
        raise AssertionError(f"Timed out waiting for {count} settled ticket intents: {summary}")

    def observation(self):
        orders = self.owner.call("GET", f"/api/v1/frontdesk/orders/history?store_id={self.store}&limit=500")
        jobs = self.owner.call("GET", f"/api/v1/admin/printing/jobs?store_id={self.store}")
        intents = self.diagnostics()
        return {"order_ids": sorted(order["id"] for order in orders), "job_ids": sorted(job["id"] for job in jobs), "intent_ids": sorted(str(row["intent_id"]) for row in intents)}

    def preview(self, item_ids):
        lines = [self.line(item_id) for item_id in item_ids]
        return self.owner.call("POST", self.path("kitchen-routing/preview"), {"items": [{"menu_item_id": line["menu_item_id"], "quantity": line["quantity"], "options": line["options"]} for line in lines]})

    def preview_zero_write(self):
        self.routing("one")
        before = self.observation()
        preview = self.preview([self.item_a, self.item_b, self.item_c])
        equal(len(preview["tickets"]), 1, "One-group preview")
        equal(self.observation(), before, "Pure preview must not create Order/Job/Outbox intent")
        if not all(self.catalog_item(item_id)["name_zh"] in preview["tickets"][0]["rendered_text"] for item_id in (self.item_a, self.item_b, self.item_c)):
            raise AssertionError("Preview omitted a routed item")
        return {"ticket_count": 1, "before_after_ids_equal": True}

    def three_preview(self):
        self.routing()
        preview = self.preview([self.item_a, self.item_b, self.item_c])
        equal(len(preview["tickets"]), 3, "Three-group preview")
        equal(len({ticket["printer_id"] for ticket in preview["tickets"]}), 1, "Three groups share one MOCK device")
        equal(len({ticket["group_code"] for ticket in preview["tickets"]}), 3, "Logical groups remain separate")
        self.routing(override=True)
        overridden = self.preview([self.item_c])
        equal([ticket["group_code"] for ticket in overridden["tickets"]], [self.prefix + "_A"], "Explicit item override wins over category")
        self.routing()
        return {"groups": 3, "distinct_devices": 1, "item_override": True}

    def normal_order(self, mode="three"):
        self.routing(mode)
        payload = self.payload([self.line(self.item_a, 2), self.line(self.item_b), self.line(self.item_c)], mode)
        result = self.submit(payload)
        count = 1 if mode == "one" else 3
        tickets = self.wait(result["order_id"], count, states={"PRINTED"})
        jobs = self.jobs(result["order_id"])
        front = [job for job in jobs if job["module_code"] == "FRONTDESK_RECEIPT"]
        deadline = time.monotonic() + self.wait_seconds
        while time.monotonic() < deadline and not any(job["status"] == "PRINTED" for job in front):
            time.sleep(0.25)
            jobs = self.jobs(result["order_id"])
            front = [job for job in jobs if job["module_code"] == "FRONTDESK_RECEIPT"]
        equal(len(front), 1, "Exactly one original frontdesk receipt")
        if front[0]["status"] != "PRINTED" or not all(self.catalog_item(item_id)["name_zh"] in (front[0].get("rendered_text_snapshot") or "") for item_id in (self.item_a, self.item_b, self.item_c)):
            raise AssertionError("Original frontdesk receipt was not complete/processed")
        equal(money(result["order"]["subtotal_amount"]), money(payload["expected_subtotal_amount"]), "Server option/base price authority")
        equal(len(jobs), count + 1, "No accidental legacy kitchen duplicates")
        snapshot = {"intent_ids": [ticket["intent_id"] for ticket in tickets], "job_ids": sorted(job["id"] for job in jobs)}
        replay = self.submit(payload)
        equal(replay["order_id"], result["order_id"], "Replay order identity")
        equal(replay["replayed"], True, "Server identifies replay")
        equal(sorted(job["id"] for job in self.jobs(result["order_id"])), snapshot["job_ids"], "Replay does not create extra jobs")
        equal([row["intent_id"] for row in self.diagnostics(result["order_id"])], snapshot["intent_ids"], "Replay does not create extra intents")
        if mode == "three":
            self.main_order = result["order_id"]
            self.main_job_ids = snapshot["job_ids"]
            self.main_payload = copy.deepcopy(payload)
        return {"order_id": result["order_id"], "kitchen_tickets": count, **snapshot, "frontdesk_complete": True, "replay_no_duplicates": True}

    def concurrent_submission(self):
        self.routing()
        payload = self.payload([self.line(self.item_a), self.line(self.item_b), self.line(self.item_c)], "CONCURRENT")
        path = f"/api/v1/stores/{self.store}/orders/idempotent-submit"
        with ThreadPoolExecutor(max_workers=4) as pool:
            responses = list(pool.map(lambda _: self.owner.call("POST", path, payload), range(4)))
        ids = {response["order_id"] for response in responses}
        equal(len(ids), 1, "Four concurrent requests share one saved Order identity")
        order_id = ids.pop()
        self.artifacts["order_ids"].append(order_id)
        equal(sum(bool(response["replayed"]) for response in responses), 3, "Exactly three concurrent replays")
        tickets = self.wait(order_id, 3, states={"PRINTED"})
        jobs = self.jobs(order_id)
        equal(len({row["intent_id"] for row in tickets}), 3, "Concurrent submit has exactly three kitchen intents")
        equal(len(jobs), 4, "Concurrent submit has three kitchen jobs and one frontdesk job")
        return {"requests": 4, "order_id": order_id, "kitchen_intents": 3, "job_ids": sorted(job["id"] for job in jobs)}

    def conflicting_payload(self):
        payload = copy.deepcopy(self.main_payload)
        payload["pickup_no"] += "-CONFLICT"
        return self.rejected(lambda: self.owner.call("POST", f"/api/v1/stores/{self.store}/orders/idempotent-submit", payload), (409,))

    def combo(self):
        self.routing()
        item_id = self.create_item("COMBO", "SYNTHETIC_HOT", "WOK", 15)
        library = self.get("option-library")
        spicy = copy.deepcopy(next(group for group in library["groups"] if group["kind"] == "SPICY_LEVEL"))
        spicy["group_instance_code"] = self.prefix + "_combo_spicy"
        spicy["default_option_ids"] = []
        for option in spicy["options"]:
            option.pop("id", None)
        binding = {"group_instance_code": self.prefix + "_combo", "name": "SYNTHETIC Existing Combo", "kind": "COMBO", "selection_mode": "SINGLE",
                   "required": False, "is_active": True, "sort_order": 20, "default_option_ids": [], "options": []}
        self.put(f"items/{item_id}/option-groups", {"groups": [spicy, binding]})
        current = self.catalog_item(item_id)
        marker = next((option for option in current["options"] if option.get("option_group") == "COMBO" and option["is_active"]), None)
        if not marker:
            raise AssertionError("Binding existing Combo did not expose its original upcharge marker")
        line = self.line(item_id)
        def snapshot(option_id, code, group, zh, en, price):
            return {"option_id": option_id, "quantity": 1, "option_type_snapshot": "addon", "option_code_snapshot": code,
                    "option_group_snapshot": group, "parent_option_id_snapshot": None, "option_name_snapshot_zh": zh,
                    "option_name_snapshot_en": en, "option_price_snapshot": price}
        line["options"].append(snapshot(marker["id"], marker["option_code"], "COMBO", marker["name_zh"], marker["name_en"], marker["price_delta"]))
        metadata = self.catalog()["combo_configuration"]
        stable_virtual_ids = {"combo_tea_egg": -20101, "combo_fried_egg": -20102, "combo_edamame": -20201, "combo_shredded_potato": -20202, "combo_cucumber_salad": -20203}
        side = None
        for group in metadata["groups"]:
            if not group.get("enabled", True):
                continue
            chosen = next((option for option in group["components"] if option["component_code"] == group["default_component_code"] and option["enabled"]), None)
            if not chosen or chosen["component_code"] not in stable_virtual_ids:
                raise AssertionError("Synthetic Combo does not have a supported existing virtual default")
            line["options"].append(snapshot(stable_virtual_ids[chosen["component_code"]], chosen["component_code"], group["component_group"], chosen["name_zh"], chosen["name_en"], 0))
            if group["component_group"] == "COMBO_SIDE":
                side = chosen
        if side is None:
            raise AssertionError("Synthetic Combo side is absent")
        before = self.observation()
        preview = self.owner.call("POST", self.path("kitchen-routing/preview"), {"items": [{"menu_item_id": item_id, "quantity": 1, "options": line["options"]}]})
        equal(self.observation(), before, "Combo preview is also side-effect free")
        children = [row for row in preview["coverage"] if row.get("parent_source_key")]
        if not children or not any(self.prefix + "_C" in row["group_codes"] for row in children):
            raise AssertionError("Existing linked Combo side did not have explicit child coverage in C")
        response = self.submit(self.payload([line], "COMBO"))
        tickets = self.wait(response["order_id"], 2, states={"PRINTED"})
        equal({row["group_code"] for row in tickets}, {self.prefix + "_A", self.prefix + "_C"}, "Combo main and linked side route independently")
        child_ticket = next(row for row in tickets if row["group_code"] == self.prefix + "_C")
        if side["name_zh"] not in child_ticket["rendered_text"]:
            raise AssertionError("Kitchen C omitted the selected linked Combo side instruction")
        self.combo_line, self.combo_side = copy.deepcopy(line), copy.deepcopy(side)
        self.task_followup.append({"case": "normal_combo", "order_id": response["order_id"], "linked_side_item_id": side.get("linked_menu_item_id"), "expected_station": "COLD", "verification": "PENDING_PARENT_SCOPED_TEST_PG_SELECT; KDS remains disabled"})
        return {"order_id": response["order_id"], "item_id": item_id, "existing_virtual_ids": True, "explicit_child_coverage": True,
                "kitchen_job_ids": [row["job_id"] for row in tickets], "combo_engine_reused": True}

    def combo_child_remove(self):
        if not hasattr(self, "combo_line"):
            raise AssertionError("Existing Combo fixture must succeed before its child Remove acceptance")
        side_item_id = self.combo_side.get("linked_menu_item_id")
        if not side_item_id:
            raise AssertionError("Existing Combo side has no canonical linked menu item")
        original = self.get(f"items/{side_item_id}/option-groups")
        remove_name = "SYNTHETIC 子项去料 " + self.run_id
        remove = {"group_instance_code": self.prefix + "_side_remove", "name": "SYNTHETIC Child Remove", "kind": "REMOVE", "selection_mode": "MULTIPLE",
                  "required": False, "sort_order": 90, "is_active": True, "default_option_ids": [], "options": [{"option_code": "remove_" + self.prefix,
                  "name_zh": remove_name, "name_en": "SYNTHETIC Child Remove " + self.run_id, "price_delta": 0, "is_active": True, "is_default": False, "sort_order": 0}]}
        try:
            changed = self.put(f"items/{side_item_id}/option-groups", {"groups": original["groups"] + [remove]})
            group = next(row for row in changed["groups"] if row["group_instance_code"] == remove["group_instance_code"])
            leaf = group["options"][0]
            line = copy.deepcopy(self.combo_line)
            line["options"].append({"option_id": leaf["id"], "quantity": 1, "option_type_snapshot": "remove", "option_code_snapshot": leaf["option_code"],
                                   "option_group_snapshot": "COMBO_SIDE_REMOVE", "parent_option_id_snapshot": -20203,
                                   "option_name_snapshot_zh": leaf["name_zh"], "option_name_snapshot_en": leaf["name_en"], "option_price_snapshot": 0})
            self.routing()
            before = self.observation()
            preview = self.owner.call("POST", self.path("kitchen-routing/preview"), {"items": [{"menu_item_id": line["menu_item_id"], "quantity": 1, "options": line["options"]}]})
            equal(self.observation(), before, "Child Remove preview must remain zero-write")
            def verify(tickets, text_key):
                side_ticket = next(row for row in tickets if row["group_code"] == self.prefix + "_C")
                parent_ticket = next(row for row in tickets if row["group_code"] == self.prefix + "_A")
                if remove_name not in side_ticket[text_key]:
                    raise AssertionError("Selected Combo child Remove is absent from C's preparation instruction")
                if remove_name in parent_ticket[text_key]:
                    raise AssertionError("Child Remove leaked into parent ticket A")
            verify(preview["tickets"], "rendered_text")
            result = self.submit(self.payload([line], "CHILDREMOVE"))
            tickets = self.wait(result["order_id"], 2, states={"PRINTED"})
            verify(tickets, "rendered_text")
            self.task_followup.append({"case": "combo_child_remove", "order_id": result["order_id"], "linked_side_item_id": side_item_id,
                                       "expected_station": "COLD", "expected_instruction": remove_name, "verification": "PENDING_PARENT_SCOPED_TEST_PG_SELECT; KDS remains disabled"})
            return {"order_id": result["order_id"], "child_remove_option_id": leaf["id"], "parent_virtual_option_id": -20203,
                    "preview_and_real_child_instruction_present": True, "parent_instruction_unpolluted": True}
        finally:
            self.put(f"items/{side_item_id}/option-groups", original)

    def combo_missing_virtual_metadata(self):
        if not hasattr(self, "combo_line"):
            raise AssertionError("Normal Combo fixture is required")
        line = copy.deepcopy(self.combo_line)
        virtual = next(option for option in line["options"] if option["option_id"] == -20203)
        virtual["option_code_snapshot"] = None
        virtual["option_group_snapshot"] = None
        before = self.observation()
        result = self.rejected(lambda: self.submit(self.payload([line], "VIRTUALMETA")))
        equal(self.observation(), before, "Known virtual id without canonical code/group must not create Order/Job/intent")
        return {**result, "known_virtual_id": -20203, "missing_code_and_group_rejected": True, "no_order_job_intent_created": True}

    def unsupported_combo_behavior(self):
        if not hasattr(self, "combo_line"):
            raise AssertionError("Normal Combo fixture is required")
        path = "/api/v1/admin/menu/combo-configuration"
        original = self.owner.call("GET", path + f"?store_id={self.store}")
        proposed = {"store_id": self.store, "groups": copy.deepcopy(original["groups"])}
        side = next(group for group in proposed["groups"] if group["component_group"] == "COMBO_SIDE")
        chosen = next(component for component in side["components"] if component["component_code"] == side["default_component_code"])
        chosen["business_behavior"] = "NO_KITCHEN_TASK"
        saved = False
        before = self.observation()
        try:
            try:
                self.owner.call("PUT", path, proposed)
                saved = True
            except ApiFault as error:
                if error.status not in (400, 409, 422):
                    raise
                equal(self.observation(), before, "Unsupported Combo config rejection must not create business artifacts")
                return {"unsupported_side_behavior": "NO_KITCHEN_TASK", "rejected_at": "configuration", "http_status": error.status}
            result = self.rejected(lambda: self.submit(self.payload([copy.deepcopy(self.combo_line)], "UNSUPPORTEDCOMBO")))
            equal(self.observation(), before, "Unsupported Combo behavior must not silently create mismatched Task/tickets")
            return {**result, "unsupported_side_behavior": "NO_KITCHEN_TASK", "rejected_at": "order", "no_order_job_intent_created": True}
        finally:
            if saved:
                self.owner.call("PUT", path, {"store_id": self.store, "groups": original["groups"]})

    def combo_legacy_side_requires_linked_item(self):
        if not hasattr(self, "combo_line"):
            raise AssertionError("Normal Combo fixture is required")
        path = "/api/v1/admin/menu/combo-configuration"
        original = self.owner.call("GET", path + f"?store_id={self.store}")
        proposed = {"store_id": self.store, "groups": copy.deepcopy(original["groups"])}
        side = next(group for group in proposed["groups"] if group["component_group"] == "COMBO_SIDE")
        chosen = next(component for component in side["components"] if component["component_code"] == side["default_component_code"])
        chosen["business_behavior"] = "NO_KITCHEN_TASK"
        chosen["linked_menu_item_id"] = None
        saved = False
        before = self.observation()
        try:
            # An incoming null in legacy behavior preserves the existing link.
            # Use the established NO_KITCHEN_TASK transition to clear it first.
            self.owner.call("PUT", path, proposed)
            saved = True
            chosen["business_behavior"] = "LEGACY_COMBO_SIDE_TASK"
            self.owner.call("PUT", path, proposed)
            persisted = self.owner.call("GET", path + f"?store_id={self.store}")
            persisted_side = next(group for group in persisted["groups"] if group["component_group"] == "COMBO_SIDE")
            persisted_component = next(component for component in persisted_side["components"] if component["component_code"] == chosen["component_code"])
            equal(persisted_component["business_behavior"], "LEGACY_COMBO_SIDE_TASK", "Negative fixture legacy side behavior persisted")
            equal(persisted_component.get("linked_menu_item_id"), None, "Negative fixture linked menu item was actually cleared")
            line = copy.deepcopy(self.combo_line)
            preview = self.rejected(lambda: self.owner.call("POST", self.path("kitchen-routing/preview"), {
                "items": [{"menu_item_id": line["menu_item_id"], "quantity": line["quantity"], "options": line["options"]}]}))
            order = self.rejected(lambda: self.submit(self.payload([line], "UNLINKEDCOMBOSIDE")))
            equal(self.observation(), before, "Unlinked legacy Combo side must not create a Task without its child ticket")
            return {"behavior": "LEGACY_COMBO_SIDE_TASK", "linked_menu_item_id": None, "rejected_at": "preview_and_order",
                    "fixture_transition": "NO_KITCHEN_TASK then LEGACY_COMBO_SIDE_TASK; GET verified persisted null link",
                    "preview": preview, "order": order, "no_order_job_intent_created": True}
        finally:
            if saved:
                self.owner.call("PUT", path, {"store_id": self.store, "groups": original["groups"]})

    def cors(self, origin, allowed):
        client = Client(self.owner.base, self.owner.redactions)
        if not allowed:
            return self.rejected(lambda: client.login(self.credentials["owner_username"], self.credentials["owner_password"], "OWNER", origin), (403,))
        preflight = client.call("OPTIONS", "/api/v1/auth/login", extra_headers={"Origin": origin, "Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "content-type"}, envelope=False)
        if preflight["http_status"] not in (200, 204):
            raise AssertionError("Exact allowed browser Origin preflight did not succeed")
        equal(preflight["allow_origin"], origin, "Exact preflight Origin reflection")
        client.login(self.credentials["owner_username"], self.credentials["owner_password"], "OWNER", origin)
        equal(client.last_response_headers.get("access-control-allow-origin"), origin, "Exact login Origin reflection")
        return {"origin": origin, "preflight_and_real_login": True, "credentials_or_tokens_logged": False}

    def snapshot_and_zero_price_instruction(self):
        before_order = self.owner.call("GET", f"/api/v1/orders/{self.main_order}")
        before_line = next(line for line in before_order["items"] if line["menu_item_id"] == self.item_a)
        before_jobs = {job["id"]: job.get("rendered_text_snapshot") for job in self.jobs(self.main_order)}
        groups = self.get(f"items/{self.item_a}/option-groups")
        custom = next(group for group in groups["groups"] if group["kind"] == "CUSTOM")
        selected = next(option for option in custom["options"] if option["id"] in custom["default_option_ids"])
        selected["price_delta"] = 0
        selected["name_zh"] = "SYNTHETIC 零价制作选项 " + self.run_id
        selected["name_en"] = "SYNTHETIC Zero Price Instruction " + self.run_id
        self.put(f"items/{self.item_a}/option-groups", groups)
        after_order = self.owner.call("GET", f"/api/v1/orders/{self.main_order}")
        after_line = next(line for line in after_order["items"] if line["id"] == before_line["id"])
        equal(after_line, before_line, "Historical option/name/price/quantity snapshot after current menu change")
        equal({job["id"]: job.get("rendered_text_snapshot") for job in self.jobs(self.main_order)}, before_jobs, "Historical rendered Job snapshots after menu change")
        self.routing()
        submitted = self.submit(self.payload([self.line(self.item_a)], "ZERO"))
        tickets = self.wait(submitted["order_id"], 1, states={"PRINTED"})
        if selected["name_zh"] not in tickets[0]["rendered_text"]:
            raise AssertionError("Independent kitchen layer silently omitted a zero-price custom preparation instruction")
        deadline = time.monotonic() + self.wait_seconds
        front = []
        while time.monotonic() < deadline:
            front = [job for job in self.jobs(submitted["order_id"]) if job["module_code"] == "FRONTDESK_RECEIPT" and job["status"] == "PRINTED"]
            if front:
                break
            time.sleep(0.25)
        if not front:
            raise AssertionError("Original frontdesk receipt did not complete for zero-price custom selection")
        detail_present = selected["name_zh"] in (front[0].get("rendered_text_snapshot") or "")
        return {"order_id": submitted["order_id"], "historical_order_snapshot_unchanged": True, "historical_job_texts_unchanged": True,
                "zero_price_custom_kitchen_instruction_present": True,
                "frontdesk_zero_price_custom_detail": "PRESENT" if detail_present else "OMITTED_BY_PROTECTED_LEGACY_RENDERER_KNOWN_LIMITATION",
                "legacy_renderer_changed": False}

    def update(self):
        new_line = self.line(self.item_b)
        request = {"idempotency_key": "synthetic-http-update-" + uuid.uuid4().hex, "items": [new_line]}
        result = self.owner.call("POST", f"/api/v1/orders/{self.main_order}/updates", request)
        batch = result["update_batch_id"]
        tickets = self.wait(self.main_order, 1, batch, {"PRINTED"})
        equal(tickets[0]["group_code"], self.prefix + "_B", "Update only routes the new B item")
        jobs = self.jobs(self.main_order)
        new_jobs = [job for job in jobs if job.get("order_update_batch_id") == batch]
        equal(len(new_jobs), 2, "Update has one kitchen ticket and one original frontdesk update receipt")
        front = next(job for job in new_jobs if job["module_code"] == "FRONTDESK_RECEIPT")
        if self.catalog_item(self.item_b)["name_zh"] not in (front.get("rendered_text_snapshot") or ""):
            raise AssertionError("Update frontdesk omitted the added item")
        if self.catalog_item(self.item_a)["name_zh"] in (front.get("rendered_text_snapshot") or "") or self.catalog_item(self.item_c)["name_zh"] in (front.get("rendered_text_snapshot") or ""):
            raise AssertionError("Update reprinted old lines")
        again = self.owner.call("POST", f"/api/v1/orders/{self.main_order}/updates", request)
        equal(again["update_batch_id"], batch, "Update replay batch identity")
        equal(sorted(job["id"] for job in self.jobs(self.main_order)), sorted(job["id"] for job in jobs), "Update replay no duplicates")
        return {"order_id": self.main_order, "batch_id": batch, "new_job_ids": sorted(job["id"] for job in new_jobs)}

    def fault(self, kind):
        self.routing(missing=kind == "MISSING", failure=None if kind == "MISSING" else kind)
        response = self.submit(self.payload([self.line(self.item_a), self.line(self.item_b), self.line(self.item_c)], kind))
        order_id = response["order_id"]
        tickets = self.wait(order_id, 3)
        by_code = {ticket["group_code"].rsplit("_", 1)[-1]: ticket for ticket in tickets}
        equal(by_code["A"]["state"], "PRINTED", "A continues after C failure")
        equal(by_code["B"]["state"], "PRINTED", "B continues after C failure")
        c = by_code["C"]
        if kind == "MISSING":
            equal(c["job_id"], None, "Missing-printer intent has no PrintJob")
            equal(c["state"], "UNRESOLVED", "Pre-Job is unresolved, never PRINTED")
        elif c["state"] not in {"FAILED", "UNCERTAIN", "CANCELLED"} or c["job_id"] is None:
            raise AssertionError("MOCK failure/uncertain was not recorded in the existing Job state path")
        if not c.get("reason") or not c.get("error_code"):
            raise AssertionError("Failed ticket has no persistent reason/code")
        saved_order = self.owner.call("GET", f"/api/v1/orders/{order_id}")
        equal(saved_order["id"], order_id, "Print failure does not roll back legal order")
        before = {"intents": [ticket["intent_id"] for ticket in tickets], "jobs": sorted(job["id"] for job in self.jobs(order_id))}
        time.sleep(1.2)
        reloaded = self.diagnostics(order_id)
        equal([ticket["intent_id"] for ticket in reloaded], before["intents"], "Persistent diagnostics reload identity")
        equal(sorted(job["id"] for job in self.jobs(order_id)), before["jobs"], "No automatic duplicate/resend job")
        c_reload = next(ticket for ticket in reloaded if ticket["group_code"] == c["group_code"])
        equal(c_reload["reason"], c["reason"], "Persistent C reason after reload")
        if kind == "UNCERTAIN":
            job_before = next(job for job in self.jobs(order_id) if job["id"] == c["job_id"])
            time.sleep(1.2)
            job_after = next(job for job in self.jobs(order_id) if job["id"] == c["job_id"])
            equal((job_after["status"], job_after.get("retry_count")), (job_before["status"], job_before.get("retry_count")), "Uncertain job not automatically retried")
        return {"order_id": order_id, "states": {name: row["state"] for name, row in by_code.items()}, "c_job_id": c["job_id"], "c_error_code": c["error_code"], "durable_reload": True, "no_extra_jobs": True}

    def run(self):
        if not self.case("auth_and_synthetic_environment_guard", self.setup):
            return
        self.case("anonymous_management_rejected", lambda: self.rejected(lambda: self.anonymous.call("GET", self.path("option-library")), (401,)))
        for origin in ("http://127.0.0.1:28080", "https://test.lanzhounoodlesmtl.com"):
            self.case("exact_browser_origin_login_" + ("local" if "127.0.0.1" in origin else "test_https"), lambda origin=origin: self.cors(origin, True))
        self.case("evil_browser_origin_login_rejected", lambda: self.cors("https://evil.invalid", False))
        for role, client in (("owner", self.owner), ("manager", self.manager)):
            self.case(role + "_own_store_management", lambda client=client: {"groups": len(self.get("option-library", client)["groups"])})
            self.case(role + "_foreign_org_store_rejected", lambda client=client: self.rejected(lambda: client.call("GET", f"/api/v1/stores/{self.foreign}/template/option-library"), (403, 404)))
            self.case(role + "_foreign_org_write_rejected", lambda client=client: self.rejected(lambda: client.call("PUT", f"/api/v1/stores/{self.foreign}/template/kitchen-routing", {"enabled": False, "groups": [], "item_overrides": []}), (403, 404)))
        if not self.case("owner_manager_create_no_group_items_atomic_catalog", self.create_items):
            return
        if not self.case("preset_custom_per_item_copy_price_default_two_level_order", self.configure_groups):
            return
        for variant in ("base_price", "option_price", "required", "single", "single_quantity", "item_scope", "unknown_item", "negative_item", "category_forgery", "sku_forgery"):
            self.case("server_rejects_" + variant, lambda variant=variant: self.validation(variant))
        self.case("pure_preview_has_no_order_job_intent_writes_one_group", self.preview_zero_write)
        self.case("three_group_shared_mock_printer_and_item_override_preview", self.three_preview)
        self.case("real_order_one_kitchen_frontdesk_and_idempotency", lambda: self.normal_order("one"))
        ready = self.case("real_order_three_kitchens_frontdesk_and_idempotency", self.normal_order)
        if ready:
            self.case("update_only_new_batch_and_idempotency", self.update)
            self.case("same_idempotency_key_different_payload_conflict", self.conflicting_payload)
            self.case("foreign_org_order_diagnostics_rejected", lambda: self.rejected(lambda: self.owner.call("GET", f"/api/v1/stores/{self.foreign}/template/printing-diagnostics?order_id={self.main_order}"), (403, 404)))
        self.case("fresh_concurrent_submit_single_order_three_intents_four_jobs", self.concurrent_submission)
        self.case("existing_combo_virtual_defaults_linked_child_preview_and_real_jobs", self.combo)
        self.case("combo_child_remove_full_snapshot_preview_and_real_child_ticket", self.combo_child_remove)
        self.case("known_combo_virtual_id_missing_code_group_rejected", self.combo_missing_virtual_metadata)
        self.case("unsupported_combo_behavior_fails_closed_no_business_artifacts", self.unsupported_combo_behavior)
        self.case("legacy_combo_side_without_linked_item_rejected_no_business_artifacts", self.combo_legacy_side_requires_linked_item)
        if ready:
            self.case("historical_snapshots_immutable_zero_price_custom_kitchen_instruction", self.snapshot_and_zero_price_instruction)
        for kind in ("MISSING", "FAILED", "UNCERTAIN"):
            self.case("legal_order_success_other_groups_continue_durable_" + kind.lower(), lambda kind=kind: self.fault(kind))

    def restore(self):
        if self.original_route is None:
            return
        def action():
            restored = self.put("kitchen-routing", self.original_route)
            for key in ("enabled", "groups", "item_overrides"):
                equal(restored[key], self.original_route[key], "Restore controlled synthetic routing " + key)
            return {"restored_original_synthetic_routing": True, "orders_and_jobs_retained": True}
        self.case("restore_original_synthetic_routing_without_deleting_artifacts", action)


def source_identity():
    def git(*args):
        return subprocess.check_output(["git", *args], cwd=REPO, stderr=subprocess.DEVNULL)
    digest = hashlib.sha256(git("diff", "--no-ext-diff", "--no-textconv", "--binary", "HEAD", "--", "backend", "frontend"))
    for relative in sorted(git("ls-files", "--others", "--exclude-standard", "-z", "--", "backend", "frontend").split(b"\0")):
        if relative:
            path = REPO / os.fsdecode(relative)
            if path.is_file():
                digest.update(relative + b"\0" + hashlib.sha256(path.read_bytes()).digest())
    return {"git_head": git("rev-parse", "HEAD").decode().strip(), "source_diff_digest": digest.hexdigest(),
            "dirty_source": bool(git("status", "--porcelain", "--", "backend", "frontend").strip())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, choices=sorted(ALLOWED_BASES))
    parser.add_argument("--credentials", type=Path, default=REPO / ".template-test-private" / "credentials.json")
    parser.add_argument("--foreign-store-id", type=int, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--runtime-evidence", type=Path, required=True,
                        help="Non-secret, integration-owner verified runtime ceiling evidence JSON")
    parser.add_argument("--wait-seconds", type=int, default=30)
    args = parser.parse_args()
    if stat.S_IMODE(args.credentials.stat().st_mode) != 0o600:
        raise SystemExit("Private credentials must have mode 0600; values were not read")
    credentials = json.loads(args.credentials.read_text())
    for key in ("owner_username", "owner_password", "manager_username", "manager_password"):
        if not isinstance(credentials.get(key), str) or not credentials[key]:
            raise SystemExit("Private credential file is missing a required key; no values logged")
    runtime_bytes = args.runtime_evidence.read_bytes()
    runtime_evidence = json.loads(runtime_bytes)
    runner = Acceptance(args.base_url, credentials, args.foreign_store_id, args.wait_seconds, runtime_evidence)
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    identity = source_identity()
    try:
        runner.run()
    finally:
        runner.restore()
        receipt = {"suite": "template-platform-real-http-v1", "base_url": args.base_url, "started_at": started,
                   "finished_at": dt.datetime.now(dt.timezone.utc).isoformat(), **identity, "results": runner.cases, "artifacts": runner.artifacts,
                   "request_count": runner.owner.requests + runner.manager.requests + runner.anonymous.requests,
                   "runtime_build_identity": "Managed separately by integration owner; source digest describes the client-start worktree, not an asserted running binary hash",
                   "runtime_evidence": {"path": str(args.runtime_evidence), "sha256": hashlib.sha256(runtime_bytes).hexdigest(), "evidence_ref": runtime_evidence.get("evidence_ref"),
                                        "classification": "INTEGRATION_OWNER_RUNTIME_ATTESTATION; not an invented HTTP property"},
                   "oracle_correction": {"prior_failure_receipt": "http-local-complete-guarded.json", "reason": "Previous all-Jobs execution_mode=MOCK assertion was incorrect for the unchanged legacy frontdesk MOCK branch",
                                         "source": "PrintDispatcherServiceImpl dispatchPersistedEvent MOCK_RENDERED predicate and original doDispatch Mock branch; PrintJobServiceImpl.toResponse nullable-IP display concatenation",
                                         "template_rule": "TEMPLATE_KITCHEN strictly MOCK, actual printer config type MOCK with no IP/port; only the known null:9100 display placeholder is tolerated",
                                         "legacy_rule": "Only FRONTDESK_RECEIPT may have null mode; runtime ceiling proof + current Store MOCK + no Job printer_id/endpoint required; REAL/PAD_DIRECT/unknown modules rejected",
                                         "protected_core_modified_for_oracle": False},
                   "legacy_mock_result_followup": {"job_ids": sorted(runner.legacy_mock_jobs), "http_api_exposes_mock_rendered_outcome": False,
                                                   "source_plus_parent_isolated_db_mock_result_proof": "PENDING_INTEGRATION_OWNER_SUPPLEMENT; not claimed by this HTTP client"},
                   "actual_kitchen_task_followup": runner.task_followup,
                   "boundaries": {"client_target": "ALLOWLISTED_LOCAL_OR_TEMPLATE_TEST_ONLY_NO_REDIRECTS", "data": "SYNTHETIC_ONLY", "credentials": "IN_PROCESS_ONLY_NOT_LOGGED", "existing_orders_jobs_deleted": False,
                                  "printing_http_guard": "MOCK mode, endpoint-free printer metadata and observed Job execution modes checked", "production_staging_android_external_platform": "NO_DIRECT_CLIENT_REQUESTS; server isolation proof belongs to integration owner"},
                   "limitations": ["HTTP assertions are not browser UI acceptance", "No physical printer or Pad execution", "No Production backup restore or multi-tenant SaaS security certification"]}
        receipt["summary"] = {status: sum(result["status"] == status for result in runner.cases) for status in ("PASS", "FAIL")}
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        descriptor = os.open(args.receipt, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, "w") as handle:
            json.dump(receipt, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print("SUMMARY " + json.dumps(receipt["summary"]) + " receipt=" + str(args.receipt), flush=True)
    return 1 if any(result["status"] == "FAIL" for result in runner.cases) else 0


if __name__ == "__main__":
    sys.exit(main())
