#!/usr/bin/env bash
set -Eeuo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
SCRIPT="$REPO_ROOT/deployment/cloud/staging-runtime-evidence.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-ops001-runtime-test.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"
trap '[[ "${BASH_SUBSHELL:-0}" -ne 0 ]] || rm -rf "$TMP_DIR"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() { local label="$1"; shift; if (trap - EXIT; "$@") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then fail "$label unexpectedly passed"; fi; }

bash -n "$SCRIPT"
"$SCRIPT" --help >"$TMP_DIR/help"
assert_contains 'same-image-restart uses' "$TMP_DIR/help"
expect_failure missing_bindings "$SCRIPT" --validate
assert_contains 'exact SHA, environment, and preflight bindings are required' "$TMP_DIR/missing_bindings.err"

# shellcheck source=../staging-runtime-evidence.sh
source "$SCRIPT"
EXPECTED_ROOT="$TMP_DIR/staging"
OPS001_EXPECTED_ROOT="$EXPECTED_ROOT"
mkdir -p "$EXPECTED_ROOT/state"; chmod 700 "$EXPECTED_ROOT/state"
EXPECTED_PROJECT=restaurant-pos-staging
APPROVED_SHA=0123456789abcdef0123456789abcdef01234567
ENV_SNAPSHOT_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
PREFLIGHT_EVIDENCE_SHA256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
READINESS_EVIDENCE_SHA256=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
ACTION=same-image-restart

