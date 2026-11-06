---
component: "org.jspecify:jspecify"
kind: maven
version: "1.0.1"
verdict: APPROVE-WITH-CONDITIONS
scope: "Any compile, runtime and test classpath of :core, :junit5 and :example-app. Published as a compile-scope (api) dependency of :core and :junit5, as JSpecify recommends"
conditions: ["Every configuration resolves exactly 1.0.1. Guava 33.6.0-jre (via Structurizr) and JUnit 6.0.3 request 1.0.0, which is not reviewed", "A one-time inventory of the resolved JAR at bootstrap shows only org/jspecify/annotations/*.class, module-info.class and META-INF/MANIFEST.MF, and no META-INF/services. The JAR was not inspected in this review", "The resolved JAR SHA-256 equals 070d75f261fe4c5b8202508366715f7f2d4660f88c8ef7e6d3575e48c9683b66 (from the publisher's Gradle module metadata). Any other version or hash is a new review", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: org.jspecify:jspecify 1.0.1

## 1. Verdict
**APPROVE-WITH-CONDITIONS.**
- **What it is.** Four annotation types and a `module-info`, with no dependencies and no executable code in the source.
- **Why conditions anyway.** The release appears to be built and signed outside CI, the tag carries no verified signature, and the published JAR was not inspected. So the approval rests on two conditions: one resolved version, and a one-time JAR inventory.

## 2. What it is and why it is needed
It expresses public nullness contracts on the library API. "Annotations alone do not enforce nullness"; Error Prone and NullAway are deferred (repository design note `v01-design.md`, section 4).

## 3. Provenance
- **Publisher.** The JSpecify group, namespace `org.jspecify`, repo `github.com/jspecify/jspecify`. POM developers: `kevinb9n`, `netdpb` and `cpovirk` ([POM](https://repo1.maven.org/maven2/org/jspecify/jspecify/1.0.1/jspecify-1.0.1.pom)).
- **Release.**
  - Central files are dated 2026-07-29 22:41 ([listing](https://repo1.maven.org/maven2/org/jspecify/jspecify/1.0.1/)).
  - The GitHub release, 2026-07-29T22:51:04Z, is by `cpovirk`. Its tag commit is `ce9bec0b8895f424d999d31e3a1bd33694ee4c0c`, "Set version to 1.0.1." ([release](https://github.com/jspecify/jspecify/releases/tag/v1.0.1)).
  - On the [tags page](https://github.com/jspecify/jspecify/tags), `v1.0.1` shows **no "Verified" badge**, while older tags (`spec-1.0.0`, `v0.1`) do.
- **Build and signing.**
  - The vanniktech `maven-publish` plugin runs `signAllPublications()` with `useInMemoryPgpKeys(privateKey, pgpPassword)` from Gradle properties ([publish.gradle](https://raw.githubusercontent.com/jspecify/jspecify/v1.0.1/gradle/publish.gradle)).
  - None of the three workflows publishes the annotations JAR ([workflows](https://github.com/jspecify/jspecify/tree/v1.0.1/.github/workflows)), so it was most likely published from a maintainer's machine. That is an inference.
  - The build sets reproducible archives (`preserveFileTimestamps = false`, `reproducibleFileOrder = true`; bnd `-reproducible: true`) ([build.gradle](https://raw.githubusercontent.com/jspecify/jspecify/v1.0.1/build.gradle)). A rebuild could be compared but was not.
  - Every file has a `.asc`, including `.module.asc`. There is no SLSA provenance ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.jspecify%3Ajspecify/versions/1.0.1)).
- **SHA-256.**
  - Central has **no** `.sha256` for this release.
  - The value comes from the publisher's signed Gradle module metadata ([`jspecify-1.0.1.module`](https://repo1.maven.org/maven2/org/jspecify/jspecify/1.0.1/jspecify-1.0.1.module)): `070d75f2…683b66`.
  - Its `sha1` equals the published [`.jar.sha1`](https://repo1.maven.org/maven2/org/jspecify/jspecify/1.0.1/jspecify-1.0.1.jar.sha1), `3d60fd98…02cf3d7`, and its size (3,064 bytes) equals the listing. It was read twice in independent fetches.

## 4. Maintenance and adoption
- **Releases.** 0.2.0 (2021), 0.3.0 (2022-12), 1.0.0 (2024-07-16) and 1.0.1 (2026-07-29). 1.0.1 is the newest ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.jspecify%3Ajspecify)). A slow cadence is expected for a specification artifact.
- **What 1.0.1 changed.** "small changes to documentation and build setup but no semantic changes" ([releases](https://api.github.com/repos/jspecify/jspecify/releases)):
  - `module-info.class` moved to the JAR root.
  - The module version is now set.
- **Diff from v1.0.0.** 176 commits and 43 files. Commits under `src/` are, by title, Javadoc and link edits ([commits](https://github.com/jspecify/jspecify/commits/v1.0.1/src)).
- **Repo.** Created 2018, 1,156 stars. Contributors: `cpovirk` 355, `netdpb` 62, `wmdietl` 37 ([contributors](https://api.github.com/repos/jspecify/jspecify/contributors)).
- **Adoption.** Spring Boot 4.1.2 manages `jspecify` 1.0.1 ([coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)).
- **OpenSSF Scorecard 5.6** (2026-11-02) ([scorecard](https://api.securityscorecards.dev/projects/github.com/jspecify/jspecify)):
  - 10: Code-Review, Maintained, License, Packaging.
  - 0: Pinned-Dependencies, Token-Permissions, Security-Policy, SAST.

## 5. Known vulnerabilities at this version
**Method:** OSV `POST /v1/query` was not callable (GET-only fetch, no shell network). I used deps.dev `advisoryKeys`, which come from OSV.

- **Result:** none found on 2026-11-06. deps.dev reports `advisoryKeys: []` for 1.0.1, and also for 1.0.0.

## 6. License
Apache-2.0 ([POM](https://repo1.maven.org/maven2/org/jspecify/jspecify/1.0.1/jspecify-1.0.1.pom)).

## 7. Execution privileges (compile time and runtime: annotations only)
**Source at the tag:** `src/main/java/org/jspecify/annotations/` holds `Nullable`, `NonNull`, `NullMarked`, `NullUnmarked` and `package-info` ([tree](https://github.com/jspecify/jspecify/tree/v1.0.1/src/main/java/org/jspecify/annotations)).
- Each is an empty `@interface` with `@Retention(RUNTIME)` and only `java.lang.annotation` imports ([Nullable.java](https://raw.githubusercontent.com/jspecify/jspecify/v1.0.1/src/main/java/org/jspecify/annotations/Nullable.java)).
- `module-info.java` is only `module org.jspecify { exports org.jspecify.annotations; }`, with no `provides` or `uses` ([module-info.java](https://raw.githubusercontent.com/jspecify/jspecify/v1.0.1/src/java9/java/module-info.java)).

**What it can do:**
- No methods, initializers, network, processes or files.
- At runtime, other code can read the annotations reflectively.
- **Build time:** read by javac and other tools; it is not an annotation processor.

## 8. Direct dependencies as published
None. Both the POM and the `.module` declare none, and the target JVM is 8 ([.module](https://repo1.maven.org/maven2/org/jspecify/jspecify/1.0.1/jspecify-1.0.1.module)).

**Other versions requested in this build:**
- `jspecify` 1.0.0, from Guava 33.6.0-jre (Structurizr's tree) and from JUnit 6.0.3.
- PIT 1.30.2 uses 1.0.0 only in test scope, so it is not resolved.

## 9. Alternatives considered
- **No annotations**, with Javadoc only. Nothing to review, but no tool-readable contract.
- **JSR-305 `@Nullable`.** Dormant since 2017 (`jsr305` 3.0.2), but already present in Structurizr's tree.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:** minimal once the inventory condition holds. An annotations-only JAR runs no code unless something loads classes from it.
- The theoretical exposure is a JAR that differs from the source, for example one carrying a `META-INF/services` entry.
- The inventory check closes that exposure.

## 11. Not verified
- **The JAR itself.** Its contents were not inspected, and the `.asc` signatures and signer key were not checked.
- **The build host.** Where the build ran is unknown, and no rebuild was compared for reproducibility.
- **Source coverage.** All five files in `org/jspecify/annotations` and `module-info.java` were read at the tag, but the 43 files changed between 1.0.0 and 1.0.1 were not diffed individually (the compare view did not render).
- **The OSV name search** for `jspecify` was not run. The vulnerability result rests on deps.dev only.
