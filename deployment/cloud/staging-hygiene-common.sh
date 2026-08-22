#!/usr/bin/env bash

# Shared, fail-closed primitives for the local-only Staging infrastructure
# hygiene helpers.  This file is a library; callers own set -Eeuo pipefail and
# their exit/cleanup traps.

HYGIENE_EXPECTED_ROOT="/srv/restaurant-pos/staging"
HYGIENE_ROOT="$HYGIENE_EXPECTED_ROOT"
HYGIENE_EXPECTED_ENVIRONMENT="restaurant-pos-staging"
HYGIENE_EXPECTED_PROJECT="restaurant-pos-staging"
HYGIENE_EXPECTED_PRODUCTION_PROJECT="cloud"
HYGIENE_EXPECTED_ENV_FILE="$HYGIENE_EXPECTED_ROOT/config/.env.staging"
HYGIENE_EXPECTED_RELEASE_KEEP_COUNT=3
HYGIENE_EXPECTED_RELEASE_MIN_AGE_SECONDS=604800
HYGIENE_EXPECTED_BUILD_CACHE_MIN_AGE="168h"
HYGIENE_EXPECTED_BUILD_CACHE_KEEP_STORAGE="10GB"
HYGIENE_PLAN_SCHEMA="restaurant-pos-staging-hygiene-v1"

hygiene_die() {
  printf 'HYGIENE|NO_GO|%s\n' "$*" >&2
  exit 2
}

hygiene_usage_error() {
  printf 'HYGIENE|INPUTS|NO_GO|%s\n' "$*" >&2
  exit 2
}

hygiene_file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then
    stat -c '%a' "$1"
  else
    stat -f '%Lp' "$1"
  fi
}

hygiene_file_owner() {
  if stat -c '%u' "$1" >/dev/null 2>&1; then
    stat -c '%u' "$1"
  else
    stat -f '%u' "$1"
  fi
}

hygiene_file_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

hygiene_string_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum -a 256 | awk '{print $1}'
  fi
}

hygiene_canonical_dir() {
  (cd -P -- "$1" 2>/dev/null && pwd)
}

hygiene_canonical_file() {
  local path="$1" parent
  parent="$(hygiene_canonical_dir "$(dirname -- "$path")")" || return 1
  printf '%s/%s\n' "$parent" "$(basename -- "$path")"
}

hygiene_path_has_symlink() {
  local path="$1" part current="" old_ifs="$IFS"
  IFS='/'
  # shellcheck disable=SC2086
  set -- $path
  IFS="$old_ifs"
  for part in "$@"; do
    [[ -n "$part" ]] || continue
    current="$current/$part"
    [[ ! -L "$current" ]] || return 0
  done
  return 1
}

hygiene_root_identity() {
  if stat -c '%d:%i' "$1" >/dev/null 2>&1; then
    stat -c '%d:%i' "$1"
  else
    stat -f '%d:%i' "$1"
  fi
}

hygiene_mode_has_group_or_other_write() {
  local mode permissions group other
  mode="$(hygiene_file_mode "$1")"
  [[ "$mode" =~ ^[0-7]{3,4}$ ]] || return 0
  permissions="${mode: -3}"
  group="${permissions:1:1}"
  other="${permissions:2:1}"
  (( (group & 2) != 0 || (other & 2) != 0 ))
}

hygiene_validate_fixed_directory() {
  local description="$1" path="$2" expected_path="$3" mode
  [[ "$path" == "$expected_path" ]] || hygiene_die "$description path is not the exact approved path"
  [[ -d "$path" && ! -L "$path" ]] || hygiene_die "$description must be a real directory"
  hygiene_path_has_symlink "$path" && hygiene_die "$description must not traverse a symlink"
  [[ "$(hygiene_canonical_dir "$path")" == "$expected_path" ]] || hygiene_die "$description canonical path changed"
  [[ "$(hygiene_file_owner "$path")" == "$(id -u)" ]] || hygiene_die "$description must be owned by the invoking user"
  mode="$(hygiene_file_mode "$path")"
  hygiene_mode_has_group_or_other_write "$path" && hygiene_die "$description must not be group or other writable"
  [[ "$mode" =~ ^[0-7]{3,4}$ ]] || hygiene_die "$description mode is unavailable"
}

