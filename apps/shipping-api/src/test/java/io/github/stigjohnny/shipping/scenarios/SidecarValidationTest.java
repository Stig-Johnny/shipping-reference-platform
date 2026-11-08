package io.github.stigjohnny.shipping.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.c4contractkit.api.Conformance;
import io.github.stigjohnny.shipping.architecture.ClassDirectories;
import io.github.stigjohnny.shipping.scenarios.support.ScenarioModel;
import io.github.stigjohnny.shipping.scenarios.support.Sidecar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sidecars of architecture/scenarios/ against the model's dynamic views: every Scenario view has exactly one
 * valid sidecar and every sidecar a view, and a sidecar that breaks a rule is rejected with the rule. The fixtures are
 * the real quote sidecar with one change each.
 */
class SidecarValidationTest {

    private static final Path MODEL = Path.of("../../architecture/workspace.dsl");
    private static final Path SIDECARS = Path.of("../../architecture/scenarios");
    private static final ScenarioModel SCENARIO_MODEL = ScenarioModel.read(MODEL,
            Conformance.check(MODEL, ClassDirectories.main()));

    @TempDir
    Path dir;

    @Test
    void everyScenarioViewHasOneValidSidecarAndEverySidecarAView() throws IOException {
        List<String> views = SCENARIO_MODEL.views().keySet().stream().filter(key -> key.startsWith("Scenario"))
                .toList();
        List<String> files;
        try (Stream<Path> list = Files.list(SIDECARS)) {
            files = list.map(p -> p.getFileName().toString()).sorted().toList();
        }

        assertThat(views).containsExactly("ScenarioQuote", "ScenarioTenantDenial");
        assertThat(files).containsExactlyElementsOf(views.stream().map(Sidecar::fileName).sorted().toList());
        for (String view : views) {
            Sidecar sidecar = Sidecar.read(SIDECARS.resolve(Sidecar.fileName(view)), view, SCENARIO_MODEL);
            assertThat(sidecar.steps()).extracting(Sidecar.Step::order).containsExactlyElementsOf(
                    SCENARIO_MODEL.view(view).orElseThrow().steps().stream().map(ScenarioModel.ViewStep::order)
                            .toList());
        }
    }

    @Test
    void aStepOfTheViewWithoutAnEntryIsRejected() throws IOException {
        String text = quote().replaceFirst("(?s),\\s*\\{\\s*\"order\": 6.*?\\n    }", "");

        assertThatThrownBy(() -> read(text)).hasMessageContaining("the view ScenarioQuote has 6 steps and the sidecar "
                + "5 entries; each step needs exactly one entry and each entry a step");
    }

    @Test
    void anEntryWithoutAStepOfTheViewIsRejected() throws IOException {
        String extra = ",\n    {\"order\": 7, \"id\": \"extra\", \"kind\": \"USER_ACTION\", \"from\": \"clerk\", \"to\": "
                + "\"web\", \"phase\": \"SYNCHRONOUS\", \"cardinality\": \"1\", \"after\": []}\n  ]";

        assertThatThrownBy(() -> read(quote().replaceFirst("\\n  ]", extra))).hasMessageContaining(
                "step 7 (extra): the view has no step 7").hasMessageContaining("has 6 steps and the sidecar 7 entries");
    }

    @Test
    void endpointsThatDifferFromTheViewsStepAreRejected() throws IOException {
        String swapped = quote().replace("\"from\": \"quotes\",\n      \"to\": \"tenants\"",
                "\"from\": \"tenants\",\n      \"to\": \"quotes\"");

        assertThatThrownBy(() -> read(swapped)).hasMessageContaining("step 3 (resolve-tenant): the endpoints are "
                + "tenants -> quotes, but step 3 of the view is quotes -> tenants");
    }

    @Test
    void anOperationThatIsNotTheCalleesInterfaceIsRejected() throws IOException {
        String wrong = quote().replace("io.github.stigjohnny.shipping.tenants.api.CurrentTenant",
                "io.github.stigjohnny.shipping.quotes.api.QuoteOffers");

        assertThatThrownBy(() -> read(wrong)).hasMessageContaining("step 3 (resolve-tenant): operation must name an "
                + "interface in the api package of tenants, and one of its methods");
    }

    @Test
    void anEventThatIsNotARecordOfThePublishersApiIsRejected() throws IOException {
        String wrong = quote().replace("io.github.stigjohnny.shipping.quotes.api.QuoteIssued",
                "io.github.stigjohnny.shipping.tenants.api.TenantId");

        assertThatThrownBy(() -> read(wrong)).hasMessageContaining("step 6 (publish-quote-issued): operation.event "
                + "must be a record in the api package of quotes");
    }

