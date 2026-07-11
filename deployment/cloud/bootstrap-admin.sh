#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}"
MODE="apply"
BOOTSTRAP_ENV_FILE=""
PASSWORD_STDIN=false
SELF_TEST=false

ORGANIZATION_NAME=""
STORE_NAME=""
OWNER_USERNAME=""
OWNER_FULL_NAME=""
OWNER_EMAIL=""
OWNER_PHONE=""
OWNER_CONTACT=""
OWNER_PASSWORD=""
OWNER_PASSWORD_CONFIRM=""

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
  unset OWNER_PASSWORD OWNER_PASSWORD_CONFIRM BOOTSTRAP_OWNER_PASSWORD BOOTSTRAP_OUTPUT
}
trap cleanup EXIT

trim() {
  local value="${1//$'\r'/}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

strip_optional_quotes() {
  local value="$1"
  if [[ ${#value} -ge 2 ]]; then
    local first="${value:0:1}"
    local last="${value: -1}"
    if { [[ "$first" == '"' && "$last" == '"' ]] || [[ "$first" == "'" && "$last" == "'" ]]; }; then
      value="${value:1:${#value}-2}"
    fi
  fi
  printf '%s' "$value"
}

file_mode() {
  local file="$1"
  if stat -c '%a' "$file" >/dev/null 2>&1; then
    stat -c '%a' "$file"
  else
    stat -f '%Lp' "$file"
  fi
}

require_file_600() {
  local file="$1"
  [[ -f "$file" ]] || die "Missing bootstrap env file: $file"
  [[ ! -L "$file" ]] || die "Refusing symlink bootstrap env file: $file"
  local mode
  mode="$(file_mode "$file")"
  [[ "$mode" == "600" ]] || die "Bootstrap env file must be chmod 600: $file"
}

assign_bootstrap_env_value() {
  local key="$1"
  local value="$2"
  case "$key" in
    BOOTSTRAP_ORGANIZATION_NAME)
      ORGANIZATION_NAME="$value"
      ;;
    BOOTSTRAP_STORE_NAME)
      STORE_NAME="$value"
      ;;
    BOOTSTRAP_OWNER_USERNAME)
      OWNER_USERNAME="$value"
      ;;
    BOOTSTRAP_OWNER_FULL_NAME)
      OWNER_FULL_NAME="$value"
      ;;
    BOOTSTRAP_OWNER_EMAIL)
      OWNER_EMAIL="$value"
      ;;
    BOOTSTRAP_OWNER_PHONE)
      OWNER_PHONE="$value"
      ;;
    BOOTSTRAP_OWNER_PASSWORD)
      die "BOOTSTRAP_OWNER_PASSWORD is not supported. Keep the password out of bootstrap-admin.env."
      ;;
    "")
      ;;
    *)
      ;;
  esac
}

