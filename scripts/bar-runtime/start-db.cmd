@echo off
setlocal

set ROOT=%~dp0..
set DOCKER_DIR=%ROOT%\docker
set COMPOSE_FILE=%DOCKER_DIR%\docker-compose.yml
set DOCKER_DESKTOP=%ProgramFiles%\Docker\Docker\Docker Desktop.exe

if not exist "%DOCKER_DESKTOP%" (
  set DOCKER_DESKTOP=%ProgramFiles(x86)%\Docker\Docker\Docker Desktop.exe
)

where docker >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Docker no esta disponible en PATH.
  echo [ERROR] Instala Docker Desktop o ejecuta el instalador maestro completo.
  exit /b 1
)

if not exist "%COMPOSE_FILE%" (
  echo [ERROR] No existe %COMPOSE_FILE%
  exit /b 1
)

docker info >nul 2>nul
if errorlevel 1 (
  if exist "%DOCKER_DESKTOP%" (
    echo [INFO] Iniciando Docker Desktop...
    start "" "%DOCKER_DESKTOP%"
  )

  echo [INFO] Esperando a que Docker Engine este listo...
  for /l %%i in (1,1,120) do (
    docker info >nul 2>nul
    if not errorlevel 1 goto docker_ready
    timeout /t 2 >nul
  )

  echo [ERROR] Docker Engine no ha arrancado a tiempo.
  exit /b 1
)

:docker_ready
echo [INFO] Arrancando MySQL del bar...
pushd "%DOCKER_DIR%"
docker compose up -d mysql
if errorlevel 1 (
  popd
  echo [ERROR] No se pudo arrancar el contenedor mysql.
  exit /b 1
)
popd

echo [INFO] Esperando a que MySQL responda...
for /l %%i in (1,1,90) do (
  docker exec tpv-mysql mysqladmin ping -uroot -proot --silent >nul 2>nul
  if not errorlevel 1 goto mysql_ready
  timeout /t 2 >nul
)

echo [ERROR] MySQL no ha quedado listo a tiempo.
exit /b 1

:mysql_ready
echo [OK] Base de datos lista en 127.0.0.1:3306
endlocal
exit /b 0
