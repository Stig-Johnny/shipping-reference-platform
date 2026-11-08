---
title: "Build downloads retry briefly and refuse changed bytes"
description: "Temporary download failures get bounded retries while the future mirror remains disabled."
status: accepted
updated: 2026-11-08
pr: 0
kind: build
recorded: 2026-11-08
tests: [PagesWorkflowTest]
---

## What changed

The JDK download now has an explicit time budget and checks the archive before making it available to the build.
An unavailable source can be retried; changed bytes stop the job immediately. A future mirror can be tried before
the official source, but its committed address is empty, so this change still uses only the official source.
Gradle's official download gets a longer network timeout and three retries, with its checksum unchanged.
The Pages build now pins the same reviewed runner version as the other JDK jobs.

## Try it

From the repository root with the reviewed JDK first on the path, check policy and the build:

```sh
java tools/policy/PolicyCheck.java .
./gradlew check
```

The separate fixture command is `bash tools/ci/stage-temurin-test.sh`, also documented in the README. It needs
Bash and the runner's shell utilities, creates synthetic files in a temporary directory and removes them on exit.
It substitutes transport and sleep while using real SHA-256 calculations. It neither downloads nor installs a JDK.

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-08 on a macOS arm64 development host, with Temurin 25.0.5+8 selected for the Java checks.
The shell fixture suite needs no Java. Before implementation, the new tests rejected the old script's transport
options: exit 1, with 20 failures. After implementation the fixture command exited 0. Selected output follows;
the other transport and cleanup cases are omitted. This is the recording for the pending rollout pull request.

```text
PASS: mirror ok; official not contacted (exit 0)
PASS: mirror down; official fallback ok (exit 0)
PASS: mirror wrong bytes; no fallback (exit 65)
PASS: both sources down; bounded failure (exit 69)
PASS: official wrong bytes; stop (exit 65)
```

```text
20 download fixtures, 0 failures
```

The shell fixtures run as their own CI step after the base policy check. They produce no JUnit report and are not
listed as current publishing-build evidence. The site build validates this document but does not rerun its recording.
`PagesWorkflowTest` checks the reviewed Pages build runner in the publishing build; it does not exercise downloads.

## Limits

There is no active mirror, infrastructure or publication in this change. Fixtures assert timeout options and attempt
counts, not elapsed network deadlines or recovery from a live outage. They do not exercise a real TLS connection.
Gradle still uses its wrapper's official download path; a mirror launcher and repeated offline signature verification
need later review. The existing first-use signature evidence is retained. New source reviews and renewed owner
acceptance are required before setting the mirror address. No hosted CI run is claimed here.
