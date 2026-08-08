#!/usr/bin/env bash
set -Eeuo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
SCRIPT="$REPO_ROOT/deployment/cloud/staging-release-rotation.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-ops001-release-test.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"
trap '[[ "${BASH_SUBSHELL:-0}" -ne 0 ]] || rm -rf "$TMP_DIR"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
expect_failure() { local label="$1"; shift; if (trap - EXIT; "$@") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then fail "$label unexpectedly passed"; fi; }
assert_contains() { grep -Fq -- "$1" "$2" || { sed 's/^/TEST-DIAGNOSTIC: /' "$2" >&2; fail "missing '$1' in $2"; }; }

bash -n "$SCRIPT"
"$SCRIPT" --help >"$TMP_DIR/help"
assert_contains 'Only STAGING_COMMIT_SHA, BACKEND_IMAGE' "$TMP_DIR/help"
expect_failure missing_sha "$SCRIPT" --validate --env-file /does/not/exist
assert_contains 'approved SHA and environment file are required' "$TMP_DIR/missing_sha.err"
expect_failure mutation_without_gate "$SCRIPT" --action prepare-release-env --approved-sha 0123456789abcdef0123456789abcdef01234567 --env-file /does/not/exist

# shellcheck source=../staging-release-rotation.sh
source "$SCRIPT"
OPS001_EXPECTED_ROOT="$TMP_DIR/staging"
OPS001_EXPECTED_ENVIRONMENT="restaurant-pos-staging"
EXPECTED_ROOT="$OPS001_EXPECTED_ROOT"
mkdir -p "$OPS001_EXPECTED_ROOT"/{config,evidence,releases,state}
chmod 700 "$OPS001_EXPECTED_ROOT"/{config,evidence,releases,state}
REPOSITORY="$OPS001_EXPECTED_ROOT/repository.git"

FAKE_FLOCK="$TMP_DIR/flock"
cat >"$FAKE_FLOCK" <<'EOF'
#!/usr/bin/env bash
[[ "${1:-}" == '-n' || "${1:-}" == '-u' ]]
EOF
chmod +x "$FAKE_FLOCK"
FLOCK_BIN="$FAKE_FLOCK"

SOURCE_REPO="$TMP_DIR/source"
git init -q "$SOURCE_REPO"
git -C "$SOURCE_REPO" config user.email ops001@example.invalid
git -C "$SOURCE_REPO" config user.name OPS001
printf 'release fixture\n' >"$SOURCE_REPO/fixture.txt"
mkdir -p "$SOURCE_REPO/deployment/cloud"
cat >"$SOURCE_REPO/deployment/cloud/staging-deploy.sh" <<EOF
#!/usr/bin/env bash
[[ ! -e "$TMP_DIR/fail-validator" ]]
EOF
chmod +x "$SOURCE_REPO/deployment/cloud/staging-deploy.sh"
git -C "$SOURCE_REPO" add fixture.txt deployment/cloud/staging-deploy.sh
git -C "$SOURCE_REPO" commit -qm fixture
APPROVED_SHA="$(git -C "$SOURCE_REPO" rev-parse HEAD)"
git clone -q --bare "$SOURCE_REPO" "$REPOSITORY"
chmod 700 "$REPOSITORY"
git --git-dir="$REPOSITORY" update-ref refs/remotes/origin/main "$APPROVED_SHA"

