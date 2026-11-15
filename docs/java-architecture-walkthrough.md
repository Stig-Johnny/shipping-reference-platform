---
title: "A Java architecture walkthrough"
description: "Run the shipping API, then follow its Java modules, ports, domain rules, JDBC transactions, tenant isolation and tests."
status: accepted
updated: 2026-11-15
---

# A Java architecture walkthrough

This shipping reference application uses Java 25 and Spring Boot 4.1.2: one deployable with three Spring Modulith
modules, `quotes`, `shipments` and `tenants`. It prices a parcel, books an offer and returns a simulated SVG label
using synthetic users and shipping data.

## At a glance

- [Modular monolith with enforced boundaries](#module-boundaries).
- [Hexagonal ports and adapters](#ports-adapters-and-profiles).
- [Explicit SQL with JdbcClient](#persistence-jdbcclient-rather-than-jpa).
- [Tenant isolation in code and PostgreSQL row-level security](#transactions-tenancy-and-concurrency).
- [Idempotent booking](#transactions-tenancy-and-concurrency).
- [Contract-first OpenAPI with RFC 9457 problems](#http-api).
- [Flyway migrations with tested down scripts](#migrations).
- [Tests at each layer](#testing-at-each-layer).

## Run it locally

The [launcher][launcher] runs only on the reviewed Temurin 25.0.5+8 JDK, named by `JAVA_HOME`; it checks
`java -version` before it builds anything. From the repository root, with Bash and curl available:

```sh
bash tools/demo/run-attended-demo.sh memory 8080 tools/demo/fixture
```

The static fixture page is at `http://127.0.0.1:8080/`; the [workbench runbook](flows/attended-demo.md#memory-mode)
has the full TypeScript UI setup.

In a second terminal, paste the following three calls in order; Python 3 extracts the returned IDs.
The bodies follow [the contract examples](../contracts/shipping.openapi.yaml), with the public synthetic
[`demo-clerk-alpha` token](modules/tenants.md#demo-authentication).

Create a quote:

```sh
demo_dir=$(mktemp -d)
cd "$demo_dir"
demo_token=demo-clerk-alpha
curl --noproxy '*' --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $demo_token" -H 'Content-Type: application/json' \
  --data '{"parcel":{"weightGrams":1200,"lengthMm":300,"widthMm":200,"heightMm":100},"origin":{"countryCode":"NO","postcode":"0150"},"destination":{"countryCode":"SE","postcode":"11122"}}' \
  -o quote.json -w 'quote: HTTP %{http_code}\n' http://127.0.0.1:8080/api/v1/quotes
quote_id=$(python3 -c 'import json; print(json.load(open("quote.json"))["quoteId"])')
offer_id=$(python3 -c 'import json; print(next(o["offerId"] for o in json.load(open("quote.json"))["offers"] if o["serviceLevel"] == "STANDARD"))')
```

Book its STANDARD offer with an idempotency key:

```sh
curl --noproxy '*' --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $demo_token" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $quote_id" \
  --data "{\"quoteId\":\"$quote_id\",\"offerId\":\"$offer_id\",\"recipient\":{\"name\":\"Synthetic Recipient 1\",\"addressLine1\":\"1 Example Street\",\"postcode\":\"11122\",\"city\":\"Exampleby\",\"countryCode\":\"SE\"},\"reference\":\"ORDER-1001\"}" \
  -o shipment.json -w 'booking: HTTP %{http_code}\n' http://127.0.0.1:8080/api/v1/shipments
shipment_id=$(python3 -c 'import json; print(json.load(open("shipment.json"))["shipmentId"])')
```

Download the label:

```sh
curl --noproxy '*' --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $demo_token" \
  -o label.svg -w 'label: HTTP %{http_code}, %{content_type}\n' \
  "http://127.0.0.1:8080/api/v1/shipments/$shipment_id/label"
printf 'Demo files: %s\n' "$demo_dir"
```

Recorded on 2026-11-15 with these exact commands: quote `201`, booking `201`, label `200 image/svg+xml`.
Stop the launcher with Ctrl-C; memory data disappears. [Shell fixtures][launcher-test] test the launcher's
refusals, [port tests][port-test] its port check, and [DemoSmokeTest.java][smoke] the application over real HTTP;
[ADR 0018](adr/0018-attended-demo-runtime.md) records runtime packaging.

## Project and package layout

Start at [ShippingApplication.java][app] and the [API build][api-build]. [settings.gradle.kts][settings] includes
the application, architecture library and documentation tool; the web source lives in `apps/shipping-web`.
Each business module is a package under `io.github.stigjohnny.shipping`:

| Package | Job |
|---|---|
| `api` | Types other modules use, such as [QuoteOffers.java][quote-offers]. |
| `internal.domain` | Values, aggregates and calculations, such as [Quote.java][quote]. |
| `internal.application` | Use cases and outbound ports: [CreateQuote.java][create], [QuoteStore.java][quote-port]. |
| `internal.adapter.in.web` | HTTP and wiring: [ShipmentsController.java][shipment-controller], [QuotesConfiguration.java][quote-wiring]. |
| `internal.adapter.out.jdbc` | SQL and mapping: [JdbcQuoteStore.java][quote-jdbc]. |

The [module root][module-info] has `@ApplicationModule`; the [API package][api-info] has `@NamedInterface("api")`.
HTTP controllers stay internal. Audit, orders, tracking and returns are planned in [workspace.dsl][model].

## Module boundaries

[ModularityTest.java][modularity] calls `ApplicationModules.of(ShippingApplication.class).verify()` and rejects
fixtures with a cycle or reference to another module's internals. [ArchitectureConformanceTest.java][architecture]
compares compiled dependencies with `quotes → tenants` and `shipments → quotes, tenants` in [workspace.dsl][model],
rejects a missing relationship and checks that planned modules contain no code.
[HexagonalLayeringTest.java][layering] keeps Spring/JDBC out of domain code, adapters out of application code,
and outbound adapters out of inbound adapters. See [ADR 0002](adr/0002-c4-model-is-the-structural-authority.md)
and [ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md).

## Ports, adapters and profiles

Use cases receive interfaces through constructors; Spring wiring selects memory or JDBC implementations.
[CreateQuote.java][create], [QuotesConfiguration.java][quote-wiring] and [CreateQuoteTest.java][create-test]
show the seam. The complete [QuoteStore.java][quote-port] interface body is:

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

Memory beans use `@Profile("demo & !postgres")`; JDBC repositories use `@Profile("postgres")`
([quotes][quote-memory-wiring], [shipments][shipment-wiring], [tenants][tenants-wiring]). The launcher selects
`demo` or `demo,postgres`. [JdbcProfileTest.java][jdbc-profiles] checks the annotations;
[DemoSmokeTest.java][smoke] and [DemoPostgresSmokeIntegrationTest.java][postgres-smoke] exercise each runtime.

Shipments obtains a bookable offer through the module port [QuoteOffers.java][quote-offers], implemented by
[QuoteOffersService.java][offer-service]. [QuoteExpiryTest.java][expiry-test] checks expiry;
[QuotesModuleIntegrationTest.java][quote-db] checks foreign-tenant lookup.

## Domain model and invariants

Records validate values; use cases enforce rules across values:

- [Money.java][money] uses `BigDecimal`, refuses negative amounts and constructor rounding
  ([MoneyTest.java][money-test]). [SyntheticTariff.java][tariff] rounds once, half-up to cents
  ([SyntheticTariffTest.java][tariff-test]).
- [Parcel.java][parcel] validates dimensions and weight and calculates billable weight
  ([ParcelTest.java][parcel-test]). [Quote.java][quote] copies offers and requires a 30-minute lifetime;
  [QuoteExpiryTest.java][expiry-test] checks expiry at the exact instant.
- [BookShipment.java][booking] checks replay, offer validity, recipient destination and earlier booking in order
  ([BookShipmentTest.java][booking-test]). The sealed [BookingOutcome.java][booking-outcome] distinguishes booked,
  replayed and rejected; the controller maps these to HTTP. Injected clocks and ID generators make tests deterministic.

## Persistence: JdbcClient rather than JPA

The design keeps persistence mechanics visible: every statement is explicit SQL, with bound parameters and explicit
mapping to immutable records and values. There is no ORM session, lazy loading or dirty checking.
[JdbcQuoteStore.java][quote-jdbc] writes a quote and its offers transactionally and reconstructs the aggregate.
[JdbcShipmentStore.java][shipment-jdbc] binds the tenant with `set_config` on the same connection before each data
or lock statement, takes transaction-scoped advisory locks for booking and uses keyset pagination for listing.
These are the mechanics a reviewer can inspect directly.

[ADR 0012](adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md) chose `JdbcClient`
from the already reviewed `spring-boot-starter-jdbc` in the [API build][api-build], replacing
[ADR 0005's Spring Data JDBC choice](adr/0005-postgresql-spring-data-jdbc-flyway-and-forced-rls.md): it reuses the
JDBC stack already in the build instead of adding a dependency.

[QuotesModuleIntegrationTest.java][quote-db] runs [QuoteStoreContract.java][quote-contract] for round trips,
tenant separation and duplicate IDs, plus rollback and concurrent reads. [ShipmentListingIntegrationTest.java][shipment-db]
runs [ShipmentStoreContract.java][shipment-contract] against PostgreSQL.

## Transactions, tenancy and concurrency

In PostgreSQL mode, `BookShipment.handle` encloses the transaction. Each adapter binds the service's tenant before
each data or lock statement on that connection ([ADR 0017](adr/0017-jdbc-transaction-binding-and-booking-locks.md)).
The exact helper in [JdbcQuoteStore.java][quote-jdbc] is:

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

`true` makes the setting transaction-local. [RowLevelSecurityIntegrationTest.java][rls-test] checks clearing on
commit/rollback and tenant separation even without SQL tenant predicates. [V3__shipments.sql][v3] forces RLS:

```sql
alter table shipments.shipment enable row level security;
alter table shipments.shipment force row level security;
create policy tenant_isolation on shipments.shipment
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
```

[RowLevelSecurityCatalogTest.java][catalog-test] checks policies and the runtime role's lack of ownership,
superuser and `BYPASSRLS`. For pre-authentication lookup, [V1__tenants.sql][v1] denies direct identity-table access;
[JdbcMembershipDirectory.java][membership-jdbc] calls its security-definer function
([MembershipDirectoryIntegrationTest.java][membership-db]).

[BookShipment.java][booking] and [JdbcShipmentStore.java][shipment-jdbc] lock tenant/key, then tenant/quote,
until commit or rollback. A separate SELECT after each lock obtains a fresh READ COMMITTED snapshot after a wait.
The fingerprint check precedes expiry, preserving identical replays after expiry. [V3__shipments.sql][v3] also
makes `(tenant_id, idempotency_key)` and `(tenant_id, quote_id)` unique.
[BookingIdempotencyIntegrationTest.java][concurrency-test] observes lock waits with explicit JDBC transactions
and checks equal/changed bodies, competing keys, rollback and expiry.

## HTTP API

[shipping.openapi.yaml](../contracts/shipping.openapi.yaml) defines OpenAPI 3.1.2 and RFC 9457 problems.
Hand-written Spring MVC controllers implement every operation except shipment cancellation, which is not built
yet; the web adapter uses hand-written [types.ts][web-types].
[ShipmentsController.java][shipment-controller] returns `201` for booking, `200` with `Idempotency-Replayed: true`
for replay and `422` for a key reused with changed content. [ShipmentsHttpContractTest.java][shipment-http]
asserts replay after expiry:

```java
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getHeader("Location")).isEqualTo(location);
        assertThat(replay.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(json(replay)).isEqualTo(json(booked));
```

[ListShipments.java][listing], [ShipmentCursor.java][cursor] and [JdbcShipmentStore.java][shipment-jdbc] order by
`(booked_at DESC, shipment_id DESC)`, read `limit + 1` rows and omit `nextCursor` on the last page. The base64url
cursor binds tenant and status filter. [ShipmentsHttpContractTest.java][shipment-http] checks tied timestamps,
complete pages and foreign/filter-mismatched cursors; [ShipmentListingIntegrationTest.java][shipment-db] checks SQL.

[CorrelationIdFilter.java][correlation] reuses a valid `Correlation-Id` or generates one, setting the response and
logging MDC. [FallbackProblemHandler.java][fallback] maps framework failures to `type`, `title`, `status`, `detail`,
`instance` and `correlationId`. [ProblemDetailsHttpContractTest.java][problem-test] checks malformed input and
exceptional failures; [Contract.java][contract-helper] validates exercised statuses, headers and bodies.

## Authentication and tenant isolation

[SecurityConfiguration.java][security-config] authenticates public demo bearer tokens in the `demo` profile and
requires `TENANT_MEMBER` for `/api/**`. [DemoTokenAuthenticationFilter.java][auth-filter] derives the tenant from
membership and runs the request inside [ThreadTenantContext.java][context]'s `ScopedValue` binding.
[DemoTokenAuthenticationTest.java][auth-test] checks 401/403, ignored tenant hints, rejected tenant body members
and cleanup; [ThreadTenantContextTest.java][context-test] checks nesting and independent threads.

Foreign and unknown shipment IDs produce the same `404 shipment-not-found`, apart from request-specific echo
fields. [ShipmentsHttpContractTest.java][shipment-http] compares detail/label headers and problem bodies with
those fields removed. [GetQuote.java][get-quote] and [QuotesHttpContractTest.java][quote-http] apply the same rule
to quote reads.

## Migrations

Flyway applies [V1 tenants][v1], [V2 quotes][v2] and [V3 shipments][v3]. Paired down scripts, such as
[V3__shipments.down.sql][down], test the inverse schema change; production recovery uses forward migrations
([ADR 0016](adr/0016-down-scripts-proven-in-ci-production-rolls-forward.md)).
[MigrationFilesTest.java][migration-files] checks pairing; [ShippingDatabase.java][database-harness] runs the
scripts; [MigrationRoundTripTest.java][migration-roundtrip] compares catalogs before up, after down and after
up again, including an intentionally incomplete down script. The [Java workflow][ci] runs database tests in CI.
[application-demo.yaml][demo-config] disables runtime Flyway, so the database is migrated before launch.

## Testing at each layer

`./gradlew check` runs the ordinary Java/docs suite; `:apps:shipping-api:databaseTest` runs database-tagged tests
in CI ([API build][api-build], [Java workflow][ci]).

| Layer | What to read |
|---|---|
| Values and use cases | [MoneyTest.java][money-test], [SyntheticTariffTest.java][tariff-test], [CreateQuoteTest.java][create-test], [BookShipmentTest.java][booking-test]. |
| HTTP contract | [QuotesHttpContractTest.java][quote-http], [ShipmentsHttpContractTest.java][shipment-http], [TenantsHttpContractTest.java][tenant-http], [ProblemDetailsHttpContractTest.java][problem-test], using [Contract.java][contract-helper]. |
| Store contracts | [InMemoryStoresTest.java][memory-contracts] uses test doubles with the same contracts as [MembershipDirectoryIntegrationTest.java][membership-db], [QuotesModuleIntegrationTest.java][quote-db] and [ShipmentListingIntegrationTest.java][shipment-db]. |
| Structure | [ModularityTest.java][modularity], [HexagonalLayeringTest.java][layering], [ArchitectureConformanceTest.java][architecture], with deliberately invalid fixtures. |
| Real HTTP smoke | [DemoSmokeTest.java][smoke] and [DemoPostgresSmokeIntegrationTest.java][postgres-smoke] inherit [DemoHttpSmoke.java][smoke-flow]: sockets, production stores, fixed clock/IDs, quote through label and tenant denial. |
| Mutation testing | The [CI mutation job][ci] runs PIT for [c4-contract-kit][mutation-build]. |

## What a production system would still add

**Known limits** ([full gap register](gaps.md)):

- Memory mode supports sequential demos; concurrent booking atomicity requires the PostgreSQL transaction protocol
  ([implementation limits](gaps.md#0-state-of-this-commit)).
- Mutation testing covers only the architecture library; browser acceptance and generated-client drift checks are
  unfinished; migration round trips compare empty-database catalogs ([test limits](gaps.md#6-tests-and-quality-gates)).
- Static module checks cover Java references; SQL ownership and runtime ordering need additional checks
  ([static-analysis limits](gaps.md#1-what-the-architecture-check-cannot-prove)).
- Production identity, token lifecycle and rate limiting remain open ([identity gaps](gaps.md#2-security-and-identity)).
- Durable events, retries and audit consumers remain planned ([ADR 0006](adr/0006-module-events-through-the-publication-registry.md),
  [implementation gaps](gaps.md#0-state-of-this-commit)); deployment, TLS, backups, metrics and alerting are outside
  this slice ([operations](gaps.md#8-not-in-scope-for-v01)).
- Real carriers, cancellation, key expiry and retention/erasure remain open
  ([domain](gaps.md#4-domain-simplifications), [personal-data gaps](gaps.md#3-personal-data)).

The code was written with AI coding agents and held to the same checks as any contribution; the
[contribution records](contributions.md) and [issue history](contributions/history.md) record that work, and
the [local check commands](../README.md#try-it) reproduce its verification.

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
