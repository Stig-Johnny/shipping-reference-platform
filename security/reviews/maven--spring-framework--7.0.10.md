---
component: "org.springframework:spring-core, spring-beans, spring-aop, spring-context, spring-expression, spring-tx, spring-jdbc, spring-web, spring-webmvc, spring-test"
kind: maven
version: "7.0.10"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api as locked: spring-core, -beans, -aop, -context, -expression, -tx, -jdbc, -web, -webmvc on compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath (ship in the application); spring-test on testCompileClasspath and testRuntimeClasspath only"
conditions: ["Exact version 7.0.10 and the jar SHA-256 values in allowlist-entries.json (each equals the publisher's Central .sha256 sidecar and the hash in its .module); any other version or hash is a new review", "spring-test stays on test configurations only", "No SpEL expression is built from request data or other untrusted input; application code never evaluates expressions with StandardEvaluationContext over untrusted text (Spring's own warning)", "No XsltView, no server-side template view technology and no HTTP-invoker style remoting are added without a new review (the app is a JSON API)", "Re-review when Spring Boot moves Spring Framework, or when spring.io/security or OSV publishes an advisory whose range includes 7.0.10", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: Spring Framework 7.0.10 (ten modules)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, `.sha256` sidecars, POMs and `.module` files for all
ten modules; deps.dev; OSV; spring.io/security; Scorecard; and the release workflows, tag and Gradle build files at
`v7.0.10`. Nothing was downloaded, built or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** 7.0.10 (2026-10-22) is the newest 7.0.x, and Boot 4.1.2 manages it. All ten jars match the
publisher's SHA-256. Every Spring Framework advisory of 2026 found on OSV ends at 7.0.7 or 7.0.8 (fixed in 7.0.8 or
7.0.9).

This is the core of the application: dependency injection, reflection and runtime proxies, the web layer that maps
HTTP requests to controllers, and the expression language. The conditions address the two classic misuse paths, SpEL
over untrusted input and view technologies, and keep `spring-test` out of the shipped classpath.

## 2. What it is and why it is needed
Spring Boot 4.1.2 is built on these modules (top-level Boot review §8):
- **spring-web and spring-webmvc:** HTTP and controllers.
- **spring-jdbc and spring-tx:** JDBC and transactions, which slice 5 needs.
- **spring-context, -beans, -aop, -core and -expression:** the container.
- **spring-test:** the test support behind `spring-boot-starter-test`.

The design picks Boot 4.1.2 for the example (`v01-design.md` §4).

