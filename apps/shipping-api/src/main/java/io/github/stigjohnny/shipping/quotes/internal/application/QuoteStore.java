package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.Optional;

/**
 * The port through which quotes are stored and found, always by tenant. The postgres-profile JDBC adapter implements it on the
 * tables of schema quotes (docs/modules/quotes.md); the tests use an implementation in memory.
 */
public interface QuoteStore {

    /** Stores a newly issued quote with its offers. Quotes never change once stored. */
    void insert(Quote quote);

    /** The tenant's quote with this ID; another tenant's quote is never found. */
    Optional<Quote> find(TenantId tenant, QuoteId quote);
}
