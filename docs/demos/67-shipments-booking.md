---
title: "Booking is checked against two more arrows of the diagram"
description: "The shipments module books against quotes. Its arrows to quotes and to tenants are now checked against real code, and the model alone fails the build until that code exists."
status: accepted
updated: 2026-11-07
pr: 67
kind: gate
recorded: 2026-11-07
tests: [ArchitectureConformanceTest, ModularityTest, BookShipmentTest]
---

## What changed

Before, two modules were built and the checker evaluated one arrow of the diagram, quotes to tenants. Now the
shipments module books shipments from quote offers. It applies the specified order of checks, returns the same
shipment when a booking is sent again with the same key and request, refuses a reused key with another request, and
refuses an expired or foreign offer and a recipient outside the quote's destination. The model marks shipments as
built, so the checker evaluates three arrows between built modules; the two new ones, shipments to quotes and
shipments to tenants, are backed by real references.

## Try it

From the repository root, with the reviewed JDK as `JAVA_HOME`, run the build; it passes. The checker's report
lists each arrow with its number of references.

```sh
./gradlew check
```

The checker's report is `apps/shipping-api/build/reports/c4-contract/report.md`.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07T21:53:30Z on macOS with Temurin 25.0.5+8, on revision f288ce8 of pull request #67, the commit
that marks shipments as built before any shipments code exists. `./gradlew check` exited 1; excerpt of the checker's
report (its tables are left out):

```text
**Verdict: FAILED** with 3 violations. Model: `../../architecture/workspace.dsl`. Report format: `c4-contract-kit/report/v1`.
### ABSENCE: shipments -> quotes
The model requires shipments -> quotes, but no class of shipments references a class of quotes.
### ABSENCE: shipments -> tenants
The model requires shipments -> tenants, but no class of shipments references a class of tenants.
### BUILT_COMPONENT_EMPTY: shipments
shipments is built, but its packages contain no class.
```

Recorded 2026-11-07T21:51:59Z on revision dec225f of pull request #67, with the booking code: `./gradlew check`
exited 0; excerpt of the checker's report:

```text
**Verdict: PASSED** with no violations. Model: `../../architecture/workspace.dsl`. Report format: `c4-contract-kit/report/v1`.
| quotes | tenants | REQUIRED | convergence | 13 |
| shipments | quotes | REQUIRED | convergence | 44 |
| shipments | tenants | REQUIRED | convergence | 13 |
```

The test classes named above check the API against the model in every build. Reviewed in pull request #67.

## Limits

- Booking runs in its tests only: this change has no HTTP layer and no database. The transaction, the advisory lock
  and the handling of unique keys belong to the planned database adapter.
- The check compares direct static dependencies with the model; it does not show that booking behaves correctly.
  Weakening the behaviour behind an arrow, for example reporting an expired offer as not found, keeps the
  architecture check green and fails `BookShipmentTest` instead.
- Classes used only as type patterns in a `switch` leave no dependency the checker can see (issue #69).
- Arrows that touch planned modules (audit, orders, tracking and returns) are not evaluated until those modules are
  built.
