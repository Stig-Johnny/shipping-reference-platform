package io.github.stigjohnny.c4contractkit.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.c4contractkit.api.ComponentResult;
import io.github.stigjohnny.c4contractkit.api.ComponentStatus;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.ConformanceAssertions;
import io.github.stigjohnny.c4contractkit.api.EdgeResult;
import io.github.stigjohnny.c4contractkit.api.EdgeStatus;
import io.github.stigjohnny.c4contractkit.api.Evidence;
import io.github.stigjohnny.c4contractkit.api.Mode;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.c4contractkit.api.Violation;
import io.github.stigjohnny.c4contractkit.api.ViolationType;
import io.github.stigjohnny.c4contractkit.rules.Comparison;
import io.github.stigjohnny.c4contractkit.rules.ComponentSummary;
import io.github.stigjohnny.c4contractkit.rules.EdgeMode;
import io.github.stigjohnny.c4contractkit.rules.EdgeOutcome;
import io.github.stigjohnny.c4contractkit.rules.EdgeVerdict;
import io.github.stigjohnny.c4contractkit.rules.EvidenceItem;
import io.github.stigjohnny.c4contractkit.rules.Finding;
import io.github.stigjohnny.c4contractkit.rules.FindingType;
import io.github.stigjohnny.c4contractkit.testing.FixtureCompiler;
import io.github.stigjohnny.c4contractkit.testing.FixtureSource;
import io.github.stigjohnny.c4contractkit.testing.ShopFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Identical inputs give byte-identical reports, whatever the order of the class roots or of the model's elements:
 * UTF-8, LF, two-space indentation, no timestamps, a fixed order everywhere.
 */
class ReportDeterminismTest {

    private static final Evidence CLOSE = new Evidence("fx.shop.orders.Order", "close()", "fx.shop.audit.Log",
            "write(java.lang.String)", "method call", "Order.java", 12);

    /** A failing report with every part filled, given in report order. */
    private static final Report FAILING = new Report(Report.SCHEMA, "architecture/workspace.dsl",
            List.of(new ComponentResult("audit", "Audit", ComponentStatus.BUILT, List.of("fx.shop.audit"), 1),
                    new ComponentResult("orders", "Orders", ComponentStatus.BUILT,
                            List.of("fx.shop.orders", "fx.shop.ordering"), 2),
                    new ComponentResult("returns", "Returns", ComponentStatus.PLANNED, List.of("fx.shop.returns"), 0)),
            List.of(new EdgeResult("audit", "orders", Mode.REQUIRED, EdgeStatus.ABSENCE, List.of()),
                    new EdgeResult("orders", "audit", Mode.UNDECLARED, EdgeStatus.DIVERGENCE, List.of(CLOSE)),
                    new EdgeResult("returns", "orders", Mode.PERMITTED, EdgeStatus.NOT_EVALUATED, List.of())),
            List.of(new Violation(ViolationType.ABSENCE, List.of("audit", "orders"),
                            "The model requires audit -> orders, but no class of audit references a class of orders.",
                            List.of()),
                    new Violation(ViolationType.DIVERGENCE, List.of("orders", "audit"),
                            "orders depends on audit, but the model declares no relationship orders -> audit.",
                            List.of(CLOSE))));

    @TempDir
    Path dir = Path.of("unset");

    @Test
    void theJsonReportHasAFixedShape() throws IOException {
        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(FAILING, dir))
                .isInstanceOf(AssertionError.class);

