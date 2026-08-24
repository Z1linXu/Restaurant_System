#!/usr/bin/env bash
set -Eeuo pipefail

# Secret-safe client for the existing Owner acceptance APIs. It accepts all
# credentials, tokens, staff passwords, and raw idempotency keys only through
# one inherited non-interactive file descriptor and private temporary files.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-synthetic-acceptance.sh
source "$SCRIPT_DIR/staging-synthetic-acceptance.sh"
# shellcheck source=staging-ops-common.sh
source "$SCRIPT_DIR/staging-ops-common.sh"

ACTION="validate"
EXECUTE_RUNTIME="false"
APPROVED_SHA=""
ENV_FILE=""
ENV_DIGEST=""
APPROVAL_FILE=""
APPROVAL_SHA256=""
PREFLIGHT_EVIDENCE=""
PREFLIGHT_EVIDENCE_SHA256=""
SECRETS_FD=""
APPROVED_LOGIN_IDENTIFIER=""
ORGANIZATION_ID=""
FOREIGN_ORGANIZATION_ID=""
TARGET_STORE_ID=""
BUSINESS_STORE_ID=""
ACCEPTANCE_RUN_ID=""
ACCEPTANCE_EVIDENCE=""
FOREIGN_CLEANUP_REQUIRED="false"
ACCEPTED_BACKEND_IMAGE_ID=""
ACCEPTED_FRONTEND_IMAGE_ID=""
SOURCE_STORE_ID="1"
PROFILE_CODE="CHINATOWN_MENU_2026_02_02"
API_BASE="http://127.0.0.1:18080/api/v1"
PRIVATE_ROOT=""
SECRET_INPUT=""
LOGIN_IDENTIFIER=""
ACCESS_TOKEN=""
REFRESH_TOKEN=""
OWNER_USER_ID=""
LAST_HTTP_STATUS=""
LAST_RESPONSE=""
CURL_BIN=""
JQ_BIN=""
DOCKER_BIN=""
SAFE_API_PATH_REGEX='^/[A-Za-z0-9_./?=&-]+$'

usage() {
  cat <<'EOF'
Usage:
  staging-owner-acceptance-client.sh --validate --approved-sha <full-sha> --env-file <fixed-env>
  staging-owner-acceptance-client.sh --execute-runtime --action prepare-target \
    --approved-sha <full-sha> --env-file <fixed-env> --preflight-evidence <file> \
    --preflight-evidence-sha256 <sha256> --organization-id <id> \
    --approval <file> --approval-sha256 <sha256> --secrets-fd <open-fd>
  staging-owner-acceptance-client.sh --execute-runtime --action owner-login-acceptance \
    --approved-sha <full-sha> --env-file <fixed-env> --preflight-evidence <file> \
    --preflight-evidence-sha256 <sha256> --organization-id <id> --source-store-id 1 \
    --approval <file> --approval-sha256 <sha256> --secrets-fd <open-fd>
  staging-owner-acceptance-client.sh --execute-runtime --action rotate-owner-credential \
    --approved-sha <full-sha> --env-file <fixed-env> --preflight-evidence <file> \
    --preflight-evidence-sha256 <sha256> --organization-id <id> --source-store-id 1 \
    --owner-login-identifier <approved-synthetic-login> \
    --approval <file> --approval-sha256 <sha256> --secrets-fd <open-fd>
  staging-owner-acceptance-client.sh --execute-runtime --action clone-acceptance \
    --approved-sha <full-sha> --env-file <fixed-env> --preflight-evidence <file> \
    --preflight-evidence-sha256 <sha256> --organization-id <id> \
    --target-store-id <id> --source-store-id 1 --profile-code CHINATOWN_MENU_2026_02_02 \
    --approval <file> --approval-sha256 <sha256> --secrets-fd <open-fd>
  staging-owner-acceptance-client.sh --execute-runtime --action business-store-create-acceptance \
    --approved-sha <full-sha> --env-file <fixed-env> --preflight-evidence <file> \
    --preflight-evidence-sha256 <sha256> --organization-id <id> \
    --acceptance-run-id <32-hex> \
    --approval <file> --approval-sha256 <sha256> --secrets-fd <open-fd>

The inherited FD contains one JSON object. Every action requires
login_identifier/login_password. owner-login-acceptance performs only login,
exact Owner/workspace/dashboard access verification, and logout; it creates no
Store, staff, credential, menu, request, or other business data.
rotate-owner-credential additionally requires a distinct 20-character
new_login_password, resets only the authenticated synthetic Owner credential,
and proves the new credential through a second login/context/logout sequence.
prepare-target additionally requires
onboarding_idempotency_key and onboarding_request. clone-acceptance requires
clone_idempotency_key. Secret values are forbidden in argv/environment/output.
business-store-create-acceptance requires a synthetic business request/key and
a synthetic Manager login in the inherited secret JSON. It proves Owner
create/replay, wrong-Organization and Manager denial, immediate LIVE context,
Frontdesk access, and endpoint-free DISABLED/MOCK Printing Management access.
The client uses only loopback HTTP, no redirects/proxy, private mode-0600
request/response/config files, disables ambient curl configuration, validates
the exact running image through the OPS-001 runtime gate, and reuses the
existing API contracts.
EOF
}

