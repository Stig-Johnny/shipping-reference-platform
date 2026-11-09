package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import io.github.stigjohnny.shipping.tenants.internal.application.MembershipDirectory;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Select synthetic memory identity only when no directory is supplied and postgres is inactive. */
@Configuration(proxyBeanMethods = false)
@Profile("demo & !postgres")
class DemoTenantsConfiguration {
    @Bean
    @Fallback
    MembershipDirectory demoMembershipDirectory() { return new DemoMembershipDirectory(); }
}
