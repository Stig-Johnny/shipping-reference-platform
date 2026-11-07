package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import static io.github.stigjohnny.shipping.http.Http.bearer;
import static io.github.stigjohnny.shipping.http.Http.json;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_ALPHA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_BETA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.NO_TENANT_USER;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.TENANT_ALPHA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.TENANT_BETA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.stigjohnny.shipping.http.ApiTest;
import io.github.stigjohnny.shipping.http.Contract;
import io.github.stigjohnny.shipping.http.Http;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.MissingTenantContextException;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

/**
 * Demo bearer tokens authenticate synthetic users, and the tenant comes from the user's membership only (invariant T1):
 * a missing, malformed or unknown token or another scheme is 401, a user without a tenant is 403 on every operation,
 * tenant hints in headers and the query never choose the tenant, and a tenantId member in a body is rejected. The
 * tenant is bound for the request and only for it, also when the request fails. Spring Boot's generated user never
 * exists, and without the demo profile no token authenticates anybody. The membership directory is the test double
 * with the synthetic fixtures.
 */
@ApiTest
class DemoTokenAuthenticationTest {

    private static final String QUOTE_REQUEST = """
            {"parcel": {"weightGrams": 1200, "lengthMm": 300, "widthMm": 200, "heightMm": 100},
             "origin": {"countryCode": "NO", "postcode": "0150"},
             "destination": {"countryCode": "SE", "postcode": "11122"}}""";

    private final Contract contract = Contract.contract();
    private final WebApplicationContext context;
    private final MockMvc http;

    DemoTokenAuthenticationTest(@Autowired WebApplicationContext context) {
        this.context = context;
        http = Http.mockMvc(context);
    }

    static Stream<Arguments> notAuthenticated() {
        return Stream.of(Arguments.of("no Authorization header", null),
                Arguments.of("another scheme", "Basic ZGVtbw=="),
                Arguments.of("a token without a scheme", CLERK_ALPHA),
                Arguments.of("another scheme with a demo token", "Token " + CLERK_ALPHA),
                Arguments.of("the scheme without a token", "Bearer"),
                Arguments.of("an empty token", "Bearer "),
                Arguments.of("a malformed token", "Bearer demo-Clerk-Alpha"),
                Arguments.of("a token with more after it", "Bearer " + CLERK_ALPHA + " " + CLERK_BETA),
                Arguments.of("an unknown token", "Bearer " + UNKNOWN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notAuthenticated")
    void aRequestWithoutAValidDemoTokenIs401OnEveryOperation(String what, String authorization) throws Exception {
        assertUnauthenticated("get", "/me", http.perform(authorized(get("/api/v1/me"), authorization)).andReturn());
        assertUnauthenticated("post", "/quotes", http.perform(authorized(post("/api/v1/quotes"), authorization)
                .contentType(MediaType.APPLICATION_JSON).content(QUOTE_REQUEST)).andReturn());
        assertUnauthenticated("get", "/quotes/{quoteId}", http.perform(authorized(
                get("/api/v1/quotes/5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21"), authorization)).andReturn());
    }

    @Test
    void theSchemeNameIsCaseInsensitive() throws Exception {
        MvcResult result = http.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "bearer " + CLERK_ALPHA))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aUserWithoutATenantIs403OnEveryOperation() throws Exception {
        assertNoTenant("get", "/me", http.perform(bearer(get("/api/v1/me"), NO_TENANT_USER)).andReturn());
        assertNoTenant("post", "/quotes", http.perform(bearer(post("/api/v1/quotes"), NO_TENANT_USER)
                .contentType(MediaType.APPLICATION_JSON).content(QUOTE_REQUEST)).andReturn());
        assertNoTenant("get", "/quotes/{quoteId}", http.perform(bearer(
                get("/api/v1/quotes/5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21"), NO_TENANT_USER)).andReturn());
    }

    @Test
    void tenantHintsInHeadersAndTheQueryNeverChooseTheTenant() throws Exception {
        String beta = TENANT_BETA.value().toString();
        MvcResult result = http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA).header("X-Tenant-Id", beta)
                .header("Tenant-Id", beta).queryParam("tenantId", beta).queryParam("tenant", beta)).andReturn();

        JsonNode membership = contract.assertConforms("get", "/me", result);
        assertThat(membership.path("tenantId").stringValue()).isEqualTo(TENANT_ALPHA.value().toString());
    }

