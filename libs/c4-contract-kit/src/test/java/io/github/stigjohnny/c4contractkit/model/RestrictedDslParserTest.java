package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.ModelException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The parser runs with every Structurizr feature switched off: nothing in a model can run code, read another file,
 * reach the network or read the environment (security/reviews/maven--structurizr-dsl--6.2.4.md).
 */
class RestrictedDslParserTest {

    /** A complete, valid model; each rejection test adds exactly one construct to it. */
    private static final String VALID = """
            workspace {
                !identifiers flat
                !impliedRelationships false
                model {
                    shop = softwareSystem "Shop" {
                        app = container "App" {
                            properties {
                                "contract.root" "fx.shop"
                            }
                            orders = component "Orders" {
                                properties {
                                    "contract.id" "orders"
                                    "contract.packages" "fx.shop.orders"
                                    "contract.status" "built"
                                }
                            }
                        }
                    }
                    // MODEL
                }
                views {
                    // VIEWS
                }
                // WORKSPACE
            }
            """;

    @Test
    void theOneFactoryDisablesAllTenParserFeatures() {
        assertThat(RestrictedDslParser.DISABLED_FEATURES).containsExactlyInAnyOrder(
                Features.ENVIRONMENT, Features.FILE_SYSTEM, Features.HTTP, Features.HTTPS, Features.INCLUDE,
                Features.PLUGINS, Features.SCRIPTS, Features.COMPONENT_FINDER, Features.DOCUMENTATION,
                Features.DECISIONS);

        StructurizrDslParser parser = RestrictedDslParser.newParser();

        assertThat(RestrictedDslParser.DISABLED_FEATURES)
                .allSatisfy(feature -> assertThat(parser.getFeatures().isEnabled(feature)).as(feature).isFalse());
    }

    @Test
    void theValidModelParses(@TempDir Path dir) throws Exception {
        ContractModel model = ContractModelReader.read(write(dir, VALID), "workspace.dsl");

        assertThat(model.components()).extracting(ContractComponent::id).containsExactly("orders");
    }

