#!/usr/bin/env bash
set -euo pipefail

PGHOST_VALUE="localhost"
PGUSER_VALUE="xuzilin"
PGDATABASE_VALUE="restaurant_system"
STORE_ID="1"
OUTPUT_FILE="old-store-config.dump"

ALLOWED_TABLES=(
  stations
  dining_tables
  menu_categories
  menu_items
  menu_item_options
  inventory_items
  prep_recipes
  prep_recipe_details
  menu_item_bom
  menu_item_option_bom
  receipt_templates
  store_kds_display_configs
)

STORE_SCOPED_TABLES=(
  stations
  dining_tables
  menu_categories
  menu_items
  inventory_items
  receipt_templates
  store_kds_display_configs
)

FORBIDDEN_TABLES=(
  organizations
  stores
  users
  user_credentials
  organization_memberships
  store_memberships
  orders
  order_items
  order_item_options
  payments
  refresh_tokens
  audit_logs
  analytics_alerts
  menu_item_sales_summary
  sales_daily_summary
  sales_hourly_summary
  store_performance_summary
  print_jobs
  print_job_attempts
  printer_configs
  printer_assignments
  store_devices
)

die() {
  echo "ERROR: $*" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: ./export-store-config.sh [options]

Exports only store configuration table data from the old Mac PostgreSQL DB.

Defaults:
  --host localhost
  --user xuzilin
  --database restaurant_system
  --store-id 1
  --output old-store-config.dump

Options:
  --host HOST
  --user USER
  --database DATABASE
  --store-id STORE_ID
  --output FILE
USAGE
}

contains() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "$1 is required."
}

extract_table_data_names() {
  awk '
    {
      for (i = 1; i <= NF - 3; i++) {
        if ($i == "TABLE" && $(i + 1) == "DATA" && $(i + 2) == "public") {
          print $(i + 3)
        }
      }
    }
  '
}

validate_toc_text() {
  local toc_text="$1"
  local table
  local tables
  tables="$(printf '%s\n' "$toc_text" | extract_table_data_names | sort -u)"

  for table in "${ALLOWED_TABLES[@]}"; do
    if ! printf '%s\n' "$tables" | grep -qx "$table"; then
      die "Dump is missing required table data: $table"
    fi
  done

  while IFS= read -r table; do
    [[ -z "$table" ]] && continue
    if ! contains "$table" "${ALLOWED_TABLES[@]}"; then
      die "Dump contains non-allowed table data: $table"
    fi
  done <<< "$tables"

  for table in "${FORBIDDEN_TABLES[@]}"; do
    if printf '%s\n' "$tables" | grep -qx "$table"; then
      die "Dump contains forbidden table data: $table"
    fi
  done
}

store_scoped_validation_sql() {
  local table
  for table in "${STORE_SCOPED_TABLES[@]}"; do
    printf "select '%s' as table_name, count(*) as off_store_rows from public.%s where store_id is distinct from %s;\n" \
      "$table" "$table" "$STORE_ID"
  done
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      [[ $# -ge 2 ]] || die "--host requires a value."
      PGHOST_VALUE="$2"
      shift
      ;;
    --user)
      [[ $# -ge 2 ]] || die "--user requires a value."
      PGUSER_VALUE="$2"
      shift
      ;;
    --database)
      [[ $# -ge 2 ]] || die "--database requires a value."
      PGDATABASE_VALUE="$2"
      shift
      ;;
    --store-id)
      [[ $# -ge 2 ]] || die "--store-id requires a value."
      STORE_ID="$2"
      shift
      ;;
    --output)
      [[ $# -ge 2 ]] || die "--output requires a value."
      OUTPUT_FILE="$2"
      shift
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

[[ "$STORE_ID" =~ ^[0-9]+$ ]] || die "--store-id must be numeric."

require_command psql
require_command pg_dump
require_command pg_restore

psql_base=(psql -h "$PGHOST_VALUE" -U "$PGUSER_VALUE" -d "$PGDATABASE_VALUE" -v ON_ERROR_STOP=1 -At)

server_version_num="$("${psql_base[@]}" -c "show server_version_num;")"
server_major=$((server_version_num / 10000))
pg_dump_major="$(pg_dump --version | sed -E 's/.* ([0-9]+)(\.[0-9]+)?.*/\1/')"
if [[ -z "$pg_dump_major" || ! "$pg_dump_major" =~ ^[0-9]+$ ]]; then
  die "Unable to determine pg_dump major version."
fi
if (( pg_dump_major < server_major )); then
  die "pg_dump major version $pg_dump_major is older than source server major version $server_major. Use PostgreSQL $server_major or newer client tools."
fi

store_count="$("${psql_base[@]}" -c "select count(*) from public.stores where id = $STORE_ID;")"
[[ "$store_count" == "1" ]] || die "Source store id $STORE_ID was not found."

off_store_rows="$(store_scoped_validation_sql | "${psql_base[@]}")"
if printf '%s\n' "$off_store_rows" | awk -F '|' '$2 != 0 { exit 1 }'; then
  :
else
  printf '%s\n' "$off_store_rows" >&2
  die "Source contains rows for a different store_id in exported store-scoped tables."
fi

pg_dump_args=(
  -h "$PGHOST_VALUE"
  -U "$PGUSER_VALUE"
  -d "$PGDATABASE_VALUE"
  -Fc
  --data-only
  --no-owner
  --no-privileges
  --file "$OUTPUT_FILE"
)

for table in "${ALLOWED_TABLES[@]}"; do
  pg_dump_args+=(--table "public.$table")
done

echo "Exporting store config data from $PGDATABASE_VALUE store_id=$STORE_ID to $OUTPUT_FILE"
echo "Using pg_dump major $pg_dump_major against source PostgreSQL major $server_major"
pg_dump "${pg_dump_args[@]}"

toc_text="$(pg_restore -l "$OUTPUT_FILE")"
validate_toc_text "$toc_text"

echo "Export complete: $OUTPUT_FILE"
echo "Included tables:"
printf ' - %s\n' "${ALLOWED_TABLES[@]}"
