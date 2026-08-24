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
COMPAT="$REPO_ROOT/deployment/cloud/ops001-jq-compat.py"
python3 -m py_compile "$COMPAT"
printf '{"data":{"stores":[{"id":1,"organization_id":10}]}}' >"$TMP_DIR/compat-workspace.json"
"$COMPAT" -e --argjson organization 10 --argjson source 1 '.data.stores | type == "array" and length == 1 and .[0].id == $source and .[0].organization_id == $organization' "$TMP_DIR/compat-workspace.json"
printf '{"data":{"stores":[{"id":1,"organization_id":10},{"id":2,"organization_id":10}]}}' >"$TMP_DIR/compat-extra-store.json"
expect_failure compat_extra_store "$COMPAT" -e --argjson organization 10 --argjson source 1 '.data.stores | type == "array" and length == 1 and .[0].id == $source and .[0].organization_id == $organization' "$TMP_DIR/compat-extra-store.json"
printf '{"data":{"access_token":"abcdefghijklmnopqrstuv"}}' >"$TMP_DIR/compat-short-me.json"
expect_failure compat_short_me "$COMPAT" -er '.data.access_token | strings | select(length >= 24)' "$TMP_DIR/compat-short-me.json"
printf '{"login_identifier":"STG005_OWNER_TEST","login_password":"OwnerPassphrase-123","new_login_password":"NewOwnerPassphrase20"}' >"$TMP_DIR/compat-rotate.json"
"$COMPAT" -e '(.new_login_password | type == "string" and length == 20) and (.new_login_password != .login_password)' "$TMP_DIR/compat-rotate.json"
"$COMPAT" -c '{login_identifier: .login_identifier, password: .new_login_password}' "$TMP_DIR/compat-rotate.json" >"$TMP_DIR/compat-new-login.json"
"$COMPAT" -c '{new_password: .new_login_password}' "$TMP_DIR/compat-rotate.json" >"$TMP_DIR/compat-reset.json"
printf '{"data":{"user":{"id":7}}}' >"$TMP_DIR/compat-user-id.json"
[[ "$("$COMPAT" -er '.data.user.id | numbers' "$TMP_DIR/compat-user-id.json")" == 7 ]] || fail 'compat parser did not retain the Owner user ID contract'
printf '{"data":{"user":{"username":"STG005_OWNER_TEST"}}}' >"$TMP_DIR/compat-username.json"
"$COMPAT" -e --arg login STG005_OWNER_TEST '.data.user.username == $login' "$TMP_DIR/compat-username.json"
PRINTING_FILTER='.data.store_id == $store and (.data.printing_mode == "DISABLED" or .data.printing_mode == "MOCK") and (.data.printers | type == "array" and all(.[]; ((.ip_address // "") | length) == 0))'
printf '{"data":{"store_id":44,"printing_mode":"DISABLED","printers":[]}}' >"$TMP_DIR/compat-printing-valid.json"
"$COMPAT" -e --argjson store 44 "$PRINTING_FILTER" "$TMP_DIR/compat-printing-valid.json"
OWNER_LOGIN_FILTER='.login_identifier | strings | select(startswith("STG005_") or . == "owner")'
printf '{"login_identifier":"owner"}' >"$TMP_DIR/compat-retained-owner.json"
printf '{"login_identifier":"STG005_OWNER_TEST"}' >"$TMP_DIR/compat-prefixed-owner.json"
"$COMPAT" -er "$OWNER_LOGIN_FILTER" "$TMP_DIR/compat-retained-owner.json" >/dev/null
"$COMPAT" -er "$OWNER_LOGIN_FILTER" "$TMP_DIR/compat-prefixed-owner.json" >/dev/null
for owner_file in "$TMP_DIR/compat-retained-owner.json" "$TMP_DIR/compat-prefixed-owner.json"; do
  expect_failure compat_owner_selector_suffix "$COMPAT" -er "$OWNER_LOGIN_FILTER and false" "$owner_file"
  expect_failure compat_owner_selector_prefix "$COMPAT" -er "true and $OWNER_LOGIN_FILTER" "$owner_file"
  expect_failure compat_owner_selector_pipeline "$COMPAT" -er "$OWNER_LOGIN_FILTER | select(false)" "$owner_file"
