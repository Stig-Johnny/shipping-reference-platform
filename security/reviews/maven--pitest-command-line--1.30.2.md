---
component: "org.pitest:pitest-command-line (with org.pitest:pitest, pitest-entry and pitest-html-report 1.30.2)"
kind: maven
version: "1.30.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "Tool classpath of a local JavaExec task in :core (a dedicated resolvable configuration, `pitest` in the allowlist entries) that runs org.pitest.mutationtest.commandline.MutationCoverageReport. Only in the CI `pit-core` job on a disposable worker, or on a developer host whose firewall blocks inbound connections. Never on a compile, runtime or published classpath"
conditions: ["PIT runs only through the :core JavaExec task with main class org.pitest.mutationtest.commandline.MutationCoverageReport. The task's classpath is only the `pitest` configuration (the four org.pitest artifacts here, pitest-junit5-plugin 1.2.3, junit-platform-launcher 1.14.4 and their reviewed transitives). Code under test reaches PIT only through --classPath/--targetClasses/--sourceDirs", "PIT runs only where no other host or untrusted local process can reach the worker: the disposable CI worker for pit-core, or a developer host with inbound connections to java blocked. The parent JVM listens on all interfaces, accepts the first connection without authentication and deserializes it (section 7)", "The PIT task's environment and JVM -D/-javaagent arguments contain no secrets or tokens. Child JVMs inherit the full environment and the parent's -D and -javaagent arguments", "Options: --reportDir under :core build/; --timestampedReports=false; no --historyInputLocation/--historyOutputLocation outside build/; --jvmPath unset or the reviewed JDK 25; --jvmArgs adds no agent; --features only defaults or explicitly reviewed ones", "No other PIT plugin or feature JAR is on the tool or test classpath", "Each transitive artifact in section 8 has its own approved review before use", "The resolved JAR SHA-256 values equal those in allowlist-entries.json. Any other version or hash is a new review", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: PIT 1.30.2 (pitest-command-line, pitest-entry, pitest, pitest-html-report)

## 1. Verdict
**APPROVE-WITH-CONDITIONS.**
- **For:** built, signed and published by a SHA-pinned GitHub Actions workflow whose 1.30.2 run succeeded; no known advisories; the diff of this one-day-old release was read in full and is benign.
- **Against:** the most privileged component in the set (spawns JVMs, writes a Java agent, redefines classes, runs mutated code); its control channel is a server socket on all interfaces with no peer authentication and unfiltered Java deserialization; one maintainer, Scorecard Code-Review 1.
- The conditions confine it to unreachable workers with no secrets.

## 2. What it is and why it is needed
PIT does mutation testing of `:core`. The design runs it through "a local Gradle `JavaExec` task invoking `MutationCoverageReport` … Defer the external Gradle plugin", and requires the `pit-core` check (repository design note `v01-design.md`, sections 4–5).

## 3. Provenance
**Publisher.** Henry Coles. Namespace `org.pitest`, repo `github.com/hcoles/pitest`, Apache-2.0. The repo is owned by a user account, not an org ([repo](https://api.github.com/repos/hcoles/pitest)).

**Release.** Central files 2026-11-05 09:45 ([listing](https://repo1.maven.org/maven2/org/pitest/pitest-command-line/1.30.2/)); GitHub release 09:49:48Z by `hcoles`; tag commit `0fd2f92147db47cb30ce14a6b29e3be53707d3bc`, a merge created on GitHub.com and "signed with GitHub's verified signature" ([release](https://github.com/hcoles/pitest/releases/tag/1.30.2)). **One day old** at review.

**Build.** `release.yml` runs on push to `release/**`, imports the GPG key from secrets and runs `mvn -Prelease deploy scm:tag -DskipTests=true` with SHA-pinned actions and a restored `~/.m2` cache ([release.yml](https://raw.githubusercontent.com/hcoles/pitest/1.30.2/.github/workflows/release.yml)); the profile uses `central-publishing-maven-plugin` 0.12.0 (`autoPublish`) and `maven-gpg-plugin` 1.6 ([pom.xml](https://raw.githubusercontent.com/hcoles/pitest/1.30.2/pom.xml)). Run #136 on `release/1.30.2` at `0fd2f92` succeeded on 2026-11-05 ([runs](https://github.com/hcoles/pitest/actions/workflows/release.yml)). Signed CycloneDX SBOMs sit next to the JARs; no SLSA provenance ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.pitest%3Apitest/versions/1.30.2)).

**SHA-256.** From the `.sha256` next to each JAR, e.g. [pitest](https://repo1.maven.org/maven2/org/pitest/pitest/1.30.2/pitest-1.30.2.jar.sha256); pitest, pitest-entry and pitest-command-line read twice independently (match), pitest-html-report once; the SBOM repeats the pitest and pitest-entry values. Values in `allowlist-entries.json`.

## 4. Maintenance and adoption
- **Cadence:** 1.30.0 (2026-08-27), 1.30.1 (10-14), 1.30.2 (11-05), the newest ([releases](https://api.github.com/repos/hcoles/pitest/releases)). Repo created 2014, 1,887 stars; `hcoles` 1,300 contributions, next human 37 ([contributors](https://api.github.com/repos/hcoles/pitest/contributors)).
- **Scorecard 4.8** (2026-10-26): Code-Review 1 ("3/18 approved changesets"), Maintained 10, Pinned-Dependencies 10, Binary-Artifacts 9; Token-Permissions, Branch-Protection, Security-Policy and SAST 0 ([scorecard](https://api.securityscorecards.dev/projects/github.com/hcoles/pitest)).
- **Diff 1.30.1 → 1.30.2, read in full** (12 commits, 9 non-merge, 17 files; [patch](https://github.com/hcoles/pitest/compare/1.30.1...1.30.2.patch)): a built-in `JSpecifyNullFilter` interceptor (feature `JSPECIFY`, on by default) that drops `NULL_RETURNS` mutants in JSpecify non-null methods by reading class/`package-info`/`module-info` bytes with ASM; one new `META-INF/services` line for it; output-format listener activation in `SettingsFactory`; a test-scope `jspecify` 1.0.0. Nothing touches process launch, sockets, the agent, file writing or network; an external contributor's engine change was reverted (net zero).

## 5. Known vulnerabilities at this version
**Method.** OSV `POST /v1/query` could not be called (the fetch tool is GET-only, with no shell network access). Instead I used deps.dev `advisoryKeys`, the OSV name search and GitHub advisories.

**None found on 2026-11-06.**
- deps.dev returns `[]` for pitest, pitest-entry and pitest-command-line 1.30.2.
- The OSV search "pitest" returns no results ([osv](https://osv.dev/list?ecosystem=Maven&q=pitest)).
- The repository's advisories are `[]` ([advisories](https://api.github.com/repos/hcoles/pitest/security-advisories)).

**Transitive dependencies (section 8):** deps.dev returns `[]` for all.
- commons-lang3 3.18.0 is the version that fixes CVE-2025-48924 ([osv](https://api.osv.dev/v1/vulns/GHSA-j288-q9x7-2f5v)).
- The OSV name searches for `stringtemplate` and `antlr` return no results.

## 6. License
Apache-2.0 ([POM](https://repo1.maven.org/maven2/org/pitest/pitest-command-line/1.30.2/pitest-command-line-1.30.2.pom)). Bundled or transitive, per the [SBOM](https://repo1.maven.org/maven2/org/pitest/pitest-command-line/1.30.2/pitest-command-line-1.30.2-cyclonedx.json): ASM BSD-3-Clause, jopt-simple MIT, stringtemplate BSD, antlr 2.7.7 BSD, commons-* Apache-2.0.

## 7. Execution privileges (build and test phase: the `pit-core` job)
Sources are under https://raw.githubusercontent.com/hcoles/pitest/1.30.2/, with E = `pitest-entry/src/main/java/org/pitest/` and P = `pitest/src/main/java/org/pitest/`. Line numbers were counted from the raw files.

- **Child JVMs.** `java` from `java.home` (or `--jvmPath`) with an `@argfile` written to temp (`pitest-args-*.args`, `deleteOnExit`) holding `-classpath`, `-javaagent:<temp pitest-agent*.jar>` and user jvmArgs (E `process/Java9Process.java:87-92,117-144,160-163`). **The parent's `-javaagent` and `-D` arguments are copied to every child** (`:178-185`) and the environment is inherited, with additions only (`:61-64`).
- **Agent.** A temp JAR with `Premain-Class: HotSwapAgent`, `Can-Redefine-Classes: true`, on the child's boot classpath (E `mutationtest/tooling/JarCreatingJarFinder.java:70-103`); mutants are installed with `Instrumentation.redefineClasses` (P `boot/HotSwapAgent.java:33,46`).
- **Socket (main finding).** The parent opens `new ServerSocket(0)` (E `util/SocketFinder.java:24`); with no bind address the JDK binds the wildcard address, i.e. all interfaces (JDK `ServerSocket(int)` semantics; not tested). It accepts the **first** connection and sends it the run's arguments (E `util/SocketReadingCallable.java:28-32`); children connect to `"localhost"` (P `mutationtest/execute/MutationTestMinion.java:143`). No token, no peer check, and results are read with `ObjectInputStream.readObject()` without a filter (P `util/SafeDataInputStream.java:100-101`). Any process that reaches the port first can feed serialized objects to the parent JVM.
- **Plugins.** PIT's own `ServiceLoader` runs `Class.forName(name, true, loader)` on `META-INF/services` entries (P `util/ServiceLoader.java`): on the tool classpath, and in children on the test classpath, where `TransformationPlugin`s become class transformers (P `mutationtest/config/ClientPluginServices.java`).
- **Files.** The report directory (`timestampedReports` defaults to `false` in the CLI, E `mutationtest/config/ConfigOption.java:70`), history files only if set, temp files. **Mutated code** runs with the tests' privileges. No other network use in the files read.

## 8. Direct dependencies as published
- **`pitest-command-line`** → `pitest-entry` 1.30.2, `pitest-html-report` 1.30.2, `jopt-simple` 4.9; per its POM it also shades the last two unrelocated, so their classes may be on the classpath twice ([POM](https://repo1.maven.org/maven2/org/pitest/pitest-command-line/1.30.2/pitest-command-line-1.30.2.pom)).
- **`pitest-entry`** → `pitest`, `asm` and `asm-util` 9.11, `commons-text` 1.14.0 (shades ASM unrelocated). **`pitest`** → `asm`, `asm-commons` 9.11 (shades ASM to `org.pitest.reloc.asm`).
- **`pitest-html-report`** → `pitest-entry`, `stringtemplate` 3.2.1; shades `org.antlr:*`/`antlr:*` to `org.pitest.reloc.antlr*` ([POM](https://repo1.maven.org/maven2/org/pitest/pitest-html-report/1.30.2/pitest-html-report-1.30.2.pom)).
- **Metadata:** parent `org.pitest:pitest-parent:1.30.2` (POM-only, no repositories, test-only dependencies; entry included).
- **Transitives needing their own review:** ASM `asm`/`-commons`/`-tree`/`-util`/`-analysis` 9.11, `commons-text` 1.14.0, `commons-lang3` 3.18.0, `jopt-simple` 4.9 (2015), **`stringtemplate` 3.2.1 (2009) and `antlr` 2.7.7 (2007), long unmaintained**.

## 9. Alternatives considered
- **The `info.solidsoft.pitest` Gradle plugin.** It executes during configuration, so it widens build-time trust. The design defers it.
- **No mutation testing.** This loses the design's `pit-core` gate.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Recommendation (untested).** Pass `-Djdk.serialFilter=org.pitest.**;java.base/*;!*` to the JavaExec JVM. PIT copies `-D` arguments to children, so the filter would reach them too. Verify that PIT still runs before relying on it.

**Residual risk:** one maintainer with minimal review; a release job that skips tests and restores a cache; ancient transitives in the HTML report; the deserialization surface returns if the network condition is ever relaxed.

## 11. Not verified
- JARs not downloaded, hashed or inspected; `.asc` signatures not checked; the double-shading claims not confirmed in the JARs.
- The three merge commits were not diffed separately; the wildcard bind and the serial filter are untested.
- `CatchNewClassLoadersTransformer` and parts of the report module were not read; deps.dev was not queried individually for `pitest-html-report`.
