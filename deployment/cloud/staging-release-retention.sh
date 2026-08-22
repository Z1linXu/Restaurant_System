#!/usr/bin/env bash
set -Eeuo pipefail

# Fixed-root Staging release retention.  Dry-run is the default.  Execute can
# remove only an exact, clean detached Git worktree that was present in an
# Owner-reviewed dry-run plan; it never removes a release tree recursively and
# never invokes Docker, Compose, PostgreSQL, journald, or Production tooling.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-hygiene-common.sh
source "$SCRIPT_DIR/staging-hygiene-common.sh"

ACTION="dry-run"
ENV_FILE=""
PREVIOUS_VERIFIED_SHA=""
PLAN_FILE=""
PLAN_SHA256=""
KEEP_COUNT="$HYGIENE_EXPECTED_RELEASE_KEEP_COUNT"
MIN_AGE_SECONDS="$HYGIENE_EXPECTED_RELEASE_MIN_AGE_SECONDS"
REPOSITORY=""
RELEASES_DIR=""
STATE_DIR=""
EVIDENCE_DIR=""
ENV_DIGEST=""
CURRENT_SHA=""
DISCOVERED_PREVIOUS_SHA=""
RELEASES_IDENTITY=""
STATE_IDENTITY=""
PROTECTED_SHA_SET=""
PROTECTED_LINES=""
ALL_RELEASE_LINES=""
UNSAFE_RELEASE_SHA_SET=""
UNSAFE_RELEASE_LINES=""
ELIGIBLE_LINES=""
SKIP_LINES=""
PLAN_CONTENT=""
PLAN_PROTECTED_SHA_SET=""
PLAN_ELIGIBLE_SHA_SET=""
LOCK_HELD="false"

usage() {
  cat <<'EOF'
Staging release retention helper (fixed-root, fail-closed).

Usage:
  staging-release-retention.sh --dry-run \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    [--previous-verified-sha <full-sha>]
  staging-release-retention.sh --protected-set \
    --env-file /srv/restaurant-pos/staging/config/.env.staging
  staging-release-retention.sh --execute \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --plan-file /srv/restaurant-pos/staging/evidence/<dry-run-plan> \
    --plan-sha256 <owner-reviewed-sha256> \
    [--previous-verified-sha <full-sha>]

The policy keeps the current release, the previous verified release, and the
newest three release worktrees, and considers only clean worktrees older than
7 days beyond that set. Execute requires the exact plan digest and revalidates
the fixed root, owner/symlink topology, environment SHA, evidence/recovery/
rollback references, repository worktree registration, and eligibility before
each removal. It never changes volumes, database state, containers, images, or
Production.
EOF
}

cleanup() {
  local status=$?
  trap - EXIT ERR INT TERM
  if [[ "$LOCK_HELD" == "true" ]]; then
    flock -u 9 >/dev/null 2>&1 || true
    exec 9>&-
    LOCK_HELD="false"
  fi
  exit "$status"
}

handle_interrupt() { exit 130; }
handle_terminate() { exit 143; }

contains_sha() {
  local set="$1" wanted="$2"
  case "$set" in
    *$'\n'"$wanted"$'\n'*|"$wanted"$'\n'*|*$'\n'"$wanted"|"$wanted") return 0 ;;
  esac
  return 1
}

add_protected_sha() {
  local sha="$1" reason="$2" reference="${3:-}" line
  hygiene_validate_sha "$sha"
  if ! contains_sha "$PROTECTED_SHA_SET" "$sha"; then
    PROTECTED_SHA_SET="${PROTECTED_SHA_SET}${sha}"$'\n'
    line="RELEASE_RETENTION|PROTECTED|$sha|$reason"
    [[ -z "$reference" ]] || line="${line}|reference=$reference"
    PROTECTED_LINES="${PROTECTED_LINES}${line}"$'\n'
  fi
}

add_plan_protected_sha() {
  local sha="$1"
  if ! contains_sha "$PLAN_PROTECTED_SHA_SET" "$sha"; then
    PLAN_PROTECTED_SHA_SET="${PLAN_PROTECTED_SHA_SET}${sha}"$'\n'
  fi
}

add_plan_eligible_sha() {
  local sha="$1"
  if ! contains_sha "$PLAN_ELIGIBLE_SHA_SET" "$sha"; then
    PLAN_ELIGIBLE_SHA_SET="${PLAN_ELIGIBLE_SHA_SET}${sha}"$'\n'
  fi
}

