---
title: "The first runnable slice has its own decision record"
description: "ADR 0012 states the first runnable slice's planned approach and deferrals, and the four superseded records link readers to it."
status: accepted
updated: 2026-11-07
pr: 68
kind: docs
recorded: 2026-11-07
tests: [SitePagesTest]
---

## What changed

Before, ADRs 0003, 0004, 0005 and 0009 described a React workbench with a generated API client and persistence
through Spring Data JDBC, none of which the first runnable slice builds. Now ADR 0012 records the slice: a plain
TypeScript workbench compiled by TypeScript 5.9.3 with a hand-written typed HTTP adapter, logic tests under Node's
test runner and browser tests with Playwright and axe, the JDK import checker as the gate on the web app's imports,
and adapters on Spring JDBC's JdbcClient. It carries forward everything the four records decided that still holds,
and it says what is deferred and what that costs: without a generated client, nothing proves at build time that the
hand-written types match the contract. The four records keep their accepted text and are marked superseded.

## See it

- [ADR 0012](../adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md): the status panel
  names the four records it supersedes and its planned checks; the sections after the decision list what carries
  forward from each record.
- [ADR 0009](../adr/0009-react-workbench-with-a-generated-client.md): the status panel says "Superseded by ADR 0012",
  and the record's own text is unchanged.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07 on macOS with Temurin 25.0.5+8, on revision 825c804 of pull request #68:
`./gradlew :tools:docs:site` exited 0. The site task's own line, after the build's warnings (left out):

```text
docs: wrote the site, 62 files
```

Inspection record: [ADR 0012](../adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md)
was read in headless Chrome at 1440 pixels in light and at 390 pixels in dark, and
[ADR 0009](../adr/0009-react-workbench-with-a-generated-client.md) at 1440 pixels in light. The superseded record
shows its status and the link to ADR 0012; ADR 0012 shows the four records it supersedes; neither page scrolls
sideways. `SitePagesTest` checks the supersession links and the evidence wording in every build. Reviewed in pull
request #68.

## Limits

- A decision record states intent. Whether the code follows it is shown by the checks it names, most of which are
  planned until the code they check exists.
- The hand-written types can drift from the contract without type checking detecting it. The planned server HTTP
  contract tests and browser tests will check only the operations and fields they exercise.
- React and the generated client are deferred, not rejected; bringing them back requires their dependency reviews and
  a new decision record that supersedes ADR 0012.
