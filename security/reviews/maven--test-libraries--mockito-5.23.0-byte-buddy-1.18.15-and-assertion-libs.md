---
component: "Test libraries of apps/shipping-api: org.mockito:mockito-core, mockito-junit-jupiter; net.bytebuddy:byte-buddy, byte-buddy-agent; org.objenesis:objenesis; org.awaitility:awaitility; org.hamcrest:hamcrest; org.skyscreamer:jsonassert; com.vaadin.external.google:android-json; com.jayway.jsonpath:json-path; net.minidev:json-smart, accessors-smart; org.ow2.asm:asm; org.xmlunit:xmlunit-core"
kind: maven
version: "mockito-* 5.23.0; byte-buddy, byte-buddy-agent 1.18.15; objenesis 3.3; awaitility 4.3.0; hamcrest 3.0; jsonassert 1.5.3; android-json 0.0.20131108.vaadin1; json-path 2.10.0; json-smart, accessors-smart 2.6.0; asm 9.7.1; xmlunit-core 2.11.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api test configurations only, as locked: testCompileClasspath and testRuntimeClasspath (objenesis on testRuntimeClasspath only). Never on compileClasspath, runtimeClasspath or any published artifact"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "All fourteen stay on test configurations only", "Mockito's inline mock maker is loaded as a -javaagent on every Gradle Test task (Mockito's documented recipe for Java 21+), so byte-buddy-agent never self-attaches, never starts a helper java process and never takes its JNA or Windows-DLL attach paths; -XX:+EnableDynamicAgentLoading and jdk.attach.allowAttachSelf are not set", "JSON and XML assertion libraries (json-path, json-smart, jsonassert, android-json, xmlunit-core) are fed only the application's own responses and repository-owned fixtures; xmlunit Transformation with external stylesheets is not used", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: test libraries (Mockito, Byte Buddy, Objenesis, Awaitility, Hamcrest, JSONassert, json-path, json-smart, ASM, XMLUnit)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, sidecars, POMs and `.module` files; deps.dev; OSV;
Scorecard; and sources and workflows at the release tags. Nothing was downloaded, built or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS for all fourteen, on test configurations only.**
- **No advisory applies** to these versions, and every published SHA-256 equals ours.
- **The material capability is Mockito plus Byte Buddy:** they redefine classes in the test JVM through a Java agent.
  By default Mockito 5 attaches that agent to its own JVM at runtime. JDK 21+ warns about that, and Mockito says a
  future JDK may forbid it. Condition 3 makes the agent explicit (`-javaagent`), which also closes byte-buddy-agent's
  helper-process and native attach paths.
- **The rest** are assertion and parsing helpers, given only our own data. Several are lightly maintained, which is
  acceptable for test-only code.

## 2. What it is and why it is needed
`spring-boot-starter-test` 4.1.2 declares mockito-core, mockito-junit-jupiter, awaitility, hamcrest, jsonassert,
json-path, json-smart, xmlunit-core and others directly
([module](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-test/4.1.2/spring-boot-starter-test-4.1.2.module)).
The chains below it:
- mockito-core → byte-buddy, byte-buddy-agent, objenesis (runtime);
- jsonassert → android-json;
- json-path → json-smart → accessors-smart → asm.

Boot's BOM lifts byte-buddy from Mockito's declared 1.17.7 to 1.18.15. That matches the earlier test-path review; the
BOM line itself was not re-read. The design chose Boot's test stack for the example (`v01-design.md` §4).

