#!/usr/bin/env bash

# Shared fail-closed primitives for OPS-001 Staging tooling. This file is a
# library; callers retain set -Eeuo pipefail and their own cleanup traps.

OPS001_EXPECTED_ROOT="/srv/restaurant-pos/staging"
OPS001_EXPECTED_ENVIRONMENT="restaurant-pos-staging"
OPS001_MAX_APPROVAL_WINDOW_SECONDS=86400
OPS001_SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

ops001_die() {
  printf 'OPS001|NO_GO|%s\n' "$*" >&2
  exit 1
}

ops001_file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then stat -c '%a' "$1"; else stat -f '%Lp' "$1"; fi
}

ops001_file_owner() {
  if stat -c '%u' "$1" >/dev/null 2>&1; then stat -c '%u' "$1"; else stat -f '%u' "$1"; fi
}

ops001_file_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

ops001_string_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum -a 256 | awk '{print $1}'
  fi
}

ops001_canonical_dir() { (cd -P -- "$1" 2>/dev/null && pwd); }

ops001_canonical_file() {
  local parent
  parent="$(ops001_canonical_dir "$(dirname -- "$1")")" || return 1
  printf '%s/%s\n' "$parent" "$(basename -- "$1")"
}

ops001_path_has_symlink() {
  local path="$1" part current="" old_ifs="$IFS"
  IFS='/'; set -- $path; IFS="$old_ifs"
  for part in "$@"; do
    [[ -n "$part" ]] || continue
    current="$current/$part"
    [[ ! -L "$current" ]] || return 0
  done
  return 1
}

