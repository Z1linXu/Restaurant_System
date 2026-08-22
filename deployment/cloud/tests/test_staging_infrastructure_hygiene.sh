#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-hygiene-test.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"
trap '[[ "${BASH_SUBSHELL:-0}" -ne 0 ]] || rm -rf "$TMP_DIR"' EXIT

FAKE_FLOCK_DIR="$TMP_DIR/fake-flock"
mkdir -p "$FAKE_FLOCK_DIR"
printf '%s\n' '#!/usr/bin/env bash' '[[ "${1:-}" == "-n" || "${1:-}" == "-u" ]]' >"$FAKE_FLOCK_DIR/flock"
chmod 700 "$FAKE_FLOCK_DIR/flock"

COMMON="$REPO_ROOT/deployment/cloud/staging-hygiene-common.sh"
BUILD_SCRIPT="$REPO_ROOT/deployment/cloud/staging-buildkit-cache-hygiene.sh"
RELEASE_SCRIPT="$REPO_ROOT/deployment/cloud/staging-release-retention.sh"
DISK_SCRIPT="$REPO_ROOT/deployment/cloud/staging-disk-check.sh"
POLICY_SCRIPT="$REPO_ROOT/deployment/cloud/staging-retention-policy-check.sh"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
assert_contains() { grep -Fq -- "$1" "$2" || { sed 's/^/TEST-DIAGNOSTIC: /' "$2" >&2; fail "missing '$1' in $2"; }; }
assert_not_contains() { ! grep -Fq -- "$1" "$2" || fail "unexpected '$1' in $2"; }
expect_failure() {
  local label="$1"; shift
  if (trap - EXIT; "$@") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then
    fail "$label unexpectedly passed"
  fi
}

bash -n "$COMMON" "$BUILD_SCRIPT" "$RELEASE_SCRIPT" "$DISK_SCRIPT" "$POLICY_SCRIPT"
assert_contains 'run_release_retention_dry_run' "$REPO_ROOT/deployment/cloud/staging-deploy.sh"
assert_contains 'PRIOR_STAGING_SHA' "$REPO_ROOT/deployment/cloud/staging-release-rotation.sh"
assert_contains 'request_time=$request_time' "$REPO_ROOT/deployment/cloud/nginx.http.conf.template"
assert_contains 'upstream_response_time=$upstream_response_time' "$REPO_ROOT/deployment/cloud/nginx.http.conf.template"
assert_contains 'method=$request_method' "$REPO_ROOT/deployment/cloud/nginx.http.conf.template"
assert_contains 'uri=$uri' "$REPO_ROOT/deployment/cloud/nginx.http.conf.template"
TIMING_LINE="$(grep '^log_format staging_timing ' "$REPO_ROOT/deployment/cloud/nginx.http.conf.template")"
[[ "$TIMING_LINE" != *'$request_uri'* ]] || fail "timing log must omit query-bearing request_uri"
[[ "$TIMING_LINE" != *'$args'* ]] || fail "timing log must omit query arguments"
[[ "$TIMING_LINE" != *'$request_uri'* && "$TIMING_LINE" != *'$args'* && "$TIMING_LINE" != *'$http_'* && "$TIMING_LINE" != *'$cookie_'* ]] || fail 'Nginx timing log contains sensitive request fields'
assert_not_contains 'docker system prune' "$BUILD_SCRIPT"
assert_not_contains 'volume prune' "$BUILD_SCRIPT"
assert_not_contains 'image prune' "$BUILD_SCRIPT"
assert_not_contains 'hygiene_require_command jq' "$BUILD_SCRIPT"
assert_not_contains 'rm -rf releases' "$RELEASE_SCRIPT"
assert_not_contains 'journal vacuum' "$RELEASE_SCRIPT"

make_scope() {
  local root="$1" sha="$2" env_file
  mkdir -p "$root/config" "$root/evidence" "$root/releases" "$root/state/postgres"
  chmod 700 "$root" "$root/config" "$root/evidence" "$root/releases" "$root/state" "$root/state/postgres"
  env_file="$root/config/.env.staging"
  printf '%s\n' \
    'COMPOSE_PROJECT_NAME=restaurant-pos-staging' \
    "STAGING_ROOT=$root" \
    "STAGING_COMMIT_SHA=$sha" \
    "BACKEND_IMAGE=restaurant-pos-backend:staging-$sha" \
    "FRONTEND_IMAGE=restaurant-pos-frontend:staging-$sha" \
    "VITE_APP_BUILD_VERSION=staging-$sha" >"$env_file"
  chmod 600 "$env_file"
}

