---
title: "Database tests start only the reviewed PostgreSQL image, on the reviewed engine"
description: "A test can no longer pull or run a container image that nobody reviewed: the allowlist check refuses it before anything is pulled, and CI starts the database only under the conditions of the engine's review."
status: accepted
updated: 2026-11-07
pr: 54
kind: gate
recorded: 2026-11-07
tests: [PostgresHarnessIntegrationTest, ReviewedPostgresTest, PagesWorkflowTest]
---

## What changed

Before, the reviews allowed PostgreSQL in tests only under conditions that no check applied, so no test used a
database. Now the allowlist check also reads every place that can name a container image, and refuses an image that
no approved entry names by its digest, before anything is pulled. In CI, the runner's container engine is checked
against its review (versions, a loopback-only daemon, no registry credentials) before the database tests start, and
afterwards CI checks that no other image appeared or ran. The test harness itself refuses to start PostgreSQL
anywhere else. The site's publishing workflow runs these tests in a job with no publication privilege.

## See it

- [ADR 0005](../adr/0005-postgresql-spring-data-jdbc-flyway-and-forced-rls.md): the test database is the first part
  of the decision with checks of its own; the rest is still planned.
- [ADR 0007](../adr/0007-dependency-allowlist-and-pre-use-enforcement.md): its implementation status describes the
  image check and the engine's conditions.
- [The known gaps](../gaps.md): what these checks cannot see, in sections 5 and 6.

## Try it

From the repository root, with the reviewed JDK as `JAVA_HOME`. As the fixture, change the image in
`ReviewedPostgres.java` (under `apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/`) from
`postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583` to Testcontainers' rejected
cleanup image, `testcontainers/ryuk@sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0`, and run
the checker. Then undo the change and run it again.

```sh
java tools/policy/PolicyCheck.java .
```

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07 on macOS 26 with Temurin 25.0.5+8, on the branch of pull request #54. With the fixture, the
command exited 1:

```text
## Dependency allowlist: 1 finding(s)

Checked against `security/allowlist.json` (308 entries) on 2026-11-07.

### images

- apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/ReviewedPostgres.java:53: testcontainers/ryuk@sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0 was REJECTED in security/reviews/container-image--testcontainers-ryuk--0.14.0.md
```

Without it, the same command exited 0:

```text
## Dependency allowlist: clean

Checked against `security/allowlist.json` (308 entries) on 2026-11-07.
```

In the pull request's CI, the java check failed while the harness came without the engine's preparation (run
42410083958: the harness refused to start PostgreSQL), and passed once it was there (run 42410533646). That run's
job summary records the runner image 20261101.360.1 on Ubuntu 24.04.5, Docker Engine 28.0.4, containerd v2.4.2 and
runc 1.5.2, and its last step found 14 engine events and one container, made from the reviewed image. The test
classes named above check the harness and the publishing workflow in every build.

## Limits

- The check reads text. An image that a library names itself, or a call made through reflection, is caught only at
  run time, by the harness and by the workflow's checks of the image set and the engine's events.
- The database tests run only on GitHub-hosted ubuntu-24.04 runners. Elsewhere they refuse to start, so
  `./gradlew check` fails on a machine without that engine, by design.
- The engine review asks for the first run's containerd and runc versions to be added to it; that waits for the
  owner.
- Text only until a media gate is reviewed.
