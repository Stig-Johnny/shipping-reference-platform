package io.github.stigjohnny.shipping.quotes.internal.domain;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.EXAMPLE;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The synthetic tariff of docs/modules/quotes.md: every golden price, the band boundaries, the zones, the served
 * countries, the transit days and the order of the availability rules (invariants Q1 and Q3).
 */
class SyntheticTariffTest {

    private static final SyntheticTariff TARIFF = new SyntheticTariff();

    @ParameterizedTest(name = "{0} {1} {2}: {3} EUR")
    @CsvSource(textBlock = """
            BAND_1, DOMESTIC,      ECONOMY,  5.93
            BAND_1, DOMESTIC,      STANDARD, 7.42
            BAND_1, DOMESTIC,      EXPRESS,  11.13
            BAND_1, INTERNATIONAL, ECONOMY,  12.04
            BAND_1, INTERNATIONAL, STANDARD, 15.05
            BAND_1, INTERNATIONAL, EXPRESS,  22.58
            BAND_2, DOMESTIC,      ECONOMY,  7.31
            BAND_2, DOMESTIC,      STANDARD, 9.14
            BAND_2, DOMESTIC,      EXPRESS,  13.71
            BAND_2, INTERNATIONAL, ECONOMY,  15.05
            BAND_2, INTERNATIONAL, STANDARD, 18.81
            BAND_2, INTERNATIONAL, EXPRESS,  28.22
            BAND_3, DOMESTIC,      ECONOMY,  10.23
            BAND_3, DOMESTIC,      STANDARD, 12.79
            BAND_3, DOMESTIC,      EXPRESS,  19.19
            BAND_3, INTERNATIONAL, ECONOMY,  20.64
            BAND_3, INTERNATIONAL, STANDARD, 25.80
            BAND_3, INTERNATIONAL, EXPRESS,  38.70
            BAND_4, DOMESTIC,      ECONOMY,  13.33
            BAND_4, DOMESTIC,      STANDARD, 16.66
            BAND_4, DOMESTIC,      EXPRESS,  24.99
            BAND_4, INTERNATIONAL, ECONOMY,  27.52
            BAND_4, INTERNATIONAL, STANDARD, 34.40
            BAND_4, INTERNATIONAL, EXPRESS,  51.60
            BAND_5, DOMESTIC,      ECONOMY,  18.06
            BAND_5, DOMESTIC,      STANDARD, 22.58
            BAND_5, INTERNATIONAL, ECONOMY,  38.70
            BAND_5, INTERNATIONAL, STANDARD, 48.38
            BAND_6, DOMESTIC,      ECONOMY,  23.65
            BAND_6, DOMESTIC,      STANDARD, 29.56
            BAND_6, INTERNATIONAL, ECONOMY,  49.88
            BAND_6, INTERNATIONAL, STANDARD, 62.35
            """)
    void everyOfferedPriceEqualsTheGoldenTable(WeightBand band, Zone zone, ServiceLevel level, String euros) {
        assertThat(TARIFF.available(level, band.upToGrams())).isTrue();
        assertThat(TARIFF.price(zone, band, level)).isEqualTo(eur(euros));
    }

    @ParameterizedTest
    @CsvSource({"1, true", "10000, true", "10001, false", "20000, false", "20001, false", "30000, false"})
    void expressIsOfferedUpTo10000BillableGramsTheOtherLevelsUpTo30000(int grams, boolean express) {
        assertThat(TARIFF.available(ServiceLevel.EXPRESS, grams)).isEqualTo(express);
        assertThat(TARIFF.available(ServiceLevel.ECONOMY, grams)).isTrue();
        assertThat(TARIFF.available(ServiceLevel.STANDARD, grams)).isTrue();
    }

    @Test
    void aboveTheLastBandNothingIsOfferedAndThereIsNoBand() {
        for (ServiceLevel level : ServiceLevel.values()) {
            assertThat(TARIFF.available(level, 30_001)).as(level.name()).isFalse();
        }
        assertThat(WeightBand.of(30_001)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "1, BAND_1", "1000, BAND_1", "1001, BAND_2", "2000, BAND_2", "2001, BAND_3", "5000, BAND_3", "5001, BAND_4",
        "10000, BAND_4", "10001, BAND_5", "20000, BAND_5", "20001, BAND_6", "30000, BAND_6"
    })
    void theBandIsTheFirstWhoseUpperBoundIsAtLeastTheBillableWeight(int grams, WeightBand band) {
        assertThat(WeightBand.of(grams)).contains(band);
    }

