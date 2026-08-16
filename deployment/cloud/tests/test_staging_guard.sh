#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
TMP_PARENT="$(cd -P "${TMPDIR:-/tmp}" && pwd)"
TMP_DIR="$(mktemp -d "$TMP_PARENT/restaurant-pos-staging-guard.XXXXXX")"
FAKE_BIN="$TMP_DIR/bin"
COMPOSE_TEMP_DIR="$TMP_DIR/compose-temp"

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

assert_not_contains() {
  local needle="$1"
  local file="$2"
  if grep -Fq -- "$needle" "$file"; then
    fail "did not expect '$needle' in $file"
  fi
}

assert_line_order() {
  local first="$1"
  local second="$2"
  local file="$3"
  local first_line second_line
  first_line="$(grep -nFx -- "$first" "$file" | head -n 1 | cut -d: -f1)"
  second_line="$(grep -nFx -- "$second" "$file" | head -n 1 | cut -d: -f1)"
  [[ -n "$first_line" && -n "$second_line" && "$first_line" -lt "$second_line" ]] ||
    fail "expected '$first' before '$second' in $file"
}

assert_empty_directory() {
  local directory="$1"
  [[ -z "$(find "$directory" -mindepth 1 -maxdepth 1 -print -quit)" ]] || fail "expected no temporary files in $directory"
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

set_env_literal() {
  local key="$1"
  local value="$2"
  local file="$3"
  local next="$file.next"
  grep -v "^${key}=" "$file" >"$next"
  printf '%s=%s\n' "$key" "$value" >>"$next"
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

[[ "$1" == "--context" && "$2" == "default" ]] || exit 63
shift 2
[[ "$1" == "compose" ]] || exit 64
shift
original_args="$*"
log_file="$(dirname "$0")/docker.calls"
state_root="${HOME%/home}"
mode() {
  stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1"
}
[[ "$state_root" == */restaurant-pos-staging-docker-cli.* ]] || exit 67
[[ "$HOME" == "$state_root/home" && "$DOCKER_CONFIG" == "$state_root/docker-config" ]] || exit 68
[[ "$HOME" != "/nonexistent" && "$DOCKER_CONFIG" != "/nonexistent" ]] || exit 69
[[ -d "$state_root" && -d "$HOME" && -d "$DOCKER_CONFIG" ]] || exit 70
[[ ! -L "$state_root" && ! -L "$HOME" && ! -L "$DOCKER_CONFIG" ]] || exit 71
[[ "$(mode "$state_root")" == "700" && "$(mode "$HOME")" == "700" && "$(mode "$DOCKER_CONFIG")" == "700" ]] || exit 72
[[ -w "$state_root" && -w "$HOME" && -w "$DOCKER_CONFIG" ]] || exit 73
printf 'cli_state_root=%s home=%s docker_config=%s modes=%s,%s,%s\n' \
  "$state_root" "$HOME" "$DOCKER_CONFIG" \
  "$(mode "$state_root")" "$(mode "$HOME")" "$(mode "$DOCKER_CONFIG")" >>"$log_file"

if [[ "${1:-}" == "version" ]]; then
  printf 'compose_plugin=available context=default\n' >>"$log_file"
  printf 'Docker Compose version fake\n'
  exit 0
fi

env_file=""
compose_action=""
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
      compose_action="config"
      shift
      break
      ;;
    build|up)
      compose_action="$1"
      shift
      break
      ;;
    *)
      shift
      ;;
  esac
done

[[ -n "$env_file" ]] || exit 65
printf 'args=%s\n' "$original_args" >>"$log_file"
printf 'ambient DB_NAME=%s DOCKER_HOST=%s DOCKER_CONTEXT=%s COMPOSE_FILE=%s\n' \
  "${DB_NAME-unset}" "${DOCKER_HOST-unset}" "${DOCKER_CONTEXT-unset}" "${COMPOSE_FILE-unset}" >>"$log_file"

value() {
  grep -E "^$1=" "$env_file" | tail -n 1 | sed "s/^$1=//; s/^\"//; s/\"$//"
}

