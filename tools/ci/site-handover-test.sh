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

# Runs the script's command $2 in the checkout $work/$1, with the settings that follow (NAME=value) in its environment;
# the output goes to $work/output.
handover() {
  local dir="$work/$1" command="$2" setting
  shift 2
  mkdir -p "$work/runner"
  (
    cd "$dir" || exit 1
    export RUNNER_TEMP="$work/runner" GITHUB_OUTPUT="$work/outputs"
    export REPORTS_COUNT=1 REPORTS_1="" REPORTS_2="" REPORTS_3="" REPORTS_4=""
    for setting in "$@"; do export "$setting"; done
    bash "$script" "$command"
  ) > "$work/output" 2>&1
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
handover sender send
check "the test job sends its reports and the site's digest" pass $?
reports="$(output reports-1)"
reports_sha256="$(output reports-sha256)"
site_sha256="$(output site-sha256)"
checkout receiver
rm -f "$work/receiver/$report" "$work/receiver/$database"
handover receiver receive REPORTS_1="$reports" REPORTS_SHA256="$reports_sha256"
check "the build job takes over the reports" pass $?
test -f "$work/receiver/$report" -a -f "$work/receiver/$database"
check "the test report and the database report are unpacked" pass $?
handover receiver compare SITE_SHA256="$site_sha256"
check "the same site passes the comparison" pass $?
echo '<html>changed</html>' > "$work/receiver/tools/docs/build/site/index.html"
handover receiver compare SITE_SHA256="$site_sha256"
check "a site that differs fails the comparison" fail $? "the site differs from the one the test job built"

# The archive is the one sent, or nothing is unpacked.
checkout receiver
rm -f "$work/receiver/$report" "$work/receiver/$database"
handover receiver receive REPORTS_1="$reports" REPORTS_SHA256="$(printf '%064d' 0)"
check "a wrong digest is refused" fail $? "the reports changed in transit"
test ! -e "$work/receiver/$report"
check "and nothing of it is unpacked" pass $?
handover receiver receive REPORTS_1="" REPORTS_SHA256="$reports_sha256"
check "an empty handover is refused" fail $? "handed over no reports"
handover receiver receive REPORTS_1="not base64!" REPORTS_SHA256="$reports_sha256"
check "a handover that is not base64 is refused" fail $? "handed over no reports"

# Only report files, at their paths, and nothing else.
rm -rf "$work/source"
mkdir -p "$work/source/$(dirname "$report")"
echo '<testsuite/>' > "$work/source/$report"
archive --absolute-names --transform 's,^,../,' -- "$report"
checkout receiver
handover receiver receive REPORTS_1="$(encoded)" REPORTS_SHA256="$(digest)"
check "a path that leaves the checkout is refused" fail $? "an unexpected path: ../apps"
test ! -e "$work/apps"
check "and nothing is written outside it" pass $?
archive --absolute-names --transform "s,^,$work/escaped/," -- "$report"
handover receiver receive REPORTS_1="$(encoded)" REPORTS_SHA256="$(digest)"
check "an absolute path is refused" fail $? "an unexpected path: $work/escaped"
mkdir -p "$work/source/apps/other/build/test-results/databaseTest" "$work/source/apps/shipping-api/build/reports"
echo '<testsuite/>' > "$work/source/apps/other/build/test-results/databaseTest/TEST-x.xml"
echo 'not a report' > "$work/source/apps/shipping-api/build/reports/index.html"
for path in apps/other/build/test-results/databaseTest/TEST-x.xml apps/shipping-api/build/reports/index.html; do
  archive -- "$path"
  handover receiver receive REPORTS_1="$(encoded)" REPORTS_SHA256="$(digest)"
  check "a file outside the report directories is refused: $path" fail $? "an unexpected path: $path"
done

rm -rf "$work/source"
mkdir -p "$work/source/$(dirname "$report")"
ln -s /etc/hostname "$work/source/$report"
archive -- "$report"
checkout receiver
rm -f "$work/receiver/$report"
handover receiver receive REPORTS_1="$(encoded)" REPORTS_SHA256="$(digest)"
check "a symbolic link is refused" fail $? "something other than regular files"
test ! -L "$work/receiver/$report"
check "and it is not unpacked" pass $?
rm -f "$work/source/$report"
echo '<testsuite/>' > "$work/source/$report"
ln "$work/source/$report" "$work/source/$(dirname "$report")/TEST-io.example.SecondTest.xml"
archive -- "$report" "$(dirname "$report")/TEST-io.example.SecondTest.xml"
handover receiver receive REPORTS_1="$(encoded)" REPORTS_SHA256="$(digest)"
check "a hard link is refused" fail $? "something other than regular files"

# A report in the checkout is never overwritten.
rm -rf "$work/source"
mkdir -p "$work/source/$(dirname "$report")"
echo '<testsuite name="io.example.ParcelTest" tests="9"/>' > "$work/source/$report"
archive -- "$report"
checkout receiver
handover receiver receive REPORTS_1="$(encoded)" REPORTS_SHA256="$(digest)"
check "a report that exists in the checkout is not overwritten" fail $? "a report already exists in the checkout"
grep -q 'tests="1"' "$work/receiver/$report"
check "and keeps its content" pass $?

# Regression for #93: report content grows beyond one output/environment value. The deterministic synthetic
# system-out uses base64's alphabet so it is valid XML text, with enough entropy to resist gzip's compression.
checkout large
awk 'BEGIN {
  srand(93); alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  print "<testsuite name=\"io.example.ParcelTest\" tests=\"1\"><system-out>"
  for (i=0; i<360000; i++) printf "%s", substr(alphabet, 1+int(rand()*64), 1)
  print "</system-out></testsuite>"
}' > "$work/large/$report"
rm -f "$work/outputs"
handover large send
check "reports larger than one Linux environment value are sent intact" pass $?
parts=("REPORTS_COUNT=$(output reports-count)" "REPORTS_SHA256=$(output reports-sha256)")
for i in 1 2 3 4; do parts+=("REPORTS_$i=$(output reports-$i)"); done
[[ "$(output reports-count)" == 4 ]]
check "the fixture uses all four output slots" pass $?
[[ "$(wc -c < "$work/outputs")" -lt 500000 ]]
check "all outputs fit the UTF-16 job-output budget with margin" pass $?
checkout receiver
rm -f "$work/receiver/$report" "$work/receiver/$database"
handover receiver receive "${parts[@]}"
check "all chunks are reassembled and received" pass $?
cmp "$work/large/$report" "$work/receiver/$report" && cmp "$work/large/$database" "$work/receiver/$database"
check "both large ordinary and database reports are byte-identical" pass $?
handover receiver compare "SITE_SHA256=$(output site-sha256)"
check "the large handover preserves the tested site's digest" pass $?

