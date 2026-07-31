#!/usr/bin/env bash
set -euo pipefail

# This script prints Owner-action-required plans only. It deliberately does not
# invoke Docker or change Staging state.

EXPECTED_ROOT="/srv/restaurant-pos/staging"
EXPECTED_PROJECT="restaurant-pos-staging"
ACTION=""
ENV_FILE=""
APPROVED_SHA=""
ROLLBACK_SHA=""

usage() {
  cat <<'EOF'
Usage:
  ./staging-server-control.sh --validate --env-file /srv/restaurant-pos/staging/config/.env.staging --approved-sha <full-sha>
  ./staging-server-control.sh --dry-run --env-file /srv/restaurant-pos/staging/config/.env.staging --approved-sha <full-sha>
  ./staging-server-control.sh --plan-stop --env-file /srv/restaurant-pos/staging/config/.env.staging --approved-sha <full-sha>
  ./staging-server-control.sh --plan-rollback --env-file /srv/restaurant-pos/staging/config/.env.staging --approved-sha <current-full-sha> --to-sha <prior-full-sha>

All actions only print an OWNER_ACTION_REQUIRED plan. This script never runs
Docker, stops or starts containers, pulls images, restores data, or changes
Flyway/schema state.
EOF
}

die() { printf 'CONTROL|INPUTS|NO_GO|%s\n' "$1" >&2; exit 2; }
is_full_sha() { [[ "$1" =~ ^[0-9a-f]{40}$ ]]; }

validate_inputs() {
  [[ "$ENV_FILE" == "$EXPECTED_ROOT/config/.env.staging" ]] || die "--env-file must be the exact Staging env path"
  is_full_sha "$APPROVED_SHA" || die "--approved-sha must be a lowercase full 40-character SHA"
  if [[ "$ACTION" == "rollback" ]]; then
    is_full_sha "$ROLLBACK_SHA" || die "--to-sha must be a lowercase full 40-character SHA"
    [[ "$ROLLBACK_SHA" != "$APPROVED_SHA" ]] || die "rollback target must differ from current SHA"
  fi
}

print_stop_plan() {
  cat <<EOF
CONTROL|PLAN_STOP|OWNER_ACTION_REQUIRED|no Docker command was executed
OWNER_ACTION_REQUIRED: after a separate approval, run exactly:
docker --context default compose --project-name $EXPECTED_PROJECT --env-file $ENV_FILE -f /srv/restaurant-pos/staging/releases/$APPROVED_SHA/deployment/cloud/docker-compose.staging.yml stop nginx backend db
PROHIBITED: docker compose down, down -v, rm, prune, pull, Flyway clean, restore, volume deletion
EOF
}

print_rollback_plan() {
  cat <<EOF
CONTROL|PLAN_ROLLBACK|OWNER_ACTION_REQUIRED|no Docker command was executed
PRECONDITION: documented schema-compatibility evidence and prior SHA-specific image IDs for $ROLLBACK_SHA.
OWNER_ACTION_REQUIRED: a separately reviewed procedure may replace only backend/nginx images for $EXPECTED_PROJECT.
DATABASE_BOUNDARY: do not roll back PostgreSQL, Flyway history, volumes, backups, or data.
TARGET_SHA=$ROLLBACK_SHA
EOF
}

main() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --validate|--dry-run) [[ -z "$ACTION" ]] || die "choose one action"; ACTION="validate" ;;
      --plan-stop) [[ -z "$ACTION" ]] || die "choose one action"; ACTION="stop" ;;
      --plan-rollback) [[ -z "$ACTION" ]] || die "choose one action"; ACTION="rollback" ;;
      --env-file) ENV_FILE="${2:-}"; shift ;;
      --approved-sha) APPROVED_SHA="${2:-}"; shift ;;
      --to-sha) ROLLBACK_SHA="${2:-}"; shift ;;
      --help|-h) usage; exit 0 ;;
      *) die "unsupported option: $1" ;;
    esac
    shift
  done
  [[ -n "$ACTION" ]] || { usage; exit 2; }
  validate_inputs
  case "$ACTION" in
    validate) printf 'CONTROL|VALIDATE|PASS|input-only validation; no Docker command was executed\n' ;;
    stop) print_stop_plan ;;
    rollback) print_rollback_plan ;;
  esac
}

main "$@"
