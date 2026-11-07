package io.github.stigjohnny.shipping.shipments.internal.domain;

/**
 * A shipment's status. Booking is built, so BOOKED is the only one yet: CANCELLED and the rule of which status may
 * follow which arrive with cancellation, which is planned (docs/modules/shipments.md, "Lifecycle").
 */
public enum ShipmentStatus {
    /** Booked, and not cancelled. */
    BOOKED
}
