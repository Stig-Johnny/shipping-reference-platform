package io.github.stigjohnny.shipping.scenarios;

import io.github.stigjohnny.shipping.quotes.api.QuoteOffers;
import io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote;
import io.github.stigjohnny.shipping.quotes.internal.application.InMemoryQuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteOffersService;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures;
import io.github.stigjohnny.shipping.shipments.internal.application.BookShipment;
import io.github.stigjohnny.shipping.shipments.internal.application.InMemoryShipmentStore;
import io.github.stigjohnny.shipping.shipments.internal.application.InMemoryTrackingNumbers;
import io.github.stigjohnny.shipping.shipments.internal.application.ShipmentStore;
import io.github.stigjohnny.shipping.shipments.internal.application.TrackingNumbers;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import io.github.stigjohnny.shipping.tenants.internal.application.ThreadTenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * The built modules, tenants, quotes and shipments, wired in a Spring context for the in-process scenarios: their real
 * classes with a fixed clock, deterministic identifiers, the in-memory stores and tracking numbers counted in memory.
 * The HTTP tests use the application's wiring separately; these scenarios call the use cases directly. Each part is
 * a configuration of its own, so that a negative fixture can replace one part with an altered implementation.
 */
final class InProcessModules {

    /** The instant of the fixed clock. */
    static final Instant NOW = Instant.parse("2026-11-10T09:00:00.000Z");

    private InProcessModules() {
    }

    /** The clock, the identifiers and the tenants module's CurrentTenant. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Core {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        IdGenerator ids() {
            return QuoteFixtures.sequentialIds();
        }

        @Bean
        CurrentTenant currentTenant() {
            return new ThreadTenantContext();
        }
    }

    /** The quotes module's store: the in-memory test double, which is never database evidence. */
    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryStore {

        @Bean
        QuoteStore quoteStore() {
            return new InMemoryQuoteStore();
        }
    }

    /** The shipments module's store and tracking numbers, in memory; never database evidence. */
    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryShipments {

        @Bean
        ShipmentStore shipmentStore() {
            return new InMemoryShipmentStore();
        }

        @Bean
        TrackingNumbers trackingNumbers() {
            return new InMemoryTrackingNumbers();
        }

        @Bean
        io.github.stigjohnny.shipping.shipments.internal.domain.IdGenerator shipmentIds() {
            return ShipmentFixtures.sequentialIds();
        }
    }

    /** The shipments module's booking use case as main code defines it. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Shipments {

        @Bean
        BookShipment bookShipment(Clock clock, io.github.stigjohnny.shipping.shipments.internal.domain.IdGenerator ids,
                CurrentTenant currentTenant, QuoteOffers quoteOffers, ApplicationEventPublisher events,
                ShipmentStore store, TrackingNumbers trackingNumbers) {
            return new BookShipment(clock, ids, currentTenant, quoteOffers, events, store, trackingNumbers);
        }
    }

    /** The quotes module's use cases as main code defines them. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Quotes {

        @Bean
        CreateQuote createQuote(Clock clock, IdGenerator ids, CurrentTenant currentTenant,
                ApplicationEventPublisher events, QuoteStore store) {
            return new CreateQuote(clock, ids, currentTenant, events, store);
        }

        @Bean
        QuoteOffers quoteOffers(QuoteStore store) {
            return new QuoteOffersService(store);
        }
    }
}
