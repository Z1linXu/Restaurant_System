#!/usr/bin/env bash
set -Eeuo pipefail

# Phase B Part 1 Staging acceptance. Runtime execution creates one explicit
# PHASE_B validation Store through the same Owner provisioning API used by the
# UI, then verifies Store-local menu independence without touching Production.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-ops-common.sh
source "$SCRIPT_DIR/staging-ops-common.sh"

EXPECTED_PROJECT="restaurant-pos-staging"
EXPECTED_PRODUCTION_PROJECT="cloud"
API_BASE="http://127.0.0.1:18080/api/v1"
SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

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
STORE_NAME=""
STORE_CODE=""
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
FLOCK_BIN=""
ACTION_LOCK_FD=""
TARGET_STORE_ID=""
SOURCE_STORE_ID=""
SOURCE_SIGNATURE_BEFORE=""
MASTER_SIGNATURE_BEFORE=""
PROFILE_SIGNATURE_BEFORE=""

usage() {
  cat <<'EOF'
Usage:
  staging-phase-b-part1-acceptance.sh --validate \
    --approved-sha <full-sha> --env-file /srv/restaurant-pos/staging/config/.env.staging

  staging-phase-b-part1-acceptance.sh --execute-runtime --action phase-b-part1-acceptance \
    --approved-sha <full-sha> \
    --env-file /srv/restaurant-pos/staging/config/.env.staging \
    --preflight-evidence <file> --preflight-evidence-sha256 <sha256> \
    --organization-id <id> \
    --store-name PHASE_B_VALIDATION_STORE_<safe-suffix> \
    --store-code PHASE_B_VALIDATION_STORE_<safe-suffix> \
    --approval <file> --approval-sha256 <sha256> --secrets-fd <open-fd>

The inherited FD contains one JSON object with login_identifier,
login_password, and phase_b_idempotency_key. Secret values and raw idempotency
keys are never accepted through argv/environment/output.
EOF
}

cleanup() {
  local status=$?
  if [[ -n "$ACCESS_TOKEN" && -n "$REFRESH_TOKEN" && -d "$PRIVATE_ROOT" && -x "$CURL_BIN" && -x "$JQ_BIN" ]]; then
    local body="$PRIVATE_ROOT/cleanup-logout.json" config="$PRIVATE_ROOT/cleanup-logout.curl" response="$PRIVATE_ROOT/cleanup-logout.response"
    "$JQ_BIN" -n --arg refresh "$REFRESH_TOKEN" '{refresh_token: $refresh}' >"$body" 2>/dev/null || true
    chmod 600 "$body" 2>/dev/null || true
    write_curl_config "$config" "$ACCESS_TOKEN" "" 2>/dev/null || true
    "$CURL_BIN" -q --config "$config" --request POST --output "$response" --max-time 15 --data-binary "@$body" "$API_BASE/auth/logout" >/dev/null 2>&1 || true
  fi
  ACCESS_TOKEN=""; REFRESH_TOKEN=""; OWNER_USER_ID=""; LAST_RESPONSE=""
  if [[ -n "$PRIVATE_ROOT" && "$PRIVATE_ROOT" == "${TMPDIR:-/tmp}"/restaurant-pos-phase-b-acceptance.* ]]; then
    rm -rf -- "$PRIVATE_ROOT"
  fi
  PRIVATE_ROOT=""; SECRET_INPUT=""; LOGIN_IDENTIFIER=""
  if [[ "$ACTION_LOCK_FD" == "9" ]]; then
    "$FLOCK_BIN" -u "$ACTION_LOCK_FD" >/dev/null 2>&1 || true
    exec 9>&-
    ACTION_LOCK_FD=""
  fi
  return "$status"
}

acceptance_scope() {
  printf 'organization_id=%s;store_code=%s;store_name=%s;preflight=%s' \
    "$ORGANIZATION_ID" "$STORE_CODE" "$STORE_NAME" "$PREFLIGHT_EVIDENCE_SHA256"
}

require_phase_b_store_identity() {
  [[ "$STORE_NAME" =~ ^PHASE_B_VALIDATION_STORE_[A-Z0-9_:-]{1,96}$ ]] ||
    ops001_die "Store name must use PHASE_B_VALIDATION_STORE_ synthetic namespace"
  [[ "$STORE_CODE" =~ ^PHASE_B_VALIDATION_STORE_[A-Z0-9_:-]{1,96}$ ]] ||
    ops001_die "Store code must use PHASE_B_VALIDATION_STORE_ synthetic namespace"
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
  env -i PATH="$SAFE_PATH" HOME="${HOME:-/tmp}" DOCKER_CONFIG="${DOCKER_CONFIG:-${HOME:-/tmp}/.docker}" \
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
  printf 'PHASE_B_PART1_ACCEPTANCE|%s|PASS\n' "$label"
}

sql_literal() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

