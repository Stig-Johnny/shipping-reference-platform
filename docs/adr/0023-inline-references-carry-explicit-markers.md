---
title: "ADR 0023: Inline references carry explicit markers"
description: "A test class named in the prose's inline code must exist, a planned one is marked as planned where it is named and registered as planned, and the documentation check fails otherwise; capability ids, paths and the site's labels follow the same grammar."
status: accepted
updated: 2026-11-21
date: 2026-11-20
decision-makers: repository owner
enforcedBy:
  - "java: the documentation check fails when inline code names no test class, marks a test planned that exists or is not registered, or uses a marker the grammar does not define (ReferencesTest)"
  - "java: the documentation check fails when inline code names a capability or control id bare, names one the register lacks, or has the shape of an id that the register lacks (ReferencesTest)"
plannedEnforcement:
  - "docs: the site shows markers as labels (issue 145)"
  - "docs: paths with the planned:path:, absent:path: and illustrative:path: markers (issue 146)"
---

# ADR 0023: Inline references carry explicit markers

## Context and Problem Statement

The prose names test classes, capability ids and paths in inline code, and nothing checks those names. In the
hand-written Markdown on 2026-11-20, 436 spans named test classes. 51 of them named a class that does not exist; all
22 distinct names were planned tests. A renamed test, or a planned test written as if it existed, went unnoticed, and
a reader, person or agent, could not tell a current claim from a plan. Issue 136 asks for no drift between code and
documents, and its fourth step for an explicit reference syntax that separates current claims from planned, historical
and illustrative ones.

## Decision Drivers

- A name in the prose must not drift from the code it names.
- A reader, an agent above all, must see at the place of mention whether a name is current or planned.
- The register of what is planned stays where it is: `docs/capabilities/capabilities.yaml` and the ADRs' planned
  enforcement.
- The prose stays readable on GitHub and on the site, without link schemes that GitHub cannot follow.

## Considered Options

1. Check every inline code span that has the shape of a reference, and accept an unknown test name when the register
   lists it as planned.
2. Explicit markers in inline code: a bare name is current and must exist; a planned one carries a marker where it is
   named.
3. References as Markdown links with their own schemes.

## Decision Outcome

Chosen: option 2. Option 1 cannot tell a current claim from a plan: a planned test written as if it existed passes.
Option 3 makes links that GitHub cannot follow. The repository owner chose explicit markers on 2026-11-20.

The grammar, for inline code in hand-written Markdown:

```text
NameTest                     a test class that exists in the repository
planned:test:NameTest        a planned test: not in the repository yet, and registered as planned
illustrative:test:<Name>     a schematic example; it holds a placeholder in angle brackets
cap:<id>                     a capability or control in docs/capabilities/capabilities.yaml
```

A test is registered as planned when a capability or control that is not shipped lists it in `acceptanceTests`, or
when an ADR's `plannedEnforcement` names it. For example, `ReferencesTest` exists, and
`planned:test:CrossTenantAccessTest` is planned. A capability or control is named with its marker, such as
`cap:control.inline-references`, whatever its status; an id that the register has must carry the marker, and a bare
token with the shape of an id (a prefix the register uses, a dot and a hyphenated name) that the register lacks fails.
Any other span that starts with planned, illustrative or absent and a colon is a marker that the grammar does not
define, and fails until a later issue defines it: paths (issue 146). The site shows markers as labels instead of the
raw prefix (issue 145).

Not read: code blocks, generated files, the records (the contribution history and runs, the demonstrations apart from
their how-to, the security reviews and verifications), superseded ADRs, and the directories that the instruction check
skips (ADR 0022). Records keep the names they had when they were written.

### Consequences

- Good, because a test that is renamed or removed fails the build wherever the prose still names it.
- Good, because a reader sees at the place of mention whether a test exists or is planned.
- Bad, because the prose carries a visible prefix until the site shows it as a label.
- Bad, because the check proves names and markers, not claims: a sentence can still say that an existing test proves
  more than it does, and only a reviewer can tell.
- Neutral, because a test that stays planned under a new name in both the prose and the register is not detected.

### Confirmation

`ReferencesTest` runs in every build of the documentation tests, and the check itself runs in every `generate`,
`checkGenerated` and site build. It fails on an unknown bare test name, on a planned marker for a test that exists or
that nothing registers, on a bare or unknown capability id, and on a marker the grammar does not define. It does not
read code blocks, records or superseded ADRs. Planned: the markers for paths, and the site's labels (issues 145 and
146).
