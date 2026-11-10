---
title: "Milestone: from slides to code"
description: "The milestone deck: what is built and checked, what is still planned, one change the gate rejects, who approves what, and a proposed first 30 days with a new team."
status: accepted
updated: 2026-11-10
kind: deck
lang: en
milestone: 2026-11-10
mainSlides: 10
appendixSlides: 4
---

# Milestone: from slides to code

## AI-assisted delivery, accountable decisions

The aim is to deliver more useful changes, with clear responsibility for each change and the decisions behind it.

- Agents implement changes and a separate reviewer checks them
- Required checks must be green before merging
- A person owns the decisions that cannot be undone

### Notes

- This meeting: what I promised last time, shown in a public repository you can open yourselves
- All domain data is synthetic; the shipping app is an invented example
- The aim is delivery, not tooling for its own sake

## The promises, and what you can check today

The checker and dependency gate run. The attended shipping workbench now prices, books and opens a shipment.

| Promise | Today |
|---|---|
| The model governs the structure | Built and tested: the checker |
| The gate stops unreviewed dependencies | Declared dependencies are checked in the approved CI run |
| The shipping app itself | A runnable demo; 16 of 27 capabilities still planned |

### Notes

- This is a local, attended demonstration with synthetic users and disposable memory. The documentation site hosts the evidence pages, not the API.
- Clerk Alpha → quote → book Standard → replay the same request → shipment → simulated label. Use the [attended runbook](../flows/attended-demo.md) and [screen specification](../ui/workbench.md).
- The 2026-11-09 memory rehearsal passed sign-in, quote, booking, replay, list, detail and tenant denials. It reached "Label ready" but did not establish a freshly saved file. Earlier Chrome SVG proof remains a separate historical recording.
- The acceptance ledger is 27 capabilities: 2 shipped, 9 partial and 16 planned. Planned means its named acceptance evidence is missing, even where implementation exists.
- If the app is unavailable, show the [workbench recording](87-web-workbench-slice.md) and this deck's recorded gate failure. Keep a local copy of the documentation available for a network failure. Memory data disappears when the launcher stops.

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

- This recorded demonstration used a separate copy of the repository. Present the excerpt without changing the model during the meeting.
- First the business meaning: an agreed rule about which part may use which
- Then the error message: which classes, which calls, which line
- The excerpt below is the immediate fallback if the live demonstration is unavailable.
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
- Type-pattern switches remain an importer blind spot. A green architecture report covers the direct references the checker detects.
- The attended browser flow is separate from automated browser acceptance. Named Playwright tests and a full keyboard or screen-reader audit remain planned.

## Who approves each change

Every level requires green checks and a recorded independent AI review.

- 0: document text. 1: appearance, and prototypes without sensitive functionality, off by default.
- 2: ordinary functionality.
- 3: sensitive changes and the controls, under the owner's approval authority.

### Notes

- Level 3 covers money, booking, personal data, sign-in, the separation of the webshops' data, the database structure, third-party code and the controls
- The baseline policy requires owner approval for level 3. For this demo work, the owner has delegated review and merge to Codex after checks and separate review of the exact head, without another per-merge confirmation.
- Implementation, editorial work, integration and independent review are recorded as separate phases. Delegation does not imply an individual human approval of every commit.
- Automatic AI-review and risk/owner-approval gates remain planned
- Changes to SQL code, migrations and the processing of personal data are level 3
- At your company I propose that a person also checks the intent at level 2, as I promised last time

## A workflow your team owns

Issue → tests and change → independent review → demo.

- [Brain](../../BRAIN.md) connects modules, decisions and passing evidence. The build checks its generated sources.
- We demonstrate every delivery to you
- [Contribution records](../contributions/history.md) separate implementation, integration and review

### Notes

- The method must be transferable: clear who maintains the checks, the reviews and the settings
- In Brain, select Shipments and its Quotes dependency, then Decisions and Evidence. Map arrows describe declared relationships, not runtime traffic.
- Site search finds published titles, summaries and headings. It does not search every paragraph.
- Open [Issue 69](../contributions/history.md#issue-69) for distinct DeepSeek and Codex phases. Model and effort receipts establish provenance, not quality. Missing settings stay unknown.
- Brain is a summary of the sources. Finish with the [known gaps](../gaps.md) and the [dependency gate](../../security/README.md).

## Working with your Java lead

I own the delivery and the technical decisions; the Java lead challenges the technology-specific choices.

- I learned Java at university and have 17 years of experience with C# and .NET
- I make uncertainty explicit.
- Spring JDBC (`JdbcClient`) adapters exist for tenants, quotes and shipments. We will make any JPA decisions together.

### Notes

- State the Java background once, then move on to responsibility and collaboration
- JDBC adapters, paired V1–V3 migrations, forced row-level security and booking concurrency cases have [recorded PostgreSQL CI evidence](../flows/attended-demo.md#recorded-database-evidence).
- The meeting uses memory. No local PostgreSQL rehearsal is claimed.
- The repository has strict builds and static analysis. Checker mutation tests run in CI, with the limits in the appendix.

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
- This is a proposed reporting cadence. Separate what we demonstrated live, recorded checks and acceptance evidence still missing.

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
- 519 product allowlist entries: 504 approved, 15 rejected
- A review can be wrong; conditions are not enforced everywhere

### Notes

- The [Trust record](../../security/README.md) explains review conditions and limits. These counts describe the product repository's allowlist.

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
- The dependency check runs from the main branch. Owner authorization and independent review remain separate responsibilities.

### Notes

- Current owner delegation permits Codex to merge this demo work after required checks and independent review of the exact head.
- The baseline level 3 policy retains human accountability. Automated risk/owner approval and a separate AI-review gate remain planned.
- The controls are not a certification

## Appendix: tools and versions

Java 25, strict builds and static analysis.

- Temurin 25.0.5+8, Gradle with locked and verified dependencies
- Error Prone and NullAway; the checker uses ArchUnit and Structurizr DSL
- Mutation testing of the checker runs in CI with a 90% threshold; passing is not required before merging.

### Notes

- The API runs in the attended demo with memory stores. JDBC adapters and paired V1–V3 migrations have separate [recorded PostgreSQL evidence](../flows/attended-demo.md#recorded-database-evidence).
- The historical [runtime recording](94-demo-profile.md) belongs to its stated CI run. A later passing run does not rewrite that record or become the publishing build's own evidence.
- Pricing and booking modules are not mutation-tested. Browser acceptance, adversarial CSP checks and a full accessibility audit remain planned.
