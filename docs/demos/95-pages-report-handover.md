---
title: "Larger test reports can cross the publication handover"
description: "Growing test evidence no longer stops the site at a single 100,000-character value; the complete reports and the tested site's integrity are preserved."
status: accepted
updated: 2026-11-09
pr: 95
kind: build
recorded: 2026-11-09
tests: [PagesWorkflowTest]
---

## What changed

The site stopped updating when its test reports grew past the handover's single-value limit. The same complete
archive now travels in up to four smaller parts. The receiver requires every part, verifies the archive's original
digest, and still requires the rebuilt site to match the tested site byte for byte before publication.

## Try it

With the reviewed JDK, run the repository checks from its root. The workflow test verifies that every report part
comes from this run's test job and that the publishing job runs no tests. No fixture changes need cleanup.

```sh
./gradlew check
```

The shell handover fixtures listed in the [README's Try it section](../../README.md#try-it) create and remove their
own synthetic reports, including a large report and damaged handovers. They require bash and GNU command-line
tools, as on the Linux CI runner, and use no container engine.

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-09 on a macOS development machine, using bash, installed GNU tar and local compatibility
adapters for base64 and SHA-256 command-line flags. The Java checks used the reviewed JDK 25.0.5. The handover fixture
contains deterministic synthetic test output; its encoded archive was 364,056 characters. Against the original
sender it exited 1. Excerpt, omitting the twenty passing checks:

```text
FAIL reports larger than one Linux environment value are sent intact (exit 1, expected to pass)
       ::error::the reports take 364056 characters, more than 100000 fit the handover

20 passed, 1 failed
```

After the repair, the fixture suite exited 0. Excerpts omit the other passing checks:

```text
ok   all chunks are reassembled and received
ok   both large ordinary and database reports are byte-identical
ok   the large handover preserves the tested site's digest
ok   a missing middle chunk is refused
ok   a missing last chunk is refused
ok   reordered chunks fail the original archive digest
ok   no invalid chunk handover extracts a report

41 passed, 0 failed
```

The workflow tests first failed on the two missing chunk mappings, then all sixteen passed. The full local checks
and generated-document check also passed. Review: pull request #95. Only the named Java test above counts as
evidence of the build that generates this page; shell output here is recorded evidence.

## Limits

The total handover is still bounded: four parts of at most 100,000 characters, below both Linux's per-value limit
and GitHub's aggregate job-output budget. Larger archives fail explicitly and need a separately reviewed
transport. Local checks do not run PostgreSQL or prove a successful Pages deployment; native Linux fixtures,
database evidence and publication must be checked in CI, and the live revision after merge.
