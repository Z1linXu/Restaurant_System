#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}"
ENABLE_HTTPS_OVERRIDE=""

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

usage() {
  cat <<'USAGE'
Usage: ./deploy.sh [--http-only|--https]

  --http-only  Deploy with ENABLE_HTTPS=false. DOMAIN and LETSENCRYPT_EMAIL are optional.
  --https      Deploy with ENABLE_HTTPS=true. DOMAIN and LETSENCRYPT_EMAIL are required.
USAGE
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --http-only)
        ENABLE_HTTPS_OVERRIDE=false
        ;;
      --https)
        ENABLE_HTTPS_OVERRIDE=true
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        die "Unknown argument: $1"
        ;;
    esac
    shift
  done
}

load_env() {
  if [[ ! -f "$ENV_FILE" ]]; then
    cp "$SCRIPT_DIR/.env.example" "$ENV_FILE"
    die "Created $ENV_FILE. Fill DB_PASSWORD and JWT_SECRET. For HTTPS, also fill DOMAIN and LETSENCRYPT_EMAIL."
  fi

  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  if [[ -n "$ENABLE_HTTPS_OVERRIDE" ]]; then
    ENABLE_HTTPS="$ENABLE_HTTPS_OVERRIDE"
    export ENABLE_HTTPS
  fi
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "$value" ]]; then
    die "Missing required environment value: $name"
  fi
}

require_not_placeholder() {
  local name="$1"
  local value="${!name:-}"
  if [[ "$value" == replace-with-* || "$value" == "pos.example.com" || "$value" == "owner@example.com" ]]; then
    die "$name still contains a placeholder value."
  fi
}

compose() {
  "${SUDO[@]}" docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

install_docker() {
  if command -v docker >/dev/null 2>&1 && "${SUDO[@]}" docker compose version >/dev/null 2>&1; then
    echo "Docker and Docker Compose plugin are already installed."
    return
  fi

  echo "Installing Docker Engine and Docker Compose plugin..."
  "${SUDO[@]}" apt-get update
  "${SUDO[@]}" apt-get install -y ca-certificates curl gnupg lsb-release git openssl
  "${SUDO[@]}" install -m 0755 -d /etc/apt/keyrings

  if [[ ! -f /etc/apt/keyrings/docker.gpg ]]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
      | "${SUDO[@]}" gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    "${SUDO[@]}" chmod a+r /etc/apt/keyrings/docker.gpg
  fi

  . /etc/os-release
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    | "${SUDO[@]}" tee /etc/apt/sources.list.d/docker.list >/dev/null

  "${SUDO[@]}" apt-get update
  "${SUDO[@]}" apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  "${SUDO[@]}" systemctl enable --now docker
}

create_runtime_dirs() {
  echo "Creating deployment directories..."
  mkdir -p "$SCRIPT_DIR/data/postgres" \
    "$SCRIPT_DIR/data/certbot-www" \
    "$SCRIPT_DIR/data/letsencrypt" \
    "$SCRIPT_DIR/data/nginx" \
    "$SCRIPT_DIR/data/backups"
}

set_nginx_template() {
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

validate_env() {
  require_env DB_NAME
  require_env DB_USER
  require_env DB_PASSWORD
  require_env JWT_SECRET
  require_not_placeholder DB_PASSWORD
  require_not_placeholder JWT_SECRET

  if [[ "${ENABLE_HTTPS:-true}" == "true" ]]; then
    require_env DOMAIN
    require_env LETSENCRYPT_EMAIL
    require_not_placeholder DOMAIN
    require_not_placeholder LETSENCRYPT_EMAIL
  fi
}

wait_for_db() {
  echo "Waiting for PostgreSQL..."
  for _ in {1..60}; do
    if compose exec -T db pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
      echo "PostgreSQL is ready."
      return
    fi
    sleep 2
  done
  compose logs db || true
  die "PostgreSQL did not become ready in time."
}

wait_for_backend() {
  echo "Waiting for backend and Flyway initialization..."
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

certificate_exists() {
  [[ -f "$SCRIPT_DIR/data/letsencrypt/live/$DOMAIN/fullchain.pem" \
    && -f "$SCRIPT_DIR/data/letsencrypt/live/$DOMAIN/privkey.pem" ]]
}

issue_or_renew_certificate() {
  if [[ "${ENABLE_HTTPS:-true}" != "true" ]]; then
    echo "ENABLE_HTTPS is not true; skipping Let's Encrypt certificate issuance."
    return
  fi

  echo "Starting temporary HTTP Nginx for Let's Encrypt validation..."
  set_nginx_template http
  compose up -d --force-recreate nginx

  local certbot_args=(
    --webroot
    --webroot-path /var/www/certbot
    --email "$LETSENCRYPT_EMAIL"
    --agree-tos
    --no-eff-email
    --non-interactive
    -d "$DOMAIN"
  )

  if [[ "${LETSENCRYPT_STAGING:-false}" == "true" ]]; then
    certbot_args+=(--staging)
  fi

  if certificate_exists; then
    echo "Certificate already exists. Running certbot renew."
    if ! compose --profile tools run --rm certbot renew --webroot -w /var/www/certbot; then
      echo "Certbot renew failed. PostgreSQL and backend are still running; keeping HTTP Nginx active." >&2
      set_nginx_template http
      compose up -d --force-recreate nginx || true
      return 1
    fi
  else
    echo "Requesting initial Let's Encrypt certificate for $DOMAIN..."
    if ! compose --profile tools run --rm certbot certonly "${certbot_args[@]}"; then
      echo "Certbot certificate request failed. PostgreSQL and backend are still running; keeping HTTP Nginx active." >&2
      set_nginx_template http
      compose up -d --force-recreate nginx || true
      return 1
    fi
  fi
}

start_services() {
  if [[ "${ENABLE_HTTPS:-true}" == "true" ]]; then
    echo "Starting final HTTPS Nginx configuration..."
    set_nginx_template https
    compose up -d --force-recreate nginx
  else
    echo "Starting HTTP-only Nginx configuration..."
    set_nginx_template http
    compose up -d --force-recreate nginx
  fi

  compose up -d db backend
  compose ps
}

main() {
  echo "Restaurant POS cloud deploy"
  echo "Repository: $REPO_ROOT"
  parse_args "$@"
  load_env
  validate_env
  install_docker
  create_runtime_dirs
  set_nginx_template http

  echo "Validating docker compose configuration..."
  compose config >/dev/null

  echo "Starting PostgreSQL..."
  compose up -d db
  wait_for_db

  echo "Building backend and frontend images..."
  compose build backend nginx

  echo "Starting backend to initialize schema through Flyway..."
  compose up -d backend
  wait_for_backend

  if ! issue_or_renew_certificate; then
    compose ps || true
    die "HTTPS certificate step failed. Fix DNS/firewall/certbot, then rerun ./deploy.sh --https."
  fi
  start_services

  echo "Deployment finished."
  echo "Run: $SCRIPT_DIR/health-check.sh"
}

main "$@"