make_release_fixture() {
  local root="$1" source_repo="$TMP_DIR/release-source" repository sha index
  make_scope "$root" "0000000000000000000000000000000000000000"
  git init -q "$source_repo"
  git -C "$source_repo" config user.name hygiene-test
  git -C "$source_repo" config user.email hygiene-test@example.invalid
  for index in 1 2 3 4 5; do
    printf 'release-%s\n' "$index" >"$source_repo/release.txt"
    git -C "$source_repo" add release.txt
    GIT_AUTHOR_DATE="2020-01-0${index}T00:00:00Z" \
      GIT_COMMITTER_DATE="2020-01-0${index}T00:00:00Z" \
      git -C "$source_repo" -c user.name=hygiene-test -c user.email=hygiene-test@example.invalid commit -qm "release $index"
    sha="$(git -C "$source_repo" rev-parse HEAD)"
    RELEASE_SHAS[$((index - 1))]="$sha"
  done
  repository="$root/repository.git"
  git clone -q --bare "$source_repo" "$repository"
  chmod 700 "$repository"
  index=1
  for sha in "${RELEASE_SHAS[@]}"; do
    git --git-dir="$repository" worktree add --detach "$root/releases/$sha" "$sha" >/dev/null
    chmod 700 "$root/releases/$sha"
    touch -t "2020010${index}0000" "$root/releases/$sha"
    index=$((index + 1))
  done
  CURRENT_RELEASE_SHA="${RELEASE_SHAS[4]}"
  PREVIOUS_RELEASE_SHA="${RELEASE_SHAS[3]}"
  sed -i.bak "s/^STAGING_COMMIT_SHA=.*/STAGING_COMMIT_SHA=$CURRENT_RELEASE_SHA/; s#^BACKEND_IMAGE=.*#BACKEND_IMAGE=restaurant-pos-backend:staging-$CURRENT_RELEASE_SHA#; s#^FRONTEND_IMAGE=.*#FRONTEND_IMAGE=restaurant-pos-frontend:staging-$CURRENT_RELEASE_SHA#; s#^VITE_APP_BUILD_VERSION=.*#VITE_APP_BUILD_VERSION=staging-$CURRENT_RELEASE_SHA#" "$root/config/.env.staging"
  rm -f "$root/config/.env.staging.bak"
  printf 'OPS001_ENV_ROTATION|PRIOR_STAGING_SHA|%s\n' "$PREVIOUS_RELEASE_SHA" >"$root/state/rotation.record"
  chmod 600 "$root/state/rotation.record"
  printf 'EVIDENCE|APPROVED_SHA|%s\n' "$PREVIOUS_RELEASE_SHA" >"$root/evidence/previous-verified.evidence"
  chmod 600 "$root/evidence/previous-verified.evidence"
  printf 'EVIDENCE|HISTORICAL|legacy-mode\n' >"$root/evidence/legacy-mode.evidence"
  chmod 664 "$root/evidence/legacy-mode.evidence"
}

RELEASE_SHAS=()
RELEASE_ROOT="$TMP_DIR/release/staging"
make_release_fixture "$RELEASE_ROOT"
RELEASE_ENV="$RELEASE_ROOT/config/.env.staging"
RELEASE_PLAN="$RELEASE_ROOT/evidence/release-retention.plan"
UNSAFE_LEGACY_RELEASE_SHA=ffffffffffffffffffffffffffffffffffffffff
mkdir "$RELEASE_ROOT/releases/$UNSAFE_LEGACY_RELEASE_SHA"
chmod 775 "$RELEASE_ROOT/releases/$UNSAFE_LEGACY_RELEASE_SHA"
printf 'legacy dirty release\n' >"$RELEASE_ROOT/releases/${RELEASE_SHAS[0]}/legacy-untracked.txt"

