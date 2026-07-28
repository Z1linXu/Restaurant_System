#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
TMP_DIR_RAW="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-stg006.XXXXXX")"
TMP_DIR="$(cd -P -- "$TMP_DIR_RAW" && pwd)"
FAKE_BIN="$TMP_DIR/fake-bin"
ROOT="$TMP_DIR/restaurant-pos/staging"
SHA="0123456789abcdef0123456789abcdef01234567"
PREVIOUS_SHA="89abcdef0123456789abcdef0123456789abcdef"
ENV_FILE="$ROOT/config/.env.staging"
COMPOSE_FILE="$ROOT/releases/$SHA/deployment/cloud/docker-compose.staging.yml"
BACKEND_IMAGE="restaurant-pos-backend:staging-$SHA"
FRONTEND_IMAGE="restaurant-pos-frontend:staging-$SHA"
SECRET_VALUE="synthetic-test-secret-must-not-leak"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }

mkdir -p "$FAKE_BIN" "$(dirname "$ENV_FILE")" "$(dirname "$COMPOSE_FILE")" \
  "$ROOT/releases/$PREVIOUS_SHA" "$ROOT/backups"
cp "$REPOSITORY_ROOT/deployment/cloud/docker-compose.staging.yml" "$COMPOSE_FILE"
cp "$REPOSITORY_ROOT/deployment/cloud/staging-operations.sh" "$TMP_DIR/staging-operations.sh"
cp "$REPOSITORY_ROOT/deployment/cloud/staging-backup-plan.sh" "$TMP_DIR/staging-backup-plan.sh"