validate_release_root() {
  local mode
  RELEASES_DIR="$HYGIENE_ROOT/releases"
  [[ "$RELEASES_DIR" == "$HYGIENE_EXPECTED_ROOT/releases" ]] || hygiene_die "Staging releases directory path changed"
  [[ -d "$RELEASES_DIR" && ! -L "$RELEASES_DIR" ]] || hygiene_die "Staging releases directory is unavailable"
  hygiene_path_has_symlink "$RELEASES_DIR" && hygiene_die "Staging releases directory must not traverse a symlink"
  [[ "$(hygiene_canonical_dir "$RELEASES_DIR")" == "$RELEASES_DIR" ]] || hygiene_die "Staging releases directory canonical path changed"
  [[ "$(hygiene_file_owner "$RELEASES_DIR")" == "$(id -u)" ]] || hygiene_die "Staging releases directory must be owned by the invoking user"
  mode="$(hygiene_file_mode "$RELEASES_DIR")"
  [[ "$mode" == "700" || "$mode" == "750" ]] || hygiene_die "Staging releases directory must use mode 0700 or 0750"
  RELEASES_IDENTITY="$(hygiene_root_identity "$RELEASES_DIR")"
}

validate_state_and_evidence_roots() {
  STATE_DIR="$HYGIENE_ROOT/state"
  EVIDENCE_DIR="$HYGIENE_ROOT/evidence"
  hygiene_validate_fixed_directory "Staging state directory" "$STATE_DIR" "$HYGIENE_ROOT/state"
  hygiene_validate_fixed_directory "Staging evidence directory" "$EVIDENCE_DIR" "$HYGIENE_ROOT/evidence"
  [[ "$(hygiene_file_mode "$EVIDENCE_DIR")" == "700" ]] || hygiene_die "Staging evidence directory must use owner-only mode 0700"
  STATE_IDENTITY="$(hygiene_root_identity "$STATE_DIR")"
}

assert_release_root_unchanged() {
  [[ -d "$RELEASES_DIR" && ! -L "$RELEASES_DIR" ]] || hygiene_die "Staging releases directory identity is unavailable"
  hygiene_path_has_symlink "$RELEASES_DIR" && hygiene_die "Staging releases directory identity changed"
  [[ "$(hygiene_canonical_dir "$RELEASES_DIR")" == "$RELEASES_DIR" &&
     "$(hygiene_file_owner "$RELEASES_DIR")" == "$(id -u)" &&
     "$(hygiene_root_identity "$RELEASES_DIR")" == "$RELEASES_IDENTITY" ]] ||
    hygiene_die "Staging releases directory identity changed"
}

assert_state_root_unchanged() {
  [[ -d "$STATE_DIR" && ! -L "$STATE_DIR" ]] || hygiene_die "Staging state directory identity is unavailable"
  hygiene_path_has_symlink "$STATE_DIR" && hygiene_die "Staging state directory identity changed"
  [[ "$(hygiene_canonical_dir "$STATE_DIR")" == "$STATE_DIR" &&
     "$(hygiene_file_owner "$STATE_DIR")" == "$(id -u)" &&
     "$(hygiene_root_identity "$STATE_DIR")" == "$STATE_IDENTITY" ]] ||
    hygiene_die "Staging state directory identity changed"
}

validate_repository() {
  REPOSITORY="$HYGIENE_ROOT/repository.git"
  [[ "$REPOSITORY" == "$HYGIENE_EXPECTED_ROOT/repository.git" ]] || hygiene_die "Staging repository path changed"
  [[ -d "$REPOSITORY" && ! -L "$REPOSITORY" ]] || hygiene_die "Staging bare repository is unavailable"
  hygiene_path_has_symlink "$REPOSITORY" && hygiene_die "Staging bare repository must not traverse a symlink"
  [[ "$(hygiene_canonical_dir "$REPOSITORY")" == "$REPOSITORY" ]] || hygiene_die "Staging bare repository canonical path changed"
  [[ "$(hygiene_file_owner "$REPOSITORY")" == "$(id -u)" && "$(hygiene_file_mode "$REPOSITORY")" == "700" ]] || hygiene_die "Staging bare repository must be owner-only mode 0700"
  [[ "$(git --git-dir="$REPOSITORY" rev-parse --is-bare-repository 2>/dev/null || true)" == "true" ]] || hygiene_die "Staging repository must be bare"
}