done
printf '{"login_identifier":"cashier"}' >"$TMP_DIR/compat-arbitrary-login.json"
expect_failure compat_owner_selector_whitespace "$COMPAT" -er '.login_identifier | strings | select(startswith ("STG005_") or . == "owner")' "$TMP_DIR/compat-arbitrary-login.json"
expect_failure compat_owner_selector_compact "$COMPAT" -er '.login_identifier|strings|select(startswith("STG005_")or.=="owner")' "$TMP_DIR/compat-arbitrary-login.json"
expect_failure compat_owner_selector_comment "$COMPAT" -er $'.login_identifier | strings | select(startswith("STG005_") # comment\n or . == "owner")' "$TMP_DIR/compat-arbitrary-login.json"
for scalar in '"bad"' '1' 'null'; do
  printf '{"data":{"store_id":44,"printing_mode":"DISABLED","printers":[%s]}}' "$scalar" >"$TMP_DIR/compat-printing-scalar.json"
  expect_failure "compat_printing_scalar_${scalar//[^A-Za-z0-9]/_}" "$COMPAT" -e --argjson store 44 "$PRINTING_FILTER" "$TMP_DIR/compat-printing-scalar.json"
done
expect_failure compat_known_filter_suffix "$COMPAT" -e --argjson store 44 "$PRINTING_FILTER and false" "$TMP_DIR/compat-printing-valid.json"
expect_failure compat_known_filter_prefix "$COMPAT" -e --argjson store 44 ".data | $PRINTING_FILTER" "$TMP_DIR/compat-printing-valid.json"
printf '{"data":{"enabled":true,"profile_code":"PROFILE"}}' >"$TMP_DIR/compat-catalog-enabled.json"
"$COMPAT" -e '.data.enabled == true' "$TMP_DIR/compat-catalog-enabled.json"
expect_failure compat_catalog_enabled_suffix "$COMPAT" -e '.data.enabled == true and false' "$TMP_DIR/compat-catalog-enabled.json"
expect_failure compat_catalog_enabled_prefix "$COMPAT" -e 'true and .data.enabled == true' "$TMP_DIR/compat-catalog-enabled.json"
expect_failure compat_catalog_enabled_handler_collision "$COMPAT" -e '.data.enabled == true and false and .data.profile_code' "$TMP_DIR/compat-catalog-enabled.json"
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
config=""; output=""; url=""; data_file=""; status=200; body=""
[[ "${1:-}" == '-q' ]] || exit 98
args="$*"
[[ "$args" != *'OwnerPassphrase'* && "$args" != *'StaffPassphrase'* && "$args" != *'idem-'* && "$args" != *'access-token-value'* ]] || exit 97
while [[ $# -gt 0 ]]; do
  case "$1" in --config) config="$2"; shift ;; --output) output="$2"; shift ;; --data-binary) data_file="${2#@}"; shift ;; http://*) url="$1" ;; esac
  shift
