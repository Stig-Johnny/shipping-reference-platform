---
title: "ADR 0007: Dependency allowlist with pre-use enforcement"
description: "Every third-party component is reviewed and listed in security/allowlist.json before any build can download or run it; a base-branch preflight enforces this."
status: accepted
updated: 2026-11-08
date: 2026-11-06
decision-makers: repository owner
enforcedBy:
  - "preflight (required check): the base branch's tools/policy/PolicyCheck.java run in a pull_request_target workflow on the pull request's head, checked out as data"
  - "java (required check): Gradle strict dependency locking and SHA-256 dependency verification"
  - "tools/ci/stage-temurin.sh: SHA-256 check of the reviewed JDK archive before actions/setup-java installs it"
  - "GitHub Actions setting: selected actions only, SHA pinning required, six allowed patterns"
  - "acceptance: PolicyCheck recomputes acceptance.reviewedSetSha256 on every run"
  - "preflight and java (required checks): check 8 is a PARTIAL static lint of recognized container declarations and launch sites, with known bypasses; it is not the engine-access enforcing control"
  - "workflow runtime guards: stop and mask the engine, remove its socket and prove it unreachable before checkout and after the reviewed database harness window"
  - "tools/ci/container-engine.sh: the runner review's conditions around the database tests in java and pages, with the test harness's own checks"
plannedEnforcement:
  - "allowlist schema: security/allowlist.schema.json with the fields named in the decision"
  - "web: npm ci --ignore-scripts against the single root lockfile"
  - "tools/policy tests: AllowlistCheckTest, GradleVerificationCheckTest, NpmLockCheckTest, WorkflowPinCheckTest, ApprovalBindingTest, StrictJsonParserTest"
  - "approval binding: an allowlist entry added by a pull request is effective only after the owner approves that exact head SHA"
  - "verification workflow: dispatched by preflight from the base branch, with a trusted job publishing the check results"
  - "agent identity: a GitHub App that opens pull requests and lacks workflow-write permission"
---

# ADR 0007: Dependency allowlist with pre-use enforcement

## Context and Problem Statement

AI agents add dependencies readily, and a build executes code from every plugin and package it resolves. Integrity
checks prove that bytes did not change, not that anyone decided they are acceptable. Who decides that a component
may be used, where is that decision recorded, and how is it enforced before the component runs?

## Decision Drivers

- Explicit, version-specific authorization with an accountable review and enforceable conditions.
- Enforcement before execution, not only before merge: a late build task cannot stop a plugin that already ran.
- Coverage beyond libraries: transitives, plugins, JDK, wrapper, npm packages, Actions, images and browsers.
- A reviewable number of components.

## Considered Options

1. A schema-validated allowlist, enforced by a trusted base-branch preflight before any candidate build runs.
2. Gradle dependency verification and npm integrity alone.
3. Detection tools alone: dependency review, vulnerability scanning, Scorecard.

## Decision Outcome

Chosen option 1, with option 2 kept as integrity evidence and option 3 as optional input to reviews.

- `security/allowlist.json` (schema `security/allowlist.schema.json`, field `schemaVersion`) has one entry per exact
  component or artifact with: `ecosystem`, `coordinate`, `version`, `artifact` or `platform`, `sourceHost`, `digest`
  or `integrity`, `purpose`, `scope`, `licence`, `reviewRef`, `reviewedAt`, `expiresAt`, `findings`, `conditions`,
  `decision`. Ecosystems: `maven`, `gradle-plugin`, `gradle-distribution`, `jdk`, `npm`, `github-action`,
  `container-image`, `browser`, `tool`.
- `reviewRef` points to a record in `security/reviews/`. Reviews are written by AI review agents that inspect the
  downloaded contents as untrusted data (install scripts, plugin code, provenance, advisories, licence, suspicious
  changes) and are accepted by the owner (`security/AGENTS.md`).
- Gradle: strict lockfiles for every resolvable configuration; `gradle/verification-metadata.xml` with SHA-256 for
  every artifact; no wildcards, no trusted-artifact exclusions; wrapper `distributionSha256Sum`; exact plugin
  versions; no changing or dynamic versions; no remote DSL includes, scripts or plugins.
- npm: workspaces `apps/shipping-web`, `packages/shipping-client` and `docs` share one root `package-lock.json`.
  Every entry has an approved `resolved` host and an `integrity` value. Installs use `npm ci --ignore-scripts`;
  any build script that must run is listed with its command in the allowlist entry's `conditions`.
