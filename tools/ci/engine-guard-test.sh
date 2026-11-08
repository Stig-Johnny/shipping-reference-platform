#!/usr/bin/env bash
# Execute every workflow's inline guard with fixtures. Never contacts a real engine or runs sudo.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
export PATH="$here/fake-engine-guard:$PATH"
export FAKE_GUARD_LOG="$work/calls"
passed=0
guards=0
for workflow in "$here"/../../.github/workflows/*.yml; do
  count="$(awk '/# engine-guard: begin/ { n++ } END { print n+0 }' "$workflow")"
  for ((n=1; n<=count; n++)); do
    awk -v wanted="$n" '/# engine-guard: begin/ { n++; active=(n==wanted); next }
      /# engine-guard: end/ { active=0 } active { sub(/^          /, ""); print }' "$workflow" > "$work/guard.sh"
    guards=$((guards + 1))
    for scenario in stopped cli-reachable socket-reachable stop-failed mask-failed unlink-failed cli-error socket-error cli-timeout; do
      : > "$FAKE_GUARD_LOG"
      export FAKE_GUARD_SCENARIO="$scenario"
      status=0
      bash "$work/guard.sh" > "$work/output" 2>&1 || status=$?
      case "$scenario" in
        stopped)
          expected_status=0
          ;;
        cli-reachable|socket-reachable)
          expected_status=1
          grep -qF '::error::Container engine is reachable' "$work/output" || exit 1
          ;;
        stop-failed|mask-failed|unlink-failed) expected_status=1;;
        cli-error) expected_status=127;;
        socket-error) expected_status=2;;
        cli-timeout) expected_status=124;;
      esac
      if [[ "$status" != "$expected_status" ]]; then
        echo "FAIL $workflow guard $n: $scenario exited $status, expected $expected_status"
        exit 1
      fi
      # Shutdown must precede both probes. A failed operation stops immediately, without later calls.
      printf '%s\n' 'systemctl stop docker.service docker.socket' > "$work/expected"
      if [[ "$scenario" != stop-failed ]]; then
        printf '%s\n' 'systemctl mask --runtime docker.service docker.socket' >> "$work/expected"
        if [[ "$scenario" != mask-failed ]]; then
          printf '%s\n' 'rm -f /var/run/docker.sock' >> "$work/expected"
          if [[ "$scenario" != unlink-failed ]]; then
            if [[ "$scenario" == cli-timeout ]]; then
              echo cli-timeout >> "$work/expected"
            else
              echo info >> "$work/expected"
              if [[ "$scenario" != cli-reachable && "$scenario" != cli-error ]]; then
                echo socket-probe >> "$work/expected"
              fi
            fi
          fi
        fi
      fi
      diff -u "$work/expected" "$FAKE_GUARD_LOG"
      passed=$((passed + 1))
    done
  done
done
if [[ "$guards" != 8 ]]; then
  echo "FAIL expected 8 engine guards, found $guards"
  exit 1
fi
echo "$passed passed: 8 workflow guards reject reachable CLI/socket, failed stop/mask/unlink and probe errors/timeouts; shutdown precedes probes; stopped passes"
