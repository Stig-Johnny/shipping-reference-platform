#!/usr/bin/env bash
# Attended, loopback-only demo. Database credentials stay in the inherited environment.
set -euo pipefail
refuse() { printf '%s\n' "$1" >&2; exit 2; }
[[ $# -le 3 ]] || refuse 'usage: run-attended-demo.sh [memory|postgres] [port] [static-directory]'
[[ -n ${JAVA_HOME:-} && -x "$JAVA_HOME/bin/java" ]] || refuse 'JAVA_HOME must name the reviewed JDK 25.0.5+8'
export PATH="$JAVA_HOME/bin:$PATH"
version=$(java -version 2>&1) || refuse 'Cannot check JAVA_HOME java -version'
[[ "$version" == *'openjdk version "25.0.5"'* && "$version" == *'Temurin-25.0.5+8 (build 25.0.5+8-LTS)'* ]] \
  || refuse 'JAVA_HOME must name the reviewed Temurin JDK 25.0.5+8'
export PATH="$JAVA_HOME/bin:$PATH"
mode=${1:-memory}
port=${2:-8080}
root="$(cd "$(dirname "$0")/../.." && pwd)"
static_directory=${3:-$root/apps/shipping-web/dist}
case "$mode" in
  memory) profiles=demo ;;
  postgres)
    profiles=demo,postgres
    [[ -n ${SPRING_DATASOURCE_URL:-} && -n ${SPRING_DATASOURCE_USERNAME:-} && -n ${SPRING_DATASOURCE_PASSWORD:-} ]] \
      || refuse 'postgres requires SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD'
    ;;
  *) refuse 'mode must be memory or postgres' ;;
esac
[[ "$port" =~ ^[1-9][0-9]{0,4}$ ]] && (( port <= 65535 )) || refuse 'port must be an integer from 1 to 65535'
[[ -d "$static_directory" && -f "$static_directory/index.html" ]] || refuse 'static-directory must contain index.html'
static_directory="$(cd "$static_directory" && pwd -P)"
command -v curl >/dev/null || refuse 'curl is required for readiness'
cd "$root"
# No database credentials are needed by Gradle, and no build output can reveal them.
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD \
  ./gradlew :bootJar
# Framework connection errors may contain URLs. Do not print or persist application logs in this attended launcher.
# Diagnose an unavailable prepared database through the operator's approved database workflow.
java -jar apps/shipping-api/build/demo/shipping-demo.jar \
  --spring.profiles.active="$profiles" --server.address=127.0.0.1 --server.port="$port" \
  --shipping.demo.static-directory="$static_directory" > /dev/null 2>&1 &
child=$!
cleanup() {
  kill "$child" 2>/dev/null || true
  wait "$child" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
ready=false
for ((attempt=0; attempt<60; attempt++)); do
  kill -0 "$child" 2>/dev/null || refuse 'Demo failed to start; check port, static directory and prepared database'
  if curl --noproxy '*' --silent --fail --max-time 1 "http://127.0.0.1:$port/" > /dev/null; then
    # Confirm our child still owns a running server, not an already-running service on the requested port.
    sleep 1
    kill -0 "$child" 2>/dev/null || refuse 'Demo failed to start; port may already be occupied'
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || refuse 'Demo readiness timed out'
printf 'Demo ready: http://127.0.0.1:%s/\n' "$port"
printf 'Synthetic sign-in: choose clerk-alpha or clerk-beta (public demo tokens documented in docs/modules/tenants.md).\n'
printf 'Press Ctrl-C to stop. Memory mode loses all data at exit.\n'
wait "$child"