    @Test
    void aKindWhoseEndpointsDoNotFitIsRejected() throws IOException {
        String wrong = quote().replace("\"kind\": \"EVENT_PUBLICATION\"", "\"kind\": \"MODULE_API_CALL\"");

        assertThatThrownBy(() -> read(wrong)).hasMessageContaining("step 6 (publish-quote-issued): MODULE_API_CALL "
                + "cannot go from quotes to db: it goes from one module to another");
    }

    @Test
    void anOrderingConstraintOnALaterStepIsRejected() throws IOException {
        String wrong = quote().replace("\"after\": [\"create-quote-request\"]", "\"after\": [\"insert-quote\"]");

        assertThatThrownBy(() -> read(wrong)).hasMessageContaining("step 3 (resolve-tenant): after may name only "
                + "earlier steps, each once");
    }

    @Test
    void unknownAndDuplicateMembersAreRejected() throws IOException {
        assertThatThrownBy(() -> read(quote().replace("\"order\": 1,", "\"order\": 1, \"note\": \"x\",")))
                .hasMessageContaining("entry 1: unknown member 'note'");
        assertThatThrownBy(() -> read(quote().replace("\"order\": 1,", "\"order\": 1, \"order\": 1,")))
                .hasMessageContaining("not strict JSON");
    }

    /**
     * The review's case: A (0..*), B (1) and C (1..*) in order, where A and C repeat one signature. The execution A B C C
     * is valid, but matching in view order gives A both interactions of that signature and blames B for the order. Such
     * a sidecar is refused; with exact cardinalities the same view is accepted.
     */
    @Test
    void repeatedSignaturesWithAVaryingCardinalityAreRefused() throws IOException {
        String model = Files.readString(MODEL, StandardCharsets.UTF_8).replace("        styles {", """
                        dynamic api "ScenarioRepeated" "Fixture: one signature twice." {
                            quotes -> tenants "Resolves the tenant"
                            quotes -> db "Publishes QuoteIssued"
                            quotes -> tenants "Resolves the tenant again"
                            autoLayout lr
                        }

                        styles {""");
        Path dsl = Files.writeString(dir.resolve("workspace.dsl"), model, StandardCharsets.UTF_8);
        ScenarioModel repeated = ScenarioModel.read(dsl, Conformance.check(dsl, ClassDirectories.main()));
        String step = "{\"order\": %d, \"id\": \"%s\", \"kind\": \"%s\", \"from\": \"quotes\", \"to\": \"%s\", "
                + "\"operation\": %s, \"phase\": \"SYNCHRONOUS\", \"cardinality\": \"%s\", \"after\": [%s]}";
        String require = "{\"interface\": \"io.github.stigjohnny.shipping.tenants.api.CurrentTenant\", \"method\": "
                + "\"require\"}";
        String publish = "{\"event\": \"io.github.stigjohnny.shipping.quotes.api.QuoteIssued\"}";
        String sidecar = "{\"schema\": \"shipping-scenarios/sidecar/v1\", \"view\": \"ScenarioRepeated\", \"steps\": [%s, "
                + "%s, %s]}";

        Path varying = Files.writeString(dir.resolve("scenario-repeated.json"), sidecar.formatted(
                step.formatted(1, "a", "MODULE_API_CALL", "tenants", require, "0..*", ""),
                step.formatted(2, "b", "EVENT_PUBLICATION", "db", publish, "1", "\"a\""),
                step.formatted(3, "c", "MODULE_API_CALL", "tenants", require, "1..*", "\"b\"")),
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Sidecar.read(varying, "fixture", repeated)).hasMessageContaining("steps a, c: they "
                + "repeat one signature, and a cardinality among them varies, so an interaction could belong to more "
                + "than one of them: give repeated steps exact cardinalities");

        Path exact = Files.writeString(dir.resolve("scenario-repeated.json"), sidecar.formatted(
                step.formatted(1, "a", "MODULE_API_CALL", "tenants", require, "1", ""),
                step.formatted(2, "b", "EVENT_PUBLICATION", "db", publish, "1", "\"a\""),
                step.formatted(3, "c", "MODULE_API_CALL", "tenants", require, "2", "\"b\"")),
                StandardCharsets.UTF_8);
        assertThat(Sidecar.read(exact, "fixture", repeated).steps()).extracting(Sidecar.Step::id)
                .containsExactly("a", "b", "c");
    }

    @Test
    void aSidecarMustCarryItsViewsFileName() throws IOException {
        Path file = Files.writeString(dir.resolve("quote.json"), quote(), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> Sidecar.read(file, "quote.json", SCENARIO_MODEL)).hasMessageContaining(
                "the sidecar of view ScenarioQuote must be named scenario-quote.json");
    }

    private static String quote() throws IOException {
        return Files.readString(SIDECARS.resolve("scenario-quote.json"), StandardCharsets.UTF_8);
    }

    private Sidecar read(String text) throws IOException {
        Path file = Files.writeString(dir.resolve("scenario-quote.json"), text, StandardCharsets.UTF_8);
        return Sidecar.read(file, "fixture", SCENARIO_MODEL);
    }
}