normalized_cpu() {
  awk -v value="$1" 'BEGIN { printf "%.12g", value }'
}

memory_bytes() {
  local value="${1%[mM]}"
  printf '%s' "$((value * 1024 * 1024))"
}

if [[ "$(value DB_NAME)" == "restaurant_pos_staging_fake_config_failure" ]]; then
  exit 66
fi

if [[ "$compose_action" == "build" ]]; then
  printf 'fake build complete\n'
  exit 0
fi

if [[ "$compose_action" == "up" ]]; then
  printf 'fake up complete\n'
  exit 0
fi

if [[ "${1:-}" == "--services" ]]; then
  printf 'db\nbackend\nnginx\n'
  exit 0
fi

printf 'services:\n'
printf '  db:\n    image: postgres:%s\n' "$(value POSTGRES_IMAGE_TAG)"
printf '    source: %s\n' "$(value STAGING_POSTGRES_DATA_DIR)"
printf '    cpus: %s\n' "$(normalized_cpu "$(value STAGING_DB_CPU_LIMIT)")"
printf '    mem_limit: "%s"\n' "$(memory_bytes "$(value STAGING_DB_MEMORY_LIMIT)")"
printf '    max-size: %s\n' "$(value STAGING_LOG_MAX_SIZE)"
printf '    max-file: "%s"\n' "$(value STAGING_LOG_MAX_FILE)"
printf '  backend:\n    image: %s\n' "$(value BACKEND_IMAGE)"
printf '    SPRING_PROFILES_ACTIVE: %s\n' "$(value SPRING_PROFILES_ACTIVE)"
printf '    DB_NAME: %s\n' "$(value DB_NAME)"
printf '    DB_USER: %s\n' "$(value DB_USER)"
printf '    APP_FEATURES_PRINTING: "%s"\n' "$(value STAGING_PRINTING_FEATURE_ENABLED)"
printf '    APP_FEATURES_PLATFORM: "%s"\n' "$(value STAGING_PLATFORM_FEATURE_ENABLED)"
printf '    APP_PHASE_B_PROVISIONING_ENABLED: "%s"\n' "$(value STAGING_PHASE_B_PROVISIONING_ENABLED)"
printf '    APP_PRINTING_ALLOWED_MODES: "%s"\n' "$(value STAGING_ALLOWED_PRINTING_MODES)"
printf '    APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED: "%s"\n' "$(value STAGING_PRINTER_ENDPOINT_CONFIGURATION_ENABLED)"
printf '    cpus: %s\n' "$(normalized_cpu "$(value STAGING_BACKEND_CPU_LIMIT)")"
printf '    mem_limit: "%s"\n' "$(memory_bytes "$(value STAGING_BACKEND_MEMORY_LIMIT)")"
printf '    max-size: %s\n' "$(value STAGING_LOG_MAX_SIZE)"
printf '    max-file: "%s"\n' "$(value STAGING_LOG_MAX_FILE)"
printf '  nginx:\n    image: %s\n' "$(value FRONTEND_IMAGE)"
printf '    VITE_APP_BUILD_VERSION: %s\n' "$(value VITE_APP_BUILD_VERSION)"
printf '    NGINX_SERVER_NAME: %s\n' "$(value NGINX_SERVER_NAME)"
printf '    ports:\n      - 127.0.0.1:18080:80\n'
printf '    cpus: %s\n' "$(normalized_cpu "$(value STAGING_NGINX_CPU_LIMIT)")"
printf '    mem_limit: "%s"\n' "$(memory_bytes "$(value STAGING_NGINX_MEMORY_LIMIT)")"
printf '    max-size: %s\n' "$(value STAGING_LOG_MAX_SIZE)"
printf '    max-file: "%s"\n' "$(value STAGING_LOG_MAX_FILE)"

if [[ "$(value DB_NAME)" == "restaurant_pos_staging_fake_postgres_swap" ]]; then
  postgres_path="$(value STAGING_POSTGRES_DATA_DIR)"
  mv "$postgres_path" "$postgres_path.real"
  ln -s "$postgres_path.real" "$postgres_path"
