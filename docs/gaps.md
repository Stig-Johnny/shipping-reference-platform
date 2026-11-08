---
title: "Known gaps and limits"
description: "What v0.1 does not do, what each control cannot prove, and which decisions are still unverified. Read this before trusting a green build."
status: accepted
updated: 2026-11-08
---

# Known gaps and limits

A green build means the checks listed in `README.md` passed. It does not mean the properties below hold. Each gap
says what is missing and, where one exists, what partly compensates. Removing an entry requires the evidence that
closes it.

## 0. State of this commit

- The specification, the reviewed toolchain and the dependency allowlist with its enforcement are in place, and so
  are the reviewed dependencies of the docs generator and of the shipping API. The docs generator itself,
  `tools/docs`, exists: it generates the C4 view pages, `BRAIN.md` and the module brains, and builds the
  documentation site, which `.github/workflows/pages.yml` publishes from `main` after the publication scan. Its own
  controls, `control.brain-check` and `control.publication-scan`, are shipped with their tests as evidence. The
  library `libs/c4-contract-kit` (its slices 1 to 4) exists, and its controls carry their tests as evidence. The first
  application code exists, checked by `ArchitectureConformanceTest`, `ModularityTest` and `HexagonalLayeringTest`: the
  `quotes` module with its two HTTP operations, booking in the `shipments` module, in its domain and application layers,
  the `tenants` module's demo authentication with `GET /api/v1/me`, and the HTTP edge. Nine capabilities have passing
  tests: two of the quotes module are shipped, and three of it, two of the tenants module and two of the shipments
  module are partial, and so is the HTTP contract control. Every other capability in
  `docs/capabilities/capabilities.yaml` is `planned` with no evidence, and every test class named for them is still to
  be written (red first), except `ShipmentTest`, which exists for booking and does not yet assert cancellation.
- **The application starts in its tests only, without a database.** The HTTP tests start `ShippingApplication` with
  test doubles for the two ports that have no adapter yet: the tenants module's membership directory, which holds the
  synthetic users of `docs/modules/tenants.md`, and the quotes module's store, in memory. They leave out the data
  source and Spring Modulith's event publication registry, which need PostgreSQL. So they prove the HTTP edge, the
  problems, and that the tenant comes from the membership only. They do not prove that a token is resolved through
  `tenants.resolve_demo_token`, that a quote is stored in PostgreSQL, or that the database hides another tenant's
  quote. No transaction manager exists, so the `@Transactional` of `CreateQuote` and `GetQuote` has no effect, and
  that `QuoteIssued` is published inside the quote's transaction is unproven until `QuotesModuleIntegrationTest`
  exists. Outside its tests the application does not start yet: its ports have no production adapters, and the
  `demo` profile has no database configuration.
  The scenario checks wire the quotes and tenants classes as beans of a plain Spring context of their own,
  which is not the application's wiring (section 6).
- **The shipments module runs in its tests only.** No class of it is a Spring bean, and no endpoint, database or
  running application exists. `BookShipment`'s `@Transactional` has no effect yet, and that `ShipmentBooked` is
  published inside the booking's transaction is unproven until `ShipmentEventsIntegrationTest` exists. Requests with
  the same `Idempotency-Key` are not serialized yet: the advisory lock, the recheck under it and the handling of
  unique-key failures belong to the planned JDBC adapter, and `BookingIdempotencyIntegrationTest`, which is to show
  that concurrent duplicates create one shipment, is planned. The stores and the tracking numbers its tests use are in
  memory. The scenario checks wire `BookShipment` as a bean of their own plain Spring context, not the application's
  wiring (section 6).
- The versions in the ADRs were checked against their sources on 2026-11-06. Their combination (JDK 25, Gradle
  9.8.2, Spring Boot 4.1.2, Spring Modulith 2.1.2, Error Prone 2.51.0, NullAway 0.14.3, JUnit 6.0.3 (property
  tests as `@ParameterizedTest` in the normal test task), PIT 1.30.2 with its JUnit 5 plugin 1.2.3) now compiles the
  API and runs its tests, and Spring Modulith 2.1.2's verification passes on ArchUnit 1.5.1; the application context
  starts in the HTTP tests, without a database, while the scenario checks start a separate plain Spring context.
  In particular,
  PIT's JUnit 5 plugin with JUnit 6 is unverified; the fallback is running PIT against a JUnit 5.14.4 test task.
  The library runs PIT with JUnit 5.14.4, and that combination works.

