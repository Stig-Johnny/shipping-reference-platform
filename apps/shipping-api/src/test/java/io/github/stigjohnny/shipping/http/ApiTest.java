package io.github.stigjohnny.shipping.http;

import io.github.stigjohnny.shipping.ShippingApplication;
import io.github.stigjohnny.shipping.quotes.internal.application.QuotesTestDoubles;
import io.github.stigjohnny.shipping.tenants.internal.application.TenantsTestDoubles;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole application, started in a test with a mock servlet environment: ShippingApplication with every module,
 * the HTTP edge and Spring Security, in the {@code demo} profile unless a test names other profiles. What has no
 * production adapter yet is a test double: the membership directory, the quote store, deterministic quote IDs and a
 * test clock. PostgreSQL is not started, so the data source is not configured; the next pull request brings it with
 * the JDBC adapters. The architecture tests' fixtures are excluded from component scanning.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = {ShippingApplication.class, TenantsTestDoubles.class, QuotesTestDoubles.class,
        TestClock.Configuration.class},
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.modulith.events.jdbc.JdbcEventPublicationAutoConfiguration,"
                + "org.springframework.modulith.events.config.EventPublicationAutoConfiguration")
@ActiveProfiles
@TypeExcludeFilters(ApiTest.ArchitectureFixtures.class)
public @interface ApiTest {

    /** The active profiles: {@code demo}, in which demo bearer tokens authenticate, unless a test names others. */
    @AliasFor(annotation = ActiveProfiles.class, attribute = "profiles")
    String[] profiles() default "demo";

    /** Keeps the architecture tests' fixtures, one of which carries a Spring stereotype, out of the application. */
    final class ArchitectureFixtures extends TypeExcludeFilter {

        private static final String FIXTURES = "io.github.stigjohnny.shipping.architecture.fixtures.";

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
            return reader.getClassMetadata().getClassName().startsWith(FIXTURES);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ArchitectureFixtures;
        }

        @Override
        public int hashCode() {
            return ArchitectureFixtures.class.hashCode();
        }
    }
}
