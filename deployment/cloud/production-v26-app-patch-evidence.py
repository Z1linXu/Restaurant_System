#!/usr/bin/env python3
import argparse
import hashlib
import json
import re

PASS_FIELDS = (
    "fresh_create",
    "replay",
    "foreign_active_organization_denied",
    "manager_denied",
    "live_context",
    "frontdesk_defaults",
    "printing_management",
    "printing_endpoint_free",
    "final_result",
)
KEYS = {
    "schema", "run_id", "source_sha", "backend_image_id", "frontend_image_id", "environment_sha256",
    "runtime_preflight_sha256", "owner_approval_sha256",
    "request_fingerprint", "request_body_sha256", "organization_id",
    "foreign_organization_id", "store_id", *PASS_FIELDS,
}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate key: {key}")
        result[key] = value
    return result


def sha256_lines(lines):
    payload = "".join(f"{line}\n" for line in lines).encode()
    return hashlib.sha256(payload).hexdigest()


def validate(path, source_sha, backend_image_id, frontend_image_id, env_sha, preflight_sha, approval_sha, run_id):
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle, object_pairs_hook=unique_object)
    if set(data) != KEYS:
        raise ValueError("acceptance evidence keys differ")
    if data["schema"] != "V26_BUSINESS_STORE_CREATE_ACCEPTANCE_V1":
        raise ValueError("acceptance evidence schema differs")
    expected = {
        "source_sha": source_sha,
        "backend_image_id": backend_image_id,
        "frontend_image_id": frontend_image_id,
        "environment_sha256": env_sha,
        "runtime_preflight_sha256": preflight_sha,
        "owner_approval_sha256": approval_sha,
        "run_id": run_id,
    }
    for key, value in expected.items():
        if data[key] != value:
            raise ValueError(f"acceptance evidence {key} differs")
    if not re.fullmatch(r"[0-9a-f]{32}", run_id):
        raise ValueError("acceptance run ID is invalid")
    for key in ("source_sha", "environment_sha256", "runtime_preflight_sha256", "owner_approval_sha256", "request_fingerprint", "request_body_sha256"):
        size = 40 if key == "source_sha" else 64
        if not isinstance(data[key], str) or not re.fullmatch(rf"[0-9a-f]{{{size}}}", data[key]):
            raise ValueError(f"{key} is invalid")
    for key in ("backend_image_id", "frontend_image_id"):
        if not isinstance(data[key], str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", data[key]):
            raise ValueError(f"{key} is invalid")
    for key in ("organization_id", "foreign_organization_id", "store_id"):
        if not isinstance(data[key], int) or isinstance(data[key], bool) or data[key] <= 0:
            raise ValueError(f"{key} is invalid")
    if data["organization_id"] == data["foreign_organization_id"]:
        raise ValueError("foreign Organization is not distinct")
    if any(data[key] != "PASS" for key in PASS_FIELDS):
        raise ValueError("acceptance action gate differs")
    scope = (
        f"organization_id={data['organization_id']};target_store_id=;source_store_id=1;"
        f"profile_code=CHINATOWN_MENU_2026_02_02;preflight={preflight_sha};"
        f"acceptance_run_id={run_id}"
    )
    fingerprint = sha256_lines((
        "environment=restaurant-pos-staging",
        "action=business-store-create-acceptance",
        f"approved_sha={source_sha}",
        f"env_sha256={env_sha}",
        f"scope={scope}",
    ))
    if data["request_fingerprint"] != fingerprint:
        raise ValueError("acceptance request fingerprint differs")
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--acceptance", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--backend-image-id", required=True)
    parser.add_argument("--frontend-image-id", required=True)
    parser.add_argument("--environment-sha256", required=True)
    parser.add_argument("--runtime-preflight-sha256", required=True)
    parser.add_argument("--owner-approval-sha256", required=True)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    data = validate(
        args.acceptance, args.source_sha, args.backend_image_id, args.frontend_image_id, args.environment_sha256,
        args.runtime_preflight_sha256, args.owner_approval_sha256, args.run_id,
    )
    print(f"ACCEPTANCE_STORE_ID={data['store_id']}")
    print("ACCEPTANCE_TYPED_GATES=PASS")


if __name__ == "__main__":
    main()
