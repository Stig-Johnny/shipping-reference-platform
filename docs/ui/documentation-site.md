---
title: "Documentation site: visual design"
description: "How the documentation site is drawn: a drawing set with sheet codes, a title block on every sheet and a status ledger that keeps accepted design, planned implementation and verified evidence apart."
status: accepted
updated: 2026-11-09
---

# Documentation site: visual design

This specification is binding for the documentation site that `tools/docs` generates (ADR 0011). Changes require an
explicit design decision recorded here. The web app has its own specification, [the workbench](workbench.md); the
two no longer share tokens.

## The idea

The repository's thesis is that the architecture model is the drawing the code must conform to. The site is drawn as
that drawing set: every page is a sheet with a code and a title block, the navigation is the register of sheets, and
the status of what a sheet describes is entered in a ledger. The metaphor lives in alignment, numbering and
provenance, never in decoration.

**Not allowed outside the Brain map exception below:** paper textures, grid wallpaper, distressed or rotated stamps, screws, barcodes, ornamental dimension
lines, decorative warnings, shadows, gradients, animation and illustrations.

## What lines mean

- A solid line never means "built". Solid arrows in the model also draw relationships that no check covers.
- **Planned** is a dashed box around the word, and only beside an implementation state. Navigation links, rows and
  diagram lines are never dashed for it, and the model's own line patterns (thick for REQUIRED, dotted for
  PERMITTED) are never overwritten.
- **Accepted** is a single rule: the design is decided, which says nothing about code.
- A **double rule** marks passing evidence, and only beside the scope it covers ("Checker tests passing" says the
  tests verify the checker, not the shipping modules).
- Every state is in words. Colour and line style repeat the words; they never replace them.

## Tokens

Contrast is sRGB contrast against the lower of the page and sheet backgrounds.

| Token | Light | Dark | Use | Contrast light / dark |
|---|---|---|---|---|
| `--page` | `#F4F1E8` | `#0B2538` | Page background | — |
| `--sheet` | `#FFFDF7` | `#12344A` | Panels, tables, the register | — |
| `--ink` | `#172B3A` | `#F3F0E6` | Text, strong rules | 12.88 / 11.40 |
| `--ink-muted` | `#4C5E6B` | `#B8C8D1` | Secondary text, labels | 5.96 / 7.56 |
| `--control` | `#687987` | `#7F9CAB` | Control and focus-adjacent borders | 3.98 / 4.48 |
| `--link` | `#164F86` | `#9ACBFF` | Links, focus, the active register rule | 7.45 / 7.65 |
| `--failure` | `#A33225` | `#FFAB9D` | Rejected and failing states | 6.12 / 7.16 |
| `--rule` | `#CFD2CA` | `#325369` | Decorative rules only, never a control's only border | 1.35 / 1.59 |

Diagrams sit on the light sheet (`#FFFDF7`) in both themes: their colours are written into the generated
definitions, and a drawing must read the same wherever it is shown.

## Type and layout

- `system-ui, sans-serif` for text; `ui-monospace, SFMono-Regular, Consolas, monospace` for code, digests, sheet
  codes and labels. No web fonts: nothing is fetched from another origin.
- Sizes: metadata 14/20 px, body 16/24, H3 20/26, H2 24/30, H1 36/42 (30/36 below 720 px). Labels and sheet codes in
  monospace capitals with 0.06em tracking; prose in sentence case. Tabular figures for numbers.
- Spacing steps 4, 8, 12, 16, 24, 32 and 48 px. Square panels; controls have a 2 px radius.
- Shell: a 240 px register beside content of at most 1120 px; prose at most 72ch. Below 720 px the register becomes a
  labelled disclosure above the content, gutters are 16 px and touch targets at least 44 px.

## The five components

1. **Sheet header.** Breadcrumb; the sheet code (`M-03`) above the H1; one sentence of purpose; the title block, a
   ruled list of the source, the revision (its first eight characters; the full SHA is at the foot of the sheet) and,
   for a sheet that renders one document, that document's update date. A sheet made from several sources shows no
   date rather than an invented one. Codes are assigned page by page in `Sheets.java`, or come from a decision
   record's number or a demonstration's pull request number, never from a page's position.
