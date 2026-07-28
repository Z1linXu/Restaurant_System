#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
TMP_DIR_RAW="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-stg006.XXXXXX")"
TMP_DIR="$(cd -P -- "$TMP_DIR_RAW" && pwd)"
FAKE_BIN="$TMP_DIR/fake-bin"
ROOT="$TMP_DIR/restaurant-pos/staging"
SECRET_VALUE="A9f4k2m7q8r5t3v6x1z0"
JWT_VALUE="B8n5p2s9w4y7c1d6g3h0j8k5m2q9r4t7"

cleanup() {
  if [[ "${KEEP_STG006_TEST_TMP:-false}" == "true" ]]; then
    printf 'KEPT_STG006_TEST_TMP=%s\n' "$TMP_DIR" >&2
  else
    rm -rf "$TMP_DIR"
  fi
}
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

mkdir -p "$FAKE_BIN" "$ROOT/releases" "$ROOT/config" "$ROOT/state/postgres" "$ROOT/backups"

# Build an exact disposable release checkout. Current uncommitted candidate
# files are copied into the fixture and committed so the package validator can
# prove the same Git identity that the operations helper receives.
SEED_RELEASE="$TMP_DIR/release-seed"
git clone --quiet "$REPOSITORY_ROOT" "$SEED_RELEASE"
cp "$REPOSITORY_ROOT/deployment/cloud/staging-operations.sh" \
  "$SEED_RELEASE/deployment/cloud/staging-operations.sh"
cp "$REPOSITORY_ROOT/deployment/cloud/staging-backup-plan.sh" \
  "$SEED_RELEASE/deployment/cloud/staging-backup-plan.sh"
for script in staging-operations.sh staging-backup-plan.sh staging-deploy.sh; do
  sed "s|/srv/restaurant-pos/staging|$ROOT|g" \
    "$SEED_RELEASE/deployment/cloud/$script" >"$SEED_RELEASE/deployment/cloud/$script.next"
  mv "$SEED_RELEASE/deployment/cloud/$script.next" "$SEED_RELEASE/deployment/cloud/$script"
done
for script in staging-operations.sh staging-deploy.sh; do
  sed "s|SAFE_PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"|SAFE_PATH=\"$FAKE_BIN:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"|" \
    "$SEED_RELEASE/deployment/cloud/$script" >"$SEED_RELEASE/deployment/cloud/$script.next"
  mv "$SEED_RELEASE/deployment/cloud/$script.next" "$SEED_RELEASE/deployment/cloud/$script"
done
chmod +x "$SEED_RELEASE/deployment/cloud/staging-operations.sh" \
  "$SEED_RELEASE/deployment/cloud/staging-backup-plan.sh" \
  "$SEED_RELEASE/deployment/cloud/staging-deploy.sh"
git -C "$SEED_RELEASE" add deployment/cloud/staging-operations.sh \
  deployment/cloud/staging-backup-plan.sh deployment/cloud/staging-deploy.sh
git -C "$SEED_RELEASE" -c user.name=stg006-test -c user.email=stg006-test@example.invalid \
  commit --quiet -m "test fixture with guarded operations candidate"

SHA="$(git -C "$SEED_RELEASE" rev-parse HEAD)"
PREVIOUS_SHA="$(git -C "$SEED_RELEASE" rev-parse HEAD^)"
RELEASE="$ROOT/releases/$SHA"
PREVIOUS_RELEASE="$ROOT/releases/$PREVIOUS_SHA"
git clone --quiet "$SEED_RELEASE" "$PREVIOUS_RELEASE"
git -C "$PREVIOUS_RELEASE" checkout --quiet --detach "$PREVIOUS_SHA"
mv "$SEED_RELEASE" "$RELEASE"

