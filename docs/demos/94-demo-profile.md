---
title: "Run the shipping flow and a static page on one local origin"
description: "An attended launcher now starts a synthetic shipping demo, with disposable memory storage or an operator-prepared PostgreSQL database."
status: accepted
updated: 2026-11-09
pr: 94
kind: feature
recorded: 2026-11-09
tests: [DemoSmokeTest, DemoSecurityHeadersTest]
---

## What changed

Previously the API started only in test fixtures. The attended launcher now builds an executable archive, starts
the API on loopback, waits for readiness and stops its Java child when interrupted. The demo profile serves a
configured static directory on the same origin, with a strict Content-Security-Policy and the existing security
headers. Memory storage is the default; PostgreSQL is selected explicitly and prepared separately.

## See it

Read the [attended runbook](../flows/attended-demo.md) for prerequisites, the fixture, user selection and cleanup.
The [shipment flow](../flows/quote-book-label.md) explains the API operations. This change includes a small static
fixture; the separate workbench is not shown or claimed here.

## Try it

Use the reviewed JDK and the repository-root launcher command in the runbook. It prints the loopback URL and a
synthetic user hint. The fixture directory contains an HTML page and separate script/style files. The local smoke
test drives the API over real sockets. The ordinary check is:

```sh
./gradlew check
```

The owner accepted the 15 exact scenario framework findings for issue #92. The ledger records 215 known gaps
and still reports `COVERAGE_INCOMPLETE`; matching accepted debt does not prove scenario conformance.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-09, on macOS with Temurin 25.0.5+8, the launcher built the thin executable archive and ran memory mode
against the committed synthetic static fixture on a free loopback port. Curl exited 0 for the index page and
membership request. These are verbatim selected response headers; correlation IDs, timestamps, lengths and the
request's public fixture token are omitted:

```text
Cache-Control: no-store
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'
Content-Type: text/html
```

The authenticated membership response, recorded from the same process:

```text
{"userId":"00000000-0000-4000-8000-0000000000a1","userDisplayName":"Clerk Alpha (synthetic)","tenantId":"11111111-1111-4111-8111-111111111111","tenantDisplayName":"Tenant Alpha (synthetic)","role":"CLERK","enabledFlags":[]}
```

After interruption, the launcher exited 130 and the loopback connection was refused. Its nine refusal fixtures
also exited 0 as a test script, checking missing/wrong JDK, invalid arguments, missing static files and incomplete
PostgreSQL configuration without printing the synthetic password marker.

`DemoSmokeTest` and `DemoSecurityHeadersTest` are separately listed as current-build evidence. The smoke starts
production memory stores and exercises synthetic identity, quote, booking/replay, list, detail, labels, foreign
tenant denial, static types/CSP and traversal/symlink refusal. This demonstration belongs to pull request #94,
for issue #92.

## Limits

The recording establishes a local memory run only. The database-tagged `DemoPostgresSmokeIntegrationTest` is
implemented and compiled but must run through the reviewed harness in CI. Browser CSP enforcement, the actual
workbench, durable events, concurrent memory booking and production authentication are not established.
Memory storage resets when the process stops. Application logs are deliberately discarded by the launcher to
prevent framework connection diagnostics from revealing credentials. The scenario recorder still does not
observe these HTTP exchanges; the 15 new framework findings remain explicit accepted coverage debt.
