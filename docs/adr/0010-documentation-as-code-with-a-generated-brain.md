---
title: "ADR 0010: Documentation as code with a generated brain"
description: "Docs live beside the code with frontmatter; a deterministic generator builds BRAIN.md and per-module brains within size budgets, and CI fails on drift."
status: accepted
updated: 2026-11-06
date: 2026-11-06
decision-makers: repository owner
enforcedBy: [not enforced yet]
plannedEnforcement:
  - "docs-brain: brain --check (determinism, references, status evidence, budgets) and the VitePress build"
  - "tools/brain tests: BrainGeneratorTest, BrainCheckTest"
---

# ADR 0010: Documentation as code with a generated brain

## Context and Problem Statement

Agents start every task without memory. They need a short, current summary of the system, and they need to know
which capabilities are shipped and which are only planned. Hand-maintained summaries go stale and overclaim. How is
documentation kept current and honest, and how do agents get a compact entry point?

## Decision Drivers

- Documentation changes in the same pull request as the behaviour it describes.
- A compact entry point for agents that never claims more than the evidence supports.
- Generated content that is reproducible byte for byte.

## Considered Options

1. Markdown with frontmatter in the repository, a VitePress site, and a generated, budgeted brain checked in CI.
2. An external wiki.
3. Hand-written agent instructions only.

## Decision Outcome

Chosen option 1.

- Every Markdown file under `docs/` starts with YAML frontmatter: `title`, `description`, `status` (`draft`,
  `proposed`, `accepted`, `rejected`, `deprecated`, `superseded`) and `updated` (ISO date). Module documents also
  carry `module`, `moduleStatus` (equal to the component's `contract.status`) and `riskLevel`. Any pull request that
  edits a document updates its `updated` date.
- A pull request that changes a module's production code or tests also changes at least one document whose
  frontmatter `module` (or list `modules`) names that module, or the pull request body gives a reason under "Docs: none needed because".
- The site is built with VitePress 1.6.4 from the `docs` npm workspace. Generated pages: C4 views and module
  pages, produced by the repository's own docs tool from `architecture/workspace.dsl` and the verified module
  structure (not by Spring Modulith), OpenAPI pages, checker reports, `llms.txt` and Markdown counterparts of every
  page.
- The brain generator is a Java program, Gradle project `:tools:brain`. It reads the model through
  `:libs:c4-contract-kit`, `docs/capabilities/capabilities.yaml` (SnakeYAML, proposed and requiring its own
  artifact review and allowlist entry), ADR and module frontmatter, `policy/risk.json`, a summary of `security/allowlist.json`, `docs/flows/` and
  `docs/gaps.md`. It writes `BRAIN.md` (root router, at most 12 KiB UTF-8) and `docs/brain/<module>.md` (at most
  8 KiB each). It links detailed reviews instead of copying them. Every output contains
  `Generated—do not hand-edit`. Generated Markdown under docs/ starts with the required YAML frontmatter, followed by
  that notice.
- `brain --check` regenerates into a temporary directory and fails on any byte difference, on a broken reference
  (file, ADR, test class, check name), on a capability marked `shipped` or `partial` whose evidence is missing, and on
  an exceeded budget. A planned capability never gets a green badge because its package exists.
- The interactive C4 map (capability, risk and evidence overlays, "Copy agent context", an equivalent
  keyboard-accessible list) is rendered from the same data. Mermaid is reserved for flows and the README overview.
- Pages stay private until an independent review; public Pages and production deployment are later decisions.

### Consequences

- Good, because the brain cannot silently claim a capability the tests do not show.
- Good, because agents get a bounded, current context instead of a growing instruction file.
- Bad, because every documentation change runs the generator; a stale `updated` date or a missing link fails CI.
- Bad, because a summary within a byte budget leaves things out; the brain links to the full documents.

### Confirmation

- `docs-brain` (required check) runs `brain --check` and the VitePress build.
- `BrainGeneratorTest` (determinism, ordering, budgets, the do-not-edit line) and `BrainCheckTest` (negative
  fixtures: shipped capability without evidence, broken link, edited output, exceeded budget, stale `moduleStatus`)
  in `:tools:brain`.

## More Information

- VitePress 1.6.4: <https://github.com/vuejs/vitepress/releases/tag/v1.6.4>
- Capabilities: `docs/capabilities/capabilities.yaml`; gaps: `docs/gaps.md`.
