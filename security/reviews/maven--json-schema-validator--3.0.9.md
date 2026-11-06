---
component: "com.networknt:json-schema-validator:3.0.9"
kind: maven
version: "3.0.9"
verdict: APPROVE-WITH-CONDITIONS
scope: "Test classpath of apps/shipping-api only (`testImplementation`), for HTTP contract tests that validate response bodies against contracts/shipping.openapi.yaml. Never on a runtime classpath."
conditions: ["Exactly 3.0.9 as `testImplementation` of apps/shipping-api, locked and SHA-256 verified", "Remote retrieval stays off: never call `SchemaLoader.Builder.fetchRemoteResources()`, and set an `allow` predicate that accepts only `classpath:` IRIs so anything else fails closed", "The contract is read from the test classpath (`classpath:` IRI) with `Dialects.getOpenApi31()` and JSON-pointer sub-schemas (`#/components/schemas/...`); any spec.openapis.org meta-schema that turns out to be needed is vendored into test resources, reviewed, and mapped with `schemaIdResolvers`", "The optional joni and GraalJS regex artifacts stay off the classpath (JDK regex)", "Jackson on `testRuntimeClasspath` is constrained to Boot 4.1.2's managed 3.1.7 (jackson-core, jackson-databind, jackson-dataformat-yaml) so tests run production's Jackson; if 3.1.8 is kept instead, those artifacts need their own entries", "org.snakeyaml:snakeyaml-engine 3.0.1, com.ethlo.time:itu 1.17.0 and org.slf4j:slf4j-api 2.0.20 have allowlist entries of their own before first use"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: networknt json-schema-validator 3.0.9

**Method.** On 2026-11-06 the following were read through WebFetch:
- Maven Central (metadata, listing, POM);
- at tag `3.0.9`: `SchemaLoader`, `SchemaRegistry`, `IriResourceLoader` and `ClasspathResourceLoader`, plus the README, `doc/openapi.md`, `doc/upgrading.md` and the CHANGELOG;
- the releases feed and issue #1277;
- the Jackson BOMs;
- deps.dev, OSV and Scorecard.

Nothing was downloaded or run. `api.github.com` refused the fetches (HTTP 403).

## 1. Verdict
**APPROVE-WITH-CONDITIONS, test classpath only.** It is the current release; no advisory is known for it or its dependencies; and the schemas it reads are our own contract. Weighing against it:
- **Scorecard 4.5.**
- **Releases are built off CI.** No publishing workflow was detected.
- **Very new.** It was published 2026-11-03, three days before this review.
- **The docs contradict the code on network fetching.** The code at the tag does *not* fetch remote schemas by default; the README and `openapi.md` say it does. Conditions 2 and 3 make the safe behaviour explicit, so we never depend on either.

3.0.9 is also the right line for us. It moved from Jackson 3.2.4 back to the 3.1.x line that Boot 4.1.2 manages ([commit 558cc18](https://github.com/networknt/json-schema-validator/commit/558cc1840ac6b57e424ca5a3123b47586fdb0513); [#1277](https://github.com/networknt/json-schema-validator/issues/1277)).

## 2. What it is and why it is needed
It is a JSON Schema validator (drafts 4 to 2020-12) with OpenAPI 3.0 and 3.1 dialects ([README](https://raw.githubusercontent.com/networknt/json-schema-validator/3.0.9/README.md)). The blueprint (§4, §7) requires "backend HTTP conformance tests" so the OpenAPI 3.1 contract and the API cannot drift. It validates a response against a sub-schema by JSON pointer, e.g. `SchemaLocation.of("classpath:…yaml#/components/schemas/PetRequest")` under `Dialects.getOpenApi31()` ([openapi.md](https://raw.githubusercontent.com/networknt/json-schema-validator/3.0.9/doc/openapi.md)).

**Is Jackson's YAML module needed? Yes, on the test classpath.**
- The contract is YAML (`contracts/shipping.openapi.yaml`), and the library reads YAML schemas through `tools.jackson.dataformat:jackson-dataformat-yaml`, which 3.0.9 declares as a compile, non-optional dependency at 3.1.8 ([POM](https://repo1.maven.org/maven2/com/networknt/json-schema-validator/3.0.9/json-schema-validator-3.0.9.pom)).
- Boot 4.1.2 imports `tools.jackson:jackson-bom` 3.1.7, which manages that module at 3.1.7 ([Boot BOM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom), [jackson-bom](https://repo1.maven.org/maven2/tools/jackson/jackson-bom/3.1.7/jackson-bom-3.1.7.pom)).
- The module brings `org.snakeyaml:snakeyaml-engine` 3.0.1 ([POM](https://repo1.maven.org/maven2/tools/jackson/dataformat/jackson-dataformat-yaml/3.1.7/jackson-dataformat-yaml-3.1.7.pom)). Neither BOM manages snakeyaml-engine, so it needs its own review.
- Production never reads the contract.

## 3. Provenance
- **Publisher.** Steve Hu (`stevehu`) is the POM developer; the source is github.com/networknt/json-schema-validator; the namespace is `com.networknt`.
- **Release.** 3.0.9 was released by stevehu at 2026-11-03T20:11:09Z from commit `75c95ecaec45a4a8b2b3954586dbd99f8289da25`, which GitHub shows as verified with its own signing key ([release](https://github.com/networknt/json-schema-validator/releases/tag/3.0.9)).
- **Signing and publishing.** `maven-gpg-plugin` 3.2.8 and `central-publishing-maven-plugin` 0.12.0 run in profile `release-sign-artifacts` (`performRelease=true`), with `autoPublish` ([POM](https://repo1.maven.org/maven2/com/networknt/json-schema-validator/3.0.9/json-schema-validator-3.0.9.pom)). Scorecard finds "no GitHub/GitLab publishing workflow", so releases appear to be built on a maintainer's machine; this is an inference.
- **Moved tags.** The 2.0.3, 2.0.4, 2.0.5 and 2.0.7 releases carry the note "this tag previously pointed to a 3.x commit on master" ([atom](https://github.com/networknt/json-schema-validator/releases.atom)). Git tags here have been moved, so our pin is the Central artifact and its hash, never a tag.
- **Central files.** `.jar`, `.pom`, `-sources` and `-javadoc`, each with `.asc`, `.sha256` and `.sha512`, dated 2026-11-03 20:15 ([listing](https://repo1.maven.org/maven2/com/networknt/json-schema-validator/3.0.9/)).
- **SHA-256**, from the publisher's sidecar: `6a2aab21c6b64f4ff8acc63a0745d7103e2248b7385ca8cd1a95bccd0e7fd61d` ([.sha256](https://repo1.maven.org/maven2/com/networknt/json-schema-validator/3.0.9/json-schema-validator-3.0.9.jar.sha256)).
- **No SLSA provenance or attestations** ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/com.networknt%3Ajson-schema-validator/versions/3.0.9)).

## 4. Maintenance and adoption
- **Two release lines** ([atom](https://github.com/networknt/json-schema-validator/releases.atom)): 3.0.x (Java 17, Jackson 3) and 2.0.x (Java 8, Jackson 2). Recent releases: 3.0.6 (2026-07-07), 3.0.7 (08-20), 3.0.8 (09-30), 2.0.8 (10-02), 3.0.9 (11-03).
- **Scorecard 4.5** (2026-11-02; [scorecard](https://api.securityscorecards.dev/projects/github.com/networknt/json-schema-validator)):
  - 10: Maintained, Dangerous-Workflow, Binary-Artifacts, License.
  - **0:** Token-Permissions, Pinned-Dependencies, Security-Policy, SAST, Fuzzing. **Code-Review 2.**
- **Usage.** 1,088 stars and 362 forks ([deps.dev](https://api.deps.dev/v3/projects/github.com%2Fnetworknt%2Fjson-schema-validator)). One primary maintainer, with other contributors listed in the README.
- **Behaviour changes in 3.0.9** ([CHANGELOG](https://raw.githubusercontent.com/networknt/json-schema-validator/3.0.9/CHANGELOG.md)): stricter `date-time`, and decimals preserved as `DecimalNode`, which "intentionally changes validation". 3.0.8 reworked OpenAPI `nullable`.

## 5. Known vulnerabilities at 3.0.9
**None found on 2026-11-06.**
- The osv.dev list for `com.networknt` is empty ([OSV](https://osv.dev/list?ecosystem=Maven&q=com.networknt)), and deps.dev `advisoryKeys` is `[]`.
- Also `[]`: jackson-core, jackson-databind and jackson-dataformat-yaml at both 3.1.7 and 3.1.8, snakeyaml-engine 3.0.1, itu 1.17.0, slf4j-api 2.0.20 and joni 2.2.6.
- The Jackson CVE behind 3.0.7's bump, GHSA-5gvw-p9qm-jgwh (CVE-2026-59889), affects `tools.jackson.core:jackson-databind` 3.0.0–3.1.4 and 3.2.0 ([OSV](https://api.osv.dev/v1/vulns/GHSA-5gvw-p9qm-jgwh)). 3.1.7 and 3.1.8 are outside that range.

## 6. License
Apache-2.0 ([POM](https://repo1.maven.org/maven2/com/networknt/json-schema-validator/3.0.9/json-schema-validator-3.0.9.pom), deps.dev). itu is Apache-2.0 ([POM](https://repo1.maven.org/maven2/com/ethlo/time/itu/1.17.0/itu-1.17.0.pom)); slf4j-api is MIT.

## 7. Execution privileges
- **Phase: test only.** It parses YAML and JSON schemas and instances with Jackson, and evaluates `pattern` with JDK regex by default. The README warns it "does not validate if a given regular expression is susceptable to denial of service (ReDoS)" and "assumes that the schemas being loaded are trusted". Ours are.
- **Network: off by default, in code.**
  - The `SchemaLoader` javadoc says it "does not fetch remote resources" unless `fetchRemoteResources` is set, and the default loader list is empty ([SchemaLoader.java](https://raw.githubusercontent.com/networknt/json-schema-validator/3.0.9/src/main/java/com/networknt/schema/resource/SchemaLoader.java)).
  - When enabled, `IriResourceLoader` uses `URL.openConnection()` with no timeouts. It follows up to 5 http/https redirects, and redirect targets are **not** re-checked against `allow`/`block` ([IriResourceLoader.java](https://raw.githubusercontent.com/networknt/json-schema-validator/3.0.9/src/main/java/com/networknt/schema/resource/IriResourceLoader.java)).
  - Contradicting the code: the README ("by default will load schemas … over the internet") and `openapi.md` (meta-schemas otherwise "loaded over the internet").
  - `upgrading.md` says remote loading has been opt-in since 2.0.0.
- **File system.** `classpath:` and `resource:` IRIs read through the class loader ([ClasspathResourceLoader.java](https://raw.githubusercontent.com/networknt/json-schema-validator/3.0.9/src/main/java/com/networknt/schema/resource/ClasspathResourceLoader.java)).
- **Bundled meta-schemas.** Only json-schema.org meta-schemas are mapped to the classpath. OpenAPI meta-schema documents "are not bundled".
- **Processes and reflection.** No process execution was seen in the files read. Reflection is limited to Jackson databind.

## 8. Direct dependencies as published

| Dependency | Scope | What it is |
|---|---|---|
| tools.jackson.core:jackson-databind 3.1.8 | compile | JSON tree model (Boot manages 3.1.7). Brings jackson-core 3.1.8, jackson-annotations 2.21 |
| tools.jackson.dataformat:jackson-dataformat-yaml 3.1.8 | compile | YAML reading. **Flag:** brings snakeyaml-engine 3.0.1 |
| com.ethlo.time:itu 1.17.0 | compile | RFC 3339 date-time parsing; no runtime dependencies. **Flag:** single-developer project |
| org.slf4j:slf4j-api 2.0.20 | compile | Logging API |
| org.jruby.joni:joni 2.2.6; org.graalvm.js:js-language, truffle-runtime, graal-sdk 25.0.1 | optional | Alternative regex engines; not resolved, and condition 4 keeps it that way |

**Gradle resolution note.** Under Gradle's native `platform()`, the expected outcome is that 3.1.8 beats Boot's 3.1.7 on `testRuntimeClasspath`. The app under test would then run a different Jackson from production. Condition 5 settles this; the lock shows the actual outcome.

## 9. Alternatives considered
- **3.0.8.** It is on Jackson 3.2.x, off Boot's 3.1 line, so it is not preferred.
- **The 2.0.x line.** It is Jackson 2 and would drag the 2.x stack onto the classpath.
- **An OpenAPI-specific request/response validator,** such as swagger-request-validator. It has a larger tree, and its 3.1 support was not checked.
- **Hand-written assertions.** They would drift from the contract.

## 10. Conditions and residual risk
**Conditions:** as in the frontmatter.

**Residual risk:**
- A three-day-old release from a single maintainer, built off CI, with weak repository hygiene (Scorecard 4.5) and tags that have been moved. Our pin is the Central jar and its hash.
- The behaviour changes in 3.0.8 and 3.0.9 (`nullable`, decimals) affect what our contract tests accept. That is a correctness risk, not a security one.

## 11. Not verified
- No jar was downloaded or hashed. The `.asc` signature was not checked. That the Central jar matches the tag was not checked.
- Whether `Dialects.getOpenApi31()` ever needs a meta-schema document when only instances are validated. With fetching off it would fail closed, which is the reason for condition 3.
- Every caller of `SchemaLoader`. Only the default path was traced.
- The workflow files and contributor counts (`api.github.com` 403).
- OSV's `POST /v1/query` was not used. The osv.dev list and deps.dev stand in.
