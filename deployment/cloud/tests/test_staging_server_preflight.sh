#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-stg004-preflight.XXXXXX")"
TMP_DIR="$(cd -P -- "$TMP_DIR" && pwd)"
FAKE_BIN="$TMP_DIR/fake-bin"
FAKE_ROOT="$TMP_DIR/srv/restaurant-pos/staging"
PRODUCTION_ROOT="$TMP_DIR/production"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() {
  local label="$1"
  shift
  if "$@" >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then
    fail "$label unexpectedly passed"
  fi
  cat "$TMP_DIR/$label.err" >>"$TMP_DIR/$label.out"
}

mkdir -p "$FAKE_BIN" "$PRODUCTION_ROOT"

cat >"$FAKE_BIN/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
LOG="$(dirname "$0")/docker.calls"
printf 'argv=%s\n' "$*" >>"$LOG"
if [[ "${1:-}" == "context" ]]; then
  [[ "${2:-}" == "inspect" && "${3:-}" == "default" ]] || exit 91
  printf 'unix:///tmp/stg004-fake.sock\n'
  exit 0
fi
if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
  printf 'sha256:stg004fakeimage\n'
  exit 0
fi
if [[ "${1:-}" == "inspect" ]]; then
  printf 'name=/stg004 status=running health=healthy image=sha256:stg004fake\n'
  exit 0
fi
[[ "${1:-}" == "--context" && "${2:-}" == "default" && "${3:-}" == "compose" ]] || exit 92
shift 3
env_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-name|-f) shift 2 ;;
    --env-file) env_file="$2"; shift 2 ;;
    *) break ;;
  esac
done
[[ -n "$env_file" && -f "$env_file" ]] || exit 93
action="${1:-}"; shift || true
value() { grep -E "^$1=" "$env_file" | tail -n 1 | sed 's/^[^=]*=//; s/^"//; s/"$//'; }
case "$action" in
  config)
    if [[ "${1:-}" == "--services" ]]; then
      printf 'db\nbackend\nnginx\n'
      exit 0
    fi
    cat <<EOF
services:
  db:
    image: postgres:16-alpine
    source: $(value STAGING_POSTGRES_DATA_DIR)
    cpus: 0.75
    mem_limit: 512m
  backend:
    image: $(value BACKEND_IMAGE)
    SPRING_PROFILES_ACTIVE: cloud
    DB_NAME: $(value DB_NAME)
    DB_USER: $(value DB_USER)
    APP_FEATURES_PRINTING: "false"
    cpus: 1.00
    mem_limit: 768m
  nginx:
    image: $(value FRONTEND_IMAGE)
    VITE_APP_BUILD_VERSION: $(value VITE_APP_BUILD_VERSION)
    NGINX_SERVER_NAME: localhost
    ports:
      - 127.0.0.1:18080:80
    cpus: 0.25
    mem_limit: 128m
    max-size: 10m
    max-file: "3"
EOF
    ;;
  ps) : ;;
  *) exit 94 ;;
esac
DOCKER
chmod +x "$FAKE_BIN/docker"

cat >"$FAKE_BIN/ss" <<'SS'
#!/usr/bin/env bash
exit 0
SS
cat >"$FAKE_BIN/df" <<'DF'
#!/usr/bin/env bash
printf 'Filesystem 1024-blocks Used Available Capacity Mounted on\n'
printf '/dev/fake 10000000 1000 9000000 1%% /\n'
DF
cat >"$FAKE_BIN/getconf" <<'GETCONF'
#!/usr/bin/env bash
printf '8\n'
GETCONF
chmod +x "$FAKE_BIN/ss" "$FAKE_BIN/df" "$FAKE_BIN/getconf"

SEED="$TMP_DIR/release-seed"
git clone --quiet "$REPOSITORY_ROOT" "$SEED"
cp "$REPOSITORY_ROOT/deployment/cloud/staging-server-preflight.sh" \
  "$SEED/deployment/cloud/staging-server-preflight.sh"
cp "$REPOSITORY_ROOT/deployment/cloud/staging-deploy.sh" \
  "$SEED/deployment/cloud/staging-deploy.sh"
sed "s|/srv/restaurant-pos/staging|$FAKE_ROOT|g" \
  "$SEED/deployment/cloud/staging-server-preflight.sh" >"$SEED/deployment/cloud/staging-server-preflight.sh.next"
mv "$SEED/deployment/cloud/staging-server-preflight.sh.next" "$SEED/deployment/cloud/staging-server-preflight.sh"
sed "s|/srv/restaurant-pos/staging|$FAKE_ROOT|g" \
  "$SEED/deployment/cloud/staging-deploy.sh" >"$SEED/deployment/cloud/staging-deploy.sh.next"
mv "$SEED/deployment/cloud/staging-deploy.sh.next" "$SEED/deployment/cloud/staging-deploy.sh"
chmod +x "$SEED/deployment/cloud/staging-server-preflight.sh" "$SEED/deployment/cloud/staging-deploy.sh"
git -C "$SEED" add deployment/cloud/staging-server-preflight.sh deployment/cloud/staging-deploy.sh
git -C "$SEED" -c user.name=stg004-test -c user.email=stg004-test@example.invalid \
  commit --quiet -m 'test fixture with isolated root'
SHA="$(git -C "$SEED" rev-parse HEAD)"
RELEASE="$FAKE_ROOT/releases/$SHA"
mkdir -p "$FAKE_ROOT/releases" "$FAKE_ROOT/config" "$FAKE_ROOT/state/postgres"
chmod 700 "$FAKE_ROOT/config"
mv "$SEED" "$RELEASE"

