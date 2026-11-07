package io.github.stigjohnny.c4contractkit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.c4contractkit.testing.WebFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The web import check holds a web root's TypeScript imports to the web container of the C4 model, in both
 * directions, with file, line and specifier evidence. Each rule has a negative fixture: the conformant shop of
 * {@link WebFixture} with one file or one model line changed, which must fail with exactly that violation.
 */
class WebConformanceTest {

    @TempDir
    Path repository;

    private WebReport check(String model, Map<String, String> files) {
        return WebConformance.check(WebFixture.write(repository, model, files), repository);
    }

    private WebReport check(Map<String, String> files) {
        return check(WebFixture.MODEL, files);
    }

    private static Map<String, String> with(String path, String content) {
        Map<String, String> files = WebFixture.files();
        files.put(path, content);
        return files;
    }

    private static List<ImportEvidence> evidence(String file, int line, String specifier, String kind, String target) {
        return List.of(new ImportEvidence(file, line, specifier, kind, target));
    }

    @Test
    void aConformantWebRootPassesAndEveryEdgeHasItsStatusAndEvidence() {
        WebReport report = check(WebFixture.files());

        assertThat(report.violations()).isEmpty();
        assertThat(report.passed()).isTrue();
        assertThat(report.schema()).isEqualTo("c4-contract-kit/web-report/v1");
        assertThat(report.sourceRoot()).isEqualTo("web/src");
        assertThat(report.components()).extracting(WebComponentResult::id, WebComponentResult::status,
                WebComponentResult::fileCount).containsExactly(
                tuple("admin", ComponentStatus.PLANNED, 0),
                tuple("cart", ComponentStatus.BUILT, 3),
                tuple("catalog", ComponentStatus.BUILT, 2),
                tuple("client", ComponentStatus.BUILT, 2),
                tuple("shell", ComponentStatus.BUILT, 2),
                tuple("widgets", ComponentStatus.BUILT, 1));
        assertThat(report.edges()).extracting(WebEdgeResult::source, WebEdgeResult::target, WebEdgeResult::mode,
                WebEdgeResult::status).containsExactly(
                tuple("cart", "client", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("cart", "widgets", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("catalog", "cart", Mode.PERMITTED, EdgeStatus.CONVERGENCE),
                tuple("catalog", "client", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("catalog", "widgets", Mode.PERMITTED, EdgeStatus.NO_STATIC_EVIDENCE),
                tuple("shell", "admin", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED),
                tuple("shell", "cart", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("shell", "catalog", Mode.REQUIRED, EdgeStatus.CONVERGENCE),
                tuple("shell", "widgets", Mode.PERMITTED, EdgeStatus.CONVERGENCE));
        assertThat(report.edges()).filteredOn(edge -> edge.source().equals("catalog") && edge.target().equals("cart"))
                .singleElement().extracting(WebEdgeResult::evidence).isEqualTo(evidence(
                        "web/src/features/catalog/CatalogPage.tsx", 1, "../cart", "import type",
                        "web/src/features/cart/index.ts"));
        assertThat(report.edges()).filteredOn(edge -> edge.source().equals("shell") && edge.target().equals("widgets"))
                .singleElement().extracting(WebEdgeResult::evidence).isEqualTo(evidence(
                        "web/src/app/main.tsx", 3, "../ui/Button", "import", "web/src/ui/Button.tsx"));
    }

    @Test
    void anImportBetweenComponentsWithoutARelationshipIsADivergence() {
        WebReport report = check(with("ui/Button.tsx", """
                import type { ReactNode } from 'react';
                import { loadCart } from '../api/cart';

                export function Button({ children }: { children: ReactNode }) {
                    return <button type="button" data-lines={loadCart().length}>{children}</button>;
                }
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message, WebViolation::evidence).containsExactly(tuple(WebViolationType.DIVERGENCE,
                List.of("widgets", "client"),
                "widgets imports client, but the model declares no relationship widgets -> client.",
                evidence("web/src/ui/Button.tsx", 2, "../api/cart", "import", "web/src/api/cart.ts")));
        assertThat(report.edges()).filteredOn(edge -> edge.status() == EdgeStatus.DIVERGENCE)
                .extracting(WebEdgeResult::source, WebEdgeResult::target, WebEdgeResult::mode)
                .containsExactly(tuple("widgets", "client", Mode.UNDECLARED));
    }

    @Test
    void aRequiredRelationshipWithoutAnyImportIsAnAbsenceOfThatEdge() {
        WebReport report = check(with("features/catalog/CatalogPage.tsx", """
                import type { CartLine } from '../cart';

                export function CatalogPage() {
                    const first: CartLine | undefined = undefined;
                    return <ul>{String(first)}</ul>;
                }
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message, WebViolation::evidence).containsExactly(tuple(WebViolationType.ABSENCE,
                List.of("catalog", "client"), "The model requires catalog -> client, but no file of catalog imports "
                        + "a file of client.", List.of()));
    }

    @Test
    void aFileInNoComponentsPathsIsUnowned() {
        WebReport report = check(with("shared/format.ts", "export const format = (x: number) => String(x);\n"));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message).containsExactly(tuple(WebViolationType.UNOWNED_FILE,
                List.of("web/src/shared/format.ts"), "web/src/shared/format.ts is in no component's web.paths."));
    }

    @Test
    void aFileInAPlannedComponentIsCodeInAPlannedComponent() {
        WebReport report = check(with("features/admin/AdminPage.tsx", "export const AdminPage = () => null;\n"));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message).containsExactly(tuple(WebViolationType.PLANNED_COMPONENT_HAS_CODE,
                List.of("admin", "web/src/features/admin/AdminPage.tsx"),
                "admin is planned, but its web.paths contain web/src/features/admin/AdminPage.tsx."));
    }

    @Test
    void aBuiltComponentWithoutCodeIsEmpty() {
        String model = WebFixture.MODEL.replace("""
                            admin = component "Admin" "" "" "Planned" {""", """
                            reports = component "Reports" {
                                properties {
                                    "web.id" "reports"
                                    "web.paths" "features/reports"
                                    "web.status" "built"
                                }
                            }
                            admin = component "Admin" "" "" "Planned" {""");
        WebReport report = check(model, with("features/reports/README.css", "/* no code */\n"));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message).containsExactly(tuple(WebViolationType.BUILT_COMPONENT_EMPTY,
                List.of("reports"), "reports is built, but its web.paths contain no .ts or .tsx file."));
    }

    @Test
    void anImportOfAComponentOtherThanThroughItsEntryBypassesIt() {
        WebReport report = check(with("app/Shell.tsx", """
                import { CartPage } from '../features/cart/CartPage';
                import { CatalogPage } from '../features/catalog';

                export function Shell() {
                    return <main><CatalogPage /><CartPage /></main>;
                }
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message, WebViolation::evidence).containsExactly(tuple(
                WebViolationType.ENTRY_POINT_BYPASSED, List.of("shell", "cart"),
                "shell imports cart other than through its web.entry web/src/features/cart/index.ts.",
                evidence("web/src/app/Shell.tsx", 1, "../features/cart/CartPage", "import",
                        "web/src/features/cart/CartPage.tsx")));
        assertThat(report.edges()).filteredOn(edge -> edge.target().equals("cart") && edge.source().equals("shell"))
                .extracting(WebEdgeResult::status).containsExactly(EdgeStatus.CONVERGENCE);
    }

    @Test
    void anEntryThatDoesNotExistIsMissing() {
        WebReport report = check(WebFixture.MODEL.replace("\"web.entry\" \"features/catalog/index.ts\"",
                "\"web.entry\" \"features/catalog/main.ts\""), WebFixture.files());

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers).containsExactly(
                tuple(WebViolationType.ENTRY_POINT_BYPASSED, List.of("shell", "catalog")),
                tuple(WebViolationType.ENTRY_POINT_MISSING, List.of("catalog", "web/src/features/catalog/main.ts")));
        assertThat(report.violations()).filteredOn(violation -> violation.type()
                == WebViolationType.ENTRY_POINT_MISSING).extracting(WebViolation::message).containsExactly(
                "catalog is built, but its web.entry web/src/features/catalog/main.ts does not exist.");
    }

    @Test
    void anImportThatMatchesNoFileIsUnresolved() {
        WebReport report = check(with("features/cart/CartLine.ts", """
                import type { Sku } from './Sku';

                export type CartLine = { sku: Sku; quantity: number };
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message, WebViolation::evidence).containsExactly(tuple(
                WebViolationType.UNRESOLVED_IMPORT, List.of("web/src/features/cart/CartLine.ts", "./Sku"),
                "web/src/features/cart/CartLine.ts:1: './Sku' matches no file: tried features/cart/Sku.ts, "
                        + "features/cart/Sku.tsx, features/cart/Sku.d.ts, features/cart/Sku/index.ts, "
                        + "features/cart/Sku/index.tsx, features/cart/Sku/index.d.ts.",
                evidence("web/src/features/cart/CartLine.ts", 1, "./Sku", "import type", "")));
    }

    @Test
    void dotTsSpecifiersAreHeldToTheRulesOfTheFilesTheyName() {
        // tsc's rewriteRelativeImportExtensions has relative imports written with .ts: './QuoteCard.ts'.
        Map<String, String> files = with("app/Shell.tsx", """
                import { CartPage } from '../features/cart/index.ts';
                import { CatalogPage } from '../features/catalog/index.ts';

                export function Shell() {
                    return <main><CatalogPage /><CartPage /></main>;
                }
                """);
        files.put("features/catalog/CatalogPage.tsx", Objects.requireNonNull(files.get("features/catalog/CatalogPage.tsx"))
                .replace("from '../cart';", "from '../cart/CartLine.ts';")
                .replace("from '../../api/items';", "from '../../api/items.ts';"));
        files.put("api/cart.ts", """
                import { client } from '@shop/client';
                import { base } from './base.ts';

                export const loadCart = (): string[] => client.get(base + '/cart');
                """);
        WebReport report = check(files);

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::message, WebViolation::evidence)
                .containsExactly(
                        tuple(WebViolationType.ENTRY_POINT_BYPASSED, "catalog imports cart other than through its "
                                + "web.entry web/src/features/cart/index.ts.", evidence(
                                "web/src/features/catalog/CatalogPage.tsx", 1, "../cart/CartLine.ts", "import type",
                                "web/src/features/cart/CartLine.ts")),
                        tuple(WebViolationType.UNRESOLVED_IMPORT, "web/src/api/cart.ts:2: './base.ts' matches no file: "
                                + "api/base.ts does not exist.", evidence("web/src/api/cart.ts", 2, "./base.ts",
                                "import", "")));
        assertThat(report.edges()).filteredOn(edge -> edge.source().equals("shell") && edge.target().equals("cart"))
                .singleElement().extracting(WebEdgeResult::status, WebEdgeResult::evidence).containsExactly(
                        EdgeStatus.CONVERGENCE, evidence("web/src/app/Shell.tsx", 1, "../features/cart/index.ts",
                                "import", "web/src/features/cart/index.ts"));
    }

    @Test
    void aPackageThatTheComponentDoesNotListIsUndeclared() {
        WebReport report = check(with("ui/Button.tsx", """
                import type { ReactNode } from 'react';
                import { client } from '@shop/client';

                export function Button({ children }: { children: ReactNode }) {
                    return <button type="button" data-base={client.base}>{children}</button>;
                }
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message, WebViolation::evidence).containsExactly(tuple(
                WebViolationType.UNDECLARED_PACKAGE, List.of("widgets", "@shop/client"),
                "widgets imports the package @shop/client, which its web.packages does not list.",
                evidence("web/src/ui/Button.tsx", 2, "@shop/client", "import", "@shop/client")));
    }

    @Test
    void filesThatImportEachOtherFormACycle() {
        Map<String, String> files = with("features/cart/CartLine.ts", """
                import { CartPage } from './CartPage';

                export type CartLine = { sku: string; quantity: number; page: typeof CartPage };
                """);
        files.put("features/cart/CartPage.tsx", Objects.requireNonNull(files.get("features/cart/CartPage.tsx"))
                .replace("import { useState } from 'react';", "import { useState } from 'react';\n"
                        + "import type { CartLine } from './CartLine';"));
        WebReport report = check(files);

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message, WebViolation::evidence).containsExactly(tuple(
                WebViolationType.IMPORT_CYCLE,
                List.of("web/src/features/cart/CartLine.ts", "web/src/features/cart/CartPage.tsx"),
                "These files import each other in a cycle: web/src/features/cart/CartLine.ts, "
                        + "web/src/features/cart/CartPage.tsx.",
                List.of(new ImportEvidence("web/src/features/cart/CartLine.ts", 1, "./CartPage", "import",
                                "web/src/features/cart/CartPage.tsx"),
                        new ImportEvidence("web/src/features/cart/CartPage.tsx", 2, "./CartLine", "import type",
                                "web/src/features/cart/CartLine.ts"))));
    }

    @Test
    void aFileThatImportsItselfIsACycle() {
        WebReport report = check(with("api/cart.ts", """
                import { client } from '@shop/client';
                import * as self from './cart';

                export const loadCart = (): string[] => client.get('/cart' + String(self));
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers)
                .containsExactly(tuple(WebViolationType.IMPORT_CYCLE, List.of("web/src/api/cart.ts")));
    }

    @Test
    void whatTheGrammarRefusesIsUnsupportedSyntaxWithItsLine() {
        WebReport report = check(with("api/items.ts", """
                import { client } from '@shop/client/items';
                import base from '/src/api/base';

                export const loadItems = (): string[] => client.get(require('./path'));
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message, WebViolation::evidence).containsExactly(
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, List.of("web/src/api/items.ts"),
                        "web/src/api/items.ts:2: an absolute module specifier is not supported: write it relative to "
                                + "the importing file.",
                        evidence("web/src/api/items.ts", 2, "/src/api/base", "import", "")),
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, List.of("web/src/api/items.ts"),
                        "web/src/api/items.ts:4: require is not supported: modules are loaded with import "
                                + "declarations only.",
                        evidence("web/src/api/items.ts", 4, "", "refused", "")));
    }

    @Test
    void urlDataAndComputedImportsAreRefusedInBrowserCodeAndInTests() {
        Map<String, String> files = with("api/remote.ts", """
                import a from 'https://cdn.example/a.js';
                import b from 'data:text/javascript,export default 1';
                export const load = (name: string) => import(name);
                """);
        files.put("features/cart/remote.test.ts", """
                import c from 'https://cdn.example/c.js';
                export const later = () => import(`./${'x'}.ts`);
                """);

        WebReport report = check(files);

        String scheme = ": a module specifier with a scheme (such as node:, https: or virtual:) is not supported.";
        String computed = ": import() is supported only with one plain string literal: the module it loads must be "
                + "known without running the code.";
        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::message).containsExactly(
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, "web/src/api/remote.ts:1" + scheme),
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, "web/src/api/remote.ts:2" + scheme),
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, "web/src/api/remote.ts:3" + computed),
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, "web/src/features/cart/remote.test.ts:1" + scheme),
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, "web/src/features/cart/remote.test.ts:2" + computed));
    }

    @Test
    void aFileTypeOutsideTheGrammarIsUnsupported() {
        WebReport report = check(with("ui/legacy.js", "module.exports = require('./x');\n"));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message).containsExactly(tuple(WebViolationType.UNSUPPORTED_FILE,
                List.of("web/src/ui/legacy.js"), "web/src/ui/legacy.js is not a .ts, .tsx, .css, image or font "
                        + "file: the restricted import grammar does not cover it."));
    }

    @Test
    void anUnownedFileOnEitherSideOfAnImportIsUnownedAndNoEdge() {
        Map<String, String> files = with("shared/format.ts", """
                import { Button } from '../ui/Button';
                import pad from 'left-pad';
                export const format = String(Button) + pad;
                """);
        files.put("ui/Button.tsx", """
                import type { ReactNode } from 'react';
                import { format } from '../shared/format';

                export function Button({ children }: { children: ReactNode }) {
                    return <button type="button" title={format}>{children}</button>;
                }
                """);
        WebReport report = check(files);

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers).containsExactly(
                tuple(WebViolationType.IMPORT_CYCLE, List.of("web/src/shared/format.ts", "web/src/ui/Button.tsx")),
                tuple(WebViolationType.UNOWNED_FILE, List.of("web/src/shared/format.ts")));
        assertThat(report.edges()).noneMatch(edge -> edge.status() == EdgeStatus.DIVERGENCE);
    }

    @Test
    void aTypeScriptImportOfAStyleSheetIsUnsupportedSyntax() {
        WebReport report = check(with("app/main.tsx", """
                import { createRoot } from 'react-dom/client';
                import { Shell } from './Shell';
                import { Button } from '../ui/Button';
                import '../ui/theme.css';

                createRoot(document.getElementById('root')!).render(<Shell><Button>Start</Button></Shell>);
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::message).containsExactly(tuple(
                WebViolationType.UNSUPPORTED_SYNTAX, "web/src/app/main.tsx:4: a TypeScript import of a style sheet is "
                        + "not supported: link style sheets from index.html."));
    }

    private WebReport check(String model, Map<String, String> files, @Nullable String packageJson,
                            @Nullable String allowlist) {
        return WebConformance.check(WebFixture.write(repository, model, files, packageJson, allowlist), repository);
    }

    @Test
    void aProductionImportOfATestFileIsAViolationEvenInsideItsComponent() {
        // Codex's review of the rules: a test file was dependency evidence while nothing in it was ever read.
        Map<String, String> files = with("features/cart/bridge.test.ts", "export const helper = 1;\n");
        files.put("features/cart/CartPage.tsx", Objects.requireNonNull(files.get("features/cart/CartPage.tsx"))
                .replace("import { useState } from 'react';", "import { useState } from 'react';\n"
                        + "import { helper } from './bridge.test';"));

        WebReport report = check(files);

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message).containsExactly(tuple(WebViolationType.TEST_FILE_IMPORTED,
                List.of("web/src/features/cart/CartPage.tsx", "web/src/features/cart/bridge.test.ts"),
                "web/src/features/cart/CartPage.tsx imports the test web/src/features/cart/bridge.test.ts: production "
                        + "code may not import tests, whose imports are held to the rules for tests and are never "
                        + "evidence."));
    }

    @Test
    void aTestFileCannotBridgeARequiredRelationship() {
        // catalog -> client is REQUIRED; catalog's only import of client goes through a test file of client.
        Map<String, String> files = with("api/bridge.test.ts", "export { loadItems } from './items';\n");
        files.put("features/catalog/CatalogPage.tsx", Objects.requireNonNull(files.get(
                "features/catalog/CatalogPage.tsx")).replace("'../../api/items'", "'../../api/bridge.test'"));

        WebReport report = check(files);

        assertThat(report.passed()).isFalse();
        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers).containsExactly(
                tuple(WebViolationType.ABSENCE, List.of("catalog", "client")),
                tuple(WebViolationType.TEST_FILE_IMPORTED, List.of("web/src/features/catalog/CatalogPage.tsx",
                        "web/src/api/bridge.test.ts")));
    }

    @Test
    void aTestIsHeldToTheRelationshipsOfItsComponent() {
        WebReport report = check(with("features/cart/CartPage.test.tsx", """
                import { CatalogPage } from '../catalog';
                export const page = CatalogPage;
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message, WebViolation::evidence).containsExactly(tuple(WebViolationType.DIVERGENCE,
                List.of("cart", "catalog"), "A test of cart imports catalog, but the model declares no relationship "
                        + "cart -> catalog.", evidence("web/src/features/cart/CartPage.test.tsx", 1,
                        "../catalog", "import", "web/src/features/catalog/index.ts")));
        assertThat(report.edges()).noneMatch(edge -> edge.status() == EdgeStatus.DIVERGENCE);
    }

    @Test
    void aTestNeverSatisfiesARequiredRelationship() {
        Map<String, String> files = with("features/cart/CartPage.tsx", """
                import { useState } from 'react';
                import { Button } from '../../ui/Button';

                export function CartPage() {
                    const [lines] = useState<string[]>([]);
                    return <section><p>{lines.length} items.</p><Button>Check out</Button></section>;
                }
                """);
        files.put("features/cart/CartPage.test.tsx", """
                import { loadCart } from '../../api/cart';
                export const lines = loadCart();
                """);

        WebReport report = check(files);

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers)
                .containsExactly(tuple(WebViolationType.ABSENCE, List.of("cart", "client")));
    }

    @Test
    void aTestThatImportsPastAnEntryBypassesItAndWhatATestRefusesFails() {
        WebReport report = check(with("app/Shell.test.tsx", """
                import { CartPage } from '../features/cart/CartPage';
                const lazy = require('./anything');
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers).containsExactly(
                tuple(WebViolationType.ENTRY_POINT_BYPASSED, List.of("shell", "cart")),
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, List.of("web/src/app/Shell.test.tsx")));
    }

    @Test
    void aLoneCarriageReturnOrANonNullAssertionCannotHideAnImport() {
        // Codex's probes, end to end: either hid the import of the line they are on, and the check passed.
        WebReport carriageReturn = check(with("ui/Button.tsx", """
                // comment\rimport { loadCart } from '../api/cart';
                export const Button = () => null;
                """));
        assertThat(carriageReturn.violations()).extracting(WebViolation::type, WebViolation::message).containsExactly(
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, "web/src/ui/Button.tsx:1: a carriage return that is not "
                        + "followed by a line feed is not supported: TypeScript ends a line, and a // comment, there."));

        WebReport nonNull = check(with("ui/Button.tsx", """
                export const Button = async (value: number) => {
                    const n = value! / (await import('undeclared')).n / 2;
                    return n;
                };
                """));
        assertThat(nonNull.violations()).extracting(WebViolation::type, WebViolation::identifiers).containsExactly(
                tuple(WebViolationType.UNDECLARED_PACKAGE, List.of("widgets", "undeclared")));
    }

    @Test
    void aDeclaredPackageNeedsAnApprovalForTheAppInDependencies() {
        String reason = "security/allowlist.json approves no npm @shop/client 1.0.0 for web in dependencies, and only "
                + "that approval covers code that runs in the browser.";
        String withoutClient = WebFixture.ALLOWLIST.replace(WebFixture.npm("@shop/client", "1.0.0", "dependencies"),
                WebFixture.npm("other", "1.0.0", "dependencies"));
        String devOnly = WebFixture.ALLOWLIST.replace(WebFixture.npm("@shop/client", "1.0.0", "dependencies"),
                WebFixture.npm("@shop/client", "1.0.0", "devDependencies"));
        String rejected = WebFixture.ALLOWLIST.replace(WebFixture.npm("@shop/client", "1.0.0", "dependencies"),
                WebFixture.npm("@shop/client", "1.0.0", "dependencies", "REJECT"));
        String otherProject = WebFixture.ALLOWLIST.replace(WebFixture.npm("@shop/client", "1.0.0", "dependencies"),
                WebFixture.npm("@shop/client", "1.0.0", "dependencies").replace("\"web\"", "\"apps/other\""));

        for (String allowlist : List.of(withoutClient, devOnly, rejected, otherProject)) {
            WebReport report = check(WebFixture.MODEL, WebFixture.files(), WebFixture.PACKAGE_JSON, allowlist);
            assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                    WebViolation::message, WebViolation::evidence).containsExactly(tuple(
                    WebViolationType.UNAPPROVED_IMPORT, List.of("client", "@shop/client"), reason, List.of(
                            new ImportEvidence("web/src/api/cart.ts", 1, "@shop/client", "import", "@shop/client"),
                            new ImportEvidence("web/src/api/items.ts", 1, "@shop/client/items", "import",
                                    "@shop/client"))));
        }
    }

