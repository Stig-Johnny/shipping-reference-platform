---
component: "org.pitest:pitest-junit5-plugin"
kind: maven
version: "1.2.3"
verdict: APPROVE-WITH-CONDITIONS
scope: "Tool classpath of the :core PIT JavaExec task only (configuration `pitest`), alongside PIT 1.30.2 and junit-platform-launcher 1.14.4; same hosts as the PIT review"
conditions: ["Only on the :core `pitest` configuration, with PIT 1.30.2 and every condition of the PIT 1.30.2 review", "Its provided dependencies come only from reviewed artifacts: org.pitest:pitest 1.30.2 and org.junit.platform:junit-platform-launcher 1.14.4 (matching the Jupiter 5.14.4 engine under test)", "The pit-core job keeps PIT's CLI default failWhenNoMutations=true and also fails unless the report shows tests were run and mutants killed, because compatibility with JUnit Platform 1.14.4 is not documented upstream", "The resolved JAR SHA-256 equals d3e2fcc8db5fd52c0c4ac2fe0d937e64da3df39b657093a1581b5fd463f9be64; any other version or hash is a new review", "Human code-owner acceptance of this record before first use (v01-design §3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: org.pitest:pitest-junit5-plugin 1.2.3

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** A 12,949-byte adapter that lets PIT run JUnit Platform tests through the public Launcher
API; no known advisories, no network, process or file code in the classes read. Weak points: the repository is
effectively unmaintained (Scorecard "Maintained 0"), 1.2.3 has no GitHub release, its release workflow has never run
(so it was published another way), and it was built against older PIT/Platform versions than ours.

## 2. What it is and why it is needed
PIT needs this plugin to find and run JUnit 5 tests; the design asks for PIT "with matching JUnit launcher" (repository
design note `v01-design.md`, §4). The README: 1.2.3 "Requires pitest 1.19.4 or above" and the plugin "must be placed on
the classpath of the pitest tool" ([repo](https://github.com/pitest/pitest-junit5-plugin)).

## 3. Provenance
- **Publisher:** the `pitest` GitHub org (`github.com/pitest/pitest-junit5-plugin`, Apache-2.0), main author `hcoles`
  ([repo](https://api.github.com/repos/pitest/pitest-junit5-plugin)). Namespace `org.pitest`, no parent POM
  ([POM](https://repo1.maven.org/maven2/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.pom)).
- **Release:** Central files 2025-05-20 15:48 ([listing](https://repo1.maven.org/maven2/org/pitest/pitest-junit5-plugin/1.2.3/));
  tag `1.2.3` → `e05e0f1126e808a0c9f04b39c511916baa211e74` (merge of PR #111) ([tags](https://api.github.com/repos/pitest/pitest-junit5-plugin/tags));
  **no GitHub release** (only 1.2.2 has one) ([releases](https://api.github.com/repos/pitest/pitest-junit5-plugin/releases)).
- **Build:** `release.yml` (push to `release/**`, GPG from secrets, `mvn -Prelease deploy`, actions pinned by tag) exists
  ([release.yml](https://raw.githubusercontent.com/pitest/pitest-junit5-plugin/1.2.3/.github/workflows/release.yml)), but its
  Actions page shows "0 workflow runs" ([runs](https://github.com/pitest/pitest-junit5-plugin/actions/workflows/release.yml)):
  1.2.3 was published outside it (inference: locally). `.asc` present; no provenance
  ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.pitest%3Apitest-junit5-plugin/versions/1.2.3)).
- **SHA-256:** `d3e2fcc8…f9be64` from [`.jar.sha256`](https://repo1.maven.org/maven2/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar.sha256),
  read twice independently (match).

## 4. Maintenance and adoption
- Last push 2025-05-22; 79 stars; contributors `hcoles` 105, `tobiasstadler` 34
  ([contributors](https://api.github.com/repos/pitest/pitest-junit5-plugin/contributors)).
- Scorecard **3.1** (2026-11-02): Maintained 0 ("0 commit(s) … in the last 90 days"), Code-Review 3, Pinned-Dependencies 0,
  Token-Permissions 0, Security-Policy 0 ([scorecard](https://api.securityscorecards.dev/projects/github.com/pitest/pitest-junit5-plugin)).
- Diff 1.2.2 → 1.2.3 (9 commits, 7 files; [patch](https://github.com/pitest/pitest-junit5-plugin/compare/1.2.2...1.2.3.patch)):
  empty tag-group filtering, discovery `selectClass(name)` for Quarkus, nexus-staging → `central-publishing-maven-plugin`
  0.7.0; no dependency changes.

## 5. Known vulnerabilities at this version
OSV `POST /v1/query` was not callable (GET-only fetch, no shell network); used deps.dev `advisoryKeys`, OSV name search
and GitHub advisories. **None found on 2026-11-06**: deps.dev `[]`; OSV "pitest" no results
([osv](https://osv.dev/list?ecosystem=Maven&q=pitest)).

## 6. License
Apache-2.0 ([POM](https://repo1.maven.org/maven2/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.pom)).

## 7. Execution privileges (build/test phase, inside PIT's JVMs)
- Placed on the PIT tool classpath (README) and loaded through PIT's own ServiceLoader as a `TestPluginFactory`; in child
  JVMs `ClientPluginServices` loads it from the test-side classpath (see the PIT review §7).
- `JUnit5TestUnitFinder` calls `LauncherFactory.create()` and `launcher.execute(...)` with `selectClass` and `TagFilter`
  — tests **run during discovery** ([JUnit5TestUnitFinder.java:60,89-95](https://raw.githubusercontent.com/pitest/pitest-junit5-plugin/1.2.3/src/main/java/org/pitest/junit5/JUnit5TestUnitFinder.java)).
  `LauncherFactory.create()` uses default ServiceLoader auto-registration (JUnit review §7).
- `JUnit5TestPluginFactory` sets the JVM-wide system property `junit.jupiter.execution.parallel.enabled=false`
  ([JUnit5TestPluginFactory.java:34](https://raw.githubusercontent.com/pitest/pitest-junit5-plugin/1.2.3/src/main/java/org/pitest/junit5/JUnit5TestPluginFactory.java)).
- No network, process or file I/O in those two classes.

## 8. Direct dependencies as published
- `org.pitest:pitest` 1.15.2 and `org.junit.platform:junit-platform-launcher` 1.9.2, both **`provided`** — not resolved
  by Gradle; we supply pitest 1.30.2 and launcher 1.14.4 (covered by their reviews). deps.dev lists them as direct
  because it ignores scope ([graph](https://api.deps.dev/v3/systems/maven/packages/org.pitest%3Apitest-junit5-plugin/versions/1.2.3:dependencies)).
- Test-scope only: junit-jupiter 5.9.2, assertj 3.14.0, cucumber 5.0.0, spock (not resolved).

## 9. Alternatives considered
- PIT's built-in JUnit 4 support — does not run Jupiter tests. No other PIT JUnit 5 integration from the same publisher.

## 10. Conditions and residual risk
Conditions: see frontmatter. Residual risk: an unmaintained adapter coupled to JUnit Platform internals; a future JUnit
upgrade may break it silently (hence the fail-on-no-tests condition); publication path unknown.

## 11. Not verified
- JAR not downloaded, hashed or inspected; `.asc` not checked; how and where 1.2.3 was built.
- `JUnit5Configuration` and `JUnit5TestUnit` were not read; compatibility with Platform 1.14.4 untested (nothing was run).