done
[[ "$(stat -f '%Lp' "$config" 2>/dev/null || stat -c '%a' "$config")" == 600 ]] || exit 96
case "$url" in
  */auth/login)
    [[ -f "$data_file" ]] || exit 91
    if grep -Fq 'ManagerPassphrase-123' "$data_file"; then
      body='{"success":true,"data":{"access_token":"manager-access-token-value-abcdefghijklmnopqrstuvwxyz","refresh_token":"manager-refresh-token-value-abcdefghijklmnopqrstuvwxyz","user":{"id":8,"username":"STG005_MANAGER_TEST","role_code":"MANAGER","organization_id":10}}}'
    elif grep -Fq 'NewOwnerPassphrase20' "$data_file"; then printf 'new' >"$FAKE_STATE/login-password";
    elif grep -Fq 'OwnerPassphrase-123' "$data_file"; then printf 'old' >"$FAKE_STATE/login-password";
    else exit 90; fi
    if [[ -z "$body" ]]; then username="${FAKE_LOGIN_USERNAME:-STG005_OWNER_TEST}"; body="{\"success\":true,\"data\":{\"access_token\":\"access-token-value-abcdefghijklmnopqrstuvwxyz\",\"refresh_token\":\"refresh-token-value-abcdefghijklmnopqrstuvwxyz\",\"user\":{\"id\":7,\"username\":\"$username\",\"role_code\":\"OWNER\",\"organization_id\":10}}}"; fi ;;
  */auth/me) grep -Eq 'Authorization: Bearer (access|me-access)-token-value-' "$config" || exit 95; body='{"success":true,"data":{"access_token":"me-access-token-value-abcdefghijklmnopqrstuvwxyz","refresh_token":null,"user":{"role_code":"OWNER","organization_id":10}}}' ;;
  */me/workspaces)
    grep -Fq 'Authorization: Bearer me-access-token-value-' "$config" || exit 95
    if [[ "${FAKE_LOGIN_ONLY:-0}" == 1 ]]; then
      if [[ "${FAKE_EXTRA_STORE:-0}" == 1 ]]; then body='{"success":true,"data":{"organizations":[{"id":10,"role_code":"OWNER"}],"stores":[{"id":1,"organization_id":10},{"id":2,"organization_id":10}]}}'; else body='{"success":true,"data":{"organizations":[{"id":10,"role_code":"OWNER"}],"stores":[{"id":1,"organization_id":10}]}}'; fi
    else body='{"success":true,"data":{"organizations":[{"id":10,"role_code":"OWNER"}],"stores":[{"id":22}]}}'; fi ;;
  */owner/overview)
    grep -Fq 'Authorization: Bearer me-access-token-value-' "$config" || exit 95
    if [[ -f "$FAKE_STATE/business" ]]; then body='{"success":true,"data":{"organizations":[{"id":10,"role_code":"OWNER","can_create_store":true,"stores":[{"id":44,"operational_state":"LIVE","is_live":true}]}]}}'; elif [[ "${FAKE_LOGIN_ONLY:-0}" == 1 ]]; then body='{"success":true,"data":{"organizations":[{"id":10,"role_code":"OWNER","stores":[{"id":1}]}]}}'; else body='{"success":true,"data":{"organizations":[{"id":10,"role_code":"OWNER","stores":[{"id":22}]}]}}'; fi ;;
  */admin/staff/7/reset-password)
    grep -Fq 'Authorization: Bearer me-access-token-value-' "$config" || exit 95
    grep -Fq 'NewOwnerPassphrase20' "$data_file" || exit 89
    [[ "$(cat "$FAKE_STATE/login-password")" == old ]] || exit 88
    body='{"success":true,"data":{"id":7,"username":"STG005_OWNER_TEST","role_code":"OWNER"}}' ;;
  */stores/onboard)
    count=0; [[ ! -f "$FAKE_STATE/onboard" ]] || count="$(cat "$FAKE_STATE/onboard")"; count=$((count+1)); printf '%s' "$count" >"$FAKE_STATE/onboard"
    grep -Fq 'Idempotency-Key: idem-onboarding-' "$config" || exit 94
    if [[ "$count" -eq 1 ]]; then body='{"success":true,"data":{"store_id":22,"replayed":false}}'; else body='{"success":true,"data":{"store_id":22,"replayed":true}}'; fi ;;
  */menu-clone/validate) body='{"success":true,"data":{"valid":true,"profile_code":"CHINATOWN_MENU_2026_02_02","expected":{"categories":4,"stations":3,"items":17,"options":74},"missing_codes":[],"duplicate_codes":[]}}' ;;
  */menu-clone)
    count=0; [[ ! -f "$FAKE_STATE/clone" ]] || count="$(cat "$FAKE_STATE/clone")"; count=$((count+1)); printf '%s' "$count" >"$FAKE_STATE/clone"
    grep -Fq 'Idempotency-Key: idem-clone-acceptance-' "$config" || exit 93
    if [[ "$count" -eq 1 ]]; then body='{"success":true,"data":{"clone_request_id":33,"replayed":false,"target_revision_before":4,"target_revision_after":5,"created":{"categories":4,"stations":3,"items":17,"options":74}}}'; else body='{"success":true,"data":{"clone_request_id":33,"replayed":true,"target_revision_before":4,"target_revision_after":5,"created":{"categories":4,"stations":3,"items":17,"options":74}}}'; fi ;;
  */owner/organizations/10/stores/create-catalog) body='{"success":true,"data":{"enabled":true}}' ;;
  */admin/platform/organizations) body='{"success":true,"data":{"id":20,"status":"active"}}' ;;
  */admin/platform/organizations/20) printf inactive >"$FAKE_STATE/foreign-deactivated"; body='{"success":true,"data":{"id":20,"status":"inactive"}}' ;;
  */owner/organizations/10/stores)
    if grep -Fq 'manager-access-token-value-' "$config"; then status=403; body='{"success":false,"error_code":"BUSINESS_STORE_CREATE_AUTHORIZATION_DENIED"}'
    else count=0; [[ ! -f "$FAKE_STATE/business" ]] || count="$(cat "$FAKE_STATE/business")"; count=$((count+1)); printf '%s' "$count" >"$FAKE_STATE/business"; if [[ "$count" -eq 1 && "${FAKE_FIRST_REPLAY:-0}" != 1 ]]; then replay=false; else replay=true; fi; body="{\"success\":true,\"data\":{\"store_id\":44,\"store_kind\":\"BUSINESS\",\"store_status\":\"active\",\"lifecycle_status\":\"ACTIVE\",\"operational_state\":\"LIVE\",\"is_live\":true,\"validation_status\":\"PASS\",\"replayed\":$replay}}"; fi ;;
  */owner/organizations/20/stores) status=403; body='{"success":false,"error_code":"BUSINESS_STORE_CREATE_ORGANIZATION_DENIED"}' ;;
  */stores/44/context) body='{"success":true,"data":{"id":44,"operational_state":"LIVE","is_live":true}}' ;;
  */frontdesk/dining-tables?store_id=44) body='{"success":true,"data":[{"id":1},{"id":2}]}' ;;
  */admin/printing?store_id=44) body='{"success":true,"data":{"store_id":44,"printing_mode":"DISABLED","printers":[],"assignments":[]}}' ;;
  */auth/logout) body='{"success":true,"data":null}' ;;
  *) exit 92 ;;
