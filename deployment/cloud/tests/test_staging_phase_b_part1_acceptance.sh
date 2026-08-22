#!/usr/bin/env bash
set -Eeuo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
SCRIPT="$REPO_ROOT/deployment/cloud/staging-phase-b-part1-acceptance.sh"
JQ_COMPAT="$REPO_ROOT/deployment/cloud/ops001-jq-compat.py"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-phase-b-acceptance-test.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"
trap '[[ "${BASH_SUBSHELL:-0}" -ne 0 ]] || rm -rf "$TMP_DIR"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() { local label="$1"; shift; if (trap - EXIT; "$@") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then fail "$label unexpectedly passed"; fi; }

bash -n "$SCRIPT"
python3 -m py_compile "$JQ_COMPAT"
"$SCRIPT" --help >"$TMP_DIR/help"
assert_contains 'PHASE_B_VALIDATION_STORE_' "$TMP_DIR/help"
assert_contains 'Secret values and raw idempotency' "$TMP_DIR/help"
expect_failure missing_bindings "$SCRIPT" --validate
assert_contains 'approved SHA and environment file are required' "$TMP_DIR/missing_bindings.err"

# shellcheck source=../staging-phase-b-part1-acceptance.sh
source "$SCRIPT"
OPS001_EXPECTED_ROOT="$TMP_DIR/staging"
mkdir -p "$OPS001_EXPECTED_ROOT"/{config,evidence,state}; chmod 700 "$OPS001_EXPECTED_ROOT"/{config,evidence,state}
APPROVED_SHA=0123456789abcdef0123456789abcdef01234567
ENV_DIGEST=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
PREFLIGHT_EVIDENCE_SHA256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
ORGANIZATION_ID=10
STORE_NAME=PHASE_B_VALIDATION_STORE_TEST_001
STORE_CODE=PHASE_B_VALIDATION_STORE_TEST_001
require_phase_b_store_identity
[[ "$(acceptance_scope)" == "organization_id=10;store_code=PHASE_B_VALIDATION_STORE_TEST_001;store_name=PHASE_B_VALIDATION_STORE_TEST_001;preflight=$PREFLIGHT_EVIDENCE_SHA256" ]] ||
  fail 'acceptance approval scope is not exact'

STORE_NAME=STG005_WRONG_NAMESPACE
expect_failure invalid_store_name require_phase_b_store_identity
assert_contains 'PHASE_B_VALIDATION_STORE_' "$TMP_DIR/invalid_store_name.err"
STORE_NAME=PHASE_B_VALIDATION_STORE_TEST_001
STORE_CODE=store-lowercase
expect_failure invalid_store_code require_phase_b_store_identity
assert_contains 'PHASE_B_VALIDATION_STORE_' "$TMP_DIR/invalid_store_code.err"

JQ_BIN="$(command -v jq)"
LAST_RESPONSE="$TMP_DIR/forbidden-response.json"
printf '{"success":true,"data":{"access_token":"must-not-escape"}}\n' >"$LAST_RESPONSE"
expect_failure response_redaction reject_secret_response_fields phase_b_acceptance
assert_contains 'forbidden secret-shaped field' "$TMP_DIR/response_redaction.err"

assert_contains '/phase-b/store-provisioning' "$SCRIPT"
assert_contains '/admin/platform/menu/items' "$SCRIPT"
assert_contains '/admin/menu/categories/' "$SCRIPT"
assert_contains '/admin/menu/pricing-policy' "$SCRIPT"
assert_contains '/admin/menu/combo-configuration' "$SCRIPT"
assert_contains '/admin/printing/display-rules' "$SCRIPT"
assert_contains "store_kind = 'VALIDATION_FIXTURE'" "$SCRIPT"
assert_contains "provisioning_source = 'PHASE_B_OWNER_PROVISIONING'" "$SCRIPT"
assert_contains "printing_mode = 'DISABLED'" "$SCRIPT"
assert_contains 'STORE_CREATED_LIVE' "$SCRIPT"
assert_contains 'OPERATIONAL_BASELINE' "$SCRIPT"
assert_contains 'CREATE_FAILURE_ROLLBACK' "$SCRIPT"
assert_contains 'LIVE_FRONTDESK_AND_UNBOUND_PRINTING_MANAGEMENT' "$SCRIPT"
assert_contains '/admin/printing?store_id=$TARGET_STORE_ID' "$SCRIPT"
assert_contains '/admin/printing/devices?store_id=$TARGET_STORE_ID' "$SCRIPT"
assert_contains '.data.operational_state == "LIVE"' "$SCRIPT"
assert_contains 'master_menu.organization_id = $ORGANIZATION_ID' "$SCRIPT"
assert_contains "master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'" "$SCRIPT"
assert_contains "version.fingerprint_sha256 = 'ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c'" "$SCRIPT"
assert_contains 'parent_master_option_key is not null' "$SCRIPT"
assert_not_contains 'parent_option_key is not null' "$SCRIPT"
assert_contains 'combo_group.default_component_code' "$SCRIPT"
assert_not_contains 'is_default::text' "$SCRIPT"
assert_contains 'origin = '\''STORE_ONLY'\''' "$SCRIPT"
assert_contains 'ops001_validate_approval' "$SCRIPT"
assert_contains 'ops001_consume_approval' "$SCRIPT"
assert_contains 'ops001-jq-compat.py' "$SCRIPT"
assert_contains '.data.user.role_code == "OWNER"' "$SCRIPT"
assert_contains '.data.user.organization_id == $organization' "$SCRIPT"
assert_contains '.data.user.username == $login' "$SCRIPT"
assert_not_contains 'select(startswith("STG005_"))' "$SCRIPT"
assert_not_contains 'Owner login identifier is outside the synthetic Staging contract' "$SCRIPT"

