# AGENTS.md: libs/c4-contract-kit (Gradle project `:libs:c4-contract-kit`)

Adds to the root `AGENTS.md`. This library is the architecture control (ADR 0002). Everything under
`libs/c4-contract-kit/src/` is risk level 3, and so are its tests: weakening a test weakens the control.

## What it does

Compares a Structurizr DSL model with the static dependencies of compiled Java classes, in both directions, and
reports class-level evidence. It fails on dependencies the model does not declare (divergences) and on REQUIRED
relationships without any static reference (absences). It reports PERMITTED relationships without references as
"no static evidence", never as "unused".

## Boundaries

- Package `io.github.stigjohnny.c4contractkit` with exactly these sub-packages:
  - `api`: the only public package. `Conformance`, `Report`, `ConformanceAssertions`, result records and
    `ModelException`.
  - `model`: restricted DSL parsing and validation into an immutable `ContractModel`.
  - `bytecode`: class import with ArchUnit and evidence extraction.
  - `rules`: comparison and violation rules.
  - `report`: deterministic JSON and Markdown writers.
- Allowed internal dependencies: `api -> model, bytecode, rules, report`; `rules -> model, bytecode`;
  `report -> rules, model`. No other edges; no cycles. The self-model `libs/c4-contract-kit/architecture/self.dsl`
  (slice 4) states these edges and `SelfConformanceTest` checks the library against it.
- Third-party code: `com.structurizr:structurizr-dsl:6.2.4` (in `model` only) and `com.tngtech.archunit:archunit:1.5.1`
  (in `bytecode` only). No Spring, no logging framework, no other runtime dependency. Tests: JUnit and AssertJ 3.27.7.
- It never loads or initialises the analysed classes; it reads class files only.

## Public API

```java
// io.github.stigjohnny.c4contractkit.api
public final class Conformance {
    /** Parses the model (restricted), imports the class roots, compares. Throws ModelException listing every model problem. */
    public static Report check(Path model, List<Path> productionClassRoots);
}
public record Report(String schema, String modelPath, List<ComponentResult> components,
                     List<EdgeResult> edges, List<Violation> violations) {
    public boolean passed();   // true when violations is empty
}
public record EdgeResult(String source, String target, Mode mode, EdgeStatus status, List<Evidence> evidence) {}
public enum Mode { REQUIRED, PERMITTED, UNDECLARED }
public enum EdgeStatus { CONVERGENCE, DIVERGENCE, ABSENCE, NO_STATIC_EVIDENCE, NOT_EVALUATED }
public record Evidence(String originClass, String originMember, String targetClass, String targetMember,
                       String kind, String sourceFile, int line) {}   // line 0 when unknown
public final class ConformanceAssertions {
    /** Writes report.json and report.md to reportDirectory, then throws AssertionError if the report did not pass. */
    public static void assertConforms(Report report, Path reportDirectory);
}
```

Callers (for example `ArchitectureConformanceTest` in `:apps:shipping-api`) write reports to
`build/reports/c4-contract/`.

## Model rules (the header of `architecture/workspace.dsl` states them for model authors)

Parsing:

- Use the parser's feature controls to reject `!include` (local and remote), `!script`, `!plugin`, `!docs`, `!adrs`,
  `!decisions`, `workspace extends` and environment variable substitution. A rejected feature is a `ModelException`,
  never a warning.
- Constants (`!const`), variables (`!var`), `${...}` and line continuations are not part of the restricted DSL.
- Implied relationships are disabled. The DSL creates them unless the file says `!impliedRelationships false`, so a
  model in which the parser created one is a `ModelException`.

Validation (each is a `ModelException` entry with element identifiers):

- Exactly one container has `contract.root`. Every component of that container has `contract.id`
  (`[a-z][a-z0-9-]*`, unique), `contract.packages` (non-empty, each root under `contract.root`, no overlap across
  components) and `contract.status` (`built` or `planned`).
