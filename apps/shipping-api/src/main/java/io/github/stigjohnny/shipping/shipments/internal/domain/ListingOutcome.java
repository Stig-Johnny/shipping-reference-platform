package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.Objects;

/** A page or a cursor that cannot be used with this tenant and filter. */
public sealed interface ListingOutcome {
    record Listed(ShipmentSlice page) implements ListingOutcome {
        public Listed {
            Objects.requireNonNull(page, "page");
        }
    }
    record InvalidCursor() implements ListingOutcome {}
}
