package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.command;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.id;
import io.github.stigjohnny.shipping.quotes.api.BookableOffer;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.Recipient;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentCursor;
import io.github.stigjohnny.shipping.shipments.internal.domain.TrackingNumber;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

interface ShipmentStoreContract {
    StoreSession session() throws SQLException;

    static Shipment shipment(TenantId tenant, long number, Instant at) {
        var quote = exampleQuote(tenant, at);
        var offer = quote.offers().getFirst();
        var quoteId = new QuoteId(id(number));
        var bookable = new BookableOffer(quoteId, offer.id(), offer.level(), offer.price(), quote.request().origin(),
                quote.request().destination(), quote.expiresAt());
        return Shipment.book(new ShipmentId(id(number)), tenant, command(quoteId, offer.id()), bookable,
                new IdempotencyKey("synthetic-key-%016d".formatted(number)),
                new TrackingNumber("SIM%010d".formatted(number)), at);
    }

    @Test
    default void shipmentsRoundTripAndAllLookupsIsolateTenants() throws SQLException {
        try (StoreSession s = session()) {
            Shipment alpha = shipment(ALPHA, 1, QuoteStoreContract.ISSUED);
            s.shipments().insert(alpha);
            assertThat(s.shipments().findById(ALPHA, alpha.id())).contains(alpha);
            assertThat(s.shipments().findByKey(ALPHA, alpha.idempotencyKey())).contains(alpha);
            assertThat(s.shipments().findByQuote(ALPHA, alpha.quoteId())).contains(alpha);
            assertThat(s.shipments().findById(BETA, alpha.id())).isEmpty();
            assertThat(s.shipments().findByKey(BETA, alpha.idempotencyKey())).isEmpty();
            assertThat(s.shipments().findByQuote(BETA, alpha.quoteId())).isEmpty();
            assertThat(s.shipments().page(BETA, 10, null, null)).isEmpty();
            Shipment beta = shipment(BETA, 1, QuoteStoreContract.ISSUED);
            s.shipments().insert(beta);
            assertThat(s.shipments().findById(BETA, beta.id())).contains(beta);
        }
    }

    @Test
    default void optionalRecipientAndReferenceValuesRoundTrip() throws SQLException {
        try (StoreSession s = session()) {
            Shipment base = shipment(ALPHA, 1, QuoteStoreContract.ISSUED);
            Shipment changed = new Shipment(base.id(), base.tenantId(), base.quoteId(), base.offerId(),
                    base.serviceLevel(), base.price(), base.status(), base.trackingNumber(), base.origin(),
                    new Recipient("Synthetic Recipient 2", "2 Example Street", "Unit 3", "11122", "Exampleby", "SE"),
                    null, base.idempotencyKey(), base.requestFingerprint(), base.bookedAt());
            s.shipments().insert(changed);
            assertThat(s.shipments().findById(ALPHA, changed.id())).contains(changed);
        }
    }

    @Test
    default void pagesUseDescendingTimeAndUnsignedUuidOrderWithoutRepeats() throws SQLException {
        try (StoreSession s = session()) {
            Shipment low = shipment(ALPHA, 1, QuoteStoreContract.ISSUED);
            Shipment base = shipment(ALPHA, 2, QuoteStoreContract.ISSUED);
            Shipment high = new Shipment(new ShipmentId(UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff")),
                    base.tenantId(), base.quoteId(), base.offerId(), base.serviceLevel(), base.price(), base.status(),
                    base.trackingNumber(), base.origin(), base.recipient(), base.reference(), base.idempotencyKey(),
                    base.requestFingerprint(), base.bookedAt());
            Shipment latest = shipment(ALPHA, 3, QuoteStoreContract.ISSUED.plusMillis(1));
            s.shipments().insert(low);
            s.shipments().insert(high);
            s.shipments().insert(latest);
            s.shipments().insert(shipment(BETA, 4, QuoteStoreContract.ISSUED.plusSeconds(1)));
            assertThat(s.shipments().page(ALPHA, 2, null, "BOOKED")).containsExactly(latest, high);
            var after = new ShipmentCursor(ALPHA, high.bookedAt(), high.id(), "BOOKED");
            assertThat(s.shipments().page(ALPHA, 2, after, "BOOKED")).containsExactly(low);
            assertThat(s.shipments().page(ALPHA, 10, null, "CANCELLED")).isEmpty();
        }
    }

    @Test
    default void eachTenantUniqueKeyIsEnforcedIndependently() throws SQLException {
        for (int conflict = 0; conflict < 3; conflict++) {
            try (StoreSession s = session()) {
                Shipment first = shipment(ALPHA, 1, QuoteStoreContract.ISSUED);
                Shipment other = shipment(ALPHA, 2, QuoteStoreContract.ISSUED);
                Shipment duplicate = new Shipment(other.id(), other.tenantId(),
                        conflict == 0 ? first.quoteId() : other.quoteId(), other.offerId(), other.serviceLevel(),
                        other.price(), other.status(), conflict == 1 ? first.trackingNumber() : other.trackingNumber(),
                        other.origin(), other.recipient(), other.reference(),
                        conflict == 2 ? first.idempotencyKey() : other.idempotencyKey(),
                        other.requestFingerprint(), other.bookedAt());
                s.shipments().insert(first);
                assertThatThrownBy(() -> s.shipments().insert(duplicate)).isInstanceOf(RuntimeException.class);
            }
        }
    }

    @Test
    default void duplicateShipmentKeysAreRefused() throws SQLException {
        try (StoreSession s = session()) {
            Shipment shipment = shipment(ALPHA, 1, QuoteStoreContract.ISSUED);
            s.shipments().insert(shipment);
            assertThatThrownBy(() -> s.shipments().insert(shipment)).isInstanceOf(RuntimeException.class);
        }
    }
}
