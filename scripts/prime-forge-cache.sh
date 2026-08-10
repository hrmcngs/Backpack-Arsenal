#!/usr/bin/env bash
set -euo pipefail

gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
download_root="$gradle_home/caches/forge_gradle/maven_downloader"
forge_maven="https://maven.minecraftforge.net/releases"

# Forge 1.20.1-47.1.0 userdev and the MCP tools it invokes. The legacy Maven
# root currently returns HTTP 200 HTML redirects for these files; ForgeGradle
# caches those pages as JARs and later fails with "zip END header not found".
artifacts=(
  "net/minecraftforge/forge/1.20.1-47.1.0/forge-1.20.1-47.1.0-userdev.jar"
  "net/minecraftforge/forge/1.20.1-47.1.0/forge-1.20.1-47.1.0-universal.jar"
  "net/minecraftforge/forge/1.20.1-47.1.0/forge-1.20.1-47.1.0-sources.jar"
  "net/minecraftforge/renamer/2.0.4/renamer-2.0.4-all.jar"
  "net/minecraftforge/mergetool/1.1.7/mergetool-1.1.7-fatjar.jar"
  "net/minecraftforge/installertools/1.2.0/installertools-1.2.0-fatjar.jar"
  "net/minecraftforge/installertools/1.3.2/installertools-1.3.2-fatjar.jar"
  "net/minecraftforge/installertools/1.4.4/installertools-1.4.4-fatjar.jar"
  "net/minecraftforge/ForgeAutoRenamingTool/1.0.2/ForgeAutoRenamingTool-1.0.2-all.jar"
  "net/minecraftforge/forgeflower/2.0.629.0/forgeflower-2.0.629.0.jar"
)

for artifact in "${artifacts[@]}"; do
  target="$download_root/$artifact"
  if [[ -f "$target" ]] && unzip -tq "$target" >/dev/null 2>&1; then
    if [[ ! -f "$target.md5" ]]; then
      openssl dgst -md5 "$target" | awk '{print $NF}' > "$target.md5"
    fi
    continue
  fi

  mkdir -p "$(dirname "$target")"
  curl --fail --location --retry 3 --retry-all-errors \
    --output "$target.part" "$forge_maven/$artifact"
  unzip -tq "$target.part" >/dev/null
  mv "$target.part" "$target"
  curl --fail --location --retry 3 --retry-all-errors \
    --output "$target.md5" "$forge_maven/$artifact.md5"
done

# Prime every library (including OS-specific LWJGL classifiers) from the URLs
# published in Mojang's 1.20.1 version metadata. This prevents ForgeGradle from
# falling back to the broken legacy Forge Maven for non-Forge coordinates.
metadata_dir="$(mktemp -d)"
trap 'rm -rf "$metadata_dir"' EXIT
curl --fail --location --retry 3 --retry-all-errors --silent --show-error \
  --output "$metadata_dir/manifest.json" \
  "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
version_url="$(jq -r '.versions[] | select(.id == "1.20.1") | .url' "$metadata_dir/manifest.json")"
if [[ -z "$version_url" || "$version_url" == "null" ]]; then
  echo "Minecraft 1.20.1 metadata URL was not found" >&2
  exit 1
fi
curl --fail --location --retry 3 --retry-all-errors --silent --show-error \
  --output "$metadata_dir/version.json" "$version_url"

jq -r '
  .libraries[].downloads
  | ([.artifact] + ((.classifiers // {}) | to_entries | map(.value)))[]
  | select(.path and .url)
  | [.path, .url] | @tsv
' "$metadata_dir/version.json" |
while IFS=$'\t' read -r artifact url; do
  target="$download_root/$artifact"
  if [[ -f "$target" ]] && unzip -tq "$target" >/dev/null 2>&1; then
    if [[ ! -f "$target.md5" ]]; then
      openssl dgst -md5 "$target" | awk '{print $NF}' > "$target.md5"
    fi
    continue
  fi
  mkdir -p "$(dirname "$target")"
  curl --fail --location --retry 3 --retry-all-errors --silent --show-error \
    --output "$target.part" "$url"
  unzip -tq "$target.part" >/dev/null
  mv "$target.part" "$target"
  openssl dgst -md5 "$target" | awk '{print $NF}' > "$target.md5"
done
