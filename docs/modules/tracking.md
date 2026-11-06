---
title: "Module: tracking (planned)"
description: "Planned module that will receive carrier tracking events and move shipments through transit states. In the model, no code yet."
status: accepted
updated: 2026-11-06
module: tracking
moduleStatus: planned
riskLevel: 3
---

# Module: tracking (planned)

Package `io.github.stigjohnny.shipping.tracking`, reserved. C4 component `tracking` with `contract.status planned`.
There is no code, and code must not appear in this package until the model says `built`.

## Intended responsibilities

- Receive tracking events from a carrier (the C4 relationship `tracking -> carrier`, planned; a real carrier
  integration is out of scope for v0.1).
- Move shipments from `BOOKED` to `IN_TRANSIT` and `DELIVERED`. This needs a contract change to `ShipmentStatus`
  and a public operation in `shipments.api`.

## Planned relationships

| Direction | Mode | Status |
|---|---|---|
| tracking -> shipments | PERMITTED | Not evaluated while tracking is planned. |
| tracking -> carrier | documentation | Planned network relationship; not checked statically. |

## What changes when it is built

One pull request, risk level 3, that changes `contract.status` to `built`, adds `tracking -> tenants` as REQUIRED,
decides whether `tracking -> shipments` becomes REQUIRED, extends the contract and the shipment lifecycle, adds a
tenant-scoped schema with forced row-level security, and rewrites this document in full detail.

## Risk level

Level 3 (rule `planned-modules`).

## Capabilities

`tracking.carrier-tracking-events` (planned, no evidence).
