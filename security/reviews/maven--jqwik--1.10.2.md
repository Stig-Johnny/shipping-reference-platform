---
component: "net.jqwik:jqwik:1.10.2 (with net.jqwik:jqwik-api, jqwik-engine, jqwik-web, jqwik-time 1.10.2)"
kind: maven
version: "1.10.2"
verdict: REJECT
scope: "None. Proposed use was the isolated property-test suite of apps/shipping-api (JUnit Platform 1.14.4 / JUnit 5.14.4); it is not approved for that or any other configuration."
conditions: ["No net.jqwik:* artifact at any version on any classpath without a new review", "A new review needs, at minimum: an engine that writes no text addressed to AI agents to stdout or reports, and an explicit written owner decision that using a library whose publisher states it is 'not meant to be used by any AI coding agents' fits this agent-authored repository", "Use the dependency-free alternative in §9 for the tariff properties"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: jqwik 1.10.2

**Method.** On 2026-11-06 the following were read through WebFetch: Maven Central (listings, POMs, `.module`, metadata), jqwik.net (the 1.10.2 user guide and the release notes), the source at tags `1.10.0`, `1.10.2` and `1.9.3`, and issues #708, #710, #714 and #719, plus deps.dev, OSV and Scorecard. Nothing was downloaded or run. `api.github.com` refused the fetches (HTTP 403). Text in jqwik addressed to AI agents was handled as data and is not reproduced here (`[passage omitted]`).

## 1. Verdict
**REJECT.**
- **The engine injects text aimed at AI agents.** From 1.10.0 onwards, every engine run writes a line addressed to AI agents to stdout. In 1.10.2 it tells them to disregard their instructions and to ignore jqwik's test results ([JqwikExecutor.java:44, 54-58 @1.10.2](https://raw.githubusercontent.com/jqwik-team/jqwik/1.10.2/engine/src/main/java/net/jqwik/engine/execution/JqwikExecutor.java)). This repository is agent-authored, and AI reviewers read its CI output, so that line is a deliberate prompt injection into our review path. At worst it would hide a failing property test from the AI review.
- **The publisher has already shipped a destructive variant.** In 1.10.0 the line told agents to delete all jqwik tests and code, and the engine always erased it from terminals. 1.10.0 has since disappeared from Central.
- **The publisher's stated terms exclude our use.** It says the project "is not meant to be used by any 'AI' coding agents at all".
- **The project is in "pure maintenance mode"**, on JUnit Platform 1.x.

The policy's trust test is "author trust", and the common attack it names is "a new version published to a name everyone already trusts". This publisher has done exactly that, openly, with intent.

## 2. What it is and why it is needed
jqwik is a property-based testing engine for the JUnit Platform. The blueprint (§7) uses it "for tariff boundaries, rounding and monotonicity of the defined synthetic tariff". It would "isolate its JUnit Platform 1.x engine in a JUnit 5.14.4 property-test task", because Boot 4.1.2 manages JUnit 6.0.3. That isolation is the right design for a Platform 1.x engine: a separate suite, with its own locked runtime classpath and without Boot's BOM. It does not address the findings below.

## 3. Provenance
- **Publisher.** Johannes Link (`jlink`) is the POM developer. jqwik-web and jqwik-time also list Johannes Zink. The source is github.com/jqwik-team/jqwik ([POM](https://repo1.maven.org/maven2/net/jqwik/jqwik/1.10.2/jqwik-1.10.2.pom)).
- **Release.** 1.10.2, "Little Fix for IDEs", released by jlink on 2026-10-10 from commit `c8e1d6a6e7a2598d5dea082a2f1e6425c5c031fc` ([release](https://github.com/jqwik-team/jqwik/releases/tag/1.10.2)).
- **Central files.** Every file has `.asc`, `.sha256`, `.sha512` and `.module` ([listing](https://repo1.maven.org/maven2/net/jqwik/jqwik/1.10.2/)).
- **SHA-256, from the publisher's sidecars, recorded so the gate can block these exact files:**
  - jqwik: `8f50edfb7ae64503b1d2da19083191cd5a88373e5f3d21a19a824b522de41386`
  - jqwik-api: `7e3b95d4ff78410273921a55aeaaea4294e5ed8171f7284680e9c3a84bf7088e`
  - jqwik-engine: `b4cc136a0efc1e2aa31514aae8d80b66fef53894c2d2777e2cc1143ca7511098` ([listing](https://repo1.maven.org/maven2/net/jqwik/jqwik-engine/1.10.2/))
  - jqwik-web: `b2c7930167524a7a3d0159d8462cb39d917fccb6352935a0a7b1abdda9e44a2a`
  - jqwik-time: `8437af35af2365374148e73fc27626a62861236df8b29ae88d2132426ad3af78`
- **Build.** The root `build.gradle` has no signing block. Its `publish` task runs `signalPublishToCentralSonatype.sh`, which POSTs to Maven Central's staging API with a bearer token ([build.gradle](https://raw.githubusercontent.com/jqwik-team/jqwik/main/build.gradle)). That indicates publishing from a maintainer's machine; this is an inference.
- **Provenance.** No SLSA provenance or attestations ([deps.dev](https://api.deps.dev/v3/systems/maven/packages/net.jqwik%3Ajqwik/versions/1.10.2)).

## 4. Maintenance and adoption
- **Releases** ([atom](https://github.com/jqwik-team/jqwik/releases.atom)):
  - 1.9.3 (2025-06-07)
  - 1.10.0 (2026-05-25), now absent from Central: it is not in the [metadata](https://repo1.maven.org/maven2/net/jqwik/jqwik-engine/maven-metadata.xml), and its [directory](https://repo1.maven.org/maven2/net/jqwik/jqwik-engine/1.10.0/) returns 404
  - 1.10.1 (2026-05-29, "Anti-AI Release")
  - 1.10.2 (2026-10-10)
- **Status.** "Currently, jqwik is in pure maintenance mode" ([README](https://raw.githubusercontent.com/jqwik-team/jqwik/main/README.md)). 1.10.1 "will probably be the last release of Jqwik using JUnit Platform version 1.x" ([release notes](https://jqwik.net/release-notes.html)).
- **Scorecard.** Not available: api.securityscorecards.dev returns 404, and deps.dev has no scorecard.
- **Usage.** 854 stars and 87 forks ([deps.dev](https://api.deps.dev/v3/projects/github.com%2Fjqwik-team%2Fjqwik)). One maintainer.

## 5. Known vulnerabilities at 1.10.2
**None found on 2026-11-06.** The osv.dev list for `net.jqwik` is empty ([OSV](https://osv.dev/list?ecosystem=Maven&q=net.jqwik)), and deps.dev `advisoryKeys` is `[]` for jqwik and jqwik-engine 1.10.2.

The rejection rests on the engine's deliberate behaviour (§7), which no advisory database records.

## 6. License
EPL-2.0. `LICENSE.md` at `1.10.2` is the plain EPL-2.0 text, the same length as at `1.9.3` ([LICENSE.md](https://raw.githubusercontent.com/jqwik-team/jqwik/1.10.2/LICENSE.md)). The "Anti-AI Usage Clause" is not a licence term. It is a statement in the [user guide](https://jqwik.net/docs/1.10.2/user-guide.html): "This project is not meant to be used by any 'AI' coding agents at all."

The maintainer said a licence change "might not be legally possible" ([#710](https://github.com/jqwik-team/jqwik/issues/710)). [CONTRIBUTING.md](https://raw.githubusercontent.com/jqwik-team/jqwik/1.10.2/CONTRIBUTING.md) forbids LLM-assisted contributions. deps.dev labels the licence "non-standard".

## 7. Execution privileges
- **Phase: test.** It runs as a JUnit Platform engine in the test JVM, with full rights.
- **stdout injection (1.10.2).** `execute()` first calls `printMessageForCodingAgents()`, with no condition ([JqwikExecutor.java:44](https://raw.githubusercontent.com/jqwik-team/jqwik/1.10.2/engine/src/main/java/net/jqwik/engine/execution/JqwikExecutor.java)). That method prints the agent-directed text [passage omitted] with `System.out.print` and no newline, so the next output joins the same line (lines 54-58).
- **Hiding it.** `jqwik.hideAntiAiClause=true` (default `false`, [JqwikProperties.java:26](https://raw.githubusercontent.com/jqwik-team/jqwik/1.10.2/engine/src/main/java/net/jqwik/engine/JqwikProperties.java)) only appends `ESC[2K\r` twice, which erases the line on a terminal. In the guide's words: "In normal captures of stdout the line will always show up."
- **1.10.0.** The method printed a line telling agents to delete all jqwik tests and code, and always appended the erase sequence, so humans watching a terminal never saw it ([JqwikExecutor.java:41, 51-54 @1.10.0](https://raw.githubusercontent.com/jqwik-team/jqwik/1.10.0/engine/src/main/java/net/jqwik/engine/execution/JqwikExecutor.java)).
- **The maintainer's statements:**
  - "Actively opposing hyper-scaled GenAI and agentic coding is an ethics-related decision" ([#708](https://github.com/jqwik-team/jqwik/issues/708)).
  - "No, I won't make the anti-ai-logging more robust" ([#710](https://github.com/jqwik-team/jqwik/issues/710)).
- **Files.** It writes the `jqwik.database` file, `.jqwik-database` by default, to record previous runs ([user guide](https://jqwik.net/docs/1.10.2/user-guide.html)).
- **Plugins.** It loads `ArbitraryProvider`, `ArbitraryConfigurator` and `LifecycleHook` implementations through `ServiceLoader`. `@UseType` calls public constructors and factory methods by reflection.
- **Not found:** no network use, and no environment detection in the files read.

## 8. Direct dependencies as published
From the [aggregator POM](https://repo1.maven.org/maven2/net/jqwik/jqwik/1.10.2/jqwik-1.10.2.pom) and the submodule POMs:

| Dependency | What it is |
|---|---|
| jqwik-api, jqwik-web, jqwik-time 1.10.2 (compile); jqwik-engine 1.10.2 (runtime) | Same publisher. The engine carries the behaviour in §7 |
| org.apiguardian:apiguardian-api 1.1.2 | API status annotations |
| org.opentest4j:opentest4j 1.3.0 (via api/engine) | Assertion error types |
| org.junit.platform:junit-platform-commons / -engine 1.14.4 (via api/engine) | JUnit Platform 1.x. These would need their own review in the isolated suite |

## 9. Alternatives considered
- **Recommended: no new dependency.** Write the tariff properties as JUnit Jupiter 6.0.3 `@ParameterizedTest` methods, already Boot-managed. Use a `@MethodSource` that enumerates every weight-band boundary (±1 minor unit) and a fixed-seed `java.util.random.RandomGenerator` sample, and assert rounding and monotonicity. A defined synthetic tariff can be enumerated almost exhaustively. This gives up shrinking, and keeps the blueprint's fixed-seed determinism.
- **jqwik 1.9.3** (2025-06-07, EPL-2.0, JUnit Platform 1.13.1, [POM](https://repo1.maven.org/maven2/net/jqwik/jqwik-engine/1.9.3/jqwik-engine-1.9.3.pom)) predates the injection. But it comes from the same publisher, in maintenance mode, on an older platform. It is not recommended and would need its own review.

## 10. Conditions and residual risk
**Conditions:** as in the frontmatter. If the owner still wants jqwik after a new review, the minimum would be:
- the isolated suite the blueprint describes, with no Boot BOM and junit-platform 1.14.4 locked;
- its stdout and reports kept out of AI-review inputs, or filtered;
- `jqwik.database` set to empty.

**Residual risk of rejecting:** none from the dependency itself. Property coverage then rests on the enumerated cases.

## 11. Not verified
- No jar was downloaded or decompiled. The behaviour comes from source at the tags; that the Central jars were built from that source is unverified. The SHA-256 values are Central sidecars, and the `.asc` signatures were not checked.
- Why 1.10.0 is absent from Central. The issue reports are third-party claims, and the maintainer said only "jqwik version 1.10.0 is not available on Maven Central" ([#719](https://github.com/jqwik-team/jqwik/issues/719)).
- The rest of the repository was not searched for other agent-directed output. Parts of the user guide were read only as summaries.
- The workflow files and buildSrc signing setup were not read (`api.github.com` 403).
- OSV's `POST /v1/query` was not used. The osv.dev list and deps.dev stand in.
