package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import io.github.stigjohnny.shipping.quotes.internal.adapter.out.jdbc.JdbcQuoteStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The same store behaviours as InMemoryStoresTest, against PostgreSQL as shipping_app. */
@Tag("database")
class QuotesModuleIntegrationTest implements QuoteStoreContract {
    private static DatabaseServer server;
    private static ShippingDatabase database;
    @BeforeAll static void start() throws SQLException {
        server = DatabaseServer.start();
        database = server.newDatabase("quotesmoduleintegrationtest").migrate();
    }
    @AfterAll static void stop() { server.close(); }
    @Override public JdbcStoreSession session() throws SQLException { return new JdbcStoreSession(database); }
    @Test void adaptersBindEachCallAndRollbackRemovesTheWholeQuote() throws SQLException {
        try (JdbcStoreSession s = session()) {
            var quote = exampleQuote(ALPHA, ISSUED);
            s.quotes().insert(quote);
            assertThat(Jdbc.value(s.connection, "select current_setting('app.tenant_id')")).isEqualTo(Fixtures.ALPHA);
            assertThat(Jdbc.count(s.connection, "quotes.quote_offer")).isEqualTo(3);
            assertThat(s.quotes().find(BETA, quote.id())).isEmpty();
            assertThat(Jdbc.count(s.connection, "quotes.quote")).isZero(); // No WHERE: RLS alone.
            s.connection.rollback();
            assertThat(Jdbc.value(s.connection, "select coalesce(current_setting('app.tenant_id', true), '')"))
                    .isEmpty();
            assertThat(s.quotes().find(ALPHA, quote.id())).isEmpty();
            assertThat(Jdbc.count(s.connection, "quotes.quote_offer")).isZero();
        }
    }
    @Test void offerLookupPreservesMissingForeignAndExpiredOutcomes() throws SQLException {
        try (JdbcStoreSession s = session()) {
            var quote = exampleQuote(ALPHA, ISSUED);
            s.quotes().insert(quote);
            var offers = new io.github.stigjohnny.shipping.quotes.internal.application.QuoteOffersService(s.quotes());
            var id = quote.offers().getFirst().id();
            assertThat(offers.findOffer(ALPHA, quote.id(), id, quote.issuedAt()))
                    .isInstanceOf(io.github.stigjohnny.shipping.quotes.api.OfferLookup.Bookable.class);
            assertThat(offers.findOffer(BETA, quote.id(), id, quote.issuedAt()))
                    .isInstanceOf(io.github.stigjohnny.shipping.quotes.api.OfferLookup.QuoteNotFound.class);
            assertThat(offers.findOffer(ALPHA, quote.id(), id, quote.expiresAt()))
                    .isInstanceOf(io.github.stigjohnny.shipping.quotes.api.OfferLookup.Expired.class);
        }
    }

    @Test void aQuoteCommittingDuringReadCannotAppearWithoutItsOffers() throws Exception {
        ShippingDatabase race = server.newDatabase("quote_read_race").migrate();
        var quote = exampleQuote(ALPHA, ISSUED);
        try (Connection owner = race.owner(); JdbcStoreSession reader = new JdbcStoreSession(race);
                var workers = Executors.newSingleThreadExecutor()) {
            owner.setAutoCommit(false);
            // Hold only the parent table. A reader that asks for offers first can see none while this is uncommitted.
            Jdbc.update(owner, "lock table quotes.quote in access exclusive mode");
            var writer = new JdbcQuoteStore(JdbcClient.create(new SingleConnectionDataSource(owner, true)));
            writer.insert(quote);
            long pid = Long.parseLong(Jdbc.value(reader.connection, "select pg_backend_pid()::text"));
            var waiting = workers.submit(() -> reader.quotes().find(ALPHA, quote.id()));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean blocked = false;
                try (Connection catalog = race.superuser()) {
                    while (System.nanoTime() < deadline) {
                        if (Jdbc.value(catalog, "select count(*) from pg_locks where pid = ?"
                                + " and relation = 'quotes.quote'::regclass and not granted", pid).equals("1")) {
                            blocked = true;
                            break;
                        }
                        TimeUnit.MILLISECONDS.sleep(10);
                    }
                }
                assertThat(blocked).as("reader waits for the parent table before the writer commits").isTrue();
            } finally {
                owner.commit(); // Release the reader even when the wait assertion fails.
            }
            assertThat(waiting.get(10, TimeUnit.SECONDS)).contains(quote);
        }
    }

}
