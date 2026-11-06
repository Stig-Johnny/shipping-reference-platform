---
component: "Transitive dependencies of com.google.errorprone:error_prone_core 2.51.0 and com.uber.nullaway:nullaway 0.14.3 on the javac plugin path of :libs:c4-contract-kit"
kind: maven
version: "Exact per artifact, as locked on 2026-11-06 (18 coordinates, section 8)"
verdict: APPROVE-WITH-CONDITIONS
scope: "Build time only: configurations annotationProcessor, errorprone and testAnnotationProcessor of :libs:c4-contract-kit, inside the forked javac JVM, in CI and on local build hosts. Never on a compile, runtime, test or published classpath"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "Only on annotationProcessor, errorprone and testAnnotationProcessor; never on compileClasspath, runtimeClasspath, test classpaths or in a published POM", "Every condition of the Error Prone 2.51.0 and NullAway 0.14.3 reviews holds (forked javac, closed processor path, no -XepPatchChecks/-XepPatchLocation in CI, JarInfer and fix serialisation off)", "guava resolves to 33.7.2-jre on the processor path, although auto-common 1.2.2 asks for 32.0.1-jre and google-java-format 1.37.0 for 33.6.0-jre", "Before first use, the package list of the dataflow-errorprone and dataflow-nullaway JARs is recorded in the bundled-component inventory (v01-design section 3), because both state that they contain the dependencies they require", "The forked compiler JVM's environment, system properties and working tree hold no secrets or tokens", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: the javac plugin path under Error Prone 2.51.0 and NullAway 0.14.3 (18 transitive artifacts)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, POMs and `.sha256` sidecars, deps.dev, Scorecard, OSV and
GitHub repository pages. Nothing was downloaded, built or run. URL prefixes: `MC/` = `https://repo1.maven.org/maven2/`,
`DD/` = `https://api.deps.dev/v3/systems/maven/packages/`, `SC/` = `https://api.securityscorecards.dev/projects/github.com/`.
guava 33.7.2-jre, failureaccess, listenablefuture and j2objc-annotations are on this path too. They are reviewed in the
Structurizr family review. error_prone_annotations 2.51.0 is covered by the Error Prone review.

## 1. Verdict
**APPROVE-WITH-CONDITIONS, build time only.**
- No advisory is known for any of the 18 at its locked version.
- Every published SHA-256 matches the hash we pinned.
- Everything here runs inside javac with the compiler JVM's full rights. The Error Prone review already accepted that
  exposure, and these artifacts are its consequence: Error Prone does not run without them.
- **Weak spots:**
  - `safere` is seven months old, has one maintainer and 25 stars.
  - `software-and-algorithms` 1.0 (2017) is a single-author library whose repository woke up in October 2026 after five
    quiet years.
  - `javax.inject` 1 (2009) has no signature and no published SHA-256.
  - Both `dataflow-*` artifacts are GPL-2.0-with-classpath-exception and bundle undeclared code.
- These are acceptable on a build-only path that fails closed, under the conditions.

## 2. What it is and why it is needed
Error Prone and NullAway are the nullness enforcement the design relies on (`v01-design.md` §4; Error Prone review §2).
Every artifact below is a declared runtime dependency of theirs. None is used by the kit's own code.

