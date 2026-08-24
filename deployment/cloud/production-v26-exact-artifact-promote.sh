#!/usr/bin/env bash
set -Eeuo pipefail

readonly SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
readonly EXPECTED_PROJECT="cloud"
readonly EXPECTED_CONTROL_ROOT="/home/ubuntu/Restaurant_System/deployment/cloud"
readonly EXPECTED_POSTGRES_DATA_DIR="$EXPECTED_CONTROL_ROOT/data/postgres"
readonly EXPECTED_NGINX_TEMPLATE="$EXPECTED_CONTROL_ROOT/data/nginx/default.conf.template"
readonly EXPECTED_CERTBOT_WWW_DIR="$EXPECTED_CONTROL_ROOT/data/certbot-www"
readonly EXPECTED_LETSENCRYPT_DIR="$EXPECTED_CONTROL_ROOT/data/letsencrypt"
readonly EXPECTED_STAGING_ENV="/srv/restaurant-pos/staging/config/.env.staging"
readonly MIN_AVAILABLE_MEMORY_KB=1048576
readonly OPS_LOCK="$EXPECTED_CONTROL_ROOT/.production-ops.lock"
readonly SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BASE_COMPOSE="$EXPECTED_CONTROL_ROOT/docker-compose.yml"
readonly OVERRIDE_COMPOSE="$SCRIPT_DIR/docker-compose.production-v26-promotion.yml"
readonly RECOVERY_OVERRIDE="$SCRIPT_DIR/docker-compose.production-v26-recovery.yml"
readonly ENV_FILE="$EXPECTED_CONTROL_ROOT/.env"
readonly FLYWAY_MANIFEST="$SCRIPT_DIR/ops001-flyway-v26-checksums.txt"
readonly SMOKE_HELPER="$SCRIPT_DIR/production-v26-smoke.py"
readonly DATA_CONTRACT="$SCRIPT_DIR/production-v26-data-contract.sh"
readonly EVIDENCE_HELPER="$SCRIPT_DIR/production-v26-evidence.py"
readonly RECOVERY_HELPER="$SCRIPT_DIR/production-v26-recover.sh"
readonly DOCKER_TIMEOUT_SECONDS=120
readonly COMPOSE_TIMEOUT_SECONDS=240
readonly EXPECTED_APP_NETWORK="cloud_restaurant-pos"

ACTION="validate"
RC_MANIFEST=""
RC_MANIFEST_SHA256=""
MUTATION_STARTED="false"
COMPLETED="false"
PREMUTATION_SERVICES_STOPPED="false"
ROLLBACK_WINDOW_CLOSED="false"
PROBE_RUN_ID="$(openssl rand -hex 16)"
PROBE_CONTAINER_NAME=""
PROBE_CONTAINER_ID=""
PROBE_PORT=""

