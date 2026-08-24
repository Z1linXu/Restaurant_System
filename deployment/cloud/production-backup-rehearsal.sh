#!/usr/bin/env bash
set -Eeuo pipefail

readonly SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
readonly EXPECTED_PROJECT="cloud"
readonly EXPECTED_DB_CONTAINER="cloud-db-1"
readonly BACKUP_ROOT="/home/ubuntu/Restaurant_System/deployment/cloud/backups"
readonly EXPECTED_CONTROL_ROOT="/home/ubuntu/Restaurant_System/deployment/cloud"
readonly EXPECTED_POSTGRES_DATA_DIR="$EXPECTED_CONTROL_ROOT/data/postgres"
readonly FLYWAY_MANIFEST="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ops001-flyway-production-checksums.txt"
readonly OPS_LOCK="$EXPECTED_CONTROL_ROOT/.production-ops.lock"
readonly DOCKER_TIMEOUT_SECONDS=120
readonly RESTORE_TIMEOUT_SECONDS=900
ACTION=""; BACKUP_FILE=""; EXPECTED_BACKUP_SHA256=""; REHEARSAL_CONTAINER=""; REHEARSAL_NAME=""; TEMP_FILE=""
RC_MANIFEST=""; RC_MANIFEST_SHA256=""
RUN_ID="$(openssl rand -hex 16)"
FINALIZED="false"

die() { printf 'NO_GO|%s\n' "$*" >&2; exit 1; }
digest() { sha256sum "$1" | awk '{print $1}'; }
path_has_symlink() { local path="$1" part current="" old_ifs="$IFS"; IFS='/'; set -- $path; IFS="$old_ifs"; for part in "$@"; do [[ -n "$part" ]] || continue; current="$current/$part"; [[ ! -L "$current" ]] || return 0; done; return 1; }
bounded() { local seconds="$1"; shift; timeout --foreground --kill-after=10s "${seconds}s" "$@"; }
docker_default() { bounded "$DOCKER_TIMEOUT_SECONDS" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"; }
docker_long() { bounded "$RESTORE_TIMEOUT_SECONDS" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"; }
docker_until() {
  local deadline="$1" remaining
  shift
  remaining=$((deadline - SECONDS))
  (( remaining > 0 )) || return 124
  bounded "$remaining" env -i PATH="$SAFE_PATH" HOME="$HOME" DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker}" docker --context default "$@"
}
exact_container_id() {
  local output
  output="$(docker_default ps -aq --no-trunc --filter "name=^/${1}$")" || return 1
  [[ -z "$output" || "$output" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf '%s' "$output"
}
wait_fresh_postgres_ready() {
  local container_id="$1" database_user="$2" database_name="$3" timeout_seconds="${4:-120}" deadline logs remaining
  [[ "$timeout_seconds" =~ ^[0-9]+$ && "$timeout_seconds" -ge 2 && "$timeout_seconds" -le 120 ]] || return 1
  deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    logs="$(docker_until "$deadline" logs "$container_id" 2>&1)" || return 1
    if grep -Fq 'PostgreSQL init process complete; ready for start up.' <<<"$logs" &&
       docker_until "$deadline" exec "$container_id" pg_isready -U "$database_user" -d "$database_name" >/dev/null 2>&1; then
      remaining=$((deadline - SECONDS))
      (( remaining > 1 )) || return 1
      sleep 1
      [[ "$(docker_until "$deadline" inspect --format '{{.State.Running}}|{{.State.Restarting}}' "$container_id")" == "true|false" ]] || return 1
      if docker_until "$deadline" exec "$container_id" pg_isready -U "$database_user" -d "$database_name" >/dev/null 2>&1; then
        (( SECONDS < deadline )) && return 0
        return 1
      fi
    fi
    remaining=$((deadline - SECONDS))
    (( remaining > 1 )) || return 1
    sleep 1
  done
  return 1
}
cleanup() {
  local actual="" actual_id="" remaining="" leftovers=""
  if [[ -n "$REHEARSAL_NAME" ]]; then
    actual_id="$(exact_container_id "$REHEARSAL_NAME")" || return 1
    if [[ -n "$actual_id" ]]; then
      actual="$(docker_default inspect --format '{{.Id}}|{{index .Config.Labels "restaurant.production-rehearsal.owner"}}' "$actual_id")" || return 1
      [[ "${actual#*|}" == "$RUN_ID" ]] || return 1
      [[ -z "$REHEARSAL_CONTAINER" || "${actual%%|*}" == "$REHEARSAL_CONTAINER" ]] || return 1
      docker_default rm -f "${actual%%|*}" >/dev/null || return 1
    fi
    remaining="$(exact_container_id "$REHEARSAL_NAME")" || return 1
    [[ -z "$remaining" ]] || return 1
  fi
  leftovers="$(docker_default ps -aq --no-trunc --filter label=restaurant.production-rehearsal.owner="$RUN_ID")" || return 1
  [[ -z "$leftovers" ]] || return 1
  [[ -z "$TEMP_FILE" ]] || rm -f -- "$TEMP_FILE"
}
on_exit() {
  local result=$?
  trap - EXIT
  if [[ "$FINALIZED" != "true" ]]; then cleanup || result=1; fi
  exit "$result"
}
trap on_exit EXIT

case "${1:-}" in
  --backup) ACTION="backup"; [[ "${2:-}" == "--rc-manifest" && "${4:-}" == "--rc-manifest-sha256" ]] || die "RC manifest binding is required"; RC_MANIFEST="${3:-}"; RC_MANIFEST_SHA256="${5:-}" ;;
  --rehearse) ACTION="rehearse"; BACKUP_FILE="${2:-}"; [[ "${3:-}" == "--expected-sha256" && "${5:-}" == "--rc-manifest" && "${7:-}" == "--rc-manifest-sha256" ]] || die "backup and RC digests are required"; EXPECTED_BACKUP_SHA256="${4:-}"; RC_MANIFEST="${6:-}"; RC_MANIFEST_SHA256="${8:-}" ;;
  *) die "usage: $0 --backup --rc-manifest <json> --rc-manifest-sha256 <sha256> | --rehearse <backup> --expected-sha256 <sha256> --rc-manifest <json> --rc-manifest-sha256 <sha256>" ;;