- `contract.isolated` entries are fully qualified class names under `contract.root`, not inside any component root.
- The tag `Planned` is present exactly when `contract.status` is `planned`.
- `contract.mode` is `REQUIRED` or `PERMITTED`, only on relationships between two components of the checked
  container. The tag `Required` is present exactly when the mode is `REQUIRED`.
- A REQUIRED relationship may touch a planned component; it is NOT_EVALUATED until both endpoints are built. At
  most one relationship per ordered pair of components.
  The component relationship graph is acyclic.

## Comparison semantics

- Import every class under the given roots. Classes outside `contract.root` are ignored as origins; dependencies on
  classes outside `contract.root` (JDK, libraries) are ignored as targets.
- Assign each class to the component whose package root equals its package or is a prefix followed by `.`.
- Direct dependencies only, as ArchUnit reports them from the class (method and constructor calls, field access,
  inheritance, field, parameter and return types, throws clauses, annotations, type parameters, `instanceof`, class
  literals). Never compute transitive closure.
- Dependencies inside one component are ignored.
- Violations (each fails the check):
  - `DIVERGENCE`: a dependency from component A to component B without a relationship A -> B.
  - `ABSENCE`: a REQUIRED relationship without any dependency.
  - `UNMAPPED_CLASS`: a class under `contract.root` that is in no component and not isolated.
  - `ISOLATION_BREACH`: a dependency between an isolated class and a component class, in either direction.
  - `PLANNED_COMPONENT_HAS_CODE`: a class in a planned component's packages.
  - `BUILT_COMPONENT_EMPTY`: a built component with no class.
- Not violations: `CONVERGENCE` (declared and used), `NO_STATIC_EVIDENCE` (PERMITTED, unused statically),
  `NOT_EVALUATED` (relationship touching a planned component).

## Reports

- `report.json`: `"schema": "c4-contract-kit/report/v1"`; UTF-8; LF; two-space indentation; no timestamps; paths
  relative to the working directory; components sorted by `contract.id`; edges by source, then target; evidence by
  origin class, origin member, target class, target member, kind, line; violations by type, then identifiers.
- `report.md`: the verdict first, then violations with evidence, then an edge table with every status. The words for
  a PERMITTED relationship without references are exactly "no static evidence".
- Identical inputs produce identical bytes, whatever the order of class roots or model elements.

## Ten TDD slices

One pull request each; tests first; red-green evidence in each pull request.

1. Bootstrap: reviews, allowlist and policy checker with rejection and tampering tests, locks, repository controls
   (`tools/policy`, `security/`).
2. Restricted DSL parsing and mapping validation (`RestrictedDslParserTest`, `MappingValidationTest`).
3. Bytecode fixtures: signatures, inheritance, annotations, generics, lambdas and evidence (`BytecodeEvidenceTest`).
4. Bidirectional comparison, deterministic reports, assertions, self-check and PIT (`ConformanceDivergenceTest`,
   `ConformanceAbsenceTest`, `ReportNoStaticEvidenceTest`, `ReportDeterminismTest`, `ConformanceAssertionsTest`,
   `SelfConformanceTest`).
5. Shipping invariants: PostgreSQL tests prove that a booking and its event publication commit and roll back
   together, using fresh transactions (`:apps:shipping-api`, ADR 0006).
6. Demo pull request: an undeclared dependency fails.
7. Demo pull request: a removed REQUIRED dependency fails.
8. Demo pull request: a weakened model passes the check but waits for the owner's approval.
9. Demo pull request: an unreviewed dependency fails preflight; the reviewed, allowlisted, owner-approved revision
   passes.
10. Behavioural limit: a broken audit listener passes the architecture check but fails integration testing.

Demo pull requests 6 to 10 stay open or are preserved as reproducible recordings.

## Quality bar

- Mutation testing: at least 90 % killed mutants on changed lines (checker logic).
- Every rule above has a negative fixture: a model or class set that must fail with that exact violation type.
- Unsupported constructs fail loudly with a clear message; they never pass silently.
- Not published in v0.1. Publishing under an owner-qualified Maven coordinate comes after namespace verification.
