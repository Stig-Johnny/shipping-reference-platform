package io.github.stigjohnny.shipping.architecture.fixtures.externalimports.springdomain.quotes.internal.domain;

import org.springframework.context.ApplicationEventPublisher;

/** A negative fixture of ExternalImportPolicyTest: a domain class that uses a Spring type. */
public final class PricedEvents {

    private final ApplicationEventPublisher events;

    public PricedEvents(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void priced(Object event) {
        events.publishEvent(event);
    }
}
