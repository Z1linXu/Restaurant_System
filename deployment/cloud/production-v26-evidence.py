#!/usr/bin/env python3
"""Strict marker validation for the Production V10 -> V26 release evidence."""

from __future__ import annotations

import argparse
import pathlib
import re


def require_exact_marker(markers: dict[str, list[tuple[int, str]]], prefix: str, line: str, label: str) -> None:
    matches = markers.get(prefix, [])
    if len(matches) != 1 or matches[0][1] != line:
        raise ValueError(f"{label} marker differs or is not unique")


def require_pattern_marker(markers: dict[str, list[tuple[int, str]]], prefix: str, pattern: str, label: str) -> None:
    matches = markers.get(prefix, [])
    if len(matches) != 1 or re.fullmatch(pattern, matches[0][1]) is None:
        raise ValueError(f"{label} marker differs or is not unique")


def verify_staging(full: str, repair: str, source_sha: str) -> None:
    if "NO_GO|" in full or "NO_GO|" in repair or "result=FAIL" in full or "result=FAIL" in repair:
        raise ValueError("Staging evidence contains a failure marker")
    acceptance_prefix = "PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE"
    acceptance_lines = [line for line in full.splitlines() if line.startswith(acceptance_prefix)]
    if acceptance_lines != ["PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE = PASS"]:
        raise ValueError("full Staging acceptance marker differs or is not unique")
    source_line = f"Final exact Staging artifact: `{source_sha}`"
    source_lines = [line for line in repair.splitlines() if line.startswith("Final exact Staging artifact:")]
    if source_lines != [source_line]:
        raise ValueError("Staging repair source SHA differs")
    flyway_lines = [line for line in repair.splitlines() if line.startswith("Flyway:")]
    if len(flyway_lines) != 1 or not flyway_lines[0].startswith("Flyway: V26"):
        raise ValueError("Staging repair Flyway marker differs")
    review_lines = [line for line in repair.splitlines() if "Agent 6 reviewed the implementation" in line]
    if len(review_lines) != 1:
        raise ValueError("Staging repair review marker differs")


