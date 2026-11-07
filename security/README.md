---
title: "The dependency allowlist"
description: "How third-party code gets into this repository: a recorded security review per component, an allowlist the build enforces before anything runs, and the owner's acceptance."
status: accepted
updated: 2026-11-07
---

# The dependency allowlist

Nothing from outside this repository can be merged into `main` unless someone has reviewed it first: no JDK, Gradle
distribution, Maven or npm package, GitHub Action or container image. "Reviewed" means a written record in `reviews/` that says what
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
| `tools/policy/PolicyCheck.java` | A locked Maven coordinate, npm package or Action that is not approved, expired, rejected or missing its review; a downloaded jar whose SHA-256 differs from its review; a Gradle wrapper or distribution that is not the reviewed one; an Action not pinned to an allowlisted commit; a file under a `vendor/` directory that is not byte-identical to an approved vendored artifact; an owner's acceptance that no longer matches the reviewed set; a recognized container declaration that the reviews do not allow: an image that no approved entry names by digest, Testcontainers or docker-java outside the database harness, and the Testcontainers and docker uses the reviews forbid, in sources, build scripts, workflows and scripts (check 8, which does not isolate arbitrary build code). | The `preflight` workflow, a required status check. It runs the main branch's copy of the checker on the pull request's files as data, without running any build tool, so a pull request cannot weaken the check that judges it. The `java` job runs the same base-branch checker on the candidate before Gradle starts, and then the candidate's own copy, so that a rule a pull request adds applies to it too. |
| `tools/ci/container-engine.sh` and the test harness `ReviewedPostgres` | A runner, Ubuntu, Docker Engine or runc version other than the reviewed ones (a prerelease of the runc floor included); a daemon that is not loopback-only; registry credentials, another Docker endpoint or a Testcontainers setting the reviews do not name; an image other than the reviewed PostgreSQL digest, staged or run; a privileged harness container, a host bind or a port published beyond 127.0.0.1; a copy into a container; an engine query that fails. | The `java` job and the `pages` workflow's `test` job, around the database tests (`:apps:shipping-api:databaseTest`): before any container runs, and after the tests whatever their outcome. The versions are recorded in the job summary. `tools/ci/container-engine-test.sh` fails each engine query in turn against a fake engine, in `java`. |
| Gradle dependency locking (strict) | A resolved version that differs from the committed lockfile. | Every build. In CI, the `java` workflow, a required status check. |
| Gradle dependency verification (strict) | A downloaded artifact whose SHA-256 is not in `gradle/verification-metadata.xml`. CI never writes that file. | Every build. In CI, the `java` workflow, a required status check. |
| `tools/ci/stage-temurin.sh` | A JDK archive whose SHA-256 differs from its review. | Every CI job, before `actions/setup-java` installs it. |
| GitHub's allowed-actions setting (applied) | An Action that is not on the repository's list. The setting allows selected actions only, requires SHA pinning and lists six allowed patterns, with no wildcard for GitHub-owned or verified actions. It is set through OpenTofu in the operator's infrastructure repository. | GitHub, before the workflow starts. |
| `.github/CODEOWNERS` (**not enforced yet**) | A change to `security/`, the lockfiles, the build logic or the workflows that the owner has not approved. | Pull request review, once GitHub can require code owner review (see below). |

**Every check must be able to fail.** Until 2026-11-06, `preflight` could not. Its `java … | tee` step ran under
GitHub's default `bash -e`, without `pipefail`, so the step took `tee`'s exit code and passed with findings: the fix's
commit message names run 42280120112, which printed a finding and passed. A review found it before the repository
went public. The workflows now name `bash`, which runs with `pipefail`. The upload guard had a similar flaw:
`if: always()` uploaded the reports even after the guard step had failed. Pull request #15 fixed both. Two pull
requests then made `preflight` fail on purpose and were closed unmerged:

