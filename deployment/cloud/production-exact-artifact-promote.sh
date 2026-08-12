#!/usr/bin/env bash
set -Eeuo pipefail

readonly SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
readonly EXPECTED_PROJECT="cloud"
readonly EXPECTED_CONTROL_ROOT="/home/ubuntu/Restaurant_System/deployment/cloud"
readonly EXPECTED_POSTGRES_DATA_DIR="$EXPECTED_CONTROL_ROOT/data/postgres"
readonly EXPECTED_NGINX_TEMPLATE="$EXPECTED_CONTROL_ROOT/data/nginx/default.conf.template"
readonly EXPECTED_CERTBOT_WWW_DIR="$EXPECTED_CONTROL_ROOT/data/certbot-www"
readonly EXPECTED_LETSENCRYPT_DIR="$EXPECTED_CONTROL_ROOT/data/letsencrypt"
readonly MIN_AVAILABLE_MEMORY_KB=1048576
readonly OPS_LOCK="$EXPECTED_CONTROL_ROOT/.production-ops.lock"

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_COMPOSE="$EXPECTED_CONTROL_ROOT/docker-compose.yml"
OVERRIDE_COMPOSE="$SCRIPT_DIR/docker-compose.production-promotion.yml"
ENV_FILE="$EXPECTED_CONTROL_ROOT/.env"
FLYWAY_MANIFEST="$SCRIPT_DIR/ops001-flyway-checksums.txt"
ACTION="validate"
RC_MANIFEST=""
RC_MANIFEST_SHA256=""

