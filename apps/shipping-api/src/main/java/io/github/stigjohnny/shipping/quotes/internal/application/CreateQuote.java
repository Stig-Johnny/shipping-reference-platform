package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteIssued;
import io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteRequest;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.quotes.internal.domain.Rating;
import io.github.stigjohnny.shipping.quotes.internal.domain.SyntheticTariff;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Clock;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a quote for the current tenant: rates the request at the clock's instant and, when something is offered,
 * stores the quote and publishes {@link QuoteIssued}. An unavailable result is neither stored nor published. The
 * transaction takes effect once the class is a Spring bean with a transaction manager; both arrive with the planned
 * JDBC adapter, and nothing tests the transaction before then. The class is not final, so that Spring can proxy it.
 */
public class CreateQuote {

    private final Clock clock;
    private final IdGenerator ids;
    private final CurrentTenant currentTenant;
    private final ApplicationEventPublisher events;
    private final QuoteStore store;
    private final Rating rating;

    public CreateQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant, ApplicationEventPublisher events,
            QuoteStore store) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.currentTenant = Objects.requireNonNull(currentTenant, "currentTenant");
        this.events = Objects.requireNonNull(events, "events");
        this.store = Objects.requireNonNull(store, "store");
        this.rating = new Rating(new SyntheticTariff(), ids);
    }

    /** Rates and, if something is offered, stores and publishes; the tenant is the current one, never the request's. */
    @Transactional
    public QuoteResult handle(QuoteRequest request) {
        TenantId tenant = currentTenant.require();
        QuoteResult result = rating.quote(tenant, request, clock.instant());
        switch (result) {
            case QuoteResult.Offered(Quote quote) -> {
                store.insert(quote);
                events.publishEvent(new QuoteIssued(ids.next(), tenant, quote.id(), quote.issuedAt()));
            }
            case QuoteResult.Unavailable _ -> {
                // Nothing is stored and nothing is published.
            }
        }
        return result;
    }
}