ENV_FILE="$ROOT/config/.env.staging"
COMPOSE_FILE="$RELEASE/deployment/cloud/docker-compose.staging.yml"
BACKEND_IMAGE="restaurant-pos-backend:staging-$SHA"
FRONTEND_IMAGE="restaurant-pos-frontend:staging-$SHA"
RUNNER="$RELEASE/deployment/cloud/staging-operations.sh"
BACKUP_RUNNER="$RELEASE/deployment/cloud/staging-backup-plan.sh"
chmod 700 "$ROOT" "$ROOT/config" "$ROOT/state" "$ROOT/state/postgres"

cat >"$ENV_FILE" <<EOF
COMPOSE_PROJECT_NAME=restaurant-pos-staging
STAGING_ROOT=$ROOT
STAGING_COMMIT_SHA=$SHA
STAGING_POSTGRES_DATA_DIR=$ROOT/state/postgres
HTTP_BIND_ADDRESS=127.0.0.1
HTTP_PORT=18080
NGINX_SERVER_NAME=localhost
TZ=America/Toronto
POSTGRES_IMAGE_TAG=16-alpine
DB_NAME=restaurant_pos_staging_test
DB_USER=restaurant_pos_staging_test
DB_PASSWORD=$SECRET_VALUE
JWT_SECRET=$JWT_VALUE
SPRING_PROFILES_ACTIVE=cloud
JAVA_OPTS="-Xms128m -Xmx512m"
BACKEND_IMAGE=$BACKEND_IMAGE
FRONTEND_IMAGE=$FRONTEND_IMAGE
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

cat >"$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$(dirname "$0")/docker.calls"
if [[ "$1" == "context" && "$2" == "inspect" && "$3" == "default" ]]; then exit 0; fi
[[ "$1" == "--context" && "$2" == "default" ]] || exit 89
shift 2
if [[ "$1" == "inspect" && "$2" == "--format" ]]; then
  printf 'CONTAINER|name=/restaurant-pos-staging-backend-1|id=stg-containe|created=synthetic|restart_count=0|status=running|health=healthy|image_id=sha256:synth\n'
  exit 0
fi
if [[ "$1" == "image" && "$2" == "inspect" && "$3" == "--format" ]]; then
  printf 'sha256:synthetic-image\n'
  exit 0
fi
[[ "$1" == "compose" ]] || exit 90
shift
action=""
env_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    config|ps) action="$1"; shift; break ;;
    --env-file) env_file="$2"; shift 2 ;;
    --project-name|-f) shift 2 ;;
    *) exit 91 ;;
  esac
done
case "$action" in
  config)
    [[ "${1:-}" == "--services" ]] && { printf 'db\nbackend\nnginx\n'; exit 0; }
    [[ "${1:-}" == "--images" ]] && {
      sed -n 's/^BACKEND_IMAGE=//p; s/^FRONTEND_IMAGE=//p' "$env_file"
      exit 0
    }
    value() { grep -E "^$1=" "$env_file" | tail -n 1 | sed 's/^[^=]*=//; s/^"//; s/"$//'; }
    cat <<CONFIG
services:
  db:
    image: postgres:$(value POSTGRES_IMAGE_TAG)
    source: $(value STAGING_POSTGRES_DATA_DIR)
    cpus: $(value STAGING_DB_CPU_LIMIT)
    mem_limit: $(value STAGING_DB_MEMORY_LIMIT)
  backend:
    image: $(value BACKEND_IMAGE)
    SPRING_PROFILES_ACTIVE: $(value SPRING_PROFILES_ACTIVE)
    DB_NAME: $(value DB_NAME)
    DB_USER: $(value DB_USER)
    APP_FEATURES_PRINTING: "$(value STAGING_PRINTING_FEATURE_ENABLED)"
    cpus: $(value STAGING_BACKEND_CPU_LIMIT)
    mem_limit: $(value STAGING_BACKEND_MEMORY_LIMIT)
  nginx:
    image: $(value FRONTEND_IMAGE)
    VITE_APP_BUILD_VERSION: $(value VITE_APP_BUILD_VERSION)
    NGINX_SERVER_NAME: $(value NGINX_SERVER_NAME)
    ports:
      - 127.0.0.1:18080:80
    cpus: $(value STAGING_NGINX_CPU_LIMIT)
    mem_limit: $(value STAGING_NGINX_MEMORY_LIMIT)
    max-size: $(value STAGING_LOG_MAX_SIZE)
    max-file: "$(value STAGING_LOG_MAX_FILE)"
