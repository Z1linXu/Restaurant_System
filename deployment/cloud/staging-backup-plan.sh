#!/usr/bin/env bash
set -euo pipefail

# STG-006 preparation-only helper. It never invokes backup or database tools.

EXPECTED_ROOT="/srv/restaurant-pos/staging"
ACTION="help"
ACTION_SELECTED="false"
STAGING_ROOT=""
BACKUP_DIR=""

usage() {
  cat <<'EOF'
Usage:
  ./staging-backup-plan.sh --dry-run --root /srv/restaurant-pos/staging --backup-dir /srv/restaurant-pos/staging/backups
  ./staging-backup-plan.sh --inspect-existing --root /srv/restaurant-pos/staging --backup-dir /srv/restaurant-pos/staging/backups
  ./staging-backup-plan.sh --help

This is a planning and metadata-only helper. It never runs pg_dump, pg_restore,
psql, docker exec, hashes backup contents, creates files, creates directories,
or deletes data.
EOF
}

die() { printf 'ERROR|%s\n' "$*" >&2; exit 2; }

string_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s' "$1" | sha256sum | awk '{print $1}'
  else
    printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
  fi
}

absolute_without_traversal() {
  [[ "$1" == /* && "$1" != *$'\n'* && "$1" != *'/./'* && "$1" != *'/../'* && "$1" != *'..' ]]
}

has_symlink_component() {
  local path="$1" current="/" component old_ifs="$IFS"
  IFS='/'; set -- $path; IFS="$old_ifs"
  for component in "$@"; do
    [[ -z "$component" ]] && continue
    current="$current$component"
    [[ -L "$current" ]] && return 0
    current="$current/"
  done
  return 1
}

canonical_existing_dir() {
  [[ -d "$1" ]] || return 1
  has_symlink_component "$1" && return 1
  (cd -P -- "$1" && pwd)
}

assert_paths() {
  [[ -n "$STAGING_ROOT" && -n "$BACKUP_DIR" ]] || die "--root and --backup-dir are required"
  absolute_without_traversal "$STAGING_ROOT" || die "--root must be an absolute path without traversal"
  absolute_without_traversal "$BACKUP_DIR" || die "--backup-dir must be an absolute path without traversal"
  STAGING_ROOT="$(canonical_existing_dir "$STAGING_ROOT")" || die "--root must exist without symlink traversal"
  [[ "$STAGING_ROOT" == "$EXPECTED_ROOT" ]] || die "--root must be exactly $EXPECTED_ROOT"
  [[ "$BACKUP_DIR" == "$STAGING_ROOT/backups" ]] || die "--backup-dir must be exactly under the staging root"
}

dry_run() {
  assert_paths
  printf 'BACKUP_ACTION=NOT_EXECUTED\n'
  printf 'RESULT|BACKUP_DRY_RUN|PASS|no backup or restore command was executed\n'
  printf 'RESULT|RESTORE_REHEARSAL|REHEARSAL_NOT_EXECUTED_WAITING_FOR_OWNER_APPROVAL|no backup content was read\n'
}

inspect_existing() {
  local entry size modified basename_digest count=0
  assert_paths
  if [[ ! -d "$BACKUP_DIR" ]]; then
    printf 'RESULT|BACKUP_METADATA|PENDING|staging backup directory does not exist\n'
    return 0
  fi
  has_symlink_component "$BACKUP_DIR" && die "--backup-dir must not traverse a symlink"
  shopt -s nullglob
  for entry in "$BACKUP_DIR"/*; do
    [[ -f "$entry" && ! -L "$entry" ]] || continue
    size="$(stat -c '%s' "$entry" 2>/dev/null || stat -f '%z' "$entry")"
    modified="$(stat -c '%y' "$entry" 2>/dev/null || stat -f '%Sm' -t '%Y-%m-%dT%H:%M:%SZ' "$entry")"
    basename_digest="$(string_digest "$(basename -- "$entry")")"
    printf 'BACKUP_METADATA|basename_sha256=%s|size_bytes=%s|modified_at=%s\n' "$basename_digest" "$size" "$modified"
    count=$((count + 1))
  done
  printf 'BACKUP_ACTION=NOT_EXECUTED\n'
  printf 'RESULT|BACKUP_METADATA|PASS|files=%s; metadata-only; backup contents were not read\n' "$count"
  printf 'RESULT|RESTORE_REHEARSAL|REHEARSAL_NOT_EXECUTED_WAITING_FOR_OWNER_APPROVAL|no restore was attempted\n'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      [[ "$ACTION_SELECTED" == "false" ]] || die "choose exactly one action"
      ACTION="dry-run"; ACTION_SELECTED="true"
      ;;
    --inspect-existing)
      [[ "$ACTION_SELECTED" == "false" ]] || die "choose exactly one action"
      ACTION="inspect-existing"; ACTION_SELECTED="true"
      ;;
    --root|--backup-dir)
      [[ $# -ge 2 ]] || die "$1 requires a value"
      [[ "$1" == "--root" ]] && STAGING_ROOT="$2" || BACKUP_DIR="$2"
      shift
      ;;
    --help|-h) usage; exit 0 ;;
    *) die "unsupported option: $1" ;;
  esac
  shift
done

case "$ACTION" in
  help) usage ;;
  dry-run) dry_run ;;
  inspect-existing) inspect_existing ;;
esac
