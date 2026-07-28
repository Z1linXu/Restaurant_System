#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
RUNNER="$REPOSITORY_ROOT/deployment/cloud/staging-server-control.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-stg004-control.XXXXXX")"
SHA="0123456789abcdef0123456789abcdef01234567"
PRIOR_SHA="89abcdef0123456789abcdef0123456789abcdef"
ENV_FILE="/srv/restaurant-pos/staging/config/.env.staging"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() {
  local label="$1"
  shift
  if "$@" >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then fail "$label unexpectedly passed"; fi
}

bash -n "$RUNNER"
"$RUNNER" --validate --env-file "$ENV_FILE" --approved-sha "$SHA" >"$TMP_DIR/validate.out"
assert_contains 'CONTROL|VALIDATE|PASS|' "$TMP_DIR/validate.out"

"$RUNNER" --plan-stop --env-file "$ENV_FILE" --approved-sha "$SHA" >"$TMP_DIR/stop.out"
assert_contains 'CONTROL|PLAN_STOP|OWNER_ACTION_REQUIRED|' "$TMP_DIR/stop.out"
assert_contains "--project-name restaurant-pos-staging" "$TMP_DIR/stop.out"
assert_contains 'stop nginx backend db' "$TMP_DIR/stop.out"
stop_command="$(grep '^docker ' "$TMP_DIR/stop.out")"
[[ "$stop_command" != *' down'* && "$stop_command" != *' -v'* && "$stop_command" != *' pull'* ]] || fail 'stop plan contains a destructive or image-pull command'

"$RUNNER" --plan-rollback --env-file "$ENV_FILE" --approved-sha "$SHA" --to-sha "$PRIOR_SHA" >"$TMP_DIR/rollback.out"
assert_contains 'CONTROL|PLAN_ROLLBACK|OWNER_ACTION_REQUIRED|' "$TMP_DIR/rollback.out"
assert_contains "TARGET_SHA=$PRIOR_SHA" "$TMP_DIR/rollback.out"
assert_not_contains 'docker compose down' "$TMP_DIR/rollback.out"

expect_failure missing_action "$RUNNER" --env-file "$ENV_FILE" --approved-sha "$SHA"
assert_contains 'Usage:' "$TMP_DIR/missing_action.out"
expect_failure unsafe_project_env "$RUNNER" --validate --env-file /tmp/not-staging --approved-sha "$SHA"
assert_contains 'CONTROL|INPUTS|NO_GO|' "$TMP_DIR/unsafe_project_env.err"

[[ ! -e "$TMP_DIR/docker.calls" ]] || fail 'control script invoked Docker instead of only printing a plan'

echo 'PASS: STG-004 control script only validates inputs and prints Owner-action-required plans.'