## 3. Provenance
All rows have a `.asc` on Central except **javax.inject 1** (no `.asc`; [dir](https://repo1.maven.org/maven2/javax/inject/javax.inject/1/)).
"SHA-256" means that a publisher `.jar.sha256`, or the jar hash in a `.module`, equals our verification-metadata value.
| Artifact | Publisher → source | Central date | SHA-256 |
|---|---|---|---|
| error_prone_annotation, error_prone_check_api 2.51.0 | Google LLC, `com.google.errorprone` → google/error-prone. Built and signed by its release workflow (Error Prone review §3) | 2026-10-22 | match |
| google-java-format 1.37.0 | Google, `com.google.googlejavaformat` → google/google-java-format ([POM](https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/1.37.0/google-java-format-1.37.0.pom)) | 2026-10-01 | match |
| protobuf-java 4.36.2 | Google ("Protocol Buffers"), `com.google.protobuf` → protocolbuffers/protobuf | 2026-09-17 | **none published** |
| caffeine 3.3.0 | Ben Manes, `com.github.ben-manes.caffeine`. Also has `.sigstore.json` and `.module` on Central ([dir](https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/caffeine/3.3.0/)) | 2026-09-21 | match (sidecar and .module) |
| auto-value-annotations 1.11.1; auto-service-annotations 1.1.1; auto-common 1.2.2 | Google LLC, `com.google.auto*` → google/auto | 2025-11-11; 2023-06-12; 2023-06-12 | match; none; none |
| javax.inject 1 | JSR-330. The SCM is a code.google.com URL; the GitHub repo javax-inject/javax-inject was last committed 2016-06-10 ([commits](https://github.com/javax-inject/javax-inject/commits/master/)) | 2009-10-13 | **none, and unsigned** |
| dataflow-errorprone 3.41.0-eisop1 | EISOP (W. Dietl, Waterloo), `io.github.eisop` → eisop/checker-framework, a fork of typetools ([POM](https://repo1.maven.org/maven2/io/github/eisop/dataflow-errorprone/3.41.0-eisop1/dataflow-errorprone-3.41.0-eisop1.pom)) | 2023-12-05 | match |
| dataflow-nullaway 4.3.1; checker-qual 4.3.1 | Checker Framework (UW), `org.checkerframework` → typetools/checker-framework | 2026-10-08 | match (checker-qual also via .module) |
| safere 1.0.0 | Eddie Aftandilian, `org.safere` → eaftan/safere ([POM](https://repo1.maven.org/maven2/org/safere/safere/1.0.0/safere-1.0.0.pom)). The tag shows a GitHub "Verified" signature ([releases](https://github.com/eaftan/safere/releases)) | 2026-09-30 | match |
| software-and-algorithms 1.0 | Kevin L. Stern, `com.github.kevinstern` → KevinStern/software-and-algorithms ([POM](https://repo1.maven.org/maven2/com/github/kevinstern/software-and-algorithms/1.0/software-and-algorithms-1.0.pom)) | 2017-03-11 | none published |
| java-diff-utils 4.17 | Tobias Warneke, `io.github.java-diff-utils` → java-diff-utils/java-diff-utils | 2026-05-16 | match |
| commonmark, commonmark-ext-gfm-tables 0.29.0 | Robin Stocker, `org.commonmark` → commonmark/commonmark-java | 2026-06-20 | match |
| pcollections 5.0.0 | Harold Cooper, `org.pcollections` → hrldcpr/pcollections | 2025-07-23 | match (sidecar and .module) |

- **No SLSA provenance on Central artifacts:** deps.dev `slsaProvenances: []` for all 18 (e.g. [safere](https://api.deps.dev/v3/systems/maven/packages/org.safere%3Asafere/versions/1.0.0)). protobuf's Signed-Releases 10 covers its GitHub release assets, not these jars; caffeine's Sigstore bundle was not verified.
- **The safere developer:** Error Prone's parent POM lists a developer of the same name ([parent](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_parent/2.51.0/error_prone_parent-2.51.0.pom)). That is consistent with, but not proof of, the same person.

## 4. Maintenance and adoption
- **Scorecard** (2026-08-24 to 11-06; `SC/<owner>/<repo>` or the deps.dev project API): caffeine 8.8 (OSS-Fuzz, CII badge); error-prone 7.1; google/auto 6.9; google-java-format 6.8; protobuf 6.2; commonmark-java 4.1; eisop 4.1; java-diff-utils 3.9 (Maintained 1); typetools 3.8; pcollections 2.3 (Maintained 0); javax-inject 1 (Maintained 0). safere and software-and-algorithms: **no Scorecard** (HTTP 404). Code-Review is 0 to 5 everywhere.
- **Newer versions** (deps.dev package pages): google-java-format 2.0.0 (10-31); safere 1.1.0 (11-02, with Error Prone 2.52.0); dataflow-nullaway and checker-qual 4.4.0 (11-03); commonmark 0.32.0; java-diff-utils 4.18; caffeine 3.3.1; **dataflow-errorprone 3.49.5-eisop1** (2026-04-27), while Error Prone 2.51.0 still asks for the 2023-12-05 build ([check_api POM](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_check_api/2.51.0/error_prone_check_api-2.51.0.pom)).
- **Flags:** **safere** was first published 2026-04-06 (0.1.0) and has 25 stars and one developer ([deps.dev project](https://api.deps.dev/v3/projects/github.com%2Feaftan%2Fsafere)). **software-and-algorithms** has published only 1.0; its repository got commits on 2026-10-19, 25 and 30 after none since 2021-05-22, and no new release ([commits](https://github.com/KevinStern/software-and-algorithms/commits/master/)), so read any new release with care. **javax.inject** has been dormant since 2016.

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06** for all 18: `inputs/osv.json` (the OSV batch query of 2026-11-06, not re-run by me) and
deps.dev `advisoryKeys: []` on every version page.
- **protobuf-java:** CVE-2024-7254 / [GHSA-735f-pc8j-v9w8](https://api.osv.dev/v1/vulns/GHSA-735f-pc8j-v9w8) affects versions below 3.25.5, 4.0.0-RC1 to 4.27.5, and 4.28.0-RC1 to 4.28.2. 4.36.2 is outside every range.
- **auto-common** asks for guava 32.0.1-jre, inside the GHSA-xxph-c9ww-hj94 range. Condition 4 keeps 33.7.2-jre.

## 6. License
Apache-2.0: the Error Prone modules, google-java-format, auto-*, caffeine, java-diff-utils, javax.inject. BSD-3-Clause:
protobuf-java, safere. BSD-2-Clause: commonmark. MIT: software-and-algorithms, pcollections, checker-qual (deps.dev labels
the last two "non-standard"). **GPL-2.0 with the classpath exception:** dataflow-errorprone and dataflow-nullaway (POMs
above); they are build tools and are not shipped in any artifact of ours.

## 7. Execution privileges (build time: `compileJava`, `compileTestJava`)
All 18 are loaded into the forked javac JVM as Error Prone/NullAway's runtime. There they have that JVM's full rights:
- read every source and classpath file;
- write files, open sockets and start processes, with no sandbox (Error Prone review §7, NullAway review §7).

Their inputs are our own sources and classpath.

| Kind | Artifacts |
|---|---|
| Analysis engines that walk our ASTs | check_api, dataflow-errorprone, dataflow-nullaway (shaded copies of the Checker Framework dataflow: "packages … renamed to org.checkerframework.errorprone.*" / "….nullaway.*", and they bundle "the dependencies it requires", per the POMs) |
| Parsers of their own input formats | protobuf-java (binary protobuf), commonmark (Markdown), safere (regular expressions; "linear-time … based on RE2", [repo](https://github.com/eaftan/safere)) |
| Code generation for suggested fixes and diffs | google-java-format, java-diff-utils. Which Error Prone paths call them was **not traced**. Patching is off in CI (Error Prone condition 4) |
| Libraries | caffeine (caches), pcollections, auto-common, software-and-algorithms (Hungarian, Damerau-Levenshtein and others, per its commit log) |
| Annotations only | error_prone_annotation, auto-value-annotations, auto-service-annotations, javax.inject, checker-qual |

None reaches the kit's runtime. **Fail-closed:** a missing or broken class here crashes javac, so the build fails rather
than skipping checks.

## 8. Direct dependencies as published
- **error_prone_core** → error_prone_annotation, check_api, google-java-format, protobuf-java, auto-common, auto-value-annotations, auto-service-annotations, javax.inject, pcollections, dataflow-errorprone, guava (Error Prone review §8).
- **check_api** → error_prone_annotation, dataflow-errorprone, java-diff-utils, software-and-algorithms, caffeine, javax.inject, safere, auto-value-annotations (POM above).
- **google-java-format** → guava (asks 33.6.0-jre), commonmark, commonmark-ext-gfm-tables. Optional: jspecify, error_prone_annotations, auto-*.
- **auto-common** → guava (asks 32.0.1-jre); javapoet is optional and not resolved ([POM](https://repo1.maven.org/maven2/com/google/auto/auto-common/1.2.2/auto-common-1.2.2.pom)).
- **caffeine** → jspecify, error_prone_annotations.
- **NullAway** → dataflow-nullaway (→ checker-qual 4.3.1).
- **commonmark-ext-gfm-tables** → commonmark.
- **No dependencies:** safere, software-and-algorithms, pcollections, java-diff-utils, protobuf-java, dataflow-errorprone, javax.inject.
- **POM metadata only, not in the lockfile:** error_prone_parent 2.50.0/2.51.0, google-java-format-parent 1.37.0, protobuf-parent and protobuf-bom 4.36.2, auto-value-parent 1.11.1, auto-service-aggregator 1.1.1, java-diff-utils-parent 4.17, commonmark-parent 0.29.0 and oss-parent 7. Each was checked by a subagent; none imports another BOM except protobuf-parent, which imports protobuf-bom ([parent](https://repo1.maven.org/maven2/com/google/protobuf/protobuf-parent/4.36.2/protobuf-parent-4.36.2.pom)).

## 9. Alternatives considered, and exclusions
- **No Error Prone/NullAway:** "annotations alone do not enforce nullness" (design §4).
- **The `-with-dependencies` jar:** it hides 17 shaded libraries from the checksum inventory. The Error Prone review forbids it.
- **Exclusions.** None is recommended for v0.1, because the call sites were not traced. java-diff-utils and google-java-format may only serve suggested fixes. Excluding them would fail closed. The test must prove all of the following:
  - main and test compile with the exclusion;
  - a fixture with a known Error Prone error (e.g. `DeadException`) still fails;
  - a fixture with a NullAway error still fails;
  - both report the expected diagnostic, so the checks demonstrably ran.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **Compiler JVM rights on every build host.** Three thinly maintained single-author libraries (safere, software-and-algorithms, pcollections) and an unsigned 2009 jar run there. Version pinning plus SHA-256 is the control; a compromised maintainer account is the threat.
- **Bundled classes.** The dataflow jars carry classes that our inventory does not see until condition 5 is met.

## 11. Not verified
- **Signatures and hashes:** no JAR was downloaded or inspected, and no `.asc` was checked. For protobuf-java, software-and-algorithms, auto-service-annotations, auto-common and javax.inject the hash is trust-on-first-download, because no publisher SHA-256 exists.
- **Call sites:** which Error Prone classes use protobuf, google-java-format, java-diff-utils, commonmark and safere.
- **Bundled contents** of the two dataflow jars.
- **The safere developer's identity.**
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in.