2. **Status ledger.** One ruled list with three named rows: accepted design, planned implementation, verified
   evidence. Each row has a state in words and its scope. Passing evidence is a count and what it does not prove; the
   test names are in a disclosure, never in the first line. A decision's partial control summary supplements its
   passing evidence and planned enforcement; it must not hide the decision's other checks (`SitePagesTest`).
3. **Drawing register.** The navigation: each row is the sheet code in a fixed column, 56 px wide so that seven
   characters such as `X-GUIDE` fit, and the title beside it, with a state word where a sheet has one. The current
   row has a 3 px link-coloured rule and `aria-current`. The theme choice stays three native radio buttons in a
   labelled group.
4. **Drawing panel.** The view's title, the enlarge button, the drawing on the light sheet, the legend, and a link to
   the text equivalent. The drawing is fitted to its complete bounds before it is shown. "On this sheet" lists a
   page's sections in a disclosure; the start sheet has none.
5. **Register table.** A caption, a strong rule under the header, light rules between rows, no outer card. Prose
   left-aligned, numbers right-aligned in tabular figures, states in words. Wide tables scroll inside a labelled,
   focusable region with a hint.

## The start sheet

At 1280 × 900 the first screen shows the title, the purpose, the title block and the status ledger, then the system
context drawn, then the two reading paths; the COO's ends with the Demos section. The counts in the ledger come from
`docs/capabilities/capabilities.yaml`. The explanations of the data and of what the site publishes follow below.

## Search and the visual Brain

Design decision for issue #38, reviewed by the owner on 2026-11-09: the human Brain leads with a dark network
map instead of the full capability tables. The agent's generated Markdown remains unchanged. This is an explicit
exception to the drawing palette and decoration rules: the map uses a near-black background, a restrained purple
gradient, cyan built modules, violet planned modules and a gold selection. Module sizes are equal; layout and size
never imply importance. Capability satellites indicate ownership, not evidence or runtime activity. Every state
also appears in words. There is no particle animation or force simulation.

The map starts with API module dependencies. Selecting a module reveals its capabilities, dependency modes and
specification's named decisions; a decisions view draws those mentions, with ADR decision status kept separate
from implementation status. An evidence view draws only tests that passed in the publishing build, for the selected capability or control.
Every edge has a named meaning and source in the HTML records. REQUIRED/PERMITTED and planned are explicit text;
map arrows are code dependencies, never runtime flow. The web component map remains on its existing Model sheet.

At 1280 pixels the map and selected record share the available width; at 390 and 360 they stack. All controls have
keyboard paths and visible focus. Small satellites may hide on mobile; the searchable record list keeps the same
capabilities available. The map and selected details come first. Source metadata, the existing status ledger and the contents list follow
the main body; full capability, control and module records remain available through disclosures. Without script the records remain readable and linked. The four-step
demo journey links to the attended runbook and is visibly separate from static dependency arrows.

Search is sheet S-03, linked from every page, with a labelled input and the complete list of published sheets
(excluding the 404 fallback). Its index is generated from the assembled pages' titles, summaries and rendered
headings, never from extra repository text. The index is ordinary HTML covered by the publication scan, with no
JSON file, fetched data or storage. Without script every link and field remains readable. With script a literal,
case-insensitive phrase filters the list; title matches rank before summaries, then headings, with source order
breaking ties. Each result names the matching field and shows its text. Enter moves to the first result; Escape
clears the query. No match is an announced result, never a blank page.

Both views rebuild through the existing checked generation and Pages deployment. Changing prose updates the
published text and search; changing model/registry declarations updates the corresponding graph relations.
Free-form prose cannot invent a code dependency. Detail is available on demand so the first screen prioritizes
the map and the reader's next action.

## The Demos section