load_bootstrap_env_file() {
  local file="$1"
  require_file_600 "$file"
  local raw line key value
  while IFS= read -r raw || [[ -n "$raw" ]]; do
    line="$(trim "$raw")"
    [[ -z "$line" || "${line:0:1}" == "#" ]] && continue
    [[ "$line" == *"="* ]] || die "Invalid bootstrap env line: $line"
    key="$(trim "${line%%=*}")"
    value="$(trim "${line#*=}")"
    value="$(strip_optional_quotes "$value")"
    assign_bootstrap_env_value "$key" "$value"
  done < "$file"
}

require_value() {
  local value="$1"
  local label="$2"
  [[ -n "$(trim "$value")" ]] || die "$label is required."
}

prompt_required() {
  local prompt="$1"
  local value=""
  while [[ -z "$value" ]]; do
    read -r -p "$prompt" value
    value="$(trim "$value")"
  done
  printf '%s' "$value"
}

validate_password_confirmation() {
  [[ -n "$OWNER_PASSWORD" ]] || die "Owner password is required."
  [[ "$OWNER_PASSWORD" == "$OWNER_PASSWORD_CONFIRM" ]] || die "Password confirmation does not match."
  unset OWNER_PASSWORD_CONFIRM
}

read_password_interactive() {
  read -r -s -p "Owner password: " OWNER_PASSWORD
  echo
  read -r -s -p "Confirm owner password: " OWNER_PASSWORD_CONFIRM
  echo
  validate_password_confirmation
}

read_password_from_stdin() {
  [[ ! -t 0 ]] || die "--password-stdin requires the password to be piped through stdin."
  IFS= read -r OWNER_PASSWORD || die "Failed to read owner password from stdin."
  [[ -n "$OWNER_PASSWORD" ]] || die "Owner password is required."
}

owner_contact_from_env_file() {
  if [[ -n "$(trim "$OWNER_PHONE")" ]]; then
    OWNER_CONTACT="$(trim "$OWNER_PHONE")"
  elif [[ -n "$(trim "$OWNER_EMAIL")" ]]; then
    OWNER_CONTACT="$(trim "$OWNER_EMAIL")"
  else
    OWNER_CONTACT=""
  fi
}

run_self_test() {
  local tmp_dir tmp_env
  tmp_dir="$(mktemp -d)"
  tmp_env="$tmp_dir/bootstrap-admin.env"
  {
    printf '%s\n' 'BOOTSTRAP_ORGANIZATION_NAME=Lanzhou Noodles'
    printf '%s\n' 'BOOTSTRAP_STORE_NAME=4483 R. Saint-Denis'
    printf '%s\n' 'BOOTSTRAP_OWNER_USERNAME=owner'
    printf '%s\n' 'BOOTSTRAP_OWNER_FULL_NAME=Chuwen Huang'
    printf '%s\n' 'BOOTSTRAP_OWNER_EMAIL='
    printf '%s\n' 'BOOTSTRAP_OWNER_PHONE=5145550100'
  } > "$tmp_env"
  chmod 600 "$tmp_env"

  load_bootstrap_env_file "$tmp_env"
  owner_contact_from_env_file
  [[ "$ORGANIZATION_NAME" == "Lanzhou Noodles" ]] || die "self-test organization parse failed"
  [[ "$STORE_NAME" == "4483 R. Saint-Denis" ]] || die "self-test store parse failed"
  [[ "$OWNER_USERNAME" == "owner" ]] || die "self-test username parse failed"
  [[ "$OWNER_FULL_NAME" == "Chuwen Huang" ]] || die "self-test full name parse failed"
  [[ "$OWNER_CONTACT" == "5145550100" ]] || die "self-test contact parse failed"

  if (OWNER_PASSWORD="a" OWNER_PASSWORD_CONFIRM="b" validate_password_confirmation) >/dev/null 2>&1; then
    die "self-test password mismatch failed"
  fi

  rm -rf "$tmp_dir"
  printf '%s\n' "bootstrap-admin.sh self-test success"
}

usage() {
  cat <<'USAGE'
Usage: ./bootstrap-admin.sh [--dry-run|--validate] [--env-file bootstrap-admin.env] [--password-stdin]

Creates the first production owner/admin account exactly once.

  --dry-run        Validate inputs and one-time database guard without writing data.
  --validate       Alias for --dry-run.
  --env-file FILE  Read organization/store/owner fields from a chmod 600 env file.
  --password-stdin Read the owner password once from stdin. Requires --env-file.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run|--validate)
      MODE="dry-run"
      ;;
    --env-file)
      [[ $# -ge 2 ]] || die "--env-file requires a file path."
      BOOTSTRAP_ENV_FILE="$2"
      shift
      ;;
    --password-stdin)
      PASSWORD_STDIN=true
      ;;
    --self-test)
      SELF_TEST=true
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

if [[ "$SELF_TEST" == true ]]; then
  run_self_test
  exit 0
fi

if [[ "$PASSWORD_STDIN" == true && -z "$BOOTSTRAP_ENV_FILE" ]]; then
  die "--password-stdin requires --env-file."
fi

if [[ ! -f "$DEPLOY_ENV_FILE" ]]; then
  die "Missing $DEPLOY_ENV_FILE. Copy .env.example to .env and deploy first."
fi

set -a
# shellcheck disable=SC1090
source "$DEPLOY_ENV_FILE"
set +a
unset BOOTSTRAP_OWNER_PASSWORD

compose() {
  "${SUDO[@]}" docker compose --env-file "$DEPLOY_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

if [[ -n "$BOOTSTRAP_ENV_FILE" ]]; then
  load_bootstrap_env_file "$BOOTSTRAP_ENV_FILE"
  owner_contact_from_env_file
  require_value "$ORGANIZATION_NAME" "BOOTSTRAP_ORGANIZATION_NAME"
  require_value "$STORE_NAME" "BOOTSTRAP_STORE_NAME"
  require_value "$OWNER_USERNAME" "BOOTSTRAP_OWNER_USERNAME"
  require_value "$OWNER_FULL_NAME" "BOOTSTRAP_OWNER_FULL_NAME"
else
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
fi

if [[ "$PASSWORD_STDIN" == true ]]; then
  read_password_from_stdin
else
  read_password_interactive
fi

if ! compose up -d db backend >/dev/null 2>&1; then
  die "Failed to start PostgreSQL/backend containers."
fi

set +e
BOOTSTRAP_OUTPUT="$(
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
  ' 2>&1
)"
BOOTSTRAP_STATUS=$?
set -e

if [[ "$BOOTSTRAP_STATUS" -ne 0 ]]; then
  printf '%s\n' "$BOOTSTRAP_OUTPUT" >&2
  exit "$BOOTSTRAP_STATUS"
fi

printf '%s\n' "$BOOTSTRAP_OUTPUT" | awk '/^(organization:|store:|username:|bootstrap )/'
