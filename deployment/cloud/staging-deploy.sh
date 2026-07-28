#!/usr/bin/env bash
set -euo pipefail

# This wrapper intentionally has no production fallback. It uses a standalone
# Compose file, a fixed project name, and a controlled process environment.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STAGING_COMPOSE_FILE="$SCRIPT_DIR/docker-compose.staging.yml"
ACTION="validate"
EXECUTE_START="false"
SOURCE_ENV_FILE=""
ORIGINAL_ENV_FILE=""
ORIGINAL_CONFIG_DIR=""
ACTIVE_ENV_FILE=""
EXPECTED_PROJECT="restaurant-pos-staging"
SERVER_STAGING_ROOT="/srv/restaurant-pos/staging"
LOCAL_VALIDATE_MODE="false"
SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
RESOLVED_CONFIG=""
ENV_SNAPSHOT=""
ENV_SNAPSHOT_DIGEST=""
APPROVED_SHA=""
PREFLIGHT_EVIDENCE=""
PREFLIGHT_EVIDENCE_SHA256=""

INTERPOLATION_KEYS="
DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG DOCKER_CERT_PATH DOCKER_TLS_VERIFY
DOCKER_API_VERSION DOCKER_DEFAULT_PLATFORM DOCKER_BUILDKIT
COMPOSE_FILE COMPOSE_PROJECT_NAME COMPOSE_ENV_FILES COMPOSE_PATH_SEPARATOR COMPOSE_PROFILES
STAGING_ROOT STAGING_COMMIT_SHA STAGING_POSTGRES_DATA_DIR
HTTP_BIND_ADDRESS HTTP_PORT NGINX_SERVER_NAME TZ POSTGRES_IMAGE_TAG
DB_NAME DB_USER DB_PASSWORD JWT_SECRET SPRING_PROFILES_ACTIVE JAVA_OPTS
BACKEND_IMAGE FRONTEND_IMAGE VITE_APP_BUILD_VERSION
STAGING_PRINT_MODE STAGING_PRINTING_FEATURE_ENABLED STAGING_PRINTER_ENDPOINT
STAGING_DB_CPU_LIMIT STAGING_DB_MEMORY_LIMIT
STAGING_BACKEND_CPU_LIMIT STAGING_BACKEND_MEMORY_LIMIT
STAGING_NGINX_CPU_LIMIT STAGING_NGINX_MEMORY_LIMIT
STAGING_LOG_MAX_SIZE STAGING_LOG_MAX_FILE
APP_FEATURES_PRINTING
APP_AUTH_X_USER_ID_FALLBACK_ENABLED APP_DEV_TOOLS_ROLE_SWITCHER_ENABLED
APP_SEED_DEFAULT_USERS_ENABLED APP_SEED_DEMO_DATA_ENABLED
"

usage() {
  cat <<'EOF'
Restaurant POS isolated staging deploy helper.

Usage:
  ./staging-deploy.sh --env-file /srv/restaurant-pos/staging/config/.env.staging [--validate|--dry-run]
  ./staging-deploy.sh --execute-start --approved-sha <full-40-character-sha> \
    --preflight-evidence /srv/restaurant-pos/staging/evidence/<approved-preflight-file> \
    --preflight-evidence-sha256 <owner-reviewed-evidence-sha256> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging
  ./staging-deploy.sh --help

The default action validates only. Building and starting require the explicit
--execute-start action plus an exact approved SHA and a passed preflight
evidence file. The helper never pulls images, restores data, runs Flyway clean,
or accepts production-like configuration.

Options:
  --env-file PATH  Required absolute staging environment file.
  --validate       Validate paths, guards, and resolved Compose only.
  --dry-run        Alias for --validate.
  --execute-start  Explicitly build and start after approved preflight evidence.
  --approved-sha   Exact full SHA that the Owner approved for this start.
  --preflight-evidence PATH
                    Existing passed STG-004 preflight evidence under Staging root.
  --preflight-evidence-sha256 SHA256
                    Owner-reviewed SHA-256 of the exact evidence file.
  --local-validate Allow a non-/srv temporary root for local validation only.
  --help           Print this help text only.
EOF
}

die() {
  echo "staging guard: $*" >&2
  exit 1
}

cleanup() {
  [[ -n "$RESOLVED_CONFIG" ]] && rm -f "$RESOLVED_CONFIG"
  [[ -n "$ENV_SNAPSHOT" ]] && rm -f "$ENV_SNAPSHOT"
  return 0
}
canonical_dir() {
  (cd -P -- "$1" 2>/dev/null && pwd)
}

canonical_file() {
  local file="$1"
  local parent
  parent="$(canonical_dir "$(dirname -- "$file")")" || return 1
  printf '%s/%s\n' "$parent" "$(basename -- "$file")"
}

path_has_symlink() {
  local path="$1"
  local part current=""
  local old_ifs="$IFS"
  IFS='/'
  # shellcheck disable=SC2086
  set -- $path
  IFS="$old_ifs"
  for part in "$@"; do
    [[ -z "$part" ]] && continue
    current="$current/$part"
    [[ -L "$current" ]] && return 0
  done
  return 1
}

