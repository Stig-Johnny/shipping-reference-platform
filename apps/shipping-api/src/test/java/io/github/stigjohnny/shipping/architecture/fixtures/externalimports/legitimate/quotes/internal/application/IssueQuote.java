package io.github.stigjohnny.shipping.architecture.fixtures.externalimports.legitimate.quotes.internal.application;

import java.time.Clock;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fixture of ExternalImportPolicyTest: an application service that uses what its layer may, as CreateQuote does.
 * Its type-use annotation {@code @Nullable} is in the class file, but ArchUnit 1.5.1 reports no dependency on it.
 */
public final class IssueQuote {

    private final ApplicationEventPublisher events;
    private final Clock clock;

    public IssueQuote(ApplicationEventPublisher events, Clock clock) {
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void issue(@Nullable Object quote) {
        events.publishEvent(Objects.requireNonNullElse(quote, clock.instant()));
    }
}
