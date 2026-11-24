---
title: "A misspelled problem type in the contract fails the documentation check"
description: "The documentation check now reads the contract's problem registry strictly, and every problem response must start its description with registered types of its own status, so a typo cannot drop a problem out of the tables generated from the contract."
status: accepted
updated: 2026-11-24
pr: 160
kind: gate
recorded: 2026-11-24
tests: [ContractProblemsTest]
---

## What changed

Before, the documentation tool did not read the contract's problem registry, and a response named its problem types
only in its prose. The API's example test read them only for the responses that carry a problem example, so a
misspelled type in any other response, such as booking's 404, or a type under the wrong status passed every check.
Now the check reads the registry, the table under Errors (RFC 9457) in the contract's description, and fails on a
malformed row, a type registered twice or an owner that is not any module, the HTTP edge or a module of the contract.
Every response with status 400 or above must start its description with its problem types, each registered with that
status; code spans later in the description are prose. The tables that issue 155 generates from the contract read
these types, so none can drop out of them unnoticed (ADR 0025).

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, change the description
of `bookShipment`'s 404 response in `contracts/shipping.openapi.yaml` from `quote-not-found` or `offer-not-found` to
`quote-not-found` or `offer-not-fund`. Then run:

```sh
./gradlew :tools:docs:check
```

It fails, and the test report names the operation, the status and the unknown type. Cleanup: put the type back, and
run the same command: it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-24, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change, with the fixture above,
`./gradlew :tools:docs:check` exited 1. The documentation tests copy the repository, fixture included, so they failed
first. The excerpt shows the console's last lines, then the failure message that the test report of the same run
records for `BrainCheckTest`'s repository test:

```text
1047 tests completed, 105 failed
> Task :tools:docs:test FAILED
BUILD FAILED in 2m 46s
io.github.stigjohnny.docs.DocsException: the documentation sources are inconsistent (1 problem):
  - contracts/shipping.openapi.yaml bookShipment 404: `offer-not-fund` is no type of the problem registry (ADR 0025)
```

After the cleanup, `./gradlew :tools:docs:check` ran the documentation tests again, all passing, and exited 0. The
excerpt leaves out other tasks' lines:

```text
> Task :tools:docs:test
> Task :tools:docs:checkGenerated
docs: the 22 generated files are up to date
> Task :tools:docs:site
docs: wrote the site, 127 files
> Task :tools:docs:check
BUILD SUCCESSFUL in 4m 9s
```

## Limits

- The check reads the problem types that a description names first, in the form ADR 0025 sets; it cannot tell
  whether the prose after them is true.
- It reads the responses of operations only. A response component that no operation uses is not read, and the
  problems that the contract allows on any path are read only where an operation documents them.
- Which module raises a type is the registry's claim; nothing compares it with the code yet. The capability bindings
  (issue 157) and the generated tables (issue 155) build on it.
