#!/usr/bin/env bash
set -euo pipefail

# STG-003 is intentionally local-only. This runner never accepts a server
# root, a remote Docker endpoint, production data, or a print-capable setting.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_RELATIVE_PATH="deployment/cloud/docker-compose.staging.yml"
PROJECT_NAME="restaurant-pos-staging"
LOCAL_PORT="18080"
EVIDENCE_RELATIVE_PATH="evidence/stg-003-local-rehearsal.md"
MODE="plan"
COMMIT_SHA=""
COMMIT_WAS_EXPLICIT="false"
LOCAL_TMP_BASE="$(cd -P -- "${TMPDIR:-/tmp}" && pwd)"
LOCAL_ROOT="$LOCAL_TMP_BASE"
LOCAL_ROOT="${LOCAL_ROOT%/}/restaurant-pos/staging"
DOCKER_BIN=""

usage() {
  cat <<'EOF'
Usage:
  ./staging-local-rehearsal.sh --plan [--commit SHA] [--root ABSOLUTE_PATH]
  ./staging-local-rehearsal.sh --run --confirm-local-container-start [--commit SHA] [--root ABSOLUTE_PATH]
  ./staging-local-rehearsal.sh --cleanup --confirm-local-container-start [--commit SHA] [--root ABSOLUTE_PATH]

--plan is pure validation/command planning: it does not inspect Docker or
create files. --run and --cleanup require an explicit confirmation and a local
Docker context named default. Cleanup never removes volumes or data.
EOF
}

die() { echo "staging local rehearsal: $*" >&2; exit 1; }

file_mode() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then stat -c '%a' "$1"; else stat -f '%Lp' "$1"; fi
}