CONFIG
    exit 0
    ;;
  ps)
    [[ "${1:-}" == "-q" ]] && { printf 'stg-container-id\n'; exit 0; }
    exit 93
    ;;
esac
EOF
cat >"$FAKE_BIN/df" <<'EOF'
#!/usr/bin/env bash
printf 'Filesystem 1024-blocks Used Available Capacity Mounted on\n'
printf 'fakefs 1000000 100000 900000 10%% /tmp\n'
EOF
chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/df"

COMMON=(--env-file "$ENV_FILE" --root "$ROOT" --commit "$SHA" --compose-file "$COMPOSE_FILE" --backend-image "$BACKEND_IMAGE" --frontend-image "$FRONTEND_IMAGE")
FAKE_DOCKER_LOG="$FAKE_BIN/docker.calls"

"$RUNNER" --help >"$TMP_DIR/help.out"
[[ ! -e "$FAKE_DOCKER_LOG" ]] || fail "help must not call Docker"

if PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate "${COMMON[@]}" >"$TMP_DIR/validate.out" 2>"$TMP_DIR/validate.err"; then :; else cat "$TMP_DIR/validate.err" >&2; fail "valid read-only validation failed"; fi
assert_contains 'RESULT|VALIDATE|PASS' "$TMP_DIR/validate.out"
assert_not_contains "$SECRET_VALUE" "$TMP_DIR/validate.out"
assert_not_contains "$SECRET_VALUE" "$TMP_DIR/validate.err"
assert_not_contains "$JWT_VALUE" "$TMP_DIR/validate.out"
assert_not_contains "$JWT_VALUE" "$TMP_DIR/validate.err"
assert_contains '--context default compose --project-name restaurant-pos-staging' "$FAKE_DOCKER_LOG"
assert_contains 'config --services' "$FAKE_DOCKER_LOG"
assert_contains 'config --images' "$FAKE_DOCKER_LOG"
assert_not_contains 'up' "$FAKE_DOCKER_LOG"
assert_not_contains 'build' "$FAKE_DOCKER_LOG"
assert_not_contains 'pull' "$FAKE_DOCKER_LOG"

if PATH="/usr/bin:/bin" "$RUNNER" --validate "${COMMON[@]}" >"$TMP_DIR/no-docker.out" 2>"$TMP_DIR/no-docker.err"; then
  fail "validation unexpectedly passed without Docker"
fi
assert_contains 'exact release staging package validation failed' "$TMP_DIR/no-docker.err"

PATH="$FAKE_BIN:$PATH" "$RUNNER" --inventory "${COMMON[@]}" >"$TMP_DIR/inventory.out"
assert_contains 'CONTAINER|name=' "$TMP_DIR/inventory.out"
assert_not_contains "$SECRET_VALUE" "$TMP_DIR/inventory.out"

PATH="$FAKE_BIN:$PATH" "$RUNNER" --disk-check --min-free-bytes 1000 --max-used-percent 80 "${COMMON[@]}" >"$TMP_DIR/disk.out"
assert_contains 'RESULT|DISK_CHECK|PASS' "$TMP_DIR/disk.out"

if PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate --env-file "$ENV_FILE" --root /production/root --commit "$SHA" --compose-file "$COMPOSE_FILE" --backend-image "$BACKEND_IMAGE" --frontend-image "$FRONTEND_IMAGE" >"$TMP_DIR/prod.out" 2>"$TMP_DIR/prod.err"; then
  fail "production root unexpectedly accepted"
fi
assert_contains 'ERROR|' "$TMP_DIR/prod.err"

