@echo off
setlocal
cd /d "%~dp0"

if "%DB_HOST%"=="" set DB_HOST=localhost
if "%DB_PORT%"=="" set DB_PORT=5432
if "%DB_NAME%"=="" set DB_NAME=restaurant_pos
if "%DB_USER%"=="" set DB_USER=postgres

if "%DB_PASSWORD%"=="" (
  echo ERROR: Please set DB_PASSWORD before backup.
  exit /b 1
)

where pg_dump >nul 2>nul
if errorlevel 1 (
  echo ERROR: pg_dump was not found. Install PostgreSQL and add its bin folder to PATH.
  exit /b 1
)

if not exist backups mkdir backups

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set TS=%%i
set BACKUP_FILE=backups\restaurant_pos-%TS%.dump
set PGPASSWORD=%DB_PASSWORD%

pg_dump -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -Fc -f "%BACKUP_FILE%" %DB_NAME%

if errorlevel 1 (
  echo Backup failed.
  exit /b 1
)

echo Backup created: %BACKUP_FILE%
endlocal
