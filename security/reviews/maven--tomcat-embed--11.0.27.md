---
component: "org.apache.tomcat.embed:tomcat-embed-core, tomcat-embed-el, tomcat-embed-websocket"
kind: maven
version: "11.0.27"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api: compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath, as locked (pulled in by spring-boot-starter-tomcat 4.1.2; tomcat-embed-el also by spring-boot-starter-validation). Ships in the application as its HTTP server"
conditions: ["Exact version 11.0.27 and the jar SHA-256 values in allowlist-entries.json (each equals the publisher's Central .sha256 sidecar); any other version or hash is a new review", "Only the default HTTP/1.1 NIO connector that Spring Boot creates: no AJP connector, no Tomcat Native/APR or FFM OpenSSL implementation (JSSE only), and server.http2.enabled stays false; enabling any of these is a new review", "The Spring Boot review's exposure conditions hold (server.address=127.0.0.1 on developer hosts; Boot's request-size and part-count limits are not raised)", "org.apache.tomcat:tomcat-annotations-api stays off every classpath (it is not in the lockfile today); jakarta.annotation-api 3.0.0 provides the annotations", "Because deps.dev/OSV lag behind Tomcat's own advisories, the owner checks https://tomcat.apache.org/security-11.html at least monthly and at every Spring Boot patch release; any entry whose 'Affects' range includes 11.0.27 voids this approval until upgraded and re-reviewed", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: embedded Tomcat 11.0.27 (core, EL, WebSocket)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, POMs, `.sha256` sidecars and `maven-metadata.xml`;
deps.dev; OSV; Scorecard; Tomcat's security page; and `BUILDING.txt`, `build.xml`, `build.properties.release`, `KEYS` and
the 11.0.x changelog on GitHub. Nothing was downloaded, built or run. This is the most exposed component of the
application: it parses every HTTP request.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** 11.0.27 is the newest release (Central `maven-metadata.xml` says latest = 11.0.27). Its
publisher SHA-256 values equal ours, and Tomcat's security page lists nothing that affects it. **Weighing against it:**
11.0.25, 11.0.26 and 11.0.27 together fixed about 30 CVEs, including Important HTTP/2, AJP and WebSocket issues. 11.0.28
is in development with more HTTP/2 hardening. The conditions keep the attack surface at the default HTTP/1.1 JSSE
connector and replace the lagging advisory feeds with Tomcat's own page.

## 2. What it is and why it is needed
Spring Boot 4.1.2's `spring-boot-starter-webmvc` → `spring-boot-starter-tomcat` brings the servlet container that serves
the API. Hibernate Validator uses `tomcat-embed-el` for message interpolation. The design picks Boot 4.1.2 (`v01-design.md`
§4); the Boot review lists Tomcat as the second-most exposed transitive (top-level Boot review §8).

## 3. Provenance
- **Publisher.** Apache Software Foundation, Maven Central namespace `org.apache.tomcat.embed`. All three directories are
  dated 2026-10-06 18:35 and carry `.asc`, `.md5`, `.sha1`, `.sha256` and `.sha512`
  ([core](https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/11.0.27/),
  [el](https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-el/11.0.27/),
  [websocket](https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-websocket/11.0.27/)).
- **SHA-256.** All three sidecars (`<artifact>-11.0.27.jar.sha256` in the directories above) **equal our values**, so
  `allowlist-entries.json` cites the publisher's value. Tomcat's build writes only SHA-512 itself
  ([build.xml](https://raw.githubusercontent.com/apache/tomcat/11.0.27/build.xml)), so the `.sha256` probably comes from
  the Central/Nexus side. That is an inference.
- **Build and signing.** "Apache Tomcat releases are fully reproducible" from the source release with the recorded tool
  chain ([BUILDING.txt](https://raw.githubusercontent.com/apache/tomcat/11.0.27/BUILDING.txt)). The 11.0.27 tool chain
  was Ant 1.10.18 and Adoptium JDK 25.0.4.1+1 on Windows Server 2022, with release manager `markt`
  ([build.properties.release](https://raw.githubusercontent.com/apache/tomcat/11.0.27/build.properties.release)).
  Signatures are detached OpenPGP. The [KEYS](https://raw.githubusercontent.com/apache/tomcat/11.0.27/KEYS) file has
  Mark E D Thomas, fingerprint `A9C5 DF4D 22E9 9998 D987 5A51 10C0 1C5A 2F60 59E7`. Staging goes through ASF Nexus
  ([mvn.properties.default](https://raw.githubusercontent.com/apache/tomcat/11.0.27/res/maven/mvn.properties.default)).
  There is no SLSA provenance or attestation; deps.dev shows `slsaProvenances: []` and `attestations: []`
  ([core](https://api.deps.dev/v3/systems/maven/packages/org.apache.tomcat.embed%3Atomcat-embed-core/versions/11.0.27)).

## 4. Maintenance and adoption
- **Monthly releases:** 11.0.25 (2026-08-18), 11.0.26 (09-15), 11.0.27 (tagged 2026-10-06, announced 2026-10-12)
  ([security-11](https://tomcat.apache.org/security-11.html), [tags](https://github.com/apache/tomcat/tags)).
- **11.0.28 "in development"**, with HTTP/2 settings-ACK flood and backlog fixes but no CVE named yet
  ([changelog](https://raw.githubusercontent.com/apache/tomcat/11.0.x/webapps/docs/changelog.xml)).
- **Scorecard 6.0** (2026-11-02) ([scorecard](https://api.securityscorecards.dev/projects/github.com/apache/tomcat)):
  - Maintained, Security-Policy, Fuzzing and Dangerous-Workflow score 10.
  - Code-Review 1, Pinned-Dependencies 1, Binary-Artifacts 0.
  - Signed-Releases -1, because Tomcat does not use GitHub Releases.

## 5. Known vulnerabilities at 11.0.27
**None found on 2026-11-06:**
- `inputs/osv.json` (OSV, 2026-11-06) is empty for all three.
- deps.dev `advisoryKeys: []` for all three.
- Tomcat's security page: the "Fixed in 11.0.27" section lists CVE-2026-103119 (AJP), CVE-2026-101356 (HTTP/2),
  CVE-2026-101296 (JNDIRealm) and four Low issues, all with "Affects" ending at 11.0.26. Nothing lists 11.0.27
  ([security-11](https://tomcat.apache.org/security-11.html)).

**Caveat (drives condition 5).** deps.dev also shows `advisoryKeys: []` for **11.0.26**
([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.apache.tomcat.embed%3Atomcat-embed-core/versions/11.0.26)),
yet the security page lists CVEs fixed in 11.0.27 that affect 11.0.26. The newest Tomcat entries seen on OSV are from
August ([OSV list](https://osv.dev/list?ecosystem=Maven&q=org.apache.tomcat.embed%3Atomcat-embed-core)). For Tomcat, an
empty feed is weak evidence.

## 6. License
Apache-2.0 (POMs, e.g. [core POM](https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/11.0.27/tomcat-embed-core-11.0.27.pom)).

## 7. Execution privileges (runtime, and tests that start the server)
- **tomcat-embed-core is a network server.** It listens on `server.port` and parses HTTP/1.1. HTTP/2 is off by default
  (`server.http2.enabled=false`), and `server.address` has no default
  ([Boot properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)). The expectation
  is that the connector then binds all interfaces; that was not verified (section 11).
- **Boot's defaults bound the input:**
  - max-connections 8192, threads.max 200;
  - 2MB form-post and swallow size, 8KB request headers;
  - max-part-count 50, max-parameter-count 1000;
  - `mbeanregistry.enabled=false`.
- **Native code.** The jar includes `org/apache/tomcat/jni/**` and, on Java 22+, the FFM OpenSSL classes
  ([build.xml](https://raw.githubusercontent.com/apache/tomcat/11.0.27/build.xml)). They load a native OpenSSL or Tomcat
  Native only when `sslImplementationName` or a lifecycle listener selects it
  ([ssl-howto](https://tomcat.apache.org/tomcat-11.0-doc/ssl-howto.html)). Condition 2 forbids that. No native files
  are expected in the jar; this was not checked.
- **tomcat-embed-el** evaluates Jakarta EL. Hibernate Validator feeds it constraint message templates. EL over
  user-controlled templates is a code-execution class, which the Bean Validation review constrains.
- **tomcat-embed-websocket** registers its `ServletContainerInitializer` and serves nothing unless an endpoint is
  registered. The app registers none.
- **Build time:** none.

## 8. Direct dependencies as published
- **core:** `org.apache.tomcat:tomcat-annotations-api:11.0.27` (compile). It is **not in our lockfile**, presumably
  excluded by the Boot starter (not verified); condition 4 keeps it out.
- **el:** none.
- **websocket:** tomcat-embed-core 11.0.27.
- **Not in POMs:** no parent, `<organization>` or `<scm>` (POMs above).

## 9. Alternatives considered
- **Jetty, via `spring-boot-starter-jetty`:** a comparable surface and a larger review.
- **Reactor Netty:** needs WebFlux, which the design does not use.
- **Tomcat 11.0.28:** not released yet.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:**
- **The internet-facing parser of the app**, with a steady flow of CVEs (about 10 per monthly release lately). The
  approval is only as good as the speed of the next upgrade.
- **Weak GitHub code-review signal** (Code-Review 1). The releases rest on the ASF vote process and one release
  manager's key.

## 11. Not verified
- **Signatures and jar contents:** no `.asc` was checked, and no jar was listed (for native files or the panama classes).
- **The release vote thread:** not found.
- **Dates:** why deps.dev dates `tomcat-embed-el` to 2026-10-13 when Central shows 2026-10-06; and why the 11.0.27
  section is dated 2026-10-12 when its entries say "made public 20 October".
- **Boot specifics:** the bind interface when `server.address` is empty; how Boot excludes `tomcat-annotations-api`; Boot's effective connection and keep-alive timeouts;
  and that no AJP connector is ever created by default.
- **OSV:** not re-run by me; `inputs/osv.json`, deps.dev and Tomcat's page stand in.
