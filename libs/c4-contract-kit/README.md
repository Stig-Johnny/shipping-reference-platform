# c4-contract-kit

Checks compiled Java classes against a C4 model written in Structurizr DSL, in both directions. A dependency between
two components that the model does not declare fails (a divergence). Between built components, a `REQUIRED`
relationship without any static reference fails too (an absence), and a `PERMITTED` relationship without references
is reported as "no static evidence", never as unused; a relationship that touches a planned component is not
evaluated. A finding about dependencies (a divergence or an isolation breach) carries each reference
as evidence: class, member, kind, and the source file and line when the class file records them. Absences, empty
components, planned components with code and unmapped classes have no reference to show; they name the components
and classes concerned.

Its web reader and conformance rules ship as a **labelled partial static check**. Package `sources` reads a web app's
TypeScript sources, tests, style sheets and configuration as data, with a deliberately restricted grammar and the
JDK alone. The implemented web conformance
library compares the imports it detects with the web container of the same model in both directions, with file,
line and specifier evidence: the web import check, below ([`AGENTS.md`](AGENTS.md), "Web imports"). The reader and
conformance library are tested on synthetic fixtures; `WebArchitectureConformanceTest` runs the repository's web-app
gate in every build, with all six parts planned and no real source or test files yet. The reader
refuses some recognized unsupported or ambiguous forms; it does not guarantee that every import is found or every
unsupported construct is refused. The owner accepted this scope for the reader and conformance rules on
2026-11-08 (ADR 0013).

Known open bypass: directly after spread syntax, `import("unlisted")`, `import(name)`, `require("unlisted")` and
`import.meta.glob("./*.ts")` can produce no imports and no refusals. The review found this in object, array, call
and nested-template spreads, including whitespace, comments and line breaks after `...`, in TS/TSX and source/test
placements. Parenthesizing the operand restores detection in the reviewed control; it does not close the bypass.
The review reported no other open bypass class in its probe set, which was not exhaustive. The restrictions below
describe recognized forms and tested cases, not complete enforcement.

