---
title: "ADR 0022: Agents load the rules at start, and the map stays one read away"
description: "Measured with two models on the command line: a CLAUDE.md beside each AGENTS.md imports the rules unchanged, BRAIN.md is read when needed instead of loaded at start, a section-level search is the next aid to evaluate, and the site's engine is chosen for people."
status: accepted
updated: 2026-11-20
date: 2026-11-19
decision-makers: orchestrating agent, under the repository owner's standing delegation
enforcedBy: [not enforced yet]
plannedEnforcement:
  - "docs: a check that every AGENTS.md has a CLAUDE.md beside it whose only content is its import (issue 136, step 4)"
  - "docs: the section records of issue 136, step 3, with checks for a stale index, bounded output and published content only"
---

# ADR 0022: Agents load the rules at start, and the map stays one read away

## Context and Problem Statement

Agents that work on this repository start from what their tool loads. The documentation's goals put working well for
agents first, then no drift between code and documents, then being readable for people (issue 136). Claude Code
loads `CLAUDE.md` files but not `AGENTS.md`, and until pull request 139 the repository had no `CLAUDE.md`: unless
someone gave them the rules, Claude agents started without them, and without the pointer to the generated map,
`BRAIN.md`, that the rules give first. The site was also about to be redesigned (issue 134), with Quartz 5 suggested
as the engine of its prose pages.

Two questions followed. What should an agent have when it starts? And which documentation layout lets small models,
working from the command line, find what they need for development work with the fewest tokens?

## Decision Drivers

- Measure instead of assuming: the correctness and cost of real headless runs, each in a fresh copy of the repository.
- The rules keep their meaning: an agent gets `AGENTS.md` as written, never a digest of it.
- What loads at start is paid for in every run, so it has to earn its place.
- One source of truth with generated views (ADR 0011).

## Considered Options

Each option was measured as a setup; `tools/docs-bench/README.md` describes the method.

1. Nothing besides the repository (setup A).
2. A pointer to `BRAIN.md` in the prompt (B).
3. A section-level search command, `./docs-search`, that prints the five best documentation sections as `path:line`
   with a snippet (C), with or without the pointer (D).
4. A Quartz-format full-text index queried with `jq` (E), or a page-level search over it (F).
5. `BRAIN.md` preloaded at start, with the search (G) or without it (H).
6. A root `CLAUDE.md` that imports `AGENTS.md` (R1), or `AGENTS.md` and `BRAIN.md` (R2), through Claude Code's own
   import.

## Decision Outcome

Chosen: option 6 with `AGENTS.md` alone (R1), and the search of option 3 as the next aid to evaluate.

1. **Rules at start.** A `CLAUDE.md` beside each `AGENTS.md` imports it unchanged and holds nothing else. Pull
   request 139 added them; a scoped pair loads when an agent works in its directory.
2. **The map one read away.** `BRAIN.md` is neither imported nor preloaded. `AGENTS.md` lists it first under "Read
   before you change anything", so the rule to read it before a change stands. Preloading it cut the cost of
   single-fact lookups, but on development tasks it raised the total tokens Sonnet processed, and through the real
   import it brought no gain in passes.
3. **Search next, measured first.** The next agent-facing addition is a section-level search over records that the
   docs tool generates (issue 136, step 3). It is a candidate, not a proven winner: search alone cut the cost of
   lookups roughly in half, but on development tasks it gave Haiku the lowest pass rate. `AGENTS.md` recommends it
   only after it has been measured against the bare setup on both task sets.
4. **The site's engine is chosen for people.** Issue 134 chooses it on usability, accessibility and upkeep. These
   measurements give agents no reason to prefer Quartz: they tested an index in Quartz's format, not Quartz, and ADR
   0011's generator stays until a decision replaces it.

### Consequences

- Good, because Claude agents get the rules when they start, and a directory's scoped rules when they work there,
  without anyone pasting them.
- Good, because start-up context stays at the rules: importing `BRAIN.md` as well raised the total tokens processed
  in all four pairs of task set and model, three of them clearly.
- Bad, because only Claude Code reads `CLAUDE.md`; other agents still need to be pointed at `AGENTS.md`, and nothing
  checks the pairs yet.
- Bad, because the evidence is thin: one repository, two models, three repetitions and pass rates near the ceiling,
  so small differences cannot be seen.
- Neutral, because a pointer to `BRAIN.md` costs little and saved nothing measurable.

### Confirmation

Not enforced yet. Planned: a docs check that every `AGENTS.md` has a `CLAUDE.md` beside it whose only content is its
import (issue 136, step 4), and the checks of the section records (step 3). The measurements are historical evidence
from 19 November 2026; no build reruns them. They are repeated, as a new version of the comparison, when the map, the
task mix, the models or Claude Code's import behaviour change substantively.

## More Information

### How the measurements were made