(
  PATH="$FAKE_FLOCK_DIR:$PATH"
  source "$RELEASE_SCRIPT"
  HYGIENE_EXPECTED_ROOT="$RELEASE_ROOT"
  HYGIENE_ROOT="$RELEASE_ROOT"
  main --dry-run --env-file "$RELEASE_ENV" --previous-verified-sha "$PREVIOUS_RELEASE_SHA"
) >"$RELEASE_PLAN"
chmod 600 "$RELEASE_PLAN"
assert_contains "RELEASE_RETENTION|PROTECTED|$CURRENT_RELEASE_SHA|current_staging" "$RELEASE_PLAN"
assert_contains "RELEASE_RETENTION|PROTECTED|$PREVIOUS_RELEASE_SHA|previous_verified" "$RELEASE_PLAN"
assert_contains "RELEASE_RETENTION|ELIGIBLE|${RELEASE_SHAS[1]}|" "$RELEASE_PLAN"
assert_contains "RELEASE_RETENTION|PROTECTED|${RELEASE_SHAS[0]}|unsafe_legacy_release_metadata" "$RELEASE_PLAN"
assert_contains "RELEASE_RETENTION|UNSAFE_RETAINED|${RELEASE_SHAS[0]}|mode=700;worktree_validation_failed" "$RELEASE_PLAN"
assert_not_contains 'state/postgres' "$RELEASE_PLAN"
assert_contains "RELEASE_RETENTION|PROTECTED|$UNSAFE_LEGACY_RELEASE_SHA|unsafe_legacy_release_metadata" "$RELEASE_PLAN"
assert_contains "RELEASE_RETENTION|UNSAFE_RETAINED|$UNSAFE_LEGACY_RELEASE_SHA|mode=775;content_not_inspected" "$RELEASE_PLAN"
[[ "$(stat -f '%Lp' "$RELEASE_ROOT/evidence/legacy-mode.evidence" 2>/dev/null || stat -c '%a' "$RELEASE_ROOT/evidence/legacy-mode.evidence")" == 664 ]] || fail 'historical evidence mode was mutated'
RELEASE_PLAN_SHA256="$(sha256sum "$RELEASE_PLAN" | awk '{print $1}')"

(
  PATH="$FAKE_FLOCK_DIR:$PATH"
  source "$RELEASE_SCRIPT"
  HYGIENE_EXPECTED_ROOT="$RELEASE_ROOT"
  HYGIENE_ROOT="$RELEASE_ROOT"
  main --execute --env-file "$RELEASE_ENV" --previous-verified-sha "$PREVIOUS_RELEASE_SHA" \
    --plan-file "$RELEASE_PLAN" --plan-sha256 "$RELEASE_PLAN_SHA256"
) >"$TMP_DIR/release-execute.out"
assert_contains 'RELEASE_RETENTION|REMOVED|' "$TMP_DIR/release-execute.out"
[[ -d "$RELEASE_ROOT/releases/${RELEASE_SHAS[0]}" ]] || fail 'dirty legacy release must be retained untouched'
[[ ! -e "$RELEASE_ROOT/releases/${RELEASE_SHAS[1]}" ]] || fail 'eligible release 2 was not removed'
[[ -d "$RELEASE_ROOT/releases/$CURRENT_RELEASE_SHA" ]] || fail 'current release was removed'
[[ -d "$RELEASE_ROOT/releases/$PREVIOUS_RELEASE_SHA" ]] || fail 'previous verified release was removed'
[[ -d "$RELEASE_ROOT/releases/$UNSAFE_LEGACY_RELEASE_SHA" ]] || fail 'unsafe legacy release must be retained untouched'

(
  PATH="$FAKE_FLOCK_DIR:$PATH"
  source "$RELEASE_SCRIPT"
  HYGIENE_EXPECTED_ROOT="$RELEASE_ROOT"
  HYGIENE_ROOT="$RELEASE_ROOT"
  main --execute --env-file "$RELEASE_ENV" --previous-verified-sha "$PREVIOUS_RELEASE_SHA" \
    --plan-file "$RELEASE_PLAN" --plan-sha256 "$RELEASE_PLAN_SHA256"
) >"$TMP_DIR/release-execute-repeat.out"
assert_contains 'RELEASE_RETENTION|ALREADY_ABSENT|' "$TMP_DIR/release-execute-repeat.out"
assert_contains 'RELEASE_RETENTION|STATUS|PASS|idempotent_release_only_rotation' "$TMP_DIR/release-execute-repeat.out"

