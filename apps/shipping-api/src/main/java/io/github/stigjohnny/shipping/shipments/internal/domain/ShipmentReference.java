package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The webshop's own reference for a shipment, such as an order number, as the contract's ShipmentReference allows it:
 * 1 to 35 letters, digits, spaces, dots, underscores, slashes or hyphens.
 */
public record ShipmentReference(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9 ._/-]{1,35}$");

    public ShipmentReference {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("A reference is 1 to 35 letters, digits, spaces, dots, underscores, "
                    + "slashes or hyphens.");
        }
    }
}
