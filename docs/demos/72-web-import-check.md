---
title: "The web app's imports are checked against the diagram in every build"
description: "The model now holds the web app's six parts and their allowed imports; every build runs partial import lint on apps/shipping-web, including package and Node.js built-in checks for detected imports, and rejects files in planned parts."
status: accepted
updated: 2026-11-08
pr: 72
kind: gate
recorded: 2026-11-08
tests: [WebArchitectureConformanceTest, WebConformanceTest]
---

## What changed

Before, the diagram had no parts for the web app, and nothing compared its TypeScript with the model. Now the model
names six parts of the web app (app, auth, api, quote-feature, shipment-feature and ui), all planned, and thirteen
import relationships between them. Every build runs the web import check on `apps/shipping-web`: a file in a planned
part fails it, and so does a detected import of a package or a Node.js built-in that the model does not list or the
dependency allowlist does not approve. No part lists a package, and browser code may import no Node.js built-in. The gate reads the source root,
`e2e` test root, public root and app configuration; adding, editing or removing an `e2e` file reruns it. ADR 0012
lists this gate as implemented partial lint: no real web imports exist yet, and the reader can silently miss imports
and unsupported constructs (ADR 0013).

## See it

Sheet M-06, [the components of the web app](https://stig-johnny.github.io/shipping-reference-platform/model/web-components.html):
the six parts and their arrows, each part marked planned. Its status panel names the web parts and the web import
check's tests, says that no import of the web app has been checked yet, and does not show the API's modules.

## Try it

From the repository root, with the reviewed JDK as `JAVA_HOME`, run the check; it passes. Then create the file
`apps/shipping-web/src/app/main.ts` with the line `import { readFileSync } from 'node:fs';` and run it again; it
fails with two violations. Delete the file and its two new directories afterwards.

```sh
./gradlew :libs:c4-contract-kit:test --rerun --tests '*WebArchitectureConformanceTest'
```

The report is `libs/c4-contract-kit/build/reports/c4-contract-web/report.md`. The test class also creates a
synthetic repository using the real model, with an import of `node:fs` under `apps/shipping-web/e2e` and no Node.js
runtime approval, and asserts that the gate rejects it. It reads that TypeScript as data; it runs no browser test.

## Evidence

Recorded output — not rerun by this site build.

Recorded 2026-11-08T00:22:00Z on macOS with Temurin 25.0.5+8, on revision d81cf47 of pull request #72, with the
file above and a second line, `export const main = readFileSync;`. The command exited 1; excerpt of the report: the
verdict, the two violations and the row of the packages and built-ins table (blank lines, the violation's evidence
table and the edge and component tables are left out):

```text
**Verdict: FAILED** with 2 violations. Model: `../../architecture/workspace.dsl`. Web root: `apps/shipping-web/src`. Test roots: none. Public roots: `apps/shipping-web/public`. Report format: `c4-contract-kit/web-report/v1`.
### NODE_BUILTIN: apps/shipping-web/src/app/main.ts, node:fs
apps/shipping-web/src/app/main.ts imports the Node.js built-in node:fs: code that runs in the browser may not import Node.js built-ins.
### PLANNED_COMPONENT_HAS_CODE: app, apps/shipping-web/src/app/main.ts
app is planned, but its web.paths contain apps/shipping-web/src/app/main.ts.
| `apps/shipping-web/src/app/main.ts:1` | `node:fs` | `node:fs` | (none) | no Node.js built-in in browser code | (none) | refused |
```

Recorded 2026-11-08T00:22:02Z on the same revision, after the file was deleted: the command exited 0; excerpt of the
report (blank lines and the edge and component tables are left out):

```text
**Verdict: PASSED** with no violations. Model: `../../architecture/workspace.dsl`. Web root: `apps/shipping-web/src`. Test roots: none. Public roots: `apps/shipping-web/public`. Report format: `c4-contract-kit/web-report/v1`.
## Packages and built-ins
None.
```

The recordings above predate the addition of the `e2e` test root: their "Test roots: none" is historical output.
WebArchitectureConformanceTest now checks the repository and its declared test root in every build, including the
synthetic unapproved-import fixture above; WebConformanceTest checks each rule on synthetic web apps. These named
tests are evidence from the publishing build only when they pass there. Reviewed in pull request #72.

## Limits

- Every part is planned and the source and `e2e` test roots hold no real files, so the check has evaluated no
  import of the web app: the rules on imports between parts, cycles, REQUIRED relationships, packages and built-ins
  are shown on synthetic fixtures only (WebConformanceTest), not on the real workbench.
- The parts become built, and their imports evaluated, in the pull request that adds the workbench's code.
- The reader compares only detected imports. The known spread-syntax bypass can hide imports and loaders without
  a refusal, so a passing gate does not establish complete boundary or cycle coverage. The authoritative compiler
  import graph and browser-enforced web-app CSP remain planned (ADR 0013; known gaps, section 6).
- A detected import shows a reference, not behaviour, and the web app's calls to the API are network calls that the
  check does not see. Its other limits are in the known gaps, section 1.
