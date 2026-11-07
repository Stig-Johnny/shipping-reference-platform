package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.model.Element;
import com.structurizr.model.GroupableElement;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.ModelException;
import io.github.stigjohnny.c4contractkit.testing.Serialization;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
                    assertThat(e.problems()).singleElement(STRING).endsWith(substitutionAt(lineOf(dsl, "${PATH}")));
                    assertThat(e.getMessage()).doesNotContain(value);
                });
    }

    @Test
    void theParserItselfReadsNoEnvironmentVariable(@TempDir Path dir) throws Exception {
        // The check before parsing keeps ${PATH} from the parser. Were that check gone, the parser that the factory
        // creates would still keep the text as it is (security/reviews/maven--structurizr-dsl--6.2.4.md).
        assertThat(System.getenv("PATH")).as("the test needs PATH in its environment").isNotBlank();
        StructurizrDslParser parser = RestrictedDslParser.newParser();

        parser.parse(write(dir, VALID.replace("orders = component \"Orders\"",
                "orders = component \"Orders\" \"Reads ${PATH}\"")).toFile());

        assertThat(parser.getWorkspace().getModel().getElements())
                .filteredOn(element -> element.getName().equals("Orders"))
                .singleElement()
                .extracting(Element::getDescription)
                .isEqualTo("Reads ${PATH}");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"!const", "!var"})
    void aDefinitionAndItsUseAreBothProblems(String keyword, @TempDir Path dir) throws IOException {
        // Structurizr would substitute ${ROOT}: the restricted DSL has neither constants nor variables.
        String dsl = VALID
                .replace("!identifiers flat", "!identifiers flat\n    " + keyword + " ROOT \"fx.shop\"")
                .replace("\"contract.root\" \"fx.shop\"", "\"contract.root\" \"${ROOT}\"");

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactlyInAnyOrder(directiveAt(lineOf(dsl, keyword + " ROOT"), keyword),
                                substitutionAt(lineOf(dsl, "${ROOT}"))));
    }

    /**
     * Codex's seven files that the previous version accepted, since no check after parsing saw their reference. Each
     * opens with the same four lines: the parser defines C as the start of a block comment, so the second line opens
     * one and the third defines nothing, while the earlier source scan, which did not substitute, took HIDDEN as
     * defined.
     *
     * @return the case, the file, its lines that hold !const and its lines that hold ${
     */
    static Stream<Arguments> referencesThatNoCheckAfterParsingSees() {
        return Stream.of(
                arguments("an unused archetype", """
                        !const C /*
                        ${C}
                        !const HIDDEN x
                        */
                        workspace {
                            model {
                                softwareSystem S {
                                    container C {
                                        properties {
                                            contract.root x
                                        }
                                    }
                                }
                                archetypes {
                                    unused = person {
                                        description "${HIDDEN}"
                                    }
                                }
                            }
                        }
                        """, List.of(1, 3), List.of(2, 16)),
                arguments("an overwritten description", """
                        !const C /*
                        ${C}
                        !const HIDDEN x
                        */
                        workspace {
                            description "${HIDDEN}"
                            description ok
                            model {
                                softwareSystem S {
                                    container C {
                                        properties {
                                            contract.root x
                                        }
                                    }
                                }
                            }
                        }
                        """, List.of(1, 3), List.of(2, 6)),
                arguments("an empty group", """
                        !const C /*
                        ${C}
                        !const HIDDEN x
                        */
                        workspace {
                            model {
                                softwareSystem S {
                                    container C {
                                        properties {
                                            contract.root x
                                        }
                                    }
                                }
                                group "${HIDDEN}" {
                                }
                            }
                        }
                        """, List.of(1, 3), List.of(2, 14)),
                arguments("an empty deployment environment", """
                        !const C /*
                        ${C}
                        !const HIDDEN x
                        */
                        workspace {
                            model {
                                softwareSystem S {
                                    container C {
                                        properties {
                                            contract.root x
                                        }
                                    }
                                }
                                deploymentEnvironment "${HIDDEN}" {
                                }
                            }
                        }
                        """, List.of(1, 3), List.of(2, 14)),
                arguments("an argument that a block opener ignores", """
                        !const C /*
                        ${C}
                        !const HIDDEN x
                        */
                        workspace {
                            properties ${HIDDEN} {
                            }
                            model {
                                softwareSystem S {
                                    container C {
                                        properties {
                                            contract.root x
                                        }
                                    }
                                }
                            }
                        }
                        """, List.of(1, 3), List.of(2, 6)),
                arguments("a view's selection expression", """
                        !const C /*
                        ${C}
                        !const HIDDEN x
                        */
                        workspace {
                            model {
                                softwareSystem S {
                                    container C {
                                        properties {
                                            contract.root x
                                        }
                                    }
                                }
                            }
                            views {
                                systemLandscape v {
                                    include element.tag==${HIDDEN}
                                }
                            }
                        }
                        """, List.of(1, 3), List.of(2, 17)),
                arguments("a declaration name that a text block truncates", """
                        !const C /*
                        ${C}
                        !const HIDDEN x
                        */
                        !const N ""\"UNUSEDX""\"
                        ""\"
                        !const ${N} "${HIDDEN}"
                        workspace {
                            model {
                                softwareSystem S {
                                    container C {
                                        properties {
                                            contract.root x
                                        }
                                    }
                                }
                            }
                        }
                        """, List.of(1, 3, 5, 7), List.of(2, 7)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("referencesThatNoCheckAfterParsingSees")
    void aReferenceThatNoCheckAfterParsingSeesIsRejectedBeforeParsing(String input, String dsl,
            List<Integer> constants, List<Integer> references, @TempDir Path dir) throws IOException {
        List<String> expected = Stream.concat(constants.stream().map(line -> directiveAt(line, "!const")),
                references.stream().map(RestrictedDslParserTest::substitutionAt)).toList();

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactlyInAnyOrderElementsOf(expected));
    }

    static Stream<Arguments> lineEnds() {
        return Stream.of(arguments("LF", "\n"), arguments("CRLF", "\r\n"), arguments("CR", "\r"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lineEnds")
    void aLineContinuationIsAProblemAlsoWhereNoLineHoldsAConstantOrAReference(String input, String lineEnd,
            @TempDir Path dir) throws Exception {
        // Codex: the parser joins a line that ends in a backslash to the next one without a separator, so !con\ and
        // st define N, and $\ and {N} use it, although no line holds !const or ${.
        String dsl = ("!con\\\nst N \"foo\"\n"
                + VALID.replace("workspace {\n", "workspace {\n    description \"$\\\n{N}\"\n")).replace("\n", lineEnd);
        StructurizrDslParser parser = RestrictedDslParser.newParser();
        parser.parse(write(dir, dsl).toFile());

        assertThat(parser.getWorkspace().getDescription()).as("what the parser makes of the file").isEqualTo("foo");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactlyInAnyOrder(continuationAt(1), continuationAt(4)));
    }

    /** Codex: with $ as the group separator, the parser names the inner group: outer, then $, then its own name. */
    private static final String NESTED_GROUPS = """
            properties {
                "structurizr.groupSeparator" "$"
            }
            group "outer" {
                group "%s" {
                    clerk = person "Clerk"
                }
            }
            """;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{X}", "{", "{x y}"})
    void aDollarBraceThatOnlyTheParserPutsTogetherIsAProblem(String inner, @TempDir Path dir) throws Exception {
        // No line of the file holds ${, so only the check after parsing sees it. It rejects any ${, with a name or
        // without one.
        String dsl = VALID.replace("// MODEL", NESTED_GROUPS.formatted(inner));

        assertThat(parsedElement(dir, dsl, "Clerk")).isInstanceOfSatisfying(GroupableElement.class,
                clerk -> assertThat(clerk.getGroup()).isEqualTo("outer$" + inner));
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(assembled("clerk")));
    }

    @Test
    void aKeywordThatOnlyUnicodeCaseFoldingMatchesIsAProblem(@TempDir Path dir) throws Exception {
        // Codex: the parser compares its keywords with String.equalsIgnoreCase, which folds the long s (U+017F) to
        // s, so this line defines N. Matching only ASCII letters case-insensitively would miss it.
        String dsl = "!con\u017Ft N \"foo\"\n" + VALID;
        StructurizrDslParser parser = RestrictedDslParser.newParser();
        parser.parse(write(dir, dsl).toFile());

        assertThat(parser.getConstant("N")).as("what the parser makes of the file").isEqualTo("foo");
        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(directiveAt(1, "!con\u017Ft")));
    }

    static Stream<Arguments> constantsAndVariablesInAnyLetterCase() {
        return Stream.of(
                arguments("in upper case", "!CONST N \"foo\"", "!CONST"),
                arguments("in mixed case", "!vAr N \"foo\"", "!vAr"),
                arguments("inside a word", "clerk = person \"Clerk\" \"Reads x!constants\"", "!const"),
                arguments("as the start of a longer word", "clerk = person \"Clerk\" \"!variables\"", "!var"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constantsAndVariablesInAnyLetterCase")
    void aConstantOrAVariableIsAProblemInAnyLetterCaseAndInsideAWord(String input, String line, String keyword,
            @TempDir Path dir) throws IOException {
        String dsl = VALID.replace("// MODEL", line);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(directiveAt(lineOf(dsl, line), keyword)));
    }

    /**
     * Text that would change nothing, and is rejected all the same: the cost of reading the file as text.
     *
     * @return the case, the lines that replace the MODEL comment, a fragment of the line with the problem, and the
     *     problem at that line
     */
    static Stream<Arguments> harmlessTextThatIsRejectedToo() {
        IntFunction<String> substitution = RestrictedDslParserTest::substitutionAt;
        IntFunction<String> constant = line -> directiveAt(line, "!const");
        IntFunction<String> variable = line -> directiveAt(line, "!var");
        IntFunction<String> continuation = RestrictedDslParserTest::continuationAt;
        return Stream.of(
                arguments("${ in a line comment", "// see ${HOME}", "${HOME}", substitution),
                arguments("${ in a hash comment", "# ${HOME} is never read", "${HOME}", substitution),
                arguments("${ in a block comment", "/* ${HOME} */", "${HOME}", substitution),
                arguments("${ in a text block", """
                        clerk = person "Clerk" {
                            description ""\"
                                costs ${
                                ""\"
                        }""", "costs ${", substitution),
                arguments("${ without a name or a closing brace", "clerk = person \"Clerk\" \"pays ${ 5\"", "pays ${",
                        substitution),
                arguments("!const in a comment", "// !const is left out", "!const", constant),
                arguments("!var in a block comment", "/* no !var here */", "!var", variable),
                arguments("a comment that ends in a backslash", "// C:\\", "C:\\", continuation));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("harmlessTextThatIsRejectedToo")
    void harmlessTextThatHoldsOneOfTheseIsRejectedToo(String input, String lines, String fragment,
            IntFunction<String> problem, @TempDir Path dir) throws IOException {
        String dsl = VALID.replace("// MODEL", lines);

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(problem.apply(lineOf(dsl, fragment))));
    }

    @Test
    void everyConstructOnALineIsAProblemOfItsOwn(@TempDir Path dir) throws IOException {
        // A construct that the line holds twice is one problem.
        String dsl = VALID.replace("// MODEL", "// !const ${A} !VAR ${B} !const \\");
        int line = lineOf(dsl, "${A}");

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactlyInAnyOrder(directiveAt(line, "!const"), directiveAt(line, "!VAR"),
                                substitutionAt(line), continuationAt(line)));
    }

    @Test
    void aFileThatHoldsAnyOfTheseIsNotParsed(@TempDir Path dir) throws IOException {
        // The parser does not run, so the syntax error on the line of orders is not reported.
        String dsl = VALID.replace("orders = component", "orders = componnt").replace("// MODEL", "// ${HOME}");

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(substitutionAt(lineOf(dsl, "${HOME}"))));
    }

    @Test
    void lookalikesOfTheSyntaxAreText(@TempDir Path dir) throws Exception {
        // Neither the parser nor the check normalises Unicode or decodes an escape: fullwidth forms and the text of
        // an escape are not syntax. A line ends in a backslash only when a backslash is its last character.
        String description = "\uFF04\uFF5BHOME\uFF5D, \\u0024{HOME} and \uFF01const";
        String dsl = VALID
                .replace("orders = component \"Orders\"", "orders = component \"Orders\" \"" + description + "\"")
                .replace("// MODEL", "// a backslash and a space: \\ \n// a fullwidth backslash: \uFF3C");

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo(description);
        assertThat(ContractModelReader.read(write(dir, dsl), "workspace.dsl").components()).hasSize(1);
    }

    @Test
    void textBlocksAreText(@TempDir Path dir) throws Exception {
        // StructurizrDslParser keeps each text block in a constant of its own and puts it back through a ${...} that
        // it writes itself: after parsing, no text of a valid model with text blocks holds ${.
        String dsl = VALID.replace("orders = component \"Orders\" {", """
                orders = component "Orders" {
                    description ""\"
                        Takes orders
                        and keeps them
                        ""\"
                """).replace("// VIEWS", """
                component app "Components" {
                    include *
                    description ""\"
                        Every component
                        ""\"
                }
                """);

        assertThat(descriptionOfOrders(dir, dsl)).isEqualTo("Takes orders\nand keeps them");
        assertThat(ContractModelReader.read(write(dir, dsl), "workspace.dsl").components()).hasSize(1);
    }

    /** What the check before parsing says about a construct the restricted DSL leaves out. */
    private static final String ANYWHERE =
            " anywhere in the file, comments and text blocks included: the restricted DSL has no ";

    private static String substitutionAt(int line) {
        return "workspace.dsl:" + line + ": ${ is not supported" + ANYWHERE + "${...} substitution";
    }

    private static String directiveAt(int line, String keyword) {
        return "workspace.dsl:" + line + ": " + keyword + " is not supported" + ANYWHERE + "constants or variables";
    }

    private static String continuationAt(int line) {
        return "workspace.dsl:" + line + ": a backslash at the end of a line is not supported" + ANYWHERE
                + "line continuations";
    }

    /** What the check after parsing says about a place with a text that holds ${. */
    private static String assembled(String place) {
        return place + ": a text holds ${ after parsing, put together from parts such as nested group names: the "
                + "restricted DSL has no ${...} substitution";
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
    void aDollarBraceAfterParsingAndABrokenModelRuleAreReportedTogether(@TempDir Path dir) throws IOException {
        String dsl = VALID
                .replace("\"contract.id\" \"orders\"", "")
                .replace("// MODEL", NESTED_GROUPS.formatted("{X}"));

        assertThatThrownBy(() -> ContractModelReader.read(write(dir, dsl), "workspace.dsl"))
                .isInstanceOfSatisfying(InvalidModelException.class, e -> assertThat(e.problems())
                        .containsExactly(assembled("clerk"), "orders: contract.id is missing"));
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
