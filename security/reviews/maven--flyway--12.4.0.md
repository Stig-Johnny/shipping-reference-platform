---
component: "org.flywaydb:flyway-core, org.flywaydb:flyway-database-postgresql"
kind: maven
version: "12.4.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "runtimeClasspath and testRuntimeClasspath of :example-app, only as managed by Spring Boot 4.1.2 through spring-boot-starter-flyway (migrations run at application start and in tests). Not :core, not :junit5, not as a Gradle plugin or CLI."
conditions: ["Exactly flyway-core and flyway-database-postgresql 12.4.0 through the Spring Boot 4.1.2 BOM; jars match the published SHA-256 values", "Resolved tools.jackson.core:jackson-databind and jackson-core are 3.1.7 (Boot-managed) or a later reviewed version, never the declared 3.1.1", "Migration locations contain only V<version>__<description>.sql (and R__<description>.sql if used); a test or CI check fails on any other file, including extension-less files and callback scripts", "No Redgate edition modules (flyway-proprietary, licensing modules, com.redgate.flyway), no flyway-gradle-plugin, no Flyway CLI or Docker image", "Migrations run only against the approved postgres test container or an owner-controlled database", "Candidate builds and tests run in network-restricted workers, as v01-design.md requires (backstop for undiscovered network use)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Flyway 12.4.0, as managed by Spring Boot 4.1.2 (example application only)

## 1. Verdict

**APPROVE-WITH-CONDITIONS.** Boot 4.1.2 manages 12.4.0 (2026-04-14). It is 28 releases behind upstream 13.12.1, but
deps.dev lists no advisory for either artifact. Four findings shape the conditions.
- **Processes.** The open-source core **executes script migrations** (`.sh`, `.bash`, `.py`, `.cmd`, `.bat`, `.ps1`,
  and extension-less files on non-Windows hosts), passing `FLYWAY_PASSWORD` to the child. Condition 3 limits migration
  locations to SQL.
- **Jackson.** flyway-core **declares `jackson-databind` 3.1.1, which has 15 advisories**. Boot resolves 3.1.7, which
  has none, and condition 2 makes that mandatory.
- **Network.** **No telemetry, update check or licence-server call** appears in the files read. The telemetry hook is
  null unless set, and Boot does not set it, so there is nothing to switch off; the network-restricted worker is the
  backstop.
- **Upstream review.** The source repository is a release snapshot (0/30 approved changesets), so upstream review
  cannot be observed.

## 2. What it is and why it is needed

Versioned SQL migrations for the example's PostgreSQL schema. v01-design.md §4: "Prefer Boot-managed Flyway over
upstream 13.12.1." `spring-boot-flyway` 4.1.2 depends on `flyway-core` 12.4.0
(https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-flyway/4.1.2/spring-boot-flyway-4.1.2.pom).

## 3. Provenance

- **Publisher.** Maven Central, `org.flywaydb`. `flyway-parent` 12.4.0 names developer `flyway`, organization "Redgate
  Software", and scm `https://github.com/flyway/flyway`
  (https://repo1.maven.org/maven2/org/flywaydb/flyway-parent/12.4.0/flyway-parent-12.4.0.pom). Files are dated
  2026-04-14, each with `.asc`, `.sha256` and `.sha512`.
- **Publisher SHA-256** (copied into the allowlist):
  - `flyway-core-12.4.0.jar` is `c3f93df1f3e0c9d14652adb5ade97401f2ede503859ac693c2af5c06e2797799`
    (https://repo1.maven.org/maven2/org/flywaydb/flyway-core/12.4.0/flyway-core-12.4.0.jar.sha256).
  - `flyway-database-postgresql-12.4.0.jar` is `abc075a5c3cbf37dc64b28fec7e2398fb14f31109f7ab6271854738797407b85`
    (same path pattern).
- **Build.** Signed with `maven-gpg-plugin` 3.2.8 and published with `central-publishing-maven-plugin` 0.7.0. There is
  no publishing workflow in the repository (Scorecard Packaging -1), so the build runs at Redgate, out of view.
  deps.dev shows `slsaProvenances: []`. GitHub release `flyway-12.4.0` is at
  `be256634108dba6f760dbb0604e3421f12f2c431`. History is one "Bump version" commit per release, mostly by
  `rg-buildmonkey` (https://github.com/flyway/flyway/commits/main).

## 4. Maintenance and adoption

Releases come every week or two, from 12.0.0 (2026-01-28) to 13.12.1 (11-05)
(https://repo1.maven.org/maven2/org/flywaydb/flyway-core/). The repository dates from 2012, with 10,190 stars and 1,635
forks. **Scorecard 4.8** (2026-10-26; https://api.securityscorecards.dev/projects/github.com/flyway/flyway): Maintained
and Fuzzing score 10. Code-Review ("0/30 approved changesets"), Branch-Protection, Security-Policy,
Pinned-Dependencies and Signed-Releases all score 0.

## 5. Known vulnerabilities at 12.4.0

**Method.** OSV `POST /v1/query` is unavailable here, so deps.dev v3 (which carries OSV data) and
`https://api.osv.dev/v1/vulns/<id>` were used on 2026-11-06.

- **Flyway itself.** flyway-core and flyway-database-postgresql 12.4.0 show `advisoryKeys: []`
  (https://api.deps.dev/v3/systems/maven/packages/org.flywaydb%3Aflyway-core/versions/12.4.0, and likewise for the
  module). **None found on 2026-11-06.**
- **Declared `tools.jackson.core:jackson-databind` 3.1.1:** 15 advisories, all fixed by 3.1.7. HIGH:
  GHSA-j3rv-43j4-c7qm and -rmj7-2vxq-3g9f (PolymorphicTypeValidator bypasses), -cxp5-3px4-pw24, -wv8q-qhhj-9h54 and
  -q4xh-88c3-wmh7. The other ten are MODERATE, including -hgj6-7826-r7m5 (SSRF).
- **jackson-core 3.1.1:** GHSA-7hhh-6rmp-j9qf, -p6pp-m3f8-5c89 and -r7wm-3cxj-wff9 (HIGH).
- **3.1.7, the Boot-managed version:** `[]`.

## 6. License

**Apache-2.0.** `LICENSE.txt` at the tag reads "Copyright (C) Red Gate Software Ltd 2010-2026 … Licensed under the
Apache License, Version 2.0", and deps.dev agrees. Flagged for the owner, and not legal advice: the POM's licence URL
points at `README.txt`, and Redgate's Java API page says "By downloading Flyway Community you confirm that you have read
and agree to the terms of the Redgate EULA" (https://documentation.red-gate.com/fd/api-java-277579358.html).

## 7. Execution privileges

These apply at runtime, when Boot's `FlywayMigrationInitializer` runs at context start, and in tests. **Nothing runs at
build time.**

- **Database.** Migration SQL, including DDL, runs with the datasource's privileges.
- **Processes**
  (https://github.com/flyway/flyway/tree/flyway-12.4.0/flyway-core/src/main/java/org/flywaydb/core/internal/resolver/script).
  - `ScriptMigrationResolver` claims `{"cmd","bat","ps1","py","sh","bash"}`; on non-Windows hosts,
    `isScriptFile = extension.isEmpty()`.
  - `ScriptMigrationExecutor` runs `sh`, `bash`, `python`, `cmd /c`, `powershell`/`pwsh -File`, or the file itself
    after `setExecutable(true, true)`. The child gets `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD` and placeholders
    as environment variables.
  - It is registered unless `skipDefaultResolvers` is set, and no licence gate was seen. Script callbacks also exist,
    as does a generic `ExternalProcessRunner` whose callers were not established.
- **Files.** It reads the migration locations and checks for `~/.config/Redgate/Flyway CLI/permit`. `FileUtils` can
  create `~/.config/Redgate/feature_usage_data` (caller not established). `DockerUtils` reads `/proc/1/cgroup`.
- **Network, telemetry and licensing (at the tag).**
  - **Telemetry hook.** `Flyway.java`'s `FlywayTelemetryManager` field stays null unless `setFlywayTelemetryManager` is
    called, every use is null-guarded, and the only implementation in core is the empty `NullFlywayTelemetryManager`.
  - **Licence and version checks.** `LicenseGuard.getPermit` returns the constant `OSS_PERMIT`, and `VersionPrinter`
    reads a classpath resource only.
  - **Spring Boot.** `FlywayAutoConfiguration` at v4.1.2 has no "Telemetry"; it uses `new FluentConfiguration(...)` and
    `load()`.
  - **Opt-out switch.** No `REDGATE_DISABLE_TELEMETRY`-style switch exists, and Redgate's telemetry pages return 404.
- **Plugins.** `ServiceLoader` on `org.flywaydb.core.extensibility.Plugin` lets any classpath JAR register plugins,
  including a telemetry manager.

## 8. Direct dependencies as published

- **flyway-core:** `tools.jackson.core:jackson-databind` 3.1.1 (compile; Boot resolves 3.1.7). It needs its own review,
  shared with Boot. The optional dependencies are not resolved unless declared: slf4j-api, commons-logging, log4j-api,
  jboss-vfs, aws-secretsmanager-jdbc, ojdbc11, commons-text, jackson-dataformat-toml. osgi and lombok are `provided`.
- **flyway-database-postgresql:** `flyway-core` 12.4.0 (compile) and lombok (provided). It declares no JDBC driver.

## 9. Alternatives considered

Upstream 13.12.1 made `jackson-databind` optional, a smaller surface, but Boot 4.1.2 does not manage it, and the design
aligns with Boot. Liquibase has a larger feature surface. Boot's plain `schema.sql` has no versioning.

## 10. Conditions and residual risk

The conditions are those in the frontmatter. Condition 3, with CODEOWNERS on `db/migration`, turns the script-execution
code into dead code. **Residual risk:** that code stays in the jar; the plugin SPI widens what any future classpath JAR
can do; upstream review cannot be observed; and 12.4.0 gets no fixes once Boot moves on.

## 11. Not verified

There was no full-source audit: only the files cited were read. The callers of `ExternalProcessRunner` and of the
usage-ID writer were not established, nor whether scripts inside a JAR would execute. The `.asc` signatures were not
checked, and the jars were not compared with the source. Redgate's telemetry policy for library use was not found
(404).
