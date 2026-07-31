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
digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}
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
[[ "${1:-}" == "--context" && "${2:-}" == "default" ]] || exit 92
shift 2
if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
  printf 'sha256:stg004fakeimage\n'
  exit 0
fi
if [[ "${1:-}" == "inspect" ]]; then
  printf 'name=/stg004 status=running health=healthy image=sha256:stg004fake\n'
  exit 0
fi
[[ "${1:-}" == "compose" ]] || exit 92
shift
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
    mem_limit: 536870912
    max-size: 10m
    max-file: "3"
  backend:
    image: $(value BACKEND_IMAGE)
    SPRING_PROFILES_ACTIVE: cloud
    DB_NAME: $(value DB_NAME)
    DB_USER: $(value DB_USER)
    APP_FEATURES_PRINTING: "false"
    cpus: 1
    mem_limit: 805306368
    max-size: 10m
    max-file: "3"
  nginx:
    image: $(value FRONTEND_IMAGE)
    VITE_APP_BUILD_VERSION: $(value VITE_APP_BUILD_VERSION)
    NGINX_SERVER_NAME: localhost
    ports:
      - 127.0.0.1:18080:80
    cpus: 0.25
    mem_limit: 134217728
    max-size: 10m
    max-file: "3"
EOF
    ;;
  ps) : ;;
  build) printf 'fake build complete\n' ;;
  up) printf 'fake up complete\n' ;;
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
BASE_ENV="$TMP_DIR/base.env"
cp "$ENV_FILE" "$BASE_ENV"
reset_env() { cp "$BASE_ENV" "$ENV_FILE"; chmod 600 "$ENV_FILE"; }

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
reset_env

cat >"$FAKE_BIN/ss" <<'SS_FAILED'
#!/usr/bin/env bash
exit 42
SS_FAILED
chmod +x "$FAKE_BIN/ss"
expect_failure ss_failed run_preflight
assert_contains 'CHECK|PORT_18080|EVIDENCE_PENDING|' "$TMP_DIR/ss_failed.out"
assert_not_contains 'CHECK|PORT_18080|PASS|' "$TMP_DIR/ss_failed.out"

cat >"$FAKE_BIN/ss" <<'SS_CLEAR_AGAIN'
#!/usr/bin/env bash
exit 0
SS_CLEAR_AGAIN
chmod +x "$FAKE_BIN/ss"

EVIDENCE_DIR="$FAKE_ROOT/evidence"
EVIDENCE_FILE="$EVIDENCE_DIR/approved-preflight.txt"
mkdir -p "$EVIDENCE_DIR"
chmod 700 "$EVIDENCE_DIR"
ENV_DIGEST="$(digest "$ENV_FILE")"
cat >"$EVIDENCE_FILE" <<EOF
EVIDENCE|APPROVED_SHA|$SHA
EVIDENCE|STAGING_ROOT|$FAKE_ROOT
EVIDENCE|COMPOSE_PROJECT|restaurant-pos-staging
EVIDENCE|ENV_SHA256|$ENV_DIGEST
EVIDENCE|RESOURCE_THRESHOLDS|min_free_bytes=1048576;max_used_percent=80;min_available_memory_kb=1024;min_cpu_count=1
SUMMARY|PASS|same-host Staging preflight passed without state changes
EOF
chmod 600 "$EVIDENCE_FILE"
EVIDENCE_DIGEST="$(digest "$EVIDENCE_FILE")"
DEPLOY_RUNNER="$RELEASE/deployment/cloud/staging-deploy.sh"

: >"$FAKE_BIN/docker.calls"
PATH="$FAKE_BIN:$PATH" "$DEPLOY_RUNNER" \
  --execute-start \
  --approved-sha "$SHA" \
  --preflight-evidence "$EVIDENCE_FILE" \
  --preflight-evidence-sha256 "$EVIDENCE_DIGEST" \
  --env-file "$ENV_FILE" >"$TMP_DIR/approved-start.out"
assert_contains ' build ' "$FAKE_BIN/docker.calls"
assert_contains ' up ' "$FAKE_BIN/docker.calls"

# Authorization must remain bound to the environment snapshot that build/up
# actually consume, even if the original env file changes afterwards.
(
  source "$DEPLOY_RUNNER"
  EXECUTE_START="true"
  STAGING_COMMIT_SHA="$SHA"
  APPROVED_SHA="$SHA"
  STAGING_ROOT="$FAKE_ROOT"
  PREFLIGHT_EVIDENCE="$EVIDENCE_FILE"
  PREFLIGHT_EVIDENCE_SHA256="$EVIDENCE_DIGEST"
  ORIGINAL_ENV_FILE="$ENV_FILE"
  ENV_SNAPSHOT_DIGEST="$ENV_DIGEST"
  printf '\n# changed after environment snapshot\n' >>"$ORIGINAL_ENV_FILE"
  validate_start_authorization
  grep -Fxq "EVIDENCE|ENV_SHA256|$ENV_DIGEST" "$PREFLIGHT_EVIDENCE_SNAPSHOT"
  cleanup
) || fail 'authorization was not bound to the deployed environment snapshot'
reset_env