# The production script has a fixed server root. Only a disposable test copy
# is rewritten, allowing the exact guard contract to run in a temporary tree.
sed -i.bak "s|EXPECTED_ROOT=\"/srv/restaurant-pos/staging\"|EXPECTED_ROOT=\"$ROOT\"|" "$TMP_DIR/staging-operations.sh"
sed -i.bak "s|EXPECTED_ROOT=\"/srv/restaurant-pos/staging\"|EXPECTED_ROOT=\"$ROOT\"|" "$TMP_DIR/staging-backup-plan.sh"
sed -i.bak "s|SAFE_PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"|SAFE_PATH=\"$FAKE_BIN:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"|" "$TMP_DIR/staging-operations.sh"
rm -f "$TMP_DIR"/*.bak
chmod +x "$TMP_DIR/staging-operations.sh" "$TMP_DIR/staging-backup-plan.sh"

cat >"$ENV_FILE" <<EOF
COMPOSE_PROJECT_NAME=restaurant-pos-staging
STAGING_COMMIT_SHA=$SHA
BACKEND_IMAGE=$BACKEND_IMAGE
FRONTEND_IMAGE=$FRONTEND_IMAGE
STAGING_PRINT_MODE=DISABLED
STAGING_PRINTING_FEATURE_ENABLED=false
DB_PASSWORD=$SECRET_VALUE
JWT_SECRET=$SECRET_VALUE
EOF
chmod 600 "$ENV_FILE"

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
    exit 92
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

"$TMP_DIR/staging-operations.sh" --help >"$TMP_DIR/help.out"
[[ ! -e "$FAKE_DOCKER_LOG" ]] || fail "help must not call Docker"

if PATH="$FAKE_BIN:$PATH" "$TMP_DIR/staging-operations.sh" --validate "${COMMON[@]}" >"$TMP_DIR/validate.out" 2>"$TMP_DIR/validate.err"; then :; else cat "$TMP_DIR/validate.err" >&2; fail "valid read-only validation failed"; fi
assert_contains 'RESULT|VALIDATE|PASS' "$TMP_DIR/validate.out"
assert_not_contains "$SECRET_VALUE" "$TMP_DIR/validate.out"
assert_not_contains "$SECRET_VALUE" "$TMP_DIR/validate.err"
assert_contains '--context default compose --project-name restaurant-pos-staging' "$FAKE_DOCKER_LOG"
assert_contains 'config --services' "$FAKE_DOCKER_LOG"
assert_contains 'config --images' "$FAKE_DOCKER_LOG"
assert_not_contains 'up' "$FAKE_DOCKER_LOG"
assert_not_contains 'build' "$FAKE_DOCKER_LOG"
assert_not_contains 'pull' "$FAKE_DOCKER_LOG"

if PATH="/usr/bin:/bin" "$TMP_DIR/staging-operations.sh" --validate "${COMMON[@]}" >"$TMP_DIR/no-docker.out" 2>"$TMP_DIR/no-docker.err"; then
  fail "validation unexpectedly passed without Docker"
fi
assert_contains 'RESULT|DOCKER_RUNTIME|PENDING' "$TMP_DIR/no-docker.err"

PATH="$FAKE_BIN:$PATH" "$TMP_DIR/staging-operations.sh" --inventory "${COMMON[@]}" >"$TMP_DIR/inventory.out"
assert_contains 'CONTAINER|name=' "$TMP_DIR/inventory.out"
assert_not_contains "$SECRET_VALUE" "$TMP_DIR/inventory.out"

PATH="$FAKE_BIN:$PATH" "$TMP_DIR/staging-operations.sh" --disk-check --min-free-bytes 1000 --max-used-percent 80 "${COMMON[@]}" >"$TMP_DIR/disk.out"
assert_contains 'RESULT|DISK_CHECK|PASS' "$TMP_DIR/disk.out"

if PATH="$FAKE_BIN:$PATH" "$TMP_DIR/staging-operations.sh" --validate --env-file "$ENV_FILE" --root /production/root --commit "$SHA" --compose-file "$COMPOSE_FILE" --backend-image "$BACKEND_IMAGE" --frontend-image "$FRONTEND_IMAGE" >"$TMP_DIR/prod.out" 2>"$TMP_DIR/prod.err"; then
  fail "production root unexpectedly accepted"
fi
assert_contains 'ERROR|' "$TMP_DIR/prod.err"

sed -i.bak 's/^STAGING_PRINT_MODE=DISABLED$/STAGING_PRINT_MODE=REAL/' "$ENV_FILE"
if PATH="$FAKE_BIN:$PATH" "$TMP_DIR/staging-operations.sh" --validate "${COMMON[@]}" >"$TMP_DIR/printing.out" 2>"$TMP_DIR/printing.err"; then
  fail "real printing unexpectedly accepted"
fi
assert_contains 'only STAGING_PRINT_MODE=DISABLED is allowed' "$TMP_DIR/printing.err"
rm -f "$ENV_FILE.bak"
sed -i.bak 's/^STAGING_PRINT_MODE=REAL$/STAGING_PRINT_MODE=DISABLED/' "$ENV_FILE"
rm -f "$ENV_FILE.bak"

printf 'metadata only\n' >"$ROOT/backups/synthetic-backup.dump"
"$TMP_DIR/staging-backup-plan.sh" --dry-run --root "$ROOT" --backup-dir "$ROOT/backups" >"$TMP_DIR/backup-dry.out"
"$TMP_DIR/staging-backup-plan.sh" --inspect-existing --root "$ROOT" --backup-dir "$ROOT/backups" >"$TMP_DIR/backup-inspect.out"
assert_contains 'BACKUP_ACTION=NOT_EXECUTED' "$TMP_DIR/backup-dry.out"
assert_contains 'REHEARSAL_NOT_EXECUTED_WAITING_FOR_OWNER_APPROVAL' "$TMP_DIR/backup-inspect.out"
assert_contains 'size_bytes=' "$TMP_DIR/backup-inspect.out"

# The runtime fake rejects every Docker command other than the explicitly
# allowed read-only forms above. The source must still request formatted inspect
# rather than an unfiltered container JSON document.
grep -Fq 'inspect --format' "$REPOSITORY_ROOT/deployment/cloud/staging-operations.sh" || fail "formatted Docker inspect is required"
assert_not_contains 'compose --project-name restaurant-pos-staging up' "$FAKE_DOCKER_LOG"
assert_not_contains 'compose --project-name restaurant-pos-staging build' "$FAKE_DOCKER_LOG"
assert_not_contains 'compose --project-name restaurant-pos-staging pull' "$FAKE_DOCKER_LOG"

echo "PASS: STG-006 operations and backup planning guards are read-only, project-scoped, and fail closed."
