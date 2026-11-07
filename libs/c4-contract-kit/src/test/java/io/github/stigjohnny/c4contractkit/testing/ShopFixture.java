package io.github.stigjohnny.c4contractkit.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * A small synthetic shop: a model with four components and the classes that conform to it. Tests add or change one
 * class or one relationship and look at what the comparison reports.
 *
 * <p>orders -> billing is REQUIRED and satisfied, orders -> stock is PERMITTED and unused, audit is planned and
 * audit -> orders is REQUIRED (not evaluated). {@code fx.shop.Main} is isolated.
 */
public final class ShopFixture {

    public static final String MODEL = """
            workspace {
                !identifiers flat
                !impliedRelationships false
                model {
                    shop = softwareSystem "Shop" {
                        app = container "App" {
                            properties {
                                "contract.root" "fx.shop"
                                "contract.isolated" "fx.shop.Main"
                            }
                            orders = component "Orders" {
                                properties {
                                    "contract.id" "orders"
                                    "contract.packages" "fx.shop.orders"
                                    "contract.status" "built"
                                }
                            }
                            billing = component "Billing" {
                                properties {
                                    "contract.id" "billing"
                                    "contract.packages" "fx.shop.billing"
                                    "contract.status" "built"
                                }
                            }
                            stock = component "Stock" {
                                properties {
                                    "contract.id" "stock"
                                    "contract.packages" "fx.shop.stock"
                                    "contract.status" "built"
                                }
                            }
                            audit = component "Audit" "" "" "Planned" {
                                properties {
                                    "contract.id" "audit"
                                    "contract.packages" "fx.shop.audit"
                                    "contract.status" "planned"
                                }
                            }
                        }
                    }
                    orders -> billing "Bills" "Java" "Required" {
                        properties {
                            "contract.mode" "REQUIRED"
                        }
                    }
                    orders -> stock "Reserves" "Java" {
                        properties {
                            "contract.mode" "PERMITTED"
                        }
                    }
                    audit -> orders "Records" "Java" "Required" {
                        properties {
                            "contract.mode" "REQUIRED"
                        }
                    }
                    // RELATIONSHIPS
                }
            }
            """;

    public static final FixtureSource MAIN = new FixtureSource("fx/shop/Main.java", """
            package fx.shop;

            public class Main {
                public static void main(String[] arguments) {
                }
            }
            """);

    public static final FixtureSource ORDER = new FixtureSource("fx/shop/orders/Order.java", """
            package fx.shop.orders;

            import fx.shop.billing.Invoice;

            public class Order {
                public static int count;

                public static Order create() {
                    return new Order();
                }

                Invoice invoice() {
                    return new Invoice(); // L:invoice
                }
            }
            """);
    public static final FixtureSource PLACED = new FixtureSource("fx/shop/orders/Placed.java", """
            package fx.shop.orders;

            public interface Placed {
            }
            """);
    public static final FixtureSource ORDER_FAILURE = new FixtureSource("fx/shop/orders/OrderFailure.java", """
            package fx.shop.orders;

            public class OrderFailure extends RuntimeException {
                private static final long serialVersionUID = 1L;
            }
            """);
    public static final FixtureSource TRACKED = new FixtureSource("fx/shop/orders/Tracked.java", """
            package fx.shop.orders;

            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            @Retention(RetentionPolicy.RUNTIME)
            public @interface Tracked {
            }
            """);
    public static final FixtureSource INVOICE = new FixtureSource("fx/shop/billing/Invoice.java", """
            package fx.shop.billing;

            public class Invoice {
            }
            """);
    public static final FixtureSource ITEM = new FixtureSource("fx/shop/stock/Item.java", """
            package fx.shop.stock;

            public class Item {
            }
            """);

    private ShopFixture() {
    }

    /** The classes that conform to {@link #MODEL}, followed by {@code more}; a source in {@code more} replaces one. */
    public static FixtureSource[] classesWith(FixtureSource... more) {
        Map<String, FixtureSource> byPath = new TreeMap<>();
        for (FixtureSource source : new FixtureSource[] {MAIN, ORDER, PLACED, ORDER_FAILURE, TRACKED, INVOICE, ITEM}) {
            byPath.put(source.path(), source);
        }
        for (FixtureSource source : more) {
            byPath.put(source.path(), source);
        }
        return byPath.values().toArray(FixtureSource[]::new);
    }

    /** Writes the model, with {@code relationships} added to its relationships, to {@code workspace.dsl}. */
    public static Path writeModel(Path directory, String relationships) {
        return writeDsl(directory, MODEL.replace("// RELATIONSHIPS", relationships));
    }

    public static Path writeDsl(Path directory, String dsl) {
        try {
            return Files.writeString(directory.resolve("workspace.dsl"), dsl, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
