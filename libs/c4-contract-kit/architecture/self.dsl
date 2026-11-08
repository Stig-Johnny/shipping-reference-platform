// c4-contract-kit: the library's own structure, in Structurizr DSL, checked with the library itself.
//
// libs/c4-contract-kit/AGENTS.md ("Boundaries") allows exactly these internal dependencies:
// api -> model, bytecode, rules, report; rules -> model, bytecode; report -> rules, model. sources depends on no
// other package, and no package uses it yet. Every allowed edge is used, so each is REQUIRED: deleting one fails
// SelfConformanceTest with an absence, and any other edge, for example report -> api, model -> bytecode or
// sources -> model, fails it with a divergence. The component relationship graph has no cycle.
//
// The contract properties are those of architecture/workspace.dsl (see its header), and so are the restrictions:
// no !include, !script, !plugin, !docs or !adrs, no environment variables and no themes; no constants or variables,
// no dollar-brace substitution and no backslash at the end of a line, comments and text blocks included. Changing
// this file is a risk level 3 change. architecture/workspace.dsl stays the authority for the product (ADR 0002).

workspace "c4-contract-kit" "The internal structure of the library that checks code against a C4 model." {

    !identifiers flat
    !impliedRelationships false

    model {
        kit = softwareSystem "c4-contract-kit" "Compares a Structurizr DSL model with compiled Java classes, in both directions." {

            library = container "Library" "A plain Java library, used from tests." "Java 25" {
                properties {
                    "contract.root" "io.github.stigjohnny.c4contractkit"
                }

                apiPackage = component "api" "The only public package: Conformance, Report and its records, ConformanceAssertions and ModelException." "Java package" {
                    properties {
                        "contract.id" "api"
                        "contract.packages" "io.github.stigjohnny.c4contractkit.api"
                        "contract.status" "built"
                    }
                }

                modelPackage = component "model" "Restricted Structurizr DSL parsing and validation into an immutable ContractModel." "Java package, Structurizr DSL 6.2.4" {
                    properties {
                        "contract.id" "model"
                        "contract.packages" "io.github.stigjohnny.c4contractkit.model"
                        "contract.status" "built"
                    }
                }

                bytecodePackage = component "bytecode" "Class-file import with ArchUnit and the evidence for each dependency." "Java package, ArchUnit 1.5.1" {
                    properties {
                        "contract.id" "bytecode"
                        "contract.packages" "io.github.stigjohnny.c4contractkit.bytecode"
                        "contract.status" "built"
                    }
                }

                sourcesPackage = component "sources" "TypeScript, TSX, style sheets and the web app's configuration read as data with a restricted grammar, and each import resolved to a file, a package or a built-in module of Node.js." "Java package" {
                    properties {
                        "contract.id" "sources"
                        "contract.packages" "io.github.stigjohnny.c4contractkit.sources"
                        "contract.status" "built"
                    }
                }

                rulesPackage = component "rules" "The comparison in both directions and the violation rules." "Java package" {
                    properties {
                        "contract.id" "rules"
                        "contract.packages" "io.github.stigjohnny.c4contractkit.rules"
                        "contract.status" "built"
                    }
                }

                reportPackage = component "report" "The deterministic JSON and Markdown writers." "Java package" {
                    properties {
                        "contract.id" "report"
                        "contract.packages" "io.github.stigjohnny.c4contractkit.report"
                        "contract.status" "built"
                    }
                }
            }
        }

        apiPackage -> modelPackage "Reads and validates the model with" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        apiPackage -> bytecodePackage "Imports the class roots with" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        apiPackage -> rulesPackage "Compares the model with the classes with" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        apiPackage -> reportPackage "Writes report.json and report.md with" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        rulesPackage -> modelPackage "Reads components, package roots and relationships from" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        rulesPackage -> bytecodePackage "Reads classes and their references from" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        reportPackage -> rulesPackage "Writes the comparison from" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
        reportPackage -> modelPackage "Writes component statuses from" "Java" "Required" {
            properties {
                "contract.mode" "REQUIRED"
            }
        }
    }

    views {
        component library "Packages" "The six packages of the library and the only dependencies allowed between them." {
            include *
            autoLayout lr
        }

        styles {
            element "Component" {
                shape Component
            }
            relationship "Required" {
                thickness 4
            }
        }
    }
}
