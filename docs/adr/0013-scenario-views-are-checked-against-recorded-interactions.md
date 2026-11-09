---
title: "ADR 0013: Scenario views are checked against recorded interactions"
description: "Each executable scenario view has a validated sidecar; a test-only recorder observes module API calls and publications, bounded checks retain detected coverage gaps with an exact debt ledger, and a comparator checks the recording against the steps, with outcomes asserted separately."
status: accepted
updated: 2026-11-09
date: 2026-11-07
decision-makers: repository owner
enforcedBy:
  - "java: QuoteScenarioTest and TenantDenialScenarioTest compare one in-process run of their scenarios with the views (partial, see Implementation status)"
  - "java: ScenarioFixturesTest runs altered implementations that must fail, and two interleaved runs that must pass"
  - "java: CoverageBypassTest runs implementations that reach another module unobserved, each of which must be a coverage gap"
  - "java: ReachabilityTest checks shared receivers and containers in both owning-module traversal orders"
  - "java: SidecarValidationTest and ScenarioModelTest reject sidecars and model text that do not match the views, and ScenarioModelTest compares the reader with Structurizr"
  - "java: ScenarioReportTest holds each verdict to its claim"
  - "java: CoverageLedgerTest rejects new, changed, disappeared and duplicate coverage debt"
plannedEnforcement:
  - "java: FirstBookingScenarioTest, BookingReplayScenarioTest and LabelScenarioTest"
  - "java: HTTP exchanges observed at the API's edge, database steps observed through JDBC, listener executions and commits observed, so that AFTER_COMMIT steps can be evaluated"
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
- Detected coverage gaps must prevent conformance; only exact reviewed debt may pass the build as incomplete.
- Unexamined cases must be disclosed.
- No new dependency; c4-contract-kit stays free of Spring, and `Conformance.check` keeps its meaning.
- A claim follows its evidence: one run shows one run.

## Considered Options

1. Executable scenario views, each with a validated sidecar of machine-readable steps; a test-only recorder of
   boundary interactions, with bounded checks that refuse detected coverage gaps; a comparator; all in the API's
   test code.
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
- **Detected coverage gaps remain refused, before and after each run.** The census classifies interface signatures as
  interceptable; the walk separately checks reachable receivers. Neither proves all runtime receivers or task
  lifetimes. Unexamined cases remain in `docs/gaps.md`, section 6.
  - Every bean that implements an `api` interface must be wrapped, and a probe event must reach the publication
    observer.
  - A bounded walk follows references from module beans and census static fields: fields, captured lambda arguments,
    records, JDK collections and nested references. Visits track object identity and owning module, including shared
    containers. Another module's object that
    no proxy wraps, an event publisher other than the context, an application class outside every module, or an
    object it cannot look into is a gap. A module object registered as a ready singleton, which no post-processor
    sees, is a gap, and circular references are off.
  - All main module classes, reached test module classes and their module dependency closure are read. A cross-module
    use other than a call of an `api` interface whose
    parameters and results are values, reflection, method handles and Spring lookups from module code, a method other
    than a constructor that takes another module's service as a parameter, and a listener method while listener
    executions are not observed, are gaps. Values are exempt only through constructors,
    accessors and built-ins of value-only types.
  - Task-capable APIs in the census (thread creation/builders, executors, futures, timers and explicit parallel
    dispatch) are refused, with narrow synchronous exceptions; tasks are not tracked to completion. Virtual-thread
    and pre-existing-pool fixtures held until after close are refused by their dispatch sites. Recorded interactions
    on another thread, new non-system platform threads present at the final snapshot, and late interactions seen by
    close are also gaps. Snapshots do not prove quiescence: they omit virtual tasks, tasks on existing workers, and
    calls after close. Dispatch hidden in unexamined code can escape detection.
  - No run observes commits yet, so an `AFTER_COMMIT` step that would be evaluated is a gap. A sidecar that repeats
    one signature with a varying cardinality is refused, since an interaction could belong to more than one step.
- **The comparator** reports `MISSING_STEP`, `UNEXPECTED_INTERACTION`, `ORDER_VIOLATION` and `COVERAGE_GAP`, each of
  which prevents scenario conformance, and `NOT_EVALUATED`, which is counted and does not: a step that touches a person, a
  simulated system or a planned module, or whose kind the run cannot observe. A run without a stimulus fails. Outcomes
  are asserted separately from the interactions.
- **Reports.** Each run writes `build/reports/scenarios/<view slug>.json`, schema `shipping-scenarios/report/v1`, and a
  Markdown version, before the test asserts anything. They name the revision and the SHA-256 digests of the model, the
  sidecar and the main classes, and hold no argument, return value or payload.
