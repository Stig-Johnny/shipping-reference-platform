package io.github.stigjohnny.shipping.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/**
 * The contract's own examples (control.http-contract): each one validates against its schema with the settings of the
 * HTTP contract tests, and a problem example is its type's problem, with its response's status, of a type that its
 * response names. An example in a place this test does not read fails, so none goes unchecked. Each negative fixture
 * edits one passage of the contract's text, as a contributor would; schemas are still read from the contract itself.
 */
class ContractExamplesTest {

    private static final String JSON = "/content/application~1json/examples/";
    private static final String PROBLEM = "/content/application~1problem+json/examples/";
    private static final String MEMBERSHIP = "#/paths/~1me/get/responses/200" + JSON + "clerkAlpha";
    private static final String QUOTE = "#/paths/~1quotes/post/responses/201" + JSON + "internationalAllLevels";
    private static final String NO_OFFER = "#/paths/~1quotes/post/responses/422" + PROBLEM + "routeNotServed";
    private static final String EXPIRED = "#/paths/~1shipments/post/responses/422" + PROBLEM + "expired";
    private static final String IDEMPOTENCY_KEY = "#/components/parameters/IdempotencyKey/example";
    private static final String ROLE = "                    role: CLERK\n";

    private final Contract contract = Contract.contract();

    @Test
    void everyExampleOfTheContractConformsToItsSchemaAndItsResponse() {
        List<Contract.Example> examples = contract.examples();

        assertThat(pointers(examples)).containsExactly(MEMBERSHIP,
                "#/paths/~1quotes/post/requestBody" + JSON + "internationalAllLevels", QUOTE, NO_OFFER,
                "#/paths/~1shipments/post/requestBody" + JSON + "bookStandard",
                "#/paths/~1shipments/post/responses/201" + JSON + "booked", EXPIRED, IDEMPOTENCY_KEY,
                "#/components/responses/BadRequest" + PROBLEM + "weightTooHigh");
        examples.forEach(contract::assertExample);
    }

    @Test
    void anExampleWithAnUndocumentedMemberFails() {
        assertThatThrownBy(() -> assertEdited(MEMBERSHIP, ROLE, ROLE + "                    debug: true\n"))
                .isInstanceOf(AssertionError.class).hasMessageContaining(MEMBERSHIP).hasMessageContaining("debug");
    }

    @Test
    void anExampleWithAnImpossibleDateTimeFails() {
        // It matches the Timestamp pattern; only the date-time format rejects it.
        assertThatThrownBy(() -> assertEdited(QUOTE, "issuedAt: '2026-11-10T09:00:00.000Z'",
                "issuedAt: '2026-99-99T99:99:99.000Z'")).isInstanceOf(AssertionError.class)
                .hasMessageContaining("/issuedAt");
    }

    @Test
    void aParameterExampleThatItsSchemaRefusesFails() {
        assertThatThrownBy(() -> assertEdited(IDEMPOTENCY_KEY, "example: 6f1d2c3b-4a5e-4f60-8172-93a4b5c6d7e8",
                "example: too-short")).isInstanceOf(AssertionError.class).hasMessageContaining(IDEMPOTENCY_KEY);
    }