fi
DOCKER
chmod +x "$FAKE_BIN/docker"

SEED_RELEASE="$TMP_DIR/release-seed"
git clone --quiet "$REPOSITORY_ROOT" "$SEED_RELEASE"
SOURCE_HEAD="$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"
if ! git -C "$REPOSITORY_ROOT" diff --cached --quiet; then
  git -C "$REPOSITORY_ROOT" diff --cached --binary | git -C "$SEED_RELEASE" apply --index
  git -C "$SEED_RELEASE" -c user.name=staging-guard-test -c user.email=staging-guard-test@example.invalid \
    commit --quiet -m "test fixture staged candidate"
fi

SHA="$(git -C "$SEED_RELEASE" rev-parse HEAD)"
if git -C "$REPOSITORY_ROOT" diff --cached --quiet; then
  [[ "$SHA" == "$SOURCE_HEAD" ]] || fail "fixture must execute cloned committed HEAD when no staged changes exist"
else
  [[ "$SHA" != "$SOURCE_HEAD" ]] || fail "fixture did not include the staged candidate"
fi
FIXTURE_STAGING_ROOT="$TMP_DIR/restaurant-pos/staging"
RELEASE_DIR="$FIXTURE_STAGING_ROOT/releases/$SHA"
CONFIG_DIR="$FIXTURE_STAGING_ROOT/config"
POSTGRES_DIR="$FIXTURE_STAGING_ROOT/state/postgres"
mkdir -p "$(dirname "$RELEASE_DIR")" "$CONFIG_DIR" "$POSTGRES_DIR"
mkdir -p "$COMPOSE_TEMP_DIR"
mv "$SEED_RELEASE" "$RELEASE_DIR"

assert_contains 'postgres:16-alpine UID 70' "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"
assert_not_contains 'postgres UID 999' "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"
assert_contains 'build backend' "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"
assert_contains 'build nginx' "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"
assert_not_contains 'build backend nginx' "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"

ENV_FILE="$CONFIG_DIR/.env.staging"
CALL_LOG="$FAKE_BIN/docker.calls"
BASE_ENV="$TMP_DIR/base.env"
cat >"$BASE_ENV" <<EOF
COMPOSE_PROJECT_NAME=restaurant-pos-staging
STAGING_ROOT=$FIXTURE_STAGING_ROOT
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
JAVA_OPTS="-Xms128m -Xmx512m"
BACKEND_IMAGE=restaurant-pos-backend:staging-$SHA
FRONTEND_IMAGE=restaurant-pos-frontend:staging-$SHA
VITE_APP_BUILD_VERSION=staging-$SHA
STAGING_PRINT_MODE=DISABLED
STAGING_PRINTING_FEATURE_ENABLED=false
STAGING_ALLOWED_PRINTING_MODES=DISABLED,MOCK
STAGING_PRINTER_ENDPOINT_CONFIGURATION_ENABLED=false
STAGING_PLATFORM_FEATURE_ENABLED=true
STAGING_PHASE_B_PROVISIONING_ENABLED=true
STAGING_DB_CPU_LIMIT=0.75
STAGING_DB_MEMORY_LIMIT=512m
STAGING_BACKEND_CPU_LIMIT=1.00
STAGING_BACKEND_MEMORY_LIMIT=768m
STAGING_NGINX_CPU_LIMIT=0.25
STAGING_NGINX_MEMORY_LIMIT=128m
STAGING_LOG_MAX_SIZE=10m
STAGING_LOG_MAX_FILE=3
EOF