validate_no_symlink_path() {
  local description="$1"
  local path="$2"
  [[ "$path" == /* ]] || die "$description must be absolute"
  [[ "$path" != *'/../'* && "$path" != '..' && "$path" != *'/./'* ]] || die "$description must not contain path traversal"
  [[ -e "$path" ]] || die "$description must already exist before validation"
  path_has_symlink "$path" && die "$description must not traverse a symlink"
  return 0
}

file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then
    stat -c '%a' "$1"
  else
    stat -f '%Lp' "$1"
  fi
}

file_owner() {
  if stat -c '%u' "$1" >/dev/null 2>&1; then
    stat -c '%u' "$1"
  else
    stat -f '%u' "$1"
  fi
}

file_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

dotenv_validate_syntax() {
  local file="$1"
  local line line_number=0 key raw_value inner seen="|"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || die "unsupported dotenv syntax at line $line_number"
    key="${BASH_REMATCH[1]}"
    raw_value="${BASH_REMATCH[2]}"
    [[ "$seen" != *"|$key|"* ]] || die "duplicate dotenv key: $key"
    seen="${seen}${key}|"
    [[ "$raw_value" != *\#* ]] || die "inline comments are forbidden in dotenv values: $key"
    if [[ "$raw_value" == \"* ]]; then
      [[ ${#raw_value} -ge 2 && "${raw_value: -1}" == '"' ]] || die "ambiguous double quote in dotenv value: $key"
      inner="${raw_value:1:${#raw_value}-2}"
      [[ "$inner" != *'"'* && "$inner" != *'$'* && "$inner" != *$'\\'* ]] || die "ambiguous double-quoted dotenv value: $key"
    elif [[ "$raw_value" == \'* ]]; then
      [[ ${#raw_value} -ge 2 && "${raw_value: -1}" == "'" ]] || die "ambiguous single quote in dotenv value: $key"
      inner="${raw_value:1:${#raw_value}-2}"
      [[ "$inner" != *"'"* && "$inner" != *'$'* ]] || die "ambiguous single-quoted dotenv value: $key"
    else
      [[ "$raw_value" != *[[:space:]]* ]] || die "unquoted dotenv values cannot contain whitespace: $key"
      [[ "$raw_value" != *'"'* && "$raw_value" != *"'"* && "$raw_value" != *'$'* ]] || die "unquoted dotenv values cannot contain quotes or interpolation: $key"
    fi
  done <"$file"
}

dotenv_value() {
  local key="$1"
  local line value
  line="$(grep -E "^${key}=" "$ACTIVE_ENV_FILE" || true)"
  [[ -n "$line" ]] || return 1
  value="${line#*=}"
  if [[ "$value" == \"* ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'* ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

dotenv_present() {
  grep -Eq "^${1}=" "$ACTIVE_ENV_FILE"
}

require_value() {
  local key="$1"
  local value
  value="$(dotenv_value "$key" || true)"
  [[ -n "$value" ]] || die "$key must be set in the staging environment file"
  printf '%s' "$value"
}

is_placeholder() {
  local value
  value="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  [[ -z "$value" || "$value" == *'<'* || "$value" == *'>'* || "$value" == *'change-me'* || "$value" == *'generate-'* || "$value" == *'replace-this'* || "$value" == *'example'* || "$value" == *'password'* || "$value" == *'secret'* ]]
}

is_decimal() {
  [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

decimal_lte() {
  awk -v value="$1" -v limit="$2" 'BEGIN { exit !(value <= limit) }'
}

decimal_gt_zero() {
  awk -v value="$1" 'BEGIN { exit !(value > 0) }'
}

memory_megabytes() {
  [[ "$1" =~ ^([1-9][0-9]*)[mM]$ ]] || return 1
  printf '%s' "${BASH_REMATCH[1]}"
}

normalize_decimal() {
  awk -v value="$1" 'BEGIN { printf "%.12g", value }'
}

require_resolved_service_scalar() {
  local file="$1" service="$2" key="$3" expected="$4" description="$5"
  awk -v service="$service" -v key="$key" -v expected="$expected" '
    $0 == "  " service ":" {
      in_service = 1
      next
    }
    in_service && /^  [^ ]/ {
      in_service = 0
    }
    in_service && $1 == key ":" {
      value = $2
      gsub(/["\047]/, "", value)
      if (value == expected) {
        found = 1
      }
    }
    END { exit !found }
  ' "$file" || die "resolved Compose is missing the validated $description"
}

validate_resource_limits() {
  STAGING_DB_CPU_LIMIT="$(require_value STAGING_DB_CPU_LIMIT)"
  STAGING_BACKEND_CPU_LIMIT="$(require_value STAGING_BACKEND_CPU_LIMIT)"
  STAGING_NGINX_CPU_LIMIT="$(require_value STAGING_NGINX_CPU_LIMIT)"
  for value in "$STAGING_DB_CPU_LIMIT" "$STAGING_BACKEND_CPU_LIMIT" "$STAGING_NGINX_CPU_LIMIT"; do
    is_decimal "$value" || die "CPU limits must be decimal numbers"
    decimal_gt_zero "$value" || die "CPU limits must be greater than zero"
  done
  decimal_lte "$STAGING_DB_CPU_LIMIT" 0.75 || die "STAGING_DB_CPU_LIMIT exceeds 0.75"
  decimal_lte "$STAGING_BACKEND_CPU_LIMIT" 1.00 || die "STAGING_BACKEND_CPU_LIMIT exceeds 1.00"
  decimal_lte "$STAGING_NGINX_CPU_LIMIT" 0.25 || die "STAGING_NGINX_CPU_LIMIT exceeds 0.25"
  total_cpu="$(awk -v a="$STAGING_DB_CPU_LIMIT" -v b="$STAGING_BACKEND_CPU_LIMIT" -v c="$STAGING_NGINX_CPU_LIMIT" 'BEGIN { print a + b + c }')"
  decimal_lte "$total_cpu" 2.00 || die "total staging CPU limit exceeds 2.00"

  STAGING_DB_MEMORY_LIMIT="$(require_value STAGING_DB_MEMORY_LIMIT)"
  STAGING_BACKEND_MEMORY_LIMIT="$(require_value STAGING_BACKEND_MEMORY_LIMIT)"
  STAGING_NGINX_MEMORY_LIMIT="$(require_value STAGING_NGINX_MEMORY_LIMIT)"
  db_memory="$(memory_megabytes "$STAGING_DB_MEMORY_LIMIT")" || die "STAGING_DB_MEMORY_LIMIT must use whole megabytes"
  backend_memory="$(memory_megabytes "$STAGING_BACKEND_MEMORY_LIMIT")" || die "STAGING_BACKEND_MEMORY_LIMIT must use whole megabytes"
  nginx_memory="$(memory_megabytes "$STAGING_NGINX_MEMORY_LIMIT")" || die "STAGING_NGINX_MEMORY_LIMIT must use whole megabytes"
  [[ "$db_memory" -le 512 ]] || die "STAGING_DB_MEMORY_LIMIT exceeds 512m"
  [[ "$backend_memory" -le 768 ]] || die "STAGING_BACKEND_MEMORY_LIMIT exceeds 768m"
  [[ "$nginx_memory" -le 128 ]] || die "STAGING_NGINX_MEMORY_LIMIT exceeds 128m"
  [[ $((db_memory + backend_memory + nginx_memory)) -le 1408 ]] || die "total staging memory limit exceeds 1408m"
  STAGING_DB_CPU_NORMALIZED="$(normalize_decimal "$STAGING_DB_CPU_LIMIT")"
  STAGING_BACKEND_CPU_NORMALIZED="$(normalize_decimal "$STAGING_BACKEND_CPU_LIMIT")"
  STAGING_NGINX_CPU_NORMALIZED="$(normalize_decimal "$STAGING_NGINX_CPU_LIMIT")"
  STAGING_DB_MEMORY_BYTES="$((db_memory * 1024 * 1024))"
  STAGING_BACKEND_MEMORY_BYTES="$((backend_memory * 1024 * 1024))"
  STAGING_NGINX_MEMORY_BYTES="$((nginx_memory * 1024 * 1024))"

  JAVA_OPTS="$(require_value JAVA_OPTS)"
  [[ "$JAVA_OPTS" =~ ^-Xms([1-9][0-9]*)[mM][[:space:]]+-Xmx([1-9][0-9]*)[mM]$ ]] || die "JAVA_OPTS must be exactly '-Xms<whole-m> -Xmx<whole-m>'"
  java_xms="${BASH_REMATCH[1]}"
  java_xmx="${BASH_REMATCH[2]}"
  [[ "$java_xms" -le "$java_xmx" ]] || die "JAVA_OPTS -Xms must not exceed -Xmx"
  [[ "$java_xmx" -le 512 ]] || die "JAVA_OPTS -Xmx exceeds 512m"
  [[ "$java_xmx" -le "$backend_memory" ]] || die "JAVA_OPTS -Xmx exceeds backend memory limit"

  STAGING_LOG_MAX_SIZE="$(require_value STAGING_LOG_MAX_SIZE)"
  STAGING_LOG_MAX_FILE="$(require_value STAGING_LOG_MAX_FILE)"
  log_size="$(memory_megabytes "$STAGING_LOG_MAX_SIZE")" || die "STAGING_LOG_MAX_SIZE must use whole megabytes"
  [[ "$log_size" -le 10 ]] || die "STAGING_LOG_MAX_SIZE exceeds 10m"
  [[ "$STAGING_LOG_MAX_FILE" =~ ^[1-3]$ ]] || die "STAGING_LOG_MAX_FILE must be between 1 and 3"
}

assert_no_ambient_overrides() {
  local key
  for key in $INTERPOLATION_KEYS; do
    if [[ ${!key+x} ]]; then
      die "ambient environment override is forbidden: $key"
    fi
  done
}

assert_clean_release() {
  local status line submodules ignored_build_inputs
  [[ "$RELEASE_DIR" == "$STAGING_ROOT/releases/$STAGING_COMMIT_SHA" ]] || die "release path no longer matches STAGING_COMMIT_SHA"
  [[ "$(git -C "$RELEASE_DIR" rev-parse HEAD 2>/dev/null || true)" == "$STAGING_COMMIT_SHA" ]] || die "release Git HEAD no longer matches STAGING_COMMIT_SHA"
  git -C "$RELEASE_DIR" diff --quiet || die "staging release has tracked working-tree changes"
  git -C "$RELEASE_DIR" diff --cached --quiet || die "staging release has staged changes"
  status="$(git -C "$RELEASE_DIR" status --porcelain=v1 --untracked-files=all)"
  [[ -z "$status" ]] || die "staging release must have no tracked or untracked files"
  ignored_build_inputs="$(git -C "$RELEASE_DIR" ls-files --others --ignored --exclude-standard -- backend frontend)"
  [[ -z "$ignored_build_inputs" ]] || die "staging release contains ignored backend or frontend build inputs"
  submodules="$(git -C "$RELEASE_DIR" submodule status --recursive 2>/dev/null || true)"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    case "${line:0:1}" in
      -|+|U) die "staging release has an unclean or unresolved submodule" ;;
    esac
  done <<<"$submodules"
}

validate_source_env_permissions() {
  [[ "$LOCAL_VALIDATE_MODE" == "true" ]] && return 0
  ORIGINAL_CONFIG_DIR="$(canonical_dir "$(dirname "$ORIGINAL_ENV_FILE")")" || die "cannot canonicalize staging config directory"
  [[ "$(file_owner "$ORIGINAL_CONFIG_DIR")" == "$(id -u)" ]] || die "staging config directory must be owned by the invoking user"
  [[ "$(file_mode "$ORIGINAL_CONFIG_DIR")" == "700" ]] || die "staging config directory mode must be 0700"
  [[ "$(file_owner "$ORIGINAL_ENV_FILE")" == "$(id -u)" ]] || die "staging environment file must be owned by the invoking user"
  [[ "$(file_mode "$ORIGINAL_ENV_FILE")" == "600" ]] || die "staging environment file mode must be 0600"
}

validate_start_authorization() {
  local evidence_parent evidence_canonical environment_digest
  [[ "$EXECUTE_START" == "true" ]] || return 0
  [[ "$APPROVED_SHA" == "$STAGING_COMMIT_SHA" ]] || die "--approved-sha must exactly match STAGING_COMMIT_SHA"
  [[ "$PREFLIGHT_EVIDENCE" == "$STAGING_ROOT/evidence/"* ]] || die "--preflight-evidence must be under the exact Staging evidence directory"
  [[ -f "$PREFLIGHT_EVIDENCE" && ! -L "$PREFLIGHT_EVIDENCE" ]] || die "--preflight-evidence must be an existing regular file"
  path_has_symlink "$PREFLIGHT_EVIDENCE" && die "--preflight-evidence must not traverse a symlink"
  evidence_parent="$(canonical_dir "$(dirname -- "$PREFLIGHT_EVIDENCE")")" || die "cannot canonicalize preflight evidence directory"
  evidence_canonical="$evidence_parent/$(basename -- "$PREFLIGHT_EVIDENCE")"
  [[ "$evidence_canonical" == "$PREFLIGHT_EVIDENCE" ]] || die "--preflight-evidence canonical path changed"
  [[ "$evidence_parent" == "$STAGING_ROOT/evidence" ]] || die "--preflight-evidence must be directly under the exact Staging evidence directory"
  [[ "$(file_owner "$evidence_parent")" == "$(id -u)" && "$(file_mode "$evidence_parent")" == "700" ]] ||
    die "Staging evidence directory must be owned by the invoking user with mode 0700"
  [[ "$(file_owner "$PREFLIGHT_EVIDENCE")" == "$(id -u)" && "$(file_mode "$PREFLIGHT_EVIDENCE")" == "600" ]] ||
    die "preflight evidence must be owned by the invoking user with mode 0600"
  [[ "$(file_digest "$PREFLIGHT_EVIDENCE")" == "$PREFLIGHT_EVIDENCE_SHA256" ]] ||
    die "--preflight-evidence-sha256 does not match the exact evidence file"
  environment_digest="$(file_digest "$ORIGINAL_ENV_FILE")"
  grep -Fxq "EVIDENCE|APPROVED_SHA|$STAGING_COMMIT_SHA" "$PREFLIGHT_EVIDENCE" ||
    die "preflight evidence is not bound to the approved SHA"
  grep -Fxq "EVIDENCE|STAGING_ROOT|$STAGING_ROOT" "$PREFLIGHT_EVIDENCE" ||
    die "preflight evidence is not bound to the exact Staging root"
  grep -Fxq "EVIDENCE|COMPOSE_PROJECT|$EXPECTED_PROJECT" "$PREFLIGHT_EVIDENCE" ||
    die "preflight evidence is not bound to the exact Staging project"
  grep -Fxq "EVIDENCE|ENV_SHA256|$environment_digest" "$PREFLIGHT_EVIDENCE" ||
    die "preflight evidence is not bound to the current Staging environment"
  grep -Fxq 'SUMMARY|PASS|same-host Staging preflight passed without state changes' "$PREFLIGHT_EVIDENCE" || die "--preflight-evidence does not show a passed STG-004 preflight"
}

path_is_not_group_or_other_writable() {
  local path="$1"
  local mode permissions group other
  mode="$(file_mode "$path")"
  [[ "$mode" =~ ^[0-7]{3,4}$ ]] || return 1
  permissions="${mode: -3}"
  group="${permissions:1:1}"
  other="${permissions:2:1}"
  (( (group & 2) == 0 && (other & 2) == 0 ))
}

validate_postgres_data_path() {
  local expected_data_dir data_owner
  POSTGRES_DATA_DIR="$(require_value STAGING_POSTGRES_DATA_DIR)"
  validate_no_symlink_path "STAGING_POSTGRES_DATA_DIR" "$POSTGRES_DATA_DIR"
  POSTGRES_DATA_DIR="$(canonical_dir "$POSTGRES_DATA_DIR")" || die "cannot canonicalize STAGING_POSTGRES_DATA_DIR"
  expected_data_dir="$STAGING_ROOT/state/postgres"
  [[ "$POSTGRES_DATA_DIR" == "$expected_data_dir" ]] || die "STAGING_POSTGRES_DATA_DIR must be $expected_data_dir"
  [[ "$POSTGRES_DATA_DIR" != /home/ubuntu/Restaurant_System/* && "$POSTGRES_DATA_DIR" != */deployment/cloud/data/postgres* ]] || die "production PostgreSQL data paths are forbidden"

  [[ "$LOCAL_VALIDATE_MODE" == "true" ]] && return 0
  validate_no_symlink_path "STAGING_STATE_DIR" "$STAGING_ROOT/state"
  for path in "$STAGING_ROOT" "$STAGING_ROOT/state" "$POSTGRES_DATA_DIR"; do
    path_is_not_group_or_other_writable "$path" || die "staging PostgreSQL path must not be group or other writable: $path"
  done
  data_owner="$(file_owner "$POSTGRES_DATA_DIR")"
  [[ "$data_owner" == "$(id -u)" || "$data_owner" == "70" ]] || die "staging PostgreSQL data directory owner must be the deploy user or postgres:16-alpine UID 70"
}

