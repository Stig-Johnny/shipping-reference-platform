package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.shipments.internal.domain.TrackingNumber;

/**
 * Where tracking numbers come from. Under the postgres profile, JdbcTrackingNumbers takes the next value of the
 * database sequence shipments.tracking_number_seq, so that they are unique across tenants
 * (docs/modules/shipments.md, "Tracking numbers"); tests without a database count in memory.
 */
@FunctionalInterface
public interface TrackingNumbers {

    /** A tracking number that no shipment has had. */
    TrackingNumber next();
}
