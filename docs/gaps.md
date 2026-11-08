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
  library `libs/c4-contract-kit` (its slices 1 to 4) exists, and its controls carry their tests as evidence. So does
  its web import check, which holds `apps/shipping-web` to the web container of the model; every web part is
  planned, so it evaluates no import of the web app yet. The first application code exists: the domain and
  application layers of the `quotes` module and of booking in the `shipments` module, and the tenant context of the
  `tenants` module, checked by `ArchitectureConformanceTest`, `ModularityTest` and `HexagonalLayeringTest`. Six
  capabilities have passing tests: two of the quotes module are shipped, and two of it and two of the shipments
  module are partial. Every other capability in `docs/capabilities/capabilities.yaml` is `planned` with no evidence,
  and every test class named for them is still to be written (red first), except `ShipmentTest`, which exists for
  booking and does not yet assert cancellation.
- **The quotes module runs in its tests only.** No class of it is a Spring bean, and no endpoint, database or running
  application exists. `CreateQuote`'s `@Transactional` has no effect yet, and that `QuoteIssued` is published inside
  the quote's transaction is unproven until `QuotesModuleIntegrationTest` exists. The store its tests use is in
  memory, so nothing yet shows that another tenant's quote is not found in the database.
- **The shipments module runs in its tests only.** No class of it is a Spring bean, and no endpoint, database or
  running application exists. `BookShipment`'s `@Transactional` has no effect yet, and that `ShipmentBooked` is
  published inside the booking's transaction is unproven until `ShipmentEventsIntegrationTest` exists. Requests with
  the same `Idempotency-Key` are not serialized yet: the advisory lock, the recheck under it and the handling of
  unique-key failures belong to the planned JDBC adapter, and `BookingIdempotencyIntegrationTest`, which is to show
  that concurrent duplicates create one shipment, is planned. The stores and the tracking numbers its tests use are in
  memory.
- The versions in the ADRs were checked against their sources on 2026-11-06. Their combination (JDK 25, Gradle
  9.8.2, Spring Boot 4.1.2, Spring Modulith 2.1.2, Error Prone 2.51.0, NullAway 0.14.3, JUnit 6.0.3 (property
  tests as `@ParameterizedTest` in the normal test task), PIT 1.30.2 with its JUnit 5 plugin 1.2.3) now compiles the
  API and runs its tests, and Spring Modulith 2.1.2's verification passes on ArchUnit 1.5.1; no Spring application
  context has been started yet. In particular,
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
  assert it.
- **Scenario diagrams are not compared with what runs.** The dynamic view and the site's use case overview (sheet
  M-07) show who takes part in what, as the model and `docs/capabilities/capabilities.yaml` declare it. A use case's
  stamp there says that its named acceptance tests passed in the build that made the site, not that an actor started
  it or that its interactions happened as drawn. The next increment is to compare the interactions that a scenario
  executes with its dynamic view, step by step.
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
- **The web app's calls are outside the check.** The C4 relationships from the web app to the API are network calls.
  No static check sees them; the contract tests and Playwright tests do, for the operations they exercise.
- **The web import check reads text, not types.** It holds the web app's imports to the model's web container with a
  restricted import grammar, never with the TypeScript compiler, and fails closed: what it cannot read with certainty
  fails the build with its file and line. These limits follow:
  - It reads `package.json`, `tsconfig*.json` and the app's `*.html` pages as data and refuses every setting that
    would make TypeScript, Node.js or the browser resolve a specifier otherwise (path aliases, `baseUrl`, `rootDirs`,
    an import map, and more). It reads no other configuration: a bundler or test-runner configuration that resolved
    modules differently would not be seen. ADR 0012 has no bundler; `vite.config.ts` and `vitest.config.ts` are risk
    level 2 in `policy/risk.json` (`web-build-configuration`), which does not require the owner's approval.
  - Tests are read and held to their part's relationships and entries, and every package and built-in they name must
    be declared and approved, but their imports are never evidence. What a test does at run time with a built-in it
    may import (`node:fs` reads files) is not checked.
  - A package or built-in is approved when an entry of `security/allowlist.json` matches its kind, coordinate, exact
    version, project and configuration with an approving verdict. The check leaves expiry to `PolicyCheck`, and an
    approved package's own dependencies to the allowlist and lock checks; it sees only the imports in the app's files.
    No part declares a package today.
  - `url()` in style sheets and assets that code names in strings at run time are not dependencies to it.
  - It refuses valid TypeScript it does not cover, such as a slash it cannot classify as division or a regular
    expression, a generic arrow function starting with `<` in a `.tsx` file, `require`, `import.meta.glob` and
    triple-slash directives. A refusal fails the build; it never passes unseen. A misreading the lexer does not know
    about could still hide an import.
  - Calls of globals, such as `fetch` outside `src/api`, are not imports; review enforces that rule
    (`apps/shipping-web/AGENTS.md`).
  - As in the class check, a REQUIRED web relationship proves an import, not that the imported code runs.
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
- **Scope is recorded, not enforced.** The allowlist check matches coordinate, version and checksum. Which projects
  and configurations a review approved is a condition the code owner checks in review: a reviewed library added to a
  project its review does not cover passes CI.
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
- Mutation testing runs for `libs/c4-contract-kit` only, in CI (the `mutation` job), over all its production classes,
  at a 90 % threshold. The planned measure per change (80 % of mutants on changed lines, 90 % for pricing and checker
  code) is not built yet, and equivalent-mutant exclusions are reviewed by hand. So the pricing code of the `quotes`
  module and the booking code of the `shipments` module are not mutation-tested yet: PIT is not reviewed for
  `:apps:shipping-api`.
