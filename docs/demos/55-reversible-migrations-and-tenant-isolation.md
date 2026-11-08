---
title: "Tenants and quotes have paired up and down migrations"
description: "The first schemas define tenant isolation and reversible structure; database execution evidence for this merged tree is pending CI."
status: accepted
updated: 2026-11-08
pr: 55
kind: feature
recorded: 2026-11-08
tests: [MigrationFilesTest, DatabaseServerTest, ExternalImportPolicyTest]
---

## What changed

The tenants and quotes schemas now have migrations V1 and V2, each with a down script. The runtime database role
has restricted grants, and tenant-owned tables declare forced row-level security. The new database tests exercise
up/down/up catalog equality, runtime-role DDL refusal and isolation between synthetic tenants Alpha and Beta.
Their execution against the merged tree is pending CI.

All four database test classes carry the database tag. They use the reviewed harness's JDBC handle, and CI runs
them only inside its reviewed engine window. The normal local check needs only the reviewed JDK.

## See it

Read [the migration decision](../adr/0016-down-scripts-proven-in-ci-production-rolls-forward.md), the
[tenants schema](../modules/tenants.md)
and [quotes module](../modules/quotes.md). The decision explains why deployed databases only roll forward.

## Try it

From the repository root, with the reviewed JDK 25.0.5+8 first on PATH, run the checks below. The SQL files and the
synthetic JDBC URL fixtures are already in the repository. These checks require no database fixture or cleanup;
Gradle writes its usual build outputs.

```sh
./gradlew check
```

CI separately runs the databaseTest task in its reviewed PostgreSQL harness window. Do not start Docker locally
for this demonstration. There is no local database-free substitute for PostgreSQL's RLS or catalog behavior.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-08, on macOS with the reviewed JDK 25.0.5+8, a targeted Gradle run selected DatabaseServerTest,
ExternalImportPolicyTest and MigrationFilesTest. It exited 0. The excerpt below omits task setup and cache output:

```text
> Task :apps:shipping-api:test

BUILD SUCCESSFUL in 4s
14 actionable tasks: 3 executed, 11 up-to-date
```

This recording proves only that the JDBC URL adapter, external import boundaries and SQL file-pair checks passed.
It records no PostgreSQL execution. CI output for MigrationsIntegrationTest, MigrationRoundTripTest,
RowLevelSecurityCatalogTest and RowLevelSecurityIntegrationTest is pending; no passing up/down migration or RLS
output is claimed. Pull request #55 is the review reference.

The tests named in this page's metadata are separately evaluated from the publishing build's reports. They do
not promote the pending database run to passing evidence.

## Limits

The database tests must still pass in CI for this merged tree. The round trip compares catalogs in empty databases,
not recovery of populated data, and covers only the object kinds listed by CatalogSnapshot. Plain JDBC tenant
binding does not prove application transaction binding, HTTP tenant denial or event-listener isolation. The
application's JDBC adapters, shipments and audit schemas and demo seeds remain planned. No Docker or mutation
run was performed locally.
