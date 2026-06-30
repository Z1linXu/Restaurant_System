@echo off
setlocal
cd /d "%~dp0"

if "%DB_HOST%"=="" set DB_HOST=localhost
if "%DB_PORT%"=="" set DB_PORT=5432
if "%DB_NAME%"=="" set DB_NAME=restaurant_pos
if "%DB_USER%"=="" set DB_USER=postgres
if "%DUMP_FILE%"=="" set DUMP_FILE=database\restaurant_pos.dump

if "%DB_PASSWORD%"=="" (
  echo ERROR: Please set DB_PASSWORD before restoring.
  echo Example:
  echo   set DB_PASSWORD=your_postgres_password
  echo   restore-db-windows.bat
  exit /b 1
)

if not exist "%DUMP_FILE%" (
  echo ERROR: Dump file not found: %DUMP_FILE%
  exit /b 1
)

where psql >nul 2>nul
if errorlevel 1 (
  echo ERROR: psql was not found. Install PostgreSQL and add its bin folder to PATH.
  exit /b 1
)

where pg_restore >nul 2>nul
if errorlevel 1 (
  echo ERROR: pg_restore was not found. Install PostgreSQL and add its bin folder to PATH.
  exit /b 1
)

set PGPASSWORD=%DB_PASSWORD%

echo WARNING: This will recreate database "%DB_NAME%" on %DB_HOST%:%DB_PORT%.
choice /C YN /M "Continue"
if errorlevel 2 exit /b 1

psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='%DB_NAME%' AND pid <> pg_backend_pid();"
dropdb -h %DB_HOST% -p %DB_PORT% -U %DB_USER% --if-exists %DB_NAME%
createdb -h %DB_HOST% -p %DB_PORT% -U %DB_USER% %DB_NAME%
pg_restore -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% --clean --if-exists "%DUMP_FILE%"

if errorlevel 1 (
  echo Restore failed.
  exit /b 1
)

echo Restore completed.
endlocal
