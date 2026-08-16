#!/usr/bin/env bash
set -Eeuo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
SCRIPT="$REPO_ROOT/deployment/cloud/staging-phase-b-part1-acceptance.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-phase-b-acceptance-test.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"
trap '[[ "${BASH_SUBSHELL:-0}" -ne 0 ]] || rm -rf "$TMP_DIR"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() { local label="$1"; shift; if (trap - EXIT; "$@") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then fail "$label unexpectedly passed"; fi; }

bash -n "$SCRIPT"
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

assert_not_contains '/stores/onboard' "$SCRIPT"
assert_not_contains 'menu-clone' "$SCRIPT"
assert_not_contains 'CHINATOWN' "$SCRIPT"
assert_not_contains 'source-store-id' "$SCRIPT"
! grep -Eq '(compose (down|rm)|down -v|docker system|flyway (clean|repair)|production-exact|production-promote)' "$SCRIPT" ||
  fail 'Phase B acceptance helper contains a forbidden lifecycle or Production operation'

echo 'PASS: Phase B Part 1 acceptance helper is canonical-provisioning scoped, secret-safe, and avoids legacy clone/Production paths.'
