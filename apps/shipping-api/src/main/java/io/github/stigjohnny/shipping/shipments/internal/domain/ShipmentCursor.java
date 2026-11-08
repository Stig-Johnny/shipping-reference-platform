package io.github.stigjohnny.shipping.shipments.internal.domain;

import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A keyset position, bound to a tenant and filter; base64url of tenant|bookedAt|shipmentId|statusFilter. */
public record ShipmentCursor(TenantId tenant, Instant bookedAt, ShipmentId shipmentId, String statusFilter) {
    public ShipmentCursor {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(bookedAt, "bookedAt");
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(statusFilter, "statusFilter");
        if (!bookedAt.equals(bookedAt.truncatedTo(ChronoUnit.MILLIS))
                || (!statusFilter.isEmpty() && !statusFilter.equals("BOOKED") && !statusFilter.equals("CANCELLED"))) {
            throw new IllegalArgumentException("invalid cursor position");
        }
    }

    public String encode() {
        String text = tenant.value() + "|" + bookedAt + "|" + shipmentId.value() + "|" + statusFilter;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Strict decoding, including canonical UUIDs, timestamp and base64; no client value appears in an error. */
    public static Optional<ShipmentCursor> decode(String cursor, TenantId tenant, @Nullable String status) {
        if (!cursor.matches("[A-Za-z0-9_-]{1,200}")) {
            return Optional.empty();
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 4) {
                return Optional.empty();
            }
            ShipmentCursor position = new ShipmentCursor(new TenantId(UUID.fromString(parts[0])),
                    Instant.parse(parts[1]), new ShipmentId(UUID.fromString(parts[2])), parts[3]);
            return position.tenant().equals(tenant) && position.statusFilter().equals(status == null ? "" : status)
                    && position.encode().equals(cursor) ? Optional.of(position) : Optional.empty();
        } catch (IllegalArgumentException | DateTimeException invalid) {
            return Optional.empty();
        }
    }
}
