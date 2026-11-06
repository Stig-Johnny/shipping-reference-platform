---
component: "Annotation-only jars on apps/shipping-api: org.jmolecules:jmolecules-events, org.jmolecules:jstereotype; biz.aQute.bnd:biz.aQute.bnd.annotation; org.osgi:org.osgi.annotation.bundle, org.osgi.annotation.versioning, org.osgi.resource, org.osgi.service.serviceloader; com.google.errorprone:error_prone_annotations; org.jetbrains:annotations"
kind: maven
version: "jmolecules-events 2.0.1; jstereotype 2.0.1; biz.aQute.bnd.annotation 7.1.0; org.osgi.annotation.bundle 2.0.0; org.osgi.annotation.versioning 1.1.2; org.osgi.resource 1.0.0; org.osgi.service.serviceloader 1.0.0; error_prone_annotations 2.38.0; org.jetbrains:annotations 17.0.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api as locked. jMolecules: compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath (ships in the application). bnd, the four OSGi jars and error_prone_annotations 2.38.0: compileClasspath and testCompileClasspath only. org.jetbrains:annotations 17.0.0: testCompileClasspath and testRuntimeClasspath only"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "bnd, the four OSGi artifacts and error_prone_annotations 2.38.0 stay compile-only (never on runtimeClasspath or in a distribution); org.jetbrains:annotations 17.0.0 stays on test configurations only", "None of these jars is added to annotationProcessor, errorprone or any other processor/plugin configuration (none is reviewed as a tool)", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: annotation-only jars (jMolecules, bnd/OSGi, Error Prone annotations, JetBrains annotations)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, POMs and `.module` files; deps.dev; Scorecard; and
annotation sources at the tags. Nothing was downloaded, built or run. The brief grouped these as "Spring Modulith
internals". Tracing showed that only jMolecules comes from Modulith; the others come from log4j-api and duct-tape (§2).

## 1. Verdict
**APPROVE-WITH-CONDITIONS (scope conditions only).** All nine jars hold annotations, marker interfaces or OSGi API
types, and none has executable behaviour of note:
- **Six never reach runtime:** bnd, the four OSGi jars and error_prone_annotations sit on compile classpaths only.
  They have `CLASS` retention, so the JVM ignores them even if present.
- **jMolecules ships,** with `RUNTIME` annotations and empty marker interfaces.
- **JetBrains annotations 17.0.0** is old (2019; 26.1.0 is current) but test-only and inert.

## 2. What it is and why it is needed (who pulls each)
- **jmolecules-events 2.0.1 → jstereotype 2.0.1:** compile dependencies of `spring-modulith-moments` 2.1.2
  ([POM](https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-moments/2.1.2/spring-modulith-moments-2.1.2.pom)).
  Modulith is chosen in `v01-design.md` §4.
- **bnd.annotation 7.1.0, org.osgi.annotation.bundle 2.0.0, org.osgi.annotation.versioning 1.1.2, error_prone_annotations 2.38.0:**
  `log4j-api-2.25.5.module` declares them in its `apiElements` variant only. Its `runtimeElements` variant has no
  dependencies ([module](https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.25.5/log4j-api-2.25.5.module)).
  The parent POM marks them `provided` and lists them under `compileOnlyApiDependencies`
  ([log4j-2.25.5.pom](https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j/2.25.5/log4j-2.25.5.pom)). log4j-api
  arrives through `spring-boot-starter-logging` (logging review).
