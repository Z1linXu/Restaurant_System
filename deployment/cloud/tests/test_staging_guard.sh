#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
SOURCE_CLOUD_DIR="$REPOSITORY_ROOT/deployment/cloud"
# macOS commonly maps /var through a symlink. The production guard correctly
# rejects that shape, so use a physical local path for this positive fixture.
TMP_DIR="$(mktemp -d /private/tmp/restaurant-pos-staging-guard.XXXXXX)"
FAKE_BIN="$TMP_DIR/bin"
CALL_LOG="$TMP_DIR/docker.calls"

cleanup() {
  [[ "${KEEP_STAGING_GUARD_TMP:-false}" == "true" ]] && return
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local needle="$1"
  local file="$2"
  grep -Fq -- "$needle" "$file" || fail "expected '$needle' in $file"
}

set_env() {
  local key="$1"
  local value="$2"
  local file="$3"
  local next="$file.next"
  awk -v key="$key" -v value="$value" '
    index($0, key "=") == 1 { print key "=" value; found = 1; next }
    { print }
    END { if (!found) print key "=" value }
  ' "$file" >"$next"
  mv "$next" "$file"
}

expect_failure() {
  local label="$1"
  shift
  if "$@" >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then
    fail "$label unexpectedly passed"
  fi
  assert_contains "staging guard:" "$TMP_DIR/$label.err"
}

mkdir -p "$FAKE_BIN"
cat >"$FAKE_BIN/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail

echo "$*" >>"${FAKE_DOCKER_LOG:?}"
[[ "$1" == "compose" ]] || exit 64
shift

env_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      env_file="$2"
      shift 2
      ;;
    --project-name|-f)
      shift 2
      ;;
    config)
      shift
      break
      ;;
    *)
      shift
      ;;
  esac
done

[[ -n "$env_file" ]] || exit 65
value() {
  grep -E "^$1=" "$env_file" | tail -n 1 | sed "s/^$1=//"
}

if [[ "${1:-}" == "--services" ]]; then
  printf 'db\nbackend\nnginx\n'
  exit 0
fi

printf 'services:\n'
printf '  db:\n    image: postgres:%s\n' "$(value POSTGRES_IMAGE_TAG)"
printf '    source: %s\n' "$(value STAGING_POSTGRES_DATA_DIR)"
printf '  backend:\n    image: %s\n' "$(value BACKEND_IMAGE)"
printf '    APP_FEATURES_PRINTING: "%s"\n' "$(value STAGING_PRINTING_FEATURE_ENABLED)"
printf '  nginx:\n    image: %s\n' "$(value FRONTEND_IMAGE)"
printf '    ports:\n      - 127.0.0.1:18080:80\n'
DOCKER
chmod +x "$FAKE_BIN/docker"

SHA="$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"
STAGING_ROOT="$TMP_DIR/restaurant-pos/staging"
RELEASE_DIR="$STAGING_ROOT/releases/$SHA"
CONFIG_DIR="$STAGING_ROOT/config"
POSTGRES_DIR="$STAGING_ROOT/state/postgres"
mkdir -p "$(dirname "$RELEASE_DIR")" "$CONFIG_DIR" "$POSTGRES_DIR"
git clone --quiet "$REPOSITORY_ROOT" "$RELEASE_DIR"

# The detached test checkout has the committed base; copy only the package under
# test so the script can validate its own physical release path and Git HEAD.
cp "$SOURCE_CLOUD_DIR/docker-compose.staging.yml" "$RELEASE_DIR/deployment/cloud/"
cp "$SOURCE_CLOUD_DIR/.env.staging.example" "$RELEASE_DIR/deployment/cloud/"
cp "$SOURCE_CLOUD_DIR/staging-deploy.sh" "$RELEASE_DIR/deployment/cloud/"
cp "$SOURCE_CLOUD_DIR/staging-health-check.sh" "$RELEASE_DIR/deployment/cloud/"
chmod +x "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"

ENV_FILE="$CONFIG_DIR/.env.staging"
BASE_ENV="$TMP_DIR/base.env"
cat >"$BASE_ENV" <<EOF
COMPOSE_PROJECT_NAME=restaurant-pos-staging
STAGING_ROOT=$STAGING_ROOT
STAGING_COMMIT_SHA=$SHA
STAGING_POSTGRES_DATA_DIR=$POSTGRES_DIR
HTTP_BIND_ADDRESS=127.0.0.1
HTTP_PORT=18080
NGINX_SERVER_NAME=localhost
TZ=America/Toronto
POSTGRES_IMAGE_TAG=16-alpine
DB_NAME=restaurant_pos_staging
DB_USER=restaurant_pos_staging
DB_PASSWORD=staging-db-value-12345
JWT_SECRET=staging-jwt-value-12345678901234567890
SPRING_PROFILES_ACTIVE=cloud
JAVA_OPTS=-Xms128m -Xmx512m
BACKEND_IMAGE=restaurant-pos-backend:staging-$SHA
FRONTEND_IMAGE=restaurant-pos-frontend:staging-$SHA
VITE_APP_BUILD_VERSION=staging-$SHA
STAGING_PRINT_MODE=DISABLED
STAGING_PRINTING_FEATURE_ENABLED=false
EOF

