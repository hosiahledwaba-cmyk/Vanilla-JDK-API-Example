@echo off
setlocal

echo ========================================================
echo   Robinhood Vanilla JDK Platform - Run
echo ========================================================

cd /d "%~dp0"

if not exist "bin\com\robinhood\Main.class" (
    echo Binaries not found. Building first...
    call build.bat
    if %ERRORLEVEL% neq 0 (
        echo Build failed, aborting run.
        exit /b %ERRORLEVEL%
    )
)

echo Starting Robinhood server via com.robinhood.Main...
echo Main location: api\src\com\robinhood\Main.java
echo.
java -cp "bin;api\src" com.robinhood.Main
