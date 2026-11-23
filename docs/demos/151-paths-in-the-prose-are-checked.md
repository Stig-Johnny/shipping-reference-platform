---
title: "A path in the prose must exist, or say that it does not"
description: "Inline code that names a path now fails the documentation check unless the path exists, read from the root or the document's directory, or carries a planned, absent or example marker; the site shows those markers as labels."
status: accepted
updated: 2026-11-23
pr: 151
kind: gate
recorded: 2026-11-23
tests: [ReferencesTest, MarkdownRendererTest, SitePagesTest]
---

## What changed

Before, the prose named paths in inline code more than 400 times, and nothing checked them. Some named files that do not
exist, written exactly like existing ones: a planned branch ruleset, a script that was never built, a schema the
allowlist never got. One could be read two ways, from the root or from the document's own directory.

Now a path in current prose must exist. It is read from the root when its first name is a top-level directory, and
otherwise from the document's directory; `/architecture/` and `./architecture/self.dsl` choose the reading. When its
first name is a top-level directory, the root comes first: a path that both readings find fails, and so does one
that only the document's directory has, until it is written with `./`. A path that differs only in letter case fails
too. A path that does not exist carries its marker: `planned:path:policy/rulesets.json`,
`absent:path:tools/web-imports/`, or `illustrative:path:` with a placeholder. A pattern such as
`docs/modules/<module>.md` proves that its directory exists. Build output and tool state are never read. ADR 0023
records the rules. 28 mentions in current documents now carry a marker or an anchor, and the site shows the markers
as labels: planned in the dashed box, absent in a muted box, and example.

## See it

- [The README](../../README.md#layout): under Layout, in "Planned and not in the tree yet", `policy/rulesets.json`
  reads with the dashed planned box, and `tools/web-imports/` with the muted absent box.
- [The audit module](../modules/audit.md#risk-levels-of-its-paths-policyriskjson): the table of its paths shows two
  planned package paths with their boxes, and keeps a readable width on a phone.
- [ADR 0023](../adr/0023-inline-references-carry-explicit-markers.md#paths): the rules for paths; its grammar block
  keeps the markers as written.

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, delete the prefix
planned:path: from `planned:path:policy/rulesets.json` in the README's "Planned and not in the tree yet", so that the
planned file reads as if it existed. Then run:

```sh
./gradlew :tools:docs:check
```

It fails, and the output names the file, the line and the markers to use. Cleanup: put the prefix back, and run the
same command: it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-23, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change. With the fixture above,
`./gradlew :tools:docs:check` exited 1. The README is an input of the documentation tests, so they ran again and
failed first: the tests that copy the repository copy the edited README too. The excerpt shows the console's last
lines and the failure message that the test report records for `ReferencesTest`'s repository test. Other tests'
messages also named this demonstration, whose Evidence was still empty when the run was recorded:

```text
933 tests completed, 264 failed
> Task :tools:docs:test FAILED
BUILD FAILED in 2m 19s
Expecting empty but was: ["README.md:130: `policy/rulesets.json` names no file or directory from the repository's root; if it is planned, write planned:path:policy/rulesets.json, and if it is meant to be absent, absent:path:policy/rulesets.json (ADR 0023)"]
```

After the cleanup, `./gradlew :tools:docs:generate` exited 0 and rewrote the generated files, whose dates follow their
sources; then the same command exited 0. The generated files are inputs of the documentation tests, so the check ran
them again, all passing. The excerpt leaves out other tasks' lines, the API tests' log and a logging library's
warnings:

```text
> Task :tools:docs:test
> Task :tools:docs:checkGenerated
docs: the 22 generated files are up to date
> Task :tools:docs:site
docs: wrote the site, 122 files
> Task :tools:docs:check
BUILD SUCCESSFUL in 3m 41s
```

`ReferencesTest` checks each rule whenever the documentation tests run, as they do in every CI build; the check itself
runs in every `generate`, `checkGenerated` and site build. Before the path check was written, 69 of the 239 selected
tests failed against main's check: the path cases. Review: pull request #151.

## Limits

- A path under a directory that does not exist, such as a planned package, is read only when it is marked, and a span
  such as a site address can read as a path when its first directory exists.
- A pattern proves its directory, not a matching file.
- The check reads the working tree: a file that is not committed can make a path pass on the machine that has it. CI's
  clean checkout decides.
- The documentation tests' Gradle inputs do not name the application and tool sources, so a local test result can be
  reused after one of them changes; the check itself runs in every generate, checkGenerated and site build.
- Code blocks, records and superseded ADRs are not read; they keep the paths they had when they were written.