validate_reference_tree() {
  local root="$1" path mode
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    [[ ! -L "$path" ]] || hygiene_die "protected reference tree contains a symlink: $path"
    if [[ -d "$path" ]]; then
      [[ "$(hygiene_file_owner "$path")" == "$(id -u)" ]] || hygiene_die "protected reference directory owner is unsafe: $path"
      hygiene_mode_has_group_or_other_write "$path" && hygiene_die "protected reference directory is group or other writable: $path"
    elif [[ -f "$path" ]]; then
      [[ "$(hygiene_file_owner "$path")" == "$(id -u)" ]] || hygiene_die "protected reference file owner is unsafe: $path"
      if [[ "$root" == "$EVIDENCE_DIR" ]]; then
        # Historical evidence predates the owner-only 0600 convention. The
        # fixed parent remains owner-only and non-writable by group/other; do
        # not mutate immutable evidence solely to normalize its legacy mode.
        mode="$(hygiene_file_mode "$path")"
        [[ "$mode" =~ ^(600|640|644|660|664)$ ]] || hygiene_die "historical evidence file mode is unsafe: $path"
      else
        hygiene_mode_has_group_or_other_write "$path" && hygiene_die "protected reference file is group or other writable: $path"
      fi
    else
      hygiene_die "protected reference tree contains a non-regular path: $path"
    fi
  done < <(
    if [[ "$root" == "$STATE_DIR" ]]; then
      find -P "$root" -path "$STATE_DIR/postgres" -prune -o -mindepth 1 -print
    else
      find -P "$root" -mindepth 1 -print
    fi
  )
  return 0
}

validate_postgres_boundary() {
  local path="$STATE_DIR/postgres" owner mode permissions group other
  [[ "$path" == "$HYGIENE_EXPECTED_ROOT/state/postgres" ]] || hygiene_die "PostgreSQL protected path changed"
  [[ -d "$path" && ! -L "$path" ]] || hygiene_die "PostgreSQL protected path must be a real directory"
  owner="$(hygiene_file_owner "$path")"
  [[ "$owner" == "$(id -u)" || "$owner" == "70" ]] || hygiene_die "PostgreSQL protected path owner is unsafe"
  mode="$(hygiene_file_mode "$path")"
  [[ "$mode" =~ ^[0-7]{3,4}$ ]] || hygiene_die "PostgreSQL protected path mode is unavailable"
  permissions="${mode: -3}"
  group="${permissions:1:1}"
  other="${permissions:2:1}"
  if (( (group & 2) != 0 || (other & 2) != 0 )); then
    hygiene_die "PostgreSQL protected path is group or other writable"
  fi
}

is_retention_plan_file() {
  local path="$1" first_line
  [[ -n "$PLAN_FILE" && "$path" == "$PLAN_FILE" ]] && return 0
  first_line="$(sed -n '1p' "$path" 2>/dev/null || true)"
  [[ "$first_line" == 'RELEASE_RETENTION|SCHEMA|'* ]]
}

collect_reference_shas() {
  local tree="$1" path base normalized_base line sha protect_all_shas
  while IFS= read -r path; do
    [[ -n "$path" && -f "$path" && ! -L "$path" ]] || continue
    is_retention_plan_file "$path" && continue
    base="$(basename -- "$path")"
    normalized_base="$(printf '%s' "$base" | tr '[:upper:]' '[:lower:]')"
    protect_all_shas="false"
    [[ "$normalized_base" =~ (recovery|rollback) ]] && protect_all_shas="true"
    while IFS= read -r line || [[ -n "$line" ]]; do
      if [[ "$protect_all_shas" != "true" ]]; then
        [[ "$(printf '%s' "$line" | tr '[:upper:]' '[:lower:]')" =~ (previous_verified|retain_release|protected_release|rollback_sha|recovery_sha) ]] || continue
      fi
      while IFS= read -r sha; do
        [[ -n "$sha" ]] || continue
        add_protected_sha "$sha" "evidence_or_recovery_reference" "$base"
      done < <(printf '%s\n' "$line" | grep -Eo '[0-9a-f]{40}' || true)
    done <"$path"
  done < <(
    if [[ "$tree" == "$STATE_DIR" ]]; then
      find -P "$tree" -path "$STATE_DIR/postgres" -prune -o -type f -print
    else
      find -P "$tree" -type f -print
    fi
  )
}

