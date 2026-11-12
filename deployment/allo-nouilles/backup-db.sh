#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly root=/srv/allo-nouilles
readonly compose_file="$root/compose.yml"
readonly env_file="$root/private/runtime.env"
readonly expected_project=allo-nouilles

cd "$root"
exec 9> "$root/state/backup.lock"
flock -n 9 || { echo 'An Allo backup is already running' >&2; exit 1; }
compose=(docker compose --project-name "$expected_project" --env-file "$env_file" -f "$compose_file")
db_id="$("${compose[@]}" ps -q db)"
[[ -n "$db_id" ]] || { echo 'Allo database is not running' >&2; exit 1; }
[[ "$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$db_id")" == "$expected_project" ]]
[[ "$("${compose[@]}" exec -T db psql -XAt -U allo_test -d restaurant_allo_nouilles_test -c 'select current_database()')" == restaurant_allo_nouilles_test ]]

mkdir -p "$root/backups"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$root/backups/allo-$stamp.dump"
partial="$target.partial"
trap 'rm -f -- "$partial"' EXIT
"${compose[@]}" exec -T db pg_dump -U allo_test -d restaurant_allo_nouilles_test -Fc --no-owner --no-acl > "$partial"
[[ -s "$partial" ]]
"${compose[@]}" exec -T db pg_restore --list < "$partial" > "$target.list"
mv -- "$partial" "$target"
sha256sum "$target" > "$target.sha256"
printf '%s\n' "$target"
# This verifies archive readability. Rehearse a full restore separately.
# Bound retention to this script's own daily backups; preserve initial imports.
while IFS= read -r -d '' expired; do
  rm -f -- "$expired" "$expired.list" "$expired.sha256"
done < <(find "$root/backups" -maxdepth 1 -type f -user root -name 'allo-[0-9]*.dump' -mtime +14 -print0)
