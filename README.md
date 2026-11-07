# shipping-reference-platform

A reference monorepo that shows how a Java and React product can be built when the architecture model, the
API contract and the documentation lead and the code follows, and when each claim about the code is backed by a
check that fails. The domain is generic webshop parcel shipping: quote a parcel, book it, download a label. Data is
synthetic and the carrier is simulated.

**State:** the specification (model, contract, decisions, module specifications, risk policy, agent rules), the
reviewed toolchain, the enforced dependency allowlist and the model-versus-code checker `libs/c4-contract-kit` are here.
Application code is not written yet: every product capability in `docs/capabilities/capabilities.yaml` is `planned`, and
that file is the source of truth for status and evidence; the checker's controls carry their tests. The table below
marks each control **enforced** (it runs and can fail today) or **planned**. `tools/docs` generates the C4 view pages,
`BRAIN.md` and a static documentation site from the specification. Its own controls, `control.brain-check` and
`control.publication-scan` (the scan of the site before each upload), are enforced. The site is published from `main` at
<https://stig-johnny.github.io/shipping-reference-platform/>.

## The idea

- **The structure is written down and checked in both directions.** `architecture/workspace.dsl` is a C4 model in
  Structurizr DSL. The API's seven modules are its components, each mapped to a Java package. Every dependency
  between modules is an explicit relationship, marked `REQUIRED` (the code must reference it) or `PERMITTED` (the
  code may). The library in `libs/c4-contract-kit` reads the model and the compiled classes and fails the build when
  code depends on a module the model does not allow, and when code lacks a dependency the model requires. A
  permitted dependency with no reference is reported as "no static evidence", not as a failure.
- **The HTTP interface is written down before it is coded.** `contracts/shipping.openapi.yaml` (OpenAPI 3.1.2,
  RFC 9457 errors) is what the Java controllers are tested against and what the TypeScript client is generated from.
- **Agents write most of the code; checks decide whether it stays.** Every pull request gets a risk level from 0 to
  3. Levels 0 to 2 need green checks and a recorded AI review. Level 3 (pricing, bookings, personal data,
  authentication, tenant isolation, migrations, dependencies, and the controls themselves) also needs the owner's
  approval of the exact final version.
- **Every third-party component is reviewed before it can run.** A base-branch preflight rejects anything not in
  `security/allowlist.json` before a build starts.

```mermaid
flowchart LR
    I[Issue] --> M[Model or contract change]
    M --> T[Failing tests]
    T --> C[Code and docs]
    C --> K{Checks}
    K -- fail --> C
    K -- pass --> R[Recorded AI review]
    R --> L{Risk level}
    L -- 0 to 2 --> G[Merge]
    L -- 3 --> O[Owner approves current head] --> G
```

The workflow in the diagram, in order:

1. An issue states the change.
2. The model or the contract changes first.
3. Failing tests describe the new behaviour.
4. Code and documentation change until the tests pass.
5. The checks run; when one fails, the change goes back to step 4.
6. An AI review is recorded.
7. The risk level decides: levels 0 to 2 merge; level 3 merges only after the owner approves the current head.

## The product in one paragraph

The planned v0.1 behaviour is: a shipping clerk of a webshop (a tenant) enters a parcel and a route and gets
comparable offers priced by a published synthetic tariff, each valid for 30 minutes. Booking an offer is idempotent
per tenant and `Idempotency-Key`: booking twice returns the same shipment. The clerk downloads a simulated SVG
label. An audit module records each booking from an event, deduplicated and replayed after failures. Tenants never
see each other's data, enforced in queries and by forced PostgreSQL row-level security.

| Module | Implementation state | Depends on (from the model) |
|---|---|---|
| `quotes` | Planned for v0.1; no code yet | `tenants` (REQUIRED) |
| `shipments` | Planned for v0.1; no code yet | `quotes`, `tenants` (REQUIRED) |
| `tenants` | Minimal implementation planned for v0.1; no code yet | nothing |
| `audit` | Minimal implementation planned for v0.1; no code yet | `shipments`, `tenants` (REQUIRED); `quotes` (PERMITTED, no static evidence yet) |
| `orders` | planned, no code | `quotes`, `shipments` (PERMITTED, not evaluated) |
| `tracking` | planned, no code | `shipments` (PERMITTED, not evaluated) |
| `returns` | planned, no code | `shipments` (PERMITTED, not evaluated) |

## Controls and how each one can fail

