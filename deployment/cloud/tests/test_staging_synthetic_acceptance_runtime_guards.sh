#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
LAUNCHER="$REPOSITORY_ROOT/deployment/cloud/staging-synthetic-acceptance.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-al003s-runtime-guards.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"

cleanup_test() { rm -rf "$TMP_DIR"; }
trap cleanup_test EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
expect_function_failure() {
  local label="$1"
  shift
  if ("$@") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then
    fail "$label unexpectedly passed"
  fi
}

# shellcheck source=../staging-synthetic-acceptance.sh
source "$LAUNCHER"
ORIGINAL_ACQUIRE_ACTION_LOCK="$(declare -f acquire_action_lock)"
ORIGINAL_CONTROLLED_COMPOSE_RUN="$(declare -f controlled_compose_run)"

EXPECTED_ROOT="$TMP_DIR/staging"
mkdir -p "$EXPECTED_ROOT/config" "$EXPECTED_ROOT/evidence" "$EXPECTED_ROOT/state"
chmod 700 "$EXPECTED_ROOT" "$EXPECTED_ROOT/config" "$EXPECTED_ROOT/evidence" "$EXPECTED_ROOT/state"

APPROVED_SHA="0123456789abcdef0123456789abcdef01234567"
ACTION="bootstrap-plan"
RUN_ID="STG005_RUN_001"
ORGANIZATION_NAME="STG005_ORG_001"
ORGANIZATION_CODE="STG005_ORG_001"
SOURCE_STORE_NAME="STG005_SOURCE_001"
SOURCE_STORE_CODE="STG005_SOURCE_001"
OWNER_LOGIN="STG005_OWNER_001"
OWNER_NAME="STG005_OWNER_001"
SOURCE_STORE_ID=""

ENV_FILE="$EXPECTED_ROOT/config/.env.staging"
ENV_SNAPSHOT="$TMP_DIR/env.snapshot"
PREFLIGHT_EVIDENCE="$EXPECTED_ROOT/evidence/preflight.txt"
READINESS_EVIDENCE="$EXPECTED_ROOT/evidence/readiness.txt"
ACTION_APPROVAL="$EXPECTED_ROOT/evidence/approval.txt"
printf 'BACKEND_IMAGE=restaurant-pos-backend:staging-%s\n' "$APPROVED_SHA" >"$ENV_FILE"
cp "$ENV_FILE" "$ENV_SNAPSHOT"
printf 'PREFLIGHT\n' >"$PREFLIGHT_EVIDENCE"
chmod 600 "$ENV_FILE" "$ENV_SNAPSHOT" "$PREFLIGHT_EVIDENCE"
ACTIVE_ENV_FILE="$ENV_SNAPSHOT"
ENV_SNAPSHOT_SHA256="$(file_digest "$ENV_SNAPSHOT")"
VALIDATED_PREFLIGHT_SHA256="$(file_digest "$PREFLIGHT_EVIDENCE")"

STAGING_FINGERPRINT="$(printf 'staging' | string_digest)"
PRODUCTION_FINGERPRINT="$(printf 'production' | string_digest)"
project_fingerprint() {
  case "$1" in
    "$EXPECTED_PROJECT") printf '%s\n' "$STAGING_FINGERPRINT" ;;
    "$EXPECTED_PRODUCTION_PROJECT") printf '%s\n' "$PRODUCTION_FINGERPRINT" ;;
    *) return 1 ;;
  esac
}
available_memory_kb() { printf '2097152\n'; }
cpu_count() { printf '4\n'; }
free_disk_kb() { printf '4194304\n'; }
load_per_cpu_milli() { printf '250\n'; }

write_readiness() {
  local captured="$1"
  cat >"$READINESS_EVIDENCE" <<EOF
READINESS|STATUS|PASS
READINESS|CAPTURED_AT_EPOCH|$captured
READINESS|APPROVED_SHA|$APPROVED_SHA
READINESS|ENV_SHA256|$ENV_SNAPSHOT_SHA256
READINESS|PREFLIGHT_SHA256|$VALIDATED_PREFLIGHT_SHA256
READINESS|STAGING_PROJECT|$EXPECTED_PROJECT
READINESS|STAGING_FINGERPRINT|$STAGING_FINGERPRINT
READINESS|PRODUCTION_PROJECT|$EXPECTED_PRODUCTION_PROJECT
READINESS|PRODUCTION_FINGERPRINT|$PRODUCTION_FINGERPRINT
READINESS|MIN_AVAILABLE_MEMORY_KB|1048576
READINESS|MIN_CPU_COUNT|2
READINESS|MIN_FREE_DISK_KB|1048576
READINESS|MAX_LOAD_PER_CPU_MILLI|1000
READINESS|SUMMARY|PASS
EOF
  chmod 600 "$READINESS_EVIDENCE"
  READINESS_EVIDENCE_SHA256="$(file_digest "$READINESS_EVIDENCE")"
}

