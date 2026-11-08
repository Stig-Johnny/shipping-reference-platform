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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The scenario checks' restricted reader of the model: it reads the repository's views and modules as Structurizr
 * does, cross-checked with c4-contract-kit's parse, and refuses what it does not read exactly.
 */
class ScenarioModelTest {

    private static final Path MODEL = Path.of("../../architecture/workspace.dsl");
    private static final Path PAGES = Path.of("../../docs/architecture");
    private static final Pattern SOURCE =
            Pattern.compile("Source: `architecture/workspace.dsl`, view `([A-Za-z0-9]+)`");
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
    /** How many fixture models this test wrote: each gets a file of its own. */
    private int written;

    @Test
    void theRepositorysScenarioViewsAreReadStepByStepInFileOrder() {
        ScenarioModel model = ScenarioModel.read(MODEL, Conformance.check(MODEL, ClassDirectories.main()));

        assertThat(model.view("ScenarioQuote").orElseThrow().steps())
                .extracting(ScenarioModel.ViewStep::order, ScenarioModel.ViewStep::from, ScenarioModel.ViewStep::to)
                .containsExactly(tuple(1, "clerk", "web"), tuple(2, "web", "quotes"), tuple(3, "quotes", "tenants"),
                        tuple(4, "quotes", "carrier"), tuple(5, "quotes", "db"), tuple(6, "quotes", "db"));
        assertThat(model.module("quotes").orElseThrow().built()).isTrue();
        assertThat(model.module("shipments").orElseThrow().built()).isTrue();
        assertThat(model.module("audit").orElseThrow().built()).isFalse();
        assertThat(model.moduleOf("io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote"))
                .contains("quotes");
        assertThat(model.element("db").orElseThrow().tags()).containsExactly("Database", "Planned");
    }

    /**
     * The differential check: every dynamic view as this reader reads it, against the same view as Structurizr reads
     * it. The docs tool renders Structurizr's reading into docs/architecture/, and :tools:docs:checkGenerated fails the
     * same build when those pages differ from what Structurizr reads from the model now, so agreement with the pages is
     * agreement with Structurizr: the same views, and in each the same steps, numbering, endpoints and descriptions.
     */
    @Test
    void everyDynamicViewIsReadAsStructurizrReadsIt() throws IOException {
        ScenarioModel model = ScenarioModel.read(MODEL, Conformance.check(MODEL, ClassDirectories.main()));
        Map<String, List<List<String>>> structurizr = new TreeMap<>();
        try (Stream<Path> pages = Files.list(PAGES)) {
            for (Path page : pages.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                String text = Files.readString(page, StandardCharsets.UTF_8);
                Matcher view = SOURCE.matcher(text);
                if (text.contains("\ndescription: \"Dynamic view of") && view.find()) {
                    structurizr.put(view.group(1), steps(text));
                }
            }
        }

        assertThat(structurizr.keySet()).isEqualTo(model.views().keySet());
        for (ScenarioModel.View view : model.views().values()) {
            List<List<String>> read = view.steps().stream().map(step -> List.of(Integer.toString(step.order()),
                    model.element(step.from()).orElseThrow().name(), model.element(step.to()).orElseThrow().name(),
                    step.description())).toList();
            assertThat(read).as(view.key()).isEqualTo(structurizr.get(view.key()));
        }
    }

    @Test
    void codexsCommentThatStructurizrAndASimplerReaderSplitDifferentlyIsRefused() throws IOException {
        Path model = write("""
                /* note */ more text
                        clerk -> quotes "Uses"
                        autoLayout lr */""");

        assertThatThrownBy(() -> ScenarioModel.read(model, KIT)).hasMessageContaining("a block comment marker shares "
                + "this line with other text");
    }

    @Test
    void everyOtherAmbiguousCommentFormIsRefused() throws IOException {
        Path closesAfterText = write("/*\n        note */\n        clerk -> quotes \"Uses\"");
        Path stepAfterOpener = write("/* note\n        clerk -> quotes \"Uses\"\n        */");
        Path markerInAString = write("clerk -> quotes \"Uses /* this\"");
        Path inlineSlashes = write("clerk -> quotes \"Uses\" // note");
        Path loneLayoutText = write("clerk -> quotes \"Uses\"\n        autoLayout lr extra");

        assertThatThrownBy(() -> ScenarioModel.read(closesAfterText, KIT)).hasMessageContaining("inside a block "
                + "comment, only a line that is exactly '*/' may hold a comment marker");
        assertThatThrownBy(() -> ScenarioModel.read(stepAfterOpener, KIT)).hasMessageContaining("a block comment "
                + "marker shares this line with other text");
        assertThatThrownBy(() -> ScenarioModel.read(markerInAString, KIT)).hasMessageContaining("a block comment "
                + "marker shares this line with other text");
        assertThatThrownBy(() -> ScenarioModel.read(inlineSlashes, KIT)).hasMessageContaining("'//' after other text "
                + "is not a comment for Structurizr");
        assertThatThrownBy(() -> ScenarioModel.read(loneLayoutText, KIT)).hasMessageContaining("may hold only "
                + "'from -> to \"description\"' steps and autoLayout");
    }

    @Test
    void theUnambiguousCommentFormsAreSkippedAsStructurizrSkipsThem() throws IOException {
        Path model = write("/*\n        clerk -> quotes \"Hidden\"\n        */\n        /* one line */\n"
                + "        // a line comment\n        # another\n        clerk -> quotes \"Uses\"");

        assertThat(ScenarioModel.read(model, KIT).view("ScenarioFixture").orElseThrow().steps())
                .extracting(ScenarioModel.ViewStep::order, ScenarioModel.ViewStep::description)
                .containsExactly(tuple(1, "Uses"));
    }

    /** The rows of a generated page's relationships table: step, from, to and description. */
    private static List<List<String>> steps(String page) {
        String table = page.substring(page.indexOf("## Relationships"));
        List<List<String>> rows = new ArrayList<>();
        for (String line : table.lines().skip(4).takeWhile(l -> l.startsWith("| ")).toList()) {
            List<String> cells = Arrays.stream(line.substring(2, line.length() - 2).split(" \\| ", -1))
                    .map(c -> c.replace("\\|", "|")).toList();
            rows.add(List.of(cells.get(0), cells.get(1), cells.get(2), cells.get(3)));
        }
        return rows;
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
        written++;
        return Files.writeString(dir.resolve("fixture-" + written + ".dsl"), text, StandardCharsets.UTF_8);
    }
}
