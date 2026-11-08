package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The in-memory store behind a gate, for the scenario checks' interleaving fixture (ScenarioFixturesTest): the insert
 * of the gated tenant's quote waits until the test opens the gate, so that another run can happen inside it.
 */
public final class GatedQuoteStore implements QuoteStore {

    private static final long WAIT_SECONDS = 10;

    private final TenantId gated;
    private final CountDownLatch waiting = new CountDownLatch(1);
    private final CountDownLatch open = new CountDownLatch(1);
    private final InMemoryQuoteStore quotes = new InMemoryQuoteStore();

    public GatedQuoteStore(TenantId gated) {
        this.gated = gated;
    }

    /** Waits until the gated tenant's insert has reached the gate. */
    public boolean awaitWaiting() throws InterruptedException {
        return waiting.await(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /** Lets the gated insert through. */
    public void open() {
        open.countDown();
    }

    @Override
    public void insert(Quote quote) {
        if (quote.tenantId().equals(gated)) {
            waiting.countDown();
            try {
                if (!open.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the gate was not opened");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        synchronized (quotes) {
            quotes.insert(quote);
        }
    }

    @Override
    public Optional<Quote> find(TenantId tenant, QuoteId quote) {
        synchronized (quotes) {
            return quotes.find(tenant, quote);
        }
    }
}
