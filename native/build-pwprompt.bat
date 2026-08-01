@echo off
setlocal EnableExtensions
cd /d "%~dp0pwprompt"

set "QT_ROOT=D:\Qt\6.11.1\msvc2022_64"
set "VCVARS=C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS%" (
  echo Missing vcvars64.bat
  exit /b 1
)
if not exist "%QT_ROOT%\bin\qmake.exe" (
  echo Missing Qt at %QT_ROOT%
  exit /b 1
)

call "%VCVARS%" || exit /b 1

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build
mkdir dist

cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_PREFIX_PATH=%QT_ROOT%
if errorlevel 1 exit /b 1

cmake --build build
if errorlevel 1 exit /b 1

copy /Y build\esplus-pwprompt.exe dist\ >nul
if errorlevel 1 exit /b 1

"%QT_ROOT%\bin\windeployqt.exe" --release --no-translations --no-system-d3d-compiler --no-opengl-sw dist\esplus-pwprompt.exe
if errorlevel 1 exit /b 1

echo Built: %CD%\dist\esplus-pwprompt.exe
exit /b 0
