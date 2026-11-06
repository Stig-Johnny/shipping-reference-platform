---
title: "The dependency allowlist"
description: "How third-party code gets into this repository: a recorded security review per component, an allowlist the build enforces before anything runs, and the owner's acceptance."
status: accepted
updated: 2026-11-06
---

# The dependency allowlist

Nothing from outside this repository runs here unless someone has reviewed it first: no JDK, Gradle distribution,
Maven or npm package, GitHub Action or container image. "Reviewed" means a written record in `reviews/` that says what
the component is, who publishes it and how, what it can execute and when, its known advisories at that exact version,
and the conditions under which it may be used. `allowlist.json` turns those records into data a machine can enforce.

AI coding agents write much of the code in this repository. An agent that adds a dependency cannot get it past CI
without a review record and an allowlist entry, and both of those files belong to the code owner.

## What is in here

| Path | What it is |
|---|---|
| `reviews/*.md` | One security review per component or family, with its verdict, scope, conditions, evidence and what was not verified. |
| `allowlist.json` | Every component the repository may use, with its exact version, checksum or pinned commit, verdict, review record and expiry date. Rejected components are listed too, so the check can name them. |
| `verifications/` | Records of checks done before first use: checksums, signatures and code signing of the toolchain. |
| `AGENTS.md` | What an AI agent must do before it proposes a new dependency. |

## How it is enforced

| Layer | What fails | Where it runs |
|---|---|---|
| `tools/policy/PolicyCheck.java` | A locked Maven coordinate, npm package or Action that is not approved, expired, rejected or missing its review; a downloaded jar whose SHA-256 differs from its review; a Gradle wrapper or distribution that is not the reviewed one; an Action not pinned to an allowlisted commit. | The `preflight` workflow. It runs the main branch's copy of the checker on the pull request's files as data, before any build tool runs, so a pull request cannot weaken the check that judges it. |
| Gradle dependency locking (strict) | A resolved version that differs from the committed lockfile. | Every build. |
| Gradle dependency verification (strict) | A downloaded artifact whose SHA-256 is not in `gradle/verification-metadata.xml`. CI never writes that file. | Every build. |
| `tools/ci/stage-temurin.sh` | A JDK archive whose SHA-256 differs from its review. | Every CI job, before `actions/setup-java` installs it. |
| GitHub's allowed-actions setting (**planned**, not applied yet) | An Action that is not on the repository's list. It is set through OpenTofu in the operator's infrastructure repository. | GitHub, before the workflow starts. |
| `.github/CODEOWNERS` | A change to `security/`, the lockfiles, the build logic or the workflows that the owner has not approved. | Pull request review. |

Run the checker yourself with the JDK alone: `java tools/policy/PolicyCheck.java .` (exit 0 = clean, 1 = findings,
2 = it could not complete). `--report-only` prints the findings and exits 0.

## Adding a dependency

1. Ask a subagent for a review of the exact version, written to `reviews/` in the house format (see any existing file).
2. Resolve it with `GRADLE_USER_HOME=$(mktemp -d) ./gradlew resolveAllDependencies --write-locks
   --write-verification-metadata sha256`. This downloads and checksums artifacts without compiling or running them.
   Use an EMPTY Gradle home: with a warm cache Gradle reuses parsed metadata and never re-reads parent POMs, so they
   are missing from the file and CI, which always starts empty, fails verification. Review every NEW transitive
   coordinate the lockfile shows.
3. Add the allowlist entries, with the checksums from `gradle/verification-metadata.xml`.
4. Open a pull request. The code owner accepts or rejects it.

A version bump, including one Dependabot proposes, is a new review.

## Acceptance

The reviews are written by AI subagents. A human owner accepts them before first use: `allowlist.json` records that
acceptance under `acceptance`, and the checker fails while it is missing. The acceptance is bound to the exact reviewed
set: it stores a SHA-256 of every entry's enforced fields (`entriesSha256`), and the checker recomputes it. Changing,
adding or removing an entry afterwards fails the check until the owner accepts again.

## What this does not stop

It controls the dependencies a build declares, on the path CI runs. It cannot stop build code that downloads its own
executables, an approved artifact that is later compromised at its source, a compromised CI runner, or an agent on a
developer machine that bypasses CI. Those need execution isolation and network egress controls, which are not part of
this repository yet. See `docs/gaps.md`.

## Findings worth knowing

The reviews rejected 13 components. Four of them changed the design:

- **jqwik 1.10.x**, a property-testing library. Its test engine prints text addressed to AI agents into the test output
  on every run, with an option to erase it from terminals. The property tests use JUnit's own parameterized tests instead.
- **Testcontainers' Ryuk container.** It would need a privileged container holding the Docker socket. A label sweep
  does its cleanup instead.
- **Spring Modulith's `docs` module.** It pulls an archived Structurizr 5 line next to the Structurizr 6 this repository
  uses, and its Documenter deletes its output folder by default. Module pages come from the model instead.
- **happy-dom.** It has had eight advisories in two years. The frontend tests use jsdom.

Structurizr's transitive Guava 33.6.0-jre has an advisory (GHSA-xxph-c9ww-hj94), so the build pins 33.7.2-jre.
