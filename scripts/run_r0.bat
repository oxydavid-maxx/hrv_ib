@echo off
setlocal enabledelayedexpansion

set ROOT_DIR=%~dp0..
pushd "%ROOT_DIR%"

set SDK_PATH=

if exist "local.properties" (
  for /f "tokens=1,* delims==" %%A in (local.properties) do (
    if "%%A"=="sdk.dir" set SDK_PATH=%%B
  )
)

if not defined SDK_PATH (
  if defined ANDROID_SDK_ROOT set SDK_PATH=%ANDROID_SDK_ROOT%
)
if not defined SDK_PATH (
  if defined ANDROID_HOME set SDK_PATH=%ANDROID_HOME%
)

if not defined SDK_PATH goto :no_sdk
if not exist "%SDK_PATH%" goto :no_sdk

echo [R0 preflight] Android SDK: %SDK_PATH%
call .\gradlew.bat assembleDebug assembleRelease test lint connectedDebugAndroidTest --stacktrace
set EXIT_CODE=%ERRORLEVEL%
popd
exit /b %EXIT_CODE%

:no_sdk
echo [R0 preflight] Android SDK not found.
echo.
echo To run R0 locally, configure one of:
echo 1) local.properties in project root:
echo    sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk
echo 2) env var:
echo    setx ANDROID_SDK_ROOT "C:\\Users\\you\\AppData\\Local\\Android\\Sdk"
echo    ^(or ANDROID_HOME^)
echo.
echo If you do not want to install SDK locally, use GitHub Actions CI.
echo CI is the authoritative R0 gate in this repository.
popd
exit /b 2
