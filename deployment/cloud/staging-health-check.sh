#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE=""
ACTION="check"
LOCAL_VALIDATE_MODE="false"
SERVER_STAGING_ROOT="/srv/restaurant-pos/staging"

usage() {
  cat <<'EOF'
Usage:
  ./staging-health-check.sh --env-file /srv/restaurant-pos/staging/config/.env.staging [--validate|--dry-run]

Checks only the loopback Staging frontend and API reverse-proxy paths. It never
starts/stops containers, changes data, triggers printing, or reads credentials.
EOF
}

die() {
  echo "staging health check: $*" >&2
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

validate_no_symlink_path() {
  local description="$1"
  local path="$2"
  [[ "$path" == /* ]] || die "$description must be absolute"
  [[ "$path" != *'/../'* && "$path" != '..' && "$path" != *'/./'* ]] || die "$description must not contain path traversal"
  [[ -e "$path" ]] || die "$description must already exist before validation"
  path_has_symlink "$path" && die "$description must not traverse a symlink"
  return 0
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
    *)
      die "unsupported option: $1"
      ;;
  esac
  shift
done

[[ -n "$ENV_FILE" && "$ENV_FILE" == /* && -f "$ENV_FILE" ]] || die "an existing absolute --env-file is required"
ENV_FILE="$(canonical_file "$ENV_FILE")" || die "cannot canonicalize environment file"
path_has_symlink "$ENV_FILE" && die "environment file must not traverse a symlink"

STAGING_ROOT="$(dotenv_value STAGING_ROOT || true)"
validate_no_symlink_path "STAGING_ROOT" "$STAGING_ROOT"
STAGING_ROOT="$(canonical_dir "$STAGING_ROOT")" || die "cannot canonicalize STAGING_ROOT"
if [[ "$LOCAL_VALIDATE_MODE" == "true" ]]; then
  [[ "$STAGING_ROOT" == */restaurant-pos/staging ]] || die "local STAGING_ROOT must end with /restaurant-pos/staging"
else
  [[ "$STAGING_ROOT" == "$SERVER_STAGING_ROOT" ]] || die "server STAGING_ROOT must be exactly $SERVER_STAGING_ROOT"
fi
[[ "$ENV_FILE" == "$STAGING_ROOT/config/.env.staging" ]] || die "environment file must be under the validated STAGING_ROOT config directory"

HTTP_BIND_ADDRESS="$(dotenv_value HTTP_BIND_ADDRESS || true)"
HTTP_PORT="$(dotenv_value HTTP_PORT || true)"
[[ "$HTTP_BIND_ADDRESS" == "127.0.0.1" && "$HTTP_PORT" == "18080" ]] || die "only 127.0.0.1:18080 is accepted"

if [[ "$ACTION" == "validate" ]]; then
  echo "Staging health-check configuration passed. No HTTP request was made."
  exit 0
fi

base_url="http://${HTTP_BIND_ADDRESS}:${HTTP_PORT}"
frontend_code="$(curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}' "$base_url/" || true)"
[[ "$frontend_code" == "200" ]] || die "frontend returned HTTP $frontend_code"

backend_code="$(curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}' "$base_url/api/v1/system/health" || true)"
[[ "$backend_code" == "200" ]] || die "backend health returned HTTP $backend_code"

echo "Staging loopback health check passed."