# If the source evidence changes immediately after copy, all hash and field
# checks still read the same private snapshot.
RACE_EVIDENCE="$EVIDENCE_DIR/race-evidence.txt"
cp "$EVIDENCE_FILE" "$RACE_EVIDENCE"
chmod 600 "$RACE_EVIDENCE"
RACE_DIGEST="$(digest "$RACE_EVIDENCE")"
(
  source "$DEPLOY_RUNNER"
  EXECUTE_START="true"
  STAGING_COMMIT_SHA="$SHA"
  APPROVED_SHA="$SHA"
  STAGING_ROOT="$FAKE_ROOT"
  PREFLIGHT_EVIDENCE="$RACE_EVIDENCE"
  PREFLIGHT_EVIDENCE_SHA256="$RACE_DIGEST"
  ORIGINAL_ENV_FILE="$ENV_FILE"
  ENV_SNAPSHOT_DIGEST="$ENV_DIGEST"
  cp() {
    command cp "$@"
    if [[ "$1" == "$PREFLIGHT_EVIDENCE" ]]; then
      printf 'FORGED_AFTER_COPY\n' >"$PREFLIGHT_EVIDENCE"
    fi
  }
  validate_start_authorization
  grep -Fxq "EVIDENCE|APPROVED_SHA|$SHA" "$PREFLIGHT_EVIDENCE_SNAPSHOT"
  ! grep -Fq 'FORGED_AFTER_COPY' "$PREFLIGHT_EVIDENCE_SNAPSHOT"
  cleanup
) || fail 'evidence authorization did not use one immutable private snapshot'

: >"$FAKE_BIN/docker.calls"
expect_failure local_before_start env PATH="$FAKE_BIN:$PATH" "$DEPLOY_RUNNER" \
  --local-validate --execute-start --approved-sha "$SHA" \
  --preflight-evidence "$EVIDENCE_FILE" --preflight-evidence-sha256 "$EVIDENCE_DIGEST" \
  --env-file "$ENV_FILE"
assert_not_contains ' build ' "$FAKE_BIN/docker.calls"
assert_not_contains ' up ' "$FAKE_BIN/docker.calls"

expect_failure local_after_start env PATH="$FAKE_BIN:$PATH" "$DEPLOY_RUNNER" \
  --execute-start --local-validate --approved-sha "$SHA" \
  --preflight-evidence "$EVIDENCE_FILE" --preflight-evidence-sha256 "$EVIDENCE_DIGEST" \
  --env-file "$ENV_FILE"

FORGED_EVIDENCE="$EVIDENCE_DIR/forged.txt"
printf 'SUMMARY|PASS|same-host Staging preflight passed without state changes\n' >"$FORGED_EVIDENCE"
chmod 600 "$FORGED_EVIDENCE"
FORGED_DIGEST="$(digest "$FORGED_EVIDENCE")"
expect_failure forged_evidence env PATH="$FAKE_BIN:$PATH" "$DEPLOY_RUNNER" \
  --execute-start --approved-sha "$SHA" \
  --preflight-evidence "$FORGED_EVIDENCE" --preflight-evidence-sha256 "$FORGED_DIGEST" \
  --env-file "$ENV_FILE"
assert_contains 'not bound to the approved SHA' "$TMP_DIR/forged_evidence.out"

chmod 666 "$EVIDENCE_FILE"
expect_failure writable_evidence env PATH="$FAKE_BIN:$PATH" "$DEPLOY_RUNNER" \
  --execute-start --approved-sha "$SHA" \
  --preflight-evidence "$EVIDENCE_FILE" --preflight-evidence-sha256 "$EVIDENCE_DIGEST" \
  --env-file "$ENV_FILE"
assert_contains 'mode 0600' "$TMP_DIR/writable_evidence.out"
chmod 600 "$EVIDENCE_FILE"

printf '\n' >>"$EVIDENCE_FILE"
expect_failure tampered_evidence env PATH="$FAKE_BIN:$PATH" "$DEPLOY_RUNNER" \
  --execute-start --approved-sha "$SHA" \
  --preflight-evidence "$EVIDENCE_FILE" --preflight-evidence-sha256 "$EVIDENCE_DIGEST" \
  --env-file "$ENV_FILE"
assert_contains 'does not match the exact evidence file' "$TMP_DIR/tampered_evidence.out"
git -C "$RELEASE" status --short | grep -q . &&
  fail 'evidence tests unexpectedly changed the release checkout'

echo 'PASS: STG-004 preflight uses isolated fake tools, rejects unsafe isolation and printing input, and never executes a Docker lifecycle action.'
