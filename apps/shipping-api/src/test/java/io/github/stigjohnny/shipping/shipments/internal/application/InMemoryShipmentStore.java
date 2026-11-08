package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The {@link ShipmentStore} port in memory, for tests: shipments by tenant, and an insert that would break one of the
 * planned table's keys fails as that key would: (tenant, shipment), (tenant, idempotency key), (tenant, quote) and
 * (tenant, tracking number).
 */
final class InMemoryShipmentStore implements ShipmentStore {

    private final List<Shipment> shipments = new ArrayList<>();

    @Override
    public void insert(Shipment shipment) {
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
    public Optional<Shipment> findByKey(TenantId tenant, IdempotencyKey key) {
        return find(tenant, shipment -> shipment.idempotencyKey().equals(key));
    }

    @Override
    public Optional<Shipment> findByQuote(TenantId tenant, QuoteId quote) {
        return find(tenant, shipment -> shipment.quoteId().equals(quote));
    }

    /** Every stored shipment, in insertion order. */
    List<Shipment> all() {
        return List.copyOf(shipments);
    }

    private Optional<Shipment> find(TenantId tenant, Predicate<Shipment> match) {
        return shipments.stream().filter(shipment -> shipment.tenantId().equals(tenant)).filter(match).findFirst();
    }
}