esac
printf '%s' "$body" >"$output"
printf '%s' "$status"
EOF
chmod +x "$FAKE_CURL"; mkdir "$FAKE_STATE"; export FAKE_STATE
CURL_BIN="$FAKE_CURL"
FAKE_DOCKER="$TMP_DIR/docker"
cat >"$FAKE_DOCKER" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *'inspect'*'restaurant-pos-staging-backend-1'* ]]; then printf 'sha256:%064d\n' 0
elif [[ "$*" == *'inspect'*'restaurant-pos-staging-nginx-1'* ]]; then printf 'sha256:%064d\n' 1
else [[ "$*" == *'restaurant-pos-staging-db-1'* ]]; query="$(cat)"; if [[ "$query" == *'is_active = true'* ]]; then printf '1|0\n'; else printf '0\n'; fi; fi
EOF
chmod +x "$FAKE_DOCKER"; DOCKER_BIN="$FAKE_DOCKER"

LOGIN_ONLY_SECRET="$TMP_DIR/login-only.json"
cat >"$LOGIN_ONLY_SECRET" <<'EOF'
{"login_identifier":"STG005_OWNER_TEST","login_password":"OwnerPassphrase-123"}
EOF
chmod 600 "$LOGIN_ONLY_SECRET"
initialize_private_root
export FAKE_LOGIN_ONLY=1
exec 6<"$LOGIN_ONLY_SECRET"; SECRETS_FD=6; ACTION=owner-login-acceptance; TARGET_STORE_ID=""
read_secret_input
login >"$TMP_DIR/login-only.out"; verify_owner_context >>"$TMP_DIR/login-only.out"; logout >>"$TMP_DIR/login-only.out"
assert_contains 'OPS001_API|LOGIN|HTTP_200' "$TMP_DIR/login-only.out"
assert_contains 'OPS001_API|WORKSPACES|HTTP_200' "$TMP_DIR/login-only.out"
assert_contains 'OPS001_API|OVERVIEW|HTTP_200' "$TMP_DIR/login-only.out"
assert_contains 'OPS001_API|LOGOUT|HTTP_200' "$TMP_DIR/login-only.out"
assert_not_contains 'OwnerPassphrase' "$TMP_DIR/login-only.out"
assert_not_contains 'access-token-value' "$TMP_DIR/login-only.out"
assert_not_contains 'ONBOARDING' "$TMP_DIR/login-only.out"
assert_not_contains 'CLONE' "$TMP_DIR/login-only.out"
cleanup
initialize_private_root
export FAKE_EXTRA_STORE=1
exec 5<"$LOGIN_ONLY_SECRET"; SECRETS_FD=5; ACTION=owner-login-acceptance; TARGET_STORE_ID=""
read_secret_input
login >/dev/null
expect_failure unexpected_store_access verify_owner_context
assert_contains 'not exactly the approved synthetic source Store' "$TMP_DIR/unexpected_store_access.err"
cleanup
unset FAKE_EXTRA_STORE

