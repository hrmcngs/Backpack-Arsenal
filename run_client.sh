#!/bin/bash
# 本体MOD (the_four_primitives_and_weapons) をローカルでビルドして
# その jar を addon の libs/local 配下に配置し、addon の runClient を実行する。
#
# 旧版は git fetch で本体 MOD の jar を origin から取得していたが、
# 本体 MOD の最新ソースを反映させたい場合は git push → fetch のラウンドトリップが
# 必要だった。本版は本体 MOD のローカルソースから直接ビルドするので、
# 編集中のコードがそのまま addon の実行クライアントに反映される。
#
# 前提:
#   本体 MOD のソースは MAIN_MOD_DIR (下記) にあること。環境変数 MAIN_MOD_DIR で
#   オーバーライド可能。
#
# 使い方:
#   ./run_client.sh                  本体MODをビルドしてから実行
#   ./run_client.sh --offline        gradle --offline モードで実行
#                                    (本体MODビルドも addon runClient も --offline)
set -e

ADDON_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ADDON_DIR"
[ -x ./gradlew ] || chmod +x ./gradlew 2>/dev/null || true

COMMON_FLAGS="-Dnet.minecraftforge.gradle.check.certs=false"
MAW_JAR="libs/local/the_four_primitives_and_weapons/1.20.1-test/the_four_primitives_and_weapons-1.20.1-test.jar"

# 本体MOD のソースディレクトリ (環境変数で上書き可能)
MAIN_MOD_DIR="${MAIN_MOD_DIR:-/Users/hiromichi/The-four-primitives-and-Weapons}"

GRADLE_OPTS_EXTRA=""
OFFLINE=""
for arg in "$@"; do
    case "$arg" in
        -o|--offline) OFFLINE=1; GRADLE_OPTS_EXTRA="$GRADLE_OPTS_EXTRA --offline" ;;
        *)            GRADLE_OPTS_EXTRA="$GRADLE_OPTS_EXTRA $arg" ;;
    esac
done

# --- 本体MOD をビルドして jar を生成 ---------------------------------------
if [ ! -d "$MAIN_MOD_DIR" ]; then
    echo "[error] 本体MOD のソースディレクトリがありません: $MAIN_MOD_DIR" >&2
    echo "        MAIN_MOD_DIR 環境変数で別のパスを指定できます。" >&2
    exit 1
fi

if [ ! -x "$MAIN_MOD_DIR/gradlew" ]; then
    chmod +x "$MAIN_MOD_DIR/gradlew" 2>/dev/null || true
fi

echo "==> 本体MOD をビルド: $MAIN_MOD_DIR"
(
    cd "$MAIN_MOD_DIR"
    ./gradlew jar $GRADLE_OPTS_EXTRA $COMMON_FLAGS
)

# build/libs から最新の jar を拾う (sources/dev/javadoc jar は除外)
BUILT_JAR="$(ls -t "$MAIN_MOD_DIR/build/libs/"*.jar 2>/dev/null \
            | grep -v -E '(-sources|-dev|-javadoc)\.jar$' \
            | head -n1)"
if [ -z "$BUILT_JAR" ] || [ ! -f "$BUILT_JAR" ]; then
    echo "[error] ビルドに失敗 (jar が生成されていません)" >&2
    echo "        Expected: $MAIN_MOD_DIR/build/libs/*.jar" >&2
    exit 1
fi

mkdir -p "$(dirname "$MAW_JAR")"
cp -f "$BUILT_JAR" "$MAW_JAR"
echo "    ビルド完了: $BUILT_JAR"
echo "    コピー先  : $MAW_JAR"

# --- ForgeGradle deobf cache を invalidate --------------------------------
# fg.deobf("local:...") は固定 version (1.20.1-test) を引いているため、
# ソース jar の hash が変わっても deobf 結果のキャッシュが古いまま使われて
# 実行中のクライアントに本体MOD の変更が反映されないことがある。
# 該当 cache ディレクトリを丸ごと消して次のビルドで deobf を強制やり直しさせる。
DEOBF_CACHE_ROOTS=(
    "$HOME/.gradle/caches/forge_gradle/deobf_dependencies/local/the_four_primitives_and_weapons"
    "$HOME/.gradle/caches/forge_gradle/mod_remap_repo/local/the_four_primitives_and_weapons"
)
PURGED=0
for root in "${DEOBF_CACHE_ROOTS[@]}"; do
    if [ -d "$root" ]; then
        # 同一 artifact の全 mapping variant を削除 (= 1.20.1-test_mapped_official_1.20.1 等)
        find "$root" -mindepth 1 -maxdepth 1 -name '1.20.1-test*' -exec rm -rf {} + 2>/dev/null && PURGED=1
    fi
done
if [ "$PURGED" = 1 ]; then
    echo "    deobf cache を invalidate (本体MOD の変更を確実に反映)"
fi

# --- addon runClient ------------------------------------------------------
if [ ! -f "$MAW_JAR" ]; then
    echo "[error] 本体MOD jar の配置に失敗: $MAW_JAR" >&2
    exit 1
fi

echo "==> addon runClient$GRADLE_OPTS_EXTRA"
exec ./gradlew runClient $GRADLE_OPTS_EXTRA $COMMON_FLAGS
