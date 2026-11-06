# c4-contract-kit

Checks compiled Java classes against a C4 model written in Structurizr DSL, in both directions. A dependency between
two components that the model does not declare fails (a divergence), and so does a `REQUIRED` relationship without
any static reference (an absence). A `PERMITTED` relationship without references is reported as "no static
evidence", never as unused. Every finding carries class-level evidence: class, member, kind and source line.

The specification is [`AGENTS.md`](AGENTS.md); the decision behind it is
[ADR 0002](../../docs/adr/0002-c4-model-is-the-structural-authority.md). What the check cannot prove is in
[`docs/gaps.md`](../../docs/gaps.md), section 1.

## Use

From a test of the project whose classes are checked, for example `ArchitectureConformanceTest` in
`:apps:shipping-api`:

```java
@Test
void theCodeConformsToTheModel() {
    Report report = Conformance.check(Path.of("../../architecture/workspace.dsl"),
            List.of(Path.of("build/classes/java/main")));
    ConformanceAssertions.assertConforms(report, Path.of("build/reports/c4-contract"));
}
```

- `Conformance.check(model, productionClassRoots)` parses the model in restricted mode, reads the class files without
  loading them, and compares. It throws `ModelException` listing every model problem, each starting with the DSL
  identifiers of the elements it concerns, `IllegalArgumentException` for a class root that is not a directory or a
  class file that could not be imported (a corrupt file, a file that is not a class file, or a second definition of
  a class in another root: ArchUnit would skip it without a word), and `IllegalStateException` when an `archunit.*`
  system property would make the import load or resolve classes (see below). Class roots are directories of class
  files, such as `build/classes/java/main`, in any order; `module-info.class` is not a class and is ignored.
- `ConformanceAssertions.assertConforms(report, directory)` always writes `report.json` and `report.md`, then throws an
  `AssertionError` listing the violations if there are any.
- `Report` holds the components (`ComponentResult`), every edge (`EdgeResult`: mode `REQUIRED`, `PERMITTED` or
  `UNDECLARED`; status `CONVERGENCE`, `DIVERGENCE`, `ABSENCE`, `NO_STATIC_EVIDENCE` or `NOT_EVALUATED`), and the
  violations (`Violation`: `DIVERGENCE`, `ABSENCE`, `UNMAPPED_CLASS`, `ISOLATION_BREACH`,
  `PLANNED_COMPONENT_HAS_CODE`, `BUILT_COMPONENT_EMPTY`). `Report.passed()` is true when there is no violation.

## The model

The contract properties (`contract.root`, `contract.isolated`, `contract.id`, `contract.packages`, `contract.status`,
`contract.mode`) and their rules are in the header of [`architecture/workspace.dsl`](../../architecture/workspace.dsl).
The parser runs with all ten Structurizr features off, as
[its review](../../security/reviews/maven--structurizr-dsl--6.2.4.md) requires: `!include`, `!script`, `!plugin`,
`!docs`, `!adrs`, `!decisions`, `!components`, `workspace extends`, plugin classes for `!impliedRelationships`, files
and URLs are rejected, and so are image views and themes. Environment variables are never read: a `${NAME}` that no
`!const` or `!var` of the model defines is a model problem. Implied relationships never count, whatever the file
says. Any other `contract.*` property, or one on the wrong kind of element, is a model problem, so a misspelt
`contract.mode` cannot turn a `REQUIRED` relationship into a `PERMITTED` one.

Details the specification leaves open, decided here:

- A class nested in an isolated class, including anonymous and local classes, is isolated too.
- `package-info` declares a package, not a class. Its annotations count as references, but it never makes a
  component non-empty, a planned component built, or a class unmapped.
- References come exactly from ArchUnit's direct dependencies of each class. An array type counts as its element
  type. Missing classes are never resolved from the class path and no ArchUnit class resolver is used, so the
  result depends only on the class roots. The import switches off a class resolver or class-path resolution that
  `archunit.properties` sets; it refuses to run when an `archunit.classResolver` or
  `archunit.resolveMissingDependenciesFromClassPath=true` system property sets one, since a system property overrides
  any local setting.
- A package root must lie strictly below `contract.root`; no two package roots in the model may overlap, also not
  within one component.

## Reports

`report.json` (`"schema": "c4-contract-kit/report/v1"`) has the fields of the public records: `schema`, `modelPath`
(relative to the working directory), `passed`, `components`, `edges` and `violations`, each evidence item with
`originClass`, `originMember`, `targetClass`, `targetMember`, `kind`, `sourceFile` and `line` (0 when unknown).
`report.md` gives the verdict first, then each violation with its evidence, then a table of every edge with its
status, then the components. Both are UTF-8 with LF line ends, without timestamps, and in a fixed order:
identical inputs give identical bytes, whatever the order of class roots or model elements.

## Structure and checks

The library's five packages and the only dependencies allowed between them are in its self-model,
[`architecture/self.dsl`](architecture/self.dsl). `SelfConformanceTest` checks the library against it with the library
itself; `ReviewConditionsTest` checks the conditions of the ArchUnit and Structurizr reviews on the library's class
files (no `reflect()`, file-path imports only, one parser factory, no URL or feature re-enabled, and more).

```bash
./gradlew :libs:c4-contract-kit:check     # tests, Error Prone and NullAway
./gradlew :libs:c4-contract-kit:pitest    # mutation testing, at least 90 % killed: CI only, see below
```

Test fixtures are Java sources compiled at test time (`testing.FixtureCompiler`) into class roots of their own, so
they are never on the class path.

Mutation testing runs in CI only, in the `mutation` job of `.github/workflows/java.yml`. PIT's parent JVM listens on
all network interfaces without authentication, so its review allows it only on a disposable worker or a host whose
firewall blocks inbound connections to `java`. The `pitest` task passes `--mutableCodePaths` with the main output
directories, so PIT mutates the production classes and never the tests.