OLD_SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
ENV_FILE="$OPS001_EXPECTED_ROOT/config/.env.staging"
cat >"$ENV_FILE" <<EOF
COMPOSE_PROJECT_NAME=restaurant-pos-staging
STAGING_ROOT=$OPS001_EXPECTED_ROOT
STAGING_COMMIT_SHA=$OLD_SHA
HTTP_BIND_ADDRESS=127.0.0.1
HTTP_PORT=18080
STAGING_PRINT_MODE=DISABLED
STAGING_PRINTING_FEATURE_ENABLED=false
BACKEND_IMAGE=restaurant-pos-backend:staging-$OLD_SHA
FRONTEND_IMAGE=restaurant-pos-frontend:staging-$OLD_SHA
VITE_APP_BUILD_VERSION=staging-$OLD_SHA
DB_PASSWORD=fixture-private-value
JWT_SECRET=fixture-private-long-value-not-a-real-secret
EOF
chmod 600 "$ENV_FILE"
ENV_DIGEST="$(ops001_file_digest "$ENV_FILE")"
RELEASE_DIR="$OPS001_EXPECTED_ROOT/releases/$APPROVED_SHA"
ACTION=prepare-release-env
EXECUTE_RUNTIME=true
SCOPE="repository=$REPOSITORY;release=$RELEASE_DIR;identity_fields=4"
APPROVAL_FILE="$OPS001_EXPECTED_ROOT/evidence/approval.txt"
NOW="$(date +%s)"
cat >"$APPROVAL_FILE" <<EOF
OPS001_APPROVAL|STATUS|OWNER_APPROVED
OPS001_APPROVAL|ENVIRONMENT|restaurant-pos-staging
OPS001_APPROVAL|EXPIRES_AT_EPOCH|$((NOW + 600))
OPS001_APPROVAL|ACTION|$ACTION
OPS001_APPROVAL|APPROVED_SHA|$APPROVED_SHA
OPS001_APPROVAL|ENV_SHA256|$ENV_DIGEST
OPS001_APPROVAL|REQUEST_FINGERPRINT|$(ops001_request_fingerprint "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$SCOPE")
OPS001_APPROVAL|REFERENCE|owner/OPS-001-test
EOF
chmod 600 "$APPROVAL_FILE"
APPROVAL_SHA256="$(ops001_file_digest "$APPROVAL_FILE")"

validate_inputs
RECOVERY_REDIRECT="$TMP_DIR/recovery-redirect"
mkdir "$RECOVERY_REDIRECT"; chmod 700 "$RECOVERY_REDIRECT"
ln -s "$RECOVERY_REDIRECT" "$OPS001_EXPECTED_ROOT/state/ops001-env-recovery"
expect_failure recovery_symlink rotate_environment
assert_contains 'environment recovery directory must be a real directory' "$TMP_DIR/recovery_symlink.err"
[[ -z "$(find "$RECOVERY_REDIRECT" -mindepth 1 -print -quit)" ]] || fail 'recovery symlink redirected a secret snapshot'
unlink "$OPS001_EXPECTED_ROOT/state/ops001-env-recovery"
run_prepare >"$TMP_DIR/prepare.out"
assert_contains 'OPS001_RELEASE_ENV|PASS' "$TMP_DIR/prepare.out"
assert_release
[[ "$(ops001_file_mode "$RELEASE_DIR")" == 700 ]] || fail 'release mode is not 0700'
[[ "$(ops001_env_value "$ENV_FILE" STAGING_COMMIT_SHA)" == "$APPROVED_SHA" ]] || fail 'environment SHA not rotated'
[[ "$(ops001_env_value "$ENV_FILE" DB_PASSWORD)" == fixture-private-value ]] || fail 'secret field changed'
[[ "$(ops001_env_value "$ENV_FILE" JWT_SECRET)" == fixture-private-long-value-not-a-real-secret ]] || fail 'JWT field changed'
[[ -n "$(find "$OPS001_EXPECTED_ROOT/state/ops001-env-recovery" -name '*.env.*' -print -quit)" ]] || fail 'private recovery snapshot missing'