Authoritative controls are planned: the reviewed TypeScript compiler's import graph for import dependencies, and a
strict Content-Security-Policy in the web app, enforced by the browser, for runtime loading and execution. Neither
is implemented by this library. Passing this check does not prove a complete import graph or runtime safety; see
[`docs/gaps.md`](../../docs/gaps.md#6-tests-and-quality-gates) and
[ADR 0013](../../docs/adr/0013-partial-web-import-lint-and-authoritative-controls.md).

The reader also refuses a slash after `>`: a closing type argument can end a value, while a comparison operator
starts an expression. Parenthesize the value before dividing; `=>` still starts an arrow-function expression.
Loader capabilities (`Function`, `eval`, workers, service workers and code-loading constructors) are refused as
identifiers, quoted destructuring keys and literal bracket keys, before aliasing or indirect calls. Escaped keys are
refused without decoding; escaped identifiers already stop the lexer. Computed access and binding keys must be one
plain string or number literal: templates, concatenations, parenthesized expressions and const-derived keys are
refused, rather than evaluated. Timer references must be direct calls with a visible function literal; aliases and
unproven arguments are refused. These conservative rules also reject harmless names and computed indexing.

Numeric tokens stop at the literal's boundary: decimal fractions, exponents, BigInts and binary/octal/hexadecimal
integers leave following property names visible, including `1..constructor` and `1n.constructor`. Separators must
occur between digits; malformed continuations, identifier escapes immediately after a number and legacy leading-zero
forms are refused. Regular expression flags must be distinct letters from `dgimsuy`; unknown, escaped and duplicate
flags are refused. Unicode sets (`v`) are refused because their character classes need a different reader. These
rules apply to TS and TSX in source and test roots, including test-named files in source roots.

In HTML, duplicate attribute names (case-insensitive) and any ampersand in `src`, `href` or `type` are refused.
Every `src` and `href` on a scanned element is validated before origin classification: ASCII controls (including
TAB/LF/CR), spaces, backslashes, non-ASCII characters and malformed percent escapes are refused. The accepted URL
syntax is a conservative subset: relative references and HTTP(S)/network references with plain DNS authorities.
Other schemes, credentials, encoded hosts, IP literals, numeric-ending hosts and invalid ports are refused without
approximating WHATWG parsing. Every accepted absolute/network reference is then refused as another origin. Local
paths, queries and fragments within the subset still pass. TypeScript imports of `.css` package subpaths, scoped or
unscoped and in any letter case, remain unsupported in browser code and tests. Style sheets must be linked from HTML.

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

This API compares detected imports and propagates reader refusals. Its `PASSED` verdict means no violations were
reported for those inputs; silent reader misses can still hide undeclared imports, boundary breaks and cycles.
The following shows how the repository's web-app gate calls this API.

```java
@Test
void theWebAppConformsToTheModel() {
    Path repository = Path.of("../..");
    WebReport report = WebConformance.check(repository.resolve("architecture/workspace.dsl"), repository);
    WebConformance.assertConforms(report, Path.of("build/reports/c4-contract-web"));
}
```

`WebArchitectureConformanceTest` does this for the repository in every build, including
`apps/shipping-web/e2e` as a test root alongside the source and public roots and app configuration. These roots and
configuration files are Gradle test inputs, so adding, editing or removing an end-to-end test reruns the gate.

The model's web container names its source root with `web.root`, its test and public roots, and the Node.js built-ins
and npm packages tests may import; each of its components names its directories (`web.paths`), status, optional entry
file and the npm packages it may import; relationships between them carry `web.mode`. The vocabulary is in the header of
[`architecture/workspace.dsl`](../../architecture/workspace.dsl), and the rules in [`AGENTS.md`](AGENTS.md) ("Web
imports").

The check reads the sources as text with a deliberately restricted import grammar, so it needs the JDK alone: no
Node, no TypeScript compiler and no parser library. It never runs, compiles or installs them. What the grammar reads
(import declarations, type-only imports, re-exports, `import()` of a string literal) becomes evidence. Recognized
unsupported forms fail the check with their file and line: `require`, `import()` of a computed value,
`import.meta` other than `import.meta.env`, path aliases, absolute and URL specifiers, `.js` and `.json` files,
triple-slash directives, and JSX it cannot read with certainty. Strings, comments, templates, regular expressions and
JSX text never count as code. The known spread bypass above can still hide imports and unsupported constructs
without a refusal. A relative import names a `.ts` file with its extension, as `./QuoteCard.ts`, which
must exist with exactly that name, or a file or directory without one, which must match exactly one file.

Every detected import is whitelisted twice: in the model, and in `security/allowlist.json`. A package must be declared
by the importing component (`web.packages`, or `web.testPackages` for a test), pinned in `package.json`, and approved for the
app in the allowlist; code that runs in the browser needs an approval for `dependencies`. Browser code may import no
Node.js built-in; a test may import those `web.testBuiltins` lists, written with `node:`, when the allowlist approves
the Node.js that `engines.node` names for project `ci` in configuration `web`. Approval for another project does not
cover these tests, even with the right version and job. Tests are read and held to their component's relationships
and entries, but never count as evidence, and no production file may import one. Settings of `package.json`,
`tsconfig*.json` and the
app's pages that would resolve a specifier otherwise (path aliases, `baseUrl`, an import map, and more) fail the check.

The comparison uses only detected imports. It fails on an import between two components without a relationship, on a
REQUIRED relationship between built components without a detected import, on a file in no component or in a planned
one, on a built component without code, on an import of a component other than through its `web.entry`, on an import
of a test, on an unresolved import, on a package
or built-in that is not declared or not approved, on a detected import cycle, on recognized unsupported syntax or
files, and on a refused setting of the app's configuration. Its reports, `report.json`
(`"schema": "c4-contract-kit/web-report/v1"`) and `report.md`, are deterministic like the class check's, and list
every detected import of a package or a Node.js built-in with the file and line that make it, the rule that decided it
and the allowlist entry that approves it.

`WebConformanceTest` checks synthetic repositories through the public API, including the reader's refusals of hidden
imports, loader references, ambiguous HTML attributes and approved package CSS subpaths. Its wrong-project Node.js
fixture checks both the failing verdict and the unapproved built-in import records. `WebReaderRefusalConformanceTest`
also checks quoted and escaped loader keys, computed keys and indirect timers in `.ts` and `.tsx` browser sources,
colocated tests and a separate test root, plus ambiguous script and link URLs (including controls and backslashes).
It reuses all 66 numeric-boundary and regex-flag refusal fixtures from the scanner and reader tests, checking each
independently in those six source/test placements. Constructor chains after numeric literals, uncertain numeric
continuations and unsupported regex flags must fail the public gate, including inside template substitutions and
after continued strings; same-line findings and shifted line numbers are preserved.
Every case requires `passed=false` and the exact findings with file, line and reason; escaped identifiers must not
hide an earlier quoted-key finding. The synthetic shop uses literal `[0]` access instead of array destructuring to
stay within the restricted grammar.

The repository gate is implemented by `WebArchitectureConformanceTest`, which also proves that an unapproved import
in the repository model's end-to-end test root fails. All six web parts remain planned and the source and test roots
are empty: the import rules have synthetic fixture evidence, with no real web imports checked yet, so the control
remains partial.

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