## 1. What the architecture check cannot prove

`c4-contract-kit` compares direct static references in compiled classes with the model.

- **A REQUIRED edge proves a reference, not behaviour.** A listener that references `ShipmentBooked` but never
  writes an audit entry passes. So does an unused class that happens to reference the required API. Integration
  tests (`ShipmentAuditIntegrationTest`) are to carry the behavioural claim. A demonstration of this gap with a
  deliberately broken listener is planned as the library's slice 10 (`libs/c4-contract-kit/AGENTS.md`); it does not
  exist yet.
- **"No static evidence" is not "unused".** Reflection, Spring wiring by type or name, serialisation, configuration
  properties and string-built class names create runtime use that bytecode analysis does not see.
- **Invisible channels.** SQL that reads another module's schema, shared database rows, HTTP calls, message
  payloads, files and environment variables are not dependencies to the checker. `TableOwnershipTest`, planned, is to
  check the SQL of each module's adapters against the module's schema (ADR 0012); SQL anywhere else stays invisible
  to it.
- **Ordering and timing.** The model says who may depend on whom, not in which order things happen or whether they
  happen in the same transaction. The dynamic view and `docs/flows/quote-book-label.md` describe order; tests
  assert it. The scenario checks compare the order of one run's module API calls and publications with a scenario
  view, in process only (section 6).
- **The use case overview is not compared with what runs.** The dynamic view and the site's use case overview (sheet
  M-07) show who takes part in what, as the model and `docs/capabilities/capabilities.yaml` declare it. A use case's
  stamp there says that its named acceptance tests passed in the build that made the site, not that an actor started
  it or that its interactions happened as drawn. Only the scenario views (sheets M-08 and M-09) are compared with a
  run, and only for the steps that their reports count as evaluated (section 6).
- **Only direct references.** A transitive path neither satisfies nor violates an edge. Two modules can be coupled
  through a third without any report.