        assertThat(Files.readString(dir.resolve("report.json"), StandardCharsets.UTF_8)).isEqualTo("""
                {
                  "schema": "c4-contract-kit/report/v1",
                  "modelPath": "architecture/workspace.dsl",
                  "passed": false,
                  "components": [
                    {
                      "id": "audit",
                      "name": "Audit",
                      "status": "BUILT",
                      "packages": [
                        "fx.shop.audit"
                      ],
                      "classCount": 1
                    },
                    {
                      "id": "orders",
                      "name": "Orders",
                      "status": "BUILT",
                      "packages": [
                        "fx.shop.ordering",
                        "fx.shop.orders"
                      ],
                      "classCount": 2
                    },
                    {
                      "id": "returns",
                      "name": "Returns",
                      "status": "PLANNED",
                      "packages": [
                        "fx.shop.returns"
                      ],
                      "classCount": 0
                    }
                  ],
                  "edges": [
                    {
                      "source": "audit",
                      "target": "orders",
                      "mode": "REQUIRED",
                      "status": "ABSENCE",
                      "evidence": []
                    },
                    {
                      "source": "orders",
                      "target": "audit",
                      "mode": "UNDECLARED",
                      "status": "DIVERGENCE",
                      "evidence": [
                        {
                          "originClass": "fx.shop.orders.Order",
                          "originMember": "close()",
                          "targetClass": "fx.shop.audit.Log",
                          "targetMember": "write(java.lang.String)",
                          "kind": "method call",
                          "sourceFile": "Order.java",
                          "line": 12
                        }
                      ]
                    },
                    {
                      "source": "returns",
                      "target": "orders",
                      "mode": "PERMITTED",
                      "status": "NOT_EVALUATED",
                      "evidence": []
                    }
                  ],
                  "violations": [
                    {
                      "type": "ABSENCE",
                      "identifiers": [
                        "audit",
                        "orders"
                      ],
                      "message": "The model requires audit -> orders, but no class of audit references a class of orders.",
                      "evidence": []
                    },
                    {
                      "type": "DIVERGENCE",
                      "identifiers": [
                        "orders",
                        "audit"
                      ],
                      "message": "orders depends on audit, but the model declares no relationship orders -> audit.",
                      "evidence": [
                        {
                          "originClass": "fx.shop.orders.Order",
                          "originMember": "close()",
                          "targetClass": "fx.shop.audit.Log",
                          "targetMember": "write(java.lang.String)",
                          "kind": "method call",
                          "sourceFile": "Order.java",
                          "line": 12
                        }
                      ]
                    }
                  ]
                }
                """);
    }

    @Test
    void theMarkdownReportGivesTheVerdictFirstThenViolationsThenEveryEdge() throws IOException {
        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(FAILING, dir))
                .isInstanceOf(AssertionError.class);

        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8)).isEqualTo("""
                # C4 contract report

                **Verdict: FAILED** with 2 violations. Model: `architecture/workspace.dsl`. \
                Report format: `c4-contract-kit/report/v1`.

                ## Violations

                ### ABSENCE: audit -> orders

                The model requires audit -> orders, but no class of audit references a class of orders.

                ### DIVERGENCE: orders -> audit

                orders depends on audit, but the model declares no relationship orders -> audit.

                | Origin class | Origin member | Target class | Target member | Kind | Source |
                |---|---|---|---|---|---|
                | `fx.shop.orders.Order` | `close()` | `fx.shop.audit.Log` | `write(java.lang.String)` | method call \
                | `Order.java:12` |

                ## Edges

                | Source | Target | Mode | Status | References |
                |---|---|---|---|---|
                | audit | orders | REQUIRED | absence | 0 |
                | orders | audit | UNDECLARED | divergence | 1 |
                | returns | orders | PERMITTED | not evaluated | 0 |

                ## Components

                | Component | Status | Classes | Packages |
                |---|---|---|---|
                | audit | built | 1 | `fx.shop.audit` |
                | orders | built | 2 | `fx.shop.ordering`, `fx.shop.orders` |
                | returns | planned | 0 | `fx.shop.returns` |
                """);
    }

    @Test
    void aPassingReportSaysSoAndListsNoViolation() throws IOException {
        Report passing = new Report(Report.SCHEMA, "self.dsl",
                List.of(new ComponentResult("api", "api", ComponentStatus.BUILT, List.of("fx.api"), 1)),
                List.of(new EdgeResult("api", "model", Mode.REQUIRED, EdgeStatus.CONVERGENCE, List.of(new Evidence(
                        "fx.api.Check", "", "fx.model.Model", "", "field type", "", 0)))),
                List.of());

        ConformanceAssertions.assertConforms(passing, dir);

        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8)).isEqualTo("""
                # C4 contract report

                **Verdict: PASSED** with no violations. Model: `self.dsl`. Report format: `c4-contract-kit/report/v1`.

                ## Violations

                None.

                ## Edges

                | Source | Target | Mode | Status | References |
                |---|---|---|---|---|
                | api | model | REQUIRED | convergence | 1 |

                ## Components

                | Component | Status | Classes | Packages |
                |---|---|---|---|
                | api | built | 1 | `fx.api` |
                """);
        assertThat(Files.readString(dir.resolve("report.json"), StandardCharsets.UTF_8))
                .contains("  \"passed\": true,\n")
                .contains("\"violations\": []\n}\n");
    }

    @Test
    void aReportWithOneViolationAndNoEdgesOrComponents() throws IOException {
        Report report = new Report(Report.SCHEMA, "m.dsl", List.of(), List.of(), List.of(new Violation(
                ViolationType.UNMAPPED_CLASS, List.of("fx.Stray"), "fx.Stray is unmapped.", List.of(new Evidence(
                        "fx.Stray", "", "fx.Other", "", "extends", "", 0)))));

        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(report, dir))
                .isInstanceOf(AssertionError.class);

        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8)).isEqualTo("""
                # C4 contract report

                **Verdict: FAILED** with 1 violation. Model: `m.dsl`. Report format: `c4-contract-kit/report/v1`.

                ## Violations

                ### UNMAPPED_CLASS: fx.Stray

                fx.Stray is unmapped.

                | Origin class | Origin member | Target class | Target member | Kind | Source |
                |---|---|---|---|---|---|
                | `fx.Stray` | (class) | `fx.Other` | (type) | extends | unknown |

                ## Edges

                None.

                ## Components

                None.
                """);
        assertThat(Files.readString(dir.resolve("report.json"), StandardCharsets.UTF_8))
                .contains("  \"components\": [],\n  \"edges\": [],\n");
    }

    @Test
    void aSourceWithoutALineShowsTheFileOnly() throws IOException {
        Report report = new Report(Report.SCHEMA, "m.dsl", List.of(), List.of(), List.of(new Violation(
                ViolationType.ISOLATION_BREACH, List.of("fx.Main", "orders"), "The isolated class fx.Main depends on "
                + "orders.", List.of(new Evidence("fx.Main", "orders", "fx.orders.Order", "", "field type",
                "Main.java", 0)))));

        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(report, dir))
                .isInstanceOf(AssertionError.class);

        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8))
                .contains("### ISOLATION_BREACH: fx.Main -> orders\n")
                .contains("| `fx.Main` | `orders` | `fx.orders.Order` | (type) | field type | `Main.java` |\n");
    }

    @Test
    void identifiersWithoutADirectionAreListed() throws IOException {
        Report report = new Report(Report.SCHEMA, "m.dsl", List.of(), List.of(), List.of(new Violation(
                ViolationType.PLANNED_COMPONENT_HAS_CODE, List.of("audit", "fx.audit.Log"), "audit is planned.",
                List.of())));

        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(report, dir))
                .isInstanceOf(AssertionError.class);

        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8))
                .contains("### PLANNED_COMPONENT_HAS_CODE: audit, fx.audit.Log\n");
    }

    @Test
    void textThatWouldBreakJsonOrMarkdownIsEscaped() throws IOException {
        String awkward = "a \"quote\", a \\ backslash, a | pipe, `ticks`, a\nnew line, a\ttab, \u0001, \b, \f, \r, "
                + "é, €, 😀 and a lone \ud800 surrogate, a lone \udc00 one, a last \ud800";
        Report report = new Report(Report.SCHEMA, "m.dsl", List.of(), List.of(), List.of(new Violation(
                ViolationType.UNMAPPED_CLASS, List.of("fx.Odd"), awkward, List.of(new Evidence("fx.Odd", "",
                "fx.Other", "", "extends", "Odd`File.java", 3)))));

        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(report, dir))
                .isInstanceOf(AssertionError.class);

        assertThat(Files.readString(dir.resolve("report.json"), StandardCharsets.UTF_8)).contains("\"message\": "
                + "\"a \\\"quote\\\", a \\\\ backslash, a | pipe, `ticks`, a\\nnew line, a\\ttab, \\u0001, \\b, \\f, "
                + "\\r, é, €, 😀 and a lone \\ud800 surrogate, a lone \\udc00 one, a last \\ud800\",");
        assertThat(Files.readString(dir.resolve("report.md"), StandardCharsets.UTF_8))
                .contains("a \"quote\", a \\ backslash, a \\| pipe, `ticks`, a new line")
                .contains("| `` Odd`File.java:3 `` |");
    }

    @Test
    void aComparisonHoldsItsPartsInReportOrderWhateverOrderTheyArriveIn() {
        EvidenceItem first = new EvidenceItem("fx.a.A", "a()", "fx.b.B", "", "return type", "A.java", 0);
        EvidenceItem second = new EvidenceItem("fx.a.A", "a()", "fx.b.B", "b()", "method call", "A.java", 7);
        EvidenceItem third = new EvidenceItem("fx.a.A", "a()", "fx.b.B", "b()", "method call", "A.java", 9);
        ComponentSummary a = new ComponentSummary("a", "A", io.github.stigjohnny.c4contractkit.model.ComponentStatus.BUILT,
                List.of("fx.a", "fx.a2"), 1);
        ComponentSummary b = new ComponentSummary("b", "B", io.github.stigjohnny.c4contractkit.model.ComponentStatus.BUILT,
                List.of("fx.b"), 1);
        EdgeOutcome ab = new EdgeOutcome("a", "b", EdgeMode.UNDECLARED, EdgeVerdict.DIVERGENCE,
                List.of(first, second, third));
        EdgeOutcome ac = new EdgeOutcome("a", "c", EdgeMode.REQUIRED, EdgeVerdict.ABSENCE, List.of());
        EdgeOutcome ba = new EdgeOutcome("b", "a", EdgeMode.PERMITTED, EdgeVerdict.NO_STATIC_EVIDENCE, List.of());
        Finding unmappedA = new Finding(FindingType.UNMAPPED_CLASS, List.of("fx.A"), "A", List.of());
        Finding unmappedB = new Finding(FindingType.UNMAPPED_CLASS, List.of("fx.B"), "B", List.of());
        Finding divergence = new Finding(FindingType.DIVERGENCE, List.of("a", "b"), "D", List.of(first, second, third));

        Comparison ordered = new Comparison(List.of(a, b), List.of(ab, ac, ba),
                List.of(divergence, unmappedA, unmappedB));
        Comparison shuffled = new Comparison(List.of(b, a),
                List.of(ba, ac, new EdgeOutcome("a", "b", EdgeMode.UNDECLARED, EdgeVerdict.DIVERGENCE,
                        List.of(third, first, second, first))),
                List.of(unmappedB, new Finding(FindingType.DIVERGENCE, List.of("a", "b"), "D",
                        List.of(third, second, first)), unmappedA));

        assertThat(shuffled).isEqualTo(ordered);
        assertThat(new ComponentSummary("a", "A", io.github.stigjohnny.c4contractkit.model.ComponentStatus.BUILT,
                List.of("fx.a2", "fx.a"), 1)).isEqualTo(a);
        assertThat(JsonReport.render(Report.SCHEMA, "m.dsl", shuffled))
                .isEqualTo(JsonReport.render(Report.SCHEMA, "m.dsl", ordered));
        assertThat(MarkdownReport.render(Report.SCHEMA, "m.dsl", shuffled))
                .isEqualTo(MarkdownReport.render(Report.SCHEMA, "m.dsl", ordered));
    }

    @Test
    void shuffledModelElementsAndClassRootsGiveIdenticalBytes() throws IOException {
        Path classes = FixtureCompiler.compile(ShopFixture.classesWith(
                new FixtureSource("fx/shop/stock/Shelf.java", """
                        package fx.shop.stock;

                        public class Shelf {
                            fx.shop.orders.Order order;

                            Object place() {
                                return fx.shop.orders.Order.create();
                            }
                        }
                        """),
                new FixtureSource("fx/shop/misc/Stray.java", "package fx.shop.misc;\n\npublic class Stray {\n}\n"),
                new FixtureSource("fx/shop/misc/Lost.java", "package fx.shop.misc;\n\npublic class Lost {\n}\n")));
        Path ordersAndBilling = copyClasses(classes, dir.resolve("roots/a"), "fx/shop/orders", "fx/shop/billing");
        Path everythingElse = copyClasses(classes, dir.resolve("roots/b"), "fx/shop/stock", "fx/shop/misc",
                "fx/shop/Main.class");

        Path model = ShopFixture.writeModel(dir, "");
        Report forward = Conformance.check(model, List.of(ordersAndBilling, everythingElse));
        byte[][] forwardBytes = writeReports(forward, "forward");

        ShopFixture.writeDsl(dir, shuffled(ShopFixture.MODEL));
        Report backward = Conformance.check(model, List.of(everythingElse, ordersAndBilling));
        byte[][] backwardBytes = writeReports(backward, "backward");

        assertThat(forward.violations()).hasSize(3);
        assertThat(backward).isEqualTo(forward);
        assertThat(backwardBytes[0]).isEqualTo(forwardBytes[0]);
        assertThat(backwardBytes[1]).isEqualTo(forwardBytes[1]);
        assertThat(new String(forwardBytes[0], StandardCharsets.UTF_8)).doesNotContain("\r").endsWith("}\n");
        assertThat(new String(forwardBytes[1], StandardCharsets.UTF_8)).doesNotContain("\r").endsWith("|\n");
    }

    /** The shop model with its components, its relationships and the properties inside each block reversed. */
    private static String shuffled(String model) {
        String reordered = model
                .replace("\"contract.root\" \"fx.shop\"\n                    \"contract.isolated\" \"fx.shop.Main\"",
                        "\"contract.isolated\" \"fx.shop.Main\"\n                    \"contract.root\" \"fx.shop\"");
        int componentsStart = reordered.indexOf("                orders = component");
        int componentsEnd = reordered.indexOf("            }\n        }\n        orders -> billing");
        List<String> components = splitBlocks(reordered.substring(componentsStart, componentsEnd), "                ",
                " = component");
        int relationshipsStart = reordered.indexOf("        orders -> billing");
        int relationshipsEnd = reordered.indexOf("        // RELATIONSHIPS");
        List<String> relationships = splitBlocks(reordered.substring(relationshipsStart, relationshipsEnd), "        ",
                " -> ");
        String result = reordered.substring(0, componentsStart) + String.join("", components.reversed())
                + reordered.substring(componentsEnd, relationshipsStart) + String.join("", relationships.reversed())
                + reordered.substring(relationshipsEnd);
        assertThat(result).isNotEqualTo(model).hasSameSizeAs(model);
        return result;
    }

    /** Splits lines into blocks that each start with a line beginning with indent and containing marker. */
    private static List<String> splitBlocks(String text, String indent, String marker) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.lines().map(line -> line + "\n").toList()) {
            if (line.startsWith(indent) && !line.startsWith(indent + " ") && line.contains(marker)
                    && !current.isEmpty()) {
                blocks.add(current.toString());
                current.setLength(0);
            }
            current.append(line);
        }
        blocks.add(current.toString());
        return blocks;
    }

    private byte[][] writeReports(Report report, String name) throws IOException {
        Path reports = dir.resolve(name);
        assertThatThrownBy(() -> ConformanceAssertions.assertConforms(report, reports))
                .isInstanceOf(AssertionError.class);
        return new byte[][] {Files.readAllBytes(reports.resolve("report.json")),
                Files.readAllBytes(reports.resolve("report.md"))};
    }

    /** Copies the class files under the given paths of a class root into a new class root. */
    private static Path copyClasses(Path root, Path target, String... paths) throws IOException {
        for (String path : paths) {
            try (Stream<Path> files = Files.walk(root.resolve(path))) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Path copy = target.resolve(root.relativize(file).toString());
                    Files.createDirectories(copy.getParent());
                    Files.copy(file, copy);
                }
            }
        }
        return target;
    }
}
