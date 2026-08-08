#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"
RUNBOOK="$REPOSITORY_ROOT/deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-pos-candidate-import-test.XXXXXX")"
TMP_DIR="$(cd -P "$TMP_DIR" && pwd)"
SOURCE_REPO="$TMP_DIR/source"
STAGING_REPO="$TMP_DIR/repository.git"
ZERO_OID=0000000000000000000000000000000000000000

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then stat -c '%a' "$1"; else stat -f '%Lp' "$1"; fi
}
file_owner() {
  if stat -c '%u' "$1" >/dev/null 2>&1; then stat -c '%u' "$1"; else stat -f '%u' "$1"; fi
}
file_identity() {
  if stat -c '%d:%i' "$1" >/dev/null 2>&1; then stat -c '%d:%i' "$1"; else stat -f '%d:%i' "$1"; fi
}
file_digest() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi
}
stream_digest() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum | awk '{print $1}'; else shasum -a 256 | awk '{print $1}'; fi
}
refs_digest_without_remote_main() {
  git --git-dir="$1" for-each-ref --format='%(refname) %(objectname)' |
    awk '$1 != "refs/remotes/origin/main"' | stream_digest
}
fetch_head_identity() {
  if [[ -e "$1/FETCH_HEAD" ]]; then printf 'present:%s\n' "$(file_digest "$1/FETCH_HEAD")"; else printf 'absent\n'; fi
}

# Mirrors the documented first-use import sequence. It deliberately fetches
# objects without writing FETCH_HEAD or a ref, then updates only origin/main by
# compare-and-swap after the second pinned remote check.
safe_candidate_import() (
  set -euo pipefail
  local repository="$1" approved_sha="$2" expected_origin="$3"
  local current repository_identity prior_main expected_old other_refs_before
  local fetch_head_before remote_line

  [[ -d "$repository" && ! -L "$repository" ]]
  [[ "$(cd -P "$(dirname "$repository")" && pwd)/$(basename "$repository")" == "$repository" ]]
  current="$repository"
  while [[ "$current" != / ]]; do [[ ! -L "$current" ]]; current="$(dirname "$current")"; done
  [[ "$(file_owner "$repository")" == "$(id -u)" ]]
  [[ "$(git --git-dir="$repository" rev-parse --is-bare-repository)" == true ]]
  [[ "$(git --git-dir="$repository" remote | wc -l | tr -d ' ')" == 1 ]]
  [[ "$(git --git-dir="$repository" remote)" == origin ]]
  [[ "$(git --git-dir="$repository" remote get-url --all origin | wc -l | tr -d ' ')" == 1 ]]
  [[ "$(git --git-dir="$repository" remote get-url origin)" == "$expected_origin" ]]
  repository_identity="$(file_identity "$repository")"

  prior_main="$(git --git-dir="$repository" show-ref --verify --hash refs/remotes/origin/main 2>/dev/null || true)"
  [[ -z "$prior_main" || "${#prior_main}" == 40 ]]
  expected_old="${prior_main:-$ZERO_OID}"
  other_refs_before="$(refs_digest_without_remote_main "$repository")"
  fetch_head_before="$(fetch_head_identity "$repository")"

  remote_line="$(git --git-dir="$repository" ls-remote --refs origin refs/heads/main)"
  [[ "$remote_line" == "$(printf '%s\t%s' "$approved_sha" refs/heads/main)" ]] || exit 20

  chmod 700 "$repository"
  [[ "$(file_identity "$repository")" == "$repository_identity" ]]
  [[ "$(file_owner "$repository")" == "$(id -u)" && "$(file_mode "$repository")" == 700 ]]
  [[ ! -L "$repository" ]]
  [[ "$(git --git-dir="$repository" rev-parse --is-bare-repository)" == true ]]
  [[ "$(git --git-dir="$repository" remote)" == origin ]]
  [[ "$(git --git-dir="$repository" remote get-url --all origin | wc -l | tr -d ' ')" == 1 ]]
  [[ "$(git --git-dir="$repository" remote get-url origin)" == "$expected_origin" ]]

  git --git-dir="$repository" fetch --quiet --no-tags --no-write-fetch-head origin "$approved_sha"
  if [[ -n "${STAGING_IMPORT_TEST_AFTER_FETCH_HOOK:-}" ]]; then
    "$STAGING_IMPORT_TEST_AFTER_FETCH_HOOK" "$repository" "$approved_sha" "$expected_origin"
  fi
  remote_line="$(git --git-dir="$repository" ls-remote --refs origin refs/heads/main)"
  [[ "$remote_line" == "$(printf '%s\t%s' "$approved_sha" refs/heads/main)" ]] || exit 21
  git --git-dir="$repository" cat-file -e "$approved_sha^{commit}"
  [[ "$(file_identity "$repository")" == "$repository_identity" ]]
  [[ "$(file_owner "$repository")" == "$(id -u)" && "$(file_mode "$repository")" == 700 ]]
  [[ ! -L "$repository" ]]
  [[ "$(git --git-dir="$repository" rev-parse --is-bare-repository)" == true ]]
  [[ "$(git --git-dir="$repository" remote)" == origin ]]
  [[ "$(git --git-dir="$repository" remote get-url --all origin | wc -l | tr -d ' ')" == 1 ]]
  [[ "$(git --git-dir="$repository" remote get-url origin)" == "$expected_origin" ]]

  if [[ -n "${STAGING_IMPORT_TEST_BEFORE_CAS_HOOK:-}" ]]; then
    "$STAGING_IMPORT_TEST_BEFORE_CAS_HOOK" "$repository" "$approved_sha" "$expected_origin"
  fi
  git --git-dir="$repository" update-ref refs/remotes/origin/main "$approved_sha" "$expected_old" || exit 22
  [[ "$(git --git-dir="$repository" show-ref --verify --hash refs/remotes/origin/main)" == "$approved_sha" ]]
  [[ "$(refs_digest_without_remote_main "$repository")" == "$other_refs_before" ]]
  [[ "$(fetch_head_identity "$repository")" == "$fetch_head_before" ]]
)