## 3. Provenance
- **Publisher.** Maven Central, `org.springframework`. All ten POMs give Apache-2.0, organization "Spring IO", developer
  Juergen Hoeller and scm `github.com/spring-projects/spring-framework`
  ([spring-core POM](https://repo1.maven.org/maven2/org/springframework/spring-core/7.0.10/spring-core-7.0.10.pom)).
- **Files.** Every file is dated 2026-10-22 07:46, with `.asc`, `.sha256` and `.module`
  ([spring-core dir](https://repo1.maven.org/maven2/org/springframework/spring-core/7.0.10/); the other nine follow the
  same pattern).
- **SHA-256.** All ten `<artifact>-7.0.10.jar.sha256` sidecars **equal our values**, and each `.module` lists the same
  hash. `allowlist-entries.json` cites these.
- **Build.** `release-train-build.yml` at `v7.0.10` runs on `workflow_dispatch` with `contents: read` and SHA-pinned
  actions. It builds into a local deployment repository, runs a repeat build, then hands off to `spring-io/release-train`
  ([workflow](https://raw.githubusercontent.com/spring-projects/spring-framework/v7.0.10/.github/workflows/release-train-build.yml)).
  That repository returns 404, so **the signing and the Central upload cannot be seen**.
- **Attestations.** There are none: deps.dev shows `slsaProvenances: []` and `attestations: []`
  ([spring-core](https://api.deps.dev/v3/systems/maven/packages/org.springframework%3Aspring-core/versions/7.0.10)).
- **Tag.** `v7.0.10` is an annotated tag by "Spring Builds" on commit `33a85b0b`. Its PGP signature shows `verified: false,
  unknown_key` on GitHub ([tag](https://api.github.com/repos/spring-projects/spring-framework/git/tags/9e88a9fa9da2e61d62c391117c673b01bae5f669)).

## 4. Maintenance and adoption
- **Release state.** 7.0.10 is the newest 7.0.x; 7.1.0-RC2 came out on 2026-11-05
  ([maven-metadata](https://repo1.maven.org/maven2/org/springframework/spring-core/maven-metadata.xml)).
- **Scorecard 6.2** (2026-10-19) ([scorecard](https://api.securityscorecards.dev/projects/github.com/spring-projects/spring-framework)):
  - Maintained, Fuzzing (OSS-Fuzz), Security-Policy and Dangerous-Workflow score 10.
  - Code-Review 3, Token-Permissions 0, SAST 0.
- **Advisory channel.** Vulnerabilities are announced at [spring.io/security](https://spring.io/security).

## 5. Known vulnerabilities at 7.0.10
**None found on 2026-11-06:**
- `inputs/osv.json` is empty for all ten, and deps.dev `advisoryKeys: []` for all ten.
- The OSV records checked all end below 7.0.10:

| Advisory | Module | Affected 7.0.x | Fixed |
|---|---|---|---|
| [GHSA-j9f9-w8pj-32f8](https://api.osv.dev/v1/vulns/GHSA-j9f9-w8pj-32f8) / CVE-2026-47890 (SSE) | webmvc | 7.0.0–7.0.8 | 7.0.9 |
| [GHSA-pc63-qcmh-9cmg](https://api.osv.dev/v1/vulns/GHSA-pc63-qcmh-9cmg) / CVE-2026-47884 (XsltView) | webmvc | 7.0.0–7.0.8 | 7.0.9 |
| [GHSA-9f52-rjqv-25qv](https://api.osv.dev/v1/vulns/GHSA-9f52-rjqv-25qv) / CVE-2026-41852 (SpEL) | expression | 7.0.0–7.0.7 | 7.0.8 |
| [GHSA-7m2p-62gw-p8qq](https://api.osv.dev/v1/vulns/GHSA-7m2p-62gw-p8qq) / CVE-2026-41854 (UriComponentsBuilder) | web | 7.0.0–7.0.7 | 7.0.8 |

- **The twelve 2026-08-20 advisories** in the top-level Boot review are fixed in 7.0.9.
- **Severity labels disagree:** spring.io rates CVE-2026-47890 "Low"; GHSA rates it 9.8.

## 6. License
Apache-2.0 (POMs; deps.dev).

## 7. Execution privileges
- **Runtime (all modules except spring-test).**
  - **Heavy reflection:** classpath scanning and runtime class generation.
  - **Bundled code in spring-core:** ASM 9.x under `org.springframework.asm` and a patched CGLIB 3.3 under
    `org.springframework.cglib` (in source), plus JavaPoet and Objenesis 3.5, shaded at build time
    ([spring-core.gradle](https://raw.githubusercontent.com/spring-projects/spring-framework/v7.0.10/spring-core/spring-core.gradle),
    [asm package-info](https://raw.githubusercontent.com/spring-projects/spring-framework/v7.0.10/spring-core/src/main/java/org/springframework/asm/package-info.java)).
    POM-based scanners cannot see these.
  - **spring-web and spring-webmvc** parse and bind request data. The 2026 advisories sit in this layer.
  - **spring-jdbc** runs SQL through the pool.
- **SpEL.** The 7.0.10 reference says `StandardEvaluationContext` "must never be used to evaluate an expression
  obtained from an untrusted source". It calls `SimpleEvaluationContext`'s restrictions "best-effort"
  ([evaluation](https://docs.spring.io/spring-framework/reference/core/expressions/evaluation.html)). Limits:
  maxExpressionLength 10,000, and maxOperations 10,000 since 7.0.8. Condition 3 applies.
- **Test phase.** spring-test drives the test context and MockMvc.
- **Build time:** none. No module contains an annotation processor.
- **Not used by Framework itself:** no process execution or native code was noted.

## 8. Direct dependencies as published
All are compile scope, with no optional entries and identical apiElements and runtimeElements in the `.module` files
([spring-core module](https://repo1.maven.org/maven2/org/springframework/spring-core/7.0.10/spring-core-7.0.10.module)).
- **spring-core:** commons-logging 1.3.5 (resolved to 1.3.6; logging review), jspecify 1.0.1.
- **spring-context and spring-web:** also micrometer-observation 1.16.8 (resolved to 1.17.2; metrics review).
- **The other modules:** depend only on other Spring 7.0.10 modules.
- **spring-jcl has been removed** in favour of Apache Commons Logging 1.3
  ([7.0 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)).
  That creates a duplicate-class issue with `jcl-over-slf4j`, handled in the logging review.

## 9. Alternatives considered
None that keep Spring Boot and Spring Modulith. 7.1.0 is still a release candidate.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **A large, reflective framework** with a steady flow of web-layer advisories (seven months of 2026 brought fixes in
  7.0.8 and 7.0.9).
- **Opaque signing and publishing:** they happen in a release-train repository that cannot be seen.
- **Low Code-Review score** (3).

## 11. Not verified
- **Signatures:** no `.asc` was checked.
- **The release-train repository** and its signing.
- **spring.io/security beyond its first page** ("Load more" not reachable).
- **Some June 2026 OSV entries:** GHSA-r5w3-xv2f-j59q, GHSA-wxpp-56q6-5pcg and six webmvc entries were not opened. All
  predate 7.0.8.
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in.
