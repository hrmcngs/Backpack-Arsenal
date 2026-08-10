#!/usr/bin/env bash
set -euo pipefail

# ForgeGradle downloads some libraries into its own cache, outside Gradle's
# normal dependency cache. A transient HTML/error response can therefore be
# saved with a .jar extension and fail later with "zip END header not found".
forge_cache="${GRADLE_USER_HOME:-${HOME}/.gradle}/caches/forge_gradle"
build_log="$(mktemp "${RUNNER_TEMP:-/tmp}/backpack-arsenal-gradle.XXXXXX")"
trap 'rm -f -- "$build_log"' EXIT

remove_broken_forge_jars() {
  [[ -d "$forge_cache" ]] || return 0

  local jar
  local removed=0
  while IFS= read -r -d '' jar; do
    if ! unzip -tqq "$jar" >/dev/null 2>&1; then
      echo "::warning::Removing corrupt ForgeGradle cache entry: $jar"
      rm -f -- "$jar"
      removed=$((removed + 1))
    fi
  done < <(find "$forge_cache" -type f -name '*.jar' -print0)

  echo "ForgeGradle cache validation removed $removed corrupt jar(s)."
}

run_build() {
  : >"$build_log"
  ./gradlew assemble -x test --no-daemon \
    "-Prelease_type=${RELEASE_TYPE:-alpha}" 2>&1 | tee "$build_log"
}

remove_broken_forge_jars

if run_build; then
  exit 0
fi

# Retry only the known corrupt-download failure. Other build failures should
# remain visible immediately instead of being hidden behind a broad retry.
if ! grep -Eq 'ZipException: zip END header not found|Could not add library: .*forge_gradle' "$build_log"; then
  exit 1
fi

echo "::warning::ForgeGradle encountered a corrupt jar; cleaning its cache and retrying once."
remove_broken_forge_jars
run_build
