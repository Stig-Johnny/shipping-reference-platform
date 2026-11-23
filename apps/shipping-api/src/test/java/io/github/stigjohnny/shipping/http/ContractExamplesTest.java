package io.github.stigjohnny.shipping.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The contract's own examples (control.http-contract): each one validates against its schema with the settings of the
 * HTTP contract tests, and a problem example is its type's problem, with its response's status, of a type that its
 * response names. An example in a place this test does not read fails, so none goes unchecked. The negative fixtures
 * change one example of a copy of the contract; schemas are still read from the contract itself.
 */
class ContractExamplesTest {

    private static final String JSON = "/content/application~1json/examples/";
    private static final String PROBLEM = "/content/application~1problem+json/examples/";
    private static final String MEMBERSHIP = "#/paths/~1me/get/responses/200" + JSON + "clerkAlpha";
    private static final String QUOTE = "#/paths/~1quotes/post/responses/201" + JSON + "internationalAllLevels";
    private static final String NO_OFFER = "#/paths/~1quotes/post/responses/422" + PROBLEM + "routeNotServed";
    private static final String EXPIRED = "#/paths/~1shipments/post/responses/422" + PROBLEM + "expired";
    private static final String IDEMPOTENCY_KEY = "#/components/parameters/IdempotencyKey/example";

    private final Contract contract = Contract.contract();

    @Test
    void everyExampleOfTheContractConformsToItsSchemaAndItsResponse() {
        List<Contract.Example> examples = contract.examples();

        assertThat(examples).extracting(Contract.Example::pointer).containsExactly(MEMBERSHIP,
                "#/paths/~1quotes/post/requestBody" + JSON + "internationalAllLevels", QUOTE, NO_OFFER,
                "#/paths/~1shipments/post/requestBody" + JSON + "bookStandard",
                "#/paths/~1shipments/post/responses/201" + JSON + "booked", EXPIRED, IDEMPOTENCY_KEY,
                "#/components/responses/BadRequest" + PROBLEM + "weightTooHigh");
        examples.forEach(contract::assertExample);
    }

    @Test
    void anExampleWithAnUndocumentedMemberFails() {
        assertThatThrownBy(() -> changed(MEMBERSHIP, value -> value.put("debug", true)))
                .isInstanceOf(AssertionError.class).hasMessageContaining(MEMBERSHIP).hasMessageContaining("debug");
    }

    @Test
    void anExampleWithAnImpossibleDateTimeFails() {
        // It matches the Timestamp pattern; only the date-time format rejects it.
        assertThatThrownBy(() -> changed(QUOTE, value -> value.put("issuedAt", "2026-99-99T99:99:99.000Z")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("/issuedAt");
    }

    @Test
    void aParameterExampleThatItsSchemaRefusesFails() {
        JsonNode document = contract.copyOfDocument();
        ((ObjectNode) document.at("/components/parameters/IdempotencyKey")).put("example", "too-short");

        assertThatThrownBy(() -> contract.assertExample(example(document, IDEMPOTENCY_KEY)))
                .isInstanceOf(AssertionError.class).hasMessageContaining(IDEMPOTENCY_KEY);
    }

    @Test
    void aProblemExampleWhoseTitleIsNotItsTypesFails() {
        assertThatThrownBy(() -> changed(NO_OFFER, value -> value.put("title", "No offers")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("the registry's title");
    }

    @Test
    void aProblemExampleWhoseStatusIsNotItsResponsesFails() {
        assertThatThrownBy(() -> changed(EXPIRED, value -> value.put("status", 409)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("the status of its response");
    }

    @Test
    void aProblemExampleOfATypeThatItsResponseDoesNotNameFails() {
        // quote-expired is a 422 with its registry's title, but createQuote's 422 names only no-offer-available.
        JsonNode document = contract.copyOfDocument();
        ((ObjectNode) document.at(pointer(NO_OFFER.substring(0, NO_OFFER.lastIndexOf('/'))))).set("expired",
                document.at(pointer(EXPIRED)).deepCopy());
        String moved = NO_OFFER.substring(0, NO_OFFER.lastIndexOf('/') + 1) + "expired";

        assertThatThrownBy(() -> contract.assertExample(example(document, moved)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("a type that its response names");
    }

    /** Each place where this test reads no example, and an example's value that it cannot read. */
    @ParameterizedTest
    @ValueSource(strings = {"/components/headers/CorrelationId", "/components/schemas/Quote",
        "/components/schemas/Money/properties/amount", "/components"})
    void anExampleInAPlaceThisTestDoesNotReadFails(String place) {
        JsonNode document = contract.copyOfDocument();
        ObjectNode holder = (ObjectNode) document.at(place);
        if (place.equals("/components")) {
            holder.putObject("examples").putObject("shared").put("value", "x");
        } else {
            holder.put("example", "x");
        }

        assertThatThrownBy(() -> Contract.examples(document)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("#" + place + "/example")
                .hasMessageContaining("a place where no example is read");
    }

    @ParameterizedTest
    @ValueSource(strings = {"externalValue", "$ref"})
    void anExampleThatHoldsNoValueFails(String instead) {
        JsonNode document = contract.copyOfDocument();
        ObjectNode example = (ObjectNode) document.at(pointer(MEMBERSHIP));
        example.remove("value");
        example.put(instead, "#/components/examples/clerkAlpha");

        assertThatThrownBy(() -> Contract.examples(document)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(MEMBERSHIP).hasMessageContaining("holds its value itself");
    }

    @Test
    void aMemberNamedExampleInAPayloadOrASchemasPropertiesIsData() {
        JsonNode document = contract.copyOfDocument();
        ((ObjectNode) document.at("/components/schemas/Recipient/properties")).putObject("example")
                .put("type", "string");
        ((ObjectNode) document.at(pointer(MEMBERSHIP) + "/value")).putObject("examples").put("example", "data");

        assertThat(Contract.examples(document)).extracting(Contract.Example::pointer).hasSize(9)
                .isEqualTo(contract.examples().stream().map(Contract.Example::pointer).toList());
    }

    /** Asserts the example at {@code at} in a copy of the contract whose example value {@code change} has changed. */
    private void changed(String at, Consumer<ObjectNode> change) {
        JsonNode document = contract.copyOfDocument();
        change.accept((ObjectNode) document.at(pointer(at) + "/value"));
        contract.assertExample(example(document, at));
    }

    private static Contract.Example example(JsonNode document, String at) {
        return Contract.examples(document).stream().filter(e -> e.pointer().equals(at)).findFirst().orElseThrow();
    }

    /** A pointer of {@link Contract.Example#pointer()} without its leading {@code #}, as Jackson reads one. */
    private static String pointer(String at) {
        return at.substring(1);
    }
}
