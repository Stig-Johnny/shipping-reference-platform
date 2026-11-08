---
title: "Hidden web loaders now produce reader refusals"
description: "The import reader refuses ambiguous source and page attributes instead of reporting that they load nothing."
status: accepted
updated: 2026-11-08
pr: 70
kind: gate
recorded: 2026-11-08
tests:
  - ImportScannerTest
  - SourceReaderTest
  - AppConfigurationTest
---

## What changed

The web import reader reads sources and configuration without running them. A generic type assertion could hide an
import, an alias could hide a loader, and encoded or repeated HTML attributes could conceal a remote script or an
import map. Quoted destructuring keys and escaped or computed property keys could also conceal a loader; URL tabs,
line breaks and backslashes could conceal an external origin. Those cases now produce refusals. Keys held in const
bindings and template keys fail closed. String timers and timer aliases are refused, with direct function callbacks
still supported. URL validation precedes origin classification. CSS imports through package subpaths are refused too, in browser
sources and tests. Ordinary local imports and local script and stylesheet links still pass.

## Try it

Use the reviewed Temurin JDK 25.0.5+8 as JAVA_HOME and first on PATH. From the repository root, run the synthetic
fixtures, which create and clean up temporary files automatically. A negative fixture passes only when the reader
refuses its input with the expected location and reason. The same test classes also check accepted local imports
and the workbench's ordinary configuration. No web server, installed web packages or browser runtime is needed.

```sh
./gradlew :libs:c4-contract-kit:test --rerun --tests '*ImportScannerTest' --tests '*SourceReaderTest' --tests '*AppConfigurationTest'
```

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-08 on an ARM64 Mac running macOS 26.5, with Temurin 25.0.5+8. UTC timestamps came from `date -u`.
A separate JDK-only recording harness called the compiled reader on temporary synthetic files and returned exit 1
when it found refusals or unsupported imports, and exit 0 otherwise. These are reader verdicts, not a running
application or a model comparison. The recording harness and full red/green test logs accompany the review of
pull request #70 outside the published repository. The following outputs are complete.

The refused case combined a generic assertion containing a literal import, a Function alias, a scoped package CSS
import, an encoded script URL and duplicate link URLs. Exit status: 1.

```text
2026-11-08T00:41:36Z
generic.ts:3: a / after '>' is not supported: it could start a regular expression or be an operator, and the reader does not guess; put the expression in parentheses
loader.ts:1: the Function constructor is not supported: the code it builds could load modules that the check never sees
style.ts:1: a TypeScript import of a style sheet is not supported: link style sheets from index.html
index.html:1: the attributes of a <script> element cannot be read with certainty
index.html:2: the attributes of a <link> element cannot be read with certainty
Reader verdict: REFUSED (5 findings)
Exit status: 1
```

The accepted case used a local `.ts` import, ordinary division, a local module script and a local stylesheet link.
Exit status: 0.

```text
2026-11-08T00:41:37Z
Reader verdict: ACCEPTED (0 findings)
Exit status: 0
```

The earlier recording above accompanied 36 failing reader cases before correction and 307 passing tests afterward. Separate downstream conformance fixtures also failed before the fix and passed with it; that gate belongs
to the following slice and is not evidence supplied by this publishing build. The tests named in this demo's
metadata are the current-build evidence, only when they pass in the build that publishes this page.

The follow-up regression run added 232 failing cases for static keys and URL normalization, then four failing cases
for timer arguments whose function prefix did not determine the final value. All 543 tests in the three reader
classes passed after correction. The retained red/green outputs include each new fixture's failing baseline. These
counts describe recorded runs; the publishing build checks the test classes named above.

## Limits

This slice does not yet check a real web application against the model. It does not execute TypeScript, simulate
browser parsing or inspect installed packages. The grammar deliberately refuses uncertainty: harmless names,
computed indexing, callback aliases, a slash after a comparison and literal ampersands can be rejected too.
Non-literal computed access/binding keys are refused even when a person can evaluate their constant value. URL
attributes accept only a conservative ASCII syntax subset; some valid browser URLs are refused, including IP
literals, credentials and non-ASCII paths. Reflective calls and general runtime behaviour are outside these fixtures.
Passing reader tests proves these cases, not general runtime safety.
