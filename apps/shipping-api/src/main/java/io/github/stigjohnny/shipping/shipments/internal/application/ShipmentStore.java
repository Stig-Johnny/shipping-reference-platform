package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.util.Optional;

/**
 * The port through which shipments are stored and found, always by tenant: only what booking needs. The planned JDBC
 * adapter implements it on the table shipments.shipment, whose unique keys on (tenant, idempotency key) and (tenant,
 * quote) the two finds follow (docs/modules/shipments.md); the tests use an implementation in memory. Updating, finding
 * by ID and paging arrive with cancellation, reading and listing.
 */
public interface ShipmentStore {

    /** Stores a newly booked shipment with its key and fingerprint. */
    void insert(Shipment shipment);

    /** The tenant's shipment booked under this key; another tenant's is never found. */
    Optional<Shipment> findByKey(TenantId tenant, IdempotencyKey key);

    /** The tenant's shipment of this quote, of which there is at most one; another tenant's is never found. */
    Optional<Shipment> findByQuote(TenantId tenant, QuoteId quote);
}
