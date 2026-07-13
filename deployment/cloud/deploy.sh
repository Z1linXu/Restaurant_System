#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="${ENV_FILE:-.env}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

echo "Cloud deployment template. Review every setting before production use."

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Copy .env.example to .env and fill deployment values first." >&2
  exit 1
fi

echo "Validating compose configuration..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config >/dev/null

echo "Pulling configured images..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull

if [[ "${CLOUD_BUILD_LOCAL:-false}" == "true" ]]; then
  echo "CLOUD_BUILD_LOCAL=true, running compose build for services with build definitions."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build
fi

echo "Starting services..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d

echo "Deployment command finished. Run ./health-check.sh and complete the smoke test checklist."
