#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -P "$TEST_DIR/../../.." && pwd)"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

for template in \
  "$REPOSITORY_ROOT/deployment/cloud/nginx.http.conf.template" \
  "$REPOSITORY_ROOT/deployment/cloud/nginx.https.conf.template"
do
  api_block="$(awk '/location \/api\//,/^    }/' "$template")"
  ws_block="$(awk '/location \/ws/,/^    }/' "$template")"

  grep -Fq 'proxy_set_header Host $http_host;' <<<"$api_block" ||
    fail "API proxy must preserve the browser-visible host and explicit port in $template"
  grep -Fq 'proxy_set_header Host $http_host;' <<<"$ws_block" ||
    fail "WebSocket proxy must preserve the browser-visible host and explicit port in $template"
  if grep -Fq 'proxy_set_header Host $host;' "$template"; then
    fail "port-stripping Host forwarding remains in $template"
  fi
done

echo 'PASS: nginx preserves the browser-visible Host and port for API and WebSocket same-origin evaluation.'
