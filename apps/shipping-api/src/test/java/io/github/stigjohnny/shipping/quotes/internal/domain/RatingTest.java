package io.github.stigjohnny.shipping.quotes.internal.domain;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.eur;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.id;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.offered;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.request;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.sequentialIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Rating a request (invariants Q3 and Q6): one offer per available level in ServiceLevel order, exactly the requested
 * level when there is one, prices and transit days from the tariff, identifiers from the generator, and the reason
 * of the first failing rule when nothing can be offered.
 */
class RatingTest {

    private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

    private final Rating rating = new Rating(new SyntheticTariff(), sequentialIds());

    @Test
    void theContractsExampleGetsThreeOffersInServiceLevelOrder() {
        Quote quote = offered(rating.quote(ALPHA, EXAMPLE, NOW));

        assertThat(quote.offers()).extracting(Offer::level, Offer::price, Offer::transitDays).containsExactly(
                tuple(ServiceLevel.ECONOMY, eur("15.05"), new TransitDays(5, 8)),
                tuple(ServiceLevel.STANDARD, eur("18.81"), new TransitDays(3, 5)),
                tuple(ServiceLevel.EXPRESS, eur("28.22"), new TransitDays(1, 2)));
        assertThat(quote.tenantId()).isEqualTo(ALPHA);
        assertThat(quote.request()).isEqualTo(EXAMPLE);
        assertThat(quote.zone()).isEqualTo(Zone.INTERNATIONAL);
        assertThat(quote.billableWeightGrams()).isEqualTo(1_200);
    }

    @Test
    void theQuoteTakesTheFirstIdentifierAndItsOffersTheNextOnesInLevelOrder() {
        Quote quote = offered(rating.quote(ALPHA, EXAMPLE, NOW));

        assertThat(quote.id()).isEqualTo(new QuoteId(id(1)));
        assertThat(quote.offers()).extracting(Offer::id)
                .containsExactly(new OfferId(id(2)), new OfferId(id(3)), new OfferId(id(4)));
    }

    @Test
    void aRequestedLevelYieldsExactlyThatOffer() {
        Quote quote = offered(rating.quote(ALPHA, request(EXAMPLE.parcel(), ServiceLevel.STANDARD), NOW));

        assertThat(quote.offers()).extracting(Offer::level, Offer::price)
                .containsExactly(tuple(ServiceLevel.STANDARD, eur("18.81")));
    }

    @Test
    void aDomesticParcelIsPricedDomestic() {
        QuoteRequest domestic = new QuoteRequest(new Parcel(800, 200, 150, 100), new Place("SE", "11122"),
                new Place("SE", "41103"), null);

        Quote quote = offered(rating.quote(ALPHA, domestic, NOW));

        assertThat(quote.zone()).isEqualTo(Zone.DOMESTIC);
        assertThat(quote.offers()).extracting(Offer::price).containsExactly(eur("5.93"), eur("7.42"), eur("11.13"));
    }

    @Test
    void above10000BillableGramsOnlyEconomyAndStandardAreOffered() {
        Quote quote = offered(rating.quote(ALPHA, request(new Parcel(10_001, 10, 10, 10), null), NOW));

        assertThat(quote.offers()).extracting(Offer::level, Offer::price).containsExactly(
                tuple(ServiceLevel.ECONOMY, eur("38.70")), tuple(ServiceLevel.STANDARD, eur("48.38")));
    }

    @Test
    void whenNothingCanBeOfferedTheResultNamesTheFirstFailingRule() {
        Parcel heaviest = new Parcel(1, 1_200, 1_200, 1_200);
        QuoteRequest unserved = new QuoteRequest(heaviest, new Place("GB", "0150"), new Place("SE", "11122"),
                ServiceLevel.EXPRESS);

        assertThat(rating.quote(ALPHA, unserved, NOW))
                .isEqualTo(new QuoteResult.Unavailable(UnavailableReason.ROUTE_NOT_SERVED));
        assertThat(rating.quote(ALPHA, request(heaviest, ServiceLevel.EXPRESS), NOW))
                .isEqualTo(new QuoteResult.Unavailable(UnavailableReason.BILLABLE_WEIGHT_ABOVE_LIMIT));
        assertThat(rating.quote(ALPHA, request(new Parcel(10_001, 10, 10, 10), ServiceLevel.EXPRESS), NOW))
                .isEqualTo(new QuoteResult.Unavailable(UnavailableReason.SERVICE_LEVEL_NOT_AVAILABLE));
    }
}
