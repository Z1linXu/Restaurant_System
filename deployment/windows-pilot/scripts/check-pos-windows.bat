@echo off
setlocal

if "%SERVER_PORT%"=="" set SERVER_PORT=8080
if "%FRONTEND_PORT%"=="" set FRONTEND_PORT=5173

echo.
echo Restaurant POS Windows Pilot Health Check
echo ========================================
echo.
echo Local IPv4 addresses:
ipconfig | findstr /R /C:"IPv4"
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:%FRONTEND_PORT%/' -TimeoutSec 5; Write-Host ('Frontend OK: HTTP ' + [int]$r.StatusCode) } catch { Write-Host ('Frontend FAILED: ' + $_.Exception.Message) }"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:%FRONTEND_PORT%/api/v1/auth/me' -TimeoutSec 5 -SkipHttpErrorCheck; Write-Host ('Backend via frontend proxy OK: HTTP ' + [int]$r.StatusCode + ' (401 is OK when not logged in)') } catch { Write-Host ('Backend via frontend proxy FAILED: ' + $_.Exception.Message) }"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:%SERVER_PORT%/api/v1/auth/me' -TimeoutSec 5 -SkipHttpErrorCheck; Write-Host ('Backend direct OK: HTTP ' + [int]$r.StatusCode + ' (401 is OK when not logged in)') } catch { Write-Host ('Backend direct FAILED: ' + $_.Exception.Message) }"

echo.
echo Pad access URL:
echo   http://WINDOWS_FIXED_IP:%FRONTEND_PORT%/
echo.
echo Use the IPv4 address printed above that belongs to the restaurant LAN/Wi-Fi.
echo.
endlocal
