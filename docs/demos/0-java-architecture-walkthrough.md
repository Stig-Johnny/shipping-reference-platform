---
title: "Java readers can follow the shipping design"
description: "One page connects the running API to its Java packages, transactions and tests."
status: accepted
updated: 2026-11-15
pr: 0
kind: docs
recorded: 2026-11-15
tests: [SitePagesTest]
---

## What changed

A Java developer now has a reading path from a local run through packages, ports, JDBC, tenant isolation and tests.
Previously those details were spread across module documents and decisions. Start navigation and search now reach
the same walkthrough.

## See it

Read [the Java architecture walkthrough](../java-architecture-walkthrough.md), sheet S-04. Follow its decisions,
compare the short Java and SQL excerpts, and read the production limitations after the test layers.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-15, on macOS with Temurin 25.0.5+8, the author checked the page against application sources, tests and
decisions, verified its four excerpts exactly, and ran its memory-launch command with the static fixture. A local
HTTP exercise exited 0; this is its final output line, omitting request IDs and individual request output:

```text
PASS: synthetic quote, booking, identical replay, list, detail, label and foreign/unknown denial.
```

The launcher was stopped with Ctrl-C; the port check then exited 0. Documentation generation, the full Gradle
check including checkGenerated, and PolicyCheck each exited 0 before this unnumbered demo was added. The generated
walkthrough HTML was read for sections, excerpts, source labels and decision links. SitePagesTest checks Start,
section-navigation and search links in the build that publishes this page; the recording above is separate.

## Limits

The PR number is awaiting orchestrator assignment; zero prevents the docs build until it is replaced. Visual
browser review was unavailable because no browser was exposed and Safari access was denied. Review the rendered,
numbered demo before merge. Repository Markdown links to source files; the site shows unpublished files as text
under its existing publication rules. This recording proves neither PostgreSQL behavior nor browser acceptance,
production deployment, independent review or approval.
