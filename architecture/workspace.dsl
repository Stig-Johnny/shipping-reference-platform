// shipping-reference-platform: C4 model in Structurizr DSL (parsed with com.structurizr:structurizr-dsl 6.2.4).
//
// This file is the structural authority for the shipping product (ADR 0002). People read the rendered views;
// c4-contract-kit reads this same file and compares it with the API's compiled classes, and with the web app's
// TypeScript imports, in both directions.
// Changing this file is a risk level 3 change and needs the owner's approval (policy/risk.json, .github/CODEOWNERS).
//
// Restrictions, enforced by c4-contract-kit's restricted parser: no !include, !script, !plugin, !docs, !adrs or
// !extend, no environment variable substitution and no remote themes. No constants or variables, no dollar-brace
// substitution and no backslash at the end of a line, comments and text blocks included: the check reads every line
// of this file as text before it is parsed. Implied relationships are disabled below, so every relationship a view
// shows is written here explicitly.
//
// CONTRACT PROPERTIES
//
// On the one container that is checked (exactly one container in the workspace may carry contract.root):
//   contract.root      Java package root of the container's production classes. Every production class under it
//                      must belong to exactly one component, unless it is listed in contract.isolated.
//   contract.isolated  Comma-separated fully qualified class names (no packages, no wildcards) that belong to no
//                      component. Isolated classes must not depend on any component class, and no component class
//                      may depend on them. Both directions are checked.
//
// On every component of that container:
//   contract.id        Stable identifier matching [a-z][a-z0-9-]*. Used in reports, docs/modules/<id>.md,
//                      docs/capabilities/capabilities.yaml and the generated brain. Never reused.
//   contract.packages  Comma-separated Java package roots (surrounding whitespace ignored). A class belongs to a
//                      component when its package equals a root or starts with the root followed by ".". Roots must
//                      lie under contract.root, must not overlap across components, and the list must not be empty.
//   contract.status    built | planned.
//                      built    the packages must contain at least one production class; its edges are evaluated.
//                      planned  the packages must contain no production class (code appearing there fails the check
//                               until the status changes to built); its edges are reported as "not evaluated".
//                      The tag "Planned" must be present exactly when the status is planned. Tags drive rendering
//                      only; the checker reads the properties and rejects a model whose tags disagree with them.
//
// On relationships between two components of the checked container, and nowhere else:
//   contract.mode      REQUIRED | PERMITTED. Absent means PERMITTED; this file states it on every such relationship.
//                      Direction is the static code dependency: "a -> b" means classes of a may reference classes
//                      of b. It is not the direction of runtime data flow: audit -> shipments means audit depends
//                      on the ShipmentBooked type that shipments publishes.
//                      PERMITTED  references from a to b are allowed. With no reference the report says
//                                 "no static evidence" (never "unused") and the build stays green.
//                      REQUIRED   as PERMITTED, and at least one direct static reference from a to b must exist;
//                                 otherwise the build fails with an absence.
//                      A REQUIRED relationship may touch a planned component; it is NOT_EVALUATED until both endpoints are built.
//                      The tag "Required" must be present exactly when the mode is REQUIRED. At most one relationship may exist per ordered pair of
//                      components, and the component relationship graph must be acyclic.
//
// Any reference from one component to another without a relationship fails the build (a divergence). Only direct
// references count; a transitive path never satisfies or violates an edge. Relationships that involve people,
// software systems or containers are documentation: they must not carry contract.mode and are not checked by Java
// static analysis (the web app's calls are checked by contract and end-to-end tests instead).
//
// WEB PROPERTIES
//
// The web app is checked against this model too, by the web import check of c4-contract-kit (WebConformance), with a
// vocabulary of its own: the contract properties above belong to the API alone, and the web container, its
// components and their relationships must not carry them.
//
// On the one container whose TypeScript sources are checked (exactly one container may carry web.root):
//   web.root      Directory of the container's source files, relative to the repository root. Every file under it
//                 must belong to exactly one component by directory. Names that start with a dot are not part of it.
//                 The directory above it is the app's: its package.json, tsconfig*.json and *.html are read too.
//   web.testRoots     Optional. Comma-separated directories of tests only, inside the app's directory. Every .ts and
//                     .tsx file in them is a test. No root may equal or lie inside another, web.root included.
//   web.publicRoots   Optional. Comma-separated directories of files served as they are (style sheets, images and
//                     fonts only), inside the app's directory.
//   web.testBuiltins  Optional. Comma-separated built-in modules of Node.js that tests may import, each written with
//                     node: (node:test). Browser code may import none, and a test none without node:.
//   web.testPackages  Optional. Comma-separated npm package names that tests may import with a bare specifier.
//
// On every component of that container:
//   web.id        Stable identifier matching [a-z][a-z0-9-]*, unique among the web components. Used in reports.
//   web.paths     Comma-separated directories relative to web.root. A file belongs to the component whose directory
//                 is above it. No directory may equal or lie inside another, in one component or across components.
//   web.status    built | planned, with the tag "Planned" exactly when planned.
//                 built    the directories must hold at least one .ts or .tsx file (test files do not count).
//                 planned  the directories must hold no file at all; the component's edges are not evaluated.
//   web.entry     Optional. A .ts or .tsx file inside the component's directories, relative to web.root, and not a
//                 test. Other components, and their tests, may import the component through that file only.
//   web.packages  Optional. Comma-separated npm package names that the component's files may import with a bare
//                 specifier (react also covers react/jsx-runtime). Any other bare specifier fails the check.
//
// Every package and built-in an import names is whitelisted twice: here, and in security/allowlist.json. A package
// of browser code must be pinned in package.json "dependencies" and approved there for the app (project
// apps/shipping-web, configuration dependencies); a test's package in "devDependencies" or "dependencies". A test's
// built-ins need the approval of the Node.js that package.json "engines" names, for project ci, configuration web.
//
// On relationships between two components of that container, and nowhere else:
//   web.mode      REQUIRED | PERMITTED, as contract.mode above with an import in place of a reference: "a -> b"
//                 means files of a may import files of b. Absent means PERMITTED; this file states it on every such
//                 relationship. The tag "Required import" must be present exactly when the mode is REQUIRED. At most
//                 one relationship per ordered pair of components; the graph must be acyclic.
//
// An import from one web component to another without a relationship fails the check (a divergence), and so does a
// REQUIRED relationship between built components without any import (an absence). Type-only imports, re-exports and
// import() of a string literal count; imports inside one component do not, but no files may import each other in a
// cycle. Test files (*.test.ts, *.test.tsx) are held to the relationships and entries of their component, and the files
// of a test root, which belong to no component, may import only files of that root; but tests are never evidence: a
// test never satisfies a REQUIRED relationship, and no production file may import a test. The sources are read as data
// with a deliberately restricted import grammar, never executed: whatever it does not cover (require, import() of
// anything but a string literal, import.meta other than import.meta.env, absolute, aliased or URL specifiers, .js and
// .json files, CSS @import, and settings such as tsconfig paths, baseUrl or an import map that would resolve a
// specifier otherwise) fails the check. The rules are in libs/c4-contract-kit/AGENTS.md ("Web imports").
//
// What this model cannot prove is listed in docs/gaps.md. In short: a REQUIRED edge proves that a static reference
// or import exists, not that the behaviour behind it runs.

