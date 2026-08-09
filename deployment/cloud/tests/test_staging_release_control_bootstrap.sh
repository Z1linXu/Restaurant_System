#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
SOURCE_SCRIPT="$REPOSITORY_ROOT/deployment/cloud/staging-release-control-bootstrap.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-release-bootstrap-test.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"
FAKE_ROOT="$TMP_DIR/staging"
SOURCE_REPO="$TMP_DIR/source"
BARE_REPO="$FAKE_ROOT/repository.git"
LOG_FILE="$TMP_DIR/rotation.args"
REAL_RM="$(command -v rm)"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
expect_failure() {
  local label="$1"
  shift
  if "$@" >"$TMP_DIR/$label.out" 2>"$TMP_DIR/$label.err"; then fail "$label unexpectedly passed"; fi
}
assert_contains() { grep -Fq -- "$1" "$2" || fail "missing '$1' in $2"; }

bash -n "$SOURCE_SCRIPT"
mkdir -p "$FAKE_ROOT/state" "$SOURCE_REPO/deployment/cloud"
chmod 700 "$FAKE_ROOT"
chmod 750 "$FAKE_ROOT/state"
git init -q "$SOURCE_REPO"
git -C "$SOURCE_REPO" config user.email ops001-bootstrap@example.invalid
git -C "$SOURCE_REPO" config user.name OPS001-Bootstrap
sed -e "s|/srv/restaurant-pos/staging|$FAKE_ROOT|g" \
  -e "s|https://github.com/Z1linXu/Restaurant_System.git|$SOURCE_REPO|g" \
  "$SOURCE_SCRIPT" >"$SOURCE_REPO/deployment/cloud/staging-release-control-bootstrap.sh"
cat >"$SOURCE_REPO/deployment/cloud/staging-release-rotation.sh" <<'ROTATION'
#!/usr/bin/env bash
set -euo pipefail
if [[ -n "${STAGING_BOOTSTRAP_TEST_READY:-}" ]]; then
  trap 'exit 143' TERM
  : >"$STAGING_BOOTSTRAP_TEST_READY"
  while true; do sleep 1; done
fi
if [[ -n "${STAGING_BOOTSTRAP_TEST_FAIL:-}" ]]; then
  printf 'fixture delegate failure\n' >&2
  exit 42
fi
control_root="$(cd -P "$(dirname "$0")/../../.." && pwd)"
if [[ -n "${STAGING_BOOTSTRAP_TEST_DRIFT_MODE:-}" ]]; then
  chmod 755 "$control_root"
fi
if [[ -n "${STAGING_BOOTSTRAP_TEST_DRIFT_STATE_MODE:-}" ]]; then
  chmod 700 "$(dirname "$control_root")"
fi
if [[ -n "${STAGING_BOOTSTRAP_TEST_DRIFT_INODE:-}" ]]; then
  mv "$control_root" "$control_root.displaced"
  mkdir -m 700 "$control_root"