ops001_require_private_file() {
  local description="$1" path="$2" required_parent="${3:-}"
  [[ "$path" == /* && -f "$path" && ! -L "$path" ]] || ops001_die "$description must be an absolute regular non-symlink file"
  ops001_path_has_symlink "$path" && ops001_die "$description must not traverse a symlink"
  [[ "$(ops001_file_owner "$path")" == "$(id -u)" ]] || ops001_die "$description must be owned by the invoking user"
  [[ "$(ops001_file_mode "$path")" == "600" ]] || ops001_die "$description must use mode 0600"
  if [[ -n "$required_parent" ]]; then
    [[ "$(dirname -- "$(ops001_canonical_file "$path")")" == "$required_parent" ]] ||
      ops001_die "$description must be directly under $required_parent"
    [[ -d "$required_parent" && ! -L "$required_parent" && "$(ops001_file_owner "$required_parent")" == "$(id -u)" && "$(ops001_file_mode "$required_parent")" == "700" ]] ||
      ops001_die "$description parent directory must be owner-only mode 0700"
  fi
}

ops001_evidence_value() {
  local prefix="$1" file="$2" line
  line="$(awk -v marker="${prefix}|" 'index($0, marker) == 1 { print }' "$file")"
  [[ -n "$line" && "$(printf '%s\n' "$line" | wc -l | tr -d ' ')" == "1" ]] ||
    ops001_die "missing or duplicated binding: $prefix"
  printf '%s' "${line#${prefix}|}"
}

ops001_validate_env_file() {
  local path="$1" line line_number=0 key raw seen="|"
  ops001_require_private_file "Staging environment" "$path" "$OPS001_EXPECTED_ROOT/config"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1)); line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || ops001_die "unsupported dotenv syntax at line $line_number"
    key="${BASH_REMATCH[1]}"; raw="${BASH_REMATCH[2]}"
    [[ "$seen" != *"|$key|"* ]] || ops001_die "duplicate dotenv key: $key"
    seen="${seen}${key}|"
    [[ "$raw" != *'$'* && "$raw" != *'`'* && "$raw" != *$'\n'* ]] || ops001_die "dotenv interpolation is forbidden: $key"
  done <"$path"
}

ops001_env_value() {
  local file="$1" key="$2" line value
  line="$(grep -E "^${key}=" "$file" || true)"
  [[ -n "$line" ]] || return 1
  value="${line#*=}"
  if [[ "$value" == \"*\" || "$value" == \'*\' ]]; then value="${value:1:${#value}-2}"; fi
  printf '%s' "$value"
}

ops001_validate_fixed_env_identity() {
  local env_file="$1" approved_sha="$2"
  [[ "$(ops001_env_value "$env_file" COMPOSE_PROJECT_NAME || true)" == "$OPS001_EXPECTED_ENVIRONMENT" ]] ||
    ops001_die "environment project must be $OPS001_EXPECTED_ENVIRONMENT"
  [[ "$(ops001_env_value "$env_file" STAGING_ROOT || true)" == "$OPS001_EXPECTED_ROOT" ]] || ops001_die "environment root mismatch"
  [[ "$(ops001_env_value "$env_file" STAGING_COMMIT_SHA || true)" == "$approved_sha" ]] || ops001_die "environment SHA mismatch"
  [[ "$(ops001_env_value "$env_file" HTTP_BIND_ADDRESS || true)" == "127.0.0.1" ]] || ops001_die "Staging bind must remain loopback-only"
  [[ "$(ops001_env_value "$env_file" HTTP_PORT || true)" == "18080" ]] || ops001_die "Staging port must remain 18080"
  [[ "$(ops001_env_value "$env_file" STAGING_PRINT_MODE || true)" == "DISABLED" ]] || ops001_die "Staging printing must remain DISABLED"
  [[ "$(ops001_env_value "$env_file" STAGING_PRINTING_FEATURE_ENABLED || true)" == "false" ]] || ops001_die "Staging printing feature must remain false"
}

ops001_request_fingerprint() {
  local action="$1" approved_sha="$2" env_digest="$3" scope="$4"
  printf '%s\n' \
    "environment=$OPS001_EXPECTED_ENVIRONMENT" \
    "action=$action" \
    "approved_sha=$approved_sha" \
    "env_sha256=$env_digest" \
    "scope=$scope" | ops001_string_digest
}

ops001_validate_approval() {
  local approval="$1" approval_digest="$2" action="$3" approved_sha="$4" env_digest="$5" scope="$6"
  local now expires status environment bound_action bound_sha bound_env fingerprint reference
  ops001_require_private_file "Owner approval" "$approval" "$OPS001_EXPECTED_ROOT/evidence"
  [[ "$approval_digest" =~ ^[0-9a-f]{64}$ && "$(ops001_file_digest "$approval")" == "$approval_digest" ]] || ops001_die "Owner approval digest mismatch"
  status="$(ops001_evidence_value 'OPS001_APPROVAL|STATUS' "$approval")"
  environment="$(ops001_evidence_value 'OPS001_APPROVAL|ENVIRONMENT' "$approval")"
  expires="$(ops001_evidence_value 'OPS001_APPROVAL|EXPIRES_AT_EPOCH' "$approval")"
  bound_action="$(ops001_evidence_value 'OPS001_APPROVAL|ACTION' "$approval")"
  bound_sha="$(ops001_evidence_value 'OPS001_APPROVAL|APPROVED_SHA' "$approval")"
  bound_env="$(ops001_evidence_value 'OPS001_APPROVAL|ENV_SHA256' "$approval")"
  fingerprint="$(ops001_evidence_value 'OPS001_APPROVAL|REQUEST_FINGERPRINT' "$approval")"
  reference="$(ops001_evidence_value 'OPS001_APPROVAL|REFERENCE' "$approval")"
  now="$(date +%s)"
  [[ "$status" == "OWNER_APPROVED" && "$environment" == "$OPS001_EXPECTED_ENVIRONMENT" ]] || ops001_die "Owner approval status/environment mismatch"
  [[ "$expires" =~ ^[0-9]{10}$ && "$expires" -ge "$now" && "$expires" -le $((now + OPS001_MAX_APPROVAL_WINDOW_SECONDS)) ]] || ops001_die "Owner approval is expired or exceeds 24 hours"
  [[ "$bound_action" == "$action" && "$bound_sha" == "$approved_sha" && "$bound_env" == "$env_digest" ]] || ops001_die "Owner approval action/SHA/environment binding mismatch"
  [[ "$fingerprint" == "$(ops001_request_fingerprint "$action" "$approved_sha" "$env_digest" "$scope")" ]] || ops001_die "Owner approval request fingerprint mismatch"
  [[ "$reference" =~ ^[A-Za-z0-9_.:/#-]+$ ]] || ops001_die "Owner approval reference is invalid"
  OPS001_VALIDATED_APPROVAL_SHA256="$approval_digest"
}

ops001_consume_approval() {
  local state_parent="$OPS001_EXPECTED_ROOT/state" state_dir="$OPS001_EXPECTED_ROOT/state/ops001-approvals" marker
  [[ -n "${OPS001_VALIDATED_APPROVAL_SHA256:-}" ]] || ops001_die "Owner approval was not validated"
  [[ -d "$state_parent" && ! -L "$state_parent" ]] || ops001_die "Staging state directory is unavailable"
  ops001_path_has_symlink "$state_parent" && ops001_die "Staging state directory must not traverse symlinks"
  [[ "$(ops001_canonical_dir "$state_parent")" == "$state_parent" && "$(ops001_file_owner "$state_parent")" == "$(id -u)" ]] || ops001_die "Staging state directory identity is unsafe"
  if [[ -e "$state_dir" ]]; then
    [[ -d "$state_dir" && ! -L "$state_dir" ]] || ops001_die "approval-consumption path must be a real directory"
    ops001_path_has_symlink "$state_dir" && ops001_die "approval-consumption directory must not traverse symlinks"
  fi
  mkdir -p -- "$state_dir"
  chmod 700 "$state_dir"
  [[ "$(ops001_canonical_dir "$state_dir")" == "$state_dir" && "$(ops001_file_owner "$state_dir")" == "$(id -u)" && "$(ops001_file_mode "$state_dir")" == "700" ]] || ops001_die "approval-consumption directory is not private"
  marker="$state_dir/$OPS001_VALIDATED_APPROVAL_SHA256"
  [[ ! -e "$marker" && ! -L "$marker" ]] || ops001_die "Owner approval was already consumed"
  umask 077
  ( set -o noclobber; printf 'OPS001_APPROVAL_CONSUMED|%s\n' "$(date +%s)" >"$marker" ) 2>/dev/null || ops001_die "Owner approval was concurrently consumed"
  chmod 600 "$marker"
}

ops001_assert_approval_unchanged() {
  [[ -n "${OPS001_APPROVAL_FILE:-}" && -n "${OPS001_VALIDATED_APPROVAL_SHA256:-}" ]] || ops001_die "approval snapshot is unavailable"
  [[ "$(ops001_file_digest "$OPS001_APPROVAL_FILE")" == "$OPS001_VALIDATED_APPROVAL_SHA256" ]] || ops001_die "Owner approval changed after validation"
}
