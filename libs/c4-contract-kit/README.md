# c4-contract-kit

Checks compiled Java classes against a C4 model written in Structurizr DSL, in both directions. A dependency between
two components that the model does not declare fails (a divergence). Between built components, a `REQUIRED`
relationship without any static reference fails too (an absence), and a `PERMITTED` relationship without references
is reported as "no static evidence", never as unused; a relationship that touches a planned component is not
evaluated. A finding about dependencies (a divergence or an isolation breach) carries each reference
as evidence: class, member, kind, and the source file and line when the class file records them. Absences, empty
components, planned components with code and unmapped classes have no reference to show; they name the components
and classes concerned.

It also holds a web app's TypeScript imports to the web container of the same model, in both directions, with file,
line and specifier evidence: the web import check, below.

The reader also refuses a slash after `>`: a closing type argument can end a value, while a comparison operator
starts an expression. Parenthesize the value before dividing; `=>` still starts an arrow-function expression.
References to `Function`, `Worker`, `SharedWorker` and `importScripts` are refused even without a direct call,
including aliases, indirect calls and literal computed properties. This conservative rule also refuses type uses
of those names. In HTML, duplicate attribute names (case-insensitive) and any ampersand in `src`, `href` or `type`
are refused before URL and import-map checks; the reader does not decode character references. TypeScript imports
of `.css` package subpaths, scoped or unscoped and in any letter case, are unsupported in both browser code and
tests, even if the package is approved. Style sheets must be linked from HTML.

