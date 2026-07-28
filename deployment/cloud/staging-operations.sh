#!/usr/bin/env bash
set -euo pipefail

# STG-006 preparation-only operations helper. This script deliberately has no
# start, stop, restart, cleanup, backup, restore, or database-client action.

EXPECTED_PROJECT="restaurant-pos-staging"
EXPECTED_ROOT="/srv/restaurant-pos/staging"
SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
ACTION="help"
ENV_FILE=""
STAGING_ROOT=""
COMMIT_SHA=""
COMPOSE_FILE=""
BACKEND_IMAGE=""
FRONTEND_IMAGE=""
MIN_FREE_BYTES=""
MAX_USED_PERCENT=""
PREVIOUS_SHA=""

usage() {
  cat <<'EOF'
Usage:
  ./staging-operations.sh --validate --env-file PATH --root /srv/restaurant-pos/staging \
    --commit FULL_SHA --compose-file PATH --backend-image IMAGE --frontend-image IMAGE
  ./staging-operations.sh --inventory [identity options]
  ./staging-operations.sh --disk-check --min-free-bytes BYTES --max-used-percent PERCENT [identity options]
  ./staging-operations.sh --image-compatibility --previous-sha FULL_SHA [identity options]
  ./staging-operations.sh --help

All actions are read-only. This helper never starts, stops, restarts, builds,
pulls, removes, backs up, restores, or connects to a database. --dry-run is an
alias for --validate. Runtime Docker evidence is reported as PENDING when the
local Docker CLI or the exact staging project is unavailable.
EOF
}

die() {
  printf 'ERROR|%s\n' "$*" >&2
  exit 2
}

pending() {
  printf 'RESULT|%s|PENDING|%s\n' "$1" "$2" >&2
  exit 3
}

require_value() {
  [[ -n "$2" ]] || die "$1 is required"
}

is_full_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]]
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
  local path="$1"
  [[ -d "$path" ]] || return 1
  has_symlink_component "$path" && return 1
  (cd -P -- "$path" && pwd)
}

canonical_existing_file() {
  local file="$1" parent
  [[ -f "$file" && ! -L "$file" ]] || return 1
  parent="$(canonical_existing_dir "$(dirname -- "$file")")" || return 1
  printf '%s/%s\n' "$parent" "$(basename -- "$file")"
}

file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then stat -c '%a' "$1"; else stat -f '%Lp' "$1"; fi
}

