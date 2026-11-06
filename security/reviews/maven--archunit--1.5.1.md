---
component: "com.tngtech.archunit:archunit"
kind: maven
version: "1.5.1"
verdict: APPROVE-WITH-CONDITIONS
scope: "Runtime library of :core (compile and runtime classpaths) for bytecode import, reaching the test classpaths of :core, :junit5 and :example-app through :core"
conditions: ["The library imports only explicit first-party class-file locations (file paths of production outputs) with importPaths/importLocations. It never calls importClasspath, importPackages, importUrl with a non-file URL, or adhereToPlantUmlDiagram(URL)", "No call to any reflect() method. Enforced by an ArchUnit self-test on :core", "No FreezingArchRule in the library. :core's JAR ships no archunit.properties, and any archunit.properties in this repository is code-owned", "No Java deserialization of untrusted data in any JVM that loads ArchUnit: its shaded Guava 33.7.1-jre is affected by GHSA-xxph-c9ww-hj94. Re-review when ArchUnit ships Guava 33.7.2 or later", "org.slf4j:slf4j-api 2.0.19 has its own approved review before use", "The shaded-content inventory records ASM 9.10.1 and Guava 33.7.1-jre inside archunit-1.5.1.jar", "The resolved JAR SHA-256 equals a4dbfc51c90005ad6ac9967672a7efb43aa6928287a57238b28e60e0a75c5c6f. Any other version or hash is a new review", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: com.tngtech.archunit:archunit 1.5.1

## 1. Verdict
**APPROVE-WITH-CONDITIONS.**
- **Fit.** ArchUnit parses bytecode with ASM and does not load the classes it imports. That fits the design's "without loading application classes".
- **Maintainers and publisher.** Several maintainers in a company-backed org, no known advisory against the artifact, and one runtime dependency.
- **Shaded Guava.** It shades Guava 33.7.1-jre, which a later advisory (GHSA-xxph-c9ww-hj94) affects. The advisory is not reachable in our use.
- **Other weak points.** Releases are signed with keys from Gradle properties outside any visible CI workflow, and there is no provenance.

## 2. What it is and why it is needed
It is the bytecode backend that extracts first-party dependency edges and evidence. The design chose it because "its dependency model reduces implementation risk for one developer", and defers the JDK Class-File API (repository design note `v01-design.md`, section 2).

## 3. Provenance
**Publisher**
- Namespace `com.tngtech.archunit`, repo `github.com/TNG/ArchUnit`. The org is TNG Technology Consulting GmbH; the POM lists 6 developers ([POM](https://repo1.maven.org/maven2/com/tngtech/archunit/archunit/1.5.1/archunit-1.5.1.pom)).

**Release.** Tag `v1.5.1` is commit `6c2d659e5681b50541930d236a8c46aed5e44a5c` ([tags](https://api.github.com/repos/TNG/ArchUnit/tags)); release published 2026-09-25T07:27:04Z by `schulzjo-tng` ([releases](https://api.github.com/repos/TNG/ArchUnit/releases)); Central files dated 2026-09-25 07:03 ([listing](https://repo1.maven.org/maven2/com/tngtech/archunit/archunit/1.5.1/)).

**Build and signing.**
- Gradle `signing` with `useInMemoryPgpKeys(signingKey, signingPassword)` from Gradle properties ([release-publishing conventions](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/buildSrc/src/main/groovy/archunit.java-release-publishing-conventions.gradle)); publishing through `gradle-nexus` to the Central staging API, gated by `-PactivateReleaseToMavenCentral` ([publish.gradle](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/build-steps/release/publish.gradle)); a check task re-downloads the deployment and compares POM and content before release ([release-check](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/buildSrc/src/main/groovy/archunit.java-release-check-conventions.gradle)).
- **No workflow publishes**: the tag's `.github/workflows` holds only `build.yml`, `check-dependencies.yml` and `update-gradle-wrapper.yml` ([workflows](https://github.com/TNG/ArchUnit/tree/v1.5.1/.github/workflows)). The release commit's `actions-user` identity is git metadata and proves nothing, so the build host is unknown.
- Every file has a `.asc`; no SLSA provenance ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.tngtech.archunit%3Aarchunit/versions/1.5.1)).

**Shading.** shadowJar relocates `org.objectweb.asm` and `com.google.{common,thirdparty}` under `com.tngtech.archunit.thirdparty` and excludes `org/apache/**`, `Log4j*` and `module-info.class` ([production conventions](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/buildSrc/src/main/groovy/archunit.java-production-conventions.gradle), [build.gradle](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/build.gradle)). Versions: ASM `9.10.1`, Guava `33.7.1-jre`, with Guava's `listenablefuture`, `jspecify`, `error_prone_annotations` and `j2objc-annotations` excluded ([libs.versions.toml](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/gradle/libs.versions.toml)).

**SHA-256.** `a4dbfc51c90005ad6ac9967672a7efb43aa6928287a57238b28e60e0a75c5c6f` from [`archunit-1.5.1.jar.sha256`](https://repo1.maven.org/maven2/com/tngtech/archunit/archunit/1.5.1/archunit-1.5.1.jar.sha256); two independent reads matched.

## 4. Maintenance and adoption
- **Releases.** 1.4.2 (2026-04-18), 1.5.0 (2026-08-04) and 1.5.1 (2026-09-25). 1.5.1 is the newest; the repo was last pushed 2026-11-03.
- **Repo.** Created 2017-04-21, with 3,892 stars ([repo](https://api.github.com/repos/TNG/ArchUnit)).
- **Contributors.** `codecholeric` 1,779, `hankem` 293, `rweisleder` 34 and `schulzjo-tng` 33, plus bots ([contributors](https://api.github.com/repos/TNG/ArchUnit/contributors)).
- **OpenSSF Scorecard 5.5** (2026-11-02, main): 10 for Maintained, Code-Review, Dangerous-Workflow, License, Packaging; 8 Binary-Artifacts; 0 for Token-Permissions, Pinned-Dependencies, Security-Policy, SAST, Fuzzing, CII-Best-Practices ([scorecard](https://api.securityscorecards.dev/projects/github.com/TNG/ArchUnit)).
- **No SECURITY.md** ([main](https://raw.githubusercontent.com/TNG/ArchUnit/main/SECURITY.md) returns 404; Scorecard: "security policy file not detected").
- **Diff v1.5.0 → v1.5.1.** 42 commits ([compare](https://github.com/TNG/ArchUnit/compare/v1.5.0...v1.5.1)).
  - Library fixes: #1725, caught exception types counted as dependencies, and #1687, `java.lang.IO` added to the standard-streams rule.
  - `libs.versions.toml`: Guava 33.6.0-jre → 33.7.1-jre and slf4j 2.0.18 → 2.0.19. ASM unchanged.

## 5. Known vulnerabilities at this version
**Method:** as in every review in this set. OSV `POST /v1/query` was not callable (GET-only fetch, no shell network), so I used deps.dev `advisoryKeys`, the OSV name search and GitHub advisories.

- **archunit 1.5.1: none found on 2026-11-06.**
  - deps.dev `advisoryKeys: []`.
  - The OSV search "archunit" returned no results ([osv](https://osv.dev/list?ecosystem=Maven&q=archunit)).
  - Repo advisories are `[]` ([advisories](https://api.github.com/repos/TNG/ArchUnit/security-advisories)).
- **Shaded Guava 33.7.1-jre: GHSA-xxph-c9ww-hj94 / CVE-2026-102554** ([osv](https://api.osv.dev/v1/vulns/GHSA-xxph-c9ww-hj94)).
  - It was published 2026-10-27, after this release, and is fixed in 33.7.2.
  - Java deserialization of a crafted `CompactHashMap`/`CompactHashSet` stream triggers huge allocations (DoS).
  - **Not reachable:** the import parses class files and does not deserialize, and the freeze store is text. Neither our checker nor its tests read untrusted serialized data.
  - **Scanners miss it:** deps.dev's graph does not show the shaded copy ([graph](https://api.deps.dev/v3/systems/maven/packages/com.tngtech.archunit%3Aarchunit/versions/1.5.1:dependencies)).
- **Shaded ASM 9.10.1 and slf4j-api 2.0.19:** deps.dev `[]`.

## 6. License
- Apache-2.0, with BSD for the bundled ASM. The POM declares both ([POM](https://repo1.maven.org/maven2/com/tngtech/archunit/archunit/1.5.1/archunit-1.5.1.pom)).

## 7. Execution privileges (runtime: test JVMs running the conformance check)
Sources are the user guide sources at the tag ([006_The_Core_API.adoc](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/docs/userguide/006_The_Core_API.adoc), [010_Advanced_Configuration.adoc](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/docs/userguide/010_Advanced_Configuration.adoc)) and the source.

**Importing**
- **Reads class files and parses them with ASM.** `ClassFileImporter` reads directories, JARs, single files and `jar:`/`file:` URIs; it can also take a `URL`.
- **No class loading.** "ArchUnit's own rule APIs … never rely on the classpath."
- **Missing dependencies are resolved from the classpath by default** (`resolveMissingDependenciesFromClassPath=true`). `ClassResolverFromClasspath` calls `ClassLoader.getResource("<name>.class")` and parses the bytes, without `Class.forName` ([source](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/archunit/src/main/java/com/tngtech/archunit/core/importer/resolvers/ClassResolverFromClasspath.java)).
- **`reflect()` loads classes.** It "will … try to resolve the respective object from the Reflection API".

**Configuration can instantiate classes**
- Configuration comes from the first `archunit.properties` at the classpath root (via the context ClassLoader), plus `-Darchunit.*` system properties.
- Properties that name classes for ArchUnit to instantiate: `classResolver`, `freeze.store` (`Class.forName` in `ViolationStoreFactory`), `failureDisplayFormat` and `freeze.lineMatcher`.

**Files written** only by `FreezingArchRule`: `archunit_store/` relative to the working directory (`stored.rules` plus UUID-named files); store creation is off by default ([TextFileBasedViolationStore](https://raw.githubusercontent.com/TNG/ArchUnit/v1.5.1/archunit/src/main/java/com/tngtech/archunit/library/freeze/TextFileBasedViolationStore.java)).

**Network and processes:** none documented; not verified for `importUrl(URL)` and `adhereToPlantUmlDiagram(URL)` with non-file schemes, hence condition 1. **Build time:** none (a plain library, no annotation processor or plugin).

## 8. Direct dependencies as published
- `org.slf4j:slf4j-api` 2.0.19, compile (MIT; deps.dev `[]`). Needs its own review.
- That is the only declared dependency ([POM](https://repo1.maven.org/maven2/com/tngtech/archunit/archunit/1.5.1/archunit-1.5.1.pom)).
- **Shaded inside the JAR, so not resolved by Gradle:** ASM 9.10.1 and Guava 33.7.1-jre, plus any Guava dependency not excluded, e.g. `failureaccess`. The exact file list was not inspected.

## 9. Alternatives considered
- **JDK Class-File API (Java 24+).** It parses bytecode but leaves dependency semantics to us; the design defers it.
- **A hand-written ASM extractor.** More code to own, and the same ASM.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk**
1. **The release is built and signed outside any visible CI workflow** (host unknown), with no provenance or visible signed tag. Pinning plus re-review is the control.
2. **Shaded code** hides Guava and ASM from dependency scanners. Only the build files reveal their versions.
3. **The configuration is global.** A consumer's `archunit.properties` or `-Darchunit.*` can change how our checker imports. That is their own trust boundary, but it can change report contents.
4. **Supply-chain hygiene upstream is weak:** Scorecard 0 on pinned dependencies and token permissions.

## 11. Not verified
- **The JAR:** not downloaded, hashed or inspected (shaded file list unknown); `.asc` not checked; whether the `.sha256` file is publisher- or repository-generated is unknown.
- **The build:** that the JAR was built from tag `v1.5.1`, and on which host; commit and tag signature status (api.github.com rate-limited, no badge in the HTML).
- **The diff.** The compare page did not render its file list, so the 33 changed files were not read individually.
- **Behaviour.** Read from docs and source, not executed. URL handling for non-file schemes is unknown.
