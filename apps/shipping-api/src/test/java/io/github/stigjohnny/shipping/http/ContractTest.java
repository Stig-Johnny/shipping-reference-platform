package io.github.stigjohnny.shipping.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The negative fixtures of the HTTP contract control (docs/capabilities/capabilities.yaml, control.http-contract): the
 * check that the HTTP contract tests apply fails on a response with an undocumented member, an impossible date-time,
 * an invalid header, a problem whose title is not its type's, and a status the operation does not document. A
 * conforming response passes, so the failures come from what each fixture breaks.
 */
class ContractTest {

    private static final String MEMBERSHIP = """
            {"userId": "00000000-0000-4000-8000-0000000000a1", "userDisplayName": "Clerk Alpha (synthetic)",
             "tenantId": "11111111-1111-4111-8111-111111111111", "tenantDisplayName": "Tenant Alpha (synthetic)",
             "role": "CLERK", "enabledFlags": []}""";
    private static final String NOT_FOUND = """
            {"type": "https://shipping-reference-platform.example/problems/quote-not-found", "title": "Quote not found",
             "status": 404, "detail": "No quote with this ID exists.",
             "instance": "/api/v1/quotes/5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21", "correlationId": "fixture-0001"}""";
    private static final String QUOTE_PATH = "/api/v1/quotes/5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21";
    /** The contract's example quote, as getQuote returns it. */
    private static final String QUOTE = """
            {"quoteId": "5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21", "status": "OPEN",
             "issuedAt": "2026-11-10T09:00:00.000Z", "expiresAt": "2026-11-10T09:30:00.000Z",
             "parcel": {"weightGrams": 1200, "lengthMm": 300, "widthMm": 200, "heightMm": 100},
             "origin": {"countryCode": "NO", "postcode": "0150"},
             "destination": {"countryCode": "SE", "postcode": "11122"}, "zone": "INTERNATIONAL",
             "billableWeightGrams": 1200,
             "offers": [{"offerId": "0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02", "serviceLevel": "STANDARD",
                         "price": {"amount": "18.81", "currency": "EUR"},
                         "transitDays": {"minDays": 3, "maxDays": 5}}]}""";

    private final Contract contract = Contract.contract();

    @Test
    void conformingResponsesPass() throws Exception {
        contract.assertConforms("get", "/me", response(200, MediaType.APPLICATION_JSON, MEMBERSHIP), "/api/v1/me");
        contract.assertConforms("get", "/quotes/{quoteId}", response(200, MediaType.APPLICATION_JSON, QUOTE),
                QUOTE_PATH);
        contract.assertConforms("get", "/quotes/{quoteId}", response(404, MediaType.APPLICATION_PROBLEM_JSON,
                NOT_FOUND), QUOTE_PATH);
    }

    @Test
    void anImpossibleDateTimeFails() throws Exception {
        // It matches the Timestamp pattern; only the date-time format rejects it.
        MockHttpServletResponse response = response(200, MediaType.APPLICATION_JSON,
                QUOTE.replace("2026-11-10T09:00:00.000Z", "2026-99-99T99:99:99.000Z"));

        assertThatThrownBy(() -> contract.assertConforms("get", "/quotes/{quoteId}", response, QUOTE_PATH))
                .isInstanceOf(AssertionError.class).hasMessageContaining("/issuedAt");
    }

