package io.github.stigjohnny.shipping.http;

import io.github.stigjohnny.shipping.ShippingApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.test.context.ActiveProfiles;

/** Real sockets and production demo stores, without test doubles. */
@SpringBootTest(classes = ShippingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@TypeExcludeFilters(ApiTest.ArchitectureFixtures.class)
class DemoSmokeTest extends DemoHttpSmoke {
    @Autowired
    DemoSmokeTest(@Value("${local.server.port}") int port) { super(port); }

}
