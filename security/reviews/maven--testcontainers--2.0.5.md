---
component: "org.testcontainers:testcontainers, testcontainers-postgresql, testcontainers-junit-jupiter (with testcontainers-jdbc, testcontainers-database-commons)"
kind: maven
version: "2.0.5"
verdict: APPROVE-WITH-CONDITIONS
scope: "testCompileClasspath and testRuntimeClasspath of :example-app only, on disposable CI workers and local build hosts that talk to their own local Docker daemon; Ryuk disabled. Not :core, not :junit5, never main configurations."
conditions: ["Exactly these five artifacts at 2.0.5 through the Spring Boot 4.1.2 BOM, test configurations only; jars match the published SHA-256 values", "Ryuk is disabled: every Gradle Test task sets environment TESTCONTAINERS_RYUK_DISABLED=true (testcontainers/ryuk:0.14.0 is rejected)", "Only org.testcontainers.postgresql.PostgreSQLContainer with postgres pinned by digest (container-image--postgres--18.6); never the deprecated no-arg constructor; no Compose, exposeHostPorts/SSH, reuse, other images, Testcontainers Cloud/Desktop or tc.host", "Docker endpoint is the local daemon over its Unix socket; no tcp/TLS DOCKER_HOST", "CI: DOCKER_AUTH_CONFIG unset and DOCKER_CONFIG pointing to an empty directory for the test JVM, and the postgres image staged by digest, so no credential helper runs and no registry credential enters the test JVM", "Developer hosts: published test ports are not reachable from other machines (daemon default bind address 127.0.0.1, or a host firewall), checked once during a test run", "Re-review on the next Testcontainers release or any advisory against Testcontainers or docker-java 3.7.1"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Testcontainers for Java 2.0.5 (example application tests only)

## 1. Verdict

**APPROVE-WITH-CONDITIONS**, for the test classpath of `:example-app` only. 2.0.5 (2026-04-20) is the newest release,
and Boot 4.1.2 manages it. Testcontainers drives the Docker API, which is **root-equivalent on a Linux host**, so every
library on the test classpath can use it. By default it also starts Ryuk privileged, with the Docker socket mounted.
Ryuk is rejected in its own review, and condition 2 turns it off. The 17.8 MB core JAR is **shaded**. Inside it,
relocated, are docker-java-core 3.7.1 and its dependencies: jackson-databind 2.18.4 (13 advisories, 5 HIGH),
jackson-core 2.18.4 (4), BouncyCastle 1.82 (bcprov 5, 2 of them CRITICAL; bcpkix 1) and Guava 33.3.1-jre (1 HIGH). The
zerodep transport bundles httpclient5 5.5.1 (1 MODERATE). POM-based scanners cannot see these, and our build cannot
upgrade them. Under the conditions they parse only what the local daemon and local configuration give them. Hence the
test-only scope against a trusted daemon, and a re-review at the next release.

## 2. What it is and why it is needed

v01-design.md §4: "PostgreSQL 18.6 … Testcontainers 2.0.5 … Example only: real transactions, migrations and isolated
databases. … Reject H2 substitution." The Boot BOM imports `testcontainers-bom` 2.0.5, and `spring-boot-testcontainers`
4.1.2 depends on `testcontainers` 2.0.5.

## 3. Provenance

- **Publisher.** Maven Central, `org.testcontainers`. MIT; developer `rnorth`; scm
  `https://github.com/testcontainers/testcontainers-java/`. Files are dated 2026-04-20 18:00, each with `.asc`,
  `.sha256` and `.sha512`; there is no `.module`.
- **Publisher SHA-256.** The five values in `allowlist-entries.json` are copied verbatim from
  `https://repo1.maven.org/maven2/org/testcontainers/<a>/2.0.5/<a>-2.0.5.jar.sha256`. For example, `testcontainers`
  (17,789,146 bytes) is `0466f481343d5f350a91274cd7bf984308cbaf90d706247fd1cf4b1a8010c2e1`.
- **Build.** A published GitHub release triggers `release.yml` on `ubuntu-22.04` with `contents: read` and tag-pinned
  actions. It runs Gradle `build`, `publish` and `jreleaserDeploy`. JReleaser signs with GPG (`active = 'ALWAYS'`) and
  uploads to the Central Portal. There is no attestation
  (https://raw.githubusercontent.com/testcontainers/testcontainers-java/2.0.5/.github/workflows/release.yml).
- **Tag and owner.** The tag commit `5c448202ac69d073f746433d3e79f6a2bf0ec585` is GPG-verified. No owning company is
  stated on the publisher pages read (testcontainers.com, the GitHub org).

## 4. Maintenance and adoption

2.0.0 came out 2025-10-14, 2.0.4 on 2026-03-19, and 2.0.5 on 2026-04-20. There has been **no release in 6.5 months**,
although `main` has 122 commits since (https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.5). The
repository dates from 2015, with 8,760 stars and 1,950 forks. **Scorecard 4.7** (2026-10-26): Maintained 10,
Code-Review 6, Pinned-Dependencies 3, and Token-Permissions, Security-Policy and SAST all 0.

## 5. Known vulnerabilities at 2.0.5

**Method.** OSV `POST /v1/query` is unavailable here, so deps.dev v3 and `https://api.osv.dev/v1/vulns/<id>` were used
on 2026-11-06. `testcontainers`, `-postgresql` and `-junit-jupiter` 2.0.5 show `advisoryKeys: []`.

The libraries bundled in the core JAR or forced by its build are listed below. `core/build.gradle` forces
jackson-databind 2.18.4 in every configuration.

| Library | Advisories |
|---|---|
| jackson-databind 2.18.4 | HIGH: GHSA-cxp5-3px4-pw24, -j3rv-43j4-c7qm, -q4xh-88c3-wmh7, -rmj7-2vxq-3g9f, -wv8q-qhhj-9h54. Also -3pjw-73gf-8qr5, -5gvw-p9qm-jgwh, -5jmj-h7xm-6q6v, -gx83-3vf8-gh7j, -hgj6-7826-r7m5, -mhm7-754m-9p8w, -vvgp-rfg2-7rr6, -wjgm-6hv5-3cvf. All fixed by 2.18.11 |
| jackson-core 2.18.4 | HIGH: GHSA-7hhh-6rmp-j9qf, -p6pp-m3f8-5c89, -r7wm-3cxj-wff9. Also -72hv-8253-57qq |
| bcprov-jdk18on 1.82 | CRITICAL: GHSA-574f-3g2m-x479 (GOST CTR), -9pwp-9qqc-pr26 (name constraints). HIGH: -qp49-qgx5-5m26. Also -p93r-85wp-75v3 (FrodoKEM only), -c3fc-8qff-9hwx. Fixed by 1.85 |
| bcpkix-jdk18on 1.82 | GHSA-wg6q-6289-32hp, fixed in 1.84 |
| guava 33.3.1-jre | GHSA-xxph-c9ww-hj94 (HIGH, Java deserialization), fixed in 33.7.2 |
| httpclient5 5.5.1, inside zerodep | GHSA-hjcp-jmpx-g3qm (MODERATE), fixed in 5.6.3 |

**Clean (`[]`):** commons-compress 1.28.0, snakeyaml 2.6, zt-exec 1.12, trilead-ssh2 4.1.2, commons-io 2.21.0,
commons-lang3 3.19.0, jna 5.18.1, docker-java-core 3.7.1, duct-tape 1.0.8.

**Reachability** (assessed, not traced to call sites). Jackson maps Docker Engine API responses and the Docker config
file. BouncyCastle enters through docker-java's certificate handling for TLS daemons, which condition 4 excludes.
Guava's advisory needs Java deserialization of attacker data. Under the conditions, none of these is fed attacker
input.

## 6. License

**MIT** (POMs, deps.dev). The shadow JAR excludes `META-INF/LICENSE*` and `NOTICE`, which strips the bundled libraries'
notices. That doesn't matter for internal test use, but it would if the JAR were redistributed.

## 7. Execution privileges (test phase only)

- **Docker API.** The socket comes from `/var/run/docker.sock`, the Docker Desktop socket, `DOCKER_HOST`, or `tc.host`
  in `~/.testcontainers.properties` (which routes to Testcontainers Cloud/Desktop). Testcontainers creates, starts and
  removes containers, and pulls images through the daemon; the first `client()` call does only `info` and `version`.
- **Ryuk.** `RyukContainer` uses `testcontainers/ryuk:0.14.0` (a tag), `withPrivileged(...)` defaulting to `"true"`,
  and a Docker-socket bind. Only the environment variable `TESTCONTAINERS_RYUK_DISABLED` turns it off, after which a JVM
  shutdown hook does the cleanup.
- **Ports.** Ports are published with `Ports.Binding.empty()`, with no host IP (`ContainerDef.java`). Docker then
  publishes "to all host addresses (`0.0.0.0` and `[::]`)" by default and calls this "insecure by default"
  (https://github.com/docker/docs/blob/main/content/manuals/engine/network/port-publishing.md). The test database
  (`test`/`test`) is reachable from the network while tests run.
- **Processes.** `RegistryAuthLocator` reads `DOCKER_AUTH_CONFIG` or `$DOCKER_CONFIG/config.json`. For
  `credsStore`/`credHelpers` it runs `docker-credential-<name> get` through zt-exec, with the registry host on stdin,
  and takes the credentials into the test JVM. zt-exec and trilead-ssh2 (SSH host-port exposure) are bundled.
- **Files and class loading.** It reads `~/.testcontainers.properties`, the classpath `testcontainers.properties` and
  the environment. It writes `docker.client.strategy` to `~/.testcontainers.properties` and later loads that class by
  name. `ServiceLoader` loads strategies, image substitutors and command modifiers.
- **Native code and network.** JNA 5.18.1 comes through `docker-java-transport-zerodep`. No other network use was
  found in the files read.
- **PostgreSQL module.** `test`/`test`/`test`, `postgres -c fsync=off`, and a wait for the ready log line twice within
  60 s. An image is required; the deprecated `org.testcontainers.containers.PostgreSQLContainer()` defaults to
  `postgres:9.6.12`.

## 8. Direct dependencies as published

- **testcontainers:** slf4j-api 1.7.36 (Boot resolves 2.0.20), commons-compress 1.28.0, duct-tape 1.0.8,
  docker-java-api 3.7.1 (with jackson-annotations 2.20), docker-java-transport-zerodep 3.7.1 (with
  docker-java-transport and jna 5.18.1; bundles httpclient5 5.5.1), and jib-core 0.28.1 (provided).
- **Bundled under `org.testcontainers.shaded`:** awaitility 4.3.0; docker-java-core 3.7.1, with jackson 2.18.4,
  BouncyCastle 1.82, commons-io and commons-lang3 (inferred); guava 33.3.1-jre; snakeyaml 2.6; trilead-ssh2 4.1.2;
  zt-exec 1.12.
- **Module chain:** `-postgresql` → `-jdbc` → `-database-commons` → `testcontainers`. `-junit-jupiter` →
  `testcontainers`, with JUnit 6.0.3 coming from Boot.

**Transitives that deserve their own review:** docker-java-api, -transport and -transport-zerodep 3.7.1 (with the
bundled httpclient5); JNA (native code); commons-compress; duct-tape; jackson-annotations.

## 9. Alternatives considered

A CI service container plus local Compose keeps Docker-socket access out of the test JVM, but it loses per-test
isolation and `@ServiceConnection`. Embedded-PostgreSQL libraries ship native binaries, which would need another
review. H2 is rejected by the design.

## 10. Conditions and residual risk

The conditions are those in the frontmatter. **Residual risk:** Docker API access, which is root-equivalent on a Linux
host, is given to every test-classpath library. The bundled libraries with advisories stay until a new release. On
developer hosts, a pull may run a credential helper.

## 11. Not verified

The JAR was not listed, so the bundled set is inferred from the build files and the POM. Call-site reachability of
the bundled libraries. Who calls `lookupAuthConfig`, and whether a locally present image skips it. Who triggers the
lazy `alpine:3.17` mount check. Whether `DockerImageName` accepts `name:tag@sha256:…`. The `.asc` signatures, and the
project's ownership.
