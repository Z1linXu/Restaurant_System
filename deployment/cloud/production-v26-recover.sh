#!/usr/bin/env bash
set -Eeuo pipefail

readonly SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
readonly EXPECTED_PROJECT="cloud"
readonly EXPECTED_CONTROL_ROOT="/home/ubuntu/Restaurant_System/deployment/cloud"
readonly EXPECTED_POSTGRES_DATA_DIR="$EXPECTED_CONTROL_ROOT/data/postgres"
readonly EXPECTED_NGINX_TEMPLATE="$EXPECTED_CONTROL_ROOT/data/nginx/default.conf.template"
readonly EXPECTED_CERTBOT_WWW_DIR="$EXPECTED_CONTROL_ROOT/data/certbot-www"
readonly EXPECTED_LETSENCRYPT_DIR="$EXPECTED_CONTROL_ROOT/data/letsencrypt"
readonly OPS_LOCK="$EXPECTED_CONTROL_ROOT/.production-ops.lock"
readonly SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BASE_COMPOSE="$EXPECTED_CONTROL_ROOT/docker-compose.yml"
readonly RECOVERY_OVERRIDE="$SCRIPT_DIR/docker-compose.production-v26-recovery.yml"
readonly ENV_FILE="$EXPECTED_CONTROL_ROOT/.env"
readonly FLYWAY_MANIFEST="$SCRIPT_DIR/ops001-flyway-v26-checksums.txt"
readonly SMOKE_HELPER="$SCRIPT_DIR/production-v26-smoke.py"
readonly DATA_CONTRACT="$SCRIPT_DIR/production-v26-data-contract.sh"
readonly EVIDENCE_HELPER="$SCRIPT_DIR/production-v26-evidence.py"
readonly DOCKER_TIMEOUT_SECONDS=120
readonly RESTORE_TIMEOUT_SECONDS=900
readonly COMPOSE_TIMEOUT_SECONDS=240

RC_MANIFEST=""
RC_MANIFEST_SHA256=""
EXECUTE_COUNT=0
RECOVERY_RUN_ID="$(openssl rand -hex 12)"
TEMP_DB=""
QUARANTINE_DB=""
TEMP_DB_CREATED="false"
DATABASE_SWITCH_STARTED="false"
DATABASE_SWITCHED="false"
RECOVERY_COMPLETED="false"

die() { printf 'RECOVERY_NO_GO|%s\n' "$*" >&2; exit 1; }
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
docker_restore() {
  bounded "$RESTORE_TIMEOUT_SECONDS" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"
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

while [[ $# -gt 0 ]]; do
  case "$1" in
    --execute) EXECUTE_COUNT=$((EXECUTE_COUNT + 1)) ;;
    --rc-manifest) shift; RC_MANIFEST="${1:-}" ;;
    --rc-manifest-sha256) shift; RC_MANIFEST_SHA256="${1:-}" ;;
    *) die "usage: $0 --execute --rc-manifest <absolute-json> --rc-manifest-sha256 <sha256>" ;;
  esac
  shift
done

[[ "$EXECUTE_COUNT" -eq 1 ]] || die "exactly one --execute is required"
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

