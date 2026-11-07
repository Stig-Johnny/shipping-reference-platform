package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link QuoteStore} port in memory, for tests: quotes by tenant and ID, as the planned table keys them, and a
 * second insert of the same key fails as its primary key would. The scenario checks wire it too (scenarios package),
 * which is why it is public; it is never database evidence.
 */
public final class InMemoryQuoteStore implements QuoteStore {

    private record Key(TenantId tenant, QuoteId quote) {
    }

    private final Map<Key, Quote> quotes = new LinkedHashMap<>();

    @Override
    public void insert(Quote quote) {
        if (quotes.putIfAbsent(new Key(quote.tenantId(), quote.id()), quote) != null) {
            throw new IllegalStateException("quote " + quote.id().value() + " is already stored for this tenant");
        }
    }

    @Override
    public Optional<Quote> find(TenantId tenant, QuoteId quote) {
        return Optional.ofNullable(quotes.get(new Key(tenant, quote)));
    }

    /** Every stored quote, in insertion order. */
    public List<Quote> all() {
        return List.copyOf(quotes.values());
    }
}
