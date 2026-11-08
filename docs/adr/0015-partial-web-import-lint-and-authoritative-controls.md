---
title: "ADR 0015: Partial web import lint and planned authoritative controls"
description: "The JDK web reader, conformance rules and repository gate ship as a labelled partial check with known spread-syntax bypasses; compiler-graph boundary enforcement and browser verification of the implemented CSP remain planned."
status: accepted
updated: 2026-11-08
date: 2026-11-08
decision-makers: repository owner
enforcedBy:
  - "java: WebArchitectureConformanceTest checks detected imports in the six built web parts; the control stays partial"
plannedEnforcement:
  - "Web app pull request: import dependencies from the reviewed TypeScript compiler's tsc import graph"
  - "Web app pull request: browser verification of strict Content-Security-Policy (policy text implemented and tested, ADR 0017)"
---

# ADR 0015: Partial web import lint and planned authoritative controls

Extends ADR 0014's reader-only scope to the conformance rules in pull request #71 and the repository gate in
pull request #72 for the web import control specified in ADR 0012. ADR 0012's intended application design and
confirmation checks remain in place; this record labels the incomplete coverage of the current reader,
conformance rules and repository gate and the controls that follow. It changes no module dependency, HTTP contract or application
behaviour.

## Context and Problem Statement

The JDK web source reader in pull request #70 recognizes a restricted subset of TypeScript and page configuration.
Its fixtures show useful detections and refusals, but the review found valid spread expressions with hidden loaders
that yield no imports and no refusals. Calling this complete or fail-closed enforcement overstates the evidence.
On 2026-11-08 the owner chose to ship the reader in pull request #70, the conformance rules in pull request #71
and the repository gate in pull request #72 as a labelled partial check and defer authoritative enforcement
to the web app pull request. The capability stays partial.

## Considered Options

1. Keep expanding the custom lexer and scanner before shipping the reader.
2. Ship the reader as labelled partial static lint, record its known misses, and add authoritative controls later.

## Decision Outcome

Chosen option 2, as directed by the owner. Pull requests #70, #71 and #72 make no further attempt to close bypasses under
this decision. Existing detections, refusals and assertions remain in place; no check is weakened to make it pass.

- **Partial check now.** The reader can miss imports and unsupported constructs silently. The conformance library
  in pull request #71 compares only the dependencies it detects with the model and propagates reported refusals;
  it cannot claim a complete import graph or general fail-closed enforcement. Both the reader and the conformance
  library are implemented and tested on synthetic fixtures. In pull requests #70 and #71, the repository's web-app
  integration was still planned. Pull request #72 adds `WebArchitectureConformanceTest`, which runs the gate over
  the repository's source, test and public roots and app configuration in every build; its passing tests support
  `control.web-import-boundaries` as partial. At that integration step all six parts were planned. The workbench now has six built parts and production
  import evidence for all ten REQUIRED relationships. The partial label alone does not promote the capability's status.
- **Known open spread bypass.** Immediately after `...`, literal `import("unlisted")`, computed `import(name)`,
  `require("unlisted")` and unsupported `import.meta.glob("./*.ts")` can yield `imports=[], refusals=[]`.
  The review reproduced object, array, call and nested-template spreads, with whitespace, comments and line breaks
  after `...`, across TS/TSX and source/test placements. Parenthesizing the operand restores detection in the
  reviewed control without fixing the bypass. The conformance gate can return `passed=true` with no findings for
  these forms, including in pull request #72; integrating the gate does not recover imports the reader missed.
  The review found the numeric-token defect fixed and reported no other open bypass class in its non-exhaustive
  probe set.
- **Authoritative controls in the web app pull request.** Use the reviewed TypeScript compiler's `tsc` import graph
  for import dependencies, and a strict Content-Security-Policy in the web app, enforced by the browser, for runtime
  loading and execution.
  Neither control is implemented by the reader, conformance library or repository gate. ADR 0017 records the
  implemented policy text; compiler-graph comparison and browser evidence remain pending.

### Consequences

- The reader supplies useful early feedback while its public claims match its evidence.
- A passing lint result does not establish complete dependency detection or runtime safety. Conservative refusals
  also reject some harmless source and valid URLs, as listed in `docs/gaps.md`, section 6.
- The compiler graph and CSP serve different purposes: the graph does not prove general runtime behaviour, and
  CSP does not enforce module boundaries. Neither is a claim of universal runtime safety.
- The original first-slice design, including the application stack, model, API contract, persistence and workbench
  scope, remains as specified in ADR 0012. Its intended web enforcement is not achieved by this partial reader.

### Confirmation

`ImportScannerTest`, `SourceReaderTest` and `AppConfigurationTest` exercise specific synthetic reader cases.
`WebConformanceTest` and `WebReaderRefusalConformanceTest` check the conformance rules and propagation of tested
reader refusals through the public API. Passing them is evidence for those cases only, not for a complete graph,
the repository's web-app integration or browser enforcement. Repository integration has separate evidence in
`WebArchitectureConformanceTest`, including a fixture that rejects an unapproved import in the repository model's
`e2e` root; it does not close reader misses. The known spread misses are review findings documented
in `docs/gaps.md` and the pull requests #70, #71 and #72 demos; this decision adds no reader fixture or runtime
control. The compiler now checks and emits the workbench, and ADR 0017 records the tested strict CSP text.
Compiler-graph boundary enforcement and browser verification remain planned.
