package io.github.stigjohnny.c4contractkit.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
 * A REQUIRED relationship between two built components without any direct static reference fails the check (an
 * absence). A transitive path does not satisfy it; a relationship that touches a planned component is not evaluated.
 */
class ConformanceAbsenceTest {

    /** orders without any reference to billing. */
    private static final FixtureSource ORDER_WITHOUT_BILLING = new FixtureSource("fx/shop/orders/Order.java", """
            package fx.shop.orders;

            public class Order {
                public static int count;

                public static Order create() {
                    return new Order();
                }
            }
            """);

    @TempDir
    Path dir = Path.of("unset");

    @Test
    void aRequiredRelationshipWithoutAReferenceIsAnAbsence() {
        Report report = check("", ORDER_WITHOUT_BILLING);

        assertThat(report.passed()).isFalse();
        assertThat(report.violations()).containsExactly(new Violation(ViolationType.ABSENCE,
                List.of("orders", "billing"),
                "The model requires orders -> billing, but no class of orders references a class of billing.",
                List.of()));
        assertThat(edge(report, "orders", "billing"))
                .isEqualTo(new EdgeResult("orders", "billing", Mode.REQUIRED, EdgeStatus.ABSENCE, List.of()));
    }

    @Test
    void aRequiredRelationshipWithAReferenceIsAConvergence() {
        Report report = check("");

        assertThat(report.passed()).isTrue();
        assertThat(edge(report, "orders", "billing").status()).isEqualTo(EdgeStatus.CONVERGENCE);
    }

    static Stream<Arguments> singleReferences() {
        return Stream.of(
                arguments("an annotation", """
                        package fx.shop.orders;

                        @fx.shop.billing.Billed
                        public class Order {
                        }
                        """, "annotation"),
                arguments("a type argument", """
                        package fx.shop.orders;

                        public class Order {
                            java.util.List<fx.shop.billing.Invoice> invoices;
                        }
                        """, "field type argument"),
                arguments("a call inside a lambda", """
                        package fx.shop.orders;

                        public class Order {
                            Runnable later() {
                                return () -> new fx.shop.billing.Invoice();
                            }
                        }
                        """, "constructor call"),
                arguments("a nested class", """
                        package fx.shop.orders;

                        public class Order {
                            static class Lines {
                                fx.shop.billing.Invoice invoice;
                            }
                        }
                        """, "field type"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleReferences")
    void oneDirectReferenceOfAnyKindSatisfiesARequiredRelationship(String what, String order, String kind) {
        Report report = check("", new FixtureSource("fx/shop/orders/Order.java", order), new FixtureSource(
                "fx/shop/billing/Billed.java", """
                package fx.shop.billing;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.CLASS)
                public @interface Billed {
                }
                """));

        assertThat(report.passed()).as(what).isTrue();
        assertThat(edge(report, "orders", "billing").status()).isEqualTo(EdgeStatus.CONVERGENCE);
        assertThat(edge(report, "orders", "billing").evidence()).extracting(Evidence::kind).containsExactly(kind);
    }

    @Test
    void aTransitivePathDoesNotSatisfyARequiredRelationship() {
        // orders -> stock -> billing exists in the code and in the model, orders -> billing does not exist in the code.
        Report report = check("""
                stock -> billing "Prices" "Java" {
                    properties {
                        "contract.mode" "PERMITTED"
                    }
                }""",
                ORDER_WITHOUT_BILLING,
                new FixtureSource("fx/shop/orders/Reservation.java", """
                        package fx.shop.orders;

                        public class Reservation {
                            fx.shop.stock.Item item;
                        }
                        """),
                new FixtureSource("fx/shop/stock/Item.java", """
                        package fx.shop.stock;

                        public class Item {
                            fx.shop.billing.Invoice invoice;
                        }
                        """));

        assertThat(edge(report, "orders", "stock").status()).isEqualTo(EdgeStatus.CONVERGENCE);
        assertThat(edge(report, "stock", "billing").status()).isEqualTo(EdgeStatus.CONVERGENCE);
        assertThat(edge(report, "orders", "billing").status()).isEqualTo(EdgeStatus.ABSENCE);
        assertThat(report.violations()).extracting(Violation::type).containsExactly(ViolationType.ABSENCE);
    }

    @Test
    void aRequiredRelationshipThatTouchesAPlannedComponentIsNotEvaluated() {
        Report report = check("");

        assertThat(edge(report, "audit", "orders"))
                .isEqualTo(new EdgeResult("audit", "orders", Mode.REQUIRED, EdgeStatus.NOT_EVALUATED, List.of()));
        assertThat(report.violations()).isEmpty();
    }

    @Test
    void aRequiredRelationshipIsEvaluatedOnceBothEndpointsAreBuilt() {
        Path model = ShopFixture.writeDsl(dir, ShopFixture.MODEL
                .replace("audit = component \"Audit\" \"\" \"\" \"Planned\"", "audit = component \"Audit\"")
                .replace("\"contract.status\" \"planned\"", "\"contract.status\" \"built\""));
        Path classes = FixtureCompiler.compile(ShopFixture.classesWith(new FixtureSource("fx/shop/audit/Log.java",
                "package fx.shop.audit;\n\npublic class Log {\n}\n")));

        Report report = Conformance.check(model, List.of(classes));

        assertThat(edge(report, "audit", "orders").status()).isEqualTo(EdgeStatus.ABSENCE);
        assertThat(report.violations()).extracting(Violation::type, Violation::identifiers)
                .containsExactly(tuple(ViolationType.ABSENCE, List.of("audit", "orders")));
    }

    @Test
    void aPermittedRelationshipWithoutAReferenceIsNoViolation() {
        Report report = check("");

        assertThat(edge(report, "orders", "stock"))
                .isEqualTo(new EdgeResult("orders", "stock", Mode.PERMITTED, EdgeStatus.NO_STATIC_EVIDENCE, List.of()));
        assertThat(report.passed()).isTrue();
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
}
