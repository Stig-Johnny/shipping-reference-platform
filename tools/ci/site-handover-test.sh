#!/usr/bin/env bash
# Tests of tools/ci/site-handover.sh, with bash and GNU tar: what the build job takes over from the test job. A
# handover whose archive is not the one sent, that names a path outside the report directories, that holds anything
# but regular files or that would overwrite a file is refused, and a site that differs from the test job's fails.
#
# Run from anywhere: bash tools/ci/site-handover-test.sh   It prints one line per case and exits 1 if any failed.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/site-handover.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
passed=0
failed=0
report="apps/shipping-api/build/test-results/test/TEST-io.example.ParcelTest.xml"
database="apps/shipping-api/build/test-results/databaseTest/TEST-io.example.PostgresHarnessIntegrationTest.xml"

# A checkout in $work/$1: one test report, one database report and a site of two files.
checkout() {
  local dir="$work/$1"
  rm -rf "$dir"
  mkdir -p "$dir/$(dirname "$report")" "$dir/$(dirname "$database")" "$dir/tools/docs/build/site/assets" "$dir/libs" "$dir/tools"
  echo '<testsuite name="io.example.ParcelTest" tests="1"/>' > "$dir/$report"
  echo '<testsuite name="io.example.PostgresHarnessIntegrationTest" tests="1"/>' > "$dir/$database"
  echo '<html>site</html>' > "$dir/tools/docs/build/site/index.html"
  echo 'body {}' > "$dir/tools/docs/build/site/assets/site.css"
}

# Runs the script in a checkout, with the given environment; the output goes to $work/output.
handover() {
  local dir="$work/$1"
  shift
  mkdir -p "$work/runner"
  (cd "$dir" && env RUNNER_TEMP="$work/runner" GITHUB_OUTPUT="$work/outputs" "$@") > "$work/output" 2>&1
}

check() {
  local name="$1" want="$2" status="$3" text="${4:-}"
  local ok=1
  if [[ "$want" == "pass" && "$status" -ne 0 ]] || [[ "$want" == "fail" && "$status" -eq 0 ]]; then ok=0; fi
  if [[ -n "$text" ]] && ! grep -qF -- "$text" "$work/output"; then ok=0; fi
  if (( ok )); then
    passed=$((passed + 1))
    echo "ok   $name"
  else
    failed=$((failed + 1))
    echo "FAIL $name (exit $status, expected to $want$([[ -n "$text" ]] && echo " with '$text'"))"
    sed 's/^/       /' "$work/output"
  fi
}

output() {
  sed -n "s/^$1=//p" "$work/outputs"
}

# An archive made by hand, in $work/archive.tgz, of the given paths in $work/source; tar options before "--".
archive() {
  local options=()
  while [[ "$1" != "--" ]]; do options+=("$1"); shift; done
  shift
  (cd "$work/source" && tar --create --gzip --file "$work/archive.tgz" ${options[@]+"${options[@]}"} "$@")
}

encoded() {
  base64 --wrap=0 "$work/archive.tgz"
}

digest() {
  sha256sum "$work/archive.tgz" | cut -d ' ' -f 1
}

# The whole handover: send in the test job's checkout, receive and compare in a fresh one.
checkout sender
rm -f "$work/outputs"
handover sender bash "$script" send
check "the test job sends its reports and the site's digest" pass $?
reports="$(output reports)"
reports_sha256="$(output reports-sha256)"
site_sha256="$(output site-sha256)"
checkout receiver
rm -f "$work/receiver/$report" "$work/receiver/$database"
handover receiver REPORTS="$reports" REPORTS_SHA256="$reports_sha256" bash "$script" receive
check "the build job takes over the reports" pass $?
handover receiver test -f "$report" -a -f "$database"
check "the test report and the database report are unpacked" pass $?
handover receiver SITE_SHA256="$site_sha256" bash "$script" compare
check "the same site passes the comparison" pass $?
echo '<html>changed</html>' > "$work/receiver/tools/docs/build/site/index.html"
handover receiver SITE_SHA256="$site_sha256" bash "$script" compare
check "a site that differs fails the comparison" fail $? "the site differs from the one the test job built"

