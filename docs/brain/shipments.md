---
title: "Brain: shipments"
description: "Generated summary of module shipments for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-08
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/shipments.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: shipments

Books shipments from quote offers with tenant-scoped idempotency, cancels them and renders simulated labels. Publishes ShipmentBooked and ShipmentCancelled.

- **Status in the model:** built (`contract.status`).
- **Packages:** `io.github.stigjohnny.shipping.shipments`
- **Risk level:** 3
- **Specification:** [docs/modules/shipments.md](../modules/shipments.md)
- **Overview of all modules:** [BRAIN.md](../../BRAIN.md)

## Dependencies

- **Uses:**
  - tenants (REQUIRED): Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId) [Java]
  - quotes (REQUIRED): Books only against an existing, unexpired offer (quotes.api: QuoteOffers, OfferLookup, Money, ServiceLevel) [Java]
- **Used by:**
  - audit (REQUIRED): Records the ShipmentBooked and ShipmentCancelled events that shipments publishes (shipments.api) [Spring Modulith event listener]
  - orders (PERMITTED, planned): Will book shipments for imported orders [Java]
  - tracking (PERMITTED, planned): Will move shipments through carrier transit states [Java]
  - returns (PERMITTED, planned): Will create return shipments for delivered shipments [Java]
- **Other relationships, not checked by the Java check:**
  - from Web app (not checked): Books, lists, reads and cancels shipments; downloads labels [JSON over HTTPS]
  - to Database (not checked): Stores shipments and idempotency keys in schema shipments; writes event publications [Spring JDBC (JdbcClient)]
  - to Carrier (simulated) (not checked): Assigns simulated tracking numbers and renders simulated labels (in-process) [In-process simulation]

## HTTP operations

- `POST /api/v1/shipments` `bookShipment`: Book a shipment from one offer of an open quote. Idempotent per tenant and Idempotency-Key.
- `GET /api/v1/shipments` `listShipments`: List the caller's tenant's shipments, newest first.
- `GET /api/v1/shipments/{shipmentId}` `getShipment`: Read one shipment of the caller's tenant.
- `POST /api/v1/shipments/{shipmentId}/cancellation` `cancelShipment`: Cancel a booked shipment. Idempotent.
- `GET /api/v1/shipments/{shipmentId}/label` `getShipmentLabel`: Download the simulated label of a booked shipment as SVG.

## Capabilities

| Capability | Status | Acceptance tests | Evidence |
|---|---|---|---|
| `shipments.book-from-offer` | partial | BookShipmentTest, RecipientTest, ShipmentsHttpContractTest | BookShipmentTest, RecipientTest |
| `shipments.idempotent-booking` | partial | BookingIdempotencyIntegrationTest, RequestFingerprintTest | RequestFingerprintTest |
| `shipments.cancel-shipment` | planned | ShipmentTest, CancelShipmentTest | none yet |
| `shipments.list-and-read` | planned | ShipmentListingIntegrationTest, ShipmentsHttpContractTest | none yet |
| `shipments.simulated-label` | planned | SvgLabelRendererTest, ShipmentsHttpContractTest | none yet |
| `shipments.publish-events` | planned | ShipmentEventsIntegrationTest | none yet |

## Decisions its specification names

- [ADR 0006: Module events through the publication registry, with idempotent consumers](../adr/0006-module-events-through-the-publication-registry.md) (accepted)
- [ADR 0012: First runnable slice with a plain TypeScript workbench and Spring JDBC](../adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md) (accepted)
- [ADR 0013: Scenario views are checked against recorded interactions](../adr/0013-scenario-views-are-checked-against-recorded-interactions.md) (accepted)

## Flows

- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

- 0. State of this commit: The specification, the reviewed toolchain and the dependency allowlist with its enforcement are in place, and so are the reviewed dependencies of the docs generator and of the shipping API. The docs generator itself, `tools/docs`, exists: it generates the C4 view pages, `BRAIN.md` and the module brains, and builds the documentation site, which `.github/workflows/pages.yml` publishes from `main` after the publication scan. Its own controls, `control.brain-check` and `control.publication-scan`, are shipped with their tests as evidence. The library `libs/c4-contract-kit` (its slices 1 to 4) exists, and its controls carry their tests as evidence. So does its partial web import check, which compares detected imports in `apps/shipping-web` with the web container of the model; every web part is planned and the source and test roots are empty, so no real web imports are checked yet. The first application code exists, checked by `ArchitectureConformanceTest`, `ModularityTest` and `HexagonalLayeringTest`: the `quotes` module with its two HTTP operations, booking in the `shipments` module, in its domain and application layers, the `tenants` module's demo authentication with `GET /api/v1/me`, and the HTTP edge. Nine capabilities have passing tests: two of the quotes module are shipped, and three of it, two of the tenants module and two of the shipments module are partial, and so is the HTTP contract control. Every other capability in `docs/capabilities/capabilities.yaml` is `planned` with no evidence, and every test class named for them is still to be written (red first), except `ShipmentTest`, which exists for booking and does not yet assert cancellation. The shipping API's database harness exists too: in CI, `PostgresHarnessIntegrationTest` starts the reviewed PostgreSQL 18.6 image under the runner review's conditions and queries it. V1 (tenants), V2 (quotes), their down scripts and the role bootstrap now exist. MigrationFilesTest supplies only file-pairing evidence; the merged tree's up/down execution and RLS proof are pending CI, so control.migrations is partial and tenants.row-level-security remains planned. All four database test classes are tagged database and required in the reviewed harness window.
- 0. State of this commit: **The shipments module runs in its tests only.** No class of it is a Spring bean, and no endpoint, database or running application exists. `BookShipment`'s `@Transactional` has no effect yet, and that `ShipmentBooked` is published inside the booking's transaction is unproven until `ShipmentEventsIntegrationTest` exists. Requests with the same `Idempotency-Key` are not serialized yet: the advisory lock, the recheck under it and the handling of unique-key failures belong to the planned JDBC adapter, and `BookingIdempotencyIntegrationTest`, which is to show that concurrent duplicates create one shipment, is planned. The stores and the tracking numbers its tests use are in memory. The scenario checks wire `BookShipment` as a bean of their own plain Spring context, not the application's wiring (section 6).
- 6. Tests and quality gates: Mutation testing runs for `libs/c4-contract-kit` only, in CI (the `mutation` job), over all its production classes, at a 90 % threshold. The planned measure per change (80 % of mutants on changed lines, 90 % for pricing and checker code) is not built yet, and equivalent-mutant exclusions are reviewed by hand. So the pricing code of the `quotes` module and the booking code of the `shipments` module are not mutation-tested yet: PIT is not reviewed for `:apps:shipping-api`.
