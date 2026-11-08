package io.github.stigjohnny.shipping.shipments.internal.adapter.in.web;

import static io.github.stigjohnny.shipping.http.Http.bearer;
import static io.github.stigjohnny.shipping.http.Http.json;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_ALPHA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_BETA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.stigjohnny.shipping.http.ApiTest;
import io.github.stigjohnny.shipping.http.Http;
import io.github.stigjohnny.shipping.http.TestClock;
import io.github.stigjohnny.shipping.shipments.internal.application.InMemoryShipmentStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Every exchange, including setup and failures, goes through Http's contract validator. No database is used. */
@ApiTest
class ShipmentsHttpContractTest {
    private static final String ROOT = "/api/v1/shipments";
    private static final String UNKNOWN = "ffffffff-ffff-4fff-8fff-ffffffffffff";
    private static final String KEY = "demo-booking-key-0001";
    private final MockMvc http;
    private final TestClock clock;
    private final InMemoryShipmentStore store;

    ShipmentsHttpContractTest(@Autowired WebApplicationContext context, @Autowired TestClock clock,
            @Autowired InMemoryShipmentStore store) {
        this.http = Http.mockMvc(context);
        this.clock = clock;
        this.store = store;
    }

    @BeforeEach
    void reset() {
        clock.set(TestClock.START);
        store.clear();
    }

    @Test
    void bookingReadingAndReplayAfterExpiryReturnTheSameShipment() throws Exception {
        String body = booking(CLERK_ALPHA);
        MvcResult booked = Http.record("book-shipment", "clerk-alpha", book(CLERK_ALPHA, KEY, body));
        assertThat(booked.getResponse().getStatus()).isEqualTo(201);
        assertThat(booked.getResponse().getHeader("Idempotency-Replayed")).isNull();
        String location = java.util.Objects.requireNonNull(booked.getResponse().getHeader("Location"));
        assertThat(location).isEqualTo(ROOT + "/" + json(booked).path("shipmentId").stringValue());
        assertThat(json(booked).path("price")).isEqualTo(json("{\"amount\":\"18.81\",\"currency\":\"EUR\"}"));
        assertThat(json(booked).path("recipient")).isEqualTo(json(body).path("recipient"));
        assertThat(json(booked).path("reference").stringValue()).isEqualTo("ORDER-1001");
        assertThat(json(booked).path("bookedAt").stringValue()).isEqualTo("2026-11-10T09:00:00.000Z");
        MvcResult read = Http.record("read-shipment", "clerk-alpha", read(CLERK_ALPHA, location));
        assertThat(json(read)).isEqualTo(json(booked));
        clock.set(TestClock.START.plusSeconds(1800));
        MvcResult replay = Http.record("replay-shipment", "clerk-alpha", book(CLERK_ALPHA, KEY, body));
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getHeader("Location")).isEqualTo(location);
        assertThat(replay.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(json(replay)).isEqualTo(json(booked));
        problem(book(CLERK_ALPHA, KEY, body.replace("ORDER-1001", "ORDER-1002")), 422, "idempotency-key-reused");
        assertThat(store.all()).hasSize(1);
    }

