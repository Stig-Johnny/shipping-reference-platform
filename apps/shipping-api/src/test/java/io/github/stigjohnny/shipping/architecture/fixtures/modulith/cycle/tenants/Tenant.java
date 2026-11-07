package io.github.stigjohnny.shipping.architecture.fixtures.modulith.cycle.tenants;

import io.github.stigjohnny.shipping.architecture.fixtures.modulith.cycle.quotes.Quote;

/** A negative fixture of ModularityTest: the other half of the cycle. */
public record Tenant(Quote lastQuote) {
}
