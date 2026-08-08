#!/usr/bin/env bash
set -euo pipefail

# This is a same-host Staging preflight only. It never builds, starts, stops,
# pulls, restores, or changes Docker, database, Git, or server state.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXPECTED_STAGING_ROOT="/srv/restaurant-pos/staging"
EXPECTED_PROJECT="restaurant-pos-staging"
EXPECTED_PORT="18080"
EXPECTED_BIND="127.0.0.1"
ACTION=""
ENV_FILE=""
APPROVED_SHA=""
PRODUCTION_PROJECT=""
PRODUCTION_ROOT=""
MIN_FREE_BYTES=""
MAX_USED_PERCENT=""
MIN_AVAILABLE_MEMORY_KB=""
MIN_CPU_COUNT=""
FAILED=0
PENDING=0

usage() {
  cat <<'EOF'
Usage:
  ./staging-server-preflight.sh --validate \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --approved-sha <full-40-character-sha> \
    --production-project <explicit-production-project> \
    --production-root <explicit-production-root> \
    --min-free-bytes <positive-integer> \
    --max-used-percent <1-99> \
    --min-available-memory-kb <positive-integer> \
    --min-cpu-count <positive-integer>

  ./staging-server-preflight.sh --dry-run [same required inputs]

This command is read-only. It validates an Owner-approved same-host Staging
release without printing environment values, resolved Compose output, or
container environment data. It never invokes Compose build/up/pull/stop/down.
EOF
}

check_pass() { printf 'CHECK|%s|PASS|%s\n' "$1" "$2"; }
check_no_go() { printf 'CHECK|%s|NO_GO|%s\n' "$1" "$2"; FAILED=1; }
check_pending() { printf 'CHECK|%s|EVIDENCE_PENDING|%s\n' "$1" "$2"; PENDING=1; }
fatal_usage() { printf 'CHECK|INPUTS|NO_GO|%s\n' "$1" >&2; exit 2; }

is_full_sha() { [[ "$1" =~ ^[0-9a-f]{40}$ ]]; }
is_positive_integer() { [[ "$1" =~ ^[1-9][0-9]*$ ]]; }
is_percentage() { [[ "$1" =~ ^[1-9][0-9]?$ && "$1" -lt 100 ]]; }

path_has_symlink() {
  local path="$1" current="/" part old_ifs="$IFS"
  IFS='/'
  # shellcheck disable=SC2086
  set -- $path
  IFS="$old_ifs"
  for part in "$@"; do
    [[ -z "$part" ]] && continue
    current="$current$part"
    [[ ! -L "$current" ]] || return 0
    current="$current/"
  done
  return 1
}

canonical_existing_dir() {
  local path="$1"
  [[ -d "$path" && ! -L "$path" ]] || return 1
  path_has_symlink "$path" && return 1
  (cd -P -- "$path" && pwd)
}

canonical_existing_file() {
  local path="$1" parent
  [[ -f "$path" && ! -L "$path" ]] || return 1
  parent="$(canonical_existing_dir "$(dirname -- "$path")")" || return 1
  printf '%s/%s\n' "$parent" "$(basename -- "$path")"
}

validate_protected_postgres_leaf() {
  local path="$1" expected_parent="$2" parent_canonical owner mode
  [[ "$(dirname -- "$path")" == "$expected_parent" && "$(basename -- "$path")" == "postgres" ]] || return 1
  parent_canonical="$(canonical_existing_dir "$expected_parent")" || return 1
  [[ "$parent_canonical/postgres" == "$path" ]] || return 1
  [[ -d "$path" && ! -L "$path" ]] || return 1
  path_has_symlink "$path" && return 1
  owner="$(file_owner "$path" 2>/dev/null || true)"
  mode="$(file_mode "$path" 2>/dev/null || true)"
  [[ "$owner" == "$(id -u)" || "$owner" == "70" ]] || return 1
  [[ "$mode" == "700" ]] || return 1
}

file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then stat -c '%a' "$1"; else stat -f '%Lp' "$1"; fi
}

file_owner() {
  if stat -c '%u' "$1" >/dev/null 2>&1; then stat -c '%u' "$1"; else stat -f '%u' "$1"; fi
}

