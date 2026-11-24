---
title: "Every HTTP operation names the capabilities that serve it, and the planned one says so"
description: "Each operation and each module problem type of the contract is now bound to the capabilities that serve or raise it, the documentation check fails on one that no capability names, and the cancellation operation shows as planned on the brain and the API pages."
status: accepted
updated: 2026-11-24
pr: 161
kind: gate
recorded: 2026-11-24
tests: [OperationBindingsTest, SitePagesTest]
---

## What changed

Before, nothing said which HTTP operation is built. The contract holds `cancelShipment`, which no code serves, and
the generated brain page listed it like any other operation; each operation's page on the site showed the statuses of
every capability of its module. Now each capability in `docs/capabilities/capabilities.yaml` names the operations that
serve it and the problem types it raises, and the documentation check fails on an operation, or a problem type that
the registry gives a module, that no capability of that module names. An operation is planned while every capability
that names it is planned: the brain marks `cancelShipment` as planned, and its page on the site says Planned, with no
evidence. Every other operation's page shows its own capabilities and their passing tests (ADR 0025).

## See it

- [The cancellation operation](https://stig-johnny.github.io/shipping-reference-platform/api/cancelShipment.html):
  its status panel says Planned, one capability planned, and no passing evidence.
- [The booking operation](https://stig-johnny.github.io/shipping-reference-platform/api/bookShipment.html): its two
  partial capabilities and their own five evidence tests.
- [The shipments module's brain](../brain/shipments.md): `cancelShipment` is marked planned among the HTTP operations.

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, delete the line
`operations: [getQuote]` from the capability `quotes.get-quote` in `docs/capabilities/capabilities.yaml`, so that no
capability names the operation. Then run:

```sh
./gradlew :tools:docs:check
```

It fails, and the test report names the operation whose state is unknown. Cleanup: put the line back, and run the
same command: it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-24, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change, with the fixture above,
`./gradlew :tools:docs:check` exited 1. The documentation tests copy the repository, fixture included, so they failed
first. The excerpt shows the console's last lines, then the failure message that the test report of the same run
records for `BrainCheckTest`'s repository test:

```text
1074 tests completed, 107 failed
> Task :tools:docs:test FAILED
BUILD FAILED in 2m 40s
io.github.stigjohnny.docs.DocsException: the documentation sources are inconsistent (1 problem):
  - contracts/shipping.openapi.yaml getQuote: no capability of docs/capabilities/capabilities.yaml names this operation, so its state is unknown (ADR 0025)
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
BUILD SUCCESSFUL in 4m 1s
```

In the site that this build wrote, read as HTML and not in a browser, [the cancellation page](https://stig-johnny.github.io/shipping-reference-platform/api/cancelShipment.html)'s
status panel said Planned, "1 capability planned (shipments.cancel-shipment)", and None yet for its evidence; [the
booking page](https://stig-johnny.github.io/shipping-reference-platform/api/bookShipment.html) said Partly shipped,
two capabilities partial, with five evidence test classes, none of them another operation's; the API overview counted
seven capabilities partial and one planned.

## Limits

- The bindings are written by hand and checked against the contract and its registry, not against the code: nothing
  proves that a controller serves an operation that a partial capability names.
- An unmarked operation may still be built only in part: its capabilities may be partial.
- The module documents' operation tables are still hand-written until issue 155 generates them.
- The site's panels were read as HTML, not rendered; their component is unchanged.