- **The claim** follows the verdict. `COVERAGE_INCOMPLETE`: recorded steps matched, but exact known gaps prevent
  scenario conformance; the report states "coverage incomplete: N known gaps" and lists outcomes separately.
  `CONFORMS` is available only without detected coverage gaps: in one exercised run, the evaluated steps, counted, conformed within
  the recorder's coverage, the others were neither checked nor assumed, and not all paths, schedules or inputs are
  covered. `NO_STEP_EVALUATED`, never `CONFORMS`, for a run that evaluates no step: no declared step was verified.
  `DOES_NOT_CONFORM`: interaction failures, unledgered gaps or ledger drift block the build, with their findings.
- **Temporary coverage-debt decision (option 5, 2026-11-08).** The build may accept an incomplete run only when
  its known refusals match `apps/shipping-api/src/test/resources/scenario-coverage-gaps.tsv` exactly, its remaining
  checks pass and its outcomes hold. This changes build acceptance, not the census, receiver walk or comparison.
  Every original `COVERAGE_GAP` remains failing in the raw comparison; `coverage.verified` stays false, and failed
  coverage checks remain visible. A separate `buildBlockers` list and `buildAcceptable` field describe build policy.
  The ledger stores each finding's target owner/member/JVM descriptor, origin signature/descriptor, access kind and
  line where available; factory findings include parameter index and type. Each row has its classification and
  "phase 3 / option 2". Matching uses the entire finding and multiplicity, never a package, owner, pattern or count.
  A new or changed finding, a removed ledger entry, or a disappeared finding with a stale ledger entry fails.
  Duplicate entries fail parsing; duplicate findings cannot collapse into one. Generation never updates this ledger.
  Ledger edits are risk level 3 control changes and require review; a green build is no coverage proof.
  Phase 3 / option 2 means actual production factory invocation witnesses plus coverage of the concrete Spring
  receiver/callback graph. No safe-framework classification or production-composition witness is claimed here.
- **Placement.** The scenario support is test code of `:apps:shipping-api` (package `scenarios`). Moving its model,
  comparator and reports into c4-contract-kit, free of Spring and under an entry point of their own, is later work.

### Consequences

- Good, because a removed, added or reordered interaction that keeps the outcome fails the build, which the static
  check and the outcome assertions both let through.
- Good, because a reader can see the diagram, its machine-readable steps and one run's result side by side.
- Bad, because each scenario view needs a sidecar kept in step with it. Validation finds the drift; a person writes
  the fix.
- Bad, because the model's views are read by a restricted text reader: the review of Structurizr covers only the
  kit's and the docs tool's parsers. The reader rejects unsupported syntax, including the tested comment forms;
  every run cross-checks its modules with the kit's parse. `ScenarioModelTest` compares the repository's dynamic views
  with Structurizr's generated pages. Its accepted/refused syntax fixtures use a fabricated kit report and assertions
  against the restricted reader alone; they are not independently compared with Structurizr.
- Limit: the recorder sees calls through `api` interfaces of Spring beans and events published through the context;
  the coverage contract refuses what it can find outside that, and what it cannot find is listed in `docs/gaps.md`,
  section 6.
- Limit: a passing run shows one schedule with one input. Concurrency, other inputs and failure paths need runs of
  their own.

### Confirmation

- `java` runs `./gradlew check`. In `:apps:shipping-api`, `QuoteScenarioTest` and `TenantDenialScenarioTest` run their
  scenarios, and `ScenarioFixturesTest` runs altered code: a removed call that keeps the outcome is a missing step, an
  added call along a permitted relationship an unexpected interaction, two swapped interactions an order violation,
  a disabled call observer and a disabled publication observer coverage gaps, never missing steps, an `AFTER_COMMIT`
  publication that runs before any commit a coverage gap, and a run without a stimulus fails; two runs that interleave
  on two threads pass. `CoverageBypassTest` runs seven implementations that reach another module unobserved (a
  captured `Supplier`, a nested reference, a static field, a registered singleton, a lookup after the recording, a
  test-only implementation, a value type with behaviour), each a coverage gap with three asserted outcomes. Two more
  fixtures hold virtual-thread and pre-existing-executor work until after close and are refused by the census while
  their three quote outcomes hold. `ReachabilityTest` checks a shared receiver and shared containers in both module
  traversal orders, including a container cycle. `SidecarValidationTest` and
  `ScenarioModelTest` reject sidecars and model text that do not match, `ScenarioModelTest` compares the reader with
  Structurizr for the repository's dynamic views, and `ScenarioReportTest` holds each verdict to its claim.
- `control.scenario-conformance` in `docs/capabilities/capabilities.yaml` records the status.

## Implementation status (2026-11-09)

What is built and what remains open under the temporary coverage-debt decision above.

- **In process, on the built modules.** The scenarios run in a Spring context that the tests wire, with the real
  classes of tenants, quotes and shipments and the in-memory stores. Their explicit configurations use
  `@TestConfiguration` so the separate HTTP tests do not discover scenario fixtures through application component
  scanning. The HTTP tests use the application's wiring; the scenarios call use cases directly. HTTP exchanges,
  database steps and listener executions are not observed by the scenario recorder yet, so those steps are not
  evaluated, and each report says why.
