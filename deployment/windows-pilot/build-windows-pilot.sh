#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deployment/windows-pilot"
PACKAGE_DIR="$DEPLOY_DIR/package"
ZIP_PATH="$DEPLOY_DIR/restaurant-pos-windows-pilot.zip"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-restaurant_system}"
DB_USER="${DB_USER:-xuzilin}"
DB_PASSWORD="${DB_PASSWORD:-741x741}"

echo "== Restaurant POS Windows Pilot Package =="
echo "Root: $ROOT_DIR"
echo "Package: $PACKAGE_DIR"
echo

rm -rf "$PACKAGE_DIR" "$ZIP_PATH"
mkdir -p "$PACKAGE_DIR/backend" "$PACKAGE_DIR/frontend" "$PACKAGE_DIR/database" "$PACKAGE_DIR/config" "$PACKAGE_DIR/scripts" "$PACKAGE_DIR/logs" "$PACKAGE_DIR/backups"

echo "== Build backend jar =="
(cd "$ROOT_DIR/backend" && mvn -q -DskipTests package)
BACKEND_JAR="$(find "$ROOT_DIR/backend/target" -maxdepth 1 -type f -name '*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | head -n 1)"
if [[ -z "$BACKEND_JAR" ]]; then
  echo "ERROR: Backend jar not found in backend/target"
  exit 1
fi
cp "$BACKEND_JAR" "$PACKAGE_DIR/backend/restaurant-system-backend.jar"

echo "== Build frontend dist =="
(cd "$ROOT_DIR/frontend" && npm run build)
cp -R "$ROOT_DIR/frontend/dist" "$PACKAGE_DIR/frontend/dist"

echo "== Copy pilot config and Windows scripts =="
cp "$ROOT_DIR/backend/src/main/resources/application-pilot.yml" "$PACKAGE_DIR/config/application-pilot.yml"
cp "$DEPLOY_DIR/scripts/"*.bat "$PACKAGE_DIR/"
cp "$DEPLOY_DIR/scripts/frontend-server.js" "$PACKAGE_DIR/scripts/frontend-server.js"
cp "$DEPLOY_DIR/README_WINDOWS_PILOT.md" "$PACKAGE_DIR/README_WINDOWS_PILOT.md"

echo "== Export PostgreSQL database with pg_dump =="
if command -v pg_dump >/dev/null 2>&1; then
  export PGPASSWORD="$DB_PASSWORD"
  if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -Fc -f "$PACKAGE_DIR/database/restaurant_pos.dump" "$DB_NAME"; then
    echo "Created database/restaurant_pos.dump"
  else
    echo "WARNING: pg_dump failed. See database/PG_DUMP_FAILED.txt"
    cat > "$PACKAGE_DIR/database/PG_DUMP_FAILED.txt" <<EOF
pg_dump failed on Mac.

Retry manually from project root:

  DB_HOST=$DB_HOST DB_PORT=$DB_PORT DB_NAME=$DB_NAME DB_USER=$DB_USER DB_PASSWORD=*** \\
  deployment/windows-pilot/build-windows-pilot.sh

Or create a custom dump manually:

  PGPASSWORD=your_password pg_dump -h localhost -p 5432 -U your_user -Fc -f deployment/windows-pilot/package/database/restaurant_pos.dump restaurant_system
EOF
  fi
else
  echo "WARNING: pg_dump is not installed. See database/PG_DUMP_NOT_AVAILABLE.txt"
  cat > "$PACKAGE_DIR/database/PG_DUMP_NOT_AVAILABLE.txt" <<EOF
pg_dump was not found on this Mac.

Install PostgreSQL client tools or create the dump manually:

  PGPASSWORD=your_password pg_dump -h localhost -p 5432 -U your_user -Fc -f deployment/windows-pilot/package/database/restaurant_pos.dump restaurant_system
EOF
fi
unset PGPASSWORD

echo "== Create zip =="
(cd "$PACKAGE_DIR" && zip -qry "$ZIP_PATH" . -x "logs/*" "backups/*" "node_modules/*" "target/*" ".git/*")

echo
echo "Package complete:"
echo "  $PACKAGE_DIR"
echo "  $ZIP_PATH"
