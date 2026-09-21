@echo off
rem Local dev helper - runs the dedicated server directly (CLAUDE.md's documented
rem `exec:java` workflow), without having to remember the exact Maven commands.
setlocal
cd /d "%~dp0"

rem Always reinstall core fresh, not conditionally: server resolves core from the
rem local Maven repository, so a stale install silently runs old core code.
echo Installing core...
call mvn install -pl core -am -DskipTests -q
if errorlevel 1 (
    echo core failed to build - aborting.
    pause
    exit /b 1
)

echo Starting server...
call mvn -pl server compile exec:java
pause
