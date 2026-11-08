package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.shipments.internal.domain.ListingOutcome;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentCursor;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentQuery;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentSlice;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/** Decodes a tenant/filter-bound cursor, reads one extra row and emits the next position only when needed. */
public class ListShipments {
    private final CurrentTenant currentTenant;
    private final ShipmentStore store;

    public ListShipments(CurrentTenant currentTenant, ShipmentStore store) {
        this.currentTenant = Objects.requireNonNull(currentTenant, "currentTenant");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Transactional(readOnly = true)
    public ListingOutcome handle(ShipmentQuery query) {
        TenantId tenant = currentTenant.require();
        @Nullable ShipmentCursor after = null;
        if (query.cursor() != null) {
            Optional<ShipmentCursor> decoded = ShipmentCursor.decode(query.cursor(), tenant, query.status());
            if (decoded.isEmpty()) {
                return new ListingOutcome.InvalidCursor();
            }
            after = decoded.get();
        }
        List<Shipment> rows = store.page(tenant, query.limit() + 1, after, query.status());
        boolean more = rows.size() > query.limit();
        List<Shipment> items = more ? rows.subList(0, query.limit()) : rows;
        @Nullable String next = null;
        if (more) {
            Shipment last = items.getLast();
            next = new ShipmentCursor(tenant, last.bookedAt(), last.id(), query.status() == null ? "" : query.status())
                    .encode();
        }
        return new ListingOutcome.Listed(new ShipmentSlice(items, next));
    }
}
