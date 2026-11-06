---
title: "Module: orders (planned)"
description: "Planned module that will import webshop orders and turn them into quote and booking requests. In the model, no code yet."
status: accepted
updated: 2026-11-06
module: orders
moduleStatus: planned
riskLevel: 3
---

# Module: orders (planned)

Package `io.github.stigjohnny.shipping.orders`, reserved. C4 component `orders` with `contract.status planned`.
There is no code, and code must not appear in this package until the model says `built`.

## Intended responsibilities

- Receive orders from a webshop (the C4 relationship `webshop -> orders`, planned).
- Request quotes for an order's parcels and book the chosen offer on the clerk's instruction.

## Planned relationships

| Direction | Mode | Status |
|---|---|---|
| orders -> quotes | PERMITTED | Not evaluated while orders is planned. |
| orders -> shipments | PERMITTED | Not evaluated while orders is planned. |
| webshop -> orders | documentation | Planned network relationship; not checked statically. |

## What changes when it is built

One pull request, risk level 3, that:

1. Changes `contract.status` to `built` and removes the `Planned` tags in `architecture/workspace.dsl`; adds
   `orders -> tenants` as REQUIRED and decides whether `orders -> quotes` and `orders -> shipments` become REQUIRED.
2. Adds the endpoints to `contracts/shipping.openapi.yaml`.
3. Adds the schema `orders` with forced row-level security and extends `RowLevelSecurityCatalogTest` coverage.
4. Rewrites this document in the detail of `docs/modules/quotes.md` and sets `moduleStatus: built`.
5. Moves the capabilities below from `planned` only when their tests exist and pass.

## Risk level

Level 3: any file under `apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/orders/` changes a planned
component into code, which needs a model change and the owner's approval (rule `planned-modules`).

## Capabilities

`orders.import-webshop-orders` (planned, no evidence).
