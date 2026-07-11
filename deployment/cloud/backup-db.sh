#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}"

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

BACKUP_DIR="${BACKUP_DIR:-./data/backups}"
mkdir -p "$BACKUP_DIR"

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="$BACKUP_DIR/${DB_NAME}-${timestamp}.dump"

echo "Creating PostgreSQL custom-format backup at $backup_file"
compose exec -T -e PGPASSWORD="$DB_PASSWORD" db \
  pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --no-owner --no-privileges \
  > "$backup_file"

echo "Backup complete: $backup_file"