ln -s "$TMP_DIR/outside-release" "$RELEASE_ROOT/releases/abcdefabcdefabcdefabcdefabcdefabcdefabcd"
expect_failure release_symlink_rejected bash -c \
  "source '$RELEASE_SCRIPT'; HYGIENE_EXPECTED_ROOT='$RELEASE_ROOT'; HYGIENE_ROOT='$RELEASE_ROOT'; main --dry-run --env-file '$RELEASE_ENV'"
rm -f "$RELEASE_ROOT/releases/abcdefabcdefabcdefabcdefabcdefabcdefabcd"
chmod 775 "$RELEASE_ROOT/state"
expect_failure release_state_mode_rejected bash -c \
  "source '$RELEASE_SCRIPT'; HYGIENE_EXPECTED_ROOT='$RELEASE_ROOT'; HYGIENE_ROOT='$RELEASE_ROOT'; main --dry-run --env-file '$RELEASE_ENV'"
chmod 700 "$RELEASE_ROOT/state"
chmod 750 "$RELEASE_ROOT/evidence"
expect_failure release_evidence_parent_not_owner_only bash -c \
  "source '$RELEASE_SCRIPT'; HYGIENE_EXPECTED_ROOT='$RELEASE_ROOT'; HYGIENE_ROOT='$RELEASE_ROOT'; main --dry-run --env-file '$RELEASE_ENV'"
chmod 700 "$RELEASE_ROOT/evidence"
mv "$RELEASE_ROOT/state/postgres" "$RELEASE_ROOT/state/postgres-safe"
ln -s "$RELEASE_ROOT/state/postgres-safe" "$RELEASE_ROOT/state/postgres"
expect_failure release_postgres_symlink_rejected bash -c \
  "source '$RELEASE_SCRIPT'; HYGIENE_EXPECTED_ROOT='$RELEASE_ROOT'; HYGIENE_ROOT='$RELEASE_ROOT'; main --dry-run --env-file '$RELEASE_ENV'"
rm "$RELEASE_ROOT/state/postgres"
mv "$RELEASE_ROOT/state/postgres-safe" "$RELEASE_ROOT/state/postgres"
chmod 666 "$RELEASE_ROOT/evidence/legacy-mode.evidence"
expect_failure release_world_writable_evidence_rejected bash -c \
  "source '$RELEASE_SCRIPT'; HYGIENE_EXPECTED_ROOT='$RELEASE_ROOT'; HYGIENE_ROOT='$RELEASE_ROOT'; main --dry-run --env-file '$RELEASE_ENV'"
chmod 664 "$RELEASE_ROOT/evidence/legacy-mode.evidence"
expect_failure release_arbitrary_env_rejected "$RELEASE_SCRIPT" --dry-run --env-file "$TMP_DIR/arbitrary/.env.staging"
assert_contains 'fixed Staging path' "$TMP_DIR/release_arbitrary_env_rejected.err"

