package io.github.stigjohnny.shipping.architecture.fixtures.layering.layers.quotes.internal.domain;

import io.github.stigjohnny.shipping.architecture.fixtures.layering.layers.quotes.internal.application.IssueQuote;

/** A negative fixture of HexagonalLayeringTest: domain code that uses the application layer. */
public record PricingRule(IssueQuote useCase) {
}
