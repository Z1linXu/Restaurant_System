#!/usr/bin/env bash
set -Eeuo pipefail

# Materializes the exact candidate's reviewed release-rotation bundle from the
# dedicated Staging bare repository, then delegates unchanged arguments to it.
# It does not fetch, clone, build, start, migrate, or inspect Production.

EXPECTED_ROOT="/srv/restaurant-pos/staging"
REPOSITORY="$EXPECTED_ROOT/repository.git"
REPOSITORY_PATH="deployment/cloud/staging-release-control-bootstrap.sh"
EXPECTED_ORIGIN="https://github.com/Z1linXu/Restaurant_System.git"
APPROVED_SHA=""
ENV_FILE=""
CONTROL_ROOT=""
BUNDLE_ROOT=""
DELEGATE_PID=""
CONTROL_ROOT_IDENTITY=""
STATE_PARENT_IDENTITY=""
STATE_PARENT_MODE=""
ORIGINAL_ARGS=("$@")

usage() {
  cat <<'EOF'
Usage: materialize this exact file from the approved commit into
  /srv/restaurant-pos/staging/state/ops001-release-control.XXXXXX/
and invoke it with the same arguments accepted by staging-release-rotation.sh.

Required bindings include:
  --approved-sha <full-40-character-sha>
  --env-file /srv/restaurant-pos/staging/config/.env.staging

The dedicated bare repository must already be owner-only mode 0700, contain
the approved commit, and bind refs/remotes/origin/main to that exact commit.
This bootstrap verifies its own Git-blob identity, extracts only the approved
deployment/cloud tree into its private control root, rejects symlinks, invokes
the reviewed release helper, and removes the temporary control root on exit.
EOF
}

die() { printf 'OPS001_RELEASE_BOOTSTRAP|NO_GO|%s\n' "$1" >&2; exit 2; }

file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then stat -c '%a' "$1"; else stat -f '%Lp' "$1"; fi
}

file_owner() {
  if stat -c '%u' "$1" >/dev/null 2>&1; then stat -c '%u' "$1"; else stat -f '%u' "$1"; fi
}

file_identity() {
  if stat -c '%d:%i' "$1" >/dev/null 2>&1; then stat -c '%d:%i' "$1"; else stat -f '%d:%i' "$1"; fi
}

file_digest() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi
}

stream_digest() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum | awk '{print $1}'; else shasum -a 256 | awk '{print $1}'; fi
}

path_has_symlink() {
  local path="$1" current="" part old_ifs="$IFS"
  IFS='/'
  # shellcheck disable=SC2086
  set -- $path
  IFS="$old_ifs"
  for part in "$@"; do
    [[ -z "$part" ]] && continue
    current="$current/$part"
    [[ ! -L "$current" ]] || return 0
  done
  return 1
}

is_exact_control_root() {
  [[ "$(dirname "$1")" == "$EXPECTED_ROOT/state" &&
     "$(basename "$1")" =~ ^ops001-release-control\.[A-Za-z0-9]{6}$ ]]
}

cleanup() {
  local status=$? cleanup_error=""
  trap - EXIT ERR INT TERM
  if [[ -n "$CONTROL_ROOT" && -n "$CONTROL_ROOT_IDENTITY" && -n "$STATE_PARENT_IDENTITY" && -n "$STATE_PARENT_MODE" ]]; then
    if ! is_exact_control_root "$CONTROL_ROOT" ||
       [[ ! -d "$EXPECTED_ROOT/state" || -L "$EXPECTED_ROOT/state" ||
          "$(file_owner "$EXPECTED_ROOT/state")" != "$(id -u)" ||
          "$(file_mode "$EXPECTED_ROOT/state")" != "$STATE_PARENT_MODE" ||
          "$(file_identity "$EXPECTED_ROOT/state")" != "$STATE_PARENT_IDENTITY" ||
          ! -d "$CONTROL_ROOT" || -L "$CONTROL_ROOT" ||
          "$(file_owner "$CONTROL_ROOT")" != "$(id -u)" ||
          "$(file_mode "$CONTROL_ROOT")" != "700" ||
          "$(file_identity "$CONTROL_ROOT")" != "$CONTROL_ROOT_IDENTITY" ]]; then
      cleanup_error="private control-root identity changed; refusing unsafe removal"
    elif ! rm -rf -- "$CONTROL_ROOT" || [[ -e "$CONTROL_ROOT" ]]; then
      cleanup_error="private control-root removal failed"
    fi
  fi
  if [[ -n "$cleanup_error" ]]; then
    printf 'OPS001_RELEASE_BOOTSTRAP|NO_GO|%s\n' "$cleanup_error" >&2
    exit 2
  fi
  exit "$status"
}

