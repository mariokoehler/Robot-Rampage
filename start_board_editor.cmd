@echo off
rem Local dev helper - runs the board editor (design.md 3.13), a developer tool that
rem opens and saves boards in assets/boards. Not part of the game or its releases.
setlocal
cd /d "%~dp0"

rem Always reinstall core fresh, not conditionally: dev-tools resolves core from the
rem local Maven repository, so a stale install silently runs old core code.
echo Installing core...
call mvn install -pl core -am -DskipTests -q
if errorlevel 1 (
    echo core failed to build - aborting.
    pause
    exit /b 1
)

echo Starting the board editor...
call mvn -pl dev-tools compile exec:exec
pause
