#!/usr/bin/env bash
# Downloads the reviewed Temurin JDK for CI and checks it against the allowlist's SHA-256 before anything uses it.
# The value below is the one in security/allowlist.json (jdk, eclipse-temurin 25.0.5+8, linux x64) and its review,
# security/reviews/jdk--eclipse-temurin--25.0.5+8.md. Changing either is a new review.
set -euo pipefail
dest="${1:?usage: stage-temurin.sh <directory>}"
file="OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz"
expected="68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb"
mkdir -p "$dest"
curl --fail --silent --show-error --location --retry 3 --max-time 600 \
  --output "$dest/$file" "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/$file"
echo "$expected  $dest/$file" | sha256sum --check --strict
echo "jdk-file=$dest/$file" >> "${GITHUB_OUTPUT:-/dev/null}"