workspace "Shipping reference platform" "Reference monorepo: webshop parcel shipping with synthetic data, checked against this model." {

    !identifiers flat
    !impliedRelationships false

    model {
        clerk = person "Shipping clerk" "Works for a webshop. Prices parcels, books shipments and downloads labels."
        admin = person "Platform administrator" "Provisions tenants and memberships for webshops."

        webshop = softwareSystem "Webshop" "A merchant's online shop (simulated). Source of the orders that need shipping." "External"
        carrier = softwareSystem "Carrier (simulated)" "A fictional parcel carrier. v0.1 simulates its tariff, tracking numbers and labels inside the API; no network calls." "External,Simulated"

        platform = softwareSystem "Shipping reference platform" "Quotes, books and labels parcels for webshop tenants." {

            web = container "Web app" "Shipping workbench for clerks: parcel form, comparable quote cards, shipment list and detail drawer." "Plain TypeScript SPA (no framework), TypeScript 5.9.3" "WebApp,Planned"

            api = container "API" "Modular monolith exposing contracts/shipping.openapi.yaml. Modules are packages; each has a public api package and private internals." "Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring JDBC (JdbcClient)" {
                properties {
                    "contract.root" "io.github.stigjohnny.shipping"
                    "contract.isolated" "io.github.stigjohnny.shipping.ShippingApplication,io.github.stigjohnny.shipping.CorrelationIdFilter,io.github.stigjohnny.shipping.FallbackProblemHandler"
                }

                quotes = component "Quotes" "Prices parcels with the synthetic tariff and issues quotes whose offers expire after 30 minutes." "Spring Modulith module" {
                    properties {
                        "contract.id" "quotes"
                        "contract.packages" "io.github.stigjohnny.shipping.quotes"
                        "contract.status" "built"
                    }
                }

                shipments = component "Shipments" "Books shipments from quote offers with tenant-scoped idempotency, cancels them and renders simulated labels. Publishes ShipmentBooked and ShipmentCancelled." "Spring Modulith module" {
                    properties {
                        "contract.id" "shipments"
                        "contract.packages" "io.github.stigjohnny.shipping.shipments"
                        "contract.status" "built"
                    }
                }

                tenants = component "Tenants" "Authenticates synthetic users, derives the tenant from membership, binds it to every database transaction for row-level security, and evaluates prototype flags." "Spring Modulith module" {
                    properties {
                        "contract.id" "tenants"
                        "contract.packages" "io.github.stigjohnny.shipping.tenants"
                        "contract.status" "built"
                    }
                }

                audit = component "Audit" "Appends one tenant-scoped audit entry per shipment event, deduplicated by event ID; replayed from the publication registry after failures." "Spring Modulith module" "Planned" {
                    properties {
                        "contract.id" "audit"
                        "contract.packages" "io.github.stigjohnny.shipping.audit"
                        "contract.status" "planned"
                    }
                }

                orders = component "Orders" "Planned: imports webshop orders and turns them into quote and booking requests." "Spring Modulith module" "Planned" {
                    properties {
                        "contract.id" "orders"
                        "contract.packages" "io.github.stigjohnny.shipping.orders"
                        "contract.status" "planned"
                    }
                }

                tracking = component "Tracking" "Planned: receives carrier tracking events and moves shipments through transit states." "Spring Modulith module" "Planned" {
                    properties {
                        "contract.id" "tracking"
                        "contract.packages" "io.github.stigjohnny.shipping.tracking"
                        "contract.status" "planned"
                    }
                }

                returns = component "Returns" "Planned: creates return shipments for delivered shipments." "Spring Modulith module" "Planned" {
                    properties {
                        "contract.id" "returns"
                        "contract.packages" "io.github.stigjohnny.shipping.returns"
                        "contract.status" "planned"
                    }
                }
            }

            db = container "Database" "One schema per v0.1 module (quotes, shipments, tenants, audit) plus the event publication registry. Forced row-level security on tenant data." "PostgreSQL 18.6" "Database,Planned"
        }

        // System context relationships.
        clerk -> webshop "Manages the orders that need shipping in"
        clerk -> platform "Prices parcels, books shipments and downloads labels with"
        admin -> platform "Provisions tenants and memberships for (v0.1: reviewed seed migrations only)"
        webshop -> platform "Will submit orders to ship (planned: orders module)" "JSON over HTTPS" "Planned"
        platform -> carrier "Obtains rates, tracking numbers and labels from (simulated in-process in v0.1)" "In-process simulation" "Simulated"

        // Container relationships.
        clerk -> web "Prices parcels, books shipments and downloads labels in" "HTTPS, browser"
        admin -> web "Will manage tenants and memberships in (planned; no admin screens in v0.1)" "HTTPS, browser" "Planned"
        admin -> db "Provisions synthetic tenants and memberships through reviewed seed migrations run as the migration role" "Flyway"
        web -> api "Calls the shipping API through its hand-written typed HTTP adapter" "JSON over HTTPS"
        webshop -> api "Will submit orders to ship (planned)" "JSON over HTTPS" "Planned"
        api -> db "Reads and writes tenant-scoped data as the application role, which lacks BYPASSRLS" "JDBC"
        api -> carrier "Obtains rates, tracking numbers and labels (simulated in-process; no network call in v0.1)" "In-process simulation" "Simulated"

        // Component relationships to containers and external systems (documentation; not checked statically).
        web -> tenants "Authenticates every API request with the demo bearer token; reads the current membership and prototype flags" "JSON over HTTPS"
        web -> quotes "Creates and reads quotes" "JSON over HTTPS"
        web -> shipments "Books, lists, reads and cancels shipments; downloads labels" "JSON over HTTPS"
        webshop -> orders "Will submit orders to ship" "JSON over HTTPS" "Planned"
        quotes -> db "Stores quotes and offers in schema quotes" "Spring JDBC (JdbcClient)"
        shipments -> db "Stores shipments and idempotency keys in schema shipments; writes event publications" "Spring JDBC (JdbcClient)"
        tenants -> db "Resolves demo tokens through a security-definer function, reads prototype flags, sets app.tenant_id in every transaction" "JDBC"
        audit -> db "Appends audit entries in schema audit" "Spring JDBC (JdbcClient)"
        quotes -> carrier "Prices each service level from the carrier's synthetic tariff (simulated in-process)" "In-process simulation" "Simulated"
        shipments -> carrier "Assigns simulated tracking numbers and renders simulated labels (in-process)" "In-process simulation" "Simulated"
        tracking -> carrier "Will receive tracking events" "HTTPS webhook" "Planned"

        // Component-to-component relationships: the checked contract.
        quotes -> tenants "Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId)" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        shipments -> tenants "Resolves the caller's tenant (tenants.api: CurrentTenant, TenantId)" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        shipments -> quotes "Books only against an existing, unexpired offer (quotes.api: QuoteOffers, OfferLookup, Money, ServiceLevel)" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        audit -> shipments "Records the ShipmentBooked and ShipmentCancelled events that shipments publishes (shipments.api)" "Spring Modulith event listener" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        audit -> tenants "Writes each audit entry inside the event's tenant scope (tenants.api: TenantScope, TenantId)" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        audit -> quotes "May record QuoteIssued events; no listener exists in v0.1, so the report shows no static evidence" "Spring Modulith event listener" "Permitted" {
            properties {
                "contract.mode" "PERMITTED"
            }
        }
        orders -> quotes "Will request quotes for imported orders" "Java" "Permitted,Planned" {
            properties {
                "contract.mode" "PERMITTED"
            }
        }
        orders -> shipments "Will book shipments for imported orders" "Java" "Permitted,Planned" {
            properties {
                "contract.mode" "PERMITTED"
            }
        }
        tracking -> shipments "Will move shipments through carrier transit states" "Java" "Permitted,Planned" {
            properties {
                "contract.mode" "PERMITTED"
            }
        }
        returns -> shipments "Will create return shipments for delivered shipments" "Java" "Permitted,Planned" {
            properties {
                "contract.mode" "PERMITTED"
            }
        }
    }

    views {
        systemContext platform "SystemContext" "Who uses the platform and which systems it talks to." {
            include *
            autoLayout lr
        }

        container platform "Containers" "The web app, the API (one deployable modular monolith) and the database." {
            include *
            autoLayout lr
        }

        component api "ApiComponents" "The seven modules of the API and the dependencies between them: REQUIRED, PERMITTED or planned. Planned modules and dependencies are labelled as planned." {
            include *
            autoLayout lr
        }

        dynamic api "QuoteBookLabel" "Quote, book and download a simulated label. Steps and their tests: docs/flows/quote-book-label.md." {
            clerk -> web "Prices a parcel, books an offer and downloads its label"
            web -> tenants "Sends the demo bearer token; the tenant is derived from the user's membership"
            web -> quotes "POST /api/v1/quotes (createQuote)"
            quotes -> tenants "Resolves the caller's tenant"
            quotes -> carrier "Rates each service level with the synthetic tariff"
            quotes -> db "Stores the quote and its offers; publishes QuoteIssued"
            web -> shipments "POST /api/v1/shipments with an Idempotency-Key (bookShipment)"
            shipments -> tenants "Resolves the caller's tenant"
            shipments -> quotes "Checks that the offer exists for this tenant and has not expired"
            shipments -> db "Stores the shipment, its idempotency key and the ShipmentBooked publication in one transaction"
            audit -> shipments "Receives ShipmentBooked after the booking commits (listener; arrow shows the code dependency)"
            audit -> tenants "Binds the event's tenant before writing"
            audit -> db "Appends one audit entry per event ID; the publication is marked complete"
            web -> shipments "GET the label of the shipment (getShipmentLabel)"
            shipments -> carrier "Renders the simulated SVG label"
            autoLayout lr
        }

        styles {
            element "Element" {
                color #1e293b
                stroke #475569
            }
            element "Person" {
                shape Person
                background #0f766e
                color #ffffff
            }
            element "Software System" {
                background #334155
                color #ffffff
            }
            element "External" {
                background #64748b
                color #ffffff
            }
            element "Simulated" {
                border dashed
            }
            element "Container" {
                background #115e59
                color #ffffff
            }
            element "WebApp" {
                shape WebBrowser
            }
            element "Database" {
                shape Cylinder
            }
            element "Component" {
                shape Component
                background #ccfbf1
                color #134e4a
                stroke #0f766e
            }
            element "Planned" {
                border dotted
                opacity 55
                background #f1f5f9
                color #334155
                stroke #64748b
            }
            relationship "Relationship" {
                color #475569
                thickness 2
            }
            relationship "Required" {
                style solid
                thickness 4
                color #0f766e
            }
            relationship "Permitted" {
                style dashed
                thickness 2
                color #475569
            }
            relationship "Simulated" {
                style dashed
            }
            relationship "Planned" {
                style dotted
                opacity 55
            }
        }
    }
}
