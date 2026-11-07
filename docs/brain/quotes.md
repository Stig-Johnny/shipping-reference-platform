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

- **Status in the model:** built (`contract.status`).
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
| `quotes.synthetic-tariff` | shipped | SyntheticTariffTest, SyntheticTariffPropertiesTest, MoneyTest, ParcelTest | SyntheticTariffTest, SyntheticTariffPropertiesTest, MoneyTest, ParcelTest |
| `quotes.create-quote` | partial | RatingTest, CreateQuoteTest, QuotesHttpContractTest | RatingTest, CreateQuoteTest |
| `quotes.get-quote` | planned | QuotesModuleIntegrationTest, QuotesHttpContractTest | none yet |
| `quotes.quote-expiry` | shipped | QuoteExpiryTest | QuoteExpiryTest |
| `quotes.offer-lookup` | partial | QuotesModuleIntegrationTest, QuoteExpiryTest | QuoteExpiryTest |

## Decisions its specification names

- [ADR 0005: PostgreSQL with Spring Data JDBC, Flyway and forced row-level security](../adr/0005-postgresql-spring-data-jdbc-flyway-and-forced-rls.md) (accepted)
- [ADR 0006: Module events through the publication registry, with idempotent consumers](../adr/0006-module-events-through-the-publication-registry.md) (accepted)

## Flows

- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

- 0. State of this commit: The specification, the reviewed toolchain and the dependency allowlist with its enforcement are in place, and so are the reviewed dependencies of the docs generator and of the shipping API. The docs generator itself, `tools/docs`, exists: it generates the C4 view pages, `BRAIN.md` and the module brains, and builds the documentation site, which `.github/workflows/pages.yml` publishes from `main` after the publication scan. Its own controls, `control.brain-check` and `control.publication-scan`, are shipped with their tests as evidence. The library `libs/c4-contract-kit` (its slices 1 to 4) exists, and its controls carry their tests as evidence. The first application code exists: the domain and application layers of the `quotes` module and the tenant context of the `tenants` module, checked by `ArchitectureConformanceTest`, `ModularityTest` and `HexagonalLayeringTest`. Four capabilities of the quotes module have passing tests: two are shipped and two partial. Every other capability in `docs/capabilities/capabilities.yaml` is `planned` with no evidence, and every test class named for them is still to be written (red first).
- 0. State of this commit: **The quotes module runs in its tests only.** No class of it is a Spring bean, and no endpoint, database or running application exists. `CreateQuote`'s `@Transactional` has no effect yet, and that `QuoteIssued` is published inside the quote's transaction is unproven until `QuotesModuleIntegrationTest` exists. The store its tests use is in memory, so nothing yet shows that another tenant's quote is not found in the database.
- 6. Tests and quality gates: Mutation testing runs for `libs/c4-contract-kit` only, in CI (the `mutation` job), over all its production classes, at a 90 % threshold. The planned measure per change (80 % of mutants on changed lines, 90 % for pricing and checker code) is not built yet, and equivalent-mutant exclusions are reviewed by hand. So the pricing code of the `quotes` module is not mutation-tested yet: PIT is not reviewed for `:apps:shipping-api`.
