package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A tenant's page in descending booking instant and ID order; no cursor on the last page. */
public record ShipmentSlice(List<Shipment> items, @Nullable String nextCursor) {
    public ShipmentSlice {
        items = List.copyOf(items);
    }
}
