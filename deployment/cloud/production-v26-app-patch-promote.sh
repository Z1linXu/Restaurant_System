#!/usr/bin/env bash
set -Eeuo pipefail

# Bounded same-schema V26 application patch promotion. This path never builds,
# pulls, migrates, or touches the Production database container. It promotes
# only the immutable backend/frontend image IDs already accepted on Staging.

readonly SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
readonly CONTROL_ROOT="/home/ubuntu/Restaurant_System/deployment/cloud"
readonly REPOSITORY_ROOT="/home/ubuntu/Restaurant_System"
readonly STAGING_ENV="/srv/restaurant-pos/staging/config/.env.staging"
readonly PROJECT="cloud"
readonly APP_NETWORK="cloud_restaurant-pos"
readonly LOCK="$CONTROL_ROOT/.production-ops.lock"
readonly SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BASE_COMPOSE="$CONTROL_ROOT/docker-compose.yml"
readonly OVERRIDE_COMPOSE="$SCRIPT_DIR/docker-compose.production-v26-promotion.yml"
readonly ENV_FILE="$CONTROL_ROOT/.env"
readonly FLYWAY_MANIFEST="$SCRIPT_DIR/ops001-flyway-v26-checksums.txt"
readonly DATA_CONTRACT="$SCRIPT_DIR/production-v26-data-contract.sh"
readonly SMOKE="$SCRIPT_DIR/production-v26-smoke.py"
readonly EVIDENCE_VALIDATOR="$SCRIPT_DIR/production-v26-app-patch-evidence.py"

ACTION="validate"
MANIFEST=""
MANIFEST_SHA256=""
MUTATION_STARTED="false"
COMPLETED="false"
DB_ID_BEFORE=""
ROLLBACK_BACKEND_ID=""
ROLLBACK_FRONTEND_ID=""
BACKEND_ID=""
FRONTEND_ID=""

