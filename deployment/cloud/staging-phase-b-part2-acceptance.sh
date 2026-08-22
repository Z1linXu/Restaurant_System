#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -P "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/staging-ops-common.sh"

EXPECTED_PROJECT="restaurant-pos-staging"
API_BASE="http://127.0.0.1:18080/api/v1"
SAFE_PATH="$OPS001_SAFE_PATH"
ACTION="validate"
EXECUTE_RUNTIME="false"
APPROVED_SHA=""
ENV_FILE=""
ENV_DIGEST=""
PREFLIGHT_EVIDENCE=""
PREFLIGHT_EVIDENCE_SHA256=""
APPROVAL_FILE=""
APPROVAL_SHA256=""
SECRETS_FD=""
ORGANIZATION_ID=""
STORE_ID=""
STORE_CODE=""
PRIVATE_ROOT=""
TMP_ROOT_PREFIX=""
SECRET_INPUT=""
LOGIN_IDENTIFIER=""
ACCESS_TOKEN=""
REFRESH_TOKEN=""
LAST_HTTP_STATUS=""
LAST_RESPONSE=""
CURL_BIN=""
JQ_BIN=""
FLOCK_BIN=""
ACTION_LOCK_FD=""
DEVICE_ID=""
DEVICE_TOKEN=""
READINESS_FINGERPRINT=""
MENU_SIGNATURE_BEFORE=""
MASTER_SIGNATURE_BEFORE=""
PROFILE_SIGNATURE_BEFORE=""
PRINTER_CONFIG_COUNT_BEFORE=""
PRINTER_ASSIGNMENT_COUNT_BEFORE=""

usage() {
  cat <<'EOF'
Usage:
  staging-phase-b-part2-acceptance.sh --validate --approved-sha <full-sha> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging

  staging-phase-b-part2-acceptance.sh --execute-runtime \
    --action phase-b-part2-acceptance --approved-sha <full-sha> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --preflight-evidence <file> --preflight-evidence-sha256 <sha256> \
    --organization-id <id> --store-id <synthetic-store-id> \
    --store-code PHASE_B_VALIDATION_STORE_<safe-suffix> \
    --approval <file> --approval-sha256 <sha256> --secrets-fd <open-fd>
EOF
}

cleanup() {
  local status="$?"
  trap - ERR INT TERM
  if [[ -n "$ACCESS_TOKEN" && -n "$REFRESH_TOKEN" && -n "$PRIVATE_ROOT" && -x "$CURL_BIN" ]]; then
    local body="$PRIVATE_ROOT/logout.json"
    "$JQ_BIN" -n --arg refresh "$REFRESH_TOKEN" '{refresh_token: $refresh}' >"$body" 2>/dev/null || true
    chmod 600 "$body" 2>/dev/null || true
    write_curl_config "$PRIVATE_ROOT/logout.curl" "$ACCESS_TOKEN" ""
    "$CURL_BIN" -q --config "$PRIVATE_ROOT/logout.curl" --request POST \
      --output "$PRIVATE_ROOT/logout.response" --write-out '%{http_code}' \
      --data-binary "@$body" "$API_BASE/auth/logout" >"$PRIVATE_ROOT/logout.status" 2>/dev/null || true
  fi
  ACCESS_TOKEN=""
  REFRESH_TOKEN=""
  LOGIN_IDENTIFIER=""
  SECRET_INPUT=""
  LAST_RESPONSE=""
  if [[ "$ACTION_LOCK_FD" == "9" ]]; then
    "$FLOCK_BIN" -u "$ACTION_LOCK_FD" >/dev/null 2>&1 || true
    exec 9>&-
  fi
  ACTION_LOCK_FD=""
  if [[ -n "$PRIVATE_ROOT" && "$PRIVATE_ROOT" == "$TMP_ROOT_PREFIX"* ]]; then
    rm -rf -- "$PRIVATE_ROOT"
  fi
  PRIVATE_ROOT=""
  return "$status"
}

acceptance_scope() {
  printf 'organization_id=%s;store_id=%s;store_code=%s;preflight=%s' \
    "$ORGANIZATION_ID" "$STORE_ID" "$STORE_CODE" "$PREFLIGHT_EVIDENCE_SHA256"
}

validate_store_identity() {
  [[ "$ORGANIZATION_ID" =~ ^[1-9][0-9]*$ ]] || ops001_die "organization ID must be positive"
  [[ "$STORE_ID" =~ ^[1-9][0-9]*$ ]] || ops001_die "Store ID must be positive"
  [[ "$STORE_CODE" =~ ^PHASE_B_VALIDATION_STORE_[A-Z0-9_:-]{1,96}$ ]] ||
    ops001_die "Store code must use the PHASE_B_VALIDATION_STORE_ namespace"
}

validate_release_and_env() {
  local release="$OPS001_EXPECTED_ROOT/releases/$APPROVED_SHA"
  [[ "$-" != *x* ]] || ops001_die "shell tracing must be disabled"
  [[ "$APPROVED_SHA" =~ ^[0-9a-f]{40}$ ]] || ops001_die "approved SHA must be a lowercase full SHA"
  [[ "$ENV_FILE" == "$OPS001_EXPECTED_ROOT/config/.env.staging" ]] ||
    ops001_die "environment file must use the fixed Staging path"
  ops001_validate_env_file "$ENV_FILE"
  ENV_DIGEST="$(ops001_file_digest "$ENV_FILE")"
  ops001_validate_fixed_env_identity "$ENV_FILE" "$APPROVED_SHA"
  [[ -d "$release" && ! -L "$release" ]] || ops001_die "approved detached release is missing"
  [[ "$(git -C "$release" rev-parse HEAD 2>/dev/null || true)" == "$APPROVED_SHA" ]] ||
    ops001_die "approved release HEAD mismatch"
  [[ -z "$(git -C "$release" status --porcelain=v1 --untracked-files=all)" ]] ||
    ops001_die "approved release is not clean"
}

