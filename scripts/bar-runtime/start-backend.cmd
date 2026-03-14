@echo off
setlocal

set ROOT=%~dp0..
set BACKEND_DIR=%ROOT%\backend
set LOG_DIR=%ROOT%\logs

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

if defined JAVA_HOME (
  set JAVA_BIN=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_BIN=java
)

where "%JAVA_BIN%" >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java no encontrado. Instala JDK 21 o define JAVA_HOME.
  exit /b 1
)

if "%DB_USERNAME%"=="" set DB_USERNAME=root
if "%DB_PASSWORD%"=="" set DB_PASSWORD=root
if "%JWT_SECRET%"=="" set JWT_SECRET=1234567890123456789012345678901234567890123456789012345678901234
if "%AUTH_DB_URL%"=="" set AUTH_DB_URL=jdbc:mysql://127.0.0.1:3306/tpv_auth?createDatabaseIfNotExist=true
if "%POS_DB_URL%"=="" set POS_DB_URL=jdbc:mysql://127.0.0.1:3306/tpv_pos?createDatabaseIfNotExist=true
if "%AUTH_SERVICE_URI%"=="" set AUTH_SERVICE_URI=http://127.0.0.1:8081
if "%POS_SERVICE_URI%"=="" set POS_SERVICE_URI=http://127.0.0.1:8082

if not exist "%BACKEND_DIR%\auth-service.jar" (
  echo [ERROR] No existe %BACKEND_DIR%\auth-service.jar
  exit /b 1
)
if not exist "%BACKEND_DIR%\pos-service.jar" (
  echo [ERROR] No existe %BACKEND_DIR%\pos-service.jar
  exit /b 1
)
if not exist "%BACKEND_DIR%\gateway.jar" (
  echo [ERROR] No existe %BACKEND_DIR%\gateway.jar
  exit /b 1
)

echo [INFO] Iniciando auth-service...
set DB_URL=%AUTH_DB_URL%
start "tpv-auth-service" /min cmd /c ""%JAVA_BIN%" -jar "%BACKEND_DIR%\auth-service.jar" > "%LOG_DIR%\auth-service.log" 2>&1"

echo [INFO] Iniciando pos-service...
set DB_URL=%POS_DB_URL%
start "tpv-pos-service" /min cmd /c ""%JAVA_BIN%" -jar "%BACKEND_DIR%\pos-service.jar" > "%LOG_DIR%\pos-service.log" 2>&1"

echo [INFO] Iniciando gateway...
start "tpv-gateway" /min cmd /c ""%JAVA_BIN%" -jar "%BACKEND_DIR%\gateway.jar" > "%LOG_DIR%\gateway.log" 2>&1"

echo [OK] Servicios iniciados (auth:8081, pos:8082, gateway:8080).
echo [INFO] Logs: %LOG_DIR%
endlocal
exit /b 0
