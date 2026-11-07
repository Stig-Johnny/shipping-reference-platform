---
title: "Module: audit"
description: "Appends one tenant-scoped audit entry per shipment event, deduplicated by event ID, with replay from the publication registry after failures."
status: accepted
updated: 2026-11-07
module: audit
moduleStatus: planned
riskLevel: 2
---

# Module: audit

Package `io.github.stigjohnny.shipping.audit`. C4 component `audit` (`contract.status planned`). Risk level 2 for its
code; its migration, SQL and tenant-scope calls raise a change to level 3 (`policy/risk.json`). A minimal supporting
slice whose purpose is to prove event delivery, replay and idempotent consumption.

## Responsibilities

- Listen to `ShipmentBooked` and `ShipmentCancelled` from `shipments.api`.
- Append exactly one audit entry per event, in the event's tenant, even when the event is delivered more than once.
- Leave the publication incomplete when the write fails, so the registry can replay it.

Not its job in v0.1: reading audit entries over HTTP, recording quote events, retention.

## Relationships (from `architecture/workspace.dsl`)

| Direction | Other component | Mode | Static evidence expected |
|---|---|---|---|
| audit -> shipments | shipments | REQUIRED | Listener method parameters of type `ShipmentBooked` and `ShipmentCancelled`. |
| audit -> tenants | tenants | REQUIRED | Calls to `TenantScope.runAs` and use of `TenantId`. |
| audit -> quotes | quotes | PERMITTED | None in v0.1. The report must say "no static evidence". Audit code must not reference any `quotes` type. |

The arrow `audit -> shipments` is the code dependency. At runtime the event flows from shipments to audit.

## Public API

None in v0.1. The module has no `api` package, and no other module depends on audit.

## Event handling

- `internal.application.ShipmentAuditListener` has two `@ApplicationModuleListener` methods,
  `on(ShipmentBooked event)` and `on(ShipmentCancelled event)`. Each runs asynchronously after the publishing
  transaction commits, in its own transaction.
- Each method calls `tenantScope.runAs(event.tenantId(), () -> recordAuditEntry.handle(...))`.
- `RecordAuditEntry.handle(AuditEntry)` calls the `AuditStore` port, whose JDBC adapter runs
  `insert into audit.audit_entry (...) values (...) on conflict (tenant_id, event_id) do nothing`.
- A second delivery of the same event therefore has no effect. The listener returns normally and the registry marks
  the publication complete.
- Any exception propagates. The transaction rolls back and the publication stays incomplete until it is resubmitted
  on restart or through `IncompleteEventPublications`.

## Domain model: `internal.domain`

- `AuditEntry(TenantId tenantId, UUID eventId, AuditEventType type, String subjectType, UUID subjectId,
  Instant occurredAt, Instant recordedAt)`. `subjectType` is `SHIPMENT` in v0.1. `recordedAt` comes from the
  injected clock.
- `AuditEventType`: `SHIPMENT_BOOKED`, `SHIPMENT_CANCELLED`.

## Data: schema `audit` (migration `V4__audit.sql`)

`audit.audit_entry`: `tenant_id uuid`, `event_id uuid`, `event_type text` (enum check), `subject_type text`
(`SHIPMENT`), `subject_id uuid`, `occurred_at timestamptz`, `recorded_at timestamptz`, all `not null`.

- PK `(tenant_id, event_id)`. This key is the deduplication.
- Row-level security enabled and forced, policy `tenant_isolation`. A write without the event's tenant bound is
  rejected by the `with check` clause.
- Grants to `shipping_app`: `SELECT, INSERT` only, so the runtime role cannot update or delete audit entries.

## Invariants and the tests that assert them

| # | Invariant | Tests |
|---|---|---|
| A1 | A booking produces one audit entry with the booking's tenant, event ID and shipment ID; a cancellation produces a second entry. | `ShipmentAuditIntegrationTest` |
| A2 | Delivering the same event twice leaves one entry. | `AuditDeduplicationTest` |
| A3 | If the audit write fails once, the publication stays incomplete; after a restart against the same database it is replayed; exactly one entry exists; the publication is complete. | `AuditReplayIntegrationTest` |
| A4 | The listener cannot write under the wrong tenant or without a tenant. | `RowLevelSecurityIntegrationTest` |
| A5 | The runtime role cannot update or delete audit entries. | `RowLevelSecurityCatalogTest` |

The static check proves only that audit references `shipments.api`. A listener that compiles but never writes still
passes it. The integration tests above are to catch that case. A demonstration of exactly this limit with a
deliberately broken listener is planned as the library's slice 10 (`libs/c4-contract-kit/AGENTS.md`); like the
tests, it does not exist yet.

## Risk levels of its paths (`policy/risk.json`)

| Path | Level | Rule |
|---|---|---|
| `apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/audit/**` | 2 | `audit` |
| `apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/audit/**` | 2 | `audit` |
| changed lines with SQL, `TenantScope`, `set_config` or `@ApplicationModuleListener` | 3 | behaviour signals `sql-change`, `tenant-and-authentication-symbols`, `transaction-and-event-semantics` |

## Capabilities

`audit.record-shipment-events`, `audit.idempotent-consumer`, `audit.replay-after-failure`
(`docs/capabilities/capabilities.yaml`).
