#!/usr/bin/env bash
set -Eeuo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
SCRIPT="$REPO_ROOT/deployment/cloud/staging-owner-acceptance-client.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-ops001-api-test.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"
trap '[[ "${BASH_SUBSHELL:-0}" -ne 0 ]] || rm -rf "$TMP_DIR"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() { local label="$1"; shift; if (trap - EXIT; "$@") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then fail "$label unexpectedly passed"; fi; }

bash -n "$SCRIPT"
"$SCRIPT" --help >"$TMP_DIR/help"
assert_contains 'Secret values are forbidden in argv/environment/output.' "$TMP_DIR/help"
expect_failure missing_bindings "$SCRIPT" --validate
assert_contains 'approved SHA and environment file are required' "$TMP_DIR/missing_bindings.err"

# shellcheck source=../staging-owner-acceptance-client.sh
source "$SCRIPT"
OPS001_EXPECTED_ROOT="$TMP_DIR/staging"
EXPECTED_ROOT="$OPS001_EXPECTED_ROOT"
mkdir -p "$OPS001_EXPECTED_ROOT"/{config,evidence,releases,state}; chmod 700 "$OPS001_EXPECTED_ROOT"/{config,evidence,releases,state}
JQ_BIN="$(command -v jq)"
ORGANIZATION_ID=10
TARGET_STORE_ID=""
SOURCE_STORE_ID=1
PROFILE_CODE=CHINATOWN_MENU_2026_02_02
APPROVED_SHA=0123456789abcdef0123456789abcdef01234567

FAKE_CURL="$TMP_DIR/curl"
FAKE_STATE="$TMP_DIR/fake-state"
cat >"$FAKE_CURL" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
config=""; output=""; url=""
[[ "${1:-}" == '-q' ]] || exit 98
args="$*"
[[ "$args" != *'OwnerPassphrase'* && "$args" != *'StaffPassphrase'* && "$args" != *'idem-'* && "$args" != *'access-token-value'* ]] || exit 97
while [[ $# -gt 0 ]]; do
  case "$1" in --config) config="$2"; shift ;; --output) output="$2"; shift ;; http://*) url="$1" ;; esac
  shift
done
[[ "$(stat -f '%Lp' "$config" 2>/dev/null || stat -c '%a' "$config")" == 600 ]] || exit 96
case "$url" in
  */auth/login) body='{"success":true,"data":{"access_token":"access-token-value-abcdefghijklmnopqrstuvwxyz","refresh_token":"refresh-token-value-abcdefghijklmnopqrstuvwxyz","user":{"role_code":"OWNER","organization_id":10}}}' ;;
  */auth/me) grep -Eq 'Authorization: Bearer (access|me-access)-token-value-' "$config" || exit 95; body='{"success":true,"data":{"access_token":"me-access-token-value-abcdefghijklmnopqrstuvwxyz","refresh_token":null,"user":{"role_code":"OWNER","organization_id":10}}}' ;;
  */me/workspaces) grep -Fq 'Authorization: Bearer me-access-token-value-' "$config" || exit 95; body='{"success":true,"data":{"organizations":[{"id":10,"role_code":"OWNER"}],"stores":[{"id":22}]}}' ;;
  */owner/overview) grep -Fq 'Authorization: Bearer me-access-token-value-' "$config" || exit 95; body='{"success":true,"data":{"organizations":[{"id":10,"role_code":"OWNER","stores":[{"id":22}]}]}}' ;;
  */stores/onboard)
    count=0; [[ ! -f "$FAKE_STATE/onboard" ]] || count="$(cat "$FAKE_STATE/onboard")"; count=$((count+1)); printf '%s' "$count" >"$FAKE_STATE/onboard"
    grep -Fq 'Idempotency-Key: idem-onboarding-' "$config" || exit 94
    if [[ "$count" -eq 1 ]]; then body='{"success":true,"data":{"store_id":22,"replayed":false}}'; else body='{"success":true,"data":{"store_id":22,"replayed":true}}'; fi ;;
  */menu-clone/validate) body='{"success":true,"data":{"valid":true,"profile_code":"CHINATOWN_MENU_2026_02_02","expected":{"categories":4,"stations":3,"items":17,"options":74},"missing_codes":[],"duplicate_codes":[]}}' ;;
  */menu-clone)
    count=0; [[ ! -f "$FAKE_STATE/clone" ]] || count="$(cat "$FAKE_STATE/clone")"; count=$((count+1)); printf '%s' "$count" >"$FAKE_STATE/clone"
    grep -Fq 'Idempotency-Key: idem-clone-acceptance-' "$config" || exit 93
    if [[ "$count" -eq 1 ]]; then body='{"success":true,"data":{"clone_request_id":33,"replayed":false,"target_revision_before":4,"target_revision_after":5,"created":{"categories":4,"stations":3,"items":17,"options":74}}}'; else body='{"success":true,"data":{"clone_request_id":33,"replayed":true,"target_revision_before":4,"target_revision_after":5,"created":{"categories":4,"stations":3,"items":17,"options":74}}}'; fi ;;
  */auth/logout) body='{"success":true,"data":null}' ;;
  *) exit 92 ;;
esac
printf '%s' "$body" >"$output"
printf '200'
EOF
chmod +x "$FAKE_CURL"; mkdir "$FAKE_STATE"; export FAKE_STATE
CURL_BIN="$FAKE_CURL"

CONTENDED_FLOCK="$TMP_DIR/contended-flock"
cat >"$CONTENDED_FLOCK" <<'EOF'
#!/usr/bin/env bash
[[ "${1:-}" == '-u' ]] && exit 0
exit 1
EOF
chmod +x "$CONTENDED_FLOCK"
FLOCK_BIN="$CONTENDED_FLOCK"
expect_failure action_lock_contention acquire_action_lock
assert_contains 'another AL-003S action is already running' "$TMP_DIR/action_lock_contention.err"

