---
title: "Flow: quote, book and download a simulated label"
description: "The main v0.1 flow from parcel form to label, step by step, with the acceptance tests for each step and their status."
status: accepted
updated: 2026-11-07
modules: [tenants, quotes, shipments, audit]
---

# Flow: quote, book and download a simulated label

The structural version of this flow is the dynamic view `QuoteBookLabel` in `architecture/workspace.dsl`. This
document adds order, alternatives and failures, and names the acceptance tests for each step and their status. A named
test becomes evidence only once it exists, passes and asserts the step; each row says what it must assert. Rating and
expiry logic have passing Java tests; quote storage and publication are tested in memory. The complete HTTP/database
flow remains planned. Parts of the flow are also executable scenarios, checked against what the code does when they
run ([Executable scenarios](#executable-scenarios)).

Actors: a shipping clerk of Tenant Alpha signed in as the synthetic user `clerk-alpha`. All data is synthetic.

## Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Clerk as Shipping clerk
    participant Web as Web app
    participant Tenants as tenants
    participant Quotes as quotes
    participant Shipments as shipments
    participant DB as PostgreSQL
    participant Registry as Publication registry
    participant Audit as audit

    Clerk->>Web: Choose synthetic user clerk-alpha
    Web->>Tenants: GET /api/v1/me (Bearer demo-clerk-alpha)
    Tenants->>DB: resolve_demo_token(sha256(token))
    Tenants-->>Web: 200 Membership (Tenant Alpha, enabledFlags)

    Clerk->>Web: Enter parcel, origin, destination
    Web->>Quotes: POST /api/v1/quotes
    Note over Tenants,Quotes: Filter binds Tenant Alpha; every transaction sets app.tenant_id
    Quotes->>Quotes: Rating with SyntheticTariff (zone, band, level, surcharge, rounding)
    Quotes->>DB: insert quote and offers (RLS with check)
    Quotes->>Quotes: publish QuoteIssued (no listener in v0.1, so no publication row)
    Quotes-->>Web: 201 Quote (offers, expiresAt = issuedAt + 30 min)

    Clerk->>Web: Choose STANDARD offer, enter recipient, Book
    Web->>Shipments: POST /api/v1/shipments, Idempotency-Key K
    Shipments->>Quotes: QuoteOffers.findOffer(tenant, quote, offer, now)
    Quotes-->>Shipments: Bookable(offer)
    Shipments->>DB: insert shipment BOOKED with K and fingerprint
    Shipments->>Registry: ShipmentBooked (same transaction)
    Shipments-->>Web: 201 Shipment (SIM tracking number)

    Clerk->>Web: Book again (retry, same K and body)
    Web->>Shipments: POST /api/v1/shipments, Idempotency-Key K
    Shipments->>DB: find by (tenant, K): fingerprint matches
    Shipments-->>Web: 200 same Shipment, Idempotency-Replayed: true

    Registry-)Audit: ShipmentBooked after commit (async)
    Audit->>Tenants: TenantScope.runAs(event.tenantId)
    Audit->>DB: insert audit entry on conflict (tenant, eventId) do nothing
    Audit-->>Registry: listener returned: publication complete

    Clerk->>Web: Open shipment drawer, Download label
    Web->>Shipments: GET /api/v1/shipments/{id}/label
    Shipments->>Shipments: SvgLabelRenderer (deterministic, escaped, marked SIMULATED)
    Shipments-->>Web: 200 image/svg+xml, attachment
```

## Steps and acceptance tests

| Step (sequence numbers) | What must hold | Acceptance tests and status |
|---|---|---|
| 1 to 4: sign in as a synthetic user | A valid demo token resolves to exactly one membership; tenant hints cannot override membership; unknown body members are rejected; unknown token `401`, user without tenant `403`. | Planned: `DemoTokenAuthenticationTest`, `TenantsHttpContractTest` |
| 5 to 6: submit the parcel form | The form sends a contract-valid `QuoteRequest`; invalid input is labelled before sending. | Planned: `ParcelForm.test.tsx` |
| Note after 6: tenant binding | Every transaction carries the authenticated tenant and only that tenant. | Planned: `RowLevelSecurityIntegrationTest`, `TenantScopeTest` |
| 7: rating | Prices equal the golden table; bands, ordering, rounding and availability rules hold. | Passing: `SyntheticTariffTest`, `SyntheticTariffPropertiesTest`, `RatingTest` |
| 8 to 9: store and publish | Only offered quotes are stored, under the caller's tenant; `QuoteIssued` is published once. | Passing, in memory: `CreateQuoteTest`. Planned: `QuotesModuleIntegrationTest` |
| 10: quote response | `201` with `Location`, offers in level order and `expiresAt = issuedAt + 30 min`, conforming to the contract. | Planned: `QuotesHttpContractTest`, `QuoteCardList.test.tsx` |
| 11 to 14: book | Checks run in the documented order; the offer must be unexpired and the recipient must match the destination. | Passing, quotes' side: `QuoteExpiryTest`, `QuoteOffersServiceTest` (the offer lookup's order and expiry). Planned: `BookShipmentTest`, `BookingForm.test.tsx` |
| 15 to 17: store, publish, respond | Shipment, key and `ShipmentBooked` publication commit together; `201` conforms to the contract. | Planned: `ShipmentEventsIntegrationTest`, `ShipmentsHttpContractTest` |
| 18 to 21: retry with the same key | Same key and body return the same shipment with `200`; concurrent duplicates create one row; a different body gives `422 idempotency-key-reused`. | Planned: `BookingIdempotencyIntegrationTest`, `RequestFingerprintTest` |
| 22 to 25: audit after commit | One entry per event under the event's tenant; redelivery has no second effect; a failed write is replayed after restart. | Planned: `ShipmentAuditIntegrationTest`, `AuditDeduplicationTest`, `AuditReplayIntegrationTest` |
| 26 to 29: label | Byte-identical, escaped, marked SVG with the documented headers; the drawer offers the download. | Planned: `SvgLabelRendererTest`, `ShipmentsHttpContractTest`, `ShipmentDrawer.test.tsx` |
| Whole flow in a browser | Steps 1 to 29 through the UI against the real API, including the double booking and the download; axe finds no violations on each screen. | Planned: `quote-book-label.spec.ts`, `accessibility.spec.ts` |

## Alternatives and failures

| Situation | Expected result | Acceptance tests and status |
|---|---|---|
| Route not served, parcel too heavy, EXPRESS above 10000 g | `422 no-offer-available` with `reason`; no quote stored; the UI explains the reason. | Passing: `RatingTest` (the reason), `CreateQuoteTest` (nothing stored). Planned: `QuotesHttpContractTest`, `ProblemAlert.test.tsx` |
| Offer expired before booking | `422 quote-expired`; the UI shows the expired state and offers a fresh quote. | Passing, quotes' side: `QuoteExpiryTest` (the offer lookup says Expired). Planned: ShipmentsHttpContractTest asserts server rejection with 422; BookingForm.test.tsx asserts the rejection transition; expired-quote.spec.ts verifies the initially expired screen only. |
| Same quote booked with a different key | `409 quote-already-booked`. | Planned: `BookingIdempotencyIntegrationTest` |
| Recipient postcode differs from the quote's destination | `422 recipient-does-not-match-quote`. | Planned: `BookShipmentTest` |
| Tenant Beta uses Tenant Alpha's quote or shipment ID | `404` exactly as for an unknown ID, for read, book, cancel and label. | Passing, quotes' side: `QuoteOffersServiceTest` (another tenant's quote is not found). Planned: `CrossTenantAccessTest`, `tenant-denial.spec.ts` |
| Shipment cancelled, then label requested | `409 shipment-cancelled`. | Planned: `ShipmentsHttpContractTest` |
| Audit write fails | Booking stays committed; the publication stays incomplete and is replayed. | Planned: `AuditReplayIntegrationTest` |
| Audit listener deleted | `ArchitectureConformanceTest` fails: absence of REQUIRED `audit -> shipments`. | `ArchitectureConformanceTest` runs in every build; it evaluates this arrow once audit and shipments are built. |
| Audit listener present but writes nothing | Architecture check passes (its documented limit); the integration test fails. | Planned: `ShipmentAuditIntegrationTest` |

## Executable scenarios

The dynamic view `QuoteBookLabel` is this flow's documentation overview and is not executable: some of its steps
combine several actions. Focused views whose keys start with `Scenario` are run instead (ADR 0014). Each has a
sidecar, `architecture/scenarios/<key in kebab case>.json`, that gives every step of the view a stable id, a kind, its
endpoints, an operation (interface and method, event type, HTTP operation or schema), a phase, a cardinality and the
steps it must follow; the sidecar is validated against the view, step by step and in both directions. A test runs the
scenario on the code. A test-only recorder, which knows nothing of the steps, records every interaction that crosses a
module boundary: calls through an interface of a module's `api` package (Spring's `ProxyFactory` around every bean that
implements one, the caller found on the stack and mapped by `contract.packages`) and events published through the
application context. A comparator then reports MISSING_STEP, UNEXPECTED_INTERACTION, ORDER_VIOLATION and COVERAGE_GAP;
outcomes are asserted separately.

| Scenario | Test | Evaluated today | Not evaluated today, with the reason in the report |
|---|---|---|---|
| `ScenarioQuote` | `QuoteScenarioTest` | `CurrentTenant.require()` once, then `QuoteIssued` published once | the clerk, the HTTP exchange, the simulated carrier, the database write |
| `ScenarioTenantDenial` | `TenantDenialScenarioTest` | that the lookup makes no call across a module boundary | every step: each touches the clerk, the planned shipments module or the database |
| First booking, booking replay, label | Planned: `FirstBookingScenarioTest`, `BookingReplayScenarioTest`, `LabelScenarioTest` | | |

A passing run claims what its report says: one exercised run of the scenario conforms to its declared steps, within
the recorder's coverage, not all paths, schedules or inputs. A step that touches a person, a simulated system or a
planned module, or whose kind the run cannot observe (HTTP and SQL until the API and the database run, listener
executions until a module has one), is NOT_EVALUATED and counted; a run that evaluates no step is NO_STEP_EVALUATED,
never CONFORMS. Coverage is verified before each run: an `api` bean without a proxy, a module bean's field that holds
another module's `api` implementation unwrapped or a publisher other than the context, a probe event that does not reach
the publication observer, a listener method, or a cross-module call that no proxy can intercept is a COVERAGE_GAP. The
negative fixtures of `ScenarioFixturesTest` run altered code: a removed call that keeps the outcome, an added call along
a permitted relationship, two swapped interactions, a disabled call observer and a disabled publication observer each
fail for their reason, and two runs interleaved on two threads pass.

Each run writes `apps/shipping-api/build/reports/scenarios/<key in kebab case>.json` and `.md`, negative fixtures under
`fixtures/`, before the test asserts anything. The JSON (schema `shipping-scenarios/report/v1`) is for the
documentation site: `verdict`, `outcomesHeld`, `claim`, `scenario`, `test`, `provenance` (the revision, and SHA-256
digests of the model, the sidecar and the main classes), `summary`, `observers`, `coverage`, `stimuli`, `steps` (each
with its status, its reason and its interactions as `run:seq`), `interactions`, `findings` and `outcomes`. Operations
are types and methods; no argument, return value or payload is recorded.

## What this flow does not show

Tracking states, returns, real carriers and production identity (`docs/gaps.md`).
