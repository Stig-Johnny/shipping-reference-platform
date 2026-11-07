package io.github.stigjohnny.shipping.architecture.fixtures.modulith.internals.shipments;

import io.github.stigjohnny.shipping.architecture.fixtures.modulith.internals.quotes.internal.domain.Quote;

/** A negative fixture of ModularityTest: a module that uses another module's internal type. */
public record Booking(Quote quote) {
}
