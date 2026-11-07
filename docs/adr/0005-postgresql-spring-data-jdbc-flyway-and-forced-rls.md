---
title: "ADR 0005: PostgreSQL with Spring Data JDBC, Flyway and forced row-level security"
description: "PostgreSQL 18.6, one schema per module, Spring Data JDBC, Flyway migrations under a separate role, and forced row-level security for tenant isolation."
status: accepted
updated: 2026-11-07
date: 2026-11-06
decision-makers: repository owner
enforcedBy:
  - "java: MigrationsIntegrationTest, RowLevelSecurityCatalogTest, RowLevelSecurityIntegrationTest, against PostgreSQL 18.6 in CI, for the schemas tenants and quotes"
plannedEnforcement:
  - "java: CrossTenantAccessTest, TableOwnershipTest"
  - "java: the application's half of RowLevelSecurityIntegrationTest: TenantBindingTransactionManager over the application's pool, and listeners that write under an event's tenant"
  - "java: the same database tests for the schemas shipments and audit and the event publication registry (V3 to V5)"
---

# ADR 0005: PostgreSQL with Spring Data JDBC, Flyway and forced row-level security

## Context and Problem Statement

Every quote, shipment and audit entry belongs to one tenant. A missing `where tenant_id = ?` in one query must not
expose another tenant's data. Persistence should make SQL and aggregate boundaries visible to reviewers. How are data
stored, migrated and isolated per tenant?

## Decision Drivers

- Tenant isolation that holds even when an application query is wrong.
- Visible SQL and small aggregates instead of lazy-loaded object graphs.
- Tests against the real database engine.

## Considered Options

1. PostgreSQL 18.6 with Spring Data JDBC, Flyway 12.4.0, repository predicates and forced row-level security (RLS).
2. JPA/Hibernate with a tenant filter.
3. Schema-per-tenant or database-per-tenant.

## Decision Outcome

Chosen option 1.

- Storage: PostgreSQL 18.6. Tests use Testcontainers 2.0.5 with the same version, image pinned by digest and
  allowlisted. H2 or other substitutes are not used.
- Persistence: Spring Data JDBC (Boot-managed). One schema per built module, named after the module (`quotes`,
  `shipments`, `tenants`, `audit`). A module reads and writes only its own schema.
- Keys: every tenant-owned table has `tenant_id uuid not null`, a primary key that starts with `tenant_id`, and
  foreign keys that include `tenant_id`, so a row can never reference another tenant's row.
- Predicates: every repository query names the tenant explicitly (`findByTenantIdAndId`, `where tenant_id = :tenant`).
- RLS: every tenant-owned table has `enable row level security`, `force row level security` and a policy
  `tenant_isolation` with `using` and `with check` on
  `tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid`. Without a bound tenant a query sees no rows
  and an insert fails.
- Roles: `shipping_owner` owns the schemas, tables and functions and is used only by Flyway. `shipping_app` is the
  runtime role: not an owner, `NOSUPERUSER`, `NOBYPASSRLS`, and only the grants each table needs (audit entries:
  `INSERT, SELECT` only). Roles are created outside Flyway by `apps/shipping-api/postgres/init-roles.sql`, which
  Testcontainers and the local `compose.yaml` share.
- Tenant binding: the tenants module's transaction manager runs `select set_config('app.tenant_id', ?, true)` at
  the start of every transaction with the bound tenant, or with `''` when none is bound. The setting is
  transaction-local, so a pooled connection never carries a tenant into the next transaction.
- Identity tables (`tenants.tenant`, `tenants.app_user`, `tenants.membership`, `tenants.demo_token`) are needed
  before a tenant is known. They have no RLS; instead `shipping_app` has no privileges on them and reads them only
  through the security-definer function `tenants.resolve_demo_token(bytea)` owned by `shipping_owner`, with a fixed
  `search_path`.
