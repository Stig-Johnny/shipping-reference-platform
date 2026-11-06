---
component: "org.hibernate.validator:hibernate-validator; jakarta.validation:jakarta.validation-api; org.jboss.logging:jboss-logging; com.fasterxml:classmate; jakarta.annotation:jakarta.annotation-api; jakarta.xml.bind:jakarta.xml.bind-api; jakarta.activation:jakarta.activation-api"
kind: maven
version: "hibernate-validator 9.1.5.Final; jakarta.validation-api 3.1.1; jboss-logging 3.6.3.Final; classmate 1.7.3; jakarta.annotation-api 3.0.0; jakarta.xml.bind-api 4.0.5; jakarta.activation-api 2.1.4"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api as locked. hibernate-validator, jakarta.validation-api, jboss-logging, classmate, jakarta.annotation-api: compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath (ship in the application). jakarta.xml.bind-api and jakarta.activation-api: testCompileClasspath and testRuntimeClasspath only"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "Hibernate Validator's EL settings stay at their defaults: constraint messages at BEAN_PROPERTIES, custom violations with EL disabled; never BEAN_METHODS; MessageParameterEscaping is never set to NONE", "Custom constraint validators never concatenate user input into message templates; dynamic values go through addMessageParameter or addExpressionVariable only (the reference guide's 'only safe way')", "No JAXB or Jakarta Activation implementation (jaxb-runtime, angus-activation) is added to any classpath without its own review; jakarta.xml.bind-api and jakarta.activation-api stay test-only", "No org.jboss.logging.provider system property and no jboss-logmanager on any classpath", "Re-review when Spring Boot moves Hibernate Validator (9.1.6.Final exists, with EL resolver changes), or on any new Hibernate Validator advisory", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: Bean Validation (Hibernate Validator 9.1.5.Final) and the Jakarta APIs

**Method.** Read on 2026-11-06 with WebFetch: Central listings, sidecars and POMs; deps.dev (including its hash index);
OSV; Hibernate Validator's GitHub security advisories, compare pages and reference guide at `9.1.5.Final`; and Jakarta
API sources and release scripts. Nothing was downloaded, built or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS for all seven.**

