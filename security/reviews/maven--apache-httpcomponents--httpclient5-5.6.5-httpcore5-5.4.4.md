---
component: "org.apache.httpcomponents.client5:httpclient5; org.apache.httpcomponents.core5:httpcore5, httpcore5-h2"
kind: maven
version: "httpclient5 5.6.5; httpcore5 5.4.4; httpcore5-h2 5.4.4"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api testRuntimeClasspath only, as locked (pulled in by com.structurizr:structurizr-client 6.2.4 and lifted to these versions by the Spring Boot 4.1.2 BOM). Not docker-java's transport, which bundles its own relocated copy"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "testRuntimeClasspath only; never on main configurations", "Approved on the premise of the Structurizr transitives review: no test calls com.structurizr.http.HttpClient.allow(...), WorkspaceApiClient or any other Structurizr API client, so no code path reaches these classes", "Preferred: exclude all three from apps/shipping-api's test classpath as the Structurizr transitives review §9 proposes, once a test shows the suite and every DSL parse pass without them; this entry then lapses", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: Apache HttpClient 5.6.5 and HttpCore 5.4.4 (test runtime, through Structurizr)

**Method.** Read on 2026-11-06 with WebFetch: Central listings and POMs, deps.dev, OSV, Scorecard, and the Structurizr
client sources at `v6.2.4`. Nothing was downloaded, built or run. **Correction to the brief:** these jars are not docker-java's transport.
docker-java's `transport-zerodep` declares no httpclient5 dependency. Instead it shades a relocated httpclient5 5.5.1,
which is covered in the Testcontainers internals review
([zerodep POM](https://repo1.maven.org/maven2/com/github/docker-java/docker-java-transport-zerodep/3.7.1/docker-java-transport-zerodep-3.7.1.pom)).

## 1. Verdict
**APPROVE-WITH-CONDITIONS, test runtime only.** These are current, maintained ASF releases with no applicable advisory.
5.6.5 is past the GHSA-hjcp-jmpx-g3qm fix (5.6.3). They give the test JVM an HTTP/1.1 and HTTP/2 client. Nothing in this
app's tests needs one, so the conditions keep them unreachable. The better answer is to exclude them, as already
proposed for the library (condition 4).

## 2. What it is and why it is needed
- **How they arrive.** `structurizr-client` 6.2.4 declares httpclient5 5.6.4
  ([POM](https://repo1.maven.org/maven2/com/structurizr/structurizr-client/6.2.4/structurizr-client-6.2.4.pom)). Spring
  Boot 4.1.2's BOM manages `httpclient5.version` 5.6.5 and `httpcore5.version` 5.4.4
  ([BOM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom)).
- **Why Structurizr is there.** It is on the app's test runtime classpath through the conformance checker (`:core`,
  `v01-design.md` §2), which parses the DSL with Structurizr.
- **Need.** The app itself has no use for an HTTP client.

## 3. Provenance
- **Publisher.** Apache Software Foundation, under `org.apache.httpcomponents.client5` and `.core5`. httpclient5 5.6.5
  was published 2026-10-06; httpcore5 and httpcore5-h2 5.4.4 on 2026-09-20
  ([client dir](https://repo1.maven.org/maven2/org/apache/httpcomponents/client5/httpclient5/5.6.5/),
  [core dir](https://repo1.maven.org/maven2/org/apache/httpcomponents/core5/httpcore5/5.4.4/)).
- **Signatures and hashes.** Each has `.asc`, but **no `.sha256`**, only `.md5` and `.sha1`. The allowlist hashes are our
  first download (`verification-metadata.xml`), so they are trust-on-first-download.
- **Attestations.** None; deps.dev shows `slsaProvenances: []`
  ([httpclient5](https://api.deps.dev/v3/systems/maven/packages/org.apache.httpcomponents.client5%3Ahttpclient5/versions/5.6.5)).
- **POM metadata.** The POMs state Apache-2.0 only in their header comment and have no `<scm>`
  ([httpclient5 POM](https://repo1.maven.org/maven2/org/apache/httpcomponents/client5/httpclient5/5.6.5/httpclient5-5.6.5.pom)).

## 4. Maintenance and adoption
- **Release state.** 5.6.5 and 5.4.4 are the newest stable releases; 5.7-beta1 and 5.5-beta4 are the betas.
- **Scorecard.**
  - httpcomponents-client 7.2 ([SC](https://api.securityscorecards.dev/projects/github.com/apache/httpcomponents-client)).
  - httpcomponents-core 7.4 ([SC](https://api.securityscorecards.dev/projects/github.com/apache/httpcomponents-core)).
  - Both score Maintained 10 and Security-Policy 10.

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06.**
- `inputs/osv.json` is empty, and deps.dev `advisoryKeys: []` for all three.
- [GHSA-hjcp-jmpx-g3qm](https://api.osv.dev/v1/vulns/GHSA-hjcp-jmpx-g3qm) (classic-client connection leak) is fixed in
  5.6.3, so it does not apply to 5.6.5.

## 6. License
Apache-2.0 (POM headers; deps.dev). Not distributed.

## 7. Execution privileges (test runtime, only if called)
- **Network.** Outbound HTTP/1.1 and HTTP/2 over TLS. It honours JVM proxy and TLS system properties, because Structurizr
  builds clients with `useSystemProperties()` and `HttpClients.createSystem()`.
- **Where Structurizr calls it.**
  - `HttpClient.get()`, only for URLs that match an `allow()` pattern. Nothing is allowed by default
    ([HttpClient](https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/structurizr-client/src/main/java/com/structurizr/http/HttpClient.java)).
  - The Structurizr cloud API clients
    ([WorkspaceApiClient](https://raw.githubusercontent.com/structurizr/structurizr/v6.2.4/structurizr-client/src/main/java/com/structurizr/api/WorkspaceApiClient.java)).
  - Condition 3 forbids both.
- **Not involved.** No process execution, native code or build-time execution.

## 8. Direct dependencies as published
- **httpclient5:** httpcore5, httpcore5-h2, slf4j-api. conscrypt, commons-compress, zstd-jni and brotli4j are optional
  and not resolved.
- **httpcore5 and httpcore5-h2:** no runtime dependencies (httpcore5-h2 depends on httpcore5).
- **POM metadata only:** httpclient5-parent, httpcore5-parent and httpcomponents-parent 14. The Structurizr review notes
  that httpclient5-parent imports opentelemetry-bom.

## 9. Alternatives considered
- **Excluding all three** (condition 4) is the better path.
- **Keeping Structurizr off the app's test classpath** would also work, if the conformance check can run in `:core`'s
  own tests instead.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:** an unused network client sits on the test classpath, and its hashes are anchored only by our own
first download.

## 11. Not verified
- **Signatures and hashes:** no `.asc` checked; no publisher SHA-256 exists.
- **Reachability:** whether the JVM verifier loads `org.apache.hc.*` while linking Structurizr's `HttpClient` (Structurizr
  review §11). Which tests touch Structurizr at all.
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in.
