---
title: "ADR 0014: Partial web import lint and planned authoritative controls"
description: "The JDK web reader ships as partial lint with a known spread-syntax bypass; the reviewed compiler's import graph and browser-enforced CSP follow."
status: accepted
updated: 2026-11-08
date: 2026-11-08
decision-makers: repository owner
enforcedBy:
  - not enforced yet
plannedEnforcement:
  - "Web import dependencies from the reviewed TypeScript compiler's import graph"
  - "Strict Content-Security-Policy in the web app, enforced by the browser for runtime loading and execution"
---

# ADR 0014: Partial web import lint and planned authoritative controls

Adds the implementation scope for the web import control planned in ADR 0012. That record's intended application
design and confirmation checks remain in place; this record labels the current reader's incomplete coverage and
the controls that follow. It changes no module dependency, HTTP contract or application behaviour.

## Context and Problem Statement

The JDK web source reader in pull request #70 recognizes a restricted subset of TypeScript and page configuration.
Its fixtures show useful detections and refusals, but the review found valid spread expressions with hidden loaders
that yield no imports and no refusals. Calling this complete or fail-closed enforcement overstates the evidence.
On 2026-11-08 the owner chose to ship the reader now as labelled partial lint and defer authoritative enforcement.

## Considered Options

1. Keep expanding the custom lexer and scanner before shipping the reader.
2. Ship the reader as labelled partial static lint, record its known misses, and add authoritative controls later.

## Decision Outcome

Chosen option 2, as directed by the owner. Pull request #70 makes no further attempt to close bypasses under this
decision. Existing detections, refusals and assertions remain in place; no check is weakened to make it pass.

- **Partial lint now.** The reader can miss imports and unsupported constructs silently. A subsequent conformance
  gate using this reader can only compare the dependencies it detects with the model; it cannot claim a complete
  import graph. That gate is still planned in this reader-only slice.
- **Known open spread bypass.** Immediately after `...`, literal `import("unlisted")`, computed `import(name)`,
  `require("unlisted")` and unsupported `import.meta.glob("./*.ts")` can yield `imports=[], refusals=[]`.
  The review reproduced object, array, call and nested-template spreads, with whitespace, comments and line breaks
  after `...`, across TS/TSX and source/test placements. Parenthesizing the operand restores detection in the
  reviewed control without fixing the bypass. The review found the numeric-token defect fixed and reported no other
  open bypass class in its non-exhaustive probe set.
- **Authoritative controls later.** Use the reviewed TypeScript compiler's import graph for import dependencies,
  and a strict Content-Security-Policy in the web app, enforced by the browser, for runtime loading and execution.
  Neither control is implemented by the reader. Their implementation and evidence belong to later changes.

### Consequences

- The reader supplies useful early feedback while its public claims match its evidence.
- A passing lint result does not establish complete dependency detection or runtime safety. Conservative refusals
  also reject some harmless source and valid URLs, as listed in `docs/gaps.md`, section 6.
- The compiler graph and CSP serve different purposes: the graph does not prove general runtime behaviour, and
  CSP does not enforce module boundaries. Neither is a claim of universal runtime safety.
- The original first-slice design, including the application stack, model, API contract, persistence and workbench
  scope, remains as specified in ADR 0012. Its intended web enforcement is not achieved by this partial reader.

### Confirmation

`ImportScannerTest`, `SourceReaderTest` and `AppConfigurationTest` exercise specific synthetic reader cases. Passing
them is evidence for those cases only, not for a complete graph or browser enforcement. The known spread misses are
review findings documented in `docs/gaps.md` and the pull request #70 demo; this decision adds no fixture or runtime
control. The authoritative controls remain planned and have no enforcement evidence in this slice.