file_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

assert_clean_release() {
  local release="$EXPECTED_STAGING_ROOT/releases/$APPROVED_SHA" ignored submodule line
  if [[ ! -d "$release/.git" && ! -f "$release/.git" ]]; then
    check_no_go "RELEASE_GIT" "approved release is not a Git checkout"
    return
  fi
  if [[ "$(git -C "$release" rev-parse HEAD 2>/dev/null || true)" != "$APPROVED_SHA" ]]; then
    check_no_go "RELEASE_SHA" "release HEAD does not match approved SHA"
  else
    check_pass "RELEASE_SHA" "approved SHA matches release HEAD"
  fi
  if ! git -C "$release" diff --quiet || ! git -C "$release" diff --cached --quiet; then
    check_no_go "RELEASE_CLEAN" "release has tracked or staged changes"
  elif [[ -n "$(git -C "$release" status --porcelain=v1 --untracked-files=all)" ]]; then
    check_no_go "RELEASE_CLEAN" "release has untracked files"
  else
    ignored="$(git -C "$release" ls-files --others --ignored --exclude-standard -- backend frontend)"
    if [[ -n "$ignored" ]]; then
      check_no_go "RELEASE_BUILD_INPUTS" "release has ignored backend or frontend build inputs"
    else
      check_pass "RELEASE_CLEAN" "release and build inputs are clean"
    fi
  fi
  submodule="$(git -C "$release" submodule status --recursive 2>/dev/null || true)"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    case "${line:0:1}" in
      -|+|U) check_no_go "RELEASE_SUBMODULES" "release has an unclean submodule"; return ;;
    esac
  done <<<"$submodule"
  check_pass "RELEASE_SUBMODULES" "submodules are clean or absent"
}