write_approval() {
  local action="$1" expires="$2" fingerprint="$3"
  cat >"$ACTION_APPROVAL" <<EOF
APPROVAL|STATUS|OWNER_APPROVED
APPROVAL|EXPIRES_AT_EPOCH|$expires
APPROVAL|APPROVED_SHA|$APPROVED_SHA
APPROVAL|ACTION|$action
APPROVAL|REQUEST_FINGERPRINT|$fingerprint
APPROVAL|PREFLIGHT_SHA256|$VALIDATED_PREFLIGHT_SHA256
APPROVAL|READINESS_SHA256|$VALIDATED_READINESS_EVIDENCE_SHA256
APPROVAL|REFERENCE|owner-review/STG005-001
EOF
  chmod 600 "$ACTION_APPROVAL"
  ACTION_APPROVAL_SHA256="$(file_digest "$ACTION_APPROVAL")"
}

NOW="$(date +%s)"
write_readiness "$NOW"
validate_readiness_evidence
[[ "$VALIDATED_READINESS_EVIDENCE_SHA256" == "$READINESS_EVIDENCE_SHA256" ]] || fail 'readiness digest was not retained'

REQUEST_FINGERPRINT="$(action_request_fingerprint)"
write_approval "$ACTION" "$((NOW + 600))" "$REQUEST_FINGERPRINT"
validate_action_approval
[[ "$VALIDATED_ACTION_APPROVAL_SHA256" == "$ACTION_APPROVAL_SHA256" ]] || fail 'approval digest was not retained'
assert_snapshot_integrity

ORIGINAL_APPROVAL="$TMP_DIR/approval.original"
cp "$ACTION_APPROVAL" "$ORIGINAL_APPROVAL"
printf 'tampered\n' >>"$ACTION_APPROVAL"
expect_function_failure approval_tamper assert_snapshot_integrity
assert_contains 'action approval changed after validation' "$TMP_DIR/approval_tamper.err"
cp "$ORIGINAL_APPROVAL" "$ACTION_APPROVAL"
chmod 600 "$ACTION_APPROVAL"

write_readiness "$((NOW - MAX_READINESS_AGE_SECONDS - 1))"
expect_function_failure stale_readiness validate_readiness_evidence
assert_contains 'runtime readiness evidence is stale' "$TMP_DIR/stale_readiness.err"

write_readiness "$NOW"
validate_readiness_evidence
write_approval source-menu-plan "$((NOW + 600))" "$REQUEST_FINGERPRINT"
expect_function_failure wrong_action validate_action_approval
assert_contains 'action approval scope mismatch' "$TMP_DIR/wrong_action.err"

write_approval "$ACTION" "$((NOW + 600))" "$(printf 'different-request' | string_digest)"
expect_function_failure wrong_request validate_action_approval
assert_contains 'action approval request fingerprint mismatch' "$TMP_DIR/wrong_request.err"

# Exercise running-image, port, and loopback-health guards without Docker.
EXPECTED_IMAGE="restaurant-pos-backend:staging-$APPROVED_SHA"
EXPECTED_IMAGE_ID="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
CONTAINER_ID="bbbbbbbbbbbb"
COMPOSE_PORT="127.0.0.1:18080"
TAG_IMAGE_ID="$EXPECTED_IMAGE_ID"
controlled_compose() {
  case "$*" in
    'ps -q backend') printf '%s\n' "$CONTAINER_ID" ;;
    'port nginx 80') printf '%s\n' "$COMPOSE_PORT" ;;
    *) fail "unexpected controlled_compose call: $*" ;;
  esac
}
controlled_docker() {
  case "$*" in
    "inspect --format "*" $CONTAINER_ID") printf 'running|%s|%s\n' "$EXPECTED_IMAGE" "$EXPECTED_IMAGE_ID" ;;
    "image inspect --format {{.Id}} $EXPECTED_IMAGE") printf '%s\n' "$TAG_IMAGE_ID" ;;
    *) fail "unexpected controlled_docker call: $*" ;;
  esac
}
CURL_BIN="$TMP_DIR/curl"
cat >"$CURL_BIN" <<'EOF'
#!/usr/bin/env bash
printf '200'
EOF
chmod +x "$CURL_BIN"
validate_running_backend_identity
[[ "$RESOLVED_BACKEND_IMAGE_ID" == "$EXPECTED_IMAGE_ID" ]] || fail 'running image ID was not retained'

