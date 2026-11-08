package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serial;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request that carries {@code Authorization: Bearer <demo token>}, and binds the member's tenant for
 * the rest of the request through {@link ThreadTenantContext}: every module's {@code CurrentTenant} is then the
 * membership's tenant, never a value from the request. A request without the header goes on unauthenticated, and the
 * authorization rules answer it with 401; another scheme and a malformed or unknown token are 401 at once. The
 * binding ends with the request, when it returns and when it throws.
 */
final class DemoTokenAuthenticationFilter extends OncePerRequestFilter {

    /** The scheme and the space after it; RFC 9110 makes the scheme's name case-insensitive. */
    private static final String SCHEME = "Bearer ";

    private final AuthenticationManager authentication;
    private final ThreadTenantContext tenantContext;
    private final AuthenticationEntryPoint unauthenticated;

    DemoTokenAuthenticationFilter(AuthenticationManager authentication, ThreadTenantContext tenantContext,
            AuthenticationEntryPoint unauthenticated) {
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.unauthenticated = Objects.requireNonNull(unauthenticated, "unauthenticated");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        Authentication authenticated;
        try {
            authenticated = authentication.authenticate(DemoTokenAuthentication.presented(token(header)));
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            unauthenticated.commence(request, response, e);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        if (authenticated.getPrincipal() instanceof TenantPrincipal member) {
            continueAs(member.membership().tenantId(), request, response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    /** The text after the scheme, or none, which is no token, for another scheme. */
    private static String token(String header) {
        return header.regionMatches(true, 0, SCHEME, 0, SCHEME.length()) ? header.substring(SCHEME.length()) : "";
    }

    /** Runs the rest of the chain with the tenant bound; the binding takes unchecked work only. */
    private void continueAs(TenantId tenant, HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            tenantContext.runAs(tenant, () -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new CheckedFailure(e);
                }
            });
        } catch (CheckedFailure failure) {
            failure.rethrow();
        }
    }

    /** Carries the chain's checked exception out of the tenant binding unchanged. */
    private static final class CheckedFailure extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        CheckedFailure(Exception cause) {
            super(cause);
        }

        void rethrow() throws ServletException, IOException {
            if (getCause() instanceof IOException io) {
                throw io;
            }
            if (getCause() instanceof ServletException servlet) {
                throw servlet;
            }
            throw new IllegalStateException("Only IOException and ServletException are carried.", getCause());
        }
    }
}
