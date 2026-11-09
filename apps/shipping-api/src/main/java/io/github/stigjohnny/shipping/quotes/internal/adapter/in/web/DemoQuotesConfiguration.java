package io.github.stigjohnny.shipping.quotes.internal.adapter.in.web;

import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Memory storage is the demo default; postgres supplies the existing JDBC adapter instead. */
@Configuration(proxyBeanMethods = false)
@Profile("demo & !postgres")
class DemoQuotesConfiguration {
    @Bean
    @Fallback
    QuoteStore demoQuoteStore() { return new DemoQuoteStore(); }
}
