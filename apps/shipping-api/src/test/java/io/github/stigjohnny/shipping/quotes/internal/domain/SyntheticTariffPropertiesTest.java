package io.github.stigjohnny.shipping.quotes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Properties of the synthetic tariff (docs/modules/quotes.md, invariants Q2 and Q3) over billable weights: every
 * boundary of the tariff, one gram below, at and above it, and a fixed-seed random sample up to the heaviest billable
 * weight, 1200 x 1200 x 1200 mm / 5000 = 345600 g. The expected prices come from the specification's table, not from
 * the tariff under test.
 */
class SyntheticTariffPropertiesTest {

    private static final SyntheticTariff TARIFF = new SyntheticTariff();
    private static final long SEED = 20_261_107L;
    private static final int SAMPLE = 200;
    private static final int HEAVIEST = 345_600;
    /** The tariff's boundaries in billable grams: the lightest parcel and each band's upper bound, EXPRESS's included. */
    private static final int[] BOUNDARIES = {1, 1_000, 2_000, 5_000, 10_000, 20_000, 30_000};
    /** The specification's base prices in EUR: each band's upper bound in grams, DOMESTIC, INTERNATIONAL. */
    private static final List<List<String>> BASE = List.of(List.of("1000", "6.90", "14.00"),
            List.of("2000", "8.50", "17.50"), List.of("5000", "11.90", "24.00"), List.of("10000", "15.50", "32.00"),
            List.of("20000", "21.00", "45.00"), List.of("30000", "27.50", "58.00"));
    private static final Map<ServiceLevel, BigDecimal> LEVEL_FACTOR = Map.of(ServiceLevel.ECONOMY,
            new BigDecimal("0.80"), ServiceLevel.STANDARD, new BigDecimal("1.00"), ServiceLevel.EXPRESS,
            new BigDecimal("1.50"));
    private static final BigDecimal FUEL_SURCHARGE = new BigDecimal("1.075");
    private static final BigDecimal HALF_A_CENT = new BigDecimal("0.005");

    /** The boundary cases, then the sample; each is a billable weight in grams. */
    static IntStream billableWeights() {
        IntStream boundaries = IntStream.of(BOUNDARIES).flatMap(b -> IntStream.of(b - 1, b, b + 1)).filter(g -> g >= 1);
        RandomGenerator random = new SplittableRandom(SEED);
        return IntStream.concat(IntStream.concat(boundaries, IntStream.of(HEAVIEST - 1, HEAVIEST)),
                random.ints(SAMPLE, 1, HEAVIEST + 1));
    }

    @ParameterizedTest
    @MethodSource("billableWeights")
    void expressStopsAt10000GramsAndEveryLevelAt30000(int grams) {
        assertThat(TARIFF.available(ServiceLevel.EXPRESS, grams)).isEqualTo(grams <= 10_000);
        assertThat(TARIFF.available(ServiceLevel.STANDARD, grams)).isEqualTo(grams <= 30_000);
        assertThat(TARIFF.available(ServiceLevel.ECONOMY, grams)).isEqualTo(grams <= 30_000);
        assertThat(WeightBand.of(grams).isPresent()).isEqualTo(grams <= 30_000);
    }

    @ParameterizedTest
    @MethodSource("billableWeights")
    void thePriceNeverDecreasesAsTheBillableWeightGrows(int grams) {
        for (Zone zone : Zone.values()) {
            for (ServiceLevel level : ServiceLevel.values()) {
                if (TARIFF.available(level, grams) && TARIFF.available(level, grams + 1)) {
                    assertThat(price(zone, level, grams + 1).amount()).as("%s %s at %d g", zone, level, grams)
                            .isGreaterThanOrEqualTo(price(zone, level, grams).amount());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("billableWeights")
    void withinABandEconomyIsCheapestAndDomesticCheaperThanInternational(int grams) {
        if (WeightBand.of(grams).isEmpty()) {
            return;
        }
        for (Zone zone : Zone.values()) {
            assertThat(price(zone, ServiceLevel.ECONOMY, grams).amount()).as("%s at %d g", zone, grams)
                    .isLessThan(price(zone, ServiceLevel.STANDARD, grams).amount());
            if (TARIFF.available(ServiceLevel.EXPRESS, grams)) {
                assertThat(price(zone, ServiceLevel.STANDARD, grams).amount()).as("%s at %d g", zone, grams)
                        .isLessThan(price(zone, ServiceLevel.EXPRESS, grams).amount());
            }
        }
        for (ServiceLevel level : ServiceLevel.values()) {
            if (TARIFF.available(level, grams)) {
                assertThat(price(Zone.DOMESTIC, level, grams).amount()).as("%s at %d g", level, grams)
                        .isLessThan(price(Zone.INTERNATIONAL, level, grams).amount());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("billableWeights")
    void everyPriceIsTheExactProductRoundedHalfUpToCents(int grams) {
        for (Zone zone : Zone.values()) {
            for (ServiceLevel level : ServiceLevel.values()) {
                if (!TARIFF.available(level, grams)) {
                    continue;
                }
                BigDecimal exact = base(zone, grams).multiply(LEVEL_FACTOR.get(level)).multiply(FUEL_SURCHARGE);
                Money price = price(zone, level, grams);
                assertThat(price.amount()).as("%s %s at %d g", zone, level, grams)
                        .isEqualTo(exact.setScale(2, RoundingMode.HALF_UP));
                assertThat(price.amount().subtract(exact).abs()).isLessThanOrEqualTo(HALF_A_CENT);
                assertThat(price.currency().getCurrencyCode()).isEqualTo("EUR");
            }
        }
    }

    private static Money price(Zone zone, ServiceLevel level, int grams) {
        return TARIFF.price(zone, WeightBand.of(grams).orElseThrow(), level);
    }

    /** The specification's base price for the first band whose upper bound is at least the billable weight. */
    private static BigDecimal base(Zone zone, int grams) {
        for (List<String> band : BASE) {
            if (grams <= Integer.parseInt(band.get(0))) {
                return new BigDecimal(band.get(zone == Zone.DOMESTIC ? 1 : 2));
            }
        }
        throw new IllegalArgumentException(grams + " g is above the last band");
    }
}