    @Test
    void aPackageWithoutAnExactVersionInPackageJsonOrAnotherVersionIsNotApproved() {
        WebReport missing = check(WebFixture.MODEL, WebFixture.files(),
                WebFixture.PACKAGE_JSON.replace(", \"@shop/client\": \"1.0.0\"", ""), WebFixture.ALLOWLIST);
        assertThat(missing.violations()).extracting(WebViolation::type, WebViolation::message).containsExactly(tuple(
                WebViolationType.UNAPPROVED_IMPORT, "package.json declares no exact version of @shop/client in "
                        + "dependencies, so no reviewed release is known."));

        WebReport other = check(WebFixture.MODEL, WebFixture.files(),
                WebFixture.PACKAGE_JSON.replace("\"@shop/client\": \"1.0.0\"", "\"@shop/client\": \"1.0.1\""),
                WebFixture.ALLOWLIST);
        assertThat(other.violations()).extracting(WebViolation::type, WebViolation::message).containsExactly(tuple(
                WebViolationType.UNAPPROVED_IMPORT, "security/allowlist.json approves no npm @shop/client 1.0.1 for web "
                        + "in dependencies, and only that approval covers code that runs in the browser."));
    }

    @Test
    void aMissingOrUnreadableAllowlistApprovesNothing() {
        WebReport missing = check(WebFixture.MODEL, WebFixture.files(), WebFixture.PACKAGE_JSON, null);
        assertThat(missing.violations()).isNotEmpty().allSatisfy(violation -> {
            assertThat(violation.type()).isEqualTo(WebViolationType.UNAPPROVED_IMPORT);
            assertThat(violation.message()).isEqualTo("the allowlist is unavailable (security/allowlist.json does not "
                    + "exist or is not a regular file), so nothing is approved.");
        });
        assertThat(missing.violations()).extracting(WebViolation::identifiers).contains(List.of("client",
                "@shop/client"), List.of("cart", "@testing-library/react"),
                List.of("web/src/features/cart/CartPage.test.tsx", "node:test"));

        WebReport unreadable = check(WebFixture.MODEL, WebFixture.files(), WebFixture.PACKAGE_JSON, "{ entries: [] }");
        assertThat(unreadable.violations()).isNotEmpty().allSatisfy(violation -> assertThat(violation.message())
                .startsWith("the allowlist is unavailable (security/allowlist.json is not plain JSON"));
    }

