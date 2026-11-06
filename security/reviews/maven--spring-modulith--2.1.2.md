---
component: "org.springframework.modulith (spring-modulith-bom, -starter-core, -starter-jdbc, -starter-test, -docs)"
kind: maven
version: "2.1.2"
verdict: APPROVE-WITH-CONDITIONS
scope: ":example-app only. Approved: spring-modulith-bom (Gradle platform); spring-modulith-starter-core and spring-modulith-starter-jdbc with the 2.1.2 modules they pull in (main); spring-modulith-test (test configurations, in place of the starter). NOT approved: spring-modulith-docs, spring-modulith-starter-test."
conditions: ["Only the approved 2.1.2 artifacts listed in scope; spring-modulith-docs and spring-modulith-starter-test are not used", "spring-modulith-apt is never added to an annotationProcessor configuration", "spring.modulith.events.jdbc.schema-initialization.enabled=false; the event publication table is created by a reviewed Flyway .sql migration", "spring.modulith.events.republish-outstanding-events-on-restart and spring.modulith.runtime.flyway-enabled stay false", "The ArchUnit version that resolves on :example-app (1.4.2 declared here, 1.5.1 if :core shares the classpath) has its own approved entry, and the module-verification test passes with it", "Every third-party artifact in section 8 has its own approved entry", "Re-review on any version change, and whenever Spring Boot changes"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Spring Modulith 2.1.2 (example application only)

## 1. Verdict

**APPROVE-WITH-CONDITIONS** for the BOM, `starter-core`, `starter-jdbc` and `spring-modulith-test`.
**`spring-modulith-docs` and `spring-modulith-starter-test` are not approved.** 2.1.2 (2026-10-22) is the newest
2.1.x and was built against Spring Boot 4.1.2. deps.dev lists no advisory for it, or for the ArchUnit, jMolecules,
Structurizr and json-path versions it pulls in.

`docs` is not needed: the design documents architecture with its own Structurizr-based checker. It would also add
Structurizr 5.0.3 from `structurizr/java`, which was archived on 2026-03-28, next to the Structurizr 6.x that `:core`
uses. `starter-test` forces `docs` in, so the plain `spring-modulith-test` module replaces it.

Two defaults are tightened. The JDBC event registry creates its own table by default, so its schema should come from a
reviewed migration instead. And the `apt` annotation processor must never run.

## 2. What it is and why it is needed

v01-design.md §4: "Spring Boot 4.1.2; Modulith 2.1.2 … Example only." The example's modules (`proposal`, `voting`,
`audit`) use two things from Modulith. Module verification checks how the modules are arranged. The JDBC event
publication registry stores events in the same transaction as the business data, which slice 5 needs ("PostgreSQL
tests prove joint commit/rollback").

## 3. Provenance

- **Publisher.** Maven Central, `org.springframework.modulith`. Apache-2.0; developer `odrotbohm` (Oliver Drotbohm,
  VMware, role lead); scm `https://github.com/spring-projects/spring-modulith`
  (https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith/2.1.2/spring-modulith-2.1.2.pom).
- **Files.** All dated 2026-10-22 13:15, each with `.asc`, `.sha256` and `.sha512`. The SHA-256 values were **not
  fetched**, so the allowlist has `sha256: null`.
- **Build.** There are two release paths at the tag, and it is not known which one produced 2.1.2
  (https://github.com/spring-projects/spring-modulith/tree/2.1.2/.github/workflows):
  - `release.yaml` imports a GPG key from secrets and runs `mvn deploy` to Central, with tag-pinned actions and no
    `permissions` block.
  - `release-train-build.yml` uses SHA-pinned actions and `contents: read`, builds twice, and hands off to
    `spring-io/release-train`.

  There is no attestation step, and deps.dev shows `slsaProvenances: []`. GitHub release `2.1.2` was published by
  `odrotbohm` at `5d7515d`, a "verified signature" (GPG key 9EB72C54FB72F2A7).

## 4. Maintenance and adoption

Releases follow Boot's: 2.1.0 (2026-06-11), 2.1.1 (08-25), 2.1.2 (10-22). The 2.0.x line continues, and 2.2.0-RC1 is
out (https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-bom/). The repository dates from
2022-07-05, with 1,210 stars and 238 forks, and requires web commit sign-off. The POM names a single lead. **Scorecard:
not available** (404). The reference docs still show 2.1.1, and the compatibility matrix has no 2.1 row. Boot 4.1.2
compatibility rests only on the POMs and the release note "Upgrade to Spring Boot 4.1.2 #1956".

## 5. Known vulnerabilities at 2.1.2

**Method.** OSV `POST /v1/query` is unavailable here, so deps.dev v3 (which carries OSV data) was used on 2026-11-06.
`spring-modulith-core`, `-events-jdbc` and `-docs` 2.1.2 show `advisoryKeys: []`
(https://api.deps.dev/v3/systems/maven/packages/org.springframework.modulith%3Aspring-modulith-core/versions/2.1.2,
and the same pattern for the others). So do archunit 1.4.2 and 1.5.1, jmolecules-events 2.0.1, jspecify 1.0.1,
structurizr-core and -export 5.0.3, json-path 2.10.0, json-smart 2.6.0 and jackson-databind 2.21.7. The 2.1.2 release
notes contain no security items. **None found on 2026-11-06.**

## 6. License

**Apache-2.0** (POMs, deps.dev). ArchUnit shows "Apache-2.0, non-standard" on deps.dev; that belongs to its own review.

## 7. Execution privileges

- **Build time.** `spring-modulith-apt` arrives on the compile classpath through `starter-core`. It registers
  `SpringModulithProcessor` for `"*"` and writes `generated-spring-modulith/javadoc.json` with a plain `FileWriter`,
  outside the `Filer`
  (https://github.com/spring-projects/spring-modulith/blob/2.1.2/spring-modulith-apt/src/main/java/org/springframework/modulith/apt/SpringModulithProcessor.java).
  Gradle "ignores annotation processors on the compile classpath"
  (https://docs.gradle.org/current/userguide/java_plugin.html), so it runs only if someone adds it to
  `annotationProcessor`.
- **Runtime.**
  - `spring-modulith-core` uses ArchUnit to read the application's class files.
  - The JDBC registry writes event publications into the application database inside the business transaction.
    `events-jackson` serialises the payloads with Jackson 3 and reads them back on resubmission.
  - `spring.modulith.events.jdbc.schema-initialization.enabled` defaults to `true` in the appendix table and runs
    `CREATE TABLE IF NOT EXISTS`, but Appendix D calls the same switch opt-in
    (https://docs.spring.io/spring-modulith/reference/appendix.html). Condition 3 settles the contradiction.
  - `republish-outstanding-events-on-restart` defaults to `false`.
- **Test.** `spring-modulith-test` bootstraps module slices.
- **Rejected `docs`.** Its `Documenter` writes `build/spring-modulith-docs` and by default **deletes that folder
  recursively** first.
- **Network.** None found in `SpringModulithProcessor` or `Documenter`; the other module sources were not read.

## 8. Direct dependencies as published (POMs at 2.1.2)

- **bom:** manages 34 `org.springframework.modulith` artifacts and no third-party ones.
- **starter-core:** api, apt, core (runtime), moments, `spring-boot-starter` 4.1.2. The docs table also lists
  `spring-modulith-runtime`; the 2.1.2 POM does not.
- **starter-jdbc:** starter-core, events-api, plus events-core, events-jackson and events-jdbc (all runtime).
- **starter-test (rejected):** spring-boot-starter-test, spring-modulith-test, spring-modulith-docs, awaitility 4.3.0.
- **docs (rejected):** core, structurizr-core 5.0.3 (bringing jackson-annotations 2.20 and jsr305 3.0.2;
  commons-logging is excluded), structurizr-export 5.0.3, json-path 2.10.0.
- **Modules** (each also depends on jspecify 1.0.1):
  - core: `com.tngtech.archunit:archunit` 1.4.2, plus spring-core/-context 7.0.9 (the BOM resolves 7.0.10)
  - moments: `org.jmolecules:jmolecules-events` 2.0.1
  - events-core: spring-aop/-tx, slf4j-api 2.0.20, and jcl-over-slf4j 2.0.20 (runtime)
  - events-jackson: `tools.jackson.core:jackson-databind` 3.1.7
  - events-jdbc: spring-jdbc, spring-boot-sql, spring-boot-jdbc
  - test: spring-boot-test-autoconfigure, spring-test, assertj-core 3.27.7, awaitility 4.3.0

**Transitives that deserve their own review:** ArchUnit first. 2.1.2 declares 1.4.2, but the design picks 1.5.1 for
`:core`, and if both share a classpath Gradle picks 1.5.1. That combination is untested here; Modulith 2.2 moved to
1.5.x. Then jmolecules-events, jspecify and jcl-over-slf4j.

## 9. Alternatives considered

Module rules could come from the project's own conformance checker or from ArchUnit directly. Events could use plain
Spring events with a hand-written outbox table. For tests, `spring-modulith-test` replaces `starter-test`, as approved.

## 10. Conditions and residual risk

The conditions are those in the frontmatter. **Residual risk:** one named lead and no Scorecard; the compatibility
matrix does not cover 2.1; the POMs declare Framework 7.0.9, though the BOM wins with 7.0.10; and the `apt` jar stays
on the compile classpath, inert under Gradle.

## 11. Not verified

The `.asc` signatures and SHA-256 values; which workflow produced 2.1.2; how far `core` and `runtime` scan at start;
where `BuildSystemUtils` puts its output; whether 2.1.2 runs on ArchUnit 1.5.1; and any module source other than
`SpringModulithProcessor` and `Documenter`.