run_validate() {
  PATH="$FAKE_BIN:$PATH" FAKE_DOCKER_LOG="$CALL_LOG" \
    "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate
}

reset_env() {
  cp "$BASE_ENV" "$ENV_FILE"
  : >"$CALL_LOG"
}

reset_env
if ! run_validate >"$TMP_DIR/positive.out" 2>"$TMP_DIR/positive.err"; then
  cat "$TMP_DIR/positive.err" >&2
  fail "positive validation failed"
fi
assert_contains "Staging validation passed" "$TMP_DIR/positive.out"
assert_contains "--project-name restaurant-pos-staging" "$CALL_LOG"
assert_contains "config" "$CALL_LOG"
if grep -Eq '( build | up )' "$CALL_LOG"; then
  fail "validate invoked build or up"
fi

reset_env
expect_failure arbitrary_server_root \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --validate

reset_env
set_env COMPOSE_PROJECT_NAME cloud "$ENV_FILE"
expect_failure project_name run_validate

reset_env
set_env STAGING_COMMIT_SHA '' "$ENV_FILE"
expect_failure blank_sha run_validate

reset_env
set_env BACKEND_IMAGE restaurant-pos-backend:local "$ENV_FILE"
expect_failure local_image run_validate

reset_env
set_env FRONTEND_IMAGE "restaurant-pos-frontend:staging-${SHA}different" "$ENV_FILE"
expect_failure wrong_sha_tag run_validate

reset_env
set_env HTTP_PORT 80 "$ENV_FILE"
expect_failure production_port run_validate

reset_env
set_env HTTP_PORT 443 "$ENV_FILE"
expect_failure tls_port run_validate

reset_env
set_env HTTP_BIND_ADDRESS 0.0.0.0 "$ENV_FILE"
expect_failure public_bind run_validate

reset_env
set_env STAGING_POSTGRES_DATA_DIR relative/postgres "$ENV_FILE"
expect_failure relative_data_path run_validate

reset_env
set_env STAGING_POSTGRES_DATA_DIR "$STAGING_ROOT/state/../postgres" "$ENV_FILE"
expect_failure traversal_data_path run_validate

reset_env
set_env STAGING_POSTGRES_DATA_DIR /home/ubuntu/Restaurant_System/deployment/cloud/data/postgres "$ENV_FILE"
expect_failure production_data_path run_validate

reset_env
set_env DB_NAME restaurant_pos "$ENV_FILE"
expect_failure shared_db_name run_validate

reset_env
set_env DB_USER postgres "$ENV_FILE"
expect_failure shared_db_user run_validate

reset_env
set_env DB_PASSWORD '<change-me>' "$ENV_FILE"
expect_failure password_placeholder run_validate

reset_env
set_env JWT_SECRET '<generate-secret>' "$ENV_FILE"
expect_failure jwt_placeholder run_validate

reset_env
set_env STAGING_PRINT_MODE REAL "$ENV_FILE"
expect_failure real_printing run_validate

reset_env
set_env STAGING_PRINT_MODE PAD_DIRECT "$ENV_FILE"
expect_failure pad_direct_printing run_validate

reset_env
set_env STAGING_PRINTING_FEATURE_ENABLED true "$ENV_FILE"
expect_failure disabled_feature_enabled run_validate

reset_env
set_env STAGING_PRINT_MODE MOCK "$ENV_FILE"
expect_failure mock_without_feature run_validate

reset_env
set_env STAGING_PRINT_MODE MOCK "$ENV_FILE"
set_env STAGING_PRINTING_FEATURE_ENABLED true "$ENV_FILE"
run_validate >"$TMP_DIR/mock.out"
assert_contains "Staging validation passed" "$TMP_DIR/mock.out"

reset_env
set_env STAGING_PRINTER_ENDPOINT 192.168.1.10:9100 "$ENV_FILE"
expect_failure printer_endpoint run_validate

reset_env
set_env SPRING_PROFILES_ACTIVE dev "$ENV_FILE"
expect_failure profile run_validate

reset_env
set_env APP_SEED_DEMO_DATA_ENABLED true "$ENV_FILE"
expect_failure unsafe_seed run_validate

reset_env
rm -rf "$POSTGRES_DIR"
mkdir -p "$TMP_DIR/outside-postgres"
ln -s "$TMP_DIR/outside-postgres" "$POSTGRES_DIR"
expect_failure symlink_data_path run_validate
rm "$POSTGRES_DIR"
mkdir -p "$POSTGRES_DIR"

reset_env
PATH="$FAKE_BIN:$PATH" FAKE_DOCKER_LOG="$CALL_LOG" \
  "$RELEASE_DIR/deployment/cloud/staging-health-check.sh" --env-file "$ENV_FILE" --local-validate >"$TMP_DIR/health.out"
assert_contains "configuration passed" "$TMP_DIR/health.out"

echo "PASS: staging guard rejects unsafe project, SHA, image, network, path, credential, printing, profile, seed, and symlink configurations."
