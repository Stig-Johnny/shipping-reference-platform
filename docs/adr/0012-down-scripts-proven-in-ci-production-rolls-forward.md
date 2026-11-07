---
title: "ADR 0012: Every migration has a down script, proven in CI; production rolls forward"
description: "The reviewed Flyway Community edition has no undo migrations, so each migration has a down script in db/down that CI proves restores the previous catalog exactly; a deployed database is only ever migrated forward."
status: accepted
updated: 2026-11-07
date: 2026-11-07
decision-makers: repository owner
enforcedBy:
  - "java: MigrationRoundTripTest, against PostgreSQL 18.6 in CI: for every migration, its down script restores the previous catalog and the migration the next one"
  - "java: MigrationFilesTest, without a database: one down script per migration, outside Flyway's location"
---

# ADR 0012: Every migration has a down script, proven in CI; production rolls forward

Accepted with the pull request that adds the first two migrations (issue #52), on the owner's approval of its head.

## Context and Problem Statement

Flyway applies migrations forward only. Flyway's documentation describes undo migrations as a feature of its paid
Teams edition, not of the open-source Community edition this repository reviewed. In Flyway 12.4.0, `undo` fails with
"The command 'undo' was not recognized" unless `flyway-proprietary` is on the class path, and the Flyway review forbids
that module (`security/reviews/maven--flyway--12.4.0.md`, condition 4). Without a way back, nothing shows that a
migration does exactly what its author meant and nothing else, and a developer who finds a mistake in a local database
can only rebuild it. How is each migration shown to be reversible, without promising a reversal of a deployed database?

## Decision Drivers

- A reviewer can see each migration's whole footprint, because something removes exactly that footprint and a test
  checks that nothing else changed.
- The proof runs against the real engine in CI, not in someone's head.
- No new dependency and no paid edition.
- A reversal never deletes the data of a deployed database.

## Considered Options

1. A down script per migration in `db/down`, a location Flyway does not scan, proven by a round-trip test against
   PostgreSQL in CI; deployed databases roll forward only.
2. Flyway's undo migrations.
3. Forward-only migrations without down scripts.
4. Down scripts that roll a deployed database back with a release.

## Decision Outcome

Chosen option 1. Option 2 needs a paid edition and a module that the Flyway review forbids. Option 3 leaves a
migration's footprint unchecked. Option 4 deletes data: a down script drops tables and their rows.

- For every `db/migration/V<n>__<description>.sql` there is exactly one `db/down/V<n>__<description>.down.sql`, both
  under `apps/shipping-api/src/main/resources`. It drops what its migration created, in reverse order and without
  `cascade`, so that it fails while something else still depends on it. Neither script controls its own transaction.
- Flyway's only location is `classpath:db/migration`. Flyway keeps its history in schema `flyway`, which no migration
  and no down script touches.
- Reverting migration n, in a development database or in CI: as `shipping_owner`, in one transaction, run its down
  script and delete version n from `flyway.flyway_schema_history`, and only when n is the latest applied migration.
  The next `migrate` applies it again.
- A deployed database (none exists yet) is never migrated down. A mistake is corrected by a new migration, applied
  migrations are never edited, and changes are backward compatible, expand then contract (ADR 0005), so that the
  previous release keeps working on the newer schema: going back means redeploying the previous release, not
  reverting the schema. Flyway's documentation gives the same advice for production: keep the database compatible
  with every deployed version of the code, and rely on a tested backup and restore.

### Consequences

- Good, because each migration's effect on the catalog is written twice, as the migration and as its inverse, and a
  test fails when the two disagree, including a change the migration does not mention, such as a grant on another
  schema, a role attribute or a default privilege.
- Good, because no edition, module or tool is added: the test uses the reviewed Flyway, PostgreSQL JDBC driver and
  Testcontainers.
- Bad, because every migration needs a second script, and a migration that transforms data has no exact inverse for
  the data: the round trip compares the catalog, not the rows.
- Bad, because the comparison covers the kinds of objects that `CatalogSnapshot` lists, not operators, casts, text
  search or foreign data objects; a migration that creates one passes the round trip until the snapshot covers it.
- Neutral, because down scripts are level 3 paths, like the migrations (`policy/risk.json`, rule `migrations`).

### Confirmation

- `MigrationRoundTripTest` (java; PostgreSQL 18.6 in CI): for each migration n, a new database is migrated to n − 1
  and its catalog recorded, migrated to n, reverted with the down script, which must restore the recorded catalog of
  n − 1 exactly, and migrated to n again, which must restore the catalog of n exactly. A database that went down and
  up through every version equals one migrated straight to the latest. The test also shows that the comparison fails:
  a down script without its last statement leaves a schema behind, and each kind of change it compares (privileges,
  row-level security flags, columns, constraints, indexes, policies, functions, schemas, comments, tables, roles,
  memberships, default privileges and database privileges) changes the snapshot.
- `MigrationFilesTest` (java; no database): every migration has one down script and every down script a migration,
  neither holds transaction control or a Flyway placeholder, and the migration location holds versioned SQL migrations
  only (the Flyway review's condition 3).

## More Information

- Flyway documentation, undo migrations: <https://documentation.red-gate.com/fd/undo-migrations-273973334.html>
- Flyway documentation, the undo command: <https://documentation.red-gate.com/flyway/reference/commands/undo>
- The schema, roles and row-level security: ADR 0005, `docs/modules/tenants.md`, `docs/modules/quotes.md`.