def verify_rehearsal(
    text: str,
    source_sha: str,
    backend_image_id: str,
    frontend_image_id: str,
    backup_sha256: str,
    recovery_helper_sha256: str,
    business_fingerprint: str | None = None,
    printing_fingerprint: str | None = None,
) -> None:
    lines = text.splitlines()
    if "NO_GO|" in text or "RECOVERY_NO_GO|" in text or any("result=FAIL" in line for line in lines):
        raise ValueError("rehearsal evidence contains a failure marker")
    for line in lines:
        if "|result=" in line and not line.endswith("|result=PASS"):
            raise ValueError("rehearsal evidence contains a conflicting terminal result")

    required_prefixes = (
        "ANDROID_COMPATIBILITY",
        "RESTORE",
        "DATA_BASELINE",
        "MIGRATION",
        "ADDITIVE_INVARIANTS",
        "READ_SMOKE",
        "WRITE_SMOKE",
        "TARGET_STACK",
        "RECOVERY_RESTORE_FAILURE_PROOF",
        "RECOVERY_PROOF",
        "RESOURCE_CLEANUP",
        "REHEARSAL",
    )
    markers: dict[str, list[tuple[int, str]]] = {prefix: [] for prefix in required_prefixes}
    markers["LEGACY_READ_SMOKE"] = []
    for index, line in enumerate(lines):
        prefix = line.split("|", 1)[0]
        if prefix in markers:
            markers[prefix].append((index, line))

    for prefix in required_prefixes:
        if len(markers[prefix]) != 1:
            raise ValueError(f"{prefix} marker is missing or duplicated")
    legacy_markers = markers["LEGACY_READ_SMOKE"]
    if not 1 <= len(legacy_markers) <= 2:
        raise ValueError("legacy read smoke marker count differs")
    if any(not line.endswith("|result=PASS") for _, line in legacy_markers):
        raise ValueError("legacy read smoke contains a non-PASS result")
    all_run_bound_markers = [markers[prefix][0][1] for prefix in required_prefixes]
    all_run_bound_markers.extend(line for _, line in legacy_markers)
    run_ids: list[str] = []
    for line in all_run_bound_markers:
        match = re.match(r"^[A-Z_]+\|run_id=([0-9a-f]{32})\|", line)
        if match is None:
            raise ValueError("rehearsal marker lacks an exact run identity")
        run_ids.append(match.group(1))
    if len(set(run_ids)) != 1:
        raise ValueError("rehearsal evidence combines multiple run identities")
    run_id = run_ids[0]

    order = [
        markers[prefix][0][0]
        for prefix in (
            "ANDROID_COMPATIBILITY",
            "RESTORE",
            "DATA_BASELINE",
            "MIGRATION",
            "ADDITIVE_INVARIANTS",
            "READ_SMOKE",
            "WRITE_SMOKE",
            "TARGET_STACK",
            "RECOVERY_RESTORE_FAILURE_PROOF",
            "RECOVERY_PROOF",
            "RESOURCE_CLEANUP",
            "REHEARSAL",
        )
    ]
    if order != sorted(order) or len(set(order)) != len(order):
        raise ValueError("rehearsal evidence marker order differs")
    failure_index = markers["RECOVERY_RESTORE_FAILURE_PROOF"][0][0]
    recovery_index = markers["RECOVERY_PROOF"][0][0]
    if not failure_index < legacy_markers[-1][0] < recovery_index:
        raise ValueError("verified recovery smoke order differs")
    if len(legacy_markers) == 2 and not markers["TARGET_STACK"][0][0] < legacy_markers[0][0] < failure_index:
        raise ValueError("old-app-on-V26 classification order differs")

    require_exact_marker(
        markers,
        "RESTORE",
        f"RESTORE|run_id={run_id}|backup_sha256={backup_sha256}|flyway=V10-exact|network_internal=true|volume_isolated=true|result=PASS",
        "restore",
    )
    require_exact_marker(
        markers,
        "MIGRATION",
        f"MIGRATION|run_id={run_id}|from=V10|to=V26|migrations=16|ledger=exact|business_fingerprint=unchanged|printing_fingerprint=unchanged|result=PASS",
        "migration",
    )
    require_pattern_marker(
        markers,
        "DATA_BASELINE",
        rf"DATA_BASELINE\|run_id={run_id}\|business_fingerprint=[0-9a-f]{{64}}\|printing_fingerprint=[0-9a-f]{{64}}\|result=PASS",
        "data baseline",
    )
    if business_fingerprint is not None or printing_fingerprint is not None:
        if not business_fingerprint or not printing_fingerprint:
            raise ValueError("both expected data fingerprints are required")
        require_exact_marker(
            markers,
            "DATA_BASELINE",
            f"DATA_BASELINE|run_id={run_id}|business_fingerprint={business_fingerprint}|"
            f"printing_fingerprint={printing_fingerprint}|result=PASS",
            "frozen data baseline",
        )
    require_pattern_marker(
        markers,
        "READ_SMOKE",
        rf"READ_SMOKE\|run_id={run_id}\|.*historical_detail=PASS\|organization_claim_db_authority=PASS\|wrong_store=PASS\|websocket=PASS\|result=PASS",
        "read smoke",
    )
    require_pattern_marker(
        markers,
        "WRITE_SMOKE",
        rf"WRITE_SMOKE\|run_id={run_id}\|.*inventory_fixture=PASS\|options=PASS\|combo=PASS\|inventory=PASS\|printing_roles=PASS\|mock_endpoint_free=PASS\|result=PASS",
        "write smoke",
    )
    require_pattern_marker(markers, "ADDITIVE_INVARIANTS", rf"ADDITIVE_INVARIANTS\|run_id={run_id}\|.*violations=0\|result=PASS", "additive invariants")
    require_exact_marker(
        markers,
        "ANDROID_COMPATIBILITY",
        f"ANDROID_COMPATIBILITY|run_id={run_id}|app_version=0.2.0-offline-pr7|version_code=2|"
        "webview_entry=unchanged|auth_token=unchanged|register_heartbeat=additive|"
        "pad_direct_contract=unchanged|routes_headers=unchanged|min_version_guard=absent|result=PASS",
        "Android compatibility",
    )
    require_exact_marker(
        markers,
        "TARGET_STACK",
        f"TARGET_STACK|run_id={run_id}|backend_image_id={backend_image_id}|frontend_image_id={frontend_image_id}|"
        "restart=PASS|result=PASS",
        "target stack",
    )
    if re.fullmatch(rf"LEGACY_READ_SMOKE\|run_id={run_id}\|.*isolation=PASS\|result=PASS", legacy_markers[-1][1]) is None:
        raise ValueError("recovery read smoke marker differs")
    require_exact_marker(
        markers,
        "RECOVERY_RESTORE_FAILURE_PROOF",
        f"RECOVERY_RESTORE_FAILURE_PROOF|run_id={run_id}|restore_failed=true|primary_untouched=true|result=PASS",
        "restore failure safety",
    )
    require_exact_marker(
        markers,
        "RECOVERY_PROOF",
        f"RECOVERY_PROOF|run_id={run_id}|mode=validated-temp-db-switch|"
        f"recovery_helper_sha256={recovery_helper_sha256}|restore_failure_original_untouched=true|"
        "validated_temp=true|flyway=V10-exact|"
        "business_fingerprint=restored|result=PASS",
        "recovery",
    )
    require_pattern_marker(
        markers,
        "RESOURCE_CLEANUP",
        rf"RESOURCE_CLEANUP\|run_id={run_id}\|containers=0\|networks=0\|volumes=0\|children=0\|result=PASS",
        "resource cleanup",
    )
    require_exact_marker(
        markers,
        "REHEARSAL",
        f"REHEARSAL|run_id={run_id}|source_sha={source_sha}|backend_image_id={backend_image_id}|"
        f"frontend_image_id={frontend_image_id}|backup_sha256={backup_sha256}|"
        "production_clone=true|real_printer=false|real_pad=false|result=PASS",
        "artifact binding",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--staging-full", required=True)
    parser.add_argument("--staging-repair", required=True)
    parser.add_argument("--scope", choices=("staging", "full"), required=True)
    parser.add_argument("--rehearsal")
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--backend-image-id")
    parser.add_argument("--frontend-image-id")
    parser.add_argument("--backup-sha256")
    parser.add_argument("--recovery-helper-sha256")
    parser.add_argument("--business-fingerprint")
    parser.add_argument("--printing-fingerprint")
    args = parser.parse_args()

    verify_staging(
        pathlib.Path(args.staging_full).read_text(encoding="utf-8"),
        pathlib.Path(args.staging_repair).read_text(encoding="utf-8"),
        args.source_sha,
    )
    if args.scope == "full":
        required = {
            "rehearsal": args.rehearsal,
            "backend image ID": args.backend_image_id,
            "frontend image ID": args.frontend_image_id,
            "backup SHA-256": args.backup_sha256,
            "recovery helper SHA-256": args.recovery_helper_sha256,
        }
        missing = [label for label, value in required.items() if not value]
        if missing:
            parser.error("full scope is missing: " + ", ".join(missing))
        verify_rehearsal(
            pathlib.Path(args.rehearsal).read_text(encoding="utf-8"),
            args.source_sha,
            args.backend_image_id,
            args.frontend_image_id,
            args.backup_sha256,
            args.recovery_helper_sha256,
            args.business_fingerprint,
            args.printing_fingerprint,
        )
    print("EVIDENCE_CONTRACT|result=PASS")


if __name__ == "__main__":
    main()