grep -Fq -- '--no-write-fetch-head' "$RUNBOOK" || fail 'runbook lacks object-only fetch'
grep -Fq -- 'update-ref refs/remotes/origin/main' "$RUNBOOK" || fail 'runbook lacks remote-main CAS'
grep -Fq -- 'ls-remote --refs origin refs/heads/main' "$RUNBOOK" || fail 'runbook lacks pinned remote checks'

git init -q "$SOURCE_REPO"
git -C "$SOURCE_REPO" config user.email ops001-import@example.invalid
git -C "$SOURCE_REPO" config user.name OPS001-Import
git -C "$SOURCE_REPO" checkout -qb main
printf 'old\n' >"$SOURCE_REPO/fixture.txt"
git -C "$SOURCE_REPO" add fixture.txt
git -C "$SOURCE_REPO" commit -qm 'old candidate'
OLD_SHA="$(git -C "$SOURCE_REPO" rev-parse HEAD)"

git init -q --bare "$STAGING_REPO"
git --git-dir="$STAGING_REPO" remote add origin "$SOURCE_REPO"
git --git-dir="$STAGING_REPO" fetch -q --no-tags --no-write-fetch-head origin "$OLD_SHA"
git --git-dir="$STAGING_REPO" update-ref refs/remotes/origin/main "$OLD_SHA" "$ZERO_OID"
git --git-dir="$STAGING_REPO" update-ref refs/ops001/sentinel "$OLD_SHA" "$ZERO_OID"
printf 'preserve-fetch-head\n' >"$STAGING_REPO/FETCH_HEAD"
chmod 775 "$STAGING_REPO"

printf 'new\n' >>"$SOURCE_REPO/fixture.txt"
git -C "$SOURCE_REPO" add fixture.txt
git -C "$SOURCE_REPO" commit -qm 'approved candidate'
APPROVED_SHA="$(git -C "$SOURCE_REPO" rev-parse HEAD)"

ref_before="$(git --git-dir="$STAGING_REPO" show-ref --verify --hash refs/remotes/origin/main)"
other_refs_before="$(refs_digest_without_remote_main "$STAGING_REPO")"
fetch_head_before="$(fetch_head_identity "$STAGING_REPO")"
set +e
safe_candidate_import "$STAGING_REPO" "$OLD_SHA" "$SOURCE_REPO"
mismatch_status=$?
set -e
[[ "$mismatch_status" -ne 0 ]] || fail 'remote mismatch unexpectedly imported the stale approved SHA'
[[ "$(file_mode "$STAGING_REPO")" == 775 ]] || fail 'pre-fetch mismatch mutated repository mode'
[[ "$(git --git-dir="$STAGING_REPO" show-ref --verify --hash refs/remotes/origin/main)" == "$ref_before" ]] ||
  fail 'pre-fetch mismatch changed origin/main'
