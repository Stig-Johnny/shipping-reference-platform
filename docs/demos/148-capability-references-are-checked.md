---
title: "A capability named in the prose must be in the register"
description: "Inline code that names a capability or control now carries the cap: marker and must name an entry of docs/capabilities/capabilities.yaml, so a renamed or mistyped capability id fails the documentation check instead of drifting."
status: accepted
updated: 2026-11-21
pr: 148
kind: gate
recorded: 2026-11-21
tests: [ReferencesTest]
---

## What changed

Before, the prose named capabilities and controls 38 times in inline code, written bare, and nothing checked those
ids. A renamed or mistyped id went unnoticed.

Now a capability or control is named with its marker, such as `cap:control.inline-references`, and the id must be an
entry of `docs/capabilities/capabilities.yaml`, whatever its status. An id that the register has must carry the
marker. A bare token with the shape of an id, a prefix the register uses, a dot and a hyphenated name, fails when the
register lacks it, while a table name such as `tenants.tenant` is left alone. ADR 0023 records the grammar, and the 38
mentions in current documents carry the marker; records keep their wording.

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, change
`cap:control.scenario-conformance` in the README's scenario checks entry to the id control.scenario-checks, which the
register does not have. Then run:

```sh
./gradlew :tools:docs:check
```

It fails, and the output names the file, the line and the id. Cleanup: change the id back, and run the same command:
it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-21, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change. With the fixture above,
`./gradlew :tools:docs:check` exited 1. The README is an input of the documentation tests, so they ran again and
failed first: the tests that copy the repository copy the edited README too. The excerpt shows the console's last
lines and the failure message that the test report records for `BrainCheckTest`'s unchanged-repository test:

```text
784 tests completed, 156 failed
> Task :tools:docs:test FAILED
BUILD FAILED in 1m 44s
io.github.stigjohnny.docs.DocsException: the documentation sources are inconsistent (1 problem):
  - README.md:295: `cap:control.scenario-checks`: control.scenario-checks is no capability or control in docs/capabilities/capabilities.yaml (ADR 0023)
```

After the cleanup, `./gradlew :tools:docs:generate` and then the same command exited 0. The generation had already run
the documentation tests, all passing, so the check reused them. The excerpt leaves out other tasks' lines and a
logging library's warnings:

```text
> Task :tools:docs:checkGenerated
docs: the 22 generated files are up to date

> Task :tools:docs:site
docs: wrote the site, 120 files

> Task :tools:docs:check

BUILD SUCCESSFUL in 4s
```

`ReferencesTest` checks each rule whenever the documentation tests run, as they do in every CI build; the check itself
runs in every `generate`, `checkGenerated` and site build. Before the capability rules were written, 8 of its 62 tests
failed: exactly the cases that must be rejected. Review: pull request #148.

## Limits

- An unregistered bare token without a hyphen is never taken for an id, so a renamed id without a hyphen, written
  bare, is not caught; written with its marker, it is.
- The check proves that an id is in the register, not that a sentence describes that capability correctly.
- Paths and the site's labels follow, in issues 145 and 146; until then the site shows the marker's prefix as it is
  written.
