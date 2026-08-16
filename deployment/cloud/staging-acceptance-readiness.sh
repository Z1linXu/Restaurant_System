#!/usr/bin/env bash
set -Eeuo pipefail

# Passive evidence collector for an Owner-approved AL-003S command window.
# It reads only sanitized Docker metadata, host resources, and loopback health.

READINESS_SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-synthetic-acceptance.sh
source "$READINESS_SCRIPT_DIR/staging-synthetic-acceptance.sh"
ALLOW_SAFE_PRINTING_POLICY=true

MIN_AVAILABLE_MEMORY_KB=""
MIN_CPU_COUNT=""
MIN_FREE_DISK_KB=""
MAX_LOAD_PER_CPU_MILLI=""
PRODUCTION_PROJECT=""

readiness_usage() {
  cat <<'EOF'
Restaurant POS AL-003S passive readiness collector.

Usage:
  ./staging-acceptance-readiness.sh \
    --approved-sha <full-sha> \
    --preflight-evidence <absolute-path> \
    --preflight-evidence-sha256 <sha256> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --production-project cloud \
    --min-available-memory-kb <positive-integer> \
    --min-cpu-count <positive-integer> \
    --min-free-disk-kb <positive-integer> \
    --max-load-per-cpu-milli <positive-integer>

The command is passive: it creates no container and changes no Docker project.
Redirect its sanitized stdout to a new mode-0600 evidence file, hash that file,
and bind both digest and action request in a separately reviewed Owner approval.
EOF
}

emit_readiness_evidence() {
  local captured current_memory current_cpu current_disk current_load staging_fingerprint production_fingerprint
  current_memory="$(available_memory_kb)" || die "available memory cannot be read"
  current_cpu="$(cpu_count)" || die "CPU count cannot be read"
  current_disk="$(free_disk_kb)" || die "free disk space cannot be read"
  current_load="$(load_per_cpu_milli)" || die "normalized load cannot be read"
  [[ "$current_memory" -ge "$MIN_AVAILABLE_MEMORY_KB" ]] || die "available memory is below the approved threshold"
  [[ "$current_cpu" -ge "$MIN_CPU_COUNT" ]] || die "CPU count is below the approved threshold"
  [[ "$current_disk" -ge "$MIN_FREE_DISK_KB" ]] || die "free disk space is below the approved threshold"
  [[ "$current_load" -le "$MAX_LOAD_PER_CPU_MILLI" ]] || die "normalized load is above the approved threshold"
  staging_fingerprint="$(project_fingerprint "$EXPECTED_PROJECT")"
  production_fingerprint="$(project_fingerprint "$PRODUCTION_PROJECT")"
  captured="$(date +%s)"
  printf 'READINESS|STATUS|PASS\n'
  printf 'READINESS|CAPTURED_AT_EPOCH|%s\n' "$captured"
  printf 'READINESS|APPROVED_SHA|%s\n' "$APPROVED_SHA"
  printf 'READINESS|ENV_SHA256|%s\n' "$ENV_SNAPSHOT_SHA256"
  printf 'READINESS|PREFLIGHT_SHA256|%s\n' "$VALIDATED_PREFLIGHT_SHA256"
  printf 'READINESS|STAGING_PROJECT|%s\n' "$EXPECTED_PROJECT"
  printf 'READINESS|STAGING_FINGERPRINT|%s\n' "$staging_fingerprint"
  printf 'READINESS|PRODUCTION_PROJECT|%s\n' "$PRODUCTION_PROJECT"
  printf 'READINESS|PRODUCTION_FINGERPRINT|%s\n' "$production_fingerprint"
  printf 'READINESS|MIN_AVAILABLE_MEMORY_KB|%s\n' "$MIN_AVAILABLE_MEMORY_KB"
  printf 'READINESS|MIN_CPU_COUNT|%s\n' "$MIN_CPU_COUNT"
  printf 'READINESS|MIN_FREE_DISK_KB|%s\n' "$MIN_FREE_DISK_KB"
  printf 'READINESS|MAX_LOAD_PER_CPU_MILLI|%s\n' "$MAX_LOAD_PER_CPU_MILLI"
  printf 'READINESS|OBSERVED_AVAILABLE_MEMORY_KB|%s\n' "$current_memory"
  printf 'READINESS|OBSERVED_CPU_COUNT|%s\n' "$current_cpu"
  printf 'READINESS|OBSERVED_FREE_DISK_KB|%s\n' "$current_disk"
  printf 'READINESS|OBSERVED_LOAD_PER_CPU_MILLI|%s\n' "$current_load"
  printf 'READINESS|SUMMARY|PASS\n'
}