- GitHub Actions: repository setting "selected actions only", each action pinned by full commit SHA and allowlisted,
  no blanket allowance for GitHub-owned or verified actions. Images are pinned by digest.
- Locks and verification metadata are regenerated only in a separate, controlled maintenance environment and
  submitted as a pull request. CI never regenerates them. Renovate proposes updates without automerge.
- Pre-use enforcement: the `preflight` job runs in a `pull_request_target` workflow from the base branch. It fetches
  the pull request's changed files through the API as inert data and never checks out or executes them. The checker
  `tools/policy` is compiled with `javac` from the reviewed JDK; its `module-info.java` requires only `java.base`
  and `java.xml`, so it has no third-party code. It validates lockfiles, verification metadata, the allowlist and its
  schema, review records, expiry dates, wrapper checksums, plugin and tool manifests and workflow `uses:` references.
  An allowlist entry added by the pull request becomes effective only when the owner has approved that exact head
  SHA.
- On success, preflight dispatches the base-controlled verification workflow for the checked SHA. It revalidates
  the SHA, then runs the build on fresh hosted runners with a read-only token and no secrets. A separate trusted job
  publishes the required check results to the pull request's SHA. Ordinary pull request workflows cannot substitute
  for this path, and the GitHub App that writes code lacks workflow-write permission.

### Consequences

- Good, because the demo can show an unapproved dependency stopped before the build starts.
- Good, because the allowlist doubles as a readable inventory of everything the project trusts.
- Bad, because every addition and update waits for a review; this is deliberate pressure to keep the set small.
- Bad, because the checker includes a small JSON parser (the JDK has none) that must itself be tested and reviewed.
- Limit: this controls declared dependencies on the sanctioned CI path. It cannot stop arbitrary build code
  downloading executables, compromised approved artifacts, runner compromise or local-agent bypass. Those need
  stronger execution isolation and egress controls (docs/gaps.md).

### Confirmation

- **Enforced.** `preflight` is a required check (branch protection on `main`, strict, administrators included).
- **Planned.** `tools/policy` tests (Gradle project `:tools:policy`), each with negative fixtures: `AllowlistCheckTest`
  (unknown, expired, rejected, condition unmet), `GradleVerificationCheckTest` (missing or mismatched SHA-256,
  wildcard, exclusion), `NpmLockCheckTest` (unapproved host, missing integrity, lockfile not matching allowlist),
  `WorkflowPinCheckTest` (tag instead of SHA, unlisted action), `ApprovalBindingTest` (approval of an older head is
  ignored), `StrictJsonParserTest`. None of these exists yet.
- **Enforced** for `java`, **planned** for `web`. `java` runs Gradle with strict verification and locked
  configurations, and is a required check. `web` (`npm ci --ignore-scripts`) does not exist yet.
- **Planned.** `.github/CODEOWNERS` protects `security/`, `tools/`, `build-logic/`, `gradle/`, `.github/`, lockfiles
  and build files; `policy/risk.json` classifies them as level 3. Both files exist, but GitHub does not require code
  owner review and no check evaluates `policy/risk.json` yet (ADR 0008).

## Implementation status (2026-11-06)

What is built, and where it differs from the decision above. The decision stands; the differences are open work.

- **The allowlist fields.** `security/allowlist.json` has `schemaVersion` 1 and entries with `kind`, `coordinate`,
  `version`, `artifact`, `sha256`, `project`, `configuration`, `verdict`, `conditions`, `reviewRecord`, `reviewer`,
  `reviewedAt` and `expiresAt`, plus `alsoReviewedIn` on some entries. Kinds in use: `maven`, `gradle-distribution`,
  `jdk`, `github-action`, `container-image`, `homebrew`, `npm`, `vendored-js`, `browser-binary`, `runtime`,
  `runner-image` and `bundled` (third-party code inside another reviewed artifact, which no lockfile entry can match). Verdicts: `APPROVE`, `APPROVE-WITH-CONDITIONS`, `REJECT`. There is no
  `security/allowlist.schema.json`, and the names `ecosystem`, `sourceHost`, `purpose`, `licence`, `reviewRef` and
  `decision` are not used. `project` and `configuration` record the scope; the checker does not enforce it.
