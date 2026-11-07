---
title: "How to write a demo"
description: "When a pull request needs a public demonstration, which kind it is, the template to copy, what each section holds, and the checks the documentation tool applies."
status: accepted
updated: 2026-11-07
---

# How to write a demo

A demonstration, a demo for short, shows a reader who does not read code what one pull request changed: what can now
be seen or run, the evidence, and what it does not prove. Each demo is a sheet of the documentation site's Demos
section. Its first lines, the outcome and why it matters, are what a non-developer reads first, so write them for
that reader.

## When a demo is needed

- Every pull request that needs the owner's approval (risk level 3, see [the risk levels](../risk-levels.md)), and
  every pull request that changes what a reader sees on the site or in the app, supplies a demo. Other pull requests
  may add one; the pull request says why none is needed. The rule is in `AGENTS.md`.
- A demo is proportionate: as long as the change needs, no longer. A small change can have a very short one: two
  sentences on what changed, one recorded output, one limit.
- One demo per pull request, written with the change, in the same pull request. Open the pull request first: the
  file is named after its number, `docs/demos/<pr>-<slug>.md`, such as
  `docs/demos/42-model-edits-rerun-the-checks.md`. The site publishes it at `demos/<pr>-<slug>.html`, as sheet
  `X-<pr>`.
- `recorded` is the date the evidence was captured. It never changes afterwards, and the register of demos sorts by
  it, newest first. `updated` changes with every edit, as in any document, and never moves a demo in the register.

## Which kind

| Kind | When | Sections besides What changed, Evidence and Limits |
|---|---|---|
| `visual` | The site or the app looks or reads differently. | See it |
| `docs` | Documentation changed. | See it |
| `gate` | A check now rejects what it let through, or accepts what it rejected. | Try it |
| `build` | The build behaves differently: what runs, when, or with which inputs. | Try it |
| `feature` | The product can do something new. | See it, Try it or both |

A demo may have a section its kind does not need, such as Try it for a visual change that a command reproduces.

## The template

Copy it into `docs/demos/<pr>-<slug>.md`, then replace every `<...>` and every `YYYY-MM-DD`: the check fails on any
that is left. Remove the sections the kind does not need. `tests` may stay empty.

````markdown
---
title: "<observable outcome>"
description: "<why it matters>"
status: accepted
updated: YYYY-MM-DD
pr: <positive integer>
kind: <visual|gate|build|docs|feature>
recorded: YYYY-MM-DD
tests: []
---

## What changed
<Before and after, in plain words.>

## See it
<Published page link and what to inspect.>

## Try it
<Prerequisites, repository-root command, fixture and cleanup.>
```sh
<approved command>
```

## Evidence
Recorded output — not rerun by this site build.
<Capture date, environment, procedure and exit status.>
```text
<verbatim output excerpt>
```
<Review reference and separately identified current-build tests.>

## Limits
<What this demonstration does not establish.>
````

The title states the outcome a reader can observe ("Architecture edits now rerun the checks"), not the work done.
The description says in one sentence why it matters.

## What each section holds

- **What changed:** before and after, in plain words.
- **See it:** links to the pages of this site that show the result, and what to look at on each. Link a page by its
  source document, such as `[how the site is drawn](../ui/documentation-site.md)`, or by the site's address, such as
  `https://stig-johnny.github.io/shipping-reference-platform/index.html#reading-paths` for a page without a source
  document. The site turns both into links to its own pages. Every link in See it must open a page of this site: a
  file the site does not publish would show as plain text, and a download or another site is no page to inspect.
- **Try it:** what is needed first, the commands, run from the repository root, in a `sh` fence, the fixture to set
  up and how to remove it afterwards.
- **Evidence:** first the line "Recorded output — not rerun by this site build.", then the capture date (the
  `recorded` date), the environment (machine, operating system, JDK), the fixture or change, the command and its exit
  status, and verbatim excerpts of the output in `text` fences after that line. Say what an excerpt leaves out, such
  as "the download log is omitted". Name the review as plain text, "pull request #42", never as a link to the
  repository, which the publication scan refuses. Name the test classes that check the change in every build under
  `tests`: they count as evidence of the build that made the site, and only when they passed in it.
  - A gate's evidence shows a rejected case and an accepted case: two recorded outputs, each with its exit status.
  - A build's evidence shows the build property, such as a task that runs again after one of its inputs changed.
  - A visual change's evidence is an inspection record: what was looked at, in which browser and at which width, and
    what was seen, with a link to the published page that shows it.
  - A documentation change's evidence says what was read and checked, with a link to the page.