# Incomplete, reordered and oversized handovers fail before any report is extracted.
checkout receiver
rm -f "$work/receiver/$report" "$work/receiver/$database"
handover receiver receive "${parts[@]}" REPORTS_2=""
check "a missing middle chunk is refused" fail $? "handed over no reports"
handover receiver receive "${parts[@]}" REPORTS_4=""
check "a missing last chunk is refused" fail $? "handed over no reports"
handover receiver receive "${parts[@]}" "REPORTS_1=$(output reports-2)" "REPORTS_2=$(output reports-1)"
check "reordered chunks fail the original archive digest" fail $? "changed in transit"
handover receiver receive "${parts[@]}" "REPORTS_4=A$(output reports-4)"
check "a changed chunk is refused" fail $?
handover receiver receive REPORTS_1="$reports" REPORTS_2=AAAA REPORTS_SHA256="$reports_sha256"
check "a chunk beyond the declared count is refused" fail $? "unexpected report chunk"
handover receiver receive REPORTS_COUNT=2 REPORTS_1=AAAA REPORTS_2=AAAA REPORTS_SHA256="$reports_sha256"
check "a short non-final chunk is refused" fail $? "non-final report chunk"
handover receiver receive "${parts[@]}" "REPORTS_1=A$(output reports-1)"
check "an oversized chunk is refused" fail $? "report chunk is too large"
for count in '' 0 5 01 '1+1'; do
  handover receiver receive "${parts[@]}" "REPORTS_COUNT=$count"
  check "an invalid chunk count is refused: '$count'" fail $? "invalid report chunk count"
done
[[ ! -e "$work/receiver/$report" && ! -e "$work/receiver/$database" ]]
check "no invalid chunk handover extracts a report" pass $?

# Never silently truncate evidence if it eventually outgrows the total job-output budget.
cat "$work/large/$report" >> "$work/large/$database"
rm -f "$work/outputs"
handover large send
check "evidence beyond the aggregate budget fails explicitly" fail $? "more than 400000 fit the handover"
[[ ! -e "$work/outputs" ]]
check "an oversized send writes no partial outputs" pass $?

# The sender sends report files only.
checkout sender
mkdir -p "$work/sender/apps/shipping-api/build/test-results/test"
ln -s /etc/hostname "$work/sender/apps/shipping-api/build/test-results/test/TEST-io.example.LinkTest.xml"
rm -f "$work/outputs"
handover sender send
check "a symbolic link among the reports is not sent" fail $? "contains a symbolic link"

echo
echo "$passed passed, $failed failed"
(( failed == 0 && passed > 0 ))
