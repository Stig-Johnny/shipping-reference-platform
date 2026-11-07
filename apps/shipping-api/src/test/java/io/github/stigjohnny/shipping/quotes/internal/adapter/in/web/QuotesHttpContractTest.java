package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import static io.github.stigjohnny.shipping.http.Http.bearer;
import static io.github.stigjohnny.shipping.http.Http.json;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_ALPHA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_BETA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.stigjohnny.shipping.http.ApiTest;
import io.github.stigjohnny.shipping.http.Contract;
import io.github.stigjohnny.shipping.http.Http;
import io.github.stigjohnny.shipping.http.TestClock;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * POST /api/v1/quotes (createQuote) and GET /api/v1/quotes/{quoteId} (getQuote) against the contract (invariant
 * Q10): a quote with its offers in level order and its expiry, 422 no-offer-available with the reason, validation
 * failures with the invalid values named, a quote read back open and then expired, and another tenant's quote reported
 * exactly like an unknown one. The store is the in-memory test double and the clock a test clock, so this test proves
 * the HTTP edge, not the database.
 */
@ApiTest
class QuotesHttpContractTest {

    /** The contract's example request. */
    private static final String EXAMPLE = """
            {"parcel": {"weightGrams": 1200, "lengthMm": 300, "widthMm": 200, "heightMm": 100},
             "origin": {"countryCode": "NO", "postcode": "0150"},
             "destination": {"countryCode": "SE", "postcode": "11122"}}""";
    private static final String UNKNOWN_QUOTE = "/api/v1/quotes/5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21";

    private final Contract contract = Contract.contract();
    private final MockMvc http;
    private final TestClock clock;

    QuotesHttpContractTest(@Autowired WebApplicationContext context, @Autowired TestClock clock) {
        http = Http.mockMvc(context);
        this.clock = clock;
    }

    @BeforeEach
    void atTheContractsExampleInstant() {
        clock.set(TestClock.START);
    }