    @Test
    void aTestsPackageMustBeListedInTheModel() {
        WebReport report = check(WebFixture.MODEL.replace("                \"web.testPackages\" \"@testing-library/react\"\n",
                ""), WebFixture.files());

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message).containsExactly(tuple(WebViolationType.UNDECLARED_PACKAGE,
                List.of("cart", "@testing-library/react"), "A test of cart imports the package "
                        + "@testing-library/react, which web.testPackages does not list."));
    }

    @Test
    void browserCodeMayImportNoNodeBuiltInInAnyForm() {
        WebReport report = check(with("api/cart.ts", """
                import { client } from '@shop/client';
                import { readFileSync } from 'node:fs';
                import path from 'path';

                export const loadCart = (): string[] => client.get('/cart' + String(readFileSync) + path.sep);
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::identifiers,
                WebViolation::message).containsExactly(
                tuple(WebViolationType.NODE_BUILTIN, List.of("web/src/api/cart.ts", "node:fs"), "web/src/api/cart.ts "
                        + "imports the Node.js built-in node:fs: code that runs in the browser may not import Node.js "
                        + "built-ins."),
                tuple(WebViolationType.NODE_BUILTIN, List.of("web/src/api/cart.ts", "path"), "web/src/api/cart.ts "
                        + "imports the Node.js built-in path: code that runs in the browser may not import Node.js "
                        + "built-ins."));
    }

    @Test
    void aTestImportsOnlyListedBuiltInsWithTheirPrefix() {
        WebReport report = check(with("features/cart/CartPage.test.tsx", """
                import fs from 'fs';
                import { spawn } from 'node:child_process';
                import { test } from 'node:test';
                """));

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::message).containsExactly(
                tuple(WebViolationType.NODE_BUILTIN, "web/src/features/cart/CartPage.test.tsx imports fs without the "
                        + "node: prefix: a test names a Node.js built-in as node:fs, which web.testBuiltins must list."),
                tuple(WebViolationType.NODE_BUILTIN, "web/src/features/cart/CartPage.test.tsx imports the Node.js "
                        + "built-in node:child_process, which web.testBuiltins does not list."));
    }

    @Test
    void aTestsBuiltInsNeedTheApprovedNodeJsThatPackageJsonNames() {
        String withoutRuntime = WebFixture.ALLOWLIST.replace("\"coordinate\": \"nodejs.org/dist node\"",
                "\"coordinate\": \"Node.js\"");
        WebReport notApproved = check(WebFixture.MODEL, WebFixture.files(), WebFixture.PACKAGE_JSON, withoutRuntime);
        assertThat(notApproved.violations()).extracting(WebViolation::type, WebViolation::message).containsOnly(tuple(
                WebViolationType.UNAPPROVED_IMPORT, "security/allowlist.json approves no nodejs.org/dist node 26.13.0 "
                        + "for the web job, the Node.js whose built-in node:assert/strict tests import."), tuple(
                WebViolationType.UNAPPROVED_IMPORT, "security/allowlist.json approves no nodejs.org/dist node 26.13.0 "
                        + "for the web job, the Node.js whose built-in node:test tests import."));

        WebReport noEngine = check(WebFixture.MODEL, WebFixture.files(), WebFixture.PACKAGE_JSON.replace(
                "\"engines\": { \"node\": \"26.13.0\" },", ""), WebFixture.ALLOWLIST);
        assertThat(noEngine.violations()).extracting(WebViolation::message).containsOnly(
                "package.json names no exact engines.node, so the Node.js whose review covers node:assert/strict is "
                        + "not known.", "package.json names no exact engines.node, so the Node.js whose review covers "
                        + "node:test is not known.");
    }

    @Test
    void aResolutionOverrideInTheAppsConfigurationFailsTheCheck() {
        Map<String, String> files = with("/web/tsconfig.json", "{\n  \"compilerOptions\": {\n    \"baseUrl\": \".\"\n  }\n}\n");
        files.put("/web/index.html", "<!doctype html>\n<script type=\"importmap\">{}</script>\n");

        WebReport report = check(files);

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::message).containsExactly(
                tuple(WebViolationType.UNSUPPORTED_CONFIGURATION, "web/index.html:2: an import map is not supported: "
                        + "it changes what a specifier names."),
                tuple(WebViolationType.UNSUPPORTED_CONFIGURATION, "web/tsconfig.json:3: compilerOptions.baseUrl is "
                        + "not supported: it changes what a specifier names or adds an import that no source file "
                        + "shows."));
    }

    @Test
    void testRootsAndPublicRootsAreReadAndHeldToTheirRules() {
        String model = WebFixture.MODEL.replace("                \"web.root\" \"web/src\"\n", """
                                "web.root" "web/src"
                                "web.testRoots" "web/e2e"
                                "web.publicRoots" "web/public"
                """);
        Map<String, String> files = WebFixture.files();
        files.put("/web/e2e/flow.ts", """
                import { test } from 'node:test';
                import { expect } from '@playwright/test';
                import { open } from './helpers/open.ts';
                import { cart } from '../src/features/cart/index.ts';
                """);
        files.put("/web/e2e/helpers/open.ts", "export const open = () => undefined;\n");
        files.put("/web/public/site.css", ":root { --a: 1; }\n@import './more.css';\n");
        files.put("/web/public/app.js", "export {};\n");

        WebReport report = check(model, files);

        assertThat(report.violations()).extracting(WebViolation::type, WebViolation::message).containsExactly(
                tuple(WebViolationType.UNDECLARED_PACKAGE, "A test of web/e2e imports the package @playwright/test, "
                        + "which web.testPackages does not list."),
                tuple(WebViolationType.UNRESOLVED_IMPORT, "web/e2e/flow.ts:4: '../src/features/cart/index.ts' leaves "
                        + "the web root."),
                tuple(WebViolationType.UNSUPPORTED_FILE, "web/public/app.js is not a style sheet, image or font: a "
                        + "public root holds only those, served as they are."),
                tuple(WebViolationType.UNSUPPORTED_SYNTAX, "web/public/site.css:2: CSS @import is not supported: link "
                        + "each style sheet from index.html."));
    }

    @Test
    void aSymbolicLinkOnTheWayToTheWebRootIsAnError() throws IOException {
        Path elsewhere = Files.createDirectories(repository.resolve("elsewhere/src"));
        Files.writeString(elsewhere.resolve("a.ts"), "export const a = 1;\n", StandardCharsets.UTF_8);
        Path model = WebFixture.write(repository, WebFixture.MODEL, Map.of(), null, WebFixture.ALLOWLIST);
        Files.createSymbolicLink(repository.resolve("web"), repository.resolve("elsewhere"));

        assertThatThrownBy(() -> WebConformance.check(model, repository))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The web root web/src lies under a symbolic link, web: the check reads only regular files "
                        + "and directories");
    }

    @Test
    void aMissingWebRootHasNoFilesSoEveryBuiltComponentIsEmpty() {
        WebReport report = WebConformance.check(WebFixture.write(repository, WebFixture.MODEL, Map.of()),
                repository.resolve("elsewhere"));

        assertThat(report.violations()).extracting(WebViolation::type).containsOnly(
                WebViolationType.ABSENCE, WebViolationType.BUILT_COMPONENT_EMPTY, WebViolationType.ENTRY_POINT_MISSING);
        assertThat(report.violations()).filteredOn(violation -> violation.type()
                == WebViolationType.BUILT_COMPONENT_EMPTY).hasSize(5);
    }

    @Test
    void anInvalidModelIsAModelExceptionListingEveryProblem() {
        Path model = WebFixture.write(repository, WebFixture.MODEL.replace("\"web.id\" \"cart\"", "\"web.id\" \"Cart\"")
                .replace("\"web.status\" \"planned\"", "\"web.status\" \"later\""), WebFixture.files());

        assertThatThrownBy(() -> WebConformance.check(model, repository))
                .isInstanceOf(ModelException.class)
                .satisfies(e -> assertThat(((ModelException) e).problems()).containsExactly(
                        "admin: web.status 'later' is neither built nor planned",
                        "cart: web.id 'Cart' does not match [a-z][a-z0-9-]*"));
    }

    @Test
    void assertConformsWritesBothReportsThenFailsListingTheViolations() throws IOException {
        WebReport report = check(with("shared/format.ts", "export const format = 1;\n"));
        Path reports = repository.resolve("reports");

        assertThatThrownBy(() -> WebConformance.assertConforms(report, reports))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("The web sources do not conform to the C4 model")
                .hasMessageContaining("1 violation")
                .hasMessageContaining("UNOWNED_FILE web/src/shared/format.ts: web/src/shared/format.ts is in no "
                        + "component's web.paths.");
        assertThat(Files.readString(reports.resolve("report.md"), StandardCharsets.UTF_8))
                .startsWith("# C4 web import report\n\n**Verdict: FAILED** with 1 violation.");
        assertThat(Files.readString(reports.resolve("report.json"), StandardCharsets.UTF_8))
                .contains("\"type\": \"UNOWNED_FILE\"");
    }

    @Test
    void assertConformsPassesAConformantReportAndStillWritesIt() throws IOException {
        WebReport report = check(WebFixture.files());
        Path reports = repository.resolve("reports");

        WebConformance.assertConforms(report, reports);

        assertThat(Files.readString(reports.resolve("report.md"), StandardCharsets.UTF_8))
                .startsWith("# C4 web import report\n\n**Verdict: PASSED** with no violations.");
    }
}
