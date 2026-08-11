#!/usr/bin/env bash
set -euo pipefail

# ForgeGradle downloads some libraries into its own cache, outside Gradle's
# normal dependency cache. A transient HTML/error response can therefore be
# saved with a .jar extension and fail later with "zip END header not found".
forge_cache="${GRADLE_USER_HOME:-${HOME}/.gradle}/caches/forge_gradle"
build_log="$(mktemp "${RUNNER_TEMP:-/tmp}/backpack-arsenal-gradle.XXXXXX")"
trap 'rm -f -- "$build_log"' EXIT

repair_broken_forge_jars() {
  [[ -d "$forge_cache" ]] || return 0

  local jar
  local base_url
  local -a base_urls
  local relative_path
  local replacement
  local removed=0
  local repaired=0
  local repaired_entry
  while IFS= read -r -d '' jar; do
    if ! unzip -tqq "$jar" >/dev/null 2>&1; then
      relative_path="${jar#"$forge_cache/maven_downloader/"}"
      replacement="${jar}.download"
      repaired_entry=0

      # ForgeGradle 6 uses https://maven.minecraftforge.net as a hard-coded
      # downloader root. That endpoint now returns a JavaScript redirect page,
      # which ForgeGradle stores as a .jar. The /releases endpoint serves the
      # actual artifact. Mojang-only artifacts live on libraries.minecraft.net,
      # so use that as a checked fallback.
      if [[ "$relative_path" != "$jar" ]]; then
        if [[ "$relative_path" == com/mojang/* ]]; then
          base_urls=('https://libraries.minecraft.net' 'https://maven.minecraftforge.net/releases')
        else
          base_urls=('https://maven.minecraftforge.net/releases' 'https://libraries.minecraft.net')
        fi
        for base_url in "${base_urls[@]}"; do
          rm -f -- "$replacement"
          if curl --fail --location --silent --show-error \
              --retry 3 --connect-timeout 20 \
              --output "$replacement" "$base_url/$relative_path" \
            && unzip -tqq "$replacement" >/dev/null 2>&1; then
            # ForgeGradle trusts this sidecar on the next invocation. Replace
            # the redirect page's checksum together with the repaired JAR or
            # ForgeGradle will immediately download the HTML again.
            md5sum "$replacement" | cut -d' ' -f1 >"${jar}.md5"
            mv -f -- "$replacement" "$jar"
            repaired=$((repaired + 1))
            repaired_entry=1
            echo "::notice::Repaired ForgeGradle cache entry: $relative_path"
            break
          fi
        done
      fi

      if [[ "$repaired_entry" -eq 1 ]]; then
        continue
      fi

      rm -f -- "$replacement"
      echo "::warning::Removing unrecoverable ForgeGradle cache entry: $jar"
      rm -f -- "$jar"
      removed=$((removed + 1))
    fi
  done < <(find "$forge_cache" -type f -name '*.jar' -print0)

  echo "ForgeGradle cache validation repaired $repaired and removed $removed corrupt jar(s)."
}

run_build() {
  : >"$build_log"
  ./gradlew assemble -x test --no-daemon \
    "-Prelease_type=${RELEASE_TYPE:-alpha}" 2>&1 | tee "$build_log"
}

repair_broken_forge_jars

if run_build; then
  exit 0
fi

# Retry only the known corrupt-download failure. Other build failures should
# remain visible immediately instead of being hidden behind a broad retry.
if ! grep -Eq 'ZipException: zip END header not found|Could not add library: .*forge_gradle' "$build_log"; then
  exit 1
fi

echo "::warning::ForgeGradle encountered a corrupt jar; cleaning its cache and retrying once."
repair_broken_forge_jars
run_build
