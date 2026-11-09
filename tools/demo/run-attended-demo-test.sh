#!/usr/bin/env bash
# Refusal tests use synthetic java output; no server, Gradle or database is started.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
launcher="${1:-$here/run-attended-demo.sh}"
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/jdk/bin" "$fixture/web"
printf '<!doctype html><title>Synthetic fixture</title>\n' > "$fixture/web/index.html"
cp "$here/fake-jdk/java" "$fixture/jdk/bin/java"
chmod +x "$fixture/jdk/bin/java"
expect_refusal() {
  local label=$1 expected=$2 jdk=$3 actual=0
  shift 3
  JAVA_HOME="$jdk" bash "$launcher" "$@" > "$fixture/output" 2>&1 || actual=$?
  if [[ $actual != 2 ]] || ! grep -Fq "$expected" "$fixture/output"; then
    printf 'FAIL: %s (exit %s)\n' "$label" "$actual"
    exit 1
  fi
  if grep -Fq 'synthetic-password-marker' "$fixture/output"; then
    printf 'FAIL: credential was printed\n'
    exit 1
  fi
  printf 'PASS: %s (exit 2)\n' "$label"
}
expect_refusal 'missing JAVA_HOME' 'JAVA_HOME' ''
expect_refusal 'missing java executable' 'JAVA_HOME' "$fixture/missing"
expect_refusal 'invalid mode' 'mode' "$fixture/jdk" invalid
expect_refusal 'invalid port' 'port' "$fixture/jdk" memory 0 "$fixture/web"
expect_refusal 'non-numeric port' 'port' "$fixture/jdk" memory abc "$fixture/web"
expect_refusal 'extra arguments' 'usage' "$fixture/jdk" memory 8080 "$fixture/web" extra
expect_refusal 'missing index' 'index.html' "$fixture/jdk" memory 8080 "$fixture/missing"
export SPRING_DATASOURCE_URL='' SPRING_DATASOURCE_USERNAME='' SPRING_DATASOURCE_PASSWORD=synthetic-password-marker
expect_refusal 'missing postgres environment' 'SPRING_DATASOURCE' "$fixture/jdk" postgres 8080 "$fixture/web"
sed 's/25.0.5+8/25.0.5+9/g' "$fixture/jdk/bin/java" > "$fixture/wrong-java"
cp "$fixture/wrong-java" "$fixture/jdk/bin/java"
expect_refusal 'wrong JDK build' '25.0.5+8' "$fixture/jdk" memory 8080 "$fixture/web"
