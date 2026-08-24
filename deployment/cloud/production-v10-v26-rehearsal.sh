#!/usr/bin/env bash
set -Eeuo pipefail

readonly SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
readonly EXPECTED_CONTROL_ROOT="/home/ubuntu/Restaurant_System/deployment/cloud"
readonly EXPECTED_BACKUP_ROOT="$EXPECTED_CONTROL_ROOT/backups"
readonly EXPECTED_DB_CONTAINER="cloud-db-1"
readonly OPS_LOCK="$EXPECTED_CONTROL_ROOT/.production-ops.lock"
readonly SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly FLYWAY_MANIFEST="$SCRIPT_DIR/ops001-flyway-v26-checksums.txt"
readonly SMOKE_HELPER="$SCRIPT_DIR/production-v26-smoke.py"
readonly DATA_CONTRACT="$SCRIPT_DIR/production-v26-data-contract.sh"
readonly EVIDENCE_HELPER="$SCRIPT_DIR/production-v26-evidence.py"
readonly BACKUP_HELPER="$SCRIPT_DIR/production-backup-rehearsal.sh"
readonly RECOVERY_HELPER="$SCRIPT_DIR/production-v26-recover.sh"
readonly RECOVERY_OVERRIDE="$SCRIPT_DIR/docker-compose.production-v26-recovery.yml"
readonly NGINX_TEMPLATE="$SCRIPT_DIR/nginx.http.conf.template"
readonly DOCKER_TIMEOUT_SECONDS=120
readonly RESTORE_TIMEOUT_SECONDS=900
readonly SMOKE_TIMEOUT_SECONDS=300

RC_MANIFEST=""
RC_MANIFEST_SHA256=""
BACKUP_FILE=""
BACKUP_SHA256=""
RUN_ID="$(openssl rand -hex 16)"
RUN_SUFFIX="$(date -u +%Y%m%d%H%M%S)-${RUN_ID:0:12}"
NETWORK="production-v26-rehearsal-net-$RUN_SUFFIX"
VOLUME="production-v26-rehearsal-db-$RUN_SUFFIX"
DB_CONTAINER="production-v26-rehearsal-db-$RUN_SUFFIX"
BACKEND_CONTAINER=""
FRONTEND_CONTAINER=""
NETWORK_ID=""
VOLUME_MOUNTPOINT=""
DB_CONTAINER_ID=""
BACKEND_CONTAINER_ID=""
FRONTEND_CONTAINER_ID=""
FRONTEND_URL=""
DB_PASSWORD=""
REHEARSAL_JWT_SECRET="$(openssl rand -hex 48)"
BEFORE_FINGERPRINT=""
FINALIZED="false"

die() { printf 'NO_GO|%s\n' "$*" >&2; exit 1; }
digest() { sha256sum "$1" | awk '{print $1}'; }
bounded() {
  local seconds="$1"
  shift
  timeout --foreground --kill-after=10s "${seconds}s" "$@"
}
docker_default() {
  bounded "$DOCKER_TIMEOUT_SECONDS" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"
}
docker_until() {
  local deadline="$1" remaining
  shift
  remaining=$((deadline - SECONDS))
  (( remaining > 0 )) || return 124
  bounded "$remaining" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"
}
docker_restore() {
  bounded "$RESTORE_TIMEOUT_SECONDS" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"
}
wait_fresh_postgres_ready() {
  local container_id="$1" database_user="$2" database_name="$3" timeout_seconds="${4:-120}" deadline logs remaining
  [[ "$timeout_seconds" =~ ^[0-9]+$ && "$timeout_seconds" -ge 2 && "$timeout_seconds" -le 120 ]] || return 1
  deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    logs="$(docker_until "$deadline" logs "$container_id" 2>&1)" || return 1
    if grep -Fq 'PostgreSQL init process complete; ready for start up.' <<<"$logs" &&
       docker_until "$deadline" exec "$container_id" pg_isready -U "$database_user" -d "$database_name" >/dev/null 2>&1; then
      remaining=$((deadline - SECONDS))
      (( remaining > 1 )) || return 1
      sleep 1
      [[ "$(docker_until "$deadline" inspect --format '{{.State.Running}}|{{.State.Restarting}}' "$container_id")" == "true|false" ]] || return 1
      if docker_until "$deadline" exec "$container_id" pg_isready -U "$database_user" -d "$database_name" >/dev/null 2>&1; then
        (( SECONDS < deadline )) && return 0
        return 1
      fi
    fi
    remaining=$((deadline - SECONDS))
    (( remaining > 1 )) || return 1
    sleep 1
  done
  return 1
}

container_identity() {
  docker_default inspect --format '{{.Id}}|{{index .Config.Labels "restaurant.production-v26-rehearsal"}}' "$1"
}

