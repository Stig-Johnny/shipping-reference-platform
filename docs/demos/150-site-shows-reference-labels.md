---
title: "A planned test reads as a labelled name on the site"
description: "The site shows a planned test as its name and a dashed planned box, and a capability id without its prefix, so the pages read as prose while GitHub keeps the markers that the check proves."
status: accepted
updated: 2026-11-22
pr: 150
kind: visual
recorded: 2026-11-22
tests: [MarkdownRendererTest, SitePagesTest, DocsReviewConditionsTest]
---

## What changed

Before, the site showed the reference markers of ADR 0023 exactly as the Markdown writes them: a planned test read as
planned:test:CrossTenantAccessTest, and a capability as cap:tenants.cross-tenant-denial. Now, in the documents that
the reference check reads, the site shows a planned test as its name followed by the word planned in a dashed box, the
site's mark for planned, and a capability or control id without its prefix. GitHub still shows the markers, so the
source stays explicit for agents and for the check.

The label stays with its name and keeps the punctuation after it, unless a line is too narrow for the two together, as
for the longest names on a 320-pixel screen; then the line breaks rather than widen the page. A table that holds a
label keeps a readable width on a phone and scrolls sideways, with a hint. Records, such as the earlier
demonstrations, and code blocks keep the markers as written, because nothing checks them.

## See it

- [The tenants module](../modules/tenants.md): in the table of invariants, rows T3, T6, T7 and T8 name planned tests,
  each with the dashed planned box on the same line as its name. Above the table, three capabilities named as planned
  read as plain ids. On a phone, the table keeps its width and scrolls under the hint "Scroll horizontally for all
  columns."
- [Known gaps](../gaps.md): in section 1, TableOwnershipTest carries the box before the word planned that the sentence
  already had.
- [ADR 0023](../adr/0023-inline-references-carry-explicit-markers.md): the grammar's code block keeps the markers, and
  the paragraph below it says how the site shows them.
- [The demonstration for pull request 147](147-inline-test-references-are-checked.md): a record, so its markers stay
  as written.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-22, on macOS with the reviewed Temurin 25.0.5+8, the site that the documentation tool built from this
branch was rendered in headless Chrome at 1280 pixels in the light and dark themes, and at 390 pixels inside a frame.
On [the tenants page](../modules/tenants.md), the four planned tests in the invariants table each showed the name and
the dashed box on one line, PrototypeFlagExpiryTest, the longest, included; on a phone the table kept its width and
scrolled under the hint, and the invariant column stayed readable. The capability ids read without the prefix, the
grammar's code block in [ADR 0023](../adr/0023-inline-references-carry-explicit-markers.md) kept the markers, and
[the page for pull request 147](147-inline-test-references-are-checked.md) still showed them as written.

Before the build, three designs were rendered on the same pages, and Codex (gpt-6-astra, effort xhigh) chose the
dashed box for planned tests and plain ids for capabilities, and set the conditions that the tests now pin. A
line-breaking probe in Chrome chose a label held to its name by a no-break space: from 150 to 1000 pixels, a table
never broke the label or parted it from its name, and prose never overflowed the page; only a line narrower than 322
pixels breaks the longest pairs.

The focused tests failed against stubs before the implementation (exit 1). This excerpt omits the assertion traces:

```text
108 tests completed, 8 failed
```

After the implementation, the dependency policy check exited 0. ./gradlew :tools:docs:generate ran the 916
documentation tests with no failures and exited 0; ./gradlew check then exited 0, its tasks up to date. This excerpt
of the generate run omits the task output:

```text
BUILD SUCCESSFUL in 3m 5s
```

MarkdownRendererTest, SitePagesTest and DocsReviewConditionsTest are named under tests. They cover the label wherever
inline code can stand, the anchors of the source, no label made from a document's own text (a NUL or a character
reference), the markers as written in records, decks and code blocks, and every rendered fragment passing through the
label step. Review reference: pull request #150.

## Limits

- A label shows what the marker says and what the check proved, that a planned test is registered and does not exist
  yet; it does not show whether the sentence describes the test rightly.
- A capability or control id carries no label; its status is on the Brain page, not beside the id.
- Inspected in headless Chrome only, at 1280 and 390 pixels. On a screen narrower than about 354 pixels, a line too
  narrow for one of the longest test names and its label breaks inside the name or before the label, rather than widen
  the page. No screen reader was used; the label is the word planned in the page's text.
- Paths come with issue 146.
