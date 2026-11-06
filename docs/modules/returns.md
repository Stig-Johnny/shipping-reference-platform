---
title: "Module: returns (planned)"
description: "Planned module that will create return shipments for delivered shipments. In the model, no code yet."
status: accepted
updated: 2026-11-06
module: returns
moduleStatus: planned
riskLevel: 3
---

# Module: returns (planned)

Package `io.github.stigjohnny.shipping.returns`, reserved. C4 component `returns` with `contract.status planned`.
There is no code, and code must not appear in this package until the model says `built`.

## Intended responsibilities

- Create a return shipment, from recipient back to the webshop, for a delivered shipment.
- Requires a DELIVERED shipment, obtained through shipments.api; no direct dependency on tracking is declared.

## Planned relationships

| Direction | Mode | Status |
|---|---|---|
| returns -> shipments | PERMITTED | Not evaluated while returns is planned. |

## What changes when it is built

One pull request, risk level 3, that changes `contract.status` to `built`, adds `returns -> tenants` as REQUIRED,
decides whether `returns -> shipments` becomes REQUIRED, extends the contract, adds a tenant-scoped schema with
forced row-level security, and rewrites this document in full detail.

## Risk level

Level 3 (rule `planned-modules`).

## Capabilities

`returns.return-shipments` (planned, no evidence).
