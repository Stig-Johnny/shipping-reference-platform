#!/usr/bin/env bash
# Synthetic fixtures for the output scan. Optional argument: another implementation, for red/green evidence.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
scanner="${1:-$here/check-generated-password.sh}"
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
failures=0
cases=0
scan_fixture_path=$PATH
export scan_fixture_path

reset_fixture() {
  rm -rf "$fixture/candidate" "$fixture/bin"
  mkdir -p "$fixture/candidate/apps/api/build/test-results/test" \
    "$fixture/candidate/apps/api/build/reports/tests/test" "$fixture/bin"
  printf 'Build completed\n' > "$fixture/gradle-check.log"
  printf '<testsuite/>\n' > "$fixture/candidate/apps/api/build/test-results/test/TEST-fixture.xml"
  printf '<html>Passed</html>\n' > "$fixture/candidate/apps/api/build/reports/tests/test/index.html"
}

expect_exit() {
  local expected=$1 label=$2 actual=0
  (cd "$fixture" && PATH="$fixture/bin:$PATH" RUNNER_TEMP="$fixture" \
    bash --noprofile --norc -eo pipefail "$scanner" candidate "$fixture/gradle-check.log") \
    > "$fixture/output" 2>&1 || actual=$?
  cases=$((cases + 1))
  if [[ "$actual" == "$expected" ]]; then
    printf 'PASS: %s (exit %s)\n' "$label" "$actual"
  else
    printf 'FAIL: %s (expected exit %s, got %s)\n' "$label" "$expected" "$actual"
    failures=$((failures + 1))
  fi
}

reset_fixture
expect_exit 0 'clean console, results and reports (grep exit 1)'
for missing in gradle-check.log candidate \
  candidate/apps/api/build/test-results candidate/apps/api/build/reports/tests; do
  reset_fixture
  rm -rf "$fixture/$missing"
  expect_exit 2 "missing required output: $missing"
done

reset_fixture
: > "$fixture/gradle-check.log"
expect_exit 2 'empty console output'

# A partial discovery must not look like a complete list. The marker shows grep was never reached.
reset_fixture
cp "$here/fake-password-scan/find" "$fixture/bin/find"
cp "$here/fake-password-scan/grep-discovery" "$fixture/bin/grep"
chmod +x "$fixture/bin/find" "$fixture/bin/grep"
expect_exit 2 'find error after partial output'
cases=$((cases + 1))
if [[ -e "$fixture/grep-was-run" ]]; then
  printf 'FAIL: discovery must finish successfully before any file is scanned\n'
  failures=$((failures + 1))
else
  printf 'PASS: discovery checked synchronously before scanning\n'
fi

for location in console report; do
  reset_fixture
  cp "$here/fake-password-scan/grep-read-error" "$fixture/bin/grep"
  chmod +x "$fixture/bin/grep"
  export SCAN_ERROR=$location
  expect_exit 2 "grep read error: $location"
done

newline='
'
for output in gradle-check.log candidate/apps/api/build/test-results/test/TEST-fixture.xml \
  candidate/apps/api/build/reports/tests/test/index.html \
  "candidate/apps/api/build/test-results/test/binary output${newline}fixture.bin"; do
  reset_fixture
  printf '\0Using generated security password: synthetic-fixture\n' > "$fixture/$output"
  expect_exit 1 "forbidden phrase: ${output//$newline/<newline>}"
done

printf '%s scan fixtures, %s failures\n' "$cases" "$failures"
[[ "$failures" == 0 ]]
