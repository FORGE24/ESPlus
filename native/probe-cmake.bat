@echo off
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul
cmake --help > "%~dp0cmake-gens.txt"
where ninja >> "%~dp0cmake-gens.txt" 2>&1
where cl >> "%~dp0cmake-gens.txt" 2>&1
echo DONE>> "%~dp0cmake-gens.txt"
