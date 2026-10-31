#!/usr/bin/env python3
"""Package verified local artifacts; transfer only the closed Test bundle.

No Maven/npm/remote commands run on import. pack/upload are explicit actions.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
SHA = re.compile(r"[0-9a-f]{40}")
CHECKS = ("backend", "frontend", "integration", "security", "legacy")
FILES = ("compose.yml", "Dockerfile.backend", "Dockerfile.frontend", "nginx.conf",
         "relay.conf", "images.json", "remote.py")
SSH = ["ssh", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
       "-o", "UpdateHostKeys=no", "-o", "ControlMaster=no", "-o", "ControlPath=none",
       "-o", "PermitLocalCommand=no", "-o", "RequestTTY=no", "restaurant-prod"]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def dist_files(root):
    paths = sorted(p for p in root.rglob("*") if p.is_file())
    if not (root / "index.html").is_file() or any(p.is_symlink() for p in root.rglob("*")):
        raise ValueError("dist must contain index.html and no symlinks")
    return {"dist/" + p.relative_to(root).as_posix(): p.read_bytes() for p in paths}


def tree_digest(files):
    h = hashlib.sha256()
    for name, data in sorted(files.items()):
        h.update(name.encode() + b"\0" + hashlib.sha256(data).digest())
    return h.hexdigest()


def receipt_ok(receipt, sha, jar_hash, dist_hash):
    if receipt.get("code_sha") != sha or not SHA.fullmatch(sha):
        raise ValueError("receipt/code SHA mismatch")
    if any(receipt.get("checks", {}).get(k) != "PASS" for k in CHECKS):
        raise ValueError("all five executed check groups must PASS")
    if receipt.get("independent_review") != "ACCEPT":
        raise ValueError("required independent review has not ACCEPTED")
    if not receipt.get("commands") or receipt.get("jar_sha256") != jar_hash or receipt.get("dist_sha256") != dist_hash:
        raise ValueError("executed commands and exact artifact hashes required")
    if receipt.get("environment") != "ISOLATED_SYNTHETIC_MOCK":
        raise ValueError("receipt is not for the isolated synthetic MOCK environment")


def pack(evidence, output):
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip()
    dirty = subprocess.check_output(["git", "status", "--porcelain"], cwd=REPO)
    if dirty:
        raise ValueError("commit the reviewed candidate first; worktree is not clean")
    jars = list((REPO / "backend/target").glob("*.jar"))
    if len(jars) != 1 or jars[0].is_symlink():
        raise ValueError("exactly one compiled Spring Boot jar required")
    files = dist_files(REPO / "frontend/dist")
    jar = jars[0].read_bytes()
    receipt = json.loads(Path(evidence).read_text())
    receipt_ok(receipt, sha, digest(jar), tree_digest(files))
    if not any(sha.encode() in b for b in files.values()):
        raise ValueError("frontend must be built with VITE_APP_BUILD_VERSION=exact SHA")
    files.update({n: (HERE / n).read_bytes() for n in FILES})
    files["app.jar"] = jar
    files["code-sha.txt"] = (sha + '\n').encode()
    files["checks.json"] = json.dumps(receipt, sort_keys=True).encode()
    migrations = REPO / "backend/src/main/resources/db/migration"
    manifest = {"code_sha": sha, "environment": "ISOLATED_SYNTHETIC_MOCK",
                "files": {n: digest(b) for n, b in sorted(files.items())},
                "jar_sha256": digest(jar), "dist_sha256": tree_digest(dist_files(REPO / "frontend/dist")),
                "expected_migrations": sorted(p.name for p in migrations.glob("V*__*.sql"))}
    files["manifest.json"] = json.dumps(manifest, sort_keys=True, indent=2).encode()
    output = Path(output).resolve()
    if output.exists() or REPO in output.parents:
        raise ValueError("bundle output must be a new private path outside repository")
    with output.open("xb") as f:
        os.fchmod(f.fileno(), 0o600)
        with tarfile.open(fileobj=f, mode="w:gz") as t:
            for n, b in sorted(files.items()):
                item = tarfile.TarInfo(n)
                item.size, item.mode = len(b), 0o600
                t.addfile(item, io.BytesIO(b))
    print(json.dumps({"code_sha": sha, "bundle_sha256": digest(output.read_bytes()),
                      "bundle_path": str(output), "jar_sha256": digest(jar),
                      "dist_sha256": manifest["dist_sha256"]}))


def upload(bundle, sha):
    if not SHA.fullmatch(sha):
        raise ValueError("full expected SHA required")
    raw = Path(bundle).read_bytes()
    if len(raw) > 256 * 1024 * 1024:
        raise ValueError("bundle unexpectedly large")
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as t:
        members = t.getmembers()
        if any(not m.isfile() or m.name.startswith("/") or ".." in Path(m.name).parts for m in members):
            raise ValueError("unsafe archive member")
        manifest = json.load(t.extractfile("manifest.json"))
        if manifest["code_sha"] != sha:
            raise ValueError("wrong bundle SHA")
        data = {m.name: t.extractfile(m).read() for m in members}
        if len(data) != len(members) or set(data) != set(manifest["files"]) | {"manifest.json"}:
            raise ValueError("archive inventory mismatch")
        if any(digest(data[n]) != h for n, h in manifest["files"].items()):
            raise ValueError("archive file hash mismatch")
        receipt_ok(json.loads(data["checks.json"]), sha, digest(data["app.jar"]),
                   tree_digest({n: b for n, b in data.items() if n.startswith("dist/")}))
    # No shell interpolation of bundle data; only a validated hex SHA is inserted.
    code = """import sys,pathlib,tarfile,io,os
