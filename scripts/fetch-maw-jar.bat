@echo off
REM ====================================================================
REM 本体MOD「The four primitives and Weapons」の jar を用意する
REM   1. libs\local\ に jar があれば何もしない (--force で再取得)
REM   2. ローカルに本体MODソースがあればビルド
REM   3. 無ければ GitHub から clone してビルド
REM      https://github.com/Drowse-Lab/The-four-primitives-and-Weapons
REM
REM 使い方:
REM   scripts\fetch-maw-jar.bat            jar が無ければ用意
REM   scripts\fetch-maw-jar.bat --force    既存 jar があっても作り直す
REM   scripts\fetch-maw-jar.bat --offline  clone しない
REM ====================================================================
setlocal

set "MAW_ARTIFACT=the_four_primitives_and_weapons"
set "MAW_VERSION=1.20.1-test"
if "%MAW_REPO_URL%"==""    set "MAW_REPO_URL=https://github.com/Drowse-Lab/The-four-primitives-and-Weapons"
if "%MAW_REPO_BRANCH%"=="" set "MAW_REPO_BRANCH=1.20.1"

pushd "%~dp0.."
set "ADDON_DIR=%CD%"
popd
set "DEST_DIR=%ADDON_DIR%\libs\local\%MAW_ARTIFACT%\%MAW_VERSION%"
set "DEST_JAR=%DEST_DIR%\%MAW_ARTIFACT%-%MAW_VERSION%.jar"
REM clone先は addon プロジェクトの外に置く（addon 配下だと本体MODビルド時に
REM Gradle が親の settings.gradle を拾ってビルドエラーになる）
if "%MAW_CLONE_DIR%"=="" set "MAW_CLONE_DIR=%LOCALAPPDATA%\maw-mod-src"
set "CLONE_DIR=%MAW_CLONE_DIR%"

set "FORCE="
set "OFFLINE="
set "GRADLE_EXTRA="
:ARG_LOOP
if "%~1"=="" goto ARG_DONE
if /i "%~1"=="-f"        set "FORCE=1" & goto ARG_NEXT
if /i "%~1"=="--force"   set "FORCE=1" & goto ARG_NEXT
if /i "%~1"=="-o"        set "OFFLINE=1" & set "GRADLE_EXTRA=%GRADLE_EXTRA% --offline" & goto ARG_NEXT
if /i "%~1"=="--offline" set "OFFLINE=1" & set "GRADLE_EXTRA=%GRADLE_EXTRA% --offline" & goto ARG_NEXT
set "GRADLE_EXTRA=%GRADLE_EXTRA% %~1"
:ARG_NEXT
shift
goto ARG_LOOP
:ARG_DONE

if exist "%DEST_JAR%" if not defined FORCE (
    echo [fetch-maw] jar は既にあります: %DEST_JAR%
    echo [fetch-maw] 作り直す場合: scripts\fetch-maw-jar.bat --force
    exit /b 0
)

REM --- 本体MODソースを探す ---
set "MAW_SRC="
if not "%MAW_DIR%"=="" if exist "%MAW_DIR%\gradlew.bat" set "MAW_SRC=%MAW_DIR%"
if not defined MAW_SRC if exist "%USERPROFILE%\The-four-primitives-and-Weapons\gradlew.bat" set "MAW_SRC=%USERPROFILE%\The-four-primitives-and-Weapons"

if defined MAW_SRC (
    echo [fetch-maw] ローカルの本体MODソースを使用: %MAW_SRC%
) else (
    if defined OFFLINE (
        echo [fetch-maw] エラー: --offline 指定だが本体MODソースが見つかりません。 1>&2
        exit /b 1
    )
    where git >nul 2>nul
    if errorlevel 1 (
        echo [fetch-maw] エラー: git が見つかりません。 1>&2
        exit /b 1
    )
    if exist "%CLONE_DIR%\.git" (
        echo [fetch-maw] 既存の clone を更新: %CLONE_DIR%
        git -C "%CLONE_DIR%" fetch --depth 1 origin %MAW_REPO_BRANCH% || exit /b 1
        git -C "%CLONE_DIR%" reset --hard FETCH_HEAD || exit /b 1
    ) else (
        echo [fetch-maw] GitHub から clone: %MAW_REPO_URL% ^(%MAW_REPO_BRANCH%^)
        if exist "%CLONE_DIR%" rmdir /s /q "%CLONE_DIR%"
        git clone --depth 1 --branch %MAW_REPO_BRANCH% "%MAW_REPO_URL%" "%CLONE_DIR%" || exit /b 1
    )
    set "MAW_SRC=%CLONE_DIR%"
)

if not exist "%MAW_SRC%\gradlew.bat" (
    echo [fetch-maw] エラー: %MAW_SRC% に gradlew.bat がありません。 1>&2
    exit /b 1
)

echo [fetch-maw] 本体MODをビルド中... ^(初回は数分かかります^)
pushd "%MAW_SRC%"
call gradlew.bat build --no-daemon -Dnet.minecraftforge.gradle.check.certs=false %GRADLE_EXTRA%
set "BUILD_RC=%ERRORLEVEL%"
popd
if not "%BUILD_RC%"=="0" exit /b %BUILD_RC%

REM --- 最新の配布用 jar を探してコピー（dir /o-d で新しい順）---
set "LATEST="
for /f "delims=" %%F in ('dir /b /o-d "%MAW_SRC%\build\libs\*.jar" 2^>nul') do (
    if not defined LATEST (
        echo %%~nF| findstr /i /e /l "sources dev javadoc slim" >nul || set "LATEST=%%F"
    )
)
if not defined LATEST (
    echo [fetch-maw] エラー: build\libs に配布用 jar が見つかりません。 1>&2
    exit /b 1
)
if not exist "%DEST_DIR%" mkdir "%DEST_DIR%"
copy /y "%MAW_SRC%\build\libs\%LATEST%" "%DEST_JAR%" >nul
echo [fetch-maw] 完了: %LATEST%
echo [fetch-maw]   -^> %DEST_JAR%
exit /b 0