printf '{"login_identifier":"owner","login_password":"OwnerPassphrase-123","phase_b_idempotency_key":"phase-b-idem-123456"}\n' >"$TMP_DIR/secret.json"
"$JQ_COMPAT" -e 'type == "object"
    and (.login_identifier | type == "string" and length > 0)
    and (.login_password | type == "string" and length >= 12)
    and (.phase_b_idempotency_key | type == "string" and test("^[A-Za-z0-9._:-]{16,255}$"))' "$TMP_DIR/secret.json"
[[ "$("$JQ_COMPAT" -er '.login_identifier | strings | select(length > 0)' "$TMP_DIR/secret.json")" == owner ]] ||
  fail 'jq compatibility parser did not extract non-STG005 Owner login identifier'
if "$JQ_COMPAT" -er '.login_identifier | strings | select(startswith("STG005_"))' "$TMP_DIR/secret.json" >/dev/null 2>&1; then
  fail 'jq compatibility parser accepted non-STG005 login identifier for an explicit synthetic filter'
fi
[[ "$("$JQ_COMPAT" -er '.phase_b_idempotency_key' "$TMP_DIR/secret.json")" == phase-b-idem-123456 ]] ||
  fail 'jq compatibility parser did not extract Phase B idempotency key'

printf '{"login_identifier":"regional_owner_42","login_password":"OwnerPassphrase-123","phase_b_idempotency_key":"phase-b-idem-abcdef"}\n' >"$TMP_DIR/arbitrary-owner-secret.json"
"$JQ_COMPAT" -e 'type == "object"
    and (.login_identifier | type == "string" and length > 0)
    and (.login_password | type == "string" and length >= 12)
    and (.phase_b_idempotency_key | type == "string" and test("^[A-Za-z0-9._:-]{16,255}$"))' "$TMP_DIR/arbitrary-owner-secret.json"
[[ "$("$JQ_COMPAT" -er '.login_identifier | strings | select(length > 0)' "$TMP_DIR/arbitrary-owner-secret.json")" == regional_owner_42 ]] ||
  fail 'jq compatibility parser rejected an arbitrary Owner login identifier'

printf '{"success":true,"data":{"enabled":true,"profile_code":"ST_DENIS_CANONICAL_PROFILE","profile_version":"v2","master_menu_key":"LANZHOU_CHAIN_MASTER_MENU","master_menu_version":"v1","master_menu_fingerprint_sha256":"abc"}}\n' >"$TMP_DIR/catalog.json"
"$JQ_COMPAT" -e '.data.enabled == true and .data.profile_code == "ST_DENIS_CANONICAL_PROFILE" and .data.profile_version == "v2" and .data.master_menu_key == "LANZHOU_CHAIN_MASTER_MENU" and .data.master_menu_version == "v1"' "$TMP_DIR/catalog.json"
"$JQ_COMPAT" -n \
  --arg store_name PHASE_B_VALIDATION_STORE_TEST_001 \
  --arg store_code PHASE_B_VALIDATION_STORE_TEST_001 \
  --slurpfile catalog "$TMP_DIR/catalog.json" \
  '{
      store_name: $store_name,
      store_code: $store_code,
      profile_code: $catalog[0].data.profile_code,
      profile_version: $catalog[0].data.profile_version,
      master_menu_key: $catalog[0].data.master_menu_key,
      master_menu_version: $catalog[0].data.master_menu_version,
      master_menu_fingerprint_sha256: $catalog[0].data.master_menu_fingerprint_sha256
    }' >"$TMP_DIR/provision-request.json"
assert_contains '"profile_code": "ST_DENIS_CANONICAL_PROFILE"' "$TMP_DIR/provision-request.json"

