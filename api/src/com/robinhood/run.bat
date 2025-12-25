@echo off
setlocal

REM Set paths
set JAVA_HOME=E:\assets\java\jdk-24.0.1
set PATH=%JAVA_HOME%\bin;%PATH%
set JAVAFX_HOME=E:\assets\java\javafx-sdk-24.0.1

REM Compile
javac --module-path %JAVAFX_HOME%\lib --add-modules javafx.controls HelloWorld.java

REM Run
java --module-path %JAVAFX_HOME%\lib --add-modules javafx.controls HelloWorld

pause
