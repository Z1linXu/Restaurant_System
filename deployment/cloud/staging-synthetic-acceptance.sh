#!/usr/bin/env bash
set -Eeuo pipefail

# Guarded one-shot launcher for STG-005A/STG-005B acceptance commands.
# It never targets Production and defaults to validation-only behavior.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STAGING_COMPOSE_FILE="$SCRIPT_DIR/docker-compose.staging.yml"
STAGING_DEPLOY_VALIDATOR="$SCRIPT_DIR/staging-deploy.sh"
EXPECTED_ROOT="/srv/restaurant-pos/staging"
EXPECTED_PROJECT="restaurant-pos-staging"
EXPECTED_PRODUCTION_PROJECT="cloud"
EXPECTED_PRINTING_MODE="DISABLED"
MAX_READINESS_AGE_SECONDS=900
MAX_APPROVAL_WINDOW_SECONDS=86400
ACTION_TIMEOUT_SECONDS=600
DOCKER_METADATA_TIMEOUT_SECONDS=20
SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

ACTION="validate"
EXECUTE_RUNTIME="false"
ENV_FILE=""
APPROVED_SHA=""
PREFLIGHT_EVIDENCE=""
PREFLIGHT_EVIDENCE_SHA256=""
VALIDATED_PREFLIGHT_SHA256=""
READINESS_EVIDENCE=""
READINESS_EVIDENCE_SHA256=""
VALIDATED_READINESS_EVIDENCE_SHA256=""
ACTION_APPROVAL=""
ACTION_APPROVAL_SHA256=""
VALIDATED_ACTION_APPROVAL_SHA256=""
RUN_ID=""
ORGANIZATION_NAME=""
ORGANIZATION_CODE=""
SOURCE_STORE_NAME=""
SOURCE_STORE_CODE=""
OWNER_LOGIN=""
OWNER_NAME=""
SOURCE_STORE_ID=""

ACTIVE_ENV_FILE=""
ENV_SNAPSHOT=""
ENV_SNAPSHOT_SHA256=""
DOCKER_CLI_STATE_PARENT=""
DOCKER_CLI_STATE_ROOT=""
DOCKER_CLI_HOME=""
DOCKER_CLI_CONFIG=""
IMMUTABLE_IMAGE_OVERRIDE=""
RESOLVED_BACKEND_IMAGE_ID=""
ACTION_LOCK_FILE=""
ACTION_LOCK_FD=""
ACTION_BLOCKED_MARKER=""
ONE_SHOT_CONTAINER_NAME=""
ONE_SHOT_STARTED="false"

usage() {
  cat <<'EOF'
Restaurant POS guarded AL-003S synthetic acceptance launcher.

Usage:
  ./staging-synthetic-acceptance.sh --validate \
    --approved-sha <full-sha> \
    --preflight-evidence <absolute-path> \
    --preflight-evidence-sha256 <sha256> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging

  ./staging-synthetic-acceptance.sh --execute-runtime \
    --action bootstrap-plan|bootstrap-execute \
    --approved-sha <full-sha> \
    --preflight-evidence <absolute-path> \
    --preflight-evidence-sha256 <sha256> \
    --readiness-evidence <absolute-path> \
    --readiness-evidence-sha256 <sha256> \
    --action-approval <absolute-path> \
    --action-approval-sha256 <sha256> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --run-id <STG005_...> \
    --organization-name <STG005_...> \
    --organization-code <STG005_...> \
    --source-store-name <STG005_...> \
    --source-store-code <STG005_...> \
    --owner-login <STG005_...> \
    --owner-name <STG005_...>

  ./staging-synthetic-acceptance.sh --execute-runtime \
    --action source-menu-plan|source-menu-execute \
    --approved-sha <full-sha> \
    --preflight-evidence <absolute-path> \
    --preflight-evidence-sha256 <sha256> \
    --readiness-evidence <absolute-path> \
    --readiness-evidence-sha256 <sha256> \
    --action-approval <absolute-path> \
    --action-approval-sha256 <sha256> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --source-store-id 1 \
    --source-store-code <STG005_...>

The default action is validation only. Every one-shot container requires
--execute-runtime. Data writes additionally require an explicit *-execute
action. bootstrap-execute accepts the synthetic password only on standard
input and rejects an interactive terminal. No password or token is accepted as
an argument.
EOF
}

die() {
  echo "AL003S_ACCEPTANCE|NO_GO|$*" >&2
  exit 1
}

canonical_dir() {
  (cd -P -- "$1" 2>/dev/null && pwd)
}

canonical_file() {
  local parent
  parent="$(canonical_dir "$(dirname -- "$1")")" || return 1
  printf '%s/%s\n' "$parent" "$(basename -- "$1")"
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

string_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum -a 256 | awk '{print $1}'
  fi
}

