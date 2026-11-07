package io.github.stigjohnny.shipping.shipments.internal.domain;

/** Why a booking is refused. Each is a problem of the contract, named in its booking rules (bookShipment). */
public enum BookingRejection {
    /** The tenant has no quote with this ID; another tenant's quote is not found either. */
    QUOTE_NOT_FOUND,
    /** The quote exists, but the offer is not one of its offers. */
    OFFER_NOT_FOUND,
    /** The booking instant is at or after the quote's expiry. */
    QUOTE_EXPIRED,
    /** The recipient's country or postcode is not exactly the quote's destination. */
    RECIPIENT_DOES_NOT_MATCH_QUOTE,
    /** The quote already has a shipment, booked under another key. */
    QUOTE_ALREADY_BOOKED,
    /** The key already booked a shipment, for a request with another body. */
    IDEMPOTENCY_KEY_REUSED
}
