---
title: "ADR 0007: Dependency allowlist with pre-use enforcement"
description: "Every third-party component is reviewed and listed in security/allowlist.json before any build can download or run it; a base-branch preflight enforces this."
status: accepted
updated: 2026-11-07
date: 2026-11-06
decision-makers: repository owner
enforcedBy:
  - "preflight (required check): the base branch's tools/policy/PolicyCheck.java run in a pull_request_target workflow on the pull request's head, checked out as data"
  - "java (required check): Gradle strict dependency locking and SHA-256 dependency verification"
  - "tools/ci/stage-temurin.sh: SHA-256 check of the reviewed JDK archive before actions/setup-java installs it"
  - "GitHub Actions setting: selected actions only, SHA pinning required, six allowed patterns"
  - "acceptance: PolicyCheck recomputes acceptance.reviewedSetSha256 on every run"
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
  as an unquoted version or a lone `+`, stops the check with exit 2 instead of being read as data.
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
