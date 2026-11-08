#!/usr/bin/env bash
# Synthetic fixtures for the output scan. Optional argument: another implementation, for red/green evidence.
set -euo pipefail
scanner="${1:-$(cd "$(dirname "$0")" && pwd)/check-generated-password.sh}"
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
failures=0
cases=0
real_find=$(command -v find)
real_grep=$(command -v grep)
export real_find real_grep

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
cat > "$fixture/bin/find" <<'FIXTURE'
#!/usr/bin/env bash
"$real_find" "$@"
exit 2
FIXTURE
cat > "$fixture/bin/grep" <<'FIXTURE'
#!/usr/bin/env bash
touch grep-was-run
exec "$real_grep" "$@"
FIXTURE
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
  cat > "$fixture/bin/grep" <<'FIXTURE'
#!/usr/bin/env bash
case "$*" in
  *gradle-check.log*) [[ "$SCAN_ERROR" != console ]] || exit 2 ;;
  *index.html*) [[ "$SCAN_ERROR" != report ]] || exit 2 ;;
esac
exec "$real_grep" "$@"
FIXTURE
  chmod +x "$fixture/bin/grep"
  export SCAN_ERROR=$location
  expect_exit 2 "grep read error: $location"
done

for output in gradle-check.log candidate/apps/api/build/test-results/test/TEST-fixture.xml \
  candidate/apps/api/build/reports/tests/test/index.html \
  $'candidate/apps/api/build/test-results/test/binary output\nfixture.bin'; do
  reset_fixture
  printf '\0Using generated security password: synthetic-fixture\n' > "$fixture/$output"
  expect_exit 1 "forbidden phrase: ${output//$'\n'/<newline>}"
done

printf '%s scan fixtures, %s failures\n' "$cases" "$failures"
[[ "$failures" == 0 ]]
