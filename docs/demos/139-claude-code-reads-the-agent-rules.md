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

Before, Claude Code agents never saw this repository's rules. Claude Code loads `CLAUDE.md` files, not `AGENTS.md`,
and the repository had none, so a Claude agent started without the root rules, the four scoped rule files and the
pointer to `BRAIN.md` that the rules give first.

Now a `CLAUDE.md` beside each `AGENTS.md` imports it and nothing else. The root one loads when a session starts; a
scoped one loads only when the agent works in its directory. `CLAUDE.md` files are risk level 3, like `AGENTS.md`, in
`policy/risk.json` and `.github/CODEOWNERS`, and the root `AGENTS.md` says why they exist.

Issue 138 compared importing the rules alone with importing `BRAIN.md` as well, through Claude Code's own import
path, with Claude Haiku 5.5 and Claude Sonnet 5.5 on the questions of issues 135 and 137. The map added about a third
more tokens processed and no accuracy, so only the rules are imported; `AGENTS.md` already lists `BRAIN.md` first.

## See it

- [Risk levels](../risk-levels.md): what a level 3 change needs; `policy/risk.json` now puts `CLAUDE.md` there.
- [Contribution history](../contributions/history.md): the entry for issue 138.
- In the repository: the root `CLAUDE.md`, the opening paragraph of the root `AGENTS.md`, the `agent-instructions`
  rule in `policy/risk.json` and the agent-instructions lines of `.github/CODEOWNERS`. Issue 138 holds the
  validation table and its caveats; issues 135 and 137 hold the earlier measurements.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-19 with Claude Code 2.1.325 and Claude Haiku 5.5 in a copy of this pull request's first commit
(`9dae14c`). The host's own user-level `CLAUDE.md` was excluded with Claude Code's `claudeMdExcludes` setting, so only
the repository's files could load. The first run asked, without using a tool, which instruction files were in its
context; the second first read five lines of `apps/shipping-api/build.gradle.kts`, then answered the same question:

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

The validation in issue 138 compared `@AGENTS.md` alone (R1) with `@AGENTS.md` and `@BRAIN.md` (R2): 312 runs, three
repetitions of 14 lookups and 12 development tasks per model. Ratios are R2 against R1, the geometric mean of
per-question medians, with two-level bootstrap 95% intervals:

```text
lookups    Haiku   pass 42 / 41 of 42   total tokens 1.04 [0.89-1.34]   cost 0.83 [0.72-1.20]
lookups    Sonnet  pass 42 / 42 of 42   total tokens 1.32 [1.14-1.49]   cost 0.97 [0.71-1.78]
dev tasks  Haiku   pass 34 / 34 of 36   total tokens 1.33 [1.04-1.50]   cost 1.06 [0.86-1.26]
dev tasks  Sonnet  pass 36 / 35 of 36   total tokens 1.36 [1.19-1.54]   cost 0.99 [0.87-1.37]
```

## Limits

- Only Claude Code reads `CLAUDE.md`. Other agents still need to be pointed at `AGENTS.md`.
- No check yet requires a `CLAUDE.md` beside each `AGENTS.md`, or that a `CLAUDE.md` holds only the import; issue 136
  lists drift checks as its next step.
- The validation used two models, 26 questions and three repetitions, with scores near the ceiling. It found no
  detectable benefit from importing `BRAIN.md`, not proof that none exists, and every cost interval includes 1.
- The probe is one model's report of its own context, recorded once.
