#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deployment/cloud/production-v26-app-patch-promote.sh"

bash -n "$SCRIPT"
grep -Fq 'V26_APP_PATCH_FROZEN' "$SCRIPT"
grep -Fq 'current Staging runtime does not use accepted images' "$SCRIPT"
grep -Fq 'Production Flyway ledger is not exact V26' "$SCRIPT"
grep -Fq 'up -d --no-deps --no-build --pull never backend' "$SCRIPT"
grep -Fq 'up -d --no-deps --no-build --pull never nginx' "$SCRIPT"
grep -Fq 'DB_ID_AFTER' "$SCRIPT"
grep -Fq 'same_staging_artifact=true' "$SCRIPT"
grep -Fq 'AUTO_ROLLBACK' "$SCRIPT"
grep -Fq -- '--mode read' "$SCRIPT"
grep -Fq 'typed Staging acceptance evidence differs' "$SCRIPT"
grep -Fq 'SMALL_FRONTEND_DISPLAY_ONLY' "$SCRIPT"
grep -Fq 'NO|NO|NO|NO|NONE|PASS|PASS|PASS' "$SCRIPT"
grep -Fq 'Staging acceptance digest differs' "$SCRIPT"
grep -Fq 'Production environment digest differs from frozen preflight' "$SCRIPT"
grep -Fq 'target resolved Production Compose digest differs from frozen preflight' "$SCRIPT"
grep -Fq 'current runtime and target/rollback Compose config differ beyond immutable app images' "$SCRIPT"
grep -Fq 'backend_actual.get(legacy_optional_key) in (None,"production")' "$SCRIPT"
grep -Fq 'normalized_backend=' "$SCRIPT"
! grep -Fq 'backend_actual.get(legacy_optional_key) in (None,"production",' "$SCRIPT"
grep -Fq '"$(docker_default inspect --format '\''{{.Image}}'\'' cloud-backend-1)" == "$ROLLBACK_BACKEND_ID"' "$SCRIPT"
grep -Fq '"$(exact_container_id cloud-db-1)" == "$DB_ID_BEFORE"' "$SCRIPT"
! grep -Eq 'docker (build|pull|system prune|image prune)|compose[^\n]* build|flyway (clean|repair)|down( -v)?|dropdb|createdb|pg_restore|pg_dump' "$SCRIPT"
! grep -Fq 'source "$ENV_FILE"' "$SCRIPT"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest "$ROOT/deployment/cloud/tests/test_production_v26_app_patch_evidence.py"

profile_case="$(sed -n '/^case "$ACCEPTANCE_PROFILE" in$/,/^esac$/p' "$SCRIPT")"
[[ -n "$profile_case" ]]
validate_profile() ( die() { return 1; }; eval "$profile_case" )
ACCEPTANCE_PROFILE=SMALL_FRONTEND_DISPLAY_ONLY
BACKEND_BUSINESS_CHANGE=NO DATABASE_CHANGE=NO FLYWAY_CHANGE=NO ANDROID_APK_UPDATE=NO
PRINTING_IMPACT=NONE FRONTEND_BUILD=PASS FOCUSED_TESTS=PASS VISUAL_ACCEPTANCE=PASS
validate_profile

control_binding_function="$(sed -n '/^control_binding_is_valid() {/,/^}/p' "$SCRIPT")"
[[ -n "$control_binding_function" ]]
eval "$control_binding_function"
control_repo="$(mktemp -d)"
git -C "$control_repo" init -q
git -C "$control_repo" config user.name release-test
git -C "$control_repo" config user.email release-test@example.invalid
printf 'application\n' >"$control_repo/content"
git -C "$control_repo" add content
git -C "$control_repo" commit -qm application
application_sha="$(git -C "$control_repo" rev-parse HEAD)"
printf 'tooling\n' >>"$control_repo/content"
git -C "$control_repo" commit -qam tooling
tooling_sha="$(git -C "$control_repo" rev-parse HEAD)"
control_binding_is_valid "$control_repo" SMALL_FRONTEND_DISPLAY_ONLY "$application_sha" "$tooling_sha"
! control_binding_is_valid "$control_repo" SMALL_FRONTEND_DISPLAY_ONLY "$tooling_sha" "$application_sha"
! control_binding_is_valid "$control_repo" SMALL_FRONTEND_DISPLAY_ONLY "$(printf 'f%.0s' {1..40})" "$tooling_sha"
! control_binding_is_valid "$control_repo" V26_BUSINESS_STORE_CREATE "$application_sha" "$tooling_sha"
control_binding_is_valid "$control_repo" V26_BUSINESS_STORE_CREATE "$tooling_sha" "$tooling_sha"
rm -rf -- "$control_repo"
for mutation in \
  'ACCEPTANCE_PROFILE=SMALL_FRONTEND_DISPLAY_ONLY_EXTRA' \
  'BACKEND_BUSINESS_CHANGE=YES' \
  'DATABASE_CHANGE=YES' \
  'FLYWAY_CHANGE=YES' \
  'ANDROID_APK_UPDATE=YES' \
  'PRINTING_IMPACT=MINOR' \
  'FRONTEND_BUILD=FAIL' \
  'FOCUSED_TESTS=FAIL' \
  'VISUAL_ACCEPTANCE=FAIL'; do
  (
    eval "$mutation"
    ! validate_profile
  )
