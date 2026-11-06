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
        Path model = write(dir, VALID.replace("orders = component \"Orders\"",
                "orders = component \"Orders\" \"Reads ${PATH} and ${HOME}.\""));

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> {
                    assertThat(e.problems()).containsExactly(
                            "orders: ${HOME} is not substituted: environment variables are not read, and the model "
                                    + "defines no constant HOME",
                            "orders: ${PATH} is not substituted: environment variables are not read, and the model "
                                    + "defines no constant PATH");
                    assertThat(e.getMessage()).doesNotContain(value);
                });
    }

    @Test
    void anUnresolvedReferenceAnywhereInTheModelIsAProblem(@TempDir Path dir) throws IOException {
        Path model = write(dir, VALID
                .replace("app = container \"App\" {", "app = container \"App\" \"\" \"${TECH}\" \"${TAG}\" {")
                .replace("\"contract.root\" \"fx.shop\"", "\"contract.root\" \"fx.shop\"\n \"${KEY}\" \"${VALUE}\"")
                .replace("// MODEL", """
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
                        }"""));

        assertThatThrownBy(() -> Conformance.check(model, List.of()))
                .isInstanceOfSatisfying(ModelException.class, e -> assertThat(e.problems())
                        .extracting(problem -> problem.substring(0, problem.indexOf(" is not substituted")))
                        .containsExactly("app: ${KEY}", "app: ${TAG}", "app: ${TECH}", "app: ${VALUE}",
                                "clerk -> app: ${NOTE}", "clerk -> app: ${PROTOCOL}", "clerk -> app: ${RELATIONSHIP}",
                                "gateway: ${PROXY}", "server: ${OS}"));
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

    @Test
    void impliedRelationshipsAreIgnoredWhateverTheFileSays(@TempDir Path dir) throws Exception {
        // The file switches implied relationships on and then gives the implied app -> db relationship a contract
        // property. Were implied relationships part of the model, contract.mode on a container relationship would be
        // rejected; they are not, so the model is valid and only the written relationships count.
        Path model = write(dir, VALID
                .replace("!impliedRelationships false", "!impliedRelationships true")
                .replace("// MODEL", """
                        !element shop {
                            db = container "Database" {
                                tables = component "Tables"
                            }
                        }
                        orders -> tables "Reads"
                        !relationships "app->db" {
                            properties {
                                "contract.mode" "REQUIRED"
                            }
                        }"""));

        ContractModel contract = ContractModelReader.read(model, "workspace.dsl");

        assertThat(contract.components()).extracting(ContractComponent::id).containsExactly("orders");
        assertThat(contract.relationships()).isEmpty();
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