RC_STATUS="${rc[0]}"; RC_ID="${rc[1]}"; SOURCE_SHA="${rc[2]}"; PREVIOUS_SHA="${rc[4]}"; CONTROL_SHA="${rc[5]}"
PREVIOUS_RC_FILE="${rc[6]}"; PREVIOUS_RC_DIGEST="${rc[7]}"; POSTGRES_IMAGE_ID="${rc[8]}"
BACKEND_ID="${rc[10]}"; FRONTEND_ID="${rc[12]}"; ROLLBACK_BACKEND_ID="${rc[13]}"; ROLLBACK_FRONTEND_ID="${rc[14]}"
TOOLING_SHA="${rc[16]}"; PROMOTION_DIGEST="${rc[17]}"; PROMOTION_OVERRIDE_DIGEST="${rc[18]}"
RECOVERY_DIGEST="${rc[19]}"; RECOVERY_OVERRIDE_DIGEST="${rc[20]}"; REHEARSAL_DIGEST="${rc[21]}"
SMOKE_DIGEST="${rc[22]}"; DATA_CONTRACT_DIGEST="${rc[23]}"; EVIDENCE_HELPER_DIGEST="${rc[24]}"; FLYWAY_DIGEST="${rc[25]}"; BACKUP_HELPER_DIGEST="${rc[26]}"
STAGING_EVIDENCE_FILE="${rc[27]}"; STAGING_DIGEST="${rc[28]}"; STAGING_REPAIR_FILE="${rc[29]}"; STAGING_REPAIR_DIGEST="${rc[30]}"
BACKUP_FILE="${rc[31]}"; BACKUP_DIGEST="${rc[32]}"; REHEARSAL_EVIDENCE_FILE="${rc[33]}"; REHEARSAL_EVIDENCE_DIGEST="${rc[34]}"
EXPECTED_BUSINESS="${rc[35]}"; EXPECTED_PRINTING="${rc[36]}"; BACKUP_TARGET="${rc[37]}"; FLYWAY_TARGET="${rc[38]}"

[[ "$RC_STATUS" == "RC_FROZEN" ]] || die "recovery requires a frozen RC"
[[ "${rc[3]}" == "PASS" && "$BACKUP_TARGET" == "V10" && "$FLYWAY_TARGET" == "V26" ]] || die "RC release boundary differs"
for index in 39 40 41 42 43 44 45 46 47 48 51; do [[ "${rc[$index]}" == "PASS" ]] || die "RC automated gate type/value differs"; done
[[ "${rc[49]}" == "VERIFIED" ]] || die "Staging artifact gate must be VERIFIED"
[[ "${rc[50]}" == "ACCEPT" ]] || die "Agent 6 release gate must be ACCEPT"
[[ "$SOURCE_SHA" =~ ^[0-9a-f]{40}$ && "$PREVIOUS_SHA" =~ ^[0-9a-f]{40}$ && "$CONTROL_SHA" =~ ^[0-9a-f]{40}$ && "$TOOLING_SHA" =~ ^[0-9a-f]{40}$ ]] || die "RC Git identity is invalid"
for image in "$POSTGRES_IMAGE_ID" "$BACKEND_ID" "$FRONTEND_ID" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID"; do [[ "$image" =~ ^sha256:[0-9a-f]{64}$ ]] || die "RC image identity is invalid"; done
for value in "$PREVIOUS_RC_DIGEST" "$PROMOTION_DIGEST" "$PROMOTION_OVERRIDE_DIGEST" "$RECOVERY_DIGEST" "$RECOVERY_OVERRIDE_DIGEST" "$REHEARSAL_DIGEST" "$SMOKE_DIGEST" "$DATA_CONTRACT_DIGEST" "$EVIDENCE_HELPER_DIGEST" "$FLYWAY_DIGEST" "$BACKUP_HELPER_DIGEST" "$STAGING_DIGEST" "$STAGING_REPAIR_DIGEST" "$BACKUP_DIGEST" "$REHEARSAL_EVIDENCE_DIGEST" "$EXPECTED_BUSINESS" "$EXPECTED_PRINTING"; do [[ "$value" =~ ^[0-9a-f]{64}$ ]] || die "RC digest/fingerprint is invalid"; done

