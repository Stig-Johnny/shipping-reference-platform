---
title: "The workbench has tested shipping logic and a strict resource policy"
description: "Parcel, quote and booking sources now have logic tests and checked imports, while the record makes missing browser and server evidence explicit."
status: accepted
updated: 2026-11-08
pr: 87
kind: feature
recorded: 2026-11-08
tests: [WebArchitectureConformanceTest]
---

## What changed

The workbench now has six implemented TypeScript parts: the application shell, synthetic sign-in, the API adapter,
quote and shipment screens, and shared UI components. Logic tests exercise validation, quote expiry, booking key
reuse and recovery, pagination, labels and display formatting. The entry page declares strict same-origin content
security, with no inline execution, evaluated strings or external origins.

All ten REQUIRED relationships have detected production imports. The CI web job installs the reviewed lock,
compiles the app and runs the workspace tests after the Java job has checked the candidate. Its first step disables
and probes the container engine, as the other jobs do. No new Action or dependency is added.

## See it

Read the [workbench specification](../ui/workbench.md#implementation-evidence-2026-11-08) for the implemented source
scope and missing acceptance evidence, the [web model](../architecture/web-components.md) for the six built parts,
and [the policy decision](../adr/0017-strict-same-origin-workbench-csp.md) for the exact CSP. These are documentation
pages; the site does not host a running workbench.

## Try it

With the reviewed JDK already installed, run the real-tree import gate from the repository root. The command
reads the model and web sources and writes reports under the library's build directory; it needs no database,
container engine or browser. No fixture setup or cleanup is needed.

```sh
./gradlew :libs:c4-contract-kit:test --rerun --tests '*WebArchitectureConformanceTest'
```

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-08 on macOS arm64 with Temurin 25.0.5+8 and reviewed Node 26.13.0, in a complete uncommitted
source copy while the original worktree's rebase was blocked by Git metadata permissions. Prepared for the web
change for issue #60; the demo number is provisional until the pull request is opened.

The existing 124 logic tests passed before the new CSP test was added. That test failed on the old policy, then
all 125 passed after the entry page declared the explicit directives. Exactly `node --test`, with working directory
`apps/shipping-web`, exited 0. Individual test names and timing are omitted from this verbatim summary:

```text
ℹ tests 125
ℹ suites 35
ℹ pass 125
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
```

The import gate exited 0 after the parts were marked built and sources used syntax the partial reader supports.
Its report's recorded verdict is:

```text
**Verdict: PASSED** with no violations. Model: `../../architecture/workspace.dsl`. Web root: `apps/shipping-web/src`. Test roots: `apps/shipping-web/e2e`. Public roots: `apps/shipping-web/public`. Report format: `c4-contract-kit/web-report/v1`.
```

The report lists ten REQUIRED relationships with production import evidence, one used PERMITTED relationship and
two PERMITTED relationships with no static evidence. The compiler command `tsc -p apps/shipping-web` exited 0
without output. The policy checker reported a clean allowlist, with existing non-blocking check 8 warnings.

Separately, the current publishing build counts only `WebArchitectureConformanceTest` as this demo's evidence when
it passes. Node output above is a recording; it is not verified or rerun by the documentation build.

## Limits

The API does not serve the app yet, so no web response header or integrated browser flow is tested. Future serving
must send the policy as a response header too. No Playwright e2e, axe, keyboard or screen-reader pass is claimed.
The four web capabilities stay planned because their named component and end-to-end acceptance tests are absent.
The overall web container remains planned while its six source parts are built.

The CSP test checks declaration and placement, not enforcement in a browser. The compiler does not supply a graph
comparison with the model; the import reader remains partial, with known spread-syntax misses. Hosted execution of
the new CI job is pending. No dependency, lockfile, security record or model relationship was changed.
