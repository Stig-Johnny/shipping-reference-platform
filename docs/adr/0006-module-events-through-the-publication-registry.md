---
title: "ADR 0006: Module events through the publication registry, with idempotent consumers"
description: "Modules publish small event records transactionally; Spring Modulith's JDBC publication registry redelivers after failures; consumers deduplicate by event ID."
status: accepted
updated: 2026-11-06
date: 2026-11-06
decision-makers: repository owner
enforcedBy: [not enforced yet]
plannedEnforcement:
  - "java: ShipmentEventsIntegrationTest"
  - "java: ShipmentAuditIntegrationTest, AuditDeduplicationTest, AuditReplayIntegrationTest"
  - "java: ArchitectureConformanceTest (audit -> shipments is REQUIRED)"
---

# ADR 0006: Module events through the publication registry, with idempotent consumers

## Context and Problem Statement

Audit must record every booking and cancellation, but shipments must not depend on audit, and a failing audit write
must not roll back a booking the clerk has already seen succeed. Delivery must survive a crash between the booking
commit and the audit write. How do modules react to each other's state changes?

## Decision Drivers

- The publisher stays unaware of its consumers.
- No lost events after a committed state change; recovery after failure and restart is demonstrable.
- Honest semantics: persistence of publications does not make effects exactly-once.

## Considered Options

1. Spring application events with Spring Modulith's JDBC event publication registry and idempotent consumers.
2. Direct synchronous calls from shipments into an audit API.
3. An external message broker with an outbox.

## Decision Outcome

Chosen option 1.

- Events are Java records in the publishing module's `api` package. Payloads are minimal: `UUID eventId`, the
  `TenantId`, the subject's ID and `Instant occurredAt`. No personal data. TenantId from tenants.api is the only
  permitted type from another module. v0.1 events: `quotes.api.QuoteIssued`, `shipments.api.ShipmentBooked`, `shipments.api.ShipmentCancelled`.
- The application service publishes with `ApplicationEventPublisher` inside the transaction that changes state.
  `spring-modulith-starter-jdbc` stores a publication for each transactional listener in the same transaction. If
  the transaction rolls back, nothing is published.
- Listeners are `@ApplicationModuleListener` methods in the consumer's `internal.application` package: they run
  asynchronously after commit, in their own transaction. The publication is marked complete only when the listener
  returns normally. Completion mode is `UPDATE`, so completed publications remain visible as evidence.
- Incomplete publications are resubmitted on restart (`spring.modulith.events.republish-outstanding-events-on-restart=true`)
  and can be resubmitted on demand through `IncompleteEventPublications`.
- Delivery is at least once. Every consumer deduplicates by `eventId` with a unique constraint and
  `insert ... on conflict do nothing`, so a replayed event has no second effect.
- Consumers write tenant data inside `TenantScope.runAs(event.tenantId(), ...)`; the row-level security `with check`
  policy rejects a write without the right tenant, which leaves the publication incomplete for replay.
- Changing an event record is a level 3 change: stored publications must remain deserialisable. Only additive,
  optional members are allowed; anything else is a new event type.

### Consequences

- Good, because shipments compiles and runs without audit; the dependency points from consumer to publisher, which
  the C4 model states as `audit -> shipments` (REQUIRED).
- Good, because the replay demonstration uses the real registry and a real restart.
- Bad, because audit entries appear shortly after the booking, not in the same transaction; a reader can briefly see
  a shipment without its audit entry.
- Bad, because the `event_publication` table is shared infrastructure without row-level security; it holds
  identifiers only.

### Confirmation

- `ShipmentEventsIntegrationTest` (Spring Modulith Scenario API from `spring-modulith-test`): booking publishes exactly one `ShipmentBooked`
  after commit; a forced rollback publishes none; a cancellation publishes `ShipmentCancelled`; an idempotent replay
  publishes nothing.
- `ShipmentAuditIntegrationTest`: a booking produces one audit entry for the booking tenant.
- `AuditDeduplicationTest`: delivering the same event twice leaves one entry.
- `AuditReplayIntegrationTest`: the audit store fails once; the publication stays incomplete; a second application
  context started against the same database resubmits it; exactly one entry exists and the publication is complete.
- `ArchitectureConformanceTest` fails if audit no longer references `shipments.api` (absence of a REQUIRED edge).
  The static check cannot show that the listener writes anything; the tests above do.

## More Information

- Spring Modulith events and registry semantics: <https://docs.spring.io/spring-modulith/reference/events.html>
- Flow and step-to-test mapping: `docs/flows/quote-book-label.md`.