sed -i.bak 's/^STAGING_PRINT_MODE=DISABLED$/STAGING_PRINT_MODE=REAL/' "$ENV_FILE"
if PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate "${COMMON[@]}" >"$TMP_DIR/printing.out" 2>"$TMP_DIR/printing.err"; then
  fail "real printing unexpectedly accepted"
fi
assert_contains 'only STAGING_PRINT_MODE=DISABLED is allowed' "$TMP_DIR/printing.err"
rm -f "$ENV_FILE.bak"
reset_env

sed -i.bak 's/^HTTP_BIND_ADDRESS=127.0.0.1$/HTTP_BIND_ADDRESS=0.0.0.0/' "$ENV_FILE"
expect_failure public_bind env PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate "${COMMON[@]}"
assert_contains 'exact release staging package validation failed' "$TMP_DIR/public_bind.out"
rm -f "$ENV_FILE.bak"
reset_env

mkdir -p "$TMP_DIR/production-postgres"
sed -i.bak "s|^STAGING_POSTGRES_DATA_DIR=.*$|STAGING_POSTGRES_DATA_DIR=$TMP_DIR/production-postgres|" "$ENV_FILE"
expect_failure production_data env PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate "${COMMON[@]}"
assert_contains 'exact release staging package validation failed' "$TMP_DIR/production_data.out"
rm -f "$ENV_FILE.bak"
reset_env

printf '\n# unapproved compose mutation\n' >>"$COMPOSE_FILE"
expect_failure compose_tamper env PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate "${COMMON[@]}"
assert_contains 'exact release has tracked or staged changes' "$TMP_DIR/compose_tamper.out"
git -C "$RELEASE" show "HEAD:deployment/cloud/docker-compose.staging.yml" >"$COMPOSE_FILE"

VALIDATOR="$RELEASE/deployment/cloud/staging-deploy.sh"
MALICIOUS_MARKER="$TMP_DIR/malicious-validator-executed"
cat >"$VALIDATOR" <<EOF
#!/usr/bin/env bash
printf 'unsafe\n' >"$MALICIOUS_MARKER"
exit 0
EOF
chmod +x "$VALIDATOR"
expect_failure validator_tamper env PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate "${COMMON[@]}"
assert_contains 'exact release has tracked or staged changes' "$TMP_DIR/validator_tamper.out"
[[ ! -e "$MALICIOUS_MARKER" ]] || fail "tampered release validator executed before integrity verification"
git -C "$RELEASE" show "HEAD:deployment/cloud/staging-deploy.sh" >"$VALIDATOR"
chmod +x "$VALIDATOR"

expect_failure duplicate_action env PATH="$FAKE_BIN:$PATH" "$RUNNER" --validate --inventory "${COMMON[@]}"
assert_contains 'choose exactly one action' "$TMP_DIR/duplicate_action.out"

PATH="$FAKE_BIN:$PATH" "$RUNNER" --image-compatibility --previous-sha "$PREVIOUS_SHA" "${COMMON[@]}" >"$TMP_DIR/image.out"
assert_contains 'historical_migration_blobs_unchanged=true' "$TMP_DIR/image.out"
assert_contains 'STATIC_CHECK_ONLY_RUNTIME_PENDING' "$TMP_DIR/image.out"

BAD_SEED="$TMP_DIR/bad-migration-seed"
git clone --quiet "$RELEASE" "$BAD_SEED"
printf '\n-- synthetic forbidden historical rewrite\n' >>"$BAD_SEED/backend/src/main/resources/db/migration/V1__baseline_current_schema.sql"
git -C "$BAD_SEED" add backend/src/main/resources/db/migration/V1__baseline_current_schema.sql
git -C "$BAD_SEED" -c user.name=stg006-test -c user.email=stg006-test@example.invalid \
  commit --quiet -m "synthetic historical migration rewrite"
