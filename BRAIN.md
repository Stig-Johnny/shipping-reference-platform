---
title: "Brain: shipping-reference-platform"
description: "The generated entry point for agents: what the repository is, what is built and what is only planned, and where each module, decision, control and limit is written down."
status: accepted
updated: 2026-11-09
---

> Generated—do not hand-edit. Sources: `README.md`, `architecture/workspace.dsl`, `docs/`, `contracts/`, `policy/risk.json` and `security/allowlist.json`.
> Regenerate with `./gradlew :tools:docs:generate`; `:tools:docs:checkGenerated` fails on any difference.

# Brain: shipping-reference-platform

A reference monorepo that shows how a Java and TypeScript product can be built when the architecture model, the API contract and the documentation lead and the code follows, and when each claim about the code is backed by a check that fails. The domain is generic webshop parcel shipping: quote a parcel, book it, download a label. Data is synthetic and the carrier is simulated. The application's first code exists: the API's pricing and quotes and its demo authentication, served over HTTP in tests, and booking, listing, reading and simulated labels in its shipments module over HTTP. The tenants, quotes and shipments schemas have paired migrations and JDBC adapters behind the postgres profile; the attended demo now runs with memory stores or opt-in PostgreSQL, while database execution evidence is pending CI. The tooling that judges it exists too: a checker that holds compiled Java classes to the C4 model in both directions, a labelled partial check of detected TypeScript imports in the web app, a dependency allowlist check and a documentation tool.

**State at this commit.** 27 capabilities: 2 shipped, 9 partial, 16 planned; 19 controls: 10 shipped, 6 partial, 3 planned. A planned capability has no passing acceptance test; the test names listed for it are targets, not evidence.

## Read first

- [README.md](README.md): the idea, the controls and how each can fail.
- [AGENTS.md](AGENTS.md): rules for agents, with scoped files in `apps/*`, `libs/*` and `security/`.
- [docs/gaps.md](docs/gaps.md): what a green build does not prove.
- [docs/risk-levels.md](docs/risk-levels.md) and `policy/risk.json`: who approves what.

## Modules

From `architecture/workspace.dsl`. Arrows in the model point from the module whose code depends on the other.

| Module | Status | Risk | Depends on | Brain | Specification |
|---|---|---|---|---|---|
| `quotes` | built | 3 | tenants (REQUIRED) | [quotes](docs/brain/quotes.md) | [docs/modules/quotes.md](docs/modules/quotes.md) |
| `shipments` | built | 3 | tenants (REQUIRED), quotes (REQUIRED) | [shipments](docs/brain/shipments.md) | [docs/modules/shipments.md](docs/modules/shipments.md) |
| `tenants` | built | 3 | nothing | [tenants](docs/brain/tenants.md) | [docs/modules/tenants.md](docs/modules/tenants.md) |
| `audit` | planned | 2 | shipments (REQUIRED), tenants (REQUIRED), quotes (PERMITTED) | [audit](docs/brain/audit.md) | [docs/modules/audit.md](docs/modules/audit.md) |
| `orders` | planned | 3 | quotes (PERMITTED, planned), shipments (PERMITTED, planned) | [orders](docs/brain/orders.md) | [docs/modules/orders.md](docs/modules/orders.md) |
| `tracking` | planned | 3 | shipments (PERMITTED, planned) | [tracking](docs/brain/tracking.md) | [docs/modules/tracking.md](docs/modules/tracking.md) |
| `returns` | planned | 3 | shipments (PERMITTED, planned) | [returns](docs/brain/returns.md) | [docs/modules/returns.md](docs/modules/returns.md) |

The web app is a container, not an API module: 4 capabilities, specified in [docs/ui/workbench.md](docs/ui/workbench.md). Its 6 parts are in the model (6 planned), and the partial web import check compares only detected TypeScript imports with them (ADR 0015).

## Controls

From `docs/capabilities/capabilities.yaml`. Evidence is a test that exists and passed in this build.

| Control | Status | Check | Evidence |
|---|---|---|---|
| `control.c4-divergence` | shipped | `java` | ConformanceDivergenceTest, ArchitectureConformanceTest |
| `control.c4-absence` | shipped | `java` | ConformanceAbsenceTest, ArchitectureConformanceTest |
| `control.no-static-evidence-report` | shipped | `java` | ReportNoStaticEvidenceTest |
| `control.model-validation` | shipped | `java` | RestrictedDslParserTest, MappingValidationTest |
| `control.bytecode-evidence` | shipped | `java` | BytecodeEvidenceTest |
| `control.deterministic-reports` | shipped | `java` | ReportDeterminismTest, ConformanceAssertionsTest, SelfConformanceTest |
| `control.modulith-verification` | shipped | `java` | ModularityTest |
| `control.hexagonal-layering` | shipped | `java` | HexagonalLayeringTest |
| `control.scenario-conformance` | partial | `java` | QuoteScenarioTest, TenantDenialScenarioTest, ScenarioFixturesTest, CoverageBypassTest, ReachabilityTest, SidecarValidationTest, ScenarioModelTest, ScenarioReportTest, CoverageLedgerTest |
| `control.external-imports` | partial | `java` | ExternalImportPolicyTest |
| `control.table-ownership` | planned | `java` | none yet |
| `control.migrations` | partial | `java` | MigrationFilesTest |
| `control.http-contract` | partial | `java` | QuotesHttpContractTest, ShipmentsHttpContractTest, TenantsHttpContractTest, ProblemDetailsHttpContractTest |
| `control.web-import-boundaries` | partial | `java` | ImportScannerTest, SourceReaderTest, JsonTest, AppConfigurationTest, WebModelValidationTest, WebConformanceTest, WebReportDeterminismTest, WebArchitectureConformanceTest |
| `control.dependency-allowlist` | partial | `java` | ContainerEngineWorkflowTest |
| `control.risk-classification` | planned | `risk-approval` | none yet |
| `control.mutation-testing` | planned | `mutation` | none yet |
| `control.brain-check` | shipped | `java` | BrainGeneratorTest, BrainCheckTest |
| `control.publication-scan` | shipped | `build` | PublicationScannerTest, PagesWorkflowTest |

