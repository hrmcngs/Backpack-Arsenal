@echo off
REM 本体MOD jar を origin の最新版に強制更新してから addon の runClient を実行する。
REM
REM 本体MOD jar はこのリポジトリに libs\local\...\the_four_primitives_and_weapons-1.20.1-test.jar
REM としてコミットされている。git fetch でその jar ファイルだけを origin の最新版で
REM 上書きする（addon のコードには触れない）。本体MOD のクローン/ビルドはしない。
REM
REM 使い方:
REM   run_client.bat                  jar を最新化してから実行
REM   run_client.bat --offline        git fetch せず手元の jar で実行
REM   run_client.bat -o               同上
setlocal
cd /d "%~dp0"

set "COMMON_FLAGS=-Dnet.minecraftforge.gradle.check.certs=false"
set "MAW_JAR=libs\local\the_four_primitives_and_weapons\1.20.1-test\the_four_primitives_and_weapons-1.20.1-test.jar"
set "MAW_JAR_GIT=libs/local/the_four_primitives_and_weapons/1.20.1-test/the_four_primitives_and_weapons-1.20.1-test.jar"

set "GRADLE_OPTS_EXTRA="
set "OFFLINE="
:ARG_LOOP
if "%~1"=="" goto ARG_DONE
if /i "%~1"=="-o"        set "OFFLINE=1" & set "GRADLE_OPTS_EXTRA=%GRADLE_OPTS_EXTRA% --offline" & goto ARG_NEXT
if /i "%~1"=="--offline" set "OFFLINE=1" & set "GRADLE_OPTS_EXTRA=%GRADLE_OPTS_EXTRA% --offline" & goto ARG_NEXT
set "GRADLE_OPTS_EXTRA=%GRADLE_OPTS_EXTRA% %~1"
:ARG_NEXT
shift
goto ARG_LOOP
:ARG_DONE

if defined OFFLINE (
    echo ==^> --offline: jar の更新をスキップ
) else (
    call :update_jar
)

if not exist "%MAW_JAR%" (
    echo [error] 本体MOD jar がありません: %MAW_JAR% 1>&2
    echo         オンライン環境で run_client.bat を実行して jar を取得してください。 1>&2
    exit /b 1
)

echo ==^> addon runClient %GRADLE_OPTS_EXTRA%
call gradlew.bat runClient %GRADLE_OPTS_EXTRA% %COMMON_FLAGS%
goto :eof

REM --- 本体MOD jar を origin の最新版で強制更新（jar ファイルのみ）---
:update_jar
git rev-parse --git-dir >nul 2>nul || (
    echo ==^> git 管理下でないため jar 更新をスキップ
    goto :eof
)
git fetch --quiet origin || (
    echo [warn] git fetch 失敗。手元の jar を使用します。
    goto :eof
)
set "UPSTREAM="
for /f "delims=" %%U in ('git rev-parse --abbrev-ref --symbolic-full-name "@{u}" 2^>nul') do set "UPSTREAM=%%U"
if not defined UPSTREAM set "UPSTREAM=origin/main"
echo ==^> 本体MOD jar を %UPSTREAM% から強制取得
git cat-file -e "%UPSTREAM%:%MAW_JAR_GIT%" 2>nul || (
    echo [warn] %UPSTREAM% に jar が無いため、手元の jar を使用します。
    goto :eof
)
git show "%UPSTREAM%:%MAW_JAR_GIT%" > "%MAW_JAR%"
echo     更新完了: %MAW_JAR%
goto :eof
