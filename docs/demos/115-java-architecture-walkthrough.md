---
title: "Java readers can follow the shipping design"
description: "One page connects the running API to its Java packages, transactions and tests."
status: accepted
updated: 2026-11-15
pr: 115
kind: docs
recorded: 2026-11-15
tests: [SitePagesTest]
---

## What changed

A Java lead can scan the design choices, run three API calls and follow the implementation through packages,
ports, SQL, tenant isolation and tests. The walkthrough brings details previously spread across modules and
decisions into one reading path, reachable from Start navigation and search.

## See it

Read [the Java architecture walkthrough](../java-architecture-walkthrough.md), sheet S-04: its linked overview,
copyable quote/book/label calls, four exact source excerpts and final Known limits list.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-15, on macOS with Temurin 25.0.5+8, the initial source review verified all four excerpts and the
memory launcher. Its HTTP exercise exited 0; the final line omits individual requests:

```text
PASS: synthetic quote, booking, identical replay, list, detail, label and foreign/unknown denial.
```

The PR #115 revision preserved every source link and excerpt and executed the three curl blocks directly from
the revised page. The sequence exited 0 and saved synthetic JSON responses and an SVG label; its HTTP output was:

```text
quote: HTTP 201
booking: HTTP 201
label: HTTP 200, image/svg+xml
```

The final launcher run stopped with Ctrl-C (exit 130); the port check exited 0. Documentation generation,
`./gradlew check :tools:docs:checkGenerated` and PolicyCheck each exited 0. The generated walkthrough and
numbered demo HTML were read: overview links resolve, all eight code blocks retain their text, and sheet X-115
links to S-04. Browser preview was unavailable. SitePagesTest checks Start, section navigation and search
in the publishing build; the command recordings and HTML review are separate evidence.

## Limits

This recording covers sequential memory API requests. Database behavior and automated browser acceptance are
outside this recording; see the walkthrough's Known limits. Visual browser review remains pending. Source links open repository files in Markdown and appear as text
on the site under its existing publication rules. Final-head review and approval remain separate PR receipts.
