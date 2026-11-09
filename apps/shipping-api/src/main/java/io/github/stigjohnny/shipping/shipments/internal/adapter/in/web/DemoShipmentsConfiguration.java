package io.github.stigjohnny.shipping.shipments.internal.adapter.in.web;

import io.github.stigjohnny.shipping.shipments.internal.application.ShipmentStore;
import io.github.stigjohnny.shipping.shipments.internal.application.TrackingNumbers;
import io.github.stigjohnny.shipping.shipments.internal.domain.TrackingNumber;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Memory storage is the demo default; postgres supplies the existing JDBC adapter instead. */
@Configuration(proxyBeanMethods = false)
@Profile("demo & !postgres")
class DemoShipmentsConfiguration {
    @Bean
    @Fallback
    ShipmentStore demoShipmentStore() { return new DemoShipmentStore(); }
    @Bean
    @Fallback
    TrackingNumbers demoTrackingNumbers() {
        AtomicLong next = new AtomicLong();
        return () -> new TrackingNumber(String.format(Locale.ROOT, "SIM%010d", next.incrementAndGet()));
    }
}
