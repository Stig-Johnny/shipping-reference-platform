package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The web vocabulary of the model (the header of architecture/workspace.dsl): exactly one container has
 * {@code web.root}; its components have {@code web.id}, {@code web.paths} and {@code web.status}, optionally
 * {@code web.entry} and {@code web.packages}; relationships between them have {@code web.mode}. The model is read
 * by the same restricted parser as the API's contract, and every rule has a fixture that breaks it.
 */
class WebModelValidationTest {

    /** A valid model; each test replaces one property or adds one construct. */
    private static final String VALID = """
            workspace {
                !identifiers flat
                !impliedRelationships false
                model {
                    shop = softwareSystem "Shop" {
                        web = container "Web" {
                            properties {
                                "web.root" "apps/web/src"
                            }
                            shell = component "Shell" {
                                properties {
                                    "web.id" "shell"
                                    "web.paths" "app"
                                    "web.status" "built"
                                    "web.packages" "react, react-dom"
                                }
                            }
                            cart = component "Cart" "" "" "Planned" {
                                properties {
                                    "web.id" "cart"
                                    "web.paths" "features/cart"
                                    "web.status" "planned"
                                    "web.entry" "features/cart/index.ts"
                                }
                            }
                            widgets = component "Widgets" {
                                properties {
                                    "web.id" "widgets"
                                    "web.paths" "ui, icons"
                                    "web.status" "built"
                                }
                            }
                        }
                        db = container "Database"
                    }
                    shell -> cart "Routes to" "" "Required import" {
                        properties {
                            "web.mode" "REQUIRED"
                        }
                    }
                    shell -> widgets "Uses" {
                        properties {
                            "web.mode" "PERMITTED"
                        }
                    }
                    cart -> widgets "Uses"
                    shell -> db "Reads nothing from, documentation only"
                    // MODEL
                }
            }
            """;

    @TempDir
    Path dir;

    @Test
    void aValidModelGivesTheWebContainersComponentsAndRelationships() throws Exception {
        WebModel model = read(VALID);

        assertThat(model.root()).isEqualTo("apps/web/src");
        assertThat(model.container()).isEqualTo("web");
        assertThat(model.components()).containsExactly(
                new WebComponent("cart", "Cart", ComponentStatus.PLANNED, List.of("features/cart"),
                        Optional.of("features/cart/index.ts"), List.of()),
                new WebComponent("shell", "Shell", ComponentStatus.BUILT, List.of("app"), Optional.empty(),
                        List.of("react", "react-dom")),
                new WebComponent("widgets", "Widgets", ComponentStatus.BUILT, List.of("icons", "ui"),
                        Optional.empty(), List.of()));
        assertThat(model.relationships()).containsExactly(
                new WebRelationship("cart", "widgets", RelationshipMode.PERMITTED),
                new WebRelationship("shell", "cart", RelationshipMode.REQUIRED),
                new WebRelationship("shell", "widgets", RelationshipMode.PERMITTED));
    }

    @Test
    void aFileBelongsToTheComponentWhosePathIsOneOfItsDirectories() throws Exception {
        WebModel model = read(VALID);

        assertThat(model.componentOf("app/main.tsx")).map(WebComponent::id).contains("shell");
        assertThat(model.componentOf("ui/forms/Button.tsx")).map(WebComponent::id).contains("widgets");
        assertThat(model.componentOf("icons/Logo.svg")).map(WebComponent::id).contains("widgets");
        assertThat(model.componentOf("features/cart/index.ts")).map(WebComponent::id).contains("cart");
        assertThat(model.componentOf("features/other/index.ts")).isEmpty();
        assertThat(model.componentOf("application/Main.tsx")).isEmpty();
        assertThat(model.componentOf("main.tsx")).isEmpty();
    }

    @Test
    void theWebVocabularyDoesNotDisturbTheApisContractCheck() throws Exception {
        String both = VALID.replace("db = container \"Database\"", """
                api = container "API" {
                    properties {
                        "contract.root" "fx.api"
                    }
                    orders = component "Orders" {
                        properties {
                            "contract.id" "orders"
                            "contract.packages" "fx.api.orders"
                            "contract.status" "built"
                        }
                    }
                }
                db = container "Database"
                """);

        assertThat(read(both).components()).hasSize(3);
        assertThat(ContractModelReader.read(write(both), "both.dsl").components())
                .extracting(ContractComponent::id).containsExactly("orders");
    }

