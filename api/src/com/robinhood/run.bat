@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo   Robinhood Vanilla JDK Platform - Build and Run
echo ========================================================

REM Find repository root from api\src\com\robinhood
cd /d "%~dp0..\..\..\.."

echo [1/3] Creating output directory...
if not exist "bin" mkdir bin

echo [2/3] Compiling pure Vanilla Java project...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$files = Get-ChildItem -Recurse -Filter *.java api\src, engine\src | Select-Object -ExpandProperty FullName; javac -encoding UTF-8 -d bin $files"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [3/3] Running com.robinhood.Main...
echo.
java -cp "bin;api\src" com.robinhood.Main

pause