handle_signal() {
  local signal="$1" status="$2"
  trap - INT TERM
  if [[ -n "$DELEGATE_PID" ]] && kill -0 "$DELEGATE_PID" 2>/dev/null; then
    kill -s "$signal" "$DELEGATE_PID" 2>/dev/null || true
    wait "$DELEGATE_PID" 2>/dev/null || true
  fi
  exit "$status"
}

parse_bindings() {
  local index=0 arg
  while [[ "$index" -lt "${#ORIGINAL_ARGS[@]}" ]]; do
    arg="${ORIGINAL_ARGS[$index]}"
    case "$arg" in
      --approved-sha|--env-file)
        index=$((index + 1))
        [[ "$index" -lt "${#ORIGINAL_ARGS[@]}" ]] || die "$arg requires a value"
        if [[ "$arg" == "--approved-sha" ]]; then
          [[ -z "$APPROVED_SHA" ]] || die "--approved-sha may appear once"
          APPROVED_SHA="${ORIGINAL_ARGS[$index]}"
        else
          [[ -z "$ENV_FILE" ]] || die "--env-file may appear once"
          ENV_FILE="${ORIGINAL_ARGS[$index]}"
        fi
        ;;
      --help|-h) usage; exit 0 ;;
    esac
    index=$((index + 1))
  done
  [[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]] || die "approved SHA must be a lowercase full SHA"
  [[ "$ENV_FILE" == "$EXPECTED_ROOT/config/.env.staging" ]] || die "environment file must use the fixed Staging path"
}

validate_control_root() {
  local self_path self_parent state_parent initial_entry initial_count
  self_path="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
  [[ "$(basename "$self_path")" == "staging-release-control-bootstrap.sh" ]] || die "bootstrap source must use the fixed filename"
  self_parent="$(dirname "$self_path")"
  state_parent="$(dirname "$self_parent")"
  CONTROL_ROOT="$self_parent"
  is_exact_control_root "$self_parent" || die "bootstrap must run from the fixed private control-root pattern"
  [[ "$state_parent" == "$EXPECTED_ROOT/state" && -d "$state_parent" && ! -L "$state_parent" ]] || die "Staging state parent must be the fixed real directory"
  path_has_symlink "$state_parent" && die "Staging state parent must not traverse a symlink"
  STATE_PARENT_MODE="$(file_mode "$state_parent")"
  [[ "$(file_owner "$state_parent")" == "$(id -u)" &&
     ( "$STATE_PARENT_MODE" == "700" || "$STATE_PARENT_MODE" == "750" ) ]] ||
    die "Staging state parent must be owner-owned mode 0700 or 0750"
  [[ -d "$self_parent" && ! -L "$self_parent" && -f "$self_path" && ! -L "$self_path" ]] || die "bootstrap control source must be a regular non-symlink file"
  path_has_symlink "$self_path" && die "bootstrap control source must not traverse a symlink"
  [[ "$(file_owner "$self_parent")" == "$(id -u)" && "$(file_mode "$self_parent")" == "700" ]] || die "bootstrap control root must be owner-only mode 0700"
  [[ "$(file_owner "$self_path")" == "$(id -u)" && "$(file_mode "$self_path")" == "700" ]] || die "bootstrap control source must be owner-only mode 0700"
  initial_count="$(find "$self_parent" -mindepth 1 -maxdepth 1 -print | wc -l | tr -d ' ')"
  initial_entry="$(find "$self_parent" -mindepth 1 -maxdepth 1 -print -quit)"
  [[ "$initial_count" == "1" && "$initial_entry" == "$self_path" ]] || die "bootstrap control root must initially contain only the fixed source"
  STATE_PARENT_IDENTITY="$(file_identity "$state_parent")"
  CONTROL_ROOT_IDENTITY="$(file_identity "$self_parent")"
  trap cleanup EXIT
  trap 'handle_signal INT 130' INT
  trap 'handle_signal TERM 143' TERM
}

