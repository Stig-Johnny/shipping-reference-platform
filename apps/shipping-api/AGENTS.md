# AGENTS.md: apps/shipping-api (Gradle project `:apps:shipping-api`)

Adds to the root `AGENTS.md`. Decisions: ADR 0012 (modules, contract and database; it supersedes ADRs 0003, 0004 and
0005) and ADR 0006 (events).

## Structure

- Base package `io.github.stigjohnny.shipping`. Modules: `quotes`, `shipments`, `tenants`, `audit` (planned for v0.1);
  `orders`, `tracking`, `returns` (planned: no code until `architecture/workspace.dsl` says `built`).
- Inside a module only these packages: `api`, `internal.domain`, `internal.application`, `internal.adapter.in.web`,
  `internal.adapter.out.jdbc`. Omit the ones you do not need. Any other package fails `HexagonalLayeringTest`.
- The root package contains exactly `ShippingApplication`, `CorrelationIdFilter` and `FallbackProblemHandler`
  (the model's `contract.isolated`). Do not add classes there; do not reference them from modules.
- Other modules may use only a module's `api` package, and only along a relationship in the C4 model.
- Every package has `package-info.java` with `@NullMarked`; `api` packages also have `@NamedInterface("api")`.
  Module root packages have `@ApplicationModule` without `allowedDependencies`.

## Java

- Use `spring-boot-starter-webmvc` for HTTP; `spring-boot-starter-web` was rejected by dependency review.
- Java 25 (`--release 25`), Spring Boot 4.1.2, Spring Modulith 2.1.2, Spring JDBC (`JdbcClient`, through
  `spring-boot-starter-jdbc`), Flyway 12.4.0. Spring Data JDBC is not in the build and v0.1 does not use it (ADR 0012):
  adapters write their SQL by hand, every value a bound parameter.
- Records for values, commands, events and responses. Validate in canonical constructors.
- Sealed interfaces for expected business outcomes (`QuoteResult`, `OfferLookup`, `BookingOutcome`,
  `CancellationOutcome`, `LabelOutcome`). Controllers switch over them exhaustively. Exceptions are for exceptional
  failures only.
- Constructor injection only. Inject `java.time.Clock` and ID generators; never call `Instant.now()`,
  `LocalDate.now()` or `UUID.randomUUID()` in domain or application code.
- Domain code imports nothing from Spring, Jakarta, `java.sql` or adapters.
- No Lombok. No field injection. No `Optional` fields or parameters. No `null` returns from public methods; use
  `Optional` or a sealed outcome.
- NullAway and Error Prone findings are compile errors. Do not suppress them.
- Jackson 3 is held at 3.1.8 by a constraint in `build.gradle.kts`. Spring Boot 4.1.2 manages 3.1.7, which a critical
  advisory covers (`security/reviews/maven--jackson--3.1.8.md`). Never use default typing (`activateDefaultTyping`),
  and never put `@JsonTypeInfo(use = CLASS)` or `MINIMAL_CLASS` on a type read from a request, an event or other
  untrusted JSON. The first module's tests add an ArchUnit rule that enforces the annotation part.
- `jackson-dataformat-yaml` is for tests only. The contract tests read `contracts/shipping.openapi.yaml` through the
  default `YAMLFactory`.
- Every dependency's review conditions bind this code. They are in the `conditions` frontmatter of its record in
  `security/reviews/`.
- `BigDecimal` for money, never `double`. Rounding happens once, in `SyntheticTariff`, half-up to cents.

## HTTP

- Implement exactly `contracts/shipping.openapi.yaml`. Change the contract first if the change requires it.
- Business outcomes become RFC 9457 problems in the module's web adapter, with the type URIs, titles and statuses
  from the contract. Never put stack traces, SQL, class names or another tenant's data in a response.
- Unknown request members are rejected (`fail-on-unknown-properties`). Problem bodies always carry `correlationId`.
- Never accept a tenant from a header, path, query or body. Use `CurrentTenant`.

## Database

- Each module uses only its own schema. Tables for a tenant-owned concept have `tenant_id`, a primary key that
  starts with `tenant_id`, foreign keys that include it, forced row-level security with the `tenant_isolation`
  policy, and minimal grants to `shipping_app`. Extend `RowLevelSecurityCatalogTest` expectations when you add one.
- Repository queries always name the tenant, even though row-level security would also filter.
- Migrations live in `src/main/resources/db/migration`, are forward-only, and never change after merge. Seed data
  lives in `src/main/resources/db/demo` (profile `demo` and tests only) and binds the tenant with
  `set_config('app.tenant_id', ..., true)` before inserting tenant-owned rows.
- The runtime uses `shipping_app` only. `shipping_owner` is for Flyway only.

## Events

- Event records live in the publisher's `api` package: `UUID eventId`, `TenantId tenantId`, the subject ID,
  `Instant occurredAt`. No personal data. TenantId from tenants.api is the only permitted type from another module.
- Publish inside the transaction that changes state. Consume with `@ApplicationModuleListener` in
  `internal.application`, inside `TenantScope.runAs(event.tenantId(), ...)`, and deduplicate by `eventId`.
- Delivery is at least once. Never assume exactly once.
- Changing an existing event record is level 3 and additive only.

## Tests

- Set `TESTCONTAINERS_RYUK_DISABLED=true` for local and CI Testcontainers runs. Ryuk is rejected; the test harness and runner own container cleanup.
- Mockito's inline mock maker runs as an explicit `-javaagent` on every Gradle `Test` task, which is Mockito's recipe
  for Java 21 and later. Never set `-XX:+EnableDynamicAgentLoading` or `jdk.attach.allowAttachSelf`
  (`security/reviews/maven--test-libraries--mockito-5.23.0-byte-buddy-1.18.15-and-assertion-libs.md`).

| Suffix | Kind | Runs in |
|---|---|---|
| `*Test` | Unit or slice by default; RowLevelSecurityCatalogTest, CrossTenantAccessTest and TableOwnershipTest use PostgreSQL where their assertions require it | `java` |
| `*IntegrationTest` | Testcontainers PostgreSQL 18.6 (pinned digest); never H2 | `java` |
| `*HttpContractTest` | HTTP tests that validate responses against the contract | `java` |
| `*PropertiesTest` | Parameterized property tests (JUnit Jupiter `@ParameterizedTest` with a `@MethodSource` of boundary cases and a fixed-seed `java.util.random.RandomGenerator` sample), in the normal `java` check | `java` |

- Use the exact class names from `docs/capabilities/capabilities.yaml` and the module documents.
- Use fixed clocks and deterministic ID generators. Use the synthetic tenants Alpha and Beta.
- Architecture tests live in the test package `io.github.stigjohnny.shipping.architecture`, database-wide tests in
  `io.github.stigjohnny.shipping.database`, HTTP-edge tests in `io.github.stigjohnny.shipping.http`. They are level 3
  or level 2 paths in `policy/risk.json`; do not weaken them.
- Mutation testing (PIT 1.30.2): at least 80 % killed mutants on changed lines, at least 90 % in `quotes`.

## Logging

- Log identifiers, outcomes and the correlation ID. Never log recipient names, addresses, tokens or request bodies.

## Common tasks

- **New endpoint:** contract, failing `*HttpContractTest`, controller, module document, flow if affected. The first
  runnable slice generates no client (ADR 0012); the web app's hand-written types change with its own code.
- **New dependency between modules:** model change (level 3) with the reason in the relationship description, then
  the code. Use `REQUIRED` when the collaboration is mandatory for correctness.
- **New table:** migration with policy and grants, `RowLevelSecurityCatalogTest` passes, module document's data
  section.
