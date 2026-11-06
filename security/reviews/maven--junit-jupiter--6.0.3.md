---
component: "org.junit.jupiter:junit-jupiter (with junit-jupiter-api/-engine/-params and junit-platform-launcher/-engine/-commons 6.0.3)"
kind: maven
version: "6.0.3"
verdict: APPROVE-WITH-CONDITIONS
scope: "Test classpaths (testCompileClasspath, testRuntimeClasspath) of :example-app only, at the version managed by Spring Boot 4.1.2"
conditions: ["Only on :example-app test classpaths, versions from Spring Boot 4.1.2 dependency management (junit-bom 6.0.3). Never on :core, :junit5 or any main classpath", "org.jspecify:jspecify resolves to the reviewed 1.0.1 (managed by Boot). The 1.0.0 that these POMs request must not appear in any configuration", "JUnit's SECURITY.md does not list 6.0.x as supported, so any advisory affecting 6.0.x triggers an immediate re-review. Overriding Boot to 6.1.x is a new review", "junit.jupiter.extensions.autodetection.enabled and junit.platform.launcher.interceptors.enabled are never set to true. Any junit-platform.properties is code-owned", "org.opentest4j:opentest4j 1.3.0 and org.apiguardian:apiguardian-api 1.1.2 have their own approved reviews before use", "The resolved JAR SHA-256 values equal those in allowlist-entries.json. Any other version or hash is a new review", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: JUnit 6.0.3 (Jupiter and Platform 6.0.3)

## 1. Verdict
**APPROVE-WITH-CONDITIONS, for the example app's tests only.**

Provenance matches JUnit 5.14.4: a signed tag and the same team and key. No advisories are known.

The extra risk is support status. JUnit's own `SECURITY.md` lists only 6.1.x and 5.14.x as supported, and no 6.0.x release has followed 6.0.3. So 6.0.3 should not be expected to receive security fixes.

## 2. What it is and why it is needed
Spring Boot 4.1.2 manages JUnit 6.0.3. The design says to "use that for example tests, calling core directly" (repository design note `v01-design.md`, section 4).

Boot's dependency-versions page, headed "Spring Boot 4.1.2", lists `junit-jupiter` and `junit-platform-launcher` at `6.0.3` ([coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)).

## 3. Provenance
- **Publisher:** the JUnit team (`org.junit.*`, repo `junit-team/junit-framework`, EPL-2.0).
- **Release dates:** the Central files are dated 2026-02-15 13:34. The GitHub release is dated 2026-02-15T15:20:56Z, created by `marcphilipp` and marked "Immutable release".
- **Tag signature:** tag `r6.0.3` carries a "verified signature" by `marcphilipp` with GPG key ID `85911F425EC61B51`. It points at commit `36e3253b726fa13cb9a1d29e94b9119575078c4a` ([release page](https://github.com/junit-team/junit-framework/releases/tag/r6.0.3)).
  - That key ID is the tail of the release key `FF6E2C001948C5F2F38B0CC385911F425EC61B51` in [KEYS](https://raw.githubusercontent.com/junit-team/junit-framework/main/KEYS).
- **Release workflow:** the workflow at `r6.0.3` was **not read**. The `r5.14.4` workflow rebuilds and checks reproducibility before publishing (see the 5.14.4 review), but whether 6.0.3 went through the same process is not verified.
- **SHA-256:**
  - api, engine, params and aggregator: from the `.sha256` files on Central, each read twice in independent fetches, matching.
  - platform launcher, engine and commons: from `.sha256` files that equal the `sha256` in each Gradle `.module` file ([example](https://repo1.maven.org/maven2/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.module)).
  - Values are in `allowlist-entries.json`.

## 4. Maintenance and adoption
- **6.x releases** ([releases](https://api.github.com/repos/junit-team/junit-framework/releases)):

  | Version | Date |
  |---|---|
  | 6.0.0 | 2025-09-30 |
  | 6.0.1 | 2025-10-31 |
  | 6.0.2 | 2026-01-06 |
  | 6.0.3 | 2026-02-15 |
  | 6.1.0 | 2026-05-19 |
  | 6.1.3 | 2026-08-07 (newest) |

  There is no 6.0.x release after 6.0.3.
- **Supported versions:** `6.1.x` ✅, `5.14.x` ✅ and `< 5.14` ❌. **6.0.x is not listed** ([SECURITY.md](https://raw.githubusercontent.com/junit-team/junit-framework/main/SECURITY.md)).
- **6.0.3 fixes** a deadlock in `NamespacedHierarchicalStore.computeIfAbsent` and an `@EnabledOnJre` issue ([notes](https://docs.junit.org/6.0.3/release-notes.html)).
- **Project health:** see the 5.14.4 review (Scorecard 8.5, 2026-11-06).

## 5. Known vulnerabilities at this version
**Method:** OSV `POST /v1/query` was not callable from this environment (GET-only fetch, no shell network). I used deps.dev `advisoryKeys`, OSV name search and GitHub advisories.

**None found on 2026-11-06.**
- deps.dev `advisoryKeys: []` for jupiter-engine 6.0.3 and platform-launcher 6.0.3 ([example](https://api.deps.dev/v3/systems/maven/packages/org.junit.platform%3Ajunit-platform-launcher/versions/6.0.3)).
- The only `org.junit.*` OSV advisory, GHSA-m43g-m425-p68x, is for `junit-platform-reporting` from 5.12.0, fixed in 5.13.2. It is not in this set ([osv](https://osv.dev/list?ecosystem=Maven&q=junit)).
- Repository advisories list only that one ([advisories](https://api.github.com/repos/junit-team/junit-framework/security-advisories)).

## 6. License
EPL-2.0 for all POMs. This is test-only use.

## 7. Execution privileges (test phase: :example-app test JVMs)
**The same model as 5.14.4, and unchanged in the 6.0.3 guide** ([launcher API](https://docs.junit.org/6.0.3/advanced-topics/launcher-api.html), [extensions](https://docs.junit.org/6.0.3/extensions/registering-extensions.html)):
- **ServiceLoader auto-registration.** Engines, session and discovery listeners, execution listeners and post-discovery filters are picked up from any JAR on the test runtime classpath.
- **Extension auto-detection and launcher interceptors** are opt-in.
- **Configuration** comes from `junit-platform.properties` and system properties.
- **`@TempDir`** creates directories with `Files.createTempDirectory`, using the `junit-` prefix.
- **No processes or network.**

**Security-relevant 6.0 changes** ([notes](https://docs.junit.org/6.0.3/release-notes.html)):
- Java 17 baseline.
- JSpecify annotations on all modules.
- `@CsvSource` moves from the unmaintained univocity-parsers to FastCSV, with stricter parsing.
- Legacy reflection-search semantics are removed.
- Invalid enum configuration values now fail.
- Control characters in display names are replaced.

## 8. Direct dependencies as published
All are compile scope unless noted ([api POM](https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/6.0.3/junit-jupiter-api-6.0.3.pom)).

| Artifact | Dependencies |
|---|---|
| `junit-jupiter` | api, params; engine (runtime) |
| api | opentest4j 1.3.0, platform-commons 6.0.3, apiguardian-api 1.1.2, **jspecify 1.0.0** |
| engine | platform-engine, api, apiguardian, jspecify 1.0.0 |
| params | api, apiguardian, jspecify 1.0.0 |
| launcher | platform-engine, apiguardian, jspecify 1.0.0 |
| platform-engine | opentest4j, platform-commons, apiguardian, jspecify 1.0.0 |
| platform-commons | apiguardian, jspecify 1.0.0 |

- **POM-only metadata:** `org.junit:junit-bom:6.0.3`, whose dependencyManagement covers only `org.junit.*` at 6.0.3. It has an entry ([BOM](https://repo1.maven.org/maven2/org/junit/junit-bom/6.0.3/junit-bom-6.0.3.pom)).
- **Need their own review:** opentest4j 1.3.0 and apiguardian-api 1.1.2.
- **Covered elsewhere:** jspecify, by the 1.0.1 review. Boot manages 1.0.1.

## 9. Alternatives considered
- **JUnit 6.1.3**, which is supported. That means overriding Boot's managed version and doing a new review.
- **JUnit 5.14.4** in the example. This diverges from Boot's tested set.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **An unsupported line.** If a 6.0.x vulnerability appears, a fix may arrive only in 6.1.x, and with it a Boot override.
- **Shared ServiceLoader trust** on the example's test classpath. The example also brings Spring's own test artifacts, which are outside this review.

## 11. Not verified
- **The release process for 6.0.3.** The workflow at `r6.0.3` and the publishing run were not inspected.
- **Signatures and attestations.** The JARs were not hashed, `.asc` signatures were not checked, and attestations were not queried (api.github.com returned 403).
- **Per-version advisory checks.** deps.dev was not queried individually for api, params, aggregator, platform-engine or platform-commons 6.0.3. For those, only the name search covers them.
- **Behaviour.** This comes from the 6.0.3 user guide pages, not from a run.
