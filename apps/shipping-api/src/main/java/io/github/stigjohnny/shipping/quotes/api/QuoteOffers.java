package io.github.stigjohnny.shipping.quotes.api;

import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Instant;

/** What shipments asks quotes before it books: does this offer exist for the tenant, and may it be booked now? */
public interface QuoteOffers {

    /**
     * Looks the offer up, in the contract's order: an unknown quote or another tenant's is {@code QuoteNotFound}, an
     * offer that is not in the quote {@code OfferNotFound}, and an instant at or after the quote's expiry
     * {@code Expired}. Runs in the caller's transaction.
     */
    OfferLookup findOffer(TenantId tenant, QuoteId quote, OfferId offer, Instant at);
}
