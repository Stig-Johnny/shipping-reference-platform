---
title: "ADR 0013: Scenario views are checked against recorded interactions"
description: "Each executable scenario view has a validated sidecar of machine-readable steps; a test runs the scenario, a test-only recorder observes every interaction that crosses a module boundary, and a comparator checks the run against the steps, with the outcome asserted separately."
status: accepted
updated: 2026-11-07
date: 2026-11-07
decision-makers: repository owner
enforcedBy:
  - "java: QuoteScenarioTest and TenantDenialScenarioTest compare one in-process run of their scenarios with the views (partial, see Implementation status)"
  - "java: ScenarioFixturesTest runs altered implementations that must fail, and two interleaved runs that must pass"
  - "java: SidecarValidationTest and ScenarioModelTest reject sidecars and model text that do not match the views"
plannedEnforcement:
  - "java: FirstBookingScenarioTest, BookingReplayScenarioTest and LabelScenarioTest"
  - "java: HTTP exchanges observed at the API's edge, database steps observed through JDBC, listener executions observed after commit"
---

# ADR 0013: Scenario views are checked against recorded interactions

## Context and Problem Statement

The static check of ADR 0002 proves that compiled references keep to the declared module boundaries and that a
`REQUIRED` relationship has at least one reference. It does not prove that the collaborations a dynamic view draws
take place when the scenario runs, in the drawn order, and that nothing else does. Acceptance tests assert outcomes,
and an implementation can keep the outcome while it skips a drawn call, adds one that is not drawn, or swaps two. The
overview view `QuoteBookLabel` cannot be compared with a run as it is: several of its steps combine actions, and one
arrow shows a code dependency against the direction of delivery. How can a scenario diagram be held to what the code
does at run time, without claiming more than one run shows?

## Decision Drivers

- A diagram that a reviewer reads should fail the build when the code stops doing what it draws.
- A step is identified by data, never by matching its prose.
- Interactions are recorded independently of what the scenario expects, or extra ones stay invisible.
- What the recorder cannot see is a finding, never silence.
- No new dependency; c4-contract-kit stays free of Spring, and `Conformance.check` keeps its meaning.
- A claim follows its evidence: one run shows one run.

## Considered Options

1. Executable scenario views, each with a validated sidecar of machine-readable steps; a test-only recorder that
   observes every boundary interaction; a comparator; all in the API's test code.
2. Literal trace equality with the overview view `QuoteBookLabel`.
3. Matching step descriptions against log lines or traces.
4. Outcome assertions only.

## Decision Outcome

Chosen option 1.

- **Views.** A dynamic view whose key starts with `Scenario` is executable: one view per scenario, for quote, first
  booking, booking replay, tenant denial and label. Each step is a single interaction. `QuoteBookLabel` stays the
  documentation overview, and its description says that it is not executable. An arrow follows its model
  relationship: a listener's arrow shows the code dependency, and a publication's arrow points to the database, which
  holds the publication registry.
- **The sidecar.** `architecture/scenarios/<view key in kebab case>.json`, schema `shipping-scenarios/sidecar/v1`,
  has one entry per step occurrence of the view, keyed by its order:
  - `id`, stable and in kebab case, and `kind`: `USER_ACTION`, `HTTP_EXCHANGE`, `MODULE_API_CALL`,
    `EVENT_PUBLICATION`, `LISTENER_EXECUTION`, `DATABASE_READ`, `DATABASE_WRITE` or `SIMULATED_EXTERNAL`;
  - `from` and `to`, element identifiers of the model, equal to the view step's;
  - `operation`: an `api` interface and method; an event record; an HTTP method, path, `operationId` and status; or a
    statement and a schema. A person's action and a simulated system have none;
  - `phase` (`SYNCHRONOUS` or `AFTER_COMMIT`), `cardinality` (`n`, `n..m` or `n..*`) and `after`, the earlier steps
    that must come first: strict where one step causes the next, left out where asynchronous work may interleave.

  Reading a sidecar validates it completely: strict JSON without unknown or repeated members; exactly one entry for
  every step of the view and a step of the view for every entry, with the same endpoints; endpoint types that fit the
  kind; an interface method, or an event record, that exists in the `api` package of the right module.
