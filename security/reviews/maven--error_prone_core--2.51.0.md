---
component: "com.google.errorprone:error_prone_core:2.51.0 (with com.google.errorprone:error_prone_annotations:2.51.0)"
kind: maven
version: "2.51.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "Build time only: an `errorprone` configuration extended by `annotationProcessor` and `testAnnotationProcessor` of every Java project (apps/shipping-api, libs/c4-contract-kit), JDK 25, in CI and on local build hosts. error_prone_annotations may also sit on `compileOnly`. Never on a runtime classpath."
conditions: ["Exactly error_prone_core 2.51.0 and error_prone_annotations 2.51.0, the plain jars (never `-with-dependencies`), under Gradle locking and SHA-256 verification metadata", "Wired without the third-party Gradle plugin: `options.isFork = true`, the ten jdk.compiler `--add-exports`/`--add-opens` flags in `forkOptions.jvmArgs`, and compilerArgs `-XDcompilePolicy=simple`, `--should-stop=ifError=FLOW`, `-Xplugin:ErrorProne …`", "The processor path holds only error_prone_core, NullAway 0.14.3 and their locked transitives; no other processor or plugin", "No `-XepPatchChecks`/`-XepPatchLocation` in CI; patching is a local, attended operation", "org.safere:safere 1.0.0, io.github.eisop:dataflow-errorprone 3.41.0-eisop1, com.google.protobuf:protobuf-java 4.36.2, com.google.googlejavaformat:google-java-format 1.37.0 and com.github.kevinstern:software-and-algorithms 1.0 get their own allowlist entries before first use", "The lock resolves guava 33.7.2-jre on the processor path"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Error Prone 2.51.0, run as a plain javac plugin

**Method.** On 2026-11-06 the following were read through WebFetch: the Maven Central listings and POMs, the deps.dev API, the osv.dev list, Scorecard, the errorprone.info docs, the GitHub release pages and `release.yml` at the tag. Nothing was downloaded, built or run. `api.github.com` refused the fetches (HTTP 403).

## 1. Verdict
**APPROVE-WITH-CONDITIONS, for build time only.** The publisher is Google, and releases are built and GPG-signed by a SHA-pinned GitHub Actions workflow. No advisory is known for 2.51.0 or for any direct dependency at its resolved version. The conditions do three things: fix how it is wired without the Gradle plugin, keep the compiler's processor path closed, and send five transitives for their own review. One of those is five weeks old; one is GPL-2.0 with the classpath exception. 2.51.0 is not the newest release: 2.52.0 came out on 2026-11-02 and its notes list no security fix ([v2.52.0](https://github.com/google/error-prone/releases/tag/v2.52.0)). So the blueprint's pin stands, and an upgrade is a new review.

## 2. What it is and why it is needed
Error Prone is a javac plugin that turns known bug patterns into compile errors. The blueprint (§3) enables it with package-level `@NullMarked` and NullAway. NullAway is itself an Error Prone check, so Error Prone is its host. v01-design §4 deferred it because "compiler coupling expands initial review"; the blueprint supersedes that. `error_prone_annotations` holds the annotations the checks read, such as `@CanIgnoreReturnValue`.

## 3. Provenance
- **Publisher.** Google LLC is the `<organization>`; the namespace is `com.google.errorprone`; the source is github.com/google/error-prone ([parent POM](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_parent/2.51.0/error_prone_parent-2.51.0.pom)).
- **Build and signing** ([release.yml@v2.51.0](https://raw.githubusercontent.com/google/error-prone/v2.51.0/.github/workflows/release.yml)):
  - `workflow_dispatch` only; top-level `permissions: {}`, job `contents: write`.
  - Actions pinned by full SHA; Maven 3.9.16 checked against a SHA-512.
  - `mvn -P release clean deploy`, with the GPG key from secrets (`maven-gpg-plugin` 3.2.8) and `central-publishing-maven-plugin` 0.11.0.
  - The tag is made in CI by a plain `git tag`, which makes a lightweight, unsigned tag.
  - Upstream hygiene, not our exposure: it puts the GPG passphrase on a `run:` line and the token in a remote URL.
- **No Sigstore or SLSA provenance:** `slsaProvenances: []` ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.google.errorprone%3Aerror_prone_core/versions/2.51.0)).
- **Central files.** Every file has `.asc`, `.md5`, `.sha1`, `.sha256` and `.sha512`, dated 2026-10-22 09:12 ([listing](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_core/2.51.0/)).
- **SHA-256, from the publisher's `.sha256` sidecars:**
  - core: `9a2e2d326fc686c7a0497ab508fde32c298f59cbb78b88e3f26a24fa8e30c31d` ([sidecar](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_core/2.51.0/error_prone_core-2.51.0.jar.sha256))
  - annotations: `51a6d8348c7e307e47183859c07597dc8a2899c5d1b6f18a471bccaa3203d5cf` ([sidecar](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.51.0/error_prone_annotations-2.51.0.jar.sha256))
- **Tag.** `v2.51.0` is commit `3e9c316c3e3f4dc8b3a2011701a6488e1c23e680`, released 22 Oct ([release](https://github.com/google/error-prone/releases/tag/v2.51.0)).

## 4. Maintenance and adoption
- **Releases** ([atom](https://github.com/google/error-prone/releases.atom)): 2.45.0 (2025-11-27), 2.46.0/2.47.0 (2026-02-05), 2.48.0 (02-27), 2.49.0 (04-07), 2.50.0 (06-11), 2.51.0 (10-22), 2.52.0 (11-02). Central's `latest` is 2.52.0 ([metadata](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_core/maven-metadata.xml)).
- **Scorecard 7.1** (2026-10-26; [scorecard](https://api.securityscorecards.dev/projects/github.com/google/error-prone)):
  - 10: Maintained, Pinned-Dependencies, Dangerous-Workflow, Token-Permissions, Security-Policy.
  - **Code-Review 0** ("0/28 approved changesets"); SAST 0; Fuzzing 0.
  - Branch-Protection -1 (scanner error).
- **Usage.** 7,268 stars and 832 forks ([deps.dev](https://api.deps.dev/v3/projects/github.com%2Fgoogle%2Ferror-prone)).
- **Security policy.** Reports go to g.co/vulnz; there are no published advisories ([security](https://github.com/google/error-prone/security)).

## 5. Known vulnerabilities at 2.51.0
**None found on 2026-11-06.**
- The osv.dev list for `com.google.errorprone` is empty ([OSV](https://osv.dev/list?ecosystem=Maven&q=com.google.errorprone)).
- deps.dev `advisoryKeys` is `[]` for core and annotations, and for every dependency in §8 at its resolved version.
- guava 33.7.2-jre is the *fixed* version for GHSA-xxph-c9ww-hj94, which affects versions up to 33.7.1 ([OSV](https://api.osv.dev/v1/vulns/GHSA-xxph-c9ww-hj94)).

## 6. License
Apache-2.0 ([POM](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_core/2.51.0/error_prone_core-2.51.0.pom)). Transitive licences:
- `dataflow-errorprone` is "GPL2 with the classpath exception" ([POM](https://repo1.maven.org/maven2/io/github/eisop/dataflow-errorprone/3.41.0-eisop1/dataflow-errorprone-3.41.0-eisop1.pom)). It is a build tool and is not shipped with our artifacts, but record it.
- protobuf-java and safere are BSD-3-Clause; commonmark is BSD-2-Clause.

## 7. Execution privileges
- **Build only** (`compileJava`, `compileTestJava`). It runs inside javac, in the forked compiler JVM, with that JVM's full rights: it reads every source and classpath entry, and it could write files, open sockets or start processes. There is no sandbox.
- **javac internals.** It needs eight exports and two opens of `jdk.compiler` ([installation](https://errorprone.info/docs/installation)), meaning it reaches into javac internals by reflection.
- **Plugins.** It loads checks from the processor path (NullAway is one), so anything placed there runs in the compiler.
- **Writing sources.** `-XepPatchLocation` writes `error-prone.patch`, and `IN_PLACE` rewrites sources ([patching](https://errorprone.info/docs/patching)).
- **Network and processes.** None documented; not audited.
- **Runtime.** Nothing: `error_prone_annotations` has no compile or runtime dependency.

**JDK 25 flags, from the docs.** "Error Prone must be run on JDK 21 or newer." `-XDaddTypeAnnotationsToSymbol=true` is "necessary only if building with JDK21", so it is not needed on 25. For Gradle, the docs point only to the external plugin.

**Gradle wiring.** Gradle's `ForkOptions.jvmArgs` are "additional JVM arguments for the compiler process", applied when `fork` is true ([ForkOptions](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.ForkOptions.html)). So the docs' `-J…` flags go there without `-J`. This is inferred and was not run:
```kotlin
val errorprone: Configuration by configurations.creating
listOf("annotationProcessor", "testAnnotationProcessor").forEach { configurations.named(it) { extendsFrom(errorprone) } }
dependencies { errorprone("com.google.errorprone:error_prone_core:2.51.0"); errorprone("com.uber.nullaway:nullaway:0.14.3") }
tasks.withType<JavaCompile>().configureEach {
    options.release = 25; options.isFork = true
    options.forkOptions.jvmArgs = listOf("api", "file", "main", "model", "parser", "processing", "tree", "util")
        .map { "--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" } +
        listOf("code", "comp").map { "--add-opens=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" }
    options.compilerArgs.addAll(listOf("-XDcompilePolicy=simple", "--should-stop=ifError=FLOW",
        "-Xplugin:ErrorProne -XepDisableWarningsInGeneratedCode -Xep:NullAway:ERROR -XepOpt:NullAway:OnlyNullMarked=true"))
}
```

## 8. Direct dependencies as published
These are the POM's compile scope, with versions from the parent and the [deps.dev graph](https://api.deps.dev/v3/systems/maven/packages/com.google.errorprone%3Aerror_prone_core/versions/2.51.0:dependencies):

| Dependency | What it is |
|---|---|
| error_prone_annotation, error_prone_check_api 2.51.0 | Same publisher: bug-pattern metadata and the check API |
| google-java-format 1.37.0 | Formats suggested fixes. **Flag** |
| guava 33.7.2-jre; auto-common 1.2.2; auto-value-annotations 1.11.1; auto-service-annotations 1.1.1; javax.inject 1 | Google libraries and annotations |
| pcollections 5.0.0 | Persistent collections |
| dataflow-errorprone 3.41.0-eisop1 | EISOP Checker Framework dataflow (2023-12-05). **Flag:** GPL2+CPE, old pin |
| protobuf-java 4.36.2 | Protocol Buffers runtime. **Flag:** a large parser |
| jspecify 1.0.1; error_prone_annotations 2.51.0 | Annotations; JSpecify is already pinned by the blueprint |

**Indirect**, through check_api ([POM](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_check_api/2.51.0/error_prone_check_api-2.51.0.pom)):
- caffeine 3.3.0, java-diff-utils 4.17, commonmark 0.29.0, failureaccess 1.0.3, j2objc-annotations 3.1.
- software-and-algorithms 1.0: published 2017 by a single author. **Flag.**
- **safere 1.0.0. Flag.** "Replaced the RE2/J dependency with SafeRE" (2.51.0 notes). It was first published on 2026-09-30 from the personal repo eaftan/safere, which has 25 stars ([POM](https://repo1.maven.org/maven2/org/safere/safere/1.0.0/safere-1.0.0.pom)). 2.52.0 moves it to 1.1.0.

## 9. Alternatives considered
- The `net.ltgt.errorprone` Gradle plugin that Error Prone's docs point to. It is more third-party build code; the wiring above replaces it.
- JSpecify annotations without a checker give no enforcement (v01-design §4).

## 10. Conditions and residual risk
**Conditions:** as in the frontmatter.

**Residual risk:**
- Build-time code with full JVM rights on CI runners and developer machines. Keep secrets out of the compile environment.
- Upstream changes show no GitHub-visible review (Code-Review 0), and there is no build provenance.
- The `-with-dependencies` jar would hide 17 shaded libraries from our checksum inventory. Condition 1 forbids it.

## 11. Not verified
- No jar was downloaded or hashed. The SHA-256 values are Central's sidecars. The `.asc` signatures and the key were not checked.
- The source was not audited for network, process or file behaviour.
- The Gradle snippet was not run.
- NullAway 0.14.3 is built against Error Prone 2.50.0; it is not tested upstream with 2.51.0.
- Contributor counts were not retrieved (`api.github.com` 403).
- OSV's `POST /v1/query` was not used: WebFetch is GET-only, so the osv.dev list and deps.dev (fed by OSV) stand in.
