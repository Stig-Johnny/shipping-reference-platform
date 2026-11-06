package io.github.stigjohnny.c4contractkit.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.github.stigjohnny.c4contractkit.api.ComponentResult;
import io.github.stigjohnny.c4contractkit.api.ComponentStatus;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.EdgeResult;
import io.github.stigjohnny.c4contractkit.api.EdgeStatus;
import io.github.stigjohnny.c4contractkit.api.Evidence;
import io.github.stigjohnny.c4contractkit.api.Mode;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.c4contractkit.api.Violation;
import io.github.stigjohnny.c4contractkit.api.ViolationType;
import io.github.stigjohnny.c4contractkit.testing.FixtureCompiler;
import io.github.stigjohnny.c4contractkit.testing.FixtureSource;
import io.github.stigjohnny.c4contractkit.testing.ShopFixture;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A dependency from one component to another that the model does not declare fails the check (a divergence), with
 * the class-level evidence behind it. Only direct references count, of every kind ArchUnit reports.
 */
class ConformanceDivergenceTest {

    @TempDir
    Path dir = Path.of("unset");

    @Test
    void theShopConformsAndEveryEdgeHasItsStatus() {
        Report report = check("");

        assertThat(report.passed()).isTrue();
        assertThat(report.schema()).isEqualTo("c4-contract-kit/report/v1");
        assertThat(report.violations()).isEmpty();
        assertThat(report.components()).containsExactly(
                new ComponentResult("audit", "Audit", ComponentStatus.PLANNED, List.of("fx.shop.audit"), 0),
                new ComponentResult("billing", "Billing", ComponentStatus.BUILT, List.of("fx.shop.billing"), 1),
                new ComponentResult("orders", "Orders", ComponentStatus.BUILT, List.of("fx.shop.orders"), 4),
                new ComponentResult("stock", "Stock", ComponentStatus.BUILT, List.of("fx.shop.stock"), 1));
        assertThat(report.edges()).containsExactly(
                new EdgeResult("audit", "orders", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED, List.of()),
                new EdgeResult("orders", "billing", Mode.REQUIRED, EdgeStatus.CONVERGENCE, List.of(
                        new Evidence("fx.shop.orders.Order", "invoice()", "fx.shop.billing.Invoice", "", "return type",
                                "Order.java", 0),
                        new Evidence("fx.shop.orders.Order", "invoice()", "fx.shop.billing.Invoice", "<init>()",
                                "constructor call", "Order.java", ShopFixture.ORDER.lineOf("L:invoice")))),
                new EdgeResult("orders", "stock", Mode.PERMITTED, EdgeStatus.NO_STATIC_EVIDENCE, List.of()));
    }

    @Test
    void aReferenceWithoutARelationshipIsADivergence() {
        Report report = check("", shelf("Order order;"));

        Evidence evidence = new Evidence("fx.shop.stock.Shelf", "order", "fx.shop.orders.Order", "", "field type",
                "Shelf.java", 0);
        assertThat(report.passed()).isFalse();
        assertThat(report.violations()).containsExactly(new Violation(ViolationType.DIVERGENCE,
                List.of("stock", "orders"),
                "stock depends on orders, but the model declares no relationship stock -> orders.",
                List.of(evidence)));
        assertThat(edge(report, "stock", "orders"))
                .isEqualTo(new EdgeResult("stock", "orders", Mode.UNDECLARED, EdgeStatus.DIVERGENCE, List.of(evidence)));
    }

