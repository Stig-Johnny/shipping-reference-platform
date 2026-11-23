---
title: "ADR 0023: Inline references carry explicit markers"
description: "A test class named in the prose's inline code must exist, a planned one is marked as planned where it is named and registered as planned, and the documentation check fails otherwise; capability ids, paths and the site's labels follow the same grammar."
status: accepted
updated: 2026-11-23
date: 2026-11-20
decision-makers: repository owner
enforcedBy:
  - "java: the documentation check fails when inline code names no test class, marks a test planned that exists or is not registered, or uses a marker the grammar does not define (ReferencesTest)"
  - "java: the documentation check fails when inline code names a capability or control id bare, names one the register lacks, or has the shape of an id that the register lacks (ReferencesTest)"
  - "java: the documentation check fails when inline code names a path that does not exist, exists only in another letter case or can be read two ways, or carries a planned, absent or illustrative path marker that does not hold (ReferencesTest)"
  - "java: the site shows a planned test or path with a planned label, an example with an example label, an absent path with an absent label, and a capability or control id without its prefix, in the documents the check reads; records keep their markers as written (MarkdownRendererTest, SitePagesTest)"
plannedEnforcement: []
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
NameTest                            a test class that exists in the repository
planned:test:NameTest               a planned test: not in the repository yet, and registered as planned
illustrative:test:<Name>            a schematic example; it holds a placeholder in angle brackets
cap:<id>                            a capability or control in docs/capabilities/capabilities.yaml
docs/gaps.md                        a path that exists: from the root, or from the document's directory
/architecture/  ./architecture/     a path from the root, and one from the document's directory
planned:path:policy/rulesets.json   a planned path: it does not exist yet
absent:path:tools/web-imports/      a path that is meant to be absent: it does not exist
illustrative:path:<path>            a schematic path; it holds a placeholder in angle brackets
```

A test is registered as planned when a capability or control that is not shipped lists it in `acceptanceTests`, or
when an ADR's `plannedEnforcement` names it. For example, `ReferencesTest` exists, and
`planned:test:CrossTenantAccessTest` is planned. A capability or control is named with its marker, such as
`cap:control.inline-references`, whatever its status; an id that the register has must carry the marker, and a bare
token with the shape of an id (a prefix the register uses, a dot and a hyphenated name) that the register lacks fails.
Any other span that starts with planned, illustrative or absent and a colon is a marker that the grammar does not
define, and fails.

### Paths

A span is a path when it is names joined by slashes, with an anchor or at least one slash: `docs/` is a path, and
the word docs is not. A name holds letters, digits, dots, underscores, hyphens, `*` for a glob and placeholders in
angle brackets, which hold anything but angle brackets and slashes, spaces included; an empty name, "." and ".." are
none. Where a path is read from is explicit:

- Written with a leading slash, it is read from the root, and only when its first name is a top-level directory, so
  that a route of the API is no path. Written with a leading `./`, it is read from the document's directory, always.
- Without an anchor, it is read from the root when its first name is a top-level directory, and otherwise from the
  document's directory when its first name is a directory there; otherwise it is no path. When its first name is a
  top-level directory, the root comes first: such a path that the document's directory also has is ambiguous and
  fails, and one that only the document's directory has fails until it is written with `./`. A path whose first name
  is only a directory beside the document is read from there, with or without `./`.
- It must exist in exact letter case, as on Linux; one that differs only in case fails, and so does one that writes
  a directory on its way in another case, with that directory named. A trailing slash names a directory, and a
  directory exists only when it holds a file, as for git. A pattern, a path with a glob or a placeholder, such as
  `docs/modules/<module>.md`, proves only that the directory before them exists, never that a file matches.
- A path through build output or tool state is never read: a directory named build outside a source tree, dist,
  node_modules, .gradle, .git or .claude. A marker on such a path fails, in any letter case, because nothing there
  can prove it, and a path that writes such a name in another letter case fails with the spelling to use. A link or another special file on the way fails too, in any letter case, and never counts as
  absent.
- A planned or an absent path must exist in neither reading, in no letter case, and its first name is no pattern; an
  illustrative path needs a placeholder, and nothing else about it is checked.

The check reads the working tree, untracked files included, so a file that is not committed can make a path pass on
one machine; a clean checkout, as in CI, decides. A path under a directory that does not exist, such as a planned
package, is no path unless it is marked, and a span such as a site address can read as a path when its first
directory exists; both depend on the context, and a reviewer reads them.

GitHub shows the markers as written. The site shows them as labels in the documents the check reads: a planned test
or path as its name followed by the word planned in a dashed box, an example as its name and the word example, an
absent path as its path and the word absent in a muted box, and a capability or control id without its cap: prefix. Records, generated files and superseded ADRs keep their markers as
written on the site too, because nothing checks theirs, and code blocks, such as the grammar above, always do.

Not read: code blocks, generated files, the records (the contribution history and runs, the demonstrations apart from
their how-to, the security reviews and verifications), superseded ADRs, and the directories that the instruction check
skips (ADR 0022). Records keep the names they had when they were written.

### Consequences

- Good, because a test that is renamed or removed fails the build wherever the prose still names it.
- Good, because a reader sees at the place of mention whether a test exists or is planned, and whether a path exists,
  is planned or is meant to be absent.
- Neutral, because GitHub shows the markers with their prefixes, and the site shows labels: one source, two
  presentations.
- Bad, because the check proves names and markers, not claims: a sentence can still say that an existing test proves
  more than it does, and only a reviewer can tell.
- Neutral, because a test that stays planned under a new name in both the prose and the register is not detected.
- Bad, because a path under a directory that does not exist is read only when it is marked, and a pattern proves its
  directory, not a matching file.
- Neutral, because build output and tool state are never read as paths, and the working tree, not git, decides what
  exists.

### Confirmation

`ReferencesTest` runs in every build of the documentation tests, and the check itself runs in every `generate`,
`checkGenerated` and site build. It fails on an unknown bare test name, on a planned marker for a test that exists or
that nothing registers, on a bare or unknown capability id, and on a marker the grammar does not define. For paths,
it fails on a path that does not exist, that differs only in letter case, that both readings find, or that starts
with a top-level directory but only the document's directory has without `./`; on a planned or absent path that
exists, on a marker on build output or tool state, on a link on the way, each in any letter case, and on an example
without a placeholder; it passes a pattern whose directory exists. It does not
read code blocks, records or superseded ADRs. `MarkdownRendererTest` and `SitePagesTest` prove the site's labels: the
name and the label wherever inline code can stand, the anchors of the source, no label made from a document's own
text, the markers as written in records, decks and code blocks, and every capability and control id in the shape the
site shows.
