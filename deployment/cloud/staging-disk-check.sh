#!/usr/bin/env bash
set -Eeuo pipefail

# Read-only fixed-root Staging disk check.  It never invokes Docker, systemd,
# cleanup, or any application/database command.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-hygiene-common.sh
source "$SCRIPT_DIR/staging-hygiene-common.sh"

ACTION="validate"
ENV_FILE=""
DISK_WARNING_USED_PERCENT=80
DISK_CRITICAL_USED_PERCENT=90
DISK_WARNING_FREE_BYTES=$((10 * 1024 * 1024 * 1024))
DISK_CRITICAL_FREE_BYTES=$((5 * 1024 * 1024 * 1024))

usage() {
  cat <<'EOF'
Staging disk threshold check (read-only).

Usage:
  staging-disk-check.sh --dry-run \
    --env-file /srv/restaurant-pos/staging/config/.env.staging

The fixed thresholds are WARNING at >=80% used or <=10 GiB free, and CRITICAL
at >=90% used or <=5 GiB free.  The command only reads the exact Staging root;
it never invokes Docker, removes files, vacuums journald, or touches database or
Production paths.
EOF
}

disk_observation() {
  local row
  row="$(df -Pk "$HYGIENE_ROOT" 2>/dev/null | awk '
    NR == 2 {
      used = $5
      free = $4
      total = $2
      gsub(/%/, "", used)
      if (used ~ /^[0-9]+$/ && free ~ /^[0-9]+$/ && total ~ /^[0-9]+$/) {
        printf "%s|%s|%s\n", free, used, total
      }
      exit
    }
  ')"
  [[ "$row" =~ ^[0-9]+\|[0-9]+\|[0-9]+$ ]] || return 1
  printf '%s\n' "$row"
}

disk_status() {
  local free_kb="$1" used_percent="$2" total_kb="$3" free_bytes status="PASS" reason="within_thresholds"
  free_bytes=$((free_kb * 1024))
  if (( used_percent >= DISK_CRITICAL_USED_PERCENT || free_bytes <= DISK_CRITICAL_FREE_BYTES )); then
    status="CRITICAL"
    reason="critical_threshold_reached"
  elif (( used_percent >= DISK_WARNING_USED_PERCENT || free_bytes <= DISK_WARNING_FREE_BYTES )); then
    status="WARNING"
    reason="warning_threshold_reached"
  fi
  printf 'DISK_CHECK|SCHEMA|restaurant-pos-staging-disk-v1\n'
  printf 'DISK_CHECK|STATUS|%s\n' "$status"
  printf 'DISK_CHECK|REASON|%s\n' "$reason"
  printf 'DISK_CHECK|STAGING_ROOT|%s\n' "$HYGIENE_ROOT"
  printf 'DISK_CHECK|COMPOSE_PROJECT|%s\n' "$HYGIENE_EXPECTED_PROJECT"
  printf 'DISK_CHECK|USED_PERCENT|%s\n' "$used_percent"
  printf 'DISK_CHECK|FREE_BYTES|%s\n' "$free_bytes"
  printf 'DISK_CHECK|TOTAL_BYTES|%s\n' "$((total_kb * 1024))"
  printf 'DISK_CHECK|WARNING_USED_PERCENT|%s\n' "$DISK_WARNING_USED_PERCENT"
  printf 'DISK_CHECK|CRITICAL_USED_PERCENT|%s\n' "$DISK_CRITICAL_USED_PERCENT"
  printf 'DISK_CHECK|WARNING_FREE_BYTES|%s\n' "$DISK_WARNING_FREE_BYTES"
  printf 'DISK_CHECK|CRITICAL_FREE_BYTES|%s\n' "$DISK_CRITICAL_FREE_BYTES"
  printf 'DISK_CHECK|BOUNDARY|docker=untouched;volumes=untouched;database=untouched;production=untouched\n'
  case "$status" in
    PASS) return 0 ;;
    WARNING) return 1 ;;
    CRITICAL) return 2 ;;
  esac
}

main() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate|--dry-run)
        [[ "$ACTION" == "validate" ]] || hygiene_usage_error "only one action may be selected"
        ACTION="validate"
        ;;
      --env-file)
        [[ $# -ge 2 && -z "$ENV_FILE" ]] || hygiene_usage_error "--env-file requires one value and may appear once"
        ENV_FILE="$2"
        shift
        ;;
      --help|-h) usage; exit 0 ;;
      *) hygiene_usage_error "unsupported option: $1" ;;
    esac
    shift
  done
  [[ "$ACTION" == "validate" ]] || hygiene_usage_error "unsupported action"
  [[ -n "$ENV_FILE" ]] || hygiene_usage_error "--env-file is required"
  hygiene_validate_env_and_scope "$ENV_FILE"
  local observed free_kb used_percent total_kb
  observed="$(disk_observation || true)"
  [[ "$observed" =~ ^[0-9]+\|[0-9]+\|[0-9]+$ ]] || {
    printf 'DISK_CHECK|SCHEMA|restaurant-pos-staging-disk-v1\n'
    printf 'DISK_CHECK|STATUS|CRITICAL\n'
    printf 'DISK_CHECK|REASON|disk_metadata_unavailable\n'
    printf 'DISK_CHECK|STAGING_ROOT|%s\n' "$HYGIENE_ROOT"
    printf 'DISK_CHECK|COMPOSE_PROJECT|%s\n' "$HYGIENE_EXPECTED_PROJECT"
    printf 'DISK_CHECK|BOUNDARY|docker=untouched;volumes=untouched;database=untouched;production=untouched\n'
    exit 2
  }
  IFS='|' read -r free_kb used_percent total_kb <<<"$observed"
  disk_status "$free_kb" "$used_percent" "$total_kb"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
