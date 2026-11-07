package io.github.stigjohnny.shipping.shipments.api;

import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published when a shipment is booked, not on a replay or a rejection (ADR 0006): identifiers and the instant only. It
 * holds no recipient data and no quotes type, so a consumer gains no dependency on quotes. Changing it is a level 3
 * change, and an additive one only.
 */
public record ShipmentBooked(UUID eventId, TenantId tenantId, ShipmentId shipmentId, Instant occurredAt) {

    public ShipmentBooked {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