initialize_private_root() {
  local temporary="${TMPDIR:-/tmp}"
  umask 077
  PRIVATE_ROOT="$(mktemp -d "$temporary/restaurant-pos-phase-b-acceptance.XXXXXX")"
  chmod 700 "$PRIVATE_ROOT"
  [[ "$(ops001_file_owner "$PRIVATE_ROOT")" == "$(id -u)" && "$(ops001_file_mode "$PRIVATE_ROOT")" == "700" ]] ||
    ops001_die "private API workspace is unsafe"
}

read_secret_input() {
  [[ "$SECRETS_FD" =~ ^[3-9][0-9]*$ && "$SECRETS_FD" != "9" ]] ||
    ops001_die "--secrets-fd must name an inherited non-standard descriptor other than 9"
  [[ -r "/dev/fd/$SECRETS_FD" ]] || ops001_die "secret descriptor is not readable"
  SECRET_INPUT="$PRIVATE_ROOT/secrets.json"
  dd bs=4096 of="$SECRET_INPUT" <&"$SECRETS_FD" 2>/dev/null
  chmod 600 "$SECRET_INPUT"
  "$JQ_BIN" -e 'type == "object"
    and (.login_identifier | type == "string" and length > 0)
    and (.login_password | type == "string" and length >= 12)
    and (.phase_b_idempotency_key | type == "string" and test("^[A-Za-z0-9._:-]{16,255}$"))' "$SECRET_INPUT" >/dev/null ||
    ops001_die "secret input JSON is invalid"
  LOGIN_IDENTIFIER="$("$JQ_BIN" -er '.login_identifier | strings | select(length > 0)' "$SECRET_INPUT")" ||
    ops001_die "Owner login identifier is required"
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
  local safe_path_regex='^/[A-Za-z0-9_./?=:&-]+$'
  [[ "$path" == /* && "$path" != *'..'* && "$path" =~ $safe_path_regex ]] ||
    ops001_die "unsafe API path"
  [[ "$method" == "GET" || "$method" == "POST" || "$method" == "PUT" ]] || ops001_die "unsupported HTTP method"
  write_curl_config "$config" "$token" "$idempotency"
  local -a args=(-q --config "$config" --request "$method" --output "$response" --write-out '%{http_code}' "$API_BASE$path")
  [[ -z "$body" ]] || args+=(--data-binary "@$body")
  if ! "$CURL_BIN" "${args[@]}" >"$status_file"; then
    ops001_die "$label API request failed"
  fi
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
  "$JQ_BIN" -c '{login_identifier: .login_identifier, password: .login_password}' "$SECRET_INPUT" >"$body"
  chmod 600 "$body"
  api_call login POST /auth/login "$body"
  ACCESS_TOKEN="$("$JQ_BIN" -er '.data.access_token | strings | select(length > 20)' "$LAST_RESPONSE")" ||
    ops001_die "login response lacks access token"
  REFRESH_TOKEN="$("$JQ_BIN" -er '.data.refresh_token | strings | select(length > 20)' "$LAST_RESPONSE")" ||
    ops001_die "login response lacks refresh token"
  OWNER_USER_ID="$("$JQ_BIN" -er '.data.user.id | numbers' "$LAST_RESPONSE")" ||
    ops001_die "login response lacks Owner user ID"
  "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" --arg login "$LOGIN_IDENTIFIER" \
    '.data.user.role_code == "OWNER" and .data.user.organization_id == $organization and .data.user.username == $login' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "login identity is not the approved Organization Owner"
  printf 'PHASE_B_PART1_ACCEPTANCE|LOGIN|HTTP_%s|PASS\n' "$LAST_HTTP_STATUS"
}

logout() {
  local body="$PRIVATE_ROOT/logout.json"
  "$JQ_BIN" -n --arg refresh "$REFRESH_TOKEN" '{refresh_token: $refresh}' >"$body"
  chmod 600 "$body"
  api_call logout POST /auth/logout "$body" "$ACCESS_TOKEN"
  ACCESS_TOKEN=""; REFRESH_TOKEN=""
  printf 'PHASE_B_PART1_ACCEPTANCE|LOGOUT|HTTP_%s|PASS\n' "$LAST_HTTP_STATUS"
}

verify_owner_workspace() {
  local refreshed_access_token
  api_call me GET /auth/me "" "$ACCESS_TOKEN"
  refreshed_access_token="$("$JQ_BIN" -er '.data.access_token | strings | select(length >= 24)' "$LAST_RESPONSE")" ||
    ops001_die "me response did not return a valid access token"
  ACCESS_TOKEN="$refreshed_access_token"

  api_call workspaces GET /me/workspaces "" "$ACCESS_TOKEN"
  reject_secret_response_fields workspaces
  "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" \
    '.data.organizations | any(.id == $organization and .role_code == "OWNER")' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "workspace lacks approved Organization Owner access"
  "$JQ_BIN" -e '[.data.stores[]? | select((.store_kind // "") == "VALIDATION_FIXTURE" and (.provisioning_source // "") != "PHASE_B_OWNER_PROVISIONING")] | length == 0' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "workspace exposes non-Phase-B validation fixtures"

  api_call overview GET /owner/overview "" "$ACCESS_TOKEN"
  reject_secret_response_fields overview
  "$JQ_BIN" -e --argjson organization "$ORGANIZATION_ID" \
    '.data.organizations | any(.id == $organization and .role_code == "OWNER")' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "Owner overview lacks approved Organization"
  printf 'PHASE_B_PART1_ACCEPTANCE|OWNER_WORKSPACE|PASS\n'
}

source_store_id() {
  db_query "select coalesce((select id::text from stores where organization_id = $ORGANIZATION_ID and coalesce(store_kind, 'BUSINESS') = 'BUSINESS' and lower(coalesce(status, '')) = 'active' order by id limit 1), '')"
}

store_signature() {
  local store_id="$1"
  db_query "
with rows as (
  select 'category' as kind, id::text, coalesce(code,'') || ':' || coalesce(is_active::text,'') || ':' || coalesce(sort_order::text,'') as value from menu_categories where store_id = $store_id
  union all select 'item', id::text, coalesce(sku,'') || ':' || coalesce(base_price::text,'') || ':' || coalesce(is_active::text,'') || ':' || coalesce(category_id::text,'') from menu_items where store_id = $store_id
  union all select 'option', option_row.id::text, coalesce(option_row.option_code,'') || ':' || coalesce(option_row.price_delta::text,'') || ':' || coalesce(option_row.is_active::text,'') || ':' || coalesce(option_row.parent_option_id::text,'') from menu_item_options option_row join menu_items item on item.id = option_row.menu_item_id where item.store_id = $store_id
  union all select 'pricing', store_id::text, size_small_delta::text || ':' || size_regular_delta::text || ':' || size_large_delta::text || ':' || combo_delta::text from store_pricing_policies where store_id = $store_id
  union all select 'combo', component.id::text, component.component_group || ':' || component.component_code || ':' || component.enabled::text || ':' || coalesce((component.component_code = combo_group.default_component_code)::text,'') from store_combo_components component join store_combo_groups combo_group on combo_group.id = component.group_id where component.store_id = $store_id
  union all select 'printing', rule_set.id::text, coalesce(revision.fingerprint_sha256,'') from printing_display_rule_sets rule_set left join printing_display_rule_revisions revision on revision.id = rule_set.active_revision_id where rule_set.store_id = $store_id
)
select md5(coalesce(string_agg(kind || ':' || id || ':' || value, '|' order by kind, id), '')) from rows;
"
}

master_signature() {
  db_query "select md5(string_agg(id::text || ':' || fingerprint_sha256 || ':' || status || ':' || content_json, '|' order by id)) from chain_master_menu_versions;"
}

profile_signature() {
  db_query "select md5(string_agg(id::text || ':' || fingerprint_sha256 || ':' || status || ':' || content_json, '|' order by id)) from store_profile_versions;"
}

capture_baselines() {
  SOURCE_STORE_ID="$(source_store_id)"
  [[ "$SOURCE_STORE_ID" =~ ^[1-9][0-9]*$ ]] || ops001_die "approved Organization source Store identity is unavailable"
  SOURCE_SIGNATURE_BEFORE="$(store_signature "$SOURCE_STORE_ID")"
  MASTER_SIGNATURE_BEFORE="$(master_signature)"
  PROFILE_SIGNATURE_BEFORE="$(profile_signature)"
  db_expect_pass TARGET_CODE_AVAILABLE "select case when count(*) = 0 then 'PASS' else 'FAIL target Store code already exists' end from stores where organization_id = $ORGANIZATION_ID and code = $(sql_literal "$STORE_CODE");"
  printf 'PHASE_B_PART1_ACCEPTANCE|BASELINE|SOURCE_STORE_ID|%s|PASS\n' "$SOURCE_STORE_ID"
}

provision_store() {
  local catalog="$PRIVATE_ROOT/catalog.json" request="$PRIVATE_ROOT/provision-request.json"
  local key first_store second_store replayed validation_status
  api_call provisioning_catalog GET "/owner/organizations/$ORGANIZATION_ID/phase-b/store-provisioning/catalog" "" "$ACCESS_TOKEN"
  reject_secret_response_fields provisioning_catalog
  cp "$LAST_RESPONSE" "$catalog"
  "$JQ_BIN" -e '.data.enabled == true and .data.profile_code == "ST_DENIS_CANONICAL_PROFILE" and .data.profile_version == "v2" and .data.master_menu_key == "LANZHOU_CHAIN_MASTER_MENU" and .data.master_menu_version == "v1"' "$catalog" >/dev/null ||
    ops001_die "Phase B provisioning catalog is not enabled with the approved Profile/Master"
  "$JQ_BIN" -n \
    --arg store_name "$STORE_NAME" \
    --arg store_code "$STORE_CODE" \
    --slurpfile catalog "$catalog" \
    '{
      store_name: $store_name,
      store_code: $store_code,
      profile_code: $catalog[0].data.profile_code,
      profile_version: $catalog[0].data.profile_version,
      master_menu_key: $catalog[0].data.master_menu_key,
      master_menu_version: $catalog[0].data.master_menu_version,
      master_menu_fingerprint_sha256: $catalog[0].data.master_menu_fingerprint_sha256
    }' >"$request"
  chmod 600 "$request"
  key="$("$JQ_BIN" -er '.phase_b_idempotency_key' "$SECRET_INPUT")"
  api_call provision_first POST "/owner/organizations/$ORGANIZATION_ID/phase-b/store-provisioning" "$request" "$ACCESS_TOKEN" "$key"
  reject_secret_response_fields provision_first
  first_store="$("$JQ_BIN" -er '.data.store_id | numbers' "$LAST_RESPONSE")"
  validation_status="$("$JQ_BIN" -er '.data.validation_status | select(. == "PASS" or . == "WARNING")' "$LAST_RESPONSE")"
  "$JQ_BIN" -e '.data.status == "COMPLETED" and .data.result_code == "PHASE_B_STORE_PROVISIONED" and .data.counts.category_count > 0 and .data.counts.item_count > 0 and .data.counts.option_count > 0 and .data.counts.printing_rule_count == 1' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "provisioning result counts/status are invalid"
  api_call provision_replay POST "/owner/organizations/$ORGANIZATION_ID/phase-b/store-provisioning" "$request" "$ACCESS_TOKEN" "$key"
  reject_secret_response_fields provision_replay
  second_store="$("$JQ_BIN" -er '.data.store_id | numbers' "$LAST_RESPONSE")"
  replayed="$("$JQ_BIN" -er '.data.replayed | select(. == true)' "$LAST_RESPONSE")"
  [[ "$first_store" == "$second_store" && "$replayed" == "true" ]] ||
    ops001_die "Phase B provisioning replay contract failed"
  TARGET_STORE_ID="$first_store"
  printf 'PHASE_B_PART1_ACCEPTANCE|PROVISION|STORE_ID|%s|VALIDATION|%s|REPLAY|PASS\n' "$TARGET_STORE_ID" "$validation_status"
}

verify_materialization() {
  db_expect_pass STORE_LIFECYCLE "
select case when count(*) = 1 then 'PASS' else 'FAIL Store lifecycle/provenance mismatch' end
from stores
where id = $TARGET_STORE_ID
  and organization_id = $ORGANIZATION_ID
  and code = $(sql_literal "$STORE_CODE")
  and lower(status) <> 'active'
  and store_kind = 'VALIDATION_FIXTURE'
  and lifecycle_status = 'READY_FOR_REVIEW'
  and provisioning_source = 'PHASE_B_OWNER_PROVISIONING'
  and printing_enabled = true
  and printing_mode = 'MOCK'
  and provisioned_profile_code = 'ST_DENIS_CANONICAL_PROFILE'
  and provisioned_profile_version = 'v2'
  and provisioned_master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
  and provisioned_master_menu_version = 'v1';
"
  db_expect_pass IDEMPOTENCY_LEDGER "
select case when
  (select count(*) from stores where organization_id = $ORGANIZATION_ID and code = $(sql_literal "$STORE_CODE")) = 1
  and (select count(*) from owner_store_provisioning_requests where organization_id = $ORGANIZATION_ID and store_id = $TARGET_STORE_ID and status = 'COMPLETED') = 1
then 'PASS' else 'FAIL duplicate Store or provisioning request detected' end;
"
  db_expect_pass MASTER_MAPPING_COUNTS "
with master_version as (
  select version.id
  from chain_master_menu_versions version
  join chain_master_menus master_menu on master_menu.id = version.master_menu_id
  where master_menu.organization_id = $ORGANIZATION_ID
    and master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
    and version.version_key = 'v1'
    and version.status = 'PUBLISHED'
    and version.fingerprint_sha256 = 'ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c'
),
counts as (
  select
    (select count(*) from chain_master_menu_categories where master_menu_version_id = (select id from master_version)) as master_categories,
    (select count(*) from chain_master_menu_products where master_menu_version_id = (select id from master_version)) as master_items,
    (select count(*) from chain_master_menu_options where master_menu_version_id = (select id from master_version)) as master_options,
    (select count(*) from menu_categories where store_id = $TARGET_STORE_ID) as store_categories,
    (select count(*) from menu_items where store_id = $TARGET_STORE_ID) as store_items,
    (select count(*) from menu_item_options option_row join menu_items item on item.id = option_row.menu_item_id where item.store_id = $TARGET_STORE_ID) as store_options,
    (select count(*) from store_menu_master_mappings where store_id = $TARGET_STORE_ID and master_menu_version_id = (select id from master_version) and entity_type = 'CATEGORY') as category_mappings,
    (select count(*) from store_menu_master_mappings where store_id = $TARGET_STORE_ID and master_menu_version_id = (select id from master_version) and entity_type = 'ITEM') as item_mappings,
    (select count(*) from store_menu_master_mappings where store_id = $TARGET_STORE_ID and master_menu_version_id = (select id from master_version) and entity_type = 'OPTION') as option_mappings
)
select case when master_categories = store_categories and master_items = store_items and master_options = store_options
  and category_mappings = store_categories and item_mappings = store_items and option_mappings = store_options
then 'PASS' else 'FAIL Master mapping/materialization counts mismatch' end from counts;
"
  db_expect_pass LOCAL_IDS_AND_PARENT_REMAP "
with master_version as (
  select version.id
  from chain_master_menu_versions version
  join chain_master_menus master_menu on master_menu.id = version.master_menu_id
  where master_menu.organization_id = $ORGANIZATION_ID
    and master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
    and version.version_key = 'v1'
    and version.status = 'PUBLISHED'
    and version.fingerprint_sha256 = 'ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c'
),
representative as (
  select p.master_product_key, p.sku, target_mapping.local_entity_id as target_item_id, source_item.id as source_item_id
  from chain_master_menu_products p
  join store_menu_master_mappings target_mapping
    on target_mapping.master_menu_version_id = p.master_menu_version_id
   and target_mapping.entity_type = 'ITEM'
   and target_mapping.master_product_key = p.master_product_key
   and target_mapping.store_id = $TARGET_STORE_ID
  join menu_items source_item on source_item.store_id = $SOURCE_STORE_ID and source_item.sku = p.sku
  where p.master_menu_version_id = (select id from master_version)
    and p.sku is not null and p.sku <> ''
    and source_item.id <> target_mapping.local_entity_id
  limit 1
),
parents as (
  select
    (select count(*) from chain_master_menu_options where master_menu_version_id = (select id from master_version) and parent_master_option_key is not null) as master_parent_options,
    (select count(*) from menu_item_options option_row join menu_items item on item.id = option_row.menu_item_id where item.store_id = $TARGET_STORE_ID and option_row.parent_option_id is not null) as store_parent_options
)
select case when exists(select 1 from representative)
  and (select master_parent_options from parents) = (select store_parent_options from parents)
then 'PASS' else 'FAIL local ID or parent option remap proof missing' end;
"
}

verify_store_context_and_catalog() {
  api_call store_context GET "/stores/$TARGET_STORE_ID/context" "" "$ACCESS_TOKEN"
  reject_secret_response_fields store_context
  "$JQ_BIN" -e '.data.status != "active" and .data.store_kind == "VALIDATION_FIXTURE" and .data.lifecycle_status == "READY_FOR_REVIEW" and .data.provisioning_source == "PHASE_B_OWNER_PROVISIONING" and (.data.module_configuration.modules | length > 0)' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "Store Context does not expose Phase B lifecycle/modules"
  api_call catalog_before GET "/menu/catalog?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  reject_secret_response_fields catalog_before
  "$JQ_BIN" -e '.data.categories | length > 0 and ([.[] | .items | length] | add) > 0' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "target Store catalog is empty"
  printf 'PHASE_B_PART1_ACCEPTANCE|STORE_CONTEXT_AND_CATALOG|PASS\n'
}

local_item_deactivation() {
  local items="$PRIVATE_ROOT/items.json" item="$PRIVATE_ROOT/item.json" body="$PRIVATE_ROOT/item-deactivate.json" sku
  api_call items_for_deactivate GET "/admin/platform/menu/items?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  reject_secret_response_fields items_for_deactivate
  cp "$LAST_RESPONSE" "$items"
  "$JQ_BIN" '[.data[] | select(.is_active == true and (.sku // "") != "")] | first' "$items" >"$item"
  sku="$("$JQ_BIN" -er '.sku' "$item")"
  "$JQ_BIN" '.is_active = false' "$item" >"$body"
  api_call item_deactivate PUT "/admin/platform/menu/items/$("$JQ_BIN" -r '.id' "$item")" "$body" "$ACCESS_TOKEN"
  reject_secret_response_fields item_deactivate
  api_call catalog_after_item_deactivate GET "/menu/catalog?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  "$JQ_BIN" -e --arg sku "$sku" '[.data.categories[].items[]? | select(.sku == $sku)] | length == 0' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "deactivated target item remains visible in ordering catalog"
  db_expect_pass ITEM_DEACTIVATION_ISOLATION "
select case when
  exists(select 1 from menu_items where store_id = $TARGET_STORE_ID and sku = $(sql_literal "$sku") and is_active = false)
  and exists(select 1 from menu_items where store_id = $SOURCE_STORE_ID and sku = $(sql_literal "$sku") and is_active = true)
then 'PASS' else 'FAIL item deactivation isolation mismatch' end;
"
}

local_category_deactivation() {
  local categories="$PRIVATE_ROOT/categories.json" category="$PRIVATE_ROOT/category.json" body="$PRIVATE_ROOT/category-toggle.json" category_id category_code
  api_call categories_for_deactivate GET "/admin/platform/menu/categories?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  reject_secret_response_fields categories_for_deactivate
  cp "$LAST_RESPONSE" "$categories"
  category_id="$(db_query "select id::text from menu_categories where store_id = $TARGET_STORE_ID and is_active = true and exists (select 1 from menu_items where store_id = $TARGET_STORE_ID and category_id = menu_categories.id and is_active = true) order by sort_order, id limit 1;")"
  [[ "$category_id" =~ ^[1-9][0-9]*$ ]] || ops001_die "no target category with active child item is available for deactivation proof"
  "$JQ_BIN" --argjson category "$category_id" '[.data[] | select(.id == $category)] | first' "$categories" >"$category"
  category_code="$("$JQ_BIN" -er '.code' "$category")"
  "$JQ_BIN" --argjson store "$TARGET_STORE_ID" '{store_id: $store, name_zh, name_en, sort_order, enabled: false}' "$category" >"$body"
  api_call category_deactivate PUT "/admin/menu/categories/$category_id?store_id=$TARGET_STORE_ID" "$body" "$ACCESS_TOKEN"
  reject_secret_response_fields category_deactivate
  api_call catalog_after_category_deactivate GET "/menu/catalog?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  "$JQ_BIN" -e --arg code "$category_code" '[.data.categories[] | select(.code == $code)] | length == 0' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "deactivated target category remains visible in ordering catalog"
  db_expect_pass CATEGORY_DEACTIVATION_ISOLATION "
select case when
  exists(select 1 from menu_categories where store_id = $TARGET_STORE_ID and id = $category_id and is_active = false)
  and exists(select 1 from menu_items where store_id = $TARGET_STORE_ID and category_id = $category_id and is_active = true)
  and exists(select 1 from menu_categories where store_id = $SOURCE_STORE_ID and code = $(sql_literal "$category_code") and is_active = true)
then 'PASS' else 'FAIL category effective availability isolation mismatch' end;
"
  "$JQ_BIN" --argjson store "$TARGET_STORE_ID" '{store_id: $store, name_zh, name_en, sort_order, enabled: true}' "$category" >"$body"
  api_call category_reactivate PUT "/admin/menu/categories/$category_id?store_id=$TARGET_STORE_ID" "$body" "$ACCESS_TOKEN"
  reject_secret_response_fields category_reactivate
}

store_only_item_acceptance() {
  local categories="$PRIVATE_ROOT/categories-store-only.json" stations="$PRIVATE_ROOT/stations-store-only.json"
  local body="$PRIVATE_ROOT/store-only-item.json" sku category_id station_id
  sku="${STORE_CODE}_LOCAL_ITEM"
  api_call categories_store_only GET "/admin/platform/menu/categories?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  cp "$LAST_RESPONSE" "$categories"
  api_call stations_store_only GET "/admin/platform/stations?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  cp "$LAST_RESPONSE" "$stations"
  category_id="$("$JQ_BIN" -er '[.data[] | select(.is_active == true)] | first.id' "$categories")"
  station_id="$("$JQ_BIN" -er '[.data[] | select(.is_active == true)] | first.id' "$stations")"
  "$JQ_BIN" -n --argjson store "$TARGET_STORE_ID" --argjson category "$category_id" --argjson station "$station_id" --arg sku "$sku" '{
    store_id: $store,
    category_id: $category,
    station_id: $station,
    sku: $sku,
    name_zh: "Phase B Store-only Item",
    name_en: "Phase B Store-only Item",
    item_type: "OTHER",
    base_price: 1.23,
    cost_per_item: 0,
    is_active: true,
    is_sold_out: false,
    sort_order: 9990
  }' >"$body"
  api_call store_only_item POST "/admin/platform/menu/items" "$body" "$ACCESS_TOKEN"
  reject_secret_response_fields store_only_item
  api_call catalog_store_only GET "/menu/catalog?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  "$JQ_BIN" -e --arg sku "$sku" '[.data.categories[].items[]? | select(.sku == $sku)] | length == 1' "$LAST_RESPONSE" >/dev/null ||
    ops001_die "Store-only item is not visible in target catalog"
  db_expect_pass STORE_ONLY_ITEM_ISOLATION "
select case when
  exists(select 1 from menu_items where store_id = $TARGET_STORE_ID and sku = $(sql_literal "$sku"))
  and not exists(select 1 from menu_items where store_id = $SOURCE_STORE_ID and sku = $(sql_literal "$sku"))
  and not exists(select 1 from chain_master_menu_products where sku = $(sql_literal "$sku"))
  and exists(select 1 from store_menu_master_mappings mapping join menu_items item on item.id = mapping.local_entity_id where mapping.store_id = $TARGET_STORE_ID and mapping.entity_type = 'ITEM' and mapping.origin = 'STORE_ONLY' and mapping.mapping_status = 'STORE_ONLY' and item.sku = $(sql_literal "$sku"))
then 'PASS' else 'FAIL Store-only item isolation mismatch' end;
"
}

pricing_combo_printing_independence() {
  local target_policy="$PRIVATE_ROOT/target-pricing.json" update_policy="$PRIVATE_ROOT/update-pricing.json"
  local combo="$PRIVATE_ROOT/combo.json" combo_update="$PRIVATE_ROOT/combo-update.json" component_id
  local rules="$PRIVATE_ROOT/rules.json" draft="$PRIVATE_ROOT/rules-draft.json" publish="$PRIVATE_ROOT/rules-publish.json" draft_id

  api_call target_pricing GET "/admin/menu/pricing-policy?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  cp "$LAST_RESPONSE" "$target_policy"
  "$JQ_BIN" '.data | .size_small_delta = ((.size_small_delta | tonumber) + 0.11) | {store_id, size_small_delta, size_regular_delta, size_large_delta, combo_delta}' "$target_policy" >"$update_policy"
  api_call update_pricing PUT "/admin/menu/pricing-policy" "$update_policy" "$ACCESS_TOKEN"
  reject_secret_response_fields update_pricing
  db_expect_pass PRICE_INDEPENDENCE "
select case when
  (select size_small_delta from store_pricing_policies where store_id = $TARGET_STORE_ID) <> (select size_small_delta from store_pricing_policies where store_id = $SOURCE_STORE_ID)
then 'PASS' else 'FAIL target pricing did not diverge independently' end;
"

  api_call target_combo GET "/admin/menu/combo-configuration?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  cp "$LAST_RESPONSE" "$combo"
  component_id="$("$JQ_BIN" -er '[.data.groups[].components[] | select(.enabled == true)] | first.id' "$combo")"
  "$JQ_BIN" --argjson component "$component_id" --argjson store "$TARGET_STORE_ID" '
    .data as $data |
    {
      store_id: $store,
      groups: [$data.groups[] | {group_id, group_code, name_zh, name_en, selection_rule, required, enabled, display_order, default_component_code}],
      components: [$data.groups[].components[] | {id, group_id, component_group, component_code, name_zh, name_en, enabled: (if .id == $component then false else .enabled end), display_order, is_default, linked_menu_item_id, business_behavior}]
    }' "$combo" >"$combo_update"
  api_call update_combo PUT "/admin/menu/combo-configuration" "$combo_update" "$ACCESS_TOKEN"
  reject_secret_response_fields update_combo
  db_expect_pass COMBO_INDEPENDENCE "
select case when
  exists(select 1 from store_combo_components where store_id = $TARGET_STORE_ID and id = $component_id and enabled = false)
then 'PASS' else 'FAIL target combo component did not change independently' end;
"

  api_call target_rules GET "/admin/printing/display-rules?store_id=$TARGET_STORE_ID" "" "$ACCESS_TOKEN"
  cp "$LAST_RESPONSE" "$rules"
  "$JQ_BIN" --argjson store "$TARGET_STORE_ID" '
    .data.active_revision.content
    | .item_aliases = ((.item_aliases // []) + [{item_sku: "phase_b_acceptance_probe", outputs: {GRAB: "PB"}}])
    | {store_id: $store, content: ., summary: "Phase B acceptance local rule"}' "$rules" >"$draft"
  api_call save_rule_draft POST "/admin/printing/display-rules/draft" "$draft" "$ACCESS_TOKEN"
  reject_secret_response_fields save_rule_draft
  draft_id="$("$JQ_BIN" -er '.data.id | numbers' "$LAST_RESPONSE")"
  "$JQ_BIN" -n --argjson store "$TARGET_STORE_ID" --argjson revision "$draft_id" '{store_id: $store, revision_id: $revision}' >"$publish"
  api_call publish_rule POST "/admin/printing/display-rules/publish" "$publish" "$ACCESS_TOKEN"
  reject_secret_response_fields publish_rule
  db_expect_pass PRINTING_RULE_INDEPENDENCE "
select case when
  (select revision.fingerprint_sha256 from printing_display_rule_sets rule_set join printing_display_rule_revisions revision on revision.id = rule_set.active_revision_id where rule_set.store_id = $TARGET_STORE_ID)
  <>
  (select revision.fingerprint_sha256 from printing_display_rule_sets rule_set join printing_display_rule_revisions revision on revision.id = rule_set.active_revision_id where rule_set.store_id = $SOURCE_STORE_ID)
then 'PASS' else 'FAIL printing display rule independence mismatch' end;
"
}

verify_global_immutability() {
  [[ "$(store_signature "$SOURCE_STORE_ID")" == "$SOURCE_SIGNATURE_BEFORE" ]] ||
    ops001_die "source Store signature changed during Phase B acceptance"
  [[ "$(master_signature)" == "$MASTER_SIGNATURE_BEFORE" ]] ||
    ops001_die "published Master Menu signature changed during local Store acceptance"
  [[ "$(profile_signature)" == "$PROFILE_SIGNATURE_BEFORE" ]] ||
    ops001_die "published Store Profile signature changed during local Store acceptance"
  printf 'PHASE_B_PART1_ACCEPTANCE|SOURCE_MASTER_PROFILE_IMMUTABILITY|PASS\n'
}

acquire_action_lock() {
  local state_dir="$OPS001_EXPECTED_ROOT/state" lock_file
  [[ -d "$state_dir" && ! -L "$state_dir" ]] || ops001_die "Staging state directory is unavailable"
  lock_file="$state_dir/phase-b-part1-acceptance.lock"
  umask 077
  exec 9>>"$lock_file"
  ACTION_LOCK_FD="9"
  [[ "$(ops001_file_owner "$lock_file")" == "$(id -u)" && "$(ops001_file_mode "$lock_file")" == "600" ]] ||
    ops001_die "Phase B acceptance lock metadata is unsafe"
  "$FLOCK_BIN" -n "$ACTION_LOCK_FD" || ops001_die "another Phase B Part 1 acceptance action is already running"
}

run_acceptance() {
  acquire_action_lock
  validate_preflight_evidence
  ops001_validate_approval "$APPROVAL_FILE" "$APPROVAL_SHA256" "$ACTION" "$APPROVED_SHA" "$ENV_DIGEST" "$(acceptance_scope)"
  OPS001_APPROVAL_FILE="$APPROVAL_FILE"; ops001_assert_approval_unchanged; ops001_consume_approval
  initialize_private_root
  read_secret_input
  capture_baselines
  login
  verify_owner_workspace
  provision_store
  verify_materialization
  verify_store_context_and_catalog
  local_item_deactivation
  local_category_deactivation
  store_only_item_acceptance
  pricing_combo_printing_independence
  verify_global_immutability
  logout
  printf 'PHASE_B_PART1_ACCEPTANCE|TARGET_STORE_ID|%s|STATUS|PASS\n' "$TARGET_STORE_ID"
}

main() {
  local seen="|"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate) ACTION="validate" ;;
      --execute-runtime) EXECUTE_RUNTIME="true" ;;
      --action|--approved-sha|--env-file|--preflight-evidence|--preflight-evidence-sha256|--organization-id|--store-name|--store-code|--approval|--approval-sha256|--secrets-fd)
        [[ $# -ge 2 && "$seen" != *"|$1|"* ]] || ops001_die "$1 requires one value and may appear once"
        seen="${seen}${1}|"
        case "$1" in
          --action) ACTION="$2" ;;
          --approved-sha) APPROVED_SHA="$2" ;;
          --env-file) ENV_FILE="$2" ;;
          --preflight-evidence) PREFLIGHT_EVIDENCE="$2" ;;
          --preflight-evidence-sha256) PREFLIGHT_EVIDENCE_SHA256="$2" ;;
          --organization-id) ORGANIZATION_ID="$2" ;;
          --store-name) STORE_NAME="$2" ;;
          --store-code) STORE_CODE="$2" ;;
          --approval) APPROVAL_FILE="$2" ;;
          --approval-sha256) APPROVAL_SHA256="$2" ;;
          --secrets-fd) SECRETS_FD="$2" ;;
        esac
        shift ;;
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
    printf 'PHASE_B_PART1_ACCEPTANCE|VALIDATE|PASS|no login, Store creation, or API mutation executed\n'
    return
  fi
  [[ "$ACTION" == "phase-b-part1-acceptance" ]] || ops001_die "unsupported action: $ACTION"
  [[ "$EXECUTE_RUNTIME" == "true" ]] || ops001_die "$ACTION requires --execute-runtime"
  [[ "$ORGANIZATION_ID" =~ ^[1-9][0-9]*$ ]] || ops001_die "organization ID must be positive"
  require_phase_b_store_identity
  [[ -n "$APPROVAL_FILE" && "$APPROVAL_SHA256" =~ ^[0-9a-f]{64}$ && -n "$SECRETS_FD" ]] ||
    ops001_die "$ACTION requires Owner approval and inherited secret FD"
  PREFLIGHT_EVIDENCE="$(ops001_canonical_file "$PREFLIGHT_EVIDENCE")" || ops001_die "cannot canonicalize preflight evidence"
  APPROVAL_FILE="$(ops001_canonical_file "$APPROVAL_FILE")" || ops001_die "cannot canonicalize Owner approval"
  run_acceptance
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap cleanup EXIT ERR INT TERM
  main "$@"
fi