| Control | Fails the build when | What it cannot catch | Where it runs | Status |
|---|---|---|---|---|
| C4 conformance: divergence | A class in one module references another module without a relationship in the model. | Use through reflection, SQL, HTTP or configuration; references the class file does not record (compile-time constants, casts); a model widened to allow the dependency (that change needs the owner). | `java`: `ArchitectureConformanceTest` | planned (the library is built and tested; there is no application code to check yet) |
| C4 conformance: absence | A `REQUIRED` relationship has no static reference in the bytecode. | A reference that exists while the behaviour behind it does nothing. | `java`: `ArchitectureConformanceTest` | planned (as above) |
| Model validity | Package mappings overlap, are empty or leave classes uncovered; a planned module has code; forbidden DSL directives; tags disagree with contract properties; cycles. | Whether the model describes the right architecture. | `java`: `RepositoryWorkspaceTest` reads `architecture/workspace.dsl` with the library | **enforced** for the model file; the rules that need classes (uncovered classes, code in a planned module) wait for application code |
| Module boundaries and layering | Access to another module's internal packages, module cycles, Spring in domain code, unexpected packages. | Coupling through shared data or timing. | `java`: `ModularityTest`, `HexagonalLayeringTest` | planned |
| API contract | A response deviates from the OpenAPI schema; the regenerated TypeScript client differs from the committed one. | Operations and status codes no test exercises. | `java`: `*HttpContractTest`; `web` | planned |
| Tenant isolation | A tenant table lacks forced row-level security; the runtime role could bypass it; another tenant's IDs are readable through SQL or HTTP. | SQL run as the owner role; the shared event publication table; strength of the demo authentication. | `java`: `RowLevelSecurityCatalogTest`, `RowLevelSecurityIntegrationTest`, `CrossTenantAccessTest` | planned |
| Domain invariants | Golden tariff prices, monotonicity and rounding properties, idempotent booking, expiry rules. | Wrong requirements. | `java` (parameterized property tests included: `SyntheticTariffPropertiesTest`) | planned |
| Event delivery | A failed audit write is lost, or a replay has a second effect. | Consumers nobody has written yet. | `java`: `AuditReplayIntegrationTest` | planned |
| Mutation testing | Fewer than 80 % of mutants on changed lines are killed (90 % for pricing and checker code). | Tests that consistently assert the wrong thing; hand-reviewed equivalent mutants. | `mutation` | planned (today the `mutation` job runs PIT on the contract kit and fails under 90 % killed; it is not a required check) |
| Dependency allowlist, before use | A component lacks an approved, unexpired review; a lockfile or checksum disagrees with it; an Action is not pinned by SHA. | Build code that downloads executables itself, compromised approved artifacts, runner compromise, local agents bypassing CI. | `preflight` (base-branch workflow) | **enforced**: a required check, proven to fail |
| Risk-levelled approval | A level 3 change lacks the owner's approval of its current head. | A sensitive change that matches no rule; a single approver is not two-person review. | `risk-approval` | planned |
| AI review record | No AI review with findings and dispositions is recorded for the final commit. | AI reviewers being wrong. | `ai-review` | planned |
| Web behaviour and accessibility | Quote, book and label fail in a real browser against the real API; axe-core finds violations; import cycles or boundary breaks. | Accessibility problems that automated tools miss; unused frontend modules. | `web`, `contract-e2e` | planned |
| Documentation and brain | Generated docs or `BRAIN.md` drift; budgets exceeded; a capability claims `shipped` without passing evidence; a link, anchor or ADR reference is broken; the site's Mermaid bundle differs from its review; code changed without its module document. | Whether the prose is right. | `java`: `:tools:docs:check` (`BrainGeneratorTest`, `BrainCheckTest`, `SiteBuilderTest`) | **enforced** in the required `java` check, except the module-document check, which is planned |
| Publication scan of the public site | The built site holds a secret's shape, a link to this repository on GitHub, a local path, a private address or host name, an HTML comment or a term of the confidential dictionary; the dictionary is missing; the Mermaid bundle differs from its review; the site directory holds a link or a hidden file. | A paraphrase, a confidential term that is not in the dictionary, what a sentence means. | `pages`, build job: `:tools:docs:scanSite` before the upload (`PublicationScannerTest`, `PagesWorkflowTest`) | **enforced** on every deployment from `main`; the first run needs the `github-pages` environment and its secret |

Every advertised failure requires a deliberate negative fixture. The checker's own tests have theirs, and the
documentation and brain control has them in `BrainCheckTest` and `SiteBuilderTest`; the application's do not exist yet.
The full list of limits is in `docs/gaps.md`.

## Layout

