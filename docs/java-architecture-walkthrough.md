---
title: "A Java architecture walkthrough"
description: "Run the shipping API, then follow its Java modules, ports, domain rules, JDBC transactions, tenant isolation and tests."
status: accepted
updated: 2026-11-15
---

# A Java architecture walkthrough

This is one Java 25/Spring Boot application with three Spring Modulith modules: `quotes`, `shipments` and
`tenants`. It prices a parcel, books an offer and returns a simulated SVG label, using synthetic users and shipping
data. Start at [ShippingApplication.java][app] and the [API build][api-build];
[ModularityTest.java][modularity] checks the three modules and [DemoHttpSmoke.java][smoke-flow] exercises the
shipping flow. [ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md)
records the application design.

## Run it locally

With Temurin 25.0.5+8 selected by `JAVA_HOME` and its `bin` first on `PATH`, run this from the repository root.
It needs Bash and curl; it builds and starts the memory API with a small checked-in static page:

```sh
bash tools/demo/run-attended-demo.sh memory 8080 tools/demo/fixture
```

Open `http://127.0.0.1:8080/`. This command serves the static fixture; for the full TypeScript workbench, follow
[the workbench preparation commands](flows/attended-demo.md#memory-mode). API calls use the public synthetic
users in [demo authentication](modules/tenants.md#demo-authentication). Stop with Ctrl-C; memory data disappears.
The [launcher][launcher], [shell fixtures][launcher-test] and [port tests][port-test] cover startup/refusal and
cleanup; [DemoSmokeTest.java][smoke] covers the memory application over real HTTP.
[ADR 0018](adr/0018-attended-demo-runtime.md) explains packaging and storage selection.

Recorded locally on 2026-11-15 with that JDK: the launcher reached readiness; quote, booking, replay, list,
detail and label requests succeeded; another tenant received the same shipment-not-found type and detail as an
unknown ID. Ctrl-C ended the launcher with exit 130, and the port check then exited 0. This is a local recording,
not a PostgreSQL or browser test result.

## Project and package layout

The business modules are packages inside one Gradle application, not separate services or separate Gradle
projects. [settings.gradle.kts][settings] also includes the architecture-checking library and the documentation
tool; the web source lives in `apps/shipping-web`. The actual Java module set and dependencies are asserted by
[ModularityTest.java][modularity] and [ArchitectureConformanceTest.java][architecture].

Under `io.github.stigjohnny.shipping.<module>`, the layout is:

| Package | Job |
|---|---|
| `api` | Types another module can use: for example [QuoteOffers.java][quote-offers]. |
| `internal.domain` | Values, aggregates and business calculations, such as [Quote.java][quote]. |
| `internal.application` | Use cases and outbound ports, such as [CreateQuote.java][create] and [QuoteStore.java][quote-port]. |
| `internal.adapter.in.web` | HTTP handling and bean wiring, such as [ShipmentsController.java][shipment-controller] and [QuotesConfiguration.java][quote-wiring]. |
| `internal.adapter.out.jdbc` | SQL and row mapping, such as [JdbcQuoteStore.java][quote-jdbc]. |

A module root has `@ApplicationModule`; its `api` package has `@NamedInterface("api")`
([module declaration][module-info], [API declaration][api-info]). Here `api` means the Java interface between
modules; HTTP controllers stay internal. [HexagonalLayeringTest.java][layering] checks these package names and
dependency directions. Audit, orders, tracking and returns have no application classes yet, as
[ArchitectureConformanceTest.java][architecture] asserts against [workspace.dsl][model]. See
[ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md).

## Module boundaries

Spring Modulith rejects cycles and references to another module's internals. The architecture test separately
compares compiled Java dependencies with the declared module relationships.

[ModularityTest.java][modularity] calls `ApplicationModules.of(ShippingApplication.class).verify()` and checks
fixtures with an internal-package reference and a cycle. [ArchitectureConformanceTest.java][architecture] checks
`quotes → tenants` and `shipments → quotes, tenants` against [workspace.dsl][model], including a failing fixture
with a missing relationship. [HexagonalLayeringTest.java][layering] keeps Spring/JDBC out of domain code,
adapters out of application code, and outbound adapters out of inbound adapters. These are static checks;
they do not prove runtime call order or discover arbitrary SQL coupling. The decisions and limits are in
[ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md),
[ADR 0002](adr/0002-c4-model-is-the-structural-authority.md) and [the static-analysis limits](gaps.md#1-what-the-architecture-check-cannot-prove).

## Ports, adapters and profiles

A use case receives interfaces through its constructor. Spring configuration supplies the implementation, so the
same use case works with memory storage or JDBC; [CreateQuote.java][create], [QuotesConfiguration.java][quote-wiring]
and [CreateQuoteTest.java][create-test] show that seam.

The complete interface body from [QuoteStore.java][quote-port] is small:

```java
public interface QuoteStore {

    /** Stores a newly issued quote with its offers. Quotes never change once stored. */
    void insert(Quote quote);

    /** The tenant's quote with this ID; another tenant's quote is never found. */
    Optional<Quote> find(TenantId tenant, QuoteId quote);
}
```

| Port | Memory demo | PostgreSQL |
|---|---|---|
| [QuoteStore.java][quote-port] | [DemoQuoteStore.java][quote-memory] | [JdbcQuoteStore.java][quote-jdbc] |
| [ShipmentStore.java][shipment-port] | [DemoShipmentStore.java][shipment-memory] | [JdbcShipmentStore.java][shipment-jdbc] |
| [MembershipDirectory.java][membership-port] | [DemoMembershipDirectory.java][membership-memory] | [JdbcMembershipDirectory.java][membership-jdbc] |
| [TrackingNumbers.java][tracking-port] | Counter in [DemoShipmentsConfiguration.java][shipment-wiring] | Sequence in [JdbcTrackingNumbers.java][tracking-jdbc] |

The memory bean configurations use `@Profile("demo & !postgres")`; the JDBC repositories use
`@Profile("postgres")`. The launcher selects `demo` for memory or `demo,postgres` for an operator-prepared
database ([quote wiring][quote-memory-wiring], [shipment wiring][shipment-wiring], [tenant wiring][tenants-wiring],
[launcher][launcher]). [JdbcProfileTest.java][jdbc-profiles] checks the JDBC annotations, while
[DemoSmokeTest.java][smoke] and [DemoPostgresSmokeIntegrationTest.java][postgres-smoke] exercise the two runtimes.
Memory mode supports sequential demonstrations; synchronized store methods do not make the entire booking
transaction atomic. See [ADR 0018](adr/0018-attended-demo-runtime.md).

[QuoteOffers.java][quote-offers] is the separate module-to-module port: shipments asks quotes for a bookable offer
instead of accessing quote internals. [QuoteOffersService.java][offer-service] implements it;
[QuoteExpiryTest.java][expiry-test] exercises the expiry boundary; [QuotesModuleIntegrationTest.java][quote-db]
also checks foreign-tenant lookup. This boundary follows
[ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md).

## Domain model and invariants

Values validate themselves in record constructors; calculations and use cases enforce the rules that involve
several values. The domain does not depend on Spring, as [HexagonalLayeringTest.java][layering] checks under
[ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md).

- [Money.java][money] uses `BigDecimal`, refuses negative amounts and refuses rounding in its constructor
  ([MoneyTest.java][money-test]). [SyntheticTariff.java][tariff] performs the price rounding once, half-up to cents
  ([SyntheticTariffTest.java][tariff-test]).
- [Parcel.java][parcel] validates weight/dimension bounds and computes billable weight from actual and volumetric
  weight ([ParcelTest.java][parcel-test]). [Quote.java][quote] copies its offers and requires a 30-minute lifetime;
  an offer expires at the expiry instant, not one instant later ([QuoteExpiryTest.java][expiry-test]).
- [BookShipment.java][booking] checks replay, offer validity, recipient destination and earlier booking in order
  ([BookShipmentTest.java][booking-test]). Its [BookingOutcome.java][booking-outcome] is sealed: booked, replayed
  or rejected. [ShipmentsController.java][shipment-controller] switches over those outcomes to produce HTTP
  responses ([ShipmentsHttpContractTest.java][shipment-http]). Clocks and ID generators are injected into the
  use cases, allowing deterministic time and identifiers in these tests.

## Persistence: JdbcClient rather than JPA

The adapters write SQL and map rows explicitly with Spring JDBC's `JdbcClient`; there is no JPA entity model.
[ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md) chose the Spring
JDBC dependency already in the build, replacing the earlier plan for Spring Data JDBC. It does not record a
JPA performance comparison.

In [JdbcQuoteStore.java][quote-jdbc], insertion writes the quote and its offers in one transaction; reading maps
them back into a `Quote`. Query values are bound parameters. [QuotesModuleIntegrationTest.java][quote-db] runs
[QuoteStoreContract.java][quote-contract] for round trips, tenant separation and duplicate IDs, and adds rollback
and concurrent-read cases. [JdbcShipmentStore.java][shipment-jdbc] likewise maps shipment records; its shared
[ShipmentStoreContract.java][shipment-contract] runs in [ShipmentListingIntegrationTest.java][shipment-db].
The [API build][api-build] declares Spring JDBC and PostgreSQL, not a JPA starter.

## Transactions, tenancy and concurrency

In PostgreSQL mode, `BookShipment.handle` is the enclosing Spring transaction. Each JDBC adapter binds the
application service's tenant on that connection before each data or lock statement, with a transaction-local
setting; [ADR 0017](adr/0017-jdbc-transaction-binding-and-booking-locks.md) explains the choice.

This is the exact helper in [JdbcQuoteStore.java][quote-jdbc]:

```java
    private JdbcClient.StatementSpec sql(TenantId tenant, String statement) {
        jdbc.sql("select set_config('app.tenant_id', :tenant, true)")
                .param("tenant", tenant.value().toString())
                .query(String.class)
                .single();
        return jdbc.sql(statement)
                .param("tenant", tenant.value());
    }
```

`true` makes the setting local to the transaction. [RowLevelSecurityIntegrationTest.java][rls-test] checks that
commit and rollback clear it on a reused connection, and that SQL without tenant predicates still cannot see or
write another tenant's rows. The migrations enable and force RLS; [V3__shipments.sql][v3] contains:

```sql
alter table shipments.shipment enable row level security;
alter table shipments.shipment force row level security;
create policy tenant_isolation on shipments.shipment
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
```

[RowLevelSecurityCatalogTest.java][catalog-test] checks policies and the runtime role's lack of ownership,
superuser and `BYPASSRLS`. Identity lookup is a deliberate exception: [V1__tenants.sql][v1] denies direct runtime
access to identity tables, and [JdbcMembershipDirectory.java][membership-jdbc] calls its security-definer lookup
function before a tenant is known ([MembershipDirectoryIntegrationTest.java][membership-db]). RLS scopes a
connection; authentication still belongs at the request boundary.

Booking concurrency follows an explicit protocol in [BookShipment.java][booking] and
[JdbcShipmentStore.java][shipment-jdbc]: lock tenant/key first, then tenant/quote; hold the advisory locks until
commit or rollback. A separate SELECT after each lock gets a fresh READ COMMITTED snapshot after any wait.
The key/fingerprint check comes before quote expiry, so an identical replay still succeeds after expiry.
[V3__shipments.sql][v3] also has unique constraints on `(tenant_id, idempotency_key)` and `(tenant_id, quote_id)`.
[BookingIdempotencyIntegrationTest.java][concurrency-test] observes actual lock waits and checks equal bodies,
changed bodies, competing keys and rollback/expiry. It uses explicit JDBC transactions; it is not a concurrent
HTTP load test. Writers bypassing this protocol get database constraint errors. See
[ADR 0017](adr/0017-jdbc-transaction-binding-and-booking-locks.md).

## HTTP API

The HTTP shape comes from [shipping.openapi.yaml](../contracts/shipping.openapi.yaml), and hand-written Spring
MVC controllers are tested against it. [ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md)
keeps OpenAPI 3.1 and RFC 9457 problems, but defers a generated client; the web app's [types.ts][web-types] is
hand-written.

[ShipmentsController.java][shipment-controller] returns `201` for a booking and `200` with
`Idempotency-Replayed: true` for an identical replay; reusing a key with changed content gives `422`.
[ShipmentsHttpContractTest.java][shipment-http] contains these assertions after advancing the clock to expiry:

```java
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getHeader("Location")).isEqualTo(location);
        assertThat(replay.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(json(replay)).isEqualTo(json(booked));
```

Pagination uses `(booked_at DESC, shipment_id DESC)`, reads `limit + 1` rows and omits `nextCursor` on the last
page. [ListShipments.java][listing], [ShipmentCursor.java][cursor] and [JdbcShipmentStore.java][shipment-jdbc]
implement it; the cursor is base64url, bound to the tenant and status filter, not a signed credential.
[ShipmentsHttpContractTest.java][shipment-http] checks tied timestamps, complete pages and foreign/filter-mismatched
cursors; [ShipmentListingIntegrationTest.java][shipment-db] checks the store contract on PostgreSQL.

[CorrelationIdFilter.java][correlation] reuses a valid `Correlation-Id` or generates one and puts it in the response
and logging MDC. [FallbackProblemHandler.java][fallback] maps framework failures to problem documents with
`type`, `title`, `status`, `detail`, `instance` and `correlationId`.
[ProblemDetailsHttpContractTest.java][problem-test] checks malformed input and exceptional failures against the
contract. [Contract.java][contract-helper] validates exercised statuses, headers and bodies; it does not prove
that every documented operation exists. In particular, cancellation is still absent from
[ShipmentsController.java][shipment-controller], as [the implementation limits](gaps.md#0-state-of-this-commit) state.

## Authentication and tenant isolation

Spring Security authenticates public demo bearer tokens and derives the tenant from membership. The client cannot
choose a tenant by adding a header, query parameter or body member.

[SecurityConfiguration.java][security-config] installs the token filter only in the `demo` profile and requires
`TENANT_MEMBER` for `/api/**`. [DemoTokenAuthenticationFilter.java][auth-filter] runs the request inside
[ThreadTenantContext.java][context], whose `ScopedValue` binding ends on return or exception.
[DemoTokenAuthenticationTest.java][auth-test] checks 401/403 responses, ignored tenant hints, rejected tenant body
members and binding cleanup; [ThreadTenantContextTest.java][context-test] checks nested bindings and independent
threads. See [ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md).

A foreign shipment ID and an unknown ID produce the same `404 shipment-not-found`, apart from request-specific
echo fields. [ShipmentsController.java][shipment-controller] uses tenant-scoped lookups for detail and label;
[ShipmentsHttpContractTest.java][shipment-http] compares their headers and problem bodies after removing those
echo fields. Quote reads follow the same rule ([GetQuote.java][get-quote],
[QuotesHttpContractTest.java][quote-http]). These demo tokens are not production identity.

## Migrations

Flyway applies versioned SQL upwards; paired down scripts test the inverse schema change in disposable databases.
Production's intended recovery is a new forward migration, not running a destructive down script
([ADR 0016](adr/0016-down-scripts-proven-in-ci-production-rolls-forward.md)).

The current migrations are [V1 tenants][v1], [V2 quotes][v2] and [V3 shipments][v3]; for example,
[V3__shipments.down.sql][down] removes the shipment objects. [MigrationFilesTest.java][migration-files] checks
pairing without a database. [ShippingDatabase.java][database-harness] runs Flyway and down scripts in the test
harness; [MigrationRoundTripTest.java][migration-roundtrip] compares catalogs before up, after down and after up
again, including an intentionally incomplete down script. The [Java workflow][ci] runs these database tests in
CI. They verify schema restoration, not recovery of deleted data. The demo's [application-demo.yaml][demo-config]
disables runtime Flyway; PostgreSQL must be migrated separately before launch, as
[ADR 0018](adr/0018-attended-demo-runtime.md) specifies.

## Testing at each layer

Tests separate pure Java behavior, HTTP responses and actual PostgreSQL behavior. `./gradlew check` runs the
ordinary Java/docs suite; the separate `:apps:shipping-api:databaseTest` task runs database-tagged tests in CI
([API build][api-build], [Java workflow][ci]). Local memory success does not establish database behavior.

| Layer | What to read |
|---|---|
| Values and use cases | [MoneyTest.java][money-test], [SyntheticTariffTest.java][tariff-test], [CreateQuoteTest.java][create-test] and [BookShipmentTest.java][booking-test]. |
| HTTP contract | [QuotesHttpContractTest.java][quote-http], [ShipmentsHttpContractTest.java][shipment-http], [TenantsHttpContractTest.java][tenant-http] and [ProblemDetailsHttpContractTest.java][problem-test], using [Contract.java][contract-helper]. |
| Store contracts | [InMemoryStoresTest.java][memory-contracts] implements the same contract interfaces as [MembershipDirectoryIntegrationTest.java][membership-db], [QuotesModuleIntegrationTest.java][quote-db] and [ShipmentListingIntegrationTest.java][shipment-db]. These memory fixtures are test doubles, distinct from the demo stores. |
| Structure | [ModularityTest.java][modularity], [HexagonalLayeringTest.java][layering] and [ArchitectureConformanceTest.java][architecture], including deliberately invalid fixtures. |
| End-to-end HTTP smoke | [DemoSmokeTest.java][smoke] and [DemoPostgresSmokeIntegrationTest.java][postgres-smoke] inherit [DemoHttpSmoke.java][smoke-flow]: real sockets, production stores, fixed clock/IDs, quote through label and tenant denial. They do not drive a browser. |
| Mutation testing | The [CI mutation job][ci] runs PIT for [c4-contract-kit][mutation-build] only. API/pricing mutation tests and changed-line thresholds are not implemented; see [the test limits](gaps.md#6-tests-and-quality-gates). Do not run PIT locally. |

The choices are recorded in [ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md),
[ADR 0016](adr/0016-down-scripts-proven-in-ci-production-rolls-forward.md),
[ADR 0017](adr/0017-jdbc-transaction-binding-and-booking-locks.md) and
[ADR 0018](adr/0018-attended-demo-runtime.md). Automated browser acceptance remains unfinished; see
[the current implementation limits](gaps.md#0-state-of-this-commit).

## What a production system would still add

This is a runnable reference application with explicit limits. The missing pieces below are documented in
[known gaps](gaps.md); an absent feature has no passing acceptance test to cite.

- **Production identity and request limits.** Replace public demo tokens with a real identity provider and add
  expiry/revocation and rate limiting. The current [security configuration][security-config] and
  [authentication tests][auth-test] exercise demo identity only; [the identity gaps](gaps.md#2-security-and-identity)
  state what is absent.
- **Durable events and audit.** [CreateQuote.java][create] and [BookShipment.java][booking] call Spring's
  in-process publisher, which their unit tests check. [application-demo.yaml][demo-config] disables durable
  publication; there is no outbox/publication-table migration or audit consumer in this slice. Implement the
  publication registry, retries, deduplication and event tenant scope described by
  [ADR 0006](adr/0006-module-events-through-the-publication-registry.md) and [the current gaps](gaps.md#0-state-of-this-commit).
- **Operations.** Add deployment, TLS termination, backups/restoration, metrics/tracing and alerting appropriate
  to the service. The [launcher][launcher] provides a local process and [CorrelationIdFilter.java][correlation]
  provides log correlation; the Actuator dependency in the [build][api-build] is not an operational deployment.
  [The operations limits](gaps.md#8-not-in-scope-for-v01) remain, beyond the local [smoke tests][smoke].
- **Business lifecycle and data lifecycle.** Add real carrier integration, cancellation, retention/erasure and
  idempotency-key expiry. [ShipmentsController.java][shipment-controller] and its [tests][shipment-http] cover the
  current book/list/read/label flow; [domain simplifications](gaps.md#4-domain-simplifications) and
  [personal-data gaps](gaps.md#3-personal-data) identify the rest.

AI tools assisted the implementation and review of this repository. The [contribution records](contributions.md)
and [issue history](contributions/history.md) describe that work; the [local checks](../README.md#try-it) and
[test limits](gaps.md#6-tests-and-quality-gates) explain how it was checked and what remains unverified.

[app]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/ShippingApplication.java
[get-quote]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/application/GetQuote.java
[api-build]: ../apps/shipping-api/build.gradle.kts
[settings]: ../settings.gradle.kts
[launcher]: ../tools/demo/run-attended-demo.sh
[launcher-test]: ../tools/demo/run-attended-demo-test.sh
[port-test]: ../tools/demo/LoopbackPortCheckTest.java
[smoke]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/http/DemoSmokeTest.java
[smoke-flow]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/http/DemoHttpSmoke.java
[postgres-smoke]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/DemoPostgresSmokeIntegrationTest.java
[modularity]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/architecture/ModularityTest.java
[layering]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/architecture/HexagonalLayeringTest.java
[architecture]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/architecture/ArchitectureConformanceTest.java
[model]: ../architecture/workspace.dsl
[module-info]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/package-info.java
[api-info]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/api/package-info.java
[quote-port]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/application/QuoteStore.java
[quote-offers]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/api/QuoteOffers.java
[offer-service]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/application/QuoteOffersService.java
[quote-wiring]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/adapter/in/web/QuotesConfiguration.java
[quote-memory-wiring]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/adapter/in/web/DemoQuotesConfiguration.java
[quote-memory]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/adapter/in/web/DemoQuoteStore.java
[quote-jdbc]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/adapter/out/jdbc/JdbcQuoteStore.java
[shipment-port]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/application/ShipmentStore.java
[shipment-memory]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/adapter/in/web/DemoShipmentStore.java
[shipment-jdbc]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/adapter/out/jdbc/JdbcShipmentStore.java
[membership-port]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/application/MembershipDirectory.java
[membership-memory]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/adapter/in/web/DemoMembershipDirectory.java
[membership-jdbc]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/adapter/out/jdbc/JdbcMembershipDirectory.java
[shipment-wiring]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/adapter/in/web/DemoShipmentsConfiguration.java
[tenants-wiring]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/adapter/in/web/DemoTenantsConfiguration.java
[tracking-port]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/application/TrackingNumbers.java
[tracking-jdbc]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/adapter/out/jdbc/JdbcTrackingNumbers.java
[jdbc-profiles]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/JdbcProfileTest.java
[money]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/api/Money.java
[money-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/quotes/api/MoneyTest.java
[parcel]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/domain/Parcel.java
[parcel-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/quotes/internal/domain/ParcelTest.java
[quote]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/domain/Quote.java
[expiry-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/quotes/internal/application/QuoteExpiryTest.java
[tariff]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/domain/SyntheticTariff.java
[tariff-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/quotes/internal/domain/SyntheticTariffTest.java
[create]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/quotes/internal/application/CreateQuote.java
[create-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/quotes/internal/application/CreateQuoteTest.java
[booking]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/application/BookShipment.java
[booking-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/shipments/internal/application/BookShipmentTest.java
[booking-outcome]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/domain/BookingOutcome.java
[shipment-http]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/shipments/internal/adapter/in/web/ShipmentsHttpContractTest.java
[shipment-controller]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/adapter/in/web/ShipmentsController.java
[quote-http]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/quotes/internal/adapter/in/web/QuotesHttpContractTest.java
[tenant-http]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/tenants/internal/adapter/in/web/TenantsHttpContractTest.java
[problem-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/http/ProblemDetailsHttpContractTest.java
[contract-helper]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/http/Contract.java
[correlation]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/CorrelationIdFilter.java
[fallback]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/FallbackProblemHandler.java
[listing]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/application/ListShipments.java
[cursor]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/domain/ShipmentCursor.java
[security-config]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/adapter/in/web/SecurityConfiguration.java
[auth-filter]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/adapter/in/web/DemoTokenAuthenticationFilter.java
[context]: ../apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/application/ThreadTenantContext.java
[context-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/tenants/internal/application/ThreadTenantContextTest.java
[auth-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/tenants/internal/adapter/in/web/DemoTokenAuthenticationTest.java
[memory-contracts]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/InMemoryStoresTest.java
[quote-contract]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/QuoteStoreContract.java
[quote-db]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/QuotesModuleIntegrationTest.java
[shipment-contract]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/ShipmentStoreContract.java
[shipment-db]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/ShipmentListingIntegrationTest.java
[membership-db]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/MembershipDirectoryIntegrationTest.java
[concurrency-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/BookingIdempotencyIntegrationTest.java
[rls-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/RowLevelSecurityIntegrationTest.java
[catalog-test]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/RowLevelSecurityCatalogTest.java
[v1]: ../apps/shipping-api/src/main/resources/db/migration/V1__tenants.sql
[v2]: ../apps/shipping-api/src/main/resources/db/migration/V2__quotes.sql
[v3]: ../apps/shipping-api/src/main/resources/db/migration/V3__shipments.sql
[down]: ../apps/shipping-api/src/main/resources/db/down/V3__shipments.down.sql
[migration-files]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/MigrationFilesTest.java
[migration-roundtrip]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/MigrationRoundTripTest.java
[database-harness]: ../apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/ShippingDatabase.java
[demo-config]: ../apps/shipping-api/src/main/resources/application-demo.yaml
[ci]: ../.github/workflows/java.yml
[mutation-build]: ../libs/c4-contract-kit/build.gradle.kts
[web-types]: ../apps/shipping-web/src/api/types.ts
