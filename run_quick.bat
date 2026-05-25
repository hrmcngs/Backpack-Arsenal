@echo off
REM 手元の本体MOD jar のまま addon の runClient を実行する（最速・ネットワーク不要）。
REM
REM jar の最新化はしない。最新の本体MOD jar を取得したいときは run_client.bat を使う。
REM
REM 使い方:
REM   run_quick.bat                  実行
REM   run_quick.bat --offline        オフライン実行
REM   run_quick.bat -o               同上
setlocal
cd /d "%~dp0"

set "COMMON_FLAGS=-Dnet.minecraftforge.gradle.check.certs=false"
set "MAW_JAR=libs\local\the_four_primitives_and_weapons\1.20.1-test\the_four_primitives_and_weapons-1.20.1-test.jar"

set "GRADLE_OPTS_EXTRA="
:ARG_LOOP
if "%~1"=="" goto ARG_DONE
if /i "%~1"=="-o"        set "GRADLE_OPTS_EXTRA=%GRADLE_OPTS_EXTRA% --offline" & goto ARG_NEXT
if /i "%~1"=="--offline" set "GRADLE_OPTS_EXTRA=%GRADLE_OPTS_EXTRA% --offline" & goto ARG_NEXT
set "GRADLE_OPTS_EXTRA=%GRADLE_OPTS_EXTRA% %~1"
:ARG_NEXT
shift
goto ARG_LOOP
:ARG_DONE

if not exist "%MAW_JAR%" (
    echo [error] 本体MOD jar がありません: %MAW_JAR% 1>&2
    echo         run_client.bat を実行して jar を取得してください。 1>&2
    exit /b 1
)

echo ==^> addon runClient (jar は最新化しない) %GRADLE_OPTS_EXTRA%
call gradlew.bat runClient %GRADLE_OPTS_EXTRA% %COMMON_FLAGS%
