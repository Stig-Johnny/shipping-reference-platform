#!/usr/bin/env bash
# Scan the captured console and every test result/report; never print matching content.
# Exit 0 = clean, 1 = forbidden phrase, 2 = inspection could not complete.
set -euo pipefail
if [[ $# != 2 ]]; then
  echo 'Usage: bash check-generated-password.sh <candidate directory> <console log>' >&2
  exit 2
fi
candidate=$1
console=$2

inspection_error() {
  echo "::error::$*" >&2
  exit 2
}

[[ -d "$candidate" ]] || inspection_error 'The candidate directory is missing.'
[[ -f "$console" && -r "$console" && -s "$console" ]] \
  || inspection_error 'The required build console log is missing, unreadable or empty.'
outputs=$(mktemp) || inspection_error 'Cannot create the output manifest.'
trap 'rm -f "$outputs"' EXIT
# Do not use process substitution: its find status would be lost. Check discovery before reading its manifest.
if ! find "$candidate" \( -path '*/build/test-results/*' -o -path '*/build/reports/tests/*' \) \
    -type f -print0 > "$outputs"; then
  inspection_error 'Cannot discover all test output.'
fi
results=0
reports=0
while IFS= read -r -d '' output; do
  case "$output" in
    */build/test-results/*) results=1 ;;
    */build/reports/tests/*) reports=1 ;;
  esac
done < "$outputs"
[[ "$results" == 1 && "$reports" == 1 ]] || inspection_error 'Required test results or reports are missing.'

found=0
scan() {
  local output=$1 status
  [[ -f "$output" && -r "$output" ]] || inspection_error 'A required output file is missing or unreadable.'
  # Without -q: grep must finish reading even when it finds a match, so a later read error is not hidden.
  if grep -F -- 'Using generated security password' "$output" > /dev/null; then
    echo "::error::${output} contains Spring Boot's generated security password."
    found=1
  else
    status=$?
    [[ "$status" == 1 ]] || inspection_error "Cannot scan an output file (grep exit $status)."
  fi
}

scan "$console"
while IFS= read -r -d '' output; do
  scan "$output"
done < "$outputs"
exit "$found"