discover_previous_verified_sha() {
  local path candidate newest_path="" newest_mtime=-1 mtime
  while IFS= read -r path; do
    [[ -f "$path" && ! -L "$path" ]] || continue
    [[ "$path" == *.record ]] || continue
    mtime="$(stat -c '%Y' "$path" 2>/dev/null || stat -f '%m' "$path" 2>/dev/null || true)"
    [[ "$mtime" =~ ^[0-9]+$ ]] || continue
    if (( mtime > newest_mtime )); then
      newest_mtime="$mtime"
      newest_path="$path"
    fi
  done < <(find -P "$STATE_DIR" -path "$STATE_DIR/postgres" -prune -o -type f -name '*.record' -print)
  [[ -n "$newest_path" ]] || return 0
  candidate="$(awk -F'|' '$1 == "OPS001_ENV_ROTATION" && $2 == "PRIOR_STAGING_SHA" { print $3; exit }' "$newest_path")"
  [[ -z "$candidate" || "$candidate" =~ ^[0-9a-f]{40}$ ]] || hygiene_die "previous verified SHA marker is malformed"
  DISCOVERED_PREVIOUS_SHA="$candidate"
}

validate_release_worktree() {
  local sha="$1" path status submodules line mode mtime
  path="$RELEASES_DIR/$sha"
  hygiene_validate_sha "$sha"
  [[ -d "$path" && ! -L "$path" ]] || hygiene_die "release path is missing or not a directory: $sha"
  hygiene_path_has_symlink "$path" && hygiene_die "release path must not traverse a symlink: $sha"
  [[ "$(hygiene_canonical_dir "$path")" == "$path" ]] || hygiene_die "release canonical path changed: $sha"
  [[ "$(hygiene_file_owner "$path")" == "$(id -u)" ]] || hygiene_die "release owner is unsafe: $sha"
  mode="$(hygiene_file_mode "$path")"
  [[ "$mode" == "700" ]] || hygiene_die "release must use mode 0700: $sha"
  [[ "$(git -C "$path" rev-parse HEAD 2>/dev/null || true)" == "$sha" ]] || hygiene_die "release HEAD does not match its exact SHA: $sha"
  status="$(git -C "$path" status --porcelain=v1 --untracked-files=all)"
  [[ -z "$status" ]] || hygiene_die "release is not clean and cannot be eligible: $sha"
  submodules="$(git -C "$path" submodule status --recursive 2>/dev/null || true)"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    case "${line:0:1}" in
      -|+|U) hygiene_die "release has an unclean submodule: $sha" ;;
    esac
  done <<<"$submodules"
  if ! git --git-dir="$REPOSITORY" worktree list --porcelain 2>/dev/null | awk -v wanted_path="$path" -v wanted_sha="$sha" '
    $1 == "worktree" { current_path=$2; next }
    $1 == "HEAD" && current_path == wanted_path && $2 == wanted_sha { found=1 }
    END { exit !found }
  ' >/dev/null; then
    hygiene_die "release is not registered as the exact repository worktree: $sha"
  fi
  mtime="$(stat -c '%Y' "$path" 2>/dev/null || stat -f '%m' "$path" 2>/dev/null || true)"
  [[ "$mtime" =~ ^[0-9]+$ ]] || hygiene_die "release age metadata is unavailable: $sha"
  printf '%s|%s\n' "$mtime" "$sha"
}

