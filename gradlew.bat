@echo off
setlocal enabledelayedexpansion

set DIR=%~dp0
set GRADLE_VERSION=8.10.2
set DIST_DIR=%DIR%\.gradle-dist
set INSTALL_DIR=%DIST_DIR%\gradle-%GRADLE_VERSION%
set ARCHIVE=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set JDK_DIR=%DIR%\.jdk17
set JDK_ARCHIVE=%JDK_DIR%\jdk17.zip

if not exist "%JDK_DIR%\bin\java.exe" (
  if not exist "%JDK_DIR%" mkdir "%JDK_DIR%"
  if not exist "%JDK_ARCHIVE%" (
    echo Downloading JDK 17...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile '%JDK_ARCHIVE%'"
  )
  echo Extracting JDK 17...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Expand-Archive -LiteralPath '%JDK_ARCHIVE%' -DestinationPath '%JDK_DIR%' -Force; $folder = Get-ChildItem '%JDK_DIR%' | Where-Object { $_.PSIsContainer -and $_.Name -like 'jdk-*' } | Select-Object -First 1; if ($folder) { Move-Item -Path ($folder.FullName + '\*') -Destination '%JDK_DIR%' -Force; Remove-Item $folder.FullName -Recurse -Force }"
)

set JAVA_HOME=%JDK_DIR%
set PATH=%JAVA_HOME%\bin;%PATH%

if not exist "%INSTALL_DIR%" (
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  if not exist "%ARCHIVE%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ARCHIVE%'"
  )
  echo Extracting Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Expand-Archive -LiteralPath '%ARCHIVE%' -DestinationPath '%DIST_DIR%' -Force"
)

call "%INSTALL_DIR%\bin\gradle.bat" %*
exit /b %errorlevel%
