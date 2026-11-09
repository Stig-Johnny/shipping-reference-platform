package io.github.stigjohnny.shipping.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shared real-HTTP assertions; the database subclass runs only in the reviewed CI window. */
@org.springframework.context.annotation.Import({DemoHttpSmoke.Identifiers.class, TestClock.Configuration.class})
public abstract class DemoHttpSmoke {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    public static class Identifiers {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator smokeQuoteIds() {
            return io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.sequentialIds();
        }
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        io.github.stigjohnny.shipping.shipments.internal.domain.IdGenerator smokeShipmentIds() {
            return io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.sequentialIds();
        }
    }
    private final int port;

    protected DemoHttpSmoke(int port) { this.port = port; }
    private final JsonMapper json = JsonMapper.builder().build();

    @DynamicPropertySource
    static void staticDirectory(DynamicPropertyRegistry properties) throws Exception {
        Path parent = Files.createTempDirectory("shipping-demo-smoke-");
        Path root = Files.createDirectory(parent.resolve("public"));
        Files.writeString(root.resolve("index.html"), "<!doctype html><title>Synthetic workbench fixture</title>");
        Files.writeString(root.resolve("app.js"), "document.title = 'Synthetic workbench fixture';");
        Files.writeString(root.resolve("app.css"), "body { color: black; }");
        Path outside = Files.writeString(parent.resolve("outside.txt"), "OUTSIDE-DIRECTORY-MARKER");
        Files.createSymbolicLink(root.resolve("escape.txt"), outside);
        Files.createSymbolicLink(root.resolve("escape-dir"), parent);
        properties.add("shipping.demo.static-directory", root::toString);
    }

    @Test
    void syntheticClerkQuotesBooksListsReadsAndDownloadsLabel() throws Exception {
        assertThat(send("GET", "/api/v1/me", "", "").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/me", "demo-no-tenant-user", "").statusCode()).isEqualTo(403);
        assertThat(node(send("GET", "/api/v1/me", "demo-clerk-alpha", ""), 200)
                .path("tenantId").stringValue()).isEqualTo("11111111-1111-4111-8111-111111111111");
        JsonNode quote = node(send("POST", "/api/v1/quotes", "demo-clerk-alpha", """
                {"origin":{"countryCode":"NO","postcode":"0150"},
                 "destination":{"countryCode":"SE","postcode":"11122"},
                 "parcel":{"weightGrams":1200,"lengthMm":300,"widthMm":200,"heightMm":100}}
                """), 201);
        String body = """
                {"quoteId":"%s","offerId":"%s","recipient":{"name":"Synthetic Recipient 1",
                 "addressLine1":"1 Example Street","postcode":"11122","city":"Exampleby","countryCode":"SE"},
                 "reference":"ORDER-1001"}
                """.formatted(quote.path("quoteId").stringValue(), quote.path("offers").get(0).path("offerId").stringValue());
        HttpResponse<String> booked = send("POST", "/api/v1/shipments", "demo-clerk-alpha", body);
        JsonNode shipment = node(booked, 201);
        String location = booked.headers().firstValue("Location").orElseThrow();
        assertThat(node(send("POST", "/api/v1/shipments", "demo-clerk-alpha", body), 200)).isEqualTo(shipment);
        assertThat(node(send("GET", location, "demo-clerk-alpha", ""), 200)).isEqualTo(shipment);
        assertThat(node(send("GET", "/api/v1/shipments", "demo-clerk-alpha", ""), 200)
                .path("items").get(0).path("shipmentId")).isEqualTo(shipment.path("shipmentId"));
        assertThat(send("GET", location, "demo-clerk-beta", "").statusCode()).isEqualTo(404);
        assertThat(send("GET", location + "/label", "demo-clerk-beta", "").statusCode()).isEqualTo(404);
        HttpResponse<String> label = send("GET", location + "/label", "demo-clerk-alpha", "");
        assertThat(label.statusCode()).isEqualTo(200);
        assertThat(label.headers().firstValue("Content-Type").orElseThrow()).startsWith("image/svg+xml");
        assertThat(label.headers().firstValue("Content-Security-Policy").orElseThrow())
                .isEqualTo("default-src 'none'; sandbox");
        assertThat(label.body()).contains("SIMULATED");
    }

    @Test
    void servesStaticFilesWithStrictHeadersAndCorrectTypes() throws Exception {
        for (String path : List.of("/", "/index.html", "/app.js", "/app.css")) {
            HttpResponse<String> response = send("GET", path, "", "");
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            String type = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
            assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith(type);
            assertThat(response.headers().firstValue("Content-Security-Policy").orElseThrow()).isEqualTo(
                    "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'");
            assertThat(response.headers().firstValue("X-Content-Type-Options").orElseThrow()).isEqualTo("nosniff");
            assertThat(response.headers().firstValue("X-Frame-Options").orElseThrow()).isEqualTo("DENY");
            assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store");
        }
        assertThat(send("GET", "/missing.txt", "", "").statusCode()).isEqualTo(404);
    }

    @Test
    void traversalAndSymlinksCannotReadOutsideTheConfiguredDirectory() throws Exception {
        for (String path : List.of("/../outside.txt", "/%2e%2e/outside.txt", "/%252e%252e/outside.txt",
                "/..%2foutside.txt", "/%2e%2e%5coutside.txt", "/escape.txt", "/escape-dir/outside.txt")) {
            HttpResponse<String> response = send("GET", path, "", "");
            assertThat(response.statusCode()).as(path).isIn(400, 404);
            assertThat(response.body()).doesNotContain("OUTSIDE-DIRECTORY-MARKER");
        }
    }

    private JsonNode node(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        return json.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.ofString(body));
        if (!token.isEmpty()) { request.header("Authorization", "Bearer " + token); }
        if (!body.isEmpty()) { request.header("Content-Type", "application/json"); }
        if (path.equals("/api/v1/shipments") && method.equals("POST")) {
            request.header("Idempotency-Key", "demo-smoke-booking-0001");
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (path.startsWith("/api/")) {
                MockHttpServletResponse captured = new MockHttpServletResponse();
                captured.setStatus(response.statusCode());
                response.headers().map().forEach((name, values) -> values.forEach(value -> captured.addHeader(name, value)));
                captured.getWriter().write(response.body());
                Contract.contract().assertExercised(method, path, captured);
            }
            return response;
        }
    }
}