cleanup() {
  local status=$?
  if [[ "$FOREIGN_CLEANUP_REQUIRED" == "true" && "$FOREIGN_ORGANIZATION_ID" =~ ^[1-9][0-9]*$ && -n "$ACCESS_TOKEN" && -d "$PRIVATE_ROOT" && -x "$CURL_BIN" && -x "$JQ_BIN" ]]; then
    local foreign_cleanup_body="$PRIVATE_ROOT/cleanup-foreign-organization.json" foreign_cleanup_config="$PRIVATE_ROOT/cleanup-foreign-organization.curl" foreign_cleanup_response="$PRIVATE_ROOT/cleanup-foreign-organization.response"
    "$JQ_BIN" -n --argjson id "$FOREIGN_ORGANIZATION_ID" --arg run "$ACCEPTANCE_RUN_ID" '{id:$id,name:("STG005 Foreign Acceptance " + $run),code:("STG005_FOREIGN_" + ($run | ascii_upcase)),status:"inactive"}' >"$foreign_cleanup_body" 2>/dev/null || true
    chmod 600 "$foreign_cleanup_body" 2>/dev/null || true
    write_curl_config "$foreign_cleanup_config" "$ACCESS_TOKEN" "" 2>/dev/null || true
    "$CURL_BIN" -q --config "$foreign_cleanup_config" --request PUT --output "$foreign_cleanup_response" --max-time 15 --data-binary "@$foreign_cleanup_body" "$API_BASE/admin/platform/organizations/$FOREIGN_ORGANIZATION_ID" >/dev/null 2>&1 || true
  fi
  FOREIGN_CLEANUP_REQUIRED="false"; FOREIGN_ORGANIZATION_ID=""
  if [[ -n "$ACCESS_TOKEN" && -n "$REFRESH_TOKEN" && -d "$PRIVATE_ROOT" && -x "$CURL_BIN" && -x "$JQ_BIN" ]]; then
    local cleanup_body="$PRIVATE_ROOT/cleanup-logout.json" cleanup_config="$PRIVATE_ROOT/cleanup-logout.curl" cleanup_response="$PRIVATE_ROOT/cleanup-logout.response"
    "$JQ_BIN" -n --arg refresh "$REFRESH_TOKEN" '{refresh_token: $refresh}' >"$cleanup_body" 2>/dev/null || true
    chmod 600 "$cleanup_body" 2>/dev/null || true
    write_curl_config "$cleanup_config" "$ACCESS_TOKEN" "" 2>/dev/null || true
    "$CURL_BIN" -q --config "$cleanup_config" --request POST --output "$cleanup_response" --max-time 15 --data-binary "@$cleanup_body" "$API_BASE/auth/logout" >/dev/null 2>&1 || true
  fi
  ACCESS_TOKEN=""; REFRESH_TOKEN=""; OWNER_USER_ID=""
  if [[ -n "$PRIVATE_ROOT" && "$PRIVATE_ROOT" == "${TMPDIR:-/tmp}"/restaurant-pos-ops001-api.* ]]; then rm -rf -- "$PRIVATE_ROOT"; fi
  PRIVATE_ROOT=""; SECRET_INPUT=""; LOGIN_IDENTIFIER=""; LAST_RESPONSE=""
  if [[ "${ACTION_LOCK_FD:-}" == "9" && -n "${FLOCK_BIN:-}" ]]; then
    "$FLOCK_BIN" -u "$ACTION_LOCK_FD" >/dev/null 2>&1 || true
    exec 9>&-
    ACTION_LOCK_FD=""
  fi
  return "$status"
}

validate_release_and_env() {
  local release="$OPS001_EXPECTED_ROOT/releases/$APPROVED_SHA"
  [[ "$-" != *x* ]] || ops001_die "shell tracing must be disabled"
  [[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]] || ops001_die "approved SHA must be a lowercase full 40-character SHA"
  [[ "$ENV_FILE" == "$OPS001_EXPECTED_ROOT/config/.env.staging" ]] || ops001_die "environment file must use the fixed Staging path"
  ops001_validate_env_file "$ENV_FILE"
  ENV_DIGEST="$(ops001_file_digest "$ENV_FILE")"
  ops001_validate_fixed_env_identity "$ENV_FILE" "$APPROVED_SHA"
  [[ -d "$release" && ! -L "$release" ]] || ops001_die "approved detached release is missing"
  [[ "$(git -C "$release" rev-parse HEAD 2>/dev/null || true)" == "$APPROVED_SHA" ]] || ops001_die "approved release HEAD mismatch"
  [[ -z "$(git -C "$release" status --porcelain=v1 --untracked-files=all)" ]] || ops001_die "approved release is not clean"
}

validate_exact_runtime_target() {
  local release="$OPS001_EXPECTED_ROOT/releases/$APPROVED_SHA"
  "$release/deployment/cloud/staging-runtime-evidence.sh" \
    --validate \
    --approved-sha "$APPROVED_SHA" \
    --env-file "$ENV_FILE" \
    --preflight-evidence "$PREFLIGHT_EVIDENCE" \
    --preflight-evidence-sha256 "$PREFLIGHT_EVIDENCE_SHA256" >/dev/null ||
    ops001_die "exact running Staging identity validation failed"
  [[ "$(ops001_file_digest "$ENV_FILE")" == "$ENV_DIGEST" ]] || ops001_die "Staging environment changed before API action"
}

client_scope() {
  printf 'organization_id=%s;target_store_id=%s;source_store_id=%s;profile_code=%s;preflight=%s' "$ORGANIZATION_ID" "$TARGET_STORE_ID" "$SOURCE_STORE_ID" "$PROFILE_CODE" "$PREFLIGHT_EVIDENCE_SHA256"
  if [[ "$ACTION" == "business-store-create-acceptance" ]]; then
    printf ';acceptance_run_id=%s' "$ACCEPTANCE_RUN_ID"
  fi
  if [[ "$ACTION" == "rotate-owner-credential" ]]; then
    printf ';owner_login_identifier=%s' "$APPROVED_LOGIN_IDENTIFIER"
  fi
}

initialize_private_root() {
  local temporary="${TMPDIR:-/tmp}"
  umask 077
  PRIVATE_ROOT="$(mktemp -d "$temporary/restaurant-pos-ops001-api.XXXXXX")"
  chmod 700 "$PRIVATE_ROOT"
  [[ "$(ops001_file_owner "$PRIVATE_ROOT")" == "$(id -u)" && "$(ops001_file_mode "$PRIVATE_ROOT")" == "700" ]] || ops001_die "private API workspace is unsafe"
}

