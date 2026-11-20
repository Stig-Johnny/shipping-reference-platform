---
title: "A test name in the prose must exist, or be marked planned"
description: "Inline code that names a test class now fails the documentation check unless the class exists, or the name carries the planned marker and is registered as planned, so the prose cannot drift from the tests or present a plan as done."
status: accepted
updated: 2026-11-20
pr: 147
kind: gate
recorded: 2026-11-20
tests: [ReferencesTest]
---

## What changed

Before, the prose named test classes 436 times in inline code, and nothing checked those names. 51 of them were
planned tests that do not exist yet, written exactly like existing ones.

Now a bare name must name a test class in the repository. A planned test carries a marker where it is named, such as
`planned:test:CrossTenantAccessTest`, and must be registered as planned, in `docs/capabilities/capabilities.yaml` or in
an ADR's planned enforcement. ADR 0023 records the grammar. The 47 mentions of planned tests in current documents now
carry the marker; records keep their wording.

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, delete the prefix
planned:test: from `planned:test:CrossTenantAccessTest` in the README's Tenant isolation row, so that the planned test
reads as if it existed. Then run:

```sh
./gradlew :tools:docs:check
```

It fails, and the output names the file, the line and the rule. Cleanup: put the prefix back, and run the same
command: it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-20, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change. With the fixture above,
`./gradlew :tools:docs:check` exited 1. The README is an input of the documentation tests, so they ran again and
failed first: the tests that copy the repository copy the edited README too. The excerpt shows the console's last
lines and the failure message that the test report records for `BrainCheckTest`'s unchanged-repository test:

```text
743 tests completed, 133 failed
> Task :tools:docs:test FAILED
BUILD FAILED in 1m 29s
io.github.stigjohnny.docs.DocsException: the documentation sources are inconsistent (1 problem):
  - README.md:404: `CrossTenantAccessTest` names no test class in the repository; if it is planned, write planned:test:CrossTenantAccessTest (ADR 0023)
```

After the cleanup, `./gradlew :tools:docs:generate` and then the same command exited 0. The generation had already run
the documentation tests, all passing, so the check reused them. The excerpt leaves out other tasks' lines and a
logging library's warnings:

```text
> Task :tools:docs:checkGenerated
docs: the 22 generated files are up to date

> Task :tools:docs:site
docs: wrote the site, 119 files

> Task :tools:docs:check

BUILD SUCCESSFUL in 3s
```

`ReferencesTest` checks each rule whenever the documentation tests run, as they do in every CI build; the check itself
runs in every `generate`, `checkGenerated` and site build. Before the check was written, 19 of its 38 tests failed
against an empty check: exactly the cases that must be rejected. Review: pull request #147.

## Limits

- The check proves names and markers, not claims: a sentence can still say that an existing test proves more than it
  does, and only a reviewer can tell.
- It covers test classes only. Capability ids, paths and the site's labels follow, in issues 144 to 146; until then the
  site shows the marker's prefix as it is written.
- Code blocks, records and superseded ADRs are not read; they keep the names they had when they were written.
