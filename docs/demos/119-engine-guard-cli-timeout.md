---
title: "The engine guard tolerates a slow CLI on a freshly booted runner"
description: "The first-step guard gives docker info 30 seconds instead of 5; the engine must still be unreachable, and a CLI that never answers still fails the job."
status: accepted
updated: 2026-11-15
pr: 119
kind: build
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

Captured on 2026-11-15 on macOS with bash. A new fixture models a stopped engine whose CLI needs 10 seconds to
answer. Against the 5-second guards it failed:

```text
FAIL .github/workflows/java.yml guard 1: slow-cli exited 124, expected 0
```

With the 30-second guards, every fixture passed, including a CLI that never answers, which must still fail:

```text
90 passed: 9 workflow guards reject reachable CLI/socket, failed stop/mask/unlink and probe errors/timeouts; shutdown precedes probes; stopped and slow-CLI stopped pass
```

The hosted failures that prompted this are recorded on issue #117: six jobs between 19:15 and 20:20 UTC on
2026-11-15 failed with exit 124 at this probe, never with an engine found reachable.

Review reference: pull request #119, issue #117. `ContainerEngineWorkflowTest` is current-build evidence only when
it passes in the publishing build; the shell regression's output above is a recording.

## Limits

Why `docker info` was slow is likely but not proven: it starts each CLI plugin to list it, which can take seconds on a
freshly booted runner. The fixture models a 10-second CLI; hosted runs of this change, not the fixture, show whether
30 seconds suffices. The guard still cannot tell a slow CLI from a hung one: both fail once the bound passes.