    static Stream<Arguments> forbiddenConstructs() {
        return Stream.of(
                arguments("!script", "WORKSPACE", "!script groovy {\n    workspace.name = 'x'\n}",
                        "!script is not permitted"),
                arguments("!plugin", "WORKSPACE", "!plugin fx.Plugin", "!plugin is not permitted"),
                arguments("!include of a file", "MODEL", "!include other.dsl", "!include is not permitted"),
                arguments("!include of a URL", "MODEL", "!include https://127.0.0.1:9/other.dsl",
                        "!include is not permitted"),
                arguments("!docs", "WORKSPACE", "!docs docs", "!docs is not permitted"),
                arguments("!adrs", "WORKSPACE", "!adrs docs", "!adrs is not permitted"),
                arguments("!decisions", "WORKSPACE", "!decisions docs", "!decisions is not permitted"),
                arguments("!components", "MODEL",
                        "!element app {\n    !components {\n        classes classes\n    }\n}",
                        "!components is not permitted"),
                arguments("!impliedRelationships with a class", "WORKSPACE", "!impliedRelationships fx.Strategy",
                        "The implied relationships strategy fx.Strategy is not available"),
                arguments("!extend", "MODEL", "!extend shop {\n}", "!extend was previously deprecated"),
                arguments("an image view of a file", "VIEWS", "image shop \"Picture\" {\n    image picture.png\n}",
                        "image <file> is not permitted"),
                arguments("an image view of a URL", "VIEWS",
                        "image shop \"Picture\" {\n    image https://127.0.0.1:9/picture.png\n}",
                        "Image views via HTTPS are not permitted"),
                arguments("a theme file", "VIEWS", "theme theme.json", "File-based themes are not permitted"),
                arguments("an icon file", "VIEWS",
                        "styles {\n    element \"Component\" {\n        icon icon.png\n    }\n}",
                        "!icon <file> is not permitted"),
                arguments("an icon URL", "VIEWS",
                        "styles {\n    element \"Component\" {\n        icon https://127.0.0.1:9/icon.png\n    }\n}",
                        "Icons via HTTPS are not permitted"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forbiddenConstructs")
    void theParserRejects(String construct, String slot, String dsl, String reason, @TempDir Path dir)
            throws IOException {
        Path model = write(dir, VALID.replace("// " + slot, dsl));
        writeEveryFileTheConstructCouldReach(dir);

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> assertThat(e.problems())
                        .singleElement(STRING)
                        .contains(reason)
                        .contains("workspace.dsl"));
    }

    static Stream<Arguments> workspacesThatExtendOthers() {
        return Stream.of(
                arguments("workspace extends other.dsl {\n}\n",
                        "Extending a file-based workspace is not permitted"),
                arguments("workspace extends https://127.0.0.1:9/other.dsl {\n}\n",
                        "Extends via HTTPS are not permitted"),
                arguments("workspace extends http://127.0.0.1:9/other.dsl {\n}\n",
                        "Extends via HTTP are not permitted"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("workspacesThatExtendOthers")
    void theParserRejectsWorkspaceExtends(String dsl, String reason, @TempDir Path dir) throws IOException {
        Path model = write(dir, dsl);
        writeEveryFileTheConstructCouldReach(dir);

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> assertThat(e.problems())
                        .singleElement(STRING)
                        .contains(reason));
    }

    static Stream<Arguments> constructsTheParserStoresButTheKitRejects() {
        return Stream.of(
                arguments("an inline PlantUML image view", """
                        properties {
                            "plantuml.url" "http://127.0.0.1:9"
                        }
                        image shop "Sequence" {
                            plantuml ""\"
                        @startuml
                        a -> b
                        @enduml
                        ""\"
                        }""", "views: image view 'Sequence' is not supported: image views make renderers fetch content"),
                arguments("an inline Mermaid image view", """
                        properties {
                            "mermaid.url" "http://127.0.0.1:9"
                        }
                        image shop "Flow" {
                            mermaid ""\"
                        graph TD
                          a-->b
                        ""\"
                        }""", "views: image view 'Flow' is not supported: image views make renderers fetch content"),
                arguments("a theme URL", "theme https://127.0.0.1:9/theme.json",
                        "views: theme 'https://127.0.0.1:9/theme.json' is not supported: themes are fetched by renderers"),
                arguments("the default theme", "theme default",
                        "views: theme 'https://static.structurizr.com/themes/default/theme.json' is not supported: "
                                + "themes are fetched by renderers"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constructsTheParserStoresButTheKitRejects")
    void theKitRejectsAfterParsing(String construct, String views, String problem, @TempDir Path dir)
            throws IOException {
        Path model = write(dir, VALID.replace("// VIEWS", views));

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> assertThat(e.problems()).containsExactly(problem));
    }

    @Test
    void everyThemeIsListed(@TempDir Path dir) throws IOException {
        Path model = write(dir, VALID.replace("// VIEWS",
                "themes https://127.0.0.1:9/a.json https://127.0.0.1:9/b.json"));

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> assertThat(e.problems()).containsExactly(
                        "views: theme 'https://127.0.0.1:9/a.json' is not supported: themes are fetched by renderers",
                        "views: theme 'https://127.0.0.1:9/b.json' is not supported: themes are fetched by renderers"));
    }

    @Test
    void environmentVariablesAreNeitherSubstitutedNorIgnored(@TempDir Path dir) throws IOException {
        String value = System.getenv("PATH");
        assertThat(value).as("the test needs PATH in its environment").isNotBlank();
        String dsl = VALID.replace("orders = component \"Orders\"",
                "orders = component \"Orders\" \"Reads ${PATH} and ${HOME}.\"");
        Path model = write(dir, dsl);

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> {
                    assertThat(e.problems()).satisfiesExactly(
                            problem -> assertThat(problem).endsWith("workspace.dsl:" + lineOf(dsl, "${HOME}")
                                    + ": ${HOME} is not substituted: environment variables are not read, and no "
                                    + "!const or !var defines HOME before this line"),
                            problem -> assertThat(problem).endsWith("workspace.dsl:" + lineOf(dsl, "${PATH}")
                                    + ": ${PATH} is not substituted: environment variables are not read, and no "
                                    + "!const or !var defines PATH before this line"));
                    assertThat(e.getMessage()).doesNotContain(value);
                });
    }

    @Test
    void anUnresolvedReferenceAnywhereInTheFileIsAProblem(@TempDir Path dir) throws IOException {
        String dsl = """
                workspace "${WORKSPACE_NAME}" "${WORKSPACE_DESCRIPTION}" {
                    !identifiers flat
                    !impliedRelationships false
                    properties {
                        "owner" "${WORKSPACE_PROPERTY}"
                    }
                    model {
                        shop = softwareSystem "Shop" {
                            app = container "App" "" "${TECH}" "${TAG}" {
                                properties {
                                    "contract.root" "fx.shop"
                                    "${KEY}" "${VALUE}"
                                }
                                group "${GROUP}" {
                                    orders = component "Orders" {
                                        properties {
                                            "contract.id" "orders"
                                            "contract.packages" "fx.shop.orders"
                                            "contract.status" "built"
                                        }
                                    }
                                }
                            }
                        }
                        clerk = person "Clerk"
                        clerk -> app "Uses ${RELATIONSHIP}" "${PROTOCOL}" {
                            properties {
                                "note" "${NOTE}"
                            }
                        }
                        live = deploymentEnvironment "Live" {
                            server = deploymentNode "Server" "" "${OS}" {
                                gateway = infrastructureNode "Gateway" "" "${PROXY}"
                            }
                        }
                    }
                    views {
                        component app "Components" "${VIEW_DESCRIPTION}" {
                            title "${VIEW_TITLE}"
                            include *
                            properties {
                                "shown" "${VIEW_PROPERTY}"
                            }
                        }
                        properties {
                            "set" "${VIEWS_PROPERTY}"
                        }
                        terminology {
                            person "${TERM}"
                        }
                    }
                }
                """;

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactlyInAnyOrderElementsOf(Stream.of("WORKSPACE_NAME", "WORKSPACE_DESCRIPTION",
                                        "WORKSPACE_PROPERTY", "TECH", "TAG", "KEY", "VALUE", "GROUP", "RELATIONSHIP",
                                        "PROTOCOL", "NOTE", "OS", "PROXY", "VIEW_DESCRIPTION", "VIEW_TITLE",
                                        "VIEW_PROPERTY", "VIEWS_PROPERTY", "TERM")
                                .map(name -> unresolved(dsl, name))
                                .toList()));
    }

    @Test
    void aReferenceBeforeTheConstantThatDefinesItIsAProblem(@TempDir Path dir) throws IOException {
        String dsl = VALID
                .replace("orders = component \"Orders\"", "orders = component \"${NAME}\"")
                .replace("// MODEL", "!const NAME \"Orders\"");

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "NAME")));
    }