Demos is the second section of the register, after Start. It shows a reader who does not read code what each pull
request changed, from `docs/demos/` (the recipe for writing one is `docs/demos/how-to.md`). A milestone deck stands
there too, drawn as [decks](#decks) are.

- **Codes.** `X-00` is the register of demonstrations, `X-GUIDE` the how-to, and each demonstration is `X-` and its
  pull request's number (`X-42`), from the record's own `pr`, never from its position. The how-to stands first in the
  section, outside the chronological list.
- **Register (X-00).** A register table: the outcome, the record's title linked to its sheet with its description, the
  one sentence on why it matters, below it; the pull request as plain text (`#42`), never a link; the kind in words;
  the recorded date. Newest recording first, then the higher pull request; an edit (`updated`) never reorders it.
  Without demonstrations, it says so in a sentence. On a narrow screen the table keeps a minimum width of 40rem and
  scrolls inside its labelled, focusable region with the hint "Scroll horizontally for all columns.", hidden once the
  region is 40rem wide; up to 600 px wide its layout is fixed and the outcome's column is 16rem.
- **Sheet (X-42).** The sheet header with the pull request, the kind and the recorded date beside the description,
  and a note that the revision in the title block is the commit the site was built from, not the recording's. The
  title block's date is the record's `updated` date. Then the status ledger, "On this sheet", and the sections in a
  fixed order: What changed, See it, Try it, Evidence, Limits. A command and recorded output are labelled in words
  above their blocks ("Command", "Recorded output").
- **Ledger.** It keeps its meanings. Accepted design is the record's status. Planned implementation is "Not
  applicable": a demonstration specifies no capability. Verified evidence counts only the test classes the record
  names under `tests` that passed in the build that made the site, with the double rule; a missing, ambiguous,
  failed or skipped test is never counted, and recorded output never gets the double rule. The ledger names no merge:
  there is no trustworthy merge record yet.
- **Text only.** No image, video, embed or raw HTML until a reviewed media gate exists.

## Decks

A milestone deck presents the repository's state as slides for a meeting, from `docs/demos/deck-<slug>.md`, in English
like the rest of the site. It stands in the Demos section but is not a demonstration: no pull request is its own, and it
is never evidence.

- **Codes.** The slides are `X-Dnn` and their speaker notes `X-Nnn`, written out page by page in `Sheets.java`:
  `demos/deck-milestone.html` is X-D01 and `demos/deck-milestone-notes.html` X-N01. `X-` and a number stays a
  demonstration's. The register of demonstrations (X-00) features each deck above the demonstrations, with the neutral
  label "Presentation", never a passing-evidence badge, and its description; the start sheet links it in one compact
  line beside the two reading paths, below the title, the ledger and the drawing.
- **Layout.** The register, the overview and the slide index ("Overview") stand above the slides, never beside them, so
  that every slide has the full width. The overview is the sheet header, with the milestone, the source's update date
  and the build's revision each on its own, and the ledger; "Start slides" leads to slide one. Full screen is the
  browser's own command; the site's script is unchanged.
- **Slide anatomy.** Each slide is a section labelled by its title, whose slug is its anchor: the deck's code and the
  slide's number in a box (`01/10`, and `A1/4` in the appendix, whose titles start with "Appendix:"; read out in
  words), the title, the content, then below a rule the links "Previous", "Overview" and "Next", which are the
  dependable controls, and the source with the revision. From the first slide "Previous", and from the last "Next",
  lead back to the index. Slide 02 carries the repository's ledger, from the sources as the start sheet has it, and
  says what is still done by hand. Notes under `### Notes` stand on the notes page only, each under its slide's number
  and title, which link back to the slide.
- **Type scale.** At 1920 × 1080: titles 64/72 px, body text 32/44, evidence excerpts 28/38 and metadata 20/28. The
  sizes scale with the viewport, bounded in rem, to about 40 px titles and 24 px body text on a laptop. A slide is at
  least one viewport tall and grows with its content; nothing is clipped, and nothing is shrunk to rescue a full
  slide. Scroll snapping helps from 1024 × 600 only; narrower, shorter or zoomed, the slides are ordinary document flow
  in the site's own sizes. The end of the page is a snap position too, so that a focused footer link is never pulled
  back out of view to the last slide.