path_has_symlink() {
  local path="$1" part current="" old_ifs="$IFS"
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
  local key="$1" line value
  line="$(grep -E "^${key}=" "$ACTIVE_ENV_FILE" || true)"
  [[ -n "$line" ]] || return 1
  value="${line#*=}"
  if [[ "$value" == \"*\" && "${value: -1}" == '"' ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "${value: -1}" == "'" ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

require_env_value() {
  local value
  value="$(dotenv_value "$1" || true)"
  [[ -n "$value" ]] || die "$1 is missing from the private environment snapshot"
  printf '%s' "$value"
}

cleanup() {
  local status=$? remaining=""
  trap - ERR INT TERM
  if [[ "$ONE_SHOT_STARTED" == "true" && -n "$ONE_SHOT_CONTAINER_NAME" &&
        -n "${DOCKER_BIN:-}" && -x "${DOCKER_BIN:-}" && -d "$DOCKER_CLI_HOME" &&
        -d "$DOCKER_CLI_CONFIG" ]]; then
    if ! env -i PATH="$SAFE_PATH" HOME="$DOCKER_CLI_HOME" DOCKER_CONFIG="$DOCKER_CLI_CONFIG" \
        "$TIMEOUT_BIN" --signal=TERM --kill-after=5s "${DOCKER_METADATA_TIMEOUT_SECONDS}s" \
        "$DOCKER_BIN" --context default rm -f "$ONE_SHOT_CONTAINER_NAME" >/dev/null 2>&1; then
      mark_action_blocked "scoped_container_cleanup_failed" || true
    elif ! remaining="$(env -i PATH="$SAFE_PATH" HOME="$DOCKER_CLI_HOME" DOCKER_CONFIG="$DOCKER_CLI_CONFIG" \
        "$TIMEOUT_BIN" --signal=TERM --kill-after=5s "${DOCKER_METADATA_TIMEOUT_SECONDS}s" \
        "$DOCKER_BIN" --context default ps -aq --filter "name=^/${ONE_SHOT_CONTAINER_NAME}$" --format '{{.ID}}' 2>/dev/null)"; then
      mark_action_blocked "scoped_container_cleanup_unverified" || true
    elif [[ -n "$remaining" ]]; then
      mark_action_blocked "scoped_container_remains" || true
    fi
  fi
  ONE_SHOT_STARTED="false"
  ONE_SHOT_CONTAINER_NAME=""
  if [[ -n "$ACTION_LOCK_FD" ]]; then
    "$FLOCK_BIN" -u "$ACTION_LOCK_FD" >/dev/null 2>&1 || true
    exec 9>&-
  fi
  ACTION_LOCK_FD=""
  [[ -n "$ENV_SNAPSHOT" ]] && rm -f -- "$ENV_SNAPSHOT"
  [[ -n "$IMMUTABLE_IMAGE_OVERRIDE" ]] && rm -f -- "$IMMUTABLE_IMAGE_OVERRIDE"
  if [[ -n "$DOCKER_CLI_STATE_ROOT" ]]; then
    case "$DOCKER_CLI_STATE_ROOT" in
      "$DOCKER_CLI_STATE_PARENT"/restaurant-pos-al003s-docker-cli.*)
        rm -rf -- "$DOCKER_CLI_STATE_ROOT"
        ;;
    esac
  fi
  ENV_SNAPSHOT=""
  IMMUTABLE_IMAGE_OVERRIDE=""
  DOCKER_CLI_STATE_ROOT=""
  return "$status"
}

handle_interrupt() {
  if [[ "$ACTION" != "validate" && -n "$ACTION_LOCK_FD" ]]; then
    mark_action_blocked "interrupt_requires_owner_review" || true
  fi
  cleanup || true
  exit 130
}

handle_terminate() {
  if [[ "$ACTION" != "validate" && -n "$ACTION_LOCK_FD" ]]; then
    mark_action_blocked "termination_requires_owner_review" || true
  fi
  cleanup || true
  exit 143
}

validate_private_directory() {
  local description="$1" path="$2"
  [[ -d "$path" && ! -L "$path" ]] || die "$description must be a real directory"
  [[ "$(canonical_dir "$path")" == "$path" ]] || die "$description canonical path changed"
  [[ "$(file_owner "$path")" == "$(id -u)" ]] || die "$description must be owned by the invoking user"
  [[ "$(file_mode "$path")" == "700" ]] || die "$description must use mode 0700"
  [[ -w "$path" ]] || die "$description must be writable"
}

validate_docker_cli_state() {
  [[ -n "$DOCKER_CLI_STATE_PARENT" && -n "$DOCKER_CLI_STATE_ROOT" ]] || die "private Docker CLI state is not initialized"
  [[ "$DOCKER_CLI_STATE_ROOT" == "$DOCKER_CLI_STATE_PARENT"/restaurant-pos-al003s-docker-cli.* ]] ||
    die "private Docker CLI state escaped its temporary parent"
  [[ "$DOCKER_CLI_HOME" == "$DOCKER_CLI_STATE_ROOT/home" ]] || die "private Docker HOME path changed"
  [[ "$DOCKER_CLI_CONFIG" == "$DOCKER_CLI_STATE_ROOT/docker-config" ]] || die "private Docker config path changed"
  validate_private_directory "private Docker CLI state" "$DOCKER_CLI_STATE_ROOT"
  validate_private_directory "private Docker HOME" "$DOCKER_CLI_HOME"
  validate_private_directory "private Docker config" "$DOCKER_CLI_CONFIG"
}

bounded_docker() {
  env -i PATH="$SAFE_PATH" HOME="$DOCKER_CLI_HOME" DOCKER_CONFIG="$DOCKER_CLI_CONFIG" \
    "$TIMEOUT_BIN" --signal=TERM --kill-after=5s "${DOCKER_METADATA_TIMEOUT_SECONDS}s" \
    "$DOCKER_BIN" --context default "$@"
}

initialize_docker_cli_state() {
  local temporary_dir
  [[ -z "$DOCKER_CLI_STATE_ROOT" ]] || return 0
  temporary_dir="$(canonical_dir "${TMPDIR:-/tmp}")" || die "cannot canonicalize the temporary directory"
  [[ -d "$temporary_dir" && -w "$temporary_dir" ]] || die "temporary directory must be writable"
  DOCKER_CLI_STATE_PARENT="$temporary_dir"
  umask 077
  DOCKER_CLI_STATE_ROOT="$(mktemp -d "$temporary_dir/restaurant-pos-al003s-docker-cli.XXXXXX")" ||
    die "cannot create private Docker CLI state"
  chmod 700 "$DOCKER_CLI_STATE_ROOT"
  DOCKER_CLI_STATE_ROOT="$(canonical_dir "$DOCKER_CLI_STATE_ROOT")" || die "cannot canonicalize private Docker CLI state"
  DOCKER_CLI_HOME="$DOCKER_CLI_STATE_ROOT/home"
  DOCKER_CLI_CONFIG="$DOCKER_CLI_STATE_ROOT/docker-config"
  mkdir -m 700 "$DOCKER_CLI_HOME" "$DOCKER_CLI_CONFIG"
  validate_docker_cli_state
  bounded_docker compose version >/dev/null ||
    die "Docker Compose plugin is unavailable in the private CLI environment"
}

controlled_docker() {
  initialize_docker_cli_state
  validate_docker_cli_state
  bounded_docker "$@"
}

controlled_compose() {
  local -a compose_files=(-f "$STAGING_COMPOSE_FILE")
  [[ -z "$IMMUTABLE_IMAGE_OVERRIDE" ]] || compose_files+=(-f "$IMMUTABLE_IMAGE_OVERRIDE")
  controlled_docker compose \
    --project-name "$EXPECTED_PROJECT" \
    --env-file "$ACTIVE_ENV_FILE" \
    "${compose_files[@]}" "$@"
}

controlled_compose_run() {
  local -a compose_files=(-f "$STAGING_COMPOSE_FILE")
  [[ -z "$IMMUTABLE_IMAGE_OVERRIDE" ]] || compose_files+=(-f "$IMMUTABLE_IMAGE_OVERRIDE")
  initialize_docker_cli_state
  validate_docker_cli_state
  env -i PATH="$SAFE_PATH" HOME="$DOCKER_CLI_HOME" DOCKER_CONFIG="$DOCKER_CLI_CONFIG" \
    "$TIMEOUT_BIN" --signal=TERM --kill-after=10s "${ACTION_TIMEOUT_SECONDS}s" \
    "$DOCKER_BIN" --context default compose \
    --project-name "$EXPECTED_PROJECT" \
    --env-file "$ACTIVE_ENV_FILE" \
    "${compose_files[@]}" run "$@"
}

copy_private_env_snapshot() {
  local source_digest temporary_dir
  source_digest="$(file_digest "$ENV_FILE")"
  temporary_dir="$(canonical_dir "${TMPDIR:-/tmp}")" || die "cannot canonicalize temporary directory"
  umask 077
  ENV_SNAPSHOT="$(mktemp "$temporary_dir/restaurant-pos-al003s-env.XXXXXX")"
  cp -- "$ENV_FILE" "$ENV_SNAPSHOT"
  chmod 600 "$ENV_SNAPSHOT"
  ENV_SNAPSHOT_SHA256="$(file_digest "$ENV_SNAPSHOT")"
  [[ "$ENV_SNAPSHOT_SHA256" == "$source_digest" ]] || die "environment changed while taking the private snapshot"
  ACTIVE_ENV_FILE="$ENV_SNAPSHOT"
}

assert_snapshot_integrity() {
  [[ -f "$ENV_SNAPSHOT" && "$(file_mode "$ENV_SNAPSHOT")" == "600" ]] || die "private environment snapshot is unavailable"
  [[ "$(file_digest "$ENV_SNAPSHOT")" == "$ENV_SNAPSHOT_SHA256" ]] || die "private environment snapshot changed"
  [[ "$(file_digest "$ENV_FILE")" == "$ENV_SNAPSHOT_SHA256" ]] || die "source environment changed after validation"
  [[ "$(file_digest "$PREFLIGHT_EVIDENCE")" == "$VALIDATED_PREFLIGHT_SHA256" ]] || die "preflight evidence changed after validation"
  if [[ -n "$VALIDATED_READINESS_EVIDENCE_SHA256" ]]; then
    [[ "$(file_digest "$READINESS_EVIDENCE")" == "$VALIDATED_READINESS_EVIDENCE_SHA256" ]] ||
      die "runtime readiness evidence changed after validation"
  fi
  if [[ -n "$VALIDATED_ACTION_APPROVAL_SHA256" ]]; then
    [[ "$(file_digest "$ACTION_APPROVAL")" == "$VALIDATED_ACTION_APPROVAL_SHA256" ]] ||
      die "action approval changed after validation"
  fi
}

assert_release_identity() {
  local release_dir
  release_dir="$(canonical_dir "$SCRIPT_DIR/../..")" || die "cannot resolve release directory"
  [[ "$release_dir" == "$EXPECTED_ROOT/releases/$APPROVED_SHA" ]] || die "launcher is not running from the approved release"
  [[ "$(git -C "$release_dir" rev-parse HEAD 2>/dev/null || true)" == "$APPROVED_SHA" ]] || die "release HEAD differs from the approved SHA"
  [[ -z "$(git -C "$release_dir" status --short --untracked-files=all)" ]] || die "approved release worktree is not clean"
}

require_synthetic_name() {
  [[ "$2" =~ ^STG005_[A-Za-z0-9_-]+$ ]] || die "$1 must use the STG005_ synthetic namespace and safe identifier characters"
}

evidence_value() {
  local prefix="$1" file="$2" line
  line="$(awk -v marker="${prefix}|" 'index($0, marker) == 1 { print }' "$file")"
  [[ -n "$line" && "$(printf '%s\n' "$line" | wc -l | tr -d ' ')" == "1" ]] ||
    die "evidence field is missing or duplicated: $prefix"
  printf '%s' "${line#${prefix}|}"
}

action_request_fingerprint() {
  printf '%s\n' \
    "action=$ACTION" \
    "approved_sha=$APPROVED_SHA" \
    "run_id=$RUN_ID" \
    "organization_name=$ORGANIZATION_NAME" \
    "organization_code=$ORGANIZATION_CODE" \
    "source_store_name=$SOURCE_STORE_NAME" \
    "source_store_code=$SOURCE_STORE_CODE" \
    "owner_login=$OWNER_LOGIN" \
    "owner_name=$OWNER_NAME" \
    "source_store_id=$SOURCE_STORE_ID" | string_digest
}

validate_private_evidence_file() {
  local description="$1" path="$2" expected_digest="$3"
  [[ "$path" == "$EXPECTED_ROOT/evidence/"* ]] || die "$description must be under the fixed Staging evidence directory"
  [[ -f "$path" && ! -L "$path" ]] || die "$description must be a regular non-symlink file"
  path_has_symlink "$path" && die "$description path must not traverse symlinks"
  [[ "$(file_owner "$path")" == "$(id -u)" ]] || die "$description must be owned by the invoking user"
  [[ "$(file_mode "$path")" == "600" ]] || die "$description must use mode 0600"
  [[ "$(file_digest "$path")" == "$expected_digest" ]] || die "$description digest mismatch"
}

available_memory_kb() {
  awk '/^MemAvailable:/ {print $2; found=1} END {if (!found) exit 1}' /proc/meminfo
}

cpu_count() {
  nproc
}

free_disk_kb() {
  df -Pk "$EXPECTED_ROOT" | awk 'NR == 2 {print $4; found=1} END {if (!found) exit 1}'
}

load_per_cpu_milli() {
  local processors
  processors="$(cpu_count)" || return 1
  awk -v processors="$processors" 'NR == 1 && processors > 0 {printf "%d\n", ($1 * 1000) / processors; found=1} END {if (!found) exit 1}' /proc/loadavg
}

acquire_action_lock() {
  local state_dir mode
  state_dir="$EXPECTED_ROOT/state"
  [[ -d "$state_dir" && ! -L "$state_dir" ]] || die "Staging state directory must be a real directory"
  path_has_symlink "$state_dir" && die "Staging state directory must not traverse symlinks"
  [[ "$(canonical_dir "$state_dir")" == "$state_dir" ]] || die "Staging state directory canonical path changed"
  [[ "$(file_owner "$state_dir")" == "$(id -u)" ]] || die "Staging state directory must be owned by the invoking user"
  mode="$(file_mode "$state_dir")"
  (( (8#$mode & 8#022) == 0 )) || die "Staging state directory must not be group or other writable"
  ACTION_LOCK_FILE="$state_dir/al003s-acceptance.lock"
  ACTION_BLOCKED_MARKER="$state_dir/al003s-acceptance.blocked"
  [[ ! -L "$ACTION_LOCK_FILE" ]] || die "AL-003S action lock must not be a symlink"
  umask 077
  exec 9>>"$ACTION_LOCK_FILE"
  ACTION_LOCK_FD="9"
  chmod 600 "$ACTION_LOCK_FILE"
  [[ "$(file_owner "$ACTION_LOCK_FILE")" == "$(id -u)" && "$(file_mode "$ACTION_LOCK_FILE")" == "600" ]] ||
    die "AL-003S action lock metadata is unsafe"
  "$FLOCK_BIN" -n "$ACTION_LOCK_FD" || die "another AL-003S action is already running"
  if [[ -e "$ACTION_BLOCKED_MARKER" ]] || grep -Eq '^AL003S_BLOCKED\|' "$ACTION_LOCK_FILE"; then
    die "AL-003S actions are blocked pending Owner cleanup review"
  fi
}

mark_action_blocked() {
  local code="$1" marker lock_recorded="false" marker_recorded="false"
  marker="${ACTION_BLOCKED_MARKER:-$EXPECTED_ROOT/state/al003s-acceptance.blocked}"
  if [[ "$ACTION_LOCK_FD" == "9" ]]; then
    if printf 'AL003S_BLOCKED|%s\n' "$code" >&9; then
      lock_recorded="true"
    fi
  fi
  if [[ -d "$(dirname "$marker")" && ! -L "$(dirname "$marker")" && ! -L "$marker" ]]; then
    umask 077
    if printf 'AL003S_BLOCKED|%s\n' "$code" >"$marker" 2>/dev/null && chmod 600 "$marker" 2>/dev/null; then
      marker_recorded="true"
    fi
  fi
  if [[ "$lock_recorded" != "true" && "$marker_recorded" != "true" ]]; then
    echo "AL003S_ACCEPTANCE|CRITICAL|blocked state could not be persisted" >&2
    return 1
  fi
  [[ "$marker_recorded" == "true" ]] ||
    echo "AL003S_ACCEPTANCE|WARN|blocked marker unavailable; lock record remains authoritative" >&2
}

project_fingerprint() {
  local project="$1" ids id line metadata="" services status health
  ids="$(controlled_docker ps --filter "label=com.docker.compose.project=$project" --format '{{.ID}}')" ||
    die "cannot list $project containers"
  [[ -n "$ids" ]] || die "$project has no running containers"
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    line="$(controlled_docker inspect --format '{{.Id}}|{{.Name}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.Image}}|{{.Created}}|{{.RestartCount}}|{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}NO_HEALTHCHECK{{end}}' "$id")" ||
      die "cannot inspect $project container metadata"
    metadata="${metadata}${line}"$'\n'
  done <<<"$ids"
  services="$(printf '%s' "$metadata" | cut -d'|' -f3 | sort)"
  [[ "$services" == $'backend\ndb\nnginx' ]] || die "$project services must be exactly backend, db, nginx"
  while IFS='|' read -r _ _ _ _ _ _ status health; do
    [[ -z "$status" ]] && continue
    [[ "$status" == "running" ]] || die "$project contains a non-running service"
    [[ "$health" == "healthy" || "$health" == "NO_HEALTHCHECK" ]] || die "$project contains an unhealthy service"
  done <<<"$metadata"
  printf '%s' "$metadata" | sort | string_digest
}

validate_readiness_evidence() {
  local now captured age approved_sha env_digest preflight_digest staging_project staging_fingerprint
  local production_project production_fingerprint min_memory min_cpu min_disk max_load
  local current_memory current_cpu current_disk current_load
  validate_private_evidence_file "runtime readiness evidence" "$READINESS_EVIDENCE" "$READINESS_EVIDENCE_SHA256"
  grep -Fxq 'READINESS|STATUS|PASS' "$READINESS_EVIDENCE" || die "runtime readiness evidence is not PASS"
  now="$(date +%s)"
  captured="$(evidence_value 'READINESS|CAPTURED_AT_EPOCH' "$READINESS_EVIDENCE")"
  [[ "$captured" =~ ^[0-9]+$ && "$captured" -le "$now" ]] || die "runtime readiness timestamp is invalid"
  age=$((now - captured))
  [[ "$age" -le "$MAX_READINESS_AGE_SECONDS" ]] || die "runtime readiness evidence is stale"
  approved_sha="$(evidence_value 'READINESS|APPROVED_SHA' "$READINESS_EVIDENCE")"
  env_digest="$(evidence_value 'READINESS|ENV_SHA256' "$READINESS_EVIDENCE")"
  preflight_digest="$(evidence_value 'READINESS|PREFLIGHT_SHA256' "$READINESS_EVIDENCE")"
  staging_project="$(evidence_value 'READINESS|STAGING_PROJECT' "$READINESS_EVIDENCE")"
  staging_fingerprint="$(evidence_value 'READINESS|STAGING_FINGERPRINT' "$READINESS_EVIDENCE")"
  production_project="$(evidence_value 'READINESS|PRODUCTION_PROJECT' "$READINESS_EVIDENCE")"
  production_fingerprint="$(evidence_value 'READINESS|PRODUCTION_FINGERPRINT' "$READINESS_EVIDENCE")"
  min_memory="$(evidence_value 'READINESS|MIN_AVAILABLE_MEMORY_KB' "$READINESS_EVIDENCE")"
  min_cpu="$(evidence_value 'READINESS|MIN_CPU_COUNT' "$READINESS_EVIDENCE")"
  min_disk="$(evidence_value 'READINESS|MIN_FREE_DISK_KB' "$READINESS_EVIDENCE")"
  max_load="$(evidence_value 'READINESS|MAX_LOAD_PER_CPU_MILLI' "$READINESS_EVIDENCE")"
  [[ "$approved_sha" == "$APPROVED_SHA" ]] || die "runtime readiness SHA binding mismatch"
  [[ "$env_digest" == "$ENV_SNAPSHOT_SHA256" ]] || die "runtime readiness environment binding mismatch"
  [[ "$preflight_digest" == "$VALIDATED_PREFLIGHT_SHA256" ]] || die "runtime readiness preflight binding mismatch"
  [[ "$staging_project" == "$EXPECTED_PROJECT" ]] || die "runtime readiness Staging project mismatch"
  [[ "$production_project" == "$EXPECTED_PRODUCTION_PROJECT" ]] || die "runtime readiness Production project mismatch"
  [[ "$staging_fingerprint" == "$(project_fingerprint "$EXPECTED_PROJECT")" ]] || die "Staging containers changed after readiness capture"
  [[ "$production_fingerprint" == "$(project_fingerprint "$EXPECTED_PRODUCTION_PROJECT")" ]] || die "Production containers changed after readiness capture"
  [[ "$min_memory" =~ ^[1-9][0-9]*$ && "$min_cpu" =~ ^[1-9][0-9]*$ &&
     "$min_disk" =~ ^[1-9][0-9]*$ && "$max_load" =~ ^[1-9][0-9]*$ ]] ||
    die "runtime readiness thresholds are invalid"
  current_memory="$(available_memory_kb)" || die "available memory cannot be read"
  current_cpu="$(cpu_count)" || die "CPU count cannot be read"
  current_disk="$(free_disk_kb)" || die "free disk space cannot be read"
  current_load="$(load_per_cpu_milli)" || die "normalized load cannot be read"
  [[ "$current_memory" -ge "$min_memory" ]] || die "available memory is below the approved threshold"
  [[ "$current_cpu" -ge "$min_cpu" ]] || die "CPU count is below the approved threshold"
  [[ "$current_disk" -ge "$min_disk" ]] || die "free disk space is below the approved threshold"
  [[ "$current_load" -le "$max_load" ]] || die "normalized load is above the approved threshold"
  VALIDATED_READINESS_EVIDENCE_SHA256="$READINESS_EVIDENCE_SHA256"
}

validate_action_approval() {
  local now expires approved_sha action request_fingerprint preflight_digest readiness_digest reference
  validate_private_evidence_file "action approval" "$ACTION_APPROVAL" "$ACTION_APPROVAL_SHA256"
  grep -Fxq 'APPROVAL|STATUS|OWNER_APPROVED' "$ACTION_APPROVAL" || die "action approval is not OWNER_APPROVED"
  now="$(date +%s)"
  expires="$(evidence_value 'APPROVAL|EXPIRES_AT_EPOCH' "$ACTION_APPROVAL")"
  [[ "$expires" =~ ^[0-9]{10}$ && "$expires" -ge "$now" ]] || die "action approval is expired or invalid"
  [[ "$expires" -le $((now + MAX_APPROVAL_WINDOW_SECONDS)) ]] || die "action approval window is too long"
  approved_sha="$(evidence_value 'APPROVAL|APPROVED_SHA' "$ACTION_APPROVAL")"
  action="$(evidence_value 'APPROVAL|ACTION' "$ACTION_APPROVAL")"
  request_fingerprint="$(evidence_value 'APPROVAL|REQUEST_FINGERPRINT' "$ACTION_APPROVAL")"
  preflight_digest="$(evidence_value 'APPROVAL|PREFLIGHT_SHA256' "$ACTION_APPROVAL")"
  readiness_digest="$(evidence_value 'APPROVAL|READINESS_SHA256' "$ACTION_APPROVAL")"
  reference="$(evidence_value 'APPROVAL|REFERENCE' "$ACTION_APPROVAL")"
  [[ "$approved_sha" == "$APPROVED_SHA" && "$action" == "$ACTION" ]] || die "action approval scope mismatch"
  [[ "$request_fingerprint" == "$(action_request_fingerprint)" ]] || die "action approval request fingerprint mismatch"
  [[ "$preflight_digest" == "$VALIDATED_PREFLIGHT_SHA256" ]] || die "action approval preflight binding mismatch"
  [[ "$readiness_digest" == "$VALIDATED_READINESS_EVIDENCE_SHA256" ]] || die "action approval readiness binding mismatch"
  [[ "$reference" =~ ^[A-Za-z0-9_.:/#-]+$ ]] || die "action approval reference is invalid"
  VALIDATED_ACTION_APPROVAL_SHA256="$ACTION_APPROVAL_SHA256"
}

validate_action_arguments() {
  case "$ACTION" in
    validate)
      [[ "$EXECUTE_RUNTIME" == "false" ]] || die "--validate cannot be combined with --execute-runtime"
      [[ -z "$READINESS_EVIDENCE$READINESS_EVIDENCE_SHA256$ACTION_APPROVAL$ACTION_APPROVAL_SHA256" ]] ||
        die "runtime readiness and action approval inputs are only valid for one-shot actions"
      ;;
    bootstrap-plan|bootstrap-execute)
      [[ "$EXECUTE_RUNTIME" == "true" ]] || die "$ACTION requires --execute-runtime"
      require_synthetic_name run-id "$RUN_ID"
      require_synthetic_name organization-name "$ORGANIZATION_NAME"
      require_synthetic_name organization-code "$ORGANIZATION_CODE"
      require_synthetic_name source-store-name "$SOURCE_STORE_NAME"
      require_synthetic_name source-store-code "$SOURCE_STORE_CODE"
      require_synthetic_name owner-login "$OWNER_LOGIN"
      require_synthetic_name owner-name "$OWNER_NAME"
      [[ -z "$SOURCE_STORE_ID" ]] || die "source-store-id is only valid for source-menu actions"
      if [[ "$ACTION" == "bootstrap-execute" ]]; then
        [[ ! -t 0 ]] || die "bootstrap password must be supplied through non-interactive standard input"
      fi
      ;;
    source-menu-plan|source-menu-execute)
      [[ "$EXECUTE_RUNTIME" == "true" ]] || die "$ACTION requires --execute-runtime"
      [[ "$SOURCE_STORE_ID" == "1" ]] || die "source-menu actions require the reviewed synthetic source Store ID 1"
      require_synthetic_name source-store-code "$SOURCE_STORE_CODE"
      [[ -z "$RUN_ID$ORGANIZATION_NAME$ORGANIZATION_CODE$SOURCE_STORE_NAME$OWNER_LOGIN$OWNER_NAME" ]] ||
        die "bootstrap identity arguments are only valid for bootstrap actions"
      ;;
    *)
      die "unsupported action: $ACTION"
      ;;
  esac
  if [[ "$ACTION" != "validate" ]]; then
    [[ -n "$READINESS_EVIDENCE" && "$READINESS_EVIDENCE_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
      die "one-shot actions require runtime readiness evidence and its SHA-256"
    [[ -n "$ACTION_APPROVAL" && "$ACTION_APPROVAL_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
      die "one-shot actions require action-specific Owner approval and its SHA-256"
  fi
}

validate_release_and_evidence() {
  local evidence_digest evidence_env_digest
  [[ "$-" != *x* ]] || die "shell tracing must be disabled"
  [[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]] || die "approved SHA must be a full lowercase Git SHA"
  [[ "$PREFLIGHT_EVIDENCE_SHA256" =~ ^[0-9a-f]{64}$ ]] || die "preflight evidence SHA-256 is invalid"
  [[ "$ENV_FILE" == "$EXPECTED_ROOT/config/.env.staging" ]] || die "environment file must use the fixed Staging path"
  [[ -f "$ENV_FILE" && ! -L "$ENV_FILE" ]] || die "environment file must be a regular non-symlink file"
  [[ "$(file_mode "$ENV_FILE")" == "600" ]] || die "environment file must use mode 0600"
  [[ "$(file_owner "$ENV_FILE")" == "$(id -u)" ]] || die "environment file must be owned by the invoking user"
  path_has_symlink "$ENV_FILE" && die "environment file path must not traverse symlinks"

  [[ "$PREFLIGHT_EVIDENCE" == "$EXPECTED_ROOT/evidence/"* ]] || die "preflight evidence must be under the fixed Staging evidence directory"
  [[ -f "$PREFLIGHT_EVIDENCE" && ! -L "$PREFLIGHT_EVIDENCE" ]] || die "preflight evidence must be a regular non-symlink file"
  [[ "$(file_owner "$PREFLIGHT_EVIDENCE")" == "$(id -u)" ]] || die "preflight evidence must be owned by the invoking user"
  [[ "$(file_mode "$PREFLIGHT_EVIDENCE")" == "600" ]] || die "preflight evidence must use mode 0600"
  path_has_symlink "$PREFLIGHT_EVIDENCE" && die "preflight evidence path must not traverse symlinks"
  evidence_digest="$(file_digest "$PREFLIGHT_EVIDENCE")"
  [[ "$evidence_digest" == "$PREFLIGHT_EVIDENCE_SHA256" ]] || die "preflight evidence digest mismatch"
  VALIDATED_PREFLIGHT_SHA256="$evidence_digest"
  grep -Fxq "SUMMARY|PASS|same-host Staging preflight passed without state changes" "$PREFLIGHT_EVIDENCE" || die "preflight evidence is not PASS"
  grep -Fxq "EVIDENCE|APPROVED_SHA|$APPROVED_SHA" "$PREFLIGHT_EVIDENCE" || die "preflight evidence SHA binding mismatch"
  grep -Fxq "EVIDENCE|STAGING_ROOT|$EXPECTED_ROOT" "$PREFLIGHT_EVIDENCE" || die "preflight evidence root binding mismatch"
  grep -Fxq "EVIDENCE|COMPOSE_PROJECT|$EXPECTED_PROJECT" "$PREFLIGHT_EVIDENCE" || die "preflight evidence project binding mismatch"

  copy_private_env_snapshot
  evidence_env_digest="$(grep '^EVIDENCE|ENV_SHA256|' "$PREFLIGHT_EVIDENCE" | cut -d'|' -f3)"
  [[ "$evidence_env_digest" =~ ^[0-9a-f]{64}$ && "$evidence_env_digest" == "$ENV_SNAPSHOT_SHA256" ]] ||
    die "preflight evidence environment binding mismatch"

  [[ "$(require_env_value COMPOSE_PROJECT_NAME)" == "$EXPECTED_PROJECT" ]] || die "Compose project mismatch"
  [[ "$(require_env_value STAGING_ROOT)" == "$EXPECTED_ROOT" ]] || die "Staging root mismatch"
  [[ "$(require_env_value STAGING_COMMIT_SHA)" == "$APPROVED_SHA" ]] || die "environment SHA mismatch"
  [[ "$(require_env_value SPRING_PROFILES_ACTIVE)" == "cloud" ]] || die "base Staging profile must be cloud"
  [[ "$(require_env_value STAGING_PRINT_MODE)" == "$EXPECTED_PRINTING_MODE" ]] || die "printing mode must be DISABLED"
  [[ "$(require_env_value STAGING_PRINTING_FEATURE_ENABLED)" == "false" ]] || die "printing feature must remain disabled"
  [[ "$(require_env_value BACKEND_IMAGE)" == *":staging-$APPROVED_SHA" ]] || die "backend image tag is not bound to the approved SHA"

  assert_release_identity

  assert_snapshot_integrity
  "$STAGING_DEPLOY_VALIDATOR" --validate --env-file "$ENV_FILE" >/dev/null || die "official Staging deploy validation failed"
  assert_snapshot_integrity
  assert_release_identity
}

validate_running_backend_identity() {
  local container_id inspect_line status configured_image running_image expected_image expected_image_id published_port health_status
  expected_image="$(require_env_value BACKEND_IMAGE)"
  container_id="$(controlled_compose ps -q backend)" || die "cannot resolve the Staging backend container"
  [[ "$container_id" =~ ^[0-9a-f]{12,64}$ ]] || die "exactly one Staging backend container must be running"
  inspect_line="$(controlled_docker inspect --format '{{.State.Status}}|{{.Config.Image}}|{{.Image}}' "$container_id")" ||
    die "cannot inspect the Staging backend identity"
  IFS='|' read -r status configured_image running_image <<<"$inspect_line"
  [[ "$status" == "running" ]] || die "Staging backend is not running"
  [[ "$configured_image" == "$expected_image" ]] || die "running backend image tag differs from the approved environment"
  [[ "$running_image" =~ ^sha256:[0-9a-f]{64}$ ]] || die "running backend image ID is invalid"
  expected_image_id="$(controlled_docker image inspect --format '{{.Id}}' "$expected_image")" ||
    die "approved backend image tag is unavailable"
  [[ "$expected_image_id" == "$running_image" ]] || die "approved backend image tag no longer resolves to the running image ID"
  RESOLVED_BACKEND_IMAGE_ID="$running_image"
  published_port="$(controlled_compose port nginx 80)" || die "cannot resolve the Staging HTTP binding"
  [[ "$published_port" == "127.0.0.1:18080" ]] || die "Staging HTTP binding must be exactly 127.0.0.1:18080"
  health_status="$(env -i PATH="$SAFE_PATH" "$CURL_BIN" --silent --show-error --max-time 10 \
    --output /dev/null --write-out '%{http_code}' http://127.0.0.1:18080/api/v1/system/health || true)"
  [[ "$health_status" == "200" ]] || die "Staging backend health must return HTTP 200"
}

create_immutable_image_override() {
  local temporary_dir resolved_images
  [[ "$RESOLVED_BACKEND_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] || die "immutable backend image ID is unavailable"
  temporary_dir="$(canonical_dir "${TMPDIR:-/tmp}")" || die "cannot canonicalize temporary directory"
  umask 077
  IMMUTABLE_IMAGE_OVERRIDE="$(mktemp "$temporary_dir/restaurant-pos-al003s-image.XXXXXX")"
  chmod 600 "$IMMUTABLE_IMAGE_OVERRIDE"
  printf 'services:\n  backend:\n    image: "%s"\n' "$RESOLVED_BACKEND_IMAGE_ID" >"$IMMUTABLE_IMAGE_OVERRIDE"
  [[ "$(file_mode "$IMMUTABLE_IMAGE_OVERRIDE")" == "600" ]] || die "immutable image override mode changed"
  resolved_images="$(controlled_compose config --images)" || die "immutable Compose image resolution failed"
  printf '%s\n' "$resolved_images" | grep -Fxq "$RESOLVED_BACKEND_IMAGE_ID" ||
    die "one-shot Compose configuration is not bound to the immutable backend image ID"
}

prepare_one_shot_container() {
  local existing
  ONE_SHOT_CONTAINER_NAME="${EXPECTED_PROJECT}-al003s-${ACTION}-$(action_request_fingerprint | cut -c1-12)"
  existing="$(controlled_docker ps -aq --filter "name=^/${ONE_SHOT_CONTAINER_NAME}$" --format '{{.ID}}')" ||
    die "cannot verify the scoped AL-003S one-shot container name"
  [[ -z "$existing" ]] || die "an AL-003S one-shot container with the approved identity already exists"
  ONE_SHOT_STARTED="true"
}

finalize_one_shot_container() {
  local existing
  existing="$(controlled_docker ps -aq --filter "name=^/${ONE_SHOT_CONTAINER_NAME}$" --format '{{.ID}}')" ||
    { echo "AL003S_ACCEPTANCE|NO_GO|cannot verify one-shot container cleanup" >&2; return 1; }
  if [[ -n "$existing" ]]; then
    controlled_docker rm -f "$ONE_SHOT_CONTAINER_NAME" >/dev/null ||
      { echo "AL003S_ACCEPTANCE|NO_GO|cannot remove the scoped AL-003S one-shot container" >&2; return 1; }
    existing="$(controlled_docker ps -aq --filter "name=^/${ONE_SHOT_CONTAINER_NAME}$" --format '{{.ID}}')" ||
      { echo "AL003S_ACCEPTANCE|NO_GO|cannot recheck one-shot container cleanup" >&2; return 1; }
    [[ -z "$existing" ]] ||
      { echo "AL003S_ACCEPTANCE|NO_GO|scoped AL-003S one-shot container remains after cleanup" >&2; return 1; }
  fi
  ONE_SHOT_STARTED="false"
  ONE_SHOT_CONTAINER_NAME=""
}

run_bootstrap() {
  local -a command_args=(
    -jar /app/app.jar
    "--run-id=$RUN_ID"
    "--organization-name=$ORGANIZATION_NAME"
    "--organization-code=$ORGANIZATION_CODE"
    "--source-store-name=$SOURCE_STORE_NAME"
    "--source-store-code=$SOURCE_STORE_CODE"
    "--owner-login=$OWNER_LOGIN"
    "--owner-name=$OWNER_NAME"
    "--expected-runtime-sha=$APPROVED_SHA"
    "--observed-runtime-sha=$APPROVED_SHA"
    "--tool-sha=$APPROVED_SHA"
    "--compose-project=$EXPECTED_PROJECT"
    "--staging-root=$EXPECTED_ROOT"
    "--printing-mode=$EXPECTED_PRINTING_MODE"
  )
  if [[ "$ACTION" == "bootstrap-execute" ]]; then
    command_args+=(--execute --password-stdin)
  fi
  prepare_one_shot_container
  controlled_compose_run --rm --no-deps -T \
    --name "$ONE_SHOT_CONTAINER_NAME" \
    --pull never \
    --entrypoint java \
    -e SPRING_PROFILES_ACTIVE=cloud,staging-synthetic-bootstrap \
    -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
    -e SPRING_FLYWAY_ENABLED=false \
    -e APP_FEATURES_PRINTING=false \
    -e STG005_BOOTSTRAP_COMMAND_ENABLED=true \
    -e STG005_SOURCE_MENU_COMMAND_ENABLED=false \
    backend "${command_args[@]}"
}

run_source_menu() {
  local -a command_args=(
    -jar /app/app.jar
    "--source-store-id=$SOURCE_STORE_ID"
    "--source-store-code=$SOURCE_STORE_CODE"
    "--expected-runtime-sha=$APPROVED_SHA"
    "--observed-runtime-sha=$APPROVED_SHA"
    "--tool-sha=$APPROVED_SHA"
    "--compose-project=$EXPECTED_PROJECT"
    "--staging-root=$EXPECTED_ROOT"
    "--printing-mode=$EXPECTED_PRINTING_MODE"
  )
  [[ "$ACTION" == "source-menu-execute" ]] && command_args+=(--execute)
  prepare_one_shot_container
  controlled_compose_run --rm --no-deps -T \
    --name "$ONE_SHOT_CONTAINER_NAME" \
    --pull never \
    --entrypoint java \
    -e SPRING_PROFILES_ACTIVE=cloud,staging-synthetic-bootstrap \
    -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
    -e SPRING_FLYWAY_ENABLED=false \
    -e APP_FEATURES_PRINTING=false \
    -e STG005_BOOTSTRAP_COMMAND_ENABLED=false \
    -e STG005_SOURCE_MENU_COMMAND_ENABLED=true \
    backend "${command_args[@]}" </dev/null
}

run_action() {
  local action_status=0 finalize_status=0 postcheck_status=0
  acquire_action_lock
  assert_snapshot_integrity
  assert_release_identity
  validate_running_backend_identity
  assert_snapshot_integrity
  assert_release_identity
  validate_readiness_evidence
  validate_action_approval
  create_immutable_image_override
  assert_snapshot_integrity
  assert_release_identity
  validate_running_backend_identity
  [[ "$RESOLVED_BACKEND_IMAGE_ID" == "$(grep -E '^    image: "sha256:[0-9a-f]{64}"$' "$IMMUTABLE_IMAGE_OVERRIDE" | sed -E 's/^    image: "(.*)"$/\1/')" ]] ||
    die "immutable backend image binding changed before execution"
  validate_readiness_evidence
  validate_action_approval
  assert_snapshot_integrity
  assert_release_identity
  if case "$ACTION" in
      bootstrap-plan|bootstrap-execute) run_bootstrap ;;
      source-menu-plan|source-menu-execute) run_source_menu ;;
      *) die "internal action dispatch error" ;;
    esac
  then
    action_status=0
  else
    action_status=$?
  fi
  if finalize_one_shot_container; then
    finalize_status=0
  else
    finalize_status=$?
    mark_action_blocked "scoped_container_cleanup_failed"
  fi
  if (
    validate_running_backend_identity
    validate_readiness_evidence
    assert_snapshot_integrity
    assert_release_identity
  ); then
    postcheck_status=0
  else
    postcheck_status=$?
  fi
  [[ "$action_status" -eq 0 ]] || mark_action_blocked "action_failed_requires_owner_review"
  [[ "$postcheck_status" -eq 0 ]] || mark_action_blocked "postcheck_failed_requires_owner_review"
  [[ "$finalize_status" -eq 0 ]] || die "one-shot cleanup failed; future AL-003S actions are blocked"
  [[ "$postcheck_status" -eq 0 ]] || die "post-action continuity check failed"
  [[ "$action_status" -eq 0 ]] || die "one-shot action failed with exit code $action_status"
}

main() {
  local seen_action="false" seen_options="|"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate)
        [[ "$seen_action" == "false" ]] || die "action was provided more than once"
        ACTION="validate"
        seen_action="true"
        ;;
      --action)
        [[ "$seen_action" == "false" && $# -ge 2 ]] || die "--action is missing or duplicated"
        ACTION="$2"
        seen_action="true"
        shift
        ;;
      --execute-runtime)
        [[ "$seen_options" != *"|$1|"* ]] || die "$1 was provided more than once"
        seen_options="${seen_options}${1}|"
        EXECUTE_RUNTIME="true"
        ;;
      --env-file|--approved-sha|--preflight-evidence|--preflight-evidence-sha256|--readiness-evidence|--readiness-evidence-sha256|--action-approval|--action-approval-sha256|--run-id|--organization-name|--organization-code|--source-store-name|--source-store-code|--owner-login|--owner-name|--source-store-id)
        [[ $# -ge 2 ]] || die "$1 requires a value"
        [[ "$seen_options" != *"|$1|"* ]] || die "$1 was provided more than once"
        seen_options="${seen_options}${1}|"
        case "$1" in
          --env-file) ENV_FILE="$2" ;;
          --approved-sha) APPROVED_SHA="$2" ;;
          --preflight-evidence) PREFLIGHT_EVIDENCE="$2" ;;
          --preflight-evidence-sha256) PREFLIGHT_EVIDENCE_SHA256="$2" ;;
          --readiness-evidence) READINESS_EVIDENCE="$2" ;;
          --readiness-evidence-sha256) READINESS_EVIDENCE_SHA256="$2" ;;
          --action-approval) ACTION_APPROVAL="$2" ;;
          --action-approval-sha256) ACTION_APPROVAL_SHA256="$2" ;;
          --run-id) RUN_ID="$2" ;;
          --organization-name) ORGANIZATION_NAME="$2" ;;
          --organization-code) ORGANIZATION_CODE="$2" ;;
          --source-store-name) SOURCE_STORE_NAME="$2" ;;
          --source-store-code) SOURCE_STORE_CODE="$2" ;;
          --owner-login) OWNER_LOGIN="$2" ;;
          --owner-name) OWNER_NAME="$2" ;;
          --source-store-id) SOURCE_STORE_ID="$2" ;;
        esac
        shift
        ;;
      --help|-h) usage; exit 0 ;;
      *) die "unsupported argument: $1" ;;
    esac
    shift
  done

  [[ -n "$ENV_FILE" && -n "$APPROVED_SHA" && -n "$PREFLIGHT_EVIDENCE" && -n "$PREFLIGHT_EVIDENCE_SHA256" ]] ||
    die "env, exact SHA, preflight evidence, and evidence digest are required"
  validate_action_arguments
  ENV_FILE="$(canonical_file "$ENV_FILE")" || die "cannot canonicalize environment file"
  PREFLIGHT_EVIDENCE="$(canonical_file "$PREFLIGHT_EVIDENCE")" || die "cannot canonicalize preflight evidence"
  if [[ "$ACTION" != "validate" ]]; then
    READINESS_EVIDENCE="$(canonical_file "$READINESS_EVIDENCE")" || die "cannot canonicalize runtime readiness evidence"
    ACTION_APPROVAL="$(canonical_file "$ACTION_APPROVAL")" || die "cannot canonicalize action approval"
  fi
  [[ -f "$STAGING_COMPOSE_FILE" && -x "$STAGING_DEPLOY_VALIDATOR" ]] || die "required Staging deployment controls are unavailable"
  DOCKER_BIN="$(command -v docker || true)"
  [[ "$DOCKER_BIN" == /* && -x "$DOCKER_BIN" ]] || die "Docker CLI is required"
  CURL_BIN="$(command -v curl || true)"
  [[ "$CURL_BIN" == /* && -x "$CURL_BIN" ]] || die "curl is required for loopback health verification"
  TIMEOUT_BIN="$(command -v timeout || true)"
  FLOCK_BIN="$(command -v flock || true)"
  [[ "$TIMEOUT_BIN" == /* && -x "$TIMEOUT_BIN" ]] || die "timeout is required for bounded Docker operations"
  if [[ "$ACTION" != "validate" ]]; then
    [[ "$FLOCK_BIN" == /* && -x "$FLOCK_BIN" ]] || die "flock is required for serialized one-shot actions"
  fi
  validate_release_and_evidence

  if [[ "$ACTION" == "validate" ]]; then
    echo "AL003S_ACCEPTANCE|VALIDATED|approved_sha=$APPROVED_SHA|project=$EXPECTED_PROJECT|printing=$EXPECTED_PRINTING_MODE"
    return 0
  fi
  run_action
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap cleanup EXIT
  trap cleanup ERR
  trap handle_interrupt INT
  trap handle_terminate TERM
  main "$@"
fi
