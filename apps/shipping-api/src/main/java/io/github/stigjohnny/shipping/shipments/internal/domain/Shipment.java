package io.github.stigjohnny.shipping.shipments.internal.domain;

import io.github.stigjohnny.shipping.quotes.api.BookableOffer;
import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A booked shipment, the module's aggregate, owned by one tenant: one offer of one quote, booked for a recipient under
 * an Idempotency-Key, with the fingerprint of the request that booked it, a tracking number and the booking instant,
 * which has millisecond precision. Booking is built; the cancellation instant and the version for optimistic locking
 * (docs/modules/shipments.md, "Domain model") arrive with cancellation, their first use.
 */
public record Shipment(ShipmentId id, TenantId tenantId, QuoteId quoteId, OfferId offerId, ServiceLevel serviceLevel,
        Money price, ShipmentStatus status, TrackingNumber trackingNumber, Place origin, Recipient recipient,
        @Nullable ShipmentReference reference, IdempotencyKey idempotencyKey, RequestFingerprint requestFingerprint,
        Instant bookedAt) {

    public Shipment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(quoteId, "quoteId");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(serviceLevel, "serviceLevel");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(bookedAt, "bookedAt");
        if (!bookedAt.equals(bookedAt.truncatedTo(ChronoUnit.MILLIS))) {
            throw new IllegalArgumentException("A shipment's booking instant has millisecond precision, not "
                    + bookedAt + ".");
        }
    }

    /**
     * Books the offer that the command names, for the tenant: a new shipment in BOOKED with the offer's quote, offer,
     * service level, price and origin, the command's recipient and reference, the key and the command's fingerprint,
     * and the booking instant truncated to milliseconds.
     *
     * @param at the injected clock's instant
     */
    public static Shipment book(ShipmentId id, TenantId tenant, BookingCommand command, BookableOffer offer,
            IdempotencyKey key, TrackingNumber trackingNumber, Instant at) {
        if (!offer.quoteId().equals(command.quoteId()) || !offer.offerId().equals(command.offerId())) {
            throw new IllegalArgumentException("A shipment books the offer that its command names.");
        }
        return new Shipment(id, tenant, offer.quoteId(), offer.offerId(), offer.serviceLevel(), offer.price(),
                ShipmentStatus.BOOKED, trackingNumber, offer.origin(), command.recipient(), command.reference(), key,
                RequestFingerprint.of(command), at.truncatedTo(ChronoUnit.MILLIS));
    }
}
