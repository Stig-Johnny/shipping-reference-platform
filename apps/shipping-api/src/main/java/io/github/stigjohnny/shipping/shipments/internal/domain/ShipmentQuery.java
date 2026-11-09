package io.github.stigjohnny.shipping.shipments.internal.domain;

import org.jspecify.annotations.Nullable;

/** Contract bounds for a page; CANCELLED is a supported filter even before cancellation is implemented. */
public record ShipmentQuery(int limit, @Nullable String cursor, @Nullable String status) {
    public ShipmentQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (status != null && !status.equals("BOOKED") && !status.equals("CANCELLED")) {
            throw new IllegalArgumentException("status must be BOOKED or CANCELLED");
        }
    }
}
