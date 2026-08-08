#!/usr/bin/env bash
set -Eeuo pipefail

# Approval-bound runtime evidence and same-container restart helper. Runtime
# use is never implicit: both actions require --execute-runtime, fresh AL-003S
# readiness/preflight evidence, and a one-use OPS-001 Owner approval.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-synthetic-acceptance.sh
source "$SCRIPT_DIR/staging-synthetic-acceptance.sh"
# shellcheck source=staging-ops-common.sh
source "$SCRIPT_DIR/staging-ops-common.sh"

ACTION="validate"
EXECUTE_RUNTIME="false"
APPROVAL_FILE=""
APPROVAL_SHA256=""
RESTART_MUTATION_STARTED="false"
BEFORE_PROJECT_FINGERPRINT=""
BEFORE_FLYWAY_DIGEST=""
BEFORE_CONTAINER_IDS=""

usage() {
  cat <<'EOF'
Usage:
  staging-runtime-evidence.sh --validate <common-bindings>
  staging-runtime-evidence.sh --execute-runtime --action collect-evidence <common-bindings> <runtime-bindings>
  staging-runtime-evidence.sh --execute-runtime --action same-image-restart <common-bindings> <runtime-bindings>

Common bindings: --approved-sha, --env-file, --preflight-evidence and digest.
Runtime bindings: fresh --readiness-evidence and digest, plus one action-specific
--approval and digest. collect-evidence is read-only. same-image-restart uses
only Compose stop/start for the existing db/backend/nginx containers and fails
closed if container IDs, image IDs, Flyway history, isolation, or health drift.
No down, rm, pull, build, migrate, repair, clean, or Production action exists.
EOF
}

runtime_scope() {
  printf 'preflight=%s;readiness=%s;project=%s' "$PREFLIGHT_EVIDENCE_SHA256" "$READINESS_EVIDENCE_SHA256" "$EXPECTED_PROJECT"
}

validate_runtime_approval() {
  ops001_validate_approval "$APPROVAL_FILE" "$APPROVAL_SHA256" "$ACTION" "$APPROVED_SHA" "$ENV_SNAPSHOT_SHA256" "$(runtime_scope)"
  OPS001_APPROVAL_FILE="$APPROVAL_FILE"
  ops001_assert_approval_unchanged
}

container_identity_lines() {
  local service id line result=""
  for service in db backend nginx; do
    id="$(controlled_compose ps -q "$service")" || die "cannot resolve $service container"
    [[ -n "$id" ]] || die "$service container is missing"
    line="$(controlled_docker inspect --format '{{.Id}}|{{.Image}}|{{.State.Status}}|{{.RestartCount}}' "$id")" || die "cannot inspect $service container"
    [[ "$line" == *'|running|'* ]] || die "$service container is not running"
    result="${result}${service}|${line}"$'\n'
  done
  printf '%s' "$result"
}

