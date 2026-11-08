---
title: "Shared receivers and deferred tasks no longer pass these scenario fixtures"
description: "The scenario checks reject two reproduced coverage holes and state the limits of their evidence."
status: accepted
updated: 2026-11-08
pr: 77
kind: gate
recorded: 2026-11-08
tests: [CoverageBypassTest, ReachabilityTest, QuoteScenarioTest, TenantDenialScenarioTest, SiteAssetsTest]
---

## What changed

The two executable scenarios compare four declared steps with recorded interactions. Review found two ways for
unobserved work to pass: a tenant receiver shared across modules, and work deferred onto virtual threads or existing
executor workers. The walk now visits each object separately for each owning module, including shared containers.
The census refuses task-capable dispatch sites it examines; it does not try to prove task completion.

The value-behaviour fixture now registers and checks three quote outcomes. The public claims distinguish detected
gaps from unexamined cases, and the repository-view comparison with Structurizr from syntax fixtures without that
independent oracle.

The shared diagram renderer now reserves space around group headings and uses opaque relationship-label backgrounds.
This addresses the quote scenario's merged API heading and step 4, and the line through step 3's REQUIRED label,
without changing the model's words, relationships or their order.

## See it

Read the [executable scenarios](../flows/quote-book-label.md#executable-scenarios) and their
[known limits](../gaps.md#6-tests-and-quality-gates).
On the [quote scenario diagram](../architecture/scenario-quote.md), inspect the separation of API and step 4 and the
backing behind relationship labels. The same rendering settings apply to every diagram.

## Try it

With the reviewed JDK as `JAVA_HOME`, run from the repository root. The fixtures use synthetic tenants and stores
in memory; they need no database. Their worker tasks are released and awaited by the tests, with no manual cleanup.

```sh
./gradlew check
```

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-08 on macOS arm64 with Temurin 25.0.5+8 in the uncommitted worktree of pull request #77.
Before implementation changes, the command `./gradlew :apps:shipping-api:test --tests '*ReachabilityTest' --tests
'*CoverageBypassTest' --console=plain` exited 1. Task logs and stack traces are omitted from this excerpt:

```text
11 tests completed, 5 failed
BUILD FAILED in 4s
```

The failures were the direct and container shared-reference fixtures (both traversal orders exercised), virtual work
and pre-existing-executor work held until after close, and the value-behaviour fixture's missing outcome assertions.
Both deferred-work reports said CONFORMS before the fix, with three passing quote outcomes and no failing findings.

After the fix, the same command with `--tests '*QuoteScenarioTest' --tests '*TenantDenialScenarioTest'` added exited
0. Excerpt, omitting task logs:

```text
BUILD SUCCESSFUL in 4s
10 actionable tasks: 2 executed, 8 up-to-date
```

All 13 tests in that run passed. The two deferred-work reports now say DOES_NOT_CONFORM with COVERAGE_GAP findings
at their dispatch sites; their three outcomes still hold. The two positive scenarios pass. The tests named in this
page's metadata provide current-build evidence only when their JUnit results pass in the publishing build. Review
reference: pull request #77.

The diagram follow-up's new `SiteAssetsTest` assertion failed before the rendering settings changed (25 tests, one
failure), then passed during documentation generation on 2026-11-08 with the same JDK. It checks the generated
script's shared title margins and opaque SVG label backing. A fresh rendered inspection is still pending because
browser access was unavailable in that review session; these assertions do not establish the final pixel layout.

## Limits

These fixtures prove the reproduced cases, not every possible receiver, input or schedule. The census examines main
module classes and reached test module classes with their module dependencies. The walk sees boundary snapshots;
transient references and dispatch hidden in unexamined code can escape. The thread snapshot excludes virtual threads
and tasks on existing workers, and close cannot discover calls made after it returns. The delayed-task fixtures fail
because their dispatch sites are examined. HTTP, database operations, listeners and commit observation remain outside
this slice. Accepted/refused DSL syntax fixtures have no independent Structurizr oracle.
The diagram configuration assertions do not test browser layout or guarantee collision-free placement for future
model views.
