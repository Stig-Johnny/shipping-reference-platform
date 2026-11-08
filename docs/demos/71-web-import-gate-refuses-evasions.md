---
title: "Hidden web loaders and wrong-project approvals now fail"
description: "The model comparison rejects hidden loader keys and ambiguous URLs, and requires Node.js approval for the intended test job and project."
status: accepted
updated: 2026-11-08
pr: 71
kind: gate
recorded: 2026-11-08
tests:
  - WebConformanceTest
  - WebReaderRefusalConformanceTest
---

## What changed

The web import gate compares source imports with the model without running the web app. Hidden imports, loader
aliases, ambiguous page attributes and package stylesheet imports now fail that comparison using the reader fixes
from pull request #70. The new conformance fixtures prove that the refusals reach the gate, including for computed
imports and for approved stylesheet packages in browser sources and tests.

The corrected reader's quoted and escaped loader keys, computed keys and indirect timers are now checked through
the public gate in `.ts` and `.tsx` browser sources, colocated tests and a separate test root. Script and stylesheet
URLs containing control characters, backslashes or other ambiguous syntax must also fail. Each case requires
`passed=false` and exact findings naming the file, line and reason, so a reader refusal cannot disappear during
model comparison. An escaped identifier must not hide an earlier quoted loader key's finding.

Numeric literals also leave constructor properties visible to the gate: `1..constructor.constructor(...)`,
`1.0.constructor.constructor(...)` and `1n.constructor.constructor(...)` now fail with their exact findings.
The gate checks every numeric-boundary and regex-flag refusal fixture added by the corrected reader, including
malformed numeric continuations, unsupported flags and nested template substitutions, in each source/test placement.

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
./gradlew :libs:c4-contract-kit:test --rerun --tests '*WebConformanceTest' --tests '*WebReaderRefusalConformanceTest'
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

A further recording on 2026-11-08 covered all 155 cases of `WebReaderRefusalConformanceTest`: 81 loader-key cases,
27 key-syntax and timer cases, six cases retaining findings before escaped identifiers, and 41 URL cases. Each source
case checks six files across the three source/test locations; each URL case checks both script and link attributes.
The red run temporarily restored the three production reader files from the previous version of pull request #70.
All 155 tests failed: 139 fixtures incorrectly passed the old gate, while the other 16 already failed the gate but
lacked the exact new findings. The following excerpts are from the recording script's JUnit summaries and process
exit statuses; individual case lines and Gradle task output are omitted.

```text
WebReaderRefusalConformanceTest: tests=155, failures=155, errors=0, skipped=0
Incorrectly accepted by the previous reader: 139; remaining failures assert exact findings.
Exit status: 1
```

With the corrected reader restored, the same 155 cases and the existing 76 conformance cases passed. The synthetic
shop and its required-edge fixture use the equivalent literal `[0]` access instead of array destructuring, which the
restricted reader now refuses; their assertions and import relationships are unchanged.

```text
WebReaderRefusalConformanceTest: tests=155, failures=0, errors=0, skipped=0
WebConformanceTest: tests=76, failures=0, errors=0, skipped=0
Exit status: 0
```

The numeric-boundary recording on 2026-11-08 adds 396 independent cases: all 66 reader fixtures, each in six TS/TSX
browser-source, colocated-test and separate-test-root placements. Only the lexer's production file was temporarily
restored from the previous reader for the red run. Every new case failed: 366 incorrectly passed the old gate, and
30 already failed but lacked the required exact findings. No fixture source was executed as TypeScript.

```text
WebReaderRefusalConformanceTest: tests=396, failures=396, errors=0, skipped=0
New lexical-boundary cases: 396
Incorrectly accepted by the previous reader: 366; remaining failures assert exact findings.
Exit status: 1
```

With the corrected lexer restored, all 396 new cases passed, as did the existing 155 reader-refusal cases and 76
conformance cases. Every new case asserts `passed=false` and the complete findings, including their file, line,
reason and refusal evidence. Multiple findings on one line and the line shift after a continued string are checked.
These excerpts come from the retained JUnit summaries; task chatter, case lines and stack traces are omitted.

```text
WebConformanceTest: tests=76, failures=0, errors=0, skipped=0
WebReaderRefusalConformanceTest: tests=551, failures=0, errors=0, skipped=0
Case parity: all 396 red case names match the 396 passing new cases.
Exit status: 0
```

The trimmed red/green logs and reproduction script accompany the review of pull request #71 outside the published
repository. Separately, the tests named in this demo's metadata are evidence of the publishing build only when they
pass in that build; the recorded output above is never rerun by the site builder.

## Limits

This is a library gate exercised on synthetic repositories. The repository's web-app integration remains planned;
no real web imports are checked here. It does not execute TypeScript, simulate a browser, inspect installed packages
or prove runtime behaviour. Conservative reader refusals can reject harmless syntax, including type uses of loader
names, array destructuring, computed indexing and literal ampersands in URL attributes. Approval expiry remains PolicyCheck's responsibility before the
build. No capability is promoted by this recording.
