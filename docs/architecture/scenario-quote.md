---
title: "C4 view: Executable scenario: quote a parcel"
description: "Dynamic view of the API. Executable scenario: quote a parcel. QuoteScenarioTest runs it on the code and checks its steps (architecture/scenarios/scenario-quote.json)."
status: accepted
updated: 2026-11-08
---

> Generated—do not hand-edit. Source: `architecture/workspace.dsl`, view `ScenarioQuote`. Regenerate with
> `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# C4 view: Executable scenario: quote a parcel

Executable scenario: quote a parcel. QuoteScenarioTest runs it on the code and checks its steps (architecture/scenarios/scenario-quote.json).

[Open this diagram on the documentation site for enlargement and its text equivalent](https://stig-johnny.github.io/shipping-reference-platform/model/scenario-quote.html#figure-scenario-quote)

```mermaid
flowchart LR
    accTitle: Executable scenario: quote a parcel
    accDescr: Dynamic view with 6 elements and 6 relationships. The tables on the page list every one of them.
    1["Shipping clerk [Person]"]
    style 1 fill:#0f766e,stroke:#475569,color:#ffffff
    4["Carrier (simulated) [Software System] (external, simulated)"]
    style 4 fill:#64748b,stroke:#475569,color:#ffffff
    subgraph 5 ["Shipping reference platform"]
        style 5 fill:#ffffff,stroke:#475569,color:#475569
        subgraph 13 ["API"]
            style 13 fill:#ffffff,stroke:#475569,color:#475569
            14["Quotes [Component] (built)"]
            style 14 fill:#ccfbf1,stroke:#0f766e,color:#134e4a
            16["Tenants [Component] (built)"]
            style 16 fill:#ccfbf1,stroke:#0f766e,color:#134e4a
        end
        21[("Database [Container]")]
        style 21 fill:#115e59,stroke:#475569,color:#ffffff
        6["Web app [Container] (planned)"]
        style 6 fill:#f1f5f9,stroke:#64748b,color:#334155
    end
    1 -->|"1"| 6
    6 -->|"2"| 14
    14 ==>|"3 REQUIRED"| 16
    14 -->|"4"| 4
    14 -->|"5"| 21
    14 -->|"6"| 21
```

The same definition is in [scenario-quote.mmd](scenario-quote.mmd).

## How to read it

- Each box is an element of the model: its name, its C4 type in brackets and, in parentheses, its implementation status.
- "(planned)" marks an element that has no code yet.
- Each line is labelled with its number in the view's relationships table and, where it has one, its mode.
- A thick line is a REQUIRED dependency between modules. Where both modules are built, the checker in libs/c4-contract-kit fails when the compiled classes have no static reference for it.
- A solid thin line is a relationship of people, systems or containers, which module conformance does not cover.
- Arrows point from the element that depends on the other.

## Elements

| ID | Element | Type | Technology | Status | Description |
|---|---|---|---|---|---|
| 1 | Shipping clerk | Person |  | Role; implementation status not applicable | Works for a webshop. Prices parcels, books shipments and downloads labels. |
| 4 | Carrier (simulated) | Software System |  | external, simulated | A fictional parcel carrier. v0.1 simulates its tariff, tracking numbers and labels inside the API; no network calls. |
| 6 | Web app | Container | Plain TypeScript SPA (no framework), TypeScript 5.9.3 | Planned; not covered by module conformance | Shipping workbench for clerks: parcel form, comparable quote cards, shipment list and detail drawer. |
| 14 | Quotes | Component | Spring Modulith module | built | Prices parcels with the synthetic tariff and issues quotes whose offers expire after 30 minutes. |
| 16 | Tenants | Component | Spring Modulith module | built | Authenticates synthetic users, derives the tenant from membership, binds it to every database transaction for row-level security, and evaluates prototype flags. |
| 21 | Database | Container | PostgreSQL 18.6 | Not covered by module conformance | One schema per v0.1 module plus the event publication registry, with forced row-level security on tenant data. Built: the schemas tenants and quotes (migrations V1 and V2; CI verification pending). Planned: shipments, audit and the registry. |

## Relationships

| Step | From | To | Description | Technology | Line |
|---|---|---|---|---|---|
| 1 | Shipping clerk | Web app | Enters a parcel, its origin and its destination | HTTPS, browser | solid: not covered by module conformance |
| 2 | Web app | Quotes | POST /api/v1/quotes (createQuote), answered 201 with the quote | JSON over HTTPS | solid: not covered by module conformance |
| 3 | Quotes | Tenants | CurrentTenant.require(): the tenant bound for the request | Java | thick: REQUIRED dependency |
| 4 | Quotes | Carrier (simulated) | Rates each service level with the synthetic tariff, simulated in-process | In-process simulation | solid: not covered by module conformance |
| 5 | Quotes | Database | Inserts the quote and its offers in schema quotes | Spring JDBC (JdbcClient) | solid: not covered by module conformance |
| 6 | Quotes | Database | Publishes QuoteIssued in the quote's transaction; no module listens to it in v0.1 | Spring JDBC (JdbcClient) | solid: not covered by module conformance |

## What this view cannot show

- A diagram shows the model, not the code. The API's modules are checked against its compiled classes and the web app's parts against detected TypeScript imports using partial lint (docs/gaps.md, section 1).
- Each numbered step is a relationship of the model, in the order of the flow. Where a listener handles another module's event, its arrow shows the code dependency, not the direction in which the event travels; the authored sequence diagrams under docs/flows/ describe runtime delivery.