enumerate_releases() {
  local path base metadata mode
  ALL_RELEASE_LINES=""
  UNSAFE_RELEASE_SHA_SET=""
  UNSAFE_RELEASE_LINES=""
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    [[ ! -L "$path" ]] || hygiene_die "Staging releases contains a symlink: $path"
    [[ -d "$path" ]] || hygiene_die "Staging releases contains a non-directory entry: $path"
    base="$(basename -- "$path")"
    [[ "$base" =~ ^[0-9a-f]{40}$ ]] || hygiene_die "Staging releases contains an unexpected entry: $base"
    hygiene_path_has_symlink "$path" && hygiene_die "release path must not traverse a symlink: $base"
    [[ "$(hygiene_canonical_dir "$path")" == "$path" ]] || hygiene_die "release canonical path changed: $base"
    [[ "$(hygiene_file_owner "$path")" == "$(id -u)" ]] || hygiene_die "release owner is unsafe: $base"
    mode="$(hygiene_file_mode "$path")"
    [[ "$mode" =~ ^[0-7]{3,4}$ ]] || hygiene_die "release mode is unavailable: $base"
    if [[ "$mode" != "700" ]]; then
      UNSAFE_RELEASE_SHA_SET="${UNSAFE_RELEASE_SHA_SET}${base}"$'\n'
      UNSAFE_RELEASE_LINES="${UNSAFE_RELEASE_LINES}RELEASE_RETENTION|UNSAFE_RETAINED|${base}|mode=${mode};content_not_inspected"$'\n'
      continue
    fi
    metadata="$(validate_release_worktree "$base")"
    ALL_RELEASE_LINES="${ALL_RELEASE_LINES}${metadata}"$'\n'
  done < <(find -P "$RELEASES_DIR" -mindepth 1 -maxdepth 1 -print | sort)
  [[ -n "$ALL_RELEASE_LINES" ]] || hygiene_die "Staging releases contains no exact release worktree"
}

release_exists() {
  local sha="$1"
  grep -Fqx "${sha}" < <(printf '%s' "$ALL_RELEASE_LINES" | awk -F'|' 'NF == 2 {print $2}')
}

protect_keep_count() {
  local line rank=0 mtime sha
  while IFS='|' read -r mtime sha; do
    [[ -n "$sha" ]] || continue
    rank=$((rank + 1))
    (( rank <= KEEP_COUNT )) || break
    add_protected_sha "$sha" "newest_${rank}_of_${KEEP_COUNT}"
  done < <(printf '%s' "$ALL_RELEASE_LINES" | sed '/^$/d' | sort -t'|' -k1,1nr)
}

select_eligible_releases() {
  local now="$1" line mtime sha age
  ELIGIBLE_LINES=""
  SKIP_LINES=""
  while IFS='|' read -r mtime sha; do
    [[ -n "$sha" ]] || continue
    contains_sha "$PROTECTED_SHA_SET" "$sha" && continue
    age=$((now - mtime))
    if [[ -z "$PREVIOUS_VERIFIED_SHA" ]]; then
      SKIP_LINES="${SKIP_LINES}RELEASE_RETENTION|SKIP|${sha}|previous_verified_unknown"$'\n'
    elif (( age < MIN_AGE_SECONDS )); then
      SKIP_LINES="${SKIP_LINES}RELEASE_RETENTION|SKIP|${sha}|too_new|age_seconds=${age}"$'\n'
    else
      ELIGIBLE_LINES="${ELIGIBLE_LINES}${sha}|age_seconds=${age}"$'\n'
    fi
  done < <(printf '%s' "$ALL_RELEASE_LINES" | sed '/^$/d')
}

build_plan() {
  local now="$1"
  PROTECTED_SHA_SET=""
  PROTECTED_LINES=""
  PLAN_PROTECTED_SHA_SET=""
  PLAN_ELIGIBLE_SHA_SET=""
  add_protected_sha "$CURRENT_SHA" "current_staging"
  if [[ -n "$PREVIOUS_VERIFIED_SHA" ]]; then
    add_protected_sha "$PREVIOUS_VERIFIED_SHA" "previous_verified"
  fi
  while IFS= read -r unsafe_sha; do
    [[ -n "$unsafe_sha" ]] || continue
    add_protected_sha "$unsafe_sha" "unsafe_legacy_release_metadata"
  done <<<"$UNSAFE_RELEASE_SHA_SET"
  collect_reference_shas "$STATE_DIR"
  collect_reference_shas "$EVIDENCE_DIR"
  protect_keep_count
  select_eligible_releases "$now"
  SKIP_LINES="${UNSAFE_RELEASE_LINES}${SKIP_LINES}"
  emit_plan
}

