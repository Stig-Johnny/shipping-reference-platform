---
title: "Database tests start only the reviewed PostgreSQL image, on the reviewed engine"
description: "The database tests use the one reviewed PostgreSQL image, only on the container engine its review approves: the checker rejects other container declarations it recognizes, and CI checks what the engine did."
status: accepted
updated: 2026-11-07
pr: 54
kind: gate
recorded: 2026-11-07
tests: [PostgresHarnessIntegrationTest, ReviewedPostgresTest, PagesWorkflowTest]
---

## What changed

Before, the reviews allowed PostgreSQL in tests only under conditions that no check applied, so no test used a
database. Now the checker rejects recognized container declarations. The harness validates its PostgreSQL instance,
and CI checks subsequent engine activity. These controls do not isolate arbitrary build code. In CI, the runner's
container engine is checked against its review (versions, a loopback-only daemon, no registry credentials) before the
database tests start, and afterwards CI checks which images appeared and which images containers were made from. The
database tests run in their own task, `:apps:shipping-api:databaseTest`, which CI's java check and the site's
publishing workflow run; `./gradlew check` needs only the reviewed JDK. The publishing workflow runs the tests in a
job with no publication privilege.

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

Recorded 2026-11-07 on macOS 26 with Temurin 25.0.5+8, on the branch of pull request #54 after the review's rework.
With the fixture, the command exited 1:

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

In the pull request's CI, each rework of a control first failed on its new tests and then passed: the checker's
tests failed on 30 spellings that got through it (run 42415471627) and passed once it read them (run 42415698147);
the tests of the CI scripts failed where a failed engine query read as an empty answer and where a prerelease of runc
1.3.3 passed (run 42416220034). The runs before the rework show the harness failing closed without the engine's
preparation (run 42410083958) and passing with it (run 42410533646), whose job summary records the runner image
20261101.360.1 on Ubuntu 24.04.5, Docker Engine 28.0.4, containerd v2.4.2 and runc 1.5.2, and one container, made
from the reviewed image. The test classes named above check the harness and the publishing workflow in every build.

## Limits

- The checker rejects recognized container declarations, as text. An image that a library names itself, a call made
  through reflection or a command assembled from shell variables is outside it. These controls do not isolate
  arbitrary build code.
- At run time CI sees which images appeared, which images containers were made from and whether anything was copied
  into one. It does not see how another container was configured; only the harness's own container is checked for
  privileges, mounts, networks and published ports.
- The database tests run only on GitHub-hosted ubuntu-24.04 runners. Elsewhere `databaseTest` fails, by design, and a
  local `./gradlew check` shows no database evidence.
- Text only until a media gate is reviewed.