expected_flyway_manifest() {
  local migration_dir="$SCRIPT_DIR/../../backend/src/main/resources/db/migration" manifest="$SCRIPT_DIR/ops001-flyway-checksums.txt"
  local file base version checksum extra previous_version=0 expected_pairs="" actual_pairs="" output=""
  [[ -d "$migration_dir" && ! -L "$migration_dir" ]] || die "repository migration directory is unavailable"
  [[ -f "$manifest" && ! -L "$manifest" ]] || die "trusted Flyway checksum manifest is unavailable"
  while IFS='|' read -r version base checksum extra || [[ -n "$version$base$checksum$extra" ]]; do
    [[ -z "$version" || "$version" == \#* ]] && continue
    [[ -z "$extra" && "$version" =~ ^[1-9][0-9]*$ && "$base" =~ ^V[1-9][0-9]*__[A-Za-z0-9_]+\.sql$ && "$checksum" =~ ^-?[0-9]+$ ]] ||
      die "trusted Flyway checksum manifest is invalid"
    (( version > previous_version )) || die "trusted Flyway checksum manifest is duplicated or unordered"
    previous_version="$version"
    [[ "$base" == "V${version}__"* && -f "$migration_dir/$base" && ! -L "$migration_dir/$base" ]] ||
      die "trusted Flyway checksum manifest does not match repository files"
    expected_pairs="${expected_pairs}${version}|${base}"$'\n'
    output="${output}${version}|${base}|${checksum}"$'\n'
  done <"$manifest"
  expected_pairs="${expected_pairs%$'\n'}"; output="${output%$'\n'}"
  for file in "$migration_dir"/V[0-9]*__*.sql; do
    [[ -f "$file" && ! -L "$file" ]] || die "repository migration manifest is invalid"
    base="$(basename -- "$file")"; version="${base#V}"; version="${version%%__*}"
    [[ "$version" =~ ^[1-9][0-9]*$ ]] || die "repository migration version is invalid"
    actual_pairs="${actual_pairs}${version}|${base}"$'\n'
  done
  actual_pairs="$(printf '%s' "$actual_pairs" | sort -t '|' -k1,1n)"
  [[ "$actual_pairs" == "$expected_pairs" ]] || die "trusted Flyway checksum manifest does not cover the exact repository migration set"
  printf '%s\n' "$output"
}

collect_flyway_rows() {
  controlled_compose exec -T db sh -eu -c 'psql -X -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select installed_rank::text || chr(124) || version || chr(124) || script || chr(124) || success::text || chr(124) || checksum::text from flyway_schema_history order by installed_rank"' ||
    die "Flyway history collection failed"
}

validate_flyway_rows() {
  local rows="$1" expected actual="" rank version script success checksum previous_rank=0
  [[ -n "$rows" ]] || die "Flyway history is empty"
  expected="$(expected_flyway_manifest)"
  [[ -n "$expected" ]] || die "repository migration manifest is empty"
  while IFS='|' read -r rank version script success checksum; do
    [[ "$rank" =~ ^[1-9][0-9]*$ && "$version" =~ ^[1-9][0-9]*$ && "$script" =~ ^V[1-9][0-9]*__[A-Za-z0-9_]+\.sql$ && "$success" == "t" && "$checksum" =~ ^-?[0-9]+$ ]] ||
      die "Flyway history contains an invalid or failed row"
    (( rank > previous_rank )) || die "Flyway installed ranks are duplicated or unordered"
    previous_rank="$rank"
    actual="${actual}${version}|${script}|${checksum}"$'\n'
  done <<<"$rows"
  actual="${actual%$'\n'}"
  [[ "$actual" == "$expected" ]] || die "Flyway history does not exactly match the release migration manifest"
}

flyway_digest() {
  local rows
  rows="$(collect_flyway_rows)"
  validate_flyway_rows "$rows"
  printf '%s\n' "$rows" | string_digest
}

flyway_summary() {
  local rows count max_version digest
  rows="$(collect_flyway_rows)"
  validate_flyway_rows "$rows"
  count="$(printf '%s\n' "$rows" | awk 'NF {count++} END {print count+0}')"
  max_version="$(printf '%s\n' "$rows" | awk -F'|' '$2+0>max {max=$2+0} END {print max+0}')"
  digest="$(printf '%s\n' "$rows" | string_digest)"
  printf '%s|%s|%s\n' "$count" "$max_version" "$digest"
}

emit_evidence() {
  local phase="$1" identities summary count max_version digest service id image status restarts
  identities="$(container_identity_lines)"
  summary="$(flyway_summary)"
  IFS='|' read -r count max_version digest <<<"$summary"
  printf 'OPS001_RUNTIME|%s|APPROVED_SHA|%s\n' "$phase" "$APPROVED_SHA"
  printf 'OPS001_RUNTIME|%s|ENV_SHA256|%s\n' "$phase" "$ENV_SNAPSHOT_SHA256"
  while IFS='|' read -r service id image status restarts; do
    [[ -n "$service" ]] || continue
    printf 'OPS001_RUNTIME|%s|CONTAINER|%s|%s|%s|%s\n' "$phase" "$service" "$id" "$image" "$restarts"
  done <<<"$identities"
  printf 'OPS001_RUNTIME|%s|FLYWAY|count=%s|max_version=%s|digest=%s\n' "$phase" "$count" "$max_version" "$digest"
  printf 'OPS001_RUNTIME|%s|PROJECT_FINGERPRINT|%s\n' "$phase" "$(project_fingerprint "$EXPECTED_PROJECT")"
  printf 'OPS001_RUNTIME|%s|STATUS|PASS\n' "$phase"
}

wait_service() {
  local service="$1" attempts=30 id state
  while (( attempts > 0 )); do
    id="$(controlled_compose ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$id" ]]; then
      state="$(controlled_docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}NO_HEALTHCHECK{{end}}' "$id" 2>/dev/null || true)"
      [[ "$state" == 'running|healthy' || "$state" == 'running|NO_HEALTHCHECK' ]] && return 0
    fi
    sleep 2; attempts=$((attempts - 1))
  done
  die "$service did not become ready within 60 seconds"
}

capture_before_restart() {
  BEFORE_PROJECT_FINGERPRINT="$(project_fingerprint "$EXPECTED_PROJECT")"
  BEFORE_FLYWAY_DIGEST="$(flyway_digest)"
  BEFORE_CONTAINER_IDS="$(container_identity_lines | cut -d'|' -f1-3)"
}

