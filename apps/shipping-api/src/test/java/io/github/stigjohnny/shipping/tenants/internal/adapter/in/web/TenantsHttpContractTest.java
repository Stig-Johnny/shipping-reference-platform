package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import static io.github.stigjohnny.shipping.http.Http.bearer;
import static io.github.stigjohnny.shipping.http.Http.json;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_ALPHA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.CLERK_BETA;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.NO_TENANT_USER;
import static io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.stigjohnny.shipping.http.ApiTest;
import io.github.stigjohnny.shipping.http.Contract;
import io.github.stigjohnny.shipping.http.Http;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

/**
 * GET /api/v1/me (getCurrentMembership) against the contract (invariant T9): 200 with the caller's membership, 401 and
 * 403, and 406. The membership directory is the test double with the synthetic fixtures of docs/modules/tenants.md,
 * so this test proves the HTTP edge, not the database. Prototype flags are not built yet: every flag is off, so
 * enabledFlags is empty.
 */
@ApiTest
class TenantsHttpContractTest {

    private final Contract contract = Contract.contract();
    private final MockMvc http;

    TenantsHttpContractTest(@Autowired WebApplicationContext context) {
        http = Http.mockMvc(context);
    }

    @Test
    void clerkAlphaReadsTheirMembershipOfTenantAlpha() throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA)).andReturn();

        JsonNode membership = contract.assertConforms("get", "/me", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(membership).isEqualTo(json("""
                {"userId": "00000000-0000-4000-8000-0000000000a1", "userDisplayName": "Clerk Alpha (synthetic)",
                 "tenantId": "11111111-1111-4111-8111-111111111111", "tenantDisplayName": "Tenant Alpha (synthetic)",
                 "role": "CLERK", "enabledFlags": []}"""));
    }

    @Test
    void clerkBetaReadsTheirMembershipOfTenantBeta() throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/me"), CLERK_BETA)).andReturn();

        JsonNode membership = contract.assertConforms("get", "/me", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(membership).isEqualTo(json("""
                {"userId": "00000000-0000-4000-8000-0000000000b1", "userDisplayName": "Clerk Beta (synthetic)",
                 "tenantId": "22222222-2222-4222-8222-222222222222", "tenantDisplayName": "Tenant Beta (synthetic)",
                 "role": "CLERK", "enabledFlags": []}"""));
    }

    @Test
    void withoutATokenTheAnswerIs401Unauthenticated() throws Exception {
        MvcResult result = http.perform(get("/api/v1/me")).andReturn();

        assertUnauthenticated(result);
    }

    @Test
    void anUnknownTokenIs401Unauthenticated() throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/me"), UNKNOWN)).andReturn();

        assertUnauthenticated(result);
    }

    @Test
    void aUserWithoutATenantIs403NoTenantMembership() throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/me"), NO_TENANT_USER)).andReturn();

        JsonNode problem = contract.assertConforms("get", "/me", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(problem.path("type").stringValue()).endsWith("/no-tenant-membership");
        assertThat(problem.path("detail").stringValue()).isEqualTo("The authenticated user belongs to no tenant.");
    }

    @Test
    void anAcceptHeaderWithoutJsonIs406NotAcceptable() throws Exception {
        MvcResult result = http.perform(bearer(get("/api/v1/me"), CLERK_ALPHA).accept(MediaType.APPLICATION_XML))
                .andReturn();

        JsonNode problem = contract.assertConforms("get", "/me", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(406);
        assertThat(problem.path("type").stringValue()).endsWith("/not-acceptable");
    }

    private void assertUnauthenticated(MvcResult result) {
        JsonNode problem = contract.assertConforms("get", "/me", result);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer realm=\"shipping-reference-platform\"");
        assertThat(problem.path("type").stringValue()).endsWith("/unauthenticated");
        assertThat(problem.path("detail").stringValue()).isEqualTo("A valid demo bearer token is required.");
    }
}
