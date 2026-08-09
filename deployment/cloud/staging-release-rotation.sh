#!/usr/bin/env bash
set -Eeuo pipefail

# Creates an exact detached release from the dedicated Staging repository and
# atomically rotates only the four non-secret release identity fields. It does
# not fetch, clone, build, start, migrate, or access Production.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-synthetic-acceptance.sh
source "$SCRIPT_DIR/staging-synthetic-acceptance.sh"
# shellcheck source=staging-ops-common.sh
source "$SCRIPT_DIR/staging-ops-common.sh"

ACTION="validate"
EXECUTE_RUNTIME="false"
APPROVED_SHA=""
ENV_FILE=""
APPROVAL_FILE=""
APPROVAL_SHA256=""
REPOSITORY="$OPS001_EXPECTED_ROOT/repository.git"
ENV_DIGEST=""
RELEASE_DIR=""
RECOVERY_FILE=""
RECOVERY_DIGEST=""
ROTATION_MARKER=""
ROTATION_STATE="NONE"
NEXT_ENV_FILE=""
RELEASES_DIR="$OPS001_EXPECTED_ROOT/releases"
RELEASES_DIR_MODE=""
RELEASES_DIR_IDENTITY=""
STATE_DIR="$OPS001_EXPECTED_ROOT/state"
STATE_DIR_MODE=""
STATE_DIR_IDENTITY=""
RECOVERY_BLOCK_MARKER_DIGEST=""
RECOVERY_BLOCK_LOCK_DIGEST=""
ROTATED_ENV_DIGEST=""
ROTATION_RECORD_DIGEST=""

usage() {
  cat <<'EOF'
Usage:
  staging-release-rotation.sh --validate --approved-sha <full-sha> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging
  staging-release-rotation.sh --execute-runtime --action prepare-release-env \
    --approved-sha <full-sha> --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --approval <private-file> --approval-sha256 <sha256>
  staging-release-rotation.sh --execute-runtime --action prepare-recovery-release-env \
    --approved-sha <full-sha> --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --approval <private-file> --approval-sha256 <sha256>

Default/--validate is read-only. prepare-release-env requires an action/SHA/
environment-bound one-use Owner approval. It uses the existing dedicated bare
Staging repository; it never fetches, clones, builds, starts, runs Flyway, or
prints environment values. Only STAGING_COMMIT_SHA, BACKEND_IMAGE,
FRONTEND_IMAGE, and VITE_APP_BUILD_VERSION may change.

prepare-recovery-release-env is the narrower prerequisite for a retained
AL-003S blocked state: it requires the exact reviewed marker and lock record,
binds both digests into its one-use approval, leaves both byte-for-byte
unchanged, and only prepares the repaired exact release/environment. It does
not clear blocked state or authorize a later runtime/data action.
EOF
}

cleanup() {
  local status=$?
  trap - EXIT ERR INT TERM
  if [[ "$ROTATION_STATE" == "ROTATED" ]]; then
    if restore_prior_environment; then
      [[ -z "$ROTATION_MARKER" ]] || printf 'OPS001_ENV_ROTATION|STATUS|RESTORED_BY_EXIT_GUARD\n' >>"$ROTATION_MARKER"
    else
      persist_rotation_blocked || true
    fi
  fi
  [[ -z "$NEXT_ENV_FILE" || ! -e "$NEXT_ENV_FILE" ]] || rm -f -- "$NEXT_ENV_FILE"
  if [[ "${ACTION_LOCK_FD:-}" == "9" && -n "${FLOCK_BIN:-}" ]]; then
    "$FLOCK_BIN" -u "$ACTION_LOCK_FD" >/dev/null 2>&1 || true
    exec 9>&-
    ACTION_LOCK_FD=""
  fi
  return "$status"
}

handle_interrupt() { exit 130; }
handle_terminate() { exit 143; }

