package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A simulated tracking number: SIM and ten digits. The prefix marks it as no carrier's number. Where the digits come
 * from is the TrackingNumbers port's business (docs/modules/shipments.md, "Tracking numbers").
 */
public record TrackingNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("^SIM[0-9]{10}$");

    public TrackingNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("A tracking number is SIM and ten digits.");
        }
    }
}