PREPARE_SECRET="$TMP_DIR/prepare.json"
cat >"$PREPARE_SECRET" <<'EOF'
{"login_identifier":"STG005_OWNER_TEST","login_password":"OwnerPassphrase-123","onboarding_idempotency_key":"idem-onboarding-1234567890","onboarding_request":{"source_store_id":1,"store_name":"STG005_TARGET","store_code":"STG005_TARGET","staff":[{"login_identifier":"STG005_MANAGER","full_name":"STG005 Manager","role_code":"MANAGER","initial_password":"StaffPassphrase-123"}]}}
EOF
chmod 600 "$PREPARE_SECRET"
initialize_private_root
exec 7<"$PREPARE_SECRET"; SECRETS_FD=7; ACTION=prepare-target
read_secret_input
login >"$TMP_DIR/prepare.out"
verify_owner_context >>"$TMP_DIR/prepare.out"
prepare_target >>"$TMP_DIR/prepare.out"
clone_validation >>"$TMP_DIR/prepare.out"
logout >>"$TMP_DIR/prepare.out"
assert_contains 'OPS001_API|ONBOARDING|HTTP_200|TARGET_STORE_ID|22|REPLAY|PASS' "$TMP_DIR/prepare.out"
assert_contains 'OPS001_API|CLONE_VALIDATE|HTTP_200|PASS' "$TMP_DIR/prepare.out"
assert_not_contains 'OwnerPassphrase' "$TMP_DIR/prepare.out"
assert_not_contains 'StaffPassphrase' "$TMP_DIR/prepare.out"
assert_not_contains 'access-token-value' "$TMP_DIR/prepare.out"
assert_not_contains 'idem-onboarding' "$TMP_DIR/prepare.out"
cleanup

CLONE_SECRET="$TMP_DIR/clone.json"
cat >"$CLONE_SECRET" <<'EOF'
{"login_identifier":"STG005_OWNER_TEST","login_password":"OwnerPassphrase-123","clone_idempotency_key":"idem-clone-acceptance-1234567890"}
EOF
chmod 600 "$CLONE_SECRET"
initialize_private_root
exec 8<"$CLONE_SECRET"; SECRETS_FD=8; ACTION=clone-acceptance; TARGET_STORE_ID=22
read_secret_input
login >"$TMP_DIR/clone.out"; verify_owner_context >>"$TMP_DIR/clone.out"; clone_acceptance >>"$TMP_DIR/clone.out"; logout >>"$TMP_DIR/clone.out"
assert_contains 'OPS001_API|CLONE_VALIDATE|HTTP_200|PASS' "$TMP_DIR/clone.out"
assert_contains 'OPS001_API|CLONE_EXECUTE_REPLAY|HTTP_200|REQUEST_ID|33|PASS' "$TMP_DIR/clone.out"
assert_not_contains 'idem-clone' "$TMP_DIR/clone.out"

FORBIDDEN="$PRIVATE_ROOT/forbidden.json"; printf '{"data":{"access_token":"should-not-escape"}}' >"$FORBIDDEN"; LAST_RESPONSE="$FORBIDDEN"
expect_failure response_redaction reject_secret_response_fields forbidden
assert_contains 'forbidden secret-shaped field' "$TMP_DIR/response_redaction.err"

MISSING_SECRET="$TMP_DIR/missing-secret.json"; printf '{"login_identifier":"x","login_password":"short"}' >"$MISSING_SECRET"
chmod 600 "$MISSING_SECRET"
exec 9<"$MISSING_SECRET"; SECRETS_FD=9
expect_failure missing_secret_payload read_secret_input
assert_contains 'secret input JSON is invalid' "$TMP_DIR/missing_secret_payload.err"

# API failures never print a response body or secret.
BROKEN_CURL="$TMP_DIR/broken-curl"
cat >"$BROKEN_CURL" <<'EOF'
#!/usr/bin/env bash
exit 22
EOF
chmod +x "$BROKEN_CURL"; CURL_BIN="$BROKEN_CURL"
EMPTY_BODY="$PRIVATE_ROOT/empty.json"; printf '{}' >"$EMPTY_BODY"; chmod 600 "$EMPTY_BODY"
expect_failure api_failure api_call failing POST /auth/login "$EMPTY_BODY"
assert_contains 'failing API request failed' "$TMP_DIR/api_failure.err"
assert_not_contains 'OwnerPassphrase' "$TMP_DIR/api_failure.err"

grep -Fq 'noproxy = "*"' "$SCRIPT" || fail 'proxy bypass guard is missing'
grep -Fq 'local -a args=(-q --config' "$SCRIPT" || fail 'ambient curl configuration is not disabled first'
lock_line="$(rg -n '^  acquire_action_lock$' "$SCRIPT" | cut -d: -f1)"
runtime_line="$(rg -n '^  validate_exact_runtime_target$' "$SCRIPT" | cut -d: -f1)"
[[ "$lock_line" -lt "$runtime_line" ]] || fail 'exact-runtime validation does not occur under the shared action lock'
grep -Fq '"$FLOCK_BIN" -u "$ACTION_LOCK_FD"' "$SCRIPT" || fail 'shared action lock is not released during cleanup'
! grep -Eq '(--location|curl[^\n]* -L([[:space:]]|$)|https?://[^1]|X-User-Id|--password|--token)' "$SCRIPT" || fail 'client contains redirect, non-loopback, auth bypass, or argv-secret behavior'
echo 'PASS: OPS-001 Owner acceptance client keeps secrets off argv/output and verifies login/onboarding/validate/execute/replay failures.'
