#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT=/srv/restaurant-pos/staging
ACTION="${1:---dry-run}"
[[ "$ACTION" == --dry-run || "$ACTION" == --execute ]] || exit 2
[[ -f "$ROOT/config/disk-retention-enabled" && ! -L "$ROOT/config/disk-retention-enabled" ]] || {
  echo 'RELEASE_RETENTION|SKIP|standing_policy_not_installed'; exit 0;
}
# The deployment wrapper takes this same lock around build/up. Other legacy
# paths are also checked through build history/process inspection.
exec 8<>"$ROOT/state/server-disk-maintenance.lock"
flock -n 8 || { echo 'RELEASE_RETENTION|SKIP|maintenance_or_deploy_active'; exit 0; }
exec 7<>"$ROOT/state/al003s-acceptance.lock"
flock -n 7 || { echo 'RELEASE_RETENTION|SKIP|staging_action_active'; exit 0; }
exec 6<>"$ROOT/state/phase-b-part1-acceptance.lock"
flock -n 6 || exit 0
exec 5<>"$ROOT/state/phase-b-part2-acceptance.lock"
flock -n 5 || exit 0
python3 -I - "$SCRIPT_DIR" <<'PY'
import importlib.util, pathlib, sys
p=pathlib.Path(sys.argv[1])/'server-buildkit-retention.py'
s=importlib.util.spec_from_file_location('cache_policy',p)
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
m.idle()  # Read-only; no cache cleanup in the release-retention domain.
PY
plan="$(mktemp "$ROOT/evidence/retention-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX.plan")"
bash "$SCRIPT_DIR/staging-release-retention.sh" --dry-run \
  --env-file "$ROOT/config/.env.staging" >"$plan"
cat "$plan"
if [[ "$ACTION" == --execute ]]; then
  digest="$(sha256sum "$plan" | awk '{print $1}')"
  bash "$SCRIPT_DIR/staging-release-retention.sh" --execute \
    --env-file "$ROOT/config/.env.staging" --plan-file "$plan" --plan-sha256 "$digest"
fi
