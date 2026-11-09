package io.github.stigjohnny.shipping.database;

import io.github.stigjohnny.shipping.tenants.internal.adapter.out.jdbc.JdbcMembershipDirectory;
import io.github.stigjohnny.shipping.quotes.internal.adapter.out.jdbc.JdbcQuoteStore;
import io.github.stigjohnny.shipping.shipments.internal.adapter.out.jdbc.JdbcShipmentStore;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** A real shipping_app transaction; suppress-close lets JdbcClient release its handle without closing the session. */
final class JdbcStoreSession implements StoreSession {
    final Connection connection;
    final JdbcClient client;
    private final JdbcMembershipDirectory memberships;
    private final JdbcQuoteStore quotes;
    private final JdbcShipmentStore shipments;

    JdbcStoreSession(ShippingDatabase database) throws SQLException {
        connection = database.app();
        connection.setAutoCommit(false);
        client = JdbcClient.create(new SingleConnectionDataSource(connection, true));
        memberships = new JdbcMembershipDirectory(client);
        quotes = new JdbcQuoteStore(client);
        shipments = new JdbcShipmentStore(client);
    }
    @Override public JdbcMembershipDirectory memberships() { return memberships; }
    @Override public JdbcQuoteStore quotes() { return quotes; }
    @Override public JdbcShipmentStore shipments() { return shipments; }
    @Override public void close() throws SQLException {
        try { connection.rollback(); } finally { connection.close(); }
    }
}
