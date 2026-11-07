package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.internal.application.CreateQuote;
import io.github.stigjohnny.shipping.quotes.internal.application.GetQuote;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteOffersService;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.domain.IdGenerator;
import io.github.stigjohnny.shipping.tenants.api.CurrentTenant;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The quotes module's beans: its use cases and the offer lookup, wired with the application's clock, the current
 * tenant, the event publisher and the store port, and the module's source of new IDs, random UUIDs. Domain and
 * application code get both the clock and the IDs injected and never create them, so the wiring lives in an adapter:
 * the web adapter is the module's entry point. The store's adapter is the planned JDBC adapter; tests supply one in
 * memory.
 */
@Configuration(proxyBeanMethods = false)
class QuotesConfiguration {

    @Bean
    IdGenerator quoteIds() {
        return UUID::randomUUID;
    }

    @Bean
    CreateQuote createQuote(Clock clock, IdGenerator quoteIds, CurrentTenant currentTenant,
            ApplicationEventPublisher events, QuoteStore store) {
        return new CreateQuote(clock, quoteIds, currentTenant, events, store);
    }

    @Bean
    GetQuote getQuote(CurrentTenant currentTenant, QuoteStore store) {
        return new GetQuote(currentTenant, store);
    }

    @Bean
    QuoteOffersService quoteOffers(QuoteStore store) {
        return new QuoteOffersService(store);
    }
}
