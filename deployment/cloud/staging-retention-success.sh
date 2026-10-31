#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
ROOT=/srv/restaurant-pos/staging
SHA="${1:-}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || exit 2
# Called only after a successful existing deploy, never by validation mode.
backend="$(docker --context default image inspect --format '{{.Id}}' "restaurant-pos-backend:staging-$SHA")"
frontend="$(docker --context default image inspect --format '{{.Id}}' "restaurant-pos-frontend:staging-$SHA")"
[[ "$(docker --context default inspect --format '{{.Image}}' restaurant-pos-staging-backend-1)" == "$backend" ]] || exit 2
[[ "$(docker --context default inspect --format '{{.Image}}' restaurant-pos-staging-nginx-1)" == "$frontend" ]] || exit 2
curl --fail --silent --max-time 10 http://127.0.0.1:18080/api/v1/system/health >/dev/null
python3 -I - "$SHA" "$backend" "$frontend" <<'PY'
import datetime,json,os,pathlib,re,sys,tempfile
sha=sys.argv[1]
if not re.fullmatch('[0-9a-f]{40}',sha):raise SystemExit('invalid release SHA')
root=pathlib.Path('/srv/restaurant-pos/staging/state/release-success')
if root.is_symlink() or root.parent.resolve()!=root.parent:raise SystemExit('unsafe success ledger')
root.mkdir(mode=0o700,exist_ok=True)
if root.stat().st_uid!=os.getuid() or root.stat().st_mode&0o077:raise SystemExit('unsafe success ledger metadata')
destination=root/(sha+'.json')
if destination.is_symlink():raise SystemExit('unsafe success marker')
fd,name=tempfile.mkstemp(dir=root,prefix='.success-')
with os.fdopen(fd,'w') as f:
 json.dump({'environment':'staging','source_sha':sha,'health':'PASS','backend_image_id':sys.argv[2],'frontend_image_id':sys.argv[3],'deployed_at':datetime.datetime.now(datetime.timezone.utc).isoformat()},f)
 f.write('\n')
os.replace(name,destination)
PY
