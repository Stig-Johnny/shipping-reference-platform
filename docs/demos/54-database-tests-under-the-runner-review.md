---
title: "CI closes engine access outside the reviewed database-test window"
description: "Runtime guards stop the engine and prove CLI/socket denial; check 8 remains a PARTIAL static lint with documented bypasses."
status: accepted
updated: 2026-11-08
pr: 54
kind: gate
recorded: 2026-11-07
tests: [PostgresHarnessIntegrationTest, ReviewedPostgresTest, PagesWorkflowTest, ContainerEngineWorkflowTest]
---

## What changed

Before, CI left the engine running during ordinary builds and publication, and check 8's static scanning claims
exceeded what it recognized. Under the owner's 2026-11-08 decision, every job now stops and masks the engine and
removes its socket as its first step, then refuses to continue if the CLI or a direct socket connection succeeds.
Only preparation, image staging, the database task and engine verification have access to the engine. A shutdown
step runs immediately afterwards even on failure, and later work requires successful shutdown. The database tests
remain mandatory in Java CI and Pages testing; the other tests and documentation run after the engine is stopped.

The runtime guard is the enforcing control for engine availability. Check 8 remains unchanged and is labelled a
PARTIAL static lint. [ADR 0007](../adr/0007-dependency-allowlist-and-pre-use-enforcement.md) and
[the gaps](../gaps.md#5-supply-chain-and-approvals) list the known bypasses and the unsolved pin-update sequence.
The reviewed harness still checks its PostgreSQL instance and CI audits engine activity. This is not a sandbox
against code that deliberately uses the hosted runner's privileges to re-enable an engine.

The owner renewed acceptance of the reviewed set for pull request #54 (issue #51) at
2026-11-08T09:46:29.393Z, recorded in `security/allowlist.json`. The accepted amendment records containerd v2.4.2
and runc 1.5.2 from run 42410533646 and acceptance before merging the harness. It does not claim that advisories
for those versions were reviewed. The earlier acceptance for pull request #73 remains in the history.

## See it

- [ADR 0012](../adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md), which carries
  forward ADR 0005's database design: the test database is the first part of it with checks of its own; the rest is
  still planned.
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
1.3.3 passed (run 42416220034). After the second review, the checker's tests failed on 20 spellings that bash, the
compilers or YAML read as docker or Testcontainers (run 42420154660), then on 6 of 8 more that this pull request wrote
against its own first normalising version (run 42421241319), and the engine script's tests failed where a failing
grep read as 0 events (run 42421521416); run 42421869779 passed all of them. After the third review, which found
heredocs, multi-line YAML scalars, a Kotlin escaped name, a constant concatenation and resources that got through,
the check was made to refuse every form it cannot decode: its tests failed on those 12 examples (run 42428264126),
then on 7 of 10 evasions that this pull request tried against the fail-closed version (run 42428525277); run
42429066480 passed all of them. The runs before the rework show the harness failing closed without the engine's
preparation (run 42410083958) and passing with it (run 42410533646), whose job summary records the runner image
20261101.360.1 on Ubuntu 24.04.5, Docker Engine 28.0.4, containerd v2.4.2 and runc 1.5.2, and one container, made
from the reviewed image. The test classes named above check the harness and the publishing workflow in every build.

Supplement recorded 2026-11-08 with Temurin 25.0.5+8 on macOS, after the web-toolchain merge. The JDK-only
`PolicyCheckTest` fixtures read commands as data; they never execute Node, npm or the container payload. Before the
correction, its run exited 1 and included:

```text
FAIL web forms: accepts shell node --version
FAIL web forms: ProcessBuilder npm run plus manifest docker is refused at both sites
```

After the correction, the same cases included:

```text
ok   web forms: accepts shell node --version
ok   web forms: ProcessBuilder npm run plus manifest docker is refused at both sites
```

These are excerpts, not the complete suite result: 611 in-process cases passed, but the run still exited 1 because
13 child-process cases could not complete the sandbox's process inspection (`Operation not permitted (sysctl failed)`).
They were not skipped or changed. Before the renewal recorded above, the real-tree checker and documentation build
refused the reviewed set because it differed from the earlier acceptance. These fixture results do not establish
web execution, database evidence or a successful publishing build. The JDK-only fixtures produce no JUnit evidence
for the page's current-build test ledger.

Structural-gate supplement recorded 2026-11-08 with the same reviewed JDK. The round-five fixtures first failed
on the review's subsequent argument mutations, process wrapper and inferred Runtime aliases, and on a registered
file edit. Examples from the captured red run:

```text
FAIL launch registry: Gradle args after commandLine
FAIL launch registry: ProcessBuilder env wrapper
FAIL launch registry: qualified inferred Runtime
FAIL launch registry: any registered edit fails its hash
```

The corresponding green fixture excerpts:

```text
ok   launch registry: Gradle args after commandLine
ok   launch registry: ProcessBuilder env wrapper
ok   launch registry: qualified inferred Runtime
ok   launch registry: any registered edit fails its hash
```

All 61 new in-process cases and all 611 earlier in-process cases passed. The complete run still exited 1:
13 existing child-process cases hit the sandbox's `sysctl` restriction, with no assertion skipped. The real-tree
checker reported the earlier acceptance-digest mismatch. That Java and generated-docs build also exited 1:
all 62 documentation-test failures were caused by the mismatch, which prevented generation and rendered-demo
review at that time. These results predate the renewal above and are not a passing publishing build.

Additional capture on 2026-11-08 for the runtime decision: the shell regression executes the workflow guard blocks
with fake commands, never Docker or sudo. Before the workflow change it exited 1:

```text
FAIL expected 8 engine guards, found 0
```

A second red case showed why an unavailable probe must not count as denial (exit 1):

```text
FAIL cli-error passed the guard
```

After the change, the regression exited 0:

```text
64 passed: 8 workflow guards reject reachable CLI/socket, failed stop/mask/unlink and probe errors; stopped passes
```

The three new `ContainerEngineWorkflowTest` tests failed before the workflow change. They and the updated
`PagesWorkflowTest` passed afterwards. These are recorded local fixture and workflow results, not evidence that
hosted service shutdown has run. No local database, Docker or mutation task ran.

The resumed run on 2026-11-08 added a timeout fixture and exact call-order checks. Status assertions exit explicitly,
including on the local Bash 3.2, which does not reliably apply `set -e` inside a `case` branch. The shell regression
then exited 0:

```text
72 passed: 8 workflow guards reject reachable CLI/socket, failed stop/mask/unlink and probe errors/timeouts; shutdown precedes probes; stopped passes
```

The resumed Java check passed all 3 workflow-guard tests and all 16 publishing-workflow tests. Its 62 documentation
failures traced to the earlier acceptance-digest mismatch, before the renewal recorded above. No failing assertion
was skipped or relaxed.

Acceptance supplement recorded 2026-11-08 on macOS with Temurin 25.0.5+8. After recording the owner's renewal,
the digest remained `03d8223632a17e66bf31d5b2d0c0bdbc7d5f3837a7f3a032080343bb46517e8d`.
`java tools/policy/PolicyCheck.java .` exited 0:

```text
## Dependency allowlist: clean

Checked against `security/allowlist.json` (519 entries) on 2026-11-08.
```

All 519 entries and all 81 tracked review files were unchanged. `AllowlistTest`, the acceptance-page test in
`SitePagesTest` and `SiteAccessibilityTest` passed. ADR 0007's command table now sits outside its list so the
renderer supplies the labelled, keyboard-reachable scroll region required by the unchanged accessibility test.
These are local acceptance and presentation checks, not database or hosted CI evidence.

## Limits

- Check 8 is a PARTIAL static lint. Known bypass classes include Gradle argument/executable mutation, wrappers and
  inferred Runtime aliases; raw or Unicode-escaped CR comment boundaries; JavaBeans reflection and Groovy process
  APIs called from Java/Kotlin; implicit plugin-provided Gradle launch tasks; and indirect/dynamically assembled
  commands or code imported by the reviewed Node test runner. Existing examples caught by pins do not establish
  complete coverage. Single-digest launch pins also have no working trusted base/candidate update sequence.
- Runtime guards enforce the workflow's engine window, but privileged code can undo them or use another/remote
  engine. Gradle configuration runs during the database task. Local fixtures test failure handling; a hosted CI run
  must verify service shutdown on the actual runner. The source scanner is not the enforcing control.
- At run time CI sees which images appeared, which images containers were made from and whether anything was copied
  into one. It does not see how another container was configured; only the harness's own container is checked for
  privileges, mounts, networks and published ports.
- The database tests run only on GitHub-hosted ubuntu-24.04 runners. Elsewhere `databaseTest` fails, by design, and a
  local `./gradlew check` shows no database evidence.
- Text only until a media gate is reviewed.
