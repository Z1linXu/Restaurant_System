#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BACKUP="$ROOT/deployment/cloud/production-backup-rehearsal.sh"
REHEARSAL="$ROOT/deployment/cloud/production-v10-v26-rehearsal.sh"
PROMOTION="$ROOT/deployment/cloud/production-v26-exact-artifact-promote.sh"
RECOVERY="$ROOT/deployment/cloud/production-v26-recover.sh"
SMOKE="$ROOT/deployment/cloud/production-v26-smoke.py"
EVIDENCE="$ROOT/deployment/cloud/production-v26-evidence.py"
DATA_CONTRACT="$ROOT/deployment/cloud/production-v26-data-contract.sh"
OVERRIDE="$ROOT/deployment/cloud/docker-compose.production-v26-promotion.yml"
RECOVERY_OVERRIDE="$ROOT/deployment/cloud/docker-compose.production-v26-recovery.yml"
MANIFEST="$ROOT/deployment/cloud/ops001-flyway-v26-checksums.txt"

for script in "$BACKUP" "$REHEARSAL" "$PROMOTION" "$RECOVERY" "$DATA_CONTRACT"; do
  bash -n "$script"
done
python3 -m py_compile "$SMOKE" "$EVIDENCE"
python3 -m unittest "$ROOT/deployment/cloud/tests/test_production_v26_smoke.py" "$ROOT/deployment/cloud/tests/test_production_v26_evidence.py"
bash "$ROOT/deployment/cloud/tests/test_production_v26_recovery_policy.sh"

diff -u <(grep -E '^[0-9]+[|]' "$ROOT/deployment/cloud/ops001-flyway-checksums.txt") <(grep -E '^[0-9]+[|]' "$MANIFEST")
[[ "$(grep -Ec '^[0-9]+[|]' "$MANIFEST")" == "26" ]]

grep -Fq -- 'network create --internal' "$REHEARSAL"
grep -Fq -- '--network "$NETWORK" --network-alias db' "$REHEARSAL"
grep -Fq -- '-p 127.0.0.1::8080' "$REHEARSAL"
grep -Fq -- '-p 127.0.0.1::80' "$REHEARSAL"
grep -Fq 'REHEARSAL_JWT_SECRET="$(openssl rand -hex 48)"' "$REHEARSAL"
grep -Fq -- '--evidence-run-id "$RUN_ID"' "$REHEARSAL"
grep -Fq -- '--expected-db-container-id "$DB_CONTAINER_ID"' "$REHEARSAL"
grep -Fq -- '--expected-api-container-id "$FRONTEND_CONTAINER_ID"' "$REHEARSAL"
grep -Fq 'RESOURCE_CLEANUP|run_id=' "$REHEARSAL"
grep -Fq 'REHEARSAL|run_id=' "$REHEARSAL"
grep -Fq 'production-v26-rehearsal=' "$REHEARSAL"
grep -Fq 'docker_default volume rm "$VOLUME"' "$REHEARSAL"
grep -Fq 'timeout -s TERM -k 10 840 pg_restore' "$REHEARSAL"
grep -Fq 'OLD_PRODUCTION_APP_ON_V26_SCHEMA=' "$REHEARSAL"
grep -Fq 'production-backup-integrity-' "$BACKUP"
grep -Fq 'target restart did not prove no pending migration' "$REHEARSAL"
grep -Fq 'ANDROID_COMPATIBILITY|run_id=' "$REHEARSAL"
grep -Fq 'accepted backend requires a different Android app tree' "$REHEARSAL"
grep -Fq 'Android API paths or required device headers changed' "$REHEARSAL"
grep -Fq 'RECOVERY_RESTORE_FAILURE_PROOF|run_id=' "$REHEARSAL"
grep -Fq 'RECOVERY_PROOF|run_id=' "$REHEARSAL"
grep -Fq 'rename_rehearsal_database "$PRIMARY_DB" "$QUARANTINED_V26_DB"' "$REHEARSAL"
! grep -Fq 'stop_db_and_volume' <(tail -n 30 "$REHEARSAL")

grep -Fq 'to_jsonb(t)' "$DATA_CONTRACT"
grep -Fq "'printer_id'" "$DATA_CONTRACT" || grep -Fq 'to_jsonb(t)' "$DATA_CONTRACT"
grep -Fq 'v26_business_fingerprint' "$PROMOTION"
grep -Fq 'v26_printing_fingerprint' "$PROMOTION"
grep -Fq 'v26_additive_contract' "$PROMOTION"
grep -Fq 'fresh backup/rehearsal fingerprint differs from current Production' "$PROMOTION"
grep -Fq -- '--scope full' "$PROMOTION"
grep -Fq 'EVIDENCE_HELPER_DIGEST' "$PROMOTION"

