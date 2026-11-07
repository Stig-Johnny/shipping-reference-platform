---
title: "Each use case shows what its tests showed"
description: "For every product capability, a reader can compare its declared actors and implementation status with the results of its named acceptance tests."
status: accepted
updated: 2026-11-07
pr: 76
kind: visual
recorded: 2026-11-07
tests: [UseCasePageTest, CapabilityActorsTest, TestEvidenceTest]
---

## What changed

Before, the site listed capabilities and their status, but not who uses each one, and a reader could not see at a
glance which of their tests had passed. Now the Model section has a use case sheet. Each product capability lists
actors from the model, or explicitly declares none, and the documentation tool rejects an actor the model does not
have or does not connect to the capability's module. A table, grouped by module, stamps each use case with what its
named tests showed in the build that generated the site: all passed, some failing, not run or missing, or no tests
yet. "All passed" requires a non-empty acceptance-test list. Each name must match an existing Java test source and be
unambiguous among sources and reports. At least one JUnit report must be read for each named class, and every report
read for it must record at least one test, with no failures, errors or skips. Repeated reports of the same fully
qualified suite count as their worst outcome: failure or error, then skipped, then zero tests, then passing,
regardless of producer order. Different fully qualified suites sharing a simple name remain ambiguous and cannot
supply passing evidence. A map of actors and use cases follows the table.

## See it

- [The use case sheet](https://stig-johnny.github.io/shipping-reference-platform/model/use-cases.html): the table
  first, with each use case's status and stamp; then the actor map, which enlarges and scrolls. The legend says what a
  stamp means and what it does not.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-07 on macOS with Temurin 25.0.5+8, from the checkout at revision a886b7e of pull request #76, after
merging main's pull request #67. The tests had run during `./gradlew :tools:docs:generate` on the same source tree.
The subsequent `./gradlew check` exited 0 and reported the test tasks as `UP-TO-DATE`, reusing those results. The
JUnit XML reports in the projects' `build/test-results/test` directories recorded no failures, errors or skips. During
`check`, the site task printed the following line; other task output and warnings are omitted:

```text
docs: wrote the site, 66 files
```

Inspection record: the locally generated [use case
sheet](https://stig-johnny.github.io/shipping-reference-platform/model/use-cases.html) was inspected in headless
Chrome at revision a886b7e: 1440 pixels in light and dark, 390 pixels in light and dark, and 360 pixels in light. The
table comes before the map, and stamps use words as well as borders. At these widths, the page itself stays within the
viewport; wide tables scroll inside their own regions. The actor map's labels are readable after enlargement at 1440
and 390 pixels, with scrolling needed to inspect the complete map. The named test classes check the generated page,
actor validation and evidence reader. Reviewed in pull request #76.

## Limits

- A stamp reports the results of named acceptance tests. Passing those tests does not establish that they cover
  all the described behaviour. Implementation status is read from the capability registry, not derived from
  the stamp.
- The map's actors and arrows are declared in the registry and model, not observed during execution. A passing
  stamp does not prove that the use case ran as drawn.
- The site reads JUnit XML from the projects' ordinary test tasks, in `build/test-results/test`. Reports from
  other tasks and TypeScript or JavaScript reports are not read. Only reports actually read affect a stamp;
  a passing stamp cannot rule out failures in unread reports.
- A use case without an actor names none in the registry; that does not establish how it is triggered.
- The visual inspection covers only the recorded Chrome widths and themes.
