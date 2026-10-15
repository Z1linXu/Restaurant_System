#!/usr/bin/env python3
"""Scan only Commercial defaults/fixtures; historical evidence is not a default."""
import ipaddress
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
DEFAULTS = [
    ROOT / "backend/src/main/resources/application-commercial.yml",
    ROOT / "backend/src/test/java/com/restaurant/system/owner/profile/SyntheticPrivateProfileFixture.java",
]
DIRECTORIES = [ROOT / "deployment/commercial", ROOT / "docs/commercialization/fixtures"]


def violations(text):
    issues = []
    if re.search(r"lanzhounoodlesmtl\.com|170\.106\.13\.34|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", text, re.I):
        issues.append("real endpoint or private key")
    for match in re.findall(r"(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])", text):
        try:
            address = ipaddress.ip_address(match)
            if not address.is_loopback and not any(address in ipaddress.ip_network(n) for n in
                    ["192.0.2.0/24", "198.51.100.0/24", "203.0.113.0/24"]):
                issues.append("non-synthetic IP literal")
        except ValueError:
            pass
    for value in re.findall(r"(?:password|jwt[-_]secret|device[-_]token|client[-_]secret)[\"']?\s*[:=]\s*([^\n,}]+)", text, re.I):
        value = value.strip().strip("\"'")
        if value and not value.startswith(("${", "<")) and value not in ("null", "undefined"):
            issues.append("filled credential")
    for url in re.findall(r"https?://[^\s\"'<>]+", text, re.I):
        try:
            host = urlsplit(url).hostname or ""
            if host not in ("localhost", "127.0.0.1", "::1") and not host.endswith(".invalid"):
                issues.append("non-synthetic URL")
        except ValueError:
            issues.append("invalid URL")
    return sorted(set(issues))


def main():
    files = [Path(p) for p in sys.argv[1:]] if len(sys.argv) > 1 else DEFAULTS + [
        p for d in DIRECTORIES if d.exists() for p in d.rglob("*") if p.is_file()]
    failed = False
    for file in files:
        issues = violations(file.read_text())
        if issues:
            failed = True
            # Never echo a matched credential or endpoint into CI logs.
            print(f"FAIL {file.relative_to(ROOT) if file.is_relative_to(ROOT) else file.name}: {', '.join(issues)}")
    if failed:
        return 1
    print(f"PASS: {len(files)} Commercial default/fixture files; no runtime access")
    return 0


if __name__ == "__main__":
    sys.exit(main())
