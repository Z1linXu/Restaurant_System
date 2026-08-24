#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
RECOVERY="$ROOT/deployment/cloud/production-v26-recover.sh"

policy_definition="$(sed -n '/^recovery_nginx_identity_allowed() {$/,/^}$/p' "$RECOVERY")"
[[ "$policy_definition" == recovery_nginx_identity_allowed* ]] || {
  printf 'recovery Nginx policy function is unavailable\n' >&2
  exit 1
}
eval "$policy_definition"

EXPECTED_PROJECT="cloud"
ROLLBACK_FRONTEND_ID="sha256:$(printf 'a%.0s' {1..64})"
TARGET_FRONTEND_ID="sha256:$(printf 'b%.0s' {1..64})"
CONTAINER_ID="$(printf 'c%.0s' {1..64})"

recovery_nginx_identity_allowed "" ANY
recovery_nginx_identity_allowed "$CONTAINER_ID|cloud|nginx|$ROLLBACK_FRONTEND_ID|false" ANY
recovery_nginx_identity_allowed "$CONTAINER_ID|cloud|nginx|$ROLLBACK_FRONTEND_ID|false" "$CONTAINER_ID"

! recovery_nginx_identity_allowed "$CONTAINER_ID|cloud|nginx|$TARGET_FRONTEND_ID|false" ANY
! recovery_nginx_identity_allowed "$CONTAINER_ID|cloud|nginx|$ROLLBACK_FRONTEND_ID|true" ANY
! recovery_nginx_identity_allowed "$CONTAINER_ID|cloud|nginx|$ROLLBACK_FRONTEND_ID|true" "$CONTAINER_ID"
! recovery_nginx_identity_allowed "" "$CONTAINER_ID"
! recovery_nginx_identity_allowed "$CONTAINER_ID|cloud|nginx|$ROLLBACK_FRONTEND_ID|false" "$(printf 'd%.0s' {1..64})"

printf 'Production V26 recovery public-edge policy negative tests: PASS\n'
