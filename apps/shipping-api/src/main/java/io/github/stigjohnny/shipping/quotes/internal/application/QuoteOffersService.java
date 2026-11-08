package io.github.stigjohnny.shipping.quotes.internal.application;

import io.github.stigjohnny.shipping.quotes.api.BookableOffer;
import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.OfferLookup;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.quotes.internal.domain.Offer;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteStatus;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** {@link QuoteOffers} from the tenant's stored quotes; it opens no transaction of its own. */
public final class QuoteOffersService implements QuoteOffers {

    private final QuoteStore store;

    public QuoteOffersService(QuoteStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public OfferLookup findOffer(TenantId tenant, QuoteId quote, OfferId offer, Instant at) {
        Optional<Quote> found = store.find(tenant, quote);
        if (found.isEmpty()) {
            return new OfferLookup.QuoteNotFound();
        }
        Quote stored = found.get();
        Optional<Offer> match = stored.offer(offer);
        if (match.isEmpty()) {
            return new OfferLookup.OfferNotFound();
        }
        if (stored.status(at) == QuoteStatus.EXPIRED) {
            return new OfferLookup.Expired(stored.expiresAt());
        }
        Offer bookable = match.get();
        return new OfferLookup.Bookable(new BookableOffer(stored.id(), bookable.id(), bookable.level(),
                bookable.price(), stored.request().origin(), stored.request().destination(), stored.expiresAt()));
    }
}