validate_control_source_digest() {
  local self_path expected_digest
  self_path="$CONTROL_ROOT/$(basename "${BASH_SOURCE[0]}")"
  expected_digest="$(git --git-dir="$REPOSITORY" show "$APPROVED_SHA:$REPOSITORY_PATH" | stream_digest)" || die "approved bootstrap blob is unavailable"
  [[ "$(file_digest "$self_path")" == "$expected_digest" ]] || die "bootstrap source does not match the approved Git blob"
}

validate_repository() {
  local remote_main
  [[ "$-" != *x* ]] || die "shell tracing must be disabled"
  [[ -d "$REPOSITORY" && ! -L "$REPOSITORY" ]] || die "dedicated Staging repository is missing"
  path_has_symlink "$REPOSITORY" && die "dedicated Staging repository must not traverse symlinks"
  [[ "$(file_owner "$REPOSITORY")" == "$(id -u)" && "$(file_mode "$REPOSITORY")" == "700" ]] || die "dedicated Staging repository must be owner-only mode 0700"
  [[ "$(git --git-dir="$REPOSITORY" rev-parse --is-bare-repository 2>/dev/null || true)" == "true" ]] || die "dedicated Staging repository must be bare"
  [[ "$(git --git-dir="$REPOSITORY" remote 2>/dev/null || true)" == "origin" ]] || die "dedicated Staging repository must have exactly the expected origin remote"
  [[ "$(git --git-dir="$REPOSITORY" remote get-url origin 2>/dev/null || true)" == "$EXPECTED_ORIGIN" ]] || die "dedicated Staging repository origin URL mismatch"
  git --git-dir="$REPOSITORY" cat-file -e "$APPROVED_SHA^{commit}" 2>/dev/null || die "approved SHA is absent from the dedicated Staging repository"
  remote_main="$(git --git-dir="$REPOSITORY" rev-parse refs/remotes/origin/main 2>/dev/null || true)"
  [[ "$remote_main" == "$APPROVED_SHA" ]] || die "approved SHA must equal the dedicated repository origin/main"
}

extract_exact_bundle() {
  local relative extracted expected
  command -v tar >/dev/null 2>&1 || die "tar is required"
  BUNDLE_ROOT="$CONTROL_ROOT/bundle"
  mkdir -m 700 -- "$BUNDLE_ROOT"
  git --git-dir="$REPOSITORY" archive "$APPROVED_SHA" deployment/cloud | tar -x -C "$BUNDLE_ROOT"
  [[ -z "$(find "$BUNDLE_ROOT" -type l -print -quit)" ]] || die "approved deployment bundle contains a symlink"
  for relative in \
    deployment/cloud/staging-release-rotation.sh \
    deployment/cloud/staging-synthetic-acceptance.sh \
    deployment/cloud/staging-ops-common.sh; do
    extracted="$BUNDLE_ROOT/$relative"
    [[ -f "$extracted" && ! -L "$extracted" ]] || die "required approved release helper is missing"
    expected="$(git --git-dir="$REPOSITORY" show "$APPROVED_SHA:$relative" | stream_digest)"
    [[ "$(file_digest "$extracted")" == "$expected" ]] || die "extracted release helper digest mismatch"
  done
}

delegate_release_rotation() {
  local status
  "$BUNDLE_ROOT/deployment/cloud/staging-release-rotation.sh" "${ORIGINAL_ARGS[@]}" &
  DELEGATE_PID=$!
  if wait "$DELEGATE_PID"; then status=0; else status=$?; fi
  DELEGATE_PID=""
  return "$status"
}

main() {
  validate_control_root
  parse_bindings
  validate_repository
  validate_control_source_digest
  extract_exact_bundle
  delegate_release_rotation
}

main "$@"
