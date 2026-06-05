@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%target\aaron-1.0.0.jar"

if not exist "%JAR%" (
    call :build
    if errorlevel 1 exit /b 1
)

java -jar "%JAR%" %*
exit /b %ERRORLEVEL%

:build
echo   *  building aaron ^(first run^)... 1>&2
pushd "%SCRIPT_DIR%"
call mvnw.cmd -q clean package -DskipTests
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
