package io.github.stigjohnny.shipping.shipments.internal.adapter.out.jdbc;

import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.shipments.internal.application.ShipmentStore;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.Recipient;
import io.github.stigjohnny.shipping.shipments.internal.domain.RequestFingerprint;
import io.github.stigjohnny.shipping.shipments.internal.domain.Shipment;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentCursor;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentReference;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentStatus;
import io.github.stigjohnny.shipping.shipments.internal.domain.TrackingNumber;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped storage. Booking holds key then quote locks until its enclosing transaction commits. */
@Repository
@Profile("postgres")
@Transactional
public class JdbcShipmentStore implements ShipmentStore {
    private final JdbcClient jdbc;
    public JdbcShipmentStore(JdbcClient jdbc) { this.jdbc = Objects.requireNonNull(jdbc, "jdbc"); }

    @Override public void insert(Shipment shipment) {
        var recipient = shipment.recipient();
        var reference = shipment.reference();
        sql(shipment.tenantId(), """
                insert into shipments.shipment (tenant_id, shipment_id, quote_id, offer_id, status, service_level,
                    amount, currency, tracking_number, origin_country, origin_postcode, recipient_name,
                    recipient_address_line1, recipient_address_line2, recipient_postcode, recipient_city,
                    recipient_country, reference, idempotency_key, request_fingerprint, booked_at)
                values (:tenant, :id, :quote, :offer, :status, :level, :amount, :currency, :tracking, :originCountry,
                    :originPostcode, :name, :line1, :line2, :postcode, :city, :country, :reference, :key, :fingerprint, :at)
                """)
                .param("id", shipment.id().value())
                .param("quote", shipment.quoteId().value())
                .param("offer", shipment.offerId().value())
                .param("status", shipment.status().name())
                .param("level", shipment.serviceLevel().name())
                .param("amount", shipment.price().amount())
                .param("currency", shipment.price().currency().getCurrencyCode())
                .param("tracking", shipment.trackingNumber().value())
                .param("originCountry", shipment.origin().countryCode())
                .param("originPostcode", shipment.origin().postcode())
                .param("name", recipient.name())
                .param("line1", recipient.addressLine1())
                .param("line2", recipient.addressLine2(), Types.VARCHAR)
                .param("postcode", recipient.postcode())
                .param("city", recipient.city())
                .param("country", recipient.countryCode())
                .param("reference", reference == null ? null : reference.value(), Types.VARCHAR)
                .param("key", shipment.idempotencyKey().value())
                .param("fingerprint", shipment.requestFingerprint().value())
                .param("at", Timestamp.from(shipment.bookedAt()))
                .update();
    }

    @Override public Optional<Shipment> findByKey(TenantId tenant, IdempotencyKey key) {
        // Separate lock statement: the subsequent SELECT obtains a fresh READ COMMITTED snapshot after waiting.
        lock(tenant, 1, key.value());
        return sql(tenant, "select * from shipments.shipment where tenant_id = :tenant and idempotency_key = :key")
                .param("key", key.value())
                .query(JdbcShipmentStore::map)
                .optional();
    }

    @Override public Optional<Shipment> findByQuote(TenantId tenant, QuoteId quote) {
        lock(tenant, 2, quote.value().toString());
        return sql(tenant, "select * from shipments.shipment where tenant_id = :tenant and quote_id = :quote")
                .param("quote", quote.value())
                .query(JdbcShipmentStore::map)
                .optional();
    }

    @Override public Optional<Shipment> findById(TenantId tenant, ShipmentId id) {
        return sql(tenant, "select * from shipments.shipment where tenant_id = :tenant and shipment_id = :id")
                .param("id", id.value())
                .query(JdbcShipmentStore::map)
                .optional();
    }

    @Override public List<Shipment> page(TenantId tenant, int limit, @Nullable ShipmentCursor after,
            @Nullable String status) {
        return sql(tenant, """
                select * from shipments.shipment where tenant_id = :tenant
                    and (cast(:status as text) is null or status = :status)
                    and (cast(:at as timestamptz) is null or (booked_at, shipment_id) < (:at, :id))
                order by booked_at desc, shipment_id desc limit :limit
                """)
                .param("status", status, Types.VARCHAR)
                .param("at", after == null ? null : Timestamp.from(after.bookedAt()), Types.TIMESTAMP)
                .param("id", after == null ? null : after.shipmentId().value(), Types.OTHER)
                .param("limit", limit)
                .query(JdbcShipmentStore::map)
                .list();
    }

    private void lock(TenantId tenant, int namespace, String value) {
        sql(tenant, "select pg_advisory_xact_lock(:namespace, hashtext(cast(:tenant as text) || ':' || :value))")
                .param("namespace", namespace)
                .param("value", value)
                .query((_, _) -> true)
                .single();
    }

    private JdbcClient.StatementSpec sql(TenantId tenant, String statement) {
        jdbc.sql("select set_config('app.tenant_id', :tenant, true)")
                .param("tenant", tenant.value().toString())
                .query(String.class)
                .single();
        return jdbc.sql(statement)
                .param("tenant", tenant.value());
    }

    private static Shipment map(ResultSet row, int ignored) throws SQLException {
        String reference = row.getString("reference");
        return new Shipment(new ShipmentId(UUID.fromString(row.getString("shipment_id"))),
                new TenantId(UUID.fromString(row.getString("tenant_id"))), new QuoteId(UUID.fromString(row.getString("quote_id"))),
                new OfferId(UUID.fromString(row.getString("offer_id"))), ServiceLevel.valueOf(row.getString("service_level")),
                new Money(row.getBigDecimal("amount"), Currency.getInstance(row.getString("currency"))),
                ShipmentStatus.valueOf(row.getString("status")), new TrackingNumber(row.getString("tracking_number")),
                new Place(row.getString("origin_country"), row.getString("origin_postcode")),
                new Recipient(row.getString("recipient_name"), row.getString("recipient_address_line1"),
                        row.getString("recipient_address_line2"), row.getString("recipient_postcode"),
                        row.getString("recipient_city"), row.getString("recipient_country")),
                reference == null ? null : new ShipmentReference(reference), new IdempotencyKey(row.getString("idempotency_key")),
                new RequestFingerprint(row.getString("request_fingerprint")), row.getTimestamp("booked_at").toInstant());
    }
}