die() { printf 'NO_GO|%s\n' "$*" >&2; exit 1; }
digest() { sha256sum "$1" | awk '{print $1}'; }
bounded() { local seconds="$1"; shift; timeout --foreground --kill-after=10s "${seconds}s" "$@"; }
docker_default() {
  bounded "$DOCKER_TIMEOUT_SECONDS" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"
}
exact_container_id() {
  local output
  output="$(docker_default ps -aq --no-trunc --filter "name=^/${1}$")" || return 1
  [[ -z "$output" || "$output" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf '%s' "$output"
}
path_has_symlink() {
  local path="$1" part current="" old_ifs="$IFS"
  IFS='/'; set -- $path; IFS="$old_ifs"
  for part in "$@"; do [[ -n "$part" ]] || continue; current="$current/$part"; [[ ! -L "$current" ]] || return 0; done
  return 1
}
require_real_path() {
  local path="$1" kind="$2" expected_uid="$3"
  [[ "$path" == /* && -e "$path" && ! -L "$path" ]] || die "$kind path is missing, relative, or symlinked"
  path_has_symlink "$path" && die "$kind path traverses a symlink"
  [[ "$(realpath "$path")" == "$path" && "$(stat -c '%u' "$path")" == "$expected_uid" ]] || die "$kind identity differs"
  [[ $((8#$(stat -c '%a' "$path") & 8#022)) -eq 0 ]] || die "$kind is group/other writable"
}

control_checkout_is_release_safe() {
  local expected_sha="$1" line control_status
  [[ "$(git -C /home/ubuntu/Restaurant_System rev-parse HEAD)" == "$expected_sha" ]] || return 1
  control_status="$(git -C /home/ubuntu/Restaurant_System status --porcelain=v1 --untracked-files=normal)" || return 1
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    case "$line" in
      '?? deployment/cloud/.production-ops.lock'|\
      '?? deployment/cloud/backups/'|\
      '?? deployment/cloud/bootstrap-admin.env'|\
      '?? deployment/cloud/data/'|\
      '?? deployment/cloud/old-store-config.dump'|\
      '?? deployment/cloud/old-store-config.sql') ;;
      *) return 1 ;;
    esac
  done <<<"$control_status"
  return 0
}

usage() {
  printf '%s\n' "Usage: $0 --snapshot|--validate|--execute|--finalize-edge --rc-manifest <absolute-json> --rc-manifest-sha256 <sha256>"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --snapshot|--validate|--execute|--finalize-edge) ACTION="${1#--}" ;;
    --rc-manifest) shift; RC_MANIFEST="${1:-}" ;;
    --rc-manifest-sha256) shift; RC_MANIFEST_SHA256="${1:-}" ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
  shift
done

command -v timeout >/dev/null 2>&1 || die "GNU timeout is required"
[[ "$RC_MANIFEST" == /* && -f "$RC_MANIFEST" && ! -L "$RC_MANIFEST" ]] || die "RC manifest must be an absolute regular file"
path_has_symlink "$RC_MANIFEST" && die "RC manifest traverses a symlink"
[[ "$(realpath "$RC_MANIFEST")" == "$RC_MANIFEST" && "$(stat -c '%a|%u' "$RC_MANIFEST")" == "600|$(id -u)" ]] || die "RC manifest owner/mode/path differs"
[[ "$RC_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ && "$(digest "$RC_MANIFEST")" == "$RC_MANIFEST_SHA256" ]] || die "RC manifest digest mismatch"

rc_output="$(env -i PATH="$SAFE_PATH" python3 -I - "$RC_MANIFEST" <<'PY'
import json,sys
def reject_duplicates(pairs):
    result={}
    for key,value in pairs:
        if key in result: raise ValueError("duplicate key")
        result[key]=value
    return result
keys=(
 'status','rc_id','source_sha','source_main_ancestry','production_previous_sha',
 'production_control_checkout_sha','previous_production_rc_file','previous_production_rc_sha256',
 'postgres_image_id','backend_image_tag','backend_image_id','frontend_image_tag','frontend_image_id',
 'rollback_backend_image_id','rollback_frontend_image_id','resolved_compose_sha256','tooling_commit_sha',
 'promotion_helper_sha256','promotion_override_sha256','recovery_helper_sha256','recovery_override_sha256',
 'rehearsal_helper_sha256','smoke_helper_sha256','data_contract_sha256','evidence_contract_sha256','flyway_manifest_sha256',
 'backup_helper_sha256','staging_acceptance_file','staging_acceptance_sha256',
 'staging_repair_evidence_file','staging_repair_evidence_sha256','fresh_backup_file','fresh_backup_sha256',
 'rehearsal_evidence_file','rehearsal_evidence_sha256','production_business_fingerprint',
 'production_printing_fingerprint','backup_flyway_target','flyway_target','production_backup_result',
 'production_backup_restore_result','migration_rehearsal_result','target_app_boot_result',
 'production_data_integrity_result','read_smoke_result','write_smoke_result',
 'android_pad_compatibility_result','store_organization_isolation_result','recovery_proof_result',
 'staging_accepted_artifact_result','agent_6_release_review','production_preflight_result'
)
d=json.load(open(sys.argv[1],encoding='utf-8'),object_pairs_hook=reject_duplicates)
if set(d) != set(keys): raise SystemExit(2)
for key in keys:
    value=d[key]
    if not isinstance(value,str) or '\n' in value: raise SystemExit(2)
    print(value)
PY
)" || die "RC manifest is invalid"
mapfile -t rc <<<"$rc_output"
unset rc_output
[[ ${#rc[@]} -eq 52 ]] || die "RC manifest fields are incomplete or contains unknown fields"

RC_STATUS="${rc[0]}"; RC_ID="${rc[1]}"; APPROVED_SHA="${rc[2]}"; MAIN_ANCESTRY="${rc[3]}"; PREVIOUS_SHA="${rc[4]}"; CONTROL_SHA="${rc[5]}"
PREVIOUS_RC_FILE="${rc[6]}"; PREVIOUS_RC_DIGEST="${rc[7]}"; POSTGRES_IMAGE_ID="${rc[8]}"; BACKEND_TAG="${rc[9]}"; BACKEND_ID="${rc[10]}"
FRONTEND_TAG="${rc[11]}"; FRONTEND_ID="${rc[12]}"; ROLLBACK_BACKEND_ID="${rc[13]}"; ROLLBACK_FRONTEND_ID="${rc[14]}"
COMPOSE_DIGEST="${rc[15]}"; TOOLING_SHA="${rc[16]}"; PROMOTION_DIGEST="${rc[17]}"; OVERRIDE_DIGEST="${rc[18]}"
RECOVERY_DIGEST="${rc[19]}"; RECOVERY_OVERRIDE_DIGEST="${rc[20]}"; REHEARSAL_DIGEST="${rc[21]}"; SMOKE_DIGEST="${rc[22]}"
DATA_CONTRACT_DIGEST="${rc[23]}"; EVIDENCE_HELPER_DIGEST="${rc[24]}"; FLYWAY_DIGEST="${rc[25]}"; BACKUP_HELPER_DIGEST="${rc[26]}"
STAGING_EVIDENCE_FILE="${rc[27]}"; STAGING_DIGEST="${rc[28]}"; STAGING_REPAIR_FILE="${rc[29]}"; STAGING_REPAIR_DIGEST="${rc[30]}"
BACKUP_FILE="${rc[31]}"; BACKUP_DIGEST="${rc[32]}"; REHEARSAL_EVIDENCE_FILE="${rc[33]}"; REHEARSAL_EVIDENCE_DIGEST="${rc[34]}"
EXPECTED_BUSINESS="${rc[35]}"; EXPECTED_PRINTING="${rc[36]}"; BACKUP_TARGET="${rc[37]}"; FLYWAY_TARGET="${rc[38]}"

if [[ "$ACTION" == "snapshot" || "$ACTION" == "validate" ]]; then
  [[ "$RC_STATUS" == "RC_PREPARED" || "$RC_STATUS" == "RC_FROZEN" ]] || die "RC status is invalid for validation"
else
  [[ "$RC_STATUS" == "RC_FROZEN" ]] || die "RC is not frozen"
fi
[[ "$RC_ID" =~ ^RC-[A-Za-z0-9._-]+$ ]] || die "RC ID is invalid"
[[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ && "$PREVIOUS_SHA" =~ ^[0-9a-f]{40}$ && "$CONTROL_SHA" =~ ^[0-9a-f]{40}$ && "$TOOLING_SHA" =~ ^[0-9a-f]{40}$ ]] || die "RC Git identity is invalid"
[[ "$MAIN_ANCESTRY" == "PASS" && "$BACKUP_TARGET" == "V10" && "$FLYWAY_TARGET" == "V26" ]] || die "RC release boundary differs"
[[ "$BACKEND_TAG" == *"$APPROVED_SHA" && "$FRONTEND_TAG" == *"$APPROVED_SHA" ]] || die "target image tags are not accepted-SHA-bound"
for image in "$POSTGRES_IMAGE_ID" "$BACKEND_ID" "$FRONTEND_ID" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID"; do [[ "$image" =~ ^sha256:[0-9a-f]{64}$ ]] || die "RC image ID is invalid"; done
for value in "$PREVIOUS_RC_DIGEST" "$PROMOTION_DIGEST" "$OVERRIDE_DIGEST" "$RECOVERY_DIGEST" "$RECOVERY_OVERRIDE_DIGEST" "$REHEARSAL_DIGEST" "$SMOKE_DIGEST" "$DATA_CONTRACT_DIGEST" "$EVIDENCE_HELPER_DIGEST" "$FLYWAY_DIGEST" "$BACKUP_HELPER_DIGEST" "$STAGING_DIGEST" "$STAGING_REPAIR_DIGEST" "$BACKUP_DIGEST" "$REHEARSAL_EVIDENCE_DIGEST"; do [[ "$value" =~ ^[0-9a-f]{64}$ ]] || die "RC digest is invalid"; done
if [[ "$ACTION" == "snapshot" ]]; then
  for value in "$COMPOSE_DIGEST" "$EXPECTED_BUSINESS" "$EXPECTED_PRINTING"; do [[ "$value" == "PENDING" || "$value" =~ ^[0-9a-f]{64}$ ]] || die "RC snapshot field is invalid"; done
else
  for value in "$COMPOSE_DIGEST" "$EXPECTED_BUSINESS" "$EXPECTED_PRINTING"; do [[ "$value" =~ ^[0-9a-f]{64}$ ]] || die "RC fingerprint is invalid"; done
fi
for index in 39 40 41 42 43 44 45 46 47 48; do [[ "${rc[$index]}" == "PASS" ]] || die "RC automated gate type/value differs"; done
[[ "${rc[49]}" == "VERIFIED" ]] || die "Staging artifact gate must be VERIFIED"
[[ "${rc[50]}" == "ACCEPT" ]] || die "Agent 6 release gate must be ACCEPT"
if [[ "$ACTION" == "execute" || "$ACTION" == "finalize-edge" ]]; then
  [[ "${rc[51]}" == "PASS" ]] || die "Production preflight gate must be PASS"
else
  [[ "${rc[51]}" == "PENDING" || "${rc[51]}" == "PASS" ]] || die "Production preflight gate type/value differs"
fi

for required in "$BASE_COMPOSE" "$OVERRIDE_COMPOSE" "$RECOVERY_OVERRIDE" "$ENV_FILE" "$FLYWAY_MANIFEST" "$SMOKE_HELPER" "$DATA_CONTRACT" "$EVIDENCE_HELPER" "$RECOVERY_HELPER" "$EXPECTED_STAGING_ENV"; do require_real_path "$required" file "$(id -u)"; done
require_real_path "$EXPECTED_CONTROL_ROOT" control-root "$(id -u)"
require_real_path "$EXPECTED_POSTGRES_DATA_DIR" postgres-root 70
require_real_path "$EXPECTED_NGINX_TEMPLATE" nginx-template "$(id -u)"
require_real_path "$EXPECTED_CERTBOT_WWW_DIR" certbot-root "$(id -u)"
require_real_path "$EXPECTED_LETSENCRYPT_DIR" letsencrypt-root "$(id -u)"
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || die "Production env file must be mode 0600"
[[ -f "$OPS_LOCK" && ! -L "$OPS_LOCK" && "$(stat -c '%a|%u' "$OPS_LOCK")" == "600|$(id -u)" ]] || die "Production ops lock identity differs"
exec 9<>"$OPS_LOCK"; flock -n 9 || die "another Production operation holds the lock"

[[ "$(git -C "$SCRIPT_DIR/../.." rev-parse HEAD)" == "$TOOLING_SHA" && -z "$(git -C "$SCRIPT_DIR/../.." status --porcelain)" ]] || die "tooling checkout identity differs"
git -C "$SCRIPT_DIR/../.." merge-base --is-ancestor "$APPROVED_SHA" "$TOOLING_SHA" || die "accepted source is not tooling ancestry"
[[ "$(digest "${BASH_SOURCE[0]}")" == "$PROMOTION_DIGEST" && "$(digest "$OVERRIDE_COMPOSE")" == "$OVERRIDE_DIGEST" ]] || die "promotion tooling digest differs"
[[ "$(digest "$RECOVERY_HELPER")" == "$RECOVERY_DIGEST" && "$(digest "$RECOVERY_OVERRIDE")" == "$RECOVERY_OVERRIDE_DIGEST" ]] || die "recovery tooling digest differs"
[[ "$(digest "$SCRIPT_DIR/production-v10-v26-rehearsal.sh")" == "$REHEARSAL_DIGEST" && "$(digest "$SMOKE_HELPER")" == "$SMOKE_DIGEST" && "$(digest "$DATA_CONTRACT")" == "$DATA_CONTRACT_DIGEST" && "$(digest "$EVIDENCE_HELPER")" == "$EVIDENCE_HELPER_DIGEST" ]] || die "rehearsal/data tooling digest differs"
[[ "$(digest "$FLYWAY_MANIFEST")" == "$FLYWAY_DIGEST" && "$(digest "$SCRIPT_DIR/production-backup-rehearsal.sh")" == "$BACKUP_HELPER_DIGEST" ]] || die "Flyway/backup tooling digest differs"

EXPECTED_PREVIOUS_RC="$(realpath "$SCRIPT_DIR/../../docs/archive/governance-pre-simplification/runtime/RC_THREE_RELIABILITY_20260812_3EC4D88.json")"
[[ "$PREVIOUS_RC_FILE" == "$EXPECTED_PREVIOUS_RC" ]] || die "previous Production RC path differs"
require_real_path "$PREVIOUS_RC_FILE" previous-rc "$(id -u)"
[[ "$(digest "$PREVIOUS_RC_FILE")" == "$PREVIOUS_RC_DIGEST" ]] || die "previous Production RC digest differs"
env -i PATH="$SAFE_PATH" python3 -I - "$PREVIOUS_RC_FILE" "$PREVIOUS_SHA" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID" <<'PY' || die "previous Production SHA is not bound to current rollback images"
import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
assert d.get('status')=='RC_FROZEN'
assert d.get('source_sha')==sys.argv[2]
assert d.get('backend_image_id')==sys.argv[3]
assert d.get('frontend_image_id')==sys.argv[4]
PY

EXPECTED_STAGING_EVIDENCE="$(realpath "$SCRIPT_DIR/../../docs/governance/PHASE_B_PART2_PRODUCT_FLOW_STAGING_ACCEPTANCE_EVIDENCE.md")"
EXPECTED_STAGING_REPAIR="$(realpath "$SCRIPT_DIR/../../docs/governance/PHASE_B_PART2_P0_P1_REPAIR_STAGING_EVIDENCE.md")"
[[ "$STAGING_EVIDENCE_FILE" == "$EXPECTED_STAGING_EVIDENCE" && "$STAGING_REPAIR_FILE" == "$EXPECTED_STAGING_REPAIR" ]] || die "Staging evidence path differs"
for evidence in "$STAGING_EVIDENCE_FILE" "$STAGING_REPAIR_FILE"; do require_real_path "$evidence" evidence "$(id -u)"; done
[[ "$(digest "$STAGING_EVIDENCE_FILE")" == "$STAGING_DIGEST" && "$(digest "$STAGING_REPAIR_FILE")" == "$STAGING_REPAIR_DIGEST" ]] || die "Staging evidence digest differs from RC"

[[ "$BACKUP_FILE" == "$EXPECTED_CONTROL_ROOT/backups/"* && "$(dirname "$BACKUP_FILE")" == "$EXPECTED_CONTROL_ROOT/backups" ]] || die "fresh backup path differs"
require_real_path "$BACKUP_FILE" backup "$(id -u)"
[[ "$(stat -c '%a' "$BACKUP_FILE")" == "600" && "$(digest "$BACKUP_FILE")" == "$BACKUP_DIGEST" ]] || die "fresh backup identity differs"
[[ "$REHEARSAL_EVIDENCE_FILE" == /srv/restaurant-pos/production-tools/evidence/* && "$(dirname "$REHEARSAL_EVIDENCE_FILE")" == "/srv/restaurant-pos/production-tools/evidence" ]] || die "rehearsal evidence path differs"
require_real_path "$REHEARSAL_EVIDENCE_FILE" rehearsal-evidence "$(id -u)"
[[ "$(stat -c '%a' "$REHEARSAL_EVIDENCE_FILE")" == "600" && "$(digest "$REHEARSAL_EVIDENCE_FILE")" == "$REHEARSAL_EVIDENCE_DIGEST" ]] || die "rehearsal evidence identity differs"
bounded 60 env -i PATH="$SAFE_PATH" python3 -I "$EVIDENCE_HELPER" --scope full \
  --staging-full "$STAGING_EVIDENCE_FILE" --staging-repair "$STAGING_REPAIR_FILE" \
  --rehearsal "$REHEARSAL_EVIDENCE_FILE" --source-sha "$APPROVED_SHA" \
  --backend-image-id "$BACKEND_ID" --frontend-image-id "$FRONTEND_ID" \
  --backup-sha256 "$BACKUP_DIGEST" --recovery-helper-sha256 "$RECOVERY_DIGEST" \
  >/dev/null || die "release evidence contract differs"

control_checkout_is_release_safe "$CONTROL_SHA" || die "Production control checkout identity drifted"
production_services_output="$(docker_default ps --filter "label=com.docker.compose.project=$EXPECTED_PROJECT" --format '{{.Label "com.docker.compose.service"}}' | sort)" || die "cannot enumerate running Production services"
production_resources_output="$(docker_default ps -a --filter "label=com.docker.compose.project=$EXPECTED_PROJECT" --format '{{.Label "com.docker.compose.service"}}' | sort)" || die "cannot enumerate Production resources"
mapfile -t production_services <<<"$production_services_output"
mapfile -t production_resources <<<"$production_resources_output"
if [[ "$ACTION" == "finalize-edge" ]]; then
  [[ "${production_services[*]}" == "backend db" || "${production_services[*]}" == "backend db nginx" ]] || die "Production project services differ"
  [[ "${production_resources[*]}" == "backend db" || "${production_resources[*]}" == "backend db nginx" ]] || die "Production project resources differ"
  existing_nginx_id="$(exact_container_id cloud-nginx-1)" || die "cannot query existing frontend resource"
  if [[ -n "$existing_nginx_id" ]]; then
    existing_nginx_identity="$(docker_default inspect --format '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.Image}}' "$existing_nginx_id")"
    [[ "$existing_nginx_identity" == "$EXPECTED_PROJECT|nginx|$FRONTEND_ID" || "$existing_nginx_identity" == "$EXPECTED_PROJECT|nginx|$ROLLBACK_FRONTEND_ID" ]] || die "existing frontend resource differs before edge finalization"
  fi
else
  [[ "${production_services[*]}" == "backend db nginx" ]] || die "Production project services differ"
  [[ "${production_resources[*]}" == "backend db nginx" ]] || die "Production project resources differ"
fi
DB_ID_BEFORE="$(docker_default ps -q --filter "label=com.docker.compose.project=$EXPECTED_PROJECT" --filter label=com.docker.compose.service=db)"
[[ -n "$DB_ID_BEFORE" && "$(docker_default inspect --format '{{.Image}}|{{.State.Health.Status}}' "$DB_ID_BEFORE")" == "$POSTGRES_IMAGE_ID|healthy" ]] || die "Production DB identity/health differs"
[[ "$(docker_default inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Type}}|{{.Source}}|{{.RW}}{{end}}{{end}}' "$DB_ID_BEFORE")" == "bind|$EXPECTED_POSTGRES_DATA_DIR|true" ]] || die "Production DB fixed mount differs"
[[ "$(docker_default image inspect --format '{{.Id}}' "$BACKEND_TAG")" == "$BACKEND_ID" && "$(docker_default image inspect --format '{{.Id}}' "$FRONTEND_TAG")" == "$FRONTEND_ID" ]] || die "accepted image tags differ from immutable IDs"
[[ "$(docker_default inspect --format '{{.Image}}' restaurant-pos-staging-backend-1)" == "$BACKEND_ID" && "$(docker_default inspect --format '{{.Image}}' restaurant-pos-staging-nginx-1)" == "$FRONTEND_ID" ]] || die "current Staging runtime no longer uses accepted images"
grep -Fxq "STAGING_COMMIT_SHA=$APPROVED_SHA" "$EXPECTED_STAGING_ENV" || die "current Staging SHA differs from RC"

expected_ledger() { local target="$1"; awk -F'|' -v target="$target" '/^[0-9]+[|]/ && ($1 + 0) <= (target + 0) {print $1 "|" $2 "|true|" $3}' "$FLYWAY_MANIFEST"; }
flyway_rows() { docker_default exec "$DB_ID_BEFORE" sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version || chr(124) || script || chr(124) || success::text || chr(124) || checksum from flyway_schema_history order by installed_rank"'; }
v26_db_query() { docker_default exec -i "$DB_ID_BEFORE" sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"'; }
# shellcheck disable=SC1090
source "$DATA_CONTRACT"

resource_gate() {
  local available used
  available="$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)"
  [[ "$available" =~ ^[0-9]+$ && "$available" -ge "$MIN_AVAILABLE_MEMORY_KB" ]] || die "available memory is below 1 GiB"
  [[ "$(df -Pk "$EXPECTED_CONTROL_ROOT" | awk 'NR==2 {print $4}')" -ge 5242880 ]] || die "Production disk has less than 5 GiB free"
  used="$(df -Pk "$EXPECTED_CONTROL_ROOT" | awk 'NR==2 {gsub(/%/,"",$5);print $5}')"
  printf 'RESOURCE|mem_available_kb=%s|disk_used_percent=%s|result=PASS\n' "$available" "$used"
}
backend_private_ip() {
  local identity ip
  identity="$(docker_default inspect --format '{{len .NetworkSettings.Networks}}|{{range $name, $config := .NetworkSettings.Networks}}{{$name}}|{{$config.IPAddress}}{{end}}' cloud-backend-1)"
  [[ "$identity" == "1|$EXPECTED_APP_NETWORK|"* ]] || return 1
  ip="${identity##*|}"
  [[ "$ip" =~ ^10\.|^172\.(1[6-9]|2[0-9]|3[01])\.|^192\.168\. ]] || return 1
  printf '%s' "$ip"
}
wait_backend_health() {
  local deadline=$((SECONDS + 120)) body="" ip
  while (( SECONDS < deadline )); do
    ip="$(backend_private_ip 2>/dev/null || true)"
    if [[ -n "$ip" ]]; then
      body="$(curl --noproxy '*' --connect-timeout 2 --max-time 5 -fsS "http://$ip:8080/api/v1/system/health" 2>/dev/null || true)"
    fi
    [[ "$body" == *'"status":"UP"'* ]] && return 0
    sleep 1
  done
  return 1
}
verify_public() {
  [[ "$(curl --noproxy '*' --connect-timeout 2 --max-time 10 -sS -o /dev/null -w '%{http_code}' http://127.0.0.1/)" == "200" ]] &&
    [[ "$(curl --noproxy '*' --connect-timeout 2 --max-time 10 -sS -o /dev/null -w '%{http_code}' http://127.0.0.1/ws/info)" == "200" ]]
}
run_read_smoke() {
  local mode="$1" base_url="${2:-http://127.0.0.1}"
  bounded 180 env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" JWT_SECRET="$JWT_SECRET" python3 -I "$SMOKE_HELPER" --base-url "$base_url" --db-container cloud-db-1 --mode "$mode"
}

remove_private_probe() {
  local actual="" actual_id tail image remaining leftovers
  [[ -n "$PROBE_CONTAINER_NAME" ]] || return 0
  actual_id="$(exact_container_id "$PROBE_CONTAINER_NAME")" || return 1
  if [[ -n "$actual_id" ]]; then
    actual="$(docker_default inspect --format '{{.Id}}|{{.Image}}|{{index .Config.Labels "restaurant.production-v26-private-probe"}}' "$actual_id")" || return 1
    tail="${actual#*|}"; image="${tail%%|*}"
    [[ "${actual##*|}" == "$PROBE_RUN_ID" && "$image" == "$FRONTEND_ID" ]] || return 1
    [[ -z "$PROBE_CONTAINER_ID" || "${actual%%|*}" == "$PROBE_CONTAINER_ID" ]] || return 1
    docker_default rm -f "${actual%%|*}" >/dev/null || return 1
  fi
  remaining="$(exact_container_id "$PROBE_CONTAINER_NAME")" || return 1
  [[ -z "$remaining" ]] || return 1
  leftovers="$(docker_default ps -aq --no-trunc --filter label=restaurant.production-v26-private-probe="$PROBE_RUN_ID")" || return 1
  [[ -z "$leftovers" ]] || return 1
  PROBE_CONTAINER_NAME=""; PROBE_CONTAINER_ID=""; PROBE_PORT=""
}

run_private_target_smoke() {
  local created identity deadline code existing
  [[ "$(docker_default network inspect --format '{{.Name}}|{{.Internal}}' "$EXPECTED_APP_NETWORK")" == "$EXPECTED_APP_NETWORK|false" ]] || die "Production application network identity differs"
  PROBE_CONTAINER_NAME="production-v26-private-probe-${PROBE_RUN_ID:0:16}"
  existing="$(exact_container_id "$PROBE_CONTAINER_NAME")" || die "cannot query private probe container name"
  [[ -z "$existing" ]] || die "private probe container name already exists"
  created="$(docker_default run -d --pull=never --name "$PROBE_CONTAINER_NAME" \
    --label restaurant.production-v26-private-probe="$PROBE_RUN_ID" --network "$EXPECTED_APP_NETWORK" \
    -p 127.0.0.1::80 --cpus 0.5 --memory 256m --pids-limit 256 \
    -e DOMAIN="${DOMAIN:-}" -e NGINX_SERVER_NAME="${NGINX_SERVER_NAME:-_}" -e TZ="${TZ:-America/Toronto}" \
    -v "$EXPECTED_NGINX_TEMPLATE:/etc/nginx/templates/default.conf.template:ro" \
    -v "$EXPECTED_CERTBOT_WWW_DIR:/var/www/certbot:ro" -v "$EXPECTED_LETSENCRYPT_DIR:/etc/letsencrypt:ro" \
    "$FRONTEND_ID")" || die "private target frontend could not start"
  [[ "$created" =~ ^[0-9a-f]{64}$ ]] || die "private target frontend ID is invalid"
  PROBE_CONTAINER_ID="$created"
  identity="$(docker_default inspect --format '{{.Id}}|{{.Image}}|{{index .Config.Labels "restaurant.production-v26-private-probe"}}|{{len .NetworkSettings.Networks}}' "$PROBE_CONTAINER_NAME")"
  [[ "$identity" == "$PROBE_CONTAINER_ID|$FRONTEND_ID|$PROBE_RUN_ID|1" ]] || die "private target frontend ownership differs"
  PROBE_PORT="$(docker_default port "$PROBE_CONTAINER_ID" 80/tcp | awk -F: 'NR==1 {print $NF}')"
  [[ "$PROBE_PORT" =~ ^[0-9]+$ && "$PROBE_PORT" != "80" && "$PROBE_PORT" != "443" ]] || die "private target frontend loopback port differs"
  deadline=$((SECONDS + 60))
  while (( SECONDS < deadline )); do
    code="$(curl --noproxy '*' --connect-timeout 2 --max-time 5 -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PROBE_PORT/" 2>/dev/null || true)"
    [[ "$code" == "200" ]] && break
    sleep 1
  done
  [[ "$code" == "200" ]] || die "private target frontend did not become ready"
  run_read_smoke read "http://127.0.0.1:$PROBE_PORT"
  [[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "Production data/config changed during private target smoke"
  remove_private_probe || die "private target frontend cleanup failed"
  printf 'PRIVATE_TARGET_SMOKE|edge_public=false|backend_image=%s|frontend_image=%s|result=PASS\n' "$BACKEND_ID" "$FRONTEND_ID"
}

compose_env=(PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" PRODUCTION_POSTGRES_DATA_DIR="$EXPECTED_POSTGRES_DATA_DIR" PRODUCTION_NGINX_TEMPLATE="$EXPECTED_NGINX_TEMPLATE" PRODUCTION_CERTBOT_WWW_DIR="$EXPECTED_CERTBOT_WWW_DIR" PRODUCTION_LETSENCRYPT_DIR="$EXPECTED_LETSENCRYPT_DIR" PROMOTION_BACKEND_IMAGE="$BACKEND_ID" PROMOTION_FRONTEND_IMAGE="$FRONTEND_ID")
compose=(docker --context default compose --project-name "$EXPECTED_PROJECT" --env-file "$ENV_FILE" -f "$BASE_COMPOSE" -f "$OVERRIDE_COMPOSE")
RESOLVED="$(bounded "$COMPOSE_TIMEOUT_SECONDS" env -i "${compose_env[@]}" "${compose[@]}" config)"
ACTUAL_COMPOSE_DIGEST="$(printf '%s' "$RESOLVED" | sha256sum | awk '{print $1}')"
[[ "$ACTION" == "snapshot" || "$ACTUAL_COMPOSE_DIGEST" == "$COMPOSE_DIGEST" ]] || die "resolved Compose digest differs from RC"
RESOLVED_JSON="$(bounded "$COMPOSE_TIMEOUT_SECONDS" env -i "${compose_env[@]}" "${compose[@]}" config --format json)"
runtime_output="$(env -i PATH="$SAFE_PATH" python3 -I -c '
import json,pathlib,re,sys
env_path,*a=sys.argv[1:]
required=("DB_NAME","DB_USER","JWT_SECRET")
seen={key:0 for key in required}
for raw in pathlib.Path(env_path).read_text(encoding="utf-8").splitlines():
    match=re.match(r"^[ \t]*(?:export[ \t]+)?([A-Za-z_][A-Za-z0-9_]*)[ \t]*=",raw)
    if match and match.group(1) in seen: seen[match.group(1)]+=1
assert all(seen[key]==1 for key in required)
d=json.load(sys.stdin)
assert set(d["services"])=={"db","backend","nginx"}
db=d["services"]["db"]; backend=d["services"]["backend"]; nginx=d["services"]["nginx"]
assert backend["image"]==a[4] and str(backend["environment"]["FLYWAY_TARGET"])=="26"
assert nginx["image"]==a[5]
mounts=lambda s:{(v["source"],v["target"],bool(v.get("read_only",False))) for v in d["services"][s].get("volumes",[])}
assert mounts("db")=={(a[0],"/var/lib/postgresql/data",False)}
assert mounts("nginx")=={(a[1],"/etc/nginx/templates/default.conf.template",True),(a[2],"/var/www/certbot",True),(a[3],"/etc/letsencrypt",True)}
values=(db["environment"]["POSTGRES_DB"],db["environment"]["POSTGRES_USER"],backend["environment"]["JWT_SECRET"],nginx["environment"].get("DOMAIN",""),nginx["environment"].get("NGINX_SERVER_NAME","_"),nginx["environment"].get("TZ","America/Toronto"))
assert backend["environment"]["DB_NAME"]==values[0] and backend["environment"]["DB_USER"]==values[1]
assert all(isinstance(value,str) and "\n" not in value and "\r" not in value for value in values)
print("\n".join(values))
' "$ENV_FILE" "$EXPECTED_POSTGRES_DATA_DIR" "$EXPECTED_NGINX_TEMPLATE" "$EXPECTED_CERTBOT_WWW_DIR" "$EXPECTED_LETSENCRYPT_DIR" "$BACKEND_ID" "$FRONTEND_ID" <<<"$RESOLVED_JSON")" || die "resolved Compose/dotenv runtime contract differs"
mapfile -t runtime_values <<<"$runtime_output"
unset runtime_output
[[ ${#runtime_values[@]} -eq 6 ]] || die "resolved runtime field count differs"
POSTGRES_DB="${runtime_values[0]}"; POSTGRES_USER="${runtime_values[1]}"; JWT_SECRET="${runtime_values[2]}"
DOMAIN="${runtime_values[3]}"; NGINX_SERVER_NAME="${runtime_values[4]}"; TZ="${runtime_values[5]}"
[[ "$POSTGRES_DB" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ && "$POSTGRES_USER" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ && ${#JWT_SECRET} -ge 32 ]] || die "resolved Production DB/JWT contract is unsafe"
container_db_contract="$(docker_default exec "$DB_ID_BEFORE" sh -eu -c 'printf "%s|%s" "$POSTGRES_DB" "$POSTGRES_USER"')" || die "cannot read exact DB container runtime identity"
[[ "$container_db_contract" == "$POSTGRES_DB|$POSTGRES_USER" ]] || die "resolved Compose DB identity differs from running DB container"

resource_gate
ACTUAL_BUSINESS="$(v26_business_fingerprint)"
ACTUAL_PRINTING="$(v26_printing_fingerprint)"
bounded 60 env -i PATH="$SAFE_PATH" python3 -I "$EVIDENCE_HELPER" --scope full \
  --staging-full "$STAGING_EVIDENCE_FILE" --staging-repair "$STAGING_REPAIR_FILE" \
  --rehearsal "$REHEARSAL_EVIDENCE_FILE" --source-sha "$APPROVED_SHA" \
  --backend-image-id "$BACKEND_ID" --frontend-image-id "$FRONTEND_ID" \
  --backup-sha256 "$BACKUP_DIGEST" --recovery-helper-sha256 "$RECOVERY_DIGEST" \
  --business-fingerprint "$ACTUAL_BUSINESS" --printing-fingerprint "$ACTUAL_PRINTING" \
  >/dev/null || die "fresh backup/rehearsal fingerprint differs from current Production"
if [[ "$ACTION" == "snapshot" ]]; then
  [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID" && "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$ROLLBACK_FRONTEND_ID" ]] || die "current Production application images differ"
  [[ "$(flyway_rows)" == "$(expected_ledger 10)" ]] || die "Production Flyway is not exact V10"
  wait_backend_health || die "current backend private Docker health path is unavailable"
  verify_public || die "current Production public health differs"
  run_read_smoke legacy-read
  printf 'RC_SNAPSHOT|compose_sha256=%s|business_fingerprint=%s|printing_fingerprint=%s|previous_sha=%s|result=PASS\n' "$ACTUAL_COMPOSE_DIGEST" "$ACTUAL_BUSINESS" "$ACTUAL_PRINTING" "$PREVIOUS_SHA"
  exit 0
fi
[[ "$ACTUAL_BUSINESS" == "$EXPECTED_BUSINESS" && "$ACTUAL_PRINTING" == "$EXPECTED_PRINTING" ]] || die "Production data/config fingerprint changed after RC freeze"

if [[ "$ACTION" == "validate" ]]; then
  [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID" && "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$ROLLBACK_FRONTEND_ID" ]] || die "current Production application images differ"
  [[ "$(flyway_rows)" == "$(expected_ledger 10)" ]] || die "Production Flyway is not exact V10"
  wait_backend_health || die "current backend private Docker health path is unavailable"
  verify_public || die "current Production public health differs"
  run_read_smoke legacy-read
  printf 'PRODUCTION_PREFLIGHT|rc_id=%s|flyway=V10-exact|previous_sha=%s|same_artifact=PASS|result=PASS\n' "$RC_ID" "$PREVIOUS_SHA"
  exit 0
fi

automatic_recovery() {
  local original_status=$?
  trap - EXIT
  if ! remove_private_probe; then
    printf 'PRIVATE_TARGET_SMOKE_CLEANUP|rc_id=%s|result=FAIL|STOP=UNRECOVERABLE_OWNER_GATE\n' "$RC_ID" >&2
  fi
  if [[ "$ROLLBACK_WINDOW_CLOSED" == "true" && "$COMPLETED" != "true" ]]; then
    printf 'AUTO_RECOVERY|rc_id=%s|result=SKIPPED|reason=public_edge_boundary_closed|STOP=OWNER_GATE\n' "$RC_ID" >&2
  elif [[ "$MUTATION_STARTED" == "true" && "$COMPLETED" != "true" ]]; then
    printf 'AUTO_RECOVERY|rc_id=%s|trigger=promotion_failure|status=STARTED\n' "$RC_ID" >&2
    if ! flock -u 9; then
      printf 'AUTO_RECOVERY|rc_id=%s|result=FAIL|reason=lock_release_failed|STOP=UNRECOVERABLE_OWNER_GATE\n' "$RC_ID" >&2
    elif bounded 1800 "$RECOVERY_HELPER" --execute --rc-manifest "$RC_MANIFEST" --rc-manifest-sha256 "$RC_MANIFEST_SHA256"; then
      printf 'AUTO_RECOVERY|rc_id=%s|result=PASS\n' "$RC_ID" >&2
    else
      printf 'AUTO_RECOVERY|rc_id=%s|result=FAIL|STOP=UNRECOVERABLE_OWNER_GATE\n' "$RC_ID" >&2
    fi
  elif [[ "$PREMUTATION_SERVICES_STOPPED" == "true" && "$COMPLETED" != "true" ]]; then
    printf 'PREMUTATION_SERVICE_RESTORE|rc_id=%s|status=STARTED\n' "$RC_ID" >&2
    if docker_default start cloud-backend-1 >/dev/null && wait_backend_health &&
      docker_default start cloud-nginx-1 >/dev/null &&
      [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID" ]] &&
      [[ "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$ROLLBACK_FRONTEND_ID" ]] && verify_public; then
      printf 'PREMUTATION_SERVICE_RESTORE|rc_id=%s|result=PASS\n' "$RC_ID" >&2
    else
      printf 'PREMUTATION_SERVICE_RESTORE|rc_id=%s|result=FAIL|STOP=UNRECOVERABLE_OWNER_GATE\n' "$RC_ID" >&2
    fi
  fi
  exit "$original_status"
}
trap automatic_recovery EXIT

if [[ "$ACTION" == "execute" ]]; then
  [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID" && "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$ROLLBACK_FRONTEND_ID" && "$(flyway_rows)" == "$(expected_ledger 10)" ]] || die "execute requested from unexpected Production state"
  PREMUTATION_SERVICES_STOPPED="true"
  docker_default stop cloud-nginx-1 >/dev/null
  docker_default stop cloud-backend-1 >/dev/null
  [[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "Production data/config changed while entering maintenance"
  MUTATION_STARTED="true"
  bounded "$COMPOSE_TIMEOUT_SECONDS" env -i "${compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never backend
  [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$BACKEND_ID" ]] || die "backend image after promotion differs"
  wait_backend_health || die "V26 backend did not become healthy on its private Docker network"
  [[ "$(flyway_rows)" == "$(expected_ledger 26)" ]] || die "Production Flyway is not exact V26"
  [[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "Production content changed during migration"
  IFS='|' read -r _ _ _ _ _ _ _ _ _ _ additive_violations <<<"$(v26_additive_contract)"
  [[ "$additive_violations" == "0" ]] || die "Production additive V11-V26 relationship contract differs"

  second_start_epoch="$(date +%s)"
  bounded "$COMPOSE_TIMEOUT_SECONDS" env -i "${compose_env[@]}" "${compose[@]}" restart backend
  wait_backend_health || die "V26 backend failed same-image restart"
  [[ "$(flyway_rows)" == "$(expected_ledger 26)" ]] || die "Flyway ledger changed on same-image restart"
  second_logs="$(docker_default logs --since "$second_start_epoch" cloud-backend-1 2>&1)"
  grep -Fq 'is up to date. No migration necessary' <<<"$second_logs" || die "same-image restart did not prove no pending migration"
  [[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "Production content changed on same-image restart"
else
  [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$BACKEND_ID" && "$(flyway_rows)" == "$(expected_ledger 26)" ]] || die "finalize-edge requested from unexpected Production state"
  wait_backend_health || die "V26 backend private health failed before edge finalization"
fi

run_private_target_smoke
ROLLBACK_WINDOW_CLOSED="true"
MUTATION_STARTED="false"
PREMUTATION_SERVICES_STOPPED="false"
printf 'ROLLBACK_AUTHORITY|database_restore=closed|before_public_edge=true|result=PASS\n'

bounded "$COMPOSE_TIMEOUT_SECONDS" env -i "${compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never nginx
[[ "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$FRONTEND_ID" ]] || die "frontend image after promotion differs"
verify_public || die "post-promotion frontend/API/WebSocket readiness failed"
run_read_smoke read

DB_ID_AFTER="$(docker_default ps -q --filter "label=com.docker.compose.project=$EXPECTED_PROJECT" --filter label=com.docker.compose.service=db)"
[[ "$DB_ID_AFTER" == "$DB_ID_BEFORE" ]] || die "Production DB container identity changed"
[[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "Production data/config fingerprint changed after promotion"
resource_gate
COMPLETED="true"
printf 'PROMOTION|action=%s|rc_id=%s|source_sha=%s|flyway=V26-exact|db_container_unchanged=true|result=PASS\n' "$ACTION" "$RC_ID" "$APPROVED_SHA"
