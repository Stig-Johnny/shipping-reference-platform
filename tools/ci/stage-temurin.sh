#!/usr/bin/env bash
# Trusted CI bootstrap: every completed download must match the reviewed bytes before setup-java may use it.
# Identity and size: security/reviews/jdk--eclipse-temurin--25.0.5+8.md and security/allowlist.json.
# First-use signature evidence remains in security/verifications/2026-11-06-toolchain.md.
set -euo pipefail
umask 077
dest="${1:?usage: stage-temurin.sh <directory>}"
readonly file="OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz"
readonly expected="68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb"
readonly bytes=141341188
readonly official_url="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/$file"
# DISABLED. A nonempty value requires a source-review amendment and renewed owner acceptance first.
# This is a committed constant, never an environment or command-line override.
readonly mirror_base_url=""
mkdir -p "$dest"
dest=$(cd "$dest" && pwd)
# A previous run is never a cache hit. The caller supplies a fresh staging destination.
[[ ! -e "$dest/$file" && ! -L "$dest/$file" ]] || exit 64
tmp=$(mktemp "$dest/$file.part.XXXXXX")
trap 'rm -f -- "$tmp"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

integrity_failure() {
  printf 'artifact=jdk source=%s integrity-failure=true\n' "$source" >&2
  exit 65
}

# At most 2 * 45 + 2 * 90 + 2 * 2 = 274 seconds of transfers/backoff (182 with the mirror disabled).
for source in mirror official; do
  if [[ "$source" == mirror ]]; then
    [[ -n "$mirror_base_url" ]] || continue
    url="${mirror_base_url%/}/sha256/$expected/$file"
    limit=45; redirects=0
  else
    url=$official_url
    limit=90; redirects=5
  fi
  for attempt in 1 2; do
    : > "$tmp"
    rc=0
    # Ignore curlrc; HTTPS only, including official redirects. Never log response bodies or redirect URLs.
    code=$(curl --disable --silent --fail --location --retry 0 \
      --proto '=https' --proto-redir '=https' --connect-timeout 10 --max-time "$limit" \
      --max-redirs "$redirects" --max-filesize "$bytes" \
      --output "$tmp" --write-out '%{http_code}' "$url" 2>/dev/null) || rc=$?
    [[ "$rc" != 63 ]] || integrity_failure
    # Local read/write errors are not source outages.
    [[ "$rc" != 23 && "$rc" != 26 ]] || exit 74
    if [[ "$rc" == 0 && "$code" == 200 ]]; then
      [[ $(wc -c < "$tmp") -eq "$bytes" ]] || integrity_failure
      actual=$(sha256sum "$tmp") || integrity_failure
      [[ "${actual%% *}" == "$expected" ]] || integrity_failure
      mv -- "$tmp" "$dest/$file"
      printf 'artifact=jdk source=%s verified=true\n' "$source"
      printf 'jdk-file=%s/%s\n' "$dest" "$file" >> "${GITHUB_OUTPUT:-/dev/null}"
      exit 0
    fi
    [[ "$code" =~ ^[0-9]{3}$ ]] || code=000
    printf 'artifact=jdk source=%s attempt=%s http=%s curl=%s\n' "$source" "$attempt" "$code" "$rc" >&2
    case "$rc:$code" in
      5:*|6:*|7:*|18:*|28:*|35:*|52:*|55:*|56:*|*:408|*:429|*:500|*:502|*:503|*:504)
        if [[ "$attempt" == 1 ]]; then sleep 2; fi ;;
      *) break ;;
    esac
  done
done
printf 'artifact=jdk unavailable=true\n' >&2
exit 69