## Decisions

| ADR | Status | Enforced by |
|---|---|---|
| [ADR 0001: Record architecture decisions as MADR files](docs/adr/0001-record-architecture-decisions.md) | accepted | java |
| [ADR 0002: Diagrams first: the C4 model is the structural authority](docs/adr/0002-c4-model-is-the-structural-authority.md) | accepted | java |
| [ADR 0003: Modular monolith with Spring Modulith and hexagonal modules](docs/adr/0003-modular-monolith-with-spring-modulith-and-hexagonal-modules.md) | superseded by ADR 0012 | java |
| [ADR 0004: Contract-first OpenAPI 3.1.2 with RFC 9457 problem details](docs/adr/0004-contract-first-openapi-with-rfc-9457-problems.md) | superseded by ADR 0012 | not enforced yet |
| [ADR 0005: PostgreSQL with Spring Data JDBC, Flyway and forced row-level security](docs/adr/0005-postgresql-spring-data-jdbc-flyway-and-forced-rls.md) | superseded by ADR 0012 | not enforced yet |
| [ADR 0006: Module events through the publication registry, with idempotent consumers](docs/adr/0006-module-events-through-the-publication-registry.md) | accepted | not enforced yet |
| [ADR 0007: Dependency allowlist with pre-use enforcement](docs/adr/0007-dependency-allowlist-and-pre-use-enforcement.md) | accepted | preflight (required check), java (required check), tools/ci/stage-temurin.sh, GitHub Actions setting, acceptance, preflight and java (required checks), workflow runtime guards, tools/ci/container-engine.sh |
| [ADR 0008: Risk levels 0 to 3 decide who must approve a change](docs/adr/0008-risk-levels-0-to-3.md) | accepted | branch protection on main, GitHub Actions setting |
| [ADR 0009: React workbench with a generated API client](docs/adr/0009-react-workbench-with-a-generated-client.md) | superseded by ADR 0012 | not enforced yet |
| [ADR 0010: Documentation as code with a generated brain](docs/adr/0010-documentation-as-code-with-a-generated-brain.md) | superseded by ADR 0011 | not enforced yet |
| [ADR 0011: A Java documentation tool, a static site and a scanned publication](docs/adr/0011-java-documentation-tool-and-publication.md) | accepted | java, pages |
| [ADR 0012: First runnable slice with a plain TypeScript workbench and Spring JDBC](docs/adr/0012-first-runnable-slice-with-a-plain-typescript-workbench-and-spring-jdbc.md) | accepted | java |
| [ADR 0013: Scenario views are checked against recorded interactions](docs/adr/0013-scenario-views-are-checked-against-recorded-interactions.md) | accepted | java |
| [ADR 0014: Partial web import lint and planned authoritative controls](docs/adr/0014-partial-web-import-lint-and-authoritative-controls.md) | accepted | not enforced yet |
| [ADR 0015: Partial web import lint and planned authoritative controls](docs/adr/0015-partial-web-import-lint-and-authoritative-controls.md) | accepted | not enforced yet |
| [ADR 0016: Every migration has a down script, proven in CI; production rolls forward](docs/adr/0016-down-scripts-proven-in-ci-production-rolls-forward.md) | accepted | java |
| [ADR 0017: Bind JDBC statements locally and serialize booking keys and quotes](docs/adr/0017-jdbc-transaction-binding-and-booking-locks.md) | accepted | java |
| [ADR 0018: An attended demo on one origin with selectable storage](docs/adr/0018-attended-demo-runtime.md) | accepted | java |

## Third-party components

519 entries in `security/allowlist.json` (504 approved, 15 rejected), each with its review record in `security/reviews/`. The owner accepted this reviewed set for pull request #54: `reviewedSetSha256 03d8223632a17e66bf31d5b2d0c0bdbc7d5f3837a7f3a032080343bb46517e8d`. How it works and what it cannot stop: [security/README.md](security/README.md).

## Flows

- [Run an attended shipping demo](docs/flows/attended-demo.md): modules tenants, quotes, shipments.
- [Flow: quote, book and download a simulated label](docs/flows/quote-book-label.md): modules tenants, quotes, shipments, audit.

## Known gaps

- [0. State of this commit](docs/gaps.md#0-state-of-this-commit)
- [1. What the architecture check cannot prove](docs/gaps.md#1-what-the-architecture-check-cannot-prove)
- [2. Security and identity](docs/gaps.md#2-security-and-identity)
- [3. Personal data](docs/gaps.md#3-personal-data)
- [4. Domain simplifications](docs/gaps.md#4-domain-simplifications)
- [5. Supply chain and approvals](docs/gaps.md#5-supply-chain-and-approvals)
- [6. Tests and quality gates](docs/gaps.md#6-tests-and-quality-gates)
- [7. Documentation](docs/gaps.md#7-documentation)
- [8. Not in scope for v0.1](docs/gaps.md#8-not-in-scope-for-v01)

## Commands

- `./gradlew :tools:docs:check`: the docs tool's tests, the drift check of these generated files and the site build.
- `./gradlew :tools:docs:site`: build the documentation site into `tools/docs/build/site/`.
- `./gradlew :tools:docs:generate`: regenerate this file, `docs/brain/` and `docs/architecture/`.
- `java tools/policy/PolicyCheck.java .`: the dependency allowlist check.
