---
component: "com.uber.nullaway:nullaway:0.14.3"
kind: maven
version: "0.14.3"
verdict: APPROVE-WITH-CONDITIONS
scope: "Build time only: the same `errorprone` configuration as Error Prone 2.51.0 (extended by `annotationProcessor` and `testAnnotationProcessor`) in every Java project (apps/shipping-api, libs/c4-contract-kit), JDK 25, in CI and on local build hosts. Never on a compile or runtime classpath."
conditions: ["Exactly nullaway 0.14.3 under Gradle locking and SHA-256 verification metadata, resolved together with error_prone_core 2.51.0 in one `errorprone` configuration", "The lock resolves guava 33.7.2-jre and error_prone_annotations 2.51.0 on that path; if NullAway's declared guava 31.1-jre ever resolves, the approval lapses", "Configured with `-Xep:NullAway:ERROR -XepOpt:NullAway:OnlyNullMarked=true` (package-level `@NullMarked`, as the blueprint requires)", "`JarInferEnabled`, `SerializeFixMetadata` and `FixSerializationConfigPath` stay unset; no extra `LibraryModels` providers on the processor path", "org.checkerframework:dataflow-nullaway 4.3.1 and org.checkerframework:checker-qual 4.3.1 get their own allowlist entries before first use"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: NullAway 0.14.3

**Method.** On 2026-11-06 the following were read through WebFetch: Maven Central (listing, POM and Gradle module metadata), the NullAway README, CHANGELOG, RELEASING.md and workflows at tag `v0.14.3`, the project wiki, deps.dev, OSV and Scorecard. Nothing was downloaded, built or run. `api.github.com` refused the fetches (HTTP 403).

## 1. Verdict
**APPROVE-WITH-CONDITIONS, for build time only.**
- It is the latest release, published by Uber.
- It is released from a tag-triggered GitHub Actions workflow, with SHA-pinned actions and a read-only token, and PGP-signed.
- No advisory is known for it.
- The main condition concerns its declared `guava:31.1-jre`, which has three advisories (§5). It must not resolve: on the shared processor path, Error Prone's guava 33.7.2-jre wins, and the lock has to prove that.

## 2. What it is and why it is needed
NullAway is an Error Prone check that reports possible `NullPointerException`s at compile time. The blueprint (§3) pairs package-level `@NullMarked` (JSpecify 1.0.1) with "NullAway 0.14.3 and Error Prone 2.51.0". v01-design §4 notes that "Annotations alone do not enforce nullness". NullAway is the enforcement.

## 3. Provenance
- **Publisher.** Uber Technologies ([POM](https://repo1.maven.org/maven2/com/uber/nullaway/nullaway/0.14.3/nullaway-0.14.3.pom)); Maven Central namespace `com.uber.nullaway`; source github.com/uber/NullAway.
- **Release process** ([RELEASING.md](https://raw.githubusercontent.com/uber/NullAway/v0.14.3/RELEASING.md)):
  - A maintainer bumps the version and creates an annotated, unsigned tag (`git tag -a`) locally.
  - Pushing the tag runs [publish-release.yml](https://raw.githubusercontent.com/uber/NullAway/v0.14.3/.github/workflows/publish-release.yml): `permissions: contents: read`, actions pinned by SHA, an in-memory PGP key from secrets, and `./gradlew publishToMavenCentral`.
  - The deployment is then published by hand on the Central portal.
- **No Sigstore or SLSA provenance:** `slsaProvenances: []` ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.uber.nullaway%3Anullaway/versions/0.14.3)).
- **Central files.** `.jar`, `.pom`, `.module`, `-sources` and `-javadoc`, each with `.asc`, `.md5` and `.sha1`, dated 2026-10-22 22:17. There is **no `.sha256` sidecar** ([listing](https://repo1.maven.org/maven2/com/uber/nullaway/nullaway/0.14.3/)).
- **SHA-256 from the publisher.** The PGP-signed Gradle module metadata records the jar's SHA-256 as `440d3217f6a74d3b8dab766b0c63dbfbb87bea36d798879865218249dc3f1604` ([.module](https://repo1.maven.org/maven2/com/uber/nullaway/nullaway/0.14.3/nullaway-0.14.3.module)). Its sha1 matches the `.jar.sha1` sidecar, `7589b5334fe9bff66b3cf52c668f68ebf40f0573`.
- **Tag.** `v0.14.3` is commit `4b19c428e9abbf294bed4e9daf21bba82888e5eb`, released by msridhar on 22 Oct ([release](https://github.com/uber/NullAway/releases/tag/v0.14.3)).

## 4. Maintenance and adoption
- **Releases** ([atom](https://github.com/uber/NullAway/releases.atom)): roughly monthly. 0.13.7 (2026-06-16), 0.13.8 (07-19), 0.14.0 (08-21), 0.14.1 (08-29), 0.14.2 (09-25), 0.14.3 (10-22). Central's `latest` is 0.14.3 ([metadata](https://repo1.maven.org/maven2/com/uber/nullaway/nullaway/maven-metadata.xml)).
- **Since the release.** 59 commits have landed on master. The [CHANGELOG](https://raw.githubusercontent.com/uber/NullAway/v0.14.3/CHANGELOG.md) lists many bug fixes and no security fix, and notes that "#2005 and #2003 … may lead to new warnings".
- **Scorecard 7.6** (2026-11-02; [scorecard](https://api.securityscorecards.dev/projects/github.com/uber/NullAway)):
  - 10: Maintained, Code-Review, Dangerous-Workflow, SAST.
  - Token-Permissions 9, Pinned-Dependencies 9 (one pip step), Branch-Protection 5.
  - **Security-Policy 0**; Fuzzing 0.
- **Usage.** 4,127 stars and 379 forks ([deps.dev](https://api.deps.dev/v3/projects/github.com%2Fuber%2Fnullaway)).

## 5. Known vulnerabilities at 0.14.3
**None found on 2026-11-06 for NullAway itself.** The osv.dev list for `com.uber.nullaway` is empty ([OSV](https://osv.dev/list?ecosystem=Maven&q=com.uber.nullaway)), and deps.dev `advisoryKeys` is `[]`.

**Declared dependency `guava:31.1-jre`** has three advisories ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.google.guava%3Aguava/versions/31.1-jre)):

| Advisory | Severity | Issue | Fixed in |
|---|---|---|---|
| GHSA-5mg8-w23w-74h3 (CVE-2020-8908) | LOW | Temp-dir disclosure | 32.0.0 |
| GHSA-7g45-4rm6-3mm3 (CVE-2023-2976) | MODERATE | `FileBackedOutputStream` temp dir | 32.0.0 |
| GHSA-xxph-c9ww-hj94 (CVE-2026-102554) | HIGH | Deserialization memory exhaustion | 33.7.2 ([OSV](https://api.osv.dev/v1/vulns/GHSA-xxph-c9ww-hj94)) |

**Reachability.** Gradle resolves guava 33.7.2-jre on the shared path, because error_prone_core requires it. That is condition 2, and the lock must show it. It was not traced whether NullAway would reach those guava APIs.

## 6. License
MIT. `LICENSE.txt` is the standard text, "Copyright (c) 2017 Uber Technologies, Inc." ([LICENSE](https://raw.githubusercontent.com/uber/NullAway/v0.14.3/LICENSE.txt)). deps.dev labels it "non-standard", which disagrees with that file.

`dataflow-nullaway` is "GPL2 with the classpath exception" ([POM](https://repo1.maven.org/maven2/org/checkerframework/dataflow-nullaway/4.3.1/dataflow-nullaway-4.3.1.pom)). It is used as a build tool and is not shipped.

## 7. Execution privileges
- **Build only.** It runs inside javac as an Error Prone check, with the compiler JVM's full rights (see the Error Prone review §7). There is no sandbox.
- **Plugins.** It loads `com.uber.nullaway.LibraryModels` implementations from the processor path through `ServiceLoader`.
- **JarInfer.** With `JarInferEnabled`, it loads `.astubx` model files from the processor path.
- **Writing files.** With `SerializeFixMetadata`, it writes metadata to the path named by `FixSerializationConfigPath` ([Configuration wiki](https://github.com/uber/NullAway/wiki/Configuration)). Condition 4 keeps these off.
- **Network.** None documented. `ErrorURL`, which defaults to `http://t.uber.com/nullaway`, is only a link printed in messages.
- **Requirements.** "JDK 17 or higher and Error Prone, version 2.36.0 or higher" ([README](https://raw.githubusercontent.com/uber/NullAway/v0.14.3/README.md)). It needs exactly one of `AnnotatedPackages` or `OnlyNullMarked`.
- **JSpecify mode** (optional). It needs javac from JDK 22 or later, or `-XDaddTypeAnnotationsToSymbol=true`, so on JDK 25 no extra flag is needed. It is "still under development and may report false positive warnings" ([JSpecify wiki](https://github.com/uber/NullAway/wiki/JSpecify-Support)).

## 8. Direct dependencies as published
From the [POM](https://repo1.maven.org/maven2/com/uber/nullaway/nullaway/0.14.3/nullaway-0.14.3.pom); the `.module` agrees:

| Dependency | Scope | What it is |
|---|---|---|
| org.jspecify:jspecify 1.0.1 | compile | Nullness annotations, already pinned by the blueprint |
| org.checkerframework:dataflow-nullaway 4.3.1 | runtime | Shaded Checker Framework dataflow analysis. **Flag:** GPL2+CPE; it brings checker-qual 4.3.1 |
| com.google.guava:guava 31.1-jre | runtime | **Flag:** three advisories (§5). Must lose resolution to 33.7.2-jre |

Indirect, per [deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.uber.nullaway%3Anullaway/versions/0.14.3:dependencies): jsr305 3.0.2, error_prone_annotations 2.11.0 (resolves to 2.51.0 alongside Error Prone), failureaccess 1.0.1, j2objc-annotations 1.3, checker-qual 4.3.1. On the dependencies checked, deps.dev lists no advisories apart from guava 31.1-jre.

## 9. Alternatives considered
- The Checker Framework Nullness Checker: more complete, but slower, heavier to configure and GPL2+CPE.
- JSpecify annotations without a checker: no enforcement (v01-design §4).

## 10. Conditions and residual risk
**Conditions:** as in the frontmatter.

**Residual risk:**
- Build-time code with full compiler-JVM rights. Keep secrets out of the compile environment.
- No provenance attestation. Releases are published by hand on Central.
- There is no `SECURITY.md` (Security-Policy 0).
- Upstream does not test against Error Prone 2.51.0. Its build pins 2.50.0, and its oldest supported version is 2.36.0 ([libs.versions.toml](https://raw.githubusercontent.com/uber/NullAway/v0.14.3/gradle/libs.versions.toml)). An incompatibility would show as a compiler crash, which fails closed.

## 11. Not verified
- No jar was downloaded or hashed. The SHA-256 is the value in the publisher's `.module`. The `.asc` signatures and the key were not checked.
- The source was not audited for network, process or file behaviour.
- Which guava version actually resolves. Our build's first lock shows it (condition 2).
- Contributor counts and tag-object details were not retrieved (`api.github.com` 403). The Configuration wiki was read only in part, about 39k of 52k characters.
- OSV's `POST /v1/query` was not used. The osv.dev list and deps.dev (fed by OSV) stand in.