esac
command -v timeout >/dev/null 2>&1 || die "GNU timeout is required"
[[ "$RC_MANIFEST" == /* && -f "$RC_MANIFEST" && ! -L "$RC_MANIFEST" && "$RC_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ && "$(digest "$RC_MANIFEST")" == "$RC_MANIFEST_SHA256" ]] || die "RC manifest identity differs"
path_has_symlink "$RC_MANIFEST" && die "RC manifest traverses a symlink"
tooling_output="$(env -i PATH="$SAFE_PATH" python3 -I - "$RC_MANIFEST" <<'PY'
import json,sys
d=json.load(open(sys.argv[1], encoding='utf-8'))
print(d.get('status',''))
print(d.get('tooling_commit_sha',''))
print(d.get('backup_helper_sha256',''))
print(d.get('backup_flyway_target','V7'))
PY
)" || die "RC manifest is invalid"
mapfile -t tooling <<<"$tooling_output"
unset tooling_output
[[ "${tooling[0]:-}" == "RC_PREPARED" || "${tooling[0]:-}" == "RC_FROZEN" ]] || die "RC status is invalid"
[[ "${tooling[1]:-}" =~ ^[0-9a-f]{40}$ && "${tooling[2]:-}" =~ ^[0-9a-f]{64}$ ]] || die "RC tooling identity is not frozen"
[[ "${tooling[3]:-}" =~ ^V[0-9]+$ ]] || die "backup Flyway target is invalid"
[[ "$(git -C "$(dirname "${BASH_SOURCE[0]}")/../.." rev-parse HEAD)" == "${tooling[1]}" && -z "$(git -C "$(dirname "${BASH_SOURCE[0]}")/../.." status --porcelain)" && "$(digest "${BASH_SOURCE[0]}")" == "${tooling[2]}" ]] || die "backup tooling blob differs from RC"
[[ -d "$BACKUP_ROOT" && ! -L "$BACKUP_ROOT" && "$(realpath "$BACKUP_ROOT")" == "$BACKUP_ROOT" ]] || die "fixed backup root is unsafe"
path_has_symlink "$BACKUP_ROOT" && die "backup root traverses a symlink"
[[ "$(stat -c '%a|%u' "$BACKUP_ROOT")" == "700|$(id -u)" ]] || die "backup root must be owner-only mode 0700"
if [[ ! -e "$OPS_LOCK" ]]; then (umask 077; set -o noclobber; : >"$OPS_LOCK") 2>/dev/null || die "cannot create Production ops lock"; fi
[[ -f "$OPS_LOCK" && ! -L "$OPS_LOCK" && "$(stat -c '%a|%u' "$OPS_LOCK")" == "600|$(id -u)" ]] || die "Production ops lock identity is unsafe"
path_has_symlink "$OPS_LOCK" && die "Production ops lock traverses a symlink"
exec 9<>"$OPS_LOCK"; flock -n 9 || die "another Production operation holds the lock"
[[ "$(docker_default inspect --format '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.State.Health.Status}}' "$EXPECTED_DB_CONTAINER")" == "$EXPECTED_PROJECT|db|healthy" ]] || die "Production DB identity/health differs"
DB_ID_BEFORE="$(docker_default inspect --format '{{.Id}}' "$EXPECTED_DB_CONTAINER")"
DB_MOUNT="$(docker_default inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Type}}|{{.Source}}|{{.RW}}{{end}}{{end}}' "$EXPECTED_DB_CONTAINER")"
[[ "$DB_MOUNT" == "bind|$EXPECTED_POSTGRES_DATA_DIR|true" ]] || die "Production DB fixed mount differs"
[[ "$(df -Pk "$BACKUP_ROOT" | awk 'NR==2 {print $4}')" -ge 1048576 ]] || die "backup root has less than 1 GiB free"
POSTGRES_IMAGE_ID="$(docker_default inspect --format '{{.Image}}' "$EXPECTED_DB_CONTAINER")"
[[ "$POSTGRES_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] || die "PostgreSQL image ID is invalid"

integrity_check() {
  local file="$1" result=0 remaining
  [[ -z "$REHEARSAL_NAME" && -z "$REHEARSAL_CONTAINER" ]] || return 1
  REHEARSAL_NAME="production-backup-integrity-${RUN_ID:0:16}"
  docker_default run --rm --pull=never --name "$REHEARSAL_NAME" \
    --label restaurant.production-rehearsal.owner="$RUN_ID" --network none --read-only --user 0:0 \
    --cpus 1 --memory 512m --pids-limit 128 -v "$file:/backup.dump:ro" \
    "$POSTGRES_IMAGE_ID" pg_restore --list /backup.dump >/dev/null || result=$?
  remaining="$(exact_container_id "$REHEARSAL_NAME")" || return 1
  if [[ "$result" -eq 0 && -z "$remaining" ]]; then
    REHEARSAL_NAME=""
    return 0
  fi
  return 1
}

if [[ "$ACTION" == "backup" ]]; then
  umask 077; timestamp="$(date -u +%Y%m%dT%H%M%SZ)"; final="$BACKUP_ROOT/restaurant-pos-predeploy-$timestamp.dump"
  TEMP_FILE="$(mktemp "$BACKUP_ROOT/.restaurant-pos-predeploy-$timestamp.XXXXXX.tmp")"
  [[ ! -e "$final" ]] || die "backup target already exists"
  docker_long exec "$EXPECTED_DB_CONTAINER" sh -eu -c 'timeout -s TERM -k 10 840 pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' >"$TEMP_FILE"
  [[ "$(docker_default inspect --format '{{.Id}}' "$EXPECTED_DB_CONTAINER")" == "$DB_ID_BEFORE" ]] || die "Production DB changed during backup"
  [[ -s "$TEMP_FILE" ]] || die "backup is empty"; chmod 600 "$TEMP_FILE"; integrity_check "$TEMP_FILE" || die "backup integrity failed"
  mv "$TEMP_FILE" "$final"; TEMP_FILE=""
  cleanup || die "backup helper temporary-resource cleanup failed"
  FINALIZED="true"
  printf 'BACKUP|file=%s|size=%s|sha256=%s|created_utc=%s|integrity=PASS\n' "$(basename "$final")" "$(stat -c '%s' "$final")" "$(digest "$final")" "$timestamp"
  exit 0
fi

[[ "$EXPECTED_BACKUP_SHA256" =~ ^[0-9a-f]{64}$ ]] || die "expected backup digest is invalid"
[[ "$BACKUP_FILE" == /* && -f "$BACKUP_FILE" && ! -L "$BACKUP_FILE" ]] || die "rehearsal backup is not a regular absolute file"
path_has_symlink "$BACKUP_FILE" && die "rehearsal backup traverses a symlink"
canonical_backup="$(realpath "$BACKUP_FILE")"
[[ "$(dirname "$canonical_backup")" == "$BACKUP_ROOT" ]] || die "rehearsal backup is outside fixed root"
[[ "$(stat -c '%a|%u' "$canonical_backup")" == "600|$(id -u)" ]] || die "rehearsal backup owner/mode differs"
[[ "$(digest "$canonical_backup")" == "$EXPECTED_BACKUP_SHA256" ]] || die "rehearsal backup digest differs"
integrity_check "$canonical_backup" || die "backup integrity failed"

REHEARSAL_NAME="production-restore-rehearsal-$(date -u +%Y%m%d%H%M%S)-${RUN_ID:0:12}"
existing_rehearsal="$(exact_container_id "$REHEARSAL_NAME")" || die "cannot query isolated restore container name"
[[ -z "$existing_rehearsal" ]] || die "isolated restore container name already exists"
rehearsal_password="$(openssl rand -hex 24)"
created_container="$(docker_default run -d --rm --pull=never --name "$REHEARSAL_NAME" --label restaurant.production-rehearsal.owner="$RUN_ID" --network none --cpus 1 --memory 768m --pids-limit 256 \
  --tmpfs /var/lib/postgresql/data:rw,nosuid,nodev,size=512m -e POSTGRES_PASSWORD="$rehearsal_password" -e POSTGRES_DB=restore_rehearsal "$POSTGRES_IMAGE_ID")" || die "cannot create isolated restore container"
[[ "$created_container" =~ ^[0-9a-f]{64}$ ]] || die "isolated restore container identity is invalid"
REHEARSAL_CONTAINER="$created_container"
wait_fresh_postgres_ready "$REHEARSAL_CONTAINER" postgres restore_rehearsal || die "isolated DB did not reach stable post-init readiness"
docker_long exec -i "$REHEARSAL_CONTAINER" timeout -s TERM -k 10 840 pg_restore -U postgres -d restore_rehearsal --no-owner --no-privileges --exit-on-error --single-transaction <"$canonical_backup"
actual_flyway="$(docker_default exec "$REHEARSAL_CONTAINER" psql -X -v ON_ERROR_STOP=1 -At -U postgres -d restore_rehearsal -c "select version || chr(124) || script || chr(124) || success::text || chr(124) || checksum from flyway_schema_history order by installed_rank")"
target_version="${tooling[3]#V}"
expected_flyway="$(awk -F'|' -v target="$target_version" '/^[0-9]+[|]/ && ($1 + 0) <= (target + 0) {print $1 "|" $2 "|true|" $3}' "$FLYWAY_MANIFEST")"
[[ "$actual_flyway" == "$expected_flyway" ]] || die "isolated restore Flyway ledger differs from exact backup target"
cleanup || die "isolated restore resource cleanup failed"
REHEARSAL_CONTAINER=""; REHEARSAL_NAME=""
FINALIZED="true"
printf 'RESTORE_REHEARSAL|backup_sha256=%s|flyway=%s-exact|isolated_tmpfs=true|resources_removed=true|result=PASS\n' "$EXPECTED_BACKUP_SHA256" "${tooling[3]}"
