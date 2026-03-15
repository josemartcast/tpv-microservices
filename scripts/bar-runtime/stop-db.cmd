@echo off
setlocal

set ROOT=%~dp0..
set DOCKER_DIR=%ROOT%\docker
set COMPOSE_FILE=%DOCKER_DIR%\docker-compose.yml

where docker >nul 2>nul
if errorlevel 1 (
  echo [WARN] Docker no esta disponible en PATH.
  exit /b 0
)

if not exist "%COMPOSE_FILE%" (
  echo [WARN] No existe %COMPOSE_FILE%
  exit /b 0
)

pushd "%DOCKER_DIR%"
docker compose stop mysql
if errorlevel 1 (
  popd
  echo [WARN] No se pudo detener mysql.
  exit /b 0
)
popd

echo [OK] MySQL detenido.
endlocal
exit /b 0
