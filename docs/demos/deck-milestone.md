---
title: "Milestone: from slides to code"
description: "The milestone deck: what is built and checked, what is still planned, one change the gate rejects, who approves what, and a proposed first 30 days with a new team."
status: accepted
updated: 2026-11-07
kind: deck
lang: en
milestone: 2026-11-10
mainSlides: 10
appendixSlides: 4
---

# Milestone: from slides to code

## AI-assisted delivery, accountable decisions

The aim is to deliver more useful changes, with clear responsibility for each change and the decisions behind it.

- Agents write the code and the tests
- Required checks must be green before merging
- A person owns the decisions that cannot be undone

### Notes

- This meeting: what I promised last time, shown in a public repository you can open yourselves
- All domain data is synthetic; the shipping app is an invented example
- The aim is delivery, not tooling for its own sake

## The promises, and what you can check today

The checker and the dependency gate run; the shipping app has its first code, and most of it is planned.

| Promise | Today |
|---|---|
| The model governs the structure | Built and tested: the checker |
| The gate stops unreviewed dependencies | Declared dependencies are checked in the approved CI run |
| The shipping app itself | Planned: 23 of 27 capabilities |

### Notes

- Say it early: most of the application is not written yet, and none of it runs outside its tests
- What exists are the controls that judge it, and its first code, which they now check
- This proves part of the method; delivering product is the next evidence I owe

## One change the gate rejects

We remove one arrow from the checker's own model.

- The checker finds two calls the model no longer allows.
- The build fails, identifying the file and line.
- Restore the arrow: green again.

```text
### DIVERGENCE: report -> model
```

Recorded output — not rerun by this site build. Full record in the notes.

### Notes

- The demo runs in a separate copy of the repository, so nothing is shared by accident
- First the business meaning: an agreed rule about which part may use which
- Then the error message: which classes, which calls, which line
- If the network fails: switch to the recording at once
- Recorded output — not rerun by this site build. Recorded 2026-11-07T14:56:19Z on revision 44ef312, macOS with Temurin 25.0.5+8: `./gradlew :libs:c4-contract-kit:test --rerun --tests '*SelfConformanceTest'` exited 1 with the relationship `report -> model` removed from `libs/c4-contract-kit/architecture/self.dsl`, and 0 before and after. Excerpt from the checker's report; the report's other headings are left out:

```text
**Verdict: FAILED** with 1 violation. Model: `architecture/self.dsl`. Report format: `c4-contract-kit/report/v1`.
### DIVERGENCE: report -> model
report depends on model, but the model declares no relationship report -> model.
```

## What a green build proves

Each claim needs the right kind of evidence.

- The code compiles, the build's tests pass, and the library's recorded direct static dependencies follow its own model
- A required arrow is proven by one direct reference; an unused field is enough
- Correct prices and bookings need their own tests.

### Notes

- The checker sees compiled code, not what happens when it runs
- An arrow that touches a planned module is not evaluated yet
- So: the architecture check, tests and human judgement, each on its own

## Who approves each change

Every level requires green checks and a documented AI approval.

- 0: document text. 1: appearance, and prototypes without sensitive functionality, off by default.
- 2: ordinary functionality.
- 3: sensitive changes and the controls; the owner must also approve the exact version by hand before merging.

### Notes

- Level 3 covers money, booking, personal data, sign-in, the separation of the webshops' data, the database structure, third-party code and the controls
- Automatic enforcement of the AI and owner approvals is planned
- Changes to SQL code, migrations and the processing of personal data are level 3
- At your company I propose that a person also checks the intent at level 2, as I promised last time

## A workflow your team owns

Issue, tests, change, review, demo: every time.

- Brain: a generated summary that the agents are to read first; the build fails when it is out of date
- We demonstrate every delivery to you
- The checks are open; the README describes the setup and the limits

### Notes

- The method must be transferable: clear who maintains the checks, the reviews and the settings
- Brain is a summary of the sources, not a replacement for them

## Working with your Java lead

I own the delivery and the technical decisions; the Java lead challenges the technology-specific choices.

- I learned Java at university and have 17 years of experience with C# and .NET
- I make uncertainty explicit.
- This repository specifies Spring Data JDBC; its persistence code is still planned. We will make any JPA decisions together.

### Notes

- State the Java background once, then move on to responsibility and collaboration
- The tooling in the repository is real: strict builds, static analysis, mutation testing in CI

## A proposal for the first 30 days

We agree on one slice, a bounded feature from end to end, with acceptance criteria and a rollback plan.

1. Understand the platform, the tools and the prototypes
2. The gate in your repository, reporting only at first
3. One slice behind a flag; you decide whether to roll it out

### Notes

- No dates before I know the platform
- No promise of production before you have seen the slice work

## Evidence every Friday

Every Friday, you see working software and the evidence needed to judge our progress.

- One page: delivered, delayed, what the gate stopped, which decisions I need
- A demo of what runs
- We measure lead time, rework and defects that got through

### Notes

- Examples in the public repository are synthetic; your own numbers stay internal

## The decision

Agree on the first slice, how you will judge my contribution, and when we will review the result together.

- The first slice and its acceptance criteria
- The time it takes and who owns what
- When we look at the result together

### Notes

- Ask about your priorities before the details
- End with a date for the next review

## Appendix: the dependency gate

Declared dependencies in the approved CI run are checked against recorded reviews before the build starts.

- The check runs from the main branch against the change's files, as data
- 308 reviewed entries: 293 approved, 15 rejected
- A review can be wrong; conditions are not enforced everywhere

### Notes

- The details are on the site's Trust pages

## Appendix: evidence that the gates fail

Two changes were made to fail, and were never merged.

- Pull request #16: an unreviewed dependency; the check exited with code 1
- Pull request #17: a malformed allowlist; the check exited with code 2.
- A missing `pipefail` hid the failure status of the dependency check; this was fixed in #15

### Notes

- The full history is in security/README.md

## Appendix: how an agent could still weaken the controls

I say this before you ask.

- Broaden the model, or weaken tests and workflows in its own change
- Recompute the checksum of the reviewed set
- The dependency check runs from the main branch; level 3 needs the owner's approval by hand, which is not yet enforced automatically

### Notes

- Enforced approval and a separate AI-review check are planned
- The controls are not a certification

## Appendix: tools and versions

Java 25, strict builds and static analysis.

- Temurin 25.0.5+8, Gradle with locked and verified dependencies
- Error Prone and NullAway; the checker uses ArchUnit and Structurizr DSL
- Mutation testing of the checker runs in CI with a 90% threshold; passing is not required before merging.

### Notes

- The Spring Boot dependencies are declared; a running app and a database are planned
