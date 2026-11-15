---
title: "The engine guard tolerates a slow CLI on a freshly booted runner"
description: "The first-step guard gives docker info 30 seconds instead of 5; the engine must still be unreachable, and a CLI that never answers still fails the job."
status: accepted
updated: 2026-11-15
pr: 119
kind: gate
recorded: 2026-11-15
tests: [ContainerEngineWorkflowTest]
---

## What changed

Every CI job starts by stopping the container engine and proving it unreachable. One of its two probes runs
`docker info`, which must fail. On freshly booted hosted runners that command sometimes took longer than its
5-second limit while the engine was stopped, so the guard failed closed and required checks went red at random.
The probe now has 30 seconds. Nothing else changed: the CLI and the socket must both report the engine unreachable,
and a CLI that has not answered within the limit still fails the job.

## Try it

With the reviewed JDK, run the repository checks from its root. `ContainerEngineWorkflowTest` verifies that every job
starts with the guard and that only the reviewed database window turns the engine on. No cleanup is needed.

```sh
./gradlew check
```

The guard regression itself, `tools/ci/engine-guard-test.sh`, is in the [README's Running it table](../../README.md#running-it).
It runs all nine inline guards against fake commands, never contacts an engine or runs sudo, and runs in the `java`
CI job.

## Evidence

Recorded output — not rerun by this site build.

Captured on 2026-11-15 on macOS with bash, by running `bash tools/ci/engine-guard-test.sh` from the repository root;
the local path prefix before `tools/` is removed from the excerpts. The fake `timeout` accepts any whole-second bound,
and a `slow-cli` fixture models a stopped engine whose CLI answers after 10 seconds. With all nine guards set back to
the old 5-second bound, in a scratch copy, the run exited 1:

```text
FAIL tools/ci/../../.github/workflows/java.yml guard 1: slow-cli exited 124, expected 0
```

With a bound of 15 seconds in every guard, it exited 1 on the pin instead:

```text
FAIL tools/ci/../../.github/workflows/java.yml guard 1: the docker info probe must have exactly the reviewed 30-second bound
```

At this pull request's head, with 30 seconds in every guard, it exited 0. A CLI that never answers still fails:

```text
90 passed: 9 workflow guards reject reachable CLI/socket, failed stop/mask/unlink and probe errors/timeouts; shutdown precedes probes; stopped and slow-CLI stopped pass
```

The hosted failures that prompted this are recorded on issue #117: six jobs between 19:15 and 20:20 UTC on
2026-11-15 failed with exit 124 at this probe, never with an engine found reachable.

Review reference: pull request #119, issue #117. `ContainerEngineWorkflowTest` checks that every guard carries the
30-second bound; it is current-build evidence only when it passes in the publishing build. The shell regression's
output above is a recording.

## Limits

Why `docker info` was slow is likely but not proven: it starts each CLI plugin to list it, which can take seconds on a
freshly booted runner. The fixture models a 10-second CLI; hosted runs of this change, not the fixture, show whether
30 seconds suffices. The guard still cannot tell a slow CLI from a hung one: both fail once the bound passes.
