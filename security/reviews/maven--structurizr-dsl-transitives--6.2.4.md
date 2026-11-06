---
component: "Transitive dependencies of com.structurizr:structurizr-dsl 6.2.4 (plus archunit 1.5.1's slf4j-api) on the library classpaths of :libs:c4-contract-kit"
kind: maven
version: "Exact per artifact, as locked on 2026-11-06 (24 coordinates, section 8)"
verdict: APPROVE-WITH-CONDITIONS
scope: "compileClasspath, runtimeClasspath, testCompileClasspath and testRuntimeClasspath of :libs:c4-contract-kit, which makes them the published runtime tree of :core. guava 33.7.2-jre, failureaccess 1.0.3, listenablefuture 9999.0 and j2objc-annotations 3.1 are also approved on annotationProcessor, errorprone and testAnnotationProcessor (build time)"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "guava resolves to 33.7.2-jre on every configuration through a declared strict constraint, not only through the lock (33.6.0-jre, which javaparser-parent 3.28.2 manages, carries GHSA-xxph-c9ww-hj94)", "Every condition of the structurizr-dsl 6.2.4 review holds: all ten parser Features disabled, HttpClient.allow(...) never called, repository-owned DSL only. httpclient5, httpcore5 and httpcore5-h2 are approved on the premise that no URL is ever allowed", "The kit never reads Structurizr JSON workspaces or any other untrusted JSON with jackson-databind and never enables default typing", "No commons-logging.properties, org.apache.commons.logging.* system property, slf4j.provider property or logging provider JAR is added to a runtime or test classpath without its own review", "Before :core is first published: (a) resolving the generated POM with Maven in a scratch consumer shows guava >= 33.7.2-jre, or structurizr-component is excluded; (b) jaxb-api and javax.activation-api are excluded after the section 9 test passes, or the owner records acceptance of CDDL-1.1 / GPL-2.0-with-classpath-exception in consumers' trees", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: the Structurizr 6.2.4 runtime tree (24 transitive artifacts)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, POMs and `.sha256` sidecars, deps.dev, Scorecard, OSV and
Structurizr source at tag `v6.2.4`. Nothing was downloaded, built or run. URL prefixes: `MC/` =
`https://repo1.maven.org/maven2/`, `DD/` = `https://api.deps.dev/v3/systems/maven/packages/`, `SC/` =
`https://api.securityscorecards.dev/projects/github.com/`, `ST/` =
`https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/`.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** Each artifact comes from a known publisher, is PGP-signed on Central, and has no advisory at
its locked version. The concern is shape rather than any single artifact. The published library would carry an HTTP
client, two bytecode libraries (bcel, javassist) and a Java source parser with a symbol solver, and the kit uses none of
them. They are inert only because the parser features are off. Two artifacts, jaxb-api and javax.activation-api, come
from Java EE projects archived in 2018, and they pass a CDDL/GPL licence on to consumers. The conditions keep the
dangerous paths cold and force a decision before publication. Section 9 names three exclusions that would remove 17 of
the 24 artifacts from the published tree.

## 2. What it is and why it is needed
None of these was chosen. All arrive with `structurizr-dsl` 6.2.4, the parser the design selects (`v01-design.md` §2),
except `slf4j-api` 2.0.19, which archunit 1.5.1 declares (top-level archunit review §8). The kit calls only the parser
and the model (`Conformance.check`, design §2).

