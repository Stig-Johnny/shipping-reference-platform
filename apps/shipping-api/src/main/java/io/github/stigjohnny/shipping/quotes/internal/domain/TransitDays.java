package io.github.stigjohnny.shipping.quotes.internal.domain;

/** A simulated transit time: from {@code minDays} to {@code maxDays} days, both included. */
public record TransitDays(int minDays, int maxDays) {

    public TransitDays {
        if (minDays < 1 || maxDays < minDays) {
            throw new IllegalArgumentException("Transit days run from at least 1 to no fewer than the minimum, not "
                    + minDays + " to " + maxDays + ".");
        }
    }
}