persist_rotation_blocked() {
  local marker="$OPS001_EXPECTED_ROOT/state/ops001-env-rotation.blocked"
  [[ -d "$OPS001_EXPECTED_ROOT/state" && ! -L "$OPS001_EXPECTED_ROOT/state" && ! -L "$marker" ]] || return 1
  umask 077
  printf 'OPS001_ENV_ROTATION_BLOCKED|%s|%s\n' "$APPROVED_SHA" "$(date +%s)" >"$marker" && chmod 600 "$marker"
}

restore_prior_environment() {
  local restore_file="$OPS001_EXPECTED_ROOT/config/.env.staging.restore"
  [[ -n "$RECOVERY_FILE" && -n "$RECOVERY_DIGEST" && -f "$RECOVERY_FILE" && ! -L "$RECOVERY_FILE" ]] || return 1
  [[ "$(ops001_file_digest "$RECOVERY_FILE")" == "$RECOVERY_DIGEST" ]] || return 1
  cp -- "$RECOVERY_FILE" "$restore_file" || return 1
  chmod 600 "$restore_file" || return 1
  mv -f -- "$restore_file" "$ENV_FILE" || return 1
  [[ "$(ops001_file_digest "$ENV_FILE")" == "$RECOVERY_DIGEST" ]] || return 1
  ROTATION_STATE="RESTORED"
}

validate_repository() {
  local remote_main
  [[ "$REPOSITORY" == "$OPS001_EXPECTED_ROOT/repository.git" ]] || ops001_die "dedicated Staging repository path changed"
  [[ -d "$REPOSITORY" && ! -L "$REPOSITORY" ]] || ops001_die "dedicated Staging bare repository is missing"
  ops001_path_has_symlink "$REPOSITORY" && ops001_die "dedicated Staging repository must not traverse symlinks"
  [[ "$(ops001_file_owner "$REPOSITORY")" == "$(id -u)" ]] || ops001_die "dedicated Staging repository must be owned by the invoking user"
  [[ "$(ops001_file_mode "$REPOSITORY")" == "700" ]] || ops001_die "dedicated Staging repository must use mode 0700"
  [[ "$(git --git-dir="$REPOSITORY" rev-parse --is-bare-repository 2>/dev/null || true)" == "true" ]] || ops001_die "dedicated Staging repository must be bare"
  git --git-dir="$REPOSITORY" cat-file -e "$APPROVED_SHA^{commit}" 2>/dev/null || ops001_die "approved SHA is absent from the dedicated Staging repository"
  remote_main="$(git --git-dir="$REPOSITORY" rev-parse refs/remotes/origin/main 2>/dev/null || true)"
  [[ "$remote_main" == "$APPROVED_SHA" ]] || ops001_die "approved SHA must equal the dedicated repository origin/main"
}

release_root_identity() {
  if stat -c '%d:%i' "$1" >/dev/null 2>&1; then stat -c '%d:%i' "$1"; else stat -f '%d:%i' "$1"; fi
}

validate_releases_root() {
  [[ "$RELEASES_DIR" == "$OPS001_EXPECTED_ROOT/releases" ]] || ops001_die "Staging releases directory path changed"
  [[ -d "$RELEASES_DIR" && ! -L "$RELEASES_DIR" ]] || ops001_die "Staging releases directory is unavailable"
  ops001_path_has_symlink "$RELEASES_DIR" && ops001_die "Staging releases directory must not traverse symlinks"
  [[ "$(ops001_canonical_dir "$RELEASES_DIR")" == "$RELEASES_DIR" ]] || ops001_die "Staging releases directory must use the fixed canonical path"
  RELEASES_DIR_MODE="$(ops001_file_mode "$RELEASES_DIR")"
  [[ "$(ops001_file_owner "$RELEASES_DIR")" == "$(id -u)" &&
     ( "$RELEASES_DIR_MODE" == "700" || "$RELEASES_DIR_MODE" == "750" ) ]] ||
    ops001_die "Staging releases directory must be owner-owned mode 0700 or 0750"
  RELEASES_DIR_IDENTITY="$(release_root_identity "$RELEASES_DIR")"
}

