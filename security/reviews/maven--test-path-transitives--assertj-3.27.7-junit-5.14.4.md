---
component: "Transitive dependencies of assertj-core 3.27.7 and JUnit 5.14.4 on the test classpaths of :libs:c4-contract-kit: net.bytebuddy:byte-buddy, org.opentest4j:opentest4j, org.apiguardian:apiguardian-api"
kind: maven
version: "byte-buddy 1.18.3; opentest4j 1.3.0; apiguardian-api 1.1.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "Test classpaths of :libs:c4-contract-kit as locked: byte-buddy on testCompileClasspath and testRuntimeClasspath; opentest4j on those and on `pitest`; apiguardian-api on testCompileClasspath. Never on a main compile, runtime or published classpath"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review (Spring Boot 4.1.2 manages byte-buddy 1.18.15 for :example-app, which needs its own review)", "byte-buddy stays off main compile and runtime classpaths; net.bytebuddy:byte-buddy-agent and net.java.dev.jna:jna / jna-platform (provided-scope in its POM) are not added to any classpath without their own review", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: the test-path transitives (byte-buddy, opentest4j, apiguardian-api)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, POMs, `.sha256` sidecars and `.module` files, deps.dev,
Scorecard, OSV, and the Byte Buddy workflows and apiguardian source at their tags. Nothing was downloaded, built or run.

## 1. Verdict
**APPROVE-WITH-CONDITIONS for byte-buddy 1.18.3; APPROVE for opentest4j 1.3.0 and apiguardian-api 1.1.2.**
- All three are test-only, signed, and have publisher SHA-256 values that equal ours. None has a known advisory.
- **byte-buddy** is the one with real capability: it generates classes in the test JVM. It is also 14 patch releases
  behind (1.18.17 came out on 2026-11-03), so its conditions keep it off main classpaths and keep its agent and native
  companions out.
- **The other two** are an exception hierarchy and a single annotation.

## 2. What it is and why it is needed
- **byte-buddy:** the only compile dependency of assertj-core 3.27.7. AssertJ builds soft-assertion proxies with it
  (top-level AssertJ review §7-8).
- **opentest4j:** the assertion-failure exceptions that JUnit Platform, Jupiter and AssertJ share.
- **apiguardian-api:** the `@API` status annotation on JUnit's public API (JUnit 5.14.4 review §8).

The design chooses JUnit 5.14.4 and AssertJ 3.27.7 (`v01-design.md` §4).

