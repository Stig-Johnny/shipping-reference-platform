---
title: "A missing or edited CLAUDE.md now fails the documentation check"
description: "With a root CLAUDE.md, Claude Code reaches each scoped AGENTS.md only through the CLAUDE.md beside it, so the documentation check now fails when that file is missing, adds rules of its own or has no AGENTS.md."
status: accepted
updated: 2026-11-20
pr: 142
kind: gate
recorded: 2026-11-20
tests: [AgentInstructionsTest]
---

## What changed

Before, nothing checked the five `CLAUDE.md` files that bring this repository's rules to Claude Code. A missing one,
or one that collected rules of its own, would have gone unnoticed. Because the root has a `CLAUDE.md`, Claude Code by
default reads `CLAUDE.md` files only, so a scoped `AGENTS.md` reaches Claude only through the `CLAUDE.md` beside it.

Now each `CLAUDE.md` holds exactly `@AGENTS.md`. The documentation check fails when an `AGENTS.md` has no `CLAUDE.md`
beside it, when a `CLAUDE.md` has no `AGENTS.md` or holds anything else, and when an instruction file is a link,
differs only in letter case or lies under `.claude/`. The same pull request corrects what `AGENTS.md`, ADR 0022 and
demo 139 said about how Claude Code reads `AGENTS.md`.

## Try it

You need the reviewed JDK, as for every Gradle command in the README. Fixture: in an editor, add the line
`Run the tests first.` to `apps/shipping-web/CLAUDE.md`. Then run:

```sh
./gradlew :tools:docs:check
```

It fails, and the documentation tool's test report names the file. Cleanup: remove the line again, so that the file
holds only `@AGENTS.md`, and run the same command: it passes.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-20, on macOS with the reviewed Temurin 25.0.5+8, at this pull request's change, after
`./gradlew :tools:docs:generate`. With the fixture above, `./gradlew :tools:docs:check` exited 1. Its documentation
tests were up to date from that generation, so Gradle reused them; the generated-files check and the site build then
each stopped on the check. The excerpt leaves out other tasks' lines, a logging library's three warnings before each
message, and Gradle's failure details:

```text
> Task :tools:docs:test UP-TO-DATE
> Task :tools:docs:checkGenerated FAILED
docs: the documentation sources are inconsistent (1 problem):
  - apps/shipping-web/CLAUDE.md must hold exactly '@AGENTS.md' and a line break, so that it adds no rules of its own (ADR 0022)

> Task :tools:docs:site FAILED
docs: the documentation sources are inconsistent (1 problem):
  - apps/shipping-web/CLAUDE.md must hold exactly '@AGENTS.md' and a line break, so that it adds no rules of its own (ADR 0022)

FAILURE: Build completed with 2 failures.
BUILD FAILED in 6s
```

When the tests are not up to date, as in CI, they fail first: the documentation tests that copy the repository copy
the edited file too. In an earlier run of this change, 106 of the 698 documentation tests failed that way.

After the cleanup, the same command exited 0. The excerpt leaves out other tasks' lines and the logging library's
warnings:

```text
> Task :tools:docs:checkGenerated
docs: the 22 generated files are up to date

> Task :tools:docs:site
docs: wrote the site, 117 files

> Task :tools:docs:check

BUILD SUCCESSFUL in 2s
```

`AgentInstructionsTest` checks every case in every build. Before the check was written, 23 of its 32 tests failed
against an empty check: exactly the cases that must be rejected.

Recorded separately, also on 2026-11-20: the probes behind the correction. Each was a headless `claude -p` run with
Claude Code 2.1.325 and Claude Haiku 5.5 (`claude-haiku-5-5`, effort medium) on a Mac mini running macOS. Each ran in a
copy of revision `3eb0677`, which has no `CLAUDE.md`, with `--setting-sources project`, `--strict-mcp-config`,
`--disable-slash-commands`, `--no-session-persistence` and the Read tool only. The prompt was "Without using any tool,
list every CLAUDE.md and AGENTS.md file whose text is in your context, one per line, as a path relative to the
repository root, and nothing else. If there is none, answer NONE."

The first excerpt compares the host's own configuration directory (`default-config`) with a fresh one
(`fresh-config`). Both used a settings file that excludes the host's user-level `CLAUDE.md`:

```text
### default-config
AGENTS.md
cost 0.0014988000000000002
### fresh-config
AGENTS.md
cost 0.0014516000000000002
```

The second excerpt shows two runs with `CLAUDE_CODE_DISABLE_CLAUDE_MDS=1`, the setting of the benchmark's setups A to H,
then one without it. All three used the fresh configuration directory. `tokens` is the input, cache-creation and
cache-read tokens of the run:

```text
### disable-claude-mds rep1
NONE
tokens 4765
### disable-claude-mds rep2
NONE
tokens 4765
### normal (for token comparison)
AGENTS.md
tokens 8823
```

Review: pull request #142.

## Limits

- The check proves which instruction files exist and their bytes, not what a Claude Code session loads or follows.
  `CLAUDE.local.md`, `.claude/rules/` and user, managed or ancestor instructions are outside it.
- It does not walk `.git`, `.gradle`, `build`, `node_modules` or `.claude/worktrees`, so it never reads an instruction
  file in one of them.
- The probes are one model's report of its own context, with one Claude Code version, recorded once for each setting.
  They do not compare reading `AGENTS.md` directly with importing it.
