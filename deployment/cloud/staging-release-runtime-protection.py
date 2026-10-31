#!/usr/bin/env python3
"""Read-only release references, including runtimes outside the legacy env file."""
import argparse
import datetime
import glob
import json
import os
from pathlib import Path
import re
import subprocess

SHA = re.compile(r"(?<![0-9a-f])[0-9a-f]{40}(?![0-9a-f])")
RELEASE_PATH = re.compile(r"/srv/restaurant-pos/staging/releases/([0-9a-f]{40})")


def reference_files(tree, state):
    """Batch the existing conservative text scan without shell-per-line forks."""
    for directory, dirs, files in os.walk(tree, followlinks=False):
        dirs[:] = [name for name in dirs if Path(directory) / name != state / 'postgres']
        for name in sorted(files):
            path = Path(directory) / name
            if path.is_symlink() or not path.is_file():
                raise RuntimeError('unsafe reference file: ' + str(path))
            body = path.read_bytes()
            if path.suffix == '.dump' and body.startswith(b'PGDMP'):
                continue  # Protected database backup, outside release deletion.
            if b'\0' in body:
                yield '*', 'opaque_reference_retains_all', name
                continue
            text = body.decode('utf-8', errors='replace')
            if text.startswith('RELEASE_RETENTION|SCHEMA|'):
                continue
            all_shas = bool(re.search('recovery|rollback', name, re.I))
            for line in text.splitlines():
                if all_shas or re.search('previous_verified|retain_release|protected_release|rollback_sha|recovery_sha', line, re.I):
                    for sha in SHA.findall(line):
                        yield sha, 'evidence_or_recovery_reference', name


def successful_releases(root):
    results = {}
    for path in sorted([*root.glob("*/result.json"), *root.glob("state/release-success/*.json")]):
        data = json.loads(path.read_text())
        sha = data.get("source_sha", "")
        if (data.get("environment") != "staging" or not SHA.fullmatch(sha)
                or not (data.get("health") == "PASS" or data.get("result") == "PASS")):
            continue
        # A result is successful deployment evidence, not business acceptance.
        timestamp = data.get("deployed_at")
        when = (datetime.datetime.fromisoformat(timestamp.replace("Z", "+00:00")).timestamp()
                if timestamp else path.stat().st_mtime)
        results[sha] = max(when, results.get(sha, 0))
    return [sha for sha, _ in sorted(results.items(), key=lambda x: x[1], reverse=True)]


def collect(root):
    protected = {}

    def add(text, reason):
        for sha in SHA.findall(str(text)):
            protected.setdefault(sha, set()).add(reason)

    successes = successful_releases(root)
    if len(successes) < 5:
        for release in (root / 'releases').iterdir():
            add(release.name, 'success_history_incomplete')
    for sha in successes[:5]:
        add(sha, "latest_five_successful_deployments")

    # No raw inspect or secret-bearing configuration is printed.
    ids = subprocess.check_output(
        ["docker", "--context", "default", "ps", "-aq"], text=True).split()
    if not ids:
        raise RuntimeError("container inventory unavailable")
    containers = json.loads(subprocess.check_output(
        ["docker", "--context", "default", "inspect", *ids], text=True))
    for container in containers:
        labels = container["Config"].get("Labels") or {}
        for key in ("com.docker.compose.project.working_dir", "com.docker.compose.project.config_files",
                    "org.opencontainers.image.revision"):
            add(labels.get(key, ""), "container_reference")
        for mount in container.get("Mounts", []):
            source = os.path.realpath(mount["Source"])
            add(source, "container_mount")
            # An ancestor bind exposes every child, even without explicit SHA.
            releases = root / "releases"
            if source == str(releases) or str(releases).startswith(source.rstrip("/") + "/"):
                for release in releases.iterdir():
                    add(release.name, "container_mount_ancestor")

    # Walk metadata and operational configuration, never DB/backups/uploads or
    # historical source checkouts. Rollback models remain explicit references.
    roots = [Path("/etc/nginx"), Path("/etc/systemd/system"), Path("/etc/cron.d"),
             Path("/usr/local/bin"), Path("/usr/local/sbin"),
             Path("/home/ubuntu/Restaurant_System/deployment/cloud"), root.parent]
    excluded = {".git", "repository.git", "releases", "postgres", "backups", "uploads",
                "node_modules", "data", "artifacts", "evidence", "state", "render-proof", "classes"}
    for base in roots:
        if not base.exists():
            continue
        for directory, dirs, files in os.walk(base, followlinks=False):
            for name in dirs + files:
                path = Path(directory) / name
                if path.is_symlink():
                    target = str(path.resolve())
                    add(target, "symlink_reference")
                    if target == str(root / "releases"):
                        for release in (root / "releases").iterdir():
                            add(release.name, "symlink_release_root")
            dirs[:] = [d for d in dirs if d not in excluded and not (Path(directory) / d).is_symlink()]
            for name in files:
                path = Path(directory) / name
                if path.is_symlink() or not path.is_file():
                    continue
                if (path.suffix not in (".sh", ".py", ".json", ".yml", ".yaml", ".conf", ".template")
                        and not name.startswith('.env')):
                    continue
                if path.stat().st_size > 4_000_000:
                    raise RuntimeError("oversized operational reference: " + str(path))
                text = path.read_text(errors="strict")
                for sha in RELEASE_PATH.findall(text):
                    add(sha, "external_path_reference")
                if "rollback" in name.lower() or "recovery" in name.lower():
                    add(text, "explicit_rollback_model")

    # current/previous/rollback may be absent on this server; do not invent them.
    for name in ("current", "previous", "rollback"):
        path = root / name
        if path.is_symlink():
            add(str(path.resolve()), name + "_symlink")

    # sudo is used by the caller so other-user processes cannot hide open files.
    for process in Path("/proc").iterdir():
        if not process.name.isdigit():
            continue
        for path in [process / "cwd", process / "root", process / "exe", *process.glob("fd/*")]:
            try:
                target = os.readlink(path)
            except FileNotFoundError:
                continue  # A process/fd can disappear during the observation.
            except PermissionError:
                raise RuntimeError("cannot inspect process reference: " + str(path))
            for sha in RELEASE_PATH.findall(target):
                add(sha, "open_process_reference")
    return protected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--references', type=Path)
    parser.add_argument('--state-dir', type=Path)
    args = parser.parse_args()
    if args.references:
        if not args.state_dir:
            raise RuntimeError('state directory required')
        for row in sorted(set(reference_files(args.references, args.state_dir))):
            if any('|' in part or '\n' in part for part in row):
                raise RuntimeError('unsafe reference filename')
            print('|'.join(row))
        return
    root = Path("/srv/restaurant-pos/staging")
    if root.is_symlink() or root.resolve() != root:
        raise RuntimeError("unexpected staging root")
    for sha, reasons in sorted(collect(root).items()):
        print(sha + "|" + ",".join(sorted(reasons)))


if __name__ == "__main__":
    main()