exact_container_id() {
  local output
  output="$(docker_default ps -aq --no-trunc --filter "name=^/${1}$")" || return 1
  [[ -z "$output" || "$output" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf '%s' "$output"
}

exact_volume_name() {
  local output
  output="$(docker_default volume ls -q --filter "name=^${1}$")" || return 1
  [[ -z "$output" || "$output" == "$1" ]] || return 1
  printf '%s' "$output"
}

exact_network_name() {
  local output
  output="$(docker_default network ls --filter "name=^${1}$" --format '{{.Name}}')" || return 1
  [[ -z "$output" || "$output" == "$1" ]] || return 1
  printf '%s' "$output"
}

remove_owned_container() {
  local name="$1" expected_id="$2" actual actual_id remaining
  [[ -n "$name" ]] || return 0
  actual_id="$(exact_container_id "$name")" || return 1
  [[ -n "$actual_id" ]] || return 0
  actual="$(container_identity "$actual_id")" || return 1
  if [[ "${actual#*|}" != "$RUN_ID" ]]; then
    printf 'NO_GO|refusing to remove non-owned container %s\n' "$name" >&2
    return 1
  fi
  if [[ -n "$expected_id" && "${actual%%|*}" != "$expected_id" ]]; then
    printf 'NO_GO|refusing to remove container with changed ID %s\n' "$name" >&2
    return 1
  fi
  docker_default rm -f "${actual%%|*}" >/dev/null || return 1
  remaining="$(exact_container_id "$name")" || return 1
  [[ -z "$remaining" ]]
}

remove_owned_volume() {
  local actual label listed remaining
  listed="$(exact_volume_name "$VOLUME")" || return 1
  [[ -n "$listed" ]] || return 0
  actual="$(docker_default volume inspect --format '{{.Name}}|{{index .Labels "restaurant.production-v26-rehearsal"}}|{{.Mountpoint}}' "$listed")" || return 1
  label="${actual#*|}"; label="${label%%|*}"
  if [[ -n "$actual" && ( "${actual%%|*}" != "$VOLUME" || "$label" != "$RUN_ID" ) ]]; then
    printf 'NO_GO|refusing to remove non-owned rehearsal volume\n' >&2
    return 1
  fi
  if [[ -n "$actual" && -n "$VOLUME_MOUNTPOINT" && "${actual##*|}" != "$VOLUME_MOUNTPOINT" ]]; then
    printf 'NO_GO|refusing to remove rehearsal volume with changed mountpoint\n' >&2
    return 1
  fi
  docker_default volume rm "$VOLUME" >/dev/null || return 1
  remaining="$(exact_volume_name "$VOLUME")" || return 1
  [[ -z "$remaining" ]]
}

remove_owned_network() {
  local actual label listed remaining
  listed="$(exact_network_name "$NETWORK")" || return 1
  [[ -n "$listed" ]] || return 0
  actual="$(docker_default network inspect --format '{{.Id}}|{{index .Labels "restaurant.production-v26-rehearsal"}}|{{.Internal}}' "$listed")" || return 1
  label="${actual#*|}"; label="${label%%|*}"
  if [[ -n "$actual" && ( "$label" != "$RUN_ID" || "${actual##*|}" != "true" ) ]]; then
    printf 'NO_GO|refusing to remove non-owned rehearsal network\n' >&2
    return 1
  fi
  if [[ -n "$actual" && -n "$NETWORK_ID" && "${actual%%|*}" != "$NETWORK_ID" ]]; then
    printf 'NO_GO|refusing to remove rehearsal network with changed ID\n' >&2
    return 1
  fi
  docker_default network rm "${actual%%|*}" >/dev/null || return 1
  remaining="$(exact_network_name "$NETWORK")" || return 1
  [[ -z "$remaining" ]]
}

cleanup_owned() {
  local result=0 containers volumes networks
  remove_owned_container "$FRONTEND_CONTAINER" "$FRONTEND_CONTAINER_ID" || result=1
  remove_owned_container "$BACKEND_CONTAINER" "$BACKEND_CONTAINER_ID" || result=1
  remove_owned_container "$DB_CONTAINER" "$DB_CONTAINER_ID" || result=1
  remove_owned_volume || result=1
  remove_owned_network || result=1
  containers="$(docker_default ps -aq --no-trunc --filter label=restaurant.production-v26-rehearsal="$RUN_ID")" || result=1
  volumes="$(docker_default volume ls -q --filter label=restaurant.production-v26-rehearsal="$RUN_ID")" || result=1
  networks="$(docker_default network ls -q --filter label=restaurant.production-v26-rehearsal="$RUN_ID")" || result=1
  [[ -z "$containers" && -z "$volumes" && -z "$networks" ]] || result=1
  return "$result"
}

on_exit() {
  local result=$?
  trap - EXIT
  if [[ "$FINALIZED" != "true" ]]; then
    cleanup_owned || result=1
  fi
  exit "$result"
}
trap on_exit EXIT

usage() {
  printf '%s\n' "Usage: $0 --backup <absolute-dump> --expected-sha256 <sha256> --rc-manifest <absolute-json> --rc-manifest-sha256 <sha256>"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) shift; BACKUP_FILE="${1:-}" ;;
    --expected-sha256) shift; BACKUP_SHA256="${1:-}" ;;
    --rc-manifest) shift; RC_MANIFEST="${1:-}" ;;
    --rc-manifest-sha256) shift; RC_MANIFEST_SHA256="${1:-}" ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
  shift
done

