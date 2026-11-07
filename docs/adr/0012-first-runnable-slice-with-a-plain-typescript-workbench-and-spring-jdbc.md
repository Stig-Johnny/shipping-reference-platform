---
title: "ADR 0012: First runnable slice with a plain TypeScript workbench and Spring JDBC"
description: "Supersedes ADRs 0003, 0004, 0005 and 0009. Their modules, contract-first API and PostgreSQL design carry forward unchanged, except that adapters use Spring JDBC's JdbcClient; the workbench is plain TypeScript with a hand-written typed HTTP adapter, and React and the generated client are deferred."
status: accepted
updated: 2026-11-07
date: 2026-11-07
decision-makers: repository owner
enforcedBy:
  - "java: ModularityTest (Spring Modulith verify)"
  - "java: HexagonalLayeringTest (ArchUnit)"
  - "java: ArchitectureConformanceTest (C4 edges)"
  - "java: compilation with Error Prone and NullAway"
plannedEnforcement:
  - "java: QuotesHttpContractTest, ShipmentsHttpContractTest, TenantsHttpContractTest, ProblemDetailsHttpContractTest"
  - "java: RowLevelSecurityCatalogTest, RowLevelSecurityIntegrationTest, CrossTenantAccessTest"
  - "java: MigrationsIntegrationTest, and TableOwnershipTest on the SQL of each module's adapters"
  - "java: WebArchitectureConformanceTest, the JDK import checker over apps/shipping-web/src: import boundaries, cycles, and a direct import for each REQUIRED relationship whose endpoints are built"
  - "web: type check and emit with TypeScript 5.9.3, and the logic tests under node --test"
  - "contract-e2e: Playwright and axe-core against the API started with the demo profile on PostgreSQL"
---

# ADR 0012: First runnable slice with a plain TypeScript workbench and Spring JDBC

Supersedes ADR 0003, ADR 0004, ADR 0005 and ADR 0009. What they decided carries forward here unchanged, except where
this record says otherwise; their text stays as it was accepted.

## Context and Problem Statement

The first runnable slice is to be a real browser application on the Java API and a migrated PostgreSQL database,
built within days. Two accepted decisions cannot be built in that time without breaking ADR 0007, which allows no
third-party component before its review:

- ADR 0009's web stack (React, Vite, Vitest, Testing Library, jsdom and a client generated with openapi-typescript and
  openapi-fetch) resolves to about 170 npm packages, about 150 of them without a review. The top-level packages have
  approved entries; most of their transitive dependencies have none. Reviewing that many packages overnight would
  produce approvals nobody had the time to make real: a control that only looks enforced.
- ADR 0005 names Spring Data JDBC, which is not in the API's build. Adding it needs a dependency review and a
  maintenance lock pull request.

The package counts are derived from registry metadata, not from a lockfile. ADR 0001 requires a changed decision to be
a new record that supersedes the old one, so this record replaces ADRs 0003, 0004, 0005 and 0009 and carries forward
what stays. How is the slice built from reviewed components only, and what of those four records still holds?

## Decision Drivers

- No third-party code before its review (ADR 0007), and no review rushed to make a control look enforced.
- A slice that runs: a browser application against the Java API and PostgreSQL, not stubs.
- Every check the slice claims can fail, and what it does not check is written down.
- The accepted module structure, contract and tenant isolation do not weaken.

## Considered Options

For the web app (package counts from registry metadata):

1. ADR 0009's stack as accepted: about 170 packages, about 150 without a review.
2. React with a hand-written client: about 70 packages, about 60 without a review.
3. A small application stack compiled by TypeScript 7.0.2: 26 packages, 19 without a review, among them the native
   compiler binaries for platforms other than Linux x64.
4. A plain TypeScript app compiled by TypeScript 5.9.3, with Playwright and axe-core for browser tests: six packages,
   of which only TypeScript 5.9.3 lacks a review.

For persistence:

5. Spring Data JDBC, as ADR 0005 decided: not in the build.
6. Spring JDBC's `JdbcClient`, from `spring-boot-starter-jdbc`, which is reviewed and already in the API's build.

## Decision Outcome

Chosen options 4 and 6. React, Vite, Vitest, Testing Library, jsdom and the generated client (openapi-typescript and
openapi-fetch) are deferred, not rejected: they come back only with their reviews and a record that supersedes this
one.

