# AGENTS.md: apps/shipping-web (npm workspace `@shipping-reference-platform/web`)

Adds to the root `AGENTS.md`. Decisions: ADR 0012 supersedes ADR 0009; ADR 0015 labels the current web reader as
partial lint and records the authoritative controls that follow. Screen specification:
`docs/ui/workbench.md` (binding for visual design, behaviour, states and accessibility; changes require an explicit
design decision recorded there; its amendment of 2026-11-07 sets the scope of the first runnable slice).

## Stack

A plain TypeScript single-page app with no UI framework (ADR 0012).

- TypeScript 5.9.3 (`tsc`) type-checks the sources and emits browser ES modules; there is no bundler. Its review is
  approved with the reviewed lock (issues #59 and #80). Install only with `npm ci --ignore-scripts`.
  Node 26.13.0, bundled npm 11.20.0.
- Serving the emitted assets from the Java application on loopback (`127.0.0.1`) is planned; the API currently
  serves no static files. Do not claim an integrated browser flow.
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

The model declares six built parts, with production import evidence for all ten REQUIRED relationships. `WebArchitectureConformanceTest` uses the JDK reader
of `libs/c4-contract-kit` as partial static lint over `src/`, the `e2e/` test root, the public root and app
configuration in the `java` check. The gate runs on the real source tree in every build; Playwright tests remain absent.
`tools/web-imports/check-imports.mjs` is not built. The intended rules:

- `src/ui/` imports nothing from `app`, `auth`, `api` or `features`.
- A feature imports another feature only through that feature's `index.ts`.
- `src/auth/` imports nothing from `features`.
- No import cycles.
- No package: no part's `web.packages` lists one. A bare specifier fails unless its part lists the package,
  `package.json` pins it in `dependencies`, and `security/allowlist.json` approves that version for
  `apps/shipping-web` in `dependencies`; a review for `devDependencies` does not cover code that runs in the browser.
- No built-in module of Node.js in browser code, in any form. A test may import `node:assert/strict`, `node:test`
  and `node:fs` (the model's `web.testBuiltins`), written with `node:`, under the review of the Node.js that
  `engines.node` names.
- Tests are held to the rules of their part, but never count as an import a REQUIRED relationship needs, and no
  production file imports a test.
- No URL, `data:` or computed import; no CSS `@import` (link each style sheet from `index.html`) and no style sheet
  imported from TypeScript; no path alias, `baseUrl` or import map.

The gate compares detected imports for boundaries, cycles and a detected direct import for each REQUIRED relationship
whose endpoints are built. It cannot guarantee complete detection: imports, `require` and unsupported `import.meta`
immediately after spread syntax can pass unseen in object, array, call and nested-template expressions, including
whitespace, comments and line breaks after `...`, across TS/TSX and source/test placements. See `docs/gaps.md`,
section 6, for the reviewed examples. Relationships touching planned parts are not evaluated. The HTTP calls to the
API stay outside the check; the end-to-end tests are their evidence for the operations they exercise.

Per the owner's decision of 2026-11-08 (ADR 0015), this reader ships as a labelled partial lint. Authoritative
compiler-graph boundary enforcement remains planned. The reviewed compiler now type-checks and emits browser modules.
The entry page declares ADR 0017's strict same-origin CSP, tested as text by `src/app/csp.test.ts`; browser enforcement
is not yet exercised. Keep the policy before all resource elements, without unsafe-inline, unsafe-eval or external
origins. Any future repository-owned asset server must also send it as a response header. Passing partial lint or
this text test is not proof of runtime safety.

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

## Commands

From the repository root: `npm ci --ignore-scripts`, then `tsc -p apps/shipping-web` with the reviewed Node runtime
and the installed compiler on PATH. In `apps/shipping-web`: exactly `node --test`. The only optional manifest
wrappers are root `build:web` = `tsc -p apps/shipping-web` and workspace `test:unit` = `node --test` (ADR 0007).
No extra options, test paths, loaders or build/copy shell chains in package scripts.

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