ENV_FILE="$FAKE_ROOT/config/.env.staging"
cat >"$ENV_FILE" <<EOF
COMPOSE_PROJECT_NAME=restaurant-pos-staging
STAGING_ROOT=$FAKE_ROOT
STAGING_COMMIT_SHA=$SHA
STAGING_POSTGRES_DATA_DIR=$FAKE_ROOT/state/postgres
HTTP_BIND_ADDRESS=127.0.0.1
HTTP_PORT=18080
NGINX_SERVER_NAME=localhost
TZ=America/Toronto
POSTGRES_IMAGE_TAG=16-alpine
DB_NAME=restaurant_pos_staging_test
DB_USER=restaurant_pos_staging_test
DB_PASSWORD=stg004dbvalueabcdefghijkl
JWT_SECRET=stg004jwtvalueabcdefghijklmnopqrstuvwxyz0123456789
SPRING_PROFILES_ACTIVE=cloud
JAVA_OPTS="-Xms128m -Xmx512m"
BACKEND_IMAGE=restaurant-pos-backend:staging-$SHA
FRONTEND_IMAGE=restaurant-pos-frontend:staging-$SHA
VITE_APP_BUILD_VERSION=staging-$SHA
STAGING_PRINT_MODE=DISABLED
STAGING_PRINTING_FEATURE_ENABLED=false
STAGING_DB_CPU_LIMIT=0.75
STAGING_DB_MEMORY_LIMIT=512m
STAGING_BACKEND_CPU_LIMIT=1.00
STAGING_BACKEND_MEMORY_LIMIT=768m
STAGING_NGINX_CPU_LIMIT=0.25
STAGING_NGINX_MEMORY_LIMIT=128m
STAGING_LOG_MAX_SIZE=10m
STAGING_LOG_MAX_FILE=3
EOF
chmod 600 "$ENV_FILE"

RUNNER="$RELEASE/deployment/cloud/staging-server-preflight.sh"
run_preflight() {
  PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate --env-file "$ENV_FILE" \
    --approved-sha "$SHA" --production-project cloud --production-root "$PRODUCTION_ROOT" \
    --min-free-bytes 1048576 --max-used-percent 80 --min-available-memory-kb 1024 --min-cpu-count 1
}

bash -n "$RUNNER"
set +e
run_preflight >"$TMP_DIR/pass.out"
status=$?
set -e
if [[ "$status" -ne 0 && "$status" -ne 3 ]]; then
  cat "$TMP_DIR/pass.out" >&2
  fail 'baseline preflight did not pass'
fi
assert_contains 'CHECK|RELEASE_SHA|PASS|' "$TMP_DIR/pass.out"
assert_contains 'CHECK|PORT_18080|PASS|' "$TMP_DIR/pass.out"
assert_contains 'CHECK|STAGING_INPUTS|PASS|' "$TMP_DIR/pass.out"
if [[ "$status" -eq 0 ]]; then
  assert_contains 'SUMMARY|PASS|' "$TMP_DIR/pass.out"
else
  assert_contains 'SUMMARY|EVIDENCE_PENDING|' "$TMP_DIR/pass.out"
fi
assert_not_contains 'stg004dbvalueabcdefghijkl' "$TMP_DIR/pass.out"
assert_not_contains 'stg004jwtvalueabcdefghijklmnopqrstuvwxyz0123456789' "$TMP_DIR/pass.out"
assert_not_contains 'build ' "$FAKE_BIN/docker.calls"
assert_not_contains ' up ' "$FAKE_BIN/docker.calls"
assert_not_contains 'pull' "$FAKE_BIN/docker.calls"
assert_not_contains 'stop' "$FAKE_BIN/docker.calls"
assert_not_contains 'down' "$FAKE_BIN/docker.calls"

expect_failure no_action env PATH="$FAKE_BIN:$PATH" "$RUNNER"
assert_contains 'Usage:' "$TMP_DIR/no_action.out"

expect_failure overlapping_production env PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate --env-file "$ENV_FILE" \
  --approved-sha "$SHA" --production-project cloud --production-root "$FAKE_ROOT" \
  --min-free-bytes 1048576 --max-used-percent 80 --min-available-memory-kb 1024 --min-cpu-count 1
assert_contains 'NO_GO' "$TMP_DIR/overlapping_production.out"

cat >"$FAKE_BIN/ss" <<'SS_BUSY'
#!/usr/bin/env bash
printf 'LISTEN 0 4096 127.0.0.1:18080 0.0.0.0:*\n'
SS_BUSY
chmod +x "$FAKE_BIN/ss"
expect_failure port_busy run_preflight
assert_contains 'CHECK|PORT_18080|NO_GO|' "$TMP_DIR/port_busy.out"

cat >"$FAKE_BIN/ss" <<'SS_CLEAR'
#!/usr/bin/env bash
exit 0
SS_CLEAR
chmod +x "$FAKE_BIN/ss"
awk '{ sub(/^STAGING_PRINT_MODE=DISABLED$/, "STAGING_PRINT_MODE=PAD_DIRECT"); print }' "$ENV_FILE" >"$ENV_FILE.next"
mv "$ENV_FILE.next" "$ENV_FILE"
expect_failure printing_mode run_preflight
assert_contains 'NO_GO' "$TMP_DIR/printing_mode.out"
assert_not_contains 'PAD_DIRECT' "$TMP_DIR/printing_mode.out"

echo 'PASS: STG-004 preflight uses isolated fake tools, rejects unsafe isolation and printing input, and never executes a Docker lifecycle action.'
