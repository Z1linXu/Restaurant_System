#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
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
  die "Missing $ENV_FILE. Copy .env.example to .env and fill deployment values first."
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

compose() {
  "${SUDO[@]}" docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

set_nginx_template() {
  mkdir -p "$SCRIPT_DIR/data/nginx"
  local mode="$1"
  case "$mode" in
    http)
      cp "$SCRIPT_DIR/nginx.http.conf" "$SCRIPT_DIR/data/nginx/default.conf.template"
      ;;
    https)
      cp "$SCRIPT_DIR/nginx.conf" "$SCRIPT_DIR/data/nginx/default.conf.template"
      ;;
    *)
      die "Unknown nginx template mode: $mode"
      ;;
  esac
}

wait_for_backend() {
  echo "Waiting for backend..."
  for _ in {1..90}; do
    if compose run --rm --no-deps nginx wget -qO- http://backend:8080/api/v1/menu/health >/dev/null 2>&1; then
      echo "Backend is ready."
      return
    fi
    sleep 2
  done
  compose logs backend || true
  die "Backend did not become ready in time."
}

if [[ "${UPDATE_GIT_PULL:-false}" == "true" ]]; then
  echo "Pulling latest code with git pull --ff-only..."
  git -C "$REPO_ROOT" pull --ff-only
fi

if [[ "${ENABLE_HTTPS:-true}" == "true" ]]; then
  set_nginx_template https
else
  set_nginx_template http
fi

echo "Validating docker compose configuration..."
compose config >/dev/null

echo "Building updated backend and frontend images..."
compose build backend nginx

echo "Starting database and backend..."
compose up -d db backend
wait_for_backend

if [[ "${ENABLE_HTTPS:-true}" == "true" ]]; then
  if [[ -f "$SCRIPT_DIR/data/letsencrypt/live/$DOMAIN/fullchain.pem" ]]; then
    echo "Running certbot renew..."
    compose --profile tools run --rm certbot renew --webroot -w /var/www/certbot || true
    set_nginx_template https
    compose up -d --force-recreate nginx
  else
    die "HTTPS is enabled but no certificate exists for $DOMAIN. Run deploy.sh first."
  fi
else
  set_nginx_template http
  compose up -d --force-recreate nginx
fi

compose ps
echo "Update finished."
