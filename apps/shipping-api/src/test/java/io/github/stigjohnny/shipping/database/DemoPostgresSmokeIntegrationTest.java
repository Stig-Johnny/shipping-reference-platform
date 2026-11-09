package io.github.stigjohnny.shipping.database;

import io.github.stigjohnny.shipping.ShippingApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import io.github.stigjohnny.shipping.http.ApiTest;
import io.github.stigjohnny.shipping.http.DemoHttpSmoke;
import java.sql.Connection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The same socket smoke, with live JDBC proxies and runtime-role connections; CI only. */
@Tag("database")
@SpringBootTest(classes = ShippingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"demo", "postgres"})
@TypeExcludeFilters(ApiTest.ArchitectureFixtures.class)
class DemoPostgresSmokeIntegrationTest extends DemoHttpSmoke {
    @Autowired
    DemoPostgresSmokeIntegrationTest(@Value("${local.server.port}") int port) { super(port); }

    private static final class Fixture {
        static final DatabaseServer SERVER = DatabaseServer.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        DatabaseServer server = Fixture.SERVER;
        ShippingDatabase database = server.newDatabase("demo_http").migrate();
        try (Connection owner = database.owner(); var statement = owner.createStatement()) {
            statement.execute(Scripts.resource("db/demo/synthetic-tenants.sql"));
        }
        properties.add("spring.datasource.url", () -> server.jdbcUrl("demo_http"));
        properties.add("spring.datasource.username", () -> server.app().user());
        properties.add("spring.datasource.password", () -> server.app().password());
    }

    @AfterAll static void stop() { Fixture.SERVER.close(); }
}