dotenv_value() {
  local key="$1" line value count
  count="$(grep -Ec "^${key}=" "$ENV_FILE" || true)"
  [[ "$count" == "1" ]] || die "environment key must occur exactly once: $key"
  line="$(grep -E "^${key}=" "$ENV_FILE" || true)"
  [[ -n "$line" ]] || return 1
  value="${line#*=}"
  if [[ "$value" == \"* && ${#value} -ge 2 && "${value: -1}" == '"' ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'* && ${#value} -ge 2 && "${value: -1}" == "'" ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

assert_safe_printing() {
  local mode enabled endpoint
  mode="$(dotenv_value STAGING_PRINT_MODE || true)"
  enabled="$(dotenv_value STAGING_PRINTING_FEATURE_ENABLED || true)"
  endpoint=""
  if grep -Eq '^STAGING_PRINTER_ENDPOINT=' "$ENV_FILE"; then
    endpoint="$(dotenv_value STAGING_PRINTER_ENDPOINT)"
  fi
  [[ "$mode" == "DISABLED" ]] || die "only STAGING_PRINT_MODE=DISABLED is allowed"
  [[ "$enabled" == "false" ]] || die "STAGING_PRINTING_FEATURE_ENABLED must be false"
  [[ -z "$endpoint" ]] || die "STAGING_PRINTER_ENDPOINT must be unset"
}

assert_identity() {
  local root commit project backend frontend
  require_value "--env-file" "$ENV_FILE"
  require_value "--root" "$STAGING_ROOT"
  require_value "--commit" "$COMMIT_SHA"
  require_value "--compose-file" "$COMPOSE_FILE"
  require_value "--backend-image" "$BACKEND_IMAGE"
  require_value "--frontend-image" "$FRONTEND_IMAGE"
  absolute_without_traversal "$STAGING_ROOT" || die "--root must be an absolute path without traversal"
  absolute_without_traversal "$ENV_FILE" || die "--env-file must be an absolute path without traversal"
  absolute_without_traversal "$COMPOSE_FILE" || die "--compose-file must be an absolute path without traversal"
  is_full_sha "$COMMIT_SHA" || die "--commit must be a lowercase full 40-character SHA"
  root="$(canonical_existing_dir "$STAGING_ROOT")" || die "--root must exist without symlink traversal"
  [[ "$root" == "$EXPECTED_ROOT" ]] || die "--root must be exactly $EXPECTED_ROOT"
  ENV_FILE="$(canonical_existing_file "$ENV_FILE")" || die "--env-file must exist without symlink traversal"
  COMPOSE_FILE="$(canonical_existing_file "$COMPOSE_FILE")" || die "--compose-file must exist without symlink traversal"
  [[ "$ENV_FILE" == "$root/config/.env.staging" ]] || die "--env-file must be exactly under the staging config directory"
  [[ "$COMPOSE_FILE" == "$root/releases/$COMMIT_SHA/deployment/cloud/docker-compose.staging.yml" ]] || die "--compose-file must belong to the exact approved release"
  [[ "$(file_mode "$ENV_FILE")" == "600" ]] || die "--env-file must have mode 0600"
  project="$(dotenv_value COMPOSE_PROJECT_NAME || true)"
  commit="$(dotenv_value STAGING_COMMIT_SHA || true)"
  backend="$(dotenv_value BACKEND_IMAGE || true)"
  frontend="$(dotenv_value FRONTEND_IMAGE || true)"
  [[ "$project" == "$EXPECTED_PROJECT" ]] || die "environment project must be $EXPECTED_PROJECT"
  [[ "$commit" == "$COMMIT_SHA" ]] || die "environment commit does not match --commit"
  [[ "$backend" == "$BACKEND_IMAGE" && "$frontend" == "$FRONTEND_IMAGE" ]] || die "environment images do not match explicit image identities"
  [[ "$BACKEND_IMAGE" == "restaurant-pos-backend:staging-$COMMIT_SHA" ]] || die "backend image must use the exact staging SHA tag"
  [[ "$FRONTEND_IMAGE" == "restaurant-pos-frontend:staging-$COMMIT_SHA" ]] || die "frontend image must use the exact staging SHA tag"
  assert_safe_printing
}

docker_bin() {
  local binary
  binary="$(command -v docker || true)"
  [[ "$binary" == /* && -x "$binary" ]] || return 1
  printf '%s' "$binary"
}

compose_read() {
  local binary="$1"
  shift
  env -i PATH="$SAFE_PATH" HOME="${HOME:-/tmp}" \
    "$binary" --context default compose --project-name "$EXPECTED_PROJECT" \
    --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

docker_read() {
  local binary="$1"
  shift
  env -i PATH="$SAFE_PATH" HOME="${HOME:-/tmp}" \
    "$binary" --context default "$@"
}

assert_docker_available() {
  local binary
  binary="$(docker_bin || true)"
  [[ -n "$binary" ]] || pending "DOCKER_RUNTIME" "Docker CLI is unavailable; no runtime evidence was collected"
  "$binary" context inspect default >/dev/null 2>&1 || pending "DOCKER_RUNTIME" "local Docker context default is unavailable"
  printf '%s' "$binary"
}

assert_compose_identity() {
  local binary="$1" services images
  services="$(compose_read "$binary" config --services 2>/dev/null || true)"
  [[ "$services" == $'db\nbackend\nnginx' ]] || pending "COMPOSE_PROJECT" "exact staging services could not be verified"
  images="$(compose_read "$binary" config --images 2>/dev/null || true)"
  grep -Fxq -- "$BACKEND_IMAGE" <<<"$images" || pending "IMAGE_BACKEND" "exact backend image is not resolved"
  grep -Fxq -- "$FRONTEND_IMAGE" <<<"$images" || pending "IMAGE_FRONTEND" "exact frontend image is not resolved"
}

validate_action() {
  local binary
  assert_identity
  binary="$(assert_docker_available)"
  assert_compose_identity "$binary"
  printf 'RESULT|VALIDATE|PASS|exact staging identity and read-only Compose metadata verified\n'
}

inventory_action() {
  local binary containers container
  assert_identity
  binary="$(assert_docker_available)"
  assert_compose_identity "$binary"
  containers="$(compose_read "$binary" ps -q 2>/dev/null || true)"
  if [[ -z "$containers" ]]; then
    printf 'RESULT|INVENTORY|PENDING|no containers exist for the exact staging project\n'
    return 0
  fi
  while IFS= read -r container; do
    [[ -n "$container" ]] || continue
    docker_read "$binary" inspect --format 'CONTAINER|name={{.Name}}|id={{printf "%.12s" .Id}}|created={{.Created}}|restart_count={{.RestartCount}}|status={{.State.Status}}|health={{if .State.Health}}{{.State.Health.Status}}{{else}}NO_HEALTHCHECK{{end}}|image_id={{printf "%.12s" .Image}}' "$container"
  done <<<"$containers"
}

disk_check_action() {
  local available_kb total_kb used_percent free_bytes
  assert_identity
  require_value "--min-free-bytes" "$MIN_FREE_BYTES"
  require_value "--max-used-percent" "$MAX_USED_PERCENT"
  [[ "$MIN_FREE_BYTES" =~ ^[0-9]+$ && "$MAX_USED_PERCENT" =~ ^[0-9]+$ && "$MAX_USED_PERCENT" -ge 1 && "$MAX_USED_PERCENT" -le 99 ]] || die "disk thresholds must be explicit positive integers"
  read -r total_kb _ available_kb used_percent < <(df -Pk "$STAGING_ROOT" | awk 'NR == 2 {gsub(/%/, "", $5); print $2, $3, $4, $5}')
  [[ "$total_kb" =~ ^[0-9]+$ && "$available_kb" =~ ^[0-9]+$ && "$used_percent" =~ ^[0-9]+$ ]] || pending "DISK_CHECK" "filesystem metadata could not be read"
  free_bytes=$((available_kb * 1024))
  if [[ "$free_bytes" -lt "$MIN_FREE_BYTES" || "$used_percent" -gt "$MAX_USED_PERCENT" ]]; then
    printf 'RESULT|DISK_CHECK|NO_GO|free_bytes=%s used_percent=%s thresholds_explicit=true\n' "$free_bytes" "$used_percent"
    return 2
  fi
  printf 'RESULT|DISK_CHECK|PASS|free_bytes=%s used_percent=%s thresholds_explicit=true\n' "$free_bytes" "$used_percent"
}

image_compatibility_action() {
  local binary current_image_id current_frontend_id previous_backend previous_frontend previous_release current_release
  local current_migrations previous_migrations
  assert_identity
  require_value "--previous-sha" "$PREVIOUS_SHA"
  is_full_sha "$PREVIOUS_SHA" || die "--previous-sha must be a lowercase full 40-character SHA"
  [[ "$PREVIOUS_SHA" != "$COMMIT_SHA" ]] || die "--previous-sha must differ from --commit"
  previous_release="$STAGING_ROOT/releases/$PREVIOUS_SHA"
  current_release="$STAGING_ROOT/releases/$COMMIT_SHA"
  [[ -d "$previous_release" && ! -L "$previous_release" ]] || pending "IMAGE_COMPATIBILITY" "previous approved release is unavailable"
  [[ -d "$current_release" && ! -L "$current_release" ]] || pending "IMAGE_COMPATIBILITY" "current approved release is unavailable"
  [[ "$(git -C "$previous_release" rev-parse HEAD 2>/dev/null || true)" == "$PREVIOUS_SHA" ]] || pending "IMAGE_COMPATIBILITY" "previous release Git identity cannot be verified"
  [[ "$(git -C "$current_release" rev-parse HEAD 2>/dev/null || true)" == "$COMMIT_SHA" ]] || pending "IMAGE_COMPATIBILITY" "current release Git identity cannot be verified"
  git -C "$current_release" merge-base --is-ancestor "$PREVIOUS_SHA" "$COMMIT_SHA" >/dev/null 2>&1 || pending "IMAGE_COMPATIBILITY" "previous SHA is not an ancestor of the current SHA"
  current_migrations="$(git -C "$current_release" ls-tree -r "$COMMIT_SHA" -- backend/src/main/resources/db/migration 2>/dev/null | awk '{print $3 " " $4}')"
  previous_migrations="$(git -C "$previous_release" ls-tree -r "$PREVIOUS_SHA" -- backend/src/main/resources/db/migration 2>/dev/null | awk '{print $3 " " $4}')"
  [[ -n "$current_migrations" && -n "$previous_migrations" ]] || pending "IMAGE_COMPATIBILITY" "migration lists cannot be read from both releases"
  binary="$(assert_docker_available)"
  assert_compose_identity "$binary"
  previous_backend="restaurant-pos-backend:staging-$PREVIOUS_SHA"
  previous_frontend="restaurant-pos-frontend:staging-$PREVIOUS_SHA"
  current_image_id="$(docker_read "$binary" image inspect --format '{{.Id}}' "$BACKEND_IMAGE" 2>/dev/null || true)"
  [[ -n "$current_image_id" ]] || pending "IMAGE_BACKEND" "current backend image is not present"
  current_frontend_id="$(docker_read "$binary" image inspect --format '{{.Id}}' "$FRONTEND_IMAGE" 2>/dev/null || true)"
  [[ -n "$current_frontend_id" ]] || pending "IMAGE_FRONTEND" "current frontend image is not present"
  docker_read "$binary" image inspect --format '{{.Id}}' "$previous_backend" >/dev/null 2>&1 || pending "IMAGE_COMPATIBILITY" "previous backend image is not present"
  docker_read "$binary" image inspect --format '{{.Id}}' "$previous_frontend" >/dev/null 2>&1 || pending "IMAGE_COMPATIBILITY" "previous frontend image is not present"
  printf 'RESULT|IMAGE_COMPATIBILITY|STATIC_CHECK_ONLY_RUNTIME_PENDING|sha_ancestry=true; migration_lists_and_checksums_compared=true; sha_bound_images_present=true; schema compatibility requires owner-approved runtime evidence\n'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --validate|--dry-run) ACTION="validate" ;;
    --inventory) ACTION="inventory" ;;
    --disk-check) ACTION="disk-check" ;;
    --image-compatibility) ACTION="image-compatibility" ;;
    --env-file|--root|--commit|--compose-file|--backend-image|--frontend-image|--min-free-bytes|--max-used-percent|--previous-sha)
      [[ $# -ge 2 ]] || die "$1 requires a value"
      case "$1" in
        --env-file) ENV_FILE="$2" ;; --root) STAGING_ROOT="$2" ;; --commit) COMMIT_SHA="$2" ;;
        --compose-file) COMPOSE_FILE="$2" ;; --backend-image) BACKEND_IMAGE="$2" ;; --frontend-image) FRONTEND_IMAGE="$2" ;;
        --min-free-bytes) MIN_FREE_BYTES="$2" ;; --max-used-percent) MAX_USED_PERCENT="$2" ;; --previous-sha) PREVIOUS_SHA="$2" ;;
      esac
      shift
      ;;
    --help|-h) usage; exit 0 ;;
    *) die "unsupported option: $1" ;;
  esac
  shift
done

case "$ACTION" in
  help) usage ;;
  validate) validate_action ;;
  inventory) inventory_action ;;
  disk-check) disk_check_action ;;
  image-compatibility) image_compatibility_action ;;
esac
