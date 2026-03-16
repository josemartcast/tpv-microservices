@echo off
setlocal

set ROOT=%~dp0..
set SCRIPTS_DIR=%ROOT%\scripts

call "%SCRIPTS_DIR%\start-db.cmd"
if errorlevel 1 (
  echo [ERROR] No se pudo arrancar la base de datos.
  echo [HINT] Verifica que exista el servicio TPVMySQL e instala prerequisitos con permisos de administrador.
  echo [HINT] Script: C:\TPV-Bar\scripts\install-portatil-prereqs.ps1
  echo.
  pause
  exit /b 1
)

call "%SCRIPTS_DIR%\start-backend.cmd"
if errorlevel 1 (
  echo [ERROR] No se pudieron arrancar los servicios.
  echo.
  pause
  exit /b 1
)

set DESKTOP_EXE=%ProgramFiles%\TPV-Desktop\TPV-Desktop.exe
if not exist "%DESKTOP_EXE%" (
  set DESKTOP_EXE=%ProgramFiles(x86)%\TPV-Desktop\TPV-Desktop.exe
)

if exist "%DESKTOP_EXE%" (
  echo [INFO] Lanzando TPV Desktop: %DESKTOP_EXE%
  start "" "%DESKTOP_EXE%"
) else (
  echo [WARN] TPV Desktop no instalado en ruta por defecto.
  echo [WARN] Ejecuta primero el instalador EXE en .\installers\
)

endlocal
exit /b 0