- **The checker is one file, with JDK-only regression tests.** `tools/policy/PolicyCheck.java` runs with the JDK's
  source launcher (`java PolicyCheck.java`). There is no `module-info.java` and no `:tools:policy` Gradle project. It
  imports only JDK packages. It checks the lockfiles, the SHA-256 values in `gradle/verification-metadata.xml`, the
  wrapper JAR and distribution checksum, workflow `uses:` pins, any `package-lock.json` (none exists yet), files under
  `vendor/` directories, review record presence, verdicts, expiry dates and the acceptance digest. It does not
  validate the allowlist against a schema. Its XML parser has doctype declarations and external entities switched
  off. Its JSON reader accepts RFC 8259 JSON only: an allowlist or `package-lock.json` with a malformed value, such
  as an unquoted version or a lone `+`, stops the check with exit 2 instead of being read as data. It refuses symbolic
  links in the checked tree, the root included (exit 2), and reads only regular files inside the tree; a finding about
  an unreadable lock-file line names the file and the line number, not the line. What it prints never repeats what a
  file holds: the values a finding names, such as coordinates, Action references and URLs, are printed in safe
  characters only, and a file it cannot parse is named with a line, a column and a fixed reason.
- **The tests are one more file for the source launcher, not the planned suite.**
  `java tools/policy/PolicyCheckTest.java` compiles `PolicyCheck.java` next to it and calls the checker directly;
  launching the checker never compiles the tests. They cover the JSON reader token by token, and each check above on
  small repositories they build in temporary directories, exit codes included. The `java` job runs the candidate's
  copy once the base branch's checker has passed the candidate; `preflight` does not, because it runs no pull request
  code. They write no JUnit report, so they are not the acceptance tests named under Confirmation, and
  `docs/capabilities/capabilities.yaml` does not count them as evidence. Before them, the checker was proven by
  mutation by hand: 13 scripted tamperings, recorded in pull requests #3, #7 and #10.
- **The pull request is checked out as data, not fetched through the API.** `preflight` checks out the base commit
  into `base/` and the pull request's head into `pr/`, both with `persist-credentials: false`, and runs
  `java base/tools/policy/PolicyCheck.java pr`. Nothing from `pr/` is executed. The JDK comes from the base branch's
  `tools/ci/stage-temurin.sh`, which checks its SHA-256. Pull requests from forks fail the job. Exit 1 (findings)
  and exit 2 (could not complete) fail it too.
- **An acceptance digest, not an approval bound to a head SHA.** The owner's acceptance is recorded in
  `security/allowlist.json` as `acceptance.reviewedSetSha256`: a SHA-256 over every entry in canonical JSON and every
  review record's bytes. The checker recomputes it on every run, so an entry or review that changes after acceptance
  fails `preflight`. The digest proves that the reviewed set did not change after acceptance. It does not prove that
  a human accepted it: the owner and the AI agents use the same GitHub account, and the agent that records the
  acceptance also computes the digest. The record quotes the owner's words and their timestamp from the chat
  transcript. Nothing binds an approval to a head SHA.
- **Container declarations: PARTIAL static lint (check 8).** The existing code remains unchanged under the owner's
  2026-11-08 decision. It rejects recognized declarations, unsupported syntax it recognizes, non-reviewed image
  digests and recognized launch sites without an exact path and whole-file hash. It scans source, build scripts,
  workflow/action YAML, shell scripts and selected resources. This is a finite lexical scan, not a proof that every
  executable route has been found. Its exact web-command predicate and launch registry are described below; the
  known bypass classes are listed under the runtime decision. Its existing rejections and regression assertions
  remain in place. A clean check 8 is not evidence that code cannot reach an engine.
- **Runtime engine access is the enforcing control (owner decision, 2026-11-08).** Every job's first step, before
  checkout or any action, stops `docker.service` and `docker.socket`, masks both units for the runner's lifetime and
  removes `/var/run/docker.sock`. Any failure stops the job. The same step requires `docker info` to fail and a
  direct Unix-socket connection with `nc` to fail; success of either refuses the job. Missing probe tools, timeouts
  and unexpected probe exit codes fail too. Inline guards need no candidate checkout or additional action.

  Only `java` and Pages `test` open a reviewed harness window: compile test classes with the engine off, unmask in
  the preparation step, then prepare, stage the reviewed image, run only `:apps:shipping-api:databaseTest`, and verify
  and sweep the engine. An `always()` step immediately afterwards stops, masks, unlinks and probes again, including
  after harness failure or cancellation. Later checks, report uploads and the Pages handover require successful
  shutdown. Ordinary tests and documentation run after shutdown with `-PdatabaseEvidence=true -x databaseTest`:
  the database task has already run in this job, and its passing, unskipped reports remain required. `preflight`,
  `mutation`, Pages `build` and `deploy` never open a window. No dependency or Action pin changes.

  `tools/ci/engine-guard-test.sh` runs the actual inline blocks with fake commands in the Java CI job. Reachable
  CLI/socket fixtures and stop/mask/unlink/probe failures, including a timeout, must fail, while the stopped fixture
  passes. The regression checks call order and immediate termination after a failed operation. No real engine
  is used by this regression. `ContainerEngineWorkflowTest` checks ordering and the window, and `PagesWorkflowTest`
  retains the publication separation and evidence-handover checks. Hosted service behaviour still needs a CI run.

  This enforces the workflow's engine availability windows. It is not a hostile-code sandbox: a privileged process
  on a hosted runner could unmask/restart the daemon, install another engine or contact a remote endpoint. Arbitrary
  Gradle configuration still runs within the database invocation. The harness validates its own PostgreSQL container;
  `container-engine.sh` audits images/events afterwards, not every other container's privileges or configuration.
