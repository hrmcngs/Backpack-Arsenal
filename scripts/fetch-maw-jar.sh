#!/bin/bash
# =====================================================================
# 本体MOD「The four primitives and Weapons」の jar を用意するスクリプト
#
# 動作（上から順に試す）:
#   1. libs/local/ に jar が既にあれば何もしない（--force で強制再取得）
#   2. ローカルに本体MODソースがあれば、それをビルドして取り込む
#   3. どちらも無ければ GitHub から clone してビルドする
#        https://github.com/Drowse-Lab/The-four-primitives-and-Weapons
#
# 「Use this template」で複製しただけの環境でも、これだけで jar が揃う。
#
# 使い方:
#   scripts/fetch-maw-jar.sh            jar が無ければ用意する
#   scripts/fetch-maw-jar.sh --force    既存 jar があっても作り直す
#   scripts/fetch-maw-jar.sh --offline  clone しない（既存ソース/jar のみ）
# =====================================================================
set -e

# --- 設定（build.gradle の maw_artifact / maw_version と一致させること）--
MAW_REPO_URL="${MAW_REPO_URL:-https://github.com/Drowse-Lab/The-four-primitives-and-Weapons}"
MAW_REPO_BRANCH="${MAW_REPO_BRANCH:-1.20.1}"
MAW_ARTIFACT="the_four_primitives_and_weapons"
MAW_VERSION="1.20.1-test"

# --- パス ---------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ADDON_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST_DIR="$ADDON_DIR/libs/local/$MAW_ARTIFACT/$MAW_VERSION"
DEST_JAR="$DEST_DIR/$MAW_ARTIFACT-$MAW_VERSION.jar"
# clone先は addon プロジェクトの外に置くこと。addon 配下（.maw-src 等）に
# clone すると、本体MODをビルドするとき Gradle が親の settings.gradle を拾い
#   Project directory '.../.maw-src' is not part of the build ...
# というエラーになりビルドできない。
CLONE_DIR="${MAW_CLONE_DIR:-$HOME/.cache/maw-mod-src}"

# --- 引数 ---------------------------------------------------------------
FORCE=""
OFFLINE=""
GRADLE_EXTRA=""
for arg in "$@"; do
    case "$arg" in
        -f|--force)   FORCE=1 ;;
        -o|--offline) OFFLINE=1; GRADLE_EXTRA="$GRADLE_EXTRA --offline" ;;
        *)            GRADLE_EXTRA="$GRADLE_EXTRA $arg" ;;
    esac
done

# --- 既存 jar チェック --------------------------------------------------
if [ -f "$DEST_JAR" ] && [ -z "$FORCE" ]; then
    echo "[fetch-maw] jar は既にあります: ${DEST_JAR#"$ADDON_DIR"/}"
    echo "[fetch-maw] 作り直す場合: scripts/fetch-maw-jar.sh --force"
    exit 0
fi

# --- 本体MODソースの場所を判定 ------------------------------------------
#   1. 環境変数 MAW_DIR
#   2. gradle.properties の mawSourceProject
#   3. $HOME/The-four-primitives-and-Weapons（標準配置）
#   4. addon の親 / 祖父フォルダ
resolve_maw_src() {
    if [ -n "$MAW_DIR" ] && [ -f "$MAW_DIR/gradlew" ]; then
        echo "$MAW_DIR"; return
    fi
    if [ -f "$ADDON_DIR/gradle.properties" ]; then
        local prop
        prop=$(sed -n 's/^[[:space:]]*mawSourceProject[[:space:]]*=[[:space:]]*//p' \
               "$ADDON_DIR/gradle.properties" | head -n1)
        if [ -n "$prop" ] && [ -f "$prop/gradlew" ]; then echo "$prop"; return; fi
    fi
    local parent grandparent
    parent="$(dirname "$ADDON_DIR")"
    grandparent="$(dirname "$parent")"
    for cand in \
        "$HOME/The-four-primitives-and-Weapons" \
        "$parent/The-four-primitives-and-Weapons" \
        "$grandparent/The-four-primitives-and-Weapons"
    do
        if [ -d "$cand" ] && [ -f "$cand/gradlew" ]; then echo "$cand"; return; fi
    done
}
MAW_SRC="$(resolve_maw_src || true)"

# --- ソースが無ければ GitHub から clone --------------------------------
if [ -n "$MAW_SRC" ]; then
    MAW_SRC="$(cd "$MAW_SRC" && pwd)"
    echo "[fetch-maw] ローカルの本体MODソースを使用: $MAW_SRC"
else
    if [ -n "$OFFLINE" ]; then
        echo "[fetch-maw] エラー: --offline 指定だが本体MODソースが見つかりません。" >&2
        echo "[fetch-maw]   先にオンラインで scripts/fetch-maw-jar.sh を実行するか、" >&2
        echo "[fetch-maw]   本体MODを \$HOME/The-four-primitives-and-Weapons に置いてください。" >&2
        exit 1
    fi
    if ! command -v git >/dev/null 2>&1; then
        echo "[fetch-maw] エラー: git が見つかりません。git をインストールしてください。" >&2
        exit 1
    fi
    if [ -d "$CLONE_DIR/.git" ]; then
        echo "[fetch-maw] 既存の clone を更新: $CLONE_DIR ($MAW_REPO_BRANCH)"
        git -C "$CLONE_DIR" fetch --depth 1 origin "$MAW_REPO_BRANCH"
        git -C "$CLONE_DIR" reset --hard FETCH_HEAD
    else
        echo "[fetch-maw] GitHub から clone: $MAW_REPO_URL ($MAW_REPO_BRANCH)"
        echo "[fetch-maw]   clone先: $CLONE_DIR"
        rm -rf "$CLONE_DIR"
        mkdir -p "$(dirname "$CLONE_DIR")"
        # 自己参照する submodule が定義されているため --recurse-submodules は使わない
        git clone --depth 1 --branch "$MAW_REPO_BRANCH" "$MAW_REPO_URL" "$CLONE_DIR"
    fi
    MAW_SRC="$CLONE_DIR"
fi

# --- 本体MODをビルド ----------------------------------------------------
if [ ! -f "$MAW_SRC/gradlew" ]; then
    echo "[fetch-maw] エラー: $MAW_SRC に gradlew がありません。" >&2
    exit 1
fi
chmod +x "$MAW_SRC/gradlew" 2>/dev/null || true
echo "[fetch-maw] 本体MODをビルド中... (初回は数分かかります)"
( cd "$MAW_SRC" && ./gradlew build --no-daemon \
    -Dnet.minecraftforge.gradle.check.certs=false $GRADLE_EXTRA )

# --- ビルド成果物の jar を探す（sources / dev / javadoc を除く最新）-----
latest=""
for j in "$MAW_SRC"/build/libs/*.jar; do
    [ -e "$j" ] || continue
    case "$j" in
        *-sources.jar|*-dev.jar|*-javadoc.jar|*-slim.jar) continue ;;
    esac
    if [ -z "$latest" ] || [ "$j" -nt "$latest" ]; then
        latest="$j"
    fi
done
if [ -z "$latest" ]; then
    echo "[fetch-maw] エラー: $MAW_SRC/build/libs に配布用 jar が見つかりません。" >&2
    exit 1
fi

# --- libs/local/ に取り込む（build.gradle が参照する固定パス）----------
mkdir -p "$DEST_DIR"
cp "$latest" "$DEST_JAR"
echo "[fetch-maw] 完了: $(basename "$latest")"
echo "[fetch-maw]   → ${DEST_JAR#"$ADDON_DIR"/}"
