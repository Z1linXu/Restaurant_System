#!/usr/bin/env bash
set -Eeuo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
SCRIPT="$REPO_ROOT/deployment/cloud/staging-phase-b-part2-acceptance.sh"
JQ_COMPAT="$REPO_ROOT/deployment/cloud/ops001-jq-compat.py"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-phase-b-part2-acceptance-test.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() {
  local label="$1"
  shift
  if (trap - EXIT; "$@") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then
    fail "$label unexpectedly passed"
  fi
}

bash -n "$SCRIPT"
python3 -m py_compile "$JQ_COMPAT"
printf '%s\n' '{"data":{"ready":false,"readiness_status":"NOT_READY","checks":[{"code":"DEVICE_READINESS","status":"FAIL"}]}}' >"$TMP_DIR/readiness.json"
"$JQ_COMPAT" -e '.data.ready == false and .data.readiness_status == "NOT_READY" and any(.data.checks[]; .code == "DEVICE_READINESS" and .status == "FAIL")' "$TMP_DIR/readiness.json" >/dev/null
printf '%s\n' '{"data":{"device_token":"one-time"}}' >"$TMP_DIR/response.json"
"$JQ_COMPAT" -e '[paths(scalars)[] | tostring | select(test("password_hash|device_token_hash|printer_endpoint|ip_address"; "i"))] | length == 0' "$TMP_DIR/response.json" >/dev/null
"$SCRIPT" --help >"$TMP_DIR/help"
assert_contains 'PHASE_B_VALIDATION_STORE_' "$TMP_DIR/help"
assert_contains '--execute-runtime' "$TMP_DIR/help"
expect_failure missing_bindings "$SCRIPT" --execute-runtime
assert_contains 'approved SHA and environment file are required' "$TMP_DIR/missing_bindings.err"

assert_contains '/phase-b/part2/readiness' "$SCRIPT"
assert_contains '/phase-b/part2/provision' "$SCRIPT"
assert_contains '/phase-b/part2/activate' "$SCRIPT"
assert_contains '/devices/readiness-proof' "$SCRIPT"
assert_contains 'store_provisioning_part2_requests' "$SCRIPT"
assert_contains 'store_activation_requests' "$SCRIPT"
assert_contains 'ROLLBACK_NO_PARTIAL_ROWS' "$SCRIPT"
assert_contains 'ACTIVATION_CONCURRENCY_LEDGER' "$SCRIPT"
assert_contains 'PRODUCTION_AND_REAL_HARDWARE_UNTOUCHED' "$SCRIPT"
assert_contains 'ops001_validate_approval' "$SCRIPT"
assert_contains 'ops001_consume_approval' "$SCRIPT"
assert_contains 'ops001-jq-compat.py' "$SCRIPT"
assert_contains 'readiness_fingerprint' "$SCRIPT"
assert_contains 'FLYWAY_V24' "$SCRIPT"
assert_contains 'store_readiness_evidence_history' "$SCRIPT"
assert_contains 'printing_mode = '\''MOCK'\''' "$SCRIPT"
assert_contains 'physical_binding_status = '\''UNBOUND'\''' "$SCRIPT"
assert_not_contains 'CHINATOWN' "$SCRIPT"
assert_not_contains 'SAINTE' "$SCRIPT"
! grep -Eq '(compose (down|rm)|down -v|docker system|flyway (clean|repair)|production-exact|production-promote|printer endpoint|PAD_DIRECT)' "$SCRIPT" ||
  fail 'Part 2 acceptance helper contains a forbidden lifecycle, hardware, or Production operation'

printf 'PASS: Phase B Part 2 acceptance helper is exact-SHA scoped, rollback/idempotency tested, and real-hardware safe.\n'
