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
  application code exists: the domain and application layers of the `quotes` module and of booking in the `shipments`
  module, and the tenant context of the `tenants` module, checked by `ArchitectureConformanceTest`, `ModularityTest`
  and `HexagonalLayeringTest`. Six capabilities have passing tests: two of the quotes module are shipped, and two of
  it and two of the shipments module are partial. The shipping API's database harness exists too: in CI,
  `PostgresHarnessIntegrationTest` starts the reviewed PostgreSQL 18.6 image under the runner review's conditions and
  queries it; no schema exists yet. Every other capability in `docs/capabilities/capabilities.yaml` is `planned` with
  no evidence, and every test class named for them is still to be written (red first), except `ShipmentTest`, which
  exists for booking and does not yet assert cancellation.
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

- **The container image check rejects what it recognizes, and refuses what it cannot decode.** The checker rejects
  recognized container declarations. The harness validates its PostgreSQL instance, and CI checks subsequent engine
  activity. These controls do not isolate arbitrary build code. Check 8 matches only in the forms it decodes (shell
  words with each command in its place; YAML scalars on one line and literal blocks; Java and Kotlin without comments,
  with their string literals decoded, alone and joined) and refuses the rest: heredocs, `$'\x..'`, YAML scalars over
  lines, folded blocks, anchors, tags and expressions in run steps, a command that is an expansion or a glob, brace
  expansion, a shell that reads its input, awk programs that run commands, interpreters and build tools it does not
  read (apart from ADR 0007's exact reviewed web commands), backquoted Kotlin names, Groovy, binaries under `src/`, executables, container
  files, the variables that make a shell or the JVM run other code, and Unix sockets opened from code outside the
  harness. Java/Kotlin/Gradle launch capabilities, reflection lookup/invocation and method handles require an exact
  file path and whole-file SHA-256 in the checker's launch-site registry (ADR 0007). The structural scan includes
  `tools/policy/`, tests, build outputs and caches; image-string scanning still treats the checker's fixtures as data.
  A changed registered file, a new launch site, an alias, an `env` wrapper or subsequent argument mutation fails
  without a matching pin. Registered sites still undergo the existing command-vector checks. Hashes freeze reviewed
  launchers; their dynamic JavaExec arguments and the behaviour of libraries and commands they execute still need
  review. A library that starts a container of its own, or a program invoked in turn (sed, git's configuration,
  `find -exec`), stays outside this source gate. Documents outside `src/` and review records are not executable code. At run time CI sees which images appeared and which images
  containers were made from, and whether anything was copied into one; it does not see how another container was
  configured, its privileges, mounts or networks. Only the harness's own container is checked for those. The exact
  `node --test` exception runs repository-owned logic tests with discovery rooted in `apps/shipping-web`. It does not
  analyze their JavaScript/TypeScript or prevent their imports from reaching other code or engine sockets/endpoints.
  Exact command matching supplies no runtime isolation; an absolute engine-access restriction would need enforced
  isolation. Verified tool installation, review conditions and subsequent engine auditing remain necessary. Check 8
  inspects root and declared workspace scripts and refuses unregistered bodies, lifecycle hooks and unresolved forms;
  it does not model every program an otherwise permitted command can invoke.
- **The platform image is inferred, not read back.** The runner's engine keeps an image's index digest, not the
  digest of the platform manifest it pulled. That the tests ran the linux/amd64 image `sha256:e923a22a…` follows
  from the pinned index, whose platforms the review lists, and from the image's reported platform; nothing compares
  that digest itself.

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
- **Database tests run only where a reviewed engine is prepared.** `./gradlew check` runs checks requiring only the
  reviewed JDK. Database tests run through `:apps:shipping-api:databaseTest` and are mandatory in Java CI and Pages
  testing. Missing engine prerequisites fail that task; tests are never conditionally skipped. The engine is
  GitHub-hosted ubuntu-24.04 with Docker Engine 28.0.4, prepared by `tools/ci/container-engine.sh`; anywhere else
  `ReviewedPostgres` refuses to start PostgreSQL. A local `./gradlew check` therefore shows no database evidence, which
  only CI's builds read and require. The two colima hosts that a review approves are not engines of the harness yet.
- **The event check sees the engine's last 256 events.** After the tests, the workflow reads their window from the
  engine's event buffer to show that every container came from the reviewed image and nothing was copied into one.
  It fails when the window holds 256 events or more, because it could no longer show that it saw them all.
- **The site's evidence passes between jobs as a job output.** The publishing job builds the site from the test job's
  JUnit reports, the database tests' included, which travel as a job output of at most 100,000 characters (they
  arrive as an environment variable, which Linux limits to 128 KiB). Reports that outgrow it fail the handover, and
  another transport, such as an artifact download, needs its own review first.
- axe-core finds a subset of accessibility problems; manual keyboard and screen reader checks are required and
  recorded by hand.
- The web app's static checks are to cover import boundaries and cycles, and a direct import for each REQUIRED
  relationship whose endpoints are built (ADR 0012). Relationships touching planned parts are not evaluated, a direct
  import proves a reference and not behaviour, and a part that no REQUIRED relationship reaches can be unused without
  a report. dependency-cruiser is deferred.
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