hygiene_validate_fixed_scope() {
  local env_file="$1"
  [[ "$HYGIENE_ROOT" == "$HYGIENE_EXPECTED_ROOT" ]] || hygiene_die "Staging root guard changed"
  [[ "$env_file" == "$HYGIENE_EXPECTED_ROOT/config/.env.staging" ]] || hygiene_die "environment file must use the fixed Staging path"
  hygiene_validate_fixed_directory "Staging root" "$HYGIENE_ROOT" "$HYGIENE_EXPECTED_ROOT"
  hygiene_validate_fixed_directory "Staging config directory" "$HYGIENE_ROOT/config" "$HYGIENE_EXPECTED_ROOT/config"
  [[ -f "$env_file" && ! -L "$env_file" ]] || hygiene_die "Staging environment must be a regular non-symlink file"
  hygiene_path_has_symlink "$env_file" && hygiene_die "Staging environment must not traverse a symlink"
  [[ "$(hygiene_canonical_file "$env_file")" == "$env_file" ]] || hygiene_die "Staging environment canonical path changed"
  [[ "$(hygiene_file_owner "$env_file")" == "$(id -u)" ]] || hygiene_die "Staging environment must be owned by the invoking user"
  [[ "$(hygiene_file_mode "$env_file")" == "600" ]] || hygiene_die "Staging environment must use mode 0600"
}