    @Test
    void businessRejectionsFollowTheBookingRulesAndARejectedKeyCanBeRetried() throws Exception {
        String body = booking(CLERK_ALPHA);
        problem(book(CLERK_BETA, KEY, body), 404, "quote-not-found");
        ObjectNode missing = (ObjectNode) json(body);
        missing.put("quoteId", UNKNOWN);
        problem(book(CLERK_ALPHA, KEY, missing.toString()), 404, "quote-not-found");
        missing = (ObjectNode) json(body);
        missing.put("offerId", UNKNOWN);
        problem(book(CLERK_ALPHA, KEY, missing.toString()), 404, "offer-not-found");
        problem(book(CLERK_ALPHA, KEY, body.replace("11122", "11123")), 422, "recipient-does-not-match-quote");
        assertThat(store.all()).isEmpty();
        assertThat(book(CLERK_ALPHA, KEY, body).getResponse().getStatus()).isEqualTo(201);
        problem(book(CLERK_ALPHA, KEY + "2", body), 409, "quote-already-booked");
        String fresh = booking(CLERK_ALPHA);
        clock.set(TestClock.START.plusSeconds(1800));
        problem(book(CLERK_ALPHA, KEY + "3", fresh), 422, "quote-expired");
        // The same key is independent in another tenant.
        assertThat(book(CLERK_BETA, KEY, booking(CLERK_BETA)).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void anotherTenantsDetailAndLabelAreExactlyAsUnknownAndHintsCannotChangeTenant() throws Exception {
        MvcResult booked = book(CLERK_ALPHA, KEY, booking(CLERK_ALPHA));
        String location = java.util.Objects.requireNonNull(booked.getResponse().getHeader("Location"));
        for (String suffix : List.of("", "/label")) {
            MvcResult foreign = Http.record(suffix.isEmpty() ? "foreign-shipment" : "foreign-label", "clerk-beta",
                    read(CLERK_BETA, location + suffix));
            MvcResult unknown = Http.record(suffix.isEmpty() ? "unknown-shipment" : "unknown-label", "clerk-beta",
                    read(CLERK_BETA, ROOT + "/" + UNKNOWN + suffix));
            problem(foreign, 404, "shipment-not-found");
            problem(unknown, 404, "shipment-not-found");
            assertThat(headers(foreign)).isEqualTo(headers(unknown));
            assertThat(withoutEcho(json(foreign))).isEqualTo(withoutEcho(json(unknown)));
        }
        MvcResult beta = http.perform(bearer(get(ROOT).param("tenantId", "11111111-1111-4111-8111-111111111111")
                .header("X-Tenant-Id", "11111111-1111-4111-8111-111111111111"), CLERK_BETA)).andReturn();
        assertThat(json(beta)).isEqualTo(json("{\"items\":[]}"));
    }

    @Test
    void pagesAreNewestFirstIncludingTiesWithNoGapsOrRepeatsAndCursorIsBoundToTenantAndFilter() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            clock.set(TestClock.START.plusSeconds(i / 2));
            MvcResult booked = book(CLERK_ALPHA, KEY + i, booking(CLERK_ALPHA));
            assertThat(booked.getResponse().getStatus()).isEqualTo(201);
            ids.add(json(booked).path("shipmentId").stringValue());
        }
        MvcResult first = Http.record("list-shipments", "clerk-alpha", read(CLERK_ALPHA, ROOT));
        assertThat(json(first).path("items").size()).isEqualTo(20);
        String cursor = json(first).path("nextCursor").stringValue();
        assertThat(cursor).hasSizeLessThanOrEqualTo(200).matches("[A-Za-z0-9_-]+");
        MvcResult last = read(CLERK_ALPHA, ROOT + "?cursor=" + cursor);
        assertThat(json(last).has("nextCursor")).isFalse();
        List<String> seen = new ArrayList<>();
        for (MvcResult page : List.of(first, last)) {
            json(page).path("items").values().forEach(item -> seen.add(item.path("shipmentId").stringValue()));
        }
        assertThat(seen).containsExactlyElementsOf(ids.reversed());
        problem(read(CLERK_BETA, ROOT + "?cursor=" + cursor), 400, "validation-failed");
        problem(read(CLERK_ALPHA, ROOT + "?status=BOOKED&cursor=" + cursor), 400, "validation-failed");
        MvcResult filtered = Http.record("list-shipments-page", "clerk-alpha",
                read(CLERK_ALPHA, ROOT + "?status=BOOKED&limit=1"));
        String filteredCursor = json(filtered).path("nextCursor").stringValue();
        assertThat(json(read(CLERK_ALPHA, ROOT + "?status=BOOKED&limit=100&cursor=" + filteredCursor))
                .path("items").size()).isEqualTo(22);
        problem(read(CLERK_ALPHA, ROOT + "?status=CANCELLED&cursor=" + filteredCursor), 400, "validation-failed");
        assertThat(json(read(CLERK_ALPHA, ROOT + "?status=CANCELLED"))).isEqualTo(json("{\"items\":[]}"));
        assertThat(json(read(CLERK_ALPHA, ROOT + "?limit=100")).has("nextCursor")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit=0", "limit=101", "limit=x", "limit=1.5", "limit=2147483648", "limit=",
            "status=", "status=booked", "status=LOST", "cursor=", "cursor=garbage", "cursor=abc=", "cursor=YQ"})
    void invalidQueriesNameTheQueryParameter(String query) throws Exception {
        MvcResult response = read(CLERK_ALPHA, ROOT + "?" + query);
        invalid(response, "query", query.split("=", -1)[0]);
    }