ROTATE_SECRET="$TMP_DIR/rotate.json"
cat >"$ROTATE_SECRET" <<'EOF'
{"login_identifier":"STG005_OWNER_TEST","login_password":"OwnerPassphrase-123","new_login_password":"NewOwnerPassphrase20"}
EOF
chmod 600 "$ROTATE_SECRET"
initialize_private_root
exec 4<"$ROTATE_SECRET"; SECRETS_FD=4; ACTION=rotate-owner-credential; TARGET_STORE_ID=""
APPROVED_LOGIN_IDENTIFIER=STG005_OWNER_TEST
[[ "$(client_scope)" == *';owner_login_identifier=STG005_OWNER_TEST' ]] || fail 'credential rotation approval scope is not bound to the exact synthetic Owner identifier'
read_secret_input
login >"$TMP_DIR/rotate.out"; verify_owner_context >>"$TMP_DIR/rotate.out"; rotate_owner_credential >>"$TMP_DIR/rotate.out"; logout >>"$TMP_DIR/rotate.out"
assert_contains 'OPS001_API|OWNER_CREDENTIAL|ROTATED|HTTP_200' "$TMP_DIR/rotate.out"
[[ "$(grep -c 'OPS001_API|LOGIN|HTTP_200' "$TMP_DIR/rotate.out")" == 2 ]] || fail 'credential rotation must prove old and new credential login'
assert_not_contains 'OwnerPassphrase' "$TMP_DIR/rotate.out"
assert_not_contains 'NewOwnerPassphrase' "$TMP_DIR/rotate.out"
assert_not_contains 'access-token-value' "$TMP_DIR/rotate.out"
[[ "$(cat "$FAKE_STATE/login-password")" == new ]] || fail 'credential rotation did not prove the exact new credential after reset'
cleanup

