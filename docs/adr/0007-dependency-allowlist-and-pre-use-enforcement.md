---
title: "ADR 0007: Dependency allowlist with pre-use enforcement"
description: "Every third-party component is reviewed and listed in security/allowlist.json before any build can download or run it; a base-branch preflight enforces this."
status: accepted
updated: 2026-11-06
date: 2026-11-06
decision-makers: repository owner
enforcedBy: [not enforced yet]
plannedEnforcement:
  - "preflight: tools/policy run from the base branch in a pull_request_target workflow"
  - "java: Gradle strict dependency verification and locking"
  - "web: npm ci --ignore-scripts against the single root lockfile"
  - "tools/policy tests: AllowlistCheckTest, GradleVerificationCheckTest, NpmLockCheckTest, WorkflowPinCheckTest, ApprovalBindingTest"
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

- `preflight` is a required check.
- `tools/policy` tests (Gradle project `:tools:policy`), each with negative fixtures: `AllowlistCheckTest` (unknown,
  expired, rejected, condition unmet), `GradleVerificationCheckTest` (missing or mismatched SHA-256, wildcard,
  exclusion), `NpmLockCheckTest` (unapproved host, missing integrity, lockfile not matching allowlist),
  `WorkflowPinCheckTest` (tag instead of SHA, unlisted action), `ApprovalBindingTest` (approval of an older head is
  ignored), `StrictJsonParserTest`.
- `java` runs Gradle with strict verification and locked configurations; `web` runs `npm ci --ignore-scripts`.
- `.github/CODEOWNERS` protects `security/`, `tools/`, `build-logic/`, `gradle/`, `.github/`, lockfiles and build
  files; `policy/risk.json` classifies them as level 3.

## More Information

- Gradle dependency verification: <https://docs.gradle.org/current/userguide/dependency_verification.html>
- npm ci: <https://docs.npmjs.com/cli/v11/commands/npm-ci/>
- GitHub Actions settings: <https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository>
- Renovate execution risks: <https://docs.renovatebot.com/security-and-permissions/>
