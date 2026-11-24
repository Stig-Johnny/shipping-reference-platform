---
title: "The module documents' operation tables are generated, and an edited one fails the check"
description: "Each module document's HTTP operations section now comes from the contract and the capabilities, so it cannot drift from them: an edit by hand fails the documentation check, and one command restores it."
status: accepted
updated: 2026-11-24
pr: 162
kind: gate
recorded: 2026-11-24
tests: [OperationSectionsTest, DocsToolTest]
---

## What changed

Before, the HTTP operations section of each module document was written by hand, and nothing compared it with the
contract: the shipments table listed `validation-failed`, which the registry gives any module, among the
module-specific problems of listing shipments, and the tenants document listed its one operation as a bullet. Now the
generator owns that section in the documents of the three modules that have operations. It writes a notice that the
section is generated and from what, then one row per operation from `contracts/shipping.openapi.yaml`: its method and
path, its success responses and the problem types that the registry names the module for, with the planned marks that
the capabilities in `docs/capabilities/capabilities.yaml` give. The rest of each document stays as written. An edit of
the section fails the documentation check, which names the file and the first line that differs, and
`./gradlew :tools:docs:generate` restores it (ADR 0025).

## See it

- [The shipments module](../modules/shipments.md): its section HTTP operations, with the notice and five rows;
  `cancelShipment` and the label's `409 shipment-cancelled` are marked planned.
- [The quotes module](../modules/quotes.md): two rows; `createQuote`'s `422 no-offer-available` names its extension
  member `reason`.
- [The tenants module](../modules/tenants.md): the table's one row, and the events in a section of their own.

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, in
`docs/modules/shipments.md`, change `ShipmentPage` in the `listShipments` row of the operations table to `Shipments`.
Then run:

```sh
./gradlew :tools:docs:check
```

It fails and names the file and the line of the edited row. Cleanup: run `./gradlew :tools:docs:generate`, which
writes the generated section back, then the first command again: it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-24, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change, with the fixture above,
`./gradlew :tools:docs:check` exited 1. The documentation tests copy the repository, fixture included, and passed:
they start from a copy whose sections are generated again, so that an edited section cannot stop
`./gradlew :tools:docs:generate`, which runs them first. Then `checkGenerated` failed. The excerpt leaves out other
tasks' lines:

```text
> Task :tools:docs:test
> Task :tools:docs:checkGenerated
docs: the committed generated files are not what the sources generate; run ./gradlew :tools:docs:generate and commit the result:
  - docs/modules/shipments.md: its section HTTP operations differs from what the contract and the capabilities generate, first at line 115; run ./gradlew :tools:docs:generate (ADR 0025)
> Task :tools:docs:checkGenerated FAILED
BUILD FAILED in 4m 10s
```

Line 115 is the edited row. Then `./gradlew :tools:docs:generate` exited 0 and wrote the section back, and
`git diff` showed no change to the document. The excerpt leaves out other tasks' lines:

```text
> Task :tools:docs:generate
docs: wrote 22 generated files
docs: wrote 3 generated module sections
BUILD SUCCESSFUL in 3s
```

After the cleanup, `./gradlew :tools:docs:check` ran the documentation tests again, all passing, and exited 0. The
excerpt leaves out other tasks' lines:

```text
> Task :tools:docs:checkGenerated
docs: the 22 generated files are up to date
docs: the 3 generated module sections are up to date
> Task :tools:docs:site
docs: wrote the site, 129 files
> Task :tools:docs:check
BUILD SUCCESSFUL in 4m 7s
```

In the site that this build wrote, rendered in headless Chrome at 1280 pixels in the light and dark themes and at 390
pixels inside a frame, [the shipments module](../modules/shipments.md)'s section showed its heading, the notice in
three paragraphs and five rows, with `cancelShipment` and `409 shipment-cancelled` marked planned and none in the
`listShipments` row; [the quotes module](../modules/quotes.md) showed two rows, and
[the tenants module](../modules/tenants.md) one row followed by its Events section. At 1280 pixels each table fitted
its frame; at 390 pixels no page scrolled sideways, and each table scrolled inside its own frame, the shipments table
672 pixels wide in 358.

## Limits

- The tables show what the contract and the capabilities claim. A problem response's types are the code spans that
  start its description; nothing checks the prose after them, nor which code serves an operation.
- An unmarked operation may still be built only in part: its capabilities may be partial.
- The pages were rendered in one browser engine only, headless Chrome, at the widths above.
