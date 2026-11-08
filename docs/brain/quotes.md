---
title: "Brain: quotes"
description: "Generated summary of module quotes for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-08
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
  - to Database (not checked): Stores quotes and offers in schema quotes [Spring JDBC (JdbcClient)]
  - to Carrier (simulated) (not checked): Prices each service level from the carrier's synthetic tariff (simulated in-process) [In-process simulation]

## HTTP operations

- `POST /api/v1/quotes` `createQuote`: Price a parcel and issue a quote with one offer per available service level.
- `GET /api/v1/quotes/{quoteId}` `getQuote`: Read a quote of the caller's tenant.

## Capabilities

| Capability | Status | Acceptance tests | Evidence |
|---|---|---|---|
| `quotes.synthetic-tariff` | shipped | SyntheticTariffTest, SyntheticTariffPropertiesTest, MoneyTest, ParcelTest | SyntheticTariffTest, SyntheticTariffPropertiesTest, MoneyTest, ParcelTest |
| `quotes.create-quote` | partial | RatingTest, CreateQuoteTest, QuotesHttpContractTest | RatingTest, CreateQuoteTest, QuotesHttpContractTest |
| `quotes.get-quote` | partial | QuotesModuleIntegrationTest, QuotesHttpContractTest | QuotesHttpContractTest |
| `quotes.quote-expiry` | shipped | QuoteExpiryTest | QuoteExpiryTest |
| `quotes.offer-lookup` | partial | QuotesModuleIntegrationTest, QuoteExpiryTest | QuoteExpiryTest |

## Decisions its specification names

- [ADR 0006: Module events through the publication registry, with idempotent consumers](../adr/0006-module-events-through-the-publication-registry.md) (accepted)
- [ADR 0012: First runnable slice with a plain TypeScript workbench and Spring JDBC](../adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md) (accepted)

## Flows

- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

- 0. State of this commit: The specification, the reviewed toolchain and the dependency allowlist with its enforcement are in place, and so are the reviewed dependencies of the docs generator and of the shipping API. The docs generator itself, `tools/docs`, exists: it generates the C4 view pages, `BRAIN.md` and the module brains, and builds the documentation site, which `.github/workflows/pages.yml` publishes from `main` after the publication scan. Its own controls, `control.brain-check` and `control.publication-scan`, are shipped with their tests as evidence. The library `libs/c4-contract-kit` (its slices 1 to 4) exists, and its controls carry their tests as evidence. So does its partial web import check, which compares detected imports in `apps/shipping-web` with the web container of the model; every web part is planned and the source and test roots are empty, so no real web imports are checked yet. The first application code exists, checked by `ArchitectureConformanceTest`, `ModularityTest` and `HexagonalLayeringTest`: the `quotes` module with its two HTTP operations, booking in the `shipments` module, in its domain and application layers, the `tenants` module's demo authentication with `GET /api/v1/me`, and the HTTP edge. Nine capabilities have passing tests: two of the quotes module are shipped, and three of it, two of the tenants module and two of the shipments module are partial, and so is the HTTP contract control. Every other capability in `docs/capabilities/capabilities.yaml` is `planned` with no evidence, and every test class named for them is still to be written (red first), except `ShipmentTest`, which exists for booking and does not yet assert cancellation. The shipping API's database harness exists too: in CI, `PostgresHarnessIntegrationTest` starts the reviewed PostgreSQL 18.6 image under the runner review's conditions and queries it; no schema exists yet.
- 0. State of this commit: **The application starts in its tests only, without a database.** The HTTP tests start `ShippingApplication` with test doubles for the two ports that have no adapter yet: the tenants module's membership directory, which holds the synthetic users of `docs/modules/tenants.md`, and the quotes module's store, in memory. They leave out the data source and Spring Modulith's event publication registry, which need PostgreSQL. So they prove the HTTP edge, the problems, and that the tenant comes from the membership only. They do not prove that a token is resolved through `tenants.resolve_demo_token`, that a quote is stored in PostgreSQL, or that the database hides another tenant's quote. No transaction manager exists, so the `@Transactional` of `CreateQuote` and `GetQuote` has no effect, and that `QuoteIssued` is published inside the quote's transaction is unproven until `QuotesModuleIntegrationTest` exists. Outside its tests the application does not start yet: its ports have no production adapters, and the `demo` profile has no database configuration. The scenario checks wire the quotes and tenants classes as beans of a plain Spring context of their own, which is not the application's wiring (section 6).
- 6. Tests and quality gates: Mutation testing runs for `libs/c4-contract-kit` only, in CI (the `mutation` job), over all its production classes, at a 90 % threshold. The planned measure per change (80 % of mutants on changed lines, 90 % for pricing and checker code) is not built yet, and equivalent-mutant exclusions are reviewed by hand. So the pricing code of the `quotes` module and the booking code of the `shipments` module are not mutation-tested yet: PIT is not reviewed for `:apps:shipping-api`.
