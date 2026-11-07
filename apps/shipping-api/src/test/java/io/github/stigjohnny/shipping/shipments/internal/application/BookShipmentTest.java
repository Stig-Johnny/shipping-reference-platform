package io.github.stigjohnny.shipping.shipments.internal.application;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.OSLO;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.eur;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.KEY;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.RECIPIENT;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.REFERENCE;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.command;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.id;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.recipientAt;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.sequentialIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.quotes.internal.application.InMemoryQuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteOffersService;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
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
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentReference;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentStatus;
import io.github.stigjohnny.shipping.shipments.internal.domain.TrackingNumber;
import io.github.stigjohnny.shipping.tenants.api.MissingTenantContextException;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Booking (invariants S2 to S4), and its replay and key reuse at the application level. The booking rules run in the
 * documented order and each refusal is the documented rejection; an offer is bookable until its quote's expiry,
 * exclusive; the recipient's country and postcode must be exactly the quote's destination; the same key with the same
 * body replays the shipment, and with another body is refused; a rejection stores and publishes nothing. The quote is
 * a real one, issued by the quotes module's rating and found through QuoteOffers with its real expiry, and the tenant
 * is bound with the tenants module's thread context. Shipments are stored in memory and the publisher records what it
 * gets: the transaction, concurrent requests and the database are for BookingIdempotencyIntegrationTest and
 * ShipmentEventsIntegrationTest, which are planned.
 */
class BookShipmentTest {

    /** The quote's issue instant; it expires 30 minutes later. */
    private static final Instant ISSUED = Instant.parse("2026-11-10T09:00:00.000Z");
    private static final Instant EXPIRES = Instant.parse("2026-11-10T09:30:00.000Z");
    private static final Instant NOW = Instant.parse("2026-11-10T09:05:00.000Z");
    private static final IdempotencyKey OTHER_KEY = new IdempotencyKey("booking-key-0000000002");
    /** IDs that no quote and no offer has. */
    private static final QuoteId UNKNOWN_QUOTE = new QuoteId(id(98));
    private static final OfferId UNKNOWN_OFFER = new OfferId(id(99));

    private final Quote quote = exampleQuote(ALPHA, ISSUED);
    /** The quote's offers, in ServiceLevel order. */
    private final OfferId economy = quote.offers().get(0).id();
    private final OfferId standard = quote.offers().get(1).id();
    private final BookingCommand bookStandard = command(quote.id(), standard);
    private final InMemoryQuoteStore quotes = new InMemoryQuoteStore();
    private final ThreadTenantContext tenants = new ThreadTenantContext();
    private final InMemoryShipmentStore store = new InMemoryShipmentStore();
    private final List<Object> published = new ArrayList<>();
    private final IdGenerator ids = sequentialIds();
    private final AtomicLong trackingNumbersTaken = new AtomicLong();
    private final TrackingNumbers trackingNumbers = () -> new TrackingNumber("SIM%010d".formatted(
            trackingNumbersTaken.incrementAndGet()));

    BookShipmentTest() {
        quotes.insert(quote);
    }

    @Test
    void aBookableOfferIsBookedForTheCurrentTenantThenStoredAndPublished() {
        Shipment shipment = booked(book(ALPHA, NOW, KEY, bookStandard));

        assertThat(shipment).isEqualTo(new Shipment(new ShipmentId(id(1)), ALPHA, quote.id(), standard,
                ServiceLevel.STANDARD, eur("18.81"), ShipmentStatus.BOOKED, new TrackingNumber("SIM0000000001"), OSLO,
                RECIPIENT, REFERENCE, KEY, RequestFingerprint.of(bookStandard), NOW));
        assertThat(store.all()).containsExactly(shipment);
        // The shipment took id(1); the event takes the next one.
        assertThat(published).containsExactly(new ShipmentBooked(id(2), ALPHA, shipment.id(), NOW));
    }

    // S2: the order of the rules, each pair of neighbours failing together, and their rejections.

    @Test
    void withoutABoundTenantNothingIsStoredOrPublished() {
        assertThatThrownBy(() -> bookShipment(NOW).handle(KEY, bookStandard))
                .isInstanceOf(MissingTenantContextException.class);

        assertNothingStoredOrPublished();
    }

