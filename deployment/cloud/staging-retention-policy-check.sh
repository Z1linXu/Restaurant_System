#!/usr/bin/env bash
set -Eeuo pipefail

# Read-only policy check for the fixed Staging Compose/Nginx retention contract.
# Host journald is deliberately policy-only: this helper never edits journald
# configuration and never runs journal vacuum.

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=staging-hygiene-common.sh
source "$SCRIPT_DIR/staging-hygiene-common.sh"

ACTION="validate"
ENV_FILE=""

usage() {
  cat <<'EOF'
Staging container/Nginx/journald retention policy check (read-only).

Usage:
  staging-retention-policy-check.sh --dry-run \
    --env-file /srv/restaurant-pos/staging/config/.env.staging

The check requires the standalone Staging Compose template to retain each
service with Docker's local log driver at the existing 10m x 3 ceiling. Nginx
timing logs are emitted to Docker stdout and contain status/size/timing only.
The journald recommendation is reported as policy evidence; this command never
reads or changes host journald state and never vacuums logs.
EOF
}

service_has_local_retention() {
  local service="$1" compose_file="$2"
  awk -v wanted="$service" '
    $0 == "  " wanted ":" { in_service=1; next }
    in_service && /^  [^ ]/ { in_service=0 }
    in_service && $0 ~ /^      driver: local$/ { driver=1 }
    in_service && $0 ~ /^        max-size: \$\{STAGING_LOG_MAX_SIZE:-10m\}$/ { size=1 }
    in_service && $0 ~ /^        max-file: "\$\{STAGING_LOG_MAX_FILE:-3\}"$/ { files=1 }
    END { exit !(driver && size && files) }
  ' "$compose_file"
}

timing_log_is_sanitized() {
  local nginx_file="$1" format_line
  format_line="$(grep -E '^log_format staging_timing ' "$nginx_file" || true)"
  [[ -n "$format_line" ]] || return 1
  [[ "$format_line" == *'request_time=$request_time'* ]] || return 1
  [[ "$format_line" == *'method=$request_method'* ]] || return 1
  [[ "$format_line" == *'uri=$uri'* ]] || return 1
  [[ "$format_line" != *'$request_uri'* ]] || return 1
  [[ "$format_line" != *'$args'* ]] || return 1
  [[ "$format_line" == *'upstream_connect_time=$upstream_connect_time'* ]] || return 1
  [[ "$format_line" == *'upstream_header_time=$upstream_header_time'* ]] || return 1
  [[ "$format_line" == *'upstream_response_time=$upstream_response_time'* ]] || return 1
  ! grep -Eq '\$request([^_A-Za-z]|$)|\$request_uri|\$args' <<<"$format_line" || return 1
  [[ "$format_line" != *'$http_'* && "$format_line" != *'$cookie_'* && "$format_line" != *'$authorization'* ]] || return 1
  grep -Fq 'access_log /dev/stdout staging_timing;' "$nginx_file" || return 1
  grep -Fq 'error_log /dev/stderr warn;' "$nginx_file" || return 1
}

main() {
  local compose_file nginx_file service
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate|--dry-run)
        ACTION="validate"
        ;;
      --env-file)
        [[ $# -ge 2 && -z "$ENV_FILE" ]] || hygiene_usage_error "--env-file requires one value and may appear once"
        ENV_FILE="$2"
        shift
        ;;
      --help|-h) usage; exit 0 ;;
      *) hygiene_usage_error "unsupported option: $1" ;;
    esac
    shift
  done
  [[ "$ACTION" == "validate" ]] || hygiene_usage_error "unsupported action"
  [[ -n "$ENV_FILE" ]] || hygiene_usage_error "--env-file is required"
  hygiene_validate_env_and_scope "$ENV_FILE"
  compose_file="$SCRIPT_DIR/docker-compose.staging.yml"
  nginx_file="$SCRIPT_DIR/nginx.http.conf.template"
  [[ -f "$compose_file" && ! -L "$compose_file" ]] || hygiene_die "standalone Staging Compose template is unavailable"
  [[ -f "$nginx_file" && ! -L "$nginx_file" ]] || hygiene_die "Staging Nginx template is unavailable"

  for service in db backend nginx; do
    service_has_local_retention "$service" "$compose_file" || hygiene_die "$service does not have the fixed local 10m x 3 retention contract"
    printf 'RETENTION_CHECK|CONTAINER|%s|PASS|driver=local;max-size=10m;max-file=3\n' "$service"
  done
  timing_log_is_sanitized "$nginx_file" || hygiene_die "Nginx timing log is missing, unsafe, or not Docker stdout based"
  printf 'RETENTION_CHECK|NGINX|PASS|dimensions=method,normalized_uri;timing=request_time,upstream_connect_time,upstream_header_time,upstream_response_time;payload=non-sensitive\n'
  printf 'RETENTION_CHECK|JOURNALD|POLICY_ONLY|SystemMaxUse=1G;RuntimeMaxUse=512M;MaxRetentionSec=14day;MaxFileSec=1day;vacuum=forbidden\n'
  printf 'RETENTION_CHECK|STATUS|PASS\n'
  printf 'RETENTION_CHECK|BOUNDARY|docker_runtime=untouched;volumes=untouched;database=untouched;production=untouched\n'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
