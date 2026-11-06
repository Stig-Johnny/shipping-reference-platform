---
title: "ADR 0001: Record architecture decisions as MADR files"
description: "Every significant decision is a numbered MADR file in docs/adr with status, context, decision, consequences and the check that confirms it."
status: accepted
updated: 2026-11-06
date: 2026-11-06
decision-makers: repository owner
enforcedBy:
  - "java: :tools:docs:checkGenerated validates ADR frontmatter, numbering, required sections and enforcedBy test classes (BrainCheckTest)"
plannedEnforcement:
  - "CODEOWNERS and risk level 3 on docs/adr/"
---

# ADR 0001: Record architecture decisions as MADR files

## Context and Problem Statement

This repository exists to show how decisions turn into enforced controls. People reviewing it, and AI agents
changing it, need to find why a boundary, tool or rule exists before they change it. Decisions kept in chat logs,
issue threads or memory are lost or contradicted. How do we record decisions so that they are found, reviewed and
tied to the check that enforces them?

## Decision Drivers

- Agents read files, not conversations; the generated brain (ADR 0010) needs machine-readable decision metadata.
- A decision without a named check tends to be forgotten; the record must say how it is confirmed.
- Changing a decision must be visible and approved, not a silent edit.

## Considered Options

1. MADR 4.0 Markdown files in `docs/adr/`, with YAML frontmatter and a mandatory Confirmation section.
2. A wiki or external document store.
3. Decisions recorded only in pull request descriptions.

## Decision Outcome

Chosen option 1.

- File name: `docs/adr/NNNN-kebab-case-title.md`, numbered from `0001` without gaps or reuse.
- Frontmatter: `title`, `description`, `status`, `updated`, `date`, `decision-makers`, `enforcedBy`,
  `plannedEnforcement`. `status` is one of `proposed`, `accepted`, `rejected`, `deprecated`, `superseded`; a
  superseded ADR adds `supersededBy: "NNNN"`. `enforcedBy` lists implemented enforcement with passing evidence, or
  `[not enforced yet]`. `plannedEnforcement` lists intended checks and tests; these may be absent. Promote entries
  only when implemented and verified.
- Required sections: Context and Problem Statement, Considered Options, Decision Outcome, Consequences,
  Confirmation. Decision Drivers and More Information are optional.
- An accepted ADR is not rewritten in substance. A changed decision gets a new ADR that supersedes the old one; the
  old file only changes its `status` and `supersededBy`.
- Names of people and companies do not appear in ADRs; `decision-makers` names roles.

### Consequences

- Good, because the reason for each control sits next to the control and is reviewed like code.
- Good, because the brain can list decisions per module and flag ADRs whose confirmation is `not enforced yet`.
- Bad, because writing a short ADR adds work to decision-making pull requests.
- Neutral, because `docs/adr/` is a level 3 path: every ADR change needs the owner's approval.

### Confirmation

- `java` runs `:tools:docs:checkGenerated` (ADR 0010), which fails when an ADR lacks a required frontmatter field or
  section, uses an unknown status, breaks the numbering, or names an `enforcedBy` test class that does not exist in
  the repository (`BrainCheckTest`).
- `.github/CODEOWNERS` assigns `docs/adr/` to the owner, and `policy/risk.json` classifies it as level 3.

## More Information

- MADR: <https://adr.github.io/madr/>
- Index of decisions: ADR 0001 to ADR 0010 in this directory.
