package io.github.stigjohnny.shipping.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.c4contractkit.api.ComponentResult;
import io.github.stigjohnny.c4contractkit.api.ComponentStatus;
import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.c4contractkit.api.Report;
import io.github.stigjohnny.shipping.architecture.ClassDirectories;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The scenario checks' restricted reader of the model: it reads the repository's views and modules as Structurizr
 * does, cross-checked with c4-contract-kit's parse, and refuses what it does not read exactly.
 */
class ScenarioModelTest {

    private static final Path MODEL = Path.of("../../architecture/workspace.dsl");
    private static final String FIXTURE = """
            workspace "Fixture" {
                !identifiers flat
                model {
                    clerk = person "Clerk"
                    system = softwareSystem "System" {
                        api = container "API" {
                            properties {
                                "contract.root" "x"
                            }
                            quotes = component "Quotes" {
                                properties {
                                    "contract.id" "quotes"
                                    "contract.packages" "x.quotes"
                                    "contract.status" "built"
                                }
                            }
                        }
                    }
                    clerk -> quotes "Uses"
                }
                views {
                    dynamic api "ScenarioFixture" "Fixture." {
                        STEPS
                        autoLayout lr
                    }
                }
            }
            """;
    private static final Report KIT = new Report(Report.SCHEMA, "fixture.dsl", List.of(new ComponentResult("quotes",
            "Quotes", ComponentStatus.BUILT, List.of("x.quotes"), 1)), List.of(), List.of());

    @TempDir
    Path dir;

    @Test
    void theRepositorysScenarioViewsAreReadStepByStepInFileOrder() {
        ScenarioModel model = ScenarioModel.read(MODEL, Conformance.check(MODEL, ClassDirectories.main()));

        assertThat(model.view("ScenarioQuote").orElseThrow().steps())
                .extracting(ScenarioModel.ViewStep::order, ScenarioModel.ViewStep::from, ScenarioModel.ViewStep::to)
                .containsExactly(tuple(1, "clerk", "web"), tuple(2, "web", "quotes"), tuple(3, "quotes", "tenants"),
                        tuple(4, "quotes", "carrier"), tuple(5, "quotes", "db"), tuple(6, "quotes", "db"));
        assertThat(model.module("quotes").orElseThrow().built()).isTrue();
        assertThat(model.module("shipments").orElseThrow().built()).isFalse();
        assertThat(model.moduleOf("io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote"))
                .contains("quotes");
        assertThat(model.element("db").orElseThrow().tags()).containsExactly("Database", "Planned");
    }

    @Test
    void aFixtureModelAgreeingWithTheKitIsRead() throws IOException {
        ScenarioModel model = ScenarioModel.read(write("clerk -> quotes \"Uses\""), KIT);

        assertThat(model.view("ScenarioFixture").orElseThrow().steps()).singleElement()
                .extracting(ScenarioModel.ViewStep::from, ScenarioModel.ViewStep::to,
                        ScenarioModel.ViewStep::description).containsExactly("clerk", "quotes", "Uses");
    }

    @Test
    void parallelSequencesAndExplicitStepNumbersAreRefused() throws IOException {
        Path parallel = write("{\n            clerk -> quotes \"Uses\"\n        }");
        Path numbered = write("1: clerk -> quotes \"Uses\"");
        Path undescribed = write("clerk -> quotes");

        for (Path model : List.of(parallel, numbered, undescribed)) {
            assertThatThrownBy(() -> ScenarioModel.read(model, KIT)).hasMessageContaining("dynamic view "
                    + "ScenarioFixture may hold only 'from -> to \"description\"' steps and autoLayout");
        }
    }

    @Test
    void hierarchicalIdentifiersAreRefused() throws IOException {
        Path model = write("clerk -> quotes \"Uses\"", "!identifiers flat", "!identifiers hierarchical");

        assertThatThrownBy(() -> ScenarioModel.read(model, KIT)).hasMessageContaining("needs '!identifiers flat'");
    }

    @Test
    void aModuleThatTheKitReadsDifferentlyIsRefused() throws IOException {
        Report other = new Report(Report.SCHEMA, "fixture.dsl", List.of(new ComponentResult("quotes", "Quotes",
                ComponentStatus.BUILT, List.of("x.offers"), 1)), List.of(), List.of());

        assertThatThrownBy(() -> ScenarioModel.read(write("clerk -> quotes \"Uses\""), other))
                .hasMessageContaining("the scenario reader and c4-contract-kit disagree about module quotes");
    }

    @Test
    void aComponentWhoseIdentifierIsNotItsContractIdIsRefused() throws IOException {
        Path model = write("clerk -> quotes \"Uses\"", "\"contract.id\" \"quotes\"", "\"contract.id\" \"offers\"");

        assertThatThrownBy(() -> ScenarioModel.read(model, KIT)).hasMessageContaining("component quotes has "
                + "contract.id offers; the scenario checks need the DSL identifier and the contract.id to be equal");
    }

    private Path write(String steps, String... replacements) throws IOException {
        String text = FIXTURE.replace("STEPS", steps);
        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return Files.writeString(dir.resolve("fixture.dsl"), text, StandardCharsets.UTF_8);
    }
}
