@echo off
set "JAVA_HOME=D:\zulu21.40.17-ca-jdk21.0.6-win_x64"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"

set "CACHE=%USERPROFILE%\.gradle\caches\minecraft"
mkdir "%CACHE%" >nul 2>&1

echo Prefetching Mojang version_manifest ...
powershell -NoProfile -Command "try { Invoke-WebRequest -Uri 'https://piston-meta.mojang.com/mc/game/version_manifest.json' -OutFile '%CACHE%\launcher_metadata.json' -UseBasicParsing } catch { Invoke-WebRequest -Uri 'https://bmclapi2.bangbang93.com/mc/game/version_manifest.json' -OutFile '%CACHE%\launcher_metadata.json' -UseBasicParsing }"
if errorlevel 1 (
  echo Failed to prefetch version_manifest.json. Check network/proxy.
  exit /b 1
)

if not exist "run\server\eula.txt" (
  mkdir "run\server" >nul 2>&1
  > "run\server\eula.txt" echo eula=true
)

echo Starting NeoForge runServer ...
call gradlew.bat runServer --no-configuration-cache %*
exit /b %ERRORLEVEL%
