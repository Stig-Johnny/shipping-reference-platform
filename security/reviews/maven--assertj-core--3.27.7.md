---
component: "org.assertj:assertj-core"
kind: maven
version: "3.27.7"
verdict: APPROVE-WITH-CONDITIONS
scope: "Test classpaths (testCompileClasspath, testRuntimeClasspath) of :core, :junit5 and :example-app (Spring Boot 4.1.2 manages the same version)"
conditions: ["Test configurations only. Never on a main, published or PIT tool classpath", "net.bytebuddy:byte-buddy has its own approved review before use: 1.18.3 as requested by this POM, and 1.18.15 if Spring Boot's management resolves that in :example-app", "Tests do not use isXmlEqualTo, isXmlEqualToContentOf, xmlPrettyFormat or XmlStringPrettyFormatter. They are deprecated, and they are the CVE-2026-24400 surface", "The resolved JAR SHA-256 equals c4a445426c3c2861666863b842cc4ec7bbb1c4226fefd370b6d2fe83d6c4ff0f. Any other version or hash is a new review", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: org.assertj:assertj-core 3.27.7

## 1. Verdict
**APPROVE-WITH-CONDITIONS.**
- **Fixes a known vulnerability.** 3.27.7 is the release that fixes the XXE CVE-2026-24400, and no advisory affects it.
- **Built in CI.** The project's release workflow builds, GPG-signs and deploys from GitHub Actions with SHA-pinned actions (the 3.27.7 run itself was not inspected).
- **Runtime code generation.** At test time it generates and injects classes through Byte Buddy, which is a separate, unreviewed dependency. That is acceptable on test classpaths only.

## 2. What it is and why it is needed
It provides readable assertions on collections and evidence in library tests. The design considered JUnit's native assertions as the alternative (repository design note `v01-design.md`, section 4).

## 3. Provenance
**Publisher.** The AssertJ project.
- Namespace `org.assertj`, repo `github.com/assertj/assertj`, Apache-2.0 ([repo](https://api.github.com/repos/assertj/assertj)).
- The published POM is flattened and has no parent ([POM](https://repo1.maven.org/maven2/org/assertj/assertj-core/3.27.7/assertj-core-3.27.7.pom)).

**Release.**
- Central files are dated 2026-01-24 19:07 ([listing](https://repo1.maven.org/maven2/org/assertj/assertj-core/3.27.7/)).
- The GitHub release came out 19:35:58Z, by `scordio`. It is marked "Immutable release" and tagged `assertj-build-3.27.7`, at commit `e84071667f5f8f13084af9dfa54cee5fd9db18db` ([release](https://github.com/assertj/assertj/releases/tag/assertj-build-3.27.7)).
- No "Verified" badge appears on the tag or commit.

**Build.** `release.yml` ([release.yml](https://raw.githubusercontent.com/assertj/assertj/assertj-build-3.27.7/.github/workflows/release.yml)):
- It is a manual `workflow_dispatch` with `contents: write`.
- Its actions are pinned to SHAs, and it has setup-java with `gpg-private-key` from secrets.
- It runs `./mvnw … release:prepare release:perform` against server `central`.
- The tag commit message, `[maven-release-plugin] prepare release assertj-build-3.27.7`, fits that workflow. The Actions run itself was not inspected.
- deps.dev shows no SLSA provenance ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.assertj%3Aassertj-core/versions/3.27.7)).

**SHA-256.** `c4a44542…c4ff0f`, from [`assertj-core-3.27.7.jar.sha256`](https://repo1.maven.org/maven2/org/assertj/assertj-core/3.27.7/assertj-core-3.27.7.jar.sha256). Two independent reads matched.

## 4. Maintenance and adoption
**Releases.**
- 3.27.6 (2025-09-22) and 3.27.7 (2026-01-24). 3.27.7 is the newest 3.x.
- 4.0.0-M1 is a prerelease from 2025-03-09 ([metadata](https://repo1.maven.org/maven2/org/assertj/assertj-core/maven-metadata.xml)).
- Spring Boot 4.1.2 manages `assertj-core` at 3.27.7 ([coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)).

**Project.**
- Created 2013, 2,853 stars, last pushed 2026-11-05.
- Contributors: `joel-costigliola` 2,194, `scordio` 905, `PascalSchumacher` 464 ([contributors](https://api.github.com/repos/assertj/assertj/contributors)).
- Scorecard: not available, the API returns 404 ([scorecard](https://api.securityscorecards.dev/projects/github.com/assertj/assertj)).

**Diff 3.27.6 → 3.27.7.** 23 commits ([commits](https://github.com/assertj/assertj/commits/assertj-build-3.27.7)):
- **XXE fix** `77081dc`: `XmlStringPrettyFormatter` gets `SECURE_FEATURES`, which disallow DOCTYPE and external entities.
- **Deprecation** of `XmlStringPrettyFormatter`.
- **Byte Buddy** 1.18.2 → 1.18.3.
- **Build** moved to Java 25.
- **Licence-header commit** `b81a17b`, touching 5,150 files (see section 11).

## 5. Known vulnerabilities at this version
**Method.** OSV `POST /v1/query` was not callable (GET-only fetch, no shell network). I used deps.dev `advisoryKeys`, OSV name search and GitHub advisories.

**3.27.7: none found on 2026-11-06.**
- deps.dev `advisoryKeys: []`.
- The OSV search shows one AssertJ advisory, GHSA-rqfh-9r24-8c9r ([osv](https://osv.dev/list?ecosystem=Maven&q=assertj)).
- The repo has exactly one advisory ([advisories](https://api.github.com/repos/assertj/assertj/security-advisories)).

**GHSA-rqfh-9r24-8c9r / CVE-2026-24400** (High, CVSS v4 8.2):
- XXE in `isXmlEqualTo(CharSequence)` / `xmlPrettyFormat`.
- Affected: `>= 3.0.0, <= 3.27.6` (plus 1.x and 2.x). **Patched in 3.27.7.**
- In the fix, `catch (ParserConfigurationException ignore) {}` skips any feature the XML parser does not support. That is one reason for condition 3.

**Byte Buddy 1.18.3:** deps.dev `[]` ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/net.bytebuddy%3Abyte-buddy/versions/1.18.3)).

## 6. License
Apache-2.0 ([POM](https://repo1.maven.org/maven2/org/assertj/assertj-core/3.27.7/assertj-core-3.27.7.pom)).

## 7. Execution privileges (test phase only)
All source references are at tag `assertj-build-3.27.7`.

**Runtime class generation and injection.**
- `SoftProxies` subclasses assert classes with `new ByteBuddy()…subclass(assertClass)` and loads them into the test class loader (`SoftProxies.java:111-112,210-228`).
- `ClassLoadingStrategyFactory` chooses how:
  - **First choice:** `ClassInjector.UsingReflection`, via `ClassLoadingStrategy.Default.INJECTION`.
  - **Fallback:** `MethodHandles.privateLookupIn` with `ClassInjector.UsingLookup`.
  - **Foreign class loaders:** a `CompositeClassLoader.defineClass`.
  - Source: `ClassLoadingStrategyFactory.java:32-71`.
- `Assumptions` uses the same mechanism ([SoftProxies.java](https://raw.githubusercontent.com/assertj/assertj/assertj-build-3.27.7/assertj-core/src/main/java/org/assertj/core/api/SoftProxies.java), [ClassLoadingStrategyFactory.java](https://raw.githubusercontent.com/assertj/assertj/assertj-build-3.27.7/assertj-core/src/main/java/org/assertj/core/api/ClassLoadingStrategyFactory.java)).

**`ErrorCollector`** only collects `AssertionError`s. It inspects the current stack trace, and does no I/O ([ErrorCollector.java](https://raw.githubusercontent.com/assertj/assertj/assertj-build-3.27.7/assertj-core/src/main/java/org/assertj/core/api/ErrorCollector.java)).

**I/O.** By API design, files, paths and URLs are read only when a test calls content assertions such as `contentOf(...)` (not traced in source). XML parsing happens in the deprecated XML assertions. No process execution was seen in the files read.

**Build time.** None. It is a plain library.

## 8. Direct dependencies as published
- **`net.bytebuddy:byte-buddy` 1.18.3, compile.** The only compile dependency.
  - It is Apache-2.0.
  - It repackages ASM into `net.bytebuddy.jar.asm`, with `version.asm` 9.9.1 in its parent POM ([byte-buddy POM](https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.18.3/byte-buddy-1.18.3.pom), [parent](https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy-parent/1.18.3/byte-buddy-parent-1.18.3.pom)).
  - **Needs its own review**, because it does runtime code generation and class injection.
  - Spring Boot 4.1.2 manages `byte-buddy` 1.18.15, so `:example-app` may resolve a second version.
- **Not resolved by Gradle**, because they are `provided` and optional: junit 4.13.2, hamcrest 3.0, junit-jupiter-api 5.14.1 and opentest4j 1.3.0.

## 9. Alternatives considered
- **JUnit Jupiter's native `Assertions`.** No extra dependency, but evidence assertions are less readable. The design considered it.
- **Hamcrest.** Another matcher library to review.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- Byte Buddy's reflection-based class injection relies on JDK internals. Its behaviour on JDK 25 was not verified (section 11).
- **Two Byte Buddy versions** may be in play across projects.

## 11. Not verified
- **The JAR and its signature.** The JAR was not downloaded, hashed or inspected, and the `.asc` was not checked.
- **The licence-header commit `b81a17b`** changed 5,150 files and was only partly viewed. That it changes headers only is **not confirmed**.
- **The release run.** The Actions run that produced 3.27.7 was not inspected.
- **Byte Buddy.** Its internals and JDK 25 behaviour were not reviewed.
