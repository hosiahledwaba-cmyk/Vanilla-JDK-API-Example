@echo off
setlocal

echo ========================================================
echo   Robinhood Vanilla JDK Platform - Build
echo ========================================================

cd /d "%~dp0"

if not exist "bin" mkdir bin

echo Compiling Java source files (api and engine)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$files = Get-ChildItem -Recurse -Filter *.java api\src, engine\src | Select-Object -ExpandProperty FullName; javac -encoding UTF-8 -d bin $files"

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] Compilation successful! Output stored in .\bin
) else (
    echo.
    echo [ERROR] Compilation failed with error level %ERRORLEVEL%.
)
