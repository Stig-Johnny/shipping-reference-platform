---
title: "Module: tenants"
description: "Demo authentication for synthetic users, tenant derivation from membership, tenant binding for row-level security, and tenant-scoped prototype flags."
status: accepted
updated: 2026-11-09
module: tenants
moduleStatus: built
riskLevel: 3
---

# Module: tenants

Package `io.github.stigjohnny.shipping.tenants`. C4 component `tenants` (`contract.status built`). Risk level 3:
authentication and tenant isolation. A minimal supporting slice: just enough identity to prove isolation.

## Implementation state

Built:

- `TenantId`, `UserId`, `CurrentTenant` and `MissingTenantContextException` in `tenants.api`.
- `ThreadTenantContext` in `internal.application`. It binds a tenant to the current thread for one unit of work with a
  `ScopedValue`, so the binding ends when the work returns or throws and the previous one applies again, and
  independently started threads do not inherit the binding (`ThreadTenantContextTest`); child tasks forked through
  `StructuredTaskScope` can inherit it. The quotes module takes its tenant from `CurrentTenant`.
- Demo authentication over HTTP. `DemoToken` checks the format and computes the SHA-256 (`DemoTokenTest`).
  `ResolveMembership` asks the `MembershipDirectory` port by that hash only. Spring Security's chain and
  `DemoTokenAuthenticationFilter` authenticate the bearer token and bind the member's tenant for the rest of the
  request; `GET /api/v1/me` answers from the authenticated membership (`DemoTokenAuthenticationTest`,
  `TenantsHttpContractTest`).

The HTTP tests start the whole application with an in-memory directory. `JdbcMembershipDirectory` now implements
the port under the `postgres` profile. `MembershipDirectoryIntegrationTest` shares its exact membership contract
with `InMemoryStoresTest`: Alpha, Beta, a known user without a membership and an unknown token. The database run
is pending CI, so the two authentication capabilities remain partial and their HTTP evidence proves no SQL execution.

