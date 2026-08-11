#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/deployment/cloud/production-backup-rehearsal.sh"

bash -n "$SCRIPT"
grep -Fq 'BACKUP_ROOT="/home/ubuntu/Restaurant_System/deployment/cloud/backups"' "$SCRIPT"
grep -Fq 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' "$SCRIPT"
grep -Fq 'pg_restore --list /backup.dump' "$SCRIPT"
grep -Fq -- '--exit-on-error --single-transaction' "$SCRIPT"
grep -Fq -- '--network none' "$SCRIPT"
grep -Fq -- '--cpus 1 --memory 768m --pids-limit 256' "$SCRIPT"
grep -Fq -- '--tmpfs /var/lib/postgresql/data' "$SCRIPT"
grep -Fq 'isolated_tmpfs=true' "$SCRIPT"
grep -Fq 'dirname "$canonical_backup"' "$SCRIPT"
grep -Fq 'backup and RC digests are required' "$SCRIPT"
grep -Fq 'isolated restore Flyway ledger differs from exact V1-V7' "$SCRIPT"
grep -Fq 'success::text' "$SCRIPT"
grep -Fq '.production-ops.lock' "$SCRIPT"
grep -Fq 'Production DB fixed mount differs' "$SCRIPT"
grep -Fq 'RC manifest binding is required' "$SCRIPT"
grep -Fq 'backup tooling blob differs from RC' "$SCRIPT"
grep -Fq 'created_container=' "$SCRIPT"
! grep -Eq 'pg_restore.*--clean|docker compose down|down -v|flyway (clean|repair)|rm -rf' "$SCRIPT"
printf 'production backup and isolated restore static guards: PASS\n'