The library is built and tested, and the shipping API is its first user: `ArchitectureConformanceTest` in
`:apps:shipping-api` checks the API's classes with it. `SelfConformanceTest` checks the
library's own six packages against its self-model, [`architecture/self.dsl`](architecture/self.dsl), and the negative
fixtures of `ConformanceDivergenceTest` and `ConformanceAbsenceTest` show both directions failing. The commands to run
them, how long they took, and how to watch the self-check fail are under [Try it](../../README.md#try-it) in the
repository's README.

The specification is [`AGENTS.md`](AGENTS.md); the decision behind it is
[ADR 0002](../../docs/adr/0002-c4-model-is-the-structural-authority.md). What the check cannot prove is in
[`docs/gaps.md`](../../docs/gaps.md), section 1.

## Use

From a test of the project whose classes are checked, such as `ArchitectureConformanceTest` in `:apps:shipping-api`,
which passes every main class-output directory of the API (`SelfConformanceTest` does the same for the library):

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
  identifiers of the elements it concerns or with its place in the model file (the parser itself stops at the first
  line it rejects, and a file with a line that the restricted DSL leaves out is not parsed at all; see below),
  `IllegalArgumentException` for a class root that is not a directory, a class root that is or holds a symbolic link
  to a directory (ArchUnit does not follow one), or a class file that could not be imported (a corrupt file, a file
  that is not a class file, or a second definition of a class in another root: ArchUnit would skip it without a word),
  and `IllegalStateException` when an `archunit.*` system property would make the import load or resolve classes (see
  below). Class roots are directories of class files, such as `build/classes/java/main`, in any order;
  `module-info.class` is not a class and is ignored.
- `ConformanceAssertions.assertConforms(report, directory)` always writes `report.json` and `report.md`, then throws an
  `AssertionError` listing the violations if there are any.
- `Report` holds the components (`ComponentResult`), every edge (`EdgeResult`: mode `REQUIRED`, `PERMITTED` or
  `UNDECLARED`; status `CONVERGENCE`, `DIVERGENCE`, `ABSENCE`, `NO_STATIC_EVIDENCE` or `NOT_EVALUATED`), and the
  violations (`Violation`: `DIVERGENCE`, `ABSENCE`, `UNMAPPED_CLASS`, `ISOLATION_BREACH`,
  `PLANNED_COMPONENT_HAS_CODE`, `BUILT_COMPONENT_EMPTY`). `Report.passed()` is true when there is no violation.

## The web import check

```java
@Test
void theWebAppConformsToTheModel() {
    Path repository = Path.of("../..");
    WebReport report = WebConformance.check(repository.resolve("architecture/workspace.dsl"), repository);
    WebConformance.assertConforms(report, Path.of("build/reports/c4-contract-web"));
}
```

The model's web container names its source root with `web.root`, its test and public roots, and the Node.js built-ins
and npm packages tests may import; each of its components names its directories (`web.paths`), status, optional entry
file and the npm packages it may import; relationships between them carry `web.mode`. The vocabulary is in the header of
[`architecture/workspace.dsl`](../../architecture/workspace.dsl), and the rules in [`AGENTS.md`](AGENTS.md) ("Web
imports").

The check reads the sources as text with a deliberately restricted import grammar, so it needs the JDK alone: no
Node, no TypeScript compiler and no parser library. It never runs, compiles or installs them. What the grammar reads
(import declarations, type-only imports, re-exports, `import()` of a string literal) becomes evidence; what it does not
cover fails the check with its file and line, never passes unseen: `require`, `import()` of a computed value,
`import.meta` other than `import.meta.env`, path aliases, absolute and URL specifiers, `.js` and `.json` files,
triple-slash directives, and JSX it cannot read with certainty. Strings, comments, templates, regular expressions and
JSX text never count as code. A relative import names a `.ts` file with its extension, as `./QuoteCard.ts`, which
must exist with exactly that name, or a file or directory without one, which must match exactly one file.

Every import is whitelisted twice: in the model, and in `security/allowlist.json`. A package must be declared by the
importing component (`web.packages`, or `web.testPackages` for a test), pinned in `package.json`, and approved for the
app in the allowlist; code that runs in the browser needs an approval for `dependencies`. Browser code may import no
Node.js built-in; a test may import those `web.testBuiltins` lists, written with `node:`, when the allowlist approves
the Node.js that `engines.node` names. Tests are read and held to their component's relationships and entries, but
never count as evidence, and no production file may import one. Settings of `package.json`, `tsconfig*.json` and the
app's pages that would resolve a specifier otherwise (path aliases, `baseUrl`, an import map, and more) fail the check.

It fails on an import between two components without a relationship, on a REQUIRED relationship between built
components without an import, on a file in no component or in a planned one, on a built component without code, on an
import of a component other than through its `web.entry`, on an import of a test, on an unresolved import, on a package
or built-in that is not declared or not approved, on an import cycle, on unsupported syntax or files, and on a refused
setting of the app's configuration. Its reports, `report.json`
(`"schema": "c4-contract-kit/web-report/v1"`) and `report.md`, are deterministic like the class check's, and list
every import of a package or a Node.js built-in with the file and line that make it, the rule that decided it and the
allowlist entry that approves it.

## The model

The contract properties (`contract.root`, `contract.isolated`, `contract.id`, `contract.packages`, `contract.status`,
`contract.mode`) and their rules are in the header of [`architecture/workspace.dsl`](../../architecture/workspace.dsl).
The parser runs with all ten Structurizr features off, as
[its review](../../security/reviews/maven--structurizr-dsl--6.2.4.md) requires: `!include`, `!script`, `!plugin`,
`!docs`, `!adrs`, `!decisions`, `!components`, `workspace extends`, plugin classes for `!impliedRelationships`, files
and URLs are rejected, and so are image views and themes. Environment variables are never read. Constants, variables,
`${...}` and line continuations are not part of the restricted DSL either: before parsing, each line that holds `${`
(with a name or without one), `!const` or `!var` (in any letter case, also inside a word), or that ends in a
backslash, is a model problem located by its line, and a file with such a line is not parsed. The check reads the file
as text, and that has a deliberate cost: a harmless comment, example or text block that holds `${`, `!const`, `!var`
or a trailing backslash is rejected too. After parsing, a `${` in any text that the parsed workspace holds (every
element, relationship and view, deployment environments and groups, styles, terminology, users; `ParsedStrings` lists
the places) is a problem too, named by its element or view: the parser can put one together from parts that hold none,
such as nested group names joined by a `$` group separator. Only what the workspace keeps is read: a name that parsing
puts together for an empty group stays in the parser's own table, no element carries it, and it cannot reach a report.
The model file must be UTF-8. Implied relationships are
disabled: the DSL creates them unless the file says `!impliedRelationships false`, and a model in which the parser
created one is a model problem (a deployment instance's copy of its container's relationship is not implied). Any
other `contract.*` property, or one on the wrong kind of element or relationship, is a model problem, so a misspelt
`contract.mode` cannot turn a `REQUIRED` relationship into a `PERMITTED` one.

Details the specification leaves open, decided here:

- A class declared in an isolated class (a member, local or anonymous class) is isolated too, by the enclosing class
  its class file records. A `$` in a name is not nesting: a top-level class `Main$Helper`, or a class in a package
  `Main$helpers`, is not covered by an isolated `Main`.
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

For example, with the `report -> model` relationship removed from the self-model, `report.md` opens with
`**Verdict: FAILED** with 1 violation.` and lists `DIVERGENCE: report -> model` with each class in `report` that
calls into `model`, its member and its source line.

## Structure and checks

The library's six packages and the only dependencies allowed between them are in its self-model,
[`architecture/self.dsl`](architecture/self.dsl). `SelfConformanceTest` checks the library against it with the library
itself; `ReviewConditionsTest` checks the conditions of the ArchUnit and Structurizr reviews on the library's class
files (no `reflect()`, file-path imports only, one parser factory, no URL or feature re-enabled, and more). A rule on
a method or constructor also catches a method reference to it (`System::getenv`, `StructurizrDslParser::new`). Those
rules, and the bans on `ObjectInputStream` and `ProcessBuilder`, are shown to fail on compiled fixtures that break
them, once by a call and once by a reference.

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