- **The recorder** is test code. JDK proxies, made with Spring's `ProxyFactory`, around every bean that implements an
  interface of a module's `api` package record each call of it, and a wrapped event multicaster records each event
  published through the application context. The caller is the first frame of the application on the stack, mapped to
  a module by `contract.packages`. It records without knowing the steps.
- **Coverage is verified before each run.** Every bean that implements an `api` interface must be wrapped; no field of
  a module's bean may hold another module's `api` implementation unwrapped, or an event publisher other than the
  context; a probe event must reach the publication observer. A listener method, while listener executions are not
  observed, and a cross-module call that no proxy can intercept, found by reading the main class files, are gaps.
- **The comparator** reports `MISSING_STEP`, `UNEXPECTED_INTERACTION`, `ORDER_VIOLATION` and `COVERAGE_GAP`, each of
  which fails the scenario, and `NOT_EVALUATED`, which is counted and does not: a step that touches a person, a
  simulated system or a planned module, or whose kind the run cannot observe. A run without a stimulus fails. Outcomes
  are asserted separately from the interactions.
- **Reports.** Each run writes `build/reports/scenarios/<view slug>.json`, schema `shipping-scenarios/report/v1`, and a
  Markdown version, before the test asserts anything. They name the revision and the SHA-256 digests of the model, the
  sidecar and the main classes, and hold no argument, return value or payload.
- **The claim.** Every report states it: one exercised run of the scenario conforms to its declared steps, within the
  recorder's coverage; not all paths, schedules or inputs. A run that evaluates no step reports `NO_STEP_EVALUATED`,
  never `CONFORMS`.
- **Placement.** The scenario support is test code of `:apps:shipping-api` (package `scenarios`). Moving its model,
  comparator and reports into c4-contract-kit, free of Spring and under an entry point of their own, is later work.

### Consequences

- Good, because a removed, added or reordered interaction that keeps the outcome fails the build, which the static
  check and the outcome assertions both let through.
- Good, because a reader can see the diagram, its machine-readable steps and one run's result side by side.
- Bad, because each scenario view needs a sidecar kept in step with it. Validation finds the drift; a person writes
  the fix.
- Bad, because the model's views are read by a restricted text reader: the review of Structurizr covers only the
  kit's and the docs tool's parsers. The reader refuses what it does not read exactly, and every run cross-checks its
  modules with the kit's parse.
- Limit: the recorder sees calls through `api` interfaces of Spring beans and events published through the context.
  Static calls, references kept outside a bean's own fields, and overloads with one name are outside what it tells
  apart (`docs/gaps.md`, section 6).
- Limit: a passing run shows one schedule with one input. Concurrency, other inputs and failure paths need runs of
  their own.

### Confirmation

- `java` runs `./gradlew check`. In `:apps:shipping-api`, `QuoteScenarioTest` and `TenantDenialScenarioTest` run their
  scenarios, and `ScenarioFixturesTest` runs altered code: a removed call that keeps the outcome is a missing step, an
  added call along a permitted relationship an unexpected interaction, two swapped interactions an order violation,
  a disabled call observer and a disabled publication observer coverage gaps, never missing steps, and a run without
  a stimulus fails; two runs that interleave on two threads pass. `SidecarValidationTest` and `ScenarioModelTest`
  reject sidecars and model text that do not match.
- `control.scenario-conformance` in `docs/capabilities/capabilities.yaml` records the status.

## Implementation status (2026-11-07)

What is built, and where it differs from the decision above. The decision stands; the differences are open work.

- **In process, on the built modules.** The scenarios run in a Spring context that the tests wire, with the real
  classes of tenants, quotes and shipments and the in-memory stores; the application's own wiring is planned. HTTP
  exchanges, database steps and listener executions are not observed yet, so those steps are not evaluated, and each
  report says why.
- **Two of five scenarios.** `ScenarioQuote` evaluates two of its six steps: `CurrentTenant.require()` once, then
  `QuoteIssued` published once. `ScenarioTenantDenial` evaluates two of its six, through the real booking use case:
  shipments' `CurrentTenant.require()`, then `QuoteOffers.findOffer`. First booking, booking replay and label are
  planned.

## More Information

- The scenarios, their steps and the report format: `docs/flows/quote-book-label.md`, "Executable scenarios".
- What a green scenario does not prove: `docs/gaps.md`, section 6.
- Related: ADR 0002 (the static check), ADR 0006 (events and listeners), ADR 0012 (the modules, carried forward from
  ADR 0003).