- **No base-dispatched verification workflow.** `preflight` dispatches nothing, and no trusted job publishes check
  results. `java` is an ordinary `pull_request` workflow. It checks out the base branch next to the candidate, stages
  the JDK with the base branch's script and runs the base branch's checker on the candidate before any candidate code
  runs. Only then does it run the candidate's checker tests, verify the wrapper and run `./gradlew check` under
  strict locking and checksum verification. So an unreviewed dependency stops the job before Gradle starts. A pull
  request can still edit `java.yml` itself. `preflight`, run from the base branch, is the gate a pull request cannot
  weaken.
- **No agent App identity.** The AI agents open pull requests on the owner's GitHub account. No GitHub App exists
  for them, so their pull requests have the same author as the owner's.
- **Repository settings.** The Actions setting is applied: selected actions only, SHA pinning required, six allowed
  patterns and no GitHub-owned or verified wildcard. `preflight` and `java` are required status checks on `main`.
- **Reviewed web command forms (check 8, PARTIAL static lint).** Interpreter refusal remains the default. One predicate checks the literal
  executable, every argument, the working directory and the effective reviewed entries. It requires the accepted
  reviewed-set digest, approved and unexpired entries with existing review files, Node 26.13.0 and its bundled npm
  11.20.0 for the same platform, and the command conditions. Workflows require the Linux CI pair; scripts and source
  launches can also use the local macOS pair under its review conditions. TypeScript needs its separate 5.9.3 review
  for `apps/shipping-web`. This does not attest which executable is installed: archive verification, version assertions
  and the other toolchain conditions still apply before use.

  | Exact command | Working directory |
  |---|---|
  | `node --version`, `npm --version` | Repository root or `apps/shipping-web` |
  | `npm ci --ignore-scripts` | Repository root |
  | `tsc -p apps/shipping-web` | Repository root |
  | `node --test` | Declared workspace `apps/shipping-web`; Node's default discovery rooted there, for repository-owned logic tests |
  | `npm run build:web --ignore-scripts` | Repository root; root script `build:web` must be exactly `tsc -p apps/shipping-web` |
  | `npm run test:unit --ignore-scripts` | `apps/shipping-web`; workspace script `test:unit` must be exactly `node --test` |

  The same predicate applies to workflow run steps, shell scripts and recognized command vectors inside registered
  Java/Kotlin launch sites: `ProcessBuilder`, `Runtime.exec` and Gradle `commandLine`/`setCommandLine`. A matching
  command alone does not authorize a recognized launch site in an unregistered source file. Complete literal vectors are read;
  recognized computed vectors, unsupported setters and constructor references are refused; missed execution sites
  are outside this predicate. Root execution is the repository
  script/JVM convention; Gradle's default is its project directory. A workflow can state its directory on the step,
  a shell can start with a single literal `cd apps/shipping-web &&` followed by one command, a process builder can
  chain a literal `directory(new File(...))`, and an Exec block can state a literal `workingDir`. Inherited workflow
  defaults and unresolved directory changes earn no exception.

  Root and explicitly declared workspace manifests are inspected even without a script invocation. Every script
  must have its exact name, body and invocation in the checker's registry above; lifecycle hooks are refused.
  Repository npm configuration files and npm configuration environment overrides are refused. Arbitrary `npm run`,
  `exec`, `npx`, inline Node programs, loaders and additional options remain refused. `--ignore-scripts` stays on both
  installation and registered script invocations. The separate maintenance approval authorizes no `npm install` in CI.
  There are still no npm manifests, lockfile or web job in this tree; these rules are exercised by inert fixtures.

