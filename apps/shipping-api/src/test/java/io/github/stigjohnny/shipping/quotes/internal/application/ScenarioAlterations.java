package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.OfferLookup;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.QuoteIssued;
import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteRequest;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteResult;
import io.github.stigjohnny.shipping.quotes.internal.domain.Rating;
import io.github.stigjohnny.shipping.quotes.internal.domain.SyntheticTariff;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Altered implementations of the quotes module, for the negative fixtures of the scenario checks
 * (ScenarioFixturesTest). Each is the real class with one interaction changed, and keeps the real one's outcome for
 * the fixture's tenant, so that the outcome assertions still pass; the static architecture check passes too, since
 * quotes still depends on tenants only through tenants.api. They live in the quotes package because the recorder
 * attributes a caller to a module by its package.
 */
public final class ScenarioAlterations {

    private ScenarioAlterations() {
    }

    /**
     * CreateQuote without its call to CurrentTenant: it uses a tenant fixed when it is wired, which is the tenant the
     * fixture binds, so the quote, the store and the event are the same. A removed call.
     */
    public static final class TenantFixedAtWiring extends CreateQuote {

        private final Clock clock;
        private final IdGenerator ids;
        private final ApplicationEventPublisher events;
        private final QuoteStore store;
        private final Rating rating;
        private final TenantId tenant;

        public TenantFixedAtWiring(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store, TenantId tenant) {
            super(clock, ids, currentTenant, events, store);
            this.clock = clock;
            this.ids = ids;
            this.events = events;
            this.store = store;
            this.rating = new Rating(new SyntheticTariff(), ids);
            this.tenant = Objects.requireNonNull(tenant, "tenant");
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            QuoteResult result = rating.quote(tenant, request, clock.instant());
            if (result instanceof QuoteResult.Offered(Quote quote)) {
                store.insert(quote);
                events.publishEvent(new QuoteIssued(ids.next(), tenant, quote.id(), quote.issuedAt()));
            }
            return result;
        }
    }

    /**
     * CreateQuote that rates, stores and publishes with a tenant fixed at wiring, and only then asks CurrentTenant and
     * fails if the two differ. For the fixture's tenant nothing differs but the order: the publication comes before
     * the tenant is resolved. Two interactions swapped.
     */
    public static final class PublishesBeforeResolvingTheTenant extends CreateQuote {

        private final CurrentTenant currentTenant;
        private final TenantFixedAtWiring publisher;
        private final TenantId tenant;

        public PublishesBeforeResolvingTheTenant(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store, TenantId tenant) {
            super(clock, ids, currentTenant, events, store);
            this.currentTenant = currentTenant;
            this.publisher = new TenantFixedAtWiring(clock, ids, currentTenant, events, store, tenant);
            this.tenant = tenant;
        }

        @Override
        public QuoteResult handle(QuoteRequest request) {
            QuoteResult result = publisher.handle(request);
            if (!currentTenant.require().equals(tenant)) {
                throw new IllegalStateException("the quote was issued for another tenant than the bound one");
            }
            return result;
        }
    }

    /**
     * QuoteOffers that also asks CurrentTenant for the current tenant before it looks the offer up with the tenant it is
     * given. quotes -> tenants is a REQUIRED relationship of the model, so the extra call is statically permitted, and
     * the answer is the real one's. An added call.
     */
    public static final class OffersThatAskForTheCurrentTenant implements QuoteOffers {

        private final CurrentTenant currentTenant;
        private final QuoteOffersService offers;

        public OffersThatAskForTheCurrentTenant(CurrentTenant currentTenant, QuoteStore store) {
            this.currentTenant = currentTenant;
            this.offers = new QuoteOffersService(store);
        }

        @Override
        public OfferLookup findOffer(TenantId tenant, QuoteId quote, OfferId offer, Instant at) {
            TenantId bound = currentTenant.require();
            return offers.findOffer(bound.equals(tenant) ? tenant : bound, quote, offer, at);
        }
    }
}