```text
architecture/workspace.dsl       C4 model: structural authority (ADR 0002)
contracts/shipping.openapi.yaml  HTTP contract (ADR 0004)
apps/shipping-api/               Gradle project :apps:shipping-api, Spring Boot modular monolith
apps/shipping-web/               npm workspace, React workbench
libs/c4-contract-kit/            Gradle project :libs:c4-contract-kit, the model-versus-code checker
packages/shipping-client/        npm workspace, client generated from the contract
docs/                            Markdown with frontmatter: adr/, modules/, flows/, capabilities/, gaps.md, ui/;
                                 architecture/ and brain/ are generated by tools/docs
policy/                          risk.json (risk levels), rulesets.json (later)
security/                        allowlist.json and reviews/ (bootstrap pull request)
tools/                           policy checker (policy/), docs, brain and site generator (docs/), web import check (later)
build-logic/, gradle/            Gradle conventions, wrapper and verification metadata (later)
.github/                         CODEOWNERS; workflows (later)
AGENTS.md                        rules for AI agents; scoped files in apps/*, libs/*, security/
BRAIN.md                         generated agent entry point
```

## Running it

From a clean clone, with the reviewed JDK (Temurin 25.0.5+8) on `JAVA_HOME`. Install it by hand from the archive
whose SHA-256 is in `security/verifications/2026-11-06-toolchain.md`: Gradle's toolchain auto-download is off.

| Step | Command |
|---|---|
| Check every dependency against the allowlist (the JDK alone; nothing from the repository runs) | `java tools/policy/PolicyCheck.java .` |
| Test the allowlist checker (the JDK alone) | `java tools/policy/PolicyCheckTest.java` |
| Run all Java checks (compile with Error Prone and NullAway, tests) | `./gradlew check` |
| Run mutation tests (CI only, the `mutation` job: PIT's control socket accepts unauthenticated connections) | `./gradlew :libs:c4-contract-kit:pitest` |
| Check docs and the brain; regenerate them | `./gradlew :tools:docs:check`; `./gradlew :tools:docs:generate` |
| Build the documentation site into `tools/docs/build/site/` (open its `index.html`) | `./gradlew :tools:docs:site` |

Not built yet; their commands arrive with their code: a toolchain bootstrap script, PostgreSQL 18.6 with the two
roles, the API with the `demo` profile, the workbench, web checks, and contract and end-to-end tests.

## Honest limits

- A `REQUIRED` edge proves that a static reference exists, not that the behaviour behind it runs. Integration tests
  carry behavioural claims, and one demonstration shows a broken audit listener that passes the architecture check.
- "No static evidence" does not mean unused. Reflection, configuration and SQL are invisible to bytecode analysis.
- The model can be weakened to make a failing build pass. The checker does not understand intent; the owner's
  approval on model changes is the control.
- Authentication is a demo scheme with public, guessable tokens. It proves tenant derivation and isolation, not
  login security.
- Pre-use dependency enforcement covers declared dependencies on the sanctioned CI path only.
- The tariff, carrier and labels are fictional. Nothing here is production-ready, and nothing is deployed.

## Approval

Agent-authored changes receive recorded automated review; sensitive changes additionally receive the owner’s human
approval. This demonstrates accountable human oversight, not independent two-person review or a production-security
certification.

## Prior art

The comparison of a model with code in both directions is decades old; this repository applies it to a Structurizr
model and Java bytecode and adds required-edge semantics and evidence. The research behind it, including which tools
check in both directions and what 2025–2026 studies say about coding agents and architecture, is written up in
[Holding Code to Architecture in the Coding-Agent Era](https://www.dashecorp.com/codi-e/diagram-first-research/)
(Codi-E blog, 6 November 2026).

- **Reflexion models**, Gail Murphy, David Notkin and Kevin Sullivan, ACM SIGSOFT FSE 1995: map code onto a model
  and report convergences, divergences and absences. <https://www.cs.ubc.ca/~murphy/papers/rm/fse95.html>
- **ArchUnit** by TNG: the bytecode analysis this repository uses; its PlantUML component rules check permitted
  dependencies in one direction.
  <https://www.archunit.org/userguide/html/000_Index.html#_plantuml_component_diagrams_as_rules>
- **Spring Modulith** by the Spring team: the application's module framework; it verifies module boundaries from
  the code's own structure, in one direction. <https://docs.spring.io/spring-modulith/reference/>
- **jQAssistant with the Structurizr plugin** by Materna: imports a Structurizr DSL model into a graph for
  user-written rules; the closest open-source overlap. <https://github.com/materna-se/structurizr-jqassistant-plugin>
- **dependency-cruiser** by Sander Verweij: `forbidden`, `allowed` and `required` rules for JavaScript and
  TypeScript. <https://github.com/sverweij/dependency-cruiser/blob/main/doc/rules-reference.md>
- **Lattix**: commercial architecture enforcement whose "Must Use" rules fail when a dependency is missing.
  <https://docs.lattix.com/lattix/userGuide/Monitoring_and_Enforcing_Architecture.html>
- **Structurizr DSL and the C4 model** by Simon Brown: the modelling language and notation.
  <https://docs.structurizr.com/dsl>, <https://c4model.com/>

## Licence

Except for the third-party material identified in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), all original
code, configuration, models, specifications, ADRs, security reviews and documentation are licensed under the
[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for attribution.
