---
title: "Flow: quote, book and download a simulated label"
description: "The main v0.1 flow from parcel form to label, step by step, with the acceptance tests for each step and their status."
status: accepted
updated: 2026-11-09
modules: [tenants, quotes, shipments, audit]
---

# Flow: quote, book and download a simulated label

The structural version of this flow is the dynamic view `QuoteBookLabel` in `architecture/workspace.dsl`. This
document adds order, alternatives and failures, and names the acceptance tests for each step and their status. A named
test becomes evidence only once it exists, passes and asserts the step; each row says what it must assert. Rating and
expiry logic have passing Java tests. Signing in and creating and reading a quote pass over HTTP, in the application
started by the tests with test doubles for the token lookup and the quote store; so storage is tested in memory, and so
are booking, replay, shipment listing/reading and SVG labels over HTTP. The tenants, quotes and shipments schemas have paired up and down migrations (ADR 0016),
and JDBC adapters selected by `postgres`. Their database classes passed in [recorded CI evidence](../flows/attended-demo.md#recorded-database-evidence) (run `42612173809`). The attended demo is runnable (ADR 0018); durable
event publication remains planned.
The web sources now implement the in-scope screens and have passing logic tests for parcel validation, quote expiry,
booking key reuse and recovery, shipment pagination and label state. These run without a DOM or live HTTP server:
the `*.test.tsx` component tests and Playwright specs in the table remain planned. The attended launcher now serves
the workbench, with a separate memory-mode browser recording in `docs/demos/87-web-workbench-slice.md`.
No integrated browser-to-database quote/book/label result is claimed.
Parts of the flow are also executable scenarios, checked against what the code does when they run
([Executable scenarios](#executable-scenarios)).

Actors: a shipping clerk of Tenant Alpha signed in as the synthetic user `clerk-alpha`. All data is synthetic.

The attended runtime executes this HTTP flow on one origin. `DemoSmokeTest` starts the application with production
memory stores and drives synthetic sign-in/token selection, quote, booking, replay, list, detail and label through
real sockets, including foreign-tenant refusals and the public static page's strict CSP. The shared PostgreSQL
variant, `DemoPostgresSmokeIntegrationTest`, passed its three cases in that run through CI's reviewed database window. These smoke exchanges are
separate from the in-process scenario recorder. Its 15 new framework findings are accepted as exact known debt
for issue #92; its verdict remains `COVERAGE_INCOMPLETE` (ADR 0013).
See the [runbook](../flows/attended-demo.md).

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
    Note over Tenants,Quotes: Filter binds Tenant Alpha; each JDBC statement sets app.tenant_id locally
    Quotes->>Quotes: Rating with SyntheticTariff (zone, band, level, surcharge, rounding)
    Quotes->>DB: insert quote and offers (RLS with check)
    Quotes->>Quotes: publish QuoteIssued (no listener in v0.1, so no publication row)
    Quotes-->>Web: 201 Quote (offers, expiresAt = issuedAt + 30 min)

    Clerk->>Web: Choose STANDARD offer, enter recipient, Book
    Web->>Shipments: POST /api/v1/shipments, Idempotency-Key K
    Note over Shipments,DB: Lock tenant/key, then read prior booking
    Shipments->>Quotes: QuoteOffers.findOffer(tenant, quote, offer, now)
    Quotes-->>Shipments: Bookable(offer)
    Shipments->>DB: Lock tenant/quote, recheck, insert BOOKED with K and fingerprint
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
| 1 to 4: sign in as a synthetic user | A valid demo token resolves to exactly one membership; tenant hints cannot override membership; unknown body members are rejected; unknown token `401`, user without tenant `403`. | Passing, with a test double for the token lookup: `DemoTokenAuthenticationTest`, `TenantsHttpContractTest`. `MembershipDirectoryIntegrationTest` passed in that recorded CI run. |
| 5 to 6: submit the parcel form | The form sends a contract-valid `QuoteRequest`; invalid input is labelled before sending. | Planned: `ParcelForm.test.tsx` |
| Note after 6: tenant binding | Every transaction carries the authenticated tenant and only that tenant. | `RowLevelSecurityIntegrationTest` passed its plain-JDBC cases in recorded CI; adapter binding is implemented (ADR 0017); event `TenantScopeTest` remains planned. |
| 7: rating | Prices equal the golden table; bands, ordering, rounding and availability rules hold. | Passing: `SyntheticTariffTest`, `SyntheticTariffPropertiesTest`, `RatingTest` |
| 8 to 9: store and publish | Only offered quotes are stored, under the caller's tenant; `QuoteIssued` is published once. | Passing, in memory: `CreateQuoteTest`. `QuotesModuleIntegrationTest` passed its storage/lookup cases in recorded CI; durable publication remains unproved |
| 10: quote response | `201` with `Location`, offers in level order and `expiresAt = issuedAt + 30 min`, conforming to the contract. | Passing, with the store in memory: `QuotesHttpContractTest`. Planned: `QuoteCardList.test.tsx` |
| 11 to 14: book | Checks run in the documented order; the offer must be unexpired and the recipient must match the destination. | Passing, in memory: `BookShipmentTest` (the order of the checks and their rejections, the expiry through `QuoteOffers`, the recipient's country and postcode), `RecipientTest`; quotes' side: `QuoteExpiryTest`, `QuoteOffersServiceTest`. Planned: `BookingForm.test.tsx` |
| 15 to 17: store, publish, respond | Shipment, key and `ShipmentBooked` publication commit together; `201` conforms to the contract. | Passing, in memory: `BookShipmentTest` (the shipment is stored with its key and fingerprint, and `ShipmentBooked` is published once, not for a replay or a rejection). `ShipmentsHttpContractTest` passes the `201` response. Planned: `ShipmentEventsIntegrationTest` (in one transaction) |
| 18 to 21: retry with the same key | Same key and body return the same shipment with `200`; concurrent duplicates create one row; a different body gives `422 idempotency-key-reused`. | Passing, in memory: `RequestFingerprintTest`, `BookShipmentTest` (the same key and body replay the shipment; another body is refused). `ShipmentsHttpContractTest` passes the `200`, `422` and replay after expiry. `BookingIdempotencyIntegrationTest` passed its database cases in recorded CI |
| 22 to 25: audit after commit | One entry per event under the event's tenant; redelivery has no second effect; a failed write is replayed after restart. | Planned: `ShipmentAuditIntegrationTest`, `AuditDeduplicationTest`, `AuditReplayIntegrationTest` |
| 26 to 29: label | Byte-identical, escaped, marked SVG with the documented headers; the drawer offers the download. | Passing: `SvgLabelRendererTest`, `ShipmentsHttpContractTest` (stored in memory). Planned: `ShipmentDrawer.test.tsx`, cancelled-shipment label denial |
| Whole flow in a browser | Steps 1 to 29 through the UI against the real API, including the double booking and the download; axe finds no violations on each screen. | Planned: `quote-book-label.spec.ts`, `accessibility.spec.ts` |

## Alternatives and failures

| Situation | Expected result | Acceptance tests and status |
|---|---|---|
| Unknown member in a quote request | `400 validation-failed` names its escaped JSON Pointer through 200 Unicode code points; above that, `400 malformed-request` without `errors`. | Passing: `ProblemDetailsHttpContractTest`, including supplementary characters at both boundaries. |
| Route not served, parcel too heavy, EXPRESS above 10000 g | `422 no-offer-available` with `reason`; no quote stored; the UI explains the reason. | Passing: `RatingTest` (the reason), `CreateQuoteTest` (nothing stored), `QuotesHttpContractTest` (the problem). Planned: `ProblemAlert.test.tsx` |
| Offer expired before booking | `422 quote-expired`; the UI shows the expired state and offers a fresh quote. | Passing: `QuoteExpiryTest` (the offer lookup says Expired), `BookShipmentTest` (the booking is refused from the expiry instant on, in memory). Planned: ShipmentsHttpContractTest asserts server rejection with 422; BookingForm.test.tsx asserts the rejection transition; expired-quote.spec.ts verifies the initially expired screen only. |
| Same quote booked with a different key | `409 quote-already-booked`. | Passing, in memory: `BookShipmentTest` (the second key is refused). `BookingIdempotencyIntegrationTest` passed the two-key race in recorded CI |
| Recipient postcode differs from the quote's destination | `422 recipient-does-not-match-quote`. | Passing, in memory: `BookShipmentTest` (the booking is refused). Planned: `ShipmentsHttpContractTest` (the `422`) |
| Tenant Beta uses Tenant Alpha's quote or shipment ID | `404` exactly as for an unknown ID, for read, book, cancel and label. | Passing, in memory: `QuoteOffersServiceTest` (another tenant's quote is not found), `BookShipmentTest` (Tenant Beta cannot book Tenant Alpha's quote) and, for reading a quote over HTTP, `QuotesHttpContractTest`. Planned: `CrossTenantAccessTest`, `tenant-denial.spec.ts` |
| Shipment cancelled, then label requested | `409 shipment-cancelled`. | Planned: `ShipmentsHttpContractTest` |
| Audit write fails | Booking stays committed; the publication stays incomplete and is replayed. | Planned: `AuditReplayIntegrationTest` |
| Audit listener deleted | `ArchitectureConformanceTest` fails: absence of REQUIRED `audit -> shipments`. | `ArchitectureConformanceTest` runs in every build; shipments is built, and it evaluates this arrow once audit is built too. |
| Audit listener present but writes nothing | Architecture check passes (its documented limit); the integration test fails. | Planned: `ShipmentAuditIntegrationTest` |

Shipment browsing is also exercised by `ShipmentsHttpContractTest`: tenant-scoped detail and labels report a foreign
ID exactly as unknown; list pages use descending booking time and ID, respect the status filter and reject cursors
from another tenant or filter. None of this supplies database isolation or browser evidence.

The tracking number assigned during booking always uses `SIM` and ten ASCII digits, including on servers whose
default formatting locale uses other digits. `JdbcTrackingNumbersTest` checks the production formatter under
`ar-EG` without a database; it supplies no evidence of sequence allocation or transaction behavior.

## Executable scenarios

The dynamic view `QuoteBookLabel` is this flow's documentation overview and is not executable: some of its steps
combine several actions. Focused views whose keys start with `Scenario` are run instead (ADR 0013). Each has a
sidecar, `architecture/scenarios/<key in kebab case>.json`, that gives every step of the view a stable id, a kind, its
endpoints, an operation (interface and method, event type, HTTP operation or schema), a phase, a cardinality and the
steps it must follow; the sidecar is validated against the view, step by step and in both directions. A test runs the
scenario on the code. A test-only recorder, which knows nothing of the steps, records two kinds of boundary
interaction: calls through an interface of a module's `api` package (Spring's `ProxyFactory` around every bean that
implements one, the caller found on the stack and mapped by `contract.packages`) and events published through the
application context. It records nothing else, so a coverage contract, below, decides which steps a run can evaluate at
all. A comparator then reports MISSING_STEP, UNEXPECTED_INTERACTION, ORDER_VIOLATION and COVERAGE_GAP; outcomes are
asserted separately.

| Scenario | Test | Evaluated today | Not evaluated today, with the reason in the report |
|---|---|---|---|
| `ScenarioQuote` | `QuoteScenarioTest` | `CurrentTenant.require()` once, then `QuoteIssued` published once | the clerk, the HTTP exchange, the simulated carrier, the database write |
| `ScenarioTenantDenial` | `TenantDenialScenarioTest` | shipments' `CurrentTenant.require()` once, then `QuoteOffers.findOffer` once, through the real `BookShipment` | the clerk, the HTTP exchange, the two database reads |
| First booking, booking replay, label | Planned: `FirstBookingScenarioTest`, `BookingReplayScenarioTest`, `LabelScenarioTest` | | |

The accepted ledger has 215 distinct known gaps: 115 HTTP/security Spring accesses, 94 JDBC accesses and six
factory-parameter findings. The expected reports say `COVERAGE_INCOMPLETE`: "coverage incomplete: 215 known gaps",
with failed coverage checks and `coverage.verified=false`. The ledger reader accepts the JDBC debt classification
(ADR 0013). The exact ledger is `apps/shipping-api/src/test/resources/scenario-coverage-gaps.tsv`; every entry is
classified and tagged
"phase 3 / option 2". No scenario conformance is established while those gaps remain. A matching ledger and held
outcomes permit the build, not a claim of complete coverage. New, changed, duplicate or disappeared findings fail
unless the ledger is explicitly updated and reviewed; tests and documentation generation never refresh it.

What a report claims follows its verdict. A run without coverage gaps that conforms says that its evaluated steps, counted, conformed in one
exercised run within the recorder's coverage, and that the steps not evaluated were neither checked nor assumed; it
does not cover all paths, schedules or inputs. A step that touches a person, a simulated system or a planned module, or
whose kind the run cannot observe (HTTP and SQL until this recorder observes them, listener executions until a module
has one), is NOT_EVALUATED and counted. The separate HTTP contract tests do not make a scenario's HTTP steps observed.
A run that evaluates no step is NO_STEP_EVALUATED, never CONFORMS, and its
report says that no declared step was verified. A run with build blockers says that it does not conform. The tenant-denial
outcomes (Beta's booking is rejected as for an unknown quote, nothing is stored for Beta, Alpha's offer stays
bookable) are assertions of the test, apart from the step verdict.

Detected coverage gaps remain COVERAGE_GAP findings and prevent conformance. Exact known debt is separated from
build blockers; interaction failures, unledgered findings and stale ledger entries fail the build. The checks below do not prove every runtime receiver or task
lifetime; the unexamined cases in [known gaps](../gaps.md#6-tests-and-quality-gates) can still escape detection.

- **Receivers.** The census classifies interface signatures as interceptable; a separate bounded walk checks the
  objects reachable before and after the run from module beans and census static fields, including captured lambda
  arguments, records, collections and nested references. Visits track object identity and owning module, including
  shared containers; visiting a receiver through its own module does not exempt it through another. Another module's
  object that no proxy wraps, an event publisher other than the context, an application class outside every module,
  or a library object the walk cannot look into is a gap. A module object registered as a ready singleton, which no
  post-processor sees, is a gap, and circular references are off, so no bean can see another's raw instance.
- **Call sites.** All main module classes, reached test module classes and their module dependency closure are read.
  A cross-module use that is not an `api` interface
  call with value-only parameters and results, reflection, method handles, Spring lookups from module code, a method
  other than a constructor that takes another module's service as a parameter, and a listener method while listener
  executions are not observed are gaps. Values are exempt only through constructors, accessors and
  built-ins of value-only types: records, enums and sealed types of values, JDK value classes and JDK containers of
  them.
- **Time.** The census refuses task-capable APIs in its examined code: thread creation/builders, executors, futures,
  timers and explicit parallel dispatch, with a narrow set of synchronous thread/concurrency operations allowed.
  It does not try to prove those tasks finish. The virtual-thread and pre-existing-pool fixtures are refused even
  when their work is released only after close. A recorded interaction on another thread, a new non-system platform
  thread still present at the final snapshot, or a late interaction seen by close is also a gap. These snapshots do
  not enumerate virtual tasks, track tasks on existing workers, or detect interactions occurring after close.
- **Phases and repeats.** No run observes commits yet, so a step declared `AFTER_COMMIT` that would be evaluated is
  refused as a gap. A sidecar that repeats one signature with a varying cardinality is refused, since an interaction
  could belong to more than one step; repeats need exact cardinalities.

The model's views are read by a restricted text reader that rejects unsupported syntax, including the tested comment
forms. `ScenarioModelTest` compares the repository's dynamic views with Structurizr's generated view pages. Its
accepted/refused syntax fixtures assert against the restricted reader alone, using a fabricated kit report.

The negative fixtures run altered code. `ScenarioFixturesTest`: a removed call that keeps the outcome, an added call
along a permitted relationship, two swapped interactions, a disabled call observer, a disabled publication observer
and an `AFTER_COMMIT` publication that runs before any commit each fail for their reason, and two runs interleaved on
two threads pass. `CoverageBypassTest`: a captured `Supplier`, a nested reference, a static field, a singleton
registered as an object, a lookup after the recording ends, a test-only implementation and a value type with behaviour
each reach another module unobserved, keep three asserted outcomes, and fail with a coverage gap. Two more fixtures
hold virtual-thread and pre-existing-executor work until after close; both keep three outcomes and fail on dispatch.
`CoverageLedgerTest` checks exact debt, additions, removed entries, changed descriptors, disappeared gaps and
multiplicity. `CoverageBypassTest` also introduces a new Spring access whose owner and member are already ledgered;
it still fails, while the three quote outcomes hold. `ReachabilityTest` checks a shared raw receiver and shared containers in both module traversal orders, including a
container cycle. `SidecarValidationTest` refuses
the repeated-signature case A (0..*), B (1), C (1..*).

Each run writes `apps/shipping-api/build/reports/scenarios/<key in kebab case>.json` and `.md`, negative fixtures under
`fixtures/`, before the test asserts anything. The JSON (schema `shipping-scenarios/report/v1`) is for the
documentation site: `verdict`, `buildAcceptable`, `buildBlockers`, `outcomesHeld`, `claim`, `scenario`, `test`, `provenance` (the revision, and SHA-256
digests of the model, the sidecar and the main classes), `summary`, `observers`, `coverage`, `stimuli`, `steps` (each
with its status, its reason and its interactions as `run:seq`), `interactions`, `findings` and `outcomes`.
Coverage includes `knownGapCount`, classified `knownGaps`, `staleEntries` and the ledger path, alongside every raw gap.
`findings[].fails` and `summary.failingFindings` still mean failure to establish conformance, even for known gaps;
`buildBlockers` describes the narrower build policy. Operations
are types and methods; no argument, return value or payload is recorded.

## What this flow does not show

Tracking states, returns, real carriers and production identity (`docs/gaps.md`).