**Hibernate Validator 9.1.5.Final is the fix release** for GHSA-gv36-fx3r-29p5 (CVE-2026-93879, High, CVSS 7.5, EL
injection through `addMessageParameter` or `@UniqueElements`), published 2026-10-21. It affects 9.1.0.Alpha1 up to, but
not including, 9.1.5.Final
([advisory](https://github.com/hibernate/hibernate-validator/security/advisories/GHSA-gv36-fx3r-29p5)). OSV, deps.dev
and GitHub's global database do not list that advisory yet.

**EL is live in this application.** `tomcat-embed-el` supplies the implementation (Tomcat review), so message templates
are evaluated. The conditions pin the EL feature levels at their safe defaults and forbid the documented injection
pattern.

**The Jakarta API jars are specifications with no implementation present.**

## 2. What it is and why it is needed
- **spring-boot-starter-validation 4.1.2** brings Hibernate Validator, which pulls in jakarta.validation-api,
  jboss-logging and classmate. The app validates request DTOs with it.
- **jakarta.annotation-api 3.0.0** comes in through `spring-boot-starter-webmvc` (top-level Boot review §8).
- **spring-boot-starter-test** declares jakarta.xml.bind-api 4.0.5 directly, which pulls in activation-api
  ([module](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-test/4.1.2/spring-boot-starter-test-4.1.2.module)).

## 3. Provenance
| Artifact | Publisher → source | Central date | Publisher SHA-256 vs ours |
|---|---|---|---|
| hibernate-validator 9.1.5.Final | Red Hat developers → hibernate/hibernate-validator ([POM](https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator/9.1.5.Final/hibernate-validator-9.1.5.Final.pom)) | 2026-10-13 | sidecar **matches** |
| jakarta.validation-api 3.1.1 | Eclipse EE4J, Red Hat developers → jakartaee/validation ([POM](https://repo1.maven.org/maven2/jakarta/validation/jakarta.validation-api/3.1.1/jakarta.validation-api-3.1.1.pom)) | 2025-02-01 | no `.sha256`; deps.dev's hash index returns 3.1.1 for our SHA-256 ([query](https://api.deps.dev/v3/query?hash.type=SHA256&hash.value=Y84AFWOIw2XzrBvnH8%2BvEUaC%2FAxFICC1325%2BwjbhQqs%3D)) |
| jboss-logging 3.6.3.Final | JBoss → jboss-logging/jboss-logging ([POM](https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.6.3.Final/jboss-logging-3.6.3.Final.pom)) | 2026-03-17 | sidecar **matches** |
| classmate 1.7.3 | FasterXML (Tatu Saloranta) → FasterXML/java-classmate ([POM](https://repo1.maven.org/maven2/com/fasterxml/classmate/1.7.3/classmate-1.7.3.pom)) | 2026-01-02 | sidecar **matches** |
| jakarta.annotation-api 3.0.0 | Eclipse EE4J, Oracle developers → jakartaee/common-annotations-api ([POM](https://repo1.maven.org/maven2/jakarta/annotation/jakarta.annotation-api/3.0.0/jakarta.annotation-api-3.0.0.pom)) | 2024-02-15 | no `.sha256`; deps.dev's hash index returns 3.0.0 ([query](https://api.deps.dev/v3/query?hash.type=SHA256&hash.value=sB9VVSKEz7FJQR5k6rynXpQtJtLheGsykUJQ5DMK%2BqI%3D)) |
| jakarta.xml.bind-api 4.0.5 | Eclipse EE4J → jakartaee/jaxb-api ([parent](https://repo1.maven.org/maven2/jakarta/xml/bind/jakarta.xml.bind-api-parent/4.0.5/jakarta.xml.bind-api-parent-4.0.5.pom)) | 2026-02-06 | sidecar **matches** |
| jakarta.activation-api 2.1.4 | Eclipse EE4J → jakartaee/jaf-api ([POM](https://repo1.maven.org/maven2/jakarta/activation/jakarta.activation-api/2.1.4/jakarta.activation-api-2.1.4.pom)) | 2025-09-11 | sidecar **matches** |

- **Hibernate releases:** a Jenkins job signs with GPG and uploads through JReleaser, with no attestation
  ([Jenkinsfile](https://raw.githubusercontent.com/hibernate/hibernate-validator/9.1.5.Final/jenkins/release/Jenkinsfile)).
- **Jakarta releases:** Eclipse Jenkins jobs import a GPG keyring
  ([validation](https://raw.githubusercontent.com/jakartaee/validation/main/ci/release/Jenkinsfile)), and the Jakarta jars
  ship signed CycloneDX SBOMs.
- **Attestations:** none; deps.dev shows `slsaProvenances: []` for all seven.

## 4. Maintenance and adoption
- **Hibernate Validator:**
  - 9.1.6.Final (2026-11-01) is newer. Its changes register and then restrict a `RecordELResolver`; no security label
    ([compare](https://github.com/hibernate/hibernate-validator/compare/9.1.5.Final...9.1.6.Final)).
  - Scorecard 6.8, Code-Review 0 ([SC](https://api.securityscorecards.dev/projects/github.com/hibernate/hibernate-validator)).
- **jakarta.validation-api:** 3.1.1 is the newest stable release. Scorecard 6.7.
- **jboss-logging:** 3.6.3 is the newest release. Scorecard 4.7, Maintained 0 ([SC](https://api.securityscorecards.dev/projects/github.com/jboss-logging/jboss-logging)).
- **classmate:** 1.8.0 (2026-10-12) is newer. Scorecard 6.9.
- **annotation-api, xml.bind-api, activation-api:** each is the newest stable release. Their repositories have no
  Scorecard (404).

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06:**
- `inputs/osv.json` is empty and deps.dev `advisoryKeys: []` for all seven.
- **Hibernate Validator's ranges all end below 9.1.5.Final:**
  - GHSA-gv36-fx3r-29p5 (above): 9.1.5.Final is the patched version.
  - [GHSA-rmrm-75hp-phr2](https://api.osv.dev/v1/vulns/GHSA-rmrm-75hp-phr2) (CVE-2020-10693): fixed in 6.0.20 / 6.1.5.
  - [GHSA-m8p2-495h-ccmh](https://api.osv.dev/v1/vulns/GHSA-m8p2-495h-ccmh) (CVE-2019-10219): 6.0.x / 6.1.0 alphas.
  - [GHSA-7v6m-28jr-rg84](https://api.osv.dev/v1/vulns/GHSA-7v6m-28jr-rg84) (CVE-2025-35036): fixed in 6.2.0 / 7.0.0.CR1.

## 6. License
- **Apache-2.0:** hibernate-validator, jakarta.validation-api, jboss-logging and classmate.
- **jakarta.annotation-api:** EPL-2.0 or GPL-2.0 with Classpath Exception. It ships in the application, under the EPL
  option.
- **jakarta.xml.bind-api and jakarta.activation-api:** EDL-1.0 (BSD-3-Clause) (POMs above). Test only.

## 7. Execution privileges
- **Hibernate Validator (runtime):**
  - It reflects over validated beans; classmate resolves their generic types.
  - It interpolates messages through Jakarta EL. The reference guide sets constraint messages to `BEAN_PROPERTIES`,
    disables EL for custom violations by default, and warns that `BEAN_METHODS` "can lead to … arbitrary code
    execution". It also says `addExpressionVariable()` "is the only safe way to inject a variable into an expression"
    ([ch12](https://raw.githubusercontent.com/hibernate/hibernate-validator/9.1.5.Final/documentation/src/main/asciidoc/reference/_ch12.adoc)).
  - Since 9.1.5, message parameters are escaped by default (`MessageParameterEscaping`).
  - These are the controls behind conditions 2 and 3.
- **jboss-logging:** picks a backend by `org.jboss.logging.provider`, then ServiceLoader, then `Class.forName` probes. It
  lands on SLF4J here because Logback is present
  ([LoggerProviders](https://raw.githubusercontent.com/jboss-logging/jboss-logging/v3.6.3.Final/src/main/java/org/jboss/logging/LoggerProviders.java)).
- **jakarta.annotation-api:** annotations only.
- **jakarta.validation-api:** the API, plus `Validation` bootstrap via ServiceLoader.
- **jakarta.xml.bind-api and jakarta.activation-api (test):** they find implementations by system property, then
  ServiceLoader, then a hard-coded default class name. With none present, JAXB throws "Implementation of Jakarta XML
  Binding-API has not been found"
  ([ContextFinder](https://raw.githubusercontent.com/jakartaee/jaxb-api/4.0.5/api/src/main/java/jakarta/xml/bind/ContextFinder.java)).
  Condition 4 keeps it that way.
- **None of the seven:** network, process, native code or build-time execution.

## 8. Direct dependencies as published
- **hibernate-validator:** jakarta.validation-api 3.1.1, jboss-logging 3.6.3.Final, classmate 1.7.1 (compile; Boot
  resolves classmate 1.7.3). jakarta.el-api is provided and optional, and several optional integrations (persistence,
  joda, paranamer, money, javafx) are not resolved.
- **jakarta.xml.bind-api:** jakarta.activation-api 2.1.4.
- **jboss-logging:** only provided and test dependencies.
- **The others:** none at runtime.
- **POM metadata only:** org.eclipse.ee4j:project 1.0.9, jboss-parent 52, oss-parent 74, jakarta.xml.bind-api-parent 4.0.5.

## 9. Alternatives considered
- **Hand-written validation** would avoid EL entirely, at the cost of the standard annotations.
- **Apache BVal** would need its own review, and Boot does not support it out of the box.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **EL-based message interpolation** has produced four advisories over the years; the latest was fixed only in this
  release. Each one needed developer misuse or a non-default setting, which conditions 2 and 3 forbid.
- **jboss-logging's repository** scores Maintained 0.
- **Two hashes** are anchored only by deps.dev's index and our first download.

## 11. Not verified
- **Signatures:** no `.asc` was checked.
- **The 9.1.5 → 9.1.6 diffs:** only commit titles were read.
- **Pipeline internals:** the Eclipse `releng-pipeline` and `hibernate-release-scripts`.
- **The Jakarta API jars' contents.**
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in, and both miss GHSA-gv36-fx3r-29p5, which 9.1.5
  fixes.
