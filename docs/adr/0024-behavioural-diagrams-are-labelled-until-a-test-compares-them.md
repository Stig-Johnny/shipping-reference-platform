---
title: "ADR 0024: Behavioural diagrams are labelled until a test compares them with the code"
description: "A sequence or state diagram in current prose carries the unchecked-design label right before it, and every dynamic view of the model is generated with it, until evidence shows that a test compares the whole diagram with the code; the documentation check fails otherwise."
status: accepted
updated: 2026-11-23
date: 2026-11-23
decision-makers: orchestrating agent, under the repository owner's standing delegation
enforcedBy:
  - "java: the documentation check fails when a sequence or state diagram in current prose has no unchecked-design label right before it, when such a label is followed by no such diagram, and when a Mermaid fence's info string or first line hides its type (BehaviouralDiagramsTest)"
  - "java: every dynamic view's generated page, accessible description and site figure carry the label, and the site shows it on the closed source of a labelled diagram in current prose only (BrainGeneratorTest, MarkdownRendererTest, SitePagesTest)"
plannedEnforcement:
  - "java: a scenario view loses its label only on evidence of a complete comparison: a fresh, non-fixture report of the passing test its sidecar names, against the current model, sidecar and classes, with every step compared, delivered to the Pages build through its reviewed handover (issue 153)"
  - "java: a transition test that reads the shipment lifecycle diagram and compares it with the code, once cancellation is built"
---

# ADR 0024: Behavioural diagrams are labelled until a test compares them with the code

## Context and Problem Statement

Step 4 of issue 136 asks that every behavioural diagram, a sequence or a state diagram, be bound to a scenario or
transition test, or be labelled visibly as unchecked design. On 2026-11-23 the documentation had five:

- the model's dynamic views `QuoteBookLabel`, `ScenarioQuote` and `ScenarioTenantDenial`;
- the hand-written sequence of the quote, book and label flow;
- the hand-written state diagram of the shipment lifecycle.

ADR 0013's scenario tests run the two scenario views and compare the steps they can observe with one run of the code:
2 of 6 steps each, with the coverage incomplete. No test reads the overview view or a diagram written in Markdown.
Nothing on GitHub or on the site told a reader, person or agent, which diagram the code is held to.

## Decision Drivers

- A diagram must not show more certainty than the code gives it.
- The label stands where the diagram is: on GitHub, on the site, and in the Markdown that agents read.
- Only evidence removes a label. A view's name, its sidecar or a passing test class proves neither that the view was
  compared nor that every step was.
- The check and the site classify a diagram by the same rule.

## Considered Options

1. Label every behavioural diagram until evidence of a complete comparison removes the label. The check holds a
   hand-written diagram to a written label, and the generator labels every dynamic view.
2. Treat a view whose key starts with `Scenario` as checked, as ADR 0013 names them.
3. Bind each scenario view to its test by data now, in its sidecar, and remove its label when that test passes.
4. Draw the hand-written diagrams on the site too, or remove them.

## Decision Outcome

Chosen: option 1. Option 2 turns a naming convention into a claim. In option 3 a passing class proves neither that
it compared the view nor that every step was compared. That evidence is the scenario report. The Pages build cannot
receive it through its reviewed handover yet, and no view is compared in full until HTTP and database steps are
observed (ADR 0013). Issue 153 takes this binding on with those conditions. Option 4 changes no claim: GitHub draws
the diagrams in any case.

- **Behavioural diagrams** are every dynamic view of `architecture/workspace.dsl`, and every Mermaid fence in current
  prose whose first line is `sequenceDiagram`, `stateDiagram` or `stateDiagram-v2`. Fences in lists and quotes count.
- **The label in Markdown** is a paragraph right before the fence, in the same container, whose first inline is
  strong emphasis holding exactly the words `Unchecked design.` and nothing else, which GitHub shows in bold above the
  drawing. A sentence after it says what no test compares.
- **A stray label fails.** A paragraph that starts with the label must be followed by a sequence or state diagram, so
  that no label stands on a flowchart or on nothing.
- **Mermaid fences show their type.** In current prose, a fenced block whose info string's first word is `mermaid`, in
  any letter case, has the info string `mermaid` alone. Its first non-blank line names its type exactly:
  `flowchart` or `graph` with an optional direction, `sequenceDiagram`, `stateDiagram` or `stateDiagram-v2`. Later
  lines are free.
- **A dynamic view is labelled by the generator,** whatever its name, sidecar or tests: on its generated page right
  before its fence, in its accessible description, and on the site, where its figure shows the label after its title
  and the sentence "No test compares this whole view with the code." first in its caption. That holds on its model
  page and wherever a flow page draws it. A scenario view's description says that its test compares the steps it can
  observe.
- **The site's label** is the words Unchecked design in the muted box with a single rule, the box of an absent path,
  after the title of a view's figure and after the summary of a hand-written diagram's closed source. It shows only
  in the prose the check reads. Codex chose this look from three rendered variants; the dashed box keeps its single
  meaning, planned.

### Consequences

- Good, because a reader sees at every behavioural diagram that no test compares it in full with the code.
- Good, because a new sequence or state diagram cannot be written into current prose without the label, and a new
  dynamic view gets it from the generator.
- Neutral, because the scenario views stay labelled although their tests compare part of them; their descriptions
  and the flow's executable scenarios say which part.
- Bad, because a state machine drawn as a flowchart, a standalone `.mmd` file and a diagram in a record or a
  generated file are not read as behaviour. The site publishes no images.
- Bad, because the label disclaims and does not bind. Binding needs evidence (issue 153), and for the shipment
  lifecycle a transition test, which can come with cancellation.

### Confirmation

`BehaviouralDiagramsTest` runs in every build of the documentation tests, and the check itself runs in every
`generate`, `checkGenerated` and site build. It fails on a sequence or state diagram without the label right before
it, on a label in another form or another container, on a stray label, on an info string other than `mermaid`, and
on a first line that names no type. It does not read records, generated files or superseded ADRs.
`BrainGeneratorTest` proves the label and the accessible description on every dynamic view's generated page.
`MarkdownRendererTest` and `SitePagesTest` prove the site's label: on closed sources at any depth in current prose
only, and on every dynamic view's figure on its model page and on the flow page, and on no structural view.
