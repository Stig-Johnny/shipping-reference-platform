package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.Objects;

/** The expected outcomes of a booking request: a new shipment, the replay of an earlier one, or a refusal. */
public sealed interface BookingOutcome permits BookingOutcome.Booked, BookingOutcome.Replayed,
        BookingOutcome.Rejected {

    /** A new shipment, stored and published: the contract's 201. */
    record Booked(Shipment shipment) implements BookingOutcome {

        public Booked {
            Objects.requireNonNull(shipment, "shipment");
        }
    }

    /**
     * The shipment that an earlier request with the same key and body booked, as it is now; nothing is stored or
     * published again: the contract's 200 with Idempotency-Replayed.
     */
    record Replayed(Shipment shipment) implements BookingOutcome {

        public Replayed {
            Objects.requireNonNull(shipment, "shipment");
        }
    }

    /** Refused, for the first booking rule that failed; nothing is stored or published. */
    record Rejected(BookingRejection reason) implements BookingOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
