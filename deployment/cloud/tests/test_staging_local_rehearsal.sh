#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
RUNNER="$REPOSITORY_ROOT/deployment/cloud/staging-local-rehearsal.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-stg003-test.XXXXXX")"
TMP_DIR="$(cd -P -- "$TMP_DIR" && pwd)"
LOCAL_TMP_BASE="$(cd -P -- "${TMPDIR:-/tmp}" && pwd)"
ISOLATED_BIN="$TMP_DIR/isolated-bin"
FAKE_BIN="$TMP_DIR/fake-bin"

cleanup() {
  while IFS= read -r release; do
    git -C "$REPOSITORY_ROOT" worktree remove --force "$release" >/dev/null 2>&1 || true
  done < <(find "$TMP_DIR" -type d -path '*/restaurant-pos/staging/releases/[0-9a-f]*' -print 2>/dev/null || true)
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
expect_failure() {
  local label="$1"
  shift
  if "$@" >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then
    fail "$label unexpectedly passed"
  fi
}
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }

make_isolated_path() {
  local command_path command_name
  mkdir -p "$ISOLATED_BIN" "$FAKE_BIN"
  for command_name in bash basename cat chmod cp cut dirname env find git grep head jq mkdir od openssl pwd rm sed seq sleep stat tail tr; do
    command_path="$(command -v "$command_name")" || fail "missing local test prerequisite: $command_name"
    ln -s "$command_path" "$ISOLATED_BIN/$command_name"
  done
  # Intentionally do not add docker. Every test case opts into a fake binary.
}

make_isolated_path
bash -n "$RUNNER"

# --plan is a pure no-op. Its PATH deliberately has no Docker binary.
SAFE_ROOT="$TMP_DIR/restaurant-pos/staging"
PATH="$ISOLATED_BIN" "$RUNNER" --plan --root "$SAFE_ROOT" >"$TMP_DIR/plan.out"
assert_contains 'STG-003 LOCAL-ONLY REHEARSAL PLAN' "$TMP_DIR/plan.out"
assert_contains 'printing=DISABLED' "$TMP_DIR/plan.out"
assert_not_contains 'docker_context=' "$TMP_DIR/plan.out"

# --run fails before root creation when the isolated PATH does not contain Docker.
PATH="$ISOLATED_BIN" expect_failure no_docker "$RUNNER" --run --confirm-local-container-start --root "$SAFE_ROOT"
assert_contains 'BLOCKED_LOCAL_DOCKER_RUNTIME_UNAVAILABLE' "$TMP_DIR/no_docker.err"
[[ ! -e "$TMP_DIR/restaurant-pos" ]] || fail 'Docker-unavailable run created local state'

PATH="$ISOLATED_BIN" expect_failure srv_root "$RUNNER" --plan --root /srv/restaurant-pos/staging
assert_contains 'allowed STG-003 namespace' "$TMP_DIR/srv_root.err"
PATH="$ISOLATED_BIN" expect_failure repository_root "$RUNNER" --plan --root "$REPOSITORY_ROOT/restaurant-pos/staging"
assert_contains 'allowed STG-003 namespace' "$TMP_DIR/repository_root.err"
PATH="$ISOLATED_BIN" expect_failure arbitrary_root "$RUNNER" --plan --root "$LOCAL_TMP_BASE/arbitrary/restaurant-pos/staging"
assert_contains 'allowed STG-003 namespace' "$TMP_DIR/arbitrary_root.err"
TMPDIR=/srv PATH="$ISOLATED_BIN" expect_failure unsafe_tmpdir_srv "$RUNNER" --plan
assert_contains 'LOCAL_TMP_BASE must resolve to an approved local temporary directory' "$TMP_DIR/unsafe_tmpdir_srv.err"
TMPDIR=/private PATH="$ISOLATED_BIN" expect_failure unsafe_tmpdir_private "$RUNNER" --plan
assert_contains 'LOCAL_TMP_BASE must resolve to an approved local temporary directory' "$TMP_DIR/unsafe_tmpdir_private.err"
PATH="$ISOLATED_BIN" expect_failure evidence_option "$RUNNER" --plan --evidence-file "$TMP_DIR/anywhere"
assert_contains 'unsupported option' "$TMP_DIR/evidence_option.err"

SYMLINK_NAMESPACE="${TMP_DIR/restaurant-pos-stg003-test/restaurant-pos-stg003-symlink}"
ln -s "$TMP_DIR" "$SYMLINK_NAMESPACE"
PATH="$ISOLATED_BIN" expect_failure symlink_root "$RUNNER" --plan --root "$SYMLINK_NAMESPACE/restaurant-pos/staging"
assert_contains 'symlink' "$TMP_DIR/symlink_root.err"

cat >"$FAKE_BIN/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
LOG="$(dirname "$0")/docker.calls"
printf 'argv=%s\n' "$*" >>"$LOG"
printf 'docker_config=%s\n' "${DOCKER_CONFIG-unset}" >>"$LOG"
if [[ "${1:-}" == "context" ]]; then
  [[ "${2:-}" == "inspect" && "${3:-}" == "default" ]] || exit 91
  if [[ "${4:-}" == "--format" ]]; then printf 'unix:///tmp/stg003-fake.sock\n'; fi
  exit 0
fi
if [[ "${1:-}" == "info" ]]; then
  [[ "${2:-}" == "--format" ]] || exit 95
  printf '%s/docker-compose\n' "$(dirname "$0")"
  exit 0
fi
[[ "${1:-}" == "--context" && "${2:-}" == "default" && "${3:-}" == "compose" ]] || exit 92
shift 3
project=""; env_file=""; compose_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-name) project="$2"; shift 2 ;;
    --env-file) env_file="$2"; shift 2 ;;
    -f) compose_file="$2"; shift 2 ;;
    *) break ;;
  esac
