#!/usr/bin/env python3
"""Deterministic validation for the repository's small governance core."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "docs/governance"
STATE_PATH = CORE / "CURRENT_STATE.yml"
REQUIRED = (
    ROOT / "AGENTS.md",
    CORE / "AUTHORITY.md",
    STATE_PATH,
    CORE / "ROADMAP.md",
    CORE / "BACKLOG.md",
)
LINK_RE = re.compile(r"(?<!!)\[[^]]+\]\(([^)]+)\)")


class ValidationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def scalar(value: str):
    value = value.strip()
    if value == "true":
        return True
    if value == "false":
        return False
    if value in {"null", "~"}:
        return None
    if (value.startswith('"') and value.endswith('"')) or (
        value.startswith("'") and value.endswith("'")
    ):
        return value[1:-1]
    return value


def parse_mapping_yaml(path: Path) -> dict:
    """Parse the intentionally restricted mapping-only CURRENT_STATE schema."""

    root: dict = {}
    stack: list[tuple[int, dict]] = [(-1, root)]
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        require("\t" not in raw, f"{path}:{line_number}: tabs are not valid indentation")
        indent = len(raw) - len(raw.lstrip(" "))
        require(indent % 2 == 0, f"{path}:{line_number}: indentation must use two spaces")
        match = re.fullmatch(r"\s*([a-z][a-z0-9_]*):(?:\s*(.*))?", raw)
        require(match is not None, f"{path}:{line_number}: unsupported YAML shape")
        key, value = match.group(1), match.group(2) or ""
        while stack[-1][0] >= indent:
            stack.pop()
        parent = stack[-1][1]
        require(key not in parent, f"{path}:{line_number}: duplicate key {key}")
        if value == "":
            child: dict = {}
            parent[key] = child
            stack.append((indent, child))
        else:
            parent[key] = scalar(value)
    return root


def get(state: dict, dotted: str):
    value = state
    for part in dotted.split("."):
        require(isinstance(value, dict) and part in value, f"missing state key: {dotted}")
        value = value[part]
    return value


def validate_links(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    for raw_target in LINK_RE.findall(text):
        target = raw_target.strip().split(maxsplit=1)[0].strip("<>")
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        target = target.split("#", 1)[0]
        if not target:
            continue
        resolved = (path.parent / target).resolve()
        require(resolved.exists(), f"broken link in {path.relative_to(ROOT)}: {raw_target}")


def main() -> int:
    for path in REQUIRED:
        require(path.is_file(), f"missing mandatory authority file: {path.relative_to(ROOT)}")

    state_text = STATE_PATH.read_text(encoding="utf-8")
    state = parse_mapping_yaml(STATE_PATH)

    require(re.fullmatch(r"[0-9a-f]{40}", str(get(state, "repository.sha"))) is not None,
            "repository.sha must be a full lowercase Git SHA baseline")
    require(get(state, "repository.ref") == "origin/main", "repository.ref must be origin/main")
    require(re.fullmatch(r"[A-Z][A-Z0-9_]*", str(get(state, "phase.current"))) is not None,
            "phase.current must be one non-empty canonical identifier")
    require(re.fullmatch(r"[A-Z][A-Z0-9_]*", str(get(state, "phase.package"))) is not None,
            "phase.package must be one non-empty canonical identifier")
    require(get(state, "phase.next_phase_authorized") is False,
            "next phase must default to unauthorized")
    require(get(state, "gate.type") in {"TECHNICAL", "OWNER"}, "gate.type is invalid")
    require(bool(get(state, "gate.next_action")), "one next action is required")
    require(get(state, "production.mutation_authorized") is False,
            "Production mutation must default to false")
    require(get(state, "authorization.production") == "NONE",
            "Production authorization must be NONE")
    require(re.fullmatch(r"[A-Z][A-Z0-9_]*", str(get(state, "stop.marker"))) is not None,
            "stop.marker must be one non-empty canonical identifier")

    unique_keys = ("current", "package", "type", "next_action", "marker")
    for key in unique_keys:
        count = len(re.findall(rf"^\s*{key}:\s*\S+", state_text, flags=re.MULTILINE))
        require(count == 1, f"CURRENT_STATE must contain exactly one {key}; found {count}")

    agents = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
    for required_path in (
        "docs/governance/AUTHORITY.md",
        "docs/governance/CURRENT_STATE.yml",
    ):
        require(required_path in agents, f"AGENTS missing default-read path: {required_path}")
    for obsolete in (
        "docs/DatabaseDesign.md",
        "docs/API_bilingual.md",
        "docs/MVP_Scope.md",
        "CURRENT_HANDOFF.md",
        "ALIVE_RUNTIME_PLANBOOK.md",
    ):
        require(obsolete not in agents, f"AGENTS still requires obsolete authority: {obsolete}")

    legacy_live_paths = (
        CORE / "AGILE_LOOP_OPERATING_MODEL.md",
        CORE / "FEATURE_BACKLOG.md",
        CORE / "KNOWN_ISSUES_BACKLOG.md",
        CORE / "agile/FINAL_PRODUCTIZATION_PLANBOOK.md",
        CORE / "runtime/CURRENT_HANDOFF.md",
        CORE / "runtime/ALIVE_RUNTIME_PLANBOOK.md",
    )
    for path in legacy_live_paths:
        require(not path.exists(), f"superseded authority remains in a live path: {path}")

    for draft in (CORE / "drafts").glob("*.md"):
        if draft.name == "README.md":
            continue
        require("DRAFT / SUPPORTING / NOT AUTHORIZED" in draft.read_text(encoding="utf-8"),
                f"draft lacks non-authorization banner: {draft.relative_to(ROOT)}")

    for path in ROOT.rglob("*.md"):
        validate_links(path)

    print("GOVERNANCE_VALIDATION|PASS")
    print(f"PHASE|{get(state, 'phase.current')}")
    print(f"PACKAGE|{get(state, 'phase.package')}")
    print(f"GATE|{get(state, 'gate.type')}")
    print(f"STOP|{get(state, 'stop.marker')}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValidationError as error:
        print(f"GOVERNANCE_VALIDATION|FAIL|{error}", file=sys.stderr)
        raise SystemExit(1)
