#!/bin/bash
# 本体MOD jar を origin の最新版に強制更新してから addon の runClient を実行する。
#
# 本体MOD jar はこのリポジトリに
#   libs/local/the_four_primitives_and_weapons/1.20.1-test/the_four_primitives_and_weapons-1.20.1-test.jar
# としてコミットされている。このスクリプトは git fetch でその jar ファイル "だけ" を
# origin の最新版で上書きする（addon のソースコードには一切触れない）。
# 本体MOD のクローンやビルドはしないので軽い。
#
# 使い方:
#   ./run_client.sh                  jar を最新化してから実行
#   ./run_client.sh --offline        git fetch せず、手元の jar で実行
#   ./run_client.sh -o               同上
set -e

ADDON_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ADDON_DIR"
[ -x ./gradlew ] || chmod +x ./gradlew 2>/dev/null || true

COMMON_FLAGS="-Dnet.minecraftforge.gradle.check.certs=false"
MAW_JAR="libs/local/the_four_primitives_and_weapons/1.20.1-test/the_four_primitives_and_weapons-1.20.1-test.jar"

GRADLE_OPTS_EXTRA=""
OFFLINE=""
for arg in "$@"; do
    case "$arg" in
        -o|--offline) OFFLINE=1; GRADLE_OPTS_EXTRA="$GRADLE_OPTS_EXTRA --offline" ;;
        *)            GRADLE_OPTS_EXTRA="$GRADLE_OPTS_EXTRA $arg" ;;
    esac
done

# --- 本体MOD jar を origin の最新版で強制更新（jar ファイルのみ）---------
if [ -n "$OFFLINE" ]; then
    echo "==> --offline: jar の更新をスキップ。手元の jar を使用します"
elif ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo "==> git 管理下でないため jar 更新をスキップ。手元の jar を使用します"
else
    upstream="$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null || true)"
    [ -n "$upstream" ] || upstream="origin/main"
    echo "==> 本体MOD jar を $upstream から強制取得"
    if git fetch --quiet origin 2>/dev/null && git cat-file -e "$upstream:$MAW_JAR" 2>/dev/null; then
        tmp="$(mktemp)"
        if git show "$upstream:$MAW_JAR" > "$tmp" 2>/dev/null; then
            mv "$tmp" "$MAW_JAR"
            echo "    更新完了: $MAW_JAR"
        else
            rm -f "$tmp"
            echo "[warn] jar の取り出しに失敗。手元の jar を使用します"
        fi
    else
        echo "[warn] jar を取得できません（オフライン / origin に jar 無し）。手元の jar を使用します"
    fi
fi

if [ ! -f "$MAW_JAR" ]; then
    echo "[error] 本体MOD jar がありません: $MAW_JAR" >&2
    echo "        オンライン環境で ./run_client.sh を実行して jar を取得してください。" >&2
    exit 1
fi

echo "==> addon runClient$GRADLE_OPTS_EXTRA"
exec ./gradlew runClient $GRADLE_OPTS_EXTRA $COMMON_FLAGS