| Pull request | What it changed | Expected | Recorded outcome |
|---|---|---|---|
| #16 | Added the line `com.example:unreviewed-library:1.0.0=runtimeClasspath` to `tools/docs/gradle.lockfile`. | Exit 1 (findings), with the finding `com.example:unreviewed-library 1.0.0 (in tools/docs/gradle.lockfile) has no review on the allowlist`. | `preflight` failed with exit 1. |
| #17 | Replaced `security/allowlist.json` with the text `{ this is not JSON`. | Exit 2 (could not complete): `## Dependency allowlist: could not complete`, then `java.lang.IllegalArgumentException: JSON expected '"' at offset 2`. | `preflight` failed with exit 2. |

The expected column is the checker's own output at the time. Both can be replayed in a clone, the missing `pipefail`
too, with the same exit codes; `git checkout` undoes each change. Since pull request #44 the checker names the place
of a parse error instead of quoting the parser, so replaying #17 prints `security/allowlist.json:1:3: JSON expected '"'`
after the heading:

```bash
echo 'com.example:unreviewed-library:1.0.0=runtimeClasspath' >> tools/docs/gradle.lockfile
java tools/policy/PolicyCheck.java .; echo "exit $?"                                         # exit 1
bash -e -c 'java tools/policy/PolicyCheck.java . | tee /dev/null'; echo "exit $?"            # exit 0: the bug
bash -eo pipefail -c 'java tools/policy/PolicyCheck.java . | tee /dev/null'; echo "exit $?"  # exit 1: the fix
git checkout -- tools/docs/gradle.lockfile

echo '{ this is not JSON' > security/allowlist.json
java tools/policy/PolicyCheck.java .; echo "exit $?"                                         # exit 2
git checkout -- security/allowlist.json
```

**What GitHub enforces today.** Branch protection on `main` requires a pull request, with the status checks
`preflight` and `java` passing on a branch that is up to date with `main`, for administrators too. It dismisses stale
reviews, requires conversation resolution, and forbids force pushes and deletion. A red check blocks the merge. It
does not require an approval or code owner review. Code owner review cannot be required yet: the only code owner
authors the pull requests through AI agents on the owner's account, and GitHub does not let authors approve their own
pull requests. Until the agents have their own identity (ADR 0008), the owner's approval of a reviewed set is the
acceptance recorded in `allowlist.json`.

