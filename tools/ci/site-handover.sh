#!/usr/bin/env bash
# The handover between the two jobs of .github/workflows/pages.yml that build the site. The test job runs every
# project's tests, the database tests among them, with no environment, no secret and no publication privilege. The
# build job, in the github-pages environment, publishes. It never runs a test and never sees an artifact from outside
# this run: it gets this run's JUnit reports through the test job's outputs, builds the site again from the same
# commit, and requires the site the test job built, byte for byte.
#
#   send     (test job) writes the outputs reports (the JUnit XML reports of every project and of the shipping API's
#            databaseTest task, as a gzipped tar in base64), reports-sha256 and site-sha256 (a digest over every file
#            name and content of the site).
#   receive  (build job) checks REPORTS against REPORTS_SHA256, accepts only regular files named
#            <apps|libs|tools>/<project>/build/test-results/test/TEST-<class>.xml or
#            apps/shipping-api/build/test-results/databaseTest/TEST-<class>.xml, and unpacks them into the checkout,
#            where no report may exist yet.
#   compare  (build job) fails unless the site it built has the digest SITE_SHA256 from the test job.
#
# A job output is at most 1 MB and reaches the next job as an environment variable, which Linux limits to 128 KiB;
# send refuses more than LIMIT characters, so a growing test suite fails here, loudly, instead of losing evidence.
set -euo pipefail

readonly SITE="tools/docs/build/site"
readonly LIMIT=100000
# Every project's test reports, and the shipping API's database test reports (its databaseTest task).
readonly REPORT='^((apps|libs|tools)/[a-z0-9-]+/build/test-results/test|apps/shipping-api/build/test-results/databaseTest)/TEST-[A-Za-z0-9_.$-]+\.xml$'

fail() {
  echo "::error::$*"
  exit 1
}

site_sha256() {
  [[ -f "$SITE/index.html" ]] || fail "there is no built site in $SITE"
  [[ -z "$(find "$SITE" -type l -print -quit)" ]] || fail "the site contains a symbolic link"
  (cd "$SITE" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum) | sha256sum | cut -d ' ' -f 1
}

send() {
  local reports encoded
  reports="$(find apps libs tools \( -path '*/build/test-results/test/TEST-*.xml' \
    -o -path 'apps/shipping-api/build/test-results/databaseTest/TEST-*.xml' \) -type f 2>/dev/null | LC_ALL=C sort)"
  [[ -n "$reports" ]] || fail "the tests wrote no reports"
  [[ -z "$(find apps libs tools -path '*/build/test-results/*' -type l -print -quit 2>/dev/null)" ]] \
    || fail "a test report directory contains a symbolic link"
  while IFS= read -r file; do
    [[ "$file" =~ $REPORT ]] || fail "an unexpected report path: $file"
  done <<< "$reports"
  tar --create --gzip --file "$RUNNER_TEMP/reports.tar.gz" --sort=name --owner=0 --group=0 --numeric-owner \
    --mtime=@0 --files-from=- <<< "$reports"
  encoded="$(base64 --wrap=0 "$RUNNER_TEMP/reports.tar.gz")"
  (( ${#encoded} <= LIMIT )) || fail "the reports take ${#encoded} characters, more than $LIMIT fit the handover"
  {
    echo "reports=$encoded"
    echo "reports-sha256=$(sha256sum "$RUNNER_TEMP/reports.tar.gz" | cut -d ' ' -f 1)"
    echo "site-sha256=$(site_sha256)"
  } >> "$GITHUB_OUTPUT"
  echo "Handed over $(grep -c . <<< "$reports") reports (${#encoded} characters) and the site's digest."
}

receive() {
  [[ -n "${REPORTS:-}" && "$REPORTS" =~ ^[A-Za-z0-9+/]+={0,2}$ ]] || fail "the test job handed over no reports"
  local archive="$RUNNER_TEMP/reports.tar.gz" names types
  base64 --decode <<< "$REPORTS" > "$archive"
  echo "${REPORTS_SHA256:?}  $archive" | sha256sum --check --strict --quiet || fail "the reports changed in transit"
  names="$(tar --list --gzip --file "$archive")"
  while IFS= read -r name; do
    [[ "$name" =~ $REPORT ]] || fail "the handover holds an unexpected path: $name"
    [[ ! -e "$name" ]] || fail "a report already exists in the checkout: $name"
  done <<< "$names"
  types="$(tar --list --verbose --gzip --file "$archive" | cut -c 1 | LC_ALL=C sort -u)"
  [[ "$types" == "-" ]] || fail "the handover holds something other than regular files"
  tar --extract --gzip --file "$archive" --keep-old-files --no-same-owner --no-same-permissions
  echo "Took over $(grep -c . <<< "$names") reports from the test job."
}

compare() {
  local built
  built="$(site_sha256)"
  [[ "$built" == "${SITE_SHA256:?}" ]] \
    || fail "the site differs from the one the test job built (digest $built, expected $SITE_SHA256)"
  echo "The site is the one the test job built: $built"
}

case "${1:-}" in
  send | receive | compare) "$1" ;;
  *) echo "usage: site-handover.sh send|receive|compare" >&2; exit 2 ;;
esac