normalize_absolute_path() {
  local path="$1"
  path="$(printf '%s' "$path" | tr -s '/')"
  [[ "$path" == /* && "$path" != *$'\n'* && "$path" != *'/./'* && "$path" != *'/../'* && "$path" != *'..' ]] || return 1
  printf '%s\n' "${path%/}"
}

# Resolve an existing directory only after proving every component is not a
# symlink. This prevents a safe-looking lexical path from resolving into /srv.
canonical_existing_dir_no_symlink() {
  local path="$1" current="/" component old_ifs="$IFS"
  [[ -d "$path" ]] || return 1
  IFS='/'
  # shellcheck disable=SC2086
  set -- $path
  IFS="$old_ifs"
  for component in "$@"; do
    [[ -z "$component" ]] && continue
    current="$current$component"
    [[ ! -L "$current" ]] || return 1
    [[ -d "$current" ]] || return 1
    current="$current/"
  done
  (cd -P -- "$path" && pwd)
}

# A future root cannot be fully canonicalized. Resolve its nearest existing
# ancestor without symlinks, then append only new lexical components. The root
# is checked again immediately after creation.
canonical_future_dir_no_symlink() {
  local requested normalized probe part old_ifs="$IFS"
  local -a suffix=()
  requested="$(normalize_absolute_path "$1")" || return 1
  probe="$requested"
  while [[ ! -e "$probe" ]]; do
    part="$(basename -- "$probe")"
    if [[ ${#suffix[@]} -eq 0 ]]; then
      suffix=("$part")
    else
      suffix=("$part" "${suffix[@]}")
    fi
    probe="$(dirname -- "$probe")"
  done
  [[ -d "$probe" ]] || return 1
  probe="$(canonical_existing_dir_no_symlink "$probe")" || return 1
  for part in "${suffix[@]}"; do
    [[ -n "$part" && "$part" != . && "$part" != .. ]] || return 1
    probe="$probe/$part"
  done
  printf '%s\n' "$probe"
}

is_nonproduction_root() {
  local root="$1"
  [[ "$root" == /*/restaurant-pos/staging ]] || return 1
  [[ "$root" != /srv/* && "$root" != /home/ubuntu/* && "$root" != "$REPOSITORY_ROOT"* ]] || return 1
  [[ "$root" != *'/deployment/cloud'* && "$root" != *'/data/postgres'* ]] || return 1
}

resolve_local_root() {
  LOCAL_ROOT="$(canonical_future_dir_no_symlink "$LOCAL_ROOT")" || die "local root contains traversal, a symlink, or an invalid ancestor"
  is_nonproduction_root "$LOCAL_ROOT" || die "local root must canonically be a non-production absolute path ending in /restaurant-pos/staging"
}

revalidate_created_root() {
  local resolved
  resolved="$(canonical_existing_dir_no_symlink "$LOCAL_ROOT")" || die "local root must exist without symlink traversal"
  [[ "$resolved" == "$LOCAL_ROOT" ]] || die "local root canonical target changed after creation"
  is_nonproduction_root "$resolved" || die "local root resolved to a forbidden target"
}

assert_clean_checkout() {
  git -C "$REPOSITORY_ROOT" diff --quiet || die "repository has tracked working-tree changes"
  git -C "$REPOSITORY_ROOT" diff --cached --quiet || die "repository has staged changes"
  [[ -z "$(git -C "$REPOSITORY_ROOT" status --porcelain=v1 --untracked-files=all)" ]] || die "repository must have no untracked files"
}

assert_commit_at_clean_head() {
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
  case "$endpoint" in unix://*|npipe://*) ;; *) die "Docker context 'default' must use a local unix or npipe endpoint" ;; esac
}

local_compose() {
  local env_file="$1" compose_file="$2"
  shift 2
  env -i PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" HOME="${HOME:-/tmp}" \
    "$DOCKER_BIN" --context default compose --project-name "$PROJECT_NAME" \
    --env-file "$env_file" -f "$compose_file" "$@"
}

random_hex() {
  if command -v openssl >/dev/null 2>&1; then openssl rand -hex "$1"; else od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'; fi
}

write_synthetic_env() {
  local file="$1"
  umask 077
  cat >"$file" <<EOF
COMPOSE_PROJECT_NAME=$PROJECT_NAME
STAGING_ROOT=$LOCAL_ROOT
STAGING_COMMIT_SHA=$COMMIT_SHA
STAGING_POSTGRES_DATA_DIR=$LOCAL_ROOT/state/postgres
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
BACKEND_IMAGE=restaurant-pos-backend:staging-$COMMIT_SHA
FRONTEND_IMAGE=restaurant-pos-frontend:staging-$COMMIT_SHA
VITE_APP_BUILD_VERSION=staging-$COMMIT_SHA
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

assert_env_identity() {
  local file="$1" expected="$LOCAL_ROOT/config/.env.staging" canonical_file canonical_parent
  [[ -f "$file" && ! -L "$file" ]] || die "synthetic environment file is missing or a symlink"
  canonical_parent="$(canonical_existing_dir_no_symlink "$(dirname -- "$file")")" || die "environment parent has symlink traversal"
  canonical_file="$canonical_parent/$(basename -- "$file")"
  [[ "$canonical_file" == "$expected" ]] || die "environment file is outside the exact local config path"
  [[ "$(file_mode "$file")" == "600" ]] || die "synthetic environment file must be mode 0600"
  grep -Fxq "COMPOSE_PROJECT_NAME=$PROJECT_NAME" "$file" || die "unexpected Compose project"
  grep -Fxq "STAGING_ROOT=$LOCAL_ROOT" "$file" || die "unexpected local root"
  grep -Fxq "STAGING_COMMIT_SHA=$COMMIT_SHA" "$file" || die "unexpected commit"
  grep -Fxq "STAGING_POSTGRES_DATA_DIR=$LOCAL_ROOT/state/postgres" "$file" || die "unexpected PostgreSQL path"
  grep -Fxq 'HTTP_BIND_ADDRESS=127.0.0.1' "$file" || die "staging must bind loopback only"
  grep -Fxq "HTTP_PORT=$LOCAL_PORT" "$file" || die "unexpected staging port"
  grep -Fxq 'SPRING_PROFILES_ACTIVE=cloud' "$file" || die "unexpected Spring profile"
  grep -Fxq 'STAGING_PRINT_MODE=DISABLED' "$file" || die "printing must be disabled"
  grep -Fxq 'STAGING_PRINTING_FEATURE_ENABLED=false' "$file" || die "printing feature must be false"
  ! grep -Eq 'PRINTER|PAD_DIRECT|REAL|MOCK' "$file" || die "synthetic configuration contains forbidden printing settings"
}

assert_release_identity() {
  local release_dir="$LOCAL_ROOT/releases/$COMMIT_SHA" compose_file="$release_dir/$COMPOSE_RELATIVE_PATH" canonical_release canonical_compose_parent
  canonical_release="$(canonical_existing_dir_no_symlink "$release_dir")" || die "release directory is missing or traverses a symlink"
  [[ "$canonical_release" == "$release_dir" ]] || die "release directory canonical target changed"
  [[ "$(git -C "$release_dir" rev-parse HEAD 2>/dev/null || true)" == "$COMMIT_SHA" ]] || die "release Git HEAD does not match the requested commit"
  git -C "$release_dir" diff --quiet || die "release has tracked working-tree changes"
  git -C "$release_dir" diff --cached --quiet || die "release has staged changes"
  [[ -z "$(git -C "$release_dir" status --porcelain=v1 --untracked-files=all)" ]] || die "release must have no untracked files"
  [[ -f "$compose_file" && ! -L "$compose_file" ]] || die "standalone Compose file is missing or a symlink"
  canonical_compose_parent="$(canonical_existing_dir_no_symlink "$(dirname -- "$compose_file")")" || die "Compose parent traverses a symlink"
  [[ "$canonical_compose_parent/$(basename -- "$compose_file")" == "$compose_file" ]] || die "Compose file is outside the pinned release"
}

prepare_evidence_file() {
  local evidence_dir="$LOCAL_ROOT/evidence" evidence_file="$LOCAL_ROOT/$EVIDENCE_RELATIVE_PATH" canonical_evidence_dir
  mkdir -p "$evidence_dir"
  [[ ! -L "$evidence_dir" ]] || die "evidence directory must not be a symlink"
  canonical_evidence_dir="$(canonical_existing_dir_no_symlink "$evidence_dir")" || die "evidence directory has symlink traversal"
  [[ "$canonical_evidence_dir" == "$evidence_dir" ]] || die "evidence directory canonical target changed"
  [[ ! -e "$evidence_file" ]] || die "evidence file already exists; refusing to overwrite it"
  umask 077
  cat >"$evidence_file" <<EOF
# STG-003 Local Rehearsal Evidence

Status: LOCAL_RUN_IN_PROGRESS
Commit: $COMMIT_SHA
Compose project: $PROJECT_NAME
Printing: DISABLED
Synthetic data only: yes

EOF
  chmod 600 "$evidence_file"
  printf '%s\n' "$evidence_file"
}

append_evidence() {
  local file="$1" title="$2"
  shift 2
  { printf '\n## %s\n\n```text\n' "$title"; "$@"; printf '\n```\n'; } >>"$file"
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

assert_resolved_compose() {
  local env_file="$1" compose_file="$2" resolved services source_count
  revalidate_created_root
  assert_release_identity
  assert_env_identity "$env_file"
  resolved="$LOCAL_ROOT/evidence/resolved-compose.private.yml"
  [[ ! -e "$resolved" ]] || die "private resolved Compose file already exists"
  umask 077
  local_compose "$env_file" "$compose_file" config >"$resolved" || die "Compose config validation failed"
  chmod 600 "$resolved"
  services="$(local_compose "$env_file" "$compose_file" config --services)" || die "Compose services validation failed"
  [[ "$services" == $'db\nbackend\nnginx' ]] || die "resolved Compose services must exactly be db, backend, nginx"
  grep -Fq "postgres:16-alpine" "$resolved" || die "resolved Compose PostgreSQL image differs"
  grep -Fq "restaurant-pos-backend:staging-$COMMIT_SHA" "$resolved" || die "resolved backend image differs from SHA"
  grep -Fq "restaurant-pos-frontend:staging-$COMMIT_SHA" "$resolved" || die "resolved frontend image differs from SHA"
  grep -Fq "$LOCAL_ROOT/state/postgres" "$resolved" || die "resolved PostgreSQL bind source differs"
  grep -Fq 'target: /var/lib/postgresql/data' "$resolved" || die "resolved PostgreSQL bind target differs"
  grep -Fq 'nginx.http.conf.template' "$resolved" || die "resolved Nginx template mount differs"
  grep -Fq 'target: /etc/nginx/templates/default.conf.template' "$resolved" || die "resolved Nginx template target differs"
  grep -Eq '(127\.0\.0\.1:18080:80|host_ip: 127\.0\.0\.1)' "$resolved" || die "resolved Compose lacks loopback port 18080"
  grep -Fq 'SPRING_PROFILES_ACTIVE: cloud' "$resolved" || die "resolved Spring profile differs"
  grep -Eq "APP_FEATURES_PRINTING: [\\\"']?false" "$resolved" || die "resolved printing feature differs"
  grep -Fq 'STAGING_PRINT_MODE=DISABLED' "$env_file" || die "printing mode must remain disabled"
  for value in 0.75 512m 1.00 768m 0.25 128m 10m 3; do grep -Fq "$value" "$resolved" || die "resolved Compose misses bounded resource/log value $value"; done
  source_count="$(grep -Ec '^[[:space:]]*source:' "$resolved" || true)"
  [[ "$source_count" -eq 2 ]] || die "resolved Compose has unexpected mounts"
  ! grep -Eqi 'docker\.sock|privileged:[[:space:]]*true|network_mode:[[:space:]]*host|pid:[[:space:]]*host|:80:80|:443:443|0\.0\.0\.0|/srv/|/home/ubuntu/' "$resolved" || die "resolved Compose contains forbidden privileged, host, socket, or server configuration"
  rm -f "$resolved"
  revalidate_created_root
  assert_release_identity
  assert_env_identity "$env_file"
}

run_rehearsal() {
  local release_dir="$LOCAL_ROOT/releases/$COMMIT_SHA" config_dir="$LOCAL_ROOT/config" state_dir="$LOCAL_ROOT/state"
  local env_file="$config_dir/.env.staging" compose_file="$release_dir/$COMPOSE_RELATIVE_PATH" evidence_file
  [[ ! -e "$release_dir" && ! -e "$config_dir" && ! -e "$state_dir" ]] || die "local root already contains release/config/state; refusing to overwrite it"
  mkdir -p "$LOCAL_ROOT/releases" "$config_dir" "$state_dir/postgres"
  chmod 700 "$config_dir" "$state_dir"
  revalidate_created_root
  git -C "$REPOSITORY_ROOT" worktree add --detach "$release_dir" "$COMMIT_SHA" >/dev/null
  assert_release_identity
  write_synthetic_env "$env_file"
  assert_env_identity "$env_file"
  evidence_file="$(prepare_evidence_file)"
  assert_resolved_compose "$env_file" "$compose_file"
  append_evidence "$evidence_file" "Compose services" local_compose "$env_file" "$compose_file" config --services
  # Revalidate all sensitive identities immediately before build and up.
  assert_resolved_compose "$env_file" "$compose_file"
  local_compose "$env_file" "$compose_file" build backend nginx
  revalidate_created_root; assert_release_identity; assert_env_identity "$env_file"; assert_resolved_compose "$env_file" "$compose_file"
  local_compose "$env_file" "$compose_file" up -d
  append_evidence "$evidence_file" "First startup status" local_compose "$env_file" "$compose_file" ps
  wait_for_http "http://127.0.0.1:$LOCAL_PORT/api/v1/system/health" "backend health"
  wait_for_http "http://127.0.0.1:$LOCAL_PORT/" "nginx frontend"
  append_evidence "$evidence_file" "Flyway history after first startup" local_compose "$env_file" "$compose_file" exec -T db psql --no-psqlrc --tuples-only --no-align --field-separator='|' -U restaurant_pos_staging_local -d restaurant_pos_staging_local -c "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank"
  local_compose "$env_file" "$compose_file" stop
  revalidate_created_root; assert_release_identity; assert_env_identity "$env_file"; assert_resolved_compose "$env_file" "$compose_file"
  local_compose "$env_file" "$compose_file" up -d
  wait_for_http "http://127.0.0.1:$LOCAL_PORT/api/v1/system/health" "backend health after second startup"
  append_evidence "$evidence_file" "Second startup status" local_compose "$env_file" "$compose_file" ps
  append_evidence "$evidence_file" "Flyway history after second startup" local_compose "$env_file" "$compose_file" exec -T db psql --no-psqlrc --tuples-only --no-align --field-separator='|' -U restaurant_pos_staging_local -d restaurant_pos_staging_local -c "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank"
  append_evidence "$evidence_file" "HTTP checks" curl --silent --show-error --max-time 5 --output /dev/null --write-out 'health=%{http_code}\n' "http://127.0.0.1:$LOCAL_PORT/api/v1/system/health"
  append_evidence "$evidence_file" "WebSocket HTTP entry only" curl --silent --show-error --max-time 5 --output /dev/null --write-out 'ws=%{http_code}\n' "http://127.0.0.1:$LOCAL_PORT/ws"
  sed -i.bak 's/LOCAL_RUN_IN_PROGRESS/LOCAL_RUN_COMPLETE/' "$evidence_file" && rm -f "$evidence_file.bak"
  echo "Local rehearsal completed. Data is preserved at $LOCAL_ROOT/state/postgres. Evidence: $evidence_file"
}

cleanup_local_project() {
  local env_file="$LOCAL_ROOT/config/.env.staging" release_dir="$LOCAL_ROOT/releases/$COMMIT_SHA" compose_file="$release_dir/$COMPOSE_RELATIVE_PATH"
  revalidate_created_root
  assert_release_identity
  assert_env_identity "$env_file"
  assert_resolved_compose "$env_file" "$compose_file"
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
evidence=$LOCAL_ROOT/$EVIDENCE_RELATIVE_PATH

Planned --run sequence (requires Docker and --confirm-local-container-start):
1. Create a detached clean Git worktree under the local root.
2. Generate a mode-0600 synthetic-only environment file.
3. Privately validate exact Compose services, SHA images, loopback port, mounts, profile, print safety, and limits.
4. Build/start only $PROJECT_NAME, verify Flyway/backend/nginx, then stop/start it once without deleting data.

Not planned: Docker inspection in --plan, remote Docker, /srv, production paths/data,
real accounts/endpoints, PAD_DIRECT, REAL/MOCK printing, Flyway clean, restore, or down -v.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --plan) MODE="plan" ;;
    --run) MODE="run" ;;
    --cleanup) MODE="cleanup" ;;
    --confirm-local-container-start) CONFIRMED="true" ;;
    --commit) [[ $# -ge 2 ]] || die "--commit needs a SHA"; COMMIT_SHA="$2"; COMMIT_WAS_EXPLICIT="true"; shift ;;
    --root) [[ $# -ge 2 ]] || die "--root needs an absolute path"; LOCAL_ROOT="$2"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) die "unsupported option: $1" ;;
  esac
  shift
done

resolve_local_root
if [[ "$MODE" == "cleanup" && "$COMMIT_WAS_EXPLICIT" == "false" ]]; then
  local_releases=()
  while IFS= read -r local_release; do local_releases+=("$local_release"); done < <(find "$LOCAL_ROOT/releases" -mindepth 1 -maxdepth 1 -type d -name '[0-9a-f]*' -print 2>/dev/null || true)
  [[ ${#local_releases[@]} -eq 1 ]] || die "cleanup requires exactly one local rehearsal release or an explicit --commit"
  COMMIT_SHA="$(basename -- "${local_releases[0]}")"
else
  COMMIT_SHA="${COMMIT_SHA:-$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)}"
  assert_clean_checkout
  assert_commit_at_clean_head
fi
[[ "$COMMIT_SHA" =~ ^[0-9a-f]{40}$ ]] || die "local rehearsal commit must be a full lowercase 40-character Git SHA"

case "$MODE" in
  plan) print_plan ;;
  run) [[ "${CONFIRMED:-false}" == "true" ]] || die "--run requires --confirm-local-container-start"; assert_local_docker; run_rehearsal ;;
  cleanup) [[ "${CONFIRMED:-false}" == "true" ]] || die "--cleanup requires --confirm-local-container-start"; assert_local_docker; cleanup_local_project ;;
esac
