package io.github.stigjohnny.shipping.quotes.internal.domain;

import java.util.Optional;

/** The synthetic tariff's six weight bands, each up to and including its upper bound in billable grams. */
public enum WeightBand {
    BAND_1(1_000),
    BAND_2(2_000),
    BAND_3(5_000),
    BAND_4(10_000),
    BAND_5(20_000),
    BAND_6(30_000);

    private final int upToGrams;

    WeightBand(int upToGrams) {
        this.upToGrams = upToGrams;
    }

    /** The band's upper bound in billable grams, inclusive. */
    public int upToGrams() {
        return upToGrams;
    }

    /** The first band whose upper bound is at least the billable weight; none above 30000 g. */
    public static Optional<WeightBand> of(int billableWeightGrams) {
        if (billableWeightGrams < 1) {
            throw new IllegalArgumentException("A billable weight is at least 1 g, not " + billableWeightGrams + ".");
        }
        for (WeightBand band : values()) {
            if (billableWeightGrams <= band.upToGrams) {
                return Optional.of(band);
            }
        }
        return Optional.empty();
    }
}