while read -r invalid_label old_password invalid_password; do
  invalid_secret="$TMP_DIR/invalid-$invalid_label.json"
  printf '{"login_identifier":"STG005_OWNER_TEST","login_password":"%s","new_login_password":"%s"}\n' "$old_password" "$invalid_password" >"$invalid_secret"
  chmod 600 "$invalid_secret"
  initialize_private_root
  exec 3<"$invalid_secret"; SECRETS_FD=3; ACTION=rotate-owner-credential; APPROVED_LOGIN_IDENTIFIER=STG005_OWNER_TEST
  expect_failure "invalid_rotation_$invalid_label" read_secret_input
  cleanup
done <<'EOF'
same SameAsOldPassphrase1 SameAsOldPassphrase1
length19 OwnerPassphrase-123 NineteenCharacter12
length21 OwnerPassphrase-123 TwentyOneCharacters12
EOF

initialize_private_root
exec 3<"$ROTATE_SECRET"; SECRETS_FD=3; ACTION=rotate-owner-credential; APPROVED_LOGIN_IDENTIFIER=STG005_DIFFERENT_OWNER
expect_failure rotation_approval_identity_mismatch read_secret_input
assert_contains 'does not match the approval binding' "$TMP_DIR/rotation_approval_identity_mismatch.err"
cleanup

initialize_private_root
exec 3<"$ROTATE_SECRET"; SECRETS_FD=3; ACTION=rotate-owner-credential; APPROVED_LOGIN_IDENTIFIER=STG005_OWNER_TEST
read_secret_input
export FAKE_LOGIN_USERNAME=STG005_DIFFERENT_OWNER
expect_failure rotation_response_identity_mismatch login
assert_contains 'login principal does not match' "$TMP_DIR/rotation_response_identity_mismatch.err"
unset FAKE_LOGIN_USERNAME
cleanup
unset FAKE_LOGIN_ONLY

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

BUSINESS_SECRET="$TMP_DIR/business-create.json"
cat >"$BUSINESS_SECRET" <<'EOF'
{"login_identifier":"owner","login_password":"OwnerPassphrase-123","manager_login_identifier":"manager","manager_login_password":"ManagerPassphrase-123","business_create_idempotency_key":"idem-business-0123456789abcdef0123456789abcdef","business_create_request":{"store_name":"STG005 Business Acceptance","store_code":"STG005_BUSINESS_0123456789ABCDEF0123456789ABCDEF"}}
EOF
chmod 600 "$BUSINESS_SECRET"
rm -f "$FAKE_STATE/business"
cleanup
JQ_BIN="$COMPAT"
export FAKE_LOGIN_USERNAME=owner
initialize_private_root
exec 7<"$BUSINESS_SECRET"; SECRETS_FD=7; ACTION=business-store-create-acceptance; TARGET_STORE_ID=""; ACCEPTANCE_RUN_ID=0123456789abcdef0123456789abcdef; APPROVAL_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa; ENV_DIGEST=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb; PREFLIGHT_EVIDENCE_SHA256=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
read_secret_input
login >"$TMP_DIR/business.out"; verify_owner_context >>"$TMP_DIR/business.out"; business_store_create_acceptance >>"$TMP_DIR/business.out"; logout >>"$TMP_DIR/business.out"
assert_contains 'OPS001_API|BUSINESS_STORE_CREATE|RUN_ID|0123456789abcdef0123456789abcdef|STORE_ID|44|FRESH_CREATE|PASS|REPLAY|PASS|FOREIGN_ACTIVE_ORG|DENIED|MANAGER|DENIED|PRINTING|ENDPOINT_FREE' "$TMP_DIR/business.out"
BUSINESS_EVIDENCE="$OPS001_EXPECTED_ROOT/evidence/v26-business-create-$ACCEPTANCE_RUN_ID.json"
"$JQ_BIN" -e '.schema == "V26_BUSINESS_STORE_CREATE_ACCEPTANCE_V1" and .fresh_create == "PASS" and .foreign_active_organization_denied == "PASS" and .final_result == "PASS"' "$BUSINESS_EVIDENCE" >/dev/null
assert_not_contains 'OwnerPassphrase' "$TMP_DIR/business.out"
assert_not_contains 'ManagerPassphrase' "$TMP_DIR/business.out"
assert_not_contains 'access-token-value' "$TMP_DIR/business.out"
assert_not_contains 'idem-business' "$TMP_DIR/business.out"
cleanup
unset FAKE_LOGIN_USERNAME

