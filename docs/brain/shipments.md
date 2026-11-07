---
title: "Brain: shipments"
description: "Generated summary of module shipments for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-07
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/shipments.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: shipments

Books shipments from quote offers with tenant-scoped idempotency, cancels them and renders simulated labels. Publishes ShipmentBooked and ShipmentCancelled.

- **Status in the model:** planned (`contract.status`). No production class may exist in its packages until the status is built.
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
| `shipments.book-from-offer` | planned | BookShipmentTest, RecipientTest, ShipmentsHttpContractTest | none yet |
| `shipments.idempotent-booking` | planned | BookingIdempotencyIntegrationTest, RequestFingerprintTest | none yet |
| `shipments.cancel-shipment` | planned | ShipmentTest, CancelShipmentTest | none yet |
| `shipments.list-and-read` | planned | ShipmentListingIntegrationTest, ShipmentsHttpContractTest | none yet |
| `shipments.simulated-label` | planned | SvgLabelRendererTest, ShipmentsHttpContractTest | none yet |
| `shipments.publish-events` | planned | ShipmentEventsIntegrationTest | none yet |

## Decisions its specification names

- [ADR 0006: Module events through the publication registry, with idempotent consumers](../adr/0006-module-events-through-the-publication-registry.md) (accepted)
- [ADR 0012: First runnable slice with a plain TypeScript workbench and Spring JDBC](../adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md) (accepted)

## Flows

- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

No module-specific gaps are recorded here. See the project-wide known gaps in [docs/gaps.md](../gaps.md).
