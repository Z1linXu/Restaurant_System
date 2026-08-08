#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
LAUNCHER="$REPOSITORY_ROOT/deployment/cloud/staging-synthetic-acceptance.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-al003s-launcher.XXXXXX")"

cleanup_test() { rm -rf "$TMP_DIR"; }
trap cleanup_test EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() {
  local label="$1"
  shift
  if "$@" >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then
    fail "$label unexpectedly passed"
  fi
}

bash -n "$LAUNCHER"
"$LAUNCHER" --help >"$TMP_DIR/help.out"
assert_contains 'The default action is validation only.' "$TMP_DIR/help.out"
assert_contains 'No password or token is accepted as' "$TMP_DIR/help.out"
assert_contains '--readiness-evidence <absolute-path>' "$TMP_DIR/help.out"
assert_contains '--action-approval <absolute-path>' "$TMP_DIR/help.out"

expect_failure missing_binding "$LAUNCHER" --validate
assert_contains 'env, exact SHA, preflight evidence, and evidence digest are required' "$TMP_DIR/missing_binding.err"

expect_failure plan_without_runtime_gate "$LAUNCHER" \
  --action source-menu-plan \
  --env-file /does/not/exist \
  --approved-sha 0123456789abcdef0123456789abcdef01234567 \
  --preflight-evidence /does/not/exist \
  --preflight-evidence-sha256 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  --source-store-id 1 \
  --source-store-code STG005_SOURCE
assert_contains 'source-menu-plan requires --execute-runtime' "$TMP_DIR/plan_without_runtime_gate.err"

expect_failure wrong_source_id "$LAUNCHER" \
  --execute-runtime \
  --action source-menu-plan \
  --env-file /does/not/exist \
  --approved-sha 0123456789abcdef0123456789abcdef01234567 \
  --preflight-evidence /does/not/exist \
  --preflight-evidence-sha256 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  --source-store-id 2 \
  --source-store-code STG005_SOURCE
assert_contains 'reviewed synthetic source Store ID 1' "$TMP_DIR/wrong_source_id.err"

expect_failure duplicate_binding "$LAUNCHER" \
  --validate \
  --env-file /does/not/exist \
  --env-file /does/not/exist-again \
  --approved-sha 0123456789abcdef0123456789abcdef01234567 \
  --preflight-evidence /does/not/exist \
  --preflight-evidence-sha256 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
assert_contains '--env-file was provided more than once' "$TMP_DIR/duplicate_binding.err"

expect_failure unsafe_name "$LAUNCHER" \
  --execute-runtime \
  --action bootstrap-plan \
  --env-file /does/not/exist \
  --approved-sha 0123456789abcdef0123456789abcdef01234567 \
  --preflight-evidence /does/not/exist \
  --preflight-evidence-sha256 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  --run-id LIVE_RUN \
  --organization-name STG005_ORG \
  --organization-code STG005_ORG \
  --source-store-name STG005_SOURCE \
  --source-store-code STG005_SOURCE \
  --owner-login STG005_OWNER \
  --owner-name STG005_OWNER
assert_contains 'run-id must use the STG005_ synthetic namespace' "$TMP_DIR/unsafe_name.err"

expect_failure missing_action_evidence "$LAUNCHER" \
  --execute-runtime \
  --action bootstrap-plan \
  --env-file /does/not/exist \
  --approved-sha 0123456789abcdef0123456789abcdef01234567 \
  --preflight-evidence /does/not/exist \
  --preflight-evidence-sha256 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  --run-id STG005_RUN \
  --organization-name STG005_ORG \
  --organization-code STG005_ORG \
  --source-store-name STG005_SOURCE \
  --source-store-code STG005_SOURCE \
  --owner-login STG005_OWNER \
  --owner-name STG005_OWNER
assert_contains 'one-shot actions require runtime readiness evidence' "$TMP_DIR/missing_action_evidence.err"

# Source the launcher to exercise the private CLI state and command assembly
# without running Docker or touching a runtime environment.
# shellcheck source=../staging-synthetic-acceptance.sh
source "$LAUNCHER"

FAKE_DOCKER="$TMP_DIR/docker"
FAKE_TIMEOUT="$TMP_DIR/timeout"
DOCKER_CALLS="$TMP_DIR/docker.calls"
ORIGINAL_HOME="$HOME"
cat >"$FAKE_DOCKER" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf 'HOME=%s DOCKER_CONFIG=%s ARGS=%s\n' "\$HOME" "\$DOCKER_CONFIG" "\$*" >>"$DOCKER_CALLS"
[[ "\$HOME" != "$ORIGINAL_HOME" ]] || exit 91
[[ "\$DOCKER_CONFIG" != "$ORIGINAL_HOME/.docker" ]] || exit 92
[[ "\$*" == '--context default compose version' ]] || exit 93
EOF
chmod +x "$FAKE_DOCKER"
cat >"$FAKE_TIMEOUT" <<'EOF'
#!/usr/bin/env bash
shift 3
exec "$@"
EOF
chmod +x "$FAKE_TIMEOUT"
export DOCKER_CALLS ORIGINAL_HOME
DOCKER_BIN="$FAKE_DOCKER"
TIMEOUT_BIN="$FAKE_TIMEOUT"
TMPDIR="$(canonical_dir "$TMP_DIR")"
initialize_docker_cli_state
CLI_ROOT="$DOCKER_CLI_STATE_ROOT"
[[ "$CLI_ROOT" == "$TMPDIR"/restaurant-pos-al003s-docker-cli.* ]] || fail 'private CLI root escaped the test directory'
[[ "$(file_mode "$CLI_ROOT")" == "700" ]] || fail 'private CLI root mode is not 0700'
[[ "$(file_mode "$DOCKER_CLI_HOME")" == "700" ]] || fail 'private HOME mode is not 0700'
[[ "$(file_mode "$DOCKER_CLI_CONFIG")" == "700" ]] || fail 'private DOCKER_CONFIG mode is not 0700'
assert_contains '--context default compose version' "$DOCKER_CALLS"
assert_not_contains "$ORIGINAL_HOME/.docker" "$DOCKER_CALLS"
cleanup
[[ ! -e "$CLI_ROOT" ]] || fail 'private CLI state was not removed on success'

