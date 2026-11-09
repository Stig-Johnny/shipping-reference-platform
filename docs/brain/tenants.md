---
title: "Brain: tenants"
description: "Generated summary of module tenants for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-09
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/tenants.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: tenants

Authenticates synthetic users and derives the tenant from membership; postgres selects the JDBC token directory; the demo web adapter serves local static assets with strict CSP. Prototype flags and event TenantScope are planned.

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
| `tenants.demo-authentication` | partial | DemoTokenTest, DemoTokenAuthenticationTest, DemoSecurityHeadersTest, DemoSmokeTest, MembershipDirectoryIntegrationTest, DemoPostgresSmokeIntegrationTest | DemoTokenTest, DemoTokenAuthenticationTest, DemoSecurityHeadersTest, DemoSmokeTest |
| `tenants.current-membership` | partial | TenantsHttpContractTest | TenantsHttpContractTest |
| `tenants.row-level-security` | planned | RowLevelSecurityCatalogTest, RowLevelSecurityIntegrationTest | none yet |
| `tenants.cross-tenant-denial` | planned | CrossTenantAccessTest, tenant-denial.spec.ts | none yet |
| `tenants.tenant-scope-for-events` | planned | TenantScopeTest, RowLevelSecurityIntegrationTest | none yet |
| `tenants.prototype-flags` | planned | PrototypeFlagsTest, PrototypeFlagExpiryTest, prototype-flag.spec.ts | none yet |

## Decisions its specification names

- [ADR 0012: First runnable slice with a plain TypeScript workbench and Spring JDBC](../adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md) (accepted)
- [ADR 0013: Scenario views are checked against recorded interactions](../adr/0013-scenario-views-are-checked-against-recorded-interactions.md) (accepted)
- [ADR 0016: Every migration has a down script, proven in CI; production rolls forward](../adr/0016-down-scripts-proven-in-ci-production-rolls-forward.md) (accepted)
- [ADR 0017: Bind JDBC statements locally and serialize booking keys and quotes](../adr/0017-jdbc-transaction-binding-and-booking-locks.md) (accepted)
- [ADR 0018: An attended demo on one origin with selectable storage](../adr/0018-attended-demo-runtime.md) (accepted)
- [ADR 0019: Strict same-origin content security for the workbench](../adr/0019-strict-same-origin-workbench-csp.md) (accepted)

## Flows

- [Run an attended shipping demo](../flows/attended-demo.md)
- [Flow: quote, book and download a simulated label](../flows/quote-book-label.md)

## Gaps that name it

- 0. State of this commit: The specification, the reviewed toolchain and the dependency allowlist with its enforcement are in place, and so are the reviewed dependencies of the docs generator and of the shipping API. The docs generator itself, `tools/docs`, exists: it generates the C4 view pages, `BRAIN.md` and the module brains, and builds the documentation site, which `.github/workflows/pages.yml` publishes from `main` after the publication scan. Its own controls, `control.brain-check` and `control.publication-scan`, are shipped with their tests as evidence. The library `libs/c4-contract-kit` (its slices 1 to 4) exists, and its controls carry their tests as evidence. So does its partial web import check, which compares detected imports in `apps/shipping-web` with the web container of the model; all six web parts are built and all ten REQUIRED relationships have production import evidence. The workbench sources, logic tests and API asset serving exist. Attended browser observations are recorded separately; named browser acceptance tests do not exist. The first application code exists, checked by `ArchitectureConformanceTest`, `ModularityTest` and `HexagonalLayeringTest`: the `quotes` module with its two HTTP operations, booking, listing, reading and SVG labels over HTTP in the `shipments` module, the `tenants` module's demo authentication with `GET /api/v1/me`, and the HTTP edge. Eleven capabilities have passing tests: two of the quotes module are shipped, and three of it, two of the tenants module and four of the shipments module are partial, and so is the HTTP contract control. Every other capability in `docs/capabilities/capabilities.yaml` is `planned` with no evidence, with local evidence still separated from recorded database execution. Cancellation, event and workbench acceptance tests remain planned; `ShipmentTest` exists for booking and does not yet assert cancellation. The shipping API's database harness exists too: in CI, `PostgresHarnessIntegrationTest` starts the reviewed PostgreSQL 18.6 image under the runner review's conditions and queries it. V1 (tenants), V2 (quotes), V3 (shipments), their down scripts and the role bootstrap now exist. MigrationFilesTest supplies local file-pairing evidence. All ten database classes passed in recorded CI evidence (run `42612173809`) (45 tests, zero failures/skips), including up/down and RLS cases. The ordinary local build does not consume those CI reports; generated capability evidence retains that distinction. The migration, RLS and adapter database classes are tagged database and required in the reviewed harness window.
- 2. Security and identity: **Demo authentication only.** Tokens are public, guessable fixtures (`docs/modules/tenants.md`). No expiry, rotation, revocation, multi-factor authentication, rate limiting or lockout. Local runs use plain HTTP. Production identity (for example OpenID Connect through Spring Security's resource server) is not designed yet.