TAG_IMAGE_ID="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
expect_function_failure tag_drift validate_running_backend_identity
assert_contains 'tag no longer resolves to the running image ID' "$TMP_DIR/tag_drift.err"
TAG_IMAGE_ID="$EXPECTED_IMAGE_ID"

COMPOSE_PORT="0.0.0.0:18080"
expect_function_failure wrong_port validate_running_backend_identity
assert_contains 'must be exactly 127.0.0.1:18080' "$TMP_DIR/wrong_port.err"
COMPOSE_PORT="127.0.0.1:18080"

cat >"$CURL_BIN" <<'EOF'
#!/usr/bin/env bash
printf '503'
EOF
chmod +x "$CURL_BIN"
expect_function_failure unhealthy validate_running_backend_identity
assert_contains 'must return HTTP 200' "$TMP_DIR/unhealthy.err"

# Verify the generated override pins backend to an immutable image ID and is
# included in the one-shot Compose command, then removed by cleanup.
cat >"$CURL_BIN" <<'EOF'
#!/usr/bin/env bash
printf '200'
EOF
chmod +x "$CURL_BIN"
RESOLVED_BACKEND_IMAGE_ID="$EXPECTED_IMAGE_ID"
COMPOSE_CALLS="$TMP_DIR/compose.calls"
controlled_docker() {
  printf '%s\n' "$*" >>"$COMPOSE_CALLS"
  [[ "$*" != ps\ -aq* ]] || return 0
  [[ "$*" != *'config --images' ]] || printf '%s\npostgres:16-alpine\nnginx:alpine\n' "$RESOLVED_BACKEND_IMAGE_ID"
}
controlled_compose() {
  local -a compose_files=(-f "$STAGING_COMPOSE_FILE")
  [[ -z "$IMMUTABLE_IMAGE_OVERRIDE" ]] || compose_files+=(-f "$IMMUTABLE_IMAGE_OVERRIDE")
  controlled_docker compose \
    --project-name "$EXPECTED_PROJECT" \
    --env-file "$ACTIVE_ENV_FILE" \
    "${compose_files[@]}" "$@"
}
controlled_compose_run() { printf '%s\n' "$*" >>"$COMPOSE_CALLS"; }
create_immutable_image_override
OVERRIDE_PATH="$IMMUTABLE_IMAGE_OVERRIDE"
[[ "$(file_mode "$OVERRIDE_PATH")" == "600" ]] || fail 'immutable override mode is not 0600'
assert_contains "image: \"$EXPECTED_IMAGE_ID\"" "$OVERRIDE_PATH"
SOURCE_STORE_ID="1"
ACTION="source-menu-plan"
run_source_menu
assert_contains '--rm --no-deps -T --name restaurant-pos-staging-al003s-source-menu-plan-' "$COMPOSE_CALLS"
assert_contains '--pull never --entrypoint java' "$COMPOSE_CALLS"
cleanup
[[ ! -e "$OVERRIDE_PATH" ]] || fail 'immutable override was not removed'

# Verify orchestration rechecks runtime/readiness/approval after constructing
# the immutable override and before dispatching the one-shot action.
ORCHESTRATION_CALLS="$TMP_DIR/orchestration.calls"
assert_snapshot_integrity() { echo snapshot >>"$ORCHESTRATION_CALLS"; }
assert_release_identity() { echo release >>"$ORCHESTRATION_CALLS"; }
acquire_action_lock() { echo lock >>"$ORCHESTRATION_CALLS"; }
validate_running_backend_identity() {
  RUNNING_CHECK_COUNT=$(( ${RUNNING_CHECK_COUNT:-0} + 1 ))
  RESOLVED_BACKEND_IMAGE_ID="$EXPECTED_IMAGE_ID"
  echo running-image >>"$ORCHESTRATION_CALLS"
  [[ "${FAIL_RUNNING_CHECK_AT:-0}" -ne "$RUNNING_CHECK_COUNT" ]] || die "simulated continuity failure"
}
validate_readiness_evidence() { echo readiness >>"$ORCHESTRATION_CALLS"; }
validate_action_approval() { echo approval >>"$ORCHESTRATION_CALLS"; }
create_immutable_image_override() {
  IMMUTABLE_IMAGE_OVERRIDE="$TMP_DIR/orchestration-override"
  printf 'services:\n  backend:\n    image: "%s"\n' "$RESOLVED_BACKEND_IMAGE_ID" >"$IMMUTABLE_IMAGE_OVERRIDE"
  echo image-override >>"$ORCHESTRATION_CALLS"
}
run_source_menu() { echo dispatch >>"$ORCHESTRATION_CALLS"; return "${DISPATCH_STATUS:-0}"; }
finalize_one_shot_container() { echo finalize >>"$ORCHESTRATION_CALLS"; return "${FINALIZE_STATUS:-0}"; }
ACTION="source-menu-plan"
RUNNING_CHECK_COUNT=0
run_action
EXPECTED_SEQUENCE="$TMP_DIR/orchestration.expected"
cat >"$EXPECTED_SEQUENCE" <<'EOF'
lock
snapshot
release
running-image
snapshot
release
readiness
approval
image-override
snapshot
release
running-image
readiness
approval
snapshot
release
dispatch
finalize
running-image
readiness
snapshot
release
EOF
cmp -s "$EXPECTED_SEQUENCE" "$ORCHESTRATION_CALLS" || fail 'run_action guard order changed'