for key in \
  DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG DOCKER_CERT_PATH DOCKER_TLS_VERIFY \
  DOCKER_API_VERSION DOCKER_DEFAULT_PLATFORM DOCKER_BUILDKIT \
  COMPOSE_FILE COMPOSE_PROJECT_NAME COMPOSE_ENV_FILES COMPOSE_PATH_SEPARATOR COMPOSE_PROFILES \
  STAGING_ROOT STAGING_COMMIT_SHA STAGING_POSTGRES_DATA_DIR \
  HTTP_BIND_ADDRESS HTTP_PORT NGINX_SERVER_NAME TZ POSTGRES_IMAGE_TAG \
  DB_NAME DB_USER DB_PASSWORD JWT_SECRET SPRING_PROFILES_ACTIVE JAVA_OPTS \
  BACKEND_IMAGE FRONTEND_IMAGE VITE_APP_BUILD_VERSION \
  STAGING_PRINT_MODE STAGING_PRINTING_FEATURE_ENABLED STAGING_ALLOWED_PRINTING_MODES \
  STAGING_PRINTER_ENDPOINT_CONFIGURATION_ENABLED STAGING_PRINTER_ENDPOINT \
  STAGING_PLATFORM_FEATURE_ENABLED STAGING_PHASE_B_PROVISIONING_ENABLED \
  STAGING_DB_CPU_LIMIT STAGING_DB_MEMORY_LIMIT STAGING_BACKEND_CPU_LIMIT \
  STAGING_BACKEND_MEMORY_LIMIT STAGING_NGINX_CPU_LIMIT STAGING_NGINX_MEMORY_LIMIT \
  STAGING_LOG_MAX_SIZE STAGING_LOG_MAX_FILE APP_FEATURES_PRINTING \
  APP_FEATURES_PLATFORM APP_PHASE_B_PROVISIONING_ENABLED \
  APP_PRINTING_ALLOWED_MODES APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED \
  APP_AUTH_X_USER_ID_FALLBACK_ENABLED \
  APP_DEV_TOOLS_ROLE_SWITCHER_ENABLED APP_SEED_DEFAULT_USERS_ENABLED APP_SEED_DEMO_DATA_ENABLED; do
  unset "$key" || true
done