r=pathlib.Path('/srv/restaurant-pos/test')
for p in [r,r/'releases']:
 if p.is_symlink(): raise SystemExit('symlink refused')
 p.mkdir(mode=0o700,exist_ok=True)
 os.chmod(p,0o700)
d=r/'releases'/'SHA_VALUE'
if d.exists(): raise SystemExit('release already exists; do not overwrite')
d.mkdir(mode=0o700)
with tarfile.open(fileobj=io.BytesIO(sys.stdin.buffer.read()),mode='r:gz') as t:
 for m in t.getmembers():
  if not m.isfile() or m.name.startswith('/') or '..' in pathlib.Path(m.name).parts: raise SystemExit('unsafe member')
  p=d/m.name
  p.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
  with p.open('xb') as f:
   os.fchmod(f.fileno(),0o600);f.write(t.extractfile(m).read())
print('Test bundle transferred; no service created')
""".replace("SHA_VALUE", sha)
    import shlex
    result = subprocess.run(SSH + ["python3 -c " + shlex.quote(code)], input=raw, capture_output=True)
    if result.returncode:
        raise ValueError("Test transfer failed: " + result.stderr.decode()[:300])
    print(result.stdout.decode().strip())


def main():
    p = argparse.ArgumentParser(description=__doc__)
    s = p.add_subparsers(dest="action", required=True)
    x = s.add_parser("pack"); x.add_argument("--evidence", required=True); x.add_argument("--out", required=True)
    x = s.add_parser("upload"); x.add_argument("--bundle", required=True); x.add_argument("--expected-sha", required=True)
    s.add_parser("artifact-hashes")
    a = p.parse_args()
    if a.action == "pack": pack(a.evidence, a.out)
    elif a.action == "upload": upload(a.bundle, a.expected_sha)
    else:
        jars = list((REPO / "backend/target").glob("*.jar"))
        if len(jars) != 1: raise ValueError("one jar required")
        print(json.dumps({"jar_sha256": digest(jars[0].read_bytes()),
                          "dist_sha256": tree_digest(dist_files(REPO / "frontend/dist"))}))


if __name__ == "__main__":
    try: main()
    except (ValueError, OSError, subprocess.SubprocessError) as e: raise SystemExit(str(e))
