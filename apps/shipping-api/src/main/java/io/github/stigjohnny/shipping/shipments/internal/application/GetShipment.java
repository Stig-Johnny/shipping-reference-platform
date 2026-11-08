package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** Reads only the authenticated tenant's shipment. */
public class GetShipment {
    private final CurrentTenant currentTenant;
    private final ShipmentStore store;

    public GetShipment(CurrentTenant currentTenant, ShipmentStore store) {
        this.currentTenant = Objects.requireNonNull(currentTenant, "currentTenant");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Transactional(readOnly = true)
    public Optional<Shipment> handle(ShipmentId id) {
        return store.findById(currentTenant.require(), id);
    }
}
