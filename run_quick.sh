#!/bin/bash
# 手元の本体MOD jar のまま addon の runClient を実行する（最速・ネットワーク不要）。
#
# jar の最新化はしない。最新の本体MOD jar を取得したいときは ./run_client.sh を使う。
#
# 使い方:
#   ./run_quick.sh                  実行
#   ./run_quick.sh --offline        オフライン実行
#   ./run_quick.sh -o               同上
set -e

ADDON_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ADDON_DIR"
[ -x ./gradlew ] || chmod +x ./gradlew 2>/dev/null || true

COMMON_FLAGS="-Dnet.minecraftforge.gradle.check.certs=false"
MAW_JAR="libs/local/the_four_primitives_and_weapons/1.20.1-test/the_four_primitives_and_weapons-1.20.1-test.jar"

GRADLE_OPTS_EXTRA=""
for arg in "$@"; do
    case "$arg" in
        -o|--offline) GRADLE_OPTS_EXTRA="$GRADLE_OPTS_EXTRA --offline" ;;
        *)            GRADLE_OPTS_EXTRA="$GRADLE_OPTS_EXTRA $arg" ;;
    esac
done

if [ ! -f "$MAW_JAR" ]; then
    echo "[error] 本体MOD jar がありません: $MAW_JAR" >&2
    echo "        ./run_client.sh を実行して jar を取得してください。" >&2
    exit 1
fi

echo "==> addon runClient (jar は最新化しない)$GRADLE_OPTS_EXTRA"
exec ./gradlew runClient $GRADLE_OPTS_EXTRA $COMMON_FLAGS
