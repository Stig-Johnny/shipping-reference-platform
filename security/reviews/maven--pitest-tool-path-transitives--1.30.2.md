---
component: "Transitive dependencies of PIT 1.30.2 (org.pitest:pitest-command-line and its modules) on the :libs:c4-contract-kit `pitest` tool classpath"
kind: maven
version: "Exact per artifact, as locked on 2026-11-06 (10 coordinates, section 8)"
verdict: APPROVE-WITH-CONDITIONS
scope: "Configuration `pitest` of :libs:c4-contract-kit only: the classpath of the local JavaExec task that runs MutationCoverageReport, in the CI pit-core job or on a developer host as the PIT 1.30.2 review allows. Never on a compile, runtime, test or published classpath"
conditions: ["Exact versions and jar SHA-256 values as in allowlist-entries.json; any other version or hash is a new review", "Only on the `pitest` configuration; never on compileClasspath, runtimeClasspath, test classpaths, the processor path or in a published POM", "Every condition of the PIT 1.30.2 review holds (disposable or inbound-firewalled host, no secrets in the task's environment or JVM arguments, report options under build/)", "In the PIT slice (v01-design section 6, slice 4), excluding antlr:antlr, org.antlr:stringtemplate and net.sf.jopt-simple:jopt-simple from `pitest` is attempted with the section 9 test. If it passes, they are excluded and these three entries retired; if not, the failure is recorded in this file", "A human code owner accepts this record before first use (v01-design section 3)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Review: the PIT 1.30.2 tool-path transitives (10 artifacts)

**Method.** Read on 2026-11-06 with WebFetch: Central listings, POMs and `.sha256` sidecars, PIT's published POMs and
CycloneDX SBOMs, deps.dev, Scorecard, OSV and GitHub pages. Nothing was downloaded, built or run. URL prefixes:
`MC/` = `https://repo1.maven.org/maven2/`, `DD/` = `https://api.deps.dev/v3/systems/maven/packages/`.

## 1. Verdict
**APPROVE-WITH-CONDITIONS, on the `pitest` tool classpath only.** ASM 9.11 and the two Commons libraries are current and
well maintained. The other three are museum pieces: antlr 2.7.7 (January 2007), StringTemplate 3.2.1 (September 2009) and
jopt-simple 4.9 (June 2015). antlr and StringTemplate are **not even PGP-signed** on Central. All three appear to be
**duplicates of classes PIT already bundles**:
- pitest-html-report shades antlr and StringTemplate under `org.pitest.reloc.antlr*`.
- pitest-command-line shades jopt-simple unrelocated (section 8).

So the likely right answer is to exclude them (policy: "ask whether the dependency is needed at all"). That is condition
4, with the test in section 9. They run only in the PIT JVM, which the PIT review already confines to an unreachable,
secret-free worker. That is why the approval holds until the exclusion test has been tried.

## 2. What it is and why it is needed
PIT is the mutation tool behind the design's `pit-core` check (`v01-design.md` §4-5). These artifacts are what its four
modules declare:
- **ASM:** reads and rewrites bytecode to create mutants.
- **commons-text and commons-lang3:** utility code for pitest-entry.
- **StringTemplate and antlr:** render the HTML report.
- **jopt-simple:** parses the command line.

## 3. Provenance
| Artifact | Publisher → source | Central date | `.asc` | SHA-256 cross-check |
|---|---|---|---|---|
| asm, asm-analysis, asm-commons, asm-tree, asm-util 9.11 | OW2 (Bruneton, Kuleshov, Forax), `org.ow2.asm` → gitlab.ow2.org/asm/asm ([POM](https://repo1.maven.org/maven2/org/ow2/asm/asm/9.11/asm-9.11.pom)) | 2026-10-04 | yes | publisher `.jar.sha256` match, all five |
| commons-lang3 3.18.0; commons-text 1.14.0 | Apache Software Foundation, `org.apache.commons` → gitbox commons-lang / commons-text | 2025-07-06; 2025-07-20 | yes | no publisher SHA-256 |
| jopt-simple 4.9 | Paul Holser, `net.sf.jopt-simple`. The POM's SCM is github.com/pholser/jopt-simple ([POM](https://repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.pom)) | 2015-06-27 | yes | no publisher SHA-256 |
| stringtemplate 3.2.1 | Terence Parr and Jim Idle, `org.antlr`. The SCM is a defunct fisheye URL ([POM](https://repo1.maven.org/maven2/org/antlr/stringtemplate/3.2.1/stringtemplate-3.2.1.pom)) | 2009-09-22 | **no** | no publisher SHA-256 |
| antlr 2.7.7 | `antlr` group. The POM names no developer or SCM ([POM](https://repo1.maven.org/maven2/antlr/antlr/2.7.7/antlr-2.7.7.pom)) | 2007-01-13 | **no** | no publisher SHA-256 |

**A second record of the hashes.** For the five artifacts without a publisher SHA-256, our pinned value equals the
SHA-256 that PIT's own 1.30.2 build recorded in its CycloneDX SBOMs:
- [html-report SBOM](https://repo1.maven.org/maven2/org/pitest/pitest-html-report/1.30.2/pitest-html-report-1.30.2-cyclonedx.json)
- [command-line SBOM](https://repo1.maven.org/maven2/org/pitest/pitest-command-line/1.30.2/pitest-command-line-1.30.2-cyclonedx.json)

That is an independent second record, not an upstream checksum; the SBOMs' signatures were not checked. No artifact
has SLSA provenance on deps.dev.

## 4. Maintenance and adoption
- **ASM:** 9.11 is three weeks old, and **9.11.1 followed on 2026-10-18** ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.ow2.asm%3Aasm)); its notes were not read (asm.ow2.io not reachable). No Scorecard on GitLab.
- **Commons:** Scorecard 8.4 for both commons-lang and commons-text on 2026-11-03 ([lang](https://api.securityscorecards.dev/projects/github.com/apache/commons-lang)). Newer: lang3 3.21.0, text 1.15.0.
- **jopt-simple:** newest stable 5.0.4 (2017), newest tag 6.0-alpha-3 (2018); commits until 2025-04-26 but no release ([tags](https://github.com/jopt-simple/jopt-simple/tags)).
- **StringTemplate 3:** the README is still headed "StringTemplate 3.2.1 / September 22, 2009", and the last commit is a 2020 dependabot merge ([repo](https://github.com/antlr/stringtemplate3)). ST 4.0.2 (2011) supersedes it ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/org.antlr%3Astringtemplate)).
- **ANTLR 2:** 2.7.7 is the last 2.x release. No official end-of-life statement was found, but nothing has been released in 19 years.

## 5. Known vulnerabilities at these versions
**None found on 2026-11-06** for all ten: `inputs/osv.json` (OSV batch query of 2026-11-06, not re-run by me), deps.dev
`advisoryKeys: []` on each version page, and no results from the OSV name search for "antlr" ([osv](https://osv.dev/list?ecosystem=Maven&q=antlr)).
- **commons-lang3 3.18.0 is exactly the fixed version for CVE-2025-48924** (introduced 3.0, fixed 3.18.0, [GHSA-j288-q9x7-2f5v](https://api.osv.dev/v1/vulns/GHSA-j288-q9x7-2f5v)).
- **commons-text 1.14.0 is outside CVE-2022-42889** (1.5 to below 1.10.0, [GHSA-599f-7c49-w659](https://api.osv.dev/v1/vulns/GHSA-599f-7c49-w659)).
- **The absence of advisories for antlr 2 and ST3 reflects disinterest as much as safety.**

## 6. License
ASM: BSD-3-Clause. commons-lang3, commons-text: Apache-2.0. jopt-simple: MIT. stringtemplate: "BSD licence" (POM).
antlr: "BSD License" in the POM with no text; deps.dev says "non-standard", PIT's SBOM says BSD-3-Clause. None is
distributed with any artifact of ours.

## 7. Execution privileges (test phase: the `pit-core` JavaExec task)
- **Where they run.** All ten run in the PIT parent JVM, with its rights: files, sockets and child processes (PIT
  review §7). Whether PIT also places them on the classpath of its child (minion) JVMs was not traced.
- **ASM** parses and generates class files. PIT's mutants, built with it, are then executed by design.
- **StringTemplate and antlr** parse PIT's own templates from inside its jar and render report data. That data
  includes our class names and source lines. Whether the templates HTML-escape it was not verified (section 10).
- **jopt-simple** parses the task's arguments, which come from our build script.
- **commons-text** includes `StringSubstitutor` interpolation. PIT's call sites were not traced. In 1.10.0+ the default
  lookups exclude script, dns and url (the CVE-2022-42889 fix).
- **No build-time or runtime-classpath exposure.** No network, native code or processor use is known for any of them.

## 8. Direct dependencies as published (and what PIT bundles)
- **Shading in PIT's published POMs** ([html-report](https://repo1.maven.org/maven2/org/pitest/pitest-html-report/1.30.2/pitest-html-report-1.30.2.pom), [command-line](https://repo1.maven.org/maven2/org/pitest/pitest-command-line/1.30.2/pitest-command-line-1.30.2.pom), [pitest](https://repo1.maven.org/maven2/org/pitest/pitest/1.30.2/pitest-1.30.2.pom), [entry](https://repo1.maven.org/maven2/org/pitest/pitest-entry/1.30.2/pitest-entry-1.30.2.pom)):
  - **pitest-html-report** shades `org.antlr:*` and `antlr:*`, relocated to `org.pitest.reloc.antlr` and `org.pitest.reloc.antlr.common`. Its POM still declares stringtemplate 3.2.1, so Gradle resolves the standalone jars too.
  - **pitest-command-line** shades `net.sf.jopt-simple:*` **unrelocated** and still declares jopt-simple 4.9. The same classes therefore sit in two jars.
  - **pitest** shades ASM relocated to `org.pitest.reloc.asm`, yet still declares asm and asm-commons.
  - **pitest-entry** shades ASM with no relocation and declares asm, asm-util and commons-text.
  - None sets `createDependencyReducedPom`.
- **Edges:**
  - stringtemplate → antlr 2.7.7 (compile).
  - commons-text → commons-lang3 3.18.0.
  - asm-tree → asm; asm-analysis → asm-tree; asm-commons → asm, asm-tree; asm-util → asm, asm-tree, asm-analysis.
  - antlr, asm and jopt-simple have none.
- **Covered elsewhere:**
  - pitest, pitest-entry and pitest-html-report: PIT review.
  - junit-platform-commons, -engine and -launcher and junit-bom on `pitest`: JUnit 5.14.4 review.
  - opentest4j: the test-path family review.
- **POM metadata only:** pitest-parent 1.30.2, `org.ow2:ow2` 1.5.1, commons-parent 85 and oss-parent 7.

## 9. Alternatives considered, and the exclusion test
- **The `info.solidsoft.pitest` Gradle plugin:** the design defers it, because it runs at configuration time.
- **Exclude antlr, stringtemplate and jopt-simple from `pitest`.** The test must prove all of the following:
  - a jar listing shows `org/pitest/reloc/antlr/**` inside pitest-html-report-1.30.2.jar and `joptsimple/**` inside
    pitest-command-line-1.30.2.jar;
  - `pit-core` completes with the exclusions;
  - the HTML report contains `index.html` and per-class pages with mutation results;
  - every option the task passes is still accepted, and an invalid option still fails.
- **The ASM duplicates** (pitest-entry's unrelocated copy plus the standalone jars) are the same version. Excluding them
  is not recommended, because pitest-entry's jar contents were not inspected.

## 10. Conditions and residual risk
**Conditions:** see the frontmatter.

**Residual risk:** two unsigned, 17- and 19-year-old jars run in the PIT JVM until condition 4 is acted on; their
integrity rests on our pinned hash plus PIT's SBOM. The `joptsimple.*` classes exist twice, so which copy loads depends
on classpath order (today they are identical versions). The report embeds source text: if PIT's templates do not escape
it, a report built from a fork PR could carry script into whoever opens the uploaded artifact.

## 11. Not verified
- **JARs:** none downloaded or listed, so the shading is known from POM configuration only. No `.asc` and no SBOM signature checked.
- **Behaviour:** the ASM 9.11.1 change list; commons-text call sites; whether minion JVMs get these jars; report escaping.
- **OSV:** not re-run by me; `inputs/osv.json` and deps.dev stand in. The ASM and jopt-simple Scorecards were not queried.
