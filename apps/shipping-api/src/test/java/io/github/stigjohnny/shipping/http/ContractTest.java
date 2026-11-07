package io.github.stigjohnny.shipping.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The negative fixtures of the HTTP contract control (docs/capabilities/capabilities.yaml, control.http-contract): the
 * check that the HTTP contract tests apply fails on a response with an undocumented member, an invalid header, a
 * problem whose title is not its type's, and a status the operation does not document. A conforming response passes,
 * so the failures come from what each fixture breaks.
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

    private final Contract contract = Contract.contract();

    @Test
    void conformingResponsesPass() throws Exception {
        contract.assertConforms("get", "/me", response(200, MediaType.APPLICATION_JSON, MEMBERSHIP), "/api/v1/me");
        contract.assertConforms("get", "/quotes/{quoteId}", response(404, MediaType.APPLICATION_PROBLEM_JSON,
                NOT_FOUND), QUOTE_PATH);
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