printf '{"success":true,"data":{"categories":[{"code":"A","items":[{"sku":"SKU_A"}]}]}}\n' >"$TMP_DIR/catalog-response.json"
"$JQ_COMPAT" -e '.data.categories | length > 0 and ([.[] | .items | length] | add) > 0' "$TMP_DIR/catalog-response.json"
"$JQ_COMPAT" -e --arg sku SKU_A '[.data.categories[].items[]? | select(.sku == $sku)] | length == 1' "$TMP_DIR/catalog-response.json"

printf '{"success":true,"data":[{"id":1},{"id":2}]}\n' >"$TMP_DIR/frontdesk-tables-response.json"
"$JQ_COMPAT" -e '.data | length >= 2' "$TMP_DIR/frontdesk-tables-response.json"
printf '{"success":true,"data":[{"id":1}]}\n' >"$TMP_DIR/frontdesk-tables-short-response.json"
expect_failure compat_frontdesk_tables_short "$JQ_COMPAT" -e '.data | length >= 2' "$TMP_DIR/frontdesk-tables-short-response.json"
printf '{"success":true,"data":[]}\n' >"$TMP_DIR/printing-devices-response.json"
"$JQ_COMPAT" -e '.data | length == 0' "$TMP_DIR/printing-devices-response.json"
expect_failure compat_unexpected_device "$JQ_COMPAT" -e '.data | length == 0' "$TMP_DIR/frontdesk-tables-short-response.json"

printf '{"success":true,"data":{"store_id":11,"status":"COMPLETED","result_code":"STORE_CREATED_LIVE","validation_status":"PASS","replayed":true,"counts":{"category_count":2,"item_count":3,"option_count":4,"printing_rule_count":1}}}\n' >"$TMP_DIR/provision-response.json"
"$JQ_COMPAT" -e '.data.status == "COMPLETED" and .data.result_code == "STORE_CREATED_LIVE" and .data.counts.category_count > 0 and .data.counts.item_count > 0 and .data.counts.option_count > 0 and .data.counts.printing_rule_count == 1' "$TMP_DIR/provision-response.json"
[[ "$("$JQ_COMPAT" -er '.data.store_id | numbers' "$TMP_DIR/provision-response.json")" == 11 ]] ||
  fail 'jq compatibility parser did not extract provisioned Store ID'
[[ "$("$JQ_COMPAT" -er '.data.replayed | select(. == true)' "$TMP_DIR/provision-response.json")" == true ]] ||
  fail 'jq compatibility parser did not expose replayed=true for command substitution'

printf '{"success":true,"data":{"store_id":11,"groups":[{"group_id":1,"group_code":"COMBO_EGG","name_zh":"蛋类","name_en":"Egg","selection_rule":"EXACTLY_ONE","required":true,"enabled":true,"display_order":10,"default_component_code":"combo_tea_egg","components":[{"id":101,"group_id":1,"component_group":"COMBO_EGG","component_code":"combo_tea_egg","name_zh":"卤蛋","name_en":"Tea Egg","enabled":true,"display_order":10,"is_default":true,"linked_menu_item_id":null,"business_behavior":"NO_KITCHEN_TASK"},{"id":102,"group_id":1,"component_group":"COMBO_EGG","component_code":"combo_fried_egg","name_zh":"煎蛋","name_en":"Fried Egg","enabled":true,"display_order":20,"is_default":false,"linked_menu_item_id":null,"business_behavior":"NO_KITCHEN_TASK"}]}]}}\n' >"$TMP_DIR/combo-response.json"
"$JQ_COMPAT" --argjson component 102 --argjson store 11 '
    .data as $data |
    {
      store_id: $store,
      groups: [$data.groups[] | {
        group_id, group_code, name_zh, name_en, selection_rule, required, enabled, display_order, default_component_code,
        components: [.components[] | {id, group_id, component_group, component_code, name_zh, name_en, enabled: (if .id == $component then false else .enabled end), display_order, is_default, linked_menu_item_id, business_behavior}]
      }]
    }' "$TMP_DIR/combo-response.json" >"$TMP_DIR/combo-update.json"
python3 - "$TMP_DIR/combo-update.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
assert payload["store_id"] == 11
assert "components" not in payload
assert payload["groups"][0]["components"][0]["enabled"] is True
assert payload["groups"][0]["components"][1]["enabled"] is False
PY

assert_not_contains '/stores/onboard' "$SCRIPT"
assert_not_contains 'menu-clone' "$SCRIPT"
assert_not_contains 'CHINATOWN' "$SCRIPT"
assert_not_contains 'source-store-id' "$SCRIPT"
! grep -Eq '(compose (down|rm)|down -v|docker system|flyway (clean|repair)|production-exact|production-promote)' "$SCRIPT" ||
  fail 'Phase B acceptance helper contains a forbidden lifecycle or Production operation'

echo 'PASS: Phase B Owner Store workflow acceptance is one-action LIVE, secret-safe, and avoids legacy clone/Production paths.'
