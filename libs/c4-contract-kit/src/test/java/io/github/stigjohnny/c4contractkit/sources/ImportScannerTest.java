package io.github.stigjohnny.c4contractkit.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The restricted import grammar (libs/c4-contract-kit/AGENTS.md, "Web imports"): every form that makes a module
 * depend on another is either read, with its line and specifier, or refused. Nothing that looks like code inside a
 * string, comment, template, regular expression or JSX text counts, and nothing that resolves modules at run time
 * passes unseen.
 */
class ImportScannerTest {

    private static ImportScanner.Scan ts(String text) {
        return ImportScanner.scan(text, false);
    }

    private static ImportScanner.Scan tsx(String text) {
        return ImportScanner.scan(text, true);
    }

    @Test
    void readsEveryStaticImportFormWithTheLineOfItsSpecifier() {
        ImportScanner.Scan scan = ts("""
                import a from './a';
                import * as b from "./b";
                import { c, d as e } from './c';
                import f, { g } from './f';
                import h, * as i from './h';
                import './side-effect.css';
                import {
                    j,
                    k,
                } from './j';
                import type from './type-is-a-default-binding';
                import type, { l } from './l';
                """);

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).extracting(SourceImport::line, SourceImport::specifier, SourceImport::kind)
                .containsExactly(
                        tuple(1, "./a", ImportKind.IMPORT),
                        tuple(2, "./b", ImportKind.IMPORT),
                        tuple(3, "./c", ImportKind.IMPORT),
                        tuple(4, "./f", ImportKind.IMPORT),
                        tuple(5, "./h", ImportKind.IMPORT),
                        tuple(6, "./side-effect.css", ImportKind.SIDE_EFFECT),
                        tuple(10, "./j", ImportKind.IMPORT),
                        tuple(11, "./type-is-a-default-binding", ImportKind.IMPORT),
                        tuple(12, "./l", ImportKind.IMPORT));
    }

    @Test
    void typeOnlyImportsAndReExportsAreDependenciesOfTheirOwnKind() {
        ImportScanner.Scan scan = ts("""
                import type { A } from './a';
                import type B from './b';
                import type * as C from './c';
                import { type D } from './d';
                export * from './e';
                export * as f from './f';
                export { g, h as i } from './g';
                export type { J } from './j';
                export type * from './k';
                export type * as L from './l';
                """);

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).extracting(SourceImport::specifier, SourceImport::kind).containsExactly(
                tuple("./a", ImportKind.TYPE_IMPORT),
                tuple("./b", ImportKind.TYPE_IMPORT),
                tuple("./c", ImportKind.TYPE_IMPORT),
                tuple("./d", ImportKind.IMPORT),
                tuple("./e", ImportKind.RE_EXPORT),
                tuple("./f", ImportKind.RE_EXPORT),
                tuple("./g", ImportKind.RE_EXPORT),
                tuple("./j", ImportKind.TYPE_RE_EXPORT),
                tuple("./k", ImportKind.TYPE_RE_EXPORT),
                tuple("./l", ImportKind.TYPE_RE_EXPORT));
    }

    @Test
    void localExportsAndDeclarationsAreNoDependencies() {
        ImportScanner.Scan scan = ts("""
                const a = 1;
                export { a };
                export { a as b };
                export type T = { from: string };
                export interface U { from: 'x' }
                export const from = 'not a specifier';
                export default function main() {}
                export enum E { A }
                export abstract class Base {}
                export declare const declared: number;
                """);

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).isEmpty();
    }

    @Test
    void aDynamicImportOfAPlainStringIsADependencyAnywhereEvenInATypePosition() {
        ImportScanner.Scan scan = ts("""
                const lazy = () => import('./lazy');
                type T = typeof import("./types");
                let u: import('./more').U;
                """);

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).extracting(SourceImport::line, SourceImport::specifier, SourceImport::kind)
                .containsExactly(
                        tuple(1, "./lazy", ImportKind.DYNAMIC),
                        tuple(2, "./types", ImportKind.DYNAMIC),
                        tuple(3, "./more", ImportKind.DYNAMIC));
    }

    @ParameterizedTest
    @ValueSource(strings = {"import(name)", "import(`./a`)", "import(`./${name}`)", "import('./a' + name)",
        "import('./a', { with: { type: 'json' } })", "import()"})
    void aDynamicImportOfAnythingButOnePlainStringIsRefused(String expression) {
        ImportScanner.Scan scan = ts("const name = 'a';\nconst m = " + expression + ";\n");

        assertThat(scan.imports()).isEmpty();
        assertThat(scan.refusals()).extracting(Refusal::line, Refusal::reason).containsExactly(tuple(2,
                "import() is supported only with one plain string literal: the module it loads must be known "
                        + "without running the code"));
    }

    @Test
    void requireAndImportMetaResolutionAreRefusedWhereverTheyAppear() {
        ImportScanner.Scan scan = ts("""
                const a = require('./a');
                const modules = import.meta.glob('./pages/*.ts');
                const url = new URL('./b.svg', import.meta.url);
                const c = import.meta.resolve('./c');
                const fine = import.meta.env.MODE;
                """);

        assertThat(scan.imports()).isEmpty();
        assertThat(scan.refusals()).extracting(Refusal::line, Refusal::reason).containsExactly(
                tuple(1, "require is not supported: modules are loaded with import declarations only"),
                tuple(2, "import.meta is supported only as import.meta.env: import.meta.glob, import.meta.url and "
                        + "import.meta.resolve locate modules or files at run time"),
                tuple(3, "import.meta is supported only as import.meta.env: import.meta.glob, import.meta.url and "
                        + "import.meta.resolve locate modules or files at run time"),
                tuple(4, "import.meta is supported only as import.meta.env: import.meta.glob, import.meta.url and "
                        + "import.meta.resolve locate modules or files at run time"));
    }

    @Test
    void typeScriptModuleFormsOutsideTheGrammarAreRefused() {
        ImportScanner.Scan scan = ts("""
                import fs = require('fs');
                import Alias = Some.Namespace;
                export import Other = Some.Other;
                import json from './data.json' with { type: 'json' };
                import legacy from './legacy.json' assert { type: 'json' };
                declare module 'virtual:thing' {
                    export const x: number;
                }
                """);

        assertThat(scan.refusals()).extracting(Refusal::line, Refusal::reason).containsExactly(
                tuple(1, "import ... = (an import-equals declaration) is not supported"),
                tuple(1, "require is not supported: modules are loaded with import declarations only"),
                tuple(2, "import ... = (an import-equals declaration) is not supported"),
                tuple(3, "import ... = (an import-equals declaration) is not supported"),
                tuple(4, "import attributes (with or assert) are not supported"),
                tuple(5, "import attributes (with or assert) are not supported"),
                tuple(6, "declare module with a module name is not supported: it declares a module that no file "
                        + "of the web root is"));
        assertThat(scan.imports()).extracting(SourceImport::specifier)
                .containsExactly("./data.json", "./legacy.json");
    }

    @Test
    void tripleSlashDirectivesAndJsxPragmasAreRefusedBecauseTheyAddDependenciesInComments() {
        ImportScanner.Scan scan = tsx("""
                /// <reference types="vite/client" />
                ///<reference path="./globals.d.ts" />
                /** @jsxImportSource preact */
                // An ordinary comment that mentions /// <reference> is fine.
                /* So is one that says import x from 'y' or require('z'). */
                export const ok = 1;
                """);

        assertThat(scan.imports()).isEmpty();
        assertThat(scan.refusals()).extracting(Refusal::line, Refusal::reason).containsExactly(
                tuple(1, "triple-slash directives (/// <reference ...>) are not supported: they add dependencies "
                        + "in comments"),
                tuple(2, "triple-slash directives (/// <reference ...>) are not supported: they add dependencies "
                        + "in comments"),
                tuple(3, "JSX pragmas (@jsx, @jsxImportSource, @jsxFrag, @jsxRuntime) are not supported: they "
                        + "change which module JSX imports"));
    }

    @Test
    void wordsInStringsCommentsTemplatesAndRegularExpressionsAreNotCode() {
        ImportScanner.Scan scan = ts("""
                const s1 = "import x from './not-a'; require('./not-b')";
                const s2 = 'export * from "./not-c"';
                const t = `import('./not-d') ${s1.length > 0 ? `require('./not-e')` : ''} import.meta.glob`;
                const r1 = /import\\('x'\\)|['"`]/g;
                const r2 = s1.replace(/require\\(/, '') / 2;
                // import y from './not-f';
                /* export { z } from './not-g'; */
                const object = { import: 1, require: 2, module: 'x' };
                const member = object.import + object?.require;
                import real from './real';
                """);

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).extracting(SourceImport::line, SourceImport::specifier)
                .containsExactly(tuple(10, "./real"));
    }

    @Test
    void jsxTextAndAttributesMayHoldQuotesAndCodeLikeWordsWhileExpressionsInsideThemAreCode() {
        ImportScanner.Scan scan = tsx("""
                import { lazy } from 'react';
                export function View({ items }: { items: string[] }) {
                    const Page = lazy(() => import('./Page'));
                    return (
                        <section title="Don't import 'this' {here}" data-x='a "b" c'>
                            <p>Don't import this, and don't require that.</p>
                            {items.length > 0 ? <ul>{items.map((item) => <li key={item}>{item}'s</li>)}</ul> : null}
                            <>
                                <Page />
                                {/* import('./not-in-a-comment') */}
                                {cond && import('./also-real')}
                            </>
                        </section>
                    );
                }
                const less = 1 < 2 && 3 > 1;
                const generic = new Map<string, number>();
                """);

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).extracting(SourceImport::line, SourceImport::specifier, SourceImport::kind)
                .containsExactly(
                        tuple(1, "react", ImportKind.IMPORT),
                        tuple(3, "./Page", ImportKind.DYNAMIC),
                        tuple(11, "./also-real", ImportKind.DYNAMIC));
    }

    @Test
    void aTypeAssertionInATsFileIsNotJsx() {
        ImportScanner.Scan scan = ts("""
                const value = <string>input;
                const fn = <T>(x: T): T => x;
                import later from './later';
                """);

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).extracting(SourceImport::specifier).containsExactly("./later");
    }

    @Test
    void aGenericArrowFunctionInATsxFileIsRefusedRatherThanMisread() {
        ImportScanner.Scan scan = tsx("""
                const identity = <T,>(x: T) => x;
                import later from './later';
                """);

        assertThat(scan.imports()).isEmpty();
        assertThat(scan.refusals()).extracting(Refusal::line, Refusal::reason).containsExactly(tuple(1,
                "JSX: unexpected ',' in a tag (a generic arrow function or generic function type starting with "
                        + "'<' is not supported in .tsx files)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "const a = 'unterminated;\nimport b from './b';\n",
        "const a = \"unterminated;\nimport b from './b';\n",
        "const a = `unterminated;\nimport b from './b';\n",
        "/* unterminated comment\nimport b from './b';\n",
        "const r = /unterminated;\nimport b from './b';\n"})
    void anUnterminatedLiteralOrCommentStopsTheScanAndIsRefused(String text) {
        ImportScanner.Scan scan = ts(text);

        assertThat(scan.imports()).isEmpty();
        assertThat(scan.refusals()).hasSize(1).first().satisfies(refusal -> {
            assertThat(refusal.line()).isEqualTo(1);
            assertThat(refusal.reason()).startsWith("unterminated ");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "const a = <div>never closed;\n",
        "const a = <div>text</span>;\n",
        "const a = <div>a } b</div>;\n",
        "const a = <div>a > b</div>;\n",
        "const a = <div attr=<b />></div>;\n"})
    void jsxOutsideTheGrammarIsRefused(String text) {
        ImportScanner.Scan scan = tsx(text);

        assertThat(scan.refusals()).hasSize(1).first().extracting(Refusal::reason).asString().startsWith("JSX: ");
    }

    @Test
    void lineSeparatorsAndEscapedIdentifiersAreRefused() {
        assertThat(ts("const a = 1;\u2028import b from './b';\n").refusals()).extracting(Refusal::reason)
                .containsExactly("the characters U+2028 and U+2029 are not supported");
        assertThat(ts("const \\u0061 = 1;\n").refusals()).extracting(Refusal::reason)
                .containsExactly("a backslash outside a string, template or regular expression is not supported");
    }

    @Test
    void statementsAfterAnotherOnTheSameLineAndWithoutSemicolonsAreRead() {
        ImportScanner.Scan scan = ts("""
                import a from './a'; import b from './b'
                import c from './c'
                export { d } from './d'\r
                import e from './e'
                """);

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).extracting(SourceImport::line, SourceImport::specifier).containsExactly(
                tuple(1, "./a"), tuple(1, "./b"), tuple(2, "./c"), tuple(3, "./d"), tuple(4, "./e"));
    }

    @Test
    void escapesInsideStringSpecifiersAreRefusedSoThatTheSpecifierIsExactlyItsText() {
        ImportScanner.Scan scan = ts("import a from './\\u0061';\n");

        assertThat(scan.imports()).isEmpty();
        assertThat(scan.refusals()).extracting(Refusal::reason)
                .containsExactly("a module specifier with a backslash escape is not supported");
    }

    @Test
    void anImportClauseThatIsNotFollowedByFromAndAStringIsRefused() {
        assertThat(ts("import { a } './a';\n").refusals()).extracting(Refusal::reason)
                .containsExactly("unsupported import declaration: expected from and a string after the import clause");
        assertThat(ts("import * from './a';\n").refusals()).extracting(Refusal::reason)
                .containsExactly("unsupported import declaration: expected from and a string after the import clause");
        assertThat(ts("export * './a';\n").refusals()).extracting(Refusal::reason)
                .containsExactly("unsupported export declaration: expected from and a string after export *");
        assertThat(ts("export { a } from b;\n").refusals()).extracting(Refusal::reason)
                .containsExactly("unsupported export declaration: expected a string after from");
    }

    @Test
    void cssImportRulesAreRefusedAndOtherCssIsNoDependency() {
        assertThat(ImportScanner.scanStyle("""
                /* @import url('./not-a-rule.css'); */
                :root { --action: #0f766e; }
                .icon { background: url('./icon.svg'); }
                """)).isEmpty();
        assertThat(ImportScanner.scanStyle("""
                :root { --a: 1; }
                @IMPORT './tokens.css';
                """)).extracting(Refusal::line, Refusal::reason).containsExactly(tuple(2,
                "CSS @import is not supported: import style sheets from TypeScript, where the check sees them"));
    }

    /**
     * Snippets the lexer must read exactly: each comes before an import of ./z, which must be found on the right line
     * with nothing refused. A slash read as the start of a regular expression where it divides, or the other way
     * round, would swallow a quote and stop the scan; a line end counted wrongly would move the import.
     */
    static Stream<Arguments> snippetsTheLexerReadsExactly() {
        return Stream.of(
                arguments("division after an identifier", "const half = total / 2; const s = 'a/b';", 1, false),
                arguments("division after a number", "const r = 10 / 5; const q = \"it's\";", 1, false),
                arguments("division after )", "const r = (a + b) / 2; const s = \"it's\";", 1, false),
                arguments("division after ]", "const r = list[0] / 2; const s = \"it's\";", 1, false),
                arguments("division after ++", "let i = 0; i++ / 2; const s = \"it's\";", 1, false),
                arguments("division after --", "let i = 0; i-- / 2; const s = \"it's\";", 1, false),
                arguments("division after a string", "const r = 'a' / 2; const s = \"it's\";", 1, false),
                arguments("division after a template", "const r = `a` / 2; const s = \"it's\";", 1, false),
                arguments("division after a regular expression", "const r = /a/ / 2; const s = \"it's\";", 1, false),
                arguments("division after a decimal", "const r = 1.5 / 2; const s = \"it's\";", 1, false),
                arguments("division after a tab", "const r = a\t/ 2; const s = \"it's\";", 1, false),
                arguments("division after a no-break space", "const r = a\u00A0/ 2; const s = \"it's\";", 1, false),
                arguments("division after a form feed", "const r = a\f/ 2; const s = \"it's\";", 1, false),
                arguments("division after a vertical tab", "const r = a\u000B/ 2; const s = \"it's\";", 1, false),
                arguments("division after a byte order mark", "const r = a\uFEFF/ 2; const s = \"it's\";", 1, false),
                arguments("division after a carriage return", "const r = a\r/ 2; const s = \"it's\";", 1, false),
                arguments("a regular expression after return", "function f() { return /'/.test(s); }", 1, false),
                arguments("a regular expression after typeof", "const t = typeof /'/;", 1, false),
                arguments("a regular expression after a comma", "f(a, /'/);", 1, false),
                arguments("a regular expression after !", "if (!/'/.test(s)) {}", 1, false),
                arguments("a regular expression after =>", "const f = () => /'/;", 1, false),
                arguments("a slash inside a character class", "const r = /[/']/;", 1, false),
                arguments("an escaped slash", "const r = /\\/'/;", 1, false),
                arguments("flags", "const r = /a/gimsuy; const s = \"it's\";", 1, false),
                arguments("an escaped backtick in a template", "const t = `a\\`b ${c} 'd`;", 1, false),
                arguments("a template over two lines", "const t = `one\ntwo ${'x'}`;", 2, false),
                arguments("a nested template", "const t = `a ${b ? `c ${d}` : ''} e`;", 1, false),
                arguments("an escaped quote", "const s = 'it\\'s';", 1, false),
                arguments("a string continued after a backslash and CRLF", "const s = 'a\\\r\nb';", 2, false),
                arguments("a string continued after a backslash and LF", "const s = 'a\\\nb';", 2, false),
                arguments("a string continued after a backslash and CR", "const s = 'a\\\rb';", 1, false),
                arguments("a template continued after a backslash and LF", "const t = `a\\\nb`;", 2, false),
                arguments("a block comment over three lines", "/* one\ntwo\nthree */", 3, false),
                arguments("identifiers that only contain require", "const x = $require('a') + _require('b') "
                        + "+ require$('c') + requireAll('d');", 1, false),
                arguments("an identifier with a joiner", "const a\u200Cb = 1; const s = \"it's\";", 1, false),
                arguments("a member named after a keyword", "const m = a?.import + a.require + b.module;", 1, false),
                arguments("module as a variable", "declare module Foo { }\nconst module = 1;", 2, false),
                arguments("with as a statement", "with (scope) { }", 1, false),
                arguments("import.meta.env", "const mode = import.meta.env.MODE;", 1, false),
                arguments("comparison after ) in TSX", "const c = (a) < b && c > d;", 1, true),
                arguments("a boolean attribute", "const e = <input disabled />;", 1, true),
                arguments("spread attributes", "const e = <Field {...props} key={`k`} />;", 1, true),
                arguments("namespaced and member names", "const e = <svg:rect data-x='1'><Foo.Bar /></svg:rect>;",
                        1, true),
                arguments("comments in a tag", "const e = <div /* a */ id=\"x\" // b\n>it's</div>;", 2, true),
                arguments("spaces in a closing tag", "const e = <div>it's</ div >;", 1, true),
                arguments("an attribute string over two lines", "const e = <a title=\"one\ntwo\">it's</a>;", 2,
                        true),
                arguments("JSX text over two lines", "const e = <p>one\ntwo's</p>;", 2, true),
                arguments("an expression container over two lines", "const e = <p>{a\n}</p>;", 2, true),
                arguments("a comparison after an element", "const e = <a /> < b;", 1, true),
                arguments("JSX after default", "export default <App />;", 1, true),
                arguments("JSX after a keyword and in a ternary", "function f() { return a ? <b>it's</b> : <c />; }",
                        1, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("snippetsTheLexerReadsExactly")
    void theLexerReadsSnippetsExactly(String name, String snippet, int lines, boolean jsx) {
        ImportScanner.Scan scan = ImportScanner.scan(snippet + "\nimport z from './z';\n", jsx);

        assertThat(scan.refusals()).as(name).isEmpty();
        assertThat(scan.imports()).as(name).containsExactly(new SourceImport(lines + 1, "./z", ImportKind.IMPORT));
    }

    @Test
    void aLineCommentAtTheEndOfTheFileEndsTheFile() {
        ImportScanner.Scan scan = ts("import a from './a'; // the end");

        assertThat(scan.refusals()).isEmpty();
        assertThat(scan.imports()).extracting(SourceImport::specifier).containsExactly("./a");
    }

    @Test
    void importMetaAloneAndTypeAsAnImportEqualsBindingAreRefused() {
        assertThat(ts("const meta = import.meta;\n").refusals()).extracting(Refusal::reason)
                .containsExactly(ImportScanner.IMPORT_META);
        assertThat(ts("import type = require('x');\n").refusals()).extracting(Refusal::reason)
                .containsExactly(ImportScanner.IMPORT_EQUALS, ImportScanner.REQUIRE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"import { a; } from './a';", "import { a (b) } from './a';", "import { a { } } from './a';",
        "import { a", "import * as from './a';"})
    void bindingsThatDoNotCloseOrNameNothingAreRefused(String text) {
        assertThat(ts(text + "\n").refusals()).extracting(Refusal::reason).contains(ImportScanner.IMPORT_CLAUSE);
    }

    @Test
    void reExportsUnderAStringNameAndUnclosedLocalExports() {
        assertThat(ts("export * as \"odd name\" from './a';\n").imports())
                .containsExactly(new SourceImport(1, "./a", ImportKind.RE_EXPORT));
        assertThat(ts("export * as;\n").refusals()).extracting(Refusal::reason)
                .containsExactly(ImportScanner.EXPORT_STAR);
        ImportScanner.Scan unclosed = ts("export { a\n");
        assertThat(unclosed.imports()).isEmpty();
        assertThat(unclosed.refusals()).isEmpty();
    }

    @Test
    void anImportBeforeTheLineWhereTheLexerStoppedIsKeptAndACutOffStatementIsNotRefusedTwice() {
        ImportScanner.Scan complete = ts("import a from './a';\nconst s = 'open\n");
        assertThat(complete.imports()).extracting(SourceImport::specifier).containsExactly("./a");
        assertThat(complete.refusals()).extracting(Refusal::line, Refusal::reason)
                .containsExactly(tuple(2, "unterminated string literal"));

        ImportScanner.Scan cut = ts("import { a } from 'open\n");
        assertThat(cut.imports()).isEmpty();
        assertThat(cut.refusals()).extracting(Refusal::reason).containsExactly("unterminated string literal");
    }

    @Test
    void anUnterminatedSubstitutionAndJsxAtTheEndOfTheFileAreRefused() {
        assertThat(ts("const t = `a ${b\n").refusals()).extracting(Refusal::reason)
                .containsExactly("unterminated template literal");
        assertThat(tsx("const e = <").refusals()).extracting(Refusal::reason).containsExactly("JSX: unexpected 'the "
                + "end of the file' in a tag (a generic arrow function or generic function type starting with '<' "
                + "is not supported in .tsx files)");
        assertThat(tsx("const e = <div id").refusals()).extracting(Refusal::reason)
                .containsExactly("JSX: unterminated tag <div>");
        assertThat(tsx("const e = <div id=\"x>").refusals()).extracting(Refusal::reason)
                .containsExactly("JSX: unterminated attribute string in the tag <div>");
        assertThat(tsx("const e = <div id={a").refusals()).extracting(Refusal::reason)
                .containsExactly("JSX: unterminated attribute expression in the tag <div>");
        assertThat(tsx("const e = <div {...a").refusals()).extracting(Refusal::reason)
                .containsExactly("JSX: unterminated expression in the tag <div>");
        assertThat(tsx("const e = <div>{a").refusals()).extracting(Refusal::reason)
                .containsExactly("JSX: unterminated expression in <div>");
        assertThat(tsx("const e = <div></div;").refusals()).extracting(Refusal::reason)
                .containsExactly("JSX: expected '>' to end the closing tag </div");
        assertThat(tsx("const e = <div / >;").refusals()).extracting(Refusal::reason).containsExactly("JSX: unexpected "
                + "'/' in a tag (a generic arrow function or generic function type starting with '<' is not "
                + "supported in .tsx files)");
    }

    @Test
    void theFirstLineSeparatorNamesTheLine() {
        assertThat(ts("a;\nb;\n\u2029c;\u2028\n").refusals()).extracting(Refusal::line, Refusal::reason)
                .containsExactly(tuple(3, "the characters U+2028 and U+2029 are not supported"));
        assertThat(ts("a;\n\u2028b;\u2029\n").refusals()).extracting(Refusal::line).containsExactly(2);
    }

    @Test
    void aCssImportRuleAfterAMultiLineCommentKeepsItsLine() {
        assertThat(ImportScanner.scanStyle("/* one\ntwo */\n@import 'x.css';\n")).extracting(Refusal::line)
                .containsExactly(3);
        assertThat(ImportScanner.scanStyle("@import 'a.css';\n@import 'b.css';\n")).extracting(Refusal::line)
                .containsExactly(1, 2);
    }

    @Test
    void theScanReportsImportsInSourceOrder() {
        List<SourceImport> imports = ts("export * from './z';\nimport a from './a';\n").imports();

        assertThat(imports).extracting(SourceImport::specifier).containsExactly("./z", "./a");
    }
}
