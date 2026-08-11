#!/usr/bin/env python3
"""Reconcile TWIN-001 staff through the existing Staging Staff API.

All passwords enter through a mode-0600 inherited descriptor.  Responses and
stdout are sanitized; access/refresh tokens are retained in memory only.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import time
import urllib.error
import urllib.parse
import urllib.request


API_BASE = "http://127.0.0.1:18080/api/v1"
STORE_ID = 1
ORGANIZATION_ID = 1
TARGET_OWNER = "owner"
EXPECTED_STAFF = {"manager": "MANAGER", "staffA": "FRONTDESK", "staffB": "FRONTDESK"}
STAGING_STORE_CODE = "STG005_SRC_20260809_R01"
STAGING_ORGANIZATION_CODE = "STG005_ORG_20260809_R01"
EXPECTED_MANIFEST_FINGERPRINT = "1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_secrets(fd: int) -> dict:
    info = os.fstat(fd)
    require(stat.S_ISREG(info.st_mode), "secret descriptor must reference a regular file")
    require(stat.S_IMODE(info.st_mode) == 0o600, "secret file mode must be 0600")
    with os.fdopen(os.dup(fd), "r", encoding="utf-8") as handle:
        value = json.load(handle)
    require(set(value) == {"owner_login_identifier", "owner_target_identifier", "owner_login_password", "staff"}, "secret payload fields mismatch")
    require(value["owner_login_identifier"] in ("STG005_OWNER_20260808_R01", TARGET_OWNER), "unexpected current Owner identifier")
    require(value["owner_target_identifier"] == TARGET_OWNER, "unexpected target Owner identifier")
    require(isinstance(value["owner_login_password"], str) and 12 <= len(value["owner_login_password"]) <= 256, "Owner password contract mismatch")
    staff = value["staff"]
    require(isinstance(staff, list) and len(staff) == 3, "exactly three staff credentials are required")
    parsed = {}
    for row in staff:
        require(set(row) == {"username", "role_code", "password"}, "staff secret fields mismatch")
        require(row["username"] in EXPECTED_STAFF and row["role_code"] == EXPECTED_STAFF[row["username"]], "staff identity/role mismatch")
        require(isinstance(row["password"], str) and len(row["password"]) == 20, "staff password must use the reviewed 20-character contract")
        require(row["password"] != value["owner_login_password"], "staff credential must differ from Owner credential")
        require(row["username"] not in parsed, "duplicate staff credential")
        parsed[row["username"]] = row
    require(set(parsed) == set(EXPECTED_STAFF), "staff credential set mismatch")
    require(len({row["password"] for row in staff}) == 3, "staff credentials must be independent")
    return value


def validate_runtime_evidence(path: Path, expected_digest: str, expected_sha: str) -> None:
    require(path.is_absolute(), "runtime evidence path must be absolute")
    info = path.stat()
    require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600, "runtime evidence must be a mode-0600 regular file")
    require(time.time() - info.st_mtime <= 900, "runtime evidence is older than 15 minutes")
    raw = path.read_bytes()
    require(hashlib.sha256(raw).hexdigest() == expected_digest, "runtime evidence digest mismatch")
    text = raw.decode("utf-8")
    require(f"|APPROVED_SHA|{expected_sha}\n" in text, "runtime evidence SHA binding mismatch")
    require("|FLYWAY|count=10|max_version=10|" in text, "runtime evidence is not exact Flyway V10")
    require("|STATUS|PASS\n" in text, "runtime evidence is not PASS")


def validate_workspace(client: "ApiClient", token: str) -> None:
    workspaces = client.call("GET", "/me/workspaces", token=token)
    require(workspaces["default_store_id"] == STORE_ID, "unexpected default Store")
    organizations = workspaces.get("organizations")
    stores = workspaces.get("stores")
    require(isinstance(organizations, list) and len(organizations) == 1, "Owner Organization workspace is not exact")
    require(isinstance(stores, list) and len(stores) == 1, "Owner Store workspace is not exact")
    require(organizations[0]["id"] == ORGANIZATION_ID and organizations[0]["code"] == STAGING_ORGANIZATION_CODE and organizations[0]["status"].lower() == "active", "Staging Organization workspace identity mismatch")
    require(stores[0]["id"] == STORE_ID and stores[0]["organization_id"] == ORGANIZATION_ID and stores[0]["code"] == STAGING_STORE_CODE and stores[0]["status"].lower() == "active", "Staging Store workspace identity mismatch")
    context = client.call("GET", f"/stores/{STORE_ID}/context", token=token)
    require(context["id"] == STORE_ID and context["organization_id"] == ORGANIZATION_ID and context["code"] == STAGING_STORE_CODE and context["organization_code"] == STAGING_ORGANIZATION_CODE and context["status"].lower() == "active", "Staging Store context identity mismatch")


class ApiClient:
    def __init__(self, base: str):
        require(base == API_BASE, "TWIN-001 staff action is loopback-Staging only")
        self.base = base

    def call(self, method: str, path: str, body: dict | None = None, token: str | None = None) -> dict:
        require(path.startswith("/") and ".." not in path, "unsafe API path")
        data = None if body is None else json.dumps(body, separators=(",", ":")).encode()
        headers = {"Accept": "application/json", "Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                require(200 <= response.status < 300, f"API HTTP {response.status}")
                value = json.loads(response.read().decode())
        except urllib.error.HTTPError as exception:
            raise RuntimeError(f"Staging API request failed with HTTP {exception.code}") from None
        require(value.get("success") is True, "Staging API returned unsuccessful JSON")
        return value.get("data")


def login(client: ApiClient, identifiers: list[str], password: str) -> tuple[dict, str]:
    last_error = None
    for identifier in dict.fromkeys(identifiers):
        try:
            data = client.call("POST", "/auth/login", {"login_identifier": identifier, "password": password})
            require(data["user"]["role_code"] == "OWNER" and data["user"]["organization_id"] == ORGANIZATION_ID, "login is not the approved Organization Owner")
            require(data["user"]["username"] == identifier, "login principal mismatch")
            require(isinstance(data["access_token"], str) and len(data["access_token"]) > 20, "access token missing")
            require(isinstance(data["refresh_token"], str) and len(data["refresh_token"]) > 20, "refresh token missing")
            return data, identifier
        except (RuntimeError, ValueError) as exception:
            last_error = exception
    raise RuntimeError("Neither retained nor target synthetic Owner credential could authenticate") from last_error


def reconcile(client: ApiClient, secret: dict) -> None:
    session, login_identifier = login(
        client,
        [secret["owner_login_identifier"], secret["owner_target_identifier"]],
        secret["owner_login_password"],
    )
    token = session["access_token"]
    refresh = session["refresh_token"]
    owner_id = session["user"]["id"]
    validate_workspace(client, token)
    staff = client.call("GET", f"/admin/staff?store_id={STORE_ID}", token=token)
    require(isinstance(staff, list), "staff response is not a list")
    by_username = {row["username"]: row for row in staff}
    require(len(by_username) == len(staff), "duplicate Staging usernames")

    if login_identifier != TARGET_OWNER:
        require(set(by_username) == {login_identifier}, "Owner rename requires the exact one-user baseline")
        updated = client.call(
            "PUT",
            f"/admin/staff/{owner_id}",
            {"store_id": STORE_ID, "username": TARGET_OWNER, "full_name": None, "phone": None, "role_code": "OWNER"},
            token,
        )
        require(updated["username"] == TARGET_OWNER and updated["role_code"] == "OWNER", "Owner rename did not persist")
        by_username = {TARGET_OWNER: updated}

    for row in sorted(secret["staff"], key=lambda item: item["username"]):
        existing = by_username.get(row["username"])
        if existing is not None:
            require(existing["role_code"] == row["role_code"] and existing["status"].lower() == "active" and existing["store_id"] == STORE_ID, f"staff conflict for {row['username']}")
            continue
        created = client.call(
            "POST",
            "/admin/staff",
            {"store_id": STORE_ID, "username": row["username"], "full_name": None, "phone": None, "role_code": row["role_code"], "password": row["password"]},
            token,
        )
        require(created["username"] == row["username"] and created["role_code"] == row["role_code"], f"staff creation mismatch for {row['username']}")
        by_username[row["username"]] = created

    final = client.call("GET", f"/admin/staff?store_id={STORE_ID}", token=token)
    observed = {(row["username"], row["role_code"], row["status"].lower()) for row in final}
    expected = {(TARGET_OWNER, "OWNER", "active")} | {(username, role, "active") for username, role in EXPECTED_STAFF.items()}
    require(observed == expected, "final staff username/role set mismatch")
    final_by_username = {row["username"]: row for row in final}
    for row in sorted(secret["staff"], key=lambda item: item["username"]):
        try:
            staff_session = client.call("POST", "/auth/login", {"login_identifier": row["username"], "password": row["password"]})
        except RuntimeError:
            client.call("POST", f"/admin/staff/{final_by_username[row['username']]['id']}/reset-password", {"new_password": row["password"]}, token)
            staff_session = client.call("POST", "/auth/login", {"login_identifier": row["username"], "password": row["password"]})
        staff_user = staff_session["user"]
        require(staff_user["username"] == row["username"] and staff_user["role_code"] == row["role_code"] and staff_user["organization_id"] == ORGANIZATION_ID and staff_user["store_id"] == STORE_ID, f"staff credential verification mismatch for {row['username']}")
        require(isinstance(staff_session["access_token"], str) and isinstance(staff_session["refresh_token"], str), "verified staff session tokens missing")
        client.call("POST", "/auth/logout", {"refresh_token": staff_session["refresh_token"]}, staff_session["access_token"])
    client.call("POST", "/auth/logout", {"refresh_token": refresh}, token)
    print("TWIN001_STAFF|status=PASS|users=4|username_parity=YES|role_parity=YES|credential_parity=NO")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--secrets-fd", type=int)
    parser.add_argument("--api-base", default=API_BASE)
    parser.add_argument("--expected-runtime-sha", required=True)
    parser.add_argument("--runtime-evidence", type=Path, required=True)
    parser.add_argument("--runtime-evidence-sha256", required=True)
    parser.add_argument("--manifest-fingerprint", required=True)
    args = parser.parse_args()
    require(args.execute, "staff reconciliation requires --execute")
    require(args.secrets_fd is not None and args.secrets_fd > 2, "an inherited secret descriptor is required")
    require(len(args.expected_runtime_sha) == 40 and all(character in "0123456789abcdef" for character in args.expected_runtime_sha), "expected runtime SHA must be a full lowercase SHA")
    require(len(args.runtime_evidence_sha256) == 64 and all(character in "0123456789abcdef" for character in args.runtime_evidence_sha256), "runtime evidence digest must be lowercase SHA-256")
    require(args.manifest_fingerprint == EXPECTED_MANIFEST_FINGERPRINT, "manifest identity mismatch")
    validate_runtime_evidence(args.runtime_evidence, args.runtime_evidence_sha256, args.expected_runtime_sha)
    reconcile(ApiClient(args.api_base), read_secrets(args.secrets_fd))


if __name__ == "__main__":
    main()
