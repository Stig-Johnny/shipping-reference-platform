---
title: "Price, book twice safely and open a shipment in the workbench"
description: "A plain TypeScript workbench on the attended API, with checked imports, strict content security and explicit evidence limits."
status: accepted
updated: 2026-11-09
pr: 87
kind: feature
recorded: 2026-11-08
tests: [WebArchitectureConformanceTest, DemoSmokeTest, DemoSecurityHeadersTest]
---

## What changed

The workbench runs on the attended API: choose a synthetic clerk, price a parcel, compare offers, book one,
retry the same booking, and open the shipment and its simulated label. Six TypeScript parts implement the screens
without a framework or runtime packages. Ten REQUIRED relationships have detected production import evidence.

Choose a clerk → compare offers → book Standard → repeat safely → open shipment and label.

Modules are staged under `/assets`, away from authenticated `/api` paths. The server delivers the strict CSP and
serves the entry document for documented page routes, including direct detail reloads. Unknown assets and paths
remain errors. Session changes cancel pending sign-in and announcements; countdown and download updates preserve
interactive controls for keyboard users.

## See it

The documentation site describes the workbench; it does not host the running API. The
[screen specification](../ui/workbench.md), [web model](../architecture/web-components.md) and
[CSP decision](../adr/0019-strict-same-origin-workbench-csp.md) explain its design and limits.

## Try it

Follow the [attended runbook](../flows/attended-demo.md) to compile, stage and launch the workbench with the reviewed
toolchains. To inspect the real-tree import gate separately from the repository root:

```sh
./gradlew :libs:c4-contract-kit:test --rerun --tests '*WebArchitectureConformanceTest'
```

Use Clerk Alpha, NO 0150 to SE 11122, 1200 g and 300 × 200 × 100 mm. Standard is 18.81 EUR.
Use only synthetic recipient details. Stop with Ctrl-C; memory data disappears. The launcher requires the reviewed
JDK and refuses an occupied port. No container or database is required for this attended mode.

## Evidence

Recorded output — not rerun by this site build.

The original source recording was captured on 2026-11-08. On 2026-11-09, `tsc -p apps/shipping-web` exited 0. Exactly `node --test` from the web workspace reported:

```text
ℹ tests 130
ℹ suites 35
ℹ pass 130
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
```

Red-first regressions exposed direct-route 404s, the old response policy, emitted `/api` module collisions,
a delayed announcement surviving session end and a disposed sign-in form accepting a late response. The corrected
code passes those checks. Real-HTTP smoke keeps missing-file, traversal, symlink and API-authentication assertions.
An attended browser recording observed sign-in, the three expected prices (15.05, 18.81, 28.22 EUR), Standard booking,
replay with the same tracking number, shipment details and successful label preparation. Direct shipment reload
returned through sign-in to the same drawer. The download focus regression first left focus on the document body;
after correction it remained on the Download label button. A separate countdown regression first lost focus on
the replay action; the corrected control kept focus through a 15-second update. Switching to Clerk Beta cleared
Alpha's announcements and shipment list; opening Alpha's shipment URL as Beta showed "Shipment not found."
An empty quote submission focused its error summary and marked eight fields invalid.

At 320 CSS pixels, a regression widened the shipment page to 592 pixels because hidden price descriptions escaped
the table's scroll container. After correction, the page stays 320 pixels wide and the table scrolls inside its
labelled region. The light quote and dark shipment drawer were visually reviewed at that width.

The browser displayed the filename and a Blob download link, but the in-app browser did not report a completed
file download. The UI says "Label ready" and asks the user to check their browser downloads; a regression test
rejects the earlier saved-file claim. This recording does not prove that a file was saved. HTTP and adapter tests separately check the
label response and filename. No console warnings or errors were observed during this walkthrough.

The publishing build counts the three named Java classes above only when they pass in that build. Node output
and attended observations are recordings, not evidence rerun by the documentation build.

**Implementation record.**

Issue #60, pull request #87. The current integration and fixes were implemented with Codex model `gpt-6-astra`,
reasoning effort `xhigh`; these settings were verified from session metadata. The independent review used a separate
agent inheriting those settings. A prior Codex web continuation used the same verified settings; other earlier
authorship is not yet verified. The PR records reviewed
commit IDs and any subsequent model changes; unknown values must not be inferred from an agent's product name.

## Limits

The four web capabilities remain planned: their named component and Playwright acceptance tests are absent, and
the docs build does not consume Node results. The attended flow uses disposable memory, not PostgreSQL. No axe,
screen-reader, full keyboard audit or adversarial CSP enforcement result is claimed. Cancellation and label preview
remain deferred. The compiler does not compare its graph with the model, and the partial import reader's known
spread-syntax misses remain. Dependencies, the reviewed lock, security records and scenario coverage debt are unchanged.