assert_paths() {
  local release config state postgres env_canonical production_canonical staging_canonical
  release="$EXPECTED_STAGING_ROOT/releases/$APPROVED_SHA"
  config="$EXPECTED_STAGING_ROOT/config"
  state="$EXPECTED_STAGING_ROOT/state"
  postgres="$state/postgres"
  for path in "$EXPECTED_STAGING_ROOT" "$release" "$config" "$state"; do
    if ! canonical_existing_dir "$path" >/dev/null; then
      check_no_go "PATHS" "required Staging path is missing or traverses a symlink"
      return
    fi
  done
  if ! validate_protected_postgres_leaf "$postgres" "$state"; then
    check_no_go "PATHS" "protected PostgreSQL leaf has invalid topology, type, owner, mode, or symlink metadata"
    return
  fi
  staging_canonical="$(canonical_existing_dir "$EXPECTED_STAGING_ROOT")"
  [[ "$staging_canonical" == "$EXPECTED_STAGING_ROOT" ]] || { check_no_go "STAGING_ROOT" "Staging root is not the exact approved path"; return; }
  [[ "$(canonical_existing_dir "$release")" == "$release" ]] || { check_no_go "RELEASE_PATH" "release path is not exact"; return; }
  [[ "$ENV_FILE" == "$config/.env.staging" ]] || { check_no_go "ENV_PATH" "environment file is outside exact Staging config path"; return; }
  env_canonical="$(canonical_existing_file "$ENV_FILE" || true)"
  [[ "$env_canonical" == "$ENV_FILE" ]] || { check_no_go "ENV_PATH" "environment file is missing or traverses a symlink"; return; }
  production_canonical="$(canonical_existing_dir "$PRODUCTION_ROOT" || true)"
  if [[ -z "$production_canonical" ]]; then
    check_no_go "PRODUCTION_ROOT" "explicit production root is missing or traverses a symlink"
    return
  fi
  [[ "$production_canonical" != "$staging_canonical" && "$staging_canonical" != "$production_canonical"/* && "$production_canonical" != "$staging_canonical"/* ]] || {
    check_no_go "PATH_ISOLATION" "Staging root overlaps the explicit production root"; return;
  }
  [[ "$postgres" != "$production_canonical"/* ]] || { check_no_go "POSTGRES_ISOLATION" "Staging PostgreSQL path is inside production root"; return; }
  check_pass "PATHS" "Staging paths are isolated and the protected PostgreSQL leaf metadata is valid"
}

assert_env_metadata() {
  local config="$EXPECTED_STAGING_ROOT/config" owner mode
  owner="$(file_owner "$config" 2>/dev/null || true)"
  mode="$(file_mode "$config" 2>/dev/null || true)"
  [[ "$owner" == "$(id -u)" && "$mode" == "700" ]] || check_no_go "CONFIG_PERMISSIONS" "Staging config directory must be owner-only mode 0700"
  owner="$(file_owner "$ENV_FILE" 2>/dev/null || true)"
  mode="$(file_mode "$ENV_FILE" 2>/dev/null || true)"
  [[ "$owner" == "$(id -u)" && "$mode" == "600" ]] || check_no_go "ENV_PERMISSIONS" "Staging environment file must be owner-only mode 0600"
  [[ "$FAILED" -eq 0 ]] && check_pass "ENV_PERMISSIONS" "Staging environment metadata is owner-only"
}

assert_port_available_or_owned_by_staging() {
  local listeners listener_count nginx_ids nginx_count nginx_id ownership expected_ownership
  if ! command -v ss >/dev/null 2>&1; then
    check_pending "PORT_18080" "ss is unavailable; loopback port ownership is unverified"
    return
  fi
  if ! listeners="$(ss -H -ltn "sport = :$EXPECTED_PORT" 2>/dev/null)"; then
    check_pending "PORT_18080" "ss failed; loopback port ownership is unverified"
    return
  fi
  if [[ -z "${listeners//[[:space:]]/}" ]]; then
    check_pass "PORT_18080" "loopback Staging port 18080 is free"
    return
  fi

  listener_count="$(awk 'NF { count++ } END { print count + 0 }' <<<"$listeners")"
  if [[ "$listener_count" != "1" ]]; then
    check_no_go "PORT_18080" "port 18080 is not owned by exactly one listener"
    return
  fi
  if ! awk -v expected="$EXPECTED_BIND:$EXPECTED_PORT" 'NF && $4 != expected { invalid=1 } END { exit invalid }' <<<"$listeners"; then
    check_no_go "PORT_18080" "port 18080 has a public or unexpected listener"
    return
  fi
  if ! command -v docker >/dev/null 2>&1; then
    check_no_go "PORT_18080" "loopback listener ownership cannot be verified without Docker"
    return
  fi
  if ! nginx_ids="$(compose_read ps -q nginx 2>/dev/null)"; then
    check_no_go "PORT_18080" "expected Staging nginx ownership could not be queried"
    return
  fi
  nginx_count="$(awk 'NF { count++ } END { print count + 0 }' <<<"$nginx_ids")"
  if [[ "$nginx_count" != "1" ]]; then
    check_no_go "PORT_18080" "loopback listener is not owned by exactly one expected Staging nginx container"
    return
  fi
  nginx_id="$(awk 'NF { print; exit }' <<<"$nginx_ids")"
  if ! ownership="$(docker --context default inspect --format 'project={{index .Config.Labels "com.docker.compose.project"}}
service={{index .Config.Labels "com.docker.compose.service"}}
state={{.State.Status}}
{{range $port, $bindings := .NetworkSettings.Ports}}{{range $bindings}}binding={{$port}}|{{.HostIp}}|{{.HostPort}}
{{end}}{{end}}' "$nginx_id" 2>/dev/null)"; then
    check_no_go "PORT_18080" "expected Staging nginx metadata could not be inspected"
    return
  fi
  expected_ownership="project=$EXPECTED_PROJECT
service=nginx
state=running
binding=80/tcp|$EXPECTED_BIND|$EXPECTED_PORT"
  if [[ "$ownership" != "$expected_ownership" ]]; then
    check_no_go "PORT_18080" "loopback listener does not match the exact expected Staging nginx ownership and binding"
    return
  fi
  check_pass "PORT_18080" "retained expected Staging nginx owns the exact loopback port binding"
}

assert_host_resources() {
  local free_bytes available_kb cpu_count used_percent
  free_bytes="$(df -Pk "$EXPECTED_STAGING_ROOT" 2>/dev/null | awk 'NR == 2 { print $4 * 1024 }')"
  used_percent="$(df -Pk "$EXPECTED_STAGING_ROOT" 2>/dev/null | awk 'NR == 2 { gsub(/%/, "", $5); print $5 }')"
  if [[ -z "$free_bytes" || -z "$used_percent" ]]; then
    check_pending "DISK" "disk metadata is unavailable"
  elif [[ "$free_bytes" -lt "$MIN_FREE_BYTES" || "$used_percent" -gt "$MAX_USED_PERCENT" ]]; then
    check_no_go "DISK" "disk headroom is below Owner-supplied threshold"
  else
    check_pass "DISK" "disk headroom meets Owner-supplied threshold"
  fi
  available_kb="$(awk '/MemAvailable:/ { print $2; exit }' /proc/meminfo 2>/dev/null || true)"
  cpu_count="$(getconf _NPROCESSORS_ONLN 2>/dev/null || true)"
  if [[ -z "$available_kb" || -z "$cpu_count" ]]; then
    check_pending "HOST_RESOURCES" "CPU or available memory metadata is unavailable on this host"
  elif [[ "$available_kb" -lt "$MIN_AVAILABLE_MEMORY_KB" || "$cpu_count" -lt "$MIN_CPU_COUNT" ]]; then
    check_no_go "HOST_RESOURCES" "CPU or available memory is below an Owner-supplied threshold"
  else
    check_pass "HOST_RESOURCES" "CPU and available memory meet Owner-supplied thresholds"
  fi
}

compose_read() {
  docker --context default compose --project-name "$EXPECTED_PROJECT" --env-file "$ENV_FILE" \
    -f "$SCRIPT_DIR/docker-compose.staging.yml" "$@"
}

assert_compose_and_images() {
  local services backend_image frontend_image image_id
  if ! command -v docker >/dev/null 2>&1; then
    check_pending "DOCKER" "docker CLI is unavailable; Compose/image evidence is pending"
    return
  fi
  if ! docker context inspect default --format '{{.Endpoints.docker.Host}}' >/dev/null 2>&1; then
    check_no_go "DOCKER_CONTEXT" "Docker default context is unavailable"
    return
  fi
  check_pass "DOCKER_CONTEXT" "Docker default context is available"
  services="$(compose_read config --services 2>/dev/null || true)"
  if [[ "$services" != $'db\nbackend\nnginx' ]]; then
    check_no_go "COMPOSE_SERVICES" "Staging Compose services are not exactly db, backend, nginx"
  else
    check_pass "COMPOSE_SERVICES" "Staging Compose services are exactly db, backend, nginx"
  fi
  # staging-deploy validates secret-bearing resolved config privately. It does
  # not build/start and only prints a sanitized result.
  if "$SCRIPT_DIR/staging-deploy.sh" --env-file "$ENV_FILE" --validate >/dev/null 2>&1; then
    check_pass "STAGING_INPUTS" "Staging package validation passed with printing disabled"
  else
    check_no_go "STAGING_INPUTS" "Staging package validation failed"
  fi
  backend_image="restaurant-pos-backend:staging-$APPROVED_SHA"
  frontend_image="restaurant-pos-frontend:staging-$APPROVED_SHA"
  for image in "$backend_image" "$frontend_image"; do
    image_id="$(docker --context default image inspect --format '{{.Id}}' "$image" 2>/dev/null || true)"
    if [[ -n "$image_id" ]]; then
      check_pass "IMAGE_${image%%:*}" "SHA-specific image exists (ID recorded privately by operator)"
    else
      printf 'CHECK|IMAGE_%s|PENDING_PREBUILD|SHA-specific image is not built yet\n' "${image%%:*}"
    fi
  done
}

assert_project_scoped_container_metadata() {
  local ids id
  command -v docker >/dev/null 2>&1 || return
  ids="$(compose_read ps -q 2>/dev/null || true)"
  if [[ -z "$ids" ]]; then
    printf 'CHECK|CONTAINER_METADATA|NOT_APPLICABLE|no Staging containers exist before first approved start\n'
    return
  fi
  while IFS= read -r id; do
    [[ -z "$id" ]] && continue
    docker --context default inspect --format 'name={{.Name}} status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}NO_HEALTHCHECK{{end}} image={{.Image}}' "$id" >/dev/null 2>&1 || {
      check_no_go "CONTAINER_METADATA" "project-scoped container metadata could not be read"; return;
    }
  done <<<"$ids"
  check_pass "CONTAINER_METADATA" "project-scoped formatted container metadata is readable"
}

main() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate|--dry-run) [[ -z "$ACTION" ]] || fatal_usage "choose one action"; ACTION="validate" ;;
      --env-file) ENV_FILE="${2:-}"; shift ;;
      --approved-sha) APPROVED_SHA="${2:-}"; shift ;;
      --production-project) PRODUCTION_PROJECT="${2:-}"; shift ;;
      --production-root) PRODUCTION_ROOT="${2:-}"; shift ;;
      --min-free-bytes) MIN_FREE_BYTES="${2:-}"; shift ;;
      --max-used-percent) MAX_USED_PERCENT="${2:-}"; shift ;;
      --min-available-memory-kb) MIN_AVAILABLE_MEMORY_KB="${2:-}"; shift ;;
      --min-cpu-count) MIN_CPU_COUNT="${2:-}"; shift ;;
      --help|-h) usage; exit 0 ;;
      *) fatal_usage "unsupported option: $1" ;;
    esac
    shift
  done
  [[ "$ACTION" == "validate" ]] || { usage; exit 2; }
  [[ "$ENV_FILE" == "$EXPECTED_STAGING_ROOT/config/.env.staging" ]] || fatal_usage "--env-file must be the exact Staging env path"
  is_full_sha "$APPROVED_SHA" || fatal_usage "--approved-sha must be a lowercase full 40-character SHA"
  [[ -n "$PRODUCTION_PROJECT" && "$PRODUCTION_PROJECT" != "$EXPECTED_PROJECT" ]] || fatal_usage "--production-project must be explicit and differ from Staging"
  [[ "$PRODUCTION_ROOT" == /* && "$PRODUCTION_ROOT" != "$EXPECTED_STAGING_ROOT" ]] || fatal_usage "--production-root must be an explicit absolute non-Staging path"
  is_positive_integer "$MIN_FREE_BYTES" || fatal_usage "--min-free-bytes must be a positive integer"
  is_percentage "$MAX_USED_PERCENT" || fatal_usage "--max-used-percent must be 1-99"
  is_positive_integer "$MIN_AVAILABLE_MEMORY_KB" || fatal_usage "--min-available-memory-kb must be a positive integer"
  is_positive_integer "$MIN_CPU_COUNT" || fatal_usage "--min-cpu-count must be a positive integer"

  assert_paths
  assert_env_metadata
  assert_clean_release
  assert_port_available_or_owned_by_staging
  assert_host_resources
  assert_compose_and_images
  assert_project_scoped_container_metadata

  if [[ "$FAILED" -ne 0 ]]; then
    printf 'SUMMARY|NO_GO|failed_checks_present\n'
    exit 2
  fi
  if [[ "$PENDING" -ne 0 ]]; then
    printf 'SUMMARY|EVIDENCE_PENDING|owner action or prebuild evidence required\n'
    exit 3
  fi
  printf 'EVIDENCE|APPROVED_SHA|%s\n' "$APPROVED_SHA"
  printf 'EVIDENCE|STAGING_ROOT|%s\n' "$EXPECTED_STAGING_ROOT"
  printf 'EVIDENCE|COMPOSE_PROJECT|%s\n' "$EXPECTED_PROJECT"
  printf 'EVIDENCE|ENV_SHA256|%s\n' "$(file_digest "$ENV_FILE")"
  printf 'EVIDENCE|RESOURCE_THRESHOLDS|min_free_bytes=%s;max_used_percent=%s;min_available_memory_kb=%s;min_cpu_count=%s\n' \
    "$MIN_FREE_BYTES" "$MAX_USED_PERCENT" "$MIN_AVAILABLE_MEMORY_KB" "$MIN_CPU_COUNT"
  printf 'SUMMARY|PASS|same-host Staging preflight passed without state changes\n'
}

main "$@"
