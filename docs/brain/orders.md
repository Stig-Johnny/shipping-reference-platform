---
title: "Brain: orders"
description: "Generated summary of module orders for agents: status, dependencies, HTTP operations, capabilities with their evidence, decisions, flows and gaps."
status: accepted
updated: 2026-11-06
---

> Generated—do not hand-edit. Sources: `architecture/workspace.dsl`, `docs/modules/orders.md`, `docs/capabilities/capabilities.yaml`, `contracts/shipping.openapi.yaml` and `docs/`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: orders

Planned: imports webshop orders and turns them into quote and booking requests.

- **Status in the model:** planned (`contract.status`). No production class may exist in its packages until the status is built.
- **Packages:** `io.github.stigjohnny.shipping.orders`
- **Risk level:** 3
- **Specification:** [docs/modules/orders.md](../modules/orders.md)
- **Overview of all modules:** [BRAIN.md](../../BRAIN.md)

## Dependencies

- **Uses:**
  - quotes (PERMITTED, planned): Will request quotes for imported orders [Java]
  - shipments (PERMITTED, planned): Will book shipments for imported orders [Java]
- **Used by:** none.
- **Other relationships, not checked by the Java check:**
  - from Webshop (not checked, planned): Will submit orders to ship [JSON over HTTPS]

## HTTP operations

None in `contracts/shipping.openapi.yaml`.

## Capabilities

| Capability | Status | Acceptance tests | Evidence |
|---|---|---|---|
| `orders.import-webshop-orders` | planned | none named yet | none yet |

## Decisions its specification names

None.

## Flows

None.

## Gaps that name it

No module-specific gaps are recorded here. See the project-wide known gaps in [docs/gaps.md](../gaps.md).
