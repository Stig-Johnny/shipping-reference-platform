---
title: "Find a page and explore the shipping Brain"
description: "Search the published documentation, then follow sourced relationships between modules, decisions and test evidence."
status: accepted
updated: 2026-11-09
pr: 97
kind: visual
recorded: 2026-11-09
tests: [SearchPageTest, BrainMapTest]
---

## What changed

The site previously required readers to browse its registers and long Brain catalogue. It now has a search page and
an interactive Brain map, with details for the selected record and the complete catalogue available below it.

Search looks through published titles, summaries and headings, ranks title matches first and identifies the matched
field. The map draws declared module dependencies, specifications that name decisions, and capabilities or controls
with passing test evidence. Each relationship has a meaning and source. Design, module and capability statuses
remain separate.

Both views regenerate with the documentation build. An approved source change reaches the public site after merge
and successful Pages deployment. Editing ordinary prose does not create a dependency arrow.

## See it

- Open the [Brain](https://stig-johnny.github.io/shipping-reference-platform/brain/index.html). Select Shipments,
  follow its dependency on Quotes, or choose Decisions and Evidence. Walk the demo gives the four-step attended flow.
- Open [Search](https://stig-johnny.github.io/shipping-reference-platform/search/index.html). Try `quotes` for title
  matches or `confirmation` for a heading match. Each result says which field matched.
- Read [the site design](../ui/documentation-site.md) for the palette, keyboard and fallback behavior.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-09, the generated local site was inspected in the Codex in-app browser on macOS, at 1280, 390, 360 and
320 CSS pixels. The module map and labels fit without horizontal scrolling. The selected node had a gold highlight;
its detail showed source references and separate statuses. Search Enter focused the first result, Space selected a
record and moved focus to its detail, and Escape cleared a query. A markup-shaped query remained literal text and
returned no results. No browser console warnings or errors were observed.

The [Brain](https://stig-johnny.github.io/shipping-reference-platform/brain/index.html) inspection also followed
ADR 0017 into Decisions and QuotesHttpContractTest from quotes.get-quote into Evidence. The latter retained its
capability context even after visiting control.http-contract. A planned capability with no passing evidence showed
no evidence arrows. The complete records and search directory are ordinary HTML; their no-script structure was
reviewed in source, rather than claimed as an attended browser run with JavaScript disabled.

On the same date, with the repository's reviewed JDK 25 on macOS, the initial focused check failed before the
implementation (exit 1). This excerpt omits individual assertion traces:

```text
6 tests completed, 5 failed
```

After implementation, the dependency policy check exited 0 and the full Java/docs check with generated-file
verification exited 0. The report files contained 4,454 tests, zero failures, zero errors and zero skipped tests.
This excerpt omits task and tool-warning output:

```text
BUILD SUCCESSFUL in 2m 8s
```

SearchPageTest and BrainMapTest are separately identified as current-build evidence above; their assertions cover
published-page ownership, escaping and scan visibility, typed relationships, changed model input, and missing or
failed test evidence. Review reference: pull request #97.

Implementation record for issue #38: Codex used gpt-6-astra with xhigh reasoning effort for design, implementation
and verification. A separate Codex reviewer used gpt-6-astra with xhigh effort. This is provenance, not evidence of
correctness; the tests and recorded review provide that evidence.

## Limits

Search matches literal phrases in titles, summaries and headings; it does not index arbitrary body prose. The graph
covers the API modules. The web model remains separately linked from the full catalogue. Node size and position do
not indicate importance, and map arrows are declared relationships, not observed runtime traffic.

The browser checks are attended spot checks, not a complete accessibility certification. Long graph labels are
shortened visually; accessible names and selected details retain their full names. Database and mutation checks run
in CI, not on this Mac. The production publication dictionary runs only in the main Pages workflow. The public
site remains on its previous revision until that deployment succeeds.
