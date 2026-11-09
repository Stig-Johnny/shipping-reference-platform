package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The same store behaviours as InMemoryStoresTest, against PostgreSQL as shipping_app. */
@Tag("database")
class ShipmentListingIntegrationTest implements ShipmentStoreContract {
    private static DatabaseServer server;
    private static ShippingDatabase database;
    @BeforeAll static void start() throws SQLException {
        server = DatabaseServer.start();
        database = server.newDatabase("shipmentlistingintegrationtest").migrate();
    }
    @AfterAll static void stop() { server.close(); }
    @Override public JdbcStoreSession session() throws SQLException { return new JdbcStoreSession(database); }

}
