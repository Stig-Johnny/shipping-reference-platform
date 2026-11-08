---
title: "Hidden web loaders and wrong-project approvals now fail"
description: "The model comparison rejects hidden loading paths and requires Node.js approval for the intended test job and project."
status: accepted
updated: 2026-11-08
pr: 71
kind: gate
recorded: 2026-11-08
tests:
  - WebConformanceTest
---

## What changed

The web import gate compares source imports with the model without running the web app. Hidden imports, loader
aliases, ambiguous page attributes and package stylesheet imports now fail that comparison using the reader fixes
from pull request #70. The new conformance fixtures prove that the refusals reach the gate, including for computed
imports and for approved stylesheet packages in browser sources and tests.

A test's Node.js built-ins also need approval for the intended project, `ci`, in configuration `web`, at the exact
version named by `engines.node`. Previously, a runtime review for another project could approve these imports if its
version and job matched. That review now leaves both imports unapproved and fails the gate. The conforming synthetic
shop still passes.

## Try it

Use the reviewed Temurin JDK 25.0.5+8 as JAVA_HOME and first on PATH. From the repository root, run the synthetic
conformance fixtures. They create temporary repositories and clean them up automatically; no web packages, browser
runtime or server are needed. Negative fixtures pass only when the gate rejects the input with the expected reason
and evidence. The same class includes the accepted synthetic shop and report-writing checks.

```sh
./gradlew :libs:c4-contract-kit:test --rerun --tests '*WebConformanceTest'
```

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-08 on an ARM64 Mac running macOS 26.5, with Temurin 25.0.5+8. UTC timestamps came from `date -u`.
A separate JDK recording harness called `WebConformance.check` and `WebConformance.assertConforms` on temporary
synthetic repositories using the compiled library and its existing test fixture. It returned exit 1 when the gate
rejected a repository and exit 0 when the gate accepted it, then removed the temporary files. The harness and trimmed
red/green logs accompany the review of pull request #71 outside the published repository. These recordings include
all standard output; the logger's no-provider notice on standard error is omitted.

The first rejected case adds a generic assertion hiding an import, a Function alias, a scoped stylesheet import from
an approved package, an encoded script URL and duplicate link URLs to the conforming shop. Exit status: 1.

```text
2026-11-08T00:51:23Z
Fixture: evasions
UNSUPPORTED_CONFIGURATION: web/index.html:1: the attributes of a <script> element cannot be read with certainty.
UNSUPPORTED_CONFIGURATION: web/index.html:2: the attributes of a <link> element cannot be read with certainty.
UNSUPPORTED_SYNTAX: web/src/api/generic.ts:3: a / after '>' is not supported: it could start a regular expression or be an operator, and the reader does not guess; put the expression in parentheses.
UNSUPPORTED_SYNTAX: web/src/api/loader.ts:1: the Function constructor is not supported: the code it builds could load modules that the check never sees.
UNSUPPORTED_SYNTAX: web/src/api/style.ts:1: a TypeScript import of a style sheet is not supported: link style sheets from index.html.
Conformance verdict: FAILED (5 violations)
Exit status: 1
```

The second rejected case changes only the Node.js runtime approval's project from `ci` to `other-project`. Its
version, approving verdict and `web` configuration still match. Exit status: 1.

```text
2026-11-08T00:51:23Z
Fixture: wrong-project
UNAPPROVED_IMPORT: security/allowlist.json approves no nodejs.org/dist node 26.13.0 for project ci in the web job, the Node.js whose built-in node:assert/strict tests import.
UNAPPROVED_IMPORT: security/allowlist.json approves no nodejs.org/dist node 26.13.0 for project ci in the web job, the Node.js whose built-in node:test tests import.
node:assert/strict: UNAPPROVED
node:test: UNAPPROVED
Conformance verdict: FAILED (2 violations)
Exit status: 1
```

The accepted case uses the conforming shop, approval for project `ci`, and local script and stylesheet links.
Exit status: 0.

```text
2026-11-08T00:51:24Z
Fixture: accept
node:assert/strict: APPROVED
node:test: APPROVED
Conformance verdict: PASSED (0 violations)
Exit status: 0
```

Before the fixes, all 36 new cases incorrectly passed the gate: 35 reader evasions and the wrong-project runtime.
The red run used the pre-fix reader implementation and the original runtime matcher; the merged reader fixes were
then restored. After the runtime fix, all 76 cases in `WebConformanceTest` passed. These are recorded test results.
Separately, the test named in this demo's metadata is evidence of the publishing build only when it passes in that
build; the recorded output above is never rerun by the site builder.

## Limits

This is a library gate exercised on synthetic repositories. The repository's web-app integration remains planned;
no real web imports are checked here. It does not execute TypeScript, simulate a browser, inspect installed packages
or prove runtime behaviour. Conservative reader refusals can reject harmless syntax, including type uses of loader
names and literal ampersands in URL attributes. Approval expiry remains PolicyCheck's responsibility before the
build. No capability is promoted by this recording.
