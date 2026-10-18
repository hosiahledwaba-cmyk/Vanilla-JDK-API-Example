@echo off
setlocal
cd /d "%~dp0..\..\..\.."
call "%~dp0..\..\..\..\run.bat" %*
exit /b %ERRORLEVEL%
