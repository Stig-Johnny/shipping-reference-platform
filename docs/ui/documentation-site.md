---
title: "Documentation site: visual design"
description: "How the documentation site is drawn: a drawing set with sheet codes, a title block on every sheet and a status ledger that keeps accepted design, planned implementation and verified evidence apart."
status: accepted
updated: 2026-11-07
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

**Not allowed:** paper textures, grid wallpaper, distressed or rotated stamps, screws, barcodes, ornamental dimension
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
   record's number, never from a page's position.
2. **Status ledger.** One ruled list with three named rows: accepted design, planned implementation, verified
   evidence. Each row has a state in words and its scope. Passing evidence is a count and what it does not prove; the
   test names are in a disclosure, never in the first line.
3. **Drawing register.** The navigation: each row is the sheet code in a fixed column and the title beside it, with a
   state word where a sheet has one. The current row has a 3 px link-coloured rule and `aria-current`. The theme
   choice stays three native radio buttons in a labelled group.
4. **Drawing panel.** The view's title, the enlarge button, the drawing on the light sheet, the legend, and a link to
   the text equivalent. The drawing is fitted to its complete bounds before it is shown. "On this sheet" lists a
   page's sections in a disclosure; the start sheet has none.
5. **Register table.** A caption, a strong rule under the header, light rules between rows, no outer card. Prose
   left-aligned, numbers right-aligned in tabular figures, states in words. Wide tables scroll inside a labelled,
   focusable region with a hint.

## The start sheet

At 1280 × 900 the first screen shows the title, the purpose, the title block and the status ledger, then the system
context drawn, then the two reading paths. The counts in the ledger come from `docs/capabilities/capabilities.yaml`.
The explanations of the data and of what the site publishes follow below.

## Diagrams

Mermaid draws with `theme: 'base'` under the reviewed strict settings, with ink lines, the light sheet as background
and 18 px labels. Node colours come from the generated definitions and are not themed. Node recolouring, dashed
borders for planned elements and a dark drawing theme wait for a design decision here.

## Accessibility

WCAG 2.1 AA in both themes: 4.5:1 for text, 3:1 for controls and focus, a visible 2 px focus outline with a 2 px
offset, reduced motion, 200 % text and 400 % zoom, reflow at 320 px, and every state in words.