# The EXIT trap also removes private state when a child exits with a failure.
FAILURE_ROOT_FILE="$TMP_DIR/failure-root"
export FAILURE_ROOT_FILE LAUNCHER FAKE_DOCKER FAKE_TIMEOUT DOCKER_CALLS ORIGINAL_HOME TMP_DIR
if bash -c '
  set -Eeuo pipefail
  source "$LAUNCHER"
  DOCKER_BIN="$FAKE_DOCKER"
  TIMEOUT_BIN="$FAKE_TIMEOUT"
  TMPDIR="$TMP_DIR"
  trap cleanup EXIT
  initialize_docker_cli_state
  printf "%s" "$DOCKER_CLI_STATE_ROOT" >"$FAILURE_ROOT_FILE"
  exit 17
'; then
  fail 'failure cleanup child unexpectedly succeeded'
fi
FAILURE_ROOT="$(cat "$FAILURE_ROOT_FILE")"
[[ ! -e "$FAILURE_ROOT" ]] || fail 'private CLI state was not removed on failure'

# Verify exact one-shot command composition using a fake compose function.
COMMAND_CALLS="$TMP_DIR/command.calls"
controlled_docker() {
  [[ "$*" != ps\ -aq* ]] || return 0
  printf 'docker %s\n' "$*" >>"$COMMAND_CALLS"
}
controlled_compose_run() { printf '%s\n' "$*" >>"$COMMAND_CALLS"; }
APPROVED_SHA="0123456789abcdef0123456789abcdef01234567"
RUN_ID="STG005_RUN_001"
ORGANIZATION_NAME="STG005_ORG_001"
ORGANIZATION_CODE="STG005_ORG_001"
SOURCE_STORE_NAME="STG005_SOURCE_001"
SOURCE_STORE_CODE="STG005_SOURCE_001"
OWNER_LOGIN="STG005_OWNER_001"
OWNER_NAME="STG005_OWNER_001"

ACTION="bootstrap-plan"
run_bootstrap
assert_contains '--rm --no-deps -T --name restaurant-pos-staging-al003s-bootstrap-plan-' "$COMMAND_CALLS"
assert_contains '--pull never --entrypoint java' "$COMMAND_CALLS"
assert_contains 'SPRING_PROFILES_ACTIVE=cloud,staging-synthetic-bootstrap' "$COMMAND_CALLS"
assert_contains 'STG005_BOOTSTRAP_COMMAND_ENABLED=true' "$COMMAND_CALLS"
assert_contains 'STG005_SOURCE_MENU_COMMAND_ENABLED=false' "$COMMAND_CALLS"
assert_contains '--run-id=STG005_RUN_001' "$COMMAND_CALLS"
assert_not_contains '--password-stdin' "$COMMAND_CALLS"
assert_not_contains 'password=' "$COMMAND_CALLS"

: >"$COMMAND_CALLS"
ACTION="bootstrap-execute"
run_bootstrap </dev/null
assert_contains '--execute --password-stdin' "$COMMAND_CALLS"

: >"$COMMAND_CALLS"
ACTION="source-menu-plan"
SOURCE_STORE_ID="1"
run_source_menu
assert_contains 'STG005_BOOTSTRAP_COMMAND_ENABLED=false' "$COMMAND_CALLS"
assert_contains 'STG005_SOURCE_MENU_COMMAND_ENABLED=true' "$COMMAND_CALLS"
assert_contains '--source-store-id=1' "$COMMAND_CALLS"
assert_not_contains '--execute' "$COMMAND_CALLS"

: >"$COMMAND_CALLS"
ACTION="source-menu-execute"
run_source_menu
assert_contains '--execute' "$COMMAND_CALLS"

grep -Fq 'EXPECTED_ROOT="/srv/restaurant-pos/staging"' "$LAUNCHER" || fail 'fixed Staging root guard is missing'
grep -Fq 'EXPECTED_PROJECT="restaurant-pos-staging"' "$LAUNCHER" || fail 'fixed project guard is missing'
grep -Fq 'EXPECTED_PRINTING_MODE="DISABLED"' "$LAUNCHER" || fail 'printing-disabled guard is missing'
grep -Fq 'MAX_READINESS_AGE_SECONDS=900' "$LAUNCHER" || fail 'fresh readiness window is missing'
grep -Fq 'ACTION_TIMEOUT_SECONDS=600' "$LAUNCHER" || fail 'bounded action timeout is missing'
grep -Fq 'DOCKER_METADATA_TIMEOUT_SECONDS=20' "$LAUNCHER" || fail 'bounded Docker metadata timeout is missing'
grep -Fq 'acquire_action_lock' "$LAUNCHER" || fail 'cross-process action lock is missing'
grep -Fq 'image: "%s"' "$LAUNCHER" || fail 'immutable backend image override is missing'
! grep -Eq '(docker compose down|down -v|Flyway clean|/home/ubuntu/\.docker)' "$LAUNCHER" || fail 'launcher contains a forbidden lifecycle or Docker config reference'

echo 'PASS: AL-003S launcher defaults to validation, requires fresh action evidence, isolates Docker CLI state, and separates plan/write gates.'