Run the checker yourself with the JDK alone: `java tools/policy/PolicyCheck.java .` (exit 0 = clean, 1 = findings,
2 = it could not complete). It reads only regular files inside the tree it checks, and refuses a tree that holds a
symbolic link anywhere (exit 2). `--report-only` prints the findings and exits 0. `--print-digest` prints the digest
that an acceptance must record (see [Acceptance](#acceptance)). Its regression tests need only the JDK too:
`java tools/policy/PolicyCheckTest.java`. The `java` job runs them on every pull request, after the checker has passed
it.

## Adding a dependency

1. Ask a subagent for a review of the exact version, written to `reviews/` in the house format (see any existing file).
2. Resolve it with `GRADLE_USER_HOME=$(mktemp -d) ./gradlew resolveAllDependencies --write-locks
   --write-verification-metadata sha256`. This evaluates the repository's own settings and build scripts and its own
   `build-logic` plugins (no third-party Gradle plugins exist), then downloads and checksums the artifacts. It does
   not compile against, or execute, the downloaded libraries' code. Because it runs the build scripts, read any change
   to them before you run it.
   Use an EMPTY Gradle home: with a warm cache Gradle reuses parsed metadata and never re-reads parent POMs, so they
   are missing from the file and CI, which always starts empty, fails verification. Review every NEW transitive
   coordinate the lockfile shows.
3. Add the allowlist entries, with the checksums from `gradle/verification-metadata.xml`.
4. Open a pull request. The code owner accepts or rejects it.

A file that is committed rather than resolved, such as the docs site's Mermaid bundle, goes unmodified under a
`vendor/<name>/<version>/` directory next to the code that uses it. Its entry records the file's SHA-256, and the checker
compares the two on every pull request.

A version bump, including one Dependabot proposes, is a new review.

## Acceptance

The reviews are written by AI subagents. A human owner accepts them before first use: `allowlist.json` records that
acceptance under `acceptance`, and the checker fails while it is missing. The acceptance is bound to the exact reviewed
set. It stores one SHA-256 (`reviewedSetSha256`) over every entry, with all its fields in canonical JSON, and over the
bytes of every entry's review record. `java tools/policy/PolicyCheck.java --print-digest .` computes it, and the checker
recomputes it on every run. Changing, adding or removing an entry afterwards, or editing a review's text, scope or
conditions, fails the check until the owner accepts again.

The digest proves consistency: the reviewed set did not change after the acceptance was recorded. It does not prove
that a human accepted it. The owner and the AI agents use the same GitHub account, so GitHub cannot tell them apart,
and the agent that records the acceptance also computes the digest. The record quotes the owner's words and their
timestamp from the chat transcript.

The first acceptance used an older digest, `entriesSha256`, over the enforced fields only. CI judges a pull request
with the main branch's copy of the checker, so the pull request that introduced the new digest (#7) had to satisfy
the old one too, and recorded both. Earlier acceptances stay listed under `acceptance.previous`.

## Scope and project names

A review approves an artifact for a scope: the projects and configurations it may be on, and the conditions of its
use. The checker enforces the artifact (coordinate, version and checksum), not the scope. The code owner checks the
scope when reviewing a pull request; `docs/gaps.md` lists this gap.

The contract kit's reviews were written for its first design, which split it into three projects. Their `project` and
`configuration` fields keep the names they were reviewed under. `:core` is today's `:libs:c4-contract-kit`; `:junit5` and
`:example-app` are not projects in this repository. Scope extensions such as
`reviews/maven--docs-tool-scope-extension.md` record each further use.

## What this does not stop

It controls the dependencies a build declares, on the path CI runs. It cannot stop build code that downloads its own
executables, an approved artifact that is later compromised at its source, a compromised CI runner, or an agent on a
developer machine that bypasses CI. Those need execution isolation and network egress controls, which are not part of
this repository yet. See `docs/gaps.md`.

## Findings worth knowing

The reviews rejected 15 components. Six of them changed the design:

- **jackson-databind 3.1.7, the version Spring Boot 4.1.2 manages.** It is inside a critical advisory
  (GHSA-m8xf-2cr7-77ph, CVE-2026-110879, a `PolymorphicTypeValidator` bypass). FasterXML published it on its own
  repository on 2026-10-20. On 2026-11-06, neither GitHub's advisory database nor OSV's Maven index listed it for the
  package, so a scanner that matches by package would have passed it. The review read the publisher's advisory list.
  The build holds every Jackson 3 module at the patched 3.1.8.
- **jcl-over-slf4j**, which Spring Modulith's event support still asks for. It duplicates the commons-logging classes
  that Spring Framework 7 already routes to SLF4J, so every configuration excludes it.

- **jqwik 1.10.x**, a property-testing library. Its test engine prints text addressed to AI agents into the test output
  on every run, with an option to erase it from terminals. The property tests use JUnit's own parameterized tests instead.
- **Testcontainers' Ryuk container.** It would need a privileged container holding the Docker socket. A label sweep
  does its cleanup instead.
- **Spring Modulith's `docs` module.** It pulls an archived Structurizr 5 line next to the Structurizr 6 this repository
  uses, and its Documenter deletes its output folder by default. Module pages come from the model instead.
- **happy-dom.** It has had eight advisories in two years. The frontend tests use jsdom.

Structurizr's transitive Guava 33.6.0-jre has an advisory (GHSA-xxph-c9ww-hj94), so the build pins 33.7.2-jre.

Mermaid 11.17.2, the docs site's diagram renderer, embeds DOMPurify 3.4.12, lodash-es 4.17.23 and KaTeX 0.16.47, which
have open advisories that only the 12.x line fixes. Its approval rests on the conditions that make them unreachable:
the site renders only its own validated diagrams, in `strict` mode, under a restrictive Content Security Policy.
