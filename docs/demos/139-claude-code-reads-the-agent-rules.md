---
title: "Claude Code reads the agent rules"
description: "A CLAUDE.md beside each AGENTS.md imports it, so Claude agents get this repository's rules: the root rules when a session starts, a directory's scoped rules when they work there."
status: accepted
updated: 2026-11-19
pr: 139
kind: docs
recorded: 2026-11-19
tests: []
---

## What changed

Before, Claude Code did not load this repository's rules automatically. It loads `CLAUDE.md` files, not `AGENTS.md`,
and the repository had none, so unless someone told it to read them, a Claude agent started without the root rules,
the four scoped rule files and the pointer to `BRAIN.md` that the rules give first.

Now a `CLAUDE.md` beside each `AGENTS.md` imports it and nothing else. The root one loads when a session starts; a
scoped one loads only when the agent works in its directory. `CLAUDE.md` files are risk level 3, like `AGENTS.md`, in
`policy/risk.json` and `.github/CODEOWNERS`, and the root `AGENTS.md` says why they exist.

Issue 138 compared importing the rules alone with importing `BRAIN.md` as well, through Claude Code's own import
path, with Claude Haiku 5.5 and Claude Sonnet 5.5 on the questions of issues 135 and 137. Importing the map raised the
tokens processed to 1.32 to 1.36 times for Sonnet on both question sets and for Haiku on the development tasks, made
no clear difference for Haiku's lookups (1.04), and showed no accuracy improvement in this sample. So only the rules
are imported; `AGENTS.md` already lists `BRAIN.md` first.

## See it

- [Risk levels](../risk-levels.md): what a level 3 change needs; `policy/risk.json` now puts `CLAUDE.md` there.
- [Contribution history](../contributions/history.md): the entry for issue 138.
- In the repository: the root `CLAUDE.md`, the opening paragraph of the root `AGENTS.md`, the `agent-instructions`
  rule in `policy/risk.json` and the agent-instructions lines of `.github/CODEOWNERS`. Issue 138 holds the
  validation table and its caveats; issues 135 and 137 hold the earlier measurements.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-19 on a Mac mini running macOS, with Claude Code 2.1.325 and Claude Haiku 5.5 (`claude-haiku-5-5`,
effort medium), in a copy of this pull request's first commit (`9dae14c`). Each run was a headless `claude -p` with
`--setting-sources project`, `--strict-mcp-config`, `--disable-slash-commands`, `--no-session-persistence`, the Read
tool only, and a settings file whose `claudeMdExcludes` named the host's own user-level `CLAUDE.md`. So no user
settings, MCP servers or skills loaded, and the host's instruction file was the one file excluded. Both runs exited 0.
The first prompt was "Without using any tool, list every CLAUDE.md and AGENTS.md file whose text is in your context,
one per line, as a path relative to the repository root, and nothing else." The second first asked the agent to read
five lines of `apps/shipping-api/build.gradle.kts` with the Read tool, then asked the same. The excerpt shows both
answers in full, under the headings the script printed:

```text
### start
CLAUDE.md
AGENTS.md
### after reading apps/shipping-api/build.gradle.kts
CLAUDE.md
AGENTS.md
apps/shipping-api/CLAUDE.md
apps/shipping-api/AGENTS.md
```

Summary of issue 138's validation, which compared `@AGENTS.md` alone (R1) with `@AGENTS.md` and `@BRAIN.md` (R2) in
312 runs, three repetitions of 14 lookups and 12 development tasks per model. Ratios are R2 against R1, the geometric
mean of per-question medians, with two-level bootstrap 95% intervals; the issue holds the method and caveats.

| Questions | Model | Passed, R1 / R2 | Total tokens, R2 vs R1 | Cost, R2 vs R1 |
|---|---|---|---|---|
| Lookups | Haiku | 42 / 41 of 42 | 1.04 [0.89–1.34] | 0.83 [0.72–1.20] |
| Lookups | Sonnet | 42 / 42 of 42 | 1.32 [1.14–1.49] | 0.97 [0.71–1.78] |
| Development tasks | Haiku | 34 / 34 of 36 | 1.33 [1.04–1.50] | 1.06 [0.86–1.26] |
| Development tasks | Sonnet | 36 / 35 of 36 | 1.36 [1.19–1.54] | 0.99 [0.87–1.37] |

Read and checked: this demo's page in the site that `./gradlew check` built (exit 0), at 1280 pixels in Chrome, and
the entry for issue 138 in the [contribution history](../contributions/history.md). Review: pull request #139.

## Limits

- Only Claude Code reads `CLAUDE.md`. Other agents still need to be pointed at `AGENTS.md`.
- No check yet requires a `CLAUDE.md` beside each `AGENTS.md`, or that a `CLAUDE.md` holds only the import; issue 136
  lists drift checks as its next step.
- The validation used two models, 26 questions and three repetitions, with scores near the ceiling. It found no
  detectable benefit from importing `BRAIN.md`, not proof that none exists, and every cost interval includes 1.
- The probe is one model's report of its own context, recorded once.
