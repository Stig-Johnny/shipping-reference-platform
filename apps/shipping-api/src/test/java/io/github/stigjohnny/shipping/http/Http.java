package io.github.stigjohnny.shipping.http;

import jakarta.servlet.Filter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** MockMvc for the whole application, and the requests the HTTP tests send. */
public final class Http {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Http() {
    }

    /**
     * MockMvc with the servlet filters the application registers, in the order the servlet container would run them:
     * the same {@code ServletContextInitializerBeans} that Spring Boot hands to the embedded server decides which
     * filters there are and their order, so the correlation filter, Spring Security's chain and Boot's own filters run
     * as they do in production. Every response it gives is checked against the contract
     * ({@link Contract#assertExercised}) before the test sees it.
     */
    public static MockMvc mockMvc(WebApplicationContext context) {
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(context)
                .alwaysDo(Contract.contract()::assertExercised);
        for (ServletContextInitializer initializer : new ServletContextInitializerBeans(context)) {
            if (initializer instanceof AbstractFilterRegistrationBean<?> registration && registration.isEnabled()) {
                Filter filter = registration.getFilter();
                Collection<String> patterns = registration.getUrlPatterns();
                builder.addFilter(filter, patterns.toArray(String[]::new));
            }
        }
        return builder.build();
    }

    /** Adds {@code Authorization: Bearer <token>}. */
    public static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /**
     * Writes the exchange, the request and the response, as text to
     * {@code apps/shipping-api/build/reports/http-exchanges/<name>.txt}, so that a demo can quote what the API
     * answered. Headers are written in the order given, JSON bodies indented. A token is never written: the
     * Authorization header is replaced by the words "demo token of" and the synthetic user it belongs to.
     */
    public static MvcResult record(String name, String user, MvcResult result) {
        try {
            Path file = Path.of("build/reports/http-exchanges").resolve(name + ".txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, exchange(user, result.getRequest(), result.getResponse()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private static String exchange(String user, MockHttpServletRequest request, MockHttpServletResponse response)
            throws IOException {
        StringBuilder text = new StringBuilder();
        text.append(request.getMethod()).append(' ').append(request.getRequestURI()).append('\n');
        for (String header : List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, "Correlation-Id")) {
            String value = request.getHeader(header);
            if (value != null) {
                text.append(header).append(": ").append(value).append('\n');
            }
        }
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            text.append(HttpHeaders.AUTHORIZATION).append(": demo token of ").append(user).append('\n');
        }
        byte[] requestBody = request.getContentAsByteArray();
        if (requestBody != null && requestBody.length > 0) {
            text.append('\n').append(indented(new String(requestBody, StandardCharsets.UTF_8))).append('\n');
        }
        text.append("\nHTTP ").append(response.getStatus()).append('\n');
        for (String header : new TreeSet<>(response.getHeaderNames())) {
            text.append(header).append(": ").append(String.join(", ", response.getHeaders(header))).append('\n');
        }
        text.append('\n').append(indented(response.getContentAsString(StandardCharsets.UTF_8))).append('\n');
        return text.toString();
    }

    private static String indented(String json) {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(json));
    }

    /** The JSON tree of a text, for comparing a whole body. */
    public static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    /** The JSON tree of a response's body. */
    public static JsonNode json(MvcResult result) {
        try {
            return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
