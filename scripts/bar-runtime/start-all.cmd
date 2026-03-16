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

set "DESKTOP_INPUT=%ROOT%\_desktop_input"
set "JAVA_UI_BIN="
if exist "%ROOT%\jdk\bin\javaw.exe" (
  set "JAVA_UI_BIN=%ROOT%\jdk\bin\javaw.exe"
) else if exist "%ROOT%\jdk\bin\java.exe" (
  set "JAVA_UI_BIN=%ROOT%\jdk\bin\java.exe"
)

if exist "%DESKTOP_INPUT%\tpv-desktop-1.0.0.jar" if not "%JAVA_UI_BIN%"=="" (
  echo [INFO] Lanzando TPV Desktop desde paquete local...
  start "tpv-desktop-ui" "%JAVA_UI_BIN%" --module-path "%DESKTOP_INPUT%" --add-modules javafx.controls,javafx.fxml -cp "%DESKTOP_INPUT%\*" -Dtpv.mode=real -Dtpv.auto.login=true -Dtpv.auth.user=admin -Dtpv.auth.pass=admin123 -Dfile.encoding=UTF-8 com.tpv.desktop.App
) else (
  set "DESKTOP_EXE=%ProgramFiles%\TPV-Desktop\TPV-Desktop.exe"
  if not exist "%DESKTOP_EXE%" (
    set "DESKTOP_EXE=%ProgramFiles(x86)%\TPV-Desktop\TPV-Desktop.exe"
  )
  if exist "%DESKTOP_EXE%" (
    echo [INFO] Lanzando TPV Desktop instalado: %DESKTOP_EXE%
    start "tpv-desktop-ui" "%DESKTOP_EXE%"
  ) else (
    echo [WARN] No se encontro launcher de TPV Desktop.
    echo [WARN] Revisa C:\TPV-Bar\_desktop_input o reinstala TPV Desktop.
  )
)

endlocal
exit /b 0
