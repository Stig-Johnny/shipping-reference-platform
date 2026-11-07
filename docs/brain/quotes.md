---
title: "Brain: quotes"
description: "Generated summary of module quotes for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-07
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/quotes.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: quotes

Prices parcels with the synthetic tariff and issues quotes whose offers expire after 30 minutes.

- **Status in the model:** planned (`contract.status`). No production class may exist in its packages until the status is built.
- **Packages:** `io.github.stigjohnny.shipping.quotes`
- **Risk level:** 3
- **Specification:** [docs/modules/quotes.md](../modules/quotes.md)
- **Overview of all modules:** [BRAIN.md](../../BRAIN.md)

## Dependencies

- **Uses:**
  - tenants (REQUIRED): Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId) [Java]
- **Used by:**
  - shipments (REQUIRED): Books only against an existing, unexpired offer (quotes.api: QuoteOffers, OfferLookup, Money, ServiceLevel) [Java]
  - audit (PERMITTED): May record QuoteIssued events; no listener exists in v0.1, so the report shows no static evidence [Spring Modulith event listener]
  - orders (PERMITTED, planned): Will request quotes for imported orders [Java]
- **Other relationships, not checked by the Java check:**
  - from Web app (not checked): Creates and reads quotes [JSON over HTTPS]
  - to Database (not checked): Stores quotes and offers in schema quotes [Spring Data JDBC]
  - to Carrier (simulated) (not checked): Prices each service level from the carrier's synthetic tariff (simulated in-process) [In-process simulation]

## HTTP operations

- `POST /api/v1/quotes` `createQuote`: Price a parcel and issue a quote with one offer per available service level.
- `GET /api/v1/quotes/{quoteId}` `getQuote`: Read a quote of the caller's tenant.

## Capabilities

| Capability | Status | Acceptance tests | Evidence |
|---|---|---|---|
| `quotes.synthetic-tariff` | planned | SyntheticTariffTest, SyntheticTariffPropertiesTest, MoneyTest, ParcelTest | none yet |
| `quotes.create-quote` | planned | RatingTest, CreateQuoteTest, QuotesHttpContractTest | none yet |
| `quotes.get-quote` | planned | QuotesModuleIntegrationTest, QuotesHttpContractTest | none yet |
| `quotes.quote-expiry` | planned | QuoteExpiryTest | none yet |
| `quotes.offer-lookup` | planned | QuotesModuleIntegrationTest, QuoteExpiryTest | none yet |

## Decisions its specification names

- [ADR 0005: PostgreSQL with Spring Data JDBC, Flyway and forced row-level security](../adr/0005-postgresql-spring-data-jdbc-flyway-and-forced-rls.md) (accepted)
- [ADR 0006: Module events through the publication registry, with idempotent consumers](../adr/0006-module-events-through-the-publication-registry.md) (accepted)

## Flows

- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

No module-specific gaps are recorded here. See the project-wide known gaps in [docs/gaps.md](../gaps.md).