    @Test
    void aQuoteBelongsToTheMembersTenantWhateverTheRequestHints() throws Exception {
        String beta = TENANT_BETA.value().toString();
        MvcResult created = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA).header("X-Tenant-Id", beta)
                .queryParam("tenantId", beta).contentType(MediaType.APPLICATION_JSON).content(QUOTE_REQUEST))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(http.perform(bearer(get(String.valueOf(location)), CLERK_ALPHA)).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
        assertThat(http.perform(bearer(get(String.valueOf(location)), CLERK_BETA)).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
    }

    @Test
    void aTenantIdMemberInABodyIsRejected() throws Exception {
        String withTenant = QUOTE_REQUEST.replaceFirst("\\{", "{\"tenantId\": \"" + TENANT_BETA.value() + "\", ");
        MvcResult result = http.perform(bearer(post("/api/v1/quotes"), CLERK_ALPHA)
                .contentType(MediaType.APPLICATION_JSON).content(withTenant)).andReturn();

        JsonNode problem = contract.assertConforms("post", "/quotes", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(problem.path("type").stringValue()).endsWith("/validation-failed");
        assertThat(problem.path("errors")).isEqualTo(json("""
                [{"in": "body", "name": "/tenantId", "detail": "is not a member of this object"}]"""));
    }

    @Test
    void theTenantIsBoundForTheRequestAndOnlyForIt(@Autowired CurrentTenant currentTenant) throws Exception {
        assertThat(http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA)).andReturn().getResponse().getStatus())
                .isEqualTo(200);

        // MockMvc runs the request on this thread; the binding ended with it.
        assertThatThrownBy(currentTenant::require).isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void aCheckedExceptionFromTheRestOfTheRequestPassesUnchangedAndTheBindingEnds() {
        ThreadTenantContext tenantContext = new ThreadTenantContext();
        DemoTokenAuthenticationFilter filter = new DemoTokenAuthenticationFilter(
                presented -> DemoTokenAuthentication.member(new TenantPrincipal(FixtureMembershipDirectory.ALPHA)),
                tenantContext, (request, response, exception) -> {
                    throw new AssertionError("The member is authenticated.");
                });
        List<TenantId> bound = new ArrayList<>();
        IOException io = new IOException("the client went away");
        ServletException servlet = new ServletException("the servlet failed");
        try {
            assertPassesThrough(filter, io, (in, out) -> {
                bound.add(tenantContext.require());
                throw io;
            });
            assertPassesThrough(filter, servlet, (in, out) -> {
                bound.add(tenantContext.require());
                throw servlet;
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(bound).containsExactly(TENANT_ALPHA, TENANT_ALPHA);
        assertThatThrownBy(tenantContext::require).isInstanceOf(MissingTenantContextException.class);
    }

    private static void assertPassesThrough(DemoTokenAuthenticationFilter filter, Exception failure, FilterChain chain) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + CLERK_ALPHA);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain)).isSameAs(failure);
    }

    @Test
    void springBootsGeneratedUserDoesNotExist() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(context.getBeansOfType(AuthenticationManager.class)).hasSize(1);
    }

    private void assertUnauthenticated(String method, String path, MvcResult result) {
        JsonNode problem = contract.assertConforms(method, path, result);
        assertThat(result.getResponse().getStatus()).as("%s %s", method, path).isEqualTo(401);
        assertThat(problem.path("type").stringValue()).endsWith("/unauthenticated");
    }

    private void assertNoTenant(String method, String path, MvcResult result) {
        JsonNode problem = contract.assertConforms(method, path, result);
        assertThat(result.getResponse().getStatus()).as("%s %s", method, path).isEqualTo(403);
        assertThat(problem.path("type").stringValue()).endsWith("/no-tenant-membership");
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request,
            String authorization) {
        return authorization == null ? request : request.header(HttpHeaders.AUTHORIZATION, authorization);
    }

    /** The same application without the demo profile: the contract says no token authenticates anybody then. */
    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @ApiTest(profiles = {})
    @ExtendWith(OutputCaptureExtension.class)
    class WithoutTheDemoProfile {

        private final MockMvc withoutDemo;

        WithoutTheDemoProfile(@Autowired WebApplicationContext context) {
            withoutDemo = Http.mockMvc(context);
        }

        @Test
        void evenAValidDemoTokenIs401AndNoPasswordIsGenerated(CapturedOutput output) throws Exception {
            MvcResult result = withoutDemo.perform(bearer(get("/api/v1/me"), CLERK_ALPHA)).andReturn();

            assertUnauthenticated("get", "/me", result);
            // This context starts in this class, under the capture: Spring Boot's generated user would log this line.
            assertThat(output.getAll()).contains("No active profile set", "Started")
                    .doesNotContain("Using generated security password");
        }
    }

    /**
     * The demo profile's application, started afresh in this class under the capture, so that its own start is what
     * the capture sees: a demo token authenticates, and Spring Boot generates no user and logs no password.
     */
    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @ApiTest
    @DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
    @ExtendWith(OutputCaptureExtension.class)
    class WithTheDemoProfile {

        private final MockMvc withDemo;

        WithTheDemoProfile(@Autowired WebApplicationContext context) {
            withDemo = Http.mockMvc(context);
        }

        @Test
        void aDemoTokenAuthenticatesAndNoPasswordIsGenerated(CapturedOutput output) throws Exception {
            MvcResult result = withDemo.perform(bearer(get("/api/v1/me"), CLERK_ALPHA)).andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(output.getAll()).contains("The following 1 profile is active: \"demo\"", "Started")
                    .doesNotContain("Using generated security password");
        }
    }
}
