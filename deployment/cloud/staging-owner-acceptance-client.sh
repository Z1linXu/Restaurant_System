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
ORGANIZATION_ID=""
TARGET_STORE_ID=""
SOURCE_STORE_ID="1"
PROFILE_CODE="CHINATOWN_MENU_2026_02_02"
API_BASE="http://127.0.0.1:18080/api/v1"
PRIVATE_ROOT=""
SECRET_INPUT=""
ACCESS_TOKEN=""
REFRESH_TOKEN=""
LAST_HTTP_STATUS=""
LAST_RESPONSE=""
CURL_BIN=""
JQ_BIN=""

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
  staging-owner-acceptance-client.sh --execute-runtime --action clone-acceptance \
    --approved-sha <full-sha> --env-file <fixed-env> --preflight-evidence <file> \
    --preflight-evidence-sha256 <sha256> --organization-id <id> \
    --target-store-id <id> --source-store-id 1 --profile-code CHINATOWN_MENU_2026_02_02 \
    --approval <file> --approval-sha256 <sha256> --secrets-fd <open-fd>

The inherited FD contains one JSON object. Every action requires
login_identifier/login_password. owner-login-acceptance performs only login,
exact Owner/workspace/dashboard access verification, and logout; it creates no
Store, staff, credential, menu, request, or other business data.
prepare-target additionally requires
onboarding_idempotency_key and onboarding_request. clone-acceptance requires
clone_idempotency_key. Secret values are forbidden in argv/environment/output.
The client uses only loopback HTTP, no redirects/proxy, private mode-0600
request/response/config files, disables ambient curl configuration, validates
the exact running image through the OPS-001 runtime gate, and reuses the
existing API contracts.
EOF
}

