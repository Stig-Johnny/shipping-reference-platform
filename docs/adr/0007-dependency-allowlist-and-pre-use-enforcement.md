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
  - "preflight and java (required checks): the checker's check 8. The checker rejects recognized container declarations. The harness validates its PostgreSQL instance, and CI checks subsequent engine activity. These controls do not isolate arbitrary build code."
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
  `jdk`, `github-action`, `container-image`, `homebrew`, `npm`, `vendored-js`, `browser-binary`, `runtime` and
  `runner-image`. Verdicts: `APPROVE`, `APPROVE-WITH-CONDITIONS`, `REJECT`. There is no
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
- **Container images: recognized declarations as text before the build, the engine around the tests.** The checker
  rejects recognized container declarations. The harness validates its PostgreSQL instance, and CI checks subsequent
  engine activity. These controls do not isolate arbitrary build code. Check 8 fails closed: it matches only in the
  forms it decodes completely and refuses every other form, whatever it holds. It reads every file under a project's
  `src/` directory whatever its extension, every Java, Kotlin and Groovy file outside the checker's own directory (the
  build scripts included), `gradle.properties`, the workflows and action files, and every shell script, by its `.sh`
  extension, its `#!` line or its executable bit; in the whole tree it refuses executables and archives other than the
  wrapper JAR, and container build and compose files. It decodes Java after its Unicode escapes; Java and Kotlin
  without comments, with every string literal decoded, alone and joined with its neighbours; YAML scalars on one line
  and literal blocks, their indentation removed as YAML removes it; and shell text as bash splits it into words, with
  the place of every command known. It refuses what it cannot decode: any heredoc in a script, a workflow or a
  resource, and one with a body in a string literal; `$'...'` quoting; a YAML scalar over lines, a folded block, an
  escape in a double-quoted scalar, anchors, aliases, tags, merge keys and explicit keys, a GitHub expression in a run
  step, a shell other than bash or sh, and an action that is not composite; a command that is an expansion or a glob,
  brace expansion, a shell that reads its commands from its input, an awk program that runs commands, an interpreter
  or build tool whose programs it does not read (Python, Node.js and npm, make and others) and a file of the tree run
  as a script that it does not read as one; a backquoted Kotlin name, and every Groovy file; a runtime's name split
  across string literals; the variables that make bash, the loader or a runtime run other code (`BASH_ENV`,
  `LD_PRELOAD`, `JAVA_TOOL_OPTIONS` and others) and JVM options that run a command or load an agent; a Unix socket
  opened from code outside the harness; and a file under `src/` that is not UTF-8 text. Within what it decodes it
  rejects a digest that no approved `container-image` entry has, a tag in place of or next to a digest, an image name
  built in code, any reference to Testcontainers or docker-java outside the harness `ReviewedPostgres`, any container
  there but the reviewed PostgreSQL one, the Testcontainers features the reviews forbid (Compose, host binds, copies
  into a container, host access, privileged mode, reuse, a network mode and create-command modifiers), a container
  runtime named in code, a string or a resource, Testcontainers configuration files and image settings, and in
  workflows and scripts docker as a command word with anything but a pull by digest, reads and removals, docker in a
  word it cannot read as a command, and job and service containers written as plain, quoted, flow, escaped or explicit
  YAML keys. What it cannot see stays outside it: text assembled at run time (from variables, a file's contents, a
  program's output or a method call such as `format`), reflection, the programs that a command it allows runs in turn
  (sed, git's configuration, `find -exec`), and a library that starts a container of its own; for those, the engine
  checks after the tests are what find a container. The web check that ADR 0012 plans runs Node.js and npm, which
  check 8 refuses until it decodes JavaScript and TypeScript or a review allows them. The `java` job runs the candidate's copy of the checker as well, after the base branch's and before any other
  code of the candidate, so that a rule a pull request adds applies to that pull request. At run time,
  `tools/ci/container-engine.sh` checks the runner, the engine and runc versions against the runner review, makes the
  daemon publish on 127.0.0.1 only, stages the image by digest, and afterwards checks that no other image appeared,
  that every container created came from the reviewed image and that nothing was copied into one, every engine query
  checked on its own; it does not see how another container was configured. The harness checks its own container
  from inside the test JVM and hands a test only a `Database` handle, never the container.
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
- **npm.** No `package.json`, `package-lock.json` or `web` job exists yet, so the npm rules have nothing to apply to.

## More Information

- Gradle dependency verification: <https://docs.gradle.org/current/userguide/dependency_verification.html>
- npm ci: <https://docs.npmjs.com/cli/v11/commands/npm-ci/>
- GitHub Actions settings: <https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository>
- Renovate execution risks: <https://docs.renovatebot.com/security-and-permissions/>
