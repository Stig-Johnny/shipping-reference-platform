---
title: "A behavioural diagram says when no test compares it with the code"
description: "A sequence or state diagram in the prose now fails the documentation check without the label Unchecked design right before it, and every dynamic view of the model carries the label, on GitHub and on the site."
status: accepted
updated: 2026-11-23
pr: 154
kind: gate
recorded: 2026-11-23
tests: [BehaviouralDiagramsTest, BrainGeneratorTest, MarkdownRendererTest, SitePagesTest]
---

## What changed

Before, nothing told a reader which diagrams the code is held to. The flow's sequence and the shipment lifecycle are
written by hand and drawn by GitHub, and no test reads them; the model's dynamic views looked alike, although only the
two scenario views run against the code, and only in part. Now a sequence or state diagram in the prose that the check
reads must follow a paragraph that starts with the bold words Unchecked design. and says what no test compares, or the
documentation check fails. Every dynamic view of the model carries the same label from the generator, a scenario view
too, until evidence shows that a test compares the whole view. On the site the label is the words Unchecked design in
the muted box, after the diagram's title or on its closed source.

## See it

- [The flow: quote, book and download a simulated label](../flows/quote-book-label.md): the dynamic view's
  figure shows the label after its title and the sentence first in its caption; below it, the sequence's paragraph
  starts with Unchecked design, and its closed source carries the label.
- [The shipments module](../modules/shipments.md#lifecycle): the lifecycle's state diagram, with the paragraph and the
  label on its closed source.
- [ADR 0024](../adr/0024-behavioural-diagrams-are-labelled-until-a-test-compares-them.md): the rule, and what removes
  a label later.

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, change the bold words
Unchecked design. above the lifecycle's state diagram in `docs/modules/shipments.md` to Design., so that the diagram
reads as if a test held it to the code. Then run:

```sh
./gradlew :tools:docs:check
```

It fails, and the output names the file, the line and the label to write. Cleanup: put the words back, and run the
same command: it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-23, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change. With the fixture above,
`./gradlew :tools:docs:check` exited 1. The shipments document is an input of the documentation tests, so they ran
again and failed first: the tests that copy the repository copy the edited document too. The excerpt shows the
console's last lines and the failure message that the test report records for `BehaviouralDiagramsTest`'s repository
test:

```text
1009 tests completed, 289 failed
> Task :tools:docs:test FAILED
BUILD FAILED in 2m 34s
Expecting empty but was: ["docs/modules/shipments.md:169: no test compares this state diagram with the code; write a paragraph that starts with **Unchecked design.** right before it (ADR 0024)"]
```

After the cleanup, `./gradlew :tools:docs:generate` ran the documentation tests, 1108, all passing, and exited 0;
then the same command exited 0, its tests up to date. The excerpt leaves out other tasks' lines:

```text
> Task :tools:docs:test UP-TO-DATE
> Task :tools:docs:checkGenerated
docs: the 22 generated files are up to date
> Task :tools:docs:site
docs: wrote the site, 123 files
> Task :tools:docs:check
BUILD SUCCESSFUL in 4s
```

On the site that this branch builds, rendered in headless Chrome at 1280 pixels in the light and dark themes and at
390 pixels inside a frame, [the flow](../flows/quote-book-label.md) showed the label after the dynamic view's title and
the sentence first in its caption, and the sequence's label paragraph above its closed source, which carried the label
as well. [The shipments module](../modules/shipments.md#lifecycle) showed the same for the state diagram. On a phone,
the label wraps below the closed source's summary and stays whole. The scenario views' pages carried the label, and
the structural views' pages did not.

Before the build, three designs were rendered on the same pages, and Codex (gpt-6-astra, effort xhigh) chose the muted
solid box and the words Unchecked design, and required every dynamic view to stay labelled until a complete comparison
is proven. `BehaviouralDiagramsTest` checks each rule whenever the documentation tests run, as they do in every CI
build, and the check itself runs in every `generate`, `checkGenerated` and site build. Before the check was written,
39 of the 171 selected tests failed against main's code with an empty stub: the new cases. Review: pull request #154.

## Limits

- The label disclaims; it does not bind. No test compares a whole diagram with the code yet, and a label goes only
  when evidence of a complete comparison exists, which issue 153 is to bind.
- The check recognises behaviour by a fence's first line. A state machine drawn as a flowchart, a standalone `.mmd`
  file and diagrams in records, generated files and superseded ADRs are not read as behaviour.
- The scenario views stay labelled although their tests compare the steps they can observe, 2 of 6 in each.
- Inspected in headless Chrome only, at 1280 and 390 pixels, in the light and dark themes. No screen reader was used;
  the label is the words in the page's text, and the accessible description of each dynamic view says it as well.