    @Test
    void oversizedAndStructurallyInvalidCursorsAreRejected() throws Exception {
        for (String raw : List.of("a".repeat(201), Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("11111111-1111-4111-8111-111111111111|not-a-date|" + UNKNOWN + "|")
                        .getBytes(StandardCharsets.UTF_8)))) {
            invalid(read(CLERK_ALPHA, ROOT + "?cursor=" + raw), "query", "cursor");
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "short", "bad key with spaces", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void invalidKeysNameTheHeader(String key) throws Exception {
        invalid(book(CLERK_ALPHA, key, booking(CLERK_ALPHA)), "header", "Idempotency-Key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"quoteId", "offerId", "recipient", "reference"})
    void explicitNullAndUnknownBodyMembersAreRejected(String field) throws Exception {
        ObjectNode body = (ObjectNode) json(booking(CLERK_ALPHA));
        body.putNull(field);
        invalid(book(CLERK_ALPHA, KEY, body.toString()), "body", "/" + field);
        body = (ObjectNode) json(booking(CLERK_ALPHA));
        body.put("tenantId", "11111111-1111-4111-8111-111111111111");
        invalid(book(CLERK_ALPHA, KEY, body.toString()), "body", "/tenantId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "addressLine1", "addressLine2", "city", "postcode", "countryCode"})
    void recipientBoundsTypesAndExplicitNullAreValidated(String field) throws Exception {
        String body = booking(CLERK_ALPHA);
        for (String value : List.of("", "x".repeat(71), "bad\nvalue", "\uD800")) {
            ObjectNode changed = (ObjectNode) json(body);
            ((ObjectNode) changed.path("recipient")).put(field, value);
            invalid(book(CLERK_ALPHA, KEY, changed.toString().replace(String.valueOf((char) 0xd800), "\\uD800")), "body", "/recipient/" + field);
        }
        ObjectNode changed = (ObjectNode) json(body);
        ((ObjectNode) changed.path("recipient")).putNull(field);
        invalid(book(CLERK_ALPHA, KEY, changed.toString().replace(String.valueOf((char) 0xd800), "\\uD800")), "body", "/recipient/" + field);
        ((ObjectNode) changed.path("recipient")).put(field, 12);
        invalid(book(CLERK_ALPHA, KEY, changed.toString().replace(String.valueOf((char) 0xd800), "\\uD800")), "body", "/recipient/" + field);
    }

    @Test
    void unicodeBoundsCountCodePointsAndOptionalFieldsAreOmitted() throws Exception {
        ObjectNode body = (ObjectNode) json(booking(CLERK_ALPHA));
        body.remove("reference");
        ObjectNode recipient = (ObjectNode) body.path("recipient");
        recipient.put("name", "😀".repeat(70));
        recipient.put("city", "😀".repeat(50));
        recipient.put("addressLine1", "😀".repeat(70));
        MvcResult response = book(CLERK_ALPHA, KEY, body.toString());
        assertThat(response.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(response).has("reference")).isFalse();
        assertThat(json(response).path("recipient").has("addressLine2")).isFalse();
        recipient.put("city", "😀".repeat(51));
        invalid(book(CLERK_ALPHA, KEY, body.toString()), "body", "/recipient/city");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-id", "FFFFFFFF-FFFF-4FFF-8FFF-FFFFFFFFFFFF", "1-1-1-1-1"})
    void invalidIdentifiersAreNamed(String id) throws Exception {
        for (String suffix : List.of("", "/label")) {
            invalid(read(CLERK_ALPHA, ROOT + "/" + id + suffix), "path", "shipmentId");
        }
        ObjectNode body = (ObjectNode) json(booking(CLERK_ALPHA));
        body.put("quoteId", id);
        invalid(book(CLERK_ALPHA, KEY, body.toString()), "body", "/quoteId");
    }

    @Test
    void labelsAreDeterministicUtf8EscapedAttachments() throws Exception {
        ObjectNode body = (ObjectNode) json(booking(CLERK_ALPHA));
        ((ObjectNode) body.path("recipient")).put("name", "Synthetic <>&\"' 😀").put("addressLine2", "Unit 2");
        MvcResult booked = book(CLERK_ALPHA, KEY, body.toString());
        String path = booked.getResponse().getHeader("Location") + "/label";
        MvcResult label = Http.record("shipment-label", "clerk-alpha", read(CLERK_ALPHA, path));
        assertThat(label.getResponse().getStatus()).isEqualTo(200);
        assertThat(label.getResponse().getContentAsByteArray()).isEqualTo(read(CLERK_ALPHA, path)
                .getResponse().getContentAsByteArray()).hasSizeLessThanOrEqualTo(65536);
        String svg = label.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(svg).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .contains("SIMULATED LABEL - NOT VALID FOR CARRIAGE", "Synthetic &lt;&gt;&amp;&quot;&apos; 😀", "Unit 2")
                .doesNotContain("<script", "\r");
        problem(http.perform(bearer(get(path), CLERK_ALPHA).accept(MediaType.APPLICATION_JSON)).andReturn(),
                406, "not-acceptable");
    }

    @Test
    void authenticationMediaErrorsAndMalformedBodiesUseTheHttpEdge() throws Exception {
        String body = booking(CLERK_ALPHA);
        problem(http.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn(),
                401, "unauthenticated");
        problem(book("demo-no-tenant-user", KEY, body), 403, "no-tenant-membership");
        problem(http.perform(bearer(post(ROOT), CLERK_ALPHA).contentType(MediaType.TEXT_PLAIN)
                .content(body)).andReturn(), 415, "unsupported-media-type");
        problem(http.perform(bearer(post(ROOT), CLERK_ALPHA).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_HTML).content(body)).andReturn(), 406, "not-acceptable");
        problem(book(CLERK_ALPHA, KEY, "{"), 400, "malformed-request");
        assertThat(store.all()).isEmpty();
    }

    private String booking(String token) throws Exception {
        MvcResult response = http.perform(bearer(post("/api/v1/quotes"), token).contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"parcel":{"weightGrams":1200,"lengthMm":300,"widthMm":200,"heightMm":100},
                 "origin":{"countryCode":"NO","postcode":"0150"},
                 "destination":{"countryCode":"SE","postcode":"11122"}}
                """)).andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(201);
        JsonNode quote = json(response);
        return """
                {"quoteId":"%s","offerId":"%s","recipient":{"name":"Synthetic Recipient 1",
                "addressLine1":"1 Example Street","postcode":"11122","city":"Exampleby","countryCode":"SE"},
                "reference":"ORDER-1001"}
                """.formatted(quote.path("quoteId").stringValue(), quote.path("offers").path(1).path("offerId").stringValue());
    }

    private MvcResult book(String token, String key, String body) throws Exception {
        MockHttpServletRequestBuilder request = bearer(post(ROOT), token).contentType(MediaType.APPLICATION_JSON)
                .header("Correlation-Id", "demo-88-shipments").content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return http.perform(request).andReturn();
    }

    private MvcResult read(String token, String path) throws Exception {
        return http.perform(bearer(get(path), token).header("Correlation-Id", "demo-88-shipments")).andReturn();
    }

    private static void problem(MvcResult result, int status, String type) {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(json(result).path("type").stringValue()).endsWith("/" + type);
    }

    private static void invalid(MvcResult result, String in, String name) {
        problem(result, 400, "validation-failed");
        assertThat(json(result).path("errors").path(0).path("in").stringValue()).isEqualTo(in);
        assertThat(json(result).path("errors").path(0).path("name").stringValue()).isEqualTo(name);
    }

    private static JsonNode withoutEcho(JsonNode node) {
        ObjectNode copy = (ObjectNode) node.deepCopy();
        copy.remove("instance");
        copy.remove("correlationId");
        return copy;
    }

    private static Map<String, List<String>> headers(MvcResult result) {
        Map<String, List<String>> headers = new TreeMap<>();
        result.getResponse().getHeaderNames().forEach(name -> headers.put(name, result.getResponse().getHeaders(name)));
        return headers;
    }
}
