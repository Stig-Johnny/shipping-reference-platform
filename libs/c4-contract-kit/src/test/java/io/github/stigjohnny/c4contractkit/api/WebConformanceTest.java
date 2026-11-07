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

    @Test
    void testFilesAreOwnedButTheirImportsAreNotEvaluated() {
        WebReport report = check(with("features/catalog/CatalogPage.test.tsx", """
                import { CartPage } from '../cart/CartPage';
                const lazy = require('anything');
                """));

        assertThat(report.violations()).isEmpty();
        assertThat(report.components()).filteredOn(component -> component.id().equals("catalog"))
                .extracting(WebComponentResult::fileCount).containsExactly(2);
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
