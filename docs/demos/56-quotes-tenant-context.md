---
title: "Code that contradicts the diagram now fails the shipping API's build"
description: "The architecture model governs real application code: quotes and the tenant context are built, and a dependency the diagram does not allow stops the build, naming the classes."
status: accepted
updated: 2026-11-07
pr: 56
kind: gate
recorded: 2026-11-07
tests: [ArchitectureConformanceTest, ModularityTest, HexagonalLayeringTest]
---

## What changed

Before, the checker that holds code to the architecture model ran only on its own library: the shipping API had no
code. Now the quotes module (the synthetic tariff, rating, quote creation and offer lookup) and the tenant context are
built, the model marks both as built, and every build checks the API's compiled classes against the model. The
required arrow from quotes to tenants is backed by real references; a reference the model does not have fails the
build, and so do a cycle between modules and a breach of the layering rules.

## Try it

From the repository root, with the reviewed JDK as `JAVA_HOME`, run the build; it passes. Then add a reference from
the tenants module to quotes, for example a method in `tenants.api.TenantId` that takes a `quotes.api.QuoteId`, run
the build again, and undo the edit afterwards.

```sh
./gradlew check
```

The checker's report is `apps/shipping-api/build/reports/c4-contract/report.md`.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07T20:28:31Z on macOS with Temurin 25.0.5+8, on revision e903193 of pull request #56. With a method added to
`TenantId` that takes two `quotes.api.QuoteId` values, `./gradlew check` exited 1; excerpt of the checker's report
(its tables are left out):

```text
**Verdict: FAILED** with 1 violation. Model: `../../architecture/workspace.dsl`. Report format: `c4-contract-kit/report/v1`.
### DIVERGENCE: tenants -> quotes
tenants depends on quotes, but the model declares no relationship tenants -> quotes.
```

The same build also failed `ModularityTest` (a cycle between the modules). With the edit undone, `./gradlew check`
exited 0; excerpt of the report:

```text
**Verdict: PASSED** with no violations. Model: `../../architecture/workspace.dsl`. Report format: `c4-contract-kit/report/v1`.
```

In that passing build the arrow `quotes -> tenants` (REQUIRED) converged with 13 references. The test classes named
above check the API against the model in every build. Reviewed in pull request #56.

## Limits

- The check compares direct static dependencies in compiled classes with the model; it does not show that the code
  behaves correctly, which the module's own tests cover.
- An arrow that touches a planned module (shipments, audit and the others) is not evaluated until that module is
  built.
- The API does not run yet: there is no HTTP layer, persistence or database in this change.