BAD_SHA="$(git -C "$BAD_SEED" rev-parse HEAD)"
BAD_RELEASE="$ROOT/releases/$BAD_SHA"
mv "$BAD_SEED" "$BAD_RELEASE"
sed -e "s/^STAGING_COMMIT_SHA=.*/STAGING_COMMIT_SHA=$BAD_SHA/" \
  -e "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=restaurant-pos-backend:staging-$BAD_SHA|" \
  -e "s|^FRONTEND_IMAGE=.*|FRONTEND_IMAGE=restaurant-pos-frontend:staging-$BAD_SHA|" \
  -e "s|^VITE_APP_BUILD_VERSION=.*|VITE_APP_BUILD_VERSION=staging-$BAD_SHA|" \
  "$BASE_ENV" >"$ENV_FILE"
chmod 600 "$ENV_FILE"
BAD_COMMON=(--env-file "$ENV_FILE" --root "$ROOT" --commit "$BAD_SHA" \
  --compose-file "$BAD_RELEASE/deployment/cloud/docker-compose.staging.yml" \
  --backend-image "restaurant-pos-backend:staging-$BAD_SHA" \
  --frontend-image "restaurant-pos-frontend:staging-$BAD_SHA")
expect_failure migration_rewrite env PATH="$FAKE_BIN:$PATH" \
  "$BAD_RELEASE/deployment/cloud/staging-operations.sh" --image-compatibility \
  --previous-sha "$PREVIOUS_SHA" "${BAD_COMMON[@]}"
assert_contains 'historical_migration_missing_or_changed=true' "$TMP_DIR/migration_rewrite.out"
reset_env

WEIRD_BACKUP_NAME=$'sensitive-name\nRESULT|FORGED|PASS.dump'
printf 'metadata only\n' >"$ROOT/backups/$WEIRD_BACKUP_NAME"
"$BACKUP_RUNNER" --dry-run --root "$ROOT" --backup-dir "$ROOT/backups" >"$TMP_DIR/backup-dry.out"
"$BACKUP_RUNNER" --inspect-existing --root "$ROOT" --backup-dir "$ROOT/backups" >"$TMP_DIR/backup-inspect.out"
assert_contains 'BACKUP_ACTION=NOT_EXECUTED' "$TMP_DIR/backup-dry.out"
assert_contains 'REHEARSAL_NOT_EXECUTED_WAITING_FOR_OWNER_APPROVAL' "$TMP_DIR/backup-inspect.out"
assert_contains 'basename_sha256=' "$TMP_DIR/backup-inspect.out"
assert_contains 'size_bytes=' "$TMP_DIR/backup-inspect.out"
assert_not_contains 'sensitive-name' "$TMP_DIR/backup-inspect.out"
assert_not_contains 'RESULT|FORGED' "$TMP_DIR/backup-inspect.out"
expect_failure backup_duplicate "$BACKUP_RUNNER" --dry-run --inspect-existing --root "$ROOT" --backup-dir "$ROOT/backups"
assert_contains 'choose exactly one action' "$TMP_DIR/backup_duplicate.out"

mv "$ROOT/backups" "$ROOT/backups.real"
ln -s "$ROOT/backups.real" "$ROOT/backups"
expect_failure backup_symlink "$BACKUP_RUNNER" --inspect-existing --root "$ROOT" --backup-dir "$ROOT/backups"
assert_contains 'must not traverse a symlink' "$TMP_DIR/backup_symlink.out"
rm "$ROOT/backups"
mv "$ROOT/backups.real" "$ROOT/backups"

# The runtime fake rejects every Docker command other than the explicitly
# allowed read-only forms above. The source must still request formatted inspect
# rather than an unfiltered container JSON document.
grep -Fq 'inspect --format' "$REPOSITORY_ROOT/deployment/cloud/staging-operations.sh" || fail "formatted Docker inspect is required"
assert_not_contains 'compose --project-name restaurant-pos-staging up' "$FAKE_DOCKER_LOG"
assert_not_contains 'compose --project-name restaurant-pos-staging build' "$FAKE_DOCKER_LOG"
assert_not_contains 'compose --project-name restaurant-pos-staging pull' "$FAKE_DOCKER_LOG"

echo "PASS: STG-006 operations and backup planning guards are read-only, project-scoped, and fail closed."
