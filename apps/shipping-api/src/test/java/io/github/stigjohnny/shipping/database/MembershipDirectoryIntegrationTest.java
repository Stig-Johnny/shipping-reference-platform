package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The membership contract from InMemoryStoresTest, with real runtime-role SQL and transaction rollback per case. */
@Tag("database")
class MembershipDirectoryIntegrationTest implements MembershipDirectoryContract {
    private static DatabaseServer server;
    private static ShippingDatabase database;

    @BeforeAll static void start() throws SQLException {
        server = DatabaseServer.start();
        database = server.newDatabase("jdbc_contracts").migrate();
        try (Connection owner = database.owner()) {
            Jdbc.update(owner, "insert into tenants.tenant values (?::uuid, 'Tenant Alpha (synthetic)'),"
                    + " (?::uuid, 'Tenant Beta (synthetic)')", Fixtures.ALPHA, Fixtures.BETA);
            Jdbc.update(owner, "insert into tenants.app_user values"
                    + " (?::uuid, 'clerk-alpha', 'Clerk Alpha (synthetic)'),"
                    + " ('00000000-0000-4000-8000-0000000000b1', 'clerk-beta', 'Clerk Beta (synthetic)'),"
                    + " (?::uuid, 'no-tenant-user', 'User without tenant (synthetic)')", Fixtures.CLERK_ALPHA,
                    Fixtures.NO_TENANT_USER);
            Jdbc.update(owner, "insert into tenants.membership values (?::uuid, ?::uuid, 'CLERK'),"
                    + " ('00000000-0000-4000-8000-0000000000b1', ?::uuid, 'CLERK')", Fixtures.CLERK_ALPHA,
                    Fixtures.ALPHA, Fixtures.BETA);
            Jdbc.update(owner, "insert into tenants.demo_token select sha256(convert_to('demo-' || handle, 'UTF8')),"
                    + " user_id from tenants.app_user");
        }
    }
    @AfterAll static void stop() { server.close(); }
    @Override public JdbcStoreSession session() throws SQLException { return new JdbcStoreSession(database); }

}
