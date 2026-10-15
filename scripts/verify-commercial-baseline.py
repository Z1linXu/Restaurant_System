#!/usr/bin/env python3
"""Read-only source lineage and immutable migration guard; never contacts a server."""
import hashlib
import json
from pathlib import Path
import subprocess
import zlib

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True).strip()


def main():
    manifest = json.loads((ROOT / "docs/commercialization/phase2-baseline.json").read_text())
    for name, unit in manifest["units"].items():
        assert git("rev-parse", unit["source_commit"] + ":" + name) == unit["source_tree"], name
        assert git("rev-parse", manifest["source_main"] + ":" + name) == unit["source_tree"], name
        assert unit["source_commit"] == unit["oci_revision"], name
    android = manifest["android"]
    assert git("rev-parse", android["source_commit"] + ":restaurant-pad-app") == android["source_tree"]
    versions = set()
    for entry in manifest["migrations"]:
        raw = (ROOT / "backend/src/main/resources/db/migration" / entry["script"]).read_bytes()
        assert hashlib.sha256(raw).hexdigest() == entry["sha256"], entry["script"]
        checksum = zlib.crc32("".join(raw.decode("utf-8-sig").splitlines()).encode())
        checksum = checksum if checksum < 2**31 else checksum - 2**32
        assert checksum == entry["flyway_checksum"], entry["script"]
        versions.add(entry["version"])
    assert versions == set(range(1, 38))
    files = list((ROOT / "backend/src/main/resources/db/migration").glob("V*__*.sql"))
    all_versions = [int(p.name.split("__")[0][1:]) for p in files]
    assert len(all_versions) == len(set(all_versions)), "Duplicate migration version"
    print("PASS: B/F/Android lineage; immutable V1–V37 SHA256/Flyway checksums; unique migration versions")


if __name__ == "__main__":
    main()