    @Test
    void theZoneIsDomesticWithinOneCountryAndInternationalBetweenTwo() {
        assertThat(TARIFF.zone(new Place("NO", "0150"), new Place("NO", "5003"))).isEqualTo(Zone.DOMESTIC);
        assertThat(TARIFF.zone(new Place("NO", "0150"), new Place("SE", "11122"))).isEqualTo(Zone.INTERNATIONAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE", "DK", "FI", "NL", "NO", "SE"})
    void sixCountriesAreServedBothWays(String country) {
        assertThat(TARIFF.unavailability(route(country, "NO", grams(1_200), null))).isEmpty();
        assertThat(TARIFF.unavailability(route("SE", country, grams(1_200), null))).isEmpty();
    }

    @Test
    void theFirstFailingRuleDecidesWhyNothingIsOffered() {
        // A billable weight above 30000 g comes only from the volume: 1200 x 1200 x 1200 mm bill 345600 g.
        Parcel heaviest = new Parcel(1, 1_200, 1_200, 1_200);
        Parcel justAbove = new Parcel(30_000, 1_200, 500, 251);
        assertThat(justAbove.billableWeightGrams()).isEqualTo(30_120);

        // An unserved country, the heaviest parcel and EXPRESS requested: the route decides.
        assertThat(TARIFF.unavailability(route("GB", "NO", heaviest, ServiceLevel.EXPRESS)))
                .contains(UnavailableReason.ROUTE_NOT_SERVED);
        assertThat(TARIFF.unavailability(route("NO", "US", grams(1_200), null)))
                .contains(UnavailableReason.ROUTE_NOT_SERVED);
        // A served route: then the billable weight decides, before the requested level.
        assertThat(TARIFF.unavailability(route("NO", "SE", heaviest, ServiceLevel.EXPRESS)))
                .contains(UnavailableReason.BILLABLE_WEIGHT_ABOVE_LIMIT);
        assertThat(TARIFF.unavailability(route("NO", "SE", justAbove, null)))
                .contains(UnavailableReason.BILLABLE_WEIGHT_ABOVE_LIMIT);
        assertThat(TARIFF.unavailability(route("NO", "SE", grams(10_001), ServiceLevel.EXPRESS)))
                .contains(UnavailableReason.SERVICE_LEVEL_NOT_AVAILABLE);
        assertThat(TARIFF.unavailability(route("NO", "SE", grams(10_000), ServiceLevel.EXPRESS))).isEmpty();
        assertThat(TARIFF.unavailability(route("NO", "SE", grams(30_000), ServiceLevel.STANDARD))).isEmpty();
        assertThat(TARIFF.unavailability(route("NO", "SE", grams(30_000), null))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "DOMESTIC, ECONOMY, 3, 5", "DOMESTIC, STANDARD, 1, 3", "DOMESTIC, EXPRESS, 1, 1",
        "INTERNATIONAL, ECONOMY, 5, 8", "INTERNATIONAL, STANDARD, 3, 5", "INTERNATIONAL, EXPRESS, 1, 2"
    })
    void transitDaysAreTheSimulatedRanges(Zone zone, ServiceLevel level, int minDays, int maxDays) {
        assertThat(TARIFF.transitDays(zone, level)).isEqualTo(new TransitDays(minDays, maxDays));
    }

    @Test
    void theContractsWorkedExampleIsBandTwoInternationalAt1881ForStandard() {
        int billable = EXAMPLE.parcel().billableWeightGrams();

        assertThat(billable).isEqualTo(1_200);
        assertThat(WeightBand.of(billable)).contains(WeightBand.BAND_2);
        assertThat(TARIFF.zone(EXAMPLE.origin(), EXAMPLE.destination())).isEqualTo(Zone.INTERNATIONAL);
        // 17.50 x 1.00 x 1.075 = 18.8125, rounded half-up to 18.81.
        assertThat(TARIFF.price(Zone.INTERNATIONAL, WeightBand.BAND_2, ServiceLevel.STANDARD)).isEqualTo(eur("18.81"));
    }

    /** A parcel of the given weight, small enough that its weight is what it is priced by. */
    private static Parcel grams(int weightGrams) {
        return new Parcel(weightGrams, 10, 10, 10);
    }

    private static QuoteRequest route(String from, String to, Parcel parcel, @Nullable ServiceLevel level) {
        return new QuoteRequest(parcel, new Place(from, "0150"), new Place(to, "11122"), level);
    }
}
