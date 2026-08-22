#!/usr/bin/env bash
set -Eeuo pipefail

# BuildKit-only cache hygiene for the fixed Staging project.  It uses the
# machine-readable buildx disk-usage contract for review, never uses --all,
# never removes images/volumes, and requires an Owner-reviewed plan digest for
# any state-changing cache action.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-hygiene-common.sh
source "$SCRIPT_DIR/staging-hygiene-common.sh"

ACTION="dry-run"
ENV_FILE=""
PLAN_FILE=""
PLAN_SHA256=""
BUILDER="default"
DOCKER_BIN=""
JQ_BIN=""
ENV_BIN=""
CURRENT_SHA=""
ENV_DIGEST=""
STAGING_IMAGES=""
PRODUCTION_IMAGES=()
ACTIVE_IMAGES=()
CACHE_ELIGIBLE_IDS=""
CACHE_ELIGIBLE_DETAILS=""
CACHE_UNSAFE_LINES=""
CACHE_RECORD_COUNT=0
PLAN_PROTECTED_SET=""
PLAN_ELIGIBLE_SET=""
LIVE_PROTECTED_SET=""
LIVE_ELIGIBLE_SET=""

usage() {
  cat <<'EOF'
Staging BuildKit cache hygiene helper (fixed-root, fail-closed).

Usage:
  staging-buildkit-cache-hygiene.sh --dry-run \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --production-image <current-production-ref> \
    [--active-image <active-ref> ...]
  staging-buildkit-cache-hygiene.sh --protected-set <same bindings>
  staging-buildkit-cache-hygiene.sh --execute \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --production-image <current-production-ref> \
    --plan-file /srv/restaurant-pos/staging/evidence/<dry-run-plan> \
    --plan-sha256 <owner-reviewed-sha256>

The policy reviews only reclaimable, immutable, unshared, zero-use BuildKit
records older than 168h on the fixed default builder and retains at least 10GB
of cache. Execute rechecks the exact plan immediately before invoking the
BuildKit cache-only prune. Current Staging images, supplied active images and
current Production image references are printed as protected inputs; no image,
container, volume, database, or Production command exists in this helper.
EOF
}

append_unique_line() {
  local set_name="$1" value="$2" current
  current="${!set_name}"
  case "$current" in
    *$'\n'"$value"$'\n'*|"$value"$'\n'*|*$'\n'"$value"|"$value") return 0 ;;
  esac
  printf -v "$set_name" '%s%s\n' "$current" "$value"
}