    @Test
    void theKeyIsLookedUpBeforeTheOfferSoAReplayOutlivesTheQuotesExpiry() {
        Shipment shipment = booked(book(ALPHA, NOW, KEY, bookStandard));

        assertThat(book(ALPHA, EXPIRES.plus(Duration.ofDays(1)), KEY, bookStandard))
                .isEqualTo(new BookingOutcome.Replayed(shipment));
        assertThat(book(ALPHA, NOW, KEY, command(UNKNOWN_QUOTE, standard)))
                .isEqualTo(rejected(BookingRejection.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void eachAnswerOfTheOfferLookupBesidesBookableIsItsRejection() {
        assertThat(book(ALPHA, NOW, KEY, command(UNKNOWN_QUOTE, standard)))
                .isEqualTo(rejected(BookingRejection.QUOTE_NOT_FOUND));
        // Another tenant's quote is not found either: Tenant Beta has none.
        assertThat(book(BETA, NOW, KEY, bookStandard)).isEqualTo(rejected(BookingRejection.QUOTE_NOT_FOUND));
        assertThat(book(ALPHA, NOW, KEY, command(quote.id(), UNKNOWN_OFFER)))
                .isEqualTo(rejected(BookingRejection.OFFER_NOT_FOUND));
        assertThat(book(ALPHA, EXPIRES, KEY, bookStandard)).isEqualTo(rejected(BookingRejection.QUOTE_EXPIRED));

        assertNothingStoredOrPublished();
    }

    @Test
    void theOfferIsCheckedBeforeTheRecipient() {
        Recipient elsewhere = recipientAt("NO", "0150");

        assertThat(book(ALPHA, NOW, KEY, new BookingCommand(UNKNOWN_QUOTE, standard, elsewhere,
                null))).isEqualTo(rejected(BookingRejection.QUOTE_NOT_FOUND));
        assertThat(book(ALPHA, NOW, KEY, new BookingCommand(quote.id(), UNKNOWN_OFFER, elsewhere,
                null))).isEqualTo(rejected(BookingRejection.OFFER_NOT_FOUND));
        assertThat(book(ALPHA, EXPIRES, KEY, new BookingCommand(quote.id(), standard, elsewhere, null)))
                .isEqualTo(rejected(BookingRejection.QUOTE_EXPIRED));
        assertNothingStoredOrPublished();
    }

    @Test
    void theRecipientIsCheckedBeforeTheQuotesEarlierBooking() {
        booked(book(ALPHA, NOW, KEY, bookStandard));

        assertThat(book(ALPHA, NOW, OTHER_KEY, new BookingCommand(quote.id(), standard, recipientAt("SE", "11123"),
                null))).isEqualTo(rejected(BookingRejection.RECIPIENT_DOES_NOT_MATCH_QUOTE));
    }

    @Test
    void aQuoteBookedUnderAnotherKeyIsNotBookedAgainNotEvenForAnotherOffer() {
        Shipment shipment = booked(book(ALPHA, NOW, KEY, bookStandard));

        assertThat(book(ALPHA, NOW, OTHER_KEY, bookStandard))
                .isEqualTo(rejected(BookingRejection.QUOTE_ALREADY_BOOKED));
        assertThat(book(ALPHA, NOW, OTHER_KEY, command(quote.id(), economy)))
                .isEqualTo(rejected(BookingRejection.QUOTE_ALREADY_BOOKED));
        // Past its expiry the quote is expired before it is booked: the offer is checked first.
        assertThat(book(ALPHA, EXPIRES, OTHER_KEY, bookStandard)).isEqualTo(rejected(BookingRejection.QUOTE_EXPIRED));
        assertThat(store.all()).containsExactly(shipment);
        assertThat(published).hasSize(1);
    }

    // S3: the expiry, decided by QuoteOffers at the clock's instant.

    @Test
    void anOfferIsBookableUntilTheLastInstantBeforeItsQuotesExpiry() {
        Shipment shipment = booked(book(ALPHA, EXPIRES.minusNanos(1), KEY, bookStandard));

        // The booking instant is kept to the millisecond, as the contract's timestamps are, and the event carries it.
        assertThat(shipment.bookedAt()).isEqualTo(Instant.parse("2026-11-10T09:29:59.999Z"));
        assertThat(published).containsExactly(new ShipmentBooked(id(2), ALPHA, shipment.id(), shipment.bookedAt()));
    }

    @Test
    void fromItsQuotesExpiryOnAnOfferIsExpired() {
        assertThat(book(ALPHA, EXPIRES, KEY, bookStandard)).isEqualTo(rejected(BookingRejection.QUOTE_EXPIRED));
        assertThat(book(ALPHA, EXPIRES.plusSeconds(1), KEY, bookStandard))
                .isEqualTo(rejected(BookingRejection.QUOTE_EXPIRED));

        assertNothingStoredOrPublished();
    }

    // S4: the recipient's country and postcode are exactly the quote's destination, SE 11122.

    @Test
    void theRecipientsCountryAndPostcodeMustBeExactlyTheQuotesDestination() {
        for (Recipient elsewhere : List.of(recipientAt("SE", "11123"), recipientAt("SE", "111 22"),
                recipientAt("NO", "11122"))) {
            assertThat(book(ALPHA, NOW, KEY, new BookingCommand(quote.id(), standard, elsewhere, null)))
                    .as(elsewhere.countryCode() + " " + elsewhere.postcode())
                    .isEqualTo(rejected(BookingRejection.RECIPIENT_DOES_NOT_MATCH_QUOTE));
        }
        assertNothingStoredOrPublished();

        // The name, the address lines and the city are the clerk's to choose.
        Recipient other = new Recipient("Synthetic Recipient 2", "2 Example Street", "Floor 3", "11122",
                "Exampleby North", "SE");
        assertThat(booked(book(ALPHA, NOW, KEY, new BookingCommand(quote.id(), standard, other, null))).recipient())
                .isEqualTo(other);
    }

    // Replay and key reuse, with the shipments stored in memory.

    @Test
    void theSameKeyAndBodyReplayTheShipmentAndStoreAndPublishNothingMore() {
        Shipment shipment = booked(book(ALPHA, NOW, KEY, bookStandard));

        assertThat(book(ALPHA, NOW.plusSeconds(60), KEY, bookStandard))
                .isEqualTo(new BookingOutcome.Replayed(shipment));
        assertThat(store.all()).containsExactly(shipment);
        assertThat(published).hasSize(1);
        assertThat(trackingNumbersTaken).hasValue(1);
    }

    @Test
    void theSameKeyWithAnotherBodyIsRefused() {
        Shipment shipment = booked(book(ALPHA, NOW, KEY, bookStandard));

        for (BookingCommand other : List.of(command(quote.id(), economy),
                new BookingCommand(quote.id(), standard, RECIPIENT, null),
                new BookingCommand(quote.id(), standard, RECIPIENT, new ShipmentReference("ORDER-1002")))) {
            assertThat(book(ALPHA, NOW, KEY, other)).isEqualTo(rejected(BookingRejection.IDEMPOTENCY_KEY_REUSED));
        }
        assertThat(store.all()).containsExactly(shipment);
        assertThat(published).hasSize(1);
    }

    @Test
    void keysAndQuotesAreEachTenantsOwn() {
        // Tenant Beta has a quote of its own, with the same IDs as Tenant Alpha's, and books it with the same key.
        quotes.insert(exampleQuote(BETA, ISSUED));
        Shipment alpha = booked(book(ALPHA, NOW, KEY, bookStandard));

        Shipment beta = booked(book(BETA, NOW, KEY, bookStandard));

        assertThat(beta.tenantId()).isEqualTo(BETA);
        assertThat(beta.id()).isNotEqualTo(alpha.id());
        assertThat(store.all()).containsExactly(alpha, beta);
        assertThat(published).hasSize(2);
    }

    @Test
    void aRejectionStoresNothingSoItsKeyIsEvaluatedAgain() {
        BookingCommand misaddressed = new BookingCommand(quote.id(), standard, recipientAt("SE", "11123"), REFERENCE);
        assertThat(book(ALPHA, NOW, KEY, misaddressed))
                .isEqualTo(rejected(BookingRejection.RECIPIENT_DOES_NOT_MATCH_QUOTE));
        assertNothingStoredOrPublished();

        assertThat(book(ALPHA, NOW, KEY, bookStandard)).isInstanceOf(BookingOutcome.Booked.class);
    }

    /** Books at an instant with the tenant bound, as the planned HTTP filter is to bind it for a request. */
    private BookingOutcome book(TenantId tenant, Instant at, IdempotencyKey key, BookingCommand command) {
        BookShipment bookShipment = bookShipment(at);
        return tenants.callAs(tenant, () -> bookShipment.handle(key, command));
    }

    private BookShipment bookShipment(Instant at) {
        return new BookShipment(Clock.fixed(at, ZoneOffset.UTC), ids, tenants, new QuoteOffersService(quotes),
                published::add, store, trackingNumbers);
    }

    private void assertNothingStoredOrPublished() {
        assertThat(store.all()).isEmpty();
        assertThat(published).isEmpty();
        assertThat(trackingNumbersTaken).hasValue(0);
    }

    private static BookingOutcome rejected(BookingRejection reason) {
        return new BookingOutcome.Rejected(reason);
    }

    /** The shipment of a booked outcome; any other outcome fails the test. */
    private static Shipment booked(BookingOutcome outcome) {
        return switch (outcome) {
            case BookingOutcome.Booked(Shipment shipment) -> shipment;
            case BookingOutcome.Replayed replayed -> throw new AssertionError("not booked: " + replayed);
            case BookingOutcome.Rejected rejected -> throw new AssertionError("not booked: " + rejected);
        };
    }
}
