---
component: "org.junit.jupiter:junit-jupiter (with junit-jupiter-api/-engine/-params 5.14.4 and junit-platform-launcher/-engine/-commons 1.14.4)"
kind: maven
version: "5.14.4"
verdict: APPROVE-WITH-CONDITIONS
scope: "Test classpaths of :core and :junit5. junit-jupiter-api and junit-platform-commons also on :junit5 main classpaths, as the adapter's API dependency. junit-platform-launcher/-engine/-commons 1.14.4 also on the :core PIT tool classpath (configuration `pitest`)"
conditions: ["All JUnit artifacts on these classpaths resolve to exactly 5.14.4 (Jupiter) and 1.14.4 (Platform), aligned by org.junit:junit-bom:5.14.4. junit-platform-launcher is declared explicitly", "junit.jupiter.extensions.autodetection.enabled and junit.platform.launcher.interceptors.enabled are never set to true. Any junit-platform.properties in this repository is code-owned", "No other JUnit module (console, reporting, suite, vintage, testkit, jfr) is added without its own review", "org.opentest4j:opentest4j 1.3.0 and org.apiguardian:apiguardian-api 1.1.2 have their own approved reviews before use", "Every artifact on a test runtime classpath is allowlisted, because the Launcher runs ServiceLoader-registered code from any JAR there", "The resolved JAR SHA-256 values equal those in allowlist-entries.json. Any other version or hash is a new review", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: JUnit 5.14.4 (Jupiter 5.14.4, Platform 1.14.4)

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** The strongest provenance in the set: tags signed with the key the project's `KEYS` file names; a release workflow that rebuilds from the tag and checks the staged artifacts are reproducible before publishing; CodeQL and Scorecard 8.5; no known advisories for these artifacts. The conditions keep it on test and tool classpaths and fence its ServiceLoader extension points.

## 2. What it is and why it is needed
JUnit runs the library's tests, and the optional `:junit5` adapter writes reports before failing. `junit-platform-launcher` is needed by Gradle and by the PIT JUnit 5 plugin (repository design note `v01-design.md`, section 4: "JUnit 5.14.4 … Library tests"; PIT "with matching JUnit launcher").

## 3. Provenance
**Publisher:** the JUnit team. Group IDs `org.junit.jupiter`, `org.junit.platform` and `org.junit`; repo `github.com/junit-team/junit-framework`, EPL-2.0 ([repo](https://api.github.com/repos/junit-team/junit-framework)).

**Release, 2026-04-26.** Central files 11:20; GitHub release 11:59:08Z, created by github-actions, "Immutable release". Tag `r5.14.4` carries a "verified signature" by `marcphilipp`, GPG key ID `85911F425EC61B51`, on commit `096fd696c1b22780e025a02417d42903e0022862` ([release page](https://github.com/junit-team/junit-framework/releases/tag/r5.14.4)). That key ID is the tail of `FF6E2C001948C5F2F38B0CC385911F425EC61B51`, the key that "is used to sign releases" ([KEYS](https://raw.githubusercontent.com/junit-team/junit-framework/main/KEYS)); "JUnit JARs released on Maven Central are signed" ([SECURITY.md](https://raw.githubusercontent.com/junit-team/junit-framework/main/SECURITY.md)).

**Build.** `release.yml` at the tag runs on `workflow_dispatch` with `permissions: {}` and SHA-pinned actions; it checks out the tag, runs `verifyArtifactsInStagingRepositoryAreReproducible` against the Central Portal deployment, runs `actions/attest-build-provenance` on the rebuilt JARs, then publishes through JReleaser ([release.yml](https://raw.githubusercontent.com/junit-team/junit-framework/r5.14.4/.github/workflows/release.yml)). How the staging deployment is created and signed is not in that file. `reproducible-build.yml` builds twice and diffs SHA-512 sums ([script](https://raw.githubusercontent.com/junit-team/junit-framework/r5.14.4/.github/scripts/checkBuildReproducibility.sh)).

**SHA-256.** From the `.sha256` next to each JAR, e.g. [`junit-jupiter-api-5.14.4.jar.sha256`](https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/5.14.4/junit-jupiter-api-5.14.4.jar.sha256); all seven read twice independently (match); the API value also equals the `sha256` in its Gradle module metadata ([.module](https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/5.14.4/junit-jupiter-api-5.14.4.module)). Values in `allowlist-entries.json`.

## 4. Maintenance and adoption
**Release line**
- 5.14.4 is the newest 5.x. SECURITY.md lists **5.14.x and 6.1.x as supported**.
- 5.14.4 fixes a race in `NodeTestTask.parentContext` and has no security items ([notes](https://docs.junit.org/5.14.4/release-notes/)).

**Project**
- Created 2015, 7,060 stars, last pushed 2026-11-06.
- Maintainers include `marcphilipp` (4,245), `sbrannen` (3,232), `sormuras` (777) and `jlink` (555) ([contributors](https://api.github.com/repos/junit-team/junit-framework/contributors)).

**OpenSSF Scorecard: 8.5** (2026-11-06) ([scorecard](https://api.securityscorecards.dev/projects/github.com/junit-team/junit-framework))
- Scored 10: Security-Policy, Pinned-Dependencies, Token-Permissions, SAST, Vulnerabilities, Dependency-Update-Tool, CI-Tests.
- Lower: Code-Review 7, CII 5, Branch-Protection 4, Fuzzing 0.

## 5. Known vulnerabilities at this version
**Method.** OSV `POST /v1/query` was not callable from this environment (GET-only fetch, no shell network). I used deps.dev `advisoryKeys`, the OSV name search and GitHub advisories.

**None found on 2026-11-06.**
- deps.dev `advisoryKeys: []` for jupiter-engine 5.14.4, jupiter-api 5.14.4, platform-launcher 1.14.4 and platform-commons 1.14.4 ([example](https://api.deps.dev/v3/systems/maven/packages/org.junit.jupiter%3Ajunit-jupiter-engine/versions/5.14.4)).
- The OSV search for "junit" shows one `org.junit.*` advisory: GHSA-m43g-m425-p68x, for **`junit-platform-reporting`**, which is not in this set ([osv](https://osv.dev/list?ecosystem=Maven&q=junit), [GHSA](https://api.osv.dev/v1/vulns/GHSA-m43g-m425-p68x)). It leaks Git credentials into Open Test Reporting XML.
  - The advisory states its range in JUnit release numbers: affected from 5.12.0, fixed in 5.13.2.
  - The repository advisories list only this one ([advisories](https://api.github.com/repos/junit-team/junit-framework/security-advisories)).
- The transitive opentest4j 1.3.0 and apiguardian-api 1.1.2 also show `[]` on deps.dev.

## 6. License
EPL-2.0 for every POM. deps.dev shows "non-standard" because it does not map the POM's licence name. EPL-2.0 is weak copyleft. That is fine for test use, and for `:junit5`'s dependency on the unmodified API.

## 7. Execution privileges (test phase: Gradle test JVMs and PIT child JVMs)
Sources are the user guide at https://docs.junit.org/5.14.4/.

- **Plugin discovery runs code.** The Launcher discovers `TestEngine`, `LauncherSessionListener`, `LauncherDiscoveryListener`, `TestExecutionListener` and `PostDiscoveryFilter` through `ServiceLoader` from **any JAR on the test runtime classpath**, and they run in the test JVM; only programmatic `LauncherConfig` turns this off, and Gradle owns the launcher ([launcher API](https://docs.junit.org/5.14.4/advanced-topics/launcher-api.html)).
- **Opt-in, off by default:** Jupiter extension auto-detection (`junit.jupiter.extensions.autodetection.enabled`, [extensions](https://docs.junit.org/5.14.4/extensions/registering-extensions.html)), `LauncherInterceptor` (`junit.platform.launcher.interceptors.enabled`), parallel execution.

- **Configuration** comes from launcher parameters, JVM system properties and `junit-platform.properties` at the classpath root ([config](https://docs.junit.org/5.14.4/running-tests/configuration-parameters.html)); some parameters name classes to instantiate (`junit.jupiter.tempdir.factory.default`, `junit.jupiter.execution.parallel.config.custom.class`).
- **Files and threads:** `@TempDir` uses `Files.createTempDirectory` with prefix `junit-`, cleanup `ALWAYS` by default ([built-in extensions](https://docs.junit.org/5.14.4/writing-tests/built-in-extensions.html)); timeouts may use a separate thread. **Reflection:** invokes non-private test and lifecycle methods. **Processes and network:** none in the pages read. **Build time:** none beyond running as the test framework.

## 8. Direct dependencies as published
All compile scope unless noted; versions are aligned by `junit-bom` 5.14.4 ([aggregator POM](https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter/5.14.4/junit-jupiter-5.14.4.pom)).

| Artifact | Depends on |
|---|---|
| `junit-jupiter` | api, params, engine (runtime) |
| api | opentest4j 1.3.0, platform-commons 1.14.4, apiguardian-api 1.1.2 |
| engine | platform-engine, api, apiguardian |
| params | api, apiguardian |
| launcher | platform-engine, apiguardian |
| platform-engine | opentest4j, platform-commons, apiguardian |
| platform-commons | apiguardian |

- **POM-only metadata:** `org.junit:junit-bom:5.14.4`. It is only dependencyManagement of `org.junit.*` at 5.14.4/1.14.4, with no repositories or plugins, and it has an entry ([BOM](https://repo1.maven.org/maven2/org/junit/junit-bom/5.14.4/junit-bom-5.14.4.pom)).
- **Need their own review:** `opentest4j` 1.3.0 and `apiguardian-api` 1.1.2, both Apache-2.0.

## 9. Alternatives considered
- **JUnit 6.1.x:** a supported line, but the design pins 5.14.4 for the library and leaves 6 to Boot's example.
- **TestNG:** the PIT plugin and the `:junit5` adapter target Jupiter.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **Shared ServiceLoader trust.** Any allowlisted JAR on a test classpath can register listeners or engines that run in the test JVM. The allowlist is the only control.
- **Who signs the Central deployment** is not visible in the workflow.

## 11. Not verified
- **JARs and signatures:** the JARs were not downloaded or hashed, and the `.asc` signatures were not checked against `KEYS`.
- **Provenance attestations:** api.github.com was rate-limited, and the attestations page's first page showed only snapshots.
- **The actual run** that published 5.14.4 was not inspected.
- **deps.dev per-version checks:** junit-jupiter, params and platform-engine were not individually queried; they rely on the name search.
- **Execution behaviour:** taken from the user guide, not from source or a run.