for required in "$BASE_COMPOSE" "$RECOVERY_OVERRIDE" "$ENV_FILE" "$FLYWAY_MANIFEST" "$SMOKE_HELPER" "$DATA_CONTRACT" "$EVIDENCE_HELPER" "$BACKUP_FILE" "$REHEARSAL_EVIDENCE_FILE" "$PREVIOUS_RC_FILE" "$STAGING_EVIDENCE_FILE" "$STAGING_REPAIR_FILE"; do require_real_path "$required" file "$(id -u)"; done
require_real_path "$EXPECTED_CONTROL_ROOT" control-root "$(id -u)"
require_real_path "$EXPECTED_POSTGRES_DATA_DIR" postgres-root 70
require_real_path "$EXPECTED_NGINX_TEMPLATE" nginx-template "$(id -u)"
require_real_path "$EXPECTED_CERTBOT_WWW_DIR" certbot-root "$(id -u)"
require_real_path "$EXPECTED_LETSENCRYPT_DIR" letsencrypt-root "$(id -u)"
EXPECTED_PREVIOUS_RC="$(realpath "$SCRIPT_DIR/../../docs/archive/governance-pre-simplification/runtime/RC_THREE_RELIABILITY_20260812_3EC4D88.json")"
EXPECTED_STAGING_EVIDENCE="$(realpath "$SCRIPT_DIR/../../docs/governance/PHASE_B_PART2_PRODUCT_FLOW_STAGING_ACCEPTANCE_EVIDENCE.md")"
EXPECTED_STAGING_REPAIR="$(realpath "$SCRIPT_DIR/../../docs/governance/PHASE_B_PART2_P0_P1_REPAIR_STAGING_EVIDENCE.md")"
[[ "$PREVIOUS_RC_FILE" == "$EXPECTED_PREVIOUS_RC" ]] || die "previous Production RC path differs"
[[ "$STAGING_EVIDENCE_FILE" == "$EXPECTED_STAGING_EVIDENCE" && "$STAGING_REPAIR_FILE" == "$EXPECTED_STAGING_REPAIR" ]] || die "Staging evidence path differs"
[[ "$BACKUP_FILE" == "$EXPECTED_CONTROL_ROOT/backups/"* && "$(dirname "$BACKUP_FILE")" == "$EXPECTED_CONTROL_ROOT/backups" ]] || die "fresh backup path differs"
[[ "$REHEARSAL_EVIDENCE_FILE" == /srv/restaurant-pos/production-tools/evidence/* && "$(dirname "$REHEARSAL_EVIDENCE_FILE")" == "/srv/restaurant-pos/production-tools/evidence" ]] || die "rehearsal evidence path differs"
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" && "$(stat -c '%a' "$BACKUP_FILE")" == "600" && "$(stat -c '%a' "$REHEARSAL_EVIDENCE_FILE")" == "600" ]] || die "credential/evidence file mode differs"
[[ "$(digest "$PREVIOUS_RC_FILE")" == "$PREVIOUS_RC_DIGEST" && "$(digest "$STAGING_EVIDENCE_FILE")" == "$STAGING_DIGEST" && "$(digest "$STAGING_REPAIR_FILE")" == "$STAGING_REPAIR_DIGEST" && "$(digest "$BACKUP_FILE")" == "$BACKUP_DIGEST" && "$(digest "$REHEARSAL_EVIDENCE_FILE")" == "$REHEARSAL_EVIDENCE_DIGEST" ]] || die "RC evidence/backup digest differs"
[[ "$(digest "${BASH_SOURCE[0]}")" == "$RECOVERY_DIGEST" && "$(digest "$RECOVERY_OVERRIDE")" == "$RECOVERY_OVERRIDE_DIGEST" ]] || die "recovery tooling digest differs"
[[ "$(digest "$SCRIPT_DIR/production-v26-exact-artifact-promote.sh")" == "$PROMOTION_DIGEST" && "$(digest "$SCRIPT_DIR/docker-compose.production-v26-promotion.yml")" == "$PROMOTION_OVERRIDE_DIGEST" ]] || die "promotion tooling digest differs"
[[ "$(digest "$SCRIPT_DIR/production-v10-v26-rehearsal.sh")" == "$REHEARSAL_DIGEST" && "$(digest "$SMOKE_HELPER")" == "$SMOKE_DIGEST" && "$(digest "$DATA_CONTRACT")" == "$DATA_CONTRACT_DIGEST" && "$(digest "$EVIDENCE_HELPER")" == "$EVIDENCE_HELPER_DIGEST" ]] || die "rehearsal/data tooling digest differs"
[[ "$(digest "$FLYWAY_MANIFEST")" == "$FLYWAY_DIGEST" && "$(digest "$SCRIPT_DIR/production-backup-rehearsal.sh")" == "$BACKUP_HELPER_DIGEST" ]] || die "Flyway/backup tooling digest differs"
bounded 60 env -i PATH="$SAFE_PATH" python3 -I "$EVIDENCE_HELPER" --scope full \
  --staging-full "$STAGING_EVIDENCE_FILE" --staging-repair "$STAGING_REPAIR_FILE" \
  --rehearsal "$REHEARSAL_EVIDENCE_FILE" --source-sha "$SOURCE_SHA" \
  --backend-image-id "$BACKEND_ID" --frontend-image-id "$FRONTEND_ID" \
  --backup-sha256 "$BACKUP_DIGEST" --recovery-helper-sha256 "$RECOVERY_DIGEST" \
  --business-fingerprint "$EXPECTED_BUSINESS" --printing-fingerprint "$EXPECTED_PRINTING" \
  >/dev/null || die "release evidence contract differs"

env -i PATH="$SAFE_PATH" python3 -I - "$PREVIOUS_RC_FILE" "$PREVIOUS_SHA" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID" <<'PY' || die "previous Production RC does not bind the running rollback artifacts"
import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
assert d.get('status')=='RC_FROZEN'
assert d.get('source_sha')==sys.argv[2]
assert d.get('backend_image_id')==sys.argv[3]
assert d.get('frontend_image_id')==sys.argv[4]
PY

[[ "$(git -C "$SCRIPT_DIR/../.." rev-parse HEAD)" == "$TOOLING_SHA" && -z "$(git -C "$SCRIPT_DIR/../.." status --porcelain)" ]] || die "tooling checkout identity differs"
control_checkout_is_release_safe "$CONTROL_SHA" || die "Production control checkout identity differs"
[[ -f "$OPS_LOCK" && ! -L "$OPS_LOCK" && "$(stat -c '%a|%u' "$OPS_LOCK")" == "600|$(id -u)" ]] || die "Production ops lock identity differs"
exec 9<>"$OPS_LOCK"; flock -n 9 || die "another Production operation holds the lock"

DB_ID="$(exact_container_id cloud-db-1)" || die "cannot query canonical Production DB container"
[[ -n "$DB_ID" && "$(docker_default inspect --format '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.Image}}|{{.State.Health.Status}}' "$DB_ID")" == "$EXPECTED_PROJECT|db|$POSTGRES_IMAGE_ID|healthy" ]] || die "Production DB identity/health differs"
[[ "$(docker_default inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Type}}|{{.Source}}|{{.RW}}{{end}}{{end}}' "$DB_ID")" == "bind|$EXPECTED_POSTGRES_DATA_DIR|true" ]] || die "Production DB mount differs"

validate_project_inventory() {
  local project_inventory resource_name resource_service resource_id
  local db_resources=0 backend_resources=0 nginx_resources=0
  project_inventory="$(docker_default ps -a --no-trunc --filter "label=com.docker.compose.project=$EXPECTED_PROJECT" --format '{{.Names}}|{{.Label "com.docker.compose.service"}}|{{.ID}}')" || die "cannot enumerate complete Production project inventory"
  while IFS='|' read -r resource_name resource_service resource_id; do
    [[ -n "$resource_name" ]] || continue
    [[ "$resource_id" =~ ^[0-9a-f]{64}$ ]] || die "Production project resource ID differs"
    case "$resource_service" in
      db)
        [[ "$resource_name" == "cloud-db-1" && "$resource_id" == "$DB_ID" ]] || die "canonical Production DB resource differs"
        db_resources=$((db_resources + 1))
        ;;
      backend)
        [[ "$resource_name" == "cloud-backend-1" ]] || die "Production backend resource name differs"
        backend_resources=$((backend_resources + 1))
        ;;
      nginx)
        [[ "$resource_name" == "cloud-nginx-1" ]] || die "Production frontend resource name differs"
        nginx_resources=$((nginx_resources + 1))
        ;;
      *) die "unknown Production project service/resource exists" ;;
    esac
  done <<<"$project_inventory"
  [[ "$db_resources" -eq 1 && "$backend_resources" -le 1 && "$nginx_resources" -le 1 ]] || die "Production project resource cardinality differs"
}
validate_project_inventory

compose_env=(PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" PRODUCTION_POSTGRES_DATA_DIR="$EXPECTED_POSTGRES_DATA_DIR" PRODUCTION_NGINX_TEMPLATE="$EXPECTED_NGINX_TEMPLATE" PRODUCTION_CERTBOT_WWW_DIR="$EXPECTED_CERTBOT_WWW_DIR" PRODUCTION_LETSENCRYPT_DIR="$EXPECTED_LETSENCRYPT_DIR" RECOVERY_BACKEND_IMAGE="$ROLLBACK_BACKEND_ID" RECOVERY_FRONTEND_IMAGE="$ROLLBACK_FRONTEND_ID")
compose=(docker --context default compose --project-name "$EXPECTED_PROJECT" --env-file "$ENV_FILE" -f "$BASE_COMPOSE" -f "$RECOVERY_OVERRIDE")
resolved_json="$(bounded "$COMPOSE_TIMEOUT_SECONDS" env -i "${compose_env[@]}" "${compose[@]}" config --format json)"
runtime_output="$(env -i PATH="$SAFE_PATH" python3 -I -c '
import json,pathlib,re,sys
env_path,postgres_root,backend_image,frontend_image=sys.argv[1:]
required=("DB_NAME","DB_USER","JWT_SECRET")
seen={key:0 for key in required}
for raw in pathlib.Path(env_path).read_text(encoding="utf-8").splitlines():
    match=re.match(r"^[ \t]*(?:export[ \t]+)?([A-Za-z_][A-Za-z0-9_]*)[ \t]*=",raw)
    if match and match.group(1) in seen: seen[match.group(1)]+=1
assert all(seen[key]==1 for key in required)
d=json.load(sys.stdin)
assert set(d["services"])=={"db","backend","nginx"}
db=d["services"]["db"]; backend=d["services"]["backend"]; nginx=d["services"]["nginx"]
assert backend["image"]==backend_image and str(backend["environment"]["FLYWAY_TARGET"])=="10"
assert nginx["image"]==frontend_image
mounts={(v["source"],v["target"],bool(v.get("read_only",False))) for v in db.get("volumes",[])}
assert mounts=={(postgres_root,"/var/lib/postgresql/data",False)}
values=(db["environment"]["POSTGRES_DB"],db["environment"]["POSTGRES_USER"],backend["environment"]["JWT_SECRET"])
assert backend["environment"]["DB_NAME"]==values[0] and backend["environment"]["DB_USER"]==values[1]
assert all(isinstance(value,str) and "\n" not in value and "\r" not in value for value in values)
print("\n".join(values))
' "$ENV_FILE" "$EXPECTED_POSTGRES_DATA_DIR" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID" <<<"$resolved_json")" || die "recovery Compose/dotenv runtime contract differs"
mapfile -t runtime_values <<<"$runtime_output"
unset runtime_output
[[ ${#runtime_values[@]} -eq 3 ]] || die "resolved recovery runtime field count differs"
POSTGRES_DB="${runtime_values[0]}"; POSTGRES_USER="${runtime_values[1]}"; JWT_SECRET="${runtime_values[2]}"
[[ "$POSTGRES_DB" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ && "$POSTGRES_USER" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ && ${#JWT_SECRET} -ge 32 ]] || die "resolved recovery DB/JWT contract is unsafe"
container_db_contract="$(docker_default exec "$DB_ID" sh -eu -c 'printf "%s|%s" "$POSTGRES_DB" "$POSTGRES_USER"')" || die "cannot read exact DB container runtime identity"
[[ "$container_db_contract" == "$POSTGRES_DB|$POSTGRES_USER" ]] || die "recovery Compose DB identity differs from running DB container"

TEMP_DB="v10_recovery_${RECOVERY_RUN_ID}"
QUARANTINE_DB="v26_quarantine_${RECOVERY_RUN_ID}"

RECOVERY_QUERY_DB="$POSTGRES_DB"
v26_db_query() {
  docker_default exec -i "$DB_ID" psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$RECOVERY_QUERY_DB"
}
# shellcheck disable=SC1090
source "$DATA_CONTRACT"

expected_ledger() {
  local target="$1"
  awk -F'|' -v target="$target" '/^[0-9]+[|]/ && ($1 + 0) <= (target + 0) {print $1 "|" $2 "|true|" $3}' "$FLYWAY_MANIFEST"
}
ledger_for_db() {
  docker_default exec "$DB_ID" psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$1" -c "select version || chr(124) || script || chr(124) || success::text || chr(124) || checksum from flyway_schema_history order by installed_rank"
}
version_for_db() {
  docker_default exec "$DB_ID" psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$1" -c "select max(version::integer) from flyway_schema_history where success"
}
database_exists() {
  [[ "$(docker_default exec "$DB_ID" psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d postgres -v "candidate=$1" -c "select count(*) from pg_database where datname = :'candidate'")" == "1" ]]
}
terminate_database_sessions() {
  docker_restore exec "$DB_ID" timeout -s TERM -k 10 120 psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d postgres -v "candidate=$1" -c "select pg_terminate_backend(pid) from pg_stat_activity where datname = :'candidate' and pid <> pg_backend_pid()" >/dev/null
}
rename_database() {
  local source="$1" target="$2"
  [[ "$source" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ && "$target" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || return 1
  docker_restore exec "$DB_ID" timeout -s TERM -k 10 120 psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d postgres -c "alter database \"$source\" rename to \"$target\"" >/dev/null
}
drop_exact_recovery_database() {
  local database="$1"
  [[ "$database" == "$TEMP_DB" || "$database" == "$QUARANTINE_DB" ]] || return 1
  docker_restore exec "$DB_ID" timeout -s TERM -k 10 120 dropdb --if-exists --force -U "$POSTGRES_USER" "$database"
}
cleanup_uncommitted_temp_database() {
  local original_status=$?
  trap - EXIT
  if [[ "$RECOVERY_COMPLETED" != "true" && "$TEMP_DB_CREATED" == "true" && "$DATABASE_SWITCH_STARTED" != "true" ]]; then
    if ! drop_exact_recovery_database "$TEMP_DB"; then
      printf 'RECOVERY_NO_GO|temporary recovery database cleanup failed; primary database was not replaced\n' >&2
      original_status=1
    fi
  fi
  exit "$original_status"
}
trap cleanup_uncommitted_temp_database EXIT

actual_ledger="$(ledger_for_db "$POSTGRES_DB")"
actual_version="$(version_for_db "$POSTGRES_DB")"
[[ "$actual_version" =~ ^(1[0-9]|2[0-6])$ && "$actual_ledger" == "$(expected_ledger "$actual_version")" ]] || die "failed Production Flyway state is not an exact V10-V26 prefix"
[[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "Production data changed outside the rehearsed migration contract"

optional_app_container() {
  local service="$1" name="$2" target_image="$3" rollback_image="$4" exact_id="" identity
  exact_id="$(exact_container_id "$name")" || return 1
  [[ -n "$exact_id" ]] || return 0
  identity="$(docker_default inspect --format '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.Image}}' "$exact_id")"
  [[ "$identity" == "$EXPECTED_PROJECT|$service|$target_image" || "$identity" == "$EXPECTED_PROJECT|$service|$rollback_image" ]] || return 1
  printf '%s' "$exact_id"
}

recovery_nginx_identity_allowed() {
  local identity="$1" expected_id="$2"
  if [[ -z "$identity" ]]; then
    [[ "$expected_id" == "ANY" || -z "$expected_id" ]]
    return
  fi
  local actual_id="${identity%%|*}" remainder="${identity#*|}"
  [[ "$expected_id" == "ANY" || "$actual_id" == "$expected_id" ]] || return 1
  [[ "$remainder" == "$EXPECTED_PROJECT|nginx|$ROLLBACK_FRONTEND_ID|false" ]]
}

recovery_nginx_container() {
  local expected_id="$1" exact_id identity
  exact_id="$(exact_container_id cloud-nginx-1)" || return 1
  if [[ -z "$exact_id" ]]; then
    recovery_nginx_identity_allowed "" "$expected_id" || return 1
    return 0
  fi
  identity="$(docker_default inspect --format '{{.Id}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.Image}}|{{.State.Running}}' "$exact_id")" || return 1
  recovery_nginx_identity_allowed "$identity" "$expected_id" || return 1
  printf '%s' "$exact_id"
}

FAILED_BACKEND_ID="$(optional_app_container backend cloud-backend-1 "$BACKEND_ID" "$ROLLBACK_BACKEND_ID")" || die "unexpected or ambiguous backend container in failed state"
FAILED_NGINX_ID="$(recovery_nginx_container ANY)" || die "recovery requires an absent or exact stopped rollback frontend"
[[ -z "$FAILED_BACKEND_ID" ]] || docker_default stop "$FAILED_BACKEND_ID" >/dev/null

# Freeze and revalidate the failed pre-edge database before constructing a
# separately restorable V10 candidate. The primary database is never dropped.
RECOVERY_QUERY_DB="$POSTGRES_DB"
actual_ledger="$(ledger_for_db "$POSTGRES_DB")"
actual_version="$(version_for_db "$POSTGRES_DB")"
[[ "$actual_version" =~ ^(1[0-9]|2[0-6])$ && "$actual_ledger" == "$(expected_ledger "$actual_version")" ]] || die "stopped Production Flyway state drifted"
[[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "Production data changed while recovery entered maintenance"
! database_exists "$TEMP_DB" && ! database_exists "$QUARANTINE_DB" || die "generated recovery database identity already exists"

TEMP_DB_CREATED="true"
docker_restore exec "$DB_ID" timeout -s TERM -k 10 120 createdb -U "$POSTGRES_USER" -O "$POSTGRES_USER" "$TEMP_DB" || die "bounded temporary database creation failed"
docker_restore exec -i "$DB_ID" sh -eu -c 'timeout -s TERM -k 10 840 pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --no-privileges --exit-on-error --single-transaction' sh "$TEMP_DB" <"$BACKUP_FILE" || die "bounded V10 temporary restore failed; primary database remains intact"
[[ "$(ledger_for_db "$TEMP_DB")" == "$(expected_ledger 10)" ]] || die "temporary recovered Flyway ledger is not exact V10"
RECOVERY_QUERY_DB="$TEMP_DB"
[[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "temporary recovered V10 fingerprint differs from frozen RC"
printf 'RECOVERY_TEMP_RESTORE|flyway=V10-exact|fingerprints=verified|primary_untouched=true|result=PASS\n'

# Revalidate both databases immediately before the bounded name switch.
RECOVERY_QUERY_DB="$POSTGRES_DB"
[[ "$(ledger_for_db "$POSTGRES_DB")" == "$(expected_ledger "$actual_version")" ]] || die "primary Flyway changed before recovery switch"
[[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "primary data changed before recovery switch"
validate_project_inventory
db_id_before_switch="$(exact_container_id cloud-db-1)" || die "cannot query canonical Production DB before recovery switch"
[[ "$db_id_before_switch" == "$DB_ID" ]] || die "canonical Production DB changed before recovery switch"
backend_id_before_switch="$(optional_app_container backend cloud-backend-1 "$BACKEND_ID" "$ROLLBACK_BACKEND_ID")" || die "backend resource changed before recovery switch"
nginx_id_before_switch="$(recovery_nginx_container "$FAILED_NGINX_ID")" || die "frontend resource changed or became public before recovery switch"
[[ "$backend_id_before_switch" == "$FAILED_BACKEND_ID" && "$nginx_id_before_switch" == "$FAILED_NGINX_ID" ]] || die "Production application resource identity changed before recovery switch"
terminate_database_sessions "$POSTGRES_DB" || die "cannot terminate primary database sessions"
terminate_database_sessions "$TEMP_DB" || die "cannot terminate temporary database sessions"
DATABASE_SWITCH_STARTED="true"
if ! rename_database "$POSTGRES_DB" "$QUARANTINE_DB"; then
  DATABASE_SWITCH_STARTED="false"
  die "cannot quarantine failed Production database"
fi
if ! rename_database "$TEMP_DB" "$POSTGRES_DB"; then
  if rename_database "$QUARANTINE_DB" "$POSTGRES_DB"; then
    DATABASE_SWITCH_STARTED="false"
    die "temporary database switch failed; original database name was restored"
  fi
  die "temporary database switch and immediate name rollback both failed; preserved databases require Owner recovery"
fi
DATABASE_SWITCHED="true"
TEMP_DB_CREATED="false"
RECOVERY_QUERY_DB="$POSTGRES_DB"
[[ "$(ledger_for_db "$POSTGRES_DB")" == "$(expected_ledger 10)" ]] || die "switched recovery database is not exact V10"
[[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "switched recovery database fingerprint differs"

bounded "$COMPOSE_TIMEOUT_SECONDS" env -i "${compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never backend

backend_private_ip() {
  local identity ip
  identity="$(docker_default inspect --format '{{len .NetworkSettings.Networks}}|{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' cloud-backend-1)"
  [[ "$identity" == 1\|* ]] || return 1
  ip="${identity#*|}"
  [[ "$ip" =~ ^10\.|^172\.(1[6-9]|2[0-9]|3[01])\.|^192\.168\. ]] || return 1
  printf '%s' "$ip"
}
deadline=$((SECONDS + 120))
while (( SECONDS < deadline )); do
  backend_ip="$(backend_private_ip 2>/dev/null || true)"
  body=""
  if [[ -n "$backend_ip" ]]; then
    body="$(curl --noproxy '*' --connect-timeout 2 --max-time 5 -fsS "http://$backend_ip:8080/api/v1/system/health" 2>/dev/null || true)"
  fi
  [[ "$body" == *'"status":"UP"'* ]] && break
  sleep 1
done
[[ "$body" == *'"status":"UP"'* && "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID" ]] || die "recovered V10 backend is not healthy"
bounded "$COMPOSE_TIMEOUT_SECONDS" env -i "${compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never nginx
[[ "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$ROLLBACK_FRONTEND_ID" ]] || die "recovered frontend image differs"
[[ "$(curl --noproxy '*' --connect-timeout 2 --max-time 10 -sS -o /dev/null -w '%{http_code}' http://127.0.0.1/)" == "200" ]] || die "recovered public frontend is unavailable"
bounded 180 env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" JWT_SECRET="$JWT_SECRET" python3 -I "$SMOKE_HELPER" --base-url http://127.0.0.1 --db-container cloud-db-1 --mode legacy-read
db_id_after_recovery="$(exact_container_id cloud-db-1)" || die "cannot query Production DB after recovery"
[[ "$db_id_after_recovery" == "$DB_ID" ]] || die "Production DB container changed during recovery"
[[ "$(v26_business_fingerprint)" == "$EXPECTED_BUSINESS" && "$(v26_printing_fingerprint)" == "$EXPECTED_PRINTING" ]] || die "recovered V10 content changed during verification"
terminate_database_sessions "$QUARANTINE_DB" || die "cannot prepare verified failed database quarantine for cleanup"
drop_exact_recovery_database "$QUARANTINE_DB" || die "verified failed database quarantine cleanup failed"
DATABASE_SWITCHED="false"
RECOVERY_COMPLETED="true"
printf 'RECOVERY|rc_id=%s|backup_sha256=%s|mode=validated-temp-db-switch|flyway=V10-exact|failed_database_preserved_until_verified=true|rollback_backend=%s|rollback_frontend=%s|db_container_unchanged=true|result=PASS\n' "$RC_ID" "$BACKUP_DIGEST" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID"
