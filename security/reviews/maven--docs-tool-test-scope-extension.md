---
component: "Scope extension to tools/docs: the JUnit 5.14.4 family, AssertJ 3.27.7 with its test-path transitives, JSpecify 1.0.1, and the javac plugin path (Error Prone 2.51.0, NullAway 0.14.3 and their transitives)"
kind: maven
version: "Exact, as already reviewed: JUnit Jupiter 5.14.4 and Platform 1.14.4 (aligned by junit-bom 5.14.4); AssertJ 3.27.7; Byte Buddy 1.18.3; opentest4j 1.3.0; apiguardian-api 1.1.2; JSpecify 1.0.1; Error Prone 2.51.0; NullAway 0.14.3"
verdict: APPROVE-WITH-CONDITIONS
scope: "Build time only, tools/docs: the JUnit and AssertJ families on testCompileClasspath and testRuntimeClasspath; JSpecify on every classpath (annotations only); the javac plugin path on annotationProcessor, errorprone and testAnnotationProcessor, as srp.java-conventions sets it for every Java project. Nothing here is published or shipped in the site. Existing approvals are unchanged"
conditions: ["Same versions and jar SHA-256 values as the existing allowlist entries; any other version or hash is a new review", "Every condition of the extended records applies to tools/docs as written: maven--junit-jupiter--5.14.4.md, maven--assertj-core--3.27.7.md, maven--test-path-transitives--assertj-3.27.7-junit-5.14.4.md, maven--jspecify--1.0.1.md, maven--error_prone_core--2.51.0.md, maven--nullaway--0.14.3.md and maven--javac-plugin-path-transitives--ep-2.51.0-nullaway-0.14.3.md", "JUnit, AssertJ and their transitives stay on the test configurations of tools/docs; JSpecify is used for its annotations only", "tools/docs/gradle.lockfile resolves nothing that lacks an approved entry; slf4j-api is held at the reviewed 2.0.19 by a constraint (httpclient5 asks for the unreviewed 1.7.36)", "A human code owner accepts this record before first use"]
reviewer: "AI agent (Claude Opus 5.5, the orchestrating session), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Scope extension: test libraries, JSpecify and the javac plugin path in the docs tool

**Not a new review, and no new allowlist entries.** The records below reviewed these artifacts for the contract kit.
The kit's first design split it into three projects, `:core`, `:junit5` and `:example-app`, and their scopes still use
those names (see `security/README.md`). This record extends them to the docs generator, `tools/docs`.

| Extended record | Artifacts in `tools/docs/gradle.lockfile` |
|---|---|
| `maven--junit-jupiter--5.14.4.md` | junit-bom, junit-jupiter, -api, -engine, -params 5.14.4; junit-platform-commons, -engine, -launcher 1.14.4 |
| `maven--assertj-core--3.27.7.md` | assertj-core 3.27.7 |
| `maven--test-path-transitives--assertj-3.27.7-junit-5.14.4.md` | byte-buddy 1.18.3, opentest4j 1.3.0, apiguardian-api 1.1.2 |
| `maven--jspecify--1.0.1.md` | jspecify 1.0.1 |
| `maven--error_prone_core--2.51.0.md`, `maven--nullaway--0.14.3.md`, `maven--javac-plugin-path-transitives--ep-2.51.0-nullaway-0.14.3.md` | the javac plugin path, identical to the kit's |

The remaining coordinates are covered by other records: Structurizr and its transitives by
`maven--docs-tool-scope-extension.md`, and SnakeYAML Engine by `maven--snakeyaml-engine--3.2.md`.

## Method

On 2026-11-06 I resolved `tools/docs` from an empty Gradle home with strict locking and strict checksum verification.
Then I compared every line of `tools/docs/gradle.lockfile` with `security/allowlist.json`. Every coordinate and version
in it is an approved entry with a review record, and the jar checksums in `gradle/verification-metadata.xml` are the
reviewed ones. The only artifact the docs tool adds to the verification metadata is `snakeyaml-engine` 3.2. The first
resolution also pulled `slf4j-api` 1.7.36 through httpclient5, which has no review. In the kit, ArchUnit raises it to
2.0.19. A constraint now holds the docs tool at the reviewed 2.0.19 too.

## Verdict

**APPROVE-WITH-CONDITIONS.** The extended records judge these artifacts by what they are and by when they run: test
code and javac plugins, at build time, on a developer machine or a CI runner, against repository-owned input. None of
that depends on which project's classpath they are on. The docs tool runs at build time only and publishes no JAR, so
the risk is the same as in the kit.

## Not verified

The same gaps as in the extended records. Nothing was re-downloaded or re-inspected beyond the resolution and the
checksum comparison above.
