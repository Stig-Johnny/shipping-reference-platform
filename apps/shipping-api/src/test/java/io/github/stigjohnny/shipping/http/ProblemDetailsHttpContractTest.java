package io.github.stigjohnny.shipping.http;

import static io.github.stigjohnny.shipping.http.Http.bearer;
import static io.github.stigjohnny.shipping.http.Http.json;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_ALPHA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.FAILING_TOKEN;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.NO_TENANT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import io.github.stigjohnny.shipping.quotes.internal.application.QuotesTestDoubles;
import io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

/**
 * The HTTP edge: the isolated root classes CorrelationIdFilter and FallbackProblemHandler (ADR 0004). Framework
 * failures are RFC 9457 problems with exactly the contract's types, titles and statuses: an unknown path, a method the
 * path does not support, a request that the server refuses before routing, a body that is not JSON or not parseable or
 * has an unknown member, a response the client does not accept, and an unexpected exception in a controller or in a
 * filter, which says nothing about its cause. A problem repeats what the request says only within the contract's
 * bounds: at the bounds of a path and of a member's pointer, the boundary cases. Every response carries a correlation
 * ID, the client's when it is valid and a new UUID otherwise, and is not cached. Logs carry the correlation ID and never
 * a request body.
 */
@ApiTest
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsHttpContractTest {

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final String QUOTE_REQUEST = """
            {"parcel": {"weightGrams": 1200, "lengthMm": 300, "widthMm": 200, "heightMm": 100},
             "origin": {"countryCode": "NO", "postcode": "0150"},
             "destination": {"countryCode": "SE", "postcode": "11122"}}""";

    private final Contract contract = Contract.contract();
    private final MockMvc http;

    ProblemDetailsHttpContractTest(@Autowired WebApplicationContext context) {
        http = Http.mockMvc(context);
    }

    @Test
    void anUnknownPathIs404NotFound() throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/parcels"), CLERK_ALPHA)).andReturn();

        JsonNode problem = contract.assertGenericProblem(result);
        assertThat(problem.path("status").asInt()).isEqualTo(404);
        assertThat(problem.path("detail").stringValue()).isEqualTo("No resource exists at this path.");
    }

    @Test
    void nothingIsServedOutsideTheApiNotEvenToAMember() throws Exception {
        MvcResult member = http.perform(bearer(get("/actuator/health"), CLERK_ALPHA)).andReturn();
        MvcResult userWithoutTenant = http.perform(bearer(get("/actuator/health"), NO_TENANT_USER)).andReturn();
        MvcResult anonymous = http.perform(get("/actuator/health")).andReturn();

        assertThat(contract.assertGenericProblem(member).path("status").asInt()).isEqualTo(404);
        assertThat(contract.assertGenericProblem(userWithoutTenant).path("type").stringValue())
                .endsWith("/no-tenant-membership");
        assertThat(contract.assertGenericProblem(anonymous).path("type").stringValue()).endsWith("/unauthenticated");
    }

    static Stream<Arguments> refusedByTheFirewall() {
        return Stream.of(Arguments.of("a semicolon in the path", HttpMethod.GET, "/api/v1/me;test=1"),
                Arguments.of("an encoded slash in the path", HttpMethod.GET,
                        "/api/v1/quotes%2F5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21"),
                Arguments.of("a method the server does not accept", HttpMethod.valueOf("TRACE"), "/api/v1/me"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedByTheFirewall")
    void aRequestTheFirewallRefusesIs400MalformedRequestBeforeAuthentication(String what, HttpMethod method,
            String path) throws Exception {
        MvcResult member = http.perform(bearer(request(method, URI.create(path)), CLERK_ALPHA)
                .header("Correlation-Id", "firewall-check-0001")).andReturn();
        MvcResult anonymous = http.perform(request(method, URI.create(path))).andReturn();

        for (MvcResult result : List.of(member, anonymous)) {
            JsonNode problem = contract.assertGenericProblem(result);
            assertThat(problem.path("status").asInt()).isEqualTo(400);
            assertThat(problem.path("type").stringValue()).endsWith("/malformed-request");
            assertThat(problem.path("detail").stringValue()).isEqualTo("The server refused the request before "
                    + "routing: it does not accept its path, its method or its headers.");
            assertThat(problem.path("instance").stringValue()).isEqualTo(path);
        }
        assertThat(member.getResponse().getHeader("Correlation-Id")).isEqualTo("firewall-check-0001");
    }

    @Test
    void aPathOfTheLongestLengthAnInstanceRepeatsIsRepeated() throws Exception {
        String path = "/api/v1/" + "p".repeat(2048 - "/api/v1/".length());

        MvcResult result = http.perform(bearer(get(URI.create(path)), CLERK_ALPHA)).andReturn();

        JsonNode problem = contract.assertGenericProblem(result);
        assertThat(problem.path("status").asInt()).isEqualTo(404);
        assertThat(problem.path("instance").stringValue()).hasSize(2048).isEqualTo(path);
    }

    @Test
    void aLongerPathIsRefusedBeforeRoutingWithoutRepeatingIt() throws Exception {
        String path = "/api/v1/" + "p".repeat(2049 - "/api/v1/".length());

        MvcResult member = http.perform(bearer(get(URI.create(path)), CLERK_ALPHA)).andReturn();
        MvcResult anonymous = http.perform(get(URI.create(path))).andReturn();

        for (MvcResult result : List.of(member, anonymous)) {
            JsonNode problem = contract.assertGenericProblem(result);
            assertThat(problem.path("status").asInt()).isEqualTo(400);
            assertThat(problem.path("type").stringValue()).endsWith("/malformed-request");
            assertThat(problem.path("detail").stringValue()).isEqualTo("The request path is longer than 2048 "
                    + "characters.");
            assertThat(problem.path("instance").stringValue()).isEqualTo("about:blank");
        }
    }

    @Test
    void aPathThatIsNoUriReferenceIsRefusedBeforeRoutingWithoutRepeatingIt() throws Exception {
        // A servlet container refuses such a request line itself; MockMvc lets the test send it.
        MvcResult result = http.perform(bearer(get("/api/v1/parcels"), CLERK_ALPHA).with(request -> {
            request.setRequestURI("/api/v1/parcels/{unknown}");
            return request;
        })).andReturn();

        JsonNode problem = contract.assertGenericProblem(result);
        assertThat(problem.path("status").asInt()).isEqualTo(400);
        assertThat(problem.path("detail").stringValue()).isEqualTo("The request path is not a valid URI reference.");
        assertThat(problem.path("instance").stringValue()).isEqualTo("about:blank");
    }

    static Stream<Arguments> unknownMembers() {
        return Stream.of(
                Arguments.of("a top-level member whose pointer has 200 characters", topLevel("m".repeat(199)),
                        "/" + "m".repeat(199)),
                Arguments.of("a top-level member whose pointer has 201 characters", topLevel("m".repeat(200)), null),
                Arguments.of("a member of parcel whose pointer has 200 characters", inParcel("m".repeat(192)),
                        "/parcel/" + "m".repeat(192)),
                Arguments.of("a member of parcel whose pointer has 201 characters", inParcel("m".repeat(193)), null),
                Arguments.of("a member whose escaped pointer has 199 characters", topLevel("~".repeat(99)),
                        "/" + "~0".repeat(99)),
                Arguments.of("a member whose escaped pointer has 201 characters", topLevel("~".repeat(100)), null));
    }

    /** The quote request with an unknown member of this name before its first member. */
    private static String topLevel(String name) {
        return QUOTE_REQUEST.replaceFirst("\\{", "{\"" + name + "\": 1, ");
    }

    /** The quote request with an unknown member of this name in its parcel. */
    private static String inParcel(String name) {
        return QUOTE_REQUEST.replace("{\"weightGrams\"", "{\"" + name + "\": 1, \"weightGrams\"");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unknownMembers")
    void anUnknownMemberIsNamedOnlyWhenItsPointerFitsTheContractsBound(String what, String body,
            @Nullable String pointer) throws Exception {
        MvcResult result = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(problem.path("status").asInt()).isEqualTo(400);
        if (pointer != null) {
            assertThat(problem.path("type").stringValue()).endsWith("/validation-failed");
            assertThat(problem.path("errors").path(0).path("name").stringValue()).isEqualTo(pointer);
        } else {
            assertThat(problem.path("type").stringValue()).endsWith("/malformed-request");
            assertThat(problem.path("detail").stringValue()).isEqualTo("The request body has an unknown member "
                    + "whose JSON Pointer is longer than 200 characters, too long to name.");
            assertThat(problem.has("errors")).isFalse();
        }
    }

    @Test
    void aMethodThePathDoesNotSupportIs405MethodNotAllowed() throws Exception {
        MvcResult result = http.perform(bearer(delete("/api/v1/me"), CLERK_ALPHA)).andReturn();

        JsonNode problem = contract.assertGenericProblem(result);
        assertThat(problem.path("status").asInt()).isEqualTo(405);
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET");
    }

    @Test
    void aBodyThatIsNotJsonIs415UnsupportedMediaType() throws Exception {
        MvcResult result = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA)
                .contentType(MediaType.APPLICATION_XML).content("<parcel/>")).andReturn();

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(problem.path("status").asInt()).isEqualTo(415);
        assertThat(problem.path("type").stringValue()).endsWith("/unsupported-media-type");
    }

    @Test
    void aResponseTheClientDoesNotAcceptIs406NotAcceptable() throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA).accept(MediaType.TEXT_PLAIN))
                .andReturn();

        JsonNode problem = contract.assertConforms("get", "/me", result);
        assertThat(problem.path("status").asInt()).isEqualTo(406);
        assertThat(problem.path("type").stringValue()).endsWith("/not-acceptable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"parcel\": ", "{\"parcel\": {}} {}", "not json", ""})
    void aBodyThatIsMissingOrNotParseableIs400MalformedRequest(String body) throws Exception {
        MvcResult result = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(problem.path("status").asInt()).isEqualTo(400);
        assertThat(problem.path("type").stringValue()).endsWith("/malformed-request");
        assertThat(problem.path("detail").stringValue()).isEqualTo("The request body is missing or is not valid JSON.");
        assertThat(problem.has("errors")).isFalse();
    }

    @Test
    void anUnknownMemberIs400ValidationFailed() throws Exception {
        MvcResult result = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(QUOTE_REQUEST.replace("\"weightGrams\"", "\"fragile\": true, \"weightGrams\""))).andReturn();

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(problem.path("type").stringValue()).endsWith("/validation-failed");
        assertThat(problem.path("errors")).isEqualTo(json("""
                [{"in": "body", "name": "/parcel/fragile", "detail": "is not a member of this object"}]"""));
    }

    @Test
    void anUnexpectedFailureInAControllerIs500WithoutItsCause(CapturedOutput output) throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/quotes/" + QuotesTestDoubles.FAILING_QUOTE.value()),
                CLERK_ALPHA)).andReturn();

        assertInternalError(contract.assertConforms("get", "/quotes/{quoteId}", result), result,
                QuotesTestDoubles.FAILURE_MESSAGE, output);
    }

    @Test
    void anUnexpectedFailureInAFilterIs500WithoutItsCause(CapturedOutput output) throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/me"), FAILING_TOKEN)).andReturn();

        assertInternalError(contract.assertConforms("get", "/me", result), result,
                FixtureMembershipDirectory.FAILURE_MESSAGE, output);
    }

    @Test
    void aValidCorrelationIdFromTheClientIsEchoedInTheHeaderAndTheProblem() throws Exception {
        MvcResult problem = http.perform(get("/api/v1/me").header("Correlation-Id", "client-chosen-0001")).andReturn();
        MvcResult success = http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA)
                .header("Correlation-Id", "client-chosen-0002")).andReturn();

        assertThat(contract.assertConforms("get", "/me", problem).path("correlationId").stringValue())
                .isEqualTo("client-chosen-0001");
        assertThat(problem.getResponse().getHeader("Correlation-Id")).isEqualTo("client-chosen-0001");
        contract.assertConforms("get", "/me", success);
        assertThat(success.getResponse().getHeader("Correlation-Id")).isEqualTo("client-chosen-0002");
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "has a space-1234", "under_score_1234", "ninety-nine-characters-are-far-too-many-"
            + "for-a-correlation-id-whose-limit-is-sixty-four", "ümlaut-12345678", ""})
    void anInvalidCorrelationIdIsReplacedByANewUuid(String sent) throws Exception {
        MvcResult result = http.perform(get("/api/v1/me").header("Correlation-Id", sent)).andReturn();

        String correlationId = result.getResponse().getHeader("Correlation-Id");
        assertThat(correlationId).isNotEqualTo(sent).matches(UUID);
        assertThat(contract.assertConforms("get", "/me", result).path("correlationId").stringValue())
                .isEqualTo(correlationId);
    }

    @Test
    void withoutACorrelationIdEachRequestGetsANewUuid() throws Exception {
        String first = http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA)).andReturn().getResponse()
                .getHeader("Correlation-Id");
        String second = http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA)).andReturn().getResponse()
                .getHeader("Correlation-Id");

        assertThat(first).matches(UUID);
        assertThat(second).matches(UUID).isNotEqualTo(first);
        // MockMvc runs the request on this thread: the logging context lasted for the request only.
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void noRequestBodyIsLogged(CapturedOutput output) throws Exception {
        String marker = "MARKER9D2B";
        String[] bodies = {"{\"parcel\": \"" + marker, QUOTE_REQUEST.replace("\"0150\"", "\"" + marker + " 1\""),
                QUOTE_REQUEST.replace("\"origin\"", "\"note\": \"" + marker + "\", \"origin\""),
                QUOTE_REQUEST.replace("\"SE\"", "\"" + marker + "\"")};
        for (String body : bodies) {
            MvcResult result = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(marker);
        }
        http.perform(bearer(get("/api/v1/quotes/" + QuotesTestDoubles.FAILING_QUOTE.value()), CLERK_ALPHA)
                .content("{\"" + marker + "\": 1}"));

        assertThat(output.getAll()).contains("Unexpected failure").doesNotContain(marker);
    }

    private static void assertInternalError(JsonNode problem, MvcResult result, String cause, CapturedOutput output)
            throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(problem.path("type").stringValue()).endsWith("/internal-error");
        assertThat(problem.path("detail").stringValue())
                .isEqualTo("An unexpected error occurred. Quote the correlation ID when you report it.");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(cause,
                "Exception", "java.", "io.github", "\tat ", "trace");
        // The log has the cause, under the response's correlation ID.
        String correlationId = String.valueOf(result.getResponse().getHeader("Correlation-Id"));
        assertThat(output.getAll().lines().filter(line -> line.contains("[" + correlationId + "]")))
                .anySatisfy(line -> assertThat(line).contains("Unexpected failure"));
        assertThat(output.getAll()).contains(cause);
    }
}
