package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import static io.github.stigjohnny.shipping.http.Http.bearer;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_ALPHA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.NO_TENANT_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.stigjohnny.shipping.http.ApiTest;
import io.github.stigjohnny.shipping.http.Http;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/** The demo's CSP covers successful requests and authentication failures without replacing security headers. */
@ApiTest
class DemoSecurityHeadersTest {

    private final MockMvc http;

    DemoSecurityHeadersTest(@Autowired WebApplicationContext context) {
        http = Http.mockMvc(context);
    }

    @Test
    void aSignedInSyntheticClerkReceivesTheStrictPolicy() throws Exception {
        MockHttpServletResponse response = http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertSecurityHeaders(response);
    }

    @Test
    void anUnauthenticatedRequestReceivesTheStrictPolicy() throws Exception {
        MockHttpServletResponse response = http.perform(get("/api/v1/me")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertSecurityHeaders(response);
    }

    @Test
    void aSyntheticUserWithoutMembershipReceivesTheStrictPolicy() throws Exception {
        MockHttpServletResponse response = http.perform(bearer(get("/api/v1/me"), NO_TENANT_USER))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertSecurityHeaders(response);
    }

    private static void assertSecurityHeaders(MockHttpServletResponse response) {
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(
                "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Correlation-Id")).isNotBlank();
    }
}
