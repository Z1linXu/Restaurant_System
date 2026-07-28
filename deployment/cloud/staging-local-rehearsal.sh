#!/usr/bin/env bash
set -euo pipefail

# STG-003 is intentionally local-only. It never targets /srv, production data,
# or a remote Docker daemon. Real execution requires an explicit confirmation.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_RELATIVE_PATH="deployment/cloud/docker-compose.staging.yml"
PROJECT_NAME="restaurant-pos-staging"
LOCAL_PORT="18080"
MODE="plan"
COMMIT_SHA=""
LOCAL_ROOT="${TMPDIR:-/tmp}"
LOCAL_ROOT="${LOCAL_ROOT%/}/restaurant-pos/staging"
EVIDENCE_FILE=""
DOCKER_BIN=""

usage() {
  cat <<'EOF'
Usage:
  ./staging-local-rehearsal.sh --plan [--commit SHA] [--root ABSOLUTE_PATH]
  ./staging-local-rehearsal.sh --run --confirm-local-container-start [--commit SHA] [--root ABSOLUTE_PATH] [--evidence-file PATH]
  ./staging-local-rehearsal.sh --cleanup --confirm-local-container-start [--root ABSOLUTE_PATH]

Modes:
  --plan     Validate a clean local Git SHA and print the exact local-only
             Docker/Flyway rehearsal plan. It does not require Docker and does
             not create a worktree, configuration file, container, image, or data.
  --run      Create an isolated local release worktree and synthetic-only
             configuration, then run first/second startup evidence checks.
  --cleanup  Stop and remove only the exact local Compose project. Volumes and
             the PostgreSQL data directory are preserved; this command never uses
             `down -v`, Flyway clean, restore, or a production path.

Safety:
  * only a local Docker context named `default` is accepted;
  * /srv, /home/ubuntu, repository paths, remote Docker endpoints, and all
    non-loopback ports are rejected;
  * printing is hard-disabled and no printer endpoint is accepted;
  * --run and --cleanup require --confirm-local-container-start;
  * if Docker is unavailable, --run/--cleanup fail before creating any files.
EOF
}

die() {
  echo "staging local rehearsal: $*" >&2
  exit 1
}

canonical_dir() {
  (cd -P -- "$1" 2>/dev/null && pwd)
}