## 3. Provenance
Every row has a `.asc` on Central. "SHA-256" means that a publisher `.jar.sha256` equals our verification-metadata value.
| Artifact | Publisher, namespace → source | Central date | Publisher SHA-256 |
|---|---|---|---|
| jackson-annotations 2.22, -core 2.22.3, -databind 2.22.3, jackson-bom 2.22.3 (POM) | FasterXML, `com.fasterxml.jackson*` → FasterXML/jackson-* ([dir](https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.22.3/)) | 05-31 / 09-21 2026 | match (all four) |
| httpclient5 5.6.4; httpcore5, httpcore5-h2 5.4.3 | Apache Software Foundation, `org.apache.httpcomponents.*5` ([dir](https://repo1.maven.org/maven2/org/apache/httpcomponents/client5/httpclient5/5.6.4/)) | 2026-08-06; 06-21 | none published |
| commons-io 2.21.0, commons-logging 1.4.0, commons-lang3 3.20.0, bcel 6.12.0 | ASF Commons, gitbox.apache.org ([lang3 POM](https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.20.0/commons-lang3-3.20.0.pom)) | 2025-11-04, 2026-06-11, 2025-11-12, 2026-01-18 | none published |
| slf4j-api 2.0.19 | QOS.ch, `org.slf4j` → qos-ch/slf4j ([dir](https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.19/)) | 2026-09-04 | match |
| javaparser-core, -symbol-solver-core 3.28.2 | `com.github.javaparser` → javaparser/javaparser. The release was made by github-actions ([release](https://github.com/javaparser/javaparser/releases/tag/javaparser-parent-3.28.2)) | 2026-05-31 | match (both) |
| javassist 3.31.0-GA | Shigeru Chiba et al., `org.javassist` → jboss-javassist/javassist ([POM](https://repo1.maven.org/maven2/org/javassist/javassist/3.31.0-GA/javassist-3.31.0-GA.pom)) | 2026-04-19 | match |
| jaxb-api 2.4.0-b180830.0359 | Oracle, `javax.xml.bind` → javaee/jaxb-spec, **archived 2018-09-24** ([repo](https://github.com/javaee/jaxb-spec)) | 2018-08-30 | none published |
| javax.activation-api 1.2.0 | Oracle → javaee/activation, **archived 2018-09-07** ([repo](https://github.com/javaee/activation)) | 2017-09-06 | none published |
| jsr305 3.0.2 | `com.google.code.findbugs`; its SCM is a code.google.com URL ([POM](https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.pom)) | 2017-03-31 | none published |
| checker-qual 3.55.1 | Checker Framework (UW), `org.checkerframework` → typetools/checker-framework | 2026-04-03 | match |
| guava 33.7.2-jre; failureaccess 1.0.3; listenablefuture 9999.0-empty…; j2objc-annotations 3.1; error_prone_annotations 2.50.0 | Google, `com.google.*` → google/guava, google/j2objc, google/error-prone ([guava POM](https://repo1.maven.org/maven2/com/google/guava/guava/33.7.2-jre/guava-33.7.2-jre.pom)) | 2026-09-29; 2025-03-19; 2018-09-11; 2025-08-14; 2026-06-10 | match: guava, j2objc, EP annotations. None published: failureaccess, listenablefuture |

- **No artifact has SLSA provenance or attestations.** deps.dev returns `slsaProvenances: []` and `attestations: []`
  for all 24 (e.g. [databind](https://api.deps.dev/v3/systems/maven/packages/com.fasterxml.jackson.core%3Ajackson-databind/versions/2.22.3)).
  No build was reproduced.
- **Upstream metadata error.** The commons-lang3 3.20.0 POM gives the SCM tag `rel/commons-lang-3.18.0` (POM above).
- **Gradle ignores the snapshot repository.** jackson-bom 2.22.3 declares a snapshot `<repositories>` entry
  ([POM](https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.22.3/jackson-bom-2.22.3.pom)). Gradle
  ignores POM repositories ([docs](https://docs.gradle.org/current/userguide/supported_repository_types.html#strict_limitation_to_declared_repositories)).

## 4. Maintenance and adoption
- **Scorecard, 2026-10-19 to 11-05** (`SC/<owner>/<repo>`): guava 8.7; commons-lang 8.4; commons-bcel 8.3; jackson-databind 8.1; commons-io 7.8; httpcomponents-core 7.4; jackson-core, httpcomponents-client and commons-logging 7.2; error-prone 7.1; slf4j 5.8; javaparser 5.7; j2objc 4.7; javassist 4.2 (Security-Policy 0); checker-framework 3.8; jaxb-spec 1.9 and activation 1.7 ("project is archived"). Code-Review is 0 to 5 in every repo.
- **Newer releases exist** (deps.dev package pages, e.g. [httpclient5](https://api.deps.dev/v3/systems/maven/packages/org.apache.httpcomponents.client5%3Ahttpclient5)): jackson-core/-databind 2.22.4 (10-17); httpclient5 5.6.5 (10-06); httpcore5/-h2 5.4.4 (09-20); guava 33.8.0-jre (11-02); javassist 3.33.0-GA; bcel 6.13.0; commons-lang3 3.21.0; commons-io 2.22.0; slf4j-api 2.0.20; checker-qual 4.4.0; error_prone_annotations 2.52.0. Their release notes were not read. Each upgrade is a new review.
- **Dormant projects:** jaxb-api and activation are archived upstream. jaxb-api 2.4.0-b180830.0359 carries a build stamp; no plain 2.4.0 exists, and plain 2.3.1 was published later, on 2018-09-12 ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/javax.xml.bind%3Ajaxb-api)). jsr305 has had no release since 2017. listenablefuture is an empty marker jar by design (POM description).

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06** for all 24 coordinates: `inputs/osv.json` (an OSV batch query of every lockfile coordinate
run on 2026-11-06, not re-run by me) and deps.dev `advisoryKeys: []` on each version page (`DD/<g>%3A<a>/versions/<v>`).
guava 33.6.0-jre, which upstream resolves, carries [GHSA-xxph-c9ww-hj94](https://api.osv.dev/v1/vulns/GHSA-xxph-c9ww-hj94):
affected from 4.0 up to but not including 33.7.2; the database says HIGH, the advisory text 5.9 (Medium). 33.7.2-jre is
the fixed version, hence condition 2.

## 6. License
- **Apache-2.0:** Jackson, HttpComponents, Commons, bcel, Guava and its satellites, error_prone_annotations, jsr305 (per its POM). **MIT:** slf4j-api, checker-qual (deps.dev: "non-standard").
- **javaparser:** LGPL-3.0 or Apache-2.0 ([POM](https://repo1.maven.org/maven2/com/github/javaparser/javaparser-core/3.28.2/javaparser-core-3.28.2.pom)). **javassist:** MPL-1.1, LGPL-2.1 or Apache-2.0 (POM, section 3).
- **jaxb-api:** CDDL-1.1 / GPL-2.0 with the classpath exception ([parent](https://repo1.maven.org/maven2/javax/xml/bind/jaxb-api-parent/2.4.0-b180830.0359/jaxb-api-parent-2.4.0-b180830.0359.pom)); **activation:** "CDDL/GPLv2+CE" ([parent](https://repo1.maven.org/maven2/com/sun/activation/all/1.2.0/all-1.2.0.pom)). Both reach consumers through :core's POM (condition 6b).

## 7. Execution privileges (runtime: test JVMs that run `Conformance.check`, and consumers' test JVMs)
| Capability | Artifacts | When reached |
|---|---|---|
| **Network**: HTTP/1.1, HTTP/2, TLS, system proxy properties | httpclient5, httpcore5, -h2 | Only inside `com.structurizr.http.HttpClient.get()`, after its allowlist check (`ST/structurizr-client/src/main/java/com/structurizr/http/HttpClient.java` L65, L71-93). The constructor and `allow()` touch no Apache class (L26-32, L115-116). The parser creates one client per instance (`ST/structurizr-dsl/src/main/java/com/structurizr/dsl/StructurizrDslParser.java` L52). With nothing allowed: **never** |
| **Implementation discovery**: loads classes by name | commons-logging, slf4j-api | At parser class initialisation (`StructurizrDslParser.java` L28). LogFactory reads `commons-logging.properties`, a system property and ServiceLoader, then prefers Log4j API, then SLF4J ([source](https://raw.githubusercontent.com/apache/commons-logging/rel/commons-logging-1.4.0/src/main/java/org/apache/commons/logging/LogFactory.java)). SLF4J ServiceLoads a provider; with none it falls back to NOP and prints a warning ([source](https://raw.githubusercontent.com/qos-ch/slf4j/v_2.0.19/slf4j-api/src/main/java/org/slf4j/LoggerFactory.java)). Condition 5 |
| **Reflective object construction from JSON** | jackson-databind, -core | Structurizr's JSON reading, which the kit does not use (condition 4). Default typing is off by default: `DEFAULT_BASE` passes a null typer ([ObjectMapper](https://raw.githubusercontent.com/FasterXML/jackson-databind/jackson-databind-2.22.3/src/main/java/com/fasterxml/jackson/databind/ObjectMapper.java)) |
| **Bytecode reading and generation; Java source parsing** | bcel, javassist, javaparser-*, plus guava, commons-io and commons-lang3 under them | Only through `structurizr-component`, i.e. `!components`, which needs `COMPONENT_FINDER` (off). deps.dev edges: [graph](https://api.deps.dev/v3/systems/maven/packages/com.structurizr%3Astructurizr-dsl/versions/6.2.4:dependencies) |
| **JAXB implementation lookup** | jaxb-api, activation | Brought in by structurizr-client. Call sites were not traced (section 11) |
| **None** (annotations or markers) | jackson-annotations, jsr305, checker-qual, j2objc, error_prone_annotations, failureaccess, listenablefuture | n/a |

**Build time.** Nothing here runs at build time. On the processor path, guava and its satellites run inside javac with
the compiler JVM's rights (Error Prone review §7).

## 8. Direct dependencies as published (edges from the deps.dev graph above; versions as locked)
- **structurizr-client** → jackson-databind (→ jackson-core, jackson-annotations), httpclient5 (→ httpcore5, httpcore5-h2, slf4j-api), jaxb-api (→ javax.activation-api).
- **structurizr-core** → jackson-annotations, jsr305, commons-logging.
- **structurizr-component** → bcel (→ commons-lang3, commons-io), javaparser-symbol-solver-core (→ javaparser-core, javassist, guava, checker-qual).
- **guava 33.7.2-jre** → failureaccess, listenablefuture, jspecify 1.0.1, error_prone_annotations 2.50.0, j2objc-annotations 3.1 ([POM](https://repo1.maven.org/maven2/com/google/guava/guava/33.7.2-jre/guava-33.7.2-jre.pom)).
- **slf4j-api:** httpclient5 asks for 1.7.36 and archunit 1.5.1 for 2.0.19.
- **jackson-bom** is a platform (POM only) for the Jackson modules.
- **Covered elsewhere, not re-reviewed:** structurizr-client, -core, -component, -export and -import 6.2.4 (structurizr-dsl review). Their SHA-256 values in that review equal verification-metadata.
- **POM metadata only, not in the lockfile,** present in verification-metadata:
  - Parents: oss-parent 79, jackson-parent 2.22, javaparser-parent 3.28.2, jaxb-api-parent, `com.sun.activation:all` 1.2.0, guava-parent 26.0-android / 33.4.0-android, commons-parent 91/92/95/102, httpcomponents-parent 14, httpclient5-/httpcore5-parent, slf4j-parent / slf4j-bom 2.0.19, jvnet-parent 1/5.
  - BOMs: mockito-bom 4.11.0 (imported by [commons-parent 102](https://repo1.maven.org/maven2/org/apache/commons/commons-parent/102/commons-parent-102.pom)); opentelemetry-bom 1.49.0 (by [httpclient5-parent](https://repo1.maven.org/maven2/org/apache/httpcomponents/client5/httpclient5-parent/5.6.4/httpclient5-parent-5.6.4.pom)); groovy-bom 4.0.11 and spock-bom 2.3-groovy-4.0 (**importer not found**).
  - They carry no code. Entries are needed if the checker covers metadata (design §3).

## 9. Alternatives considered, and exclusions to test first
1. **Exclude `structurizr-component`** from :core. This removes bcel, javassist, both javaparser jars, guava, failureaccess, listenablefuture, j2objc, checker-qual 3.55.1, error_prone_annotations 2.50.0, commons-io and commons-lang3 3.20.0 from the runtime and published trees. It also settles condition 6a. structurizr-dsl declares the dependency compile, non-optional ([POM](https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/structurizr-dsl/pom.xml)). The test must prove all of the following:
   - The whole :core suite passes with the exclusion.
   - Every `architecture/*.dsl` parses.
   - A DSL containing `!components` still fails to parse, whatever the error.
   - The generated POM carries the exclusion.
2. **Exclude httpclient5, httpcore5 and httpcore5-h2.** A request would then fail closed, with `NoClassDefFoundError`. The test must prove:
   - The suite passes.
   - `!include https://…`, `workspace extends https://…`, URL image views and `theme <url>` are still rejected.
   - No parse of the repository's DSL raises `NoClassDefFoundError`.
   - slf4j-api stays, because archunit needs it.
3. **Exclude jaxb-api and javax.activation-api.** The test must prove that the suite and every DSL parse pass with both absent. A test should also assert that `Class.forName("javax.xml.bind.JAXBContext")` fails on that classpath.

The alternative of importing Structurizr JSON through core only would drop the DSL (top-level review §9).

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:** network code ships in a library that never needs it, and one mistaken `allow(".*")` would re-arm it
(with the redirect-following noted in the structurizr-dsl review). Unused bytecode and parser libraries widen consumers'
trees, and the attack surface if a future Structurizr feature defaults on. jaxb-api and activation have no upstream, so
any defect is permanent. Gradle locks do not govern Maven consumers (design §1).

## 11. Not verified
- **JARs:** none downloaded or inspected; no `.asc` checked. 12 of the 24 have no publisher SHA-256, so their hash is trust-on-first-download (section 3).
- **Call sites:** where structurizr-client uses `javax.xml.bind`; whether Structurizr's JSON reader enables default typing; whether the JVM verifier loads `org.apache.hc.*` when linking `HttpClient` (section 9 tests it).
- **Other:** upstream notes of the newer versions in section 4; the groovy-bom and spock-bom importers (the second half of commons-parent 102 was read only through a summary).
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in. Repo-level Scorecard "Vulnerabilities" findings concern each repo's HEAD and were not mapped to these versions.
