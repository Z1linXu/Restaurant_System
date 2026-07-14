#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="${ENV_FILE:-.env}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
TLS_MODE="http"
ACTION="deploy"
PULL_IMAGES="false"

usage() {
  cat <<'EOF'
Restaurant POS cloud deploy helper.

Usage:
  ./deploy.sh [--http|--https] [--validate|--dry-run] [--pull-images]
  ./deploy.sh --help

Default behavior:
  1. Require .env
  2. Render Nginx template for HTTP mode
  3. Run docker compose config
  4. Build local backend and nginx images
  5. Run docker compose up -d

Options:
  --http         Use HTTP Nginx template. Default.
  --https       Use HTTPS Nginx template. Requires valid certificates under data/letsencrypt.
  --validate    Render templates and validate compose only. Does not build or start services.
  --dry-run     Alias for --validate.
  --pull-images Pull configured images before build/up. Do not use with default :local images.
  --help        Print this help text only.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --http)
      TLS_MODE="http"
      ;;
    --https)
      TLS_MODE="https"
      ;;
    --validate|--dry-run)
      ACTION="validate"
      ;;
    --pull-images)
      PULL_IMAGES="true"
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Copy .env.example to .env and fill deployment values first." >&2
  exit 1
fi

mkdir -p data/nginx data/certbot-www data/letsencrypt data/postgres

case "$TLS_MODE" in
  http)
    cp nginx.http.conf.template data/nginx/default.conf.template
    ;;
  https)
    cp nginx.https.conf.template data/nginx/default.conf.template
    ;;
  *)
    echo "Unsupported TLS mode: $TLS_MODE" >&2
    exit 2
    ;;
esac

echo "Validating compose configuration for $TLS_MODE mode..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config >/dev/null

if [[ "$ACTION" == "validate" ]]; then
  echo "Validation passed. No services were built or started."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --services
  exit 0
fi

if [[ "$PULL_IMAGES" == "true" ]]; then
  echo "Pulling configured images because --pull-images was requested..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull
fi

echo "Building local backend and nginx images..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build backend nginx

echo "Starting db, backend, and nginx..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d

echo "Deployment finished. Run ./health-check.sh and complete FINAL_SMOKE_TEST_CHECKLIST.md."
