# Restaurant POS Windows Pilot Deployment

This package runs the Restaurant POS pilot on one Windows computer inside the restaurant LAN.

The Windows computer runs:

- Spring Boot backend on port `8080`
- Static frontend server on port `5173`
- PostgreSQL database

Pads access the system with:

```text
http://WINDOWS_FIXED_IP:5173/
```

## 1. Windows Requirements

Install these on the Windows server:

- Java 17 or newer
- PostgreSQL 14+ recommended
- Node.js 20+ recommended
- PostgreSQL command line tools in `PATH`:
  - `psql`
  - `pg_dump`
  - `pg_restore`
  - `createdb`
  - `dropdb`

Do not install or copy `node_modules`. The package uses a small built-in Node static server and the already-built frontend `dist`.

## 2. Package Layout

```text
backend/restaurant-system-backend.jar
frontend/dist/
config/application-pilot.yml
database/restaurant_pos.dump
scripts/frontend-server.js
start-pos-windows.bat
stop-pos-windows.bat
restore-db-windows.bat
backup-db-windows.bat
check-pos-windows.bat
logs/
backups/
```

## 3. Configure PostgreSQL

Default pilot database settings:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=restaurant_pos
DB_USER=postgres
DB_PASSWORD=your_postgres_password
```

Set the password in the same Command Prompt before running restore/start:

```bat
set DB_PASSWORD=your_postgres_password
```

Optional overrides:

```bat
set DB_HOST=localhost
set DB_PORT=5432
set DB_NAME=restaurant_pos
set DB_USER=postgres
set DB_PASSWORD=your_postgres_password
set JWT_SECRET=replace-with-a-long-random-secret
```

The backend reads `config/application-pilot.yml`; no Mac path is used.

## 4. Restore Database

From the extracted package folder:

```bat
set DB_PASSWORD=your_postgres_password
restore-db-windows.bat
```

This restores:

```text
database/restaurant_pos.dump
```

Do not copy a PostgreSQL data directory between Mac and Windows. Always use `pg_dump` / `pg_restore`.

## 5. Start System

From the extracted package folder:

```bat
set DB_PASSWORD=your_postgres_password
start-pos-windows.bat
```

Logs are written to:

```text
logs/backend.log
logs/frontend.log
```

Run health check:

```bat
check-pos-windows.bat
```

The script prints Windows IPv4 addresses. Use the restaurant LAN/Wi-Fi address for Pads:

```text
http://WINDOWS_FIXED_IP:5173/
```

## 6. Stop System

```bat
stop-pos-windows.bat
```

This stops processes listening on ports `8080` and `5173`.

## 7. Windows Firewall

Allow inbound TCP connections:

- `5173` for Pads accessing the frontend
- `8080` optional for direct backend diagnostics

If Windows prompts for Java or Node network access, allow access on the restaurant private network.

## 8. Fixed IP and Power Settings

Recommended:

- Set a fixed IPv4 address for the Windows server.
- Keep Pads, Windows server, and printers on the same LAN/Wi-Fi.
- Disable Windows sleep/hibernation during service hours.
- Keep the Windows server plugged into power.

## 9. Default Local Login Accounts

For pilot/dev recovery:

```text
owner   / 741xu741 / OWNER
manager / 741xu741 / MANAGER
staff   / 741xu741 / FRONTDESK
```

Passwords are stored as BCrypt hashes in PostgreSQL. Password hashes are not returned to the frontend.

## 10. Printer Setup

Requirements:

1. Windows computer and printers must be on the same LAN.
2. All printers should have fixed IP addresses.
3. Open `/admin/settings/printing`.
4. Set Print Center mode to `REAL`.
5. Configure all printer IP/port values.
6. For each printer, run:
   - Connection Test
   - Test Print
7. For assigned modules, run:
   - Test GRAB
   - Test FRONTDESK_RECEIPT

Important:

- Printing failure does not roll back order submission.
- If a kitchen/frontdesk receipt fails, use Print Center failed jobs and reprint.
- Keep printer paper and network status checked before service.

## 11. Daily Database Backup

Run this daily after service:

```bat
set DB_PASSWORD=your_postgres_password
backup-db-windows.bat
```

Backups are written to:

```text
backups/restaurant_pos-YYYYMMDD-HHMMSS.dump
```

Copy backups to external storage or cloud storage after each business day.

## 12. Reports Warning

Reports are under validation and should not be used for accounting during pilot.

Known current limitation:

- Admin Reports algorithms still need a separate readiness review.
- Use reports only as operational reference during the Windows pilot.

## 13. Troubleshooting

If Pads cannot open the system:

1. Run `check-pos-windows.bat`.
2. Confirm the Pad uses the Windows LAN IP, not `localhost`.
3. Confirm Windows Firewall allows port `5173`.
4. Confirm Windows and Pad are on the same Wi-Fi/LAN.
5. Check `logs/frontend.log`.
6. Check `logs/backend.log`.

If login fails:

1. Confirm backend is running.
2. Open `http://WINDOWS_FIXED_IP:5173/api/v1/auth/me`.
3. HTTP `401` is acceptable when not logged in; it means the backend proxy is reachable.
4. Try `owner / 741xu741`.

If database connection fails:

1. Confirm PostgreSQL service is running.
2. Confirm `DB_PASSWORD`.
3. Confirm database was restored.
4. Confirm `DB_NAME=restaurant_pos`.