: >"$ORCHESTRATION_CALLS"
RUNNING_CHECK_COUNT=0
DISPATCH_STATUS=124
if (run_action) >"$TMP_DIR/action-failure.out" 2>"$TMP_DIR/action-failure.err"; then
  fail 'failed action unexpectedly passed'
fi
assert_contains 'dispatch' "$ORCHESTRATION_CALLS"
assert_contains 'finalize' "$ORCHESTRATION_CALLS"
assert_contains 'running-image' "$ORCHESTRATION_CALLS"
assert_contains 'one-shot action failed with exit code 124' "$TMP_DIR/action-failure.err"
[[ -f "$EXPECTED_ROOT/state/al003s-acceptance.blocked" ]] || fail 'failed action did not create blocked marker'
rm -f "$EXPECTED_ROOT/state/al003s-acceptance.blocked"
DISPATCH_STATUS=0

: >"$ORCHESTRATION_CALLS"
RUNNING_CHECK_COUNT=0
FAIL_RUNNING_CHECK_AT=3
if (run_action) >"$TMP_DIR/postcheck-failure.out" 2>"$TMP_DIR/postcheck-failure.err"; then
  fail 'failed post-action continuity unexpectedly passed'
fi
assert_contains 'dispatch' "$ORCHESTRATION_CALLS"
assert_contains 'finalize' "$ORCHESTRATION_CALLS"
assert_contains 'post-action continuity check failed' "$TMP_DIR/postcheck-failure.err"
[[ -f "$EXPECTED_ROOT/state/al003s-acceptance.blocked" ]] || fail 'postcheck failure did not create blocked marker'
rm -f "$EXPECTED_ROOT/state/al003s-acceptance.blocked"
FAIL_RUNNING_CHECK_AT=0

: >"$ORCHESTRATION_CALLS"
RUNNING_CHECK_COUNT=0
FINALIZE_STATUS=1
rm -f "$EXPECTED_ROOT/state/al003s-acceptance.blocked"
if (run_action) >"$TMP_DIR/finalize-failure.out" 2>"$TMP_DIR/finalize-failure.err"; then
  fail 'failed scoped cleanup unexpectedly passed'
fi
assert_contains 'one-shot cleanup failed; future AL-003S actions are blocked' "$TMP_DIR/finalize-failure.err"
[[ -f "$EXPECTED_ROOT/state/al003s-acceptance.blocked" ]] || fail 'cleanup failure did not create blocked marker'
rm -f "$EXPECTED_ROOT/state/al003s-acceptance.blocked"
FINALIZE_STATUS=0

ACTION_BLOCKED_MARKER="$EXPECTED_ROOT/state/al003s-acceptance.blocked"
ACTION_LOCK_FD="9"
exec 9>>"$EXPECTED_ROOT/state/al003s-acceptance.lock"
if (handle_interrupt) >"$TMP_DIR/interrupt.out" 2>"$TMP_DIR/interrupt.err"; then
  fail 'interrupt handler unexpectedly returned success'
fi
exec 9>&-
ACTION_LOCK_FD=""
[[ -f "$ACTION_BLOCKED_MARKER" ]] || fail 'interrupt did not create blocked marker'
assert_contains 'AL003S_BLOCKED|interrupt_requires_owner_review' "$ACTION_BLOCKED_MARKER"
rm -f "$ACTION_BLOCKED_MARKER"
: >"$EXPECTED_ROOT/state/al003s-acceptance.lock"

