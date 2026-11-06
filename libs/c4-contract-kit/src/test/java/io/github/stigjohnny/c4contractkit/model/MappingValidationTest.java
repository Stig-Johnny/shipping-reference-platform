package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.github.stigjohnny.c4contractkit.api.ComponentResult;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.ModelException;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.c4contractkit.api.Violation;
import io.github.stigjohnny.c4contractkit.api.ViolationType;
import io.github.stigjohnny.c4contractkit.testing.FixtureCompiler;
import io.github.stigjohnny.c4contractkit.testing.FixtureSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every validation rule of libs/c4-contract-kit/AGENTS.md ("Model rules"), each with a model that breaks only that
 * rule. A rejected model is a {@link ModelException} that lists every problem, each naming its elements.
 */
class MappingValidationTest {

    /** Valid: the checked container app, a built, a planned and a second built component, and their relationships. */
    private static final String VALID = """
            workspace {
                !identifiers flat
                !impliedRelationships false
                model {
                    clerk = person "Clerk"
                    shop = softwareSystem "Shop" {
                        web = container "Web"
                        app = container "App" {
                            properties {
                                "contract.root" "fx.shop"
                                "contract.isolated" " fx.shop.Main , fx.shop.Config"
                            }
                            orders = component "Orders" {
                                properties {
                                    "contract.id" "orders"
                                    "contract.packages" "fx.shop.orders, fx.shop.ordering"
                                    "contract.status" "built"
                                }
                            }
                            billing = component "Billing" "Bills orders." "Java" "Planned" {
                                properties {
                                    "contract.id" "billing"
                                    "contract.packages" "fx.shop.billing"
                                    "contract.status" "planned"
                                }
                            }
                            stock = component "Stock" {
                                properties {
                                    "contract.id" "stock"
                                    "contract.packages" "fx.shop.stock"
                                    "contract.status" "built"
                                }
                            }
                        }
                        db = container "Database" {
                            tables = component "Tables"
                        }
                    }
                    clerk -> web "Uses"
                    web -> app "Calls" "JSON"
                    orders -> billing "Bills" "Java" "Required" {
                        properties {
                            "contract.mode" "REQUIRED"
                        }
                    }
                    orders -> stock "Reserves" "Java" "Permitted" {
                        properties {
                            "contract.mode" "PERMITTED"
                        }
                    }
                    billing -> stock "Checks"
                    orders -> tables "Reads"
                }
            }
            """;

    @TempDir
    Path dir = Path.of("unset");

