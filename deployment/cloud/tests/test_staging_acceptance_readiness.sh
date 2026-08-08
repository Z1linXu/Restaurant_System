#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
READINESS="$REPOSITORY_ROOT/deployment/cloud/staging-acceptance-readiness.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-al003s-readiness.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"

trap 'rm -rf "$TMP_DIR"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }

bash -n "$READINESS"
"$READINESS" --help >"$TMP_DIR/help.out"
assert_contains 'The command is passive' "$TMP_DIR/help.out"
assert_contains '--production-project cloud' "$TMP_DIR/help.out"

# Source the collector and replace all runtime readers with deterministic local
# fixtures. This verifies evidence formatting without Docker, curl, or a server.
# shellcheck source=../staging-acceptance-readiness.sh
source "$READINESS"
APPROVED_SHA="0123456789abcdef0123456789abcdef01234567"
ENV_SNAPSHOT_SHA256="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
VALIDATED_PREFLIGHT_SHA256="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
PRODUCTION_PROJECT="$EXPECTED_PRODUCTION_PROJECT"
MIN_AVAILABLE_MEMORY_KB="1048576"
MIN_CPU_COUNT="2"
MIN_FREE_DISK_KB="1048576"
MAX_LOAD_PER_CPU_MILLI="1000"
available_memory_kb() { printf '2097152\n'; }
cpu_count() { printf '4\n'; }
free_disk_kb() { printf '4194304\n'; }
load_per_cpu_milli() { printf '250\n'; }
project_fingerprint() {
  case "$1" in
    "$EXPECTED_PROJECT") printf 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\n' ;;
    "$EXPECTED_PRODUCTION_PROJECT") printf 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\n' ;;
    *) return 1 ;;
  esac
}
emit_readiness_evidence >"$TMP_DIR/readiness.out"
assert_contains 'READINESS|STATUS|PASS' "$TMP_DIR/readiness.out"
assert_contains "READINESS|APPROVED_SHA|$APPROVED_SHA" "$TMP_DIR/readiness.out"
assert_contains "READINESS|STAGING_PROJECT|$EXPECTED_PROJECT" "$TMP_DIR/readiness.out"
assert_contains "READINESS|PRODUCTION_PROJECT|$EXPECTED_PRODUCTION_PROJECT" "$TMP_DIR/readiness.out"
assert_contains 'READINESS|OBSERVED_AVAILABLE_MEMORY_KB|2097152' "$TMP_DIR/readiness.out"
assert_contains 'READINESS|OBSERVED_CPU_COUNT|4' "$TMP_DIR/readiness.out"
assert_contains 'READINESS|OBSERVED_FREE_DISK_KB|4194304' "$TMP_DIR/readiness.out"
assert_contains 'READINESS|OBSERVED_LOAD_PER_CPU_MILLI|250' "$TMP_DIR/readiness.out"
assert_contains 'READINESS|SUMMARY|PASS' "$TMP_DIR/readiness.out"

! grep -Eq '(docker compose (up|down|run|start|stop|restart|build|pull)|Flyway clean|pg_restore|pg_dump)' "$READINESS" ||
  fail 'readiness collector contains a mutating runtime command'

echo 'PASS: AL-003S readiness collector emits only bounded, sanitized, passive evidence fields.'
