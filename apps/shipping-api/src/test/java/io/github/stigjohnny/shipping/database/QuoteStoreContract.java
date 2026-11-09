package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.id;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.offered;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.request;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.sequentialIds;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.Rating;
import io.github.stigjohnny.shipping.quotes.internal.domain.SyntheticTariff;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

interface QuoteStoreContract {
    Instant ISSUED = Instant.parse("2026-01-05T09:00:00.123Z");
    StoreSession session() throws SQLException;

    @Test
    default void quotesRoundTripEveryOfferAndRemainTenantScoped() throws SQLException {
        try (StoreSession s = session()) {
            Quote alpha = exampleQuote(ALPHA, ISSUED);
            Quote beta = exampleQuote(BETA, ISSUED.plusSeconds(60));
            assertThat(s.quotes().find(ALPHA, alpha.id())).isEmpty();
            s.quotes().insert(alpha);
            assertThat(s.quotes().find(BETA, alpha.id())).isEmpty();
            s.quotes().insert(beta); // The same quote and offer UUIDs may belong to another tenant.
            assertThat(s.quotes().find(ALPHA, alpha.id())).contains(alpha);
            assertThat(s.quotes().find(BETA, beta.id())).contains(beta);
            assertThat(s.quotes().find(ALPHA, new QuoteId(id(99)))).isEmpty();
        }
    }

    @Test
    default void aSelectedServiceAndItsExactMoneyRoundTrip() throws SQLException {
        try (StoreSession s = session()) {
            Quote quote = offered(new Rating(new SyntheticTariff(), sequentialIds()).quote(ALPHA,
                    request(EXAMPLE.parcel(), ServiceLevel.STANDARD), ISSUED));
            s.quotes().insert(quote);
            assertThat(s.quotes().find(ALPHA, quote.id())).contains(quote);
        }
    }

    @Test
    default void duplicateQuoteKeysAreRefused() throws SQLException {
        try (StoreSession s = session()) {
            Quote quote = exampleQuote(ALPHA, ISSUED);
            s.quotes().insert(quote);
            assertThatThrownBy(() -> s.quotes().insert(quote)).isInstanceOf(RuntimeException.class);
        }
    }
}
