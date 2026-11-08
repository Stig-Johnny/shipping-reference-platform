package io.github.stigjohnny.shipping.shipments.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.shipments.internal.application.BookShipment;
import io.github.stigjohnny.shipping.shipments.internal.application.GetShipment;
import io.github.stigjohnny.shipping.shipments.internal.application.ListShipments;
import io.github.stigjohnny.shipping.shipments.internal.application.RenderLabel;
import io.github.stigjohnny.shipping.shipments.internal.application.ShipmentStore;
import io.github.stigjohnny.shipping.shipments.internal.application.TrackingNumbers;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.shipments.internal.domain.SvgLabelRenderer;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The module's HTTP entry point wires use cases to ports; tests supply the not-yet-built persistence adapters. */
@Configuration(proxyBeanMethods = false)
class ShipmentsConfiguration {
    @Bean
    IdGenerator shipmentIds() {
        return UUID::randomUUID;
    }

    @Bean
    BookShipment bookShipment(Clock clock, IdGenerator shipmentIds, CurrentTenant currentTenant, QuoteOffers offers,
            ApplicationEventPublisher events, ShipmentStore store, TrackingNumbers numbers) {
        return new BookShipment(clock, shipmentIds, currentTenant, offers, events, store, numbers);
    }

    @Bean
    GetShipment getShipment(CurrentTenant currentTenant, ShipmentStore store) {
        return new GetShipment(currentTenant, store);
    }

    @Bean
    ListShipments listShipments(CurrentTenant currentTenant, ShipmentStore store) {
        return new ListShipments(currentTenant, store);
    }

    @Bean
    RenderLabel renderLabel(GetShipment getShipment) {
        return new RenderLabel(getShipment, new SvgLabelRenderer());
    }
}
