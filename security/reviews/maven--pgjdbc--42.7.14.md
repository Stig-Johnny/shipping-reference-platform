---
component: "org.postgresql:postgresql"
kind: maven
version: "42.7.14"
verdict: APPROVE-WITH-CONDITIONS
scope: "runtimeClasspath and testRuntimeClasspath of :example-app only (JDBC driver for the example's PostgreSQL). Not :core, not :junit5."
conditions: ["Exactly org.postgresql:postgresql:42.7.14, resolved through the Spring Boot 4.1.2 BOM and recorded in the lockfile and Gradle verification metadata", "Connects only to the approved postgres test container or a database the owner controls, never a third-party server", "JDBC URL and properties come only from repository configuration or Testcontainers; socketFactory, sslfactory, sslhostnameverifier, sslpasswordcallback, authenticationPluginClassName, connectExecutor, sslrootcert, sslcert, sslkey, service and PGPASSFILE are not set", "com.github.waffle:waffle-jna is not added", "Re-review on any version change, on a fix for GHSA-hcjv-f5jh-r55v, or on a new advisory affecting 42.7.14"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: pgJDBC 42.7.14 (example application only)

## 1. Verdict

**APPROVE-WITH-CONDITIONS**, for the runtime and test classpaths of `:example-app`. 42.7.14 (2026-10-07) is the newest
release. It fixes two Moderate advisories affecting 42.7.4–42.7.13. One Low advisory, GHSA-hcjv-f5jh-r55v, affects
every version up to 42.7.14 and has no fix. It needs an attacker who controls a certificate path, which our
configuration never sets. Hardening against hostile servers is still unreleased, so the driver may talk only to our own
database. The conditions keep the class-loading and file-reading connection properties out of untrusted hands.

## 2. What it is and why it is needed

This is the PostgreSQL project's JDBC driver. v01-design.md §4: "PostgreSQL 18.6, pgJDBC 42.7.14 … Example only: real
transactions, migrations and isolated databases … Reject H2 substitution". Spring Boot 4.1.2 manages exactly this
version (`postgresql.version` = 42.7.14 in
https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom).

## 3. Provenance

- **Publisher.** Maven Central, `org.postgresql`. The POM's `<organization>` is the PostgreSQL Global Development Group,
  with 7 developers including davecramer and vlsi
  (https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.pom).
- **Files.** All dated 2026-10-07 21:41 (https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/). The jar is
  1,221,004 bytes, and every file has `.asc`, `.md5` and `.sha1`. There is **no `.sha256`, `.sha512` or `.module`**
  (the `.jar.sha256` URL returns 404), so the allowlist has `sha256: null`.
- **Build.** `release.yml` at tag `REL42.7.14`, identical on `release/42.7.x`
  (https://raw.githubusercontent.com/pgjdbc/pgjdbc/REL42.7.14/.github/workflows/release.yml), runs on
  `workflow_dispatch` on `ubuntu-latest`, with actions pinned by SHA. Gradle runs
  `publishAggregationToCentralPortal`, and the artifacts are PGP-signed with `secrets.RELEASE_PGP_PRIVATE_KEY`. The
  same jar and `.asc` go on the GitHub release (commit `ebdd9d0a4017e74182eabb0cc304105a0120b647`). There is **no
  provenance or attestation step**, and deps.dev shows `slsaProvenances: []`.

## 4. Maintenance and adoption

2026 releases: 42.7.9 (01-15), .10 (02-11), .11 (04-28), .12 (06-29), .13 (07-06), .14 (10-07); nothing newer
(https://repo1.maven.org/maven2/org/postgresql/postgresql/). `pgjdbc/pgjdbc` dates from 2012, with 1,746 stars and 959
forks, last pushed 2026-11-04. **Scorecard 7.5** (2026-11-04;
https://api.securityscorecards.dev/projects/github.com/pgjdbc/pgjdbc): Maintained, SAST (CodeQL), Pinned-Dependencies,
Security-Policy and Vulnerabilities score 10. Code-Review scores 2 ("1/4 approved changesets"), Branch-Protection 3
("master does not require approvers"), Signed-Releases 3 (`.asc` present, no provenance), Fuzzing 0. The project has
published 12 repository advisories since 2022 (https://github.com/pgjdbc/pgjdbc/security/advisories).

## 5. Known vulnerabilities at 42.7.14

**Method.** OSV `POST /v1/query` is unavailable here. deps.dev v3 (which carries OSV data), the osv.dev package list and
the project's advisory pages were used on 2026-11-06.

- **deps.dev `advisoryKeys: []`**
  (https://api.deps.dev/v3/systems/maven/packages/org.postgresql%3Apostgresql/versions/42.7.14). osv.dev lists 11 older
  advisories, all fixed earlier (https://osv.dev/list?ecosystem=Maven&q=org.postgresql%3Apostgresql).
- **GHSA-hcjv-f5jh-r55v** (Low, CVSS 3.3, CWE-400; repository advisory of 2026-10-15). Affected `<=42.7.14`, patched:
  none (https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-hcjv-f5jh-r55v). An `sslrootcert` or `sslcert`
  pointing at an endless source such as `/dev/zero` hangs the connection with sustained CPU use. It is not yet in the
  GitHub Advisory Database (404), deps.dev or OSV. **Not reachable under condition 3.**
- **Fixed in this release:** GHSA-rhp9-mr79-r74h / CVE-2026-107314 (`requireAuth` not enforced, 42.7.11–13) and
  GHSA-f64h-wr5q-3qf3 / CVE-2026-107315 (padding leaked earlier message bytes, 42.7.4–13).
- **Not in 42.7.14.** The CHANGELOG's "Unreleased" section has hardening against "a hostile or out of sync backend":
  message-length checks, a 32 MiB buffer cap, a limit of 64 authentication round trips, and `RowDescription` bounds
  (issue #4015; https://github.com/pgjdbc/pgjdbc/blob/master/CHANGELOG.md). No advisory is attached.

## 6. License

BSD-2-Clause (POM `<licenses>`, deps.dev, GitHub API).

## 7. Execution privileges

These apply at runtime and in tests. **Nothing runs at build time.**

- **Network.** TCP to the host in the JDBC URL. The default `sslmode` is `prefer`, and "`require`, `allow` and `prefer`
  all default to a non-validating SSL factory" (https://jdbc.postgresql.org/documentation/use/,
  https://jdbc.postgresql.org/documentation/ssl/).
- **Classes named in properties.** `socketFactory`, `sslfactory`, `sslhostnameverifier`, `sslpasswordcallback`,
  `authenticationPluginClassName` and `connectExecutor` each name a class that is loaded from the classpath. This is
  the mechanism behind GHSA-v7wg-cpwc-24m4 (2022).
- **Files.**
  - `~/.postgresql/` (`postgresql.crt`, `root.crt`, and the key `postgresql.pk8` or `.p12`; the two docs pages
    disagree), when SSL is used.
  - `~/.pgpass`, or `PGPASSFILE` / `org.postgresql.pgpassfile`, **only when no password is given**.
  - `pg_service.conf`, only with `service=`.
  - Both values are opened **as a URL first** (`new URL(resourceName).openStream()`), so a URL-valued `PGPASSFILE` would
    be fetched over the network (`jdbcurlresolver/PgPassParser.java` at the tag).
- **GSS/JAAS** runs only if the server requests GSS. SSPI needs `waffle-jna`, which is not bundled.
- **Not found** in the documentation or the files read: process execution and native code.

## 8. Direct dependencies as published

- `org.checkerframework:checker-qual:3.55.1` (runtime): nullness annotations. It needs its own small allowlist entry.
- `com.github.waffle:waffle-jna:1.9.1` (runtime, optional): Windows SSPI through JNA. It is not resolved unless
  declared, and it is **not approved**.

## 9. Alternatives considered

H2 and other embedded databases are rejected by the design. The R2DBC driver is not needed, because the demo uses
blocking JDBC transactions. There is no credible alternative JDBC driver for PostgreSQL.

## 10. Conditions and residual risk

The conditions are those in the frontmatter. Central publishes no SHA-256, so the jar's hash is computed once at
bootstrap and pinned in the Gradle verification metadata.

**Residual risk:**
- GHSA-hcjv-f5jh-r55v stays unfixed: Low, and not reachable under the conditions.
- `sslmode=prefer` does not verify certificates. That is fine on loopback, but not for a networked database.
- Code-Review scores 2, so most changes merge without a recorded approval.

## 11. Not verified

The `.asc` signatures and the signing key's identity, because no gpg was run. The jar's SHA-256 and the
reproducibility of the build, because nothing was downloaded. Source beyond `Driver.parseURL`, `PgPassParser`,
`PgServiceConfParser`, `OSUtil` and `PGEnvironment`. GHSA-hcjv's details, which come from the repository advisory page
only.