[[ "$(refs_digest_without_remote_main "$STAGING_REPO")" == "$other_refs_before" ]] ||
  fail 'pre-fetch mismatch changed another ref'
[[ "$(fetch_head_identity "$STAGING_REPO")" == "$fetch_head_before" ]] ||
  fail 'pre-fetch mismatch changed FETCH_HEAD'

safe_candidate_import "$STAGING_REPO" "$APPROVED_SHA" "$SOURCE_REPO"
[[ "$(file_mode "$STAGING_REPO")" == 700 ]] || fail 'successful import did not secure repository mode'
[[ "$(git --git-dir="$STAGING_REPO" show-ref --verify --hash refs/remotes/origin/main)" == "$APPROVED_SHA" ]] ||
  fail 'successful import did not bind origin/main to the approved SHA'
[[ "$(refs_digest_without_remote_main "$STAGING_REPO")" == "$other_refs_before" ]] ||
  fail 'successful import changed another ref'
[[ "$(fetch_head_identity "$STAGING_REPO")" == "$fetch_head_before" ]] ||
  fail 'successful object fetch changed FETCH_HEAD'

git --git-dir="$STAGING_REPO" update-ref refs/remotes/origin/main "$OLD_SHA" "$APPROVED_SHA"
chmod 775 "$STAGING_REPO"
cat >"$TMP_DIR/after-fetch-race.sh" <<'HOOK'
#!/usr/bin/env bash
set -euo pipefail
source_repo="$3"
printf 'remote-race\n' >>"$source_repo/fixture.txt"
git -C "$source_repo" add fixture.txt
git -C "$source_repo" commit -qm 'post-fetch remote race'
HOOK
chmod +x "$TMP_DIR/after-fetch-race.sh"
set +e
STAGING_IMPORT_TEST_AFTER_FETCH_HOOK="$TMP_DIR/after-fetch-race.sh" \
  safe_candidate_import "$STAGING_REPO" "$APPROVED_SHA" "$SOURCE_REPO"
post_fetch_status=$?
set -e
[[ "$post_fetch_status" == 21 ]] || fail "post-fetch mismatch status was $post_fetch_status"
[[ "$(git --git-dir="$STAGING_REPO" show-ref --verify --hash refs/remotes/origin/main)" == "$OLD_SHA" ]] ||
  fail 'post-fetch mismatch changed origin/main'
[[ "$(refs_digest_without_remote_main "$STAGING_REPO")" == "$other_refs_before" ]] ||
  fail 'post-fetch mismatch changed another ref'
[[ "$(fetch_head_identity "$STAGING_REPO")" == "$fetch_head_before" ]] ||
  fail 'post-fetch mismatch changed FETCH_HEAD'

RACE_SHA="$(git -C "$SOURCE_REPO" rev-parse HEAD)"
git -C "$SOURCE_REPO" checkout -q --detach "$RACE_SHA"
git -C "$SOURCE_REPO" branch -f main "$APPROVED_SHA"
git -C "$SOURCE_REPO" checkout -q main
git --git-dir="$STAGING_REPO" fetch -q --no-tags --no-write-fetch-head origin "$RACE_SHA"
cat >"$TMP_DIR/before-cas-race.sh" <<HOOK
#!/usr/bin/env bash
set -euo pipefail
git --git-dir="\$1" update-ref refs/remotes/origin/main "$RACE_SHA" "$OLD_SHA"
HOOK
chmod +x "$TMP_DIR/before-cas-race.sh"
set +e
STAGING_IMPORT_TEST_BEFORE_CAS_HOOK="$TMP_DIR/before-cas-race.sh" \
  safe_candidate_import "$STAGING_REPO" "$APPROVED_SHA" "$SOURCE_REPO"
cas_status=$?
set -e
[[ "$cas_status" == 22 ]] || fail "CAS-contention status was $cas_status"
[[ "$(git --git-dir="$STAGING_REPO" show-ref --verify --hash refs/remotes/origin/main)" == "$RACE_SHA" ]] ||
  fail 'CAS contention overwrote the concurrent origin/main value'
[[ "$(refs_digest_without_remote_main "$STAGING_REPO")" == "$other_refs_before" ]] ||
  fail 'CAS contention changed another ref'
[[ "$(fetch_head_identity "$STAGING_REPO")" == "$fetch_head_before" ]] ||
  fail 'CAS contention changed FETCH_HEAD'

echo 'PASS: exact candidate import pins remote main before/after object-only fetch, preserves FETCH_HEAD/other refs on both mismatches, and fails closed on CAS contention.'
