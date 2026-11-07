# AGENTS.md: libs/c4-contract-kit (Gradle project `:libs:c4-contract-kit`)

Adds to the root `AGENTS.md`. This library is the architecture control (ADR 0002). Everything under
`libs/c4-contract-kit/src/` is risk level 3, and so are its tests: weakening a test weakens the control.

## What it does

Compares a Structurizr DSL model with the static dependencies of compiled Java classes, in both directions, and
reports class-level evidence. It fails on dependencies the model does not declare (divergences) and on REQUIRED
relationships without any static reference (absences). It reports PERMITTED relationships without references as
"no static evidence", never as "unused".

Its package `sources` reads a web app's TypeScript sources as data, with a restricted import grammar, and resolves
each import to a file or a package (section "Web imports"). The web import check that is to hold them to the web
container of the same model is not built yet.

## Boundaries

- Package `io.github.stigjohnny.c4contractkit` with exactly these sub-packages:
  - `api`: the only public package. `Conformance`, `Report`, `ConformanceAssertions`, result records and
    `ModelException`.
  - `model`: restricted DSL parsing and validation into an immutable `ContractModel`.
  - `bytecode`: class import with ArchUnit and evidence extraction.
  - `sources`: a web root read as data with the restricted import grammar, and each import resolved; the JDK only.
  - `rules`: comparison and violation rules.
  - `report`: deterministic JSON and Markdown writers.
- Allowed internal dependencies: `api -> model, bytecode, rules, report`; `rules -> model, bytecode`;
  `report -> rules, model`. `sources` depends on no other package, and no package uses it yet. No other edges; no
  cycles. The self-model `libs/c4-contract-kit/architecture/self.dsl`
  (slice 4) states these edges and `SelfConformanceTest` checks the library against it.
- Third-party code: `com.structurizr:structurizr-dsl:6.2.4` (in `model` only) and `com.tngtech.archunit:archunit:1.5.1`
  (in `bytecode` only). No Spring, no logging framework, no other runtime dependency. Tests: JUnit and AssertJ 3.27.7.
- It never loads or initialises the analysed classes; it reads class files only. It never runs, compiles or installs
  the analysed web sources; it reads them as text.

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

## Web imports

Reading the sources (`sources`): every regular file under a web root, by name and the kind of root. In a source root,
`.ts`, `.tsx` and `.d.ts` are code, and `*.test.ts` and `*.test.tsx` are tests; both are scanned. In a test root every
`.ts` and `.tsx` file is a test. A public root may hold style sheets, images and fonts only. `.css` is searched, as raw
text, for `@import` in any letter case and for an at-rule whose name holds a backslash escape (`@\69mport`), both
refused wherever they are, comments and strings included; images and fonts are assets; any other file is unsupported.
Names that start with a dot are left out, but a symbolic link anywhere is an error, hidden ones and those in hidden
directories included: links are checked before anything is left out. Files must be UTF-8.

The restricted import grammar reads, with the line of the specifier: `import ... from 'x'`, `import 'x'`,
`import type`, `export * from`, `export { a } from`, their type-only forms, and `import('x')` with one plain string
literal anywhere, types included. It refuses: `import()` with anything else, `require`, `import.meta` other than
`import.meta.env` (a test, which runs in Node.js, may also locate files with `import.meta.url`, `import.meta.dirname`
and `import.meta.filename`), import-equals declarations, import attributes, a module declaration with a module name
(`module 'x'`, with or without `declare`), code that loads code at run time (`eval` and the `Function` constructor,
even as properties such as `globalThis.eval`; a call of a `constructor` property; `Worker`, `SharedWorker`,
`importScripts` and `serviceWorker`; `setTimeout` and `setInterval` with a string), triple-slash directives, JSX
pragmas and a backslash escape in a specifier. Strings, comments, templates, regular expressions and JSX text never
yield code; template substitutions and JSX expressions do.

The lexer fails closed: where it cannot decide with certainty, it refuses the file with the line, and never guesses.

- A slash, and in `.tsx` a `<`, starts a regular expression or a JSX element where an expression starts: after a
  punctuator other than `)`, `]`, `}`, `++` and `--`, after a keyword such as `return`, `typeof` or `else`, after the
  `)` that ends the head of `if`, `while`, `for` (or `for await`) or `with`, and after a negating `!`.
- It is an operator after a name, a literal, `]`, a property named like a keyword (`options.default / 2` divides), the
  `)` of a call or a group, and a non-null assertion: a `!` right after a value on the same line (`n! / 2`).
- Where it could be either, after `}`, `++`, `--`, an unmatched `)`, a module specifier, `of`, `await` or `yield`, the
  file is refused.
- White space is ECMAScript's: every Zs character, TAB, VT, FF and ZWNBSP. Line ends are LF and CRLF.
- Refused: a carriage return without a line feed and U+2028 and U+2029 anywhere, since TypeScript ends a comment
  there; any other character outside strings, comments, templates and JSX text (U+0085 and U+200B among them, which
  TypeScript skips as white space); unterminated literals or comments; a backslash in code; JSX the lexer does not
  cover, including a `.tsx` generic arrow function that starts with `<`; every comment that names `@jsx`, in any
  letter case; and every comment or line that starts with `///`.

Resolution: a relative specifier that ends in `.ts` names exactly that file (the form TypeScript's
`rewriteRelativeImportExtensions` rewrites to `.js` on emit); it must exist with exactly that name, and no other
extension, `index` file or letter case is tried in its place. One without an extension names `p.ts`, `p.tsx`,
`p.d.ts` or the `index` file of the directory `p` and must match exactly one existing file, by exact name; one with an
image or font extension names that file; one that names a style sheet (`.css`) is unsupported, since style sheets
are linked from `index.html`; one with any other code or JSON extension (`.tsx`, `.js`, `.mts`, `.json`, or `.TS` in
another letter case) is unsupported; one that leaves the web root is unresolved. A bare specifier names a built-in
module of Node.js when it is the name of one (`fs`, `assert/strict`), and otherwise a package (scoped or not), after
which a subpath of names may follow: no empty, `.` or `..` segment and no percent sign. `node:` followed by the name of
a built-in module names that module (`node:test`, `node:assert/strict`); any other `node:` specifier is unsupported.
Absolute specifiers, other schemes (`https:`, `data:`, `virtual:`), queries and fragments are unsupported.

The web app's configuration (`AppConfiguration`, read as strict JSON and as text): the check resolves specifiers
itself, so a setting that would make TypeScript, Node.js or the browser resolve one otherwise, or add an import that no
source file shows, is refused with its file and line. In `tsconfig*.json`: `extends`, `references`, and in
`compilerOptions` `paths`, `baseUrl`, `rootDirs`, `typeRoots`, `moduleSuffixes`, `customConditions`,
`allowArbitraryExtensions` and `jsxImportSource`; `types` unless it is empty, `importHelpers` when true, and a `jsx`
that imports a JSX runtime. In `package.json`: `imports`, `overrides`, `browser` and dependencies other than
`dependencies` and `devDependencies`, whose versions must be exact; `engines.node` names one exact version of Node.js.
In each page (`*.html`): an import map, an inline script, a `<base>` element, a script or link from another origin, and
a style element that names `@import`. A file that is not plain JSON (comments and trailing commas included), not UTF-8
or not a regular file is refused too.

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
