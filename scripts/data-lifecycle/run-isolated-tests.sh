#!/usr/bin/env bash
set -euo pipefail
umask 077
task_dir="$(cd "$(dirname "$0")" && pwd)"
pg_bin="${LIFECYCLE_PG_BIN:-/opt/homebrew/opt/postgresql@16/bin}"
scratch="$(mktemp -d "${TMPDIR:-/tmp}/restaurant-lifecycle-synthetic.XXXXXX")"
cleanup() {
  if [[ -f "$scratch/pgdata/postmaster.pid" ]]; then
    "$pg_bin/pg_ctl" -D "$scratch/pgdata" -m fast -w stop >/dev/null
  fi
  # Preserve the private synthetic fixture/logs for debugging; no shared path is removed.
}
trap cleanup EXIT
printf '%s\n' 'SYNTHETIC_ONLY_NO_PRODUCTION_DATA' > "$scratch/.resource-lifecycle-synthetic"
"$pg_bin/initdb" -D "$scratch/pgdata" --no-locale -E UTF8 -A trust > "$scratch/initdb.log"
"$pg_bin/pg_ctl" -D "$scratch/pgdata" -l "$scratch/postgres.log" -o '-h 127.0.0.1 -p 55487' -w start > "$scratch/start.log"
export PGHOST=127.0.0.1 PGPORT=55487 PGDATABASE=resource_lifecycle_test
export PSQL="$pg_bin/psql" LIFECYCLE_TEST_POSTGRES=1 LIFECYCLE_SANDBOX_ROOT="$scratch"
export PYTHONDONTWRITEBYTECODE=1
unset PGSERVICE PGSERVICEFILE PGOPTIONS
"$pg_bin/createdb" -h "$PGHOST" -p "$PGPORT" "$PGDATABASE"
python3 "$task_dir/test_lifecycle.py"
printf 'Synthetic sandbox: %s (stopped on exit)\n' "$scratch"