## 3. Provenance
| Artifact | Publisher → source | Central date | `.asc` | Publisher SHA-256 |
|---|---|---|---|---|
| byte-buddy 1.18.3 | Rafael Winterhalter, `net.bytebuddy` → raphw/byte-buddy ([POM](https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.18.3/byte-buddy-1.18.3.pom), [parent](https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy-parent/1.18.3/byte-buddy-parent-1.18.3.pom)) | 2025-12-19 | yes | `d78396e3…d17c66` matches ([sidecar](https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.18.3/byte-buddy-1.18.3.jar.sha256)) |
| opentest4j 1.3.0 | ota4j-team (JUnit maintainers), `org.opentest4j` ([POM](https://repo1.maven.org/maven2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.pom)) | 2023-07-06 | yes | `48e2df63…742f1b` matches, in the sidecar and in the [.module](https://repo1.maven.org/maven2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.module) |
| apiguardian-api 1.1.2 | "@API Guardian Team", `org.apiguardian` ([POM](https://repo1.maven.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.pom)) | 2021-06-27 | yes | `b509448a…b70b38` matches, in the sidecar and in the [.module](https://repo1.maven.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.module) |

**Byte Buddy's release pipeline.** At tag `byte-buddy-1.18.3`, the release job in `main.yml` works like this
([main.yml](https://raw.githubusercontent.com/raphw/byte-buddy/byte-buddy-1.18.3/.github/workflows/main.yml)):
- It runs on a push to master whose commit message starts with `[release]`, with `contents: write`.
- It signs with GPG key `B4AC8CDC141AF0AE468D16921DA784CCB5C46DD5` from secrets.
- It runs `release:prepare release:perform` with `-Pchecksum-enforce`.
- **It has no provenance step**, and deps.dev shows `slsaProvenances: []` for all three artifacts
  ([byte-buddy](https://api.deps.dev/v3/systems/maven/packages/net.bytebuddy%3Abyte-buddy/versions/1.18.3)).
- Whether this job produced 1.18.3 was not checked.

## 4. Maintenance and adoption
- **byte-buddy:**
  - Scorecard 7.2 on 2026-11-02 ([scorecard](https://api.securityscorecards.dev/projects/github.com/raphw/byte-buddy)): Maintained 10 and Code-Review 0 ("2/30").
  - Binary-Artifacts 7, because of the Windows attach DLLs in the separate `byte-buddy-agent` module.
  - About monthly releases; the newest is 1.18.17 ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/net.bytebuddy%3Abyte-buddy)). Our pin is fixed by AssertJ 3.27.7's POM.
- **opentest4j:** Scorecard 7.3 on 2026-11-02; 1.3.0 is the newest release.
- **apiguardian:** Scorecard 7.4 on 2026-11-02; 1.1.2 is the newest release.
- **Both small ones** report Signed-Releases -1 ("no releases found"). Their age reflects stability: neither has
  needed a release since.

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06:**
- `inputs/osv.json`, the OSV batch query of 2026-11-06 (not re-run by me).
- deps.dev `advisoryKeys: []` for all three.
- Byte Buddy's repo-level Scorecard lists "115 existing vulnerabilities". That figure concerns the repository's HEAD
  dependency manifests, not this jar, and it was not mapped.

## 6. License
All three are Apache-2.0 (POMs above). None is distributed with any artifact of ours.

## 7. Execution privileges (test phase only)
- **byte-buddy** generates bytecode and defines classes at runtime in the test JVM. AssertJ subclasses assert classes
  and loads them into the test class loader (AssertJ review §7).
  - It bundles ASM 9.9.1, repackaged to `net.bytebuddy.jar.asm` (`version.asm` in the parent POM).
  - Its JPMS descriptor makes `net.bytebuddy.agent` and JNA optional (`static-requires`), and its POM declares JNA as
    `provided`, so neither is resolved.
  - The native attach DLLs live in `byte-buddy-agent`, which is not on our classpath (condition 2).
  - How it injects classes (`MethodHandles.Lookup`, `Unsafe`, reflection) was not audited.
  - No network or process use is known.
- **opentest4j** holds exception and value classes only.
- **apiguardian-api** is one annotation, `@API`, with `@Retention(RUNTIME)` and no executable logic
  ([API.java](https://raw.githubusercontent.com/apiguardian-team/apiguardian/r1.1.2/src/main/java/org/apiguardian/api/API.java)).
- **Build time:** none of the three runs at build time.

## 8. Direct dependencies as published
- **byte-buddy:** JNA 5.12.1, jna-platform 5.12.1, findbugs-annotations 3.0.1 and jsr305 3.0.2, all `provided`, so none is resolved here.
- **opentest4j and apiguardian-api:** none (POMs and `.module`s).
- **POM metadata only, not in the lockfile:** byte-buddy-parent 1.18.3. It has no repositories, no plugin repositories and no BOM imports.
- **Covered elsewhere:** junit-jupiter-api, -engine and -params, junit-platform-* and junit-bom 5.14.4 (JUnit 5.14.4 review); assertj-core (AssertJ review).

## 9. Alternatives considered
- **Byte Buddy could be avoided** only by not using AssertJ's soft assertions, which the design chose for readable
  evidence assertions. The JUnit-native assertions were considered (design §4).
- **opentest4j and apiguardian-api** are mandatory for JUnit.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **Runtime class generation by a single-maintainer project** with no GitHub-visible review (Code-Review 0). The jar is
  large (9.2 MB) and test-only; pinning plus SHA-256 is the control.
- **A lagging pin.** Fixes in 1.18.4–1.18.17 are not ours until AssertJ moves.

## 11. Not verified
- **The JARs:** none was downloaded or inspected (e.g. for native files in byte-buddy), and no `.asc` was checked.
- **Byte Buddy on JDK 25:** whether 1.18.3 supports JDK 25 class files. An incompatibility would fail tests, so it
  fails closed.
- **Byte Buddy's class-injection strategies:** not audited.
- **The 1.18.3 release run:** that the `main.yml` release job actually produced it.
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in.
