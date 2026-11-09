---
title: "ADR 0018: An attended demo on one origin with selectable storage"
description: "The demo profile serves a configured build directory, defaults to disposable memory stores, and selects existing JDBC adapters with postgres."
status: accepted
updated: 2026-11-09
date: 2026-11-09
decision-makers: repository owner
enforcedBy:
  - "java: DemoSmokeTest, DemoSecurityHeadersTest, ExternalImportPolicyTest"
plannedEnforcement:
  - "databaseTest: DemoPostgresSmokeIntegrationTest; implemented, CI execution pending"
---

# ADR 0018: An attended demo on one origin with selectable storage

## Context and Problem Statement

Issue #92 needs a runnable API before the separate workbench is merged. ADR 0012's database design and module
boundaries stay intact. An attended run must use the reviewed toolchain and no new dependency or container launcher.

## Considered Options

1. Add the Boot plugin and a self-contained archive, requiring a new dependency review.
2. Use the existing Gradle packaging tasks and keep reviewed runtime jars beside a thin executable archive.

Choose option 2; it meets attended startup without changing the reviewed dependency set.

## Decision Outcome

The `demo` profile supplies tenant-keyed in-memory ports by default. `postgres` disables those defaults and selects
the existing JDBC adapters. Explicit test ports take precedence through Spring's fallback-bean mechanism.
The tenants web adapter owns the public static edge because it already owns demo authentication and security.
No module dependency, package mapping or module status changes.

A configured `shipping.demo.static-directory` enables a separate static security chain outside `/api` and a file
controller. It serves only regular files whose normalized and real paths stay inside the directory. Operators
supply an immutable trusted build. GET/HEAD use exact media types; unknown paths do not become the index page.
One shared CSP constant and configuration call retains default Spring security headers on both chains; the SVG
label keeps its stricter policy. This provides HTTP header evidence, not browser execution or a compiler import graph.

Gradle's existing Jar and Sync tasks package a thin executable archive and unmodified runtime jars. No Boot Gradle
plugin or loader is added. The attended Bash launcher checks Temurin 25.0.5+8, builds with the wrapper, binds
loopback, waits for readiness and reaps its child on exit. Application logs are discarded to prevent third-party
connection diagnostics from exposing credentials. Standard Spring datasource environment variables reach the
application only; Gradle receives none of those three variables.

PostgreSQL is prepared separately: migrations and the synthetic demo seed run as `shipping_owner`, runtime runs
as `shipping_app`. Runtime Flyway is off in the demo. The seed is an explicit operator SQL script, `db/demo/synthetic-tenants.sql`, not a versioned Flyway migration
or part of the ordinary migration location. This limited demo leaves automatic seed migrations to later work.
Modulith durable event infrastructure is disabled in this limited profile while its table and consumers remain
planned; ADR 0006's production decision stands. Memory storage supports sequential attended use, not durable or
concurrent booking guarantees.

## Consequences

The launcher is runnable with the reviewed toolchain alone in memory mode. Operators must preserve the sibling
library directory and prepare PostgreSQL independently. Logs cannot aid startup diagnosis because they are
intentionally discarded; the public diagnostics remain credential-free.

## Confirmation

`DemoSmokeTest` exercises the production memory runtime over real sockets with synthetic fixtures and validates
all API responses against OpenAPI. It checks static files, CSP, traversal and symlink containment, authentication,
quote, booking/replay, list, detail, label and foreign-tenant denial. The shared PostgreSQL subclass is tagged
`database` and uses ReviewedPostgres through the existing harness; execution awaits CI. Shell refusal tests and a
live launcher run supplement JUnit. [The runbook](../flows/attended-demo.md) gives preparation and cleanup.

New scenario census findings remain unaccepted until the owner reviews their exact proposed rows. The ledger
still accepts only its existing 200 findings; line-only updates preserve those findings. Neither smoke tests nor
CSP make the scenario recorder observe HTTP exchanges, JDBC receiver behavior or browser execution.