PRIOR_ENV_DIGEST="$(ops001_file_digest "$ENV_FILE")"
ENV_DIGEST="$PRIOR_ENV_DIGEST"
touch "$TMP_DIR/fail-validator"
OPS001_APPROVAL_FILE="$APPROVAL_FILE"
OPS001_VALIDATED_APPROVAL_SHA256="$(ops001_file_digest "$APPROVAL_FILE")"
ROTATION_STATE=NONE
expect_failure validator_recovery rotate_environment
assert_contains 'official Staging validation failed; prior environment restored' "$TMP_DIR/validator_recovery.err"
[[ "$(ops001_file_digest "$ENV_FILE")" == "$PRIOR_ENV_DIGEST" ]] || fail 'validator failure did not restore prior environment'
rg -l 'RESTORED_AFTER_VALIDATION_FAILURE' "$OPS001_EXPECTED_ROOT/state/ops001-env-recovery"/*.record >/dev/null || fail 'validator recovery status was not retained'
rm -f "$TMP_DIR/fail-validator"

expect_failure approval_replay ops001_consume_approval
assert_contains 'already consumed' "$TMP_DIR/approval_replay.err"

mv "$OPS001_EXPECTED_ROOT/state/ops001-approvals" "$OPS001_EXPECTED_ROOT/state/ops001-approvals-real"
ln -s ops001-approvals-real "$OPS001_EXPECTED_ROOT/state/ops001-approvals"
OPS001_VALIDATED_APPROVAL_SHA256=dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd
expect_failure approval_symlink ops001_consume_approval
assert_contains 'real directory' "$TMP_DIR/approval_symlink.err"
unlink "$OPS001_EXPECTED_ROOT/state/ops001-approvals"
mv "$OPS001_EXPECTED_ROOT/state/ops001-approvals-real" "$OPS001_EXPECTED_ROOT/state/ops001-approvals"

# Wrong SHA/action/environment/private-permission and non-identity drift fail closed.
ORIGINAL_APPROVAL="$TMP_DIR/approval.original"; cp "$APPROVAL_FILE" "$ORIGINAL_APPROVAL"
sed "s/OPS001_APPROVAL|APPROVED_SHA|$APPROVED_SHA/OPS001_APPROVAL|APPROVED_SHA|bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/" "$ORIGINAL_APPROVAL" >"$APPROVAL_FILE"; chmod 600 "$APPROVAL_FILE"
expect_failure wrong_sha ops001_validate_approval "$APPROVAL_FILE" "$(ops001_file_digest "$APPROVAL_FILE")" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$SCOPE"
assert_contains 'action/SHA/environment binding mismatch' "$TMP_DIR/wrong_sha.err"
cp "$ORIGINAL_APPROVAL" "$APPROVAL_FILE"; chmod 644 "$APPROVAL_FILE"
expect_failure approval_permissions ops001_validate_approval "$APPROVAL_FILE" "$(ops001_file_digest "$APPROVAL_FILE")" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$SCOPE"
assert_contains 'mode 0600' "$TMP_DIR/approval_permissions.err"
chmod 600 "$APPROVAL_FILE"
sed "s/OPS001_APPROVAL|EXPIRES_AT_EPOCH|[0-9]*/OPS001_APPROVAL|EXPIRES_AT_EPOCH|$((NOW - 1))/" "$ORIGINAL_APPROVAL" >"$APPROVAL_FILE"; chmod 600 "$APPROVAL_FILE"
expect_failure expired_approval ops001_validate_approval "$APPROVAL_FILE" "$(ops001_file_digest "$APPROVAL_FILE")" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$SCOPE"
assert_contains 'expired or exceeds 24 hours' "$TMP_DIR/expired_approval.err"
sed 's/OPS001_APPROVAL|ENVIRONMENT|restaurant-pos-staging/OPS001_APPROVAL|ENVIRONMENT|production/' "$ORIGINAL_APPROVAL" >"$APPROVAL_FILE"; chmod 600 "$APPROVAL_FILE"
expect_failure wrong_environment ops001_validate_approval "$APPROVAL_FILE" "$(ops001_file_digest "$APPROVAL_FILE")" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$SCOPE"
assert_contains 'status/environment mismatch' "$TMP_DIR/wrong_environment.err"
expect_failure missing_approval ops001_validate_approval /does/not/exist aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$SCOPE"
assert_contains 'absolute regular non-symlink file' "$TMP_DIR/missing_approval.err"
cp "$ORIGINAL_APPROVAL" "$APPROVAL_FILE"; chmod 600 "$APPROVAL_FILE"

OLD_COPY="$TMP_DIR/old.env"; NEW_COPY="$TMP_DIR/new.env"
cp "$ENV_FILE" "$OLD_COPY"; cp "$ENV_FILE" "$NEW_COPY"; sed -i.bak 's/DB_PASSWORD=fixture-private-value/DB_PASSWORD=changed/' "$NEW_COPY"; rm "$NEW_COPY.bak"
expect_failure non_identity_drift assert_only_identity_changed "$OLD_COPY" "$NEW_COPY"
assert_contains 'non-identity environment field changed: DB_PASSWORD' "$TMP_DIR/non_identity_drift.err"

! grep -Eq '(git clone|git fetch|docker|flyway|ssh )' "$SCRIPT" || fail 'release helper contains forbidden runtime/network command'
grep -Fq 'acquire_action_lock' "$SCRIPT" || fail 'release/env action does not use the shared Staging action lock'
echo 'PASS: OPS-001 detached release and four-field atomic environment rotation fail closed.'