read_secret_input() {
  [[ "$SECRETS_FD" =~ ^[3-9][0-9]*$ ]] || ops001_die "--secrets-fd must name an inherited non-standard descriptor"
  [[ -r "/dev/fd/$SECRETS_FD" ]] || ops001_die "secret descriptor is not readable"
  if [[ -e "/proc/self/fd/$SECRETS_FD" && -f "$(readlink -f "/proc/self/fd/$SECRETS_FD")" ]]; then
    local descriptor_source
    descriptor_source="$(readlink -f "/proc/self/fd/$SECRETS_FD")"
    [[ "$(ops001_file_owner "$descriptor_source")" == "$(id -u)" && "$(ops001_file_mode "$descriptor_source")" == "600" ]] ||
      ops001_die "regular-file secret descriptor input must be owner-only mode 0600"
  fi
  SECRET_INPUT="$PRIVATE_ROOT/secrets.json"
  dd bs=4096 of="$SECRET_INPUT" <&"$SECRETS_FD" 2>/dev/null
  chmod 600 "$SECRET_INPUT"
  "$JQ_BIN" -e 'type == "object" and (.login_identifier | type == "string" and length > 0) and (.login_password | type == "string" and length >= 12)' "$SECRET_INPUT" >/dev/null || ops001_die "secret input JSON is invalid"
  LOGIN_IDENTIFIER="$("$JQ_BIN" -er '.login_identifier | strings | select(startswith("STG005_") or . == "owner")' "$SECRET_INPUT")" || ops001_die "Owner login identifier is outside the retained synthetic contract"
  if [[ "$ACTION" == "rotate-owner-credential" && "$LOGIN_IDENTIFIER" != "$APPROVED_LOGIN_IDENTIFIER" ]]; then
    ops001_die "credential rotation login identifier does not match the approval binding"
  fi
  case "$ACTION" in
    rotate-owner-credential)
      "$JQ_BIN" -e '(.new_login_password | type == "string" and length == 20) and (.new_login_password != .login_password)' "$SECRET_INPUT" >/dev/null || ops001_die "credential rotation secret payload is invalid"
      ;;
    prepare-target)
      "$JQ_BIN" -e '(.onboarding_idempotency_key | type == "string" and length >= 16) and (.onboarding_request | type == "object") and (.onboarding_request.source_store_id == 1) and (.onboarding_request.store_code | type == "string" and startswith("STG005_")) and (.onboarding_request.staff | type == "array" and length > 0) and all(.onboarding_request.staff[]; (.login_identifier | type == "string" and startswith("STG005_")) and (.role_code == "MANAGER" or .role_code == "FRONTDESK") and (.initial_password | type == "string" and length >= 12))' "$SECRET_INPUT" >/dev/null || ops001_die "prepare-target secret payload is incomplete or outside the synthetic contract"
      ;;
    clone-acceptance)
      "$JQ_BIN" -e '(.clone_idempotency_key | type == "string" and length >= 16)' "$SECRET_INPUT" >/dev/null || ops001_die "clone-acceptance secret payload is incomplete"
      ;;
    business-store-create-acceptance)
      "$JQ_BIN" -e --arg run "$ACCEPTANCE_RUN_ID" '(.business_create_idempotency_key | type == "string" and contains($run)) and (.business_create_request | type == "object") and (.business_create_request.store_name | type == "string" and length > 0) and (.business_create_request.store_code == ("STG005_BUSINESS_" + ($run | ascii_upcase))) and (.manager_login_identifier | type == "string" and (startswith("STG005_") or . == "manager")) and (.manager_login_password | type == "string" and length >= 12)' "$SECRET_INPUT" >/dev/null || ops001_die "business Store acceptance secret payload is incomplete or not bound to this run"
      ;;
  esac
}

write_curl_config() {
  local config="$1" token="${2:-}" idempotency="${3:-}"
  : >"$config"; chmod 600 "$config"
  printf 'silent\nshow-error\nfail-with-body\nmax-time = 30\nconnect-timeout = 5\nnoproxy = "*"\nheader = "Accept: application/json"\nheader = "Content-Type: application/json"\n' >>"$config"
  [[ -z "$token" ]] || printf 'header = "Authorization: Bearer %s"\n' "$token" >>"$config"
  [[ -z "$idempotency" ]] || printf 'header = "Idempotency-Key: %s"\n' "$idempotency" >>"$config"
}