command -v timeout >/dev/null 2>&1 || die "GNU timeout is required"
[[ "$BACKUP_FILE" == /* && -f "$BACKUP_FILE" && ! -L "$BACKUP_FILE" ]] || die "backup must be an absolute regular file"
[[ "$(realpath "$BACKUP_FILE")" == "$BACKUP_FILE" && "$(dirname "$BACKUP_FILE")" == "$EXPECTED_BACKUP_ROOT" ]] || die "backup is outside the fixed Production backup root"
[[ "$(stat -c '%a|%u' "$BACKUP_FILE")" == "600|$(id -u)" ]] || die "backup owner/mode differs"
[[ "$BACKUP_SHA256" =~ ^[0-9a-f]{64}$ && "$(digest "$BACKUP_FILE")" == "$BACKUP_SHA256" ]] || die "backup digest differs"
[[ "$RC_MANIFEST" == /* && -f "$RC_MANIFEST" && ! -L "$RC_MANIFEST" ]] || die "RC manifest must be an absolute regular file"
[[ "$(realpath "$RC_MANIFEST")" == "$RC_MANIFEST" && "$(stat -c '%a|%u' "$RC_MANIFEST")" == "600|$(id -u)" ]] || die "RC manifest owner/mode/path differs"
[[ "$RC_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ && "$(digest "$RC_MANIFEST")" == "$RC_MANIFEST_SHA256" ]] || die "RC manifest digest differs"

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
    'rehearsal_helper_sha256','smoke_helper_sha256','data_contract_sha256','evidence_contract_sha256',
    'flyway_manifest_sha256','backup_helper_sha256','staging_acceptance_file','staging_acceptance_sha256',
    'staging_repair_evidence_file','staging_repair_evidence_sha256','fresh_backup_file','fresh_backup_sha256',
    'rehearsal_evidence_file','rehearsal_evidence_sha256','production_business_fingerprint',
    'production_printing_fingerprint','backup_flyway_target','flyway_target','production_backup_result',
    'production_backup_restore_result','migration_rehearsal_result','target_app_boot_result',
    'production_data_integrity_result','read_smoke_result','write_smoke_result',
    'android_pad_compatibility_result','store_organization_isolation_result','recovery_proof_result',
    'staging_accepted_artifact_result','agent_6_release_review','production_preflight_result'
)
selected=(
    'status','source_sha','source_main_ancestry','production_previous_sha',
    'production_control_checkout_sha','postgres_image_id','backend_image_id',
    'frontend_image_id','rollback_backend_image_id','rollback_frontend_image_id',
    'tooling_commit_sha','rehearsal_helper_sha256','smoke_helper_sha256',
    'data_contract_sha256','evidence_contract_sha256','backup_helper_sha256','recovery_helper_sha256',
    'recovery_override_sha256','flyway_manifest_sha256','staging_acceptance_file',
    'staging_acceptance_sha256','staging_repair_evidence_file',
    'staging_repair_evidence_sha256','backup_flyway_target','flyway_target'
)
d=json.load(open(sys.argv[1],encoding='utf-8'),object_pairs_hook=reject_duplicates)
if set(d) != set(keys): raise SystemExit(2)
for key in selected:
    value=d[key]
    if not isinstance(value,str) or '\n' in value: raise SystemExit(2)
    print(value)
PY
)" || die "RC manifest is invalid"
mapfile -t rc <<<"$rc_output"
unset rc_output
[[ ${#rc[@]} -eq 25 ]] || die "RC manifest fields are incomplete"
RC_STATUS="${rc[0]}"; SOURCE_SHA="${rc[1]}"; MAIN_ANCESTRY="${rc[2]}"; PRODUCTION_PREVIOUS_SHA="${rc[3]}"
CONTROL_SHA="${rc[4]}"; POSTGRES_IMAGE_ID="${rc[5]}"; TARGET_BACKEND_ID="${rc[6]}"; TARGET_FRONTEND_ID="${rc[7]}"
ROLLBACK_BACKEND_ID="${rc[8]}"; ROLLBACK_FRONTEND_ID="${rc[9]}"; TOOLING_SHA="${rc[10]}"; REHEARSAL_DIGEST="${rc[11]}"
SMOKE_DIGEST="${rc[12]}"; DATA_CONTRACT_DIGEST="${rc[13]}"; EVIDENCE_HELPER_DIGEST="${rc[14]}"; BACKUP_HELPER_DIGEST="${rc[15]}"
RECOVERY_DIGEST="${rc[16]}"; RECOVERY_OVERRIDE_DIGEST="${rc[17]}"; FLYWAY_DIGEST="${rc[18]}"
STAGING_EVIDENCE_FILE="${rc[19]}"; STAGING_EVIDENCE_DIGEST="${rc[20]}"; STAGING_REPAIR_FILE="${rc[21]}"; STAGING_REPAIR_DIGEST="${rc[22]}"
BACKUP_TARGET="${rc[23]}"; FLYWAY_TARGET="${rc[24]}"

[[ "$RC_STATUS" == "RC_PREPARED" || "$RC_STATUS" == "RC_FROZEN" ]] || die "RC status is invalid"
[[ "$SOURCE_SHA" =~ ^[0-9a-f]{40}$ && "$PRODUCTION_PREVIOUS_SHA" =~ ^[0-9a-f]{40}$ && "$CONTROL_SHA" =~ ^[0-9a-f]{40}$ && "$TOOLING_SHA" =~ ^[0-9a-f]{40}$ ]] || die "RC Git identity is invalid"
[[ "$MAIN_ANCESTRY" == "PASS" && "$BACKUP_TARGET" == "V10" && "$FLYWAY_TARGET" == "V26" ]] || die "RC migration boundary differs"
for image in "$POSTGRES_IMAGE_ID" "$TARGET_BACKEND_ID" "$TARGET_FRONTEND_ID" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID"; do
  [[ "$image" =~ ^sha256:[0-9a-f]{64}$ ]] || die "RC image identity is invalid"
  docker_default image inspect "$image" >/dev/null || die "RC image is unavailable"
done
for value in "$REHEARSAL_DIGEST" "$SMOKE_DIGEST" "$DATA_CONTRACT_DIGEST" "$EVIDENCE_HELPER_DIGEST" "$BACKUP_HELPER_DIGEST" "$RECOVERY_DIGEST" "$RECOVERY_OVERRIDE_DIGEST" "$FLYWAY_DIGEST" "$STAGING_EVIDENCE_DIGEST" "$STAGING_REPAIR_DIGEST"; do
  [[ "$value" =~ ^[0-9a-f]{64}$ ]] || die "RC digest is invalid"
done
[[ "$(digest "${BASH_SOURCE[0]}")" == "$REHEARSAL_DIGEST" ]] || die "rehearsal helper digest differs"
[[ "$(digest "$SMOKE_HELPER")" == "$SMOKE_DIGEST" && "$(digest "$DATA_CONTRACT")" == "$DATA_CONTRACT_DIGEST" && "$(digest "$EVIDENCE_HELPER")" == "$EVIDENCE_HELPER_DIGEST" ]] || die "rehearsal contract tooling digest differs"
[[ "$(digest "$BACKUP_HELPER")" == "$BACKUP_HELPER_DIGEST" && "$(digest "$RECOVERY_HELPER")" == "$RECOVERY_DIGEST" && "$(digest "$RECOVERY_OVERRIDE")" == "$RECOVERY_OVERRIDE_DIGEST" ]] || die "backup/recovery tooling digest differs"
[[ "$(digest "$FLYWAY_MANIFEST")" == "$FLYWAY_DIGEST" ]] || die "Flyway manifest digest differs"
EXPECTED_STAGING_EVIDENCE="$(realpath "$SCRIPT_DIR/../../docs/governance/PHASE_B_PART2_PRODUCT_FLOW_STAGING_ACCEPTANCE_EVIDENCE.md")"
EXPECTED_STAGING_REPAIR="$(realpath "$SCRIPT_DIR/../../docs/governance/PHASE_B_PART2_P0_P1_REPAIR_STAGING_EVIDENCE.md")"
[[ "$STAGING_EVIDENCE_FILE" == "$EXPECTED_STAGING_EVIDENCE" && "$STAGING_REPAIR_FILE" == "$EXPECTED_STAGING_REPAIR" ]] || die "Staging evidence path differs"
[[ -f "$STAGING_EVIDENCE_FILE" && ! -L "$STAGING_EVIDENCE_FILE" && -f "$STAGING_REPAIR_FILE" && ! -L "$STAGING_REPAIR_FILE" ]] || die "Staging evidence is unavailable"
[[ "$(digest "$STAGING_EVIDENCE_FILE")" == "$STAGING_EVIDENCE_DIGEST" && "$(digest "$STAGING_REPAIR_FILE")" == "$STAGING_REPAIR_DIGEST" ]] || die "Staging evidence digest differs"
bounded 60 env -i PATH="$SAFE_PATH" python3 -I "$EVIDENCE_HELPER" --scope staging \
  --staging-full "$STAGING_EVIDENCE_FILE" --staging-repair "$STAGING_REPAIR_FILE" \
  --source-sha "$SOURCE_SHA" >/dev/null || die "Staging evidence contract differs"
[[ "$(git -C "$SCRIPT_DIR/../.." rev-parse HEAD)" == "$TOOLING_SHA" ]] || die "tooling checkout SHA differs"
[[ -z "$(git -C "$SCRIPT_DIR/../.." status --porcelain)" ]] || die "tooling checkout is dirty"
git -C "$SCRIPT_DIR/../.." merge-base --is-ancestor "$SOURCE_SHA" "$TOOLING_SHA" || die "accepted source is not tooling ancestry"
[[ "$(git -C /home/ubuntu/Restaurant_System rev-parse HEAD)" == "$CONTROL_SHA" ]] || die "Production control checkout drifted"
[[ -f "$OPS_LOCK" && ! -L "$OPS_LOCK" && "$(stat -c '%a|%u' "$OPS_LOCK")" == "600|$(id -u)" ]] || die "Production ops lock identity differs"
exec 9<>"$OPS_LOCK"; flock -n 9 || die "another Production operation holds the lock"
[[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID" ]] || die "current Production backend image differs"
[[ "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$ROLLBACK_FRONTEND_ID" ]] || die "current Production frontend image differs"
[[ "$(docker_default inspect --format '{{.Image}}|{{.State.Health.Status}}' "$EXPECTED_DB_CONTAINER")" == "$POSTGRES_IMAGE_ID|healthy" ]] || die "Production PostgreSQL identity/health differs"

expected_ledger() {
  local target="$1"
  awk -F'|' -v target="$target" '/^[0-9]+[|]/ && ($1 + 0) <= (target + 0) {print $1 "|" $2 "|true|" $3}' "$FLYWAY_MANIFEST"
}
ledger() {
  docker_default exec "$1" sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version || chr(124) || script || chr(124) || success::text || chr(124) || checksum from flyway_schema_history order by installed_rank"'
}
ledger_named() {
  docker_default exec "$1" sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$1" -c "select version || chr(124) || script || chr(124) || success::text || chr(124) || checksum from flyway_schema_history order by installed_rank"' sh "$2"
}
[[ "$(ledger "$EXPECTED_DB_CONTAINER")" == "$(expected_ledger 10)" ]] || die "live Production Flyway is not exact V10"

REHEARSAL_QUERY_DB="restaurant_pos_rehearsal"
v26_db_query() {
  docker_default exec -i "$DB_CONTAINER_ID" sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$1"' sh "$REHEARSAL_QUERY_DB"
}
# shellcheck disable=SC1090
source "$DATA_CONTRACT"

assert_names_absent() {
  local container network volume
  container="$(exact_container_id "$DB_CONTAINER")" || die "cannot query rehearsal DB name"
  network="$(exact_network_name "$NETWORK")" || die "cannot query rehearsal network name"
  volume="$(exact_volume_name "$VOLUME")" || die "cannot query rehearsal volume name"
  [[ -z "$container" ]] || die "rehearsal DB name already exists"
  [[ -z "$network" ]] || die "rehearsal network name already exists"
  [[ -z "$volume" ]] || die "rehearsal volume name already exists"
}

create_network() {
  local created
  created="$(docker_default network create --internal --label restaurant.production-v26-rehearsal="$RUN_ID" "$NETWORK")" || die "cannot create rehearsal network"
  [[ "$created" =~ ^[0-9a-f]{64}$ ]] || die "rehearsal network ID is invalid"
  NETWORK_ID="$created"
  [[ "$(docker_default network inspect --format '{{.Id}}|{{index .Labels "restaurant.production-v26-rehearsal"}}|{{.Internal}}' "$NETWORK")" == "$NETWORK_ID|$RUN_ID|true" ]] || die "rehearsal network ownership differs"
}

create_volume() {
  local created existing
  existing="$(exact_volume_name "$VOLUME")" || die "cannot query rehearsal volume name"
  [[ -z "$existing" ]] || die "rehearsal volume name already exists"
  created="$(docker_default volume create --label restaurant.production-v26-rehearsal="$RUN_ID" "$VOLUME")" || die "cannot create rehearsal volume"
  [[ "$created" == "$VOLUME" ]] || die "rehearsal volume identity is invalid"
  VOLUME_MOUNTPOINT="$(docker_default volume inspect --format '{{.Mountpoint}}' "$VOLUME")"
  [[ -n "$VOLUME_MOUNTPOINT" && "$(docker_default volume inspect --format '{{.Name}}|{{index .Labels "restaurant.production-v26-rehearsal"}}' "$VOLUME")" == "$VOLUME|$RUN_ID" ]] || die "rehearsal volume ownership differs"
}

start_db_and_restore() {
  local created
  create_volume
  DB_PASSWORD="$(openssl rand -hex 24)"
  created="$(docker_default run -d --pull=never --name "$DB_CONTAINER" --label restaurant.production-v26-rehearsal="$RUN_ID" --network "$NETWORK" --network-alias db --cpus 1 --memory 768m --pids-limit 256 -v "$VOLUME:/var/lib/postgresql/data" -e POSTGRES_DB=restaurant_pos_rehearsal -e POSTGRES_USER=rehearsal -e POSTGRES_PASSWORD="$DB_PASSWORD" "$POSTGRES_IMAGE_ID")" || die "cannot create isolated PostgreSQL"
  [[ "$created" =~ ^[0-9a-f]{64}$ ]] || die "isolated PostgreSQL ID is invalid"
  DB_CONTAINER_ID="$created"
  [[ "$(container_identity "$DB_CONTAINER")" == "$DB_CONTAINER_ID|$RUN_ID" ]] || die "isolated PostgreSQL ownership differs"
  wait_fresh_postgres_ready "$DB_CONTAINER_ID" rehearsal restaurant_pos_rehearsal || die "isolated PostgreSQL did not reach stable post-init readiness"
  docker_restore exec -i "$DB_CONTAINER_ID" timeout -s TERM -k 10 840 pg_restore -U rehearsal -d restaurant_pos_rehearsal --no-owner --no-privileges --exit-on-error --single-transaction <"$BACKUP_FILE" || die "isolated restore failed or timed out"
  [[ "$(ledger "$DB_CONTAINER_ID")" == "$(expected_ledger 10)" ]] || die "restored clone Flyway is not exact V10"
}

container_network_ip() {
  local address
  address="$(docker_default inspect --format "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}" "$1")" || return 1
  [[ "$address" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || return 1
  printf '%s' "$address"
}

start_backend() {
  local image="$1" target="$2" label="$3" created existing
  BACKEND_CONTAINER="production-v26-rehearsal-${label}-backend-$RUN_SUFFIX"
  existing="$(exact_container_id "$BACKEND_CONTAINER")" || die "cannot query rehearsal backend name"
  [[ -z "$existing" ]] || die "rehearsal backend name already exists"
  created="$(docker_default run -d --pull=never --name "$BACKEND_CONTAINER" --label restaurant.production-v26-rehearsal="$RUN_ID" --network "$NETWORK" --network-alias backend --cpus 2 --memory 1024m --pids-limit 512 -e SPRING_PROFILES_ACTIVE=cloud -e SERVER_PORT=8080 -e DB_HOST=db -e DB_PORT=5432 -e DB_NAME=restaurant_pos_rehearsal -e DB_USER=rehearsal -e DB_PASSWORD="$DB_PASSWORD" -e JWT_SECRET="$REHEARSAL_JWT_SECRET" -e APP_ENVIRONMENT=production -e FLYWAY_TARGET="$target" -e APP_AUTH_X_USER_ID_FALLBACK_ENABLED=false -e APP_DEV_TOOLS_ROLE_SWITCHER_ENABLED=false -e APP_SEED_DEFAULT_USERS_ENABLED=false -e APP_SEED_DEMO_DATA_ENABLED=false -e APP_SEED_MEMBERSHIP_SUPPLEMENT_ENABLED=false -e APP_SEED_PRODUCTION_BOOTSTRAP_ENABLED=false -e APP_PHASE_B_PROVISIONING_ENABLED=false -e APP_PHASE_B_RUNTIME=disabled -e APP_PRINTING_ALLOWED_MODES=DISABLED,MOCK,PAD_DIRECT -e APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED=false -e APP_PRINTING_DISPATCH_OUTBOX_INITIAL_DELAY_MS=3600000 "$image")" || die "cannot create rehearsal backend"
  [[ "$created" =~ ^[0-9a-f]{64}$ ]] || die "rehearsal backend ID is invalid"
  BACKEND_CONTAINER_ID="$created"
  [[ "$(container_identity "$BACKEND_CONTAINER")" == "$BACKEND_CONTAINER_ID|$RUN_ID" ]] || die "rehearsal backend ownership differs"
}

wait_backend() {
  local address deadline body
  address="$(container_network_ip "$BACKEND_CONTAINER_ID")" || return 1
  deadline=$((SECONDS + 120))
  while (( SECONDS < deadline )); do
    body="$(curl --noproxy '*' --connect-timeout 2 --max-time 5 -fsS "http://$address:8080/api/v1/system/health" 2>/dev/null || true)"
    [[ "$body" == *'"status":"UP"'* ]] && return 0
    sleep 1
  done
  return 1
}

start_frontend() {
  local image="$1" label="$2" created deadline code existing
  FRONTEND_CONTAINER="production-v26-rehearsal-${label}-frontend-$RUN_SUFFIX"
  existing="$(exact_container_id "$FRONTEND_CONTAINER")" || die "cannot query rehearsal frontend name"
  [[ -z "$existing" ]] || die "rehearsal frontend name already exists"
  created="$(docker_default run -d --pull=never --name "$FRONTEND_CONTAINER" --label restaurant.production-v26-rehearsal="$RUN_ID" --network "$NETWORK" --cpus 0.5 --memory 256m --pids-limit 256 -e NGINX_SERVER_NAME=localhost -v "$NGINX_TEMPLATE:/etc/nginx/templates/default.conf.template:ro" "$image")" || die "cannot create rehearsal frontend"
  [[ "$created" =~ ^[0-9a-f]{64}$ ]] || die "rehearsal frontend ID is invalid"
  FRONTEND_CONTAINER_ID="$created"
  [[ "$(container_identity "$FRONTEND_CONTAINER")" == "$FRONTEND_CONTAINER_ID|$RUN_ID" ]] || die "rehearsal frontend ownership differs"
  FRONTEND_URL="http://$(container_network_ip "$FRONTEND_CONTAINER_ID")" || die "temporary frontend address is unavailable"
  deadline=$((SECONDS + 60))
  while (( SECONDS < deadline )); do
    code="$(curl --noproxy '*' --connect-timeout 2 --max-time 5 -sS -o /dev/null -w '%{http_code}' "$FRONTEND_URL/" 2>/dev/null || true)"
    [[ "$code" == "200" ]] && return
    sleep 1
  done
  die "temporary frontend did not become ready"
}

stop_app() {
  remove_owned_container "$FRONTEND_CONTAINER" "$FRONTEND_CONTAINER_ID" || die "cannot remove exact rehearsal frontend"
  remove_owned_container "$BACKEND_CONTAINER" "$BACKEND_CONTAINER_ID" || die "cannot remove exact rehearsal backend"
  FRONTEND_CONTAINER=""; FRONTEND_CONTAINER_ID=""; FRONTEND_URL=""
  BACKEND_CONTAINER=""; BACKEND_CONTAINER_ID=""
}

stop_db_and_volume() {
  remove_owned_container "$DB_CONTAINER" "$DB_CONTAINER_ID" || die "cannot remove exact rehearsal DB"
  DB_CONTAINER_ID=""
  remove_owned_volume || die "cannot remove exact rehearsal volume"
  VOLUME_MOUNTPOINT=""
}

rehearsal_database_absent() {
  local database="$1" count
  [[ "$database" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || return 1
  count="$(docker_default exec -i "$DB_CONTAINER_ID" psql -qX -v ON_ERROR_STOP=1 -At -U rehearsal -d postgres -v "candidate=$database" <<'SQL'
select count(*) from pg_database where datname = :'candidate';
SQL
)" || return 1
  [[ "$count" == "0" ]]
}

create_rehearsal_database() {
  local database="$1"
  [[ "$database" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || return 1
  docker_restore exec "$DB_CONTAINER_ID" timeout -s TERM -k 10 120 createdb -U rehearsal -O rehearsal "$database"
}

drop_rehearsal_database() {
  local database="$1" fail_db="$2" restore_db="$3" quarantine_db="$4"
  [[ "$database" == "$fail_db" || "$database" == "$restore_db" || "$database" == "$quarantine_db" ]] || return 1
  docker_restore exec "$DB_CONTAINER_ID" timeout -s TERM -k 10 120 dropdb --if-exists --force -U rehearsal "$database"
}

terminate_rehearsal_database_sessions() {
  local database="$1"
  [[ "$database" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || return 1
  docker_restore exec -i "$DB_CONTAINER_ID" timeout -s TERM -k 10 120 psql -qX -v ON_ERROR_STOP=1 -At -U rehearsal -d postgres -v "candidate=$database" >/dev/null <<'SQL'
select pg_terminate_backend(pid) from pg_stat_activity where datname = :'candidate' and pid <> pg_backend_pid();
SQL
}

rename_rehearsal_database() {
  local source="$1" target="$2"
  [[ "$source" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ && "$target" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || return 1
  docker_restore exec "$DB_CONTAINER_ID" timeout -s TERM -k 10 120 psql -qX -v ON_ERROR_STOP=1 -At -U rehearsal -d postgres -c "alter database \"$source\" rename to \"$target\"" >/dev/null
}

run_smoke() {
  local base_url="$1" mode="$2"
  local arguments=(--base-url "$base_url" --db-container "$DB_CONTAINER_ID" --mode "$mode" --evidence-run-id "$RUN_ID")
  arguments+=(--expected-db-container-id "$DB_CONTAINER_ID" --expected-backend-container-id "$BACKEND_CONTAINER_ID" --expected-api-container-id "$FRONTEND_CONTAINER_ID" --expected-run-id "$RUN_ID" --expected-network "$NETWORK")
  bounded "$SMOKE_TIMEOUT_SECONDS" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" JWT_SECRET="$REHEARSAL_JWT_SECRET" python3 -I "$SMOKE_HELPER" "${arguments[@]}"
}

android_compatibility_check() {
  local controller previous_contract target_contract version_name version_code
  local -a stable_contract_paths=(
    backend/src/main/java/com/restaurant/system/auth/controller/AuthController.java
    backend/src/main/java/com/restaurant/system/auth/dto
    backend/src/main/java/com/restaurant/system/auth/filter/AuthTokenFilter.java
    backend/src/main/java/com/restaurant/system/auth/service
    backend/src/main/java/com/restaurant/system/common/auth/AuthenticatedUser.java
    backend/src/main/java/com/restaurant/system/printing/dto/DeviceHeartbeatRequest.java
    backend/src/main/java/com/restaurant/system/printing/dto/DeviceRegisterRequest.java
    backend/src/main/java/com/restaurant/system/printing/dto/DeviceRegisterResponse.java
    backend/src/main/java/com/restaurant/system/printing/dto/StoreDeviceResponse.java
    backend/src/main/java/com/restaurant/system/printing/dto/PadPrintJobClaimRequest.java
    backend/src/main/java/com/restaurant/system/printing/dto/PadPrintJobStartPrintRequest.java
    backend/src/main/java/com/restaurant/system/printing/dto/PadPrintJobCompleteRequest.java
    backend/src/main/java/com/restaurant/system/printing/dto/PadPrintJobFailRequest.java
    backend/src/main/java/com/restaurant/system/printing/dto/PadPrintJobReleaseRequest.java
    backend/src/main/java/com/restaurant/system/printing/dto/PadPrintJobPayloadResponse.java
    backend/src/main/java/com/restaurant/system/printing/service/PadPrintJobService.java
    backend/src/main/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImpl.java
    backend/src/main/java/com/restaurant/system/printing/service/StoreDeviceService.java
  )

  git -C "$SCRIPT_DIR/../.." diff --quiet "$PRODUCTION_PREVIOUS_SHA" "$SOURCE_SHA" -- restaurant-pad-app ||
    die "accepted backend requires a different Android app tree"
  git -C "$SCRIPT_DIR/../.." diff --quiet "$PRODUCTION_PREVIOUS_SHA" "$SOURCE_SHA" -- "${stable_contract_paths[@]}" ||
    die "Android worker request/response/service contract changed"

  for controller in \
    backend/src/main/java/com/restaurant/system/printing/controller/StoreDeviceController.java \
    backend/src/main/java/com/restaurant/system/printing/controller/PadPrintingController.java; do
    previous_contract="$(git -C "$SCRIPT_DIR/../.." show "$PRODUCTION_PREVIOUS_SHA:$controller" | grep -E '@(Get|Post|Put|Delete)Mapping|@RequestHeader' | tr -s '[:space:]' ' ')"
    target_contract="$(git -C "$SCRIPT_DIR/../.." show "$SOURCE_SHA:$controller" | grep -E '@(Get|Post|Put|Delete)Mapping|@RequestHeader' | tr -s '[:space:]' ' ')"
    [[ -n "$previous_contract" && "$target_contract" == "$previous_contract" ]] ||
      die "Android API paths or required device headers changed"
  done

  ! git -C "$SCRIPT_DIR/../.." grep -Ei 'minimum.?app.?version|min.?app.?version|android.?version.?guard' "$SOURCE_SHA" -- \
    backend/src/main/java/com/restaurant/system/printing >/dev/null || die "new Android minimum-version guard exists"
  version_name="$(git -C "$SCRIPT_DIR/../.." show "$SOURCE_SHA:restaurant-pad-app/android/app/build.gradle" | awk '/versionName/ {gsub(/\"/,"",$2); print $2; exit}')"
  version_code="$(git -C "$SCRIPT_DIR/../.." show "$SOURCE_SHA:restaurant-pad-app/android/app/build.gradle" | awk '/versionCode/ {print $2; exit}')"
  [[ "$version_name" == "0.2.0-offline-pr7" && "$version_code" == "2" ]] || die "Android app identity differs from Production evidence"
  grep -Fq 'Installed versionCode: 2' "$SCRIPT_DIR/../../docs/archive/governance-pre-simplification/runtime/ANDROID_RUNTIME_EVIDENCE.md" || die "Production Android versionCode evidence differs"
  grep -Fq 'Installed versionName: 0.2.0-offline-pr7' "$SCRIPT_DIR/../../docs/archive/governance-pre-simplification/runtime/ANDROID_RUNTIME_EVIDENCE.md" || die "Production Android versionName evidence differs"

  printf 'ANDROID_COMPATIBILITY|run_id=%s|app_version=%s|version_code=%s|webview_entry=unchanged|auth_token=unchanged|register_heartbeat=additive|pad_direct_contract=unchanged|routes_headers=unchanged|min_version_guard=absent|result=PASS\n' "$RUN_ID" "$version_name" "$version_code"
}

android_compatibility_check
assert_names_absent
create_network
start_db_and_restore
BEFORE_FINGERPRINT="$(v26_business_fingerprint)"
BEFORE_PRINTING="$(v26_printing_fingerprint)"
printf 'RESTORE|run_id=%s|backup_sha256=%s|flyway=V10-exact|network_internal=true|volume_isolated=true|result=PASS\n' "$RUN_ID" "$BACKUP_SHA256"
printf 'DATA_BASELINE|run_id=%s|business_fingerprint=%s|printing_fingerprint=%s|result=PASS\n' "$RUN_ID" "$BEFORE_FINGERPRINT" "$BEFORE_PRINTING"

start_backend "$TARGET_BACKEND_ID" 26 target
wait_backend || die "target backend did not boot on V26"
[[ "$(ledger "$DB_CONTAINER_ID")" == "$(expected_ledger 26)" ]] || die "V10 to V26 Flyway ledger differs"
AFTER_FINGERPRINT="$(v26_business_fingerprint)"
AFTER_PRINTING="$(v26_printing_fingerprint)"
[[ "$AFTER_FINGERPRINT" == "$BEFORE_FINGERPRINT" ]] || die "Production-clone business content changed during migration"
[[ "$AFTER_PRINTING" == "$BEFORE_PRINTING" ]] || die "Production-clone printing topology changed during migration"
IFS='|' read -r stores pricing groups components modules rule_sets revisions profiles masters cleanup_count additive_violations <<<"$(v26_additive_contract)"
[[ "$additive_violations" == "0" ]] || die "V11-V26 additive relationship contract has violations"
printf 'MIGRATION|run_id=%s|from=V10|to=V26|migrations=16|ledger=exact|business_fingerprint=unchanged|printing_fingerprint=unchanged|result=PASS\n' "$RUN_ID"
printf 'ADDITIVE_INVARIANTS|run_id=%s|stores=%s|pricing=%s|groups=%s|components=%s|modules=%s|rule_sets=%s|revisions=%s|profiles=%s|masters=%s|cleanup=%s|violations=0|result=PASS\n' "$RUN_ID" "$stores" "$pricing" "$groups" "$components" "$modules" "$rule_sets" "$revisions" "$profiles" "$masters" "$cleanup_count"

target_restart_epoch="$(date +%s)"
docker_default restart "$BACKEND_CONTAINER_ID" >/dev/null
wait_backend || die "target backend failed same-image restart"
[[ "$(ledger "$DB_CONTAINER_ID")" == "$(expected_ledger 26)" ]] || die "Flyway ledger changed on target restart"
target_restart_logs="$(docker_default logs --since "$target_restart_epoch" "$BACKEND_CONTAINER_ID" 2>&1)"
grep -Fq 'is up to date. No migration necessary' <<<"$target_restart_logs" || die "target restart did not prove no pending migration"
start_frontend "$TARGET_FRONTEND_ID" target
run_smoke "$FRONTEND_URL" write
printf 'TARGET_STACK|run_id=%s|backend_image_id=%s|frontend_image_id=%s|restart=PASS|result=PASS\n' "$RUN_ID" "$TARGET_BACKEND_ID" "$TARGET_FRONTEND_ID"

stop_app
start_backend "$ROLLBACK_BACKEND_ID" 10 old-on-v26
OLD_ON_V26="NOT_SUPPORTED"
if wait_backend; then
  start_frontend "$ROLLBACK_FRONTEND_ID" old-on-v26
  if old_on_v26_output="$(run_smoke "$FRONTEND_URL" legacy-read 2>&1)"; then
    printf '%s\n' "$old_on_v26_output"
    OLD_ON_V26="PASS"
  fi
fi
printf 'OLD_PRODUCTION_APP_ON_V26_SCHEMA=%s\n' "$OLD_ON_V26"
stop_app

# Exercise the exact recovery architecture while the disposable V26 database is
# retained. A deliberately invalid restore must fail against a separate database
# without changing the primary clone.
PRIMARY_DB="restaurant_pos_rehearsal"
FAILED_RESTORE_DB="v10_restore_fail_${RUN_ID:0:16}"
RESTORED_V10_DB="v10_recovery_${RUN_ID:0:16}"
QUARANTINED_V26_DB="v26_quarantine_${RUN_ID:0:16}"
REHEARSAL_QUERY_DB="$PRIMARY_DB"
FAILED_V26_BUSINESS="$(v26_business_fingerprint)"
FAILED_V26_PRINTING="$(v26_printing_fingerprint)"
[[ "$(ledger_named "$DB_CONTAINER_ID" "$PRIMARY_DB")" == "$(expected_ledger 26)" ]] || die "failed V26 clone ledger differs before recovery proof"
rehearsal_database_absent "$FAILED_RESTORE_DB" && rehearsal_database_absent "$RESTORED_V10_DB" && rehearsal_database_absent "$QUARANTINED_V26_DB" || die "generated recovery proof database exists or cannot be verified absent"

create_rehearsal_database "$FAILED_RESTORE_DB" || die "cannot create restore-failure proof database"
if printf 'not-a-postgresql-custom-dump\n' | docker_restore exec -i "$DB_CONTAINER_ID" timeout -s TERM -k 10 120 pg_restore -U rehearsal -d "$FAILED_RESTORE_DB" --no-owner --no-privileges --exit-on-error --single-transaction >/dev/null 2>&1; then
  die "invalid recovery dump unexpectedly restored"
fi
REHEARSAL_QUERY_DB="$PRIMARY_DB"
[[ "$(ledger_named "$DB_CONTAINER_ID" "$PRIMARY_DB")" == "$(expected_ledger 26)" ]] || die "failed restore changed primary Flyway ledger"
[[ "$(v26_business_fingerprint)" == "$FAILED_V26_BUSINESS" && "$(v26_printing_fingerprint)" == "$FAILED_V26_PRINTING" ]] || die "failed restore changed primary clone"
drop_rehearsal_database "$FAILED_RESTORE_DB" "$FAILED_RESTORE_DB" "$RESTORED_V10_DB" "$QUARANTINED_V26_DB" || die "restore-failure proof database cleanup failed"
printf 'RECOVERY_RESTORE_FAILURE_PROOF|run_id=%s|restore_failed=true|primary_untouched=true|result=PASS\n' "$RUN_ID"

create_rehearsal_database "$RESTORED_V10_DB" || die "cannot create validated V10 recovery database"
docker_restore exec -i "$DB_CONTAINER_ID" sh -eu -c 'timeout -s TERM -k 10 840 pg_restore -U rehearsal -d "$1" --no-owner --no-privileges --exit-on-error --single-transaction' sh "$RESTORED_V10_DB" <"$BACKUP_FILE" || die "validated V10 recovery restore failed"
[[ "$(ledger_named "$DB_CONTAINER_ID" "$RESTORED_V10_DB")" == "$(expected_ledger 10)" ]] || die "validated recovery database is not exact V10"
REHEARSAL_QUERY_DB="$RESTORED_V10_DB"
[[ "$(v26_business_fingerprint)" == "$BEFORE_FINGERPRINT" ]] || die "validated recovery business fingerprint differs"
[[ "$(v26_printing_fingerprint)" == "$BEFORE_PRINTING" ]] || die "validated recovery printing fingerprint differs"

terminate_rehearsal_database_sessions "$PRIMARY_DB" || die "cannot freeze rehearsal primary database"
terminate_rehearsal_database_sessions "$RESTORED_V10_DB" || die "cannot freeze rehearsal recovery database"
rename_rehearsal_database "$PRIMARY_DB" "$QUARANTINED_V26_DB" || die "cannot quarantine rehearsal V26 database"
if ! rename_rehearsal_database "$RESTORED_V10_DB" "$PRIMARY_DB"; then
  rename_rehearsal_database "$QUARANTINED_V26_DB" "$PRIMARY_DB" || die "rehearsal switch and name rollback both failed"
  die "rehearsal V10 database switch failed; V26 primary name restored"
fi
REHEARSAL_QUERY_DB="$PRIMARY_DB"
[[ "$(ledger_named "$DB_CONTAINER_ID" "$PRIMARY_DB")" == "$(expected_ledger 10)" ]] || die "switched rehearsal database is not exact V10"
[[ "$(v26_business_fingerprint)" == "$BEFORE_FINGERPRINT" && "$(v26_printing_fingerprint)" == "$BEFORE_PRINTING" ]] || die "switched rehearsal recovery fingerprint differs"
start_backend "$ROLLBACK_BACKEND_ID" 10 recovery
wait_backend || die "old Production backend did not boot after recovery restore"
start_frontend "$ROLLBACK_FRONTEND_ID" recovery
run_smoke "$FRONTEND_URL" legacy-read
[[ "$(ledger_named "$DB_CONTAINER_ID" "$PRIMARY_DB")" == "$(expected_ledger 10)" ]] || die "recovery Flyway is not exact V10"
[[ "$(v26_business_fingerprint)" == "$BEFORE_FINGERPRINT" && "$(v26_printing_fingerprint)" == "$BEFORE_PRINTING" ]] || die "verified recovery content fingerprint differs"
terminate_rehearsal_database_sessions "$QUARANTINED_V26_DB" || die "cannot prepare rehearsal quarantine cleanup"
drop_rehearsal_database "$QUARANTINED_V26_DB" "$FAILED_RESTORE_DB" "$RESTORED_V10_DB" "$QUARANTINED_V26_DB" || die "rehearsal quarantine cleanup failed"
printf 'RECOVERY_PROOF|run_id=%s|mode=validated-temp-db-switch|recovery_helper_sha256=%s|restore_failure_original_untouched=true|validated_temp=true|flyway=V10-exact|business_fingerprint=restored|result=PASS\n' "$RUN_ID" "$RECOVERY_DIGEST"

cleanup_owned || die "exact rehearsal resource cleanup failed"
FRONTEND_CONTAINER_ID=""; BACKEND_CONTAINER_ID=""; DB_CONTAINER_ID=""; VOLUME_MOUNTPOINT=""; NETWORK_ID=""
children="$(jobs -pr | wc -l | tr -d ' ')"
[[ "$children" == "0" ]] || die "rehearsal child process remains"
FINALIZED="true"
printf 'RESOURCE_CLEANUP|run_id=%s|containers=0|networks=0|volumes=0|children=0|result=PASS\n' "$RUN_ID"
printf 'REHEARSAL|run_id=%s|source_sha=%s|backend_image_id=%s|frontend_image_id=%s|backup_sha256=%s|production_clone=true|real_printer=false|real_pad=false|result=PASS\n' "$RUN_ID" "$SOURCE_SHA" "$TARGET_BACKEND_ID" "$TARGET_FRONTEND_ID" "$BACKUP_SHA256"
