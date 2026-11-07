package io.github.stigjohnny.shipping.shipments.api;

import java.util.Objects;
import java.util.UUID;

/** A shipment's identity. */
public record ShipmentId(UUID value) {

    public ShipmentId {
        Objects.requireNonNull(value, "value");
    }
}
