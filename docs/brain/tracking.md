---
title: "Brain: tracking"
description: "Generated summary of module tracking for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-06
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/tracking.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: tracking

Planned: receives carrier tracking events and moves shipments through transit states.

- **Status in the model:** planned (`contract.status`). No production class may exist in its packages until the status is built.
- **Packages:** `io.github.stigjohnny.shipping.tracking`
- **Risk level:** 3
- **Specification:** [docs/modules/tracking.md](../modules/tracking.md)
- **Overview of all modules:** [BRAIN.md](../../BRAIN.md)

## Dependencies

- **Uses:**
  - shipments (PERMITTED, planned): Will move shipments through carrier transit states [Java]
- **Used by:** none.
- **Other relationships, not checked by the Java check:**
  - to Carrier (simulated) (not checked, planned): Will receive tracking events [HTTPS webhook]

## HTTP operations

None in `contracts/shipping.openapi.yaml`.

## Capabilities

| Capability | Status | Acceptance tests | Evidence |
|---|---|---|---|
| `tracking.carrier-tracking-events` | planned | none named yet | none yet |

## Decisions its specification names

None.

## Flows

None.

## Gaps that name it

- 4. Domain simplifications: Shipment states beyond `BOOKED` and `CANCELLED` wait for the planned tracking module.
