@echo off
setlocal

if "%SERVER_PORT%"=="" set SERVER_PORT=8080
if "%FRONTEND_PORT%"=="" set FRONTEND_PORT=5173

echo Stopping Restaurant POS processes on ports %SERVER_PORT% and %FRONTEND_PORT%...

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ports=@(%SERVER_PORT%,%FRONTEND_PORT%); foreach($port in $ports){ $lines=netstat -ano ^| Select-String (':' + $port + '\s'); foreach($line in $lines){ $parts=($line -split '\s+') ^| Where-Object { $_ }; $pid=$parts[-1]; if($pid -match '^\d+$'){ Write-Host ('Stopping PID ' + $pid + ' on port ' + $port); Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue } } }"

echo Done.
endlocal
