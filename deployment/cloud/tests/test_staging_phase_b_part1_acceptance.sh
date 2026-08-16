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
assert_contains "printing_mode = 'MOCK'" "$SCRIPT"
assert_contains 'master_menu.organization_id = $ORGANIZATION_ID' "$SCRIPT"
assert_contains "master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'" "$SCRIPT"
assert_contains "version.fingerprint_sha256 = 'e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7'" "$SCRIPT"
assert_contains 'parent_master_option_key is not null' "$SCRIPT"
assert_not_contains 'parent_option_key is not null' "$SCRIPT"
assert_contains 'combo_group.default_component_code' "$SCRIPT"
assert_not_contains 'is_default::text' "$SCRIPT"
assert_contains 'origin = '\''STORE_ONLY'\''' "$SCRIPT"
assert_contains 'ops001_validate_approval' "$SCRIPT"
assert_contains 'ops001_consume_approval' "$SCRIPT"
assert_contains 'ops001-jq-compat.py' "$SCRIPT"

printf '{"login_identifier":"STG005_OWNER_TEST","login_password":"OwnerPassphrase-123","phase_b_idempotency_key":"phase-b-idem-123456"}\n' >"$TMP_DIR/secret.json"
"$JQ_COMPAT" -e 'type == "object"
    and (.login_identifier | type == "string" and length > 0)
    and (.login_password | type == "string" and length >= 12)
    and (.phase_b_idempotency_key | type == "string" and test("^[A-Za-z0-9._:-]{16,255}$"))' "$TMP_DIR/secret.json"
[[ "$("$JQ_COMPAT" -er '.phase_b_idempotency_key' "$TMP_DIR/secret.json")" == phase-b-idem-123456 ]] ||
  fail 'jq compatibility parser did not extract Phase B idempotency key'

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

printf '{"success":true,"data":{"store_id":11,"status":"COMPLETED","result_code":"PHASE_B_STORE_PROVISIONED","validation_status":"PASS","replayed":true,"counts":{"category_count":2,"item_count":3,"option_count":4,"printing_rule_count":1}}}\n' >"$TMP_DIR/provision-response.json"
"$JQ_COMPAT" -e '.data.status == "COMPLETED" and .data.result_code == "PHASE_B_STORE_PROVISIONED" and .data.counts.category_count > 0 and .data.counts.item_count > 0 and .data.counts.option_count > 0 and .data.counts.printing_rule_count == 1' "$TMP_DIR/provision-response.json"
[[ "$("$JQ_COMPAT" -er '.data.store_id | numbers' "$TMP_DIR/provision-response.json")" == 11 ]] ||
  fail 'jq compatibility parser did not extract provisioned Store ID'

assert_not_contains '/stores/onboard' "$SCRIPT"
assert_not_contains 'menu-clone' "$SCRIPT"
assert_not_contains 'CHINATOWN' "$SCRIPT"
assert_not_contains 'source-store-id' "$SCRIPT"
! grep -Eq '(compose (down|rm)|down -v|docker system|flyway (clean|repair)|production-exact|production-promote)' "$SCRIPT" ||
  fail 'Phase B acceptance helper contains a forbidden lifecycle or Production operation'

echo 'PASS: Phase B Part 1 acceptance helper is canonical-provisioning scoped, secret-safe, and avoids legacy clone/Production paths.'