### The web app of the first runnable slice

- **No framework.** The workbench is a plain TypeScript single-page app. Its router (History API), state, forms and
  components are the project's own code, and it installs no runtime package. `docs/ui/workbench.md` stays binding and
  records this slice's scope: cancellation and the prototype-flag preview are deferred.
- **Compiler.** TypeScript 5.9.3 instead of 7.0.2, emitting browser ES modules, without a bundler. It is one package
  without runtime dependencies, whereas the review of 7.0.2 approves only its Linux x64 native binary and the slice is
  also built on macOS. Its review is pending (issue #59); until 5.9.3 has an approved allowlist entry, nothing
  installs it.
- **Erasable syntax only**, so that Node 26's built-in type stripping runs the logic tests from the sources, without a
  build step. `tsc` rejects most departures (`erasableSyntaxOnly`, `verbatimModuleSyntax`), and relative imports name
  their `.ts` file, which `tsc` rewrites to `.js` (`rewriteRelativeImportExtensions`). The rules are listed in
  `apps/shipping-web/AGENTS.md`.
- **Serving.** The Java application serves the emitted assets on loopback (`127.0.0.1`), from the same origin as the
  API. There is no separate web server.
- **API calls.** One hand-written, typed HTTP adapter in `apps/shipping-web/src/api` is the only code that calls
  `fetch`. It adds the bearer token and `Correlation-Id` and returns typed results and problems. Its types are written
  by hand from `contracts/shipping.openapi.yaml`; `packages/shipping-client` is not created.
- **Tests.** `node --test`, Node's built-in test runner, runs the logic tests. Playwright 1.65.0 with axe-core 4.14.0
  runs the end-to-end and accessibility specs in the `contract-e2e` CI job, against the API started with the `demo`
  profile on PostgreSQL. Their reviews approve them for that job only, so browser automation does not run on developer
  machines.
- **Import rules.** The JDK import checker in `libs/c4-contract-kit` (issues #62 to #64) enforces them, not
  `tools/web-imports/check-imports.mjs`, which is not built. The model declares the web app's six parts, `app`,
  `auth`, `api`, `quote-feature`, `shipment-feature` and `ui`, and `WebArchitectureConformanceTest` checks
  `apps/shipping-web/src` against them in the `java` check. The rules are listed in `apps/shipping-web/AGENTS.md`.
- **Scope of the import check.** The checker enforces import boundaries and cycles, and requires a direct import for
  each REQUIRED relationship whose endpoints are built. Relationships touching planned parts are not evaluated. The
  HTTP relationship from the web app to the API stays outside the check; the end-to-end tests are its evidence, for
  the operations they run. This changes ADR 0009, which claimed import boundaries and cycles only and deferred
  required frontend edges: the frontend gets both failure directions, as the backend has.

### Persistence through Spring JDBC

- **Adapters.** In v0.1, `internal.adapter.out.jdbc` holds adapters written with Spring JDBC's `JdbcClient` from
  `spring-jdbc` 7.0.10, auto-configured through `spring-boot-starter-jdbc` 4.1.2. They implement the module's outbound
  ports with explicit SQL, and the review conditions of both artifacts bind them (`security/reviews/`).
- **SQL.** Each module's SQL is written by hand in its `internal.adapter.out.jdbc` package. Every value is a bound
  parameter; no statement is assembled from input. Every statement names the tenant (`where tenant_id = :tenant`).
  Optimistic locking, where a table has a `version` column, is a `version` check in the update's own SQL.
- **Table ownership.** A module reads and writes only its own schema, and that needs evidence that can fail. With no
  `@Table` mappings, a check of `@Table` annotations would scan nothing and pass: an empty `@Table` scan proves
  nothing. `TableOwnershipTest` shows ownership from the SQL instead: every statement of a module's adapters touches
  only tables of that module's schema. One way is a test that reads each statement and requires every table it names
  to be qualified with the module's schema; another is a check that asks PostgreSQL which relations each statement
  uses and looks up their schemas in the catalog. Either way, a fixture in which an audit adapter statement reads
  `shipments.shipment` must fail it.

### Carried forward from ADR 0003: a modular monolith with hexagonal modules

- One deployable on Spring Boot 4.1.2 and Spring Modulith 2.1.2. Base package `io.github.stigjohnny.shipping`; each
  module is a direct sub-package: `quotes`, `shipments`, `tenants`, `audit` (planned for v0.1) and `orders`,
  `tracking`, `returns` (planned, no code).
- Inside a built module exactly these packages may exist, and a module omits the ones it does not need:
  - `api`: the only package other modules may use, annotated `@NamedInterface("api")` in `package-info.java`;
  - `internal.domain`: records, validated values, aggregates and sealed outcomes, without Spring, Jakarta, JDBC or
    adapter imports;
  - `internal.application`: use cases (one public method each), outbound port interfaces and event listeners;
  - `internal.adapter.in.web`: controllers, request and response records, problem mapping;
  - `internal.adapter.out.jdbc`: the Spring JDBC adapters above.
- Inside a module, `adapter.*` may use `application`, `domain` and `api`; `application` may use `domain` and `api`;
  `domain` may use the JDK, JSpecify and `api` packages, and nothing from Spring, Jakarta, JDBC, `application` or
  adapters; `adapter.in` must not use `adapter.out`. Across modules only `api` packages may be used, and only along
  relationships declared in `architecture/workspace.dsl`.
- A module's `package-info.java` carries `@ApplicationModule` without `allowedDependencies`: the C4 model is the single
  list of allowed edges.
- The root package holds only the isolated classes named in the model (`ShippingApplication`, `CorrelationIdFilter`,
  `FallbackProblemHandler`). They depend on no module, and no module depends on them.
- Java style: records for values and events; validation in canonical constructors; sealed interfaces for expected
  business outcomes; exceptions only for exceptional failures; constructor injection only; an injected
  `java.time.Clock`; `@NullMarked` on every package (JSpecify 1.0.1), enforced by NullAway 0.14.3 on Error Prone
  2.51.0; no Lombok; no field injection.

### Carried forward from ADR 0004: contract-first OpenAPI with RFC 9457 problems

- `contracts/shipping.openapi.yaml`, OpenAPI 3.1.2 (JSON Schema 2020-12 dialect), is the authority for the HTTP
  interface. Schemas are closed (`additionalProperties: false`), bounded (lengths, ranges, patterns, enums) and use no
  `null`; the server rejects unknown request members (`spring.jackson.deserialization.fail-on-unknown-properties`).
- Java controllers are written by hand. Tests validate each exercised response (status, headers, body) against the
  contract with a test-only JSON Schema 2020-12 validator that is reviewed and allowlisted before use (candidate:
  `com.networknt:json-schema-validator`).
- Errors are RFC 9457 `application/problem+json`: stable `type` URIs under
  `https://shipping-reference-platform.example/problems/` (registry in the contract's description), a fixed `title`
  per type, `status`, a safe `detail`, `instance` (the request path) and a `correlationId` member. A resource of
  another tenant is reported exactly like a missing one. The isolated `FallbackProblemHandler` maps framework errors;
  each module's web adapter maps business outcomes from sealed results, not from exceptions.
- Every response carries `Correlation-Id`, generated by `CorrelationIdFilter` or reused from a valid request header,
  and logged in the MDC under `correlationId`.
- Contract changes are level 3. No breaking change is made in v0.1 without a new `info.version` and `/api/v1` prefix.
- The generated client is deferred (above): the server's HTTP contract tests remain the contract gate.

### Carried forward from ADR 0005: PostgreSQL, Flyway and forced row-level security

- PostgreSQL 18.6. Tests use Testcontainers 2.0.5 with the same version, the image pinned by digest and allowlisted;
  H2 and other substitutes are not used.
- One schema per built module, named after the module (`quotes`, `shipments`, `tenants`, `audit`). A module reads and
  writes only its own schema.
- Every tenant-owned table has `tenant_id uuid not null`, a primary key that starts with `tenant_id`, and foreign keys
  that include `tenant_id`, so a row can never reference another tenant's row.
- Every tenant-owned table has `enable row level security`, `force row level security` and a policy
  `tenant_isolation` with `using` and `with check` on
  `tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid`. Without a bound tenant a query sees no rows
  and an insert fails.
- Roles: `shipping_owner` owns the schemas, tables and functions and is used only by Flyway. `shipping_app` is the
  runtime role: not an owner, `NOSUPERUSER`, `NOBYPASSRLS`, and only the grants each table needs (audit entries:
  `INSERT, SELECT` only). `apps/shipping-api/postgres/init-roles.sql` creates them outside Flyway, for Testcontainers
  and the local `compose.yaml` alike.
- Tenant binding: the tenants module's transaction manager runs `select set_config('app.tenant_id', ?, true)` at the
  start of every transaction with the bound tenant, or with `''` when none is bound. The setting is transaction-local,
  so a pooled connection never carries a tenant into the next transaction. Inside a transaction, `JdbcClient` runs its
  statements on that transaction's connection, so the binding applies to them.
- Identity tables (`tenants.tenant`, `tenants.app_user`, `tenants.membership`, `tenants.demo_token`) have no row-level
  security; `shipping_app` has no privileges on them and reads them only through the security-definer function
  `tenants.resolve_demo_token(bytea)`, owned by `shipping_owner`, with a fixed `search_path`.
- Migrations: Flyway 12.4.0 runs as `shipping_owner` from `classpath:db/migration` (`V1__tenants.sql`,
  `V2__quotes.sql`, `V3__shipments.sql`, `V4__audit.sql`, `V5__event_publication.sql`); the synthetic seed in
  `classpath:db/demo` (`V1000__synthetic_tenants.sql`, `V1001__expired_quote_fixture.sql`) is added only by the `demo`
  profile and tests. Applied migrations are never edited. Later production use requires backward-compatible (expand,
  then contract) migrations.

### Carried forward from ADR 0009: the workbench's rules

- One single-page app for clerks. Its screen specification is `docs/ui/workbench.md`, whose visual decisions bind the
  implementation.
- The web app keeps business rules on the server. It may format, disable and explain (for example disable booking of
  an offer whose `expiresAt` has passed), but the server's answer decides.
- No router, state-management, data-fetching or component library: five routes, a small router on the History API,
  and plain CSS with custom properties as design tokens. Adding any runtime library needs a review and an allowlist
  entry.
- The demo token is held in memory only, never in `localStorage`, `sessionStorage` or cookies.
- dependency-cruiser stays deferred. The C4 relationship from the web app to the API is a network call that Java
  static checks cannot see; end-to-end tests verify it.
- Node 26.13.0 and npm 11.20.0; Playwright 1.65.0 and axe-core 4.14.0.

### What this replaces in other records

- ADR 0007's decision names `apps/shipping-web`, `packages/shipping-client` and `docs` as the npm workspaces that
  share the root `package-lock.json`. ADR 0011 removed `docs`, and this record defers `packages/shipping-client`,
  which leaves `apps/shipping-web`.
- ADR 0011's correction of that list says that only `apps/shipping-web` and `packages/shipping-client` are npm
  workspaces. Of the two, only `apps/shipping-web` remains.

### Consequences

- Good, because the slice needs one new package review instead of about 150, and the database code none.
- Good, because the app sends the browser only the project's own code.
- Good, because the frontend gets both failure directions, as the backend has: an import the model does not allow
  fails, and so does a REQUIRED relationship between built parts without a direct import. A direct import proves a
  reference, not that the code behind it runs, and a part that no REQUIRED relationship reaches can be unused without
  a report.
- Good, because every statement is SQL that reviewers read, and the level 3 signal for changed SQL in
  `policy/risk.json` covers it.
- Bad, because there is no generated-client drift guarantee. The hand-written types can drift from the contract, and
  no type check notices. The server's HTTP contract tests and the end-to-end tests are what catch drift, and only for
  the operations and fields they exercise (`docs/gaps.md`). ADR 0009's consequence that a contract change which breaks
  the UI fails type checking, and ADR 0004's that the UI and API cannot disagree silently, hold only on the server's
  side.
- Bad, because the component tests are deferred with their tooling. `ParcelForm.test.tsx`, `QuoteCardList.test.tsx`,
  `BookingForm.test.tsx`, `ShipmentDrawer.test.tsx`, `ProblemAlert.test.tsx` and `ThemeToggle.test.tsx` stay the
  acceptance targets in `docs/capabilities/capabilities.yaml`, so the capabilities that name them stay unshipped until
  they exist.
- Bad, because rendering is the project's own DOM code, with no framework that escapes text by default. The rule
  against `innerHTML` and its relatives in `apps/shipping-web/AGENTS.md` carries that risk; only review enforces it
  today.
- Bad, because mapping rows to records is hand-written code that tests must cover.
- Still true from the superseded records: extracting a module later is a redesign of its boundary, not a packaging
  change; hexagonal ports add interfaces a small module would not need; Error Prone and NullAway couple the build to
  compiler internals; contract conformance is only as complete as the tests; a closed schema makes every additive
  change a contract change; a new tenant table needs its policy, grants and a catalog test update; the event
  publication table is not tenant-scoped; one runtime role serves all modules, so the database does not stop one
  module's SQL from reading another module's schema, and `TableOwnershipTest` is the control; the web app's router,
  state and components are code the project maintains.

### Confirmation

- `java` runs `ModularityTest` (`ApplicationModules.of(ShippingApplication.class).verify()`), `HexagonalLayeringTest`
  (ArchUnit 1.5.1: only the five package names above inside a built module; the dependency rules above; a
  `package-info.java` annotated `@NullMarked` in every package; each rule also fails on a fixture that breaks it) and
  `ArchitectureConformanceTest` (ADR 0002). Compilation fails on Error Prone errors and NullAway findings;
  `@SuppressWarnings("NullAway")` is a level 3 behaviour signal in `policy/risk.json`.
- Planned, `java`: `QuotesHttpContractTest`, `ShipmentsHttpContractTest` and `TenantsHttpContractTest` validate
  responses against the contract; `ProblemDetailsHttpContractTest` covers an unknown path, a wrong method, a wrong
  media type, malformed JSON, an unknown member and an unexpected exception: the correct type, no stack trace, a
  correlation ID.
- Planned, `java`: `RowLevelSecurityCatalogTest` queries `pg_class`, `pg_policy` and `pg_roles`: every table with a
  `tenant_id` column has row-level security enabled and forced and at least one policy, except the four identity
  tables, on which `shipping_app` has no privileges; `shipping_app` is not superuser, lacks `BYPASSRLS` and owns
  nothing. `RowLevelSecurityIntegrationTest` uses plain JDBC as `shipping_app`: a foreign tenant sees and changes
  nothing; no bound tenant sees nothing and cannot insert; with a pool of size 1, tenant A's transaction followed by
  tenant B's sees only B's rows. `CrossTenantAccessTest` calls the HTTP API with Tenant Beta's token and Tenant
  Alpha's quote and shipment IDs and expects `404` problems for read, book, cancel and label.
  `MigrationsIntegrationTest` migrates an empty database as `shipping_owner` and asserts that `shipping_app` cannot
  run DDL.
- Planned, `java`: `TableOwnershipTest` asserts that every statement of each module's adapters touches only that
  module's schema, and fails on the fixture above.
- Planned, `java`: `WebArchitectureConformanceTest` runs the JDK import checker over `apps/shipping-web/src`
  (issue #64). An import the model does not allow, an import cycle, and a REQUIRED relationship between built parts
  without a direct import each fail it.
- Planned, `web`: `npm ci --ignore-scripts` from the reviewed lockfile; `tsc` type-checks and emits; `node --test`
  runs the logic tests.
- Planned, `contract-e2e`: `quote-book-label.spec.ts`, `expired-quote.spec.ts`, `tenant-denial.spec.ts` and
  `accessibility.spec.ts` (axe-core on every screen and state) against the API started with the `demo` profile on
  PostgreSQL. `prototype-flag.spec.ts` waits for the label preview that this slice defers.
- Deferred: the `web` check that regenerates the client and fails on any diff, and the Vitest component tests.
- Not enforced yet: a check that every documented operation and status code is exercised by at least one test, and
  OpenAPI style linting (`docs/gaps.md`).

## More Information

- The superseded records, unchanged: ADR 0003, ADR 0004, ADR 0005 and ADR 0009.
- Node.js and TypeScript: <https://nodejs.org/api/typescript.html>; the compiler option `erasableSyntaxOnly`:
  <https://www.typescriptlang.org/tsconfig/#erasableSyntaxOnly>.
- Screens, states and accessibility: `docs/ui/workbench.md`. Module data: `docs/modules/<module>.md`.
