package io.github.stigjohnny.shipping.quotes.internal.application;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.OfferLookup;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Offers expire 30 minutes after issue, decided by the instant the caller passes, which comes from the injected clock
 * (invariant Q7): a quote is OPEN until its expiry, exclusive, and EXPIRED from it, and findOffer says Expired from it.
 */
class QuoteExpiryTest {

    /** The issue instant of the synthetic expired-quote fixture in docs/modules/quotes.md. */
    private static final Instant ISSUED = Instant.parse("2026-01-05T09:00:00.000Z");
    private static final Instant EXPIRES = Instant.parse("2026-01-05T09:30:00.000Z");

    @Test
    void aQuoteExpiresThirtyMinutesAfterItsIssueInstantTruncatedToMilliseconds() {
        Quote quote = exampleQuote(ALPHA, Instant.parse("2026-01-05T09:00:00.000999999Z"));

        assertThat(quote.issuedAt()).isEqualTo(ISSUED);
        assertThat(quote.expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    void aQuoteIsOpenUntilItsExpiryAndExpiredFromIt() {
        Quote quote = exampleQuote(ALPHA, ISSUED);

        assertThat(quote.status(ISSUED)).isEqualTo(QuoteStatus.OPEN);
        assertThat(quote.status(EXPIRES.minusNanos(1))).isEqualTo(QuoteStatus.OPEN);
        assertThat(quote.status(EXPIRES)).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(quote.status(EXPIRES.plus(Duration.ofDays(1)))).isEqualTo(QuoteStatus.EXPIRED);
    }

    @Test
    void anOfferIsBookableUntilTheExpiryAndExpiredFromIt() {
        Quote quote = exampleQuote(ALPHA, ISSUED);
        InMemoryQuoteStore store = new InMemoryQuoteStore();
        store.insert(quote);
        QuoteOffersService offers = new QuoteOffersService(store);
        OfferId offer = quote.offers().getFirst().id();

        assertThat(offers.findOffer(ALPHA, quote.id(), offer, EXPIRES.minusNanos(1)))
                .isInstanceOf(OfferLookup.Bookable.class);
        assertThat(offers.findOffer(ALPHA, quote.id(), offer, EXPIRES)).isEqualTo(new OfferLookup.Expired(EXPIRES));
        assertThat(offers.findOffer(ALPHA, quote.id(), offer, EXPIRES.plusSeconds(1)))
                .isEqualTo(new OfferLookup.Expired(EXPIRES));
    }

    @Test
    void aQuoteWhoseExpiryIsNotThirtyMinutesAfterItsIssueIsRejected() {
        Quote quote = exampleQuote(ALPHA, ISSUED);

        assertThatThrownBy(() -> new Quote(quote.id(), quote.tenantId(), quote.request(), quote.zone(),
                quote.billableWeightGrams(), quote.issuedAt(), quote.expiresAt().plusMillis(1), quote.offers()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quote expires 30 minutes after its issue: issued 2026-01-05T09:00:00Z, so expires "
                        + "2026-01-05T09:30:00Z, not 2026-01-05T09:30:00.001Z.");
        assertThatThrownBy(() -> new Quote(quote.id(), quote.tenantId(), quote.request(), quote.zone(),
                quote.billableWeightGrams(), quote.issuedAt().plusNanos(1), quote.expiresAt().plusNanos(1),
                quote.offers()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quote's issue instant has millisecond precision, not 2026-01-05T09:00:00.000000001Z.");
    }
}