    @Test
    void referencesInCommentsDoNotCount(@TempDir Path dir) throws Exception {
        String dsl = VALID.replace("// MODEL", """
                // ${LINE_COMMENT}
                # ${HASH_COMMENT}
                /* ${ONE_LINE_BLOCK} */
                /* ${BLOCK_START}
                   ${BLOCK_INSIDE}
                   ${BLOCK_END} */""");

        ContractModel model = ContractModelReader.read(write(dir, dsl), "workspace.dsl");

        assertThat(model.components()).extracting(ContractComponent::id).containsExactly("orders");
    }

    @Test
    void aReferenceInATextBlockCountsEvenOnALineThatLooksLikeAComment(@TempDir Path dir) throws IOException {
        String dsl = VALID.replace("orders = component \"Orders\" {", """
                orders = component "Orders" {
                    description ""\"
                # Orders, from ${TEXT_BLOCK}
                ""\"
                """);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "TEXT_BLOCK")));
    }

    @Test
    void aByteOrderMarkDoesNotHideACommentOnTheFirstLine(@TempDir Path dir) throws Exception {
        String dsl = "\uFEFF// ${COMMENTED}\n" + VALID;

        ContractModel model = ContractModelReader.read(write(dir, dsl), "workspace.dsl");

        assertThat(model.components()).extracting(ContractComponent::id).containsExactly("orders");
    }

    @Test
    void aModelFileThatIsNotUtf8IsAProblem(@TempDir Path dir) throws IOException {
        Path model = Files.write(dir.resolve("workspace.dsl"), new byte[] {'w', 'o', (byte) 0xFF, 'r', 'k'});

        assertThatThrownBy(() -> ContractModelReader.read(model, "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .singleElement(STRING)
                        .startsWith("workspace.dsl: the model file is not readable UTF-8: "));
    }

    /** The problem for a ${name} that nothing defines, at the only line of the model that contains it. */
    private static String unresolved(String dsl, String name) {
        return "workspace.dsl:" + lineOf(dsl, "${" + name + "}") + ": ${" + name + "} is not substituted: environment "
                + "variables are not read, and no !const or !var defines " + name + " before this line";
    }

    private static int lineOf(String text, String fragment) {
        List<String> lines = text.lines().toList();
        List<Integer> found = java.util.stream.IntStream.range(0, lines.size())
                .filter(index -> lines.get(index).contains(fragment))
                .mapToObj(index -> index + 1)
                .toList();
        assertThat(found).as("lines with %s", fragment).hasSize(1);
        return found.get(0);
    }

    @Test
    void constantsDefinedInTheModelAreStillSubstituted(@TempDir Path dir) throws Exception {
        Path model = write(dir, VALID
                .replace("!identifiers flat", "!identifiers flat\n!const ROOT \"fx.shop\"\n!const NAME \"Orders\"")
                .replace("\"contract.root\" \"fx.shop\"", "\"contract.root\" \"${ROOT}\"")
                .replace("orders = component \"Orders\"", "orders = component \"${NAME}\""));

        ContractModel contract = ContractModelReader.read(model, "workspace.dsl");

        assertThat(contract.root()).isEqualTo("fx.shop");
        assertThat(contract.components()).extracting(ContractComponent::name).containsExactly("Orders");
    }

    /** A component of another container, and a relationship to it from orders that implies three more. */
    private static final String OTHER_CONTAINER = """
            !element shop {
                db = container "Database" {
                    tables = component "Tables"
                }
            }
            orders -> tables "Reads"
            """;

    private static final String IMPLIED = ": implied by orders -> tables; implied relationships are disabled, so the "
            + "model must say !impliedRelationships false and never switch them on";

    @Test
    void anImpliedRelationshipIsRejectedAndSoIsTheContractPropertyItWasGiven(@TempDir Path dir) throws IOException {
        // The file switches implied relationships on and gives the implied app -> db relationship contract.mode.
        Path model = write(dir, VALID
                .replace("!impliedRelationships false", "!impliedRelationships true")
                .replace("// MODEL", OTHER_CONTAINER + """
                        !relationships "app->db" {
                            properties {
                                "contract.mode" "REQUIRED"
                            }
                        }"""));

        assertThatThrownBy(() -> ContractModelReader.read(model, "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems()).containsExactly(
                        "app -> db: contract.mode is only allowed on relationships between two components of app",
                        "app -> db" + IMPLIED,
                        "app -> tables" + IMPLIED,
                        "orders -> db" + IMPLIED));
    }

    @Test
    void aModelThatLeavesImpliedRelationshipsOnIsRejectedWhenItCreatesOne(@TempDir Path dir) throws IOException {
        // Without !impliedRelationships false the DSL creates implied relationships by default.
        Path model = write(dir, VALID
                .replace("!impliedRelationships false\n", "")
                .replace("// MODEL", OTHER_CONTAINER));

        assertThatThrownBy(() -> ContractModelReader.read(model, "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems()).containsExactly(
                        "app -> db" + IMPLIED, "app -> tables" + IMPLIED, "orders -> db" + IMPLIED));
    }

    @Test
    void parsingAValidModelCreatesNoImpliedRelationship(@TempDir Path dir) throws Exception {
        Path model = write(dir, VALID.replace("// MODEL", OTHER_CONTAINER));

        ParsedModel parsed = RestrictedDslParser.parse(model, "workspace.dsl", new Problems());

        assertThat(parsed.workspace().getModel().getRelationships())
                .extracting(relationship -> relationship.getSource().getName() + " -> "
                        + relationship.getDestination().getName())
                .containsExactly("Orders -> Tables");
        assertThat(parsed.workspace().getModel().getRelationships())
                .allSatisfy(relationship -> assertThat(relationship.getLinkedRelationshipId()).isNull());
        assertThat(ContractModelReader.read(model, "workspace.dsl").components()).hasSize(1);
    }

    @Test
    void theCopyOfARelationshipBetweenDeploymentInstancesIsNotAnImpliedOne(@TempDir Path dir) throws Exception {
        // Structurizr links each copy to the container relationship it copies, as it links an implied relationship.
        Path model = write(dir, VALID.replace("// MODEL", OTHER_CONTAINER + """
                app -> db "Writes"
                deploymentEnvironment "Live" {
                    deploymentNode "Server" {
                        containerInstance app
                        containerInstance db
                    }
                }
                """));

        ParsedModel parsed = RestrictedDslParser.parse(model, "workspace.dsl", new Problems());

        assertThat(parsed.workspace().getModel().getRelationships())
                .filteredOn(relationship -> relationship.getLinkedRelationshipId() != null)
                .extracting(relationship -> relationship.getSource().getClass().getSimpleName() + " "
                        + relationship.getSource().getName() + " -> " + relationship.getDestination().getName())
                .containsExactly("ContainerInstance App -> Database");
        assertThat(ContractModelReader.read(model, "workspace.dsl").components()).hasSize(1);
    }

    @Test
    void anUnresolvedReferenceAndABrokenModelRuleAreReportedTogether(@TempDir Path dir) throws IOException {
        String dsl = VALID
                .replace("orders = component \"Orders\"", "orders = component \"Orders\" \"Reads ${HOME}\"")
                .replace("\"contract.id\" \"orders\"", "");

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly("orders: contract.id is missing", unresolved(dsl, "HOME")));
    }

    @Test
    void withoutTheCheckedContainerOnlyTheChecksThatNeedItAreSkipped(@TempDir Path dir) throws IOException {
        // No contract.root, a theme, and implied relationships left on.
        String dsl = VALID
                .replace("!impliedRelationships false\n", "")
                .replace("\"contract.root\" \"fx.shop\"", "")
                .replace("// MODEL", OTHER_CONTAINER)
                .replace("// VIEWS", "theme https://127.0.0.1:9/theme.json");

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems()).containsExactly(
                        "app -> db" + IMPLIED,
                        "app -> tables" + IMPLIED,
                        "model: no container has contract.root; exactly one must",
                        "orders -> db" + IMPLIED,
                        "views: theme 'https://127.0.0.1:9/theme.json' is not supported: themes are fetched by "
                                + "renderers"));
    }

    @Test
    void aMissingFileIsAModelProblem(@TempDir Path dir) {
        Path missing = dir.resolve("missing.dsl");

        assertThatThrownBy(() -> ContractModelReader.read(missing, "architecture/missing.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly("architecture/missing.dsl: the model file does not exist"));
    }

    @Test
    void aFileWithoutAWorkspaceIsAModelProblem(@TempDir Path dir) throws IOException {
        Path empty = write(dir, "// nothing here\n");

        assertThatThrownBy(() -> ContractModelReader.read(empty, "architecture/empty.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly("architecture/empty.dsl: the file defines no workspace"));
    }

    @Test
    void aSyntaxErrorNamesTheLineAndTheDisplayPathOnly(@TempDir Path dir) throws IOException {
        Path model = write(dir, VALID.replace("orders = component", "orders = componnt"));

        assertThatThrownBy(() -> ContractModelReader.read(model, "architecture/workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .singleElement(STRING)
                        .contains("at line 10 of architecture/workspace.dsl")
                        .doesNotContain(dir.toString()));
    }

    private static Path write(Path dir, String dsl) throws IOException {
        return Files.writeString(dir.resolve("workspace.dsl"), dsl, StandardCharsets.UTF_8);
    }

    /** Creates the files a rejected construct names, so a rejection cannot come from a missing file. */
    private static void writeEveryFileTheConstructCouldReach(Path dir) throws IOException {
        Files.writeString(dir.resolve("other.dsl"), "workspace {\n}\n", StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("docs"));
        Files.writeString(dir.resolve("docs/0001-record.md"), "# 1. Record\n\nDate: 2026-11-06\n\n## Status\n\nAccepted\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("classes"));
        Files.write(dir.resolve("picture.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        Files.write(dir.resolve("icon.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        Files.writeString(dir.resolve("theme.json"), "{}", StandardCharsets.UTF_8);
    }
}
