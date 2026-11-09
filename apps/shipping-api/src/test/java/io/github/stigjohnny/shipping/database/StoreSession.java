package io.github.stigjohnny.shipping.database;

import io.github.stigjohnny.shipping.tenants.internal.application.MembershipDirectory;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;
import io.github.stigjohnny.shipping.shipments.internal.application.ShipmentStore;
import java.sql.SQLException;

/** One isolated store fixture; the JDBC fixture owns one transaction and runtime-role connection. */
interface StoreSession extends AutoCloseable {
    MembershipDirectory memberships();
    QuoteStore quotes();
    ShipmentStore shipments();
    @Override void close() throws SQLException;
}
