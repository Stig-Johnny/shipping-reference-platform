package io.github.stigjohnny.shipping.quotes.api;

import java.time.Instant;
import java.util.Objects;

/** An offer that may be booked: everything shipments needs to book it and to check the recipient's address. */
public record BookableOffer(QuoteId quoteId, OfferId offerId, ServiceLevel serviceLevel, Money price, Place origin,
        Place destination, Instant expiresAt) {

    public BookableOffer {
        Objects.requireNonNull(quoteId, "quoteId");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(serviceLevel, "serviceLevel");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
