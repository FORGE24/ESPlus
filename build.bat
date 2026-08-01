@echo off
set "JAVA_HOME=D:\zulu21.40.17-ca-jdk21.0.6-win_x64"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"

if "%~1"=="" (
  echo Usage:
  echo   build.bat panel     - test + bootJar panel
  echo   build.bat mod       - test + build mod + copy panel jar
  echo   build.bat all       - panel then mod
  exit /b 1
)

if /I "%~1"=="panel" (
  call gradlew.bat -p panel test bootJar --no-daemon --no-configuration-cache
  exit /b %ERRORLEVEL%
)

if /I "%~1"=="mod" (
  call gradlew.bat test build copyPanelJar --no-daemon --no-configuration-cache
  exit /b %ERRORLEVEL%
)

if /I "%~1"=="all" (
  call gradlew.bat -p panel test bootJar --no-daemon --no-configuration-cache || exit /b %ERRORLEVEL%
  call gradlew.bat test build copyPanelJar --no-daemon --no-configuration-cache
  exit /b %ERRORLEVEL%
)

echo Unknown target: %~1
exit /b 1
