# AGENTS.md: apps/shipping-web (npm workspace `@shipping-reference-platform/web`)

Adds to the root `AGENTS.md`. Decision: ADR 0009. Screen specification: `docs/ui/workbench.md` (binding for visual
design, behaviour, states and accessibility; changes require an explicit design decision recorded there).

## Stack

React 19.3.0, TypeScript 7.0.2, Vite 8.3.6, Node 26.13.0, npm 11.20.0. Tests: Vitest 5.1.0 with Testing Library
16.3.3; Playwright 1.65.0 with axe-core 4.14.0. No router, state-management, data-fetching, form or component
library: adding any runtime package needs a review and an allowlist entry (`security/AGENTS.md`).

- Vitest component tests use `jsdom`; `happy-dom` was rejected by dependency review.

## Structure and import rules

```text
src/
  app/                 shell, left navigation, router (History API), theme
  auth/                synthetic user picker; token held in memory          (risk level 3)
  api/                 hooks around @shipping-reference-platform/client      (risk level 2)
  features/quotes/     ParcelForm, QuoteCardList, QuoteCard
  features/shipments/  BookingForm, ShipmentTable, ShipmentDrawer, LabelPreview
  ui/                  presentational components: Button, ProblemAlert, EmptyState, StatusBadge, Skeleton, ThemeToggle
e2e/                   Playwright specs                                       (risk level 2)
```

Checked by `tools/web-imports/check-imports.mjs` in the `web` check:

- Only `src/api/` imports `@shipping-reference-platform/client`.
- `src/ui/` imports nothing from `app`, `auth`, `api` or `features`.
- A feature imports another feature only through that feature's `index.ts`.
- `src/auth/` imports nothing from `features`.
- No import cycles.

## Rules

- Call the API only through the generated client. Never write `fetch('/api/...')` by hand, never edit
  `packages/shipping-client/src/schema.d.ts`, and regenerate it when the contract changes.
- The server decides. The UI may disable or explain (an offer past `expiresAt` cannot be booked), but it must handle
  the server's problem for every action: `quote-expired`, `quote-already-booked`, `idempotency-key-reused`, and the
  rest of the contract's problem types.
- Booking: generate one `Idempotency-Key` (`crypto.randomUUID()`) per booking intent and reuse it for every retry
  of that intent, including after a network error. A changed recipient or offer is a new intent with a new key.
- Tokens stay in memory. Do not use `localStorage`, `sessionStorage`, cookies or IndexedDB for anything: v0.1 uses
  no browser storage at all, including for the theme override.
- Never use `dangerouslySetInnerHTML`, `innerHTML` or `eval`. Render the label preview with `<img>` from an object
  URL of the downloaded SVG, never as inline SVG markup.
- Prices: show `amount` and `currency` from the contract as received, formatted with `Intl.NumberFormat` for
  display only. Never compute prices in the browser.
- Every async view has explicit loading, empty, expired (where applicable) and failed states as specified.
- Accessibility is part of done: keyboard operation, visible focus, labelled errors, AA contrast, reduced motion.
  Query elements in tests by role and accessible name.
- Synthetic data only, also in tests and screenshots.

## Tests

| File | Kind |
|---|---|
| `src/**/<Component>.test.tsx` | Vitest and Testing Library component tests, API mocked at the client boundary |
| `e2e/*.spec.ts` | Playwright against the real API started with the `demo` profile and seed |

Use the names in `docs/capabilities/capabilities.yaml`: `ParcelForm.test.tsx`, `QuoteCardList.test.tsx`,
`BookingForm.test.tsx`, `ShipmentDrawer.test.tsx`, `ProblemAlert.test.tsx`, `ThemeToggle.test.tsx`,
`quote-book-label.spec.ts`, `expired-quote.spec.ts`, `tenant-denial.spec.ts`, `prototype-flag.spec.ts`,
`accessibility.spec.ts`. Never use `.only`, `.skip` or `test.fixme` in committed code.
