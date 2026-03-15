@echo off
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo La instalacion ha fallado con codigo %EXIT_CODE%.
  pause
)

exit /b %EXIT_CODE%
