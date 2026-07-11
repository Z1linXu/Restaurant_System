#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="${ENV_FILE:-.env}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

if [[ -n "${DOMAIN:-}" && "${ENABLE_HTTPS:-true}" == "true" ]]; then
  FRONTEND_URL="${FRONTEND_URL:-https://${DOMAIN}}"
elif [[ -n "${DOMAIN:-}" ]]; then
  FRONTEND_URL="${FRONTEND_URL:-http://${DOMAIN}}"
else
  FRONTEND_URL="${FRONTEND_URL:-http://localhost:${HTTP_PORT:-80}}"
fi

echo "Checking frontend: $FRONTEND_URL"
frontend_code="$(curl -ksS -o /dev/null -w "%{http_code}" "$FRONTEND_URL" || true)"
if [[ "$frontend_code" =~ ^(200|301|302)$ ]]; then
  echo "Frontend reachable: HTTP $frontend_code"
else
  echo "Frontend check failed or unexpected: HTTP $frontend_code" >&2
  exit 1
fi

auth_url="${FRONTEND_URL%/}/api/v1/menu/health"
echo "Checking backend through reverse proxy: $auth_url"
backend_code="$(curl -ksS -o /dev/null -w "%{http_code}" "$auth_url" || true)"
if [[ "$backend_code" == "200" ]]; then
  echo "Backend reachable: HTTP $backend_code"
else
  echo "Backend check failed or unexpected: HTTP $backend_code" >&2
  exit 1
fi

echo "Optional WebSocket path sanity check: ${FRONTEND_URL%/}/ws"
ws_code="$(curl -ksS -o /dev/null -w "%{http_code}" -I "${FRONTEND_URL%/}/ws" || true)"
echo "WebSocket endpoint HTTP probe returned: $ws_code"

echo "Health check completed."
