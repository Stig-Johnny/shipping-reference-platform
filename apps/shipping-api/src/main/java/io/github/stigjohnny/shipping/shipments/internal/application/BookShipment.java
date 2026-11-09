package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.quotes.api.BookableOffer;
import io.github.stigjohnny.shipping.quotes.api.OfferLookup;
import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.shipments.api.ShipmentBooked;
import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingCommand;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingOutcome;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingRejection;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.Recipient;
import io.github.stigjohnny.shipping.shipments.internal.domain.RequestFingerprint;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Books a shipment for the current tenant, by the booking rules in their documented order (docs/modules/shipments.md,
 * "Booking rules"): the tenant; the key, whose earlier shipment is replayed for the same body and refused for another;
 * the offer, looked up through QuoteOffers at the clock's instant; the recipient, who must live at the quote's
 * destination; the quote, which is booked once. Only then is the shipment created in BOOKED, given the next tracking
 * number, stored and {@link ShipmentBooked} published. A rejection stores and publishes nothing, so a key that was
 * refused is evaluated again when it is sent again.
 *
 * <p>The HTTP adapter wires this class as a Spring bean. With the postgres profile and a configured data source,
 * Spring's JDBC transaction manager encloses the whole booking. JdbcShipmentStore acquires a transaction-scoped
 * tenant/key lock before the first lookup and a tenant/quote lock before the quote lookup, each followed by a fresh
 * READ COMMITTED statement (ADR 0017). The unique constraints remain the final guard. Existing HTTP tests retain
 * in-memory ports without a transaction manager; database fixtures explicitly control their JDBC transactions.
 * This class is not final so Spring can proxy it. Durable event publication remains separate work.
 */
public class BookShipment {

    private final Clock clock;
    private final IdGenerator ids;
    private final CurrentTenant currentTenant;
    private final QuoteOffers quoteOffers;
    private final ApplicationEventPublisher events;
    private final ShipmentStore store;
    private final TrackingNumbers trackingNumbers;

    public BookShipment(Clock clock, IdGenerator ids, CurrentTenant currentTenant, QuoteOffers quoteOffers,
            ApplicationEventPublisher events, ShipmentStore store, TrackingNumbers trackingNumbers) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.currentTenant = Objects.requireNonNull(currentTenant, "currentTenant");
        this.quoteOffers = Objects.requireNonNull(quoteOffers, "quoteOffers");
        this.events = Objects.requireNonNull(events, "events");
        this.store = Objects.requireNonNull(store, "store");
        this.trackingNumbers = Objects.requireNonNull(trackingNumbers, "trackingNumbers");
    }

    /** Books, replays or refuses; the tenant is the current one, never the request's. */
    @Transactional
    public BookingOutcome handle(IdempotencyKey key, BookingCommand command) {
        TenantId tenant = currentTenant.require();
        RequestFingerprint fingerprint = RequestFingerprint.of(command);
        Optional<Shipment> earlier = store.findByKey(tenant, key);
        if (earlier.isPresent()) {
            Shipment shipment = earlier.get();
            return shipment.requestFingerprint().equals(fingerprint) ? new BookingOutcome.Replayed(shipment)
                    : rejected(BookingRejection.IDEMPOTENCY_KEY_REUSED);
        }
        Instant now = clock.instant();
        return switch (quoteOffers.findOffer(tenant, command.quoteId(), command.offerId(), now)) {
            case OfferLookup.Bookable(BookableOffer offer) -> book(tenant, key, command, offer, now);
            case OfferLookup.QuoteNotFound _ -> rejected(BookingRejection.QUOTE_NOT_FOUND);
            case OfferLookup.OfferNotFound _ -> rejected(BookingRejection.OFFER_NOT_FOUND);
            case OfferLookup.Expired _ -> rejected(BookingRejection.QUOTE_EXPIRED);
        };
    }

    /** The rules after the offer's: the recipient's address, then the quote's earlier booking; then the booking. */
    private BookingOutcome book(TenantId tenant, IdempotencyKey key, BookingCommand command, BookableOffer offer,
            Instant now) {
        if (!livesAt(command.recipient(), offer.destination())) {
            return rejected(BookingRejection.RECIPIENT_DOES_NOT_MATCH_QUOTE);
        }
        if (store.findByQuote(tenant, offer.quoteId()).isPresent()) {
            return rejected(BookingRejection.QUOTE_ALREADY_BOOKED);
        }
        Shipment shipment = Shipment.book(new ShipmentId(ids.next()), tenant, command, offer, key,
                trackingNumbers.next(), now);
        store.insert(shipment);
        events.publishEvent(new ShipmentBooked(ids.next(), tenant, shipment.id(), shipment.bookedAt()));
        return new BookingOutcome.Booked(shipment);
    }

    /** Whether the recipient's country and postcode are exactly the place's: both are upper case by contract. */
    private static boolean livesAt(Recipient recipient, Place destination) {
        return recipient.countryCode().equals(destination.countryCode())
                && recipient.postcode().equals(destination.postcode());
    }

    private static BookingOutcome rejected(BookingRejection reason) {
        return new BookingOutcome.Rejected(reason);
    }
}
