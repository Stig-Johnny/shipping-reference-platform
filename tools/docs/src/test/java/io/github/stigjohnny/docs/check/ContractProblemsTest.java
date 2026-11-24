package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.ProblemType;
import io.github.stigjohnny.docs.testing.Repository;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The contract's problem types, read strictly (ADR 0025): the registry in its description, with each type's status,
 * title and owners, and the types that each problem response's description names first, each registered with that
 * response's status. The negative fixtures edit one passage of the contract's text.
 */
class ContractProblemsTest {

    private static final String CONTRACT = Repository.read(ApiContract.PATH);
    private static final String EXPIRED_ROW = "| `quote-expired`                  | 422    | Quote expired                  "
            + "| shipments                    |";
    private static final String BOOKING_422 = "'`quote-expired`, `recipient-does-not-match-quote` or "
            + "`idempotency-key-reused`.'";
    private static final String QUOTE_404 = "        '404':\n          $ref: '#/components/responses/QuoteNotFound'\n";

    @Test
    void theRegistryIsReadWithItsOwnersAndTheRepositoryPasses() {
        ApiContract contract = ApiContract.parse(CONTRACT);

        assertThat(contract.problemTypes()).hasSize(18);
        assertThat(contract.problemType("quote-not-found")).hasValue(new ProblemType("quote-not-found", 404,
                "Quote not found", List.of("quotes", "shipments"), false, ""));
        assertThat(contract.problemType("validation-failed")).hasValue(new ProblemType("validation-failed", 400,
                "Request validation failed", List.of(), true, "errors"));
        assertThat(contract.problemType("no-offer-available")).hasValue(new ProblemType("no-offer-available", 422,
                "No offer available", List.of("quotes"), false, "reason"));
        assertThat(contract.problemType("not-found")).hasValue(new ProblemType("not-found", 404, "Resource not found",
                List.of(), false, ""));
        assertThat(problems(contract)).isEmpty();
    }