die() { printf 'NO_GO|%s\n' "$*" >&2; exit 1; }
digest() { sha256sum "$1" | awk '{print $1}'; }
bounded() { local seconds="$1"; shift; timeout --foreground --kill-after=10s "${seconds}s" "$@"; }
docker_default() { bounded 180 env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"; }

path_has_symlink() {
  local path="$1" part current="" old_ifs="$IFS"
  IFS='/'; set -- $path; IFS="$old_ifs"
  for part in "$@"; do [[ -n "$part" ]] || continue; current="$current/$part"; [[ ! -L "$current" ]] || return 0; done
  return 1
}

require_private_file() {
  local path="$1"
  [[ "$path" == /* && -f "$path" && ! -L "$path" && "$(realpath "$path")" == "$path" ]] || die "private file path is unsafe"
  path_has_symlink "$path" && die "private file path traverses a symlink"
  [[ "$(stat -c '%u|%a' "$path")" == "$(id -u)|600" ]] || die "private file owner/mode differs"
}

exact_container_id() {
  local output
  output="$(docker_default ps -aq --no-trunc --filter "name=^/${1}$")" || return 1
  [[ -z "$output" || "$output" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf '%s' "$output"
}

usage() { printf 'Usage: %s --validate|--execute --manifest <absolute-json> --manifest-sha256 <sha256>\n' "$0"; }
while [[ $# -gt 0 ]]; do
  case "$1" in
    --validate|--execute) ACTION="${1#--}" ;;
    --manifest) shift; MANIFEST="${1:-}" ;;
    --manifest-sha256) shift; MANIFEST_SHA256="${1:-}" ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
  shift
done

command -v timeout >/dev/null || die "GNU timeout is required"
require_private_file "$MANIFEST"
[[ "$MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ && "$(digest "$MANIFEST")" == "$MANIFEST_SHA256" ]] || die "manifest digest mismatch"

manifest_output="$(env -i PATH="$SAFE_PATH" python3 -I - "$MANIFEST" <<'PY'
import json,sys
def unique(pairs):
    out={}
    for key,value in pairs:
        if key in out: raise ValueError("duplicate key")
        out[key]=value
    return out
keys=("status","source_sha","previous_source_sha","control_checkout_sha","postgres_image_id",
      "backend_image_tag","backend_image_id","frontend_image_tag","frontend_image_id",
      "rollback_backend_image_id","rollback_frontend_image_id","staging_acceptance_file",
      "staging_acceptance_sha256","staging_environment_sha256","staging_runtime_preflight_sha256",
      "staging_owner_approval_sha256","acceptance_run_id","flyway_manifest_sha256",
      "promotion_helper_sha256","promotion_override_sha256","evidence_validator_sha256",
      "production_environment_sha256","target_resolved_compose_sha256","rollback_resolved_compose_sha256",
      "production_runtime_config_sha256","staging_acceptance_result","agent_6_review","production_preflight_result")
d=json.load(open(sys.argv[1],encoding="utf-8"),object_pairs_hook=unique)
if set(d)!=set(keys): raise SystemExit(2)
for key in keys:
    value=d[key]
    if not isinstance(value,str) or "\n" in value or "\r" in value: raise SystemExit(2)
    print(value)
PY
)" || die "manifest is invalid"
mapfile -t m <<<"$manifest_output"; unset manifest_output
[[ ${#m[@]} -eq 28 ]] || die "manifest field count differs"
STATUS="${m[0]}"; SOURCE_SHA="${m[1]}"; PREVIOUS_SHA="${m[2]}"; CONTROL_SHA="${m[3]}"; POSTGRES_ID="${m[4]}"
BACKEND_TAG="${m[5]}"; BACKEND_ID="${m[6]}"; FRONTEND_TAG="${m[7]}"; FRONTEND_ID="${m[8]}"
ROLLBACK_BACKEND_ID="${m[9]}"; ROLLBACK_FRONTEND_ID="${m[10]}"; ACCEPTANCE_FILE="${m[11]}"; ACCEPTANCE_DIGEST="${m[12]}"
STAGING_ENV_DIGEST="${m[13]}"; STAGING_PREFLIGHT_DIGEST="${m[14]}"; STAGING_APPROVAL_DIGEST="${m[15]}"; ACCEPTANCE_RUN_ID="${m[16]}"
FLYWAY_DIGEST="${m[17]}"; HELPER_DIGEST="${m[18]}"; OVERRIDE_DIGEST="${m[19]}"; EVIDENCE_VALIDATOR_DIGEST="${m[20]}"
PRODUCTION_ENV_DIGEST="${m[21]}"; TARGET_COMPOSE_DIGEST="${m[22]}"; ROLLBACK_COMPOSE_DIGEST="${m[23]}"; RUNTIME_CONFIG_DIGEST="${m[24]}"

[[ "$STATUS" == "V26_APP_PATCH_FROZEN" ]] || die "manifest status differs"
for sha in "$SOURCE_SHA" "$PREVIOUS_SHA" "$CONTROL_SHA"; do [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || die "Git identity is invalid"; done
for image in "$POSTGRES_ID" "$BACKEND_ID" "$FRONTEND_ID" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID"; do [[ "$image" =~ ^sha256:[0-9a-f]{64}$ ]] || die "image identity is invalid"; done
for value in "$ACCEPTANCE_DIGEST" "$STAGING_ENV_DIGEST" "$STAGING_PREFLIGHT_DIGEST" "$STAGING_APPROVAL_DIGEST" "$FLYWAY_DIGEST" "$HELPER_DIGEST" "$OVERRIDE_DIGEST" "$EVIDENCE_VALIDATOR_DIGEST"; do [[ "$value" =~ ^[0-9a-f]{64}$ ]] || die "digest identity is invalid"; done
for value in "$PRODUCTION_ENV_DIGEST" "$TARGET_COMPOSE_DIGEST" "$ROLLBACK_COMPOSE_DIGEST" "$RUNTIME_CONFIG_DIGEST"; do if [[ "$ACTION" == "validate" && "$value" == "PENDING" ]]; then continue; fi; [[ "$value" =~ ^[0-9a-f]{64}$ ]] || die "Production snapshot digest is invalid"; done
[[ "$ACCEPTANCE_RUN_ID" =~ ^[0-9a-f]{32}$ ]] || die "acceptance run ID is invalid"
[[ "$BACKEND_TAG" == *"$SOURCE_SHA" && "$FRONTEND_TAG" == *"$SOURCE_SHA" ]] || die "target image tags are not exact-SHA-bound"
[[ "${m[25]}" == "PASS" && "${m[26]}" == "ACCEPT" ]] || die "Staging acceptance or Agent 6 gate differs"
if [[ "$ACTION" == "execute" ]]; then [[ "${m[27]}" == "PASS" ]] || die "Production preflight gate is not PASS"; else [[ "${m[27]}" == "PENDING" || "${m[27]}" == "PASS" ]] || die "Production preflight gate differs"; fi

for path in "$BASE_COMPOSE" "$OVERRIDE_COMPOSE" "$ENV_FILE" "$STAGING_ENV" "$FLYWAY_MANIFEST" "$DATA_CONTRACT" "$SMOKE" "$EVIDENCE_VALIDATOR"; do [[ -f "$path" && ! -L "$path" ]] || die "required control file is missing or symlinked"; done
[[ "$(realpath "$SCRIPT_DIR")" == "$CONTROL_ROOT" && "$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)" == "$CONTROL_SHA" && "$CONTROL_SHA" == "$SOURCE_SHA" ]] || die "Production control checkout is not the exact accepted SHA"
[[ -z "$(git -C "$REPOSITORY_ROOT" status --porcelain --untracked-files=normal | grep -Ev '^\?\? deployment/cloud/(\.production-ops\.lock|backups/|bootstrap-admin\.env|data/|old-store-config\.(dump|sql))' || true)" ]] || die "Production control checkout is dirty"
[[ "$(digest "${BASH_SOURCE[0]}")" == "$HELPER_DIGEST" && "$(digest "$OVERRIDE_COMPOSE")" == "$OVERRIDE_DIGEST" && "$(digest "$FLYWAY_MANIFEST")" == "$FLYWAY_DIGEST" && "$(digest "$EVIDENCE_VALIDATOR")" == "$EVIDENCE_VALIDATOR_DIGEST" ]] || die "reviewed tooling digest differs"
git -C "$REPOSITORY_ROOT" merge-base --is-ancestor "$PREVIOUS_SHA" "$SOURCE_SHA" || die "target SHA does not descend from current Production authority"

require_private_file "$ACCEPTANCE_FILE"
[[ "$(digest "$ACCEPTANCE_FILE")" == "$ACCEPTANCE_DIGEST" ]] || die "Staging acceptance digest differs"
bounded 30 env -i PATH="$SAFE_PATH" python3 -I "$EVIDENCE_VALIDATOR" --acceptance "$ACCEPTANCE_FILE" --source-sha "$SOURCE_SHA" --backend-image-id "$BACKEND_ID" --frontend-image-id "$FRONTEND_ID" --environment-sha256 "$STAGING_ENV_DIGEST" --runtime-preflight-sha256 "$STAGING_PREFLIGHT_DIGEST" --owner-approval-sha256 "$STAGING_APPROVAL_DIGEST" --run-id "$ACCEPTANCE_RUN_ID" >/dev/null || die "typed business Store acceptance evidence differs"
grep -Fxq "STAGING_COMMIT_SHA=$SOURCE_SHA" "$STAGING_ENV" || die "current Staging SHA differs"
[[ "$(digest "$STAGING_ENV")" == "$STAGING_ENV_DIGEST" ]] || die "Staging environment changed after acceptance"
[[ "$(docker_default inspect --format '{{.Image}}' restaurant-pos-staging-backend-1)" == "$BACKEND_ID" && "$(docker_default inspect --format '{{.Image}}' restaurant-pos-staging-nginx-1)" == "$FRONTEND_ID" ]] || die "current Staging runtime does not use accepted images"
[[ "$(docker_default image inspect --format '{{.Id}}' "$BACKEND_TAG")" == "$BACKEND_ID" && "$(docker_default image inspect --format '{{.Id}}' "$FRONTEND_TAG")" == "$FRONTEND_ID" ]] || die "exact-SHA image tags differ from immutable IDs"

[[ -f "$LOCK" && ! -L "$LOCK" && "$(stat -c '%u|%a' "$LOCK")" == "$(id -u)|600" ]] || die "Production lock identity differs"
exec 9<>"$LOCK"; flock -n 9 || die "another Production operation holds the lock"

DB_ID_BEFORE="$(exact_container_id cloud-db-1)"; [[ -n "$DB_ID_BEFORE" ]] || die "Production DB container is missing"
[[ "$(docker_default inspect --format '{{.Image}}|{{.State.Health.Status}}' "$DB_ID_BEFORE")" == "$POSTGRES_ID|healthy" ]] || die "Production DB identity or health differs"
[[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID" && "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$ROLLBACK_FRONTEND_ID" ]] || die "current Production app images differ from rollback binding"
[[ "$(docker_default inspect --format '{{len .NetworkSettings.Networks}}|{{range $name, $config := .NetworkSettings.Networks}}{{$name}}{{end}}' cloud-backend-1)" == "1|$APP_NETWORK" ]] || die "Production backend network identity differs"

expected_ledger() { awk -F'|' '/^[0-9]+[|]/ && ($1 + 0) <= 26 {print $1 "|" $2 "|true|" $3}' "$FLYWAY_MANIFEST"; }
flyway_rows() { docker_default exec "$DB_ID_BEFORE" sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version || chr(124) || script || chr(124) || success::text || chr(124) || checksum from flyway_schema_history order by installed_rank"'; }
[[ "$(flyway_rows)" == "$(expected_ledger)" ]] || die "Production Flyway ledger is not exact V26"

backend_health() {
  local deadline=$((SECONDS + 120)) ip body
  while (( SECONDS < deadline )); do
    ip="$(docker_default inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' cloud-backend-1 2>/dev/null || true)"
    body="$(curl --noproxy '*' --connect-timeout 2 --max-time 5 -fsS "http://$ip:8080/api/v1/system/health" 2>/dev/null || true)"
    [[ "$body" == *'"status":"UP"'* ]] && return 0
    sleep 1
  done
  return 1
}
public_health() { [[ "$(curl --noproxy '*' --connect-timeout 2 --max-time 10 -sS -o /dev/null -w '%{http_code}' http://127.0.0.1/)" == "200" ]] && [[ "$(curl --noproxy '*' --connect-timeout 2 --max-time 10 -sS -o /dev/null -w '%{http_code}' http://127.0.0.1/ws/info)" == "200" ]]; }
backend_health || die "current Production backend health differs"
public_health || die "current Production public health differs"

ACTUAL_PRODUCTION_ENV_DIGEST="$(digest "$ENV_FILE")"
[[ "$PRODUCTION_ENV_DIGEST" == "PENDING" || "$ACTUAL_PRODUCTION_ENV_DIGEST" == "$PRODUCTION_ENV_DIGEST" ]] || die "Production environment digest differs from frozen preflight"
base_compose_env=(PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" PRODUCTION_POSTGRES_DATA_DIR="$CONTROL_ROOT/data/postgres" PRODUCTION_NGINX_TEMPLATE="$CONTROL_ROOT/data/nginx/default.conf.template" PRODUCTION_CERTBOT_WWW_DIR="$CONTROL_ROOT/data/certbot-www" PRODUCTION_LETSENCRYPT_DIR="$CONTROL_ROOT/data/letsencrypt")
compose_env=("${base_compose_env[@]}" PROMOTION_BACKEND_IMAGE="$BACKEND_ID" PROMOTION_FRONTEND_IMAGE="$FRONTEND_ID")
rollback_compose_env=("${base_compose_env[@]}" PROMOTION_BACKEND_IMAGE="$ROLLBACK_BACKEND_ID" PROMOTION_FRONTEND_IMAGE="$ROLLBACK_FRONTEND_ID")
compose=(docker --context default compose --project-name "$PROJECT" --env-file "$ENV_FILE" -f "$BASE_COMPOSE" -f "$OVERRIDE_COMPOSE")
RUNTIME_ROOT="$(mktemp -d /tmp/restaurant-v26-app-patch.XXXXXX)"; chmod 700 "$RUNTIME_ROOT"
cleanup_runtime() { if [[ -n "${RUNTIME_ROOT:-}" && "$RUNTIME_ROOT" == /tmp/restaurant-v26-app-patch.* && -d "$RUNTIME_ROOT" ]]; then rm -f "$RUNTIME_ROOT/target.json" "$RUNTIME_ROOT/rollback.json" "$RUNTIME_ROOT/inspect.json"; rmdir "$RUNTIME_ROOT"; fi; RUNTIME_ROOT=""; }
trap cleanup_runtime ERR INT TERM
bounded 240 env -i "${compose_env[@]}" "${compose[@]}" config --format json >"$RUNTIME_ROOT/target.json" || die "target Production Compose cannot resolve"
bounded 240 env -i "${rollback_compose_env[@]}" "${compose[@]}" config --format json >"$RUNTIME_ROOT/rollback.json" || die "rollback Production Compose cannot resolve"
docker_default inspect cloud-backend-1 cloud-nginx-1 >"$RUNTIME_ROOT/inspect.json" || die "current Production runtime cannot be inspected"
chmod 600 "$RUNTIME_ROOT"/*.json
ACTUAL_TARGET_COMPOSE_DIGEST="$(digest "$RUNTIME_ROOT/target.json")"; ACTUAL_ROLLBACK_COMPOSE_DIGEST="$(digest "$RUNTIME_ROOT/rollback.json")"
[[ "$TARGET_COMPOSE_DIGEST" == "PENDING" || "$ACTUAL_TARGET_COMPOSE_DIGEST" == "$TARGET_COMPOSE_DIGEST" ]] || die "target resolved Production Compose digest differs from frozen preflight"
[[ "$ROLLBACK_COMPOSE_DIGEST" == "PENDING" || "$ACTUAL_ROLLBACK_COMPOSE_DIGEST" == "$ROLLBACK_COMPOSE_DIGEST" ]] || die "rollback resolved Production Compose digest differs from frozen preflight"
runtime_values="$(env -i PATH="$SAFE_PATH" python3 -I - "$RUNTIME_ROOT/target.json" "$RUNTIME_ROOT/rollback.json" "$RUNTIME_ROOT/inspect.json" "$BACKEND_ID" "$FRONTEND_ID" "$ROLLBACK_BACKEND_ID" "$ROLLBACK_FRONTEND_ID" <<'PY'
import hashlib,json,sys
target=json.load(open(sys.argv[1],encoding="utf-8")); rollback=json.load(open(sys.argv[2],encoding="utf-8")); actual=json.load(open(sys.argv[3],encoding="utf-8"))
assert set(target["services"])==set(rollback["services"])=={"db","backend","nginx"}
assert target["services"]["backend"]["image"]==sys.argv[4] and target["services"]["nginx"]["image"]==sys.argv[5]
assert rollback["services"]["backend"]["image"]==sys.argv[6] and rollback["services"]["nginx"]["image"]==sys.argv[7]
def without_app_images(value):
    value=json.loads(json.dumps(value))
    value["services"]["backend"]["image"]="<APP_IMAGE>"; value["services"]["nginx"]["image"]="<APP_IMAGE>"
    return value
assert without_app_images(target)==without_app_images(rollback)
services={item["Name"].lstrip("/"):item for item in actual}; assert set(services)=={"cloud-backend-1","cloud-nginx-1"}
def env_map(item): return dict(entry.split("=",1) for entry in item["Config"]["Env"] if "=" in entry)
backend_actual=env_map(services["cloud-backend-1"]); nginx_actual=env_map(services["cloud-nginx-1"])
backend_expected={k:str(v) for k,v in rollback["services"]["backend"]["environment"].items()}
nginx_expected={k:str(v) for k,v in rollback["services"]["nginx"]["environment"].items()}
backend_keys=("SPRING_PROFILES_ACTIVE","SERVER_PORT","DB_HOST","DB_PORT","DB_NAME","DB_USER","DB_PASSWORD","JWT_SECRET","JAVA_OPTS","TZ","APP_ENVIRONMENT","FLYWAY_TARGET")
nginx_keys=("DOMAIN","NGINX_SERVER_NAME","TZ")
legacy_optional_key="APP_ENVIRONMENT"
assert backend_expected.get(legacy_optional_key)=="production"
assert backend_actual.get(legacy_optional_key) in (None,"production")
assert all(backend_actual.get(k)==backend_expected.get(k) for k in backend_keys if k!=legacy_optional_key)
assert all(nginx_actual.get(k)==nginx_expected.get(k) for k in nginx_keys)
normalized_backend={k:(backend_expected[k] if k==legacy_optional_key else backend_actual[k]) for k in backend_keys}
runtime={"backend":normalized_backend,"nginx":{k:nginx_actual[k] for k in nginx_keys},"backend_mounts":services["cloud-backend-1"]["Mounts"],"nginx_mounts":services["cloud-nginx-1"]["Mounts"]}
fingerprint=hashlib.sha256(json.dumps(runtime,sort_keys=True,separators=(",",":"),default=str).encode()).hexdigest()
print(backend_actual["JWT_SECRET"]); print(fingerprint)
PY
)" || die "current runtime and target/rollback Compose config differ beyond immutable app images"
JWT_SECRET="${runtime_values%%$'\n'*}"; ACTUAL_RUNTIME_CONFIG_DIGEST="${runtime_values##*$'\n'}"; unset runtime_values
[[ "$RUNTIME_CONFIG_DIGEST" == "PENDING" || "$ACTUAL_RUNTIME_CONFIG_DIGEST" == "$RUNTIME_CONFIG_DIGEST" ]] || die "Production runtime config fingerprint differs from frozen preflight"
[[ ${#JWT_SECRET} -ge 32 ]] || die "resolved JWT contract is unsafe"

# shellcheck disable=SC1090
source "$DATA_CONTRACT"
v26_db_query() { docker_default exec -i "$DB_ID_BEFORE" sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"'; }
BEFORE_BUSINESS="$(v26_business_fingerprint)"; BEFORE_PRINTING="$(v26_printing_fingerprint)"

if [[ "$ACTION" == "validate" ]]; then
  cleanup_runtime
  printf 'PRODUCTION_V26_APP_PATCH_PREFLIGHT|source_sha=%s|flyway=V26-exact|db_unchanged=true|staging_artifact=VERIFIED|result=PASS\n' "$SOURCE_SHA"
  printf 'PRODUCTION_V26_APP_PATCH_SNAPSHOT|environment_sha256=%s|target_compose_sha256=%s|rollback_compose_sha256=%s|runtime_config_sha256=%s\n' "$ACTUAL_PRODUCTION_ENV_DIGEST" "$ACTUAL_TARGET_COMPOSE_DIGEST" "$ACTUAL_ROLLBACK_COMPOSE_DIGEST" "$ACTUAL_RUNTIME_CONFIG_DIGEST"
  exit 0
fi

rollback() {
  local status=$?
  trap - EXIT
  if [[ "$MUTATION_STARTED" == "true" && "$COMPLETED" != "true" ]]; then
    printf 'AUTO_ROLLBACK|source_sha=%s|status=STARTED\n' "$SOURCE_SHA" >&2
    if bounded 240 env -i "${rollback_compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never backend nginx >/dev/null && backend_health && public_health && [[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID" && "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$ROLLBACK_FRONTEND_ID" && "$(exact_container_id cloud-db-1)" == "$DB_ID_BEFORE" && "$(flyway_rows)" == "$(expected_ledger)" && "$(v26_business_fingerprint)" == "$BEFORE_BUSINESS" && "$(v26_printing_fingerprint)" == "$BEFORE_PRINTING" ]]; then
      printf 'AUTO_ROLLBACK|source_sha=%s|result=PASS|database_restore=not_required\n' "$SOURCE_SHA" >&2
    else
      printf 'AUTO_ROLLBACK|source_sha=%s|result=FAIL|STOP=OWNER_GATE\n' "$SOURCE_SHA" >&2
    fi
  fi
  cleanup_runtime || true
  exit "$status"
}
trap rollback EXIT

MUTATION_STARTED="true"
bounded 240 env -i "${compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never backend
[[ "$(docker_default inspect --format '{{.Image}}' cloud-backend-1)" == "$BACKEND_ID" ]] || die "promoted backend image differs"
backend_health || die "promoted backend did not become healthy"
[[ "$(flyway_rows)" == "$(expected_ledger)" ]] || die "Flyway ledger changed during app-only patch"
bounded 240 env -i "${compose_env[@]}" "${compose[@]}" up -d --no-deps --no-build --pull never nginx
[[ "$(docker_default inspect --format '{{.Image}}' cloud-nginx-1)" == "$FRONTEND_ID" ]] || die "promoted frontend image differs"
public_health || die "promoted public edge health differs"
bounded 180 env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" JWT_SECRET="$JWT_SECRET" python3 -I "$SMOKE" --base-url http://127.0.0.1 --db-container cloud-db-1 --mode read

DB_ID_AFTER="$(exact_container_id cloud-db-1)"
[[ "$DB_ID_AFTER" == "$DB_ID_BEFORE" && "$(flyway_rows)" == "$(expected_ledger)" ]] || die "Production DB identity or Flyway changed"
[[ "$(v26_business_fingerprint)" == "$BEFORE_BUSINESS" && "$(v26_printing_fingerprint)" == "$BEFORE_PRINTING" ]] || die "Production business data changed during app-only patch"
COMPLETED="true"; MUTATION_STARTED="false"
cleanup_runtime
printf 'PRODUCTION_V26_APP_PATCH|source_sha=%s|flyway=V26-exact|db_container_unchanged=true|same_staging_artifact=true|read_smoke=PASS|result=PASS\n' "$SOURCE_SHA"
