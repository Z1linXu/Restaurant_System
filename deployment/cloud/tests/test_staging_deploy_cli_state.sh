#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
RUNNER="$REPOSITORY_ROOT/deployment/cloud/staging-deploy.sh"
TMP_PARENT="$(cd -P "${TMPDIR:-/tmp}" && pwd)"
TMP_DIR="$(mktemp -d "$TMP_PARENT/restaurant-pos-staging-cli-state-test.XXXXXX")"
CLI_TEMP_PARENT="$TMP_DIR/cli-temp"
FAKE_BIN="$TMP_DIR/bin"
FAKE_LOG="$FAKE_BIN/docker.calls"
ORIGINAL_HOME="$TMP_DIR/original-home"

cleanup_test() {
  rm -rf "$TMP_DIR"
}
trap cleanup_test EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"
}

assert_not_contains() {
  if grep -Fq -- "$1" "$2"; then
    fail "unexpected '$1' in $2"
  fi
}

assert_empty_directory() {
  [[ -z "$(find "$1" -mindepth 1 -maxdepth 1 -print -quit)" ]] ||
    fail "temporary Docker CLI state was not removed from $1"
}

assert_line_order() {
  local first="$1"
  local second="$2"
  local file="$3"
  local first_line second_line
  first_line="$(grep -nF -- "$first" "$file" | head -n 1 | cut -d: -f1)"
  second_line="$(grep -nF -- "$second" "$file" | head -n 1 | cut -d: -f1)"
  [[ -n "$first_line" && -n "$second_line" && "$first_line" -lt "$second_line" ]] ||
    fail "expected '$first' before '$second' in $file"
}

mkdir -m 700 "$CLI_TEMP_PARENT" "$FAKE_BIN" "$ORIGINAL_HOME"
mkdir -m 700 "$ORIGINAL_HOME/.docker"
printf 'must-not-be-read\n' >"$ORIGINAL_HOME/.docker/credential-sentinel"

cat >"$FAKE_BIN/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail

LOG="$(dirname "$0")/docker.calls"
ROOT="${HOME%/home}"
MODE() {
  stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1"
}

[[ "${1:-}" == "--context" && "${2:-}" == "default" ]] || exit 61
shift 2
[[ "${1:-}" == "compose" ]] || exit 62
shift
[[ "$ROOT" == */restaurant-pos-staging-docker-cli.* ]] || exit 63
[[ "$HOME" == "$ROOT/home" && "$DOCKER_CONFIG" == "$ROOT/docker-config" ]] || exit 64
[[ "$HOME" != "/nonexistent" && "$DOCKER_CONFIG" != "/nonexistent" ]] || exit 65
[[ -d "$ROOT" && -d "$HOME" && -d "$DOCKER_CONFIG" ]] || exit 66
[[ ! -L "$ROOT" && ! -L "$HOME" && ! -L "$DOCKER_CONFIG" ]] || exit 67
[[ "$(MODE "$ROOT")" == "700" && "$(MODE "$HOME")" == "700" && "$(MODE "$DOCKER_CONFIG")" == "700" ]] || exit 68
[[ -w "$ROOT" && -w "$HOME" && -w "$DOCKER_CONFIG" ]] || exit 69

printf 'state=%s home=%s config=%s modes=%s,%s,%s args=%s\n' \
  "$ROOT" "$HOME" "$DOCKER_CONFIG" \
  "$(MODE "$ROOT")" "$(MODE "$HOME")" "$(MODE "$DOCKER_CONFIG")" "$*" >>"$LOG"

if [[ "${1:-}" == "version" ]]; then
  printf 'plugin-discovery context=default\n' >>"$LOG"
  printf 'Docker Compose version fake\n'
  exit 0
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-name|--env-file|-f)
      shift 2
      ;;
    build|up)
      action="$1"
      shift
      printf 'action=%s remaining=%s\n' "$action" "$*" >>"$LOG"
      if [[ "$action" == "build" && "${1:-}" == "backend" && -f "$(dirname "$0")/fail-backend" ]]; then
        exit 70
      fi
      exit 0
      ;;
    *)
      shift
      ;;
  esac
