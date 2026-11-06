---
component: "tools.jackson.core:jackson-core, tools.jackson.core:jackson-databind, tools.jackson.dataformat:jackson-dataformat-yaml, tools.jackson:jackson-bom"
kind: maven
version: "3.1.8"
verdict: APPROVE-WITH-CONDITIONS
scope: "The project locked by jackson-3.1.8-lock.txt, recorded by the 3.1.7 review as apps/shipping-api (path to be confirmed, condition 7). jackson-core, jackson-databind and jackson-bom 3.1.8 on compileClasspath, runtimeClasspath, testCompileClasspath and testRuntimeClasspath. jackson-dataformat-yaml 3.1.8 on testCompileClasspath and testRuntimeClasspath only. The parent POMs jackson-base 3.1.8 and jackson-dataformats-text 3.1.8 are approved as metadata only"
conditions: ["Exact version 3.1.8 and the SHA-256 values in allowlist-entries.json, all of which equal the publisher's Central .sha256 sidecars. Any other version or hash is a new review", "Every tools.jackson module resolves to 3.1.8 on every configuration. Spring Boot 4.1.2 manages 3.1.7, so the override must be a build-level constraint in CODEOWNERS-protected build logic: with native Gradle platforms, Boot's jackson-bom.version property has no effect. No tools.jackson 3.1.7 module may appear in the lock, and jackson-databind 3.1.7 stays REJECTED", "Carried over from the 3.1.7 review: no default typing (activateDefaultTyping) and no @JsonTypeInfo(use = Id.CLASS or Id.MINIMAL_CLASS) on any type read from request bodies, events or other untrusted JSON. An ArchUnit rule in the app's tests enforces the annotation part", "The project's full test suite passes on 3.1.8 before merge. No released Spring Boot 4.1.x is built against 3.1.8, and 3.1.8 deliberately tightens PolymorphicTypeValidator behaviour (allowIfBaseType, allowIfSubTypeIsArray)", "jackson-dataformat-yaml 3.1.8 is test only. It is used through the default YAMLFactory, not YAMLAnchorReplayingFactory, and reads only the repository's own contract file", "jackson-annotations is not approved by this record. Version 2.21, which 3.1.8 requires, is covered by the 3.1.7 review; the 2.22 that resolves on testRuntimeClasspath still needs its own review", "A human code owner confirms the project path (v01-design names :example-app, not apps/shipping-api) and accepts this record before first use"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Delta review: Jackson 3.1.8 (jackson-core, jackson-databind, jackson-dataformat-yaml, jackson-bom)

**Method.** This is a delta on `jackson-3.1.7-review.md`, which still holds for anything not restated here. On 2026-11-06 I read, with WebFetch: every FasterXML advisory list page and the detail page of every 2026 advisory on 3.x; release notes and source at the `*-3.1.8` and `*-3.1.7` tags; Central listings, sidecars, POMs, `.module` files and SBOMs; deps.dev, OSV and Scorecard; and Spring's build files at `v4.1.2` and `v7.0.10`. The GitHub advisory API returned 403, and GitHub's compare pages timed out. Nothing was downloaded, built or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS for all four.**
- **3.1.8 is the patched release for GHSA-m8xf-2cr7-77ph.**
  - **The advisory** lists `tools.jackson.core:jackson-databind` `>= 3.0.0, <= 3.1.7` and `>= 3.2.0, <= 3.2.3` as affected, and `3.1.8` and `3.2.4` as patched ([advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-m8xf-2cr7-77ph)).
  - **OSV's CVE-2026-110879 record** names `893c391` as the fix commit for the 3.x range ([OSV](https://api.osv.dev/v1/vulns/CVE-2026-110879)). That commit is "[maven-release-plugin] prepare release jackson-databind-3.1.8" and is the tag commit ([commit](https://github.com/FasterXML/jackson-databind/commit/893c39121eaace97d7e9e02054f50af7831510bd)).
  - **The 3.1.8 release notes** list "#6287: Type parameters of generic type ids bypass `PolymorphicTypeValidator` [CVE-2026-110879]" ([VERSION](https://raw.githubusercontent.com/FasterXML/jackson-databind/jackson-databind-3.1.8/release-notes/VERSION)).
  - **The code is in the tag.** `PolymorphicTypeValidator.isArrayTypeAllowedExplicitly` and `jsontype/impl/ApprovedBaseTypeValidator` are both `@since 3.1.8`; they exist at 3.1.8 and are absent at 3.1.7 ([PTV](https://raw.githubusercontent.com/FasterXML/jackson-databind/jackson-databind-3.1.8/src/main/java/tools/jackson/databind/jsontype/PolymorphicTypeValidator.java)). `DatabindContext` now validates type parameters and array element types, with `[CVE-2026-110879]` comments ([source](https://raw.githubusercontent.com/FasterXML/jackson-databind/jackson-databind-3.1.8/src/main/java/tools/jackson/databind/DatabindContext.java)).
- **No advisory covers 3.1.8** (§5), and **every hash equals the publisher's sidecar** (§3).
- **Compatible with Boot 4.1.2 and Framework 7.0.10 at the API level, with one caveat (§10).** The fix intentionally changes polymorphic-typing behaviour, which is why the test-suite condition exists.

## 2. What it is and why it is needed
Unchanged from the 3.1.7 review §2:
- **Jackson 3 is Spring Boot 4's JSON stack** for the example app (v01-design §4: Boot 4.1.2, Modulith 2.1.2), and Modulith uses it to serialize events.
- **jackson-dataformat-yaml is test only.** The 3.1.7 review traces it to json-schema-validator 3.0.9, which v01-design does not name.
- **Why this delta:** jackson-databind 3.1.7 was rejected, and moving databind moves the whole set, because each `.module` aligns strictly on the BOM.

## 3. Provenance
- **Publisher:** FasterXML (Tatu Saloranta, `cowtowncoder`), namespace `tools.jackson`. Each POM's SCM tag is `jackson-*-3.1.8` ([core POM](https://repo1.maven.org/maven2/tools/jackson/core/jackson-core/3.1.8/jackson-core-3.1.8.pom)).
- **Tag commits:** core `072a872`, databind `893c391`, dataformats-text `1a9f03b`. All three are maven-release-plugin commits by cowtowncoder ([tag](https://github.com/FasterXML/jackson-databind/releases/tag/jackson-databind-3.1.8)).
- **Dates:** the wiki says 3.1.8 was released on 2026-10-17 ([wiki](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.8)). Central's files are dated 2026-10-18 00:01–00:35; the listing gives no time zone ([dir](https://repo1.maven.org/maven2/tools/jackson/core/jackson-databind/3.1.8/)).
- **Hashes.** Each Central `.sha256` sidecar equals the value in `jackson-3.1.8-checksums.xml`. That covers all 9 artifacts in the table below, plus jackson-bom 3.1.7 at `26c28ddf…27ff`. A pattern search matched all 10 values.

| Artifact (3.1.8) | SHA-256 (sidecar = checksums file) |
|---|---|
| jackson-core `.jar` / `.module` | `762caa00…c471` / `66a45b7f…694b` |
| jackson-databind `.jar` / `.module` | `2e7583c9…fb58` / `7cd9c3b4…a93e` |
| jackson-dataformat-yaml `.jar` / `.module` | `d9af4570…17a4` / `76824955…1a3d` |
| jackson-bom `.pom`; jackson-base `.pom`; jackson-dataformats-text `.pom` | `7d30fd95…81c8`; `282090b4…e278`; `c3909329…a03d` |

- **Signatures, SBOMs, builds.** Every file has an `.asc`. Core, databind and yaml also ship CycloneDX 1.6 SBOMs; the BOM has none. The Maven release builds set `project.build.outputTimestamp`, the reproducible-build setting.
- **Attestations:** deps.dev shows `slsaProvenances: []` and `attestations: []` ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/tools.jackson.core%3Ajackson-databind/versions/3.1.8)). The only repository declared is `central-snapshots`, with releases disabled.

## 4. Maintenance and adoption
- **Release line.** 3.1 is an LTS branch ([Jackson-Releases](https://github.com/FasterXML/jackson/wiki/Jackson-Releases)). 3.1.7 came out on 2026-09-21 and 3.1.8 on 2026-10-17.
- **Newer versions.** 3.2.4 (2026-10-18) is the newest 3.x. Central has no 3.1.9, and the `3.1` branch notes show "3.1.9 (not yet released)".
- **Scorecard** ([databind](https://api.securityscorecards.dev/projects/github.com/FasterXML/jackson-databind)): databind 8.1, core 7.2, dataformats-text 7.1, bom 6.4. Weak points: Code-Review 0–3; Branch-Protection 0–3 (`3.x` "does not require approvers"); core Token-Permissions 0 (`update-readme-version.yml` grants `contents: write`); Signed-Releases -1 (no GitHub releases).
- **Adoption:** Jackson 3 is Spring Boot 4's default JSON library (Boot BOM, §10).

## 5. Known vulnerabilities at 3.1.8
**None found on 2026-11-06.**

**The feeds are not usable as evidence.** deps.dev shows `advisoryKeys: []` for all four, but also for databind 3.1.7 ([3.1.7](https://api.deps.dev/v3/systems/maven/packages/tools.jackson.core%3Ajackson-databind/versions/3.1.7)). OSV returns 404 for GHSA-m8xf, GHSA-vvjm and GHSA-255r, and its Maven search for `tools.jackson` is empty. OSV has the databind bug only as CVE-2026-110879, published 2026-10-28. The evidence is therefore the publisher's advisory pages, read one by one:
- **jackson-databind, 18 advisories** ([list](https://github.com/FasterXML/jackson-databind/security/advisories)). GHSA-m8xf is patched in 3.1.8. In 15 others every 3.x range ends at or below 3.1.7; the latest, GHSA-wv8q-qhhj-9h54 and GHSA-cxp5-3px4-pw24, are patched in 3.1.7. GHSA-mhm7-754m-9p8w and GHSA-3wrr-7qpf-2prh have no 3.x entry.
- **jackson-core, GHSA-vvjm-vc27-799r** (CVE-2026-110729, Low, 2026-10-20) ([advisory](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-vvjm-vc27-799r)).
  - The structured range is `>= 3.2.0, <= 3.2.3`, but the text says "3.0.0-rc1 through latest 3.x RC".
  - The range is right. The bug needs the async parser with `ALLOW_HEXADECIMAL_NUMBERS`, which arrived in 3.2.0 (core #707), and the fix (#1765) is in 3.2.4 ([3.2 notes](https://raw.githubusercontent.com/FasterXML/jackson-core/3.2/release-notes/VERSION)). 3.1.8 is not affected.
  - The other 7 jackson-core advisories end at or below 3.1.6.
- **jackson-dataformats-text, GHSA-255r-36wv-4qpr** (CVE-2026-115876, High, 2026-10-16) ([advisory](https://github.com/FasterXML/jackson-dataformats-text/security/advisories/GHSA-255r-36wv-4qpr)).
  - It lists only the `com.fasterxml` 2.x package. The 3.x line fixed it in 3.1.6: "#707: (yaml) … (GHSA-255r-36wv-4qpr)" ([notes](https://raw.githubusercontent.com/FasterXML/jackson-dataformats-text/jackson-dataformats-text-3.1.8/release-notes/VERSION)).
  - In 3.1.8 the opt-in replaying parser loops instead of recursing, and calls `validateNestingDepth` ([source](https://raw.githubusercontent.com/FasterXML/jackson-dataformats-text/jackson-dataformats-text-3.1.8/yaml/src/main/java/tools/jackson/dataformat/yaml/YAMLAnchorReplayingParser.java)).
  - The advisory credits the fix to "PR #624", which is wrong: #624 fixes event sequences.
- **No advisory dated after 2026-10-20** appears in any of the three repositories.

## 6. License
- All four are Apache-2.0 (POMs, deps.dev).
- jackson-core shades fastdoubleparser 2.0.1, which is MIT and the same version as in 3.1.7 ([SBOM](https://repo1.maven.org/maven2/tools/jackson/core/jackson-core/3.1.8/jackson-core-3.1.8-sbom-cyclonedx.json)).

## 7. Execution privileges
**Unchanged from the 3.1.7 review §7:** no network access, process execution, native code or build-time execution. jackson-databind constructs objects reflectively at runtime, which is the CWE-502 surface; jackson-dataformat-yaml runs only in tests.

**What 3.1.8 changes:** stricter type-id validation (#6287), plus input checks. Databind #6232 applies `StreamReadConstraints` when coercing `StringNode` to numbers, and core #1683 and #1744 reject malformed escapes. No release note lists a new capability; the full code diff was not read (§11).

## 8. Direct dependencies as published
- **jackson-databind:** jackson-annotations `requires 2.21`, jackson-core 3.1.8, and the jackson-bom 3.1.8 platform with `endorseStrictVersions` ([module](https://repo1.maven.org/maven2/tools/jackson/core/jackson-databind/3.1.8/jackson-databind-3.1.8.module)).
- **jackson-dataformat-yaml:** jackson-core, jackson-databind 3.1.8, and snakeyaml-engine 3.0.1, which the 3.1.7 review approved for test use ([module](https://repo1.maven.org/maven2/tools/jackson/dataformat/jackson-dataformat-yaml/3.1.8/jackson-dataformat-yaml-3.1.8.module)).
- **jackson-core:** only the BOM platform; fastdoubleparser is shaded in.
- **jackson-bom:** manages annotations 2.21 and every module at 3.1.8. It imports no BOM, and its parent is `oss-parent` 79 ([BOM](https://repo1.maven.org/maven2/tools/jackson/jackson-bom/3.1.8/jackson-bom-3.1.8.pom)).
- **Metadata only:** `jackson-base` 3.1.8, whose parent is jackson-bom and whose dependencyManagement imports junit-bom for tests ([POM](https://repo1.maven.org/maven2/tools/jackson/jackson-base/3.1.8/jackson-base-3.1.8.pom)), and `jackson-dataformats-text` 3.1.8, with `pom` packaging. oss-parent 79 (sidecar `9206b7dd…dae1`) is not in the given checksums excerpt.
- **Compared with 3.1.7,** only Jackson's own versions, tags, timestamps and hashes changed. No dependency was added or removed, and annotations 2.21, snakeyaml-engine 3.0.1 and oss-parent 79 are the same.
- **Nothing new needs its own review.**

## 9. Alternatives considered
- **3.2.4** is also patched, but it leaves Boot 4.1's line; Boot's BOM upgrade policy is `same-minor-version` ([build.gradle](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.2/platform/spring-boot-dependencies/build.gradle)).
- **Waiting for Boot 4.1.3** is not possible yet: there is no 4.1.3 on Central. Boot's "Upgrade to Jackson Bom 3.1.8" (#52601) is milestoned 4.2.0-RC2, and no 4.1.x backport was found ([issue](https://github.com/spring-projects/spring-boot/issues/52601)).

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Spring compatibility:**
- **Boot 4.1.2** (Central, 2026-10-22) manages `jackson-bom.version` 3.1.7, Jackson 2 at 2.21.7 and Framework 7.0.10 ([BOM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom)).
- **Framework 7.0.10** is built against `tools.jackson:jackson-bom:3.0.4` ([platform](https://raw.githubusercontent.com/spring-projects/spring-framework/v7.0.10/framework-platform/framework-platform.gradle)), so Boot already runs it on a newer Jackson minor.
- **Jackson's policy:** "Patch releases must be fully replaceable, with no changes to source or binary compatibility". Core components "MUST have same minor version", which holds: every module is 3.1.8, with annotations 2.21.
- **Boot's docs warn** that overriding "may cause compatibility issues and should be done with care". With a native platform, "you cannot use the properties" ([docs](https://docs.spring.io/spring-boot/gradle-plugin/managing-dependencies.html)).
- **The caveat.** The #6287 note says `allowIfBaseType()` now "only covers ones that are subtypes of the base type". It also says `allowIfSubTypeIsArray()` "no longer accepts abstract or interface element types". Code that relies on either can now fail at runtime, which is why the test suite must pass (condition 4).

**Residual risk:**
- **The PolymorphicTypeValidator needed at least four security fixes in 2026:** GHSA-j3rv and GHSA-rmj7 in 3.1.4, GHSA-gx83 in 3.1.6, and GHSA-m8xf in 3.1.8. GHSA-m8xf is an incomplete fix for GHSA-j3rv. Condition 3, not the version pin, is the real control.
- **The feeds lag.** deps.dev still misses GHSA-m8xf 17 days after publication, and OSV listed it 8 days late, as a CVE only. CI scanners that rely on these feeds would miss the next one too.
- **Repository controls are weak** (Scorecard). There is no provenance, and the 3.1.7 review found that releases appear to be built off CI.
- **Outside this delta, still open from the 3.1.7 review:** `com.fasterxml.jackson.core:jackson-databind` 2.22.3 on testRuntimeClasspath is inside GHSA-m8xf's 2.x range and is fixed in 2.22.4.

## 11. Not verified
- **Signatures and bytes.** No `.asc` signature was checked, and no JAR was hashed by me. The sidecars and the checksums file are compared with each other only, and the sidecars are served by the same host as the JARs.
- **The code diff from 3.1.7 to 3.1.8,** beyond the jsontype and `DatabindContext` files. GitHub's compare pages timed out at 101, 70 and 37 commits for databind, core and dataformats-text.
- **Whether the 3.1.8 source has `ALLOW_HEXADECIMAL_NUMBERS`.** It is inferred absent from the release notes.
- **GitHub's global advisory database.** The API returned 403, and `github.com/advisories/` returned 404 for GHSA-vvjm and GHSA-255r.
- **Whether Boot 4.1.2, Framework 7.0.10 or Modulith 2.1.2 call `allowIfBaseType`, `allowIfSubTypeIsArray` or default typing internally.** No code search was possible.
- **The build:** how it forces 3.1.8, the full lockfile (only the tools.jackson lines were given), why the parent POMs and the jackson-bom 3.1.7 POM appear in verification metadata, and the project path.
