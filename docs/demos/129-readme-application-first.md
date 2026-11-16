---
title: "The README describes the application first and how it was built after it"
description: "A Java reader meets the application, how to run it and its limits before the AI-assisted delivery and governance material, which keeps its checks and evidence in one later section."
status: accepted
updated: 2026-11-16
pr: 129
kind: docs
recorded: 2026-11-16
tests: [SitePagesTest]
---

## What changed

Before, the README's sections after "What exists today" alternated between the application and how it was built. The
checker, Try it, the idea and the review pipeline came before the product's modules, the controls table before the
layout, and the workbench section came after the licence. "What exists today" listed the checker, the allowlist check,
the scenario checks, the database harness and the documentation tool beside the API. The README sheet's ledger opened
with every control's remaining work, such as the scenario ledger's next phase and the import reader's gaps.

Now the README describes the application first: The Java architecture, Run it, What exists today, The product in one
paragraph, Layout, Running it, Run the workbench locally and Honest limits. "How this was built" follows, and the
AI-assisted delivery and governance material forms its subsections: the idea, how a change is made, the tools that
judge the code, the checker and Try it, the controls, the limits of the checks, the scenario coverage ledger,
contribution records and model runs, the CI toolchain and prior art. The blocks moved unchanged, apart from one
merged statement of the ledger's acceptance, one positional reference that became a link and one lead-in line. The
README sheet's ledger gives the start sheet's counts. The other controls' remaining work is still in the ledger of
the sheet whose document specifies it, such as a decision record or a Trust sheet. The README itself specifies two
controls, external imports and mutation testing: its controls table says what remains of each, and the external
imports sentence that its ledger showed is now on no ledger.

## See it

- [The repository's README](../../README.md): "On this sheet" lists the application's sections, then "How this was
  built" and Licence.
- [How this was built](../../README.md#how-this-was-built): its subsections, from the idea to prior art.
- [The Trust sheet](https://stig-johnny.github.io/shipping-reference-platform/trust/index.html): the dependency
  allowlist control's remaining work in its ledger.
- [Controls and how each one can fail](../../README.md#controls-and-how-each-one-can-fail): the external imports
  row's status says what remains of that control.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-16 on macOS with Temurin 25.0.5+8. `SitePagesTest` was changed first, to require:

- the README sheet's level-2 sections in the new order;
- the eleven subsections under "How this was built";
- the governance material only after that heading;
- the README sheet's planned implementation equal to the start sheet's.

Run with `./gradlew :tools:docs:test --tests '*SitePagesTest'` against the unchanged README and site, it exited 1. The
excerpt omits the assertions' messages:

```text
SitePagesTest > theReadmeSheetsLedgerGivesTheRepositorysStateAsTheStartSheetDoes() FAILED
    org.opentest4j.AssertionFailedError at SitePagesTest.java:153
SitePagesTest > theReadmeDescribesTheApplicationFirstAndHowItWasBuiltAfterIt() FAILED
    org.opentest4j.AssertionFailedError at SitePagesTest.java:124
39 tests completed, 2 failed
```

The line numbers are the test's as first changed. After the first review it also checks the Trust sheet's and two
decision records' ledgers, the README's controls table and the moved `web` job sentence, and the same run now fails
at lines 154 and 124.

After the change all 39 passed. `./gradlew check :tools:docs:checkGenerated` and `java tools/policy/PolicyCheck.java .`
exited 0. A line-by-line comparison found every line of the old README in the new one, except:

- the headings that became subsections;
- the paragraphs that were rewrapped or that gained a pointer;
- two statements of the scenario ledger's acceptance, now one.

The rendered [README sheet](../../README.md) was read with its new order and its ledger row. Review: pull request
#129.

## Limits

The site's sections, the start page's reading paths and the controls' own sheets are unchanged, and the Demos section
still comes second in the navigation. Of the ledgers that keep the controls' remaining work, the site test checks the
Trust sheet's and two decision records'; the others were not read page by page. The launcher still prints a sign-in
hint that the static fixture page does not have (issue 128).