- **Only what the class file records, as ArchUnit 1.5.1 reads it.** javac copies compile-time constants
  (`static final` primitives and strings) into the class that uses them, so using another module's constant leaves
  no reference. Casts, the types of local variables, explicit type arguments of method calls and array creation
  (`new T[n]`) are not reported either. A module that uses another only in these ways shows no divergence, and such
  a use does not satisfy a REQUIRED edge. `BytecodeEvidenceTest` pins this list. Type patterns in a `switch`
  (`case T t`, `case T _`) compile to an `invokedynamic` type switch whose bootstrap arguments hold the classes; they
  are not reported either, and `BytecodeEvidenceTest` does not pin that yet (issue #69).
- **Granularity.** Mapping is by package root. A class placed in the wrong module's package is attributed to that
  module. Encapsulation inside a module (which internal class may call which) is Spring Modulith's and ArchUnit's
  job, and both see the same static references only.
- **Planned components are not evaluated.** Their edges are reported as "not evaluated". Code appearing in a planned
  package fails the build, but the planned edges themselves carry no meaning until the module is built.
- **The model can be weakened.** Adding a relationship or broadening a package mapping makes a failing build pass.
  The checker does not understand intent. The compensating control is to be the owner's approval: `architecture/` is
  level 3 and owned by the owner. That approval is not enforced yet (section 5), and the demonstration of a weakening
  pull request waiting for it, the library's slice 8, is planned.
- **The web app is outside the check.** The C4 relationships from the web app to the API are network calls. Java
  static analysis does not see them; the contract tests and Playwright tests do, for the operations they exercise.
- **Library self-model.** The library's internal structure is governed by `libs/c4-contract-kit/architecture/self.dsl`,
  not by `architecture/workspace.dsl`; the two are checked separately.

## 2. Security and identity

- **Demo authentication only.** Tokens are public, guessable fixtures (`docs/modules/tenants.md`). No expiry,
  rotation, revocation, multi-factor authentication, rate limiting or lockout. Local runs use plain HTTP. Production
  identity (for example OpenID Connect through Spring Security's resource server) is not designed yet.
- **One membership per user, one role.** No tenant switching, no administrator role, no admin screens.
- **Row-level security depends on discipline at two points.** Every tenant-owned table needs its policy (caught by
  `RowLevelSecurityCatalogTest`), and every connection must go through `TenantBindingTransactionManager`. Code that
  obtains a raw connection outside a Spring transaction sees no rows, which fails safe but may surprise.
- **Identity tables are exempt from RLS.** They are protected by privileges and a security-definer function instead.
- **The event publication table is not tenant-scoped.** It stores event payloads, which contain identifiers only.
- **One runtime role for all modules.** The database does not stop one module's SQL from reading another module's
  schema.
- **Owner role and superusers bypass the application's guarantees** when used directly. Only Flyway uses the owner
  role.
- **Tracking numbers come from one global sequence.** A tenant can infer the platform's total booking volume.
- **Labels are SVG.** They are served as attachments with a restrictive Content Security Policy and contain no
  active content, but SVG remains a document format that some viewers treat as active.

## 3. Personal data

- Recipient names and addresses are personal data even though v0.1 uses synthetic values only. There is no
  retention period, no erasure, no export, no column encryption and no data-processing record.
- "Never log recipient data" is a rule in `apps/shipping-api/AGENTS.md`. Not enforced yet: no automated log scan.

## 4. Domain simplifications

- One simulated carrier, one currency (EUR), six served countries, no postcode validation per country, no
  remote-area surcharges, no dimensional limits beyond 1200 mm per side, no multi-parcel shipments.
- Labels are not carrier labels and the bar pattern is not a barcode.
- Idempotency keys are kept forever; real systems expire them.
- Quotes are never deleted.
- Shipment states beyond `BOOKED` and `CANCELLED` wait for the planned tracking module.

## 5. Supply chain and approvals

- **The download mirror is disabled.** JDK staging has bounded retries, size/SHA-256 checks on every successful
  retrieval and a tested mirror-first path, but its committed mirror URL is empty. Gradle retains its official
  wrapper URL and checksum with longer timeouts and retries. Local shell fixtures substitute transport and sleep;
  they prove branching, retry budgets and integrity refusal, not live DNS/TLS, throughput or outage recovery.
  Enabling a new source still needs review amendments and renewed owner acceptance, infrastructure and publication
  evidence. A staged Gradle launcher and repeated offline signature checks are not built; the existing first-use
  signature record remains unchanged. Issue #82 tracks the later rollout. These fixtures run separately in CI and
  produce no JUnit evidence, so they do not change capability status.
- **Pre-use enforcement covers the sanctioned CI path only.** It controls declared dependencies on that path. It
  cannot stop arbitrary build code downloading executables, compromised approved artifacts, runner compromise or
  local-agent bypass. Those need stronger execution isolation and egress controls.
- **Hashes establish identity, not safety.** A reviewed, allowlisted artifact can still be malicious; reviews reduce
  that risk and record who accepted it.
- **AI reviews can be wrong.** Package text is untrusted input to the reviewing agent. The owner accepts each
  review; that acceptance is a judgement, not a proof.
- **One accountable human.** Level 3 changes need the owner's approval. This is accountable human oversight, not
  independent two-person review. Branch protection applies to administrators too, so nobody merges past a red
  check, but an administrator can change the protection itself (through OpenTofu in the infrastructure repository).
- **GitHub enforces the required checks, not code owner review.** Branch protection on `main` requires a pull
  request, the status checks `preflight` and `java` (strict: up to date with `main`), and resolved conversations,
  for administrators too. It dismisses stale reviews and forbids force pushes and deletion. A red check blocks the
  merge. It requires no approval and no code owner review. Code owner review cannot be required: the only code owner
  authors the pull requests through AI agents on the owner's account, and GitHub refuses self-approval. It needs the
  agents to open pull requests under their own identity. The branch ruleset in `policy/rulesets.json`, the
  `risk-approval` and `ai-review` checks and the admin workflow that reports drift (ADR 0008) are planned, so level 3
  approval is not enforced yet.
- **The acceptance digest proves consistency, not who accepted.** `acceptance.reviewedSetSha256` in
  `security/allowlist.json` proves that the reviewed set did not change after acceptance. It does not prove that a
  human accepted it: the owner and the AI agents use the same GitHub account, and the agent that records the
  acceptance also computes the digest. The record quotes the owner's words and their timestamp from the chat
  transcript.
- **Scope is recorded, and enforced only for the classes the API uses.** The allowlist check matches coordinate,
  version and checksum. Which projects and configurations a review approved is a condition the code owner checks in
  review: a reviewed library added to a project its review does not cover passes `preflight`. `ExternalImportPolicyTest`
  checks the scope of an artifact only when a class of the API uses one of its classes (next entry). On 2026-11-07,
  23 artifacts across 26 configuration pairs on the API's classpaths had no entry for `:apps:shipping-api` and the configuration they are on, and
  nothing failed: among them ArchUnit 1.5.1 on `runtimeClasspath`, which Spring Modulith brings and its review
  approves for the API's test classpaths only, and the contract kit's own dependencies on `testRuntimeClasspath`,
  approved for `:libs:c4-contract-kit`. Enforcing scope for every locked artifact first needs the entries' scope
  migrated to one machine-readable form, with the owner's acceptance: today they name the API three ways
  (`:apps:shipping-api`, `apps/shipping-api` and the first design's `:example-app`) and write configurations with
  and without a project prefix.
- **The external-import check covers dependencies reported by ArchUnit 1.5.1 only.** `ExternalImportPolicyTest`
  reads the API's main and test outputs. Internal ownership comes from the actual classes in those outputs, not a
  package prefix; an artifact colliding with any of them fails even without a use. External targets are classified
  through the provider index or the JDK's module descriptors and checked against the caller's layer whitelist.
  The isolated root HTTP classes and the quotes and tenants web adapters have exact class grants; the shared
  `io.github.stigjohnny.shipping.http` test package has its own exact-class whitelist, separate from module tests.
  The exact `scenarios` and `scenarios.support` test packages have a separate exact-class whitelist. A second
  scenario area covers only the named test fixtures `ScenarioAlterations`, `GatedQuoteStore`, `TenantBadge` and
  `ScenarioReference`, including their nested classes; they retain module packages for the recorder's attribution.
  These grants do not apply to main output or other module tests. They permit the harness to inspect and exercise
  deliberately refused behavior; they do not close any of the 77 scenario coverage gaps in section 6.
  No package grants cover these additions, and every whitelist entry must be used by the current compiled outputs.
  The JDBC adapter whitelist stays empty until an adapter exists.
  Maven providers need unexpired approval for the project, configuration and exact file, with SHA-256 comparison
  where the review records a hash. The first-party exemption from Maven review requires root-build identity and an
  exact registered repository-owned output (currently the contract kit); other project providers are rejected.
  Included-build identity is retained. The check treats `:example-app` as the API only while the current acceptance
  is `accepted` and the exact PR #10 `projectPathConfirmed` text remains in that record or `acceptance.previous`.
  History is read newest first in its stored order. Historical snapshots may omit `status`, as PR #73's rollover
  does; an explicit status must be `accepted`. A newer changed or negated confirmation, or non-accepted status,
  stops the search before any older affirmation; missing confirmation fails too. This reads the preserved owner
  record without granting new scope. It does not see:
  - compiler-erased references such as inlined constants, or retained bytecode that ArchUnit 1.5.1 does not report.
    Casts, local variable types, explicit type arguments and array creation are not reported
    (`BytecodeEvidenceTest` pins these), nor are retained type-use annotations such as JSpecify's `@Nullable`
    (`ExternalImportPolicyTest` pins this). A source-level pass with the JDK's compiler is planned
    (`ExternalImportSourceTest`);
  - use at run time: reflection, `ServiceLoader`, Spring's wiring and auto-configuration, class names in
    configuration, native libraries. The whitelists name what the API's code refers to, not what its frameworks load;
  - the classes that run: a main class is checked against `compileClasspath` and a test class against
    `testCompileClasspath`, and the artifact that provides a class at run time can differ (jackson-annotations is
    2.21 when the tests compile and 2.22 when they run, and only the kit's entry approves 2.22);
  - the code of the contract kit, the docs tool, the policy checker and the build logic;
  - review conditions below the class: a whitelist names classes and packages, not methods, so a condition such as
    ArchUnit's ban on `importClasspath` or AssertJ's on `isXmlEqualTo` is still the reviewer's to check;
  - which JDK runs: the reports name the JDK reviews of the running version, and another version fails nothing.
- **A vendored file is checked by hash only.** The check proves the file is the reviewed one. How a page may load it
  (Mermaid's `strict` mode, the Content Security Policy, the diagram validator) is a review condition that the docs
  generator's tests enforce, not the allowlist check.
- **Risk signals are patterns.** Behaviour signals in `policy/risk.json` match paths, symbols and SQL keywords.
  Unknown paths escalate to level 3; an ordinary-looking change with a sensitive meaning can still be classified too
  low. The AI reviewer may raise the level; nothing may lower it.
- **The review-condition tests match calls by name.** `ReviewConditionsTest` finds a forbidden method or constructor
  (`reflect()`, a class-path import, a second parser factory, `System.getenv`, `Class.forName` and the others) by its
  owner and name, in a call or a method or constructor reference. A call through `java.lang.invoke`, a method handle
  looked up by name, is outside those rules; only `java.lang.reflect` is banned outright. No production class uses
  `java.lang.invoke` today, and `policy/risk.json` raises an added `MethodHandles.` in production code to level 3.
- **The model check reads what the workspace keeps.** Before parsing it rejects `${`, constants, variables and line
  continuations anywhere in the file; after parsing it reads every text the parsed workspace holds. A name that
  parsing assembles for an empty group (for example with a `$` group separator) stays in the parser's own table:
  no element carries it, so it is not checked and cannot reach a report.

- **Dependabot sees the workflows' Actions, not the Gradle dependencies.** GitHub's dependency graph lists only what
  it can read without running the build: the pinned Actions. Gradle dependencies would need a dependency-submission
  step, which is not set up, because an automatic submission build would run the build outside the reviewed
  toolchain. So Dependabot alerts cover the Actions only. The Gradle dependencies are covered by the reviews recorded
  in the allowlist, each of which checked the known advisories when it was written; nothing re-checks them
  continuously yet.

## 6. Tests and quality gates

- Contract conformance covers only the responses that tests exercise. Not enforced yet: a check that every
  documented operation and status code is exercised, and OpenAPI style linting.
- The HTTP tests run on MockMvc, with the servlet filters that the application registers, in their order. No servlet
  container starts, so the container's own request parsing and error pages are not exercised.
- Mutation testing runs for `libs/c4-contract-kit` only, in CI (the `mutation` job), over all its production classes,
  at a 90 % threshold. The planned measure per change (80 % of mutants on changed lines, 90 % for pricing and checker
  code) is not built yet, and equivalent-mutant exclusions are reviewed by hand. So the pricing code of the `quotes`
  module and the booking code of the `shipments` module are not mutation-tested yet: PIT is not reviewed for
  `:apps:shipping-api`.
- `ModularityTest` sees the modules as Spring Modulith finds them, from the class path without the test classes;
  `ArchitectureConformanceTest` and `HexagonalLayeringTest` read the API's main class-output directories only.
- Mockito is on the API's test class path, and Spring's test framework initialises it in every Spring test, even
  though no test uses it. Every test task of the API therefore loads it as an explicit `-javaagent`, as its review
  requires; without that, the first HTTP test attached the agent to the running JVM itself.
- PIT runs only on disposable CI workers. Its parent JVM listens on all network interfaces and accepts its workers'
  first connection without authentication, so its review forbids developer machines whose firewall lets inbound
  connections reach `java`. On 2026-11-06 the library's development ran PIT on such a machine (firewall off) before
  that condition was checked; the exposure was one listening socket per run, for the length of the run.
- axe-core finds a subset of accessibility problems; manual keyboard and screen reader checks are required and
  recorded by hand.
- The planned web conformance gate uses a **partial static lint** for import boundaries and cycles, and a direct
  detected import for each REQUIRED relationship whose endpoints are built (ADR 0014). Relationships touching
  planned parts are not evaluated, a direct import proves a reference and not behaviour, and a part that no REQUIRED
  relationship reaches can be unused without a report. dependency-cruiser is deferred.
- **The web source reader is a partial static lint, not authoritative enforcement.** The owner accepted this scope
  on 2026-11-08 (ADR 0014). It is implemented and tested on synthetic fixtures; the web conformance gate is still
  planned on this reader-only slice. It can miss imports and unsupported constructs without a refusal. Its grammar
  is deliberately restricted; the following are recognized forms covered by fixtures, not universal guarantees:
  ambiguous slashes after closing type arguments, loader references (including quoted keys, aliases and indirect
  calls), escaped property keys, non-literal computed access/binding keys (including templates and const-derived
  keys), duplicate HTML attributes, encoded URL/type
  attributes, and package CSS subpaths are refused. Timers require a direct call with a visible function literal.
  Numeric literals preserve following property names; malformed numeric continuations, misplaced separators and
  legacy leading-zero forms are refused. Regex flags accept only distinct letters from `dgimsuy`: unknown, escaped,
  duplicate and Unicode-set (`v`) flags are refused. This rejects some valid syntax intentionally.
  Every scanned URL attribute is validated before origin classification against a conservative ASCII subset:
  controls, spaces, backslashes, malformed escapes and uncertain schemes/authorities are refused. This is no full
  TypeScript or WHATWG URL parser and no proof about reflective calls or general runtime behaviour. Conservative
  refusals reject harmless names, computed indexing, callback aliases and valid URLs outside that subset too. The
  reader does not execute a web app or inspect installed packages.
- **Known open web-reader bypass: spread syntax.** The review of pull request #70 found that
  `const p = { ...import("unlisted") };`, `const p = { ...import(name) };`,
  `const p = { ...require("unlisted") };` and `const p = { ...import.meta.glob("./*.ts") };` produce
  `imports=[], refusals=[]`. Object, array, call and nested-template spreads reproduce this, including whitespace,
  comments and line breaks after `...`, in TS/TSX and source/test placements. The lexer splits `...` into dots and
  the scanner treats the next keyword as a property name. Parenthesizing the operand restores detection in the
  reviewed control; that does not close the bypass. The review reported the numeric-token defect fixed and no other
  open bypass class in its probe set; that set was not exhaustive. A green lint result cannot prove the absence of
  undeclared imports or hidden runtime loaders.
- **Authoritative web controls are planned, not delivered by the reader.** The reviewed TypeScript compiler's
  import graph is to supply import dependencies, and a strict Content-Security-Policy in the web app is to be
  enforced by the browser for runtime loading and execution (ADR 0014). The compiler graph is not a proof of general
  runtime behaviour, and CSP does not enforce module boundaries. Neither control is implemented in this slice;
  reader fixtures are not evidence of either one.
- The first runnable slice defers the generated client and the component tests (ADR 0012). So there is no
  generated-client drift guarantee: the web app's request and response types are written by hand in
  `apps/shipping-web/src/api` and can drift from `contracts/shipping.openapi.yaml` without failing a build. The
  server's HTTP contract tests and the Playwright tests catch drift only for the operations and fields they exercise.
  The component tests (`ParcelForm.test.tsx`, `QuoteCardList.test.tsx`, `BookingForm.test.tsx`,
  `ShipmentDrawer.test.tsx`, `ProblemAlert.test.tsx`, `ThemeToggle.test.tsx`) wait for Vitest, Testing Library and
  jsdom. They stay acceptance targets, so the capabilities that name them stay unshipped; until they exist, only
  logic tests under `node --test` and the Playwright specs test the web app.
- The CI time target of 8 to 12 minutes is a target, not a measurement.
- **The scenario checks prove one run, within what the recorder sees** (`control.scenario-conformance`, partial). A
  run without detected gaps can show its evaluated steps conformed for one exercised run, not every path, schedule
  or input. Today's two reports instead say `COVERAGE_INCOMPLETE`, "coverage incomplete: 77 known gaps".
  Four of their twelve steps are evaluated. They run in process on the built modules with test wiring; the HTTP
  tests separately use the application's wiring: HTTP exchanges, database steps and listener executions are not
  observed yet, and steps that touch a person, a simulated system or a planned module are never evaluated; the report
  counts each one with its reason. Outcomes, such as the tenant denial's rejection and absence of stored data, are
  test assertions, not verified steps.
  - **Known coverage debt, still refused:** 75 Spring accesses in the HTTP adapters and two foreign-service
    factory parameters remain `COVERAGE_GAP`, with `coverage.verified=false`. The source-controlled exact ledger
    `apps/shipping-api/src/test/resources/scenario-coverage-gaps.tsv` records owner, member/JVM descriptor,
    origin signature/descriptor, access kind and source line where available, plus classification and
    "phase 3 / option 2". Only exact matches may pass the build as incomplete. New/changed findings, removed
    entries, duplicate findings and disappeared findings without a ledger update fail; generation never updates it.
    This is an explicit change in build acceptance, not proof that framework calls are safe. The ledger's classes
    and counts are in ADR 0013. Production factory invocations, ambient security state, inherited credential-erasure
    callbacks, interface implementations and builder callback graphs remain unproved until phase 3 / option 2.
  - **Refused, not passed** (each is a coverage gap or a refused sidecar): an `AFTER_COMMIT` step that would be
    evaluated, since no run observes commits; task-capable APIs found by the census (thread creation/builders,
    executors, futures, timers and explicit parallel dispatch), since tasks are not tracked to completion; a recorded
    interaction on a thread other than the stimulus's, a new non-system platform thread present at the final snapshot,
    and a late interaction observed by close; a sidecar that repeats one signature with a varying cardinality; a raw
    receiver found by the walk; reflection,
    method handles and Spring lookups from module code; an `api` method that takes or returns anything but values; a
    listener method; a module object registered as a ready singleton; a walk beyond 200,000 object/owning-module pairs; a model
    line where a comment opens or closes beside other text.
  - **Still unsound, without a gap:** the walk checks receivers only through what is reachable from module beans and
    from static fields before and after the run, so a reference obtained and dropped during the run is seen only
    through the census of the code that used it. The census reads all main module classes and the dependency closure
    of reached test module classes, not arbitrary external helpers or test code outside that closure. References
    that the test passes into a stimulus are not walked; async dispatch hidden in unexamined code can escape the
    dispatch checks. The thread snapshot excludes virtual threads and does not track tasks on pre-existing executors;
    close checks late calls only once, so work released later cannot change a completed verdict. The virtual-thread
    and pre-existing-executor fixtures are refused by their examined dispatch sites before close, not by those
    snapshots. Calls are told apart by interface and method name, not by overload; the caller is the
    first application frame on the stack, mapped by `contract.packages`; whether a type holds values only is judged
    from its declared field types.
  - **The model reader** is a restricted text reader, since Structurizr's review covers only the kit's and the docs
    tool's parsers. It refuses what it does not read exactly; `ScenarioModelTest` compares its reading of the
    repository's dynamic views with Structurizr's generated pages. Accepted/refused syntax fixtures use a fabricated
    kit report and assert against `ScenarioModel` alone; they have no independent Structurizr oracle. This is not
    equivalence for every possible DSL text.
  - A report's revision is the checked-out commit. Its digests cover the model, sidecar and main classes, not test
    support or wiring; uncommitted changes to the latter are not identified by report provenance.

## 7. Documentation

- The brain is a budgeted summary (12 KiB root, 8 KiB per module) and omits detail by design; it links to sources.
- Frontmatter-linked documentation proves that a document changed with the code, not that it is correct. Not
  enforced yet: the check that a pull request changing a module's code also changes its documents.
- Evidence is read from the JUnit reports of the same build, so only Java test classes can count as passing. A
  TypeScript test name stays an acceptance target until the web build writes reports that the docs tool reads.
- The module brains list the entries of this file that name a module in code, by its document or as "the X module".
  An entry that concerns a module without naming it is not listed there, so this whole file still applies.
- The documentation site and the repository are both public (the repository since 2026-11-07). The site still does
  not link into the repository: paths of files it does not publish, such as source code, workflows and review
  records, appear as plain text, because its publication scan still treats a link to the repository as a finding.
  Enabling those links is a separate, reviewed change to the scan.
- The publication scan matches shapes and listed terms. It cannot catch a paraphrase, a confidential name or detail
  that is not in its dictionary, or what a sentence means; nor a secret whose shape it does not know. A person
  still reads every public page that changes. The dictionary lives outside the repository, so whether it is
  complete is not checked here.
- **A demonstration's recorded output is not rerun.** The docs tool checks a demonstration's form: its metadata, its
  sections, its commands against the approved list, that it is text only, and that See it opens pages of the site.
  It never runs a command a demonstration shows and never compares recorded output with anything, so recorded output
  can be stale, edited or wrong; only a reviewer can tell. Only the named tests that passed in the build that made the
  site count as its evidence. That `recorded` never changes after the first recording is a rule, not a check: the
  tool does not read history. Whether a pull request that needs a demonstration has one is a rule for agents
  (`AGENTS.md`); no check requires it yet. The same holds for a milestone deck: the tool checks that its recorded
  output is labelled and that its notes name the date, revision, command and exit status, not that they are true.
- **Media waits for a gate.** Screenshots, recordings and other media are not published: until a reviewed media gate
  exists, demonstrations are text only, and the screenshots of a visible change go to the owner with the review
  request, outside the repository.

## 8. Not in scope for v0.1

Real carriers, webshop connectors, tracking and returns workflows, invoicing, production identity and deployment,
backups, observability beyond logs with correlation IDs, customer code execution and broader frontend architecture
enforcement.