assert_releases_root_unchanged() {
  [[ -n "$RELEASES_DIR_MODE" && -n "$RELEASES_DIR_IDENTITY" &&
     -d "$RELEASES_DIR" && ! -L "$RELEASES_DIR" ]] || ops001_die "Staging releases directory identity is unavailable"
  ops001_path_has_symlink "$RELEASES_DIR" && ops001_die "Staging releases directory identity changed"
  [[ "$(ops001_canonical_dir "$RELEASES_DIR")" == "$RELEASES_DIR" &&
     "$(ops001_file_owner "$RELEASES_DIR")" == "$(id -u)" &&
     "$(ops001_file_mode "$RELEASES_DIR")" == "$RELEASES_DIR_MODE" &&
     "$(release_root_identity "$RELEASES_DIR")" == "$RELEASES_DIR_IDENTITY" ]] ||
    ops001_die "Staging releases directory identity changed"
}

state_root_identity() {
  if stat -c '%d:%i' "$1" >/dev/null 2>&1; then stat -c '%d:%i' "$1"; else stat -f '%d:%i' "$1"; fi
}

validate_state_root() {
  [[ "$STATE_DIR" == "$OPS001_EXPECTED_ROOT/state" ]] || ops001_die "Staging state directory path changed"
  [[ -d "$STATE_DIR" && ! -L "$STATE_DIR" ]] || ops001_die "Staging state directory must be a real directory"
  ops001_path_has_symlink "$STATE_DIR" && ops001_die "Staging state directory must not traverse symlinks"
  [[ "$(ops001_canonical_dir "$STATE_DIR")" == "$STATE_DIR" ]] || ops001_die "Staging state directory must use the fixed canonical path"
  STATE_DIR_MODE="$(ops001_file_mode "$STATE_DIR")"
  [[ "$(ops001_file_owner "$STATE_DIR")" == "$(id -u)" &&
     ( "$STATE_DIR_MODE" == "700" || "$STATE_DIR_MODE" == "750" ) ]] ||
    ops001_die "Staging state directory must be owner-owned mode 0700 or 0750"
  STATE_DIR_IDENTITY="$(state_root_identity "$STATE_DIR")"
}

assert_state_root_unchanged() {
  [[ -n "$STATE_DIR_MODE" && -n "$STATE_DIR_IDENTITY" && -d "$STATE_DIR" && ! -L "$STATE_DIR" ]] ||
    ops001_die "Staging state directory identity is unavailable"
  ops001_path_has_symlink "$STATE_DIR" && ops001_die "Staging state directory identity changed"
  [[ "$(ops001_canonical_dir "$STATE_DIR")" == "$STATE_DIR" &&
     "$(ops001_file_owner "$STATE_DIR")" == "$(id -u)" &&
     "$(ops001_file_mode "$STATE_DIR")" == "$STATE_DIR_MODE" &&
     "$(state_root_identity "$STATE_DIR")" == "$STATE_DIR_IDENTITY" ]] ||
    ops001_die "Staging state directory identity changed"
}

validate_inputs() {
  [[ "$-" != *x* ]] || ops001_die "shell tracing must be disabled"
  [[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]] || ops001_die "approved SHA must be a lowercase full 40-character SHA"
  [[ "$ENV_FILE" == "$OPS001_EXPECTED_ROOT/config/.env.staging" ]] || ops001_die "environment file must use the fixed Staging path"
  ops001_validate_env_file "$ENV_FILE"
  ENV_DIGEST="$(ops001_file_digest "$ENV_FILE")"
  RELEASES_DIR="$OPS001_EXPECTED_ROOT/releases"
  STATE_DIR="$OPS001_EXPECTED_ROOT/state"
  RELEASE_DIR="$RELEASES_DIR/$APPROVED_SHA"
  validate_state_root
  validate_releases_root
  validate_repository
}

