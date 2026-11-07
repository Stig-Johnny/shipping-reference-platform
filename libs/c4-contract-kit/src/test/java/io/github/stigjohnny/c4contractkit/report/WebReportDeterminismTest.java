package io.github.stigjohnny.c4contractkit.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.c4contractkit.api.ComponentStatus;
import io.github.stigjohnny.c4contractkit.api.EdgeStatus;
import io.github.stigjohnny.c4contractkit.api.ImportEvidence;
import io.github.stigjohnny.c4contractkit.api.Mode;
import io.github.stigjohnny.c4contractkit.api.WebComponentResult;
import io.github.stigjohnny.c4contractkit.api.WebConformance;
import io.github.stigjohnny.c4contractkit.api.WebEdgeResult;
import io.github.stigjohnny.c4contractkit.api.WebReport;
import io.github.stigjohnny.c4contractkit.api.WebViolation;
import io.github.stigjohnny.c4contractkit.api.WebViolationType;
import io.github.stigjohnny.c4contractkit.testing.WebFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The web import check's reports have a fixed shape and identical inputs give byte-identical reports, whatever the
 * order of the model's elements: UTF-8, LF, two-space indentation, no timestamps, file and line in every piece of
 * evidence.
 */
class WebReportDeterminismTest {

    private static final ImportEvidence BYPASS = new ImportEvidence("web/src/app/Shell.tsx", 3,
            "../features/cart/CartPage", "import", "web/src/features/cart/CartPage.tsx");
    private static final ImportEvidence REFUSED = new ImportEvidence("web/src/ui/Lazy.ts", 7, "", "refused", "");

    /** A failing report with every part filled, given in report order. */
    private static final WebReport FAILING = new WebReport(WebReport.SCHEMA, "architecture/web.dsl", "web/src",
            List.of(new WebComponentResult("cart", "Cart", ComponentStatus.BUILT, List.of("features/cart"),
                            Optional.of("features/cart/index.ts"), List.of("react"), 2),
                    new WebComponentResult("shell", "Shell", ComponentStatus.BUILT, List.of("app", "boot"),
                            Optional.empty(), List.of(), 1),
                    new WebComponentResult("widgets", "Widgets", ComponentStatus.PLANNED, List.of("ui"),
                            Optional.empty(), List.of(), 0)),
            List.of(new WebEdgeResult("cart", "widgets", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED, List.of()),
                    new WebEdgeResult("shell", "cart", Mode.REQUIRED, EdgeStatus.CONVERGENCE, List.of(BYPASS))),
            List.of(new WebViolation(WebViolationType.ENTRY_POINT_BYPASSED, List.of("shell", "cart"),
                            "shell imports cart other than through its web.entry web/src/features/cart/index.ts.",
                            List.of(BYPASS)),
                    new WebViolation(WebViolationType.UNSUPPORTED_SYNTAX, List.of("web/src/ui/Lazy.ts"),
                            "web/src/ui/Lazy.ts:7: require is not supported: modules are loaded with import "
                                    + "declarations only.", List.of(REFUSED))));

    @TempDir
    Path dir;

    @Test
    void theJsonReportHasAFixedShape() throws IOException {
        assertThatThrownBy(() -> WebConformance.assertConforms(FAILING, dir)).isInstanceOf(AssertionError.class)
                .hasMessage("The web sources do not conform to the C4 model architecture/web.dsl: 2 violations. "
                        + "Reports: " + dir.resolve("report.md") + " and report.json."
                        + "\n  - ENTRY_POINT_BYPASSED shell, cart: shell imports cart other than through its web.entry "
                        + "web/src/features/cart/index.ts."
                        + "\n  - UNSUPPORTED_SYNTAX web/src/ui/Lazy.ts: web/src/ui/Lazy.ts:7: require is not "
                        + "supported: modules are loaded with import declarations only.");

        assertThat(Files.readString(dir.resolve("report.json"), StandardCharsets.UTF_8)).isEqualTo("""
                {
                  "schema": "c4-contract-kit/web-report/v1",
                  "modelPath": "architecture/web.dsl",
                  "sourceRoot": "web/src",
                  "passed": false,
                  "components": [
                    {
                      "id": "cart",
                      "name": "Cart",
                      "status": "BUILT",
                      "paths": [
                        "features/cart"
                      ],
                      "entry": "features/cart/index.ts",
                      "packages": [
                        "react"
                      ],
                      "fileCount": 2
                    },
                    {
                      "id": "shell",
                      "name": "Shell",
                      "status": "BUILT",
                      "paths": [
                        "app",
                        "boot"
                      ],
                      "entry": "",
                      "packages": [],
                      "fileCount": 1
                    },
                    {
                      "id": "widgets",
                      "name": "Widgets",
                      "status": "PLANNED",
                      "paths": [
                        "ui"
                      ],
                      "entry": "",
                      "packages": [],
                      "fileCount": 0
                    }
                  ],
                  "edges": [
                    {
                      "source": "cart",
                      "target": "widgets",
                      "mode": "PERMITTED",
                      "status": "NOT_EVALUATED",
                      "evidence": []
                    },
                    {
                      "source": "shell",
                      "target": "cart",
                      "mode": "REQUIRED",
                      "status": "CONVERGENCE",
                      "evidence": [
                        {
                          "file": "web/src/app/Shell.tsx",
                          "line": 3,
                          "specifier": "../features/cart/CartPage",
                          "kind": "import",
                          "target": "web/src/features/cart/CartPage.tsx"
                        }
                      ]
                    }
                  ],
                  "violations": [
                    {
                      "type": "ENTRY_POINT_BYPASSED",
                      "identifiers": [
                        "shell",
                        "cart"
                      ],
                      "message": "shell imports cart other than through its web.entry web/src/features/cart/index.ts.",
                      "evidence": [
                        {
                          "file": "web/src/app/Shell.tsx",
                          "line": 3,
                          "specifier": "../features/cart/CartPage",
                          "kind": "import",
                          "target": "web/src/features/cart/CartPage.tsx"
                        }
                      ]
                    },
                    {
                      "type": "UNSUPPORTED_SYNTAX",
                      "identifiers": [
                        "web/src/ui/Lazy.ts"
                      ],
                      "message": "web/src/ui/Lazy.ts:7: require is not supported: modules are loaded with import declarations only.",
                      "evidence": [
                        {
                          "file": "web/src/ui/Lazy.ts",
                          "line": 7,
                          "specifier": "",
                          "kind": "refused",
                          "target": ""
                        }
                      ]
                    }
                  ]
                }
                """);
    }

