---
title: "The API's comments say what is built"
description: "A Java reader who follows the walkthrough's links into the code finds comments that agree with it: built parts are no longer called planned, and what is still planned is named."
status: accepted
updated: 2026-11-16
pr: 131
kind: docs
recorded: 2026-11-16
tests: []
---

## What changed

Before, the Java architecture walkthrough linked readers into code whose comments still called built parts planned.
The quotes module's package comment said its database adapter was planned, `TrackingNumbers` named a planned JDBC
adapter, and test comments sent readers to a planned `QuotesModuleIntegrationTest` and a planned HTTP filter. All of
them exist: `JdbcQuoteStore`, `JdbcShipmentStore`, `JdbcTrackingNumbers` and `JdbcMembershipDirectory` under the
`postgres` profile, `DemoTokenAuthenticationFilter`, which binds the member's tenant for each request, and the
integration tests. Two comments named a planned `TenantBindingTransactionManager`, which ADR 0017 replaced with a
binding before every statement.

Now those comments say what exists, and what is still planned is named: cancellation, durable event publication,
event `TenantScope`, prototype flags, the audit, orders, tracking and returns modules, and
`ShipmentEventsIntegrationTest`. Only comments, Javadoc and Markdown changed.

Three wording items from the final review of pull request #129 are fixed too: demo X-129 no longer says the
Trust-ledger check came after the first review, a `SitePagesTest` comment names the README's two controls, and the
site design doc's README paragraph wraps at 120 columns. The design doc also says what the walkthrough's status rows
show.

## See it

- [How this site is drawn](../ui/documentation-site.md): the paragraph on the Java reading path says what the
  walkthrough's status rows show; the README paragraph above it is rewrapped.
- [Demo X-129](129-readme-application-first.md): the sentence after its evidence excerpt.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-16 on macOS with Temurin 25.0.5+8. `git grep -c -i planned -- apps/shipping-api` counted 31 lines in
25 files on main (`bb40654`) and 17 lines in 12 files after the change. The run after the change printed:

```text
apps/shipping-api/AGENTS.md:2
apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/internal/domain/ShipmentStatus.java:1
apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/shipments/package-info.java:1
apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/adapter/in/web/MeController.java:1
apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/internal/application/ThreadTenantContext.java:1
apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/tenants/package-info.java:1
apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/architecture/ArchitectureConformanceTest.java:4
apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/RowLevelSecurityIntegrationTest.java:1
apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/quotes/internal/application/CreateQuoteTest.java:1
apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/scenarios/support/Comparison.java:2
apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/shipments/internal/application/BookShipmentTest.java:1
apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/shipments/internal/domain/ShipmentTest.java:1
```

Each remaining line names a part that is not built: the planned modules (`AGENTS.md`,
`ArchitectureConformanceTest`), cancellation (`ShipmentStatus`, the shipments package, `ShipmentTest`), durable event
publication (the shipments package, `CreateQuoteTest`, `BookShipmentTest`), event `TenantScope` (`ThreadTenantContext`,
the tenants package, `RowLevelSecurityIntegrationTest`), prototype flags (`MeController`, the tenants package), or a
planned module in general (`Comparison`, which reports the steps that touch one).

Each changed comment was checked against the code: the adapters' `postgres` profile, the filter's thread binding, the
integration tests' assertions, ADR 0017 and the capability file. `java tools/policy/PolicyCheck.java .`,
`./gradlew check :tools:docs:checkGenerated` and `./gradlew :tools:docs:test --rerun` exited 0: 762 documentation
tests passed, and the 22 generated files were up to date. Review: pull request #131.

## Limits

No test reads comments, so nothing keeps them current, and a later change can make one stale again. Capability
statuses are unchanged: `tenants.row-level-security`, for example, stays planned, because the audit schema and event
`TenantScope` are planned. Only the changed comments were re-read; the wording of other documents was not swept.