On 19 November 2026, Claude Code 2.1.325 on a separate macOS host ran Claude Haiku 5.5 and Claude Sonnet 5.5 headless
(`claude -p`, effort medium), with read-only tools, no MCP servers and no skills, in a fresh copy of revision
`3eb0677` for each setup, three repetitions of each question. A run passes when its answer matches every answer key
and no forbidden pattern. Cost is the API-equivalent cost that Claude Code reported, not a bill. Total tokens include
cache reads. Each ratio compares two setups per question and model: the geometric mean over the questions of the
ratio of medians, with a 95% interval from a two-level bootstrap over questions and repetitions. The intervals are
exploratory. A first run was discarded because the host's own user-level `CLAUDE.md` had been loaded into every run.
The method, the questions, the tasks and the keys are in `tools/docs-bench/`; the harness that ran them is not in the
repository yet (issue 135).

### Single-fact lookups (issue 135)

14 questions, 672 runs, $6.06. The keys were fixed before the run, and two answers were marked wrong by hand.

| Setup | Haiku passed | Haiku cost vs A | Sonnet passed | Sonnet cost vs A |
|---|---|---|---|---|
| A, nothing | 42/42 | 1.00 | 42/42 | 1.00 |
| B, pointer to `BRAIN.md` | 42/42 | 0.92 [0.65–1.22] | 42/42 | 0.91 [0.69–1.28] |
| C, section search | 42/42 | 0.51 [0.39–0.67] | 42/42 | 0.57 [0.46–0.74] |
| D, pointer and search | 42/42 | 0.72 [0.57–0.95] | 42/42 | 0.72 [0.56–0.98] |
| E, Quartz-format index with `jq` | 42/42 | 0.97 [0.80–1.18] | 41/42 | 1.01 [0.85–1.21] |
| F, page search over that index | 41/42 | 0.67 [0.53–0.85] | 42/42 | 0.70 [0.51–0.93] |
| G, `BRAIN.md` preloaded, and search | 42/42 | 0.49 [0.37–0.70] | 42/42 | 0.44 [0.32–0.67] |
| H, `BRAIN.md` preloaded | 42/42 | 0.55 [0.42–0.78] | 42/42 | 0.38 [0.29–0.62] |

The savings are mostly in uncached input and output. Against A, the total tokens processed fell clearly only for Haiku
with search: 0.72 [0.59–0.94]. With the pointer, Sonnet opened `BRAIN.md` in 6 of 42 runs. Both failures counted the
reverse dependency edges on the shipments brain page as the module's own dependencies.

### Multi-file development tasks (issue 137)

12 tasks that a separate reviewer wrote and keyed from the repository alone, 288 runs, $5.89, answers of up to eight
sentences. Two keys were widened after the run, so passes are shown before and after.

| Model | Setup | Passed, keys before → after | Cost vs A | Total tokens vs A |
|---|---|---|---|---|
| Haiku | A | 34 → 35 of 36 | 1.00 | 1.00 |
| Haiku | C | 29 → 30 of 36 | 0.78 [0.66–0.89] | 1.02 [0.78–1.19] |
| Haiku | G | 31 → 33 of 36 | 0.88 [0.73–1.04] | 1.19 [0.94–1.40] |
| Haiku | H | 32 → 35 of 36 | 0.84 [0.74–1.01] | 1.08 [0.86–1.32] |
| Sonnet | A | 33 → 35 of 36 | 1.00 | 1.00 |
| Sonnet | C | 32 → 35 of 36 | 0.85 [0.71–1.03] | 1.17 [0.97–1.48] |
| Sonnet | G | 34 → 35 of 36 | 0.77 [0.62–0.94] | 1.49 [1.16–1.77] |
| Sonnet | H | 33 → 36 of 36 | 0.95 [0.85–1.09] | 1.33 [1.17–1.59] |

On these tasks the savings shrink to between 5 and 23%, and several intervals include no saving. For Sonnet,
preloading `BRAIN.md` raised the total tokens processed (H 1.33, G 1.49), mostly through repeated cache reads of the
same prefix; this is not the peak context. Search alone gave Haiku the lowest pass rate.

### The real import (issue 138)

R1 imports `AGENTS.md`; R2 imports `AGENTS.md` and `BRAIN.md`. Both task sets with the amended keys, 312 runs, $5.87,
with only the host's own user-level `CLAUDE.md` excluded.

| Task set | Model | Passed, R1 / R2 | Total tokens, R2 vs R1 | Cost, R2 vs R1 |
|---|---|---|---|---|
| Lookups | Haiku | 42 / 41 of 42 | 1.04 [0.89–1.34] | 0.83 [0.72–1.20] |
| Lookups | Sonnet | 42 / 42 of 42 | 1.32 [1.14–1.49] | 0.97 [0.71–1.78] |
| Development tasks | Haiku | 34 / 34 of 36 | 1.33 [1.04–1.50] | 1.06 [0.86–1.26] |
| Development tasks | Sonnet | 36 / 35 of 36 | 1.36 [1.19–1.54] | 0.99 [0.87–1.37] |

Every cost interval includes 1, so cost does not decide between them. This measures the automatic import, not how
useful `BRAIN.md` is when an agent reads it.

### Limits

- One repository at one revision, two models, three repetitions, one version and configuration of Claude Code.
- The lookups were written by the author of the search command; the development tasks were not.
- Pass rates are near the ceiling, so the runs cannot show small differences in correctness.
- The ratios hold for the cache behaviour observed. Cold starts and the upkeep of an index were not measured.
- Costs are the API-equivalent estimates Claude Code reported. The runs used a subscription, so they are not what was
  billed.