fi
printf '%s\n' "$*" >"$STAGING_BOOTSTRAP_TEST_LOG"
printf 'OPS001_RELEASE_ENV|PASS|fixture\n'
ROTATION
cat >"$SOURCE_REPO/deployment/cloud/staging-synthetic-acceptance.sh" <<'SYNTHETIC'
#!/usr/bin/env bash
SYNTHETIC
cat >"$SOURCE_REPO/deployment/cloud/staging-ops-common.sh" <<'COMMON'
#!/usr/bin/env bash
COMMON
chmod +x "$SOURCE_REPO"/deployment/cloud/*.sh
git -C "$SOURCE_REPO" add deployment/cloud
git -C "$SOURCE_REPO" commit -qm 'bootstrap fixture'
printf 'fixture marker\n' >"$SOURCE_REPO/deployment/cloud/bootstrap-fixture-marker.txt"
git -C "$SOURCE_REPO" add deployment/cloud/bootstrap-fixture-marker.txt
git -C "$SOURCE_REPO" commit -qm 'bootstrap fixture marker'
APPROVED_SHA="$(git -C "$SOURCE_REPO" rev-parse HEAD)"
git clone -q --bare "$SOURCE_REPO" "$BARE_REPO"
chmod 700 "$BARE_REPO"
git --git-dir="$BARE_REPO" update-ref refs/remotes/origin/main "$APPROVED_SHA"

materialize() {
  local sha="$1" control
  control="$(mktemp -d "$FAKE_ROOT/state/ops001-release-control.XXXXXX")"
  chmod 700 "$control"
  git --git-dir="$BARE_REPO" show "$sha:deployment/cloud/staging-release-control-bootstrap.sh" >"$control/staging-release-control-bootstrap.sh"
  chmod 700 "$control/staging-release-control-bootstrap.sh"
  printf '%s\n' "$control"
}

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging" >"$TMP_DIR/pass.out"
assert_contains 'OPS001_RELEASE_ENV|PASS|fixture' "$TMP_DIR/pass.out"
assert_contains "--validate --approved-sha $APPROVED_SHA --env-file $FAKE_ROOT/config/.env.staging" "$LOG_FILE"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'successful bootstrap did not clean its private control root'

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --execute-runtime --action prepare-recovery-release-env \
  --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging" \
  --approval "$FAKE_ROOT/evidence/recovery-release.approval" \
  --approval-sha256 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  >"$TMP_DIR/recovery-action-pass.out"
assert_contains 'OPS001_RELEASE_ENV|PASS|fixture' "$TMP_DIR/recovery-action-pass.out"
assert_contains "--execute-runtime --action prepare-recovery-release-env --approved-sha $APPROVED_SHA" "$LOG_FILE"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'recovery-action bootstrap did not clean its private control root'

chmod 700 "$FAKE_ROOT/state"
CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging" >"$TMP_DIR/pass-0700.out"
assert_contains 'OPS001_RELEASE_ENV|PASS|fixture' "$TMP_DIR/pass-0700.out"
[[ ! -e "$CONTROL_ROOT" ]] || fail '0700 state-parent bootstrap did not clean its private control root'
chmod 750 "$FAKE_ROOT/state"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
chmod 775 "$FAKE_ROOT/state"
expect_failure state_parent_mode "$CONTROL_ROOT/staging-release-control-bootstrap.sh" --help
assert_contains 'Staging state parent must be owner-owned mode 0700 or 0750' "$TMP_DIR/state_parent_mode.err"
[[ -d "$CONTROL_ROOT" ]] || fail 'unsafe-state-parent root was unexpectedly deleted'
chmod 750 "$FAKE_ROOT/state"
rm -rf "$CONTROL_ROOT"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
"$CONTROL_ROOT/staging-release-control-bootstrap.sh" --help >"$TMP_DIR/help.out"
assert_contains 'Required bindings include:' "$TMP_DIR/help.out"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'help path did not clean its private control root'

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
expect_failure invalid_binding "$CONTROL_ROOT/staging-release-control-bootstrap.sh" --validate --approved-sha invalid \
  --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'approved SHA must be a lowercase full SHA' "$TMP_DIR/invalid_binding.err"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'invalid-binding path did not clean its private control root'

WRONG_SUFFIX_ROOT="$FAKE_ROOT/state/ops001-release-control.short"
mkdir -m 700 "$WRONG_SUFFIX_ROOT"
cp "$SOURCE_REPO/deployment/cloud/staging-release-control-bootstrap.sh" \
  "$WRONG_SUFFIX_ROOT/staging-release-control-bootstrap.sh"
chmod 700 "$WRONG_SUFFIX_ROOT/staging-release-control-bootstrap.sh"
expect_failure wrong_suffix "$WRONG_SUFFIX_ROOT/staging-release-control-bootstrap.sh" --help
assert_contains 'bootstrap must run from the fixed private control-root pattern' "$TMP_DIR/wrong_suffix.err"
[[ -d "$WRONG_SUFFIX_ROOT" ]] || fail 'wrong-suffix root was unexpectedly deleted'
rm -rf "$WRONG_SUFFIX_ROOT"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
mv "$CONTROL_ROOT/staging-release-control-bootstrap.sh" "$CONTROL_ROOT/renamed-bootstrap.sh"
expect_failure renamed_source "$CONTROL_ROOT/renamed-bootstrap.sh" --help
assert_contains 'bootstrap source must use the fixed filename' "$TMP_DIR/renamed_source.err"
[[ -d "$CONTROL_ROOT" ]] || fail 'renamed-source root was unexpectedly deleted'
rm -rf "$CONTROL_ROOT"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
printf 'unexpected\n' >"$CONTROL_ROOT/unexpected-sibling"
expect_failure unexpected_sibling "$CONTROL_ROOT/staging-release-control-bootstrap.sh" --help
assert_contains 'bootstrap control root must initially contain only the fixed source' "$TMP_DIR/unexpected_sibling.err"
[[ -d "$CONTROL_ROOT" ]] || fail 'unexpected-sibling root was unexpectedly deleted'
rm -rf "$CONTROL_ROOT"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
STAGING_BOOTSTRAP_TEST_FAIL=true STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" \
  expect_failure delegate_failure "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'fixture delegate failure' "$TMP_DIR/delegate_failure.err"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'delegate failure did not clean its private control root'

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
STAGING_BOOTSTRAP_TEST_DRIFT_MODE=true STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" \
  expect_failure cleanup_mode_drift "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'private control-root identity changed; refusing unsafe removal' "$TMP_DIR/cleanup_mode_drift.err"
[[ -d "$CONTROL_ROOT" ]] || fail 'mode-drift root was unexpectedly deleted'
chmod 700 "$CONTROL_ROOT"
rm -rf "$CONTROL_ROOT"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
STAGING_BOOTSTRAP_TEST_DRIFT_STATE_MODE=true STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" \
  expect_failure cleanup_state_mode_drift "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'private control-root identity changed; refusing unsafe removal' "$TMP_DIR/cleanup_state_mode_drift.err"
[[ -d "$CONTROL_ROOT" ]] || fail 'state-mode-drift root was unexpectedly deleted'
chmod 750 "$FAKE_ROOT/state"
rm -rf "$CONTROL_ROOT"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
STAGING_BOOTSTRAP_TEST_DRIFT_INODE=true STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" \
  expect_failure cleanup_inode_drift "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'private control-root identity changed; refusing unsafe removal' "$TMP_DIR/cleanup_inode_drift.err"
[[ -d "$CONTROL_ROOT" && -d "$CONTROL_ROOT.displaced" ]] || fail 'inode-drift fixtures were unexpectedly deleted'
rm -rf "$CONTROL_ROOT" "$CONTROL_ROOT.displaced"

mkdir -p "$TMP_DIR/fakebin"
cat >"$TMP_DIR/fakebin/rm" <<'FAKE_RM'
#!/usr/bin/env bash
exit 1
FAKE_RM
chmod +x "$TMP_DIR/fakebin/rm"
CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
PATH="$TMP_DIR/fakebin:$PATH" STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" \
  expect_failure cleanup_remove_failure "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'private control-root removal failed' "$TMP_DIR/cleanup_remove_failure.err"
[[ -d "$CONTROL_ROOT" ]] || fail 'removal-failure root was unexpectedly deleted'
"$REAL_RM" -rf "$CONTROL_ROOT"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
SIGNAL_READY="$TMP_DIR/signal.ready"
STAGING_BOOTSTRAP_TEST_LOG="$LOG_FILE" STAGING_BOOTSTRAP_TEST_READY="$SIGNAL_READY" \
  "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging" \
  >"$TMP_DIR/signal.out" 2>"$TMP_DIR/signal.err" &
BOOTSTRAP_PID=$!
for _ in $(seq 1 50); do [[ -e "$SIGNAL_READY" ]] && break; sleep 0.1; done
[[ -e "$SIGNAL_READY" ]] || fail 'signal fixture did not reach delegated helper'
kill -TERM "$BOOTSTRAP_PID"
set +e
wait "$BOOTSTRAP_PID"
signal_status=$?
set -e
[[ "$signal_status" == "143" ]] || fail "signal bootstrap status was $signal_status"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'signal bootstrap did not clean its private control root'

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
printf '\n# tampered\n' >>"$CONTROL_ROOT/staging-release-control-bootstrap.sh"
expect_failure tampered "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'bootstrap source does not match the approved Git blob' "$TMP_DIR/tampered.err"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'tampered bootstrap did not clean its private control root'

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
chmod 755 "$BARE_REPO"
expect_failure repository_mode "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'dedicated Staging repository must be owner-only mode 0700' "$TMP_DIR/repository_mode.err"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'repository failure did not clean its private control root'
chmod 700 "$BARE_REPO"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
git --git-dir="$BARE_REPO" remote set-url origin "$TMP_DIR/unexpected-origin"
expect_failure repository_origin "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'dedicated Staging repository origin URL mismatch' "$TMP_DIR/repository_origin.err"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'origin failure did not clean its private control root'
git --git-dir="$BARE_REPO" remote set-url origin "$SOURCE_REPO"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
chmod 755 "$CONTROL_ROOT"
expect_failure control_mode "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'bootstrap control root must be owner-only mode 0700' "$TMP_DIR/control_mode.err"
rm -rf "$CONTROL_ROOT"

CONTROL_ROOT="$(materialize "$APPROVED_SHA")"
git --git-dir="$BARE_REPO" update-ref refs/remotes/origin/main "${APPROVED_SHA}^"
expect_failure wrong_ref "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$APPROVED_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'approved SHA must equal the dedicated repository origin/main' "$TMP_DIR/wrong_ref.err"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'wrong-ref failure did not clean its private control root'
git --git-dir="$BARE_REPO" update-ref refs/remotes/origin/main "$APPROVED_SHA"

ln -s staging-ops-common.sh "$SOURCE_REPO/deployment/cloud/unexpected-link"
git -C "$SOURCE_REPO" add deployment/cloud/unexpected-link
git -C "$SOURCE_REPO" commit -qm 'unsafe symlink fixture'
SYMLINK_SHA="$(git -C "$SOURCE_REPO" rev-parse HEAD)"
git --git-dir="$BARE_REPO" fetch -q "$SOURCE_REPO" "$SYMLINK_SHA"
git --git-dir="$BARE_REPO" update-ref refs/remotes/origin/main "$SYMLINK_SHA"
CONTROL_ROOT="$(materialize "$SYMLINK_SHA")"
expect_failure bundle_symlink "$CONTROL_ROOT/staging-release-control-bootstrap.sh" \
  --validate --approved-sha "$SYMLINK_SHA" --env-file "$FAKE_ROOT/config/.env.staging"
assert_contains 'approved deployment bundle contains a symlink' "$TMP_DIR/bundle_symlink.err"
[[ ! -e "$CONTROL_ROOT" ]] || fail 'symlink failure did not clean its private control root'

echo 'PASS: OPS-001 release bootstrap verifies its exact Git source, private control root, dedicated repository identity, extracted bundle, delegation, and cleanup.'