    @Test
    void aResponseWithAnUndocumentedMemberFails() throws Exception {
        MockHttpServletResponse response = response(200, MediaType.APPLICATION_JSON,
                MEMBERSHIP.replace("\"role\"", "\"debug\": true, \"role\""));

        assertThatThrownBy(() -> contract.assertConforms("get", "/me", response, "/api/v1/me"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("debug");
    }

    @Test
    void anInvalidCorrelationIdHeaderFails() throws Exception {
        MockHttpServletResponse response = response(200, MediaType.APPLICATION_JSON, MEMBERSHIP);
        response.setHeader("Correlation-Id", "no");

        assertThatThrownBy(() -> contract.assertConforms("get", "/me", response, "/api/v1/me"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("header Correlation-Id");
    }

    @Test
    void aProblemWhoseTitleIsNotItsTypesFails() throws Exception {
        MockHttpServletResponse response = response(404, MediaType.APPLICATION_PROBLEM_JSON,
                NOT_FOUND.replace("Quote not found", "Quote missing"));

        assertThatThrownBy(() -> contract.assertConforms("get", "/quotes/{quoteId}", response, QUOTE_PATH))
                .isInstanceOf(AssertionError.class).hasMessageContaining("the registry's title");
    }

    @Test
    void aStatusTheOperationDoesNotDocumentFails() throws Exception {
        MockHttpServletResponse response = response(409, MediaType.APPLICATION_PROBLEM_JSON,
                NOT_FOUND.replace("404", "409"));

        assertThatThrownBy(() -> contract.assertConforms("get", "/me", response, "/api/v1/me"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("a status the contract allows on any path");
    }

    @Test
    void anExercisedResponseIsCheckedAgainstTheOperationThatItsRequestNames() throws Exception {
        contract.assertExercised("GET", QUOTE_PATH, response(200, MediaType.APPLICATION_JSON, QUOTE));
        MockHttpServletResponse undocumented = response(200, MediaType.APPLICATION_JSON,
                MEMBERSHIP.replace("\"role\"", "\"debug\": true, \"role\""));

        assertThatThrownBy(() -> contract.assertExercised("GET", "/api/v1/me", undocumented))
                .isInstanceOf(AssertionError.class).hasMessageContaining("debug");
        // A method that the path has no operation for is no operation: only a problem of any path conforms.
        assertThatThrownBy(() -> contract.assertExercised("DELETE", "/api/v1/me",
                response(200, MediaType.APPLICATION_JSON, MEMBERSHIP)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("a status the contract allows on any path");
    }

    @Test
    void aResponseToAPathThatIsNoOperationMustBeAProblemThatAnyPathCanGive() throws Exception {
        String notFound = NOT_FOUND.replace("quote-not-found", "not-found").replace("Quote not found",
                "Resource not found").replace(QUOTE_PATH, "/api/v1/parcels");
        String unauthenticated = NOT_FOUND.replace("quote-not-found", "unauthenticated")
                .replace("Quote not found", "Authentication required").replace("404", "401")
                .replace(QUOTE_PATH, "/actuator/health");
        MockHttpServletResponse challenged = response(401, MediaType.APPLICATION_PROBLEM_JSON, unauthenticated);
        challenged.setHeader("WWW-Authenticate", "Bearer realm=\"shipping-reference-platform\"");

        contract.assertExercised("GET", "/api/v1/parcels", response(404, MediaType.APPLICATION_PROBLEM_JSON, notFound));
        contract.assertExercised("GET", "/actuator/health", challenged);
        assertThatThrownBy(() -> contract.assertExercised("GET", "/actuator/health",
                response(401, MediaType.APPLICATION_PROBLEM_JSON, unauthenticated)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("required header WWW-Authenticate is missing");
        assertThatThrownBy(() -> contract.assertExercised("GET", "/api/v1/parcels",
                response(200, MediaType.APPLICATION_JSON, MEMBERSHIP)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("a status the contract allows on any path");
    }

    @Test
    void theProblemsOfAnyPathAreTheOnesTheContractNames() {
        assertThat(Contract.anyPathTypes()).containsExactly(entry(400, "malformed-request"),
                entry(401, "unauthenticated"), entry(403, "no-tenant-membership"), entry(404, "not-found"),
                entry(405, "method-not-allowed"));
        Contract.anyPathTypes().forEach((status, type) -> assertThat(contract.description().replaceAll("\\s+", " "))
                .contains("`" + status + " " + type + "`"));
    }

    @Test
    void anInstanceIsThePathWhereTheContractSaysItCanBeRepeated() {
        String longest = "/api/v1/" + "p".repeat(2040);

        assertThat(Contract.instanceFor(longest)).hasSize(2048).isEqualTo(longest);
        assertThat(Contract.instanceFor(longest + "p")).isEqualTo("about:blank");
        assertThat(Contract.instanceFor("/api/v1/parcels/{unknown}")).isEqualTo("about:blank");
        assertThat(Contract.instanceFor("/api/v1/me;test=1")).isEqualTo("/api/v1/me;test=1");
    }

    @Test
    void theRegistryIsTheContracts() throws Exception {
        assertThat(contract.problemTitle("no-offer-available")).isEqualTo("No offer available");
        assertThat(contract.problemTitle("internal-error")).isEqualTo("Internal error");
    }

    private static MockHttpServletResponse response(int status, MediaType type, String body) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        response.setContentType(type.toString());
        response.setHeader("Correlation-Id", "fixture-0001");
        response.setHeader("Cache-Control", "no-store");
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter().write(body);
        return response;
    }
}
