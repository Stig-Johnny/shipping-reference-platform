package io.github.stigjohnny.shipping.quotes.api;

import java.time.Instant;
import java.util.Objects;

/** Whether an offer may be booked at an instant: the answer of {@link QuoteOffers#findOffer}. */
public sealed interface OfferLookup permits OfferLookup.Bookable, OfferLookup.QuoteNotFound, OfferLookup.OfferNotFound,
        OfferLookup.Expired {

    /** The offer exists for the tenant and has not expired. */
    record Bookable(BookableOffer offer) implements OfferLookup {

        public Bookable {
            Objects.requireNonNull(offer, "offer");
        }
    }

    /** The tenant has no quote with this ID; another tenant's quote is not found either. */
    record QuoteNotFound() implements OfferLookup {
    }

    /** The quote exists, but the offer is not one of its offers. */
    record OfferNotFound() implements OfferLookup {
    }

    /** The offer exists, but the instant is at or after its expiry. */
    record Expired(Instant expiredAt) implements OfferLookup {

        public Expired {
            Objects.requireNonNull(expiredAt, "expiredAt");
        }
    }
}
