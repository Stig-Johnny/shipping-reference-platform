package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.Objects;

/** A rendered label, or no shipment visible to the caller. Cancellation is not implemented yet. */
public sealed interface LabelOutcome {
    record Rendered(String svg, TrackingNumber trackingNumber) implements LabelOutcome {
        public Rendered {
            Objects.requireNonNull(svg, "svg");
            Objects.requireNonNull(trackingNumber, "trackingNumber");
        }
    }
    record NotFound() implements LabelOutcome {}
}
