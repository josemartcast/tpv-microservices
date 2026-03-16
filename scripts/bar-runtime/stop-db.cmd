@echo off
setlocal

set SERVICE_NAME=TPVMySQL

sc query "%SERVICE_NAME%" >nul 2>nul
if errorlevel 1 (
  echo [WARN] No existe el servicio %SERVICE_NAME%.
  exit /b 0
)

sc query "%SERVICE_NAME%" | findstr /I "STOPPED" >nul
if not errorlevel 1 (
  echo [OK] MySQL ya estaba detenido.
  exit /b 0
)

echo [INFO] Deteniendo servicio %SERVICE_NAME%...
net stop "%SERVICE_NAME%" >nul
if errorlevel 1 (
  echo [WARN] No se pudo detener %SERVICE_NAME%.
  exit /b 0
)

echo [OK] MySQL detenido (%SERVICE_NAME%).
endlocal
exit /b 0
