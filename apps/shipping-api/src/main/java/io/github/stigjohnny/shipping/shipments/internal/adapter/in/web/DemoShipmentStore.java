package io.github.stigjohnny.shipping.shipments.internal.adapter.in.web;

import io.github.stigjohnny.shipping.shipments.internal.application.ShipmentStore;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.ArrayList;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;
import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentCursor;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Disposable, tenant-keyed storage for the attended memory demo; never database evidence. */
public final class DemoShipmentStore implements ShipmentStore {

    private final List<Shipment> shipments = new ArrayList<>();

    @Override
    public synchronized void insert(Shipment shipment) {
        boolean taken = shipments.stream().filter(stored -> stored.tenantId().equals(shipment.tenantId()))
                .anyMatch(stored -> stored.id().equals(shipment.id())
                        || stored.idempotencyKey().equals(shipment.idempotencyKey())
                        || stored.quoteId().equals(shipment.quoteId())
                        || stored.trackingNumber().equals(shipment.trackingNumber()));
        if (taken) {
            throw new IllegalStateException("a key of shipments.shipment is already taken for this tenant");
        }
        shipments.add(shipment);
    }

    @Override
    public synchronized Optional<Shipment> findByKey(TenantId tenant, IdempotencyKey key) {
        return find(tenant, shipment -> shipment.idempotencyKey().equals(key));
    }

    @Override
    public synchronized Optional<Shipment> findByQuote(TenantId tenant, QuoteId quote) {
        return find(tenant, shipment -> shipment.quoteId().equals(quote));
    }

    @Override
    public synchronized Optional<Shipment> findById(TenantId tenant, ShipmentId id) {
        return find(tenant, shipment -> shipment.id().equals(id));
    }

    @Override
    public synchronized List<Shipment> page(TenantId tenant, int limit, @Nullable ShipmentCursor after, @Nullable String status) {
        Comparator<Shipment> order = Comparator.comparing(Shipment::bookedAt)
                .thenComparing(shipment -> shipment.id().value().toString());
        return shipments.stream().filter(shipment -> shipment.tenantId().equals(tenant))
                .filter(shipment -> status == null || shipment.status().name().equals(status))
                .filter(shipment -> after == null || shipment.bookedAt().isBefore(after.bookedAt())
                        || (shipment.bookedAt().equals(after.bookedAt())
                            && shipment.id().value().toString().compareTo(after.shipmentId().value().toString()) < 0))
                .sorted(order.reversed()).limit(limit).toList();
    }

    /** Clear the HTTP fixture between requests of separate tests. */
    public synchronized void clear() {
        shipments.clear();
    }

    /** Every stored shipment, in insertion order. */
    public synchronized List<Shipment> all() {
        return List.copyOf(shipments);
    }

    private Optional<Shipment> find(TenantId tenant, Predicate<Shipment> match) {
        return shipments.stream().filter(shipment -> shipment.tenantId().equals(tenant)).filter(match).findFirst();
    }
}
