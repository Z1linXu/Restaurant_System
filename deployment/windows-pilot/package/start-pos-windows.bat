@echo off
setlocal
cd /d "%~dp0"

if not exist logs mkdir logs

if "%DB_HOST%"=="" set DB_HOST=localhost
if "%DB_PORT%"=="" set DB_PORT=5432
if "%DB_NAME%"=="" set DB_NAME=restaurant_pos
if "%DB_USER%"=="" set DB_USER=postgres
if "%SERVER_PORT%"=="" set SERVER_PORT=8080
if "%FRONTEND_PORT%"=="" set FRONTEND_PORT=5173
if "%BACKEND_HOST%"=="" set BACKEND_HOST=127.0.0.1
if "%BACKEND_PORT%"=="" set BACKEND_PORT=8080
if "%JWT_SECRET%"=="" set JWT_SECRET=change-this-windows-pilot-secret-before-real-use

echo.
echo Starting Restaurant POS Windows Pilot...
echo Database: %DB_USER%@%DB_HOST%:%DB_PORT%/%DB_NAME%
echo Backend:  http://localhost:%SERVER_PORT%
echo Frontend: http://localhost:%FRONTEND_PORT%
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java 17+ is not installed or not in PATH.
  exit /b 1
)

where node >nul 2>nul
if errorlevel 1 (
  echo ERROR: Node.js is not installed or not in PATH.
  exit /b 1
)

start "Restaurant POS Backend" cmd /c "java -jar backend\restaurant-system-backend.jar --spring.profiles.active=pilot --spring.config.additional-location=file:config/application-pilot.yml > logs\backend.log 2>&1"
timeout /t 5 /nobreak >nul
start "Restaurant POS Frontend" cmd /c "node scripts\frontend-server.js > logs\frontend.log 2>&1"

echo.
echo Started. Run check-pos-windows.bat to see the Windows IP for Pad access.
echo Logs:
echo   logs\backend.log
echo   logs\frontend.log
echo.
endlocal
