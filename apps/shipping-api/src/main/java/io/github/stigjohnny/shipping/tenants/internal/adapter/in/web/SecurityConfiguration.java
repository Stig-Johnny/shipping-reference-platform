package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import io.github.stigjohnny.shipping.tenants.internal.application.MembershipDirectory;
import io.github.stigjohnny.shipping.tenants.internal.application.ResolveMembership;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security for the API and the tenants module's beans (docs/modules/tenants.md, "Request and transaction
 * flow"). One stateless chain requires a tenant member on every {@code /api/**} path and denies every other path. The
 * demo bearer token filter is part of it only in the {@code demo} profile: without it nobody authenticates, and every
 * API request is 401, as the contract's demoBearer scheme says. The 401 and 403 problems are written through the MVC
 * exception resolvers by {@link AuthenticationProblems}, like every other problem, and so are the firewall's
 * rejections, before routing, which the HTTP edge maps to {@code 400 malformed-request}. The module's own
 * authentication manager means that Spring Boot never creates its generated user and password
 * (security/reviews/maven--spring-security--7.1.2.md, condition 3).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    /** The profile in which demo bearer tokens authenticate. */
    static final String DEMO_PROFILE = "demo";
    /** The authority of a user with a tenant membership: only such a user may call the API. */
    static final String TENANT_MEMBER = "TENANT_MEMBER";

    @Bean
    ThreadTenantContext threadTenantContext() {
        return new ThreadTenantContext();
    }

    @Bean
    ResolveMembership resolveMembership(MembershipDirectory directory) {
        return new ResolveMembership(directory);
    }

    @Bean
    AuthenticationManager authenticationManager(ResolveMembership resolveMembership) {
        return new ProviderManager(new DemoTokenAuthenticationProvider(resolveMembership));
    }

    /**
     * What Spring Security's firewall refuses (a path with {@code ;} or an encoded {@code /}, a method it does not
     * accept) becomes a problem, not the firewall's empty 400; WebSecurity takes this bean for its filter chain proxy.
     */
    @Bean
    RequestRejectedHandler requestRejectedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver problems) {
        return (request, response, rejected) -> problem(problems, request, response, rejected);
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, Environment environment, AuthenticationManager authentication,
            ThreadTenantContext tenantContext,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver problems) throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, exception) -> problem(problems, request,
                response, exception);
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/api/**").hasAuthority(TENANT_MEMBER)
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Off with a reason: the API takes bearer tokens only and sets no cookie, so a cross-site request
                // carries no credential (docs/modules/tenants.md; the Spring Security review's condition 5).
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler((request, response, exception) -> problem(problems, request, response,
                                exception)));
        if (environment.matchesProfiles(DEMO_PROFILE)) {
            http.addFilterBefore(new DemoTokenAuthenticationFilter(authentication, tenantContext, unauthenticated),
                    AuthorizationFilter.class);
            DemoStaticConfiguration.securityHeaders(http);
        }
        return http.build();
    }

    private static void problem(HandlerExceptionResolver problems, HttpServletRequest request,
            HttpServletResponse response, Exception exception) {
        if (problems.resolveException(request, response, null, exception) == null) {
            throw new IllegalStateException("No problem is mapped for " + exception.getClass().getName(), exception);
        }
    }
}
