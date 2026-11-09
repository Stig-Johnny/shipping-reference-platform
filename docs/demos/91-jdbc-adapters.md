---
title: "Quotes and shipments have PostgreSQL storage adapters"
description: "The same storage contracts now target in-memory fixtures and tenant-scoped JDBC adapters, with a paired shipments migration."
status: accepted
updated: 2026-11-09
pr: 91
kind: feature
recorded: 2026-11-09
tests: [InMemoryStoresTest, JdbcProfileTest, MigrationFilesTest, ExternalImportPolicyTest, JdbcTrackingNumbersTest]
---

## What changed

The tenants, quotes and shipments ports now have JDBC implementations selected by the postgres profile. Existing
HTTP tests retain their in-memory ports. Migration V3 adds shipments with forced row-level security, unique tenant
booking keys and a bounded tracking sequence, plus a down script. Booking locks the tenant/key and tenant/quote
inside its transaction so a duplicate can read the winner after waiting.

Review also found that a server using an Arabic formatting locale rejected its own generated tracking numbers.
The formatter now always produces `SIM` followed by ten ASCII digits. A database-free regression exercises padding,
mixed digits and the sequence's upper bound under `ar-EG`.

## See it

Read the [transaction and booking decision](../adr/0017-jdbc-transaction-binding-and-booking-locks.md),
[shipments module](../modules/shipments.md) and [main flow](../flows/quote-book-label.md).
They distinguish implemented adapters from database execution evidence and the still-planned runnable demo.

## Try it

With the reviewed JDK 25.0.5+8 first on PATH, run from the repository root. The synthetic fixtures are in the tree;
these local checks need no database setup or cleanup. Gradle writes its ordinary build outputs.

```sh
./gradlew check
```

The database-tagged classes run separately in CI through ReviewedPostgres. Do not start Docker locally for this
recording. The owner accepted the JDBC findings: the ledger now contains 200 distinct findings (ADR 0013).
The ledger reader accepts the JDBC debt classification; the census and multiplicity checks remain unchanged.
This demonstration belongs to pull request #91, for issue #90.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-09, on macOS with the reviewed JDK 25.0.5+8, a targeted Gradle run selected InMemoryStoresTest,
JdbcProfileTest, MigrationFilesTest and ExternalImportPolicyTest. It exited 0, with 61 tests passed and none skipped.
The excerpt omits task setup and cache output:

```text
> Task :apps:shipping-api:test

BUILD SUCCESSFUL in 5s
14 actionable tasks: 3 executed, 11 up-to-date
```

Before V3 existed, the new MigrationFilesTest case failed, with 5 tests completed and 1 failed. Before adapters
existed, the JDBC fixture failed compilation on their missing classes. No database test was run locally.
Review reference: pull request #91, issue #90. The tests named in this page's metadata count as current-build
evidence only when they pass in the publishing build. The recorded output above remains separate from that evidence.

On 2026-11-09, on the same macOS and JDK, the first JdbcTrackingNumbersTest run exited 1: all four Arabic-locale
cases failed with IllegalArgumentException. After using Locale.ROOT, a targeted run of that class and
ExternalImportPolicyTest exited 0; all 50 tests passed, with no failures or skips. These excerpts omit task setup,
failure details, Gradle guidance and cache output:

```text
4 tests completed, 4 failed
BUILD FAILED in 4s
```

```text
BUILD SUCCESSFUL in 5s
14 actionable tasks: 4 executed, 10 up-to-date
```

## Limits

CI must prove MembershipDirectoryIntegrationTest, QuotesModuleIntegrationTest, ShipmentListingIntegrationTest,
BookingIdempotencyIntegrationTest and the extended migration/RLS classes. The database fixtures explicitly control
transactions; the local profile check asserts annotations and does not prove live Spring proxies. PostgreSQL HTTP
behavior, durable events, cancellation and a runnable demo are not claimed. Catalog round trips do not restore
rows. The runnable demo profile is the next issue.
The locale regression exercises only tracking-number formatting; it does not allocate a database sequence value
or prove a booking transaction commits.
