package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The capabilities that name each HTTP operation and problem type (ADR 0025): every operation of the contract, and
 * every problem type that its registry gives a module, is named by a capability of that module, so that its state is
 * known; an operation or a problem type is planned while every capability that names it is planned. The negative
 * fixtures edit one line of docs/capabilities/capabilities.yaml in a copy of the repository.
 */
class OperationBindingsTest {

    private static final String WHERE = "docs/capabilities/capabilities.yaml ";

    @TempDir
    Path dir;

    private Fixture fixture;

    @BeforeEach
    void copyTheRepository() {
        fixture = Fixture.copy(dir).passingEvidence();
    }

    @Test
    void everyOperationAndModuleProblemOfTheRepositoryIsNamedAndOnlyCancellationIsPlanned() {
        Sources sources = sources();
        Capabilities capabilities = sources.capabilities();

        assertThat(problems(sources)).isEmpty();
        assertThat(sources.contract().operations()).allSatisfy(o -> assertThat(capabilities.naming(o.id()))
                .as(o.id()).isNotEmpty());
        assertThat(sources.contract().operations().stream().filter(o -> capabilities.plannedOperation(o.id()))
                .map(o -> o.id()).toList()).containsExactly("cancelShipment");
        assertThat(sources.contract().problemTypes().stream().filter(t -> capabilities.plannedProblem(t.type()))
                .map(t -> t.type()).toList()).containsExactly("shipment-cancelled");
        assertThat(capabilities.naming("bookShipment")).extracting(c -> c.id())
                .containsExactly("shipments.book-from-offer", "shipments.idempotent-booking");
    }

    @Test
    void anOperationOrAProblemTypeIsPlannedOnlyWhileEveryCapabilityThatNamesItIs() {
        assertThat(sources().capabilities().plannedOperation("cancelShipment")).isTrue();
        // The partial capability list-and-read now also names cancelShipment and shipment-cancelled.
        edit("""
                    operations: [listShipments, getShipment]
                    problems: [shipment-not-found]
                """, """
                    operations: [listShipments, getShipment, cancelShipment]
                    problems: [shipment-not-found, shipment-cancelled]
                """);
        Capabilities capabilities = sources().capabilities();

        assertThat(capabilities.plannedOperation("cancelShipment")).isFalse();
        assertThat(capabilities.plannedProblem("shipment-cancelled")).isFalse();
        assertThat(capabilities.plannedOperation("unknownOperation")).isFalse();
    }

    @Test
    void anOperationThatIsNoOperationOfTheContractFails() {
        edit("    operations: [getQuote]\n", "    operations: [getQuot]\n");

        assertThat(problems(sources())).contains(WHERE + "quotes.get-quote: operation 'getQuot' is no operation of "
                + "contracts/shipping.openapi.yaml (ADR 0025)");
    }

    @Test
    void anOperationOfAnotherModuleFails() {
        edit("    operations: [getQuote]\n", "    operations: [getQuote, getShipment]\n");

        assertThat(problems(sources())).containsExactly(WHERE + "quotes.get-quote: operation 'getShipment' is tagged "
                + "shipments, not quotes (ADR 0025)");
    }

    @Test
    void aProblemTypeThatIsNotRegisteredFails() {
        edit("    problems: [quote-not-found]\n", "    problems: [quote-not-fund]\n");

        assertThat(problems(sources())).contains(WHERE + "quotes.get-quote: problem type 'quote-not-fund' is no type "
                + "of the contract's problem registry (ADR 0025)");
    }

    @Test
    void aProblemTypeThatTheRegistryGivesAnotherModuleFails() {
        edit("    problems: [no-offer-available]\n", "    problems: [no-offer-available, offer-not-found]\n");

        assertThat(problems(sources())).containsExactly(WHERE + "quotes.create-quote: the registry gives the problem "
                + "type 'offer-not-found' to shipments, not to quotes (ADR 0025)");
    }

    @Test
    void anOperationThatNoCapabilityNamesFails() {
        edit("    operations: [getQuote]\n", "");

        assertThat(problems(sources())).containsExactly("contracts/shipping.openapi.yaml getQuote: no capability of "
                + "docs/capabilities/capabilities.yaml names this operation, so its state is unknown (ADR 0025)");
    }

    @Test
    void aModuleProblemTypeThatNoCapabilityNamesFails() {
        edit("    problems: [shipment-cancelled]\n", "");

        assertThat(problems(sources())).containsExactly("contracts/shipping.openapi.yaml: no capability of "
                + "docs/capabilities/capabilities.yaml names the problem type `shipment-cancelled`, which the registry "
                + "gives to shipments, so its state is unknown (ADR 0025)");
    }

    /** A name twice, an empty list, and a list that is no list of names. */
    @ParameterizedTest
    @ValueSource(strings = {"[getQuote, getQuote]", "[]", "getQuote"})
    void aListOfOperationsThatIsNotOneOfNamesFails(String list) {
        edit("    operations: [getQuote]\n", "    operations: " + list + "\n");

        assertThatThrownBy(this::sources).isInstanceOf(DocsException.class).hasMessageContaining(WHERE
                + "quotes.get-quote: operations ").hasMessageContaining(list.equals("[getQuote, getQuote]")
                        ? "names 'getQuote' twice" : "is a list of names, such as [getQuote]");
    }

    @Test
    void aControlNamesNoOperationsAndNoProblems() {
        fixture.edit(Capabilities.PATH, text -> text.replace("    check: build\n",
                "    check: build\n    operations: [getQuote]\n"));

        assertThatThrownBy(this::sources).isInstanceOf(DocsException.class).hasMessageContaining(WHERE
                + "control.publication-scan: a control names no operations");
    }

    @Test
    void aPlannedOperationIsMarkedOnItsModulesBrainPage() {
        String brain = GeneratedFiles.generate(sources()).get("docs/brain/shipments.md");

        assertThat(brain).contains("- `POST /api/v1/shipments/{shipmentId}/cancellation` `cancelShipment` (planned): ")
                .contains("- `GET /api/v1/shipments/{shipmentId}/label` `getShipmentLabel`: ");
    }

    /** Replaces one passage of the capabilities, which must occur once. */
    private void edit(String passage, String replacement) {
        fixture.edit(Capabilities.PATH, text -> {
            assertThat(text.split(Pattern.quote(passage), -1)).as(passage).hasSize(2);
            return text.replace(passage, replacement);
        });
    }

    private Sources sources() {
        return Sources.read(fixture.root(), List.of(fixture.reports()));
    }

    private static List<String> problems(Sources sources) {
        Problems problems = new Problems();
        OperationBindings.check(sources, problems);
        return problems.list();
    }
}