    @Test
    void aValidModelBecomesAnImmutableSortedContractModel() throws Exception {
        ContractModel model = ContractModelReader.read(write(VALID), "workspace.dsl");

        assertThat(model.root()).isEqualTo("fx.shop");
        assertThat(model.isolated()).containsExactly("fx.shop.Config", "fx.shop.Main");
        assertThat(model.components()).containsExactly(
                new ContractComponent("billing", "Billing", ComponentStatus.PLANNED, List.of("fx.shop.billing")),
                new ContractComponent("orders", "Orders", ComponentStatus.BUILT,
                        List.of("fx.shop.ordering", "fx.shop.orders")),
                new ContractComponent("stock", "Stock", ComponentStatus.BUILT, List.of("fx.shop.stock")));
        // A REQUIRED relationship may touch a planned component; a relationship without contract.mode is PERMITTED;
        // orders -> tables leaves the checked container and is documentation.
        assertThat(model.relationships()).containsExactly(
                new ContractRelationship("billing", "stock", RelationshipMode.PERMITTED),
                new ContractRelationship("orders", "billing", RelationshipMode.REQUIRED),
                new ContractRelationship("orders", "stock", RelationshipMode.PERMITTED));
        assertThatThrownBy(() -> model.components().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.relationships().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.isolated().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void classesBelongToTheComponentWhosePackageRootIsTheirPackageOrAPrefixFollowedByADot() throws Exception {
        ContractModel model = ContractModelReader.read(write(VALID), "workspace.dsl");

        assertThat(model.componentOf("fx.shop.orders.Order")).map(ContractComponent::id).hasValue("orders");
        assertThat(model.componentOf("fx.shop.orders.api.Order$Line")).map(ContractComponent::id).hasValue("orders");
        assertThat(model.componentOf("fx.shop.ordering.Queue")).map(ContractComponent::id).hasValue("orders");
        assertThat(model.componentOf("fx.shop.ordersx.Order")).isEmpty();
        assertThat(model.componentOf("fx.shop.Main")).isEmpty();
        assertThat(model.componentOf("fx.shop.orders")).isEmpty();
        assertThat(model.isUnderRoot("fx.shop.Main")).isTrue();
        assertThat(model.isUnderRoot("fx.shop.orders.Order")).isTrue();
        assertThat(model.isUnderRoot("fx.shopping.Main")).isFalse();
        assertThat(model.isUnderRoot("fx.shop")).isFalse();
        assertThat(model.isolatedEntryOf("fx.shop.Main")).hasValue("fx.shop.Main");
        assertThat(model.isolatedEntryOf("fx.shop.Main$Inner")).hasValue("fx.shop.Main");
        assertThat(model.isolatedEntryOf("fx.shop.Main$1")).hasValue("fx.shop.Main");
        assertThat(model.isolatedEntryOf("fx.shop.MainView")).isEmpty();
        assertThat(model.isolatedEntryOf("fx.shop.orders.Order")).isEmpty();
    }

    @Test
    void noContainerHasTheContractRoot() {
        assertRejected(VALID.replace("\"contract.root\" \"fx.shop\"", ""),
                "model: no container has contract.root; exactly one must");
    }

    @Test
    void twoContainersHaveTheContractRoot() {
        assertRejected(VALID.replace("web = container \"Web\"",
                        "web = container \"Web\" {\n properties {\n \"contract.root\" \"fx.web\"\n }\n }"),
                "app, web: more than one container has contract.root; exactly one may");
    }

    @Test
    void theContractRootIsNotAPackageName() {
        assertRejected(VALID.replace("\"contract.root\" \"fx.shop\"", "\"contract.root\" \"fx shop\""),
                "app: contract.root 'fx shop' is not a Java package name");
    }

    static Stream<Arguments> invalidIds() {
        return Stream.of(arguments("Stock"), arguments("1stock"), arguments("stock_room"), arguments("stock "),
                arguments("-stock"));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void aContractIdMustMatchThePattern(String id) {
        assertRejected(VALID.replace("\"contract.id\" \"stock\"", "\"contract.id\" \"" + id + "\""),
                "stock: contract.id '" + id + "' does not match [a-z][a-z0-9-]*");
    }

    @Test
    void aContractIdWithHyphensAndDigitsIsValid() throws Exception {
        ContractModel model = ContractModelReader.read(
                write(VALID.replace("\"contract.id\" \"stock\"", "\"contract.id\" \"stock-2\"")), "workspace.dsl");

        assertThat(model.components()).extracting(ContractComponent::id).contains("stock-2");
    }

    @Test
    void aComponentWithoutContractId() {
        assertRejected(VALID.replace("\"contract.id\" \"stock\"", ""), "stock: contract.id is missing");
    }

    @Test
    void twoComponentsWithTheSameContractId() {
        assertRejected(VALID.replace("\"contract.id\" \"stock\"", "\"contract.id\" \"orders\""),
                "orders, stock: contract.id 'orders' is used by more than one component");
    }

    @Test
    void aComponentWithoutContractPackages() {
        assertRejected(VALID.replace("\"contract.packages\" \"fx.shop.stock\"", ""),
                "stock: contract.packages is missing");
    }

    static Stream<Arguments> packagesWithAnEmptyEntry() {
        return Stream.of(arguments(","), arguments("fx.shop.stock,"), arguments("fx.shop.stock, ,fx.shop.x"),
                arguments(",fx.shop.stock"));
    }

    @ParameterizedTest
    @MethodSource("packagesWithAnEmptyEntry")
    void contractPackagesWithAnEmptyEntry(String packages) {
        assertRejected(VALID.replace("\"contract.packages\" \"fx.shop.stock\"",
                        "\"contract.packages\" \"" + packages + "\""),
                "stock: contract.packages has an empty entry");
    }

    static Stream<Arguments> packagesThatAreNotPackageNames() {
        return Stream.of(arguments("fx.shop.1stock"), arguments("fx.shop.stock.*"), arguments("fx.shop..stock"),
                arguments("fx.shop.class"));
    }

    @ParameterizedTest
    @MethodSource("packagesThatAreNotPackageNames")
    void contractPackagesMustBeJavaPackageNames(String packageName) {
        assertRejected(VALID.replace("\"contract.packages\" \"fx.shop.stock\"",
                        "\"contract.packages\" \"" + packageName + "\""),
                "stock: contract.packages entry '" + packageName + "' is not a Java package name");
    }

    static Stream<Arguments> packagesOutsideTheRoot() {
        return Stream.of(arguments("fx.other.stock"), arguments("fx.shop"), arguments("fx.shopping.stock"));
    }

    @ParameterizedTest
    @MethodSource("packagesOutsideTheRoot")
    void contractPackagesMustLieUnderTheRoot(String packageName) {
        assertRejected(VALID.replace("\"contract.packages\" \"fx.shop.stock\"",
                        "\"contract.packages\" \"" + packageName + "\""),
                "stock: contract.packages entry '" + packageName + "' is not under contract.root 'fx.shop'");
    }

    @Test
    void contractPackagesOfTwoComponentsMustNotOverlap() {
        assertRejected(VALID.replace("\"contract.packages\" \"fx.shop.stock\"",
                        "\"contract.packages\" \"fx.shop.orders.stock\""),
                "orders, stock: contract.packages entries 'fx.shop.orders' and 'fx.shop.orders.stock' overlap");
    }

    @Test
    void contractPackagesOfTwoComponentsMustNotBeEqual() {
        assertRejected(VALID.replace("\"contract.packages\" \"fx.shop.stock\"",
                        "\"contract.packages\" \"fx.shop.billing\""),
                "billing, stock: contract.packages entries 'fx.shop.billing' and 'fx.shop.billing' overlap");
    }

    @Test
    void contractPackagesOfOneComponentMustNotOverlap() {
        assertRejected(VALID.replace("\"contract.packages\" \"fx.shop.stock\"",
                        "\"contract.packages\" \"fx.shop.stock.api, fx.shop.stock\""),
                "stock: contract.packages entries 'fx.shop.stock' and 'fx.shop.stock.api' overlap");
    }

    @Test
    void aComponentWithoutContractStatus() {
        assertRejected(VALID.replace("\"contract.status\" \"planned\"", ""), "billing: contract.status is missing");
    }

    static Stream<Arguments> invalidStatuses() {
        return Stream.of(arguments("done"), arguments("Built"), arguments("built "));
    }

    @ParameterizedTest
    @MethodSource("invalidStatuses")
    void contractStatusIsBuiltOrPlanned(String status) {
        assertRejected(replaceFirst(VALID, "\"contract.status\" \"built\"", "\"contract.status\" \"" + status + "\""),
                "orders: contract.status '" + status + "' is neither built nor planned");
    }

    @Test
    void anEmptyPropertyValueIsRejectedByTheParser() {
        Path model = write(VALID.replace("\"contract.status\" \"planned\"", "\"contract.status\" \"\""));

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> assertThat(e.problems())
                        .singleElement(STRING)
                        .contains("A property value must be specified at line 24 of "));
    }

    @Test
    void aPlannedComponentWithoutThePlannedTag() {
        assertRejected(VALID.replace("\"Bills orders.\" \"Java\" \"Planned\"", "\"Bills orders.\" \"Java\""),
                "billing: contract.status is planned, but the tag Planned is missing");
    }

    @Test
    void aBuiltComponentWithThePlannedTag() {
        assertRejected(VALID.replace("stock = component \"Stock\"", "stock = component \"Stock\" \"\" \"\" \"Planned\""),
                "stock: the tag Planned is present, but contract.status is built");
    }

    @Test
    void anIsolatedClassOutsideTheRoot() {
        assertRejected(VALID.replace(" fx.shop.Main , fx.shop.Config", "fx.other.Main"),
                "app: contract.isolated entry 'fx.other.Main' is not under contract.root 'fx.shop'");
    }

    @Test
    void anIsolatedClassInsideAComponent() {
        assertRejected(VALID.replace(" fx.shop.Main , fx.shop.Config", "fx.shop.orders.Main"),
                "app: contract.isolated entry 'fx.shop.orders.Main' is inside the packages of orders");
    }

    static Stream<Arguments> isolatedEntriesThatAreNotClassNames() {
        return Stream.of(arguments("fx.shop.*"), arguments("fx.shop.Main Class"), arguments("fx.shop.new"));
    }

    @ParameterizedTest
    @MethodSource("isolatedEntriesThatAreNotClassNames")
    void isolatedEntriesAreFullyQualifiedClassNames(String entry) {
        assertRejected(VALID.replace(" fx.shop.Main , fx.shop.Config", entry),
                "app: contract.isolated entry '" + entry + "' is not a fully qualified class name");
    }

    @Test
    void contractIsolatedWithAnEmptyEntry() {
        assertRejected(VALID.replace(" fx.shop.Main , fx.shop.Config", "fx.shop.Main,"),
                "app: contract.isolated has an empty entry");
    }

    @Test
    void contractIsolatedListsAClassTwice() {
        assertRejected(VALID.replace(" fx.shop.Main , fx.shop.Config", "fx.shop.Main, fx.shop.Main"),
                "app: contract.isolated lists 'fx.shop.Main' more than once");
    }

    @Test
    void contractModeIsRequiredOrPermitted() {
        assertRejected(VALID.replace("\"contract.mode\" \"REQUIRED\"", "\"contract.mode\" \"MUST\""),
                "orders -> billing: contract.mode 'MUST' is neither REQUIRED nor PERMITTED");
    }

    @Test
    void contractModeOnARelationshipBetweenContainers() {
        assertRejected(VALID.replace("web -> app \"Calls\" \"JSON\"",
                        "web -> app \"Calls\" \"JSON\" {\n properties {\n \"contract.mode\" \"PERMITTED\"\n }\n }"),
                "web -> app: contract.mode is only allowed on relationships between two components of app");
    }

    @Test
    void contractModeOnARelationshipThatLeavesTheCheckedContainer() {
        assertRejected(VALID.replace("orders -> tables \"Reads\"",
                        "orders -> tables \"Reads\" {\n properties {\n \"contract.mode\" \"PERMITTED\"\n }\n }"),
                "orders -> tables: contract.mode is only allowed on relationships between two components of app");
    }

    @Test
    void aRequiredRelationshipWithoutTheRequiredTag() {
        assertRejected(VALID.replace("\"Bills\" \"Java\" \"Required\"", "\"Bills\" \"Java\""),
                "orders -> billing: contract.mode is REQUIRED, but the tag Required is missing");
    }

    @Test
    void aPermittedRelationshipWithTheRequiredTag() {
        assertRejected(VALID.replace("\"Reserves\" \"Java\" \"Permitted\"", "\"Reserves\" \"Java\" \"Required\""),
                "orders -> stock: the tag Required is present, but contract.mode is PERMITTED");
    }

    @Test
    void aRelationshipWithoutContractModeIsPermittedAndMustNotCarryTheRequiredTag() {
        assertRejected(VALID.replace("billing -> stock \"Checks\"", "billing -> stock \"Checks\" \"Java\" \"Required\""),
                "billing -> stock: the tag Required is present, but contract.mode is PERMITTED");
    }

    @Test
    void theRequiredTagOnARelationshipThatIsNotChecked() {
        assertRejected(VALID.replace("web -> app \"Calls\" \"JSON\"", "web -> app \"Calls\" \"JSON\" \"Required\""),
                "web -> app: the tag Required is only allowed on relationships between two components of app");
    }

    @Test
    void twoRelationshipsForOneOrderedPairOfComponents() {
        assertRejected(VALID.replace("billing -> stock \"Checks\"",
                        "billing -> stock \"Checks\"\n billing -> stock \"Counts\""),
                "billing -> stock: 2 relationships connect these components; at most one may");
    }

    @Test
    void relationshipsInBothDirectionsFormACycle() {
        assertRejected(VALID.replace("orders -> tables \"Reads\"", "orders -> tables \"Reads\"\n stock -> billing \"Tells\""),
                "billing, stock: the component relationships form a cycle");
    }

    @Test
    void aLongerCycleIsOneProblem() {
        assertRejected(VALID.replace("orders -> tables \"Reads\"", "orders -> tables \"Reads\"\n stock -> orders \"Tells\""),
                "billing, orders, stock: the component relationships form a cycle");
    }

    @Test
    void aComponentThatDependsOnItselfFormsACycle() {
        assertRejected(VALID.replace("orders -> tables \"Reads\"", "orders -> tables \"Reads\"\n stock -> stock \"Loops\""),
                "stock: the component relationships form a cycle");
    }

    @Test
    void anUnknownContractPropertyOnAComponent() {
        assertRejected(VALID.replace("\"contract.id\" \"stock\"", "\"contract.id\" \"stock\"\n \"contract.owner\" \"x\""),
                "stock: contract.owner is not a contract property");
    }

    @Test
    void aMisspelledContractModeIsNotSilentlyPermitted() {
        assertRejected(VALID.replace("\"contract.mode\" \"PERMITTED\"", "\"contract.mod\" \"PERMITTED\""),
                "orders -> stock: contract.mod is not a contract property");
    }

    @Test
    void contractRootOnASoftwareSystem() {
        assertRejected(VALID.replace("shop = softwareSystem \"Shop\" {",
                        "shop = softwareSystem \"Shop\" {\n properties {\n \"contract.root\" \"fx\"\n }"),
                "shop: contract.root is only allowed on a container");
    }

    @Test
    void contractIdOnAComponentOfAnotherContainer() {
        assertRejected(VALID.replace("tables = component \"Tables\"",
                        "tables = component \"Tables\" {\n properties {\n \"contract.id\" \"tables\"\n }\n }"),
                "tables: contract.id is only allowed on components of app");
    }

    @Test
    void contractIsolatedOnAComponent() {
        assertRejected(VALID.replace("\"contract.id\" \"stock\"", "\"contract.id\" \"stock\"\n \"contract.isolated\" \"fx.shop.X\""),
                "stock: contract.isolated is only allowed on the container with contract.root (app)");
    }

    @Test
    void contractPackagesAndStatusOnAContainer() {
        assertRejected(VALID.replace("web = container \"Web\"", """
                        web = container "Web" {
                            properties {
                                "contract.packages" "fx.web"
                                "contract.status" "built"
                            }
                        }"""),
                "web: contract.packages is only allowed on components of app",
                "web: contract.status is only allowed on components of app");
    }

    @Test
    void everyProblemIsListedAtOnceAndSorted() {
        assertRejected(VALID
                        .replace("\"contract.status\" \"planned\"", "\"contract.status\" \"later\"")
                        .replace("\"contract.mode\" \"REQUIRED\"", "\"contract.mode\" \"MUST\"")
                        .replace("\"contract.id\" \"stock\"", "\"contract.id\" \"Stock\""),
                "billing: contract.status 'later' is neither built nor planned",
                "orders -> billing: contract.mode 'MUST' is neither REQUIRED nor PERMITTED",
                "stock: contract.id 'Stock' does not match [a-z][a-z0-9-]*");
    }

    @Test
    void elementsWithoutAnIdentifierAreNamedByTheirCanonicalName() {
        assertRejected(VALID.replace("stock = component \"Stock\" {", "component \"Loose\"\n stock = component \"Stock\" {"),
                "Component://Shop.App.Loose: contract.id is missing",
                "Component://Shop.App.Loose: contract.packages is missing",
                "Component://Shop.App.Loose: contract.status is missing");
    }

    @Test
    void hierarchicalIdentifiersNameElementsByTheirPath() {
        assertRejected(VALID
                        .replace("!identifiers flat", "!identifiers hierarchical")
                        .replace("clerk -> web", "clerk -> shop.web")
                        .replace("web -> app", "shop.web -> shop.app")
                        .replace("orders -> billing", "shop.app.orders -> shop.app.billing")
                        .replace("orders -> stock", "shop.app.orders -> shop.app.stock")
                        .replace("billing -> stock", "shop.app.billing -> shop.app.stock")
                        .replace("orders -> tables", "shop.app.orders -> shop.db.tables")
                        .replace("\"contract.id\" \"stock\"", ""),
                "shop.app.stock: contract.id is missing");
    }

    @Test
    void everyClassBelongsToAComponentOrIsIsolated() {
        Report report = checkClasses();

        assertThat(report.violations()).isEmpty();
        assertThat(report.components()).extracting(ComponentResult::id, ComponentResult::classCount)
                .containsExactly(tuple("billing", 0), tuple("orders", 2), tuple("stock", 1));
    }

    @Test
    void aClassInNoComponentAndNotIsolatedIsUnmapped() {
        Report report = checkClasses(
                new FixtureSource("fx/shop/misc/Stray.java", "package fx.shop.misc;\n\npublic class Stray {\n}\n"),
                new FixtureSource("fx/shop/Helper.java", "package fx.shop;\n\nclass Helper {\n}\n"),
                new FixtureSource("fx/other/Elsewhere.java", "package fx.other;\n\npublic class Elsewhere {\n}\n"));

        assertThat(report.violations()).containsExactly(
                new Violation(ViolationType.UNMAPPED_CLASS, List.of("fx.shop.Helper"), "fx.shop.Helper is under "
                        + "contract.root fx.shop, but in no component's packages and not in contract.isolated.",
                        List.of()),
                new Violation(ViolationType.UNMAPPED_CLASS, List.of("fx.shop.misc.Stray"), "fx.shop.misc.Stray is "
                        + "under contract.root fx.shop, but in no component's packages and not in contract.isolated.",
                        List.of()));
    }

    @Test
    void aClassFileTheImportCannotReadIsNeverASilentPass() throws IOException {
        // Every built component has classes, so BUILT_COMPONENT_EMPTY cannot be what fails this check. Readable,
        // fx.shop.misc.Stray would be an unmapped class; truncated, ArchUnit would skip it.
        Path classes = dir.resolve("classes");
        Path compiled = FixtureCompiler.compile(MAIN, CONFIG, ORDER, QUEUE, ITEM,
                new FixtureSource("fx/shop/misc/Stray.java", "package fx.shop.misc;\n\npublic class Stray {\n}\n"));
        try (Stream<Path> files = Files.walk(compiled)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path copy = classes.resolve(compiled.relativize(file).toString());
                Files.createDirectories(copy.getParent());
                Files.copy(file, copy);
            }
        }
        Path stray = classes.resolve("fx/shop/misc/Stray.class");
        Files.write(stray, java.util.Arrays.copyOf(Files.readAllBytes(stray), 30));

        assertThatThrownBy(() -> check(VALID, classes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(stray.toString());
    }

    @Test
    void aClassInAPlannedComponentFailsUntilTheComponentIsBuilt() {
        Report report = checkClasses(
                new FixtureSource("fx/shop/billing/Invoice.java", "package fx.shop.billing;\n\npublic class Invoice {\n}\n"));

        assertThat(report.violations()).containsExactly(new Violation(ViolationType.PLANNED_COMPONENT_HAS_CODE,
                List.of("billing", "fx.shop.billing.Invoice"),
                "billing is planned, but its packages contain fx.shop.billing.Invoice.", List.of()));
        assertThat(report.components()).extracting(ComponentResult::id, ComponentResult::classCount)
                .contains(tuple("billing", 1));
    }

    @Test
    void aBuiltComponentWithoutAClassFails() {
        Report report = check(VALID, FixtureCompiler.compile(MAIN, CONFIG, ORDER, QUEUE));

        assertThat(report.violations()).containsExactly(new Violation(ViolationType.BUILT_COMPONENT_EMPTY,
                List.of("stock"), "stock is built, but its packages contain no class.", List.of()));
    }

    @Test
    void packageInfoIsNotAClass() {
        Report report = check(VALID, FixtureCompiler.compile(MAIN, CONFIG, ORDER, QUEUE,
                new FixtureSource("fx/shop/package-info.java", "@Deprecated\npackage fx.shop;\n"),
                new FixtureSource("fx/shop/billing/package-info.java", "@Deprecated\npackage fx.shop.billing;\n"),
                new FixtureSource("fx/shop/stock/package-info.java", "@Deprecated\npackage fx.shop.stock;\n")));

        assertThat(report.violations()).extracting(Violation::type, Violation::identifiers)
                .containsExactly(tuple(ViolationType.BUILT_COMPONENT_EMPTY, List.of("stock")));
        assertThat(report.components()).extracting(ComponentResult::id, ComponentResult::classCount)
                .containsExactly(tuple("billing", 0), tuple("orders", 2), tuple("stock", 0));
    }

    @Test
    void nestedAndAnonymousClassesOfAnIsolatedClassAreIsolated() {
        Report report = checkClasses(new FixtureSource("fx/shop/Main.java", """
                package fx.shop;

                public class Main {
                    static class Settings {
                    }

                    Runnable task() {
                        return new Runnable() {
                            @Override
                            public void run() {
                            }
                        };
                    }
                }
                """));

        assertThat(report.violations()).isEmpty();
    }

    private void assertRejected(String dsl, String... problems) {
        Path model = write(dsl);

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> {
                    assertThat(e.problems()).containsExactly(problems);
                    assertThat(e.getMessage()).contains(problems);
                });
    }

    private static final FixtureSource MAIN =
            new FixtureSource("fx/shop/Main.java", "package fx.shop;\n\npublic class Main {\n}\n");
    private static final FixtureSource CONFIG =
            new FixtureSource("fx/shop/Config.java", "package fx.shop;\n\npublic class Config {\n}\n");
    private static final FixtureSource ORDER =
            new FixtureSource("fx/shop/orders/Order.java", "package fx.shop.orders;\n\npublic class Order {\n}\n");
    private static final FixtureSource QUEUE =
            new FixtureSource("fx/shop/ordering/Queue.java", "package fx.shop.ordering;\n\npublic class Queue {\n}\n");
    private static final FixtureSource ITEM =
            new FixtureSource("fx/shop/stock/Item.java", "package fx.shop.stock;\n\npublic class Item {\n}\n");

    /** The valid model with classes for each built component, plus {@code more} (a source may replace one). */
    private Report checkClasses(FixtureSource... more) {
        Map<String, FixtureSource> sources = new TreeMap<>();
        for (FixtureSource source : List.of(MAIN, CONFIG, ORDER, QUEUE, ITEM)) {
            sources.put(source.path(), source);
        }
        for (FixtureSource source : more) {
            sources.put(source.path(), source);
        }
        return check(VALID, FixtureCompiler.compile(sources.values().toArray(FixtureSource[]::new)));
    }

    private Report check(String dsl, Path classes) {
        return Conformance.check(write(dsl), List.of(classes));
    }

    private static String replaceFirst(String text, String target, String replacement) {
        int at = text.indexOf(target);
        assertThat(at).as("the model contains %s", target).isNotNegative();
        return text.substring(0, at) + replacement + text.substring(at + target.length());
    }

    private Path write(String dsl) {
        try {
            return Files.writeString(dir.resolve("workspace.dsl"), dsl, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