- **org.osgi.resource and org.osgi.service.serviceloader 1.0.0:** compile dependencies of bnd.annotation
  ([POM](https://repo1.maven.org/maven2/biz/aQute/bnd/biz.aQute.bnd.annotation/7.1.0/biz.aQute.bnd.annotation-7.1.0.pom)).
  osgi.annotation.versioning is also a dependency of osgi.annotation.bundle.
- **org.jetbrains:annotations 17.0.0:** declared by `org.rnorth.duct-tape:duct-tape` 1.0.8
  ([POM](https://repo1.maven.org/maven2/org/rnorth/duct-tape/duct-tape/1.0.8/duct-tape-1.0.8.pom)), a compile
  dependency of Testcontainers 2.0.5. Other pullers were not checked.

## 3. Provenance
| Artifact | Publisher → source | Central date | `.asc` | Publisher SHA-256 |
|---|---|---|---|---|
| jmolecules-events, jstereotype 2.0.1 | `org.jmolecules`; Oliver Drotbohm, Henning Schwentner, Stephan Pirnbaum → xmolecules/jmolecules ([POM](https://repo1.maven.org/maven2/org/jmolecules/jmolecules-events/2.0.1/jmolecules-events-2.0.1.pom)) | 2025-11-20 | yes | both sidecars **match** ours ([events](https://repo1.maven.org/maven2/org/jmolecules/jmolecules-events/2.0.1/jmolecules-events-2.0.1.jar.sha256), [jstereotype](https://repo1.maven.org/maven2/org/jmolecules/jstereotype/2.0.1/jstereotype-2.0.1.jar.sha256)) |
| biz.aQute.bnd.annotation 7.1.0 | Bndtools (Peter Kriens, BJ Hargrave, Ray Augé) → bndtools/bnd | 2024-11-26 | yes | none (only `.md5`/`.sha1`) ([dir](https://repo1.maven.org/maven2/biz/aQute/bnd/biz.aQute.bnd.annotation/7.1.0/)) |
| org.osgi.annotation.bundle 2.0.0; .versioning 1.1.2 | Eclipse Foundation / OSGi Working Group → osgi/osgi ([POM](https://repo1.maven.org/maven2/org/osgi/org.osgi.annotation.bundle/2.0.0/org.osgi.annotation.bundle-2.0.0.pom)) | 2022-02-08; 2021-10-13 | yes | none |
| org.osgi.resource 1.0.0; .service.serviceloader 1.0.0 | OSGi Alliance → osgi.org/git/build.git ([POM](https://repo1.maven.org/maven2/org/osgi/org.osgi.resource/1.0.0/org.osgi.resource-1.0.0.pom)) | 2016-11-15; 2015-08-18 | yes | none (a `.jar.asc.sha256` hashes the signature, not the jar) |
| error_prone_annotations 2.38.0 | Google → google/error-prone ([POM](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.38.0/error_prone_annotations-2.38.0.pom)) | 2025-04-18 | yes | none |
| org.jetbrains:annotations 17.0.0 | JetBrains Team → JetBrains/java-annotations ([POM](https://repo1.maven.org/maven2/org/jetbrains/annotations/17.0.0/annotations-17.0.0.pom)) | 2019-01-30 | yes | none |

- **Seven of nine have no publisher SHA-256.** Their allowlist hashes are our own first download
  (`verification-metadata.xml`, 2026-11-06), i.e. trust-on-first-download.
- **No attestations:** deps.dev shows `slsaProvenances: []` and `attestations: []` for all nine.
- **Release pipelines** were not read for these projects.

## 4. Maintenance and adoption
| Artifact | Newest (deps.dev) | Scorecard |
|---|---|---|
| jMolecules 2.0.1 | 2.0.1 is newest ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.jmolecules%3Ajmolecules-events)) | 3.2, Maintained 0, Code-Review 0 ([SC](https://api.securityscorecards.dev/projects/github.com/xmolecules/jmolecules)) |
| bnd.annotation 7.1.0 | 7.4.0 (2026-08-19) ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/biz.aQute.bnd%3Abiz.aQute.bnd.annotation)) | 5, Maintained 10, Code-Review 1 ([SC](https://api.securityscorecards.dev/projects/github.com/bndtools/bnd)) |
| OSGi annotation.bundle / .versioning | both newest | 6.8, Code-Review 10 ([SC](https://api.securityscorecards.dev/projects/github.com/osgi/osgi)) |
| osgi.resource 1.0.0; serviceloader 1.0.0 | 1.0.1 (2020); 1.0.0 is newest | same repo, though their POMs point to the old OSGi Alliance git |
| error_prone_annotations 2.38.0 | 2.52.0 (2026-11-02) ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.google.errorprone%3Aerror_prone_annotations)) | 7.1 ([SC](https://api.securityscorecards.dev/projects/github.com/google/error-prone)) |
| jetbrains annotations 17.0.0 | 26.1.0 (2026-02-18) ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.jetbrains%3Aannotations)) | 2.5, Maintained 0 ([SC](https://api.securityscorecards.dev/projects/github.com/JetBrains/java-annotations)) |

The old versions are inert annotation APIs, and log4j-api and duct-tape fix them. Upgrading would mean overriding
those publishers' metadata for no security gain.

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06** (`inputs/osv.json`; deps.dev `advisoryKeys: []` for all nine).

## 6. License
- **Apache-2.0:** jMolecules, the four OSGi jars, error_prone_annotations and JetBrains annotations.
- **bnd.annotation:** `(Apache-2.0 OR EPL-2.0)`.

All from the POMs above. None of the six compile-only jars is distributed.

## 7. Execution privileges
- **jMolecules (runtime).**
  - `@DomainEvent` and `@Stereotype` have `RUNTIME` retention; `types.DomainEvent` is an empty interface
    ([DomainEvent](https://raw.githubusercontent.com/xmolecules/jmolecules/2.0.1/jmolecules-events/src/main/java/org/jmolecules/event/annotation/DomainEvent.java),
    [Stereotype](https://raw.githubusercontent.com/xmolecules/jmolecules/2.0.1/jstereotype/src/main/java/org/jmolecules/stereotype/Stereotype.java)).
  - The jars also carry JSON catalogue files in `META-INF` (data, not code).
  - Modulith reads these annotations reflectively.
- **bnd and OSGi (compile only).**
  - `@ServiceProvider`, `@Export` and `@Version` are `CLASS` retention. The OSGi javadoc says "not retained at runtime"
    and "for use by tools to generate bundle manifests"
    ([ServiceProvider](https://raw.githubusercontent.com/bndtools/bnd/7.1.0/biz.aQute.bnd.annotation/src/aQute/bnd/annotation/spi/ServiceProvider.java),
    [Export](https://raw.githubusercontent.com/osgi/osgi/main/org.osgi.annotation.bundle/src/org/osgi/annotation/bundle/Export.java)).
  - They take effect only if a bnd tool runs, and none is in our build.
- **error_prone_annotations, JetBrains annotations:** `CLASS` retention
  ([CanIgnoreReturnValue](https://raw.githubusercontent.com/google/error-prone/v2.38.0/annotations/src/main/java/com/google/errorprone/annotations/CanIgnoreReturnValue.java),
  [NotNull](https://raw.githubusercontent.com/JetBrains/java-annotations/17.0.0/java8/src/main/java/org/jetbrains/annotations/NotNull.java)).
- **Build time:** none. Gradle ignores processors on the compile classpath, and no `javax.annotation.processing.Processor`
  service file was seen in the source trees read. The trees were partly truncated, so this is not verified (§11).
- **Network, process, file system, native code:** none of these.

## 8. Direct dependencies as published
- **jmolecules-events:** jstereotype 2.0.1 (compile). **jstereotype:** none.
- **bnd.annotation:** org.osgi.resource 1.0.0 and org.osgi.service.serviceloader 1.0.0 (compile); osgi.annotation 8.1.0 and two OSGi namespace jars (provided, so not resolved).
- **org.osgi.annotation.bundle:** org.osgi.annotation.versioning 1.1.2.
- **The rest:** none at compile or runtime.
- **POM metadata only:** `error_prone_parent` 2.38.0. Parents were not read for repository declarations.

## 9. Alternatives considered
- **Excluding the five log4j compile-only jars from `compileClasspath`** is possible. It would only remove inert
  annotations and risks compiler warnings about missing annotation types.
- **jMolecules cannot be removed** without dropping `spring-modulith-moments` from the starter.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:** very low. These jars have no behaviour, and six never load. The only material point is
trust-on-first-download for the seven hashes that no publisher published.

## 11. Not verified
- **The jars themselves:** no jar was listed. The absence of processor service files and native or class surprises is
  inferred from the source trees, and GitHub truncated those listings.
- **Signatures:** no `.asc` checked.
- **bnd annotations other than `@ServiceProvider`:** retention not checked. OSGi sources were read at `main`, not at
  the release tags.
- **Other pullers of jetbrains annotations,** beyond duct-tape.
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in.