hygiene_validate_env_syntax() {
  local file="$1" line line_number=0 key raw seen="|"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || hygiene_die "unsupported dotenv syntax at line $line_number"
    key="${BASH_REMATCH[1]}"
    raw="${BASH_REMATCH[2]}"
    [[ "$seen" != *"|$key|"* ]] || hygiene_die "duplicate dotenv key: $key"
    seen="${seen}${key}|"
    [[ "$raw" != *'$'* && "$raw" != *'`'* && "$raw" != *$'\n'* ]] || hygiene_die "dotenv interpolation is forbidden: $key"
    if [[ "$raw" == \"* ]]; then
      [[ ${#raw} -ge 2 && "${raw: -1}" == '"' ]] || hygiene_die "ambiguous double quote in dotenv value: $key"
      [[ "${raw:1:${#raw}-2}" != *'"'* && "${raw:1:${#raw}-2}" != *'\\'* ]] || hygiene_die "ambiguous double-quoted dotenv value: $key"
    elif [[ "$raw" == \'* ]]; then
      [[ ${#raw} -ge 2 && "${raw: -1}" == "'" ]] || hygiene_die "ambiguous single quote in dotenv value: $key"
      [[ "${raw:1:${#raw}-2}" != *"'"* ]] || hygiene_die "ambiguous single-quoted dotenv value: $key"
    else
      [[ "$raw" != *[[:space:]]* ]] || hygiene_die "unquoted dotenv values cannot contain whitespace: $key"
      [[ "$raw" != *'"'* && "$raw" != *"'"* ]] || hygiene_die "unquoted dotenv values cannot contain quotes: $key"
    fi
  done <"$file"
}

hygiene_env_value() {
  local file="$1" wanted="$2" line value
  line="$(awk -v wanted="$wanted" 'index($0, wanted "=") == 1 { print; exit }' "$file")"
  [[ -n "$line" ]] || return 1
  value="${line#*=}"
  if [[ "$value" == \"* || "$value" == \'* ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

hygiene_require_env_value() {
  local file="$1" key="$2" value
  value="$(hygiene_env_value "$file" "$key" || true)"
  [[ -n "$value" ]] || hygiene_die "$key must be set in the exact Staging environment"
  printf '%s' "$value"
}

hygiene_validate_env_identity() {
  local env_file="$1" project root sha
  hygiene_validate_env_syntax "$env_file"
  project="$(hygiene_require_env_value "$env_file" COMPOSE_PROJECT_NAME)"
  [[ "$project" == "$HYGIENE_EXPECTED_PROJECT" ]] || hygiene_die "Compose project must be $HYGIENE_EXPECTED_PROJECT"
  root="$(hygiene_require_env_value "$env_file" STAGING_ROOT)"
  [[ "$root" == "$HYGIENE_EXPECTED_ROOT" ]] || hygiene_die "environment Staging root must be $HYGIENE_EXPECTED_ROOT"
  sha="$(hygiene_require_env_value "$env_file" STAGING_COMMIT_SHA)"
  [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || hygiene_die "STAGING_COMMIT_SHA must be a lowercase full 40-character SHA"
  HYGIENE_CURRENT_SHA="$sha"
}

hygiene_validate_env_and_scope() {
  local env_file="$1"
  hygiene_validate_fixed_scope "$env_file"
  hygiene_validate_env_identity "$env_file"
}

hygiene_validate_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] || hygiene_usage_error "SHA must be a lowercase full 40-character SHA"
}

hygiene_validate_image_ref() {
  local reference="$1"
  [[ "$reference" =~ ^[A-Za-z0-9][A-Za-z0-9._/@:-]*$ ]] || hygiene_usage_error "image reference contains unsafe characters"
  [[ "$reference" != *[[:space:]]* ]] || hygiene_usage_error "image reference contains whitespace"
}

hygiene_validate_no_ambient_docker_overrides() {
  local key
  for key in DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG DOCKER_CERT_PATH DOCKER_TLS_VERIFY \
    DOCKER_API_VERSION DOCKER_DEFAULT_PLATFORM DOCKER_BUILDKIT COMPOSE_FILE \
    COMPOSE_PROJECT_NAME COMPOSE_ENV_FILES COMPOSE_PATH_SEPARATOR COMPOSE_PROFILES; do
    [[ -z "${!key+x}" ]] || hygiene_die "ambient Docker/Compose override is forbidden: $key"
  done
}

hygiene_validate_production_project() {
  local project="$1"
  [[ "$project" == "$HYGIENE_EXPECTED_PRODUCTION_PROJECT" ]] || hygiene_usage_error "Production project must be exactly $HYGIENE_EXPECTED_PRODUCTION_PROJECT"
  [[ "$project" != "$HYGIENE_EXPECTED_PROJECT" ]] || hygiene_usage_error "Production project must differ from Staging"
}

hygiene_validate_plan_file() {
  local path="$1" digest="$2" canonical parent
  [[ "$path" == "$HYGIENE_ROOT/evidence/"* ]] || hygiene_die "plan file must be directly under the exact Staging evidence directory"
  [[ -f "$path" && ! -L "$path" ]] || hygiene_die "plan file must be an existing regular non-symlink file"
  hygiene_path_has_symlink "$path" && hygiene_die "plan file must not traverse a symlink"
  parent="$(hygiene_canonical_dir "$(dirname -- "$path")")" || hygiene_die "plan directory cannot be canonicalized"
  [[ "$parent" == "$HYGIENE_ROOT/evidence" ]] || hygiene_die "plan file must be directly under the exact Staging evidence directory"
  canonical="$parent/$(basename -- "$path")"
  [[ "$canonical" == "$path" ]] || hygiene_die "plan file canonical path changed"
  [[ "$(hygiene_file_owner "$parent")" == "$(id -u)" && "$(hygiene_file_mode "$parent")" == "700" ]] || hygiene_die "Staging evidence directory must be owner-only mode 0700"
  [[ "$(hygiene_file_owner "$path")" == "$(id -u)" && "$(hygiene_file_mode "$path")" == "600" ]] || hygiene_die "plan file must be owner-only mode 0600"
  [[ "$digest" =~ ^[0-9a-f]{64}$ ]] || hygiene_usage_error "plan SHA-256 must be a lowercase 64-character digest"
  [[ "$(hygiene_file_digest "$path")" == "$digest" ]] || hygiene_die "plan file digest mismatch"
}

hygiene_require_command() {
  local name="$1" path
  path="$(command -v "$name" || true)"
  [[ "$path" == /* && -x "$path" ]] || hygiene_die "required command is unavailable: $name"
  printf '%s' "$path"
}

hygiene_validate_lock_parent() {
  local parent="$HYGIENE_ROOT/state"
  hygiene_validate_fixed_directory "Staging state directory" "$parent" "$HYGIENE_ROOT/state"
}

hygiene_acquire_lock() {
  local lock_file="$HYGIENE_ROOT/state/restaurant-pos-staging-hygiene.lock" flock_bin
  flock_bin="$(command -v flock || true)"
  [[ "$flock_bin" == /* && -x "$flock_bin" ]] || hygiene_die "flock is required for hygiene execute"
  hygiene_validate_lock_parent
  [[ ! -L "$lock_file" ]] || hygiene_die "hygiene lock path must not be a symlink"
  umask 077
  exec 9>>"$lock_file"
  chmod 600 "$lock_file"
  [[ "$(hygiene_file_owner "$lock_file")" == "$(id -u)" && "$(hygiene_file_mode "$lock_file")" == "600" ]] || hygiene_die "hygiene lock metadata is unsafe"
  "$flock_bin" -n 9 || hygiene_die "another Staging hygiene action is already running"
  HYGIENE_LOCK_FILE="$lock_file"
}

hygiene_emit_boundary() {
  printf 'BOUNDARY|STAGING_ROOT|%s\n' "$HYGIENE_ROOT"
  printf 'BOUNDARY|COMPOSE_PROJECT|%s\n' "$HYGIENE_EXPECTED_PROJECT"
  printf 'BOUNDARY|PRODUCTION|UNTOUCHED\n'
  printf 'BOUNDARY|VOLUME_DATABASE|UNTOUCHED\n'
  printf 'BOUNDARY|RUNTIME_START_STOP|UNTOUCHED\n'
}