done
[[ "$project" == "restaurant-pos-staging" && -f "$env_file" && -f "$compose_file" ]] || exit 93
action="${1:-}"; shift || true
printf 'action=%s remaining=%s\n' "$action" "$*" >>"$LOG"
value() { grep -E "^$1=" "$env_file" | tail -n 1 | sed "s/^$1=//; s/^\"//; s/\"$//"; }
root="$(value STAGING_ROOT)"; sha="$(value STAGING_COMMIT_SHA)"
case "$action" in
  config)
    if [[ "${1:-}" == "--services" ]]; then printf 'db\nbackend\nnginx\n'; exit 0; fi
    if [[ "${1:-}" == "--format" && "${2:-}" == "json" ]]; then
      cat <<EOF
{"services":{"db":{"cpus":0.75,"mem_limit":"536870912","logging":{"driver":"local","options":{"max-size":"10m","max-file":"3"}},"ports":[]},"backend":{"cpus":1,"mem_limit":"805306368","logging":{"driver":"local","options":{"max-size":"10m","max-file":"3"}},"ports":[]},"nginx":{"cpus":0.25,"mem_limit":"134217728","logging":{"driver":"local","options":{"max-size":"10m","max-file":"3"}},"ports":[{"host_ip":"127.0.0.1","published":"18080","target":80,"protocol":"tcp"}]}}}
EOF
      exit 0
    fi
    cat <<EOF
services:
  db:
    image: postgres:16-alpine
    cpus: 0.75
    mem_limit: "536870912"
    logging:
      driver: local
      options:
        max-size: 10m
        max-file: "3"
    volumes:
      - type: bind
        source: $root/state/postgres
        target: /var/lib/postgresql/data
    cpus: 0.75
    mem_limit: 512m
    logging:
      options:
        max-size: 10m
        max-file: "3"
  backend:
    image: restaurant-pos-backend:staging-$sha
    environment:
      SPRING_PROFILES_ACTIVE: cloud
      APP_FEATURES_PRINTING: "false"
    cpus: 1
    mem_limit: "805306368"
    logging:
      options:
        max-size: 10m
        max-file: "3"
  nginx:
    image: restaurant-pos-frontend:staging-$sha
    ports:
      - host_ip: 127.0.0.1
        published: "18080"
        target: 80
    volumes:
      - type: bind
        source: $root/releases/$sha/deployment/cloud/nginx.http.conf.template
        target: /etc/nginx/templates/default.conf.template
    cpus: 0.25
    mem_limit: "134217728"
    logging:
      options:
        max-size: 10m
        max-file: "3"
EOF
    ;;
  build|up|stop|down|ps) printf 'fake-%s\n' "$action" ;;
  exec) printf '1|V1__initial.sql|t\n8|V8__add_owner_store_onboarding_requests.sql|t\n' ;;
  *) exit 94 ;;
esac
DOCKER
chmod +x "$FAKE_BIN/docker"
ln -s "$FAKE_BIN/docker" "$FAKE_BIN/docker-compose"

cat >"$FAKE_BIN/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
printf '200'
CURL
chmod +x "$FAKE_BIN/curl"

# This full fake run proves the fixed context/project and lifecycle command
# plan without resolving any host Docker binary or starting any container.
FAKE_ROOT="$SAFE_ROOT"
PATH="$FAKE_BIN:$ISOLATED_BIN" "$RUNNER" --run --confirm-local-container-start --root "$FAKE_ROOT" >"$TMP_DIR/fake-run.out"
FAKE_SHA="$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"
FAKE_RELEASE_DIR="$FAKE_ROOT/releases/$FAKE_SHA"
[[ -f "$FAKE_ROOT/evidence/stg-003-local-rehearsal.md" ]] || fail 'fixed evidence file was not created'
[[ "$(stat -f '%Lp' "$FAKE_ROOT/config/.env.staging" 2>/dev/null || stat -c '%a' "$FAKE_ROOT/config/.env.staging")" == "600" ]] || fail 'synthetic env is not mode 0600'
assert_contains 'action=build remaining=backend nginx' "$FAKE_BIN/docker.calls"
assert_contains 'action=up remaining=-d' "$FAKE_BIN/docker.calls"
assert_contains 'action=stop remaining=' "$FAKE_BIN/docker.calls"
assert_contains '--context default compose --project-name restaurant-pos-staging' "$FAKE_BIN/docker.calls"
assert_contains "docker_config=$FAKE_ROOT/config/docker-cli" "$FAKE_BIN/docker.calls"
assert_not_contains 'down -v' "$FAKE_BIN/docker.calls"
assert_not_contains 'Flyway clean' "$FAKE_BIN/docker.calls"
assert_not_contains '/srv/' "$FAKE_BIN/docker.calls"

PATH="$FAKE_BIN:$ISOLATED_BIN" "$RUNNER" --cleanup --confirm-local-container-start --root "$FAKE_ROOT" >"$TMP_DIR/fake-cleanup.out"
down_actions="$(grep '^action=down ' "$FAKE_BIN/docker.calls" || true)"
[[ "$down_actions" == 'action=down remaining=' ]] || fail 'cleanup must issue exactly one plain down action with no flags or services'
assert_not_contains '-v' "$FAKE_BIN/docker.calls"
assert_not_contains '--volumes' "$FAKE_BIN/docker.calls"

echo 'PASS: STG-003 uses pure planning, symlink-safe local roots, fixed evidence, isolated fake Docker lifecycle coverage, and no destructive cleanup.'