done
ACCEPTANCE_PROFILE=V26_BUSINESS_STORE_CREATE
BACKEND_BUSINESS_CHANGE=N/A DATABASE_CHANGE=N/A FLYWAY_CHANGE=N/A ANDROID_APK_UPDATE=N/A
PRINTING_IMPACT=N/A FRONTEND_BUILD=N/A FOCUSED_TESTS=N/A VISUAL_ACCEPTANCE=N/A
validate_profile

python3 - "$SCRIPT" <<'PY'
import pathlib,sys
text=pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
backend=text.index('up -d --no-deps --no-build --pull never backend\n')
backend_health=text.index('backend_health || die "promoted backend did not become healthy"', backend)
nginx=text.index('up -d --no-deps --no-build --pull never nginx\n', backend_health)
read_smoke=text.index('--mode read', nginx)
db_after=text.index('DB_ID_AFTER=', read_smoke)
assert backend < backend_health < nginx < read_smoke < db_after
PY

rollback_function="$(sed -n '/^rollback() {/,/^}/p' "$SCRIPT")"
ROLLBACK_LOG="$(mktemp)"
ROLLBACK_BACKEND_ID="sha256:$(printf 'b%.0s' {1..64})"; ROLLBACK_FRONTEND_ID="sha256:$(printf 'c%.0s' {1..64})"
TARGET_BACKEND_ID="sha256:$(printf 'd%.0s' {1..64})"; TARGET_FRONTEND_ID="sha256:$(printf 'e%.0s' {1..64})"
if (
  set +e
  eval "$rollback_function"
  SOURCE_SHA="$(printf 'a%.0s' {1..40})"; MUTATION_STARTED=true; COMPLETED=false
  DB_ID_BEFORE="$(printf 'f%.0s' {1..64})"; BEFORE_BUSINESS=business-before; BEFORE_PRINTING=printing-before
  rollback_compose_env=(PROMOTION_BACKEND_IMAGE="$ROLLBACK_BACKEND_ID" PROMOTION_FRONTEND_IMAGE="$ROLLBACK_FRONTEND_ID")
  compose=(docker compose); RUNTIME_ROOT=""
  bounded() { shift; printf '%s\n' "$*" >>"$ROLLBACK_LOG"; return 0; }
  backend_health() { return 0; }; public_health() { return 0; }
  docker_default() { if [[ "$*" == *'cloud-backend-1'* ]]; then printf '%s\n' "$ROLLBACK_BACKEND_ID"; else printf '%s\n' "$ROLLBACK_FRONTEND_ID"; fi; }
  exact_container_id() { printf '%s\n' "$DB_ID_BEFORE"; }; flyway_rows() { printf 'ledger\n'; }; expected_ledger() { printf 'ledger\n'; }
  v26_business_fingerprint() { printf 'business-before\n'; }; v26_printing_fingerprint() { printf 'printing-before\n'; }; cleanup_runtime() { :; }
  false
  rollback
); then
  printf 'rollback failure injection unexpectedly returned success\n' >&2; exit 1
fi
grep -Fq "$ROLLBACK_BACKEND_ID" "$ROLLBACK_LOG"
grep -Fq "$ROLLBACK_FRONTEND_ID" "$ROLLBACK_LOG"
! grep -Fq "$TARGET_BACKEND_ID" "$ROLLBACK_LOG"
! grep -Fq "$TARGET_FRONTEND_ID" "$ROLLBACK_LOG"
rm -f "$ROLLBACK_LOG"

printf 'Production V26 app-only patch promotion static guards: PASS\n'
