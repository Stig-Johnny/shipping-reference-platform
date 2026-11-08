package io.github.stigjohnny.shipping.tenants.internal.application;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The tenants module's ports without production adapters, for the application started in tests: the membership
 * directory holds the synthetic fixtures. The JDBC adapter replaces it with the PostgreSQL-backed demo profile.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TenantsTestDoubles {

    @Bean
    MembershipDirectory membershipDirectory() {
        return new FixtureMembershipDirectory();
    }
}