grep -Fq 'FLYWAY_TARGET: "26"' "$OVERRIDE"
grep -Fq 'FLYWAY_TARGET: "10"' "$RECOVERY_OVERRIDE"
grep -Fq 'no-build --pull never backend' "$PROMOTION"
grep -Fq 'no-build --pull never nginx' "$PROMOTION"
grep -Fq -- '--snapshot|--validate|--execute' "$PROMOTION"
! grep -Fq -- '--second-start' "$PROMOTION"
grep -Fq 'same-image restart did not prove no pending migration' "$PROMOTION"
grep -Fq 'backend_private_ip' "$PROMOTION"
grep -Fq 'current backend private Docker health path is unavailable' "$PROMOTION"
grep -Fq 'AUTO_RECOVERY|rc_id=' "$PROMOTION"
grep -Fq 'PREMUTATION_SERVICE_RESTORE|rc_id=' "$PROMOTION"
grep -Fq 'Production data/config changed while entering maintenance' "$PROMOTION"
grep -Fq 'UNRECOVERABLE_OWNER_GATE' "$PROMOTION"
grep -Fq 'production-v26-recover.sh' "$PROMOTION"
grep -Fq 'ROLLBACK_AUTHORITY|database_restore=closed|before_public_edge=true|result=PASS' "$PROMOTION"
grep -Fq 'PRIVATE_TARGET_SMOKE|edge_public=false' "$PROMOTION"
grep -Fq -- '--finalize-edge' "$PROMOTION"
grep -Fq 'TEMP_DB="v10_recovery_' "$RECOVERY"
grep -Fq 'QUARANTINE_DB="v26_quarantine_' "$RECOVERY"
grep -Fq 'primary database remains intact' "$RECOVERY"
grep -Fq 'RECOVERY_TEMP_RESTORE|flyway=V10-exact|fingerprints=verified|primary_untouched=true|result=PASS' "$RECOVERY"
grep -Fq 'mode=validated-temp-db-switch' "$RECOVERY"
grep -Fq 'exactly one --execute is required' "$RECOVERY"
grep -Fq 'optional_app_container' "$RECOVERY"
grep -Fq 'validate_project_inventory' "$RECOVERY"
[[ "$(grep -Fc 'validate_project_inventory' "$RECOVERY")" -ge 3 ]]
grep -Fq 'DB_NAME","DB_USER","JWT_SECRET' "$RECOVERY"
grep -Fq 'recovery Compose DB identity differs from running DB container' "$RECOVERY"
grep -Fq 'resolved Compose DB identity differs from running DB container' "$PROMOTION"
! grep -Fq 'source "$ENV_FILE"' "$RECOVERY"
! grep -Fq 'source "$ENV_FILE"' "$PROMOTION"
! grep -Fq 'dropdb --if-exists --force -U "$POSTGRES_USER" "$POSTGRES_DB"' "$RECOVERY"
! grep -Fq 'createdb -U "$POSTGRES_USER" -O "$POSTGRES_USER" "$POSTGRES_DB"' "$RECOVERY"
grep -Fq 'Production data changed outside the rehearsed migration contract' "$RECOVERY"
grep -Fq 'RECOVERY|rc_id=' "$RECOVERY"

python3 - "$PROMOTION" <<'PY'
import pathlib,sys
text=pathlib.Path(sys.argv[1]).read_text(encoding='utf-8')
private=text.index('run_private_target_smoke\nROLLBACK_WINDOW_CLOSED="true"')
closed=text.index('ROLLBACK_WINDOW_CLOSED="true"', private)
edge=text.index('up -d --no-deps --no-build --pull never nginx', closed)
assert private < closed < edge
assert 'run_read_smoke read' in text[edge:]
assert 'RECOVERY_HELPER' not in text[edge:]
PY

for script in "$BACKUP" "$REHEARSAL" "$PROMOTION" "$RECOVERY"; do
  grep -Fq 'python3 -I' "$script"
  ! grep -Fq -- '--untracked-files=no' "$script"
  ! grep -Eq 'inspect .*\|\| true|! docker_default .*inspect|< <\(' "$script"
done
grep -Fq 'exact_container_id' "$BACKUP"
grep -Fq 'exact_container_id' "$REHEARSAL"
grep -Fq 'exact_container_id' "$PROMOTION"
grep -Fq 'exact_container_id' "$RECOVERY"

grep -Fq 'RC automated gate type/value differs' "$PROMOTION"
grep -Fq 'Staging artifact gate must be VERIFIED' "$PROMOTION"
grep -Fq 'Agent 6 release gate must be ACCEPT' "$PROMOTION"
grep -Fq 'Production preflight gate must be PASS' "$PROMOTION"
grep -Fq 'previous Production SHA is not bound to current rollback images' "$PROMOTION"
grep -Fq 'current Staging runtime no longer uses accepted images' "$PROMOTION"
grep -Fq 'Production data/config fingerprint changed after RC freeze' "$PROMOTION"
grep -Fq 'Production DB container identity changed' "$PROMOTION"
grep -Fq 'run_read_smoke read' "$PROMOTION"
! grep -Fq -- '--mode write' "$PROMOTION"

grep -Fq 'write smoke refuses live Production/Staging DB containers' "$SMOKE"
grep -Fq 'write-target container is not owned by this rehearsal' "$SMOKE"
grep -Fq 'write-target API URL is not bound to the exact rehearsal frontend' "$SMOKE"
grep -Fq 'write-target network members differ from the exact rehearsal stack' "$SMOKE"
grep -Fq 'wrong_organization_real_store=PASS' "$SMOKE"
grep -Fq 'historical_detail=PASS' "$SMOKE"
grep -Fq 'mock_endpoint_free=PASS' "$SMOKE"
grep -Fq 'inventory=PASS' "$SMOKE"

for script in "$BACKUP" "$REHEARSAL" "$PROMOTION" "$RECOVERY"; do
  grep -Fq 'timeout --foreground --kill-after=10s' "$script"
  ! grep -Eq 'docker (system|volume) prune|docker image prune|builder prune|down( -v)?|flyway (clean|repair)|rm -rf|yes[[:space:]]*\|' "$script"
  ! grep -Eq "printf [^#]*'[^']*' +\+ +" "$script"
done
! grep -Eq 'compose .*build|compose .*pull' "$PROMOTION"

find "$ROOT/deployment/cloud" -type d -name __pycache__ -prune -exec find {} -type f -name '*.pyc' -delete \;
find "$ROOT/deployment/cloud" -type d -name __pycache__ -empty -delete
printf 'Production V10 -> V26 release tooling guards: PASS\n'
