#!/usr/bin/env bash
set -euo pipefail

# This wrapper deliberately has no production fallback. Every Compose command
# uses the explicit staging project and the standalone staging Compose file.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.staging.yml"
ACTION="deploy"
ENV_FILE=""
EXPECTED_PROJECT="restaurant-pos-staging"
SERVER_STAGING_ROOT="/srv/restaurant-pos/staging"
LOCAL_VALIDATE_MODE="false"

usage() {
  cat <<'EOF'
Restaurant POS isolated staging deploy helper.

Usage:
  ./staging-deploy.sh --env-file /srv/restaurant-pos/staging/config/.env.staging [--validate|--dry-run]
  ./staging-deploy.sh --help

The default action validates, then builds and starts only the explicit
restaurant-pos-staging Compose project. It never pulls images, restores data,
runs Flyway clean, or accepts production-like configuration.

Options:
  --env-file PATH  Required absolute staging environment file.
  --validate       Validate paths, guards, and resolved Compose only.
  --dry-run        Alias for --validate.
  --local-validate Allow a non-/srv temporary root for local validation only.
  --help           Print this help text only.
EOF
}

die() {
  echo "staging guard: $*" >&2
  exit 1
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

dotenv_value() {
  local key="$1"
  local line value
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$ENV_FILE" | tail -n 1 || true)"
  [[ -n "$line" ]] || return 1
  line="${line#export }"
  value="${line#*=}"
  value="${value%$'\r'}"
  if [[ "$value" == \"*\" && ${#value} -ge 2 ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && ${#value} -ge 2 ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
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

validate_no_symlink_path() {
  local description="$1"
  local path="$2"
  [[ "$path" == /* ]] || die "$description must be absolute"
  [[ "$path" != *'/../'* && "$path" != '..' && "$path" != *'/./'* ]] || die "$description must not contain path traversal"
  [[ -e "$path" ]] || die "$description must already exist before validation"
  path_has_symlink "$path" && die "$description must not traverse a symlink"
  return 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      [[ $# -ge 2 ]] || die "--env-file requires a path"
      ENV_FILE="$2"
      shift
      ;;
    --validate|--dry-run)
      ACTION="validate"
      ;;
    --local-validate)
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

[[ -n "$ENV_FILE" ]] || die "--env-file is required; no default environment file is used"
[[ "$ENV_FILE" == /* ]] || die "--env-file must be an absolute path"
[[ -f "$ENV_FILE" ]] || die "environment file does not exist"
ENV_FILE="$(canonical_file "$ENV_FILE")" || die "cannot canonicalize environment file"
path_has_symlink "$ENV_FILE" && die "environment file must not traverse a symlink"

[[ -f "$COMPOSE_FILE" ]] || die "missing standalone staging Compose file"
[[ -f "$SCRIPT_DIR/nginx.http.conf.template" ]] || die "missing HTTP Nginx template"

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

EXPECTED_ENV_FILE="$STAGING_ROOT/config/.env.staging"
[[ "$ENV_FILE" == "$EXPECTED_ENV_FILE" ]] || die "environment file must be $EXPECTED_ENV_FILE"

STAGING_COMMIT_SHA="$(require_value STAGING_COMMIT_SHA)"
[[ "$STAGING_COMMIT_SHA" =~ ^[0-9a-f]{40}$ ]] || die "STAGING_COMMIT_SHA must be a full lowercase 40-character Git SHA"

RELEASE_DIR="$(canonical_dir "$SCRIPT_DIR/../..")" || die "cannot canonicalize staging release directory"
path_has_symlink "$RELEASE_DIR" && die "staging release directory must not traverse a symlink"
[[ "$RELEASE_DIR" == "$STAGING_ROOT/releases/$STAGING_COMMIT_SHA" ]] || die "release directory must be $STAGING_ROOT/releases/$STAGING_COMMIT_SHA"
[[ -d "$RELEASE_DIR/.git" || -f "$RELEASE_DIR/.git" ]] || die "staging release directory is not a Git checkout"
[[ "$(git -C "$RELEASE_DIR" rev-parse HEAD 2>/dev/null || true)" == "$STAGING_COMMIT_SHA" ]] || die "release Git HEAD does not match STAGING_COMMIT_SHA"

POSTGRES_DATA_DIR="$(require_value STAGING_POSTGRES_DATA_DIR)"
validate_no_symlink_path "STAGING_POSTGRES_DATA_DIR" "$POSTGRES_DATA_DIR"
POSTGRES_DATA_DIR="$(canonical_dir "$POSTGRES_DATA_DIR")" || die "cannot canonicalize STAGING_POSTGRES_DATA_DIR"
[[ "$POSTGRES_DATA_DIR" == "$STAGING_ROOT/state/postgres" ]] || die "STAGING_POSTGRES_DATA_DIR must be $STAGING_ROOT/state/postgres"
[[ "$POSTGRES_DATA_DIR" != /home/ubuntu/Restaurant_System/* && "$POSTGRES_DATA_DIR" != */deployment/cloud/data/postgres* ]] || die "production PostgreSQL data paths are forbidden"

HTTP_BIND_ADDRESS="$(require_value HTTP_BIND_ADDRESS)"
HTTP_PORT="$(require_value HTTP_PORT)"
[[ "$HTTP_BIND_ADDRESS" == "127.0.0.1" ]] || die "HTTP_BIND_ADDRESS must be 127.0.0.1"
[[ "$HTTP_PORT" == "18080" ]] || die "HTTP_PORT must be the isolated staging port 18080"

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
[[ "$STAGING_PRINT_MODE" == "DISABLED" || "$STAGING_PRINT_MODE" == "MOCK" ]] || die "STAGING_PRINT_MODE may only be DISABLED or MOCK"
STAGING_PRINTING_FEATURE_ENABLED="$(require_value STAGING_PRINTING_FEATURE_ENABLED)"
if [[ "$STAGING_PRINT_MODE" == "DISABLED" ]]; then
  [[ "$STAGING_PRINTING_FEATURE_ENABLED" == "false" ]] || die "DISABLED requires STAGING_PRINTING_FEATURE_ENABLED=false"
else
  [[ "$STAGING_PRINTING_FEATURE_ENABLED" == "true" ]] || die "MOCK requires STAGING_PRINTING_FEATURE_ENABLED=true"
fi
PRINTER_ENDPOINT="$(dotenv_value STAGING_PRINTER_ENDPOINT || true)"
[[ -z "$PRINTER_ENDPOINT" ]] || die "STAGING_PRINTER_ENDPOINT must remain unset"

for key in APP_AUTH_X_USER_ID_FALLBACK_ENABLED APP_DEV_TOOLS_ROLE_SWITCHER_ENABLED APP_SEED_DEFAULT_USERS_ENABLED APP_SEED_DEMO_DATA_ENABLED; do
  value="$(dotenv_value "$key" || true)"
  [[ -z "$value" || "$value" == "false" ]] || die "$key must be false when set"
done

COMPOSE=(docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
RESOLVED_CONFIG="$(mktemp "${TMPDIR:-/tmp}/restaurant-pos-staging-config.XXXXXX")"
trap 'rm -f "$RESOLVED_CONFIG"' EXIT
"${COMPOSE[@]}" config >"$RESOLVED_CONFIG" || die "Compose config validation failed"
services="$("${COMPOSE[@]}" config --services)" || die "Compose service validation failed"
[[ "$services" == $'db\nbackend\nnginx' ]] || die "resolved Compose services must be exactly db, backend, nginx"

# The resolved file can contain secret values, so it is never printed. These
# checks make the independent environment contract fail closed before build/up.
grep -Fq "$BACKEND_IMAGE" "$RESOLVED_CONFIG" || die "resolved Compose backend image is not the validated staging image"
grep -Fq "$FRONTEND_IMAGE" "$RESOLVED_CONFIG" || die "resolved Compose frontend image is not the validated staging image"
grep -Fq "$POSTGRES_DATA_DIR" "$RESOLVED_CONFIG" || die "resolved Compose PostgreSQL source path is not the validated staging path"
if ! grep -Eq "APP_FEATURES_PRINTING: [\"']?${STAGING_PRINTING_FEATURE_ENABLED}" "$RESOLVED_CONFIG"; then
  die "resolved backend printing feature does not match the validated staging mode"
fi
if ! grep -Eq '(127\.0\.0\.1:18080:80|published: "18080")' "$RESOLVED_CONFIG"; then
  die "resolved Compose does not expose the required loopback staging HTTP port"
fi
if grep -Eq '(:local|0\.0\.0\.0|:80:80|:443:443|/home/ubuntu/Restaurant_System/deployment/cloud/data/postgres)' "$RESOLVED_CONFIG"; then
  die "resolved Compose contains a forbidden production-like value"
fi

if [[ "$ACTION" == "validate" ]]; then
  echo "Staging validation passed for $STAGING_COMMIT_SHA. No directories, images, or containers were changed."
  printf '%s\n' "$services"
  exit 0
fi

echo "Building isolated staging images for $STAGING_COMMIT_SHA..."
"${COMPOSE[@]}" build backend nginx
echo "Starting only the $COMPOSE_PROJECT_NAME project..."
"${COMPOSE[@]}" up -d
echo "Staging deployment started. Run staging-health-check.sh with the same --env-file."
