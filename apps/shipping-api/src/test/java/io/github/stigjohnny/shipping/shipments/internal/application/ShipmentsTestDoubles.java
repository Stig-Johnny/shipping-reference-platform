package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.shipments.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures;
import io.github.stigjohnny.shipping.shipments.internal.domain.TrackingNumber;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** In-memory shipment persistence and deterministic identifiers; never database or concurrency evidence. */
@TestConfiguration(proxyBeanMethods = false)
public class ShipmentsTestDoubles {
    @Bean
    InMemoryShipmentStore shipmentStore() {
        return new InMemoryShipmentStore();
    }

    @Bean
    @Primary
    IdGenerator sequentialShipmentIds() {
        return ShipmentFixtures.sequentialIds();
    }

    @Bean
    TrackingNumbers trackingNumbers() {
        AtomicLong next = new AtomicLong();
        return () -> new TrackingNumber("SIM%010d".formatted(next.incrementAndGet()));
    }
}