    @Test
    void theMarkdownReportGivesTheVerdictThenEvidenceWithFileLineAndSpecifier() throws IOException {
        assertThatThrownBy(() -> WebConformance.assertConforms(FAILING, dir)).isInstanceOf(AssertionError.class);

        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8)).isEqualTo("""
                # C4 web import report

                **Verdict: FAILED** with 2 violations. Model: `architecture/web.dsl`. Web root: `web/src`. \
                Report format: `c4-contract-kit/web-report/v1`.

                ## Violations

                ### ENTRY_POINT_BYPASSED: shell -> cart

                shell imports cart other than through its web.entry web/src/features/cart/index.ts.

                | File | Specifier | Kind | Target |
                |---|---|---|---|
                | `web/src/app/Shell.tsx:3` | `../features/cart/CartPage` | import | `web/src/features/cart/CartPage.tsx` |

                ### UNSUPPORTED_SYNTAX: web/src/ui/Lazy.ts

                web/src/ui/Lazy.ts:7: require is not supported: modules are loaded with import declarations only.

                | File | Specifier | Kind | Target |
                |---|---|---|---|
                | `web/src/ui/Lazy.ts:7` | (none) | refused | (none) |

                ## Edges

                | Source | Target | Mode | Status | Imports |
                |---|---|---|---|---|
                | cart | widgets | PERMITTED | not evaluated | 0 |
                | shell | cart | REQUIRED | convergence | 1 |

                ## Components

                | Component | Status | Files | Paths | Entry | Packages |
                |---|---|---|---|---|---|
                | cart | built | 2 | `features/cart` | `features/cart/index.ts` | `react` |
                | shell | built | 1 | `app`, `boot` | (none) | (none) |
                | widgets | planned | 0 | `ui` | (none) | (none) |
                """);
    }

    @Test
    void aPassingReportWithoutEdgesOrComponentsSaysSo() throws IOException {
        WebReport empty = new WebReport(WebReport.SCHEMA, "m.dsl", "src", List.of(), List.of(), List.of());

        WebConformance.assertConforms(empty, dir);

        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8)).isEqualTo("""
                # C4 web import report

                **Verdict: PASSED** with no violations. Model: `m.dsl`. Web root: `src`. Report format: \
                `c4-contract-kit/web-report/v1`.

                ## Violations

                None.

                ## Edges

                None.

                ## Components

                None.
                """);
        assertThat(Files.readString(dir.resolve("report.json"), StandardCharsets.UTF_8))
                .contains("\"passed\": true,\n  \"components\": [],\n  \"edges\": [],\n  \"violations\": []\n}\n");
    }

    @Test
    void aModelInAnotherOrderGivesByteIdenticalReports() throws IOException {
        Map<String, String> files = WebFixture.files();
        files.put("ui/Button.tsx", """
                import { loadCart } from '../api/cart';
                import x from 'left-pad';
                export const Button = () => loadCart() + x;
                """);
        files.put("shared/format.ts", "export const format = 1;\n");
        String reordered = adminFirstWithItsPropertiesReversed(WebFixture.MODEL);
        assertThat(reordered).isNotEqualTo(WebFixture.MODEL).hasSameSizeAs(WebFixture.MODEL);

        Path repository = dir.resolve("repo");
        WebReport first = WebConformance.check(WebFixture.write(repository, WebFixture.MODEL, files), repository);
        WebReport second = WebConformance.check(WebFixture.write(repository, reordered, files), repository);
        assertThatThrownBy(() -> WebConformance.assertConforms(first, dir.resolve("a")))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> WebConformance.assertConforms(second, dir.resolve("b")))
                .isInstanceOf(AssertionError.class);

        assertThat(second).isEqualTo(first);
        for (String report : List.of("report.json", "report.md")) {
            assertThat(Files.readAllBytes(dir.resolve("b").resolve(report)))
                    .isEqualTo(Files.readAllBytes(dir.resolve("a").resolve(report)));
        }
        assertThat(first.violations()).extracting(WebViolation::type).containsExactly(WebViolationType.DIVERGENCE,
                WebViolationType.UNDECLARED_PACKAGE, WebViolationType.UNOWNED_FILE);
    }

    /** The model with the component admin moved before shell and its three properties in reverse order. */
    private static String adminFirstWithItsPropertiesReversed(String model) {
        int admin = model.lastIndexOf('\n', model.indexOf("admin = component")) + 1;
        int end = model.indexOf("}\n", model.indexOf("}\n", admin) + 2) + 2;
        String block = model.substring(admin, end);
        String id = "\"web.id\" \"admin\"";
        String status = "\"web.status\" \"planned\"";
        String reversed = block.replace(id, "ID").replace(status, id).replace("ID", status);
        String without = model.substring(0, admin) + model.substring(end);
        int shell = without.lastIndexOf('\n', without.indexOf("shell = component")) + 1;
        return without.substring(0, shell) + reversed + without.substring(shell);
    }
}
