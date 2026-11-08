#!/usr/bin/env bash
# Local transport fixtures; real SHA-256 verification, no network or downloaded code.
# An optional implementation path supports red/green replay against the old script.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
script="${1:-$here/stage-temurin.sh}"
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
export fixture
mkdir "$fixture/bin"
printf 'accepted-fixture\n' > "$fixture/good"
printf 'rejected-fixture\n' > "$fixture/bad"
digest=$(sha256sum "$fixture/good")
digest=${digest%% *}
bytes=$(wc -c < "$fixture/good" | tr -d ' ')
file=OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz
export digest bytes file
failures=0
cases=0

cp "$here/fake-download/curl" "$fixture/bin/curl"
cp "$here/fake-download/sleep" "$fixture/bin/sleep"
chmod +x "$fixture/bin/curl" "$fixture/bin/sleep"

run_case() {
  local label=$1 mirror=$2 official=$3 expected_exit=$4 requests=$5 sleeps=$6 enabled=${7:-yes}
  local actual=0 failed=0 base=https://mirror.example
  [[ "$enabled" == yes ]] || base=""
  rm -rf "$fixture/staged files" "$fixture/bad-options"
  : > "$fixture/requests"; : > "$fixture/sleeps"; : > "$fixture/github-output"
  # Only the isolated copy gets synthetic identities. Production has no environment/CLI URL or hash override.
  sed -E -e "s|^(readonly )?mirror_base_url=.*|readonly mirror_base_url=\"$base\"|" \
    -e "s|^(readonly )?expected=.*|readonly expected=\"$digest\"|" \
    -e "s|^(readonly )?bytes=.*|readonly bytes=$bytes|" "$script" > "$fixture/stage.sh"
  PATH="$fixture/bin:$PATH" MIRROR_RESULT="$mirror" OFFICIAL_RESULT="$official" \
    mirror_base_url=https://unaccepted.example expected=unaccepted \
    GITHUB_OUTPUT="$fixture/github-output" bash -c 'export STAGING_PID=$$; exec bash "$@"' \
    _ "$fixture/stage.sh" "$fixture/staged files" \
    > "$fixture/output" 2>&1 || actual=$?
  [[ "$actual" == "$expected_exit" ]] || failed=1
  [[ "$(tr '\n' ' ' < "$fixture/requests")" == "$requests" ]] || failed=1
  [[ "$(wc -l < "$fixture/sleeps" | tr -d ' ')" == "$sleeps" ]] || failed=1
  [[ ! -e "$fixture/bad-options" ]] || failed=1
  if [[ "$expected_exit" == 0 ]]; then
    cmp -s "$fixture/good" "$fixture/staged files/$file" || failed=1
    [[ "$(cat "$fixture/github-output")" == "jdk-file=$fixture/staged files/$file" ]] || failed=1
    grep -q 'verified=true' "$fixture/output" || failed=1
  else
    [[ ! -e "$fixture/staged files/$file" && ! -s "$fixture/github-output" ]] || failed=1
    [[ "$expected_exit" != 65 ]] || grep -q 'integrity-failure' "$fixture/output" || failed=1
  fi
  [[ -z "$(find "$fixture/staged files" -name '*.part.*' -print)" ]] || failed=1
  if grep -q 'untrusted-response-marker' "$fixture/output"; then failed=1; fi
  cases=$((cases + 1))
  if [[ "$failed" == 0 ]]; then
    printf 'PASS: %s (exit %s)\n' "$label" "$actual"
  else
    printf 'FAIL: %s (expected exit %s, got %s; requests: %s)\n' \
      "$label" "$expected_exit" "$actual" "$(tr '\n' ' ' < "$fixture/requests")"
    failures=$((failures + 1))
  fi
}

run_case 'mirror ok; official not contacted' ok down 0 'mirror ' 0
run_case 'mirror down; official fallback ok' down ok 0 'mirror mirror official ' 1
run_case 'mirror wrong bytes; no fallback' wrong ok 65 'mirror ' 0
run_case 'both sources down; bounded failure' down down 69 'mirror mirror official official ' 2
run_case 'official wrong bytes; stop' down wrong 65 'mirror mirror official ' 1
run_case 'empty mirror ignores environment override' down ok 0 'official ' 0 no
run_case 'official transient timeout; retry ok' down retry-ok 0 'official official ' 1 no
run_case 'official wrong bytes without mirror; stop' down wrong 65 'official ' 0 no
run_case 'mirror transient timeout; retry ok' retry-ok down 0 'mirror mirror ' 1
run_case 'both sources time out; bounded failure' timeout timeout 69 'mirror mirror official official ' 2
run_case 'mirror missing; immediate fallback' missing ok 0 'mirror official ' 0
run_case 'mirror DNS failure; fallback' dns ok 0 'mirror mirror official ' 1
run_case 'mirror TLS failure; fallback' tls ok 0 'mirror official ' 0
run_case 'mirror throttled; bounded fallback' throttle ok 0 'mirror mirror official ' 1
run_case 'mirror redirect refused; fallback' redirect ok 0 'mirror official ' 0
run_case 'mirror oversized; no fallback' large ok 65 'mirror ' 0
run_case 'mirror truncated success; no fallback' short ok 65 'mirror ' 0
run_case 'official oversized; stop' missing large 65 'mirror official ' 0
run_case 'official truncated success; stop' missing short 65 'mirror official ' 0
run_case 'cancelled download; cleanup' cancel ok 143 'mirror ' 0

printf '%s download fixtures, %s failures\n' "$cases" "$failures"
[[ "$failures" == 0 ]]