readiness_main() {
  local seen="|"
  ACTION="validate"
  EXECUTE_RUNTIME="false"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --env-file|--approved-sha|--preflight-evidence|--preflight-evidence-sha256|--production-project|--min-available-memory-kb|--min-cpu-count|--min-free-disk-kb|--max-load-per-cpu-milli)
        [[ $# -ge 2 ]] || die "$1 requires a value"
        [[ "$seen" != *"|$1|"* ]] || die "$1 was provided more than once"
        seen="${seen}${1}|"
        case "$1" in
          --env-file) ENV_FILE="$2" ;;
          --approved-sha) APPROVED_SHA="$2" ;;
          --preflight-evidence) PREFLIGHT_EVIDENCE="$2" ;;
          --preflight-evidence-sha256) PREFLIGHT_EVIDENCE_SHA256="$2" ;;
          --production-project) PRODUCTION_PROJECT="$2" ;;
          --min-available-memory-kb) MIN_AVAILABLE_MEMORY_KB="$2" ;;
          --min-cpu-count) MIN_CPU_COUNT="$2" ;;
          --min-free-disk-kb) MIN_FREE_DISK_KB="$2" ;;
          --max-load-per-cpu-milli) MAX_LOAD_PER_CPU_MILLI="$2" ;;
        esac
        shift
        ;;
      --help|-h) readiness_usage; exit 0 ;;
      *) die "unsupported readiness argument: $1" ;;
    esac
    shift
  done

  [[ -n "$ENV_FILE" && -n "$APPROVED_SHA" && -n "$PREFLIGHT_EVIDENCE" &&
     -n "$PREFLIGHT_EVIDENCE_SHA256" && -n "$PRODUCTION_PROJECT" &&
     -n "$MIN_AVAILABLE_MEMORY_KB" && -n "$MIN_CPU_COUNT" &&
     -n "$MIN_FREE_DISK_KB" && -n "$MAX_LOAD_PER_CPU_MILLI" ]] ||
    die "all readiness bindings and thresholds are required"
  [[ "$PRODUCTION_PROJECT" == "$EXPECTED_PRODUCTION_PROJECT" ]] || die "Production project must be exactly cloud"
  [[ "$MIN_AVAILABLE_MEMORY_KB" =~ ^[1-9][0-9]*$ ]] || die "minimum available memory must be a positive integer"
  [[ "$MIN_CPU_COUNT" =~ ^[1-9][0-9]*$ ]] || die "minimum CPU count must be a positive integer"
  [[ "$MIN_FREE_DISK_KB" =~ ^[1-9][0-9]*$ ]] || die "minimum free disk must be a positive integer"
  [[ "$MAX_LOAD_PER_CPU_MILLI" =~ ^[1-9][0-9]*$ ]] || die "maximum normalized load must be a positive integer"
  ENV_FILE="$(canonical_file "$ENV_FILE")" || die "cannot canonicalize environment file"
  PREFLIGHT_EVIDENCE="$(canonical_file "$PREFLIGHT_EVIDENCE")" || die "cannot canonicalize preflight evidence"
  DOCKER_BIN="$(command -v docker || true)"
  [[ "$DOCKER_BIN" == /* && -x "$DOCKER_BIN" ]] || die "Docker CLI is required"
  CURL_BIN="$(command -v curl || true)"
  [[ "$CURL_BIN" == /* && -x "$CURL_BIN" ]] || die "curl is required"
  TIMEOUT_BIN="$(command -v timeout || true)"
  [[ "$TIMEOUT_BIN" == /* && -x "$TIMEOUT_BIN" ]] || die "timeout is required for bounded Docker inspection"
  validate_action_arguments
  validate_release_and_evidence
  validate_running_backend_identity
  assert_snapshot_integrity
  assert_release_identity
  emit_readiness_evidence
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap cleanup EXIT
  trap cleanup ERR
  trap handle_interrupt INT
  trap handle_terminate TERM
  readiness_main "$@"
fi