BUILD_ROOT="$TMP_DIR/buildkit/staging"
BUILD_SHA=0123456789abcdef0123456789abcdef01234567
make_scope "$BUILD_ROOT" "$BUILD_SHA"
FAKE_DOCKER_DIR="$TMP_DIR/fake-docker"
mkdir -p "$FAKE_DOCKER_DIR"
DOCKER_MARKER="$TMP_DIR/fake-docker.pruned"
DOCKER_CALLS="$TMP_DIR/fake-docker.calls"
BUILDER_MODE="$TMP_DIR/fake-builder.mode"
cat >"$FAKE_DOCKER_DIR/docker" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "\$*" >>"$DOCKER_CALLS"
case "\$*" in
  *'context inspect default'*) printf 'unix:///var/run/docker.sock\n' ;;
  *'buildx inspect default'*)
    mode="\$(cat "$BUILDER_MODE" 2>/dev/null || true)"
    case "\$mode" in
      partial-nonzero) printf '%s\n' 'Name: default' 'Driver: docker' 'Status: running'; exit 88 ;;
      conflicting-status) printf '%s\n' 'Name: default' 'Driver: docker' 'Status: running' 'Status: stopped' ;;
      conflicting-driver) printf '%s\n' 'Name: default' 'Driver: docker' 'Driver: remote' 'Status: running' ;;
      conflicting-name) printf '%s\n' 'Name: default' 'Name: unexpected' 'Driver: docker' 'Status: running' ;;
      extra-token) printf '%s\n' 'Name: default extra' 'Driver: docker' 'Status: running' ;;
      *) printf '%s\n' 'Name: default' 'Driver: docker' 'Nodes:' 'Name: default' 'Status: running' ;;
    esac
    ;;
  *'buildx du --help'*) printf '%s\n' '--format --filter' ;;
  *'buildx prune --help'*)
    mode="\$(cat "$BUILDER_MODE" 2>/dev/null || true)"
    if [[ "\$mode" == legacy-keep-storage ]]; then
      printf '%s\n' '--filter --force --keep-storage'
    else
      printf '%s\n' '--filter --force --reserved-space'
    fi
    ;;
  *' ps --format {{.Image}}'*)
    printf '%s\n' 'restaurant-pos-backend:staging-active' 'restaurant-pos-backend:production-sha'
    ;;
  *' image inspect '*' --format {{.Id}}'*) printf 'sha256:%064d\n' 1 ;;
  *'buildx du --builder default --format {{.ID}}|{{.Reclaimable}}|{{.Mutable}}|{{.Shared}}|{{.UsageCount}}|{{.LastUsedAt}}|{{.Size}}|{{.Type}} --filter until=168h'*)
    if [[ -e "$DOCKER_MARKER" ]]; then exit 0; fi
    printf '%s\n' 'cache-safe|true|false|false|0|2 weeks ago|1.234kB|regular'
    printf '%s\n' 'cache-in-use|false|false|false|1|2 weeks ago|4.567kB|regular'
    printf '%s\n' 'cache-retained-no-last-used|false|false|false|0||4.096MB|regular'
    ;;
  *'buildx prune --builder default --filter until=168h --reserved-space 10GB --force'*)
    : >"$DOCKER_MARKER"
    ;;
  *'buildx prune --builder default --filter until=168h --keep-storage 10GB --force'*) : >"$DOCKER_MARKER" ;;
  *) printf 'unexpected fake Docker command: %s\n' "\$*" >&2; exit 97 ;;
esac
EOF
chmod 700 "$FAKE_DOCKER_DIR/docker"
BUILD_PLAN="$BUILD_ROOT/evidence/buildkit.plan"
(
  PATH="$FAKE_DOCKER_DIR:$FAKE_FLOCK_DIR:$PATH"
  source "$BUILD_SCRIPT"
  HYGIENE_EXPECTED_ROOT="$BUILD_ROOT"
  HYGIENE_ROOT="$BUILD_ROOT"
  main --dry-run --env-file "$BUILD_ROOT/config/.env.staging" \
    --production-image restaurant-pos-backend:production-sha \
    --production-image restaurant-pos-frontend:production-sha \
    --active-image restaurant-pos-backend:staging-active
) >"$BUILD_PLAN"
chmod 600 "$BUILD_PLAN"
assert_contains 'BUILDKIT_CACHE|PROTECTED|PRODUCTION_IMAGE|restaurant-pos-backend:production-sha' "$BUILD_PLAN"
assert_contains 'BUILDKIT_CACHE|PROTECTED|STAGING_IMAGE|restaurant-pos-backend:staging-' "$BUILD_PLAN"
assert_contains 'BUILDKIT_CACHE|ELIGIBLE_ID|cache-safe|size=1.234kB' "$BUILD_PLAN"
assert_contains 'BUILDKIT_CACHE|STORAGE_OPTION|reserved-space' "$BUILD_PLAN"
assert_not_contains 'cache-in-use' "$BUILD_PLAN"
expect_builder_failure() {
  local mode="$1"
  printf '%s\n' "$mode" >"$BUILDER_MODE"
  expect_failure "buildkit_builder_${mode}" bash -c \
    "PATH='$FAKE_DOCKER_DIR:$FAKE_FLOCK_DIR:$PATH'; source '$BUILD_SCRIPT'; HYGIENE_EXPECTED_ROOT='$BUILD_ROOT'; HYGIENE_ROOT='$BUILD_ROOT'; main --dry-run --env-file '$BUILD_ROOT/config/.env.staging' --production-image restaurant-pos-backend:production-sha"
  rm -f "$BUILDER_MODE"
}
expect_builder_failure partial-nonzero
expect_builder_failure conflicting-status
expect_builder_failure conflicting-driver
expect_builder_failure conflicting-name
expect_builder_failure extra-token
printf '%s\n' legacy-keep-storage >"$BUILDER_MODE"
(
  PATH="$FAKE_DOCKER_DIR:$FAKE_FLOCK_DIR:$PATH"
  source "$BUILD_SCRIPT"
  HYGIENE_EXPECTED_ROOT="$BUILD_ROOT"
  HYGIENE_ROOT="$BUILD_ROOT"
  main --dry-run --env-file "$BUILD_ROOT/config/.env.staging" \
    --production-image restaurant-pos-backend:production-sha
) >"$TMP_DIR/buildkit-legacy-storage.plan"
rm -f "$BUILDER_MODE"
assert_contains 'BUILDKIT_CACHE|STORAGE_OPTION|keep-storage' "$TMP_DIR/buildkit-legacy-storage.plan"
expect_failure buildkit_extra_field_rejected bash -c \
  "source '$BUILD_SCRIPT'; scan_cache_records 'cache-safe|true|false|false|0|2 weeks ago|1.234kB|regular|unexpected'"
