package io.github.stigjohnny.shipping.quotes.internal.application;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.BETA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.OSLO;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.STOCKHOLM;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.eur;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.quotes.api.BookableOffer;
import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.OfferLookup;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * What shipments learns about an offer through QuoteOffers, checked in the contract's order: an unknown quote or one
 * of another tenant is not found, then an offer that is not in the quote, then the expiry. The quote is held in memory;
 * the same answers from the database are for QuotesModuleIntegrationTest, which is planned.
 */
class QuoteOffersServiceTest {

    private static final Instant ISSUED = Instant.parse("2026-01-05T09:00:00.000Z");
    private static final Instant BEFORE_EXPIRY = Instant.parse("2026-01-05T09:10:00Z");

    private final Quote quote = exampleQuote(ALPHA, ISSUED);
    private final InMemoryQuoteStore store = new InMemoryQuoteStore();
    private final QuoteOffersService offers = new QuoteOffersService(store);

    QuoteOffersServiceTest() {
        store.insert(quote);
    }

    @Test
    void aBookableOfferCarriesWhatShipmentsNeedsToBook() {
        // id(3) is the STANDARD offer: the quote took id(1), and ECONOMY id(2).
        OfferLookup lookup = offers.findOffer(ALPHA, quote.id(), new OfferId(id(3)), BEFORE_EXPIRY);

        assertThat(lookup).isEqualTo(new OfferLookup.Bookable(new BookableOffer(new QuoteId(id(1)),
                new OfferId(id(3)), ServiceLevel.STANDARD, eur("18.81"), OSLO, STOCKHOLM,
                Instant.parse("2026-01-05T09:30:00Z"))));
    }

    @Test
    void anotherTenantsQuoteIsNotFound() {
        assertThat(offers.findOffer(BETA, quote.id(), new OfferId(id(3)), BEFORE_EXPIRY))
                .isEqualTo(new OfferLookup.QuoteNotFound());
    }

    @Test
    void anUnknownQuoteIsNotFound() {
        assertThat(offers.findOffer(ALPHA, new QuoteId(id(99)), new OfferId(id(3)), BEFORE_EXPIRY))
                .isEqualTo(new OfferLookup.QuoteNotFound());
    }

    @Test
    void anOfferThatIsNotInTheQuoteIsNotFoundAlsoAfterTheExpiry() {
        assertThat(offers.findOffer(ALPHA, quote.id(), new OfferId(id(99)), BEFORE_EXPIRY))
                .isEqualTo(new OfferLookup.OfferNotFound());
        assertThat(offers.findOffer(ALPHA, quote.id(), new OfferId(id(99)), Instant.parse("2026-01-05T10:00:00Z")))
                .isEqualTo(new OfferLookup.OfferNotFound());
    }
}
