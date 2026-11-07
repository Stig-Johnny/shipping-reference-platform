---
title: "ADR 0005: PostgreSQL with Spring Data JDBC, Flyway and forced row-level security"
description: "PostgreSQL 18.6, one schema per module, Spring Data JDBC, Flyway migrations under a separate role, and forced row-level security for tenant isolation. Amended 2026-11-07: v0.1 adapters use Spring JDBC's JdbcClient, and table ownership is shown from the SQL they run."
status: accepted
updated: 2026-11-07
date: 2026-11-06
decision-makers: repository owner
enforcedBy: [not enforced yet]
plannedEnforcement:
  - "java: RowLevelSecurityCatalogTest, RowLevelSecurityIntegrationTest, CrossTenantAccessTest"
  - "java: MigrationsIntegrationTest, and TableOwnershipTest on the SQL of each module's adapters (amendment 2026-11-07)"
---

# ADR 0005: PostgreSQL with Spring Data JDBC, Flyway and forced row-level security

Amended on 2026-11-07: see
[Amendment 2026-11-07](#amendment-2026-11-07-spring-jdbc-adapters-and-sql-schema-ownership-evidence). The sections
before the amendment are the decision as accepted on 2026-11-06, unchanged.

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

## Amendment 2026-11-07: Spring JDBC adapters and SQL schema-ownership evidence

- **Persistence.** In v0.1 the adapters use Spring JDBC: `JdbcClient` from `spring-jdbc` 7.0.10, auto-configured
  through `spring-boot-starter-jdbc` 4.1.2. Both are reviewed and already in the API's build, and their review
  conditions bind the adapters (`security/reviews/`). Spring Data JDBC is not in the build: adding it would need a
  dependency review and a maintenance lock pull request (ADR 0007), so v0.1 does without it (ADR 0003, amendment of
  the same date).
- **SQL.** Each module's SQL is written by hand in its `internal.adapter.out.jdbc` package. Every value is a bound
  parameter; no statement is assembled from input. The predicate rule above holds for every statement: each one names
  the tenant (`where tenant_id = :tenant`). Optimistic locking, where a table has a `version` column, is a `version`
  check in the update's own SQL.
- **Table ownership.** "A module reads and writes only its own schema" stands, and it needs evidence that can fail.
  With no `@Table` mappings, the check in the Confirmation above would scan nothing and pass: an empty `@Table` scan
  proves nothing. `TableOwnershipTest` instead shows ownership from the SQL: every statement of a module's adapters
  touches only tables of that module's schema. One way is a test that reads each statement and requires every table
  it names to be qualified with the module's schema; another is a check that asks PostgreSQL which relations each
  statement uses and looks up their schemas in the catalog. Either way, a fixture in which an audit adapter statement
  reads `shipments.shipment` must fail it.
- **Unchanged:** forced row-level security with the `tenant_isolation` policy, the two roles `shipping_owner` and
  `shipping_app`, and the transaction-local tenant binding: `TenantBindingTransactionManager` runs
  `set_config('app.tenant_id', ?, true)` at the start of every transaction. Inside a transaction, `JdbcClient` runs
  its statements on that transaction's connection, so the binding applies to them.
- **Consequences.** Good, because v0.1 needs no new dependency, and every statement is SQL that reviewers read and
  that the level 3 signal for changed SQL covers. Bad, because mapping rows to records is hand-written code that tests
  must cover. The consequence above that `TableOwnershipTest` checks mapped tables and not hand-written SQL strings no
  longer describes the plan: it checks the SQL. The database still does not stop one module's SQL from reading another
  module's schema, because one runtime role serves all modules; the test is the control.
- **Confirmation.** `TableOwnershipTest` asserts that every statement of each module's adapters touches only that
  module's schema, and fails on the fixture above. The other confirmations are unchanged. Before this amendment, the
  planned enforcement read "java: MigrationsIntegrationTest, TableOwnershipTest".

## More Information

- PostgreSQL row security: <https://www.postgresql.org/docs/current/ddl-rowsecurity.html>
- Module data details: `docs/modules/tenants.md`, `quotes.md`, `shipments.md`, `audit.md`.