validate_image_inputs() {
  local image
  [[ ${#PRODUCTION_IMAGES[@]} -gt 0 ]] || hygiene_usage_error "at least one --production-image is required"
  for image in "${PRODUCTION_IMAGES[@]}"; do
    hygiene_validate_image_ref "$image"
  done
  for image in "${ACTIVE_IMAGES[@]-}"; do
    [[ -n "$image" ]] || continue
    hygiene_validate_image_ref "$image"
  done
}

validate_scope_and_inputs() {
  local backend_image frontend_image
  [[ "$BUILDER" == "default" ]] || hygiene_usage_error "--builder is fixed to default"
  hygiene_validate_env_and_scope "$ENV_FILE"
  ENV_DIGEST="$(hygiene_file_digest "$ENV_FILE")"
  CURRENT_SHA="$HYGIENE_CURRENT_SHA"
  backend_image="$(hygiene_require_env_value "$ENV_FILE" BACKEND_IMAGE)"
  frontend_image="$(hygiene_require_env_value "$ENV_FILE" FRONTEND_IMAGE)"
  hygiene_validate_image_ref "$backend_image"
  hygiene_validate_image_ref "$frontend_image"
  [[ "$backend_image" == *":staging-$CURRENT_SHA" ]] || hygiene_die "Staging backend image is not bound to the exact current SHA"
  [[ "$frontend_image" == *":staging-$CURRENT_SHA" ]] || hygiene_die "Staging frontend image is not bound to the exact current SHA"
  STAGING_IMAGES="$backend_image"$'\n'"$frontend_image"$'\n'
  LIVE_PROTECTED_SET="$STAGING_IMAGES"
  for image in "${ACTIVE_IMAGES[@]-}" "${PRODUCTION_IMAGES[@]}"; do
    [[ -n "$image" ]] || continue
    append_unique_line LIVE_PROTECTED_SET "$image"
  done
  validate_image_inputs
}

docker_safe() {
  "$ENV_BIN" -i PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
    "$DOCKER_BIN" --context default "$@"
}

validate_docker_cli() {
  local context_host buildx_du_help buildx_prune_help builder_info image image_id running_images
  hygiene_validate_no_ambient_docker_overrides
  DOCKER_BIN="$(hygiene_require_command docker)"
  JQ_BIN="$(hygiene_require_command jq)"
  ENV_BIN="$(hygiene_require_command env)"
  context_host="$(docker_safe context inspect default --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
  [[ "$context_host" == unix://* ]] || hygiene_die "Docker default context must be a local Unix socket"
  builder_info="$(docker_safe buildx inspect --builder "$BUILDER" --format '{{.Name}}|{{.Driver}}' 2>/dev/null || true)"
  [[ "$builder_info" == *'|'* && "$builder_info" != *$'\n'* ]] || hygiene_die "fixed default BuildKit builder metadata is unavailable"
  buildx_du_help="$(docker_safe buildx du --help 2>/dev/null || true)"
  [[ "$buildx_du_help" == *'--format'* && "$buildx_du_help" == *'--filter'* ]] || hygiene_die "BuildKit disk-usage JSON/filter API is unavailable"
  buildx_prune_help="$(docker_safe buildx prune --help 2>/dev/null || true)"
  [[ "$buildx_prune_help" == *'--filter'* && "$buildx_prune_help" == *'--force'* && "$buildx_prune_help" == *'--keep-storage'* ]] || hygiene_die "BuildKit cache-only prune API is unavailable"

  running_images="$(docker_safe ps --format '{{.Image}}' 2>/dev/null)" || hygiene_die "active Docker image set is unavailable"
  while IFS= read -r image; do
    [[ -n "$image" ]] || continue
    hygiene_validate_image_ref "$image"
    ACTIVE_IMAGES+=("$image")
    append_unique_line LIVE_PROTECTED_SET "$image"
  done <<<"$running_images"

  for image in "${PRODUCTION_IMAGES[@]}" "${ACTIVE_IMAGES[@]-}"; do
    [[ -n "$image" ]] || continue
    image_id="$(docker_safe image inspect "$image" --format '{{.Id}}' 2>/dev/null || true)"
    [[ "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]] || hygiene_die "protected image reference is unavailable: $image"
  done
}

scan_cache_records() {
  local json="$1" record id reclaimable mutable shared usage last_used size type
  CACHE_ELIGIBLE_IDS=""
  CACHE_ELIGIBLE_DETAILS=""
  CACHE_UNSAFE_LINES=""
  CACHE_RECORD_COUNT=0
  while IFS= read -r record || [[ -n "$record" ]]; do
    [[ -n "$record" ]] || continue
    CACHE_RECORD_COUNT=$((CACHE_RECORD_COUNT + 1))
    id="$(printf '%s\n' "$record" | "$JQ_BIN" -r '.ID // empty')"
    reclaimable="$(printf '%s\n' "$record" | "$JQ_BIN" -r 'if .Reclaimable == true then "true" elif .Reclaimable == false then "false" else empty end')"
    mutable="$(printf '%s\n' "$record" | "$JQ_BIN" -r 'if .Mutable == true then "true" elif .Mutable == false then "false" else empty end')"
    shared="$(printf '%s\n' "$record" | "$JQ_BIN" -r 'if .Shared == true then "true" elif .Shared == false then "false" else empty end')"
    usage="$(printf '%s\n' "$record" | "$JQ_BIN" -r '.UsageCount // empty')"
    last_used="$(printf '%s\n' "$record" | "$JQ_BIN" -r '.LastUsedAt // empty')"
    size="$(printf '%s\n' "$record" | "$JQ_BIN" -r '.Size // empty')"
    type="$(printf '%s\n' "$record" | "$JQ_BIN" -r '.Type // empty')"
    [[ "$id" =~ ^[A-Za-z0-9._:-]+$ && "$reclaimable" =~ ^(true|false)$ && "$mutable" =~ ^(true|false)$ && "$shared" =~ ^(true|false)$ && "$usage" =~ ^[0-9]+$ && "$last_used" != *$'\n'* && "$size" =~ ^[0-9]+$ && -n "$type" ]] || hygiene_die "BuildKit disk-usage record is not machine-readable and safe"
    if [[ "$reclaimable" == "true" && "$mutable" == "false" && "$shared" == "false" && "$usage" == "0" ]]; then
      append_unique_line CACHE_ELIGIBLE_IDS "$id"
      CACHE_ELIGIBLE_DETAILS="${CACHE_ELIGIBLE_DETAILS}${id}|size_bytes=${size};last_used=${last_used}"$'\n'
    elif [[ "$reclaimable" == "true" ]]; then
      CACHE_UNSAFE_LINES="${CACHE_UNSAFE_LINES}BUILDKIT_CACHE|UNSAFE_RECLAIMABLE|${id}|mutable=${mutable};shared=${shared};usage_count=${usage}"$'\n'
    else
      printf 'BUILDKIT_CACHE|RETAINED_IN_USE|%s|reclaimable=%s\n' "$id" "$reclaimable"
    fi
  done <<<"$json"
}

read_cache() {
  local json
  json="$(docker_safe buildx du --builder "$BUILDER" --format json --filter 'until=168h' 2>/dev/null)" || hygiene_die "BuildKit disk usage could not be read"
  scan_cache_records "$json"
  [[ -z "$CACHE_UNSAFE_LINES" ]] || hygiene_die "reclaimable BuildKit records are not clearly eligible"
}

emit_protected_set() {
  local image
  printf 'BUILDKIT_CACHE|PROTECTED|STAGING_ROOT|%s\n' "$HYGIENE_ROOT"
  printf 'BUILDKIT_CACHE|PROTECTED|COMPOSE_PROJECT|%s\n' "$HYGIENE_EXPECTED_PROJECT"
  printf 'BUILDKIT_CACHE|PROTECTED|BUILDER|%s\n' "$BUILDER"
  for image in "${ACTIVE_IMAGES[@]-}"; do
    [[ -n "$image" ]] || continue
    printf 'BUILDKIT_CACHE|PROTECTED|ACTIVE_IMAGE|%s\n' "$image"
  done
  for image in "${PRODUCTION_IMAGES[@]}"; do
    printf 'BUILDKIT_CACHE|PROTECTED|PRODUCTION_IMAGE|%s\n' "$image"
  done
  while IFS= read -r image; do
    [[ -n "$image" ]] || continue
    printf 'BUILDKIT_CACHE|PROTECTED|STAGING_IMAGE|%s\n' "$image"
  done <<<"$STAGING_IMAGES"
}

emit_plan() {
  printf 'BUILDKIT_CACHE|SCHEMA|%s\n' "$HYGIENE_PLAN_SCHEMA"
  printf 'BUILDKIT_CACHE|ENVIRONMENT|%s\n' "$HYGIENE_EXPECTED_ENVIRONMENT"
  printf 'BUILDKIT_CACHE|COMPOSE_PROJECT|%s\n' "$HYGIENE_EXPECTED_PROJECT"
  printf 'BUILDKIT_CACHE|STAGING_ROOT|%s\n' "$HYGIENE_ROOT"
  printf 'BUILDKIT_CACHE|ENV_SHA256|%s\n' "$ENV_DIGEST"
  printf 'BUILDKIT_CACHE|BUILDER|%s\n' "$BUILDER"
  printf 'BUILDKIT_CACHE|CACHE_MIN_AGE|168h\n'
  printf 'BUILDKIT_CACHE|KEEP_STORAGE|10GB\n'
  emit_protected_set
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    local details
    details="$(printf '%s' "$CACHE_ELIGIBLE_DETAILS" | awk -F'|' -v wanted="$id" '$1 == wanted {sub(/^[^|]*\|/, ""); print; exit}')"
    printf 'BUILDKIT_CACHE|ELIGIBLE_ID|%s|%s\n' "$id" "$details"
  done <<<"$CACHE_ELIGIBLE_IDS"
  printf '%s' "$CACHE_UNSAFE_LINES"
}

plan_value() {
  local key="$1" file="$2"
  awk -F'|' -v wanted="$key" '$1 == "BUILDKIT_CACHE" && $2 == wanted {print $3; exit}' "$file"
}

validate_plan_shape() {
  local current_env current_builder boundary
  grep -Fxq "BUILDKIT_CACHE|SCHEMA|$HYGIENE_PLAN_SCHEMA" "$PLAN_FILE" || hygiene_die "BuildKit plan schema mismatch"
  grep -Fxq "BUILDKIT_CACHE|ENVIRONMENT|$HYGIENE_EXPECTED_ENVIRONMENT" "$PLAN_FILE" || hygiene_die "BuildKit plan environment mismatch"
  grep -Fxq "BUILDKIT_CACHE|COMPOSE_PROJECT|$HYGIENE_EXPECTED_PROJECT" "$PLAN_FILE" || hygiene_die "BuildKit plan project mismatch"
  grep -Fxq "BUILDKIT_CACHE|STAGING_ROOT|$HYGIENE_ROOT" "$PLAN_FILE" || hygiene_die "BuildKit plan root mismatch"
  current_env="$(plan_value ENV_SHA256 "$PLAN_FILE")"
  [[ "$current_env" == "$ENV_DIGEST" ]] || hygiene_die "BuildKit plan environment digest differs from live Staging"
  current_builder="$(plan_value BUILDER "$PLAN_FILE")"
  [[ "$current_builder" == "$BUILDER" ]] || hygiene_die "BuildKit plan builder differs from the fixed builder"
  grep -Fxq 'BUILDKIT_CACHE|CACHE_MIN_AGE|168h' "$PLAN_FILE" || hygiene_die "BuildKit plan age policy changed"
  grep -Fxq 'BUILDKIT_CACHE|KEEP_STORAGE|10GB' "$PLAN_FILE" || hygiene_die "BuildKit plan storage policy changed"
  boundary="$(grep -F 'BUILDKIT_CACHE|BOUNDARY|' "$PLAN_FILE" || true)"
  [[ "$boundary" == 'BUILDKIT_CACHE|BOUNDARY|action=BuildKit_cache_only;all=false;images=untouched;containers=untouched;volumes=untouched;database=untouched;production=untouched' ]] || hygiene_die "BuildKit plan boundary is unsafe"
}

read_plan_sets() {
  local line kind value
  PLAN_PROTECTED_SET=""
  PLAN_ELIGIBLE_SET=""
  while IFS='|' read -r prefix kind value rest; do
    [[ "$prefix" == BUILDKIT_CACHE ]] || continue
    case "$kind" in
      PROTECTED)
        case "$value" in
          ACTIVE_IMAGE|PRODUCTION_IMAGE|STAGING_IMAGE)
            [[ -n "$rest" ]] || hygiene_die "BuildKit plan protected image is empty"
            append_unique_line PLAN_PROTECTED_SET "${value}|${rest%%|*}"
            ;;
        esac
        ;;
      ELIGIBLE_ID) append_unique_line PLAN_ELIGIBLE_SET "$value" ;;
    esac
  done <"$PLAN_FILE"
}

assert_protected_inputs_match_plan() {
  local image protected
  while IFS= read -r image; do
    [[ -n "$image" ]] || continue
    protected="STAGING_IMAGE|$image"
    case "${PLAN_PROTECTED_SET}" in *$'\n'"$protected"$'\n'*|"$protected"$'\n'*|*$'\n'"$protected"|"$protected") ;; *) hygiene_die "BuildKit plan omits protected Staging image" ;; esac
  done <<<"$STAGING_IMAGES"
  for image in "${ACTIVE_IMAGES[@]-}"; do
    [[ -n "$image" ]] || continue
    protected="ACTIVE_IMAGE|$image"
    case "${PLAN_PROTECTED_SET}" in *$'\n'"$protected"$'\n'*|"$protected"$'\n'*|*$'\n'"$protected"|"$protected") ;; *) hygiene_die "BuildKit plan omits protected active image" ;; esac
  done
  for image in "${PRODUCTION_IMAGES[@]}"; do
    protected="PRODUCTION_IMAGE|$image"
    case "${PLAN_PROTECTED_SET}" in *$'\n'"$protected"$'\n'*|"$protected"$'\n'*|*$'\n'"$protected"|"$protected") ;; *) hygiene_die "BuildKit plan omits protected Production image" ;; esac
  done
}

