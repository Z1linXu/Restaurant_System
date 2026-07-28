#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
RUNNER="$REPOSITORY_ROOT/deployment/cloud/staging-local-rehearsal.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-stg003-test.XXXXXX")"
FAKE_BIN="$TMP_DIR/bin"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
expect_failure() {
  local label="$1"; shift
  if "$@" >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then
    fail "$label unexpectedly passed"
  fi
}
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }

bash -n "$RUNNER"

# A plan has no Docker side effect and is intentionally usable on this machine.
"$RUNNER" --plan --root "$TMP_DIR/restaurant-pos/staging" >"$TMP_DIR/plan.out"
assert_contains 'STG-003 LOCAL-ONLY REHEARSAL PLAN' "$TMP_DIR/plan.out"
assert_contains 'printing=DISABLED' "$TMP_DIR/plan.out"
assert_contains 'Flyway clean' "$TMP_DIR/plan.out"

# A real run fails closed before creating a root when Docker is unavailable.
PATH="/usr/bin:/bin" expect_failure no_docker "$RUNNER" --run --confirm-local-container-start --root "$TMP_DIR/no-docker/restaurant-pos/staging"
assert_contains 'BLOCKED_LOCAL_DOCKER_RUNTIME_UNAVAILABLE' "$TMP_DIR/no_docker.err"
[[ ! -e "$TMP_DIR/no-docker" ]] || fail 'Docker-unavailable run created local state'

expect_failure srv_root "$RUNNER" --plan --root /srv/restaurant-pos/staging
assert_contains 'non-production absolute path' "$TMP_DIR/srv_root.err"

expect_failure repository_root "$RUNNER" --plan --root "$REPOSITORY_ROOT/restaurant-pos/staging"
assert_contains 'non-production absolute path' "$TMP_DIR/repository_root.err"

expect_failure no_confirmation "$RUNNER" --run --root "$TMP_DIR/no-confirm/restaurant-pos/staging"
assert_contains 'requires --confirm-local-container-start' "$TMP_DIR/no_confirmation.err"

# Fake Docker proves the command plan is local-context only. It cannot start a
# container and checks that no inherited Docker endpoint enters the command.
mkdir -p "$FAKE_BIN"
cat >"$FAKE_BIN/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf 'argv=%s\n' "$*" >>"$(dirname "$0")/docker.calls"
printf 'docker_host=%s docker_context=%s\n' "${DOCKER_HOST-unset}" "${DOCKER_CONTEXT-unset}" >>"$(dirname "$0")/docker.calls"
[[ "${1:-}" == "context" && "${2:-}" == "inspect" && "${3:-}" == "default" ]] || exit 81
if [[ "${4:-}" == "--format" ]]; then
  printf 'unix:///tmp/fake-docker.sock\n'
fi
DOCKER
chmod +x "$FAKE_BIN/docker"

PATH="$FAKE_BIN:/usr/bin:/bin" "$RUNNER" --plan --root "$TMP_DIR/fake/restaurant-pos/staging" >"$TMP_DIR/fake-plan.out"
assert_contains '127.0.0.1:18080' "$TMP_DIR/fake-plan.out"
assert_contains 'docker_context=default (local endpoint verified)' "$TMP_DIR/fake-plan.out"
assert_contains 'argv=context inspect default' "$FAKE_BIN/docker.calls"
assert_contains 'docker_host=unset docker_context=unset' "$FAKE_BIN/docker.calls"

DOCKER_HOST='tcp://forbidden.invalid:2375' PATH="$FAKE_BIN:/usr/bin:/bin" \
  expect_failure ambient_docker "$RUNNER" --plan --root "$TMP_DIR/ambient/restaurant-pos/staging"
assert_contains 'ambient Docker overrides are forbidden' "$TMP_DIR/ambient_docker.err"

echo 'PASS: STG-003 local rehearsal plan is local-only, Docker fail-closed, and rejects production/repository roots.'
