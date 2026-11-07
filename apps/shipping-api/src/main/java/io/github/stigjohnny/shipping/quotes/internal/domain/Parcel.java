package io.github.stigjohnny.shipping.quotes.internal.domain;

/**
 * A parcel within the contract's bounds, 1 to 30000 g and 1 to 1200 mm per side, and the weight it is priced by: the
 * greater of its weight and its volumetric weight.
 */
public record Parcel(int weightGrams, int lengthMm, int widthMm, int heightMm) {

    public static final int MAX_WEIGHT_GRAMS = 30_000;
    public static final int MAX_SIDE_MM = 1_200;
    private static final long CUBIC_MM_PER_VOLUMETRIC_GRAM = 5_000;

    public Parcel {
        within("weightGrams", weightGrams, MAX_WEIGHT_GRAMS);
        within("lengthMm", lengthMm, MAX_SIDE_MM);
        within("widthMm", widthMm, MAX_SIDE_MM);
        within("heightMm", heightMm, MAX_SIDE_MM);
    }

    /** ceil(length x width x height / 5000), computed in long: up to 345600 g for the largest parcel. */
    public int volumetricGrams() {
        return Math.toIntExact(Math.ceilDiv((long) lengthMm * widthMm * heightMm, CUBIC_MM_PER_VOLUMETRIC_GRAM));
    }

    /** max(weight, volumetric weight): what the tariff prices. */
    public int billableWeightGrams() {
        return Math.max(weightGrams, volumetricGrams());
    }

    private static void within(String name, int value, int max) {
        if (value < 1 || value > max) {
            throw new IllegalArgumentException(name + " must be 1 to " + max + ", not " + value + ".");
        }
    }
}
