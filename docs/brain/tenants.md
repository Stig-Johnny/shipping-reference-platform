---
title: "Brain: tenants"
description: "Generated summary of module tenants for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-06
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/tenants.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: tenants

Authenticates synthetic users, derives the tenant from membership, binds it to every database transaction for row-level security, and evaluates prototype flags.

- **Status in the model:** planned (`contract.status`). No production class may exist in its packages until the status is built.
- **Packages:** `io.github.stigjohnny.shipping.tenants`
- **Risk level:** 3
- **Specification:** [docs/modules/tenants.md](../modules/tenants.md)
- **Overview of all modules:** [BRAIN.md](../../BRAIN.md)

## Dependencies

- **Uses:** none.
- **Used by:**
  - quotes (REQUIRED): Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId) [Java]
  - shipments (REQUIRED): Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId) [Java]
  - audit (REQUIRED): Writes each audit entry inside the event's tenant scope (tenants.api: TenantScope, TenantId) [Java]
- **Other relationships, not checked by the Java check:**
  - from Web app (not checked): Authenticates every API request with the demo bearer token; reads the current membership and prototype flags [JSON over HTTPS]
  - to Database (not checked): Resolves demo tokens through a security-definer function, reads prototype flags, sets app.tenant_id in every transaction [JDBC]

## HTTP operations

- `GET /api/v1/me` `getCurrentMembership`: Return the authenticated user's tenant membership and enabled prototype flags.

## Capabilities

| Capability | Status | Acceptance tests | Evidence |
|---|---|---|---|
| `tenants.demo-authentication` | planned | DemoTokenTest, DemoTokenAuthenticationTest | none yet |
| `tenants.current-membership` | planned | TenantsHttpContractTest | none yet |
| `tenants.row-level-security` | planned | RowLevelSecurityCatalogTest, RowLevelSecurityIntegrationTest | none yet |
| `tenants.cross-tenant-denial` | planned | CrossTenantAccessTest, tenant-denial.spec.ts | none yet |
| `tenants.tenant-scope-for-events` | planned | TenantScopeTest, RowLevelSecurityIntegrationTest | none yet |
| `tenants.prototype-flags` | planned | PrototypeFlagsTest, PrototypeFlagExpiryTest, prototype-flag.spec.ts | none yet |

## Decisions its specification names

- [ADR 0005: PostgreSQL with Spring Data JDBC, Flyway and forced row-level security](../adr/0005-postgresql-spring-data-jdbc-flyway-and-forced-rls.md) (accepted)

## Flows

- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

- 2. Security and identity: **Demo authentication only.** Tokens are public, guessable fixtures (`docs/modules/tenants.md`). No expiry, rotation, revocation, multi-factor authentication, rate limiting or lockout. Local runs use plain HTTP. Production identity (for example OpenID Connect through Spring Security's resource server) is not designed yet.
