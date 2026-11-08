# AGENTS.md: apps/shipping-web (npm workspace `@shipping-reference-platform/web`)

Adds to the root `AGENTS.md`. Decisions: ADR 0012 supersedes ADR 0009; ADR 0014 labels the current web reader as
partial lint and records the authoritative controls that follow. Screen specification:
`docs/ui/workbench.md` (binding for visual design, behaviour, states and accessibility; changes require an explicit
design decision recorded there; its amendment of 2026-11-07 sets the scope of the first runnable slice).

## Stack

A plain TypeScript single-page app with no UI framework (ADR 0012).

- TypeScript 5.9.3 (`tsc`) type-checks the sources and emits browser ES modules; there is no bundler. Its review is
  pending (issue #59): do not install it before its allowlist entry is approved. Node 26.13.0, npm 11.20.0.
- The Java application serves the emitted assets on loopback (`127.0.0.1`).
- Tests: `node --test`, Node's built-in runner, for logic; Playwright 1.65.0 with axe-core 4.14.0 for end-to-end and
  accessibility tests, in the `contract-e2e` CI job only. Their reviews approve no local or headed browser use: do not
  run them on a developer machine.
- No runtime package at all: the router (History API), state, forms and components are the project's own code.
  Adding any package needs a review and an allowlist entry (`security/AGENTS.md`).
- Deferred: React, Vite, Vitest, Testing Library, jsdom and the generated client (openapi-typescript and
  openapi-fetch). They come back only with their reviews and a new decision. `happy-dom` was rejected by dependency
  review.
- Compiler options `erasableSyntaxOnly`, `verbatimModuleSyntax` and `rewriteRelativeImportExtensions`. Use only
  erasable syntax: no `enum`, no `namespace` with code, no parameter properties, no `import =` aliases, and no
  decorators, which `tsc` does not reject but Node's type stripping cannot run. Import types with `import type`. Name
  every relative import with its `.ts` extension; `tsc` rewrites it to `.js`, and Node runs the `.ts` sources of the
  logic tests directly.

## Structure and import rules

```text
src/
  app/                 part app: shell, left navigation, router (History API), theme
  auth/                part auth: synthetic user picker; token held in memory          (risk level 3)
  api/                 part api: the typed HTTP adapter, the only caller of fetch       (risk level 2)
  features/quotes/     part quote-feature: ParcelForm, QuoteCardList, QuoteCard
  features/shipments/  part shipment-feature: BookingForm, ShipmentTable, ShipmentDrawer (LabelPreview deferred)
  ui/                  part ui, presentational: Button, ProblemAlert, EmptyState, StatusBadge, Skeleton, ThemeToggle
e2e/                   Playwright specs                                                  (risk level 2)
```

The model is to declare the six parts (issue #64). The planned `WebArchitectureConformanceTest` uses the JDK reader
of `libs/c4-contract-kit` as partial static lint over `src/` in the `java` check; the reader exists, but that gate is
not built in this slice. `tools/web-imports/check-imports.mjs` is not built. The intended rules:

- `src/ui/` imports nothing from `app`, `auth`, `api` or `features`.
- A feature imports another feature only through that feature's `index.ts`.
- `src/auth/` imports nothing from `features`.
- No import cycles.

The planned gate compares detected imports for boundaries, cycles and a direct import for each REQUIRED relationship
whose endpoints are built. It cannot guarantee complete detection: imports, `require` and unsupported `import.meta`
immediately after spread syntax can pass unseen in object, array, call and nested-template expressions, including
whitespace, comments and line breaks after `...`, across TS/TSX and source/test placements. See `docs/gaps.md`,
section 6, for the reviewed examples. Relationships touching planned parts are not evaluated. The HTTP calls to the
API stay outside the check; the end-to-end tests are their evidence for the operations they exercise.

Per the owner's decision of 2026-11-08 (ADR 0014), this reader ships as a labelled partial lint. Authoritative
controls follow: the reviewed TypeScript compiler's import graph for import dependencies, and a strict
Content-Security-Policy in the web app enforced by the browser for runtime loading and execution. Neither control is
implemented by the reader; passing lint is not proof of runtime safety.

## Rules

- Call the API only through the adapter in `src/api/`; no other code calls `fetch`. The import check cannot see a
  call to a global such as `fetch`, so review enforces this rule.
- The adapter's request and response types are written by hand from `contracts/shipping.openapi.yaml`. When the
  contract changes, change them in the same pull request. Nothing checks them against the contract: the server's HTTP
  contract tests and the end-to-end tests catch drift only for what they exercise.
- The server decides. The UI may disable or explain (an offer past `expiresAt` cannot be booked), but it must handle
  the server's problem for every action: `quote-expired`, `quote-already-booked`, `idempotency-key-reused`, and the
  rest of the contract's problem types.
- Booking: generate one `Idempotency-Key` (`crypto.randomUUID()`) per booking intent and reuse it for every retry
  of that intent, including after a network error. A changed recipient or offer is a new intent with a new key.
- Tokens stay in memory. Do not use `localStorage`, `sessionStorage`, cookies or IndexedDB for anything: v0.1 uses
  no browser storage at all, including for the theme override.
- Never use `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval` or `new Function`. Build elements
  with the DOM API and set text with `textContent`. Render the label preview, when it comes, with `<img>` from an
  object URL of the downloaded SVG, never as inline SVG markup.
- Prices: show `amount` and `currency` from the contract as received, formatted with `Intl.NumberFormat` for
  display only. Never compute prices in the browser.
- Every async view has explicit loading, empty, expired (where applicable) and failed states as specified.
- Accessibility is part of done: keyboard operation, visible focus, labelled errors, AA contrast, reduced motion.
  Query elements in tests by role and accessible name.
- Synthetic data only, also in tests and screenshots.

## Tests

| File | Kind |
|---|---|
| `src/**/<module>.test.ts` | Logic tests, beside the module they test: `node --test` runs them from the TypeScript sources with Node's type stripping, without a DOM |
| `e2e/*.spec.ts` | Playwright with axe-core in the `contract-e2e` CI job, against the real API started with the `demo` profile on PostgreSQL |
| `src/**/<Component>.test.tsx` | Deferred with Vitest and Testing Library: component tests, API mocked at the adapter boundary |

Use the names in `docs/capabilities/capabilities.yaml`: `ParcelForm.test.tsx`, `QuoteCardList.test.tsx`,
`BookingForm.test.tsx`, `ShipmentDrawer.test.tsx`, `ProblemAlert.test.tsx`, `ThemeToggle.test.tsx`,
`quote-book-label.spec.ts`, `expired-quote.spec.ts`, `tenant-denial.spec.ts`, `prototype-flag.spec.ts`,
`accessibility.spec.ts`. They stay acceptance targets: the component tests wait for their tooling, and
`prototype-flag.spec.ts` for the label preview. Never use `.only`, `.skip` or `test.fixme` in committed code.
