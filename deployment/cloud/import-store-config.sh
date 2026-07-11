#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}"
POSTGRES_CLIENT_IMAGE="${POSTGRES_CLIENT_IMAGE:-postgres:18-alpine}"
DUMP_FILE=""
TARGET_STORE_ID=""
DRY_RUN=false
SELF_TEST=false
NETWORK_NAME=""
CLIENT_DUMP_PATH="/tmp/old-store-config.dump"
BACKEND_STOPPED=false

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

usage() {
  cat <<'USAGE'
Usage: ./import-store-config.sh --file old-store-config.dump --target-store-id 1 [--dry-run]

Imports only whitelisted single-store configuration data into the cloud DB.

Required:
  --file FILE
  --target-store-id STORE_ID

Options:
  --dry-run   Validate target DB and dump contents without importing.

Environment:
  POSTGRES_CLIENT_IMAGE  PostgreSQL client image used for pg_restore/psql.
                         Default: postgres:18-alpine
USAGE
}

compose() {
  "${SUDO[@]}" docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

cleanup() {
  if [[ "$BACKEND_STOPPED" == "true" ]]; then
    compose up -d backend >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

compose_project_name() {
  compose config --format json | awk -F '"' '/"name"/ { print $4; exit }'
}

resolve_network_name() {
  local project_name
  project_name="$(compose_project_name)"
  [[ -n "$project_name" ]] || die "Unable to resolve Docker Compose project name."
  NETWORK_NAME="${project_name}_restaurant-pos"
}

absolute_dump_file() {
  printf '%s/%s' "$(cd "$(dirname "$DUMP_FILE")" && pwd)" "$(basename "$DUMP_FILE")"
}

client_run() {
  [[ -n "$NETWORK_NAME" ]] || resolve_network_name
  "${SUDO[@]}" docker run --rm -i \
    --network "$NETWORK_NAME" \
    -e PGPASSWORD="$DB_PASSWORD" \
    -e DB_USER="$DB_USER" \
    -e DB_NAME="$DB_NAME" \
    -e DUMP_FILE_PATH="$CLIENT_DUMP_PATH" \
    -v "$(absolute_dump_file):$CLIENT_DUMP_PATH:ro" \
    "$POSTGRES_CLIENT_IMAGE" "$@"
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

allowed_toc_fixture() {
  local oid=1000
  local table
  for table in "${ALLOWED_TABLES[@]}"; do
    printf '%s; 0 %s TABLE DATA public %s postgres\n' "$oid" "$oid" "$table"
    oid=$((oid + 1))
  done
}

build_empty_table_check_sql() {
  local table
  cat <<SQL
do \$\$
declare
  target_store_id bigint := $TARGET_STORE_ID;
  row_count bigint;
  table_name text;
begin
  select count(*) into row_count from public.stores where id = target_store_id;
  if row_count <> 1 then
    raise exception 'Target store id % was not found', target_store_id;
  end if;

SQL
  for table in "${ALLOWED_TABLES[@]}"; do
    printf "  select count(*) into row_count from public.%s;\n" "$table"
    printf "  if row_count <> 0 then\n"
    printf "    raise exception 'Target config table %s is not empty (%% rows)', row_count;\n" "$table"
    printf "  end if;\n\n"
  done
  cat <<'SQL'
end $$;
select id, name, printing_enabled, printing_mode
from public.stores
where id = TARGET_STORE_ID_PLACEHOLDER;
SQL
}

preflight_sql() {
  build_empty_table_check_sql | sed "s/TARGET_STORE_ID_PLACEHOLDER/$TARGET_STORE_ID/g"
}

sequence_table_values_sql() {
  local first=true
  local table
  for table in "${ALLOWED_TABLES[@]}"; do
    if [[ "$first" == "true" ]]; then
      first=false
    else
      printf ",\n"
    fi
    printf "  ('%s')" "$table"
  done
}

post_import_validation_sql() {
  local table
  cat <<SQL

create temp table _store_config_sequence_tables(table_name text) on commit drop;
insert into _store_config_sequence_tables(table_name) values
$(sequence_table_values_sql);

do \$\$
declare
  target_store_id bigint := $TARGET_STORE_ID;
  invalid_count bigint;
  table_name text;
  max_id bigint;
  last_id bigint;
  seq_name text;
begin
SQL
  for table in "${STORE_SCOPED_TABLES[@]}"; do
    printf "  execute format('select count(*) from public.%%I where store_id is distinct from \$1', '%s') into invalid_count using target_store_id;\n" "$table"
    printf "  if invalid_count <> 0 then raise exception 'Table %s contains rows outside target store %%', target_store_id; end if;\n\n" "$table"
  done
  cat <<'SQL'
  select count(*) into invalid_count
  from public.menu_items item
  left join public.menu_categories category on category.id = item.category_id
  where item.category_id is null or category.id is null;
  if invalid_count <> 0 then
    raise exception 'menu_items.category_id contains invalid references';
  end if;

  select count(*) into invalid_count
  from public.menu_items item
  left join public.stations station on station.id = item.station_id
  where item.station_id is not null and station.id is null;
  if invalid_count <> 0 then
    raise exception 'menu_items.station_id contains invalid references';
  end if;

  select count(*) into invalid_count
  from public.menu_item_options option_row
  left join public.menu_items item on item.id = option_row.menu_item_id
  where option_row.menu_item_id is null or item.id is null;
  if invalid_count <> 0 then
    raise exception 'menu_item_options.menu_item_id contains invalid references';
  end if;

  select count(*) into invalid_count
  from public.menu_item_options option_row
  left join public.menu_item_options parent_option on parent_option.id = option_row.parent_option_id
  where option_row.parent_option_id is not null and parent_option.id is null;
  if invalid_count <> 0 then
    raise exception 'menu_item_options.parent_option_id contains invalid references';
  end if;

  select count(*) into invalid_count
  from public.menu_item_bom bom
  left join public.menu_items item on item.id = bom.menu_item_id
  left join public.inventory_items inventory on inventory.id = bom.inventory_item_id
  where bom.menu_item_id is null or item.id is null
     or bom.inventory_item_id is null or inventory.id is null;
  if invalid_count <> 0 then
    raise exception 'menu_item_bom contains invalid references';
  end if;

  select count(*) into invalid_count
  from public.menu_item_option_bom bom
  left join public.menu_item_options option_row on option_row.id = bom.menu_item_option_id
  left join public.inventory_items inventory on inventory.id = bom.inventory_item_id
  where bom.menu_item_option_id is null or option_row.id is null
     or bom.inventory_item_id is null or inventory.id is null;
  if invalid_count <> 0 then
    raise exception 'menu_item_option_bom contains invalid references';
  end if;

  select count(*) into invalid_count
  from public.prep_recipes recipe
  left join public.inventory_items inventory on inventory.id = recipe.output_inventory_item_id
  where recipe.output_inventory_item_id is null or inventory.id is null;
  if invalid_count <> 0 then
    raise exception 'prep_recipes.output_inventory_item_id contains invalid references';
  end if;

  select count(*) into invalid_count
  from public.prep_recipe_details detail
  left join public.prep_recipes recipe on recipe.id = detail.prep_recipe_id
  left join public.inventory_items inventory on inventory.id = detail.input_inventory_item_id
  where detail.prep_recipe_id is null or recipe.id is null
     or detail.input_inventory_item_id is null or inventory.id is null;
  if invalid_count <> 0 then
    raise exception 'prep_recipe_details contains invalid references';
  end if;

  for table_name in select table_name from _store_config_sequence_tables loop
    select pg_get_serial_sequence('public.' || table_name, 'id') into seq_name;
    if seq_name is null then
      raise exception 'No id sequence found for table %', table_name;
    end if;
    execute format('select coalesce(max(id), 0) from public.%I', table_name) into max_id;
    execute 'select setval($1, $2, false)' using seq_name, max_id + 1;
    execute format('select last_value from %s', seq_name::regclass) into last_id;
    if last_id <= max_id then
      raise exception 'Sequence % next value is not greater than max id for %', seq_name, table_name;
    end if;
  end loop;
end $$;
SQL
}

counts_sql() {
  local first=true
  local table
  for table in "${ALLOWED_TABLES[@]}"; do
    if [[ "$first" == "true" ]]; then
      first=false
    else
      printf " union all\n"
    fi
    printf "select '%s' as table_name, count(*)::bigint as row_count from public.%s" "$table" "$table"
  done
  printf " order by table_name;\n"
}

run_psql() {
  client_run psql -h db -p 5432 -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 "$@"
}

dump_toc_text() {
  client_run pg_restore -l "$CLIENT_DUMP_PATH"
}

run_import_transaction() {
  client_run sh -ceu "$(cat <<'SH'
{
  printf '%s\n' '\set ON_ERROR_STOP on'
  printf '%s\n' 'begin;'
  pg_restore --data-only --no-owner --no-privileges --exit-on-error "$DUMP_FILE_PATH"
  cat
  printf '%s\n' 'commit;'
} | psql -h db -p 5432 -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1
SH
)" <<SQL
$(post_import_validation_sql)
SQL
}

run_self_test() {
  local toc_text
  toc_text="$(allowed_toc_fixture)"
  validate_toc_text "$toc_text"

  if (validate_toc_text "$toc_text"$'\n''9999; 0 9999 TABLE DATA public orders postgres') >/dev/null 2>&1; then
    die "self-test failed: forbidden table was accepted"
  fi

  TARGET_STORE_ID=1
  if ! preflight_sql | grep -q "Target config table menu_items is not empty"; then
    die "self-test failed: non-empty table guard missing"
  fi
  if ! post_import_validation_sql | grep -q "setval"; then
    die "self-test failed: sequence calibration missing"
  fi

  echo "import-store-config.sh self-test success"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      ;;
    --file)
      [[ $# -ge 2 ]] || die "--file requires a value."
      DUMP_FILE="$2"
      shift
      ;;
    --target-store-id)
      [[ $# -ge 2 ]] || die "--target-store-id requires a value."
      TARGET_STORE_ID="$2"
      shift
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

if [[ "$SELF_TEST" == "true" ]]; then
  run_self_test
  exit 0
fi

[[ -n "$DUMP_FILE" ]] || die "--file is required."
[[ -f "$DUMP_FILE" ]] || die "Dump file not found: $DUMP_FILE"
[[ -n "$TARGET_STORE_ID" ]] || die "--target-store-id is required."
[[ "$TARGET_STORE_ID" =~ ^[0-9]+$ ]] || die "--target-store-id must be numeric."
[[ -f "$ENV_FILE" ]] || die "Missing $ENV_FILE. Copy .env.example to .env and fill database settings first."

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

[[ -n "${DB_NAME:-}" ]] || die "Missing required environment value: DB_NAME"
[[ -n "${DB_USER:-}" ]] || die "Missing required environment value: DB_USER"
[[ -n "${DB_PASSWORD:-}" ]] || die "Missing required environment value: DB_PASSWORD"

compose up -d db >/dev/null
resolve_network_name

toc_text="$(dump_toc_text)"
validate_toc_text "$toc_text"

echo "Validated dump whitelist."
echo "Checking target store and empty config tables..."
preflight_sql | run_psql -At

if [[ "$DRY_RUN" == "true" ]]; then
  echo "dry-run success: dump is allowed, target store exists, and target config tables are empty."
  exit 0
fi

echo "Creating pre-import backup..."
ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" "$SCRIPT_DIR/backup-db.sh"

echo "Stopping backend..."
compose stop backend >/dev/null || true
BACKEND_STOPPED=true

echo "Importing store configuration in one transaction..."
run_import_transaction

echo "Imported row counts:"
counts_sql | run_psql -F $'\t' -At

echo "Starting backend..."
compose up -d backend >/dev/null
BACKEND_STOPPED=false

echo "Running health check..."
ENV_FILE="$ENV_FILE" "$SCRIPT_DIR/health-check.sh"

echo "Store configuration import complete."
