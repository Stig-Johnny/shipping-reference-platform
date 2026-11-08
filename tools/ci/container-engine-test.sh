#!/usr/bin/env bash
# Tests of tools/ci/container-engine.sh, with bash alone: tools/ci/fake-engine/, first on PATH, answers the queries that
# the staging and the verification make, and fails the one a case names. A query that fails must stop the script with an error,
# never let it pass on an empty answer. The version comparator must refuse a prerelease of its floor.
#
# Run from anywhere: bash tools/ci/container-engine-test.sh   It prints one line per case and exits 1 if any failed.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/container-engine.sh"
image="postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
passed=0
failed=0


# Runs the script with the fake docker, in a fresh runner directory per case unless the case keeps one.
engine() {
  PATH="$here/fake-engine:$PATH" RUNNER_TEMP="$work/runner" GITHUB_STEP_SUMMARY="$work/summary" \
    DOCKER_CONFIG="$work/docker-config" FAKE_STATE="$work/state" bash "$script" "$@" > "$work/output" 2>&1
}

fresh() {
  rm -rf "$work/runner" "$work/state" "$work/docker-config"
  mkdir -p "$work/runner" "$work/state" "$work/docker-config"
  printf '{}\n' > "$work/docker-config/config.json"
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

# A clean staging and verification pass.
fresh
engine stage; s=$?
engine verify; v=$?
check "the staging and the verification pass when every query answers" pass $((s + v))

# Every query of the staging that fails stops it.
for query in "image ls" "pull" "image inspect"; do
  fresh
  FAKE_FAIL="$query" engine stage
  check "a failed '$query' stops the staging" fail $? "::error::"
done

# Every query of the verification that fails stops it, whatever the other queries say.
for query in "image ls" "events" "ps -aq" "network ls"; do
  fresh
  engine stage
  FAKE_FAIL="$query" engine verify
  check "a failed '$query' stops the verification" fail $? "::error::"
done
fresh
engine stage
FAKE_LEFTOVER=9f8e FAKE_FAIL="rm -f -v" engine verify
check "a failed removal stops the verification" fail $? "::error::"

# What the verification is there to find.
fresh
engine stage
FAKE_EXTRA_IMAGE="sha256:dead alpine 3.17 sha256:beef" engine verify
check "an image that appeared during the tests fails the verification" fail $? "An image that has no review appeared"
fresh
engine stage
FAKE_EVENTS="container create 1a2b alpine:3.17" engine verify
check "a container made from another image fails the verification" fail $? "A container was made from alpine:3.17"
fresh
engine stage
FAKE_EVENTS="container extract-to-dir 1a2b $image" engine verify
check "a copy into a container fails the verification" fail $? "Files were copied into a container"
fresh
engine stage
FAKE_LEFTOVER=9f8e engine verify
check "a container left by the tests is swept and the verification passes" pass $?

# The engine keeps its last 256 events: a window with that many cannot be shown complete, and no failure of the
# count may read as a count of 0.
many="$(for n in $(seq 1 300); do echo "container create c$n $image"; done)"
fresh
engine stage
FAKE_EVENTS="$many" engine verify
check "a window of 300 events fails the verification" fail $? "The tests produced 300 engine events"
fresh
engine stage
FAKE_EVENTS="$many" FAKE_GREP_FAIL=1 engine verify
check "a count that fails is no count of 0: 300 events still fail the verification" fail $? "300 engine events"
fresh
engine verify
check "a verification without a staging fails" fail $? "nothing was staged"

# The version comparator, as the script defines it: a prerelease of the floor is below it.
sed -n '/^at_least() {/,/^}/p' "$script" > "$work/comparator.sh"
for pair in "1.3.3 yes" "1.3.4 yes" "v1.3.3 yes" "1.4.0-rc.3 yes" "1.3.4-rc.1 yes" "1.10.0 yes" "2.0.0 yes" \
    "1.3.3-rc.1 no" "v1.3.3-rc.2 no" "1.3.3+dirty no" "1.3.2 no" "1.2.8 no" "1.3.0-rc.1 no" "1.3 no" "unknown no" "no"; do
  version="${pair% *}"
  [[ "$pair" == "no" ]] && version=""
  expected="${pair##* }"
  if bash -c 'source "$1" && at_least "$2" 1.3.3' _ "$work/comparator.sh" "$version"; then got=yes; else got=no; fi
  : > "$work/output"
  check "runc '$version' against the floor 1.3.3: $expected" pass "$([[ "$got" == "$expected" ]] && echo 0 || echo 1)"
done

echo
echo "$passed passed, $failed failed"
(( failed == 0 && passed > 0 ))