- **Limits:** what the demo does not establish, such as other screen sizes, other inputs or behaviour that no test
  covers.

Excerpts are published as they are pasted. Take out tokens, local paths, private addresses and names before pasting:
the publication scan refuses the shapes it knows, and a person reads every page that changes.

## Commands

A `sh` fence holds commands only, one per line. A line that ends with a backslash continues on the next, as in the
shell: the backslash and the line break are removed and nothing takes their place, so write the space before the
backslash. Each command is one of these, whole, with exactly these arguments. They are the commands that
[the README documents](../../README.md#try-it):

- `java tools/policy/PolicyCheck.java .`
- `./gradlew check`
- `./gradlew :tools:docs:check`
- `./gradlew :tools:docs:generate`
- `./gradlew :tools:docs:site`
- `git checkout -- libs/c4-contract-kit/architecture/self.dsl`
- `./gradlew :libs:c4-contract-kit:test --rerun --tests '*<TestClass>'`, with one or more `--tests '*<TestClass>'`,
  each naming a test class of `libs/c4-contract-kit` (its name ends in `Test`)

Anything else fails the check: another command or task, an extra argument, chaining (`;`, `&&`, `||`, `&`), a pipe, a
redirection, a substitution or variable (`$`, backticks), a comment, a prompt, an absolute path. PIT's mutation tests
are not on the list: they run in CI only. Output goes in a `text` fence, never in a `sh` one.

## Text only, until the media gate

Screenshots, screen recordings, videos and other media wait for a reviewed media gate, a decision of its own that
does not exist yet. Until then a demo is text only: no image, no embedded page or video, no raw HTML and no link to a
media file, and the check rejects each of them. The screenshots of a visible change go to the owner with the review
request, outside the repository; the public page describes in words what they show.

## What the tool checks

The documentation tool checks every demo with the other documents (`./gradlew :tools:docs:check`, part of
`./gradlew check`). It fails on:

- a file under `docs/demos/` other than this one that is not named `docs/demos/<pr>-<slug>.md`, and any file there
  that is not Markdown: the repository is public as well, so a screenshot committed there would be published;
- frontmatter beyond the documents' four keys and `pr`, `kind`, `recorded` and `tests`, so that no revision or merge
  is entered by hand; a `pr` that is not a positive integer or not the file name's number; two demos of one pull
  request, which would share a sheet; an unknown `kind`; a `recorded` that is no ISO date or is later than `updated`;
  a named test that does not exist or whose name another test class shares;
- sections, read as Markdown headings: one that is unknown, twice, out of order or empty, one the kind needs that is
  missing, text before the first, and a level-1 heading (the title is the frontmatter's);
- anything left of the template: `YYYY-MM-DD` or a `<placeholder>`;
- a fence that is neither `sh` nor `text`, an indented code block and a fence inside a list or a quote; commands
  outside Try it and Evidence, and a Try it without one; recorded output outside Evidence or before the line that
  says it was not rerun; an evidence section without its capture date; a gate's evidence without two recorded
  outputs; a command that is not one of the list above;
- an image, raw HTML or a link to a media file, whose path is read percent-decoded (`shot%2epng` is `shot.png`), and a
  link whose percent-encoding is malformed.

When it builds the site, it also fails when a link in See it opens no page of this site, when a link to the site's
address names a page or an anchor the site does not have, and when the evidence of a visual change links no page.

It never runs a command that a demo shows and never compares recorded output with anything: recorded output can be
stale or wrong, which only a reviewer can tell ([known gaps](../gaps.md#7-documentation)). The publication scan reads
the demo pages like every other page.

## How a demo is shown

Its sheet shows the outcome as the title and why it matters below it, then the pull request, the kind and the
recorded date, and a note that the revision in the title block is the commit the site was built from, not the
recording's. The status ledger keeps its meanings (see [how the site is drawn](../ui/documentation-site.md)): the
record's status, no capability of its own, and as verified evidence only the named tests that passed in the build
that made the site, with the double rule. Recorded output is never verified evidence. The register of demos, sheet
X-00, lists every demo, newest recording first.

## Before merge

Run `./gradlew check`, which also builds the site; open `tools/docs/build/site/demos/<pr>-<slug>.html` and read it
as a reader would; then record in the pull request, under "Review of claims and rendered demo", what you checked. The
pull request template also asks for the demo's file, its public route and the limits of its recording, or for the
reason a pull request needs none.
