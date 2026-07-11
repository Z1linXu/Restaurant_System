#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}"
MODE="apply"

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

cleanup() {
  unset OWNER_PASSWORD OWNER_PASSWORD_CONFIRM
}
trap cleanup EXIT

usage() {
  cat <<'USAGE'
Usage: ./bootstrap-admin.sh [--dry-run|--validate]

Creates the first production owner/admin account exactly once.

  --dry-run   Validate inputs and one-time database guard without writing data.
  --validate  Alias for --dry-run.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run|--validate)
      MODE="dry-run"
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

if [[ ! -f "$ENV_FILE" ]]; then
  die "Missing $ENV_FILE. Copy .env.example to .env and deploy first."
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

compose() {
  "${SUDO[@]}" docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

prompt_required() {
  local prompt="$1"
  local value=""
  while [[ -z "$value" ]]; do
    read -r -p "$prompt" value
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
  done
  printf '%s' "$value"
}

ORGANIZATION_NAME="$(prompt_required "Organization name: ")"
echo
STORE_NAME="$(prompt_required "Store name: ")"
echo
OWNER_USERNAME="$(prompt_required "Owner username: ")"
echo
OWNER_FULL_NAME="$(prompt_required "Owner full name: ")"
echo
read -r -p "Owner email/phone (optional): " OWNER_CONTACT
echo
read -r -s -p "Owner password: " OWNER_PASSWORD
echo
read -r -s -p "Confirm owner password: " OWNER_PASSWORD_CONFIRM
echo

if [[ -z "$OWNER_PASSWORD" ]]; then
  die "Owner password is required."
fi

if [[ "$OWNER_PASSWORD" != "$OWNER_PASSWORD_CONFIRM" ]]; then
  die "Password confirmation does not match."
fi
unset OWNER_PASSWORD_CONFIRM

compose up -d db backend >/dev/null

{
  printf '%s\n' "$MODE"
  printf '%s\n' "$ORGANIZATION_NAME"
  printf '%s\n' "$STORE_NAME"
  printf '%s\n' "$OWNER_USERNAME"
  printf '%s\n' "$OWNER_FULL_NAME"
  printf '%s\n' "$OWNER_CONTACT"
  printf '%s\n' "$OWNER_PASSWORD"
} | compose run --rm -T --no-deps --entrypoint sh backend -c '
  java ${JAVA_OPTS:-} -jar /app/app.jar \
    --spring.main.web-application-type=none \
    --spring.main.banner-mode=off \
    --logging.level.root=ERROR \
    --app.seed.default-users-enabled=false \
    --app.seed.demo-data-enabled=false \
    --app.seed.membership-supplement-enabled=false \
    --app.seed.production-bootstrap-enabled=false \
    --app.bootstrap-admin.enabled=true
'