expect_failure buildkit_unsafe_size_rejected bash -c \
  "source '$BUILD_SCRIPT'; scan_cache_records 'cache-safe|true|false|false|0|2 weeks ago|1.234kB;touch bad|regular'"
BUILD_PLAN_SHA256="$(sha256sum "$BUILD_PLAN" | awk '{print $1}')"

(
  PATH="$FAKE_DOCKER_DIR:$FAKE_FLOCK_DIR:$PATH"
  source "$BUILD_SCRIPT"
  HYGIENE_EXPECTED_ROOT="$BUILD_ROOT"
  HYGIENE_ROOT="$BUILD_ROOT"
  main --execute --env-file "$BUILD_ROOT/config/.env.staging" \
    --production-image restaurant-pos-backend:production-sha \
    --production-image restaurant-pos-frontend:production-sha \
    --active-image restaurant-pos-backend:staging-active \
    --plan-file "$BUILD_PLAN" --plan-sha256 "$BUILD_PLAN_SHA256"
) >"$TMP_DIR/buildkit-execute.out"
assert_contains 'BUILDKIT_CACHE|STATUS|PASS|eligible_cache_only_pruned' "$TMP_DIR/buildkit-execute.out"
[[ -e "$DOCKER_MARKER" ]] || fail 'BuildKit execute did not call the cache-only prune'
[[ "$(wc -l <"$DOCKER_CALLS" | tr -d ' ')" -ge 1 ]] || fail 'fake Docker was not called'

PRUNE_CALL_COUNT="$(grep -Fc 'buildx prune --builder default --filter until=168h --reserved-space 10GB --force' "$DOCKER_CALLS" || true)"
[[ "$PRUNE_CALL_COUNT" == "1" ]] || fail 'unexpected initial BuildKit prune count'
(
  PATH="$FAKE_DOCKER_DIR:$FAKE_FLOCK_DIR:$PATH"
  source "$BUILD_SCRIPT"
  HYGIENE_EXPECTED_ROOT="$BUILD_ROOT"
  HYGIENE_ROOT="$BUILD_ROOT"
  main --execute --env-file "$BUILD_ROOT/config/.env.staging" \
    --production-image restaurant-pos-backend:production-sha \
    --production-image restaurant-pos-frontend:production-sha \
    --active-image restaurant-pos-backend:staging-active \
    --plan-file "$BUILD_PLAN" --plan-sha256 "$BUILD_PLAN_SHA256"
) >"$TMP_DIR/buildkit-execute-repeat.out"
assert_contains 'BUILDKIT_CACHE|STATUS|PASS|nothing_eligible;idempotent_noop' "$TMP_DIR/buildkit-execute-repeat.out"
PRUNE_CALL_COUNT_AFTER="$(grep -Fc 'buildx prune --builder default --filter until=168h --reserved-space 10GB --force' "$DOCKER_CALLS" || true)"
[[ "$PRUNE_CALL_COUNT_AFTER" == "1" ]] || fail 'repeat BuildKit execute was not an idempotent no-op'

expect_failure buildkit_wrong_plan_digest bash -c \
  "PATH=\"$FAKE_DOCKER_DIR:$FAKE_FLOCK_DIR:\$PATH\"; source '$BUILD_SCRIPT'; HYGIENE_EXPECTED_ROOT='$BUILD_ROOT'; HYGIENE_ROOT='$BUILD_ROOT'; main --execute --env-file '$BUILD_ROOT/config/.env.staging' --production-image restaurant-pos-backend:production-sha --production-image restaurant-pos-frontend:production-sha --active-image restaurant-pos-backend:staging-active --plan-file '$BUILD_PLAN' --plan-sha256 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