same_image_restart() {
  local after_ids after_flyway after_project http_code
  assert_snapshot_integrity
  assert_release_identity
  validate_running_backend_identity
  validate_readiness_evidence
  validate_runtime_approval
  ops001_assert_approval_unchanged
  capture_before_restart
  ops001_assert_approval_unchanged
  assert_snapshot_integrity
  assert_release_identity
  RESTART_MUTATION_STARTED="true"
  controlled_compose stop nginx backend db
  controlled_compose start db; wait_service db
  controlled_compose start backend; wait_service backend
  controlled_compose start nginx; wait_service nginx
  http_code="$("$CURL_BIN" --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 15 --noproxy '*' http://127.0.0.1:18080/api/v1/system/health)" || die "loopback health request failed"
  [[ "$http_code" == "200" ]] || die "loopback health must return HTTP 200"
  after_ids="$(container_identity_lines | cut -d'|' -f1-3)"
  after_flyway="$(flyway_digest)"
  after_project="$(project_fingerprint "$EXPECTED_PROJECT")"
  [[ "$after_ids" == "$BEFORE_CONTAINER_IDS" ]] || die "container or image identity changed during restart"
  [[ "$after_flyway" == "$BEFORE_FLYWAY_DIGEST" ]] || die "Flyway history changed during restart"
  [[ "$after_project" == "$BEFORE_PROJECT_FINGERPRINT" ]] || die "project fingerprint changed during restart"
  RESTART_MUTATION_STARTED="false"
  emit_evidence AFTER_RESTART
}

collect_evidence() {
  assert_snapshot_integrity
  assert_release_identity
  validate_running_backend_identity
  validate_readiness_evidence
  validate_runtime_approval
  ops001_assert_approval_unchanged
  emit_evidence COLLECT
}

run_runtime_action() {
  acquire_action_lock
  validate_release_and_evidence
  validate_running_backend_identity
  validate_readiness_evidence
  validate_runtime_approval
  ops001_assert_approval_unchanged
  ops001_consume_approval
  case "$ACTION" in collect-evidence) collect_evidence ;; same-image-restart) same_image_restart ;; *) die "unsupported action: $ACTION" ;; esac
}

runtime_error() {
  local status=$?
  if [[ "$RESTART_MUTATION_STARTED" == "true" ]]; then mark_action_blocked "OPS001_RUNTIME_RESTART_FAILED" || true; fi
  cleanup || true
  exit "$status"
}

main() {
  local seen="|"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate) ACTION="validate" ;;
      --execute-runtime) EXECUTE_RUNTIME="true" ;;
      --action|--env-file|--approved-sha|--preflight-evidence|--preflight-evidence-sha256|--readiness-evidence|--readiness-evidence-sha256|--approval|--approval-sha256)
        [[ $# -ge 2 && "$seen" != *"|$1|"* ]] || die "$1 requires one value and may appear once"
        seen="${seen}${1}|"
        case "$1" in --action) ACTION="$2" ;; --env-file) ENV_FILE="$2" ;; --approved-sha) APPROVED_SHA="$2" ;; --preflight-evidence) PREFLIGHT_EVIDENCE="$2" ;; --preflight-evidence-sha256) PREFLIGHT_EVIDENCE_SHA256="$2" ;; --readiness-evidence) READINESS_EVIDENCE="$2" ;; --readiness-evidence-sha256) READINESS_EVIDENCE_SHA256="$2" ;; --approval) APPROVAL_FILE="$2" ;; --approval-sha256) APPROVAL_SHA256="$2" ;; esac
        shift ;;
      --help|-h) usage; exit 0 ;;
      *) die "unsupported runtime-evidence option: $1" ;;
    esac
    shift
  done
  [[ -n "$ENV_FILE$APPROVED_SHA$PREFLIGHT_EVIDENCE$PREFLIGHT_EVIDENCE_SHA256" ]] || die "exact SHA, environment, and preflight bindings are required"
  ENV_FILE="$(canonical_file "$ENV_FILE")" || die "cannot canonicalize environment file"
  PREFLIGHT_EVIDENCE="$(canonical_file "$PREFLIGHT_EVIDENCE")" || die "cannot canonicalize preflight evidence"
  DOCKER_BIN="$(command -v docker || true)"; CURL_BIN="$(command -v curl || true)"; TIMEOUT_BIN="$(command -v timeout || true)"; FLOCK_BIN="$(command -v flock || true)"
  [[ "$DOCKER_BIN" == /* && "$CURL_BIN" == /* && "$TIMEOUT_BIN" == /* && "$FLOCK_BIN" == /* ]] || die "docker, curl, timeout, and flock are required"
  if [[ "$ACTION" == "validate" ]]; then
    [[ "$EXECUTE_RUNTIME" == "false" && -z "$READINESS_EVIDENCE$READINESS_EVIDENCE_SHA256$APPROVAL_FILE$APPROVAL_SHA256" ]] || die "validation accepts no runtime/approval inputs"
    validate_release_and_evidence
    validate_running_backend_identity
    printf 'OPS001_RUNTIME|VALIDATE|PASS|no runtime action executed\n'; return
  fi
  [[ "$EXECUTE_RUNTIME" == "true" ]] || die "$ACTION requires --execute-runtime"
  [[ -n "$READINESS_EVIDENCE$READINESS_EVIDENCE_SHA256$APPROVAL_FILE$APPROVAL_SHA256" ]] || die "$ACTION requires readiness and Owner approval bindings"
  READINESS_EVIDENCE="$(canonical_file "$READINESS_EVIDENCE")" || die "cannot canonicalize readiness evidence"
  run_runtime_action
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap runtime_error ERR INT TERM
  trap cleanup EXIT
  main "$@"
fi