assert_eligible_ids_match_plan() {
  local id
  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    case "$PLAN_ELIGIBLE_SET" in *$'\n'"$id"$'\n'*|"$id"$'\n'*|*$'\n'"$id"|"$id") ;; *) hygiene_die "BuildKit cache gained an unreviewed eligible ID: $id" ;; esac
  done <<<"$CACHE_ELIGIBLE_IDS"
}

execute_plan() {
  local id
  [[ -n "$PLAN_FILE" && -n "$PLAN_SHA256" ]] || hygiene_usage_error "execute requires --plan-file and --plan-sha256"
  hygiene_acquire_lock
  hygiene_validate_plan_file "$PLAN_FILE" "$PLAN_SHA256"
  validate_plan_shape
  read_plan_sets
  printf 'BUILDKIT_CACHE|EXECUTE|PLAN_VERIFIED|%s\n' "$PLAN_SHA256"
  emit_protected_set
  read_cache >/dev/null
  assert_protected_inputs_match_plan
  assert_eligible_ids_match_plan
  if [[ -z "$CACHE_ELIGIBLE_IDS" ]]; then
    printf 'BUILDKIT_CACHE|STATUS|PASS|nothing_eligible;idempotent_noop\n'
    printf 'BUILDKIT_CACHE|BOUNDARY|action=BuildKit_cache_only;all=false;images=untouched;containers=untouched;volumes=untouched;database=untouched;production=untouched\n'
    return 0
  fi
  docker_safe buildx prune --builder "$BUILDER" --filter 'until=168h' --keep-storage 10GB --force >/dev/null 2>&1 || hygiene_die "BuildKit cache-only prune failed; no fallback cleanup exists"
  printf 'BUILDKIT_CACHE|STATUS|PASS|eligible_cache_only_pruned\n'
  printf 'BUILDKIT_CACHE|BOUNDARY|action=BuildKit_cache_only;all=false;images=untouched;containers=untouched;volumes=untouched;database=untouched;production=untouched\n'
}

