package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a quote of the current tenant. Another tenant's quote is not found, exactly like an unknown ID. As with
 * CreateQuote, the read-only transaction takes effect once a transaction manager exists, which arrives with the
 * planned JDBC adapter; the class is not final, so that Spring can proxy it then.
 */
public class GetQuote {

    private final CurrentTenant currentTenant;
    private final QuoteStore store;

    public GetQuote(CurrentTenant currentTenant, QuoteStore store) {
        this.currentTenant = Objects.requireNonNull(currentTenant, "currentTenant");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** The current tenant's quote with this ID; the tenant is the current one, never the request's. */
    @Transactional(readOnly = true)
    public Optional<Quote> handle(QuoteId quote) {
        return store.find(currentTenant.require(), quote);
    }
}