assert_no_pending_rotation() {
  local recovery_dir="$OPS001_EXPECTED_ROOT/state/ops001-env-recovery" record
  [[ -e "$OPS001_EXPECTED_ROOT/state/ops001-env-rotation.blocked" ]] && ops001_die "environment rotation is blocked pending Owner recovery"
  [[ -d "$recovery_dir" ]] || return 0
  for record in "$recovery_dir"/*.record; do
    [[ -e "$record" ]] || continue
    if grep -Fxq 'OPS001_ENV_ROTATION|STATUS|PREPARED' "$record" &&
       ! grep -Eq '^OPS001_ENV_ROTATION\|STATUS\|(COMMITTED|RESTORED_)' "$record"; then
      ops001_die "an incomplete environment rotation requires Owner recovery"
    fi
  done
}

assert_recovery_blocked_state() {
  local marker="$STATE_DIR/al003s-acceptance.blocked" lock="$STATE_DIR/al003s-acceptance.lock"
  local marker_line lock_first_line lock_second_line marker_digest lock_digest
  local marker_bytes lock_bytes lock_lines expected_two_line_bytes path valid_lock="false"
  for path in "$marker" "$lock"; do
    [[ -f "$path" && ! -L "$path" ]] || ops001_die "recovery release requires both retained AL-003S blocked records"
    ops001_path_has_symlink "$path" && ops001_die "retained AL-003S blocked records must not traverse symlinks"
    [[ "$(ops001_file_owner "$path")" == "$(id -u)" && "$(ops001_file_mode "$path")" == "600" ]] ||
      ops001_die "retained AL-003S blocked records must be owner-owned mode 0600"
  done
  marker_line="$(sed -n '1p' "$marker")"
  lock_first_line="$(sed -n '1p' "$lock")"
  lock_second_line="$(sed -n '2p' "$lock")"
  marker_bytes="$(wc -c <"$marker" | tr -d ' ')"
  lock_bytes="$(wc -c <"$lock" | tr -d ' ')"
  lock_lines="$(wc -l <"$lock" | tr -d ' ')"
  [[ "$marker_line" =~ ^AL003S_BLOCKED\|[A-Za-z0-9_]+$ &&
     "$marker_bytes" == "$(( ${#marker_line} + 1 ))" ]] ||
    ops001_die "retained AL-003S blocked record identity is invalid or mismatched"
  if [[ "$lock_lines" == "1" && "$lock_first_line" == "$marker_line" &&
        "$lock_bytes" == "$(( ${#marker_line} + 1 ))" ]]; then
    valid_lock="true"
  else
    expected_two_line_bytes="$(( ${#lock_first_line} + ${#marker_line} + 2 ))"
    [[ "$lock_lines" == "2" &&
       "$lock_first_line" == "AL003S_BLOCKED|scoped_container_cleanup_failed" &&
       "$lock_second_line" == "$marker_line" &&
       "$lock_bytes" == "$expected_two_line_bytes" ]] && valid_lock="true"
  fi
  [[ "$valid_lock" == "true" ]] ||
    ops001_die "retained AL-003S blocked record identity is invalid or mismatched"
  marker_digest="$(ops001_file_digest "$marker")"
  lock_digest="$(ops001_file_digest "$lock")"
  if [[ -z "$RECOVERY_BLOCK_MARKER_DIGEST$RECOVERY_BLOCK_LOCK_DIGEST" ]]; then
    RECOVERY_BLOCK_MARKER_DIGEST="$marker_digest"
    RECOVERY_BLOCK_LOCK_DIGEST="$lock_digest"
  else
    [[ "$marker_digest" == "$RECOVERY_BLOCK_MARKER_DIGEST" && "$lock_digest" == "$RECOVERY_BLOCK_LOCK_DIGEST" ]] ||
      ops001_die "retained AL-003S blocked records changed during release preparation"
  fi
}

assert_release() {
  [[ -d "$RELEASE_DIR" && ! -L "$RELEASE_DIR" ]] || ops001_die "exact detached release is missing"
  ops001_path_has_symlink "$RELEASE_DIR" && ops001_die "exact detached release must not traverse symlinks"
  [[ "$(git -C "$RELEASE_DIR" rev-parse HEAD 2>/dev/null || true)" == "$APPROVED_SHA" ]] || ops001_die "detached release HEAD mismatch"
  [[ -z "$(git -C "$RELEASE_DIR" status --porcelain=v1 --untracked-files=all)" ]] || ops001_die "detached release is not clean"
}

create_detached_release() {
  assert_releases_root_unchanged
  [[ ! -e "$RELEASE_DIR" && ! -L "$RELEASE_DIR" ]] || ops001_die "exact release path already exists; refusing overwrite"
  umask 077
  git --git-dir="$REPOSITORY" worktree add --detach "$RELEASE_DIR" "$APPROVED_SHA" >/dev/null
  assert_releases_root_unchanged
  chmod 700 "$RELEASE_DIR"
  assert_release
}

replace_identity_fields() {
  local source="$1" destination="$2" key line seen="|" value
  : >"$destination"; chmod 600 "$destination"
  while IFS= read -r line || [[ -n "$line" ]]; do
    key="${line%%=*}"
    case "$key" in
      STAGING_COMMIT_SHA) value="$APPROVED_SHA" ;;
      BACKEND_IMAGE)
        value="$(ops001_env_value "$source" BACKEND_IMAGE)"
        value="${value%:*}:staging-$APPROVED_SHA"
        ;;
      FRONTEND_IMAGE)
        value="$(ops001_env_value "$source" FRONTEND_IMAGE)"
        value="${value%:*}:staging-$APPROVED_SHA"
        ;;
      VITE_APP_BUILD_VERSION) value="staging-$APPROVED_SHA" ;;
      *) printf '%s\n' "$line" >>"$destination"; continue ;;
    esac
    [[ "$seen" != *"|$key|"* ]] || ops001_die "duplicate identity field: $key"
    seen="${seen}${key}|"
    printf '%s=%s\n' "$key" "$value" >>"$destination"
  done <"$source"
  for key in STAGING_COMMIT_SHA BACKEND_IMAGE FRONTEND_IMAGE VITE_APP_BUILD_VERSION; do
    [[ "$seen" == *"|$key|"* ]] || ops001_die "missing required identity field: $key"
  done
}

assert_only_identity_changed() {
  local old="$1" new="$2" key
  for key in $(awk -F= '/^[A-Z][A-Z0-9_]*=/{print $1}' "$old"); do
    case "$key" in STAGING_COMMIT_SHA|BACKEND_IMAGE|FRONTEND_IMAGE|VITE_APP_BUILD_VERSION) continue ;; esac
    [[ "$(grep -E "^${key}=" "$old")" == "$(grep -E "^${key}=" "$new")" ]] || ops001_die "non-identity environment field changed: $key"
  done
  ops001_validate_fixed_env_identity "$new" "$APPROVED_SHA"
  [[ "$(ops001_env_value "$new" BACKEND_IMAGE)" == *":staging-$APPROVED_SHA" ]] || ops001_die "backend image identity mismatch"
  [[ "$(ops001_env_value "$new" FRONTEND_IMAGE)" == *":staging-$APPROVED_SHA" ]] || ops001_die "frontend image identity mismatch"
  [[ "$(ops001_env_value "$new" VITE_APP_BUILD_VERSION)" == "staging-$APPROVED_SHA" ]] || ops001_die "frontend build identity mismatch"
}

rotate_environment() {
  local recovery_dir="$STATE_DIR/ops001-env-recovery" prior_digest next_digest marker
  assert_state_root_unchanged
  if [[ -e "$recovery_dir" || -L "$recovery_dir" ]]; then
    [[ -d "$recovery_dir" && ! -L "$recovery_dir" ]] || ops001_die "environment recovery directory must be a real directory"
    ops001_path_has_symlink "$recovery_dir" && ops001_die "environment recovery directory must not traverse symlinks"
  else
    mkdir -m 700 -- "$recovery_dir"
  fi
  [[ "$(ops001_canonical_dir "$recovery_dir")" == "$recovery_dir" && "$(ops001_file_owner "$recovery_dir")" == "$(id -u)" && "$(ops001_file_mode "$recovery_dir")" == "700" ]] ||
    ops001_die "environment recovery directory is not the fixed private directory"
  umask 077
  RECOVERY_FILE="$(mktemp "$recovery_dir/$(date +%s)-$APPROVED_SHA.env.XXXXXX")"
  cp -- "$ENV_FILE" "$RECOVERY_FILE"; chmod 600 "$RECOVERY_FILE"
  prior_digest="$(ops001_file_digest "$RECOVERY_FILE")"
  RECOVERY_DIGEST="$prior_digest"
  [[ "$prior_digest" == "$ENV_DIGEST" ]] || ops001_die "environment changed while creating recovery snapshot"
  NEXT_ENV_FILE="$(mktemp "$OPS001_EXPECTED_ROOT/config/.env.staging.next.XXXXXX")"
  replace_identity_fields "$RECOVERY_FILE" "$NEXT_ENV_FILE"
  assert_only_identity_changed "$RECOVERY_FILE" "$NEXT_ENV_FILE"
  next_digest="$(ops001_file_digest "$NEXT_ENV_FILE")"
  ops001_assert_approval_unchanged
  marker="$recovery_dir/$(basename "$RECOVERY_FILE").record"
  ROTATION_MARKER="$marker"
  printf 'OPS001_ENV_ROTATION|STATUS|PREPARED\nOPS001_ENV_ROTATION|APPROVED_SHA|%s\nOPS001_ENV_ROTATION|PRIOR_SHA256|%s\nOPS001_ENV_ROTATION|CURRENT_SHA256|%s\n' "$APPROVED_SHA" "$prior_digest" "$next_digest" >"$marker"
  chmod 600 "$marker"
  ROTATION_STATE="PREPARED"
  assert_state_root_unchanged
  [[ "$ACTION" != "prepare-recovery-release-env" ]] || assert_recovery_blocked_state
  mv -f -- "$NEXT_ENV_FILE" "$ENV_FILE"; NEXT_ENV_FILE=""
  ROTATION_STATE="ROTATED"
  chmod 600 "$ENV_FILE"
  if [[ "$(ops001_file_digest "$ENV_FILE")" != "$next_digest" ]]; then
    if restore_prior_environment; then
      printf 'OPS001_ENV_ROTATION|STATUS|RESTORED_AFTER_DIGEST_FAILURE\n' >>"$marker"
      ops001_die "atomic environment rotation digest mismatch; prior environment restored"
    fi
    persist_rotation_blocked || true
    printf 'OPS001_ENV_ROTATION|STATUS|BLOCKED_AFTER_DIGEST_FAILURE\n' >>"$marker" 2>/dev/null || true
    ops001_die "atomic environment rotation digest mismatch; recovery failed and Owner recovery is required"
  fi
  if ! "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --validate --env-file "$ENV_FILE" >/dev/null; then
    if restore_prior_environment; then
      printf 'OPS001_ENV_ROTATION|STATUS|RESTORED_AFTER_VALIDATION_FAILURE\n' >>"$marker"
      ops001_die "official Staging validation failed; prior environment restored"
    fi
    persist_rotation_blocked || true
    printf 'OPS001_ENV_ROTATION|STATUS|BLOCKED_AFTER_VALIDATION_FAILURE\n' >>"$marker" 2>/dev/null || true
    ops001_die "official Staging validation failed; recovery failed and Owner recovery is required"
  fi
  ops001_assert_approval_unchanged
  [[ "$ACTION" != "prepare-recovery-release-env" ]] || assert_recovery_blocked_state
  printf 'OPS001_ENV_ROTATION|STATUS|COMMITTED\n' >>"$marker"
  ROTATION_STATE="COMMITTED"
  ROTATED_ENV_DIGEST="$next_digest"
  ROTATION_RECORD_DIGEST="$(ops001_file_digest "$marker")"
}

run_prepare() {
  local scope="repository=$REPOSITORY;release=$RELEASE_DIR;identity_fields=4"
  if [[ "$ACTION" == "prepare-recovery-release-env" ]]; then
    # This action is only the exact release prerequisite for a separately
    # approved recovery. It serializes with every AL-003S action while leaving
    # the reviewed blocked records authoritative and byte-for-byte unchanged.
    acquire_staging_serialization_lock
    assert_recovery_blocked_state
    scope="${scope};blocked_marker_sha256=$RECOVERY_BLOCK_MARKER_DIGEST;blocked_lock_sha256=$RECOVERY_BLOCK_LOCK_DIGEST"
  else
    acquire_action_lock
  fi
  assert_state_root_unchanged
  assert_releases_root_unchanged
  assert_no_pending_rotation
  ops001_validate_approval "$APPROVAL_FILE" "$APPROVAL_SHA256" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$scope"
  OPS001_APPROVAL_FILE="$APPROVAL_FILE"
  ops001_assert_approval_unchanged
  ops001_consume_approval
  [[ "$ACTION" != "prepare-recovery-release-env" ]] || assert_recovery_blocked_state
  create_detached_release
  [[ "$ACTION" != "prepare-recovery-release-env" ]] || assert_recovery_blocked_state
  rotate_environment
  printf 'OPS001_RELEASE_ENV|PASS|approved_sha=%s|env_sha256=%s|recovery_record_sha256=%s\n' \
    "$APPROVED_SHA" "$ROTATED_ENV_DIGEST" "$ROTATION_RECORD_DIGEST"
}

main() {
  local seen="|"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate) ACTION="validate" ;;
      --execute-runtime) EXECUTE_RUNTIME="true" ;;
      --action|--approved-sha|--env-file|--approval|--approval-sha256)
        [[ $# -ge 2 && "$seen" != *"|$1|"* ]] || ops001_die "$1 requires one value and may appear once"
        seen="${seen}${1}|"
        case "$1" in --action) ACTION="$2" ;; --approved-sha) APPROVED_SHA="$2" ;; --env-file) ENV_FILE="$2" ;; --approval) APPROVAL_FILE="$2" ;; --approval-sha256) APPROVAL_SHA256="$2" ;; esac
        shift ;;
      --help|-h) usage; exit 0 ;;
      *) ops001_die "unsupported option: $1" ;;
    esac
    shift
  done
  [[ -n "$APPROVED_SHA" && -n "$ENV_FILE" ]] || ops001_die "approved SHA and environment file are required"
  validate_inputs
  case "$ACTION" in
    validate)
      [[ "$EXECUTE_RUNTIME" == "false" && -z "$APPROVAL_FILE$APPROVAL_SHA256" ]] || ops001_die "validation accepts no runtime/approval gate"
      if [[ -e "$RELEASE_DIR" ]]; then assert_release; fi
      printf 'OPS001_RELEASE_ENV|VALIDATE|PASS|no state changed\n'
      ;;
    prepare-release-env|prepare-recovery-release-env)
      [[ "$EXECUTE_RUNTIME" == "true" ]] || ops001_die "$ACTION requires --execute-runtime"
      [[ -n "$APPROVAL_FILE" && "$APPROVAL_SHA256" =~ ^[0-9a-f]{64}$ ]] || ops001_die "$ACTION requires one action-specific Owner approval"
      FLOCK_BIN="$(command -v flock || true)"
      [[ "$FLOCK_BIN" == /* && -x "$FLOCK_BIN" ]] || ops001_die "flock is required for the shared Staging action lock"
      run_prepare
      ;;
    *) ops001_die "unsupported action: $ACTION" ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap cleanup EXIT
  trap handle_interrupt INT
  trap handle_terminate TERM
  main "$@"
fi