- `ModularityTest` sees the modules as Spring Modulith finds them, from the class path without the test classes;
  `ArchitectureConformanceTest` and `HexagonalLayeringTest` read the API's main class-output directories only.
- Mockito is on the API's test class path, but no test uses it, so nothing loads it or attaches an agent. The
  `-javaagent` wiring that its review requires for every test task arrives with the first test that uses it.
- PIT runs only on disposable CI workers. Its parent JVM listens on all network interfaces and accepts its workers'
  first connection without authentication, so its review forbids developer machines whose firewall lets inbound
  connections reach `java`. On 2026-11-06 the library's development ran PIT on such a machine (firewall off) before
  that condition was checked; the exposure was one listening socket per run, for the length of the run.
- axe-core finds a subset of accessibility problems; manual keyboard and screen reader checks are required and
  recorded by hand.
- The web app's repository import gate is implemented: `WebArchitectureConformanceTest` holds `src`, the `e2e`
  test root, `public` and app configuration to the model in every build. It covers import boundaries and cycles,
  and requires a direct import for each REQUIRED relationship whose endpoints are built (ADR 0012; limits in
  section 1). All six web parts remain planned, the source and test roots contain no real files, and every
  relationship touches a planned part, so no real web imports are checked and no relationship is evaluated yet.
  The control remains partial. A direct import proves a reference, not behaviour, and a part that no REQUIRED
  relationship reaches can be unused without a report. dependency-cruiser is deferred.
- The reader and conformance library are implemented, and the import rules have synthetic fixture evidence.
  `WebArchitectureConformanceTest` proves that an unapproved import in the repository model's `e2e` root fails the
  gate. `WebConformanceTest` proves that reader refusals fail the model comparison, and that Node.js built-ins in
  tests need runtime approval for project `ci` in configuration `web`, at the version named by `engines.node`.
  `WebReaderRefusalConformanceTest` checks the quoted/escaped loader keys, computed keys, indirect timers and ambiguous
  script/link URLs through the same public gate, asserting the failing verdict and exact findings in browser sources
  and both kinds of test root. It also checks all 66 numeric-boundary
  and regex-flag refusal fixtures independently in six TS/TSX source/test placements, asserting the failing verdict
  and exact file, line and reason, including multiple findings on one line and findings after continued strings.
  This is fixture evidence, not a check of real web imports. The reader's grammar is deliberately restricted:
  ambiguous slashes after closing type arguments, loader references (including quoted keys, aliases and indirect
  calls), escaped property keys, non-literal computed access/binding keys (including templates and const-derived keys),
  duplicate HTML attributes, encoded URL/type attributes, and package CSS subpaths are refused.
  Timers require a direct call with a visible function literal.
  Numeric literals preserve following property names; malformed numeric continuations, misplaced separators and
  legacy leading-zero forms are refused. Regex flags accept only distinct letters from `dgimsuy`: unknown, escaped,
  duplicate and Unicode-set (`v`) flags are refused. This rejects some valid syntax intentionally.
  Every scanned URL attribute is validated before origin classification against a conservative ASCII subset:
  controls, spaces, backslashes, malformed escapes and uncertain schemes/authorities are refused. This is no full
  TypeScript, browser or WHATWG URL parser and no proof about reflective calls or general runtime behaviour.
  Conservative refusals reject harmless names (including type names), literal ampersands, computed indexing,
  array destructuring, callback aliases and valid URLs outside that subset too. The reader does not execute a web app
  or inspect installed packages.
- The first runnable slice defers the generated client and the component tests (ADR 0012). So there is no
  generated-client drift guarantee: the web app's request and response types are written by hand in
  `apps/shipping-web/src/api` and can drift from `contracts/shipping.openapi.yaml` without failing a build. The
  server's HTTP contract tests and the Playwright tests catch drift only for the operations and fields they exercise.
  The component tests (`ParcelForm.test.tsx`, `QuoteCardList.test.tsx`, `BookingForm.test.tsx`,
  `ShipmentDrawer.test.tsx`, `ProblemAlert.test.tsx`, `ThemeToggle.test.tsx`) wait for Vitest, Testing Library and
  jsdom. They stay acceptance targets, so the capabilities that name them stay unshipped; until they exist, only
  logic tests under `node --test` and the Playwright specs test the web app.
- The CI time target of 8 to 12 minutes is a target, not a measurement.

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