api_call() {
  local label="$1" method="$2" path="$3" body="$4" token="${5:-}" idempotency="${6:-}"
  local config="$PRIVATE_ROOT/$label.curl" response="$PRIVATE_ROOT/$label.response" status_file="$PRIVATE_ROOT/$label.status"
  [[ "$path" == /* && "$path" != *'..'* && "$path" =~ $SAFE_API_PATH_REGEX ]] || ops001_die "unsafe API path"
  [[ "$method" == "GET" || "$method" == "POST" || "$method" == "PUT" ]] || ops001_die "unsupported HTTP method"
  [[ -z "$idempotency" || "$idempotency" =~ ^[A-Za-z0-9._:-]{16,255}$ ]] || ops001_die "idempotency key has unsafe characters or length"
  write_curl_config "$config" "$token" "$idempotency"
  local -a args=(-q --config "$config" --request "$method" --output "$response" --write-out '%{http_code}' "$API_BASE$path")
  [[ -z "$body" ]] || args+=(--data-binary "@$body")
  if ! "$CURL_BIN" "${args[@]}" >"$status_file"; then ops001_die "$label API request failed"; fi
  LAST_HTTP_STATUS="$(cat "$status_file")"; LAST_RESPONSE="$response"
  [[ "$LAST_HTTP_STATUS" =~ ^2[0-9][0-9]$ ]] || ops001_die "$label returned HTTP $LAST_HTTP_STATUS"
  "$JQ_BIN" -e '.success == true' "$response" >/dev/null || ops001_die "$label returned invalid or unsuccessful JSON"
}

api_expect_error() {
  local label="$1" method="$2" path="$3" body="$4" token="$5" idempotency="$6" expected_status="$7" expected_code="$8"
  local config="$PRIVATE_ROOT/$label.curl" response="$PRIVATE_ROOT/$label.response" status_file="$PRIVATE_ROOT/$label.status"
  [[ "$path" == /* && "$path" != *'..'* && "$path" =~ $SAFE_API_PATH_REGEX ]] || ops001_die "unsafe API path"
  [[ "$method" == "GET" || "$method" == "POST" ]] || ops001_die "unsupported HTTP method"
  [[ -z "$idempotency" || "$idempotency" =~ ^[A-Za-z0-9._:-]{16,255}$ ]] || ops001_die "idempotency key has unsafe characters or length"
  write_curl_config "$config" "$token" "$idempotency"
  local -a args=(-q --config "$config" --request "$method" --output "$response" --write-out '%{http_code}' "$API_BASE$path")
  [[ -z "$body" ]] || args+=(--data-binary "@$body")
  "$CURL_BIN" "${args[@]}" >"$status_file" || true
  LAST_HTTP_STATUS="$(cat "$status_file")"; LAST_RESPONSE="$response"
  [[ "$LAST_HTTP_STATUS" == "$expected_status" ]] || ops001_die "$label returned HTTP $LAST_HTTP_STATUS instead of $expected_status"
  "$JQ_BIN" -e --arg code "$expected_code" '.success == false and .error_code == $code' "$response" >/dev/null || ops001_die "$label returned an unexpected error contract"
  reject_secret_response_fields "$label"
}

reject_secret_response_fields() {
  local label="$1"
  "$JQ_BIN" -e '[paths(scalars) as $p | ($p[-1] | tostring | ascii_downcase) | select(test("password|token|cookie|authorization|secret"))] | length == 0' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "$label response contains a forbidden secret-shaped field"
}

login() {
  local password_field="${1:-login_password}"
  local body="$PRIVATE_ROOT/login.json"
  if [[ "$password_field" == "new_login_password" ]]; then
    "$JQ_BIN" -c '{login_identifier: .login_identifier, password: .new_login_password}' "$SECRET_INPUT" >"$body"
  else
    "$JQ_BIN" -c '{login_identifier: .login_identifier, password: .login_password}' "$SECRET_INPUT" >"$body"
  fi
  chmod 600 "$body"
  api_call login POST /auth/login "$body"
  ACCESS_TOKEN="$("$JQ_BIN" -er '.data.access_token | select(type == "string" and length > 20)' "$LAST_RESPONSE")" || ops001_die "login response lacks access token"
  REFRESH_TOKEN="$("$JQ_BIN" -er '.data.refresh_token | select(type == "string" and length > 20)' "$LAST_RESPONSE")" || ops001_die "login response lacks refresh token"
  OWNER_USER_ID="$("$JQ_BIN" -er '.data.user.id | numbers' "$LAST_RESPONSE")" || ops001_die "login response lacks Owner user ID"
  [[ "$OWNER_USER_ID" =~ ^[1-9][0-9]*$ ]] || ops001_die "login response contains an invalid Owner user ID"
  "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" '.data.user.role_code == "OWNER" and .data.user.organization_id == $organization' "$LAST_RESPONSE" >/dev/null || ops001_die "login identity is not the approved Organization Owner"
  "$JQ_BIN" -e --arg login "$LOGIN_IDENTIFIER" '.data.user.username == $login' "$LAST_RESPONSE" >/dev/null || ops001_die "login principal does not match the approved synthetic identifier"
  printf 'OPS001_API|LOGIN|HTTP_%s\n' "$LAST_HTTP_STATUS"
}

rotate_owner_credential() {
  local body="$PRIVATE_ROOT/reset-owner-password.json"
  "$JQ_BIN" -c '{new_password: .new_login_password}' "$SECRET_INPUT" >"$body"; chmod 600 "$body"
  api_call credential_rotate POST "/admin/staff/$OWNER_USER_ID/reset-password" "$body" "$ACCESS_TOKEN"
  reject_secret_response_fields credential_rotate
  printf 'OPS001_API|OWNER_CREDENTIAL|ROTATED|HTTP_%s\n' "$LAST_HTTP_STATUS"
  logout
  login new_login_password
  verify_owner_context
}

logout() {
  local body="$PRIVATE_ROOT/logout.json"
  "$JQ_BIN" -n --arg refresh "$REFRESH_TOKEN" '{refresh_token: $refresh}' >"$body"; chmod 600 "$body"
  api_call logout POST /auth/logout "$body" "$ACCESS_TOKEN"
  ACCESS_TOKEN=""; REFRESH_TOKEN=""
  printf 'OPS001_API|LOGOUT|HTTP_%s\n' "$LAST_HTTP_STATUS"
}

verify_owner_context() {
  local label path refreshed_access_token
  for label in me workspaces overview; do
    case "$label" in me) path=/auth/me ;; workspaces) path=/me/workspaces ;; overview) path=/owner/overview ;; esac
    api_call "$label" GET "$path" "" "$ACCESS_TOKEN"
    [[ "$label" == "me" ]] || reject_secret_response_fields "$label"
    case "$label" in
      me)
        "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" '.data.user.role_code == "OWNER" and .data.user.organization_id == $organization' "$LAST_RESPONSE" >/dev/null || ops001_die "authenticated identity is not the approved Organization Owner"
        refreshed_access_token="$("$JQ_BIN" -er '.data.access_token | strings | select(length >= 24)' "$LAST_RESPONSE")" || ops001_die "me response did not return a valid access token"
        ACCESS_TOKEN="$refreshed_access_token"; refreshed_access_token=""
        ;;
      workspaces)
        "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" '.data.organizations | any(.id == $organization and .role_code == "OWNER")' "$LAST_RESPONSE" >/dev/null || ops001_die "workspace lacks approved Organization Owner access"
        if [[ "$ACTION" == "owner-login-acceptance" || "$ACTION" == "rotate-owner-credential" ]]; then
          "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" --argjson source "$SOURCE_STORE_ID" '.data.stores | type == "array" and length == 1 and .[0].id == $source and .[0].organization_id == $organization' "$LAST_RESPONSE" >/dev/null || ops001_die "workspace Store access is not exactly the approved synthetic source Store"
        elif [[ -n "$TARGET_STORE_ID" ]]; then
          "$JQ_BIN" -e --argjson target "$TARGET_STORE_ID" '.data.stores | any(.id == $target)' "$LAST_RESPONSE" >/dev/null || ops001_die "workspace lacks inherited target Store access"
        fi
        ;;
      overview)
        "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" '.data.organizations | any(.id == $organization and .role_code == "OWNER")' "$LAST_RESPONSE" >/dev/null || ops001_die "Owner overview lacks approved Organization"
        if [[ "$ACTION" == "owner-login-acceptance" || "$ACTION" == "rotate-owner-credential" ]]; then
          "$JQ_BIN" -e --argjson source "$SOURCE_STORE_ID" '[.data.organizations[].stores[]?] | type == "array" and length == 1 and .[0].id == $source' "$LAST_RESPONSE" >/dev/null || ops001_die "Owner overview Store access is not exactly the approved synthetic source Store"
        elif [[ -n "$TARGET_STORE_ID" ]]; then
          "$JQ_BIN" -e --argjson target "$TARGET_STORE_ID" '[.data.organizations[].stores[]?] | any(.id == $target)' "$LAST_RESPONSE" >/dev/null || ops001_die "Owner overview lacks target Store"
        fi
        ;;
    esac
    printf 'OPS001_API|%s|HTTP_%s\n' "$(printf '%s' "$label" | tr '[:lower:]' '[:upper:]')" "$LAST_HTTP_STATUS"
  done
}

prepare_target() {
  local body="$PRIVATE_ROOT/onboarding.json" key first_id first_status second_id replayed
  "$JQ_BIN" -c '.onboarding_request' "$SECRET_INPUT" >"$body"; chmod 600 "$body"
  key="$("$JQ_BIN" -er '.onboarding_idempotency_key' "$SECRET_INPUT")"
  api_call onboarding_first POST "/owner/organizations/$ORGANIZATION_ID/stores/onboard" "$body" "$ACCESS_TOKEN" "$key"
  reject_secret_response_fields onboarding_first
  first_id="$("$JQ_BIN" -er '.data.store_id | numbers' "$LAST_RESPONSE")"; first_status="$LAST_HTTP_STATUS"
  api_call onboarding_replay POST "/owner/organizations/$ORGANIZATION_ID/stores/onboard" "$body" "$ACCESS_TOKEN" "$key"
  reject_secret_response_fields onboarding_replay
  second_id="$("$JQ_BIN" -er '.data.store_id | numbers' "$LAST_RESPONSE")"; replayed="$("$JQ_BIN" -er '.data.replayed | select(. == true)' "$LAST_RESPONSE")"
  [[ "$first_id" == "$second_id" && "$replayed" == "true" ]] || ops001_die "onboarding replay contract failed"
  TARGET_STORE_ID="$first_id"
  verify_owner_context
  printf 'OPS001_API|ONBOARDING|HTTP_%s|TARGET_STORE_ID|%s|REPLAY|PASS\n' "$first_status" "$first_id"
}

clone_validation() {
  local request="$PRIVATE_ROOT/clone.json" valid
  "$JQ_BIN" -n --argjson source "$SOURCE_STORE_ID" --arg profile "$PROFILE_CODE" '{source_store_id: $source, profile_code: $profile}' >"$request"; chmod 600 "$request"
  api_call clone_validate POST "/owner/organizations/$ORGANIZATION_ID/stores/$TARGET_STORE_ID/menu-clone/validate" "$request" "$ACCESS_TOKEN"
  reject_secret_response_fields clone_validate
  valid="$("$JQ_BIN" -er '.data.valid | select(. == true)' "$LAST_RESPONSE")"; [[ "$valid" == "true" ]] || ops001_die "menu-clone validation did not pass"
  "$JQ_BIN" -e '.data.profile_code == "CHINATOWN_MENU_2026_02_02" and .data.expected.categories == 4 and .data.expected.stations == 3 and .data.expected.items == 17 and .data.expected.options == 74 and (.data.missing_codes | length == 0) and (.data.duplicate_codes | length == 0)' "$LAST_RESPONSE" >/dev/null || ops001_die "menu-clone validation counts/profile/diagnostics mismatch"
  printf 'OPS001_API|CLONE_VALIDATE|HTTP_%s|PASS\n' "$LAST_HTTP_STATUS"
}

clone_acceptance() {
  local request="$PRIVATE_ROOT/clone.json" key first_request replay_request replayed
  clone_validation
  key="$("$JQ_BIN" -er '.clone_idempotency_key' "$SECRET_INPUT")"
  api_call clone_execute POST "/owner/organizations/$ORGANIZATION_ID/stores/$TARGET_STORE_ID/menu-clone" "$request" "$ACCESS_TOKEN" "$key"
  reject_secret_response_fields clone_execute
  "$JQ_BIN" -e '.data.created.categories == 4 and .data.created.stations == 3 and .data.created.items == 17 and .data.created.options == 74 and .data.target_revision_after == (.data.target_revision_before + 1)' "$LAST_RESPONSE" >/dev/null || ops001_die "menu-clone execute counts or revision mismatch"
  first_request="$("$JQ_BIN" -er '.data.clone_request_id | numbers' "$LAST_RESPONSE")"
  api_call clone_replay POST "/owner/organizations/$ORGANIZATION_ID/stores/$TARGET_STORE_ID/menu-clone" "$request" "$ACCESS_TOKEN" "$key"
  reject_secret_response_fields clone_replay
  replay_request="$("$JQ_BIN" -er '.data.clone_request_id | numbers' "$LAST_RESPONSE")"; replayed="$("$JQ_BIN" -er '.data.replayed | select(. == true)' "$LAST_RESPONSE")"
  [[ "$first_request" == "$replay_request" && "$replayed" == "true" ]] || ops001_die "menu-clone replay contract failed"
  printf 'OPS001_API|CLONE_EXECUTE_REPLAY|HTTP_%s|REQUEST_ID|%s|PASS\n' "$LAST_HTTP_STATUS" "$first_request"
}

business_store_create_acceptance() {
  local request="$PRIVATE_ROOT/business-create.json" key first_id second_id replayed first_replayed manager_body manager_token manager_refresh logout_body request_digest request_fingerprint foreign_proof foreign_body foreign_after
  "$JQ_BIN" -c '.business_create_request' "$SECRET_INPUT" >"$request"; chmod 600 "$request"
  key="$("$JQ_BIN" -er '.business_create_idempotency_key' "$SECRET_INPUT")"

  foreign_body="$PRIVATE_ROOT/foreign-organization.json"
  "$JQ_BIN" -n --arg run "$ACCEPTANCE_RUN_ID" '{name:("STG005 Foreign Acceptance " + $run),code:("STG005_FOREIGN_" + ($run | ascii_upcase)),status:"active"}' >"$foreign_body"; chmod 600 "$foreign_body"
  api_call foreign_org_create POST /admin/platform/organizations "$foreign_body" "$ACCESS_TOKEN"
  FOREIGN_ORGANIZATION_ID="$("$JQ_BIN" -er '.data.id | numbers' "$LAST_RESPONSE")"
  FOREIGN_CLEANUP_REQUIRED="true"
  "$JQ_BIN" -e --argjson foreign "$FOREIGN_ORGANIZATION_ID" '.data.id == $foreign and (.data.status | ascii_downcase) == "active"' "$LAST_RESPONSE" >/dev/null || ops001_die "synthetic foreign Organization creation did not return active authority"

  api_call business_catalog GET "/owner/organizations/$ORGANIZATION_ID/stores/create-catalog" "" "$ACCESS_TOKEN"
  "$JQ_BIN" -e '.data.enabled == true' "$LAST_RESPONSE" >/dev/null || ops001_die "business Store catalog is not enabled"
  api_call business_create POST "/owner/organizations/$ORGANIZATION_ID/stores" "$request" "$ACCESS_TOKEN" "$key"
  reject_secret_response_fields business_create
  "$JQ_BIN" -e '.data.replayed == false and .data.store_kind == "BUSINESS" and .data.store_status == "active" and .data.lifecycle_status == "ACTIVE" and .data.operational_state == "LIVE" and .data.is_live == true and .data.validation_status == "PASS"' "$LAST_RESPONSE" >/dev/null || ops001_die "business Store first request was not a fresh canonical LIVE creation"
  first_replayed="false"
  first_id="$("$JQ_BIN" -er '.data.store_id | numbers' "$LAST_RESPONSE")"
  api_call business_replay POST "/owner/organizations/$ORGANIZATION_ID/stores" "$request" "$ACCESS_TOKEN" "$key"
  second_id="$("$JQ_BIN" -er '.data.store_id | numbers' "$LAST_RESPONSE")"; replayed="$("$JQ_BIN" -er '.data.replayed | select(. == true)' "$LAST_RESPONSE")"
  [[ "$first_id" == "$second_id" && "$replayed" == "true" ]] || ops001_die "business Store create replay contract failed"
  BUSINESS_STORE_ID="$first_id"

  api_call business_context GET "/stores/$BUSINESS_STORE_ID/context" "" "$ACCESS_TOKEN"
  "$JQ_BIN" -e --argjson store "$BUSINESS_STORE_ID" '.data.id == $store and .data.operational_state == "LIVE" and .data.is_live == true' "$LAST_RESPONSE" >/dev/null || ops001_die "new business Store context is not immediately LIVE"
  api_call business_frontdesk GET "/frontdesk/dining-tables?store_id=$BUSINESS_STORE_ID" "" "$ACCESS_TOKEN"
  "$JQ_BIN" -e '.data | type == "array" and length >= 2' "$LAST_RESPONSE" >/dev/null || ops001_die "new business Store lacks default Frontdesk tables"
  api_call business_printing GET "/admin/printing?store_id=$BUSINESS_STORE_ID" "" "$ACCESS_TOKEN"
  "$JQ_BIN" -e --argjson store "$BUSINESS_STORE_ID" '.data.store_id == $store and (.data.printing_mode == "DISABLED" or .data.printing_mode == "MOCK") and (.data.printers | type == "array" and all(.[]; ((.ip_address // "") | length) == 0))' "$LAST_RESPONSE" >/dev/null || ops001_die "new business Store Printing Management is not endpoint-free DISABLED/MOCK"
  api_call business_overview GET /owner/overview "" "$ACCESS_TOKEN"
  "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" --argjson store "$BUSINESS_STORE_ID" '.data.organizations | any(.id == $organization and .can_create_store == true and (.stores | any(.id == $store and .operational_state == "LIVE" and .is_live == true)))' "$LAST_RESPONSE" >/dev/null || ops001_die "Owner overview does not expose canonical create/LIVE state"

  foreign_proof="$(printf '%s\n' "select (select count(*) from organizations where id = :'foreign' and lower(status) = 'active') || chr(124) || (select count(*) from organization_memberships where organization_id = :'foreign' and user_id = :'owner' and is_active = true);" | "$DOCKER_BIN" --context default exec -i restaurant-pos-staging-db-1 sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v foreign="$1" -v owner="$2"' sh "$FOREIGN_ORGANIZATION_ID" "$OWNER_USER_ID")" || ops001_die "foreign Organization authority proof query failed"
  [[ "$foreign_proof" == "1|0" ]] || ops001_die "foreign Organization must exist, be active, and have no active membership for this Owner"
  api_expect_error business_wrong_org POST "/owner/organizations/$FOREIGN_ORGANIZATION_ID/stores" "$request" "$ACCESS_TOKEN" "${key}-foreign-org" 403 BUSINESS_STORE_CREATE_ORGANIZATION_DENIED

  "$JQ_BIN" --argjson id "$FOREIGN_ORGANIZATION_ID" '.id=$id | .status="inactive"' "$foreign_body" >"$PRIVATE_ROOT/foreign-organization-inactive.json"; chmod 600 "$PRIVATE_ROOT/foreign-organization-inactive.json"
  api_call foreign_org_deactivate PUT "/admin/platform/organizations/$FOREIGN_ORGANIZATION_ID" "$PRIVATE_ROOT/foreign-organization-inactive.json" "$ACCESS_TOKEN"
  foreign_after="$(printf '%s\n' "select count(*) from organizations where id = :'foreign' and lower(status) = 'active';" | "$DOCKER_BIN" --context default exec -i restaurant-pos-staging-db-1 sh -eu -c 'psql -qX -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v foreign="$1"' sh "$FOREIGN_ORGANIZATION_ID")" || ops001_die "foreign Organization deactivation proof query failed"
  [[ "$foreign_after" == "0" ]] || ops001_die "synthetic foreign Organization remained active after authority test"
  FOREIGN_CLEANUP_REQUIRED="false"

  manager_body="$PRIVATE_ROOT/manager-login.json"
  "$JQ_BIN" -c '{login_identifier: .manager_login_identifier, password: .manager_login_password}' "$SECRET_INPUT" >"$manager_body"; chmod 600 "$manager_body"
  api_call manager_login POST /auth/login "$manager_body"
  manager_token="$("$JQ_BIN" -er '.data.access_token | strings | select(length > 20)' "$LAST_RESPONSE")"
  manager_refresh="$("$JQ_BIN" -er '.data.refresh_token | strings | select(length > 20)' "$LAST_RESPONSE")"
  "$JQ_BIN" -e '.data.user.role_code == "MANAGER"' "$LAST_RESPONSE" >/dev/null || ops001_die "negative authority principal is not Manager-shaped"
  api_expect_error business_manager_denied POST "/owner/organizations/$ORGANIZATION_ID/stores" "$request" "$manager_token" "${key}-manager" 403 BUSINESS_STORE_CREATE_AUTHORIZATION_DENIED
  logout_body="$PRIVATE_ROOT/manager-logout.json"; "$JQ_BIN" -n --arg refresh "$manager_refresh" '{refresh_token: $refresh}' >"$logout_body"; chmod 600 "$logout_body"
  api_call manager_logout POST /auth/logout "$logout_body" "$manager_token"
  manager_token=""; manager_refresh=""
  [[ "$first_replayed" == "false" ]] || ops001_die "fresh-create proof was lost"
  request_digest="$(ops001_file_digest "$request")"
  ACCEPTED_BACKEND_IMAGE_ID="$("$DOCKER_BIN" --context default inspect --format '{{.Image}}' restaurant-pos-staging-backend-1)"
  ACCEPTED_FRONTEND_IMAGE_ID="$("$DOCKER_BIN" --context default inspect --format '{{.Image}}' restaurant-pos-staging-nginx-1)"
  [[ "$ACCEPTED_BACKEND_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ && "$ACCEPTED_FRONTEND_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] || ops001_die "accepted Staging immutable image identity is invalid"
  request_fingerprint="$(ops001_request_fingerprint "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$(client_scope)")"
  ACCEPTANCE_EVIDENCE="$OPS001_EXPECTED_ROOT/evidence/v26-business-create-$ACCEPTANCE_RUN_ID.json"
  [[ -d "$OPS001_EXPECTED_ROOT/evidence" && ! -L "$OPS001_EXPECTED_ROOT/evidence" && "$(ops001_canonical_dir "$OPS001_EXPECTED_ROOT/evidence")" == "$OPS001_EXPECTED_ROOT/evidence" && "$(ops001_file_owner "$OPS001_EXPECTED_ROOT/evidence")" == "$(id -u)" ]] || ops001_die "acceptance evidence root is unsafe"
  [[ ! -e "$ACCEPTANCE_EVIDENCE" && ! -L "$ACCEPTANCE_EVIDENCE" ]] || ops001_die "acceptance evidence path already exists"
  umask 077
  ( set -o noclobber; "$JQ_BIN" -n \
    --arg schema "V26_BUSINESS_STORE_CREATE_ACCEPTANCE_V1" --arg run "$ACCEPTANCE_RUN_ID" --arg sha "$APPROVED_SHA" \
    --arg env "$ENV_DIGEST" --arg preflight "$PREFLIGHT_EVIDENCE_SHA256" --arg approval "$APPROVAL_SHA256" \
    --arg fingerprint "$request_fingerprint" --arg request "$request_digest" --arg backend "$ACCEPTED_BACKEND_IMAGE_ID" --arg frontend "$ACCEPTED_FRONTEND_IMAGE_ID" --argjson organization "$ORGANIZATION_ID" \
    --argjson foreign "$FOREIGN_ORGANIZATION_ID" --argjson store "$BUSINESS_STORE_ID" \
    '{schema:$schema,run_id:$run,source_sha:$sha,backend_image_id:$backend,frontend_image_id:$frontend,environment_sha256:$env,runtime_preflight_sha256:$preflight,owner_approval_sha256:$approval,request_fingerprint:$fingerprint,request_body_sha256:$request,organization_id:$organization,foreign_organization_id:$foreign,store_id:$store,fresh_create:"PASS",replay:"PASS",foreign_active_organization_denied:"PASS",manager_denied:"PASS",live_context:"PASS",frontdesk_defaults:"PASS",printing_management:"PASS",printing_endpoint_free:"PASS",final_result:"PASS"}' >"$ACCEPTANCE_EVIDENCE" ) || ops001_die "acceptance evidence creation failed"
  chmod 600 "$ACCEPTANCE_EVIDENCE"
  printf 'OPS001_API|BUSINESS_STORE_CREATE|RUN_ID|%s|STORE_ID|%s|FRESH_CREATE|PASS|REPLAY|PASS|FOREIGN_ACTIVE_ORG|DENIED|MANAGER|DENIED|PRINTING|ENDPOINT_FREE\n' "$ACCEPTANCE_RUN_ID" "$BUSINESS_STORE_ID"
  printf 'OPS001_API|BUSINESS_STORE_CREATE_EVIDENCE|SHA256|%s|FINAL|PASS\n' "$(ops001_file_digest "$ACCEPTANCE_EVIDENCE")"
}

run_action() {
  acquire_action_lock
  validate_exact_runtime_target
  ops001_validate_approval "$APPROVAL_FILE" "$APPROVAL_SHA256" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$(client_scope)"
  OPS001_APPROVAL_FILE="$APPROVAL_FILE"; ops001_assert_approval_unchanged; ops001_consume_approval
  initialize_private_root; read_secret_input; login; verify_owner_context
  case "$ACTION" in owner-login-acceptance) ;; rotate-owner-credential) rotate_owner_credential ;; prepare-target) prepare_target; clone_validation ;; clone-acceptance) clone_acceptance ;; business-store-create-acceptance) business_store_create_acceptance ;; esac
  logout
  printf 'OPS001_API|%s|PASS\n' "$(printf '%s' "$ACTION" | tr '[:lower:]' '[:upper:]')"
}

main() {
  local seen="|"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate) ACTION="validate" ;;
      --execute-runtime) EXECUTE_RUNTIME="true" ;;
      --action|--approved-sha|--env-file|--preflight-evidence|--preflight-evidence-sha256|--approval|--approval-sha256|--secrets-fd|--organization-id|--acceptance-run-id|--target-store-id|--source-store-id|--profile-code|--owner-login-identifier)
        [[ $# -ge 2 && "$seen" != *"|$1|"* ]] || ops001_die "$1 requires one value and may appear once"
        seen="${seen}${1}|"
        case "$1" in --action) ACTION="$2" ;; --approved-sha) APPROVED_SHA="$2" ;; --env-file) ENV_FILE="$2" ;; --preflight-evidence) PREFLIGHT_EVIDENCE="$2" ;; --preflight-evidence-sha256) PREFLIGHT_EVIDENCE_SHA256="$2" ;; --approval) APPROVAL_FILE="$2" ;; --approval-sha256) APPROVAL_SHA256="$2" ;; --secrets-fd) SECRETS_FD="$2" ;; --organization-id) ORGANIZATION_ID="$2" ;; --acceptance-run-id) ACCEPTANCE_RUN_ID="$2" ;; --target-store-id) TARGET_STORE_ID="$2" ;; --source-store-id) SOURCE_STORE_ID="$2" ;; --profile-code) PROFILE_CODE="$2" ;; --owner-login-identifier) APPROVED_LOGIN_IDENTIFIER="$2" ;; esac
        shift ;;
      --help|-h) usage; exit 0 ;;
      *) ops001_die "unsupported option: $1" ;;
    esac
    shift
  done
  [[ -n "$APPROVED_SHA$ENV_FILE" ]] || ops001_die "approved SHA and environment file are required"
  CURL_BIN="$(command -v curl || true)"; JQ_BIN="$(command -v jq || true)"
  DOCKER_BIN="$(command -v docker || true)"
  if [[ -z "$JQ_BIN" && ( "$ACTION" == "owner-login-acceptance" || "$ACTION" == "rotate-owner-credential" || "$ACTION" == "business-store-create-acceptance" ) ]]; then
    JQ_BIN="$SCRIPT_DIR/ops001-jq-compat.py"
  fi
  [[ "$CURL_BIN" == /* && "$JQ_BIN" == /* && -x "$JQ_BIN" && "$DOCKER_BIN" == /* ]] || ops001_die "curl, Docker, and jq-compatible parser are required"
  validate_release_and_env
  if [[ "$ACTION" == "validate" ]]; then
    [[ "$EXECUTE_RUNTIME" == "false" && -z "$APPROVAL_FILE$APPROVAL_SHA256$SECRETS_FD" ]] || ops001_die "validation accepts no runtime, approval, or secret input"
    printf 'OPS001_API|VALIDATE|PASS|no login or API request executed\n'; return
  fi
  [[ "$ACTION" == "owner-login-acceptance" || "$ACTION" == "rotate-owner-credential" || "$ACTION" == "prepare-target" || "$ACTION" == "clone-acceptance" || "$ACTION" == "business-store-create-acceptance" ]] || ops001_die "unsupported action: $ACTION"
  [[ "$EXECUTE_RUNTIME" == "true" ]] || ops001_die "$ACTION requires --execute-runtime"
  [[ "$ORGANIZATION_ID" =~ ^[1-9][0-9]*$ ]] || ops001_die "organization ID must be positive"
  if [[ "$ACTION" == "business-store-create-acceptance" ]]; then
    [[ "$ACCEPTANCE_RUN_ID" =~ ^[0-9a-f]{32}$ ]] || ops001_die "business acceptance requires a 32-hex run ID"
  else
    [[ -z "$ACCEPTANCE_RUN_ID" ]] || ops001_die "run binding is accepted only for business Store acceptance"
  fi
  [[ "$SOURCE_STORE_ID" == "1" && "$PROFILE_CODE" == "CHINATOWN_MENU_2026_02_02" ]] || ops001_die "Owner acceptance requires reviewed source/profile bindings"
  if [[ "$ACTION" == "rotate-owner-credential" ]]; then
    [[ "$APPROVED_LOGIN_IDENTIFIER" == "owner" || "$APPROVED_LOGIN_IDENTIFIER" =~ ^STG005_[A-Z0-9_]{1,96}$ ]] || ops001_die "credential rotation requires an approved retained synthetic Owner login identifier"
  else
    [[ -z "$APPROVED_LOGIN_IDENTIFIER" ]] || ops001_die "Owner login identifier binding is accepted only for credential rotation"
  fi
  if [[ "$ACTION" == "clone-acceptance" ]]; then
    [[ "$TARGET_STORE_ID" =~ ^[1-9][0-9]*$ ]] || ops001_die "clone acceptance requires reviewed target binding"
  else
    [[ -z "$TARGET_STORE_ID" ]] || ops001_die "target Store ID is not accepted before onboarding"
  fi
  [[ -n "$PREFLIGHT_EVIDENCE" && "$PREFLIGHT_EVIDENCE_SHA256" =~ ^[0-9a-f]{64}$ ]] || ops001_die "$ACTION requires exact preflight evidence and digest"
  [[ -n "$APPROVAL_FILE" && "$APPROVAL_SHA256" =~ ^[0-9a-f]{64}$ && -n "$SECRETS_FD" && "$SECRETS_FD" != "9" ]] || ops001_die "$ACTION requires Owner approval and inherited secret FD other than reserved lock FD 9"
  FLOCK_BIN="$(command -v flock || true)"
  [[ "$FLOCK_BIN" == /* && -x "$FLOCK_BIN" ]] || ops001_die "flock is required for the shared Staging action lock"
  run_action
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then trap cleanup EXIT ERR INT TERM; main "$@"; fi
