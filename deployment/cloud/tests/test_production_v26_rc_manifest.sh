#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROMOTION="$ROOT/deployment/cloud/production-v26-exact-artifact-promote.sh"
RECOVERY="$ROOT/deployment/cloud/production-v26-recover.sh"
TEMP_ROOT="$(mktemp -d "$ROOT/.production-v26-rc-test.XXXXXX")"
trap 'rm -rf -- "$TEMP_ROOT"' EXIT

# The scripts check for GNU timeout before parsing the RC. No bounded command is
# reached in these fail-closed tests, so this sentinel must never be executed.
mkdir "$TEMP_ROOT/bin"
mkdir "$TEMP_ROOT/shadow"
printf 'raise RuntimeError("ambient json.py was imported")\n' >"$TEMP_ROOT/shadow/json.py"
printf '#!/bin/sh\nexit 97\n' >"$TEMP_ROOT/bin/timeout"
chmod 700 "$TEMP_ROOT/bin/timeout"
printf '%s\n' '#!/bin/sh' \
  'if [ "$1" = "-c" ] && [ "$2" = "%a|%u" ]; then printf "600|%s\\n" "$(id -u)"; exit 0; fi' \
  'exec /usr/bin/stat "$@"' >"$TEMP_ROOT/bin/stat"
chmod 700 "$TEMP_ROOT/bin/stat"

# macOS ships Bash 3; the release host uses Bash 5. Supply only the Bash 4
# mapfile primitive needed to exercise pre-path RC parsing on local workstations.
if ! type mapfile >/dev/null 2>&1; then
  mapfile() {
    local array_name="MAPFILE" line quoted index=0
    [[ "${1:-}" == "-t" ]] && shift
    [[ $# -eq 1 ]] && array_name="$1"
    eval "$array_name=()"
    while IFS= read -r line; do
      printf -v quoted '%q' "$line"
      eval "$array_name[$index]=$quoted"
      index=$((index + 1))
    done
  }
  export -f mapfile
fi

make_manifest() {
  local output="$1" field="$2" value="$3" value_type="${4:-string}"
  python3 - "$output" "$field" "$value" "$value_type" <<'PY'
import json,sys
sha="a"*40
digest="b"*64
image="sha256:"+"c"*64
d={
 "status":"RC_FROZEN","rc_id":"RC-test","source_sha":sha,"source_main_ancestry":"PASS",
 "production_previous_sha":"d"*40,"production_control_checkout_sha":"e"*40,
 "previous_production_rc_file":"/missing/previous.json","previous_production_rc_sha256":digest,
 "postgres_image_id":image,"backend_image_tag":"accepted:"+sha,"backend_image_id":image,
 "frontend_image_tag":"accepted:"+sha,"frontend_image_id":image,
 "rollback_backend_image_id":image,"rollback_frontend_image_id":image,
 "resolved_compose_sha256":digest,"tooling_commit_sha":"f"*40,
 "promotion_helper_sha256":digest,"promotion_override_sha256":digest,
 "recovery_helper_sha256":digest,"recovery_override_sha256":digest,
 "rehearsal_helper_sha256":digest,"smoke_helper_sha256":digest,
 "data_contract_sha256":digest,"evidence_contract_sha256":digest,
 "flyway_manifest_sha256":digest,"backup_helper_sha256":digest,
 "staging_acceptance_file":"/missing/staging.md","staging_acceptance_sha256":digest,
 "staging_repair_evidence_file":"/missing/repair.md","staging_repair_evidence_sha256":digest,
 "fresh_backup_file":"/missing/backup.dump","fresh_backup_sha256":digest,
 "rehearsal_evidence_file":"/missing/rehearsal.log","rehearsal_evidence_sha256":digest,
 "production_business_fingerprint":digest,"production_printing_fingerprint":digest,
 "backup_flyway_target":"V10","flyway_target":"V26",
 "production_backup_result":"PASS","production_backup_restore_result":"PASS",
 "migration_rehearsal_result":"PASS","target_app_boot_result":"PASS",
 "production_data_integrity_result":"PASS","read_smoke_result":"PASS",
 "write_smoke_result":"PASS","android_pad_compatibility_result":"PASS",
 "store_organization_isolation_result":"PASS","recovery_proof_result":"PASS",
 "staging_accepted_artifact_result":"VERIFIED","agent_6_release_review":"ACCEPT",
 "production_preflight_result":"PASS"
}
d[sys.argv[2]] = (sys.argv[3] == "true") if sys.argv[4] == "bool" else sys.argv[3]
with open(sys.argv[1],"w",encoding="utf-8") as handle: json.dump(d,handle,separators=(",",":"))
PY
  chmod 600 "$output"
}

expect_rejected() {
  local script="$1" field="$2" value="$3" expected="$4" value_type="${5:-string}"
  local manifest="$TEMP_ROOT/${field}-${value_type}.json" output status digest
  make_manifest "$manifest" "$field" "$value" "$value_type"
  digest="$(sha256sum "$manifest" | awk '{print $1}')"
  set +e
  output="$(PYTHONPATH="$TEMP_ROOT/shadow" PATH="$TEMP_ROOT/bin:$PATH" "$script" --execute --rc-manifest "$manifest" --rc-manifest-sha256 "$digest" 2>&1)"
  status=$?
  set -e
  [[ "$status" -ne 0 && "$output" == *"$expected"* ]] || {
    printf 'expected rejection was not observed: %s %s\n%s\n' "$field" "$value" "$output" >&2
    exit 1
  }
}

expect_rejected "$PROMOTION" production_backup_result ACCEPT 'RC automated gate type/value differs'
expect_rejected "$PROMOTION" staging_accepted_artifact_result PASS 'Staging artifact gate must be VERIFIED'
expect_rejected "$PROMOTION" agent_6_release_review PASS 'Agent 6 release gate must be ACCEPT'
expect_rejected "$PROMOTION" production_preflight_result VERIFIED 'Production preflight gate must be PASS'
expect_rejected "$PROMOTION" production_backup_result true 'RC manifest is invalid' bool
expect_rejected "$PROMOTION" unexpected_extra forbidden 'RC manifest is invalid'
expect_rejected "$RECOVERY" android_pad_compatibility_result VERIFIED 'RC automated gate type/value differs'
expect_rejected "$RECOVERY" agent_6_release_review PASS 'Agent 6 release gate must be ACCEPT'
expect_rejected "$RECOVERY" unexpected_extra forbidden 'RC manifest is invalid'

execute_manifest="$TEMP_ROOT/execute-count.json"
make_manifest "$execute_manifest" status RC_FROZEN
execute_digest="$(sha256sum "$execute_manifest" | awk '{print $1}')"
for execute_args in "" "--execute --execute"; do
  set +e
  execute_output="$(PATH="$TEMP_ROOT/bin:$PATH" "$RECOVERY" $execute_args --rc-manifest "$execute_manifest" --rc-manifest-sha256 "$execute_digest" 2>&1)"
  execute_status=$?
  set -e
  [[ "$execute_status" -ne 0 && "$execute_output" == *'exactly one --execute is required'* ]] || {
    printf 'recovery execute-count guard was not observed\n%s\n' "$execute_output" >&2
    exit 1
  }
done

printf 'Production V26 typed RC manifest negative tests: PASS\n'
