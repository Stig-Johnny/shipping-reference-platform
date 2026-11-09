---
title: "ADR 0017: Bind JDBC statements locally and serialize booking keys and quotes"
description: "The postgres-profile adapters bind a tenant before every statement; booking holds ordered advisory locks and keeps tenant-scoped unique constraints as its final guard."
status: accepted
updated: 2026-11-09
date: 2026-11-09
decision-makers: repository owner
enforcedBy:
  - "java: ExternalImportPolicyTest, JdbcProfileTest, MigrationFilesTest, InMemoryStoresTest"
plannedEnforcement:
  - "databaseTest: MembershipDirectoryIntegrationTest, QuotesModuleIntegrationTest, ShipmentListingIntegrationTest, BookingIdempotencyIntegrationTest; implemented, CI execution pending"
  - "databaseTest: MigrationRoundTripTest, RowLevelSecurityCatalogTest, RowLevelSecurityIntegrationTest; V3 execution pending CI"
---

# ADR 0017: Bind JDBC statements locally and serialize booking keys and quotes

Refines ADR 0012's transaction-binding design for synchronous JDBC adapters and the booking concurrency plan in
the shipments module. Module boundaries, the HTTP contract, roles and RLS predicates remain unchanged.

## Context and Problem Statement

Issue #90 adds persistence behind three existing ports without adding libraries or building the runnable demo.
The existing in-memory tests must remain the default. The database must isolate tenants independently of WHERE
clauses, and an overlapping duplicate booking must return the committed shipment rather than expose a unique-key error.

## Decision Drivers

- Reuse reviewed Spring JDBC and preserve every module's schema ownership.
- Keep transaction-local tenant settings from leaking through reused connections.
- Preserve booking check order, including a replay after offer expiry.
- Require real PostgreSQL evidence for SQL, locking and RLS; a local test must not impersonate it.

## Considered Options

1. A custom tenant-binding transaction manager and unique-violation recovery in fresh transactions.
2. Per-statement binding inside Spring JDBC transactions, ordered key/quote locks and database unique constraints.

## Decision Outcome

Choose option 2 for the synchronous adapters. Every repository is selected by `@Profile("postgres")` and declares
`@Transactional`. Boot supplies the ordinary JDBC transaction infrastructure when a data source is configured.
The existing HTTP test configuration retains its in-memory ports. Runnable demo configuration and seeds are separate work.

Before every data or lock statement, the adapter executes bound `set_config('app.tenant_id', ..., true)` on the
same transaction connection. The port's tenant comes from the application service; SQL also includes the tenant.
The membership directory is pre-authentication: it binds the empty value and calls only
`tenants.resolve_demo_token(bytea)` to discover the user's membership. It never reads identity tables directly.
Commit and rollback remove the setting. This replaces ADR 0012's custom transaction manager for these adapters;
event TenantScope, including refusal to switch an event transaction's tenant, remains planned.

`BookShipment.handle` is the enclosing transaction, using PostgreSQL's default READ COMMITTED isolation.
`findByKey` acquires `pg_advisory_xact_lock(1, hashtext(tenant || ':' || key))`; `findByQuote` uses namespace 2
and the quote ID. Both locks last to transaction end. The key lookup and fingerprint comparison precede offer
validation; the quote lookup follows recipient validation. A query after a lock is a separate SQL statement so
it gets a snapshot after any wait. Hash collisions serialize unrelated bookings, without changing their outcome.
The protocol acquires the key before the quote and does not acquire another key afterwards.

Unique constraints on tenant/idempotency key and tenant/quote remain the final guard. An insert that bypasses the
booking protocol fails normally on a conflict. The adapter never catches a constraint error and tries to query an
aborted transaction. This avoids the planned fresh-transaction recovery layer for supported booking calls.
The sequence is bounded to the ten-digit tracking number format, does not cycle and may leave gaps on rollback.

### Consequences

The shared store contracts run unchanged against in-memory and JDBC fixtures. Database fixtures use runtime-role
connections and explicit transactions. Concurrency fixtures observe a real advisory-lock wait before releasing
the winner; they cover equal and changed bodies, distinct keys for one quote, replay after expiry and expiry after
rollback. Their compile success is not execution evidence. Live Spring proxy behavior, HTTP persistence, event
publication durability and the runnable demo remain outside these tests.

V3 reserves cancellation and version columns according to the module design. The adapter currently writes BOOKED
only and exposes no update; cancellation behavior is separate work. Down scripts restore catalogs, not deleted data.

### Confirmation

`JdbcProfileTest` checks profile and transaction declarations locally. `InMemoryStoresTest` checks the shared
behaviors without PostgreSQL. The database classes in the metadata must pass in CI through ReviewedPostgres before
any database capability is promoted. `ExternalImportPolicyTest` still rejects every unlisted class use and unused
whitelist entry; entries added for this implementation are exact types only, with one-line justifications.

### Attended runtime follow-up, issue #92

[ADR 0018](0018-attended-demo-runtime.md) now supplies the runnable demo profile, default memory ports,
configured static directory with strict CSP, operator-prepared JDBC mode and synthetic identity seed.
`DemoSmokeTest` passes over real sockets with memory storage; `DemoPostgresSmokeIntegrationTest` compiles and
awaits CI. Durable events and browser behavior remain unproved.
