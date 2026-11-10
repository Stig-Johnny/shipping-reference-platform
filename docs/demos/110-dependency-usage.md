---
title: "Reviewed packages link to current declarations"
description: "See where a reviewed dependency is declared, with locations refreshed whenever the documentation is built."
status: accepted
updated: 2026-11-10
pr: 110
kind: visual
recorded: 2026-11-10
tests: [DependencyUsageTest, DependencyUsagePageTest]
---

## What changed

The dependency register previously showed where a package was approved for use. It now also shows where that exact
package is currently declared, with clickable file locations and source excerpts. Removing a declaration or changing
its version removes the old location on the next successful site build. The review history stays available.

## See it

Open the [dependency register](https://stig-johnny.github.io/shipping-reference-platform/trust/allowlist.html#entries).
Find `org.jspecify:jspecify` and expand **Current declarations**. Each link opens the corresponding lockfile line in
the same page. TypeScript shows an exact package-lock field; pinned Actions show workflow steps. **Approved scope**
remains a separate column. Entries with no match say **No declaration found in the scanned inputs**; unsupported
kinds say **Evidence not collected for this kind**.

## Evidence

Recorded output — not rerun by this site build.

On 2026-11-10, a macOS checkout using Temurin JDK 25.0.5+8 passed the ordinary Java check with reviewed cached
dependencies and offline mode. Generation produced 4,485 passing tests across 105 reports, with no failures,
errors or skipped tests. The subsequent complete check exited zero; its final output excerpt was:

```text
docs: wrote the site, 102 files

> Task :tools:docs:check

BUILD SUCCESSFUL in 2m 7s
22 actionable tasks: 4 executed, 18 up-to-date
```

The excerpt omits earlier task output and describes the initial implementation before this demo was added. Reader
tests cover version changes, deletion, exact identities, malformed inputs and bundle-carrier removal. Renderer tests
check source excerpts, link targets and the disappearance of removed usage after a fresh source read. The final
focused suite had 31 passing tests, including existing review-boundary and fixture tests.

The Codex in-app browser inspection covered the
[register and evidence targets](https://stig-johnny.github.io/shipping-reference-platform/trust/allowlist.html#usage-evidence)
in a local build at 1280-pixel and 360-pixel widths. Maven, npm, Action and bundled-carrier links reached the expected
source excerpts. Native disclosures opened and closed with Enter; the narrow page remained within the viewport.
The rejected logging bridge retained its verdict with no detected declaration; the browser-binary entry explicitly
reported missing usage coverage. No browser warnings or errors were observed. Independent review and final-head
checks are recorded separately in pull request #110.

## Limits

A declaration does not prove installation or execution. Gradle locks identify modules and configurations, not
artifact variants or byte identity. Bundle links identify reviewed carriers in the npm lock, without re-inspecting
their archives. Local tools, containers, runtime downloads and other unsupported sources remain unverified. A missing
match is not proof that a package is unused. Links show same-build evidence, not an editable repository browser.

The public page refreshes after a successful Pages deployment. Publication rereads these inputs every time; the
unchanged pinned build script does not invalidate the unit-test task for every lock-only edit, so such validation
must explicitly rerun the tests. The wide register can scroll horizontally on small screens. No runtime, approval,
dependency version or security control changed.