    /** A status that is no code, a type that is no code span, a row without its title, and a blank title. */
    @ParameterizedTest
    @ValueSource(strings = {"| `quote-expired` | 42 | Quote expired | shipments |",
        "| quote-expired | 422 | Quote expired | shipments |", "| `quote-expired` | 422 | shipments |",
        "| `quote-expired` | 422 | \t | shipments |"})
    void aRowThatIsNotOfTheRegistrysFormFails(String row) {
        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace(EXPIRED_ROW, row))).isInstanceOf(
                DocsException.class).hasMessageContaining("contracts/shipping.openapi.yaml: a row of the problem "
                        + "registry is not | `type` | status | title | raised by |: '" + row + "'");
    }

    @Test
    void aRowWithoutItsPipesFailsWithinTheTableOrOutsideIt() {
        String last = line("| `internal-error`");
        String row = "`quote-expired` | 422 | Quote expired | shipments |";

        // Right after the table the site's parser reads it as a row of the table; after a blank line, as a paragraph.
        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace(last + "\n", last + "\n    " + row + "\n")))
                .isInstanceOf(DocsException.class).hasMessageContaining("a row of the problem registry is not | "
                        + "`type` | status | title | raised by |: '" + row + "'");
        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace(last + "\n", last + "\n\n    " + row + "\n")))
                .isInstanceOf(DocsException.class).hasMessageContaining("the problem registry is one table, and this "
                        + "line of its section is not a row of it: '" + row + "'");
    }

    @Test
    void aSecondRegistrySectionFails() {
        String registry = CONTRACT.substring(CONTRACT.indexOf("    ## Errors (RFC 9457)\n"),
                CONTRACT.indexOf(line("| `internal-error`")) + line("| `internal-error`").length() + 1);
        String second = registry.substring(0, registry.indexOf(line("| `validation-failed`")))
                + "    | `quote-not-found` | 404 | Quote not found | shipping |\n";

        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace(registry, registry + "\n" + second)))
                .isInstanceOf(DocsException.class).hasMessageContaining("its description has 2 sections 'Errors (RFC "
                        + "9457)'; the problem registry stands in one (ADR 0025)");
    }

    /** A registry section in a quote, as a supplementary note might be written, and one in a list item. */
    @ParameterizedTest
    @ValueSource(strings = {"""
                > ## Errors (RFC 9457)
                >
                > | type (suffix) | status | title | raised by |
                > |---|---|---|---|
                > | `bad-type` | 404 | Bad type | shipping |

            """, "    - ## Errors (RFC 9457)\n\n"})
    void aRegistrySectionInsideAQuoteOrAListFails(String nested) {
        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace("    ## Errors (RFC 9457)\n", nested
                + "    ## Errors (RFC 9457)\n"))).isInstanceOf(DocsException.class).hasMessageContaining("its "
                        + "description has a section 'Errors (RFC 9457)' inside a quote or a list, which is not read; "
                        + "the problem registry stands in one section at the top level (ADR 0025)");
    }

    /** An underlined heading and a heading after a tab end the section, so a table after them is not the registry's. */
    @ParameterizedTest
    @ValueSource(strings = {"    Other section\n    -------------\n", "    ##\tOther section\n"})
    void aLaterHeadingOfEitherFormEndsTheRegistrysSection(String heading) {
        String last = line("| `internal-error`");

        assertThat(ApiContract.parse(CONTRACT.replace(last + "\n", last + "\n\n" + heading + "\n    | a | b |\n"
                + "    |---|---|\n    | 1 | 2 |\n")).problemTypes()).hasSize(18);
    }

    /** A rule of one column, a header with a cell after its last pipe, and a table under a later heading. */
    @ParameterizedTest
    @ValueSource(strings = {"rule", "header", "heading"})
    void aRegistryThatIsNotOneTableOfFourColumnsUnderItsHeadingFails(String broken) {
        String text = switch (broken) {
            case "rule" -> CONTRACT.replace(line("|---"), "    |---|");
            case "header" -> CONTRACT.replace(line("| type (suffix)"), line("| type (suffix)") + " extra-cell");
            default -> CONTRACT.replace("    ## Errors (RFC 9457)\n", "    ## Errors (RFC 9457)\n\n    ## Elsewhere\n");
        };

        assertThatThrownBy(() -> ApiContract.parse(text)).isInstanceOf(DocsException.class).hasMessageContaining(
                "the problem registry under '## Errors (RFC 9457)' is a table with the columns type (suffix), status, "
                        + "title, raised by and at least one row");
    }

    @Test
    void paddingInACellIsNotPartOfItsValue() {
        String padded = EXPIRED_ROW.replace("| `quote-expired`", "|  `quote-expired`").replace("| shipments ",
                "|  shipments");

        assertThat(ApiContract.parse(CONTRACT.replace(EXPIRED_ROW, padded)).problemType("quote-expired"))
                .hasValue(new ProblemType("quote-expired", 422, "Quote expired", List.of("shipments"), false, ""));
    }

    @Test
    void aTypeRegisteredTwiceFails() {
        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace(EXPIRED_ROW, EXPIRED_ROW.replace(
                "`quote-expired`  ", "`quote-not-found`")))).isInstanceOf(DocsException.class)
                .hasMessageContaining("`quote-not-found` is registered twice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"shipping", "quotes and shipments", "Shipments", "shipments (has reason"})
    void anOwnerThatIsNoModuleAnyModuleOrTheHttpEdgeFails(String owners) {
        String row = EXPIRED_ROW.replace("| shipments                    |", "| " + owners + " |");

        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace(EXPIRED_ROW, row))).isInstanceOf(
                DocsException.class).hasMessageContaining("`quote-expired` is raised by '" + owners + "'");
    }

    @Test
    void aContractWithoutTheRegistryFails() {
        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace("## Errors (RFC 9457)", "## Errors")))
                .isInstanceOf(DocsException.class).hasMessageContaining("its description has 0 sections 'Errors (RFC "
                        + "9457)'; the problem registry stands in one (ADR 0025)");
    }

    @Test
    void aResponseThatNamesAnUnregisteredTypeFails() {
        ApiContract contract = ApiContract.parse(CONTRACT.replace(BOOKING_422, BOOKING_422.replace("quote-expired",
                "quote-expird")));

        assertThat(problems(contract)).containsExactly("contracts/shipping.openapi.yaml bookShipment 422: "
                + "`quote-expird` is no type of the problem registry (ADR 0025)");
    }

    @Test
    void aResponseThatNamesATypeOfAnotherStatusFails() {
        ApiContract contract = ApiContract.parse(CONTRACT.replace("'`quote-not-found` or `offer-not-found`.'",
                "'`quote-not-found` or `quote-expired`.'"));

        assertThat(problems(contract)).containsExactly("contracts/shipping.openapi.yaml bookShipment 404: the "
                + "registry gives `quote-expired` the status 422 (ADR 0025)");
    }

    @Test
    void aProblemResponseWhoseDescriptionDoesNotStartWithItsTypesFails() {
        ApiContract contract = ApiContract.parse(CONTRACT.replace("'`no-offer-available`: the parcel cannot be "
                + "offered on this route;", "'The parcel cannot be offered on this route, `no-offer-available`;"));

        assertThat(problems(contract)).containsExactly("contracts/shipping.openapi.yaml createQuote 422: start the "
                + "description of a problem response with its problem types, such as `quote-not-found` or "
                + "`offer-not-found` (ADR 0025)");
    }

    @Test
    void aResponseKeyThatIsNoStatusCodeFails() {
        ApiContract contract = ApiContract.parse(CONTRACT.replace(QUOTE_404, QUOTE_404
                + "        default:\n          $ref: '#/components/responses/InternalError'\n"));

        assertThat(problems(contract)).containsExactly("contracts/shipping.openapi.yaml getQuote default: a "
                + "response is keyed by its status code; default and ranges such as 4XX are not read (ADR 0025)");
    }

    @Test
    void aDescriptionBesideAResponsesRefFails() {
        ApiContract contract = ApiContract.parse(CONTRACT.replace(QUOTE_404, QUOTE_404
                + "          description: '`quote-not-found`: none for this tenant.'\n"));

        assertThat(problems(contract)).containsExactly("contracts/shipping.openapi.yaml getQuote 404: write the "
                + "description in the response it references; a description beside a response's $ref is not read "
                + "(ADR 0025)");
    }

    @Test
    void aResponseReferenceNamesTheComponentThatDefinesTheResponse() {
        String alias = CONTRACT.replace("    QuoteNotFound:\n", "    QuoteNotFoundAlias:\n      $ref: "
                + "'#/components/responses/QuoteNotFound'\n    QuoteNotFound:\n");

        assertThat(problems(ApiContract.parse(alias.replace(QUOTE_404, QUOTE_404.replace("QuoteNotFound'",
                "QuoteNotFoundAlias'"))))).containsExactly("contracts/shipping.openapi.yaml getQuote 404: "
                        + "#/components/responses/QuoteNotFoundAlias is a reference itself; reference the component "
                        + "that defines the response (ADR 0025)");
        assertThat(problems(ApiContract.parse(CONTRACT.replace(QUOTE_404, QUOTE_404.replace("QuoteNotFound'",
                "QuoteMissing'"))))).containsExactly("contracts/shipping.openapi.yaml getQuote 404: "
                        + "#/components/responses/QuoteMissing names no response (ADR 0025)");
    }

    /** A path item's reference, webhooks, callbacks and an operationId twice, which no check would read. */
    static Stream<Arguments> unreadForms() {
        return Stream.of(
                Arguments.of("\npaths:\n", "\npaths:\n  /probe:\n    $ref: '#/components/pathItems/Probe'\n",
                        "contracts/shipping.openapi.yaml /probe: a path item's $ref is not read; write its operations "
                                + "under the path (ADR 0025)"),
                Arguments.of("\npaths:\n", "\nwebhooks: {}\npaths:\n", "contracts/shipping.openapi.yaml: webhooks are "
                        + "not read; write each operation under paths (ADR 0025)"),
                Arguments.of("      operationId: getQuote\n", "      operationId: getQuote\n      callbacks: {}\n",
                        "contracts/shipping.openapi.yaml /quotes/{quoteId} get: callbacks are not read (ADR 0025)"),
                Arguments.of("      operationId: getShipment\n", "      operationId: getQuote\n",
                        "contracts/shipping.openapi.yaml /shipments/{shipmentId} get: the operationId 'getQuote' names "
                                + "another operation too (ADR 0025)"));
    }

    @ParameterizedTest
    @MethodSource("unreadForms")
    void aFormOfOperationThatTheCheckDoesNotReadFails(String passage, String replacement, String message) {
        assertThatThrownBy(() -> ApiContract.parse(CONTRACT.replace(passage, replacement)))
                .isInstanceOf(DocsException.class).hasMessageContaining(message);
    }

    @Test
    void aTraceOperationIsReadToo() {
        ApiContract contract = ApiContract.parse(CONTRACT.replace("  /me:\n", "  /me:\n    trace:\n      "
                + "operationId: traceMe\n      tags: [tenants]\n      responses:\n        '404':\n          description: "
                + "'`quote-not-fund`: a probe.'\n"));

        assertThat(problems(contract)).containsExactly("contracts/shipping.openapi.yaml traceMe 404: `quote-not-fund` "
                + "is no type of the problem registry (ADR 0025)");
    }

    @Test
    void onlyTheCodeSpansThatStartADescriptionAreItsTypes() {
        assertThat(ApiContract.problemTypesNamed("`validation-failed` (with `errors`) or `malformed-request` (the "
                + "body is missing, or not JSON).")).containsExactly("validation-failed", "malformed-request");
        assertThat(ApiContract.problemTypesNamed("`quote-expired`, `recipient-does-not-match-quote`, or "
                + "`idempotency-key-reused`.")).containsExactly("quote-expired", "recipient-does-not-match-quote",
                        "idempotency-key-reused");
        assertThat(ApiContract.problemTypesNamed("`not-acceptable`: the `Accept` header excludes it."))
                .containsExactly("not-acceptable");
        assertThat(ApiContract.problemTypesNamed("`unauthenticated`")).containsExactly("unauthenticated");
        for (String none : List.of("`quote-expired` and `quote-not-found`.", "Expired: `quote-expired`.",
                "`Quote-expired`.", "`quote-expired` (no end", "`quote-expired` or")) {
            assertThat(ApiContract.problemTypesNamed(none)).as(none).isEmpty();
        }
    }

    /** The line of the contract's text that contains {@code text}, with its indentation, without its line break. */
    private static String line(String text) {
        return CONTRACT.lines().filter(l -> l.contains(text)).findFirst().orElseThrow();
    }

    private static List<String> problems(ApiContract contract) {
        Problems problems = new Problems();
        ContractProblems.check(contract, problems);
        return problems.list();
    }
}
