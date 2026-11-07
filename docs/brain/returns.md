---
title: "Brain: returns"
description: "Generated summary of module returns for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-07
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/returns.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: returns

Planned: creates return shipments for delivered shipments.

- **Status in the model:** planned (`contract.status`). No production class may exist in its packages until the status is built.
- **Packages:** `io.github.stigjohnny.shipping.returns`
- **Risk level:** 3
- **Specification:** [docs/modules/returns.md](../modules/returns.md)
- **Overview of all modules:** [BRAIN.md](../../BRAIN.md)

## Dependencies

- **Uses:**
  - shipments (PERMITTED, planned): Will create return shipments for delivered shipments [Java]
- **Used by:** none.
- **Other relationships, not checked by the Java check:** none.

## HTTP operations

None in `contracts/shipping.openapi.yaml`.

## Capabilities

| Capability | Status | Acceptance tests | Evidence |
|---|---|---|---|
| `returns.return-shipments` | planned | none named yet | none yet |

## Decisions its specification names

None.

## Flows

None.

## Gaps that name it

No module-specific gaps are recorded here. See the project-wide known gaps in [docs/gaps.md](../gaps.md).
