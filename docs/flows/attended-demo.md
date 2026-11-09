---
title: "Run an attended shipping demo"
description: "Start a loopback-only API and static workbench with disposable memory stores or an already prepared PostgreSQL database, using synthetic users."
status: accepted
updated: 2026-11-09
modules: [tenants, quotes, shipments]
---

# Run an attended shipping demo

Use the reviewed Temurin JDK 25.0.5+8, the repository Gradle wrapper, Bash and curl. Set `JAVA_HOME` to that
JDK and put its `bin` first on `PATH`. The launcher checks `java -version`; it does not download a JDK.
Use synthetic data only. The workbench runs against the API in this profile; a small HTML/JS/CSS fixture also remains available for serving checks.
The launcher binds to `127.0.0.1` and stays in the foreground until interrupted.

## Memory mode

From the repository root, with reviewed Node and the installed compiler on PATH, compile and stage the
workbench (`apps/shipping-web/README.md` describes the checks):

```sh
npm ci --ignore-scripts
tsc -p apps/shipping-web
cp apps/shipping-web/index.html apps/shipping-web/dist/index.html
cp apps/shipping-web/public/workbench.css apps/shipping-web/dist/workbench.css
bash tools/demo/run-attended-demo.sh memory 8080
```

The arguments are mode (`memory` by default), port (`8080` by default), and static directory
(`apps/shipping-web/dist` by default). Pass `tools/demo/fixture` as the third argument only to inspect the static-serving fixture.
It must contain `index.html`. Files are served from this directory only; there is no directory listing or general SPA fallback. Only the documented workbench page routes serve the entry file;
unknown paths, missing assets and API requests never fall back to HTML. Give the process read-only access to an immutable, trusted build directory. Do not put secrets there.
The server checks normalized and real paths, including symlink destinations, on each request. API paths remain
behind the API authentication chain. CSP forbids inline script/style, remote resources, framing and base URLs.
Early malformed-request JSON 400 responses from the path filter and strict firewall precede those chains and
carry correlation/no-store headers without their CSP or default security headers. Outer fallback/reset responses
also do not retain those chain-written headers.

Gradle's root `bootJar` task packages an executable **thin** archive at
`apps/shipping-api/build/demo/shipping-demo.jar`, alongside its `lib` directory. Keep both together.
It uses the existing reviewed Gradle Jar/Sync tasks and resolved runtime artifacts; no Boot plugin, loader,
new dependency or runtime download is introduced. The launcher runs this archive with `java -jar`.

The printed URL opens the workbench. Choose `clerk-alpha` or `clerk-beta`. Their public fixture tokens and the no-membership
user are documented in [the tenants module](../modules/tenants.md#demo-authentication).
There is no new sign-in endpoint: each API request carries the selected public bearer token and `/api/v1/me`
returns its membership. Memory mode forgets quotes, bookings and tracking numbers when stopped. It is for
sequential attended use, not durable storage or concurrent booking guarantees.

## PostgreSQL mode

Provision a disposable PostgreSQL database through the approved operator workflow. Bootstrap the roles using
`apps/shipping-api/postgres/init-roles.sql`; run the existing V1–V3 migrations as `shipping_owner`, and then
`apps/shipping-api/src/main/resources/db/demo/synthetic-tenants.sql` once in a transaction as that owner.
The seed contains only the documented identity fixtures; it does not create flags, quotes or shipments.
Runtime credentials must belong to `shipping_app`, never the owner or superuser. Runtime Flyway is disabled in
the demo, so it cannot silently migrate with the runtime role. This launcher neither provisions nor starts a database.

Supply `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` through the operator's
private environment mechanism. Do not paste credentials into command lines, recordings, the repository or logs.
Then run:

```sh
bash tools/demo/run-attended-demo.sh postgres 8080 tools/demo/fixture
```

This selects `demo,postgres` and the existing JDBC adapters and Spring transaction manager. Spring reads the
standard environment variables. The launcher checks presence without printing values, removes them from the
Gradle environment, and discards application stdout/stderr because framework connection failures can disclose URLs.
A startup failure prints only a fixed diagnostic. Check the port, directory and prepared database through the
approved operator workflow; do not enable credential-bearing debug logging. Durable event publication is disabled
for both demo modes until its schema and listeners exist (ADR 0006 remains the production target).

## Readiness, verification and cleanup

The launcher checks that its IPv4 loopback port is free before the build and again before starting Java. An existing
HTTP or non-HTTP listener is refused without contacting it. Run one launcher at a time: another process can still
claim the port after a check, and child liveness alone does not establish ownership of a socket.
Readiness polls the public index for up to 60 attempts, with a one-second request timeout. The launcher verifies
that the child is alive and prints the URL and synthetic sign-in hint. Interrupt with Ctrl-C; EXIT/INT/TERM cleanup
terminates and waits for the Java child. If startup fails or readiness times out it also cleans up. Memory resets;
PostgreSQL data persists and must be removed by the operator's database workflow when no longer needed.

```sh
bash tools/demo/run-attended-demo-test.sh
java tools/demo/LoopbackPortCheckTest.java
./gradlew :apps:shipping-api:test --tests '*DemoSmokeTest' --tests '*DemoSecurityHeadersTest'
java tools/policy/PolicyCheck.java .
```

The nine shell refusal fixtures do not start Gradle, Java servers or PostgreSQL. The JDK port regressions create
temporary loopback HTTP and non-HTTP listeners and require refusal before Gradle or readiness. Both run in Java CI.
The real-HTTP `DemoSmokeTest`
boots production memory stores on a random port with fixed test clocks/IDs, verifies static content types/CSP,
rejects traversal and escaping symlinks, and drives authentication, quote, book, replay, list, detail, label and
cross-tenant denial. Every API exchange passes the existing contract helper.
`DemoPostgresSmokeIntegrationTest` runs those same assertions with live JDBC proxies and the reviewed harness,
under the mandatory `database` tag in CI; local compilation is not PostgreSQL evidence.

The live local launcher recording and rendered-page review are described in the public demo sources `docs/demos/94-demo-profile.md` (runtime) and
`docs/demos/87-web-workbench-slice.md` (integrated workbench).
See [ADR 0018](../adr/0018-attended-demo-runtime.md) for boundaries and
[known gaps](../gaps.md#0-state-of-this-commit) for what this does not prove.

## Recorded database evidence

CI run `42612173809`, recorded 2026-11-09, ran all ten database classes through the reviewed PostgreSQL harness:
45 tests, zero failures and zero skips. The per-class report for `DemoPostgresSmokeIntegrationTest` contains three
passing real-HTTP cases: the synthetic shipping flow, static types/headers, and traversal/symlink refusal.
The other nine classes cover migration round trips, runtime roles/RLS, membership, quote storage, idempotent
booking and shipment listing. This is a recording; ordinary local builds do not read a previous CI report as their
own evidence. CI and Pages require database reports from their own run. No database or container was started locally.
