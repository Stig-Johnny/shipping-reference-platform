---
title: "Shipping workbench: UI specification"
description: "Screens, states, components, keyboard and accessibility requirements of the workbench in apps/shipping-web."
status: accepted
updated: 2026-11-08
module: web
---

# Shipping workbench: UI specification

This specification is binding for visual design, behavior, states and accessibility. Changes require an explicit
design decision recorded here. ADR 0012 defines the technical approach; the OpenAPI contract defines API behavior.

## Amendment 2026-11-07: scope of the first runnable slice

An explicit design decision under the rule above. The rest of this specification is unchanged.

- **In scope:** the synthetic user picker (`/sign-in`); the parcel form and the offer cards (`/quotes/new`,
  `/quotes/:quoteId`); booking, with the recovery after an uncertain outcome; the shipments table and the shipment
  drawer (`/shipments`, `/shipments/:shipmentId`); and the label download.
- **Deferred: cancellation.** The drawer offers no "Cancel shipment" action and no confirmation, and the workbench
  never calls `cancelShipment`.
- **Deferred: the prototype-flag preview.** `LabelPreview` is not built. For every user the drawer behaves as it does
  with the `label-preview` flag off: it renders no preview and makes no label request when it opens.
- **Kept:** for everything in scope, every state in [States](#states) and every rule in
  [Keyboard and accessibility requirements](#keyboard-and-accessibility-requirements); and the no-storage rule:
  tokens, the theme override and the booking recovery are held in memory only.
- **Technology:** the components are plain TypeScript, without a framework (ADR 0012). Their
  names and responsibilities in [Components](#components) stay. The component tests named there are deferred with
  their tooling and stay acceptance targets; until they exist, logic tests under `node --test` cover its models. End-to-end
  specs remain planned.
- **End-to-end:** `prototype-flag.spec.ts` waits for the preview; the other specs in
  [End-to-end scenarios](#end-to-end-scenarios-contract-e2e) apply to this slice.

## Implementation evidence, 2026-11-08

The six model parts now contain the plain TypeScript workbench. The 125 `node --test` cases cover the API adapter,
validation, sign-in state, routes, parcel and quote models, booking intent and recovery, shipments, labels and
formatting; they include the exact CSP policy and its placement in the entry page. They run without a DOM.
`WebArchitectureConformanceTest` checks the real imports and every REQUIRED relationship; its reader remains partial.
The `web` job installs the reviewed lock, runs `tsc -p apps/shipping-web` and then `node --test` in the workspace.

The API does not serve these assets yet. No Playwright e2e, axe, keyboard, screen-reader or browser CSP enforcement
result is claimed. The component and e2e names below stay acceptance targets, and all four `web.*` capabilities stay
planned until their evidence exists and the documentation build can consume it. Cancellation and preview stay
deferred. ADR 0017 records the owner's strict same-origin CSP decision.

## Users and the job

One user type in v0.1: a shipping clerk of a webshop tenant. The job: price a parcel, compare offers, book one,
get the label, and find shipments again. The demo also uses the workbench to show tenant denial and a prototype
flag in both states.

## Visual direction

A calm, dense work tool: left navigation, a parcel form, comparable quote cards, a shipments table and a shipment
detail drawer. System sans-serif, tabular figures for prices and weights, warm white and slate surfaces, restrained
teal for actions. No illustrations, no decorative motion.

### Tokens

Contrast values are approximate and must be confirmed by axe-core's colour-contrast rule in both themes.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--surface` | `#FAF8F5` | `#0F172A` | Page background |
| `--surface-raised` | `#FFFFFF` | `#1E293B` | Cards, drawer, table |
| `--text` | `#1E293B` (about 13.8:1) | `#F1F5F9` (about 16:1) | Body text |
| `--text-muted` | `#475569` (about 7:1) | `#CBD5E1` (about 12:1) | Secondary text, hints |
| `--border-input` | `#64748B` (about 4.7:1) | `#94A3B8` (about 7:1) | Input and control borders (3:1 minimum) |
| `--border-subtle` | `#E2E8F0` | `#334155` | Decorative dividers only |
| `--action` | `#0F766E`, white text (about 5.5:1) | `#2DD4BF`, `#0F172A` text (about 9.5:1) | Primary buttons, links, focus ring |
| `--danger` | `#B91C1C` | `#F87171` | Errors |
| `--warning` | `#92400E` | `#FBBF24` | Expired state |
| `--success` | `#166534` | `#4ADE80` | Booked state |

- Type: `system-ui, sans-serif`; monospace `ui-monospace, monospace` for tracking numbers and IDs. Base 16 px,
  line height 1.5, scale 14 / 16 / 20 / 24 / 32 px.
- Numbers: `font-variant-numeric: tabular-nums` for prices, weights, dimensions and times.
- Status is never shown by colour alone: every badge has a text label (`Open`, `Expired`, `Booked`, `Cancelled`).
- Theme: follows `prefers-color-scheme`; a three-way override (System, Light, Dark) in the navigation footer. The
  override lasts until reload, because v0.1 uses no browser storage.
- Motion: the drawer slides in over 160 ms; skeletons pulse. Both are disabled under
  `prefers-reduced-motion: reduce`.

## Layout

- **App shell:** left navigation 240 px wide with "New quote" and "Shipments", and a footer with the signed-in user,
  tenant name, theme override and "Switch user". Below 720 px viewport width the navigation becomes a top bar.
  Main content up to 1120 px wide. A "Skip to main content" link is the first focusable element.
- **Drawer:** opens from the right over the shipments table, 480 px wide (full width below 720 px).
- Every route sets the document title: `<screen> - Shipping workbench`.

## Routes and screens

| Route | Screen | API operations |
|---|---|---|
| `/sign-in` | Synthetic user picker | `getCurrentMembership` |
| `/quotes/new` | Parcel form; offers appear below it | `createQuote` |
| `/quotes/:quoteId` | Quote with offer cards and booking form | `getQuote`, `bookShipment` |
| `/shipments` | Shipments table with status filter | `listShipments` |
| `/shipments/:shipmentId` | Table with the shipment drawer open | `getShipment`, `cancelShipment`, `getShipmentLabel` |

`/` redirects to `/quotes/new`. Without an authenticated membership, protected routes redirect to /sign-in and
preserve the requested internal route. Switch user clears membership, tokens, tenant data, forms, booking intents and
object URLs. Abort outstanding requests and reject late results from the previous session. Failed sign-in clears the
attempted token. tenant-denial.spec.ts verifies switching during an outstanding request cannot display the previous
tenant’s data.

### Sign in (`/sign-in`)

- A radio group listing the three synthetic users from `docs/modules/tenants.md` by display name, with the tenant
  in the description, and a "Continue" button. The page states that these are public demo users.
- On continue: hold `demo-<handle>` in memory and call `getCurrentMembership`. `200`: store the membership in memory
  and go on. `403 no-tenant-membership`: stay, show "This synthetic user belongs to no tenant." `401`: show
  "Sign-in failed."

### New quote (`/quotes/new`)

- **ParcelForm** fields, each with a visible label, unit and hint:
  - Weight (g): integer 1 to 30000.
  - Length, width, height (mm): integers 1 to 1200, in one fieldset "Dimensions".
  - Origin and destination: country (select of DE, DK, FI, NL, NO, SE, plus "Other" with a two-letter text input),
    postcode (text, converted to upper case on blur).
  - Service level: select "Any" (default), Economy, Standard, Express.
- Client-side checks mirror the contract's bounds and patterns so most errors appear before sending. The server's
  `validation-failed` remains authoritative: each `errors[]` item with `in: body` maps its JSON Pointer to a field.
- Submit: "Get offers". While pending, the button shows "Getting offers..." and is disabled; the results area shows
  three skeleton cards and `aria-busy="true"`.
- `201`: navigate to `/quotes/:quoteId` with the returned quote (no second request).
- `422 no-offer-available`: a ProblemAlert above the results area with a message per `reason` (see Messages).

### Quote (`/quotes/:quoteId`)

- Header: route (`NO 0150 -> SE 11122`), parcel summary, billable weight, zone, and "Valid until 09:30 (in 12 min)"
  computed from `expiresAt`. The relative part updates every 15 seconds and is not announced by screen readers.
- **QuoteCardList:** one card per offer in contract order (Economy, Standard, Express). Each card shows the service
  level as its heading, the price (`18.81 EUR`, tabular, with the accessible name "18.81 euros"), transit days
  ("3 to 5 days") and a radio button "Choose this offer". The list is a radio group labelled "Offers".
- **Expired state:** once the clock reaches `expiresAt` (or `status` is `EXPIRED` when loaded), every card shows the
  badge "Expired", the radio buttons are disabled, the booking form is hidden, and a "Get a fresh quote" button
  returns to `/quotes/new` with the same parcel and route filled in.
- **BookingForm** (shown once an offer is chosen): recipient name, address line 1, address line 2 (optional), city,
  reference (optional). Postcode and country are filled from the quote's destination and read-only, so a mismatch
  cannot be entered. Submit: "Book shipment".
- Booking and idempotency:
  - The first submit of an intent generates an `Idempotency-Key` with `crypto.randomUUID()`. Retries of the same
    intent (after a network error, or the explicit "Book again" in the demo) reuse it. Before submission, edits
    define a new intent. While submission is pending or its outcome is unknown, freeze the submitted payload and
    retain its key across route changes. Show ‘Booking outcome unknown’ with Retry using the identical payload/key and
    Open shipments. Expiry must not hide this recovery action: replay remains valid after expiry. Warn before reload
    or switching users that in-memory recovery will be lost. After reload, direct the user to shipments before
    attempting another booking.
  - While pending, the button is disabled and reads "Booking...".
  - `201`: status message "Shipment booked: SIM0000000001" with "Open shipment" and "Book again (same request)".
  - `200` with `Idempotency-Replayed: true`: status message "Already booked. This is the same shipment:
    SIM0000000001." This is the "book twice safely" demonstration.
  - Problems: see Messages.
- Loading a quote: header and card skeletons. `404 quote-not-found`: EmptyState "Quote not found." with a link to
  "New quote".

### Shipments (`/shipments`)

- **ShipmentTable** columns: booked (date and time), tracking number (monospace), recipient name, destination,
  service level, price, status badge, reference. Rows are ordered as the API returns them (newest first).
- Status filter: segmented control "All", "Booked", "Cancelled", which sets the `status` query parameter and resets
  pagination.
- Pagination: "Load more" appends the next page using `nextCursor`; the button disappears on the last page.
- Each row's tracking number is a link (a real `<a>`) to `/shipments/:shipmentId`.
- Empty state: "No shipments yet." with a "New quote" action. With a filter: "No cancelled shipments."
- Loading: five skeleton rows, `aria-busy="true"` on the table region.

### Shipment drawer (`/shipments/:shipmentId`)

- **ShipmentDrawer** is a dialog (`role="dialog"`, `aria-modal="true"`, labelled by its heading
  "Shipment SIM0000000001").
- Content: status badge, service level, price, booked time, cancelled time when present, origin, recipient block,
  reference, quote and offer IDs (monospace, small).
- Actions:
  - "Download label (SVG)" when `BOOKED`: calls `getShipmentLabel` as a blob and saves it with the file name from
    `Content-Disposition`.
  - "Cancel shipment" when `BOOKED`: opens a confirmation ("Cancel shipment SIM0000000001? It cannot be booked
    again.") with "Cancel shipment" and "Keep shipment". On success the drawer and the table row update to
    `Cancelled`.
  - "Close".
- **LabelPreview** (prototype, flag `label-preview` in `Membership.enabledFlags`): when on and the shipment is BOOKED,
  the drawer shows the label as `<img>` from an object URL of the downloaded SVG, alt text "Simulated label for
  SIM0000000001", revoked on replacement, cancellation, user switch or close. Preview has independent loading,
  failure and retry states; cancellation removes it immediately. When off, nothing is rendered and no request is made. The flag changes presentation only; download and
  cancel behave identically in both states.
- `404 shipment-not-found`: the drawer shows "Shipment not found." This is also what Tenant Beta sees for Tenant
  Alpha's shipment ID (the tenant-denial demonstration).

## States

Every view that loads data has these states, each with a test:

| State | Presentation |
|---|---|
| Loading | Skeletons in the shape of the content, `aria-busy="true"`, no layout shift when data arrives. |
| Empty | EmptyState with one sentence and one action. |
| Expired | Warning badge "Expired" with time, actions disabled, "Get a fresh quote". |
| Failed | ProblemAlert (`role="alert"`) with the problem's title, a message from the table below, "Reference: <correlationId>" in monospace, and "Try again" when retrying can help. Network failures without a problem body show "The server could not be reached." with "Try again". |
| Success | Content, plus a polite status message (`role="status"`) for completed actions. |

## Messages per problem type

| Problem `type` suffix | Where | Message |
|---|---|---|
| `validation-failed` | Form | Field messages from `errors[].detail` next to each field; summary "Check the highlighted fields." at the top of the form, focused. |
| `malformed-request` | ProblemAlert | "The request could not be read. Reload and try again." |
| `unauthenticated` | Any | Return to `/sign-in` with "Sign in again." |
| `no-tenant-membership` | Sign in | "This synthetic user belongs to no tenant." |
| `no-offer-available` with `ROUTE_NOT_SERVED` | Quote form | "The simulated carrier does not serve this route. Served countries: DE, DK, FI, NL, NO, SE." |
| `no-offer-available` with `BILLABLE_WEIGHT_ABOVE_LIMIT` | Quote form | "The billable weight is above 30 kg. Billable weight is the larger of actual weight and length x width x height / 5000." |
| `no-offer-available` with `SERVICE_LEVEL_NOT_AVAILABLE` | Quote form | "Express is available up to 10 kg billable weight. Choose another service level or Any." |
| `quote-not-found`, `offer-not-found` | Quote | "Quote not found." with "New quote". |
| `quote-expired` | Booking | Switch to the expired state: "This quote expired at 09:30. Get a fresh quote." |
| `recipient-does-not-match-quote` | Booking | "The recipient's postcode and country must match the quote's destination." (should not occur, because they are read-only) |
| `idempotency-key-reused` | Booking | "This booking changed after it was sent. Review the details and book again." The next submit uses a fresh key. |
| `quote-already-booked` | Booking | "This quote has already been booked." with "Open shipments". |
| `shipment-not-found` | Drawer | "Shipment not found." |
| `shipment-cancelled` | Drawer | "This shipment is cancelled, so it has no label." |
| `not-acceptable`, `unsupported-media-type`, `method-not-allowed`, `not-found`, `internal-error` | ProblemAlert | "Something went wrong on our side. Try again." with the reference. |

## Components

| Component | Responsibility | Test |
|---|---|---|
| `AppShell` | Navigation, skip link, user and tenant, theme override, route outlet | `accessibility.spec.ts` |
| `ThemeToggle` | System, Light, Dark override applied as `data-theme` on `<html>` | `ThemeToggle.test.tsx` |
| `ParcelForm` | Fields, client checks, mapping of server `errors[]` to fields, submit | `ParcelForm.test.tsx` |
| `QuoteCardList`, `QuoteCard` | Offer cards, radio group, expiry badge and countdown | `QuoteCardList.test.tsx` |
| `BookingForm` | Recipient fields, read-only destination, idempotency key per intent, replay message | `BookingForm.test.tsx` |
| `ShipmentTable` | Rows, filter, "Load more", links | `quote-book-label.spec.ts` |
| `ShipmentDrawer` | Dialog, focus management, download, cancel confirmation, label preview slot | `ShipmentDrawer.test.tsx` |
| `LabelPreview` | Flag-gated `<img>` preview | `prototype-flag.spec.ts` |
| `ProblemAlert` | Problem title, message, reference, retry | `ProblemAlert.test.tsx` |
| `EmptyState`, `StatusBadge`, `Skeleton`, `Button` | Presentational building blocks | covered by the tests above |

## Keyboard and accessibility requirements

Target: WCAG 2.2 level AA. axe-core supplements, and does not replace, a manual keyboard and screen reader pass
recorded in the pull request for each screen.

- **Keyboard:** everything is operable with the keyboard alone, in a logical tab order that follows the visual
  order. No keyboard traps except the drawer's intentional focus containment. Radio groups use arrow keys. Enter
  submits forms. Escape closes only the topmost active surface. Use a native modal dialog with an inert background;
  cancellation confirmation replaces the drawer’s action area. Dismissal restores focus to Cancel shipment. Closing
  restores the originating link, or the shipments heading after direct navigation. Direct drawer URLs load both list
  and detail; Close always returns within the application.
- **Focus:** a visible focus indicator on every interactive element: 2 px outline in `--action` with 2 px offset,
  at least 3:1 against adjacent colours. Opening the drawer moves focus to its heading; closing returns focus to the
  link that opened it. After a route change, focus moves to the page's `<h1>`. After a failed submit, focus moves to
  the error summary.
- **Forms:** every input has a programmatic label; hints and errors are linked with `aria-describedby`; invalid
  fields have `aria-invalid="true"`; required fields are marked in text, not only with an asterisk. Errors say what
  to do, not only what is wrong.
- **Announcements:** completed actions ("Offers ready", "Shipment booked", "Shipment cancelled") use one
  `role="status"` region; failures use `role="alert"`. The expiry countdown is not announced.
- **Contrast:** text at least 4.5:1 (3:1 for 24 px and larger or 18.66 px bold); controls, borders that identify
  controls, and focus indicators at least 3:1. Checked in both themes.
- **Target size:** interactive targets at least 24 by 24 CSS px.
- **Motion:** honour `prefers-reduced-motion`; nothing flashes.
- **Zoom and reflow:** usable at 400 % zoom and 320 CSS px width without horizontal scrolling, except the table,
  which scrolls inside its own region with a visible label.
- **Semantics:** one `<h1>` per screen; landmarks `nav`, `main`; the shipments table uses `<table>` with `<th>`
  scope and a caption; the offer list is a list; `lang="en"` on `<html>`.
- **Prices and codes:** prices are read as amount and currency; tracking numbers are plain text, never only in an
  image.

## End-to-end scenarios (`contract-e2e`)

| Spec | Scenario |
|---|---|
| `quote-book-label.spec.ts` | Sign in as `clerk-alpha`; quote `NO 0150 -> SE 11122`, 1200 g, 300 x 200 x 100 mm; see 15.05, 18.81 and 28.22 EUR; book Standard; book again with the same request and see the replay message with the same tracking number; open the shipment; download the label and check the file name and the simulation banner. |
| `expired-quote.spec.ts` | Open the seeded expired quote `33333333-3333-4333-8333-333333333333`; see the expired state; booking is not offered; "Get a fresh quote" fills the form. |
| `tenant-denial.spec.ts` | Book as `clerk-alpha`; switch to `clerk-beta`; open Alpha's quote and shipment URLs; see "Quote not found." and "Shipment not found."; Beta's table does not contain Alpha's shipment. |
| `prototype-flag.spec.ts` | `clerk-alpha` (flag on) sees the label preview in the drawer; `clerk-beta` (flag off) does not, and no label request is made when the drawer opens. |
| `accessibility.spec.ts` | axe-core on every screen and state (loading, empty, expired, failed, drawer open) in light and dark colour schemes; keyboard-only run of the main flow. |

## Binding layout decisions

Binding layout decisions: retain the listed color tokens and system typography. Use spacing steps 4, 8, 12, 16, 24
and 32 px. ParcelForm is at most 720 px wide, with two columns above 600 px available width and one below. The quote
page shows a read-only parcel summary above the offers; Edit parcel opens a prefilled new-quote form. Use three equal
offer columns when content width reaches 840 px, otherwise one column. BookingForm sits below, at most 640 px wide.
Retain the specified navigation and drawer dimensions.
