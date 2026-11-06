---
component: "tools.jackson.core:jackson-core, jackson-databind; tools.jackson.dataformat:jackson-dataformat-yaml; tools.jackson:jackson-bom; com.fasterxml.jackson.core:jackson-annotations; org.yaml:snakeyaml; org.snakeyaml:snakeyaml-engine; com.ethlo.time:itu"
kind: maven
version: "jackson-core, jackson-databind, jackson-dataformat-yaml, jackson-bom 3.1.7; jackson-annotations 2.21; snakeyaml 2.6; snakeyaml-engine 3.0.1; itu 1.17.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api as locked. jackson-databind 3.1.7 is REJECTED on every configuration (GHSA-m8xf-2cr7-77ph). Approved: jackson-core 3.1.7, jackson-bom 3.1.7 and snakeyaml 2.6 on compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath; jackson-annotations 2.21 on compileClasspath, runtimeClasspath, testCompileClasspath (testRuntimeClasspath resolves 2.22, not covered here); jackson-dataformat-yaml 3.1.7, snakeyaml-engine 3.0.1 and itu 1.17.0 on testCompileClasspath and testRuntimeClasspath only"
conditions: ["Exact versions and SHA-256 values as in allowlist-entries.json (all equal the publisher's Central .sha256 sidecars); any other version or hash is a new review", "jackson-databind 3.1.7 is REJECTED: it is inside the affected range '>= 3.0.0, <= 3.1.7' of GHSA-m8xf-2cr7-77ph / CVE-2026-110879 (Critical, CVSS 9.8, published 2026-10-20), fixed in 3.1.8. Remedy: constrain the whole tools.jackson 3.x set to 3.1.8 (for example an enforced platform on tools.jackson:jackson-bom:3.1.8, which json-schema-validator 3.0.9 already declares) and submit jackson-core, jackson-databind, jackson-dataformat-yaml and jackson-bom 3.1.8 for review", "Until and after the upgrade: no default typing (activateDefaultTyping) and no @JsonTypeInfo(use = Id.CLASS or Id.MINIMAL_CLASS) on any type that is read from request bodies, events or other untrusted JSON; an ArchUnit rule in the app's tests enforces the annotation part", "jackson-dataformat-yaml 3.1.7 is usable only together with an approved jackson-databind of the same version; it and snakeyaml-engine read only the repository's own contract file", "snakeyaml 2.6 parses only repository-owned or deployer-owned configuration (application*.yml): Spring Boot's OriginTrackedYamlLoader disables SnakeYAML's alias, code-point and recursive-key limits, so no YAML from requests, uploads or other untrusted sources is parsed with it, and no CompactConstructor is used", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: Jackson 3.1.7 and jackson-annotations 2.21; SnakeYAML 2.6, snakeyaml-engine 3.0.1, itu 1.17.0

**Method.** Read on 2026-11-06 with WebFetch: Central listings, sidecars, POMs and `.module` files; deps.dev; OSV; the
FasterXML repository security advisories; Scorecard; and Jackson, SnakeYAML and Spring Boot sources at their tags (the
SnakeYAML GitHub mirror at `master`; bitbucket.org was refused). A second, independent fetch confirmed the Critical
advisory. Nothing was downloaded, built or run.

## 1. Verdict
**REJECT jackson-databind 3.1.7. APPROVE-WITH-CONDITIONS for the other seven artifacts.**
- **The advisory.** On 2026-10-20 FasterXML published GHSA-m8xf-2cr7-77ph (CVE-2026-110879, Critical,
  `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H`, CWE-502): "PolymorphicTypeValidator bypass … allows arbitrary class
  instantiation". It lists `tools.jackson.core:jackson-databind` `>= 3.0.0, <= 3.1.7` as affected and **3.1.8** as
  patched ([advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-m8xf-2cr7-77ph)).
- **The feeds don't have it.** OSV returns 404 for it, and deps.dev still shows `advisoryKeys: []` for 3.1.7, so
  `inputs/osv.json` misses it ([OSV](https://api.osv.dev/v1/vulns/GHSA-m8xf-2cr7-77ph),
  [deps.dev](https://api.deps.dev/v3/systems/maven/packages/tools.jackson.core%3Ajackson-databind/versions/3.1.7)).
- **Reachability: not reachable by default.** The affected paths are class-name polymorphism (`Id.CLASS` or
  `Id.MINIMAL_CLASS`) and default typing. Jackson 3 leaves default typing off by default
  ([MapperBuilder](https://raw.githubusercontent.com/FasterXML/jackson-databind/jackson-databind-3.1.7/src/main/java/tools/jackson/databind/cfg/MapperBuilder.java)).
  The app is not yet written, so this cannot be traced to call sites. A single annotation in a later PR would arm the
  bug, and the advisory says the default validator gives no protection.
- **Why reject rather than condition.** jackson-databind is the request-body deserializer of a network-facing API. A
  fix in the same patch line has been on Central since 2026-10-18, and json-schema-validator 3.0.9 already asks for
  3.1.8 (its review §8). The remedy, moving to 3.1.8, is in condition 2.

## 2. What it is and why it is needed
- **Jackson 3** is Spring Boot 4's JSON stack (`spring-boot-starter-jackson`). Spring Modulith's `events-jackson` uses
  it to serialize event publications (Modulith review §7). jackson-annotations 2.x is the annotation line that
  Jackson 3 shares with 2.x.
- **SnakeYAML 2.6** reads `application.yml`; Boot provides it through `spring-boot-starter`
  ([external config](https://docs.spring.io/spring-boot/reference/features/external-config.html)).
- **jackson-dataformat-yaml → snakeyaml-engine, and itu:** dependencies of json-schema-validator 3.0.9, for the YAML
  contract and RFC 3339 dates (that review §8). Test only.

## 3. Provenance
| Artifact | Publisher → source | Central date | Publisher SHA-256 |
|---|---|---|---|
| jackson-core, -databind 3.1.7 | FasterXML, `tools.jackson.core` → FasterXML/jackson-core, jackson-databind ([POM](https://repo1.maven.org/maven2/tools/jackson/core/jackson-databind/3.1.7/jackson-databind-3.1.7.pom)) | 2026-09-21/22 | sidecars **match** ([dir](https://repo1.maven.org/maven2/tools/jackson/core/jackson-databind/3.1.7/)) |
| jackson-dataformat-yaml 3.1.7 | FasterXML → jackson-dataformats-text ([POM](https://repo1.maven.org/maven2/tools/jackson/dataformat/jackson-dataformat-yaml/3.1.7/jackson-dataformat-yaml-3.1.7.pom)) | 2026-09-22 | **match** |
| jackson-bom 3.1.7 (POM only) | FasterXML, parent `oss-parent` 79 ([POM](https://repo1.maven.org/maven2/tools/jackson/jackson-bom/3.1.7/jackson-bom-3.1.7.pom)) | 2026-09-21 | `.pom.sha256` **matches** |
| jackson-annotations 2.21 | FasterXML → jackson-annotations ([dir](https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.21/)) | 2026-01-18 | **match** |
| snakeyaml 2.6 | Andrey Somov, Alexander Maslov → bitbucket snakeyaml ([POM](https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.6/snakeyaml-2.6.pom)) | 2026-02-26 | **match** |
| snakeyaml-engine 3.0.1 | same maintainers ([POM](https://repo1.maven.org/maven2/org/snakeyaml/snakeyaml-engine/3.0.1/snakeyaml-engine-3.0.1.pom)) | 2025-11-13 | **match** |
| itu 1.17.0 | Morten Haraldsen (ethlo) → ethlo/itu ([POM](https://repo1.maven.org/maven2/com/ethlo/time/itu/1.17.0/itu-1.17.0.pom)) | 2026-10-07 | **match** |

- **Signatures and SBOMs.** All have `.asc`. Jackson publishes CycloneDX SBOMs on Central.
- **Build and signing.**
  - Jackson's 3.x CI workflow deploys only snapshots, and no CI release workflow was found, so releases appear to be
    built off CI ([main.yml](https://raw.githubusercontent.com/FasterXML/jackson-databind/3.x/.github/workflows/main.yml)).
  - itu releases from a tag-triggered GitHub workflow with GPG
    ([release.yml](https://raw.githubusercontent.com/ethlo/itu/main/.github/workflows/release.yml)).
  - SnakeYAML signs with maven-gpg in a release profile.
- **Attestations.** deps.dev shows `slsaProvenances: []` for all eight.
- **Repositories.** No POM declares a repository other than Central's snapshot repository.

## 4. Maintenance and adoption
- **Jackson:** 3.1.8 and 3.2.4 came out on 2026-10-18 ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/tools.jackson.core%3Ajackson-databind)).
  Scorecards: databind 8.1, core 7.2, dataformats-text 7.1, annotations 7.2, all with Code-Review 1–3
  ([SC](https://api.securityscorecards.dev/projects/github.com/FasterXML/jackson-databind)).
- **SnakeYAML:** 2.7 (2026-08-30) is newer. Its changelog removes `CompactConstructor`, which bypassed `TagInspector`,
  with no advisory published ([changes](https://raw.githubusercontent.com/snakeyaml/snakeyaml/master/src/changes/changes.xml)).
- **snakeyaml-engine:** 3.2 (2026-10-04) is newer.
- **No Scorecard:** snakeyaml, snakeyaml-engine and itu all return 404. itu 1.17.0 is its newest release; it has one
  listed developer and 41 stars ([repo](https://github.com/ethlo/itu)).

## 5. Known vulnerabilities at these versions
- **jackson-databind 3.1.7: GHSA-m8xf-2cr7-77ph**, Critical (§1). It is absent from OSV and deps.dev, and **found in
  FasterXML's repository advisories**.
- **The other seven: none found on 2026-11-06** (`inputs/osv.json`; deps.dev `advisoryKeys: []`).
- **Earlier Jackson 3 advisories end below 3.1.7:**
  - jackson-core: [GHSA-7hhh-6rmp-j9qf](https://api.osv.dev/v1/vulns/GHSA-7hhh-6rmp-j9qf) and
    [GHSA-p6pp-m3f8-5c89](https://api.osv.dev/v1/vulns/GHSA-p6pp-m3f8-5c89) are fixed in 3.1.7;
    GHSA-649p-m576-vr99 in 3.1.6.
  - jackson-databind: [GHSA-wv8q-qhhj-9h54](https://api.osv.dev/v1/vulns/GHSA-wv8q-qhhj-9h54) and
    GHSA-cxp5-3px4-pw24 are fixed in 3.1.7.
  - [GHSA-5gvw-p9qm-jgwh](https://api.osv.dev/v1/vulns/GHSA-5gvw-p9qm-jgwh) is fixed in 3.1.5.
- **SnakeYAML CVEs end below 2.6:** CVE-2022-1471
  ([GHSA-mjmj-j48q-9wg2](https://api.osv.dev/v1/vulns/GHSA-mjmj-j48q-9wg2)) is fixed in 2.0, and the 2022 DoS CVEs in
  1.31–1.32.
- **Out of scope here, but affected:** `com.fasterxml.jackson.core:jackson-databind` 2.22.3 on testRuntimeClasspath
  (through structurizr-client) is inside the advisory's 2.x range `>= 2.22.0, <= 2.22.3` (fixed 2.22.4). Its own review
  needs reopening.

## 6. License
Apache-2.0 for all eight (POMs). jackson-core shades `fastdoubleparser` 2.0.1 (MIT), according to its SBOM.

## 7. Execution privileges
- **jackson-databind (runtime): reflective object construction from JSON.** This is the CWE-502 surface. The default
  `DefaultBaseTypeLimitingValidator` is the only guard for annotation-based polymorphism, and the advisory bypasses it
  ([validator](https://raw.githubusercontent.com/FasterXML/jackson-databind/jackson-databind-3.1.7/src/main/java/tools/jackson/databind/jsontype/DefaultBaseTypeLimitingValidator.java)).
- **jackson-core:** a streaming parser with `StreamReadConstraints`.
- **jackson-annotations:** annotations only.
- **jackson-bom:** metadata only. It decides versions and imports no other BOM.
- **snakeyaml 2.6 (startup):**
  - Boot's `OriginTrackedYamlLoader` uses a `SafeConstructor` subclass.
  - **It disables the limits:** `setMaxAliasesForCollections(Integer.MAX_VALUE)`, `setAllowRecursiveKeys(true)`,
    `setCodePointLimit(Integer.MAX_VALUE)`
    ([loader](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.2/core/spring-boot/src/main/java/org/springframework/boot/env/OriginTrackedYamlLoader.java)).
  - That is safe only for trusted files (condition 5).
- **jackson-dataformat-yaml and snakeyaml-engine (test):**
  - Engine defaults apply: 50 aliases, a 3 MB code-point limit and no recursive keys.
  - Jackson's YAML parser reports aliases rather than expanding them
    ([YAMLParser](https://raw.githubusercontent.com/FasterXML/jackson-dataformats-text/jackson-dataformats-text-3.1.7/yaml/src/main/java/tools/jackson/dataformat/yaml/YAMLParser.java)).
    The defaults were read on `master`.
- **itu:** pure date-time parsing, with no dependencies.
- **None of the eight:** network, process, native code or build-time execution.

## 8. Direct dependencies as published
- **jackson-databind:** jackson-annotations (requires 2.21), jackson-core 3.1.7, and the jackson-bom 3.1.7 platform with
  `endorseStrictVersions` ([module](https://repo1.maven.org/maven2/tools/jackson/core/jackson-databind/3.1.7/jackson-databind-3.1.7.module)).
  **Because of that strict alignment, moving databind to 3.1.8 moves the whole set.**
- **jackson-dataformat-yaml:** jackson-core, jackson-databind 3.1.7, snakeyaml-engine 3.0.1.
- **jackson-core:** none (fastdoubleparser is shaded).
- **jackson-annotations, snakeyaml, snakeyaml-engine, itu:** none at runtime.
- **POM metadata only:** jackson-base 3.1.7, oss-parent 79, jackson-dataformats-text 3.1.7, jackson-parent 2.21.

## 9. Alternatives considered
- **Moving the Jackson set to 3.1.8** (recommended), rather than 3.2.4: 3.1.8 stays on Boot's line.
- **Gson:** would leave Boot's defaults and Modulith's Jackson serializer.
- **`application.properties` instead of YAML:** would remove SnakeYAML. That is cheap and worth considering, but Boot
  still ships the jar.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **Jackson's polymorphism has failed twice in 2026** (CVE-2026-54512 and its incomplete fix). Version pins alone do
  not protect a reflective deserializer, so condition 3 forbids the risky configuration outright.
- **SnakeYAML has two maintainers, an off-GitHub home and no Scorecard.**
- **Tests run jackson-annotations 2.22 while production runs 2.21.**

## 11. Not verified
- **Signatures:** no `.asc` was checked.
- **Jackson's release build process.**
- **SnakeYAML and snakeyaml-engine defaults at their exact tags** (read on `master`); Bitbucket and Codeberg were not
  read.
- **itu's contributor count.**
- **Call-site reachability:** the application code does not exist yet.
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in, and both miss GHSA-m8xf-2cr7-77ph.
