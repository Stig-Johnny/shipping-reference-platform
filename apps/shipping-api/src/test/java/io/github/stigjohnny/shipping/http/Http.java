package io.github.stigjohnny.shipping.http;

import jakarta.servlet.Filter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.http.HttpHeaders;
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
     * as they do in production.
     */
    public static MockMvc mockMvc(WebApplicationContext context) {
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(context);
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