## 3. Provenance
| Artifact | Publisher → source | Central date | Publisher SHA-256 |
|---|---|---|---|
| mockito-core, -junit-jupiter 5.23.0 | Mockito team → mockito/mockito ([POM](https://repo1.maven.org/maven2/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.pom)) | 2026-03-11 | sidecars **match** |
| byte-buddy, -agent 1.18.15 | Rafael Winterhalter → raphw/byte-buddy ([parent](https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy-parent/1.18.15/byte-buddy-parent-1.18.15.pom)) | 2026-10-13 | sidecars **match**; Central also carries `.sigstore.json` bundles ([dir](https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.18.15/)) |
| objenesis 3.3 | EasyMock team → easymock/objenesis | 2022-07-31 | **none** (md5/sha1 only) |
| awaitility 4.3.0 | Johan Haleby → awaitility/awaitility | 2025-02-21 | **none** |
| hamcrest 3.0 | Hamcrest → hamcrest/JavaHamcrest | 2024-08-01 | sidecar **matches** |
| jsonassert 1.5.3 | Skyscreamer → skyscreamer/JSONassert | 2024-06-28 | **none** |
| android-json 0.0.20131108.vaadin1 | Vaadin repackaging of Android's org.json; the POM points only to developer.android.com ([POM](https://repo1.maven.org/maven2/com/vaadin/external/google/android-json/0.0.20131108.vaadin1/android-json-0.0.20131108.vaadin1.pom)) | **2014-02-28** | **none** |
| json-path 2.10.0 | Kalle Stenflo → json-path/JsonPath | 2025-10-31 | sidecar **matches** |
| json-smart, accessors-smart 2.6.0 | Uriel Chemouni → netplex/json-smart-v2 | 2025-08-02 | sidecars **match** |
| asm 9.7.1 | OW2 → gitlab.ow2.org/asm | 2024-10-06 | sidecar **matches** |
| xmlunit-core 2.11.0 | XMLUnit contributors → xmlunit/xmlunit | 2025-10-24 | sidecar **matches** |

- **Byte Buddy's release job** runs on a `[release]` commit with `contents: write` and `id-token: write`. It signs with
  GPG key `B4AC8CDC…5C46DD5` and keyless cosign, uses SHA-pinned actions and harden-runner with egress blocked
  ([main.yml](https://raw.githubusercontent.com/raphw/byte-buddy/byte-buddy-1.18.15/.github/workflows/main.yml)).
- **Mockito** publishes from GitHub Actions with in-memory PGP keys and tag-pinned actions
  ([ci.yml](https://raw.githubusercontent.com/mockito/mockito/v5.23.0/.github/workflows/ci.yml)).
- **Attestations:** none in deps.dev (`slsaProvenances: []`) for any of the fourteen.
- **Four artifacts have no publisher SHA-256**, so their hashes are trust-on-first-download.

## 4. Maintenance and adoption
| Artifact | Newest (deps.dev) | Scorecard (2026-10/11) |
|---|---|---|
| mockito 5.23.0 | 5.24.0 (2026-09-24) | 7, Code-Review 6 ([SC](https://api.securityscorecards.dev/projects/github.com/mockito/mockito)) |
| byte-buddy 1.18.15 | 1.18.17 (2026-11-03) | 7.2, Code-Review 0 ([SC](https://api.securityscorecards.dev/projects/github.com/raphw/byte-buddy)) |
| objenesis 3.3 | 3.6 (2026-08-03) | 5.1 |
| awaitility 4.3.0 / hamcrest 3.0 | both newest | 2.9 / 3.8, both Maintained 0 |
| jsonassert 1.5.3 | 2.0-rc1 (drops android-json) | 3.5, Maintained 0 |
| android-json | the only version ever (2014); no source repository found | none |
| json-path 2.10.0 | 3.0.0 (2026-02-22) | 4.4, Maintained 0 |
| json-smart / accessors-smart 2.6.0 | 2.6.1 (2026-11-03) | 4.9 |
| asm 9.7.1 | 9.11.1 (2026-10-18) | n/a (GitLab) |
| xmlunit-core 2.11.0 | 2.14.0 (2026-09-18) | 7.2 ([SC](https://api.securityscorecards.dev/projects/github.com/xmlunit/xmlunit)) |

The lagging versions are fixed by Boot 4.1.2's management; each upgrade is a new review.

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06:**
- `inputs/osv.json` is empty and deps.dev `advisoryKeys: []` for all fourteen.
- **Older advisories all end below our versions:**
  - json-smart: [GHSA-493p-pfq6-5258](https://api.osv.dev/v1/vulns/GHSA-493p-pfq6-5258) (`< 2.4.9`), GHSA-fg2v-w576-w4v3, GHSA-v528-7hrm-frqp, and [GHSA-pq2g-wx69-c263](https://api.osv.dev/v1/vulns/GHSA-pq2g-wx69-c263) (`[2.5.0, 2.5.2)`).
  - json-path: [GHSA-pfh2-hfmq-phg5](https://api.osv.dev/v1/vulns/GHSA-pfh2-hfmq-phg5) (`[2.2.0, 2.9.0)`).
  - xmlunit: [GHSA-chfm-68vv-pvw5](https://api.osv.dev/v1/vulns/GHSA-chfm-68vv-pvw5) (`< 2.10.0`).
- **Byte Buddy's repository Scorecard** reports "115 existing vulnerabilities". That figure concerns the repository's
  build manifests and was not mapped to the jar.

## 6. License
- **Mockito:** MIT.
- **hamcrest, asm:** BSD-3-Clause.
- **The other ten:** Apache-2.0 (POMs; deps.dev).

None is distributed.

## 7. Execution privileges (test phase only)
- **mockito-core and byte-buddy-agent: runtime class instrumentation.** The inline mock maker is the default since 5.0
  ([Mockito.java §0.2–0.3](https://raw.githubusercontent.com/mockito/mockito/v5.23.0/mockito-core/src/main/java/org/mockito/Mockito.java)).
  - Without `-javaagent`, byte-buddy-agent attaches to the running JVM. Providers are tried in order: `jdk.attach`, J9,
    tools.jar, then emulated attach via JNA.
  - If `jdk.attach.allowAttachSelf` is false, it **starts a separate `java` process** to attach.
  - It writes a temporary agent jar with redefine and retransform rights
    ([ByteBuddyAgent](https://raw.githubusercontent.com/raphw/byte-buddy/byte-buddy-1.18.15/byte-buddy-agent/src/main/java/net/bytebuddy/agent/ByteBuddyAgent.java)).
  - It ships Windows attach DLLs, loaded through JNA ([resources](https://github.com/raphw/byte-buddy/tree/byte-buddy-1.18.15/byte-buddy-agent/src/main/resources)).
    JNA *is* on this test classpath, through docker-java (Testcontainers internals review).
  - Condition 3 removes all of these paths.
- **byte-buddy:** generates and defines classes, with ASM repackaged inside it.
- **objenesis:** instantiates classes without constructors via `ReflectionFactory` or `Unsafe`
  ([StdInstantiatorStrategy](https://raw.githubusercontent.com/easymock/objenesis/3.3/main/src/main/java/org/objenesis/strategy/StdInstantiatorStrategy.java)).
- **accessors-smart:** generates accessor classes with ASM and defines them in a `DynamicClassLoader`
  ([BeansAccessBuilder](https://raw.githubusercontent.com/netplex/json-smart-v2/v2.6.0/accessors-smart/src/main/java/net/minidev/asm/BeansAccessBuilder.java)).
- **awaitility:** runs polls on its own executor thread
  ([InternalExecutorServiceFactory](https://raw.githubusercontent.com/awaitility/awaitility/awaitility-4.3.0/awaitility/src/main/java/org/awaitility/core/InternalExecutorServiceFactory.java)).
- **xmlunit-core:** has disabled external DTDs and entities since 2.6.0, and extension functions since 2.10.0. The
  legacy API and `Transformation` still load external resources
  ([RELEASE_NOTES](https://raw.githubusercontent.com/xmlunit/xmlunit/v2.11.0/RELEASE_NOTES.md)).
- **hamcrest, jsonassert, android-json, json-path:** in-memory matching and parsing.
- **None of them:** network access, or execution at build time.

## 8. Direct dependencies as published
- **mockito-core:** byte-buddy and byte-buddy-agent 1.17.7 (compile; resolved to 1.18.15), objenesis 3.3 (runtime).
- **mockito-junit-jupiter:** mockito-core, junit-jupiter-api (runtime; resolved through Boot's JUnit 6.0.3).
- **byte-buddy and byte-buddy-agent:** provided dependencies only (JNA 5.12.1, findbugs), none resolved.
- **awaitility:** hamcrest (declared 2.1, resolved to 3.0).
- **jsonassert:** android-json.
- **json-path:** json-smart 2.6.0 and slf4j-api (runtime).
- **json-smart:** accessors-smart. **accessors-smart:** asm 9.7.1.
- **xmlunit-core:** jakarta.xml.bind-api, in a JDK 9+ profile; jaxb-runtime is optional and not resolved.
- **None:** objenesis, hamcrest, android-json, asm.
- **POM metadata only:** byte-buddy-parent, objenesis-parent, awaitility-parent and xmlunit-parent 2.11.0.

## 9. Alternatives considered
- **Dropping Mockito** in favour of hand-written fakes would remove the agent entirely. The design defers mocking
  frameworks for `:core`; the example inherits Boot's set.
- **JSONassert 2.0** would drop android-json, but it is only a release candidate.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **A single-maintainer bytecode library** (Byte Buddy, Code-Review 0) runs with instrumentation rights in the test JVM.
  That JVM also holds Docker API access (Testcontainers review).
- **android-json** is a 2014 repackaging with no source repository.
- **Four hashes are trust-on-first-download.**

## 11. Not verified
- **Signatures:** no `.asc` or sigstore bundle was checked.
- **The jars:** none was inspected, so all three win32 DLLs being in the published agent jar is not confirmed.
- **Boot's BOM entry for byte-buddy 1.18.15:** taken from the earlier test-path review.
- **ASM's release process:** GitLab was not read.
- **The xmlunit `java9+` profile:** whether Gradle activates it.
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in.