    @Test
    void exactlyOneContainerHasWebRoot() throws Exception {
        assertThat(problems(VALID.replace("\"web.root\" \"apps/web/src\"", "")))
                .contains("model: no container has web.root; exactly one must");
        assertThat(problems(VALID.replace("db = container \"Database\"", """
                db = container "Database" {
                    properties {
                        "web.root" "db"
                    }
                }
                """))).containsExactly("db, web: more than one container has web.root; exactly one may");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/apps/web/src", "apps/web/src/", "apps//src", "apps/../src", "./src", "apps/web src",
        ".hidden/src", "apps/*"})
    void webRootIsARelativePathOfPlainDirectoryNames(String root) throws Exception {
        assertThat(problems(VALID.replace("\"web.root\" \"apps/web/src\"", "\"web.root\" \"" + root + "\"")))
                .containsExactly("web: web.root '" + root + "' is not a relative path of plain directory names");
    }

    @Test
    void everyComponentHasAValidUniqueWebId() throws Exception {
        assertThat(problems(VALID.replace("\"web.id\" \"shell\"", "")))
                .containsExactly("shell: web.id is missing");
        assertThat(problems(VALID.replace("\"web.id\" \"shell\"", "\"web.id\" \"Shell\"")))
                .containsExactly("shell: web.id 'Shell' does not match [a-z][a-z0-9-]*");
        assertThat(problems(VALID.replace("\"web.id\" \"shell\"", "\"web.id\" \"cart\"")))
                .containsExactly("cart, shell: web.id 'cart' is used by more than one component");
    }

    @Test
    void everyComponentHasPathsThatAreValidAndDoNotOverlap() throws Exception {
        assertThat(problems(VALID.replace("\"web.paths\" \"app\"", "")))
                .containsExactly("shell: web.paths is missing");
        assertThat(problems(VALID.replace("\"web.paths\" \"app\"", "\"web.paths\" \"app, \"")))
                .containsExactly("shell: web.paths has an empty entry");
        assertThat(problems(VALID.replace("\"web.paths\" \"app\"", "\"web.paths\" \"../app\"")))
                .containsExactly("shell: web.paths entry '../app' is not a relative path of plain directory names");
        assertThat(problems(VALID.replace("\"web.paths\" \"app\"", "\"web.paths\" \"ui/forms\"")))
                .containsExactly("shell, widgets: web.paths entries 'ui' and 'ui/forms' overlap");
        assertThat(problems(VALID.replace("\"web.paths\" \"ui, icons\"", "\"web.paths\" \"ui, ui\"")))
                .containsExactly("widgets: web.paths entries 'ui' and 'ui' overlap");
    }

    @Test
    void everyComponentHasAStatusThatAgreesWithThePlannedTag() throws Exception {
        assertThat(problems(inShell("\"web.status\" \"built\"", "")))
                .containsExactly("shell: web.status is missing");
        assertThat(problems(inShell("\"web.status\" \"built\"", "\"web.status\" \"done\"")))
                .containsExactly("shell: web.status 'done' is neither built nor planned");
        assertThat(problems(VALID.replace("\"web.status\" \"planned\"", "\"web.status\" \"built\"")))
                .containsExactly("cart: the tag Planned is present, but web.status is built");
        assertThat(problems(inShell("\"web.status\" \"built\"",
                "\"web.status\" \"planned\""))).containsExactly(
                "shell: web.status is planned, but the tag Planned is missing");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "features/other/index.ts| cart: web.entry 'features/other/index.ts' is not a file in the component's web.paths",
        "features/cart| cart: web.entry 'features/cart' is not a file in the component's web.paths",
        "features/cart/index.js| cart: web.entry 'features/cart/index.js' is not a .ts or .tsx file",
        "features/cart/../x.ts| cart: web.entry 'features/cart/../x.ts' is not a relative path of plain names",
        "features/cart/index.test.ts| cart: web.entry 'features/cart/index.test.ts' is a test file, which no production file may import",
        "features/cart/main.test.tsx| cart: web.entry 'features/cart/main.test.tsx' is a test file, which no production file may import"})
    void anEntryIsATypeScriptFileInsideTheComponent(String entry, String problem) throws Exception {
        assertThat(problems(VALID.replace("\"web.entry\" \"features/cart/index.ts\"", "\"web.entry\" \"" + entry
                + "\""))).containsExactly(problem.strip());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "react, | shell: web.packages has an empty entry",
        "React| shell: web.packages entry 'React' is not an npm package name",
        "react-dom/client| shell: web.packages entry 'react-dom/client' is not an npm package name",
        "react, react| shell: web.packages lists 'react' more than once"})
    void packagesAreDistinctNpmPackageNames(String packages, String problem) throws Exception {
        assertThat(problems(VALID.replace("\"web.packages\" \"react, react-dom\"", "\"web.packages\" \"" + packages
                + "\""))).containsExactly(problem.strip());
    }

    @Test
    void aRelationshipsModeIsValidAndAgreesWithTheRequiredImportTag() throws Exception {
        assertThat(problems(VALID.replace("\"web.mode\" \"REQUIRED\"", "\"web.mode\" \"MUST\"")))
                .containsExactly("shell -> cart: web.mode 'MUST' is neither REQUIRED nor PERMITTED");
        assertThat(problems(VALID.replace("\"web.mode\" \"REQUIRED\"", "")))
                .containsExactly("shell -> cart: the tag Required import is present, but web.mode is PERMITTED");
        assertThat(problems(VALID.replace("cart -> widgets \"Uses\"", """
                cart -> widgets "Uses" {
                    properties {
                        "web.mode" "REQUIRED"
                    }
                }
                """))).containsExactly("cart -> widgets: web.mode is REQUIRED, but the tag Required import is missing");
    }

    @Test
    void webModeAndTheRequiredImportTagBelongOnlyToRelationshipsBetweenTwoWebComponents() throws Exception {
        assertThat(problems(VALID.replace("shell -> db \"Reads nothing from, documentation only\"", """
                shell -> db "Reads nothing from" "" "Required import" {
                    properties {
                        "web.mode" "REQUIRED"
                    }
                }
                """))).containsExactly(
                "shell -> db: the tag Required import is only allowed on relationships between two components of web",
                "shell -> db: web.mode is only allowed on relationships between two components of web");
    }

    @Test
    void webPropertiesBelongOnlyWhereTheVocabularyPlacesThem() throws Exception {
        assertThat(problems(VALID.replace("\"web.id\" \"shell\"",
                "\"web.id\" \"shell\"\n\"web.root\" \"x\"\n\"web.colour\" \"teal\""))).containsExactly(
                "shell: web.colour is not a web property",
                "shell: web.root is only allowed on a container");
        assertThat(problems(VALID.replace("db = container \"Database\"", """
                db = container "Database" {
                    properties {
                        "web.id" "db"
                    }
                    table = component "Table" {
                        properties {
                            "web.paths" "x"
                        }
                    }
                }
                """))).containsExactly(
                "db: web.id is only allowed on components of web",
                "table: web.paths is only allowed on components of web");
    }

    @Test
    void theWebContainerItsComponentsAndTheirRelationshipsCarryNoContractProperties() throws Exception {
        assertThat(problems(VALID.replace("\"web.id\" \"shell\"",
                "\"web.id\" \"shell\"\n\"contract.status\" \"built\"").replace("\"web.root\" \"apps/web/src\"",
                "\"web.root\" \"apps/web/src\"\n\"contract.isolated\" \"x\""))).containsExactly(
                "shell: contract.status is a property of the API's checked container, not of the web container "
                        + "or its components, which use web.* properties",
                "web: contract.isolated is a property of the API's checked container, not of the web container "
                        + "or its components, which use web.* properties");
    }

    @Test
    void aRelationshipBetweenWebComponentsCarriesNoContractMode() throws Exception {
        assertThat(problems(VALID.replace("cart -> widgets \"Uses\"", """
                cart -> widgets "Uses" {
                    properties {
                        "contract.mode" "REQUIRED"
                    }
                }
                """))).containsExactly("cart -> widgets: contract.mode is a property of the API's checked container, "
                + "not of the web container or its components, which use web.* properties");
    }

    @Test
    void withoutTheWebContainerWebModeIsStillCheckedForWhereItCanNeverBelong() throws Exception {
        String noRoot = VALID.replace("\"web.root\" \"apps/web/src\"", "").replace("// MODEL", """
                shell -> table "Reads" {
                    properties {
                        "web.mode" "PERMITTED"
                    }
                }
                """).replace("db = container \"Database\"", """
                db = container "Database" {
                    table = component "Table"
                }
                """);

        assertThat(problems(noRoot)).containsExactly("model: no container has web.root; exactly one must",
                "shell -> table: web.mode is only allowed on relationships between two components of the container "
                        + "with web.root");
    }

    @Test
    void packageNamesAreAtMost214CharactersAndScopedOnce() throws Exception {
        String longest = "a".repeat(214);
        assertThat(read(VALID.replace("\"web.packages\" \"react, react-dom\"", "\"web.packages\" \"" + longest
                + ", @scope/name\"")).component("shell").packages()).containsExactly("@scope/name", longest);
        assertThat(problems(VALID.replace("\"web.packages\" \"react, react-dom\"", "\"web.packages\" \"" + longest
                + "a, @a/b/c, @/b\""))).containsExactly(
                "shell: web.packages entry '@/b' is not an npm package name",
                "shell: web.packages entry '@a/b/c' is not an npm package name",
                "shell: web.packages entry '" + longest + "a' is not an npm package name");
    }

    @Test
    void atMostOneRelationshipPerOrderedPairAndNoCycle() throws Exception {
        assertThat(problems(VALID.replace("// MODEL", "cart -> widgets \"Again\""))).containsExactly(
                "cart -> widgets: 2 relationships connect these components; at most one may");
        assertThat(problems(VALID.replace("// MODEL", "widgets -> shell \"Calls back\""))).containsExactly(
                "cart, shell, widgets: the web component relationships form a cycle");
    }

    @Test
    void impliedRelationshipsAreRejected() throws Exception {
        String implied = VALID.replace("// MODEL", """
                !impliedRelationships true
                cart -> db "Implies shop -> db relationships"
                """);

        assertThat(problems(implied)).anyMatch(problem -> problem.contains("implied by cart -> db"));
    }

    @Test
    void theWebReaderUsesTheRestrictedParser() throws Exception {
        assertThat(problems(VALID.replace("// MODEL", "!include other.dsl")))
                .singleElement().asString().contains("!include");
        assertThat(problems(VALID.replace("// MODEL", "// ${HOME}"))).singleElement().asString()
                .contains("${");
    }

    /** The valid model with one text replaced inside the shell component only. */
    private static String inShell(String text, String replacement) {
        int shell = VALID.indexOf("shell = component");
        int at = VALID.indexOf(text, shell);
        return VALID.substring(0, at) + replacement + VALID.substring(at + text.length());
    }

    /** The container with these properties besides web.root. */
    private static String container(String properties) {
        String root = "\"web.root\" \"apps/web/src\"\n";
        assertThat(VALID).contains(root);
        return VALID.replace(root, root + properties);
    }

    @Test
    void theContainerNamesTestRootsPublicRootsAndWhatTestsMayImport() throws Exception {
        WebModel model = read(container("""
                                "web.testRoots" "apps/web/e2e, apps/web/test"
                                "web.publicRoots" "apps/web/public"
                                "web.testBuiltins" "node:test, node:assert/strict, node:fs"
                                "web.testPackages" "@playwright/test"
                """));

        assertThat(model.app()).isEqualTo("apps/web");
        assertThat(model.testRoots()).containsExactly("apps/web/e2e", "apps/web/test");
        assertThat(model.publicRoots()).containsExactly("apps/web/public");
        assertThat(model.testBuiltins()).containsExactly("node:assert/strict", "node:fs", "node:test");
        assertThat(model.testPackages()).containsExactly("@playwright/test");
        assertThat(read(VALID).testBuiltins()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "\"web.testRoots\" \"../e2e\"| web: web.testRoots entry '../e2e' is not a relative path of plain directory names",
        "\"web.testRoots\" \"other/e2e\"| web: 'other/e2e' is not inside the app's directory 'apps/web', the directory above web.root",
        "\"web.testRoots\" \"apps/web/src/tests\"| web: the roots 'apps/web/src' and 'apps/web/src/tests' overlap",
        "\"web.publicRoots\" \"apps/web/e2e\"\\n\"web.testRoots\" \"apps/web/e2e\"| web: the roots 'apps/web/e2e' and 'apps/web/e2e' overlap",
        "\"web.testRoots\" \"apps/web/e2e, \"| web: web.testRoots has an empty entry",
        "\"web.testBuiltins\" \"fs\"| web: web.testBuiltins entry 'fs' is not a built-in module of Node.js written with node:, such as node:test",
        "\"web.testBuiltins\" \"node:fs, node:fs\"| web: web.testBuiltins lists 'node:fs' more than once",
        "\"web.testBuiltins\" \"node:FS\"| web: web.testBuiltins entry 'node:FS' is not a built-in module of Node.js written with node:, such as node:test",
        "\"web.testPackages\" \"Playwright\"| web: web.testPackages entry 'Playwright' is not an npm package name"})
    void theContainersRootsAndTestImportsAreValid(String properties, String problem) throws Exception {
        assertThat(problems(container(properties.replace("\\n", "\n") + "\n"))).containsExactly(problem.strip());
    }

    @Test
    void theContainersPropertiesBelongOnTheContainerOnly() throws Exception {
        assertThat(problems(VALID.replace("\"web.id\" \"shell\"", "\"web.id\" \"shell\"\n\"web.testBuiltins\" \"node:test\"")))
                .containsExactly("shell: web.testBuiltins is only allowed on a container");
    }

    private WebModel read(String dsl) throws Exception {
        return WebModelReader.read(write(dsl), "web.dsl");
    }

    private List<String> problems(String dsl) throws IOException {
        Path file = write(dsl);
        InvalidModelException e = catchThrowableOfType(InvalidModelException.class,
                () -> WebModelReader.read(file, "web.dsl"));
        assertThat(e).as("the model is invalid").isNotNull();
        return e.problems();
    }

    private Path write(String dsl) throws IOException {
        return Files.writeString(dir.resolve("web.dsl"), dsl, StandardCharsets.UTF_8);
    }
}
