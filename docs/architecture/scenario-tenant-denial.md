---
title: "C4 view: Executable scenario: another tenant's quote is not found"
description: "Dynamic view of the API. Executable scenario: another tenant's quote is not found. TenantDenialScenarioTest runs it on the code and checks its steps (architecture/scenarios/scenario-tenant-denial.json)."
status: accepted
updated: 2026-11-07
---

> Generated—do not hand-edit. Source: `architecture/workspace.dsl`, view `ScenarioTenantDenial`. Regenerate with
> `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# C4 view: Executable scenario: another tenant's quote is not found

Executable scenario: another tenant's quote is not found. TenantDenialScenarioTest runs it on the code and checks its steps (architecture/scenarios/scenario-tenant-denial.json).

[Open this diagram on the documentation site for enlargement and its text equivalent](https://stig-johnny.github.io/shipping-reference-platform/model/scenario-tenant-denial.html#figure-scenario-tenant-denial)

```mermaid
flowchart LR
    accTitle: Executable scenario: another tenant's quote is not found
    accDescr: Dynamic view with 6 elements and 5 relationships. The tables on the page list every one of them.
    1["Shipping clerk [Person]"]
    style 1 fill:#0f766e,stroke:#475569,color:#ffffff
    subgraph 5 ["Shipping reference platform"]
        style 5 fill:#ffffff,stroke:#475569,color:#475569
        subgraph 7 ["API"]
            style 7 fill:#ffffff,stroke:#475569,color:#475569
            10["Tenants [Component] (built)"]
            style 10 fill:#ccfbf1,stroke:#0f766e,color:#134e4a
            8["Quotes [Component] (built)"]
            style 8 fill:#ccfbf1,stroke:#0f766e,color:#134e4a
            9["Shipments [Component] (planned)"]
            style 9 fill:#f1f5f9,stroke:#64748b,color:#334155
        end
        15[("Database [Container] (planned)")]
        style 15 fill:#f1f5f9,stroke:#64748b,color:#334155
        6["Web app [Container] (planned)"]
        style 6 fill:#f1f5f9,stroke:#64748b,color:#334155
    end
    1 -->|"1"| 6
    6 -->|"2"| 9
    9 ==>|"3 REQUIRED"| 10
    9 ==>|"4 REQUIRED"| 8
    8 -->|"5"| 15
```

The same definition is in [scenario-tenant-denial.mmd](scenario-tenant-denial.mmd).

## How to read it

- Each box is an element of the model: its name, its C4 type in brackets and, in parentheses, its implementation status.
- "(planned)" marks an element that has no code yet.
- Each line is labelled with its number in the view's relationships table and, where it has one, its mode.
- A thick line is a REQUIRED dependency between modules. Where both modules are built, the checker in libs/c4-contract-kit fails when the compiled classes have no static reference for it.
- The checker does not evaluate a line that touches a planned module until both modules are built.
- A solid thin line is a relationship of people, systems or containers, which module conformance does not cover.
- Arrows point from the element that depends on the other.

## Elements

| ID | Element | Type | Technology | Status | Description |
|---|---|---|---|---|---|
| 1 | Shipping clerk | Person |  | Role; implementation status not applicable | Works for a webshop. Prices parcels, books shipments and downloads labels. |
| 6 | Web app | Container | Plain TypeScript SPA (no framework), TypeScript 5.9.3 | Planned; not covered by module conformance | Shipping workbench for clerks: parcel form, comparable quote cards, shipment list and detail drawer. |
| 8 | Quotes | Component | Spring Modulith module | built | Prices parcels with the synthetic tariff and issues quotes whose offers expire after 30 minutes. |
| 9 | Shipments | Component | Spring Modulith module | planned | Books shipments from quote offers with tenant-scoped idempotency, cancels them and renders simulated labels. Publishes ShipmentBooked and ShipmentCancelled. |
| 10 | Tenants | Component | Spring Modulith module | built | Authenticates synthetic users, derives the tenant from membership, binds it to every database transaction for row-level security, and evaluates prototype flags. |
| 15 | Database | Container | PostgreSQL 18.6 | Planned; not covered by module conformance | One schema per v0.1 module (quotes, shipments, tenants, audit) plus the event publication registry. Forced row-level security on tenant data. |

## Relationships

| Step | From | To | Description | Technology | Line |
|---|---|---|---|---|---|
| 1 | Shipping clerk | Web app | A clerk of Tenant Beta books an offer of Tenant Alpha's quote | HTTPS, browser | solid: not covered by module conformance |
| 2 | Web app | Shipments | POST /api/v1/shipments (bookShipment), answered 404 quote-not-found, as for an unknown quote | JSON over HTTPS | solid: not covered by module conformance |
| 3 | Shipments | Tenants | CurrentTenant.require(): Tenant Beta | Java | thick: REQUIRED dependency |
| 4 | Shipments | Quotes | QuoteOffers.findOffer for Tenant Beta: QuoteNotFound | Java | thick: REQUIRED dependency |
| 5 | Quotes | Database | Selects the quote by Tenant Beta and its ID: no row | Spring JDBC (JdbcClient) | solid: not covered by module conformance |

## What this view cannot show

- A diagram shows the model, not the code. Only the API's modules are checked against the code (docs/gaps.md, section 1).
- Each numbered step is a relationship of the model, in the order of the flow. Where a listener handles another module's event, its arrow shows the code dependency, not the direction in which the event travels; the authored sequence diagrams under docs/flows/ describe runtime delivery.
