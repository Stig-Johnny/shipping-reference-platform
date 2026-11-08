---
title: "External classes need a verified provider, even under the API's package name"
description: "The API import gate rejects namespace impersonation, unverified project providers and withdrawn alias approval. Its bytecode and scope limits remain explicit."
status: accepted
updated: 2026-11-08
pr: 78
kind: gate
recorded: 2026-11-07
tests: [ExternalImportPolicyTest]
---

## What changed

The API's import gate checks dependencies reported by ArchUnit 1.5.1 against their providers and the caller's layer
whitelist. Internal ownership now comes from actual main and test outputs. An external class cannot become internal
by using the API's package name, and an artifact colliding with an output class fails even when unused.

Project providers keep their build identity. Only root-build projects with exact registered repository-owned outputs
are exempt from Maven review; currently that is the contract kit. Included builds and unverified outputs fail.
The old `:example-app` name means the API only while the exact existing affirmative owner record and its accepted
status remain. Missing, changed or withdrawn acceptance fails. No security record or whitelist was widened.

## Try it

From the repository root, with the reviewed JDK as `JAVA_HOME`, run the checks. The negative fixtures assert the
rejection, so the complete suite passes. Fixtures use temporary files and need no manual cleanup.

```sh
./gradlew check
```

Reports are in `apps/shipping-api/build/reports/external-imports/`: `main.md`, `test.md` and `fixtures/`.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07T23:44:09Z on macOS with Temurin 25.0.5+8, based on revision 4e2b0b6 with uncommitted
regression tests before the ownership fix. The focused Gradle test run exited 1 because the old policy let these
cases through. Excerpt; task setup and assertion stack traces are omitted:

```text
ExternalImportPolicyTest > anArtifactCollidingWithAnUnusedMainOutputFails() FAILED
ExternalImportPolicyTest > anExternalFileUnderTheApiNamespaceStillNeedsReview(Path) FAILED
ExternalImportPolicyTest > anExternalClassUnderTheApiNamespaceStillNeedsAProvider(Path) FAILED
ExternalImportPolicyTest > anArtifactCollidingWithAnUnusedTestOutputFails() FAILED
4 tests completed, 4 failed
BUILD FAILED in 3s
```

Recorded 2026-11-07T23:48:30Z on the same base revision with uncommitted fixes. The Gradle run of
`ExternalImportPolicyTest` and its build-identity prerequisite exited 0. Excerpt; task setup is omitted:

```text
PASS: project identity retains build :
PASS: project identity retains build :shadow
BUILD SUCCESSFUL in 4s
```

Recorded 2026-11-07T23:52:13Z, still based on revision 4e2b0b6 with uncommitted fixes, after the passing policy
suite. The rejected external-file fixture's report says:

```text
2 uses, 1 denied.
- `io.github.stigjohnny.shipping.architecture.fixtures.externalimports.namespace.quotes.internal.application.UsesHelper` uses `io.github.stigjohnny.shipping.quotes.api.UnreviewedHelper`, rule allowlist: unknown unreviewed.jar is neither a Maven artifact nor a project of this build: no review covers it
```

The API's main-output report from that run says:

```text
266 uses, 0 denied.
```

These report excerpts omit the inputs and full dependency tables. `ExternalImportPolicyTest` checks the fixtures and
the current API outputs in every build. The JDK-only `ProviderIdentityTest` harness checks that Gradle project
identity retains the build path. Only passing named JUnit evidence from the publishing build is shown as verified
by the site. These local recordings are review material for pull request #78, not publishing-build evidence.

## Limits

- The control remains partial. Compiler-erased references, such as inlined constants, and retained bytecode that
  ArchUnit does not report, such as type-use annotations, need the planned source analysis.
- Maven SHA-256 comparison applies where the review records a hash. The first-party exemption does not remove the
  caller's layer whitelist.
- Review scope is checked for used classes on their compile classpath, not every resolved artifact. The recorded
  gap is 23 artifacts across 26 configuration pairs, including ArchUnit on production `runtimeClasspath`. Closing
  that gap needs owner-accepted scope changes; this pull request does not authorize them.
- Reflection, framework wiring, runtime provider changes and method-level review conditions remain outside this
  class-level check. The contract kit and tools' own external imports are not checked here.
- The recordings use a dirty worktree based on the stated revision. The revision in the site's title block identifies
  the build's base commit; it does not identify these uncommitted changes. Final-head owner approval is still needed.