resolve_previous_verified() {
  discover_previous_verified_sha
  if [[ -n "$PREVIOUS_VERIFIED_SHA" && -n "$DISCOVERED_PREVIOUS_SHA" && "$PREVIOUS_VERIFIED_SHA" != "$DISCOVERED_PREVIOUS_SHA" ]]; then
    hygiene_die "explicit previous verified SHA disagrees with the newest rotation record"
  fi
  [[ -n "$PREVIOUS_VERIFIED_SHA" ]] || PREVIOUS_VERIFIED_SHA="$DISCOVERED_PREVIOUS_SHA"
  if [[ -n "$PREVIOUS_VERIFIED_SHA" ]]; then
    hygiene_validate_sha "$PREVIOUS_VERIFIED_SHA"
    release_exists "$PREVIOUS_VERIFIED_SHA" || hygiene_die "previous verified SHA is not a retained release: $PREVIOUS_VERIFIED_SHA"
  fi
}

prepare_scope() {
  hygiene_validate_env_and_scope "$ENV_FILE"
  ENV_DIGEST="$(hygiene_file_digest "$ENV_FILE")"
  CURRENT_SHA="$HYGIENE_CURRENT_SHA"
  validate_release_root
  validate_state_and_evidence_roots
  validate_postgres_boundary
  validate_repository
  validate_reference_tree "$STATE_DIR"
  validate_reference_tree "$EVIDENCE_DIR"
  enumerate_releases
  release_exists "$CURRENT_SHA" || hygiene_die "current Staging SHA is not a retained release: $CURRENT_SHA"
  resolve_previous_verified
}

emit_plan() {
  local line sha reason
  printf 'RELEASE_RETENTION|SCHEMA|%s\n' "$HYGIENE_PLAN_SCHEMA"
  printf 'RELEASE_RETENTION|ENVIRONMENT|%s\n' "$HYGIENE_EXPECTED_ENVIRONMENT"
  printf 'RELEASE_RETENTION|COMPOSE_PROJECT|%s\n' "$HYGIENE_EXPECTED_PROJECT"
  printf 'RELEASE_RETENTION|STAGING_ROOT|%s\n' "$HYGIENE_ROOT"
  printf 'RELEASE_RETENTION|ENV_SHA256|%s\n' "$ENV_DIGEST"
  printf 'RELEASE_RETENTION|CURRENT_SHA|%s\n' "$CURRENT_SHA"
  if [[ -n "$PREVIOUS_VERIFIED_SHA" ]]; then
    printf 'RELEASE_RETENTION|PREVIOUS_VERIFIED_SHA|%s\n' "$PREVIOUS_VERIFIED_SHA"
  else
    printf 'RELEASE_RETENTION|PREVIOUS_VERIFIED_SHA|UNKNOWN\n'
  fi
  printf 'RELEASE_RETENTION|KEEP_COUNT|%s\n' "$KEEP_COUNT"
  printf 'RELEASE_RETENTION|MIN_AGE_SECONDS|%s\n' "$MIN_AGE_SECONDS"
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    printf '%s\n' "$line"
    sha="$(printf '%s' "$line" | awk -F'|' '$2 == "PROTECTED" {print $3}')"
    [[ -n "$sha" ]] && add_plan_protected_sha "$sha"
  done <<<"$PROTECTED_LINES"
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    printf 'RELEASE_RETENTION|ELIGIBLE|%s\n' "$line"
    sha="${line%%|*}"
    add_plan_eligible_sha "$sha"
  done <<<"$ELIGIBLE_LINES"
  printf '%s' "$SKIP_LINES"
  printf 'RELEASE_RETENTION|BOUNDARY|delete=exact_clean_git_worktree_only;docker=untouched;volumes=untouched;database=untouched;production=untouched\n'
}

plan_value() {
  local key="$1" file="$2" value
  value="$(awk -F'|' -v wanted="$key" '$1 == "RELEASE_RETENTION" && $2 == wanted {print $3; exit}' "$file")"
  printf '%s' "$value"
}

