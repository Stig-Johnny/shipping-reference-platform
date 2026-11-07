package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.shipments.internal.domain.TrackingNumber;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link TrackingNumbers} counted in memory, for tests: SIM0000000001, SIM0000000002 and so on, where the planned JDBC
 * adapter takes the database sequence's next value.
 */
public final class InMemoryTrackingNumbers implements TrackingNumbers {

    private final AtomicLong taken = new AtomicLong();

    @Override
    public TrackingNumber next() {
        return new TrackingNumber("SIM%010d".formatted(taken.incrementAndGet()));
    }
}