run_validate() {
  TMPDIR="$COMPOSE_TEMP_DIR" PATH="$FAKE_BIN:$PATH" \
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
assert_contains "ambient DB_NAME=unset DOCKER_HOST=unset DOCKER_CONTEXT=unset COMPOSE_FILE=unset" "$CALL_LOG"
assert_contains "compose_plugin=available context=default" "$CALL_LOG"
assert_contains "cli_state_root=$COMPOSE_TEMP_DIR/restaurant-pos-staging-docker-cli." "$CALL_LOG"
assert_contains "modes=700,700,700" "$CALL_LOG"
assert_not_contains "/nonexistent" "$CALL_LOG"
assert_not_contains "$ENV_FILE" "$CALL_LOG"
assert_empty_directory "$COMPOSE_TEMP_DIR"
if grep -Eq '( build | up )' "$CALL_LOG"; then
  fail "validate invoked build or up"
fi

reset_env
run_validate >"$TMP_DIR/repeated.out" 2>"$TMP_DIR/repeated.err"
assert_contains "Staging validation passed" "$TMP_DIR/repeated.out"
assert_empty_directory "$COMPOSE_TEMP_DIR"

reset_env
set_env DB_NAME restaurant_pos_staging_fake_config_failure "$ENV_FILE"
expect_failure resolved_config_failure run_validate
assert_empty_directory "$COMPOSE_TEMP_DIR"

reset_env
set_env DB_NAME restaurant_pos_staging_fake_postgres_swap "$ENV_FILE"
expect_failure postgres_path_swap_after_config run_validate
assert_empty_directory "$COMPOSE_TEMP_DIR"
rm "$POSTGRES_DIR"
mv "$POSTGRES_DIR.real" "$POSTGRES_DIR"

reset_env
chmod 600 "$ENV_FILE"
chmod 700 "$FIXTURE_STAGING_ROOT" "$FIXTURE_STAGING_ROOT/state" "$POSTGRES_DIR"
HARNESS_LOG="$TMP_DIR/serial_build_success_harness.calls"
: >"$HARNESS_LOG"
if ! (
  # Source only function definitions. The guarded main does not run when sourced.
  source "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"
  ACTIVE_ENV_FILE="$ENV_FILE"
  ENV_SNAPSHOT="$ENV_FILE"
  ENV_SNAPSHOT_DIGEST="$(file_digest "$ENV_FILE")"
  STAGING_ROOT="$FIXTURE_STAGING_ROOT"
  STAGING_COMMIT_SHA="$SHA"
  POSTGRES_DATA_DIR="$POSTGRES_DIR"
  COMPOSE_PROJECT_NAME="restaurant-pos-staging"
  LOCAL_VALIDATE_MODE=false
  assert_clean_release() { :; }
  assert_resolved_compose() { :; }
  controlled_compose() {
    local active_env_file="$1"
    shift
    printf '%s\n' "$*" >>"$HARNESS_LOG"
  }
  run_deploy_sequence
) >"$TMP_DIR/serial_build_success_harness.out" 2>"$TMP_DIR/serial_build_success_harness.err"; then
  cat "$TMP_DIR/serial_build_success_harness.err" >&2
  fail "serial_build_success_harness failed"
fi
assert_contains "build backend" "$HARNESS_LOG"
assert_contains "build nginx" "$HARNESS_LOG"
assert_contains "up -d" "$HARNESS_LOG"
assert_not_contains "build backend nginx" "$HARNESS_LOG"
assert_line_order "build backend" "build nginx" "$HARNESS_LOG"
assert_line_order "build nginx" "up -d" "$HARNESS_LOG"

HARNESS_LOG="$TMP_DIR/backend_build_failure_harness.calls"
: >"$HARNESS_LOG"
if (
  # Source only function definitions. The guarded main does not run when sourced.
  source "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"
  ACTIVE_ENV_FILE="$ENV_FILE"
  ENV_SNAPSHOT="$ENV_FILE"
  ENV_SNAPSHOT_DIGEST="$(file_digest "$ENV_FILE")"
  STAGING_ROOT="$FIXTURE_STAGING_ROOT"
  STAGING_COMMIT_SHA="$SHA"
  POSTGRES_DATA_DIR="$POSTGRES_DIR"
  COMPOSE_PROJECT_NAME="restaurant-pos-staging"
  LOCAL_VALIDATE_MODE=false
  assert_clean_release() { :; }
  assert_resolved_compose() { :; }
  controlled_compose() {
    local active_env_file="$1"
    shift
    printf '%s\n' "$*" >>"$HARNESS_LOG"
    [[ "$*" != "build backend" ]]
  }
  run_deploy_sequence
) >"$TMP_DIR/backend_build_failure_harness.out" 2>"$TMP_DIR/backend_build_failure_harness.err"; then
  fail "backend_build_failure_harness unexpectedly passed"
fi
assert_contains "isolated staging backend image build failed" "$TMP_DIR/backend_build_failure_harness.err"
assert_contains "build backend" "$HARNESS_LOG"
assert_not_contains "build nginx" "$HARNESS_LOG"
assert_not_contains "build backend nginx" "$HARNESS_LOG"
assert_not_contains "up -d" "$HARNESS_LOG"

HARNESS_LOG="$TMP_DIR/post_build_swap_harness.calls"
: >"$HARNESS_LOG"
if (
  # Source only function definitions. The guarded main does not run when sourced.
  source "$RELEASE_DIR/deployment/cloud/staging-deploy.sh"
  ACTIVE_ENV_FILE="$ENV_FILE"
  ENV_SNAPSHOT="$ENV_FILE"
  ENV_SNAPSHOT_DIGEST="$(file_digest "$ENV_FILE")"
  STAGING_ROOT="$FIXTURE_STAGING_ROOT"
  STAGING_COMMIT_SHA="$SHA"
  POSTGRES_DATA_DIR="$POSTGRES_DIR"
  LOCAL_VALIDATE_MODE=false
  assert_clean_release() { :; }
  assert_resolved_compose() { :; }
  controlled_compose() {
    local active_env_file="$1"
    shift
    case "$1" in
      build)
        printf 'build %s\n' "$*" >>"$HARNESS_LOG"
        mv "$POSTGRES_DIR" "$POSTGRES_DIR.real"
        ln -s "$POSTGRES_DIR.real" "$POSTGRES_DIR"
        ;;
      up)
        printf 'up %s\n' "$*" >>"$HARNESS_LOG"
        ;;
      *)
        printf 'unexpected compose action: %s\n' "$1" >&2
        return 97
        ;;
    esac
  }
  run_deploy_sequence
) >"$TMP_DIR/post_build_swap_harness.out" 2>"$TMP_DIR/post_build_swap_harness.err"; then
  fail "post_build_swap_harness unexpectedly passed"