canonical_file_or_future() {
  local path="$1"
  # --plan must not create its future temporary root. Restrict the lexical
  # form instead of resolving a parent which may not exist yet.
  path="$(printf '%s' "$path" | tr -s '/')"
  [[ "$path" == /* && "$path" != *"/./"* && "$path" != *"/../"* && "$path" != *".." ]] || return 1
  printf '%s\n' "${path%/}"
}

is_local_root() {
  local root="$1"
  [[ "$root" == /*/restaurant-pos/staging ]] || return 1
  [[ "$root" != /srv/* && "$root" != /home/ubuntu/* && "$root" != "$REPOSITORY_ROOT"* ]] || return 1
  [[ "$root" != *"/deployment/cloud"* && "$root" != *"/data/postgres"* ]] || return 1
}

assert_clean_checkout() {
  git -C "$REPOSITORY_ROOT" diff --quiet || die "repository has tracked working-tree changes"
  git -C "$REPOSITORY_ROOT" diff --cached --quiet || die "repository has staged changes"
  [[ -z "$(git -C "$REPOSITORY_ROOT" status --porcelain=v1 --untracked-files=all)" ]] || die "repository must have no untracked files"
}

assert_commit() {
  [[ "$COMMIT_SHA" =~ ^[0-9a-f]{40}$ ]] || die "--commit must be a full lowercase 40-character Git SHA"
  git -C "$REPOSITORY_ROOT" cat-file -e "$COMMIT_SHA^{commit}" 2>/dev/null || die "requested commit does not exist locally"
  [[ "$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)" == "$COMMIT_SHA" ]] || die "requested commit must exactly match the clean local HEAD"
}

assert_local_docker() {
  DOCKER_BIN="$(command -v docker || true)"
  [[ "$DOCKER_BIN" == /* && -x "$DOCKER_BIN" ]] || die "BLOCKED_LOCAL_DOCKER_RUNTIME_UNAVAILABLE: Docker CLI is required for --run/--cleanup"
  [[ -z "${DOCKER_HOST+x}" && -z "${DOCKER_CONTEXT+x}" && -z "${DOCKER_CONFIG+x}" ]] || die "ambient Docker overrides are forbidden"
  "$DOCKER_BIN" context inspect default >/dev/null 2>&1 || die "Docker context 'default' is unavailable"
  local endpoint
  endpoint="$($DOCKER_BIN context inspect default --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
  case "$endpoint" in
    unix://*|npipe://*) ;;
    *) die "Docker context 'default' must use a local unix or npipe endpoint" ;;
  esac
}

local_compose() {
  env -i \
    PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
    HOME="${HOME:-/tmp}" \
    "$DOCKER_BIN" --context default compose \
    --project-name "$PROJECT_NAME" \
    --env-file "$1" \
    -f "$2" "${@:3}"
}

random_hex() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$1"
  else
    # This fallback is only a local non-production guard. It never writes to Git.
    od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'
  fi
}

write_synthetic_env() {
  local file="$1" root="$2" sha="$3"
  umask 077
  cat >"$file" <<EOF
COMPOSE_PROJECT_NAME=$PROJECT_NAME
STAGING_ROOT=$root
STAGING_COMMIT_SHA=$sha
STAGING_POSTGRES_DATA_DIR=$root/state/postgres
HTTP_BIND_ADDRESS=127.0.0.1
HTTP_PORT=$LOCAL_PORT
NGINX_SERVER_NAME=localhost
TZ=America/Toronto
POSTGRES_IMAGE_TAG=16-alpine
DB_NAME=restaurant_pos_staging_local
DB_USER=restaurant_pos_staging_local
DB_PASSWORD=$(random_hex 24)
JWT_SECRET=$(random_hex 48)
SPRING_PROFILES_ACTIVE=cloud
JAVA_OPTS="-Xms128m -Xmx512m"
BACKEND_IMAGE=restaurant-pos-backend:staging-$sha
FRONTEND_IMAGE=restaurant-pos-frontend:staging-$sha
VITE_APP_BUILD_VERSION=staging-$sha
STAGING_PRINT_MODE=DISABLED
STAGING_PRINTING_FEATURE_ENABLED=false
STAGING_DB_CPU_LIMIT=0.75
STAGING_DB_MEMORY_LIMIT=512m
STAGING_BACKEND_CPU_LIMIT=1.00
STAGING_BACKEND_MEMORY_LIMIT=768m
STAGING_NGINX_CPU_LIMIT=0.25
STAGING_NGINX_MEMORY_LIMIT=128m
STAGING_LOG_MAX_SIZE=10m
STAGING_LOG_MAX_FILE=3
EOF
  chmod 600 "$file"
}

assert_generated_env() {
  local file="$1" root="$2" sha="$3"
  [[ "$(stat -f '%Lp' "$file" 2>/dev/null || stat -c '%a' "$file")" == "600" ]] || die "synthetic environment file must be mode 0600"
  grep -Fxq "COMPOSE_PROJECT_NAME=$PROJECT_NAME" "$file" || die "unexpected Compose project"
  grep -Fxq "STAGING_ROOT=$root" "$file" || die "unexpected local root"
  grep -Fxq "STAGING_COMMIT_SHA=$sha" "$file" || die "unexpected commit"
  grep -Fxq "HTTP_BIND_ADDRESS=127.0.0.1" "$file" || die "staging must bind loopback only"
  grep -Fxq "HTTP_PORT=$LOCAL_PORT" "$file" || die "unexpected staging port"
  grep -Fxq "STAGING_PRINT_MODE=DISABLED" "$file" || die "printing must be disabled"
  grep -Fxq "STAGING_PRINTING_FEATURE_ENABLED=false" "$file" || die "printing feature must be false"
  ! grep -Eq 'PRINTER|PAD_DIRECT|REAL' "$file" || die "synthetic configuration contains forbidden printing settings"
}

write_evidence_header() {
  local file="$1" sha="$2"
  mkdir -p "$(dirname -- "$file")"
  umask 077
  cat >"$file" <<EOF
# STG-003 Local Rehearsal Evidence

Status: LOCAL_RUN_IN_PROGRESS
Commit: $sha
Compose project: $PROJECT_NAME
Printing: DISABLED
Synthetic data only: yes

EOF
  chmod 600 "$file"
}

append_evidence() {
  local file="$1" title="$2"
  shift 2
  {
    printf '\n## %s\n\n```text\n' "$title"
    "$@"
    printf '\n```\n'
  } >>"$file"
}

wait_for_http() {
  local url="$1" label="$2" attempt code
  for attempt in $(seq 1 30); do
    code="$(curl --silent --show-error --max-time 3 --output /dev/null --write-out '%{http_code}' "$url" || true)"
    [[ "$code" == "200" ]] && return 0
    sleep 2
  done
  die "$label did not return HTTP 200"
}

run_rehearsal() {
  local release_dir="$LOCAL_ROOT/releases/$COMMIT_SHA"
  local config_dir="$LOCAL_ROOT/config"
  local state_dir="$LOCAL_ROOT/state"
  local env_file="$config_dir/.env.staging"
  local compose_file="$release_dir/$COMPOSE_RELATIVE_PATH"
  local local_evidence="$EVIDENCE_FILE"

  [[ ! -e "$release_dir" ]] || die "release directory already exists; choose a new root or clean up only after owner approval"
  [[ ! -e "$config_dir" && ! -e "$state_dir" ]] || die "local root already has configuration or state; refusing to overwrite it"
  mkdir -p "$LOCAL_ROOT/releases" "$config_dir" "$state_dir/postgres"
  chmod 700 "$config_dir" "$state_dir"
  git -C "$REPOSITORY_ROOT" worktree add --detach "$release_dir" "$COMMIT_SHA" >/dev/null
  [[ "$(git -C "$release_dir" rev-parse HEAD)" == "$COMMIT_SHA" ]] || die "local rehearsal worktree does not match requested SHA"
  [[ -z "$(git -C "$release_dir" status --porcelain=v1 --untracked-files=all)" ]] || die "local rehearsal worktree is not clean"
  [[ -f "$compose_file" ]] || die "standalone STG-002 Compose file is missing from the pinned release"

  write_synthetic_env "$env_file" "$LOCAL_ROOT" "$COMMIT_SHA"
  assert_generated_env "$env_file" "$LOCAL_ROOT" "$COMMIT_SHA"
  [[ -n "$local_evidence" ]] || local_evidence="$LOCAL_ROOT/evidence/stg-003-local-rehearsal.md"
  write_evidence_header "$local_evidence" "$COMMIT_SHA"

  local_compose "$env_file" "$compose_file" config --services | grep -Fxq 'db' || die "Compose plan is missing db"
  local_compose "$env_file" "$compose_file" config --services | grep -Fxq 'backend' || die "Compose plan is missing backend"
  local_compose "$env_file" "$compose_file" config --services | grep -Fxq 'nginx' || die "Compose plan is missing nginx"
  append_evidence "$local_evidence" "Compose services" local_compose "$env_file" "$compose_file" config --services

  local_compose "$env_file" "$compose_file" build backend nginx
  local_compose "$env_file" "$compose_file" up -d
  append_evidence "$local_evidence" "First startup status" local_compose "$env_file" "$compose_file" ps
  wait_for_http "http://127.0.0.1:$LOCAL_PORT/api/v1/system/health" "backend health"
  wait_for_http "http://127.0.0.1:$LOCAL_PORT/" "nginx frontend"
  append_evidence "$local_evidence" "Flyway history after first startup" \
    local_compose "$env_file" "$compose_file" exec -T db psql --no-psqlrc --tuples-only --no-align --field-separator='|' -U restaurant_pos_staging_local -d restaurant_pos_staging_local -c "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank"

  # A second exact-project start verifies restart/persistence without deleting data.
  local_compose "$env_file" "$compose_file" stop
  local_compose "$env_file" "$compose_file" up -d
  wait_for_http "http://127.0.0.1:$LOCAL_PORT/api/v1/system/health" "backend health after second startup"
  append_evidence "$local_evidence" "Second startup status" local_compose "$env_file" "$compose_file" ps
  append_evidence "$local_evidence" "Flyway history after second startup" \
    local_compose "$env_file" "$compose_file" exec -T db psql --no-psqlrc --tuples-only --no-align --field-separator='|' -U restaurant_pos_staging_local -d restaurant_pos_staging_local -c "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank"
  append_evidence "$local_evidence" "HTTP checks" curl --silent --show-error --max-time 5 --output /dev/null --write-out 'health=%{http_code}\n' "http://127.0.0.1:$LOCAL_PORT/api/v1/system/health"
  append_evidence "$local_evidence" "WebSocket HTTP entry only" curl --silent --show-error --max-time 5 --output /dev/null --write-out 'ws=%{http_code}\n' "http://127.0.0.1:$LOCAL_PORT/ws"
  sed -i.bak 's/LOCAL_RUN_IN_PROGRESS/LOCAL_RUN_COMPLETE/' "$local_evidence" && rm -f "$local_evidence.bak"
  echo "Local rehearsal completed. Data is preserved at $LOCAL_ROOT/state/postgres. Evidence: $local_evidence"
}

cleanup_local_project() {
  local env_file="$LOCAL_ROOT/config/.env.staging"
  local release_dir="$LOCAL_ROOT/releases/$COMMIT_SHA"
  local compose_file="$release_dir/$COMPOSE_RELATIVE_PATH"
  [[ -f "$env_file" && -f "$compose_file" ]] || die "cleanup requires the exact local root created by a rehearsal"
  assert_generated_env "$env_file" "$LOCAL_ROOT" "$COMMIT_SHA"
  local_compose "$env_file" "$compose_file" down
  echo "Stopped and removed only $PROJECT_NAME containers and network. Volumes and $LOCAL_ROOT/state/postgres were preserved."
}

print_plan() {
  cat <<EOF
STG-003 LOCAL-ONLY REHEARSAL PLAN
commit=$COMMIT_SHA
local_root=$LOCAL_ROOT
compose_project=$PROJECT_NAME
port=127.0.0.1:$LOCAL_PORT
printing=DISABLED
data=$LOCAL_ROOT/state/postgres

Planned --run sequence (requires Docker and --confirm-local-container-start):
1. Create a detached clean Git worktree at $LOCAL_ROOT/releases/$COMMIT_SHA.
2. Generate a mode-0600 synthetic-only environment file at $LOCAL_ROOT/config/.env.staging.
3. Validate exactly db, backend, nginx with the standalone STG-002 Compose file.
4. Build backend/nginx and start only $PROJECT_NAME on loopback port $LOCAL_PORT.
5. Collect local Flyway V1-V8 history, HTTP health, nginx, websocket HTTP-entry, and status evidence.
6. Stop then start only $PROJECT_NAME once more; re-check Flyway history and persistence.

Not planned: remote Docker, /srv, production paths/data, real accounts, printer endpoints,
PAD_DIRECT, REAL/MOCK printing, Flyway clean, restore, down -v, or production deployment.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --plan) MODE="plan" ;;
    --run) MODE="run" ;;
    --cleanup) MODE="cleanup" ;;
    --confirm-local-container-start) CONFIRMED="true" ;;
    --commit) [[ $# -ge 2 ]] || die "--commit needs a SHA"; COMMIT_SHA="$2"; shift ;;
    --root) [[ $# -ge 2 ]] || die "--root needs an absolute path"; LOCAL_ROOT="$2"; shift ;;
    --evidence-file) [[ $# -ge 2 ]] || die "--evidence-file needs an absolute path"; EVIDENCE_FILE="$2"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unsupported option: $1" ;;
  esac
  shift
done

COMMIT_SHA="${COMMIT_SHA:-$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)}"
assert_clean_checkout
assert_commit
LOCAL_ROOT="$(canonical_file_or_future "$LOCAL_ROOT")" || die "cannot canonicalize local root parent"
is_local_root "$LOCAL_ROOT" || die "local root must be a non-production absolute path ending in /restaurant-pos/staging"
[[ -z "$EVIDENCE_FILE" || "$EVIDENCE_FILE" == "$LOCAL_ROOT"/* ]] || die "evidence file must remain under the local rehearsal root"

case "$MODE" in
  plan)
    if command -v docker >/dev/null 2>&1; then
      # This is an inspection-only context guard. --plan never calls Compose.
      assert_local_docker
      echo "docker_context=default (local endpoint verified)"
    else
      echo "docker_context=unavailable (runtime rehearsal remains blocked)"
    fi
    print_plan
    ;;
  run)
    [[ "${CONFIRMED:-false}" == "true" ]] || die "--run requires --confirm-local-container-start"
    assert_local_docker
    run_rehearsal
    ;;
  cleanup)
    [[ "${CONFIRMED:-false}" == "true" ]] || die "--cleanup requires --confirm-local-container-start"
    assert_local_docker
    cleanup_local_project
    ;;
esac
