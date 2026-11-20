---
title: "The measurements behind the agent entry point are a decision record"
description: "ADR 0022 records why Claude agents load the rules at start and read the map when they need it, with the measurements that chose it and their limits, so the reason no longer lives only in issue comments."
status: accepted
updated: 2026-11-20
pr: 140
kind: docs
recorded: 2026-11-20
tests: []
---

## What changed

Before, the measurements that chose the agent entry point of pull request 139, and the agent-facing part of the
site's redesign, were only in comments on issues 135, 137 and 138. Now ADR 0022 records the decision, the setups that
were measured, the results with their intervals, and the limits. `tools/docs-bench/` holds the questions, tasks,
answer keys, overrides and prompts that the runs used, and the known gaps name what is still unchecked.

## See it

- [ADR 0022](../adr/0022-agent-entry-point-and-documentation-lookup.md): the Decision Outcome, then the three tables
  under More Information.
- [The known gaps](../gaps.md): the entry "The agent entry point is not checked", under Documentation.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-20, on macOS with the reviewed Temurin 25.0.5+8, `./gradlew :tools:docs:generate` added ADR 0022 to the
decisions in the regenerated `BRAIN.md`:

```text
| [ADR 0022: Agents load the rules at start, and the map stays one read away](docs/adr/0022-agent-entry-point-and-documentation-lookup.md) | accepted | not enforced yet |
```

Then `./gradlew check` exited 0. Read and checked: the ADR's page in the site that it built, at 1280 and 390 pixels
in Chrome, and the numbers in its tables against the result comments on issues 135, 137 and 138. Review: pull
request #140.

## Limits

- The measurements are historical. This site build does not rerun them, and no test compares the ADR's tables with the
  data in `tools/docs-bench/`.
- The harness that made them is not in the repository, so they cannot be repeated from it yet (issue 135).