In the scenario coverage fixtures, `ReachabilityTest` shares one raw `ThreadTenantContext` between tenant-owned and
quote-owned roots, directly and through shared containers, and requires a bypass gap in both traversal orders.
Visits track object identity and owning module. This checks boundary snapshots, not every runtime reference
([known gaps](../gaps.md#6-tests-and-quality-gates)). Scenario configurations use `@TestConfiguration` and are wired
explicitly, so the HTTP tests do not discover them through application component scanning.
Both scenarios retain the full-main census. Their expected report is `COVERAGE_INCOMPLETE`, "coverage incomplete:
215 known gaps". The exact ledger includes the Spring authentication/security
accesses, 94 distinct JDBC findings and six quotes and shipments factory parameters; each is classified and tagged
"phase 3 / option 2". Ambient security state and framework callbacks remain unproved.
Only exact reviewed debt can pass the build; new, changed or disappeared findings require an explicit ledger update
(ADR 0013). Separate HTTP tests do not make scenario HTTP exchanges observed.

Built: `JdbcMembershipDirectory` calls `tenants.resolve_demo_token`. It sets an empty transaction-local tenant
before this pre-authentication query, since the token lookup is what discovers the tenant (ADR 0017).
Built: the demo runtime defaults to synthetic memory identity; `demo,postgres` uses the JDBC directory with standard datasource environment variables and an operator-prepared seed. Planned: `TenantScope` and prototype flags; until then every flag is off and `enabledFlags` is empty.
The V1 schema, role bootstrap and database RLS tests exist; their execution against the merged tree is pending CI.
Until that evidence exists, `tenants.row-level-security` remains planned.
`tenants.cross-tenant-denial`, `tenants.tenant-scope-for-events` and
`tenants.prototype-flags` are planned.

**Scenario coverage ledger:** the owner accepted 15 exact demo findings for issue #92, bringing the ledger to
215 distinct findings: 115 HTTP/security accesses, 94 JDBC accesses and six factory parameters. ADR 0013 records
the acceptance. Existing classifications and the census, matching, duplicate and bypass checks are unchanged.
Every finding remains a coverage refusal; the expected verdict is `COVERAGE_INCOMPLETE`.

## Responsibilities

- Authenticate each API request with a demo bearer token (demo profile only).
- Derive the tenant from the authenticated user's membership. Never from a header, path, query or body.
- Bind the tenant to every database transaction so PostgreSQL row-level security applies (ADR 0012).
- Offer `TenantScope` so asynchronous work (event listeners) runs in the event's tenant.
- Evaluate server-side, tenant-scoped prototype flags (default off, with owner and expiry).
- Serve `GET /api/v1/me`.

Not its job in v0.1: tenant administration, user management, roles beyond `CLERK`, production identity.

## Relationships (from `architecture/workspace.dsl`)

| Direction | Other component | Mode |
|---|---|---|
| quotes -> tenants | quotes | REQUIRED |
| shipments -> tenants | shipments | REQUIRED |
| audit -> tenants | audit | REQUIRED |

tenants depends on no other module.

## Public API: `io.github.stigjohnny.shipping.tenants.api`

| Type | Shape | Semantics |
|---|---|---|
| `TenantId` | `record TenantId(UUID value)` | |
| `UserId` | `record UserId(UUID value)` | |
| `CurrentTenant` | `interface { TenantId require(); }` | The tenant bound to the current thread. Throws `MissingTenantContextException` when none is bound; that is a programming error, because the HTTP filter binds a tenant before any controller runs. |
| `TenantScope` | `interface { <T> T callAs(TenantId tenant, Supplier<T> work); void runAs(TenantId tenant, Runnable work); }` | Binds `tenant` to the current thread for the duration of `work` and restores the previous binding afterwards, also on exceptions. If a transaction is already active, it first applies `set_config('app.tenant_id', tenant, true)` to that transaction's connection. Binding a different tenant while a transaction with another tenant is active throws `IllegalStateException`. |
| `MissingTenantContextException` | `final class extends RuntimeException` | |

## HTTP operations and events

- `getCurrentMembership`: `GET /api/v1/me`, `200` `Membership`; `401 unauthenticated`, `403 no-tenant-membership`.
- The `401` and `403` problems for every other operation also come from this module's filter.
- Events: none published, none consumed. `TenantScope` is how other modules' listeners run in an event's tenant.

## Demo authentication

Scheme `demoBearer` in the contract: `Authorization: Bearer <token>`.

| User handle | User ID | Display name | Membership | Token |
|---|---|---|---|---|
| `clerk-alpha` | `00000000-0000-4000-8000-0000000000a1` | Clerk Alpha (synthetic) | Tenant Alpha, `CLERK` | `demo-clerk-alpha` |
| `clerk-beta` | `00000000-0000-4000-8000-0000000000b1` | Clerk Beta (synthetic) | Tenant Beta, `CLERK` | `demo-clerk-beta` |
| `no-tenant-user` | `00000000-0000-4000-8000-0000000000c1` | User without tenant (synthetic) | none | `demo-no-tenant-user` |

| Tenant | Tenant ID | Display name |
|---|---|---|
| Alpha | `11111111-1111-4111-8111-111111111111` | Tenant Alpha (synthetic) |
| Beta | `22222222-2222-4222-8222-222222222222` | Tenant Beta (synthetic) |

These tokens are public, guessable test fixtures, not secrets. Rules:

- Token pattern `^demo-[a-z0-9-]{3,40}$`. The database stores only `sha256(token)`; the seed computes it with
  PostgreSQL's `sha256(convert_to('<token>', 'UTF8'))`, so no hash literals are committed.
- Missing header, wrong scheme, malformed token or unknown token: `401 unauthenticated` with
  `WWW-Authenticate: Bearer realm="shipping-reference-platform"`.
- Known user without membership: `403 no-tenant-membership`.
- Tenant headers and query hints never affect membership. Unknown JSON members, including tenantId, produce 400
  validation-failed, consistent with OpenAPI. DemoTokenAuthenticationTest verifies both behaviors.
- The demo token filter is part of the security chain only with the `demo` Spring profile. Without it nobody
  authenticates, and every `/api/**` request returns `401 unauthenticated`. Without a configured demo static directory, paths outside `/api` serve nothing: the
  chain denies them, with `401` to anyone unauthenticated, `403` to a user without a tenant and the generic
  `404 not-found` to a member.
- The `demo` chain adds `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; form-action 'self';
  base-uri 'none'` while retaining its existing security headers. `DemoSecurityHeadersTest` checks successful
  membership, unauthenticated and no-membership responses through the HTTP contract helper. The simulated label
  retains its stricter `default-src 'none'; sandbox` policy, checked by `ShipmentsHttpContractTest`.
  `DemoStaticController` serves the configured immutable build directory outside `/api`, with normalized/real-path
  containment. `DemoSmokeTest` proves static content types, CSP, traversal and escaping-symlink refusal plus the
  full shipment flow over real HTTP. See the [attended runbook](../flows/attended-demo.md) and
  [ADR 0018](../adr/0018-attended-demo-runtime.md). PostgreSQL smoke execution awaits CI.
- A request that Spring Security's firewall refuses, such as a path with `;` or an encoded `/` or a method the
  server does not accept, is `400 malformed-request` before it is authenticated: the module's
  `RequestRejectedHandler` hands the rejection to the MVC exception resolvers, and the HTTP edge writes the problem,
  with the correlation ID and `Cache-Control: no-store`.
- Spring Boot's generated user never exists, because the module defines its own `AuthenticationManager`
  (`security/reviews/maven--spring-security--7.1.2.md`, condition 3). `DemoTokenAuthenticationTest` checks the beans
  and the startup log of the application with and without the `demo` profile, and the `java` workflow fails when
  "Using generated security password" appears in the build's output or in any test result or test report.
  `tools/ci/check-generated-password.sh` also refuses missing output, failed file discovery and read errors;
  `check-generated-password-test.sh` proves these cases with synthetic fixtures in the same workflow.
- Limits are stated in the contract's `demoBearer` description and in `docs/gaps.md`.

## Request and transaction flow

1. `CorrelationIdFilter` (isolated root class, highest precedence) sets the correlation ID.
2. Spring Security's chain (stateless; CSRF protection disabled because the API uses no cookies and accepts only
   bearer tokens) runs `DemoTokenAuthenticationFilter`. It hands the token to the module's `AuthenticationManager`,
   whose `DemoTokenAuthenticationProvider` resolves it through `ResolveMembership`: the SHA-256 goes to the
   `MembershipDirectory` port, whose JDBC adapter calls `tenants.resolve_demo_token`. The filter sets the
   `SecurityContext` with a `TenantPrincipal(Membership)` and runs the rest of the request inside
   `ThreadTenantContext.runAs(tenant, ...)`, so the binding ends when the request returns or throws. A member has the
   authority `TENANT_MEMBER`, which every `/api/**` path requires; a user without a tenant has none and gets `403`.
   The entry point and the access-denied handler hand their exceptions to the MVC exception resolvers, where
   `AuthenticationProblems` writes the `401` and `403` problems.
3. Under `postgres`, Spring JDBC transactions enclose adapter calls. Each adapter sets `app.tenant_id` locally
   before every SQL statement, using the tenant supplied by its application port; authentication sets it empty.
   The setting vanishes on commit or rollback. ADR 0017 replaces the planned custom transaction manager for these
   synchronous adapters; event `TenantScope` remains planned.
4. Listeners call `TenantScope.runAs(event.tenantId(), ...)` inside their transaction.

## Prototype flags

Server-evaluated, tenant-scoped, default off. A flag never changes authorization or the API's behaviour; it only
tells the web app what to show (`Membership.enabledFlags`).

| Key | Owner | Expires on | Purpose | Risk |
|---|---|---|---|---|
| `label-preview` | repository owner (see `.github/CODEOWNERS`) | 2027-03-31 | Inline preview of the simulated label in the shipment drawer. | 1 (UI) |

- Definitions live in code (`internal.domain.FlagDefinitions`): key, owner, expiry date, purpose. Per-tenant
  settings live in `tenants.prototype_flag`. A flag is on only if its definition exists, has not expired at the
  injected clock's date, and the tenant's row has `enabled = true`.
- Demo seed: on for Tenant Alpha, absent (off) for Tenant Beta, so both states can be shown.
- `PrototypeFlagExpiryTest` fails the build once any definition's expiry date has passed on the real calendar. This
  deliberate time limit forces removal or a reviewed extension.

## Package contents

Items marked built exist; the rest is planned.

- `internal.domain`: `Membership(UserId, TenantId, MembershipRole, String userDisplayName, String tenantDisplayName)`,
  `MembershipRole` (`CLERK`) and `DemoToken` (pattern, `sha256()`), built; `FlagKey`
  (`LABEL_PREVIEW("label-preview")`), `FlagDefinition(FlagKey, String owner, LocalDate expiresOn, String purpose)`,
  `FlagDefinitions`.
- `internal.application`: `ResolveMembership` (`MembershipResolution`: sealed `Member`, `UnknownToken`,
  `NoMembership`), the port `MembershipDirectory` and `ThreadTenantContext implements CurrentTenant`, built;
  `TransactionalTenantScope implements TenantScope`, `PrototypeFlags`, `GetCurrentMembership` (`MeController` reads
  the membership from the principal until flags exist), the port `FlagSettings`.
- `internal.adapter.in.web`, built: `SecurityConfiguration` (the chain, the module's beans and its
  `AuthenticationManager`, so Spring Boot never generates a user), `DemoTokenAuthenticationFilter`,
  `DemoTokenAuthenticationProvider`, `DemoTokenAuthentication`, `TenantPrincipal`, `MeController`
  (`getCurrentMembership`) and `AuthenticationProblems`, which maps `unauthenticated` and `no-tenant-membership`.
- `internal.adapter.out.jdbc`: `JdbcMembershipDirectory` is built (calls the function); `JdbcFlagSettings` is planned.

## Data: schema `tenants` (migration `V1__tenants.sql`; seed `db/demo/synthetic-tenants.sql`)

`V1__tenants.sql` exists in `apps/shipping-api/src/main/resources/db/migration`, with its down script
`db/down/V1__tenants.down.sql` (ADR 0016). The opt-in seed now supplies the three documented users and two memberships; it is applied separately as the owner before launching `demo,postgres`. No prototype flags are seeded. Every column is `not null`.

| Table | Columns | Notes |
|---|---|---|
| `tenants.tenant` | `tenant_id uuid pk`, `display_name varchar(70)` (not empty) | Identity table: no RLS; `shipping_app` has no privileges. |
| `tenants.app_user` | `user_id uuid pk`, `handle varchar(40) unique` (pattern `^[a-z0-9-]{3,40}$`), `display_name varchar(70)` (not empty) | Identity table: no RLS; no privileges. |
| `tenants.membership` | `user_id uuid pk` (one membership per user in v0.1), `tenant_id uuid`, `role text` (`CLERK`) | Identity table: no RLS; no privileges. FKs to `tenant` and `app_user`. |
| `tenants.demo_token` | `token_sha256 bytea pk` (32 bytes), `user_id uuid` | Identity table: no RLS; no privileges. FK to `app_user`. |
| `tenants.prototype_flag` | `tenant_id uuid`, `flag_key varchar(40)`, `enabled boolean default false` | PK `(tenant_id, flag_key)`; FK `tenant_id` to `tenant`; RLS enabled and forced; `shipping_app`: `SELECT`. |

Function `tenants.resolve_demo_token(p_token_sha256 bytea)` returns
`table (user_id uuid, user_display_name varchar, tenant_id uuid, tenant_display_name varchar, role text)`:
`language sql stable security definer set search_path = pg_catalog, tenants`, owned by `shipping_owner`; zero rows
for an unknown token; one row with null tenant columns for a user without membership. `EXECUTE` is revoked from
`PUBLIC` and granted to `shipping_app` only.

Seed note: Flyway runs as `shipping_owner`, which forced RLS also restricts. Seed scripts that insert tenant-owned
rows (`prototype_flag`, the expired quote fixture) first run `select set_config('app.tenant_id', '<tenant>', true)`
inside the migration's transaction.

Database roles (created by `apps/shipping-api/postgres/init-roles.sql`, not by Flyway): `shipping_owner` (login,
owns everything, used only by Flyway) and `shipping_app` (login, `NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`,
owns nothing). Both are also `NOINHERIT` and `NOREPLICATION`, and neither is a member of any role. A superuser runs
the script through JDBC in each database before its first migration; running it again is safe. It also revokes
`CONNECT` and `TEMPORARY` on the database from PUBLIC, grants `CONNECT` to both roles and `CREATE` (for schemas) to
`shipping_owner` only, and sets no passwords. Flyway keeps its history in schema `flyway`, on which `shipping_app` has
no privileges.

## Invariants and the tests that assert them

| # | Invariant | Tests |
|---|---|---|
| T1 | Requests without a valid token get `401`; a user without membership gets `403`; header/query tenant hints cannot override membership; tenantId body members are rejected. | `DemoTokenAuthenticationTest` |
| T2 | Token format and hashing are exact. | `DemoTokenTest` |
| T3 | `TenantScope` binds, restores after exceptions and refuses to switch tenants inside a transaction. | `TenantScopeTest` |
| T4 | Every tenant-owned table has RLS enabled, forced and a policy; identity tables are unreadable by `shipping_app`; `shipping_app` lacks `BYPASSRLS`, superuser and ownership. | `RowLevelSecurityCatalogTest` |
| T5 | With plain JDBC as `shipping_app`: a foreign tenant sees and changes nothing; no bound tenant sees nothing and cannot insert; a reused pooled connection (pool size 1) carries no tenant into the next transaction; an asynchronous listener writes only under the event's tenant. | `RowLevelSecurityIntegrationTest` |
| T6 | Tenant Beta cannot read, book, cancel or download labels for Tenant Alpha's quotes and shipments over HTTP (always `404`). | `CrossTenantAccessTest` |
| T7 | Flags are off by default, per tenant, off after expiry, and never change authorization. | `PrototypeFlagsTest` |
| T8 | No flag definition is past its expiry date. | `PrototypeFlagExpiryTest` |
| T9 | `GET /api/v1/me` matches the contract for `200`, `401` and `403`. | `TenantsHttpContractTest` |

## Risk levels of its paths (`policy/risk.json`)

| Path | Level | Rule |
|---|---|---|
| `apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/**` | 3 | `tenant-isolation-and-authentication` |
| `apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/tenants/**` | 3 | `tenant-isolation-and-authentication` |
| `apps/shipping-api/src/main/resources/db/**`, `apps/shipping-api/postgres/**` | 3 | `migrations` |

## Capabilities

`tenants.demo-authentication`, `tenants.current-membership`, `tenants.row-level-security`,
`tenants.cross-tenant-denial`, `tenants.tenant-scope-for-events`, `tenants.prototype-flags`
(`docs/capabilities/capabilities.yaml`).
