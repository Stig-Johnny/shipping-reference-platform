---
title: "C4 view: Containers"
description: "Container view of the Shipping reference platform. The web app, the API (one deployable modular monolith) and the database."
status: accepted
updated: 2026-11-06
---

> Generated—do not hand-edit. Source: `architecture/workspace.dsl`, view `Containers`. Regenerate with
> `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# C4 view: Containers

The web app, the API (one deployable modular monolith) and the database.

```mermaid
flowchart LR
    accTitle: Containers
    accDescr: Container view with 7 elements and 8 relationships. The tables on the page list every one of them.
    1["Shipping clerk [Person]"]
    style 1 fill:#0f766e,stroke:#475569,color:#ffffff
    2["Platform administrator [Person]"]
    style 2 fill:#0f766e,stroke:#475569,color:#ffffff
    3["Webshop [Software System] (external)"]
    style 3 fill:#64748b,stroke:#475569,color:#ffffff
    4["Carrier (simulated) [Software System] (external, simulated)"]
    style 4 fill:#64748b,stroke:#475569,color:#ffffff
    subgraph 5 ["Shipping reference platform"]
        style 5 fill:#ffffff,stroke:#475569,color:#475569
        15[("Database [Container] (planned)")]
        style 15 fill:#115e59,stroke:#475569,color:#ffffff
        6["Web app [Container] (planned)"]
        style 6 fill:#115e59,stroke:#475569,color:#ffffff
        7["API [Container] (planned)"]
        style 7 fill:#115e59,stroke:#475569,color:#ffffff
    end
    1 -->|"1"| 3
    1 -->|"2"| 6
    2 -.->|"3 — planned"| 6
    2 -->|"4"| 15
    6 -->|"5"| 7
    3 -.->|"6 — planned"| 7
    7 -->|"7"| 15
    7 -->|"8"| 4
```

The same definition is in [containers.mmd](containers.mmd).

## How to read it

- Each box is an element of the model: its name, its C4 type in brackets and, in parentheses, its implementation status.
- "(planned)" marks an element that has no code yet.
- Each line is labelled with its number in the view's relationships table and, where it has one, its mode.
- A dotted line labelled planned is a relationship that does not exist yet.
- A solid thin line is a relationship of people, systems or containers, which module conformance does not cover.
- Arrows point from the element that depends on the other.

## Elements

| ID | Element | Type | Technology | Status | Description |
|---|---|---|---|---|---|
| 1 | Shipping clerk | Person |  | Role; implementation status not applicable | Works for a webshop. Prices parcels, books shipments and downloads labels. |
| 2 | Platform administrator | Person |  | Role; implementation status not applicable | Provisions tenants and memberships for webshops. |
| 3 | Webshop | Software System |  | external | A merchant's online shop (simulated). Source of the orders that need shipping. |
| 4 | Carrier (simulated) | Software System |  | external, simulated | A fictional parcel carrier. v0.1 simulates its tariff, tracking numbers and labels inside the API; no network calls. |
| 6 | Web app | Container | React 19 SPA, TypeScript 7, Vite 8, generated OpenAPI client | Planned; not covered by module conformance | Shipping workbench for clerks: parcel form, comparable quote cards, shipment list and detail drawer. |
| 7 | API | Container | Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring Data JDBC | Planned; not covered by module conformance | Modular monolith exposing contracts/shipping.openapi.yaml. Modules are packages; each has a public api package and private internals. |
| 15 | Database | Container | PostgreSQL 18.6 | Planned; not covered by module conformance | One schema per v0.1 module (quotes, shipments, tenants, audit) plus the event publication registry. Forced row-level security on tenant data. |

## Relationships

| # | From | To | Description | Technology | Line |
|---|---|---|---|---|---|
| 1 | Shipping clerk | Webshop | Manages the orders that need shipping in |  | solid: not covered by module conformance |
| 2 | Shipping clerk | Web app | Prices parcels, books shipments and downloads labels in | HTTPS, browser | solid: not covered by module conformance |
| 3 | Platform administrator | Web app | Will manage tenants and memberships in (planned; no admin screens in v0.1) | HTTPS, browser | dotted: planned |
| 4 | Platform administrator | Database | Provisions synthetic tenants and memberships through reviewed seed migrations run as the migration role | Flyway | solid: not covered by module conformance |
| 5 | Web app | API | Calls the shipping API through the generated client | JSON over HTTPS | solid: not covered by module conformance |
| 6 | Webshop | API | Will submit orders to ship (planned) | JSON over HTTPS | dotted: planned |
| 7 | API | Database | Reads and writes tenant-scoped data as the application role, which lacks BYPASSRLS | JDBC | solid: not covered by module conformance |
| 8 | API | Carrier (simulated) | Obtains rates, tracking numbers and labels (simulated in-process; no network call in v0.1) | In-process simulation | solid: not covered by module conformance |

## What this view cannot show

- A diagram shows the model, not the code. Only the API's modules are checked against the code (docs/gaps.md, section 1).
- Relationships of people, software systems and containers are documentation: they carry no contract mode, and module conformance does not cover them. The planned contract and end-to-end tests are to cover the web app's calls.