    @Test
    void aQuoteHasOneOfferPerLevelInLevelOrderAndExpiresThirtyMinutesAfterIssue() throws Exception {
        MvcResult result = create(CLERK_ALPHA, EXAMPLE);

        JsonNode quote = contract.assertConforms("post", "/quotes", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/v1/quotes/" + quote.path("quoteId").stringValue());
        assertThat(withoutIds(quote)).isEqualTo(json("""
                {"status": "OPEN", "issuedAt": "2026-11-10T09:00:00.000Z", "expiresAt": "2026-11-10T09:30:00.000Z",
                 "parcel": {"weightGrams": 1200, "lengthMm": 300, "widthMm": 200, "heightMm": 100},
                 "origin": {"countryCode": "NO", "postcode": "0150"},
                 "destination": {"countryCode": "SE", "postcode": "11122"},
                 "zone": "INTERNATIONAL", "billableWeightGrams": 1200,
                 "offers": [
                   {"serviceLevel": "ECONOMY", "price": {"amount": "15.05", "currency": "EUR"},
                    "transitDays": {"minDays": 5, "maxDays": 8}},
                   {"serviceLevel": "STANDARD", "price": {"amount": "18.81", "currency": "EUR"},
                    "transitDays": {"minDays": 3, "maxDays": 5}},
                   {"serviceLevel": "EXPRESS", "price": {"amount": "28.22", "currency": "EUR"},
                    "transitDays": {"minDays": 1, "maxDays": 2}}]}"""));
    }

    @Test
    void aRequestedLevelIsTheOnlyOffer() throws Exception {
        MvcResult result = create(CLERK_ALPHA, EXAMPLE.replace("\"destination\"",
                "\"serviceLevel\": \"STANDARD\", \"destination\""));

        JsonNode quote = contract.assertConforms("post", "/quotes", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(withoutIds(quote).path("offers")).isEqualTo(json("""
                [{"serviceLevel": "STANDARD", "price": {"amount": "18.81", "currency": "EUR"},
                  "transitDays": {"minDays": 3, "maxDays": 5}}]"""));
    }

    static Stream<Arguments> unavailable() {
        return Stream.of(
                Arguments.of(EXAMPLE.replace("\"SE\"", "\"US\""), "ROUTE_NOT_SERVED",
                        "The route from NO to US is not served by the simulated carrier."),
                Arguments.of(EXAMPLE.replace("\"lengthMm\": 300, \"widthMm\": 200, \"heightMm\": 100",
                        "\"lengthMm\": 1200, \"widthMm\": 1200, \"heightMm\": 1200"), "BILLABLE_WEIGHT_ABOVE_LIMIT",
                        "The billable weight of 345600 g is above the limit of 30000 g."),
                Arguments.of(EXAMPLE.replace("\"weightGrams\": 1200", "\"weightGrams\": 12000")
                        .replace("\"destination\"", "\"serviceLevel\": \"EXPRESS\", \"destination\""),
                        "SERVICE_LEVEL_NOT_AVAILABLE", "EXPRESS is not available for a billable weight of 12000 g."));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("unavailable")
    void nothingOfferableIs422NoOfferAvailableWithTheReason(String request, String reason, String detail)
            throws Exception {
        MvcResult result = create(CLERK_ALPHA, request);

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(problem.path("type").stringValue()).endsWith("/no-offer-available");
        assertThat(problem.path("reason").stringValue()).isEqualTo(reason);
        assertThat(problem.path("detail").stringValue()).isEqualTo(detail);
    }

    static Stream<Arguments> invalid() {
        String weight = "must be between 1 and 30000";
        String side = "must be between 1 and 1200";
        String level = "must be one of ECONOMY, STANDARD, EXPRESS";
        return Stream.of(
                Arguments.of("the contract's example of a weight too high",
                        EXAMPLE.replace("\"weightGrams\": 1200", "\"weightGrams\": 30001"),
                        errors(body("/parcel/weightGrams", weight))),
                Arguments.of("a weight of zero", EXAMPLE.replace("\"weightGrams\": 1200", "\"weightGrams\": 0"),
                        errors(body("/parcel/weightGrams", weight))),
                Arguments.of("a side too long", EXAMPLE.replace("\"lengthMm\": 300", "\"lengthMm\": 1201"),
                        errors(body("/parcel/lengthMm", side))),
                Arguments.of("a missing destination", EXAMPLE.replaceFirst(",\\s*\"destination\": \\{[^}]*}", ""),
                        errors(body("/destination", "is required"))),
                Arguments.of("a lower-case country", EXAMPLE.replace("\"NO\"", "\"no\""),
                        errors(body("/origin/countryCode", "must be two upper-case letters (ISO 3166-1 alpha-2)"))),
                Arguments.of("two invalid values, named in order", EXAMPLE.replace("\"0150\"", "\"01_50\"")
                        .replace("\"heightMm\": 100", "\"heightMm\": 0"),
                        errors(body("/origin/postcode", "must be 2 to 10 upper-case letters, digits, spaces or "
                                + "hyphens, starting and ending with a letter or digit"),
                                body("/parcel/heightMm", side))),
                Arguments.of("a weight as a string",
                        EXAMPLE.replace("\"weightGrams\": 1200", "\"weightGrams\": \"1200\""),
                        errors(body("/parcel/weightGrams", "must be an integer"))),
                Arguments.of("a weight beyond any integer",
                        EXAMPLE.replace("\"weightGrams\": 1200", "\"weightGrams\": 99999999999"),
                        errors(body("/parcel/weightGrams", "is out of range"))),
                Arguments.of("a country as a number", EXAMPLE.replace("\"NO\"", "47"),
                        errors(body("/origin/countryCode", "must be a string"))),
                Arguments.of("a weight with a fraction",
                        EXAMPLE.replace("\"weightGrams\": 1200", "\"weightGrams\": 1200.5"),
                        errors(body("/parcel/weightGrams", "must be an integer"))),
                Arguments.of("an unknown service level",
                        EXAMPLE.replace("\"destination\"", "\"serviceLevel\": \"FAST\", \"destination\""),
                        errors(body("/serviceLevel", level))),
                Arguments.of("a service level by number",
                        EXAMPLE.replace("\"destination\"", "\"serviceLevel\": 1, \"destination\""),
                        errors(body("/serviceLevel", level))),
                Arguments.of("a parcel that is not an object",
                        EXAMPLE.replaceFirst("\\{\"weightGrams\"[^}]*}", "\"small\""),
                        errors(body("/parcel", "must be an object"))),
                Arguments.of("an unknown member", EXAMPLE.replace("\"origin\"", "\"insurance\": true, \"origin\""),
                        errors(body("/insurance", "is not a member of this object"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalid")
    void anInvalidRequestIs400ValidationFailedNamingEachInvalidValue(String what, String request, String errors)
            throws Exception {
        MvcResult result = create(CLERK_ALPHA, request);

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(problem.path("type").stringValue()).endsWith("/validation-failed");
        assertThat(problem.path("errors")).isEqualTo(json(errors));
        int count = problem.path("errors").size();
        assertThat(problem.path("detail").stringValue())
                .isEqualTo("The request has " + count + " invalid value" + (count == 1 ? "." : "s."));
    }

    @Test
    void aQuoteIsReadBackOpenUntilItsExpiryAndExpiredFromIt() throws Exception {
        MvcResult created = create(CLERK_ALPHA, EXAMPLE);
        String location = String.valueOf(created.getResponse().getHeader(HttpHeaders.LOCATION));
        JsonNode issued = json(created);

        clock.set(Instant.parse("2026-11-10T09:29:59.999Z"));
        MvcResult open = http.perform(bearer(get(location), CLERK_ALPHA)).andReturn();
        clock.set(Instant.parse("2026-11-10T09:30:00.000Z"));
        MvcResult expired = http.perform(bearer(get(location), CLERK_ALPHA)).andReturn();

        assertThat(contract.assertConforms("get", "/quotes/{quoteId}", open)).isEqualTo(issued);
        JsonNode read = contract.assertConforms("get", "/quotes/{quoteId}", expired);
        assertThat(read.path("status").stringValue()).isEqualTo("EXPIRED");
        assertThat(((ObjectNode) read).put("status", "OPEN")).isEqualTo(issued);
    }

    @Test
    void anUnknownQuoteIs404QuoteNotFound() throws Exception {
        MvcResult result = http.perform(bearer(get(UNKNOWN_QUOTE), CLERK_ALPHA)).andReturn();

        JsonNode problem = contract.assertConforms("get", "/quotes/{quoteId}", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(problem.path("type").stringValue()).endsWith("/quote-not-found");
        assertThat(problem.path("detail").stringValue()).isEqualTo("No quote with this ID exists.");
    }

    @Test
    void anotherTenantsQuoteIsReportedExactlyLikeAnUnknownOne() throws Exception {
        String alphasQuote = String.valueOf(create(CLERK_ALPHA, EXAMPLE).getResponse().getHeader(HttpHeaders.LOCATION));

        MvcResult foreign = http.perform(bearer(get(alphasQuote), CLERK_BETA)).andReturn();
        MvcResult unknown = http.perform(bearer(get(UNKNOWN_QUOTE), CLERK_BETA)).andReturn();

        JsonNode foreignProblem = contract.assertConforms("get", "/quotes/{quoteId}", foreign);
        JsonNode unknownProblem = contract.assertConforms("get", "/quotes/{quoteId}", unknown);
        assertThat(foreign.getResponse().getStatus()).isEqualTo(unknown.getResponse().getStatus()).isEqualTo(404);
        assertThat(foreign.getResponse().getHeaderNames()).isEqualTo(unknown.getResponse().getHeaderNames());
        // The two differ only in what the request itself says: its path and its correlation ID.
        assertThat(withoutRequestEcho(foreignProblem)).isEqualTo(withoutRequestEcho(unknownProblem));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-quote-id", "5B0F6C3E-2A43-4C1E-9D55-0D6F3E1A7B21",
            "5b0f6c3e2a434c1e9d550d6f3e1a7b21"})
    void aQuoteIdThatIsNotALowerCaseUuidIs400ValidationFailed(String quoteId) throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/quotes/" + quoteId), CLERK_ALPHA)).andReturn();

        JsonNode problem = contract.assertConforms("get", "/quotes/{quoteId}", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(problem.path("errors")).isEqualTo(json("""
                [{"in": "path", "name": "quoteId", "detail": "must be a lowercase UUID"}]"""));
    }

    @Test
    void aBodyThatIsNotJsonIs415UnsupportedMediaType() throws Exception {
        MvcResult result = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA)
                .contentType(MediaType.TEXT_PLAIN).content(EXAMPLE)).andReturn();

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(problem.path("type").stringValue()).endsWith("/unsupported-media-type");
    }

    @Test
    void aQuoteThatTheClientCannotAcceptAsJsonIs406AndIsNotCreated() throws Exception {
        MvcResult result = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_HTML).content(EXAMPLE)).andReturn();

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(406);
        assertThat(problem.path("type").stringValue()).endsWith("/not-acceptable");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
    }

    private MvcResult create(String token, String request) throws Exception {
        return http.perform(bearer(post("/api/v1/quotes"), token).contentType(MediaType.APPLICATION_JSON)
                .content(request)).andReturn();
    }

    /** The quote without its generated identifiers, which only the store and the ID generator decide. */
    private static JsonNode withoutIds(JsonNode quote) {
        ObjectNode copy = (ObjectNode) quote.deepCopy();
        copy.remove("quoteId");
        copy.path("offers").values().forEach(offer -> ((ObjectNode) offer).remove("offerId"));
        return copy;
    }

    /** An invalid value in the body, as the contract's InvalidParam writes it. */
    private static String body(String name, String detail) {
        return "{\"in\": \"body\", \"name\": \"" + name + "\", \"detail\": \"" + detail + "\"}";
    }

    private static String errors(String... errors) {
        return "[" + String.join(", ", errors) + "]";
    }

    private static JsonNode withoutRequestEcho(JsonNode problem) {
        ObjectNode copy = (ObjectNode) problem.deepCopy();
        copy.remove("instance");
        copy.remove("correlationId");
        return copy;
    }
}
