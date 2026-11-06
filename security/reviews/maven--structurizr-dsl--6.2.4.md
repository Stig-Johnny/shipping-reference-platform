---
component: "com.structurizr:structurizr-dsl"
kind: maven
version: "6.2.4"
verdict: APPROVE-WITH-CONDITIONS
scope: "Runtime library of :core (compile and runtime classpaths), reaching the test classpaths of :core, :junit5 and :example-app through :core. Covers the six com.structurizr 6.2.4 JARs and two POM-only metadata files listed in section 8. Parsing repository-owned DSL only, with all ten parser features off."
conditions: ["Every StructurizrDslParser is created by one :core factory that disables all ten Features keys (ENVIRONMENT, FILE_SYSTEM, HTTP, HTTPS, INCLUDE, PLUGINS, SCRIPTS, COMPONENT_FINDER, DOCUMENTATION, DECISIONS) before parse. It never calls getHttpClient().allow(...) or the deprecated setRestricted(...)", "Tests in :core prove that each of these is rejected: !script, !plugin, !include (file and URL), workspace extends, !docs, !adrs, !components, !impliedRelationships <class>, image views and theme files. They also prove that ${NAME} is not replaced from the environment", "The library never fetches, renders or follows URLs stored in a parsed workspace (themes, icons, image views)", "Models are parsed with parse(File) from code-owned repository paths (architecture/*.dsl). A model from anywhere else is treated as code and needs its own review", "No JSR-223 script engine (Groovy, Kotlin scripting, JRuby, a JavaScript engine) is on any classpath where the parser runs", "Every transitive artifact listed in section 8 has its own approved review before use. Guava 33.6.0-jre carries GHSA-xxph-c9ww-hj94", "The resolved JAR SHA-256 values equal those in section 3. Any other version or hash is a new review", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: com.structurizr:structurizr-dsl 6.2.4 (with structurizr-core and four sibling modules)

## 1. Verdict
**APPROVE-WITH-CONDITIONS.**
- Every feature the design feared exists. All are on by default: the constructor enables all ten keys ([`StructurizrDslParser.java:74-92`](https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/structurizr-dsl/src/main/java/com/structurizr/dsl/StructurizrDslParser.java)).
- Each feature can be switched off with `getFeatures().disable(key)`.
- **The design's list is too short.** `!docs`, `!adrs` and `!components` also load classes and read files, and they are gated by `DOCUMENTATION`, `DECISIONS` and `COMPONENT_FINDER`, not by `PLUGINS` or `FILE_SYSTEM` (section 7).
- The publisher is a single maintainer, with an off-CI release and no provenance, and the artifact brings 25 transitive artifacts. Acceptable only under the conditions.

## 2. What it is and why it is needed
The parser reads the C4 model (`architecture/*.dsl`) and its `contract.id`/`contract.packages` properties for `Conformance.check`. The design asks for this consolidated parser, with scripts, plugins, environment substitution and external or file inclusion disabled through its feature controls (repository design note `v01-design.md`, section 2).

## 3. Provenance
- **Publisher.** Namespace `com.structurizr`, developer "Structurizr <hello@structurizr.com>", SCM `github.com/structurizr/structurizr` ([parent POM](https://repo1.maven.org/maven2/com/structurizr/structurizr/6.2.4/structurizr-6.2.4.pom)). Repo created 2025-11-30, Apache-2.0 ([repo API](https://api.github.com/repos/structurizr/structurizr)).
- **Release.** Tag `v6.2.4` is commit `92571b877c52899594351c46189519f1ef256cfd`, also application tag `v2026.10.26` ([tags](https://api.github.com/repos/structurizr/structurizr/tags)); GitHub release "v6.2.4 (library)", 2026-10-26T10:11:36Z, by `simonbrowndotje`, no uploaded assets ([releases](https://api.github.com/repos/structurizr/structurizr/releases)); Central files dated 2026-10-26 09:21 ([listing](https://repo1.maven.org/maven2/com/structurizr/structurizr-dsl/6.2.4/)).
- **Build and signing: outside GitHub Actions.** The root POM binds `maven-gpg-plugin` 3.2.7 and `central-publishing-maven-plugin` 0.9.0 in the main build and has no reproducible-build settings ([pom.xml](https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/pom.xml)). The only workflow, `maven.yml`, builds and tests on push or PR to `main`: no deploy, GPG or secrets, actions pinned by tag ([maven.yml](https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/.github/workflows/maven.yml)). So releases are built and signed off-CI, presumably on the maintainer's machine. Every file has a `.asc`; no SLSA provenance or attestations ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.structurizr%3Astructurizr-dsl/versions/6.2.4)).
- **SHA-256.** From the `.sha256` next to each JAR, e.g. [`structurizr-dsl-6.2.4.jar.sha256`](https://repo1.maven.org/maven2/com/structurizr/structurizr-dsl/6.2.4/structurizr-dsl-6.2.4.jar.sha256): dsl `265811fe…f9bcfb`, core `531e4692…57a86c`, client `e4edd0ae…288e5a`, import `3662d8f8…a401a4`, export `ed026114…14401b`, component `1408a9f5…35f2ee`. dsl and core read twice independently (match), the other four once. Full values in `allowlist-entries.json`.

## 4. Maintenance and adoption
- **Releases:** 6.0.0 (2026-02-01), 6.1.0 (03-06), 6.2.0 (04-18), 6.2.1 (05-15), 6.2.2 (06-28), 6.2.3 (09-17) and 6.2.4 (deps.dev 10-27). 6.2.4 is the newest ([versions](https://api.deps.dev/v3/systems/maven/packages/com.structurizr%3Astructurizr-dsl)).
- **One maintainer.** The contributors API lists only `simonbrowndotje` (482), and `has_pull_requests` is false ([contributors](https://api.github.com/repos/structurizr/structurizr/contributors)). Bus factor 1. 522 stars; last push 2026-10-31.
- **Scorecard:** not available. The API returns 404 ([scorecard](https://api.securityscorecards.dev/projects/github.com/structurizr/structurizr)).
- **Security policy:** report by e-mail to help@structurizr.com ([policy](https://github.com/structurizr/structurizr/security/policy)).
- **Diff 6.2.3 → 6.2.4:** 17 commits, 56 files, one author ([patch](https://github.com/structurizr/structurizr/compare/v6.2.3...v6.2.4.patch)). **Nothing under `structurizr-dsl/src/main` changed**; the only library main-code change is `escapeSemicolon()` in `MermaidDiagramExporter`, the only library dependency change jackson-databind 2.22.2 → 2.22.3 in client; the rest is the application and Docker.

## 5. Known vulnerabilities at this version
**Method.** OSV `POST /v1/query` could not be called: the fetch tool is GET-only, and shell network access was not permitted. I used deps.dev `advisoryKeys` (sourced from OSV), the OSV name search and GitHub advisories.

- **Direct: none found on 2026-11-06.**
  - deps.dev `advisoryKeys: []` for dsl and core 6.2.4.
  - The OSV search for `structurizr` returned no results ([osv](https://osv.dev/list?ecosystem=Maven&q=structurizr)).
  - Repo advisories: `[]` ([advisories](https://api.github.com/repos/structurizr/structurizr/security-advisories)).
- **Transitive: guava 33.6.0-jre, GHSA-xxph-c9ww-hj94 / CVE-2026-102554** (fixed in 33.7.2; [osv](https://api.osv.dev/v1/vulns/GHSA-xxph-c9ww-hj94)).
  - A DoS on Java deserialization of `CompactHashMap`/`CompactHashSet`.
  - **Not reachable:** it needs `ObjectInputStream.readObject` on attacker data, and `:core` does no Java deserialization.
  - Upstream's application POM forces guava 33.7.2-jre "to resolve vulnerabilities"; the library tree does not.
- **All other transitives in section 8:** deps.dev `[]`.

## 6. License
- **This component:** Apache-2.0 ([LICENSE](https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/LICENSE)).
- **Passed on to consumers by the published :core POM:**
  - jaxb-api 2.4.0-b180830.0359: CDDL-1.1 / GPL-2.0-with-classpath-exception ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/javax.xml.bind%3Ajaxb-api/versions/2.4.0-b180830.0359)).
  - javassist 3.31.0-GA: "non-standard, LGPL-2.1, Apache-2.0" ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.javassist%3Ajavassist/versions/3.31.0-GA)).
  - Both need an owner licence decision.

## 7. Execution privileges (runtime, wherever the conformance check runs: test JVMs)
Paths are under https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/, mostly `structurizr-dsl/src/main/java/com/structurizr/dsl/`. Line numbers were counted from the raw files.

| Capability | Gate (`Features.java:5-18`) | Evidence |
|---|---|---|
| `${NAME}` → `System.getenv` | `ENVIRONMENT` (when off, the token silently stays literal) | `StructurizrDslParser.substituteStrings` |
| `!include` file/dir: **no containment, `../` allowed** | `INCLUDE` + `FILE_SYSTEM` | `IncludeParser.java:24-26,58` |
| `!include <url>`, `workspace extends <url>`, URL images | `HTTP(S)` **and** HttpClient allowlist | `IncludeParser.java:39-54`, `WorkspaceParser.java:42-50` |
| HttpClient: **deny-all until `allow(regex)`**. It checks only the first URL; redirects get the default handling, with `useSystemProperties()` | allowlist | `structurizr-client/.../http/HttpClient.java:27,64-67,79-82,115-127` |
| `!plugin <class>`: URLClassLoader over `plugins/*.jar` beside the DSL, plus the app classpath | `PLUGINS` | `DslContext.java:139-158`, `PluginDslContext.java:28-31` |
| **`!docs`/`!adrs <path> <class>`**: instantiates an importer and reads files | `DOCUMENTATION`/`DECISIONS` only | `DocumentationDslContext.java:53-55`, `DecisionsDslContext.java:53-55` |
| **`!components … matcher <class>`**: `newInstance(String)` | `COMPONENT_FINDER` only | `ComponentFinderStrategyParser.java:126-131` |
| `!impliedRelationships <class>` (non-built-in) | `PLUGINS` | `ImpliedRelationshipsParser.java:44-47` |
| `!script`: JSR-223 `eval`, full JVM rights. No engine is bundled (test scope in the module POM) | `SCRIPTS` | `ScriptDslContext.java:46-47,61-62` |
| Inline PlantUML/Mermaid/Kroki server fetch | **no key**: allowlist only | `ImageViewContentParser.java:53-55` |
| `theme <url>`/`theme default`: stored, not fetched | **no key** | `ThemeParser.java:12-13,42-50` |

- Other gates throw `FeatureNotEnabledException`, wrapped as `StructurizrDslParserException`.
- `parse(File)` itself does not need `FILE_SYSTEM` (`StructurizrDslParser.java:178-192`).
- `setRestricted` is `@Deprecated` and leaves `INCLUDE`, `HTTP` and `HTTPS` on (`:131-142`).
- No build-time code (no annotation processor or plugin), and no process execution in the code read.
- The docs describe no restricted mode for library callers ([docs](https://docs.structurizr.com/dsl/parser)).

## 8. Direct dependencies as published
All compile scope, at 6.2.4 ([dsl POM](https://repo1.maven.org/maven2/com/structurizr/structurizr-dsl/6.2.4/structurizr-dsl-6.2.4.pom)). All six com.structurizr modules are covered by this review:
- **`structurizr-client`:** the HttpClient wrapper and Structurizr service clients, which the parser does not call. Depends on jackson-databind 2.22.3, httpclient5 5.6.4, jaxb-api 2.4.0-b180830.0359 and core.
- **`structurizr-import`:** diagram and image importers; network only through the allowlist.
- **`structurizr-export`:** exporters, unused here.
- **`structurizr-component`:** the component finder, reached only by `!components`. Depends on bcel 6.12.0 and javaparser-symbol-solver-core 3.28.2.
- **`structurizr-core`:** the model. Depends on jackson-annotations 2.22, jsr305 3.0.2 and commons-logging 1.4.0.
- **POM-only metadata, with entries:** the parent `com.structurizr:structurizr:6.2.4`, and `org.junit:junit-bom:5.11.0`, which the parent imports. The BOM holds only dependencyManagement of `org.junit.*`, with no repositories or plugins ([BOM](https://repo1.maven.org/maven2/org/junit/junit-bom/5.11.0/junit-bom-5.11.0.pom)).

**Transitives** ([deps.dev graph](https://api.deps.dev/v3/systems/maven/packages/com.structurizr%3Astructurizr-dsl/versions/6.2.4:dependencies)). Each needs its own review; flagged ones in bold:
- **jackson-databind 2.22.3** (deserialization history), jackson-core 2.22.3, jackson-annotations 2.22
- **httpclient5 5.6.4** (network), httpcore5/-h2 5.4.3
- **jaxb-api 2.4.0-b180830.0359** (a 2018 beta build), javax.activation-api 1.2.0
- bcel 6.12.0, javaparser-core/-symbol-solver-core 3.28.2, **javassist 3.31.0-GA** (bytecode generation)
- **guava 33.6.0-jre** (section 5 advisory) with failureaccess, listenablefuture, error_prone_annotations, j2objc-annotations, checker-qual and **jspecify 1.0.0** (must lose to the reviewed 1.0.1)
- commons-lang3 3.20.0, commons-io 2.21.0, commons-logging 1.4.0, jsr305 3.0.2, slf4j-api 1.7.36

## 9. Alternatives considered
- **Import Structurizr JSON through core only.** No DSL feature surface, but it drops the DSL.
- **Write our own parser.** The design rejects it.
- **Exclude `structurizr-component`** (bcel, javaparser, guava, javassist). Untested; only `!components` reaches it.

## 10. Conditions and residual risk
Conditions: see the frontmatter.

Residual risk:
1. **The single maintainer** publishes off-CI with no provenance, so a compromised account could ship a poisoned release. Version pinning plus re-review is the control.
2. **A future version could add a feature key, and it would default to on.** Re-check `Features.java` on every upgrade.
3. **The transitive tree is large and almost all dead code here.**
4. **Redirects would bypass the allowlist** if a URL were ever allowed.

## 11. Not verified
- **The JARs:** not downloaded, hashed or inspected; `.asc` signatures not checked (gpg not run, signing key unknown); that they were built from the tag (the build is not reproducible); whether the `.sha256` files are publisher- or repository-generated.
- **Commit and tag signature status:** api.github.com returned 403 (rate limit), and the HTML showed no badge.
- **Per-version advisories:** deps.dev was checked only for dsl and core. client, import, export and component rely on the name search.
- **Behaviour:** read, not executed. That each disabled key throws, and how redirects behave, is untested. Line numbers were counted by hand.
- **Transitive artifacts' code:** not reviewed.