- **Two of five scenarios.** `ScenarioQuote` evaluates two of its six steps: `CurrentTenant.require()` once, then
  `QuoteIssued` published once. `ScenarioTenantDenial` evaluates two of its six, through the real booking use case:
  shipments' `CurrentTenant.require()`, then `QuoteOffers.findOffer`. Its outcomes (the booking is rejected as for an
  unknown quote, nothing is stored for Beta, Alpha's offer stays bookable) are asserted by the test, apart from the
  steps. First booking, booking replay and label are planned.
- **215 distinct known gaps remain in the accepted ledger.** The full-main census includes the HTTP and JDBC
  adapters even though the direct-use-case scenarios do not instantiate them. The ledger contains 115 HTTP/security
  Spring accesses, 94 JDBC accesses and six quotes/shipments factory parameters. `COVERAGE_INCOMPLETE` is the expected
  report verdict; the ledger reader accepts the JDBC debt classification described below. The factories are not witnessed
  by the scenario wiring.
  Classes in the ledger are fixed constants (13), HTTP assembly (52), authentication construction/state (4),
  credential erasure callback (1), filter superclass construction (1), security builders/callbacks (30), provider
  composition (1), environment dispatch (1), ambient security state (6), authentication/authority extraction (3),
  framework dispatch to application callbacks (3), foreign-service factory parameters (6), and JDBC
  statement construction and execution (94). These labels describe
  the missing proof, not exemptions. Ambient state, inherited credential callbacks and unknown implementations
  remain unproved; closing them is phase 3 / option 2.
- **Refused, not evaluated.** Evaluatable `AFTER_COMMIT` steps, task-capable dispatch found in the census, and repeated
  signatures with varying cardinalities are refused. Unexamined dispatch remains a limit (`docs/gaps.md`, section 6).

## More Information

- The scenarios, their steps and the report format: `docs/flows/quote-book-label.md`, "Executable scenarios".
- What a green scenario does not prove: `docs/gaps.md`, section 6.
- Related: ADR 0002 (the static check), ADR 0006 (events and listeners), ADR 0012 (the modules, carried forward from
  ADR 0003).

### Shipments HTTP coverage debt, issue #88 (historical)

The new adapter adds 29 exact entries to the existing ledger: 24 HTTP response/problem assembly calls, one media-type
constant, and four foreign-service factory parameters in `ShipmentsConfiguration` (the booking factory takes both
`CurrentTenant` and `QuoteOffers`). The original 77 entries remain unchanged. Including the issue #90 JDBC addition
below, all 200 distinct ledger findings remain coverage refusals under the existing phase 3 / option 2 decision;
no receiver, matcher or comparison rule is broadened. The negative fixture still requires exactly one additional
unledgered Spring call and pins the raw total at 201; these expectations do not override parsing or multiplicity.

### JDBC coverage debt, issue #90 (historical)

Owner acceptance: "accept #90 ledger 94 rows", recorded at `2026-11-09T08:36:06.479Z`.
Each JdbcClient call in the four adapters now occupies its own source line, with no behavior change. The fresh
census reports **94 occurrences and 94 distinct findings**. The ledger replaces the earlier 76-row addition with
those 94 exact rows, classified as `JDBC statement construction and execution`, with follow-up `phase 3 / option 2`.
At that acceptance, the original 106 rows remained byte-for-byte unchanged and the ledger contained 200 distinct findings. No duplicate
rows, patterns or wildcards are added.

The ledger reader's allowed classifications gain only `JDBC statement construction and execution`. The detector,
census, duplicate checks, occurrence-sensitive matching, blocking and bypass logic are unchanged. The previous
18 repeated occurrences in 16 duplicate groups shared source lines; formatting makes their locations distinct.
Every finding remains a coverage refusal, and the extra-Spring-access fixture still requires exactly one blocker
and 201 raw findings. Accepting these exact rows does not establish JDBC coverage.

### Attended demo coverage debt, issue #92

Owner acceptance: "accept #92 ledger 15 rows", recorded at `2026-11-09T11:43:10Z`.
The exact proposal adds eight security configuration/building and callback findings, five HTTP response/problem
assembly findings and two fixed HTTP/security constants. All use existing classifications and `phase 3 / option 2`.
The proposal's SHA-256 is `b8793ae217d3c990df505f9a948b4742b40f12d5bdd1116b40a8bcb2f1cc8820`.

The ledger now contains 215 distinct findings. Two existing security findings only moved source line and were
updated in place; their targets and classifications are unchanged. No detector, census, duplicate, matching,
blocking or bypass logic changed. The additional-access fixture still requires exactly one blocker and now
216 raw findings. Smoke tests exercise the HTTP runtime separately; these accepted gaps do not make the
scenario recorder observe HTTP, framework receivers or callback execution.