# The archive is the one sent, or nothing is unpacked.
checkout receiver
rm -f "$work/receiver/$report" "$work/receiver/$database"
handover receiver REPORTS="$reports" REPORTS_SHA256="$(printf '0%.0s' {1..64})" bash "$script" receive
check "a wrong digest is refused" fail $? "the reports changed in transit"
handover receiver test ! -e "$report"
check "and nothing of it is unpacked" pass $?
handover receiver REPORTS="" REPORTS_SHA256="$reports_sha256" bash "$script" receive
check "an empty handover is refused" fail $? "handed over no reports"
handover receiver REPORTS="not base64!" REPORTS_SHA256="$reports_sha256" bash "$script" receive
check "a handover that is not base64 is refused" fail $? "handed over no reports"

# Only report files, at their paths, and nothing else.
rm -rf "$work/source"
mkdir -p "$work/source/$(dirname "$report")"
echo '<testsuite/>' > "$work/source/$report"
archive --absolute-names --transform 's,^,../,' -- "$report"
checkout receiver
handover receiver REPORTS="$(encoded)" REPORTS_SHA256="$(digest)" bash "$script" receive
check "a path that leaves the checkout is refused" fail $? "an unexpected path: ../apps"
handover receiver test ! -e "$work/apps"
check "and nothing is written outside it" pass $?
archive --absolute-names --transform "s,^,$work/escaped/," -- "$report"
handover receiver REPORTS="$(encoded)" REPORTS_SHA256="$(digest)" bash "$script" receive
check "an absolute path is refused" fail $? "an unexpected path: $work/escaped"
mkdir -p "$work/source/apps/other/build/test-results/databaseTest" "$work/source/apps/shipping-api/build/reports"
echo '<testsuite/>' > "$work/source/apps/other/build/test-results/databaseTest/TEST-x.xml"
echo 'not a report' > "$work/source/apps/shipping-api/build/reports/index.html"
for path in apps/other/build/test-results/databaseTest/TEST-x.xml apps/shipping-api/build/reports/index.html; do
  archive -- "$path"
  handover receiver REPORTS="$(encoded)" REPORTS_SHA256="$(digest)" bash "$script" receive
  check "a file outside the report directories is refused: $path" fail $? "an unexpected path: $path"
done

rm -rf "$work/source"
mkdir -p "$work/source/$(dirname "$report")"
ln -s /etc/hostname "$work/source/$report"
archive -- "$report"
checkout receiver
rm -f "$work/receiver/$report"
handover receiver REPORTS="$(encoded)" REPORTS_SHA256="$(digest)" bash "$script" receive
check "a symbolic link is refused" fail $? "something other than regular files"
handover receiver test ! -L "$report"
check "and it is not unpacked" pass $?
rm -f "$work/source/$report"
echo '<testsuite/>' > "$work/source/$report"
ln "$work/source/$report" "$work/source/$(dirname "$report")/TEST-io.example.SecondTest.xml"
archive -- "$report" "$(dirname "$report")/TEST-io.example.SecondTest.xml"
handover receiver REPORTS="$(encoded)" REPORTS_SHA256="$(digest)" bash "$script" receive
check "a hard link is refused" fail $? "something other than regular files"

# A report in the checkout is never overwritten.
rm -rf "$work/source"
mkdir -p "$work/source/$(dirname "$report")"
echo '<testsuite name="io.example.ParcelTest" tests="9"/>' > "$work/source/$report"
archive -- "$report"
checkout receiver
handover receiver REPORTS="$(encoded)" REPORTS_SHA256="$(digest)" bash "$script" receive
check "a report that exists in the checkout is not overwritten" fail $? "a report already exists in the checkout"
handover receiver grep -q 'tests="1"' "$report"
check "and keeps its content" pass $?

# The sender sends report files only.
checkout sender
mkdir -p "$work/sender/apps/shipping-api/build/test-results/test"
ln -s /etc/hostname "$work/sender/apps/shipping-api/build/test-results/test/TEST-io.example.LinkTest.xml"
rm -f "$work/outputs"
handover sender bash "$script" send
check "a symbolic link among the reports is not sent" fail $? "contains a symbolic link"

echo
echo "$passed passed, $failed failed"
(( failed == 0 && passed > 0 ))
