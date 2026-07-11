#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}"
BACKUP_FILE="${1:-}"

if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
  SUDO=()
else
  SUDO=(sudo)
fi

cd "$SCRIPT_DIR"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

if [[ -z "$BACKUP_FILE" ]]; then
  die "Usage: $0 <backup-file.dump>"
fi

if [[ ! -f "$BACKUP_FILE" ]]; then
  die "Backup file not found: $BACKUP_FILE"
fi

if [[ ! -f "$ENV_FILE" ]]; then
  die "Missing $ENV_FILE. Copy .env.example to .env and fill database settings first."
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    die "Missing required environment value: $name"
  fi
}

compose() {
  "${SUDO[@]}" docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

require_env DB_NAME
require_env DB_USER
require_env DB_PASSWORD

echo "Danger: database restore can overwrite live data."
echo "The backend container will be stopped during restore and restarted afterwards."
read -r -p "Type RESTORE to continue: " confirmation

if [[ "$confirmation" != "RESTORE" ]]; then
  die "Restore cancelled."
fi

compose up -d db
compose stop backend || true

echo "Restoring $BACKUP_FILE into $DB_NAME..."
compose exec -T -e PGPASSWORD="$DB_PASSWORD" db \
  pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner --no-privileges \
  < "$BACKUP_FILE"

compose up -d backend
echo "Restore complete."