main() {
  local json
  PRODUCTION_IMAGES=()
  ACTIVE_IMAGES=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run|--validate) ACTION="dry-run" ;;
      --protected-set) ACTION="protected-set" ;;
      --execute) ACTION="execute" ;;
      --builder)
        [[ $# -ge 2 && "$BUILDER" == "default" ]] || hygiene_usage_error "--builder requires one value and is fixed to default"
        BUILDER="$2"
        shift
        ;;
      --env-file)
        [[ $# -ge 2 && -z "$ENV_FILE" ]] || hygiene_usage_error "--env-file requires one value and may appear once"
        ENV_FILE="$2"
        shift
        ;;
      --production-image)
        [[ $# -ge 2 ]] || hygiene_usage_error "--production-image requires a value"
        PRODUCTION_IMAGES+=("$2")
        shift
        ;;
      --active-image)
        [[ $# -ge 2 ]] || hygiene_usage_error "--active-image requires a value"
        ACTIVE_IMAGES+=("$2")
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
  validate_scope_and_inputs
  validate_docker_cli
  case "$ACTION" in
    dry-run|protected-set)
      if [[ "$ACTION" == "protected-set" ]]; then
        printf 'BUILDKIT_CACHE|PROTECTED_SET|BEGIN\n'
        emit_protected_set
        printf 'BUILDKIT_CACHE|PROTECTED_SET|END\n'
        read_cache >/dev/null
        printf 'BUILDKIT_CACHE|STATUS|PASS|protected_set_only\n'
      else
        emit_protected_set
        read_cache >/dev/null
        emit_plan
        printf 'BUILDKIT_CACHE|STATUS|PASS|dry_run_only\n'
      fi
      printf 'BUILDKIT_CACHE|BOUNDARY|action=BuildKit_cache_only;all=false;images=untouched;containers=untouched;volumes=untouched;database=untouched;production=untouched\n'
      ;;
    execute) execute_plan ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