expect_failure buildkit_unsafe_builder bash -c \
  "source '$BUILD_SCRIPT'; HYGIENE_EXPECTED_ROOT='$BUILD_ROOT'; HYGIENE_ROOT='$BUILD_ROOT'; main --builder remote --dry-run --env-file '$BUILD_ROOT/config/.env.staging' --production-image restaurant-pos-backend:production-sha"
assert_contains 'fixed to default' "$TMP_DIR/buildkit_unsafe_builder.err"
expect_failure buildkit_ambient_context bash -c \
  "PATH=\"$FAKE_DOCKER_DIR:$FAKE_FLOCK_DIR:\$PATH\"; DOCKER_CONTEXT=remote; source '$BUILD_SCRIPT'; HYGIENE_EXPECTED_ROOT='$BUILD_ROOT'; HYGIENE_ROOT='$BUILD_ROOT'; main --dry-run --env-file '$BUILD_ROOT/config/.env.staging' --production-image restaurant-pos-backend:production-sha"
assert_contains 'ambient Docker/Compose override' "$TMP_DIR/buildkit_ambient_context.err"

DISK_ROOT="$TMP_DIR/disk/staging"
DISK_SHA=abcdefabcdefabcdefabcdefabcdefabcdefabcd
make_scope "$DISK_ROOT" "$DISK_SHA"
FAKE_DF_DIR="$TMP_DIR/fake-df"
mkdir -p "$FAKE_DF_DIR"
write_df() {
  local used="$1" free_kb="$2"
  printf '#!/usr/bin/env bash\nprintf "Filesystem 1024-blocks Used Available Capacity Mounted on\\n/dev/fake 20000000 %s %s %s%% /\\n"\n' \
    "$((20000000 - free_kb))" "$free_kb" "$used" >"$FAKE_DF_DIR/df"
  chmod 700 "$FAKE_DF_DIR/df"
}
run_disk_check() {
  local used="$1" free_kb="$2" label="$3"
  write_df "$used" "$free_kb"
  (PATH="$FAKE_DF_DIR:$PATH"; source "$DISK_SCRIPT"; HYGIENE_EXPECTED_ROOT="$DISK_ROOT"; HYGIENE_ROOT="$DISK_ROOT"; main --dry-run --env-file "$DISK_ROOT/config/.env.staging") >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"
}
if run_disk_check 85 7000000 disk_warning; then fail 'disk warning unexpectedly returned zero'; fi
assert_contains 'DISK_CHECK|STATUS|WARNING' "$TMP_DIR/disk_warning.out"
if run_disk_check 95 20000000 disk_critical; then fail 'disk critical unexpectedly returned zero'; fi
assert_contains 'DISK_CHECK|STATUS|CRITICAL' "$TMP_DIR/disk_critical.out"
run_disk_check 50 20000000 disk_pass
assert_contains 'DISK_CHECK|STATUS|PASS' "$TMP_DIR/disk_pass.out"

(
  source "$POLICY_SCRIPT"
  HYGIENE_EXPECTED_ROOT="$DISK_ROOT"
  HYGIENE_ROOT="$DISK_ROOT"
  main --dry-run --env-file "$DISK_ROOT/config/.env.staging"
) >"$TMP_DIR/policy.out"
assert_contains 'RETENTION_CHECK|CONTAINER|db|PASS|driver=local;max-size=10m;max-file=3' "$TMP_DIR/policy.out"
assert_contains 'RETENTION_CHECK|NGINX|PASS|dimensions=method,normalized_uri;timing=request_time,upstream_connect_time,upstream_header_time,upstream_response_time;payload=non-sensitive' "$TMP_DIR/policy.out"
assert_contains 'RETENTION_CHECK|JOURNALD|POLICY_ONLY|SystemMaxUse=1G;RuntimeMaxUse=512M;MaxRetentionSec=14day;MaxFileSec=1day;vacuum=forbidden' "$TMP_DIR/policy.out"

printf 'PASS: Staging hygiene dry-run/protected-set/execute, exact guards, threshold statuses, timing sanitization, and idempotent safety boundaries passed.\n'
