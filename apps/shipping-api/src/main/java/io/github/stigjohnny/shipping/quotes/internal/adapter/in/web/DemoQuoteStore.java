package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Disposable, tenant-keyed storage for the attended memory demo; never database evidence. */
public final class DemoQuoteStore implements QuoteStore {

    private record Key(TenantId tenant, QuoteId quote) {
    }

    private final Map<Key, Quote> quotes = new LinkedHashMap<>();

    @Override
    public synchronized void insert(Quote quote) {
        if (quotes.putIfAbsent(new Key(quote.tenantId(), quote.id()), quote) != null) {
            throw new IllegalStateException("quote " + quote.id().value() + " is already stored for this tenant");
        }
    }

    @Override
    public synchronized Optional<Quote> find(TenantId tenant, QuoteId quote) {
        return Optional.ofNullable(quotes.get(new Key(tenant, quote)));
    }

    /** Every stored quote, in insertion order. */
    public synchronized List<Quote> all() {
        return List.copyOf(quotes.values());
    }
}
