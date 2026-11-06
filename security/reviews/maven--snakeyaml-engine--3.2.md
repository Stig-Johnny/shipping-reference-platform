---
component: "org.snakeyaml:snakeyaml-engine"
kind: maven
version: "3.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "Build time only: compileClasspath, runtimeClasspath, testCompileClasspath and testRuntimeClasspath of the docs generator project (tools/docs), in CI and on local build hosts, parsing repository-owned YAML (Markdown frontmatter, capabilities.yaml, the OpenAPI contract). Never on a classpath of :core, :junit5 or :example-app, never in a published POM, never shipped in the site"
conditions: ["Exact version 3.2 and jar SHA-256 88d816316a19c6e7c48b04dc7374522ad436fa51ab105407e95b991f86d21167; any other version or hash is a new review", "Every load goes through one tools/docs factory that builds LoadSettings explicitly: allowDuplicateKeys(false), allowRecursiveKeys(false), allowNonScalarKeys(false), no setEnvConfig, no setTagConstructors, no custom constructor or schema class, schema CoreSchema or JsonSchema named explicitly, parseComments(false)", "Limits are set explicitly: maxAliasesForCollections 0 unless a repository input is shown to need collection aliases (then at most the default 50), and codePointLimit sized to the largest real input and never above the 3 MB default", "Only single-document APIs are used (loadFromString/loadFromReader or lowlevel Compose.composeString), never loadAll*, so the per-document size limit cannot be reset by extra documents", "Explicit tags are rejected: any node whose Node.isResolved() is false (for example !foo, !!binary, !!set, !!merge) fails the build, and merge keys (<<) are rejected", "Input files come only from code-owned repository paths; nothing is fetched, downloaded or taken from a PR comment or other external source; values rendered into HTML pass through the generator's HTML escaping", "Any exception or Error from the parser, including StackOverflowError, fails the build; tests prove that a duplicate key, an explicit tag, an over-limit alias, an over-size input and 10,000-deep nesting each fail the build", "org.yaml:snakeyaml (the 1.x/2.x library that can construct arbitrary Java objects) and jackson-dataformat-yaml are not approved by this record and must not resolve on any configuration", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: org.snakeyaml:snakeyaml-engine 3.2

**Method.** Read on 2026-11-06 through WebFetch only (GET; shell network and local hashing not permitted): the Central
listing, POM and checksum sidecars, deps.dev, Scorecard, the OSV list, the GitHub mirror and the Bitbucket API. Source
was read at release commit `8a670d3f78ebfd3c60fe6ea16f5ea94f0b2c611b`. Nothing was built or run. `SRC/` =
`https://raw.githubusercontent.com/snakeyaml/snakeyaml-engine/8a670d3f78ebfd3c60fe6ea16f5ea94f0b2c611b/src/main/java/org/snakeyaml/engine/v2/`.

## 1. Verdict
**APPROVE-WITH-CONDITIONS, build time only.** No dependencies, no known advisory, and no Java object construction from
tags (an unknown tag throws). Safe defaults are already on: duplicate keys rejected, environment substitution off,
alias and size limits. **Weak spots:** there is **no nesting-depth limit**, so deep input can end in
`StackOverflowError`; there is effectively **one maintainer**, releasing and signing off-CI with no provenance; and the
official source host (Codeberg) could not be checked. On a build-only path with repository-owned input each of these
means at worst a failed build, hence the conditions.

## 2. What it is and why it is needed
A YAML 1.2 parser for the docs tool's Markdown frontmatter, `capabilities.yaml` and OpenAPI contract. The design asks
for YAML 1.2 scalar handling, rejection of duplicate keys and custom tags, and bounded aliases, depth and input size; it
"avoids writing a YAML parser on Saturday" (`docs-site-design.md` §1).

## 3. Provenance
- **Publisher.** Namespace `org.snakeyaml`; developers `asomov` (Andrey Somov) and `maslovalex` (Alexander Maslov).
  The POM's `url`, `scm` and issue tracker all name `codeberg.org/snakeyaml/snakeyaml-engine`, scm tag
  `snakeyaml-engine-3.2` ([POM](https://repo1.maven.org/maven2/org/snakeyaml/snakeyaml-engine/3.2/snakeyaml-engine-3.2.pom)).
  [github.com/snakeyaml/snakeyaml-engine](https://github.com/snakeyaml/snakeyaml-engine) calls itself a "Mirror of
  https://bitbucket.org/snakeyaml/snakeyaml-engine" and has no tags; its head equals Bitbucket's
  ([Bitbucket API](https://bitbucket.org/!api/2.0/repositories/snakeyaml/snakeyaml-engine/commits?pagelen=5)).
- **Release commit** `8a670d3`, "[maven-release-plugin] prepare release snakeyaml-engine-3.2", 2026-10-03 09:39 UTC.
  The only later commit, `08833ea`, changes only `pom.xml` (3.2 → 3.3-SNAPSHOT)
  ([commit](https://github.com/snakeyaml/snakeyaml-engine/commit/08833ea4ed5ab50b3b70e57479d5852d6c25c5f4)).
- **Built and signed off-CI.** `maven-gpg-plugin` 3.2.8 runs in profile `release`, and `central-publishing-maven-plugin`
  0.10.0 publishes (POM). The only CI file runs `./mvnw -V -B clean install` and deploys nothing
  ([bitbucket-pipelines.yml](https://bitbucket.org/snakeyaml/snakeyaml-engine/raw/master/bitbucket-pipelines.yml)), so
  releases are built on a maintainer machine. No `project.build.outputTimestamp` (not reproducible). deps.dev:
  `slsaProvenances: []`, `attestations: []`
  ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.snakeyaml%3Asnakeyaml-engine/versions/3.2)).
- **Files**, all dated 2026-10-03 10:40: each artifact has `.asc`, `.sha256` and `.sha512`; no `.module` or
  `.sigstore.json` ([listing](https://repo1.maven.org/maven2/org/snakeyaml/snakeyaml-engine/3.2/)). The jar's `.asc` is
  an RSA/SHA-512 v4 signature, issuer fingerprint `2FC53E6B1F681184F4CCD637F5C81DE10A0B8ECC`, created 2026-10-03 ~09:40
  UTC (decoded by hand from the armour, not checked with gpg). **SHA-256**
  `88d816316a19c6e7c48b04dc7374522ad436fa51ab105407e95b991f86d21167` from
  [`snakeyaml-engine-3.2.jar.sha256`](https://repo1.maven.org/maven2/org/snakeyaml/snakeyaml-engine/3.2/snakeyaml-engine-3.2.jar.sha256);
  the jar is 309,167 bytes.

## 4. Maintenance and adoption
- **Releases:** 2.8 (2024-09-17), 2.9 (2025-01-15), 2.10 (2025-07-12), 3.0 (2025-11-12, Java 11), 3.0.1 (11-13), 3.1
  (2026-08-04), 3.1.1 (08-07), 3.2 (2026-10-03, latest;
  [maven-metadata](https://repo1.maven.org/maven2/org/snakeyaml/snakeyaml-engine/maven-metadata.xml)). The 3.2 changes
  are comment-handling and TAB fixes (#88–#99), none marked security
  ([changes.xml](https://raw.githubusercontent.com/snakeyaml/snakeyaml-engine/master/src/changes/changes.xml)).
- **Maintainers.** `asomov` has 757 of ~790 commits, and the next contributor has 8
  ([contributors](https://api.github.com/repos/snakeyaml/snakeyaml-engine/contributors)). **Bus factor 1.** The mirror
  has 19 stars; usage was not measured.
- **Scorecard:** none (API 404; deps.dev's project record has none,
  [project](https://api.deps.dev/v3/projects/github.com%2Fsnakeyaml%2Fsnakeyaml-engine)). The mirror has no
  `SECURITY.md`, and its advisories API returns `[]`.
- **Warning:** `snakeyaml.org` is no longer the project's site; it now hosts an unrelated media site. Trust nothing
  from that domain about this library.

## 5. Known vulnerabilities at this version
**None found on 2026-11-06.** deps.dev `advisoryKeys: []` (§3); the OSV list has "No results" for `snakeyaml-engine` and
for `org.snakeyaml` ([osv](https://osv.dev/list?ecosystem=Maven&q=snakeyaml-engine)); GitHub advisories `[]`. OSV
`POST /v1/query` was not run (the fetch tool is GET-only). The well-known SnakeYAML CVEs, such as CVE-2022-1471, are in
`org.yaml:snakeyaml`, a different artifact.

## 6. License
Apache-2.0 (POM; deps.dev).

## 7. Execution privileges (build time, inside the docs-tool JVM)
- **A pure parser.** It reads only the `String`, `Reader` or `InputStream` it is given (`SRC/api/Load.java`). The files
  read show no network, file, process, `ServiceLoader` or `native` use, and `module-info` has no `requires`.
- **No object construction from tags.** `StandardConstructor` registers only set, str, seq, map, the schema's tags and
  ENV. An unknown tag throws `ConstructorException("could not determine a constructor for the tag …")`
  (`SRC/constructor/BaseConstructor.java`). No `Class.forName`, `newInstance` or `java.lang.reflect` in the files read.
- **`System.getenv`** is reachable only through `ConstructEnv`, and only when `envConfig` is set. The default is
  `Optional.empty()`, so `${VAR}` stays a literal string (`SRC/constructor/StandardConstructor.java`).
- **Defaults** (`SRC/api/LoadSettingsBuilder.java`): `allowDuplicateKeys = false` (raises `DuplicateKeyException`),
  `allowRecursiveKeys = false`, `allowNonScalarKeys = false`, `maxAliasesForCollections = 50` (per document, checked in
  `Composer.composeNode`), `codePointLimit = 3 MB` per document (checked in `ScannerImpl.fetchMoreTokens`). **The
  default schema is `JsonSchema`,** although the `setSchema` javadoc says Core. `CoreSchema` resolves `<<` to merge
  (`SRC/resolver/CoreScalarResolver.java`).
- **No depth limit.** `Composer.java` contains no "depth", "nesting" or "limit", and `composeNode` and the constructor
  recurse without a counter. Deep nesting can therefore raise `StackOverflowError`, which `BaseConstructor.construct`
  does not catch. Inferred from the code, not tested.
- **No build-time code:** not an annotation processor or plugin. The jar is `Multi-Release: true` (POM bundle
  configuration); its versioned entries were not inspected.

## 8. Direct dependencies as published
**None.** All five POM dependencies are test scope: junit-jupiter-engine and -api 5.14.3, guava 33.5.0-jre, jmh-core
and jmh-generator-annprocess 1.36. No parent and no `<repositories>`. The deps.dev graph has one node
([graph](https://api.deps.dev/v3/systems/maven/packages/org.snakeyaml%3Asnakeyaml-engine/versions/3.2:dependencies)).
No transitive dependency needs review.

## 9. Alternatives considered
jackson-dataformat-yaml pulls in `org.yaml:snakeyaml`, which has a history of unsafe-constructor CVEs and would need
its own review. A hand-written parser is rejected by the design. JSON-only inputs would avoid YAML, but the
frontmatter and the contract are YAML.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter. **Residual risk:**
1. A compromised maintainer key or account could publish a poisoned 3.3. The controls are the exact-version pin, the
   SHA-256 and a re-review on upgrade.
2. Deep or pathological input can crash the docs build; condition 7 makes that fail closed. The inputs are
   repository-owned, so an attacker would need write access, which is already the bigger threat.
3. The schema javadoc contradicts the code, so condition 2 makes the schema explicit.

## 11. Not verified
- The jar and its `.asc` were not downloaded and checked, and the signing key's owner is unknown (no keyserver was
  reached). The SHA-256 rests on Central's sidecar; whether the publisher or Central computed it is unknown.
- That the jar was built from `8a670d3`: the build is not reproducible, and there is no tag on GitHub or Bitbucket.
  The Codeberg tag was not fetched (the domain was not allowed).
- Source read: `api/`, `constructor/`, `composer/`, `resolver/`, `env/`, `nodes/Node.java`, `module-info` and most of
  `ParserImpl`/`ScannerImpl`. Not read: the emitter, representer, serializer, `common/*` and the Multi-Release entries.
  The repository could not be searched for reflection (GitHub code search needs sign-in).
- Behaviour (the exceptions, the stack-overflow depth) was not executed. OSV POST was not run.
