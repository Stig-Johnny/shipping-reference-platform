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
# Run from the repository root. Every step is checked on its own, so that a command that fails stops the script
# instead of reading as an empty answer (tools/ci/site-handover-test.sh). A job output is at most 1 MB and reaches the
# next job as an environment variable, which Linux limits to 128 KiB; send refuses more than LIMIT characters, so a
# growing test suite fails here, loudly, instead of losing evidence.
set -euo pipefail

readonly SITE="tools/docs/build/site"
readonly LIMIT=100000
# Every project's test reports, and the shipping API's database test reports (its databaseTest task).
readonly REPORT='^((apps|libs|tools)/[a-z0-9-]+/build/test-results/test|apps/shipping-api/build/test-results/databaseTest)/TEST-[A-Za-z0-9_.$-]+\.xml$'
readonly WORK="${RUNNER_TEMP:?RUNNER_TEMP is not set}"

fail() {
  echo "::error::$*"
  exit 1
}

# Writes the site's digest, a SHA-256 over the sorted list of every file's SHA-256 and name, into the file $1.
site_digest() {
  local out="$1" links
  [[ -f "$SITE/index.html" ]] || fail "there is no built site in $SITE"
  links="$(find "$SITE" -type l -print -quit)" || fail "the site cannot be searched for symbolic links"
  [[ -z "$links" ]] || fail "the site contains a symbolic link"
  (cd "$SITE" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum) > "$WORK/site-files" \
    || fail "the site's files cannot be hashed"
  sha256sum < "$WORK/site-files" | cut -d ' ' -f 1 > "$out" || fail "the site's digest cannot be computed"
}

send() {
  local reports links file encoded sum digest
  reports="$(find apps libs tools \( -path '*/build/test-results/test/TEST-*.xml' \
    -o -path 'apps/shipping-api/build/test-results/databaseTest/TEST-*.xml' \) -type f | LC_ALL=C sort)" \
    || fail "the test reports cannot be listed"
  [[ -n "$reports" ]] || fail "the tests wrote no reports"
  links="$(find apps libs tools -path '*/build/test-results/*' -type l -print -quit)" \
    || fail "the test report directories cannot be searched for symbolic links"
  [[ -z "$links" ]] || fail "a test report directory contains a symbolic link"
  while IFS= read -r file; do
    [[ "$file" =~ $REPORT ]] || fail "an unexpected report path: $file"
  done <<< "$reports"
  tar --create --gzip --file "$WORK/reports.tar.gz" --sort=name --owner=0 --group=0 --numeric-owner \
    --mtime=@0 --files-from=- <<< "$reports" || fail "the reports cannot be archived"
  encoded="$(base64 --wrap=0 "$WORK/reports.tar.gz")" || fail "the reports cannot be encoded"
  (( ${#encoded} <= LIMIT )) || fail "the reports take ${#encoded} characters, more than $LIMIT fit the handover"
  sum="$(sha256sum "$WORK/reports.tar.gz" | cut -d ' ' -f 1)" || fail "the reports' digest cannot be computed"
  site_digest "$WORK/site.sha256"
  digest="$(cat "$WORK/site.sha256")"
  {
    echo "reports=$encoded"
    echo "reports-sha256=$sum"
    echo "site-sha256=$digest"
  } >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is not set}"
  echo "Handed over $(grep -c . <<< "$reports") reports (${#encoded} characters) and the site's digest."
}

receive() {
  [[ -n "${REPORTS:-}" && "$REPORTS" =~ ^[A-Za-z0-9+/]+={0,2}$ ]] || fail "the test job handed over no reports"
  local archive="$WORK/reports.tar.gz" names name types
  base64 --decode <<< "$REPORTS" > "$archive" || fail "the reports cannot be decoded"
  echo "${REPORTS_SHA256:?REPORTS_SHA256 is not set}  $archive" | sha256sum --check --strict --quiet \
    || fail "the reports changed in transit"
  names="$(tar --list --gzip --file "$archive")" || fail "the reports cannot be listed"
  while IFS= read -r name; do
    [[ "$name" =~ $REPORT ]] || fail "the handover holds an unexpected path: $name"
    [[ ! -e "$name" && ! -L "$name" ]] || fail "a report already exists in the checkout: $name"
  done <<< "$names"
  types="$(tar --list --verbose --gzip --file "$archive" | cut -c 1 | LC_ALL=C sort -u)" \
    || fail "the reports' types cannot be listed"
  [[ "$types" == "-" ]] || fail "the handover holds something other than regular files"
  tar --extract --gzip --file "$archive" --keep-old-files --no-same-owner --no-same-permissions \
    || fail "the reports cannot be unpacked"
  echo "Took over $(grep -c . <<< "$names") reports from the test job."
}

compare() {
  local built
  site_digest "$WORK/site.sha256"
  built="$(cat "$WORK/site.sha256")"
  [[ "$built" == "${SITE_SHA256:?SITE_SHA256 is not set}" ]] \
    || fail "the site differs from the one the test job built (digest $built, expected $SITE_SHA256)"
  echo "The site is the one the test job built: $built"
}

case "${1:-}" in
  send | receive | compare) "$1" ;;
  *) echo "usage: site-handover.sh send|receive|compare" >&2; exit 2 ;;
esac