    @Test
    void aProblemExampleWhoseTitleIsNotItsTypesFails() {
        assertThatThrownBy(() -> assertEdited(NO_OFFER, "title: No offer available", "title: No offers available"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("the registry's title");
    }

    @Test
    void aProblemExampleWhoseStatusIsNotItsResponsesFails() {
        assertThatThrownBy(() -> assertEdited(EXPIRED, "status: 422\n                    detail: The quote expired",
                "status: 409\n                    detail: The quote expired")).isInstanceOf(AssertionError.class)
                .hasMessageContaining("the status of its response");
    }

    @Test
    void aProblemExampleOfATypeThatItsResponseDoesNotNameFails() {
        // quote-expired is a 422 with its registry's title, but createQuote's 422 names only no-offer-available.
        String expired = """
                                expired:
                                  value:
                                    type: https://shipping-reference-platform.example/problems/quote-expired
                                    title: Quote expired
                                    status: 422
                                    detail: The quote expired at 2026-11-10T09:30:00.000Z. Request a fresh quote.
                                    instance: /api/v1/quotes
                                    correlationId: 7a6b5c4d-3e2f-4a1b-8c9d-0e1f2a3b4c5d
                """;
        String moved = NO_OFFER.substring(0, NO_OFFER.lastIndexOf('/') + 1) + "expired";

        assertThatThrownBy(() -> assertEdited(moved, "                routeNotServed:\n",
                expired + "                routeNotServed:\n")).isInstanceOf(AssertionError.class)
                .hasMessageContaining("a type that its response names");
    }

    @Test
    void aConstraintBesideTheRefOfAnExamplesSchemaApplies() {
        // The fixture's example satisfies the component Thing, but not maxProperties: 0 beside the $ref that names it.
        String fixture = "contract-fixtures/constraint-beside-ref.openapi.yaml";
        List<Contract.Example> examples = Contract.examples(Contract.parse(Contract.text(fixture)), "classpath:"
                + fixture);
        Contract.Example example = examples.getFirst();

        assertThat(pointers(examples)).containsExactly("#/paths/~1things/get/responses/200" + JSON + "one");
        contract.assertExample(new Contract.Example(example.pointer(), "classpath:" + fixture
                + "#/components/schemas/Thing", example.value(), false, 0, List.of()));
        assertThatThrownBy(() -> contract.assertExample(example)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(fixture + "#/paths/~1things/get/responses/200/content/application~1json/schema");
    }

    @Test
    void aUseThatOverridesTheDescriptionOfAResponseWithAProblemExampleFails() {
        String use = """
                                $ref: '#/components/schemas/ShipmentPage'
                        '400':
                          $ref: '#/components/responses/BadRequest'
                """;
        JsonNode document = edited(use, use + "          description: '`malformed-request` only.'\n");

        assertThatThrownBy(() -> Contract.examples(document)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("#/paths/~1shipments/get/responses/400 overrides the description of "
                        + "#/components/responses/BadRequest");
    }

    /**
     * Each place where this test reads no example: a header, a schema, a property's schema, a schema under a legacy
     * definitions keyword named like a keyword of data, and the shared examples.
     */
    static Stream<Arguments> places() {
        return Stream.of(
                Arguments.of("#/components/headers/CorrelationId/example", "  headers:\n    CorrelationId:\n",
                        "  headers:\n    CorrelationId:\n      example: x\n"),
                Arguments.of("#/components/schemas/Quote/example", "    Quote:\n      type: object\n",
                        "    Quote:\n      example: x\n      type: object\n"),
                Arguments.of("#/components/schemas/Quote/definitions/default/examples",
                        "    Quote:\n      type: object\n", """
                                    Quote:
                                      definitions:
                                        default:
                                          type: string
                                          examples: [42]
                                      type: object
                                """),
                Arguments.of("#/components/schemas/Money/properties/amount/example",
                        "        amount:\n          type: string\n",
                        "        amount:\n          example: x\n          type: string\n"),
                Arguments.of("#/components/examples", "\ncomponents:\n",
                        "\ncomponents:\n  examples:\n    shared:\n      value: x\n"));
    }

    @ParameterizedTest
    @MethodSource("places")
    void anExampleInAPlaceThisTestDoesNotReadFails(String example, String passage, String replacement) {
        JsonNode document = edited(passage, replacement);

        assertThatThrownBy(() -> Contract.examples(document)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(example + ": an example in a place where no example is read");
    }

    @ParameterizedTest
    @ValueSource(strings = {"externalValue", "$ref"})
    void anExampleThatDoesNotHoldItsValueItselfFails(String instead) {
        String example = "                clerkAlpha:\n";
        JsonNode document = edited(example, example + "                  " + instead
                + ": '#/components/examples/clerkAlpha'\n");

        assertThatThrownBy(() -> Contract.examples(document)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(MEMBERSHIP + ": an example holds its value itself");
    }

    @Test
    void aMemberNamedExampleInAPayloadOrInASchemasDataOrPropertiesIsData() {
        String recipient = """
                      description: Personal data. Use synthetic values only. Never logged.
                      properties:
                """;
        String named = """
                      description: Personal data. Use synthetic values only. Never logged.
                      dependentRequired:
                        example: [name]
                      properties:
                        example:
                          type: string
                """;
        JsonNode document = edited(recipient, named, ROLE, ROLE + "                    examples: {example: data}\n");

        assertThat(pointers(Contract.examples(document))).hasSize(9).isEqualTo(pointers(contract.examples()));
    }

    /** Asserts the example at {@code at} in the contract with one passage of its text replaced. */
    private void assertEdited(String at, String passage, String replacement) {
        JsonNode document = edited(passage, replacement);
        contract.assertExample(Contract.examples(document).stream().filter(e -> e.pointer().equals(at)).findFirst()
                .orElseThrow());
    }

    /** The contract's document with passages of its text replaced, each given with its replacement and found once. */
    private static JsonNode edited(String... passagesAndReplacements) {
        String text = Contract.text();
        for (int i = 0; i < passagesAndReplacements.length; i += 2) {
            String passage = passagesAndReplacements[i];
            assertThat(text.split(Pattern.quote(passage), -1).length - 1).as("the fixture's passage %s", passage)
                    .isEqualTo(1);
            text = text.replace(passage, passagesAndReplacements[i + 1]);
        }
        return Contract.parse(text);
    }

    private static List<String> pointers(List<Contract.Example> examples) {
        return examples.stream().map(Contract.Example::pointer).toList();
    }
}