- Migrations: Flyway runs as `shipping_owner` from `classpath:db/migration` (`V1__tenants.sql`, `V2__quotes.sql`,
  `V3__shipments.sql`, `V4__audit.sql`, `V5__event_publication.sql`); the synthetic seed lives in `classpath:db/demo`
  (`V1000__synthetic_tenants.sql`, `V1001__expired_quote_fixture.sql`) and is added only by the `demo` profile and
  tests. Applied migrations are never edited. Later production use requires backward-compatible (expand, then
  contract) migrations.

### Consequences

- Good, because an application bug that forgets a tenant predicate returns nothing instead of another tenant's rows.
- Good, because SQL is explicit and reviewable; changed SQL is a level 3 behaviour signal.
- Bad, because every new tenant table needs its policy, grants and a catalog test update; forgetting the policy is
  caught by `RowLevelSecurityCatalogTest`, not by the compiler.
- Bad, because the event publication table (ADR 0006) is not tenant-scoped; it holds identifiers only.
- Bad, because one runtime role serves all modules, so the database does not enforce that a module only touches its
  own schema; `TableOwnershipTest` checks mapped tables, not hand-written SQL strings.

### Confirmation

- `RowLevelSecurityCatalogTest` queries `pg_class`, `pg_policy` and `pg_roles`: every table with a `tenant_id`
  column has RLS enabled and forced and at least one policy, except the four identity tables, on which `shipping_app`
  has no privileges; `shipping_app` is not superuser, lacks `BYPASSRLS` and owns nothing.
- `RowLevelSecurityIntegrationTest` uses plain JDBC as `shipping_app` (bypassing repository predicates): reads and
  writes with a foreign tenant bound see and change nothing; no bound tenant sees nothing and cannot insert; with a
  pool of size 1, tenant A's transaction followed by tenant B's sees only B's rows.
- `CrossTenantAccessTest` calls the HTTP API with Tenant Beta's token using Tenant Alpha's quote and shipment IDs and
  expects `404` problems for read, book, cancel and label.
- `MigrationsIntegrationTest` migrates an empty database as `shipping_owner` and asserts that `shipping_app` cannot
  run DDL.
- `TableOwnershipTest` asserts that every Spring Data JDBC `@Table` in module `m` uses schema `m`.

## Implementation status (2026-11-07)

- Built: `V1__tenants.sql` and `V2__quotes.sql` in `apps/shipping-api/src/main/resources/db/migration`, each with a
  down script (ADR 0012), and the role bootstrap `apps/shipping-api/postgres/init-roles.sql`. A module's schema can
  exist before its code: the components `tenants` and `quotes` stay planned in the C4 model until their Java classes
  exist.
- The bootstrap runs as a superuser, through JDBC, in each database before its first migration; nothing is mounted as
  an initialisation script. Both roles are also `NOINHERIT`, PUBLIC loses `CONNECT` and `TEMPORARY` on the database,
  and only `shipping_owner` may create schemas in it.
- Flyway runs as `shipping_owner` from `classpath:db/migration` and keeps its history in schema `flyway`, which
  `shipping_app` cannot use.
- Proven in the `java` check against the reviewed PostgreSQL 18.6 image, with plain JDBC: `MigrationsIntegrationTest`,
  `RowLevelSecurityCatalogTest` and the database's half of `RowLevelSecurityIntegrationTest`. Forced row-level security
  restricts what the owner role's queries read and write; it does not stop the owner from changing or dropping a
  policy, which is why only Flyway uses that role and every migration is level 3.
- Not built yet: V3 to V5, the demo seed in `classpath:db/demo`, `compose.yaml`, the Spring configuration that runs
  Flyway at start, `TenantBindingTransactionManager`, `CrossTenantAccessTest` and `TableOwnershipTest`.

## More Information

- PostgreSQL row security: <https://www.postgresql.org/docs/current/ddl-rowsecurity.html>
- Module data details: `docs/modules/tenants.md`, `quotes.md`, `shipments.md`, `audit.md`.
