package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The quotes module's ports without production adapters, for the application started in tests: the store is in
 * memory, so tests that use it prove the HTTP edge, not the database, and the IDs are deterministic. Reading
 * {@link #FAILING_QUOTE} fails as an unavailable database would. The JDBC adapter replaces the store with the
 * PostgreSQL-backed demo profile.
 */
@TestConfiguration(proxyBeanMethods = false)
public class QuotesTestDoubles {

    /** Reading this quote fails with an exception whose message no response may show. */
    public static final QuoteId FAILING_QUOTE = new QuoteId(UUID.fromString("0000dead-0000-4000-8000-000000000000"));
    public static final String FAILURE_MESSAGE = "synthetic store failure 4c1e";

    @Bean
    QuoteStore quoteStore() {
        InMemoryQuoteStore quotes = new InMemoryQuoteStore();
        return new QuoteStore() {
            @Override
            public synchronized void insert(Quote quote) {
                quotes.insert(quote);
            }

            @Override
            public synchronized Optional<Quote> find(TenantId tenant, QuoteId quote) {
                if (quote.equals(FAILING_QUOTE)) {
                    throw new IllegalStateException(FAILURE_MESSAGE);
                }
                return quotes.find(tenant, quote);
            }
        };
    }

    @Bean
    @Primary
    IdGenerator sequentialQuoteIds() {
        return QuoteFixtures.sequentialIds();
    }
}
