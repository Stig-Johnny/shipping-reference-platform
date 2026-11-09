package io.github.stigjohnny.shipping.database;

import io.github.stigjohnny.shipping.tenants.internal.application.FixtureMembershipDirectory;
import io.github.stigjohnny.shipping.quotes.internal.application.InMemoryQuoteStore;
import io.github.stigjohnny.shipping.shipments.internal.application.InMemoryShipmentStore;

/** These exact inherited behaviours also run against PostgreSQL in the database contract classes. */
class InMemoryStoresTest implements MembershipDirectoryContract, QuoteStoreContract, ShipmentStoreContract {
    @Override public StoreSession session() {
        return new StoreSession() {
            private final FixtureMembershipDirectory memberships = new FixtureMembershipDirectory();
            private final InMemoryQuoteStore quotes = new InMemoryQuoteStore();
            private final InMemoryShipmentStore shipments = new InMemoryShipmentStore();
            @Override public FixtureMembershipDirectory memberships() { return memberships; }
            @Override public InMemoryQuoteStore quotes() { return quotes; }
            @Override public InMemoryShipmentStore shipments() { return shipments; }
            @Override public void close() { }
        };
    }
}
