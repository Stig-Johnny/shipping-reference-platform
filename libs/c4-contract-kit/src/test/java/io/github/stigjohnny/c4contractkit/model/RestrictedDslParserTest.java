package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.model.Element;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.ModelException;
import io.github.stigjohnny.c4contractkit.testing.Serialization;
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

    /** StructurizrDslParser.STRING_SUBSTITUTION_PATTERN. */
    private static final java.util.regex.Pattern REFERENCE =
            java.util.regex.Pattern.compile("\\$\\{([a-zA-Z0-9-_.]+?)}");

    /** Where the check of the parsed strings finds each name of {@link #EVERY_PLACE}. */
    private static final org.assertj.core.groups.Tuple[] EVERY_PLACE_FOUND = {
        tuple("app", "KEY"), tuple("app", "TAG"), tuple("app", "TECH"), tuple("app", "VALUE"),
        tuple("appInstance", "DEPLOYMENT_GROUP"), tuple("appInstance", "ENVIRONMENT"),
        tuple("appInstance", "HEALTH_CHECK"),
        tuple("clerk", "PERSON_NAME"),
        tuple("clerk -> app", "NOTE"), tuple("clerk -> app", "PROTOCOL"), tuple("clerk -> app", "RELATIONSHIP"),
        tuple("clerk -> app", "RELATIONSHIP_TAG"),
        tuple("configuration", "USER"),
        tuple("custom", "CUSTOM_DESCRIPTION"), tuple("custom", "METADATA"),
        tuple("gateway", "ENVIRONMENT"), tuple("gateway", "PROXY"),
        tuple("model", "MODEL_PROPERTY"),
        tuple("orders", "COMPONENT_TECH"), tuple("orders", "GROUP"), tuple("orders", "PERSPECTIVE_BLOCK"),
        tuple("orders", "PERSPECTIVE_NAME"), tuple("orders", "PERSPECTIVE_TEXT"), tuple("orders", "PERSPECTIVE_VALUE"),
        tuple("server", "ENVIRONMENT"), tuple("server", "OS"),
        tuple("shop", "SYSTEM_DESCRIPTION"),
        tuple("view Components", "VIEW_DESCRIPTION"), tuple("view Components", "VIEW_PROPERTY"),
        tuple("view Components", "VIEW_TITLE"),
        tuple("view Filtered", "FILTER_TAG"),
        tuple("view Flow", "DYNAMIC_DESCRIPTION"), tuple("view Flow", "STEP"), tuple("view Flow", "STEP_ORDER"),
        tuple("view Flow", "STEP_PROPERTY"),
        tuple("view LiveDeployment", "ENVIRONMENT"),
        tuple("views", "COMPONENT_TERM"), tuple("views", "CONTAINER_TERM"), tuple("views", "INFRASTRUCTURE_TERM"),
        tuple("views", "NODE_TERM"), tuple("views", "RELATIONSHIP_STYLE_PROPERTY"),
        tuple("views", "RELATIONSHIP_STYLE_TAG"), tuple("views", "RELATIONSHIP_TERM"), tuple("views", "STYLE_PROPERTY"),
        tuple("views", "STYLE_TAG"), tuple("views", "SYSTEM_TERM"), tuple("views", "TERM"),
        tuple("views", "VIEWS_PROPERTY"),
        tuple("workspace", "WORKSPACE_DESCRIPTION"), tuple("workspace", "WORKSPACE_NAME"),
        tuple("workspace", "WORKSPACE_PROPERTY")};

    /**
     * A ${NAME} in every place of the workspace that the restricted DSL lets a model write text into. The tests below
     * take the names from this text, so a place added here must be found by both checks.
     */
    private static final String EVERY_PLACE = """
            workspace "${WORKSPACE_NAME}" "${WORKSPACE_DESCRIPTION}" {
                !identifiers flat
                !impliedRelationships false
                properties {
                    "owner" "${WORKSPACE_PROPERTY}"
                }
                model {
                    properties {
                        "m" "${MODEL_PROPERTY}"
                    }
                    shop = softwareSystem "Shop" "Sells ${SYSTEM_DESCRIPTION}" {
                        app = container "App" "" "${TECH}" "${TAG}" {
                            properties {
                                "contract.root" "fx.shop"
                                "${KEY}" "${VALUE}"
                            }
                            group "${GROUP}" {
                                orders = component "Orders" "" "${COMPONENT_TECH}" {
                                    properties {
                                        "contract.id" "orders"
                                        "contract.packages" "fx.shop.orders"
                                        "contract.status" "built"
                                    }
                                    perspectives {
                                        "Security ${PERSPECTIVE_NAME}" "${PERSPECTIVE_TEXT}" "${PERSPECTIVE_VALUE}"
                                        perspective "Cost" {
                                            description "${PERSPECTIVE_BLOCK}"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    clerk = person "Clerk ${PERSON_NAME}"
                    custom = element "Custom" "${METADATA}" "Custom ${CUSTOM_DESCRIPTION}"
                    clerk -> app "Uses ${RELATIONSHIP}" "${PROTOCOL}" "${RELATIONSHIP_TAG}" {
                        properties {
                            "note" "${NOTE}"
                        }
                    }
                    live = deploymentEnvironment "Live ${ENVIRONMENT}" {
                        cluster = deploymentGroup "Group ${DEPLOYMENT_GROUP}"
                        server = deploymentNode "Server" "" "${OS}" {
                            gateway = infrastructureNode "Gateway" "" "${PROXY}"
                            appInstance = containerInstance app cluster {
                                healthCheck "Check ${HEALTH_CHECK}" "https://example.com/health"
                            }
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
                    filtered "Components" include "Tag ${FILTER_TAG}" "Filtered"
                    dynamic shop "Flow" "Flow ${DYNAMIC_DESCRIPTION}" {
                        ${STEP_ORDER}: clerk -> app "Asks ${STEP}"
                        clerk -> app {
                            properties {
                                "p" "${STEP_PROPERTY}"
                            }
                        }
                    }
                    deployment * "Live ${ENVIRONMENT}" "LiveDeployment" {
                        include *
                    }
                    styles {
                        element "${STYLE_TAG}" {
                            color #000000
                            properties {
                                "p" "${STYLE_PROPERTY}"
                            }
                        }
                        relationship "${RELATIONSHIP_STYLE_TAG}" {
                            color #000000
                            properties {
                                "p" "${RELATIONSHIP_STYLE_PROPERTY}"
                            }
                        }
                    }
                    properties {
                        "set" "${VIEWS_PROPERTY}"
                    }
                    terminology {
                        person "${TERM}"
                        softwareSystem "${SYSTEM_TERM}"
                        container "${CONTAINER_TERM}"
                        component "${COMPONENT_TERM}"
                        deploymentNode "${NODE_TERM}"
                        infrastructureNode "${INFRASTRUCTURE_TERM}"
                        relationship "${RELATIONSHIP_TERM}"
                    }
                }
                configuration {
                    users {
                        "${USER}" read
                    }
                }
            }
            """;

    @Test
    void anUnresolvedReferenceAnywhereInTheFileIsAProblem(@TempDir Path dir) throws IOException {
        List<String> lines = EVERY_PLACE.lines().toList();
        List<String> expected = java.util.stream.IntStream.range(0, lines.size()).boxed()
                .flatMap(index -> REFERENCE.matcher(lines.get(index)).results()
                        .map(reference -> unresolvedAt(index + 1, reference.group(1))))
                .toList();

        assertThat(expected).hasSizeGreaterThan(40);
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, EVERY_PLACE), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactlyInAnyOrderElementsOf(expected));
    }

    @Test
    void afterParsingEveryStringTheWorkspaceHoldsIsCheckedToo(@TempDir Path dir) throws Exception {
        ParsedModel parsed = RestrictedDslParser.parse(write(dir, EVERY_PLACE), "workspace.dsl", new Problems());

        List<ParsedStrings.Reference> found =
                ParsedStrings.unresolved(parsed.workspace(), new ElementNames(parsed.identifiers()));

        // Every name the fixture writes is found somewhere: a place the walk skips fails here.
        assertThat(found.stream().map(ParsedStrings.Reference::name).distinct().sorted().toList())
                .isEqualTo(REFERENCE.matcher(EVERY_PLACE).results().map(reference -> reference.group(1)).distinct()
                        .sorted().toList());
        assertThat(found).extracting(ParsedStrings.Reference::place, ParsedStrings.Reference::name)
                .containsExactly(EVERY_PLACE_FOUND);
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
    void aReferenceSplitByALineContinuationIsOneReference(@TempDir Path dir) throws Exception {
        // StructurizrDslParser joins a line that ends in a backslash with the next one, without its leading spaces.
        String dsl = VALID.replace("orders = component \"Orders\"",
                "orders = component \"Orders\" \"Reads ${HO\\\n        ME}\"");

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Reads ${HOME}");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolvedAt(lineOf(dsl, "${HO"), "HOME")));
    }

    @Test
    void aCommentLineThatContinuesALineIsPartOfIt(@TempDir Path dir) throws Exception {
        String dsl = VALID.replace("orders = component \"Orders\"",
                "orders = component \"Orders\" \"Reads \\\n        // ${JOINED}\"");

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Reads // ${JOINED}");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "JOINED")));
    }

    @Test
    void aDefinitionInATextBlockIsTextAndDefinesNothing(@TempDir Path dir) throws Exception {
        String dsl = VALID.replace("orders = component \"Orders\" {", """
                orders = component "Orders" {
                    description ""\"
                        !const HOME safe
                        ""\"
                    technology "Reads ${HOME}"
                """);

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("!const HOME safe");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "HOME")));
    }

    @Test
    void textBeforeTheClosingMarkerOfATextBlockIsDropped(@TempDir Path dir) throws Exception {
        // The line after the block is a line of its own again: its !const defines AFTER.
        String dsl = VALID.replace("orders = component \"Orders\" {", """
                orders = component "Orders" {
                    description ""\"
                        Reads orders
                        and ${DROPPED}""\"
                    !const AFTER "fx"
                    technology "${AFTER}"
                """);

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Reads orders");
        assertThat(ContractModelReader.read(write(dir, dsl), "workspace.dsl").components()).hasSize(1);
    }

    @Test
    void aSecondMarkerOnTheOpeningLineEndsTheBlockThere(@TempDir Path dir) throws Exception {
        // The parser keeps the text between the first two markers without its last character, and drops the rest:
        // the tags of Clerk end in "Rest ${CUT", and ${LOST} is gone.
        String dsl = VALID.replace("// MODEL", """
                clerk = person "Clerk" "Reads ${OPENING}" ""\"Rest ${CUT}""\"
                    ${LOST}
                    ""\"
                """);

        assertThat(parsedElement(dir, dsl, "Clerk").getTags()).endsWith(",Rest ${CUT");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "OPENING")));
    }

    @Test
    void aCommentLineThatEndsInABackslashDoesNotContinue(@TempDir Path dir) throws Exception {
        String dsl = VALID.replace("!identifiers flat", "!identifiers flat\n    // a comment \\\n    !const NAME \"x\"")
                .replace("orders = component \"Orders\" {", "orders = component \"Orders\" \"Reads ${NAME}\" {");

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Reads x");
        assertThat(ContractModelReader.read(write(dir, dsl), "workspace.dsl").components()).hasSize(1);
    }

    @Test
    void aCommentLineWithTextBlockMarkersOpensNoTextBlock(@TempDir Path dir) throws Exception {
        String dsl = VALID.replace("!identifiers flat",
                        "!identifiers flat\n    // a comment with \"\"\" markers \"\"\"\n    !const NAME \"x\"")
                .replace("orders = component \"Orders\" {", "orders = component \"Orders\" \"Reads ${NAME}\" {");

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Reads x");
        assertThat(ContractModelReader.read(write(dir, dsl), "workspace.dsl").components()).hasSize(1);
    }

    @Test
    void aVariableDefinesItsNameToo(@TempDir Path dir) throws Exception {
        String dsl = VALID.replace("!identifiers flat", "!identifiers flat\n    !var NAME \"x\"")
                .replace("orders = component \"Orders\" {", "orders = component \"Orders\" \"Reads ${NAME}\" {");

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Reads x");
        assertThat(ContractModelReader.read(write(dir, dsl), "workspace.dsl").components()).hasSize(1);
    }

    @Test
    void aReferenceThatOnlySubstitutionPutsTogetherIsCaughtInTheParsedStrings(@TempDir Path dir) throws Exception {
        // The file holds ${DOLLAR}{HOME}, which refers to DOLLAR only. The parser makes it ${HOME} and does not
        // substitute that again, so only the check of the parsed strings can see it.
        String dsl = VALID.replace("!identifiers flat", "!identifiers flat\n    !const DOLLAR \"$\"")
                .replace("orders = component \"Orders\" {",
                        "orders = component \"Orders\" \"Reads ${DOLLAR}{HOME}\" {");

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Reads ${HOME}");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly("orders: ${HOME} is not substituted: environment variables are not read, "
                                + "and nothing in the model defines HOME"));
    }

    /** A perspectives block on orders with a line that reads like a definition: the parser makes it a perspective. */
    private static final String FAKE_DEFINITION = """
            orders = component "Orders" {
                perspectives {
                    !const HIDDEN x
                }
            """;

    @Test
    void aDefinitionInAPerspectivesBlockHidesNoReferenceInADeploymentEnvironmentName(@TempDir Path dir)
            throws Exception {
        String dsl = VALID.replace("orders = component \"Orders\" {", FAKE_DEFINITION).replace("// MODEL", """
                deploymentEnvironment "Live ${HIDDEN}" {
                    deploymentNode "Server"
                }
                """);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "HIDDEN")));
    }

    @Test
    void aDefinitionInAPerspectivesBlockHidesNoReferenceInTheTagsOfAFilteredView(@TempDir Path dir)
            throws Exception {
        String dsl = VALID.replace("orders = component \"Orders\" {", FAKE_DEFINITION).replace("// VIEWS", """
                component app "Components" {
                    include *
                }
                filtered "Components" include "Tag ${HIDDEN}" "Filtered"
                """);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "HIDDEN")));
    }

    @Test
    void onlyALineThatTheParserReadsAsADirectiveDefinesAName(@TempDir Path dir) throws Exception {
        // In perspectives { } each line is a perspective, unless it opens a perspective block (perspective <name> {),
        // whose lines are directives again. In properties { } each line is a property. After a block, they are
        // directives too.
        String dsl = VALID.replace("orders = component \"Orders\" {", """
                orders = component "Orders" {
                    perspectives {
                        perspective "Security" {
                            !const INSIDE "x"
                            description "${INSIDE}"
                        }
                        perspective "Looks" "like an opener, but has no brace"
                        !const HIDDEN x
                    }
                    properties {
                        !const PROPERTY
                    }
                    !const AFTER "y"
                    description "${HIDDEN} ${PROPERTY} ${AFTER}"
                """);

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("${HIDDEN} ${PROPERTY} y");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "HIDDEN"), unresolved(dsl, "PROPERTY")));
    }

    /** Lines that start with properties and look like relationships, where StructurizrDslParser opens the block. */
    static Stream<Arguments> relationshipShapedLinesThatOpenAPropertiesBlock() {
        return Stream.of(
                // The workspace has no relationships, so the line opens the workspace's properties.
                arguments("-> at workspace scope", "// WORKSPACE", "properties -> ignored {"),
                // No archetype is named missing, so this is no relationship but the model's properties.
                arguments("an unknown archetype in the model", "// MODEL", "properties --missing-> app {"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("relationshipShapedLinesThatOpenAPropertiesBlock")
    void aDefinitionInAPropertiesBlockOpenedByARelationshipShapedLineDefinesNothing(String input, String slot,
            String opener, @TempDir Path dir) throws IOException {
        // !const HIDDEN is a property. The constant that holds ${HIDDEN} is never used, so the reference reaches no
        // parsed string, and only the source scan can see it.
        String dsl = VALID.replace(slot, opener + """

                    !const HIDDEN
                }
                !const UNUSED "${HIDDEN}"
                """);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "HIDDEN")));
    }

    @Test
    void aLineOfDataThatEndsWithABraceKeepsTheScanInTheBlockAKnownFalseRejection(@TempDir Path dir)
            throws Exception {
        // A known limitation, safe by design (docs/gaps.md, section 5): the scan counts the perspective whose value
        // is a brace as a nested block, so it reads the block's closing brace as closing that, and !const AFTER as
        // data. Structurizr substitutes ${AFTER}; the check rejects it.
        String dsl = VALID.replace("orders = component \"Orders\" {", """
                orders = component "Orders" {
                    perspectives {
                        "Braced" "value" "{"
                        !const HIDDEN x
                    }
                    !const AFTER "y"
                    description "${HIDDEN} ${AFTER}"
                """);

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("${HIDDEN} y");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "AFTER"), unresolved(dsl, "HIDDEN")));
    }

    /**
     * Lines that StructurizrDslParser reads only after substituting them: a block keyword, a comment opener or an
     * identifier assignment that a constant or a text block produces. The source scan reads the tokens as written, so
     * for it HIDDEN is defined; for the parser it is a property, or in a comment.
     */
    static Stream<Arguments> definitionsThatOnlyTheSubstitutedLineHides() {
        return Stream.of(
                arguments("a block keyword from a constant",
                        "!const P \"properties\"\n${P} {\n    !const HIDDEN\n}\n"),
                arguments("a comment opener from a constant",
                        "!const C \"/*\"\n${C} a comment, for the parser\n!const HIDDEN \"x\"\n*/\n"),
                arguments("an identifier assignment from a constant",
                        "!const EQ \"=\"\nx ${EQ} properties {\n    !const HIDDEN\n}\n"),
                arguments("a block keyword from a text block",
                        "\"\"\"\nproperties\n\"\"\"\n    !const HIDDEN\n}\n"),
                arguments("a comment opener from a text block",
                        "\"\"\"\n/*\n\"\"\"\n!const HIDDEN \"x\"\n*/\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("definitionsThatOnlyTheSubstitutedLineHides")
    void aConstantThatStillHoldsAReferenceAfterParsingIsAProblem(String input, String lines, @TempDir Path dir)
            throws IOException {
        // Nothing uses UNUSED, so its ${HIDDEN} reaches no parsed string; only the value the parser gave it shows it.
        String dsl = VALID.replace("// MODEL", lines + "!const UNUSED \"${HIDDEN}\"\n");

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly("!const UNUSED: ${HIDDEN} is not substituted: environment variables are not "
                                + "read, and nothing in the model defines HIDDEN"));
    }

    /** Makes HIDDEN look defined to the source scan only: the parser reads !const HIDDEN as a property. */
    private static final String HIDDEN_FOR_THE_SCAN = "!const P \"properties\"\n${P} {\n    !const HIDDEN\n}\n";

    static Stream<Arguments> declarationsThatHoldTheReference() {
        return Stream.of(
                arguments("a variable", "!var LEFT \"${HIDDEN}\"\n", "!var LEFT"),
                arguments("a declaration that a line continuation splits", "!const \\\n    LEFT \"${HIDDEN}\"\n",
                        "!const LEFT"),
                arguments("a declaration whose keyword a constant produces",
                        "!const K \"!const\"\n${K} LEFT \"${HIDDEN}\"\n", "!const or !var LEFT"),
                arguments("a declaration whose name a constant produces",
                        "!const N \"LEFT\"\n!const ${N} \"${HIDDEN}\"\n", "!const or !var LEFT"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("declarationsThatHoldTheReference")
    void everyDeclaredConstantOrVariableIsAskedForItsValue(String input, String declaration, String place,
            @TempDir Path dir) throws IOException {
        String dsl = VALID.replace("// MODEL", HIDDEN_FOR_THE_SCAN + declaration);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(place + ": ${HIDDEN} is not substituted: environment variables are not "
                                + "read, and nothing in the model defines HIDDEN"));
    }

    @Test
    void aBraceInsideABlockThatTheScanTakesForDataCannotCloseItEarly(@TempDir Path dir) throws IOException {
        // The scan opens a properties block at the relationship below, which StructurizrDslParser does not. The
        // braces of the blocks after it belong to the parser's blocks; were the first closing brace to end the
        // scan's block, the scan would read the !const in the perspectives block as a directive, which the parser
        // reads as a perspective.
        String dsl = VALID.replace("// MODEL", """
                properties = person "Named like the keyword"
                properties -> app "Uses"
                other = person "Other" {
                    perspectives {
                        perspective "Inner" {
                            description "d"
                        }
                        !const HIDDEN x
                    }
                }
                !const UNUSED "${HIDDEN}"
                """);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "HIDDEN")));
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"properties", "perspectives"})
    void aRelationshipFromAnElementNamedLikeABlockKeywordIsAKnownFalseRejection(String identifier,
            @TempDir Path dir) throws Exception {
        // A known limitation (docs/gaps.md, section 5), safe by design: the scan does not follow scopes, so it reads
        // this relationship as the start of a block and the !const after it as data. Structurizr substitutes ${OK};
        // the check rejects it. Renaming the identifier avoids it.
        String dsl = VALID.replace("// MODEL", """
                %1$s = person "Named like the keyword"
                %1$s -> app "Uses"
                !const OK "yes"
                reader = person "Reader" "Reads ${OK}"
                """.formatted(identifier));

        assertThat(parsedElement(dir, dsl, "Reader").getDescription()).isEqualTo("Reads yes");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "OK")));
    }

    @Test
    void anEscapedQuoteInAPerspectiveNameIsPartOfTheName(@TempDir Path dir) throws Exception {
        // As StructurizrDslParser's Tokenizer reads it, the line has three tokens and opens a perspective block.
        String dsl = VALID.replace("orders = component \"Orders\" {", """
                orders = component "Orders" {
                    perspectives {
                        perspective "A \\"quoted\\" name" {
                            !const QUOTED "x"
                            description "${QUOTED}"
                        }
                    }
                """);

        assertThat(parsedElement(dir, dsl, "Orders").getPerspectives())
                .extracting(com.structurizr.model.Perspective::getDescription).containsExactly("x");
        assertThat(ContractModelReader.read(write(dir, dsl), "workspace.dsl").components()).hasSize(1);
    }

    /** The other blocks that read each line as data: there, a !const or !var line does not parse at all. */
    static Stream<Arguments> blocksWhoseLinesCannotBeDefinitions() {
        return Stream.of(
                arguments("a dynamic view", "// VIEWS", "dynamic shop \"Flow\" {\n    !const A b\n}"),
                arguments("a parallel sequence", "// VIEWS",
                        "dynamic shop \"Flow\" {\n    {\n        !var A b\n    }\n}"),
                arguments("an animation", "// VIEWS",
                        "component app \"Components\" {\n    include *\n    animation {\n        !const A b\n    }\n}"),
                arguments("the users", "// WORKSPACE", "configuration {\n    users {\n        !const A b\n    }\n}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blocksWhoseLinesCannotBeDefinitions")
    void aDefinitionInAnyOtherBlockThatReadsLinesAsDataDoesNotParse(String block, String slot, String lines,
            @TempDir Path dir) throws IOException {
        Path model = write(dir, VALID.replace(slot, lines));

        assertThatThrownBy(() -> ContractModelReader.read(model, "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .singleElement(STRING).contains(" at line ").contains(" of workspace.dsl: "));
    }

    @Test
    void aDefinitionAfterAnIdentifierAssignmentStillDefines(@TempDir Path dir) throws Exception {
        // The parser drops "x =" from a line before it reads the keyword, so this line is a !const.
        String dsl = VALID.replace("!identifiers flat", "!identifiers flat\n    x = !const NAME \"Orders\"")
                .replace("orders = component \"Orders\" {", "orders = component \"Orders\" \"Reads ${NAME}\" {");

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Reads Orders");
        assertThat(ContractModelReader.read(write(dir, dsl), "workspace.dsl").components()).hasSize(1);
    }

    @Test
    void blockCommentsNestAsTheParserNestsThem(@TempDir Path dir) throws Exception {
        String dsl = VALID.replace("// MODEL", """
                /*
                /* inner
                */
                ${STILL_IN_THE_OUTER_COMMENT}
                */
                clerk = person "Clerk" "${AFTER_THE_COMMENT}"
                """);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(unresolved(dsl, "AFTER_THE_COMMENT")));
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
        return unresolvedAt(lineOf(dsl, "${" + name + "}"), name);
    }

    private static String unresolvedAt(int line, String name) {
        return "workspace.dsl:" + line + ": ${" + name + "} is not substituted: environment variables are not read, "
                + "and no !const or !var defines " + name + " before this line";
    }

    /** What the parser made of the description of the component Orders. */
    private static String descriptionOfOrders(Path dir, String dsl) throws Exception {
        return parsedElement(dir, dsl, "Orders").getDescription();
    }

    private static Element parsedElement(Path dir, String dsl, String name) throws Exception {
        ParsedModel parsed = RestrictedDslParser.parse(write(dir, dsl), "workspace.dsl", new Problems());
        return parsed.workspace().getModel().getElements().stream()
                .filter(element -> element.getName().equals(name))
                .findFirst()
                .orElseThrow();
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
    void theProblemsSurviveSerialization(@TempDir Path dir) throws Exception {
        Path model = write(dir, "workspace {\n    model {\n    }\n}\n");
        InvalidModelException thrown =
                catchThrowableOfType(InvalidModelException.class, () -> ContractModelReader.read(model, "workspace.dsl"));

        InvalidModelException copy = Serialization.roundTrip(thrown, InvalidModelException.class);

        assertThat(copy.problems()).containsExactly("model: no container has contract.root; exactly one must");
        assertThat(copy).hasMessage(thrown.getMessage());
        assertThatThrownBy(() -> copy.problems().set(0, "changed")).isInstanceOf(UnsupportedOperationException.class);
    }

    /** Inputs that make StructurizrDslParser fail while it joins lines and text blocks, before it names a line. */
    static Stream<Arguments> filesThePreprocessingCannotRead() {
        return Stream.of(
                arguments("a line comment that ends with a text block marker",
                        VALID.replace("// MODEL", "// see \"\"\""), "java.lang.ArrayIndexOutOfBoundsException"),
                arguments("a text block line indented less than the first one", VALID.replace("// MODEL", """
                        clerk = person "Clerk" {
                            description ""\"
                                a long first line
                          x
                                ""\"
                        }"""), "java.lang.IndexOutOfBoundsException"),
                arguments("an empty text block", VALID.replace("// MODEL", """
                        clerk = person "Clerk" {
                            description ""\"
                            ""\"
                        }"""), "java.lang.ArrayIndexOutOfBoundsException"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filesThePreprocessingCannotRead")
    void aFileThePreprocessingCannotReadIsAModelProblem(String input, String dsl, String exception, @TempDir Path dir)
            throws IOException {
        Path model = write(dir, dsl);

        assertThatThrownBy(() -> ContractModelReader.read(model, "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems()).containsExactly(
                        "workspace.dsl: the Structurizr DSL parser stopped with " + exception
                                + " and named no line: it cannot read a construct in this file"));
        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> assertThat(e.problems()).singleElement(STRING)
                        .endsWith("workspace.dsl: the Structurizr DSL parser stopped with " + exception
                                + " and named no line: it cannot read a construct in this file"));
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