cleanup() {
  local status=$?
  if [[ -n "$ACCESS_TOKEN" && -n "$REFRESH_TOKEN" && -d "$PRIVATE_ROOT" && -x "$CURL_BIN" && -x "$JQ_BIN" ]]; then
    local cleanup_body="$PRIVATE_ROOT/cleanup-logout.json" cleanup_config="$PRIVATE_ROOT/cleanup-logout.curl" cleanup_response="$PRIVATE_ROOT/cleanup-logout.response"
    "$JQ_BIN" -n --arg refresh "$REFRESH_TOKEN" '{refresh_token: $refresh}' >"$cleanup_body" 2>/dev/null || true
    chmod 600 "$cleanup_body" 2>/dev/null || true
    write_curl_config "$cleanup_config" "$ACCESS_TOKEN" "" 2>/dev/null || true
    "$CURL_BIN" -q --config "$cleanup_config" --request POST --output "$cleanup_response" --max-time 15 --data-binary "@$cleanup_body" "$API_BASE/auth/logout" >/dev/null 2>&1 || true
  fi
  ACCESS_TOKEN=""; REFRESH_TOKEN=""
  if [[ -n "$PRIVATE_ROOT" && "$PRIVATE_ROOT" == "${TMPDIR:-/tmp}"/restaurant-pos-ops001-api.* ]]; then rm -rf -- "$PRIVATE_ROOT"; fi
  PRIVATE_ROOT=""; SECRET_INPUT=""; LAST_RESPONSE=""
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
  case "$ACTION" in
    prepare-target)
      "$JQ_BIN" -e '(.onboarding_idempotency_key | type == "string" and length >= 16) and (.onboarding_request | type == "object") and (.onboarding_request.source_store_id == 1) and (.onboarding_request.store_code | type == "string" and startswith("STG005_")) and (.onboarding_request.staff | type == "array" and length > 0) and all(.onboarding_request.staff[]; (.login_identifier | type == "string" and startswith("STG005_")) and (.role_code == "MANAGER" or .role_code == "FRONTDESK") and (.initial_password | type == "string" and length >= 12))' "$SECRET_INPUT" >/dev/null || ops001_die "prepare-target secret payload is incomplete or outside the synthetic contract"
      ;;
    clone-acceptance)
      "$JQ_BIN" -e '(.clone_idempotency_key | type == "string" and length >= 16)' "$SECRET_INPUT" >/dev/null || ops001_die "clone-acceptance secret payload is incomplete"
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
  [[ "$path" == /* && "$path" != *'..'* && "$path" =~ ^/[A-Za-z0-9_./-]+$ ]] || ops001_die "unsafe API path"
  [[ "$method" == "GET" || "$method" == "POST" ]] || ops001_die "unsupported HTTP method"
  [[ -z "$idempotency" || "$idempotency" =~ ^[A-Za-z0-9._:-]{16,255}$ ]] || ops001_die "idempotency key has unsafe characters or length"
  write_curl_config "$config" "$token" "$idempotency"
  local -a args=(-q --config "$config" --request "$method" --output "$response" --write-out '%{http_code}' "$API_BASE$path")
  [[ -z "$body" ]] || args+=(--data-binary "@$body")
  if ! "$CURL_BIN" "${args[@]}" >"$status_file"; then ops001_die "$label API request failed"; fi
  LAST_HTTP_STATUS="$(cat "$status_file")"; LAST_RESPONSE="$response"
  [[ "$LAST_HTTP_STATUS" =~ ^2[0-9][0-9]$ ]] || ops001_die "$label returned HTTP $LAST_HTTP_STATUS"
  "$JQ_BIN" -e '.success == true' "$response" >/dev/null || ops001_die "$label returned invalid or unsuccessful JSON"
}

reject_secret_response_fields() {
  local label="$1"
  "$JQ_BIN" -e '[paths(scalars) as $p | ($p[-1] | tostring | ascii_downcase) | select(test("password|token|cookie|authorization|secret"))] | length == 0' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "$label response contains a forbidden secret-shaped field"
}

login() {
  local body="$PRIVATE_ROOT/login.json"
  "$JQ_BIN" -c '{login_identifier: .login_identifier, password: .login_password}' "$SECRET_INPUT" >"$body"; chmod 600 "$body"
  api_call login POST /auth/login "$body"
  ACCESS_TOKEN="$("$JQ_BIN" -er '.data.access_token | select(type == "string" and length > 20)' "$LAST_RESPONSE")" || ops001_die "login response lacks access token"
  REFRESH_TOKEN="$("$JQ_BIN" -er '.data.refresh_token | select(type == "string" and length > 20)' "$LAST_RESPONSE")" || ops001_die "login response lacks refresh token"
  "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" '.data.user.role_code == "OWNER" and .data.user.organization_id == $organization' "$LAST_RESPONSE" >/dev/null || ops001_die "login identity is not the approved Organization Owner"
  printf 'OPS001_API|LOGIN|HTTP_%s\n' "$LAST_HTTP_STATUS"
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
        if [[ "$ACTION" == "owner-login-acceptance" ]]; then
          "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" --argjson source "$SOURCE_STORE_ID" '.data.stores | type == "array" and length == 1 and .[0].id == $source and .[0].organization_id == $organization' "$LAST_RESPONSE" >/dev/null || ops001_die "workspace Store access is not exactly the approved synthetic source Store"
        elif [[ -n "$TARGET_STORE_ID" ]]; then
          "$JQ_BIN" -e --argjson target "$TARGET_STORE_ID" '.data.stores | any(.id == $target)' "$LAST_RESPONSE" >/dev/null || ops001_die "workspace lacks inherited target Store access"
        fi
        ;;
      overview)
        "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" '.data.organizations | any(.id == $organization and .role_code == "OWNER")' "$LAST_RESPONSE" >/dev/null || ops001_die "Owner overview lacks approved Organization"
        if [[ "$ACTION" == "owner-login-acceptance" ]]; then
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

run_action() {
  acquire_action_lock
  validate_exact_runtime_target
  ops001_validate_approval "$APPROVAL_FILE" "$APPROVAL_SHA256" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$(client_scope)"
  OPS001_APPROVAL_FILE="$APPROVAL_FILE"; ops001_assert_approval_unchanged; ops001_consume_approval
  initialize_private_root; read_secret_input; login; verify_owner_context
  case "$ACTION" in owner-login-acceptance) ;; prepare-target) prepare_target; clone_validation ;; clone-acceptance) clone_acceptance ;; esac
  logout
  printf 'OPS001_API|%s|PASS\n' "$(printf '%s' "$ACTION" | tr '[:lower:]' '[:upper:]')"
}

main() {
  local seen="|"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate) ACTION="validate" ;;
      --execute-runtime) EXECUTE_RUNTIME="true" ;;
      --action|--approved-sha|--env-file|--preflight-evidence|--preflight-evidence-sha256|--approval|--approval-sha256|--secrets-fd|--organization-id|--target-store-id|--source-store-id|--profile-code)
        [[ $# -ge 2 && "$seen" != *"|$1|"* ]] || ops001_die "$1 requires one value and may appear once"
        seen="${seen}${1}|"
        case "$1" in --action) ACTION="$2" ;; --approved-sha) APPROVED_SHA="$2" ;; --env-file) ENV_FILE="$2" ;; --preflight-evidence) PREFLIGHT_EVIDENCE="$2" ;; --preflight-evidence-sha256) PREFLIGHT_EVIDENCE_SHA256="$2" ;; --approval) APPROVAL_FILE="$2" ;; --approval-sha256) APPROVAL_SHA256="$2" ;; --secrets-fd) SECRETS_FD="$2" ;; --organization-id) ORGANIZATION_ID="$2" ;; --target-store-id) TARGET_STORE_ID="$2" ;; --source-store-id) SOURCE_STORE_ID="$2" ;; --profile-code) PROFILE_CODE="$2" ;; esac
        shift ;;
      --help|-h) usage; exit 0 ;;
      *) ops001_die "unsupported option: $1" ;;
    esac
    shift
  done
  [[ -n "$APPROVED_SHA$ENV_FILE" ]] || ops001_die "approved SHA and environment file are required"
  CURL_BIN="$(command -v curl || true)"; JQ_BIN="$(command -v jq || true)"
  if [[ -z "$JQ_BIN" && "$ACTION" == "owner-login-acceptance" ]]; then
    JQ_BIN="$SCRIPT_DIR/ops001-jq-compat.py"
  fi
  [[ "$CURL_BIN" == /* && "$JQ_BIN" == /* && -x "$JQ_BIN" ]] || ops001_die "curl and jq-compatible parser are required"
  validate_release_and_env
  if [[ "$ACTION" == "validate" ]]; then
    [[ "$EXECUTE_RUNTIME" == "false" && -z "$APPROVAL_FILE$APPROVAL_SHA256$SECRETS_FD" ]] || ops001_die "validation accepts no runtime, approval, or secret input"
    printf 'OPS001_API|VALIDATE|PASS|no login or API request executed\n'; return
  fi
  [[ "$ACTION" == "owner-login-acceptance" || "$ACTION" == "prepare-target" || "$ACTION" == "clone-acceptance" ]] || ops001_die "unsupported action: $ACTION"
  [[ "$EXECUTE_RUNTIME" == "true" ]] || ops001_die "$ACTION requires --execute-runtime"
  [[ "$ORGANIZATION_ID" =~ ^[1-9][0-9]*$ ]] || ops001_die "organization ID must be positive"
  [[ "$SOURCE_STORE_ID" == "1" && "$PROFILE_CODE" == "CHINATOWN_MENU_2026_02_02" ]] || ops001_die "Owner acceptance requires reviewed source/profile bindings"
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
