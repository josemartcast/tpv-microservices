@echo off
setlocal

set ROOT=%~dp0..
set MYSQL_BIN=%ROOT%\mysql\bin
set MYSQLADMIN=%MYSQL_BIN%\mysqladmin.exe
set SERVICE_NAME=TPVMySQL
if "%MYSQL_ROOT_PASSWORD%"=="" set MYSQL_ROOT_PASSWORD=root

if not exist "%MYSQLADMIN%" (
  echo [ERROR] No existe %MYSQLADMIN%
  echo [ERROR] MySQL no parece instalado en %ROOT%\mysql
  exit /b 1
)

sc query "%SERVICE_NAME%" >nul 2>nul
if errorlevel 1 (
  echo [ERROR] No existe el servicio %SERVICE_NAME%.
  exit /b 1
)

sc query "%SERVICE_NAME%" | findstr /I "RUNNING" >nul
if errorlevel 1 (
  echo [INFO] Iniciando servicio %SERVICE_NAME%...
  net start "%SERVICE_NAME%" >nul
  if errorlevel 1 (
    echo [ERROR] No se pudo iniciar el servicio %SERVICE_NAME%.
    exit /b 1
  )
)

echo [INFO] Esperando a que MySQL responda...
for /l %%i in (1,1,60) do (
  "%MYSQLADMIN%" --host=localhost --port=3306 --user=root --password=%MYSQL_ROOT_PASSWORD% ping --silent >nul 2>nul
  if not errorlevel 1 goto mysql_ready
  timeout /t 2 >nul
)

echo [ERROR] MySQL no ha quedado listo a tiempo.
exit /b 1

:mysql_ready
echo [OK] Base de datos lista en 127.0.0.1:3306
endlocal
exit /b 0