fi
assert_contains "staging guard:" "$TMP_DIR/post_build_swap_harness.err"
assert_contains "build backend" "$HARNESS_LOG"
assert_not_contains "build nginx" "$HARNESS_LOG"
assert_not_contains "build backend nginx" "$HARNESS_LOG"
assert_not_contains "up -d" "$HARNESS_LOG"
rm "$POSTGRES_DIR"
mv "$POSTGRES_DIR.real" "$POSTGRES_DIR"

reset_env
expect_failure arbitrary_server_root \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --validate

reset_env
expect_failure ambient_db env DB_NAME=caller-value PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate

reset_env
expect_failure ambient_printing_feature env APP_FEATURES_PRINTING=true PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate

reset_env
expect_failure ambient_platform_feature env APP_FEATURES_PLATFORM=false PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate

reset_env
expect_failure ambient_phase_b_gate env APP_PHASE_B_PROVISIONING_ENABLED=false PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate

reset_env
expect_failure ambient_docker_host env DOCKER_HOST=tcp://forbidden.invalid:2375 PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate

reset_env
expect_failure ambient_docker_context env DOCKER_CONTEXT=forbidden-context PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate

reset_env
expect_failure ambient_compose_profiles env COMPOSE_PROFILES=forbidden-profile PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate

reset_env
expect_failure ambient_compose_file env COMPOSE_FILE=forbidden.yml PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-deploy.sh" --env-file "$ENV_FILE" --local-validate

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
set_env_literal NGINX_SERVER_NAME '"localhost; injected"' "$ENV_FILE"
expect_failure nginx_server_name_injection run_validate

reset_env
set_env STAGING_POSTGRES_DATA_DIR relative/postgres "$ENV_FILE"
expect_failure relative_data_path run_validate

reset_env
set_env STAGING_POSTGRES_DATA_DIR "$FIXTURE_STAGING_ROOT/state/../postgres" "$ENV_FILE"
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
run_validate >"$TMP_DIR/local_mock.out"
assert_contains "Staging validation passed" "$TMP_DIR/local_mock.out"

reset_env
set_env STAGING_ALLOWED_PRINTING_MODES DISABLED,MOCK,REAL "$ENV_FILE"
expect_failure unsafe_runtime_mode_allowlist run_validate

reset_env
set_env STAGING_PRINTER_ENDPOINT_CONFIGURATION_ENABLED true "$ENV_FILE"
expect_failure endpoint_configuration_enabled run_validate

reset_env
set_env STAGING_PRINTER_ENDPOINT PRINTER_ENDPOINT_FORBIDDEN "$ENV_FILE"
expect_failure printer_endpoint run_validate

reset_env
set_env STAGING_PLATFORM_FEATURE_ENABLED false "$ENV_FILE"
expect_failure platform_gate_disabled run_validate

reset_env
set_env STAGING_PHASE_B_PROVISIONING_ENABLED false "$ENV_FILE"
expect_failure phase_b_gate_disabled run_validate

reset_env
set_env SPRING_PROFILES_ACTIVE dev "$ENV_FILE"
expect_failure profile run_validate

reset_env
set_env APP_SEED_DEMO_DATA_ENABLED true "$ENV_FILE"
expect_failure unsafe_seed run_validate

reset_env
printf '\nDB_NAME=restaurant_pos_staging\n' >>"$ENV_FILE"
expect_failure duplicate_key run_validate

reset_env
set_env DB_NAME 'restaurant_pos_staging # inline' "$ENV_FILE"
expect_failure inline_comment run_validate

