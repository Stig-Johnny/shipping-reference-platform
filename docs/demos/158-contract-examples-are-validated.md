---
title: "The contract's own examples fail the build when they drift from it"
description: "Every example in the OpenAPI contract is now validated against its schema in every build, and a problem example against the error registry and its response, so an example that a reader or a client copies cannot quietly go wrong."
status: accepted
updated: 2026-11-23
pr: 158
kind: gate
recorded: 2026-11-23
tests: [ContractExamplesTest]
---

## What changed

Before, nothing read the examples in `contracts/shipping.openapi.yaml`: an example could break its own schema, or a
problem example could carry another type's title, and every check still passed. Now `ContractExamplesTest` reads all
nine of them, the eight named examples of request and response bodies and the `Idempotency-Key` parameter's example,
and validates each against its schema with the validator settings of the HTTP contract tests. A problem example must
also be its type's problem in the error registry, have its response's status, and be of a type that its response's
description names. An example in a place the test does not read fails, so a new kind of example cannot go unchecked.
The test is evidence of the HTTP contract control and of ADR 0012, whose page on the site now counts nine evidence
classes.

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, change the title of the
`routeNotServed` example in `contracts/shipping.openapi.yaml`, under `createQuote`'s 422 response, from No offer
available to No offers available. Then run:

```sh
./gradlew check
```

It fails, and the test report names the example by its JSON pointer and the registry's title it expected. Cleanup:
put the title back, and run the same command: it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-23, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change, with the fixture above.
Instead of the whole `./gradlew check`, the API's test task ran, filtered to `ContractExamplesTest`, and exited 1. The
excerpt leaves out the other tasks' lines and Gradle's failure summary, and ends with the failure message that the
test report records for the first test. The second test fails as well, because the passage of the contract that its
fixture edits is no longer there:

```text
> Task :apps:shipping-api:test FAILED
ContractExamplesTest > everyExampleOfTheContractConformsToItsSchemaAndItsResponse() FAILED
ContractExamplesTest > aProblemExampleWhoseTitleIsNotItsTypesFails() FAILED
14 tests completed, 2 failed
BUILD FAILED in 3s
org.opentest4j.AssertionFailedError: [#/paths/~1quotes/post/responses/422/content/application~1problem+json/examples/routeNotServed: the registry's title for https://shipping-reference-platform.example/problems/no-offer-available]
expected: "No offer available"
 but was: "No offers available"
```

After the cleanup, the same task ran the 14 tests again, all passing, and exited 0. The excerpt shows the console's
last line and the counts of the test report:

```text
BUILD SUCCESSFUL in 2s
tests="14" skipped="0" failures="0" errors="0"
```

## Limits

- The test holds the examples to the contract, not to the code: it does not run the example request against the API,
  so the example quote's prices are not compared with the synthetic tariff. The HTTP contract tests use their own
  requests.
- An example's `instance` and `correlationId` are checked for their form only.
- The site's API pages show the eight named examples; the parameter's example is validated but not shown there.
- A header's example, a schema's examples and a shared example under `components/examples` fail the test instead of
  being validated: using one means extending the test first.
- The negative fixtures edit passages of the contract's text, so editing such a passage means updating its fixture
  too; the failure names the passage.