validate_plan_shape() {
  local plan="$1" current previous env_digest keep age boundary
  grep -Fxq "RELEASE_RETENTION|SCHEMA|$HYGIENE_PLAN_SCHEMA" "$plan" || hygiene_die "plan schema mismatch"
  grep -Fxq "RELEASE_RETENTION|ENVIRONMENT|$HYGIENE_EXPECTED_ENVIRONMENT" "$plan" || hygiene_die "plan environment mismatch"
  grep -Fxq "RELEASE_RETENTION|COMPOSE_PROJECT|$HYGIENE_EXPECTED_PROJECT" "$plan" || hygiene_die "plan project mismatch"
  grep -Fxq "RELEASE_RETENTION|STAGING_ROOT|$HYGIENE_ROOT" "$plan" || hygiene_die "plan root mismatch"
  current="$(plan_value CURRENT_SHA "$plan")"
  previous="$(plan_value PREVIOUS_VERIFIED_SHA "$plan")"
  env_digest="$(plan_value ENV_SHA256 "$plan")"
  keep="$(plan_value KEEP_COUNT "$plan")"
  age="$(plan_value MIN_AGE_SECONDS "$plan")"
  hygiene_validate_sha "$current"
  [[ "$current" == "$CURRENT_SHA" ]] || hygiene_die "plan current SHA differs from live Staging"
  if [[ "$previous" != UNKNOWN ]]; then hygiene_validate_sha "$previous"; fi
  [[ "$previous" == "${PREVIOUS_VERIFIED_SHA:-UNKNOWN}" ]] || hygiene_die "plan previous verified SHA differs from live Staging"
  [[ "$env_digest" == "$ENV_DIGEST" ]] || hygiene_die "plan environment digest differs from live Staging"
  [[ "$keep" == "$KEEP_COUNT" && "$age" == "$MIN_AGE_SECONDS" ]] || hygiene_die "plan retention policy differs from this helper"
  boundary="$(grep -F 'RELEASE_RETENTION|BOUNDARY|' "$plan" || true)"
  [[ "$boundary" == 'RELEASE_RETENTION|BOUNDARY|delete=exact_clean_git_worktree_only;docker=untouched;volumes=untouched;database=untouched;production=untouched' ]] || hygiene_die "plan boundary is not the protected release-only boundary"
}

read_plan_sets() {
  local line sha
  PLAN_PROTECTED_SHA_SET=""
  PLAN_ELIGIBLE_SHA_SET=""
  while IFS='|' read -r prefix kind sha rest; do
    [[ "$prefix" == RELEASE_RETENTION ]] || continue
    case "$kind" in
      PROTECTED) add_plan_protected_sha "$sha" ;;
      ELIGIBLE) add_plan_eligible_sha "$sha" ;;
    esac
  done <"$PLAN_FILE"
  contains_sha "$PLAN_PROTECTED_SHA_SET" "$CURRENT_SHA" || hygiene_die "plan protected set does not contain current Staging SHA"
  if [[ -n "$PREVIOUS_VERIFIED_SHA" ]]; then
    contains_sha "$PLAN_PROTECTED_SHA_SET" "$PREVIOUS_VERIFIED_SHA" || hygiene_die "plan protected set does not contain previous verified SHA"
  fi
}

assert_live_protection_matches_plan() {
  local sha
  while IFS= read -r sha; do
    [[ -n "$sha" ]] || continue
    contains_sha "$PLAN_PROTECTED_SHA_SET" "$sha" || hygiene_die "live protected set gained an unreviewed SHA: $sha"
  done <<<"$PROTECTED_SHA_SET"
  while IFS= read -r sha; do
    [[ -n "$sha" ]] || continue
    contains_sha "$PROTECTED_SHA_SET" "$sha" || hygiene_die "plan protected set is no longer live: $sha"
  done <<<"$PLAN_PROTECTED_SHA_SET"
}

assert_live_eligible_matches_plan() {
  local line sha
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    sha="${line%%|*}"
    contains_sha "$PLAN_ELIGIBLE_SHA_SET" "$sha" || hygiene_die "live eligibility gained an unreviewed release: $sha"
  done <<<"$ELIGIBLE_LINES"
}

