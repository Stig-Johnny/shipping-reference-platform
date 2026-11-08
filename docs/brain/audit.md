---
title: "Brain: audit"
description: "Generated summary of module audit for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-08
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/audit.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: audit

Appends one tenant-scoped audit entry per shipment event, deduplicated by event ID; replayed from the publication registry after failures.

- **Status in the model:** planned (`contract.status`). No production class may exist in its packages until the status is built.
- **Packages:** `io.github.stigjohnny.shipping.audit`
- **Risk level:** 2
- **Specification:** [docs/modules/audit.md](../modules/audit.md)
- **Overview of all modules:** [BRAIN.md](../../BRAIN.md)

## Dependencies

- **Uses:**
  - shipments (REQUIRED): Records the ShipmentBooked and ShipmentCancelled events that shipments publishes (shipments.api) [Spring Modulith event listener]
  - tenants (REQUIRED): Writes each audit entry inside the event's tenant scope (tenants.api: TenantScope, TenantId) [Java]
  - quotes (PERMITTED): May record QuoteIssued events; no listener exists in v0.1, so the report shows no static evidence [Spring Modulith event listener]
- **Used by:** none.
- **Other relationships, not checked by the Java check:**
  - to Database (not checked): Appends audit entries in schema audit [Spring JDBC (JdbcClient)]

## HTTP operations

None in `contracts/shipping.openapi.yaml`.

## Capabilities

| Capability | Status | Acceptance tests | Evidence |
|---|---|---|---|
| `audit.record-shipment-events` | planned | ShipmentAuditIntegrationTest | none yet |
| `audit.idempotent-consumer` | planned | AuditDeduplicationTest | none yet |
| `audit.replay-after-failure` | planned | AuditReplayIntegrationTest | none yet |

## Decisions its specification names

None.

## Flows

- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

No module-specific gaps are recorded here. See the project-wide known gaps in [docs/gaps.md](../gaps.md).