    static Stream<Arguments> everyKindOfReference() {
        return Stream.of(
                arguments("Order order;", "field type"),
                arguments("void take(Order order) {\n}", "parameter type"),
                arguments("Order give() {\n    return null;\n}", "return type"),
                arguments("void fail() throws OrderFailure {\n}", "throws"),
                arguments("void guard() {\n    try {\n        System.gc();\n    } catch (OrderFailure e) {\n    }\n}",
                        "catch"),
                arguments("java.util.List<Order> orders;", "field type argument"),
                arguments("boolean is(Object value) {\n    return value instanceof Order;\n}", "instanceof"),
                arguments("Object type() {\n    return Order.class;\n}", "class literal"),
                arguments("void make() {\n    Order.create();\n}", "method call"),
                arguments("Object make() {\n    return new Order();\n}", "constructor call"),
                arguments("int count() {\n    return Order.count;\n}", "field read"),
                arguments("void count() {\n    Order.count = 1;\n}", "field write"),
                arguments("Runnable later() {\n    return () -> Order.create();\n}", "method call"),
                arguments("java.util.function.Supplier<Object> supplier() {\n    return Order::create;\n}",
                        "method reference"),
                arguments("@Tracked\nvoid tracked() {\n}", "annotation"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("everyKindOfReference")
    void everyKindOfDirectReferenceIsADependency(String member, String kind) {
        Report report = check("", shelf(member));

        assertThat(report.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.DIVERGENCE);
            assertThat(violation.identifiers()).containsExactly("stock", "orders");
            assertThat(violation.evidence()).extracting(Evidence::kind).contains(kind);
        });
    }

    static Stream<Arguments> everyKindOfDeclarationReference() {
        return Stream.of(
                arguments("public class Shelf extends Order {\n}", "extends"),
                arguments("public class Shelf implements Placed {\n}", "implements"),
                arguments("@Tracked\npublic class Shelf {\n}", "annotation"),
                arguments("public class Shelf<T extends Order> {\n}", "type parameter bound"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("everyKindOfDeclarationReference")
    void referencesInTheClassDeclarationAreDependencies(String declaration, String kind) {
        Report report = check("", source("fx/shop/stock/Shelf.java", declaration));

        assertThat(edge(report, "stock", "orders").evidence()).extracting(Evidence::kind).contains(kind);
        assertThat(edge(report, "stock", "orders").status()).isEqualTo(EdgeStatus.DIVERGENCE);
    }

    @Test
    void aDivergenceAgainstTheDirectionOfADeclaredRelationship() {
        Report report = check("", source("fx/shop/billing/Reminder.java",
                "public class Reminder {\n    Order order;\n}"));

        assertThat(edge(report, "billing", "orders").status()).isEqualTo(EdgeStatus.DIVERGENCE);
        assertThat(edge(report, "orders", "billing").status()).isEqualTo(EdgeStatus.CONVERGENCE);
    }

    @Test
    void onlyDirectReferencesCount() {
        // orders -> billing -> stock: the path through billing neither satisfies nor violates orders -> stock.
        Report report = check("", source("fx/shop/billing/Invoice.java", "public class Invoice {\n    Item item;\n}"));

        assertThat(report.violations()).extracting(Violation::type, Violation::identifiers)
                .containsExactly(tuple(ViolationType.DIVERGENCE,
                        List.of("billing", "stock")));
        assertThat(edge(report, "orders", "stock").status()).isEqualTo(EdgeStatus.NO_STATIC_EVIDENCE);
    }

    @Test
    void referencesInsideOneComponentAreIgnored() {
        Report report = check("", source("fx/shop/orders/internal/Ledger.java",
                "public class Ledger {\n    Order order;\n    fx.shop.orders.Placed placed;\n}"));

        assertThat(report.passed()).isTrue();
        assertThat(report.edges()).extracting(EdgeResult::source, EdgeResult::target)
                .doesNotContain(tuple("orders", "orders"));
    }

    @Test
    void classesOutsideTheRootAreIgnoredAsOriginsAndAsTargets() {
        Report report = check("",
                new FixtureSource("fx/tools/Tool.java", """
                        package fx.tools;

                        public class Tool {
                            fx.shop.orders.Order order;
                            fx.shop.stock.Item item;
                            fx.shop.Main main;
                        }
                        """),
                source("fx/shop/stock/Shelf.java", "public class Shelf {\n    fx.tools.Tool tool;\n}"));

        assertThat(report.passed()).isTrue();
        assertThat(report.edges()).hasSize(3);
    }

    @Test
    void anIsolatedClassMustNotDependOnAComponent() {
        FixtureSource main = new FixtureSource("fx/shop/Main.java", """
                package fx.shop;

                public class Main {
                    public static void main(String[] arguments) {
                        fx.shop.orders.Order.create(); // L:call
                    }
                }
                """);

        Report report = check("", main);

        assertThat(report.violations()).containsExactly(new Violation(ViolationType.ISOLATION_BREACH,
                List.of("fx.shop.Main", "orders"), "The isolated class fx.shop.Main depends on orders.",
                List.of(new Evidence("fx.shop.Main", "main(java.lang.String[])", "fx.shop.orders.Order", "create()",
                        "method call", "Main.java", main.lineOf("L:call")))));
    }

    @Test
    void aComponentMustNotDependOnAnIsolatedClass() {
        Report report = check("", source("fx/shop/stock/Shelf.java", "public class Shelf {\n    fx.shop.Main main;\n}"));

        assertThat(report.violations()).containsExactly(new Violation(ViolationType.ISOLATION_BREACH,
                List.of("stock", "fx.shop.Main"), "stock depends on the isolated class fx.shop.Main.",
                List.of(new Evidence("fx.shop.stock.Shelf", "main", "fx.shop.Main", "", "field type", "Shelf.java",
                        0))));
    }

    @Test
    void nestedClassesOfAnIsolatedClassAreIsolatedToo() {
        Report report = check("", new FixtureSource("fx/shop/Main.java", """
                package fx.shop;

                public class Main {
                    static class Wiring {
                        fx.shop.stock.Item item;
                    }

                    public static void main(String[] arguments) {
                    }
                }
                """));

        assertThat(report.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.ISOLATION_BREACH);
            assertThat(violation.identifiers()).containsExactly("fx.shop.Main", "stock");
            assertThat(violation.evidence()).extracting(Evidence::originClass).containsExactly("fx.shop.Main$Wiring");
        });
    }

    @Test
    void aDependencyOnAPlannedComponentWithoutARelationshipIsADivergence() {
        Report report = check("", source("fx/shop/stock/Shelf.java", "public class Shelf {\n    fx.shop.audit.Log log;\n}"),
                new FixtureSource("fx/shop/audit/Log.java", "package fx.shop.audit;\n\npublic class Log {\n}\n"));

        assertThat(report.violations()).extracting(Violation::type, Violation::identifiers).containsExactly(
                tuple(ViolationType.DIVERGENCE, List.of("stock", "audit")),
                tuple(ViolationType.PLANNED_COMPONENT_HAS_CODE,
                        List.of("audit", "fx.shop.audit.Log")));
    }

    @Test
    void everyDivergingPairIsOneViolationWithAllItsEvidence() {
        Report report = check("",
                source("fx/shop/stock/Shelf.java", "public class Shelf {\n    Order first;\n    Order second;\n}"),
                source("fx/shop/stock/Rack.java", "public class Rack {\n    Placed placed;\n}"));

        assertThat(report.violations()).singleElement().satisfies(violation -> assertThat(violation.evidence())
                .extracting(Evidence::originClass, Evidence::originMember)
                .containsExactly(
                        tuple("fx.shop.stock.Rack", "placed"),
                        tuple("fx.shop.stock.Shelf", "first"),
                        tuple("fx.shop.stock.Shelf", "second")));
    }

    private Report check(String relationships, FixtureSource... classes) {
        Path model = ShopFixture.writeModel(dir, relationships);
        return Conformance.check(model, List.of(FixtureCompiler.compile(ShopFixture.classesWith(classes))));
    }

    private static EdgeResult edge(Report report, String source, String target) {
        return report.edges().stream()
                .filter(edge -> edge.source().equals(source) && edge.target().equals(target))
                .findFirst()
                .orElseThrow();
    }

    /** A class Shelf in stock with one member, and every orders type imported. */
    private static FixtureSource shelf(String member) {
        return source("fx/shop/stock/Shelf.java", "public class Shelf {\n" + member.indent(4) + "}");
    }

    private static FixtureSource source(String path, String body) {
        String packageName = path.substring(0, path.lastIndexOf('/')).replace('/', '.');
        return new FixtureSource(path, """
                package %s;

                import fx.shop.orders.Order;
                import fx.shop.orders.OrderFailure;
                import fx.shop.orders.Placed;
                import fx.shop.orders.Tracked;
                import fx.shop.stock.Item;

                %s
                """.formatted(packageName, body));
    }
}