execute_plan() {
  local sha path line reviewed_plan_protected_set reviewed_plan_eligible_set
  [[ -n "$PREVIOUS_VERIFIED_SHA" ]] || hygiene_die "execute requires an explicit or recorded previous verified SHA"
  hygiene_acquire_lock
  LOCK_HELD="true"
  prepare_scope
  hygiene_validate_plan_file "$PLAN_FILE" "$PLAN_SHA256"
  validate_plan_shape "$PLAN_FILE"
  read_plan_sets
  reviewed_plan_protected_set="$PLAN_PROTECTED_SHA_SET"
  reviewed_plan_eligible_set="$PLAN_ELIGIBLE_SHA_SET"
  build_plan "$(date +%s)" >/dev/null
  PLAN_PROTECTED_SHA_SET="$reviewed_plan_protected_set"
  PLAN_ELIGIBLE_SHA_SET="$reviewed_plan_eligible_set"
  assert_live_protection_matches_plan
  assert_live_eligible_matches_plan
  printf 'RELEASE_RETENTION|EXECUTE|PLAN_VERIFIED|%s\n' "$PLAN_SHA256"
  printf '%s' "$PROTECTED_LINES"
  while IFS='|' read -r sha rest; do
    [[ -n "$sha" ]] || continue
    path="$RELEASES_DIR/$sha"
    if [[ ! -e "$path" ]]; then
      printf 'RELEASE_RETENTION|ALREADY_ABSENT|%s\n' "$sha"
      continue
    fi
    contains_sha "$PROTECTED_SHA_SET" "$sha" && hygiene_die "planned release became protected: $sha"
    contains_sha "$PLAN_ELIGIBLE_SHA_SET" "$sha" || hygiene_die "release is not in the reviewed eligible set: $sha"
    validate_release_worktree "$sha" >/dev/null
    assert_release_root_unchanged
    git --git-dir="$REPOSITORY" worktree remove "$path" >/dev/null 2>&1 || hygiene_die "exact clean release worktree removal failed: $sha"
    [[ ! -e "$path" && ! -L "$path" ]] || hygiene_die "release path remained after exact worktree removal: $sha"
    printf 'RELEASE_RETENTION|REMOVED|%s\n' "$sha"
  done <<<"$reviewed_plan_eligible_set"
  assert_release_root_unchanged
  assert_state_root_unchanged
  printf 'RELEASE_RETENTION|STATUS|PASS|idempotent_release_only_rotation\n'
  hygiene_emit_boundary
}

main() {
  local now
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run|--validate) ACTION="dry-run" ;;
      --protected-set) ACTION="protected-set" ;;
      --execute) ACTION="execute" ;;
      --env-file)
        [[ $# -ge 2 && -z "$ENV_FILE" ]] || hygiene_usage_error "--env-file requires one value and may appear once"
        ENV_FILE="$2"
        shift
        ;;
      --previous-verified-sha)
        [[ $# -ge 2 && -z "$PREVIOUS_VERIFIED_SHA" ]] || hygiene_usage_error "--previous-verified-sha requires one value and may appear once"
        PREVIOUS_VERIFIED_SHA="$2"
        shift
        ;;
      --plan-file)
        [[ $# -ge 2 && -z "$PLAN_FILE" ]] || hygiene_usage_error "--plan-file requires one value and may appear once"
        PLAN_FILE="$2"
        shift
        ;;
      --plan-sha256)
        [[ $# -ge 2 && -z "$PLAN_SHA256" ]] || hygiene_usage_error "--plan-sha256 requires one value and may appear once"
        PLAN_SHA256="$2"
        shift
        ;;
      --help|-h) usage; exit 0 ;;
      *) hygiene_usage_error "unsupported option: $1" ;;
    esac
    shift
  done
  [[ -n "$ENV_FILE" ]] || hygiene_usage_error "--env-file is required"
  [[ "$ACTION" != "execute" || ( -n "$PLAN_FILE" && -n "$PLAN_SHA256" ) ]] || hygiene_usage_error "--execute requires --plan-file and --plan-sha256"
  prepare_scope
  now="$(date +%s)"
  case "$ACTION" in
    dry-run)
      PLAN_CONTENT="$(build_plan "$now")" || hygiene_die "release retention plan generation failed"
      printf '%s\n' "$PLAN_CONTENT"
      if [[ -z "$PREVIOUS_VERIFIED_SHA" ]]; then
        printf 'RELEASE_RETENTION|STATUS|PASS|previous_verified_input_required_before_execute\n'
      else
        printf 'RELEASE_RETENTION|STATUS|PASS|dry_run_only\n'
      fi
      hygiene_emit_boundary
      ;;
    protected-set)
      build_plan "$now" >/dev/null
      printf 'RELEASE_RETENTION|PROTECTED_SET|BEGIN\n'
      printf '%s' "$PROTECTED_LINES"
      printf 'RELEASE_RETENTION|PROTECTED_SET|END\n'
      printf 'RELEASE_RETENTION|STATUS|PASS|protected_set_only\n'
      hygiene_emit_boundary
      ;;
    execute)
      execute_plan
      ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap cleanup EXIT
  trap cleanup ERR
  trap handle_interrupt INT
  trap handle_terminate TERM
  main "$@"
fi