- **Structural launch-site lint (check 8, PARTIAL, 2026-11-08).** Before inspecting commands, the checker refuses
  recognized launch capabilities in Java, Kotlin and Gradle source unless its exact path and whole-file SHA-256 appear in
  `PolicyCheck.LAUNCH_SITES`. This scan includes tests, `tools/policy/`, source packages named `build`, generated
  source directories and caches; only Git's own metadata is outside it. It reads code after Java Unicode decoding
  and removal of identifier-ignorable characters, comments and inert literals. Kotlin expression templates and
  backquoted identifiers are refused without a pin; nested Kotlin comments retain their lexical boundaries.

  The lint recognizes a finite list of process-builder, Runtime, process-handle, Exec/JavaExec, setter/provider,
  reflection and method-handle spellings. Some qualified names, aliases and static imports expose those tokens;
  the bypasses below show that the list and lexical model are incomplete. It does not try to reconstruct a command
  after arbitrary setters or resolve an inferred receiver. Existing regression examples of `env` wrappers, later
  argument mutations and inferred Runtime aliases in new files are rejected; this is not complete coverage. Every present registered file is
  hash-checked even if an edit removes all its launch code; copying it to another path grants nothing.

  Seven existing files are pinned: `ReviewedPostgres.java` (the DB harness), the API build script (its test JVM
  argument provider), the docs build script (the docs JVMs and git revision read), `MutationTestingPlugin.java`
  (the reviewed CI-only PIT task), `PolicyCheckTest.java` (the child-JVM test launcher and cleanup),
  `FixtureCompiler.java` (its Runtime shutdown hook), and `RestrictedStructurizrTest.java` (reflection that checks
  parser restrictions). There are no directory permissions or synthetic launch entries. The latter two files
  need pins because they hold Runtime/reflection capabilities even though their present code launches no process.
  The registry grants no permission to run PIT outside its existing reviewed CI conditions.

  The registry belongs to the running checker; preflight uses the base branch's version, not a registry supplied
  by the candidate. There is currently no working transition protocol for updating an existing pin: a source plus
  candidate-pin edit fails the base checker, while a registry-only replacement fails the candidate checker against
  unchanged source. Owner approval alone does not resolve this. A trusted current/successor-digest protocol remains
  open work; this change neither implements one nor changes any pin. The existing workflow/shell
  web predicate is unchanged, and registered source command vectors still receive its exact-form decision.
  Hashes freeze the reviewed launchers, including their dynamic JavaExec arguments; they do not prove their logic
  safe or isolate the repository code, approved libraries or tools those launchers execute.

### Known static-lint gaps from the two PR #54 reviews

The 2026-11-08 owner decision retains these as documented gaps rather than claiming complete static enforcement:

- Gradle can mutate an initially approved vector through `args`, `setArgs`, `setExecutable`, property assignments
  or argument providers. Process wrappers such as `env`, executable-path variants, and qualified or statically
  imported inferred `Runtime` aliases are additional launch routes. Existing registry fixtures reject several
  examples from the earlier review; they do not prove complete execution-site coverage.
- Raw CR line endings in Java/Kotlin and Java Unicode-escaped CR can end a line comment for the compiler while the
  scanner continues treating executable text as a comment. CR/LF/CRLF lexical handling is incomplete.
- `java.beans.Expression` and `java.beans.Statement.execute()` provide reflection outside the token list.
  `org.codehaus.groovy.runtime.ProcessGroovyMethods.execute` is a direct process API callable from Java or Kotlin
  even though `.groovy` source is refused. Imports and aliases broaden these routes.
- Gradle's Application plugin supplies an implicit `run` JavaExec task; `dependsOn(tasks.run)` can invoke it without
  spelling a registered process API. Other plugin-provided tasks are not exhaustively modeled.
- Commands assembled at runtime, dynamic arguments within pinned launchers, code imported by the reviewed Node test
  runner, libraries, and commands launched indirectly by allowed tools (`find`, `sed`, git configuration) are outside
  the complete-command predicate. An accepted command vector does not isolate any code it executes.
- Existing single-digest pins have the base/candidate transition deadlock described above. Static false positives
  also remain, including inert Testcontainers metadata and servlet `getMethod()` confused with reflection. These
  limitations must not be worked around by relaxing a check or silently pinning ordinary helpers.

The runtime guard is the enforcing control for local engine availability in CI. Broader isolation and a trusted
pin-transition design remain open work; the static lint stays PARTIAL.

## More Information

- Gradle dependency verification: <https://docs.gradle.org/current/userguide/dependency_verification.html>
- npm ci: <https://docs.npmjs.com/cli/v11/commands/npm-ci/>
- GitHub Actions settings: <https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository>
- Renovate execution risks: <https://docs.renovatebot.com/security-and-permissions/>
