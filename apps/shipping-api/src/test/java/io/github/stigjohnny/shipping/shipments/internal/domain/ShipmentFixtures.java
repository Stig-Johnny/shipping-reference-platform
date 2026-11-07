package io.github.stigjohnny.shipping.shipments.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synthetic values for the shipments tests: the contract's example booking request (bookShipment) and deterministic
 * IDs, apart from those of the quotes tests. The recipient lives at the destination of the quotes tests' example quote,
 * SE 11122.
 */
public final class ShipmentFixtures {

    /** The contract's example recipient, without a second address line. */
    public static final Recipient RECIPIENT = new Recipient("Synthetic Recipient 1", "1 Example Street", null, "11122",
            "Exampleby", "SE");
    /** The contract's example reference. */
    public static final ShipmentReference REFERENCE = new ShipmentReference("ORDER-1001");
    /** The contract's example Idempotency-Key. */
    public static final IdempotencyKey KEY = new IdempotencyKey("6f1d2c3b-4a5e-4f60-8172-93a4b5c6d7e8");

    private ShipmentFixtures() {
    }

    /** The booking of an offer of a quote for the example recipient, with the example reference. */
    public static BookingCommand command(QuoteId quote, OfferId offer) {
        return new BookingCommand(quote, offer, RECIPIENT, REFERENCE);
    }

    /** The example recipient, at another postcode and in another country. */
    public static Recipient recipientAt(String countryCode, String postcode) {
        return new Recipient(RECIPIENT.name(), RECIPIENT.addressLine1(), null, postcode, RECIPIENT.city(), countryCode);
    }

    /** Hands out {@link #id(long) id(1)}, {@code id(2)} and so on, in the order they are asked for. */
    public static IdGenerator sequentialIds() {
        AtomicLong last = new AtomicLong();
        return () -> id(last.incrementAndGet());
    }

    /** The n-th deterministic ID of the shipments tests: 00000000-0000-4000-9000-000000000001 for 1. */
    public static UUID id(long n) {
        return UUID.fromString("00000000-0000-4000-9000-%012d".formatted(n));
    }
}