# Exercise the fixed state lock metadata and bounded Docker wrapper with local
# fake commands. No Docker daemon or runtime environment is touched.
FLOCK_BIN="$TMP_DIR/flock"
cat >"$FLOCK_BIN" <<'EOF'
#!/usr/bin/env bash
if [[ "${FLOCK_CREATE_MARKER:-false}" == "true" && "$1" == "-n" ]]; then
  printf 'AL003S_BLOCKED|race_test\n' >"$FLOCK_MARKER_PATH"
  chmod 600 "$FLOCK_MARKER_PATH"
fi
exit 0
EOF
chmod +x "$FLOCK_BIN"
eval "$ORIGINAL_ACQUIRE_ACTION_LOCK"
ACTION_LOCK_FD=""
acquire_action_lock
[[ "$ACTION_LOCK_FILE" == "$EXPECTED_ROOT/state/al003s-acceptance.lock" ]] || fail 'action lock escaped fixed Staging state'
[[ "$(file_mode "$ACTION_LOCK_FILE")" == "600" ]] || fail 'action lock mode is not 0600'
"$FLOCK_BIN" -u "$ACTION_LOCK_FD"
exec 9>&-
ACTION_LOCK_FD=""

ACTION_BLOCKED_MARKER="$EXPECTED_ROOT/state/al003s-acceptance.blocked"
ln -s /dev/null "$ACTION_BLOCKED_MARKER"
exec 9>>"$EXPECTED_ROOT/state/al003s-acceptance.lock"
ACTION_LOCK_FD="9"
mark_action_blocked marker_file_unavailable >"$TMP_DIR/marker-write.out" 2>"$TMP_DIR/marker-write.err"
exec 9>&-
ACTION_LOCK_FD=""
assert_contains 'lock record remains authoritative' "$TMP_DIR/marker-write.err"
rm -f "$ACTION_BLOCKED_MARKER"
if (ACTION_LOCK_FD=""; acquire_action_lock) >"$TMP_DIR/lock-record.out" 2>"$TMP_DIR/lock-record.err"; then
  fail 'authoritative blocked lock record unexpectedly passed'
fi
assert_contains 'blocked pending Owner cleanup review' "$TMP_DIR/lock-record.err"
: >"$EXPECTED_ROOT/state/al003s-acceptance.lock"

export FLOCK_CREATE_MARKER=true FLOCK_MARKER_PATH="$EXPECTED_ROOT/state/al003s-acceptance.blocked"
if (ACTION_LOCK_FD=""; acquire_action_lock) >"$TMP_DIR/marker-race.out" 2>"$TMP_DIR/marker-race.err"; then
  fail 'post-flock blocked marker race unexpectedly passed'
fi
assert_contains 'blocked pending Owner cleanup review' "$TMP_DIR/marker-race.err"
rm -f "$FLOCK_MARKER_PATH"
export FLOCK_CREATE_MARKER=false

WRAPPER_CALLS="$TMP_DIR/wrapper.calls"
DOCKER_BIN="$TMP_DIR/docker-wrapper"
cat >"$DOCKER_BIN" <<EOF
#!/usr/bin/env bash
printf 'docker %s\n' "\$*" >>"$WRAPPER_CALLS"
exit 0
EOF
chmod +x "$DOCKER_BIN"
TIMEOUT_BIN="$TMP_DIR/timeout-wrapper"
cat >"$TIMEOUT_BIN" <<EOF
#!/usr/bin/env bash
printf 'timeout %s\n' "\$*" >>"$WRAPPER_CALLS"
shift 3
exec "\$@"
EOF
chmod +x "$TIMEOUT_BIN"
DOCKER_CLI_STATE_ROOT=""
DOCKER_CLI_HOME=""
DOCKER_CLI_CONFIG=""
IMMUTABLE_IMAGE_OVERRIDE=""
eval "$ORIGINAL_CONTROLLED_COMPOSE_RUN"
controlled_compose_run --rm --no-deps -T backend
assert_contains 'timeout --signal=TERM --kill-after=10s 600s' "$WRAPPER_CALLS"
assert_contains 'docker --context default compose --project-name restaurant-pos-staging' "$WRAPPER_CALLS"

ONE_SHOT_CONTAINER_NAME="restaurant-pos-staging-al003s-test-cleanup"
ONE_SHOT_STARTED="true"
cleanup
assert_contains 'docker --context default rm -f restaurant-pos-staging-al003s-test-cleanup' "$WRAPPER_CALLS"

echo 'PASS: AL-003S runtime guards bind fresh readiness, scoped approval, immutable images, loopback health, and evidence integrity.'