CALLS="$TMP_DIR/calls"
FLYWAY_ROWS="$(while IFS='|' read -r version script checksum; do printf '%s|%s|%s|true|%s\n' "$version" "$version" "$script" "$checksum"; done < <(expected_flyway_manifest))"
controlled_compose() {
  printf '%s\n' "$*" >>"$CALLS"
  case "$*" in
    'ps -q db') printf 'db-id\n' ;;
    'ps -q backend') printf 'backend-id\n' ;;
    'ps -q nginx') printf 'nginx-id\n' ;;
    'exec -T db '*) printf '%s\n' "$FLYWAY_ROWS" ;;
  esac
}
controlled_docker() {
  local id="${*: -1}"
  if [[ "$*" == *RestartCount* ]]; then printf '%s|sha256:%s-image|running|0\n' "$id" "$id"; else printf 'running|healthy\n'; fi
}
project_fingerprint() { printf 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\n'; }
LOCK_CONTENDED=false
acquire_action_lock() { printf 'lock\n' >>"$CALLS"; [[ "$LOCK_CONTENDED" != true ]] || die 'another AL-003S action is already running'; ACTION_LOCK_FD=""; }
ops001_assert_approval_unchanged() { :; }
ops001_consume_approval() { printf 'consume\n' >>"$CALLS"; }
validate_release_and_evidence() { printf 'exact-runtime\n' >>"$CALLS"; }
assert_snapshot_integrity() { printf 'snapshot\n' >>"$CALLS"; }
assert_release_identity() { printf 'release\n' >>"$CALLS"; }
validate_running_backend_identity() { printf 'running-identity\n' >>"$CALLS"; }
validate_readiness_evidence() { printf 'readiness\n' >>"$CALLS"; }
validate_runtime_approval() { printf 'approval\n' >>"$CALLS"; }
mark_action_blocked() { printf 'blocked:%s\n' "$1" >>"$CALLS"; }
CURL_BIN="$TMP_DIR/curl"
cat >"$CURL_BIN" <<'EOF'
#!/usr/bin/env bash
printf '200'
EOF
chmod +x "$CURL_BIN"

emit_evidence COLLECT >"$TMP_DIR/evidence"
assert_contains 'OPS001_RUNTIME|COLLECT|FLYWAY|count=10|max_version=10|digest=' "$TMP_DIR/evidence"
assert_contains 'OPS001_RUNTIME|COLLECT|CONTAINER|backend|backend-id|sha256:backend-id-image|0' "$TMP_DIR/evidence"
assert_not_contains "$FLYWAY_ROWS" "$TMP_DIR/evidence"

for contended_action in collect-evidence same-image-restart; do
  : >"$CALLS"; ACTION="$contended_action"; LOCK_CONTENDED=true
  expect_failure "${contended_action}_contention" run_runtime_action
  assert_contains 'another AL-003S action is already running' "$TMP_DIR/${contended_action}_contention.err"
  assert_contains 'lock' "$CALLS"
  assert_not_contains 'consume' "$CALLS"
done
LOCK_CONTENDED=false; ACTION=collect-evidence; : >"$CALLS"
run_runtime_action >"$TMP_DIR/locked-collect-evidence"
[[ "$(sed -n '1p' "$CALLS")" == lock ]] || fail 'collect did not acquire the shared lock first'
[[ "$(rg -n '^consume$' "$CALLS" | cut -d: -f1)" -gt "$(rg -n '^approval$' "$CALLS" | head -n 1 | cut -d: -f1)" ]] || fail 'collect consumed approval before locked validation'
assert_contains 'OPS001_RUNTIME|COLLECT|STATUS|PASS' "$TMP_DIR/locked-collect-evidence"

: >"$CALLS"
ACTION=same-image-restart
same_image_restart >"$TMP_DIR/restart-evidence"
assert_contains 'stop nginx backend db' "$CALLS"
assert_contains 'start db' "$CALLS"
assert_contains 'start backend' "$CALLS"
assert_contains 'start nginx' "$CALLS"
[[ "$(rg -c '^(snapshot|release|running-identity|readiness|approval)$' "$CALLS")" -ge 7 ]] || fail 'post-lock integrity rechecks are missing'
assert_contains 'OPS001_RUNTIME|AFTER_RESTART|STATUS|PASS' "$TMP_DIR/restart-evidence"
[[ "$RESTART_MUTATION_STARTED" == false ]] || fail 'restart mutation flag was not cleared'

VALID_FLYWAY_ROWS="$FLYWAY_ROWS"
FLYWAY_ROWS="$(printf '%s\n' "$VALID_FLYWAY_ROWS" | sed '2s/|true|/|false|/')"
expect_failure failed_flyway flyway_digest
assert_contains 'invalid or failed row' "$TMP_DIR/failed_flyway.err"
FLYWAY_ROWS="$(printf '%s\n' "$VALID_FLYWAY_ROWS" | sed '2s/|true|/|t|/')"
expect_failure abbreviated_success_flyway flyway_digest
assert_contains 'invalid or failed row' "$TMP_DIR/abbreviated_success_flyway.err"
FLYWAY_ROWS="$(printf '%s\n' "$VALID_FLYWAY_ROWS" | sed '5d')"
expect_failure missing_flyway flyway_digest
assert_contains 'does not exactly match' "$TMP_DIR/missing_flyway.err"
FLYWAY_ROWS="$(printf '%s\n' "$VALID_FLYWAY_ROWS" | sed '3s/V3__/V3__unexpected_/')"
expect_failure script_mismatch flyway_digest
assert_contains 'does not exactly match' "$TMP_DIR/script_mismatch.err"
FLYWAY_ROWS="$(printf '%s\n' "$VALID_FLYWAY_ROWS" | sed '4s/|[-0-9][0-9]*$/|123456789/')"
expect_failure checksum_mismatch flyway_digest
assert_contains 'does not exactly match' "$TMP_DIR/checksum_mismatch.err"
FLYWAY_ROWS="$(printf '%s\n' "$VALID_FLYWAY_ROWS"; printf '%s\n' "$(printf '%s\n' "$VALID_FLYWAY_ROWS" | tail -n 1)")"
expect_failure duplicate_flyway flyway_digest
assert_contains 'duplicated or unordered' "$TMP_DIR/duplicate_flyway.err"

# Image/container drift and health failure are blocking findings.
FLYWAY_ROWS="$VALID_FLYWAY_ROWS"
DRIFT=false
container_identity_lines() {
  if [[ "$DRIFT" == true ]]; then printf 'db|db-new|sha256:new|running|0\nbackend|backend-id|sha256:backend|running|0\nnginx|nginx-id|sha256:nginx|running|0\n';
  else printf 'db|db-id|sha256:db|running|0\nbackend|backend-id|sha256:backend|running|0\nnginx|nginx-id|sha256:nginx|running|0\n'; fi
}
capture_before_restart() { BEFORE_PROJECT_FINGERPRINT="$(project_fingerprint)"; BEFORE_FLYWAY_DIGEST="$(flyway_digest)"; DRIFT=false; BEFORE_CONTAINER_IDS="$(container_identity_lines | cut -d'|' -f1-3)"; DRIFT=true; }
expect_failure identity_drift same_image_restart
assert_contains 'container or image identity changed' "$TMP_DIR/identity_drift.err"

grep -Fq 'controlled_compose stop nginx backend db' "$SCRIPT" || fail 'ordered stop is missing'
grep -Fq 'controlled_compose start db' "$SCRIPT" || fail 'same-container start is missing'
grep -Fq 'success::text' "$SCRIPT" || fail 'runtime query no longer emits PostgreSQL canonical boolean text'
lock_line="$(rg -n '^  acquire_action_lock$' "$SCRIPT" | tail -n 1 | cut -d: -f1)"
consume_line="$(rg -n '^  ops001_consume_approval$' "$SCRIPT" | cut -d: -f1)"
[[ "$lock_line" -lt "$consume_line" ]] || fail 'runtime approval is consumed before the shared lock'
python3 - "$REPO_ROOT" <<'PY'
from pathlib import Path
import re, sys, zlib
root = Path(sys.argv[1])
manifest = {}
for line in (root / 'deployment/cloud/ops001-flyway-checksums.txt').read_text().splitlines():
    if not line or line.startswith('#'):
        continue
    version, script, checksum = line.split('|')
    manifest[(int(version), script)] = int(checksum)
computed = {}
for path in (root / 'backend/src/main/resources/db/migration').glob('V*__*.sql'):
    match = re.match(r'V(\d+)__', path.name)
    if not match:
        continue
    crc = 0
    with path.open(encoding='utf-8-sig', newline=None) as source:
        for sql_line in source.read().splitlines():
            crc = zlib.crc32(sql_line.lstrip('\ufeff').encode('utf-8'), crc)
    computed[(int(match.group(1)), path.name)] = crc if crc < 2**31 else crc - 2**32
if computed != manifest:
    raise SystemExit('trusted Flyway checksum manifest differs from repository SQL')
PY
! grep -Eq '(compose (down|rm)|down -v|image pull|build |flyway (clean|repair)|docker system)' "$SCRIPT" || fail 'runtime helper contains forbidden lifecycle operation'
echo 'PASS: OPS-001 runtime collector and same-image restart preserve image/container/Flyway identity and fail closed.'