- **Language and theme.** A deck declares `lang: en`, the site's language, which its two pages state; the check
  refuses any other. Light is the meeting's palette, chosen with the theme switch; dark stays for readers.
- **Print.** One 16:9 slide on each landscape page (320 × 180 mm), in the light palette, with each slide's code, number
  and provenance, and without the register, the overview, the index, the controls or the notes.

## The use case overview

Sheet `M-07`, after the flows in the Model section: every product capability of
`docs/capabilities/capabilities.yaml` as a use case, stamped with what the build that made the site observed of its
acceptance tests. It answers which use cases have evidence; it never claims that a use case ran as drawn. The evidence
comes first: the introduction, with a visible link to the actor map, then the register, the actor map, the actors and
what a stamp means.

- **Actors.** A capability names its actors in `actors`, by their identifiers in `architecture/workspace.dsl`: people
  and external systems, or `[]` to declare none, which does not establish how the use case is triggered. The docs tool
  fails unless the model relates each actor to the capability's module, directly or through an element the actor
  uses, and that relationship sets the arrow: from an actor that starts the use case, to a system that the use case
  relies on.
- **Register.** One table, grouped by module: the use case's ID and title, its status in the registry, its test
  evidence, and its actors ("Started by", "Relies on" or "No actor declared"). It keeps 60rem, 44rem up to 600 px
  wide, and takes its column widths from its header, so that no status word breaks and a use case and its status
  share a phone's first screen; narrower, it scrolls inside its region with the hint.
- **Actor map.** After the register, under its own heading, with the instruction "Enlarge the diagram and scroll to
  read its labels." One drawing panel, whose region is at most 36rem tall and scrolls, and which enlarges like every
  drawing: actors as boxes with their C4 type; each module's use cases as rounded boxes, Mermaid's stadium, in a
  group, with the registry's status in parentheses; thin solid arrows without labels. It has no colours of its own.
  The legend says that a use case without an arrow names no actor in the registry, which does not establish how it is
  triggered; that the arrows are declared, not traced; and that a stamp says which named tests passed, not that the
  use case ran as drawn. A person or system that no capability names is not drawn; the actors table lists it.
- **Stamps.** A badge in words, a sentence with the counts, and the named tests with their states in a disclosure.
  "All passed" has the double rule and needs every named test to have passed in that build; "Some failing" is in the
  failure colour; "Not run or missing" (no report, skipped, no test run, ambiguous, not in the repository, or a
  TypeScript or JavaScript test, which the build does not read) and "No tests yet" (planned: none named, or none of
  those named in the repository) are muted. An empty list is never green. A test that more than one producer reports
  counts as its worst report.
- **What a stamp means.** Below the actors table: the four stamps in words, that a stamp is no runtime trace, and the
  reports the build read, by Gradle task, so that a test whose task did not run shows as not run.

## Diagrams

Mermaid draws with `theme: 'base'` under the reviewed strict settings, with ink lines, the light sheet as background
and 18 px labels. Group titles reserve 16 px above and 32 px below in the drawing's coordinates; relationship labels
have an opaque light-sheet backing so arrows do not cross their text. These settings apply to every diagram.
Node colours come from the generated definitions and are not themed. Node recolouring, dashed
borders for planned elements and a dark drawing theme wait for a design decision here.

## Accessibility

WCAG 2.1 AA in both themes: 4.5:1 for text, 3:1 for controls and focus, a visible 2 px focus outline with a 2 px
offset, reduced motion, 200 % text and 400 % zoom, reflow at 320 px, and every state in words.

## Attended demo runbook

The [attended runbook](../flows/attended-demo.md) uses the existing flow-document route in the Model section,
with stable sheet code `M-10` and route `model/flow-attended-demo.html`. It describes launch preparation,
synthetic identity, both storage modes, credential-free diagnostics and cleanup. `SitePagesTest` checks that the
rendered page retains those instructions and the PostgreSQL evidence limit. The COO reading path continues to
select the quote-book-label flow explicitly; adding a runbook does not redirect its main-flow link.
