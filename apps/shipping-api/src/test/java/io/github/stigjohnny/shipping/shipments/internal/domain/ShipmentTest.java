package io.github.stigjohnny.shipping.shipments.internal.domain;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.OSLO;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.STOCKHOLM;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.eur;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.KEY;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.RECIPIENT;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.REFERENCE;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.command;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.shipping.quotes.api.BookableOffer;
import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The booking half of the shipment aggregate: what {@code book} produces from an offer and a command, and the values a
 * shipment carries. Cancellation, the other half of invariant S1, is planned, with CancelShipmentTest.
 */
class ShipmentTest {

    private static final BookableOffer OFFER = new BookableOffer(new QuoteId(id(901)), new OfferId(id(902)),
            ServiceLevel.STANDARD, eur("18.81"), OSLO, STOCKHOLM, Instant.parse("2026-11-10T09:30:00Z"));
    private static final BookingCommand COMMAND = command(OFFER.quoteId(), OFFER.offerId());
    private static final TrackingNumber FIRST = new TrackingNumber("SIM0000000001");
    private static final Instant NOW = Instant.parse("2026-11-10T09:05:00.000Z");

    @Test
    void bookingMakesABookedShipmentOfTheOffersTermsAndTheCommandsRecipient() {
        Shipment shipment = Shipment.book(new ShipmentId(id(1)), ALPHA, COMMAND, OFFER, KEY, FIRST, NOW);

        assertThat(shipment).isEqualTo(new Shipment(new ShipmentId(id(1)), ALPHA, OFFER.quoteId(), OFFER.offerId(),
                ServiceLevel.STANDARD, eur("18.81"), ShipmentStatus.BOOKED, FIRST, OSLO, RECIPIENT, REFERENCE, KEY,
                RequestFingerprint.of(COMMAND), NOW));
    }

    @Test
    void theBookingInstantIsKeptToTheMillisecond() {
        Shipment shipment = Shipment.book(new ShipmentId(id(1)), ALPHA, COMMAND, OFFER, KEY, FIRST,
                Instant.parse("2026-11-10T09:05:00.123456789Z"));

        assertThat(shipment.bookedAt()).isEqualTo(Instant.parse("2026-11-10T09:05:00.123Z"));
        assertThatThrownBy(() -> new Shipment(shipment.id(), ALPHA, shipment.quoteId(), shipment.offerId(),
                shipment.serviceLevel(), shipment.price(), shipment.status(), FIRST, shipment.origin(), RECIPIENT,
                REFERENCE, KEY, shipment.requestFingerprint(), shipment.bookedAt().plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A shipment's booking instant has millisecond precision, not "
                        + "2026-11-10T09:05:00.123000001Z.");
    }

    @Test
    void aShipmentBooksTheOfferItsCommandNames() {
        BookingCommand otherOffer = command(OFFER.quoteId(), new OfferId(id(903)));
        BookingCommand otherQuote = command(new QuoteId(id(904)), OFFER.offerId());

        for (BookingCommand command : List.of(otherOffer, otherQuote)) {
            assertThatThrownBy(() -> Shipment.book(new ShipmentId(id(1)), ALPHA, command, OFFER, KEY, FIRST, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("A shipment books the offer that its command names.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SIM0000000001", "SIM9999999999", "SIM0123456789"})
    void aTrackingNumberIsSimAndTenDigits(String value) {
        assertThat(new TrackingNumber(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "SIM", "SIM000000001", "SIM00000000001", "sim0000000001", "SIM000000000A",
            "XSIM000000001", "SIM0000000001\n"})
    void anythingElseIsNoTrackingNumber(String value) {
        assertThatThrownBy(() -> new TrackingNumber(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A tracking number is SIM and ten digits.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"6f1d2c3b-4a5e-4f60-8172-93a4b5c6d7e8", "abcdefghijklmnop", "booking_key-0001",
            "0123456789012345678901234567890123456789012345678901234567890123"})
    void anIdempotencyKeyIsSixteenToSixtyFourLettersDigitsUnderscoresOrHyphens(String value) {
        assertThat(new IdempotencyKey(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abcdefghijklmno", "01234567890123456789012345678901234567890123456789012345678901234",
            "booking key 0001", "booking.key.0001", "booking-key-000ø", "booking-key-0001\n"})
    void anythingElseIsNoIdempotencyKey(String value) {
        assertThatThrownBy(() -> new IdempotencyKey(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An Idempotency-Key is 16 to 64 letters, digits, underscores or hyphens.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER-1001", "A", "Order 2026/11.10_A-1", "12345678901234567890123456789012345"})
    void aReferenceIsOneToThirtyFiveOfTheContractsCharacters(String value) {
        assertThat(new ShipmentReference(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123456789012345678901234567890123456", "ORDER#1001", "ORDER-1001\n",
            "ORDRE-ø"})
    void anythingElseIsNoReference(String value) {
        assertThatThrownBy(() -> new ShipmentReference(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reference is 1 to 35 letters, digits, spaces, dots, underscores, slashes or "
                        + "hyphens.");
    }
}
