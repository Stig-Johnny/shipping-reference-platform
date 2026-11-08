package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.Optional;
import java.util.List;
import org.jspecify.annotations.Nullable;
import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentCursor;

/**
 * The port through which shipments are stored and found, always by tenant: booking, reading and paging. The planned JDBC
 * adapter implements it on the table shipments.shipment, whose unique keys on (tenant, idempotency key) and (tenant,
 * quote) the two finds follow (docs/modules/shipments.md); the tests use an implementation in memory. Updating arrives with cancellation.
 */
public interface ShipmentStore {

    /** Stores a newly booked shipment with its key and fingerprint. */
    void insert(Shipment shipment);

    /** The tenant's shipment booked under this key; another tenant's is never found. */
    Optional<Shipment> findByKey(TenantId tenant, IdempotencyKey key);

    /** The tenant's shipment of this quote, of which there is at most one; another tenant's is never found. */
    Optional<Shipment> findByQuote(TenantId tenant, QuoteId quote);
    /** The tenant's shipment; unknown and foreign identifiers are both absent. */
    Optional<Shipment> findById(TenantId tenant, ShipmentId id);

    /**
     * At most limit rows, newest first (bookedAt DESC, UUID DESC, unsigned/lexical UUID order), strictly after the
     * cursor position, with this tenant and optional status. ListShipments requests one extra row for continuation.
     */
    List<Shipment> page(TenantId tenant, int limit, @Nullable ShipmentCursor after, @Nullable String status);
}