validate_inputs() {
  dotenv_validate_syntax "$ACTIVE_ENV_FILE"
  COMPOSE_PROJECT_NAME="$(require_value COMPOSE_PROJECT_NAME)"
  [[ "$COMPOSE_PROJECT_NAME" == "$EXPECTED_PROJECT" ]] || die "COMPOSE_PROJECT_NAME must be $EXPECTED_PROJECT"

  STAGING_ROOT="$(require_value STAGING_ROOT)"
  validate_no_symlink_path "STAGING_ROOT" "$STAGING_ROOT"
  STAGING_ROOT="$(canonical_dir "$STAGING_ROOT")" || die "cannot canonicalize STAGING_ROOT"
  if [[ "$LOCAL_VALIDATE_MODE" == "true" ]]; then
    [[ "$STAGING_ROOT" == */restaurant-pos/staging ]] || die "local STAGING_ROOT must end with /restaurant-pos/staging"
  else
    [[ "$STAGING_ROOT" == "$SERVER_STAGING_ROOT" ]] || die "server STAGING_ROOT must be exactly $SERVER_STAGING_ROOT"
  fi
  [[ "$ORIGINAL_ENV_FILE" == "$STAGING_ROOT/config/.env.staging" ]] || die "environment file must be $STAGING_ROOT/config/.env.staging"
  [[ "$LOCAL_VALIDATE_MODE" == "true" || "$ORIGINAL_CONFIG_DIR" == "$STAGING_ROOT/config" ]] || die "staging config directory no longer matches the validated root"

  STAGING_COMMIT_SHA="$(require_value STAGING_COMMIT_SHA)"
  [[ "$STAGING_COMMIT_SHA" =~ ^[0-9a-f]{40}$ ]] || die "STAGING_COMMIT_SHA must be a full lowercase 40-character Git SHA"

  RELEASE_DIR="$(canonical_dir "$SCRIPT_DIR/../..")" || die "cannot canonicalize staging release directory"
  path_has_symlink "$RELEASE_DIR" && die "staging release directory must not traverse a symlink"
  [[ "$RELEASE_DIR" == "$STAGING_ROOT/releases/$STAGING_COMMIT_SHA" ]] || die "release directory must be $STAGING_ROOT/releases/$STAGING_COMMIT_SHA"
  [[ -d "$RELEASE_DIR/.git" || -f "$RELEASE_DIR/.git" ]] || die "staging release directory is not a Git checkout"
  [[ "$(git -C "$RELEASE_DIR" rev-parse HEAD 2>/dev/null || true)" == "$STAGING_COMMIT_SHA" ]] || die "release Git HEAD does not match STAGING_COMMIT_SHA"
  assert_clean_release

  validate_postgres_data_path

  HTTP_BIND_ADDRESS="$(require_value HTTP_BIND_ADDRESS)"
  HTTP_PORT="$(require_value HTTP_PORT)"
  NGINX_SERVER_NAME="$(require_value NGINX_SERVER_NAME)"
  [[ "$HTTP_BIND_ADDRESS" == "127.0.0.1" ]] || die "HTTP_BIND_ADDRESS must be 127.0.0.1"
  [[ "$HTTP_PORT" == "18080" ]] || die "HTTP_PORT must be the isolated staging port 18080"
  [[ "$NGINX_SERVER_NAME" == "localhost" ]] || die "NGINX_SERVER_NAME must be localhost"

  POSTGRES_IMAGE_TAG="$(require_value POSTGRES_IMAGE_TAG)"
  [[ "$POSTGRES_IMAGE_TAG" == "16-alpine" ]] || die "POSTGRES_IMAGE_TAG must be 16-alpine"

  DB_NAME="$(require_value DB_NAME)"
  DB_USER="$(require_value DB_USER)"
  DB_PASSWORD="$(require_value DB_PASSWORD)"
  JWT_SECRET="$(require_value JWT_SECRET)"
  [[ "$DB_NAME" == *staging* && "$DB_NAME" != "restaurant_pos" && "$DB_NAME" != "restaurant_system" ]] || die "DB_NAME must be staging-specific"
  [[ "$DB_USER" == *staging* && "$DB_USER" != "restaurant_pos" && "$DB_USER" != "postgres" ]] || die "DB_USER must be staging-specific"
  is_placeholder "$DB_PASSWORD" && die "DB_PASSWORD is blank or a placeholder"
  is_placeholder "$JWT_SECRET" && die "JWT_SECRET is blank or a placeholder"
  [[ ${#DB_PASSWORD} -ge 16 ]] || die "DB_PASSWORD must be at least 16 characters"
  [[ ${#JWT_SECRET} -ge 32 ]] || die "JWT_SECRET must be at least 32 characters"
  [[ "$DB_PASSWORD" != "$JWT_SECRET" ]] || die "DB_PASSWORD and JWT_SECRET must differ"

  SPRING_PROFILES_ACTIVE="$(require_value SPRING_PROFILES_ACTIVE)"
  [[ "$SPRING_PROFILES_ACTIVE" == "cloud" ]] || die "SPRING_PROFILES_ACTIVE must be cloud"

  BACKEND_IMAGE="$(require_value BACKEND_IMAGE)"
  FRONTEND_IMAGE="$(require_value FRONTEND_IMAGE)"
  VITE_APP_BUILD_VERSION="$(require_value VITE_APP_BUILD_VERSION)"
  for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
    [[ "$image" != *':local' && "$image" == *":staging-$STAGING_COMMIT_SHA" ]] || die "images must use a SHA-specific staging tag"
  done
  [[ "$VITE_APP_BUILD_VERSION" == "staging-$STAGING_COMMIT_SHA" ]] || die "VITE_APP_BUILD_VERSION must include the exact staging SHA"

  STAGING_PRINT_MODE="$(require_value STAGING_PRINT_MODE)"
  STAGING_PRINTING_FEATURE_ENABLED="$(require_value STAGING_PRINTING_FEATURE_ENABLED)"
  if [[ "$LOCAL_VALIDATE_MODE" == "true" && "$ACTION" == "validate" && "$STAGING_PRINT_MODE" == "MOCK" ]]; then
    [[ "$STAGING_PRINTING_FEATURE_ENABLED" == "true" ]] || die "local MOCK validation requires STAGING_PRINTING_FEATURE_ENABLED=true"
  else
    [[ "$STAGING_PRINT_MODE" == "DISABLED" ]] || die "server and default staging must use STAGING_PRINT_MODE=DISABLED"
    [[ "$STAGING_PRINTING_FEATURE_ENABLED" == "false" ]] || die "DISABLED requires STAGING_PRINTING_FEATURE_ENABLED=false"
  fi
  ! dotenv_present STAGING_PRINTER_ENDPOINT || die "STAGING_PRINTER_ENDPOINT must remain absent"

  for key in APP_AUTH_X_USER_ID_FALLBACK_ENABLED APP_DEV_TOOLS_ROLE_SWITCHER_ENABLED APP_SEED_DEFAULT_USERS_ENABLED APP_SEED_DEMO_DATA_ENABLED; do
    value="$(dotenv_value "$key" || true)"
    [[ -z "$value" || "$value" == "false" ]] || die "$key must be false when set"
  done
  validate_resource_limits
}

create_env_snapshot() {
  local temporary_dir source_digest snapshot_digest
  temporary_dir="$(canonical_dir "${TMPDIR:-/tmp}")" || die "cannot canonicalize temporary directory"
  [[ -d "$temporary_dir" ]] || die "temporary directory does not exist"
  source_digest="$(file_digest "$ORIGINAL_ENV_FILE")"
  umask 077
  ENV_SNAPSHOT="$(mktemp "$temporary_dir/restaurant-pos-staging-env.XXXXXX")"
  cp "$ORIGINAL_ENV_FILE" "$ENV_SNAPSHOT"
  chmod 600 "$ENV_SNAPSHOT"
  snapshot_digest="$(file_digest "$ENV_SNAPSHOT")"
  [[ "$source_digest" == "$snapshot_digest" ]] || die "staging environment changed while creating the private snapshot"
  ENV_SNAPSHOT_DIGEST="$snapshot_digest"
  ACTIVE_ENV_FILE="$ENV_SNAPSHOT"
}

assert_snapshot_integrity() {
  [[ -n "$ENV_SNAPSHOT" && -n "$ENV_SNAPSHOT_DIGEST" && -f "$ENV_SNAPSHOT" ]] || die "private staging environment snapshot is unavailable"
  [[ "$(file_mode "$ENV_SNAPSHOT")" == "600" ]] || die "private staging environment snapshot mode must be 0600"
  [[ "$(file_digest "$ENV_SNAPSHOT")" == "$ENV_SNAPSHOT_DIGEST" ]] || die "private staging environment snapshot changed during deployment"
}

controlled_compose() {
  local active_env_file="$1"
  shift
  env -i \
    PATH="$SAFE_PATH" \
    HOME="/nonexistent" \
    DOCKER_CONFIG="/nonexistent" \
    "$DOCKER_BIN" --context default compose \
    --project-name "$COMPOSE_PROJECT_NAME" \
    --env-file "$active_env_file" \
    -f "$STAGING_COMPOSE_FILE" "$@"
}

assert_resolved_compose() {
  local active_env_file="$1"
  local temporary_dir resolved_config services
  assert_snapshot_integrity
  validate_postgres_data_path
  temporary_dir="$(canonical_dir "${TMPDIR:-/tmp}")" || die "cannot canonicalize temporary directory"
  [[ -d "$temporary_dir" ]] || die "temporary directory does not exist"
  resolved_config="$(mktemp "$temporary_dir/restaurant-pos-staging-config.XXXXXX")"
  RESOLVED_CONFIG="$resolved_config"
  chmod 600 "$resolved_config"
  controlled_compose "$active_env_file" config >"$resolved_config" || die "Compose config validation failed"
  assert_snapshot_integrity
  validate_postgres_data_path
  services="$(controlled_compose "$active_env_file" config --services)" || die "Compose service validation failed"
  [[ "$services" == $'db\nbackend\nnginx' ]] || die "resolved Compose services must be exactly db, backend, nginx"

  # The resolved file can contain secret values, so it is never printed.
  grep -Fq "postgres:$POSTGRES_IMAGE_TAG" "$resolved_config" || die "resolved Compose PostgreSQL image differs from the validated tag"
  grep -Fq "$BACKEND_IMAGE" "$resolved_config" || die "resolved Compose backend image is not the validated staging image"
  grep -Fq "$FRONTEND_IMAGE" "$resolved_config" || die "resolved Compose frontend image is not the validated staging image"
  grep -Fq "$POSTGRES_DATA_DIR" "$resolved_config" || die "resolved Compose PostgreSQL source path is not the validated staging path"
  grep -Eq "SPRING_PROFILES_ACTIVE: [\"']?${SPRING_PROFILES_ACTIVE}" "$resolved_config" || die "resolved Compose profile differs from the validated profile"
  grep -Eq "DB_NAME: [\"']?${DB_NAME}" "$resolved_config" || die "resolved Compose DB_NAME differs from the validated identity"
  grep -Eq "DB_USER: [\"']?${DB_USER}" "$resolved_config" || die "resolved Compose DB_USER differs from the validated identity"
  grep -Eq "NGINX_SERVER_NAME: [\"']?${NGINX_SERVER_NAME}" "$resolved_config" || die "resolved Compose NGINX server name differs from the validated value"
  grep -Fq "VITE_APP_BUILD_VERSION: staging-$STAGING_COMMIT_SHA" "$resolved_config" || die "resolved Compose frontend build version differs from the validated SHA"
  grep -Eq "APP_FEATURES_PRINTING: [\"']?${STAGING_PRINTING_FEATURE_ENABLED}" "$resolved_config" || die "resolved backend printing feature does not match the validated staging mode"
  grep -Eq '(127\.0\.0\.1:18080:80|published: "18080")' "$resolved_config" || die "resolved Compose does not expose the required loopback staging HTTP port"
  # Compose normalizes values such as 1.00 to 1 and 512m to bytes.
  require_resolved_service_scalar "$resolved_config" db cpus "$STAGING_DB_CPU_NORMALIZED" "database CPU limit"
  require_resolved_service_scalar "$resolved_config" backend cpus "$STAGING_BACKEND_CPU_NORMALIZED" "backend CPU limit"
  require_resolved_service_scalar "$resolved_config" nginx cpus "$STAGING_NGINX_CPU_NORMALIZED" "nginx CPU limit"
  require_resolved_service_scalar "$resolved_config" db mem_limit "$STAGING_DB_MEMORY_BYTES" "database memory limit"
  require_resolved_service_scalar "$resolved_config" backend mem_limit "$STAGING_BACKEND_MEMORY_BYTES" "backend memory limit"
  require_resolved_service_scalar "$resolved_config" nginx mem_limit "$STAGING_NGINX_MEMORY_BYTES" "nginx memory limit"
  for service in db backend nginx; do
    require_resolved_service_scalar "$resolved_config" "$service" max-size "$STAGING_LOG_MAX_SIZE" "$service log size limit"
    require_resolved_service_scalar "$resolved_config" "$service" max-file "$STAGING_LOG_MAX_FILE" "$service log file limit"
  done
  if grep -Eq '(:local|0\.0\.0\.0|:80:80|:443:443|/home/ubuntu/Restaurant_System/deployment/cloud/data/postgres)' "$resolved_config"; then
    die "resolved Compose contains a forbidden production-like value"
  fi
  rm -f "$resolved_config"
  RESOLVED_CONFIG=""
}

run_deploy_sequence() {
  assert_snapshot_integrity
  validate_postgres_data_path
  assert_clean_release
  assert_resolved_compose "$ACTIVE_ENV_FILE"
  assert_snapshot_integrity
  validate_postgres_data_path
  assert_clean_release

  echo "Building isolated staging images for $STAGING_COMMIT_SHA..."
  controlled_compose "$ACTIVE_ENV_FILE" build backend nginx
  assert_snapshot_integrity
  validate_postgres_data_path
  assert_clean_release
  echo "Starting only the $COMPOSE_PROJECT_NAME project..."
  controlled_compose "$ACTIVE_ENV_FILE" up -d
  echo "Staging deployment started. Run staging-health-check.sh with the same --env-file."
}

main() {
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      [[ $# -ge 2 ]] || die "--env-file requires a path"
      SOURCE_ENV_FILE="$2"
      shift
      ;;
    --validate|--dry-run)
      [[ "$EXECUTE_START" == "false" ]] || die "--validate/--dry-run cannot be combined with --execute-start"
      ACTION="validate"
      ;;
    --execute-start)
      [[ "$ACTION" == "validate" && "$EXECUTE_START" == "false" ]] || die "--execute-start cannot be combined with another action"
      [[ "$LOCAL_VALIDATE_MODE" == "false" ]] || die "--execute-start cannot be combined with --local-validate"
      ACTION="deploy"
      EXECUTE_START="true"
      ;;
    --approved-sha)
      [[ $# -ge 2 ]] || die "--approved-sha requires a full SHA"
      APPROVED_SHA="$2"
      shift
      ;;
    --preflight-evidence)
      [[ $# -ge 2 ]] || die "--preflight-evidence requires a path"
      PREFLIGHT_EVIDENCE="$2"
      shift
      ;;
    --preflight-evidence-sha256)
      [[ $# -ge 2 ]] || die "--preflight-evidence-sha256 requires a SHA-256"
      PREFLIGHT_EVIDENCE_SHA256="$2"
      shift
      ;;
    --local-validate)
      [[ "$EXECUTE_START" == "false" ]] || die "--local-validate cannot be combined with --execute-start"
      ACTION="validate"
      LOCAL_VALIDATE_MODE="true"
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --pull-images|--http|--https|--down|--clean|--restore|--*)
      die "unsupported staging option: $1"
      ;;
    *)
      die "unexpected argument: $1"
      ;;
  esac
  shift
done

[[ -n "$SOURCE_ENV_FILE" ]] || die "--env-file is required; no default environment file is used"
if [[ "$EXECUTE_START" == "true" ]]; then
  [[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]] || die "--approved-sha must be a full lowercase 40-character Git SHA"
  [[ -n "$PREFLIGHT_EVIDENCE" ]] || die "--execute-start requires --preflight-evidence"
  [[ "$PREFLIGHT_EVIDENCE_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
    die "--execute-start requires a lowercase 64-character --preflight-evidence-sha256"
  [[ "$LOCAL_VALIDATE_MODE" == "false" ]] || die "--execute-start cannot use local validation mode"
else
  [[ -z "$APPROVED_SHA" && -z "$PREFLIGHT_EVIDENCE" && -z "$PREFLIGHT_EVIDENCE_SHA256" ]] ||
    die "approval inputs are only valid with --execute-start"
fi
[[ "$SOURCE_ENV_FILE" == /* ]] || die "--env-file must be an absolute path"
[[ -f "$SOURCE_ENV_FILE" ]] || die "environment file does not exist"
SOURCE_ENV_FILE="$(canonical_file "$SOURCE_ENV_FILE")" || die "cannot canonicalize environment file"
path_has_symlink "$SOURCE_ENV_FILE" && die "environment file must not traverse a symlink"
ORIGINAL_ENV_FILE="$SOURCE_ENV_FILE"
[[ -f "$STAGING_COMPOSE_FILE" ]] || die "missing standalone staging Compose file"
[[ -f "$SCRIPT_DIR/nginx.http.conf.template" ]] || die "missing HTTP Nginx template"
assert_no_ambient_overrides
validate_source_env_permissions
create_env_snapshot
DOCKER_BIN="$(command -v docker || true)"
[[ "$DOCKER_BIN" == /* && -x "$DOCKER_BIN" ]] || die "docker CLI is required"

assert_snapshot_integrity
validate_inputs
assert_snapshot_integrity
assert_resolved_compose "$ACTIVE_ENV_FILE"
validate_start_authorization

if [[ "$ACTION" == "validate" ]]; then
  echo "Staging validation passed for $STAGING_COMMIT_SHA. No directories, images, or containers were changed."
  assert_snapshot_integrity
  controlled_compose "$ACTIVE_ENV_FILE" config --services
  exit 0
fi

run_deploy_sequence
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap cleanup EXIT
  main "$@"
fi