done
exit 71
DOCKER
chmod +x "$FAKE_BIN/docker"

run_case() {
  local case_name="$1"
  local body="$2"
  local output="$TMP_DIR/$case_name.out"
  local error="$TMP_DIR/$case_name.err"

  HOME="$ORIGINAL_HOME" TMPDIR="$CLI_TEMP_PARENT" \
    bash -c '
      set -Eeuo pipefail
      source "$1"
      SAFE_PATH="$2:/usr/bin:/bin"
      DOCKER_BIN="$2/docker"
      COMPOSE_PROJECT_NAME="restaurant-pos-staging"
      STAGING_COMPOSE_FILE="$3"
      touch "$STAGING_COMPOSE_FILE"
      trap cleanup EXIT
      trap cleanup ERR
      trap handle_interrupt INT
      trap handle_terminate TERM
      eval "$4"
    ' bash "$RUNNER" "$FAKE_BIN" "$TMP_DIR/compose.yml" "$body" >"$output" 2>"$error"
}

bash -n "$RUNNER"
assert_not_contains '/nonexistent' "$RUNNER"

: >"$FAKE_LOG"
run_case success '
  controlled_compose /tmp/fake.env build backend
  controlled_compose /tmp/fake.env build nginx
  controlled_compose /tmp/fake.env up -d
'
assert_contains 'plugin-discovery context=default' "$FAKE_LOG"
assert_contains "state=$CLI_TEMP_PARENT/restaurant-pos-staging-docker-cli." "$FAKE_LOG"
assert_contains 'modes=700,700,700' "$FAKE_LOG"
assert_not_contains "$ORIGINAL_HOME/.docker" "$FAKE_LOG"
assert_not_contains '/nonexistent' "$FAKE_LOG"
assert_contains 'action=build remaining=backend' "$FAKE_LOG"
assert_contains 'action=build remaining=nginx' "$FAKE_LOG"
assert_contains 'action=up remaining=-d' "$FAKE_LOG"
assert_line_order 'action=build remaining=backend' 'action=build remaining=nginx' "$FAKE_LOG"
assert_line_order 'action=build remaining=nginx' 'action=up remaining=-d' "$FAKE_LOG"
assert_empty_directory "$CLI_TEMP_PARENT"

: >"$FAKE_LOG"
touch "$FAKE_BIN/fail-backend"
if run_case backend_failure '
  controlled_compose /tmp/fake.env build backend
  controlled_compose /tmp/fake.env build nginx
  controlled_compose /tmp/fake.env up -d
'; then
  fail 'backend failure case unexpectedly passed'
fi
rm "$FAKE_BIN/fail-backend"
assert_contains 'action=build remaining=backend' "$FAKE_LOG"
assert_not_contains 'action=build remaining=nginx' "$FAKE_LOG"
assert_not_contains 'action=up remaining=-d' "$FAKE_LOG"
assert_empty_directory "$CLI_TEMP_PARENT"

: >"$FAKE_LOG"
if run_case symlink_replacement '
  initialize_docker_cli_state
  rm -rf "$DOCKER_CLI_CONFIG"
  ln -s "'"$ORIGINAL_HOME"'/.docker" "$DOCKER_CLI_CONFIG"
  controlled_compose /tmp/fake.env build backend
'; then
  fail 'symlink replacement case unexpectedly passed'
fi
assert_not_contains 'action=build remaining=backend' "$FAKE_LOG"
[[ -f "$ORIGINAL_HOME/.docker/credential-sentinel" ]] ||
  fail 'symlink replacement modified the original Docker config sentinel'
assert_empty_directory "$CLI_TEMP_PARENT"

for handler in handle_interrupt handle_terminate; do
  : >"$FAKE_LOG"
  if run_case "$handler" "
    initialize_docker_cli_state
    $handler
  "; then
    fail "$handler unexpectedly returned success"
  fi
  assert_contains 'plugin-discovery context=default' "$FAKE_LOG"
  assert_empty_directory "$CLI_TEMP_PARENT"
done

echo 'PASS: STG-004 deploy wrapper uses writable private Docker CLI state, preserves plugin discovery and serial build safety, rejects symlink replacement, and cleans success/error/signal paths.'
