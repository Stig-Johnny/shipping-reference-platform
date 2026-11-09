package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Public static GET/HEAD requests have a separate chain; the existing API chain is unchanged. */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
@ConditionalOnProperty("shipping.demo.static-directory")
class DemoStaticConfiguration {
    private static final String CSP = "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'";

    static void securityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CSP)));
    }

    @Bean
    @Order(0)
    SecurityFilterChain demoStaticSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher(request -> !request.getServletPath().equals("/api")
                && !request.getServletPath().startsWith("/api/"));
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/**").permitAll());
        securityHeaders(http);
        return http.build();
    }
}