RETAINED_OWNER_ROTATE_SECRET="$TMP_DIR/retained-owner-rotate.json"
printf '%s\n' '{"login_identifier":"owner","login_password":"OwnerPassphrase-123","new_login_password":"NewOwnerPassphrase20"}' >"$RETAINED_OWNER_ROTATE_SECRET"
chmod 600 "$RETAINED_OWNER_ROTATE_SECRET"
initialize_private_root
exec 6<"$RETAINED_OWNER_ROTATE_SECRET"; SECRETS_FD=6; ACTION=rotate-owner-credential; APPROVED_LOGIN_IDENTIFIER=owner
read_secret_input
cleanup

INVALID_RETAINED_SECRET="$TMP_DIR/invalid-retained-business.json"
python3 - "$BUSINESS_SECRET" "$INVALID_RETAINED_SECRET" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); d['manager_login_identifier']='cashier'
json.dump(d,open(sys.argv[2],'w'),separators=(',',':'))
PY
chmod 600 "$INVALID_RETAINED_SECRET"
initialize_private_root
exec 6<"$INVALID_RETAINED_SECRET"; SECRETS_FD=6; ACTION=business-store-create-acceptance; ACCEPTANCE_RUN_ID=0123456789abcdef0123456789abcdef
expect_failure invalid_retained_manager read_secret_input
cleanup

REPLAY_SECRET="$TMP_DIR/business-first-replay.json"
cat >"$REPLAY_SECRET" <<'EOF'
{"login_identifier":"STG005_OWNER_TEST","login_password":"OwnerPassphrase-123","manager_login_identifier":"STG005_MANAGER_TEST","manager_login_password":"ManagerPassphrase-123","business_create_idempotency_key":"idem-business-fedcba9876543210fedcba9876543210","business_create_request":{"store_name":"STG005 Replay Rejection","store_code":"STG005_BUSINESS_FEDCBA9876543210FEDCBA9876543210"}}
EOF
chmod 600 "$REPLAY_SECRET"; rm -f "$FAKE_STATE/business" "$FAKE_STATE/foreign-deactivated"; export FAKE_FIRST_REPLAY=1
initialize_private_root
exec 7<"$REPLAY_SECRET"; SECRETS_FD=7; ACTION=business-store-create-acceptance; ACCEPTANCE_RUN_ID=fedcba9876543210fedcba9876543210
read_secret_input; login >/dev/null; verify_owner_context >/dev/null
business_with_failure_cleanup() { trap cleanup EXIT; business_store_create_acceptance; }
expect_failure first_request_replay business_with_failure_cleanup
assert_contains 'first request was not a fresh canonical LIVE creation' "$TMP_DIR/first_request_replay.err"
[[ ! -e "$OPS001_EXPECTED_ROOT/evidence/v26-business-create-$ACCEPTANCE_RUN_ID.json" ]] || fail 'replayed first request created PASS evidence'
[[ -f "$FAKE_STATE/foreign-deactivated" ]] || fail 'failed acceptance did not deactivate its synthetic foreign Organization'
unset FAKE_FIRST_REPLAY; cleanup
initialize_private_root

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
grep -Fq 'owner-login-acceptance' "$SCRIPT" || fail 'dedicated Owner login acceptance action is missing'
echo 'PASS: OPS-001 Owner acceptance client keeps secrets off argv/output and verifies bounded login/onboarding/validate/execute/replay behavior.'
