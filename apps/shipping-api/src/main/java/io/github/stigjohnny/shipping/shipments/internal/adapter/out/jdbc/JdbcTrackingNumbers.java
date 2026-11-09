package io.github.stigjohnny.shipping.shipments.internal.adapter.out.jdbc;

import io.github.stigjohnny.shipping.shipments.internal.application.TrackingNumbers;
import io.github.stigjohnny.shipping.shipments.internal.domain.TrackingNumber;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** A global, bounded sequence; rolled-back bookings may leave gaps. */
@Repository
@Profile("postgres")
@Transactional
public class JdbcTrackingNumbers implements TrackingNumbers {
    private final JdbcClient jdbc;
    private final CurrentTenant currentTenant;
    public JdbcTrackingNumbers(JdbcClient jdbc, CurrentTenant currentTenant) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.currentTenant = Objects.requireNonNull(currentTenant, "currentTenant");
    }
    @Override public TrackingNumber next() {
        jdbc.sql("select set_config('app.tenant_id', :tenant, true)")
                .param("tenant", currentTenant.require().value().toString())
                .query(String.class)
                .single();
        long next = jdbc.sql("select nextval('shipments.tracking_number_seq')")
                .query(Long.class)
                .single();
        return format(next);
    }

    static TrackingNumber format(long next) {
        return new TrackingNumber(String.format(Locale.ROOT, "SIM%010d", next));
    }
}