reset_env
set_env DB_NAME '"restaurant_pos_staging' "$ENV_FILE"
expect_failure ambiguous_quote run_validate

reset_env
set_env_literal DB_PASSWORD '"staging-db-value-12345\escaped"' "$ENV_FILE"
expect_failure escaped_secret run_validate

reset_env
set_env STAGING_BACKEND_CPU_LIMIT 1.01 "$ENV_FILE"
expect_failure excessive_cpu run_validate

reset_env
set_env STAGING_DB_CPU_LIMIT 0 "$ENV_FILE"
expect_failure zero_db_cpu run_validate

reset_env
set_env STAGING_BACKEND_CPU_LIMIT 0 "$ENV_FILE"
expect_failure zero_backend_cpu run_validate

reset_env
set_env STAGING_NGINX_CPU_LIMIT 0 "$ENV_FILE"
expect_failure zero_nginx_cpu run_validate

reset_env
set_env STAGING_BACKEND_MEMORY_LIMIT 769m "$ENV_FILE"
expect_failure excessive_memory run_validate

reset_env
set_env JAVA_OPTS '"-Xms128m -Xmx513m"' "$ENV_FILE"
expect_failure excessive_jvm_heap run_validate

reset_env
set_env JAVA_OPTS '"-Xms512m -Xmx128m"' "$ENV_FILE"
expect_failure inverted_jvm_heap run_validate

reset_env
set_env JAVA_OPTS '"-Xms128m -Xmx512m -Xmx513m"' "$ENV_FILE"
expect_failure duplicate_late_jvm_heap run_validate

reset_env
set_env JAVA_OPTS '"-Xms128m -Xmx512m -Dunsafe=true"' "$ENV_FILE"
expect_failure injected_jvm_flag run_validate

reset_env
set_env STAGING_LOG_MAX_SIZE 11m "$ENV_FILE"
expect_failure excessive_log_size run_validate

reset_env
set_env STAGING_LOG_MAX_FILE 4 "$ENV_FILE"
expect_failure excessive_log_count run_validate

reset_env
printf '\n# tracked dirty fixture\n' >>"$RELEASE_DIR/AGENTS.md"
expect_failure tracked_dirty run_validate
git -C "$RELEASE_DIR" checkout -- AGENTS.md

reset_env
touch "$RELEASE_DIR/untracked-build-input.txt"
expect_failure untracked_build_input run_validate
rm "$RELEASE_DIR/untracked-build-input.txt"

reset_env
printf 'ignored frontend input\n' >"$RELEASE_DIR/frontend/.env.production"
git -C "$RELEASE_DIR" check-ignore -q frontend/.env.production || fail "frontend ignored build-input fixture is not ignored"
expect_failure ignored_frontend_build_input run_validate
rm "$RELEASE_DIR/frontend/.env.production"

reset_env
printf 'ignored backend input\n' >"$RELEASE_DIR/backend/src/main/resources/application-local.yml"
git -C "$RELEASE_DIR" check-ignore -q backend/src/main/resources/application-local.yml || fail "backend ignored build-input fixture is not ignored"
expect_failure ignored_backend_build_input run_validate
rm "$RELEASE_DIR/backend/src/main/resources/application-local.yml"

reset_env
rm -rf "$POSTGRES_DIR"
mkdir -p "$TMP_DIR/outside-postgres"
ln -s "$TMP_DIR/outside-postgres" "$POSTGRES_DIR"
expect_failure symlink_data_path run_validate
rm "$POSTGRES_DIR"
mkdir -p "$POSTGRES_DIR"

reset_env
PATH="$FAKE_BIN:$PATH" \
  "$RELEASE_DIR/deployment/cloud/staging-health-check.sh" --env-file "$ENV_FILE" --local-validate >"$TMP_DIR/health.out"
assert_contains "configuration passed" "$TMP_DIR/health.out"

echo "PASS: staging guard rejects ambient overrides, unsafe dotenv input, dirty Git inputs, unsafe printing, and unsafe resource/network/path configuration."
