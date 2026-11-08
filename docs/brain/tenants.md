---
title: "Brain: tenants"
description: "Generated summary of module tenants for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-08
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/tenants.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: tenants

Authenticates synthetic users, derives the tenant from membership, binds it to every database transaction for row-level security, and evaluates prototype flags.

- **Status in the model:** built (`contract.status`).
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
| `tenants.demo-authentication` | partial | DemoTokenTest, DemoTokenAuthenticationTest | DemoTokenTest, DemoTokenAuthenticationTest |
| `tenants.current-membership` | partial | TenantsHttpContractTest | TenantsHttpContractTest |
| `tenants.row-level-security` | planned | RowLevelSecurityCatalogTest, RowLevelSecurityIntegrationTest | none yet |
| `tenants.cross-tenant-denial` | planned | CrossTenantAccessTest, tenant-denial.spec.ts | none yet |
| `tenants.tenant-scope-for-events` | planned | TenantScopeTest, RowLevelSecurityIntegrationTest | none yet |
| `tenants.prototype-flags` | planned | PrototypeFlagsTest, PrototypeFlagExpiryTest, prototype-flag.spec.ts | none yet |

## Decisions its specification names

- [ADR 0012: First runnable slice with a plain TypeScript workbench and Spring JDBC](../adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md) (accepted)
- [ADR 0013: Scenario views are checked against recorded interactions](../adr/0013-scenario-views-are-checked-against-recorded-interactions.md) (accepted)
- [ADR 0016: Every migration has a down script, proven in CI; production rolls forward](../adr/0016-down-scripts-proven-in-ci-production-rolls-forward.md) (accepted)

## Flows

- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

- 0. State of this commit: The specification, the reviewed toolchain and the dependency allowlist with its enforcement are in place, and so are the reviewed dependencies of the docs generator and of the shipping API. The docs generator itself, `tools/docs`, exists: it generates the C4 view pages, `BRAIN.md` and the module brains, and builds the documentation site, which `.github/workflows/pages.yml` publishes from `main` after the publication scan. Its own controls, `control.brain-check` and `control.publication-scan`, are shipped with their tests as evidence. The library `libs/c4-contract-kit` (its slices 1 to 4) exists, and its controls carry their tests as evidence. So does its partial web import check, which compares detected imports in `apps/shipping-web` with the web container of the model; every web part is planned and the source and test roots are empty, so no real web imports are checked yet. The first application code exists, checked by `ArchitectureConformanceTest`, `ModularityTest` and `HexagonalLayeringTest`: the `quotes` module with its two HTTP operations, booking, listing, reading and SVG labels over HTTP in the `shipments` module, the `tenants` module's demo authentication with `GET /api/v1/me`, and the HTTP edge. Eleven capabilities have passing tests: two of the quotes module are shipped, and three of it, two of the tenants module and four of the shipments module are partial, and so is the HTTP contract control. Every other capability in `docs/capabilities/capabilities.yaml` is `planned` with no evidence, and every test class named for them is still to be written (red first), except `ShipmentTest`, which exists for booking and does not yet assert cancellation. The shipping API's database harness exists too: in CI, `PostgresHarnessIntegrationTest` starts the reviewed PostgreSQL 18.6 image under the runner review's conditions and queries it. V1 (tenants), V2 (quotes), their down scripts and the role bootstrap now exist. MigrationFilesTest supplies only file-pairing evidence; the merged tree's up/down execution and RLS proof are pending CI, so control.migrations is partial and tenants.row-level-security remains planned. All four database test classes are tagged database and required in the reviewed harness window.
- 0. State of this commit: **The application starts in its tests only, without a database.** The HTTP tests start `ShippingApplication` with test doubles for the ports that have no adapter yet: the tenants module's membership directory, which holds the synthetic users of `docs/modules/tenants.md`, and the quotes and shipments stores and tracking numbers, in memory. They leave out the data source and Spring Modulith's event publication registry, which need PostgreSQL. So they prove the HTTP edge, the problems, and that the tenant comes from the membership only. They do not prove that a token is resolved through `tenants.resolve_demo_token`, that a quote is stored in PostgreSQL, or that the database hides another tenant's quote. No transaction manager exists, so the `@Transactional` of `CreateQuote` and `GetQuote` has no effect, and that `QuoteIssued` is published inside the quote's transaction is unproven until `QuotesModuleIntegrationTest` exists. Outside its tests the application does not start yet: its ports have no production adapters, and the `demo` profile has no database configuration. The scenario checks wire the quotes and tenants classes as beans of a plain Spring context of their own, which is not the application's wiring (section 6).
- 2. Security and identity: **Demo authentication only.** Tokens are public, guessable fixtures (`docs/modules/tenants.md`). No expiry, rotation, revocation, multi-factor authentication, rate limiting or lockout. Local runs use plain HTTP. Production identity (for example OpenID Connect through Spring Security's resource server) is not designed yet.
