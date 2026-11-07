package io.github.stigjohnny.shipping.shipments.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** What a clerk asks to book: one offer of a quote, for a recipient and, optionally, with the webshop's reference. */
public record BookingCommand(QuoteId quoteId, OfferId offerId, Recipient recipient,
        @Nullable ShipmentReference reference) {

    public BookingCommand {
        Objects.requireNonNull(quoteId, "quoteId");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(recipient, "recipient");
    }
}