die() { printf 'NO_GO|%s\n' "$*" >&2; exit 1; }
digest() { sha256sum "$1" | awk '{print $1}'; }
path_has_symlink() {
  local path="$1" part current="" old_ifs="$IFS"
  IFS='/'; set -- $path; IFS="$old_ifs"
  for part in "$@"; do
    [[ -n "$part" ]] || continue
    current="$current/$part"
    [[ ! -L "$current" ]] || return 0
  done
  return 1
}
require_real_path() {
  local path="$1" kind="$2" expected_uid="$3"
  [[ "$path" == /* ]] || die "$kind path is not absolute"
  [[ -e "$path" && ! -L "$path" ]] || die "$kind path is missing or symlinked"
  path_has_symlink "$path" && die "$kind path traverses a symlink"
  [[ "$(realpath "$path")" == "$path" ]] || die "$kind path is not canonical"
  [[ "$(stat -c '%u' "$path")" == "$expected_uid" ]] || die "$kind owner differs"
  [[ $((8#$(stat -c '%a' "$path") & 8#022)) -eq 0 ]] || die "$kind is group/other writable"
}
docker_default() { env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"; }

usage() {
  cat <<'EOF'
Usage: production-exact-artifact-promote.sh --validate|--execute|--second-start \
  --rc-manifest <absolute-json> --rc-manifest-sha256 <sha256>

The immutable RC manifest supplies every source/image/config identity. This
command never builds, pulls, stops, recreates, or removes the Production DB.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --validate|--execute|--second-start) ACTION="${1#--}" ;;
    --rc-manifest) shift; RC_MANIFEST="${1:-}" ;;
    --rc-manifest-sha256) shift; RC_MANIFEST_SHA256="${1:-}" ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
  shift
done

[[ "$RC_MANIFEST" == /* && -f "$RC_MANIFEST" && ! -L "$RC_MANIFEST" ]] || die "RC manifest must be an absolute regular file"
path_has_symlink "$RC_MANIFEST" && die "RC manifest traverses a symlink"
[[ "$RC_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ && "$(digest "$RC_MANIFEST")" == "$RC_MANIFEST_SHA256" ]] || die "RC manifest digest mismatch"
mapfile -t rc < <(python3 - "$RC_MANIFEST" <<'PY'
import json, re, sys
d=json.load(open(sys.argv[1], encoding='utf-8'))
keys=('status','rc_id','source_sha','source_main_ancestry','production_previous_sha','production_control_checkout_sha','postgres_image_id','backend_image_tag','backend_image_id','frontend_image_tag','frontend_image_id','resolved_compose_sha256','tooling_commit_sha','promotion_helper_sha256','backup_helper_sha256','promotion_override_sha256','flyway_manifest_sha256','staging_acceptance_sha256','flyway_target','parity_result','owner_field_test_result','printing_field_test_result','android_client_compatibility_result','migration_rehearsal_result','rollback_compatibility_result','backup_recovery_result','isolated_restore_result','fresh_backup_sha256')
for k in keys:
    v=d.get(k,'')
    if k == 'production_control_checkout_sha' and not v:
        v=d.get('production_previous_sha','')
    if not isinstance(v,str) or '\n' in v: raise SystemExit(2)
    print(v)
PY
) || die "RC manifest is invalid"
[[ ${#rc[@]} -eq 28 ]] || die "RC manifest fields are incomplete"
RC_STATUS="${rc[0]}"; RC_ID="${rc[1]}"; APPROVED_SHA="${rc[2]}"; MAIN_ANCESTRY="${rc[3]}"; PRODUCTION_PREVIOUS_SHA="${rc[4]}"; EXPECTED_CONTROL_CHECKOUT_SHA="${rc[5]}"
POSTGRES_IMAGE_ID="${rc[6]}"; BACKEND_IMAGE="${rc[7]}"; BACKEND_IMAGE_ID="${rc[8]}"; FRONTEND_IMAGE="${rc[9]}"; FRONTEND_IMAGE_ID="${rc[10]}"
EXPECTED_COMPOSE_DIGEST="${rc[11]}"; TOOLING_SHA="${rc[12]}"; EXPECTED_PROMOTION_HELPER_DIGEST="${rc[13]}"; EXPECTED_BACKUP_HELPER_DIGEST="${rc[14]}"; EXPECTED_OVERRIDE_DIGEST="${rc[15]}"; EXPECTED_FLYWAY_DIGEST="${rc[16]}"; STAGING_ACCEPTANCE_DIGEST="${rc[17]}"; FLYWAY_TARGET="${rc[18]}"
PARITY_RESULT="${rc[19]}"; OWNER_RESULT="${rc[20]}"; PRINTING_RESULT="${rc[21]}"; ANDROID_RESULT="${rc[22]}"; MIGRATION_RESULT="${rc[23]}"; ROLLBACK_RESULT="${rc[24]}"; BACKUP_RESULT="${rc[25]}"; RESTORE_RESULT="${rc[26]}"; FRESH_BACKUP_SHA="${rc[27]}"
[[ "$RC_STATUS" == "RC_FROZEN" || ( "$ACTION" == "validate" && "$RC_STATUS" == "RC_PREPARED" ) ]] || die "RC status does not authorize this action"
[[ "$RC_ID" =~ ^RC-[A-Za-z0-9._-]+$ ]] || die "RC ID is invalid"
[[ "$MAIN_ANCESTRY" == "PASS" && "$FLYWAY_TARGET" == "V10" && "$PARITY_RESULT" == PASS* && "$OWNER_RESULT" == PASS* && "$PRINTING_RESULT" == PASS* ]] || die "RC acceptance fields are not PASS"
if [[ "$ACTION" != "validate" ]]; then
  [[ "$TOOLING_SHA" =~ ^[0-9a-f]{40}$ && "$EXPECTED_PROMOTION_HELPER_DIGEST" =~ ^[0-9a-f]{64}$ && "$EXPECTED_BACKUP_HELPER_DIGEST" =~ ^[0-9a-f]{64}$ && "$EXPECTED_OVERRIDE_DIGEST" =~ ^[0-9a-f]{64}$ ]] || die "RC tooling identity is not frozen"
  [[ "$ANDROID_RESULT" == PASS* && "$MIGRATION_RESULT" == PASS* && ( "$ROLLBACK_RESULT" == "YES" || "$ROLLBACK_RESULT" == "NO|ROLL_FORWARD_ONLY_AFTER_V10" ) && "$BACKUP_RESULT" == PASS* && "$RESTORE_RESULT" == PASS* && "$FRESH_BACKUP_SHA" =~ ^[0-9a-f]{64}$ ]] || die "RC mandatory execution gates are not closed"
fi
[[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ && "$PRODUCTION_PREVIOUS_SHA" =~ ^[0-9a-f]{40}$ && "$EXPECTED_CONTROL_CHECKOUT_SHA" =~ ^[0-9a-f]{40}$ ]] || die "RC Git identity is invalid"
[[ "$BACKEND_IMAGE" == *"$APPROVED_SHA" && "$FRONTEND_IMAGE" == *"$APPROVED_SHA" ]] || die "RC image tags are not SHA-bound"
[[ "$POSTGRES_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ && "$BACKEND_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ && "$FRONTEND_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] || die "RC image ID is invalid"
[[ "$EXPECTED_COMPOSE_DIGEST" =~ ^[0-9a-f]{64}$ && "$EXPECTED_FLYWAY_DIGEST" =~ ^[0-9a-f]{64}$ && "$STAGING_ACCEPTANCE_DIGEST" =~ ^[0-9a-f]{64}$ ]] || die "RC evidence digest is invalid"

for required in "$BASE_COMPOSE" "$OVERRIDE_COMPOSE" "$ENV_FILE" "$FLYWAY_MANIFEST"; do require_real_path "$required" file "$(id -u)"; done
require_real_path "$EXPECTED_CONTROL_ROOT" control-root "$(id -u)"
if [[ ! -e "$OPS_LOCK" ]]; then (umask 077; set -o noclobber; : >"$OPS_LOCK") 2>/dev/null || die "cannot create Production ops lock"; fi
[[ -f "$OPS_LOCK" && ! -L "$OPS_LOCK" && "$(stat -c '%a|%u' "$OPS_LOCK")" == "600|$(id -u)" ]] || die "Production ops lock identity is unsafe"
path_has_symlink "$OPS_LOCK" && die "Production ops lock traverses a symlink"
exec 9<>"$OPS_LOCK"; flock -n 9 || die "another Production operation holds the lock"
require_real_path "$EXPECTED_POSTGRES_DATA_DIR" postgres-root 70
require_real_path "$EXPECTED_NGINX_TEMPLATE" nginx-template "$(id -u)"
require_real_path "$EXPECTED_CERTBOT_WWW_DIR" certbot-root "$(id -u)"
require_real_path "$EXPECTED_LETSENCRYPT_DIR" letsencrypt-root "$(id -u)"
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || die "Production env file must be mode 0600"
[[ "$(digest "$FLYWAY_MANIFEST")" == "$EXPECTED_FLYWAY_DIGEST" ]] || die "Flyway manifest digest differs from RC"
if [[ "$ACTION" != "validate" ]]; then
  [[ "$(git -C "$SCRIPT_DIR/../.." rev-parse HEAD)" == "$TOOLING_SHA" && -z "$(git -C "$SCRIPT_DIR/../.." status --porcelain --untracked-files=no)" ]] || die "tooling checkout identity differs from RC"
  [[ "$(digest "${BASH_SOURCE[0]}")" == "$EXPECTED_PROMOTION_HELPER_DIGEST" && "$(digest "$SCRIPT_DIR/production-backup-rehearsal.sh")" == "$EXPECTED_BACKUP_HELPER_DIGEST" && "$(digest "$OVERRIDE_COMPOSE")" == "$EXPECTED_OVERRIDE_DIGEST" ]] || die "tooling blob digest differs from RC"
fi

actual_sha="$(git -C /home/ubuntu/Restaurant_System rev-parse HEAD)"
[[ "$actual_sha" == "$EXPECTED_CONTROL_CHECKOUT_SHA" ]] || die "Production control checkout identity drifted"
mapfile -t production_services < <(docker_default ps --filter "label=com.docker.compose.project=$EXPECTED_PROJECT" --format '{{.Label "com.docker.compose.service"}}' | sort)
[[ "${production_services[*]}" == "backend db nginx" ]] || die "Production project services differ"
db_id_before="$(docker_default ps -q --filter "label=com.docker.compose.project=$EXPECTED_PROJECT" --filter label=com.docker.compose.service=db)"
[[ -n "$db_id_before" ]] || die "Production DB container is not running"
[[ "$(docker_default inspect --format '{{.Image}}' "$db_id_before")" == "$POSTGRES_IMAGE_ID" ]] || die "Production DB image differs from RC"
[[ "$(docker_default inspect --format '{{.Image}}' "$db_id_before")" == "$POSTGRES_IMAGE_ID" ]] || die "Production DB image differs from RC"
db_mount="$(docker_default inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Type}}|{{.Source}}|{{.RW}}{{end}}{{end}}' "$db_id_before")"
[[ "$db_mount" == "bind|$EXPECTED_POSTGRES_DATA_DIR|true" ]] || die "Production DB mount differs from fixed state root"
[[ "$(docker_default image inspect --format '{{.Id}}' "$BACKEND_IMAGE")" == "$BACKEND_IMAGE_ID" ]] || die "backend tag differs from accepted ID"
[[ "$(docker_default image inspect --format '{{.Id}}' "$FRONTEND_IMAGE")" == "$FRONTEND_IMAGE_ID" ]] || die "frontend tag differs from accepted ID"

resource_gate() {
  local available; available="$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)"
  [[ "$available" =~ ^[0-9]+$ ]] || die "cannot read available memory"
  (( available >= MIN_AVAILABLE_MEMORY_KB )) || die "available memory is below 1 GiB"
  [[ "$(df -Pk "$EXPECTED_CONTROL_ROOT" | awk 'NR==2 {print $4}')" -ge 5242880 ]] || die "Production disk has less than 5 GiB free"
  printf 'RESOURCE|mem_available_kb=%s|result=PASS\n' "$available"
}

compose_env=(PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" PRODUCTION_POSTGRES_DATA_DIR="$EXPECTED_POSTGRES_DATA_DIR" PRODUCTION_NGINX_TEMPLATE="$EXPECTED_NGINX_TEMPLATE" PRODUCTION_CERTBOT_WWW_DIR="$EXPECTED_CERTBOT_WWW_DIR" PRODUCTION_LETSENCRYPT_DIR="$EXPECTED_LETSENCRYPT_DIR" PROMOTION_BACKEND_IMAGE="$BACKEND_IMAGE_ID" PROMOTION_FRONTEND_IMAGE="$FRONTEND_IMAGE_ID")
compose=(docker --context default compose --project-name "$EXPECTED_PROJECT" --env-file "$ENV_FILE" -f "$BASE_COMPOSE" -f "$OVERRIDE_COMPOSE")
resolved="$(env -i "${compose_env[@]}" "${compose[@]}" config)"
[[ "$(printf '%s' "$resolved" | sha256sum | awk '{print $1}')" == "$EXPECTED_COMPOSE_DIGEST" ]] || die "resolved Compose digest differs from RC"
resolved_json="$(env -i "${compose_env[@]}" "${compose[@]}" config --format json)"
python3 -c 'import json,sys; d=json.load(sys.stdin); a=sys.argv[1:]; assert set(d["services"])=={"db","backend","nginx"}; assert d["services"]["backend"]["image"]==a[4]; assert d["services"]["nginx"]["image"]==a[5]; mounts=lambda s:{(v["source"],v["target"],bool(v.get("read_only",False))) for v in d["services"][s].get("volumes",[])}; assert mounts("db")=={(a[0],"/var/lib/postgresql/data",False)}; assert mounts("nginx")=={(a[1],"/etc/nginx/templates/default.conf.template",True),(a[2],"/var/www/certbot",True),(a[3],"/etc/letsencrypt",True)}; assert {(str(p.get("published")),int(p["target"])) for p in d["services"]["nginx"].get("ports",[])}=={("80",80),("443",443)}' "$EXPECTED_POSTGRES_DATA_DIR" "$EXPECTED_NGINX_TEMPLATE" "$EXPECTED_CERTBOT_WWW_DIR" "$EXPECTED_LETSENCRYPT_DIR" "$BACKEND_IMAGE_ID" "$FRONTEND_IMAGE_ID" <<<"$resolved_json" || die "resolved Compose topology differs"

expected_flyway="$(awk -F'|' '/^[0-9]+\|/ {print $1 "|" $2 "|true|" $3}' "$FLYWAY_MANIFEST")"
flyway_rows() { docker_default exec "$db_id_before" sh -eu -c 'psql -X -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version || chr(124) || script || chr(124) || success::text || chr(124) || checksum from flyway_schema_history order by installed_rank"'; }
wait_health() {
  local body=""
  local deadline=$((SECONDS + 90))
  while (( SECONDS < deadline )); do body="$(env -i PATH="$SAFE_PATH" curl --noproxy '*' --connect-timeout 2 --max-time 5 -fsS http://127.0.0.1/api/v1/system/health || true)"; [[ "$body" == *'"status":"UP"'* ]] && return 0; sleep 1; done
  die "candidate backend did not become healthy"
}
verify_public_routes() {
  local frontend_code="" health_body=""
  local deadline=$((SECONDS + 30))
  while (( SECONDS < deadline )); do
    frontend_code="$(env -i PATH="$SAFE_PATH" curl --noproxy '*' --connect-timeout 2 --max-time 5 -sS -o /dev/null -w '%{http_code}' http://127.0.0.1/ || true)"
    health_body="$(env -i PATH="$SAFE_PATH" curl --noproxy '*' --connect-timeout 2 --max-time 5 -fsS http://127.0.0.1/api/v1/system/health || true)"
    [[ "$frontend_code" == "200" && "$health_body" == *'"status":"UP"'* ]] && return 0
    sleep 1
  done
  die "post-nginx frontend/API readiness failed"
}

resource_gate
printf 'VALIDATION|rc_id=%s|project=cloud|db_id=%s|result=PASS\n' "$RC_ID" "${db_id_before:0:12}"
[[ "$ACTION" != "validate" ]] || exit 0

if [[ "$ACTION" == "execute" ]]; then
  env -i "${compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never backend
  [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$BACKEND_IMAGE_ID" ]] || die "backend image after start differs"
  wait_health
  [[ "$(flyway_rows)" == "$expected_flyway" ]] || die "Production Flyway ledger is not exact V10"
  resource_gate
  env -i "${compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never nginx
  [[ "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$FRONTEND_IMAGE_ID" ]] || die "frontend image after start differs"
  verify_public_routes
else
  [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$BACKEND_IMAGE_ID" ]] || die "second start requested before successful promotion"
  second_start_epoch="$(date +%s)"
  env -i "${compose_env[@]}" "${compose[@]}" restart backend
  wait_health
  [[ "$(flyway_rows)" == "$expected_flyway" ]] || die "Flyway ledger changed on second start"
  second_start_logs="$(docker_default logs --since "$second_start_epoch" cloud-backend-1 2>&1)"
  grep -Fq 'is up to date. No migration necessary' <<<"$second_start_logs" || die "second start did not prove no pending migration"
fi

db_id_after="$(docker_default ps -q --filter "label=com.docker.compose.project=$EXPECTED_PROJECT" --filter label=com.docker.compose.service=db)"
[[ "$db_id_after" == "$db_id_before" ]] || die "Production DB container identity changed"
printf 'PROMOTION|action=%s|rc_id=%s|db_unchanged=true|result=PASS\n' "$ACTION" "$RC_ID"