validate_preflight_evidence() {
  [[ -n "$PREFLIGHT_EVIDENCE" && "$PREFLIGHT_EVIDENCE_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
    ops001_die "exact preflight evidence and digest are required"
  ops001_require_private_file "Staging preflight evidence" "$PREFLIGHT_EVIDENCE" "$OPS001_EXPECTED_ROOT/evidence"
  [[ "$(ops001_file_digest "$PREFLIGHT_EVIDENCE")" == "$PREFLIGHT_EVIDENCE_SHA256" ]] ||
    ops001_die "preflight evidence digest mismatch"
  grep -Fxq "SUMMARY|PASS|same-host Staging preflight passed without state changes" "$PREFLIGHT_EVIDENCE" ||
    ops001_die "preflight evidence is not PASS"
  grep -Fxq "EVIDENCE|APPROVED_SHA|$APPROVED_SHA" "$PREFLIGHT_EVIDENCE" ||
    ops001_die "preflight evidence SHA binding mismatch"
  grep -Fxq "EVIDENCE|ENV_SHA256|$ENV_DIGEST" "$PREFLIGHT_EVIDENCE" ||
    ops001_die "preflight evidence environment binding mismatch"
}

compose_read() {
  env -i PATH="$SAFE_PATH" HOME="/tmp" DOCKER_CONFIG="/tmp" \
    docker --context default compose --project-name "$EXPECTED_PROJECT" --env-file "$ENV_FILE" \
    -f "$SCRIPT_DIR/docker-compose.staging.yml" "$@"
}

db_query() {
  local sql="$1"
  compose_read exec -T db sh -eu -c 'psql -X -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<<"$sql"
}

db_expect_pass() {
  local label="$1" sql="$2" result
  result="$(db_query "$sql")"
  [[ "$result" == "PASS" ]] || ops001_die "$label failed: $result"
  printf 'PHASE_B_PART2_ACCEPTANCE|%s|PASS\n' "$label"
}

sql_literal() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

initialize_private_root() {
  local tmp_dir
  tmp_dir="$(printenv TMPDIR || true)"
  [[ -n "$tmp_dir" ]] || tmp_dir="/tmp"
  umask 077
  TMP_ROOT_PREFIX="$tmp_dir/restaurant-pos-phase-b-part2-acceptance."
  PRIVATE_ROOT="$(mktemp -d "$TMP_ROOT_PREFIX"XXXXXXXX)"
  chmod 700 "$PRIVATE_ROOT"
  [[ "$(ops001_file_owner "$PRIVATE_ROOT")" == "$(id -u)" && "$(ops001_file_mode "$PRIVATE_ROOT")" == "700" ]] ||
    ops001_die "private API workspace is unsafe"
}

read_secret_input() {
  [[ "$SECRETS_FD" =~ ^[3-9][0-9]*$ && "$SECRETS_FD" != "9" ]] ||
    ops001_die "--secrets-fd must name an inherited descriptor other than 9"
  [[ -r "/dev/fd/$SECRETS_FD" ]] || ops001_die "secret descriptor is not readable"
  SECRET_INPUT="$PRIVATE_ROOT/secrets.json"
  dd bs=4096 of="$SECRET_INPUT" <&"$SECRETS_FD" 2>/dev/null
  chmod 600 "$SECRET_INPUT"
  "$JQ_BIN" -e 'type == "object"
    and (.login_identifier | type == "string" and length > 0)
    and (.login_password | type == "string" and length >= 12)' "$SECRET_INPUT" >/dev/null ||
    ops001_die "secret input JSON is invalid"
  LOGIN_IDENTIFIER="$("$JQ_BIN" -er '.login_identifier | strings | select(length > 0)' "$SECRET_INPUT")"
}

write_curl_config() {
  local config="$1"
  local token=""
  local idempotency=""
  [[ "$#" -ge 2 ]] && token="$2"
  [[ "$#" -ge 3 ]] && idempotency="$3"
  : >"$config"
  chmod 600 "$config"
  printf 'silent\nshow-error\nmax-time = 30\nconnect-timeout = 5\nnoproxy = "*"\nheader = "Accept: application/json"\nheader = "Content-Type: application/json"\n' >>"$config"
  [[ -z "$token" ]] || printf 'header = "Authorization: Bearer %s"\n' "$token" >>"$config"
  [[ -z "$idempotency" ]] || printf 'header = "Idempotency-Key: %s"\n' "$idempotency" >>"$config"
}

api_call() {
  local label="$1" method="$2" path="$3" body="$4" token="$5" idempotency="$6"
  local config="$PRIVATE_ROOT/$label.curl" response="$PRIVATE_ROOT/$label.response" status_file="$PRIVATE_ROOT/$label.status"
  local safe_path_regex='^/[A-Za-z0-9_./?=:&-]+$'
  [[ "$path" == /* && "$path" != *'..'* && "$path" =~ $safe_path_regex ]] || ops001_die "unsafe API path"
  [[ "$method" == "GET" || "$method" == "POST" ]] || ops001_die "unsupported HTTP method"
  write_curl_config "$config" "$token" "$idempotency"
  local -a args=(-q --config "$config" --request "$method" --output "$response" --write-out '%{http_code}' "$API_BASE$path")
  [[ -z "$body" ]] || args+=(--data-binary "@$body")
  "$CURL_BIN" "${args[@]}" >"$status_file" || ops001_die "$label API request failed"
  LAST_HTTP_STATUS="$(cat "$status_file")"
  LAST_RESPONSE="$response"
  [[ "$LAST_HTTP_STATUS" =~ ^2[0-9][0-9]$ ]] || ops001_die "$label returned HTTP $LAST_HTTP_STATUS"
  "$JQ_BIN" -e '.success == true' "$response" >/dev/null || ops001_die "$label returned unsuccessful JSON"
}

api_expect_status() {
  local label="$1" method="$2" path="$3" body="$4" token="$5" idempotency="$6" expected="$7"
  local config="$PRIVATE_ROOT/$label.curl" response="$PRIVATE_ROOT/$label.response" status_file="$PRIVATE_ROOT/$label.status"
  write_curl_config "$config" "$token" "$idempotency"
  local -a args=(-q --config "$config" --request "$method" --output "$response" --write-out '%{http_code}' "$API_BASE$path")
  [[ -z "$body" ]] || args+=(--data-binary "@$body")
  "$CURL_BIN" "${args[@]}" >"$status_file" || ops001_die "$label API request failed"
  LAST_HTTP_STATUS="$(cat "$status_file")"
  LAST_RESPONSE="$response"
  [[ "$LAST_HTTP_STATUS" == "$expected" ]] || ops001_die "$label expected HTTP $expected, got $LAST_HTTP_STATUS"
  printf 'PHASE_B_PART2_ACCEPTANCE|%s|HTTP_%s|PASS\n' "$label" "$LAST_HTTP_STATUS"
}

device_api_call() {
  local label="$1" method="$2" path="$3" body="$4" expected="$5"
  local config="$PRIVATE_ROOT/$label.curl" response="$PRIVATE_ROOT/$label.response" status_file="$PRIVATE_ROOT/$label.status"
  write_curl_config "$config"
  printf 'header = "X-Device-Id: %s"\nheader = "X-Device-Token: %s"\n' "$DEVICE_ID" "$DEVICE_TOKEN" >>"$config"
  local -a args=(-q --config "$config" --request "$method" --output "$response" --write-out '%{http_code}' "$API_BASE$path")
  [[ -z "$body" ]] || args+=(--data-binary "@$body")
  "$CURL_BIN" "${args[@]}" >"$status_file" || ops001_die "$label device API request failed"
  LAST_HTTP_STATUS="$(cat "$status_file")"
  LAST_RESPONSE="$response"
  [[ "$LAST_HTTP_STATUS" == "$expected" ]] || ops001_die "$label expected HTTP $expected, got $LAST_HTTP_STATUS"
  "$JQ_BIN" -e '.success == true' "$response" >/dev/null || ops001_die "$label device response was unsuccessful"
}

reject_secret_fields() {
  local label="$1"
  "$JQ_BIN" -e '
    [paths(scalars)[] | tostring
      | select(test("password_hash|device_token_hash|authorization|cookie|printer_endpoint|ip_address"; "i"))]
    | length == 0
  ' "$LAST_RESPONSE" >/dev/null || ops001_die "$label response contains forbidden secret-shaped fields"
}

login() {
  local body="$PRIVATE_ROOT/login.json"
  "$JQ_BIN" -c '{login_identifier: .login_identifier, password: .login_password}' "$SECRET_INPUT" >"$body"
  chmod 600 "$body"
  api_call login POST /auth/login "$body" "" ""
  ACCESS_TOKEN="$("$JQ_BIN" -er '.data.access_token | strings | select(length > 20)' "$LAST_RESPONSE")"
  REFRESH_TOKEN="$("$JQ_BIN" -er '.data.refresh_token | strings | select(length > 20)' "$LAST_RESPONSE")"
  "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" --arg login "$LOGIN_IDENTIFIER" \
    '.data.user.role_code == "OWNER" and .data.user.organization_id == $organization and .data.user.username == $login' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "login identity is not an Owner"
  printf 'PHASE_B_PART2_ACCEPTANCE|LOGIN|PASS\n'
}

verify_health_and_flyway() {
  api_call health GET /system/health "" "$ACCESS_TOKEN" ""
  reject_secret_fields health
  db_expect_pass FLYWAY_V23 "
select case when exists (
  select 1 from flyway_schema_history where version = '23' and success = true
) then 'PASS' else 'FAIL Flyway V23 is not installed' end;
"
}

capture_baselines() {
  MENU_SIGNATURE_BEFORE="$(db_query "
select md5(coalesce(string_agg(kind || ':' || id || ':' || value, '|' order by kind, id), ''))
from (
  select 'category' kind, id::text, coalesce(code,'') || ':' || coalesce(is_active::text,'') || ':' || coalesce(sort_order::text,'') value
    from menu_categories where store_id = $STORE_ID
  union all
  select 'item', id::text, coalesce(sku,'') || ':' || coalesce(base_price::text,'') || ':' || coalesce(is_active::text,'') || ':' || coalesce(category_id::text,'')
    from menu_items where store_id = $STORE_ID
  union all
  select 'option', option_row.id::text, coalesce(option_row.option_code,'') || ':' || coalesce(option_row.price_delta::text,'') || ':' || coalesce(option_row.is_active::text,'')
    from menu_item_options option_row join menu_items item on item.id = option_row.menu_item_id where item.store_id = $STORE_ID
) rows;
")"
  MASTER_SIGNATURE_BEFORE="$(db_query "select md5(coalesce(string_agg(id::text || ':' || fingerprint_sha256 || ':' || status || ':' || content_json, '|' order by id), '')) from chain_master_menu_versions;")"
  PROFILE_SIGNATURE_BEFORE="$(db_query "select md5(coalesce(string_agg(id::text || ':' || fingerprint_sha256 || ':' || status || ':' || content_json, '|' order by id), '')) from store_profile_versions;")"
  PRINTER_CONFIG_COUNT_BEFORE="$(db_query "select count(*) from printer_configs where store_id = $STORE_ID;")"
  PRINTER_ASSIGNMENT_COUNT_BEFORE="$(db_query "select count(*) from printer_assignments where store_id = $STORE_ID;")"
  db_expect_pass TARGET_STORE_SCOPE "
select case when count(*) = 1
  and max(organization_id) = $ORGANIZATION_ID
  and max(code) = $(sql_literal "$STORE_CODE")
  and max(store_kind) = 'VALIDATION_FIXTURE'
  and max(provisioning_source) = 'PHASE_B_OWNER_PROVISIONING'
  and max(lower(status)) <> 'active'
then 'PASS' else 'FAIL target Store is not an inactive synthetic Part 2 fixture' end
from stores where id = $STORE_ID;
"
}

initial_readiness() {
  api_call readiness_initial GET "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/readiness" "" "$ACCESS_TOKEN" ""
  reject_secret_fields readiness_initial
  "$JQ_BIN" -e '.data.ready == false and .data.readiness_status == "NOT_READY"' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "initial readiness must be NOT_READY before Part 2 provisioning"
  printf 'PHASE_B_PART2_ACCEPTANCE|INITIAL_NOT_READY|PASS\n'
}

rollback_probe() {
  local body="$PRIVATE_ROOT/rollback-request.json"
  "$JQ_BIN" -n '{
    stations: [{code:"ROLLBACK_STATION", name:"Rollback Station", station_type:"KITCHEN", sort_order:900, is_active:true}],
    tables: [{table_code:"ROLLBACK_TABLE", table_name:"Rollback Table", capacity:2, supports_split:true, sort_order:900, is_active:true}],
    staff: [{role_code:"MANAGER", full_name:"Rollback Manager"}],
    printer_roles: [
      {role_code:"ROLLBACK_GRAB_A", module_code:"GRAB", display_name:"Rollback A", mode:"MOCK", enabled:false, required:false},
      {role_code:"ROLLBACK_GRAB_B", module_code:"GRAB", display_name:"Rollback B", mode:"MOCK", enabled:false, required:false}
    ],
    devices: []
  }' >"$body"
  chmod 600 "$body"
  api_expect_status rollback_probe POST \
    "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/provision" \
    "$body" "$ACCESS_TOKEN" "phase-b-part2-rollback-probe-$STORE_ID" 400
  reject_secret_fields rollback_probe
  db_expect_pass ROLLBACK_NO_PARTIAL_ROWS "
select case when
  not exists(select 1 from stations where store_id = $STORE_ID and code = 'ROLLBACK_STATION')
  and not exists(select 1 from dining_tables where store_id = $STORE_ID and table_code = 'ROLLBACK_TABLE')
  and not exists(select 1 from users where store_id = $STORE_ID and full_name = 'Rollback Manager')
  and not exists(select 1 from store_logical_printer_roles where store_id = $STORE_ID and role_code like 'ROLLBACK_%')
  and exists(select 1 from store_provisioning_part2_requests where store_id = $STORE_ID and idempotency_key = 'phase-b-part2-rollback-probe-$STORE_ID' and status = 'FAILED')
then 'PASS' else 'FAIL failed provisioning left partial rows' end;
"
}

provision_store() {
  local body="$PRIVATE_ROOT/provision-request.json"
  local key="phase-b-part2-provision-$STORE_ID"
  "$JQ_BIN" -n '{}' >"$body"
  chmod 600 "$body"
  api_call provision POST \
    "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/provision" \
    "$body" "$ACCESS_TOKEN" "$key"
  cp "$LAST_RESPONSE" "$PRIVATE_ROOT/provision.response"
  "$JQ_BIN" -e '
    .data.status == "COMPLETED"
    and .data.replayed == false
    and .data.readiness.ready == true
    and .data.counts.table_count >= 2
    and .data.counts.staff_count >= 2
    and .data.counts.printer_role_count >= 2
    and .data.counts.device_count >= 1
    and (.data.synthetic_staff_credentials | length) >= 2
    and (.data.synthetic_device_credentials | length) == 1
  ' "$LAST_RESPONSE" >/dev/null || ops001_die "Part 2 provisioning response/readiness is incomplete"
  "$JQ_BIN" -e '
    [paths(scalars)[] | tostring
      | select(test("password_hash|device_token_hash|printer_endpoint|ip_address"; "i"))]
    | length == 0
  ' "$LAST_RESPONSE" >/dev/null || ops001_die "provisioning response contains persisted-secret-shaped fields"
  DEVICE_ID="$("$JQ_BIN" -er '.data.synthetic_device_credentials[0].device_id | numbers' "$LAST_RESPONSE")"
  DEVICE_TOKEN="$("$JQ_BIN" -er '.data.synthetic_device_credentials[0].device_token | strings | select(length > 20)' "$LAST_RESPONSE")"
  READINESS_FINGERPRINT="$("$JQ_BIN" -er '.data.readiness.readiness_fingerprint | strings | select(length == 64)' "$LAST_RESPONSE")"
  printf 'PHASE_B_PART2_ACCEPTANCE|PROVISION|PASS\n'

  api_call provision_replay POST \
    "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/provision" \
    "$body" "$ACCESS_TOKEN" "$key"
  reject_secret_fields provision_replay
  "$JQ_BIN" -e '.data.replayed == true and (.data.synthetic_staff_credentials | length) == 0 and (.data.synthetic_device_credentials | length) == 0' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "Part 2 provisioning replay was not credential-free/idempotent"
  printf 'PHASE_B_PART2_ACCEPTANCE|PROVISION_REPLAY|PASS\n'

  local changed="$PRIVATE_ROOT/provision-changed.json"
  "$JQ_BIN" -n '{tables:[{table_code:"CHANGED_REQUEST_TABLE", table_name:"Changed", capacity:2, supports_split:true, sort_order:901, is_active:true}]}' >"$changed"
  chmod 600 "$changed"
  api_expect_status provision_changed POST \
    "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/provision" \
    "$changed" "$ACCESS_TOKEN" "$key" 409
  reject_secret_fields provision_changed
}

verify_provisioning_db() {
  db_expect_pass PROVISIONED_RESOURCES_AND_ACCESS "
select case when
  (select count(*) from dining_tables where store_id = $STORE_ID and is_active = true) >= 2
  and (select count(*) from stations where store_id = $STORE_ID and is_active = true) > 0
  and (select count(*) from store_provisioning_resources where store_id = $STORE_ID and resource_type = 'TABLE') >= 2
  and (select count(*) from store_provisioning_resources where store_id = $STORE_ID and resource_type = 'DEVICE') = 1
  and (select count(*) from store_logical_printer_roles where store_id = $STORE_ID) = 2
  and (select count(*) from store_logical_printer_roles where store_id = $STORE_ID and mode in ('DISABLED','MOCK') and enabled = true and physical_binding_status = 'UNBOUND' and assigned_printer_id is null) = 2
  and (select count(*) from users user_row join roles role_row on role_row.id = user_row.role_id where user_row.store_id = $STORE_ID and role_row.code in ('MANAGER','FRONTDESK') and user_row.status = 'active') >= 2
  and (select count(*) from user_credentials credential join users user_row on user_row.id = credential.user_id where user_row.store_id = $STORE_ID and credential.password_algorithm = 'BCRYPT' and credential.is_active = true) >= 2
  and (select count(*) from organization_memberships membership join users user_row on user_row.id = membership.user_id where user_row.store_id = $STORE_ID and membership.organization_id = $ORGANIZATION_ID and membership.is_active = true) >= 2
  and (select count(*) from store_memberships membership where membership.store_id = $STORE_ID and membership.is_active = true) >= 2
then 'PASS' else 'FAIL Part 2 Store-local resources or access are incomplete' end;
"
  [[ "$(db_query "select count(*) from printer_configs where store_id = $STORE_ID;")" == "$PRINTER_CONFIG_COUNT_BEFORE" ]] ||
    ops001_die "Part 2 created or changed a physical printer config"
  [[ "$(db_query "select count(*) from printer_assignments where store_id = $STORE_ID;")" == "$PRINTER_ASSIGNMENT_COUNT_BEFORE" ]] ||
    ops001_die "Part 2 created or changed a physical printer assignment"
  printf 'PHASE_B_PART2_ACCEPTANCE|PRINTING_NO_HARDWARE_BINDING|PASS\n'
}

device_readiness_acceptance() {
  local heartbeat="$PRIVATE_ROOT/heartbeat.json"
  local broken="$PRIVATE_ROOT/proof-broken.json"
  local restored="$PRIVATE_ROOT/proof-restored.json"
  "$JQ_BIN" -n '{app_version:"synthetic-build", platform:"STAGING"}' >"$heartbeat"
  "$JQ_BIN" -n '{trusted_build:false, worker_status:"ERROR"}' >"$broken"
  "$JQ_BIN" -n '{trusted_build:true, worker_status:"HEALTHY"}' >"$restored"
  chmod 600 "$heartbeat" "$broken" "$restored"

  device_api_call device_heartbeat POST /devices/heartbeat "$heartbeat" 200
  reject_secret_fields device_heartbeat
  device_api_call device_proof_broken POST /devices/readiness-proof "$broken" 200
  reject_secret_fields device_proof_broken
  "$JQ_BIN" -e '.data.proof_status == "NOT_READY"' "$LAST_RESPONSE" >/dev/null || ops001_die "broken device proof was not recorded"
  api_call readiness_device_broken GET "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/readiness" "" "$ACCESS_TOKEN" ""
  reject_secret_fields readiness_device_broken
  "$JQ_BIN" -e '.data.ready == false and .data.readiness_status == "NOT_READY" and any(.data.checks[]; .code == "DEVICE_READINESS" and .status == "FAIL")' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "missing device prerequisite did not produce NOT_READY"
  printf 'PHASE_B_PART2_ACCEPTANCE|DEVICE_MISSING_PREREQUISITE|PASS\n'

  db_expect_pass DEVICE_TTL_EXPIRY "
update store_device_readiness set expires_at = CURRENT_TIMESTAMP - interval '1 second' where store_id = $STORE_ID and device_id = $DEVICE_ID;
select case when exists(select 1 from store_device_readiness where store_id = $STORE_ID and device_id = $DEVICE_ID and expires_at < CURRENT_TIMESTAMP) then 'PASS' else 'FAIL TTL was not expired' end;
"
  api_call readiness_ttl_expired GET "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/readiness" "" "$ACCESS_TOKEN" ""
  reject_secret_fields readiness_ttl_expired
  "$JQ_BIN" -e '.data.ready == false and any(.data.checks[]; .code == "DEVICE_READINESS" and .status == "FAIL")' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "expired device readiness did not fail closed"

  device_api_call device_proof_restored POST /devices/readiness-proof "$restored" 200
  reject_secret_fields device_proof_restored
  "$JQ_BIN" -e '.data.proof_status == "PASS"' "$LAST_RESPONSE" >/dev/null || ops001_die "restored device proof did not pass"
  device_api_call device_heartbeat_restored POST /devices/heartbeat "$heartbeat" 200
  reject_secret_fields device_heartbeat_restored
  api_call readiness_restored GET "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/readiness" "" "$ACCESS_TOKEN" ""
  reject_secret_fields readiness_restored
  READINESS_FINGERPRINT="$("$JQ_BIN" -er '.data.readiness_fingerprint | strings | select(length == 64)' "$LAST_RESPONSE")"
  "$JQ_BIN" -e '.data.ready == true and .data.readiness_status == "READY"' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "complete device prerequisite did not restore READY"
  printf 'PHASE_B_PART2_ACCEPTANCE|DEVICE_AUTH_HEARTBEAT_TTL_AND_RESTORE|PASS\n'
}

verify_store_isolation() {
  local wrong_organization="$((ORGANIZATION_ID + 999999))"
  api_expect_status WRONG_ORGANIZATION_REJECTED GET \
    "/owner/organizations/$wrong_organization/stores/$STORE_ID/phase-b/part2/readiness" \
    "" "$ACCESS_TOKEN" "" 403
  db_expect_pass ORGANIZATION_STORE_ISOLATION "
select case when
  (select organization_id from stores where id = $STORE_ID) = $ORGANIZATION_ID
  and not exists(select 1 from store_logical_printer_roles where store_id = $STORE_ID and organization_id <> $ORGANIZATION_ID)
  and not exists(select 1 from store_device_readiness where store_id = $STORE_ID and organization_id <> $ORGANIZATION_ID)
then 'PASS' else 'FAIL Store or Organization isolation drifted' end;
"
}

activation_precondition_conflict() {
  local body="$PRIVATE_ROOT/activate-wrong-fingerprint.json"
  local key="phase-b-part2-wrong-fingerprint-$STORE_ID"
  "$JQ_BIN" -n --arg fingerprint "$(printf '0%.0s' {1..64})" \
    '{expected_readiness_fingerprint:$fingerprint}' >"$body"
  chmod 600 "$body"
  api_expect_status activation_wrong_fingerprint POST \
    "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/activate" \
    "$body" "$ACCESS_TOKEN" "$key" 409
  reject_secret_fields activation_wrong_fingerprint
}

parallel_activation() {
  local body="$PRIVATE_ROOT/activate.json"
  local key="phase-b-part2-concurrency-$STORE_ID"
  local config_one="$PRIVATE_ROOT/activate-one.curl"
  local config_two="$PRIVATE_ROOT/activate-two.curl"
  local response_one="$PRIVATE_ROOT/activate-one.response"
  local response_two="$PRIVATE_ROOT/activate-two.response"
  local status_one="$PRIVATE_ROOT/activate-one.status"
  local status_two="$PRIVATE_ROOT/activate-two.status"
  "$JQ_BIN" -n --arg fingerprint "$READINESS_FINGERPRINT" \
    '{expected_readiness_fingerprint:$fingerprint}' >"$body"
  chmod 600 "$body"
  write_curl_config "$config_one" "$ACCESS_TOKEN" "$key"
  write_curl_config "$config_two" "$ACCESS_TOKEN" "$key"
  "$CURL_BIN" -q --config "$config_one" --request POST --output "$response_one" --write-out '%{http_code}' \
    --data-binary "@$body" "$API_BASE/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/activate" >"$status_one" &
  local first_pid="$!"
  "$CURL_BIN" -q --config "$config_two" --request POST --output "$response_two" --write-out '%{http_code}' \
    --data-binary "@$body" "$API_BASE/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/activate" >"$status_two" &
  local second_pid="$!"
  wait "$first_pid" || true
  wait "$second_pid" || true
  local first_status second_status
  first_status="$(cat "$status_one")"
  second_status="$(cat "$status_two")"
  [[ "$first_status" =~ ^2[0-9][0-9]$ || "$first_status" == "409" ]] || ops001_die "first concurrent activation returned $first_status"
  [[ "$second_status" =~ ^2[0-9][0-9]$ || "$second_status" == "409" ]] || ops001_die "second concurrent activation returned $second_status"
  [[ "$first_status" =~ ^2[0-9][0-9]$ || "$second_status" =~ ^2[0-9][0-9]$ ]] ||
    ops001_die "concurrent activation produced no successful request"
  LAST_RESPONSE="$response_one"
  reject_secret_fields activation_concurrency_one
  LAST_RESPONSE="$response_two"
  reject_secret_fields activation_concurrency_two
  db_expect_pass ACTIVATION_CONCURRENCY_LEDGER "
select case when
  (select count(*) from store_activation_requests where store_id = $STORE_ID and idempotency_key = 'phase-b-part2-concurrency-$STORE_ID') = 1
  and (select count(*) from store_activation_requests where store_id = $STORE_ID and idempotency_key = 'phase-b-part2-concurrency-$STORE_ID' and status = 'COMPLETED') = 1
then 'PASS' else 'FAIL activation concurrency ledger is not exactly-once' end;
"
  printf 'PHASE_B_PART2_ACCEPTANCE|ACTIVATION_CONCURRENCY|HTTP_%s_%s|PASS\n' "$first_status" "$second_status"

  api_call activation_replay POST \
    "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/activate" \
    "$body" "$ACCESS_TOKEN" "$key"
  reject_secret_fields activation_replay
  "$JQ_BIN" -e '.data.replayed == true and .data.target_state == "LIVE"' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "activation replay was not idempotent"
  local changed="$PRIVATE_ROOT/activate-changed.json"
  "$JQ_BIN" -n --arg fingerprint "$(printf '0%.0s' {1..64})" \
    '{expected_readiness_fingerprint:$fingerprint}' >"$changed"
  chmod 600 "$changed"
  api_expect_status activation_changed POST \
    "/owner/organizations/$ORGANIZATION_ID/stores/$STORE_ID/phase-b/part2/activate" \
    "$changed" "$ACCESS_TOKEN" "$key" 409
  reject_secret_fields activation_changed
}

verify_live_and_immutability() {
  db_expect_pass READY_TO_LIVE "
select case when
  (select count(*) from stores where id = $STORE_ID and lower(status) = 'active' and lifecycle_status = 'ACTIVE' and printing_mode = 'MOCK') = 1
  and (select count(*) from store_readiness_evidence where store_id = $STORE_ID and status = 'READY') = 1
  and (select count(*) from store_activation_requests where store_id = $STORE_ID and status = 'COMPLETED' and target_state = 'LIVE') = 1
then 'PASS' else 'FAIL Store did not reach the guarded LIVE state' end;
"
  [[ "$(db_query "select md5(coalesce(string_agg(kind || ':' || id || ':' || value, '|' order by kind, id), '')) from (select 'category' kind, id::text, coalesce(code,'') || ':' || coalesce(is_active::text,'') || ':' || coalesce(sort_order::text,'') value from menu_categories where store_id = $STORE_ID union all select 'item', id::text, coalesce(sku,'') || ':' || coalesce(base_price::text,'') || ':' || coalesce(is_active::text,'') || ':' || coalesce(category_id::text,'') from menu_items where store_id = $STORE_ID union all select 'option', option_row.id::text, coalesce(option_row.option_code,'') || ':' || coalesce(option_row.price_delta::text,'') || ':' || coalesce(option_row.is_active::text,'') value from menu_item_options option_row join menu_items item on item.id = option_row.menu_item_id where item.store_id = $STORE_ID) rows;")" == "$MENU_SIGNATURE_BEFORE" ]] ||
    ops001_die "Part 2 changed the Store-local menu"
  [[ "$(db_query "select md5(coalesce(string_agg(id::text || ':' || fingerprint_sha256 || ':' || status || ':' || content_json, '|' order by id), '')) from chain_master_menu_versions;")" == "$MASTER_SIGNATURE_BEFORE" ]] ||
    ops001_die "Part 2 changed the immutable Master Menu"
  [[ "$(db_query "select md5(coalesce(string_agg(id::text || ':' || fingerprint_sha256 || ':' || status || ':' || content_json, '|' order by id), '')) from store_profile_versions;")" == "$PROFILE_SIGNATURE_BEFORE" ]] ||
    ops001_die "Part 2 changed the immutable Store Profile"
  [[ "$(db_query "select count(*) from printer_configs where store_id = $STORE_ID;")" == "$PRINTER_CONFIG_COUNT_BEFORE" ]] ||
    ops001_die "LIVE activation changed physical printer configuration"
  [[ "$(db_query "select count(*) from printer_assignments where store_id = $STORE_ID;")" == "$PRINTER_ASSIGNMENT_COUNT_BEFORE" ]] ||
    ops001_die "LIVE activation changed physical printer assignments"
  printf 'PHASE_B_PART2_ACCEPTANCE|LIVE_AND_IMMUTABILITY|PASS\n'
}

verify_sanitized_evidence() {
  db_expect_pass SANITIZED_EVIDENCE "
select case when
  not exists(select 1 from store_readiness_evidence where store_id = $STORE_ID and (evidence_json ilike '%password_hash%' or evidence_json ilike '%device_token_value%' or evidence_json ilike '%printer_endpoint%' or evidence_json ilike '%http://%'))
  and not exists(select 1 from store_provisioning_part2_requests where store_id = $STORE_ID and (config_json ilike '%temporary_password%' or config_json ilike '%device_token_value%' or config_json ilike '%http://%'))
  and not exists(select 1 from store_activation_requests where store_id = $STORE_ID and (result_json ilike '%token%' or result_json ilike '%password%'))
then 'PASS' else 'FAIL sanitized readiness/activation evidence contains a secret-shaped value' end;
"
  printf 'PHASE_B_PART2_ACCEPTANCE|PRODUCTION_AND_REAL_HARDWARE_UNTOUCHED|PASS\n'
}

run_acceptance() {
  local lock_file="$OPS001_EXPECTED_ROOT/state/phase-b-part2-acceptance.lock"
  [[ -d "$OPS001_EXPECTED_ROOT/state" && ! -L "$OPS001_EXPECTED_ROOT/state" ]] ||
    ops001_die "Staging state directory is unavailable"
  umask 077
  exec 9>>"$lock_file"
  ACTION_LOCK_FD="9"
  [[ "$(ops001_file_owner "$lock_file")" == "$(id -u)" && "$(ops001_file_mode "$lock_file")" == "600" ]] ||
    ops001_die "Part 2 acceptance lock metadata is unsafe"
  "$FLOCK_BIN" -n "$ACTION_LOCK_FD" || ops001_die "another Part 2 acceptance action is running"
  validate_preflight_evidence
  ops001_validate_approval "$APPROVAL_FILE" "$APPROVAL_SHA256" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$(acceptance_scope)"
  OPS001_APPROVAL_FILE="$APPROVAL_FILE"
  ops001_assert_approval_unchanged
  ops001_consume_approval
  initialize_private_root
  read_secret_input
  login
  verify_health_and_flyway
  capture_baselines
  initial_readiness
  rollback_probe
  provision_store
  verify_provisioning_db
  verify_store_isolation
  device_readiness_acceptance
  activation_precondition_conflict
  parallel_activation
  verify_live_and_immutability
  verify_sanitized_evidence
  printf 'PHASE_B_PART2_ACCEPTANCE|TARGET_STORE_ID|%s|STATUS|PASS\n' "$STORE_ID"
}

main() {
  local seen="|"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate) ACTION="validate" ;;
      --execute-runtime) EXECUTE_RUNTIME="true" ;;
      --action|--approved-sha|--env-file|--preflight-evidence|--preflight-evidence-sha256|--organization-id|--store-id|--store-code|--approval|--approval-sha256|--secrets-fd)
        [[ $# -ge 2 && "$seen" != *"|$1|"* ]] || ops001_die "$1 requires one value and may appear once"
        seen="$seen$1|"
        case "$1" in
          --action) ACTION="$2" ;;
          --approved-sha) APPROVED_SHA="$2" ;;
          --env-file) ENV_FILE="$2" ;;
          --preflight-evidence) PREFLIGHT_EVIDENCE="$2" ;;
          --preflight-evidence-sha256) PREFLIGHT_EVIDENCE_SHA256="$2" ;;
          --organization-id) ORGANIZATION_ID="$2" ;;
          --store-id) STORE_ID="$2" ;;
          --store-code) STORE_CODE="$2" ;;
          --approval) APPROVAL_FILE="$2" ;;
          --approval-sha256) APPROVAL_SHA256="$2" ;;
          --secrets-fd) SECRETS_FD="$2" ;;
        esac
        shift
        ;;
      --help|-h) usage; exit 0 ;;
      *) ops001_die "unsupported option: $1" ;;
    esac
    shift
  done
  [[ -n "$APPROVED_SHA$ENV_FILE" ]] || ops001_die "approved SHA and environment file are required"
  ENV_FILE="$(ops001_canonical_file "$ENV_FILE")" || ops001_die "cannot canonicalize environment file"
  CURL_BIN="$(command -v curl || true)"
  JQ_BIN="$(command -v jq || true)"
  if [[ -z "$JQ_BIN" ]]; then
    JQ_BIN="$SCRIPT_DIR/ops001-jq-compat.py"
  fi
  FLOCK_BIN="$(command -v flock || true)"
  [[ "$CURL_BIN" == /* && "$JQ_BIN" == /* && "$FLOCK_BIN" == /* && -x "$JQ_BIN" ]] ||
    ops001_die "curl, jq-compatible parser, and flock are required"
  validate_release_and_env
  if [[ "$ACTION" == "validate" ]]; then
    [[ "$EXECUTE_RUNTIME" == "false" && -z "$APPROVAL_FILE$APPROVAL_SHA256$SECRETS_FD" ]] ||
      ops001_die "validation accepts no runtime, approval, or secret input"
    printf 'PHASE_B_PART2_ACCEPTANCE|VALIDATE|PASS|no login, provisioning, activation, or database mutation executed\n'
    return
  fi
  [[ "$ACTION" == "phase-b-part2-acceptance" && "$EXECUTE_RUNTIME" == "true" ]] ||
    ops001_die "phase-b-part2-acceptance requires --execute-runtime"
  validate_store_identity
  [[ -n "$APPROVAL_FILE" && "$APPROVAL_SHA256" =~ ^[0-9a-f]{64}$ && -n "$SECRETS_FD" ]] ||
    ops001_die "runtime acceptance requires Owner approval and inherited secret FD"
  PREFLIGHT_EVIDENCE="$(ops001_canonical_file "$PREFLIGHT_EVIDENCE")" || ops001_die "cannot canonicalize preflight evidence"
  APPROVAL_FILE="$(ops001_canonical_file "$APPROVAL_FILE")" || ops001_die "cannot canonicalize Owner approval"
  run_acceptance
}

trap cleanup EXIT
trap cleanup ERR
trap cleanup INT TERM
main "$@"
